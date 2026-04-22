/* Resource loader: hierarchical JSON5 world file.
 *
 * Phase 6d retired the flat JSONL loader (data/resources.jsonl) and
 * folded all seven legacy record kinds — enum / item / monster / spell /
 * terrain / sextext / text — into the world tree. The parser is a
 * hand-rolled JSON5 front-end on top of a tagged node tree (node_kind /
 * node — see eval.h) that the evaluator walks directly.
 *
 * Load flow (res_load_world):
 *   1. parse_file(path): JSON5 source -> node tree.
 *   2. resolve_imports: splice every {"$import": "relpath"} directive
 *      with the parsed subtree of data/<relpath>. Cycles fatal.
 *   3. fold_*: walk the known subtrees (flags / items / monsters /
 *      classes+spells / texts / terrain / sextext / enums) and populate
 *      the matching legacy in-memory tables (ryl_item / ryl_monster /
 *      ryl_spell / kar_tab / color_tab / sextext_tab / enums / texts).
 *   4. compile_world_tree: rewrite every path-position N_STR (e.g.
 *      "caster.hp", "$dmg") to a packed N_PATH so the evaluator hot
 *      path does zero strcmp on actor/field names.
 *
 * The folds are forgiving about field order and missing optionals but
 * fatal on structural surprises; errors dispatch through die() with a
 * "res: path:line: msg" message. The legacy tables retain the fold
 * results for the rest of run time; the full node tree is also kept
 * (process-lifetime) for the evaluator via res_world().
 */

#include "res.h"
#include "eval.h"
#include "ryl.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define RES_MAX_ITEM_ID    111
#define RES_MAX_MONSTER_ID 90

typedef struct {
    char *id;
    char **values;
    size_t n_values;
} enum_rec;

static enum_rec *enums;
static size_t n_enums;

typedef struct {
    char *id;
    char *text;
} text_rec;

static text_rec *texts;
static size_t n_texts;
/* Set after fold_texts completes so res_text can switch from the
   linear append path (emit_text clears the flag) to bsearch. */
static int texts_sorted;

static ryl_item    items[RES_MAX_ITEM_ID + 1];
static ryl_monster monsters[RES_MAX_MONSTER_ID + 1];

#define RES_MAX_SPELL_SLOT 7
static ryl_spell priest_spells[RES_MAX_SPELL_SLOT + 1];
static ryl_spell mage_spells[RES_MAX_SPELL_SLOT + 1];

static char   *kar_tab[256];
static int32_t color_tab[256];
static int     color_tab_set[256];
static int32_t color_default = 36;    /* cyan */

/* ANSI foreground colour name (as it appears in data/world.json5) to
   SGR code. Authors should never type the raw numbers; fold_terrain
   resolves here so the config file reads in English. */
static int32_t color_code_from_name(const char *name) {
    if (strcmp(name, "black")   == 0) return 30;
    if (strcmp(name, "red")     == 0) return 31;
    if (strcmp(name, "green")   == 0) return 32;
    if (strcmp(name, "yellow")  == 0) return 33;
    if (strcmp(name, "blue")    == 0) return 34;
    if (strcmp(name, "magenta") == 0) return 35;
    if (strcmp(name, "cyan")    == 0) return 36;
    if (strcmp(name, "white")   == 0) return 37;
    return -1;
}

static char *sextext_tab[3][4];

/* Flags table (docs/dsl.md §3.1). Symbolic name -> {slot, scope, values}.
   Populated from data/world.json5 flags subtree; empty until world loads. */
typedef struct {
    char    *id;
    int32_t  slot;
    int32_t  scope;    /* 0 = party (state.p[cpl].flag[slot]), 1 = encounter */
    char   **values;
    size_t   n_values;
} flag_rec;

static flag_rec *flags_tab;
static size_t    n_flags_tab;

/* JSON5 world tree + its lazily-built per-subtree indices. Raw node
   pointers live here; the evaluator walks them via eval.h. */
static node *world_tree;

__attribute__((noreturn))
static void die(const char *path, int line, const char *msg) {
    fprintf(stderr, "res: %s:%d: %s\n", path, line, msg);
    exit(1);
}

/* Map a `legacy_effect` string from the world tree to the SE_* enum
   strid.c's priest/mage switches dispatch on. Returns -1 for unknown
   names so the caller can die() with a path:line pointing at the tree
   node. Phase 6d retired the JSONL handle_spell; this helper owns the
   string table on behalf of fold_spells. */
static int32_t se_from_legacy_effect(const char *eff) {
    if (strcmp(eff, "heal")        == 0) return SE_HEAL;
    if (strcmp(eff, "restoration") == 0) return SE_RESTORATION;
    if (strcmp(eff, "rebirth")     == 0) return SE_REBIRTH;
    if (strcmp(eff, "banish")      == 0) return SE_BANISH;
    if (strcmp(eff, "revelation")  == 0) return SE_REVELATION;
    if (strcmp(eff, "teleport")    == 0) return SE_TELEPORT;
    if (strcmp(eff, "earthquake")  == 0) return SE_EARTHQUAKE;
    if (strcmp(eff, "fire")        == 0) return SE_FIRE;
    if (strcmp(eff, "confusion")   == 0) return SE_CONFUSION;
    if (strcmp(eff, "death")       == 0) return SE_DEATH;
    if (strcmp(eff, "hold")        == 0) return SE_HOLD;
    if (strcmp(eff, "antimagic")   == 0) return SE_ANTIMAGIC;
    return -1;
}

const char *res_enum(const char *id, int index) {
    for (size_t i = 0; i < n_enums; i++) {
        if (strcmp(enums[i].id, id) == 0) {
            if (index < 0 || (size_t) index >= enums[i].n_values) return "";
            return enums[i].values[index];
        }
    }
    return "";
}

static int text_cmp(const void *a, const void *b) {
    return strcmp(((const text_rec *) a)->id, ((const text_rec *) b)->id);
}

static void texts_sort(void) {
    if (texts_sorted || n_texts == 0) return;
    qsort(texts, n_texts, sizeof *texts, text_cmp);
    texts_sorted = 1;
}

const char *res_text(const char *id) {
    if (!texts_sorted) {
        for (size_t i = 0; i < n_texts; i++) {
            if (strcmp(texts[i].id, id) == 0) return texts[i].text;
        }
        return "";
    }
    text_rec key = { (char *) id, NULL };
    text_rec *hit = bsearch(&key, texts, n_texts, sizeof *texts, text_cmp);
    return hit ? hit->text : "";
}

const ryl_item *res_item(int id) {
    if (id < 0 || id > RES_MAX_ITEM_ID) return NULL;
    return items[id].name ? &items[id] : NULL;
}

const ryl_monster *res_monster(int id) {
    if (id < 0 || id > RES_MAX_MONSTER_ID) return NULL;
    return monsters[id].name ? &monsters[id] : NULL;
}

const ryl_spell *res_spell(const char *class_name, int slot) {
    if (slot < 1 || slot > RES_MAX_SPELL_SLOT) return NULL;
    int cid = pm_class_id(class_name);
    const ryl_spell *tab;
    if (cid == PC_PRIEST)    tab = priest_spells;
    else if (cid == PC_MAGE) tab = mage_spells;
    else return NULL;
    return tab[slot].name ? &tab[slot] : NULL;
}

const char *res_kar(char glyph) {
    unsigned char g = (unsigned char) glyph;
    return kar_tab[g] ? kar_tab[g] : "  ";
}

int32_t res_color(char glyph) {
    unsigned char g = (unsigned char) glyph;
    return color_tab_set[g] ? color_tab[g] : color_default;
}

const char *res_sextext(int sex, int xx) {
    if (sex < 1 || sex > 2 || xx < 1 || xx > 3) return "";
    return sextext_tab[sex][xx] ? sextext_tab[sex][xx] : "";
}

/* ---------------------------------------------------------------- */
/*  JSON5 parser + $import resolver + world loader (Phase 6d).      */
/* ---------------------------------------------------------------- */

/* node / node_kv / node_new / node_free / obj_get live in eval.c so parser
   and evaluator share one vocabulary; see eval.h for the shapes. */

typedef struct {
    const char *path;
    const char *src;      /* owned by caller */
    const char *cur;
    int         line;
} parser;

__attribute__((noreturn))
static void pdie(const parser *p, const char *msg) {
    die(p->path, p->line, msg);
}

static void p_skip_ws(parser *p) {
    for (;;) {
        unsigned char c = (unsigned char) *p->cur;
        if (c == ' ' || c == '\t' || c == '\r') {
            p->cur++;
        } else if (c == '\n') {
            p->cur++;
            p->line++;
        } else if (c == '/' && p->cur[1] == '/') {
            while (*p->cur && *p->cur != '\n') p->cur++;
        } else if (c == '/' && p->cur[1] == '*') {
            p->cur += 2;
            while (*p->cur && !(p->cur[0] == '*' && p->cur[1] == '/')) {
                if (*p->cur == '\n') p->line++;
                p->cur++;
            }
            if (*p->cur) p->cur += 2;
        } else {
            break;
        }
    }
}

static int is_ident_start(int c) { return c == '_' || c == '$' || isalpha(c); }
static int is_ident_cont(int c)  { return c == '_' || c == '$' || isalnum(c); }

static char *p_ident_raw(parser *p) {
    if (!is_ident_start((unsigned char) *p->cur)) pdie(p, "expected identifier");
    const char *s = p->cur;
    while (is_ident_cont((unsigned char) *p->cur)) p->cur++;
    size_t n = (size_t) (p->cur - s);
    char *out = malloc(n + 1);
    if (!out) pdie(p, "oom");
    memcpy(out, s, n);
    out[n] = 0;
    return out;
}

/* Quoted string supporting "..." and '...'. Escapes: \" \' \\ \n \t \r \b
   \f \/ and \uXXXX (basic; encodes >0x7f as UTF-8 up to U+FFFF). */
static char *p_quoted_string(parser *p) {
    char quote = *p->cur;
    if (quote != '"' && quote != '\'') pdie(p, "expected string");
    p->cur++;
    const char *s = p->cur;
    /* first pass: bound the output size at input length */
    size_t cap = 16;
    char *out = malloc(cap);
    if (!out) pdie(p, "oom");
    size_t n = 0;
#define PUSH(b) do { \
    if (n + 4 >= cap) { cap *= 2; out = realloc(out, cap); if (!out) pdie(p, "oom"); } \
    out[n++] = (char) (b); \
} while (0)
    while (*s && *s != quote) {
        if (*s == '\n') p->line++;
        if (*s == '\\') {
            s++;
            switch (*s) {
                case '"':  PUSH('"');  s++; break;
                case '\'': PUSH('\''); s++; break;
                case '\\': PUSH('\\'); s++; break;
                case '/':  PUSH('/');  s++; break;
                case 'n':  PUSH('\n'); s++; break;
                case 't':  PUSH('\t'); s++; break;
                case 'r':  PUSH('\r'); s++; break;
                case 'b':  PUSH('\b'); s++; break;
                case 'f':  PUSH('\f'); s++; break;
                case 'u': {
                    s++;
                    unsigned v = 0;
                    for (int i = 0; i < 4; i++) {
                        int c = (unsigned char) s[i];
                        int d = (c >= '0' && c <= '9') ? c - '0'
                              : (c >= 'a' && c <= 'f') ? c - 'a' + 10
                              : (c >= 'A' && c <= 'F') ? c - 'A' + 10 : -1;
                        if (d < 0) { p->cur = s; pdie(p, "bad \\u escape"); }
                        v = v * 16 + (unsigned) d;
                    }
                    s += 4;
                    if (v < 0x80) {
                        PUSH(v);
                    } else if (v < 0x800) {
                        PUSH(0xC0 | (v >> 6));
                        PUSH(0x80 | (v & 0x3F));
                    } else {
                        PUSH(0xE0 | (v >> 12));
                        PUSH(0x80 | ((v >> 6) & 0x3F));
                        PUSH(0x80 | (v & 0x3F));
                    }
                    break;
                }
                case '\n': /* line continuation */ p->line++; s++; break;
                default:   PUSH(*s); if (*s) s++; break;
            }
        } else {
            PUSH(*s);
            s++;
        }
    }
    if (*s != quote) { p->cur = s; pdie(p, "unterminated string"); }
    PUSH(0);
    n--;
    p->cur = s + 1;
#undef PUSH
    return out;
}

static int32_t p_int(parser *p) {
    const char *s = p->cur;
    int neg = 0;
    if (*s == '+' || *s == '-') { neg = (*s == '-'); s++; }
    if (!isdigit((unsigned char) *s)) pdie(p, "expected number");
    char *end;
    long v = strtol(p->cur, &end, 10);
    if (end == p->cur) pdie(p, "expected number");
    /* reject fractional / exponent for now — phase 6d has no float needs */
    if (*end == '.' || *end == 'e' || *end == 'E') pdie(p, "floats not supported");
    p->cur = end;
    (void) neg; /* strtol already handled sign */
    return (int32_t) v;
}

static node *p_value(parser *p);

static node *p_array(parser *p) {
    if (*p->cur != '[') pdie(p, "expected '['");
    node *a = node_new(N_ARR, p->line);
    p->cur++;
    size_t cap = 0;
    for (;;) {
        p_skip_ws(p);
        if (*p->cur == ']') { p->cur++; return a; }
        node *v = p_value(p);
        if (a->u.arr.n == cap) {
            cap = cap ? cap * 2 : 8;
            a->u.arr.items = realloc(a->u.arr.items, cap * sizeof *a->u.arr.items);
            if (!a->u.arr.items) pdie(p, "oom");
        }
        a->u.arr.items[a->u.arr.n++] = v;
        p_skip_ws(p);
        if (*p->cur == ',') { p->cur++; continue; }
        if (*p->cur == ']') { p->cur++; return a; }
        pdie(p, "expected ',' or ']'");
    }
}

static node *p_object(parser *p) {
    if (*p->cur != '{') pdie(p, "expected '{'");
    node *o = node_new(N_OBJ, p->line);
    p->cur++;
    size_t cap = 0;
    for (;;) {
        p_skip_ws(p);
        if (*p->cur == '}') { p->cur++; return o; }
        char *key;
        if (*p->cur == '"' || *p->cur == '\'') {
            key = p_quoted_string(p);
        } else if (is_ident_start((unsigned char) *p->cur)) {
            key = p_ident_raw(p);
        } else {
            pdie(p, "expected object key");
        }
        p_skip_ws(p);
        if (*p->cur != ':') { free(key); pdie(p, "expected ':'"); }
        p->cur++;
        p_skip_ws(p);
        node *v = p_value(p);
        if (o->u.obj.n == cap) {
            cap = cap ? cap * 2 : 8;
            o->u.obj.items = realloc(o->u.obj.items, cap * sizeof *o->u.obj.items);
            if (!o->u.obj.items) pdie(p, "oom");
        }
        o->u.obj.items[o->u.obj.n].key = key;
        o->u.obj.items[o->u.obj.n].val = v;
        o->u.obj.n++;
        p_skip_ws(p);
        if (*p->cur == ',') { p->cur++; continue; }
        if (*p->cur == '}') { p->cur++; return o; }
        pdie(p, "expected ',' or '}'");
    }
}

static node *p_value(parser *p) {
    p_skip_ws(p);
    int line = p->line;
    unsigned char c = (unsigned char) *p->cur;
    if (c == '{') return p_object(p);
    if (c == '[') return p_array(p);
    if (c == '"' || c == '\'') {
        node *n = node_new(N_STR, line);
        n->u.sv = p_quoted_string(p);
        return n;
    }
    if (c == '-' || c == '+' || isdigit(c)) {
        node *n = node_new(N_INT, line);
        n->u.iv = p_int(p);
        return n;
    }
    if (is_ident_start(c)) {
        char *id = p_ident_raw(p);
        if (strcmp(id, "true") == 0)  { free(id); node *n = node_new(N_BOOL, line); n->u.bv = 1; return n; }
        if (strcmp(id, "false") == 0) { free(id); node *n = node_new(N_BOOL, line); n->u.bv = 0; return n; }
        if (strcmp(id, "null") == 0)  { free(id); return node_new(N_NULL, line); }
        free(id);
        pdie(p, "unexpected identifier as value");
    }
    pdie(p, "unexpected character");
}

static char *slurp_file(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    if (fseek(f, 0, SEEK_END) != 0) { fclose(f); return NULL; }
    long sz = ftell(f);
    if (sz < 0) { fclose(f); return NULL; }
    rewind(f);
    char *buf = malloc((size_t) sz + 1);
    if (!buf) { fclose(f); return NULL; }
    size_t n = fread(buf, 1, (size_t) sz, f);
    fclose(f);
    buf[n] = 0;
    return buf;
}

static node *parse_file(const char *path) {
    char *src = slurp_file(path);
    if (!src) {
        fprintf(stderr, "res: cannot read %s\n", path);
        exit(1);
    }
    parser p = { .path = path, .src = src, .cur = src, .line = 1 };
    p_skip_ws(&p);
    node *root = p_value(&p);
    p_skip_ws(&p);
    if (*p.cur != 0) die(path, p.line, "trailing content after root value");
    free(src);
    return root;
}

/* $import resolver (docs/dsl.md §2.1).
 *
 * Walk the tree; any object whose sole member is "$import": "relpath"
 * is replaced by the tree parsed from data/<relpath>. Cycles are fatal.
 * Only .json5 imports are supported here; .dat byte adapters (§3.7) are
 * deferred until the map-externalisation step.
 *
 * The `stack` list of currently-loading paths is passed through the
 * recursion so an import referring back to an ancestor fails fast with
 * a readable message.
 */
typedef struct import_frame {
    const char          *path;
    struct import_frame *prev;
} import_frame;

static int is_import_directive(const node *n) {
    return n && n->kind == N_OBJ && n->u.obj.n == 1 &&
           strcmp(n->u.obj.items[0].key, "$import") == 0 &&
           n->u.obj.items[0].val && n->u.obj.items[0].val->kind == N_STR;
}

static node *resolve_imports(node *n, import_frame *stack, const char *src_path);

static node *load_import(const char *relpath, int directive_line,
                         import_frame *stack, const char *src_path) {
    /* suffix check — only .json5 supported this commit */
    size_t len = strlen(relpath);
    if (len < 6 || strcmp(relpath + len - 6, ".json5") != 0) {
        fprintf(stderr, "res: %s:%d: $import: only .json5 supported yet (%s)\n",
                src_path, directive_line, relpath);
        exit(1);
    }
    char full[512];
    int wrote = snprintf(full, sizeof full, "data/%s", relpath);
    if (wrote < 0 || (size_t) wrote >= sizeof full) {
        fprintf(stderr, "res: %s:%d: import path too long\n", src_path, directive_line);
        exit(1);
    }
    for (import_frame *f = stack; f; f = f->prev) {
        if (strcmp(f->path, full) == 0) {
            fprintf(stderr, "res: %s:%d: import cycle: %s\n",
                    src_path, directive_line, full);
            exit(1);
        }
    }
    node *sub = parse_file(full);
    import_frame frame = { .path = full, .prev = stack };
    return resolve_imports(sub, &frame, full);
}

static node *resolve_imports(node *n, import_frame *stack, const char *src_path) {
    if (!n) return n;
    if (is_import_directive(n)) {
        const char *relpath = n->u.obj.items[0].val->u.sv;
        int line = n->line;
        node *repl = load_import(relpath, line, stack, src_path);
        node_free(n);
        return repl;
    }
    if (n->kind == N_ARR) {
        for (size_t i = 0; i < n->u.arr.n; i++) {
            n->u.arr.items[i] = resolve_imports(n->u.arr.items[i], stack, src_path);
        }
    } else if (n->kind == N_OBJ) {
        for (size_t i = 0; i < n->u.obj.n; i++) {
            n->u.obj.items[i].val = resolve_imports(n->u.obj.items[i].val, stack, src_path);
        }
    }
    return n;
}

/* -------- flags subtree -------- */

static void fold_flags(const node *flags_node, const char *path) {
    if (!flags_node) return;
    if (flags_node->kind != N_OBJ) die(path, flags_node->line, "flags: expected object");
    for (size_t i = 0; i < flags_node->u.obj.n; i++) {
        const char *id = flags_node->u.obj.items[i].key;
        const node *rec = flags_node->u.obj.items[i].val;
        if (rec->kind != N_OBJ) die(path, rec->line, "flags: entry must be object");
        const node *slot_n = obj_get(rec, "slot");
        if (!slot_n || slot_n->kind != N_INT) die(path, rec->line, "flags: missing 'slot'");
        const node *scope_n = obj_get(rec, "scope");
        int32_t scope = 0;
        if (scope_n) {
            if (scope_n->kind != N_STR) die(path, scope_n->line, "flags: scope must be string");
            if (strcmp(scope_n->u.sv, "party") == 0) scope = 0;
            else if (strcmp(scope_n->u.sv, "encounter") == 0) scope = 1;
            else die(path, scope_n->line, "flags: scope must be 'party' or 'encounter'");
        }
        const node *vals_n = obj_get(rec, "values");
        if (!vals_n || vals_n->kind != N_ARR) die(path, rec->line, "flags: missing 'values' array");
        flag_rec *grown = realloc(flags_tab, (n_flags_tab + 1) * sizeof *flags_tab);
        if (!grown) die(path, rec->line, "oom");
        flags_tab = grown;
        flag_rec *r = &flags_tab[n_flags_tab++];
        r->id = strdup(id);
        r->slot = slot_n->u.iv;
        r->scope = scope;
        r->n_values = vals_n->u.arr.n;
        r->values = malloc(r->n_values * sizeof *r->values);
        if (!r->values) die(path, rec->line, "oom");
        for (size_t j = 0; j < r->n_values; j++) {
            const node *v = vals_n->u.arr.items[j];
            if (v->kind != N_STR) die(path, v->line, "flags: value must be string");
            r->values[j] = strdup(v->u.sv);
        }
    }
}

/* -------- items subtree (docs/dsl.md §2.2) --------
   The items tree is sectioned by kind (weapons/armour/gear/potions/
   treasures/magic_items); the sections are an authoring convenience,
   the legacy ryl_item[0..111] table is still keyed by flat `id`.

   For each record: id, name, cost are read and folded into the legacy
   table. `damage` (weapons) and `ac` (armour) both land in the single
   ryl_item.damage slot — obdamage() at the call sites reads the same
   field for both roles. `wieldable_by` is rewritten in place from an
   array of class-name strings to an integer class-id bitmask so the
   evaluator can dispatch without strcmp at runtime; fatal on unknown
   class. Other fields (kind / slot / effect / ...) parse into the node
   tree for the evaluator but are inert this commit. */
static void fold_wieldable_by(node *rec, const char *path) {
    for (size_t j = 0; j < rec->u.obj.n; j++) {
        if (strcmp(rec->u.obj.items[j].key, "wieldable_by") != 0) continue;
        node *val = rec->u.obj.items[j].val;
        if (!val || val->kind != N_ARR) {
            die(path, val ? val->line : rec->line, "wieldable_by: expected array");
        }
        int32_t mask = 0;
        for (size_t k = 0; k < val->u.arr.n; k++) {
            node *el = val->u.arr.items[k];
            if (!el || el->kind != N_STR) die(path, val->line, "wieldable_by: element must be string");
            int cid = pm_class_id(el->u.sv);
            if (cid < 0) die(path, el->line, "wieldable_by: unknown class");
            mask |= (int32_t) (1u << (unsigned) cid);
        }
        node *repl = node_new(N_INT, val->line);
        repl->u.iv = mask;
        node_free(val);
        rec->u.obj.items[j].val = repl;
        return;
    }
}

static void fold_items_section(const node *section, const char *path) {
    if (section->kind != N_OBJ) die(path, section->line, "items: section must be object");
    for (size_t i = 0; i < section->u.obj.n; i++) {
        const char *sym = section->u.obj.items[i].key;
        node *rec = section->u.obj.items[i].val;
        if (rec->kind != N_OBJ) die(path, rec->line, "items: entry must be object");
        const node *id_n = obj_get(rec, "id");
        if (!id_n || id_n->kind != N_INT) die(path, rec->line, "items: missing 'id'");
        int32_t id = id_n->u.iv;
        if (id < 0 || id > RES_MAX_ITEM_ID) die(path, id_n->line, "items: id out of range");
        ryl_item *it = &items[id];
        free((void *) it->name);
        const node *name_n = obj_get(rec, "name");
        it->name = strdup((name_n && name_n->kind == N_STR) ? name_n->u.sv : sym);
        const node *cost_n = obj_get(rec, "cost");
        it->cost = (cost_n && cost_n->kind == N_INT) ? cost_n->u.iv : 0;
        const node *dmg_n = obj_get(rec, "damage");
        const node *ac_n  = obj_get(rec, "ac");
        if      (dmg_n && dmg_n->kind == N_INT) it->damage = dmg_n->u.iv;
        else if (ac_n  && ac_n->kind  == N_INT) it->damage = ac_n->u.iv;
        else                                    it->damage = 0;
        fold_wieldable_by(rec, path);
    }
}

static void fold_items(const node *items_node, const char *path) {
    if (!items_node) return;
    if (items_node->kind != N_OBJ) die(path, items_node->line, "items: expected object");
    for (size_t s = 0; s < items_node->u.obj.n; s++) {
        fold_items_section(items_node->u.obj.items[s].val, path);
    }
}

/* -------- monsters subtree (docs/dsl.md §2.2) --------
   Symbolic key in the shard is author-facing; `id` (map.dat's monster
   index) is the runtime key. Missing optional fields default to 0. */
static void fold_monsters(const node *monsters_node, const char *path) {
    if (!monsters_node) return;
    if (monsters_node->kind != N_OBJ) die(path, monsters_node->line, "monsters: expected object");
    for (size_t i = 0; i < monsters_node->u.obj.n; i++) {
        const node *rec = monsters_node->u.obj.items[i].val;
        if (rec->kind != N_OBJ) die(path, rec->line, "monsters: entry must be object");
        const node *id_n = obj_get(rec, "id");
        if (!id_n || id_n->kind != N_INT) die(path, rec->line, "monsters: missing 'id'");
        int32_t id = id_n->u.iv;
        if (id < 0 || id > RES_MAX_MONSTER_ID) die(path, id_n->line, "monsters: id out of range");
        ryl_monster *m = &monsters[id];
        free((void *) m->name);
        const node *name_n = obj_get(rec, "name");
        m->name = strdup((name_n && name_n->kind == N_STR) ? name_n->u.sv : "");
        const node *n1 = obj_get(rec, "n1"); m->n1 = (n1 && n1->kind == N_INT) ? n1->u.iv : 0;
        const node *n2 = obj_get(rec, "n2"); m->n2 = (n2 && n2->kind == N_INT) ? n2->u.iv : 0;
        const node *h1 = obj_get(rec, "h1"); m->h1 = (h1 && h1->kind == N_INT) ? h1->u.iv : 0;
        const node *h2 = obj_get(rec, "h2"); m->h2 = (h2 && h2->kind == N_INT) ? h2->u.iv : 0;
        const node *ws = obj_get(rec, "ws"); m->ws = (ws && ws->kind == N_INT) ? ws->u.iv : 0;
        const node *sp = obj_get(rec, "sp"); m->sp = (sp && sp->kind == N_INT) ? sp->u.iv : 0;
        const node *im = obj_get(rec, "im"); m->im = (im && im->kind == N_INT) ? im->u.iv : 0;
    }
}

/* -------- texts subtree (docs/dsl.md §2.2) --------
   Hierarchical: every nested object's key becomes one dotted segment of
   the final text id. Leaves are either strings (verbatim) or arrays of
   strings (joined with \r\n, trailing \r\n appended). Emitted records
   are sorted once after all world folds complete, enabling bsearch. */
static void emit_text(const char *id, const char *text) {
    text_rec *grown = realloc(texts, (n_texts + 1) * sizeof *texts);
    if (!grown) { fprintf(stderr, "res: oom\n"); exit(1); }
    texts = grown;
    texts[n_texts].id = strdup(id);
    texts[n_texts].text = strdup(text);
    n_texts++;
    texts_sorted = 0;
}

static char *join_lines_crlf(const node *arr, const char *path) {
    size_t total = 0;
    for (size_t i = 0; i < arr->u.arr.n; i++) {
        const node *el = arr->u.arr.items[i];
        if (el->kind != N_STR) die(path, el->line, "texts: line must be string");
        total += strlen(el->u.sv) + 2;
    }
    char *out = malloc(total + 1);
    if (!out) die(path, arr->line, "oom");
    size_t o = 0;
    for (size_t i = 0; i < arr->u.arr.n; i++) {
        const char *s = arr->u.arr.items[i]->u.sv;
        size_t n = strlen(s);
        memcpy(out + o, s, n);
        o += n;
        out[o++] = '\r';
        out[o++] = '\n';
    }
    out[o] = 0;
    return out;
}

static void fold_texts_walk(const node *n, const char *prefix, const char *path) {
    if (!n) return;
    if (n->kind == N_STR) {
        emit_text(prefix, n->u.sv);
        return;
    }
    if (n->kind == N_ARR) {
        char *joined = join_lines_crlf(n, path);
        emit_text(prefix, joined);
        free(joined);
        return;
    }
    if (n->kind == N_OBJ) {
        char buf[256];
        for (size_t i = 0; i < n->u.obj.n; i++) {
            const char *key = n->u.obj.items[i].key;
            if (!prefix[0]) {
                snprintf(buf, sizeof buf, "%s", key);
            } else {
                snprintf(buf, sizeof buf, "%s.%s", prefix, key);
            }
            fold_texts_walk(n->u.obj.items[i].val, buf, path);
        }
        return;
    }
    die(path, n->line, "texts: unexpected node kind");
}

static void fold_texts(const node *texts_node, const char *path) {
    if (!texts_node) return;
    if (texts_node->kind != N_OBJ) die(path, texts_node->line, "texts: expected object");
    fold_texts_walk(texts_node, "", path);
}

/* -------- classes + spells subtree (docs/dsl.md §2.2) --------
   The spells tree is nested by class (spells.mage / spells.priest); the
   dotted ids in classes.<cls>.spells (e.g. "mage.frost") give the full
   symbolic name, but fold only reads the local part after the dot when
   indexing into the per-class subtree.

   Each spell folds into the legacy ryl_spell table so the cast menu
   can still render name/base_cost. Effect dispatch:
     - `do` present        -> SE_NONE; strid.c's res_spell_node path
                              runs eval_effect on the body instead of
                              reaching the switch.
     - `legacy_effect` set -> se_from_legacy_effect() picks the SE_*
                              the remaining switch cases fire on. */
static void fold_spells(const node *classes_node, const node *spells_node, const char *path) {
    if (!classes_node || !spells_node) return;
    if (classes_node->kind != N_OBJ) die(path, classes_node->line, "classes: expected object");
    if (spells_node->kind != N_OBJ) die(path, spells_node->line, "spells: expected object");
    for (size_t c = 0; c < classes_node->u.obj.n; c++) {
        const char *cname = classes_node->u.obj.items[c].key;
        const node *cls = classes_node->u.obj.items[c].val;
        if (cls->kind != N_OBJ) continue;
        const node *list = obj_get(cls, "spells");
        if (!list || list->kind != N_ARR) continue;
        int cid = pm_class_id(cname);
        ryl_spell *tab = NULL;
        if (cid == PC_PRIEST)    tab = priest_spells;
        else if (cid == PC_MAGE) tab = mage_spells;
        else continue;
        const node *sub = obj_get(spells_node, cname);
        if (!sub || sub->kind != N_OBJ) die(path, list->line, "spells: missing subtree for class");
        for (size_t i = 0; i < list->u.arr.n; i++) {
            const node *id_n = list->u.arr.items[i];
            if (!id_n || id_n->kind != N_STR) die(path, list->line, "classes.spells: id must be string");
            const char *dot = strchr(id_n->u.sv, '.');
            const char *local = dot ? dot + 1 : id_n->u.sv;
            const node *spell = obj_get(sub, local);
            if (!spell || spell->kind != N_OBJ) die(path, list->line, "spells: missing id referenced by classes");
            const node *slot_n = obj_get(spell, "slot");
            if (!slot_n || slot_n->kind != N_INT) die(path, spell->line, "spell: missing 'slot'");
            int32_t slot = slot_n->u.iv;
            if (slot < 1 || slot > RES_MAX_SPELL_SLOT) die(path, slot_n->line, "spell: slot out of range");
            const node *name_n = obj_get(spell, "name");
            const node *cost_n = obj_get(spell, "base_cost");
            ryl_spell *s = &tab[slot];
            free((void *) s->id);
            free((void *) s->name);
            s->id = strdup(id_n->u.sv);
            s->name = strdup((name_n && name_n->kind == N_STR) ? name_n->u.sv : id_n->u.sv);
            s->slot = slot;
            s->base_cost = (cost_n && cost_n->kind == N_INT) ? cost_n->u.iv : 0;
            const node *leg_n = obj_get(spell, "legacy_effect");
            if (leg_n) {
                if (leg_n->kind != N_STR) die(path, leg_n->line, "spell: legacy_effect must be string");
                int32_t se = se_from_legacy_effect(leg_n->u.sv);
                if (se < 0) die(path, leg_n->line, "spell: unknown legacy_effect");
                s->effect = se;
            } else {
                s->effect = SE_NONE;
            }
        }
    }
}

/* -------- terrain subtree --------
   Keyed by the literal map.dat glyph byte as a single-character string
   so the record reads the same way the map file stores it. `kar` is
   the two-char cell; `color` is one of the ANSI foreground colour
   names black/red/green/yellow/blue/magenta/cyan/white. */
static void fold_terrain(const node *terrain_node, const char *path) {
    if (!terrain_node) return;
    if (terrain_node->kind != N_OBJ) die(path, terrain_node->line, "terrain: expected object");
    for (size_t i = 0; i < terrain_node->u.obj.n; i++) {
        const char *glyph = terrain_node->u.obj.items[i].key;
        const node *rec = terrain_node->u.obj.items[i].val;
        if (!glyph || !glyph[0] || glyph[1]) die(path, rec->line, "terrain: key must be one-char string");
        if (rec->kind != N_OBJ) die(path, rec->line, "terrain: entry must be object");
        unsigned char g = (unsigned char) glyph[0];
        const node *kar_n = obj_get(rec, "kar");
        if (kar_n) {
            if (kar_n->kind != N_STR) die(path, kar_n->line, "terrain: kar must be string");
            free(kar_tab[g]);
            kar_tab[g] = strdup(kar_n->u.sv);
        }
        const node *color_n = obj_get(rec, "color");
        if (color_n) {
            if (color_n->kind != N_STR) die(path, color_n->line, "terrain: color must be string");
            int32_t code = color_code_from_name(color_n->u.sv);
            if (code < 0) die(path, color_n->line, "terrain: unknown color name");
            color_tab[g] = code;
            color_tab_set[g] = 1;
        }
    }
}

/* -------- sextext subtree --------
   Two fixed keys, "male" and "female", map to sex indices 1 and 2 that
   the legacy res_sextext() API already uses. */
static void fold_sextext(const node *sextext_node, const char *path) {
    if (!sextext_node) return;
    if (sextext_node->kind != N_OBJ) die(path, sextext_node->line, "sextext: expected object");
    for (size_t i = 0; i < sextext_node->u.obj.n; i++) {
        const char *key = sextext_node->u.obj.items[i].key;
        const node *rec = sextext_node->u.obj.items[i].val;
        int sex;
        if      (strcmp(key, "male")   == 0) sex = 1;
        else if (strcmp(key, "female") == 0) sex = 2;
        else die(path, rec->line, "sextext: key must be 'male' or 'female'");
        if (rec->kind != N_OBJ) die(path, rec->line, "sextext: entry must be object");
        const node *subj = obj_get(rec, "subject");
        const node *obj_n = obj_get(rec, "object");
        const node *poss = obj_get(rec, "possessive");
        free(sextext_tab[sex][1]);
        sextext_tab[sex][1] = strdup((subj  && subj->kind  == N_STR) ? subj->u.sv  : "");
        free(sextext_tab[sex][2]);
        sextext_tab[sex][2] = strdup((obj_n && obj_n->kind == N_STR) ? obj_n->u.sv : "");
        free(sextext_tab[sex][3]);
        sextext_tab[sex][3] = strdup((poss  && poss->kind  == N_STR) ? poss->u.sv  : "");
    }
}

/* -------- enums subtree --------
   `{ jobname: [...], name: [...] }` — key is the enum id, value is the
   string table res_enum(id, index) indexes into. */
static void fold_enums(const node *enums_node, const char *path) {
    if (!enums_node) return;
    if (enums_node->kind != N_OBJ) die(path, enums_node->line, "enums: expected object");
    for (size_t i = 0; i < enums_node->u.obj.n; i++) {
        const char *id = enums_node->u.obj.items[i].key;
        const node *arr = enums_node->u.obj.items[i].val;
        if (arr->kind != N_ARR) die(path, arr->line, "enums: value must be array");
        enum_rec *grown = realloc(enums, (n_enums + 1) * sizeof *enums);
        if (!grown) die(path, arr->line, "oom");
        enums = grown;
        enum_rec *r = &enums[n_enums++];
        r->id = strdup(id);
        r->n_values = arr->u.arr.n;
        r->values = r->n_values ? malloc(r->n_values * sizeof *r->values) : NULL;
        if (r->n_values && !r->values) die(path, arr->line, "oom");
        for (size_t j = 0; j < arr->u.arr.n; j++) {
            const node *v = arr->u.arr.items[j];
            if (v->kind != N_STR) die(path, v->line, "enums: element must be string");
            r->values[j] = strdup(v->u.sv);
        }
    }
}

void res_load_world(const char *path) {
    /* Tolerant: missing file = empty world. Corrupt file = fatal
       (parser die()s with path:line:msg). */
    FILE *probe = fopen(path, "rb");
    if (!probe) return;
    fclose(probe);

    node *root = parse_file(path);
    root = resolve_imports(root, NULL, path);
    if (root->kind != N_OBJ) die(path, root->line, "world root must be object");

    /* Fold every known subtree into its legacy table. Order is not
       load-bearing — each fold reads disjoint state — but keeping it
       stable helps when diffing error output across commits. */
    fold_flags(obj_get(root, "flags"), path);
    fold_items(obj_get(root, "items"), path);
    fold_monsters(obj_get(root, "monsters"), path);
    fold_spells(obj_get(root, "classes"), obj_get(root, "spells"), path);
    fold_terrain(obj_get(root, "terrain"), path);
    fold_sextext(obj_get(root, "sextext"), path);
    fold_enums(obj_get(root, "enums"), path);
    fold_texts(obj_get(root, "texts"), path);

    /* Walk every effect / cond / expr array in the tree and rewrite path
       strings (e.g. "caster.hp", "$dmg") in place to N_PATH. After this,
       the evaluator hot path does zero strcmp on actor/field names. */
    compile_world_tree(root, path);
    world_tree = root;
    texts_sort();
}

const void *res_world(void) {
    return world_tree;
}

int32_t res_flag_slot(const char *id) {
    for (size_t i = 0; i < n_flags_tab; i++) {
        if (strcmp(flags_tab[i].id, id) == 0) return flags_tab[i].slot;
    }
    return -1;
}

const char *res_flag_value(const char *id, int index) {
    for (size_t i = 0; i < n_flags_tab; i++) {
        if (strcmp(flags_tab[i].id, id) == 0) {
            if (index < 0 || (size_t) index >= flags_tab[i].n_values) return "";
            return flags_tab[i].values[index];
        }
    }
    return "";
}

const void *res_spell_node(const char *class_id, int slot) {
    const node *root = world_tree;
    if (!root) return NULL;
    const node *classes = obj_get(root, "classes");
    if (!classes) return NULL;
    const node *cls = obj_get(classes, class_id);
    if (!cls) return NULL;
    const node *spells_list = obj_get(cls, "spells");
    if (!spells_list || spells_list->kind != N_ARR) return NULL;
    const node *spells_tree = obj_get(root, "spells");
    if (!spells_tree) return NULL;
    const node *sub = obj_get(spells_tree, class_id);
    if (!sub) return NULL;
    for (size_t i = 0; i < spells_list->u.arr.n; i++) {
        const node *id_n = spells_list->u.arr.items[i];
        if (!id_n || id_n->kind != N_STR) continue;
        const char *dot = strchr(id_n->u.sv, '.');
        const char *local = dot ? dot + 1 : id_n->u.sv;
        const node *spell = obj_get(sub, local);
        if (!spell) continue;
        const node *slot_n = obj_get(spell, "slot");
        if (!slot_n || slot_n->kind != N_INT) continue;
        if (slot_n->u.iv != slot) continue;
        const node *do_n = obj_get(spell, "do");
        if (!do_n) return NULL;
        return do_n;
    }
    return NULL;
}
