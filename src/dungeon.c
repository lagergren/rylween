#include "ryl.h"
#include "res.h"
#include "battle.h"
#include "driv.h"
#include "dungeon.h"
#include "gfx.h"
#include "inventory.h"
#include "primitives.h"
#include "subs.h"
#include "towne.h"

void dungeon(int32_t levels, int32_t flag) {
    /*
	** The flag determines if it is a special dungeon.
	** Set to 0 if it's a randomly discovered one.
	*/

    int32_t i, cl, oc, lvl[26];
    char c, d, ans[80];
    int32_t x = 0;

    blitgfx(insidecavepic);

    state.banished = 0;

    for (i = 1; i < levels + 1; i++) {
        lvl[i] = ryl_random(1, 9);
    }
    switch (flag) {
        case 1:
            lvl[19] = 20;
            lvl[20] = 21;
            break;
        case 2:
            lvl[4] = 22;
            lvl[8] = 23;
            lvl[12] = 24;
            lvl[18] = 25;
            break;
        case 3:
            lvl[20] = 28;
            lvl[19] = 27;
            lvl[18] = 27;
            lvl[17] = 27;
            lvl[16] = 27;
            lvl[15] = 27;
            lvl[14] = 27;
            lvl[13] = 27;
            lvl[12] = 26;
            break;
        case 4:
            lvl[10] = 29;
            break;
        case 5:
            lvl[20] = 30;
            break;
        case 6:
            lvl[3] = 31;
            lvl[6] = 31;
            lvl[9] = 31;
            lvl[14] = 31;
            lvl[17] = 31;
            lvl[19] = 31;
            lvl[20] = 32;
            lvl[21] = 33;
            lvl[22] = 34;
            break;
        case 7:
            lvl[6] = 35;
            lvl[7] = 36;
            break;
        case 8:
            lvl[3] = 37;
            lvl[7] = 37;
            lvl[12] = 38;
            break;
    }

    /*
	** 0: Visited, Nothing left
	** 1: Find an object
	** 2: Find gold
	** 3: Encounter
	** 4: Encounter
	** 5: Encounter
	** 6: Nothing
	** 7: Nothing
	** 8: Find gold in chest
	** 9: Find torches
	** 20: The Guardian of the Fire Shield
  ** 21: The Fire Shield.
	** 22: Riddle 1, Thun
	** 23: Riddle 2, Thun
	** 24: Riddle 3, Thun
	** 25: The Bracelets of Thun
	** 26: Spectrum Message
	** 27: Colour Riddle
	** 28: Nolrim's Tomb & Reebar
	** 29: The Master Crown
	** 30: The Silver Sanctum
	** 31: Hobgoblin Patrol (Kill one, get password)
	** 32: Royal Guard one. Hobgoblin Skeletons
	** 33: Royal Guard two. Hobgoblin Zombies
	** 34: Hobgoblin Chief. Who is it?
	** 35: Orc Nest
	** 36: Tammy's Prison
	** 37: Priests of Erethor
	** 38: The Chamber of the Lute
	*/

    cl = 1;

    while (1) {
        clw();

        switch (lvl[cl]) {
            case 0:
                break;

            case 1:
                lvl[cl] = 0;
                x = ryl_random(0, 4);
                if (x) {
                    x = ryl_random(0, 2);
                    if (x == 0) {
                        oc = ryl_random(0, 13);
                        oc += 1;
                    } /* 0-13 + 1 = 1-14 */
                    if (x == 1) {
                        oc = ryl_random(0, 6);
                        oc += 36;
                    } /* 0-6  + 36= 36-42 */
                    if (x == 2) {
                        oc = ryl_random(0, 7);
                        oc += 39;
                    } /* 0-7  + 39= 39-46 */
                } else { /* 0-11 + 100, ger 100-111 */
                    oc = ryl_random(0, 11);
                    oc += 100;
                }
                tx("You find a\r\n");
                tx("%s.\r\n\r\n", object(oc));
                tx("(T)ake it\r\n");
                tx("(L)eave it\r\n");
                c = key();
                c = toupper(c);
                if (c == 'T') {
                    x = inv_add(oc) != 0;
                    if (!x) {
                        clw();
                        tx("You can't carry more.\r\n");
                        ryl_delay(800);
                    } else {
                        tx("\r\n");
                        tx("Ok.\r\n");
                        ryl_delay(800);
                    }
                }
                break;

            case 2:
                x = ryl_random(40, 339);
                tx("You find %d\r\n", x);
                tx("gold pieces.\r\n\r\n");
                state.p[state.cpl].gold += x;
                lvl[cl] = 0;
                ryl_pause();
                break;

            case 3:
                encounter(ryl_random(1, 10), 0);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                break;
            case 4:
                encounter(ryl_random(1, 10), 0);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                break;
            case 5:
                encounter(ryl_random(60, 79), 0);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                break;

            case 6:
                tx("You find nothing\r\n");
                tx("special on this level.\r\n\r\n");
                lvl[cl] = 0;
                ryl_pause();
                break;
            case 7:
                tx("This level is empty.\r\n\r\n");
                lvl[cl] = 0;
                ryl_pause();
                break;

            case 8:
                tx("You find a chest!\r\n\r\n");
                tx("(O)pen chest\r\n");
                tx("(L)eave\r\n");
                c = key();
                c = toupper(c);
                if (c == 'O') {
                    lvl[cl] = 0;
                    x = 4;
                    for (i = 1; i <= res_party_member_max(); i++) {
                        if (state.p[state.cpl].cl[i] == 2) {
                            clw();
                            tx("%s checks the\r\n", state.p[state.cpl].name[i]);
                            tx("chest for traps.\r\n");
                            ryl_delay(1000);
                            x = 2;
                            break;
                        }
                    }
                    if (ryl_random(0, 9) < x) {
                        clw();
                        tx("A TRAP!\r\n");
                        ryl_delay(600);
                        switch (ryl_random(0, 2)) {
                            case 0:
                                tx("Poisonous gas\r\n");
                                tx("hisses out through\r\n");
                                tx("hidden holes.\r\n\r\n");
                                ryl_delay(1400);
                                tx("The party coughs\r\n");
                                tx("and retches.\r\n");
                                ryl_delay(600);
                                tx("Finally. The gas clears.\r\n\r\n");
                                break;
                            case 1:
                                tx("A spiked pitfall\r\n");
                                tx("opens in the floor\r\n");
                                tx("beneath you.\r\n\r\n");
                                ryl_delay(1400);
                                tx("You climb out of it,\r\n");
                                tx("badly wounded.\r\n\r\n");
                                break;
                            case 2:
                                tx("A large boulder\r\n");
                                tx("drops from above!!!\r\n");
                                ryl_delay(1400);
                                tx("Badly bruised, you\r\n");
                                tx("try to stand up again.\r\n\r\n");
                                break;
                        }
                        for (i = 1; i <= res_party_member_max(); i++) {
                            state.p[state.cpl].hp[i] -= 4 * state.p[state.cpl].lv[i];
                            if (state.p[state.cpl].hp[i] < 1) {
                                state.p[state.cpl].hp[i] = 1;
                            }
                        }
                        ryl_pause();
                    }
                    clw();
                    tx("The chest contained\r\n");
                    x = ryl_random(0, 99);
                    x += 20;
                    x += state.p[state.cpl].exp[1] / 500;
                    x += 300;
                    tx("%d pieces of gold.\r\n\r\n", x);
                    state.p[state.cpl].gold += x;
                    ryl_pause();
                }
                break;

            case 9:
                clw();
                x = ryl_random(0, 2);
                x += 2;
                tx("You find %d torches.\r\n\r\n", x);
                state.p[state.cpl].torches += x;
                lvl[cl] = 0;
                ryl_pause();
                break;

                /***************************************************************/

            case 20:
                clw();
                tx("As you enter this level\r\n");
                tx("you hear a horrible\r\n");
                tx("evil laughter.\r\n");
                tx("Suddenly!\r\n");
                tx("Out of the shadows,\r\n");
                tx("something HIDEOUS\r\n");
                tx("emerges.\r\n\r\n");
                ryl_pause();
                clw();
                encounter(11, 1);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                lvl[cl] = 0;
                ryl_pause();
                break;
            case 21:
                clw();
                tx("After you have bested\r\n");
                tx("the foul thing, the\r\n");
                tx("dungeon is strangely\r\n");
                tx("quiet. Upon entering\r\n");
                tx("this level, you find the\r\n");
                tx("marvelous FIRE SHIELD on\r\n");
                tx("a pedestal! You take it.\r\n\r\n");
                ryl_pause();
                lvl[cl] = 0;
                clw();
                party_award_special(PS_FIRESHIELD);
                state.p[state.cpl].flag[33] = 4;
                break;

            case 22:
                clw();
                tx("You stand before a\r\n");
                tx("mage dressed entirenly\r\n");
                tx("in red.\r\n\r\n");
                tx("- Answer this, fools, or\r\n");
                tx("  die where you stand!\r\n\r\n");
                ryl_pause();
                clw();
                tx("'Between fire and ice,\r\n");
                tx("who ends the fight?\r\n");
                tx("Who stands between those\r\n");
                tx("two? Not red, not white\r\n");
                tx("but blue!\r\n\r\n");
                tx("Answer: ");
                getstr(10, ans, sizeof ans);
                lvl[cl] = 0;
                clw();
                if (!stricmp("water", ans)) {
                    tx("- Well done. Pass.\r\n\r\n");
                    ryl_pause();
                    break;
                } else {
                    tx("- FOOLS! Meet thy doom!\r\n");
                    tx("at a gesture from him,\r\n");
                    tx("something evil appears.\r\n\r\n");
                    ryl_pause();
                    encounter(12, 1);
                    clw();
                    clr_map_window();
                    blitgfx(insidecavepic);
                    break;
                }
                break;

            case 23:
                clw();
                tx("You stand before a\r\n");
                tx("mage dressed entirenly\r\n");
                tx("in green.\r\n\r\n");
                tx("- Answer this, fools, or\r\n");
                tx("  die where you stand!\r\n\r\n");
                ryl_pause();
                clw();
                tx("'Siblings we are,\r\n");
                tx("I'm black and she's\r\n");
                tx("white. Each dreary\r\n");
                tx("evening, I swallow her\r\n");
                tx("light.'\r\n\r\n");
                tx("Answer: ");
                getstr(10, ans, sizeof ans);
                lvl[cl] = 0;
                clw();
                if (!stricmp("night", ans) || !stricmp("darkness", ans)) {
                    tx("- Well done. Pass.\r\n\r\n");
                    ryl_pause();
                    break;
                } else {
                    tx("- FOOLS! Meet thy doom!\r\n");
                    tx("at a gesture from him,\r\n");
                    tx("something evil appears.\r\n\r\n");
                    ryl_pause();
                    encounter(13, 1);
                    clw();
                    break;
                }
                break;

            case 24:
                clw();
                tx("You stand before a\r\n");
                tx("mage dressed entirenly\r\n");
                tx("in blue.\r\n\r\n");
                tx("- Answer this, fools, or\r\n");
                tx("  die where you stand!\r\n\r\n");
                ryl_pause();
                clw();
                tx("'Tall as a house,\r\n");
                tx("round as a cup, and\r\n");
                tx("all the king's horses\r\n");
                tx("can't draw me up! What\r\n");
                tx("am I?'\r\n\r\n");
                tx("Answer: ");
                getstr(10, ans, sizeof ans);
                lvl[cl] = 0;
                clw();
                if ((!stricmp("well", ans)) || (!stricmp("a well", ans))) {
                    tx("- Well done. Pass.\r\n\r\n");
                    ryl_pause();
                    break;
                } else {
                    tx("- FOOLS! Meet thy doom!\r\n");
                    tx("at a gesture from him,\r\n");
                    tx("something evil appears.\r\n\r\n");
                    ryl_pause();
                    encounter(14, 1);
                    clw();
                    break;
                }
                break;

            case 25:
                clw();
                tx("This whole chamber\r\n");
                tx("shines of a bright\r\n");
                tx("white light! On a\r\n");
                tx("table before you,\r\n");
                tx("lie the fabled\r\n");
                tx("Bracelets of Thun!\r\n");
                tx("The glow fades as\r\n");
                tx("you put them in your\r\n");
                tx("backpack.\r\n\r\n");
                lvl[cl] = 0;
                ryl_pause();
                clw();
                party_award_special(PS_BRACELETS);
                state.p[state.cpl].flag[13] = 2;
                break;

            case 26:
                clw();
                tx("A message on the wall\r\n");
                tx("reads:\r\n\r\n");
                tx("'M U R T C E P S'\r\n");
                tx("THiNgs aren't aLways iN\r\n");
                tx("theIR naTuRAl oRDeR...\r\n\r\n");
                ryl_pause();
                break;

            case 27:
                clw();
                tx("A plaque here reads:\r\n");
                tx("ANTECHAMBER #%d\r\n\r\n", cl - 12);
                ryl_pause();
                clw();
                tx("Before you are seven\r\n");
                tx("squares of 20x20 feet.\r\n");
                tx("To get across this room\r\n");
                tx("you will need to step\r\n");
                tx("on one of them.\r\n\r\n");
                ryl_pause();
                clw();
                tx("1. Red Square\r\n");
                tx("2. Orange Square\r\n");
                tx("3. Yellow Square\r\n");
                tx("4. Green Square\r\n");
                tx("5. Blue Square\r\n");
                tx("6. Indigo Square\r\n");
                tx("7. Violet Square\r\n");
                while (1) {
                    c = key();
                    if (c >= '1' && c <= '7') {
                        break;
                    }
                }
                if ((cl == 13 && c == '7') || (cl == 14 && c == '6') || (cl == 15 && c == '5') ||
                    (cl == 16 && c == '4') || (cl == 17 && c == '3') || (cl == 18 && c == '2') ||
                    (cl == 19 && c == '1')) {
                    clw();
                    tx("You feel a slight\r\n");
                    tx("tingle in your bodies,\r\n");
                    tx("but otherwise, nothing\r\n");
                    tx("happens.\r\n\r\n");
                    ryl_pause();
                    break;
                }
                clw();
                tx("With a horrible scream\r\n");
                tx("a shape of pure colour\r\n");
                tx("forms in thin air before\r\n");
                tx("you!\r\n\r\n");
                ryl_pause();
                encounter(16, 1);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                break;

            case 28:
                clw();
                tx("Nolrim's body rests on\r\n");
                tx("a stone slab in this room.\r\n");
                tx("On his chest lies the\r\n");
                tx("dagger you seek. You put\r\n");
                tx("it in your backpack.\r\n\r\n");
                ryl_pause();
                state.p[state.cpl].flag[23] = 7;
                lvl[cl] = 0;
                break;

            case 29:
                clw();
                tx("FINALLY! In the hands of\r\n");
                tx("a long dead warrior, who\r\n");
                tx("is placed like a statue\r\n");
                tx("by the east wall of this\r\n");
                tx("chamber, is the Master\r\n");
                tx("Crown. The most powerful\r\n");
                tx("object in the realm.\r\n");
                tx("It shines with an inner\r\n");
                tx("light.\r\n\r\n");
                ryl_pause();
                clw();
                tx("...but as you take it,\r\n");
                tx("THE WARRIOR COMES\r\n");
                tx("TO LIFE!!!\r\n\r\n");
                ryl_pause();
                clw();
                encounter(17, 1);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                state.p[state.cpl].flag[23] = 10;
                party_award_special(PS_MASTERCROWN);
                lvl[cl] = 0;
                break;

            case 30:
                clw();
                tx("This room shines like\r\n");
                tx("silver. You can see your\r\n");
                tx("mirror image in the\r\n");
                tx("walls. After searching\r\n");
                tx("the room thorougly, you\r\n");
                tx("find nothing. No Silver\r\n");
                tx("Key, no treasure.\r\n\r\n");
                ryl_pause();
                clw();
                tx("You curse the rouge who\r\n");
                tx("tricked you and are just\r\n");
                tx("about to leave...\r\n\r\n");
                ryl_pause();
                clw();
                tx("...when you spot a paper\r\n");
                tx("of some kind in a crack\r\n");
                tx("in the wall... You pull\r\n");
                tx("it out and read...\r\n\r\n");
                ryl_pause();
                clw();
                tx("'Guild Master: This is\r\n");
                tx("the proof that I, %s\r\n", res_enum("name", 18));
                tx("am worthy of the title\r\n");
                tx("Master Thief.'\r\n");
                tx("      - %s\r\n\r\n", res_enum("name", 18));
                ryl_pause();
                clw();
                state.p[state.cpl].flag[6] = 2;
                lvl[cl] = 0;
                break;

            case 31:
                clw();
                tx("You bump into a hobgoblin\r\n");
                tx("patrol. With shiny and\r\n");
                tx("well used scimitars, they\r\n");
                tx("attack you!\r\n\r\n");
                ryl_pause();
                clw();
                encounter(9, 1);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                if (ryl_chance(39)) {
                    clw();
                    tx("You find a scribbled\r\n");
                    tx("note on the leader:\r\n");
                    tx("Password: 'RISH EKKIN'\r\n\r\n");
                    ryl_pause();
                    clw();
                }
                break;

            case 32:
                clw();
                tx("As you enter this room\r\n");
                tx("an army of hobgoblin\r\n");
                tx("skeletons attack you!\r\n\r\n");
                ryl_pause();
                encounter(24, 1);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                lvl[cl] = 0;
                break;

            case 33:
                clw();
                tx("As you enter this room\r\n");
                tx("an army of undead\r\n");
                tx("hobgoblins, smelling\r\n");
                tx("of rotting flesh and\r\n");
                tx("brandising gleaming\r\n");
                tx("tridents attack you.\r\n\r\n");
                ryl_pause();
                encounter(25, 1);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                lvl[cl] = 0;
                break;

            case 34:
                clw();
                tx("You stand before a\r\n");
                tx("silk curtain. You hear\r\n");
                tx("someone moving behind\r\n");
                tx("it. A voice calls:\r\n");
                tx("- What's the password?\r\n\r\n");
                ryl_pause();
                clw();
                tx("Password: ");
                getstr(15, ans, sizeof ans);
                if (!stricmp(ans, "RISH EKKIN")) {
                    clw();
                    tx("- Step inside and identify\r\n");
                    tx("yourself. You do so. The\r\n");
                    tx("hobgoblin chief eyes you\r\n");
                    tx("suspiciously for a few\r\n");
                    tx("seconds. Not hestitating\r\n");
                    tx("a moment you attack.\r\n\r\n");
                    ryl_pause();
                    encounter(23, 1);
                    clr_map_window();
                    blitgfx(insidecavepic);
                    clw();
                } else {
                    clw();
                    tx("- GUARDS! GUARDS!\r\n\r\n");
                    tx("(Hobgoblin guards rush\r\n");
                    tx(" to confront you.)\r\n");
                    ryl_delay(2000);
                    encounter(9, 1);
                    encounter(23, 1);
                    clr_map_window();
                    blitgfx(insidecavepic);
                    clw();
                }
                clw();
                tx("You have killed the chief\r\n");
                tx("of the hobgoblin tribe.\r\n");
                tx("The precious Silver Key\r\n");
                tx("hangs around his neck.\r\n");
                tx("With trembling hands\r\n");
                tx("you pick it up!!!\r\n\r\n");
                ryl_pause();
                clw();
                party_award_special(PS_SILVERKEY);
                state.p[state.cpl].flag[6] = 6;
                lvl[cl] = 0;
                break;

            case 35:
                lvl[cl] = 0;
                clw();
                tx("Some orcs watch a trapdoor\r\n");
                tx("in the floor here. As\r\n");
                tx("you enter, they attack!\r\n\r\n");
                ryl_pause();
                encounter(2, 1);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                tx("The last of the guardians\r\n");
                tx("lie dead. Suddenly, more\r\n");
                tx("orcs rush out of their\r\n");
                tx("sleeping quarters,\r\n");
                tx("disturbed by the noise.\r\n\r\n");
                ryl_pause();
                encounter(2, 1);
                encounter(2, 1);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                tx("You have slain the orcs.\r\n\r\n");
                ryl_pause();
                break;

            case 36:
                clw();
                if (state.p[state.cpl].flag[19] == 1) {
                    lvl[cl] = 0;
                    tx("The peasant's wife is in\r\n");
                    tx("this room. You untie\r\n");
                    tx("her bonds and let her\r\n");
                    tx("join your party!\r\n\r\n");
                    state.p[state.cpl].flag[19] = 2;
                    ryl_pause();
                }
                break;

            case 37:
                clw();
                tx("You are spotted by\r\n");
                tx("some priests. They\r\n");
                tx("attack you with blind\r\n");
                tx("fury.\r\n\r\n");
                ryl_pause();
                encounter(32, 1);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                break;

            case 38:
                clw();
                tx("This is the altar chamber\r\n");
                tx("of the temple. Hooded\r\n");
                tx("shapes kneel at the\r\n");
                tx("blood stained altar at\r\n");
                tx("the end of this chamber.\r\n\r\n");
                ryl_pause();
                clw();
                tx("You rush to attack the\r\n");
                tx("priests...\r\n\r\n");
                ryl_pause();
                clw();
                tx("They quickly turn around\r\n");
                tx("and draw their daggers.\r\n\r\n");
                ryl_pause();
                clw();
                tx("The acolyte kneels before\r\n");
                tx("the altar in silent prayer\r\n");
                tx("while the others form a\r\n");
                tx("circle around him to\r\n");
                tx("protect him.\r\n\r\n");
                ryl_pause();
                clw();
                tx("Suddenly there is an\r\n");
                tx("ear splitting bang\r\n");
                tx("and a ghastly creature\r\n");
                tx("emerges from the shadows\r\n");
                tx("of this uncanny sanctuary.\r\n\r\n");
                ryl_pause();
                encounter(33, 1);
                encounter(32, 1);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                tx("More priests rush to\r\n");
                tx("attack you...\r\n\r\n");
                ryl_pause();
                encounter(32, 1);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                tx("The last priest lies\r\n");
                tx("dead. You stand still\r\n");
                tx("for a moment, and then\r\n");
                tx("start searching the\r\n");
                tx("hall for the Lute.\r\n\r\n");
                ryl_pause();
                clw();
                tx("%s suddenly\r\n", state.p[state.cpl].name[3]);
                tx("hears something behind\r\n");
                tx("him!\r\n\r\n");
                ryl_pause();
                clw();
                tx("You scream in terror\r\n");
                tx("when you see the source\r\n");
                tx("of that sound! ERETHOR\r\n");
                tx("HAS COME TO LIFE ONCE\r\n");
                tx("AGAIN!!!\r\n\r\n");
                ryl_pause();
                encounter(33, 1);
                clr_map_window();
                blitgfx(insidecavepic);
                clw();
                tx("This time Erethor\r\n");
                tx("remains dead. His flesh\r\n");
                tx("blisters and bursts.\r\n");
                tx("A rotten smell spreads\r\n");
                tx("throughout the hall.\r\n\r\n");
                ryl_pause();
                clw();
                tx("%s finds the\r\n", state.p[state.cpl].name[4]);
                tx("Lute on a pedestal,\r\n");
                tx("and you are all glad\r\n");
                tx("to leave this place.\r\n\r\n");
                lvl[cl] = 0;
                state.p[state.cpl].flag[8] = 2;
                ryl_pause();
                clw();
                break;
        }

        /* Pick an alternative - Encounter over */

        if (cl >= 20) {
            lvl[cl] = 0;
        }
        while (1) {
            clw();
            statusline();
            tx("You are on level %d.\r\n\r\n", cl);
            tx("(D) Descend\r\n");
            tx("(A) Ascend\r\n\r\n");
            c = key();
            c = toupper(c);

            if (c == '?') {
                help();
            }
            if (c == 'L') {
                drop();
            }
            if (c == 'T') {
                armouroff();
            }
            if (c == 'P') {
                weaponoff();
            }
            if (c == 'W') {
                weaponr();
            }
            if (c == 'R') {
                armourr();
            }
            if (c == 'Q') {
                quaff();
            }
            if (c == 'Z') {
                spell(1); /* 0 means over ground */
                if (state.banished) {
                    for (i = 1; i < levels; i++) {
                        if (lvl[i] >= 3 && lvl[i] <= 5) {
                            lvl[i] = 0;
                        }
                    }
                }
            }
            if (c == 'V') {
                showparty();
                clw();
                tx("Who? (1-4/Q/A)\r\n");
                while (1) {
                    d = key();
                    if ((d >= '1' && d <= '4') || toupper(d) == 'Q' || toupper(d) == 'A') {
                        break;
                    }
                }
                if (d >= '1' && d <= '4') {
                    show(d - 48);
                }
                if (toupper(d) == 'A') {
                    for (i = 1; i <= res_party_member_max(); i++) {
                        show(i);
                    }
                }
                clw();
            }

            if (c == 'D') {
                if (!state.p[state.cpl].torches) {
                    clw();
                    tx("You have used your last\r\n");
                    tx("torch. You can't go\r\n");
                    tx("any further.\r\n\r\n");
                    ryl_pause();
                    clw();
                } else {
                    if (cl < levels) {
                        state.p[state.cpl].torches--;
                    }
                    if (cl == levels) {
                        clw();
                        tx("The cave doesn't\r\n");
                        tx("go deeper.\r\n");
                        ryl_delay(1000);
                    } else {
                        clw();
                        tx("You enter level %d.\r\n", cl + 1);
                        ryl_delay(600);
                        cl++;
                        break;
                    }
                }
            }

            if (c == 'A') {
                if (cl == 1) {
                    clw();
                    tx("You leave the\r\n");
                    tx("hideous cavern!\r\n\r\n");
                    ryl_pause();
                    clr_map_window();
                    update_map();
                    clw();
                    return;
                } else {
                    cl--;
                    break;
                }
            }
        }
    }
}
