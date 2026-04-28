#ifndef YDARG_H__
#define YDARG_H__

#include <stdint.h>

/* Ydarg end-game boss + the scripted confrontation that precedes it. */

void ydarg(void);

/* §23.J ydarg DSL lift — module-level state accessors exposed to
   the script DSL via int_engine_tbl / engine_tbl. The xl/yl/lvl
   trio + per-cell room kind is what the per-floor while(1) driver
   needs to read/write across case dispatch; lifting it to file
   scope lets each room-type lift live in DSL while keeping the
   bytes byte-identical. Re-seeded on every ydarg() entry; no
   save-game persistence. */
int32_t ydarg_get_xl(void);
int32_t ydarg_get_yl(void);
int32_t ydarg_get_lvl(void);
int32_t ydarg_get_map_size(void);
int32_t ydarg_get_room_at_cur(void);
void ydarg_set_xl(int32_t v);
void ydarg_set_yl(int32_t v);
void ydarg_set_lvl(int32_t v);
void ydarg_clear_room_at_cur(void);

/* §23.J ydarg.main backers. ydarg_state_init seeds g_ydarg with
   a fresh map_size + per-floor grid + (xl, yl, lvl) = (1, 1, 1)
   on every entry. ydarg_try_move runs one directional-move attempt
   for the uppercased keycode `c` — non-direction keys are a no-op,
   direction keys gate on the (xl, yl) staying inside [1..map_size]
   and either emit the matching go_<dir> narration + 300ms delay +
   xl/yl update or do nothing. */
void ydarg_state_init(void);
void ydarg_try_move(int32_t c);

/* §23.J cmd-dispatch backer: (V)iew-party command. showparty +
   view_who prompt + key-wait loop + conditional show/show-all.
   Bundled because the digit-or-Q-or-A key gate has no clean DSL
   idiom and the inner key wait is a tight three-condition loop. */
void ydarg_view_command(void);

/* §23.J case-50 backers. ydarg_stairs_try_give resolves a digit
   1..5 against ydarg.stairs.items[] and consumes the matched
   special on success (returns 1) or emits the reject message
   (returns 0). ydarg_teleport_exit emits the (T)eleport vortex /
   outside narration, redraws the wilderness map, and flags the
   ydarg() driver to break out. ydarg_get_exit_requested is the
   per-iteration probe the C while(1) reads after each play_script. */
int32_t ydarg_stairs_try_give(int32_t digit);
void ydarg_teleport_exit(void);
int32_t ydarg_get_exit_requested(void);

/* §23.J case-3 backer: roll a treasure-room item id off the
   ydarg.drop_tables.room subtree. RNG-identity sensitive — the
   gate draw always advances the stream. Returns the rolled item
   id (positive); used by ydarg.case_treasure as an engine_int
   `let` source for the find / inv_add ops. */
int32_t ydarg_treasure_roll(void);

/* §23.J case-2 backer: pitfall undisarmed damage. Picks one of
   ydarg.pitfall.trap_types at random, tx0()s its narration, ryl_
   pause()s, and applies pm_hit_points / damage_div to every alive
   member. Invoked from the ydarg.case_pitfall DSL when no thief
   disarmed the trap. */
void ydarg_pitfall_undisarmed_apply(void);

/* DSL engine_call backers for the ydarg.confrontation script:
     ydarg_run_seq             — walk ydarg.confrontation_sequence and
                                 fire encounter(monster, mandatory=1)
                                 for each entry.
     ydarg_record_completion   — pa_set_special(PS_SILVERKEY, 2) +
                                 save(). Bundled because the silverkey
                                 bump must persist before the engine
                                 ever shows the title screen again.
     ydarg_finalize            — heroes.txt append + cleanexit. Does
                                 not return; always the last op. */
void ydarg_run_seq(void);
void ydarg_record_completion(void);
void ydarg_finalize(void);

#endif
