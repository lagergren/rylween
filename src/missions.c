#include "ryl.h"
#include "res.h"

uint8_t mission_control() {
    int32_t i, gems;
    char send[144];
    char c;

    /* Mission Control */

    /******************************************************************/
    if (state.p[state.cpl].flag[8] == 1 && state.p[state.cpl].x == 6 && state.p[state.cpl].y == 20) {
        clw();
        tx("Here is the temple of\r\n");
        tx("the chaos lord Erethor.\r\n");
        tx("A winding staircase\r\n");
        tx("spirals down between\r\n");
        tx("broken pillars.\r\n\r\n");
        tx("(E)nter\r\n");
        tx("(L)eave\r\n");
        c = key();
        c = toupper(c);
        if (c == 'E') {
            dungeon(12, 8);
            clr_map_window();
            update_map();
        } else {
            clw();
        }
    }

    /******************************************************************/
    if (state.p[state.cpl].flag[19] == 1 && state.p[state.cpl].x == 42 && state.p[state.cpl].y == 23) {
        clw();
        tx("There is a cave entrance\r\n");
        tx("in the foothills to the\r\n");
        tx("east. Is this the place\r\n");
        tx("%s talked about?\r\n\r\n", res_enum("name", 19));
        tx("(E)nter\r\n");
        tx("(L)eave\r\n");
        c = key();
        c = toupper(c);
        if (c == 'E') {
            dungeon(7, 7);
            clr_map_window();
            update_map();
        } else {
            clw();
        }
    }
    /******************************************************************/
    if (state.p[state.cpl].flag[1] == 1 && rand() % 100 > 90) {
        clw();
        tx("You come across the\r\n");
        tx("camp of the bandits.\r\n");
        tx("One of them spots you\r\n");
        tx("and they attack!\r\n\r\n");
        ryl_pause();
        encounter(8, 1);
        update_map();
        clw();
        tx("You find the cargo\r\n");
        tx("the merchant wanted\r\n");
        tx("you to bring him.\r\n\r\n");
        ryl_pause();
        clw();
        state.p[state.cpl].flag[1] = 2;
    }
    /******************************************************************/
    if (state.p[state.cpl].flag[33] == 1 && rand() % 100 > 90) {
        encounter(3, 1);
        update_map();
        clw();
        tx("You break off a troll\r\n");
        tx("tooth to give the hermit.\r\n");
        tx("After wiping the slime of\r\n");
        tx("it, you put it in your\r\n");
        tx("backpack.\r\n\r\n");
        ryl_pause();
        clw();
        state.p[state.cpl].flag[33] = 2;
    }
    if (state.p[state.cpl].flag[33] == 3 && state.p[state.cpl].x == state.dx[1] && state.p[state.cpl].y == state.dy[1]) {
        clw();
        tx("You have found a dungeon.\r\n");
        tx("The entrance has been\r\n");
        tx("cut out of some huge\r\n");
        tx("animal's skull.\r\n\r\n");
        tx("(E)nter\r\n");
        tx("(L)eave\r\n");
        c = key();
        c = toupper(c);
        if (c == 'E') {
            dungeon(20, 1);
            clr_map_window();
            update_map();
        }
    }
    /******************************************************************/
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
        tx("%s", res_text("common.yes"));
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
    if (state.p[state.cpl].flag[23] == 3 && state.map[state.p[state.cpl].y][state.p[state.cpl].x] == 't') {
        clw();
        tx("On the ground before you\r\n");
        tx("grows the skullweed plant.\r\n");
        tx("You break off a few plants\r\n");
        tx("and put in your backpack.\r\n\r\n");
        ryl_pause();
        clw();
        state.p[state.cpl].flag[23] = 4;
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
    if (state.p[state.cpl].flag[22] == 2 && state.map[state.p[state.cpl].y][state.p[state.cpl].x] == 'f') {
        clw();
        tx("Something about this whole\r\n");
        tx("area is weird. The air\r\n");
        tx("seems to be loaded with\r\n");
        tx("electricity. Suddenly you\r\n");
        tx("spot an object on the\r\n");
        tx("ground. It is a delicate\r\n");
        tx("ivory statue in the shape\r\n");
        tx("of a human foot. THIS IS\r\n");
        tx("THE FOOT OF JAZZY!\r\n\r\n");
        ryl_pause();
        clw();
        tx("Just as you touch it,\r\n");
        tx("the sky splits open and\r\n");
        tx("at the wailing of hundreds\r\n");
        tx("of tortured souls, Jazzy's\r\n");
        tx("minions attack you, for\r\n");
        tx("the heresy they find you\r\n");
        tx("guilty of...\r\n\r\n");
        ryl_pause();
        clw();
        encounter(20, 1);
        update_map();
        clw();
        tx("Quiet settles again, as\r\n");
        tx("you destroy the foul\r\n");
        tx("creatures, one by one.\r\n");
        tx("You reach for the foot\r\n");
        tx("again, when suddenly,\r\n");
        tx("something EVEN MORE\r\n");
        tx("horrible happens...\r\n\r\n");
        ryl_pause();
        clw();
        tx("A twenty foot tall\r\n");
        tx("being materializes\r\n");
        tx("in front of you! You will\r\n");
        tx("have to fight Jazzy,\r\n");
        tx("himself!\r\n\r\n");
        ryl_pause();
        clw();
        encounter(21, 1);
        update_map();
        clw();
        tx("Today, you have killed a\r\n");
        tx("god. You watch silently as\r\n");
        tx("the huge body starts to\r\n");
        tx("melt. It fades away into\r\n");
        tx("Nothingness. This time,\r\n");
        tx("when you pick up the foot,\r\n");
        tx("nothing happens.\r\n\r\n");
        ryl_pause();
        clw();
        state.p[state.cpl].flag[22] = 3;
        state.p[state.cpl].jazzyfoot = 1;
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
    if (state.p[state.cpl].flag[6] == 4 && rand() % 100 > 90) {
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
