#ifndef SCRIPT_H__
#define SCRIPT_H__

#include "primitives.h"

/* §16.4 + §17-E + §17-F dialogue / mission script interpreter.
 *
 * A script is an array node in the `scripts.*` subtree of data/world.json5
 * (authored in data/world/scripts/). Each element is a one-op object:
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
 *   { wait_input: true }          - ryl_pause() (prompts "Smash RETURN
 *                                    key", blocks until enter). Distinct
 *                                    from `pause`, which is ryl_delay.
 *
 *   { pause: N }                  - ryl_delay(N) ms between screens.
 *
 *   { chance: {                   - ryl_chance(pct) gated branch.
 *       pct: N,
 *       then: [...],
 *       else: [...] } }
 *
 *   { if: {                       - flag / gold condition branch.
 *       cond: { flag|gold: ...,
 *               eq|ne|gte|lt: N },
 *       then: [...],
 *       else: [...] } }
 *
 *   { menu_branch: {              - one ryl_menu-style keypress. Options
 *       prompt: '<name>',           are one-char strings; then is a
 *       options: [                  nested op array. Unmatched keys fall
 *         { key: 'E',               to optional `default: [...]` or
 *           then: [...] } ],        return silently.
 *       default: [...] } }
 *
 *   { engine_call: '<name>',      - call a registered C entry point with
 *     args: [<int>...] }            literal int args. Registry lives in
 *                                   script.c (engine_tbl). Currently:
 *                                   dungeon(2) encounter(2) update_map(0)
 *                                   clr_map_window(0) blitgfx(1)
 *                                   statusline(0) clw(0).
 *
 * §17-F control-flow ops (§17-F step 1 — loop primitives):
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
 * Bindings flow through to every screen / journal / menu_branch prompt
 * so {placeholder} expansion works identically to tx_fmt. The
 * interpreter still has no arithmetic, loops, or runtime binding
 * assignment — those flows keep imperative logic in C. §16.8's "must
 * not grow into a general scripting language" still applies.
 *
 * On unknown op, bad shape, or missing script, ryl_die()s with a
 * diagnostic naming the script id and op index.
 */
void play_script(const char *script_id, const ryl_bind *bindings);

#endif
