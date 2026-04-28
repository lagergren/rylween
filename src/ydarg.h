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
