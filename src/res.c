#include "res.h"
#include "ryl.h"

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
    else if (strcmp(eff, "frost")       == 0) s->effect = SE_FROST;
    else if (strcmp(eff, "fire")        == 0) s->effect = SE_FIRE;
    else if (strcmp(eff, "confusion")   == 0) s->effect = SE_CONFUSION;
    else if (strcmp(eff, "death")       == 0) s->effect = SE_DEATH;
    else if (strcmp(eff, "hold")        == 0) s->effect = SE_HOLD;
    else if (strcmp(eff, "antimagic")   == 0) s->effect = SE_ANTIMAGIC;
    else die(path, line, "spell has unknown effect");
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
