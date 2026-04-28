#ifndef INTERACT_H__
#define INTERACT_H__

#include <stdint.h>

/* Terrain-effect hooks. The guild/mail and attackhandler flows from
   the Amiga BBS original are unfinished and currently #if 0'd out in
   interact.c; re-expose their prototypes here when that port lands. */

void check_terrain(char, char);

/* Journal display: render the current party's journal as a paginated
   list (16 lines per page with `ryl_pause` between pages). */
void show_journal(void);

/* printf-style wrapper that delegates to party_journal_add on the current
   party (party.h). Call sites pass the journal line verbatim — no `%d `
   cpl prefix, no trailing `\n`; the old file-backed impl needed both,
   the in-memory per-party list needs neither. A trailing newline that
   slips in through a format is tolerated and stripped. */
__attribute__((format(printf, 1, 2))) void journal_add(const char *fmt, ...);

#endif
