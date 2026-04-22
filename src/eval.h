#ifndef EVAL_H__
#define EVAL_H__

/* Phase 6d evaluator types and entry points.
 *
 * The world-file parser (res.c) builds a tagged `node` tree; the evaluator
 * walks that tree to execute spells / dialogue / town services. Paths like
 * "caster.hp" / "$dmg" / "target.inventory[3]" are compiled at load time
 * (by compile_world_tree) into a packed path_t stored in-place as N_PATH
 * nodes, so the eval hot path does zero strcmp on actor/field names.
 *
 * Only eval_expr / eval_cond land in this commit. eval_effect, the binding-
 * frame machinery, and res_spell_node() follow in the next commit.
 */

#include "ryl.h"

typedef enum {
    N_NULL,
    N_BOOL,
    N_INT,
    N_STR,
    N_ARR,
    N_OBJ,
    N_PATH,
} node_kind;

typedef enum {
    PA_NONE,
    PA_CASTER,
    PA_TARGET,
    PA_SPEAKER,
    PA_SELF,
    PA_STATE,
    PA_PARTY,
    PA_LOCAL,
    PA_NPC,
} path_actor;

typedef enum {
    PF_NONE,
    PF_HP,
    PF_H,
    PF_MA,
    PF_WI,
    PF_LV,
    PF_WS,
    PF_CH,
    PF_AC,
    PF_DEAD,
    PF_NAME,
    PF_CL,
    PF_GOLD,
    PF_VISITED,
    PF_INVENTORY,
    PF_BANISHED,
    PF_X,
    PF_Y,
    PF_FIRESHIELD,
    PF_BRACELETS,
    PF_MASTERCROWN,
    PF_JAZZYFOOT,
    PF_SILVERKEY,
} path_field;

/* Compiled path. Hot-path reads dispatch on actor + field; no strcmp. */
typedef struct {
    int8_t actor;  /* path_actor */
    int8_t field;  /* path_field */
    int32_t index; /* subscript for inventory[N] / npc[N] */
    char *local;   /* owned; non-NULL iff actor == PA_LOCAL */
    int line;      /* source line for runtime errors */
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

/* Actor binding. Each of the four actor slots (caster / target / speaker /
   self) can be unbound, bound to a party member, or (for target after
   `ask_target "monster_alive"`) bound to a combat-scope monster index.
   Additional kinds (npc) land when dialogue migrates. */
typedef enum {
    AB_NONE = 0,
    AB_PARTY,
    AB_MONSTER,
} actor_kind;

typedef struct {
    int kind;
    union {
        pm_t party;
        int mon_index; /* 1..noc — reads env->mh[] / env->deadc[] */
    } u;
} actor_bind;

/* Evaluator environment. Four actor bindings, a linear $locals array, and
   a small setjmp stack used by `seq` to let `require` abort just the
   enclosing seq. Combat context (noc / mh / deadc / fn) is populated by
   the encounter() caller before eval_effect so actor = monster reads /
   writes reach the right ints without the evaluator knowing combat. */
#include <setjmp.h>

typedef struct env_t env_t;
struct env_t {
    actor_bind caster;
    actor_bind target;
    actor_bind speaker;
    actor_bind self;
    struct {
        char *name;
        int32_t val;
    } *locals;
    size_t n_locals;
    size_t cap_locals;

    int noc;
    int *mh;
    int *deadc;
    const char *fn;

    jmp_buf *seq_stack[8];
    int seq_top;
};

void env_init(env_t *e);
void env_free(env_t *e);
void env_set_local(env_t *e, const char *name, int32_t val);
void env_bind_caster_party(env_t *e, pm_t pm);
void env_bind_combat(env_t *e, int noc, int *mh, int *deadc, const char *fn);

/* Compile pass: walks `root` post-import-resolution, recursively rewriting
   N_STR nodes at path positions into N_PATH. `src_path` is used in error
   messages. Unknown ops in effect/cond/expr array position are fatal. */
void compile_world_tree(node *root, const char *src_path);

int32_t eval_expr(const node *n, env_t *e);
int eval_cond(const node *n, env_t *e);
void eval_effect(const node *n, env_t *e);

#endif
