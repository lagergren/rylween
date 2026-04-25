#include "ryl.h"
#include "ansi.h"
#include "res.h"
#include "battle.h"
#include "driv.h"
#include "dungeon.h"
#include "gfx.h"
#include "interact.h"
#include "inventory.h"
#include "missions.h"
#include "party.h"
#include "primitives.h"
#include "script.h"
#include "subs.h"
#include "towne.h"

static int32_t color(char);
static char *glyph(char);

void timehandler() {
    advance_hour();
}
void foodhandler() {
    int32_t i;
    int mm = res_party_member_max();

    // Regenerate mana //
    int32_t regen_pct = res_int_path("upkeep.mana_regen_chance");
    for (i = 1; i <= mm; i++) {
        pm_t pm = pm_cur(i);
        if ((pm_is_mage(pm) || pm_is_priest(pm)) &&
            pm_ma(pm) < pm_wi(pm) && ryl_chance(regen_pct)) {
            pm_add_ma(pm, 1);
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
        for (i = 1; i <= mm; i++) {
            pm_t pm = pm_cur(i);
            if (!pm_dead(pm)) {
                pm_add_hp(pm, -damage);
            }
            if (pm_hp(pm) < 1 && !pm_dead(pm)) {
                pm_set_hp(pm, 0);
                clw();
                tx("%s starved\r\n", pm_name(pm));
                tx("to death!!!\r\n");
                ryl_delay(1000);
                clw();
                pm_set_dead(pm, 1);
                if (pa_alive_count() == 0) {
                    losegame();
                }
            }
        }
    }

    // Time routine //
    pa_add_hours_rested(-1);
    if (pa_hours_rested() == 0) {
        clw();
        tx("You begin to feel tired\r\n");
        return;
    }
    if (pa_hours_rested() == -4) {
        clw();
        tx("You are exhausted!!!\r\n");
        for (i = 1; i <= res_party_member_max(); i++) {
            pm_t pm = pm_cur(i);
            if (pm_hp(pm) > 1) {
                pm_add_hp(pm, -1);
            }
        }
        return;
    }
    if (pa_hours_rested() == -8) {
        clw();
        tx("You begin to feel weak\r\n");
        tx("from lack of sleep.\r\n\r\n");
        for (i = 1; i <= res_party_member_max(); i++) {
            pm_t pm = pm_cur(i);
            if (pm_hp(pm) > 1) {
                pm_add_hp(pm, -1);
            }
        }
        ryl_pause();
        clw();
        return;
    }
    if (pa_hours_rested() < -8) {
        clw();
        tx0(res_text("subs.faint"));
        for (i = 1; i <= res_party_member_max(); i++) {
            pm_t pm = pm_cur(i);
            if (pm_hp(pm) > 1) {
                pm_add_hp(pm, -1);
            }
        }
    }
}
void random_wilderness() {
    char c;
    int32_t r;
    uint8_t rflag = 0;

    r = ryl_random(0, 99);

    rflag = mission_control();
    if (rflag) {
        return;
    }

    if (!(pa_time_left() % 101) && !stricmp(state.global_name, "Karl Skagerberg")) {
        encounter(res_monster_id("skagerberg_killer"), 1);
    }

    int32_t cave_lo = res_int_path("wilderness.cave_chance");
    int32_t quiet_hi = cave_lo + res_int_path("wilderness.quiet_chance");
    if (r >= cave_lo && r < quiet_hi) {
        // Nothing special, see if terrain has changed and display nice message //
        check_terrain(state.last_terrain, state.map[pa_y()][pa_x()]);
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

void losegame() {
    clr_map_window();
    blitgfx(deathpic);
    clw();
    tx0(res_text("subs.losegame.flavor"));
    ryl_pause();
    cleanexit();
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
void update_map() {
    const int32_t px = pa_x();
    const int32_t py = pa_y();

    for (int dy = -2; dy <= 2; dy++) {
        char row[256];
        size_t off = 0;
        c(9, 6 + (dy + 2) * 2);
        for (int dx = -2; dx <= 2; dx++) {
            char t = state.map[py + dy][px + dx];
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

void weaponoff() {
    char c;
    int32_t x;

    clw();
    tx("Who? (1-%d)\r\n", res_party_member_max());
    c = key();
    if (pm_digit_in_range(c)) {
        pm_t pm = pm_from_digit(c);
        if (!pm_has_weapon(pm)) {
            clw();
            tx("%s has no weapon!\r\n", pronoun(c - 48, 1));
            ryl_delay(800);
            clw();
            return;
        }
        x = inv_find_empty();
        if (!x) {
            clw();
            tx("No room!\r\n");
            ryl_delay(600);
            clw();
            return;
        } else {
            clw();
            tx("%s removed.\r\n", object(pm_wp(pm)));
            ryl_delay(800);
            pa_set_inventory(x, pm_wp(pm));
            pm_set_wp(pm, 0);
            clw();
            return;
        }
    }
    clw();
}
void armouroff() {
    char c;
    int32_t x;

    clw();
    tx("Who? (1-%d)\r\n", res_party_member_max());
    c = key();
    if (pm_digit_in_range(c)) {
        pm_t pm = pm_from_digit(c);
        if (!pm_has_armour(pm)) {
            clw();
            tx("%s has no armour!\r\n", pronoun(c - 48, 1));
            ryl_delay(800);
            clw();
            return;
        }
        x = inv_find_empty();
        if (!x) {
            clw();
            tx("No room!\r\n");
            ryl_delay(600);
            clw();
            return;
        } else {
            clw();
            tx("%s removed.\r\n", object(pm_ar(pm)));
            ryl_delay(800);
            pa_set_inventory(x, pm_ar(pm));
            pm_set_ar(pm, 0);
            clw();
            return;
        }
    }
    clw();
}
void weaponr() {
    clw();
    invlist();
    char d = (char)toupper(key());
    int slot = inv_slot_from_letter(d);
    if (!slot) {
        clw();
        return;
    }
    int32_t oc = pa_inventory(slot);
    if (!item_is_weapon(oc)) {
        clw();
        tx("Not a valid weapon.\r\n");
        ryl_delay(600);
        clw();
        return;
    }

    clw();
    tx("Who? (1-%d)\r\n", res_party_member_max());
    char b = key();
    if (b < '1' || b > '4') {
        clw();
        return;
    }
    pm_t pm = pm_from_digit(b);
    if (pm_dead(pm)) {
        clw();
        tx("Dead.\r\n");
        return;
    }
    int32_t temp = pm_wp(pm);
    pm_set_wp(pm, pa_inventory(slot));
    pa_set_inventory(slot, temp);
    clw();
    tx("Weapon changed.\r\n");
    ryl_delay(600);
    clw();
    return;
}
void armourr() {
    clw();
    invlist();
    char d = (char)toupper(key());
    int slot = inv_slot_from_letter(d);
    if (!slot) {
        clw();
        return;
    }
    int32_t oc = pa_inventory(slot);
    if (!item_is_armour(oc)) {
        clw();
        tx("Not a valid armour.\r\n");
        ryl_delay(600);
        clw();
        return;
    }

    clw();
    tx("Who? (1-%d)\r\n", res_party_member_max());
    char b = key();
    if (b < '1' || b > '4') {
        clw();
        return;
    }
    pm_t pm = pm_from_digit(b);
    if (pm_dead(pm)) {
        clw();
        tx("Dead.\r\n");
        return;
    }
    int32_t temp = pm_ar(pm);
    pm_set_ar(pm, pa_inventory(slot));
    pa_set_inventory(slot, temp);
    clw();
    tx("Armour changed.\r\n");
    ryl_delay(600);
    clw();
    return;
}
uint8_t drop() {
    char d;
    int32_t y;

    clw();
    invlist();
    d = key();
    d = toupper(d);
    if (inv_letter_in_range(d)) {
        int slot = inv_slot_from_letter(d);
        clw();
        if (pa_inventory(slot)) {
            tx("You drop the\r\n");
            tx("%s.\r\n", object(pa_inventory(slot)));
            ryl_delay(1000);
            if (pa_inventory(slot) == 47) {
                y = inv_count_in_range(47, 47) - 1;
                int32_t cap = res_formula_int("donkey_food_cap", "y", y);
                if (pa_food() > cap) {
                    clw();
                    tx("You are forced to\r\n");
                    tx("leave some of your\r\n");
                    tx("food behind...\r\n");
                    ryl_delay(1000);
                    pa_set_food(cap);
                }
            }
            pa_set_inventory(slot, 0);
            clw();
            return (uint8_t)slot;
        } else {
            tx("Nothing to drop.\r\n");
            ryl_delay(600);
            clw();
            return 0;
        }
    } else {
        clw();
        tx("Aborted.\r\n");
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
void camp() {
    char ap[16], hg[16], fpm[16], rh[16], hrr[16];
    char tmx[16], tll[16], tlh[16], tvll[16], tld[16], tvld[16];
    snprintf(ap,   sizeof ap,   "%d", res_int_path("balance.rest.ambush_chance"));
    snprintf(hg,   sizeof hg,   "%d", res_int_path("balance.rest.health_gain"));
    snprintf(fpm,  sizeof fpm,  "%d", res_int_path("balance.rest.food_cost_per_member"));
    snprintf(rh,   sizeof rh,   "%d", res_int_path("balance.rest.hours_advanced"));
    snprintf(hrr,  sizeof hrr,  "%d", res_int_path("balance.rest.hours_rested_reset"));
    snprintf(tmx,  sizeof tmx,  "%d", res_int_path("balance.rest.temperature_max"));
    snprintf(tll,  sizeof tll,  "%d", res_int_path("balance.rest.temp_low_lo"));
    snprintf(tlh,  sizeof tlh,  "%d", res_int_path("balance.rest.temp_low_hi"));
    snprintf(tvll, sizeof tvll, "%d", res_int_path("balance.rest.temp_very_low_lo"));
    snprintf(tld,  sizeof tld,  "%d", res_int_path("balance.rest.temp_low_damage"));
    snprintf(tvld, sizeof tvld, "%d", res_int_path("balance.rest.temp_very_low_damage"));
    play_script("rest", (ryl_bind[]){
        {"ambush_pct",         ap},
        {"health_gain",        hg},
        {"food_per_member",    fpm},
        {"rest_hours",         rh},
        {"hours_rested_reset", hrr},
        {"temp_max",           tmx},
        {"temp_low_lo",        tll},
        {"temp_low_hi",        tlh},
        {"temp_very_low_lo",   tvll},
        {"temp_low_dmg",       tld},
        {"temp_very_low_dmg",  tvld},
        {0},
    });
}
void quaff() {
    int32_t oc;
    char c, d;

    clw();
    tx("Quaff what?\r\n\r\n");
    invlist();
    while (1) {
        d = key();
        d = toupper(d);
        oc = 0;
        if (inv_letter_in_range(d)) {
            oc = pa_inventory(inv_slot_from_letter(d));
            if (!item_is_potion(oc)) {
                clw();
                tx("Not valid.\r\n");
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
    tx("Who? (1-%d)\r\n", res_party_member_max());
    while (1) {
        c = key();
        c = toupper(c);
        if (pm_digit_in_range(c)) {
            if (!pm_dead(pm_from_digit(c))) {
                break;
            } else {
                clw();
                tx("Dead.\r\n");
                ryl_delay(600);
                clw();
            }
        }
    }
    pa_set_inventory(inv_slot_from_letter(d), 0);
    clw();
    tx("Gulp!\r\n\r\n");
    ryl_delay(600);

    apply_potion(oc, pm_from_digit(c));
}
/* §20 L7: ranger food-trapping. C wrapper resolves the four
   balance.hunt tunables (success%, time cost, food range) and hands
   them to play_script as int bindings; the script owns the who-loop,
   the ranger gate, the time-left gate, and the trap success / failure
   narration in data/world/scripts/hunt.json5. */
void hunt() {
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
    char b, c, d;
    int32_t i, who, x, oc;
    pm_t caster;
    const ryl_spell *s;

    clw();
    tx("Who? (1-%d)\r\n", res_party_member_max());
    while (1) {
        c = key();
        if (pm_digit_in_range(c)) {
            break;
        }
    }
    who = (c - 48);
    caster = pm_cur(who);

    clw();
    if (pm_is_mage(caster)) {
        tx("Sorry. Mage spells are\r\n");
        tx("combat-only.\r\n");
        ryl_delay(1500);
        clw();
        return;
    }
    if (!pm_is_priest(caster)) {
        tx("Wrong class.\r\n");
        ryl_delay(1000);
        clw();
        return;
    }
    if (pm_dead(caster)) {
        clw();
        tx("Dead.\r\n");
        ryl_delay(600);
        return;
    }

    for (int slot = 1; slot <= 7; slot++) {
        s = res_spell("priest", slot);
        if (!s) {
            continue;
        }
        tx("%d. %-12s(%3d/%3d)\r\n", slot, s->name,
           s->base_cost + pm_lv(caster), pm_ma(caster));
    }
    tx("8. Abort\r\n");

    while (1) {
        c = key();
        if (c >= '1' && c <= '8') {
            break;
        }
    }

    if (c == '8') {
        clw();
        return;
    }

    s = res_spell("priest", c - 48);
    if (!s) {
        return;
    }

    /* §22 Lane A priest dispatch shim — mirror battle.c's gate. When the
       spell record carries a `do:` body, route through play_spell_body
       with caster_idx + place as bindings; ctx is NULL because priest
       spells run out of combat. Falls through to the legacy SE_* switch
       below for spells whose behaviour hasn't migrated yet. */
    {
        const void *spell_node = res_spell_node("priest", c - 48);
        if (spell_node) {
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
            return;
        }
    }

    switch (s->effect) {
        case SE_BANISH:
            clw();
            if (pm_ma(caster) < (s->base_cost + pm_lv(caster))) {
                tx0(res_text("spell.not_enough_pts"));
                ryl_delay(1000);
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
            pm_add_ma(caster, -(s->base_cost + pm_lv(caster)));
            clw();
            tx("%s raises %s hands.\r\n", pm_name(caster), pronoun(who, 3));
            tx("%s gathers divine power\r\n", pronoun(who, 1));
            tx("between %s palms, and\r\n", pronoun(who, 3));
            tx("suddenly clasps them.\r\n");
            tx("together over %s head.\r\n", pronoun(who, 3));
            tx0(res_text("spell.priest.banish.thunder"));
            ryl_pause();
            clw();
            state.banished = 1;
            break;

        case SE_REVELATION:
            clw();
            if (pm_ma(caster) < (s->base_cost + pm_lv(caster))) {
                tx0(res_text("spell.not_enough_pts"));
                ryl_delay(1000);
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
            pm_add_ma(caster, -(s->base_cost + pm_lv(caster)));
            if (ryl_chance(59)) {
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
            ryl_delay(1500);
            clw();
            x = ryl_random(0, 2);
            if (x == 0) {
                oc = ryl_random(0, 13);
                oc += 1;
            }
            if (x == 1) {
                oc = ryl_random(0, 6);
                oc += 36;
            }
            if (x == 2) {
                oc = ryl_random(0, 11);
                oc += 100;
            }
            tx("You find a\r\n");
            tx("%s.\r\n\r\n", object(oc));
            tx("(T)ake it\r\n");
            tx("(L)eave it\r\n");
            c = key();
            c = toupper(c);
            if (c == 'T') {
                x = inv_add(oc) != 0;
                if (!x) {
                    clw();
                    tx("You can't carry more.\r\n");
                    ryl_delay(800);
                } else {
                    tx("\r\n");
                    tx("Ok.\r\n");
                    ryl_delay(800);
                }
            }
            break;

        case SE_TELEPORT:
            if (pm_ma(caster) < (s->base_cost + pm_lv(caster))) {
                tx0(res_text("spell.not_enough_pts"));
                ryl_delay(1000);
                clw();
                break;
            }
            pm_add_ma(caster, -(s->base_cost + pm_lv(caster)));
            clw();
            tx("%s carefully\r\n", pm_name(caster));
            tx("prepares the ritual of\r\n");
            tx("Teleportation. %s spends\r\n", pronoun(who, 1));
            tx("an hour chanting mantras\r\n");
            tx("and burning blue candles.\r\n\r\n");
            advance_hour();
            ryl_pause();
            clw();
            tx("%s picks up a stone slab\r\n", pronoun(who, 1));
            tx0(res_text("spell.priest.teleport.satchel"));
            tx("%s wants you to teleport\r\n", pronoun(who, 1));
            tx("to.\r\n\r\n");
            ryl_pause();
            clw();
            tx0(res_text("spell.priest.teleport.compass"));
            while (1) {
                d = key();
                if (d == '8' || d == '4' || d == '6' || d == '2') {
                    break;
                }
            }
            clw();
            tx0(res_text("spell.priest.teleport.how_far"));
            while (1) {
                b = key();
                if (b >= '1' && b <= '9') {
                    break;
                }
            }
            switch (d) {
                case '2':
                    for (i = 1; i < ((b - 48) * 2) + 1; i++) {
                        if (state.map[pa_y() + 1][pa_x()] != 'm') {
                            pa_add_y(1);
                        }
                    }
                    break;
                case '4':
                    for (i = 1; i < ((b - 48) * 2) + 1; i++) {
                        if (state.map[pa_y()][pa_x() - 1] != 'm') {
                            pa_add_x(-1);
                        }
                    }
                    break;
                case '6':
                    for (i = 1; i < ((b - 48) * 2) + 1; i++) {
                        if (state.map[pa_y()][pa_x() + 1] != 'm') {
                            pa_add_x(1);
                        }
                    }
                    break;
                case '8':
                    for (i = 1; i < ((b - 48) * 2) + 1; i++) {
                        if (state.map[pa_y() - 1][pa_x()] != 'm') {
                            pa_add_y(-1);
                        }
                    }
                    break;
            }
            tx0(res_text("spell.priest.teleport.arrived"));
            update_map();
            ryl_delay(1200);
            clw();
            break;

        case SE_EARTHQUAKE:
            clw();
            if (place == 1) {
                tx("The roof would cave\r\n");
                tx("in on you!\r\n\r\n");
                ryl_pause();
                clw();
                break;
            }
            if (pm_ma(caster) < (s->base_cost + pm_lv(caster))) {
                tx0(res_text("spell.not_enough_pts"));
                ryl_delay(1000);
                clw();
                break;
            }
            pm_add_ma(caster, -(s->base_cost + pm_lv(caster)));
            tx("%s utters\r\n", pm_name(caster));
            tx0(res_text("spell.priest.earthquake.incantation"));
            ryl_delay(2000);
            tx0(res_text("spell.priest.earthquake.eruption"));
            ryl_delay(3000);
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
                if (pa_time_left() > 10) {
                    pa_add_time_left(-10);
                } else {
                    clw();
                    tx0(res_text("cave.no_moves"));
                    ryl_pause();
                    clr_map_window();
                    clw();
                    break;
                }
                clr_map_window();
                dungeon(ryl_random(5, 18), NULL);
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

    for (i = 1; i < state.num_parties + 1; i++) {
        pts[i] = (state.p[i].exp[1] + state.p[i].exp[2] + state.p[i].exp[3] + state.p[i].exp[4]) / 4;
        lvl[i] = (state.p[i].lv[1] + state.p[i].lv[2] + state.p[i].lv[3] + state.p[i].lv[4]) / 4;
        kills[i] = state.p[i].kills;
        strcpy(pname[i], state.p[i].party_name);
        strcpy(lname[i], state.p[i].name[1]);
        item[i][1] = state.p[i].fireshield;
        item[i][2] = state.p[i].bracelets;
        item[i][3] = state.p[i].mastercrown;
        item[i][4] = state.p[i].silverkey;
        item[i][5] = state.p[i].jazzyfoot;
        for (j = 1; j < 5; j++) {
            cls[i][j] = state.p[i].cl[j];
        }
        pnr[i] = i;
    }

    // SORT!

    n = state.num_parties - 1;
    o = state.num_parties - 1;

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

    if (!(fh = fopen("data/vscore.txt", "w"))) {
        return;
    }

    snprintf(str, sizeof str, "%c\n", 12);
    fputs(str, fh);
    fputs(" +============================================================================+\n", fh);
    fputs(" I              ADVENTURERS OF RYLWEEN - PARTY STATUS AND SCORES              I\n", fh);
    fputs(" +============================================================================+\n\n",
          fh);

    for (i = 1; i < state.num_parties + 1; i++) {
        snprintf(str, sizeof str, "%2d. %-30s\n", i, pname[i]);
        fputs(str, fh);
        fputs(" +----------------------------------------------------------------------------+\n",
              fh);
        snprintf(str, sizeof str, "  Leader             : %s\n  Classes            : ", lname[i]);
        for (j = 1; j < 5; j++) {
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
            if (j < 4) {
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
        return;
    }

    snprintf(str, sizeof str, "%c\n", 12);
    fputs(str, fh);
    fputs(" +============================================================================+\n", fh);
    fputs(" I              ADVENTURERS OF RYLWEEN - PARTY STATUS AND SCORES              I\n", fh);
    fputs(" +============================================================================+\n\n",
          fh);
    fputs("         #     Party Name                    Experience       Level\n\n", fh);

    for (i = 1; i < state.num_parties + 1; i++) {
        snprintf(str, sizeof str, "        %2d.    %-30s  %6d          %3d\n", i, pname[i], pts[i], lvl[i]);
        fputs(str, fh);
    }
    fputs("\n", fh);
    fclose(fh);
}
