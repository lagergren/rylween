#ifndef MISSIONS_H__
#define MISSIONS_H__

#include <stdint.h>

/* Mission dispatcher — polled on every turn, fires the appropriate
   mission-specific scene when its trigger conditions are met. */

uint8_t mission_control(void);

#endif
