#ifndef SCRIPT_H__
#define SCRIPT_H__

#include "primitives.h"

/* §16.4 dialogue script interpreter.
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
 *
 * Bindings flow through to every screen and journal string so
 * {placeholder} expansion works identically to tx_fmt. The interpreter
 * deliberately has no arithmetic / loops / conditionals: branching on
 * flag values is the caller's job (the outer dispatcher picks the right
 * script by flag).
 *
 * On unknown op, bad shape, or missing script, ryl_die()s with a
 * diagnostic naming the script id and op index.
 */
void play_script(const char *script_id, const ryl_bind *bindings);

#endif
