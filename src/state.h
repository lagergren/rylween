#ifndef STATE_H__
#define STATE_H__

#include <assert.h>
#include <stdint.h>

/* Game state shapes + party-member accessors.
 *
 * Split out of ryl.h so the prototype soup is reviewable and can be
 * sliced into per-module headers in a later commit. The existing
 * `struct party` SoA layout is preserved (save-format compatibility);
 * pm_t is a cheap by-value view so new code reads
 * `pm_lv(pm)` instead of `state.p[state.cpl].lv[m]`.
 */

/* Inventory slot count — the UI maps letters A..P to slots 1..16, so
   the number is fundamentally tied to the keystroke range and has no
   runtime knob; this is just a named constant to replace the raw 17s
   and 16s scattered through the iteration loops. Slot 0 is unused
   (the C code is 1-indexed). Array size is MAX+1 so inventory[MAX]
   is valid. res_inventory_slot_max() (res.h) is the runtime accessor
   that C and DSL callers go through; it returns this value unless a
   future limits: knob overrides. */
#define INVENTORY_SLOT_MAX 16

struct person {
    int32_t job; /* 1 Merchant */
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

    int32_t nr;                /* Reference number 1-40*/
    int32_t towne;             /* What town? 0=wilderness*/
    int32_t building;          /* What building? 1=inn, 2=tavern. Not valid for wilderness*/
    int32_t x;                 /* What X coord.*/
    int32_t y;                 /* What Y coord. Not valid for townes.*/
                               /* The wilderness X&Y coord automagically become*/
                               /* HH (hut), if they are set for a character*/
    unsigned char visited[26]; /* Start at 0. Visited before???*/
};

struct party {
    char player[30];
    char party_name[30];
    char name[5][30];

    unsigned char flag[40];

    unsigned char fireshield;
    unsigned char bracelets;
    unsigned char mastercrown;
    unsigned char jazzyfoot;
    unsigned char silverkey;

    unsigned char pl; /* How many are they */
    int32_t x;
    int32_t y;
    int32_t gold;
    uint32_t torches;
    int32_t food;
    int32_t time_left;

    int32_t inventory[INVENTORY_SLOT_MAX + 1];

    int32_t ws[5]; /* Wisdom */
    int32_t h[5];  /* Health */
    int32_t hp[5]; /* HitPts */
    int32_t wi[5]; /* Wisdom */
    int32_t ma[5]; /* Magic  */
    int32_t ch[5]; /* Charis */

    int32_t cl[5]; /* Class  */
    int32_t ra[5]; /* Race   */
    int32_t lv[5]; /* Level  */

    int32_t ar[5]; /* Ready Armour */
    int32_t wp[5]; /* Ready Weapon */
    uint32_t exp[5];

    int32_t dead[5];                /* Dead | Alive - dead[0] contains number of times door was opened today */
    unsigned char sex[5];           /* 1= male , 2 = female */
    unsigned char attack_today[26]; /* parties attacked today */
                                    /* turns to zero every time the attacked
                                     party logs on */
    unsigned char waiting_mail;     /* For the guild */
    unsigned char attacked;         /* Attack bulletin to display upon login */

    uint32_t kills;

    int32_t empty1;
    int32_t empty2;
    int32_t empty3;
    int32_t empty4;
    int32_t empty5;
};

struct ryl_state {
    /* Per-session transient state. */
    int32_t tid;
    int32_t cpl;
    int32_t twn;
    int32_t hours_pigg;
    int32_t banished;
    int32_t global_x;
    char last_terrain;

    /* Per-boot configuration. */
    int32_t num_parties;
    int32_t last_played_day;
    int32_t day_at_game_start;
    char global_name[30];

    /* Location tables (set at init, read thereafter). */
    int32_t dx[15];
    int32_t dy[15];

    /* Persistent game data (flushed to realm.dat / npc.dat). */
    char map[42][82];
    struct party p[26];
    struct person npc[41];
};

extern struct ryl_state state;

/* Party member class IDs (party.cl[]). Note this is distinct from the
   `struct person`.job numbering used for NPCs — different enum, same names
   for some entries. Driv.c:904-906 is the authoritative menu. */
#define PC_FIGHTER 1
#define PC_THIEF   2
#define PC_MAGE    3
#define PC_PRIEST  4
#define PC_BARD    5
#define PC_RANGER  6

/* View handle over a single party member. Cheap by-value. Field access
   goes through pm_* accessors so call sites read `pm_lv(pm)` instead of
   `state.p[state.cpl].lv[m]`. The existing `struct party` SoA layout is
   preserved (save-format compatibility); pm_t is purely a readability
   wrapper. New code should prefer pm_*; legacy sites stay as-is until
   a deliberate migration phase. */
typedef struct {
    struct party *party;
    int m; /* 1..4 */
} pm_t;

static inline pm_t pm_of(struct party *p, int m) {
    assert(p && m >= 1 && m <= 4);
    pm_t r = {p, m};
    return r;
}
static inline pm_t pm_cur(int m) {
    assert(state.cpl >= 1 && state.cpl <= 25);
    return pm_of(&state.p[state.cpl], m);
}

static inline int pm_cl(pm_t pm) {
    return pm.party->cl[pm.m];
}
static inline int pm_lv(pm_t pm) {
    return pm.party->lv[pm.m];
}
static inline int pm_ma(pm_t pm) {
    return pm.party->ma[pm.m];
}
static inline int pm_hp(pm_t pm) {
    return pm.party->hp[pm.m];
}
static inline int pm_h(pm_t pm) {
    return pm.party->h[pm.m];
}
static inline int pm_dead(pm_t pm) {
    return pm.party->dead[pm.m];
}
static inline char *pm_name(pm_t pm) {
    return pm.party->name[pm.m];
}

static inline int pm_is_class(pm_t pm, int cls) {
    return pm_cl(pm) == cls;
}
static inline int pm_is_mage(pm_t pm) {
    return pm_is_class(pm, PC_MAGE);
}
static inline int pm_is_priest(pm_t pm) {
    return pm_is_class(pm, PC_PRIEST);
}

/* Class name (e.g. "priest") -> PC_* id, or -1 if unknown. Used by the
   resource loader to map JSON class strings to the runtime enum. */
int pm_class_id(const char *name);

#endif
