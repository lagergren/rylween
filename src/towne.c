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
            for (i = 1, y = 0; i <= res_party_member_max(); i++) {
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
            for (i = 1, y = 0; i <= res_party_member_max(); i++) {
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

/* §17-F migration: C wrapper is a pass-through; the H/R/E/L loop,
   cost arithmetic, and all state mutations live in towne.church in
   data/world/scripts/towne.json5. */
static void church() {
    play_script("towne.church", NULL);
}
/* §17-F migration: C wrapper advances the in-game clock (tid) and
   resolves the smith's opening hours from the world data, then hands
   control to towne.smith in data/world/scripts/towne.json5. The
   script owns the closed screen, the clr_map_window + blitgfx entry
   scene, the main H/R/E/L style shop loop, the beer-offer chance
   branch, the A/W/N buy flow with its armour / weapon pagination,
   and the S sell flow with the Y/N/B bargain loop. Opening hours
   stay in world.json5 (buildings.smith.opens_at / closes_at) so
   tavern / inn / merchant can use the same pattern as they migrate.

   Bug fixed in migration: the original weapon-buy path scanned
   inventory slots 1..15 (`for (i = 1; i < 16; i++)`) to place the
   purchased item, while the armour path used `inv_add` which scans
   1..16. The DSL rewrites both routes through inv_find_empty
   (1..16). Not covered by replay fixtures today, so no
   byte-identical regression path. */
static void smith() {
    state.tid++;
    if (state.tid == 25) {
        state.tid = 1;
    }

    int open_lo = 0;
    int open_hi = 23;
    res_building_hours("smith", &open_lo, &open_hi);

    char lo_s[8], hi_s[8];
    snprintf(lo_s, sizeof lo_s, "%d", open_lo);
    snprintf(hi_s, sizeof hi_s, "%d", open_hi);
    play_script("towne.smith", (ryl_bind[]){
                                   {"open_lo", lo_s},
                                   {"open_hi", hi_s},
                                   {0},
                               });
}
/* §17-F migration: C wrapper advances the clock, resolves tavern
   opening hours from world.json5 (buildings.tavern — open 6..1, wrap
   around midnight), and dispatches to towne.tavern in
   data/world/scripts/towne.json5. The script owns the closed screen,
   the clr_map_window + blitgfx entry scene, the scruffy-offer gate
   (ryl_chance(5 + priest-charisma/10 per member), 17-case special-
   item dispatch, B/N buy loop), and the main O/S/T/R/L menu loop —
   including the tell-story flow (dead / alive, T/B story type, st
   score thresholds, liar path that exits the tavern) and the R
   rumour cap + intro switch + engine_call rumour.

   Pre-existing bug fixed in migration: the scruffy random draw was
   `ryl_random(0, 15) + 1` but the follow-up switch had 17 cases —
   case 17 (item id 111, Jazzyfoot) was never reachable. The DSL
   uses `random(0, 16) + 1` so all 17 items can appear. Rare path,
   no replay fixture. */
static void tavern() {
    state.tid++;
    if (state.tid == 25) {
        state.tid = 1;
    }

    int open_lo = 0;
    int open_hi = 23;
    res_building_hours("tavern", &open_lo, &open_hi);

    char lo_s[8], hi_s[8];
    snprintf(lo_s, sizeof lo_s, "%d", open_lo);
    snprintf(hi_s, sizeof hi_s, "%d", open_hi);
    play_script("towne.tavern", (ryl_bind[]){
                                    {"open_lo", lo_s},
                                    {"open_hi", hi_s},
                                    {0},
                                });
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
                for (i = 1; i <= res_party_member_max(); i++) {
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
                for (i = 1; i <= res_party_member_max(); i++) {
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
            for (i = 1; i <= res_party_member_max(); i++) {
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
