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

/* Enum: values[index] for matching record. "" if missing/out of range. */
const char *res_enum(const char *id, int index);

/* Enum inverse: index where values[i] matches `value`. -1 if the enum id
   is unknown or no entry matches. Linear search — suitable for loader
   paths, not hot-path code. */
int res_enum_id(const char *id, const char *value);

/* Text: content strings for {"kind":"text",...}. "" if missing. Never NULL. */
const char *res_text(const char *id);

/* Items 0..111. NULL if no record. */
const ryl_item *res_item(int id);

/* Symbolic item lookup (§16.5). Returns the id of the item whose shard
   key matches `sym` (e.g. "red_gem" -> 39); -1 if unknown. Walks the
   `items.<shard>.<sym>` subtree of the world tree — loader-path speed,
   linear search. Lets §16.4 scripts author reward lists by name
   instead of hard-coding ints. */
int32_t res_item_id(const char *sym);

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

/* Pronoun for sex (1=male, 2=female) and xx (1=subject, 2=object, 3=possessive).
   "" if missing. Never NULL. */
const char *res_pronoun(int sex, int xx);

/* Towns — fixed display name and world-map coordinates per twn id 1..4
   (matching state.twn / struct person.towne). Order matches the
   enums.towne table. res_town_name returns "" for 0 (wilderness) or out
   of range; res_town_x / res_town_y return 0; res_town_at returns the
   twn id for a (x,y) that matches a declared town, 0 otherwise. */
const char *res_town_name(int twn);
int32_t res_town_x(int twn);
int32_t res_town_y(int twn);
int32_t res_town_at(int x, int y);

/* §15 step 3: realize NPC placements from data/world.json5 npcs:.
   Populates state.npc[1..slot_max]. Each spec entry is either FIXED
   (towne declared — apply job/towne/bl/x/y verbatim) or RANDOM (no
   towne — roll towne, bl, and wilderness x/y at cold-start time).
   Prints the "Setting up new campaign" UI, runs the per-random-npc
   delay/dot, and zeroes each NPC's visited[] grid. Called from init()
   cold-start only; the caller follows up with write_current_world(). */
void res_realize_npcs(void);

/* Uniform "res: path:line: msg" fatal error. Used by the JSON5 parser /
   import resolver (json5.c) and the world-folds (res.c) so every loader
   diagnostic has one shape. Never returns. */
__attribute__((noreturn)) void res_die(const char *path, int line, const char *msg);

#endif
