#include "ryl.h"
#include "battle.h"
#include "driv.h"
#include "gfx.h"
#include "primitives.h"
#include "res.h"
#include "subs.h"
#include "towne.h"
#include "ydarg.h"

static void confrontation(void);

void ydarg() {
    int32_t room[8][8];
    int32_t xl, yl, lvl, n, s, e, w, i, j, x, oc;
    char c, d;

    clr_map_window();
    blitgfx(ydargpic);
    clw();
    if (state.p[state.cpl].silverkey == 2) {
        tx0(res_text("ydarg.ruin"));
        ryl_pause();
        clr_map_window();
        update_map();
        clw();
        return;
    }
    tx0(res_text("ydarg.approach"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.jaara_form"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.jaara_speech"));
    ryl_pause();
    clw();
    if (!state.p[state.cpl].silverkey) {
        tx0(res_text("ydarg.no_key"));
        ryl_pause();
        clr_map_window();
        update_map();
        return;
    }
    tx0(res_text("ydarg.key_accepted"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.enter"));
    ryl_pause();
    clw();

    /*
		Bracelets of Thun
		Fire Shield
		Jazzy Foot
		Crown

		Monster, F{lla, Hitta Vapen, Hitta f|rem}l, Trappa upp
		4 levels. level 1 - Bracelets, Level 2 = fire shield, level 3 = foot
		level 4 = crown.

    XX XX XX XX XX

    XX XX XX XX XX

    XX XX XX XX XX

    XX XX XX XX XX

    XX XX XX XX XX

	*/

    tx0(res_text("ydarg.door_slams"));
    ryl_delay(2000);
    clw();
    lvl = 1;
    xl = 1;
    yl = 1;
    for (i = 1; i < 6; i++) {
        for (j = 1; j < 6; j++) {
            room[i][j] = ryl_random(1, 5);
        }
    }
    room[5][5] = 50;
    ryl_pause();

    while (1) {
        clw();

        switch (room[xl][yl]) {
            case 1: /* Monster */
                room[xl][yl] = 0;
                tx0(res_text("ydarg.room.monster"));
                ryl_delay(1500);
                encounter(ryl_random(60, 79), 0);
                clr_map_window();
                blitgfx(ydargpic);
                clw();
                break;

            case 2: /* Pitfall */
                room[xl][yl] = 0;
                tx("%s springs a\r\n", state.p[state.cpl].name[ryl_random(1, 4)]);
                tx("trap!!!\r\n");
                ryl_delay(1500);
                clw();
                j = 0;
                for (i = 1; i < 5; i++) {
                    if (state.p[state.cpl].cl[i] == 2) {
                        tx("%s tries to\r\n", state.p[state.cpl].name[i]);
                        tx("disarm it...\r\n\r\n");
                        ryl_delay(1500);
                        if (ryl_chance(state.p[state.cpl].wi[i])) {
                            tx0(res_text("ydarg.room.trap.disarm_succeed"));
                            ryl_delay(1000);
                            j = 1;
                        } else {
                            tx0(res_text("ydarg.room.trap.disarm_fail"));
                            ryl_delay(1000);
                            j = 0;
                        }
                        break;
                    }
                }
                if (!j) {
                    switch (ryl_random(0, 2)) {
                        case 0:
                            tx0(res_text("ydarg.room.trap.arrows"));
                            break;
                        case 1:
                            tx0(res_text("ydarg.room.trap.poison"));
                            break;
                        case 2:
                            tx0(res_text("ydarg.room.trap.shurikens"));
                            break;
                    }
                    ryl_pause();
                    for (i = 1; i < 5; i++) {
                        if (!state.p[state.cpl].dead[i]) {
                            state.p[state.cpl].hp[i] -= state.p[state.cpl].hp[i] / 6;
                        }
                    }
                }
                break;

            case 3:
                room[xl][yl] = 0;
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
                tx0(res_text("ydarg.room.treasure.find"));
                tx("%s.\r\n\r\n", object(oc));
                tx0(res_text("ydarg.room.treasure.menu"));
                c = key();
                c = toupper(c);
                if (c == 'T') {
                    for (i = 1, x = 0; i < 17; i++) {
                        if (!state.p[state.cpl].inventory[i]) {
                            state.p[state.cpl].inventory[i] = oc;
                            x = 1;
                            break;
                        }
                    }
                    if (!x) {
                        clw();
                        tx0(res_text("ydarg.room.treasure.full"));
                        ryl_delay(800);
                    } else {
                        tx0(res_text("ydarg.room.treasure.taken"));
                        ryl_delay(800);
                    }
                }
                break;

            case 50:
                clw();
                tx0(res_text("ydarg.room.stairs.intro"));
                ryl_delay(1500);
                clw();
                tx0(res_text("ydarg.room.stairs.mage_offer"));
                ryl_pause();
                while (1) {
                    clw();
                    tx0(res_text("ydarg.room.stairs.menu"));
                    while (1) {
                        d = key();
                        d = toupper(d);
                        if (d == 'T' || d == 'G' || d == 'L') {
                            break;
                        }
                    }
                    if (d == 'L') {
                        clw();
                        tx0(res_text("ydarg.room.stairs.leave"));
                        ryl_delay(1000);
                        yl--;
                        break;
                    }
                    if (d == 'T') {
                        clw();
                        tx0(res_text("ydarg.room.stairs.teleport_vortex"));
                        ryl_delay(3000);
                        tx0(res_text("ydarg.room.stairs.teleport_outside"));
                        ryl_delay(3000);
                        clr_map_window();
                        update_map();
                        clw();
                        return;
                    }
                    clw();
                    tx0(res_text("ydarg.room.stairs.give_menu"));
                    while (1) {
                        d = key();
                        if (d >= '1' && d <= '5') {
                            break;
                        }
                    }
                    j = 0;
                    switch (d - 48) {
                        case 1:
                            if (!state.p[state.cpl].fireshield) {
                                clw();
                                tx0(res_text("ydarg.room.stairs.no_have_sing"));
                                ryl_delay(1500);
                                break;
                            }
                            state.p[state.cpl].fireshield = 0;
                            j = 1;
                            break;
                        case 2:
                            if (!state.p[state.cpl].mastercrown) {
                                clw();
                                tx0(res_text("ydarg.room.stairs.no_have_sing"));
                                ryl_delay(1500);
                                break;
                            }
                            state.p[state.cpl].mastercrown = 0;
                            j = 1;
                            break;
                        case 3:
                            if (!state.p[state.cpl].jazzyfoot) {
                                clw();
                                tx0(res_text("ydarg.room.stairs.no_have_sing"));
                                ryl_delay(1500);
                                break;
                            }
                            state.p[state.cpl].jazzyfoot = 0;
                            j = 1;
                            break;
                        case 4:
                            if (!state.p[state.cpl].bracelets) {
                                clw();
                                tx0(res_text("ydarg.room.stairs.no_have_plur"));
                                ryl_delay(1500);
                                break;
                            }
                            state.p[state.cpl].bracelets = 0;
                            j = 1;
                            break;
                    }
                    if (j) {
                        clw();
                        tx0(res_text("ydarg.room.stairs.mage_grateful"));
                        ryl_pause();
                        clw();
                        xl = 1;
                        yl = 1;
                        lvl++;
                        if (lvl == 5) {
                            confrontation();
                        }
                        break;
                    }
                }
                break;
        }

        clw();
        statusline();
        tx("You are at %dx%d.\r\n\r\n", xl, yl, lvl);
        tx("You are on level %d.\r\n", lvl);
        switch (lvl) {
            case 1:
                tx0(res_text("ydarg.nav.floor_1"));
                break;
            case 2:
                tx0(res_text("ydarg.nav.floor_2"));
                break;
            case 3:
                tx0(res_text("ydarg.nav.floor_3"));
                break;
            case 4:
                tx0(res_text("ydarg.nav.floor_4"));
                break;
        }
        n = 0;
        s = 0;
        e = 0;
        w = 0;
        if (yl > 1) {
            n = 1;
        }
        if (xl > 1) {
            w = 1;
        }
        if (xl < 5) {
            e = 1;
        }
        if (yl < 5) {
            s = 1;
        }
        if (n) {
            tx0(res_text("ydarg.nav.north"));
        } else {
            tx0(res_text("ydarg.nav.no_north"));
        }

        if (e && w) {
            tx0(res_text("ydarg.nav.east_west"));
        }
        if (e && !w) {
            tx0(res_text("ydarg.nav.east"));
        }
        if (!e && w) {
            tx0(res_text("ydarg.nav.west"));
        }
        if (s) {
            tx0(res_text("ydarg.nav.south"));
        }

        c = key();
        c = toupper(c);
        if (c == '8' && n) {
            clw();
            tx0(res_text("ydarg.nav.go_north"));
            ryl_delay(300);
            clw();
            yl--;
        }
        if (c == '2' && s) {
            clw();
            tx0(res_text("ydarg.nav.go_south"));
            ryl_delay(300);
            clw();
            yl++;
        }
        if (c == '6' && e) {
            clw();
            tx0(res_text("ydarg.nav.go_east"));
            ryl_delay(300);
            clw();
            xl++;
        }
        if (c == '4' && w) {
            clw();
            tx0(res_text("ydarg.nav.go_west"));
            ryl_delay(300);
            clw();
            xl--;
        }
        if (c == '?') {
            help();
        }
        if (c == 'T') {
            armouroff();
        }
        if (c == 'P') {
            weaponoff();
        }
        if (c == 'L') {
            drop();
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
            spell(0); /* 0 means over ground */
        }
        if (c == 'V') {
            showparty();
            clw();
            tx0(res_text("ydarg.nav.view_who"));
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
        }
    }
}

static void confrontation() {
    FILE *fh;
    char str[80];
    char kl[8];
    char mstr[15];

    clw();
    tx0(res_text("ydarg.conf.trapdoor_slam"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.conf.fools"));
    ryl_pause();
    clw();
    encounter(26, 1);
    encounter(27, 1);
    encounter(28, 1);
    encounter(29, 1);
    clw();
    tx0(res_text("ydarg.conf.corpses"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.conf.impress"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.conf.sphere"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.conf.mages_pre"));
    tx("else near. %s turns\r\n", state.p[state.cpl].name[1]);
    tx0(res_text("ydarg.conf.mages_post"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.conf.curses"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.conf.trident"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.conf.commence"));
    ryl_pause();
    encounter(30, 1);
    clw();
    tx0(res_text("ydarg.conf.trident_falls"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.conf.not_over"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.conf.cloak"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.conf.dust"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.conf.congrats"));
    ryl_pause();
    clw();
    state.p[state.cpl].silverkey = 2;
    save();
    tx0(res_text("ydarg.conf.heroes"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.conf.signoff"));
    ryl_pause();
    clw();

    if (!(fh = fopen("data/heroes.txt", "r"))) {
        fh = fopen("data/heroes.txt", "w");
        snprintf(str, sizeof str, "%c\n", 12);
        fputs(str, fh);
        fputs(" +============================================================================+\n",
              fh);
        fputs(" I              ADVENTURERS OF RYLWEEN - HALL OF NATIONAL HEROES              I\n",
              fh);
        fputs(" +============================================================================+\n\n",
              fh);
        fclose(fh);
    }

    if (!(fh = fopen("data/heroes.txt", "a+"))) {
        return;
    }

    getclk(kl);

    switch (kl[2]) {
        case 1:
            strcpy(mstr, "January");
            break;
        case 2:
            strcpy(mstr, "February");
            break;
        case 3:
            strcpy(mstr, "March");
            break;
        case 4:
            strcpy(mstr, "April");
            break;
        case 5:
            strcpy(mstr, "May");
            break;
        case 6:
            strcpy(mstr, "June");
            break;
        case 7:
            strcpy(mstr, "July");
            break;
        case 8:
            strcpy(mstr, "August");
            break;
        case 9:
            strcpy(mstr, "September");
            break;
        case 10:
            strcpy(mstr, "October");
            break;
        case 11:
            strcpy(mstr, "November");
            break;
        case 12:
            strcpy(mstr, "December");
            break;
    }

    snprintf(str, sizeof str, "  %s defeated baron Ydarg %s %d, 19%d\n", state.p[state.cpl].partyname, mstr, kl[3],
             kl[1] + 80);
    fputs(str, fh);
    fclose(fh);
    cleanexit();
}
