#include "ryl.h"
#include "res.h"
#include "driv.h"
#include "primitives.h"
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
        tx0(res_text("speak.chatting"));
        tx("%s:\r\n\r\n", res_enum("name", talk));

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
                    tx0(res_text(state.npc[talk].job == 13 ? "speak.chit.well_f" : "speak.chit.well_m"));
                    break;
                case 1:
                    tx0(res_text("speak.chit.weather"));
                    break;
                case 2:
                    tx0(res_text(state.npc[talk].job == 13 ? "speak.chit.health_f" : "speak.chit.health_m"));
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
                    tx0(res_text("speak.people.j8_pre"));
                    tx("%s\r\n", res_enum("name", 3));
                    tx0(res_text("speak.people.j8_post"));
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
                    clw();
                    tx0(res_text("speak.lodge.j13.offer_pre"));
                    tx("  upstairs for %d\r\n", 50 * state.p[state.cpl].lv[1]);
                    tx0(res_text("speak.lodge.j13.offer_post"));
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
                            for (i = 1; i < 5; i++) {
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
/* Per-NPC mission state-machine step. Each case checks the party's
   mission flags for this NPC (indexed by NPC nr) and, when a quest is
   at the right stage, emits the corresponding dialogue screen(s) and
   advances the flag. Historically named "flaggkoll" (Swedish for "flag
   check"); the responsibilities grew beyond a pure check. */
static void npc_mission_hint(int32_t npc) {
    int32_t i, x, oc;
    char loc[80];
    char c, d;
    uint8_t carry;

    switch (npc) {
        /*****************************************************************/
        case 1:
            if (state.p[state.cpl].flag[1] == 0) {
                clw();
                tx0(res_text("speak.merchant.offer"));
                ryl_pause();
                clw();
                tx0(res_text("speak.merchant.bandits"));
                ryl_pause();
                clw();
                state.p[state.cpl].flag[1] = 1;
                break;
            }
            if (state.p[state.cpl].flag[1] == 2) {
                clw();
                tx0(res_text("speak.merchant.grateful"));
                for (i = 1, x = 0; i < 17; i++) {
                    if (state.p[state.cpl].inventory[i] == 0) {
                        state.p[state.cpl].inventory[i] = 39;
                        x = 1;
                        break;
                    }
                }
                if (!x) {
                    tx0(res_text("speak.merchant.cant_carry"));
                    ryl_delay(800);
                    carry = 0;
                    carry = drop_option();
                    if (carry) {
                        clw();
                        tx0(res_text("speak.merchant.picked_up"));
                        state.p[state.cpl].inventory[carry] = 39;
                        ryl_delay(800);
                    }
                }
                ryl_pause();
                state.p[state.cpl].gold += 2000;
                state.p[state.cpl].flag[1] = 3;
                clw();
                break;
            }
            break;
        /*****************************************************************/
        case 2:
            if (state.p[state.cpl].flag[2] == 0) {
                if (state.npc[14].towne) {
                    snprintf(loc, sizeof loc, "in %s", res_town_name(state.npc[14].towne));
                } else {
                    snprintf(loc, sizeof loc, "at %dx%d", state.npc[14].x, state.npc[14].y);
                }
                tx0(res_text("speak.ranger.intro"));
                ryl_pause();
                clw();
                tx0(res_text("speak.ranger.deliver_pre"));
                tx("to my brother, %s,\r\n", res_enum("name", 14));
                tx("who lives %s,\r\n", loc);
                tx0(res_text("speak.ranger.deliver_post"));
                ryl_pause();
                clw();
                journalf("%d %s, the Ranger lives %s\n", state.cpl, res_enum("name", 14), loc);
                state.p[state.cpl].flag[2] = 1;
            }
            break;
        /*****************************************************************/
        case 3:
            if (state.p[state.cpl].flag[3] == 0) {
                if (state.npc[5].towne) {
                    snprintf(loc, sizeof loc, "in %s", res_town_name(state.npc[5].towne));
                } else {
                    snprintf(loc, sizeof loc, "at %dx%d", state.npc[5].x, state.npc[5].y);
                }
                clw();
                tx0(res_text("speak.fighter.intro"));
                ryl_pause();
                clw();
                tx0(res_text("speak.fighter.favour_pre"));
                tx("named %s has got\r\n", res_enum("name", 5));
                tx0(res_text("speak.fighter.favour_post"));
                ryl_pause();
                clw();
                tx("%s lives\r\n", res_enum("name", 5));
                tx("%s.\r\n\r\n", loc);
                journalf("%d %s, %s's friend lives %s\n", state.cpl, res_enum("name", 5), res_enum("name", 3), loc);
                ryl_pause();
                clw();
                state.p[state.cpl].flag[3] = 1;
            }
            if (state.p[state.cpl].flag[3] == 3) {
                clw();
                tx0(res_text("speak.fighter.blade"));
                ryl_pause();
                clw();
                state.p[state.cpl].flag[3] = 4;
                for (i = 1, x = 0; i < 17; i++) {
                    if (state.p[state.cpl].inventory[i] == 0) {
                        state.p[state.cpl].inventory[i] = 18;
                        x = 1;
                        break;
                    }
                }
                if (!x) {
                    tx0(res_text("speak.fighter.cant_carry"));
                    ryl_delay(800);
                    carry = 0;
                    carry = drop_option();
                    if (carry) {
                        clw();
                        tx0(res_text("speak.fighter.take_blade"));
                        state.p[state.cpl].inventory[carry] = 18;
                        ryl_delay(800);
                    }
                    ryl_pause();
                }
            }
            break;
        case 4:
            if (state.p[state.cpl].flag[3] == 2) {
                clw();
                tx0(res_text("speak.wizard.robbed"));
                state.p[state.cpl].flag[3] = 3;
                ryl_pause();
                clw();
            }
            break;
        case 5:
            if (state.p[state.cpl].flag[3] == 1) {
                clw();
                tx0(res_text("speak.priest.intro_pre"));
                tx("called %s stole it\r\n", res_enum("name", 4));
                tx0(res_text("speak.priest.intro_post"));
                ryl_pause();
                clw();
                tx0(res_text("speak.priest.fled"));
                ryl_pause();
                clw();
                tx0(res_text("speak.priest.please_pre"));
                tx("take it back to %s\r\n", res_enum("name", 3));
                tx0(res_text("speak.priest.please_post"));
                ryl_pause();
                clw();
                state.p[state.cpl].flag[3] = 2;
                /* GIZZARD */
            }
            break;
        /*****************************************************************/
        case 6:
            if (state.p[state.cpl].flag[6] == 0) {
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
                tx0(res_text("speak.buy_nothanks"));
                c = key();
                c = toupper(c);
                if (c == 'B') {
                    if (state.p[state.cpl].gold < 50000) {
                        clw();
                        tx0(res_text("speak.silver_key.broke"));
                        ryl_pause();
                        clw();
                        break;
                    }
                    state.p[state.cpl].gold -= 50000;
                    clw();
                    tx0(res_text("speak.silver_key.map_pre"));
                    tx("%dx%d!\r\n\r\n", state.dx[8], state.dy[8]);
                    state.p[state.cpl].flag[6] = 1;
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
        case 8:
            if (state.p[state.cpl].flag[8] == 0) {
                tx0(res_text("speak.erethor.rumour"));
                ryl_pause();
                clw();
                tx0(res_text("speak.erethor.demon"));
                ryl_pause();
                clw();
                tx0(res_text("speak.erethor.poor_man"));
                ryl_pause();
                clw();
                tx0(res_text("speak.erethor.trade"));
                ryl_pause();
                clw();
                tx0(res_text("speak.erethor.dangerous"));
                ryl_pause();
                clw();
                journalf("%d Erethor's temple is situated at 6x20.\n", state.cpl);
                state.p[state.cpl].flag[8] = 1;
            }
            if (state.p[state.cpl].flag[8] == 2) {
                clw();
                tx0(res_text("speak.erethor.hand_lute"));
                ryl_pause();
                clw();
                tx0(res_text("speak.erethor.note_pre"));
                ryl_pause();
                clw();
                tx0(res_text("speak.erethor.note_seek"));
                tx("%s. He is also a\r\n", res_enum("name", 20));
                tx0(res_text("speak.erethor.minstrel"));
                if (state.npc[20].towne) {
                    snprintf(loc, sizeof loc, "%s lives in %s.\n", res_enum("name", 20), res_town_name(state.npc[20].towne));
                } else {
                    snprintf(loc, sizeof loc, "%s lives at %dx%d.\n", res_enum("name", 20), state.npc[20].x, state.npc[20].y);
                }
                tx("%s\r\r\n", loc);
                journalf("%d %s", state.cpl, loc);
                ryl_pause();
                clw();
                tx0(res_text("speak.erethor.together"));
                ryl_pause();
                clw();
                tx0(res_text("speak.erethor.paper"));
                ryl_pause();
                clw();
                state.p[state.cpl].flag[8] = 3;
            }
            break;

        /*****************************************************************/
        case 13:
            if (state.p[state.cpl].flag[13] == 0) {
                clw();
                tx0(res_text("speak.thun.legend"));
                ryl_pause();
                clw();
                tx0(res_text("speak.thun.offer"));
                tx0(res_text("speak.buy_nothanks"));
                c = key();
                c = toupper(c);
                if (c == 'B') {
                    if (state.p[state.cpl].gold < 10000) {
                        clw();
                        tx0(res_text("speak.thun.broke"));
                        ryl_pause();
                        clw();
                        break;
                    }
                    state.p[state.cpl].gold -= 10000;
                    clw();
                    tx0(res_text("speak.thun.map_pre"));
                    tx("%dx%d!\r\n\r\n", state.dx[2], state.dy[2]);
                    journalf("%d The Temple of Thun is at %dx%d\n", state.cpl, state.dx[2], state.dy[2]);
                    state.p[state.cpl].flag[13] = 1;
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
        case 14:
            if (state.p[state.cpl].flag[2] == 1) {
                clw();
                tx0(res_text("speak.brother.give_sword"));
                ryl_pause();
                clw();
                tx0(res_text("speak.brother.produces"));
                for (i = 1, x = 0; i < 17; i++) {
                    if (state.p[state.cpl].inventory[i] == 0) {
                        state.p[state.cpl].inventory[i] = 16;
                        x = 1;
                        break;
                    }
                }
                if (!x) {
                    tx0(res_text("speak.brother.cant_carry"));
                    ryl_delay(800);
                    carry = 0;
                    carry = drop_option();
                    if (carry) {
                        clw();
                        tx0(res_text("speak.brother.take_sword"));
                        state.p[state.cpl].inventory[carry] = 16;
                        ryl_delay(800);
                    }
                }
                ryl_pause();
                state.p[state.cpl].flag[2] = 2;
                clw();
                break;
            }
            break;
        /*****************************************************************/
        case 15:
            if (state.p[state.cpl].flag[15] == 0) {
                if (state.npc[23].towne) {
                    snprintf(loc, sizeof loc, "in %s", res_town_name(state.npc[23].towne));
                } else {
                    snprintf(loc, sizeof loc, "at %dx%d", state.npc[23].x, state.npc[23].y);
                }
                clw();
                tx0(res_text("speak.nymph.know_pre"));
                tx("%s knows of the\r\n", res_enum("name", 23));
                tx0(res_text("speak.nymph.know_post"));
                ryl_pause();
                clw();
                tx("%s lives\r\n", res_enum("name", 23));
                tx("%s\r\n\r\n", loc);
                journalf("%d %s lives %s.\n", state.cpl, res_enum("name", 23), loc);
                state.p[state.cpl].flag[15] = 1;
                ryl_pause();
                clw();
            }
            break;
        /*****************************************************************/
        case 18:
            if (state.p[state.cpl].flag[6] == 2) {
                state.p[state.cpl].flag[6] = 3;
                clw();
                tx0(res_text("speak.thief2.push"));
                ryl_pause();
                clw();
                tx0(res_text("speak.thief2.smith_pre"));
                tx("name of %s...\r\n\r\n", res_enum("name", 24));
                ryl_pause();
                clw();
                tx0(res_text("speak.thief2.let_go"));
                ryl_pause();
                clw();
            }
            break;
        /*****************************************************************/
        case 19:
            if (state.p[state.cpl].flag[19] == 0) {
                clw();
                tx0(res_text("speak.husband.depressed"));
                ryl_pause();
                clw();
                tx0(res_text("speak.husband.fortune"));
                ryl_pause();
                clw();
                tx0(res_text("speak.husband.cave"));
                journalf("%d The orc cavern is at 42x23.\n", state.cpl);
                ryl_pause();
                clw();
                state.p[state.cpl].flag[19] = 1;
            }
            if (state.p[state.cpl].flag[19] == 2) {
                clw();
                tx0(res_text("speak.husband.reunion"));
                ryl_pause();
                clw();
                tx0(res_text("speak.husband.gives"));
                ryl_pause();
                clw();
                state.p[state.cpl].flag[19] = 3;
                state.p[state.cpl].gold += 3000;
                for (i = 1, x = 0; i < 17; i++) {
                    if (state.p[state.cpl].inventory[i] == 0) {
                        state.p[state.cpl].inventory[i] = 26;
                        x++;
                        break;
                    }
                }
                for (i = 1; i < 17; i++) {
                    if (state.p[state.cpl].inventory[i] == 0) {
                        state.p[state.cpl].inventory[i] = 110;
                        x++;
                        break;
                    }
                }
                for (i = 1; i < 17; i++) {
                    if (state.p[state.cpl].inventory[i] == 0) {
                        state.p[state.cpl].inventory[i] = 38;
                        x++;
                        break;
                    }
                }
                for (i = 1; i < 17; i++) {
                    if (state.p[state.cpl].inventory[i] == 0) {
                        state.p[state.cpl].inventory[i] = 101;
                        x++;
                        break;
                    }
                }
                for (i = 1; i < 17; i++) {
                    if (state.p[state.cpl].inventory[i] == 0) {
                        state.p[state.cpl].inventory[i] = 45;
                        x++;
                        break;
                    }
                }
                if (x < 5) {
                    clw();
                    tx0(res_text("speak.husband.cant_carry"));
                    ryl_pause();
                }
                clw();
            }
            break;
        /*****************************************************************/
        case 20:
            if (state.p[state.cpl].flag[8] == 3) {
                clw();
                tx0(res_text("speak.minstrel.reads_pre"));
                tx("%s cries tears\r\n", res_enum("name", 20));
                tx0(res_text("speak.minstrel.reads_post"));
                ryl_pause();
                for (i = 1, x = 0; i < 17; i++) {
                    if (state.p[state.cpl].inventory[i] == 0) {
                        state.p[state.cpl].inventory[i] = 28;
                        x++;
                        break;
                    }
                }
                if (!x) {
                    clw();
                    tx0(res_text("speak.minstrel.cant_carry"));
                    ryl_delay(800);
                    carry = 0;
                    carry = drop_option();
                    if (carry) {
                        clw();
                        tx0(res_text("speak.minstrel.take_cloak"));
                        ryl_delay(800);
                        state.p[state.cpl].inventory[carry] = 28;
                    }
                    ryl_pause();
                }
                clw();
                tx0(res_text("speak.minstrel.travel_pre"));
                tx("%s the very\r\n", res_enum("name", 8));
                tx0(res_text("speak.minstrel.travel_post"));
                ryl_pause();
                clw();
                state.p[state.cpl].flag[8] = 4;
            }
            break;
        /*****************************************************************/
        case 23:
            if (state.p[state.cpl].flag[15] == 1 && state.p[state.cpl].flag[23] == 0) {
                clw();
                tx0(res_text("speak.wizard3t.intro"));
                ryl_pause();
                clw();
                tx0(res_text("speak.wizard3t.agree"));
                ryl_pause();
                clw();
                tx0(res_text("speak.wizard3t.task1_pre"));
                tx("%dx%d, and kill all life\r\n", state.dx[3], state.dy[3]);
                tx0(res_text("speak.wizard3t.task1_post"));
                journalf("%d The kobold lair is at %dx%d.\n", state.cpl, state.dx[3], state.dy[3]);
                ryl_pause();
                clw();
                state.p[state.cpl].flag[15] = 2;
                state.p[state.cpl].flag[23] = 1;
                break;
            }
            if (state.p[state.cpl].flag[23] == 2) {
                clw();
                tx0(res_text("speak.wizard3t.pendant"));
                ryl_pause();
                clw();
                tx0(res_text("speak.wizard3t.task2"));
                ryl_pause();
                clw();
                state.p[state.cpl].flag[23] = 3;
                break;
            }
            if (state.p[state.cpl].flag[23] == 4) {
                clw();
                tx0(res_text("speak.wizard3t.task3"));
                ryl_pause();
                clw();
                tx0(res_text("speak.wizard3t.task3_pre"));
                tx("%dx%d. Be sure to treat\r\n", state.dx[4], state.dy[4]);
                tx0(res_text("speak.wizard3t.task3_post"));
                journalf("%d The dwarf village is at %dx%d.\n", state.cpl, state.dx[4], state.dy[4]);
                ryl_pause();
                clw();
                state.p[state.cpl].flag[23] = 5;
                break;
            }
            if (state.p[state.cpl].flag[23] == 8) {
                clw();
                tx0(res_text("speak.wizard3t.grateful_pre"));
                tx("a dungeon near %dx%d!\r\n", state.dx[6], state.dy[6]);
                tx0(res_text("speak.wizard3t.grateful_post"));
                journalf("%d The Master Crown dungeon is at %dx%d.\n", state.cpl, state.dx[6], state.dy[6]);
                ryl_pause();
                clw();
                state.p[state.cpl].flag[23] = 9;
                break;
            }
            break;
        /*****************************************************************/
        case 24:
            if (state.p[state.cpl].flag[6] == 3) {
                clw();
                tx0(res_text("speak.smith.ask"));
                ryl_pause();
                clw();
                tx0(res_text("speak.smith.sorry"));
                ryl_pause();
                clw();
                tx0(res_text("speak.smith.pursued"));
                ryl_pause();
                clw();
                tx0(res_text("speak.smith.clubbed"));
                ryl_pause();
                clw();
                state.p[state.cpl].flag[6] = 4;
            }
            break;
        /*****************************************************************/
        case 22:
            if (state.p[state.cpl].flag[22] == 0) {
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
                        tx0(res_text("speak.jazzy_ogre.accept_pre"));
                        tx("GO TO CAVE AT %dx%d!\r\n", state.dx[7], state.dy[7]);
                        tx0(res_text("speak.jazzy_ogre.accept_post"));
                        journalf("%d The 'Beast Cave' is at %dx%d.\n", state.cpl, state.dx[7], state.dy[7]);
                        state.p[state.cpl].flag[22] = 1;
                        state.p[state.cpl].inventory[d - 64] = 0;
                        ryl_pause();
                        clw();
                        break;
                    }
                }
            }
            break;
        /*****************************************************************/
        case 33:
            if (state.p[state.cpl].flag[33] == 0) {
                clw();
                tx0(res_text("speak.hermit.welcome"));
                ryl_pause();
                clw();
                state.p[state.cpl].flag[33] = 1;
            }
            if (state.p[state.cpl].flag[33] == 2) {
                clw();
                tx0(res_text("speak.hermit.tooth_pre"));
                tx("%dx%d is the cave you\r\n", state.dx[1], state.dy[1]);
                tx0(res_text("speak.hermit.tooth_post"));
                journalf("%d The Fire Shield Dungeon is at %dx%d.\n", state.cpl, state.dx[1], state.dy[1]);
                ryl_pause();
                clw();
                state.p[state.cpl].flag[33] = 3;
            }
            break;
            /*****************************************************************/
    }
}
