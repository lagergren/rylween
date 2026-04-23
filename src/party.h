#ifndef PARTY_H__
#define PARTY_H__

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

#endif
