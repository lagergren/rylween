#include "res.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct {
    char *kind;
    char *id;
    char **values;
    size_t n_values;
} res_record;

static res_record *records;
static size_t n_records;

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

static void parse_line(char *buf, const char *path, int line) {
    const char *p = skip_ws(buf);
    if (!*p || *p == '\n' || *p == '\r') return;
    if (*p == '#' || (*p == '/' && p[1] == '/')) return;
    if (*p != '{') die(path, line, "expected '{'");
    p++;

    res_record rec = {0};
    p = skip_ws(p);
    while (*p && *p != '}') {
        p = skip_ws(p);
        char *key = parse_string(&p, path, line);
        p = skip_ws(p);
        if (*p != ':') die(path, line, "expected ':'");
        p++;
        p = skip_ws(p);

        if (strcmp(key, "kind") == 0) {
            rec.kind = parse_string(&p, path, line);
        } else if (strcmp(key, "id") == 0) {
            rec.id = parse_string(&p, path, line);
        } else if (strcmp(key, "values") == 0) {
            parse_string_array(&p, &rec.values, &rec.n_values, path, line);
        } else {
            skip_value(&p, path, line);
        }
        free(key);
        p = skip_ws(p);
        if (*p == ',') { p++; p = skip_ws(p); }
    }

    if (!rec.kind || !rec.id) die(path, line, "record missing kind or id");
    res_record *grown = realloc(records, (n_records + 1) * sizeof *records);
    if (!grown) die(path, line, "oom");
    records = grown;
    records[n_records++] = rec;
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
    for (size_t i = 0; i < n_records; i++) {
        if (strcmp(records[i].kind, "enum") == 0 &&
            strcmp(records[i].id, id) == 0) {
            if (index < 0 || (size_t) index >= records[i].n_values) {
                return "";
            }
            return records[i].values[index];
        }
    }
    return "";
}
