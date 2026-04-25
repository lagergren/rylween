#ifndef SPEAK_H__
#define SPEAK_H__

#include <stddef.h>
#include <stdint.h>

/* Dialogue with NPCs + the rumour/chitchat flag machinery. */

void speak(int32_t, int32_t);

/* §20 S8: per-NPC mission stage table. Loaded from
   data/world/scripts/speak_missions.json5 by fold_speak_missions
   (res.c) and consumed by speak.c::mission_dispatch. The four
   primitive bind kinds match the JSON5 shape; `arg` is a dungeon-
   location index for DX/DY kinds, `npc_role` is a string id resolved
   via res_npc_role at dispatch for NPC kinds. */
enum mission_bind_kind {
    BIND_NPC_NAME = 1, /* res_enum("name", res_npc_role(b->npc_role)) */
    BIND_NPC_LOC,      /* npc_loc_str(npc_for_role(b->npc_role), ...) */
    BIND_DX_STR,       /* state.dx[arg] stringified */
    BIND_DY_STR,       /* state.dy[arg] stringified */
};

typedef struct mission_cond_ {
    const char *flag_tag;   /* world.json5 flags: key */
    const char *when_state; /* values[] name in that flag's shard */
} mission_cond;

typedef struct mission_binding_ {
    const char *name; /* placeholder id in the script */
    enum mission_bind_kind kind;
    int32_t arg;          /* dungeon index (DX/DY) */
    const char *npc_role; /* set for BIND_NPC_NAME / BIND_NPC_LOC */
} mission_binding;

typedef struct mission_stage_ {
    const mission_cond *conds; /* AND-of-all; never NULL; nconds >= 1 */
    size_t nconds;
    const char *script;
    const mission_binding *binds; /* NULL iff nbinds == 0 */
    size_t nbinds;
    uint8_t pre_clw;
} mission_stage;

typedef struct mission_spec_ {
    const char *npc_role; /* resolved via res_npc_role at dispatch */
    const mission_stage *stages;
    size_t nstages;
} mission_spec;

#endif
