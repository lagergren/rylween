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

/* Party-member slot count — the party has a fixed 1..4 layout baked
   into the save format (struct party.*[5], index 0 unused). There
   is no world.json5 knob because changing this would break saves,
   UI digit prompts ("Who? (1-4)"), and story assumptions. This is
   the named constant to replace the raw `< 5` and `<= 4` literals
   in member-iteration loops. res_party_member_max() is the runtime
   accessor that C and DSL callers go through; it always returns
   this value. */
#define PARTY_MEMBER_MAX 4

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

    /* Fatigue countdown, party-wide. Reset to 14 by sleeping at the inn
       and by a full camp cycle; decremented each hour by timehandler.
       At 0 the party "begins to feel tired"; the negative ladder (-4,
       -8, <-8) drives progressively worse exhaustion messages and
       per-member hp damage. "pigg" was the original Swedish
       (lively/untired); this reads as "hours the party remains
       rested". */
    int32_t hours_rested;

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
    int32_t hour_of_day;
    int32_t cpl;
    int32_t twn;
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

/* Wall-clock helpers. state.hour_of_day is 1..24; advancing past 24
   wraps to 1. is_hour_in_range accepts inclusive [lo, hi]; when
   lo > hi the range wraps around midnight (e.g., lo=22, hi=5 means
   22,23,24,1..5). Used by town shop open-hours checks and by
   driv.c's dawn/midday/twilight/midnight status-line bucket. */
static inline void advance_hour(void) {
    state.hour_of_day++;
    if (state.hour_of_day == 25) {
        state.hour_of_day = 1;
    }
}

static inline int is_hour_in_range(int lo, int hi) {
    int h = state.hour_of_day;
    if (lo <= hi) {
        return h >= lo && h <= hi;
    }
    return h >= lo || h <= hi;
}

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

/* Member-index from an ASCII digit keypress. '1'..'4' -> pm_cur(1..4).
   Any other byte returns an invalid handle with m out of range; callers
   are expected to validate the keypress before calling. Replaces the
   ubiquitous `state.p[state.cpl].<field>[c - 48]` pattern so the member
   index stays inside a typed handle rather than a loose ASCII subtract
   peppered through combat / spell / interact code. */
static inline pm_t pm_from_digit(char c) {
    return pm_cur((int)(c - '0'));
}

/* Inventory slot from an ASCII letter keypress. 'A'..'P' -> 1..16.
   Replaces the bare `c - 64` subtract for inventory-letter decoding.
   Returns 0 (no-such-slot) for any byte outside the range — inv_*
   helpers already treat 0 as the empty-slot sentinel. Callers are
   expected to toupper() before calling; the helper also tolerates
   lowercase to keep legacy sites that skip the toupper. */
static inline int inv_slot_from_letter(char c) {
    if (c >= 'a' && c <= 'z') {
        c = (char)(c - ('a' - 'A'));
    }
    if (c >= 'A' && c <= 'A' + INVENTORY_SLOT_MAX - 1) {
        return (int)(c - 'A' + 1);
    }
    return 0;
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

/* Write-side pm_t helpers. All operate on the member's own fields;
   no cross-member / party-wide side-effects live here (those belong
   at the party_* layer below). The `add_` variants use signed deltas
   so callers can subtract by passing a negative value. Replace bare
   state.p[...]?[i] = ... and ...[i] += ... sites with these so the
   member index stays inside a typed handle rather than a loose
   1..4 integer sloshing through the code. */
static inline void pm_set_hp(pm_t pm, int v) {
    pm.party->hp[pm.m] = v;
}
static inline void pm_add_hp(pm_t pm, int d) {
    pm.party->hp[pm.m] += d;
}
static inline void pm_set_h(pm_t pm, int v) {
    pm.party->h[pm.m] = v;
}
static inline void pm_add_h(pm_t pm, int d) {
    pm.party->h[pm.m] += d;
}
static inline void pm_add_ws(pm_t pm, int d) {
    pm.party->ws[pm.m] += d;
}
static inline void pm_add_wi(pm_t pm, int d) {
    pm.party->wi[pm.m] += d;
}
static inline void pm_add_ch_clamp0(pm_t pm, int d) {
    pm.party->ch[pm.m] += d;
    if (pm.party->ch[pm.m] < 0) {
        pm.party->ch[pm.m] = 0;
    }
}
static inline void pm_add_ch(pm_t pm, int d) {
    pm.party->ch[pm.m] += d;
}
static inline void pm_add_lv(pm_t pm, int d) {
    pm.party->lv[pm.m] += d;
}
static inline void pm_add_exp(pm_t pm, uint32_t d) {
    pm.party->exp[pm.m] += d;
}
static inline void pm_set_dead(pm_t pm, int v) {
    pm.party->dead[pm.m] = v;
}
static inline int pm_wp(pm_t pm) {
    return pm.party->wp[pm.m];
}
static inline void pm_set_wp(pm_t pm, int v) {
    pm.party->wp[pm.m] = v;
}
static inline int pm_ar(pm_t pm) {
    return pm.party->ar[pm.m];
}
static inline void pm_set_ar(pm_t pm, int v) {
    pm.party->ar[pm.m] = v;
}

/* Heal to full: hp = h. No-op if dead. */
static inline void pm_full_heal(pm_t pm) {
    if (!pm_dead(pm)) {
        pm.party->hp[pm.m] = pm.party->h[pm.m];
    }
}

/* Clamp HP up to `floor` (leaves hp alone if already above). Used
   after level-up to ensure the post-gain h bump rescues partial-hp
   members to at least 2/3 h. */
static inline void pm_hp_floor(pm_t pm, int floor) {
    if (pm.party->hp[pm.m] < floor) {
        pm.party->hp[pm.m] = floor;
    }
}

/* Active-party accessor. Every write site that reads
   `state.p[state.cpl]` should prefer party_current(): the name
   carries the invariant (current-player-party) that the raw
   indexed access obscures. */
static inline struct party *party_current(void) {
    return &state.p[state.cpl];
}
static inline int32_t party_gold(void) {
    return party_current()->gold;
}
static inline void party_add_gold(int32_t d) {
    party_current()->gold += d;
}
static inline void party_set_gold(int32_t v) {
    party_current()->gold = v;
}

/* Class name (e.g. "priest") -> PC_* id, or -1 if unknown. Used by the
   resource loader to map JSON class strings to the runtime enum. */
int pm_class_id(const char *name);

#endif
