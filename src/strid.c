#include "ryl.h"

/* 
** Never find and never fool
** The first one drinks the poison pool
** The third one reads the ancient tome
** The last one breaks the double dome
*/

void encounter(int foe, int mandatory) {
    char fn[80], str[80], b, c, d;
    int n1, n2; /* Min & Max nr */
    int h1, h2; /* Min & Max hp */
    int sp;     /* Fire frost hold */
    int im;     /* Immune to magic? */
    int ws;     /* WS */
    int x, i, mh[15], deadc[15], vem, dp, g, noc, mhtemp, spelldamage;
    int oc;
    long t;
    uint8_t defend = 0;

    int antimagic = 0, confused = 0, held = 0, playerheld = 0, playerconfused = 0;

    clw();
    tx("ENCOUNTER!\r\n\r\n");
    clr_map_window();
    blitgfx(encounterpic + rand() % 5);
    Delay(20);
    sp = 0;
    im = 0;
    switch (foe) {
        case 1:
            strcpy(fn, "Kobold");
            n1 = 2;
            n2 = 8;
            h1 = 30;
            h2 = 50;
            ws = 35;
            break;
        case 2:
            strcpy(fn, "Orc");
            n1 = 2;
            n2 = 5;
            h1 = 50;
            h2 = 99;
            ws = 50;
            break;
        case 3:
            strcpy(fn, "Troll");
            n1 = 1;
            n2 = 3;
            h1 = 99;
            h2 = 199;
            ws = 55;
            break;
        case 4:
            strcpy(fn, "Warrior");
            n1 = 1;
            n2 = 4;
            h1 = 50;
            h2 = 99;
            ws = 60;
            break;
        case 5:
            strcpy(fn, "Goblin");
            n1 = 2;
            n2 = 7;
            h1 = 50;
            h2 = 80;
            ws = 30;
            break;
        case 6:
            strcpy(fn, "Wild Dog");
            n1 = 1;
            n2 = 3;
            h1 = 15;
            h2 = 40;
            ws = 60;
            break;
        case 7:
            strcpy(fn, "Knight");
            n1 = 1;
            n2 = 5;
            h1 = 25;
            h2 = 99;
            ws = 75;
            break;
        case 8:
            strcpy(fn, "Bandit");
            n1 = 2;
            n2 = 6;
            h1 = 50;
            h2 = 99;
            ws = 50;
            break;
        case 9:
            strcpy(fn, "Hobgoblin");
            n1 = 2;
            n2 = 5;
            h1 = 20;
            h2 = 70;
            ws = 40;
            sp = 5;
            break;
        case 10:
            strcpy(fn, "Zombie");
            n1 = 2;
            n2 = 4;
            h1 = 70;
            h2 = 70;
            ws = 65;
            sp = 5;
            break;
        case 40:
            strcpy(fn, "Wander Mage");
            n1 = 3;
            n2 = 4;
            h1 = 50;
            h2 = 60;
            ws = 20;
            sp = 80;
            break;
        case 41:
            strcpy(fn, "Stirge");
            n1 = 6;
            n2 = 8;
            h1 = 10;
            h2 = 20;
            ws = 90;
            break;
        case 42:
            strcpy(fn, "Hill Giant");
            n1 = 1;
            n2 = 2;
            h1 = 100;
            h2 = 190;
            ws = 40;
            break;
        case 43:
            strcpy(fn, "Wizard");
            n1 = 2;
            n2 = 4;
            h1 = 30;
            h2 = 50;
            ws = 40;
            sp = 70;
            break;
        case 44:
            strcpy(fn, "Druid");
            n1 = 3;
            n2 = 4;
            h1 = 50;
            h2 = 60;
            ws = 60;
            sp = 60;
            break;
        case 45:
            strcpy(fn, "Sabre Tooth Tiger");
            n1 = 1;
            n2 = 2;
            h1 = 100;
            h2 = 120;
            ws = 95;
            break;
        case 46:
            strcpy(fn, "Head Hunter");
            n1 = 3;
            n2 = 5;
            h1 = 60;
            h2 = 80;
            ws = 65;
            break;
        case 47:
            strcpy(fn, "Skeleton");
            n1 = 3;
            n2 = 6;
            h1 = 20;
            h2 = 60;
            ws = 70;
            break;
        case 48:
            strcpy(fn, "Vampire Bat");
            n1 = 7;
            n2 = 8;
            h1 = 10;
            h2 = 20;
            ws = 55;
            break;
        case 49:
            strcpy(fn, "Giant Snake");
            n1 = 1;
            n2 = 3;
            h1 = 40;
            h2 = 90;
            ws = 60;
            break;
        case 50:
            strcpy(fn, "Ghoul");
            n1 = 1;
            n2 = 2;
            h1 = 100;
            h2 = 150;
            ws = 35;
            sp = 10;
            break;
        case 51:
            strcpy(fn, "Spider");
            n1 = 5;
            n2 = 8;
            h1 = 10;
            h2 = 20;
            ws = 95;
            break;
        case 52:
            strcpy(fn, "Huge Lizard");
            n1 = 3;
            n2 = 5;
            h1 = 30;
            h2 = 60;
            ws = 75;
            break;
        case 53:
            strcpy(fn, "Dryder");
            n1 = 1;
            n2 = 5;
            h1 = 80;
            h2 = 130;
            ws = 75;
            sp = 20;
            break;

            /* Special Encounters */

        case 11:
            strcpy(fn, "Guardian");
            n1 = 1;
            n2 = 1;
            h1 = 250;
            h2 = 350;
            ws = 80;
            break;
        case 12:
            strcpy(fn, "Norker");
            n1 = 1;
            n2 = 1;
            h1 = 350;
            h2 = 400;
            ws = 90;
            break;
        case 13:
            strcpy(fn, "Voodoo Lord");
            n1 = 1;
            n2 = 1;
            h1 = 300;
            h2 = 350;
            ws = 80;
            break;
        case 14:
            strcpy(fn, "Archdevil");
            n1 = 1;
            n2 = 1;
            h1 = 450;
            h2 = 450;
            ws = 85;
            sp = 60;
            break;
        case 15:
            strcpy(fn, "Kobold");
            n1 = 6;
            n2 = 8;
            h1 = 30;
            h2 = 45;
            ws = 25;
            break;
        case 16:
            strcpy(fn, "Rainbow Demon");
            n1 = 1;
            n2 = 1;
            h1 = 150;
            h2 = 250;
            ws = 95;
            sp = 10;
            break;
        case 17:
            strcpy(fn, "Long Dead Warrior");
            n1 = 1;
            n2 = 1;
            h1 = 400;
            h2 = 500;
            ws = 80;
            break;
        case 18:
            strcpy(fn, "Ogre");
            n1 = 4;
            n2 = 7;
            h1 = 90;
            h2 = 190;
            ws = 65;
            break;
        case 19:
            strcpy(fn, "Ogre Lord");
            n1 = 1;
            n2 = 1;
            h1 = 190;
            h2 = 270;
            ws = 85;
            sp = 25;
            break;
        case 20:
            strcpy(fn, "Winged Thing");
            n1 = 7;
            n2 = 8;
            h1 = 150;
            h2 = 200;
            ws = 95;
            break;
        case 21:
            strcpy(fn, "Jazzy");
            n1 = 1;
            n2 = 1;
            h1 = 450;
            h2 = 580;
            ws = 95;
            sp = 50;
            im = 1;
            break;
        case 22:
            strcpy(fn, "Riding Hobgoblin");
            n1 = 4;
            n2 = 7;
            h1 = 30;
            h2 = 70;
            ws = 40;
            sp = 10;
            break;
        case 23:
            strcpy(fn, "Hobgoblin Chief");
            n1 = 1;
            n2 = 1;
            h1 = 150;
            h2 = 230;
            ws = 100;
            sp = 40;
            break;
        case 24:
            strcpy(fn, "Hobgoblin Skeleton");
            n1 = 5;
            n2 = 6;
            h1 = 50;
            h2 = 60;
            ws = 90;
            break;
        case 25:
            strcpy(fn, "Undead Hobgoblin");
            n1 = 5;
            n2 = 5;
            h1 = 60;
            h2 = 70;
            ws = 95;
            break;
        case 26:
            strcpy(fn, "Crypt Horror");
            n1 = 3;
            n2 = 4;
            h1 = 100;
            h2 = 200;
            ws = 60;
            sp = 5;
            break;
        case 27:
            strcpy(fn, "Night Gaunt");
            n1 = 1;
            n2 = 2;
            h1 = 200;
            h2 = 350;
            ws = 90;
            sp = 10;
            break;
        case 28:
            strcpy(fn, "Thyraga");
            n1 = 1;
            n2 = 2;
            h1 = 250;
            h2 = 300;
            ws = 90;
            break;
        case 29:
            strcpy(fn, "Liche");
            n1 = 1;
            n2 = 1;
            h1 = 300;
            h2 = 400;
            ws = 85;
            break;
        case 30:
            strcpy(fn, "Baron Ydarg");
            n1 = 1;
            n2 = 1;
            h1 = 500;
            h2 = 1000;
            ws = 90;
            sp = 5;
            im = 1;
            break;
        case 31:
            strcpy(fn, "Gnoll");
            n1 = 2;
            n2 = 2 + p[cpl].lv[1] / 2;
            h1 = 60;
            h2 = 80;
            ws = 70;
            break;
        case 32:
            strcpy(fn, "Erethor Priest");
            n1 = 3;
            n2 = 4;
            h1 = 80;
            h2 = 99;
            ws = 70;
            sp = 50;
            break;
        case 33:
            strcpy(fn, "Erethor");
            n1 = 1;
            n2 = 1;
            h1 = 600;
            h2 = 900;
            ws = 80;
            sp = 10;
            break;

            /* More nice dungeon type monsters */

        case 60:
            strcpy(fn, "Xvart");
            n1 = 2;
            n2 = 2;
            h1 = 50;
            h2 = 75;
            ws = 80;
            sp = 10;
            break;
        case 61:
            strcpy(fn, "Jelly Cube");
            n1 = 1;
            n2 = 1;
            h1 = 200;
            h2 = 300;
            ws = 50;
            sp = 2;
            break;
        case 62:
            strcpy(fn, "Troglodyte");
            n1 = 2;
            n2 = 4;
            h1 = 40;
            h2 = 60;
            ws = 70;
            sp = 5;
            break;
        case 63:
            strcpy(fn, "Bugbear");
            n1 = 3;
            n2 = 4;
            h1 = 40;
            h2 = 50;
            ws = 70;
            break;
        case 64:
            strcpy(fn, "Drow Elf");
            n1 = 2;
            n2 = 4;
            h1 = 80;
            h2 = 100;
            ws = 70;
            sp = 50;
            break;
        case 65:
            strcpy(fn, "Phantom");
            n1 = 2;
            n2 = 3;
            h1 = 100;
            h2 = 200;
            ws = 80;
            sp = 40;
            break;
        case 66:
            strcpy(fn, "Worg");
            n1 = 2;
            n2 = 4;
            h1 = 80;
            h2 = 100;
            ws = 60;
            sp = 20;
            break;
        case 67:
            strcpy(fn, "Rust Monster");
            n1 = 1;
            n2 = 1;
            h1 = 150;
            h2 = 250;
            ws = 70;
            break;
        case 68:
            strcpy(fn, "Owlbear");
            n1 = 2;
            n2 = 3;
            h1 = 100;
            h2 = 120;
            ws = 70;
            break;
        case 69:
            strcpy(fn, "Werewolf");
            n1 = 1;
            n2 = 1;
            h1 = 300;
            h2 = 300;
            ws = 90;
            break;
        case 70:
            strcpy(fn, "Hook Horror");
            n1 = 2;
            n2 = 3;
            h1 = 70;
            h2 = 100;
            ws = 85;
            break;
        case 71:
            strcpy(fn, "Minotaur");
            n1 = 1;
            n2 = 3;
            h1 = 200;
            h2 = 240;
            ws = 80;
            break;
        case 72:
            strcpy(fn, "Imp");
            n1 = 1;
            n2 = 3;
            h1 = 100;
            h2 = 120;
            ws = 90;
            sp = 30;
            break;
        case 73:
            strcpy(fn, "Svirfneblin");
            n1 = 2;
            n2 = 3;
            h1 = 100;
            h2 = 100;
            ws = 90;
            sp = 20;
            break;
        case 74:
            strcpy(fn, "Yeti");
            n1 = 1;
            n2 = 1;
            h1 = 300;
            h2 = 350;
            ws = 60;
            break;
        case 75:
            strcpy(fn, "Wight");
            n1 = 1;
            n2 = 3;
            h1 = 90;
            h2 = 120;
            ws = 95;
            sp = 35;
            break;
        case 76:
            strcpy(fn, "Wraith");
            n1 = 2;
            n2 = 4;
            h1 = 80;
            h2 = 110;
            ws = 80;
            sp = 25;
            break;
        case 77:
            strcpy(fn, "Basilisk");
            n1 = 1;
            n2 = 1;
            h1 = 240;
            h2 = 300;
            ws = 60;
            sp = 20;
            break;
        case 78:
            strcpy(fn, "Wyvern");
            n1 = 1;
            n2 = 1;
            h1 = 350;
            h2 = 450;
            ws = 80;
            break;
        case 79:
            strcpy(fn, "Tunnel Worm");
            n1 = 1;
            n2 = 1;
            h1 = 300;
            h2 = 350;
            ws = 45;
            break;

        case 90:
            strcpy(fn, "Skagerberg Killer");
            n1 = 1;
            n2 = 1;
            h1 = 2000;
            h2 = 3500;
            ws = 100;
            sp = 20;
            im = 1;
            break;
    }

    noc = (rand() % n2);
    noc++;
    if (noc < n1)
        noc = n1;
    h1 += ((p[cpl].lv[1] + p[cpl].lv[2] + p[cpl].lv[3] + p[cpl].lv[4]) / 4) * 2;
    if (p[cpl].lv[1] > 2)
        h1 += p[cpl].lv[1] * 2;
    h2 += p[cpl].lv[1] * 3;
    if (h1 > h2)
        h1 = h2;

    if (noc > 8)
        noc = 8;

    ws += p[cpl].lv[1];

    for (i = 1; i < noc + 1; i++) {
        mhtemp = (rand() % h2);
        mhtemp++;
        if (mhtemp < h1)
            mhtemp = h1;
        mh[i] = mhtemp;
        deadc[i] = 0;
    }

    sprintf(str, "%d %s", noc, fn);
    if (noc > 1)
        strcat(str, "s");
    while (1) {
        clw();
        tx("You face\r\n");
        tx("%s\r\n\r\n", str);
        tx("(F)ight\r\n");
        tx("(R)un\r\n");

        c = key();
        c = toupper(c);
        if (c == 'F' || c == 'R')
            break;
    }
    if (c == 'R') {
        if (!mandatory) {
            if (rand() % 100 > 4) {
                clw();
                tx("Coward. You won't get\r\n");
                tx("any EXP for this battle.\r\n");
                Delay(40);
                clw();
                clr_map_window();
                return;
            } else {
                clw();
                tx("You fail to get away!\r\n");
                Delay(40);
                clw();
            }
        } else {
            clw();
            tx("You desperately try to\r\n");
            tx("get away, but fail!\r\n");
            Delay(40);
            clw();
        }
    }

    clw();
    tx("Blood shall flow!\r\n");
    Delay(50);

    g = 1;
    while (1) {
        clw();
        if (!p[cpl].dead[g] && !playerheld) {
        combat:
            tx("%s\r\n", p[cpl].name[g]);
            tx("What now?\r\n\r\n");
            tx("(A)ttack\r\n");
            tx("(D)efend\r\n");
            tx("(U)se Item\r\n");
            tx("(B)attle Cry\r\n");
            if (p[cpl].cl[g] == 3)
                tx("(C)ast Spell\r\n");
            while (1) {
                c = key();
                c = toupper(c);
                if (c == 'U' || c == 'D' || c == 'A' || c == 'B' || c == 'C')
                    break;
            }

            /****************************************************************************/
            if (c == 'U') {
                clw();
                tx("Use what?\r\n\r\n");
                invlist();
                d = key();
                d = toupper(d);
                oc = 0;
                if (d >= 'A' && d <= 'P') {
                    oc = p[cpl].inventory[d - 64];
                    if (oc < 100) {
                        clw();
                        tx("Not possible.\r\n");
                        Delay(50);
                        clw();
                        goto combat;
                    } else {
                        if (im) {
                            p[cpl].inventory[d - 64] = 0;
                            clw();
                            tx("The magic item doesn't seem\r\n");
                            tx("to work on this enemy!!!\r\n");
                            Delay(50);
                            clw();
                        } else {
                            switch (oc) {
                                case 100:
                                    p[cpl].inventory[d - 64] = 0;
                                    for (i = 1, vem = 0; i < noc + 1; i++)
                                        if (!deadc[i]) {
                                            vem = i;
                                            break;
                                        }
                                    if (vem) {
                                        spelldamage = 5 + (rand() % 7) + p[cpl].lv[g];
                                        clw();
                                        tx("%s chills %s\r\n", p[cpl].name[g], fn);
                                        tx("for %d damage pts.\r\n", spelldamage);
                                        Delay(50);
                                        mh[vem] -= spelldamage;
                                        if (mh[vem] <= 0) {
                                            mh[vem] = 0;
                                            tx("...KILLED!");
                                            Delay(30);
                                            deadc[vem] = 1;
                                        }
                                    }
                                    break;

                                case 101:
                                    p[cpl].inventory[d - 64] = 0;
                                    for (i = 1; i < noc + 1; i++) {
                                        spelldamage = p[cpl].lv[g] * ((rand() % 6) + 3);
                                        if (!deadc[i]) {
                                            clw();
                                            tx("%s burns %s\r\n", p[cpl].name[g], fn);
                                            tx("for %d pts!\r\n", spelldamage);
                                            Delay(50);
                                            mh[i] -= spelldamage;
                                            if (mh[i] <= 0) {
                                                mh[i] = 0;
                                                tx("...KILLED!\r\n");
                                                Delay(30);
                                                deadc[i] = 1;
                                            }
                                        }
                                    }
                                    break;

                                case 102:
                                    p[cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s throws the\r\n", p[cpl].name[g]);
                                    tx("at the enemy. It\r\n");
                                    tx("breaks before them in\r\n");
                                    tx("a rain of sparks.\r\n");
                                    tx("They bump into each\r\n");
                                    tx("other as they try to\r\n");
                                    tx("see you.\r\n\r\n");
                                    ryl_pause();
                                    clw();
                                    ws /= 2;
                                    break;

                                case 103:
                                    p[cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s throws the\r\n", p[cpl].name[g]);
                                    tx("gem at the enemy.\r\n");
                                    tx("It breaks, releasing\r\n");
                                    tx("a brilliant cloud\r\n");
                                    tx("of light which completely\r\n");
                                    tx("surrounds them. As the\r\n");
                                    tx("light fades, your enemies\r\n");
                                    tx("are blind.\r\n\r\n");
                                    ryl_pause();
                                    clw();
                                    ws = 5;
                                    break;

                                case 104:
                                    p[cpl].inventory[d - 64] = 0;
                                    for (i = 1, vem = 0; i < noc + 1; i++)
                                        if (!deadc[i]) {
                                            vem = i;
                                            break;
                                        }
                                    if (vem) {
                                        clw();
                                        tx("%s summons death\r\n", p[cpl].name[g]);
                                        tx("to take a %s\r\n", fn);
                                        tx("away...\r\n");
                                        Delay(100);
                                        tx("...KILLED!\r\n");
                                        Delay(30);
                                        deadc[vem] = 1;
                                        mh[vem] = 0;
                                        clw();
                                    }
                                    break;

                                case 105:
                                    p[cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("On Whom? (1-4)\r\n");
                                    while (1) {
                                        d = key();
                                        if (d >= '1' && d <= '4')
                                            break;
                                    }
                                    p[cpl].hp[d - 48] = p[cpl].h[d - 48];
                                    clw();
                                    tx("%s is fully\r\n", p[cpl].name[d - 48]);
                                    tx("healed.\r\n");
                                    Delay(50);
                                    clw();
                                    break;

                                case 106:
                                    p[cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("The party feels their\r\n");
                                    tx("spirits grow. You are\r\n");
                                    tx("all stronger than\r\n");
                                    tx("before.\r\n\r\n");
                                    ryl_pause();
                                    clw();
                                    for (i = 1; i < 5; i++) {
                                        p[cpl].ws[i] += 3;
                                        p[cpl].h[i] += 3;
                                        p[cpl].ch[i] += 3;
                                        p[cpl].wi[i] += 3;
                                    }
                                    break;

                                case 107:
                                    p[cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("The party is fully\r\n");
                                    tx("healed!\r\n\r\n");
                                    ryl_pause();
                                    clw();
                                    for (i = 1; i < 5; i++)
                                        if (!p[cpl].dead[i])
                                            p[cpl].hp[i] = p[cpl].h[i];
                                    break;

                                case 108:
                                    clw();
                                    tx("On Whom? (1-4)\r\n");
                                    while (1) {
                                        b = key();
                                        if (b >= '1' && b <= '4')
                                            break;
                                    }
                                    if (p[cpl].dead[b - 48]) {
                                        p[cpl].dead[b - 48] = 0;
                                        clw();
                                        tx("%s opens %s eyes!\r\n", p[cpl].name[b - 48],
                                           sextext(b - 48, 3));
                                        Delay(50);
                                        p[cpl].pl++;
                                        p[cpl].dead[b - 48] = 0;
                                        p[cpl].hp[b - 48] = 1;
                                        p[cpl].inventory[d - 64] = 0;
                                    } else {
                                        clw();
                                        tx("%s is not dead.\r\n", p[cpl].name[b - 48]);
                                        Delay(50);
                                        goto combat;
                                    }
                                    break;

                                case 109:
                                    p[cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s throws Kael's\r\n", p[cpl].name[g]);
                                    tx("axe at the enemy!\r\n\r\n");
                                    Delay(75);
                                    tx("It wounds them severely\r\n");
                                    tx("as it spins around,\r\n");
                                    tx("wielded by hands you\r\n");
                                    tx("cannot see!\r\n");
                                    Delay(200);
                                    clw();
                                    tx("With a raging battle\r\n");
                                    tx("cry, you attack the\r\n");
                                    tx("mortally wounded foes!\r\n");
                                    Delay(150);
                                    for (i = 1; i < noc + 1; i++)
                                        if (!deadc[i])
                                            mh[i] = 1 + (rand() % 3);
                                    break;

                                case 110:
                                    p[cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s pulls the\r\n", p[cpl].name[g]);
                                    tx("holy pin, and lobbeth\r\n");
                                    tx("the holy handgrenade\r\n");
                                    tx("at the foes...\r\n\r\n");
                                    Delay(150);
                                    tx("KA-BLAM!\r\n\r\n");
                                    Delay(75);
                                    clw();
                                    for (i = 1; i < noc + 1; i++)
                                        if (!deadc[i]) {
                                            mh[i] /= 4;
                                            mh[i]++;
                                        }
                                    break;

                                case 111:
                                    p[cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s plays the\r\n", p[cpl].name[g]);
                                    tx("Flute of Madness.\r\n");
                                    tx("It produces a shrill,\r\n");
                                    tx("fragile sound, and breaks.\r\n");
                                    tx("Nothing happens.\r\n\r\n");
                                    ryl_pause();
                                    clw();
                                    break;
                            }
                        }
                    }
                }
            }
            /****************************************************************************/
            if (c == 'C' && p[cpl].cl[g] == 3) {
                if (p[cpl].cl[g] == 3) /* Wizard Spells */
                {
                    clw();
                    tx("1. Frost Finger (%3d/%3d)\r\n", 8 + (p[cpl].lv[g]), p[cpl].ma[g]);
                    tx("2. Fire Cloud   (%3d/%3d)\r\n", 25 + (p[cpl].lv[g]), p[cpl].ma[g]);
                    tx("3. Confusion    (%3d/%3d)\r\n", 17 + p[cpl].lv[g], p[cpl].ma[g]);
                    tx("4. Death Strike (%3d/%3d)\r\n", 78 + p[cpl].lv[g], p[cpl].ma[g]);
                    tx("5. Hold Enemies (%3d/%3d)\r\n", 43 + p[cpl].lv[g], p[cpl].ma[g]);
                    tx("6. Anti Magic   (%3d/%3d)\r\n", 28 + p[cpl].lv[g], p[cpl].ma[g]);
                    tx("7. Abort\r\n");
                    while (1) {
                        d = key();
                        if (d >= '1' && d <= '7')
                            break;
                    }
                    if (im) {
                        clw();
                        tx("The spell fizzles!\r\n");
                        tx("this creature is immune\r\n");
                        tx("to ordinary magic!!!\r\n");
                        Delay(100);
                    }
                    /*************************** FROST ************************/
                    if (d == '1' && !im) {
                        if (p[cpl].ma[g] < (8 + p[cpl].lv[g])) {
                            clw();
                            tx("Not enough magic!\r\n");
                            Delay(50);
                        } else {
                            p[cpl].ma[g] -= (8 + p[cpl].lv[g]);
                            while (1) {
                                clw();
                                tx("Who?\r\n\r\n");
                                for (i = 1; i < noc + 1; i++) {
                                    if (deadc[i])
                                        strcpy(str, "Dead.");
                                    else
                                        strcpy(str, "Alive.");
                                    tx("%c. %-7s (%3d hp)\r\n", 64 + i, str, mh[i]);
                                }
                                d = key();
                                d = toupper(d);
                                if (d >= 'A' && d <= 64 + noc && !deadc[d - 64])
                                    break;
                            }
                            vem = d - 64;
                            spelldamage = p[cpl].lv[g] * ((rand() % 7) + 2);
                            clw();
                            tx("%s chills the\r\n", p[cpl].name[g]);
                            tx("opponent for %d\r\n", spelldamage);
                            tx("damage pts.\r\n");
                            Delay(80);
                            mh[vem] -= spelldamage;
                            if (mh[vem] <= 0) {
                                mh[vem] = 0;
                                tx("...KILLED!\r\n");
                                Delay(30);
                                deadc[vem] = 1;
                            }
                        }
                    }
                    /*************************** FIRE *************************/
                    if (d == '2' && !im) {
                        if (p[cpl].ma[g] < (25 + p[cpl].lv[g])) {
                            clw();
                            tx("Not enough magic!\r\n");
                            Delay(50);
                        } else {
                            p[cpl].ma[g] -= (25 + p[cpl].lv[g]);
                            for (i = 1; i < noc + 1; i++) {
                                time(&t);
                                srand((unsigned int) t);
                                spelldamage = p[cpl].lv[g] * ((rand() % 4) + 1);
                                if (!deadc[i]) {
                                    clw();
                                    tx("%s burns %s\r\n", p[cpl].name[g], fn);
                                    tx("for %d pts!\r\n", spelldamage);
                                    Delay(50);
                                    mh[i] -= spelldamage;
                                    if (mh[i] <= 0) {
                                        mh[i] = 0;
                                        tx("...KILLED!\r\n");
                                        Delay(30);
                                        deadc[i] = 1;
                                    }
                                }
                            }
                        }
                    }
                    /*********************** CONFUSION ************************/
                    if (d == '3' && !im) {
                        if (p[cpl].ma[g] < (17 + p[cpl].lv[g])) {
                            clw();
                            tx("Not enough magic!\r\n");
                            Delay(50);
                        } else {
                            p[cpl].ma[g] -= (17 + p[cpl].lv[g]);
                            clw();
                            tx("%s utters a word.\r\n", p[cpl].name[g]);
                            tx("Your images wavers and\r\n");
                            tx("blurs in front of your\r\n");
                            tx("enemies\r\n");
                            Delay(100);
                            ws /= 2;
                            confused = 1;
                        }
                    }
                    /************************ DEATH ***************************/
                    if (d == '4' && !im) {
                        if (p[cpl].ma[g] < (78 + p[cpl].lv[g])) {
                            clw();
                            tx("Not enough magic!\r\n");
                            Delay(50);
                        } else {
                            p[cpl].ma[g] -= (78 + p[cpl].lv[g]);
                            while (1) {
                                clw();
                                tx("Who?\r\n\r\n");
                                for (i = 1; i < noc + 1; i++) {
                                    if (deadc[i])
                                        strcpy(str, "Dead.");
                                    else
                                        strcpy(str, "Alive.");
                                    tx("%c. %-7s (%3d hp)\r\n", 64 + i, str, mh[i]);
                                }
                                d = key();
                                d = toupper(d);
                                if (d >= 'A' && d <= 64 + noc && !deadc[d - 64])
                                    break;
                            }
                            vem = d - 64;
                            if (rand() % 200 < mh[vem]) {
                                clw();
                                tx("Through INCREDIBLE\r\n");
                                tx("resistance, the enemy\r\n");
                                tx("is still alive, badly\r\n");
                                tx("wounded.\r\n");
                                Delay(100);
                                mh[vem] = 1;
                            } else {
                                clw();
                                tx("The enemy is wiped\r\n");
                                tx("straigt off the surface\r\n");
                                tx("of the earth!\r\n");
                                Delay(100);
                                deadc[vem] = 1;
                                mh[vem] = 0;
                            }
                        }
                    }
                    /************************  HOLD  ***************************/
                    if (d == '5' && !im) {
                        if (p[cpl].ma[g] < (43 + p[cpl].lv[g])) {
                            clw();
                            tx("Not enough magic!\r\n");
                            Delay(50);
                        } else {
                            p[cpl].ma[g] -= (43 + p[cpl].lv[g]);
                            clw();
                            tx("%s utters a word.\r\n", p[cpl].name[g]);
                            tx("The enemies freeze dead\r\n");
                            tx("in their tracks.\r\n");
                            Delay(100);
                            held = 1;
                        }
                    }
                    /********************** ANTI-MAGIC *************************/
                    if (d == '6' && !im) {
                        if (p[cpl].ma[g] < (28 + p[cpl].lv[g])) {
                            clw();
                            tx("Not enough magic!\r\n");
                            Delay(50);
                        } else {
                            p[cpl].ma[g] -= (28 + p[cpl].lv[g]);
                            clw();
                            tx("%s utters a word.\r\n", p[cpl].name[g]);
                            tx("The enemy is now\r\n");
                            tx("stopped from using magic.\r\n");
                            tx("against you this round.\r\n");
                            Delay(140);
                            antimagic = 1;
                        }
                    }
                    if (d == '7') {
                        clw();
                        goto combat;
                    }
                }
            }
            /****************************************************************************/
            if (c == 'B') {
                clw();
                tx("HI-YAAAA!\r\n\r\n");
                Delay(40);
                tx("(You manage to scare your\r\n");
                tx(" enemies enough to get\r\n");
                tx(" them off guard.)\r\n");
                if (ws > 4)
                    ws -= 4;
                Delay(75);
            }
            /****************************************************************************/
            if (c == 'D') {
                if (p[cpl].hp[g] + (p[cpl].lv[g]) > p[cpl].h[g])
                    p[cpl].hp[g] = p[cpl].h[g];
                else
                    p[cpl].hp[g] += (p[cpl].lv[g]);
                clw();
                if (defend < 5) {
                    tx("You gain some hits pts\r\n");
                    tx("as you rest this round.\r\n");
                    Delay(50);
                } else {
                    tx("You put up a defensive\r\n");
                    tx("stance...\r\n");
                    Delay(50);
                }
                defend++;
                if (defend > 250)
                    defend = 250;
                if (ws > 2)
                    ws -= 2;
            }
            /****************************************************************************/
            if (c == 'A') {
                while (1) {
                    clw();
                    tx("Who?\r\n\r\n");
                    for (i = 1; i < noc + 1; i++) {
                        if (deadc[i])
                            strcpy(str, "Dead.");
                        else
                            strcpy(str, "Alive.");
                        tx("%c. %-7s (%3d hp)\r\n", 64 + i, str, mh[i]);
                    }
                    d = key();
                    d = toupper(d);
                    if (d >= 'A' && d <= 64 + noc && !deadc[d - 64])
                        break;
                }
                vem = d - 64;
                x = p[cpl].ws[g];
                if (playerconfused)
                    x /= 3;
                if (rand() % 100 <= x) {
                    /* HIT */
                    dp = obdamage(p[cpl].wp[g]);
                    dp += rand() % 5;
                    dp += p[cpl].hp[g] / 12;
                    clw();
                    tx("%s hits for\r\n", p[cpl].name[g]);
                    tx("%d damage pts!\r\n", dp);
                    Delay(30);
                    mh[vem] -= dp;
                    if (mh[vem] <= 0) {
                        mh[vem] = 0;
                        deadc[vem] = 1;
                        tx("...KILLED!\r\n");
                        Delay(30);
                    }
                } else {
                    /* MISS */
                    clw();
                    tx("%s misses!\r\n", p[cpl].name[g]);
                    Delay(30);
                }
            }
            /* SLUTET */

            for (i = 1, x = 0; i < noc + 1; i++)
                if (!deadc[i])
                    x = 1;
            if (!x) {
                /* WIN */
                /* 100-111, 36-46 */
                clr_map_window();
                blitgfx(wonbattlepic);
                clw();
                tx("Thou hast bested\r\n");
                tx("thy foes.\r\n\r\n");
                tx("Each survivor gets\r\n");
                tx("%d experience pts\r\n", ((noc * (h2 / 4) * (ws / 4)) + sp * 3));
                tx("and %d gold pieces.\r\n\r\n", ((noc * (h2 / 7) * (ws / 4)) / 4));
                Delay(50);
                p[cpl].kills += noc;
                for (i = 1, x = 0; i < 17; i++)
                    if (p[cpl].inventory[i] == 0) {
                        x = i;
                        break;
                    }
                i = 0;
                if (x) {
                    if (rand() % 100 < 5) {
                        if (rand() % 100 > 40)
                            i = 36 + (rand() % 11);
                        else
                            i = 100 + (rand() % 12);
                        clw();
                        tx("You find a\r\n");
                        tx("%s.\r\n", object(i));
                        Delay(75);
                        p[cpl].inventory[x] = i;
                    }
                }
                p[cpl].gold += (noc * (h2 / 7) * (ws / 4));
                for (i = 1; i < 5; i++)
                    if (!p[cpl].dead[i])
                        p[cpl].exp[i] += ((noc * (h2 / 4) * (ws / 4)) + sp * 3);
                expcheck();
                clr_map_window();
                clw();
                return;
            }
        }
        g++;
        /****************************************************************************/
        if (g == 5) {
            playerheld = 0;
            playerconfused = 0;
            /* MONSTERS GET NASTY */
            g = 1;
            x = 1;
            while (x < noc + 1) {
                if (p[cpl].dead[1] && p[cpl].dead[2] && p[cpl].dead[3] && p[cpl].dead[4])
                    losegame();
                if (!deadc[x] && !held) {
                    if (!antimagic && sp &&
                        (rand() % 100) < (15 + sp / 2)) /* MOSTERS PRACTICE NASTY MAGIC */
                    {
                        clw();
                        tx("%s casts\r\n", fn);

                        switch (rand() % 4) {
                            case 0: /* Monster casts frost   */
                                tx("Frost Finger\r\n");
                                while (1) {
                                    vem = (rand() % 4) + 1;
                                    if (!p[cpl].dead[vem])
                                        break;
                                }
                                tx("on %s!\r\n", p[cpl].name[vem]);
                                Delay(50);
                                dp = h2 / 5 + rand() % 4;
                                tx("and does %d pts\r\n", dp);
                                tx("of damage!\r\n");
                                Delay(50);
                                p[cpl].hp[vem] -= dp;
                                if (p[cpl].hp[vem] <= 0) {
                                    p[cpl].hp[vem] = 0;
                                    p[cpl].dead[vem] = 1;
                                    p[cpl].pl--;
                                    tx("[31m%s DIES![37m\r\n", p[cpl].name[vem]);
                                    Delay(50);
                                }
                                break;
                            case 1: /* Monster casts fire    */
                                tx("Cloud of Fire!\r\n");
                                Delay(50);
                                for (i = 1; i < 5; i++) {
                                    if (!p[cpl].dead[i]) {
                                        clw();
                                        tx("%s is burnt for\r\n", p[cpl].name[i]);
                                        dp = h2 / 5 + rand() % 5;
                                        tx("%d damage pts!\r\n", dp);
                                        Delay(50);
                                        p[cpl].hp[i] -= dp;
                                        if (p[cpl].hp[i] <= 0) {
                                            p[cpl].hp[i] = 0;
                                            p[cpl].dead[i] = 1;
                                            p[cpl].pl--;
                                            tx("[31m%s DIES![37m\r\n", p[cpl].name[i]);
                                            Delay(50);
                                        }
                                    }
                                }
                                break;
                            case 2: /* Monster casts hold    */
                                tx("Hold Creature!\r\n");
                                Delay(30);
                                tx("You realize with horror\r\n");
                                tx("that you cannot move!\r\n");
                                Delay(75);
                                playerheld = 1;
                                break;
                            case 3: /* Monster casts confuse */
                                tx("Confusion!\r\n");
                                Delay(30);
                                tx("You are almost blinded\r\n");
                                tx("by a colorful haze.\r\n");
                                Delay(75);
                                playerconfused = 1;
                                break;
                        }
                    } else {
                        while (1) {
                            vem = (rand() % 4) + 1;
                            if (!p[cpl].dead[vem])
                                break;
                        }
                        clw();
                        tx("%s attacks\r\n", fn);
                        tx("%s!\r\n", p[cpl].name[vem]);
                        Delay(20);
                        if (rand() % 100 <= ws) {
                            dp = mh[x] / 6;
                            dp += rand() % 10;
                            dp -= obdamage(p[cpl].ar[vem]);
                            if (dp < 1)
                                dp = 1;
                            tx("And hits for\r\n");
                            tx("%d damage pts!!!\r\n", dp);
                            p[cpl].hp[vem] -= dp;
                            if (p[cpl].hp[vem] <= 0) {
                                if (p[cpl].pl)
                                    p[cpl].pl--;
                                p[cpl].hp[vem] = 0;
                                p[cpl].dead[vem] = 1;
                                tx("[31m%s DIES!!![37m\r\n", p[cpl].name[vem]);
                                Delay(50);
                            }
                            Delay(50);
                        } else {
                            tx("Blow misses!\r\n");
                            Delay(30);
                        }
                    }
                }
                x++;
            }
            antimagic = 0;
            if (confused) {
                confused = 0;
                ws *= 2;
            }
            held = 0;
            clw();
            if (playerheld) {
                tx("PARTY IS HELD!\r\n");
                Delay(50);
            } else {
                if (p[cpl].dead[1] && p[cpl].dead[2] && p[cpl].dead[3] && p[cpl].dead[4])
                    losegame();
                statusline();
                tx("You still face\r\n");
                for (i = 1, x = 0; i < noc + 1; i++)
                    if (!deadc[i])
                        x++;
                sprintf(str, "%d %s", x, fn);
                if (x > 1)
                    strcat(str, "s");
                tx("%s\r\n\r\n", str);
                tx("(F)ight\r\n");
                tx("(R)un\r\n");
                while (1) {
                    c = key();
                    c = toupper(c);
                    if (c == 'F')
                        break;
                    if (c == 'R') {
                        if (rand() % 100 <= 30 || mandatory) {
                            clw();
                            tx("You fail to get away!\r\n");
                            Delay(50);
                            break;
                        } else {
                            clw();
                            tx("You escaped...\r\n");
                            clr_map_window();
                            return;
                        }
                    }
                }
            }
        }
    }
    clr_map_window();
    clw();
}
/****************************************************************************/

void expcheck() {
    int i, l, add[5];
    unsigned long e;

    for (i = 1; i < 5; i++)
        add[i] = 0;

    for (i = 1; i < 5; i++) {
        l = p[cpl].lv[i];
        e = p[cpl].exp[i];
        if (l < 2 && e >= 2000)
            add[i]++;
        if (l < 3 && e >= 4000)
            add[i]++;
        if (l < 4 && e >= 8000)
            add[i]++;
        if (l < 5 && e >= 12000)
            add[i]++;
        if (l < 6 && e >= 20000)
            add[i]++;
        if (l < 7 && e >= 28000)
            add[i]++;
        if (l < 8 && e >= 36000)
            add[i]++;
        if (l < 9 && e >= 48000)
            add[i]++;
        if (l < 10 && e >= 54000)
            add[i]++;
        if (l < 11 && e >= 64000)
            add[i]++;
        if (l < 12 && e >= 74000)
            add[i]++;
        if (l < 13 && e >= 86000)
            add[i]++;
        if (l < 14 && e >= 98000)
            add[i]++;
        if (l < 15 && e >= 110000)
            add[i]++;
        if (l < 16 && e >= 125000)
            add[i]++;
        if (l < 17 && e >= 150000)
            add[i]++;
        if (l < 18 && e >= 180000)
            add[i]++;
        if (l < 19 && e >= 240000)
            add[i]++;
        if (l < 20 && e >= 300000)
            add[i]++;
        if (l < 21 && e >= 400000)
            add[i]++;
        if (l < 22 && e >= 550000)
            add[i]++;
        if (l < 23 && e >= 700000)
            add[i]++;
        if (l < 24 && e >= 900000)
            add[i]++;
        if (l < 25 && e >= 1100000)
            add[i]++;
        if (l < 26 && e >= 1500000)
            add[i]++;
        if (l < 27 && e >= 2000000)
            add[i]++;
        if (l < 28 && e >= 2800000)
            add[i]++;
    }

    for (i = 1; i < 5; i++) {
        if (add[i]) {
            clw();
            tx("%s gains >%d<\r\n", p[cpl].name[i], add[i]);
            tx("experience levels.\r\n");
            Delay(30);
            p[cpl].lv[i] += add[i];
            switch (p[cpl].cl[i]) {
                case 1:
                    p[cpl].ws[i] += 3;
                    p[cpl].h[i] += 8;
                    p[cpl].wi[i] += 1;
                    p[cpl].ch[i] += 1;
                    if (p[cpl].hp[i] < (p[cpl].h[i] * 2) / 3)
                        p[cpl].hp[i] = (p[cpl].h[i] * 2) / 3;
                    break;
                case 2:
                    p[cpl].ws[i] += 1;
                    p[cpl].h[i] += 5;
                    p[cpl].wi[i] += 1;
                    p[cpl].ch[i] += 2;
                    if (p[cpl].hp[i] < (p[cpl].h[i] * 2) / 3)
                        p[cpl].hp[i] = (p[cpl].h[i] * 2) / 3;
                    break;
                case 3:
                    p[cpl].ws[i] += 1;
                    p[cpl].h[i] += 4;
                    p[cpl].wi[i] += 7;
                    p[cpl].ch[i] += 2;
                    if (p[cpl].hp[i] < (p[cpl].h[i] * 2) / 3)
                        p[cpl].hp[i] = (p[cpl].h[i] * 2) / 3;
                    break;
                case 4:
                    p[cpl].ws[i] += 1;
                    p[cpl].h[i] += 5;
                    p[cpl].wi[i] += 8;
                    p[cpl].ch[i] += 3;
                    if (p[cpl].hp[i] < (p[cpl].h[i] * 2) / 3)
                        p[cpl].hp[i] = (p[cpl].h[i] * 2) / 3;
                    break;
                case 5:
                    p[cpl].ws[i] += 2;
                    p[cpl].h[i] += 7;
                    p[cpl].wi[i] += 2;
                    p[cpl].ch[i] += 6;
                    if (p[cpl].hp[i] < (p[cpl].h[i] * 2) / 3)
                        p[cpl].hp[i] = (p[cpl].h[i] * 2) / 3;
                    break;
                case 6:
                    p[cpl].ws[i] += 2;
                    p[cpl].h[i] += 8;
                    p[cpl].wi[i] += 3;
                    p[cpl].ch[i] += 3;
                    if (p[cpl].hp[i] < (p[cpl].h[i] * 2) / 3)
                        p[cpl].hp[i] = (p[cpl].h[i] * 2) / 3;
                    break;
            }
        }
    }
}
