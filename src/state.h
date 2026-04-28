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

/* The UI maps inventory letters to slots (A=1..P=16). The mapping
   relies on the slot count fitting into the 26-letter alphabet; if
   a future limits: knob ever pushes this past 26, the letter-decode
   helpers below need a different keying strategy (chord, two
   letters, etc.). Force a build break before the data drift gets
   loose. */
_Static_assert(INVENTORY_SLOT_MAX <= 26,
               "inventory letter decode runs off the end of the alphabet");

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

/* Sentinel value for `struct person.towne` (NPC placed in the
   wilderness) and `state.twn` (player not standing in any town).
   Distinct from any valid towns[] slot id — towns are 0-indexed
   after §25.B step 3b, so the sentinel sits below the valid range
   to keep array-index comparisons unambiguous. The persistence
   layer (driv.c::write_current_world / read_current_world) bridges
   WILDERNESS_TOWN <-> the symbolic "wilderness" enum string;
   in-memory comparisons should use this constant directly. */
#define WILDERNESS_TOWN (-1)

/* Static caps for the persistent NPC and party slot tables on
   `struct ryl_state`. The runtime-active counts come from the data
   layer (`res_npc_slot_max()`) and from `state.num_parties` — these
   constants are just the upper bounds the static table can hold so
   indexing `state.npc[slot]` / `state.p[i]` stays in-bounds for any
   valid slot id. NPC capacity = up to NPC_SLOT_HARD_MAX records
   (slots 0..NPC_SLOT_HARD_MAX-1, post-§25.B step 4 axis flip);
   party capacity = slot 0 (unused) + 25 parties (driv.c::create_party
   refuses #26 with "all parties created"). Bumping these requires
   matching changes in the data layer; the NPC bound is also asserted
   against `res_npc_slot_max()` once the world has loaded. */
#define NPC_SLOT_HARD_MAX 41
#define PARTY_SLOT_HARD_MAX 26

/* Compile-time cap on the towns[] table in res.c. The runtime-active
   count comes from `res_town_count()`; this is just the static array
   bound. Capacity = slot 0 (null wilderness sentinel pre-§25.B step
   3b; first real town after) + up to TOWN_SLOT_HARD_MAX-1 declared
   towns. Bumping requires no other changes (fold_towns res_die()s
   if a world declares more than fit); §12.3 will eventually make
   the table heap-allocated off `limits.towns_max`. */
#define TOWN_SLOT_HARD_MAX 5

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
    int32_t towne;             /* WILDERNESS_TOWN=wilderness, else town slot id*/
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
       heap-allocated to size res_party_member_max() at party-realize /
       load time; members live at slots 0..res_party_member_max() - 1.
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

    int32_t *dead;                  /* Dead | Alive */
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
    struct party p[PARTY_SLOT_HARD_MAX];
    /* NPC runtime table — heap-allocated to res_npc_slot_max()
       entries by res_load_world() (post-§25.B step 4 axis flip)
       so the size matches the data layer's slot count.
       NPC_SLOT_HARD_MAX is the static ceiling res_npc_slot_max() is
       asserted against; the in-memory table is right-sized at world
       load. NULL until res_load_world() runs; res_shutdown() frees
       it. All readers / writers index through state.npc[slot]
       (slot < res_npc_slot_max()). */
    struct person *npc;
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
    int m; /* 0..N-1, where N = res_party_member_max() */
} pm_t;

static inline pm_t pm_of(struct party *p, int m) {
    assert(p && m >= 0 && m < res_party_member_max());
    pm_t r = {p, m};
    return r;
}
static inline pm_t pm_cur(int m) {
    assert(state.cpl >= 1 && state.cpl <= 25);
    return pm_of(&state.p[state.cpl], m);
}

/* Member-index from an ASCII digit keypress. '1'..'N' -> pm_cur(0..N-1).
   The player-facing prompt stays "Who? (1-N)" so digit '1' is the first
   member; the helper translates digit-displayed-as-1-based to slot-as-
   0-based. Any other byte returns an invalid handle with m out of range;
   callers are expected to validate the keypress before calling
   (pm_digit_in_range below). */
static inline pm_t pm_from_digit(char c) {
    return pm_cur((int)(c - '1'));
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

/* Predicate sibling: 1 iff `c` decodes to a valid inventory slot.
   Replaces the hardcoded `if (d >= 'A' && d <= 'P')` gates that
   pre-filter user keystrokes — the upper bound now tracks
   INVENTORY_SLOT_MAX rather than an open-coded 'P'. */
static inline int inv_letter_in_range(char c) {
    return inv_slot_from_letter(c) != 0;
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
static inline int pm_ws(pm_t pm) {
    return pm.party->ws[pm.m];
}
static inline int pm_wi(pm_t pm) {
    return pm.party->wi[pm.m];
}
static inline int pm_ch(pm_t pm) {
    return pm.party->ch[pm.m];
}
static inline int pm_ra(pm_t pm) {
    return pm.party->ra[pm.m];
}
static inline uint32_t pm_exp(pm_t pm) {
    return pm.party->exp[pm.m];
}
static inline unsigned char pm_gender(pm_t pm) {
    return pm.party->gender[pm.m];
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
static inline void pm_set_lv(pm_t pm, int v) {
    pm.party->lv[pm.m] = v;
}
static inline void pm_add_exp(pm_t pm, uint32_t d) {
    pm.party->exp[pm.m] += d;
}
static inline void pm_set_exp(pm_t pm, uint32_t v) {
    pm.party->exp[pm.m] = v;
}
static inline void pm_set_ws(pm_t pm, int v) {
    pm.party->ws[pm.m] = v;
}
static inline void pm_set_wi(pm_t pm, int v) {
    pm.party->wi[pm.m] = v;
}
static inline void pm_set_ch(pm_t pm, int v) {
    pm.party->ch[pm.m] = v;
}
static inline void pm_set_ma(pm_t pm, int v) {
    pm.party->ma[pm.m] = v;
}
static inline void pm_add_ma(pm_t pm, int d) {
    pm.party->ma[pm.m] += d;
}
static inline void pm_set_cl(pm_t pm, int v) {
    pm.party->cl[pm.m] = v;
}
static inline void pm_set_ra(pm_t pm, int v) {
    pm.party->ra[pm.m] = v;
}
static inline void pm_set_gender(pm_t pm, unsigned char v) {
    pm.party->gender[pm.m] = v;
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
   accessors that need the slot (npc_name -> res_npc_name(slot))
   don't have to reach back into state.npc arithmetic.

   Two constructors:

     npc_at_slot(slot)   — slot is already known (iteration, tests)
     npc_for_role(role)  — look up via npc_roles: in world.json5,
                           fatal if the role isn't bound (caller
                           bug or data rename out of sync)

   Accessors are pointer-chases through n.p for the struct-person
   fields; npc_name goes through res_npc_name since the display
   string lives on the npcs subtree's per-record `.name` field, not
   the struct. */

/* Forward declarations (implemented in res.c). Keeps state.h from
   pulling in res.h / ryl.h — the NPC handle only needs res_npc_role
   to resolve role->slot and res_npc_name to format the display string. */
int32_t res_npc_role(const char *role);
const char *res_npc_name(int32_t slot);

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

/* Direct struct-person handle for save/load paths that walk every NPC
   and need to read or write struct fields directly. Equivalent to
   npc_at_slot(slot).p but reads cleanly at sites that don't need the
   slot field. Mirrors party_at: the cross-NPC escape hatch for the
   handful of save/load loops; per-field reads inside game code go
   through npc_x / npc_y / npc_visited / etc. */
static inline struct person *npc_record_at(int slot) {
    assert(slot >= 0);
    return &state.npc[slot];
}

static inline int32_t npc_towne(npc_t n) {
    return n.p->towne;
}
static inline int npc_in_wilderness(npc_t n) {
    return n.p->towne == WILDERNESS_TOWN;
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
static inline int32_t npc_building(npc_t n) {
    return n.p->building;
}
static inline int32_t npc_nr(npc_t n) {
    return n.p->nr;
}
static inline const char *npc_name(npc_t n) {
    return res_npc_name(n.slot);
}

/* Per-NPC visited flag indexed by party slot. Sized to fit
   PARTY_SLOT_HARD_MAX (struct person.visited is unsigned char[26],
   matching the party-slot static cap). */
static inline unsigned char npc_visited(npc_t n, int party_slot) {
    assert(party_slot >= 0 &&
           (size_t)party_slot < sizeof n.p->visited / sizeof n.p->visited[0]);
    return n.p->visited[party_slot];
}
static inline void npc_visited_set(npc_t n, int party_slot, unsigned char v) {
    assert(party_slot >= 0 &&
           (size_t)party_slot < sizeof n.p->visited / sizeof n.p->visited[0]);
    n.p->visited[party_slot] = v;
}
static inline int npc_is_at(npc_t n, int x, int y) {
    return n.p->x == x && n.p->y == y;
}

#endif
