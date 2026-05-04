#ifndef DRIV_H__
#define DRIV_H__

#include <stdint.h>

/* Driver / main-loop UI. Owns init, the party-create flow, save/load,
   the main game loop (the_game), and the HUD pieces (status_line, show_party,
   inv_list). Journal display + journal_add() live in interact.c. */

void init(void);
void show(int32_t);
void save(void);
uint8_t drop_option(void);
char *pronoun(uint8_t, uint8_t);
void show_party(void);
void inv_list(void);
void status_line(void);
void help(void);

#endif
