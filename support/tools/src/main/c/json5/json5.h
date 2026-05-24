#ifndef JSON5_H__
#define JSON5_H__

#include <stddef.h>
#include <stdint.h>

/* JSON5 front-end: parses a JSON5 source file into a tagged `node` tree.
 * The active native bundle checks use strict JSON input, but keep this parser
 * vendored as a small C dependency for structural verification.
 *
 * Parser errors dispatch through res_die (declared in res.h) so all
 * path:line:msg messages share one format.
 */

typedef enum {
    N_NULL,
    N_BOOL,
    N_INT,
    N_STR,
    N_ARR,
    N_OBJ,
} node_kind;

typedef struct node_kv node_kv;
typedef struct node node;

struct node {
    node_kind kind;
    int line;
    union {
        int32_t bv;
        int32_t iv;
        char *sv;
        struct {
            node **items;
            size_t n;
        } arr;
        struct {
            node_kv *items;
            size_t n;
        } obj;
    } u;
};

struct node_kv {
    char *key;
    node *val;
};

node *node_new(node_kind k, int line);
void node_free(node *n);
const node *obj_get(const node *o, const char *key);

/* Parse a JSON5 source file into a node tree. Fatal on I/O or syntax
   errors (via res_die). Caller owns the returned tree. */
node *json5_parse_file(const char *path);

/* Walk the tree and splice every {"$import": "relpath"} object with the
   parsed + recursively-resolved subtree from game/src/main/resources/<relpath>. Cycles
   fatal. `src_path` is used in error messages for imports referenced at
   the top level of the passed-in tree. Returns the (possibly replaced)
   root. */
node *json5_resolve_imports(node *root, const char *src_path);

#endif
