#ifndef MISSIONS_H__
#define MISSIONS_H__

#include <stddef.h>
#include <stdint.h>

/* Mission dispatcher — wilderness step-poll path + E-key path, both
   feeding the same data-driven trigger table (data/world/missions.
   json5). mission_control fires step-poll triggers (MW_STEP);
   mission_try_at_enter fires the towne.c::enter (E-key) triggers
   (MW_ENTER_KEY). The trigger table itself is populated by
   res.c::fold_missions at world-load time and walked through
   res_missions_count() / res_mission_at(); the struct shape lives in
   this header so the loader and the dispatcher agree on the layout. */

/* Guard predicate kind. Matched against the JSON5 `kind` slug:
     'pos'         -> MG_POS
     'dungeon_idx' -> MG_DUNGEON_IDX
     'terrain'     -> MG_TERRAIN
     'chance'      -> MG_CHANCE
     'tile_at_pos' -> MG_TILE_AT_POS
     'special_eq'  -> MG_SPECIAL_EQ */
enum mission_guard_kind {
    MG_POS,         /* x == a && y == b                                */
    MG_DUNGEON_IDX, /* x == state.dx[a] && y == state.dy[a]            */
    MG_TERRAIN,     /* wld_tile_at(x, y) == (char)a                    */
    MG_CHANCE,      /* ryl_chance(a) — rolled after the flag guard     */
    MG_TILE_AT_POS, /* (x, y) == res_int_path(`<pos_path>.x|.y`) AND
                       res_terrain_class(wld_tile_at(x,y)) == tile_class */
    MG_SPECIAL_EQ,  /* MG_TILE_AT_POS + pa_special(special_tag) == a.
                       Resolves the slug to PS_* via inv_special_id_for_tag
                       so castle-entry triggers can split on silverkey
                       state in data instead of the C body. */
};

/* Where the trigger fires from. Step-poll runs from random_wilderness
   on every wilderness move; enter-key runs from towne.c::enter (E
   key) on a settlement tile. New triggers default to MW_STEP. */
enum mission_where {
    MW_STEP = 0,
    MW_ENTER_KEY,
};

/* What the trigger does once flag + guard match. MA_SCRIPT plays a
   DSL script (existing path); MA_C_FN calls a C function with no
   bindings (used for legacy C scenes that haven't been lifted to
   the DSL — Ydarg's castle gate is the first). */
enum mission_action_kind {
    MA_SCRIPT = 0,
    MA_C_FN,
};

typedef void (*mission_c_fn)(void);

enum mission_bind_kind {
    MB_NPC_NAME = 1, /* res_npc_name(res_npc_role(b->npc_role))      */
    MB_PLAYER_NAME,  /* state.p[cpl].name[b->arg] (party-slot name)  */
    MB_DX_STR,       /* state.dx[b->arg] stringified                 */
    MB_DY_STR,       /* state.dy[b->arg] stringified                 */
};

typedef struct mission_binding {
    char *name; /* placeholder ident in the script (heap-owned) */
    enum mission_bind_kind kind;
    int32_t arg;    /* dungeon index / player-member slot */
    char *npc_role; /* set for MB_NPC_NAME; resolved via
                        res_npc_role so C doesn't name
                        specific characters. Heap-owned. */
} mission_binding;

enum mission_post_op {
    POST_NONE = 0,
    POST_SET_JAZZYFOOT, /* Jazzy-foot pickup sets state.p[cpl].jazzyfoot = 1 */
};

typedef struct mission_trigger {
    char *flag_tag;   /* world.json5 flags: key. NULL = no flag gate. */
    char *when_state; /* values[] name in that flag's shard           */
    enum mission_where where;
    enum mission_guard_kind kind;
    int32_t a, b;
    char *tile_class;  /* MG_TILE_AT_POS / MG_SPECIAL_EQ: terrain class to match */
    char *pos_path;    /* MG_TILE_AT_POS / MG_SPECIAL_EQ: dotted path to a
                         {x,y} subtree under the world tree. Resolved
                         via res_int_path("<pos_path>.x|.y") at guard
                         time so the position lives in data, not C. */
    char *special_tag; /* MG_SPECIAL_EQ: special-tag slug ("silverkey"
                          etc.) — resolved to PS_* via
                          inv_special_id_for_tag. Compared against
                          `a` field. */
    enum mission_action_kind action;
    char *script;    /* MA_SCRIPT only (heap-owned)        */
    mission_c_fn fn; /* MA_C_FN only — set at fold time by
                            mission_resolve_c_fn from the data slug */
    char *c_fn;      /* MA_C_FN only — original slug, kept for
                           diagnostics + reset cleanup           */
    uint8_t returns_on_skip;
    enum mission_post_op post;
    mission_binding *binds;
    size_t nbinds;
} mission_trigger;

/* Resolves a c_fn slug from data/world/missions.json5 to the C
   function that mission dispatch should call. Returns NULL on an
   unknown slug — the fold path treats that as a hard error. Today
   the only legal slug is "ydarg". */
mission_c_fn mission_resolve_c_fn(const char *slug);

uint8_t mission_control(void);
uint8_t mission_try_at_enter(void);

#endif
