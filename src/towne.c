#include "ryl.h"
#include "res.h"
#include "driv.h"
#include "gfx.h"
#include "interact.h"
#include "inventory.h"
#include "primitives.h"
#include "script.h"
#include "speak.h"
#include "subs.h"
#include "towne.h"
#include "ydarg.h"

static void church(void);
static void hut(void);
static void inn(void);
static void merchant(void);
static int32_t obcost(int32_t);
static void smith(void);
static void tavern(void);

void rumour() {
    char loc[40];
    int32_t pick = ryl_random(0, 32);

    /* Most rumours are static paragraphs; a handful (12, 13, 25, 27)
       fold in an NPC name / town-or-coord loc via tx_fmt. */
    switch (pick) {
        case 12:
            tx_fmt("towne.rumour.r12",
                   (ryl_bind[]){{"name1", res_enum("name", 1)}, {0}});
            break;
        case 13:
            tx_fmt("towne.rumour.r13",
                   (ryl_bind[]){{"name2", res_enum("name", 2)}, {0}});
            break;
        case 25:
            if (state.npc[15].towne) {
                snprintf(loc, sizeof loc, "in %s", res_town_name(state.npc[15].towne));
            } else {
                snprintf(loc, sizeof loc, "at %dx%d", state.npc[15].x, state.npc[15].y);
            }
            tx_fmt("towne.rumour.r25",
                   (ryl_bind[]){
                       {"name15", res_enum("name", 15)},
                       {"loc15", loc},
                       {0},
                   });
            break;
        case 27:
            if (state.npc[13].towne) {
                snprintf(loc, sizeof loc, "in %s", res_town_name(state.npc[13].towne));
            } else {
                snprintf(loc, sizeof loc, "at %dx%d", state.npc[13].x, state.npc[13].y);
            }
            tx_fmt("towne.rumour.r27",
                   (ryl_bind[]){
                       {"name13", res_enum("name", 13)},
                       {"loc13", loc},
                       {0},
                   });
            break;
        default: {
            char id[32];
            snprintf(id, sizeof id, "towne.rumour.r%d", pick);
            tx0(res_text(id));
            break;
        }
    }
    tx("\r\n");
}
int32_t obdamage(int32_t nr) {
    const ryl_item *it = res_item(nr);
    return it ? it->damage : 0;
}
static int32_t obcost(int32_t nr) {
    const ryl_item *it = res_item(nr);
    return it ? it->cost : 0;
}
char *object(int32_t nr) {
    const ryl_item *it = res_item(nr);
    return it ? (char *)it->name : "-";
}

void enter() {
    char c, twnname[80];
    int32_t i, x, y, tax;

    //printf("%dx%d - map: %c\n",p[cpl].x,p[cpl].y,map[p[cpl].y][p[cpl].x]);

    if (state.map[state.p[state.cpl].y][state.p[state.cpl].x] != '0' && state.map[state.p[state.cpl].y][state.p[state.cpl].x] != '1' &&
        state.map[state.p[state.cpl].y][state.p[state.cpl].x] != '2') {
        clw();
        tx0(res_text("towne.enter.nothing"));
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
    strncpy(twnname, res_town_name(state.twn), sizeof twnname - 1);
    twnname[sizeof twnname - 1] = '\0';

    /* Har skall checkas om det {r en s{rskild stad, som tex Ydarg */

    /* Det skall det inte alls!!! */

    if (state.p[state.cpl].lv[1] >= 5) {
        char pct[8], amount[16];

        snprintf(pct, sizeof pct, "%d", tax);
        snprintf(amount, sizeof amount, "%d", (state.p[state.cpl].gold * tax) / 100);
        clw();
        tx_fmt("towne.enter.welcome",
               (ryl_bind[]){{"twn", twnname}, {0}});
        tx_fmt("towne.enter.tax",
               (ryl_bind[]){{"pct", pct}, {"amount", amount}, {0}});
        if (ryl_menu("towne.enter.tax_menu",
                     (ryl_menu_opt[]){{'P', 1}, {0, 0}}) == 1) {
            clw();
            tx0(res_text("towne.enter.thanks"));
            ryl_delay(2000);
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
        tx_fmt(state.twn == 3 ? "towne.enter.city" : "towne.enter.village",
               (ryl_bind[]){{"twn", twnname}, {0}});
        tx0(res_text("towne.enter.divider"));
        tx0(res_text("towne.enter.menu_core"));
        if (state.twn != 1 && state.twn != 3) {
            tx0(res_text("towne.enter.menu_smith"));
        }
        if (state.twn == 1 || state.twn == 3) {
            tx0(res_text("towne.enter.menu_smith_ch"));
        }
        tx0(res_text("towne.enter.menu_leave"));

        tx0(res_text("towne.enter.prompt"));

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
            x = inv_find_in_range(1, 19) != 0;
            for (i = 1, y = 0; i < 5; i++) {
                if (!state.p[state.cpl].dead[i] && !state.p[state.cpl].wp[i]) {
                    y = 1;
                }
            }
            if (x && y) {
                clw();
                tx0(res_text("towne.enter.weapons_q"));
                tx0(res_text("common.yes"));
                tx0(res_text("common.no"));
                c = key();
                c = toupper(c);
                if (c == 'Y') {
                    weaponr();
                }
            }

            x = inv_find_in_range(20, 30) != 0;
            for (i = 1, y = 0; i < 5; i++) {
                if (!state.p[state.cpl].dead[i] && !state.p[state.cpl].ar[i]) {
                    y = 1;
                }
            }
            if (x && y) {
                clw();
                tx0(res_text("towne.enter.armours_q"));
                tx0(res_text("common.yes"));
                tx0(res_text("common.no"));
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

/* §17-F migration: wrapper owns the static entry scene (clr_map_window
   + blitgfx + entry text + fixed 600ms hold). The interactive
   greeter/menu loop is `towne.hut` in data/world/scripts/towne.json5;
   play_script receives `vem` (which NPC occupies the square) and
   `npc_job` (its job id) as int bindings — the DSL resolves the
   display strings via engine_bind res_enum on each iteration. */
static void hut() {
    int32_t i, vem = 0;
    int nmax = res_npc_slot_max();

    for (i = 1; i <= nmax; i++) {
        if (state.npc[i].x == state.p[state.cpl].x && state.npc[i].y == state.p[state.cpl].y) {
            vem = i;
        }
    }
    if (!vem) {
        return; /* Error */
    }

    clr_map_window();
    blitgfx(hutpic);
    clw();
    tx0(res_text("towne.hut.enter"));
    ryl_delay(600);

    char vem_s[8], job_s[8];
    snprintf(vem_s, sizeof vem_s, "%d", vem);
    snprintf(job_s, sizeof job_s, "%d", state.npc[vem].job);
    play_script("towne.hut", (ryl_bind[]){
                                 {"vem", vem_s},
                                 {"npc_job", job_s},
                                 {0},
                             });
}

static void church() {
    char c, d;
    int32_t cost;

    clr_map_window();
    blitgfx(churchpic);

    while (1) {
        clw();
        statusline();
        tx0(res_text("towne.church.intro"));
        c = (char)ryl_menu("towne.church.menu",
                           (ryl_menu_opt[]){
                               {'H', 'H'},
                               {'R', 'R'},
                               {'E', 'E'},
                               {'L', 'L'},
                               {0, 0},
                           });

        if (c == 'L') {
            clw();
            clr_map_window();
            return;
        }

        if (c == 'E') {
            clw();
            tx0(res_text("towne.church.who"));
            d = key();
            d = toupper(d);
            if (d >= '1' && d <= '4') {
                if (state.p[state.cpl].cl[d - 48] != 3 && state.p[state.cpl].cl[d - 48] != 4) {
                    clw();
                    tx0(res_text("towne.church.not_spellcaster"));
                    ryl_delay(1000);
                } else /* Spellcaster */
                {
                    if (state.p[state.cpl].ma[d - 48] == state.p[state.cpl].wi[d - 48]) {
                        clw();
                        tx0(res_text("towne.church.no_need_sp"));
                        ryl_delay(2000);
                    } else {
                        char cost_s[16];

                        clw();
                        cost = state.p[state.cpl].lv[d - 48] * (state.p[state.cpl].wi[d - 48] - state.p[state.cpl].ma[d - 48]) * 2;
                        if (!cost) {
                            cost = state.p[state.cpl].lv[d - 48] * 2;
                        }
                        snprintf(cost_s, sizeof cost_s, "%d", cost);
                        tx_fmt("towne.church.cost2",
                               (ryl_bind[]){{"cost", cost_s}, {0}});
                        if (ryl_menu("towne.church.pn_menu",
                                     (ryl_menu_opt[]){{'P', 1}, {0, 0}}) == 1) {
                            clw();
                            if (state.p[state.cpl].gold < cost) {
                                tx0(res_text("towne.church.no_gold"));
                                ryl_delay(1000);
                            } else {
                                state.p[state.cpl].gold -= cost;
                                state.p[state.cpl].ma[d - 48] = state.p[state.cpl].wi[d - 48];
                                clw();
                                tx0(res_text("towne.church.restored"));
                                ryl_delay(1000);
                            }
                        }
                    }
                }
            }
        }

        if (c == 'R') {
            clw();
            tx0(res_text("towne.church.who"));
            d = key();
            d = toupper(d);
            if (d >= '1' && d <= '4') {
                if (!state.p[state.cpl].dead[d - 48]) {
                    clw();
                    tx_fmt("towne.church.alive",
                           (ryl_bind[]){{"pro_sub", pronoun(d - 48, 1)}, {0}});
                    ryl_delay(1200);
                } else {
                    char cost_s[16];

                    clw();
                    snprintf(cost_s, sizeof cost_s, "%d", 650 * state.p[state.cpl].lv[d - 48]);
                    tx_fmt("towne.church.cost2",
                           (ryl_bind[]){{"cost", cost_s}, {0}});
                    if (ryl_menu("towne.church.pn_menu",
                                 (ryl_menu_opt[]){{'P', 1}, {0, 0}}) == 1) {
                        clw();
                        if (state.p[state.cpl].gold < 650 * state.p[state.cpl].lv[d - 48]) {
                            tx0(res_text("towne.church.no_gold"));
                            ryl_delay(1000);
                        } else {
                            state.p[state.cpl].gold -= 650 * state.p[state.cpl].lv[d - 48];
                            state.p[state.cpl].hp[d - 48] = 1;
                            state.p[state.cpl].dead[d - 48] = 0;
                            state.p[state.cpl].pl++;
                            clw();
                            tx_fmt("towne.church.raised",
                                   (ryl_bind[]){{"pro_obj", pronoun(d - 48, 2)}, {0}});
                            ryl_delay(1000);
                        }
                    }
                }
            }
        }
        if (c == 'H') {
            clw();
            tx0(res_text("towne.church.who"));
            d = key();
            d = toupper(d);
            if (d >= '1' && d <= '4') {
                if (state.p[state.cpl].dead[d - 48]) {
                    clw();
                    tx0(res_text("towne.church.corpse"));
                    ryl_delay(1200);
                    return;
                }
                if (state.p[state.cpl].hp[d - 48] == state.p[state.cpl].h[d - 48]) {
                    clw();
                    tx_fmt("towne.church.no_healing",
                           (ryl_bind[]){{"pro_sub", pronoun(d - 48, 1)}, {0}});
                    ryl_delay(1000);
                } else {
                    char cost_s[16];

                    cost = state.p[state.cpl].h[d - 48] - state.p[state.cpl].hp[d - 48];
                    cost *= 7;
                    clw();
                    snprintf(cost_s, sizeof cost_s, "%d", cost);
                    tx_fmt("towne.church.cost",
                           (ryl_bind[]){{"cost", cost_s}, {0}});
                    if (ryl_menu("towne.church.pn_menu",
                                 (ryl_menu_opt[]){{'P', 1}, {0, 0}}) == 1) {
                        if (state.p[state.cpl].gold < cost) {
                            tx0(res_text("towne.church.no_gold2"));
                            ryl_delay(1000);
                        } else {
                            clw();
                            state.p[state.cpl].gold -= cost;
                            tx_fmt("towne.church.healed",
                                   (ryl_bind[]){{"pro_obj", pronoun(d - 48, 2)}, {0}});
                            ryl_delay(1000);
                            state.p[state.cpl].hp[d - 48] = state.p[state.cpl].h[d - 48];
                        }
                    }
                }
            }
        }
    }
}
static void smith() {
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
        tx0(res_text("towne.smith.closed"));
        ryl_delay(1000);
        return;
    }

    clr_map_window();
    blitgfx(smithpic);

    while (1) {
        clw();
        statusline();
        tx0(res_text("towne.smith.intro"));

        if (ryl_chance(9)) {
            tx0(res_text("towne.smith.beer_offer"));
            c = (char)ryl_menu("towne.smith.beer_menu",
                               (ryl_menu_opt[]){{'A', 'A'}, {'D', 'D'}, {0, 'D'}});
            if (c == 'A') {
                clw();
                tx0(res_text("towne.smith.accept_1"));
                ryl_delay(2000);
                tx0(res_text("towne.smith.accept_2"));
                ryl_delay(2000);
                tx0(res_text("towne.smith.accept_3"));
                ryl_delay(2000);
                clw();
                rumour();
                ryl_pause();
            } else {
                clw();
                tx0(res_text("towne.smith.decline"));
                ryl_pause();
            }
        } else {
            c = (char)ryl_menu("towne.smith.shop_menu",
                               (ryl_menu_opt[]){
                                   {'B', 'B'},
                                   {'S', 'S'},
                                   {'L', 'L'},
                                   {0, 0},
                               });
            if (c == 'L') {
                clw();
                tx0(res_text("towne.smith.bye"));
                ryl_delay(1000);
                clr_map_window();
                return;
            }

            if (c == 'B') {
                clw();
                d = (char)ryl_menu("towne.smith.buy_menu",
                                   (ryl_menu_opt[]){
                                       {'A', 'A'},
                                       {'W', 'W'},
                                       {'N', 'N'},
                                       {0, 0},
                                   });

                if (d == 'A') {
                    char p20[8], p21[8], p22[8], p23[8], p24[8], p25[8];

                    snprintf(p20, sizeof p20, "%4d", obcost(20));
                    snprintf(p21, sizeof p21, "%4d", obcost(21));
                    snprintf(p22, sizeof p22, "%4d", obcost(22));
                    snprintf(p23, sizeof p23, "%4d", obcost(23));
                    snprintf(p24, sizeof p24, "%4d", obcost(24));
                    snprintf(p25, sizeof p25, "%4d", obcost(25));
                    clw();
                    tx0(res_text("towne.smith.we_have"));
                    tx_fmt("towne.smith.armour_menu",
                           (ryl_bind[]){
                               {"p20", p20},
                               {"p21", p21},
                               {"p22", p22},
                               {"p23", p23},
                               {"p24", p24},
                               {"p25", p25},
                               {0},
                           });
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
                        tx0(res_text("towne.smith.too_expensive"));
                        ryl_delay(1000);
                    } else {
                        clw();
                        x = inv_add(oc);
                        if (!x) {
                            tx0(res_text("towne.smith.cant_carry"));
                            ryl_delay(1000);
                        } else {
                            state.p[state.cpl].gold -= pr;
                            tx0(res_text("towne.smith.deal"));
                            ryl_delay(1000);
                        }
                    }
                }

                if (d == 'W') {
                    while (1) {
                        clw();
                        tx0(res_text("towne.smith.we_have"));
                        if (menu == 1) {
                            char p1[8], p2[8], p3[8], p4[8], p9[8], p11[8];

                            snprintf(p1, sizeof p1, "%4d", obcost(1));
                            snprintf(p2, sizeof p2, "%4d", obcost(2));
                            snprintf(p3, sizeof p3, "%4d", obcost(3));
                            snprintf(p4, sizeof p4, "%4d", obcost(4));
                            snprintf(p9, sizeof p9, "%4d", obcost(9));
                            snprintf(p11, sizeof p11, "%4d", obcost(11));
                            tx_fmt("towne.smith.weapon_page1",
                                   (ryl_bind[]){
                                       {"p1", p1},
                                       {"p2", p2},
                                       {"p3", p3},
                                       {"p4", p4},
                                       {"p9", p9},
                                       {"p11", p11},
                                       {0},
                                   });
                        }
                        if (menu == 2) {
                            char p7[8], p14[8], p10[8], p8[8], p15[8], p16[8];

                            snprintf(p7, sizeof p7, "%4d", obcost(7));
                            snprintf(p14, sizeof p14, "%4d", obcost(14));
                            snprintf(p10, sizeof p10, "%4d", obcost(10));
                            snprintf(p8, sizeof p8, "%4d", obcost(8));
                            snprintf(p15, sizeof p15, "%4d", obcost(15));
                            snprintf(p16, sizeof p16, "%4d", obcost(16));
                            tx_fmt("towne.smith.weapon_page2",
                                   (ryl_bind[]){
                                       {"p7", p7},
                                       {"p14", p14},
                                       {"p10", p10},
                                       {"p8", p8},
                                       {"p15", p15},
                                       {"p16", p16},
                                       {0},
                                   });
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
                        tx0(res_text("towne.smith.too_expensive"));
                        ryl_delay(1000);
                    } else {
                        clw();
                        for (i = 1, x = 0; i < 16; i++) {
                            if (state.p[state.cpl].inventory[i] == 0) {
                                x = i;
                                break;
                            }
                        }
                        if (!x) {
                            tx0(res_text("towne.smith.cant_carry"));
                            ryl_delay(1000);
                        } else {
                            state.p[state.cpl].inventory[x] = oc;
                            state.p[state.cpl].gold -= pr;
                            tx0(res_text("towne.smith.deal"));
                            ryl_delay(1000);
                        }
                    }
                }
            }

            if (c == 'S') {
                clw();
                tx0(res_text("towne.smith.sell_prompt"));
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
                        tx0(res_text("towne.smith.not_w_or_a"));
                        ryl_delay(1000);
                    } else {
                        while (1) {
                            char pr_s[16];

                            clw();
                            snprintf(pr_s, sizeof pr_s, "%d", pr);
                            tx_fmt("towne.smith.offer",
                                   (ryl_bind[]){{"pr", pr_s}, {0}});
                            tx0(res_text("common.yes"));
                            tx0(res_text("common.no"));
                            tx0(res_text("towne.smith.bargain_opt"));
                            b = key();
                            b = toupper(b);
                            if (b == 'Y') {
                                state.p[state.cpl].inventory[d - 64] = 0;
                                state.p[state.cpl].gold += pr;
                                pr = 0;
                                break;
                            }
                            if (b == 'B') {
                                i = ryl_random(0, 99);
                                if (i > 90) {
                                    pr += pr / 4;
                                }
                                if (i > 75) {
                                    pr += pr / 6;
                                }
                                if (i < 76) {
                                    clw();
                                    tx0(res_text("towne.smith.no_thanks"));
                                    ryl_delay(1000);
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
static void tavern() {
    char c, d, b;
    int32_t i, x, st, xs;
    int32_t pr;
    int32_t rumours = 0;

    state.tid++;
    if (state.tid == 25) {
        state.tid = 1;
    }

    if (state.tid < 6 && state.tid > 1) {
        clw();
        tx0(res_text("towne.tavern.closed"));
        ryl_delay(1000);
        return;
    }

    clr_map_window();
    blitgfx(tavernpic);

    for (i = 1, x = 0; i < 5; i++) {
        if (state.p[state.cpl].cl[i] == 5) {
            x += state.p[state.cpl].ch[i] / 10;
        }
    }

    if (ryl_chance(5 + x)) {
        clw();
        tx0(res_text("towne.tavern.scruffy_1"));
        ryl_delay(3000);
        tx0(res_text("towne.tavern.scruffy_2"));
        ryl_delay(3000);
        clw();
        /* 15,17,26,27,38,41,100,101,102,103,104,106,107,108,109,110,111 */
        i = ryl_random(0, 15);
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
        {
            char price_s[16];

            snprintf(price_s, sizeof price_s, "%d", obcost(i));
            tx_fmt("towne.tavern.scruffy_offer",
                   (ryl_bind[]){
                       {"item", object(i)},
                       {"price", price_s},
                       {0},
                   });
        }
        tx0(res_text("towne.tavern.buy_nothanks"));

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
                tx0(res_text("towne.tavern.scruffy_broke"));
                ryl_delay(800);
            } else {
                x = inv_add(i) != 0;
                if (x) {
                    tx0(res_text("towne.tavern.scruffy_deal"));
                    state.p[state.cpl].gold -= obcost(i);
                    ryl_delay(800);
                } else {
                    tx0(res_text("towne.tavern.scruffy_full"));
                    ryl_delay(1000);
                }
            }
        }
        if (c == 'N') {
            tx0(res_text("towne.tavern.scruffy_no"));
            ryl_delay(1000);
        }
    }
    while (1) {
        clw();
        tx_fmt("towne.tavern.greeter",
               (ryl_bind[]){
                   {"name", res_enum("name", 28 + state.twn)},
                   {0},
               });
        c = (char)ryl_menu("towne.tavern.main_menu",
                           (ryl_menu_opt[]){
                               {'O', 'O'},
                               {'S', 'S'},
                               {'T', 'T'},
                               {'R', 'R'},
                               {'L', 'L'},
                               {0, 0},
                           });
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
            tx0(res_text("towne.tavern.story_who"));
            d = key();
            d = toupper(d);
            if (d >= '1' && d <= '4') {
                if (state.p[state.cpl].dead[d - 48]) {
                    clw();
                    tx0(res_text("towne.tavern.dead_story"));
                    ryl_delay(2000);
                } else {
                    clw();
                    tx_fmt("towne.tavern.stands_up",
                           (ryl_bind[]){
                               {"who", state.p[state.cpl].name[d - 48]},
                               {0},
                           });
                    ryl_delay(2000);
                    tx0(res_text("towne.tavern.yeah"));
                    ryl_delay(2000);
                    b = (char)ryl_menu("towne.tavern.story_menu",
                                       (ryl_menu_opt[]){
                                           {'T', 'T'},
                                           {'B', 'B'},
                                           {0, 0},
                                       });
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
                    xs = ryl_random(1, 150);
                    st += xs;
                    if (b == 'B') {
                        if (ryl_random(0, 99) > state.p[state.cpl].ch[d - 48]) {
                            clw();
                            tx0(res_text("towne.tavern.liar_kicked"));
                            st = 0;
                            ryl_delay(2000);
                            tx0(res_text("towne.tavern.thrown_out"));
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
                        tx0(res_text("towne.tavern.st_superb"));
                        ryl_pause();
                        state.p[state.cpl].ch[d - 48] += 3;
                    }
                    if (st > 300 && st < 401) {
                        tx0(res_text("towne.tavern.st_vgood"));
                        ryl_pause();
                        state.p[state.cpl].ch[d - 48]++;
                    }
                    if (st > 150 && st < 301) {
                        tx0(res_text("towne.tavern.st_nice"));
                        ryl_pause();
                    }
                    if (st > 100 && st < 151) {
                        tx0(res_text("towne.tavern.st_bored"));
                        ryl_pause();
                        if (state.p[state.cpl].ch[d - 48]) {
                            state.p[state.cpl].ch[d - 48]--;
                        }
                    }
                    if (st < 101) {
                        tx0(res_text("towne.tavern.st_asleep"));
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
                tx0(res_text("towne.tavern.quiet"));
                ryl_pause();
            } else {
                switch (ryl_random(0, 4)) {
                    case 0:
                        tx0(res_text("towne.tavern.rum_old"));
                        break;
                    case 1:
                        tx0(res_text("towne.tavern.rum_bard"));
                        break;
                    case 2:
                        tx0(res_text("towne.tavern.rum_halfling"));
                        break;
                    case 3:
                        tx0(res_text("towne.tavern.rum_keeper"));
                        break;
                    case 4:
                        tx0(res_text("towne.tavern.rum_bargirl"));
                        break;
                }
                tx("\r\n");
                rumour();
                ryl_pause();
            }
        }
        if (c == 'O') {
            clw();
            tx0(res_text("towne.tavern.order_header"));
            tx0(res_text("towne.tavern.order_menu"));
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
                    tx0(res_text("towne.tavern.no_drink"));
                    ryl_delay(1000);
                } else {
                    clw();
                    if (d != 'D') {
                        tx0(res_text("towne.tavern.aah"));
                        ryl_delay(1000);
                    } else {
                        tx0(res_text("towne.tavern.laughed_at"));
                        ryl_delay(2000);
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
static void inn() {
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
        tx_fmt("towne.inn.greeter",
               (ryl_bind[]){
                   {"name", res_enum("name", 24 + state.twn)},
                   {0},
               });
        tx0(res_text("common.yes"));
        tx0(res_text("common.no"));
        c = key();
        c = toupper(c);
        if (c == 'Y') {
            if (state.p[state.cpl].gold < 15 * state.p[state.cpl].lv[1] * state.p[state.cpl].lv[1]) {
                char cost_s[16];

                clw();
                snprintf(cost_s, sizeof cost_s, "%d",
                         15 * state.p[state.cpl].lv[1] * state.p[state.cpl].lv[1]);
                tx_fmt("towne.inn.supper_cost",
                       (ryl_bind[]){{"cost", cost_s}, {0}});
                ryl_delay(1000);
            } else {
                state.p[state.cpl].gold -= 15 * state.p[state.cpl].lv[1] * state.p[state.cpl].lv[1];
                clw();
                tx0(res_text("towne.inn.supper_served"));
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
        tx0(res_text("towne.inn.drink_q"));
        tx0(res_text("common.yes"));
        tx0(res_text("common.no"));
        c = key();
        c = toupper(c);
        if (c == 'Y') {
            clw();
            tx0(res_text("towne.inn.drink_menu"));
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
                tx0(res_text("towne.tavern.no_drink"));
                ryl_delay(1000);
            } else {
                state.p[state.cpl].gold -= pr;
                clw();
                if (d != 'E') {
                    tx0(res_text("towne.inn.ahh"));
                } else {
                    tx0(res_text("towne.inn.giggle"));
                }
                ryl_delay(1000);
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
        tx0(res_text("towne.inn.lodging_q"));
        tx0(res_text("common.yes"));
        tx0(res_text("common.no"));
        c = key();
        c = toupper(c);
        if (c == 'Y') {
            char cost_s[16];

            clw();
            snprintf(cost_s, sizeof cost_s, "%d",
                     state.p[state.cpl].lv[1] * 15 * state.p[state.cpl].lv[1]);
            tx_fmt("towne.inn.room_cost",
                   (ryl_bind[]){{"cost", cost_s}, {0}});
            d = (char)ryl_menu("towne.inn.room_menu",
                               (ryl_menu_opt[]){{'T', 'T'}, {'L', 'L'}, {0, 'L'}});
            if (d != 'T') {
                clw();
                clr_map_window();
                return;
            }
            if (state.p[state.cpl].gold < state.p[state.cpl].lv[1] * 15 * state.p[state.cpl].lv[1]) {
                clw();
                tx0(res_text("towne.inn.no_gold_kick"));
                ryl_delay(2000);
                clr_map_window();
                return;
            }
            state.p[state.cpl].gold -= state.p[state.cpl].lv[1] * 15 * state.p[state.cpl].lv[1];
            clw();
            tx0(res_text("towne.inn.bedtime"));
            ryl_delay(1000);
            tx0(res_text("towne.inn.zzz"));
            state.hours_pigg = 14;
            ryl_delay(1500);
            tx0(res_text("towne.inn.morning"));
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
            tx0(res_text("towne.inn.leaves"));
            c = (char)ryl_menu("towne.inn.chit_menu",
                               (ryl_menu_opt[]){{'C', 'C'}, {'L', 'L'}, {0, 'L'}});

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
static void merchant() /* HOS THE MERCHANT */
{
    char c, d, f;
    int32_t x, y, oc, pr;
    char str[10];

    state.tid++;
    if (state.tid == 25) {
        state.tid = 1;
    }

    if (state.tid > 18 || state.tid < 6) {
        clw();
        tx0(res_text("towne.merchant.closed"));
        ryl_delay(1000);
        return;
    }

    clr_map_window();
    blitgfx(merchantpic);

    while (1) {
        clw();
        tx0(res_text("towne.merchant.intro"));
        c = (char)ryl_menu("towne.merchant.menu",
                           (ryl_menu_opt[]){
                               {'B', 'B'},
                               {'S', 'S'},
                               {'L', 'L'},
                               {0, 0},
                           });

        if (c == 'L') {
            clw();
            tx0(res_text("towne.merchant.bye"));
            ryl_delay(1000);
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
                    tx0(res_text("towne.merchant.wrong_kind"));
                    ryl_delay(1000);
                } else {
                    char pr_s[16];

                    clw();
                    snprintf(pr_s, sizeof pr_s, "%d", obcost(oc) / 2);
                    tx_fmt("towne.merchant.sell_offer",
                           (ryl_bind[]){{"pr", pr_s}, {0}});
                    tx0(res_text("common.yes"));
                    tx0(res_text("common.no"));
                    f = key();
                    f = toupper(f);
                    if (f == 'Y') {
                        tx0(res_text("towne.merchant.sold"));
                        ryl_delay(1000);
                        state.p[state.cpl].gold += (obcost(oc) / 2);
                        state.p[state.cpl].inventory[d - 64] = 0;
                        statusline();
                    }
                }
            }
        }

        if (c == 'B') {
            while (1) {
                char food_s[8], potion_s[16];

                clw();
                statusline();
                snprintf(food_s, sizeof food_s, "%2d", state.p[state.cpl].lv[1] + 1);
                snprintf(potion_s, sizeof potion_s, "%d",
                         obcost(36) + state.p[state.cpl].lv[1] * 3);
                tx_fmt("towne.merchant.buy_menu",
                       (ryl_bind[]){
                           {"food_price", food_s},
                           {"potion_price", potion_s},
                           {0},
                       });
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
                        tx0(res_text("towne.merchant.desc.torch"));
                        break;
                    case 32:
                        tx0(res_text("towne.merchant.desc.food"));
                        break;
                    case 33:
                        tx0(res_text("towne.merchant.desc.blanket"));
                        break;
                    case 34:
                        tx0(res_text("towne.merchant.desc.tent"));
                        break;
                    case 35:
                        tx0(res_text("towne.merchant.desc.rope"));
                        break;
                    case 36:
                        tx0(res_text("towne.merchant.desc.potion"));
                        break;
                    case 47:
                        tx0(res_text("towne.merchant.desc.donkey"));
                        break;
                }
                tx("\r\n");
                if (oc == 31 || oc == 32) {
                    char pr_s[16];

                    pr = obcost(oc);
                    if (oc == 32) {
                        pr = 0;
                        pr += (state.p[state.cpl].lv[1] + 1);
                    }
                    snprintf(pr_s, sizeof pr_s, "%d", pr);
                    tx_fmt("towne.merchant.each",
                           (ryl_bind[]){{"pr", pr_s}, {0}});
                    tx0(res_text("towne.merchant.how_many"));
                    getstr(5, str, sizeof str);
                    x = atol(str);
                    if (oc == 31) {
                        if (x + state.p[state.cpl].torches > 80) {
                            clw();
                            tx0(res_text("towne.merchant.too_many_torches"));
                            ryl_delay(1000);
                            x = 80 - state.p[state.cpl].torches;
                        }
                    }
                    if (oc == 32) {
                        y = inv_count_in_range(47, 47);
                        // 150 + y*150 = 150(y + 1)
                        if (x + state.p[state.cpl].food > (150 + y * 150)) {
                            char cap_s[16];

                            clw();
                            snprintf(cap_s, sizeof cap_s, "%d", 150 + y * 150);
                            tx_fmt("towne.merchant.too_many_food",
                                   (ryl_bind[]){{"cap", cap_s}, {0}});
                            ryl_delay(1000);
                            x = (150 + y * 150) - state.p[state.cpl].food;
                        }
                    }
                    if (x < 0) {
                        x = 0;
                    }
                    if (state.p[state.cpl].gold < pr * x) {
                        clw();
                        tx0(res_text("towne.merchant.too_expensive"));
                        ryl_delay(1000);
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
                        ryl_delay(1000);
                    }
                } else {
                    char pr_s[16];

                    pr = obcost(oc);
                    if (oc == 36) {
                        pr += state.p[state.cpl].lv[1] * 3;
                    }
                    snprintf(pr_s, sizeof pr_s, "%d", pr);
                    tx_fmt("towne.merchant.price",
                           (ryl_bind[]){{"pr", pr_s}, {0}});
                    if (ryl_menu("towne.merchant.pn_menu",
                                 (ryl_menu_opt[]){{'P', 'P'}, {'N', 'N'}, {0, 'N'}}) == 'P') {
                        if (state.p[state.cpl].gold < pr) {
                            clw();
                            tx0(res_text("towne.merchant.too_expensive"));
                            ryl_delay(1000);
                        } else {
                            x = inv_add(oc);
                            if (!x) {
                                tx0(res_text("towne.merchant.cant_carry"));
                                ryl_delay(1000);
                            } else {
                                state.p[state.cpl].gold -= pr;
                                clw();
                                tx0(res_text("towne.merchant.deal"));
                                ryl_delay(1000);
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
