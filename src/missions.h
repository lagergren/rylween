#ifndef MISSIONS_H__
#define MISSIONS_H__

#include <stdint.h>

/* Mission dispatcher — wilderness step-poll path + E-key path, both
   feeding the same mission_triggers[] table. mission_control fires
   step-poll triggers (MW_STEP); mission_try_at_enter fires the
   towne.c::enter (E-key) triggers (MW_ENTER_KEY). */

uint8_t mission_control(void);
uint8_t mission_try_at_enter(void);

#endif
