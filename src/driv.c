#include "ryl.h"

extern void attackhandler();
extern void timehandler();
extern void statusline();

void spelet() {
    char cc, d;
    int32_t i;

    clw();
    tid = 5;
    hours_pigg = 14;

    tx(" ADVENTURERS of RYLWEEN\r\n\r\n");

    tx("Programming & Design by:\r\n");
    tx("    Marcus Lagergren\r\n\r\n");

    tx("      Created by:\r\n");
    tx("    Niklas Blomqvist\r\n\r\n");

    tx("      Graphics by:\r\n");
    tx("     Matias Larsson   v3\r\n");

    /* Prep map for positions of huts/cottages */

    for (i = 1; i < 40; i++) {
        if (!npc[i].towne) {
            map[npc[i].y][npc[i].x] = '1';
        }
    }

    update_map();

    while (1) {
        timehandler();
        foodhandler();
        attackhandler();
        statusline();

        if (!p[cpl].tid_kvar) {
            clw();
            tx("You are out of moves\r\n");
            tx("for today.\r\n\r\n");
            tx("(S)ave and Quit\r\n");
            tx("(Q)uit (no save)\r\n");
            while (1) {
                cc = key();
                cc = toupper(cc);
                if (cc == 'S' || cc == 'Q') {
                    break;
                }
            }
            if (cc == 'S') {
                clw();
                tx("Saving position...\r\n");
                save();
                Delay(20);
                tx("Done.\r\n");
                Delay(20);
            }
            clw();
            tx("Fare thee well.\r\n");
            Delay(50);
            scorelist();
            cleanexit();
        }

        cc = key();
        cc = toupper(cc);

        switch (cc) {
            case ' ':
                clw();
                tx("Pass...\r\n");
                break;
            case '8':
                if (!p[cpl].tid_kvar) {
                    clw();
                    tx("No moves left!\r\n");
                    break;
                }
                if (map[p[cpl].y - 1][p[cpl].x] != 'm' && map[p[cpl].y - 1][p[cpl].x] != 'w') {
                    last_terrain = map[p[cpl].y][p[cpl].x];
                    p[cpl].y--;
                    update_map();
                    slumpvis();
                    p[cpl].tid_kvar--;
                }
                break;
            case '4':
                if (!p[cpl].tid_kvar) {
                    clw();
                    tx("No moves left!\r\n");
                    break;
                }
                if (map[p[cpl].y][p[cpl].x - 1] != 'm' && map[p[cpl].y][p[cpl].x - 1] != 'w') {
                    last_terrain = map[p[cpl].y][p[cpl].x];
                    p[cpl].x--;
                    update_map();
                    slumpvis();
                    p[cpl].tid_kvar--;
                }
                break;
            case '2':
                if (!p[cpl].tid_kvar) {
                    clw();
                    tx("No moves left!\r\n");
                    break;
                }
                if (map[p[cpl].y + 1][p[cpl].x] != 'm' && map[p[cpl].y + 1][p[cpl].x] != 'w') {
                    last_terrain = map[p[cpl].y][p[cpl].x];
                    p[cpl].y++;
                    update_map();
                    slumpvis();
                    p[cpl].tid_kvar--;
                }
                break;
            case '6':
                if (!p[cpl].tid_kvar) {
                    clw();
                    tx("No moves left!\r\n");
                    break;
                }
                if (map[p[cpl].y][p[cpl].x + 1] != 'm' && map[p[cpl].y][p[cpl].x + 1] != 'w') {
                    last_terrain = map[p[cpl].y][p[cpl].x];
                    p[cpl].x++;
                    update_map();
                    slumpvis();
                    p[cpl].tid_kvar--;
                }
                break;

            case 'S':
                clw();
                tx("Saving position...\r\n");
                save();
                Delay(20);
                tx("Done.\r\n");
                break;
            case 'W':
                weaponr();
                break;
            case 'R':
                armourr();
                break;
            case '!':
                cl();
                disptext("data/ansi/screen.ans");
                update_map();
                clw();
                break;
            case 'J':
                cl();
                c(0, 0);
                read_journal();
                cl();
                c(49, 6);
                clw();
                disptext("data/ansi/screen.ans");
                update_map();
                clw();
                break;
            case '?':
                help();
                break;
            case 'H':
                hunt();
                break;
            case 'V':
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
                    for (i = 1; i < 5; i++) {
                        show(i);
                    }
                }
                clw();
                break;
            case 'T':
                armouroff();
                break;
            case 'P':
                weaponoff();
                break;
            case 'E':
                enter();
                break;
            case 'L':
                drop();
                break;
            case 'C':
                camp();
                break;
            case 'Z':
                spell(0);
                break;
            case 'Q':
                quaff();
                break;
            case 'X':
                clw();
                tx("Do you really want\r\n");
                tx("to quit?\r\n\r\n");
                tx("(Y)es\r\n");
                tx("(N)o\r\n");
                d = key();
                d = toupper(d);
                if (d != 'Y') {
                    clw();
                    break;
                }
                scorelist();
                cleanexit();
        }
    }
}

uint8_t drop_option() {
    char c;

    clw();
    tx("Do you want to drop\r\n");
    tx("another object? (y/N)\r\n");
    c = key();
    c = toupper(c);
    if (c == 'Y') {
        clw();
        return drop();
    } else {
        clw();
        return 0;
    }
}

void read_journal() {
    FILE *j;
    char str[90], str2[88], plrstr[6];
    int32_t plr = 0;
    unsigned char line = 0;

    if (!(j = fopen("data/journal", "r"))) {
        return;
    }

    tx("\r\n============================= TRAVELLER'S JOURNAL ==============================\r\n\r\n");

    while (1) {
        str[0] = 0;
        fgets((char *) str, sizeof(str), j);
        if (str[0] == 0) {
            ryl_pause();
            break;
        }
        sscanf(str, "%s %s", plrstr, str2);
        plr = atoi(plrstr);

        if (plr == cpl) {
            str[0] = '-';
            if (plr > 9) {
                str[1] = '-';
            }
            tx("%s\r\n", str);
            line++;
            if (line == 16) {
                line = 0;
                tx("\r\n");
                ryl_pause();
                cl();
            }
        }
    }
    fclose(j);
}
void statusline() {
    /* Redo status line */

    char str[256];
    char stline[256];

    if (p[cpl].food < 0) {
        p[cpl].food = 0;
    }
    stline[0] = 0;
    if (tid >= 6 && tid < 12) {
        snprintf(stline, sizeof stline, "TIME: [33mDAWN[32m      ");
    }
    if (tid >= 12 && tid < 18) {
        snprintf(stline, sizeof stline, "TIME: [33mMIDDAY[32m    ");
    }
    if (tid >= 18 && tid < 24) {
        snprintf(stline, sizeof stline, "TIME: [33mTWILIGHT[32m  ");
    }
    if (tid == 24 || tid < 6) {
        snprintf(stline, sizeof stline, "TIME: [33mMIDNIGHT[32m  ");
    }
    if (p[cpl].food < p[cpl].pl * 3 && p[cpl].food) {
        str_append(stline, sizeof stline, "ST: [35mHUNGRY  ");
    }
    if (!p[cpl].food) {
        str_append(stline, sizeof stline, "ST: [31mSTARVING[32m ");
    }
    if (p[cpl].food && p[cpl].food >= p[cpl].pl * 3) {
        str_append(stline, sizeof stline, "ST: [33mOK    ");
    }
    snprintf(str, sizeof str, "[32m X: [33m%2d   [32mY: [33m%2d[32m     ", p[cpl].x, p[cpl].y);
    str_append(stline, sizeof stline, str);
    snprintf(str, sizeof str, "FD: [33m%4d[32m  ", p[cpl].food);
    str_append(stline, sizeof stline, str);
    snprintf(str, sizeof str, "MV: [33m%3d[32m  ", p[cpl].tid_kvar);
    str_append(stline, sizeof stline, str);
    snprintf(str, sizeof str, "AU: [33m%6d[32m", p[cpl].gold);
    str_append(stline, sizeof stline, str);
    c(3, 21);
    tx("[32m%s    ", stline);
    snprintf(stline, sizeof stline, "H1: [33m%3d/%3d   [32mH2: [33m%3d/%3d   [32mH3: [33m%3d/%3d   [32mH4: [33m%3d/%3d[37m",
             p[cpl].hp[1], p[cpl].h[1], p[cpl].hp[2], p[cpl].h[2], p[cpl].hp[3], p[cpl].h[3],
             p[cpl].hp[4], p[cpl].h[4]);
    c(13, 22);
    tx("%s\r\n", stline);
    c(49, 6);
}
void position_locations() {
    time_t t;
    int32_t i;

    for (i = 0; i < 15; i++) {
        dx[i] = 0;
        dy[i] = 0;
    }

    time(&t);
    srand((unsigned int) t);

    for (i = 1; i < 10; i++) {
        while (1) {
            dx[i] = (rand() % 70) + 5;
            dy[i] = (rand() % 30) + 5;
            if (map[dy[i]][dx[i]] != 'w' && map[dy[i]][dx[i]] != 'm' && map[dy[i]][dx[i]] != '0' &&
                map[dy[i]][dx[i]] != '1' && map[dy[i]][dx[i]] != '2') {
                break;
            }
        }
    }
}

void init() {
    char str[90], kl[8], d, ch;
    FILE *fh;
    uint8_t abort = 0, x = 0, i, j = 0;

    tx("[30m%c%c%c%c%c[37m", 27, 91, 48, 32, 112);

    if (!(fh = fopen("data/map.dat", "rb"))) {
        c(49, 6);
        clw();
        tx("Bloody 'ell! Unable to find map!!!\r\n");
        ryl_pause();
        cleanexit();
    }
    for (i = 1; i < 41; i++) {
        fgets((char *) map[i], sizeof(map[i]), fh);
    }
    fclose(fh);

    if (!(fh = fopen("data/realm.dat", "rb"))) {
        antal_spelare = 0;
        senaste_dagen = 0;
        position_locations();

        if (!(fh = fopen("data/realm.dat", "wb"))) {
            cleanexit();
        }

        fwrite((char *) &antal_spelare, sizeof(antal_spelare), 1, fh);
        fwrite((char *) &senaste_dagen, sizeof(senaste_dagen), 1, fh);
        for (i = 1; i < 15; i++) {
            fwrite((char *) &dx[i], sizeof(dx[i]), 1, fh);
        }
        for (i = 1; i < 15; i++) {
            fwrite((char *) &dy[i], sizeof(dy[i]), 1, fh);
        }

        fclose(fh);

        place_npcs();
    }
    if (fh) {
        fclose(fh);
    }

    cl();
    disptext("data/ansi/title.ans");
    tx("\r\n");
    tx("[37m<MORE>\r\n");
    key();

    reg = 1;

    load();
    getclk(kl);
    if (kl[3] != senaste_dagen) {
        cl();
        tx("First Game of the Day... Updating... Please Wait...");

        // Clear entry file

        fh = fopen("data/entry.dat", "wb");
        fclose(fh);

        for (i = 1; i < antal_spelare + 1; i++) {
            tx(".");
            if (!reg) {
                p[i].tid_kvar = 75;
            }
            if (reg == 1) {
                p[i].tid_kvar = 175;
            }
            Delay(2);
        }
        getclk(kl);
        day_at_game_start = kl[3];
        save();
    }
    getclk(kl);
    day_at_game_start = kl[3];

    cl();
    disptext("data/ansi/screen.ans");

    while (!abort) {
        clw();

        tx("ADVENTURERS of RYLWEEN\r\n\r\n");

        /* ============================= REG FILE ============================ */

        switch (reg) {
            case 0:
                tx("   [31mUNREGISTERED[37m copy!\r\n\r\n");
                break;
            case 1:
                tx("%s's copy.\r\n\r\n", global_name);
                break;
            case 2:
                cl();
                c(1, 1);
                tx("[31m!CORRUPTED REGISTRATION FILE![37m RYLWEEN WILL REFUSE TO RUN UNTIL IT HAS BEEN\r\n");
                tx("REMOVED. YOU'LL NEVER GUESS HOW IT'S DONE, SO QUIT FOOLING AROUND OR I'LL\r\n");
                tx("STRANGLE YOUR PETS! - YOU IMMORAL LITTLE BASTARD, YOU! EAT $#!T!!!!!!!!!!\r\n");
                while (1)
                    ;
        }

        /* =================================================================== */

        for (i = 1, x = 0; i < antal_spelare + 1; i++) {
            if (!stricmp(global_name, p[i].player)) {
                x = i;
            }
        }
        for (i = 1; i < antal_spelare + 1; i++) {
            if (p[i].silverkey == 2) {
                tx("The struggle is over.\r\n");
                tx("[31m%s[37m have\r\n", p[i].partyname);
                tx("defeated Baron Ydarg.\r\n");
                tx("Please ask the SysOp\r\n");
                tx("to reset Rylween\r\n\r\n");
                ryl_pause();
                clw();
                cleanexit();
            }
        }
        if (!x) {
            tx("N. Create New Party\r\n");
        }
        if (x) {
            tx("C. Continue Saved Game\r\n");
        }
        if (x) {
            tx("T. Terminate Party\r\n");
        }
        tx("S. View Currect Scores\r\n");
        tx("H. View Hall of Fame\r\n");
        tx("D. View Documentation\r\n");
        tx("X. Exit\r\n\r\n");

        ch = '\0';

        while (toupper(ch) != 'N' && toupper(ch) != 'C' && toupper(ch) != 'X' &&
               toupper(ch) != 'T' && toupper(ch) != 'S' && toupper(ch) != 'H' &&
               toupper(ch) != 'D') {
            ch = key();
            ch = toupper(ch);
        }

        switch (toupper(ch)) {
            case 'S':
                cl();
                disptext("data/score.txt");
                tx("\r\n");
                tx("[37m<MORE>\r\n");
                key();
                cl();
                c(49, 6);
                clw();
                disptext("data/ansi/screen.ans");
                clw();
                tx("Do you want to see the\r\n");
                tx("verbose listing? (y/N)\r\n");
                d = key();
                d = toupper(d);
                if (d == 'Y') {
                    cl();
                    disptext("data/vscore.txt");
                    tx("\r\n");
                    tx("[37m<MORE>\r\n");
                    key();
                    cl();
                    c(49, 6);
                    clw();
                    disptext("data/ansi/screen.ans");
                }
                break;

            case 'D':
                clw();
                tx("You may want to\r\n");
                tx("turn on your capture\r\n");
                tx("buffer now...\r\n\r\n");
                ryl_pause();
                cl();
                disptext("data/rylween.doc");
                tx("\r\n");
                tx("[37m<MORE>\r\n");
                key();
                cl();
                c(49, 6);
                clw();
                disptext("data/ansi/screen.ans");
                break;

            case 'H':
                cl();
                disptext("data/heroes.txt");
                tx("\r\n");
                tx("[37m<MORE>\r\n");
                key();
                cl();
                c(49, 6);
                clw();
                disptext("data/ansi/screen.ans");
                break;

            case 'N':
                if (!x) {
                    clw();
                    tx("Please read the manual\r\n");
                    tx("before starting play.\r\n");
                    tx("You will understand\r\n");
                    tx("_very_ little of the\r\n");
                    tx("game otherwise...\r\n\r\n");
                    ryl_pause();
                    clw();
                    createparty();
                } else {
                    cleanexit();
                }
                break;
            case 'C':
                if (x) {
                    // Check ENTRY.DAT for 2-4 present names. Add one more
                    // if less.

                    fh = fopen("data/entry.dat", "rb");
                    i = 0;
                    while (1) {
                        str[0] = 0;
                        fread((char *) str, sizeof(str), 1, fh);
                        if (str[0] == 0) {
                            break;
                        }
                        if (!stricmp(str, global_name)) {
                            i++;
                        }
                    }
                    fclose(fh);
                    if ((reg == 1 && i >= 4) || (reg == 0 && i >= 2)) {
                        if (reg == 1) {
                            j = 4;
                        } else {
                            j = 2;
                        }
                        clw();
                        tx("Sorry! You can't enter\r\n");
                        tx("the realm again today.\r\n");
                        tx("You only have %d turns\r\n", j);
                        tx("per day to use your\r\n");
                        tx("moves on.\r\n\r\n");
                        ryl_pause();
                        cleanexit();
                    }

                    // Append name to file

                    fh = fopen("data/entry.dat", "ab");
                    strcpy(str, global_name);
                    fwrite((char *) str, sizeof(str), 1, fh);
                    fclose(fh);

                    // Checking done. Load

                    load();
                    abort = 1;
                    snprintf(str, sizeof str, "%s and %s party '%s' entered the realm.", p[cpl].name[1],
                             sextext(1, 3), p[cpl].partyname);
                    ryl_log(str);
                    last_terrain = 0;
                } else {
                    clw();
                    tx("No game available.\r\n");
                    Delay(75);
                    cleanexit();
                }
                break;
            case 'X':
                cleanexit();
                break;
            case 'T':
                if (x) {
                    clw();
                    tx("Really? C'mon now!\r\n");
                    tx("It can't be that bad!\r\n");
                    tx("Your attitude sucks,\r\n");
                    tx("my friend!\r\n\r\n");
                    tx("Kill Party? (y/N)\r\n");
                    d = key();
                    d = toupper(d);
                    if (d == 'Y') {
                        clw();
                        tx("Editing REALM.DAT...");
                        if (!(fh = fopen("data/realm.dat", "wb"))) {
                            cleanexit();
                        }
                        antal_spelare--;
                        fwrite((char *) &antal_spelare, sizeof(antal_spelare), 1, fh);
                        fwrite((char *) &senaste_dagen, sizeof(senaste_dagen), 1, fh);
                        for (i = 1; i < 15; i++) {
                            fwrite((char *) &dx[i], sizeof(dx[i]), 1, fh);
                        }
                        for (i = 1; i < 15; i++) {
                            fwrite((char *) &dy[i], sizeof(dy[i]), 1, fh);
                        }
                        for (i = 1; i < antal_spelare + 2; i++) {
                            if (i != x) {
                                fwrite((char *) &p[i], sizeof(p[i]), 1, fh);
                            }
                        }
                        fclose(fh);
                        Delay(50);

                        /* Fixa journalen... */

                        clw();
                        tx("Editing Journal...");
                        clrjr(x);
                        Delay(50);

                        /* Clean NPC dat */

                        clw();
                        tx("Editing NPC.DAT...");
                        if (!(fh = fopen("data/npc.dat", "wb"))) {
                            cleanexit();
                        }
                        // Om I > x (ditt party) och i for:as till antal_spelare (ej +1)
                        // npc[i].visited[x] = npc[i].visited[x+1];
                        for (i = 1; i < 40; i++) {
                            for (j = 1; j < antal_spelare; j++) {
                                npc[i].visited[x] = npc[i].visited[x + 1];
                            }
                            fwrite((char *) &npc[i], sizeof(npc[i]), 1, fh);
                        }
                        fclose(fh);
                        Delay(50);

                        clw();
                        tx("Your party has been\r\n");
                        tx("deleted. Please play\r\n");
                        tx("again.\r\n\r\n");
                        ryl_pause();
                        cleanexit();
                    }
                }
                break;
        }
    }
    abort = 0;
    spelet();
}

void save() {
    FILE *fh;
    int32_t i;
    char kl[8];

    getclk(kl);

    //senaste_dagen = day_at_game_start;
    senaste_dagen = kl[3];

    if (!(fh = fopen("data/realm.dat", "wb"))) {
        c(49, 6);
        clw();
        tx("\r\nUnable to save data!!!\r\n");
        ryl_pause();
        cleanexit();
    }

    fwrite((char *) &antal_spelare, sizeof(antal_spelare), 1, fh);
    fwrite((char *) &senaste_dagen, sizeof(senaste_dagen), 1, fh);
    for (i = 1; i < 15; i++) {
        fwrite((char *) &dx[i], sizeof(dx[i]), 1, fh);
    }
    for (i = 1; i < 15; i++) {
        fwrite((char *) &dy[i], sizeof(dy[i]), 1, fh);
    }

    if (antal_spelare) {
        for (i = 1; i < antal_spelare + 1; i++) {
            fwrite((char *) &p[i], sizeof(p[i]), 1, fh);
        }
    }
    fclose(fh);

    if (!(fh = fopen("data/npc.dat", "wb"))) {
        c(49, 6);
        clw();
        tx("\r\nUnable to save NPCs!!!\r\n");
        ryl_pause();
        cleanexit();
    }
    for (i = 1; i < 40; i++) {
        fwrite((char *) &npc[i], sizeof(npc[i]), 1, fh);
    }
    fclose(fh);
}
void load() {
    FILE *fh;
    int32_t i;

    if (!(fh = fopen("data/realm.dat", "rb"))) {
        c(49, 6);
        clw();
        tx("Unable to load data!!!\r\n");
        ryl_pause();
        cleanexit();
    }

    fread((char *) &antal_spelare, sizeof(antal_spelare), 1, fh);
    fread((char *) &senaste_dagen, sizeof(senaste_dagen), 1, fh);
    for (i = 1; i < 15; i++) {
        fread((char *) &dx[i], sizeof(dx[i]), 1, fh);
    }
    for (i = 1; i < 15; i++) {
        fread((char *) &dy[i], sizeof(dy[i]), 1, fh);
    }

    if (antal_spelare) {
        for (i = 1; i < antal_spelare + 1; i++) {
            fread((char *) &p[i], sizeof(p[i]), 1, fh);
        }
    }
    fclose(fh);
    for (i = 1; i < antal_spelare + 1; i++) {
        if (!stricmp(global_name, p[i].player)) {
            cpl = i;
        }
    }

    if (!(fh = fopen("data/npc.dat", "rb"))) {
        c(49, 6);
        clw();
        tx("\r\nUnable to load NPCs!!!\r\n");
        ryl_pause();
        cleanexit();
    }
    for (i = 1; i < 40; i++) {
        fread((char *) &npc[i], sizeof(npc[i]), 1, fh);
    }
    fclose(fh);

    if (!(fh = fopen("data/map.dat", "rb"))) {
        c(49, 6);
        clw();
        tx("Unable to find map!!!\r\n");
        ryl_pause();
        cleanexit();
    }

    for (i = 1; i < 41; i++) {
        fgets((char *) map[i], sizeof(map[i]), fh);
    }
    fclose(fh);
}

char *sextext(uint8_t nr, uint8_t xx) {
    switch (p[cpl].sex[nr]) {
        case 1:
            if (xx == 1) {
                return "he";
            }
            if (xx == 2) {
                return "him";
            }
            if (xx == 3) {
                return "his";
            }
            break;
        case 2:
            if (xx == 1) {
                return "she";
            }
            if (xx > 1) {
                return "her";
            }
            break;
    }
    return "";
}

void createparty() {
    int32_t i, a, g, passed;
    time_t t;
    int32_t tries = 0;
    char str[80];

    clw();

    tx("Party Creation\r\n\r\n");

    if (antal_spelare == 25) {
        tx("Sorry. Maximum number of\r\n");
        tx("players reached. Play\r\n");
        tx("again later on.\r\n\r\n");
        ryl_pause();
        cleanexit();
    }

    antal_spelare++;
    a = antal_spelare;
    cpl = a;

    while (1) {
        tx("What is the name of your\r\n");
        tx("party?\r\n\r\n");
        tx(": ");

        strcpy(p[a].partyname, getstr(18));
        if (!p[a].partyname[0]) {
            antal_spelare--;
            cpl = 0;
            return;
        } else {
            break;
        }
    }

    g = 1;

    while (g < 5) {
        clw();
        time(&t);
        srand((unsigned int) t + tries);
        tries++;
        tx("Stats for char: %d\r\n\r\n", g);
        p[a].ws[g] = (rand() % 56) + 15;
        p[a].wi[g] = (rand() % 56) + 15;
        p[a].h[g] = (rand() % 56) + 15;
        p[a].hp[g] = p[a].h[g];
        p[a].ch[g] = (rand() % 56) + 15;
        tx("Weapon Skill : %2d\r\n", p[a].ws[g]);
        tx("Health       : %2d\r\n", p[a].h[g]);
        tx("Wisdom       : %2d\r\n", p[a].wi[g]);
        tx("Charisma     : %2d\r\n\r\n", p[a].ch[g]);
        tx("Reroll (y/N) ");
        if (!hotkey()) {
            while (1) {
                clw();
                tx("Give Character %d a name!\r\n", g);
                tx(": ");
                str[0] = 0;
                strcpy(p[a].name[g], getstr(12));
                if (!p[a].name[g][0]) {
                    antal_spelare--;
                    cpl = 0;
                    return;
                } else {
                    break;
                }
            }
            p[a].name[g][0] = toupper(p[a].name[g][0]);

            clw();
            tx("What sex is\r\n");
            tx("%s?\r\n\r\n", p[a].name[g]);
            tx("1. Male\r\n");
            tx("2. Female\r\n");
            str[0] = 0;
            while (str[0] < '1' || str[0] > '2') {
                tx(": ");
                strcpy(str, getstr(2));
            }
            p[a].sex[g] = str[0] - 48;

            clw();
            tx("What race is\r\n");
            tx("%s?\r\n\r\n", p[a].name[g]);
            tx("1. Human  2. Dwarf\r\n");
            tx("3. Elf    4. Halfling\r\n");
            str[0] = 0;
            while (str[0] < '1' || str[0] > '4') {
                tx(": ");
                strcpy(str, getstr(2));
            }
            p[a].ra[g] = str[0] - 48;

            clw();
            tx("Pick a class for this\r\n");
            tx("character...\r\n\r\n");
            tx("1. Fighter 2. Thief\r\n");
            tx("3. Mage    4. Priest\r\n");
            tx("5. Bard    6. Ranger\r\n");
            str[0] = 0;
            while (str[0] < '1' || str[0] > '6') {
                tx(": ");
                strcpy(str, getstr(2));
            }
            p[a].cl[g] = str[0] - 48;
            clw();
            passed = 0;
            switch (p[a].cl[g]) {
                case 1:
                    if (p[a].ws[g] < 50) {
                        tx("Weap. skill must be > 50.\r\n");
                        Delay(50);
                    } else {
                        passed = 1;
                    }
                    break;
                case 2:
                    passed = 1;
                    break;
                case 3:
                    if (p[a].wi[g] < 50) {
                        tx("Wisdom must be > 50.\r\n");
                        Delay(50);
                    } else {
                        passed = 1;
                    }
                    break;
                case 4:
                    if (p[a].wi[g] < 50) {
                        tx("Wisdom must be > 50.\r\n");
                        Delay(50);
                    } else {
                        passed = 1;
                    }
                    break;
                case 5:
                    if (p[a].ch[g] < 50) {
                        tx("Charisma must be > 50.\r\n");
                        Delay(50);
                    } else {
                        passed = 1;
                    }
                    break;
                case 6:
                    passed = 1;
                    break;
            }
            if (passed) {
                passed = 0;
                if (p[a].cl[g] == 3 || p[a].cl[g] == 4) {
                    p[a].ma[g] = p[a].wi[g];
                } else {
                    p[a].ma[g] = 0;
                }
                show(g);
                g++;
            }
        }
    }
    p[a].x = 12;
    p[a].y = 9;
    p[a].gold = 1000;
    p[a].torches = 5;
    p[a].food = 100;
    if (reg == 1) {
        p[a].tid_kvar = 200;
    } else {
        p[a].tid_kvar = 100;
    }
    p[a].pl = 4;
    p[a].fireshield = 0;
    p[a].bracelets = 0;
    p[a].mastercrown = 0;
    p[a].jazzyfoot = 0;
    p[a].silverkey = 0;
    for (i = 0; i < 17; i++) {
        p[a].inventory[i] = 0;
    }
    for (i = 0; i < 40; i++) {
        p[a].flag[i] = 0;
    }
    for (i = 1; i < 5; i++) {
        p[a].lv[i] = 1;
    }
    for (i = 1; i < 5; i++) {
        p[a].ar[i] = 0;
    }
    for (i = 1; i < 5; i++) {
        p[a].lv[i] = 1;
    }
    for (i = 1; i < 5; i++) {
        p[a].exp[i] = 0;
    }
    for (i = 1; i < 5; i++) {
        p[a].dead[i] = 0;
    }
    strcpy(p[a].player, global_name);
    clw();
    tx("Saving...\r\n");
    save();
    Delay(50);
}

void showparty() {
    clw();
    tx("%u torches.\r\n", p[cpl].torches);
    tx("%d rations.\r\n", p[cpl].food);
    tx("%d gold.\r\n\r\n", p[cpl].gold);
    if (p[cpl].fireshield) {
        tx("You have the Shield.\r\n");
    }
    if (p[cpl].bracelets) {
        tx("You have the Bracelets.\r\n");
    }
    if (p[cpl].mastercrown) {
        tx("You have the Crown.\r\n");
    }
    if (p[cpl].jazzyfoot) {
        tx("You have the Foot.\r\n");
    }
    if (p[cpl].silverkey) {
        tx("You have the Key.\r\n");
    }

    ryl_pause();
    clw();
    invlist();
    tx("\r\n");
    ryl_pause();
}

void invlist() {
    char item[20][80];
    int32_t i;

    for (i = 1; i < 17; i++) {
        strncpy(item[i], object(p[cpl].inventory[i]), 9);
        item[i][9] = 0;
    }

    for (i = 1; i < 16; i += 2) {
        tx("%c. %-9s %c. %-9s\r\n", 64 + i, item[i], 65 + i, item[i + 1]);
    }
}

void show(int32_t nr) {
    char race[5][15];
    char clss[7][15];
    char str[10];

    strcpy(race[1], "Human");
    strcpy(race[2], "Dwarf");
    strcpy(race[3], "Elf");
    strcpy(race[4], "Halfling");
    strcpy(clss[1], "Fighter");
    strcpy(clss[2], "Thief");
    strcpy(clss[3], "Mage");
    strcpy(clss[4], "Priest");
    strcpy(clss[5], "Bard");
    strcpy(clss[6], "Ranger");

    clw();
    tx("Name      : %s\r\n", p[cpl].name[nr]);
    tx("Race      : %s\r\n", race[p[cpl].ra[nr]]);
    tx("Class     : %s\r\n", clss[p[cpl].cl[nr]]);
    tx("\r\n");
    tx("Skill     : %d\r\n", p[cpl].ws[nr]);
    tx("Hp/H      : %d/%d\r\n", p[cpl].hp[nr], p[cpl].h[nr]);
    tx("Wisdom    : %d\r\n", p[cpl].wi[nr]);
    tx("Charisma  : %d\r\n", p[cpl].ch[nr]);
    tx("Magic     : %d\r\n", p[cpl].ma[nr]);
    tx("\r\n");
    ryl_pause();
    clw();
    tx("Level     : %d\r\n", p[cpl].lv[nr]);
    tx("Weapon    : %s\r\n", object(p[cpl].wp[nr]));
    tx("Armour    : %s\r\n\r\n", object(p[cpl].ar[nr]));
    if (p[cpl].dead[nr]) {
        strcpy(str, "Dead.");
    } else {
        strcpy(str, "Alive.");
    }
    tx("Status    : %s\r\n\r\n", str);
    tx("Exp       : %u\r\n\r\n", p[cpl].exp[nr]);
    ryl_pause();
}

void journal(char *send_me) {
    FILE *fh;
    char str[90];

    if ((fh = fopen("data/journal", "r"))) {
        while (1) {
            str[0] = 0;
            fgets((char *) str, sizeof(str), fh);
            if (str[0] == 0) {
                break;
            }
            if (!stricmp(str, send_me)) {
                /* Match */
                fclose(fh);
                return;
            }
        }
        fclose(fh);
    }

    if (!(fh = fopen("data/journal", "a+"))) {
        return;
    }
    fputs(send_me, fh);
    fclose(fh);
}
void clrjr(uint8_t nr) {
    FILE *fh;
    char str[144], a[10], b[20], temp[4];
    uint32_t byte[50];
    uint8_t i, j, k, current;

    if (!(fh = fopen("data/journal", "r"))) {
        exit(0);
    }

    i = 1;

    while (1) {
        str[0] = 0;
        fgets((char *) str, sizeof(str), fh);
        if (str[0] == 0) {
            break;
        }
        sscanf(str, "%s %s", a, b);
        if (atoi(a) == nr) {
            byte[i] = ftell(fh);
            byte[i] -= strlen(str);
            printf("%d\n", byte[i]);
            i++;
        }
    }

    if (!(fh = fopen("data/journal", "r+"))) {
        exit(0);
    }

    for (j = 1; j < i + 1; j++) {
        fseek(fh, byte[j], 0);
        fputc('*', fh);
        fputc('*', fh);
    }

    fclose(fh);

    /* Och nu skall alla som hade h|gre spelarnummer {n 'nr' minskas ett steg */
    /* Till{ggas kan att det blir ett helvete */

    current = nr + 1;

    if (!(fh = fopen("data/journal", "r"))) {
        exit(0);
    }

    for (k = current; k < antal_spelare + 1; k++) {
        i = 1;

        while (1) {
            str[0] = 0;
            fgets((char *) str, sizeof(str), fh);
            if (str[0] == 0) {
                break;
            }
            sscanf(str, "%s %s", a, b);
            if (atoi(a) == current) {
                byte[i] = ftell(fh);
                byte[i] -= strlen(str);
                //printf("%d\n",byte[i]);
                i++;
            }
        }

        if (!(fh = fopen("data/journal", "r+"))) {
            exit(0);
        }

        for (j = 1; j < i + 1; j++) {
            fseek(fh, byte[j], 0);
            if (current <= 10) {
                snprintf(temp, sizeof temp, "%d", current - 1);
                fputc(temp[0], fh);
                fputc(32, fh);
            } else {
                snprintf(temp, sizeof temp, "%d", current - 1);
                fputc(temp[0], fh);
                fputc(temp[1], fh);
            }
        }
        fclose(fh);
    }
}
void help() {
    clw();
    /* C E H J L  P Q R S T  V W X Z ! ? SPC */
    tx("C = Camp\r\n");
    tx("E = Enter\r\n");
    tx("H = Hunt for Food\r\n");
    tx("J = View Journal\r\n");
    tx("L = Leave (Discard) Item\r\n");
    tx("P = Put down weapon\r\n\r\n");
    ryl_pause();
    clw();
    tx("Q = Quaff Potion\r\n");
    tx("R = Ready Armour\r\n");
    tx("S = Save Game\r\n");
    tx("T = Take off armour\r\n");
    tx("V = View Party\r\n");
    tx("W = Wield Weapon\r\n\r\n");
    ryl_pause();
    clw();
    tx("X = Quit Game (No Save)\r\n");
    tx("Z = Cast Spell\r\n");
    tx("! = Redraw Screen\r\n");
    tx("? = View this help\r\n");
    tx("    8     Move North,\r\n");
    tx("  4-+-6   South, East &\r\n");
    tx("    2     West\r\n\r\n");
    ryl_pause();
    clw();
}
