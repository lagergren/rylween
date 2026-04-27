#ifndef BATTLE_H__
#define BATTLE_H__

#include <stdint.h>

/* Combat entry (encounter) and the per-win XP distribution (expcheck). */

void encounter(int32_t, int32_t);

/* §25.A.2: iterate monster slots 1..(noc). The body sees `idx` 1-based —
   matches `for_each_party_member` in party.h. Unlike the party-member
   iterator (whose bound is fixed by the save format), `noc` is the
   encounter-local live monster count, so the bound is parameterized.
   battle.c::encounter passes its local `noc`; script.c passes
   `g_combat_ctx->noc`. §25.B will edit this macro (and re-bless
   fixtures) when the codebase flips to 0-indexed. */
#define for_each_monster(idx, noc) \
    for (int idx = 1; idx <= (noc); idx++)

#endif
