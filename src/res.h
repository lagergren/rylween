#ifndef RES_H__
#define RES_H__

#include <stdint.h>

/* Resource types and accessors. Data is loaded from data/world.json5
   (a tagged JSON5 node tree with $import support); the flat JSONL
   loader retired in Phase 6d. */

typedef struct {
    const char *name; /* NULL if no record for this id */
    int32_t cost;
    int32_t damage;
} ryl_item;

typedef struct {
    const char *name; /* NULL if no record for this id */
    int32_t n1, n2;
    int32_t h1, h2;
    int32_t ws;
    int32_t sp;
    int32_t im;
    /* Optional level-scaling override: when non-zero, battle.c uses
       n2 + state.p[state.cpl].lv[1] / n2_lv_div as the count-max
       instead of the static n2. Encodes the legacy gnoll special
       case `n2 = 2 + lv/2` as data. Zero means no override. */
    int32_t n2_lv_div;
} ryl_monster;

/* Spell effect identities. Decouples behavior from UI slot — the menu
   order (`slot`) can change without breaking dispatch. Each SE_* gets a
   corresponding case in the priest/mage effect switches; when the phase
   6d DSL lands, each case becomes `eval_effect(s->effect_tree)`. */
enum ryl_spell_effect {
    SE_NONE = 0,
    SE_HEAL,
    SE_RESTORATION,
    SE_REBIRTH,
    SE_BANISH,
    SE_REVELATION,
    SE_TELEPORT,
    SE_EARTHQUAKE,
    SE_FIRE,
    SE_CONFUSION,
    SE_DEATH,
    SE_HOLD,
    SE_ANTIMAGIC,
};

typedef struct {
    const char *id;
    const char *name; /* NULL if no record for this slot */
    int32_t slot;
    int32_t base_cost;
    int32_t effect; /* enum ryl_spell_effect */
} ryl_spell;

/* Phase 6d: hierarchical JSON5 world loader. Parses data/world.json5
   into an in-memory tagged node tree, resolves $import directives
   recursively (cycles fatal), then folds every known subtree (flags /
   items / monsters / classes+spells / terrain / pronoun / enums /
   texts) into the matching legacy tables. If `path` does not exist the
   function is a no-op (empty world, accessors return NULL / ""). */
void res_load_world(const char *path);

/* Release every heap allocation owned by this module: the world node
   tree plus every fold target (items/monsters/spells/glyph/pronoun
   string slots; the dynamic flags/enums/texts backing arrays). All
   file-scope globals reset to their initial zero state so a subsequent
   res_load_world() is safe. Idempotent; safe on an empty module.

   Registered via atexit() at the end of res_load_world; also called
   explicitly from cleanexit() so exits that bypass atexit (unlikely,
   but `_exit` / abort()) still drop the legacy tables. */
void res_shutdown(void);

/* Flags subtree (docs/dsl.md §3.1): symbolic id -> party/encounter slot.
   Returns -1 for unknown ids. */
int32_t res_flag_slot(const char *id);

/* Flags subtree: values[] lookup. res_flag_value_id resolves a named
   state (e.g. "rescued") to its index in the flag's values: array;
   res_flag_value is the inverse (index -> string). Both return -1 / ""
   on unknown flag id, unknown value, or out-of-range index. */
int32_t res_flag_value_id(const char *flag_id, const char *value);
const char *res_flag_value(const char *flag_id, int index);

/* Enum: values[index] for matching record. "" if missing/out of range. */
const char *res_enum(const char *id, int index);

/* Enum inverse: index where values[i] matches `value`. -1 if the enum id
   is unknown or no entry matches. Linear search — suitable for loader
   paths, not hot-path code. */
int res_enum_id(const char *id, const char *value);

/* Text: content strings for {"kind":"text",...}. "" if missing. Never NULL. */
const char *res_text(const char *id);

/* Non-empty existence probe distinct from res_text (which collapses
   "missing" into "" and loses the 1-bit of information against an
   intentionally-empty record). Used by resolve_text_id to decide
   between the nested-script lookup and the shared-atom fallback. */
int res_text_exists(const char *id);

/* Items 0..111. NULL if no record. */
const ryl_item *res_item(int id);

/* Symbolic item lookup (§16.5). Returns the id of the item whose shard
   key matches `sym` (e.g. "red_gem" -> 39); -1 if unknown. Walks the
   `items.<shard>.<sym>` subtree of the world tree — loader-path speed,
   linear search. Lets §16.4 scripts author reward lists by name
   instead of hard-coding ints. */
int32_t res_item_id(const char *sym);

/* Walk every item record carrying a string `special:` tag (see
   data/world/items/specials.json5). For each, invoke cb(id, tag, user);
   stop early if cb returns non-zero. `tag` is borrowed from the world
   tree and remains valid until res_shutdown. Used by inventory.c to
   build its id -> PS_* map from data rather than hard-coding the set. */
void res_items_foreach_special(
    int (*cb)(int id, const char *tag, void *user),
    void *user);

/* Shard (kind) each item id was loaded from in data/world/items/,
   e.g. "weapons", "armour", "gear", "potions", "treasures",
   "magic_items", "specials". NULL for ids without a record. The
   kind comes straight from the items.<section>.<sym> shard name,
   so new shards pick their own kind without a C-side change.
   Use inventory.h's item_is_* helpers for category predicates
   instead of calling this directly unless you specifically need
   the tag string. */
const char *res_item_kind(int id);

/* Integer lookup by dotted path from the world-tree root. E.g.
     res_int_path("combat.flee_chance")         -> 95
     res_int_path("upkeep.starve_damage")       -> 2
     res_int_path("wilderness.cave_chance")     -> 3
   Returns 0 if any path component is missing or the leaf isn't an
   integer. Path keys are stable by convention — the C side hard-codes
   the names it asks for, and the 0-on-missing behaviour is the
   natural no-op across most gameplay tuning callers. */
int32_t res_int_path(const char *dotted_path);

/* Shorthand: res_int_path("combat." + key). Pre-dates the more general
   res_int_path and retained for the existing battle.c call sites; new
   code should prefer res_int_path directly so the subtree root is
   visible at the call site. */
int32_t res_combat_int(const char *dotted_path);

/* XP total needed to REACH the given level (2..28). Reads
   xp_thresholds[level] from the world tree. Returns 0 for out-of-range
   or missing entries — level 1 and slots past 28 are guard values, and
   the battle.c caller treats 0 as "no further threshold" and stops the
   loop naturally. */
int32_t res_xp_threshold(int level);

/* Per-class level-up delta applied on each gained level. `cls` is a
   lowercased class name ("fighter", "thief", ...) matching the
   classes.<cls> shard key; `stat` is one of "ws" / "h" / "wi" / "ch".
   Returns 0 for unknown class or stat — callers treat zero as "no
   change", which is the natural no-op. */
int32_t res_class_levelup(const char *cls, const char *stat);

/* Named-formula evaluator. Looks up formulas.<name> in the world tree,
   evaluates the expression body after seeding one named local
   (`$<var_name>` -> `val`) into a fresh env, and returns the resulting
   int32. Intended for flat "game logic that should be data" arithmetic
   like  `150 + 150 * y` — call sites stay a one-liner while the formula
   itself lives in world.json5. Fatal if the formula is missing. */
int32_t res_formula_int(const char *name, const char *var_name, int32_t val);

/* Monsters 0..90. NULL if no record. */
const ryl_monster *res_monster(int id);

/* Spell for a given class ("priest" or "mage") and slot (1..7). NULL if missing. */
const ryl_spell *res_spell(const char *class_name, int slot);

/* DSL spell lookup. Returns the `do` body node of
   `spells."<class>.<name>"` whose slot matches, for the spell referenced
   at `classes.<class>.spells[slot-1]` in the world tree. NULL if the
   world tree doesn't carry a matching spell (fall back to res_spell /
   enum dispatch). The returned pointer is owned by the world tree. */
const void *res_spell_node(const char *class_id, int slot);

/* §16.4 dialogue script lookup. Returns the array node at
   `scripts.<dotted_id>` — e.g. res_script("speak.erethor.first_visit")
   returns the screen/journal/set_flag op list authored in the scripts
   subtree. NULL if the path doesn't resolve to an array (missing node,
   wrong kind, or the scripts subtree itself is absent). The returned
   pointer is owned by the world tree; callers must not free it. */
const void *res_script(const char *dotted_id);

/* Terrain byte -> two-char glyph the renderer writes. "  " if unknown. */
const char *res_glyph(char terrain);

/* Terrain byte -> ANSI foreground SGR code (30..37). Default 36 (cyan)
   if the byte has no color binding in the world file. */
int32_t res_color(char terrain);

/* Pronoun for gender (1=male, 2=female) and xx (1=subject, 2=object,
   3=possessive). "" if missing. Never NULL. */
const char *res_pronoun(int gender, int xx);

/* Towns — fixed display name and world-map coordinates per twn id 1..4
   (matching state.twn / struct person.towne). Order matches the
   enums.towne table. res_town_name returns "" for 0 (wilderness) or out
   of range; res_town_x / res_town_y return 0; res_town_at returns the
   twn id for a (x,y) that matches a declared town, 0 otherwise. */
const char *res_town_name(int twn);
int32_t res_town_x(int twn);
int32_t res_town_y(int twn);
int32_t res_town_at(int x, int y);
/* Percentage tax rate at the town entry (enter() tax preamble). 0 if
   twn is out of range or the town record omits `tax:`. */
int32_t res_town_tax(int twn);

/* Per-building opening hours. `name` matches a key under the
   `buildings` subtree of data/world.json5 (e.g. "smith"). Writes
   the configured opens_at / closes_at (inclusive 0..23 hours) into
   the out parameters on hit and returns 1. Returns 0 for an
   unknown name (out parameters untouched); callers should default
   before calling so the "open 24/7" fallback is explicit. Consulted
   by the towne.c C wrappers that hand bindings to play_script. */
int res_building_hours(const char *name, int *opens_at, int *closes_at);

/* §15 step 3: realize NPC placements from data/world.json5 npcs:.
   Populates state.npc[1..slot_max]. Each spec entry is either FIXED
   (towne declared — apply job/towne/building/x/y verbatim) or RANDOM
   (no towne — roll towne, building, and wilderness x/y at cold-start
   time).
   Prints the "Setting up new campaign" UI, runs the per-random-npc
   delay/dot, and zeroes each NPC's visited[] grid. Called from init()
   cold-start only; the caller follows up with write_current_world(). */
void res_realize_npcs(void);

/* Highest valid NPC slot id in the current world. Derived from
   enums.name's entry count (sentinel at 0, one name per populated
   slot). Iterate NPCs as `for (i = 1; i <= res_npc_slot_max(); i++)`.
   Returns 0 before the world has loaded. */
int res_npc_slot_max(void);

/* Highest valid party-inventory slot id. Defaults to INVENTORY_SLOT_
   MAX (state.h); overrideable via limits.inventory_slot_max in
   world.json5, clamped to INVENTORY_SLOT_MAX (the C array is sized
   at compile time). Iterate slots as `for (i = 1; i <=
   res_inventory_slot_max(); i++)`. The UI also maps keypresses
   A..P into slots 1..16, so changing the max to anything below
   INVENTORY_SLOT_MAX is the only safe override. */
int res_inventory_slot_max(void);

/* Highest valid party-member slot id. Always returns PARTY_MEMBER_MAX
   (4). There is no world.json5 knob: 4 is pinned by the save format,
   UI digit prompts ("Who? (1-4)"), and story assumptions. Iterate
   members as `for (i = 1; i <= res_party_member_max(); i++)`. */
int res_party_member_max(void);

/* Uniform "res: path:line: msg" fatal error. Used by the JSON5 parser /
   import resolver (json5.c) and the world-folds (res.c) so every loader
   diagnostic has one shape. Never returns. */
__attribute__((noreturn)) void res_die(const char *path, int line, const char *msg);

#endif
