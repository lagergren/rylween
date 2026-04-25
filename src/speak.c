#include "ryl.h"
#include "res.h"
#include "driv.h"
#include "party.h"
#include "primitives.h"
#include "script.h"
#include "speak.h"
#include "towne.h"
#include "wld.h"

static void npc_mission_hint(int32_t);
static void reply_chitchat(int32_t, int32_t);

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
            if (state.npc[i].x == pa_x() && state.npc[i].y == pa_y()) {
                talk = i;
            }
        }
        if (!talk) {
            return; /* Error */
        }
    }

    npc_t talker = npc_at_slot(talk);
    int female = (npc_gender(talker) == res_enum_id("gender", "female"));

    if (talker.p->visited[state.cpl]) {
        clw();
        tx("%s nods as\r\n", npc_name(talker));
        tx0(res_text(female ? "speak.recognize_she" : "speak.recognize_he"));
        ryl_delay(800);
    } else {
        talker.p->visited[state.cpl] = 1;
    }

    while (1) {
        npc_mission_hint(talk);

        clw();
        tx_fmt("speak.chatting",
               (ryl_bind[]){
                   {"npc_name", npc_name(talker)},
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
                tx("%s tells you:\r\n\r\n", npc_name(talker));
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
                    tx0(res_text(female ? "speak.chit.well_f" : "speak.chit.well_m"));
                    break;
                case 1:
                    tx0(res_text("speak.chit.weather"));
                    break;
                case 2:
                    tx0(res_text(female ? "speak.chit.health_f" : "speak.chit.health_m"));
                    break;
            }
            ryl_delay(1000);
            tx("\r\n");
            reply_chitchat(x, npc_job(talker));
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
                if (npc_job(talker) == res_enum_id("jobname", "bard")) {
                    tx_fmt("speak.people.j8",
                           (ryl_bind[]){
                               {"fighter_name",
                                npc_name(npc_for_role("fighter_intro"))},
                               {0},
                           });
                } else {
                    char job[16];
                    snprintf(job, sizeof job, "%d", npc_job(talker));
                    play_script("speak.people", (ryl_bind[]){
                        {"job", job},
                        {0},
                    });
                }
                tx("\r\n");
                ryl_pause();
            }
            if (d == 'M') {
                tx("\r\n");
                char job[16];
                snprintf(job, sizeof job, "%d", npc_job(talker));
                play_script("speak.magic", (ryl_bind[]){
                    {"job", job},
                    {0},
                });
                tx("\r\n");
                ryl_pause();
            }
            if (d == 'L') {
                tx("\r\n");
                if (npc_job(talker) == res_enum_id("jobname", "bargirl")) {
                    /* §20 L7: bargirl interactive flow lifts to
                       data/world/scripts/lodge.json5 (lodge.bargirl).
                       Wrapper resolves the leader-level fee and hands
                       it as the `price` binding — used both as the
                       {price} text placeholder and as the int gold
                       threshold via {var:'price'}. */
                    char price[16];
                    snprintf(price, sizeof price, "%d", 50 * pa_first_level());
                    play_script("lodge.bargirl", (ryl_bind[]){
                        {"price", price},
                        {0},
                    });
                } else {
                    /* §20.20 #2: per-job static text now goes through
                       lodge.nonbargirl, whose single emit_indexed op
                       replaces the txi("speak.lodge.j%d", ...) callsite. */
                    char job[16];
                    snprintf(job, sizeof job, "%d", npc_job(talker));
                    play_script("lodge.nonbargirl", (ryl_bind[]){
                        {"job", job},
                        {0},
                    });
                }
                tx("\r\n");
                ryl_pause();
            }
        }
    }
}

static void reply_chitchat(int32_t subj, int32_t jb) {
    /* Caller picks subj via ryl_random(0, 2) and jb is the NPC job
       (1..16), so both axes are always in range here. The script
       speak.chit_reply switches on subj to pick the literal
       well/weather/health segment of the text id, then emit_indexed
       fans out on jb. */
    char subj_s[8], job[16];
    snprintf(subj_s, sizeof subj_s, "%d", subj);
    snprintf(job, sizeof job, "%d", jb);
    play_script("speak.chit_reply", (ryl_bind[]){
        {"subj", subj_s},
        {"job",  job},
        {0},
    });
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

/* §20 S8: per-NPC mission stage struct types live in speak.h so res.c
   can fold the runtime table from world.json5 speak_missions: into
   them at load. The static stages_*[] arrays + mission_table[] that
   used to live here are now data, retrieved via res_speak_missions(). */

#define MAX_BINDINGS   4  /* max per stage observed today: 2 */
#define BIND_ZONE_SIZE 80 /* per-binding scratch (NPC_LOC needs ~40) */

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
                out[i].value = res_enum("name", res_npc_role(b->npc_role));
                break;
            case BIND_NPC_LOC:
                npc_loc_str(npc_for_role(b->npc_role), zone, BIND_ZONE_SIZE);
                out[i].value = zone;
                break;
            case BIND_DX_STR:
                snprintf(zone, BIND_ZONE_SIZE, "%d", wld_location_x(b->arg));
                out[i].value = zone;
                break;
            case BIND_DY_STR:
                snprintf(zone, BIND_ZONE_SIZE, "%d", wld_location_y(b->arg));
                out[i].value = zone;
                break;
        }
    }
    out[st->nbinds] = (ryl_bind){0};
}

/* Returns 1 if a stage fired, 0 otherwise. */
static int mission_dispatch(int32_t npc) {
    size_t mission_count = 0;
    const mission_spec *missions = res_speak_missions(&mission_count);
    for (size_t i = 0; i < mission_count; i++) {
        const mission_spec *spec = &missions[i];
        if (res_npc_role(spec->npc_role) != npc) {
            continue;
        }
        for (size_t j = 0; j < spec->nstages; j++) {
            const mission_stage *st = &spec->stages[j];
            int match = 1;
            for (size_t c = 0; c < st->nconds; c++) {
                if (!pa_flag_is(st->conds[c].flag_tag,
                                st->conds[c].when_state)) {
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
   `mission_table` above; the remaining quest-role NPCs use menus or
   invlist lookups that don't fit the screen/journal/set_flag/reward
   DSL and stay inline pending §16.1 slice B work. Dispatch goes
   through res_npc_role so neither the specific character's name nor
   their numeric slot is baked into C — world.json5's npc_roles:
   subtree is the one place that binds a role to a character. */
static void npc_mission_hint(int32_t npc) {
    if (mission_dispatch(npc)) {
        return;
    }

    /* Areth — fighter intro.
       Left inline pending a data-authored script (per the §16.5 slice
       scope, the grateful branch shipped by the foreground is enough
       to exercise the dispatcher; folding the intro-branch is a
       follow-up once a "%s lives" tail-screen can be minted
       consistently with the location-split case). */
    if (npc == res_npc_role("fighter_intro")) {
        if (pa_flag_is("fighter.priest_book", "none")) {
            char loc[80];
            npc_t priest = npc_for_role("priest_with_book");
            npc_loc_str(priest, loc, sizeof loc);
            clw();
            tx0(res_text("speak.fighter.intro"));
            ryl_pause();
            clw();
            tx_fmt("speak.fighter.favour",
                   (ryl_bind[]){
                       {"priest_name", npc_name(priest)},
                       {0},
                   });
            ryl_pause();
            clw();
            tx("%s lives\r\n", npc_name(priest));
            tx("%s.\r\n\r\n", loc);
            journalf("%s, %s's friend lives %s",
                     npc_name(priest),
                     npc_name(npc_at_slot(npc)), loc);
            ryl_pause();
            clw();
            pa_flag_set("fighter.priest_book", "priest_told");
        }
        return;
    }

    /* Bernak — Silver Key map purchase. */
    if (npc == res_npc_role("silver_key_vendor")) {
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
                if (pa_gold() < 50000) {
                    clw();
                    tx0(res_text("speak.silver_key.broke"));
                    ryl_pause();
                    clw();
                    return;
                }
                pa_add_gold(-50000);
                clw();
                {
                    char x[8], y[8];
                    snprintf(x, sizeof x, "%d", wld_location_x(8));
                    snprintf(y, sizeof y, "%d", wld_location_y(8));
                    tx_fmt("speak.silver_key.map",
                           (ryl_bind[]){
                               {"pit_x", x},
                               {"pit_y", y},
                               {0},
                           });
                }
                pa_flag_set("silver_key", "map_bought");
                journalf("The Pit of the Silver Key is at %dx%d", wld_location_x(8), wld_location_y(8));
                ryl_pause();
                clw();
            } else {
                clw();
            }
        }
        return;
    }

    /* Harry — Temple of Thun bracelets. */
    if (npc == res_npc_role("temple_of_thun_seer")) {
        if (pa_flag_is("bracelets.thun", "none")) {
            clw();
            tx0(res_text("speak.thun.legend"));
            ryl_pause();
            clw();
            tx0(res_text("speak.thun.offer"));
            if (ryl_menu("speak.buy_nothanks",
                         (ryl_menu_opt[]){{'B', 1}, {0, 0}}) == 1) {
                if (pa_gold() < 10000) {
                    clw();
                    tx0(res_text("speak.thun.broke"));
                    ryl_pause();
                    clw();
                    return;
                }
                pa_add_gold(-10000);
                clw();
                {
                    char x[8], y[8];
                    snprintf(x, sizeof x, "%d", wld_location_x(2));
                    snprintf(y, sizeof y, "%d", wld_location_y(2));
                    tx_fmt("speak.thun.map",
                           (ryl_bind[]){
                               {"temple_x", x},
                               {"temple_y", y},
                               {0},
                           });
                }
                journalf("The Temple of Thun is at %dx%d", wld_location_x(2), wld_location_y(2));
                pa_flag_set("bracelets.thun", "map_bought");
                ryl_pause();
                clw();
            } else {
                clw();
            }
        }
        return;
    }

    /* Leander — jazzy_ogre trade. The DSL `input_item` op handles
       the inventory-letter prompt, accept/refuse branch, journal
       entry and slot clear; gating on the trade-not-yet-completed
       flag stays here so speech() doesn't even invoke the script
       once it's done. */
    if (npc == res_npc_role("jazzy_ogre_trader") &&
        pa_flag_is("jazzy_ogre", "none")) {
        play_script("speak.jazzy_ogre.trade", NULL);
        return;
    }
}
