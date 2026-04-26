#ifndef SCRIPT_H__
#define SCRIPT_H__

#include "primitives.h"

/* Forward decl of the JSON5 node — script body access for play_spell_body
   below. The struct lives in src/json5.h; we don't pull it in here so
   battle.c / subs.c can include script.h without dragging in the parser
   surface. Callers that already speak in nodes (battle.c via
   res_spell_node) simply pass the void* through. */
struct node;

/* §16.4 + §17-E + §17-F dialogue / mission script interpreter.
 *
 * The single-doc reference for the DSL — every op, expression form,
 * lvalue, registry, and the text-binding rules — lives at
 * docs/dsl.md. This header is the in-source companion: it documents
 * the surface the runtime accepts in enough detail to keep the C
 * call sites self-contained, but designers and authors should read
 * docs/dsl.md first.
 *
 * A script is an array node in the `scripts.*` subtree of data/world.json5
 * (authored in data/world/scripts/). Each element is a one-op object.
 *
 * An rt_env threaded through every nested body holds runtime bindings
 * introduced by `let` / `input_number` / `menu_branch key_var` (and,
 * in later §17-F steps, for_member / for_inventory loop vars). These
 * bindings are visible both to `{var:'<name>'}` in expressions and to
 * `{<name>}` placeholder expansion in every text-emitting op (screen,
 * journal, emit, menu_branch prompt, input_number prompt_screen,
 * reward cant_carry / picked_up screens). rt_env entries shadow
 * caller-supplied ryl_bind entries with the same name.
 *
 * Ops:
 *
 *   { screen:   '<name>' }        - tx_fmt(parent.<name>, bindings)
 *                                    + ryl_pause() + clw()
 *   { journal:  '<text>' }        - bind_expand(text, bindings) prepended
 *                                    with "<cpl> " and a trailing "\n",
 *                                    forwarded to journal()
 *   { set_flag: <slot|'name'>,    - state.p[cpl].flag[slot] = value
 *     value: <int> }
 *   { reward: {                   - gold += N; for each items[] entry,
 *       gold: <int>,                place into first-empty inventory
 *       items: [<int|str>...],      slot (1..16). On no-free-slot and
 *       cant_carry_screen:          cant_carry_screen set, emit screen
 *         '<name>',                 (delay 800ms, drop_option, optional
 *       picked_up_screen:           picked_up_screen on success, delay
 *         '<name>' } }              800ms, ryl_pause).
 *
 * §17-E control-flow ops (added for A.4 / §17 slices):
 *
 *   { emit: '<name>' }            - tx_fmt only (no ryl_pause, no clw).
 *                                    For flows that interleave text with
 *                                    engine_call side effects where the
 *                                    screen-op's bundled pause+clw would
 *                                    over-clear.
 *
 *   { emit_indexed: {             - emit a text id constructed by
 *       fmt: 'speak.lodge.j{}',     substituting an int into a format.
 *       value: <expr> } }           Mirrors C-side txi(fmt, val): fmt
 *                                    must contain exactly one '{}'
 *                                    placeholder, replaced with the
 *                                    decimal value. Strict — die_at if
 *                                    the resolved id has no text record.
 *
 *   { wait_input: true }          - ryl_pause() (prompts "Smash RETURN
 *                                    key", blocks until enter). Distinct
 *                                    from `pause`, which is ryl_delay.
 *
 *   { pause: N }                  - ryl_delay(N) ms between screens.
 *
 *   { if: {                       - cond is any expression (eval_expr);
 *       cond: <expr>,               truthy fires `then`, else fires
 *       then: [...],                `else`. For probability gates use
 *       else: [...] } }             `cond: { chance: <pct-expr> }`.
 *
 *   { menu_branch: {              - one ryl_menu-style keypress.
 *       prompt: '<name>',           Each option matches on exactly one
 *       key_var: '<name>',          of `key:'K'` (single char),
 *       options: [                  `keys:['A','B',...]` (any listed
 *         { key:       'E',         char), or `key_range:['LO','HI']`
 *           then: [...] },          (inclusive). Optional top-level
 *         { keys:      ['A','B'],   `key_var` binds the matched
 *           then: [...] },          (uppercased) key into rt_env as a
 *         { key_range: ['A','P'],   one-char string for the selected
 *           then: [...] } ],        then: body. Unmatched keys fall to
 *       default: [...] } }          optional `default: [...]`.
 *
 *   { engine_call: '<name>',      - call a registered C entry point with
 *     args: [<int>...] }            literal int args. Registry lives in
 *                                   script.c (engine_tbl). Currently:
 *                                   dungeon(2) encounter(2) update_map(0)
 *                                   clr_map_window(0) blitgfx(1)
 *                                   statusline(0) clw(0).
 *
 *   { play_script: '<id>' }       - invoke a nested script by dotted id
 *                                   (resolved via res_script). Caller
 *                                   bindings + the current rt_env entries
 *                                   are forwarded as the nested call's
 *                                   ryl_bind list (rt_env first so it
 *                                   shadows). The nested script gets a
 *                                   fresh rt_env. Used by the speak_
 *                                   missions dispatcher scripts to fan
 *                                   out into the existing speak.<x>.<y>
 *                                   bodies after gating on flags +
 *                                   populating role-keyed bindings.
 *
 * §17-F control-flow ops (step 1 — loop primitives):
 *
 *   { loop: [...ops...] }         - run the body repeatedly until an
 *                                   inner `break` fires. `continue`
 *                                   restarts. Natural fall-off of the
 *                                   end of body also restarts (body
 *                                   is an infinite loop by
 *                                   construction — every loop must
 *                                   have a break path).
 *
 *   { break: true }               - exit innermost enclosing loop.
 *                                   Fatal error if no enclosing loop.
 *                                   Typical use: menu_branch's L-option
 *                                   then: is `[{ break: true }]`.
 *
 *   { continue: true }            - restart innermost enclosing loop.
 *                                   Fatal error if no enclosing loop.
 *
 * §17-F step 2 — expression evaluator (used in if.cond, mutate.value,
 * let.value, etc.):
 *
 *   expr ::= <int>                       literal
 *          | { flag:   <expr-or-str> }   state.p[cpl].flag[slot]
 *          | { gold:   <any> }           state.p[cpl].gold
 *          | { torches: <any> }          state.p[cpl].torches
 *          | { food:   <any> }           state.p[cpl].food
 *          | { pl:     <any> }           state.p[cpl].pl (living members)
 *          | { twn:    <any> }           state.twn
 *          | { hour_of_day: <any> }      state.hour_of_day
 *          | { cpl:    <any> }           state.cpl
 *          | { random: [ <expr>, <expr> ] }  ryl_random(lo, hi) inclusive
 *          | { chance: <expr> }          ryl_chance(pct) -> 0/1
 *          | { inventory: <expr> }       inventory[slot]  slot in 1..16
 *          | { member: <expr>,
 *              field: 'lv'|'hp'|'h'|'ma'|'wi'|'ws'|'ch'|'cl'|'ra'|
 *                     'dead'|'ar'|'wp'|'exp'|'gender' }
 *          | { var: '<name>' }           runtime-bound (§17-F step 3+)
 *                                        or atoi'd caller ryl_bind
 *          | { add | sub | mul | div | mod | min | max: [a, b] }
 *          | { eq  | ne  | lt  | lte | gt  | gte: [a, b] }
 *          | { and | or: [a, b, ...] }   short-circuit, returns 0 or 1
 *          | { not: <expr> }             returns 0 or 1
 *          | { obcost:   <expr> }        res_item(id)->cost
 *          | { obdamage: <expr> }        res_item(id)->damage
 *          | { item: '<name>'|<int> }    res_item_id('torch') etc.
 *          | { pic:  '<name>'|<int> }    res_enum_id('pic', ...)
 *          | { enum: ['<tab>','<val>'] } generic res_enum_id lookup
 *          | { flag_value: ['<tag>','<val>'] }
 *                                        res_flag_value_id lookup —
 *                                        symbolic value of a flag's
 *                                        values[] array, paired with
 *                                        `{flag:'<tag>'}` for compares.
 *          | { engine_int: '<name>',     whitelisted int-returning
 *              args: [<expr>...] }       C entry point (argc enforced
 *                                        by registry). Current names:
 *                                        pa_is_alive / pa_is_dead /
 *                                        pa_is_spellcaster / pa_is_class /
 *                                        pa_first_alive / pa_count_class /
 *                                        pa_count_alive_class / inv_find_
 *                                        empty / inv_find_item / inv_find_
 *                                        in_range / inv_count_in_range.
 *                                        Parallel to engine_bind but
 *                                        returns int for use in
 *                                        expressions (eg. if.cond,
 *                                        mutate.value, let.value).
 *
 *   `if.cond` is just an expression — eval_expr is run on the cond
 *   node and the result is truthy-checked.
 *
 * §17-F step 3 — mutate + runtime bindings + input_number:
 *
 *   { let: '<name>', value: <expr> }
 *                                   Evaluate `value`, bind the result
 *                                   into rt_env under `<name>`. Later
 *                                   eval_expr `{var:'<name>'}` and
 *                                   text-placeholder `{<name>}` lookups
 *                                   resolve against the binding.
 *
 *   { mutate: {                     Assign a computed value into a
 *       target: <lvalue>,           mutable game-state slot. `op`
 *       op: 'set'|'add'|'sub'|      selects set / add / sub / mul / div
 *           'mul'|'div',            relative to the slot's current
 *       value: <expr>,              value; optional `clamp` bounds
 *       clamp?: { min: <expr>,      the result after the op (either
 *                 max: <expr> } } } bound is independently optional).
 *
 *   lvalue ::=
 *       { gold }                                state.p[cpl].gold
 *     | { torches }                             state.p[cpl].torches
 *     | { food }                                state.p[cpl].food
 *     | { pl }                                  state.p[cpl].pl (0..255)
 *     | { flag: <expr> }                        state.p[cpl].flag[slot]
 *     | { inventory: <expr> }                   inventory[slot]  1..16
 *     | { member_field: { idx: <expr>,          state.p[cpl].<field>[idx]
 *                         field: '<name>' } }   same field set as eval_expr
 *                                               member reads.
 *
 *   { input_number: {               Read a numeric line from the
 *       prompt_screen?: '<name>',   player. Optional `prompt_screen`
 *       lg: N,                      emits a screen text (parent-relative
 *       var: '<name>',              like `screen`) before the read.
 *       min: <expr>,                `lg` is the getstr buffer cap.
 *       max: <expr> } }             Result is clamped into [min, max]
 *                                   (non-numeric input clamps to min),
 *                                   then bound into rt_env under `var`.
 *
 * Bindings flow through to every screen / journal / menu_branch prompt
 * so {placeholder} expansion works identically to tx_fmt. §16.8's "must
 * not grow into a general scripting language" still applies; steps 4-5
 * (iteration, switch, engine_bind widening) land as this section grows.
 *
 * On unknown op, bad shape, or missing script, ryl_die()s with a
 * diagnostic naming the script id and op index.
 */
void play_script(const char *script_id, const ryl_bind *bindings);

/* §22 Lane A: combat context for in-battle spell `do:` bodies. battle.c
   sets this struct's contents to the local mh[] / deadc[] / noc / fn /
   per-encounter-flag pointers, calls play_spell_body, then lets the
   pointers fall out of scope. The script DSL uses the ctx through a
   file-static pointer in script.c (see script_combat_ctx_set / clear);
   spell ops read combat state via expressions like { monster_hp: <expr> }
   and { battle_flag: 'confused' } and write via the matching mutate
   lvalues. NULL on out-of-combat callers (priest spells via subs.c) —
   any monster_* / battle_flag access in that ctx dies loudly. */
struct script_combat_ctx {
    int noc;          /* live monster count (1..noc are valid indices) */
    int *mh;          /* mh[0] unused; mh[1..noc] = current HP */
    int *deadc;       /* deadc[0] unused; deadc[1..noc] = 0 alive / 1 dead */
    const char *fn;   /* monster display name for caster.name-style binds */
    int *confused;    /* per-encounter flags — NULL if the spell can't set */
    int *held;
    int *antimagic;
    int *ws;          /* monster work-skill (mage.confusion halves it) */
    int *player_held; /* §23.A: monster.hold sets this — battle.c's */
                      /* outer loop emits "PARTY IS HELD!" and skips */
                      /* the next player-input round. Distinct from */
                      /* `held` (player→monster hold above). */
    int *player_confused; /* §23.A: monster.confuse sets this; the */
                      /* upcoming player attack roll halves ws. */
    int *action_retry; /* §23.B: gem_of_resurrection's not-dead branch */
                      /* sets this to abort the body and bump battle.c */
                      /* back to the action menu (legacy `goto combat`). */
                      /* NULL on callers that don't expose a retry path. */
    int h2;           /* per-encounter monster max-HP scalar (reward + */
                      /* monster-spell scaling). Pushed into rt_env as */
                      /* `h2` on play_spell_body entry so combat bodies */
                      /* read it via { var: 'h2' } without a C local. */
};

/* §22 Lane A: evaluate a `formulas.<name>` body as an int expression
   under the script DSL. Used by res_formula_int_n in place of the
   retired eval.c expression evaluator. `bindings` carries the input
   variables ($h1_base / $h2_base / $party_avg / etc. — names match
   the {var:'<name>'} reads inside the formula body). Fatal on
   unknown ops / wrong shape, with `formula_id` in the diagnostic. */
int32_t play_formula_int(const char *formula_id, const struct node *body,
                         const ryl_bind *bindings);

/* §22 Lane A: run a spell `do:` body (a node*) under the script DSL.
   `spell_id` is borrowed for diagnostics ("mage.frost"); `body` is the
   N_ARR returned by res_spell_node. `bindings` carries caster_idx /
   place / etc. as ryl_bind ints. `ctx` is non-NULL for in-combat casts
   (battle.c) and NULL for the out-of-combat priest path (subs.c).
   The static combat-ctx pointer is set on entry, cleared on exit so
   nested calls (none today) would re-establish their own ctx. */
void play_spell_body(const char *spell_id, const struct node *body,
                     const ryl_bind *bindings,
                     const struct script_combat_ctx *ctx);

/* Whitelist introspection for fold-time validation in res.c::fold_scripts.
   Each returns the registry entry's argc (>=0) when <name> is a known
   engine_call / engine_int / engine_bind fn, or -1 if unknown. The DSL
   evaluator already enforces argc at runtime; these are exposed so the
   script-walker can fail at world load on a typo'd fn name rather than
   only when the script fires. */
int script_engine_call_argc(const char *name);
int script_engine_int_argc(const char *name);
int script_engine_bind_argc(const char *name);

#endif
