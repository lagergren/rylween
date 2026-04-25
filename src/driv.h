#ifndef DRIV_H__
#define DRIV_H__

#include <stdint.h>

/* Driver / main-loop UI. Owns init, the party-create flow, save/load,
   the main game loop (the_game), and the HUD pieces (statusline, showparty,
   invlist, journal). */

void init(void);
void show(int32_t);
void save(void);
uint8_t drop_option(void);
char *pronoun(uint8_t, uint8_t);
void showparty(void);
void invlist(void);
void statusline(void);

/* printf-style wrapper that delegates to party_journal_add on the current
   party (party.h). Call sites pass the journal line verbatim — no `%d `
   cpl prefix, no trailing `\n`; the old file-backed impl needed both,
   the in-memory per-party list needs neither. A trailing newline that
   slips in through a format is tolerated and stripped. */
__attribute__((format(printf, 1, 2))) void journalf(const char *fmt, ...);

void help(void);

#endif
