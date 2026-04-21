#include "ryl.h"
#include "res.h"

void rumour() {
    char kl[8], str[40];

    getclk(kl);
    srand((unsigned int) kl[7]);

    switch (rand() % 33) {
        case 0:
            tx("Baron Ydarg's castle\r\n");
            tx("Zadraz is hidden in a\r\n");
            tx("forest to the soutwest.\r\n");
            break;
        case 1:
            tx("The Silver Key unlocks\r\n");
            tx("the gate to Ydarg's\r\n");
            tx("dimension.\r\n");
            break;
        case 2:
            tx("You will need the Fire\r\n");
            tx("shield to defeat baron\r\n");
            tx("Ydarg.\r\n");
            break;
        case 3:
            tx("You will need the\r\n");
            tx("Master Crown to\r\n");
            tx("defeat baron Ydarg.\r\n");
            break;
        case 4:
            tx("You will need the\r\n");
            tx("Bracelets of Thun\r\n");
            tx("to resist the most\r\n");
            tx("evil of all spells...\r\n");
            break;
        case 5:
            tx("The scythe is a\r\n");
            tx("useless weapon.\r\n");
            break;
        case 6:
            tx("Some say that you\r\n");
            tx("need gems to enter\r\n");
            tx("the old Temple of\r\n");
            tx("Thun.\r\n");
            break;
        case 7:
            tx("Seek the Hermit if\r\n");
            tx("you want to know\r\n");
            tx("of the Fire Shield\r\n");
            break;
        case 8:
            tx("The Hermit lives in\r\n");
            tx("the swamp.\r\n");
            break;
        case 9:
            tx("Always post a guard\r\n");
            tx("when you camp in the\r\n");
            tx("wilderness.\r\n");
            break;
            break;
        case 10:
            tx("The Foot of Jazzy is\r\n");
            tx("one of the objects\r\n");
            tx("Baron Ydarg can't\r\n");
            tx("control!\r\n");
            break;
        case 11:
            tx("Skullweed plant grows\r\n");
            tx("deep in the swamp. It is\r\n");
            tx("a powerful reagent.\r\n");
            break;
        case 12:
            tx("A man named %s\r\n", res_enum("name", 1));
            tx("will pay you for\r\n");
            tx("killing some bandits.\r\n");
            break;
        case 13:
            tx("A hunter called %s\r\n", res_enum("name", 2));
            tx("seeks a brave party\r\n");
            tx("of adventurers.\r\n");
            break;
        case 14:
            tx("It is always wise to\r\n");
            tx("haggle.\r\n");
            break;
        case 15:
            tx("Cthulhu will rise again!\r\n");
            break;
        case 16:
            tx("Baron Ydarg's castle\r\n");
            tx("in this dimension lies\r\n");
            tx("far to the southwest.\r\n");
            break;
        case 17:
            tx("Exploring caves can\r\n");
            tx("be profitable.\r\n");
            break;
        case 18:
            tx("Flee from all beasts\r\n");
            tx("that you are uncertain\r\n");
            tx("of.\r\n");
            break;
        case 19:
            tx("Before tempering in\r\n");
            tx("the business of a God,\r\n");
            tx("be a demi god yourself.\r\n");
            break;
        case 20:
            tx("Jazzy is a mighty\r\n");
            tx("lesser god or a demon.\r\n");
            break;
        case 21:
            tx("Baron Ydargs castle\r\n");
            tx("Zadraz is a gate to\r\n");
            tx("another dimension.\r\n");
            break;
        case 22:
            tx("Never meddle in the\r\n");
            tx("affairs of wizards, for\r\n");
            tx("they are subtle, and\r\n");
            tx("swift to anger.\r\n");
            break;
        case 23:
            tx("Magical objects can be\r\n");
            tx("a great aid in battle.\r\n");
            break;
        case 24:
            tx("Seek the holy handgrenade!\r\n");
            break;
        case 25:
            if (state.npc[15].towne == 1) {
                snprintf(str, sizeof str, "in Thord");
            }
            if (state.npc[15].towne == 2) {
                snprintf(str, sizeof str, "in Ollk");
            }
            if (state.npc[15].towne == 3) {
                snprintf(str, sizeof str, "in Wilor");
            }
            if (state.npc[15].towne == 4) {
                snprintf(str, sizeof str, "in Murn");
            }
            if (state.npc[15].towne == 0) {
                snprintf(str, sizeof str, "at %dx%d", state.npc[15].x, state.npc[15].y);
            }
            tx("A man named %s\r\n", res_enum("name", 15));
            tx("knows of the Crown.\r\n");
            tx("He lives %s!\r\n", str);
            break;
        case 26:
            tx("Murn is situated\r\n");
            tx("somewhere near 50x30!\r\n");
            break;
        case 27:
            if (state.npc[13].towne == 1) {
                snprintf(str, sizeof str, "in Thord");
            }
            if (state.npc[13].towne == 2) {
                snprintf(str, sizeof str, "in Ollk");
            }
            if (state.npc[13].towne == 3) {
                snprintf(str, sizeof str, "in Wilor");
            }
            if (state.npc[13].towne == 4) {
                snprintf(str, sizeof str, "in Murn");
            }
            if (state.npc[13].towne == 0) {
                snprintf(str, sizeof str, "at %dx%d", state.npc[13].x, state.npc[13].y);
            }
            tx("Seek %s if you\r\n", res_enum("name", 13));
            tx("search the Temple of\r\n");
            tx("Thun! He lives %s\r\n", str);
            break;
        case 28:
            tx("The more powerful\r\n");
            tx("creatures, the more\r\n");
            tx("experience.\r\n");
            break;
        case 29:
            tx("A good sword beats\r\n");
            tx("four aces!\r\n");
            break;
        case 30:
            tx("You need FIVE items,\r\n");
            tx("no less, no more\r\n");
            tx("to kill Ydarg!\r\n");
            break;
        case 31:
            tx("Wilor is situated\r\n");
            tx("at the coordinates\r\n");
            tx("43x20!\r\n");
            break;
        case 32:
            tx("Ollk is situated\r\n");
            tx("somewhere near 50x10!\r\n");
            break;
    }
    tx("\r\n");
}
int32_t obdamage(int32_t nr) {
    const ryl_item *it = res_item(nr);
    return it ? it->damage : 0;
}
int32_t obcost(int32_t nr) {
    const ryl_item *it = res_item(nr);
    return it ? it->cost : 0;
}
char *object(int32_t nr) {
    const ryl_item *it = res_item(nr);
    return it ? (char *) it->name : "-";
}

void enter() {
    char c, twnname[80];
    int32_t i, x, y, tax;

    //printf("%dx%d - map: %c\n",p[cpl].x,p[cpl].y,map[p[cpl].y][p[cpl].x]);

    if (state.map[state.p[state.cpl].y][state.p[state.cpl].x] != '0' && state.map[state.p[state.cpl].y][state.p[state.cpl].x] != '1' &&
        state.map[state.p[state.cpl].y][state.p[state.cpl].x] != '2') {
        clw();
        tx("Nothing to enter here!\r\n");
        return;
    }

    state.twn = 0;

    if (state.map[state.p[state.cpl].y][state.p[state.cpl].x] == '1') {
        hut();
        return;
    }
    if (state.map[state.p[state.cpl].y][state.p[state.cpl].x] == '2' && state.p[state.cpl].x == 14 && state.p[state.cpl].y == 30) {
        ydarg();
        return;
    }

    /* TOWNES : 1=thord, 2=ollk, 3=wilor, 4=murn */

    if (state.p[state.cpl].x == 12 && state.p[state.cpl].y == 11) {
        state.twn = 1;
        tax = 7;
    }
    if (state.p[state.cpl].x == 48 && state.p[state.cpl].y == 12) {
        state.twn = 2;
        tax = 10;
    }
    if (state.p[state.cpl].x == 43 && state.p[state.cpl].y == 20) {
        state.twn = 3;
        tax = 9;
    }
    if (state.p[state.cpl].x == 55 && state.p[state.cpl].y == 29) {
        state.twn = 4;
        tax = 8;
    }

    if (!state.twn) {
        return;
    }
    switch (state.twn) {
        case 1:
            strcpy(twnname, "Thord");
            break;
        case 2:
            strcpy(twnname, "Ollk");
            break;
        case 3:
            strcpy(twnname, "Wilor");
            break;
        case 4:
            strcpy(twnname, "Murn");
            break;
    }

    /* Har skall checkas om det {r en s{rskild stad, som tex Ydarg */

    /* Det skall det inte alls!!! */

    if (state.p[state.cpl].lv[1] >= 5) {
        clw();
        tx("Welcome to %s\r\n", twnname);
        tx("Our current tax rate\r\n");
        tx("is %d%% - In order to\r\n", tax);
        tx("enter, we must request\r\n");
        tx("you pay %d au.\r\n\r\n", (state.p[state.cpl].gold * tax) / 100);
        tx("(P)ay to Enter\r\n");
        tx("(L)eave\r\n");
        c = key();
        c = toupper(c);
        if (c == 'P') {
            clw();
            tx("Thank you!");
            Delay(100);
            state.p[state.cpl].gold -= (state.p[state.cpl].gold * tax) / 100;
        } else {
            clw();
            clr_map_window();
            update_map();
            return;
        }
    }

    clr_map_window();

    while (1) {
        blitgfx(citypic);
        clw();
        statusline();
        if (state.twn != 3) {
            tx("Village of %s\r\n", twnname);
        }
        if (state.twn == 3) {
            tx("City of %s\r\n", twnname);
        }
        tx("=======================\r\n\r\n");
        tx("(M)erchant  (I)nn\r\n");
        tx("(T)avern    (G)uild\r\n");
        if (state.twn != 1 && state.twn != 3) {
            tx("(S)mith\r\n");
        }
        if (state.twn == 1 || state.twn == 3) {
            tx("(S)mith     (C)hurch\r\n");
        }
        tx("(L)eave\r\n\r\n");

        tx(": ");

        c = key();
        if (toupper(c) == 'C' && (state.twn == 1 || state.twn == 3)) {
            church();
        }
        if (toupper(c) == 'S') {
            smith();
        }
        if (toupper(c) == 'I') {
            inn();
        }
        if (toupper(c) == 'M') {
            merchant();
        }
        if (toupper(c) == 'T') {
            tavern();
        }
        if (toupper(c) == 'G' && !stricmp(state.global_name, "Marcus Lagergren")) {
            guild();
        }
        if (toupper(c) == 'L') {
            for (i = 1, x = 0; i < 17; i++) {
                if (state.p[state.cpl].inventory[i] >= 1 && state.p[state.cpl].inventory[i] <= 19) {
                    x = 1;
                }
            }
            for (i = 1, y = 0; i < 5; i++) {
                if (!state.p[state.cpl].dead[i] && !state.p[state.cpl].wp[i]) {
                    y = 1;
                }
            }
            if (x && y) {
                clw();
                tx("Do you want to ready\r\n");
                tx("some weapons before\r\n");
                tx("leaving?\r\n\r\n");
                tx("(Y)es\r\n");
                tx("(N)o\r\n");
                c = key();
                c = toupper(c);
                if (c == 'Y') {
                    weaponr();
                }
            }

            for (i = 1, x = 0; i < 17; i++) {
                if (state.p[state.cpl].inventory[i] >= 20 && state.p[state.cpl].inventory[i] <= 30) {
                    x = 1;
                }
            }
            for (i = 1, y = 0; i < 5; i++) {
                if (!state.p[state.cpl].dead[i] && !state.p[state.cpl].ar[i]) {
                    y = 1;
                }
            }
            if (x && y) {
                clw();
                tx("Do you want to ready\r\n");
                tx("some armours before\r\n");
                tx("leaving?\r\n\r\n");
                tx("(Y)es\r\n");
                tx("(N)o\r\n");
                c = key();
                c = toupper(c);
                if (c == 'Y') {
                    armourr();
                }
            }

            clw();
            clr_map_window();
            update_map();
            return;
        }
    }
}

void hut() {
    int32_t i, vem = 0;
    char c;

    for (i = 1; i < 40; i++) {
        if (state.npc[i].x == state.p[state.cpl].x && state.npc[i].y == state.p[state.cpl].y) {
            vem = i;
        }
    }
    if (!vem) {
        return; /* Error */
    }
    //printf("vem: %d\n",vem);

    clr_map_window();
    blitgfx(hutpic);
    clw();
    tx("You enter the hut.\r\n");
    Delay(30);
    while (1) {
        clw();
        tx("A %s who\r\n", res_enum("jobname", state.npc[vem].job));
        tx("introduces himself as\r\n");
        tx("%s, greets you.\r\n\r\n", res_enum("name", vem));
        tx("(T)alk\r\n");
        tx("(L)eave\r\n");
        c = key();
        c = toupper(c);
        if (c == 'L') {
            clw();
            clr_map_window();
            update_map();
            return;
        }
        if (c == 'T') {
            speak(0, 0);
        }
    }
}

void church() {
    char c, d, f;
    int32_t cost;

    clr_map_window();
    blitgfx(churchpic);

    while (1) {
        clw();
        statusline();
        tx("You enter the church\r\n");
        tx("of Dalina, a beautiful\r\n");
        tx("goddess. This is one of\r\n");
        tx("her two temples.\r\n\r\n");
        tx("(H)eal person\r\n");
        tx("(R)aise dead\r\n");
        tx("(E)nergize\r\n");
        tx("(L)eave\r\n");
        c = key();
        c = toupper(c);

        if (c == 'L') {
            clw();
            clr_map_window();
            return;
        }

        if (c == 'E') {
            clw();
            tx("Who? (1-4)\r\n");
            d = key();
            d = toupper(d);
            if (d >= '1' && d <= '4') {
                if (state.p[state.cpl].cl[d - 48] != 3 && state.p[state.cpl].cl[d - 48] != 4) {
                    clw();
                    tx("Not a spellcaster!\r\n");
                    Delay(50);
                } else /* Spellcaster */
                {
                    if (state.p[state.cpl].ma[d - 48] == state.p[state.cpl].wi[d - 48]) {
                        clw();
                        tx("That character needs\r\n");
                        tx("no spell points\r\n");
                        tx("to be restored.\r\n");
                        Delay(100);
                    } else {
                        clw();
                        cost = state.p[state.cpl].lv[d - 48] * (state.p[state.cpl].wi[d - 48] - state.p[state.cpl].ma[d - 48]) * 2;
                        if (!cost) {
                            cost = state.p[state.cpl].lv[d - 48] * 2;
                        }
                        tx("- That will be %d\r\n", cost);
                        tx("gold coins, please.\r\n\r\n");
                        tx("(P)ay\r\n");
                        tx("(N)o thanks\r\n");
                        f = key();
                        f = toupper(f);
                        if (f == 'P') {
                            clw();
                            if (state.p[state.cpl].gold < cost) {
                                tx("You don't have the gold!\r\n");
                                Delay(50);
                            } else {
                                state.p[state.cpl].gold -= cost;
                                state.p[state.cpl].ma[d - 48] = state.p[state.cpl].wi[d - 48];
                                clw();
                                tx("The priestess\r\n");
                                tx("restores his energy.\r\n");
                                Delay(50);
                            }
                        }
                    }
                }
            }
        }

        if (c == 'R') {
            clw();
            tx("Who? (1-4)\r\n");
            d = key();
            d = toupper(d);
            if (d >= '1' && d <= '4') {
                if (!state.p[state.cpl].dead[d - 48]) {
                    clw();
                    tx("- %s seems very alive\r\n", sextext(d - 48, 1));
                    tx("at the moment.\r\n");
                    Delay(60);
                } else {
                    clw();
                    tx("- That will be %d\r\n", 650 * state.p[state.cpl].lv[d - 48]);
                    tx("gold coins, please.\r\n\r\n");
                    tx("(P)ay\r\n");
                    tx("(N)o thanks\r\n");
                    f = key();
                    f = toupper(f);
                    if (f == 'P') {
                        clw();
                        if (state.p[state.cpl].gold < 650 * state.p[state.cpl].lv[d - 48]) {
                            tx("You don't have the gold!\r\n");
                            Delay(50);
                        } else {
                            state.p[state.cpl].gold -= 650 * state.p[state.cpl].lv[d - 48];
                            state.p[state.cpl].hp[d - 48] = 1;
                            state.p[state.cpl].dead[d - 48] = 0;
                            state.p[state.cpl].pl++;
                            clw();
                            tx("The priestess raises\r\n");
                            tx("%s.\r\n", sextext(d - 48, 2));
                            Delay(50);
                        }
                    }
                }
            }
        }
        if (c == 'H') {
            clw();
            tx("Who? (1-4)\r\n");
            d = key();
            d = toupper(d);
            if (d >= '1' && d <= '4') {
                if (state.p[state.cpl].dead[d - 48]) {
                    clw();
                    tx("- GET THAT CORPSE\r\n");
                    tx("OUTTA HERE!\r\n");
                    tx("(You are thrown out).\r\n\r\n");
                    Delay(60);
                    return;
                }
                if (state.p[state.cpl].hp[d - 48] == state.p[state.cpl].h[d - 48]) {
                    clw();
                    tx("%s needs no healing.\r\n", sextext(d - 48, 1));
                    Delay(50);
                } else {
                    cost = state.p[state.cpl].h[d - 48] - state.p[state.cpl].hp[d - 48];
                    cost *= 7;
                    clw();
                    tx("- That'll be %d\r\n", cost);
                    tx("gold coins, please.\r\n\r\n");
                    tx("(P)ay\r\n");
                    tx("(N)o thanks\r\n");
                    f = key();
                    f = toupper(f);
                    if (f == 'P') {
                        if (state.p[state.cpl].gold < cost) {
                            tx("You don't have enough\r\n");
                            tx("gold!\r\n");
                            Delay(50);
                        } else {
                            clw();
                            state.p[state.cpl].gold -= cost;
                            tx("The priestess heals\r\n");
                            tx("%s fully.\r\n", sextext(d - 48, 2));
                            Delay(50);
                            state.p[state.cpl].hp[d - 48] = state.p[state.cpl].h[d - 48];
                        }
                    }
                }
            }
        }
    }
}
void smith() {
    char b, c, d;
    int32_t i, x, oc;
    int32_t pr;
    int32_t menu = 1;

    state.tid++;
    if (state.tid == 25) {
        state.tid = 1;
    }

    if (state.tid > 18 || state.tid < 6) {
        clw();
        tx("Shop Closed.\r\n");
        Delay(50);
        return;
    }

    clr_map_window();
    blitgfx(smithpic);

    while (1) {
        clw();
        statusline();
        tx("Smith.\r\n\r\n");
        tx("The smith welcomes you\r\n");
        tx("to his small shop.\r\n\r\n");

        if ((rand() % 100) > 90) {
            tx("The smith offers you some\r\n");
            tx("beer, and the latest news\r\n");
            tx("from the realm.\r\n\r\n");
            tx("(A)ccept\r\n");
            tx("(D)ecline\r\n");
            c = key();
            c = toupper(c);
            if (c == 'A') {
                clw();
                tx("You accept the invitation.\r\n");
                Delay(100);
                tx("After a while the smith\r\n");
                tx("leaves to get more beer.\r\n");
                Delay(100);
                tx("When he returns, you ask\r\n");
                tx("him for the latest news...\r\n\r\n");
                Delay(100);
                clw();
                rumour();
                ryl_pause();
            } else {
                clw();
                tx("The smith is disappointed.\r\n");
                tx("- Maybe another time,\r\n");
                tx("you tell him.\r\n");
                tx("- Yeah, he answers.\r\n\r\n");
                ryl_pause();
            }
        } else {
            tx("(B)uy\r\n");
            tx("(S)ell\r\n");
            tx("(L)eave\r\n");
            c = key();
            c = toupper(c);
            if (c == 'L') {
                clw();
                tx("Well, bye then...\r\n");
                Delay(50);
                clr_map_window();
                return;
            }

            if (c == 'B') {
                clw();
                tx("Armour or Weapon?\r\n\r\n");
                tx("(A)rmour\r\n");
                tx("(W)eapon\r\n");
                tx("(N)othing\r\n");

                d = key();
                d = toupper(d);

                if (d == 'A') {
                    clw();
                    tx("We have...\r\n\r\n");
                    tx("1. Leather     (%4d Au)\r\n", obcost(20));
                    tx("2. Chain Mail  (%4d Au)\r\n", obcost(21));
                    tx("3. Plate Mail  (%4d Au)\r\n", obcost(22));
                    tx("4. Ring Mail   (%4d Au)\r\n", obcost(23));
                    tx("5. Elvin Cloak (%4d Au)\r\n", obcost(24));
                    tx("6. Mith. Mail  (%4d Au)\r\n", obcost(25));
                    d = key();
                    d = toupper(d);
                    oc = 0;
                    pr = 0;
                    switch (d) {
                        case '1':
                            oc = 20;
                            pr = obcost(oc);
                            break;
                        case '2':
                            oc = 21;
                            pr = obcost(oc);
                            break;
                        case '3':
                            oc = 22;
                            pr = obcost(oc);
                            break;
                        case '4':
                            oc = 23;
                            pr = obcost(oc);
                            break;
                        case '5':
                            oc = 24;
                            pr = obcost(oc);
                            break;
                        case '6':
                            oc = 25;
                            pr = obcost(oc);
                            break;
                    }
                    if (state.p[state.cpl].gold < pr) {
                        clw();
                        tx("Too expensive...\r\n");
                        Delay(50);
                    } else {
                        clw();
                        for (i = 1, x = 0; i < 17; i++) {
                            if (state.p[state.cpl].inventory[i] == 0) {
                                x = i;
                                break;
                            }
                        }
                        if (!x) {
                            tx("You cannot carry more.\r\n");
                            Delay(50);
                        } else {
                            state.p[state.cpl].inventory[x] = oc;
                            state.p[state.cpl].gold -= pr;
                            tx("Deal!\r\n");
                            Delay(50);
                        }
                    }
                }

                if (d == 'W') {
                    while (1) {
                        clw();
                        tx("We have...\r\n\r\n", state.p[state.cpl].gold);
                        if (menu == 1) {
                            tx("1. Sword     (%4d Au)\r\n", obcost(1));
                            tx("2. Axe       (%4d Au)\r\n", obcost(2));
                            tx("3. Bow       (%4d Au)\r\n", obcost(3));
                            tx("4. Club      (%4d Au)\r\n", obcost(4));
                            tx("5. Halbard   (%4d Au)\r\n", obcost(9));
                            tx("6. Crossbow  (%4d Au)\r\n\r\n", obcost(11));
                            tx("N. Next Page\r\n");
                        }
                        if (menu == 2) {
                            tx("7. Longsword (%4d Au)\r\n", obcost(7));
                            tx("8. 2h-Sword  (%4d Au)\r\n", obcost(14));
                            tx("9. Pole Arm  (%4d Au)\r\n", obcost(10));
                            tx("A. Trident   (%4d Au)\r\n", obcost(8));
                            tx("B. Mgk Dagger(%4d Au)\r\n", obcost(15));
                            tx("C. Mgk Sword (%4d Au)\r\n\r\n", obcost(16));
                            tx("P. Previous Page\r\n");
                        }
                        d = key();
                        d = toupper(d);
                        if (d == 'N') {
                            if (menu == 1) {
                                menu = 2;
                            }
                        }
                        if (d == 'P') {
                            if (menu == 2) {
                                menu = 1;
                            }
                        }
                        if (d != 'N' && d != 'P') {
                            break;
                        }
                    }
                    oc = 0;
                    pr = 0;
                    switch (d) {
                        case '1':
                            oc = 1;
                            pr = obcost(1);
                            break;
                        case '2':
                            oc = 2;
                            pr = obcost(2);
                            break;
                        case '3':
                            oc = 3;
                            pr = obcost(3);
                            break;
                        case '4':
                            oc = 4;
                            pr = obcost(4);
                            break;
                        case '5':
                            oc = 9;
                            pr = obcost(9);
                            break;
                        case '6':
                            oc = 11;
                            pr = obcost(11);
                            break;
                        case '7':
                            oc = 7;
                            pr = obcost(7);
                            break;
                        case '8':
                            oc = 14;
                            pr = obcost(14);
                            break;
                        case '9':
                            oc = 10;
                            pr = obcost(10);
                            break;
                        case 'A':
                            oc = 8;
                            pr = obcost(8);
                            break;
                        case 'B':
                            oc = 15;
                            pr = obcost(15);
                            break;
                        case 'C':
                            oc = 16;
                            pr = obcost(16);
                            break;
                    }
                    if (state.p[state.cpl].gold < pr) {
                        clw();
                        tx("Too expensive...\r\n");
                        Delay(50);
                    } else {
                        clw();
                        for (i = 1, x = 0; i < 16; i++) {
                            if (state.p[state.cpl].inventory[i] == 0) {
                                x = i;
                                break;
                            }
                        }
                        if (!x) {
                            tx("You cannot carry more.\r\n");
                            Delay(50);
                        } else {
                            state.p[state.cpl].inventory[x] = oc;
                            state.p[state.cpl].gold -= pr;
                            tx("Deal!\r\n");
                            Delay(50);
                        }
                    }
                }
            }

            if (c == 'S') {
                clw();
                tx("Sell what?\r\n\r\n");
                invlist();
                d = key();
                d = toupper(d);
                oc = 0;
                pr = 0;
                if (d >= 'A' && d <= 'P') {
                    oc = state.p[state.cpl].inventory[d - 64];
                    pr = obcost(state.p[state.cpl].inventory[d - 64]);
                    pr /= 2;
                    if (oc == 0 || oc > 30) {
                        clw();
                        tx("Not weapon or armour.\r\n");
                        Delay(50);
                    } else {
                        while (1) {
                            clw();
                            tx("Ok! Give ya %d!\r\n\r\n", pr);
                            tx("(Y)es\r\n");
                            tx("(N)o\r\n");
                            tx("(B)argain\r\n");
                            b = key();
                            b = toupper(b);
                            if (b == 'Y') {
                                state.p[state.cpl].inventory[d - 64] = 0;
                                state.p[state.cpl].gold += pr;
                                pr = 0;
                                break;
                            }
                            if (b == 'B') {
                                i = rand() % 100;
                                if (i > 90) {
                                    pr += pr / 4;
                                }
                                if (i > 75) {
                                    pr += pr / 6;
                                }
                                if (i < 76) {
                                    clw();
                                    tx("No thanks.\r\n");
                                    Delay(50);
                                    break;
                                }
                            }
                            if (b == 'N') {
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
    clr_map_window();
}
void tavern() {
    char c, d, b;
    int32_t i, j, x, st, xs;
    int32_t pr;
    int32_t rumours = 0;

    state.tid++;
    if (state.tid == 25) {
        state.tid = 1;
    }

    if (state.tid < 6 && state.tid > 1) {
        clw();
        tx("Tavern Closed.\r\n");
        Delay(50);
        return;
    }

    clr_map_window();
    blitgfx(tavernpic);

    for (i = 1, x = 0; i < 5; i++) {
        if (state.p[state.cpl].cl[i] == 5) {
            x += state.p[state.cpl].ch[i] / 10;
        }
    }

    if ((rand() % 100) <= (4 + x)) {
        clw();
        tx("Just as you are about\r\n");
        tx("to sit down at a,\r\n");
        tx("table, a scruffy\r\n");
        tx("little man, pulls\r\n");
        tx("at your sleeve...\r\n\r\n");
        Delay(150);
        tx("- Hey, hey, hey! You!\r\n");
        tx("  yes... you! I have\r\n");
        tx("  and offer that may\r\n");
        tx("  interest you...\r\n\r\n");
        Delay(150);
        clw();
        /* 15,17,26,27,38,41,100,101,102,103,104,106,107,108,109,110,111 */
        i = rand() % 16;
        i++;
        switch (i) {
            case 1:
                i = 15;
                break;
            case 2:
                i = 17;
                break;
            case 3:
                i = 26;
                break;
            case 4:
                i = 38;
                break;
            case 5:
                i = 41;
                break;
            case 6:
                i = 100;
                break;
            case 7:
                i = 101;
                break;
            case 8:
                i = 102;
                break;
            case 9:
                i = 103;
                break;
            case 10:
                i = 104;
                break;
            case 11:
                i = 105;
                break;
            case 12:
                i = 106;
                break;
            case 13:
                i = 107;
                break;
            case 14:
                i = 108;
                break;
            case 15:
                i = 109;
                break;
            case 16:
                i = 110;
                break;
            case 17:
                i = 111;
                break;
        }
        tx("- I will sell you this\r\n");
        tx("  %s\r\n", object(i));
        tx("  for the ridiculously\r\n");
        tx("  low price of %d\r\n", obcost(i));
        tx("  Aurum!\r\n\r\n");
        tx("(B)uy it\r\n");
        tx("(N)o thanks\r\n");

        while (1) {
            c = key();
            c = toupper(c);
            if (c == 'N' || c == 'B') {
                break;
            }
        }
        clw();
        if (c == 'B') {
            if (state.p[state.cpl].gold < obcost(i)) {
                tx("- You don't have that\r\n");
                tx("  much gold!\r\n");
                Delay(40);
            } else {
                for (j = 1, x = 0; j < 17; j++) {
                    if (state.p[state.cpl].inventory[j] == 0) {
                        state.p[state.cpl].inventory[j] = i;
                        x = 1;
                        break;
                    }
                }
                if (x) {
                    tx("- Deal!!!\r\n");
                    state.p[state.cpl].gold -= obcost(i);
                    Delay(40);
                } else {
                    tx("- You can't carry it!\r\n");
                    Delay(50);
                }
            }
        }
        if (c == 'N') {
            tx("- Fine, I can live with\r\n");
            tx("  that.\r\n");
            Delay(50);
        }
    }
    while (1) {
        clw();
        tx("- Welcome! Have a seat\r\n");
        tx("%s, the keeper\r\n", res_enum("name", 28 + state.twn));
        tx("of the tavern greets\r\n");
        tx("you...\r\n\r\n");
        tx("(O)rder drink\r\n");
        tx("(S)peak\r\n");
        tx("(T)ell story\r\n");
        tx("(R)umors\r\n");
        tx("(L)eave\r\n");
        c = key();
        c = toupper(c);
        if (c == 'L') {
            clw();
            clr_map_window();
            return;
        }
        if (c == 'S') {
            speak(1, state.twn);
        }
        if (c == 'T') {
            clw();
            tx("Who wants to tell\r\n");
            tx("a story? (1-4)\r\n");
            d = key();
            d = toupper(d);
            if (d >= '1' && d <= '4') {
                if (state.p[state.cpl].dead[d - 48]) {
                    clw();
                    tx("Dead men do not\r\n");
                    tx("tell any great\r\n");
                    tx("stories...\r\n");
                    Delay(100);
                } else {
                    clw();
                    tx("%s stands up\r\n", state.p[state.cpl].name[d - 48]);
                    tx("and screams:\r\n");
                    tx("- ANYONE WHO WANNA HEAR\r\n");
                    tx("  A TRAVELLER'S STORY?\r\n\r\n");
                    Delay(100);
                    tx("- YEAAH! Roars the crowd!\r\n\r\n");
                    Delay(100);
                    tx("(T)rue story\r\n");
                    tx("(B)oast\r\n");
                    b = key();
                    b = toupper(b);
                    st = 0;
                    if (b == 'B') {
                        st = 100;
                    }
                    if (state.p[state.cpl].cl[d - 48] == 5) {
                        st += 100;
                    }
                    if (state.p[state.cpl].ch[d - 48] > 90) {
                        st += 50;
                    }
                    if (state.p[state.cpl].ch[d - 48] > 70) {
                        st += 50;
                    }
                    xs = (rand() % 150) + 1;
                    st += xs;
                    if (b == 'B') {
                        if ((rand() % 100) > state.p[state.cpl].ch[d - 48]) {
                            clw();
                            tx("- Out with the liar!\r\n");
                            st = 0;
                            Delay(100);
                            tx("You are thrown out.\r\n");
                            state.p[state.cpl].ch[d - 48] -= 10;
                            if (state.p[state.cpl].ch[d - 48] < 0) {
                                state.p[state.cpl].ch[d - 48] = 0;
                            }
                            clr_map_window();
                            return;
                        }
                    }
                    clw();
                    if (st > 400) {
                        tx("Superb Story! Let's\r\n");
                        tx("give 'em some ale, lads!\r\n");
                        tx("You are given a whole\r\n");
                        tx("tray of ale!\r\n\r\n");
                        ryl_pause();
                        state.p[state.cpl].ch[d - 48] += 3;
                    }
                    if (st > 300 && st < 401) {
                        tx("Very good! You've been\r\n");
                        tx("through much!\r\n\r\n");
                        ryl_pause();
                        state.p[state.cpl].ch[d - 48]++;
                    }
                    if (st > 150 && st < 301) {
                        tx("Nice story, Sir!\r\n\r\n");
                        ryl_pause();
                    }
                    if (st > 100 && st < 151) {
                        tx("The mob is bored.\r\n\r\n");
                        ryl_pause();
                        if (state.p[state.cpl].ch[d - 48]) {
                            state.p[state.cpl].ch[d - 48]--;
                        }
                    }
                    if (st < 101) {
                        tx("Most of the crowd\r\n");
                        tx("falls asleep. The rest\r\n");
                        tx("beg you to stop.\r\n\r\n");
                        ryl_pause();
                        if (state.p[state.cpl].ch[d - 48] >= 3) {
                            state.p[state.cpl].ch[d - 48] -= 3;
                        }
                    }
                }
            }
        }
        if (c == 'R') {
            clw();
            if (rumours < 5) {
                rumours++;
            }
            if (rumours > 4) {
                tx("The tavern has suddenly\r\n");
                tx("become very quiet.\r\n\r\n");
                ryl_pause();
            } else {
                switch (rand() % 5) {
                    case 0:
                        tx("After listening a while\r\n");
                        tx("you hear an old man say\r\n");
                        tx("to another:\r\n");
                        break;
                    case 1:
                        tx("A drunken bard bellows:\r\n");
                        break;
                    case 2:
                        tx("A halfling you rescued\r\n");
                        tx("from a brawl whispers:\r\n");
                        break;
                    case 3:
                        tx("The tavern keeper\r\n");
                        tx("motions to you to\r\n");
                        tx("come closer, and\r\n");
                        tx("whispers :\r\n");
                        break;
                    case 4:
                        tx("A bargirl tells you:\r\n");
                        break;
                }
                tx("\r\n");
                rumour();
                ryl_pause();
            }
        }
        if (c == 'O') {
            clw();
            tx("Order:\r\n\r\n");
            tx("A. Ale       2 au\r\n");
            tx("B. Mead      3 au\r\n");
            tx("C. Wine      4 au\r\n");
            tx("D. Fanta     1 au\r\n");
            tx("E. Nothing\r\n");
            d = key();
            d = toupper(d);
            if (d >= 'A' && d <= 'D') {
                pr = 0;
                switch (d) {
                    case 'A':
                        pr = 2;
                        break;
                    case 'B':
                        pr = 3;
                        break;
                    case 'C':
                        pr = 4;
                        break;
                    case 'D':
                        pr = 1;
                        break;
                }
                if (state.p[state.cpl].gold < pr) {
                    clw();
                    tx("No money - No drink!\r\n");
                    Delay(50);
                } else {
                    clw();
                    if (d != 'D') {
                        tx("AAH! (your health\r\n");
                        tx("returns).\r\n");
                        Delay(50);
                    } else {
                        tx("You are the laughing\r\n");
                        tx("stock of the entire\r\n");
                        tx("tavern...\r\n");
                        Delay(100);
                    }
                    state.p[state.cpl].gold -= pr;
                    for (i = 1; i < 5; i++) {
                        state.p[state.cpl].hp[i] += ((pr / 2) + 1);
                        if (state.p[state.cpl].hp[i] > state.p[state.cpl].h[i]) {
                            state.p[state.cpl].hp[i] = state.p[state.cpl].h[i];
                        }
                    }
                }
            }
        }
    }
}
void inn() {
    char c, d;
    int32_t i, pr = 0;

    state.tid++;
    if (state.tid == 25) {
        state.tid = 1;
    }

    clr_map_window();
    blitgfx(innpic);

    while (1) {
        clw();
        tx("%s, the Innkeeper\r\n", res_enum("name", 24 + state.twn));
        tx("welcomes you.\r\n\r\n");
        tx("Do you want supper,\r\n");
        tx("travellers?\r\n\r\n");
        tx("(Y)es\r\n");
        tx("(N)o\r\n");
        c = key();
        c = toupper(c);
        if (c == 'Y') {
            if (state.p[state.cpl].gold < 15 * state.p[state.cpl].lv[1] * state.p[state.cpl].lv[1]) {
                clw();
                tx("Sorry, supper is %d\r\n", 15 * state.p[state.cpl].lv[1] * state.p[state.cpl].lv[1]);
                tx("gold.\r\n");
                Delay(50);
            } else {
                state.p[state.cpl].gold -= 15 * state.p[state.cpl].lv[1] * state.p[state.cpl].lv[1];
                clw();
                tx("You are served a well\r\n");
                tx("tasting dinner. Some\r\n");
                tx("of your health returns.\r\n\r\n");
                for (i = 1; i < 5; i++) {
                    if (!state.p[state.cpl].dead[i]) {
                        state.p[state.cpl].hp[i] += 6;
                    }
                    if (state.p[state.cpl].hp[i] > state.p[state.cpl].h[i]) {
                        state.p[state.cpl].hp[i] = state.p[state.cpl].h[i];
                    }
                }
                ryl_pause();
            }
        }
        clw();
        tx("Anything to drink?\r\n\r\n");
        tx("(Y)es\r\n");
        tx("(N)o\r\n");
        c = key();
        c = toupper(c);
        if (c == 'Y') {
            clw();
            tx("What'll it be?\r\n\r\n");
            tx("A. Ale         2 au\r\n");
            tx("B. Mead        3 au\r\n");
            tx("C. Wine        5 au\r\n");
            tx("D. Whiskey     7 au\r\n");
            tx("E. Fanta       1 au\r\n");
            pr = 0;
            d = key();
            d = toupper(d);
            switch (d) {
                case 'A':
                    pr = 2;
                    break;
                case 'B':
                    pr = 3;
                    break;
                case 'C':
                    pr = 5;
                    break;
                case 'D':
                    pr = 7;
                    break;
                case 'E':
                    pr = 1;
                    break;
            }
            if (state.p[state.cpl].gold < pr) {
                clw();
                tx("No money - No drink!\r\n");
                Delay(50);
            } else {
                state.p[state.cpl].gold -= pr;
                clw();
                if (d != 'E') {
                    tx("Ahh...!\r\n");
                } else {
                    tx("The other guests giggle.\r\n");
                }
                Delay(50);
                for (i = 1; i < 5; i++) {
                    if (!state.p[state.cpl].dead[i]) {
                        state.p[state.cpl].hp[i] += ((pr / 2) + 1);
                        if (state.p[state.cpl].hp[i] > state.p[state.cpl].h[i]) {
                            state.p[state.cpl].hp[i] = state.p[state.cpl].h[i];
                        }
                    }
                }
            }
        }
        clw();
        tx("Are you in need\r\n");
        tx("of lodging?\r\n\r\n");
        tx("(Y)es\r\n");
        tx("(N)o\r\n");
        c = key();
        c = toupper(c);
        if (c == 'Y') {
            clw();
            tx("A room is %d gold/night.\r\n\r\n", state.p[state.cpl].lv[1] * 15 * state.p[state.cpl].lv[1]);
            tx("(T)ake it\r\n");
            tx("(L)eave\r\n");
            d = key();
            d = toupper(d);
            if (d != 'T') {
                clw();
                clr_map_window();
                return;
            }
            if (state.p[state.cpl].gold < state.p[state.cpl].lv[1] * 15 * state.p[state.cpl].lv[1]) {
                clw();
                tx("- AHA! No gold!\r\n");
                tx("You are thrown out.\r\n");
                Delay(100);
                clr_map_window();
                return;
            }
            state.p[state.cpl].gold -= state.p[state.cpl].lv[1] * 15 * state.p[state.cpl].lv[1];
            clw();
            tx("You go to bed...\r\n");
            Delay(50);
            tx("Zzzz...\r\n\r\n");
            state.hours_pigg = 14;
            Delay(75);
            tx("You wake up a little\r\n");
            tx("before dawn, well\r\n");
            tx("rested.\r\n\r\n");
            tx("You have breakfast\r\n");
            tx("and leave the inn.\r\n\r\n");
            ryl_pause();
            state.tid = 5;
            for (i = 1; i < 5; i++) {
                if (!state.p[state.cpl].dead[i]) {
                    state.p[state.cpl].hp[i] += state.p[state.cpl].h[i] / 4;
                }
                if (state.p[state.cpl].hp[i] > state.p[state.cpl].h[i]) {
                    state.p[state.cpl].hp[i] = state.p[state.cpl].h[i];
                }
            }
            clr_map_window();
            return;
        }
        while (1) {
            clw();
            tx("The innkeeper leaves.\r\n\r\n");
            tx("(C)hitchat with guests\r\n");
            tx("(L)eave\r\n");
            c = key();
            c = toupper(c);

            if (c == 'C') {
                speak(2, state.twn);
            } else {
                clw();
                clr_map_window();
                return;
            }
        }
    }
}
void merchant() /* HOS THE MERCHANT */
{
    char c, d, f;
    int32_t i, x, y, oc, pr;
    char str[10];

    state.tid++;
    if (state.tid == 25) {
        state.tid = 1;
    }

    if (state.tid > 18 || state.tid < 6) {
        clw();
        tx("Shop Closed.\r\n");
        Delay(50);
        return;
    }

    clr_map_window();
    blitgfx(merchantpic);

    while (1) {
        clw();
        tx("Merchant\r\n\r\n");
        tx("Can I help you? I\r\n");
        tx("trade in the finest\r\n");
        tx("items in the realm!\r\n\r\n");
        tx("(B)uy item\r\n");
        tx("(S)ell item\r\n");
        tx("(L)eave\r\n");

        c = key();
        c = toupper(c);

        if (c == 'L') {
            clw();
            tx("Well, bye then...\r\n");
            Delay(50);
            clr_map_window();
            return;
        }

        if (c == 'S') {
            clw();
            invlist();
            d = key();
            d = toupper(d);
            oc = 0;
            if (d >= 'A' && d <= 'P') {
                oc = state.p[state.cpl].inventory[d - 64];
                if (oc < 32 || oc == 47) {
                    clw();
                    tx("- I don't deal in that\r\n");
                    tx("  kind of stuff!\r\n");
                    Delay(50);
                } else {
                    clw();
                    tx("- Ok. Give ya %d!\r\n\r\n", obcost(oc) / 2);
                    tx("(Y)es\r\n");
                    tx("(N)o\r\n\r\n");
                    f = key();
                    f = toupper(f);
                    if (f == 'Y') {
                        tx("Sold!\r\n");
                        Delay(50);
                        state.p[state.cpl].gold += (obcost(oc) / 2);
                        state.p[state.cpl].inventory[d - 64] = 0;
                        statusline();
                    }
                }
            }
        }

        if (c == 'B') {
            while (1) {
                clw();
                statusline();
                tx("(A) Torch           1 au\r\n");
                tx("(B) Food Ration    %2d au\r\n", state.p[state.cpl].lv[1] + 1);
                tx("(C) Blanket        30 au\r\n");
                tx("(D) Tent           80 au\r\n");
                tx("(E) Rope           15 au\r\n");
                tx("(F) Healing Potion %d au\r\n", obcost(36) + state.p[state.cpl].lv[1] * 3);
                tx("(G) Donkey        800 au\r\n");
                tx("(H) Nothing\r\n");
                c = key();
                c = toupper(c);
                oc = c - 64;
                oc += 30;
                if (oc == 37) {
                    oc = 47;
                }
                if (oc != 47) {
                    if (oc < 31 || oc > 36) {
                        break;  // The availible items
                    }
                    // There are others, but they
                }  // Aren't purchasable here.
                pr = obcost(oc);
                if (oc == 36) {
                    pr += state.p[state.cpl].lv[1] * 3;
                }
                clw();
                switch (oc) {
                    case 31:
                        tx("Ah, the finest torches\r\n");
                        tx("in the realm.\r\n");
                        break;
                    case 32:
                        tx("Better food is not\r\n");
                        tx("availible anywhere else\r\n");
                        tx("in the entire realm.\r\n");
                        break;
                    case 33:
                        tx("Our blankets are very\r\n");
                        tx("warm and soft.\r\n");
                        break;
                    case 34:
                        tx("Noone manufactures\r\n");
                        tx("better tents than\r\n");
                        tx("we do!\r\n");
                        break;
                    case 35:
                        tx("The finest hemp\r\n");
                        tx("ropes in the realm.\r\n");
                        break;
                    case 36:
                        tx("The best healing\r\n");
                        tx("potions availible\r\n");
                        tx("for money!\r\n");
                        break;
                    case 47:
                        tx("Our stout donkeys\r\n");
                        tx("are bred just for\r\n");
                        tx("carrying your food\r\n");
                        tx("and equipment.\r\n");
                        break;
                }
                tx("\r\n");
                if (oc == 31 || oc == 32) {
                    pr = obcost(oc);
                    if (oc == 32) {
                        pr = 0;
                        pr += (state.p[state.cpl].lv[1] + 1);
                    }
                    tx("%d gold each, sir.\r\n", pr);
                    tx("How many? ");
                    strcpy(str, getstr(5));
                    x = atol(str);
                    if (oc == 31) {
                        if (x + state.p[state.cpl].torches > 80) {
                            clw();
                            tx("You can carry no\r\n");
                            tx("more than 80\r\n");
                            tx("torches...\r\n");
                            Delay(50);
                            x = 80 - state.p[state.cpl].torches;
                        }
                    }
                    if (oc == 32) {
                        for (i = 1, y = 0; i < 17; i++) {
                            if (state.p[state.cpl].inventory[i] == 47) {
                                y++;
                            }
                        }
                        // 150 + y*150 = 150(y + 1)
                        if (x + state.p[state.cpl].food > (150 + y * 150)) {
                            clw();
                            tx("You can carry no\r\n");
                            tx("more than %d units!\r\n", (150 + y * 150));
                            Delay(50);
                            x = (150 + y * 150) - state.p[state.cpl].food;
                        }
                    }
                    if (x < 0) {
                        x = 0;
                    }
                    if (state.p[state.cpl].gold < pr * x) {
                        clw();
                        tx("Too expensive!\r\n");
                        Delay(50);
                    } else {
                        state.p[state.cpl].gold -= pr * x;
                        if (oc == 31) {
                            state.p[state.cpl].torches += x;
                        }
                        if (oc == 32) {
                            state.p[state.cpl].food += x;
                        }
                        clw();
                        if (oc == 31 || (oc == 32 && x)) {
                            tx("Deal!");
                        }
                        if (oc != 31 && oc != 32) {
                            tx("Deal!");
                        }
                        Delay(50);
                    }
                } else {
                    pr = obcost(oc);
                    if (oc == 36) {
                        pr += state.p[state.cpl].lv[1] * 3;
                    }
                    tx("Price: %d\r\n\r\n", pr);
                    tx("(P)ay\r\n");
                    tx("(N)one\r\n");
                    c = key();
                    c = toupper(c);

                    if (c == 'P') {
                        if (state.p[state.cpl].gold < pr) {
                            clw();
                            tx("Too expensive!\r\n");
                            Delay(50);
                        } else {
                            for (i = 1, x = 0; i < 17; i++) {
                                if (state.p[state.cpl].inventory[i] == 0) {
                                    x = i;
                                    break;
                                }
                            }
                            if (!x) {
                                tx("You cannot carry more.\r\n");
                                Delay(50);
                            } else {
                                state.p[state.cpl].inventory[x] = oc;
                                state.p[state.cpl].gold -= pr;
                                clw();
                                tx("Deal!\r\n");
                                Delay(50);
                            }
                        }
                    } else {
                        clw();
                        break;
                    }
                }
            }
        }
    }
    clr_map_window();
}
