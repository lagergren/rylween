/* INCLUDE FILE FOR ADVENTURERS OF RYLWEEN */

#ifndef RYL_H__
#define RYL_H__

#include <assert.h>
#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <fcntl.h>
#include <time.h>
#include "compat.h"
#include "state.h"

#define innpic        1
#define tavernpic     2
#define smithpic      3
#define churchpic     4
#define merchantpic   5
#define insidecavepic 10
#define cavepic       11
#define deathpic      12
#define hutpic        13
#define ydargpic      14
#define citypic       15
#define wonbattlepic  16
#define tentpic       17
#define notentpic     18
#define guildpic      19
#define encounterpic  40 /* Addera random sedan +x */

extern char *_version_string;

/* Function prototypes split into per-module headers:
     battle.h dungeon.h driv.h gfx.h interact.h missions.h
     primitives.h speak.h subs.h towne.h ydarg.h
   Each .c source pulls only the headers whose APIs it calls. This file
   stays as the umbrella for stdlib + compat + state so existing call
   sites keep their ambient dependencies. */

#endif
