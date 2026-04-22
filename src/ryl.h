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

#define innpic 1
#define tavernpic 2
#define smithpic 3
#define churchpic 4
#define merchantpic 5
#define insidecavepic 10
#define cavepic 11
#define deathpic 12
#define hutpic 13
#define ydargpic 14
#define citypic 15
#define wonbattlepic 16
#define tentpic 17
#define notentpic 18
#define guildpic 19
#define encounterpic 40 /* Addera random sedan +x */

extern char *_version_string;

/* TERMINAL PRIMITIVES — all live in primitives.c as io->... wrappers */

int main(int, char **);
char *getstr(uint8_t);
uint32_t tx(const char *, ...);
void cleanexit(void);
uint8_t carrier(void);
void cl(void);
void c(uint8_t, uint8_t);
void clw(void);
void ryl_pause(void);
uint8_t hotkey(void);
void render_at(const char *, uint8_t, uint8_t);

/* OTHER PROTOS */

void init(void);
void createparty(void);
void show(int32_t);
void load(void);
void save(void);
char key(void);
void spelet(void);
void timehandler(void);
void foodhandler(void);
void slumpvis(void);
void update_map(void);
void weaponr(void);
void armourr(void);
void enter(void);
uint8_t drop(void);
uint8_t drop_option(void);
void camp(void);
void spell(int32_t);
void quaff(void);
char *pronoun(uint8_t, uint8_t);
void smith(void);
void tavern(void);
void inn(void);
void merchant(void);
void rumour(void);
void place_npcs(void);
void speak(int32_t, int32_t);
void reply_chitchat(int32_t, int32_t);
char *object(int32_t);
int32_t obcost(int32_t);
int32_t obdamage(int32_t);
void encounter(int32_t, int32_t);
void losegame(void);
void expcheck(void);
void showparty(void);
void church(void);
void hut(void);
void dungeon(int32_t, int32_t);
void flaggkoll(int32_t);
int32_t color(char);
void position_locations(void);
char *glyph(char);
void ydarg(void);
void confrontation(void);
void invlist(void);
void statusline(void);
void scorelist(void);
void hunt(void);
void journal(char *);
void read_journal(void);
void blitgfx(uint8_t);
void clr_map_window(void);
uint8_t mission_control(void);
void ryl_log(char *);
void weaponoff(void);
void armouroff(void);
void clrjr(uint8_t);
void help(void);
void disptext(char *);
void guild(void);
void read_internal_mail(void);
void trade(void);
void attackhandler(void);
uint8_t select_party(void);
void guild_title(char *);
void editor(char *, uint8_t);
void check_terrain(char, char);

/* Pointer protos */

#endif
