#include "ryl.h"
#include "res.h"
#include "driv.h"
#include "dungeon.h"
#include "gfx.h"
#include "log.h"
#include "party.h"
#include "primitives.h"
#include "script.h"
#include "subs.h"

void dungeon(int32_t levels, const char *scenario) {
    /* scenario: symbolic key into dungeons.scenarios in world.json5
       (e.g. "fire_shield_cave"). NULL = randomly discovered cave with
       no quest placements — random fill stands alone.

       Each dungeon level is filled with a random generic-event id
       (1..9) and then overlaid with the scenario's quest events
       (10..28). Inside the main loop, every visit dispatches lvl[cl]
       through the dungeon_encounter enum to a `dungeon.<symbol>`
       script in data/world/scripts/dungeon.json5. The script owns
       all narration, combat, flag updates and inventory; the C side
       is just the loop driver + the (D)escend / (A)scend / utility
       sub-menu after each event. */

    int32_t i, cl, lvl[26];
    char c, d;

    blitgfx(insidecavepic);

    state.banished = 0;

    for (i = 1; i < levels + 1; i++) {
        lvl[i] = ryl_random(1, 9);
    }
    res_dungeon_apply_placements(scenario, lvl, levels);

    cl = 1;

    while (1) {
        clw();

        /* Resolve the encounter id at this level to its symbol and
           play dungeon.<symbol>. Slot 0 is the sentinel returned by
           res_enum as "" so the loop falls through to the post-event
           utility menu. After the script, reset lvl[cl] unless
           dungeons.recurring marks the encounter as recurring
           (optionally `until_flag` is satisfied). */
        const char *enc_sym = res_enum("dungeon_encounter", lvl[cl]);
        if (enc_sym[0]) {
            char script_id[96];
            snprintf(script_id, sizeof script_id, "dungeon.%s", enc_sym);
            /* `cl` is exposed as a binding so colour_riddle can
               render ANTECHAMBER #(cl-12) and pick the per-level
               passing colour. */
            char cl_s[8];
            snprintf(cl_s, sizeof cl_s, "%d", (int)cl);
            play_script(script_id, (ryl_bind[]){
                                       {"cl", cl_s},
                                       {0},
                                   });
            if (!res_dungeon_encounter_recurring(enc_sym)) {
                lvl[cl] = 0;
            }
        }

        /* Pick an alternative — encounter over. */
        while (1) {
            clw();
            statusline();
            tx("You are on level %d.\r\n\r\n", cl);
            tx("(D) Descend\r\n");
            tx("(A) Ascend\r\n\r\n");
            c = key();
            c = toupper(c);

            if (c == '?') {
                help();
            }
            if (c == 'L') {
                drop();
            }
            if (c == 'T') {
                armouroff();
            }
            if (c == 'P') {
                weaponoff();
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
                spell(1); /* 0 means over ground */
                if (state.banished) {
                    for (i = 1; i < levels; i++) {
                        if (lvl[i] >= 3 && lvl[i] <= 5) {
                            lvl[i] = 0;
                        }
                    }
                }
            }
            if (c == 'V') {
                showparty();
                clw();
                tx("Who? (1-%d/Q/A)\r\n", res_party_member_max());
                while (1) {
                    d = key();
                    if (pm_digit_in_range(d) || toupper(d) == 'Q' || toupper(d) == 'A') {
                        break;
                    }
                }
                if (pm_digit_in_range(d)) {
                    int slot = d - '1';
                    LOG_D("input", "dungeon show: digit=%c -> slot=%d", d, slot);
                    show(slot);
                }
                if (toupper(d) == 'A') {
                    for_each_party_member(i) {
                        show(i);
                    }
                }
                clw();
            }

            if (c == 'D') {
                if (!pa_torches()) {
                    clw();
                    tx("You have used your last\r\n");
                    tx("torch. You can't go\r\n");
                    tx("any further.\r\n\r\n");
                    ryl_pause();
                    clw();
                } else {
                    if (cl < levels) {
                        pa_add_torches(-1);
                    }
                    if (cl == levels) {
                        clw();
                        tx("The cave doesn't\r\n");
                        tx("go deeper.\r\n");
                        ryl_delay(1000);
                    } else {
                        clw();
                        tx("You enter level %d.\r\n", cl + 1);
                        ryl_delay(600);
                        cl++;
                        break;
                    }
                }
            }

            if (c == 'A') {
                if (cl == 1) {
                    clw();
                    tx("You leave the\r\n");
                    tx("hideous cavern!\r\n\r\n");
                    ryl_pause();
                    clr_map_window();
                    update_map();
                    clw();
                    return;
                } else {
                    cl--;
                    break;
                }
            }
        }
    }
}
