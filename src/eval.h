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

#include "json5.h"
#include "ryl.h"

/* Path actor / field enums. path_t lives in json5.h because it's a
   member of the node union; the actor / field constants are evaluator
   vocabulary and live here. */
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
void env_bind_caster_party(env_t *e, pm_t pm);
void env_bind_combat(env_t *e, int noc, int *mh, int *deadc, const char *fn);

/* Compile pass: walks `root` post-import-resolution, recursively rewriting
   N_STR nodes at path positions into N_PATH. `src_path` is used in error
   messages. Unknown ops in effect/cond/expr array position are fatal. */
void compile_world_tree(node *root, const char *src_path);

void eval_effect(const node *n, env_t *e);

/* Expression entry point. Exposed so res.c's named-formula helper can
   evaluate a preparsed expression node with a freshly-seeded local. */
int32_t eval_expr(const node *n, env_t *e);

/* Insert / overwrite one named local in the env's linear locals array.
   Used by the formula helper to seed the one input variable before
   calling eval_expr. */
void env_set_local(env_t *e, const char *name, int32_t val);

#endif
