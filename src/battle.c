#include "ryl.h"
#include "res.h"
#include "battle.h"
#include "driv.h"
#include "gfx.h"
#include "inventory.h"
#include "party.h"
#include "primitives.h"
#include "replay_knobs.h"
#include "script.h"
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
    char fn[80], str[80], c, d;
    int32_t n1 = 0, n2 = 0; /* Min & Max nr */
    int32_t h1 = 0, h2 = 0; /* Min & Max hp */
    int32_t sp;             /* Fire frost hold */
    int32_t im;             /* Immune to magic? */
    int32_t ws = 0;         /* WS */
    int32_t x, i, mh[15], deadc[15], who, dp, g, noc, mhtemp;
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
            n2 = m->n2 + pa_first_level() / m->n2_lv_div;
        }
    }

    noc = ryl_random(1, n2);
    if (noc < n1) {
        noc = n1;
    }
    /* Replay-mode override (rk_force_encounter_noc): pin the foe
       count after the natural roll above so the post-encounter RNG
       state matches the unforced path. */
    noc = rk_force_encounter_noc(noc);
    int32_t foe_max = res_combat_int("encounter_foe_count_max");
    int32_t party_avg =
        (pm_lv(pm_cur(1)) + pm_lv(pm_cur(2)) + pm_lv(pm_cur(3)) + pm_lv(pm_cur(4))) / 4;
    res_binding hp_bindings[] = {
        {"h1_base", h1},
        {"h2_base", h2},
        {"party_avg", party_avg},
        {"first_level", pa_first_level()},
    };
    size_t hp_n = sizeof hp_bindings / sizeof hp_bindings[0];
    h1 = res_formula_int_n("encounter_h1_scaled", hp_bindings, hp_n);
    h2 = res_formula_int_n("encounter_h2_scaled", hp_bindings, hp_n);
    if (h1 > h2) {
        h1 = h2;
    }

    if (noc > foe_max) {
        noc = foe_max;
    }
    assert(noc >= 1 && noc <= foe_max);
    assert(h1 <= h2);

    ws += pa_first_level();

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
        if (pa_is_alive(g) && !playerheld) {
        combat:
            tx("%s\r\n", pm_name(pm_cur(g)));
            tx("What now?\r\n\r\n");
            tx("(A)ttack\r\n");
            tx("(D)efend\r\n");
            tx("(U)se Item\r\n");
            tx("(B)attle Cry\r\n");
            if (pm_is_mage(pm_cur(g))) {
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
                if (inv_letter_in_range(d)) {
                    oc = pa_inventory(inv_slot_from_letter(d));
                    if (!item_is_magic_item(oc)) {
                        clw();
                        tx("Not possible.\r\n");
                        ryl_delay(1000);
                        clw();
                        goto combat;
                    } else {
                        if (im) {
                            pa_set_inventory(inv_slot_from_letter(d), 0);
                            clw();
                            tx0(res_text("battle.item.magic_immune"));
                            ryl_delay(1000);
                            clw();
                        } else {
                            /* §23.B closed: every magic item has an
                               `effect:` DSL body in magic_items.json5;
                               the dispatcher routes every Use-Item
                               invocation through play_spell_body.
                               Inventory consume is body-owned
                               (pa_set_inventory engine_call) so
                               conditional consumers like
                               gem_of_resurrection can defer to their
                               success branch. */
                            const char *slug = NULL;
                            const void *effect =
                                res_magic_item_effect_node(oc, &slug);
                            if (!effect) {
                                char err[96];
                                snprintf(err, sizeof err,
                                         "magic_items: no effect body for id %d",
                                         (int)oc);
                                ryl_die(err);
                            }
                            char caster_buf[8];
                            snprintf(caster_buf, sizeof caster_buf, "%d", g);
                            char inv_slot_buf[8];
                            snprintf(inv_slot_buf, sizeof inv_slot_buf,
                                     "%d", inv_slot_from_letter(d));
                            ryl_bind binds[] = {
                                {"caster_idx", caster_buf},
                                {"monster_name", fn},
                                {"inv_slot", inv_slot_buf},
                                {0},
                            };
                            int ws_int = ws;
                            int action_retry = 0;
                            struct script_combat_ctx ctx = {
                                .noc = noc,
                                .mh = mh,
                                .deadc = deadc,
                                .fn = fn,
                                .confused = &confused,
                                .held = &held,
                                .antimagic = &antimagic,
                                .ws = &ws_int,
                                .action_retry = &action_retry,
                                .h2 = h2,
                            };
                            char id_buf[80];
                            snprintf(id_buf, sizeof id_buf,
                                     "magic_items.%s",
                                     slug ? slug : "(unknown)");
                            play_spell_body(id_buf, effect, binds, &ctx);
                            ws = (int32_t)ws_int;
                            if (action_retry) {
                                /* gem_of_resurrection's not-dead branch
                                   — re-prompt the player's action menu
                                   without consuming a turn (legacy
                                   `goto combat`). */
                                goto combat;
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
                        pm_add_ma(caster, -(s->base_cost + pm_lv(caster)));
                        /* §22 Lane A: every mage spell carries a do:
                           body — res_spell_node always resolves. */
                        const void *spell_node = res_spell_node("mage", d - 48);
                        char caster_buf[8];
                        snprintf(caster_buf, sizeof caster_buf, "%d", g);
                        ryl_bind binds[] = {
                            {"caster_idx", caster_buf},
                            {"monster_name", fn},
                            {0},
                        };
                        int ws_int = ws;
                        struct script_combat_ctx ctx = {
                            .noc = noc,
                            .mh = mh,
                            .deadc = deadc,
                            .fn = fn,
                            .confused = &confused,
                            .held = &held,
                            .antimagic = &antimagic,
                            .ws = &ws_int,
                            .h2 = h2,
                        };
                        play_spell_body(s->id ? s->id : "mage.(unknown)",
                                        spell_node, binds, &ctx);
                        ws = (int32_t)ws_int;
                    }
                }
            }
            /****************************************************************************/
            if (c == 'B') {
                clw();
                tx("HI-YAAAA!\r\n\r\n");
                ryl_delay(800);
                tx0(res_text("battle.cry.off_guard"));
                int32_t cry_pen = res_combat_int("battle_cry.ws_penalty");
                if (ws > cry_pen) {
                    ws -= cry_pen;
                }
                ryl_delay(1500);
            }
            /****************************************************************************/
            if (c == 'D') {
                pm_t pmg = pm_cur(g);
                if (pm_hp(pmg) + pm_lv(pmg) > pm_h(pmg)) {
                    pm_set_hp(pmg, pm_h(pmg));
                } else {
                    pm_add_hp(pmg, pm_lv(pmg));
                }
                clw();
                if (defend < res_combat_int("defend.rest_threshold")) {
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
                int32_t def_pen = res_combat_int("defend.ws_penalty");
                if (ws > def_pen) {
                    ws -= def_pen;
                }
            }
            /****************************************************************************/
            if (c == 'A') {
                while (1) {
                    clw();
                    tx_fmt("battle.prompt.who", NULL);
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
                who = d - 64;
                x = pm_ws(pm_cur(g));
                if (playerconfused) {
                    int32_t cdiv = res_combat_int("attack.confusion_div");
                    if (cdiv > 0) {
                        x /= cdiv;
                    }
                }
                if (ryl_chance(x + res_combat_int("attack.hit_bonus"))) {
                    /* HIT */
                    dp = obdamage(pm_wp(pm_cur(g)));
                    dp += ryl_random(0, res_combat_int("attack.damage_spread_max"));
                    int32_t hpd = res_combat_int("hp_defense_div");
                    if (hpd > 0) {
                        dp += pm_hp(pm_cur(g)) / hpd;
                    }
                    clw();
                    tx("%s hits for\r\n", pm_name(pm_cur(g)));
                    tx("%d damage pts!\r\n", dp);
                    ryl_delay(600);
                    mh[who] -= dp;
                    if (mh[who] <= 0) {
                        mh[who] = 0;
                        deadc[who] = 1;
                        tx0(res_text("battle.killed"));
                        ryl_delay(600);
                    }
                } else {
                    /* MISS */
                    clw();
                    tx("%s misses!\r\n", pm_name(pm_cur(g)));
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
                res_binding reward_bindings[] = {
                    {"noc", noc},
                    {"h2", h2},
                    {"ws", ws},
                    {"sp", sp},
                };
                size_t reward_n = sizeof reward_bindings / sizeof reward_bindings[0];
                int32_t exp_gain =
                    res_formula_int_n("reward_xp_per_survivor", reward_bindings, reward_n);
                int32_t gold_gain =
                    res_formula_int_n("reward_gold_party", reward_bindings, reward_n);
                tx("Each survivor gets\r\n");
                tx("%d experience pts\r\n", exp_gain);
                tx("and %d gold pieces.\r\n\r\n", gold_gain);
                ryl_delay(1000);
                pa_add_kills((uint32_t)noc);
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
                        pa_set_inventory(x, i);
                    }
                }
                party_add_gold(gold_gain);
                for_each_party_member(i) {
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
                if (pa_alive_count() == 0) {
                    losegame();
                }
                if (!deadc[x] && !held) {
                    if (!antimagic && sp &&
                        ryl_chance(15 + sp / 2)) /* MOSTERS PRACTICE NASTY MAGIC */
                    {
                        /* §23.A: monster nasty-magic dispatcher.
                           clw + "<monster> casts" stay in C (shared
                           prefix across all spells); the spell-pick
                           random(0, n-1) and the body run via the
                           script DSL. n comes from data so an author
                           can grow the spell list without touching
                           battle.c. RNG order matches the legacy
                           switch — pick draw fires AFTER the casts
                           line and BEFORE the body's first emit. */
                        clw();
                        ryl_bind cast_binds[] = {
                            {"monster_name", fn},
                            {0},
                        };
                        tx_fmt("battle.monster.casts", cast_binds);
                        int spell_count = res_monster_spell_count();
                        if (spell_count > 0) {
                            int pick = ryl_random(0, spell_count - 1);
                            const void *spell_body =
                                res_monster_spell_node(pick);
                            if (spell_body) {
                                char caster_buf[8];
                                snprintf(caster_buf, sizeof caster_buf,
                                         "%d", x);
                                ryl_bind binds[] = {
                                    {"caster_idx", caster_buf},
                                    {"monster_name", fn},
                                    {0},
                                };
                                int ws_int = ws;
                                struct script_combat_ctx ctx = {
                                    .noc = noc,
                                    .mh = mh,
                                    .deadc = deadc,
                                    .fn = fn,
                                    .confused = &confused,
                                    .held = &held,
                                    .antimagic = &antimagic,
                                    .ws = &ws_int,
                                    .player_held = &playerheld,
                                    .player_confused = &playerconfused,
                                    .h2 = h2,
                                };
                                char id_buf[64];
                                snprintf(id_buf, sizeof id_buf,
                                         "monster_spells.spells[%d]",
                                         pick);
                                play_spell_body(id_buf, spell_body, binds,
                                                &ctx);
                                ws = (int32_t)ws_int;
                            }
                        }
                    } else {
                        while (1) {
                            who = ryl_random(1, 4);
                            if (pa_is_alive(who)) {
                                break;
                            }
                        }
                        clw();
                        tx("%s attacks\r\n", fn);
                        tx("%s!\r\n", pm_name(pm_cur(who)));
                        ryl_delay(400);
                        if (ryl_chance(ws + 1)) {
                            dp = mh[x] / 6;
                            dp += ryl_random(0, 9);
                            dp -= obdamage(pm_ar(pm_cur(who)));
                            if (dp < 1) {
                                dp = 1;
                            }
                            tx("And hits for\r\n");
                            tx("%d damage pts!!!\r\n", dp);
                            pm_add_hp(pm_cur(who), -dp);
                            if (pm_hp(pm_cur(who)) <= 0) {
                                if (pa_pl()) {
                                    pa_add_pl(-1);
                                }
                                pm_set_hp(pm_cur(who), 0);
                                pm_set_dead(pm_cur(who), 1);
                                tx("[31m%s DIES!!![37m\r\n", pm_name(pm_cur(who)));
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
                if (pa_alive_count() == 0) {
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
                        if (ryl_chance(res_combat_int("run.desperate_chance")) || mandatory) {
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
    int32_t add[5];

    for_each_party_member(i) {
        add[i] = 0;
    }

    for_each_party_member(i) {
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
    for_each_party_member(i) {
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
