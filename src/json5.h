#ifndef JSON5_H__
#define JSON5_H__

#include <stddef.h>
#include <stdint.h>

/* JSON5 front-end: parses data/world.json5 and its $import shards into a
 * tagged `node` tree. Shared vocabulary between the parser (json5.c) and
 * the evaluator (eval.c) — the evaluator walks the same nodes directly,
 * rewriting N_STR nodes at path positions into N_PATH in place via
 * compile_world_tree (see eval.h).
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
    N_PATH,
} node_kind;

/* Compiled path form. Populated by the eval-layer compile pass; laid out
   here because it's a member of the node union. `actor` and `field` hold
   path_actor / path_field values (see eval.h). */
typedef struct {
    int8_t actor;
    int8_t field;
    int32_t index;
    char *local; /* owned; non-NULL iff actor == PA_LOCAL */
    int line;
} path_t;

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
        path_t path;
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
   parsed + recursively-resolved subtree from data/<relpath>. Cycles
   fatal. `src_path` is used in error messages for imports referenced at
   the top level of the passed-in tree. Returns the (possibly replaced)
   root. */
node *json5_resolve_imports(node *root, const char *src_path);

#endif
