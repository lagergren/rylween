#ifndef INTERACT_H__
#define INTERACT_H__

#include <stdint.h>

/* Multi-party (guild/mail/trade) interactions + the editor overlay +
   terrain-effect hooks. Most entry points here are stubs on single-
   player boot; the guild/mail flows ran against a shared datastore on
   the Amiga BBS originals. */

void guild(void);
void attackhandler(void);
void check_terrain(char, char);

#endif
