#include "ryl.h"
#include "res.h"

/*
** Never find and never fool
** The first one drinks the poison pool
** The third one reads the ancient tome
** The last one breaks the double dome
*/

void encounter(int32_t foe, int32_t mandatory) {
    assert(foe >= 1);
    assert(state.cpl >= 1 && state.cpl <= state.antal_spelare);
    char fn[80], str[80], b, c, d;
    int32_t n1 = 0, n2 = 0; /* Min & Max nr */
    int32_t h1 = 0, h2 = 0; /* Min & Max hp */
    int32_t sp;             /* Fire frost hold */
    int32_t im;             /* Immune to magic? */
    int32_t ws = 0;         /* WS */
    int32_t x, i, mh[15], deadc[15], vem, dp, g, noc, mhtemp, spelldamage;
    int32_t oc;
    uint8_t defend = 0;

    int32_t antimagic = 0, confused = 0, held = 0, playerheld = 0, playerconfused = 0;

    clw();
    tx("ENCOUNTER!\r\n\r\n");
    clr_map_window();
    blitgfx(encounterpic + ryl_random(0, 4));
    ryl_delay(400);
    sp = 0;
    im = 0;
    {
        const ryl_monster *m = res_monster(foe);
        if (m) {
            strncpy(fn, m->name, sizeof fn - 1);
            fn[sizeof fn - 1] = 0;
            n1 = m->n1;
            n2 = m->n2;
            h1 = m->h1;
            h2 = m->h2;
            ws = m->ws;
            sp = m->sp;
            im = m->im;
        }
        if (foe == 31) {
            n2 = 2 + state.p[state.cpl].lv[1] / 2;
        }
    }

    noc = ryl_random(1, n2);
    if (noc < n1) {
        noc = n1;
    }
    h1 += ((state.p[state.cpl].lv[1] + state.p[state.cpl].lv[2] + state.p[state.cpl].lv[3] + state.p[state.cpl].lv[4]) / 4) * 2;
    if (state.p[state.cpl].lv[1] > 2) {
        h1 += state.p[state.cpl].lv[1] * 2;
    }
    h2 += state.p[state.cpl].lv[1] * 3;
    if (h1 > h2) {
        h1 = h2;
    }

    if (noc > 8) {
        noc = 8;
    }
    assert(noc >= 1 && noc <= 8);
    assert(h1 <= h2);

    ws += state.p[state.cpl].lv[1];

    for (i = 1; i < noc + 1; i++) {
        mhtemp = ryl_random(1, h2);
        if (mhtemp < h1) {
            mhtemp = h1;
        }
        mh[i] = mhtemp;
        deadc[i] = 0;
    }

    snprintf(str, sizeof str, "%d %s", noc, fn);
    if (noc > 1) {
        str_append(str, sizeof str, "s");
    }
    while (1) {
        clw();
        tx("You face\r\n");
        tx("%s\r\n\r\n", str);
        tx("(F)ight\r\n");
        tx("(R)un\r\n");

        c = key();
        c = toupper(c);
        if (c == 'F' || c == 'R') {
            break;
        }
    }
    if (c == 'R') {
        if (!mandatory) {
            if (ryl_chance(95)) {
                clw();
                tx("%s", res_text("strid.run.coward"));
                ryl_delay(800);
                clw();
                clr_map_window();
                return;
            } else {
                clw();
                tx("%s", res_text("strid.run.fail_away"));
                ryl_delay(800);
                clw();
            }
        } else {
            clw();
            tx("%s", res_text("strid.run.desperate_fail"));
            ryl_delay(800);
            clw();
        }
    }

    clw();
    tx("Blood shall flow!\r\n");
    ryl_delay(1000);

    g = 1;
    while (1) {
        clw();
        if (!state.p[state.cpl].dead[g] && !playerheld) {
        combat:
            tx("%s\r\n", state.p[state.cpl].name[g]);
            tx("What now?\r\n\r\n");
            tx("(A)ttack\r\n");
            tx("(D)efend\r\n");
            tx("(U)se Item\r\n");
            tx("(B)attle Cry\r\n");
            if (state.p[state.cpl].cl[g] == 3) {
                tx("(C)ast Spell\r\n");
            }
            while (1) {
                c = key();
                c = toupper(c);
                if (c == 'U' || c == 'D' || c == 'A' || c == 'B' || c == 'C') {
                    break;
                }
            }

            /****************************************************************************/
            if (c == 'U') {
                clw();
                tx("Use what?\r\n\r\n");
                invlist();
                d = key();
                d = toupper(d);
                oc = 0;
                if (d >= 'A' && d <= 'P') {
                    oc = state.p[state.cpl].inventory[d - 64];
                    if (oc < 100) {
                        clw();
                        tx("Not possible.\r\n");
                        ryl_delay(1000);
                        clw();
                        goto combat;
                    } else {
                        if (im) {
                            state.p[state.cpl].inventory[d - 64] = 0;
                            clw();
                            tx("%s", res_text("strid.item.magic_immune"));
                            ryl_delay(1000);
                            clw();
                        } else {
                            switch (oc) {
                                case 100:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    for (i = 1, vem = 0; i < noc + 1; i++) {
                                        if (!deadc[i]) {
                                            vem = i;
                                            break;
                                        }
                                    }
                                    if (vem) {
                                        spelldamage = ryl_random(5, 11) + state.p[state.cpl].lv[g];
                                        clw();
                                        tx("%s chills %s\r\n", state.p[state.cpl].name[g], fn);
                                        tx("for %d damage pts.\r\n", spelldamage);
                                        ryl_delay(1000);
                                        mh[vem] -= spelldamage;
                                        if (mh[vem] <= 0) {
                                            mh[vem] = 0;
                                            tx("...KILLED!");
                                            ryl_delay(600);
                                            deadc[vem] = 1;
                                        }
                                    }
                                    break;

                                case 101:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    for (i = 1; i < noc + 1; i++) {
                                        spelldamage = state.p[state.cpl].lv[g] * ryl_random(3, 8);
                                        if (!deadc[i]) {
                                            clw();
                                            tx("%s burns %s\r\n", state.p[state.cpl].name[g], fn);
                                            tx("for %d pts!\r\n", spelldamage);
                                            ryl_delay(1000);
                                            mh[i] -= spelldamage;
                                            if (mh[i] <= 0) {
                                                mh[i] = 0;
                                                tx("%s", res_text("strid.killed"));
                                                ryl_delay(600);
                                                deadc[i] = 1;
                                            }
                                        }
                                    }
                                    break;

                                case 102:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s throws the\r\n", state.p[state.cpl].name[g]);
                                    tx("%s", res_text("strid.item.sparks_bump"));
                                    ryl_pause();
                                    clw();
                                    ws /= 2;
                                    break;

                                case 103:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s throws the\r\n", state.p[state.cpl].name[g]);
                                    tx("%s", res_text("strid.item.light_blind"));
                                    ryl_pause();
                                    clw();
                                    ws = 5;
                                    break;

                                case 104:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    for (i = 1, vem = 0; i < noc + 1; i++) {
                                        if (!deadc[i]) {
                                            vem = i;
                                            break;
                                        }
                                    }
                                    if (vem) {
                                        clw();
                                        tx("%s summons death\r\n", state.p[state.cpl].name[g]);
                                        tx("to take a %s\r\n", fn);
                                        tx("away...\r\n");
                                        ryl_delay(2000);
                                        tx("%s", res_text("strid.killed"));
                                        ryl_delay(600);
                                        deadc[vem] = 1;
                                        mh[vem] = 0;
                                        clw();
                                    }
                                    break;

                                case 105:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s", res_text("strid.prompt.on_whom"));
                                    while (1) {
                                        d = key();
                                        if (d >= '1' && d <= '4') {
                                            break;
                                        }
                                    }
                                    state.p[state.cpl].hp[d - 48] = state.p[state.cpl].h[d - 48];
                                    clw();
                                    tx("%s is fully\r\n", state.p[state.cpl].name[d - 48]);
                                    tx("healed.\r\n");
                                    ryl_delay(1000);
                                    clw();
                                    break;

                                case 106:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s", res_text("strid.spell.morale_quest"));
                                    ryl_pause();
                                    clw();
                                    for (i = 1; i < 5; i++) {
                                        state.p[state.cpl].ws[i] += 3;
                                        state.p[state.cpl].h[i] += 3;
                                        state.p[state.cpl].ch[i] += 3;
                                        state.p[state.cpl].wi[i] += 3;
                                    }
                                    break;

                                case 107:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s", res_text("strid.spell.party_healed"));
                                    ryl_pause();
                                    clw();
                                    for (i = 1; i < 5; i++) {
                                        if (!state.p[state.cpl].dead[i]) {
                                            state.p[state.cpl].hp[i] = state.p[state.cpl].h[i];
                                        }
                                    }
                                    break;

                                case 108:
                                    clw();
                                    tx("%s", res_text("strid.prompt.on_whom"));
                                    while (1) {
                                        b = key();
                                        if (b >= '1' && b <= '4') {
                                            break;
                                        }
                                    }
                                    if (state.p[state.cpl].dead[b - 48]) {
                                        state.p[state.cpl].dead[b - 48] = 0;
                                        clw();
                                        tx("%s opens %s eyes!\r\n", state.p[state.cpl].name[b - 48],
                                           sextext(b - 48, 3));
                                        ryl_delay(1000);
                                        state.p[state.cpl].pl++;
                                        state.p[state.cpl].dead[b - 48] = 0;
                                        state.p[state.cpl].hp[b - 48] = 1;
                                        state.p[state.cpl].inventory[d - 64] = 0;
                                    } else {
                                        clw();
                                        tx("%s is not dead.\r\n", state.p[state.cpl].name[b - 48]);
                                        ryl_delay(1000);
                                        goto combat;
                                    }
                                    break;

                                case 109:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s throws Kael's\r\n", state.p[state.cpl].name[g]);
                                    tx("axe at the enemy!\r\n\r\n");
                                    ryl_delay(1500);
                                    tx("%s", res_text("strid.item.ghost_axe.wounds"));
                                    ryl_delay(4000);
                                    clw();
                                    tx("%s", res_text("strid.item.battle_cry"));
                                    ryl_delay(3000);
                                    for (i = 1; i < noc + 1; i++) {
                                        if (!deadc[i]) {
                                            mh[i] = ryl_random(1, 3);
                                        }
                                    }
                                    break;

                                case 110:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s pulls the\r\n", state.p[state.cpl].name[g]);
                                    tx("holy pin, and lobbeth\r\n");
                                    tx("the holy handgrenade\r\n");
                                    tx("at the foes...\r\n\r\n");
                                    ryl_delay(3000);
                                    tx("KA-BLAM!\r\n\r\n");
                                    ryl_delay(1500);
                                    clw();
                                    for (i = 1; i < noc + 1; i++) {
                                        if (!deadc[i]) {
                                            mh[i] /= 4;
                                            mh[i]++;
                                        }
                                    }
                                    break;

                                case 111:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s plays the\r\n", state.p[state.cpl].name[g]);
                                    tx("Flute of Madness.\r\n");
                                    tx("It produces a shrill,\r\n");
                                    tx("fragile sound, and breaks.\r\n");
                                    tx("Nothing happens.\r\n\r\n");
                                    ryl_pause();
                                    clw();
                                    break;
                            }
                        }
                    }
                }
            }
            /****************************************************************************/
            if (c == 'C' && pm_is_mage(pm_cur(g))) {
                pm_t caster = pm_cur(g);
                const ryl_spell *s;
                clw();
                for (int slot = 1; slot <= 6; slot++) {
                    s = res_spell("mage", slot);
                    if (!s) continue;
                    tx("%d. %-13s(%3d/%3d)\r\n", slot, s->name,
                       s->base_cost + pm_lv(caster), pm_ma(caster));
                }
                tx("7. Abort\r\n");
                while (1) {
                    d = key();
                    if (d >= '1' && d <= '7') {
                        break;
                    }
                }
                if (im) {
                    clw();
                    tx("%s", res_text("strid.spell.fizzle"));
                    ryl_delay(2000);
                }
                if (d == '7') {
                    clw();
                    goto combat;
                }
                if (im) {
                    s = NULL;  /* swallow the key press, no effect */
                } else {
                    s = res_spell("mage", d - 48);
                }
                if (s) {
                    if (pm_ma(caster) < (s->base_cost + pm_lv(caster))) {
                        clw();
                        tx("%s", res_text("strid.spell.not_enough_magic"));
                        ryl_delay(1000);
                    } else {
                        state.p[state.cpl].ma[g] -= (s->base_cost + pm_lv(caster));
                        switch (s->effect) {
                            case SE_FROST:
                                while (1) {
                                    clw();
                                    tx("%s", res_text("strid.prompt.who"));
                                    for (i = 1; i < noc + 1; i++) {
                                        if (deadc[i]) {
                                            strcpy(str, "Dead.");
                                        } else {
                                            strcpy(str, "Alive.");
                                        }
                                        tx("%c. %-7s (%3d hp)\r\n", 64 + i, str, mh[i]);
                                    }
                                    d = key();
                                    d = toupper(d);
                                    if (d >= 'A' && d <= 64 + noc && !deadc[d - 64]) {
                                        break;
                                    }
                                }
                                vem = d - 64;
                                spelldamage = pm_lv(caster) * ryl_random(2, 8);
                                clw();
                                tx("%s chills the\r\n", pm_name(caster));
                                tx("opponent for %d\r\n", spelldamage);
                                tx("damage pts.\r\n");
                                ryl_delay(1600);
                                mh[vem] -= spelldamage;
                                if (mh[vem] <= 0) {
                                    mh[vem] = 0;
                                    tx("%s", res_text("strid.killed"));
                                    ryl_delay(600);
                                    deadc[vem] = 1;
                                }
                                break;
                            case SE_FIRE:
                                for (i = 1; i < noc + 1; i++) {
                                    spelldamage = pm_lv(caster) * ryl_random(1, 4);
                                    if (!deadc[i]) {
                                        clw();
                                        tx("%s burns %s\r\n", pm_name(caster), fn);
                                        tx("for %d pts!\r\n", spelldamage);
                                        ryl_delay(1000);
                                        mh[i] -= spelldamage;
                                        if (mh[i] <= 0) {
                                            mh[i] = 0;
                                            tx("%s", res_text("strid.killed"));
                                            ryl_delay(600);
                                            deadc[i] = 1;
                                        }
                                    }
                                }
                                break;
                            case SE_CONFUSION:
                                clw();
                                tx("%s utters a word.\r\n", pm_name(caster));
                                tx("%s", res_text("strid.spell.mirror"));
                                ryl_delay(2000);
                                ws /= 2;
                                confused = 1;
                                break;
                            case SE_DEATH:
                                while (1) {
                                    clw();
                                    tx("%s", res_text("strid.prompt.who"));
                                    for (i = 1; i < noc + 1; i++) {
                                        if (deadc[i]) {
                                            strcpy(str, "Dead.");
                                        } else {
                                            strcpy(str, "Alive.");
                                        }
                                        tx("%c. %-7s (%3d hp)\r\n", 64 + i, str, mh[i]);
                                    }
                                    d = key();
                                    d = toupper(d);
                                    if (d >= 'A' && d <= 64 + noc && !deadc[d - 64]) {
                                        break;
                                    }
                                }
                                vem = d - 64;
                                if (ryl_random(0, 199) < mh[vem]) {
                                    clw();
                                    tx("%s", res_text("strid.spell.annihilate.resist"));
                                    ryl_delay(2000);
                                    mh[vem] = 1;
                                } else {
                                    clw();
                                    tx("%s", res_text("strid.spell.annihilate.kill"));
                                    ryl_delay(2000);
                                    deadc[vem] = 1;
                                    mh[vem] = 0;
                                }
                                break;
                            case SE_HOLD:
                                clw();
                                tx("%s utters a word.\r\n", pm_name(caster));
                                tx("%s", res_text("strid.spell.hold_enemy"));
                                ryl_delay(2000);
                                held = 1;
                                break;
                            case SE_ANTIMAGIC:
                                clw();
                                tx("%s utters a word.\r\n", pm_name(caster));
                                tx("%s", res_text("strid.spell.silence"));
                                ryl_delay(2800);
                                antimagic = 1;
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
            /****************************************************************************/
            if (c == 'B') {
                clw();
                tx("HI-YAAAA!\r\n\r\n");
                ryl_delay(800);
                tx("%s", res_text("strid.cry.off_guard"));
                if (ws > 4) {
                    ws -= 4;
                }
                ryl_delay(1500);
            }
            /****************************************************************************/
            if (c == 'D') {
                if (state.p[state.cpl].hp[g] + (state.p[state.cpl].lv[g]) > state.p[state.cpl].h[g]) {
                    state.p[state.cpl].hp[g] = state.p[state.cpl].h[g];
                } else {
                    state.p[state.cpl].hp[g] += (state.p[state.cpl].lv[g]);
                }
                clw();
                if (defend < 5) {
                    tx("%s", res_text("strid.rest.gain_hits"));
                    ryl_delay(1000);
                } else {
                    tx("%s", res_text("strid.defend.stance"));
                    ryl_delay(1000);
                }
                defend++;
                if (defend > 250) {
                    defend = 250;
                }
                if (ws > 2) {
                    ws -= 2;
                }
            }
            /****************************************************************************/
            if (c == 'A') {
                while (1) {
                    clw();
                    tx("%s", res_text("strid.prompt.who"));
                    for (i = 1; i < noc + 1; i++) {
                        if (deadc[i]) {
                            strcpy(str, "Dead.");
                        } else {
                            strcpy(str, "Alive.");
                        }
                        tx("%c. %-7s (%3d hp)\r\n", 64 + i, str, mh[i]);
                    }
                    d = key();
                    d = toupper(d);
                    if (d >= 'A' && d <= 64 + noc && !deadc[d - 64]) {
                        break;
                    }
                }
                vem = d - 64;
                x = state.p[state.cpl].ws[g];
                if (playerconfused) {
                    x /= 3;
                }
                if (ryl_chance(x + 1)) {
                    /* HIT */
                    dp = obdamage(state.p[state.cpl].wp[g]);
                    dp += ryl_random(0, 4);
                    dp += state.p[state.cpl].hp[g] / 12;
                    clw();
                    tx("%s hits for\r\n", state.p[state.cpl].name[g]);
                    tx("%d damage pts!\r\n", dp);
                    ryl_delay(600);
                    mh[vem] -= dp;
                    if (mh[vem] <= 0) {
                        mh[vem] = 0;
                        deadc[vem] = 1;
                        tx("%s", res_text("strid.killed"));
                        ryl_delay(600);
                    }
                } else {
                    /* MISS */
                    clw();
                    tx("%s misses!\r\n", state.p[state.cpl].name[g]);
                    ryl_delay(600);
                }
            }
            /* SLUTET */

            for (i = 1, x = 0; i < noc + 1; i++) {
                if (!deadc[i]) {
                    x = 1;
                }
            }
            if (!x) {
                /* WIN */
                /* 100-111, 36-46 */
                clr_map_window();
                blitgfx(wonbattlepic);
                clw();
                tx("%s", res_text("strid.victory.bested"));
                tx("Each survivor gets\r\n");
                tx("%d experience pts\r\n", ((noc * (h2 / 4) * (ws / 4)) + sp * 3));
                tx("and %d gold pieces.\r\n\r\n", ((noc * (h2 / 7) * (ws / 4)) / 4));
                ryl_delay(1000);
                state.p[state.cpl].kills += noc;
                for (i = 1, x = 0; i < 17; i++) {
                    if (state.p[state.cpl].inventory[i] == 0) {
                        x = i;
                        break;
                    }
                }
                i = 0;
                if (x) {
                    if (ryl_chance(5)) {
                        if (ryl_chance(59)) {
                            i = ryl_random(36, 46);
                        } else {
                            i = ryl_random(100, 111);
                        }
                        clw();
                        tx("You find a\r\n");
                        tx("%s.\r\n", object(i));
                        ryl_delay(1500);
                        state.p[state.cpl].inventory[x] = i;
                    }
                }
                state.p[state.cpl].gold += (noc * (h2 / 7) * (ws / 4));
                for (i = 1; i < 5; i++) {
                    if (!state.p[state.cpl].dead[i]) {
                        state.p[state.cpl].exp[i] += ((noc * (h2 / 4) * (ws / 4)) + sp * 3);
                    }
                }
                expcheck();
                clr_map_window();
                clw();
                return;
            }
        }
        g++;
        /****************************************************************************/
        if (g == 5) {
            playerheld = 0;
            playerconfused = 0;
            /* MONSTERS GET NASTY */
            g = 1;
            x = 1;
            while (x < noc + 1) {
                if (state.p[state.cpl].dead[1] && state.p[state.cpl].dead[2] && state.p[state.cpl].dead[3] && state.p[state.cpl].dead[4]) {
                    losegame();
                }
                if (!deadc[x] && !held) {
                    if (!antimagic && sp &&
                        ryl_chance(15 + sp / 2)) /* MOSTERS PRACTICE NASTY MAGIC */
                    {
                        clw();
                        tx("%s casts\r\n", fn);

                        switch (ryl_random(0, 3)) {
                            case 0: /* Monster casts frost   */
                                tx("Frost Finger\r\n");
                                while (1) {
                                    vem = ryl_random(1, 4);
                                    if (!state.p[state.cpl].dead[vem]) {
                                        break;
                                    }
                                }
                                tx("on %s!\r\n", state.p[state.cpl].name[vem]);
                                ryl_delay(1000);
                                dp = h2 / 5 + ryl_random(0, 3);
                                tx("and does %d pts\r\n", dp);
                                tx("of damage!\r\n");
                                ryl_delay(1000);
                                state.p[state.cpl].hp[vem] -= dp;
                                if (state.p[state.cpl].hp[vem] <= 0) {
                                    state.p[state.cpl].hp[vem] = 0;
                                    state.p[state.cpl].dead[vem] = 1;
                                    state.p[state.cpl].pl--;
                                    tx("[31m%s DIES![37m\r\n", state.p[state.cpl].name[vem]);
                                    ryl_delay(1000);
                                }
                                break;
                            case 1: /* Monster casts fire    */
                                tx("Cloud of Fire!\r\n");
                                ryl_delay(1000);
                                for (i = 1; i < 5; i++) {
                                    if (!state.p[state.cpl].dead[i]) {
                                        clw();
                                        tx("%s is burnt for\r\n", state.p[state.cpl].name[i]);
                                        dp = h2 / 5 + ryl_random(0, 4);
                                        tx("%d damage pts!\r\n", dp);
                                        ryl_delay(1000);
                                        state.p[state.cpl].hp[i] -= dp;
                                        if (state.p[state.cpl].hp[i] <= 0) {
                                            state.p[state.cpl].hp[i] = 0;
                                            state.p[state.cpl].dead[i] = 1;
                                            state.p[state.cpl].pl--;
                                            tx("[31m%s DIES![37m\r\n", state.p[state.cpl].name[i]);
                                            ryl_delay(1000);
                                        }
                                    }
                                }
                                break;
                            case 2: /* Monster casts hold    */
                                tx("Hold Creature!\r\n");
                                ryl_delay(600);
                                tx("%s", res_text("strid.monster.hold_player"));
                                ryl_delay(1500);
                                playerheld = 1;
                                break;
                            case 3: /* Monster casts confuse */
                                tx("Confusion!\r\n");
                                ryl_delay(600);
                                tx("%s", res_text("strid.monster.confusion"));
                                ryl_delay(1500);
                                playerconfused = 1;
                                break;
                        }
                    } else {
                        while (1) {
                            vem = ryl_random(1, 4);
                            if (!state.p[state.cpl].dead[vem]) {
                                break;
                            }
                        }
                        clw();
                        tx("%s attacks\r\n", fn);
                        tx("%s!\r\n", state.p[state.cpl].name[vem]);
                        ryl_delay(400);
                        if (ryl_chance(ws + 1)) {
                            dp = mh[x] / 6;
                            dp += ryl_random(0, 9);
                            dp -= obdamage(state.p[state.cpl].ar[vem]);
                            if (dp < 1) {
                                dp = 1;
                            }
                            tx("And hits for\r\n");
                            tx("%d damage pts!!!\r\n", dp);
                            state.p[state.cpl].hp[vem] -= dp;
                            if (state.p[state.cpl].hp[vem] <= 0) {
                                if (state.p[state.cpl].pl) {
                                    state.p[state.cpl].pl--;
                                }
                                state.p[state.cpl].hp[vem] = 0;
                                state.p[state.cpl].dead[vem] = 1;
                                tx("[31m%s DIES!!![37m\r\n", state.p[state.cpl].name[vem]);
                                ryl_delay(1000);
                            }
                            ryl_delay(1000);
                        } else {
                            tx("Blow misses!\r\n");
                            ryl_delay(600);
                        }
                    }
                }
                x++;
            }
            antimagic = 0;
            if (confused) {
                confused = 0;
                ws *= 2;
            }
            held = 0;
            clw();
            if (playerheld) {
                tx("PARTY IS HELD!\r\n");
                ryl_delay(1000);
            } else {
                if (state.p[state.cpl].dead[1] && state.p[state.cpl].dead[2] && state.p[state.cpl].dead[3] && state.p[state.cpl].dead[4]) {
                    losegame();
                }
                statusline();
                tx("You still face\r\n");
                for (i = 1, x = 0; i < noc + 1; i++) {
                    if (!deadc[i]) {
                        x++;
                    }
                }
                snprintf(str, sizeof str, "%d %s", x, fn);
                if (x > 1) {
                    str_append(str, sizeof str, "s");
                }
                tx("%s\r\n\r\n", str);
                tx("(F)ight\r\n");
                tx("(R)un\r\n");
                while (1) {
                    c = key();
                    c = toupper(c);
                    if (c == 'F') {
                        break;
                    }
                    if (c == 'R') {
                        if (ryl_chance(31) || mandatory) {
                            clw();
                            tx("%s", res_text("strid.run.fail_away"));
                            ryl_delay(1000);
                            break;
                        } else {
                            clw();
                            tx("%s", res_text("strid.run.escaped"));
                            clr_map_window();
                            return;
                        }
                    }
                }
            }
        }
    }
    clr_map_window();
    clw();
}
/****************************************************************************/

void expcheck() {
    int32_t i, l, add[5];
    uint32_t e;

    for (i = 1; i < 5; i++) {
        add[i] = 0;
    }

    for (i = 1; i < 5; i++) {
        l = state.p[state.cpl].lv[i];
        e = state.p[state.cpl].exp[i];
        if (l < 2 && e >= 2000) {
            add[i]++;
        }
        if (l < 3 && e >= 4000) {
            add[i]++;
        }
        if (l < 4 && e >= 8000) {
            add[i]++;
        }
        if (l < 5 && e >= 12000) {
            add[i]++;
        }
        if (l < 6 && e >= 20000) {
            add[i]++;
        }
        if (l < 7 && e >= 28000) {
            add[i]++;
        }
        if (l < 8 && e >= 36000) {
            add[i]++;
        }
        if (l < 9 && e >= 48000) {
            add[i]++;
        }
        if (l < 10 && e >= 54000) {
            add[i]++;
        }
        if (l < 11 && e >= 64000) {
            add[i]++;
        }
        if (l < 12 && e >= 74000) {
            add[i]++;
        }
        if (l < 13 && e >= 86000) {
            add[i]++;
        }
        if (l < 14 && e >= 98000) {
            add[i]++;
        }
        if (l < 15 && e >= 110000) {
            add[i]++;
        }
        if (l < 16 && e >= 125000) {
            add[i]++;
        }
        if (l < 17 && e >= 150000) {
            add[i]++;
        }
        if (l < 18 && e >= 180000) {
            add[i]++;
        }
        if (l < 19 && e >= 240000) {
            add[i]++;
        }
        if (l < 20 && e >= 300000) {
            add[i]++;
        }
        if (l < 21 && e >= 400000) {
            add[i]++;
        }
        if (l < 22 && e >= 550000) {
            add[i]++;
        }
        if (l < 23 && e >= 700000) {
            add[i]++;
        }
        if (l < 24 && e >= 900000) {
            add[i]++;
        }
        if (l < 25 && e >= 1100000) {
            add[i]++;
        }
        if (l < 26 && e >= 1500000) {
            add[i]++;
        }
        if (l < 27 && e >= 2000000) {
            add[i]++;
        }
        if (l < 28 && e >= 2800000) {
            add[i]++;
        }
    }

    for (i = 1; i < 5; i++) {
        if (add[i]) {
            clw();
            tx("%s gains >%d<\r\n", state.p[state.cpl].name[i], add[i]);
            tx("experience levels.\r\n");
            ryl_delay(600);
            state.p[state.cpl].lv[i] += add[i];
            switch (state.p[state.cpl].cl[i]) {
                case 1:
                    state.p[state.cpl].ws[i] += 3;
                    state.p[state.cpl].h[i] += 8;
                    state.p[state.cpl].wi[i] += 1;
                    state.p[state.cpl].ch[i] += 1;
                    if (state.p[state.cpl].hp[i] < (state.p[state.cpl].h[i] * 2) / 3) {
                        state.p[state.cpl].hp[i] = (state.p[state.cpl].h[i] * 2) / 3;
                    }
                    break;
                case 2:
                    state.p[state.cpl].ws[i] += 1;
                    state.p[state.cpl].h[i] += 5;
                    state.p[state.cpl].wi[i] += 1;
                    state.p[state.cpl].ch[i] += 2;
                    if (state.p[state.cpl].hp[i] < (state.p[state.cpl].h[i] * 2) / 3) {
                        state.p[state.cpl].hp[i] = (state.p[state.cpl].h[i] * 2) / 3;
                    }
                    break;
                case 3:
                    state.p[state.cpl].ws[i] += 1;
                    state.p[state.cpl].h[i] += 4;
                    state.p[state.cpl].wi[i] += 7;
                    state.p[state.cpl].ch[i] += 2;
                    if (state.p[state.cpl].hp[i] < (state.p[state.cpl].h[i] * 2) / 3) {
                        state.p[state.cpl].hp[i] = (state.p[state.cpl].h[i] * 2) / 3;
                    }
                    break;
                case 4:
                    state.p[state.cpl].ws[i] += 1;
                    state.p[state.cpl].h[i] += 5;
                    state.p[state.cpl].wi[i] += 8;
                    state.p[state.cpl].ch[i] += 3;
                    if (state.p[state.cpl].hp[i] < (state.p[state.cpl].h[i] * 2) / 3) {
                        state.p[state.cpl].hp[i] = (state.p[state.cpl].h[i] * 2) / 3;
                    }
                    break;
                case 5:
                    state.p[state.cpl].ws[i] += 2;
                    state.p[state.cpl].h[i] += 7;
                    state.p[state.cpl].wi[i] += 2;
                    state.p[state.cpl].ch[i] += 6;
                    if (state.p[state.cpl].hp[i] < (state.p[state.cpl].h[i] * 2) / 3) {
                        state.p[state.cpl].hp[i] = (state.p[state.cpl].h[i] * 2) / 3;
                    }
                    break;
                case 6:
                    state.p[state.cpl].ws[i] += 2;
                    state.p[state.cpl].h[i] += 8;
                    state.p[state.cpl].wi[i] += 3;
                    state.p[state.cpl].ch[i] += 3;
                    if (state.p[state.cpl].hp[i] < (state.p[state.cpl].h[i] * 2) / 3) {
                        state.p[state.cpl].hp[i] = (state.p[state.cpl].h[i] * 2) / 3;
                    }
                    break;
            }
        }
    }
}
