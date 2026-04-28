/* §16.4 + §17-E + §17-F dialogue / mission script interpreter.
   Public contract: script.h. */

#include "script.h"

#include "battle.h"
#include "compat.h"
#include "driv.h"
#include "dungeon.h"
#include "gfx.h"
#include "inventory.h"
#include "json5.h"
#include "party.h"
#include "primitives.h"
#include "log.h"
#include "res.h"
#include "speak.h"
#include "state.h"
#include "subs.h"
#include "towne.h"
#include "wld.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* §22 Lane A: file-static pointer to the active in-combat ctx. Set by
   play_spell_body and cleared on its way out so the surrounding
   battle.c stack frame's mh[] / deadc[] live exactly as long as the
   spell body runs. Out-of-combat callers (priest path via subs.c)
   leave it NULL — monster_* exprs and ask_target monster_alive die
   loudly when that's the case rather than touching freed memory. */
static const struct script_combat_ctx *g_combat_ctx = NULL;

/* Build the parent-path prefix for resolving relative {screen: 'name'}
   text ids. Script id "speak.erethor.first_visit" -> "speak.erethor".
   Writes into `out` (bounded); caller guarantees enough room. */
static void script_parent(const char *script_id, char *out, size_t out_sz) {
    const char *last = strrchr(script_id, '.');
    if (!last) {
        if (out_sz > 0) {
            out[0] = 0;
        }
        return;
    }
    size_t len = (size_t)(last - script_id);
    if (len >= out_sz) {
        len = out_sz - 1;
    }
    memcpy(out, script_id, len);
    out[len] = 0;
}

static __attribute__((noreturn)) void die_at(const char *script_id, size_t idx,
                                             const char *msg) {
    char buf[256];
    snprintf(buf, sizeof buf, "play_script '%s' op %zu: %s", script_id, idx, msg);
    ryl_die(buf);
}

/* Resolve a {screen|emit|prompt|...: '<name>'} operand to its fully-
   qualified text id. Used by do_screen, do_emit, menu_branch prompt,
   do_input_number prompt_screen, and do_reward's cant_carry /
   picked_up fall-outs; shared helper so every site takes the same
   "text id too long" diagnostic.

   Resolution order:
     1. If <name> contains '.', treat as absolute — parent is
        ignored. Lets a flow reference shared atoms like
        `common.yes` without duplicating them.
     2. Probe "<script_id>.<name>" against res_text_exists. Flows
        whose texts are nested under the script id (towne.church,
        missions.bandits, towne.smith) resolve here on the first
        try.
     3. Fall back to "<parent>.<name>". Flows whose texts share an
        atom namespace across multiple scripts (speak.merchant.*
        scripts all drawing from texts.speak.merchant.*) land
        here — parent strips the flow-suffix segment so the shared
        atoms resolve.
     4. Absent a parent too, just the bare name.

   Never returns an existence failure — if nothing matches, the
   fallback string is still written. tx_fmt turns a missing text
   into "", and the blank screen is the visible signal that a
   name is misspelled. */
static void resolve_text_id(const char *parent, const char *name, char *out,
                            size_t out_sz, const char *script_id, size_t idx,
                            const char *what) {
    int n;
    if (strchr(name, '.') != NULL) {
        n = snprintf(out, out_sz, "%s", name);
    } else if (script_id && script_id[0]) {
        char probe[256];
        int pn = snprintf(probe, sizeof probe, "%s.%s", script_id, name);
        if (pn > 0 && pn < (int)sizeof probe && res_text_exists(probe)) {
            n = snprintf(out, out_sz, "%s", probe);
        } else if (parent && parent[0]) {
            n = snprintf(out, out_sz, "%s.%s", parent, name);
        } else {
            n = snprintf(out, out_sz, "%s", name);
        }
    } else if (parent && parent[0]) {
        n = snprintf(out, out_sz, "%s.%s", parent, name);
    } else {
        n = snprintf(out, out_sz, "%s", name);
    }
    if (n < 0 || n >= (int)out_sz) {
        char buf[64];
        snprintf(buf, sizeof buf, "%s: text id too long", what);
        die_at(script_id, idx, buf);
    }
}

/* §17-F control-flow unwinding. `play_ops` threads these through every
   nested-body op so `break` / `continue` from deep inside a menu_branch
   / if correctly unwinds to the enclosing `loop`. */
typedef enum {
    OPS_NORMAL = 0, /* finished naturally — advance to next op */
    OPS_BREAK,      /* break: exit innermost loop */
    OPS_CONTINUE,   /* continue: restart innermost loop */
} ops_result;

/* §17-F runtime-binding environment. One rt_env is instantiated per
   play_script call and threaded through every nested body. Holds
   named values bound at runtime by `let`, `input_number`,
   `menu_branch key_var`, for_member / for_inventory loop vars (all
   RT_INT), and `engine_bind` (RT_STR). Each entry always exposes a
   string view via `sv` — merged into the ryl_bind list handed to
   tx_fmt / bind_expand so `{placeholder}` expansion sees runtime
   names. RT_INT entries additionally expose `iv` for expr_var's
   `{var:'name'}` lookup; RT_STR entries are expr_var-fatal (engine_
   bind values are only for text substitution). sv is sized for the
   longest res-layer string the registry returns (item names, town
   names, enum values are all well under 48 chars). */
enum { RT_ENV_CAP = 32,
       RT_SV_CAP = 48 };

typedef enum {
    RT_INT = 0,
    RT_STR,
} rt_kind;

typedef struct {
    const char *name; /* borrowed — points into the AST (N_STR->sv)
                          or a string literal; never freed by rt_env */
    rt_kind kind;
    int32_t iv;         /* integer view — read by expr_var; meaningless
                          for RT_STR */
    char sv[RT_SV_CAP]; /* string view — merged into tx_fmt bindings */
} rt_bind;

typedef struct {
    rt_bind items[RT_ENV_CAP];
    size_t n;
} rt_env;

/* Replace-or-append an (name, int) binding. New bindings shadow
   caller ryl_bind entries with the same name (merge_bindings places
   rt_env first). Scanning newest-first means `rt_bind_set` updates
   the innermost scope when a for_* loop has pushed a same-named
   shadow — matching expr_var's newest-first read path. A prior RT_
   STR binding at the same name is replaced outright (kind flips to
   RT_INT). Fatal on overflow of the 32-entry cap. */
static void rt_bind_set(rt_env *env, const char *name, int32_t v,
                        const char *script_id, size_t idx) {
    for (size_t i = env->n; i > 0; i--) {
        if (strcmp(env->items[i - 1].name, name) == 0) {
            env->items[i - 1].kind = RT_INT;
            env->items[i - 1].iv = v;
            snprintf(env->items[i - 1].sv, sizeof env->items[i - 1].sv, "%d",
                     (int)v);
            return;
        }
    }
    if (env->n >= RT_ENV_CAP) {
        die_at(script_id, idx, "rt_env full (32-binding cap)");
    }
    env->items[env->n].name = name;
    env->items[env->n].kind = RT_INT;
    env->items[env->n].iv = v;
    snprintf(env->items[env->n].sv, sizeof env->items[env->n].sv, "%d", (int)v);
    env->n++;
}

/* As rt_bind_set but stores a single-char string view (`key_var`).
   iv becomes the ASCII code so expressions referencing the key as
   an integer still work. Kind is still RT_INT — the single-char sv
   is incidental; expr_var against it returns the ASCII code. */
static void rt_bind_set_char(rt_env *env, const char *name, char c,
                             const char *script_id, size_t idx) {
    for (size_t i = env->n; i > 0; i--) {
        if (strcmp(env->items[i - 1].name, name) == 0) {
            env->items[i - 1].kind = RT_INT;
            env->items[i - 1].iv = (int32_t)(unsigned char)c;
            env->items[i - 1].sv[0] = c;
            env->items[i - 1].sv[1] = 0;
            return;
        }
    }
    if (env->n >= RT_ENV_CAP) {
        die_at(script_id, idx, "rt_env full (32-binding cap)");
    }
    env->items[env->n].name = name;
    env->items[env->n].kind = RT_INT;
    env->items[env->n].iv = (int32_t)(unsigned char)c;
    env->items[env->n].sv[0] = c;
    env->items[env->n].sv[1] = 0;
    env->n++;
}

/* §17-F step 5: set a string-kind binding (engine_bind result). The
   string is copied into sv; caller guarantees it fits (fatal on
   overflow so silent truncation never hides a mis-sized registry
   entry). Replaces any prior same-name binding in place regardless
   of its previous kind. */
static void rt_bind_set_str(rt_env *env, const char *name, const char *s,
                            const char *script_id, size_t idx) {
    size_t len = strlen(s);
    if (len >= RT_SV_CAP) {
        char buf[128];
        snprintf(buf, sizeof buf,
                 "rt_bind_set_str: value for '%s' exceeds %d-char cap", name,
                 (int)(RT_SV_CAP - 1));
        die_at(script_id, idx, buf);
    }
    for (size_t i = env->n; i > 0; i--) {
        if (strcmp(env->items[i - 1].name, name) == 0) {
            env->items[i - 1].kind = RT_STR;
            env->items[i - 1].iv = 0;
            memcpy(env->items[i - 1].sv, s, len + 1);
            return;
        }
    }
    if (env->n >= RT_ENV_CAP) {
        die_at(script_id, idx, "rt_env full (32-binding cap)");
    }
    env->items[env->n].name = name;
    env->items[env->n].kind = RT_STR;
    env->items[env->n].iv = 0;
    memcpy(env->items[env->n].sv, s, len + 1);
    env->n++;
}

/* §17-F step 4 scope machinery. `rt_env_mark` captures the current
   binding count so a for_* loop can rewind everything pushed during
   its body, including the loop var. `rt_bind_push_int` always
   appends a new entry (even when a same-named binding exists below),
   so nested fors with the same loop-var name cleanly shadow. Per-
   iteration updates go through the returned slot index with
   `rt_bind_update_int` to avoid the newest-first name scan. */
static size_t rt_env_mark(const rt_env *env) {
    return env->n;
}

static void rt_env_rewind(rt_env *env, size_t mark) {
    env->n = mark;
}

static size_t rt_bind_push_int(rt_env *env, const char *name, int32_t v,
                               const char *script_id, size_t idx) {
    if (env->n >= RT_ENV_CAP) {
        die_at(script_id, idx, "rt_env full (32-binding cap)");
    }
    size_t slot = env->n;
    env->items[slot].name = name;
    env->items[slot].kind = RT_INT;
    env->items[slot].iv = v;
    snprintf(env->items[slot].sv, sizeof env->items[slot].sv, "%d", (int)v);
    env->n++;
    return slot;
}

static void rt_bind_update_int(rt_env *env, size_t slot, int32_t v) {
    env->items[slot].kind = RT_INT;
    env->items[slot].iv = v;
    snprintf(env->items[slot].sv, sizeof env->items[slot].sv, "%d", (int)v);
}

/* Build a flat ryl_bind list that overlays rt_env entries on top of
   the caller-supplied `bindings`. Rt_env comes first (newest entry
   at the front so it shadows older duplicates), then caller bindings
   (unchanged order), terminated by a {NULL, NULL} sentinel. Returns
   the merged list when env has entries, else returns `caller`
   unchanged — callers can pass the result straight to tx_fmt /
   bind_expand. `scratch` must have room for env->n + caller_count +
   1 entries; fatal on overflow. */
static const ryl_bind *merge_bindings(const ryl_bind *caller,
                                      const rt_env *env,
                                      ryl_bind *scratch, size_t scratch_cap,
                                      const char *script_id, size_t idx) {
    if (!env || env->n == 0) {
        return caller;
    }
    size_t k = 0;
    for (size_t i = env->n; i > 0; i--) {
        if (k + 1 >= scratch_cap) {
            die_at(script_id, idx, "merge_bindings: scratch overflow");
        }
        scratch[k].name = env->items[i - 1].name;
        scratch[k].value = env->items[i - 1].sv;
        k++;
    }
    for (const ryl_bind *b = caller; b && b->name; b++) {
        if (k + 1 >= scratch_cap) {
            die_at(script_id, idx, "merge_bindings: scratch overflow");
        }
        scratch[k++] = *b;
    }
    scratch[k].name = NULL;
    scratch[k].value = NULL;
    return scratch;
}

/* Forward declarations: play_ops recurses into do_if / do_menu_branch
   / do_loop bodies. `parent` matches do_screen's derivation so nested
   screens resolve against the same res_text tree as the enclosing
   script. eval_expr is forward-declared so the helpers below can call
   it without worrying about definition order. */
static ops_result play_ops(const node *ops, const char *script_id,
                           const char *parent, const ryl_bind *bindings,
                           rt_env *env);
static int32_t eval_expr(const node *n, const char *script_id, size_t idx,
                         const ryl_bind *bindings, const rt_env *env);

/* §17-F step 2: expression evaluator.
 *
 *   expr ::= <int>                       literal
 *          | { flag:   <expr-or-str> }   state.p[cpl].flag[slot]
 *          | { gold:   <any> }           state.p[cpl].gold
 *          | { torches: <any> }          state.p[cpl].torches
 *          | { food:   <any> }           state.p[cpl].food
 *          | { twn:    <any> }           state.twn
 *          | { hour_of_day: <any> }      state.hour_of_day
 *          | { cpl:    <any> }           state.cpl
 *          | { random: [ <expr>, <expr> ] }  ryl_random(lo, hi)  inclusive
 *          | { chance: <expr> }          ryl_chance(pct) -> 0/1
 *          | { inventory: <expr> }       state.p[cpl].inventory[slot]
 *          | { member:   <expr>,
 *              field:    '<name>' }      state.p[cpl].<field>[idx]
 *          | { var:      '<name>' }      runtime-bound (step 3+) or const
 *                                        binding (atoi'd)
 *          | { add:  [a, b] } .. { sub | mul | div | mod | min | max }
 *          | { eq:   [a, b] } .. { ne | lt | lte | gt | gte }
 *          | { and:  [a, b, ...] } short-circuit; all non-zero -> 1, else 0
 *          | { or:   [a, b, ...] } short-circuit; any non-zero -> 1, else 0
 *          | { not:  <expr> }       !a -> 0/1
 *          | { obcost:   <expr> }   res_item(id)->cost
 *          | { obdamage: <expr> }   res_item(id)->damage
 *          | { combat_int: '<dotted_path>' } res_combat_int (string-only)
 *          | { item:     <str|int> } res_item_id('torch') / passthrough
 *          | { pic:      <str|int> } res_enum_id('pic', 'inn') / passthrough
 *          | { enum:     [<str>,<str>] } res_enum_id(tab, value)
 *          | { flag_value:[<str>,<str>] } res_flag_value_id(tag, value)
 *
 * All evaluator failures (bad shape, wrong kind, out-of-range index,
 * divide-by-zero, unknown symbolic flag / binding) dispatch through
 * die_at with the enclosing script_id + op index.
 *
 * `{var}`-returning string forms and `{res_enum}` land in later §17-F
 * steps alongside engine_bind, which introduces the string-valued
 * engine registry. */

/* Two-operand array helper. Used by every binop (add, sub, eq, lt, ...). */
static void expr_pair(const node *arr, const char *script_id, size_t idx,
                      const char *op_name, const ryl_bind *bindings,
                      const rt_env *env, int32_t *a, int32_t *b) {
    if (!arr || arr->kind != N_ARR || arr->u.arr.n != 2) {
        char buf[96];
        snprintf(buf, sizeof buf, "expr %s: value must be 2-element array",
                 op_name);
        die_at(script_id, idx, buf);
    }
    *a = eval_expr(arr->u.arr.items[0], script_id, idx, bindings, env);
    *b = eval_expr(arr->u.arr.items[1], script_id, idx, bindings, env);
}

/* Resolve an expr that names a flag slot — accepts a literal int, a
   symbolic res_flag_slot() string, or a generic expr that evaluates
   to an int. Bounds-checked against the party flag array size. */
static int32_t expr_flag_slot(const node *v, const char *script_id, size_t idx,
                              const ryl_bind *bindings, const rt_env *env) {
    int32_t slot;
    if (!v) {
        die_at(script_id, idx, "expr flag: missing slot");
    }
    if (v->kind == N_STR) {
        slot = res_flag_slot(v->u.sv);
        if (slot < 0) {
            die_at(script_id, idx, "expr flag: unknown symbolic flag id");
        }
    } else {
        slot = eval_expr(v, script_id, idx, bindings, env);
    }
    if (slot < 0 || slot >= pa_flag_count()) {
        die_at(script_id, idx, "expr flag: slot out of range");
    }
    return slot;
}

/* `{member: <expr>, field: '<name>'}` — resolve a party-array field
   read. Member idx must be 1..4 (pm_t convention). Field names match
   the plan's inventory; `ws` is included so the full struct party
   SoA is reachable. */
static int32_t expr_member_field(const node *op, const char *script_id,
                                 size_t idx, const ryl_bind *bindings,
                                 const rt_env *env) {
    const node *m_n = obj_get(op, "member");
    const node *f_n = obj_get(op, "field");
    if (!m_n) {
        die_at(script_id, idx, "expr member: missing idx expression");
    }
    if (!f_n || f_n->kind != N_STR) {
        die_at(script_id, idx, "expr member: field must be string");
    }
    int32_t m = eval_expr(m_n, script_id, idx, bindings, env);
    if (m < 0 || m >= res_party_member_max()) {
        die_at(script_id, idx, "expr member: idx out of range");
    }
    const char *field = f_n->u.sv;
    pm_t pm = pm_cur((int)m);
    if (strcmp(field, "lv") == 0) {
        return pm_lv(pm);
    }
    if (strcmp(field, "hp") == 0) {
        return pm_hp(pm);
    }
    if (strcmp(field, "h") == 0) {
        return pm_h(pm);
    }
    if (strcmp(field, "ma") == 0) {
        return pm_ma(pm);
    }
    if (strcmp(field, "wi") == 0) {
        return pm_wi(pm);
    }
    if (strcmp(field, "ws") == 0) {
        return pm_ws(pm);
    }
    if (strcmp(field, "ch") == 0) {
        return pm_ch(pm);
    }
    if (strcmp(field, "cl") == 0) {
        return pm_cl(pm);
    }
    if (strcmp(field, "ra") == 0) {
        return pm_ra(pm);
    }
    if (strcmp(field, "exp") == 0) {
        /* uint32 → int32 cast: exp values stay well below 2^31 in
           legacy save files (the experience cap is much lower). */
        return (int32_t)pm_exp(pm);
    }
    if (strcmp(field, "dead") == 0) {
        return pm_dead(pm);
    }
    if (strcmp(field, "ar") == 0) {
        return pm_ar(pm);
    }
    if (strcmp(field, "wp") == 0) {
        return pm_wp(pm);
    }
    if (strcmp(field, "gender") == 0) {
        return pm_gender(pm);
    }
    die_at(script_id, idx, "expr member: unknown field name");
}

/* `{var: 'name'}` — look up a runtime binding first, then fall through
   to the caller-passed const ryl_bind list (atoi on the string value).
   RT_STR bindings (engine_bind results) are fatal here — they're only
   valid as `{placeholder}` text substitutions; arithmetic / comparison
   against a string value is a script bug. Unknown / unparseable
   bindings die. */
static int32_t expr_var(const char *name, const char *script_id, size_t idx,
                        const ryl_bind *bindings, const rt_env *env) {
    if (env) {
        for (size_t i = env->n; i > 0; i--) {
            const rt_bind *b = &env->items[i - 1];
            if (strcmp(b->name, name) == 0) {
                if (b->kind == RT_STR) {
                    char buf[128];
                    snprintf(buf, sizeof buf,
                             "expr var: '%s' is string-bound (engine_bind) "
                             "— not readable as int",
                             name);
                    die_at(script_id, idx, buf);
                }
                return b->iv;
            }
        }
    }
    for (const ryl_bind *b = bindings; b && b->name; b++) {
        if (strcmp(b->name, name) == 0) {
            if (!b->value) {
                die_at(script_id, idx, "expr var: NULL binding value");
            }
            char *endp = NULL;
            long v = strtol(b->value, &endp, 10);
            if (!endp || *endp != 0) {
                char buf[128];
                snprintf(buf, sizeof buf,
                         "expr var: binding '%s' value '%s' is not integer",
                         name, b->value);
                die_at(script_id, idx, buf);
            }
            return (int32_t)v;
        }
    }
    char buf[96];
    snprintf(buf, sizeof buf, "expr var: unbound '%s'", name);
    die_at(script_id, idx, buf);
}

/* Int-returning C entry points callable from `{ engine_int: '<name>',
   args: [<expr>...] }` expressions. Separate from engine_tbl (void
   return) and str_engine_tbl (const char *). All args are evaluated
   as §17-F expressions, with one shortcut: an entry can mark a
   positional arg as belonging to a `world.json5 enums:` table
   (e.g. arg_enums[1] = "pc_class" on pa_is_class). When the script
   passes a multi-char string literal in that position, the dispatch
   resolves it via res_enum_id BEFORE eval_expr — so the script
   author writes args: [{var:'who'}, 'Ranger'] instead of
   args: [{var:'who'}, {enum:['pc_class','Ranger']}]. 1-char strings
   keep their ASCII-int meaning (handled by eval_expr); ints / object
   exprs are evaluated normally. argc is enforced strictly. */
typedef int32_t (*int_engine_fn)(const int32_t *args);

typedef struct {
    const char *name;
    int argc;
    int_engine_fn fn;
    const char *arg_enums[4]; /* per-arg enum table; NULL = bare int */
} int_engine_entry;

static int32_t ie_pa_is_alive(const int32_t *a) {
    return pa_is_alive((int)a[0]);
}
static int32_t ie_pa_is_dead(const int32_t *a) {
    return pa_is_dead((int)a[0]);
}
static int32_t ie_pa_is_spellcaster(const int32_t *a) {
    return pa_is_spellcaster((int)a[0]);
}
static int32_t ie_pa_is_class(const int32_t *a) {
    return pa_is_class((int)a[0], (int)a[1]);
}
static int32_t ie_pa_first_alive(const int32_t *a) {
    (void)a;
    return pa_first_alive();
}
static int32_t ie_pa_alive_count(const int32_t *a) {
    (void)a;
    return pa_alive_count();
}
static int32_t ie_pa_first_level(const int32_t *a) {
    (void)a;
    return pa_first_level();
}
static int32_t ie_pa_count_class(const int32_t *a) {
    return pa_count_class((int)a[0]);
}
static int32_t ie_pa_count_alive_class(const int32_t *a) {
    return pa_count_alive_class((int)a[0]);
}
static int32_t ie_inv_find_empty(const int32_t *a) {
    (void)a;
    return inv_find_empty();
}
static int32_t ie_inv_find_item(const int32_t *a) {
    return inv_find_item((int)a[0]);
}
static int32_t ie_inv_find_in_range(const int32_t *a) {
    return inv_find_in_range((int)a[0], (int)a[1]);
}
static int32_t ie_inv_count_in_range(const int32_t *a) {
    return inv_count_in_range((int)a[0], (int)a[1]);
}
static int32_t ie_pa_party_member_max(const int32_t *a) {
    (void)a;
    return res_party_member_max();
}
static int32_t ie_inv_slot_max(const int32_t *a) {
    (void)a;
    return res_inventory_slot_max();
}
static int32_t ie_wld_location_x(const int32_t *a) {
    return wld_location_x((int)a[0]);
}
static int32_t ie_wld_location_y(const int32_t *a) {
    return wld_location_y((int)a[0]);
}

/* §23.A: monster nasty-magic frost target pick. Mirrors the legacy
   `while (1) { who = ryl_random(0, max-1); if (pa_is_alive(who)) break; }`
   loop byte-for-byte — every draw advances RNG, the first alive hit
   returns. Caller must guarantee at least one party member is alive
   (battle.c gates the cast block on pa_alive_count(); a wipe takes
   the losegame() path before this is reached). */
static int32_t ie_pick_random_alive_player(const int32_t *a) {
    (void)a;
    int max = res_party_member_max();
    while (1) {
        int who = ryl_random(0, max - 1);
        if (pa_is_alive(who)) {
            return who;
        }
    }
}

/* §23.B: pick the lowest-index alive monster slot, or -1 if every
   slot in the encounter is dead. Single-target frost wand / drums of
   death use this — the legacy idiom is the same `for (i = 0, who = -1;
   i < noc; i++) if (!deadc[i]) { who = i; break; }` loop on both. No
   RNG, no side effects; safe to call out of the cast-line gate.
   Sentinel is -1 (not 0) post-§25.B since slot 0 is now a valid live
   monster; DSL callers gate via `gte: [target, 0]`. */
static int32_t ie_pick_first_alive_monster(const int32_t *a) {
    (void)a;
    if (!g_combat_ctx) {
        return -1;
    }
    for_each_monster(i, g_combat_ctx->noc) {
        if (!g_combat_ctx->deadc[i]) {
            return i;
        }
    }
    return -1;
}

static const int_engine_entry int_engine_tbl[] = {
    {"pa_is_alive",          1, ie_pa_is_alive,          {0}},
    {"pa_is_dead",           1, ie_pa_is_dead,           {0}},
    {"pa_is_spellcaster",    1, ie_pa_is_spellcaster,    {0}},
    {"pa_is_class",          2, ie_pa_is_class,          {NULL, "pc_class"}},
    {"pa_first_alive",       0, ie_pa_first_alive,       {0}},
    {"pa_alive_count",       0, ie_pa_alive_count,       {0}},
    {"pa_first_level",       0, ie_pa_first_level,       {0}},
    {"pa_count_class",       1, ie_pa_count_class,       {"pc_class"}},
    {"pa_count_alive_class", 1, ie_pa_count_alive_class, {"pc_class"}},
    {"inv_find_empty",       0, ie_inv_find_empty,       {0}},
    {"inv_find_item",        1, ie_inv_find_item,        {"item"}},
    {"inv_find_in_range",    2, ie_inv_find_in_range,    {"item", "item"}},
    {"inv_count_in_range",   2, ie_inv_count_in_range,   {"item", "item"}},
    {"pa_party_member_max",  0, ie_pa_party_member_max,  {0}},
    {"inv_slot_max",         0, ie_inv_slot_max,         {0}},
    {"wld_location_x",       1, ie_wld_location_x,       {"world_locations"}},
    {"wld_location_y",       1, ie_wld_location_y,       {"world_locations"}},
    {"pick_random_alive_player", 0, ie_pick_random_alive_player, {0}},
    {"pick_first_alive_monster", 0, ie_pick_first_alive_monster, {0}},
};

/* Forward declaration: used by eval_expr to handle `engine_int`
   expressions. Looks the fn up in int_engine_tbl, evaluates each
   arg as an expression, enforces argc, returns the fn's int32_t. */
static int32_t eval_engine_int(const node *n, const char *script_id, size_t idx,
                               const ryl_bind *bindings, const rt_env *env);

static int32_t eval_expr(const node *n, const char *script_id, size_t idx,
                         const ryl_bind *bindings, const rt_env *env) {
    if (!n) {
        die_at(script_id, idx, "expr: null node");
    }
    if (n->kind == N_INT) {
        return (int32_t)n->u.iv;
    }
    if (n->kind == N_BOOL) {
        return n->u.bv ? 1 : 0;
    }
    /* Single-character string literal -> ASCII int. Lets switch/match
       cases for keypress dispatch read as `match: '1'` / `match: 'A'`
       instead of bare 49 / 65. Strict 1-char so multi-char strings
       (likely bugs) still die_at. */
    if (n->kind == N_STR) {
        const char *s = n->u.sv;
        if (!s || !s[0] || s[1] != 0) {
            die_at(script_id, idx,
                   "expr: string literal in int position must be a single character");
        }
        return (int32_t)(unsigned char)s[0];
    }
    if (n->kind != N_OBJ) {
        die_at(script_id, idx, "expr: must be int or object");
    }

    /* `{flag:N, eq|ne:V}` was the legacy if.cond shorthand (retired);
       diagnose it with a redirect to the expression form rather than
       silently returning the flag value alone. */
    const node *flag_n = obj_get(n, "flag");
    if (flag_n && (obj_get(n, "eq") || obj_get(n, "ne"))) {
        die_at(script_id, idx,
               "expr: legacy {flag:N, eq|ne:V} shape retired — "
               "use { eq: [{flag:N}, V] } or flag_value: for symbolic V");
    }

    if (flag_n) {
        int32_t slot = expr_flag_slot(flag_n, script_id, idx, bindings, env);
        return pa_flag(slot);
    }
    if (obj_get(n, "gold")) {
        return pa_gold();
    }
    if (obj_get(n, "torches")) {
        return pa_torches();
    }
    if (obj_get(n, "food")) {
        return pa_food();
    }
    if (obj_get(n, "pl")) {
        return pa_pl();
    }
    if (obj_get(n, "twn")) {
        return state.twn;
    }
    if (obj_get(n, "hour_of_day")) {
        return state.hour_of_day;
    }
    if (obj_get(n, "hours_rested")) {
        return pa_hours_rested();
    }
    if (obj_get(n, "time_left")) {
        return pa_time_left();
    }
    if (obj_get(n, "cpl")) {
        return party_current_slot();
    }

    const node *v;
    if ((v = obj_get(n, "random"))) {
        if (!v || v->kind != N_ARR || v->u.arr.n != 2) {
            die_at(script_id, idx, "expr random: value must be [lo, hi]");
        }
        int32_t lo = eval_expr(v->u.arr.items[0], script_id, idx, bindings, env);
        int32_t hi = eval_expr(v->u.arr.items[1], script_id, idx, bindings, env);
        if (lo > hi) {
            die_at(script_id, idx, "expr random: lo > hi");
        }
        return ryl_random((int)lo, (int)hi);
    }
    if ((v = obj_get(n, "chance"))) {
        int32_t pct = eval_expr(v, script_id, idx, bindings, env);
        return ryl_chance((int)pct);
    }
    if ((v = obj_get(n, "inventory"))) {
        int32_t slot = eval_expr(v, script_id, idx, bindings, env);
        int smax = res_inventory_slot_max();
        if (slot < 1 || slot > smax) {
            char buf[96];
            snprintf(buf, sizeof buf,
                     "expr inventory: slot must be 1..%d", smax);
            die_at(script_id, idx, buf);
        }
        return pa_inventory(slot);
    }
    if (obj_get(n, "member")) {
        return expr_member_field(n, script_id, idx, bindings, env);
    }
    if ((v = obj_get(n, "var"))) {
        if (v->kind != N_STR) {
            die_at(script_id, idx, "expr var: name must be string");
        }
        return expr_var(v->u.sv, script_id, idx, bindings, env);
    }

    int32_t a, b;
    if ((v = obj_get(n, "add"))) {
        expr_pair(v, script_id, idx, "add", bindings, env, &a, &b);
        return a + b;
    }
    if ((v = obj_get(n, "sub"))) {
        expr_pair(v, script_id, idx, "sub", bindings, env, &a, &b);
        return a - b;
    }
    if ((v = obj_get(n, "mul"))) {
        expr_pair(v, script_id, idx, "mul", bindings, env, &a, &b);
        return a * b;
    }
    if ((v = obj_get(n, "div"))) {
        expr_pair(v, script_id, idx, "div", bindings, env, &a, &b);
        if (b == 0) {
            die_at(script_id, idx, "expr div: divide by zero");
        }
        return a / b;
    }
    if ((v = obj_get(n, "mod"))) {
        expr_pair(v, script_id, idx, "mod", bindings, env, &a, &b);
        if (b == 0) {
            die_at(script_id, idx, "expr mod: modulo by zero");
        }
        return a % b;
    }
    if ((v = obj_get(n, "min"))) {
        expr_pair(v, script_id, idx, "min", bindings, env, &a, &b);
        return a < b ? a : b;
    }
    if ((v = obj_get(n, "max"))) {
        expr_pair(v, script_id, idx, "max", bindings, env, &a, &b);
        return a > b ? a : b;
    }

    if ((v = obj_get(n, "eq"))) {
        expr_pair(v, script_id, idx, "eq", bindings, env, &a, &b);
        return a == b;
    }
    if ((v = obj_get(n, "ne"))) {
        expr_pair(v, script_id, idx, "ne", bindings, env, &a, &b);
        return a != b;
    }
    if ((v = obj_get(n, "lt"))) {
        expr_pair(v, script_id, idx, "lt", bindings, env, &a, &b);
        return a < b;
    }
    if ((v = obj_get(n, "lte"))) {
        expr_pair(v, script_id, idx, "lte", bindings, env, &a, &b);
        return a <= b;
    }
    if ((v = obj_get(n, "gt"))) {
        expr_pair(v, script_id, idx, "gt", bindings, env, &a, &b);
        return a > b;
    }
    if ((v = obj_get(n, "gte"))) {
        expr_pair(v, script_id, idx, "gte", bindings, env, &a, &b);
        return a >= b;
    }

    if ((v = obj_get(n, "and"))) {
        if (v->kind != N_ARR || v->u.arr.n == 0) {
            die_at(script_id, idx, "expr and: value must be non-empty array");
        }
        for (size_t i = 0; i < v->u.arr.n; i++) {
            if (!eval_expr(v->u.arr.items[i], script_id, idx, bindings, env)) {
                return 0;
            }
        }
        return 1;
    }
    if ((v = obj_get(n, "or"))) {
        if (v->kind != N_ARR || v->u.arr.n == 0) {
            die_at(script_id, idx, "expr or: value must be non-empty array");
        }
        for (size_t i = 0; i < v->u.arr.n; i++) {
            if (eval_expr(v->u.arr.items[i], script_id, idx, bindings, env)) {
                return 1;
            }
        }
        return 0;
    }
    if ((v = obj_get(n, "not"))) {
        return eval_expr(v, script_id, idx, bindings, env) ? 0 : 1;
    }

    const node *obcost_n = obj_get(n, "obcost");
    const node *obdmg_n = obj_get(n, "obdamage");
    if (obcost_n || obdmg_n) {
        if (obcost_n && obdmg_n) {
            die_at(script_id, idx,
                   "expr: obcost and obdamage are mutually exclusive");
        }
        int is_damage = obdmg_n != NULL;
        int32_t id = eval_expr(is_damage ? obdmg_n : obcost_n, script_id, idx,
                               bindings, env);
        const ryl_item *it = res_item((int)id);
        if (!it || !it->name) {
            char buf[96];
            snprintf(buf, sizeof buf,
                     "expr %s: item id %d has no record",
                     is_damage ? "obdamage" : "obcost", (int)id);
            die_at(script_id, idx, buf);
        }
        return is_damage ? it->damage : it->cost;
    }

    /* §23.A follow-up: { combat_int: '<dotted_path>' } — read a
       res_combat_int value from world.json5's combat: subtree. The
       path is a string literal so the lookup is identical to the C
       call sites it replaces (e.g. res_combat_int("monster_attack.
       hit_bonus")). Restricted to N_STR — there's no use case yet
       for a runtime-built path, and accepting one would hide typos
       behind a non-fatal NULL return. die_at on a missing path. */
    const node *cint_n = obj_get(n, "combat_int");
    if (cint_n) {
        if (cint_n->kind != N_STR) {
            die_at(script_id, idx,
                   "expr combat_int: path must be a string literal");
        }
        return res_combat_int(cint_n->u.sv);
    }

    /* `{ item: '<name>' }` — resolve a symbolic item key (the shard
       record key, e.g. 'torch' / 'ration' / 'donkey') to its int
       id via res_item_id. Accepts a non-string expression too and
       passes it through after validating res_item has a record —
       lets callers mix symbolic authoring with numeric paths (e.g.
       `{ inventory: { var: 'slot' } }` reads into an int, which
       can then feed a switch over `{ match: { item: 'torch' } }`
       etc). Die-on-unknown-name: catches typos at first fire.
       Parallel to the reward op's items[] resolver. */
    const node *item_n = obj_get(n, "item");
    if (item_n) {
        if (item_n->kind == N_STR) {
            int32_t id = res_item_id(item_n->u.sv);
            if (id < 0) {
                char buf[96];
                snprintf(buf, sizeof buf,
                         "expr item: unknown symbol '%s'", item_n->u.sv);
                die_at(script_id, idx, buf);
            }
            return id;
        }
        int32_t id = eval_expr(item_n, script_id, idx, bindings, env);
        if (!res_item((int)id)) {
            char buf[96];
            snprintf(buf, sizeof buf,
                     "expr item: id %d has no record", (int)id);
            die_at(script_id, idx, buf);
        }
        return id;
    }

    /* `{ pic: '<name>' }` — resolve a symbolic blitgfx pic key from
       the enums.pic table (see world.json5). Dense array indexed by
       pic id; gaps are empty strings. Die-on-unknown-name. Int
       expression passes through unvalidated. */
    const node *pic_n = obj_get(n, "pic");
    if (pic_n) {
        if (pic_n->kind == N_STR) {
            int32_t id = res_enum_id("pic", pic_n->u.sv);
            if (id < 0) {
                char buf[96];
                snprintf(buf, sizeof buf,
                         "expr pic: unknown symbol '%s'", pic_n->u.sv);
                die_at(script_id, idx, buf);
            }
            return id;
        }
        return eval_expr(pic_n, script_id, idx, bindings, env);
    }

    /* `{ flag_value: ['<tag>', '<value>'] }` — resolve a symbolic
       flag value name through the flag's `values:` array. Mirrors
       the legacy `if.cond` shorthand's `eq:'name'` resolution path
       (res_flag_value_id) but in the expression namespace, so
       comparisons compose: `{ eq: [{flag:'X'}, {flag_value:['X',
       'val']}] }`. */
    const node *fv_n = obj_get(n, "flag_value");
    if (fv_n) {
        if (fv_n->kind != N_ARR || fv_n->u.arr.n != 2) {
            die_at(script_id, idx,
                   "expr flag_value: value must be ['<tag>', '<value>']");
        }
        const node *tag_n = fv_n->u.arr.items[0];
        const node *val_n = fv_n->u.arr.items[1];
        if (!tag_n || tag_n->kind != N_STR || !val_n || val_n->kind != N_STR) {
            die_at(script_id, idx,
                   "expr flag_value: both entries must be strings");
        }
        int32_t id = res_flag_value_id(tag_n->u.sv, val_n->u.sv);
        if (id < 0) {
            char buf[128];
            snprintf(buf, sizeof buf,
                     "expr flag_value: unknown '%s.%s'",
                     tag_n->u.sv, val_n->u.sv);
            die_at(script_id, idx, buf);
        }
        return id;
    }

    /* `{ enum: ['<tab>', '<value>'] }` — generic enum symbol→int
       lookup for any world.json5 `enums:` table (pc_class / race /
       job / towne / building / gender / pic / ...). Both
       operands must be string literals; the pair goes through
       res_enum_id. Parallel to the `pic:` shortcut, which this form
       subsumes but is kept for readability. */
    const node *enum_n = obj_get(n, "enum");
    if (enum_n) {
        if (enum_n->kind != N_ARR || enum_n->u.arr.n != 2) {
            die_at(script_id, idx,
                   "expr enum: value must be ['<tab>', '<value>']");
        }
        const node *tab_n = enum_n->u.arr.items[0];
        const node *val_n = enum_n->u.arr.items[1];
        if (!tab_n || tab_n->kind != N_STR || !val_n || val_n->kind != N_STR) {
            die_at(script_id, idx, "expr enum: both entries must be strings");
        }
        int32_t id = res_enum_id(tab_n->u.sv, val_n->u.sv);
        if (id < 0) {
            char buf[128];
            snprintf(buf, sizeof buf,
                     "expr enum: unknown '%s.%s'", tab_n->u.sv, val_n->u.sv);
            die_at(script_id, idx, buf);
        }
        return id;
    }

    if (obj_get(n, "engine_int")) {
        return eval_engine_int(n, script_id, idx, bindings, env);
    }

    /* §22 Lane A: { monster_count } — read combat ctx noc.
       { monster_hp: <expr> } / { monster_dead: <expr> } — read mh[idx]
       / deadc[idx]. Index 0..noc-1; out of range / no ctx is fatal. */
    if (obj_get(n, "monster_count")) {
        if (!g_combat_ctx) {
            die_at(script_id, idx,
                   "expr monster_count: no combat ctx (called outside spell body)");
        }
        return g_combat_ctx->noc;
    }
    const node *mhp_n = obj_get(n, "monster_hp");
    const node *mdd_n = obj_get(n, "monster_dead");
    if (mhp_n || mdd_n) {
        if (!g_combat_ctx) {
            die_at(script_id, idx,
                   "expr monster_hp/monster_dead: no combat ctx");
        }
        int32_t i = eval_expr(mhp_n ? mhp_n : mdd_n, script_id, idx,
                              bindings, env);
        if (i < 0 || i >= g_combat_ctx->noc) {
            die_at(script_id, idx,
                   "expr monster_hp/monster_dead: idx out of range");
        }
        return mhp_n ? g_combat_ctx->mh[i] : g_combat_ctx->deadc[i];
    }

    /* §22 Lane A: { battle_flag: '<name>' } — read a per-encounter
       flag through the combat ctx pointer table. Names map 1:1 to
       struct script_combat_ctx fields (confused / held / antimagic /
       ws). NULL pointer (caller didn't expose that flag) is fatal —
       script authoring bug. Pair with the matching mutate lvalue. */
    const node *bf_n = obj_get(n, "battle_flag");
    if (bf_n) {
        if (!g_combat_ctx) {
            die_at(script_id, idx, "expr battle_flag: no combat ctx");
        }
        if (bf_n->kind != N_STR) {
            die_at(script_id, idx, "expr battle_flag: name must be string");
        }
        int *p = NULL;
        if (strcmp(bf_n->u.sv, "confused") == 0) {
            p = g_combat_ctx->confused;
        } else if (strcmp(bf_n->u.sv, "held") == 0) {
            p = g_combat_ctx->held;
        } else if (strcmp(bf_n->u.sv, "antimagic") == 0) {
            p = g_combat_ctx->antimagic;
        } else if (strcmp(bf_n->u.sv, "ws") == 0) {
            p = g_combat_ctx->ws;
        } else if (strcmp(bf_n->u.sv, "player_held") == 0) {
            p = g_combat_ctx->player_held;
        } else if (strcmp(bf_n->u.sv, "player_confused") == 0) {
            p = g_combat_ctx->player_confused;
        } else if (strcmp(bf_n->u.sv, "action_retry") == 0) {
            p = g_combat_ctx->action_retry;
        } else {
            die_at(script_id, idx,
                   "expr battle_flag: unknown name (use confused/held/"
                   "antimagic/ws/player_held/player_confused/action_retry)");
        }
        if (!p) {
            die_at(script_id, idx,
                   "expr battle_flag: pointer NULL — caller didn't expose flag");
        }
        return *p;
    }

    /* §22 Lane A: { state_banished } — read state.banished. Paired with
       the state_banished lvalue used by priest.banish; not strictly
       needed but symmetry-friendly so {state_banished} works inside an
       if-cond. */
    if (obj_get(n, "state_banished")) {
        return state.banished;
    }

    die_at(script_id, idx, "expr: unknown expression form");
}

static int32_t eval_engine_int(const node *n, const char *script_id, size_t idx,
                               const ryl_bind *bindings, const rt_env *env) {
    const node *fn_n = obj_get(n, "engine_int");
    if (!fn_n || fn_n->kind != N_STR) {
        die_at(script_id, idx, "engine_int: fn must be string");
    }
    const int_engine_entry *e = NULL;
    for (size_t i = 0; i < sizeof int_engine_tbl / sizeof *int_engine_tbl; i++) {
        if (strcmp(int_engine_tbl[i].name, fn_n->u.sv) == 0) {
            e = &int_engine_tbl[i];
            break;
        }
    }
    if (!e) {
        char buf[96];
        snprintf(buf, sizeof buf, "engine_int: unknown fn '%s'", fn_n->u.sv);
        die_at(script_id, idx, buf);
    }
    const node *args_n = obj_get(n, "args");
    int32_t args[4] = {0};
    int got = 0;
    if (args_n) {
        if (args_n->kind != N_ARR) {
            die_at(script_id, idx, "engine_int: args must be array");
        }
        got = (int)args_n->u.arr.n;
        if (got > 4) {
            die_at(script_id, idx, "engine_int: too many args (max 4)");
        }
        for (int i = 0; i < got; i++) {
            const node *an = args_n->u.arr.items[i];
            /* Multi-char string literal in an enum-tagged arg position
               -> resolve the symbol before eval_expr sees it. (1-char
               strings still take the ASCII-int path inside eval_expr.)
               Tag "item" routes through res_item_id (items live in
               their own shards, not the enums: subtree); every other
               tag is an enums: table looked up via res_enum_id. */
            if (i < e->argc && e->arg_enums[i] && an && an->kind == N_STR &&
                an->u.sv && an->u.sv[0] && an->u.sv[1] != 0) {
                int32_t v;
                if (strcmp(e->arg_enums[i], "item") == 0) {
                    v = res_item_id(an->u.sv);
                } else {
                    v = res_enum_id(e->arg_enums[i], an->u.sv);
                }
                if (v < 0) {
                    char buf[160];
                    snprintf(buf, sizeof buf,
                             "engine_int: '%s' arg %d: unknown %s '%s'",
                             e->name, i, e->arg_enums[i], an->u.sv);
                    die_at(script_id, idx, buf);
                }
                args[i] = v;
            } else {
                args[i] = eval_expr(an, script_id, idx, bindings, env);
            }
        }
    }
    if (got != e->argc) {
        char buf[96];
        snprintf(buf, sizeof buf, "engine_int: '%s' expects %d args, got %d",
                 e->name, e->argc, got);
        die_at(script_id, idx, buf);
    }
    return e->fn(args);
}

static void do_screen(const node *op, const char *script_id, size_t idx,
                      const char *parent, const ryl_bind *bindings,
                      const rt_env *env) {
    const node *name_n = obj_get(op, "screen");
    if (!name_n || name_n->kind != N_STR) {
        die_at(script_id, idx, "screen: value must be string");
    }
    char text_id[256];
    resolve_text_id(parent, name_n->u.sv, text_id, sizeof text_id,
                    script_id, idx, "screen");
    ryl_bind merged[RT_ENV_CAP + 32];
    tx_fmt(text_id, merge_bindings(bindings, env, merged,
                                   sizeof merged / sizeof *merged,
                                   script_id, idx));
    ryl_pause();
    clw();
}

static void do_journal(const node *op, const char *script_id, size_t idx,
                       const ryl_bind *bindings, const rt_env *env) {
    const node *txt_n = obj_get(op, "journal");
    if (!txt_n || txt_n->kind != N_STR) {
        die_at(script_id, idx, "journal: value must be string");
    }
    char body[512];
    char ctx[320];
    snprintf(ctx, sizeof ctx, "%s:journal[%zu]", script_id, idx);
    ryl_bind merged[RT_ENV_CAP + 32];
    bind_expand(txt_n->u.sv,
                merge_bindings(bindings, env, merged,
                               sizeof merged / sizeof *merged, script_id, idx),
                body, sizeof body, ctx);

    party_journal_add(party_current(), body);
}

static int32_t resolve_reward_item(const node *e, const char *script_id,
                                   size_t idx) {
    if (!e) {
        die_at(script_id, idx, "reward: items[] entry missing");
    }
    if (e->kind == N_INT) {
        int32_t id = (int32_t)e->u.iv;
        if (id <= 0) {
            die_at(script_id, idx, "reward: item id must be positive");
        }
        if (!res_item(id)) {
            die_at(script_id, idx, "reward: item id has no record");
        }
        return id;
    }
    if (e->kind == N_STR) {
        int32_t id = res_item_id(e->u.sv);
        if (id < 0) {
            die_at(script_id, idx, "reward: unknown item symbol");
        }
        return id;
    }
    die_at(script_id, idx, "reward: items[] entry must be int or string");
}

/* §16.5: { reward: { gold: N, items: [int|str...], cant_carry_screen:
   '<name>', picked_up_screen: '<name>' } }. Adds gold to the current
   party, then walks items[]: places each into the first empty
   inventory slot (1..16). If no slot is free, emits the optional
   cant_carry_screen (tx_fmt + bindings inherited from the enclosing
   play_script), delays 800ms, calls drop_option(); on success emits
   the optional picked_up_screen, delays 800ms, and ryl_pause()s so
   the player reads the pickup line. Items may be authored as literal
   ids (matching the port-plan example) or as symbolic shard keys
   resolved via res_item_id. */
static void do_reward(const node *op, const char *script_id, size_t idx,
                      const char *parent, const ryl_bind *bindings,
                      const rt_env *env) {
    const node *reward = obj_get(op, "reward");
    if (!reward || reward->kind != N_OBJ) {
        die_at(script_id, idx, "reward: value must be object");
    }
    const node *gold_n = obj_get(reward, "gold");
    const node *items_n = obj_get(reward, "items");
    const node *cc_n = obj_get(reward, "cant_carry_screen");
    const node *pu_n = obj_get(reward, "picked_up_screen");
    if (gold_n) {
        if (gold_n->kind != N_INT) {
            die_at(script_id, idx, "reward: gold must be int");
        }
        pa_add_gold((int32_t)gold_n->u.iv);
    }
    if (!items_n) {
        return;
    }
    if (items_n->kind != N_ARR) {
        die_at(script_id, idx, "reward: items must be array");
    }
    if (cc_n && cc_n->kind != N_STR) {
        die_at(script_id, idx, "reward: cant_carry_screen must be string");
    }
    if (pu_n && pu_n->kind != N_STR) {
        die_at(script_id, idx, "reward: picked_up_screen must be string");
    }

    for (size_t k = 0; k < items_n->u.arr.n; k++) {
        int32_t item_id = resolve_reward_item(items_n->u.arr.items[k],
                                              script_id, idx);
        int which;
        if (item_is_special((int)item_id, &which)) {
            party_award_special(which);
            continue;
        }
        int placed = inv_add((int)item_id) != 0;
        if (placed || !cc_n) {
            continue;
        }
        char text_id[256];
        resolve_text_id(parent, cc_n->u.sv, text_id, sizeof text_id,
                        script_id, idx, "reward cant_carry_screen");
        ryl_bind merged[RT_ENV_CAP + 32];
        const ryl_bind *eff =
            merge_bindings(bindings, env, merged,
                           sizeof merged / sizeof *merged, script_id, idx);
        tx_fmt(text_id, eff);
        ryl_delay(800);
        uint8_t carry = drop_option();
        if (carry) {
            clw();
            pa_set_inventory(carry, item_id);
            if (pu_n) {
                resolve_text_id(parent, pu_n->u.sv, text_id, sizeof text_id,
                                script_id, idx, "reward picked_up_screen");
                tx_fmt(text_id, eff);
            }
            ryl_delay(800);
        }
        ryl_pause();
    }
}

static void do_set_flag(const node *op, const char *script_id, size_t idx) {
    const node *slot_n = obj_get(op, "set_flag");
    const node *val_n = obj_get(op, "value");
    if (!slot_n) {
        die_at(script_id, idx, "set_flag: missing slot");
    }
    if (!val_n) {
        die_at(script_id, idx, "set_flag: missing value");
    }
    int32_t slot;
    const char *flag_id = NULL;
    if (slot_n->kind == N_INT) {
        slot = slot_n->u.iv;
    } else if (slot_n->kind == N_STR) {
        flag_id = slot_n->u.sv;
        slot = res_flag_slot(flag_id);
        if (slot < 0) {
            die_at(script_id, idx, "set_flag: unknown symbolic flag id");
        }
    } else {
        die_at(script_id, idx, "set_flag: slot must be int or string");
    }
    if (slot < 0 || slot >= pa_flag_count()) {
        die_at(script_id, idx, "set_flag: slot out of range");
    }
    int32_t value;
    if (val_n->kind == N_INT) {
        value = (int32_t)val_n->u.iv;
    } else if (val_n->kind == N_STR) {
        if (!flag_id) {
            die_at(script_id, idx,
                   "set_flag: string value requires symbolic slot id");
        }
        value = res_flag_value_id(flag_id, val_n->u.sv);
        if (value < 0) {
            die_at(script_id, idx,
                   "set_flag: value not in flag's values[] array");
        }
    } else {
        die_at(script_id, idx, "set_flag: value must be int or string");
    }
    pa_set_flag(slot, (int)value);
}

/* §17-E: { pause: N } — ryl_delay(N) ms. Replaces inline ryl_delay()
   calls between screens in the imperative flows A.4 is migrating. */
static void do_pause(const node *op, const char *script_id, size_t idx) {
    const node *ms_n = obj_get(op, "pause");
    if (!ms_n || ms_n->kind != N_INT) {
        die_at(script_id, idx, "pause: value must be int (ms)");
    }
    if (ms_n->u.iv < 0) {
        die_at(script_id, idx, "pause: ms must be non-negative");
    }
    ryl_delay((unsigned)ms_n->u.iv);
}

/* §17-E: { emit: '<name>' } — tx_fmt only, no ryl_pause, no clw. For
   flows that interleave text with engine_call side effects (encounter
   / update_map / clw) where the screen-op's bundled pause+clw would
   double-clear. */
static void do_emit(const node *op, const char *script_id, size_t idx,
                    const char *parent, const ryl_bind *bindings,
                    const rt_env *env) {
    const node *name_n = obj_get(op, "emit");
    if (!name_n || name_n->kind != N_STR) {
        die_at(script_id, idx, "emit: value must be string");
    }
    char text_id[256];
    resolve_text_id(parent, name_n->u.sv, text_id, sizeof text_id,
                    script_id, idx, "emit");
    ryl_bind merged[RT_ENV_CAP + 32];
    tx_fmt(text_id, merge_bindings(bindings, env, merged,
                                   sizeof merged / sizeof *merged,
                                   script_id, idx));
}

/* §20.20 #2: { emit_indexed: { fmt: 'speak.lodge.j{}', value: <expr> } }
   — emit a text id constructed by substituting an int into a format.
   Mirrors the C-side txi("speak.lodge.j%d", job) shape so flows whose
   text id is selected by job / class / item id collapse to a single
   op rather than a switch over every value.

   `fmt` must contain exactly one `{}` placeholder. The placeholder
   is replaced with the decimal value of `value` (any expr), and the
   result is run through resolve_text_id like a plain `emit`. Strict
   variant — die_at if the resolved id has no text record, since a
   mistyped fmt or out-of-range value silently producing an empty
   line is the failure mode this op is meant to prevent. */
static void do_emit_indexed(const node *op, const char *script_id, size_t idx,
                            const char *parent, const ryl_bind *bindings,
                            const rt_env *env) {
    const node *body = obj_get(op, "emit_indexed");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "emit_indexed: value must be object");
    }
    const node *fmt_n = obj_get(body, "fmt");
    if (!fmt_n || fmt_n->kind != N_STR) {
        die_at(script_id, idx, "emit_indexed: fmt must be string");
    }
    const node *val_n = obj_get(body, "value");
    if (!val_n) {
        die_at(script_id, idx, "emit_indexed: value missing");
    }
    const char *fmt = fmt_n->u.sv;
    const char *hole = strstr(fmt, "{}");
    if (!hole || strstr(hole + 2, "{}")) {
        die_at(script_id, idx,
               "emit_indexed: fmt must contain exactly one '{}' placeholder");
    }
    int32_t v = eval_expr(val_n, script_id, idx, bindings, env);
    char name[256];
    int n = snprintf(name, sizeof name, "%.*s%d%s",
                     (int)(hole - fmt), fmt, (int)v, hole + 2);
    if (n < 0 || n >= (int)sizeof name) {
        die_at(script_id, idx, "emit_indexed: formatted name too long");
    }
    char text_id[256];
    resolve_text_id(parent, name, text_id, sizeof text_id,
                    script_id, idx, "emit_indexed");
    if (!res_text_exists(text_id)) {
        char buf[384];
        snprintf(buf, sizeof buf,
                 "emit_indexed: unknown text id '%s' (fmt='%s', value=%d)",
                 text_id, fmt, (int)v);
        die_at(script_id, idx, buf);
    }
    ryl_bind merged[RT_ENV_CAP + 32];
    tx_fmt(text_id, merge_bindings(bindings, env, merged,
                                   sizeof merged / sizeof *merged,
                                   script_id, idx));
}

/* §17-E: { wait_input: true } — ryl_pause(). Separate from `pause`
   because ryl_pause blocks on a keypress ("Smash RETURN key") and
   ryl_delay is a fixed-length sleep. Both appear in the original
   control flow, sometimes back-to-back. */
static void do_wait_input(const node *op, const char *script_id, size_t idx) {
    const node *v = obj_get(op, "wait_input");
    if (!v) {
        die_at(script_id, idx, "wait_input: missing value");
    }
    (void)v;
    ryl_pause();
}

/* Engine-function registry: the whitelist of C entry points callable
   from { engine_call: '<name>', args: [int...] }. All registered fns
   take zero, one, or two int32 args — no return value. Adding a new
   entry is a line in `engine_tbl`; the DSL does NOT accept arbitrary
   symbol lookups. */
typedef void (*engine_fn)(const int32_t *args);

static void eng_update_map(const int32_t *a) {
    (void)a;
    update_map();
}
static void eng_clr_map_window(const int32_t *a) {
    (void)a;
    clr_map_window();
}
static void eng_blitgfx(const int32_t *a) {
    blitgfx((uint8_t)a[0]);
}
static void eng_statusline(const int32_t *a) {
    (void)a;
    statusline();
}
static void eng_clw(const int32_t *a) {
    (void)a;
    clw();
}
static void eng_advance_hour(const int32_t *a) {
    (void)a;
    advance_hour();
}
static void eng_weaponr(const int32_t *a) {
    (void)a;
    weaponr();
}
static void eng_armourr(const int32_t *a) {
    (void)a;
    armourr();
}
static void eng_invlist(const int32_t *a) {
    (void)a;
    invlist();
}
static void eng_showparty(const int32_t *a) {
    (void)a;
    showparty();
}
/* §17-F step 5: drop_option returns the slot the player chose (0 if
   declined). Exposing it as a void engine_call fires the UX — scripts
   that need the slot should stick with `reward`, which consumes the
   return internally. */
static void eng_drop_option(const int32_t *a) {
    (void)a;
    (void)drop_option();
}
/* towne.c rumour() picks a random r0..r32 line and emits it (with
   optional NPC/town bindings for slots 12, 13, 25, 27). Called from
   smith's beer-accept flow and tavern's rumour menu; exposed as a
   void engine_call so the DSL owns the surrounding screens. */
static void eng_rumour(const int32_t *a) {
    (void)a;
    rumour();
}
/* §20 L2: per-member HP delta — pm_add_hp(pm_cur(member), delta).
   Used by the chest-trap damage in dungeon.gold_chest (case 8)
   inside a for_member loop. Negative deltas damage; pair with
   pm_hp_floor to clamp from below. */
static void eng_pm_add_hp(const int32_t *a) {
    pm_add_hp(pm_cur((int)a[0]), (int)a[1]);
}
/* §23.A: add `delta` to the active party's `pl` (alive-member count
   of the current sub-party). Wraps through pa_add_pl for u8-cast
   parity with the legacy `pa_add_pl(-1)` calls in battle.c monster
   spells; mutate's `LV_PL` path die_at's on negatives, which would
   diverge from the legacy when pl is already 0. */
static void eng_pa_add_pl(const int32_t *a) {
    pa_add_pl((int)a[0]);
}
/* §20 L2: clamp HP from below — pm_hp_floor(pm_cur(member), floor).
   Used right after pm_add_hp so the trap can't reduce a member to
   a negative HP. */
static void eng_pm_hp_floor(const int32_t *a) {
    pm_hp_floor(pm_cur((int)a[0]), (int)a[1]);
}

/* §23.B: pm_full_heal(pm_cur(member)) — hp = h. Used by Staff of
   Healing's effect body. The C helper itself guards on pm_dead, so
   the script can fire this unconditionally and let the engine no-op
   on dead targets (matching the legacy `pm_full_heal(target)` call
   in case 105). */
static void eng_pm_full_heal(const int32_t *a) {
    pm_full_heal(pm_cur((int)a[0]));
}

/* §23.B: pm_set_dead(pm_cur(member), value) — flip a member's dead
   bit to 0 (resurrect) or 1 (kill). The Gem of Resurrection body
   uses this to clear the dead flag once a target validates; pairs
   with a `member_field hp` mutate to give the resurrected member
   1 hp. */
static void eng_pm_set_dead(const int32_t *a) {
    pm_set_dead(pm_cur((int)a[0]), (int)a[1]);
}

/* §23.B: pa_set_inventory(slot, item_id). Magic-item effect bodies
   call this to consume the triggering slot (`pa_set_inventory(slot,
   0)`). Most items consume up-front (first op); gem_of_resurrection
   consumes only inside its success branch. Lifting the consume into
   the body keeps the dispatcher in battle.c uniform and lets the
   data describe the policy. */
static void eng_pa_set_inventory(const int32_t *a) {
    pa_set_inventory((int)a[0], (int)a[1]);
}

/* §22 Lane A: priest.teleport movement loop. dir is the ASCII digit
   key the player pressed ('2'/'4'/'6'/'8' = south/west/east/north;
   passed as int via {var:'<key_var>'}); distance is the ASCII digit
   '1'..'9'. Loop count is (distance - '0') * 2 — same legacy
   formula. Each step gates on the next-tile NOT being a 'm' (mountain)
   so chains of mountains halt the teleport mid-flight. */
static void eng_pa_teleport_step(const int32_t *a) {
    int dir = (int)a[0];
    int distance = (int)a[1] - '0';
    if (distance < 1) {
        return;
    }
    int steps = distance * 2;
    for (int i = 1; i < steps + 1; i++) {
        switch (dir) {
            case '2':
                if (wld_tile_at(pa_x(), pa_y() + 1) != 'm') {
                    pa_add_y(1);
                }
                break;
            case '4':
                if (wld_tile_at(pa_x() - 1, pa_y()) != 'm') {
                    pa_add_x(-1);
                }
                break;
            case '6':
                if (wld_tile_at(pa_x() + 1, pa_y()) != 'm') {
                    pa_add_x(1);
                }
                break;
            case '8':
                if (wld_tile_at(pa_x(), pa_y() - 1) != 'm') {
                    pa_add_y(-1);
                }
                break;
        }
    }
}

typedef struct {
    const char *name;
    int argc;
    engine_fn fn;
} engine_entry;

static const engine_entry engine_tbl[] = {
    {"update_map", 0, eng_update_map},
    {"clr_map_window", 0, eng_clr_map_window},
    {"blitgfx", 1, eng_blitgfx},
    {"statusline", 0, eng_statusline},
    {"clw", 0, eng_clw},
    {"advance_hour", 0, eng_advance_hour},
    {"weaponr", 0, eng_weaponr},
    {"armourr", 0, eng_armourr},
    {"invlist", 0, eng_invlist},
    {"showparty", 0, eng_showparty},
    {"drop_option", 0, eng_drop_option},
    {"rumour", 0, eng_rumour},
    {"pm_add_hp", 2, eng_pm_add_hp},
    {"pm_hp_floor", 2, eng_pm_hp_floor},
    {"pa_teleport_step", 2, eng_pa_teleport_step},
    {"pa_add_pl", 1, eng_pa_add_pl},
    {"pm_full_heal", 1, eng_pm_full_heal},
    {"pm_set_dead", 2, eng_pm_set_dead},
    {"pa_set_inventory", 2, eng_pa_set_inventory},
};

/* §20 L2: { dungeon: { levels: <expr>, scenario?: '<sym>' } }. Wraps
   dungeon.c's dungeon(levels, scenario) entry — `levels` is the
   number of dungeon levels to fill; `scenario` is a symbolic key
   into dungeons.scenarios (e.g. 'fire_shield_cave'), or omit for a
   random cave. Replaces the legacy { engine_call: 'dungeon', args:
   [int, int] } shape so callers no longer juggle anonymous integer
   flags. */
static void do_dungeon(const node *op, const char *script_id, size_t idx,
                       const ryl_bind *bindings, const rt_env *env) {
    const node *body = obj_get(op, "dungeon");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "dungeon: value must be object");
    }
    const node *lvl_n = obj_get(body, "levels");
    if (!lvl_n) {
        die_at(script_id, idx, "dungeon: levels required");
    }
    int32_t levels = eval_expr(lvl_n, script_id, idx, bindings, env);
    const node *sc_n = obj_get(body, "scenario");
    const char *scenario = NULL;
    if (sc_n) {
        if (sc_n->kind != N_STR) {
            die_at(script_id, idx, "dungeon: scenario must be string");
        }
        scenario = sc_n->u.sv;
    }
    dungeon(levels, scenario);
}

/* §20 L2: { speak: { building: '<sym>'|<int>, town?: <expr> } }. Wraps
   speak.c's speak(bld, tow) entry point — the chit-chat NPC picker.
   `building` accepts a symbol resolved through enums.building
   ('none' / 'inn' / 'tavern') or a raw int. `town` is an expression;
   omit it to default to state.twn (the player's current town). Pass
   town: 0 explicitly for the wilderness/hut path. Replaces the legacy
   { engine_call: 'speak', args: [int, int] } shape. The legacy
   bld=1/inn vs bld=2/tavern semantics — under which the inn lists
   tavern-NPCs and vice versa — is preserved verbatim (see the inn /
   tavern callsites in towne.json5). */
static void do_speak(const node *op, const char *script_id, size_t idx,
                     const ryl_bind *bindings, const rt_env *env) {
    const node *body = obj_get(op, "speak");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "speak: value must be object");
    }
    const node *bld_n = obj_get(body, "building");
    const node *tow_n = obj_get(body, "town");
    if (!bld_n) {
        die_at(script_id, idx, "speak: building required");
    }
    int32_t bld;
    if (bld_n->kind == N_INT) {
        bld = (int32_t)bld_n->u.iv;
    } else if (bld_n->kind == N_STR) {
        int id = res_enum_id("building", bld_n->u.sv);
        if (id < 0) {
            char buf[128];
            snprintf(buf, sizeof buf,
                     "speak: unknown building symbol '%s'", bld_n->u.sv);
            die_at(script_id, idx, buf);
        }
        bld = (int32_t)id;
    } else {
        die_at(script_id, idx, "speak: building must be int or string");
    }
    int32_t tow = tow_n
        ? eval_expr(tow_n, script_id, idx, bindings, env)
        : state.twn;
    speak(bld, tow);
}

/* §20 L2: { encounter: { foe: '<sym>'|<int>, mandatory?: <bool>|<int> } }
       OR { encounter: { tier: '<sym>', mandatory?: ... } }
   Calls battle.c's encounter(foe_id, mandatory). `foe` is a monster
   symbol (resolved via res_monster_id) or a raw monsters.json5 id;
   `tier` picks a random monster from a tier (e.g. 'weak' / 'deep')
   via res_monster_tier_pick. Exactly one of foe/tier is required.
   `mandatory` defaults to false (player may try to flee); set true
   to lock out the (R)un option (used by quest encounters).
   Replaces the legacy { engine_call: 'encounter', args: [int, int] }
   shape so foe ids and the mandatory flag both read symbolically at
   the callsite. */
static void do_encounter(const node *op, const char *script_id, size_t idx,
                         const ryl_bind *bindings, const rt_env *env) {
    const node *body = obj_get(op, "encounter");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "encounter: value must be object");
    }
    const node *foe_n = obj_get(body, "foe");
    const node *tier_n = obj_get(body, "tier");
    if (!foe_n && !tier_n) {
        die_at(script_id, idx, "encounter: foe or tier required");
    }
    if (foe_n && tier_n) {
        die_at(script_id, idx, "encounter: foe and tier are exclusive");
    }
    int32_t foe;
    if (tier_n) {
        if (tier_n->kind != N_STR) {
            die_at(script_id, idx, "encounter: tier must be string");
        }
        foe = res_monster_tier_pick(tier_n->u.sv);
    } else if (foe_n->kind == N_INT) {
        foe = (int32_t)foe_n->u.iv;
    } else if (foe_n->kind == N_STR) {
        int id = res_monster_id(foe_n->u.sv);
        if (id < 0) {
            char buf[128];
            snprintf(buf, sizeof buf,
                     "encounter: unknown foe symbol '%s'", foe_n->u.sv);
            die_at(script_id, idx, buf);
        }
        foe = (int32_t)id;
    } else {
        die_at(script_id, idx, "encounter: foe must be int or string");
    }
    int32_t mandatory = 0;
    const node *mand_n = obj_get(body, "mandatory");
    if (mand_n) {
        mandatory = eval_expr(mand_n, script_id, idx, bindings, env);
    }
    encounter(foe, mandatory);
}

/* §17-E: { engine_call: '<name>', args: [<expr>...] }. Args are
   evaluated as §17-F expressions — literal ints still work, but
   callers can also drop in { var:'name' } or arithmetic. Registry
   whitelist is closed: unknown names die with a pointing
   diagnostic. */
static void do_engine_call(const node *op, const char *script_id, size_t idx,
                           const ryl_bind *bindings, const rt_env *env) {
    const node *name_n = obj_get(op, "engine_call");
    if (!name_n || name_n->kind != N_STR) {
        die_at(script_id, idx, "engine_call: value must be string");
    }
    const engine_entry *e = NULL;
    for (size_t i = 0; i < sizeof engine_tbl / sizeof *engine_tbl; i++) {
        if (strcmp(engine_tbl[i].name, name_n->u.sv) == 0) {
            e = &engine_tbl[i];
            break;
        }
    }
    if (!e) {
        char buf[96];
        snprintf(buf, sizeof buf, "engine_call: unknown fn '%s'", name_n->u.sv);
        die_at(script_id, idx, buf);
    }
    const node *args_n = obj_get(op, "args");
    int32_t args[4] = {0};
    int got = 0;
    if (args_n) {
        if (args_n->kind != N_ARR) {
            die_at(script_id, idx, "engine_call: args must be array");
        }
        got = (int)args_n->u.arr.n;
        if (got > 4) {
            die_at(script_id, idx, "engine_call: too many args (max 4)");
        }
        for (int i = 0; i < got; i++) {
            args[i] = eval_expr(args_n->u.arr.items[i], script_id, idx,
                                bindings, env);
        }
    }
    if (got != e->argc) {
        char buf[96];
        snprintf(buf, sizeof buf, "engine_call: '%s' expects %d args, got %d",
                 e->name, e->argc, got);
        die_at(script_id, idx, buf);
    }
    e->fn(args);
}

/* §17-F step 5 string-returning registry. Separate from engine_tbl
   because the return type differs (const char * vs void) and
   because res_enum takes a string arg that we don't want to
   shoehorn into an int-only dispatch. Each entry's `sig` describes
   args[] positionally: 'i' = expression evaluated to int, 's' =
   literal N_STR node (its u.sv is borrowed into strs[]). The fn
   reads parsed ints[] / strs[] and writes the result into `out`;
   truncation is caller's problem (rt_bind_set_str checks cap). */
typedef void (*str_engine_fn)(const int32_t *ints, const char **strs,
                              char *out, size_t out_sz);

typedef struct {
    const char *name;
    const char *sig;
    str_engine_fn fn;
} str_engine_entry;

static void sb_object(const int32_t *ints, const char **strs, char *out,
                      size_t out_sz) {
    (void)strs;
    snprintf(out, out_sz, "%s", object(ints[0]));
}
/* §20 L2: pronoun's grammatical-case arg now reads as a symbol —
   'subject' / 'object' / 'possessive' (matches the keys folded by
   res.c::fold_pronoun: pronoun_tab columns 1/2/3). Replaces the
   legacy {1,2,3} ints at every callsite in towne.json5. */
static void sb_pronoun(const int32_t *ints, const char **strs, char *out,
                       size_t out_sz) {
    int xx;
    if (strcmp(strs[0], "subject") == 0) {
        xx = 1;
    } else if (strcmp(strs[0], "object") == 0) {
        xx = 2;
    } else if (strcmp(strs[0], "possessive") == 0) {
        xx = 3;
    } else {
        snprintf(out, out_sz, "?case=%s?", strs[0]);
        return;
    }
    snprintf(out, out_sz, "%s", pronoun((uint8_t)ints[0], (uint8_t)xx));
}
static void sb_res_town_name(const int32_t *ints, const char **strs, char *out,
                             size_t out_sz) {
    (void)strs;
    snprintf(out, out_sz, "%s", res_town_name((int)ints[0]));
}
static void sb_res_enum(const int32_t *ints, const char **strs, char *out,
                        size_t out_sz) {
    snprintf(out, out_sz, "%s", res_enum(strs[0], (int)ints[0]));
}
/* Display name of the first NPC whose `job:` matches the symbol and
   whose `towne:` matches the int — replaces the retired
   res_enum('innkeepers'|'tavernkeepers', twn) DSL pattern. */
static void sb_npc_in_town(const int32_t *ints, const char **strs, char *out,
                           size_t out_sz) {
    snprintf(out, out_sz, "%s", res_npc_in_town((int)ints[0], strs[0]));
}
/* §20 L2: role-keyed NPC slot lookup, mirrors res_npc_role. Used by
   dungeon scripts that need to render an NPC name without knowing
   the cold-start slot — e.g. silver_sanctum's "...by the Master
   Thief" text resolves to the Master-Thief NPC's randomized name.
   The result is the slot id rendered as a numeric string so the
   next engine_bind ('res_enum') can re-read it as int via the
   binding's atoi path. */
static void sb_res_npc_role(const int32_t *ints, const char **strs,
                            char *out, size_t out_sz) {
    (void)ints;
    snprintf(out, out_sz, "%d", (int)res_npc_role(strs[0]));
}
/* Active party member's name for slot 1..res_party_member_max(). Out-
   of-range slots render "-" (matches object's unknown-id fallback)
   rather than dying — callers already validate the slot via
   key_range ['1','4'] before binding. */
static void sb_pa_name(const int32_t *ints, const char **strs, char *out,
                       size_t out_sz) {
    (void)strs;
    const char *nm = pa_name((int)ints[0]);
    snprintf(out, out_sz, "%s", nm ? nm : "-");
}
/* §22.F: NPC display name keyed by quest-role symbol. Resolves the
   role through res_npc_role (fatal on unknown role / dangling
   binding), then renders the matched NPC's display name. Lets the
   speak_missions dispatcher scripts produce {npc_name}-style
   bindings without going through C. */
static void sb_npc_name_at_role(const int32_t *ints, const char **strs,
                                char *out, size_t out_sz) {
    (void)ints;
    snprintf(out, out_sz, "%s", res_npc_name(res_npc_role(strs[0])));
}
/* §22.F: NPC location string keyed by quest-role symbol. Mirrors
   npc_loc_str (towne ? "in <town>" : "at <x>x<y>"); used by
   speak_missions stages to build the {npc_loc} placeholder for
   the nested speak.<x>.<y> screen + journal text. */
static void sb_npc_loc_at_role(const int32_t *ints, const char **strs,
                               char *out, size_t out_sz) {
    (void)ints;
    npc_loc_str(npc_for_role(strs[0]), out, out_sz);
}

static const str_engine_entry str_engine_tbl[] = {
    {"object", "i", sb_object},
    {"pronoun", "is", sb_pronoun},
    {"res_town_name", "i", sb_res_town_name},
    {"res_enum", "si", sb_res_enum},
    {"npc_in_town", "si", sb_npc_in_town},
    {"res_npc_role", "s", sb_res_npc_role},
    {"pa_name", "i", sb_pa_name},
    {"npc_name_at_role", "s", sb_npc_name_at_role},
    {"npc_loc_at_role", "s", sb_npc_loc_at_role},
};

/* Whitelist introspection — see script.h. -1 if <name> isn't in the
   matching registry; the registry's argc (or sig length, for
   engine_bind) on hit. */
int script_engine_call_argc(const char *name) {
    for (size_t i = 0; i < sizeof engine_tbl / sizeof *engine_tbl; i++) {
        if (strcmp(engine_tbl[i].name, name) == 0) {
            return engine_tbl[i].argc;
        }
    }
    return -1;
}
int script_engine_int_argc(const char *name) {
    for (size_t i = 0; i < sizeof int_engine_tbl / sizeof *int_engine_tbl; i++) {
        if (strcmp(int_engine_tbl[i].name, name) == 0) {
            return int_engine_tbl[i].argc;
        }
    }
    return -1;
}
int script_engine_bind_argc(const char *name) {
    for (size_t i = 0; i < sizeof str_engine_tbl / sizeof *str_engine_tbl; i++) {
        if (strcmp(str_engine_tbl[i].name, name) == 0) {
            return (int)strlen(str_engine_tbl[i].sig);
        }
    }
    return -1;
}

/* §17-F step 5: { engine_bind: { fn:'<name>', args:[<expr>|'<str>',
   ...], var:'<name>' } }. Calls a whitelisted string-returning C
   entry point and stashes the result into rt_env as RT_STR so
   `{<name>}` text expansion picks it up. `args` entries are
   positional per the registry entry's sig string — 'i' positions
   are evaluated as §17-F expressions; 's' positions must be N_STR
   literals (authoring-time constants like the res_enum id). */
static void do_engine_bind(const node *op, const char *script_id, size_t idx,
                           const ryl_bind *bindings, rt_env *env) {
    const node *body = obj_get(op, "engine_bind");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "engine_bind: value must be object");
    }
    const node *fn_n = obj_get(body, "fn");
    const node *args_n = obj_get(body, "args");
    const node *var_n = obj_get(body, "var");
    if (!fn_n || fn_n->kind != N_STR) {
        die_at(script_id, idx, "engine_bind: fn must be string");
    }
    if (!var_n || var_n->kind != N_STR) {
        die_at(script_id, idx, "engine_bind: var must be string");
    }
    const str_engine_entry *e = NULL;
    for (size_t i = 0; i < sizeof str_engine_tbl / sizeof *str_engine_tbl;
         i++) {
        if (strcmp(str_engine_tbl[i].name, fn_n->u.sv) == 0) {
            e = &str_engine_tbl[i];
            break;
        }
    }
    if (!e) {
        char buf[96];
        snprintf(buf, sizeof buf, "engine_bind: unknown fn '%s'", fn_n->u.sv);
        die_at(script_id, idx, buf);
    }
    size_t argc = strlen(e->sig);
    if (argc > 4) {
        die_at(script_id, idx, "engine_bind: sig too long (max 4)");
    }
    if (!args_n || args_n->kind != N_ARR) {
        die_at(script_id, idx, "engine_bind: args must be array");
    }
    if (args_n->u.arr.n != argc) {
        char buf[128];
        snprintf(buf, sizeof buf, "engine_bind: '%s' expects %zu args, got %zu",
                 e->name, argc, args_n->u.arr.n);
        die_at(script_id, idx, buf);
    }
    int32_t ints[4] = {0};
    const char *strs[4] = {NULL};
    size_t ni = 0, ns = 0;
    for (size_t i = 0; i < argc; i++) {
        const node *a = args_n->u.arr.items[i];
        if (e->sig[i] == 'i') {
            ints[ni++] = eval_expr(a, script_id, idx, bindings, env);
        } else if (e->sig[i] == 's') {
            if (!a || a->kind != N_STR) {
                char buf[128];
                snprintf(buf, sizeof buf,
                         "engine_bind: '%s' arg %zu must be string literal",
                         e->name, i);
                die_at(script_id, idx, buf);
            }
            strs[ns++] = a->u.sv;
        } else {
            die_at(script_id, idx, "engine_bind: corrupt registry sig");
        }
    }
    char out[RT_SV_CAP];
    e->fn(ints, strs, out, sizeof out);
    rt_bind_set_str(env, var_n->u.sv, out, script_id, idx);
}

/* Evaluate an `if` condition object: any node accepted by
 * eval_expr. Truthy (!= 0) -> 1, else 0. */
static int eval_cond(const node *cond, const char *script_id, size_t idx,
                     const ryl_bind *bindings, const rt_env *env) {
    if (!cond || cond->kind != N_OBJ) {
        die_at(script_id, idx, "if: cond must be object");
    }
    return eval_expr(cond, script_id, idx, bindings, env) != 0 ? 1 : 0;
}

/* §17-E + §17-F step 2: { if: { cond, then:[...], else:[...] } } —
   cond accepts the legacy flag/gold shorthand or any §17-F bare
   expression (see eval_cond). */
static ops_result do_if(const node *op, const char *script_id, size_t idx,
                        const char *parent, const ryl_bind *bindings,
                        rt_env *env) {
    const node *body = obj_get(op, "if");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "if: value must be object");
    }
    const node *cond = obj_get(body, "cond");
    const node *then_n = obj_get(body, "then");
    const node *else_n = obj_get(body, "else");
    const node *take =
        eval_cond(cond, script_id, idx, bindings, env) ? then_n : else_n;
    if (!take) {
        return OPS_NORMAL;
    }
    if (take->kind != N_ARR) {
        die_at(script_id, idx, "if: then/else must be array");
    }
    return play_ops(take, script_id, parent, bindings, env);
}

/* Does the N_STR `key_n` pattern match the uppercased input `ch`?
   Accepts five per-option shapes: `key: 'K'` (single char),
   `keys: ['A','B',...]` (any listed char), `key_range: ['LO','HI']`
   (ASCII-inclusive literal range after upper-casing),
   `digit_range: { lo:<int-expr>, hi:<int-expr> }` (digit-char range
   evaluated dynamically via eval_expr — both bounds must land in
   [1, 9]), and `letter_range: { lo: 'A', count: <int-expr> }` (the
   letter analogue of digit_range — N consecutive letters starting
   at lo, count evaluated dynamically). Returns 1 on match. Fatal
   on malformed option.

   key_range remains the literal-char path (covers fixed letter and
   digit ranges like ['A','G'] and ['1','6'] for shop menus).
   digit_range and letter_range are the dynamic-bound shapes —
   used where the bound depends on something like
   res_party_member_max() / res_inventory_slot_max(). Both support
   an optional `index_var:` on the option that auto-binds the
   matched ordinal into rt_env so the body reads the slot directly
   without ASCII arithmetic: letter_range binds `ch - lo + 1`
   (1-based offset from `lo`), digit_range binds `ch - '0'` (the
   matched digit's int value, which equals the menu's `lo` when the
   user picked the lowest option). */
static int menu_opt_matches(const node *o, char ch, const char *script_id,
                            size_t idx, const ryl_bind *bindings,
                            const rt_env *env) {
    const node *key_n = obj_get(o, "key");
    const node *keys_n = obj_get(o, "keys");
    const node *range_n = obj_get(o, "key_range");
    const node *digit_n = obj_get(o, "digit_range");
    const node *letter_n = obj_get(o, "letter_range");
    int shapes = (key_n != NULL) + (keys_n != NULL) + (range_n != NULL) +
                 (digit_n != NULL) + (letter_n != NULL);
    if (shapes == 0) {
        die_at(script_id, idx,
               "menu_branch: option needs key, keys, key_range, "
               "digit_range, or letter_range");
    }
    if (shapes > 1) {
        die_at(script_id, idx,
               "menu_branch: key / keys / key_range / digit_range / "
               "letter_range are mutually exclusive");
    }
    if (key_n) {
        if (key_n->kind != N_STR || key_n->u.sv[0] == 0 ||
            key_n->u.sv[1] != 0) {
            die_at(script_id, idx,
                   "menu_branch: key must be one-char string");
        }
        return (char)toupper((unsigned char)key_n->u.sv[0]) == ch;
    }
    if (keys_n) {
        if (keys_n->kind != N_ARR || keys_n->u.arr.n == 0) {
            die_at(script_id, idx,
                   "menu_branch: keys must be non-empty array");
        }
        for (size_t i = 0; i < keys_n->u.arr.n; i++) {
            const node *k = keys_n->u.arr.items[i];
            if (!k || k->kind != N_STR || k->u.sv[0] == 0 ||
                k->u.sv[1] != 0) {
                die_at(script_id, idx,
                       "menu_branch: keys entry must be one-char string");
            }
            if ((char)toupper((unsigned char)k->u.sv[0]) == ch) {
                return 1;
            }
        }
        return 0;
    }
    if (digit_n) {
        if (digit_n->kind != N_OBJ) {
            die_at(script_id, idx,
                   "menu_branch: digit_range must be object");
        }
        const node *lo_e = obj_get(digit_n, "lo");
        const node *hi_e = obj_get(digit_n, "hi");
        if (!lo_e || !hi_e) {
            die_at(script_id, idx,
                   "menu_branch: digit_range needs lo and hi");
        }
        int32_t lo_v = eval_expr(lo_e, script_id, idx, bindings, env);
        int32_t hi_v = eval_expr(hi_e, script_id, idx, bindings, env);
        if (lo_v < 1 || lo_v > 9 || hi_v < 1 || hi_v > 9) {
            die_at(script_id, idx,
                   "menu_branch: digit_range bounds must be in [1, 9]");
        }
        if (lo_v > hi_v) {
            die_at(script_id, idx,
                   "menu_branch: digit_range lo > hi");
        }
        if (ch < '0' || ch > '9') {
            return 0;
        }
        int32_t cv = ch - '0';
        return cv >= lo_v && cv <= hi_v;
    }
    if (letter_n) {
        if (letter_n->kind != N_OBJ) {
            die_at(script_id, idx,
                   "menu_branch: letter_range must be object");
        }
        const node *lo_n = obj_get(letter_n, "lo");
        const node *count_e = obj_get(letter_n, "count");
        if (!lo_n || !count_e) {
            die_at(script_id, idx,
                   "menu_branch: letter_range needs lo and count");
        }
        if (lo_n->kind != N_STR || lo_n->u.sv[0] == 0 ||
            lo_n->u.sv[1] != 0) {
            die_at(script_id, idx,
                   "menu_branch: letter_range lo must be one-char string");
        }
        char lo = (char)toupper((unsigned char)lo_n->u.sv[0]);
        if (lo < 'A' || lo > 'Z') {
            die_at(script_id, idx,
                   "menu_branch: letter_range lo must be A..Z");
        }
        int32_t count = eval_expr(count_e, script_id, idx, bindings, env);
        if (count < 0) {
            die_at(script_id, idx,
                   "menu_branch: letter_range count must be >= 0");
        }
        if (count == 0) {
            return 0;
        }
        if (lo + count - 1 > 'Z') {
            die_at(script_id, idx,
                   "menu_branch: letter_range overflows past Z");
        }
        char hi = (char)(lo + count - 1);
        return ch >= lo && ch <= hi;
    }
    /* range_n (literal-char key_range) */
    if (range_n->kind != N_ARR || range_n->u.arr.n != 2) {
        die_at(script_id, idx,
               "menu_branch: key_range must be [lo, hi] array");
    }
    const node *lo_n = range_n->u.arr.items[0];
    const node *hi_n = range_n->u.arr.items[1];
    if (!lo_n || lo_n->kind != N_STR || lo_n->u.sv[0] == 0 ||
        lo_n->u.sv[1] != 0 || !hi_n || hi_n->kind != N_STR ||
        hi_n->u.sv[0] == 0 || hi_n->u.sv[1] != 0) {
        die_at(script_id, idx,
               "menu_branch: key_range entries must be one-char strings");
    }
    char lo = (char)toupper((unsigned char)lo_n->u.sv[0]);
    char hi = (char)toupper((unsigned char)hi_n->u.sv[0]);
    if (lo > hi) {
        die_at(script_id, idx, "menu_branch: key_range lo > hi");
    }
    return ch >= lo && ch <= hi;
}

/* §17-E + §17-F step 3: { menu_branch: { prompt: '<name>'?,
   key_var: '<name>'?, options: [...], default: [...] } }. Optional
   `prompt` names a screen text id (parent-relative, like `screen`)
   emitted before the keypress; omit to stack on an earlier tx_fmt /
   screen. See menu_opt_matches for the per-option shapes (key /
   keys / key_range / digit_range / letter_range). Optional
   top-level `key_var` binds the matched (uppercased) key as a
   one-char string into rt_env for the selected then: body — the
   caller can then reference it as `{<key_var>}` inside nested
   screen / journal text or as `{var:'<key_var>'}` (ASCII code) in
   expressions. Unmatched keys fall through to an optional
   `default: [...]` op array. Single keypress — the enclosing loop
   (if any) is the caller's job. */
static ops_result do_menu_branch(const node *op, const char *script_id,
                                 size_t idx, const char *parent,
                                 const ryl_bind *bindings, rt_env *env) {
    const node *body = obj_get(op, "menu_branch");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "menu_branch: value must be object");
    }
    const node *prompt_n = obj_get(body, "prompt");
    const node *opts_n = obj_get(body, "options");
    const node *default_n = obj_get(body, "default");
    const node *key_var_n = obj_get(body, "key_var");
    if (!opts_n || opts_n->kind != N_ARR) {
        die_at(script_id, idx, "menu_branch: options must be array");
    }
    if (key_var_n && key_var_n->kind != N_STR) {
        die_at(script_id, idx, "menu_branch: key_var must be string");
    }
    if (prompt_n) {
        if (prompt_n->kind != N_STR) {
            die_at(script_id, idx, "menu_branch: prompt must be string");
        }
        char text_id[256];
        resolve_text_id(parent, prompt_n->u.sv, text_id, sizeof text_id,
                        script_id, idx, "menu_branch prompt");
        ryl_bind merged[RT_ENV_CAP + 32];
        tx_fmt(text_id,
               merge_bindings(bindings, env, merged,
                              sizeof merged / sizeof *merged, script_id, idx));
    }
    char ch = (char)toupper((unsigned char)key());
    /* key_var binds regardless of which option matches (or whether one
       matches at all). That way callers that follow a menu_branch with
       a switch on the same key get a meaningful `last_key` even when
       the user pressed something unmapped and the `default: [...]`
       arm fired — useful for pagination-loop exits and similar flows
       where the body breaks on "anything not N/P" but still wants to
       decide what to do based on which key the user pressed. */
    if (key_var_n) {
        rt_bind_set_char(env, key_var_n->u.sv, ch, script_id, idx);
    }
    for (size_t i = 0; i < opts_n->u.arr.n; i++) {
        const node *o = opts_n->u.arr.items[i];
        if (!o || o->kind != N_OBJ) {
            die_at(script_id, idx, "menu_branch: option must be object");
        }
        if (!menu_opt_matches(o, ch, script_id, idx, bindings, env)) {
            continue;
        }
        /* letter_range / digit_range: bind the matched ordinal into
           rt_env so the body can use {var:'<index_var>'} or
           {<index_var>} directly without ASCII arithmetic. For
           letter_range the bound int is 1-based (`ch - lo + 1`,
           inventory convention); for digit_range it's a 0-based
           offset from `lo` (`ch - '0' - lo_v`, matching the §25.B
           party-member 0-based slot convention). menu_opt_matches
           already validated bounds so the lookup here is safe. */
        const node *letter_n = obj_get(o, "letter_range");
        const node *digit_n = obj_get(o, "digit_range");
        const node *index_var_n = obj_get(o, "index_var");
        if (index_var_n) {
            if (index_var_n->kind != N_STR) {
                die_at(script_id, idx,
                       "menu_branch: index_var must be string");
            }
            int32_t slot;
            const char *bind_kind;
            if (letter_n) {
                const node *lo_n = obj_get(letter_n, "lo");
                char lo = (char)toupper((unsigned char)lo_n->u.sv[0]);
                slot = (int32_t)(ch - lo + 1);
                bind_kind = "letter_range";
            } else if (digit_n) {
                /* §25.B: digit_range binds 0-based offset from `lo`.
                   With `lo: 1` (the only form in use today, for party-
                   member pickers), digit '1' binds 0, '4' binds 3 —
                   matching the 0-based slot convention used by pa_*
                   and pm_* and the for_each_party_member macro. */
                const node *lo_n = obj_get(digit_n, "lo");
                int32_t lo_v = eval_expr(lo_n, script_id, idx, bindings, env);
                slot = (int32_t)(ch - '0') - lo_v;
                bind_kind = "digit_range";
            } else {
                die_at(script_id, idx,
                       "menu_branch: index_var requires letter_range "
                       "or digit_range option");
            }
            LOG_D("input", "menu_branch %s: key=%c -> slot=%d (bind '%s')",
                  bind_kind, ch, slot, index_var_n->u.sv);
            rt_bind_set(env, index_var_n->u.sv, slot, script_id, idx);
        }
        const node *then_n = obj_get(o, "then");
        if (!then_n) {
            return OPS_NORMAL;
        }
        if (then_n->kind != N_ARR) {
            die_at(script_id, idx, "menu_branch: then must be array");
        }
        return play_ops(then_n, script_id, parent, bindings, env);
    }
    if (default_n) {
        if (default_n->kind != N_ARR) {
            die_at(script_id, idx, "menu_branch: default must be array");
        }
        return play_ops(default_n, script_id, parent, bindings, env);
    }
    return OPS_NORMAL;
}

/* §17-F step 3: { let: '<name>', value: <expr> } — evaluate `value`
   and bind into rt_env so later `{var:'<name>'}` expressions and
   `{<name>}` text placeholders pick it up. Repeated sets of the
   same name mutate in place (no scope stack yet — step 4 adds one
   for for_member / for_inventory). */
static void do_let(const node *op, const char *script_id, size_t idx,
                   const ryl_bind *bindings, rt_env *env) {
    const node *name_n = obj_get(op, "let");
    if (!name_n || name_n->kind != N_STR) {
        die_at(script_id, idx, "let: name must be string");
    }
    const node *val_n = obj_get(op, "value");
    if (!val_n) {
        die_at(script_id, idx, "let: missing value");
    }
    int32_t v = eval_expr(val_n, script_id, idx, bindings, env);
    rt_bind_set(env, name_n->u.sv, v, script_id, idx);
}

/* Resolve a mutate lvalue into (read_fn, write_fn) via a small tagged
   union. `mutate_lv` carries enough context to round-trip through
   eval_expr's lvalue sub-grammar (gold / torches / food / pl /
   flag:<expr> / inventory:<expr> / member_field{ idx, field } /
   hours_rested / hour_of_day) and write back a computed int32 at the
   same location. hours_rested lives on state.p[state.cpl] (per-party);
   hour_of_day is the top-level wall-clock scalar (1..24). */
typedef enum {
    LV_GOLD,
    LV_TORCHES,
    LV_FOOD,
    LV_PL,
    LV_FLAG,
    LV_INVENTORY,
    LV_MEMBER_FIELD,
    LV_HOURS_RESTED,
    LV_HOUR_OF_DAY,
    LV_TIME_LEFT,
    /* §22 Lane A — combat-ctx-resident lvalues. Index encodes the
       monster slot for monster_hp/_dead; battle_flag picks the
       in-encounter int* (confused/held/antimagic/ws); state_banished
       writes state.banished. */
    LV_MONSTER_HP,
    LV_MONSTER_DEAD,
    LV_BATTLE_FLAG,
    LV_STATE_BANISHED,
} mutate_lv_kind;

typedef struct {
    mutate_lv_kind kind;
    int32_t index;     /* flag slot / inventory slot / member idx 1..4 /
                          monster idx 1..noc */
    const char *field; /* LV_MEMBER_FIELD only — borrowed AST string;
                          LV_BATTLE_FLAG reuses for the flag name */
} mutate_lv;

static mutate_lv resolve_lv(const node *tgt, const char *script_id, size_t idx,
                            const ryl_bind *bindings, const rt_env *env) {
    if (!tgt || tgt->kind != N_OBJ) {
        die_at(script_id, idx, "mutate: target must be object");
    }
    if (obj_get(tgt, "gold")) {
        return (mutate_lv){LV_GOLD, 0, NULL};
    }
    if (obj_get(tgt, "torches")) {
        return (mutate_lv){LV_TORCHES, 0, NULL};
    }
    if (obj_get(tgt, "food")) {
        return (mutate_lv){LV_FOOD, 0, NULL};
    }
    if (obj_get(tgt, "pl")) {
        return (mutate_lv){LV_PL, 0, NULL};
    }
    if (obj_get(tgt, "hours_rested")) {
        return (mutate_lv){LV_HOURS_RESTED, 0, NULL};
    }
    if (obj_get(tgt, "hour_of_day")) {
        return (mutate_lv){LV_HOUR_OF_DAY, 0, NULL};
    }
    if (obj_get(tgt, "time_left")) {
        return (mutate_lv){LV_TIME_LEFT, 0, NULL};
    }
    const node *flag_n = obj_get(tgt, "flag");
    if (flag_n) {
        int32_t slot = expr_flag_slot(flag_n, script_id, idx, bindings, env);
        return (mutate_lv){LV_FLAG, slot, NULL};
    }
    const node *inv_n = obj_get(tgt, "inventory");
    if (inv_n) {
        int32_t slot = eval_expr(inv_n, script_id, idx, bindings, env);
        int smax = res_inventory_slot_max();
        if (slot < 1 || slot > smax) {
            char buf[96];
            snprintf(buf, sizeof buf,
                     "mutate: inventory slot must be 1..%d", smax);
            die_at(script_id, idx, buf);
        }
        return (mutate_lv){LV_INVENTORY, slot, NULL};
    }
    const node *mf_n = obj_get(tgt, "member_field");
    if (mf_n) {
        if (mf_n->kind != N_OBJ) {
            die_at(script_id, idx,
                   "mutate: member_field must be object");
        }
        const node *i_n = obj_get(mf_n, "idx");
        const node *f_n = obj_get(mf_n, "field");
        if (!i_n) {
            die_at(script_id, idx, "mutate: member_field missing idx");
        }
        if (!f_n || f_n->kind != N_STR) {
            die_at(script_id, idx,
                   "mutate: member_field field must be string");
        }
        int32_t m = eval_expr(i_n, script_id, idx, bindings, env);
        if (m < 0 || m >= res_party_member_max()) {
            die_at(script_id, idx,
                   "mutate: member_field idx out of range");
        }
        return (mutate_lv){LV_MEMBER_FIELD, m, f_n->u.sv};
    }
    /* §22 Lane A combat-ctx targets: { monster_hp: <expr> },
       { monster_dead: <expr> }, { battle_flag: '<name>' },
       { state_banished: <any> }. The first two need 1..noc; the
       battle_flag carries the flag name in `field`. ctx-NULL is
       fatal so a script-author bug fires immediately. */
    const node *mhp_n = obj_get(tgt, "monster_hp");
    const node *mdd_n = obj_get(tgt, "monster_dead");
    if (mhp_n || mdd_n) {
        if (!g_combat_ctx) {
            die_at(script_id, idx,
                   "mutate monster_hp/monster_dead: no combat ctx");
        }
        int32_t i = eval_expr(mhp_n ? mhp_n : mdd_n, script_id, idx,
                              bindings, env);
        if (i < 0 || i >= g_combat_ctx->noc) {
            die_at(script_id, idx,
                   "mutate monster_hp/monster_dead: idx out of range");
        }
        return (mutate_lv){mhp_n ? LV_MONSTER_HP : LV_MONSTER_DEAD,
                           i, NULL};
    }
    const node *bf_n = obj_get(tgt, "battle_flag");
    if (bf_n) {
        if (!g_combat_ctx) {
            die_at(script_id, idx, "mutate battle_flag: no combat ctx");
        }
        if (bf_n->kind != N_STR) {
            die_at(script_id, idx, "mutate battle_flag: name must be string");
        }
        return (mutate_lv){LV_BATTLE_FLAG, 0, bf_n->u.sv};
    }
    if (obj_get(tgt, "state_banished")) {
        return (mutate_lv){LV_STATE_BANISHED, 0, NULL};
    }
    die_at(script_id, idx, "mutate: unknown target shape");
}

static int32_t read_lv(const mutate_lv *lv, const char *script_id,
                       size_t idx) {
    switch (lv->kind) {
        case LV_GOLD:
            return pa_gold();
        case LV_TORCHES:
            return pa_torches();
        case LV_FOOD:
            return pa_food();
        case LV_PL:
            return pa_pl();
        case LV_HOURS_RESTED:
            return pa_hours_rested();
        case LV_HOUR_OF_DAY:
            return state.hour_of_day;
        case LV_TIME_LEFT:
            return pa_time_left();
        case LV_FLAG:
            return pa_flag(lv->index);
        case LV_INVENTORY:
            return pa_inventory(lv->index);
        case LV_MEMBER_FIELD: {
            pm_t pm = pm_cur(lv->index);
            const char *f = lv->field;
            if (strcmp(f, "lv") == 0) {
                return pm_lv(pm);
            }
            if (strcmp(f, "hp") == 0) {
                return pm_hp(pm);
            }
            if (strcmp(f, "h") == 0) {
                return pm_h(pm);
            }
            if (strcmp(f, "ma") == 0) {
                return pm_ma(pm);
            }
            if (strcmp(f, "wi") == 0) {
                return pm_wi(pm);
            }
            if (strcmp(f, "ws") == 0) {
                return pm_ws(pm);
            }
            if (strcmp(f, "ch") == 0) {
                return pm_ch(pm);
            }
            if (strcmp(f, "cl") == 0) {
                return pm_cl(pm);
            }
            if (strcmp(f, "ra") == 0) {
                return pm_ra(pm);
            }
            if (strcmp(f, "dead") == 0) {
                return pm_dead(pm);
            }
            if (strcmp(f, "ar") == 0) {
                return pm_ar(pm);
            }
            if (strcmp(f, "wp") == 0) {
                return pm_wp(pm);
            }
            if (strcmp(f, "exp") == 0) {
                return (int32_t)pm_exp(pm);
            }
            if (strcmp(f, "gender") == 0) {
                return pm_gender(pm);
            }
            die_at(script_id, idx, "mutate: member_field unknown field name");
        }
        case LV_MONSTER_HP:
            return g_combat_ctx->mh[lv->index];
        case LV_MONSTER_DEAD:
            return g_combat_ctx->deadc[lv->index];
        case LV_BATTLE_FLAG: {
            const char *f = lv->field;
            int *p = NULL;
            if (strcmp(f, "confused") == 0) {
                p = g_combat_ctx->confused;
            } else if (strcmp(f, "held") == 0) {
                p = g_combat_ctx->held;
            } else if (strcmp(f, "antimagic") == 0) {
                p = g_combat_ctx->antimagic;
            } else if (strcmp(f, "ws") == 0) {
                p = g_combat_ctx->ws;
            } else if (strcmp(f, "player_held") == 0) {
                p = g_combat_ctx->player_held;
            } else if (strcmp(f, "player_confused") == 0) {
                p = g_combat_ctx->player_confused;
            } else if (strcmp(f, "action_retry") == 0) {
                p = g_combat_ctx->action_retry;
            } else {
                die_at(script_id, idx,
                       "mutate battle_flag: unknown name");
            }
            if (!p) {
                die_at(script_id, idx,
                       "mutate battle_flag: pointer NULL — caller didn't expose");
            }
            return *p;
        }
        case LV_STATE_BANISHED:
            return state.banished;
    }
    die_at(script_id, idx, "mutate: corrupt lvalue tag");
}

static void write_lv(const mutate_lv *lv, int32_t v, const char *script_id,
                     size_t idx) {
    switch (lv->kind) {
        case LV_GOLD:
            pa_set_gold(v);
            return;
        case LV_TORCHES:
            if (v < 0) {
                die_at(script_id, idx, "mutate: torches cannot be negative");
            }
            pa_set_torches(v);
            return;
        case LV_FOOD:
            pa_set_food(v);
            return;
        case LV_PL:
            if (v < 0 || v > 255) {
                die_at(script_id, idx, "mutate: pl must be 0..255");
            }
            pa_set_pl((int)v);
            return;
        case LV_HOURS_RESTED:
            pa_set_hours_rested(v);
            return;
        case LV_HOUR_OF_DAY:
            if (v < 1 || v > 24) {
                die_at(script_id, idx,
                       "mutate: hour_of_day must be 1..24");
            }
            state.hour_of_day = v;
            return;
        case LV_TIME_LEFT:
            pa_set_time_left(v);
            return;
        case LV_FLAG:
            pa_set_flag(lv->index, (int)v);
            return;
        case LV_INVENTORY:
            pa_set_inventory(lv->index, v);
            return;
        case LV_MEMBER_FIELD: {
            pm_t pm = pm_cur(lv->index);
            const char *f = lv->field;
            if (strcmp(f, "lv") == 0) {
                pm_set_lv(pm, (int)v);
                return;
            }
            if (strcmp(f, "hp") == 0) {
                pm_set_hp(pm, (int)v);
                return;
            }
            if (strcmp(f, "h") == 0) {
                pm_set_h(pm, (int)v);
                return;
            }
            if (strcmp(f, "ma") == 0) {
                pm_set_ma(pm, (int)v);
                return;
            }
            if (strcmp(f, "wi") == 0) {
                pm_set_wi(pm, (int)v);
                return;
            }
            if (strcmp(f, "ws") == 0) {
                pm_set_ws(pm, (int)v);
                return;
            }
            if (strcmp(f, "ch") == 0) {
                pm_set_ch(pm, (int)v);
                return;
            }
            if (strcmp(f, "cl") == 0) {
                pm_set_cl(pm, (int)v);
                return;
            }
            if (strcmp(f, "ra") == 0) {
                pm_set_ra(pm, (int)v);
                return;
            }
            if (strcmp(f, "dead") == 0) {
                pm_set_dead(pm, (int)v);
                return;
            }
            if (strcmp(f, "ar") == 0) {
                pm_set_ar(pm, (int)v);
                return;
            }
            if (strcmp(f, "wp") == 0) {
                pm_set_wp(pm, (int)v);
                return;
            }
            if (strcmp(f, "exp") == 0) {
                if (v < 0) {
                    die_at(script_id, idx,
                           "mutate: member_field exp cannot be negative");
                }
                pm_set_exp(pm, (uint32_t)v);
                return;
            }
            if (strcmp(f, "gender") == 0) {
                pm_set_gender(pm, (unsigned char)v);
                return;
            }
            die_at(script_id, idx, "mutate: member_field unknown field name");
        }
        case LV_MONSTER_HP:
            g_combat_ctx->mh[lv->index] = (int)v;
            return;
        case LV_MONSTER_DEAD:
            g_combat_ctx->deadc[lv->index] = (int)v;
            return;
        case LV_BATTLE_FLAG: {
            const char *f = lv->field;
            int *p = NULL;
            if (strcmp(f, "confused") == 0) {
                p = g_combat_ctx->confused;
            } else if (strcmp(f, "held") == 0) {
                p = g_combat_ctx->held;
            } else if (strcmp(f, "antimagic") == 0) {
                p = g_combat_ctx->antimagic;
            } else if (strcmp(f, "ws") == 0) {
                p = g_combat_ctx->ws;
            } else if (strcmp(f, "player_held") == 0) {
                p = g_combat_ctx->player_held;
            } else if (strcmp(f, "player_confused") == 0) {
                p = g_combat_ctx->player_confused;
            } else if (strcmp(f, "action_retry") == 0) {
                p = g_combat_ctx->action_retry;
            } else {
                die_at(script_id, idx,
                       "mutate battle_flag: unknown name");
            }
            if (!p) {
                die_at(script_id, idx,
                       "mutate battle_flag: pointer NULL — caller didn't expose");
            }
            *p = (int)v;
            return;
        }
        case LV_STATE_BANISHED:
            state.banished = v;
            return;
    }
    die_at(script_id, idx, "mutate: corrupt lvalue tag");
}

/* §17-F step 3: { mutate: { target:<lvalue>, op:'set'|'add'|'sub'|
   'mul'|'div', value:<expr>, clamp?: { min:<expr>, max:<expr> } } }.
   Lvalues: { gold } | { torches } | { food } | { pl } |
   { flag:<expr> } | { inventory:<expr> } |
   { member_field: { idx:<expr>, field:'...' } } |
   { hours_rested } | { hour_of_day }.
   Optional clamp applied after the op. Divide-by-zero is fatal. */
static void do_mutate(const node *op, const char *script_id, size_t idx,
                      const ryl_bind *bindings, rt_env *env) {
    const node *body = obj_get(op, "mutate");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "mutate: value must be object");
    }
    const node *tgt = obj_get(body, "target");
    const node *op_n = obj_get(body, "op");
    const node *val_n = obj_get(body, "value");
    const node *clamp_n = obj_get(body, "clamp");
    if (!tgt) {
        die_at(script_id, idx, "mutate: missing target");
    }
    if (!op_n || op_n->kind != N_STR) {
        die_at(script_id, idx, "mutate: op must be string");
    }
    if (!val_n) {
        die_at(script_id, idx, "mutate: missing value");
    }
    mutate_lv lv = resolve_lv(tgt, script_id, idx, bindings, env);
    int32_t v = eval_expr(val_n, script_id, idx, bindings, env);
    int32_t cur = read_lv(&lv, script_id, idx);
    int32_t next;
    const char *op_s = op_n->u.sv;
    if (strcmp(op_s, "set") == 0) {
        next = v;
    } else if (strcmp(op_s, "add") == 0) {
        next = cur + v;
    } else if (strcmp(op_s, "sub") == 0) {
        next = cur - v;
    } else if (strcmp(op_s, "mul") == 0) {
        next = cur * v;
    } else if (strcmp(op_s, "div") == 0) {
        if (v == 0) {
            die_at(script_id, idx, "mutate: divide by zero");
        }
        next = cur / v;
    } else {
        die_at(script_id, idx,
               "mutate: op must be set/add/sub/mul/div");
    }
    if (clamp_n) {
        if (clamp_n->kind != N_OBJ) {
            die_at(script_id, idx, "mutate: clamp must be object");
        }
        const node *min_n = obj_get(clamp_n, "min");
        const node *max_n = obj_get(clamp_n, "max");
        if (min_n) {
            int32_t lo = eval_expr(min_n, script_id, idx, bindings, env);
            if (next < lo) {
                next = lo;
            }
        }
        if (max_n) {
            int32_t hi = eval_expr(max_n, script_id, idx, bindings, env);
            if (next > hi) {
                next = hi;
            }
        }
    }
    write_lv(&lv, next, script_id, idx);
}

/* §17-F step 3: { input_number: { prompt_screen?: '<name>', lg: N,
   var: '<name>', min: <expr>, max: <expr> } }. Optionally emits
   prompt_screen via tx_fmt (parent-relative, merged bindings), then
   reads a line with getstr(lg), parses with strtol, clamps into
   [min, max], and binds the result into rt_env under `var`. Invalid
   (non-numeric / empty) input clamps to `min`. */
static void do_input_number(const node *op, const char *script_id, size_t idx,
                            const char *parent, const ryl_bind *bindings,
                            rt_env *env) {
    const node *body = obj_get(op, "input_number");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "input_number: value must be object");
    }
    const node *ps_n = obj_get(body, "prompt_screen");
    const node *lg_n = obj_get(body, "lg");
    const node *var_n = obj_get(body, "var");
    const node *min_n = obj_get(body, "min");
    const node *max_n = obj_get(body, "max");
    if (!lg_n || lg_n->kind != N_INT) {
        die_at(script_id, idx, "input_number: lg must be int");
    }
    if (lg_n->u.iv < 1 || lg_n->u.iv > 255) {
        die_at(script_id, idx, "input_number: lg out of range (1..255)");
    }
    if (!var_n || var_n->kind != N_STR) {
        die_at(script_id, idx, "input_number: var must be string");
    }
    if (!min_n || !max_n) {
        die_at(script_id, idx, "input_number: min and max required");
    }
    int32_t lo = eval_expr(min_n, script_id, idx, bindings, env);
    int32_t hi = eval_expr(max_n, script_id, idx, bindings, env);
    if (lo > hi) {
        die_at(script_id, idx, "input_number: min > max");
    }
    if (ps_n) {
        if (ps_n->kind != N_STR) {
            die_at(script_id, idx,
                   "input_number: prompt_screen must be string");
        }
        char text_id[256];
        resolve_text_id(parent, ps_n->u.sv, text_id, sizeof text_id,
                        script_id, idx, "input_number prompt_screen");
        ryl_bind merged[RT_ENV_CAP + 32];
        tx_fmt(text_id,
               merge_bindings(bindings, env, merged,
                              sizeof merged / sizeof *merged, script_id, idx));
    }
    char buf[64];
    uint8_t lg = (uint8_t)lg_n->u.iv;
    if (lg >= sizeof buf) {
        lg = (uint8_t)(sizeof buf - 1);
    }
    getstr(lg, buf, sizeof buf);
    char *endp = NULL;
    long parsed = strtol(buf, &endp, 10);
    int32_t v;
    if (!endp || endp == buf) {
        v = lo;
    } else {
        v = (int32_t)parsed;
    }
    if (v < lo) {
        v = lo;
    }
    if (v > hi) {
        v = hi;
    }
    rt_bind_set(env, var_n->u.sv, v, script_id, idx);
}

/* §20 L2: { inv_add: { item: <expr>, added?: [...], full?: [...] } }.
   Calls inv_add(item) and dispatches on the return — slot != 0 means
   placed (runs `added`), slot == 0 means inventory full (runs `full`).
   Distinct from the reward op's `cant_carry_screen` path because the
   chest/object find flow doesn't offer drop_option — full is just a
   "you can't carry more" message + delay. Item arg is any expression
   (typically a `{ var: 'oc' }` from the find-object roll). */
static ops_result do_inv_add(const node *op, const char *script_id,
                             size_t idx, const char *parent,
                             const ryl_bind *bindings, rt_env *env) {
    const node *body = obj_get(op, "inv_add");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "inv_add: value must be object");
    }
    const node *item_n = obj_get(body, "item");
    if (!item_n) {
        die_at(script_id, idx, "inv_add: item required");
    }
    int32_t item = eval_expr(item_n, script_id, idx, bindings, env);
    int slot = inv_add((int)item);
    const node *take = (slot != 0) ? obj_get(body, "added")
                                   : obj_get(body, "full");
    if (!take) {
        return OPS_NORMAL;
    }
    if (take->kind != N_ARR) {
        die_at(script_id, idx, "inv_add: added/full must be array");
    }
    return play_ops(take, script_id, parent, bindings, env);
}

/* §20 L2: { input_string: { lg: N, accept: '<sym>'|[ '<sym>'... ],
   match?: [...], miss?: [...] } }. Reads a getstr(lg)-bounded line,
   compares it case-insensitively against each `accept` entry, and
   plays the matching body. Used by the dungeon riddle/password
   encounters (Thun riddles, hobgoblin chief). lg caps at 64 to fit
   the local buffer; both bodies are optional (a missing body is a
   no-op fall-through). Bubbles through break/continue from the
   outer loop, like do_menu_branch. */
static ops_result do_input_string(const node *op, const char *script_id,
                                  size_t idx, const char *parent,
                                  const ryl_bind *bindings, rt_env *env) {
    const node *body = obj_get(op, "input_string");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "input_string: value must be object");
    }
    const node *lg_n = obj_get(body, "lg");
    const node *acc_n = obj_get(body, "accept");
    const node *match_n = obj_get(body, "match");
    const node *miss_n = obj_get(body, "miss");
    if (!lg_n || lg_n->kind != N_INT) {
        die_at(script_id, idx, "input_string: lg must be int");
    }
    if (lg_n->u.iv < 1 || lg_n->u.iv > 63) {
        die_at(script_id, idx, "input_string: lg out of range (1..63)");
    }
    if (!acc_n) {
        die_at(script_id, idx, "input_string: accept required");
    }
    if (acc_n->kind != N_STR && acc_n->kind != N_ARR) {
        die_at(script_id, idx, "input_string: accept must be string or array");
    }
    char buf[64];
    getstr((uint8_t)lg_n->u.iv, buf, sizeof buf);
    int matched = 0;
    if (acc_n->kind == N_STR) {
        matched = (stricmp(buf, acc_n->u.sv) == 0);
    } else {
        for (size_t i = 0; i < acc_n->u.arr.n; i++) {
            const node *e = acc_n->u.arr.items[i];
            if (!e || e->kind != N_STR) {
                die_at(script_id, idx,
                       "input_string: accept entry must be string");
            }
            if (stricmp(buf, e->u.sv) == 0) {
                matched = 1;
                break;
            }
        }
    }
    const node *take = matched ? match_n : miss_n;
    if (!take) {
        return OPS_NORMAL;
    }
    if (take->kind != N_ARR) {
        die_at(script_id, idx, "input_string: match/miss must be array");
    }
    return play_ops(take, script_id, parent, bindings, env);
}

/* §20 follow-up: { input_item: { slot_var: '<name>', item_var: '<name>',
   then?: [...ops...] } }. Reads one key (uppercased), validates it as
   an inventory letter (A..P -> slot 1..16). On valid: binds slot_var
   to the slot index and item_var to state.p[cpl].inventory[slot] as
   rt_env ints, then runs `then:`. On any other key: skips `then:`
   silently — callers that want abort-on-cancel keep the rest of the
   logic inside `then:`. Replaces the speak.c jazzy_ogre_trader thin
   C wrapper that hand-rolled the same key/slot/dispatch shape. */
static ops_result do_input_item(const node *op, const char *script_id, size_t idx,
                                const char *parent, const ryl_bind *bindings,
                                rt_env *env) {
    const node *body = obj_get(op, "input_item");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "input_item: value must be object");
    }
    const node *slot_var_n = obj_get(body, "slot_var");
    const node *item_var_n = obj_get(body, "item_var");
    const node *then_n = obj_get(body, "then");
    if (!slot_var_n || slot_var_n->kind != N_STR) {
        die_at(script_id, idx, "input_item: slot_var must be string");
    }
    if (!item_var_n || item_var_n->kind != N_STR) {
        die_at(script_id, idx, "input_item: item_var must be string");
    }
    char d = (char)toupper((unsigned char)key());
    if (d < 'A' || d > 'P') {
        return OPS_NORMAL;
    }
    int slot = d - 'A' + 1;
    int oc = pa_inventory(slot);
    rt_bind_set(env, slot_var_n->u.sv, slot, script_id, idx);
    rt_bind_set(env, item_var_n->u.sv, oc, script_id, idx);
    if (!then_n) {
        return OPS_NORMAL;
    }
    if (then_n->kind != N_ARR) {
        die_at(script_id, idx, "input_item: then must be array");
    }
    return play_ops(then_n, script_id, parent, bindings, env);
}

/* §22 Lane A: { ask_target: 'monster_alive', var: '<name>' } picks one
   live monster from the combat ctx and binds the (1-based) index into
   rt_env. The render loop reproduces battle.c:424-441 byte-for-byte —
   `clw + battle.prompt.who` header, one "letter. Alive./Dead. (NNN hp)"
   row per monster, keypress that must land on a live slot — so a DSL-
   driven cast emits the exact same bytes as the legacy hand-rolled
   monster picker. Fatal outside combat ctx.

   { ask_target: 'party_member', var: '<name>' } prints "Who? (1-N)"
   and loops on key() until pm_digit_in_range fires; binds the digit
   1..party_max into rt_env. Mirrors the prompt every priest spell
   uses to pick a target party member (subs.c). */
static void do_ask_target(const node *op, const char *script_id, size_t idx,
                          rt_env *env) {
    const node *kind_n = obj_get(op, "ask_target");
    const node *var_n = obj_get(op, "var");
    if (!kind_n || kind_n->kind != N_STR) {
        die_at(script_id, idx, "ask_target: kind must be string");
    }
    if (!var_n || var_n->kind != N_STR) {
        die_at(script_id, idx, "ask_target: var must be string");
    }
    if (strcmp(kind_n->u.sv, "monster_alive") == 0) {
        if (!g_combat_ctx) {
            die_at(script_id, idx,
                   "ask_target monster_alive: no combat ctx");
        }
        char str[16], d;
        while (1) {
            clw();
            tx_fmt("battle.prompt.who", NULL);
            for_each_monster(i, g_combat_ctx->noc) {
                if (g_combat_ctx->deadc[i]) {
                    strcpy(str, "Dead.");
                } else {
                    strcpy(str, "Alive.");
                }
                tx("%c. %-7s (%3d hp)\r\n", 'A' + i, str, g_combat_ctx->mh[i]);
            }
            d = key();
            d = (char)toupper((unsigned char)d);
            if (d >= 'A' && d < 'A' + g_combat_ctx->noc &&
                !g_combat_ctx->deadc[d - 'A']) {
                break;
            }
        }
        int32_t mon_slot = (int32_t)(d - 'A');
        LOG_D("input", "ask_target monster_alive: letter=%c -> slot=%d (bind '%s')",
              d, mon_slot, var_n->u.sv);
        rt_bind_set(env, var_n->u.sv, mon_slot, script_id, idx);
        return;
    }
    if (strcmp(kind_n->u.sv, "party_member") == 0) {
        char d;
        tx("Who? (1-%d)\r\n", res_party_member_max());
        while (1) {
            d = key();
            if (pm_digit_in_range(d)) {
                break;
            }
        }
        int32_t mem_slot = (int32_t)(d - '1');
        LOG_D("input", "ask_target party_member: digit=%c -> slot=%d (bind '%s')",
              d, mem_slot, var_n->u.sv);
        rt_bind_set(env, var_n->u.sv, mem_slot, script_id, idx);
        return;
    }
    die_at(script_id, idx,
           "ask_target: kind must be 'monster_alive' or 'party_member'");
}

/* §17-F: { loop: [...ops...] } — run the body repeatedly until an
   inner `{ break }` fires. `{ continue }` restarts the loop early;
   a natural fall-off of the end of the body also restarts (the
   loop is infinite by construction). Typically paired with a
   menu_branch whose L-option then: is a `{ break }`. */
static ops_result do_loop(const node *op, const char *script_id, size_t idx,
                          const char *parent, const ryl_bind *bindings,
                          rt_env *env) {
    const node *body = obj_get(op, "loop");
    if (!body || body->kind != N_ARR) {
        die_at(script_id, idx, "loop: value must be array");
    }
    for (;;) {
        ops_result r = play_ops(body, script_id, parent, bindings, env);
        if (r == OPS_BREAK) {
            return OPS_NORMAL;
        }
        /* OPS_NORMAL and OPS_CONTINUE both iterate. */
    }
}

/* §17-F: { break } — exits innermost enclosing `loop`. Runtime error
   if the nearest enclosing op is not a loop (checked at top-level
   unwinding in play_script). */
static ops_result do_break(const node *op, const char *script_id, size_t idx) {
    const node *v = obj_get(op, "break");
    if (!v) {
        die_at(script_id, idx, "break: missing value");
    }
    (void)v;
    return OPS_BREAK;
}

/* §17-F: { continue } — restarts innermost enclosing `loop`. */
static ops_result do_continue(const node *op, const char *script_id,
                              size_t idx) {
    const node *v = obj_get(op, "continue");
    if (!v) {
        die_at(script_id, idx, "continue: missing value");
    }
    (void)v;
    return OPS_CONTINUE;
}

/* §17-F step 4 shared helper. Both for_member and for_inventory have
   the same shape: validate { var:'name', then:[...] }, push the loop
   var as a fresh scope, iterate lo..hi updating the same slot each
   pass, honor break/continue, rewind env on exit. `body_key` is the
   outer op keyword used in diagnostic messages ("for_member" /
   "for_inventory"). */
static ops_result do_for_range(const node *op, const char *script_id,
                               size_t idx, const char *parent,
                               const ryl_bind *bindings, rt_env *env,
                               const char *body_key, int32_t lo, int32_t hi) {
    const node *body = obj_get(op, body_key);
    if (!body || body->kind != N_OBJ) {
        char buf[64];
        snprintf(buf, sizeof buf, "%s: value must be object", body_key);
        die_at(script_id, idx, buf);
    }
    const node *var_n = obj_get(body, "var");
    const node *then_n = obj_get(body, "then");
    if (!var_n || var_n->kind != N_STR) {
        char buf[64];
        snprintf(buf, sizeof buf, "%s: var must be string", body_key);
        die_at(script_id, idx, buf);
    }
    if (!then_n || then_n->kind != N_ARR) {
        char buf[64];
        snprintf(buf, sizeof buf, "%s: then must be array", body_key);
        die_at(script_id, idx, buf);
    }
    size_t mark = rt_env_mark(env);
    size_t slot = rt_bind_push_int(env, var_n->u.sv, lo, script_id, idx);
    for (int32_t i = lo; i <= hi; i++) {
        rt_bind_update_int(env, slot, i);
        ops_result r = play_ops(then_n, script_id, parent, bindings, env);
        if (r == OPS_BREAK) {
            rt_env_rewind(env, mark);
            return OPS_NORMAL;
        }
        /* OPS_NORMAL and OPS_CONTINUE both fall through to next iter. */
    }
    rt_env_rewind(env, mark);
    return OPS_NORMAL;
}

/* §17-F step 4: { for_member: { var:'name', then:[...] } } — binds
   `name` to 0..res_party_member_max() - 1 in turn. Body sees the
   loop var via `{var:'name'}` in expressions and `{name}` in
   screen / journal text. Break and continue propagate through the
   shared do_for_range helper. */
static ops_result do_for_member(const node *op, const char *script_id,
                                size_t idx, const char *parent,
                                const ryl_bind *bindings, rt_env *env) {
    return do_for_range(op, script_id, idx, parent, bindings, env,
                        "for_member", 0, res_party_member_max() - 1);
}

/* §17-F step 4: { for_inventory: { var:'name', then:[...] } } — same
   shape as for_member, iterating 1..res_inventory_slot_max() (the
   party inventory slot range; typically 16 but foldable via
   limits.inventory_slot_max). Body must guard on `{inventory:
   <var>}` > 0 if it only cares about occupied slots. */
static ops_result do_for_inventory(const node *op, const char *script_id,
                                   size_t idx, const char *parent,
                                   const ryl_bind *bindings, rt_env *env) {
    return do_for_range(op, script_id, idx, parent, bindings, env,
                        "for_inventory", 1, res_inventory_slot_max());
}

/* §22 Lane A: { for_monster: { var:'name', then:[...] } } — iterate
   0..combat_ctx->noc-1. Pairs with monster_hp / monster_dead reads
   so a mass-AOE spell ({mage.fire}) can apply per-monster damage.
   Fatal outside a combat ctx. */
static ops_result do_for_monster(const node *op, const char *script_id,
                                 size_t idx, const char *parent,
                                 const ryl_bind *bindings, rt_env *env) {
    if (!g_combat_ctx) {
        die_at(script_id, idx, "for_monster: no combat ctx");
    }
    return do_for_range(op, script_id, idx, parent, bindings, env,
                        "for_monster", 0, g_combat_ctx->noc - 1);
}

/* §23 prep: shared helper for the alive-only iterators. Same shape as
   do_for_range but takes a fixed loop-var name and skips indices where
   the predicate is false (no rt_env update, no body call, no RNG draw).
   The body sees the loop var as `{ var: '<fixed_var>' }`; inner
   break/continue propagate. `kind` selects the predicate:
     ALIVE_MONSTER → !g_combat_ctx->deadc[i] (caller validates ctx)
     ALIVE_PLAYER  → pa_is_alive(i)          (no ctx required) */
typedef enum { ALIVE_MONSTER, ALIVE_PLAYER } alive_kind;

static int alive_at(alive_kind kind, int32_t i) {
    if (kind == ALIVE_MONSTER) {
        return !g_combat_ctx->deadc[i];
    }
    return pa_is_alive((int)i);
}

static ops_result do_for_range_alive(const node *op, const char *script_id,
                                     size_t idx, const char *parent,
                                     const ryl_bind *bindings, rt_env *env,
                                     const char *body_key, alive_kind kind,
                                     const char *fixed_var,
                                     int32_t lo, int32_t hi) {
    const node *body = obj_get(op, body_key);
    if (!body || body->kind != N_OBJ) {
        char buf[64];
        snprintf(buf, sizeof buf, "%s: value must be object", body_key);
        die_at(script_id, idx, buf);
    }
    const node *then_n = obj_get(body, "then");
    if (!then_n || then_n->kind != N_ARR) {
        char buf[64];
        snprintf(buf, sizeof buf, "%s: then must be array", body_key);
        die_at(script_id, idx, buf);
    }
    size_t mark = rt_env_mark(env);
    size_t slot = rt_bind_push_int(env, fixed_var, lo, script_id, idx);
    for (int32_t i = lo; i <= hi; i++) {
        if (!alive_at(kind, i)) {
            continue;
        }
        rt_bind_update_int(env, slot, i);
        ops_result r = play_ops(then_n, script_id, parent, bindings, env);
        if (r == OPS_BREAK) {
            rt_env_rewind(env, mark);
            return OPS_NORMAL;
        }
    }
    rt_env_rewind(env, mark);
    return OPS_NORMAL;
}

/* §23 prep: { for_each_alive_monster: { then: [...] } } — iterate
   1..combat_ctx->noc, skipping slots where deadc[i] is set. Body
   sees the current monster slot as `{ var: 'monster_idx' }` (fixed
   name, no `var:` parameter — these ops formalize the §23 monster-
   spell pattern where battle.c reads/writes a stable index). Fatal
   outside a combat ctx. RNG and side effects only fire on alive
   slots — note this differs from for_monster, which always loops
   noc times for byte-identity with the legacy mage.fire AOE. */
static ops_result do_for_each_alive_monster(const node *op,
                                            const char *script_id, size_t idx,
                                            const char *parent,
                                            const ryl_bind *bindings,
                                            rt_env *env) {
    if (!g_combat_ctx) {
        die_at(script_id, idx, "for_each_alive_monster: no combat ctx");
    }
    return do_for_range_alive(op, script_id, idx, parent, bindings, env,
                              "for_each_alive_monster", ALIVE_MONSTER,
                              "monster_idx", 0, g_combat_ctx->noc - 1);
}

/* §23 prep: { for_each_alive_player: { then: [...] } } — iterate
   0..res_party_member_max() - 1, skipping members with pm_dead set.
   Body sees the current member slot as `{ var: 'target_idx' }` —
   the same name `ask_target` uses for picked targets, so single-
   target ops compose under the iterator. No combat ctx required
   (works in both subs.c-style and battle.c-style callers). */
static ops_result do_for_each_alive_player(const node *op,
                                           const char *script_id, size_t idx,
                                           const char *parent,
                                           const ryl_bind *bindings,
                                           rt_env *env) {
    return do_for_range_alive(op, script_id, idx, parent, bindings, env,
                              "for_each_alive_player", ALIVE_PLAYER,
                              "target_idx", 0, res_party_member_max() - 1);
}

/* §17-F step 4: { switch: { expr:<expr>, cases:[...] } }. Each case
   is either { match:<expr>, then:[...] }, { in:[<expr>,...],
   then:[...] }, or { default:true, then:[...] }. First-match-wins;
   no fall-through. Case values are re-evaluated on every switch
   execution (cheap — they're usually literals). `default` must be
   exactly the bool true; an explicit false is not meaningful. */
static ops_result do_switch(const node *op, const char *script_id, size_t idx,
                            const char *parent, const ryl_bind *bindings,
                            rt_env *env) {
    const node *body = obj_get(op, "switch");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "switch: value must be object");
    }
    const node *expr_n = obj_get(body, "expr");
    const node *cases_n = obj_get(body, "cases");
    if (!expr_n) {
        die_at(script_id, idx, "switch: missing expr");
    }
    if (!cases_n || cases_n->kind != N_ARR) {
        die_at(script_id, idx, "switch: cases must be array");
    }
    int32_t v = eval_expr(expr_n, script_id, idx, bindings, env);
    for (size_t i = 0; i < cases_n->u.arr.n; i++) {
        const node *c = cases_n->u.arr.items[i];
        if (!c || c->kind != N_OBJ) {
            die_at(script_id, idx, "switch: case must be object");
        }
        const node *match_n = obj_get(c, "match");
        const node *in_n = obj_get(c, "in");
        const node *default_n = obj_get(c, "default");
        const node *then_n = obj_get(c, "then");
        if ((match_n ? 1 : 0) + (in_n ? 1 : 0) + (default_n ? 1 : 0) != 1) {
            die_at(script_id, idx,
                   "switch: case must have exactly one of match/in/default");
        }
        if (!then_n || then_n->kind != N_ARR) {
            die_at(script_id, idx, "switch: case then must be array");
        }
        int fire = 0;
        if (match_n) {
            int32_t mv = eval_expr(match_n, script_id, idx, bindings, env);
            fire = (v == mv);
        } else if (in_n) {
            if (in_n->kind != N_ARR || in_n->u.arr.n == 0) {
                die_at(script_id, idx,
                       "switch: in must be non-empty array");
            }
            for (size_t k = 0; k < in_n->u.arr.n; k++) {
                int32_t iv = eval_expr(in_n->u.arr.items[k], script_id, idx,
                                       bindings, env);
                if (v == iv) {
                    fire = 1;
                    break;
                }
            }
        } else {
            if (default_n->kind != N_BOOL || !default_n->u.bv) {
                die_at(script_id, idx, "switch: default must be true");
            }
            fire = 1;
        }
        if (fire) {
            return play_ops(then_n, script_id, parent, bindings, env);
        }
    }
    return OPS_NORMAL;
}

/* §22.F: invoke a nested script by id, forwarding the current
   bindings + rt_env entries (rt_env first so it shadows). The nested
   play_script gets a fresh rt_env so its own let/engine_bind work
   doesn't leak back. Used by the speak_missions dispatcher scripts
   to fan out into the existing speak.<x>.<y> bodies after gating
   on flags + populating role-keyed bindings. */
static void do_play_script(const node *op, const char *script_id, size_t idx,
                           const ryl_bind *bindings, rt_env *env) {
    const node *id_n = obj_get(op, "play_script");
    if (!id_n || id_n->kind != N_STR) {
        die_at(script_id, idx, "play_script: id must be string");
    }
    ryl_bind scratch[RT_ENV_CAP + 16];
    const ryl_bind *merged = merge_bindings(bindings, env, scratch,
                                            sizeof scratch / sizeof *scratch,
                                            script_id, idx);
    play_script(id_n->u.sv, merged);
}

static ops_result dispatch_op(const node *op, const char *script_id, size_t idx,
                              const char *parent, const ryl_bind *bindings,
                              rt_env *env) {
    if (!op || op->kind != N_OBJ) {
        die_at(script_id, idx, "op must be object");
    }
    if (obj_get(op, "screen")) {
        do_screen(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "journal")) {
        do_journal(op, script_id, idx, bindings, env);
    } else if (obj_get(op, "set_flag")) {
        do_set_flag(op, script_id, idx);
    } else if (obj_get(op, "reward")) {
        do_reward(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "pause")) {
        do_pause(op, script_id, idx);
    } else if (obj_get(op, "emit")) {
        do_emit(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "emit_indexed")) {
        do_emit_indexed(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "wait_input")) {
        do_wait_input(op, script_id, idx);
    } else if (obj_get(op, "if")) {
        return do_if(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "menu_branch")) {
        return do_menu_branch(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "dungeon")) {
        do_dungeon(op, script_id, idx, bindings, env);
    } else if (obj_get(op, "speak")) {
        do_speak(op, script_id, idx, bindings, env);
    } else if (obj_get(op, "encounter")) {
        do_encounter(op, script_id, idx, bindings, env);
    } else if (obj_get(op, "engine_call")) {
        do_engine_call(op, script_id, idx, bindings, env);
    } else if (obj_get(op, "engine_bind")) {
        do_engine_bind(op, script_id, idx, bindings, env);
    } else if (obj_get(op, "loop")) {
        return do_loop(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "break")) {
        return do_break(op, script_id, idx);
    } else if (obj_get(op, "continue")) {
        return do_continue(op, script_id, idx);
    } else if (obj_get(op, "let")) {
        do_let(op, script_id, idx, bindings, env);
    } else if (obj_get(op, "mutate")) {
        do_mutate(op, script_id, idx, bindings, env);
    } else if (obj_get(op, "input_number")) {
        do_input_number(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "input_string")) {
        return do_input_string(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "inv_add")) {
        return do_inv_add(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "input_item")) {
        return do_input_item(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "for_member")) {
        return do_for_member(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "for_inventory")) {
        return do_for_inventory(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "for_monster")) {
        return do_for_monster(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "for_each_alive_monster")) {
        return do_for_each_alive_monster(op, script_id, idx, parent, bindings,
                                         env);
    } else if (obj_get(op, "for_each_alive_player")) {
        return do_for_each_alive_player(op, script_id, idx, parent, bindings,
                                        env);
    } else if (obj_get(op, "switch")) {
        return do_switch(op, script_id, idx, parent, bindings, env);
    } else if (obj_get(op, "ask_target")) {
        do_ask_target(op, script_id, idx, env);
    } else if (obj_get(op, "play_script")) {
        do_play_script(op, script_id, idx, bindings, env);
    } else {
        die_at(script_id, idx, "unknown op");
    }
    return OPS_NORMAL;
}

static ops_result play_ops(const node *ops, const char *script_id,
                           const char *parent, const ryl_bind *bindings,
                           rt_env *env) {
    for (size_t i = 0; i < ops->u.arr.n; i++) {
        ops_result r = dispatch_op(ops->u.arr.items[i], script_id, i, parent,
                                   bindings, env);
        if (r != OPS_NORMAL) {
            return r;
        }
    }
    return OPS_NORMAL;
}

void play_script(const char *script_id, const ryl_bind *bindings) {
    const node *script = res_script(script_id);
    if (!script) {
        char buf[256];
        snprintf(buf, sizeof buf, "play_script: script '%s' not found", script_id);
        ryl_die(buf);
    }
    char parent[256];
    script_parent(script_id, parent, sizeof parent);
    rt_env env = {{{0}}, 0};
    ops_result r = play_ops(script, script_id, parent, bindings, &env);
    if (r == OPS_BREAK) {
        ryl_die("play_script: `break` with no enclosing loop");
    }
    if (r == OPS_CONTINUE) {
        ryl_die("play_script: `continue` with no enclosing loop");
    }
}

/* §22 Lane A: evaluate a formulas.<name> body via the script DSL's
   eval_expr. Caller-supplied `bindings` carry the input ints (e.g.
   $h1_base, $party_avg) — the formula reads them via {var:'<name>'}.
   Wraps eval_expr with a fresh rt_env so any rt_env state from
   surrounding play_script / play_spell_body invocations doesn't
   leak in (formulas are pure functions of the bindings). */
int32_t play_formula_int(const char *formula_id, const struct node *body,
                         const ryl_bind *bindings) {
    if (!body) {
        char buf[160];
        snprintf(buf, sizeof buf,
                 "play_formula_int: '%s' body is NULL", formula_id);
        ryl_die(buf);
    }
    rt_env env = {{{0}}, 0};
    return eval_expr((const node *)body, formula_id, 0, bindings, &env);
}

/* §22 Lane A entry point. Same machinery as play_script but takes a
   pre-resolved body node (from res_spell_node) and a combat ctx
   pointer; spell_id is borrowed for diagnostics so a die_at fires
   with "spells.mage.frost op N: ..." instead of an anonymous body.
   The static g_combat_ctx is set on entry and cleared on exit so
   any non-combat caller of script.c that runs while a spell body
   is suspended (none today, but a future engine_call into a script
   that itself called play_script would compose) sees NULL. */
void play_spell_body(const char *spell_id, const struct node *body,
                     const ryl_bind *bindings,
                     const struct script_combat_ctx *ctx) {
    const node *b = (const node *)body;
    if (!b || b->kind != N_ARR) {
        char buf[160];
        snprintf(buf, sizeof buf,
                 "play_spell_body: '%s' body not an array", spell_id);
        ryl_die(buf);
    }
    char parent[256];
    script_parent(spell_id, parent, sizeof parent);
    const struct script_combat_ctx *prev = g_combat_ctx;
    g_combat_ctx = ctx;
    rt_env env = {{{0}}, 0};
    /* §23 prep: surface combat-ctx scalar `h2` (per-encounter monster
       max-HP) as a top-level rt_env binding so `do:` bodies can read
       it via `{ var: 'h2' }`. Out-of-combat callers (subs.c) leave
       ctx NULL — h2 stays unbound there and any `{ var: 'h2' }` will
       die with the standard "expr var: unbound" message. */
    if (ctx) {
        rt_bind_push_int(&env, "h2", ctx->h2, spell_id, 0);
    }
    ops_result r = play_ops(b, spell_id, parent, bindings, &env);
    g_combat_ctx = prev;
    if (r == OPS_BREAK) {
        ryl_die("play_spell_body: `break` with no enclosing loop");
    }
    if (r == OPS_CONTINUE) {
        ryl_die("play_spell_body: `continue` with no enclosing loop");
    }
}
