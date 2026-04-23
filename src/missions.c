#include "res.h"
#include "compat.h"
#include "dungeon.h"
#include "gfx.h"
#include "missions.h"
#include "primitives.h"
#include "script.h"
#include "state.h"
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
    MB_NPC_NAME = 1, /* res_enum("name", arg)                   */
    MB_PLAYER_NAME,  /* state.p[cpl].name[arg] (party-slot name)*/
    MB_DX_STR,       /* state.dx[arg] stringified               */
    MB_DY_STR,       /* state.dy[arg] stringified               */
};

typedef struct {
    const char *name; /* placeholder ident in the script */
    enum mission_bind_kind kind;
    int32_t arg;
} mission_binding;

enum mission_post_op {
    POST_NONE = 0,
    POST_SET_JAZZYFOOT, /* Jazzy-foot pickup sets state.p[cpl].jazzyfoot = 1 */
};

typedef struct {
    int32_t flag;
    int32_t when;
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
   fell through to the random-event roll in slumpvis(). */
static const mission_trigger mission_triggers[] = {
    /* Block 1: Erethor temple at (6,20). Flag 8 == 1. */
    {.flag = 8, .when = 1, .kind = MG_POS, .a = 6, .b = 20, .script = "missions.erethor_entry"},

    /* Block 2: Husband cave at (42,23). Flag 19 == 1. Binds `name19`. */
    {.flag = 19, .when = 1, .kind = MG_POS, .a = 42, .b = 23, .script = "missions.husband_cave", .binds = (const mission_binding[]){{"name19", MB_NPC_NAME, 19}}, .nbinds = 1},

    /* Block 3: Bandit cargo. Flag 1 == 1 + 9% chance. */
    {.flag = 1, .when = 1, .kind = MG_CHANCE, .a = 9, .script = "missions.bandits"},

    /* Block 4: Troll tooth. Flag 33 == 1 + 9% chance. */
    {.flag = 33, .when = 1, .kind = MG_CHANCE, .a = 9, .script = "missions.troll_tooth"},

    /* Block 4b: Hermit-quest dungeon. Flag 33 == 3 at dx[1],dy[1]. */
    {.flag = 33, .when = 3, .kind = MG_DUNGEON_IDX, .a = 1, .script = "missions.hermit_dungeon"},

    /* Block 6: Kobold village. Flag 23 == 1 at dx[3],dy[3]. */
    {.flag = 23, .when = 1, .kind = MG_DUNGEON_IDX, .a = 3, .script = "missions.kobold_village"},

    /* Block 7: Skullweed on terrain 't'. Flag 23 == 3. */
    {.flag = 23, .when = 3, .kind = MG_TERRAIN, .a = 't', .script = "missions.skullweed"},

    /* Block 8: Borin first visit. Flag 23 == 5 at dx[4],dy[4]. Binds
       {dx5}/{dy5} for the crypt-location screen + journal. */
    {.flag = 23, .when = 5, .kind = MG_DUNGEON_IDX, .a = 4, .script = "missions.borin_meet", .binds = (const mission_binding[]){
                                                                                                 {"dx5", MB_DX_STR, 5},
                                                                                                 {"dy5", MB_DY_STR, 5},
                                                                                             },
     .nbinds = 2},

    /* Block 9: Nolrim crypt. Flag 23 == 6 at dx[5],dy[5].
       returns_on_skip: original `return 1` after L. */
    {.flag = 23, .when = 6, .kind = MG_DUNGEON_IDX, .a = 5, .script = "missions.nolrim_crypt", .returns_on_skip = 1},

    /* Block 10: Borin gift. Flag 23 == 7 at dx[4],dy[4]. */
    {.flag = 23, .when = 7, .kind = MG_DUNGEON_IDX, .a = 4, .script = "missions.borin_gift"},

    /* Block 11: Master crown dungeon. Flag 23 == 9 at dx[6],dy[6].
       returns_on_skip: original `return 1` after L. */
    {.flag = 23, .when = 9, .kind = MG_DUNGEON_IDX, .a = 6, .script = "missions.master_crown", .returns_on_skip = 1},

    /* Block 12: Ogre cave. Flag 22 == 1 at dx[7],dy[7]. Binds
       {p2_name} (player-slot 2's name, used in the 'note-found'
       screen). returns_on_skip: original `return 1` after L. */
    {.flag = 22, .when = 1, .kind = MG_DUNGEON_IDX, .a = 7, .script = "missions.ogre_cave", .returns_on_skip = 1, .binds = (const mission_binding[]){
                                                                                                                      {"p2_name", MB_PLAYER_NAME, 2},
                                                                                                                  },
     .nbinds = 1},

    /* Block 13: Foot of Jazzy altar on terrain 'f'. Flag 22 == 2.
       Post-action sets state.p[cpl].jazzyfoot = 1 — not yet a DSL
       primitive, applied in C after the script returns. */
    {.flag = 22, .when = 2, .kind = MG_TERRAIN, .a = 'f', .script = "missions.jazzy_foot", .post = POST_SET_JAZZYFOOT},

    /* Block 14: Silver key dungeon. Flag 6 == 1 at dx[8],dy[8].
       returns_on_skip: original `return 1` after L. */
    {.flag = 6, .when = 1, .kind = MG_DUNGEON_IDX, .a = 8, .script = "missions.silver_key", .returns_on_skip = 1},

    /* Block 15: Hobgoblin caravan. Flag 6 == 4 + 9% chance. Binds
       {dx9}/{dy9} for the interrogate screen + journal. */
    {.flag = 6, .when = 4, .kind = MG_CHANCE, .a = 9, .script = "missions.hobgoblin_caravan", .binds = (const mission_binding[]){
                                                                                                  {"dx9", MB_DX_STR, 9},
                                                                                                  {"dy9", MB_DY_STR, 9},
                                                                                              },
     .nbinds = 2},

    /* Block 16: Hobgoblin cave (pit). Flag 6 == 5 at dx[9],dy[9].
       returns_on_skip: original `return 1` after L. */
    {.flag = 6, .when = 5, .kind = MG_DUNGEON_IDX, .a = 9, .script = "missions.hobgoblin_cave", .returns_on_skip = 1},
};

static int guard_matches(const mission_trigger *t) {
    int32_t x = state.p[state.cpl].x;
    int32_t y = state.p[state.cpl].y;
    switch (t->kind) {
        case MG_POS:
            return x == t->a && y == t->b;
        case MG_DUNGEON_IDX:
            return x == state.dx[t->a] && y == state.dy[t->a];
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
                out[i].value = res_enum("name", b->arg);
                break;
            case MB_PLAYER_NAME:
                out[i].value = state.p[state.cpl].name[b->arg];
                break;
            case MB_DX_STR:
                snprintf(zone, BIND_ZONE_SIZE, "%d", state.dx[b->arg]);
                out[i].value = zone;
                break;
            case MB_DY_STR:
                snprintf(zone, BIND_ZONE_SIZE, "%d", state.dy[b->arg]);
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
            state.p[state.cpl].jazzyfoot = 1;
            break;
    }
}

/* Returns 1 when a trigger fired and asked the caller to short-circuit
   (returns_on_skip), 0 when a trigger fired fall-through, -1 when no
   trigger matched. */
static int dispatch_triggers(void) {
    const unsigned char *flag = state.p[state.cpl].flag;
    for (size_t i = 0; i < sizeof mission_triggers / sizeof *mission_triggers; i++) {
        const mission_trigger *t = &mission_triggers[i];
        if (flag[t->flag] != (unsigned char)t->when) {
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
    int32_t i, gems;
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
       single remaining inline block. */
    if (state.p[state.cpl].flag[13] == 1 && state.p[state.cpl].x == state.dx[2] && state.p[state.cpl].y == state.dy[2]) {
        clw();
        tx("You stand before the\r\n");
        tx("ancient temple of Thun.\r\n");
        tx("Around you are the ruins\r\n");
        tx("of the once proud city\r\n");
        tx("of Thun...\r\n\r\n");
        ryl_pause();
        clw();
        tx("You see an altar\r\n");
        tx("with five sockets before\r\n");
        tx("you. Each is roughly\r\n");
        tx("gem-sized. An inscription\r\n");
        tx("says: 'Insert the gems\r\n");
        tx("and your path shall open'\r\n\r\n");
        ryl_pause();
        clw();
        tx("Do you want to put\r\n");
        tx("five gems in the sockets?\r\n\r\n");
        tx0(res_text("common.yes"));
        tx("(L)eave\r\n");
        c = key();
        c = toupper(c);
        if (c == 'L') {
            clw();
            return 1;
        }
        for (i = 1, gems = 0; i < 17; i++) {
            if (state.p[state.cpl].inventory[i] >= 39 && state.p[state.cpl].inventory[i] <= 41) {
                gems++;
            }
        }
        if (gems < 5) {
            clw();
            tx("You don't have five\r\n");
            tx("different gems.\r\n\r\n");
            ryl_pause();
            clw();
            return 1;
        } else {
            clw();
            tx("With a resounding 'CRUNG'\r\n");
            tx("the altar slides away\r\n");
            tx("to reveal a dark pit.\r\n");
            tx("You enter...\r\n\r\n");
            ryl_pause();
            clw();
            dungeon(18, 2);
            clr_map_window();
            update_map();
        }
    }

    return 0;
}
