#include "ryl.h"

void timehandler() {
    tid++;
    if (tid == 25)
        tid = 1;
}
void foodhandler() {
    int i;

    // Regenerate mana //
    for (i = 1; i < 5; i++) {
        if (p[cpl].cl[i] == 3 || p[cpl].cl[i] == 4) {
            if (p[cpl].ma[i] < p[cpl].wi[i] && !(rand() % 2))
                p[cpl].ma[i]++;
        }
    }

    if (!(p[cpl].tid_kvar % 5)) {
        p[cpl].food -= p[cpl].pl;
        p[cpl].food -= p[cpl].lv[1] / 4;
    }
    if (p[cpl].food < 0)
        p[cpl].food = 0;
    // Starving routine //

    if (p[cpl].food == 0) {
        for (i = 1; i < 5; i++) {
            if (!p[cpl].dead[i])
                p[cpl].hp[i] -= 2;
            if (p[cpl].hp[i] < 1 && !p[cpl].dead[i]) {
                p[cpl].hp[i] = 0;
                clw();
                tx("%s starved\r\n", p[cpl].name[i]);
                tx("to death!!!\r\n");
                Delay(50);
                clw();
                p[cpl].dead[i] = 1;
                if (p[cpl].dead[1] && p[cpl].dead[2] && p[cpl].dead[3] && p[cpl].dead[4])
                    losegame();
            }
        }
    }

    // Time routine //
    hours_pigg--;
    if (hours_pigg == 0) {
        clw();
        tx("You begin to feel tired\r\n");
        return;
    }
    if (hours_pigg == -4) {
        clw();
        tx("You are exhausted!!!\r\n");
        for (i = 1; i < 5; i++)
            if (p[cpl].hp[i] > 1)
                p[cpl].hp[i]--;
        return;
    }
    if (hours_pigg == -8) {
        clw();
        tx("You begin to feel weak\r\n");
        tx("from lack of sleep.\r\n\r\n");
        for (i = 1; i < 5; i++)
            if (p[cpl].hp[i] > 1)
                p[cpl].hp[i]--;
        ryl_pause();
        clw();
        return;
    }
    if (hours_pigg < -8) {
        clw();
        tx("You faint from lack\r\n");
        tx("of well needed sleep.\r\n");
        for (i = 1; i < 5; i++)
            if (p[cpl].hp[i] > 1)
                p[cpl].hp[i]--;
    }
}
void slumpvis() {
    long t;
    char c, kl[8];
    int r;
    uint8_t rflag = 0;

    getclk(kl);
    srand((unsigned int) kl[7]);
    r = rand() % 100;

    rflag = mission_control();
    if (rflag)
        return;

    if (!(p[cpl].tid_kvar % 101) && !stricmp(global_name, "Karl Skagerberg"))
        encounter(90, 1);

    if (!(p[cpl].tid_kvar % 60) && p[cpl].tid_kvar) {
        if (!reg) {
            clw();
            tx("Aw! Shucks! Fergot!\r\n");
            tx("This is the standard\r\n");
            tx("'Unregistered Game\r\n");
            tx(" painful delay!'\r\n");
            Delay(300);
            clw();
            tx("Five.\r\n");
            Delay(150);
            tx("Four.\r\n");
            Delay(150);
            tx("Three.\r\n");
            Delay(150);
            tx("Two.\r\n");
            Delay(150);
            tx("One.\r\n");
            Delay(150);
            clw();
            tx("Please encourage your\r\n");
            tx("sysop to register!\r\n");
        }
    }

    if (!(p[cpl].tid_kvar % 41) && p[cpl].tid_kvar) {
        if (!reg) {
            clw();
            tx("Suddenly some gnolls\r\n");
            tx("appear out of nowhere.\r\n");
            tx("The leader speaks:\r\n");
            tx("- Tell ze sysop to\r\n");
            tx("  register, or we'll tear\r\n");
            tx("  yar guts out!!!\r\n\r\n");
            ryl_pause();
            clw();
            tx("- Ah, guess we can do\r\n");
            tx("  anyway...\r\n\r\n");
            ryl_pause();
            encounter(31, 1);
            update_map();
            clw();
            tx("Wiping the blood of your\r\n");
            tx("weapons, you think it\r\n");
            tx("might be a good idea to\r\n");
            tx("get this thing registered.\r\n\r\n");
            tx("You know that you'll get\r\n");
            tx("100 extra moves per\r\n");
            tx("day with the registered\r\n");
            tx("version.\r\n\r\n");
            ryl_pause();
            clw();
            if (p[cpl].tid_kvar)
                p[cpl].tid_kvar--;
        }
    }

    if (r < 94 && r >= 3) {
        // Nothing special, see if terrain has changed and display nice message //
        check_terrain(last_terrain, map[p[cpl].y][p[cpl].x]);
        return;
    }
    if (r < 3) {
        clr_map_window();
        blitgfx(cavepic);
        clw();
        tx("You find a cave!\r\n\r\n");
        tx("(E)nter\r\n");
        tx("(L)eave\r\n");
        while (1) {
            c = key();
            c = toupper(c);
            if (c == 'L' || c == 'E')
                break;
        }
        time(&t);
        srand((unsigned int) t);
        if (c == 'E') {
            if (p[cpl].tid_kvar > 10)
                p[cpl].tid_kvar -= 10;
            else {
                clw();
                tx("You don't have enough\r\n");
                tx("moves left to explore\r\n");
                tx("the cave...\r\n\r\n");
                ryl_pause();
                clr_map_window();
                clw();
                return;
            }
            clr_map_window();
            dungeon(((rand() % 14) + 3), 0);
            return;
        } else {
            clw();
            clr_map_window();
            update_map();
            return;
        }
    }

    // slumpmote //

    time(&t);
    srand((unsigned int) t - tid + p[cpl].x);

    // 40-53 //

    if (!(rand() % 2)) {
        encounter((rand() % 10) + 1, 0);
        clr_map_window();
        update_map();
    } else {
        encounter((rand() % 14) + 40, 0);
        clr_map_window();
        update_map();
    }
}

void losegame() {
    clr_map_window();
    blitgfx(deathpic);
    clw();
    tx("Alas, your party of\r\n");
    tx("adventurers have all\r\n");
    tx("gone to meet their\r\n");
    tx("maker...\r\n\r\n");
    ryl_pause();
    cleanexit();
}

void update_map() {
    c(9, 6);
    tx("[%dm%s [%dm%s [%dm%s [%dm%s [%dm%s", frg(map[p[cpl].y - 2][p[cpl].x - 2]),
       kar(map[p[cpl].y - 2][p[cpl].x - 2]), frg(map[p[cpl].y - 2][p[cpl].x - 1]),
       kar(map[p[cpl].y - 2][p[cpl].x - 1]), frg(map[p[cpl].y - 2][p[cpl].x]),
       kar(map[p[cpl].y - 2][p[cpl].x]), frg(map[p[cpl].y - 2][p[cpl].x + 1]),
       kar(map[p[cpl].y - 2][p[cpl].x + 1]), frg(map[p[cpl].y - 2][p[cpl].x + 2]),
       kar(map[p[cpl].y - 2][p[cpl].x + 2]));

    c(9, 8);
    tx("[%dm%s [%dm%s [%dm%s [%dm%s [%dm%s", frg(map[p[cpl].y - 1][p[cpl].x - 2]),
       kar(map[p[cpl].y - 1][p[cpl].x - 2]), frg(map[p[cpl].y - 1][p[cpl].x - 1]),
       kar(map[p[cpl].y - 1][p[cpl].x - 1]), frg(map[p[cpl].y - 1][p[cpl].x]),
       kar(map[p[cpl].y - 1][p[cpl].x]), frg(map[p[cpl].y - 1][p[cpl].x + 1]),
       kar(map[p[cpl].y - 1][p[cpl].x + 1]), frg(map[p[cpl].y - 1][p[cpl].x + 2]),
       kar(map[p[cpl].y - 1][p[cpl].x + 2]));

    c(9, 10);
    tx("[%dm%s [%dm%s [44m[%dm%s[40m [%dm%s [%dm%s", frg(map[p[cpl].y][p[cpl].x - 2]),
       kar(map[p[cpl].y][p[cpl].x - 2]), frg(map[p[cpl].y][p[cpl].x - 1]),
       kar(map[p[cpl].y][p[cpl].x - 1]), frg(map[p[cpl].y][p[cpl].x]), kar(map[p[cpl].y][p[cpl].x]),
       frg(map[p[cpl].y][p[cpl].x + 1]), kar(map[p[cpl].y][p[cpl].x + 1]),
       frg(map[p[cpl].y][p[cpl].x + 2]), kar(map[p[cpl].y][p[cpl].x + 2]));

    c(9, 12);
    tx("[%dm%s [%dm%s [%dm%s [%dm%s [%dm%s", frg(map[p[cpl].y + 1][p[cpl].x - 2]),
       kar(map[p[cpl].y + 1][p[cpl].x - 2]), frg(map[p[cpl].y + 1][p[cpl].x - 1]),
       kar(map[p[cpl].y + 1][p[cpl].x - 1]), frg(map[p[cpl].y + 1][p[cpl].x]),
       kar(map[p[cpl].y + 1][p[cpl].x]), frg(map[p[cpl].y + 1][p[cpl].x + 1]),
       kar(map[p[cpl].y + 1][p[cpl].x + 1]), frg(map[p[cpl].y + 1][p[cpl].x + 2]),
       kar(map[p[cpl].y + 1][p[cpl].x + 2]));

    c(9, 14);
    tx("[%dm%s [%dm%s [%dm%s [%dm%s [%dm%s[37m", frg(map[p[cpl].y + 2][p[cpl].x - 2]),
       kar(map[p[cpl].y + 2][p[cpl].x - 2]), frg(map[p[cpl].y + 2][p[cpl].x - 1]),
       kar(map[p[cpl].y + 2][p[cpl].x - 1]), frg(map[p[cpl].y + 2][p[cpl].x]),
       kar(map[p[cpl].y + 2][p[cpl].x]), frg(map[p[cpl].y + 2][p[cpl].x + 1]),
       kar(map[p[cpl].y + 2][p[cpl].x + 1]), frg(map[p[cpl].y + 2][p[cpl].x + 2]),
       kar(map[p[cpl].y + 2][p[cpl].x + 2]));
}
int frg(char t) {
    switch (t) {
        case 'm':
            return 37;
        case 'w':
            return 34;
        case 's':
            return 33;
        case '0':
            return 37;
        case '1':
            return 37;
        case 'f':
            return 37;
        case 'd':
            return 31;
        case 't':
            return 35;
        case 'g':
            return 32;
        case '2':
            return 37;
    }
    return 36;
}
char *kar(char t) {
    switch (t) {
        case 'm':
            return "/\\";
        case 'w':
            return "~~";
        case 's':
            return "..";
        case '0':
            return "TT";
        case '1':
            return "HH";
        case 'f':
            return "^^";
        case 'd':
            return "::";
        case 't':
            return "%%";
        case 'g':
            return "gg";
        case '2':
            return "CC";
    }
}

void weaponoff() {
    char c;
    int i, x;

    clw();
    tx("Who? (1-4)\r\n");
    c = key();
    if (c >= '1' && c <= '4') {
        if (p[cpl].wp[c - 48] == 0) {
            clw();
            tx("%s has no weapon!\r\n", sextext(c - 48, 1));
            Delay(40);
            clw();
            return;
        }
        for (i = 1, x = 0; i < 17; i++) {
            if (!p[cpl].inventory[i]) {
                x = i;
                break;
            }
        }
        if (!x) {
            clw();
            tx("No room!\r\n");
            Delay(30);
            clw();
            return;
        } else {
            clw();
            tx("%s removed.\r\n", object(p[cpl].wp[c - 48]));
            Delay(40);
            p[cpl].inventory[x] = p[cpl].wp[c - 48];
            p[cpl].wp[c - 48] = 0;
            clw();
            return;
        }
    }
    clw();
}
void armouroff() {
    char c;
    int i, x;

    clw();
    tx("Who? (1-4)\r\n");
    c = key();
    if (c >= '1' && c <= '4') {
        if (p[cpl].ar[c - 48] == 0) {
            clw();
            tx("%s has no armour!\r\n", sextext(c - 48, 1));
            Delay(40);
            clw();
            return;
        }
        for (i = 1, x = 0; i < 17; i++) {
            if (!p[cpl].inventory[i]) {
                x = i;
                break;
            }
        }
        if (!x) {
            clw();
            tx("No room!\r\n");
            Delay(30);
            clw();
            return;
        } else {
            clw();
            tx("%s removed.\r\n", object(p[cpl].ar[c - 48]));
            Delay(40);
            p[cpl].inventory[x] = p[cpl].ar[c - 48];
            p[cpl].ar[c - 48] = 0;
            clw();
            return;
        }
    }
    clw();
}
void weaponr() {
    int oc, temp;
    char d, b;

    clw();
    invlist();
    d = key();
    d = toupper(d);
    oc = 0;
    if (d >= 'A' && d <= 'P') {
        oc = p[cpl].inventory[d - 64];
        if (oc > 19 || oc == 0) {
            clw();
            tx("Not a valid weapon.\r\n");
            Delay(30);
            clw();
            return;
        }
    } else {
        clw();
        return;
    }

    clw();
    tx("Who? (1-4)\r\n");
    b = key();
    if (b < '1' || b > '4') {
        clw();
        return;
    }

    if (p[cpl].dead[b - 48]) {
        clw();
        tx("Dead.\r\n");
        return;
    }
    temp = p[cpl].wp[b - 48];
    p[cpl].wp[b - 48] = p[cpl].inventory[d - 64];
    p[cpl].inventory[d - 64] = temp;
    clw();
    tx("Weapon changed.\r\n");
    Delay(30);
    clw();
    return;
}
void armourr() {
    int oc, temp;
    char d, b;

    clw();
    invlist();
    d = key();
    d = toupper(d);
    oc = 0;
    if (d >= 'A' && d <= 'P') {
        oc = p[cpl].inventory[d - 64];
        if (oc < 20 || oc > 29 || oc == 0) {
            clw();
            tx("Not a valid armour.\r\n");
            Delay(30);
            clw();
            return;
        }
    } else {
        clw();
        return;
    }

    clw();
    tx("Who? (1-4)\r\n");
    b = key();
    if (b < '1' || b > '4') {
        clw();
        return;
    }

    if (p[cpl].dead[b - 48]) {
        clw();
        tx("Dead.\r\n");
        return;
    }
    temp = p[cpl].ar[b - 48];
    p[cpl].ar[b - 48] = p[cpl].inventory[d - 64];
    p[cpl].inventory[d - 64] = temp;
    clw();
    tx("Armour changed.\r\n");
    Delay(30);
    clw();
    return;
}
uint8_t drop() {
    char d;
    int oc, i, y;

    clw();
    invlist();
    d = key();
    d = toupper(d);
    oc = 0;
    if (d >= 'A' && d <= 'P') {
        clw();
        if (p[cpl].inventory[d - 64]) {
            tx("You drop the\r\n");
            tx("%s.\r\n", object(p[cpl].inventory[d - 64]));
            Delay(50);
            if (p[cpl].inventory[d - 64] == 47) {
                for (i = 1, y = 0; i < 17; i++)
                    if (p[cpl].inventory[i] == 47)
                        y++;
                y--;
                if (p[cpl].food > (150 + 150 * y)) {
                    clw();
                    tx("You are forced to\r\n");
                    tx("leave some of your\r\n");
                    tx("food behind...\r\n");
                    Delay(50);
                    p[cpl].food = (150 + 150 * y);
                }
            }
            p[cpl].inventory[d - 64] = 0;
            clw();
            return (uint8_t) (d - 64);
        } else {
            tx("Nothing to drop.\r\n");
            Delay(30);
            clw();
            return 0;
        }
    } else {
        clw();
        tx("Aborted.\r\n");
        Delay(30);
        return 0;
    }
}
void camp() {
    int i, death, guard = 0, temperature, x;
    char c, d;

    if (hours_pigg > 0) {
        clw();
        tx("Not tired.\r\n");
        Delay(30);
        clw();
        return;
    }

    if (p[cpl].dead[1] && p[cpl].dead[2] && p[cpl].dead[3] && p[cpl].dead[4])
        return;

    clr_map_window();

    for (i = 1, x = 0; i < 17; i++)
        if (p[cpl].inventory[i] == 34)
            x = 1;

    if (x)
        blitgfx(17);
    else
        blitgfx(18);
    clw();

    tx("Camp.\r\n\r\n");
    tx("Post a guard?\r\n\r\n");
    tx("(Y)es\r\n");
    tx("(N)o\r\n");
    c = key();
    c = toupper(c);
    if (c == 'Y') {
        while (1) {
            clw();
            tx("Who? (1-4)\r\n");
            d = key();
            d = toupper(d);
            if (d >= '1' && d <= '4') {
                if (!(p[cpl].dead[d - 48]))
                    break;
                else {
                    clw();
                    tx("Dead.\r\n");
                    Delay(50);
                }
            } else {
                clw();
                tx("Not valid!\r\n");
                Delay(50);
            }
        }
        guard = d - 48;
        clw();
        tx("%s stands guard.\r\n", p[cpl].name[d - 48]);
        tx("%s won't get any\r\n", sextext(d - 48, 1));
        tx("health back during\r\n");
        tx("this rest.\r\n\r\n");
        ryl_pause();
    }
    clw();
    tx("Zzz...\r\n");
    Delay(75);

    /* TEMPERATURE CHECK */

    temperature = (rand() % 15) + 1;
    if (temperature >= 6 && temperature < 10) {
        clw();
        tx("Low Temperature.\r\n");
        Delay(30);
        for (i = 1, x = 0; i < 17; i++) {
            if (p[cpl].inventory[i] == 33 || p[cpl].inventory[i] == 34) {
                x = 1;
                break;
            }
        }
        if (!x) {
            tx("No good equipment,\r\n");
            tx("Party loses 5 health.\r\n");
            Delay(75);
            for (i = 1; i < 5; i++) {
                p[cpl].hp[i] -= 5;
                if (p[cpl].hp[i] < 1)
                    p[cpl].hp[i] = 1;
            }
        }
    }
    if (temperature >= 10) {
        tx("Very low temperature.\r\n");
        for (i = 1, x = 0; i < 17; i++) {
            if (p[cpl].inventory[i] == 34) {
                x = 1;
                break;
            }
        }
        Delay(30);
        if (!x) {
            tx("No good equipment,\r\n");
            tx("Party loses 10 health.\r\n");
            Delay(75);
            for (i = 1; i < 5; i++) {
                p[cpl].hp[i] -= 5;
                if (p[cpl].hp[i] < 1)
                    p[cpl].hp[i] = 1;
            }
        }
    }

    /* INCREASE TIME */

    for (i = 1; i < 9; i++) {
        tid++;
        if (tid == 25)
            tid = 1;
    }
    hours_pigg = 14;
    if (p[cpl].food < 4 * p[cpl].pl)
        p[cpl].food = 0;
    else
        p[cpl].food -= 4 * p[cpl].pl;
    if (p[cpl].food < 0)
        p[cpl].food = 0;
    for (i = 1; i < 5; i++) {
        if (i != guard) {
            if (!p[cpl].dead[i])
                p[cpl].hp[i] += 15;
            if (p[cpl].hp[i] > p[cpl].h[i])
                p[cpl].hp[i] = p[cpl].h[i];
        }
    }
    if (rand() % 100 > 80) {
        clw();
        tx("Ambush!!!\r\n");
        Delay(50);
        if (!guard && p[cpl].pl > 1) {
            death = rand() % 4;
            death++;
            tx("%s is dead\r\n", p[cpl].name[death]);
            tx("before you can\r\n");
            tx("react!!!\r\n\r\n");
            p[cpl].hp[death] = 0;
            p[cpl].dead[death] = 1;
            p[cpl].pl--;
            ryl_pause();
        }
        encounter((rand() % 10) + 1, 0);
        //		update_map();
    } else {
        clw();
        tx("You wake up 8 hours\r\n");
        tx("later. Well rested.\r\n\r\n");
        ryl_pause();
        clr_map_window();
        clw();
    }
    update_map();
}
void quaff() {
    int oc;
    char c, d;

    clw();
    tx("Quaff what?\r\n\r\n");
    invlist();
    while (1) {
        d = key();
        d = toupper(d);
        oc = 0;
        if (d >= 'A' && d <= 'P') {
            oc = p[cpl].inventory[d - 64];
            if (oc < 36 || oc > 38) {
                clw();
                tx("Not valid.\r\n");
                Delay(50);
                clw();
                return;
            }
            break;
        } else {
            clw();
            return;
        }
    }

    clw();
    tx("Who? (1-4)\r\n");
    while (1) {
        c = key();
        c = toupper(c);
        if (c >= '1' && c <= '4') {
            if (!p[cpl].dead[c - 48])
                break;
            else {
                clw();
                tx("Dead.\r\n");
                Delay(30);
                clw();
            }
        }
    }
    p[cpl].inventory[d - 64] = 0;
    clw();
    tx("Gulp!\r\n\r\n");
    Delay(30);

    switch (oc) {
        case 36:
            tx("The healing brew warms\r\n");
            tx("%s inside...\r\n\r\n", sextext(c - 48, 2));
            ryl_pause();
            clw();
            p[cpl].hp[c - 48] += (15 + p[cpl].lv[c - 48]);
            if (p[cpl].hp[c - 48] > p[cpl].h[c - 48])
                p[cpl].hp[c - 48] = p[cpl].h[c - 48];
            break;

        case 37:
            tx("Horrible pain sears %s\r\n", sextext(c - 48, 3));
            tx("entrails as the poison\r\n");
            tx("begins to work.\r\n\r\n");
            ryl_pause();
            clw();
            p[cpl].hp[c - 48] -= (15 + p[cpl].lv[c - 48]);
            if (p[cpl].hp[c - 48] < 1)
                p[cpl].hp[c - 48] = 1;
            break;

        case 38:
            if (p[cpl].cl[c - 48] == 3 || p[cpl].cl[c - 48] == 4) {
                tx("It tingles slightly in\r\n");
                tx("%s body, as all his\r\n", sextext(c - 48, 3));
                tx("spell points return.\r\n\r\n");
                ryl_pause();
                clw();
                p[cpl].ma[c - 48] = p[cpl].wi[c - 48];
            } else {
                tx("The potion has no special\r\n");
                tx("effect on this character.\r\n\r\n");
                ryl_pause();
                clw();
            }
            break;
    }
}
void hunt() {
    char c;
    int fdplus;

    while (1) {
        clw();
        tx("Who? (1-4)\r\n");
        c = key();
        c = toupper(c);
        if (c >= '1' && c <= '4') {
            if (!p[cpl].dead[c - 48])
                break;
            else {
                clw();
                tx("Dead.\r\n");
                Delay(30);
            }
        }
    }
    if (p[cpl].cl[c - 48] != 6) {
        clw();
        tx("%s is not a ranger.\r\n", sextext(c - 48, 1));
        Delay(35);
        clw();
        return;
    }
    clw();
    if (p[cpl].tid_kvar < 5) {
        tx("Too few moves for that.\r\n");
        Delay(50);
        return;
    }
    p[cpl].tid_kvar -= 5;
    tx("%s tries\r\n", p[cpl].name[c - 48]);
    tx("to trap a rabbit...\r\n");
    Delay(70);
    if (rand() % 100 < 45) {
        fdplus = 10 + (rand() % 10) + p[cpl].lv[1];
        tx("...and succeeds.\r\n");
        tx("The party gains\r\n");
        tx("%d food rations.\r\n\r\n", fdplus);
        p[cpl].food += fdplus;
        ryl_pause();
        clw();
        return;
    }
    tx("...but fails.\r\n\r\n");
    ryl_pause();
    clw();
}
void spell(int place) {
    char b, c, d;
    int i, vem, x, oc;
    long t;

    clw();
    tx("Who? (1-4)\r\n");
    while (1) {
        c = key();
        if (c >= '1' && c <= '4')
            break;
    }
    vem = (c - 48);

    clw();
    if (p[cpl].cl[vem] == 3) {
        tx("Sorry. Mage spells are\r\n");
        tx("combat-only.\r\n");
        Delay(75);
        clw();
        return;
    }
    if (p[cpl].cl[vem] != 4) {
        tx("Wrong class.\r\n");
        Delay(50);
        clw();
        return;
    }
    if (p[cpl].dead[vem]) {
        clw();
        tx("Dead.\r\n");
        Delay(30);
        return;
    }

    tx("1. Healing     (%3d/%3d)\r\n", 8 + p[cpl].lv[vem], p[cpl].ma[vem]);
    tx("2. Restoration (%3d/%3d)\r\n", 20 + p[cpl].lv[vem], p[cpl].ma[vem]);
    tx("3. Rebirth     (%3d/%3d)\r\n", 90 + p[cpl].lv[vem], p[cpl].ma[vem]);
    tx("4. Banish Evil (%3d/%3d)\r\n", 85 + p[cpl].lv[vem], p[cpl].ma[vem]);
    tx("5. Revelation  (%3d/%3d)\r\n", 40 + p[cpl].lv[vem], p[cpl].ma[vem]);
    tx("6. Teleport    (%3d/%3d)\r\n", 35 + p[cpl].lv[vem], p[cpl].ma[vem]);
    tx("7. Earthquake  (%3d/%3d)\r\n", 55 + p[cpl].lv[vem], p[cpl].ma[vem]);
    tx("8. Abort\r\n");

    while (1) {
        c = key();
        if (c >= '1' && c <= '8')
            break;
    }

    if (c == '8') {
        clw();
        return;
    }

    switch (c - 48) {
        case 1:
            clw();
            if (p[cpl].ma[vem] < (8 + p[cpl].lv[vem])) {
                tx("Not enough spell pts!\r\n");
                Delay(50);
                clw();
                break;
            }
            p[cpl].ma[vem] -= (8 + p[cpl].lv[vem]);
            tx("Who? (1-4)\r\n");
            while (1) {
                d = key();
                if (d >= '1' && d <= '4')
                    break;
            }
            x = (rand() % 10) + 10 + (p[cpl].lv[vem] * 2);
            if (p[cpl].dead[d - 48]) {
                clw();
                tx("Dead.\r\n");
                Delay(30);
                clw();
                break;
            }
            p[cpl].hp[d - 48] += x;
            if (p[cpl].hp[d - 48] > p[cpl].h[d - 48])
                p[cpl].hp[d - 48] = p[cpl].h[d - 48];
            clw();
            tx("%s heals\r\n", p[cpl].name[vem]);
            tx("%s\r\n", p[cpl].name[d - 48]);
            tx("for %d hit points...\r\n", x);
            Delay(75);
            clw();
            break;

        case 2:
            clw();
            if (p[cpl].ma[vem] < (20 + p[cpl].lv[vem])) {
                tx("Not enough spell pts!\r\n");
                Delay(50);
                clw();
                break;
            }
            p[cpl].ma[vem] -= (20 + p[cpl].lv[vem]);
            tx("Who? (1-4)\r\n");
            while (1) {
                d = key();
                if (d >= '1' && d <= '4')
                    break;
            }
            p[cpl].hp[d - 48] = p[cpl].h[d - 48];
            clw();
            tx("%s heals\r\n", p[cpl].name[vem]);
            tx("%s\r\n", p[cpl].name[d - 48]);
            tx("fully...\r\n");
            Delay(75);
            clw();
            break;

        case 3:
            clw();
            if (p[cpl].ma[vem] < (90 + p[cpl].lv[vem])) {
                tx("Not enough spell pts!\r\n");
                Delay(50);
                clw();
                break;
            }
            tx("Who? (1-4)\r\n");
            while (1) {
                d = key();
                if (d >= '1' && d <= '4')
                    break;
            }
            if (!p[cpl].dead[d - 48]) {
                tx("%s is alive and\r\n", p[cpl].name[d - 48]);
                tx("healthy.\r\n");
                Delay(75);
                break;
            }
            p[cpl].ma[vem] -= (90 + p[cpl].lv[vem]);
            p[cpl].dead[d - 48] = 0;
            p[cpl].hp[d - 48] = 1;
            clw();
            tx("%s's eyelids\r\n", p[cpl].name[d - 48]);
            tx("flicker, and finally\r\n");
            tx("%s opens %s eyes!\r\n", sextext(d - 48, 1), sextext(d - 48, 3));
            Delay(85);
            clw();
            break;

        case 4:
            clw();
            if (p[cpl].ma[vem] < (85 + p[cpl].lv[vem])) {
                tx("Not enough spell pts!\r\n");
                Delay(50);
                clw();
                break;
            }
            if (place != 1) {
                tx("There is no particular\r\n");
                tx("target for this banishing.\r\n");
                tx("This spell is meant for\r\n");
                tx("use in dungeons and caves.\r\n\r\n");
                ryl_pause();
                clw();
                break;
            }
            p[cpl].ma[vem] -= (85 + p[cpl].lv[vem]);
            clw();
            tx("%s raises %s hands.\r\n", p[cpl].name[vem], sextext(vem, 3));
            tx("%s gathers divine power\r\n", sextext(vem, 1));
            tx("between %s palms, and\r\n", sextext(vem, 3));
            tx("suddenly clasps them.\r\n");
            tx("together over %s head.\r\n", sextext(vem, 3));
            tx("There is a deafening clap\r\n");
            tx("of thunder, and you hear\r\n");
            tx("beings scream in agony\r\n");
            tx("in the dungeon below...\r\n\r\n");
            ryl_pause();
            clw();
            banished = 1;
            break;

        case 5:
            clw();
            if (p[cpl].ma[vem] < (40 + p[cpl].lv[vem])) {
                tx("Not enough spell pts!\r\n");
                Delay(50);
                clw();
                break;
            }
            if (place != 1) {
                tx("This spell can be used in\r\n");
                tx("dungeons only, where there\r\n");
                tx("is a limited radius for\r\n");
                tx("the caster to enchant.\r\n\r\n");
                ryl_pause();
                clw();
                break;
            }
            p[cpl].ma[vem] -= (40 + p[cpl].lv[vem]);
            if (rand() % 100 > 40) {
                clw();
                tx("There is nothing\r\n");
                tx("special near.\r\n\r\n");
                ryl_pause();
                clw();
                break;
            }
            clw();
            tx("Something special is\r\n");
            tx("near!\r\n");
            Delay(75);
            clw();
            x = (rand() % 3);
            if (x == 0) {
                oc = rand() % 14;
                oc += 1;
            }
            if (x == 1) {
                oc = rand() % 7;
                oc += 36;
            }
            if (x == 2) {
                oc = rand() % 12;
                oc += 100;
            }
            tx("You find a\r\n");
            tx("%s.\r\n\r\n", object(oc));
            tx("(T)ake it\r\n");
            tx("(L)eave it\r\n");
            c = key();
            c = toupper(c);
            if (c == 'T') {
                for (i = 1, x = 0; i < 17; i++) {
                    if (!p[cpl].inventory[i]) {
                        p[cpl].inventory[i] = oc;
                        x = 1;
                        break;
                    }
                }
                if (!x) {
                    clw();
                    tx("You can't carry more.\r\n");
                    Delay(40);
                } else {
                    tx("\r\n");
                    tx("Ok.\r\n");
                    Delay(40);
                }
            }
            break;

        case 6:
            if (p[cpl].ma[vem] < (35 + p[cpl].lv[vem])) {
                tx("Not enough spell pts!\r\n");
                Delay(50);
                clw();
                break;
            }
            p[cpl].ma[vem] -= (35 + p[cpl].lv[vem]);
            clw();
            tx("%s carefully\r\n", p[cpl].name[vem]);
            tx("prepares the ritual of\r\n");
            tx("Teleportation. %s spends\r\n", sextext(vem, 1));
            tx("an hour chanting mantras\r\n");
            tx("and burning blue candles.\r\n\r\n");
            tid++;
            if (tid == 25)
                tid = 1;
            ryl_pause();
            clw();
            tx("%s picks up a stone slab\r\n", sextext(vem, 1));
            tx("from his satchel and\r\n");
            tx("uses a special instrument\r\n");
            tx("to scratch the direction\r\n");
            tx("%s wants you to teleport\r\n", sextext(vem, 1));
            tx("to.\r\n\r\n");
            ryl_pause();
            clw();
            tx("Teleport :\r\n\r\n");
            tx("   8\r\n");
            tx(" 4-+-6\r\n");
            tx("   2\r\n\r\n");
            while (1) {
                d = key();
                if (d == '8' || d == '4' || d == '6' || d == '2')
                    break;
            }
            clw();
            tx("How many miles do you\r\n");
            tx("want to travel? (1-9)\r\n");
            while (1) {
                b = key();
                if (b >= '1' && b <= '9')
                    break;
            }
            switch (d) {
                case '2':
                    for (i = 1; i < ((b - 48) * 2) + 1; i++)
                        if (map[p[cpl].y + 1][p[cpl].x] != 'm')
                            p[cpl].y++;
                    break;
                case '4':
                    for (i = 1; i < ((b - 48) * 2) + 1; i++)
                        if (map[p[cpl].y][p[cpl].x - 1] != 'm')
                            p[cpl].x--;
                    break;
                case '6':
                    for (i = 1; i < ((b - 48) * 2) + 1; i++)
                        if (map[p[cpl].y][p[cpl].x + 1] != 'm')
                            p[cpl].x++;
                    break;
                case '8':
                    for (i = 1; i < ((b - 48) * 2) + 1; i++)
                        if (map[p[cpl].y - 1][p[cpl].x] != 'm')
                            p[cpl].y--;
                    break;
            }
            tx("You appear to be\r\n");
            tx("somewhere else.\r\n");
            update_map();
            Delay(60);
            clw();
            break;

        case 7:  // EARTHQUAKE //
            clw();
            if (place == 1) {
                tx("The roof would cave\r\n");
                tx("in on you!\r\n\r\n");
                ryl_pause();
                clw();
                break;
            }
            if (p[cpl].ma[vem] < (55 + p[cpl].lv[vem])) {
                tx("Not enough spell pts!\r\n");
                Delay(50);
                clw();
                break;
            }
            p[cpl].ma[vem] -= (55 + p[cpl].lv[vem]);
            tx("%s utters\r\n", p[cpl].name[vem]);
            tx("three syllabels in an\r\n");
            tx("archaic tongue...\r\n\r\n");
            Delay(100);
            tx("and the ground cracks\r\n");
            tx("open!\r\n");
            Delay(150);
            clr_map_window();
            blitgfx(cavepic);
            clw();
            tx("You find a cave!\r\n\r\n");
            tx("(E)nter\r\n");
            tx("(L)eave\r\n");
            while (1) {
                c = key();
                c = toupper(c);
                if (c == 'L' || c == 'E')
                    break;
            }
            time(&t);
            srand((unsigned int) t);
            if (c == 'E') {
                if (p[cpl].tid_kvar > 10)
                    p[cpl].tid_kvar -= 10;
                else {
                    clw();
                    tx("You don't have enough\r\n");
                    tx("moves left to explore\r\n");
                    tx("the cave...\r\n\r\n");
                    ryl_pause();
                    clr_map_window();
                    clw();
                    break;
                }
                clr_map_window();
                dungeon(((rand() % 14) + 5), 0);
                break;
            } else {
                clw();
                clr_map_window();
                update_map();
                break;
            }
            break;
    }
}

void scorelist() {
    FILE *fh;
    char str[144];
    uint8_t i, j, xx, o, n, q;
    uint32_t pts[26], lvl[26], temp_ulong, kills[26];
    char pname[26][30], lname[26][30], temp_string[30];
    uint8_t cls[26][5], item[26][6], pnr[26], temp_ubyte[26];

    for (i = 1; i < antal_spelare + 1; i++) {
        pts[i] = (p[i].exp[1] + p[i].exp[2] + p[i].exp[3] + p[i].exp[4]) / 4;
        lvl[i] = (p[i].lv[1] + p[i].lv[2] + p[i].lv[3] + p[i].lv[4]) / 4;
        kills[i] = p[i].kills;
        strcpy(pname[i], p[i].partyname);
        strcpy(lname[i], p[i].name[1]);
        item[i][1] = p[i].fireshield;
        item[i][2] = p[i].bracelets;
        item[i][3] = p[i].mastercrown;
        item[i][4] = p[i].silverkey;
        item[i][5] = p[i].jazzyfoot;
        for (j = 1; j < 5; j++)
            cls[i][j] = p[i].cl[j];
        pnr[i] = i;
    }

    // SORT!

    n = antal_spelare - 1;
    o = antal_spelare - 1;

    for (i = 1; i < n + 1; i++) {
        for (j = 1; j < o + 1; j++) {
            q = j + 1;
            if (pts[q] > pts[j]) {
                // SWAP NUMBER
                temp_ubyte[0] = pnr[j];
                pnr[j] = pnr[q];
                pnr[q] = temp_ubyte[0];
                // SWAP ITEMS
                for (xx = 1; xx < 6; xx++) {
                    temp_ubyte[xx] = item[j][xx];
                    item[j][xx] = item[q][xx];
                    item[q][xx] = temp_ubyte[xx];
                }
                // SWAP KILLS
                temp_ulong = kills[j];
                kills[j] = kills[q];
                kills[q] = temp_ulong;
                // SWAP PARTYNAME
                strcpy(temp_string, pname[j]);
                strcpy(pname[j], pname[q]);
                strcpy(pname[q], temp_string);
                // SWAP LEADERNAME
                strcpy(temp_string, lname[j]);
                strcpy(lname[j], lname[q]);
                strcpy(lname[q], temp_string);
                // SWAP CLASSES
                for (xx = 1; xx < 5; xx++) {
                    temp_ubyte[xx] = cls[j][xx];
                    cls[j][xx] = cls[q][xx];
                    cls[q][xx] = temp_ubyte[xx];
                }
                // SWAP EXPERIENCE
                temp_ulong = pts[j];
                pts[j] = pts[q];
                pts[q] = temp_ulong;
                // SWAP LEVEL
                temp_ulong = lvl[j];
                lvl[j] = lvl[q];
                lvl[q] = temp_ulong;
            }
        }
    }

    if (!(fh = fopen("data/vscore.txt", "w")))
        return;

    sprintf(str, "%c\n", 12);
    fputs(str, fh);
    fputs(" +============================================================================+\n", fh);
    fputs(" I              ADVENTURERS OF RYLWEEN - PARTY STATUS AND SCORES              I\n", fh);
    fputs(" +============================================================================+\n\n",
          fh);

    for (i = 1; i < antal_spelare + 1; i++) {
        sprintf(str, "%2d. %-30s\n", i, pname[i]);
        fputs(str, fh);
        fputs(" +----------------------------------------------------------------------------+\n",
              fh);
        sprintf(str, "  Leader             : %s\n  Classes            : ", lname[i]);
        for (j = 1; j < 5; j++) {
            switch (cls[i][j]) {
                case 1:
                    strcat(str, "Fighter");
                    break;
                case 2:
                    strcat(str, "Thief");
                    break;
                case 3:
                    strcat(str, "Mage");
                    break;
                case 4:
                    strcat(str, "Priest");
                    break;
                case 5:
                    strcat(str, "Bard");
                    break;
                case 6:
                    strcat(str, "Ranger");
                    break;
            }
            if (j < 4)
                strcat(str, ", ");
        }
        strcat(str, "\n");
        fputs(str, fh);
        sprintf(str, "  Confirmed Kills    : %-4d\n", kills[i]);
        sprintf(str, "  Average Experience : %-6d (level %-3d)\n\n", pts[i], lvl[i]);
        fputs(str, fh);
        fputs("  Power Items        :\n\n", fh);
        xx = 0;
        for (j = 1; j < 6; j++)
            if (item[i][j])
                xx = 1;
        if (item[i][1])
            fputs("  Fire Shield\n", fh);
        if (item[i][2])
            fputs("  Bracelets of Thun\n", fh);
        if (item[i][3])
            fputs("  Master Crown\n", fh);
        if (item[i][4])
            fputs("  Silver Key\n", fh);
        if (item[i][5])
            fputs("  Jazzy's Foot\n", fh);
        if (!xx)
            fputs("  None.\n", fh);
        fputs(" +----------------------------------------------------------------------------+\n\n",
              fh);
    }
    fclose(fh);

    if (!(fh = fopen("data/score.txt", "w")))
        return;

    sprintf(str, "%c\n", 12);
    fputs(str, fh);
    fputs(" +============================================================================+\n", fh);
    fputs(" I              ADVENTURERS OF RYLWEEN - PARTY STATUS AND SCORES              I\n", fh);
    fputs(" +============================================================================+\n\n",
          fh);
    fputs("         #     Party Name                    Experience       Level\n\n", fh);

    for (i = 1; i < antal_spelare + 1; i++) {
        sprintf(str, "        %2d.    %-30s  %6d          %3d\n", i, pname[i], pts[i], lvl[i]);
        fputs(str, fh);
    }
    fputs("\n", fh);
    fclose(fh);
}
