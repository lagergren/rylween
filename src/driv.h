#ifndef DRIV_H__
#define DRIV_H__

#include <stdint.h>

/* Driver / main-loop UI. Owns init, the party-create flow, save/load,
   the main game loop (spelet), and the HUD pieces (statusline, showparty,
   invlist, journal). */

void init(void);
void createparty(void);
void show(int32_t);
void load(void);
void save(void);
void spelet(void);
uint8_t drop_option(void);
char *pronoun(uint8_t, uint8_t);
void showparty(void);
void position_locations(void);
void invlist(void);
void statusline(void);
void journal(char *);
void read_journal(void);
void clrjr(uint8_t);
void help(void);

#endif
