#ifndef RES_H__
#define RES_H__

#include <stdint.h>

/* Flat JSONL resource loader. Records are {"kind":"...","id":"...",...}
   one per line; unknown fields are tolerated. */

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
    SE_FROST,
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

void res_load(const char *path);

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

/* Terrain glyph -> two-char display. "  " if unknown. Never NULL. */
const char *res_kar(char glyph);

/* Terrain glyph -> ANSI foreground color code. Default 36 if unknown. */
int32_t res_frg(char glyph);

/* Pronoun for sex (1=male, 2=female) and xx (1=subject, 2=object, 3=possessive).
   "" if missing. Never NULL. */
const char *res_sextext(int sex, int xx);

#endif
