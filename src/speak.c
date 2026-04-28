#include <ctype.h>
#include <stdio.h>
#include <string.h>

#include "compat.h"
#include "interact.h"
#include "party.h"
#include "primitives.h"
#include "res.h"
#include "script.h"
#include "speak.h"
#include "towne.h"
#include "wld.h"

static void npc_mission_hint(int32_t);
static void reply_chitchat(int32_t, const char *);

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
    int32_t talk, j, x, reference[40];
    char c, d;
    int32_t rumours = 0;

    clw();
    /* WILDERNESS_TOWN selects the at-location single-NPC path used
       when speaking outside any town; any real town slot id (0-indexed
       post-§25.B step 3b) selects the building/town listing. The
       prior `if (tow)` check used tow == 0 as the wilderness sentinel,
       which collided with the now-valid towns[0] slot. */
    if (tow != WILDERNESS_TOWN) {
        j = 1;
        x = 0;
        for_each_npc(i) {
            npc_t n = npc_at_slot(i);
            if (npc_building(n) == bld && npc_towne(n) == tow) {
                tx("%c. %-8s (%s)\r\n", j + 64, npc_name(n), res_enum("job", npc_job(n)));
                reference[j] = npc_nr(n);
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
        /* -1 sentinel: post-§25.B step 4 NPC axis flip, slot 0 is
           a real NPC (Garth), so the legacy `talk = 0` / `if (!talk)`
           "not found" idiom collides with a valid slot. */
        talk = -1;
        for_each_npc(i) {
            if (npc_is_at(npc_at_slot(i), pa_x(), pa_y())) {
                talk = i;
            }
        }
        if (talk < 0) {
            return; /* Error */
        }
    }

    npc_t talker = npc_at_slot(talk);
    int female = (npc_gender(talker) == res_enum_id("gender", "female"));

    if (npc_visited(talker, party_current_slot())) {
        clw();
        tx("%s nods as\r\n", npc_name(talker));
        tx0(res_text(female ? "speak.recognize_she" : "speak.recognize_he"));
        ryl_delay(800);
    } else {
        npc_visited_set(talker, party_current_slot(), 1);
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
            reply_chitchat(x, npc_job_sym(talker));
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
                if (npc_job_is_sym(talker, "bard")) {
                    tx_fmt("speak.people.jbard",
                           (ryl_bind[]){
                               {"fighter_name",
                                npc_name(npc_for_role("fighter_intro"))},
                               {0},
                           });
                } else {
                    play_script("speak.people",
                                (ryl_bind[]){
                                    {"job_sym", npc_job_sym(talker)},
                                    {0},
                                });
                }
                tx("\r\n");
                ryl_pause();
            }
            if (d == 'M') {
                tx("\r\n");
                play_script("speak.magic",
                            (ryl_bind[]){
                                {"job_sym", npc_job_sym(talker)},
                                {0},
                            });
                tx("\r\n");
                ryl_pause();
            }
            if (d == 'L') {
                tx("\r\n");
                if (npc_job_is_sym(talker, "bargirl")) {
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
                       replaces the txi("speak.lodge.j%d", ...) callsite.
                       Slug binding (npc_job_sym) feeds emit_indexed's
                       string path so the text key is speak.lodge.j<slug>
                       (jmerchant, jranger, ...) — a positional `j<n>`
                       shape would silently rot under enum reordering. */
                    play_script("lodge.nonbargirl",
                                (ryl_bind[]){
                                    {"job_sym", npc_job_sym(talker)},
                                    {0},
                                });
                }
                tx("\r\n");
                ryl_pause();
            }
        }
    }
}

static void reply_chitchat(int32_t subj, const char *job_sym) {
    /* Caller picks subj via ryl_random(0, 2) and job_sym is the NPC's
       job slug (`merchant`, `ranger`, ...). The script speak.chit_reply
       switches on subj to pick the literal well/weather/health segment
       of the text id, then emit_indexed fans out on job_sym via its
       string-spliced path. */
    char subj_s[8];
    snprintf(subj_s, sizeof subj_s, "%d", subj);
    play_script("speak.chit_reply", (ryl_bind[]){
                                        {"subj", subj_s},
                                        {"job_sym", job_sym},
                                        {0},
                                    });
}
/* §22.F: per-NPC mission state-machine step. Looks up the role bound
   to this slot via res_role_for_slot, then plays the matching
   `speak_missions.<role>` dispatch script (an if-chain over flag
   conditions; bodies inline pre_clw + role-keyed bindings + play_
   script of the existing speak.<x>.<y> body). Dispatch script not
   present == role has no scriptable stages yet; fall through to the
   inline blocks below for menu / inv_list branches the DSL can't
   express today (silver-key map purchase, Thun gem altar offer,
   Jazzy ogre item-trade, fighter-intro location-split intro).
   Flag advancement is the script's job (`set_flag` op); C only reads
   flags in the residual inline blocks here. */
static void npc_mission_hint(int32_t npc) {
    const char *role = res_role_for_slot(npc);
    if (role) {
        char dispatch_id[96];
        int n = snprintf(dispatch_id, sizeof dispatch_id,
                         "speak_missions.%s", role);
        if (n > 0 && (size_t)n < sizeof dispatch_id &&
            res_script(dispatch_id)) {
            play_script(dispatch_id, NULL);
        }
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
            journal_add("%s, %s's friend lives %s",
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
                journal_add("The Pit of the Silver Key is at %dx%d", wld_location_x(8), wld_location_y(8));
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
                journal_add("The Temple of Thun is at %dx%d", wld_location_x(2), wld_location_y(2));
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
