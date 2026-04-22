#ifndef BATTLE_H__
#define BATTLE_H__

#include <stdint.h>

/* Combat entry (encounter) and the per-win XP distribution (expcheck). */

void encounter(int32_t, int32_t);
void expcheck(void);

#endif
