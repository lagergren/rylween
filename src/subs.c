#include "ryl.h"
#include "res.h"

void timehandler() {
    state.tid++;
    if (state.tid == 25) {
        state.tid = 1;
    }
}
void foodhandler() {
    int32_t i;

    // Regenerate mana //
    for (i = 1; i < 5; i++) {
        if (state.p[state.cpl].cl[i] == 3 || state.p[state.cpl].cl[i] == 4) {
            if (state.p[state.cpl].ma[i] < state.p[state.cpl].wi[i] && ryl_chance(50)) {
                state.p[state.cpl].ma[i]++;
            }
        }
    }

    if (!(state.p[state.cpl].tid_kvar % 5)) {
        state.p[state.cpl].food -= state.p[state.cpl].pl;
        state.p[state.cpl].food -= state.p[state.cpl].lv[1] / 4;
    }
    if (state.p[state.cpl].food < 0) {
        state.p[state.cpl].food = 0;
    }
    // Starving routine //

    if (state.p[state.cpl].food == 0) {
        for (i = 1; i < 5; i++) {
            if (!state.p[state.cpl].dead[i]) {
                state.p[state.cpl].hp[i] -= 2;
            }
            if (state.p[state.cpl].hp[i] < 1 && !state.p[state.cpl].dead[i]) {
                state.p[state.cpl].hp[i] = 0;
                clw();
                tx("%s starved\r\n", state.p[state.cpl].name[i]);
                tx("to death!!!\r\n");
                ryl_delay(1000);
                clw();
                state.p[state.cpl].dead[i] = 1;
                if (state.p[state.cpl].dead[1] && state.p[state.cpl].dead[2] && state.p[state.cpl].dead[3] && state.p[state.cpl].dead[4]) {
                    losegame();
                }
            }
        }
    }

    // Time routine //
    state.hours_pigg--;
    if (state.hours_pigg == 0) {
        clw();
        tx("You begin to feel tired\r\n");
        return;
    }
    if (state.hours_pigg == -4) {
        clw();
        tx("You are exhausted!!!\r\n");
        for (i = 1; i < 5; i++) {
            if (state.p[state.cpl].hp[i] > 1) {
                state.p[state.cpl].hp[i]--;
            }
        }
        return;
    }
    if (state.hours_pigg == -8) {
        clw();
        tx("You begin to feel weak\r\n");
        tx("from lack of sleep.\r\n\r\n");
        for (i = 1; i < 5; i++) {
            if (state.p[state.cpl].hp[i] > 1) {
                state.p[state.cpl].hp[i]--;
            }
        }
        ryl_pause();
        clw();
        return;
    }
    if (state.hours_pigg < -8) {
        clw();
        tx("%s", res_text("subs.faint"));
        for (i = 1; i < 5; i++) {
            if (state.p[state.cpl].hp[i] > 1) {
                state.p[state.cpl].hp[i]--;
            }
        }
    }
}
void slumpvis() {
    char c;
    int32_t r;
    uint8_t rflag = 0;

    r = ryl_random(0, 99);

    rflag = mission_control();
    if (rflag) {
        return;
    }

    if (!(state.p[state.cpl].tid_kvar % 101) && !stricmp(state.global_name, "Karl Skagerberg")) {
        encounter(90, 1);
    }

    if (r < 94 && r >= 3) {
        // Nothing special, see if terrain has changed and display nice message //
        check_terrain(state.last_terrain, state.map[state.p[state.cpl].y][state.p[state.cpl].x]);
        return;
    }
    if (r < 3) {
        clr_map_window();
        blitgfx(cavepic);
        clw();
        tx("%s", res_text("cave.found_prompt"));
        while (1) {
            c = key();
            c = toupper(c);
            if (c == 'L' || c == 'E') {
                break;
            }
        }
        if (c == 'E') {
            if (state.p[state.cpl].tid_kvar > 10) {
                state.p[state.cpl].tid_kvar -= 10;
            } else {
                clw();
                tx("%s", res_text("cave.no_moves"));
                ryl_pause();
                clr_map_window();
                clw();
                return;
            }
            clr_map_window();
            dungeon(ryl_random(3, 16), 0);
            return;
        } else {
            clw();
            clr_map_window();
            update_map();
            return;
        }
    }

    // slumpmote //

    // 40-53 //

    if (ryl_chance(50)) {
        encounter(ryl_random(1, 10), 0);
        clr_map_window();
        update_map();
    } else {
        encounter(ryl_random(40, 53), 0);
        clr_map_window();
        update_map();
    }
}

void losegame() {
    clr_map_window();
    blitgfx(deathpic);
    clw();
    tx("%s", res_text("subs.losegame.flavor"));
    ryl_pause();
    cleanexit();
}

/* Render the 5x5 map window centred on the party tile. Each cell is
   "\x1b[<fg>m<glyph>"; the party's own tile wraps the glyph in a blue
   background ("\x1b[44m...\x1b[40m"); cells are separated by a single
   space; the very last cell of the last row is followed by "\x1b[37m"
   to restore the default foreground.

   Build each row into a local buffer and emit it with one tx() call —
   tx() prefixes every invocation with a cursor-right escape from c()
   (see primitives.c), so driving the render cell-by-cell would bloat
   the output with redundant escapes. */
void update_map() {
    const int32_t px = state.p[state.cpl].x;
    const int32_t py = state.p[state.cpl].y;

    for (int dy = -2; dy <= 2; dy++) {
        char row[256];
        size_t off = 0;
        c(9, 6 + (dy + 2) * 2);
        for (int dx = -2; dx <= 2; dx++) {
            char t = state.map[py + dy][px + dx];
            int32_t fg = color(t);
            char *gly = glyph(t);
            if (dy == 0 && dx == 0) {
                off += snprintf(row + off, sizeof row - off, "\x1b[44m\x1b[%dm%s\x1b[40m", fg, gly);
            } else {
                off += snprintf(row + off, sizeof row - off, "\x1b[%dm%s", fg, gly);
            }
            if (dx < 2) {
                off += snprintf(row + off, sizeof row - off, " ");
            }
        }
        if (dy == 2) {
            off += snprintf(row + off, sizeof row - off, "\x1b[37m");
        }
        tx("%s", row);
    }
}
int32_t color(char t) {
    return res_color(t);
}
char *glyph(char t) {
    return (char *)res_glyph(t);
}

void weaponoff() {
    char c;
    int32_t i, x;

    clw();
    tx("Who? (1-4)\r\n");
    c = key();
    if (c >= '1' && c <= '4') {
        if (state.p[state.cpl].wp[c - 48] == 0) {
            clw();
            tx("%s has no weapon!\r\n", pronoun(c - 48, 1));
            ryl_delay(800);
            clw();
            return;
        }
        for (i = 1, x = 0; i < 17; i++) {
            if (!state.p[state.cpl].inventory[i]) {
                x = i;
                break;
            }
        }
        if (!x) {
            clw();
            tx("No room!\r\n");
            ryl_delay(600);
            clw();
            return;
        } else {
            clw();
            tx("%s removed.\r\n", object(state.p[state.cpl].wp[c - 48]));
            ryl_delay(800);
            state.p[state.cpl].inventory[x] = state.p[state.cpl].wp[c - 48];
            state.p[state.cpl].wp[c - 48] = 0;
            clw();
            return;
        }
    }
    clw();
}
void armouroff() {
    char c;
    int32_t i, x;

    clw();
    tx("Who? (1-4)\r\n");
    c = key();
    if (c >= '1' && c <= '4') {
        if (state.p[state.cpl].ar[c - 48] == 0) {
            clw();
            tx("%s has no armour!\r\n", pronoun(c - 48, 1));
            ryl_delay(800);
            clw();
            return;
        }
        for (i = 1, x = 0; i < 17; i++) {
            if (!state.p[state.cpl].inventory[i]) {
                x = i;
                break;
            }
        }
        if (!x) {
            clw();
            tx("No room!\r\n");
            ryl_delay(600);
            clw();
            return;
        } else {
            clw();
            tx("%s removed.\r\n", object(state.p[state.cpl].ar[c - 48]));
            ryl_delay(800);
            state.p[state.cpl].inventory[x] = state.p[state.cpl].ar[c - 48];
            state.p[state.cpl].ar[c - 48] = 0;
            clw();
            return;
        }
    }
    clw();
}
void weaponr() {
    int32_t oc, temp;
    char d, b;

    clw();
    invlist();
    d = key();
    d = toupper(d);
    oc = 0;
    if (d >= 'A' && d <= 'P') {
        oc = state.p[state.cpl].inventory[d - 64];
        if (oc > 19 || oc == 0) {
            clw();
            tx("Not a valid weapon.\r\n");
            ryl_delay(600);
            clw();
            return;
        }
    } else {
        clw();
        return;
    }

    clw();
    tx("Who? (1-4)\r\n");
    b = key();
    if (b < '1' || b > '4') {
        clw();
        return;
    }

    if (state.p[state.cpl].dead[b - 48]) {
        clw();
        tx("Dead.\r\n");
        return;
    }
    temp = state.p[state.cpl].wp[b - 48];
    state.p[state.cpl].wp[b - 48] = state.p[state.cpl].inventory[d - 64];
    state.p[state.cpl].inventory[d - 64] = temp;
    clw();
    tx("Weapon changed.\r\n");
    ryl_delay(600);
    clw();
    return;
}
void armourr() {
    int32_t oc, temp;
    char d, b;

    clw();
    invlist();
    d = key();
    d = toupper(d);
    oc = 0;
    if (d >= 'A' && d <= 'P') {
        oc = state.p[state.cpl].inventory[d - 64];
        if (oc < 20 || oc > 29 || oc == 0) {
            clw();
            tx("Not a valid armour.\r\n");
            ryl_delay(600);
            clw();
            return;
        }
    } else {
        clw();
        return;
    }

    clw();
    tx("Who? (1-4)\r\n");
    b = key();
    if (b < '1' || b > '4') {
        clw();
        return;
    }

    if (state.p[state.cpl].dead[b - 48]) {
        clw();
        tx("Dead.\r\n");
        return;
    }
    temp = state.p[state.cpl].ar[b - 48];
    state.p[state.cpl].ar[b - 48] = state.p[state.cpl].inventory[d - 64];
    state.p[state.cpl].inventory[d - 64] = temp;
    clw();
    tx("Armour changed.\r\n");
    ryl_delay(600);
    clw();
    return;
}
uint8_t drop() {
    char d;
    int32_t i, y;

    clw();
    invlist();
    d = key();
    d = toupper(d);
    if (d >= 'A' && d <= 'P') {
        clw();
        if (state.p[state.cpl].inventory[d - 64]) {
            tx("You drop the\r\n");
            tx("%s.\r\n", object(state.p[state.cpl].inventory[d - 64]));
            ryl_delay(1000);
            if (state.p[state.cpl].inventory[d - 64] == 47) {
                for (i = 1, y = 0; i < 17; i++) {
                    if (state.p[state.cpl].inventory[i] == 47) {
                        y++;
                    }
                }
                y--;
                if (state.p[state.cpl].food > (150 + 150 * y)) {
                    clw();
                    tx("You are forced to\r\n");
                    tx("leave some of your\r\n");
                    tx("food behind...\r\n");
                    ryl_delay(1000);
                    state.p[state.cpl].food = (150 + 150 * y);
                }
            }
            state.p[state.cpl].inventory[d - 64] = 0;
            clw();
            return (uint8_t)(d - 64);
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
void camp() {
    int32_t i, death, guard = 0, temperature, x;
    char c, d;

    if (state.hours_pigg > 0) {
        clw();
        tx("Not tired.\r\n");
        ryl_delay(600);
        clw();
        return;
    }

    if (state.p[state.cpl].dead[1] && state.p[state.cpl].dead[2] && state.p[state.cpl].dead[3] && state.p[state.cpl].dead[4]) {
        return;
    }

    clr_map_window();

    for (i = 1, x = 0; i < 17; i++) {
        if (state.p[state.cpl].inventory[i] == 34) {
            x = 1;
        }
    }

    if (x) {
        blitgfx(17);
    } else {
        blitgfx(18);
    }
    clw();

    tx("Camp.\r\n\r\n");
    tx("Post a guard?\r\n\r\n");
    tx("%s", res_text("common.yes"));
    tx("%s", res_text("common.no"));
    c = key();
    c = toupper(c);
    if (c == 'Y') {
        while (1) {
            clw();
            tx("Who? (1-4)\r\n");
            d = key();
            d = toupper(d);
            if (d >= '1' && d <= '4') {
                if (!(state.p[state.cpl].dead[d - 48])) {
                    break;
                } else {
                    clw();
                    tx("Dead.\r\n");
                    ryl_delay(1000);
                }
            } else {
                clw();
                tx("Not valid!\r\n");
                ryl_delay(1000);
            }
        }
        guard = d - 48;
        clw();
        tx("%s stands guard.\r\n", state.p[state.cpl].name[d - 48]);
        tx("%s won't get any\r\n", pronoun(d - 48, 1));
        tx("health back during\r\n");
        tx("this rest.\r\n\r\n");
        ryl_pause();
    }
    clw();
    tx("Zzz...\r\n");
    ryl_delay(1500);

    /* TEMPERATURE CHECK */

    temperature = ryl_random(1, 15);
    if (temperature >= 6 && temperature < 10) {
        clw();
        tx("Low Temperature.\r\n");
        ryl_delay(600);
        for (i = 1, x = 0; i < 17; i++) {
            if (state.p[state.cpl].inventory[i] == 33 || state.p[state.cpl].inventory[i] == 34) {
                x = 1;
                break;
            }
        }
        if (!x) {
            tx("No good equipment,\r\n");
            tx("Party loses 5 health.\r\n");
            ryl_delay(1500);
            for (i = 1; i < 5; i++) {
                state.p[state.cpl].hp[i] -= 5;
                if (state.p[state.cpl].hp[i] < 1) {
                    state.p[state.cpl].hp[i] = 1;
                }
            }
        }
    }
    if (temperature >= 10) {
        tx("Very low temperature.\r\n");
        for (i = 1, x = 0; i < 17; i++) {
            if (state.p[state.cpl].inventory[i] == 34) {
                x = 1;
                break;
            }
        }
        ryl_delay(600);
        if (!x) {
            tx("No good equipment,\r\n");
            tx("Party loses 10 health.\r\n");
            ryl_delay(1500);
            for (i = 1; i < 5; i++) {
                state.p[state.cpl].hp[i] -= 5;
                if (state.p[state.cpl].hp[i] < 1) {
                    state.p[state.cpl].hp[i] = 1;
                }
            }
        }
    }

    /* INCREASE TIME */

    for (i = 1; i < 9; i++) {
        state.tid++;
        if (state.tid == 25) {
            state.tid = 1;
        }
    }
    state.hours_pigg = 14;
    if (state.p[state.cpl].food < 4 * state.p[state.cpl].pl) {
        state.p[state.cpl].food = 0;
    } else {
        state.p[state.cpl].food -= 4 * state.p[state.cpl].pl;
    }
    if (state.p[state.cpl].food < 0) {
        state.p[state.cpl].food = 0;
    }
    for (i = 1; i < 5; i++) {
        if (i != guard) {
            if (!state.p[state.cpl].dead[i]) {
                state.p[state.cpl].hp[i] += 15;
            }
            if (state.p[state.cpl].hp[i] > state.p[state.cpl].h[i]) {
                state.p[state.cpl].hp[i] = state.p[state.cpl].h[i];
            }
        }
    }
    if (ryl_chance(19)) {
        clw();
        tx("Ambush!!!\r\n");
        ryl_delay(1000);
        if (!guard && state.p[state.cpl].pl > 1) {
            death = ryl_random(1, 4);
            tx("%s is dead\r\n", state.p[state.cpl].name[death]);
            tx("before you can\r\n");
            tx("react!!!\r\n\r\n");
            state.p[state.cpl].hp[death] = 0;
            state.p[state.cpl].dead[death] = 1;
            state.p[state.cpl].pl--;
            ryl_pause();
        }
        encounter(ryl_random(1, 10), 0);
        //		update_map();
    } else {
        clw();
        tx("You wake up 8 hours\r\n");
        tx("later. Well rested.\r\n\r\n");
        ryl_pause();
        clr_map_window();
        clw();
    }
    update_map();
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
        if (d >= 'A' && d <= 'P') {
            oc = state.p[state.cpl].inventory[d - 64];
            if (oc < 36 || oc > 38) {
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
    tx("Who? (1-4)\r\n");
    while (1) {
        c = key();
        c = toupper(c);
        if (c >= '1' && c <= '4') {
            if (!state.p[state.cpl].dead[c - 48]) {
                break;
            } else {
                clw();
                tx("Dead.\r\n");
                ryl_delay(600);
                clw();
            }
        }
    }
    state.p[state.cpl].inventory[d - 64] = 0;
    clw();
    tx("Gulp!\r\n\r\n");
    ryl_delay(600);

    switch (oc) {
        case 36:
            tx("The healing brew warms\r\n");
            tx("%s inside...\r\n\r\n", pronoun(c - 48, 2));
            ryl_pause();
            clw();
            state.p[state.cpl].hp[c - 48] += (15 + state.p[state.cpl].lv[c - 48]);
            if (state.p[state.cpl].hp[c - 48] > state.p[state.cpl].h[c - 48]) {
                state.p[state.cpl].hp[c - 48] = state.p[state.cpl].h[c - 48];
            }
            break;

        case 37:
            tx("Horrible pain sears %s\r\n", pronoun(c - 48, 3));
            tx("entrails as the poison\r\n");
            tx("begins to work.\r\n\r\n");
            ryl_pause();
            clw();
            state.p[state.cpl].hp[c - 48] -= (15 + state.p[state.cpl].lv[c - 48]);
            if (state.p[state.cpl].hp[c - 48] < 1) {
                state.p[state.cpl].hp[c - 48] = 1;
            }
            break;

        case 38:
            if (state.p[state.cpl].cl[c - 48] == 3 || state.p[state.cpl].cl[c - 48] == 4) {
                tx("It tingles slightly in\r\n");
                tx("%s body, as all his\r\n", pronoun(c - 48, 3));
                tx("spell points return.\r\n\r\n");
                ryl_pause();
                clw();
                state.p[state.cpl].ma[c - 48] = state.p[state.cpl].wi[c - 48];
            } else {
                tx("The potion has no special\r\n");
                tx("effect on this character.\r\n\r\n");
                ryl_pause();
                clw();
            }
            break;
    }
}
void hunt() {
    char c;
    int32_t fdplus;

    while (1) {
        clw();
        tx("Who? (1-4)\r\n");
        c = key();
        c = toupper(c);
        if (c >= '1' && c <= '4') {
            if (!state.p[state.cpl].dead[c - 48]) {
                break;
            } else {
                clw();
                tx("Dead.\r\n");
                ryl_delay(600);
            }
        }
    }
    if (state.p[state.cpl].cl[c - 48] != 6) {
        clw();
        tx("%s is not a ranger.\r\n", pronoun(c - 48, 1));
        ryl_delay(700);
        clw();
        return;
    }
    clw();
    if (state.p[state.cpl].tid_kvar < 5) {
        tx("Too few moves for that.\r\n");
        ryl_delay(1000);
        return;
    }
    state.p[state.cpl].tid_kvar -= 5;
    tx("%s tries\r\n", state.p[state.cpl].name[c - 48]);
    tx("to trap a rabbit...\r\n");
    ryl_delay(1400);
    if (ryl_chance(45)) {
        fdplus = ryl_random(10, 19) + state.p[state.cpl].lv[1];
        tx("...and succeeds.\r\n");
        tx("The party gains\r\n");
        tx("%d food rations.\r\n\r\n", fdplus);
        state.p[state.cpl].food += fdplus;
        ryl_pause();
        clw();
        return;
    }
    tx("...but fails.\r\n\r\n");
    ryl_pause();
    clw();
}
void spell(int32_t place) {
    char b, c, d;
    int32_t i, vem, x, oc;
    pm_t caster;
    const ryl_spell *s;

    clw();
    tx("Who? (1-4)\r\n");
    while (1) {
        c = key();
        if (c >= '1' && c <= '4') {
            break;
        }
    }
    vem = (c - 48);
    caster = pm_cur(vem);

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

    switch (s->effect) {
        case SE_HEAL:
            clw();
            if (pm_ma(caster) < (s->base_cost + pm_lv(caster))) {
                tx("%s", res_text("spell.not_enough_pts"));
                ryl_delay(1000);
                clw();
                break;
            }
            state.p[state.cpl].ma[vem] -= (s->base_cost + pm_lv(caster));
            tx("Who? (1-4)\r\n");
            while (1) {
                d = key();
                if (d >= '1' && d <= '4') {
                    break;
                }
            }
            x = ryl_random(10, 19) + (state.p[state.cpl].lv[vem] * 2);
            if (state.p[state.cpl].dead[d - 48]) {
                clw();
                tx("Dead.\r\n");
                ryl_delay(600);
                clw();
                break;
            }
            state.p[state.cpl].hp[d - 48] += x;
            if (state.p[state.cpl].hp[d - 48] > state.p[state.cpl].h[d - 48]) {
                state.p[state.cpl].hp[d - 48] = state.p[state.cpl].h[d - 48];
            }
            clw();
            tx("%s heals\r\n", state.p[state.cpl].name[vem]);
            tx("%s\r\n", state.p[state.cpl].name[d - 48]);
            tx("for %d hit points...\r\n", x);
            ryl_delay(1500);
            clw();
            break;

        case SE_RESTORATION:
            clw();
            if (pm_ma(caster) < (s->base_cost + pm_lv(caster))) {
                tx("%s", res_text("spell.not_enough_pts"));
                ryl_delay(1000);
                clw();
                break;
            }
            state.p[state.cpl].ma[vem] -= (s->base_cost + pm_lv(caster));
            tx("Who? (1-4)\r\n");
            while (1) {
                d = key();
                if (d >= '1' && d <= '4') {
                    break;
                }
            }
            state.p[state.cpl].hp[d - 48] = state.p[state.cpl].h[d - 48];
            clw();
            tx("%s heals\r\n", state.p[state.cpl].name[vem]);
            tx("%s\r\n", state.p[state.cpl].name[d - 48]);
            tx("fully...\r\n");
            ryl_delay(1500);
            clw();
            break;

        case SE_REBIRTH:
            clw();
            if (pm_ma(caster) < (s->base_cost + pm_lv(caster))) {
                tx("%s", res_text("spell.not_enough_pts"));
                ryl_delay(1000);
                clw();
                break;
            }
            tx("Who? (1-4)\r\n");
            while (1) {
                d = key();
                if (d >= '1' && d <= '4') {
                    break;
                }
            }
            if (!state.p[state.cpl].dead[d - 48]) {
                tx("%s is alive and\r\n", state.p[state.cpl].name[d - 48]);
                tx("healthy.\r\n");
                ryl_delay(1500);
                break;
            }
            state.p[state.cpl].ma[vem] -= (s->base_cost + pm_lv(caster));
            state.p[state.cpl].dead[d - 48] = 0;
            state.p[state.cpl].hp[d - 48] = 1;
            clw();
            tx("%s's eyelids\r\n", state.p[state.cpl].name[d - 48]);
            tx("flicker, and finally\r\n");
            tx("%s opens %s eyes!\r\n", pronoun(d - 48, 1), pronoun(d - 48, 3));
            ryl_delay(1700);
            clw();
            break;

        case SE_BANISH:
            clw();
            if (pm_ma(caster) < (s->base_cost + pm_lv(caster))) {
                tx("%s", res_text("spell.not_enough_pts"));
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
            state.p[state.cpl].ma[vem] -= (s->base_cost + pm_lv(caster));
            clw();
            tx("%s raises %s hands.\r\n", state.p[state.cpl].name[vem], pronoun(vem, 3));
            tx("%s gathers divine power\r\n", pronoun(vem, 1));
            tx("between %s palms, and\r\n", pronoun(vem, 3));
            tx("suddenly clasps them.\r\n");
            tx("together over %s head.\r\n", pronoun(vem, 3));
            tx("%s", res_text("spell.priest.banish.thunder"));
            ryl_pause();
            clw();
            state.banished = 1;
            break;

        case SE_REVELATION:
            clw();
            if (pm_ma(caster) < (s->base_cost + pm_lv(caster))) {
                tx("%s", res_text("spell.not_enough_pts"));
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
            state.p[state.cpl].ma[vem] -= (s->base_cost + pm_lv(caster));
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
                for (i = 1, x = 0; i < 17; i++) {
                    if (!state.p[state.cpl].inventory[i]) {
                        state.p[state.cpl].inventory[i] = oc;
                        x = 1;
                        break;
                    }
                }
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
                tx("%s", res_text("spell.not_enough_pts"));
                ryl_delay(1000);
                clw();
                break;
            }
            state.p[state.cpl].ma[vem] -= (s->base_cost + pm_lv(caster));
            clw();
            tx("%s carefully\r\n", state.p[state.cpl].name[vem]);
            tx("prepares the ritual of\r\n");
            tx("Teleportation. %s spends\r\n", pronoun(vem, 1));
            tx("an hour chanting mantras\r\n");
            tx("and burning blue candles.\r\n\r\n");
            state.tid++;
            if (state.tid == 25) {
                state.tid = 1;
            }
            ryl_pause();
            clw();
            tx("%s picks up a stone slab\r\n", pronoun(vem, 1));
            tx("%s", res_text("spell.priest.teleport.satchel"));
            tx("%s wants you to teleport\r\n", pronoun(vem, 1));
            tx("to.\r\n\r\n");
            ryl_pause();
            clw();
            tx("%s", res_text("spell.priest.teleport.compass"));
            while (1) {
                d = key();
                if (d == '8' || d == '4' || d == '6' || d == '2') {
                    break;
                }
            }
            clw();
            tx("%s", res_text("spell.priest.teleport.how_far"));
            while (1) {
                b = key();
                if (b >= '1' && b <= '9') {
                    break;
                }
            }
            switch (d) {
                case '2':
                    for (i = 1; i < ((b - 48) * 2) + 1; i++) {
                        if (state.map[state.p[state.cpl].y + 1][state.p[state.cpl].x] != 'm') {
                            state.p[state.cpl].y++;
                        }
                    }
                    break;
                case '4':
                    for (i = 1; i < ((b - 48) * 2) + 1; i++) {
                        if (state.map[state.p[state.cpl].y][state.p[state.cpl].x - 1] != 'm') {
                            state.p[state.cpl].x--;
                        }
                    }
                    break;
                case '6':
                    for (i = 1; i < ((b - 48) * 2) + 1; i++) {
                        if (state.map[state.p[state.cpl].y][state.p[state.cpl].x + 1] != 'm') {
                            state.p[state.cpl].x++;
                        }
                    }
                    break;
                case '8':
                    for (i = 1; i < ((b - 48) * 2) + 1; i++) {
                        if (state.map[state.p[state.cpl].y - 1][state.p[state.cpl].x] != 'm') {
                            state.p[state.cpl].y--;
                        }
                    }
                    break;
            }
            tx("%s", res_text("spell.priest.teleport.arrived"));
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
                tx("%s", res_text("spell.not_enough_pts"));
                ryl_delay(1000);
                clw();
                break;
            }
            state.p[state.cpl].ma[vem] -= (s->base_cost + pm_lv(caster));
            tx("%s utters\r\n", state.p[state.cpl].name[vem]);
            tx("%s", res_text("spell.priest.earthquake.incantation"));
            ryl_delay(2000);
            tx("%s", res_text("spell.priest.earthquake.eruption"));
            ryl_delay(3000);
            clr_map_window();
            blitgfx(cavepic);
            clw();
            tx("%s", res_text("cave.found_prompt"));
            while (1) {
                c = key();
                c = toupper(c);
                if (c == 'L' || c == 'E') {
                    break;
                }
            }
            if (c == 'E') {
                if (state.p[state.cpl].tid_kvar > 10) {
                    state.p[state.cpl].tid_kvar -= 10;
                } else {
                    clw();
                    tx("%s", res_text("cave.no_moves"));
                    ryl_pause();
                    clr_map_window();
                    clw();
                    break;
                }
                clr_map_window();
                dungeon(ryl_random(5, 18), 0);
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

    for (i = 1; i < state.antal_spelare + 1; i++) {
        pts[i] = (state.p[i].exp[1] + state.p[i].exp[2] + state.p[i].exp[3] + state.p[i].exp[4]) / 4;
        lvl[i] = (state.p[i].lv[1] + state.p[i].lv[2] + state.p[i].lv[3] + state.p[i].lv[4]) / 4;
        kills[i] = state.p[i].kills;
        strcpy(pname[i], state.p[i].partyname);
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

    n = state.antal_spelare - 1;
    o = state.antal_spelare - 1;

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

    for (i = 1; i < state.antal_spelare + 1; i++) {
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

    for (i = 1; i < state.antal_spelare + 1; i++) {
        snprintf(str, sizeof str, "        %2d.    %-30s  %6d          %3d\n", i, pname[i], pts[i], lvl[i]);
        fputs(str, fh);
    }
    fputs("\n", fh);
    fclose(fh);
}
