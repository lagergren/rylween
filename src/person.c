#include "ryl.h"

/* 24 random people, 15 fixed, like innkeepers, bargirls etc. */

/*
** The people get the names in the same order. Player 1 has always
** name one, player 15 name 15 and so on. The last 15 are fixed 
** characters with fixed positions. eg. innkeepers, bargirls and so on.
** They have also fixed jobs. The first 12 have jobs 1-12, the second
** too. There is a CHAIN of jobs, and 2 persons have the same job.
*/

/*
** 1: Merchant A has been robbed. If the players accept the mission
**    set flag 1, and the next random encounter will get them the money
**    set flag 2, return, get money, set flag 3
**
** 2: Ranger A wants the players to get a message for Ranger B who
**    will reward them. Mission gotten, A.1, mission completet A.2
**
** 3: Fighter needs money. Give him 500 gold and he will tell you
**    that Thief B knows about the Master Crown (Fa.1). Thief B
**    directs the players to the fixed hermit at 20x20. (Tb.1)
**    The Hermit will give them the mission (H.1) to get the
**    head of a gnoll. Next encounter. (H.2) Then he gives them
**    the sword. (H.3)
*/

void place_npcs() {
    /* NPC: People to talk to. There are others, but not as interesting. */

    int32_t i, j, x, ut = 0;
    FILE *fh;

    // Random people //

    tx("%cSetting up new campaign. Please wait.", 12);
    ryl_log("A new campaign starts. Baron Ydarg awaits...\n");

    for (i = 1, j = 1; i < 25; i++, j++) {
        ryl_delay(600);
        if (j == 13) {
            j = 1;
        }
        state.npc[i].job = j;
        state.npc[i].nr = i;
        state.npc[i].towne = ryl_random(0, 4);
        if (state.npc[i].towne) {
            state.npc[i].bl = ryl_random(0, 1);
            state.npc[i].bl++;
        } else {
            state.npc[i].bl = 0;
        }
        if (!state.npc[i].towne) {
            while (1) {
                state.npc[i].x = ryl_random(8, 57);
                state.npc[i].y = ryl_random(8, 27);
                if (state.npc[i].x == 48) {
                    state.npc[i].x++;  // No hermit collisions
                }
                for (x = 1, ut = 0; x < i + 1; x++) {
                    if ((state.npc[x].x == state.npc[i].x) && (state.npc[x].y == state.npc[i].y)) {
                        ut++;
                    }
                }
                if (state.map[state.npc[i].y][state.npc[i].x] != '0' && state.map[state.npc[i].y][state.npc[i].x] != '1' &&
                    state.map[state.npc[i].y][state.npc[i].x] != '2' && state.map[state.npc[i].y][state.npc[i].x] != 'm' &&
                    state.map[state.npc[i].y][state.npc[i].x] != 'w') {
                    ut++;
                }
                if (ut > 1) {
                    break;
                }
            }
        } else {
            state.npc[i].x = 0;
            state.npc[i].y = 0;
        }
        tx(".");
    }
    tx("%c", 12);

    /* FIXED */

    /* Garth - Reference Mission */
    state.npc[1].nr = 1;
    state.npc[1].job = 1;
    state.npc[1].towne = 1;
    state.npc[1].bl = 1;
    state.npc[1].x = state.npc[1].y = 0;

    /* Innkeeper town 1 */
    state.npc[25].nr = 25;
    state.npc[25].job = 14;
    state.npc[25].towne = 1;
    state.npc[25].bl = 2;
    state.npc[25].x = state.npc[25].y = 0;

    /* Innkeeper town 2 */
    state.npc[26].nr = 26;
    state.npc[26].job = 14;
    state.npc[26].towne = 2;
    state.npc[26].bl = 2;
    state.npc[26].x = state.npc[26].y = 0;

    /* Innkeeper town 3 */
    state.npc[27].nr = 27;
    state.npc[27].job = 14;
    state.npc[27].towne = 3;
    state.npc[27].bl = 2;
    state.npc[27].x = state.npc[27].y = 0;

    /* Innkeeper town 4 */
    state.npc[28].nr = 28;
    state.npc[28].job = 14;
    state.npc[28].towne = 4;
    state.npc[28].bl = 2;
    state.npc[28].x = state.npc[28].y = 0;

    /* Tkeeper town 1 */
    state.npc[29].nr = 29;
    state.npc[29].job = 15;
    state.npc[29].towne = 1;
    state.npc[29].bl = 1;
    state.npc[29].x = state.npc[29].y = 0;

    /* Tkeeper town 2 */
    state.npc[30].nr = 30;
    state.npc[30].job = 15;
    state.npc[30].towne = 2;
    state.npc[30].bl = 1;
    state.npc[30].x = state.npc[30].y = 0;

    /* Tkeeper town 3 */
    state.npc[31].nr = 31;
    state.npc[31].job = 15;
    state.npc[31].towne = 3;
    state.npc[31].bl = 1;
    state.npc[31].x = state.npc[31].y = 0;

    /* Tkeeper town 4 */
    state.npc[32].nr = 32;
    state.npc[32].job = 15;
    state.npc[32].towne = 4;
    state.npc[32].bl = 1;
    state.npc[32].x = state.npc[32].y = 0;

    /* Hermit somewhere in wilderness */
    state.npc[33].nr = 33;
    state.npc[33].job = 16;
    state.npc[33].towne = 0;
    state.npc[33].bl = 0;
    state.npc[33].x = 48;
    state.npc[33].y = 7;

    /* Bargirl Town 1 */
    state.npc[34].nr = 34;
    state.npc[34].job = 13;
    state.npc[34].towne = 1;
    state.npc[34].bl = 1;
    state.npc[34].x = state.npc[34].y = 0;

    /* Bargirl Town 3 */
    state.npc[35].nr = 35;
    state.npc[35].job = 13;
    state.npc[35].towne = 3;
    state.npc[35].bl = 1;
    state.npc[35].x = state.npc[35].y = 0;

    for (i = 36; i < 40; i++) {
        state.npc[i].nr = i;
        state.npc[i].job = 1;
        state.npc[i].towne = 0;
        state.npc[i].bl = state.npc[i].x = state.npc[i].y = 0;
    }

    for (i = 1; i < 40; i++) {
        for (j = 0; j < 26; j++) {
            state.npc[i].visited[j] = 0;
        }
    }

    if (!(fh = fopen(ryl_state_path("npc.dat"), "wb"))) {
        cleanexit();
    }
    for (i = 1; i < 40; i++) {
        fwrite((char *)&state.npc[i], sizeof(state.npc[i]), 1, fh);
    }
    fclose(fh);
}
