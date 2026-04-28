#ifndef STATE_H__
#define STATE_H__

#include <assert.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

/* Game state shapes + party-member accessors.
 *
 * Split out of ryl.h so the prototype soup is reviewable and can be
 * sliced into per-module headers in a later commit. The existing
 * `struct party` SoA layout is preserved (save-format compatibility);
 * pm_t is a cheap by-value view so new code reads
 * `pm_level(pm)` instead of `state.p[state.cpl].members[m].level`.
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

/* Forward decl: party-slot cap (drives the per-party / per-NPC byte
   arrays that index by party slot — struct party.attack_today and
   struct person.visited are heap-allocated to this size). Implemented
   in res.c. */
int res_party_slot_max(void);

/* Sentinel value for `struct person.towne` (NPC placed in the
   wilderness) and `state.twn` (player not standing in any town).
   Distinct from any valid towns[] slot id — towns are 0-indexed
   after §25.B step 3b, so the sentinel sits below the valid range
   to keep array-index comparisons unambiguous. The persistence
   layer (driv.c::write_current_world / read_current_world) bridges
   WILDERNESS_TOWN <-> the symbolic "wilderness" enum string;
   in-memory comparisons should use this constant directly. */
#define WILDERNESS_TOWN (-1)

/* Default ceilings for the persistent NPC and party slot tables.
   The runtime-active counts come from the data layer
   (`res_npc_slot_max()` / `res_party_slot_max()`); state.npc[],
   state.p[], struct party.attack_today[], and struct person.visited[]
   are all heap-allocated off those at world-load / party-realize time.
   NPC_SLOT_HARD_MAX is still a defensive upper bound that
   `npc_slot_max()` rejects worlds against; PARTY_SLOT_HARD_MAX is
   just the default value used when world.json5 omits
   `limits.party_slot_max` (after the §12.3 final cap-flip in 2026-04-28
   #12, the data knob accepts any value >= 2 since every party-slot-
   indexed buffer + bounds check is heap- / runtime-bound). Slot 0
   of the party table is reserved (1-indexed); valid party ids are
   1..res_party_slot_max()-1; driv.c::create_party refuses past
   `res_party_slot_max() - 1` with "all parties created". */
#define NPC_SLOT_HARD_MAX   41
#define PARTY_SLOT_HARD_MAX 26

/* World-map static-ceiling dimensions. The runtime capacity comes
   from `world.json5 map.rows_max` / `map.cols_max` via
   res_map_rows_max() / res_map_cols_max() (res.h), and state.map is
   a flat 1D heap buffer of rows × cols bytes — these defines are
   only the defensive upper bounds fold_map_dims clamps the data
   knobs against. Defaults (42 × 82) match the legacy data/map.dat
   layout (40 used rows in slots 1..40 of a [42] array; row 0 +
   row 41 unused; each row up to 81 byte-cells + newline). The
   runtime active row count populated from data/map.dat is
   `res_int_path("map.rows")`, independent of the capacity.
   wld_rows() / wld_cols() (wld.h) are the consumer-facing names. */
#define MAP_ROWS_HARD_MAX 42
#define MAP_COLS_HARD_MAX 82

/* Compile-time cap on the towns[] table in res.c. The runtime-active
   count comes from `res_town_count()`; this is just the static array
   bound. Capacity = slot 0 (null wilderness sentinel pre-§25.B step
   3b; first real town after) + up to TOWN_SLOT_HARD_MAX-1 declared
   towns. Bumping requires no other changes (fold_towns res_die()s
   if a world declares more than fit); §12.3 will eventually make
   the table heap-allocated off `limits.towns_max`. */
#define TOWN_SLOT_HARD_MAX 5

struct person {
    /* §24bis phase 3 job axis: slug pointer interned in res's enum
       keys[] table (stable across program lifetime). NULL for unset.
       Legacy values it represents (now derived via res_enum_id at the
       npc_job / save boundary):
         1 Merchant  / 2 Ranger    / 3 Fighter   / 4 Wizard
         5 Priest    / 6 Thief     / 7 Peasant   / 8 Bard
         9 Drifter   /10 Half-Orc  /11 Half-Elf  /12 Smith
        13 Bargirl   /14 Innkeeper /15 Tavernkeeper /16 Hermit       */
    const char *job;

    int32_t nr;       /* Reference number 1-40*/
    int32_t towne;    /* WILDERNESS_TOWN=wilderness, else town slot id*/
    int32_t building; /* What building? 1=inn, 2=tavern. Not valid for wilderness*/
    int32_t x;        /* What X coord.*/
    int32_t y;        /* What Y coord. Not valid for townes.*/
                      /* The wilderness X&Y coord automagically become*/
                      /* HH (hut), if they are set for a character*/
    /* Per-party "have we met?" flags, indexed by party slot. Heap-
       allocated to res_party_slot_max() bytes per NPC at world-load
       time (alongside state.npc[]); freed in res_shutdown. Calloc'd
       so the default state is "not yet visited" for every party. */
    unsigned char *visited;
    unsigned char gender; /* 1=male, 2=female. Drives res_pronoun
                                  when the NPC is referred to in prose
                                  and splits gender-flavoured chitchat
                                  in speak.c. Authored in world.json5
                                  npcs records; defaults to 1 (male). */
};

/* One adventurer in a party. Storage shape for the per-member stats
   that used to live as 14 parallel heap arrays on struct party. The
   slug-pointer fields (class, race) point into res's enum keys[]
   table — stable across program lifetime, NULL for unset slots. */
struct party_member {
    char name[30];
    int32_t weapon_skill;
    int32_t health;
    int32_t hit_points;
    int32_t wisdom;
    int32_t magic;
    int32_t charisma;
    const char *class_slug; /* §24bis phase 3 slug; res_enum_id recovers int */
    const char *race_slug;  /* §24bis phase 3 slug; res_enum_id recovers int */
    int32_t level;
    int32_t armour;
    int32_t weapon;
    uint32_t experience;
    int32_t dead;
    unsigned char gender; /* 1=male, 2=female */
};

struct party {
    char player[30];
    char party_name[30];

    /* Per-member records (size res_party_member_max(), 0-indexed). Heap-
       allocated by party_realize at create / load time; freed by
       party_release_members. Single allocation replaces the 14 parallel
       per-field heap arrays the SoA layout used pre-§24bis phase 4. */
    struct party_member *members;

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
       per-member hit-points damage. "pigg" was the original Swedish
       (lively/untired); this reads as "hours the party remains
       rested". */
    int32_t hours_rested;

    int32_t inventory[INVENTORY_SLOT_MAX + 1];

    /* "Parties attacked today", indexed by other-party slot. Heap-
       allocated to res_party_slot_max() bytes per party at
       party_realize time; freed by party_release_members. Turns to
       zero every time the attacked party logs on. */
    unsigned char *attack_today;
    unsigned char waiting_mail; /* For the guild */
    unsigned char attacked;     /* Attack bulletin to display upon login */

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
    /* World-map runtime grid — flat 1D buffer of res_map_rows_max() ×
       res_map_cols_max() bytes, heap-allocated at world-load time.
       Tile (x, y) lives at `state.map[y * res_map_cols_max() + x]`;
       wld.c is the only consumer that should compute that offset
       directly. NULL until res_load_world() runs; res_shutdown()
       frees it. All readers / writers go through wld.h
       (wld_tile_at / wld_set_tile_at / wld_load_row). MAP_ROWS_HARD_MAX
       / MAP_COLS_HARD_MAX are defensive ceilings the data layer
       clamps against; the runtime active dims come from the data. */
    char *map;
    /* Party runtime table — heap-allocated to res_party_slot_max()
       entries by res_load_world() so the size matches the data
       layer's cap. After 2026-04-28 #12's final cap-flip the data
       knob is unbounded above 2 (PARTY_SLOT_HARD_MAX is just the
       default); the in-memory table is right-sized at world load.
       NULL until res_load_world() runs; res_shutdown() frees it.
       All readers / writers go through party_at(slot) (party.h).
       Slot 0 is the reserved 1-indexed sentinel; valid party ids
       are 1..res_party_slot_max()-1. */
    struct party *p;
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

/* §24bis phase 3 cleanup: PC_FIGHTER..PC_RANGER int constants retired.
   struct party.cl[] now stores the slug pointer (e.g. "fighter") into
   res's enum keys[] table; class predicates compare by slug via
   pm_class_is_sym / pa_class_is_sym. The legacy int slot id (1..6) is
   still recoverable at the snapshot / save / DSL boundary via
   res_enum_id("pc_class", slug). */

/* View handle over a single party member. Cheap by-value. Field access
   goes through pm_* accessors so call sites read `pm_level(pm)` instead of
   `state.p[state.cpl].members[m].level`. The `struct party` SoA layout is per-
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
    assert(state.cpl >= 1 && state.cpl < res_party_slot_max());
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

/* Forward declarations (implemented in res.c). Same pattern as
   res_npc_role / res_npc_name above — keeps state.h from pulling res.h.
   res_enum_id is paired with res_enum_sym for the slug ↔ int round-trip
   used by pm_race_id (§24bis phase 3 race axis): storage is slug, int form
   recovered at the boundary so legacy callers keep their int API. */
const char *res_enum_sym(const char *id, int index);
int res_enum_id(const char *id, const char *value);

static inline struct party_member *pm_rec(pm_t pm) {
    return &pm.party->members[pm.m];
}
static inline int pm_class_id(pm_t pm) {
    const char *s = pm_rec(pm)->class_slug;
    return s ? res_enum_id("pc_class", s) : 0;
}
static inline int pm_level(pm_t pm) {
    return pm_rec(pm)->level;
}
static inline int pm_magic(pm_t pm) {
    return pm_rec(pm)->magic;
}
static inline int pm_hit_points(pm_t pm) {
    return pm_rec(pm)->hit_points;
}
static inline int pm_health(pm_t pm) {
    return pm_rec(pm)->health;
}
static inline int pm_dead(pm_t pm) {
    return pm_rec(pm)->dead;
}
static inline char *pm_name(pm_t pm) {
    return pm_rec(pm)->name;
}
static inline int pm_weapon_skill(pm_t pm) {
    return pm_rec(pm)->weapon_skill;
}
static inline int pm_wisdom(pm_t pm) {
    return pm_rec(pm)->wisdom;
}
static inline int pm_charisma(pm_t pm) {
    return pm_rec(pm)->charisma;
}
static inline int pm_race_id(pm_t pm) {
    const char *s = pm_rec(pm)->race_slug;
    return s ? res_enum_id("race", s) : 0;
}
static inline uint32_t pm_experience(pm_t pm) {
    return pm_rec(pm)->experience;
}
static inline unsigned char pm_gender(pm_t pm) {
    return pm_rec(pm)->gender;
}

static inline int pm_is_class(pm_t pm, int cls) {
    return pm_class_id(pm) == cls;
}

/* §24bis phase 1 — slug-form siblings to pm_class_id / pm_race_id. Returns the
   lowercase enum key ("fighter" / "thief" / ... for class; "human" /
   "dwarf" / ... for race) so call sites can compare against script-
   stable symbols rather than the PC_FIGHTER / 1..6 magic numbers.
   Storage is unchanged; the helpers just translate at the boundary.
   Empty string for the no-class / no-race sentinel slot 0. */
static inline const char *pm_class_sym(pm_t pm) {
    /* §24bis phase 3: class storage is the slug pointer; passthrough.
       Empty string for unset slots so caller string compares can't
       match by accident. */
    const char *s = pm.party->members[pm.m].class_slug;
    return s ? s : "";
}
static inline const char *pm_race_sym(pm_t pm) {
    /* §24bis phase 3: race storage is the slug pointer; passthrough.
       Empty string for unset slots so caller string compares can't
       match by accident. */
    const char *s = pm.party->members[pm.m].race_slug;
    return s ? s : "";
}
static inline int pm_class_is_sym(pm_t pm, const char *sym) {
    const char *s = pm_class_sym(pm);
    return s && sym && strcmp(s, sym) == 0;
}
static inline int pm_race_is_sym(pm_t pm, const char *sym) {
    const char *s = pm_race_sym(pm);
    return s && sym && strcmp(s, sym) == 0;
}
/* Class predicates by slug. §24bis phase 3 cleanup: storage is the
   slug pointer, so these compare directly to the script-stable enum
   key without going through PC_* magic numbers. */
static inline int pm_is_fighter(pm_t pm) {
    return pm_class_is_sym(pm, "fighter");
}
static inline int pm_is_thief(pm_t pm) {
    return pm_class_is_sym(pm, "thief");
}
static inline int pm_is_mage(pm_t pm) {
    return pm_class_is_sym(pm, "mage");
}
static inline int pm_is_priest(pm_t pm) {
    return pm_class_is_sym(pm, "priest");
}
static inline int pm_is_bard(pm_t pm) {
    return pm_class_is_sym(pm, "bard");
}
static inline int pm_is_ranger(pm_t pm) {
    return pm_class_is_sym(pm, "ranger");
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
static inline void pm_set_hit_points(pm_t pm, int v) {
    pm_rec(pm)->hit_points = v;
}
static inline void pm_add_hit_points(pm_t pm, int d) {
    pm_rec(pm)->hit_points += d;
}
static inline void pm_set_health(pm_t pm, int v) {
    pm_rec(pm)->health = v;
}
static inline void pm_add_health(pm_t pm, int d) {
    pm_rec(pm)->health += d;
}
static inline void pm_add_weapon_skill(pm_t pm, int d) {
    pm_rec(pm)->weapon_skill += d;
}
static inline void pm_add_wisdom(pm_t pm, int d) {
    pm_rec(pm)->wisdom += d;
}
static inline void pm_add_charisma_clamp0(pm_t pm, int d) {
    struct party_member *r = pm_rec(pm);
    r->charisma += d;
    if (r->charisma < 0) {
        r->charisma = 0;
    }
}
static inline void pm_add_charisma(pm_t pm, int d) {
    pm_rec(pm)->charisma += d;
}
static inline void pm_add_level(pm_t pm, int d) {
    pm_rec(pm)->level += d;
}
static inline void pm_set_level(pm_t pm, int v) {
    pm_rec(pm)->level = v;
}
static inline void pm_add_experience(pm_t pm, uint32_t d) {
    pm_rec(pm)->experience += d;
}
static inline void pm_set_experience(pm_t pm, uint32_t v) {
    pm_rec(pm)->experience = v;
}
static inline void pm_set_weapon_skill(pm_t pm, int v) {
    pm_rec(pm)->weapon_skill = v;
}
static inline void pm_set_wisdom(pm_t pm, int v) {
    pm_rec(pm)->wisdom = v;
}
static inline void pm_set_charisma(pm_t pm, int v) {
    pm_rec(pm)->charisma = v;
}
static inline void pm_set_magic(pm_t pm, int v) {
    pm_rec(pm)->magic = v;
}
static inline void pm_add_magic(pm_t pm, int d) {
    pm_rec(pm)->magic += d;
}
static inline void pm_set_class_id(pm_t pm, int v) {
    /* §24bis phase 3 class axis: storage is slug pointer interned in
       res's keys[] table. Mirrors pm_set_race_id. */
    const char *s = res_enum_sym("pc_class", v);
    pm_rec(pm)->class_slug = (s && s[0]) ? s : NULL;
}
static inline void pm_set_race_id(pm_t pm, int v) {
    /* §24bis phase 3 race axis: storage is slug pointer interned in
       res's keys[] table. res_enum_sym returns "" for out-of-range
       indices; we map that to NULL so the unset sentinel matches the
       allocator's calloc-zero fresh-slot state. */
    const char *s = res_enum_sym("race", v);
    pm_rec(pm)->race_slug = (s && s[0]) ? s : NULL;
}
static inline void pm_set_gender(pm_t pm, unsigned char v) {
    pm_rec(pm)->gender = v;
}
static inline void pm_set_dead(pm_t pm, int v) {
    pm_rec(pm)->dead = v;
}
static inline int pm_weapon(pm_t pm) {
    return pm_rec(pm)->weapon;
}
static inline void pm_set_weapon(pm_t pm, int v) {
    pm_rec(pm)->weapon = v;
}
static inline int pm_armour(pm_t pm) {
    return pm_rec(pm)->armour;
}
static inline void pm_set_armour(pm_t pm, int v) {
    pm_rec(pm)->armour = v;
}
/* Equipped-slot predicates. wp/ar of 0 means no weapon / no armour
   equipped — the inventory item-id 0 sentinel doubles as "empty".
   Replaces the bare `pm_weapon(pm) != 0` peppered through battle / spell
   gating. */
static inline int pm_has_weapon(pm_t pm) {
    return pm_weapon(pm) != 0;
}
static inline int pm_has_armour(pm_t pm) {
    return pm_armour(pm) != 0;
}

/* Heal to full: hit_points = health. No-op if dead. */
static inline void pm_full_heal(pm_t pm) {
    if (!pm_dead(pm)) {
        struct party_member *r = pm_rec(pm);
        r->hit_points = r->health;
    }
}

/* Clamp hit_points up to `floor` (leaves it alone if already above).
   Used after level-up to ensure the post-gain health bump rescues
   partial-hit-points members to at least 2/3 health. */
static inline void pm_hit_points_floor(pm_t pm, int floor) {
    struct party_member *r = pm_rec(pm);
    if (r->hit_points < floor) {
        r->hit_points = floor;
    }
}

/* Current-party slot index (state.cpl). Reads / writes go through the
   helper so future state-shape changes (slug-keyed party identity,
   dynamic sizing) only touch state.h. The slot is 1-based today
   matching the legacy state.p[] 1-indexed layout. */
static inline int party_current_slot(void) {
    return state.cpl;
}
static inline void party_set_current_slot(int slot) {
    state.cpl = slot;
}

/* Total active parties in the realm (state.num_parties). Plain
   getter / mutator pair — the count is incremented on createparty,
   decremented on terminate / login-failure paths. */
static inline int party_count(void) {
    return state.num_parties;
}
static inline void party_set_count(int n) {
    state.num_parties = n;
}
static inline void party_count_inc(void) {
    state.num_parties++;
}
static inline void party_count_dec(void) {
    state.num_parties--;
}

/* Active-party accessor. Every write site that reads
   `state.p[state.cpl]` should prefer party_current(): the name
   carries the invariant (current-player-party) that the raw
   indexed access obscures. */
static inline struct party *party_current(void) {
    return &state.p[state.cpl];
}

/* ------------ Session-level scalars ----------------------------------
   Top-level fields of struct ryl_state that aren't tied to a party,
   NPC, or map abstraction module. The getters/setters here are pure
   field aliases — the abstraction's value is encapsulation: when the
   storage shape changes (§12.3 dynamic sizing, §24bis slug-keyed
   state, save-format rework) only state.h moves. */

/* Logged-in player display name (state.global_name). Set once by
   main() from argv; every other site is a read. */
static inline const char *session_player_name(void) {
    return state.global_name;
}

/* Wall-clock day cursors. last_played_day records the day the realm
   was last advanced; day_at_game_start is captured per-boot for status
   / score displays. */
static inline int32_t session_last_played_day(void) {
    return state.last_played_day;
}
static inline void session_set_last_played_day(int32_t d) {
    state.last_played_day = d;
}
static inline int32_t session_day_at_game_start(void) {
    return state.day_at_game_start;
}
static inline void session_set_day_at_game_start(int32_t d) {
    state.day_at_game_start = d;
}

/* Banished flag (state.banished). Set by the priest banish ritual via
   the DSL mutator; read by dungeon.c to gate dungeon entry; cleared on
   dungeon entry. */
static inline int32_t session_banished(void) {
    return state.banished;
}
static inline void session_set_banished(int32_t v) {
    state.banished = v;
}

/* Hour of day (state.hour_of_day, 1..24). The plain getter/setter
   pair lets the DSL eval/set the field directly; advance_hour() is
   the only correct ++ path. */
static inline int32_t session_hour(void) {
    return state.hour_of_day;
}
static inline void session_set_hour(int32_t h) {
    state.hour_of_day = h;
}

/* Current town (state.twn). Owned by towne.c (writes on enter/leave);
   the getter is for cross-module reads (script.c DSL bindings,
   snapshot writer). */
static inline int32_t session_current_town(void) {
    return state.twn;
}
static inline void session_set_current_town(int32_t t) {
    state.twn = t;
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

/* §24bis phase 3 cleanup: pm_class_id retired. Class name → int id is
   now `res_enum_id("pc_class", name)`; the same lookup that any other
   enum slug-to-int conversion uses. */

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
    /* §24bis phase 3 job axis: storage is slug pointer; recover the
       legacy int form via res_enum_id for callers that still want
       the int slot id. NULL slug → 0 (unset sentinel). */
    return n.p->job ? res_enum_id("job", n.p->job) : 0;
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

/* Per-NPC visited flag indexed by party slot. The visited[] buffer is
   heap-allocated by res_load_world to res_party_slot_max() bytes per
   NPC; the bound below catches stale slot ids before they index into
   the buffer. */
static inline unsigned char npc_visited(npc_t n, int party_slot) {
    assert(n.p->visited && party_slot >= 0 &&
           party_slot < res_party_slot_max());
    return n.p->visited[party_slot];
}
static inline void npc_visited_set(npc_t n, int party_slot, unsigned char v) {
    assert(n.p->visited && party_slot >= 0 &&
           party_slot < res_party_slot_max());
    n.p->visited[party_slot] = v;
}
static inline int npc_is_at(npc_t n, int x, int y) {
    return n.p->x == x && n.p->y == y;
}

/* §24bis phase 1 — slug-form sibling to npc_job. Returns the lowercase
   `job` enum key (e.g. "merchant", "innkeeper") so dispatch code can
   compare against script-stable symbols rather than the int slot.
   §24bis phase 3: storage is the slug pointer; passthrough. */
static inline const char *npc_job_sym(npc_t n) {
    return n.p->job ? n.p->job : "";
}
static inline int npc_job_is_sym(npc_t n, const char *sym) {
    const char *s = npc_job_sym(n);
    return s && sym && strcmp(s, sym) == 0;
}

#endif
