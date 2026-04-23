#ifndef INTERACT_H__
#define INTERACT_H__

#include <stdint.h>

/* Terrain-effect hooks. The guild/mail and attackhandler flows from
   the Amiga BBS original are unfinished and currently #if 0'd out in
   interact.c; re-expose their prototypes here when that port lands. */

void check_terrain(char, char);

#endif
