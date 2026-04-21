/* INCLUDE FILE FOR ADVENTURERS OF RYLWEEN */

#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <fcntl.h>
#include <time.h>
#include "compat.h"

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

extern int tid, cpl, antal_spelare, global_x, senaste_dagen, twn;
extern int hours_pigg;
extern int banished;
extern int dx[15], dy[15];
extern char map[42][82];
extern int reg;
extern int day_at_game_start;
extern char global_name[30];
extern char last_terrain;

extern char *name[];
extern char *jobname[];

extern char *_version_string;

struct person {
    int job; /* 1 Merchant */
             /* 2 Ranger   */
             /* 3 Fighter  */
             /* 4 Wizard   */
             /* 5 Priest   */
             /* 6 Thief    */
             /* 7 Peasant  */
             /* 8 Bard     */
             /* 9 Drifter  */
             /* 10Half-Orc */
             /* 11Half-Elf */
             /* 12Smith    */

    /* 13Bargirl (special)*/
    /* 14Innkeeper (special)*/
    /* 15Tavernkeeper (special)*/
    /* 16Hermit*/

    int nr;                    /* Reference number 1-40*/
    int towne;                 /* What town? 0=wilderness*/
    int bl;                    /* What building? 1=inn, 2=tavern. Not valid for wilderness*/
    int x;                     /* What X coord.*/
    int y;                     /* What Y coord. Not valid for townes.*/
                               /* The wilderness X&Y coord automagically become*/
                               /* HH (hut), if they are set for a character*/
    unsigned char visited[26]; /* Start at 0. Visited before???*/
};

extern struct person npc[41];

struct party {
    char player[30];
    char partyname[30];
    char name[5][30];

    unsigned char flag[40];

    unsigned char fireshield;
    unsigned char bracelets;
    unsigned char mastercrown;
    unsigned char jazzyfoot;
    unsigned char silverkey;

    unsigned char pl; /* How many are they */
    int x;
    int y;
    unsigned long gold;
    unsigned long torches;
    long food;
    int tid_kvar;

    int inventory[17];

    int ws[5]; /* Wisdom */
    int h[5];  /* Health */
    int hp[5]; /* HitPts */
    int wi[5]; /* Wisdom */
    int ma[5]; /* Magic  */
    int ch[5]; /* Charis */

    int cl[5]; /* Class  */
    int ra[5]; /* Race   */
    int lv[5]; /* Level  */

    int ar[5]; /* Ready Armour */
    int wp[5]; /* Ready Weapon */
    unsigned long exp[5];

    int dead[5];                    /* Dead | Alive - dead[0] contains number of times door was opened today */
    unsigned char sex[5];           /* 1= male , 2 = female */
    unsigned char attack_today[26]; /* parties attacked today */
                                    /* turns to zero every time the attacked
                                     party logs on */
    unsigned char waiting_mail;     /* For the guild */
    unsigned char attacked;         /* Attack bulletin to display upon login */

    unsigned long kills;

    int empty1;
    int empty2;
    int empty3;
    int empty4;
    int empty5;
};

extern struct party p[26];

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

/* OTHER PROTOS */

void init(void);
void createparty(void);
void show(int);
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
void spell(int);
void quaff(void);
char *sextext(uint8_t, uint8_t);
void smith(void);
void tavern(void);
void inn(void);
void merchant(void);
void rumour(void);
void place_npcs(void);
void speak(int, int, int, int);
void reply_chitchat(int, int);
char *object(int);
long obcost(int);
int obdamage(int);
void encounter(int, int);
void losegame(void);
void expcheck(void);
void showparty(void);
void church(void);
void hut(void);
void dungeon(int, int);
void flaggkoll(int);
int frg(char);
void position_locations(void);
char *kar(char);
void ydarg(void);
void confrontation(void);
void invlist(void);
void statusline(void);
char cf(int);
void scorelist(void);
void hunt(void);
void journal(char *);
void read_journal(void);
void inactive(void);
void reganim(void);
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
void write_internal_mail(void);
void read_bulletin(void);
void write_bulletin(void);
void quest_status(void);
void trade(void);
uint8_t select_party(void);
void guild_title(char *);
void editor(char *, uint8_t);
void check_terrain(char, char);

/* Pointer protos */
