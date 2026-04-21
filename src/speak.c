#include "ryl.h"

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

void speak(int bld, int tow, int xxx, int yyy) {
    int talk, i, j, x, reference[40];
    char c, d;
    int rumours = 0;

    clw();
    if (tow) {
        for (i = 1, j = 1, x = 0; i < 40; i++) {
            if (npc[i].bl == bld && npc[i].towne == tow) {
                tx("%c. %-8s (%s)\r\n", j + 64, name[npc[i].nr], jobname[npc[i].job]);
                reference[j] = npc[i].nr;
                j++;
                x = 1;
            }
        }
        if (!x) {
            clw();
            tx("Noone is here!\r\n");
            Delay(50);
            return;
        }
        tx("\r\n");
        c = key();
        c = toupper(c);
        if (c < 65 || c > (j + 63))
            return;
        talk = reference[c - 64];
    } else {
        for (i = 1, talk = 0; i < 40; i++)
            if (npc[i].x == p[cpl].x && npc[i].y == p[cpl].y)
                talk = i;
        if (!talk)
            return; /* Error */
    }

    if (npc[talk].visited[cpl]) {
        clw();
        tx("%s nods as\r\n", name[talk]);
        if (npc[talk].job != 13)
            tx("he recognizes you.\r\n");
        else
            tx("she recognizes you.\r\n");
        Delay(40);
    } else
        npc[talk].visited[cpl] = 1;

    while (1) {
        /* Flag-koll */

        flaggkoll(talk);

        clw();
        tx("Chatting with\r\n");
        tx("%s:\r\n\r\n", name[talk]);

        /* Passed */

        tx("(C)hitchat\r\n");
        tx("(Q)uestion\r\n");
        tx("(R)umors\r\n");
        tx("(B)ye\r\n");
        c = key();
        c = toupper(c);
        if (c == 'B') {
            clw();
            return;
        }

        if (c == 'R') {
            clw();
            if (!rumours) {
                tx("%s tells you:\r\n\r\n", name[talk]);
                rumour();
                ryl_pause();
                rumours = 1;
            } else {
                tx("- Sorry. There's\r\n");
                tx("nothing new I can\r\n");
                tx("tell you.\r\n\r\n");
                ryl_pause();
            }
        }

        if (c == 'C') {
            x = (rand() % 3);
            clw();
            switch (x) {
                case 0:
                    if (npc[talk].job == 13) {
                        tx("- You look well today,\r\n");
                        tx("  miss...\r\n");
                    } else {
                        tx("- You look well today,\r\n");
                        tx("  Sir...\r\n");
                    }
                    break;
                case 1:
                    tx("- Nice weather we're\r\n");
                    tx("  having...\r\n");
                    break;
                case 2:
                    if (npc[talk].job == 13) {
                        tx("- Is all well with\r\n");
                        tx("  you, miss?\r\n");
                    } else {
                        tx("- Is all well with\r\n");
                        tx("  you, Sir?\r\n");
                    }
                    break;
            }
            Delay(50);
            tx("\r\n");
            reply_chitchat(x, npc[talk].job);
            tx("\r\n");
            ryl_pause();
        }

        if (c == 'Q') {
            clw();
            tx("(L)odging\r\n");
            tx("(M)agic\r\n");
            tx("(P)eople\r\n");
            tx("(N)othing\r\n");
            d = key();
            d = toupper(d);
            if (d == 'P') {
                tx("\r\n");
                switch (npc[talk].job) {
                    case 1:
                        tx("- Well, I don't know\r\n");
                        tx("that many people.\r\n");
                        break;
                    case 2:
                        tx("- No I'm a lone ranger.\r\n");
                        break;
                    case 3:
                        tx("- Well, I met this\r\n");
                        tx("merchant who seeks\r\n");
                        tx("revenge on a band of\r\n");
                        tx("bandits.\r\n");
                        break;
                    case 4:
                        tx("- OH! Well... not really.\r\n");
                        break;
                    case 5:
                        tx("- I've heard of a hermit\r\n");
                        tx("who lives in the swamps\r\n");
                        tx("by Ollk. Perhaps he can\r\n");
                        tx("help you.\r\n");
                        break;
                    case 6:
                        tx("- No. Sorry. Dunno.\r\n");
                        break;
                    case 7:
                        tx("- I think it's time\r\n");
                        tx("someone found the\r\n");
                        tx("Foot of Jazzy and\r\n");
                        tx("defeated Ydarg!\r\n");
                        break;
                    case 8:
                        tx("- Rumour has it that a\r\n");
                        tx("fighter called %s\r\n", name[3]);
                        tx("has a mission for you.\r\n");
                        break;
                    case 9:
                        tx("- Don't know anyone.\r\n");
                        break;
                    case 10:
                        tx("- ME ALONE AND STRONG!\r\n");
                        break;
                    case 11:
                        tx("- There are many huts\r\n");
                        tx("in the wilderness.\r\n");
                        break;
                    case 12:
                        tx("- Sorry, can't help ya.\r\n");
                        break;
                    case 13:
                        tx("- Well...\r\n");
                        break;
                    case 14:
                        tx("- Want to meet interesting\r\n");
                        tx("travellers? Try the\r\n");
                        tx("inn or the tavern.\r\n");
                        break;
                    case 15:
                        tx("- Don't know about\r\n");
                        tx("anyone particular.\r\n");
                        break;
                    case 16:
                        tx("- Many faces shine\r\n");
                        tx("through the mist.\r\n");
                        break;
                }
                tx("\r\n");
                ryl_pause();
            }
            if (d == 'M') {
                tx("\r\n");
                switch (npc[talk].job) {
                    case 1:
                        tx("- It is said that the\r\n");
                        tx("Master Crown is the\r\n");
                        tx("most powerful object\r\n");
                        tx("in the realm.\r\n");
                        break;
                    case 2:
                        tx("- You will need five\r\n");
                        tx("magical items to\r\n");
                        tx("defeat baron Ydarg.\r\n");
                        break;
                    case 3:
                        tx("- What? I don't believe\r\n");
                        tx("in magic...\r\n");
                        break;
                    case 4:
                        tx("- Magic... Yes, Magic.\r\n");
                        break;
                    case 5:
                        tx("- Yes my son, but we\r\n");
                        tx("are all puny beings\r\n");
                        tx("compared to the gods.\r\n");
                        break;
                    case 6:
                        tx("- I know nothing of it.\r\n");
                        break;
                    case 7:
                        tx("- I don't know much about\r\n");
                        tx("magic. Gems often have\r\n");
                        tx("magical properties,\r\n");
                        tx("though.\r\n");
                        break;
                    case 8:
                        tx("- Magic is the subject\r\n");
                        tx("of many of my songs.\r\n");
                        tx("Ballad of the Fire\r\n");
                        tx("Shield, for instance.\r\n");
                        break;
                    case 9:
                        tx("- GO AWAY!\r\n");
                        break;
                    case 10:
                        tx("- ME HAVE TRUSTY SWORD!\r\n");
                        tx("ME NOT CARE 'BOUT\r\n");
                        tx("MAGIK!\r\n");
                        break;
                    case 11:
                        tx("- Yes, my people were\r\n");
                        tx("created from the magic\r\n");
                        tx("of mother earth.\r\n");
                        break;
                    case 12:
                        tx("- Rumour has it, that\r\n");
                        tx("some ogres have the foot\r\n");
                        tx("of Jazzy, a powerful\r\n");
                        tx("item.\r\n");
                        break;
                    case 13:
                        tx("- I know nothing about\r\n");
                        tx("that.\r\n");
                        break;
                    case 14:
                        tx("- Magic? Well, they say\r\n");
                        tx("that baron Ydarg can\r\n");
                        tx("be defeated only by\r\n");
                        tx("aid of the Silver Key\r\n");
                        break;
                    case 15:
                        tx("- Sorry. Can't help you.\r\n");
                        break;
                    case 16:
                        tx("- The Crown, the Shield,\r\n");
                        tx("the Bracelets and the Foot\r\n");
                        tx("is all you need.\r\n");
                        break;
                }
                tx("\r\n");
                ryl_pause();
            }
            if (d == 'L') {
                tx("\r\n");
                switch (npc[talk].job) {
                    case 1:
                        tx("- Well. The city inn is\r\n");
                        tx("a good try.\r\n");
                        break;
                    case 2:
                        tx("- I sleep under the open\r\n");
                        tx("skies. Just beware of\r\n");
                        tx("bandits if you do this!");
                        break;
                    case 3:
                        tx("- Well. I usually stay at\r\n");
                        tx("the inn when I'm in town.\r\n");
                        break;
                    case 4:
                        tx("- Oh??? Eternal sleep!\r\n");
                        break;
                    case 5:
                        tx("- I hire a room at\r\n");
                        tx("the inn to meditate!\r\n");
                        break;
                    case 6:
                        tx("- I sleep in the shadows.\r\n");
                        break;
                    case 7:
                        tx("- I sleep in my own\r\n");
                        tx("room at my farm.\r\n");
                        break;
                    case 8:
                        tx("- Imagine the beauty\r\n");
                        tx("and danger to camp in\r\n");
                        tx("the wilderness these days.\r\n");
                        break;
                    case 9:
                        tx("- Look, why don't you\r\n");
                        tx("just _GO AWAY_???\r\n");
                        break;
                    case 10:
                        tx("- ME NEVER GET TIRED!\r\n");
                        break;
                    case 11:
                        tx("- I rent a room at the inn!\r\n");
                        break;
                    case 12:
                        tx("- Well, sleeping at the\r\n");
                        tx("inn is often nicer than\r\n");
                        tx("in the wilderness.\r\n");
                        break;
                    case 13:
                        clw();
                        tx("- Would you like to,\r\n");
                        tx("  spend an hour\r\n");
                        tx("  upstairs for %d\r\n", 50 * p[cpl].lv[1]);
                        tx("  gold coins?\r\n\r\n");
                        tx("  (Y)es\r\n");
                        tx("  (N)o\r\n");
                        c = key();
                        c = toupper(c);
                        if (c == 'Y') {
                            if (p[cpl].gold < 50 * p[cpl].lv[1]) {
                                clw();
                                tx("- No money, eh?\r\n");
                                tx("  don't bother\r\n");
                                tx("  to come back!\r\n");
                                Delay(100);
                                break;
                            }
                            p[cpl].gold -= 50 * p[cpl].lv[1];
                            clw();
                            tx("You spend a very\r\n");
                            tx("exhausting hour\r\n");
                            tx("upstairs.\r\n");
                            tx("(PERVERT!) ;-)\r\n\r\n");
                            for (i = 1; i < 5; i++)
                                if (p[cpl].hp[i] > 1)
                                    p[cpl].hp[i]--;
                            break;
                        }
                        break;
                    case 14:
                        tx("- Well. My inn is the\r\n");
                        tx("best in the realm!\r\n");
                        break;
                    case 15:
                        tx("- I dunno, try my\r\n");
                        tx("brother's inn.\r\n");
                        break;
                    case 16:
                        tx("- Yes. You are indeed\r\n");
                        tx("the one...\r\n");
                        break;
                }
                tx("\r\n");
                ryl_pause();
            }
        }
    }
}

void reply_chitchat(int subj, int jb) {
    if (subj == 0) {
        switch (jb) {
            case 1:
                tx("- Thanks, and the same\r\n");
                tx("to you!\r\n");
                break;
            case 2:
                tx("- I do? Really? Are\r\n");
                tx("you gay or smth'n?\r\n");
                break;
            case 3:
                tx("- Thank you. My health\r\n");
                tx("is slowly returning\r\n");
                tx("after a hard battle.\r\n");
                break;
            case 4:
                tx("- Aha?\r\n");
                break;
            case 5:
                tx("- You too, my son.\r\n");
                break;
            case 6:
                tx("- What? No! Sccch!\r\n");
                tx("They can hear you.\r\n");
                break;
            case 7:
                tx("- Thank you! You too.\r\n");
                break;
            case 8:
                tx("- That inspires me.\r\n");
                tx("I'll compose a song\r\n");
                tx("about you!\r\n");
                break;
            case 9:
                tx("- YEAH? Take that!\r\n");
                break;
            case 10:
                tx("- ME NOT LOOK WELL!\r\n");
                tx("ME BIG AND UGLY!\r\n");
                break;
            case 11:
                tx("- Oh...\r\n");
                break;
            case 12:
                tx("- I thank thee, my lad!\r\n");
                break;
            case 13:
                tx("- You too, honey!\r\n");
                break;
            case 14:
                tx("- The same to you.\r\n");
                break;
            case 15:
                tx("- You've had to much\r\n");
                tx("to drink, boy?\r\n");
                break;
            case 16:
                tx("- It is written in the\r\n");
                tx("scrolls of Skelos...\r\n");
                break;
        }
    }
    if (subj == 1) {
        switch (jb) {
            case 1:
                tx("- I think it will rain.\r\n");
                break;
            case 2:
                tx("- Not a fine weather for\r\n");
                tx("tracking, mind you!\r\n");
                break;
            case 3:
                tx("- Yeah! But my armour\r\n");
                tx("gets quite rusty.\r\n");
                break;
            case 4:
                tx("- Really?\r\n");
                break;
            case 5:
                tx("- The gods have been\r\n");
                tx("kind to us, my son!\r\n");
                break;
            case 6:
                tx("- Perfect weather\r\n");
                tx("for stealing!\r\n");
                break;
            case 7:
                tx("- Yeah, the crops grow\r\n");
                tx("better than ever.\r\n");
                break;
            case 8:
                tx("- This rain really\r\n");
                tx("inspires me. (He\r\n");
                tx("starts singing).\r\n");
                break;
            case 9:
                tx("- Hehehehe...\r\n");
                break;
            case 10:
                tx("- YEAH! NICE'N SLIMY!\r\n");
                break;
            case 11:
                tx("- I find it depressing.\r\n");
                break;
            case 12:
                tx("- Well, kinda good.\r\n");
                break;
            case 13:
                tx("- Let's not talk about\r\n");
                tx("the weather, honey?\r\n");
                break;
            case 14:
                tx("- Well. Can't complain.\r\n");
                break;
            case 15:
                tx("- Want a drink, sonny?\r\n");
                break;
            case 16:
                tx("- Yes, a day like this\r\n");
                tx("is destined to come!\r\n");
                break;
        }
    }
    if (subj == 2) {
        switch (jb) {
            case 1:
                tx("- All's well, thanks.\r\n");
                break;
            case 2:
                tx("- I'm fine.\r\n");
                break;
            case 3:
                tx("- Yep.\r\n");
                break;
            case 4:
                tx("- Health... yes.\r\n");
                break;
            case 5:
                tx("- All is well, my son.\r\n");
                break;
            case 6:
                tx("- Yeh, quite nice.\r\n");
                break;
            case 7:
                tx("- Are you a fag?\r\n");
                break;
            case 8:
                tx("- Yes. I will sing\r\n");
                tx("a song of health\r\n");
                tx("for you!\r\n");
                break;
            case 9:
                tx("(He chuckles under\r\n");
                tx("his breath).\r\n");
                break;
            case 10:
                tx("- ME JUST FINE!\r\n");
                break;
            case 11:
                tx("- Very well, thanks.\r\n");
                break;
            case 12:
                tx("- Fine, laddie!\r\n");
                break;
            case 13:
                tx("- Sure, honey!\r\n");
                break;
            case 14:
                tx("- Well...\r\n");
                break;
            case 15:
                tx("- Just fine. Do you\r\n");
                tx("want something to\r\n");
                tx("drink?\r\n");
                break;
            case 16:
                tx("- Oh. Meneth Sabaoth.\r\n");
                break;
        }
    }
}
void flaggkoll(int vem) {
    int i, x, oc;
    char str[80];
    char send[144];
    char c, d;
    uint8_t carry;

    switch (vem) {
        /*****************************************************************/
        case 1:
            if (p[cpl].flag[1] == 0) {
                clw();
                tx("In fact, I have a mission\r\n");
                tx("for you. It will bring\r\n");
                tx("you much gold!\r\n\r\n");
                ryl_pause();
                clw();
                tx("I was robbed by bandits\r\n");
                tx("just to the east of here.\r\n");
                tx("If you find these bandits,\r\n");
                tx("kill them, and return\r\n");
                tx("my cargo here, I'll pay\r\n");
                tx("you 2000 gold and a ruby\r\n");
                tx("my grandfather gave me.\r\n\r\n");
                ryl_pause();
                clw();
                p[cpl].flag[1] = 1;
                break;
            }
            if (p[cpl].flag[1] == 2) {
                clw();
                tx("The merchant is GRATEFUL!\r\n");
                tx("He pays you 2000 gold and\r\n");
                tx("hands you a red gem.\r\n\r\n");
                for (i = 1, x = 0; i < 17; i++) {
                    if (p[cpl].inventory[i] == 0) {
                        p[cpl].inventory[i] = 39;
                        x = 1;
                        break;
                    }
                }
                if (!x) {
                    tx("You can't carry the gem.\r\n");
                    Delay(40);
                    carry = 0;
                    carry = drop_option();
                    if (carry) {
                        clw();
                        tx("You pick up the gem.\r\n");
                        p[cpl].inventory[carry] = 39;
                        Delay(40);
                    }
                }
                ryl_pause();
                p[cpl].gold += 2000;
                p[cpl].flag[1] = 3;
                clw();
                break;
            }
            break;
        /*****************************************************************/
        case 2:
            if (p[cpl].flag[2] == 0) {
                if (npc[14].towne == 1)
                    snprintf(str, sizeof str, "in Thord");
                if (npc[14].towne == 2)
                    snprintf(str, sizeof str, "in Ollk");
                if (npc[14].towne == 3)
                    snprintf(str, sizeof str, "in Wilor");
                if (npc[14].towne == 4)
                    snprintf(str, sizeof str, "in Murn");
                if (npc[14].towne == 0)
                    snprintf(str, sizeof str, "at %dx%d", npc[14].x, npc[14].y);
                tx("- Greetings, adventurers.\r\n");
                tx("I have a mission for you.\r\n");
                tx("He hands you a letter.\r\n\r\n");
                ryl_pause();
                clw();
                tx("- If you deliver this\r\n");
                tx("to my brother, %s,\r\n", name[14]);
                tx("who lives %s,\r\n", str);
                tx("he will give you\r\n");
                tx("a great weapon!\r\n\r\n");
                ryl_pause();
                clw();
                snprintf(send, sizeof send, "%d %s, the Ranger lives %s\n", cpl, name[14], str);
                journal(send);
                p[cpl].flag[2] = 1;
            }
            break;
        /*****************************************************************/
        case 3:
            if (p[cpl].flag[3] == 0) {
                if (npc[5].towne == 1)
                    snprintf(str, sizeof str, "in Thord");
                if (npc[5].towne == 2)
                    snprintf(str, sizeof str, "in Ollk");
                if (npc[5].towne == 3)
                    snprintf(str, sizeof str, "in Wilor");
                if (npc[5].towne == 4)
                    snprintf(str, sizeof str, "in Murn");
                if (npc[5].towne == 0)
                    snprintf(str, sizeof str, "at %dx%d", npc[5].x, npc[5].y);
                clw();
                tx("- Greetings. Once upon\r\n");
                tx("a time, I was a mighty\r\n");
                tx("fighter. Before I retired\r\n");
                tx("I managed to slay over\r\n");
                tx("two hundred orcs. All\r\n");
                tx("of this, because of my\r\n");
                tx("Dancing Blade. I have\r\n");
                tx("no use for this weapon\r\n\r\n");
                ryl_pause();
                clw();
                tx("anymore, and I will give\r\n");
                tx("it to you, if you do me a\r\n");
                tx("small favour. A priest\r\n");
                tx("named %s has got\r\n", name[5]);
                tx("one of my books. We are\r\n");
                tx("old friends, and I lended\r\n");
                tx("it to him. However, I\r\n");
                tx("haven't heard much from\r\n");
                tx("him lately.\r\n\r\n");
                ryl_pause();
                clw();
                tx("%s lives\r\n", name[5]);
                tx("%s.\r\n\r\n", str);
                snprintf(send, sizeof send, "%d %s, %s's friend lives %s\n", cpl, name[5], name[3], str);
                journal(send);
                ryl_pause();
                clw();
                p[cpl].flag[3] = 1;
            }
            if (p[cpl].flag[3] == 3) {
                clw();
                tx("- Oh! Thank you!!! He\r\n");
                tx("removes a floor board\r\n");
                tx("and gives you a WONDERFUL\r\n");
                tx("blade. It sparkles in\r\n");
                tx("the light!\r\n\r\n");
                ryl_pause();
                clw();
                p[cpl].flag[3] = 4;
                for (i = 1, x = 0; i < 17; i++) {
                    if (p[cpl].inventory[i] == 0) {
                        p[cpl].inventory[i] = 18;
                        x = 1;
                        break;
                    }
                }
                if (!x) {
                    tx("You can't carry it!!!.\r\n\r\n");
                    Delay(40);
                    carry = 0;
                    carry = drop_option();
                    if (carry) {
                        clw();
                        tx("You take the blade.\r\n");
                        p[cpl].inventory[carry] = 18;
                        Delay(40);
                    }
                    ryl_pause();
                }
            }
            break;
        case 4:
            if (p[cpl].flag[3] == 2) {
                clw();
                tx("You persuade him to\r\n");
                tx("give you the book.\r\n");
                tx("After showing him your\r\n");
                tx("sharp weapons, he\r\n");
                tx("chickens out and pulls\r\n");
                tx("it out of his robe.\r\n");
                tx("He throws it at you and\r\n");
                tx("flees...\r\n\r\n");
                p[cpl].flag[3] = 3;
                ryl_pause();
                clw();
            }
            break;
        case 5:
            if (p[cpl].flag[3] == 1) {
                clw();
                tx("When you mention the\r\n");
                tx("book, he nods and looks\r\n");
                tx("a bit embarrased.\r\n");
                tx("- Yes, I know. I happened\r\n");
                tx("to lose it. A wizard\r\n");
                tx("called %s stole it\r\n", name[4]);
                tx("from my bedroom a week\r\n");
                tx("ago...\r\n\r\n");
                ryl_pause();
                clw();
                tx("He quickly fled, but I\r\n");
                tx("know it's him, because\r\n");
                tx("he made inquieries about\r\n");
                tx("it, and tried to steal\r\n");
                tx("it from me when I was\r\n");
                tx("having dinner.\r\n\r\n");
                ryl_pause();
                clw();
                tx("I don't know his\r\n");
                tx("whereabouts, but please\r\n");
                tx("find the book and\r\n");
                tx("take it back to %s\r\n", name[3]);
                tx("if you can.\r\n\r\n");
                ryl_pause();
                clw();
                p[cpl].flag[3] = 2;
                /* GIZZARD */
            }
            break;
        /*****************************************************************/
        case 6:
            if (p[cpl].flag[6] == 0) {
                clw();
                tx("- There is an item of\r\n");
                tx("great power, he hisses.\r\n\r\n");
                ryl_pause();
                clw();
                tx("- It is called the\r\n");
                tx("SILVER KEY. It has taken\r\n");
                tx("me many years of research\r\n");
                tx("to find its location, and\r\n");
                tx("this only to be stopped\r\n");
                tx("by a guardian telling me\r\n");
                tx("that 'only those of pure\r\n");
                tx("heart can enter'.\r\n\r\n");
                ryl_pause();
                clw();
                tx("- I fled for my life as\r\n");
                tx("horned creatures chased\r\n");
                tx("me through the woods.\r\n\r\n");
                ryl_pause();
                clw();
                tx("I have an offer to make\r\n");
                tx("thee. I will sell you\r\n");
                tx("a map to the location of\r\n");
                tx("the SILVER KEY for 50000\r\n");
                tx("gold coins. The price is\r\n");
                tx("resonable. 10000 gold for\r\n");
                tx("each of those fruitless\r\n");
                tx("years.\r\n\r\n");
                ryl_pause();
                clw();
                tx("- Deal? \r\n\r\n");
                tx("(B)uy it\r\n");
                tx("(N)o thanks\r\n");
                c = key();
                c = toupper(c);
                if (c == 'B') {
                    if (p[cpl].gold < 50000) {
                        clw();
                        tx("- Sorry! 50000! No more\r\n");
                        tx("no less. Come back when\r\n");
                        tx("you have the gold.\r\n\r\n");
                        ryl_pause();
                        clw();
                        break;
                    }
                    p[cpl].gold -= 50000;
                    clw();
                    tx("He quietly slips you the\r\n");
                    tx("precious map. It\r\n");
                    tx("tells you that the Pit\r\n");
                    tx("of the Key is at\r\n");
                    tx("%dx%d!\r\n\r\n", dx[8], dy[8]);
                    p[cpl].flag[6] = 1;
                    snprintf(send, sizeof send, "%d The Pit of the Silver Key is at %dx%d\n", cpl, dx[8], dy[8]);
                    journal(send);
                    ryl_pause();
                    clw();
                    break;
                } else
                    clw();
                break;
            }
            break;
        /*****************************************************************/
        case 8:
            if (p[cpl].flag[8] == 0) {
                tx("- Rumor has it that\r\n");
                tx("Erethor's Lute, a\r\n");
                tx("musical instrument with\r\n");
                tx("magical properties\r\n");
                tx("exists somewhere in\r\n");
                tx("the realm.\r\n\r\n");
                ryl_pause();
                clw();
                tx("- Erethor is a mighty\r\n");
                tx("demon...\r\n\r\n");
                ryl_pause();
                clw();
                tx("- I am a very poor man\r\n");
                tx("myself, but I know of\r\n");
                tx("the Cloak of Gods,\r\n");
                tx("the most powerful of\r\n");
                tx("all armours. I will\r\n\r\n");
                ryl_pause();
                clw();
                tx("trade this information\r\n");
                tx("if you find me Erethor's\r\n");
                tx("Lute. One of Erethor's\r\n");
                tx("temples, where mad\r\n");
                tx("priests practise\r\n");
                tx("blasphemous magic is\r\n");
                tx("located at 6x20, near\r\n");
                tx("the western peaks.\r\n\r\n");
                ryl_pause();
                clw();
                tx("- This may be a dangerous\r\n");
                tx("mission for such a\r\n");
                tx("small reward, but the\r\n");
                tx("Cloak will make it's\r\n");
                tx("bearer almost\r\n");
                tx("invulnerable.\r\n\r\n");
                ryl_pause();
                clw();
                snprintf(send, sizeof send, "%d Erethor's temple is situated at 6x20.\n", cpl);
                journal(send);
                p[cpl].flag[8] = 1;
            }
            if (p[cpl].flag[8] == 2) {
                clw();
                tx("You hand him the\r\n");
                tx("golden lute...\r\n\r\n");
                ryl_pause();
                clw();
                tx("- I can't believe you\r\n");
                tx("actually made it!!!\r\n\r\n");
                tx("- Now for my part of\r\n");
                tx("the deal. Listen closely,\r\n");
                tx("for I shall say this only\r\n");
                tx("once.\r\n\r\n");
                ryl_pause();
                clw();
                tx("- Take this note here\r\n");
                tx("and seek out my friend\r\n");
                tx("%s. He is also a\r\n", name[20]);
                tx("minstrel.\r\n");
                if (npc[20].towne == 1)
                    snprintf(str, sizeof str, "%s lives in Thord.\n", name[20]);
                if (npc[20].towne == 2)
                    snprintf(str, sizeof str, "%s lives in Ollk.\n", name[20]);
                if (npc[20].towne == 3)
                    snprintf(str, sizeof str, "%s lives in Wilor.\n", name[20]);
                if (npc[20].towne == 4)
                    snprintf(str, sizeof str, "%s lives in Murn.\n", name[20]);
                if (npc[20].towne == 0)
                    snprintf(str, sizeof str, "%s lives at %dx%d.\n", name[20], npc[20].x, npc[20].y);
                tx("%s\r\r\n", str);
                snprintf(send, sizeof send, "%d %s", cpl, str);
                journal(send);
                ryl_pause();
                clw();
                tx("- We used to travel\r\n");
                tx("together and he found\r\n");
                tx("the Cloak in a long dead\r\n");
                tx("hero's tomb. Now that\r\n");
                tx("we have the Lute, the\r\n");
                tx("Cloak is yours. Just\r\n");
                tx("deliver this explaining\r\n");
                tx("note!\r\n\r\n");
                ryl_pause();
                clw();
                tx("He hands you a piece of\r\n");
                tx("paper and bids you\r\n");
                tx("farewell.\r\n\r\n");
                ryl_pause();
                clw();
                p[cpl].flag[8] = 3;
            }
            break;

        /*****************************************************************/
        case 13:
            if (p[cpl].flag[13] == 0) {
                clw();
                tx("- I have a map of the\r\n");
                tx("road to the temple of\r\n");
                tx("Thun! The legend says\r\n");
                tx("it keeps the Bracelets\r\n");
                tx("of Thun, which is one of\r\n");
                tx("the objects that can\r\n");
                tx("destroy baron Ydarg!\r\n\r\n");
                ryl_pause();
                clw();
                tx("- I will sell it to you\r\n");
                tx("for 10000 gold pieces!\r\n\r\n");
                tx("(B)uy it\r\n");
                tx("(N)o thanks\r\n");
                c = key();
                c = toupper(c);
                if (c == 'B') {
                    if (p[cpl].gold < 10000) {
                        clw();
                        tx("- Sorry! 10000! No more\r\n");
                        tx("no less. Come back when\r\n");
                        tx("you have the gold.\r\n\r\n");
                        ryl_pause();
                        clw();
                        break;
                    }
                    p[cpl].gold -= 10000;
                    clw();
                    tx("- Ok, here. He slowly\r\n");
                    tx("hands you an old map. It\r\n");
                    tx("tells you that the temple\r\n");
                    tx("of Thun is situated at\r\n");
                    tx("%dx%d!\r\n\r\n", dx[2], dy[2]);
                    snprintf(send, sizeof send, "%d The Temple of Thun is at %dx%d\n", cpl, dx[2], dy[2]);
                    journal(send);
                    p[cpl].flag[13] = 1;
                    ryl_pause();
                    clw();
                    break;
                } else
                    clw();
                break;
            }
            break;
        /*****************************************************************/
        case 14:
            if (p[cpl].flag[2] == 1) {
                clw();
                tx("- OH! A message from my\r\n");
                tx("brother! This is indeed\r\n");
                tx("great news. I will give\r\n");
                tx("you this sword for\r\n");
                tx("delivering the letter!\r\n\r\n");
                ryl_pause();
                clw();
                tx("He produces a sparkling\r\n");
                tx("sword and wishes you a\r\n");
                tx("happy journey.\r\n\r\n");
                for (i = 1, x = 0; i < 17; i++) {
                    if (p[cpl].inventory[i] == 0) {
                        p[cpl].inventory[i] = 16;
                        x = 1;
                        break;
                    }
                }
                if (!x) {
                    tx("You can't carry it!!!.\r\n\r\n");
                    Delay(40);
                    carry = 0;
                    carry = drop_option();
                    if (carry) {
                        clw();
                        tx("You take the sword.\r\n");
                        p[cpl].inventory[carry] = 16;
                        Delay(40);
                    }
                }
                ryl_pause();
                p[cpl].flag[2] = 2;
                clw();
                break;
            }
            break;
        /*****************************************************************/
        case 15:
            if (p[cpl].flag[15] == 0) {
                if (npc[23].towne == 1)
                    snprintf(str, sizeof str, "in Thord");
                if (npc[23].towne == 2)
                    snprintf(str, sizeof str, "in Ollk");
                if (npc[23].towne == 3)
                    snprintf(str, sizeof str, "in Wilor");
                if (npc[23].towne == 4)
                    snprintf(str, sizeof str, "in Murn");
                if (npc[23].towne == 0)
                    snprintf(str, sizeof str, "at %dx%d", npc[23].x, npc[23].y);
                clw();
                tx("I heard that a man called\r\n");
                tx("%s knows of the\r\n", name[23]);
                tx("Crown. I have roamed the\r\n");
                tx("land in search for him,\r\n");
                tx("since I want this item\r\n");
                tx("to be found. Here! Take\r\n");
                tx("this ring and show it\r\n");
                tx("to him. You are better fit\r\n");
                tx("for this mission than me.\r\n\r\n");
                ryl_pause();
                clw();
                tx("%s lives\r\n", name[23]);
                tx("%s\r\n\r\n", str);
                snprintf(send, sizeof send, "%d %s lives %s.\n", cpl, name[23], str);
                journal(send);
                p[cpl].flag[15] = 1;
                ryl_pause();
                clw();
            }
            break;
        /*****************************************************************/
        case 18:
            if (p[cpl].flag[6] == 2) {
                p[cpl].flag[6] = 3;
                clw();
                tx("You push him up against\r\n");
                tx("a wall and start asking\r\n");
                tx("him questions. After\r\n");
                tx("some 'mild' persuation\r\n");
                tx("he tells you that he\r\n");
                tx("sold the Key to a\r\n");
                tx("travelling blacksmith\r\n");
                tx("who was interested in\r\n");
                tx("its metalurgic structure.\r\n\r\n");
                ryl_pause();
                clw();
                tx("After breaking a few\r\n");
                tx("of his teeth, he also\r\n");
                tx("willinly reveals that\r\n");
                tx("the smith went by the\r\n");
                tx("name of %s...\r\n\r\n", name[24]);
                ryl_pause();
                clw();
                tx("You let go of your grip\r\n");
                tx("of his collar, and he\r\n");
                tx("falls to the floor.\r\n\r\n");
                ryl_pause();
                clw();
            }
            break;
        /*****************************************************************/
        case 19:
            if (p[cpl].flag[19] == 0) {
                clw();
                tx("- Oh, I'm so depressed.\r\n");
                tx("Orcs have kidnapped my\r\n");
                tx("wife, Tammy, and demand\r\n");
                tx("a ransom for her. I will\r\n");
                tx("give you my entire fortune\r\n");
                tx("if you retrieve her.\r\n\r\n");
                ryl_pause();
                clw();
                tx("About 3000 gold coins,\r\n");
                tx("a fine armour, and\r\n");
                tx("some strange items my\r\n");
                tx("father left me, will be\r\n");
                tx("yours if you rescue her.\r\n\r\n");
                ryl_pause();
                clw();
                tx("The orcs have a cave\r\n");
                tx("at 42x23 in Lonewood.\r\n\r\n");
                snprintf(send, sizeof send, "%d The orc cavern is at 42x23.\n", cpl);
                journal(send);
                ryl_pause();
                clw();
                p[cpl].flag[19] = 1;
            }
            if (p[cpl].flag[19] == 2) {
                clw();
                tx("When he sees Tammy enter\r\n");
                tx("the room, he cries tears\r\n");
                tx("of happiness and kisses\r\n");
                tx("her. When they have had\r\n");
                tx("this happy reunion, he\r\n");
                tx("motions to you to follow.\r\n\r\n");
                ryl_pause();
                clw();
                tx("He hands you 3000 gold,\r\n");
                tx("an Adamantium Armour of\r\n");
                tx("advanced craftmanship\r\n");
                tx("and some other assorted\r\n");
                tx("objects. (An amber potion\r\n");
                tx("a holy handgrenade, a\r\n");
                tx("fire wand and a crystal.\r\n\r\n");
                ryl_pause();
                clw();
                p[cpl].flag[19] = 3;
                p[cpl].gold += 3000;
                for (i = 1, x = 0; i < 17; i++)
                    if (p[cpl].inventory[i] == 0) {
                        p[cpl].inventory[i] = 26;
                        x++;
                        break;
                    }
                for (i = 1; i < 17; i++)
                    if (p[cpl].inventory[i] == 0) {
                        p[cpl].inventory[i] = 110;
                        x++;
                        break;
                    }
                for (i = 1; i < 17; i++)
                    if (p[cpl].inventory[i] == 0) {
                        p[cpl].inventory[i] = 38;
                        x++;
                        break;
                    }
                for (i = 1; i < 17; i++)
                    if (p[cpl].inventory[i] == 0) {
                        p[cpl].inventory[i] = 101;
                        x++;
                        break;
                    }
                for (i = 1; i < 17; i++)
                    if (p[cpl].inventory[i] == 0) {
                        p[cpl].inventory[i] = 45;
                        x++;
                        break;
                    }
                if (x < 5) {
                    clw();
                    tx("You can't carry all the\r\n");
                    tx("items...\r\n\r\n");
                    ryl_pause();
                }
                clw();
            }
            break;
        /*****************************************************************/
        case 20:
            if (p[cpl].flag[8] == 3) {
                clw();
                tx("After reading the note\r\n");
                tx("%s cries tears\r\n", name[20]);
                tx("of happiness. He\r\n");
                tx("shakes all your hands\r\n");
                tx("and produces a purple\r\n");
                tx("cloak.\r\n\r\n");
                ryl_pause();
                for (i = 1, x = 0; i < 17; i++)
                    if (p[cpl].inventory[i] == 0) {
                        p[cpl].inventory[i] = 28;
                        x++;
                        break;
                    }
                if (!x) {
                    clw();
                    tx("You can't carry it!\r\n\r\n");
                    Delay(40);
                    carry = 0;
                    carry = drop_option();
                    if (carry) {
                        clw();
                        tx("You take the cloak.\r\n");
                        Delay(40);
                        p[cpl].inventory[carry] = 28;
                    }
                    ryl_pause();
                }
                clw();
                tx("He bids you farewell\r\n");
                tx("and tells you that\r\n");
                tx("he'll travel to\r\n");
                tx("%s the very\r\n", name[8]);
                tx("next day...\r\n\r\n");
                ryl_pause();
                clw();
                p[cpl].flag[8] = 4;
            }
            break;
        /*****************************************************************/
        case 23:
            if (p[cpl].flag[15] == 1 && p[cpl].flag[23] == 0) {
                clw();
                tx("- Ah, a seeker! Yes, I\r\n");
                tx("know about the Master\r\n");
                tx("Crown, but since I'm\r\n");
                tx("neither good nor evil,\r\n");
                tx("I have no interest in it.\r\n");
                tx("I will give you the\r\n");
                tx("information if you do\r\n");
                tx("three tasks for me.\r\n\r\n");
                ryl_pause();
                clw();
                tx("(Since there is nothing\r\n");
                tx("else you can do,you agree\r\n");
                tx("to complete the three\r\n");
                tx("tasks).\r\n\r\n");
                ryl_pause();
                clw();
                tx("- Firstly, I want you to\r\n");
                tx("go to the kobold lair at\r\n");
                tx("%dx%d, and kill all life\r\n", dx[3], dy[3]);
                tx("there. It is close to my\r\n");
                tx("home village, and the foul\r\n");
                tx("creatures have many times\r\n");
                tx("attacked us. Farewell.\r\n\r\n");
                snprintf(send, sizeof send, "%d The kobold lair is at %dx%d.\n", cpl, dx[3], dy[3]);
                journal(send);
                ryl_pause();
                clw();
                p[cpl].flag[15] = 2;
                p[cpl].flag[23] = 1;
                break;
            }
            if (p[cpl].flag[23] == 2) {
                clw();
                tx("You hand him the pendant.\r\n");
                tx("He stares at you, almost\r\n");
                tx("admiring.\r\n");
                tx("- WELL DONE! I will now\r\n");
                tx("grant you the second\r\n");
                tx("quest.\r\n\r\n");
                ryl_pause();
                clw();
                tx("- Being a magician, I'm\r\n");
                tx("always in search for\r\n");
                tx("powerful spell reagents.\r\n");
                tx("There is one plant I need\r\n");
                tx("but can't find. It is\r\n");
                tx("called Skullweed, and is\r\n");
                tx("purple with small green\r\n");
                tx("twigs. Find this plant\r\n");
                tx("plant for me!\r\n\r\n");
                ryl_pause();
                clw();
                p[cpl].flag[23] = 3;
                break;
            }
            if (p[cpl].flag[23] == 4) {
                clw();
                tx("- Well done again,\r\n");
                tx("adventurers. The third\r\n");
                tx("of my tasks will perhaps\r\n");
                tx("be the toughest one\r\n");
                tx("of them all! Find me a\r\n");
                tx("DWARF STONE. It is a\r\n");
                tx("stone of magnetic iron\r\n");
                tx("which gives the wielder\r\n");
                tx("the power of levitation.\r\n\r\n");
                ryl_pause();
                clw();
                tx("- Begin your search in\r\n");
                tx("the dwarven colony, near\r\n");
                tx("%dx%d. Be sure to treat\r\n", dx[4], dy[4]);
                tx("the dwarves with respect,\r\n");
                tx("for they are swift to\r\n");
                tx("anger.\r\n\r\n");
                snprintf(send, sizeof send, "%d The dwarf village is at %dx%d.\n", cpl, dx[4], dy[4]);
                journal(send);
                ryl_pause();
                clw();
                p[cpl].flag[23] = 5;
                break;
            }
            if (p[cpl].flag[23] == 8) {
                clw();
                tx("- I JUST CAN'T TELL YOU\r\n");
                tx("HOW GRATEFUL I AM!\r\n");
                tx("He takes the stone and\r\n");
                tx("smiles broadly at you.\r\n");
                tx("- Very well, seekers! The\r\n");
                tx("Master Crown is hidden in\r\n");
                tx("a dungeon near %dx%d!\r\n", dx[6], dy[6]);
                tx("Fare thee well.\r\n\r\n");
                snprintf(send, sizeof send, "%d The Master Crown dungeon is at %dx%d.\n", cpl, dx[6], dy[6]);
                journal(send);
                ryl_pause();
                clw();
                p[cpl].flag[23] = 9;
                break;
            }
            break;
        /*****************************************************************/
        case 24:
            if (p[cpl].flag[6] == 3) {
                clw();
                tx("You ask him if he still\r\n");
                tx("has the Key, prepared\r\n");
                tx("for the worst...\r\n\r\n");
                ryl_pause();
                clw();
                tx("- Nah, sorry. I kinda\r\n");
                tx("studied it enough and\r\n");
                tx("was gonna bring it to\r\n");
                tx("Ollk to sell it. However\r\n");
                tx("on the road between Wilor\r\n");
                tx("and Ollk I was caught by\r\n");
                tx("filthy hobgoblins!\r\n\r\n");
                ryl_pause();
                clw();
                tx("- I managed to get away,\r\n");
                tx("but they followed me! Just\r\n");
                tx("to the south-west of here\r\n");
                tx("one of their arrows hit\r\n");
                tx("my horse.\r\n\r\n");
                ryl_pause();
                clw();
                tx("- They clubbed me\r\n");
                tx("unconcious and took all\r\n");
                tx("my belonings!!! Including\r\n");
                tx("that key. Find them and\r\n");
                tx("take it back. It's yours\r\n");
                tx("if you get it.\r\n\r\n");
                ryl_pause();
                clw();
                p[cpl].flag[6] = 4;
            }
            break;
        /*****************************************************************/
        case 22:
            if (p[cpl].flag[22] == 0) {
                clw();
                tx("- ME HEAR YOU LOOK\r\n");
                tx("FOR POWER ITEMS!\r\n\r\n");
                tx("- ME VERY POOR! SELL\r\n");
                tx("YOU INFORMATION ABOUT\r\n");
                tx("FOOT OF JAZZY, YES?\r\n\r\n");
                ryl_pause();
                clw();
                tx("- LET US TRADE! OFFER\r\n");
                tx("ITEM AND I'LL TELL YA!\r\n\r\n");
                tx("(You offer him something).\r\n\r\n");
                ryl_pause();
                clw();
                invlist();
                d = key();
                d = toupper(d);
                oc = 0;
                if (d >= 'A' && d <= 'P') {
                    oc = p[cpl].inventory[d - 64];
                    if (oc < 35) {
                        clw();
                        tx("- NAH, THINK NOT!.\r\n");
                        Delay(50);
                        break;
                    } else {
                        clw();
                        tx("- YEEES! NICE NIICE!\r\n");
                        tx("GO TO CAVE AT %dx%d!\r\n", dx[7], dy[7]);
                        tx("BEAST HAS THE FOOT.\r\n\r\n");
                        snprintf(send, sizeof send, "%d The 'Beast Cave' is at %dx%d.\n", cpl, dx[7], dy[7]);
                        journal(send);
                        p[cpl].flag[22] = 1;
                        p[cpl].inventory[d - 64] = 0;
                        ryl_pause();
                        clw();
                        break;
                    }
                }
            }
            break;
        /*****************************************************************/
        case 33:
            if (p[cpl].flag[33] == 0) {
                clw();
                tx("- Welcome, chosen ones!\r\n");
                tx("the hermit cackles.\r\n");
                tx("- I know of the Fire\r\n");
                tx("Shield, an item of\r\n");
                tx("great power.\r\n");
                tx("Go find me teeth of\r\n");
                tx("a troll, and I will\r\n");
                tx("tell you about it.\r\n\r\n");
                ryl_pause();
                clw();
                p[cpl].flag[33] = 1;
            }
            if (p[cpl].flag[33] == 2) {
                clw();
                tx("You hand him the troll\r\n");
                tx("tooth.\r\n");
                tx("- Oh YES! He cackles.\r\n");
                tx("At areas uncharted, near\r\n");
                tx("%dx%d is the cave you\r\n", dx[1], dy[1]);
                tx("seek. Beware of the\r\n");
                tx("guardian the ancients\r\n");
                tx("gave birth too...\r\n\r\n");
                snprintf(send, sizeof send, "%d The Fire Shield Dungeon is at %dx%d.\n", cpl, dx[1], dy[1]);
                journal(send);
                ryl_pause();
                clw();
                p[cpl].flag[33] = 3;
            }
            break;
            /*****************************************************************/
    }
}
