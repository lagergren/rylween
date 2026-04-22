#ifndef DRIV_H__
#define DRIV_H__

#include <stdint.h>

/* Driver / main-loop UI. Owns init, the party-create flow, save/load,
   the main game loop (spelet), and the HUD pieces (statusline, showparty,
   invlist, journal). */

void init(void);
void show(int32_t);
void save(void);
uint8_t drop_option(void);
char *pronoun(uint8_t, uint8_t);
void showparty(void);
void invlist(void);
void statusline(void);
void journal(char *);
void help(void);

#endif
