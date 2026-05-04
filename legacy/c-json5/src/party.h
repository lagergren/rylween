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

/* §24bis phase 1 — slug-form accessors for class/race on the current
   party. pa_class_sym / pa_race_sym return the lowercase enum key
   ("fighter" / "thief" / "human" / ...) or "" on out-of-range m or
   the no-class / no-race sentinel slot 0. The is_sym predicates compare
   the slug against `sym` (string equality, NULL/empty-safe). */
const char *pa_class_sym(int m);
const char *pa_race_sym(int m);
int pa_class_is_sym(int m, const char *sym);
int pa_race_is_sym(int m, const char *sym);

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
void pa_set_x(int32_t v);
void pa_set_y(int32_t v);
void pa_add_x(int32_t delta);
void pa_add_y(int32_t delta);

/* Current-party gold (state.p[state.cpl].gold). Pa-prefixed sibling
   to party_gold() so call sites that already use the pa_* / pm_*
   abstraction layer don't have to reach back into the party_*
   namespace. The two are aliases today; new code should prefer
   pa_gold so a future S2 sweep can retire party_gold cleanly. */
int32_t pa_gold(void);

/* First member's level (state.p[state.cpl].members[0].level). Used as the
   party's "headline" level for content-scaling decisions (encounter
   tier, dungeon depth, special-encounter chance). The 70+ raw
   `lv[0]` reads in dungeon.c / battle.c / driv.c are queued for an
   S2 sweep; this helper is the single point of truth they migrate
   to. */
int pa_first_level(void);

/* Mean party-member level (sum of pm_level across all slots, divided
   by res_party_member_max()). Used by content-scaling consumers
   that want to track every member's level rather than just the
   first member's — encounter HP scaling reads it via the
   "party_avg" formula binding. */
int32_t pa_level_avg(void);

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

/* Raw-slot party-state accessors. The tag-keyed pa_flag_get / pa_flag_is /
   pa_flag_set above are for hand-written C; these slot-indexed forms are
   for the DSL eval path (script.c eval_expr / do_mutate / eval_cond),
   which has already resolved a slot via res_flag_slot. Both groups
   bound-check and exit(1) on out-of-range. */

/* Number of party-flag slots (sizeof state.p[cpl].flag). */
int pa_flag_count(void);

/* state.p[cpl].flag[slot] read / write. */
int pa_flag(int slot);
void pa_set_flag(int slot, int v);

/* Top-level party scalars. The set_* siblings assume the caller has
   already enforced the field's domain (DSL mutate paths do this with
   die_at so script context is preserved in the error); they only do
   the type narrowing. */
int32_t pa_torches(void);
void pa_set_torches(int32_t v);
void pa_add_torches(int32_t delta);
int32_t pa_food(void);
void pa_set_food(int32_t v);
void pa_add_food(int32_t delta);
int pa_pl(void);
void pa_set_pl(int v);
void pa_add_pl(int delta);
int32_t pa_hours_rested(void);
void pa_set_hours_rested(int32_t v);
void pa_add_hours_rested(int32_t delta);

/* Per-tile move budget (state.p[cpl].time_left). Decremented on each
   driv.c step / hunt / cave-entry; refilled at midnight by the realm
   advance flow. The hunt/spell flow only checks magnitude, not signed
   wrap, so callers stay unsigned in spirit. */
int32_t pa_time_left(void);
void pa_set_time_left(int32_t v);
void pa_add_time_left(int32_t delta);

/* Gold writes (pa_gold above is the read sibling). */
void pa_set_gold(int32_t v);
void pa_add_gold(int32_t delta);

/* Bump party.kills by `delta`. The save/load paths still go through
   the raw `p->kills` since they iterate over arbitrary parties, not
   the current one — pa_* helpers are current-party-specific. */
void pa_add_kills(uint32_t delta);

/* state.p[cpl].inventory[slot] read / write. slot is 1..INVENTORY_SLOT_MAX. */
int32_t pa_inventory(int slot);
void pa_set_inventory(int slot, int32_t item_id);

/* Per-member display name. m is 1..res_party_member_max(); returns NULL on
   out-of-range so the caller can fall back to a placeholder (sb_pa_name
   renders "-" for invalid slots). */
const char *pa_name(int m);

/* §25.A.1: iterate party-member slots 0..res_party_member_max()-1.
   Body sees `slot` 0-based. Single point of truth for the dozens of
   member-slot loops across battle / driv / dungeon / snapshot / subs
   / towne / ydarg / replay_knobs / party. */
#define for_each_party_member(slot) \
    for (int slot = 0; slot < res_party_member_max(); slot++)

/* Party display name (state.p[cpl].party_name). Owned by struct party;
   the returned pointer is valid until the party slot is freed. */
const char *pa_party_name(void);

/* Journal accessors. `pa_journal_n` is the line count, `pa_journal_line`
   reads the i-th (0-based) line; out-of-range returns NULL. The buffer
   is owned by struct party — pointers are valid until the party slot
   is freed. */
size_t pa_journal_n(void);
const char *pa_journal_line(size_t i);

/* Special-item flag read / write. `which` is a PS_* value from
   inventory.h (PS_FIRESHIELD / PS_BRACELETS / PS_MASTERCROWN /
   PS_JAZZYFOOT / PS_SILVERKEY). silverkey is a 3-state field
   (0/1/2 for none / owned / returned-to-ydarg); the other four are
   0/1. PS_NONE is fatal. The DSL reward op short-circuits via
   inventory.c::party_award_special which sets the flag to 1; this
   pair gives C call sites the read access plus the silverkey
   value-2 write that ydarg uses. */
int pa_special(int which);
void pa_set_special(int which, int v);

/* By-index party handle. Returns &state.p[idx]; asserts the index is
   in [0, PARTY_SLOT_HARD_MAX). The current-party-only `pa_*` family
   above is the natural shape for the 95% of call sites that read /
   mutate the active party; this helper is the escape hatch for the
   handful of cross-party loops (score_list, driv.c::struggle-over
   sweep, driv.c::owner-match) that have to walk every party slot.
   Out-of-range is a programmer error and exits(1) with a descriptive
   log line — callers don't have to NULL-check. */
struct party *party_at(int idx);

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

   Call once before the first per-member write: at create_party after
   num_parties++, at read_current_world before rcw_i32_arr(..., p->ws,
   ...), after position_locations / res_realize_npcs in the cold-
   start path if the party slot will be used. */
void party_realize(struct party *p);

/* Free every per-party heap buffer allocated by party_realize
   (members[] + attack_today[]). Does NOT free the journal — use
   free_journal for that. Call from terminate before the memmove
   that shifts state.p[] entries down (otherwise the deleted slot's
   buffers leak). */
void party_release_members(struct party *p);

/* NULL out every heap-owned pointer on struct party (members +
   attack_today + journal) without freeing. Use after a memmove that
   shifts struct party entries down in state.p[] — the tail slot's
   pointers still alias the entries above it, so clearing them
   prevents save-time double-emit and lets a future realize into
   that slot start clean. */
void party_clear_heap_pointers(struct party *p);

#endif
