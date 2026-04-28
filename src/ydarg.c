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
#include "script.h"
#include "subs.h"
#include "towne.h"
#include "wld.h"
#include "ydarg.h"

/* §23.J ydarg DSL lift — function-local state hoisted to module
   scope so the per-case scripts can read/write it via engine_call
   backers. The `while(1)` driver inside ydarg() still mutates
   `g_ydarg.xl/yl/lvl/room[][]` directly today; the lifts below
   funnel each access through these helpers so scripts that touch
   the same fields land on the identical bytes. The whole struct is
   re-seeded on every ydarg() entry — no save-game persistence. */
struct ydarg_state {
    int32_t xl;
    int32_t yl;
    int32_t lvl;
    int32_t map_size;
    int32_t room[8][8];
    /* §23.J: set by ydarg.case_stairs's (T)eleport path before the
       script returns; the C while(1) driver tests this flag after
       each play_script and break/returns out of ydarg() so the
       teleport behaves like the legacy `return` did. Cleared on
       every ydarg() entry by ydarg_state_init. */
    int32_t exit_requested;
};

static struct ydarg_state g_ydarg;

/* §23.J: seed the per-floor grid. Fills slots 1..map_size with
   ryl_random(1, room_type_max) — except when the easy_rooms test
   knob is on, in which case every non-stairs slot is 4 (no-op room)
   to remove the per-slot RNG draw from a confrontation fixture's
   keystroke stream. The map[size][size] = 50 stair slot is fixed
   verbatim. xl/yl/lvl reset to (1, 1, 1); map_size is cached so
   the navigation block doesn't re-read the world tree. */
static void ydarg_state_init(void) {
    g_ydarg.map_size = res_int_path("ydarg.map.size");
    g_ydarg.lvl = 1;
    g_ydarg.xl = 1;
    g_ydarg.yl = 1;
    g_ydarg.exit_requested = 0;
    int room_type_max = res_int_path("ydarg.room.type_max");
    int easy_rooms = rk_ydarg_easy_rooms();
    for (int i = 1; i <= g_ydarg.map_size; i++) {
        for (int j = 1; j <= g_ydarg.map_size; j++) {
            g_ydarg.room[i][j] = easy_rooms ? 4 : ryl_random(1, room_type_max);
        }
    }
    g_ydarg.room[g_ydarg.map_size][g_ydarg.map_size] = 50;
}

/* §23.J state accessors — narrow helpers exposed to the DSL via
   the engine_int / engine_call dispatch tables. Callers in scripts
   read xl/yl/lvl/room_at_cur/map_size as engine_int args; mutators
   live as engine_calls (set_xl, set_yl, set_lvl, clear_room_at_cur,
   reset_pos). The room grid is only ever consumed at the cur (xl,
   yl) cell; exposing a generic [x][y] reader/writer would invite
   the script to walk the grid which the legacy code never did. */
int32_t ydarg_get_xl(void) {
    return g_ydarg.xl;
}
int32_t ydarg_get_yl(void) {
    return g_ydarg.yl;
}
int32_t ydarg_get_lvl(void) {
    return g_ydarg.lvl;
}
int32_t ydarg_get_map_size(void) {
    return g_ydarg.map_size;
}
int32_t ydarg_get_room_at_cur(void) {
    return g_ydarg.room[g_ydarg.xl][g_ydarg.yl];
}
void ydarg_set_xl(int32_t v) {
    g_ydarg.xl = v;
}
void ydarg_set_yl(int32_t v) {
    g_ydarg.yl = v;
}
void ydarg_set_lvl(int32_t v) {
    g_ydarg.lvl = v;
}
void ydarg_clear_room_at_cur(void) {
    g_ydarg.room[g_ydarg.xl][g_ydarg.yl] = 0;
}

void ydarg() {
    char c;

    /* silverkey == 0 (no_key) and silverkey == 2 (ruin) are now
       handled by the MG_SPECIAL_EQ triggers in missions.c —
       ydarg() only runs for silverkey == 1 (legitimate entry).
       The approach / Jaara / key_accepted / enter / door_slams
       narration is the ydarg.intro DSL play_script. */
    play_script("ydarg.intro", NULL);

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

    ydarg_state_init();
    ryl_pause();

    while (1) {
        clw();

        switch (g_ydarg.room[g_ydarg.xl][g_ydarg.yl]) {
            case 1: /* Monster */
                g_ydarg.room[g_ydarg.xl][g_ydarg.yl] = 0;
                play_script("ydarg.room_monster", NULL);
                break;

            case 2: /* Pitfall */
                play_script("ydarg.case_pitfall", NULL);
                break;

            case 3:
                play_script("ydarg.case_treasure", NULL);
                break;

            case 50:
                play_script("ydarg.case_stairs", NULL);
                if (g_ydarg.exit_requested) {
                    return;
                }
                break;
        }

        play_script("ydarg.nav_render", NULL);

        c = key();
        c = toupper(c);
        dir_t move = wld_dir_from_key(c);
        if (move != DIR_NONE) {
            int can_go = 0;
            const char *go_text = NULL;
            switch (move) {
                case DIR_NORTH:
                    can_go = (g_ydarg.yl > 1);
                    go_text = "ydarg.nav.go_north";
                    break;
                case DIR_SOUTH:
                    can_go = (g_ydarg.yl < g_ydarg.map_size);
                    go_text = "ydarg.nav.go_south";
                    break;
                case DIR_EAST:
                    can_go = (g_ydarg.xl < g_ydarg.map_size);
                    go_text = "ydarg.nav.go_east";
                    break;
                case DIR_WEST:
                    can_go = (g_ydarg.xl > 1);
                    go_text = "ydarg.nav.go_west";
                    break;
                default:
                    break;
            }
            if (can_go) {
                clw();
                tx0(res_text(go_text));
                ryl_delay(300);
                clw();
                g_ydarg.xl += wld_dir_dx(move);
                g_ydarg.yl += wld_dir_dy(move);
            }
        }
        char c_buf[8];
        snprintf(c_buf, sizeof c_buf, "%d", (int)(unsigned char)c);
        play_script("ydarg.cmd_dispatch",
                    (ryl_bind[]){
                        {"c", c_buf},
                        {0},
                    });
    }
}

/* §23.J cmd-dispatch backer: (V)iew-party command. Lifts the
   showparty + view_who prompt + key-wait loop + per-slot show
   into one C entry so the DSL switch in ydarg.cmd_dispatch
   doesn't have to express the ASCII gate (digit-in-range OR
   Q OR A) and the conditional show / for-each-show. The legacy
   semantics are preserved verbatim: show only when the digit
   was a valid party-member slot, show-all on 'A', no-op on 'Q'. */
void ydarg_view_command(void) {
    char d;
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

/* §23.J case-50 backer: handle the (G)ive-item attempt. Looks up
   `digit` (1..5; '5' is the explicit "Nothing" / out-of-range pass-
   through that also happens to fail the data lookup) in
   ydarg.stairs.items[], resolves the matched special tag to a PS_*,
   gates on pa_special, and either clears the special (returns 1 ⇒
   mage opens the trapdoor) or clw + emits the reject message + delays
   1500ms (returns 0 ⇒ mage menu loops). The reject_msg id is split
   per item (no_have_sing for singular, no_have_plur for the plural-
   pronoun BRACELETS). Mirrors the case-50 inner block verbatim. */
int32_t ydarg_stairs_try_give(int32_t digit) {
    const char *sp_tag = NULL;
    const char *reject = NULL;
    if (!res_ydarg_stairs_item((int)digit, &sp_tag, &reject)) {
        return 0;
    }
    int ps = inv_special_id_for_tag(sp_tag);
    if (ps == PS_NONE || !pa_special(ps)) {
        clw();
        char tid[64];
        snprintf(tid, sizeof tid, "ydarg.room.stairs.%s", reject);
        tx0(res_text(tid));
        ryl_delay(1500);
        return 0;
    }
    pa_set_special(ps, 0);
    return 1;
}

/* §23.J case-50 backer: (T)eleport-out exit. Emits the vortex /
   outside narration, redraws the wilderness map under the legacy
   blank-blit, and sets exit_requested so the outer C while(1) falls
   through to ydarg()'s final return. Bundled because the teleport
   has no caller-visible state apart from "ydarg() must exit now". */
void ydarg_teleport_exit(void) {
    clw();
    tx0(res_text("ydarg.room.stairs.teleport_vortex"));
    ryl_delay(3000);
    tx0(res_text("ydarg.room.stairs.teleport_outside"));
    ryl_delay(3000);
    clr_map_window();
    update_map();
    clw();
    g_ydarg.exit_requested = 1;
}

int32_t ydarg_get_exit_requested(void) {
    return g_ydarg.exit_requested;
}

/* §23.J case-3 backer: roll the treasure-room item id. The drop
   table is a two-step pick (gate_max -> 0 picks magic, else gate
   the kind 0..kind_max -> kind.lo..kind.hi range), all bounded by
   data/world.json5 ydarg.drop_tables.room.*. The RNG identity is
   load-bearing — the gate_max draw advances the stream regardless
   of the branch taken — so the ordering inside this helper must
   stay verbatim. Returns the rolled item id; the DSL case_treasure
   script feeds it into engine_bind 'object' for the find narration
   and inv_add for the take/leave dispatcher. */
int32_t ydarg_treasure_roll(void) {
    int32_t x = ryl_random(0, res_int_path("ydarg.drop_tables.room.gate_max"));
    if (x) {
        char rng_key[64];
        int32_t rng_lo, rng_hi;
        x = ryl_random(0, res_int_path("ydarg.drop_tables.room.kind_max"));
        snprintf(rng_key, sizeof rng_key, "ydarg.drop_tables.room.kind_%d.lo", x);
        rng_lo = res_int_path(rng_key);
        snprintf(rng_key, sizeof rng_key, "ydarg.drop_tables.room.kind_%d.hi", x);
        rng_hi = res_int_path(rng_key);
        return ryl_random(rng_lo, rng_hi);
    }
    return ryl_random(res_int_path("ydarg.drop_tables.room.magic.lo"),
                      res_int_path("ydarg.drop_tables.room.magic.hi"));
}

/* §23.J case-2 backer: undisarmed-pitfall damage application. The
   case body picks a random trap_type slug from
   ydarg.pitfall.trap_types, emits its narration, ryl_pauses, then
   walks the alive party slots and shaves pm_hit_points / damage_div
   off every alive member. The whole transaction stays in C because
   the trap-type pick + narration emit + per-member damage divider
   read are all data-driven internals — wrapping each in a separate
   DSL op would buy nothing and the slug-array index → text-id step
   has no DSL op. The case-2 dispatcher (find-thief, disarm-result
   branch, gate this damage path) lives in ydarg.case_pitfall. */
void ydarg_pitfall_undisarmed_apply(void) {
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
    int max = res_party_member_max();
    for (int i = 0; i < max; i++) {
        if (pa_is_alive(i)) {
            pm_t pm = pm_cur(i);
            if (dmg_div > 0) {
                pm_add_hit_points(pm, -pm_hit_points(pm) / dmg_div);
            }
        }
    }
}

/* DSL engine_call backer: walk ydarg.confrontation_sequence (a string-
   array of monster slugs) and fire encounter(monster, mandatory=1) for
   each. The legacy C confrontation() body had this as an inline
   res_str_arr_at + encounter loop between act-1 and act-2 narration;
   the script invokes it via { engine_call: 'ydarg_run_seq' }. */
void ydarg_run_seq(void) {
    int n_seq = res_str_arr_count("ydarg.confrontation_sequence");
    for (int k = 0; k < n_seq; k++) {
        encounter(res_monster_id(res_str_arr_at("ydarg.confrontation_sequence", k)), 1);
    }
}

/* DSL engine_call backer: silverkey -> 2 + save. Runs between act-3
   and act-4 narration so the title-screen "you have defeated Baron
   Ydarg" gate persists even if the engine is killed before
   ydarg_finalize. Bundled because the two operations always pair. */
void ydarg_record_completion(void) {
    pa_set_special(PS_SILVERKEY, 2);
    save();
}

/* DSL engine_call backer: end-of-confrontation epilogue. Appends a
   Hall-of-Heroes line to heroes.txt under data/ and cleanexits the
   engine. Does not return. */
void ydarg_finalize(void) {
    FILE *fh;
    char str[80];
    char path[512];

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
        cleanexit();
    }

    struct wall_clock c;
    clock_now(&c);
    const char *mstr = res_enum("month", c.month);

    snprintf(str, sizeof str, "  %s defeated baron Ydarg %s %d, %d\n", pa_party_name(), mstr, c.day,
             c.year);
    fputs(str, fh);
    fclose(fh);
    cleanexit();
}
