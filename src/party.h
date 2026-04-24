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

#endif
