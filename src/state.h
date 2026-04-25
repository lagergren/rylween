#ifndef STATE_H__
#define STATE_H__

#include <assert.h>
#include <stddef.h>
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

/* Party-member slot default. The per-member arrays on struct party
   are heap-allocated off res_party_member_max() at party-realize /
   load time (§19.3), so the bound is a data-driven knob:
   world.json5 `limits.party_member_max` overrides, default 4.
   Story / combat / damage tables bake 4-member assumptions in
   subtle ways, so treat N != 4 as experimental. This #define is
   just the compile-time fallback for the res_party_member_max()
   accessor when the data knob is absent. */
#define PARTY_MEMBER_MAX 4

/* Forward decl so pm_of's bounds assert and struct-party accessors
   can reach the data knob without pulling res.h (which includes
   state.h — would be circular). Implemented in res.c. */
int res_party_member_max(void);

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
    unsigned char gender;      /* 1=male, 2=female. Drives res_pronoun
                                  when the NPC is referred to in prose
                                  and splits gender-flavoured chitchat
                                  in speak.c. Authored in world.json5
                                  npcs records; defaults to 1 (male). */
};

struct party {
    char player[30];
    char party_name[30];

    /* Per-member arrays below (name, ws, h, ..., gender). All are
       heap-allocated to size res_party_member_max() + 1 at party-
       realize / load time; slot 0 is still reserved (legacy 1-indexed
       iteration), members live at slots 1..res_party_member_max().
       party_realize / party_release in party.c own the lifecycle.

       `name` stays as an array-of-arrays view so `p->name[m]` is
       still a `char[30]` (every getstr/strncpy/sizeof call site
       keeps working); other fields are plain int32_t * / uint32_t *
       / unsigned char *. */
    char (*name)[30];

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

    int32_t *ws; /* Weapon Skill */
    int32_t *h;  /* Health       */
    int32_t *hp; /* HitPts       */
    int32_t *wi; /* Wisdom       */
    int32_t *ma; /* Magic        */
    int32_t *ch; /* Charisma     */

    int32_t *cl; /* Class  */
    int32_t *ra; /* Race   */
    int32_t *lv; /* Level  */

    int32_t *ar; /* Ready Armour */
    int32_t *wp; /* Ready Weapon */
    uint32_t *exp;

    int32_t *dead;                  /* Dead | Alive — dead[0] contains number of times door was opened today */
    unsigned char *gender;          /* 1=male, 2=female */
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

    /* Per-party journal of discovered locations / quest notes. Heap-owned;
       persisted through current-world.json5's `journal: [...]` array. The
       file-backed `data/journal` sidecar is gone as of §19.2. */
    char **journal;
    size_t journal_n;
    size_t journal_cap;
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
   `state.p[state.cpl].lv[m]`. The `struct party` SoA layout is per-
   member heap-allocated off res_party_member_max() (§19.3); pm_t is
   purely a readability wrapper. New code should prefer pm_*; legacy
   sites stay as-is until a deliberate migration phase. */
typedef struct {
    struct party *party;
    int m; /* 1..N, where N = res_party_member_max() */
} pm_t;

static inline pm_t pm_of(struct party *p, int m) {
    assert(p && m >= 1 && m <= res_party_member_max());
    pm_t r = {p, m};
    return r;
}
static inline pm_t pm_cur(int m) {
    assert(state.cpl >= 1 && state.cpl <= 25);
    return pm_of(&state.p[state.cpl], m);
}

/* Member-index from an ASCII digit keypress. '1'..'N' -> pm_cur(1..N).
   Any other byte returns an invalid handle with m out of range; callers
   are expected to validate the keypress before calling (pm_digit_in_range
   below). Replaces the ubiquitous `state.p[state.cpl].<field>[c - 48]`
   pattern so the member index stays inside a typed handle rather than a
   loose ASCII subtract peppered through combat / spell / interact code. */
static inline pm_t pm_from_digit(char c) {
    return pm_cur((int)(c - '0'));
}

/* Is an ASCII digit keypress a valid party-member selector?
   Predicate variant of pm_from_digit: returns 1 iff c is '1'..'N'
   where N = res_party_member_max(). Replaces the hardcoded
   `c >= '1' && c <= '4'` guard that gates nearly every "Who? (1-4)"
   prompt; the bound now tracks the data knob instead of assuming 4.
   Caller is expected to toupper() or equivalent if the prompt is
   case-insensitive — this helper only looks at the ASCII byte. */
static inline int pm_digit_in_range(char c) {
    if (c < '1') {
        return 0;
    }
    return (int)(c - '0') <= res_party_member_max();
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
static inline int pm_is_fighter(pm_t pm) {
    return pm_is_class(pm, PC_FIGHTER);
}
static inline int pm_is_thief(pm_t pm) {
    return pm_is_class(pm, PC_THIEF);
}
static inline int pm_is_mage(pm_t pm) {
    return pm_is_class(pm, PC_MAGE);
}
static inline int pm_is_priest(pm_t pm) {
    return pm_is_class(pm, PC_PRIEST);
}
static inline int pm_is_bard(pm_t pm) {
    return pm_is_class(pm, PC_BARD);
}
static inline int pm_is_ranger(pm_t pm) {
    return pm_is_class(pm, PC_RANGER);
}
/* Mage + priest — the two classes with a spell list / mana pool. Used
   by the mana-restoration potion and the level-up mana-regen branch,
   both of which treat the two classes identically. */
static inline int pm_is_spellcaster(pm_t pm) {
    return pm_is_mage(pm) || pm_is_priest(pm);
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
/* Equipped-slot predicates. wp/ar of 0 means no weapon / no armour
   equipped — the inventory item-id 0 sentinel doubles as "empty".
   Replaces the bare `pm_wp(pm) != 0` peppered through battle / spell
   gating. */
static inline int pm_has_weapon(pm_t pm) {
    return pm_wp(pm) != 0;
}
static inline int pm_has_armour(pm_t pm) {
    return pm_ar(pm) != 0;
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

/* ---- NPC handle ---------------------------------------------------
   View over a single state.npc[] record, mirroring pm_t for party
   members. Bundles the `struct person *` with the slot id so
   accessors that need the slot (npc_name -> res_enum("name", slot))
   don't have to reach back into state.npc arithmetic.

   Two constructors:

     npc_at_slot(slot)   — slot is already known (iteration, tests)
     npc_for_role(role)  — look up via npc_roles: in world.json5,
                           fatal if the role isn't bound (caller
                           bug or data rename out of sync)

   Accessors are pointer-chases through n.p for the struct-person
   fields; npc_name goes through res_enum since the display string
   lives in the name enum, not the struct. */

/* Forward declarations (implemented in res.c). Keeps state.h from
   pulling in res.h / ryl.h — the NPC handle only needs res_npc_role
   to resolve role->slot and res_enum to format the display name. */
int32_t res_npc_role(const char *role);
const char *res_enum(const char *id, int index);

typedef struct {
    struct person *p;
    int slot;
} npc_t;

static inline npc_t npc_at_slot(int slot) {
    assert(slot >= 0);
    npc_t r = {&state.npc[slot], slot};
    return r;
}
static inline npc_t npc_for_role(const char *role) {
    return npc_at_slot(res_npc_role(role));
}

static inline int32_t npc_towne(npc_t n) {
    return n.p->towne;
}
static inline int32_t npc_x(npc_t n) {
    return n.p->x;
}
static inline int32_t npc_y(npc_t n) {
    return n.p->y;
}
static inline int32_t npc_job(npc_t n) {
    return n.p->job;
}
static inline unsigned char npc_gender(npc_t n) {
    return n.p->gender;
}
static inline const char *npc_name(npc_t n) {
    return res_enum("name", n.slot);
}

#endif
