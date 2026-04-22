#include "ryl.h"

void guild() {
    char f, d, str2[80], str[80];
    uint8_t i, x, xx, yy;
    char townname[10];
    FILE *fh;

    clr_map_window();
    blitgfx(guildpic);

    while (1) {
        clw();
        statusline();
        if (state.p[state.cpl].waiting_mail) {
            tx("- We have messages\r\n");
            tx("  for you, Sirs...\r\n\r\n");
            ryl_pause();
            read_internal_mail();
        } else {
            tx("- How may we be of\r\n");
            tx("  assistance?\r\n\r\n");
        }
        tx("(B)ulletin Board\r\n"); /* Public. Has to be Read/Written */
        tx("(Q)uest Status\r\n");   /* Info about individual parties */
        tx("(S)end Courier\r\n");   /* Private Message */
        tx("(T)rade Items\r\n");    /* Select a party to trade with. */
        tx("(V)iew Parties\r\n");   /* View present parties */
        tx("(L)eave\r\n");
        while (1) {
            f = key();
            f = toupper(f);
            if (f == 'L') {
                return;
            }
            if (f == 'B' || f == 'Q' || f == 'S' || f == 'T' || f == 'V') {
                break;
            }
        }

        // * ACTION * //

        switch (f) {
            case 'B':
                guild_title("  BULLETINS POSTED  ");
                for (i = 1; i < 4; i++) {
                    snprintf(str2, sizeof str2, "data/external/bull%d.dat", i);
                    if (!(fh = fopen(str2, "r"))) {
                        fh = fopen(str2, "w");
                        fputs("[37mNeblod Zin, [36mleader of Dwellers Below, posted this bulletin Jan 10 1994\n",
                              fh);
                        fputs("===============================================================================\n",
                              fh);
                        fputs("[37mFinally! Rylween version 3. It has been delayed by my emigration, a\n",
                              fh);
                        fputs("motor vehicle accident and general laziness. BUT ENJOY IT NOW!!!\n",
                              fh);
                        fclose(fh);
                        fh = fopen("data/external/last_bull", "w");
                        fputs("3\n", fh);
                        fclose(fh);
                        fh = fopen(str2, "r");
                    }
                    while (1) {
                        str[0] = 0;
                        fgets((char *)&str, sizeof(str), fh);
                        tx("%s\r", str);
                        if (str[0] == 0) {
                            break;
                        }
                    }
                    fclose(fh);
                    tx("\r\n");
                    tx("<MORE>\r\n");
                    key();
                    tx("\r\n");
                }
                guild_title("  BULLETIN MENU  ");
                tx("Do you want to enter a bulletin? (y/N)");
                d = key();
                d = toupper(d);
                if (d == 'Y') {
                    fh = fopen("data/external/last_bull", "r");
                    fgets((char *)str2, sizeof(str2), fh);
                    fclose(fh);
                    x = atoi(str2);
                    x++;
                    if (x == 4) {
                        x = 1;
                    }
                    snprintf(str2, sizeof str2, "data/external/bull%d.dat", x);
                    cl();
                    c(0, 0);
                    editor(str2, 1);
                    fh = fopen("data/external/last_bull", "w");
                    snprintf(str2, sizeof str2, "%d\n", x);
                    fputs(str2, fh);
                    fclose(fh);
                }

                cl();
                c(49, 6);
                disptext("data/ansi/screen.ans");
                clr_map_window();
                blitgfx(guildpic);
                break;

            case 'S':
                clw();
                tx("Whom do you want to\r\n");
                tx("send a message?\r\n\r\n");
                ryl_pause();
                cl();
                c(0, 0);
                x = select_party();
                if (x == state.cpl) {
                    break;
                }

                snprintf(str2, sizeof str2, "data/internal/msg%d.dat", x);
                tx("%s\n", str2);
                ryl_delay(2000);
                cl();
                c(0, 0);
                editor(str2, 0);
                state.p[x].waiting_mail = 1;

                cl();
                c(49, 6);
                disptext("data/ansi/screen.ans");
                clr_map_window();
                blitgfx(guildpic);
                break;

            case 'Q':  // QUEST STATUS //
                cl();
                c(0, 0);
                x = select_party();
                cl();
                disptext("data/ansi/screen.ans");
                blitgfx(guildpic);
                clw();
                tx("%s\r\n", state.p[x].partyname);
                if (state.map[state.p[x].y][state.p[x].x] == '0') {
                    if (state.p[x].x == 12 && state.p[x].y == 11) {
                        strcpy(townname, "Thord");
                    }
                    if (state.p[x].x == 48 && state.p[x].y == 12) {
                        strcpy(townname, "Ollk");
                    }
                    if (state.p[x].x == 43 && state.p[x].y == 20) {
                        strcpy(townname, "Wilor");
                    }
                    if (state.p[x].x == 55 && state.p[x].y == 29) {
                        strcpy(townname, "Murn");
                    }
                    tx("are in %s.\r\n\r\n", townname);
                } else {
                    xx = state.p[x].x + ryl_random(0, 4);
                    yy = state.p[x].y + ryl_random(0, 4);
                    tx("were last seen in\r\n");
                    tx("the area around %dx%d.\r\n\r\n", xx, yy);
                }
                ryl_pause();
                clw();
                break;

            case 'V':  // VIEW PARTIES //
                cl();
                c(0, 0);
                tx("[37m[44m            ==================== PARTIES IN TOWN ====================          \r\n");
                tx("             #        Name                 Leader             Avg Lvl          \r\n");
                tx("            =========================================================          \r\n\r\n");
                for (i = 1; i < state.antal_spelare + 1; i++) {
                    if (state.p[i].x == state.p[state.cpl].x && state.p[i].y == state.p[state.cpl].y) {
                        tx("[40m             [33m%c.       [35m%-20s  [31m%-15s   [34m%3d\r\n",
                           64 + i, state.p[i].partyname, state.p[i].name[1],
                           (state.p[i].lv[1] + state.p[i].lv[2] + state.p[i].lv[3] + state.p[i].lv[4]) / 4);
                    }
                    if (i == 20) {
                        tx("[37m<MORE>\r\n");
                        key();
                    }
                }
                tx("[37m\r\n");
                tx("<MORE>\r\n");
                key();
                cl();
                c(49, 6);
                disptext("data/ansi/screen.ans");
                clr_map_window();
                blitgfx(guildpic);
                break;
        }
    }
}
uint8_t select_party() {
    uint8_t i;
    char d;

    cl();
    c(0, 0);
    tx("[37m[44m            ==================== SELECT A PARTY =====================          \r\n");
    tx("             #        Name                 Leader             Avg Lvl          \r\n");
    tx("            =========================================================          \r\n\r\n");
    for (i = 1; i < state.antal_spelare + 1; i++) {
        tx("[40m             [33m%c.[35m       %-20s  [31m%-15s   [34m%3d\r\n", 64 + i,
           state.p[i].partyname, state.p[i].name[1], (state.p[i].lv[1] + state.p[i].lv[2] + state.p[i].lv[3] + state.p[i].lv[4]) / 4);
        if (i == 20) {
            tx("[37m<MORE>\r\n");
            key();
        }
    }
    tx("[37m\r\n");
    tx("Who? (A-%c)\r\n", 64 + state.antal_spelare);
    while (1) {
        d = key();
        d = toupper(d);
        if (d >= 'A' && d <= 64 + state.antal_spelare) {
            break;
        }
    }
    return (uint8_t)(d - 64);
}

void editor(char *outfile, uint8_t bulletin) {
    FILE *fh2;
    char str2[80], mnt[5], str[80];
    char kl[8];
    uint8_t i;

    // BULLETIN =1 , bulletin. Otherwise message //

    getclk(kl);

    switch (kl[2]) {
        case 1:
            strcpy(mnt, "Jan");
            break;
        case 2:
            strcpy(mnt, "Feb");
            break;
        case 3:
            strcpy(mnt, "Mar");
            break;
        case 4:
            strcpy(mnt, "Apr");
            break;
        case 5:
            strcpy(mnt, "May");
            break;
        case 6:
            strcpy(mnt, "Jun");
            break;
        case 7:
            strcpy(mnt, "Jul");
            break;
        case 8:
            strcpy(mnt, "Aug");
            break;
        case 9:
            strcpy(mnt, "Sep");
            break;
        case 10:
            strcpy(mnt, "Oct");
            break;
        case 11:
            strcpy(mnt, "Nov");
            break;
        case 12:
            strcpy(mnt, "Dec");
            break;
    }

    if (bulletin) {
        fh2 = fopen(outfile, "w");
        if (!fh2) {
            return;
        }
    } else {
        tx("OPENING %s\r\n", outfile);
        ryl_delay(4000);
        fh2 = fopen(outfile, "a+");
        if (!fh2) {
            return;
        }
    }

    if (!bulletin) {
        snprintf(str2, sizeof str2, "%s, the leader of %s sent you this message:\n\n", state.p[state.cpl].name[1],
                 state.p[state.cpl].partyname);
        fputs(str2, fh2);
    } else {
        snprintf(str2, sizeof str2, "[37m%s, [36mthe leader of %s posted this bulletin %s %d 19%d\n",
                 state.p[state.cpl].name[1], state.p[state.cpl].partyname, mnt, kl[3], kl[1] + 80);
        fputs(str2, fh2);
        fputs("===============================================================================\n[37m",
              fh2);
    }

    tx("[36mYou have up to [33m5[36m lines below. Press [37m[44mRETURN[40m[36m on a blank line to save.\r\n\r\n");
    for (i = 1; i < 6; i++) {
        tx("[33m%d[34m>[37m  ", i);
        strcpy(str, getstr(75));
        if (str[0] == 0) {
            break;
        }
        fputs(str, fh2);
        fputs("\n", fh2);
    }
    // SAVE
    if (!bulletin) {
        fputs("#\n", fh2);
    }
    tx("\r\n");
    if (!bulletin) {
        tx("[37mMessage Sent...\r\n");
    } else {
        tx("[37mBulletin Registered...\r\n");
    }
    ryl_delay(400);

    fclose(fh2);
}

void attackhandler(void) {
    uint8_t i, x;

    for (i = 1, x = 0; i < state.antal_spelare + 1; i++) {
        if (i != state.cpl && state.p[i].x == state.p[state.cpl].x && state.p[i].y == state.p[state.cpl].y) {
            x = 1;
            break;
        }
    }

    if (!x) {
        return;
    }

    clw();
    tx("You have stumbled upon\r\n");
    tx("the camp of\r\n");
    tx("%s!\r\n\r\n", state.p[i].partyname);
    tx("They are %d level\r\n", state.p[i].pl);
    tx("%d adventurers.\r\n\r\n", (state.p[i].lv[1] + state.p[i].lv[2] + state.p[i].lv[3] + state.p[i].lv[4]) / 4);
    ryl_pause();
    clw();
    tx("Do you want to:\r\n\r\n");
    tx("(A)ttack\r\n");
    tx("(S)teal Gold\r\n");
    tx("(L)eave\r\n");
}

void guild_title(char *title) {
    cl();
    tx("[37m[44m");
    c((80 - strlen(title)) / 2, 1);
    tx("%s", title);
    c(0, 3);
    tx("[40m");
}

void trade() {
}
void read_internal_mail() {
    state.p[state.cpl].waiting_mail = 0;
}

// TERRAIN DESCRIPTIONS - PUT HERE TO EVEN OUT THE FILE LENGTHS //
void check_terrain(char old, char new) {
    if (old == new) {
        return;
    }
    if (!ryl_chance(9)) {
        return;
    }

    clw();

    switch (new) {
        case 's':
            tx("The rich green plainland\r\n");
            tx("of Rylween stretches far\r\n");
            tx("in all the directions.\r\n");
            break;
        case '1':
            tx("You have found a hut.\r\n");
            break;
        case '0':
            tx("You are close to a towne.\r\n");
            break;
        case 'f':
            tx("Small foothills make your\r\n");
            tx("progress more arduous.\r\n");
            break;
        case 'd':
            tx("You are in a hot and dry\r\n");
            tx("desert. The sun fries\r\n");
            tx("you mercilessly.\r\n");
            break;
        case 't':
            tx("You are in the swampy\r\n");
            tx("marshlands of Rylween.\r\n");
            tx("Bubbles of gas rise to\r\n");
            tx("the surface where they\r\n");
            tx("burst and emit a foul\r\n");
            tx("smelling odour.\r\n");
            break;
        case 'g':
            tx("The ancient forest is\r\n");
            tx("very tranquil. A soft\r\n");
            tx("breeze rustles the\r\n");
            tx("leaves of mighty oaks.\r\n");
            break;
        case '2':
            tx("You stand outside the\r\n");
            tx("castle Zadraz. The\r\n");
            tx("black steel building\r\n");
            tx("seems to want to swallow\r\n");
            tx("you whole.\r\n");
            break;
    }
    tx("\r\n");
}
