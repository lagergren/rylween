#ifndef SUBS_H__
#define SUBS_H__

#include <stdint.h>

/* Shared subsystems: time / food tick, map redraw, weapon / armour /
   inventory actions, spell / quaff from the inventory side, the
   per-turn chance of a random encounter, the end-of-game paths, and
   terrain colour/glyph lookups that the renderer uses. */

void timehandler(void);
void foodhandler(void);
void slumpvis(void);
void update_map(void);
void weaponr(void);
void armourr(void);
uint8_t drop(void);
void camp(void);
void spell(int32_t);
void quaff(void);
void losegame(void);
void scorelist(void);
void hunt(void);
void weaponoff(void);
void armouroff(void);

#endif
