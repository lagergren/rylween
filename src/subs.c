#include "ryl.h"
#include "ansi.h"
#include "res.h"
#include "battle.h"
#include "driv.h"
#include "dungeon.h"
#include "gfx.h"
#include "interact.h"
#include "inventory.h"
#include "log.h"
#include "missions.h"
#include "party.h"
#include "primitives.h"
#include "replay_knobs.h"
#include "script.h"
#include "subs.h"
#include "towne.h"
#include "wld.h"

static int32_t color(char);
static char *glyph(char);

/* "Who? (1-N)" — six callsites in subs.c (weapon_off/armor_off/
   weapon_ready/armor_ready/quaff/spell). Resolves the party-member cap and
   emits subs.who_prompt with a {party_max} binding. */
static void tx_who_prompt(void) {
    char max_s[8];
    snprintf(max_s, sizeof max_s, "%d", res_party_member_max());
    tx_fmt("subs.who_prompt",
           (ryl_bind[]){{"party_max", max_s}, {0}});
}

void time_handler(void) {
    advance_hour();
}

/* Sleep-deprivation hp drain: -1 hp per member with hp > 1 (so the
   penalty bruises the party rather than killing anyone outright).
   Three food_handler arms (-4, -8, < -8 hours_rested) all trigger
   the same drain, with different surrounding text + pacing. */
static void sleep_deprivation_drain(void) {
    for_each_party_member(i) {
        pm_t pm = pm_cur(i);
        if (pm_hit_points(pm) > 1) {
            pm_add_hit_points(pm, -1);
        }
    }
}

void food_handler(void) {
    // Regenerate mana //
    int32_t regen_pct = res_int_path("upkeep.mana_regen_chance");
    for_each_party_member(i) {
        pm_t pm = pm_cur(i);
        if ((pm_is_mage(pm) || pm_is_priest(pm)) &&
            pm_magic(pm) < pm_wisdom(pm) && ryl_chance(regen_pct)) {
            pm_add_magic(pm, 1);
        }
    }

    int32_t tick = res_int_path("upkeep.food_tick_every");
    int32_t lv_div = res_int_path("upkeep.food_cost_leader_div");
    if (tick > 0 && !(pa_time_left() % tick)) {
        pa_add_food(-pa_pl());
        if (lv_div > 0) {
            pa_add_food(-(pa_first_level() / lv_div));
        }
    }
    if (pa_food() < 0) {
        pa_set_food(0);
    }
    // Starving routine //

    if (pa_food() == 0) {
        int32_t damage = res_int_path("upkeep.starve_damage");
        for_each_party_member(i) {
            pm_t pm = pm_cur(i);
            if (!pm_dead(pm)) {
                pm_add_hit_points(pm, -damage);
            }
            if (pm_hit_points(pm) < 1 && !pm_dead(pm)) {
                pm_set_hit_points(pm, 0);
                clw();
                tx_fmt("subs.food_handler.starved",
                       (ryl_bind[]){{"name", pm_name(pm)}, {0}});
                ryl_delay(1000);
                clw();
                pm_set_dead(pm, 1);
                if (pa_alive_count() == 0) {
                    lose_game();
                }
            }
        }
    }

    // Time routine //
    pa_add_hours_rested(-1);
    if (pa_hours_rested() == 0) {
        clw();
        tx0(res_text("subs.food_handler.tired"));
        return;
    }
    if (pa_hours_rested() == -4) {
        clw();
        tx0(res_text("subs.food_handler.exhausted"));
        sleep_deprivation_drain();
        return;
    }
    if (pa_hours_rested() == -8) {
        clw();
        tx0(res_text("subs.food_handler.weak"));
        sleep_deprivation_drain();
        ryl_pause();
        clw();
        return;
    }
    if (pa_hours_rested() < -8) {
        clw();
        tx0(res_text("subs.faint"));
        sleep_deprivation_drain();
    }
}
void random_wilderness(void) {
    char c;
    int32_t r;
    uint8_t rflag = 0;

    /* Replay-mode override (rk_force_encounter): fire one mandatory
       encounter with a named monster on the next move and resume the
       natural RNG path on subsequent moves. The post-encounter
       clr_map_window + update_map mirrors the wilderness encounter
       return path. */
    const char *force = rk_force_encounter();
    if (force) {
        int32_t foe = res_monster_id(force);
        if (foe >= 1) {
            encounter(foe, 1);
            clr_map_window();
            update_map();
            return;
        }
    }

    r = ryl_random(0, 99);

    rflag = mission_control();
    if (rflag) {
        return;
    }

    /* Sysop backdoor — name-keyed periodic encounter lifted to
       data/world.json5 secrets:. The cadence and monster slug are
       both data; the engine returns the resolved monster id. */
    secret_action act;
    if (res_secrets_step_trigger(session_player_name(), pa_time_left(), &act) &&
        act.kind == SECRET_SPAWN_ENCOUNTER) {
        encounter(act.monster_id, 1);
    }

    int32_t cave_lo = res_int_path("wilderness.cave_chance");
    int32_t quiet_hi = cave_lo + res_int_path("wilderness.quiet_chance");
    if (r >= cave_lo && r < quiet_hi) {
        // Nothing special, see if terrain has changed and display nice message //
        check_terrain(wld_last_terrain(), wld_tile_at(pa_x(), pa_y()));
        return;
    }
    if (r < cave_lo) {
        clr_map_window();
        blitgfx(cavepic);
        clw();
        tx0(res_text("cave.found_prompt"));
        while (1) {
            c = key();
            c = toupper(c);
            if (c == 'L' || c == 'E') {
                break;
            }
        }
        if (c == 'E') {
            int32_t cost = res_int_path("wilderness.cave_entry_cost");
            if (pa_time_left() > cost) {
                pa_add_time_left(-cost);
            } else {
                clw();
                tx0(res_text("cave.no_moves"));
                ryl_pause();
                clr_map_window();
                clw();
                return;
            }
            clr_map_window();
            dungeon(ryl_random(res_int_path("wilderness.cave_dungeon_lo"),
                               res_int_path("wilderness.cave_dungeon_hi")),
                    0);
            return;
        } else {
            clw();
            clr_map_window();
            update_map();
            return;
        }
    }

    // Random encounter. Two tiers configured in world.json5 (wilderness.
    // encounter_{easy,hard}_tier) — the tier keyword on each monster
    // record decides which pool it joins.
    const char *tier = ryl_chance(res_int_path("wilderness.encounter_split"))
                           ? res_str_path("wilderness.encounter_easy_tier")
                           : res_str_path("wilderness.encounter_hard_tier");
    encounter(res_monster_tier_pick(tier), 0);
    clr_map_window();
    update_map();
}

void lose_game(void) {
    clr_map_window();
    blitgfx(deathpic);
    clw();
    tx0(res_text("subs.lose_game.flavor"));
    ryl_pause();
    clean_exit();
}

/* Render the 5x5 map window centred on the party tile. Each cell is
   ANSI_CSI "<fg>m<glyph>"; the party's own tile wraps the glyph in a
   blue background (ANSI_BG_BLUE ... ANSI_BG_BLACK); cells are separated
   by a single space; the very last cell of the last row is followed by
   ANSI_FG_WHITE to restore the default foreground.

   Build each row into a local buffer and emit it with one tx0() call —
   tx()/tx0() prefix every invocation with a cursor-right escape from
   c() (see primitives.c), so driving the render cell-by-cell would
   bloat the output with redundant escapes. */
void update_map(void) {
    const int32_t px = pa_x();
    const int32_t py = pa_y();

    for (int dy = -2; dy <= 2; dy++) {
        char row[256];
        size_t off = 0;
        c(9, 6 + (dy + 2) * 2);
        for (int dx = -2; dx <= 2; dx++) {
            char t = wld_tile_at(px + dx, py + dy);
            int32_t fg = color(t);
            char *gly = glyph(t);
            if (dy == 0 && dx == 0) {
                off += snprintf(row + off, sizeof row - off, ANSI_BG_BLUE ANSI_CSI "%dm%s" ANSI_BG_BLACK, fg, gly);
            } else {
                off += snprintf(row + off, sizeof row - off, ANSI_CSI "%dm%s", fg, gly);
            }
            if (dx < 2) {
                off += snprintf(row + off, sizeof row - off, " ");
            }
        }
        if (dy == 2) {
            off += snprintf(row + off, sizeof row - off, ANSI_FG_WHITE);
        }
        tx0(row);
    }
}
static int32_t color(char t) {
    return res_color(t);
}
static char *glyph(char t) {
    return (char *)res_glyph(t);
}

void weapon_off(void) {
    char c;
    int32_t x;

    clw();
    tx_who_prompt();
    c = key();
    if (pm_digit_in_range(c)) {
        pm_t pm = pm_from_digit(c);
        LOG_D("input", "weapon_off: digit=%c -> slot=%d", c, pm.m);
        if (!pm_has_weapon(pm)) {
            clw();
            tx_fmt("subs.weapon_off.no_weapon",
                   (ryl_bind[]){{"pronoun", pronoun(c - '1', 1)}, {0}});
            ryl_delay(800);
            clw();
            return;
        }
        x = inv_find_empty();
        if (!x) {
            clw();
            tx0(res_text("subs.no_room"));
            ryl_delay(600);
            clw();
            return;
        } else {
            clw();
            tx_fmt("subs.equipment.removed",
                   (ryl_bind[]){{"object", object(pm_weapon(pm))}, {0}});
            ryl_delay(800);
            pa_set_inventory(x, pm_weapon(pm));
            pm_set_weapon(pm, 0);
            clw();
            return;
        }
    }
    clw();
}
void armor_off(void) {
    char c;
    int32_t x;

    clw();
    tx_who_prompt();
    c = key();
    if (pm_digit_in_range(c)) {
        pm_t pm = pm_from_digit(c);
        LOG_D("input", "armor_off: digit=%c -> slot=%d", c, pm.m);
        if (!pm_has_armor(pm)) {
            clw();
            tx_fmt("subs.armor_off.no_armor",
                   (ryl_bind[]){{"pronoun", pronoun(c - '1', 1)}, {0}});
            ryl_delay(800);
            clw();
            return;
        }
        x = inv_find_empty();
        if (!x) {
            clw();
            tx0(res_text("subs.no_room"));
            ryl_delay(600);
            clw();
            return;
        } else {
            clw();
            tx_fmt("subs.equipment.removed",
                   (ryl_bind[]){{"object", object(pm_armor(pm))}, {0}});
            ryl_delay(800);
            pa_set_inventory(x, pm_armor(pm));
            pm_set_armor(pm, 0);
            clw();
            return;
        }
    }
    clw();
}
void weapon_ready(void) {
    clw();
    inv_list();
    char d = (char)toupper(key());
    int slot = inv_slot_from_letter(d);
    if (!slot) {
        clw();
        return;
    }
    int32_t oc = pa_inventory(slot);
    if (!item_is_weapon(oc)) {
        clw();
        tx0(res_text("subs.weapon_ready.invalid"));
        ryl_delay(600);
        clw();
        return;
    }

    clw();
    tx_who_prompt();
    char b = key();
    if (b < '1' || b > '4') {
        clw();
        return;
    }
    pm_t pm = pm_from_digit(b);
    if (pm_dead(pm)) {
        clw();
        tx0(res_text("subs.dead"));
        return;
    }
    int32_t temp = pm_weapon(pm);
    pm_set_weapon(pm, pa_inventory(slot));
    pa_set_inventory(slot, temp);
    clw();
    tx0(res_text("subs.weapon_ready.changed"));
    ryl_delay(600);
    clw();
    return;
}
void armor_ready(void) {
    clw();
    inv_list();
    char d = (char)toupper(key());
    int slot = inv_slot_from_letter(d);
    if (!slot) {
        clw();
        return;
    }
    int32_t oc = pa_inventory(slot);
    if (!item_is_armor(oc)) {
        clw();
        tx0(res_text("subs.armor_ready.invalid"));
        ryl_delay(600);
        clw();
        return;
    }

    clw();
    tx_who_prompt();
    char b = key();
    if (b < '1' || b > '4') {
        clw();
        return;
    }
    pm_t pm = pm_from_digit(b);
    if (pm_dead(pm)) {
        clw();
        tx0(res_text("subs.dead"));
        return;
    }
    int32_t temp = pm_armor(pm);
    pm_set_armor(pm, pa_inventory(slot));
    pa_set_inventory(slot, temp);
    clw();
    tx0(res_text("subs.armor_ready.changed"));
    ryl_delay(600);
    clw();
    return;
}
uint8_t drop(void) {
    char d;
    int32_t y;

    clw();
    inv_list();
    d = key();
    d = toupper(d);
    if (inv_letter_in_range(d)) {
        int slot = inv_slot_from_letter(d);
        clw();
        if (pa_inventory(slot)) {
            tx_fmt("subs.drop.you_drop",
                   (ryl_bind[]){{"object", object(pa_inventory(slot))}, {0}});
            ryl_delay(1000);
            if (pa_inventory(slot) == 47) {
                y = inv_count_in_range(47, 47) - 1;
                int32_t cap = res_formula_int("donkey_food_cap", "y", y);
                if (pa_food() > cap) {
                    clw();
                    tx0(res_text("subs.drop.donkey_food_cap"));
                    ryl_delay(1000);
                    pa_set_food(cap);
                }
            }
            pa_set_inventory(slot, 0);
            clw();
            return (uint8_t)slot;
        } else {
            tx0(res_text("subs.drop.nothing"));
            ryl_delay(600);
            clw();
            return 0;
        }
    } else {
        clw();
        tx0(res_text("subs.drop.aborted"));
        ryl_delay(600);
        return 0;
    }
}
/* §20 L7: party-rest flow. C wrapper resolves the eleven balance.rest
   tunables (ambush%, healing/food cost, hours advanced, temperature
   thresholds + damage values) and hands them to play_script as int
   bindings. The script in data/world/scripts/rest.json5 owns the
   not-tired / wiped-out early-outs, the tent/notent pic select, the
   (Y/N) guard prompt with its inner who-loop, the temperature roll
   + damage pass, the time/food/heal sweep, and the ambush vs
   well-rested split (including the random surprise-kill arm). */
void camp(void) {
    char ap[16], hg[16], fpm[16], rh[16], hrr[16];
    char tmx[16], tll[16], tlh[16], tvll[16], tld[16], tvld[16];
    snprintf(ap, sizeof ap, "%d", res_int_path("balance.rest.ambush_chance"));
    snprintf(hg, sizeof hg, "%d", res_int_path("balance.rest.health_gain"));
    snprintf(fpm, sizeof fpm, "%d", res_int_path("balance.rest.food_cost_per_member"));
    snprintf(rh, sizeof rh, "%d", res_int_path("balance.rest.hours_advanced"));
    snprintf(hrr, sizeof hrr, "%d", res_int_path("balance.rest.hours_rested_reset"));
    snprintf(tmx, sizeof tmx, "%d", res_int_path("balance.rest.temperature_max"));
    snprintf(tll, sizeof tll, "%d", res_int_path("balance.rest.temp_low_lo"));
    snprintf(tlh, sizeof tlh, "%d", res_int_path("balance.rest.temp_low_hi"));
    snprintf(tvll, sizeof tvll, "%d", res_int_path("balance.rest.temp_very_low_lo"));
    snprintf(tld, sizeof tld, "%d", res_int_path("balance.rest.temp_low_damage"));
    snprintf(tvld, sizeof tvld, "%d", res_int_path("balance.rest.temp_very_low_damage"));
    play_script("rest", (ryl_bind[]){
                            {"ambush_pct", ap},
                            {"health_gain", hg},
                            {"food_per_member", fpm},
                            {"rest_hours", rh},
                            {"hours_rested_reset", hrr},
                            {"temp_max", tmx},
                            {"temp_low_lo", tll},
                            {"temp_low_hi", tlh},
                            {"temp_very_low_lo", tvll},
                            {"temp_low_dmg", tld},
                            {"temp_very_low_dmg", tvld},
                            {0},
                        });
}
void quaff(void) {
    int32_t oc;
    char c, d;

    clw();
    tx0(res_text("subs.quaff.prompt"));
    inv_list();
    while (1) {
        d = key();
        d = toupper(d);
        oc = 0;
        if (inv_letter_in_range(d)) {
            oc = pa_inventory(inv_slot_from_letter(d));
            if (!item_is_potion(oc)) {
                clw();
                tx0(res_text("subs.quaff.invalid"));
                ryl_delay(1000);
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
    tx_who_prompt();
    while (1) {
        c = key();
        c = toupper(c);
        if (pm_digit_in_range(c)) {
            if (!pm_dead(pm_from_digit(c))) {
                break;
            } else {
                clw();
                tx0(res_text("subs.dead"));
                ryl_delay(600);
                clw();
            }
        }
    }
    pa_set_inventory(inv_slot_from_letter(d), 0);
    clw();
    tx0(res_text("subs.quaff.gulp"));
    ryl_delay(600);

    apply_potion(oc, pm_from_digit(c));
}
/* §20 L7: ranger food-trapping. C wrapper resolves the four
   balance.hunt tunables (success%, time cost, food range) and hands
   them to play_script as int bindings; the script owns the who-loop,
   the ranger gate, the time-left gate, and the trap success / failure
   narration in data/world/scripts/hunt.json5. */
void hunt(void) {
    char tc[16], sc[16], fmn[16], fmx[16];
    snprintf(tc, sizeof tc, "%d", res_int_path("balance.hunt.time_cost"));
    snprintf(sc, sizeof sc, "%d", res_int_path("balance.hunt.success_chance"));
    snprintf(fmn, sizeof fmn, "%d", res_int_path("balance.hunt.food_min"));
    snprintf(fmx, sizeof fmx, "%d", res_int_path("balance.hunt.food_max"));
    play_script("hunt", (ryl_bind[]){
                            {"time_cost", tc},
                            {"success_pct", sc},
                            {"food_min", fmn},
                            {"food_max", fmx},
                            {0},
                        });
}
void spell(int32_t place) {
    char c;
    int32_t who;
    pm_t caster;
    const ryl_spell *s;

    clw();
    tx_who_prompt();
    while (1) {
        c = key();
        if (pm_digit_in_range(c)) {
            break;
        }
    }
    who = c - '1';
    LOG_D("input", "priest cast: digit=%c -> caster slot=%d", c, who);
    caster = pm_cur(who);

    clw();
    if (pm_is_mage(caster)) {
        tx0(res_text("subs.spell.mage_combat_only"));
        ryl_delay(1500);
        clw();
        return;
    }
    if (!pm_is_priest(caster)) {
        tx0(res_text("subs.spell.wrong_class"));
        ryl_delay(1000);
        clw();
        return;
    }
    if (pm_dead(caster)) {
        clw();
        tx0(res_text("subs.dead"));
        ryl_delay(600);
        return;
    }

    int spell_max = res_class_spell_max("priest");
    for (int slot = 1; slot <= spell_max; slot++) {
        s = res_spell("priest", slot);
        if (!s) {
            continue;
        }
        tx("%d. %-12s(%3d/%3d)\r\n", slot, s->name,
           s->base_cost + pm_level(caster), pm_magic(caster));
    }
    int abort_key = spell_max + 1;
    tx("%d. Abort\r\n", abort_key);

    while (1) {
        c = key();
        if (c >= '1' && c <= ('0' + abort_key)) {
            break;
        }
    }

    if (c == '0' + abort_key) {
        clw();
        return;
    }

    s = res_spell("priest", c - 48);
    if (!s) {
        return;
    }
    const void *spell_node = res_spell_node("priest", c - 48);
    if (!spell_node) {
        return;
    }
    char caster_buf[8], place_buf[8];
    snprintf(caster_buf, sizeof caster_buf, "%d", who);
    snprintf(place_buf, sizeof place_buf, "%d", (int)place);
    ryl_bind binds[] = {
        {"caster_idx", caster_buf},
        {"place", place_buf},
        {0},
    };
    play_spell_body(s->id ? s->id : "priest.(unknown)",
                    spell_node, binds, NULL);
}

void score_list(void) {
    FILE *fh;
    char str[144];
    uint8_t i, j, xx, o, n, q;
    uint32_t temp_ulong;
    char temp_string[30];
    /* Swap buffer for items[1..5] (length 6) and cls[0..mmax-1] (length
       <= res_party_member_max()) — stays on the stack since the upper
       bound is small and independent of res_party_slot_max(). */
    uint8_t temp_ubyte[32];

    /* Per-party tables — sized to res_party_slot_max() so the
       function works for any data-driven party-slot cap. Single
       allocation block + single free at function exit. */
    int mmax = res_party_member_max();
    size_t slots = (size_t)res_party_slot_max();
    uint32_t *pts = calloc(slots, sizeof *pts);
    uint32_t *lvl = calloc(slots, sizeof *lvl);
    uint32_t *kills = calloc(slots, sizeof *kills);
    char (*pname)[30] = calloc(slots, sizeof *pname);
    char (*lname)[30] = calloc(slots, sizeof *lname);
    uint8_t (*cls)[5] = calloc(slots, sizeof *cls);
    uint8_t (*item)[6] = calloc(slots, sizeof *item);
    uint8_t *pnr = calloc(slots, sizeof *pnr);
    if (!pts || !lvl || !kills || !pname || !lname || !cls || !item || !pnr) {
        LOG_E("subs", "score_list: calloc(%zu slots) failed", slots);
        free(pts);
        free(lvl);
        free(kills);
        free(pname);
        free(lname);
        free(cls);
        free(item);
        free(pnr);
        return;
    }

    for (i = 1; i < party_count() + 1; i++) {
        const struct party *p = party_at(i);
        uint32_t exp_sum = 0;
        uint32_t lv_sum = 0;
        for (j = 0; j < mmax; j++) {
            exp_sum += p->members[j].experience;
            lv_sum += (uint32_t)p->members[j].level;
        }
        pts[i] = exp_sum / (uint32_t)mmax;
        lvl[i] = lv_sum / (uint32_t)mmax;
        kills[i] = p->kills;
        strcpy(pname[i], p->party_name);
        strcpy(lname[i], p->members[0].name);
        item[i][1] = p->fireshield;
        item[i][2] = p->bracelets;
        item[i][3] = p->mastercrown;
        item[i][4] = p->silverkey;
        item[i][5] = p->jazzyfoot;
        for (j = 0; j < mmax; j++) {
            /* §24bis phase 3 class axis: storage is slug pointer;
               recover the legacy int id for the cls[][] sort table.
               NULL → 0 (matches the unset sentinel). */
            cls[i][j] = (uint8_t)(p->members[j].class_slug ? res_enum_id("pc_class", p->members[j].class_slug) : 0);
        }
        pnr[i] = i;
    }

    // SORT!

    n = party_count() - 1;
    o = party_count() - 1;

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
                for (xx = 0; xx < mmax; xx++) {
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

    if (!(fh = fopen("data/vscore.txt", "w"))) {
        goto cleanup;
    }

    snprintf(str, sizeof str, "%c\n", 12);
    fputs(str, fh);
    fputs(" +============================================================================+\n", fh);
    fputs(" I              ADVENTURERS OF RYLWEEN - PARTY STATUS AND SCORES              I\n", fh);
    fputs(" +============================================================================+\n\n",
          fh);

    for (i = 1; i < party_count() + 1; i++) {
        snprintf(str, sizeof str, "%2d. %-30s\n", i, pname[i]);
        fputs(str, fh);
        fputs(" +----------------------------------------------------------------------------+\n",
              fh);
        snprintf(str, sizeof str, "  Leader             : %s\n  Classes            : ", lname[i]);
        for (j = 0; j < mmax; j++) {
            switch (cls[i][j]) {
                case 1:
                    str_append(str, sizeof str, "Fighter");
                    break;
                case 2:
                    str_append(str, sizeof str, "Thief");
                    break;
                case 3:
                    str_append(str, sizeof str, "Mage");
                    break;
                case 4:
                    str_append(str, sizeof str, "Priest");
                    break;
                case 5:
                    str_append(str, sizeof str, "Bard");
                    break;
                case 6:
                    str_append(str, sizeof str, "Ranger");
                    break;
            }
            if (j < mmax - 1) {
                str_append(str, sizeof str, ", ");
            }
        }
        str_append(str, sizeof str, "\n");
        fputs(str, fh);
        snprintf(str, sizeof str, "  Confirmed Kills    : %-4d\n", kills[i]);
        snprintf(str, sizeof str, "  Average Experience : %-6d (level %-3d)\n\n", pts[i], lvl[i]);
        fputs(str, fh);
        fputs("  Power Items        :\n\n", fh);
        xx = 0;
        for (j = 1; j < 6; j++) {
            if (item[i][j]) {
                xx = 1;
            }
        }
        if (item[i][1]) {
            fputs("  Fire Shield\n", fh);
        }
        if (item[i][2]) {
            fputs("  Bracelets of Thun\n", fh);
        }
        if (item[i][3]) {
            fputs("  Master Crown\n", fh);
        }
        if (item[i][4]) {
            fputs("  Silver Key\n", fh);
        }
        if (item[i][5]) {
            fputs("  Jazzy's Foot\n", fh);
        }
        if (!xx) {
            fputs("  None.\n", fh);
        }
        fputs(" +----------------------------------------------------------------------------+\n\n",
              fh);
    }
    fclose(fh);

    if (!(fh = fopen("data/score.txt", "w"))) {
        goto cleanup;
    }

    snprintf(str, sizeof str, "%c\n", 12);
    fputs(str, fh);
    fputs(" +============================================================================+\n", fh);
    fputs(" I              ADVENTURERS OF RYLWEEN - PARTY STATUS AND SCORES              I\n", fh);
    fputs(" +============================================================================+\n\n",
          fh);
    fputs("         #     Party Name                    Experience       Level\n\n", fh);

    for (i = 1; i < party_count() + 1; i++) {
        snprintf(str, sizeof str, "        %2d.    %-30s  %6d          %3d\n", i, pname[i], pts[i], lvl[i]);
        fputs(str, fh);
    }
    fputs("\n", fh);
    fclose(fh);

cleanup:
    free(pts);
    free(lvl);
    free(kills);
    free(pname);
    free(lname);
    free(cls);
    free(item);
    free(pnr);
}
