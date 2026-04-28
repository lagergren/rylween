#include "ryl.h"
#include "battle.h"
#include "driv.h"
#include "gfx.h"
#include "inventory.h"
#include "log.h"
#include "party.h"
#include "primitives.h"
#include "replay_knobs.h"
#include "res.h"
#include "subs.h"
#include "towne.h"
#include "ydarg.h"

static void confrontation(void);

void ydarg() {
    int32_t room[8][8];
    int32_t xl, yl, lvl, n, s, e, w, i, j, x, oc;
    int32_t map_size = res_int_path("ydarg.map.size");
    char c, d;

    clr_map_window();
    blitgfx(ydargpic);
    clw();
    if (pa_special(PS_SILVERKEY) == 2) {
        tx0(res_text("ydarg.ruin"));
        ryl_pause();
        clr_map_window();
        update_map();
        clw();
        return;
    }
    tx0(res_text("ydarg.approach"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.jaara_form"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.jaara_speech"));
    ryl_pause();
    clw();
    if (!pa_special(PS_SILVERKEY)) {
        tx0(res_text("ydarg.no_key"));
        ryl_pause();
        clr_map_window();
        update_map();
        return;
    }
    tx0(res_text("ydarg.key_accepted"));
    ryl_pause();
    clw();
    tx0(res_text("ydarg.enter"));
    ryl_pause();
    clw();

    /*
		Bracelets of Thun
		Fire Shield
		Jazzy Foot
		Crown

		Monster, F{lla, Hitta Vapen, Hitta f|rem}l, Trappa upp
		4 levels. level 1 - Bracelets, Level 2 = fire shield, level 3 = foot
		level 4 = crown.

    XX XX XX XX XX

    XX XX XX XX XX

    XX XX XX XX XX

    XX XX XX XX XX

    XX XX XX XX XX

	*/

    tx0(res_text("ydarg.door_slams"));
    ryl_delay(2000);
    clw();
    lvl = 1;
    xl = 1;
    yl = 1;
    int room_type_max = res_int_path("ydarg.room.type_max");
    int easy_rooms = rk_ydarg_easy_rooms();
    for (i = 1; i <= map_size; i++) {
        for (j = 1; j <= map_size; j++) {
            /* Easy-rooms knob fills with case 4 (no-op) so the
               keystroke stream of a confrontation fixture isn't at
               the mercy of the per-slot RNG draw. Production builds
               never see this branch. */
            room[i][j] = easy_rooms ? 4 : ryl_random(1, room_type_max);
        }
    }
    room[map_size][map_size] = 50;
    ryl_pause();

    while (1) {
        clw();

        switch (room[xl][yl]) {
            case 1: /* Monster */
                room[xl][yl] = 0;
                tx0(res_text("ydarg.room.monster"));
                ryl_delay(1500);
                encounter(res_monster_tier_pick("deep"), 0);
                clr_map_window();
                blitgfx(ydargpic);
                clw();
                break;

            case 2: /* Pitfall */
                room[xl][yl] = 0;
                tx("%s springs a\r\n", pm_name(pm_cur(ryl_random(0, 3))));
                tx("trap!!!\r\n");
                ryl_delay(1500);
                clw();
                j = 0;
                for_each_party_member(i) {
                    if (pm_is_thief(pm_cur(i))) {
                        tx("%s tries to\r\n", pm_name(pm_cur(i)));
                        tx("disarm it...\r\n\r\n");
                        ryl_delay(1500);
                        if (ryl_chance(pm_wisdom(pm_cur(i)))) {
                            tx0(res_text("ydarg.room.trap.disarm_succeed"));
                            ryl_delay(1000);
                            j = 1;
                        } else {
                            tx0(res_text("ydarg.room.trap.disarm_fail"));
                            ryl_delay(1000);
                            j = 0;
                        }
                        break;
                    }
                }
                if (!j) {
                    int n_traps = res_str_arr_count("ydarg.pitfall.trap_types");
                    if (n_traps > 0) {
                        const char *trap_id = res_str_arr_at(
                            "ydarg.pitfall.trap_types",
                            ryl_random(0, n_traps - 1));
                        if (trap_id) {
                            tx0(res_text(trap_id));
                        }
                    }
                    ryl_pause();
                    int32_t dmg_div = res_int_path("ydarg.pitfall.damage_div");
                    for_each_party_member(i) {
                        if (pa_is_alive(i)) {
                            pm_t pm = pm_cur(i);
                            if (dmg_div > 0) {
                                pm_add_hit_points(pm, -pm_hit_points(pm) / dmg_div);
                            }
                        }
                    }
                }
                break;

            case 3:
                room[xl][yl] = 0;
                x = ryl_random(0, res_int_path("ydarg.drop_tables.room.gate_max"));
                if (x) {
                    char rng_key[64];
                    int32_t rng_lo, rng_hi;
                    x = ryl_random(0, res_int_path("ydarg.drop_tables.room.kind_max"));
                    snprintf(rng_key, sizeof rng_key, "ydarg.drop_tables.room.kind_%d.lo", x);
                    rng_lo = res_int_path(rng_key);
                    snprintf(rng_key, sizeof rng_key, "ydarg.drop_tables.room.kind_%d.hi", x);
                    rng_hi = res_int_path(rng_key);
                    oc = ryl_random(rng_lo, rng_hi);
                } else {
                    oc = ryl_random(res_int_path("ydarg.drop_tables.room.magic.lo"),
                                    res_int_path("ydarg.drop_tables.room.magic.hi"));
                }
                tx_fmt("ydarg.room.treasure.find",
                       (ryl_bind[]){
                           {"object", object(oc)},
                           {0},
                       });
                tx0(res_text("ydarg.room.treasure.menu"));
                c = key();
                c = toupper(c);
                if (c == 'T') {
                    x = inv_add(oc) != 0;
                    if (!x) {
                        clw();
                        tx0(res_text("ydarg.room.treasure.full"));
                        ryl_delay(800);
                    } else {
                        tx0(res_text("ydarg.room.treasure.taken"));
                        ryl_delay(800);
                    }
                }
                break;

            case 50:
                clw();
                tx0(res_text("ydarg.room.stairs.intro"));
                ryl_delay(1500);
                clw();
                tx0(res_text("ydarg.room.stairs.mage_offer"));
                ryl_pause();
                while (1) {
                    clw();
                    tx0(res_text("ydarg.room.stairs.menu"));
                    while (1) {
                        d = key();
                        d = toupper(d);
                        if (d == 'T' || d == 'G' || d == 'L') {
                            break;
                        }
                    }
                    if (d == 'L') {
                        clw();
                        tx0(res_text("ydarg.room.stairs.leave"));
                        ryl_delay(1000);
                        yl--;
                        break;
                    }
                    if (d == 'T') {
                        clw();
                        tx0(res_text("ydarg.room.stairs.teleport_vortex"));
                        ryl_delay(3000);
                        tx0(res_text("ydarg.room.stairs.teleport_outside"));
                        ryl_delay(3000);
                        clr_map_window();
                        update_map();
                        clw();
                        return;
                    }
                    clw();
                    tx0(res_text("ydarg.room.stairs.give_menu"));
                    while (1) {
                        d = key();
                        if (d >= '1' && d <= '5') {
                            break;
                        }
                    }
                    j = 0;
                    const char *sp_tag = NULL;
                    const char *reject = NULL;
                    if (res_ydarg_stairs_item(d - 48, &sp_tag, &reject)) {
                        int ps = inv_special_id_for_tag(sp_tag);
                        if (ps == PS_NONE || !pa_special(ps)) {
                            clw();
                            char tid[64];
                            snprintf(tid, sizeof tid,
                                     "ydarg.room.stairs.%s", reject);
                            tx0(res_text(tid));
                            ryl_delay(1500);
                        } else {
                            pa_set_special(ps, 0);
                            j = 1;
                        }
                    }
                    if (j) {
                        clw();
                        tx0(res_text("ydarg.room.stairs.mage_grateful"));
                        ryl_pause();
                        clw();
                        xl = 1;
                        yl = 1;
                        lvl++;
                        if (lvl == 5) {
                            confrontation();
                        }
                        break;
                    }
                }
                break;
        }

        clw();
        statusline();
        tx("You are at %dx%d.\r\n\r\n", xl, yl, lvl);
        tx("You are on level %d.\r\n", lvl);
        {
            char floor_id[32];
            snprintf(floor_id, sizeof floor_id, "ydarg.nav.floor_%d", lvl);
            tx0(res_text(floor_id));
        }
        n = 0;
        s = 0;
        e = 0;
        w = 0;
        if (yl > 1) {
            n = 1;
        }
        if (xl > 1) {
            w = 1;
        }
        if (xl < map_size) {
            e = 1;
        }
        if (yl < map_size) {
            s = 1;
        }
        if (n) {
            tx0(res_text("ydarg.nav.north"));
        } else {
            tx0(res_text("ydarg.nav.no_north"));
        }

        if (e && w) {
            tx0(res_text("ydarg.nav.east_west"));
        }
        if (e && !w) {
            tx0(res_text("ydarg.nav.east"));
        }
        if (!e && w) {
            tx0(res_text("ydarg.nav.west"));
        }
        if (s) {
            tx0(res_text("ydarg.nav.south"));
        }

        c = key();
        c = toupper(c);
        if (c == '8' && n) {
            clw();
            tx0(res_text("ydarg.nav.go_north"));
            ryl_delay(300);
            clw();
            yl--;
        }
        if (c == '2' && s) {
            clw();
            tx0(res_text("ydarg.nav.go_south"));
            ryl_delay(300);
            clw();
            yl++;
        }
        if (c == '6' && e) {
            clw();
            tx0(res_text("ydarg.nav.go_east"));
            ryl_delay(300);
            clw();
            xl++;
        }
        if (c == '4' && w) {
            clw();
            tx0(res_text("ydarg.nav.go_west"));
            ryl_delay(300);
            clw();
            xl--;
        }
        if (c == '?') {
            help();
        }
        if (c == 'T') {
            armouroff();
        }
        if (c == 'P') {
            weaponoff();
        }
        if (c == 'L') {
            drop();
        }
        if (c == 'W') {
            weaponr();
        }
        if (c == 'R') {
            armourr();
        }
        if (c == 'Q') {
            quaff();
        }
        if (c == 'Z') {
            spell(0); /* 0 means over ground */
        }
        if (c == 'V') {
            showparty();
            clw();
            tx_fmt("ydarg.nav.view_who", NULL);
            while (1) {
                d = key();
                if (pm_digit_in_range(d) || toupper(d) == 'Q' || toupper(d) == 'A') {
                    break;
                }
            }
            if (pm_digit_in_range(d)) {
                int slot = d - '1';
                LOG_D("input", "ydarg show: digit=%c -> slot=%d", d, slot);
                show(slot);
            }
            if (toupper(d) == 'A') {
                for_each_party_member(i) {
                    show(i);
                }
            }
            clw();
        }
    }
}

static void confrontation() {
    FILE *fh;
    char str[80];
    char path[512];
    const char *mstr;

    ryl_bind binds[] = {
        {"p1_name", pm_name(pm_cur(0))},
        {0},
    };
    tx_narrate("ydarg.narration.conf_act1", binds);
    clw();
    {
        int n_seq = res_str_arr_count("ydarg.confrontation_sequence");
        for (int k = 0; k < n_seq; k++) {
            encounter(res_monster_id(res_str_arr_at("ydarg.confrontation_sequence", k)), 1);
        }
    }
    tx_narrate("ydarg.narration.conf_act2", binds);
    encounter(res_monster_id("baron_ydarg"), 1);
    tx_narrate("ydarg.narration.conf_act3", binds);
    pa_set_special(PS_SILVERKEY, 2);
    save();
    tx_narrate("ydarg.narration.conf_act4", binds);
    clw();

    res_data_path("heroes.txt", path, sizeof path);
    if (!(fh = fopen(path, "r"))) {
        fh = fopen(path, "w");
        snprintf(str, sizeof str, "%c\n", 12);
        fputs(str, fh);
        fputs(" +============================================================================+\n",
              fh);
        fputs(" I              ADVENTURERS OF RYLWEEN - HALL OF NATIONAL HEROES              I\n",
              fh);
        fputs(" +============================================================================+\n\n",
              fh);
        fclose(fh);
    }

    if (!(fh = fopen(path, "a+"))) {
        return;
    }

    struct wall_clock c;
    clock_now(&c);
    mstr = res_enum("month", c.month);

    snprintf(str, sizeof str, "  %s defeated baron Ydarg %s %d, %d\n", pa_party_name(), mstr, c.day,
             c.year);
    fputs(str, fh);
    fclose(fh);
    cleanexit();
}
