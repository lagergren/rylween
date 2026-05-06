#ifndef SUBS_H__
#define SUBS_H__

#include <stdint.h>

/* Shared subsystems: time / food tick, map redraw, weapon / armor /
   inventory actions, spell / quaff from the inventory side, the
   per-turn chance of a random encounter, the end-of-game paths, and
   terrain colour/glyph lookups that the renderer uses. */

void time_handler(void);
void food_handler(void);
void random_wilderness(void);
void update_map(void);
void weapon_ready(void);
void armor_ready(void);
uint8_t drop(void);
void camp(void);
void spell(int32_t);
void quaff(void);
void lose_game(void);
void score_list(void);
void hunt(void);
void weapon_off(void);
void armor_off(void);

#endif
