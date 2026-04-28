#ifndef YDARG_H__
#define YDARG_H__

/* Ydarg end-game boss + the scripted confrontation that precedes it. */

void ydarg(void);

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
