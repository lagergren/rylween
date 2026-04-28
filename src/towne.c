#include "ryl.h"
#include "res.h"
#include "driv.h"
#include "gfx.h"
#include "inventory.h"
#include "missions.h"
#include "party.h"
#include "primitives.h"
#include "script.h"
#include "subs.h"
#include "towne.h"
#include "wld.h"

static void church(void);
static void hut(void);
static void inn(void);
static void merchant(void);
static void smith(void);
static void tavern(void);

/* The rumour id roll, the four bound arms (r12 / r13 / r25 / r27),
   and the default static-text branch all live in
   data/world/scripts/towne.json5's `rumour` script; engine_int
   `rumour_roll` handles the ryl_random + RYL_REPLAY_RUMOUR override.
   The trailing tx("\r\n") here matches the legacy separator that
   smith's beer-accept and tavern's (R)umours flow expected after
   each call. */
void rumour() {
    play_script("towne.rumour", NULL);
    tx("\r\n");
}
int32_t obdamage(int32_t nr) {
    const ryl_item *it = res_item(nr);
    return it ? it->damage : 0;
}
char *object(int32_t nr) {
    const ryl_item *it = res_item(nr);
    return it ? (char *)it->name : "-";
}

/* Post-shop exit flow: offer to ready any weapon / armor the party
   is carrying before returning to the wilderness map. Called once
   from enter()'s L-arm; weapon range is item ids 1..19, armor is
   20..30. Prompt skips entirely when the party has none carried or
   every member already has one equipped. */
static void enter_ready_prompt(void) {
    int32_t x, y;

    x = inv_find_in_range(1, 19) != 0;
    y = 0;
    for_each_party_member(i) {
        pm_t pm = pm_cur(i);
        if (!pm_dead(pm) && !pm_weapon(pm)) {
            y = 1;
        }
    }
    if (x && y) {
        clw();
        tx0(res_text("towne.enter.weapons_q"));
        tx0(res_text("common.yes"));
        tx0(res_text("common.no"));
        if (toupper(key()) == 'Y') {
            weapon_ready();
        }
    }

    x = inv_find_in_range(20, 30) != 0;
    y = 0;
    for_each_party_member(i) {
        pm_t pm = pm_cur(i);
        if (!pm_dead(pm) && !pm_armor(pm)) {
            y = 1;
        }
    }
    if (x && y) {
        clw();
        tx0(res_text("towne.enter.armors_q"));
        tx0(res_text("common.yes"));
        tx0(res_text("common.no"));
        if (toupper(key()) == 'Y') {
            armor_ready();
        }
    }
}

/* Tax preamble for high-level parties. Split out so enter() reads as
   a dispatcher. Returns 1 if the party paid (or wasn't prompted),
   0 if they refused and should bounce back to the wilderness. */
static int enter_tax_preamble(const char *twnname) {
    if (pa_first_level() < 5) {
        return 1;
    }
    int32_t tax = res_town_tax(state.twn);
    int32_t amount = (pa_gold() * tax) / 100;
    char pct_s[8], amount_s[16];

    snprintf(pct_s, sizeof pct_s, "%d", tax);
    snprintf(amount_s, sizeof amount_s, "%d", amount);
    clw();
    tx_fmt("towne.enter.welcome",
           (ryl_bind[]){{"twn", twnname}, {0}});
    tx_fmt("towne.enter.tax",
           (ryl_bind[]){{"pct", pct_s}, {"amount", amount_s}, {0}});
    if (ryl_menu("towne.enter.tax_menu",
                 (ryl_menu_opt[]){{'P', 1}, {0, 0}}) != 1) {
        return 0;
    }
    clw();
    tx0(res_text("towne.enter.thanks"));
    ryl_delay(2000);
    pa_add_gold(-amount);
    return 1;
}

/* Town-menu keystroke dispatcher. Walks the data/world.json5
   `town_keys` table in declared order; the first entry whose key
   matches (table holds the upper-case form, caller pre-uppercases)
   and whose optional `cond` is satisfied wins. `cond` is currently
   limited to the per-town `has_church` bool — extending it means
   adding a branch here. Returns 1 when the leave_town arm fired
   (caller should drop out of the menu loop), 0 otherwise (no match
   or a re-entrant action that returns control to the menu). */
static int town_dispatch_key(char c, int has_church) {
    int n = res_town_keys_count();
    for (int i = 0; i < n; i++) {
        char ekey;
        const char *cond = NULL;
        const char *action = NULL;
        if (!res_town_key_at(i, &ekey, &cond, &action)) {
            continue;
        }
        if (c != ekey) {
            continue;
        }
        if (cond) {
            if (strcmp(cond, "has_church") == 0) {
                if (!has_church) {
                    continue;
                }
            } else {
                continue;
            }
        }
        if (strcmp(action, "enter_church") == 0) {
            church();
        } else if (strcmp(action, "enter_smith") == 0) {
            smith();
        } else if (strcmp(action, "enter_inn") == 0) {
            inn();
        } else if (strcmp(action, "enter_merchant") == 0) {
            merchant();
        } else if (strcmp(action, "enter_tavern") == 0) {
            tavern();
        } else if (strcmp(action, "leave_town") == 0) {
            enter_ready_prompt();
            clw();
            clr_map_window();
            update_map();
            return 1;
        }
        return 0;
    }
    return 0;
}

void enter() {
    const char tile = wld_tile_at(pa_x(), pa_y());

    if (!wld_tile_is_settlement(tile)) {
        clw();
        tx0(res_text("towne.enter.nothing"));
        return;
    }

    state.twn = WILDERNESS_TOWN;

    /* Pluggable settlement triggers from the mission table (E-key
       phase). Castle Ydarg is the first user; new entries register
       a MW_ENTER_KEY mission_trigger with MG_TILE_AT_POS guard. */
    if (mission_try_at_enter()) {
        return;
    }

    if (wld_tile_is_hut(tile)) {
        hut();
        return;
    }

    state.twn = res_town_at(pa_x(), pa_y());
    if (state.twn == WILDERNESS_TOWN) {
        return;
    }

    char twnname[80];
    strncpy(twnname, res_town_name(state.twn), sizeof twnname - 1);
    twnname[sizeof twnname - 1] = '\0';

    if (!enter_tax_preamble(twnname)) {
        clw();
        clr_map_window();
        update_map();
        return;
    }

    clr_map_window();

    /* Town menu loop. The set of towns with churches comes from
       the world.json5 town record's `has_church` flag; only Murn
       and Ollk have a smith-only prompt (the others get the
       combined smith+church line). Guild is an author-only back
       door keyed on the global name. L exits via the ready-prompt
       flow. */
    const int has_church = res_town_has_church(state.twn);

    while (1) {
        blitgfx(citypic);
        clw();
        status_line();
        /* Settlement class lives on each town record (`class: 'city'`
           vs the 'village' default in data/world.json5); res_town_is_city
           returns 1 for cities, 0 for villages. */
        tx_fmt(res_town_is_city(state.twn) ? "towne.enter.city" : "towne.enter.village",
               (ryl_bind[]){{"twn", twnname}, {0}});
        tx0(res_text("towne.enter.divider"));
        tx0(res_text("towne.enter.menu_core"));
        tx0(res_text(has_church ? "towne.enter.menu_smith_ch"
                                : "towne.enter.menu_smith"));
        tx0(res_text("towne.enter.menu_leave"));
        tx0(res_text("towne.enter.prompt"));

        char c = (char)toupper(key());
        if (town_dispatch_key(c, has_church)) {
            return;
        }
    }
}

/* §17-F migration: wrapper owns the static entry scene (clr_map_window
   + blitgfx + entry text + fixed 600ms hold). The interactive
   greeter/menu loop is `towne.hut` in data/world/scripts/towne.json5;
   play_script receives `who` (which NPC occupies the square) and
   `npc_job_sym` (its job slug) as bindings — the DSL resolves the
   display strings via engine_bind res_enum_name_by_sym on each
   iteration. */
static void hut() {
    /* -1 sentinel: post-§25.B step 4 NPC axis flip, slot 0 is a
       real NPC (Garth), so the legacy `who = 0` / `if (!who)`
       "not found" idiom collides with a valid slot. */
    int32_t who = -1;

    for_each_npc(i) {
        if (npc_is_at(npc_at_slot(i), pa_x(), pa_y())) {
            who = i;
        }
    }
    if (who < 0) {
        return; /* Error */
    }

    clr_map_window();
    blitgfx(hutpic);
    clw();
    tx0(res_text("towne.hut.enter"));
    ryl_delay(600);

    char who_s[8];
    snprintf(who_s, sizeof who_s, "%d", who);
    play_script("towne.hut", (ryl_bind[]){
                                 {"who", who_s},
                                 {"npc_job_sym", npc_job_sym(npc_at_slot(who))},
                                 {0},
                             });
}

/* §17-F migration: C wrapper is a pass-through; the H/R/E/L loop,
   cost arithmetic, and all state mutations live in towne.church in
   data/world/scripts/towne.json5. */
static void church() {
    play_script("towne.church", NULL);
}
/* §17-F migration: C wrapper advances the in-game clock and
   resolves the smith's opening hours from the world data, then hands
   control to towne.smith in data/world/scripts/towne.json5. The
   script owns the closed screen, the clr_map_window + blitgfx entry
   scene, the main H/R/E/L style shop loop, the beer-offer chance
   branch, the A/W/N buy flow with its armor / weapon pagination,
   and the S sell flow with the Y/N/B bargain loop. Opening hours
   stay in world.json5 (buildings.smith.opens_at / closes_at) so
   tavern / inn / merchant can use the same pattern as they migrate.

   Bug fixed in migration: the original weapon-buy path scanned
   inventory slots 1..15 (`for (i = 1; i < 16; i++)`) to place the
   purchased item, while the armor path used `inv_add` which scans
   1..16. The DSL rewrites both routes through inv_find_empty
   (1..16). Not covered by replay fixtures today, so no
   byte-identical regression path. */
static void smith() {
    advance_hour();

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
    advance_hour();

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
/* §17-F migration: C wrapper advances the in-game clock and hands
   control to towne.inn in data/world/scripts/towne.json5. The inn
   has no opening-hour check (it's always open), so no
   res_building_hours lookup and no bindings pass through. The DSL
   owns the clr_map_window + blitgfx entry scene, the outer supper
   → drink → lodging loop, the inner chitchat sub-menu, and the
   room-pay flow (which uses the new hour_of_day / hours_rested
   mutate targets). */
static void inn() {
    advance_hour();
    play_script("towne.inn", NULL);
}
/* §17-F migration: C wrapper advances the in-game clock and
   resolves the merchant's opening hours from world.json5, then
   hands control to towne.merchant in data/world/scripts/towne.json5.
   The DSL owns the closed-hour screen, the clr_map_window +
   blitgfx entry scene, the B/S/L main menu, the sell-slot offer
   loop, and the buy-item inner loop (including the torch / food
   multi-buy caps and the single-purchase P/N path). Pattern
   matches the smith / tavern wrappers. */
static void merchant() {
    advance_hour();

    int open_lo = 0;
    int open_hi = 23;
    res_building_hours("merchant", &open_lo, &open_hi);

    char lo_s[8], hi_s[8];
    snprintf(lo_s, sizeof lo_s, "%d", open_lo);
    snprintf(hi_s, sizeof hi_s, "%d", open_hi);
    play_script("towne.merchant", (ryl_bind[]){
                                      {"open_lo", lo_s},
                                      {"open_hi", hi_s},
                                      {0},
                                  });
}
