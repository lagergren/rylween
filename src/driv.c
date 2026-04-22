#include "io.h"
#include "res.h"
#include "ryl.h"
#include "driv.h"
#include "interact.h"
#include "person.h"
#include "primitives.h"
#include "subs.h"
#include "towne.h"

static void clrjr(uint8_t);
static void createparty(void);
static void load(void);
static void position_locations(void);
static void read_journal(void);
static void spelet(void);

static void spelet() {
    char cc, d;
    int32_t i;

    clw();
    state.tid = 5;
    state.hours_pigg = 14;

    tx0(res_text("title.header"));

    tx0(res_text("title.prog_by"));
    tx0(res_text("title.prog_name"));

    tx0(res_text("title.created_by"));
    tx0(res_text("title.creator_name"));

    tx0(res_text("title.graphics_by"));
    tx0(res_text("title.graphics_name"));

    /* Prep map for positions of huts/cottages */

    for (i = 1; i < 40; i++) {
        if (!state.npc[i].towne) {
            state.map[state.npc[i].y][state.npc[i].x] = '1';
        }
    }

    update_map();

    while (1) {
        if (io_winch_pending) {
            io_winch_pending = 0;
            cl();
            disptext("data/ansi/screen.ans");
            update_map();
            clw();
        }

        assert(state.cpl >= 1 && state.cpl <= state.antal_spelare);
        timehandler();
        foodhandler();
        attackhandler();
        statusline();

        if (!state.p[state.cpl].tid_kvar) {
            clw();
            tx0(res_text("driv.moves.out_of_moves"));
            tx0(res_text("driv.menu.out_of_moves"));
            while (1) {
                cc = key();
                cc = toupper(cc);
                if (cc == 'S' || cc == 'Q') {
                    break;
                }
            }
            if (cc == 'S') {
                clw();
                tx0(res_text("driv.save.saving"));
                save();
                ryl_delay(400);
                tx0(res_text("driv.save.done"));
                ryl_delay(400);
            }
            clw();
            tx0(res_text("driv.quit.farewell"));
            ryl_delay(1000);
            scorelist();
            cleanexit();
        }

        cc = key();
        cc = toupper(cc);

        switch (cc) {
            case ' ':
                clw();
                tx0(res_text("driv.move.pass"));
                break;
            case '8':
                if (!state.p[state.cpl].tid_kvar) {
                    clw();
                    tx0(res_text("driv.move.no_moves"));
                    break;
                }
                if (state.map[state.p[state.cpl].y - 1][state.p[state.cpl].x] != 'm' && state.map[state.p[state.cpl].y - 1][state.p[state.cpl].x] != 'w') {
                    state.last_terrain = state.map[state.p[state.cpl].y][state.p[state.cpl].x];
                    state.p[state.cpl].y--;
                    update_map();
                    slumpvis();
                    state.p[state.cpl].tid_kvar--;
                }
                break;
            case '4':
                if (!state.p[state.cpl].tid_kvar) {
                    clw();
                    tx0(res_text("driv.move.no_moves"));
                    break;
                }
                if (state.map[state.p[state.cpl].y][state.p[state.cpl].x - 1] != 'm' && state.map[state.p[state.cpl].y][state.p[state.cpl].x - 1] != 'w') {
                    state.last_terrain = state.map[state.p[state.cpl].y][state.p[state.cpl].x];
                    state.p[state.cpl].x--;
                    update_map();
                    slumpvis();
                    state.p[state.cpl].tid_kvar--;
                }
                break;
            case '2':
                if (!state.p[state.cpl].tid_kvar) {
                    clw();
                    tx0(res_text("driv.move.no_moves"));
                    break;
                }
                if (state.map[state.p[state.cpl].y + 1][state.p[state.cpl].x] != 'm' && state.map[state.p[state.cpl].y + 1][state.p[state.cpl].x] != 'w') {
                    state.last_terrain = state.map[state.p[state.cpl].y][state.p[state.cpl].x];
                    state.p[state.cpl].y++;
                    update_map();
                    slumpvis();
                    state.p[state.cpl].tid_kvar--;
                }
                break;
            case '6':
                if (!state.p[state.cpl].tid_kvar) {
                    clw();
                    tx0(res_text("driv.move.no_moves"));
                    break;
                }
                if (state.map[state.p[state.cpl].y][state.p[state.cpl].x + 1] != 'm' && state.map[state.p[state.cpl].y][state.p[state.cpl].x + 1] != 'w') {
                    state.last_terrain = state.map[state.p[state.cpl].y][state.p[state.cpl].x];
                    state.p[state.cpl].x++;
                    update_map();
                    slumpvis();
                    state.p[state.cpl].tid_kvar--;
                }
                break;

            case 'S':
                clw();
                tx0(res_text("driv.save.saving"));
                save();
                ryl_delay(400);
                tx0(res_text("driv.save.done"));
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
                tx0(res_text("driv.view.who"));
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
                tx0(res_text("driv.quit.confirm"));
                tx0(res_text("common.yes"));
                tx0(res_text("common.no"));
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
    tx0(res_text("driv.drop.another"));
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

static void read_journal() {
    FILE *j;
    char str[90], str2[88], plrstr[6];
    int32_t plr = 0;
    unsigned char line = 0;

    if (!(j = fopen("data/journal", "r"))) {
        return;
    }

    tx0(res_text("driv.journal.header"));

    while (1) {
        str[0] = 0;
        fgets((char *)str, sizeof(str), j);
        if (str[0] == 0) {
            ryl_pause();
            break;
        }
        sscanf(str, "%s %s", plrstr, str2);
        plr = atoi(plrstr);

        if (plr == state.cpl) {
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

    if (state.p[state.cpl].food < 0) {
        state.p[state.cpl].food = 0;
    }
    stline[0] = 0;
    if (state.tid >= 6 && state.tid < 12) {
        snprintf(stline, sizeof stline, "TIME: [33mDAWN[32m      ");
    }
    if (state.tid >= 12 && state.tid < 18) {
        snprintf(stline, sizeof stline, "TIME: [33mMIDDAY[32m    ");
    }
    if (state.tid >= 18 && state.tid < 24) {
        snprintf(stline, sizeof stline, "TIME: [33mTWILIGHT[32m  ");
    }
    if (state.tid == 24 || state.tid < 6) {
        snprintf(stline, sizeof stline, "TIME: [33mMIDNIGHT[32m  ");
    }
    if (state.p[state.cpl].food < state.p[state.cpl].pl * 3 && state.p[state.cpl].food) {
        str_append(stline, sizeof stline, "ST: [35mHUNGRY  ");
    }
    if (!state.p[state.cpl].food) {
        str_append(stline, sizeof stline, "ST: [31mSTARVING[32m ");
    }
    if (state.p[state.cpl].food && state.p[state.cpl].food >= state.p[state.cpl].pl * 3) {
        str_append(stline, sizeof stline, "ST: [33mOK    ");
    }
    snprintf(str, sizeof str, "[32m X: [33m%2d   [32mY: [33m%2d[32m     ", state.p[state.cpl].x, state.p[state.cpl].y);
    str_append(stline, sizeof stline, str);
    snprintf(str, sizeof str, "FD: [33m%4d[32m  ", state.p[state.cpl].food);
    str_append(stline, sizeof stline, str);
    snprintf(str, sizeof str, "MV: [33m%3d[32m  ", state.p[state.cpl].tid_kvar);
    str_append(stline, sizeof stline, str);
    snprintf(str, sizeof str, "AU: [33m%6d[32m", state.p[state.cpl].gold);
    str_append(stline, sizeof stline, str);
    c(3, 21);
    tx("[32m%s    ", stline);
    snprintf(stline, sizeof stline, "H1: [33m%3d/%3d   [32mH2: [33m%3d/%3d   [32mH3: [33m%3d/%3d   [32mH4: [33m%3d/%3d[37m",
             state.p[state.cpl].hp[1], state.p[state.cpl].h[1], state.p[state.cpl].hp[2], state.p[state.cpl].h[2], state.p[state.cpl].hp[3], state.p[state.cpl].h[3],
             state.p[state.cpl].hp[4], state.p[state.cpl].h[4]);
    c(13, 22);
    tx("%s\r\n", stline);
    c(49, 6);
}
static void position_locations() {
    int32_t i;

    for (i = 0; i < 15; i++) {
        state.dx[i] = 0;
        state.dy[i] = 0;
    }

    for (i = 1; i < 10; i++) {
        while (1) {
            state.dx[i] = ryl_random(5, 74);
            state.dy[i] = ryl_random(5, 34);
            if (state.map[state.dy[i]][state.dx[i]] != 'w' && state.map[state.dy[i]][state.dx[i]] != 'm' && state.map[state.dy[i]][state.dx[i]] != '0' &&
                state.map[state.dy[i]][state.dx[i]] != '1' && state.map[state.dy[i]][state.dx[i]] != '2') {
                break;
            }
        }
    }
}

void init() {
    char str[90], kl[8], path[512], d, ch;
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
        fgets((char *)state.map[i], sizeof(state.map[i]), fh);
    }
    fclose(fh);

    ryl_state_path("realm.dat", path, sizeof path);
    if (!(fh = fopen(path, "rb"))) {
        state.antal_spelare = 0;
        state.senaste_dagen = 0;
        position_locations();

        if (!(fh = fopen(path, "wb"))) {
            cleanexit();
        }

        fwrite((char *)&state.antal_spelare, sizeof(state.antal_spelare), 1, fh);
        fwrite((char *)&state.senaste_dagen, sizeof(state.senaste_dagen), 1, fh);
        for (i = 1; i < 15; i++) {
            fwrite((char *)&state.dx[i], sizeof(state.dx[i]), 1, fh);
        }
        for (i = 1; i < 15; i++) {
            fwrite((char *)&state.dy[i], sizeof(state.dy[i]), 1, fh);
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

    load();
    getclk(kl);
    if (kl[3] != state.senaste_dagen) {
        cl();
        tx("First Game of the Day... Updating... Please Wait...");

        // Clear entry file

        ryl_state_path("entry.dat", path, sizeof path);
        fh = fopen(path, "wb");
        fclose(fh);

        for (i = 1; i < state.antal_spelare + 1; i++) {
            tx(".");
            state.p[i].tid_kvar = 175;
            ryl_delay(40);
        }
        getclk(kl);
        state.day_at_game_start = kl[3];
        save();
    }
    getclk(kl);
    state.day_at_game_start = kl[3];

    cl();
    disptext("data/ansi/screen.ans");

    while (!abort) {
        clw();

        tx("ADVENTURERS of RYLWEEN\r\n\r\n");

        tx("%s's copy.\r\n\r\n", state.global_name);

        for (i = 1, x = 0; i < state.antal_spelare + 1; i++) {
            if (!stricmp(state.global_name, state.p[i].player)) {
                x = i;
            }
        }
        for (i = 1; i < state.antal_spelare + 1; i++) {
            if (state.p[i].silverkey == 2) {
                tx("The struggle is over.\r\n");
                tx("[31m%s[37m have\r\n", state.p[i].partyname);
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

                    ryl_state_path("entry.dat", path, sizeof path);
                    fh = fopen(path, "rb");
                    i = 0;
                    while (1) {
                        str[0] = 0;
                        fread((char *)str, sizeof(str), 1, fh);
                        if (str[0] == 0) {
                            break;
                        }
                        if (!stricmp(str, state.global_name)) {
                            i++;
                        }
                    }
                    fclose(fh);
                    if (i >= 4) {
                        j = 4;
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

                    ryl_state_path("entry.dat", path, sizeof path);
                    fh = fopen(path, "ab");
                    strcpy(str, state.global_name);
                    fwrite((char *)str, sizeof(str), 1, fh);
                    fclose(fh);

                    // Checking done. Load

                    load();
                    abort = 1;
                    snprintf(str, sizeof str, "%s and %s party '%s' entered the realm.", state.p[state.cpl].name[1],
                             pronoun(1, 3), state.p[state.cpl].partyname);
                    ryl_log(str);
                    state.last_terrain = 0;
                } else {
                    clw();
                    tx("No game available.\r\n");
                    ryl_delay(1500);
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
                        ryl_state_path("realm.dat", path, sizeof path);
                        if (!(fh = fopen(path, "wb"))) {
                            cleanexit();
                        }
                        state.antal_spelare--;
                        fwrite((char *)&state.antal_spelare, sizeof(state.antal_spelare), 1, fh);
                        fwrite((char *)&state.senaste_dagen, sizeof(state.senaste_dagen), 1, fh);
                        for (i = 1; i < 15; i++) {
                            fwrite((char *)&state.dx[i], sizeof(state.dx[i]), 1, fh);
                        }
                        for (i = 1; i < 15; i++) {
                            fwrite((char *)&state.dy[i], sizeof(state.dy[i]), 1, fh);
                        }
                        for (i = 1; i < state.antal_spelare + 2; i++) {
                            if (i != x) {
                                fwrite((char *)&state.p[i], sizeof(state.p[i]), 1, fh);
                            }
                        }
                        fclose(fh);
                        ryl_delay(1000);

                        /* Fixa journalen... */

                        clw();
                        tx("Editing Journal...");
                        clrjr(x);
                        ryl_delay(1000);

                        /* Clean NPC dat */

                        clw();
                        tx("Editing NPC.DAT...");
                        ryl_state_path("npc.dat", path, sizeof path);
                        if (!(fh = fopen(path, "wb"))) {
                            cleanexit();
                        }
                        // Om I > x (ditt party) och i for:as till antal_spelare (ej +1)
                        // npc[i].visited[x] = npc[i].visited[x+1];
                        for (i = 1; i < 40; i++) {
                            for (j = 1; j < state.antal_spelare; j++) {
                                state.npc[i].visited[x] = state.npc[i].visited[x + 1];
                            }
                            fwrite((char *)&state.npc[i], sizeof(state.npc[i]), 1, fh);
                        }
                        fclose(fh);
                        ryl_delay(1000);

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
    char path[512];

    getclk(kl);

    //senaste_dagen = day_at_game_start;
    state.senaste_dagen = kl[3];

    ryl_state_path("realm.dat", path, sizeof path);
    if (!(fh = fopen(path, "wb"))) {
        c(49, 6);
        clw();
        tx("\r\nUnable to save data!!!\r\n");
        ryl_pause();
        cleanexit();
    }

    fwrite((char *)&state.antal_spelare, sizeof(state.antal_spelare), 1, fh);
    fwrite((char *)&state.senaste_dagen, sizeof(state.senaste_dagen), 1, fh);
    for (i = 1; i < 15; i++) {
        fwrite((char *)&state.dx[i], sizeof(state.dx[i]), 1, fh);
    }
    for (i = 1; i < 15; i++) {
        fwrite((char *)&state.dy[i], sizeof(state.dy[i]), 1, fh);
    }

    if (state.antal_spelare) {
        for (i = 1; i < state.antal_spelare + 1; i++) {
            fwrite((char *)&state.p[i], sizeof(state.p[i]), 1, fh);
        }
    }
    fclose(fh);

    ryl_state_path("npc.dat", path, sizeof path);
    if (!(fh = fopen(path, "wb"))) {
        c(49, 6);
        clw();
        tx("\r\nUnable to save NPCs!!!\r\n");
        ryl_pause();
        cleanexit();
    }
    for (i = 1; i < 40; i++) {
        fwrite((char *)&state.npc[i], sizeof(state.npc[i]), 1, fh);
    }
    fclose(fh);
}
static void load() {
    FILE *fh;
    int32_t i;
    char path[512];

    ryl_state_path("realm.dat", path, sizeof path);
    if (!(fh = fopen(path, "rb"))) {
        c(49, 6);
        clw();
        tx("Unable to load data!!!\r\n");
        ryl_pause();
        cleanexit();
    }

    fread((char *)&state.antal_spelare, sizeof(state.antal_spelare), 1, fh);
    fread((char *)&state.senaste_dagen, sizeof(state.senaste_dagen), 1, fh);
    for (i = 1; i < 15; i++) {
        fread((char *)&state.dx[i], sizeof(state.dx[i]), 1, fh);
    }
    for (i = 1; i < 15; i++) {
        fread((char *)&state.dy[i], sizeof(state.dy[i]), 1, fh);
    }

    if (state.antal_spelare) {
        for (i = 1; i < state.antal_spelare + 1; i++) {
            fread((char *)&state.p[i], sizeof(state.p[i]), 1, fh);
        }
    }
    fclose(fh);
    for (i = 1; i < state.antal_spelare + 1; i++) {
        if (!stricmp(state.global_name, state.p[i].player)) {
            state.cpl = i;
        }
    }

    ryl_state_path("npc.dat", path, sizeof path);
    if (!(fh = fopen(path, "rb"))) {
        c(49, 6);
        clw();
        tx("\r\nUnable to load NPCs!!!\r\n");
        ryl_pause();
        cleanexit();
    }
    for (i = 1; i < 40; i++) {
        fread((char *)&state.npc[i], sizeof(state.npc[i]), 1, fh);
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
        fgets((char *)state.map[i], sizeof(state.map[i]), fh);
    }
    fclose(fh);
}

char *pronoun(uint8_t nr, uint8_t xx) {
    uint8_t sex = state.p[state.cpl].sex[nr];
    /* Original: female's xx in {2,3} both returned "her"; clamp to match. */
    if (sex == 2 && xx == 3) {
        xx = 2;
    }
    return (char *)res_pronoun(sex, xx);
}

static void createparty() {
    int32_t i, a, g, passed;
    char str[80];

    clw();

    tx("Party Creation\r\n\r\n");

    if (state.antal_spelare == 25) {
        tx("Sorry. Maximum number of\r\n");
        tx("players reached. Play\r\n");
        tx("again later on.\r\n\r\n");
        ryl_pause();
        cleanexit();
    }

    state.antal_spelare++;
    a = state.antal_spelare;
    state.cpl = a;

    while (1) {
        tx("What is the name of your\r\n");
        tx("party?\r\n\r\n");
        tx(": ");

        getstr(18, state.p[a].partyname, sizeof state.p[a].partyname);
        if (!state.p[a].partyname[0]) {
            state.antal_spelare--;
            state.cpl = 0;
            return;
        } else {
            break;
        }
    }

    g = 1;

    while (g < 5) {
        clw();
        tx("Stats for char: %d\r\n\r\n", g);
        state.p[a].ws[g] = ryl_random(15, 70);
        state.p[a].wi[g] = ryl_random(15, 70);
        state.p[a].h[g] = ryl_random(15, 70);
        state.p[a].hp[g] = state.p[a].h[g];
        state.p[a].ch[g] = ryl_random(15, 70);
        tx("Weapon Skill : %2d\r\n", state.p[a].ws[g]);
        tx("Health       : %2d\r\n", state.p[a].h[g]);
        tx("Wisdom       : %2d\r\n", state.p[a].wi[g]);
        tx("Charisma     : %2d\r\n\r\n", state.p[a].ch[g]);
        tx("Reroll (y/N) ");
        if (!hotkey()) {
            while (1) {
                clw();
                tx("Give Character %d a name!\r\n", g);
                tx(": ");
                str[0] = 0;
                getstr(12, state.p[a].name[g], sizeof state.p[a].name[g]);
                if (!state.p[a].name[g][0]) {
                    state.antal_spelare--;
                    state.cpl = 0;
                    return;
                } else {
                    break;
                }
            }
            state.p[a].name[g][0] = toupper(state.p[a].name[g][0]);

            clw();
            tx("What sex is\r\n");
            tx("%s?\r\n\r\n", state.p[a].name[g]);
            tx("1. Male\r\n");
            tx("2. Female\r\n");
            str[0] = 0;
            while (str[0] < '1' || str[0] > '2') {
                tx(": ");
                getstr(2, str, sizeof str);
            }
            state.p[a].sex[g] = str[0] - 48;

            clw();
            tx("What race is\r\n");
            tx("%s?\r\n\r\n", state.p[a].name[g]);
            tx("1. Human  2. Dwarf\r\n");
            tx("3. Elf    4. Halfling\r\n");
            str[0] = 0;
            while (str[0] < '1' || str[0] > '4') {
                tx(": ");
                getstr(2, str, sizeof str);
            }
            state.p[a].ra[g] = str[0] - 48;

            clw();
            tx("Pick a class for this\r\n");
            tx("character...\r\n\r\n");
            tx("1. Fighter 2. Thief\r\n");
            tx("3. Mage    4. Priest\r\n");
            tx("5. Bard    6. Ranger\r\n");
            str[0] = 0;
            while (str[0] < '1' || str[0] > '6') {
                tx(": ");
                getstr(2, str, sizeof str);
            }
            state.p[a].cl[g] = str[0] - 48;
            clw();
            passed = 0;
            switch (state.p[a].cl[g]) {
                case 1:
                    if (state.p[a].ws[g] < 50) {
                        tx("Weap. skill must be > 50.\r\n");
                        ryl_delay(1000);
                    } else {
                        passed = 1;
                    }
                    break;
                case 2:
                    passed = 1;
                    break;
                case 3:
                    if (state.p[a].wi[g] < 50) {
                        tx("Wisdom must be > 50.\r\n");
                        ryl_delay(1000);
                    } else {
                        passed = 1;
                    }
                    break;
                case 4:
                    if (state.p[a].wi[g] < 50) {
                        tx("Wisdom must be > 50.\r\n");
                        ryl_delay(1000);
                    } else {
                        passed = 1;
                    }
                    break;
                case 5:
                    if (state.p[a].ch[g] < 50) {
                        tx("Charisma must be > 50.\r\n");
                        ryl_delay(1000);
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
                if (state.p[a].cl[g] == 3 || state.p[a].cl[g] == 4) {
                    state.p[a].ma[g] = state.p[a].wi[g];
                } else {
                    state.p[a].ma[g] = 0;
                }
                show(g);
                g++;
            }
        }
    }
    state.p[a].x = 12;
    state.p[a].y = 9;
    state.p[a].gold = 1000;
    state.p[a].torches = 5;
    state.p[a].food = 100;
    state.p[a].tid_kvar = 200;
    state.p[a].pl = 4;
    state.p[a].fireshield = 0;
    state.p[a].bracelets = 0;
    state.p[a].mastercrown = 0;
    state.p[a].jazzyfoot = 0;
    state.p[a].silverkey = 0;
    for (i = 0; i < 17; i++) {
        state.p[a].inventory[i] = 0;
    }
    for (i = 0; i < 40; i++) {
        state.p[a].flag[i] = 0;
    }
    for (i = 1; i < 5; i++) {
        state.p[a].lv[i] = 1;
    }
    for (i = 1; i < 5; i++) {
        state.p[a].ar[i] = 0;
    }
    for (i = 1; i < 5; i++) {
        state.p[a].lv[i] = 1;
    }
    for (i = 1; i < 5; i++) {
        state.p[a].exp[i] = 0;
    }
    for (i = 1; i < 5; i++) {
        state.p[a].dead[i] = 0;
    }
    strcpy(state.p[a].player, state.global_name);
    clw();
    tx("Saving...\r\n");
    save();
    ryl_delay(1000);
}

void showparty() {
    clw();
    tx("%u torches.\r\n", state.p[state.cpl].torches);
    tx("%d rations.\r\n", state.p[state.cpl].food);
    tx("%d gold.\r\n\r\n", state.p[state.cpl].gold);
    if (state.p[state.cpl].fireshield) {
        tx("You have the Shield.\r\n");
    }
    if (state.p[state.cpl].bracelets) {
        tx("You have the Bracelets.\r\n");
    }
    if (state.p[state.cpl].mastercrown) {
        tx("You have the Crown.\r\n");
    }
    if (state.p[state.cpl].jazzyfoot) {
        tx("You have the Foot.\r\n");
    }
    if (state.p[state.cpl].silverkey) {
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
        strncpy(item[i], object(state.p[state.cpl].inventory[i]), 9);
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
    tx("Name      : %s\r\n", state.p[state.cpl].name[nr]);
    tx("Race      : %s\r\n", race[state.p[state.cpl].ra[nr]]);
    tx("Class     : %s\r\n", clss[state.p[state.cpl].cl[nr]]);
    tx("\r\n");
    tx("Skill     : %d\r\n", state.p[state.cpl].ws[nr]);
    tx("Hp/H      : %d/%d\r\n", state.p[state.cpl].hp[nr], state.p[state.cpl].h[nr]);
    tx("Wisdom    : %d\r\n", state.p[state.cpl].wi[nr]);
    tx("Charisma  : %d\r\n", state.p[state.cpl].ch[nr]);
    tx("Magic     : %d\r\n", state.p[state.cpl].ma[nr]);
    tx("\r\n");
    ryl_pause();
    clw();
    tx("Level     : %d\r\n", state.p[state.cpl].lv[nr]);
    tx("Weapon    : %s\r\n", object(state.p[state.cpl].wp[nr]));
    tx("Armour    : %s\r\n\r\n", object(state.p[state.cpl].ar[nr]));
    if (state.p[state.cpl].dead[nr]) {
        strcpy(str, "Dead.");
    } else {
        strcpy(str, "Alive.");
    }
    tx("Status    : %s\r\n\r\n", str);
    tx("Exp       : %u\r\n\r\n", state.p[state.cpl].exp[nr]);
    ryl_pause();
}

void journal(char *send_me) {
    FILE *fh;
    char str[90];

    if ((fh = fopen("data/journal", "r"))) {
        while (1) {
            str[0] = 0;
            fgets((char *)str, sizeof(str), fh);
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
static void clrjr(uint8_t nr) {
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
        fgets((char *)str, sizeof(str), fh);
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

    for (k = current; k < state.antal_spelare + 1; k++) {
        i = 1;

        while (1) {
            str[0] = 0;
            fgets((char *)str, sizeof(str), fh);
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
