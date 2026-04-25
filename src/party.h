#ifndef PARTY_H__
#define PARTY_H__

#include "state.h"

/* Party-member helpers. state.p[state.cpl].* arrays are 1-indexed,
   size PARTY_MEMBER_MAX+1 (state.h); every helper here iterates
   slot 1..res_party_member_max() — slot 0 is reserved. Single
   point of truth for the "iterate the party" pattern scattered
   across battle / dungeon / driv / subs / towne / ydarg.

   All helpers operate on the current party (state.cpl). The 1..4
   cap is compile-time pinned by the save format; there is no
   runtime knob.

   Class IDs match state.h's PC_* enum (PC_MAGE=3, PC_PRIEST=4). */

/* Is member `m` alive? 1 on 1..4 and !dead[m], else 0. */
int pa_is_alive(int m);

/* Is member `m` dead? Inverse of pa_is_alive (also 0 on out-of-range
   `m`, so callers can't accidentally treat an invalid slot as dead). */
int pa_is_dead(int m);

/* Is member `m` a spellcaster (PC_MAGE or PC_PRIEST)? 0 on
   out-of-range m. */
int pa_is_spellcaster(int m);

/* Is member `m` of class `cl`? 0 on out-of-range m. */
int pa_is_class(int m, int cl);

/* First slot m in 1..4 with !dead[m], or 0 if every member is
   dead (a game-over state state.p[cpl].pl should mirror). */
int pa_first_alive(void);

/* Count members with cl[m] == cl. Used by flows that scale with a
   class count (e.g. tavern scruffy chance uses bard charisma). */
int pa_count_class(int cl);

/* Count members with cl[m] == cl and !dead[m]. */
int pa_count_alive_class(int cl);

/* Count of !dead[m] members across the whole party. Scaling helper
   for any flow whose damage / healing / reward should track surviving
   headcount rather than raw party size. */
int pa_alive_count(void);

/* Current-party world-map coordinates. Centralises the
   state.p[state.cpl].{x,y} reads scattered through driv.c / subs.c
   so future map-frame translation lands in one place. */
int32_t pa_x(void);
int32_t pa_y(void);

/* Current-party gold (state.p[state.cpl].gold). Pa-prefixed sibling
   to party_gold() so call sites that already use the pa_* / pm_*
   abstraction layer don't have to reach back into the party_*
   namespace. The two are aliases today; new code should prefer
   pa_gold so a future S2 sweep can retire party_gold cleanly. */
int32_t pa_gold(void);

/* First member's level (state.p[state.cpl].lv[1]). Used as the
   party's "headline" level for content-scaling decisions (encounter
   tier, dungeon depth, special-encounter chance). The 70+ raw
   `lv[1]` reads in dungeon.c / battle.c / driv.c are queued for an
   S2 sweep; this helper is the single point of truth they migrate
   to. */
int pa_first_level(void);

/* Party mission / quest flags (data/world.json5 `flags:` subtree).
   All three helpers operate on state.p[state.cpl].flag[slot] where
   slot is resolved from `tag` via res_flag_slot(). An unknown tag —
   i.e. one not declared in world.json5 — is a programmer error and
   aborts with a descriptive fprintf.

   `state` names are the entries in the tag's `values:` array in
   world.json5. They read like prose at the call site: `pa_flag_is(
   "peasant_wife", "rescued")` rather than `flag[19] == 2`. */

/* Raw value in the slot (0..n_values-1). Prefer pa_flag_is for
   equality checks — keeps call sites in string space. */
int pa_flag_get(const char *tag);

/* 1 if the slot's current value matches `state`, else 0. */
int pa_flag_is(const char *tag, const char *state);

/* Set the slot to the value indexed by `state`. */
void pa_flag_set(const char *tag, const char *state);

/* Append `line` to p->journal, skipping if a case-insensitive duplicate is
   already present (behaviour parity with the file-backed `stricmp` dedup
   it replaces). The caller's buffer is copied; ownership moves into the
   party. `line` must not embed a trailing newline — the journal viewer
   renders with its own row formatting. */
void party_journal_add(struct party *p, const char *line);

/* Free every heap entry in p->journal and the backing pointer array.
   Leaves journal_n = journal_cap = 0 and journal = NULL. Call on
   party-delete and at shutdown. */
void free_journal(struct party *p);

/* Allocate the per-member heap buffers on struct party (name, ws,
   h, hp, wi, ma, ch, cl, ra, lv, ar, wp, exp, dead, gender) to
   size res_party_member_max() + 1, zero-filled. Idempotent: any
   previous buffers are freed first. Does NOT touch the journal —
   its lifecycle is separate (load clears + re-appends per save).

   Call once before the first per-member write: at createparty after
   num_parties++, at read_current_world before rcw_i32_arr(..., p->ws,
   ...), after position_locations / res_realize_npcs in the cold-
   start path if the party slot will be used. */
void party_realize(struct party *p);

/* Free every per-member buffer allocated by party_realize. Does NOT
   free the journal — use free_journal for that. Call from terminate
   before the memmove that shifts state.p[] entries down (otherwise
   the deleted slot's buffers leak). */
void party_release_members(struct party *p);

/* NULL out every heap-owned pointer on struct party (members +
   journal) without freeing. Use after a memmove that shifts struct
   party entries down in state.p[] — the tail slot's pointers still
   alias the entries above it, so clearing them prevents save-time
   double-emit and lets a future realize into that slot start clean. */
void party_clear_heap_pointers(struct party *p);

#endif
