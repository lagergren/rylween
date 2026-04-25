#include "ryl.h"
#include "res.h"
#include "driv.h"
#include "gfx.h"
#include "inventory.h"
#include "primitives.h"
#include "script.h"
#include "subs.h"
#include "towne.h"
#include "ydarg.h"

static void church(void);
static void hut(void);
static void inn(void);
static void merchant(void);
static void smith(void);
static void tavern(void);

/* Rumour IDs that need C-side parameter binding before display. The
   remaining ids in 0..32 are fully static and dispatched via the
   default branch (tx0 on `towne.rumour.r<pick>`). Names track the
   in-rumour NPC reference, not the text topic. */
enum {
    RUMOUR_GARTH_BANDITS = 12, /* {name1}  — Garth quest hook       */
    RUMOUR_NRIMTO_HUNT = 13,   /* {name2}  — Nrim'to hunter rumour  */
    RUMOUR_BROTH_CROWN = 25,   /* {name15}, {loc15} — Master Crown  */
    RUMOUR_HARRY_TEMPLE = 27,  /* {name13}, {loc13} — Temple of Thun */
};

void rumour() {
    char loc[40];
    int32_t pick = ryl_random(0, 32);

    /* Replay-mode override. Setting RYL_REPLAY_RUMOUR=<N> in a fixture
       pins the rumour id so `towne-thord-tavern-rumour-r12` reliably
       exercises the Garth-bandits branch, etc. Production never sets
       this env var. Clamped to 0..32 — out-of-range falls through to
       the random roll. */
    const char *force = getenv("RYL_REPLAY_RUMOUR");
    if (force && *force) {
        char *end;
        long v = strtol(force, &end, 10);
        if (end != force && v >= 0 && v <= 32) {
            pick = (int32_t)v;
        }
    }

    /* Most rumours are static paragraphs; a handful fold in an NPC name
       and a town-or-coord loc via tx_fmt. */
    switch (pick) {
        case RUMOUR_GARTH_BANDITS:
            tx_fmt("towne.rumour.r12",
                   (ryl_bind[]){{"name1",
                                 res_enum("name", res_npc_role("bandits_quest_giver"))},
                                {0}});
            break;
        case RUMOUR_NRIMTO_HUNT:
            tx_fmt("towne.rumour.r13",
                   (ryl_bind[]){{"name2",
                                 res_enum("name", res_npc_role("rangers_quest_giver"))},
                                {0}});
            break;
        case RUMOUR_BROTH_CROWN: {
            npc_t seer = npc_for_role("crown_seer");
            npc_loc_str(seer, loc, sizeof loc);
            tx_fmt("towne.rumour.r25",
                   (ryl_bind[]){
                       {"name15", npc_name(seer)},
                       {"loc15", loc},
                       {0},
                   });
            break;
        }
        case RUMOUR_HARRY_TEMPLE: {
            npc_t seer = npc_for_role("temple_of_thun_seer");
            npc_loc_str(seer, loc, sizeof loc);
            tx_fmt("towne.rumour.r27",
                   (ryl_bind[]){
                       {"name13", npc_name(seer)},
                       {"loc13", loc},
                       {0},
                   });
            break;
        }
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
char *object(int32_t nr) {
    const ryl_item *it = res_item(nr);
    return it ? (char *)it->name : "-";
}

/* Post-shop exit flow: offer to ready any weapon / armour the party
   is carrying before returning to the wilderness map. Called once
   from enter()'s L-arm; weapon range is item ids 1..19, armour is
   20..30. Prompt skips entirely when the party has none carried or
   every member already has one equipped. */
static void enter_ready_prompt(void) {
    int32_t x, y, i;

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
        if (toupper(key()) == 'Y') {
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
        if (toupper(key()) == 'Y') {
            armourr();
        }
    }
}

/* Tax preamble for high-level parties. Split out so enter() reads as
   a dispatcher. Returns 1 if the party paid (or wasn't prompted),
   0 if they refused and should bounce back to the wilderness. */
static int enter_tax_preamble(const char *twnname) {
    if (state.p[state.cpl].lv[1] < 5) {
        return 1;
    }
    int32_t tax = res_town_tax(state.twn);
    int32_t amount = (state.p[state.cpl].gold * tax) / 100;
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
    state.p[state.cpl].gold -= amount;
    return 1;
}

void enter() {
    const char tile = state.map[state.p[state.cpl].y][state.p[state.cpl].x];

    if (tile != '0' && tile != '1' && tile != '2') {
        clw();
        tx0(res_text("towne.enter.nothing"));
        return;
    }

    state.twn = 0;

    if (tile == '1') {
        hut();
        return;
    }
    if (tile == '2' &&
        state.p[state.cpl].x == 14 && state.p[state.cpl].y == 30) {
        ydarg();
        return;
    }

    state.twn = res_town_at(state.p[state.cpl].x, state.p[state.cpl].y);
    if (!state.twn) {
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

    /* Town menu loop. Only Thord and Wilor have a church; only
       Murn and Ollk have a smith-only prompt (the others get the
       combined smith+church line). Guild is an author-only back
       door keyed on the global name. L exits via the ready-prompt
       flow. */
    const int has_church = (state.twn == 1 || state.twn == 3);

    while (1) {
        blitgfx(citypic);
        clw();
        statusline();
        tx_fmt(state.twn == 3 ? "towne.enter.city" : "towne.enter.village",
               (ryl_bind[]){{"twn", twnname}, {0}});
        tx0(res_text("towne.enter.divider"));
        tx0(res_text("towne.enter.menu_core"));
        tx0(res_text(has_church ? "towne.enter.menu_smith_ch"
                                : "towne.enter.menu_smith"));
        tx0(res_text("towne.enter.menu_leave"));
        tx0(res_text("towne.enter.prompt"));

        char c = (char)toupper(key());
        if (c == 'C' && has_church) {
            church();
        } else if (c == 'S') {
            smith();
        } else if (c == 'I') {
            inn();
        } else if (c == 'M') {
            merchant();
        } else if (c == 'T') {
            tavern();
        } else if (c == 'L') {
            enter_ready_prompt();
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
   play_script receives `who` (which NPC occupies the square) and
   `npc_job` (its job id) as int bindings — the DSL resolves the
   display strings via engine_bind res_enum on each iteration. */
static void hut() {
    int32_t i, who = 0;
    int nmax = res_npc_slot_max();

    for (i = 1; i <= nmax; i++) {
        if (state.npc[i].x == state.p[state.cpl].x && state.npc[i].y == state.p[state.cpl].y) {
            who = i;
        }
    }
    if (!who) {
        return; /* Error */
    }

    clr_map_window();
    blitgfx(hutpic);
    clw();
    tx0(res_text("towne.hut.enter"));
    ryl_delay(600);

    char who_s[8], job_s[8];
    snprintf(who_s, sizeof who_s, "%d", who);
    snprintf(job_s, sizeof job_s, "%d", state.npc[who].job);
    play_script("towne.hut", (ryl_bind[]){
                                 {"who", who_s},
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
/* §17-F migration: C wrapper advances the in-game clock and
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
