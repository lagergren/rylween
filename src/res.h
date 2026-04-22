#ifndef RES_H__
#define RES_H__

#include <stdint.h>

/* Resource types and accessors. Data is loaded from data/world.json5
   (a tagged JSON5 node tree with $import support); the flat JSONL
   loader retired in Phase 6d. */

typedef struct {
    const char *name;     /* NULL if no record for this id */
    int32_t cost;
    int32_t damage;
} ryl_item;

typedef struct {
    const char *name;     /* NULL if no record for this id */
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
    const char *name;     /* NULL if no record for this slot */
    int32_t slot;
    int32_t base_cost;
    int32_t effect;       /* enum ryl_spell_effect */
} ryl_spell;

/* Phase 6d: hierarchical JSON5 world loader. Parses data/world.json5
   into an in-memory tagged node tree, resolves $import directives
   recursively (cycles fatal), then folds every known subtree (flags /
   items / monsters / classes+spells / terrain / sextext / enums /
   texts) into the matching legacy tables. If `path` does not exist the
   function is a no-op (empty world, accessors return NULL / ""). */
void res_load_world(const char *path);

/* Opaque accessor for the parsed world tree. Returns NULL if no world
   file was loaded. The returned pointer is consumed by the evaluator
   (landing in the follow-up commit that implements eval_cond / _effect /
   _expr over docs/dsl.md §6). */
const void *res_world(void);

/* Flags subtree (docs/dsl.md §3.1): symbolic id -> party/encounter slot.
   res_flag_slot returns -1 for unknown ids. res_flag_value returns "" for
   unknown ids or out-of-range indices; never NULL. */
int32_t     res_flag_slot(const char *id);
const char *res_flag_value(const char *id, int index);

/* Enum: values[index] for matching record. "" if missing/out of range. */
const char *res_enum(const char *id, int index);

/* Text: content strings for {"kind":"text",...}. "" if missing. Never NULL. */
const char *res_text(const char *id);

/* Items 0..111. NULL if no record. */
const ryl_item *res_item(int id);

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

/* Terrain byte -> two-char glyph the renderer writes. "  " if unknown. */
const char *res_glyph(char terrain);

/* Terrain byte -> ANSI foreground SGR code (30..37). Default 36 (cyan)
   if the byte has no color binding in the world file. */
int32_t res_color(char terrain);

/* Pronoun for sex (1=male, 2=female) and xx (1=subject, 2=object, 3=possessive).
   "" if missing. Never NULL. */
const char *res_sextext(int sex, int xx);

#endif
