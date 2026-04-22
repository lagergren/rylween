#ifndef INTERACT_H__
#define INTERACT_H__

#include <stdint.h>

/* Multi-party (guild/mail/trade) interactions + the editor overlay +
   terrain-effect hooks. Most entry points here are stubs on single-
   player boot; the guild/mail flows ran against a shared datastore on
   the Amiga BBS originals. */

void guild(void);
void read_internal_mail(void);
void trade(void);
void attackhandler(void);
uint8_t select_party(void);
void guild_title(char *);
void editor(char *, uint8_t);
void check_terrain(char, char);

#endif
