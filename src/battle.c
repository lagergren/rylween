#include "ryl.h"
#include "res.h"
#include "eval.h"
#include "battle.h"
#include "driv.h"
#include "gfx.h"
#include "inventory.h"
#include "primitives.h"
#include "subs.h"
#include "towne.h"

static void expcheck(void);

/*
** Never find and never fool
** The first one drinks the poison pool
** The third one reads the ancient tome
** The last one breaks the double dome
*/

void encounter(int32_t foe, int32_t mandatory) {
    assert(foe >= 1);
    assert(state.cpl >= 1 && state.cpl <= state.num_parties);
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
        if (m && m->n2_lv_div > 0) {
            n2 = m->n2 + state.p[state.cpl].lv[1] / m->n2_lv_div;
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
            if (ryl_chance(res_combat_int("flee_chance"))) {
                clw();
                tx0(res_text("battle.run.coward"));
                ryl_delay(800);
                clw();
                clr_map_window();
                return;
            } else {
                clw();
                tx0(res_text("battle.run.fail_away"));
                ryl_delay(800);
                clw();
            }
        } else {
            clw();
            tx0(res_text("battle.run.desperate_fail"));
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
                    if (!item_is_magic_item(oc)) {
                        clw();
                        tx("Not possible.\r\n");
                        ryl_delay(1000);
                        clw();
                        goto combat;
                    } else {
                        if (im) {
                            state.p[state.cpl].inventory[d - 64] = 0;
                            clw();
                            tx0(res_text("battle.item.magic_immune"));
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
                                                tx0(res_text("battle.killed"));
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
                                    tx0(res_text("battle.item.sparks_bump"));
                                    ryl_pause();
                                    clw();
                                    ws /= 2;
                                    break;

                                case 103:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s throws the\r\n", state.p[state.cpl].name[g]);
                                    tx0(res_text("battle.item.light_blind"));
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
                                        tx0(res_text("battle.killed"));
                                        ryl_delay(600);
                                        deadc[vem] = 1;
                                        mh[vem] = 0;
                                        clw();
                                    }
                                    break;

                                case 105: {
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx0(res_text("battle.prompt.on_whom"));
                                    while (1) {
                                        d = key();
                                        if (d >= '1' && d <= '4') {
                                            break;
                                        }
                                    }
                                    pm_t target = pm_cur(d - 48);
                                    pm_full_heal(target);
                                    clw();
                                    tx("%s is fully\r\n", pm_name(target));
                                    tx("healed.\r\n");
                                    ryl_delay(1000);
                                    clw();
                                    break;
                                }

                                case 106:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx0(res_text("battle.spell.morale_quest"));
                                    ryl_pause();
                                    clw();
                                    for (i = 1; i <= res_party_member_max(); i++) {
                                        pm_t pm = pm_cur(i);
                                        pm_add_ws(pm, 3);
                                        pm_add_h(pm, 3);
                                        pm_add_ch(pm, 3);
                                        pm_add_wi(pm, 3);
                                    }
                                    break;

                                case 107:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx0(res_text("battle.spell.party_healed"));
                                    ryl_pause();
                                    clw();
                                    for (i = 1; i <= res_party_member_max(); i++) {
                                        pm_full_heal(pm_cur(i));
                                    }
                                    break;

                                case 108: {
                                    clw();
                                    tx0(res_text("battle.prompt.on_whom"));
                                    while (1) {
                                        b = key();
                                        if (b >= '1' && b <= '4') {
                                            break;
                                        }
                                    }
                                    pm_t target = pm_cur(b - 48);
                                    if (pm_dead(target)) {
                                        clw();
                                        tx("%s opens %s eyes!\r\n", pm_name(target),
                                           pronoun(b - 48, 3));
                                        ryl_delay(1000);
                                        party_current()->pl++;
                                        pm_set_dead(target, 0);
                                        pm_set_hp(target, 1);
                                        state.p[state.cpl].inventory[d - 64] = 0;
                                    } else {
                                        clw();
                                        tx("%s is not dead.\r\n", pm_name(target));
                                        ryl_delay(1000);
                                        goto combat;
                                    }
                                    break;
                                }

                                case 109:
                                    state.p[state.cpl].inventory[d - 64] = 0;
                                    clw();
                                    tx("%s throws Kael's\r\n", state.p[state.cpl].name[g]);
                                    tx("axe at the enemy!\r\n\r\n");
                                    ryl_delay(1500);
                                    tx0(res_text("battle.item.ghost_axe.wounds"));
                                    ryl_delay(4000);
                                    clw();
                                    tx0(res_text("battle.item.battle_cry"));
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
                    if (!s) {
                        continue;
                    }
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
                    tx0(res_text("battle.spell.fizzle"));
                    ryl_delay(2000);
                }
                if (d == '7') {
                    clw();
                    goto combat;
                }
                if (im) {
                    s = NULL; /* swallow the key press, no effect */
                } else {
                    s = res_spell("mage", d - 48);
                }
                if (s) {
                    if (pm_ma(caster) < (s->base_cost + pm_lv(caster))) {
                        clw();
                        tx0(res_text("battle.spell.not_enough_magic"));
                        ryl_delay(1000);
                    } else {
                        state.p[state.cpl].ma[g] -= (s->base_cost + pm_lv(caster));
                        const void *spell_node = res_spell_node("mage", d - 48);
                        if (spell_node) {
                            env_t env;
                            env_init(&env);
                            env_bind_caster_party(&env, caster);
                            env_bind_combat(&env, noc, mh, deadc, fn);
                            eval_effect((const node *)spell_node, &env);
                            env_free(&env);
                            /* DSL path handled the spell; skip the
                               legacy enum switch entirely. */
                            spell_node = NULL; /* fall through past switch */
                            goto after_mage_switch;
                        }
                        switch (s->effect) {
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
                                            tx0(res_text("battle.killed"));
                                            ryl_delay(600);
                                            deadc[i] = 1;
                                        }
                                    }
                                }
                                break;
                            case SE_CONFUSION:
                                clw();
                                tx("%s utters a word.\r\n", pm_name(caster));
                                tx0(res_text("battle.spell.mirror"));
                                ryl_delay(2000);
                                ws /= 2;
                                confused = 1;
                                break;
                            case SE_DEATH:
                                while (1) {
                                    clw();
                                    tx0(res_text("battle.prompt.who"));
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
                                    tx0(res_text("battle.spell.annihilate.resist"));
                                    ryl_delay(2000);
                                    mh[vem] = 1;
                                } else {
                                    clw();
                                    tx0(res_text("battle.spell.annihilate.kill"));
                                    ryl_delay(2000);
                                    deadc[vem] = 1;
                                    mh[vem] = 0;
                                }
                                break;
                            case SE_HOLD:
                                clw();
                                tx("%s utters a word.\r\n", pm_name(caster));
                                tx0(res_text("battle.spell.hold_enemy"));
                                ryl_delay(2000);
                                held = 1;
                                break;
                            case SE_ANTIMAGIC:
                                clw();
                                tx("%s utters a word.\r\n", pm_name(caster));
                                tx0(res_text("battle.spell.silence"));
                                ryl_delay(2800);
                                antimagic = 1;
                                break;
                            default:
                                break;
                        }
                    after_mage_switch:;
                    }
                }
            }
            /****************************************************************************/
            if (c == 'B') {
                clw();
                tx("HI-YAAAA!\r\n\r\n");
                ryl_delay(800);
                tx0(res_text("battle.cry.off_guard"));
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
                    tx0(res_text("battle.rest.gain_hits"));
                    ryl_delay(1000);
                } else {
                    tx0(res_text("battle.defend.stance"));
                    ryl_delay(1000);
                }
                defend++;
                int32_t cap = res_combat_int("defend_cap");
                if (cap > 0 && defend > cap) {
                    defend = (uint8_t)cap;
                }
                if (ws > 2) {
                    ws -= 2;
                }
            }
            /****************************************************************************/
            if (c == 'A') {
                while (1) {
                    clw();
                    tx0(res_text("battle.prompt.who"));
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
                    int32_t hpd = res_combat_int("hp_defense_div");
                    if (hpd > 0) {
                        dp += state.p[state.cpl].hp[g] / hpd;
                    }
                    clw();
                    tx("%s hits for\r\n", state.p[state.cpl].name[g]);
                    tx("%d damage pts!\r\n", dp);
                    ryl_delay(600);
                    mh[vem] -= dp;
                    if (mh[vem] <= 0) {
                        mh[vem] = 0;
                        deadc[vem] = 1;
                        tx0(res_text("battle.killed"));
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
                tx0(res_text("battle.victory.bested"));
                tx("Each survivor gets\r\n");
                tx("%d experience pts\r\n", ((noc * (h2 / 4) * (ws / 4)) + sp * 3));
                tx("and %d gold pieces.\r\n\r\n", ((noc * (h2 / 7) * (ws / 4)) / 4));
                ryl_delay(1000);
                party_current()->kills += noc;
                x = inv_find_empty();
                i = 0;
                if (x) {
                    if (ryl_chance(res_combat_int("drop_tables.default.drop_chance"))) {
                        if (ryl_chance(res_combat_int("drop_tables.default.treasure_chance"))) {
                            i = ryl_random(res_combat_int("drop_tables.default.treasure_lo"),
                                           res_combat_int("drop_tables.default.treasure_hi"));
                        } else {
                            i = ryl_random(res_combat_int("drop_tables.default.magic_lo"),
                                           res_combat_int("drop_tables.default.magic_hi"));
                        }
                        clw();
                        tx("You find a\r\n");
                        tx("%s.\r\n", object(i));
                        ryl_delay(1500);
                        state.p[state.cpl].inventory[x] = i;
                    }
                }
                int32_t gold_gain = (noc * (h2 / 7) * (ws / 4));
                int32_t exp_gain = ((noc * (h2 / 4) * (ws / 4)) + sp * 3);
                party_add_gold(gold_gain);
                for (i = 1; i <= res_party_member_max(); i++) {
                    pm_t pm = pm_cur(i);
                    if (!pm_dead(pm)) {
                        pm_add_exp(pm, (uint32_t)exp_gain);
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
                                for (i = 1; i <= res_party_member_max(); i++) {
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
                                tx0(res_text("battle.monster.hold_player"));
                                ryl_delay(1500);
                                playerheld = 1;
                                break;
                            case 3: /* Monster casts confuse */
                                tx("Confusion!\r\n");
                                ryl_delay(600);
                                tx0(res_text("battle.monster.confusion"));
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
                            tx0(res_text("battle.run.fail_away"));
                            ryl_delay(1000);
                            break;
                        } else {
                            clw();
                            tx0(res_text("battle.run.escaped"));
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
    int32_t i, add[5];
    int mm = res_party_member_max();

    for (i = 1; i <= mm; i++) {
        add[i] = 0;
    }

    for (i = 1; i <= mm; i++) {
        pm_t pm = pm_cur(i);
        int32_t l = pm_lv(pm);
        uint32_t e = pm.party->exp[pm.m];
        for (int n = l + 1; n <= 28; n++) {
            int32_t need = res_xp_threshold(n);
            if (need <= 0 || e < (uint32_t)need) {
                break;
            }
            add[i]++;
        }
    }

    /* PC_* ids are 1..6; the lowercased names here must match the
       classes.<cls> shard keys in world.json5. Missing data returns
       0 deltas (no-op). */
    static const char *const pc_class_key[] = {
        "",
        "fighter",
        "thief",
        "mage",
        "priest",
        "bard",
        "ranger",
    };
    for (i = 1; i <= mm; i++) {
        if (add[i]) {
            pm_t pm = pm_cur(i);
            clw();
            tx("%s gains >%d<\r\n", pm_name(pm), add[i]);
            tx("experience levels.\r\n");
            ryl_delay(600);
            pm_add_lv(pm, add[i]);
            int cl_id = pm_cl(pm);
            const char *cls = (cl_id >= 1 && cl_id <= 6) ? pc_class_key[cl_id] : "";
            pm_add_ws(pm, res_class_levelup(cls, "ws"));
            pm_add_h(pm, res_class_levelup(cls, "h"));
            pm_add_wi(pm, res_class_levelup(cls, "wi"));
            pm_add_ch(pm, res_class_levelup(cls, "ch"));
            pm_hp_floor(pm, (pm_h(pm) * 2) / 3);
        }
    }
}
