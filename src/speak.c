#include "ryl.h"
#include "res.h"
#include "driv.h"
#include "party.h"
#include "primitives.h"
#include "script.h"
#include "speak.h"
#include "towne.h"

static void npc_mission_hint(int32_t);
static void reply_chitchat(int32_t, int32_t);

/* tx() a text record whose id is built from a printf-style format. Used
   by the per-job fan-outs in speak() and reply_chitchat() to map a
   (job-number, subject) pair onto a key in the speak.* shard without
   an ad-hoc stack buffer at every call site. */
__attribute__((format(printf, 1, 2))) static void txi(const char *fmt, ...) {
    char id[96];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(id, sizeof id, fmt, ap);
    va_end(ap);
    tx0(res_text(id));
}

/* Random coords:
**
** Fire Shield Dungeon
** Temple of Thun
** Kobold Village (always on a forest square)
** Dwarf Colony   (always on a desert square)
** Nolrim's Crypt
** The Master Crypt
** Forest Cavern (always on a forest square)
** Pit of The Silver Key
** Hobgoblin Cavern
*/

/*
** The Fire Shield
** (At the bottom of a DEEP dungeon located at XxY. The hermit
**  will tell the adventurers of it after they have slain a 
**  troll for him, and brought him troll's teeth. Note that
**  the position is randomized at the game start. Just before
**  the last dungeon level the party encounters a GUARDIAN.
**  He's quite tough).
**
** The Bracelets of Thun
**
** (Merchant 13 offers to sell the adventurers a MAP for 10000
**  gold pieces. The map will reveal the position of THE LOST
**  temple of THUN. To enter, the players require five gems,
**  no matter which color. They can be randomly found in dungeons
**  or given as a tribute for solving missions). Of course this
**  is a dungeon. On the way down, the players are confronted
**  with 3 riddles. If you answer them right, they can go down,
**  otherwise, they have to fight a monster before they descend.)
**
** The Master Crown
**
** (In a dungeon. Rumors tell that FIGHTER 2 is in search of the master
**  crown. He will tell them that Half-Elf 2 knows its whereabouts. 
**  If the players complete 3 tasks for him, he will give them the
**  dungeon coordinates. Task 1: Destroy a kobold village. 3 kobold
**  fights and 2 orc fights (visitors).  Task 2:  Get him a spell
**  ingredient, Skullweed. He doesn't know where it grows. Rumors
**  tell that it grows in the swamp, if the players step on a swamp
**  square with this mission they will automatically find and recognize
**  Skullweed.   Task 3:  Get him a DWARF STONE. Rumors tell of a dwarf
**  colony at coordinates XxY. Nothing is seen here except for a ruin
**  ,but the elf gives them a password to call, and dwarfs appear.
**  To give them a dwarf stone the players need to get them the dagger
**  of Nolrim, their long dead master, who rests in a crypt at XxY,
**  the dwarves have forgotten their fine art of "smide" and want
**  it for studying. The players get it from a particularly nasty
**  dungeon and gives it to the dwarves who give them the stone.
**  The elf then tells them the coordinates XxY to the master crown's
**  dungeon.)
**
** The Foot of Jazzy
**
** (Half-Orc 2 is very poor and will give the players information about
**  Jazzyfoot for any item > 35. He tells them that a beast in a cavern
**  in the woods has it. Cavern, pos XxY, NOT A DUNGEON. The players
**  defeat the beast, search through its belongings and find a message
**  written in blood. "He shall never get the foot. I use my last strength
**  to transport it high up in the mountains." When the players next
**  step on a foothill square, they find the foot.) However, when they
**  pick it up, Jazzy himself (lesser god) appears and attacks the party.)
**
** The Silver Key
**
** (Thief one will sell a map with the dungeon location for 80000 gold.
**  This amount of money can be collected by selling valuable items
**  from dungeon raids, or by completing small "apocryphic" missions.
**  Dungeon at pos XxY. However, the key has been stolen from the
**  deepest level of the crypt. A message containing Thief 2's name
**  will be there, boasting of the effort. Find him. Use mild force
**  to make him speak. He sold it to Smith 2 who was interested in
**  it's structure. The smith was on his way to a colleague (smith 1)
**  but was ambushed and robbed of all his belongings just "to the south-
**  west", by a gang of hobgoblins. NExt encounter. The last one speaks
**  of the key, it is in their caves, it is around their masters neck.
**  Find master, beat the $#!T out of him, take the key).
**
*/

/* Flute of
**
**
*/

void speak(int32_t bld, int32_t tow) {
    int32_t talk, i, j, x, reference[40];
    char c, d;
    int32_t rumours = 0;

    clw();
    if (tow) {
        for (i = 1, j = 1, x = 0; i < 40; i++) {
            if (state.npc[i].building == bld && state.npc[i].towne == tow) {
                tx("%c. %-8s (%s)\r\n", j + 64, res_enum("name", state.npc[i].nr), res_enum("jobname", state.npc[i].job));
                reference[j] = state.npc[i].nr;
                j++;
                x = 1;
            }
        }
        if (!x) {
            clw();
            tx0(res_text("speak.no_npc"));
            ryl_delay(1000);
            return;
        }
        tx("\r\n");
        c = key();
        c = toupper(c);
        if (c < 65 || c > (j + 63)) {
            return;
        }
        talk = reference[c - 64];
    } else {
        for (i = 1, talk = 0; i < 40; i++) {
            if (state.npc[i].x == state.p[state.cpl].x && state.npc[i].y == state.p[state.cpl].y) {
                talk = i;
            }
        }
        if (!talk) {
            return; /* Error */
        }
    }

    if (state.npc[talk].visited[state.cpl]) {
        clw();
        tx("%s nods as\r\n", res_enum("name", talk));
        if (state.npc[talk].job != 13) {
            tx0(res_text("speak.recognize_he"));
        } else {
            tx0(res_text("speak.recognize_she"));
        }
        ryl_delay(800);
    } else {
        state.npc[talk].visited[state.cpl] = 1;
    }

    while (1) {
        npc_mission_hint(talk);

        clw();
        tx_fmt("speak.chatting",
               (ryl_bind[]){
                   {"npc_name", res_enum("name", talk)},
                   {0},
               });

        /* Passed */

        tx0(res_text("speak.menu"));
        c = key();
        c = toupper(c);
        if (c == 'B') {
            clw();
            return;
        }

        if (c == 'R') {
            clw();
            if (!rumours) {
                tx("%s tells you:\r\n\r\n", res_enum("name", talk));
                rumour();
                ryl_pause();
                rumours = 1;
            } else {
                tx0(res_text("speak.no_rumour"));
                ryl_pause();
            }
        }

        if (c == 'C') {
            x = ryl_random(0, 2);
            clw();
            switch (x) {
                case 0:
                    tx0(res_text(state.npc[talk].gender == 2 ? "speak.chit.well_f" : "speak.chit.well_m"));
                    break;
                case 1:
                    tx0(res_text("speak.chit.weather"));
                    break;
                case 2:
                    tx0(res_text(state.npc[talk].gender == 2 ? "speak.chit.health_f" : "speak.chit.health_m"));
                    break;
            }
            ryl_delay(1000);
            tx("\r\n");
            reply_chitchat(x, state.npc[talk].job);
            tx("\r\n");
            ryl_pause();
        }

        if (c == 'Q') {
            clw();
            tx0(res_text("speak.question"));
            d = key();
            d = toupper(d);
            if (d == 'P') {
                tx("\r\n");
                if (state.npc[talk].job == 8) {
                    tx_fmt("speak.people.j8",
                           (ryl_bind[]){
                               {"fighter_name", res_enum("name", 3)},
                               {0},
                           });
                } else {
                    txi("speak.people.j%d", state.npc[talk].job);
                }
                tx("\r\n");
                ryl_pause();
            }
            if (d == 'M') {
                tx("\r\n");
                txi("speak.magic.j%d", state.npc[talk].job);
                tx("\r\n");
                ryl_pause();
            }
            if (d == 'L') {
                tx("\r\n");
                if (state.npc[talk].job == 13) {
                    char price[16];
                    snprintf(price, sizeof price, "%d", 50 * state.p[state.cpl].lv[1]);
                    clw();
                    tx_fmt("speak.lodge.j13.offer",
                           (ryl_bind[]){
                               {"price", price},
                               {0},
                           });
                    tx("  %s", res_text("common.yes"));
                    tx("  %s", res_text("common.no"));
                    c = key();
                    c = toupper(c);
                    if (c == 'Y') {
                        if (state.p[state.cpl].gold < 50 * state.p[state.cpl].lv[1]) {
                            clw();
                            tx0(res_text("speak.lodge.j13.broke"));
                            ryl_delay(2000);
                        } else {
                            state.p[state.cpl].gold -= 50 * state.p[state.cpl].lv[1];
                            clw();
                            tx0(res_text("speak.lodge.j13.spent"));
                            for (i = 1; i <= res_party_member_max(); i++) {
                                if (state.p[state.cpl].hp[i] > 1) {
                                    state.p[state.cpl].hp[i]--;
                                }
                            }
                        }
                    }
                } else {
                    txi("speak.lodge.j%d", state.npc[talk].job);
                }
                tx("\r\n");
                ryl_pause();
            }
        }
    }
}

static void reply_chitchat(int32_t subj, int32_t jb) {
    /* Caller picks subj via ryl_random(0, 2) and jb is the NPC job
       (1..16), so both axes are always in range here. */
    static const char *const subjects[3] = {"well", "weather", "health"};
    txi("speak.chit.reply.%s.j%d", subjects[subj], jb);
}
/* §16.5 mission-state-machine dispatcher. Table-driven replacement for
   the historical `npc_mission_hint` switch (one case per NPC slot,
   flag-guarded if-chains, inline tx_fmt / play_script).

   The stage tables are pure data — adding a new NPC or stage is a
   data edit, no new C functions. Conditions (mission_cond) are
   AND-of-N: a stage fires only when every listed flag slot matches
   its expected value. OR across stages is implicit — stages are
   tried in order and the first match wins; multi-way OR is expressed
   by listing the stage N times with different conds + same script.

   Bindings are authored as {name, kind, arg} triples. The four
   primitive kinds (NPC_NAME / NPC_LOC / DX_STR / DY_STR) cover every
   call site the original C had; widening the vocabulary is a data
   edit plus one `case` in mission_resolve_bindings.

   NPCs whose "hint" is a menu / invlist-keyed branch (cases 6, 13,
   22) are NOT in the table — they need DSL ops that don't exist yet
   (conditional gold-check, inventory-letter key) and stay inline
   below. Flag advancement is the script's job (`set_flag` op); C
   only reads flags here. */

enum mission_bind_kind {
    BIND_NPC_NAME = 1, /* res_enum("name", arg) */
    BIND_NPC_LOC,      /* "in <town>" | "at <x>x<y>" via fmt_npc_loc */
    BIND_DX_STR,       /* state.dx[arg] stringified */
    BIND_DY_STR,       /* state.dy[arg] stringified */
};

typedef struct {
    int32_t flag;
    int32_t when;
} mission_cond;

typedef struct {
    const char *name; /* placeholder id */
    enum mission_bind_kind kind;
    int32_t arg; /* npc slot or dungeon index, per kind */
} mission_binding;

typedef struct {
    const mission_cond *conds; /* AND-of-all; never NULL; nconds >= 1 */
    size_t nconds;
    const char *script;
    const mission_binding *binds; /* NULL iff nbinds == 0 */
    size_t nbinds;
    uint8_t pre_clw;
} mission_stage;

typedef struct {
    int32_t npc_slot;
    const mission_stage *stages;
    size_t nstages;
} mission_spec;

#define MAX_BINDINGS   4  /* max per stage observed today: 2 */
#define BIND_ZONE_SIZE 80 /* per-binding scratch (NPC_LOC needs ~40) */

/* Write "in <town>" OR "at <x>x<y>" for NPC `slot` into `dst`. */
static void fmt_npc_loc(int32_t slot, char *dst, size_t dst_sz) {
    if (state.npc[slot].towne) {
        snprintf(dst, dst_sz, "in %s", res_town_name(state.npc[slot].towne));
    } else {
        snprintf(dst, dst_sz, "at %dx%d", state.npc[slot].x, state.npc[slot].y);
    }
}

/* Stage tables. First-match-wins within each NPC; stages are never
   expected to fire simultaneously for a single flag snapshot. Cond
   and binding arrays use C99 compound literals so adding a stage is
   a single struct-literal append. */

static const mission_stage stages_npc1[] = {
    {.conds = (const mission_cond[]){{.flag = 1, .when = 0}}, .nconds = 1, .script = "speak.merchant.first_visit", .pre_clw = 1},
    {.conds = (const mission_cond[]){{.flag = 1, .when = 2}}, .nconds = 1, .script = "speak.merchant.grateful", .pre_clw = 1},
};

static const mission_stage stages_npc2[] = {
    {.conds = (const mission_cond[]){{.flag = 2, .when = 0}}, .nconds = 1, .script = "speak.ranger.first_visit", .binds = (const mission_binding[]){
                                                                                                                     {"ranger_name", BIND_NPC_NAME, 14},
                                                                                                                     {"ranger_loc", BIND_NPC_LOC, 14},
                                                                                                                 },
     .nbinds = 2,
     .pre_clw = 0},
};

static const mission_stage stages_npc3[] = {
    /* flag[3]==0 (fighter intro) stays inline — no script yet. */
    {.conds = (const mission_cond[]){{.flag = 3, .when = 3}}, .nconds = 1, .script = "speak.fighter.grateful", .pre_clw = 1},
};

static const mission_stage stages_npc4[] = {
    /* NPC 4 reads flag[3] (priest-chain's flag). */
    {.conds = (const mission_cond[]){{.flag = 3, .when = 2}}, .nconds = 1, .script = "speak.wizard.robbed_flow", .pre_clw = 1},
};

static const mission_stage stages_npc5[] = {
    {.conds = (const mission_cond[]){{.flag = 3, .when = 1}}, .nconds = 1, .script = "speak.priest.intro_flow", .binds = (const mission_binding[]){
                                                                                                                    {"wizard_name", BIND_NPC_NAME, 4},
                                                                                                                    {"giver_name", BIND_NPC_NAME, 3},
                                                                                                                },
     .nbinds = 2,
     .pre_clw = 1},
};

static const mission_stage stages_npc8[] = {
    {.conds = (const mission_cond[]){{.flag = 8, .when = 0}}, .nconds = 1, .script = "speak.erethor.first_visit", .pre_clw = 0},
    {.conds = (const mission_cond[]){{.flag = 8, .when = 2}}, .nconds = 1, .script = "speak.erethor.delivered_lute", .binds = (const mission_binding[]){
                                                                                                                         {"minstrel_name", BIND_NPC_NAME, 20},
                                                                                                                         {"minstrel_loc", BIND_NPC_LOC, 20},
                                                                                                                     },
     .nbinds = 2,
     .pre_clw = 1},
};

static const mission_stage stages_npc14[] = {
    /* NPC 14 (brother) reads flag[2] (ranger's flag). */
    {.conds = (const mission_cond[]){{.flag = 2, .when = 1}}, .nconds = 1, .script = "speak.brother.give_sword", .pre_clw = 1},
};

static const mission_stage stages_npc15[] = {
    {.conds = (const mission_cond[]){{.flag = 15, .when = 0}}, .nconds = 1, .script = "speak.nymph.know_flow", .binds = (const mission_binding[]){
                                                                                                                   {"seeker_name", BIND_NPC_NAME, 23},
                                                                                                                   {"seeker_loc", BIND_NPC_LOC, 23},
                                                                                                               },
     .nbinds = 2,
     .pre_clw = 1},
};

static const mission_stage stages_npc18[] = {
    /* NPC 18 (thief 2) reads flag[6] (silver-key). */
    {.conds = (const mission_cond[]){{.flag = 6, .when = 2}}, .nconds = 1, .script = "speak.thief2.squeezed", .binds = (const mission_binding[]){
                                                                                                                  {"smith_name", BIND_NPC_NAME, 24},
                                                                                                              },
     .nbinds = 1,
     .pre_clw = 1},
};

static const mission_stage stages_npc19[] = {
    {.conds = (const mission_cond[]){{.flag = 19, .when = 0}}, .nconds = 1, .script = "speak.husband.first_visit", .pre_clw = 1},
    {.conds = (const mission_cond[]){{.flag = 19, .when = 2}}, .nconds = 1, .script = "speak.husband.rescued", .pre_clw = 1},
};

static const mission_stage stages_npc20[] = {
    /* NPC 20 (minstrel) reads flag[8] (Erethor-chain). */
    {.conds = (const mission_cond[]){{.flag = 8, .when = 3}}, .nconds = 1, .script = "speak.minstrel.gives_cloak", .binds = (const mission_binding[]){
                                                                                                                       {"minstrel_name", BIND_NPC_NAME, 20},
                                                                                                                       {"next_town", BIND_NPC_NAME, 8},
                                                                                                                   },
     .nbinds = 2,
     .pre_clw = 1},
};

static const mission_stage stages_npc23[] = {
    /* task1 is a two-condition stage (flag[15]==1 AND flag[23]==0). */
    {.conds = (const mission_cond[]){
         {.flag = 15, .when = 1},
         {.flag = 23, .when = 0},
     },
     .nconds = 2,
     .script = "speak.wizard3t.task1_flow",
     .binds = (const mission_binding[]){
         {"kobold_x", BIND_DX_STR, 3},
         {"kobold_y", BIND_DY_STR, 3},
     },
     .nbinds = 2,
     .pre_clw = 1},
    {.conds = (const mission_cond[]){{.flag = 23, .when = 2}}, .nconds = 1, .script = "speak.wizard3t.task2_flow", .pre_clw = 1},
    {.conds = (const mission_cond[]){{.flag = 23, .when = 4}}, .nconds = 1, .script = "speak.wizard3t.task3_flow", .binds = (const mission_binding[]){
                                                                                                                       {"dwarf_x", BIND_DX_STR, 4},
                                                                                                                       {"dwarf_y", BIND_DY_STR, 4},
                                                                                                                   },
     .nbinds = 2,
     .pre_clw = 1},
    {.conds = (const mission_cond[]){{.flag = 23, .when = 8}}, .nconds = 1, .script = "speak.wizard3t.grateful_flow", .binds = (const mission_binding[]){
                                                                                                                          {"crown_x", BIND_DX_STR, 6},
                                                                                                                          {"crown_y", BIND_DY_STR, 6},
                                                                                                                      },
     .nbinds = 2,
     .pre_clw = 1},
};

static const mission_stage stages_npc24[] = {
    {.conds = (const mission_cond[]){{.flag = 6, .when = 3}}, .nconds = 1, .script = "speak.smith.after_thief2", .pre_clw = 1},
};

static const mission_stage stages_npc33[] = {
    {.conds = (const mission_cond[]){{.flag = 33, .when = 0}}, .nconds = 1, .script = "speak.hermit.welcome_visit", .pre_clw = 1},
    {.conds = (const mission_cond[]){{.flag = 33, .when = 2}}, .nconds = 1, .script = "speak.hermit.tooth_trade", .binds = (const mission_binding[]){
                                                                                                                      {"cave_x", BIND_DX_STR, 1},
                                                                                                                      {"cave_y", BIND_DY_STR, 1},
                                                                                                                  },
     .nbinds = 2,
     .pre_clw = 1},
};

#define MS_ENTRY(slot, arr) \
    {slot, arr, sizeof arr / sizeof *arr}

static const mission_spec mission_table[] = {
    MS_ENTRY(1, stages_npc1),
    MS_ENTRY(2, stages_npc2),
    MS_ENTRY(3, stages_npc3),
    MS_ENTRY(4, stages_npc4),
    MS_ENTRY(5, stages_npc5),
    MS_ENTRY(8, stages_npc8),
    MS_ENTRY(14, stages_npc14),
    MS_ENTRY(15, stages_npc15),
    MS_ENTRY(18, stages_npc18),
    MS_ENTRY(19, stages_npc19),
    MS_ENTRY(20, stages_npc20),
    MS_ENTRY(23, stages_npc23),
    MS_ENTRY(24, stages_npc24),
    MS_ENTRY(33, stages_npc33),
};

#undef MS_ENTRY

/* Resolve `st->binds[]` into `out` (+ trailing {0} sentinel), using
   per-binding zones of BIND_ZONE_SIZE bytes in `scratch` for any
   values that need inline formatting. `scratch` must be at least
   nbinds * BIND_ZONE_SIZE bytes. */
static void mission_resolve_bindings(const mission_stage *st, ryl_bind *out,
                                     char *scratch) {
    for (size_t i = 0; i < st->nbinds; i++) {
        const mission_binding *b = &st->binds[i];
        char *zone = scratch + i * BIND_ZONE_SIZE;
        out[i].name = b->name;
        switch (b->kind) {
            case BIND_NPC_NAME:
                out[i].value = res_enum("name", b->arg);
                break;
            case BIND_NPC_LOC:
                fmt_npc_loc(b->arg, zone, BIND_ZONE_SIZE);
                out[i].value = zone;
                break;
            case BIND_DX_STR:
                snprintf(zone, BIND_ZONE_SIZE, "%d", state.dx[b->arg]);
                out[i].value = zone;
                break;
            case BIND_DY_STR:
                snprintf(zone, BIND_ZONE_SIZE, "%d", state.dy[b->arg]);
                out[i].value = zone;
                break;
        }
    }
    out[st->nbinds] = (ryl_bind){0};
}

/* Returns 1 if a stage fired, 0 otherwise. */
static int mission_dispatch(int32_t npc) {
    const unsigned char *flag = state.p[state.cpl].flag;
    for (size_t i = 0; i < sizeof mission_table / sizeof *mission_table; i++) {
        const mission_spec *spec = &mission_table[i];
        if (spec->npc_slot != npc) {
            continue;
        }
        for (size_t j = 0; j < spec->nstages; j++) {
            const mission_stage *st = &spec->stages[j];
            int match = 1;
            for (size_t c = 0; c < st->nconds; c++) {
                if (flag[st->conds[c].flag] != (unsigned char)st->conds[c].when) {
                    match = 0;
                    break;
                }
            }
            if (!match) {
                continue;
            }
            ryl_bind binds[MAX_BINDINGS + 1];
            char scratch[MAX_BINDINGS * BIND_ZONE_SIZE];
            const ryl_bind *pass = NULL;
            if (st->nbinds > 0) {
                mission_resolve_bindings(st, binds, scratch);
                pass = binds;
            }
            if (st->pre_clw) {
                clw();
            }
            play_script(st->script, pass);
            return 1;
        }
        return 0;
    }
    return 0;
}

/* Per-NPC mission state-machine step. Dispatches scripted flows via the
   `mission_table` above; the remaining cases (6, 13, 22) use menus or
   invlist lookups that don't fit the screen/journal/set_flag/reward DSL
   and stay inline pending §16.1 slice B work. */
static void npc_mission_hint(int32_t npc) {
    if (mission_dispatch(npc)) {
        return;
    }

    int32_t oc;
    char d;

    switch (npc) {
        /*****************************************************************/
        /* case 3, flag[3]==0 — fighter-intro branch. Left inline
           pending a data-authored script (per the §16.5 slice scope, the
           grateful branch shipped by the foreground is enough to
           exercise the dispatcher; folding the intro-branch is a
           follow-up once a {"%s lives" tail-screen can be minted
           consistently with case 15's location-split). */
        case 3:
            if (pa_flag_is("fighter.priest_book", "none")) {
                char loc[80];
                if (state.npc[5].towne) {
                    snprintf(loc, sizeof loc, "in %s", res_town_name(state.npc[5].towne));
                } else {
                    snprintf(loc, sizeof loc, "at %dx%d", state.npc[5].x, state.npc[5].y);
                }
                clw();
                tx0(res_text("speak.fighter.intro"));
                ryl_pause();
                clw();
                tx_fmt("speak.fighter.favour",
                       (ryl_bind[]){
                           {"priest_name", res_enum("name", 5)},
                           {0},
                       });
                ryl_pause();
                clw();
                tx("%s lives\r\n", res_enum("name", 5));
                tx("%s.\r\n\r\n", loc);
                journalf("%d %s, %s's friend lives %s\n", state.cpl, res_enum("name", 5), res_enum("name", 3), loc);
                ryl_pause();
                clw();
                pa_flag_set("fighter.priest_book", "priest_told");
            }
            break;
        /*****************************************************************/
        case 6:
            if (pa_flag_is("silver_key", "none")) {
                clw();
                tx0(res_text("speak.silver_key.hiss"));
                ryl_pause();
                clw();
                tx0(res_text("speak.silver_key.research"));
                ryl_pause();
                clw();
                tx0(res_text("speak.silver_key.chased"));
                ryl_pause();
                clw();
                tx0(res_text("speak.silver_key.offer"));
                ryl_pause();
                clw();
                tx0(res_text("speak.silver_key.deal"));
                if (ryl_menu("speak.buy_nothanks",
                             (ryl_menu_opt[]){{'B', 1}, {0, 0}}) == 1) {
                    if (state.p[state.cpl].gold < 50000) {
                        clw();
                        tx0(res_text("speak.silver_key.broke"));
                        ryl_pause();
                        clw();
                        break;
                    }
                    state.p[state.cpl].gold -= 50000;
                    clw();
                    {
                        char x[8], y[8];
                        snprintf(x, sizeof x, "%d", state.dx[8]);
                        snprintf(y, sizeof y, "%d", state.dy[8]);
                        tx_fmt("speak.silver_key.map",
                               (ryl_bind[]){
                                   {"pit_x", x},
                                   {"pit_y", y},
                                   {0},
                               });
                    }
                    pa_flag_set("silver_key", "map_bought");
                    journalf("%d The Pit of the Silver Key is at %dx%d\n", state.cpl, state.dx[8], state.dy[8]);
                    ryl_pause();
                    clw();
                    break;
                } else {
                    clw();
                }
                break;
            }
            break;
        /*****************************************************************/
        case 13:
            if (pa_flag_is("bracelets.thun", "none")) {
                clw();
                tx0(res_text("speak.thun.legend"));
                ryl_pause();
                clw();
                tx0(res_text("speak.thun.offer"));
                if (ryl_menu("speak.buy_nothanks",
                             (ryl_menu_opt[]){{'B', 1}, {0, 0}}) == 1) {
                    if (state.p[state.cpl].gold < 10000) {
                        clw();
                        tx0(res_text("speak.thun.broke"));
                        ryl_pause();
                        clw();
                        break;
                    }
                    state.p[state.cpl].gold -= 10000;
                    clw();
                    {
                        char x[8], y[8];
                        snprintf(x, sizeof x, "%d", state.dx[2]);
                        snprintf(y, sizeof y, "%d", state.dy[2]);
                        tx_fmt("speak.thun.map",
                               (ryl_bind[]){
                                   {"temple_x", x},
                                   {"temple_y", y},
                                   {0},
                               });
                    }
                    journalf("%d The Temple of Thun is at %dx%d\n", state.cpl, state.dx[2], state.dy[2]);
                    pa_flag_set("bracelets.thun", "map_bought");
                    ryl_pause();
                    clw();
                    break;
                } else {
                    clw();
                }
                break;
            }
            break;
        /*****************************************************************/
        /* case 22 — jazzy_ogre. invlist + key-letter + inventory lookup
           pattern; the item-keyed branch isn't a reward/menu shape and
           stays inline pending §16.1 slice B. See port-plan §16.3. */
        case 22:
            if (pa_flag_is("jazzy_ogre", "none")) {
                clw();
                tx0(res_text("speak.jazzy_ogre.hear"));
                ryl_pause();
                clw();
                tx0(res_text("speak.jazzy_ogre.trade"));
                ryl_pause();
                clw();
                invlist();
                d = key();
                d = toupper(d);
                oc = 0;
                if (d >= 'A' && d <= 'P') {
                    oc = state.p[state.cpl].inventory[d - 64];
                    if (oc < 35) {
                        clw();
                        tx0(res_text("speak.jazzy_ogre.refuse"));
                        ryl_delay(1000);
                        break;
                    } else {
                        clw();
                        {
                            char x[8], y[8];
                            snprintf(x, sizeof x, "%d", state.dx[7]);
                            snprintf(y, sizeof y, "%d", state.dy[7]);
                            tx_fmt("speak.jazzy_ogre.accept",
                                   (ryl_bind[]){
                                       {"cave_x", x},
                                       {"cave_y", y},
                                       {0},
                                   });
                        }
                        journalf("%d The 'Beast Cave' is at %dx%d.\n", state.cpl, state.dx[7], state.dy[7]);
                        pa_flag_set("jazzy_ogre", "accepted");
                        state.p[state.cpl].inventory[d - 64] = 0;
                        ryl_pause();
                        clw();
                        break;
                    }
                }
            }
            break;
            /*****************************************************************/
    }
}
