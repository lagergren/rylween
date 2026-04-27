#include "io.h"
#include "interact.h"
#include "inventory.h"
#include "json5.h"
#include "log.h"
#include "party.h"
#include "replay_knobs.h"
#include "res.h"
#include "ryl.h"
#include "driv.h"
#include "primitives.h"
#include "subs.h"
#include "towne.h"
#include "wld.h"

static void createparty(void);
static void position_locations(void);
static int read_current_world(void);
static void the_game(void);
static void write_current_world(void);

static void the_game() {
    char cc, d;
    char path[64];

    clw();
    state.hour_of_day = 5;
    pa_set_hours_rested(14);

    tx0(res_text("title.header"));

    tx0(res_text("title.prog_by"));
    tx0(res_text("title.prog_name"));

    tx0(res_text("title.created_by"));
    tx0(res_text("title.creator_name"));

    tx0(res_text("title.graphics_by"));
    tx0(res_text("title.graphics_name"));

    /* Prep map for positions of huts/cottages */

    for_each_npc(i) {
        npc_t n = npc_at_slot(i);
        if (npc_in_wilderness(n)) {
            state.map[npc_y(n)][npc_x(n)] = '1';
        }
    }

    update_map();

    while (1) {
        if (io_winch_pending) {
            io_winch_pending = 0;
            cl();
            res_ansi_path("screen", path, sizeof path);
            disptext(path);
            update_map();
            clw();
        }

        assert(state.cpl >= 1 && state.cpl <= state.num_parties);
        timehandler();
        foodhandler();
        statusline();

        if (pa_time_left() == 0) {
            clw();
            tx0(res_text("driv.moves.out_of_moves"));
            tx0(res_text("driv.menu.out_of_moves"));
            while (1) {
                cc = key();
                cc = toupper(cc);
                if (cc == 'S' || cc == 'Q') {
                    break;
                }
            }
            if (cc == 'S') {
                clw();
                tx0(res_text("driv.save.saving"));
                save();
                ryl_delay(400);
                tx0(res_text("driv.save.done"));
                ryl_delay(400);
            }
            clw();
            tx0(res_text("driv.quit.farewell"));
            ryl_delay(1000);
            scorelist();
            cleanexit();
        }

        cc = key();
        cc = toupper(cc);

        switch (cc) {
            case ' ':
                clw();
                tx0(res_text("driv.move.pass"));
                break;
            case '8':
                if (pa_time_left() == 0) {
                    clw();
                    tx0(res_text("driv.move.no_moves"));
                    break;
                }
                if (wld_is_passable(pa_x(), pa_y() - 1)) {
                    wld_set_last_terrain(wld_tile_at(pa_x(), pa_y()));
                    pa_add_y(-1);
                    update_map();
                    random_wilderness();
                    pa_add_time_left(-1);
                }
                break;
            case '4':
                if (pa_time_left() == 0) {
                    clw();
                    tx0(res_text("driv.move.no_moves"));
                    break;
                }
                if (wld_is_passable(pa_x() - 1, pa_y())) {
                    wld_set_last_terrain(wld_tile_at(pa_x(), pa_y()));
                    pa_add_x(-1);
                    update_map();
                    random_wilderness();
                    pa_add_time_left(-1);
                }
                break;
            case '2':
                if (pa_time_left() == 0) {
                    clw();
                    tx0(res_text("driv.move.no_moves"));
                    break;
                }
                if (wld_is_passable(pa_x(), pa_y() + 1)) {
                    wld_set_last_terrain(wld_tile_at(pa_x(), pa_y()));
                    pa_add_y(1);
                    update_map();
                    random_wilderness();
                    pa_add_time_left(-1);
                }
                break;
            case '6':
                if (pa_time_left() == 0) {
                    clw();
                    tx0(res_text("driv.move.no_moves"));
                    break;
                }
                if (wld_is_passable(pa_x() + 1, pa_y())) {
                    wld_set_last_terrain(wld_tile_at(pa_x(), pa_y()));
                    pa_add_x(1);
                    update_map();
                    random_wilderness();
                    pa_add_time_left(-1);
                }
                break;

            case 'S':
                clw();
                tx0(res_text("driv.save.saving"));
                save();
                ryl_delay(400);
                tx0(res_text("driv.save.done"));
                break;
            case 'W':
                weaponr();
                break;
            case 'R':
                armourr();
                break;
            case '!':
                cl();
                res_ansi_path("screen", path, sizeof path);
                disptext(path);
                update_map();
                clw();
                break;
            case 'J':
                cl();
                c(0, 0);
                read_journal();
                cl();
                c(49, 6);
                clw();
                res_ansi_path("screen", path, sizeof path);
                disptext(path);
                update_map();
                clw();
                break;
            case '?':
                help();
                break;
            case 'H':
                hunt();
                break;
            case 'V':
                showparty();
                clw();
                tx_fmt("driv.view.who", NULL);
                while (1) {
                    d = key();
                    if (pm_digit_in_range(d) || toupper(d) == 'Q' || toupper(d) == 'A') {
                        break;
                    }
                }
                if (pm_digit_in_range(d)) {
                    int slot = d - '1';
                    LOG_D("input", "driv show: digit=%c -> slot=%d", d, slot);
                    show(slot);
                }
                if (toupper(d) == 'A') {
                    for_each_party_member(i) {
                        show(i);
                    }
                }
                clw();
                break;
            case 'T':
                armouroff();
                break;
            case 'P':
                weaponoff();
                break;
            case 'E':
                enter();
                break;
            case 'L':
                drop();
                break;
            case 'C':
                camp();
                break;
            case 'Z':
                spell(0);
                break;
            case 'Q':
                quaff();
                break;
            case 'X':
                clw();
                tx0(res_text("driv.quit.confirm"));
                tx0(res_text("common.yes"));
                tx0(res_text("common.no"));
                d = key();
                d = toupper(d);
                if (d != 'Y') {
                    clw();
                    break;
                }
                scorelist();
                cleanexit();
        }
    }
}

uint8_t drop_option() {
    char c;

    clw();
    tx0(res_text("driv.drop.another"));
    c = key();
    c = toupper(c);
    if (c == 'Y') {
        clw();
        return drop();
    } else {
        clw();
        return 0;
    }
}

void statusline() {
    /* Redo status line */

    char str[256];
    char stline[256];

    if (pa_food() < 0) {
        pa_set_food(0);
    }
    stline[0] = 0;
    if (is_hour_in_range(6, 11)) {
        snprintf(stline, sizeof stline, "TIME: [33mDAWN[32m      ");
    }
    if (is_hour_in_range(12, 17)) {
        snprintf(stline, sizeof stline, "TIME: [33mMIDDAY[32m    ");
    }
    if (is_hour_in_range(18, 23)) {
        snprintf(stline, sizeof stline, "TIME: [33mTWILIGHT[32m  ");
    }
    if (is_hour_in_range(24, 5)) {
        snprintf(stline, sizeof stline, "TIME: [33mMIDNIGHT[32m  ");
    }
    if (pa_food() < pa_pl() * 3 && pa_food()) {
        str_append(stline, sizeof stline, "ST: [35mHUNGRY  ");
    }
    if (!pa_food()) {
        str_append(stline, sizeof stline, "ST: [31mSTARVING[32m ");
    }
    if (pa_food() && pa_food() >= pa_pl() * 3) {
        str_append(stline, sizeof stline, "ST: [33mOK    ");
    }
    snprintf(str, sizeof str, "[32m X: [33m%2d   [32mY: [33m%2d[32m     ", pa_x(), pa_y());
    str_append(stline, sizeof stline, str);
    snprintf(str, sizeof str, "FD: [33m%4d[32m  ", pa_food());
    str_append(stline, sizeof stline, str);
    snprintf(str, sizeof str, "MV: [33m%3d[32m  ", pa_time_left());
    str_append(stline, sizeof stline, str);
    snprintf(str, sizeof str, "AU: [33m%6d[32m", pa_gold());
    str_append(stline, sizeof stline, str);
    c(3, 21);
    tx("[32m%s    ", stline);
    snprintf(stline, sizeof stline, "H1: [33m%3d/%3d   [32mH2: [33m%3d/%3d   [32mH3: [33m%3d/%3d   [32mH4: [33m%3d/%3d[37m",
             pm_hp(pm_cur(0)), pm_h(pm_cur(0)),
             pm_hp(pm_cur(1)), pm_h(pm_cur(1)),
             pm_hp(pm_cur(2)), pm_h(pm_cur(2)),
             pm_hp(pm_cur(3)), pm_h(pm_cur(3)));
    c(13, 22);
    tx("%s\r\n", stline);
    c(49, 6);
}
static void position_locations() {
    for (int i = 0; i < WLD_LOCATION_SLOT_MAX; i++) {
        wld_location_set(i, 0, 0);
    }

    int32_t x_lo = res_int_path("map.location_bounds.x_min");
    int32_t x_hi = res_int_path("map.location_bounds.x_max");
    int32_t y_lo = res_int_path("map.location_bounds.y_min");
    int32_t y_hi = res_int_path("map.location_bounds.y_max");
    const char *avoid = res_str_path("map.dungeon_avoid");
    if (!avoid) {
        avoid = "";
    }
    for (int i = 1; i <= WLD_RANDOM_LOCATION_MAX; i++) {
        while (1) {
            int32_t rx = ryl_random(x_lo, x_hi);
            int32_t ry = ryl_random(y_lo, y_hi);
            wld_location_set(i, rx, ry);
            if (!strchr(avoid, wld_tile_at(rx, ry))) {
                break;
            }
        }
    }
}

void init() {
    char str[90], kl[8], path[512], d, ch;
    FILE *fh;
    uint8_t abort = 0, x = 0, i, j = 0;

    tx("[30m%c%c%c%c%c[37m", 27, 91, 48, 32, 112);

    res_map_path(path, sizeof path);
    if (!(fh = fopen(path, "rb"))) {
        c(49, 6);
        clw();
        tx0(res_text("driv.init.no_map"));
        ryl_pause();
        cleanexit();
    }
    int32_t map_rows = res_int_path("map.rows");
    for (i = 1; i <= map_rows; i++) {
        fgets((char *)state.map[i], sizeof(state.map[i]), fh);
    }
    fclose(fh);

    /* State load: current-world.json5 is the sole persistence path
       (§15 step 5 dropped the realm.dat/npc.dat readers). Missing file
       = sysop cold-start: build a fresh realm in memory (empty parties,
       rolled locations, realized NPCs) and write the first
       current-world.json5 so the next boot finds it. */
    if (!read_current_world()) {
        state.num_parties = 0;
        state.last_played_day = 0;
        position_locations();
        res_realize_npcs();
        write_current_world();
    }

    cl();
    res_ansi_path("title", path, sizeof path);
    disptext(path);
    tx0(res_text("driv.more"));
    key();

    getclk(kl);
    if (kl[3] != state.last_played_day) {
        cl();
        tx0(res_text("driv.init.first_day"));

        // Clear entry file

        ryl_state_path("entry.dat", path, sizeof path);
        fh = fopen(path, "wb");
        fclose(fh);

        for (i = 1; i < state.num_parties + 1; i++) {
            tx(".");
            state.p[i].time_left = 175;
            ryl_delay(40);
        }
        getclk(kl);
        state.day_at_game_start = kl[3];
        save();
    }
    getclk(kl);
    state.day_at_game_start = kl[3];

    cl();
    res_ansi_path("screen", path, sizeof path);
    disptext(path);

    while (!abort) {
        clw();

        tx_fmt("driv.menu.owner",
               (ryl_bind[]){
                   {"name", state.global_name},
                   {0},
               });

        for (i = 1, x = 0; i < state.num_parties + 1; i++) {
            if (!stricmp(state.global_name, state.p[i].player)) {
                x = i;
            }
        }
        for (i = 1; i < state.num_parties + 1; i++) {
            if (state.p[i].silverkey == 2) {
                tx_fmt("driv.struggle.over",
                       (ryl_bind[]){
                           {"party_name", state.p[i].party_name},
                           {0},
                       });
                tx0(res_text("driv.struggle.conclusion"));
                ryl_pause();
                clw();
                cleanexit();
            }
        }
        if (!x) {
            tx0(res_text("driv.menu.new_party"));
        }
        if (x) {
            tx0(res_text("driv.menu.continue"));
        }
        if (x) {
            tx0(res_text("driv.menu.terminate"));
        }
        tx0(res_text("driv.menu.common_s"));
        tx0(res_text("driv.menu.common_h"));
        tx0(res_text("driv.menu.common_d"));
        tx0(res_text("driv.menu.common_x"));

        ch = '\0';

        while (toupper(ch) != 'N' && toupper(ch) != 'C' && toupper(ch) != 'X' &&
               toupper(ch) != 'T' && toupper(ch) != 'S' && toupper(ch) != 'H' &&
               toupper(ch) != 'D') {
            ch = key();
            ch = toupper(ch);
        }

        switch (toupper(ch)) {
            case 'S':
                cl();
                res_data_path("score.txt", path, sizeof path);
                disptext(path);
                tx0(res_text("driv.more"));
                key();
                cl();
                c(49, 6);
                clw();
                res_ansi_path("screen", path, sizeof path);
                disptext(path);
                clw();
                tx0(res_text("driv.scores.verbose_q"));
                d = key();
                d = toupper(d);
                if (d == 'Y') {
                    cl();
                    res_data_path("vscore.txt", path, sizeof path);
                    disptext(path);
                    tx0(res_text("driv.more"));
                    key();
                    cl();
                    c(49, 6);
                    clw();
                    res_ansi_path("screen", path, sizeof path);
                    disptext(path);
                }
                break;

            case 'D':
                clw();
                tx0(res_text("driv.docs.capture"));
                ryl_pause();
                cl();
                res_data_path("rylween.doc", path, sizeof path);
                disptext(path);
                tx0(res_text("driv.more"));
                key();
                cl();
                c(49, 6);
                clw();
                res_ansi_path("screen", path, sizeof path);
                disptext(path);
                break;

            case 'H':
                cl();
                res_data_path("heroes.txt", path, sizeof path);
                disptext(path);
                tx0(res_text("driv.more"));
                key();
                cl();
                c(49, 6);
                clw();
                res_ansi_path("screen", path, sizeof path);
                disptext(path);
                break;

            case 'N':
                if (!x) {
                    clw();
                    tx0(res_text("driv.create_new.manual_reminder"));
                    ryl_pause();
                    clw();
                    createparty();
                } else {
                    cleanexit();
                }
                break;
            case 'C':
                if (x) {
                    // Check ENTRY.DAT for 2-4 present names. Add one more
                    // if less.

                    ryl_state_path("entry.dat", path, sizeof path);
                    fh = fopen(path, "rb");
                    i = 0;
                    while (1) {
                        str[0] = 0;
                        fread((char *)str, sizeof(str), 1, fh);
                        if (str[0] == 0) {
                            break;
                        }
                        if (!stricmp(str, state.global_name)) {
                            i++;
                        }
                    }
                    fclose(fh);
                    if (i >= 4) {
                        char turns[8];
                        j = 4;
                        snprintf(turns, sizeof turns, "%d", j);
                        clw();
                        tx_fmt("driv.entry.exhausted",
                               (ryl_bind[]){
                                   {"turns", turns},
                                   {0},
                               });
                        ryl_pause();
                        cleanexit();
                    }

                    // Append name to file

                    ryl_state_path("entry.dat", path, sizeof path);
                    fh = fopen(path, "ab");
                    strcpy(str, state.global_name);
                    fwrite((char *)str, sizeof(str), 1, fh);
                    fclose(fh);

                    // Checking done. Refresh state from disk in case another
                    // concurrent BBS player saved while this one sat at the
                    // menu.

                    read_current_world();
                    abort = 1;
                    LOG_A("realm",
                          "%s and %s party '%s' entered the realm.",
                          pa_name(0), pronoun(0, 3), pa_party_name());
                    wld_set_last_terrain(0);
                } else {
                    clw();
                    tx0(res_text("driv.menu.no_game"));
                    ryl_delay(1500);
                    cleanexit();
                }
                break;
            case 'X':
                cleanexit();
                break;
            case 'T':
                if (x) {
                    clw();
                    tx0(res_text("driv.terminate.ugh"));
                    tx0(res_text("driv.terminate.confirm"));
                    d = key();
                    d = toupper(d);
                    if (d == 'Y') {
                        /* Shift state.p[] down so party x drops out, then
                           let write_current_world() emit the new realm.
                           The visited[] inner-loop preserves the legacy
                           .dat path's quirk verbatim — iterator j cycles
                           but the target column never advances past x,
                           so only visited[x] is actually overwritten.
                           No fixture covers this branch; the process
                           cleanexit()s before it matters anyway. */
                        clw();
                        tx0(res_text("driv.terminate.editing_realm"));
                        /* Free both the journal and the per-member heap
                           buffers on the slot that's about to be shifted
                           over. The memmove below would otherwise leak
                           them and duplicate the pointer in two slots. */
                        free_journal(&state.p[x]);
                        party_release_members(&state.p[x]);
                        state.num_parties--;
                        for_each_npc(i) {
                            npc_t n = npc_at_slot(i);
                            for (j = 1; j < state.num_parties; j++) {
                                npc_visited_set(n, x, npc_visited(n, x + 1));
                            }
                        }
                        if ((int)x <= state.num_parties) {
                            memmove(&state.p[x], &state.p[x + 1],
                                    (size_t)(state.num_parties + 1 - x) * sizeof state.p[0]);
                            /* memmove duplicated the old tail's heap
                               pointers (journal + every per-member buf);
                               NULL them on the tail so save doesn't
                               double-emit and a future realize into the
                               slot starts clean. */
                            party_clear_heap_pointers(&state.p[state.num_parties + 1]);
                        }
                        ryl_delay(1000);

                        clw();
                        tx0(res_text("driv.terminate.editing_journal"));
                        ryl_delay(1000);

                        write_current_world();

                        clw();
                        tx0(res_text("driv.terminate.deleted"));
                        ryl_pause();
                        cleanexit();
                    }
                }
                break;
        }
    }
    abort = 0;
    the_game();
}

/* Enum -> string via res_enum(enum_id, v). Writes the quoted symbolic
   value if res_enum returns a non-empty string, else falls back to the
   raw integer so the file stays round-trippable even if the world enum
   is missing an entry. Step 2's reader uses res_enum_id for the inverse. */
static void wcw_enum(FILE *f, const char *enum_id, int v) {
    const char *s = res_enum(enum_id, v);
    if (s && s[0]) {
        fputc('"', f);
        fputs(s, f);
        fputc('"', f);
    } else {
        fprintf(f, "%d", v);
    }
}

/* JSON5 string literal: double-quoted, escaping \", \\, and control
   bytes (\n/\t/\r plus \uXXXX for other <0x20). Safe for arbitrary
   party/member names containing user input. */
static void wcw_str(FILE *f, const char *s) {
    fputc('"', f);
    for (; *s; s++) {
        unsigned char c = (unsigned char)*s;
        switch (c) {
            case '"':
                fputs("\\\"", f);
                break;
            case '\\':
                fputs("\\\\", f);
                break;
            case '\n':
                fputs("\\n", f);
                break;
            case '\r':
                fputs("\\r", f);
                break;
            case '\t':
                fputs("\\t", f);
                break;
            default:
                if (c < 0x20) {
                    fprintf(f, "\\u%04x", c);
                } else {
                    fputc(c, f);
                }
        }
    }
    fputc('"', f);
}

static void wcw_i32_arr(FILE *f, const int32_t *a, int n) {
    fputc('[', f);
    for (int i = 0; i < n; i++) {
        if (i > 0) {
            fputs(", ", f);
        }
        fprintf(f, "%d", a[i]);
    }
    fputc(']', f);
}

static void wcw_u32_arr(FILE *f, const uint32_t *a, int n) {
    fputc('[', f);
    for (int i = 0; i < n; i++) {
        if (i > 0) {
            fputs(", ", f);
        }
        fprintf(f, "%u", a[i]);
    }
    fputc(']', f);
}

static void wcw_byte_arr(FILE *f, const unsigned char *a, int n) {
    fputc('[', f);
    for (int i = 0; i < n; i++) {
        if (i > 0) {
            fputs(", ", f);
        }
        fprintf(f, "%u", (unsigned)a[i]);
    }
    fputc(']', f);
}

/* Emit party quest flags as a sparse map keyed by flag tag and with
   the state name as the value: `{ silver_key: "note_found", ... }`.
   Slots whose value is 0 (the 'none' state) are omitted — an absent
   key means the quest hasn't been touched. An orphan slot (non-zero
   byte at a slot the dictionary doesn't name) aborts rather than
   silently dropping data. */
static void wcw_party_flags(FILE *f, const struct party *p) {
    fputs("{", f);
    int first = 1;
    for (size_t slot = 0; slot < sizeof p->flag; slot++) {
        unsigned v = p->flag[slot];
        if (!v) {
            continue;
        }
        const char *id = res_flag_id_for_slot((int32_t)slot);
        if (!id) {
            LOG_E("driv",
                  "save: party flag slot %zu = %u but no tag claims "
                  "that slot in data/world.json5 flags:",
                  slot, v);
            exit(1);
        }
        const char *vname = res_flag_value(id, (int)v);
        if (!vname || !*vname) {
            LOG_E("driv",
                  "save: party flag '%s' value %u out of range in "
                  "its values[] array",
                  id, v);
            exit(1);
        }
        fputs(first ? " " : ", ", f);
        first = 0;
        wcw_str(f, id);
        fputs(": ", f);
        wcw_str(f, vname);
    }
    fputs(first ? "}" : " }", f);
}

/* Enum array via res_enum: each int slot emitted as the symbolic value,
   with null for slot-0 (1-based enum sentinel). Fall back to the raw
   int if the lookup returns ""; keeps the file round-trippable even if
   world.json5 is missing an entry. */
static void wcw_enum_arr(FILE *f, const char *enum_id, const int *vals, int n) {
    fputc('[', f);
    for (int i = 0; i < n; i++) {
        if (i > 0) {
            fputs(", ", f);
        }
        if (vals[i] == 0) {
            fputs("null", f);
            continue;
        }
        const char *s = res_enum(enum_id, vals[i]);
        if (s && s[0]) {
            fputc('"', f);
            fputs(s, f);
            fputc('"', f);
        } else {
            fprintf(f, "%d", vals[i]);
        }
    }
    fputc(']', f);
}

/* Byte-valued enum array (for gender[], which is unsigned char[5]). */
static void wcw_enum_barr(FILE *f, const char *enum_id, const unsigned char *vals, int n) {
    fputc('[', f);
    for (int i = 0; i < n; i++) {
        if (i > 0) {
            fputs(", ", f);
        }
        int v = vals[i];
        if (v == 0) {
            fputs("null", f);
            continue;
        }
        const char *s = res_enum(enum_id, v);
        if (s && s[0]) {
            fputc('"', f);
            fputs(s, f);
            fputc('"', f);
        } else {
            fprintf(f, "%d", v);
        }
    }
    fputc(']', f);
}

/* §15 step 1: serialize persistent state (realm header + NPCs + parties)
   to $HOME/.rylween/current-world.json5. Mirror-written alongside every
   existing realm.dat/npc.dat write; no reader yet, so output is for
   inspection only in this commit. Silent on fopen failure — .dat is
   still authoritative until step 2.

   Schema matches §15.6 except locations stay as raw dx/dy arrays for
   now (semantic split into towns/castle/catacombs is §15 step 3's job).
   Per §15.5, visited bits move out of the NPC subtree into each party's
   visited_npcs[] so party deletion no longer needs the index-shift
   dance. The `empty1..5` struct-party padding fields are omitted.

   Step 1.5: enum-valued fields (job, towne, building, cl, ra, gender) emit as
   symbolic strings; NPC records are keyed by symbolic id per §15.3.
   The nr field is dropped from NPC records (redundant with the slot
   key). Step 2's reader is the inverse. */
static void write_current_world() {
    char path[512];
    FILE *f;

    ryl_state_path("current-world.json5", path, sizeof path);
    if (!(f = fopen(path, "wb"))) {
        return;
    }

    fputs("{\n", f);

    fputs("  realm: {\n", f);
    fprintf(f, "    last_played_day: %d,\n", state.last_played_day);
    /* Recorded so the reader can reject saves whose member-array
       widths disagree with the current limits.party_member_max
       (§19.3.2). Strict-match policy — we're still porting and
       no production saves exist, so reshaping a stale save
       isn't worth the complexity. */
    fprintf(f, "    party_member_max: %d,\n", res_party_member_max());
    fputs("  },\n\n", f);

    fputs("  locations: {\n", f);
    fputs("    dx: ", f);
    wcw_i32_arr(f, state.dx, WLD_LOCATION_SLOT_MAX);
    fputs(",\n", f);
    fputs("    dy: ", f);
    wcw_i32_arr(f, state.dy, WLD_LOCATION_SLOT_MAX);
    fputs(",\n", f);
    fputs("  },\n\n", f);

    fputs("  npcs: {\n", f);
    for_each_npc(i) {
        const struct person *n = &state.npc[i];
        const char *sym = res_npc_sym(i);
        const char *display = res_npc_name(i);
        if (!sym) {
            continue;
        }
        /* Quote the key — symbols with apostrophes (legacy, while transition
           proceeds) or future hyphens aren't bare-identifier safe. */
        fputs("    ", f);
        wcw_str(f, sym);
        fprintf(f, ": { id: %d, name: ", i);
        wcw_str(f, display);
        fputs(", job: ", f);
        wcw_enum(f, "job", n->job);
        fputs(", towne: ", f);
        /* Save format keeps the symbolic "wilderness" string from
           enums.towne[0]; the runtime sentinel WILDERNESS_TOWN
           bridges back to that enum index here. */
        wcw_enum(f, "towne", n->towne == WILDERNESS_TOWN ? 0 : n->towne);
        fputs(", building: ", f);
        wcw_enum(f, "building", n->building);
        fprintf(f, ", x: %d, y: %d, gender: ", n->x, n->y);
        wcw_enum(f, "gender", n->gender);
        fputs(" },\n", f);
    }
    fputs("  },\n\n", f);

    fputs("  parties: [\n", f);
    /* All per-member arrays on struct party share the same length:
       res_party_member_max() (slots 0..N-1 used). Cached once so the
       writer emits a consistent width everywhere and the loader has
       one value to compare against. */
    int mlen = res_party_member_max();
    for (int i = 1; i <= state.num_parties; i++) {
        const struct party *p = &state.p[i];
        /* wcw_enum_arr wants plain int[], so materialize cl/ra into
           a stack buffer at member-array length. VLAs aren't in c99
           strict, so cap via alloca-style malloc. Small enough to
           keep on the stack via variable-length array on most
           targets; use heap for portability. */
        int *cl_int = malloc((size_t)mlen * sizeof *cl_int);
        int *ra_int = malloc((size_t)mlen * sizeof *ra_int);
        if (!cl_int || !ra_int) {
            LOG_E("driv", "write_current_world: malloc failed");
            exit(1);
        }
        for (int m = 0; m < mlen; m++) {
            cl_int[m] = p->cl[m];
            ra_int[m] = p->ra[m];
        }
        fputs("    {\n", f);
        fputs("      player: ", f);
        wcw_str(f, p->player);
        fputs(",\n", f);
        fputs("      party_name: ", f);
        wcw_str(f, p->party_name);
        fputs(",\n", f);
        fputs("      name: [", f);
        for (int m = 0; m < mlen; m++) {
            if (m > 0) {
                fputs(", ", f);
            }
            wcw_str(f, p->name[m]);
        }
        fputs("],\n", f);
        fputs("      flags: ", f);
        wcw_party_flags(f, p);
        fputs(",\n", f);
        fprintf(f, "      fireshield: %u,\n", p->fireshield);
        fprintf(f, "      bracelets: %u,\n", p->bracelets);
        fprintf(f, "      mastercrown: %u,\n", p->mastercrown);
        fprintf(f, "      jazzyfoot: %u,\n", p->jazzyfoot);
        fprintf(f, "      silverkey: %u,\n", p->silverkey);
        fprintf(f, "      pl: %u,\n", p->pl);
        fprintf(f, "      x: %d,\n", p->x);
        fprintf(f, "      y: %d,\n", p->y);
        fprintf(f, "      gold: %d,\n", p->gold);
        fprintf(f, "      torches: %u,\n", p->torches);
        fprintf(f, "      food: %d,\n", p->food);
        fprintf(f, "      time_left: %d,\n", p->time_left);
        fprintf(f, "      hours_rested: %d,\n", p->hours_rested);
        fputs("      inventory: ", f);
        wcw_i32_arr(f, p->inventory, 17);
        fputs(",\n", f);
        fputs("      ws: ", f);
        wcw_i32_arr(f, p->ws, mlen);
        fputs(",\n", f);
        fputs("      h: ", f);
        wcw_i32_arr(f, p->h, mlen);
        fputs(",\n", f);
        fputs("      hp: ", f);
        wcw_i32_arr(f, p->hp, mlen);
        fputs(",\n", f);
        fputs("      wi: ", f);
        wcw_i32_arr(f, p->wi, mlen);
        fputs(",\n", f);
        fputs("      ma: ", f);
        wcw_i32_arr(f, p->ma, mlen);
        fputs(",\n", f);
        fputs("      ch: ", f);
        wcw_i32_arr(f, p->ch, mlen);
        fputs(",\n", f);
        fputs("      cl: ", f);
        wcw_enum_arr(f, "pc_class", cl_int, mlen);
        fputs(",\n", f);
        fputs("      ra: ", f);
        wcw_enum_arr(f, "race", ra_int, mlen);
        fputs(",\n", f);
        fputs("      lv: ", f);
        wcw_i32_arr(f, p->lv, mlen);
        fputs(",\n", f);
        fputs("      ar: ", f);
        wcw_i32_arr(f, p->ar, mlen);
        fputs(",\n", f);
        fputs("      wp: ", f);
        wcw_i32_arr(f, p->wp, mlen);
        fputs(",\n", f);
        fputs("      exp: ", f);
        wcw_u32_arr(f, p->exp, mlen);
        fputs(",\n", f);
        fputs("      dead: ", f);
        wcw_i32_arr(f, p->dead, mlen);
        fputs(",\n", f);
        fputs("      gender: ", f);
        wcw_enum_barr(f, "gender", p->gender, mlen);
        fputs(",\n", f);
        fputs("      attack_today: ", f);
        wcw_byte_arr(f, p->attack_today, 26);
        fputs(",\n", f);
        fprintf(f, "      waiting_mail: %u,\n", p->waiting_mail);
        fprintf(f, "      attacked: %u,\n", p->attacked);
        fprintf(f, "      kills: %u,\n", p->kills);
        fputs("      visited_npcs: [", f);
        for (int j = 0; j <= res_npc_slot_max(); j++) {
            if (j > 0) {
                fputs(", ", f);
            }
            fprintf(f, "%u", (unsigned)state.npc[j].visited[i]);
        }
        fputs("],\n", f);
        fputs("      journal: [", f);
        for (size_t j = 0; j < p->journal_n; j++) {
            if (j > 0) {
                fputs(", ", f);
            }
            wcw_str(f, p->journal[j]);
        }
        fputs("],\n", f);
        fputs("    },\n", f);
        free(cl_int);
        free(ra_int);
    }
    fputs("  ],\n", f);

    fputs("}\n", f);
    fclose(f);
}

/* §15 step 2 reader helpers: tolerant DOM -> state field unpacking.
   Every getter leaves `out` untouched if the field is absent or the
   wrong kind, so missing optionals fall back to whatever state[i]
   already held (static-zero at boot; previous value on reload). */

static int32_t rcw_int(const node *obj, const char *key, int32_t dflt) {
    const node *n = obj_get(obj, key);
    if (n && n->kind == N_INT) {
        return n->u.iv;
    }
    return dflt;
}

/* Accept either the symbolic string (resolved via res_enum_id) or the
   raw integer — keeps the file round-trippable when an enum misses. */
static int32_t rcw_enum(const node *obj, const char *key, const char *enum_id, int32_t dflt) {
    const node *n = obj_get(obj, key);
    if (!n) {
        return dflt;
    }
    if (n->kind == N_INT) {
        return n->u.iv;
    }
    if (n->kind == N_STR) {
        int id = res_enum_id(enum_id, n->u.sv);
        return id >= 0 ? id : dflt;
    }
    return dflt;
}

static void rcw_i32_arr(const node *arr, int32_t *out, int out_n) {
    if (!arr || arr->kind != N_ARR) {
        return;
    }
    size_t n = arr->u.arr.n < (size_t)out_n ? arr->u.arr.n : (size_t)out_n;
    for (size_t i = 0; i < n; i++) {
        const node *it = arr->u.arr.items[i];
        if (it && it->kind == N_INT) {
            out[i] = it->u.iv;
        }
    }
}

static void rcw_u32_arr(const node *arr, uint32_t *out, int out_n) {
    if (!arr || arr->kind != N_ARR) {
        return;
    }
    size_t n = arr->u.arr.n < (size_t)out_n ? arr->u.arr.n : (size_t)out_n;
    for (size_t i = 0; i < n; i++) {
        const node *it = arr->u.arr.items[i];
        if (it && it->kind == N_INT) {
            out[i] = (uint32_t)it->u.iv;
        }
    }
}

/* Inverse of wcw_party_flags. Reads `{ tag: "state", ... }` into the
   party's flag[] byte array. Legacy `flag: [...]` arrays are still
   accepted for round-tripping older saves. */
static void rcw_party_flags(const node *pnode, unsigned char *flag, size_t flag_n) {
    memset(flag, 0, flag_n);
    const node *obj = obj_get(pnode, "flags");
    if (obj && obj->kind == N_OBJ) {
        for (size_t i = 0; i < obj->u.obj.n; i++) {
            const char *id = obj->u.obj.items[i].key;
            const node *val = obj->u.obj.items[i].val;
            int32_t slot = res_flag_slot(id);
            if (slot < 0 || (size_t)slot >= flag_n) {
                LOG_E("driv",
                      "load: unknown party flag tag '%s' — "
                      "add to data/world.json5 flags:",
                      id);
                exit(1);
            }
            int32_t v;
            if (val && val->kind == N_STR) {
                v = res_flag_value_id(id, val->u.sv);
                if (v < 0) {
                    LOG_E("driv",
                          "load: flag '%s' value '%s' not in its "
                          "values[] array",
                          id, val->u.sv);
                    exit(1);
                }
            } else if (val && val->kind == N_INT) {
                v = (int32_t)val->u.iv;
            } else {
                LOG_E("driv",
                      "load: flag '%s' value must be string or int", id);
                exit(1);
            }
            flag[slot] = (unsigned char)v;
        }
        return;
    }
    const node *legacy = obj_get(pnode, "flag");
    if (legacy && legacy->kind == N_ARR) {
        size_t nn = legacy->u.arr.n < flag_n ? legacy->u.arr.n : flag_n;
        for (size_t i = 0; i < nn; i++) {
            const node *it = legacy->u.arr.items[i];
            if (it && it->kind == N_INT) {
                flag[i] = (unsigned char)it->u.iv;
            }
        }
    }
}

static void rcw_byte_arr(const node *arr, unsigned char *out, int out_n) {
    if (!arr || arr->kind != N_ARR) {
        return;
    }
    size_t n = arr->u.arr.n < (size_t)out_n ? arr->u.arr.n : (size_t)out_n;
    for (size_t i = 0; i < n; i++) {
        const node *it = arr->u.arr.items[i];
        if (it && it->kind == N_INT) {
            out[i] = (unsigned char)it->u.iv;
        }
    }
}

/* Enum-int32 array: each element is a symbolic string (resolved via
   res_enum_id) or a raw int. N_NULL leaves the out slot untouched —
   writer emits null for 0-valued slots (1-based enum sentinel). */
static void rcw_enum_arr(const node *arr, const char *enum_id, int32_t *out, int out_n) {
    if (!arr || arr->kind != N_ARR) {
        return;
    }
    size_t n = arr->u.arr.n < (size_t)out_n ? arr->u.arr.n : (size_t)out_n;
    for (size_t i = 0; i < n; i++) {
        const node *it = arr->u.arr.items[i];
        if (!it) {
            continue;
        }
        if (it->kind == N_INT) {
            out[i] = it->u.iv;
        } else if (it->kind == N_STR) {
            int id = res_enum_id(enum_id, it->u.sv);
            if (id >= 0) {
                out[i] = id;
            }
        }
    }
}

static void rcw_enum_barr(const node *arr, const char *enum_id, unsigned char *out, int out_n) {
    if (!arr || arr->kind != N_ARR) {
        return;
    }
    size_t n = arr->u.arr.n < (size_t)out_n ? arr->u.arr.n : (size_t)out_n;
    for (size_t i = 0; i < n; i++) {
        const node *it = arr->u.arr.items[i];
        if (!it) {
            continue;
        }
        if (it->kind == N_INT) {
            out[i] = (unsigned char)it->u.iv;
        } else if (it->kind == N_STR) {
            int id = res_enum_id(enum_id, it->u.sv);
            if (id >= 0) {
                out[i] = (unsigned char)id;
            }
        }
    }
}

static void rcw_str(const node *obj, const char *key, char *out, int out_sz) {
    const node *n = obj_get(obj, key);
    if (!n || n->kind != N_STR) {
        return;
    }
    strncpy(out, n->u.sv, out_sz - 1);
    out[out_sz - 1] = '\0';
}

/* §15 step 2: populate state.* from $HOME/.rylween/current-world.json5.
   Returns 1 if the file was read successfully, 0 if it doesn't exist
   (caller falls back to realm.dat / npc.dat). Fatal on parse error via
   res_die — a corrupt save is a sysop problem, not a silent fallback.

   Expects the schema emitted by write_current_world (steps 1/1.5/1.6):
   realm{last_played_day}, locations{dx,dy}, npcs{<name>:{slot,...}},
   parties[{..., visited_npcs:[40]}]. Per §15.5, the visited bits live
   per-party; this loop rebuilds state.npc[j].visited[i]. */
static int read_current_world() {
    char path[512];
    FILE *probe;
    ryl_state_path("current-world.json5", path, sizeof path);
    if (!(probe = fopen(path, "rb"))) {
        return 0;
    }
    fclose(probe);

    node *root = json5_parse_file(path);
    root = json5_resolve_imports(root, path);

    const node *realm = obj_get(root, "realm");
    if (realm && realm->kind == N_OBJ) {
        state.last_played_day = rcw_int(realm, "last_played_day", state.last_played_day);
        /* Strict member-array width check. A save whose recorded
           party_member_max disagrees with the current world's
           limits.party_member_max has per-member arrays of the
           wrong length; rather than silently truncate or pad,
           refuse to load. No production saves exist, so the
           stricter policy is free (§19.3.2). The key is
           optional — pre-§19.3.2 saves lack it, and for those
           we fall back to the current knob assuming default. */
        int save_mmax = rcw_int(realm, "party_member_max", res_party_member_max());
        if (save_mmax != res_party_member_max()) {
            LOG_E("driv",
                  "load: current-world.json5 recorded "
                  "party_member_max=%d, current world limit=%d. "
                  "Saves can't be loaded across member-max "
                  "changes; either flip the world knob back or "
                  "discard the save.",
                  save_mmax, res_party_member_max());
            exit(1);
        }
    }

    const node *loc = obj_get(root, "locations");
    if (loc && loc->kind == N_OBJ) {
        rcw_i32_arr(obj_get(loc, "dx"), state.dx, WLD_LOCATION_SLOT_MAX);
        rcw_i32_arr(obj_get(loc, "dy"), state.dy, WLD_LOCATION_SLOT_MAX);
    }

    const node *npcs = obj_get(root, "npcs");
    if (npcs && npcs->kind == N_OBJ) {
        for (size_t i = 0; i < npcs->u.obj.n; i++) {
            const node *rec = npcs->u.obj.items[i].val;
            if (!rec || rec->kind != N_OBJ) {
                continue;
            }
            int slot = rcw_int(rec, "id", 0);
            if (slot < 1 || slot > 39) {
                continue;
            }
            struct person *n = &state.npc[slot];
            n->nr = slot;
            n->job = rcw_enum(rec, "job", "job", n->job);
            /* Save format encodes wilderness as the symbolic
               "wilderness" string (enum index 0); bridge back to
               WILDERNESS_TOWN for in-memory comparisons. */
            int twne = rcw_enum(rec, "towne", "towne", 0);
            n->towne = (twne == 0) ? WILDERNESS_TOWN : twne;
            n->building = rcw_enum(rec, "building", "building", n->building);
            n->x = rcw_int(rec, "x", n->x);
            n->y = rcw_int(rec, "y", n->y);
            n->gender = (unsigned char)rcw_enum(rec, "gender", "gender", n->gender);
        }
    }

    const node *parties = obj_get(root, "parties");
    if (parties && parties->kind == N_ARR) {
        state.num_parties = (int32_t)parties->u.arr.n;
        for (size_t pi = 0; pi < parties->u.arr.n; pi++) {
            const node *pnode = parties->u.arr.items[pi];
            if (!pnode || pnode->kind != N_OBJ) {
                continue;
            }
            int i = (int)pi + 1;
            if (i > 25) {
                break;
            }
            struct party *p = &state.p[i];

            /* Allocate per-member heap buffers before any
               rcw_i32_arr(..., p->ws, 5) writes below. Idempotent;
               re-entry on a concurrent-refresh path frees + reallocates
               cleanly (§19.3). */
            party_realize(p);

            rcw_str(pnode, "player", p->player, sizeof p->player);
            rcw_str(pnode, "party_name", p->party_name, sizeof p->party_name);

            /* Per-member array width — the save's party_member_max
               must already match res_party_member_max() per the
               strict check on realm.party_member_max above. */
            size_t mlen = (size_t)res_party_member_max();

            const node *names = obj_get(pnode, "name");
            if (names && names->kind == N_ARR) {
                size_t nn = names->u.arr.n < mlen ? names->u.arr.n : mlen;
                for (size_t m = 0; m < nn; m++) {
                    const node *nm = names->u.arr.items[m];
                    if (nm && nm->kind == N_STR) {
                        strncpy(p->name[m], nm->u.sv, sizeof p->name[m] - 1);
                        p->name[m][sizeof p->name[m] - 1] = '\0';
                    }
                }
            }

            rcw_party_flags(pnode, p->flag, sizeof p->flag);
            p->fireshield = (unsigned char)rcw_int(pnode, "fireshield", p->fireshield);
            p->bracelets = (unsigned char)rcw_int(pnode, "bracelets", p->bracelets);
            p->mastercrown = (unsigned char)rcw_int(pnode, "mastercrown", p->mastercrown);
            p->jazzyfoot = (unsigned char)rcw_int(pnode, "jazzyfoot", p->jazzyfoot);
            p->silverkey = (unsigned char)rcw_int(pnode, "silverkey", p->silverkey);
            p->pl = (unsigned char)rcw_int(pnode, "pl", p->pl);
            p->x = rcw_int(pnode, "x", p->x);
            p->y = rcw_int(pnode, "y", p->y);
            p->gold = rcw_int(pnode, "gold", p->gold);
            p->torches = (uint32_t)rcw_int(pnode, "torches", p->torches);
            p->food = rcw_int(pnode, "food", p->food);
            p->time_left = rcw_int(pnode, "time_left", p->time_left);
            p->hours_rested = rcw_int(pnode, "hours_rested", 14);

            rcw_i32_arr(obj_get(pnode, "inventory"), p->inventory, 17);
            rcw_i32_arr(obj_get(pnode, "ws"), p->ws, (int)mlen);
            rcw_i32_arr(obj_get(pnode, "h"), p->h, (int)mlen);
            rcw_i32_arr(obj_get(pnode, "hp"), p->hp, (int)mlen);
            rcw_i32_arr(obj_get(pnode, "wi"), p->wi, (int)mlen);
            rcw_i32_arr(obj_get(pnode, "ma"), p->ma, (int)mlen);
            rcw_i32_arr(obj_get(pnode, "ch"), p->ch, (int)mlen);
            rcw_enum_arr(obj_get(pnode, "cl"), "pc_class", p->cl, (int)mlen);
            rcw_enum_arr(obj_get(pnode, "ra"), "race", p->ra, (int)mlen);
            rcw_i32_arr(obj_get(pnode, "lv"), p->lv, (int)mlen);
            rcw_i32_arr(obj_get(pnode, "ar"), p->ar, (int)mlen);
            rcw_i32_arr(obj_get(pnode, "wp"), p->wp, (int)mlen);
            rcw_u32_arr(obj_get(pnode, "exp"), p->exp, (int)mlen);
            rcw_i32_arr(obj_get(pnode, "dead"), p->dead, (int)mlen);
            rcw_enum_barr(obj_get(pnode, "gender"), "gender", p->gender, (int)mlen);
            rcw_byte_arr(obj_get(pnode, "attack_today"), p->attack_today, 26);
            p->waiting_mail = (unsigned char)rcw_int(pnode, "waiting_mail", p->waiting_mail);
            p->attacked = (unsigned char)rcw_int(pnode, "attacked", p->attacked);
            p->kills = (uint32_t)rcw_int(pnode, "kills", p->kills);

            const node *vn = obj_get(pnode, "visited_npcs");
            if (vn && vn->kind == N_ARR) {
                size_t cap = (size_t)(res_npc_slot_max() + 1);
                size_t nvn = vn->u.arr.n < cap ? vn->u.arr.n : cap;
                for (size_t j = 0; j < nvn; j++) {
                    const node *it = vn->u.arr.items[j];
                    if (it && it->kind == N_INT) {
                        state.npc[j].visited[i] = (unsigned char)it->u.iv;
                    }
                }
            }

            /* Clear any pre-populated list from an earlier read pass, then
               re-append. Pre-§19.2 saves have no journal: key — party stays
               empty (free_journal already did the reset). */
            free_journal(p);
            const node *jn = obj_get(pnode, "journal");
            if (jn && jn->kind == N_ARR) {
                for (size_t j = 0; j < jn->u.arr.n; j++) {
                    const node *it = jn->u.arr.items[j];
                    if (it && it->kind == N_STR) {
                        party_journal_add(p, it->u.sv);
                    }
                }
            }
        }
    }

    /* Match load()'s behavior: set state.cpl by matching the logged-in
       player name against each party's `player` field. */
    for (int i = 1; i <= state.num_parties; i++) {
        if (!stricmp(state.global_name, state.p[i].player)) {
            state.cpl = i;
        }
    }

    node_free(root);
    return 1;
}

void save() {
    char kl[8];

    getclk(kl);
    state.last_played_day = kl[3];

    write_current_world();
}

char *pronoun(uint8_t nr, uint8_t xx) {
    uint8_t gender = pm_gender(pm_cur(nr));
    /* Original: female's xx in {2,3} both returned "her"; clamp to match. */
    if (gender == res_enum_id("gender", "female") && xx == 3) {
        xx = 2;
    }
    return (char *)res_pronoun(gender, xx);
}

static void createparty() {
    int32_t a, g, passed;
    char str[80];

    clw();

    tx0(res_text("driv.create.header"));

    if (state.num_parties >= PARTY_SLOT_HARD_MAX - 1) {
        tx0(res_text("driv.create.full"));
        ryl_pause();
        cleanexit();
    }

    state.num_parties++;
    a = state.num_parties;
    state.cpl = a;

    /* Allocate per-member heap buffers before the first state.p[a].<f>[g]
       write below. state.p[] is a static BSS global so the pointers
       start NULL; without this the ws/h/hp/... writes would segfault
       (§19.3). Safe to call twice — party_realize is idempotent. */
    party_realize(&state.p[a]);

    while (1) {
        tx0(res_text("driv.create.name_q"));
        tx(": ");

        getstr(18, state.p[a].party_name, sizeof state.p[a].party_name);
        if (!state.p[a].party_name[0]) {
            state.num_parties--;
            state.cpl = 0;
            return;
        } else {
            break;
        }
    }

    g = 0;

    int sr_lo = res_int_path("driv.stat_roll.min");
    int sr_hi = res_int_path("driv.stat_roll.max");
    while (g < res_party_member_max()) {
        clw();
        /* Display the slot 1-based for the player ("Stats for char: 1");
           the C side stores them 0-based but the UX still calls them
           1..N to match the existing "Who? (1-N)" prompts. */
        tx("Stats for char: %d\r\n\r\n", g + 1);
        state.p[a].ws[g] = ryl_random(sr_lo, sr_hi);
        state.p[a].wi[g] = ryl_random(sr_lo, sr_hi);
        state.p[a].h[g] = ryl_random(sr_lo, sr_hi);
        state.p[a].hp[g] = state.p[a].h[g];
        state.p[a].ch[g] = ryl_random(sr_lo, sr_hi);
        tx("Weapon Skill : %2d\r\n", state.p[a].ws[g]);
        tx("Health       : %2d\r\n", state.p[a].h[g]);
        tx("Wisdom       : %2d\r\n", state.p[a].wi[g]);
        tx("Charisma     : %2d\r\n\r\n", state.p[a].ch[g]);
        tx0(res_text("driv.create.reroll"));
        if (!hotkey()) {
            while (1) {
                clw();
                tx("Give Character %d a name!\r\n", g + 1);
                tx(": ");
                str[0] = 0;
                getstr(12, state.p[a].name[g], sizeof state.p[a].name[g]);
                if (!state.p[a].name[g][0]) {
                    state.num_parties--;
                    state.cpl = 0;
                    return;
                } else {
                    break;
                }
            }
            state.p[a].name[g][0] = toupper(state.p[a].name[g][0]);

            clw();
            tx_fmt("driv.create.gender_q",
                   (ryl_bind[]){
                       {"name", state.p[a].name[g]},
                       {0},
                   });
            tx0(res_text("driv.create.gender_opts"));
            str[0] = 0;
            while (str[0] < '1' || str[0] > '2') {
                tx(": ");
                getstr(2, str, sizeof str);
            }
            state.p[a].gender[g] = str[0] - 48;

            clw();
            tx_fmt("driv.create.race_q",
                   (ryl_bind[]){
                       {"name", state.p[a].name[g]},
                       {0},
                   });
            tx0(res_text("driv.create.race_opts"));
            str[0] = 0;
            while (str[0] < '1' || str[0] > '4') {
                tx(": ");
                getstr(2, str, sizeof str);
            }
            state.p[a].ra[g] = str[0] - 48;

            clw();
            tx0(res_text("driv.create.class_menu"));
            str[0] = 0;
            while (str[0] < '1' || str[0] > '6') {
                tx(": ");
                getstr(2, str, sizeof str);
            }
            state.p[a].cl[g] = str[0] - 48;
            clw();
            const char *fail_text = NULL;
            passed = res_class_min_stat_check(state.p[a].cl[g],
                                              state.p[a].ws[g],
                                              state.p[a].wi[g],
                                              state.p[a].ch[g],
                                              &fail_text);
            if (!passed && fail_text) {
                tx0(res_text(fail_text));
                ryl_delay(1000);
            }
            if (passed) {
                passed = 0;
                if (pm_is_spellcaster(pm_of(&state.p[a], g))) {
                    state.p[a].ma[g] = state.p[a].wi[g];
                } else {
                    state.p[a].ma[g] = 0;
                }
                show(g);
                g++;
            }
        }
    }
    state.p[a].x = res_int_path("driv.start.x");
    state.p[a].y = res_int_path("driv.start.y");
    state.p[a].gold = rk_starting_gold(res_int_path("driv.start.gold"));
    state.p[a].torches = res_int_path("driv.start.torches");
    state.p[a].food = res_int_path("driv.start.food");
    state.p[a].time_left = res_int_path("driv.start.time_left");
    state.p[a].hours_rested = res_int_path("driv.start.hours_rested");
    state.p[a].pl = (unsigned char)res_party_member_max();
    state.p[a].fireshield = 0;
    state.p[a].bracelets = 0;
    state.p[a].mastercrown = 0;
    state.p[a].jazzyfoot = 0;
    state.p[a].silverkey = 0;
    memset(state.p[a].inventory, 0, sizeof state.p[a].inventory);
    memset(state.p[a].flag, 0, sizeof state.p[a].flag);
    for_each_party_member(i) {
        pm_t pm = pm_of(&state.p[a], i);
        pm_set_lv(pm, 1);
        pm_set_ar(pm, 0);
        pm_set_exp(pm, 0);
        pm_set_dead(pm, 0);
    }
    rk_apply_party_lv(a);
    rk_apply_dead_slots(a);
    rk_apply_pos(a);
    rk_apply_ydarg_grants(a);
    rk_apply_party_wp(a);
    rk_apply_party_hp_full(a);
    strcpy(state.p[a].player, state.global_name);
    clw();
    tx0(res_text("driv.create.saving"));
    save();
    ryl_delay(1000);
}

void showparty() {
    clw();
    tx("%u torches.\r\n", pa_torches());
    tx("%d rations.\r\n", pa_food());
    tx("%d gold.\r\n\r\n", pa_gold());
    if (pa_special(PS_FIRESHIELD)) {
        tx0(res_text("driv.party.has_shield"));
    }
    if (pa_special(PS_BRACELETS)) {
        tx0(res_text("driv.party.has_bracelets"));
    }
    if (pa_special(PS_MASTERCROWN)) {
        tx0(res_text("driv.party.has_crown"));
    }
    if (pa_special(PS_JAZZYFOOT)) {
        tx0(res_text("driv.party.has_foot"));
    }
    if (pa_special(PS_SILVERKEY)) {
        tx0(res_text("driv.party.has_key"));
    }

    ryl_pause();
    clw();
    invlist();
    tx("\r\n");
    ryl_pause();
}

void invlist() {
    /* 8 rows × 2 columns of letter-labeled slots (A..P). The column
       pairing is UI-bound to INVENTORY_SLOT_MAX == 16; raising the
       cap would need a UI redesign, not just a larger iteration. */
    char item[INVENTORY_SLOT_MAX + 2][80];
    int smax = res_inventory_slot_max();

    for (int i = 1; i <= INVENTORY_SLOT_MAX; i++) {
        int id = (i <= smax) ? pa_inventory(i) : 0;
        strncpy(item[i], object(id), 9);
        item[i][9] = 0;
    }

    for (int i = 1; i < INVENTORY_SLOT_MAX; i += 2) {
        tx("%c. %-9s %c. %-9s\r\n", 64 + i, item[i], 65 + i, item[i + 1]);
    }
}

void show(int32_t nr) {
    char str[10];
    pm_t pm = pm_cur(nr);

    clw();
    tx("Name      : %s\r\n", pm_name(pm));
    tx("Race      : %s\r\n", res_enum("race", pm_ra(pm)));
    tx("Class     : %s\r\n", res_enum("pc_class", pm_cl(pm)));
    tx("\r\n");
    tx("Skill     : %d\r\n", pm_ws(pm));
    tx("Hp/H      : %d/%d\r\n", pm_hp(pm), pm_h(pm));
    tx("Wisdom    : %d\r\n", pm_wi(pm));
    tx("Charisma  : %d\r\n", pm_ch(pm));
    tx("Magic     : %d\r\n", pm_ma(pm));
    tx("\r\n");
    ryl_pause();
    clw();
    tx("Level     : %d\r\n", pm_lv(pm));
    tx("Weapon    : %s\r\n", object(pm_wp(pm)));
    tx("Armour    : %s\r\n\r\n", object(pm_ar(pm)));
    if (pm_dead(pm)) {
        strcpy(str, "Dead.");
    } else {
        strcpy(str, "Alive.");
    }
    tx("Status    : %s\r\n\r\n", str);
    tx("Exp       : %u\r\n\r\n", pm_exp(pm));
    ryl_pause();
}

void help() {
    clw();
    /* C E H J L  P Q R S T  V W X Z ! ? SPC */
    tx0(res_text("driv.help.p1"));
    ryl_pause();
    clw();
    tx0(res_text("driv.help.p2"));
    ryl_pause();
    clw();
    tx0(res_text("driv.help.p3"));
    ryl_pause();
    clw();
}
