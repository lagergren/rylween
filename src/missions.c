#include "ryl.h"
#include "res.h"
#include "battle.h"
#include "driv.h"
#include "dungeon.h"
#include "gfx.h"
#include "missions.h"
#include "primitives.h"
#include "script.h"
#include "subs.h"

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
    MB_NPC_NAME = 1, /* res_enum("name", arg)        */
    MB_DX_STR,       /* state.dx[arg] stringified    */
    MB_DY_STR,       /* state.dy[arg] stringified    */
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
   mission-control turn (blocks 12, 14, 16) — the other blocks fell
   through to the random-event roll in slumpvis(). */
static const mission_trigger mission_triggers[] = {
    /* Block 1: Erethor temple at (6,20). Flag 8 == 1. */
    {.flag = 8, .when = 1, .kind = MG_POS, .a = 6, .b = 20, .script = "missions.erethor_entry", .returns_on_skip = 0},

    /* Block 2: Husband cave at (42,23). Flag 19 == 1. Binds `name19`. */
    {.flag = 19, .when = 1, .kind = MG_POS, .a = 42, .b = 23, .script = "missions.husband_cave", .returns_on_skip = 0, .binds = (const mission_binding[]){{"name19", MB_NPC_NAME, 19}}, .nbinds = 1},

    /* Block 3: Bandit cargo. Flag 1 == 1 + 9% chance. */
    {.flag = 1, .when = 1, .kind = MG_CHANCE, .a = 9, .script = "missions.bandits", .returns_on_skip = 0},

    /* Block 4: Troll tooth. Flag 33 == 1 + 9% chance. */
    {.flag = 33, .when = 1, .kind = MG_CHANCE, .a = 9, .script = "missions.troll_tooth", .returns_on_skip = 0},

    /* Block 4b: Hermit-quest dungeon. Flag 33 == 3 at dx[1],dy[1]. */
    {.flag = 33, .when = 3, .kind = MG_DUNGEON_IDX, .a = 1, .script = "missions.hermit_dungeon", .returns_on_skip = 0},

    /* Block 7: Skullweed on terrain 't'. Flag 23 == 3. */
    {.flag = 23, .when = 3, .kind = MG_TERRAIN, .a = 't', .script = "missions.skullweed", .returns_on_skip = 0},

    /* Block 13: Foot of Jazzy altar on terrain 'f'. Flag 22 == 2.
       Post-action sets state.p[cpl].jazzyfoot = 1 — not yet a DSL
       primitive, applied in C after the script returns. */
    {.flag = 22, .when = 2, .kind = MG_TERRAIN, .a = 'f', .script = "missions.jazzy_foot", .returns_on_skip = 0, .post = POST_SET_JAZZYFOOT},
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
   (returns_on_skip), 0 otherwise. */
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
        /* Chance-guarded blocks roll once here (inside guard_matches);
           non-chance blocks fire unconditionally. Each script is
           responsible for its own opening clw() if it has one — the
           original blocks split on that (block 4 opens on encounter
           with no leading clw, everyone else clears first). */
        ryl_bind binds[MAX_BINDINGS + 1];
        char scratch[MAX_BINDINGS * BIND_ZONE_SIZE];
        const ryl_bind *pass = NULL;
        if (t->nbinds > 0) {
            resolve_bindings(t, binds, scratch);
            pass = binds;
        }
        play_script(t->script, pass);
        apply_post(t->post);
        if (t->returns_on_skip) {
            return 1;
        }
        return 0;
    }
    return -1;
}

uint8_t mission_control() {
    int32_t i, gems;
    char send[144];
    char c;

    int handled = dispatch_triggers();
    if (handled == 1) {
        return 1;
    }
    if (handled == 0) {
        /* A trigger fired but asked to fall through. The historical
           blocks could in theory chain (two triggers match in the
           same turn) — preserve that by continuing down this
           function rather than returning. */
    }

    /* ---------------------------------------------------------------
       Blocks not yet expressible in the DSL:

       flag[13]==1 gem altar (arithmetic: count inventory items in id
       range 39..41 and branch on >= 5).

       Blocks 6, 8, 9, 10, 11, 12, 14, 15, 16 still live here pending
       a later pass — they are all menu_branch shapes that the DSL
       can already express, but the text extraction is mechanical
       and was left for a follow-up slice to keep this commit
       focused on the dispatcher + non-menu triggers.
       --------------------------------------------------------------- */

    /* Block 5: temple of thun altar — 5-gem inventory check keeps
       this inline. */
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
    /******************************************************************/
    if (state.p[state.cpl].flag[23] == 1 && state.p[state.cpl].x == state.dx[3] && state.p[state.cpl].y == state.dy[3]) {
        clw();
        tx("The kobold village lies\r\n");
        tx("ahead. You can hear the\r\n");
        tx("noise from some kind of\r\n");
        tx("party even at a distance\r\n");
        tx("of 200 yards.\r\n\r\n");
        tx("(A)ttack now\r\n");
        tx("(L)eave\r\n");
        c = key();
        c = toupper(c);
        if (c == 'A') {
            clw();
            tx("You run screaming into\r\n");
            tx("a large celebration of\r\n");
            tx("some sort. There are\r\n");
            tx("about forty kobolds here.\r\n");
            tx("At first, they are very\r\n");
            tx("surprised, and you manage\r\n");
            tx("to slay many, before they\r\n");
            tx("attack...\r\n\r\n");
            ryl_pause();
            clw();
            encounter(15, 1);
            clw();
            tx("Piles of dead kobolds are\r\n");
            tx("lying all around. Still,\r\n");
            tx("they keep coming!\r\n\r\n");
            ryl_pause();
            encounter(15, 1);
            clw();
            tx("You have annihilated most\r\n");
            tx("of the village. You pause\r\n");
            tx("to wipe the blood of your\r\n");
            tx("weapons, when suddenly,\r\n");
            tx("some ORCS rush out to\r\n");
            tx("attack you from nowhere.\r\n\r\n");
            ryl_pause();
            encounter(2, 1);
            update_map();
            clw();
            tx("The entire village is\r\n");
            tx("wiped out. You snatch\r\n");
            tx("an ugly pendant from the\r\n");
            tx("head of the kobold chief\r\n");
            tx("to prove the bloody deed.\r\n\r\n");
            ryl_pause();
            clw();
            state.p[state.cpl].flag[23] = 2;
        }
    }
    if (state.p[state.cpl].flag[23] == 5 && state.p[state.cpl].x == state.dx[4] && state.p[state.cpl].y == state.dy[4]) {
        clw();
        tx("You arrive at a village\r\n");
        tx("which looks typically\r\n");
        tx("dwarfish. As you approach\r\n");
        tx("the village square, an\r\n");
        tx("old dwarf who introduces\r\n");
        tx("himself as Borin greets\r\n");
        tx("you.\r\n\r\n");
        ryl_pause();
        clw();
        tx("You explain why you are\r\n");
        tx("here, and he frowns.\r\n");
        tx("- Well, he says, you\r\n");
        tx("actually COULD have a\r\n");
        tx("stone of that type\r\n");
        tx("if you do our tribe a\r\n");
        tx("favour...\r\n\r\n");
        ryl_pause();
        clw();
        tx("- Years have passed, and\r\n");
        tx("much of our knowledge\r\n");
        tx("about the making of\r\n");
        tx("arms is gone. We haven't\r\n");
        tx("practised it, generation\r\n");
        tx("after generation, and the\r\n");
        tx("arms we produce nowadays\r\n");
        tx("are not as unbeatable as\r\n");
        tx("they once were...\r\n\r\n");
        ryl_pause();
        clw();
        tx("- When our king Nolrim\r\n");
        tx("died, nine hundred years\r\n");
        tx("ago we buried him with his\r\n");
        tx("trusty dagger Reebar. If\r\n");
        tx("we had this to study\r\n");
        tx("we could once again become\r\n");
        tx("the greatest smiths that\r\n");
        tx("there are...\r\n\r\n");
        ryl_pause();
        clw();
        tx("- If you go to Nolrim's\r\n");
        tx("crypt, breach the traps my\r\n");
        tx("ancestors set to protect\r\n");
        tx("him against looters, and\r\n");
        tx("return Reebar, I will not\r\n");
        tx("only give you the stone,\r\n");
        tx("but also lots of gold,and\r\n");
        tx("some dwarven treasures.\r\n\r\n");
        ryl_pause();
        clw();
        tx("- Nolrim's crypt is at\r\n");
        tx("%dx%d. Be careful, and\r\n", state.dx[5], state.dy[5]);
        tx("fare the well.\r\n\r\n");
        ryl_pause();
        clw();
        snprintf(send, sizeof send, "%d Nolrim's Crypt is at %dx%d.\n", state.cpl, state.dx[5], state.dy[5]);
        journal(send);
        state.p[state.cpl].flag[23] = 6;
    }
    if (state.p[state.cpl].flag[23] == 6 && state.p[state.cpl].x == state.dx[5] && state.p[state.cpl].y == state.dy[5]) {
        clw();
        tx("Before you is the crypt\r\n");
        tx("of Nolrim. After levering\r\n");
        tx("a stone slab away, a dark\r\n");
        tx("forboding passage leads\r\n");
        tx("down to unknown depths.\r\n\r\n");
        tx("(E)nter the Crypt\r\n");
        tx("(L)eave\r\n");
        c = key();
        c = toupper(c);
        if (c == 'E') {
            dungeon(20, 3);
            clr_map_window();
            update_map();
        } else {
            clw();
            return 1;
        }
    }
    if (state.p[state.cpl].flag[23] == 7 && state.p[state.cpl].x == state.dx[4] && state.p[state.cpl].y == state.dy[4]) {
        clw();
        tx("Borin greets you. He\r\n");
        tx("can hardly believe his\r\n");
        tx("eyes when you show him\r\n");
        tx("the dagger. He motions\r\n");
        tx("to you to follow him,\r\n");
        tx("and guides you to his\r\n");
        tx("hut.\r\n\r\n");
        ryl_pause();
        clw();
        tx("Smiling, he offers you\r\n");
        tx("3578 gold coins and 3\r\n");
        tx("gems. You greedily accept\r\n");
        tx("the gift. He also hands\r\n");
        tx("you a purple dwarf stone.\r\n\r\n");
        ryl_pause();
        clw();
        state.p[state.cpl].gold += 3578;
        for (i = 1; i < 17; i++) {
            if (state.p[state.cpl].inventory[i] == 0) {
                state.p[state.cpl].inventory[i] = 39;
                break;
            }
        }
        for (i = 1; i < 17; i++) {
            if (state.p[state.cpl].inventory[i] == 0) {
                state.p[state.cpl].inventory[i] = 40;
                break;
            }
        }
        for (i = 1; i < 17; i++) {
            if (state.p[state.cpl].inventory[i] == 0) {
                state.p[state.cpl].inventory[i] = 41;
                break;
            }
        }
        state.p[state.cpl].flag[23] = 8;
    }
    if (state.p[state.cpl].flag[23] == 9 && state.p[state.cpl].x == state.dx[6] && state.p[state.cpl].y == state.dy[6]) {
        clw();
        tx("Before you is the dungeon\r\n");
        tx("where the ancients hid the\r\n");
        tx("Master Crown. Your long\r\n");
        tx("trek has come to an end.\r\n\r\n");
        tx("(E)nter\r\n");
        tx("(L)eave\r\n");
        c = key();
        c = toupper(c);
        if (c == 'E') {
            dungeon(10, 4);
            clr_map_window();
            update_map();
        } else {
            clw();
            return 1;
        }
    }
    if (state.p[state.cpl].flag[22] == 1 && state.p[state.cpl].x == state.dx[7] && state.p[state.cpl].y == state.dy[7]) {
        clw();
        tx("You find a the cave the\r\n");
        tx("half-orc told you about.\r\n");
        tx("As you get closer, you\r\n");
        tx("become aware of a hideous\r\n");
        tx("smell, emnating from the\r\n");
        tx("cavern depths...\r\n\r\n");
        tx("(E)nter\r\n");
        tx("(L)eave\r\n");
        c = key();
        c = toupper(c);
        if (c == 'E') {
            clw();
            tx("You enter the cave.\r\n");
            tx("piles of treasure are\r\n");
            tx("thrown all around.\r\n");
            tx("You note with terror\r\n");
            tx("that some OGRES sit\r\n");
            tx("among the gold and\r\n");
            tx("and jewels! They spot\r\n");
            tx("you and run screaming\r\n");
            tx("to attack!\r\n\r\n");
            ryl_pause();
            encounter(18, 1);
            update_map();
            clw();
            tx("You defeat the ogres and\r\n");
            tx("start to search for the\r\n");
            tx("fabled Foot of Jazzy.\r\n");
            tx("Just as you begin to\r\n");
            tx("wonder if the half-orc\r\n");
            tx("lied to you, you hear a\r\n");
            tx("sound and quickly turn\r\n");
            tx("around. An OGRE LORD has\r\n");
            tx("quietly entered the cave!\r\n\r\n");
            ryl_pause();
            encounter(19, 1);
            update_map();
            clw();
            tx("You defeat the Ogre Lord.\r\n");
            tx("You take all the gold\r\n");
            tx("you find, 5645 gold coins!\r\n");
            tx("You also find a Mithril\r\n");
            tx("Mail some unfortunate\r\n");
            tx("warrior has left here.\r\n\r\n");
            ryl_pause();
            clw();
            state.p[state.cpl].gold += 5645;
            for (i = 1; i < 17; i++) {
                if (state.p[state.cpl].inventory[i] == 0) {
                    state.p[state.cpl].inventory[i] = 25;
                }
                break;
            }
            tx("...There is however no\r\n");
            tx("trace of the Foot. Just\r\n");
            tx("as you are about to give\r\n");
            tx("up, %s finds a note!\r\n", state.p[state.cpl].name[2]);
            tx("It is written in shaky\r\n");
            tx("hand with a strange\r\n");
            tx("brown ink...\r\n\r\n");
            ryl_pause();
            clw();
            tx("'THEY SHALL NOT HAVE THE\r\n");
            tx("FOOT! I'm dying and I'll\r\n");
            tx("soon be dead, but I will\r\n");
            tx("use my last strength to\r\n");
            tx("teleport it away from\r\n");
            tx("here to the eastern peaks,\r\n");
            tx("Jazzy's regions.'\r\n");
            tx("         - Kastagir.\r\n\r\n");
            ryl_pause();
            clw();
            tx("You leave the cave.\r\n\r\n");
            ryl_pause();
            clw();
            state.p[state.cpl].flag[22] = 2;
        } else {
            clw();
            return 1;
        }
    }
    if (state.p[state.cpl].flag[6] == 1 && state.p[state.cpl].x == state.dx[8] && state.p[state.cpl].y == state.dy[8]) {
        clw();
        tx("Here lies the dungeon\r\n");
        tx("of the Silver Key.\r\n");
        tx("It is merely a pit in the\r\n");
        tx("ground. You _hope_ that\r\n");
        tx("that despicable thief\r\n");
        tx("didn't trick you.\r\n\r\n");
        tx("(E)nter dungeon\r\n");
        tx("(L)eave\r\n");
        c = key();
        c = toupper(c);
        if (c == 'E') {
            dungeon(20, 5);
            clr_map_window();
            update_map();
        } else {
            clw();
            return 1;
        }
    }
    /******************************************************************/
    if (state.p[state.cpl].flag[6] == 4 && ryl_chance(9)) {
        clw();
        tx("Just before you is a\r\n");
        tx("caravan of hobgoblins on\r\n");
        tx("white, well-bred horses.\r\n\r\n");
        ryl_pause();
        clw();
        tx("It is unusual that these\r\n");
        tx("foul creatures have such\r\n");
        tx("beautiful animals. Hoping\r\n");
        tx("that they can give you a\r\n");
        tx("clue about the key, you\r\n");
        tx("emit a loud battle cry\r\n");
        tx("and attack!\r\n\r\n");
        ryl_pause();
        clw();
        encounter(22, 1);
        update_map();
        clw();
        tx("Before you kill the last\r\n");
        tx("hobgoblin you ask him\r\n");
        tx("about the key. The foul\r\n");
        tx("creature blurts:\r\n");
        tx("- Boss has it! In ze cave!\r\n");
        tx("at home, yess, %dx%d...\r\n", state.dx[9], state.dy[9]);
        tx("pleeez sirs, don't kill\r\n");
        tx("me...\r\n\r\n");
        ryl_pause();
        clw();
        tx("(at this point you sever\r\n");
        tx(" his carotid artery).\r\n\r\n");
        ryl_pause();
        clw();
        snprintf(send, sizeof send, "%d The hobgoblin cave is at %dx%d.\n", state.cpl, state.dx[9], state.dy[9]);
        journal(send);
        state.p[state.cpl].flag[6] = 5;
    }
    if (state.p[state.cpl].flag[6] == 5 && state.p[state.cpl].x == state.dx[9] && state.p[state.cpl].y == state.dy[9]) {
        clw();
        tx("This is a hobgoblin cave.\r\n");
        tx("Smoke drifts out of a pit\r\n");
        tx("in the ground. A staircase\r\n");
        tx("spirals around the inside\r\n");
        tx("of the pit. When you see\r\n");
        tx("the impaled human skulls\r\n");
        tx("decorating the entrance,\r\n");
        tx("you feel uneasy.\r\n\r\n");
        tx("(E)nter pit\r\n");
        tx("(L)eave\r\n");
        c = key();
        c = toupper(c);
        if (c == 'E') {
            dungeon(22, 6);
            clr_map_window();
            update_map();
        } else {
            clw();
            return 1;
        }
    }
    /******************************************************************/
    /* End of Mission Checking */
    return 0;
}
