#include "runtime_cfg.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "compat.h"
#include "json5.h"
#include "log.h"

/* §24.F implementation. The cache is a tiny first-writer-wins string
   table: load $HOME/.rylween/runtime.json5 first, then data/
   runtime.json5; the personal config gets priority because keys it
   declared are skipped by the project-default loader. Env vars are
   not stored here — rcfg_get consults getenv directly so the
   harness's per-fixture exports always win. */

#define RCFG_MAX 64

typedef struct {
    char *key;
    char *val;
} rcfg_entry;

static rcfg_entry cache[RCFG_MAX];
static int cache_n = 0;
static int initialized = 0;

static void rcfg_close(void) {
    for (int i = 0; i < cache_n; ++i) {
        free(cache[i].key);
        free(cache[i].val);
        cache[i].key = NULL;
        cache[i].val = NULL;
    }
    cache_n = 0;
}

static void cache_put(const char *key, const char *val) {
    for (int i = 0; i < cache_n; ++i) {
        if (!strcmp(cache[i].key, key)) {
            return; /* first writer wins — preserves load-order priority */
        }
    }
    if (cache_n >= RCFG_MAX) {
        LOG_W("rcfg",
              "runtime config table full (%d entries); ignoring '%s'",
              RCFG_MAX, key);
        return;
    }
    cache[cache_n].key = strdup(key);
    cache[cache_n].val = strdup(val);
    if (!cache[cache_n].key || !cache[cache_n].val) {
        free(cache[cache_n].key);
        free(cache[cache_n].val);
        return;
    }
    cache_n++;
}

/* JSON5 scalar → owned char buffer suitable for cache_put. Returns 1
   on success; 0 with a one-shot LOG_W on a non-scalar value (the
   caller skips the key). buf must hold at least 32 bytes for int/
   bool stringification. */
static int scalar_to_str(const node *v, const char *path, const char *key,
                         char *buf, size_t buf_sz, const char **out) {
    switch (v->kind) {
        case N_STR:
            *out = v->u.sv;
            return 1;
        case N_INT:
            snprintf(buf, buf_sz, "%d", v->u.iv);
            *out = buf;
            return 1;
        case N_BOOL:
            *out = v->u.bv ? "1" : "0";
            return 1;
        default:
            LOG_W("rcfg",
                  "%s: '%s' must be a scalar (string / int / bool); "
                  "ignored",
                  path, key);
            return 0;
    }
}

/* Load one runtime.json5 file into the cache. Missing file is silent.
   A non-object root (e.g. an array) is a config error and gets a
   one-shot LOG_W; the file is otherwise dropped. json5_parse_file is
   fatal on I/O / syntax errors via res_die, which is the right policy
   here too — a malformed config should be loud, not silently ignored. */
static void load_one(const char *path) {
    if (access(path, F_OK) != 0) {
        return;
    }
    node *root = json5_parse_file(path);
    if (!root) {
        return;
    }
    if (root->kind != N_OBJ) {
        LOG_W("rcfg", "%s: top-level must be an object; ignored", path);
        node_free(root);
        return;
    }
    for (size_t i = 0; i < root->u.obj.n; ++i) {
        const char *k = root->u.obj.items[i].key;
        const node *v = root->u.obj.items[i].val;
        char buf[32];
        const char *vs = NULL;
        if (!scalar_to_str(v, path, k, buf, sizeof buf, &vs)) {
            continue;
        }
        cache_put(k, vs);
    }
    node_free(root);
}

static void init_once(void) {
    if (initialized) {
        return;
    }
    initialized = 1;
    atexit(rcfg_close);
    char path[512];
    ryl_state_path("runtime.json5", path, sizeof path);
    load_one(path);
    /* Project-default fallback. Not present in the repo; intentionally
       so — fixtures run with HOME=$(mktemp -d) which makes the
       personal path unreachable, and a tracked data/runtime.json5
       would leak project-wide defaults into every fixture run.
       Listed here for the rare case where a fork wants shared
       defaults checked in. */
    if (strcmp(path, "data/runtime.json5") != 0) {
        load_one("data/runtime.json5");
    }
}

const char *rcfg_get(const char *key) {
    const char *env = getenv(key);
    if (env && *env) {
        return env;
    }
    init_once();
    for (int i = 0; i < cache_n; ++i) {
        if (!strcmp(cache[i].key, key)) {
            return cache[i].val;
        }
    }
    return NULL;
}
