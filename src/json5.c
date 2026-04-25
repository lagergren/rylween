/* JSON5 front-end for the Phase 6d world loader.
 *
 * Hand-rolled parser over a tagged node tree (node_kind / node — see
 * json5.h); also owns the $import directive resolver that splices
 * referenced shards into the tree. Emits all path:line:msg errors
 * through res_die so diagnostics stay uniform across the loader.
 *
 * Public entry points:
 *   json5_parse_file(path)         — parse one file into a node tree.
 *   json5_resolve_imports(root, p) — splice every {"$import": "..."}
 *                                    object with the shard's tree.
 *
 * Node helpers (node_new / node_free / obj_get) live here too so the
 * parser and evaluator share one allocation surface.
 */

#include "json5.h"
#include "res.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* ---------------------------------------------------------------- */
/*  Node helpers                                                    */
/* ---------------------------------------------------------------- */

node *node_new(node_kind k, int line) {
    node *n = calloc(1, sizeof *n);
    if (!n) {
        fprintf(stderr, "res: oom\n");
        exit(1);
    }
    n->kind = k;
    n->line = line;
    return n;
}

void node_free(node *n) {
    if (!n) {
        return;
    }
    switch (n->kind) {
        case N_STR:
            free(n->u.sv);
            break;
        case N_ARR:
            for (size_t i = 0; i < n->u.arr.n; i++) {
                node_free(n->u.arr.items[i]);
            }
            free(n->u.arr.items);
            break;
        case N_OBJ:
            for (size_t i = 0; i < n->u.obj.n; i++) {
                free(n->u.obj.items[i].key);
                node_free(n->u.obj.items[i].val);
            }
            free(n->u.obj.items);
            break;
        default:
            break;
    }
    free(n);
}

const node *obj_get(const node *o, const char *key) {
    if (!o || o->kind != N_OBJ) {
        return NULL;
    }
    for (size_t i = 0; i < o->u.obj.n; i++) {
        if (strcmp(o->u.obj.items[i].key, key) == 0) {
            return o->u.obj.items[i].val;
        }
    }
    return NULL;
}

/* ---------------------------------------------------------------- */
/*  Parser                                                          */
/* ---------------------------------------------------------------- */

typedef struct {
    const char *path;
    const char *src; /* owned by caller */
    const char *cur;
    int line;
} parser;

__attribute__((noreturn)) static void pdie(const parser *p, const char *msg) {
    res_die(p->path, p->line, msg);
}

static void p_skip_ws(parser *p) {
    for (;;) {
        unsigned char c = (unsigned char)*p->cur;
        if (c == ' ' || c == '\t' || c == '\r') {
            p->cur++;
        } else if (c == '\n') {
            p->cur++;
            p->line++;
        } else if (c == '/' && p->cur[1] == '/') {
            while (*p->cur && *p->cur != '\n') {
                p->cur++;
            }
        } else if (c == '/' && p->cur[1] == '*') {
            p->cur += 2;
            while (*p->cur && !(p->cur[0] == '*' && p->cur[1] == '/')) {
                if (*p->cur == '\n') {
                    p->line++;
                }
                p->cur++;
            }
            if (*p->cur) {
                p->cur += 2;
            }
        } else {
            break;
        }
    }
}

static int is_ident_start(int c) {
    return c == '_' || c == '$' || isalpha(c);
}
static int is_ident_cont(int c) {
    return c == '_' || c == '$' || isalnum(c);
}

static char *p_ident_raw(parser *p) {
    if (!is_ident_start((unsigned char)*p->cur)) {
        pdie(p, "expected identifier");
    }
    const char *s = p->cur;
    while (is_ident_cont((unsigned char)*p->cur)) {
        p->cur++;
    }
    size_t n = (size_t)(p->cur - s);
    char *out = malloc(n + 1);
    if (!out) {
        pdie(p, "oom");
    }
    memcpy(out, s, n);
    out[n] = 0;
    return out;
}

/* Quoted string supporting "..." and '...'. Escapes: \" \' \\ \n \t \r \b
   \f \/ and \uXXXX (basic; encodes >0x7f as UTF-8 up to U+FFFF). */
static char *p_quoted_string(parser *p) {
    char quote = *p->cur;
    if (quote != '"' && quote != '\'') {
        pdie(p, "expected string");
    }
    p->cur++;
    const char *s = p->cur;
    /* first pass: bound the output size at input length */
    size_t cap = 16;
    char *out = malloc(cap);
    if (!out) {
        pdie(p, "oom");
    }
    size_t n = 0;
#define PUSH(b)                      \
    do {                             \
        if (n + 4 >= cap) {          \
            cap *= 2;                \
            out = realloc(out, cap); \
            if (!out)                \
                pdie(p, "oom");      \
        }                            \
        out[n++] = (char)(b);        \
    } while (0)
    while (*s && *s != quote) {
        if (*s == '\n') {
            p->line++;
        }
        if (*s == '\\') {
            s++;
            switch (*s) {
                case '"':
                    PUSH('"');
                    s++;
                    break;
                case '\'':
                    PUSH('\'');
                    s++;
                    break;
                case '\\':
                    PUSH('\\');
                    s++;
                    break;
                case '/':
                    PUSH('/');
                    s++;
                    break;
                case 'n':
                    PUSH('\n');
                    s++;
                    break;
                case 't':
                    PUSH('\t');
                    s++;
                    break;
                case 'r':
                    PUSH('\r');
                    s++;
                    break;
                case 'b':
                    PUSH('\b');
                    s++;
                    break;
                case 'f':
                    PUSH('\f');
                    s++;
                    break;
                case 'u': {
                    s++;
                    unsigned v = 0;
                    for (int i = 0; i < 4; i++) {
                        int c = (unsigned char)s[i];
                        int d = (c >= '0' && c <= '9')   ? c - '0'
                                : (c >= 'a' && c <= 'f') ? c - 'a' + 10
                                : (c >= 'A' && c <= 'F') ? c - 'A' + 10
                                                         : -1;
                        if (d < 0) {
                            p->cur = s;
                            pdie(p, "bad \\u escape");
                        }
                        v = v * 16 + (unsigned)d;
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
                case '\n': /* line continuation */
                    p->line++;
                    s++;
                    break;
                default:
                    PUSH(*s);
                    if (*s) {
                        s++;
                    }
                    break;
            }
        } else {
            PUSH(*s);
            s++;
        }
    }
    if (*s != quote) {
        p->cur = s;
        pdie(p, "unterminated string");
    }
    PUSH(0);
    n--;
    p->cur = s + 1;
#undef PUSH
    return out;
}

static int32_t p_int(parser *p) {
    const char *s = p->cur;
    int neg = 0;
    if (*s == '+' || *s == '-') {
        neg = (*s == '-');
        s++;
    }
    if (!isdigit((unsigned char)*s)) {
        pdie(p, "expected number");
    }
    char *end;
    long v = strtol(p->cur, &end, 10);
    if (end == p->cur) {
        pdie(p, "expected number");
    }
    /* reject fractional / exponent for now — phase 6d has no float needs */
    if (*end == '.' || *end == 'e' || *end == 'E') {
        pdie(p, "floats not supported");
    }
    p->cur = end;
    (void)neg; /* strtol already handled sign */
    return (int32_t)v;
}

static node *p_value(parser *p);

static node *p_array(parser *p) {
    if (*p->cur != '[') {
        pdie(p, "expected '['");
    }
    node *a = node_new(N_ARR, p->line);
    p->cur++;
    size_t cap = 0;
    for (;;) {
        p_skip_ws(p);
        if (*p->cur == ']') {
            p->cur++;
            return a;
        }
        node *v = p_value(p);
        if (a->u.arr.n == cap) {
            cap = cap ? cap * 2 : 8;
            a->u.arr.items = realloc(a->u.arr.items, cap * sizeof *a->u.arr.items);
            if (!a->u.arr.items) {
                pdie(p, "oom");
            }
        }
        a->u.arr.items[a->u.arr.n++] = v;
        p_skip_ws(p);
        if (*p->cur == ',') {
            p->cur++;
            continue;
        }
        if (*p->cur == ']') {
            p->cur++;
            return a;
        }
        pdie(p, "expected ',' or ']'");
    }
}

static node *p_object(parser *p) {
    if (*p->cur != '{') {
        pdie(p, "expected '{'");
    }
    node *o = node_new(N_OBJ, p->line);
    p->cur++;
    size_t cap = 0;
    for (;;) {
        p_skip_ws(p);
        if (*p->cur == '}') {
            p->cur++;
            return o;
        }
        char *key;
        if (*p->cur == '"' || *p->cur == '\'') {
            key = p_quoted_string(p);
        } else if (is_ident_start((unsigned char)*p->cur)) {
            key = p_ident_raw(p);
        } else {
            pdie(p, "expected object key");
        }
        p_skip_ws(p);
        if (*p->cur != ':') {
            free(key);
            pdie(p, "expected ':'");
        }
        p->cur++;
        p_skip_ws(p);
        node *v = p_value(p);
        if (o->u.obj.n == cap) {
            cap = cap ? cap * 2 : 8;
            o->u.obj.items = realloc(o->u.obj.items, cap * sizeof *o->u.obj.items);
            if (!o->u.obj.items) {
                pdie(p, "oom");
            }
        }
        o->u.obj.items[o->u.obj.n].key = key;
        o->u.obj.items[o->u.obj.n].val = v;
        o->u.obj.n++;
        p_skip_ws(p);
        if (*p->cur == ',') {
            p->cur++;
            continue;
        }
        if (*p->cur == '}') {
            p->cur++;
            return o;
        }
        pdie(p, "expected ',' or '}'");
    }
}

static node *p_value(parser *p) {
    p_skip_ws(p);
    int line = p->line;
    unsigned char c = (unsigned char)*p->cur;
    if (c == '{') {
        return p_object(p);
    }
    if (c == '[') {
        return p_array(p);
    }
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
        if (strcmp(id, "true") == 0) {
            free(id);
            node *n = node_new(N_BOOL, line);
            n->u.bv = 1;
            return n;
        }
        if (strcmp(id, "false") == 0) {
            free(id);
            node *n = node_new(N_BOOL, line);
            n->u.bv = 0;
            return n;
        }
        if (strcmp(id, "null") == 0) {
            free(id);
            return node_new(N_NULL, line);
        }
        free(id);
        pdie(p, "unexpected identifier as value");
    }
    pdie(p, "unexpected character");
}

static char *slurp_file(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) {
        return NULL;
    }
    if (fseek(f, 0, SEEK_END) != 0) {
        fclose(f);
        return NULL;
    }
    long sz = ftell(f);
    if (sz < 0) {
        fclose(f);
        return NULL;
    }
    rewind(f);
    char *buf = malloc((size_t)sz + 1);
    if (!buf) {
        fclose(f);
        return NULL;
    }
    size_t n = fread(buf, 1, (size_t)sz, f);
    fclose(f);
    buf[n] = 0;
    return buf;
}

node *json5_parse_file(const char *path) {
    char *src = slurp_file(path);
    if (!src) {
        fprintf(stderr, "res: cannot read %s\n", path);
        exit(1);
    }
    parser p = {.path = path, .src = src, .cur = src, .line = 1};
    p_skip_ws(&p);
    node *root = p_value(&p);
    p_skip_ws(&p);
    if (*p.cur != 0) {
        res_die(path, p.line, "trailing content after root value");
    }
    free(src);
    return root;
}

/* ---------------------------------------------------------------- */
/*  $import resolver (docs/dsl.md §2.1)                             */
/* ---------------------------------------------------------------- */

/* Walk the tree; any object whose sole member is "$import": "relpath"
 * is replaced by the tree parsed from data/<relpath>. Cycles are fatal.
 * Only .json5 imports are supported here; .dat byte adapters (§3.7) are
 * deferred until the map-externalisation step.
 *
 * The `stack` list of currently-loading paths is passed through the
 * recursion so an import referring back to an ancestor fails fast with
 * a readable message.
 */
typedef struct import_frame {
    const char *path;
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
    if (wrote < 0 || (size_t)wrote >= sizeof full) {
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
    node *sub = json5_parse_file(full);
    import_frame frame = {.path = full, .prev = stack};
    return resolve_imports(sub, &frame, full);
}

static node *resolve_imports(node *n, import_frame *stack, const char *src_path) {
    if (!n) {
        return n;
    }
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

node *json5_resolve_imports(node *root, const char *src_path) {
    return resolve_imports(root, NULL, src_path);
}
