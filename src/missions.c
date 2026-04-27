#include "res.h"
#include "compat.h"
#include "dungeon.h"
#include "gfx.h"
#include "inventory.h"
#include "missions.h"
#include "party.h"
#include "primitives.h"
#include "script.h"
#include "state.h"
#include "wld.h"
#include "subs.h"

#include <ctype.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

/* §17 / A.4 slice A: map-trigger dispatcher. The historical mission_
   control() was 16 ad-hoc `if (flag[N]==V && <guard>) { tx(...); ... }`
   blocks. They decompose into a small number of guard shapes plus a
   linear / menu_branch script body; this dispatcher keeps the guard
   evaluation in C (where arithmetic and `state.dx[i]`/`state.dy[i]`
   lookups live) and hands the UI off to play_script.

   One block still isn't expressible in the DSL: the gem-altar at
   flag[13]==1, which branches on a runtime inventory count (>=5 gems
   of ids 39..41). It lives inline below as the one residual block
   pending a DSL widening. */

enum mission_guard_kind {
    MG_POS,         /* x == a && y == b                                */
    MG_DUNGEON_IDX, /* x == state.dx[a] && y == state.dy[a]            */
    MG_TERRAIN,     /* state.map[y][x] == (char)a                      */
    MG_CHANCE,      /* ryl_chance(a) — rolled after the flag guard     */
};

enum mission_bind_kind {
    MB_NPC_NAME = 1, /* res_npc_name(res_npc_role(b->npc_role))      */
    MB_PLAYER_NAME,  /* state.p[cpl].name[b->arg] (party-slot name)  */
    MB_DX_STR,       /* state.dx[b->arg] stringified                 */
    MB_DY_STR,       /* state.dy[b->arg] stringified                 */
};

typedef struct {
    const char *name; /* placeholder ident in the script */
    enum mission_bind_kind kind;
    int32_t arg;          /* dungeon index / player-member slot */
    const char *npc_role; /* set for MB_NPC_NAME; resolved via
                                res_npc_role so C doesn't name
                                specific characters */
} mission_binding;

enum mission_post_op {
    POST_NONE = 0,
    POST_SET_JAZZYFOOT, /* Jazzy-foot pickup sets state.p[cpl].jazzyfoot = 1 */
};

typedef struct {
    const char *flag_tag;   /* world.json5 flags: key (e.g. "silver_key") */
    const char *when_state; /* values[] name in that flag's shard        */
    enum mission_guard_kind kind;
    int32_t a, b;
    const char *script;
    uint8_t returns_on_skip;
    enum mission_post_op post;
    const mission_binding *binds;
    size_t nbinds;
} mission_trigger;

#define MAX_BINDINGS   4
#define BIND_ZONE_SIZE 48

/* Triggers are tried in the historical order; first one whose flag /
   guard passes fires. `returns_on_skip` reproduces the original's
   `return 1` after blocks whose L-menu unambiguously ended the
   mission-control turn (blocks 9, 11, 12, 14, 16) — the other blocks
   fell through to the random-event roll in random_wilderness(). */
static const mission_trigger mission_triggers[] = {
    /* Block 1: Erethor temple at (6,20). */
    {.flag_tag = "erethor.lute", .when_state = "rumour_told", .kind = MG_POS, .a = 6, .b = 20, .script = "missions.erethor_entry"},

    /* Block 2: Husband cave at (42,23). Binds `name19` to the
       peasant-husband NPC's display name. */
    {.flag_tag = "peasant_wife", .when_state = "accepted", .kind = MG_POS, .a = 42, .b = 23, .script = "missions.husband_cave", .binds = (const mission_binding[]){{.name = "name19", .kind = MB_NPC_NAME, .npc_role = "peasant_husband"}}, .nbinds = 1},

    /* Block 3: Bandit cargo. 9% chance gate. */
    {.flag_tag = "garth.bandits", .when_state = "accepted", .kind = MG_CHANCE, .a = 9, .script = "missions.bandits"},

    /* Block 4: Troll tooth. 9% chance gate. */
    {.flag_tag = "hermit.troll_tooth", .when_state = "accepted", .kind = MG_CHANCE, .a = 9, .script = "missions.troll_tooth"},

    /* Block 4b: Hermit-quest dungeon at dx[1],dy[1]. */
    {.flag_tag = "hermit.troll_tooth", .when_state = "delivered", .kind = MG_DUNGEON_IDX, .a = 1, .script = "missions.hermit_dungeon"},

    /* Block 6: Kobold village at dx[3],dy[3]. */
    {.flag_tag = "mastercrown", .when_state = "task1_assigned", .kind = MG_DUNGEON_IDX, .a = 3, .script = "missions.kobold_village"},

    /* Block 7: Skullweed on terrain 't'. */
    {.flag_tag = "mastercrown", .when_state = "task2_assigned", .kind = MG_TERRAIN, .a = 't', .script = "missions.skullweed"},

    /* Block 8: Borin first visit at dx[4],dy[4]. Binds {dx5}/{dy5}
       for the crypt-location screen + journal. */
    {.flag_tag = "mastercrown", .when_state = "task3_assigned", .kind = MG_DUNGEON_IDX, .a = 4, .script = "missions.borin_meet", .binds = (const mission_binding[]){
                                                                                                                                     {.name = "dx5", .kind = MB_DX_STR, .arg = 5},
                                                                                                                                     {.name = "dy5", .kind = MB_DY_STR, .arg = 5},
                                                                                                                                 },
     .nbinds = 2},

    /* Block 9: Nolrim crypt at dx[5],dy[5]. */
    {.flag_tag = "mastercrown", .when_state = "borin_met", .kind = MG_DUNGEON_IDX, .a = 5, .script = "missions.nolrim_crypt", .returns_on_skip = 1},

    /* Block 10: Borin gift at dx[4],dy[4]. */
    {.flag_tag = "mastercrown", .when_state = "dagger_taken", .kind = MG_DUNGEON_IDX, .a = 4, .script = "missions.borin_gift"},

    /* Block 11: Master crown dungeon at dx[6],dy[6]. */
    {.flag_tag = "mastercrown", .when_state = "crown_told", .kind = MG_DUNGEON_IDX, .a = 6, .script = "missions.master_crown", .returns_on_skip = 1},

    /* Block 12: Ogre cave at dx[7],dy[7]. Binds {p2_name} (player
       member 2's name, used in the note-found screen). */
    {.flag_tag = "jazzy_ogre", .when_state = "accepted", .kind = MG_DUNGEON_IDX, .a = 7, .script = "missions.ogre_cave", .returns_on_skip = 1, .binds = (const mission_binding[]){
                                                                                                                                                   {.name = "p2_name", .kind = MB_PLAYER_NAME, .arg = 2},
                                                                                                                                               },
     .nbinds = 1},

    /* Block 13: Foot of Jazzy altar on terrain 'f'. Post-action
       sets state.p[cpl].jazzyfoot = 1 — not yet a DSL primitive,
       applied in C after the script returns. */
    {.flag_tag = "jazzy_ogre", .when_state = "ogres_cleared", .kind = MG_TERRAIN, .a = 'f', .script = "missions.jazzy_foot", .post = POST_SET_JAZZYFOOT},

    /* Block 14: Silver key dungeon at dx[8],dy[8]. */
    {.flag_tag = "silver_key", .when_state = "map_bought", .kind = MG_DUNGEON_IDX, .a = 8, .script = "missions.silver_key", .returns_on_skip = 1},

    /* Block 15: Hobgoblin caravan. 9% chance + binds {dx9}/{dy9}
       for the interrogate screen + journal. */
    {.flag_tag = "silver_key", .when_state = "smith_confessed", .kind = MG_CHANCE, .a = 9, .script = "missions.hobgoblin_caravan", .binds = (const mission_binding[]){
                                                                                                                                       {.name = "dx9", .kind = MB_DX_STR, .arg = 9},
                                                                                                                                       {.name = "dy9", .kind = MB_DY_STR, .arg = 9},
                                                                                                                                   },
     .nbinds = 2},

    /* Block 16: Hobgoblin cave (pit) at dx[9],dy[9]. */
    {.flag_tag = "silver_key", .when_state = "cave_located", .kind = MG_DUNGEON_IDX, .a = 9, .script = "missions.hobgoblin_cave", .returns_on_skip = 1},
};

static int guard_matches(const mission_trigger *t) {
    int32_t x = state.p[state.cpl].x;
    int32_t y = state.p[state.cpl].y;
    switch (t->kind) {
        case MG_POS:
            return x == t->a && y == t->b;
        case MG_DUNGEON_IDX:
            return x == wld_location_x(t->a) && y == wld_location_y(t->a);
        case MG_TERRAIN:
            return state.map[y][x] == (char)t->a;
        case MG_CHANCE:
            return ryl_chance((int)t->a);
    }
    return 0;
}

static void resolve_bindings(const mission_trigger *t, ryl_bind *out,
                             char *scratch) {
    for (size_t i = 0; i < t->nbinds; i++) {
        const mission_binding *b = &t->binds[i];
        char *zone = scratch + i * BIND_ZONE_SIZE;
        out[i].name = b->name;
        switch (b->kind) {
            case MB_NPC_NAME:
                out[i].value = res_npc_name(res_npc_role(b->npc_role));
                break;
            case MB_PLAYER_NAME:
                out[i].value = state.p[state.cpl].name[b->arg];
                break;
            case MB_DX_STR:
                snprintf(zone, BIND_ZONE_SIZE, "%d", wld_location_x(b->arg));
                out[i].value = zone;
                break;
            case MB_DY_STR:
                snprintf(zone, BIND_ZONE_SIZE, "%d", wld_location_y(b->arg));
                out[i].value = zone;
                break;
        }
    }
    out[t->nbinds] = (ryl_bind){0};
}

static void apply_post(enum mission_post_op op) {
    switch (op) {
        case POST_NONE:
            break;
        case POST_SET_JAZZYFOOT:
            party_award_special(PS_JAZZYFOOT);
            break;
    }
}

/* Returns 1 when a trigger fired and asked the caller to short-circuit
   (returns_on_skip), 0 when a trigger fired fall-through, -1 when no
   trigger matched. */
static int dispatch_triggers(void) {
    for (size_t i = 0; i < sizeof mission_triggers / sizeof *mission_triggers; i++) {
        const mission_trigger *t = &mission_triggers[i];
        if (!pa_flag_is(t->flag_tag, t->when_state)) {
            continue;
        }
        if (!guard_matches(t)) {
            continue;
        }
        ryl_bind binds[MAX_BINDINGS + 1];
        char scratch[MAX_BINDINGS * BIND_ZONE_SIZE];
        const ryl_bind *pass = NULL;
        if (t->nbinds > 0) {
            resolve_bindings(t, binds, scratch);
            pass = binds;
        }
        play_script(t->script, pass);
        apply_post(t->post);
        return t->returns_on_skip ? 1 : 0;
    }
    return -1;
}

uint8_t mission_control() {
    int32_t gems;
    char c;

    int handled = dispatch_triggers();
    if (handled == 1) {
        return 1;
    }
    if (handled == 0) {
        /* A trigger fired but asked to fall through. The original
           blocks are all keyed on disjoint flag+position guards so
           no two fire simultaneously; nothing remains to try below
           except the gem-altar residual. */
    }

    /* Block 5: temple of Thun gem altar. Inventory-count check
       (>=5 gems of ids 39..41) isn't expressible in the DSL —
       single remaining inline block. Text records live under
       missions.thun_temple in data/world/texts/missions.json5;
       gem_prompt bundles the (Y)es/(L)eave menu inline. */
    if (pa_flag_is("bracelets.thun", "map_bought") && pa_x() == wld_location_x(2) && pa_y() == wld_location_y(2)) {
        clw();
        tx0(res_text("missions.thun_temple.temple_arrival"));
        ryl_pause();
        clw();
        tx0(res_text("missions.thun_temple.altar_inscription"));
        ryl_pause();
        clw();
        tx0(res_text("missions.thun_temple.gem_prompt"));
        c = key();
        c = toupper(c);
        if (c == 'L') {
            clw();
            return 1;
        }
        gems = inv_count_in_range(39, 41);
        if (gems < 5) {
            clw();
            tx0(res_text("missions.thun_temple.no_gems"));
            ryl_pause();
            clw();
            return 1;
        } else {
            clw();
            tx0(res_text("missions.thun_temple.altar_opens"));
            ryl_pause();
            clw();
            dungeon(18, "thun_temple");
            clr_map_window();
            update_map();
        }
    }

    return 0;
}
