/* Resource loader: flat JSONL (legacy) + hierarchical JSON5 world file.
 *
 * Phase 6d lands the JSON5 side. Three candidate strategies existed:
 *   (a) vendor a full C JSON5 library (json5-c / similar)
 *   (b) use json-parser (strict JSON) + a JSON5 -> JSON preprocessor
 *   (c) hand-roll on top of the existing micro-JSON state machine
 *
 * We pick (c). Rationale:
 *   - Makefile has zero external deps; adding one is a build-system change
 *     that buys us <500 LOC of parser we'd have to audit anyway.
 *   - The existing parse_string / parse_number / skip_value helpers
 *     already cover most of what JSON would need; JSON5 over JSON is a
 *     small delta (line/block comments, trailing commas, unquoted ident
 *     keys, single-quote strings).
 *   - Hand-rolling keeps error reporting uniform ("res: path:line: msg")
 *     and lets the parser emit a tagged node tree that the Phase 6d
 *     evaluator (eval_cond / eval_effect / eval_expr, landing in a follow-
 *     up commit) can walk without a second pass or copy.
 *
 * The loader's contract (docs/dsl.md §4):
 *   res_load(path)          - flat JSONL legacy records (texts, items,
 *                             monsters, spells, terrain, sextext, enum).
 *   res_load_world(path)    - parses data/world.json5 into a tagged node
 *                             tree, resolves $import directives recursively
 *                             (cycles fatal; .json5 imports only for now,
 *                             .dat adapter per §3.7 is deferred), then
 *                             folds the `flags` and `items` subtrees into
 *                             the in-memory tables. JSON5 records shadow
 *                             JSONL records of the same id (§2.2).
 *
 * The full node tree is retained and exposed via res_world() for the
 * upcoming evaluator; `spells`, `classes`, `npcs`, `towns`, `monsters`,
 * `encounters`, `missions`, `map`, `texts`, `lib`, `meta` are parsed but
 * not yet folded into the legacy tables.
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

static ryl_item    items[RES_MAX_ITEM_ID + 1];
static ryl_monster monsters[RES_MAX_MONSTER_ID + 1];

#define RES_MAX_SPELL_SLOT 7
static ryl_spell priest_spells[RES_MAX_SPELL_SLOT + 1];
static ryl_spell mage_spells[RES_MAX_SPELL_SLOT + 1];

static char   *kar_tab[256];
static int32_t frg_tab[256];
static int     frg_tab_set[256];
static int32_t frg_default = 36;

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

static const char *skip_ws(const char *p) {
    while (*p == ' ' || *p == '\t') p++;
    return p;
}

static char *parse_string(const char **p, const char *path, int line) {
    if (**p != '"') die(path, line, "expected string");
    const char *s = *p + 1;
    char *out = malloc(strlen(s) + 1);
    if (!out) die(path, line, "oom");
    char *o = out;
    while (*s && *s != '"') {
        if (*s == '\\') {
            s++;
            switch (*s) {
                case '"':  *o++ = '"';  break;
                case '\\': *o++ = '\\'; break;
                case '/':  *o++ = '/';  break;
                case 'n':  *o++ = '\n'; break;
                case 't':  *o++ = '\t'; break;
                case 'r':  *o++ = '\r'; break;
                case 'b':  *o++ = '\b'; break;
                case 'f':  *o++ = '\f'; break;
                default:   *o++ = *s;   break;
            }
            if (*s) s++;
        } else {
            *o++ = *s++;
        }
    }
    if (*s != '"') die(path, line, "unterminated string");
    *o = 0;
    *p = s + 1;
    return out;
}

static int32_t parse_number(const char **p, const char *path, int line) {
    char *end;
    long v = strtol(*p, &end, 10);
    if (end == *p) die(path, line, "expected number");
    *p = end;
    return (int32_t) v;
}

static void skip_value(const char **p, const char *path, int line) {
    *p = skip_ws(*p);
    if (**p == '"') {
        char *s = parse_string(p, path, line);
        free(s);
    } else if (**p == '[' || **p == '{') {
        char open = **p;
        char close = (char) (open == '[' ? ']' : '}');
        int depth = 1;
        (*p)++;
        while (**p && depth > 0) {
            if (**p == '"') {
                char *s = parse_string(p, path, line);
                free(s);
            } else {
                if (**p == open) depth++;
                else if (**p == close) depth--;
                (*p)++;
            }
        }
    } else {
        while (**p && **p != ',' && **p != '}' && **p != ']' &&
               **p != ' ' && **p != '\t' && **p != '\n' && **p != '\r') {
            (*p)++;
        }
    }
}

static void parse_string_array(const char **p, char ***values, size_t *n,
                               const char *path, int line) {
    *p = skip_ws(*p);
    if (**p != '[') die(path, line, "expected '['");
    (*p)++;
    size_t cap = 0;
    *values = NULL;
    *n = 0;
    *p = skip_ws(*p);
    while (**p && **p != ']') {
        char *s = parse_string(p, path, line);
        if (*n == cap) {
            cap = cap ? cap * 2 : 8;
            char **grown = realloc(*values, cap * sizeof **values);
            if (!grown) die(path, line, "oom");
            *values = grown;
        }
        (*values)[(*n)++] = s;
        *p = skip_ws(*p);
        if (**p == ',') { (*p)++; *p = skip_ws(*p); }
    }
    if (**p != ']') die(path, line, "unterminated array");
    (*p)++;
}

typedef struct {
    char *key;
    char *str;
    int32_t num;
    int has_num;
    char **strs;
    size_t n_strs;
} kv;

static void free_kvs(kv *a, size_t n) {
    for (size_t i = 0; i < n; i++) {
        free(a[i].key);
        free(a[i].str);
        if (a[i].strs) {
            for (size_t j = 0; j < a[i].n_strs; j++) free(a[i].strs[j]);
            free(a[i].strs);
        }
    }
    free(a);
}

static const kv *find_kv(const kv *a, size_t n, const char *key) {
    for (size_t i = 0; i < n; i++) {
        if (strcmp(a[i].key, key) == 0) return &a[i];
    }
    return NULL;
}

static const char *kv_str(const kv *a, size_t n, const char *key, const char *def) {
    const kv *e = find_kv(a, n, key);
    return (e && e->str) ? e->str : def;
}

static int32_t kv_int(const kv *a, size_t n, const char *key, int32_t def) {
    const kv *e = find_kv(a, n, key);
    return (e && e->has_num) ? e->num : def;
}

static void handle_enum(const kv *kvs, size_t n, const char *path, int line) {
    const char *id = kv_str(kvs, n, "id", NULL);
    const kv *v = find_kv(kvs, n, "values");
    if (!id || !v || !v->strs) die(path, line, "enum missing id or values");
    enum_rec *grown = realloc(enums, (n_enums + 1) * sizeof *enums);
    if (!grown) die(path, line, "oom");
    enums = grown;
    enum_rec *r = &enums[n_enums++];
    r->id = strdup(id);
    r->n_values = v->n_strs;
    r->values = malloc(v->n_strs * sizeof *r->values);
    for (size_t i = 0; i < v->n_strs; i++) {
        r->values[i] = strdup(v->strs[i]);
    }
}

static void handle_item(const kv *kvs, size_t n, const char *path, int line) {
    int32_t id = kv_int(kvs, n, "id", -1);
    if (id < 0 || id > RES_MAX_ITEM_ID) die(path, line, "item id out of range");
    ryl_item *it = &items[id];
    free((void *) it->name);
    it->name = strdup(kv_str(kvs, n, "name", "-"));
    it->cost = kv_int(kvs, n, "cost", 0);
    it->damage = kv_int(kvs, n, "damage", 0);
}

static void handle_monster(const kv *kvs, size_t n, const char *path, int line) {
    int32_t id = kv_int(kvs, n, "id", -1);
    if (id < 0 || id > RES_MAX_MONSTER_ID) die(path, line, "monster id out of range");
    ryl_monster *m = &monsters[id];
    free((void *) m->name);
    m->name = strdup(kv_str(kvs, n, "name", ""));
    m->n1 = kv_int(kvs, n, "n1", 0);
    m->n2 = kv_int(kvs, n, "n2", 0);
    m->h1 = kv_int(kvs, n, "h1", 0);
    m->h2 = kv_int(kvs, n, "h2", 0);
    m->ws = kv_int(kvs, n, "ws", 0);
    m->sp = kv_int(kvs, n, "sp", 0);
    m->im = kv_int(kvs, n, "im", 0);
}

static void handle_terrain(const kv *kvs, size_t n, const char *path, int line) {
    const char *glyph = kv_str(kvs, n, "glyph", NULL);
    if (!glyph || !glyph[0]) die(path, line, "terrain missing glyph");
    unsigned char g = (unsigned char) glyph[0];
    const char *kar = kv_str(kvs, n, "kar", NULL);
    if (kar) {
        free(kar_tab[g]);
        kar_tab[g] = strdup(kar);
    }
    const kv *fc = find_kv(kvs, n, "frg");
    if (fc && fc->has_num) {
        frg_tab[g] = fc->num;
        frg_tab_set[g] = 1;
    }
}

static void handle_spell(const kv *kvs, size_t n, const char *path, int line) {
    const char *cls = kv_str(kvs, n, "class", NULL);
    int32_t slot = kv_int(kvs, n, "slot", -1);
    if (!cls) die(path, line, "spell missing class");
    if (slot < 1 || slot > RES_MAX_SPELL_SLOT) die(path, line, "spell slot out of range");
    int cid = pm_class_id(cls);
    ryl_spell *tab;
    if (cid == PC_PRIEST)    tab = priest_spells;
    else if (cid == PC_MAGE) tab = mage_spells;
    else die(path, line, "spell class must be priest or mage");
    ryl_spell *s = &tab[slot];
    s->slot = slot;
    free((void *) s->id);
    free((void *) s->name);
    s->id = strdup(kv_str(kvs, n, "id", ""));
    s->name = strdup(kv_str(kvs, n, "name", ""));
    s->base_cost = kv_int(kvs, n, "base_cost", 0);
    const char *eff = kv_str(kvs, n, "effect", "");
    if      (strcmp(eff, "heal")        == 0) s->effect = SE_HEAL;
    else if (strcmp(eff, "restoration") == 0) s->effect = SE_RESTORATION;
    else if (strcmp(eff, "rebirth")     == 0) s->effect = SE_REBIRTH;
    else if (strcmp(eff, "banish")      == 0) s->effect = SE_BANISH;
    else if (strcmp(eff, "revelation")  == 0) s->effect = SE_REVELATION;
    else if (strcmp(eff, "teleport")    == 0) s->effect = SE_TELEPORT;
    else if (strcmp(eff, "earthquake")  == 0) s->effect = SE_EARTHQUAKE;
    else if (strcmp(eff, "fire")        == 0) s->effect = SE_FIRE;
    else if (strcmp(eff, "confusion")   == 0) s->effect = SE_CONFUSION;
    else if (strcmp(eff, "death")       == 0) s->effect = SE_DEATH;
    else if (strcmp(eff, "hold")        == 0) s->effect = SE_HOLD;
    else if (strcmp(eff, "antimagic")   == 0) s->effect = SE_ANTIMAGIC;
    else die(path, line, "spell has unknown effect");
}

static void handle_text(const kv *kvs, size_t n, const char *path, int line) {
    const char *id = kv_str(kvs, n, "id", NULL);
    const char *text = kv_str(kvs, n, "text", NULL);
    if (!id || !text) die(path, line, "text missing id or text");
    text_rec *grown = realloc(texts, (n_texts + 1) * sizeof *texts);
    if (!grown) die(path, line, "oom");
    texts = grown;
    text_rec *r = &texts[n_texts++];
    r->id = strdup(id);
    r->text = strdup(text);
}

static void handle_sextext(const kv *kvs, size_t n, const char *path, int line) {
    int32_t sex = kv_int(kvs, n, "sex", -1);
    if (sex < 1 || sex > 2) die(path, line, "sextext sex must be 1 or 2");
    const char *subj = kv_str(kvs, n, "subject", "");
    const char *obj  = kv_str(kvs, n, "object", "");
    const char *poss = kv_str(kvs, n, "possessive", "");
    free(sextext_tab[sex][1]); sextext_tab[sex][1] = strdup(subj);
    free(sextext_tab[sex][2]); sextext_tab[sex][2] = strdup(obj);
    free(sextext_tab[sex][3]); sextext_tab[sex][3] = strdup(poss);
}

static void parse_line(char *buf, const char *path, int line) {
    const char *p = skip_ws(buf);
    if (!*p || *p == '\n' || *p == '\r') return;
    if (*p == '#' || (*p == '/' && p[1] == '/')) return;
    if (*p != '{') die(path, line, "expected '{'");
    p++;

    kv *kvs = NULL;
    size_t nk = 0, cap = 0;
    p = skip_ws(p);
    while (*p && *p != '}') {
        p = skip_ws(p);
        char *key = parse_string(&p, path, line);
        p = skip_ws(p);
        if (*p != ':') die(path, line, "expected ':'");
        p++;
        p = skip_ws(p);

        if (nk == cap) {
            cap = cap ? cap * 2 : 8;
            kv *grown = realloc(kvs, cap * sizeof *kvs);
            if (!grown) die(path, line, "oom");
            kvs = grown;
        }
        kv *e = &kvs[nk++];
        memset(e, 0, sizeof *e);
        e->key = key;

        if (*p == '"') {
            e->str = parse_string(&p, path, line);
        } else if (*p == '[') {
            parse_string_array(&p, &e->strs, &e->n_strs, path, line);
        } else if ((*p >= '0' && *p <= '9') || *p == '-') {
            e->num = parse_number(&p, path, line);
            e->has_num = 1;
        } else {
            skip_value(&p, path, line);
        }
        p = skip_ws(p);
        if (*p == ',') { p++; p = skip_ws(p); }
    }

    const char *kind = kv_str(kvs, nk, "kind", NULL);
    if (!kind) die(path, line, "record missing kind");
    if      (strcmp(kind, "enum")    == 0) handle_enum(kvs, nk, path, line);
    else if (strcmp(kind, "item")    == 0) handle_item(kvs, nk, path, line);
    else if (strcmp(kind, "monster") == 0) handle_monster(kvs, nk, path, line);
    else if (strcmp(kind, "terrain") == 0) handle_terrain(kvs, nk, path, line);
    else if (strcmp(kind, "spell")   == 0) handle_spell(kvs, nk, path, line);
    else if (strcmp(kind, "sextext") == 0) handle_sextext(kvs, nk, path, line);
    else if (strcmp(kind, "text")    == 0) handle_text(kvs, nk, path, line);
    /* unknown kinds are tolerated */

    free_kvs(kvs, nk);
}

void res_load(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) {
        fprintf(stderr, "res: cannot open %s\n", path);
        exit(1);
    }
    char buf[4096];
    int line = 0;
    while (fgets(buf, sizeof buf, f)) {
        line++;
        parse_line(buf, path, line);
    }
    fclose(f);
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

const char *res_text(const char *id) {
    for (size_t i = 0; i < n_texts; i++) {
        if (strcmp(texts[i].id, id) == 0) return texts[i].text;
    }
    return "";
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

int32_t res_frg(char glyph) {
    unsigned char g = (unsigned char) glyph;
    return frg_tab_set[g] ? frg_tab[g] : frg_default;
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

/* -------- items subtree (shadows JSONL; docs/dsl.md §2.2) --------
   For this commit we honor only the legacy fields the runtime reads
   today: id, name, cost, damage. The richer kind/effect/wieldable_by
   schema parses fine (preserved in world_tree) but is inert until the
   evaluator lands. */
static void fold_items(const node *items_node, const char *path) {
    if (!items_node) return;
    if (items_node->kind != N_OBJ) die(path, items_node->line, "items: expected object");
    for (size_t i = 0; i < items_node->u.obj.n; i++) {
        const char *sym  = items_node->u.obj.items[i].key;
        const node *rec  = items_node->u.obj.items[i].val;
        if (rec->kind != N_OBJ) die(path, rec->line, "items: entry must be object");
        const node *id_n = obj_get(rec, "id");
        if (!id_n || id_n->kind != N_INT) die(path, rec->line, "items: missing 'id'");
        int32_t id = id_n->u.iv;
        if (id < 0 || id > RES_MAX_ITEM_ID) die(path, id_n->line, "items: id out of range");
        ryl_item *it = &items[id];
        free((void *) it->name);
        const node *name_n = obj_get(rec, "name");
        if (name_n && name_n->kind == N_STR) {
            it->name = strdup(name_n->u.sv);
        } else {
            /* fall back to the subtree key, capitalised */
            it->name = strdup(sym);
        }
        const node *cost_n = obj_get(rec, "cost");
        it->cost = (cost_n && cost_n->kind == N_INT) ? cost_n->u.iv : 0;
        const node *dmg_n = obj_get(rec, "damage");
        it->damage = (dmg_n && dmg_n->kind == N_INT) ? dmg_n->u.iv : 0;
    }
}

/* -------- classes + spells subtree (shadows JSONL; docs/dsl.md §2.2) --
   Populates the legacy ryl_spell tables from the new world tree so the
   cast menu still has name/base_cost to render. The spell's enum effect
   is set to SE_NONE because dispatch now runs through res_spell_node
   → eval_effect on the `do` body; the switch(s->effect) in strid.c is
   only reached when no DSL body exists. */
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
        for (size_t i = 0; i < list->u.arr.n; i++) {
            const node *id_n = list->u.arr.items[i];
            if (!id_n || id_n->kind != N_STR) die(path, list->line, "classes.spells: id must be string");
            const node *spell = obj_get(spells_node, id_n->u.sv);
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
            s->effect = SE_NONE;
        }
    }
}

void res_load_world(const char *path) {
    /* Tolerant: missing file = no world tree, legacy JSONL-only. Corrupt
       file = fatal (parser die()s with path:line:msg). */
    FILE *probe = fopen(path, "rb");
    if (!probe) return;
    fclose(probe);

    node *root = parse_file(path);
    root = resolve_imports(root, NULL, path);
    if (root->kind != N_OBJ) die(path, root->line, "world root must be object");

    /* Fold shadowable subtrees into the legacy tables before the compile
       pass rewrites paths; folding only reads leaf ints/strings so it is
       unaffected by either order, but doing it first keeps the world_tree
       handoff to the evaluator purely about behavior, not data. */
    fold_flags(obj_get(root, "flags"), path);
    fold_items(obj_get(root, "items"), path);
    fold_spells(obj_get(root, "classes"), obj_get(root, "spells"), path);

    /* Walk every effect / cond / expr array in the tree and rewrite path
       strings (e.g. "caster.hp", "$dmg") in place to N_PATH. After this,
       the evaluator hot path does zero strcmp on actor/field names. */
    compile_world_tree(root, path);
    world_tree = root;
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
    for (size_t i = 0; i < spells_list->u.arr.n; i++) {
        const node *id_n = spells_list->u.arr.items[i];
        if (!id_n || id_n->kind != N_STR) continue;
        const node *spell = obj_get(spells_tree, id_n->u.sv);
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
