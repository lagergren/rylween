/* Resource loader: hierarchical JSON5 world file.
 *
 * Phase 6d retired the flat JSONL loader (data/resources.jsonl) and
 * folded all seven legacy record kinds — enum / item / monster / spell /
 * terrain / pronoun / text — into the world tree. JSON5 parsing and the
 * $import resolver live in json5.c; this file owns the folds that
 * populate the legacy in-memory tables and expose accessors to the
 * rest of the game.
 *
 * Load flow (res_load_world):
 *   1. json5_parse_file(path): JSON5 source -> node tree.
 *   2. json5_resolve_imports: splice every {"$import": "relpath"}
 *      directive with the parsed subtree of data/<relpath>. Cycles fatal.
 *   3. fold_*: walk the known subtrees (flags / items / monsters /
 *      classes+spells / texts / terrain / pronoun / enums) and populate
 *      the matching legacy in-memory tables (ryl_item / ryl_monster /
 *      ryl_spell / glyph_tab / color_tab / pronoun_tab / enums / texts).
 *
 * The folds are forgiving about field order and missing optionals but
 * fatal on structural surprises; errors dispatch through res_die with a
 * "res: path:line: msg" message. The legacy tables retain the fold
 * results for the rest of run time; the full node tree is also kept
 * (process-lifetime) for the evaluator via res_world().
 */

#include "res.h"
#include "compat.h"
#include "json5.h"
#include "log.h"
#include "party.h"
#include "primitives.h"
#include "runtime_cfg.h"
#include "script.h"
#include "wld.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define RES_MAX_ITEM_ID    111
#define RES_MAX_MONSTER_ID 90

typedef struct {
    char *id;
    char **keys;   /* keys[idx] = lowercase symbol; NULL for the legacy
                        positional-array shape and for sparse gaps in the
                        keyed-record shape. */
    char **values; /* values[idx] = display string. For positional arrays
                        this is the only string the entry has; for keyed
                        records this comes from the entry's `name:` field. */
    size_t n_values;
} enum_rec;

static enum_rec *enums;
static size_t n_enums;

typedef struct {
    char *id;
    char *text;
} text_rec;

static text_rec *texts;
static size_t n_texts;
/* Set after fold_texts completes so res_text can switch from the
   linear append path (emit_text clears the flag) to bsearch. */
static int texts_sorted;

static ryl_item items[RES_MAX_ITEM_ID + 1];
/* Shard each item id came from (e.g. "weapons", "armour", "specials").
   Pointer is borrowed from the world tree (shard key strings live on
   the node allocator) and valid until res_shutdown. NULL for ids with
   no record. Populated from fold_items; used by inventory.c for the
   item-kind predicates (item_is_weapon etc.) so C conditionals that
   used to read `oc < 20 || oc > 29` for "armour?" now ask the data. */
static const char *item_kind[RES_MAX_ITEM_ID + 1];
static ryl_monster monsters[RES_MAX_MONSTER_ID + 1];

#define RES_MAX_SPELL_SLOT 7
static ryl_spell priest_spells[RES_MAX_SPELL_SLOT + 1];
static ryl_spell mage_spells[RES_MAX_SPELL_SLOT + 1];

/* ANSI SGR foreground color codes. The authored form in world.json5 is
   the English name ("cyan"), which color_code_from_name resolves; the
   codes themselves are only referenced symbolically here so no caller
   needs to spell the numbers out. */
enum {
    ANSI_FG_BLACK = 30,
    ANSI_FG_RED,
    ANSI_FG_GREEN,
    ANSI_FG_YELLOW,
    ANSI_FG_BLUE,
    ANSI_FG_MAGENTA,
    ANSI_FG_CYAN,
    ANSI_FG_WHITE,
};

static char *glyph_tab[256];
static char *class_tab[256];
static int32_t color_tab[256];
static int color_tab_set[256];
static int32_t color_default = ANSI_FG_CYAN;

/* ANSI foreground colour name (as it appears in data/world.json5) to
   SGR code. Authors should never type the raw numbers; fold_terrain
   resolves here so the config file reads in English. */
static int32_t color_code_from_name(const char *name) {
    if (strcmp(name, "black") == 0) {
        return ANSI_FG_BLACK;
    }
    if (strcmp(name, "red") == 0) {
        return ANSI_FG_RED;
    }
    if (strcmp(name, "green") == 0) {
        return ANSI_FG_GREEN;
    }
    if (strcmp(name, "yellow") == 0) {
        return ANSI_FG_YELLOW;
    }
    if (strcmp(name, "blue") == 0) {
        return ANSI_FG_BLUE;
    }
    if (strcmp(name, "magenta") == 0) {
        return ANSI_FG_MAGENTA;
    }
    if (strcmp(name, "cyan") == 0) {
        return ANSI_FG_CYAN;
    }
    if (strcmp(name, "white") == 0) {
        return ANSI_FG_WHITE;
    }
    return -1;
}

static char *pronoun_tab[3][4];

/* Town table. Slot 0 = null (wilderness sentinel); slots 1..n_towns
   carry a display name (owned string) plus fixed (x, y) world-map
   coords. See data/world.json5 towns:. Heap-allocated by fold_towns
   to (n_towns + 1) entries — slot 0 plus one slot per declared town.
   Reset by reset_towns (which walks 0..towns_cap freeing owned name
   strings). The TOWN_SLOT_HARD_MAX named cap in state.h is now only
   the fold-time overflow check; the runtime allocation is data-
   driven, so adding a 5th/6th town in world.json5 only requires
   bumping that cap (and §12.3 will eventually retire it). */
typedef struct {
    char *name;
    int32_t x, y;
    int32_t tax;
    int32_t has_church;
    int32_t is_city; /* class: 'city' (true) or 'village'/absent (false) */
} ryl_town;
static ryl_town *towns;
static size_t towns_cap;
/* Count of declared towns (slots 1..n_towns). Populated by fold_towns. */
static size_t n_towns;

/* Per-building opening hours. Looked up by name ("smith", "tavern",
   ...) so each shop's C wrapper can hand the DSL a closed/open
   check without hardcoding literal hours in either layer. Sized at
   a small fixed cap — grown on demand if more buildings than the
   initial four-shop towne set up a record. */
typedef struct {
    char *name;
    int32_t opens_at;
    int32_t closes_at;
} ryl_building;
static ryl_building *buildings_tab;
static size_t n_buildings;

/* Cold-start realization knobs (port-plan §15.4). npc_slack is the
   `slack` term added to ceil(N/T) when computing the per-bin cap for
   the capacity-capped NPC draw in res_realize_npcs(). Default matches
   the §15.4 recommendation; overridden by data/world.json5 limits:. */
static int32_t npc_slack = 1;

/* Party-inventory slot cap. Defaults to INVENTORY_SLOT_MAX (state.h);
   overrideable at world-load via limits.inventory_slot_max, clamped
   to the compile-time array size so a smaller-than-16 realm works
   without rebuilding. A larger value is rejected at fold time — the
   UI's A..P keystroke map, state.p.inventory[] array, and many
   helpers all assume the cap is 16. */
static int32_t inventory_slot_max = INVENTORY_SLOT_MAX;

/* Party-member slot cap. Default matches the story-balanced N=4
   assumption baked into combat / damage tables; overrideable via
   limits.party_member_max for experimental larger parties. The
   per-member struct-party buffers are heap-allocated off this at
   party-realize time (§19.3), so only the static comment in state.h
   caps it — no compile-time array to bound against. Minimum 1; a
   silly-large value still allocates but the UI won't surface members
   past the "(1-N)" digit prompts. */
static int32_t party_member_max = PARTY_MEMBER_MAX;

/* Party-slot cap. Default matches PARTY_SLOT_HARD_MAX; overrideable
   via limits.party_slot_max. The three party-slot-indexed buffers
   (state.p[], struct party.attack_today[], struct person.visited[])
   are heap-allocated off this value at world-load / party-realize
   time, and subs.c::scorelist heap-allocates its per-party tables
   the same way; every party.c / state.h / snapshot.c bounds check
   resolves through res_party_slot_max(). After 2026-04-28 #12 the
   fold_limits clamp is just `>= 2`; the data layer is the single
   source of truth. */
static int32_t party_slot_max = PARTY_SLOT_HARD_MAX;

/* Flags table (docs/dsl.md §3.1). Symbolic name -> {slot, scope, values}.
   Populated from data/world.json5 flags subtree; empty until world loads. */
typedef struct {
    char *id;
    int32_t slot;
    int32_t scope; /* 0 = party (state.p[cpl].flag[slot]), 1 = encounter */
    char **values;
    size_t n_values;
} flag_rec;

static flag_rec *flags_tab;
static size_t n_flags_tab;

/* JSON5 world tree + its lazily-built per-subtree indices. Raw node
   pointers live here; the script DSL evaluator (script.c) walks them
   on demand. */
static node *world_tree;

__attribute__((noreturn)) void res_die(const char *path, int line, const char *msg) {
    LOG_E("res", "%s:%d: %s", path, line, msg);
    exit(1);
}

/* Forward decl: defined alongside the npc subtree walkers below. */
const char *res_npc_name(int32_t slot);

const char *res_enum(const char *id, int index) {
    for (size_t i = 0; i < n_enums; i++) {
        if (strcmp(enums[i].id, id) == 0) {
            if (index < 0 || (size_t)index >= enums[i].n_values) {
                return "";
            }
            return enums[i].values[index];
        }
    }
    /* `name` no longer lives as a positional array; the per-record
       `.name` field on the npcs subtree is the source of truth. The
       fallthrough keeps the engine_bind {fn:'res_enum',args:['name',...]}
       DSL surface working without forcing every script to migrate to
       a parallel new fn. */
    if (strcmp(id, "name") == 0) {
        return res_npc_name(index);
    }
    return "";
}

int res_enum_id(const char *id, const char *value) {
    if (!value) {
        return -1;
    }
    for (size_t i = 0; i < n_enums; i++) {
        if (strcmp(enums[i].id, id) != 0) {
            continue;
        }
        /* Symbol match first (lowercase key on keyed-record enums). */
        if (enums[i].keys) {
            for (size_t j = 0; j < enums[i].n_values; j++) {
                if (enums[i].keys[j] && strcmp(enums[i].keys[j], value) == 0) {
                    return (int)j;
                }
            }
        }
        /* Display fallback — covers legacy positional-array enums and
           keyed-record callers that still pass the .name string during
           migration. */
        for (size_t j = 0; j < enums[i].n_values; j++) {
            if (strcmp(enums[i].values[j], value) == 0) {
                return (int)j;
            }
        }
        return -1;
    }
    return -1;
}

const char *res_enum_name(const char *id, int index) {
    return res_enum(id, index);
}

const char *res_enum_name_by_sym(const char *id, const char *sym) {
    int slot = res_enum_id(id, sym);
    if (slot < 0) {
        return "";
    }
    return res_enum(id, slot);
}

const char *res_enum_sym(const char *id, int index) {
    for (size_t i = 0; i < n_enums; i++) {
        if (strcmp(enums[i].id, id) != 0) {
            continue;
        }
        if (index < 0 || (size_t)index >= enums[i].n_values) {
            return "";
        }
        if (!enums[i].keys || !enums[i].keys[index]) {
            /* Positional-array enum (no per-entry symbol) or empty
               slot — fall through to the display string so callers
               that just want a stable label still get one. */
            return enums[i].values[index] ? enums[i].values[index] : "";
        }
        return enums[i].keys[index];
    }
    return "";
}

static int text_cmp(const void *a, const void *b) {
    return strcmp(((const text_rec *)a)->id, ((const text_rec *)b)->id);
}

static void texts_sort(void) {
    if (texts_sorted || n_texts == 0) {
        return;
    }
    qsort(texts, n_texts, sizeof *texts, text_cmp);
    texts_sorted = 1;
}

const char *res_text(const char *id) {
    if (!texts_sorted) {
        for (size_t i = 0; i < n_texts; i++) {
            if (strcmp(texts[i].id, id) == 0) {
                return texts[i].text;
            }
        }
        return "";
    }
    text_rec key = {(char *)id, NULL};
    text_rec *hit = bsearch(&key, texts, n_texts, sizeof *texts, text_cmp);
    return hit ? hit->text : "";
}

int res_text_exists(const char *id) {
    if (!id || !id[0]) {
        return 0;
    }
    if (!texts_sorted) {
        for (size_t i = 0; i < n_texts; i++) {
            if (strcmp(texts[i].id, id) == 0) {
                return 1;
            }
        }
        return 0;
    }
    text_rec key = {(char *)id, NULL};
    return bsearch(&key, texts, n_texts, sizeof *texts, text_cmp) != NULL;
}

const ryl_item *res_item(int id) {
    if (id < 0 || id > RES_MAX_ITEM_ID) {
        return NULL;
    }
    return items[id].name ? &items[id] : NULL;
}

int32_t res_item_id(const char *sym) {
    if (!world_tree || !sym) {
        return -1;
    }
    const node *items_n = obj_get(world_tree, "items");
    if (!items_n || items_n->kind != N_OBJ) {
        return -1;
    }
    for (size_t s = 0; s < items_n->u.obj.n; s++) {
        const node *section = items_n->u.obj.items[s].val;
        if (!section || section->kind != N_OBJ) {
            continue;
        }
        for (size_t i = 0; i < section->u.obj.n; i++) {
            if (strcmp(section->u.obj.items[i].key, sym) != 0) {
                continue;
            }
            const node *rec = section->u.obj.items[i].val;
            if (!rec || rec->kind != N_OBJ) {
                return -1;
            }
            const node *id_n = obj_get(rec, "id");
            if (!id_n || id_n->kind != N_INT) {
                return -1;
            }
            return (int32_t)id_n->u.iv;
        }
    }
    return -1;
}

const char *res_item_kind(int id) {
    if (id < 0 || id > RES_MAX_ITEM_ID) {
        return NULL;
    }
    return item_kind[id];
}

void res_items_foreach_special(int (*cb)(int, const char *, void *),
                               void *user) {
    if (!world_tree || !cb) {
        return;
    }
    const node *items_n = obj_get(world_tree, "items");
    if (!items_n || items_n->kind != N_OBJ) {
        return;
    }
    for (size_t s = 0; s < items_n->u.obj.n; s++) {
        const node *section = items_n->u.obj.items[s].val;
        if (!section || section->kind != N_OBJ) {
            continue;
        }
        for (size_t i = 0; i < section->u.obj.n; i++) {
            const node *rec = section->u.obj.items[i].val;
            if (!rec || rec->kind != N_OBJ) {
                continue;
            }
            const node *id_n = obj_get(rec, "id");
            const node *sp_n = obj_get(rec, "special");
            if (!id_n || id_n->kind != N_INT) {
                continue;
            }
            if (!sp_n || sp_n->kind != N_STR) {
                continue;
            }
            if (cb((int)id_n->u.iv, sp_n->u.sv, user) != 0) {
                return;
            }
        }
    }
}

int32_t res_int_path(const char *dotted_path) {
    if (!world_tree || !dotted_path) {
        return 0;
    }
    char buf[192];
    size_t n = strlen(dotted_path);
    if (n >= sizeof buf) {
        return 0;
    }
    memcpy(buf, dotted_path, n + 1);
    const node *cur = world_tree;
    char *p = buf;
    while (*p) {
        char *dot = strchr(p, '.');
        if (dot) {
            *dot = 0;
        }
        if (cur->kind != N_OBJ) {
            return 0;
        }
        cur = obj_get(cur, p);
        if (!cur) {
            return 0;
        }
        if (!dot) {
            break;
        }
        p = dot + 1;
    }
    return (cur->kind == N_INT) ? (int32_t)cur->u.iv : 0;
}

const char *res_str_path(const char *dotted_path) {
    if (!world_tree || !dotted_path) {
        return NULL;
    }
    char buf[192];
    size_t n = strlen(dotted_path);
    if (n >= sizeof buf) {
        return NULL;
    }
    memcpy(buf, dotted_path, n + 1);
    const node *cur = world_tree;
    char *p = buf;
    while (*p) {
        char *dot = strchr(p, '.');
        if (dot) {
            *dot = 0;
        }
        if (cur->kind != N_OBJ) {
            return NULL;
        }
        cur = obj_get(cur, p);
        if (!cur) {
            return NULL;
        }
        if (!dot) {
            break;
        }
        p = dot + 1;
    }
    return (cur->kind == N_STR) ? cur->u.sv : NULL;
}

/* Number of items in the array at `dotted_path`. 0 if the path doesn't
   resolve to an array (missing path, scalar leaf, etc.) — callers can
   treat that the same as "empty array" without a separate exists probe. */
int res_str_arr_count(const char *dotted_path) {
    if (!world_tree || !dotted_path) {
        return 0;
    }
    char buf[192];
    size_t n = strlen(dotted_path);
    if (n >= sizeof buf) {
        return 0;
    }
    memcpy(buf, dotted_path, n + 1);
    const node *cur = world_tree;
    char *p = buf;
    while (*p) {
        char *dot = strchr(p, '.');
        if (dot) {
            *dot = 0;
        }
        if (cur->kind != N_OBJ) {
            return 0;
        }
        cur = obj_get(cur, p);
        if (!cur) {
            return 0;
        }
        if (!dot) {
            break;
        }
        p = dot + 1;
    }
    return (cur->kind == N_ARR) ? (int)cur->u.arr.n : 0;
}

/* String-array element at `dotted_path[idx]`. NULL if the path doesn't
   resolve to an array, the index is out of range, or the element isn't
   a string. Pointer is owned by the world tree (valid until shutdown). */
const char *res_str_arr_at(const char *dotted_path, int idx) {
    if (!world_tree || !dotted_path || idx < 0) {
        return NULL;
    }
    char buf[192];
    size_t n = strlen(dotted_path);
    if (n >= sizeof buf) {
        return NULL;
    }
    memcpy(buf, dotted_path, n + 1);
    const node *cur = world_tree;
    char *p = buf;
    while (*p) {
        char *dot = strchr(p, '.');
        if (dot) {
            *dot = 0;
        }
        if (cur->kind != N_OBJ) {
            return NULL;
        }
        cur = obj_get(cur, p);
        if (!cur) {
            return NULL;
        }
        if (!dot) {
            break;
        }
        p = dot + 1;
    }
    if (cur->kind != N_ARR || (size_t)idx >= cur->u.arr.n) {
        return NULL;
    }
    const node *el = cur->u.arr.items[idx];
    return (el && el->kind == N_STR) ? el->u.sv : NULL;
}

/* §23.F: ydarg stairs (G)ive-item dispatch. Walks
   `ydarg.stairs.items[]` looking for the entry whose `digit:` matches
   `digit`. Stores the entry's `special:` tag in *out_special and the
   `reject_msg:` short-id in *out_reject_msg, then returns 1. Returns
   0 if no entry has that digit (out-params untouched). The caller
   maps the tag to PS_* via inv_special_id_for_tag and concatenates
   the reject_msg under "ydarg.room.stairs.<msg>". */
int res_ydarg_stairs_item(int digit, const char **out_special,
                          const char **out_reject_msg) {
    if (!world_tree) {
        return 0;
    }
    const node *yd = obj_get(world_tree, "ydarg");
    if (!yd || yd->kind != N_OBJ) {
        return 0;
    }
    const node *stairs = obj_get(yd, "stairs");
    if (!stairs || stairs->kind != N_OBJ) {
        return 0;
    }
    const node *items = obj_get(stairs, "items");
    if (!items || items->kind != N_ARR) {
        return 0;
    }
    for (size_t i = 0; i < items->u.arr.n; i++) {
        const node *rec = items->u.arr.items[i];
        if (!rec || rec->kind != N_OBJ) {
            continue;
        }
        const node *d_n = obj_get(rec, "digit");
        if (!d_n || d_n->kind != N_INT || (int)d_n->u.iv != digit) {
            continue;
        }
        const node *sp_n = obj_get(rec, "special");
        const node *rj_n = obj_get(rec, "reject_msg");
        if (!sp_n || sp_n->kind != N_STR || !rj_n || rj_n->kind != N_STR) {
            return 0;
        }
        if (out_special) {
            *out_special = sp_n->u.sv;
        }
        if (out_reject_msg) {
            *out_reject_msg = rj_n->u.sv;
        }
        return 1;
    }
    return 0;
}

int32_t res_combat_int(const char *dotted_path) {
    char buf[192];
    int n = snprintf(buf, sizeof buf, "combat.%s", dotted_path ? dotted_path : "");
    if (n < 0 || (size_t)n >= sizeof buf) {
        return 0;
    }
    return res_int_path(buf);
}

int32_t res_xp_threshold(int level) {
    if (!world_tree || level < 0) {
        return 0;
    }
    const node *arr = obj_get(world_tree, "xp_thresholds");
    if (!arr || arr->kind != N_ARR) {
        return 0;
    }
    if ((size_t)level >= arr->u.arr.n) {
        return 0;
    }
    const node *v = arr->u.arr.items[level];
    return (v && v->kind == N_INT) ? (int32_t)v->u.iv : 0;
}

/* `pc_class` is the keyed-record enum carrying class data — id, display
   name, and the `levelup`/`spells`/`casts_in` payload that lives on each
   class record. There used to be a parallel `classes:` block; §22 folded
   it into the enum record so the symbol-vs-display split is the single
   source of truth. */
static const node *pc_class_node(const char *cls) {
    if (!world_tree || !cls) {
        return NULL;
    }
    const node *enums_n = obj_get(world_tree, "enums");
    if (!enums_n || enums_n->kind != N_OBJ) {
        return NULL;
    }
    const node *pc = obj_get(enums_n, "pc_class");
    if (!pc || pc->kind != N_OBJ) {
        return NULL;
    }
    const node *rec = obj_get(pc, cls);
    return (rec && rec->kind == N_OBJ) ? rec : NULL;
}

int32_t res_class_levelup(const char *cls, const char *stat) {
    if (!stat) {
        return 0;
    }
    const node *cls_n = pc_class_node(cls);
    if (!cls_n) {
        return 0;
    }
    const node *lvl_n = obj_get(cls_n, "levelup");
    if (!lvl_n || lvl_n->kind != N_OBJ) {
        return 0;
    }
    const node *v = obj_get(lvl_n, stat);
    return (v && v->kind == N_INT) ? (int32_t)v->u.iv : 0;
}

/* Look up the pc_class slug whose `id:` field matches `cl_id`. Returns
   NULL on unknown id — driv.c guarantees state.p[].cl is in 1..6 via
   the input-loop guard, so NULL means a corrupt save or out-of-range
   probe. */
static const char *pc_class_key_by_id(int cl_id) {
    if (!world_tree) {
        return NULL;
    }
    const node *enums_n = obj_get(world_tree, "enums");
    if (!enums_n || enums_n->kind != N_OBJ) {
        return NULL;
    }
    const node *pc = obj_get(enums_n, "pc_class");
    if (!pc || pc->kind != N_OBJ) {
        return NULL;
    }
    for (size_t i = 0; i < pc->u.obj.n; i++) {
        const node *rec = pc->u.obj.items[i].val;
        if (!rec || rec->kind != N_OBJ) {
            continue;
        }
        const node *id_n = obj_get(rec, "id");
        if (id_n && id_n->kind == N_INT && (int)id_n->u.iv == cl_id) {
            return pc->u.obj.items[i].key;
        }
    }
    return NULL;
}

/* createparty() class gate. Reads enums.pc_class.<slug>.min_stat and
   compares the named stat against `value`. Classes without a min_stat
   record (thief, ranger) pass unconditionally. On failure, *fail_text
   is set to the text id from the record (e.g. "driv.create.ws_low")
   and the function returns 0; otherwise returns 1. */
int res_class_min_stat_check(int cl_id, int ws, int wi, int ch,
                             const char **fail_text) {
    if (fail_text) {
        *fail_text = NULL;
    }
    const char *cls = pc_class_key_by_id(cl_id);
    if (!cls) {
        return 1;
    }
    const node *cls_n = pc_class_node(cls);
    if (!cls_n) {
        return 1;
    }
    const node *ms = obj_get(cls_n, "min_stat");
    if (!ms || ms->kind != N_OBJ) {
        return 1;
    }
    const node *name_n = obj_get(ms, "name");
    const node *val_n = obj_get(ms, "value");
    if (!name_n || name_n->kind != N_STR || !val_n || val_n->kind != N_INT) {
        return 1;
    }
    int got;
    if (strcmp(name_n->u.sv, "weapon_skill") == 0) {
        got = ws;
    } else if (strcmp(name_n->u.sv, "wisdom") == 0) {
        got = wi;
    } else if (strcmp(name_n->u.sv, "charisma") == 0) {
        got = ch;
    } else {
        return 1;
    }
    if (got >= (int)val_n->u.iv) {
        return 1;
    }
    if (fail_text) {
        const node *txt_n = obj_get(ms, "fail_text");
        if (txt_n && txt_n->kind == N_STR) {
            *fail_text = txt_n->u.sv;
        }
    }
    return 0;
}

/* Named-formula evaluator. `formulas.<name>` in world.json5 holds a
   single script DSL expression node (e.g. {add: [150, {mul: [150,
   {var:'y'}]}]}). The int bindings are converted to a ryl_bind list
   (script DSL atoi's them inside expr_var) and handed to
   play_formula_int. Fatal if the formula is missing — callers treat
   a named formula as a load-time contract. */
int32_t res_formula_int_n(const char *name, const res_binding *bindings, size_t n) {
    if (!world_tree || !name) {
        LOG_E("res", "res_formula_int: world not loaded");
        exit(1);
    }
    const node *fs = obj_get(world_tree, "formulas");
    if (!fs || fs->kind != N_OBJ) {
        LOG_E("res", "res_formula_int: missing top-level 'formulas' block");
        exit(1);
    }
    const node *body = obj_get(fs, name);
    if (!body) {
        LOG_E("res", "res_formula_int: unknown formula '%s'", name);
        exit(1);
    }
    /* §22 Lane A: formulas now run through the script DSL evaluator.
       Convert the int bindings to a ryl_bind list (script DSL atoi's
       integer-string values inside expr_var) and call the public
       formula entry. Cap of 16 inputs is comfortably above every
       caller's needs (encounter scaling tops out at 4 bindings). */
    enum { MAX_FORMULA_BINDINGS = 16 };
    if (n > MAX_FORMULA_BINDINGS) {
        LOG_E("res", "res_formula_int: '%s' too many bindings (%zu > %d)",
              name, n, MAX_FORMULA_BINDINGS);
        exit(1);
    }
    char buf[MAX_FORMULA_BINDINGS][16];
    ryl_bind binds[MAX_FORMULA_BINDINGS + 1] = {{0}};
    for (size_t i = 0; i < n; i++) {
        snprintf(buf[i], sizeof buf[i], "%d", (int)bindings[i].val);
        binds[i].name = bindings[i].name;
        binds[i].value = buf[i];
    }
    binds[n].name = NULL;
    binds[n].value = NULL;
    return play_formula_int(name, body, binds);
}

int32_t res_formula_int(const char *name, const char *var_name, int32_t val) {
    if (!var_name) {
        return res_formula_int_n(name, NULL, 0);
    }
    res_binding b = {var_name, val};
    return res_formula_int_n(name, &b, 1);
}

const ryl_monster *res_monster(int id) {
    if (id < 0 || id > RES_MAX_MONSTER_ID) {
        return NULL;
    }
    return monsters[id].name ? &monsters[id] : NULL;
}

int32_t res_monster_id(const char *sym) {
    if (!world_tree || !sym) {
        return -1;
    }
    const node *mon_n = obj_get(world_tree, "monsters");
    if (!mon_n || mon_n->kind != N_OBJ) {
        return -1;
    }
    for (size_t i = 0; i < mon_n->u.obj.n; i++) {
        if (strcmp(mon_n->u.obj.items[i].key, sym) != 0) {
            continue;
        }
        const node *rec = mon_n->u.obj.items[i].val;
        if (!rec || rec->kind != N_OBJ) {
            return -1;
        }
        const node *id_n = obj_get(rec, "id");
        if (!id_n || id_n->kind != N_INT) {
            return -1;
        }
        return (int32_t)id_n->u.iv;
    }
    return -1;
}

/* Pick a random monster id from the symbolic tier (e.g. "weak" / "wild" /
   "deep" as tagged in data/world/monsters/monsters.json5). Walks the
   monsters shard each call — the table is ~60 records and encounter()
   roll frequency is low enough that the cost is a rounding error.
   Fatal if the tier has no members: this always indicates a caller bug
   or a data edit that dropped every member of a still-referenced tier,
   and silent fallback (return a random non-tier monster) would bury it. */
int32_t res_monster_tier_pick(const char *tier) {
    if (!world_tree || !tier) {
        LOG_E("res", "res_monster_tier_pick: world not loaded / null tier");
        exit(1);
    }
    const node *mon_n = obj_get(world_tree, "monsters");
    if (!mon_n || mon_n->kind != N_OBJ) {
        LOG_E("res", "res_monster_tier_pick: monsters shard missing");
        exit(1);
    }
    int32_t ids[RES_MAX_MONSTER_ID + 1];
    size_t n = 0;
    for (size_t i = 0; i < mon_n->u.obj.n; i++) {
        const node *rec = mon_n->u.obj.items[i].val;
        if (!rec || rec->kind != N_OBJ) {
            continue;
        }
        const node *tier_n = obj_get(rec, "tier");
        if (!tier_n || tier_n->kind != N_STR) {
            continue;
        }
        if (strcmp(tier_n->u.sv, tier) != 0) {
            continue;
        }
        const node *id_n = obj_get(rec, "id");
        if (!id_n || id_n->kind != N_INT) {
            continue;
        }
        ids[n++] = (int32_t)id_n->u.iv;
    }
    if (n == 0) {
        LOG_E("res", "res_monster_tier_pick: empty tier '%s'", tier);
        exit(1);
    }
    return ids[ryl_random(0, (int32_t)n - 1)];
}

int res_monster_tier_exists(const char *tier) {
    if (!world_tree || !tier) {
        return 0;
    }
    const node *mon_n = obj_get(world_tree, "monsters");
    if (!mon_n || mon_n->kind != N_OBJ) {
        return 0;
    }
    for (size_t i = 0; i < mon_n->u.obj.n; i++) {
        const node *rec = mon_n->u.obj.items[i].val;
        if (!rec || rec->kind != N_OBJ) {
            continue;
        }
        const node *tier_n = obj_get(rec, "tier");
        if (tier_n && tier_n->kind == N_STR &&
            strcmp(tier_n->u.sv, tier) == 0) {
            return 1;
        }
    }
    return 0;
}

int res_dungeon_scenario_exists(const char *scenario) {
    if (!world_tree || !scenario) {
        return 0;
    }
    const node *d_n = obj_get(world_tree, "dungeons");
    if (!d_n || d_n->kind != N_OBJ) {
        return 0;
    }
    const node *sc_n = obj_get(d_n, "scenarios");
    if (!sc_n || sc_n->kind != N_OBJ) {
        return 0;
    }
    return obj_get(sc_n, scenario) != NULL;
}

void res_dungeon_apply_placements(const char *scenario, int32_t *lvl,
                                  size_t n_lvl) {
    if (!scenario) {
        return;
    }
    if (!world_tree || !lvl) {
        LOG_E("res", "res_dungeon_apply_placements: world not loaded");
        exit(1);
    }
    const node *d_n = obj_get(world_tree, "dungeons");
    if (!d_n || d_n->kind != N_OBJ) {
        LOG_E("res", "res_dungeon_apply_placements: dungeons shard missing");
        exit(1);
    }
    const node *sc_n = obj_get(d_n, "scenarios");
    if (!sc_n || sc_n->kind != N_OBJ) {
        LOG_E("res",
              "res_dungeon_apply_placements: dungeons.scenarios missing");
        exit(1);
    }
    const node *s = obj_get(sc_n, scenario);
    if (!s || s->kind != N_OBJ) {
        LOG_E("res", "res_dungeon_apply_placements: unknown scenario '%s'",
              scenario);
        exit(1);
    }
    const node *placements = obj_get(s, "placements");
    if (!placements || placements->kind != N_ARR) {
        LOG_E("res",
              "res_dungeon_apply_placements: scenario '%s' missing placements",
              scenario);
        exit(1);
    }
    for (size_t i = 0; i < placements->u.arr.n; i++) {
        const node *p = placements->u.arr.items[i];
        if (!p || p->kind != N_OBJ) {
            LOG_E("res",
                  "res_dungeon_apply_placements: scenario '%s' placements[%zu] "
                  "must be object",
                  scenario, i);
            exit(1);
        }
        const node *lvl_n = obj_get(p, "level");
        const node *enc_n = obj_get(p, "encounter");
        if (!lvl_n || lvl_n->kind != N_INT || !enc_n ||
            enc_n->kind != N_STR) {
            LOG_E("res",
                  "res_dungeon_apply_placements: scenario '%s' placements[%zu] "
                  "needs int level + string encounter",
                  scenario, i);
            exit(1);
        }
        int32_t L = (int32_t)lvl_n->u.iv;
        if (L < 1 || (size_t)L > n_lvl) {
            LOG_E("res",
                  "res_dungeon_apply_placements: scenario '%s' placement level "
                  "%d out of [1, %zu]",
                  scenario, (int)L, n_lvl);
            exit(1);
        }
        /* fold_dungeons has already verified the symbol resolves. */
        lvl[L] = (int32_t)res_enum_id("dungeon_encounter", enc_n->u.sv);
    }
}

int res_dungeon_encounter_recurring(const char *symbol) {
    if (!symbol || !symbol[0] || !world_tree) {
        return 0;
    }
    const node *d = obj_get(world_tree, "dungeons");
    if (!d || d->kind != N_OBJ) {
        return 0;
    }
    const node *r = obj_get(d, "recurring");
    if (!r || r->kind != N_ARR) {
        return 0;
    }
    for (size_t i = 0; i < r->u.arr.n; i++) {
        const node *e = r->u.arr.items[i];
        if (!e) {
            continue;
        }
        /* Plain-string entry: unconditionally recurring. */
        if (e->kind == N_STR) {
            if (strcmp(e->u.sv, symbol) == 0) {
                return 1;
            }
            continue;
        }
        /* Object entry: { symbol: 's', until_flag: { tag, value } }.
           Recurring as long as the named flag has not yet been set
           to the named value. Once the flag matches, the encounter
           "completes" — dispatcher resets lvl[cl] on this visit
           even though the symbol is in the recurring list. */
        if (e->kind != N_OBJ) {
            continue;
        }
        const node *sym_n = obj_get(e, "symbol");
        if (!sym_n || sym_n->kind != N_STR) {
            continue;
        }
        if (strcmp(sym_n->u.sv, symbol) != 0) {
            continue;
        }
        const node *uf = obj_get(e, "until_flag");
        if (!uf || uf->kind != N_OBJ) {
            return 1;
        }
        const node *tag_n = obj_get(uf, "tag");
        const node *val_n = obj_get(uf, "value");
        if (!tag_n || tag_n->kind != N_STR ||
            !val_n || val_n->kind != N_STR) {
            return 1;
        }
        int32_t slot = res_flag_slot(tag_n->u.sv);
        int32_t want = res_flag_value_id(tag_n->u.sv, val_n->u.sv);
        if (slot < 0 || want < 0) {
            return 1;
        }
        return pa_flag(slot) != want;
    }
    return 0;
}

const ryl_spell *res_spell(const char *class_name, int slot) {
    if (slot < 1 || slot > RES_MAX_SPELL_SLOT) {
        return NULL;
    }
    /* §24bis phase 3 cleanup: slug compare drops the pm_class_id /
       PC_* int round-trip. class_name is the lowercase enum key. */
    const ryl_spell *tab;
    if (class_name && strcmp(class_name, "priest") == 0) {
        tab = priest_spells;
    } else if (class_name && strcmp(class_name, "mage") == 0) {
        tab = mage_spells;
    } else {
        return NULL;
    }
    return tab[slot].name ? &tab[slot] : NULL;
}

const char *res_glyph(char terrain) {
    unsigned char g = (unsigned char)terrain;
    return glyph_tab[g] ? glyph_tab[g] : "  ";
}

int32_t res_color(char terrain) {
    unsigned char g = (unsigned char)terrain;
    return color_tab_set[g] ? color_tab[g] : color_default;
}

const char *res_terrain_class(char terrain) {
    unsigned char g = (unsigned char)terrain;
    return class_tab[g];
}

const char *res_pronoun(int gender, int xx) {
    if (gender < 1 || gender > 2 || xx < 1 || xx > 3) {
        return "";
    }
    return pronoun_tab[gender][xx] ? pronoun_tab[gender][xx] : "";
}

const char *res_town_name(int twn) {
    if (twn < 0 || twn >= (int)n_towns || !towns[twn].name) {
        return "";
    }
    return towns[twn].name;
}

int32_t res_town_x(int twn) {
    if (twn < 0 || twn >= (int)n_towns || !towns[twn].name) {
        return 0;
    }
    return towns[twn].x;
}

int32_t res_town_y(int twn) {
    if (twn < 0 || twn >= (int)n_towns || !towns[twn].name) {
        return 0;
    }
    return towns[twn].y;
}

int res_town_count(void) {
    return (int)n_towns;
}

int32_t res_town_at(int x, int y) {
    for_each_town(i) {
        if (towns[i].name && towns[i].x == x && towns[i].y == y) {
            return i;
        }
    }
    return WILDERNESS_TOWN;
}

int32_t res_town_tax(int twn) {
    if (twn < 0 || twn >= (int)n_towns || !towns[twn].name) {
        return 0;
    }
    return towns[twn].tax;
}

int32_t res_town_has_church(int twn) {
    if (twn < 0 || twn >= (int)n_towns || !towns[twn].name) {
        return 0;
    }
    return towns[twn].has_church;
}

int32_t res_town_is_city(int twn) {
    if (twn < 0 || twn >= (int)n_towns || !towns[twn].name) {
        return 0;
    }
    return towns[twn].is_city;
}

int res_building_hours(const char *name, int *opens_at, int *closes_at) {
    if (!name) {
        return 0;
    }
    for (size_t i = 0; i < n_buildings; i++) {
        if (strcmp(buildings_tab[i].name, name) == 0) {
            if (opens_at) {
                *opens_at = (int)buildings_tab[i].opens_at;
            }
            if (closes_at) {
                *closes_at = (int)buildings_tab[i].closes_at;
            }
            return 1;
        }
    }
    return 0;
}

/* Read an optional int field with a default. */
static int32_t rn_int(const node *obj, const char *key, int32_t dflt) {
    const node *n = obj_get(obj, key);
    return (n && n->kind == N_INT) ? n->u.iv : dflt;
}

/* Count of NPC records declared in the npcs subtree (slots
   0..count-1, post-§25.B step 4). Cached on first call after world
   load — the writer iterates every slot on save, so the linear scan
   would otherwise repeat per call. The cache is invalidated by
   res_shutdown when world_tree is freed. */
static int npc_slot_max_cache = -1;

static void npc_slot_max_invalidate(void) {
    npc_slot_max_cache = -1;
}

static int npc_slot_max(void) {
    if (npc_slot_max_cache >= 0) {
        return npc_slot_max_cache;
    }
    if (!world_tree) {
        return 0;
    }
    const node *npcs = obj_get(world_tree, "npcs");
    if (!npcs || npcs->kind != N_OBJ) {
        return 0;
    }
    int max_id = -1;
    int count = 0;
    for (size_t i = 0; i < npcs->u.obj.n; i++) {
        const node *rec = npcs->u.obj.items[i].val;
        if (!rec || rec->kind != N_OBJ) {
            continue;
        }
        const node *id_n = obj_get(rec, "id");
        if (id_n && id_n->kind == N_INT) {
            count++;
            if ((int)id_n->u.iv > max_id) {
                max_id = (int)id_n->u.iv;
            }
        }
    }
    /* Ids must form 0..count-1 contiguous (post-§25.B step 4). */
    if (count > 0 && max_id != count - 1) {
        LOG_E("realm",
              "world.json5 npcs ids must be 0..count-1 contiguous "
              "(max=%d, count=%d)",
              max_id, count);
        exit(1);
    }
    if (count > NPC_SLOT_HARD_MAX) {
        LOG_E("realm",
              "world.json5 npcs count=%d exceeds NPC_SLOT_HARD_MAX (%d); "
              "bump state.h or trim ids",
              count, NPC_SLOT_HARD_MAX);
        exit(1);
    }
    npc_slot_max_cache = count;
    return count;
}

int res_npc_slot_max(void) {
    return npc_slot_max();
}

/* Symbolic NPC key (e.g. "gorm", "harry") -> state.npc[] slot. Walks
   the npcs subtree directly and reads the entry's `id:` field. Fatal
   on miss so a C-side typo or a rename of the NPC in world.json5
   doesn't silently degrade to slot 0 (the wilderness sentinel).

   Prefer res_npc_role at mission / rumour / quest call sites so the
   specific character's symbol doesn't leak into C; use res_npc_slot
   only when an NPC's shard key is the actual identity (e.g. in the
   realizer, or in tests). */
int32_t res_npc_slot(const char *sym) {
    if (!world_tree || !sym) {
        LOG_E("res", "res_npc_slot: world not loaded / null name");
        exit(1);
    }
    const node *npcs = obj_get(world_tree, "npcs");
    if (!npcs || npcs->kind != N_OBJ) {
        LOG_E("res", "res_npc_slot: npcs subtree missing");
        exit(1);
    }
    const node *rec = obj_get(npcs, sym);
    if (!rec || rec->kind != N_OBJ) {
        LOG_E("res", "res_npc_slot: unknown NPC '%s'", sym);
        exit(1);
    }
    const node *id_n = obj_get(rec, "id");
    if (!id_n || id_n->kind != N_INT) {
        LOG_E("res", "res_npc_slot: '%s' missing 'id'", sym);
        exit(1);
    }
    return (int32_t)id_n->u.iv;
}

/* Slot-keyed walk over the npcs subtree. Returns the matching record,
   or NULL when no entry claims the slot. Both res_npc_sym and
   res_npc_name are thin shells around this. Slot 0 is a valid NPC
   slot (post-§25.B step 4); negative slots are the only invalid
   range. */
static const node *npc_record_for_slot(int32_t slot) {
    if (!world_tree || slot < 0) {
        return NULL;
    }
    const node *npcs = obj_get(world_tree, "npcs");
    if (!npcs || npcs->kind != N_OBJ) {
        return NULL;
    }
    for (size_t i = 0; i < npcs->u.obj.n; i++) {
        const node *rec = npcs->u.obj.items[i].val;
        if (!rec || rec->kind != N_OBJ) {
            continue;
        }
        const node *id_n = obj_get(rec, "id");
        if (id_n && id_n->kind == N_INT && id_n->u.iv == slot) {
            return rec;
        }
    }
    return NULL;
}

/* Inverse of res_npc_slot: state.npc[] slot id -> the npcs-subtree key
   (lowercase symbol) for that entry. Returns NULL when no record claims
   the slot — used by the persistent-save writer, which iterates slots
   0..N-1 regardless. */
const char *res_npc_sym(int32_t slot) {
    if (!world_tree || slot < 0) {
        return NULL;
    }
    const node *npcs = obj_get(world_tree, "npcs");
    if (!npcs || npcs->kind != N_OBJ) {
        return NULL;
    }
    for (size_t i = 0; i < npcs->u.obj.n; i++) {
        const node *rec = npcs->u.obj.items[i].val;
        if (!rec || rec->kind != N_OBJ) {
            continue;
        }
        const node *id_n = obj_get(rec, "id");
        if (id_n && id_n->kind == N_INT && id_n->u.iv == slot) {
            return npcs->u.obj.items[i].key;
        }
    }
    return NULL;
}

/* Display name for an NPC slot. Replaces the legacy res_enum("name",
   slot) lookup; the source of truth is the per-record `.name` field
   on the npcs subtree, not a parallel positional array. Returns "" on
   miss so callers that format directly into tx() see an empty string
   instead of a NULL deref. */
const char *res_npc_name(int32_t slot) {
    const node *rec = npc_record_for_slot(slot);
    if (!rec) {
        return "";
    }
    const node *name_n = obj_get(rec, "name");
    return (name_n && name_n->kind == N_STR) ? name_n->u.sv : "";
}

/* First NPC matching (job_sym, twn) — display name. The town greeter
   binds in the inn / tavern scripts use this to pick the right keeper
   (innkeeper / tavernkeeper) for state.twn without a parallel
   positional array. Returns "" when no NPC matches; both the inn and
   tavern populations have one NPC per town in the current data. */
const char *res_npc_in_town(int32_t twn, const char *job_sym) {
    if (!world_tree || !job_sym) {
        return "";
    }
    int target_job = res_enum_id("job", job_sym);
    if (target_job < 0) {
        return "";
    }
    const node *npcs = obj_get(world_tree, "npcs");
    if (!npcs || npcs->kind != N_OBJ) {
        return "";
    }
    for (size_t i = 0; i < npcs->u.obj.n; i++) {
        const node *rec = npcs->u.obj.items[i].val;
        if (!rec || rec->kind != N_OBJ) {
            continue;
        }
        const node *job_n = obj_get(rec, "job");
        const node *twn_n = obj_get(rec, "towne");
        if (!job_n || job_n->kind != N_STR) {
            continue;
        }
        if (!twn_n || twn_n->kind != N_STR) {
            continue;
        }
        if (res_enum_id("job", job_n->u.sv) != target_job) {
            continue;
        }
        /* `twn_n` is the world.json5 string ('Thord' / 'wilderness' /
           …); 'wilderness' is matched as a literal (post-§25.B step
           3b/C4 it is no longer an enums.towne entry), real town
           names route through res_enum_id which shares the
           enums.towne ↔ towns[] index space. */
        if (strcmp(twn_n->u.sv, "wilderness") == 0) {
            if (twn != WILDERNESS_TOWN) {
                continue;
            }
        } else {
            int twn_id = res_enum_id("towne", twn_n->u.sv);
            if (twn_id < 0 || twn_id != twn) {
                continue;
            }
        }
        const node *name_n = obj_get(rec, "name");
        return (name_n && name_n->kind == N_STR) ? name_n->u.sv : "";
    }
    return "";
}

/* Quest-role -> NPC slot indirection. The role name (e.g.
   "bandits_quest_giver", "master_thief") is C-stable; the binding
   to a specific character's shard key lives in world.json5's
   npc_roles: subtree. Renaming a character, or reassigning a quest
   to a different character, is a pure data edit.

   Fatal on unknown role (C typo or role absent from data), fatal on
   role pointing at an NPC that isn't in npcs: (dangling binding).
   Both conditions are load-time bugs we want surfaced loudly. */
void npc_loc_str(npc_t n, char *dst, size_t dst_sz) {
    if (npc_in_wilderness(n)) {
        snprintf(dst, dst_sz, "at %dx%d", n.p->x, n.p->y);
    } else {
        snprintf(dst, dst_sz, "in %s", res_town_name(n.p->towne));
    }
}

int32_t res_npc_role(const char *role) {
    if (!world_tree || !role) {
        LOG_E("res", "res_npc_role: world not loaded / null role");
        exit(1);
    }
    const node *roles = obj_get(world_tree, "npc_roles");
    if (!roles || roles->kind != N_OBJ) {
        LOG_E("res", "res_npc_role: npc_roles subtree missing");
        exit(1);
    }
    const node *binding = obj_get(roles, role);
    if (!binding || binding->kind != N_STR) {
        LOG_E("res", "res_npc_role: unknown role '%s'", role);
        exit(1);
    }
    return res_npc_slot(binding->u.sv);
}

const char *res_role_for_slot(int32_t slot) {
    if (!world_tree) {
        return NULL;
    }
    const node *roles = obj_get(world_tree, "npc_roles");
    if (!roles || roles->kind != N_OBJ) {
        return NULL;
    }
    for (size_t i = 0; i < roles->u.obj.n; i++) {
        const node *binding = roles->u.obj.items[i].val;
        if (!binding || binding->kind != N_STR) {
            continue;
        }
        if (res_npc_slot(binding->u.sv) == slot) {
            return roles->u.obj.items[i].key;
        }
    }
    return NULL;
}

int res_inventory_slot_max(void) {
    return (int)inventory_slot_max;
}

int res_party_slot_max(void) {
    return (int)party_slot_max;
}

/* Map capacity dims. fold_limits validates the world's `map.rows_max`
   / `map.cols_max` against MAP_*_HARD_MAX and stashes them here so
   wld.c access (per-tile, hot path) doesn't re-walk the world tree
   on every read. NULL world tree falls back to MAP_*_HARD_MAX so
   res-free smoke tests still work. Reset by reset_limits. */
static int32_t map_rows_max = MAP_ROWS_HARD_MAX;
static int32_t map_cols_max = MAP_COLS_HARD_MAX;

int res_map_rows_max(void) {
    return (int)map_rows_max;
}

int res_map_cols_max(void) {
    return (int)map_cols_max;
}

/* Random-location slot caps. wld.h's WLD_LOCATION_SLOT_MAX (15) and
   WLD_RANDOM_LOCATION_MAX (9) are the defaults; world.json5 can
   override via `limits.location_slot_max` / `limits.random_location_max`.
   state.dx / state.dy are heap-allocated off location_slot_max at
   world-load. */
static int32_t location_slot_max = 15;
static int32_t random_location_max = 9;

int res_location_slot_max(void) {
    return (int)location_slot_max;
}

int res_random_location_max(void) {
    return (int)random_location_max;
}

int res_party_member_max(void) {
    /* Test-harness override: RYL_PARTY_MEMBER_MAX is read once and
       cached so res_party_member_max() stays cheap. Lets a §19.3.4
       smoke fixture exercise N != 4 without authoring a separate
       world.json5 — the env var wins over the limits: knob and the
       compile-time default. Only honoured for values >= 1 (bad
       values fall through to the data/compile-time chain). */
    static int override = -2; /* -2 = unchecked, -1 = no override */
    if (override == -2) {
        override = -1;
        const char *env = rcfg_get("RYL_PARTY_MEMBER_MAX");
        if (env && *env) {
            char *end;
            long v = strtol(env, &end, 10);
            if (end != env && v >= 1 && v <= 100) {
                override = (int)v;
            }
        }
    }
    if (override >= 1) {
        return override;
    }
    return (int)party_member_max;
}

/* Read an optional enum field (string or int) with a default. */
static int32_t rn_enum(const node *obj, const char *key, const char *enum_id, int32_t dflt) {
    const node *n = obj_get(obj, key);
    if (!n) {
        return dflt;
    }
    if (n->kind == N_INT) {
        return n->u.iv;
    }
    if (n->kind == N_STR) {
        int id = res_enum_id(enum_id, n->u.sv);
        return id >= 0 ? id : dflt;
    }
    return dflt;
}

void res_realize_npcs(void) {
    tx("%cSetting up new campaign. Please wait.", 12);
    LOG_A("realm", "A new campaign starts. Baron Ydarg awaits...");

    const node *root = world_tree;
    const node *npcs = root ? obj_get(root, "npcs") : NULL;
    if (!npcs || npcs->kind != N_OBJ) {
        /* Without a spec there's nothing to realize. Keep state.npc[]
           zeroed from static init; step 2 reader will see empty slots. */
        LOG_W("realm", "no npcs subtree in world tree; campaign will start "
                       "with no realized NPCs");
        tx("%c", 12);
        return;
    }

    /* Capacity-capped draw (port-plan §15.4). Bins = wilderness (0) +
       each declared town (1..n_towns) = T. N is the count of random
       entries in the spec (those without a declared `towne`). cap =
       ceil(N/T) + npc_slack guarantees total capacity T*cap >= N, so
       the linear probe below always finds a free bin. With today's
       N=23, T=5, slack=1 => cap=6 per bin. Both T and slot_count come
       from the folded world tree — towns: and the npcs subtree's
       record count respectively. */
    int T = 1 + (int)n_towns;
    int slot_count = npc_slot_max();
    int N = 0;
    int fixed_count = 0;
    for (size_t i = 0; i < npcs->u.obj.n; i++) {
        const node *rec = npcs->u.obj.items[i].val;
        if (!rec || rec->kind != N_OBJ) {
            continue;
        }
        int slot = rn_int(rec, "id", -1);
        if (slot < 0 || slot >= slot_count) {
            continue;
        }
        if (!obj_get(rec, "towne")) {
            N++;
        } else {
            fixed_count++;
        }
    }
    int cap = (N + T - 1) / T + npc_slack;
    LOG_I("realm",
          "realizing %d NPC(s): %d fixed + %d random across %d bin(s) "
          "(capacity %d/bin, slot_count=%d)",
          N + fixed_count, fixed_count, N, T, cap, slot_count);
    /* Sized to the static cap (T <= TOWN_SLOT_HARD_MAX); the active
       part is bins 0..T-1. Zero-initialized so unreached bins stay
       0 even when fewer towns than the cap are declared. */
    int counts[TOWN_SLOT_HARD_MAX] = {0};

    for (size_t i = 0; i < npcs->u.obj.n; i++) {
        const node *rec = npcs->u.obj.items[i].val;
        if (!rec || rec->kind != N_OBJ) {
            continue;
        }
        int slot = rn_int(rec, "id", -1);
        if (slot < 0 || slot >= slot_count) {
            continue;
        }
        struct person *n = &state.npc[slot];
        n->nr = slot;
        /* §24bis phase 3 job axis: storage is slug pointer; rn_enum
           returns the int slot, canonicalize via res_enum_sym to the
           stable keys[] pointer. 0 → NULL (unset sentinel). */
        {
            int parsed = rn_enum(rec, "job", "job", 0);
            if (parsed <= 0) {
                n->job = NULL;
            } else {
                const char *s = res_enum_sym("job", parsed);
                n->job = (s && s[0]) ? s : NULL;
            }
        }
        n->gender = (unsigned char)rn_enum(rec, "gender", "gender", 1);

        const node *towne_node = obj_get(rec, "towne");
        if (towne_node) {
            /* FIXED: declared slot. Apply job/towne/building/x/y verbatim.
               'wilderness' is recognized as a literal string and bridges
               to the runtime sentinel WILDERNESS_TOWN — it is no longer
               an enums.towne entry (dropped in §25.B step 3b/C4); real
               town strings share the enums.towne ↔ towns[] index space
               directly so the lookup returns the runtime slot id with
               no offset. */
            if (towne_node->kind == N_STR &&
                strcmp(towne_node->u.sv, "wilderness") == 0) {
                n->towne = WILDERNESS_TOWN;
            } else {
                n->towne = rn_enum(rec, "towne", "towne", WILDERNESS_TOWN);
            }
            n->building = rn_enum(rec, "building", "building", 0);
            n->x = rn_int(rec, "x", 0);
            n->y = rn_int(rec, "y", 0);
        } else {
            /* RANDOM: realize placement now. Matches the legacy
               place_npcs() roll pattern but with a capacity cap on the
               towne draw — roll, then linear-probe to the next eligible
               bin on overflow. Building (1..2) and wilderness (x,y)
               rolls are untouched; the collision loop's accept predicate
               (ut > 1) is preserved verbatim from person.c:64-76.
               §24.A: cosmetic per-NPC delay shortened from 600ms to
               40ms. The original spacing was hiding Amiga disk loads;
               the modern build has nothing to hide, but a brief tick
               between dots keeps the visual pacing recognizable
               (~1s total over ~23 random NPCs vs the prior ~14s).
               The bin draw range and the linear probe stay 0..T-1
               (bin 0 = wilderness, bins 1..n_towns = town slots
               0..n_towns-1) so the RNG stream advances identically to
               the pre-§25.B step 3a sequence; bin 0 maps to the
               WILDERNESS_TOWN sentinel and bin N>=1 to towns[] slot
               N-1 directly. The bin numbering is independent of the
               enums.towne index space (which dropped its wilderness
               entry in step 3b/C4); the bin draw is purely a
               capacity-balancing roll. The collision loop walks
               state.npc[0..slot] (post-§25.B step 4 NPC axis flip)
               so a 0-indexed first-realized NPC is included; the
               self-match at x=slot keeps the ut > 1 predicate's
               meaning verbatim. */
            ryl_delay(40);
            int twn = ryl_random(0, T - 1);
            while (counts[twn] >= cap) {
                twn = (twn + 1) % T;
            }
            counts[twn]++;
            n->towne = (twn == 0) ? WILDERNESS_TOWN : twn - 1;
            if (!npc_in_wilderness(npc_at_slot(slot))) {
                n->building = ryl_random(0, 1) + 1;
                n->x = 0;
                n->y = 0;
            } else {
                n->building = 0;
                while (1) {
                    n->x = ryl_random(8, 57);
                    n->y = ryl_random(8, 27);
                    if (n->x == 48) {
                        n->x++;
                    }
                    int ut = 0;
                    for (int x = 0; x < slot + 1; x++) {
                        if (state.npc[x].x == n->x && state.npc[x].y == n->y) {
                            ut++;
                        }
                    }
                    char tile = wld_tile_at(n->x, n->y);
                    if (!wld_tile_is_settlement(tile) && !wld_tile_is_impassable(tile)) {
                        ut++;
                    }
                    if (ut > 1) {
                        break;
                    }
                }
            }
            tx(".");
            const node *name = obj_get(rec, "name");
            const char *who = (name && name->kind == N_STR) ? name->u.sv
                                                            : npcs->u.obj.items[i].key;
            LOG_I("realm", "  realized random NPC '%s' -> slot %d in %s",
                  who, slot,
                  n->towne == WILDERNESS_TOWN ? "wilderness"
                                              : towns[n->towne].name);
        }
    }
    tx("%c", 12);

    /* Zero every NPC's visited[] grid — fresh realm means no party
       has met anyone yet. The caller (driv.c init()) follows up with
       write_current_world() to persist the realized state. */
    {
        size_t visited_n = (size_t)res_party_slot_max();
        for_each_npc(i) {
            memset(state.npc[i].visited, 0, visited_n);
        }
    }
    LOG_I("realm", "campaign realized");
}

/* -------- flags subtree -------- */

static void fold_flags(const node *flags_node, const char *path) {
    if (!flags_node) {
        return;
    }
    if (flags_node->kind != N_OBJ) {
        res_die(path, flags_node->line, "flags: expected object");
    }
    for (size_t i = 0; i < flags_node->u.obj.n; i++) {
        const char *id = flags_node->u.obj.items[i].key;
        const node *rec = flags_node->u.obj.items[i].val;
        if (rec->kind != N_OBJ) {
            res_die(path, rec->line, "flags: entry must be object");
        }
        const node *slot_n = obj_get(rec, "slot");
        if (!slot_n || slot_n->kind != N_INT) {
            res_die(path, rec->line, "flags: missing 'slot'");
        }
        const node *scope_n = obj_get(rec, "scope");
        int32_t scope = 0;
        if (scope_n) {
            if (scope_n->kind != N_STR) {
                res_die(path, scope_n->line, "flags: scope must be string");
            }
            if (strcmp(scope_n->u.sv, "party") == 0) {
                scope = 0;
            } else if (strcmp(scope_n->u.sv, "encounter") == 0) {
                scope = 1;
            } else {
                res_die(path, scope_n->line, "flags: scope must be 'party' or 'encounter'");
            }
        }
        const node *vals_n = obj_get(rec, "values");
        if (!vals_n || vals_n->kind != N_ARR) {
            res_die(path, rec->line, "flags: missing 'values' array");
        }
        flag_rec *grown = realloc(flags_tab, (n_flags_tab + 1) * sizeof *flags_tab);
        if (!grown) {
            res_die(path, rec->line, "oom");
        }
        flags_tab = grown;
        flag_rec *r = &flags_tab[n_flags_tab++];
        r->id = strdup(id);
        r->slot = slot_n->u.iv;
        r->scope = scope;
        r->n_values = vals_n->u.arr.n;
        r->values = malloc(r->n_values * sizeof *r->values);
        if (!r->values) {
            res_die(path, rec->line, "oom");
        }
        for (size_t j = 0; j < r->n_values; j++) {
            const node *v = vals_n->u.arr.items[j];
            if (v->kind != N_STR) {
                res_die(path, v->line, "flags: value must be string");
            }
            r->values[j] = strdup(v->u.sv);
        }
    }
}

/* -------- items subtree (docs/dsl.md §2.2) --------
   The items tree is sectioned by kind (weapons/armour/gear/potions/
   treasures/magic_items); the sections are an authoring convenience,
   the legacy ryl_item[0..111] table is still keyed by flat `id`.

   For each record: id, name, cost are read and folded into the legacy
   table. `damage` (weapons) and `ac` (armour) both land in the single
   ryl_item.damage slot — obdamage() at the call sites reads the same
   field for both roles. `wieldable_by` is rewritten in place from an
   array of class-name strings to an integer class-id bitmask so the
   evaluator can dispatch without strcmp at runtime; fatal on unknown
   class. Other fields (kind / slot / effect / ...) parse into the node
   tree for the evaluator but are inert this commit. */
static void fold_wieldable_by(node *rec, const char *path) {
    for (size_t j = 0; j < rec->u.obj.n; j++) {
        if (strcmp(rec->u.obj.items[j].key, "wieldable_by") != 0) {
            continue;
        }
        node *val = rec->u.obj.items[j].val;
        if (!val || val->kind != N_ARR) {
            res_die(path, val ? val->line : rec->line, "wieldable_by: expected array");
        }
        int32_t mask = 0;
        for (size_t k = 0; k < val->u.arr.n; k++) {
            node *el = val->u.arr.items[k];
            if (!el || el->kind != N_STR) {
                res_die(path, val->line, "wieldable_by: element must be string");
            }
            /* §24bis phase 3 cleanup: lookup the slug in the
               pc_class enum directly; no need for the pm_class_id
               name→PC_* mapper. */
            int cid = res_enum_id("pc_class", el->u.sv);
            if (cid < 0) {
                res_die(path, el->line, "wieldable_by: unknown class");
            }
            mask |= (int32_t)(1u << (unsigned)cid);
        }
        node *repl = node_new(N_INT, val->line);
        repl->u.iv = mask;
        node_free(val);
        rec->u.obj.items[j].val = repl;
        return;
    }
}

static void fold_items_section(const node *section, const char *section_key,
                               const char *path) {
    if (section->kind != N_OBJ) {
        res_die(path, section->line, "items: section must be object");
    }
    for (size_t i = 0; i < section->u.obj.n; i++) {
        const char *sym = section->u.obj.items[i].key;
        node *rec = section->u.obj.items[i].val;
        if (rec->kind != N_OBJ) {
            res_die(path, rec->line, "items: entry must be object");
        }
        const node *id_n = obj_get(rec, "id");
        if (!id_n || id_n->kind != N_INT) {
            res_die(path, rec->line, "items: missing 'id'");
        }
        int32_t id = id_n->u.iv;
        if (id < 0 || id > RES_MAX_ITEM_ID) {
            res_die(path, id_n->line, "items: id out of range");
        }
        ryl_item *it = &items[id];
        item_kind[id] = section_key;
        free((void *)it->name);
        const node *name_n = obj_get(rec, "name");
        it->name = strdup((name_n && name_n->kind == N_STR) ? name_n->u.sv : sym);
        const node *cost_n = obj_get(rec, "cost");
        it->cost = (cost_n && cost_n->kind == N_INT) ? cost_n->u.iv : 0;
        const node *dmg_n = obj_get(rec, "damage");
        const node *ac_n = obj_get(rec, "ac");
        if (dmg_n && dmg_n->kind == N_INT) {
            it->damage = dmg_n->u.iv;
        } else if (ac_n && ac_n->kind == N_INT) {
            it->damage = ac_n->u.iv;
        } else {
            it->damage = 0;
        }
        it->effect_kind = NULL;
        it->effect_amount = 0;
        /* §20 S4 consumable-effect spec. Object-form `effect:` is the
           apply_potion dispatch payload; array-form `effect:` is
           magic_items' legacy DSL expression and out of scope here.
           Distinguish by node kind so the two coexist. */
        const node *effect_n = obj_get(rec, "effect");
        if (effect_n && effect_n->kind == N_OBJ) {
            const node *kind_n = obj_get(effect_n, "kind");
            if (!kind_n || kind_n->kind != N_STR) {
                res_die(path, effect_n->line,
                        "items: effect.kind must be string");
            }
            it->effect_kind = kind_n->u.sv;
            const node *amt_n = obj_get(effect_n, "amount");
            it->effect_amount =
                (amt_n && amt_n->kind == N_INT) ? amt_n->u.iv : 0;
        }
        fold_wieldable_by(rec, path);
    }
}

static void fold_items(const node *items_node, const char *path) {
    if (!items_node) {
        return;
    }
    if (items_node->kind != N_OBJ) {
        res_die(path, items_node->line, "items: expected object");
    }
    for (size_t s = 0; s < items_node->u.obj.n; s++) {
        fold_items_section(items_node->u.obj.items[s].val,
                           items_node->u.obj.items[s].key, path);
    }
}

/* -------- monsters subtree (docs/dsl.md §2.2) --------
   Symbolic key in the shard is author-facing; `id` (map.dat's monster
   index) is the runtime key. Missing optional fields default to 0. */
static void fold_monsters(const node *monsters_node, const char *path) {
    if (!monsters_node) {
        return;
    }
    if (monsters_node->kind != N_OBJ) {
        res_die(path, monsters_node->line, "monsters: expected object");
    }
    for (size_t i = 0; i < monsters_node->u.obj.n; i++) {
        const node *rec = monsters_node->u.obj.items[i].val;
        if (rec->kind != N_OBJ) {
            res_die(path, rec->line, "monsters: entry must be object");
        }
        const node *id_n = obj_get(rec, "id");
        if (!id_n || id_n->kind != N_INT) {
            res_die(path, rec->line, "monsters: missing 'id'");
        }
        int32_t id = id_n->u.iv;
        if (id < 0 || id > RES_MAX_MONSTER_ID) {
            res_die(path, id_n->line, "monsters: id out of range");
        }
        ryl_monster *m = &monsters[id];
        free((void *)m->name);
        const node *name_n = obj_get(rec, "name");
        m->name = strdup((name_n && name_n->kind == N_STR) ? name_n->u.sv : "");
        const node *n1 = obj_get(rec, "n1");
        m->n1 = (n1 && n1->kind == N_INT) ? n1->u.iv : 0;
        const node *n2 = obj_get(rec, "n2");
        m->n2 = (n2 && n2->kind == N_INT) ? n2->u.iv : 0;
        const node *h1 = obj_get(rec, "h1");
        m->h1 = (h1 && h1->kind == N_INT) ? h1->u.iv : 0;
        const node *h2 = obj_get(rec, "h2");
        m->h2 = (h2 && h2->kind == N_INT) ? h2->u.iv : 0;
        const node *ws = obj_get(rec, "ws");
        m->ws = (ws && ws->kind == N_INT) ? ws->u.iv : 0;
        const node *sp = obj_get(rec, "sp");
        m->sp = (sp && sp->kind == N_INT) ? sp->u.iv : 0;
        const node *im = obj_get(rec, "im");
        m->im = (im && im->kind == N_INT) ? im->u.iv : 0;
        const node *n2d = obj_get(rec, "n2_lv_div");
        m->n2_lv_div = (n2d && n2d->kind == N_INT) ? n2d->u.iv : 0;
    }
}

/* -------- texts subtree (docs/dsl.md §2.2) --------
   Hierarchical: every nested object's key becomes one dotted segment of
   the final text id. Leaves are either strings (verbatim) or arrays of
   strings (joined with \r\n, trailing \r\n appended). Emitted records
   are sorted once after all world folds complete, enabling bsearch. */
static void emit_text(const char *id, const char *text) {
    text_rec *grown = realloc(texts, (n_texts + 1) * sizeof *texts);
    if (!grown) {
        LOG_E("res", "oom");
        exit(1);
    }
    texts = grown;
    texts[n_texts].id = strdup(id);
    texts[n_texts].text = strdup(text);
    n_texts++;
    texts_sorted = 0;
}

static char *join_lines_crlf(const node *arr, const char *path) {
    size_t total = 0;
    for (size_t i = 0; i < arr->u.arr.n; i++) {
        const node *el = arr->u.arr.items[i];
        if (el->kind != N_STR) {
            res_die(path, el->line, "texts: line must be string");
        }
        total += strlen(el->u.sv) + 2;
    }
    char *out = malloc(total + 1);
    if (!out) {
        res_die(path, arr->line, "oom");
    }
    size_t o = 0;
    for (size_t i = 0; i < arr->u.arr.n; i++) {
        const char *s = arr->u.arr.items[i]->u.sv;
        size_t n = strlen(s);
        memcpy(out + o, s, n);
        o += n;
        out[o++] = '\r';
        out[o++] = '\n';
    }
    out[o] = 0;
    return out;
}

static void fold_texts_walk(const node *n, const char *prefix, const char *path) {
    if (!n) {
        return;
    }
    if (n->kind == N_STR) {
        emit_text(prefix, n->u.sv);
        return;
    }
    if (n->kind == N_ARR) {
        char *joined = join_lines_crlf(n, path);
        emit_text(prefix, joined);
        free(joined);
        return;
    }
    if (n->kind == N_OBJ) {
        char buf[256];
        for (size_t i = 0; i < n->u.obj.n; i++) {
            const char *key = n->u.obj.items[i].key;
            if (!prefix[0]) {
                snprintf(buf, sizeof buf, "%s", key);
            } else {
                snprintf(buf, sizeof buf, "%s.%s", prefix, key);
            }
            fold_texts_walk(n->u.obj.items[i].val, buf, path);
        }
        return;
    }
    res_die(path, n->line, "texts: unexpected node kind");
}

static void fold_texts(const node *texts_node, const char *path) {
    if (!texts_node) {
        return;
    }
    if (texts_node->kind != N_OBJ) {
        res_die(path, texts_node->line, "texts: expected object");
    }
    fold_texts_walk(texts_node, "", path);
}

/* -------- classes + spells subtree (docs/dsl.md §2.2) --------
   The spells tree is nested by class (spells.mage / spells.priest); the
   dotted ids in classes.<cls>.spells (e.g. "mage.frost") give the full
   symbolic name, but fold only reads the local part after the dot when
   indexing into the per-class subtree.

   Each spell folds into the legacy ryl_spell table so the cast menu
   can still render name/base_cost. Behaviour dispatch goes through
   res_spell_node: every spell carries a `do:` body that battle.c /
   subs.c hand to play_spell_body. The legacy `legacy_effect` /
   SE_* switch retired with §22 Lane A. */
static void fold_spells(const node *classes_node, const node *spells_node, const char *path) {
    if (!classes_node || !spells_node) {
        return;
    }
    if (classes_node->kind != N_OBJ) {
        res_die(path, classes_node->line, "classes: expected object");
    }
    if (spells_node->kind != N_OBJ) {
        res_die(path, spells_node->line, "spells: expected object");
    }
    for (size_t c = 0; c < classes_node->u.obj.n; c++) {
        const char *cname = classes_node->u.obj.items[c].key;
        const node *cls = classes_node->u.obj.items[c].val;
        if (cls->kind != N_OBJ) {
            continue;
        }
        const node *list = obj_get(cls, "spells");
        if (!list || list->kind != N_ARR) {
            continue;
        }
        /* §24bis phase 3 cleanup: slug compare. */
        ryl_spell *tab = NULL;
        if (strcmp(cname, "priest") == 0) {
            tab = priest_spells;
        } else if (strcmp(cname, "mage") == 0) {
            tab = mage_spells;
        } else {
            continue;
        }
        const node *sub = obj_get(spells_node, cname);
        if (!sub || sub->kind != N_OBJ) {
            res_die(path, list->line, "spells: missing subtree for class");
        }
        for (size_t i = 0; i < list->u.arr.n; i++) {
            const node *id_n = list->u.arr.items[i];
            if (!id_n || id_n->kind != N_STR) {
                res_die(path, list->line, "classes.spells: id must be string");
            }
            const char *dot = strchr(id_n->u.sv, '.');
            const char *local = dot ? dot + 1 : id_n->u.sv;
            const node *spell = obj_get(sub, local);
            if (!spell || spell->kind != N_OBJ) {
                res_die(path, list->line, "spells: missing id referenced by classes");
            }
            const node *slot_n = obj_get(spell, "slot");
            if (!slot_n || slot_n->kind != N_INT) {
                res_die(path, spell->line, "spell: missing 'slot'");
            }
            int32_t slot = slot_n->u.iv;
            if (slot < 1 || slot > RES_MAX_SPELL_SLOT) {
                res_die(path, slot_n->line, "spell: slot out of range");
            }
            const node *name_n = obj_get(spell, "name");
            const node *cost_n = obj_get(spell, "base_cost");
            ryl_spell *s = &tab[slot];
            free((void *)s->id);
            free((void *)s->name);
            s->id = strdup(id_n->u.sv);
            s->name = strdup((name_n && name_n->kind == N_STR) ? name_n->u.sv : id_n->u.sv);
            s->slot = slot;
            s->base_cost = (cost_n && cost_n->kind == N_INT) ? cost_n->u.iv : 0;
        }
    }
}

/* -------- terrain subtree --------
   Keyed by the literal map.dat byte as a single-character string so
   the record reads the same way the map file stores it. `glyph` is
   the two-char cell the renderer writes; `color` is one of the ANSI
   foreground colour names black/red/green/yellow/blue/magenta/cyan/
   white. */
static void fold_terrain(const node *terrain_node, const char *path) {
    if (!terrain_node) {
        return;
    }
    if (terrain_node->kind != N_OBJ) {
        res_die(path, terrain_node->line, "terrain: expected object");
    }
    for (size_t i = 0; i < terrain_node->u.obj.n; i++) {
        const char *key = terrain_node->u.obj.items[i].key;
        const node *rec = terrain_node->u.obj.items[i].val;
        if (!key || !key[0] || key[1]) {
            res_die(path, rec->line, "terrain: key must be one-char string");
        }
        if (rec->kind != N_OBJ) {
            res_die(path, rec->line, "terrain: entry must be object");
        }
        unsigned char g = (unsigned char)key[0];
        const node *glyph_n = obj_get(rec, "glyph");
        if (glyph_n) {
            if (glyph_n->kind != N_STR) {
                res_die(path, glyph_n->line, "terrain: glyph must be string");
            }
            free(glyph_tab[g]);
            glyph_tab[g] = strdup(glyph_n->u.sv);
        }
        const node *color_n = obj_get(rec, "color");
        if (color_n) {
            if (color_n->kind != N_STR) {
                res_die(path, color_n->line, "terrain: color must be string");
            }
            int32_t code = color_code_from_name(color_n->u.sv);
            if (code < 0) {
                res_die(path, color_n->line, "terrain: unknown color name");
            }
            color_tab[g] = code;
            color_tab_set[g] = 1;
        }
        const node *class_n = obj_get(rec, "class");
        if (class_n) {
            if (class_n->kind != N_STR) {
                res_die(path, class_n->line, "terrain: class must be string");
            }
            free(class_tab[g]);
            class_tab[g] = strdup(class_n->u.sv);
        }
    }
}

/* -------- pronoun subtree --------
   Two fixed keys, "male" and "female", map to gender indices 1 and 2
   that the res_pronoun() API uses. */
static void fold_pronoun(const node *pronoun_node, const char *path) {
    if (!pronoun_node) {
        return;
    }
    if (pronoun_node->kind != N_OBJ) {
        res_die(path, pronoun_node->line, "pronoun: expected object");
    }
    for (size_t i = 0; i < pronoun_node->u.obj.n; i++) {
        const char *key = pronoun_node->u.obj.items[i].key;
        const node *rec = pronoun_node->u.obj.items[i].val;
        int gender;
        if (strcmp(key, "male") == 0) {
            gender = 1;
        } else if (strcmp(key, "female") == 0) {
            gender = 2;
        } else {
            res_die(path, rec->line, "pronoun: key must be 'male' or 'female'");
        }
        if (rec->kind != N_OBJ) {
            res_die(path, rec->line, "pronoun: entry must be object");
        }
        const node *subj = obj_get(rec, "subject");
        const node *obj_n = obj_get(rec, "object");
        const node *poss = obj_get(rec, "possessive");
        free(pronoun_tab[gender][1]);
        pronoun_tab[gender][1] = strdup((subj && subj->kind == N_STR) ? subj->u.sv : "");
        free(pronoun_tab[gender][2]);
        pronoun_tab[gender][2] = strdup((obj_n && obj_n->kind == N_STR) ? obj_n->u.sv : "");
        free(pronoun_tab[gender][3]);
        pronoun_tab[gender][3] = strdup((poss && poss->kind == N_STR) ? poss->u.sv : "");
    }
}

/* -------- towns subtree --------
   Array indexed by twn slot 0..n_towns-1. Heap-allocated to fit one
   slot per declared entry; the static cap TOWN_SLOT_HARD_MAX is
   enforced at fold-time only. The wilderness sentinel is the
   distinct WILDERNESS_TOWN value (state.h), never a towns[] slot. */
static void fold_towns(const node *towns_node, const char *path) {
    if (!towns_node) {
        return;
    }
    if (towns_node->kind != N_ARR) {
        res_die(path, towns_node->line, "towns: expected array");
    }
    if (towns_node->u.arr.n > TOWN_SLOT_HARD_MAX) {
        res_die(path, towns_node->line, "towns: too many entries");
    }
    size_t n = towns_node->u.arr.n;
    n_towns = n;

    /* Drop any prior allocation; reload paths re-fold from scratch. */
    for (size_t i = 0; i < towns_cap; i++) {
        free(towns[i].name);
    }
    free(towns);
    towns = NULL;
    towns_cap = 0;
    if (n == 0) {
        return;
    }

    towns = calloc(n, sizeof *towns);
    if (!towns) {
        res_die(path, towns_node->line, "oom");
    }
    towns_cap = n;

    for (size_t i = 0; i < n; i++) {
        const node *rec = towns_node->u.arr.items[i];
        if (!rec || rec->kind != N_OBJ) {
            res_die(path, rec ? rec->line : towns_node->line,
                    "towns: entry must be object");
        }
        const node *name = obj_get(rec, "name");
        const node *x = obj_get(rec, "x");
        const node *y = obj_get(rec, "y");
        if (!name || name->kind != N_STR) {
            res_die(path, rec->line, "towns: name string required");
        }
        if (!x || x->kind != N_INT || !y || y->kind != N_INT) {
            res_die(path, rec->line, "towns: x/y integer required");
        }
        towns[i].name = strdup(name->u.sv);
        towns[i].x = x->u.iv;
        towns[i].y = y->u.iv;
        const node *tax = obj_get(rec, "tax");
        towns[i].tax = (tax && tax->kind == N_INT) ? tax->u.iv : 0;
        const node *ch = obj_get(rec, "has_church");
        towns[i].has_church = (ch && ch->kind == N_BOOL && ch->u.bv) ? 1 : 0;
        const node *cls = obj_get(rec, "class");
        towns[i].is_city =
            (cls && cls->kind == N_STR && strcmp(cls->u.sv, "city") == 0) ? 1 : 0;
    }
}

static void reset_towns(void) {
    for (size_t i = 0; i < towns_cap; i++) {
        free(towns[i].name);
    }
    free(towns);
    towns = NULL;
    towns_cap = 0;
    n_towns = 0;
}

/* -------- buildings subtree --------
   `{ smith: { opens_at: 6, closes_at: 18 }, ... }` — per-building
   opening hours consulted by the C wrappers in src/towne.c so
   the DSL can author the closed screen without embedding hour
   literals. `opens_at` and `closes_at` are both inclusive hours in
   the 0..23 convention that state.hour_of_day advances through. If
   `opens_at > closes_at` the range wraps around midnight (open from
   opens_at through 24, and through closes_at the next day); the
   tavern uses this form — open 6..1 means closed only at 2..5. It
   is up to the per-building DSL script to choose the matching
   closed-check shape (`hour_of_day<lo || hour_of_day>hi` for the
   non-wrap form, `hour_of_day<lo && hour_of_day>hi` for the wrap
   form). */
static void fold_buildings(const node *buildings_node, const char *path) {
    if (!buildings_node) {
        return;
    }
    if (buildings_node->kind != N_OBJ) {
        res_die(path, buildings_node->line, "buildings: expected object");
    }
    size_t count = buildings_node->u.obj.n;
    if (count == 0) {
        return;
    }
    buildings_tab = calloc(count, sizeof *buildings_tab);
    if (!buildings_tab) {
        LOG_E("res", "oom");
        exit(1);
    }
    for (size_t i = 0; i < count; i++) {
        const char *key = buildings_node->u.obj.items[i].key;
        const node *rec = buildings_node->u.obj.items[i].val;
        if (!rec || rec->kind != N_OBJ) {
            res_die(path, rec ? rec->line : buildings_node->line,
                    "buildings: entry must be object");
        }
        const node *lo_n = obj_get(rec, "opens_at");
        const node *hi_n = obj_get(rec, "closes_at");
        if (!lo_n || lo_n->kind != N_INT) {
            res_die(path, rec->line,
                    "buildings: opens_at integer required");
        }
        if (!hi_n || hi_n->kind != N_INT) {
            res_die(path, rec->line,
                    "buildings: closes_at integer required");
        }
        if (lo_n->u.iv < 0 || lo_n->u.iv > 23 ||
            hi_n->u.iv < 0 || hi_n->u.iv > 23) {
            res_die(path, rec->line,
                    "buildings: opens_at/closes_at must be in 0..23");
        }
        /* opens_at > closes_at is the explicit wrap-around form (e.g.
           tavern 6..1). Each building's DSL picks the matching
           closed-check shape (see the comment on fold_buildings). */
        buildings_tab[i].name = strdup(key);
        buildings_tab[i].opens_at = lo_n->u.iv;
        buildings_tab[i].closes_at = hi_n->u.iv;
    }
    n_buildings = count;
}

static void reset_buildings(void) {
    for (size_t i = 0; i < n_buildings; i++) {
        free(buildings_tab[i].name);
    }
    free(buildings_tab);
    buildings_tab = NULL;
    n_buildings = 0;
}

/* -------- limits subtree --------
   Scalar tuning knobs for cold-start realization. Missing keys keep
   their static defaults (set above); a present key overrides. */
static void fold_limits(const node *limits_node, const char *path) {
    if (!limits_node) {
        return;
    }
    if (limits_node->kind != N_OBJ) {
        res_die(path, limits_node->line, "limits: expected object");
    }
    const node *slack = obj_get(limits_node, "npc_slack");
    if (slack) {
        if (slack->kind != N_INT) {
            res_die(path, slack->line, "limits.npc_slack: expected integer");
        }
        if (slack->u.iv < 0) {
            res_die(path, slack->line, "limits.npc_slack: must be >= 0");
        }
        npc_slack = slack->u.iv;
    }
    const node *inv = obj_get(limits_node, "inventory_slot_max");
    if (inv) {
        if (inv->kind != N_INT) {
            res_die(path, inv->line,
                    "limits.inventory_slot_max: expected integer");
        }
        if (inv->u.iv < 1 || inv->u.iv > INVENTORY_SLOT_MAX) {
            res_die(path, inv->line,
                    "limits.inventory_slot_max: must be in 1..16");
        }
        inventory_slot_max = inv->u.iv;
    }
    const node *pmm = obj_get(limits_node, "party_member_max");
    if (pmm) {
        if (pmm->kind != N_INT) {
            res_die(path, pmm->line,
                    "limits.party_member_max: expected integer");
        }
        if (pmm->u.iv < 1) {
            res_die(path, pmm->line,
                    "limits.party_member_max: must be >= 1");
        }
        party_member_max = pmm->u.iv;
    }
    const node *psm = obj_get(limits_node, "party_slot_max");
    if (psm) {
        if (psm->kind != N_INT) {
            res_die(path, psm->line,
                    "limits.party_slot_max: expected integer");
        }
        if (psm->u.iv < 2) {
            res_die(path, psm->line,
                    "limits.party_slot_max: must be >= 2");
        }
        party_slot_max = psm->u.iv;
    }
    const node *lsm = obj_get(limits_node, "location_slot_max");
    if (lsm) {
        if (lsm->kind != N_INT) {
            res_die(path, lsm->line,
                    "limits.location_slot_max: expected integer");
        }
        if (lsm->u.iv < 1) {
            res_die(path, lsm->line,
                    "limits.location_slot_max: must be >= 1");
        }
        location_slot_max = lsm->u.iv;
    }
    const node *rlm = obj_get(limits_node, "random_location_max");
    if (rlm) {
        if (rlm->kind != N_INT) {
            res_die(path, rlm->line,
                    "limits.random_location_max: expected integer");
        }
        if (rlm->u.iv < 0) {
            res_die(path, rlm->line,
                    "limits.random_location_max: must be >= 0");
        }
        random_location_max = rlm->u.iv;
    }
    /* random_location_max addresses slots 1..random_location_max; that
       must fit inside location_slot_max (slots 0..location_slot_max-1).
       Catch the misconfiguration at fold time. */
    if (random_location_max >= location_slot_max) {
        res_die(path, limits_node->line,
                "limits.random_location_max must be < location_slot_max");
    }
}

static void reset_limits(void) {
    npc_slack = 1;
    inventory_slot_max = INVENTORY_SLOT_MAX;
    party_member_max = PARTY_MEMBER_MAX;
    party_slot_max = PARTY_SLOT_HARD_MAX;
    map_rows_max = MAP_ROWS_HARD_MAX;
    map_cols_max = MAP_COLS_HARD_MAX;
    location_slot_max = 15;
    random_location_max = 9;
}

/* -------- map dims (storage capacity) --------
   `map.rows_max` / `map.cols_max` size state.map[][]'s heap allocation.
   The active row count populated from data/map.dat is `map.rows`,
   read lazily via res_int_path. Both caps default to MAP_*_HARD_MAX
   if the world omits the keys. */
static void fold_map_dims(const node *map_node, const char *path) {
    if (!map_node) {
        return;
    }
    if (map_node->kind != N_OBJ) {
        res_die(path, map_node->line, "map: expected object");
    }
    const node *rm = obj_get(map_node, "rows_max");
    if (rm) {
        if (rm->kind != N_INT) {
            res_die(path, rm->line, "map.rows_max: expected integer");
        }
        if (rm->u.iv < 1 || rm->u.iv > MAP_ROWS_HARD_MAX) {
            res_die(path, rm->line,
                    "map.rows_max: must be in 1..MAP_ROWS_HARD_MAX");
        }
        map_rows_max = rm->u.iv;
    }
    const node *cm = obj_get(map_node, "cols_max");
    if (cm) {
        if (cm->kind != N_INT) {
            res_die(path, cm->line, "map.cols_max: expected integer");
        }
        if (cm->u.iv < 1 || cm->u.iv > MAP_COLS_HARD_MAX) {
            res_die(path, cm->line,
                    "map.cols_max: must be in 1..MAP_COLS_HARD_MAX");
        }
        map_cols_max = cm->u.iv;
    }
    /* Capacity must hold the active-row count populated from
       data/map.dat (`map.rows`). driv.c::init reads into rows
       1..map.rows inclusive (slot 0 is the north-edge sentinel,
       intentionally left zero), so the calloc needs at least
       `map.rows + 1` row slots; one more is conventional for the
       south-edge sentinel (the default 42 vs 40 layout has both).
       Catch undersizing at fold time so a bad world.json5 fails
       loud rather than silently scribbling past the buffer. */
    const node *rows_active = obj_get(map_node, "rows");
    if (rows_active && rows_active->kind == N_INT &&
        rows_active->u.iv >= map_rows_max) {
        res_die(path, rows_active->line,
                "map.rows >= map.rows_max — capacity must exceed active count");
    }
}

/* -------- enums subtree --------
   `{ job: {...}, towne: {...}, ... }` — key is the enum id, value is the
   keyed-records table res_enum(id, index) indexes into. */
/* §20 L1.5: validate dungeons.scenarios at load time so typos in
   placement encounter symbols fail fast (under --check-world) rather
   than only when a player triggers the scenario. Each placement is
   { level: int, encounter: string } and the symbol must resolve via
   the dungeon_encounter enum. fold runs after fold_enums so the enum
   table is ready. Level upper bound is per-scenario (the C caller
   supplies levels), so only the lower bound is checked here;
   res_dungeon_apply_placements does the upper-bound check. */
static void fold_dungeons(const node *dungeons_node, const char *path) {
    if (!dungeons_node) {
        return;
    }
    if (dungeons_node->kind != N_OBJ) {
        res_die(path, dungeons_node->line, "dungeons: expected object");
    }
    const node *recurring = obj_get(dungeons_node, "recurring");
    if (recurring) {
        if (recurring->kind != N_ARR) {
            res_die(path, recurring->line,
                    "dungeons.recurring: expected array");
        }
        for (size_t i = 0; i < recurring->u.arr.n; i++) {
            const node *e = recurring->u.arr.items[i];
            if (!e) {
                res_die(path, recurring->line,
                        "dungeons.recurring entry missing");
            }
            const char *sym = NULL;
            int line = e->line;
            const node *uf = NULL;
            if (e->kind == N_STR) {
                sym = e->u.sv;
            } else if (e->kind == N_OBJ) {
                const node *sn = obj_get(e, "symbol");
                if (!sn || sn->kind != N_STR) {
                    res_die(path, e->line,
                            "dungeons.recurring object entry needs symbol");
                }
                sym = sn->u.sv;
                line = sn->line;
                uf = obj_get(e, "until_flag");
            } else {
                res_die(path, e->line,
                        "dungeons.recurring entry must be string or object");
            }
            if (res_enum_id("dungeon_encounter", sym) < 0) {
                LOG_E("res",
                      "%s:%d: dungeons.recurring[%zu]: unknown "
                      "dungeon_encounter symbol '%s'",
                      path, line, i, sym);
                exit(1);
            }
            if (uf) {
                if (uf->kind != N_OBJ) {
                    res_die(path, uf->line,
                            "dungeons.recurring until_flag must be object");
                }
                const node *tag_n = obj_get(uf, "tag");
                const node *val_n = obj_get(uf, "value");
                if (!tag_n || tag_n->kind != N_STR) {
                    res_die(path, uf->line,
                            "dungeons.recurring until_flag.tag missing");
                }
                if (!val_n || val_n->kind != N_STR) {
                    res_die(path, uf->line,
                            "dungeons.recurring until_flag.value missing");
                }
                if (res_flag_slot(tag_n->u.sv) < 0) {
                    LOG_E("res",
                          "%s:%d: dungeons.recurring until_flag.tag "
                          "'%s' has no record",
                          path, tag_n->line, tag_n->u.sv);
                    exit(1);
                }
                if (res_flag_value_id(tag_n->u.sv, val_n->u.sv) < 0) {
                    LOG_E("res",
                          "%s:%d: dungeons.recurring until_flag.value "
                          "'%s' not in flag '%s' values[]",
                          path, val_n->line, val_n->u.sv, tag_n->u.sv);
                    exit(1);
                }
            }
        }
    }
    const node *scenarios = obj_get(dungeons_node, "scenarios");
    if (!scenarios) {
        return;
    }
    if (scenarios->kind != N_OBJ) {
        res_die(path, scenarios->line,
                "dungeons.scenarios: expected object");
    }
    for (size_t i = 0; i < scenarios->u.obj.n; i++) {
        const char *flag_key = scenarios->u.obj.items[i].key;
        const node *s = scenarios->u.obj.items[i].val;
        if (!s || s->kind != N_OBJ) {
            res_die(path, s ? s->line : scenarios->line,
                    "dungeons.scenarios entry must be object");
        }
        const node *placements = obj_get(s, "placements");
        if (!placements) {
            res_die(path, s->line,
                    "dungeons.scenarios entry missing placements");
        }
        if (placements->kind != N_ARR) {
            res_die(path, placements->line,
                    "dungeons.scenarios.placements must be array");
        }
        for (size_t j = 0; j < placements->u.arr.n; j++) {
            const node *p = placements->u.arr.items[j];
            if (!p || p->kind != N_OBJ) {
                res_die(path, p ? p->line : placements->line,
                        "placement must be object");
            }
            const node *lvl_n = obj_get(p, "level");
            const node *enc_n = obj_get(p, "encounter");
            if (!lvl_n || lvl_n->kind != N_INT) {
                res_die(path, p->line,
                        "placement.level must be int");
            }
            if (lvl_n->u.iv < 1) {
                res_die(path, lvl_n->line,
                        "placement.level must be >= 1");
            }
            if (!enc_n || enc_n->kind != N_STR) {
                res_die(path, p->line,
                        "placement.encounter must be a string symbol");
            }
            if (res_enum_id("dungeon_encounter", enc_n->u.sv) < 0) {
                LOG_E("res",
                      "%s:%d: scenario '%s' placement[%zu]: "
                      "unknown dungeon_encounter symbol '%s'",
                      path, enc_n->line, flag_key, j, enc_n->u.sv);
                exit(1);
            }
        }
    }
}

/* Recursive validator for the scripts subtree. Walks every node under
   `scripts.*` and, on any object that looks like a known op or
   expression with a symbol-typed field, resolves the symbol against
   the matching res_*_id table — failing at world load on a typo
   instead of waiting for the script to fire. The walk is structural
   (descend into every child object/array, no whitelist of "op array"
   field names) so new control-flow ops with nested bodies don't need
   to be added here.

   What's checked:
     { encounter: { foe: '<sym>' } }      res_monster_id
     { encounter: { tier: '<sym>' } }     res_monster_tier_exists
     { dungeon:   { scenario: '<sym>' } } res_dungeon_scenario_exists
     { speak:     { building: '<sym>' } } res_enum_id("building", ...)
     { reward:    { items: ['<sym>'...] } } res_item_id (each N_STR slot)
     { set_flag:  '<tag>', value: '<v>' } res_flag_slot + res_flag_value_id
     { engine_call: '<fn>' }              script_engine_call_argc
     { engine_int:  '<fn>' }              script_engine_int_argc
     { engine_bind: { fn: '<fn>' } }      script_engine_bind_argc
     { enum:        ['<table>','<v>'] }   res_enum_id
     { item:        '<sym>' }             res_item_id  (in expressions)
     { pic:         '<sym>' }             res_enum_id("pic", ...)
     { flag:        '<tag>' }             res_flag_slot (cond/expr/mutate)

   Skipped (runtime-only, intentionally):
     - { var: 'name' } indirection (rt_env binding name, not a symbol)
     - { random: [...] } / { add: [...] } / etc. arithmetic operands
     - input_string `accept` (user-input strings, not symbols)
     - text-template placeholders (resolved by tx_fmt at runtime) */
static void fold_scripts_walk(const node *n, const char *path,
                              const char *script_id) {
    if (!n) {
        return;
    }
    if (n->kind == N_OBJ) {
        const node *enc = obj_get(n, "encounter");
        if (enc && enc->kind == N_OBJ) {
            const node *foe = obj_get(enc, "foe");
            if (foe && foe->kind == N_STR &&
                res_monster_id(foe->u.sv) < 0) {
                LOG_E("res",
                      "%s:%d: scripts.%s: unknown monster symbol "
                      "'%s' in encounter.foe",
                      path, foe->line, script_id, foe->u.sv);
                exit(1);
            }
            const node *tier = obj_get(enc, "tier");
            if (tier && tier->kind == N_STR &&
                !res_monster_tier_exists(tier->u.sv)) {
                LOG_E("res",
                      "%s:%d: scripts.%s: unknown monster tier "
                      "'%s' in encounter.tier",
                      path, tier->line, script_id, tier->u.sv);
                exit(1);
            }
        }
        const node *dgn = obj_get(n, "dungeon");
        if (dgn && dgn->kind == N_OBJ) {
            const node *sc = obj_get(dgn, "scenario");
            if (sc && sc->kind == N_STR &&
                !res_dungeon_scenario_exists(sc->u.sv)) {
                LOG_E("res",
                      "%s:%d: scripts.%s: unknown dungeons.scenarios "
                      "key '%s' in dungeon.scenario",
                      path, sc->line, script_id, sc->u.sv);
                exit(1);
            }
        }
        const node *spk = obj_get(n, "speak");
        if (spk && spk->kind == N_OBJ) {
            const node *bld = obj_get(spk, "building");
            if (bld && bld->kind == N_STR &&
                res_enum_id("building", bld->u.sv) < 0) {
                LOG_E("res",
                      "%s:%d: scripts.%s: unknown building symbol "
                      "'%s' in speak.building",
                      path, bld->line, script_id, bld->u.sv);
                exit(1);
            }
        }
        const node *rew = obj_get(n, "reward");
        if (rew && rew->kind == N_OBJ) {
            const node *items = obj_get(rew, "items");
            if (items && items->kind == N_ARR) {
                for (size_t i = 0; i < items->u.arr.n; i++) {
                    const node *e = items->u.arr.items[i];
                    if (e && e->kind == N_STR &&
                        res_item_id(e->u.sv) < 0) {
                        LOG_E("res",
                              "%s:%d: scripts.%s: unknown item "
                              "symbol '%s' in reward.items[%zu]",
                              path, e->line, script_id, e->u.sv, i);
                        exit(1);
                    }
                }
            }
        }
        const node *sf = obj_get(n, "set_flag");
        if (sf && sf->kind == N_STR) {
            if (res_flag_slot(sf->u.sv) < 0) {
                LOG_E("res",
                      "%s:%d: scripts.%s: unknown flag '%s' in "
                      "set_flag",
                      path, sf->line, script_id, sf->u.sv);
                exit(1);
            }
            const node *vn = obj_get(n, "value");
            if (vn && vn->kind == N_STR &&
                res_flag_value_id(sf->u.sv, vn->u.sv) < 0) {
                LOG_E("res",
                      "%s:%d: scripts.%s: unknown value '%s' for "
                      "flag '%s' in set_flag.value",
                      path, vn->line, script_id, vn->u.sv, sf->u.sv);
                exit(1);
            }
        }
        const node *ec = obj_get(n, "engine_call");
        if (ec && ec->kind == N_STR &&
            script_engine_call_argc(ec->u.sv) < 0) {
            LOG_E("res",
                  "%s:%d: scripts.%s: unknown engine_call fn '%s'",
                  path, ec->line, script_id, ec->u.sv);
            exit(1);
        }
        const node *ei = obj_get(n, "engine_int");
        if (ei && ei->kind == N_STR &&
            script_engine_int_argc(ei->u.sv) < 0) {
            LOG_E("res",
                  "%s:%d: scripts.%s: unknown engine_int fn '%s'",
                  path, ei->line, script_id, ei->u.sv);
            exit(1);
        }
        const node *eb = obj_get(n, "engine_bind");
        if (eb && eb->kind == N_OBJ) {
            const node *fn = obj_get(eb, "fn");
            if (fn && fn->kind == N_STR &&
                script_engine_bind_argc(fn->u.sv) < 0) {
                LOG_E("res",
                      "%s:%d: scripts.%s: unknown engine_bind fn "
                      "'%s'",
                      path, fn->line, script_id, fn->u.sv);
                exit(1);
            }
        }
        const node *en = obj_get(n, "enum");
        if (en && en->kind == N_ARR && en->u.arr.n == 2) {
            const node *ename = en->u.arr.items[0];
            const node *eval = en->u.arr.items[1];
            if (ename && ename->kind == N_STR && eval &&
                eval->kind == N_STR &&
                res_enum_id(ename->u.sv, eval->u.sv) < 0) {
                LOG_E("res",
                      "%s:%d: scripts.%s: unknown enum '%s' value "
                      "'%s'",
                      path, eval->line, script_id, ename->u.sv,
                      eval->u.sv);
                exit(1);
            }
        }
        const node *item = obj_get(n, "item");
        if (item && item->kind == N_STR &&
            res_item_id(item->u.sv) < 0) {
            LOG_E("res",
                  "%s:%d: scripts.%s: unknown item symbol '%s'",
                  path, item->line, script_id, item->u.sv);
            exit(1);
        }
        const node *pic = obj_get(n, "pic");
        if (pic && pic->kind == N_STR &&
            res_enum_id("pic", pic->u.sv) < 0) {
            LOG_E("res",
                  "%s:%d: scripts.%s: unknown pic symbol '%s'",
                  path, pic->line, script_id, pic->u.sv);
            exit(1);
        }
        const node *fln = obj_get(n, "flag");
        if (fln && fln->kind == N_STR &&
            res_flag_slot(fln->u.sv) < 0) {
            LOG_E("res",
                  "%s:%d: scripts.%s: unknown flag '%s'",
                  path, fln->line, script_id, fln->u.sv);
            exit(1);
        }

        /* Recurse into every child value. Use the child key as a path
           segment for diagnostics so a typo in
           scripts.dungeon.find_object surfaces with that breadcrumb. */
        for (size_t i = 0; i < n->u.obj.n; i++) {
            char child_id[256];
            const char *key = n->u.obj.items[i].key;
            if (script_id[0]) {
                snprintf(child_id, sizeof child_id, "%s.%s", script_id, key);
            } else {
                snprintf(child_id, sizeof child_id, "%s", key);
            }
            fold_scripts_walk(n->u.obj.items[i].val, path, child_id);
        }
    } else if (n->kind == N_ARR) {
        for (size_t i = 0; i < n->u.arr.n; i++) {
            fold_scripts_walk(n->u.arr.items[i], path, script_id);
        }
    }
}

static void fold_scripts(const node *scripts_node, const char *path) {
    if (!scripts_node) {
        return;
    }
    if (scripts_node->kind != N_OBJ) {
        res_die(path, scripts_node->line, "scripts: expected object");
    }
    fold_scripts_walk(scripts_node, path, "");
}

static void fold_enum_array(enum_rec *r, const node *arr, const char *path) {
    r->n_values = arr->u.arr.n;
    r->keys = NULL;
    r->values = r->n_values ? malloc(r->n_values * sizeof *r->values) : NULL;
    if (r->n_values && !r->values) {
        res_die(path, arr->line, "oom");
    }
    for (size_t j = 0; j < arr->u.arr.n; j++) {
        const node *v = arr->u.arr.items[j];
        if (v->kind != N_STR) {
            res_die(path, v->line, "enums: element must be string");
        }
        r->values[j] = strdup(v->u.sv);
    }
}

/* Keyed-record enum shape: each child is an object carrying `id:` (slot
   index) and `name:` (display string). The lowercase key becomes the
   stable symbol; the display text moves into a separate field so renaming
   the visible label doesn't change script identity. Unfilled slots stay
   empty so the array indexing semantics match the legacy positional shape. */
static void fold_enum_records(enum_rec *r, const node *obj, const char *path) {
    /* §24bis phase 3 cleanup: `id:` is optional. Two shapes are
       accepted, distinguished by whether any entry carries an
       explicit id field:

       - Explicit ids: every entry must declare `id: <int>`. Slots
         are spaced as in the data; gaps stay empty (legacy npcs-
         like sparse layout, though npcs itself isn't an enum).

       - Implicit ids: no entry carries `id:`. Entries land at slots
         1..N in object-key order, with slot 0 reserved as the
         "unset" sentinel. Mixing the two shapes within one enum is
         rejected — pick one or the other so the data is unambiguous. */
    int has_explicit = 0;
    int has_implicit = 0;
    int32_t max_id = 0;
    for (size_t j = 0; j < obj->u.obj.n; j++) {
        const node *rec = obj->u.obj.items[j].val;
        if (rec->kind != N_OBJ) {
            res_die(path, rec->line, "enums: keyed entry must be object");
        }
        const node *id_n = obj_get(rec, "id");
        if (id_n) {
            if (id_n->kind != N_INT) {
                res_die(path, id_n->line, "enums: id must be int");
            }
            if (id_n->u.iv < 0) {
                res_die(path, id_n->line, "enums: id must be >= 0");
            }
            has_explicit = 1;
            if (id_n->u.iv > max_id) {
                max_id = id_n->u.iv;
            }
        } else {
            has_implicit = 1;
        }
    }
    if (has_explicit && has_implicit) {
        res_die(path, obj->line,
                "enums: mix of id-bearing and id-less entries — pick one shape");
    }
    if (has_explicit) {
        r->n_values = (size_t)max_id + 1;
    } else {
        /* 1-indexed positional. Slot 0 stays the unset sentinel so
           pm_class_id(NULL slot) → res_enum_id → 0 still maps to "no class". */
        r->n_values = obj->u.obj.n + 1;
    }
    r->keys = calloc(r->n_values, sizeof *r->keys);
    r->values = calloc(r->n_values, sizeof *r->values);
    if (!r->keys || !r->values) {
        res_die(path, obj->line, "oom");
    }
    for (size_t j = 0; j < r->n_values; j++) {
        r->values[j] = strdup("");
    }
    for (size_t j = 0; j < obj->u.obj.n; j++) {
        const char *key = obj->u.obj.items[j].key;
        const node *rec = obj->u.obj.items[j].val;
        const node *id_n = obj_get(rec, "id");
        const node *name_n = obj_get(rec, "name");
        if (!name_n || name_n->kind != N_STR) {
            res_die(path, rec->line, "enums: keyed entry missing 'name'");
        }
        size_t idx = has_explicit ? (size_t)id_n->u.iv : j + 1;
        if (r->keys[idx]) {
            res_die(path, id_n ? id_n->line : rec->line,
                    "enums: duplicate slot in keyed enum");
        }
        r->keys[idx] = strdup(key);
        free(r->values[idx]);
        r->values[idx] = strdup(name_n->u.sv);
    }
}

static void fold_enums(const node *enums_node, const char *path) {
    if (!enums_node) {
        return;
    }
    if (enums_node->kind != N_OBJ) {
        res_die(path, enums_node->line, "enums: expected object");
    }
    for (size_t i = 0; i < enums_node->u.obj.n; i++) {
        const char *id = enums_node->u.obj.items[i].key;
        const node *val = enums_node->u.obj.items[i].val;
        if (val->kind != N_ARR && val->kind != N_OBJ) {
            res_die(path, val->line, "enums: value must be array or object");
        }
        enum_rec *grown = realloc(enums, (n_enums + 1) * sizeof *enums);
        if (!grown) {
            res_die(path, val->line, "oom");
        }
        enums = grown;
        enum_rec *r = &enums[n_enums++];
        r->id = strdup(id);
        if (val->kind == N_ARR) {
            fold_enum_array(r, val, path);
        } else {
            fold_enum_records(r, val, path);
        }
    }
}

void res_load_world(const char *path) {
    /* Tolerant: missing file = empty world. Corrupt file = fatal
       (parser res_die()s with path:line:msg). */
    FILE *probe = fopen(path, "rb");
    if (!probe) {
        return;
    }
    fclose(probe);

    node *root = json5_parse_file(path);
    root = json5_resolve_imports(root, path);
    if (root->kind != N_OBJ) {
        res_die(path, root->line, "world root must be object");
    }

    /* Fold every known subtree into its legacy table. Order is not
       load-bearing — each fold reads disjoint state — but keeping it
       stable helps when diffing error output across commits. */
    fold_flags(obj_get(root, "flags"), path);
    fold_items(obj_get(root, "items"), path);
    fold_monsters(obj_get(root, "monsters"), path);
    {
        const node *enums_n = obj_get(root, "enums");
        const node *pc_class_n = enums_n ? obj_get(enums_n, "pc_class") : NULL;
        fold_spells(pc_class_n, obj_get(root, "spells"), path);
    }
    fold_terrain(obj_get(root, "terrain"), path);
    fold_pronoun(obj_get(root, "pronoun"), path);
    fold_towns(obj_get(root, "towns"), path);
    fold_buildings(obj_get(root, "buildings"), path);
    fold_limits(obj_get(root, "limits"), path);
    fold_map_dims(obj_get(root, "map"), path);
    fold_enums(obj_get(root, "enums"), path);
    fold_dungeons(obj_get(root, "dungeons"), path);
    /* fold_scripts uses res_monster_id / res_item_id / etc., all of
       which read off `world_tree`. Publish the (still-uncompiled) tree
       before walking scripts so the lookups can resolve; the path-
       compile pass below leaves identity lookups untouched. */
    world_tree = root;
    fold_scripts(obj_get(root, "scripts"), path);
    fold_texts(obj_get(root, "texts"), path);
    texts_sort();

    /* Right-size the runtime NPC table to res_npc_slot_max() entries
       (slots 0..count-1, post-§25.B step 4 NPC axis flip), zeroed.
       Idempotent so the res_shutdown / res_load_world reload path
       drops the prior allocation before allocating a freshly-sized
       one. The slot-count cap check inside npc_slot_max() bounds
       this against NPC_SLOT_HARD_MAX before we calloc anything
       bogus. A degenerate world with zero NPC records leaves
       state.npc as NULL — for_each_npc never iterates so no
       accessor dereferences it. */
    free(state.npc);
    state.npc = NULL;
    size_t npc_n = (size_t)res_npc_slot_max();
    if (npc_n > 0) {
        state.npc = calloc(npc_n, sizeof(struct person));
        if (!state.npc) {
            LOG_E("realm", "out of memory allocating state.npc[%zu]", npc_n);
            exit(1);
        }
        /* Per-NPC visited[] grids: one byte per party slot. Calloc'd so
           the default state is "not yet visited" for every party slot.
           Sized off res_party_slot_max() (already finalized by
           fold_limits above). Freed by res_shutdown's symmetric loop
           before the bulk free(state.npc). */
        size_t visited_n = (size_t)res_party_slot_max();
        for (size_t i = 0; i < npc_n; i++) {
            state.npc[i].visited = calloc(visited_n, sizeof *state.npc[i].visited);
            if (!state.npc[i].visited) {
                LOG_E("realm", "out of memory allocating npc[%zu].visited[%zu]",
                      i, visited_n);
                exit(1);
            }
        }
    }

    /* Right-size the persistent party table to res_party_slot_max()
       entries, zeroed. Slot 0 is the 1-indexed sentinel (unused);
       valid party ids are 1..res_party_slot_max()-1. Same idempotent
       free-then-alloc pattern as state.npc. The static cap
       PARTY_SLOT_HARD_MAX bounds res_party_slot_max() at world-load
       time (fold_limits) so this calloc never sees a wild value. */
    free(state.p);
    state.p = NULL;
    size_t party_n = (size_t)res_party_slot_max();
    if (party_n > 0) {
        state.p = calloc(party_n, sizeof(struct party));
        if (!state.p) {
            LOG_E("realm", "out of memory allocating state.p[%zu]", party_n);
            exit(1);
        }
    }

    /* Right-size the world-map grid to res_map_rows_max() rows ×
       res_map_cols_max() columns, flat 1D so the inner row width can
       be a runtime knob. Tile (x, y) lives at offset
       y * res_map_cols_max() + x; wld.c access goes through
       wld_tile_at / wld_set_tile_at. fold_map_dims has already
       validated both against MAP_*_HARD_MAX. Same idempotent
       free-then-alloc pattern as state.npc / state.p. */
    free(state.map);
    state.map = NULL;
    size_t map_rows = (size_t)res_map_rows_max();
    size_t map_cols = (size_t)res_map_cols_max();
    state.map = calloc(map_rows * map_cols, sizeof *state.map);
    if (!state.map) {
        LOG_E("realm", "out of memory allocating state.map[%zu][%zu]",
              map_rows, map_cols);
        exit(1);
    }

    /* Random-location placement tables. state.dx[] / state.dy[] are
       sized to res_location_slot_max() (default 15). Slot 0 is the
       (0,0) wilderness sentinel; slots 1..res_random_location_max()
       receive random (x, y) draws in driv.c::position_locations;
       higher slots stay (0,0) for static placements. Calloc gives
       the zero default for free. */
    free(state.dx);
    state.dx = NULL;
    free(state.dy);
    state.dy = NULL;
    size_t loc_n = (size_t)res_location_slot_max();
    state.dx = calloc(loc_n, sizeof *state.dx);
    state.dy = calloc(loc_n, sizeof *state.dy);
    if (!state.dx || !state.dy) {
        LOG_E("realm", "out of memory allocating state.dx/dy[%zu]", loc_n);
        exit(1);
    }

    /* Register the teardown the first time a world is loaded. Guarded
       so reload paths (res_shutdown followed by another res_load_world)
       don't stack LIFO copies on the atexit chain. */
    static int atexit_registered = 0;
    if (!atexit_registered) {
        atexit(res_shutdown);
        atexit_registered = 1;
    }
}

int32_t res_flag_slot(const char *id) {
    for (size_t i = 0; i < n_flags_tab; i++) {
        if (strcmp(flags_tab[i].id, id) == 0) {
            return flags_tab[i].slot;
        }
    }
    return -1;
}

const char *res_flag_value(const char *flag_id, int index) {
    for (size_t i = 0; i < n_flags_tab; i++) {
        if (strcmp(flags_tab[i].id, flag_id) == 0) {
            if (index < 0 || (size_t)index >= flags_tab[i].n_values) {
                return "";
            }
            return flags_tab[i].values[index];
        }
    }
    return "";
}

int32_t res_flag_value_id(const char *flag_id, const char *value) {
    for (size_t i = 0; i < n_flags_tab; i++) {
        if (strcmp(flags_tab[i].id, flag_id) == 0) {
            for (size_t j = 0; j < flags_tab[i].n_values; j++) {
                if (strcmp(flags_tab[i].values[j], value) == 0) {
                    return (int32_t)j;
                }
            }
            return -1;
        }
    }
    return -1;
}

const char *res_flag_id_for_slot(int32_t slot) {
    for (size_t i = 0; i < n_flags_tab; i++) {
        if (flags_tab[i].scope == 0 && flags_tab[i].slot == slot) {
            return flags_tab[i].id;
        }
    }
    return NULL;
}

const void *res_spell_node(const char *class_id, int slot) {
    const node *root = world_tree;
    if (!root) {
        return NULL;
    }
    const node *cls = pc_class_node(class_id);
    if (!cls) {
        return NULL;
    }
    const node *spells_list = obj_get(cls, "spells");
    if (!spells_list || spells_list->kind != N_ARR) {
        return NULL;
    }
    const node *spells_tree = obj_get(root, "spells");
    if (!spells_tree) {
        return NULL;
    }
    const node *sub = obj_get(spells_tree, class_id);
    if (!sub) {
        return NULL;
    }
    for (size_t i = 0; i < spells_list->u.arr.n; i++) {
        const node *id_n = spells_list->u.arr.items[i];
        if (!id_n || id_n->kind != N_STR) {
            continue;
        }
        const char *dot = strchr(id_n->u.sv, '.');
        const char *local = dot ? dot + 1 : id_n->u.sv;
        const node *spell = obj_get(sub, local);
        if (!spell) {
            continue;
        }
        const node *slot_n = obj_get(spell, "slot");
        if (!slot_n || slot_n->kind != N_INT) {
            continue;
        }
        if (slot_n->u.iv != slot) {
            continue;
        }
        const node *do_n = obj_get(spell, "do");
        if (!do_n) {
            return NULL;
        }
        return do_n;
    }
    return NULL;
}

/* §23.A: monster nasty-magic spell list lookup helpers. The data
   shape is monster_spells.spells: [<spell-obj>, ...] — an ordered
   array, not a slot map; battle.c::monster_cast_spell draws
   ryl_random(0, count-1) and asks for the body at that index. */
const void *res_monster_spell_node(int idx) {
    const node *root = world_tree;
    if (!root) {
        return NULL;
    }
    const node *ms = obj_get(root, "monster_spells");
    if (!ms || ms->kind != N_OBJ) {
        return NULL;
    }
    const node *spells = obj_get(ms, "spells");
    if (!spells || spells->kind != N_ARR) {
        return NULL;
    }
    if (idx < 0 || (size_t)idx >= spells->u.arr.n) {
        return NULL;
    }
    const node *spell = spells->u.arr.items[idx];
    if (!spell || spell->kind != N_OBJ) {
        return NULL;
    }
    return obj_get(spell, "do");
}

int res_monster_spell_count(void) {
    const node *root = world_tree;
    if (!root) {
        return 0;
    }
    const node *ms = obj_get(root, "monster_spells");
    if (!ms || ms->kind != N_OBJ) {
        return 0;
    }
    const node *spells = obj_get(ms, "spells");
    if (!spells || spells->kind != N_ARR) {
        return 0;
    }
    return (int)spells->u.arr.n;
}

/* §23.A follow-up: monster-melee body lookup at
   monster_spells.melee.do. Sibling of spells: the melee turn's
   `do:` body lives next to the spell array because both are
   monster-side combat ops sharing the same combat-ctx wiring;
   the file's top-level shape stays a single $import in
   world.json5. */
const void *res_monster_melee_node(void) {
    const node *root = world_tree;
    if (!root) {
        return NULL;
    }
    const node *ms = obj_get(root, "monster_spells");
    if (!ms || ms->kind != N_OBJ) {
        return NULL;
    }
    const node *melee = obj_get(ms, "melee");
    if (!melee || melee->kind != N_OBJ) {
        return NULL;
    }
    return obj_get(melee, "do");
}

/* UI verb pool — `prompts.pause_verbs[idx]`. Indexed by
   ryl_random(0, count-1) at the ryl_pause call site so RNG identity
   stays explicit there. Returns NULL on missing subtree, wrong
   shape, or out-of-range idx; ryl_pause falls back to a static
   "Press" prefix in that case. */
const char *res_pause_verb(int idx) {
    const node *root = world_tree;
    if (!root) {
        return NULL;
    }
    const node *prompts = obj_get(root, "prompts");
    if (!prompts || prompts->kind != N_OBJ) {
        return NULL;
    }
    const node *verbs = obj_get(prompts, "pause_verbs");
    if (!verbs || verbs->kind != N_ARR) {
        return NULL;
    }
    if (idx < 0 || (size_t)idx >= verbs->u.arr.n) {
        return NULL;
    }
    const node *v = verbs->u.arr.items[idx];
    if (!v || v->kind != N_STR) {
        return NULL;
    }
    return v->u.sv;
}

int res_pause_verb_count(void) {
    const node *root = world_tree;
    if (!root) {
        return 0;
    }
    const node *prompts = obj_get(root, "prompts");
    if (!prompts || prompts->kind != N_OBJ) {
        return 0;
    }
    const node *verbs = obj_get(prompts, "pause_verbs");
    if (!verbs || verbs->kind != N_ARR) {
        return 0;
    }
    return (int)verbs->u.arr.n;
}

/* §23.B magic-item effect lookup. magic_items is an OBJ keyed by
   slug ('frost_wand', 'gem_of_confusion', ...); each value carries an
   integer `id` field (100..111). Walk the OBJ until we find the entry
   whose `id` matches `oc`, then return its `effect:` body. Linear
   scan is fine — the table is fixed at ~12 entries and this lookup
   only fires on the player's "use magic item" branch in combat. */
const void *res_magic_item_effect_node(int oc, const char **slug_out) {
    if (slug_out) {
        *slug_out = NULL;
    }
    const node *root = world_tree;
    if (!root) {
        return NULL;
    }
    const node *items = obj_get(root, "magic_items");
    if (!items || items->kind != N_OBJ) {
        return NULL;
    }
    for (size_t i = 0; i < items->u.obj.n; i++) {
        const node_kv *kv = &items->u.obj.items[i];
        const node *entry = kv->val;
        if (!entry || entry->kind != N_OBJ) {
            continue;
        }
        const node *id_n = obj_get(entry, "id");
        if (!id_n || id_n->kind != N_INT) {
            continue;
        }
        if (id_n->u.iv != oc) {
            continue;
        }
        const node *eff = obj_get(entry, "effect");
        if (eff && slug_out) {
            *slug_out = kv->key;
        }
        return eff;
    }
    return NULL;
}

const void *res_script(const char *dotted_id) {
    const node *cur = world_tree;
    if (!cur || cur->kind != N_OBJ) {
        return NULL;
    }
    cur = obj_get(cur, "scripts");
    if (!cur || cur->kind != N_OBJ) {
        return NULL;
    }
    const char *p = dotted_id;
    while (*p) {
        const char *dot = strchr(p, '.');
        size_t seg_len = dot ? (size_t)(dot - p) : strlen(p);
        if (seg_len == 0 || seg_len >= 128) {
            return NULL;
        }
        char seg[128];
        memcpy(seg, p, seg_len);
        seg[seg_len] = 0;
        if (cur->kind != N_OBJ) {
            return NULL;
        }
        cur = obj_get(cur, seg);
        if (!cur) {
            return NULL;
        }
        if (!dot) {
            break;
        }
        p = dot + 1;
    }
    return (cur->kind == N_ARR) ? cur : NULL;
}

/* ---------------------------------------------------------------- */
/*  Teardown                                                        */
/* ---------------------------------------------------------------- */

/* Rule for this module: the *parser* owns cardinality. Every walk here
   loops over `node->u.obj.n` / `node->u.arr.n` on subtrees read out of
   `world_tree` — the same structural counts the folds used on the way
   in. No schema-declared "count" fields; no NULL-sentinel scans over
   fixed-size legacy arrays; no hardcoded 0..111 / 0..90 / 0..255
   bounds. The dynamic tables (flags_tab / enums / texts) keep their
   own `n_*` because the fold copies strings *out* of the tree into a
   flat table — that count is bookkeeping the parse tree doesn't have.
   See port-plan.md §13.3. */

static void reset_flags_tab(void) {
    for (size_t i = 0; i < n_flags_tab; i++) {
        free(flags_tab[i].id);
        for (size_t j = 0; j < flags_tab[i].n_values; j++) {
            free(flags_tab[i].values[j]);
        }
        free(flags_tab[i].values);
    }
    free(flags_tab);
    flags_tab = NULL;
    n_flags_tab = 0;
}

static void reset_enums(void) {
    for (size_t i = 0; i < n_enums; i++) {
        free(enums[i].id);
        for (size_t j = 0; j < enums[i].n_values; j++) {
            if (enums[i].keys) {
                free(enums[i].keys[j]);
            }
            free(enums[i].values[j]);
        }
        free(enums[i].keys);
        free(enums[i].values);
    }
    free(enums);
    enums = NULL;
    n_enums = 0;
}

static void reset_texts(void) {
    for (size_t i = 0; i < n_texts; i++) {
        free(texts[i].id);
        free(texts[i].text);
    }
    free(texts);
    texts = NULL;
    n_texts = 0;
    texts_sorted = 0;
}

static void reset_items_from_tree(const node *items_node) {
    if (!items_node || items_node->kind != N_OBJ) {
        return;
    }
    for (size_t s = 0; s < items_node->u.obj.n; s++) {
        const node *section = items_node->u.obj.items[s].val;
        if (!section || section->kind != N_OBJ) {
            continue;
        }
        for (size_t i = 0; i < section->u.obj.n; i++) {
            const node *rec = section->u.obj.items[i].val;
            if (!rec || rec->kind != N_OBJ) {
                continue;
            }
            const node *id_n = obj_get(rec, "id");
            if (!id_n || id_n->kind != N_INT) {
                continue;
            }
            int32_t id = id_n->u.iv;
            if (id < 0 || id > RES_MAX_ITEM_ID) {
                continue;
            }
            free((void *)items[id].name);
            items[id].name = NULL;
            items[id].cost = 0;
            items[id].damage = 0;
            item_kind[id] = NULL;
        }
    }
}

static void reset_monsters_from_tree(const node *monsters_node) {
    if (!monsters_node || monsters_node->kind != N_OBJ) {
        return;
    }
    for (size_t i = 0; i < monsters_node->u.obj.n; i++) {
        const node *rec = monsters_node->u.obj.items[i].val;
        if (!rec || rec->kind != N_OBJ) {
            continue;
        }
        const node *id_n = obj_get(rec, "id");
        if (!id_n || id_n->kind != N_INT) {
            continue;
        }
        int32_t id = id_n->u.iv;
        if (id < 0 || id > RES_MAX_MONSTER_ID) {
            continue;
        }
        free((void *)monsters[id].name);
        monsters[id].name = NULL;
        monsters[id].n1 = 0;
        monsters[id].n2 = 0;
        monsters[id].h1 = 0;
        monsters[id].h2 = 0;
        monsters[id].ws = 0;
        monsters[id].sp = 0;
        monsters[id].im = 0;
    }
}

static void reset_spells_from_tree(const node *classes_node, const node *spells_node) {
    if (!classes_node || classes_node->kind != N_OBJ) {
        return;
    }
    if (!spells_node || spells_node->kind != N_OBJ) {
        return;
    }
    for (size_t c = 0; c < classes_node->u.obj.n; c++) {
        const char *cname = classes_node->u.obj.items[c].key;
        const node *cls = classes_node->u.obj.items[c].val;
        if (!cls || cls->kind != N_OBJ) {
            continue;
        }
        const node *list = obj_get(cls, "spells");
        if (!list || list->kind != N_ARR) {
            continue;
        }
        /* §24bis phase 3 cleanup: slug compare. */
        ryl_spell *tab;
        if (strcmp(cname, "priest") == 0) {
            tab = priest_spells;
        } else if (strcmp(cname, "mage") == 0) {
            tab = mage_spells;
        } else {
            continue;
        }
        const node *sub = obj_get(spells_node, cname);
        if (!sub || sub->kind != N_OBJ) {
            continue;
        }
        for (size_t i = 0; i < list->u.arr.n; i++) {
            const node *id_n = list->u.arr.items[i];
            if (!id_n || id_n->kind != N_STR) {
                continue;
            }
            const char *dot = strchr(id_n->u.sv, '.');
            const char *local = dot ? dot + 1 : id_n->u.sv;
            const node *spell = obj_get(sub, local);
            if (!spell || spell->kind != N_OBJ) {
                continue;
            }
            const node *slot_n = obj_get(spell, "slot");
            if (!slot_n || slot_n->kind != N_INT) {
                continue;
            }
            int32_t slot = slot_n->u.iv;
            if (slot < 1 || slot > RES_MAX_SPELL_SLOT) {
                continue;
            }
            free((void *)tab[slot].id);
            free((void *)tab[slot].name);
            tab[slot].id = NULL;
            tab[slot].name = NULL;
            tab[slot].slot = 0;
            tab[slot].base_cost = 0;
        }
    }
}

static void reset_terrain_from_tree(const node *terrain_node) {
    if (!terrain_node || terrain_node->kind != N_OBJ) {
        return;
    }
    for (size_t i = 0; i < terrain_node->u.obj.n; i++) {
        const char *key = terrain_node->u.obj.items[i].key;
        if (!key || !key[0]) {
            continue;
        }
        unsigned char g = (unsigned char)key[0];
        free(glyph_tab[g]);
        glyph_tab[g] = NULL;
        free(class_tab[g]);
        class_tab[g] = NULL;
        color_tab[g] = 0;
        color_tab_set[g] = 0;
    }
}

/* fold_pronoun always strdups all three grammatical forms (subject /
   object / possessive) for every gender it sees, defaulting missing
   ones to "". So the teardown must visit all three slots per gender
   present in the parse tree — iterating `rec->u.obj.n` would miss ""
   defaults whose key never appeared in JSON5. The 1..3 range is a
   schema invariant (three grammatical cases), not a memory-bound
   magic number. */
static void reset_pronoun_from_tree(const node *pronoun_node) {
    if (!pronoun_node || pronoun_node->kind != N_OBJ) {
        return;
    }
    for (size_t i = 0; i < pronoun_node->u.obj.n; i++) {
        const char *key = pronoun_node->u.obj.items[i].key;
        int gender;
        if (strcmp(key, "male") == 0) {
            gender = 1;
        } else if (strcmp(key, "female") == 0) {
            gender = 2;
        } else {
            continue;
        }
        for (int xx = 1; xx <= 3; xx++) {
            free(pronoun_tab[gender][xx]);
            pronoun_tab[gender][xx] = NULL;
        }
    }
}

void res_ansi_path(const char *leaf, char *out, size_t out_sz) {
    if (out_sz == 0) {
        return;
    }
    snprintf(out, out_sz, "data/ansi/%s.ans", leaf ? leaf : "");
}

void res_map_path(char *out, size_t out_sz) {
    if (out_sz == 0) {
        return;
    }
    snprintf(out, out_sz, "data/map.dat");
}

void res_data_path(const char *leaf, char *out, size_t out_sz) {
    if (out_sz == 0) {
        return;
    }
    snprintf(out, out_sz, "data/%s", leaf ? leaf : "");
}

void res_shutdown(void) {
    /* Order matters: legacy tables first (they walk world_tree to drive
       their loops); world_tree itself last. */
    if (world_tree) {
        const node *root = world_tree;
        reset_items_from_tree(obj_get(root, "items"));
        reset_monsters_from_tree(obj_get(root, "monsters"));
        const node *enums_n = obj_get(root, "enums");
        const node *pc_class_n = enums_n ? obj_get(enums_n, "pc_class") : NULL;
        reset_spells_from_tree(pc_class_n, obj_get(root, "spells"));
        reset_terrain_from_tree(obj_get(root, "terrain"));
        reset_pronoun_from_tree(obj_get(root, "pronoun"));
    }
    reset_flags_tab();
    reset_enums();
    reset_texts();
    reset_towns();
    reset_buildings();
    /* Free per-NPC visited[] BEFORE invalidating npc_slot_max_cache —
       for_each_npc reads the cached count, and the cache invalidation
       below clears it. world_tree is still alive at this point so any
       cache miss would re-walk it correctly anyway. */
    if (state.npc) {
        for_each_npc(i) {
            free(state.npc[i].visited);
            state.npc[i].visited = NULL;
        }
    }
    reset_limits();
    npc_slot_max_invalidate();
    free(state.npc);
    state.npc = NULL;
    free(state.p);
    state.p = NULL;
    free(state.map);
    state.map = NULL;
    free(state.dx);
    state.dx = NULL;
    free(state.dy);
    state.dy = NULL;
    node_free(world_tree);
    world_tree = NULL;
    color_default = ANSI_FG_CYAN;
}
