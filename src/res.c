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
 *   4. compile_world_tree: rewrite every path-position N_STR (e.g.
 *      "caster.hp", "$dmg") to a packed N_PATH so the evaluator hot
 *      path does zero strcmp on actor/field names.
 *
 * The folds are forgiving about field order and missing optionals but
 * fatal on structural surprises; errors dispatch through res_die with a
 * "res: path:line: msg" message. The legacy tables retain the fold
 * results for the rest of run time; the full node tree is also kept
 * (process-lifetime) for the evaluator via res_world().
 */

#include "res.h"
#include "eval.h"
#include "json5.h"
#include "primitives.h"
#include "ryl.h"
#include "towne.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define RES_MAX_ITEM_ID    111
#define RES_MAX_MONSTER_ID 90

typedef struct {
    char *id;
    char **values;
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

/* Town table. Slot 0 = null (wilderness sentinel); slots 1..4 carry a
   display name (owned string) plus fixed (x, y) world-map coords. See
   data/world.json5 towns:. */
typedef struct {
    char *name;
    int32_t x, y;
} ryl_town;
static ryl_town towns[5];
/* Count of declared towns (slots 1..n_towns). Populated by fold_towns.
   The fixed-size `towns` array caps this at 4; fold_towns res_die()s if
   the data declares more. */
static size_t n_towns;

/* Cold-start realization knobs (port-plan §15.4). npc_slack is the
   `slack` term added to ceil(N/T) when computing the per-bin cap for
   the capacity-capped NPC draw in res_realize_npcs(). Default matches
   the §15.4 recommendation; overridden by data/world.json5 limits:. */
static int32_t npc_slack = 1;

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
   pointers live here; the evaluator walks them via eval.h. */
static node *world_tree;

__attribute__((noreturn)) void res_die(const char *path, int line, const char *msg) {
    fprintf(stderr, "res: %s:%d: %s\n", path, line, msg);
    exit(1);
}

/* Map a `legacy_effect` string from the world tree to the SE_* enum
   battle.c's priest/mage switches dispatch on. Returns -1 for unknown
   names so the caller can res_die() with a path:line pointing at the tree
   node. Phase 6d retired the JSONL handle_spell; this helper owns the
   string table on behalf of fold_spells. */
static int32_t se_from_legacy_effect(const char *eff) {
    if (strcmp(eff, "heal") == 0) {
        return SE_HEAL;
    }
    if (strcmp(eff, "restoration") == 0) {
        return SE_RESTORATION;
    }
    if (strcmp(eff, "rebirth") == 0) {
        return SE_REBIRTH;
    }
    if (strcmp(eff, "banish") == 0) {
        return SE_BANISH;
    }
    if (strcmp(eff, "revelation") == 0) {
        return SE_REVELATION;
    }
    if (strcmp(eff, "teleport") == 0) {
        return SE_TELEPORT;
    }
    if (strcmp(eff, "earthquake") == 0) {
        return SE_EARTHQUAKE;
    }
    if (strcmp(eff, "fire") == 0) {
        return SE_FIRE;
    }
    if (strcmp(eff, "confusion") == 0) {
        return SE_CONFUSION;
    }
    if (strcmp(eff, "death") == 0) {
        return SE_DEATH;
    }
    if (strcmp(eff, "hold") == 0) {
        return SE_HOLD;
    }
    if (strcmp(eff, "antimagic") == 0) {
        return SE_ANTIMAGIC;
    }
    return -1;
}

const char *res_enum(const char *id, int index) {
    for (size_t i = 0; i < n_enums; i++) {
        if (strcmp(enums[i].id, id) == 0) {
            if (index < 0 || (size_t)index >= enums[i].n_values) {
                return "";
            }
            return enums[i].values[index];
        }
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
        for (size_t j = 0; j < enums[i].n_values; j++) {
            if (strcmp(enums[i].values[j], value) == 0) {
                return (int)j;
            }
        }
        return -1;
    }
    return -1;
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

const ryl_item *res_item(int id) {
    if (id < 0 || id > RES_MAX_ITEM_ID) {
        return NULL;
    }
    return items[id].name ? &items[id] : NULL;
}

const ryl_monster *res_monster(int id) {
    if (id < 0 || id > RES_MAX_MONSTER_ID) {
        return NULL;
    }
    return monsters[id].name ? &monsters[id] : NULL;
}

const ryl_spell *res_spell(const char *class_name, int slot) {
    if (slot < 1 || slot > RES_MAX_SPELL_SLOT) {
        return NULL;
    }
    int cid = pm_class_id(class_name);
    const ryl_spell *tab;
    if (cid == PC_PRIEST) {
        tab = priest_spells;
    } else if (cid == PC_MAGE) {
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

const char *res_pronoun(int sex, int xx) {
    if (sex < 1 || sex > 2 || xx < 1 || xx > 3) {
        return "";
    }
    return pronoun_tab[sex][xx] ? pronoun_tab[sex][xx] : "";
}

const char *res_town_name(int twn) {
    if (twn < 1 || twn > 4 || !towns[twn].name) {
        return "";
    }
    return towns[twn].name;
}

int32_t res_town_x(int twn) {
    if (twn < 1 || twn > 4 || !towns[twn].name) {
        return 0;
    }
    return towns[twn].x;
}

int32_t res_town_y(int twn) {
    if (twn < 1 || twn > 4 || !towns[twn].name) {
        return 0;
    }
    return towns[twn].y;
}

int32_t res_town_at(int x, int y) {
    for (int i = 1; i <= 4; i++) {
        if (towns[i].name && towns[i].x == x && towns[i].y == y) {
            return i;
        }
    }
    return 0;
}

/* Read an optional int field with a default. */
static int32_t rn_int(const node *obj, const char *key, int32_t dflt) {
    const node *n = obj_get(obj, key);
    return (n && n->kind == N_INT) ? n->u.iv : dflt;
}

/* Internal: number of values in an enum table by id (the raw array
   length, so res_enum(id, n - 1) is the highest valid index). 0 if the
   enum wasn't declared. Used to derive iteration bounds from data. */
static size_t enum_count(const char *id) {
    for (size_t i = 0; i < n_enums; i++) {
        if (strcmp(enums[i].id, id) == 0) {
            return enums[i].n_values;
        }
    }
    return 0;
}

/* Highest valid NPC slot id. enums.name has a sentinel at index 0 and
   one name per populated slot thereafter, so max_slot = count - 1. The
   state.npc[] array is oversized to hold this range plus a guard. */
static int npc_slot_max(void) {
    size_t n = enum_count("name");
    return n > 0 ? (int)(n - 1) : 0;
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
    ryl_log("A new campaign starts. Baron Ydarg awaits...\n");

    const node *root = world_tree;
    const node *npcs = root ? obj_get(root, "npcs") : NULL;
    if (!npcs || npcs->kind != N_OBJ) {
        /* Without a spec there's nothing to realize. Keep state.npc[]
           zeroed from static init; step 2 reader will see empty slots. */
        tx("%c", 12);
        return;
    }

    /* Capacity-capped draw (port-plan §15.4). Bins = wilderness (0) +
       each declared town (1..n_towns) = T. N is the count of random
       entries in the spec (those without a declared `towne`). cap =
       ceil(N/T) + npc_slack guarantees total capacity T*cap >= N, so
       the linear probe below always finds a free bin. With today's
       N=23, T=5, slack=1 => cap=6 per bin. Both T and slot_max come
       from the folded world tree — towns: and enums.name respectively. */
    int T = 1 + (int)n_towns;
    int slot_max = npc_slot_max();
    int N = 0;
    for (size_t i = 0; i < npcs->u.obj.n; i++) {
        const node *rec = npcs->u.obj.items[i].val;
        if (!rec || rec->kind != N_OBJ) {
            continue;
        }
        int slot = rn_int(rec, "slot", 0);
        if (slot < 1 || slot > slot_max) {
            continue;
        }
        if (!obj_get(rec, "towne")) {
            N++;
        }
    }
    int cap = (N + T - 1) / T + npc_slack;
    int counts[sizeof towns / sizeof towns[0]] = {0};

    for (size_t i = 0; i < npcs->u.obj.n; i++) {
        const node *rec = npcs->u.obj.items[i].val;
        if (!rec || rec->kind != N_OBJ) {
            continue;
        }
        int slot = rn_int(rec, "slot", 0);
        if (slot < 1 || slot > slot_max) {
            continue;
        }
        struct person *n = &state.npc[slot];
        n->nr = slot;
        n->job = rn_enum(rec, "job", "jobname", 0);

        const node *towne_node = obj_get(rec, "towne");
        if (towne_node) {
            /* FIXED: declared slot. Apply job/towne/bl/x/y verbatim. */
            n->towne    = rn_enum(rec, "towne",    "towne",    0);
            n->building = rn_enum(rec, "building", "building", 0);
            n->x        = rn_int (rec, "x", 0);
            n->y        = rn_int (rec, "y", 0);
        } else {
            /* RANDOM: realize placement now. Matches the legacy
               place_npcs() roll pattern but with a capacity cap on the
               towne draw — roll, then linear-probe to the next eligible
               bin on overflow. Building (1..2) and wilderness (x,y)
               rolls are untouched; the collision loop's accept predicate
               (ut > 1) is preserved verbatim from person.c:64-76. */
            ryl_delay(600);
            int twn = ryl_random(0, T - 1);
            while (counts[twn] >= cap) {
                twn = (twn + 1) % T;
            }
            counts[twn]++;
            n->towne = twn;
            if (n->towne) {
                n->building = ryl_random(0, 1) + 1;
                n->x  = 0;
                n->y  = 0;
            } else {
                n->building = 0;
                while (1) {
                    n->x = ryl_random(8, 57);
                    n->y = ryl_random(8, 27);
                    if (n->x == 48) {
                        n->x++;
                    }
                    int ut = 0;
                    for (int x = 1; x < slot + 1; x++) {
                        if (state.npc[x].x == n->x && state.npc[x].y == n->y) {
                            ut++;
                        }
                    }
                    char tile = state.map[n->y][n->x];
                    if (tile != '0' && tile != '1' && tile != '2' && tile != 'm' && tile != 'w') {
                        ut++;
                    }
                    if (ut > 1) {
                        break;
                    }
                }
            }
            tx(".");
        }
    }
    tx("%c", 12);

    /* Zero every NPC's visited[] grid — fresh realm means no party
       has met anyone yet. */
    for (int i = 1; i <= slot_max; i++) {
        memset(state.npc[i].visited, 0, sizeof state.npc[i].visited);
    }

    /* Mirror-write npc.dat so the legacy reader in load() finds the
       realized state. §15 step 5 drops this alongside realm.dat. */
    char path[512];
    FILE *fh;
    ryl_state_path("npc.dat", path, sizeof path);
    if (!(fh = fopen(path, "wb"))) {
        cleanexit();
    }
    for (int i = 1; i <= slot_max; i++) {
        fwrite(&state.npc[i], sizeof(state.npc[i]), 1, fh);
    }
    fclose(fh);
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
            int cid = pm_class_id(el->u.sv);
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

static void fold_items_section(const node *section, const char *path) {
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
        fold_items_section(items_node->u.obj.items[s].val, path);
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
        fprintf(stderr, "res: oom\n");
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
   can still render name/base_cost. Effect dispatch:
     - `do` present        -> SE_NONE; battle.c's res_spell_node path
                              runs eval_effect on the body instead of
                              reaching the switch.
     - `legacy_effect` set -> se_from_legacy_effect() picks the SE_*
                              the remaining switch cases fire on. */
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
        int cid = pm_class_id(cname);
        ryl_spell *tab = NULL;
        if (cid == PC_PRIEST) {
            tab = priest_spells;
        } else if (cid == PC_MAGE) {
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
            const node *leg_n = obj_get(spell, "legacy_effect");
            if (leg_n) {
                if (leg_n->kind != N_STR) {
                    res_die(path, leg_n->line, "spell: legacy_effect must be string");
                }
                int32_t se = se_from_legacy_effect(leg_n->u.sv);
                if (se < 0) {
                    res_die(path, leg_n->line, "spell: unknown legacy_effect");
                }
                s->effect = se;
            } else {
                s->effect = SE_NONE;
            }
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
    }
}

/* -------- pronoun subtree --------
   Two fixed keys, "male" and "female", map to sex indices 1 and 2 that
   the legacy res_pronoun() API already uses. */
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
        int sex;
        if (strcmp(key, "male") == 0) {
            sex = 1;
        } else if (strcmp(key, "female") == 0) {
            sex = 2;
        } else {
            res_die(path, rec->line, "pronoun: key must be 'male' or 'female'");
        }
        if (rec->kind != N_OBJ) {
            res_die(path, rec->line, "pronoun: entry must be object");
        }
        const node *subj = obj_get(rec, "subject");
        const node *obj_n = obj_get(rec, "object");
        const node *poss = obj_get(rec, "possessive");
        free(pronoun_tab[sex][1]);
        pronoun_tab[sex][1] = strdup((subj && subj->kind == N_STR) ? subj->u.sv : "");
        free(pronoun_tab[sex][2]);
        pronoun_tab[sex][2] = strdup((obj_n && obj_n->kind == N_STR) ? obj_n->u.sv : "");
        free(pronoun_tab[sex][3]);
        pronoun_tab[sex][3] = strdup((poss && poss->kind == N_STR) ? poss->u.sv : "");
    }
}

/* -------- towns subtree --------
   Array indexed by twn (slot 0 = null wilderness; 1..4 = towns). Each
   record has a `name` string and `x` / `y` integer coords. */
static void fold_towns(const node *towns_node, const char *path) {
    if (!towns_node) {
        return;
    }
    if (towns_node->kind != N_ARR) {
        res_die(path, towns_node->line, "towns: expected array");
    }
    size_t cap = sizeof towns / sizeof towns[0];
    if (towns_node->u.arr.n > cap) {
        res_die(path, towns_node->line, "towns: too many entries");
    }
    size_t n = towns_node->u.arr.n;
    n_towns = n > 0 ? n - 1 : 0;
    for (size_t i = 1; i < n; i++) {
        const node *rec = towns_node->u.arr.items[i];
        if (!rec || rec->kind == N_NULL) {
            continue;
        }
        if (rec->kind != N_OBJ) {
            res_die(path, rec->line, "towns: entry must be object or null");
        }
        const node *name = obj_get(rec, "name");
        const node *x    = obj_get(rec, "x");
        const node *y    = obj_get(rec, "y");
        if (!name || name->kind != N_STR) {
            res_die(path, rec->line, "towns: name string required");
        }
        if (!x || x->kind != N_INT || !y || y->kind != N_INT) {
            res_die(path, rec->line, "towns: x/y integer required");
        }
        towns[i].name = strdup(name->u.sv);
        towns[i].x = x->u.iv;
        towns[i].y = y->u.iv;
    }
}

static void reset_towns(void) {
    size_t cap = sizeof towns / sizeof towns[0];
    for (size_t i = 0; i < cap; i++) {
        free(towns[i].name);
        towns[i].name = NULL;
        towns[i].x = 0;
        towns[i].y = 0;
    }
    n_towns = 0;
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
}

static void reset_limits(void) {
    npc_slack = 1;
}

/* -------- enums subtree --------
   `{ jobname: [...], name: [...] }` — key is the enum id, value is the
   string table res_enum(id, index) indexes into. */
static void fold_enums(const node *enums_node, const char *path) {
    if (!enums_node) {
        return;
    }
    if (enums_node->kind != N_OBJ) {
        res_die(path, enums_node->line, "enums: expected object");
    }
    for (size_t i = 0; i < enums_node->u.obj.n; i++) {
        const char *id = enums_node->u.obj.items[i].key;
        const node *arr = enums_node->u.obj.items[i].val;
        if (arr->kind != N_ARR) {
            res_die(path, arr->line, "enums: value must be array");
        }
        enum_rec *grown = realloc(enums, (n_enums + 1) * sizeof *enums);
        if (!grown) {
            res_die(path, arr->line, "oom");
        }
        enums = grown;
        enum_rec *r = &enums[n_enums++];
        r->id = strdup(id);
        r->n_values = arr->u.arr.n;
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
    fold_spells(obj_get(root, "classes"), obj_get(root, "spells"), path);
    fold_terrain(obj_get(root, "terrain"), path);
    fold_pronoun(obj_get(root, "pronoun"), path);
    fold_towns(obj_get(root, "towns"), path);
    fold_limits(obj_get(root, "limits"), path);
    fold_enums(obj_get(root, "enums"), path);
    fold_texts(obj_get(root, "texts"), path);

    /* Walk every effect / cond / expr array in the tree and rewrite path
       strings (e.g. "caster.hp", "$dmg") in place to N_PATH. After this,
       the evaluator hot path does zero strcmp on actor/field names. */
    compile_world_tree(root, path);
    world_tree = root;
    texts_sort();

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

/* Kept #if 0: originally added as the evaluator's world-tree + flag-value
   accessors. The evaluator lookups ended up routed through different
   paths, but these are intentionally preserved for future consumers
   (e.g. a second-pass flag lookup that wants "" on out-of-range). */
#if 0
static const void *res_world(void) {
    return world_tree;
}

static const char *res_flag_value(const char *id, int index) {
    for (size_t i = 0; i < n_flags_tab; i++) {
        if (strcmp(flags_tab[i].id, id) == 0) {
            if (index < 0 || (size_t)index >= flags_tab[i].n_values) {
                return "";
            }
            return flags_tab[i].values[index];
        }
    }
    return "";
}
#endif

const void *res_spell_node(const char *class_id, int slot) {
    const node *root = world_tree;
    if (!root) {
        return NULL;
    }
    const node *classes = obj_get(root, "classes");
    if (!classes) {
        return NULL;
    }
    const node *cls = obj_get(classes, class_id);
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
            free(enums[i].values[j]);
        }
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
        int cid = pm_class_id(cname);
        ryl_spell *tab;
        if (cid == PC_PRIEST) {
            tab = priest_spells;
        } else if (cid == PC_MAGE) {
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
            tab[slot].effect = SE_NONE;
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
        color_tab[g] = 0;
        color_tab_set[g] = 0;
    }
}

/* fold_pronoun always strdups all three grammatical forms (subject /
   object / possessive) for every sex it sees, defaulting missing ones
   to "". So the teardown must visit all three slots per sex present in
   the parse tree — iterating `rec->u.obj.n` would miss "" defaults
   whose key never appeared in JSON5. The 1..3 range is a schema
   invariant (three grammatical cases), not a memory-bound magic
   number. */
static void reset_pronoun_from_tree(const node *pronoun_node) {
    if (!pronoun_node || pronoun_node->kind != N_OBJ) {
        return;
    }
    for (size_t i = 0; i < pronoun_node->u.obj.n; i++) {
        const char *key = pronoun_node->u.obj.items[i].key;
        int sex;
        if (strcmp(key, "male") == 0) {
            sex = 1;
        } else if (strcmp(key, "female") == 0) {
            sex = 2;
        } else {
            continue;
        }
        for (int xx = 1; xx <= 3; xx++) {
            free(pronoun_tab[sex][xx]);
            pronoun_tab[sex][xx] = NULL;
        }
    }
}

void res_shutdown(void) {
    /* Order matters: legacy tables first (they walk world_tree to drive
       their loops); world_tree itself last. */
    if (world_tree) {
        const node *root = world_tree;
        reset_items_from_tree(obj_get(root, "items"));
        reset_monsters_from_tree(obj_get(root, "monsters"));
        reset_spells_from_tree(obj_get(root, "classes"), obj_get(root, "spells"));
        reset_terrain_from_tree(obj_get(root, "terrain"));
        reset_pronoun_from_tree(obj_get(root, "pronoun"));
    }
    reset_flags_tab();
    reset_enums();
    reset_texts();
    reset_towns();
    reset_limits();
    node_free(world_tree);
    world_tree = NULL;
    color_default = ANSI_FG_CYAN;
}
