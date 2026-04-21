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

    int i, j, x, ut = 0;
    long t;
    FILE *fh;

    // Random people //

    tx("%cSetting up new campaign. Please wait.", 12);
    log("A new campaign starts. Baron Ydarg awaits...\n");

    for (i = 1, j = 1; i < 25; i++, j++) {
        Delay(30);
        time(&t);
        srand((unsigned int) t + i);
        if (j == 13)
            j = 1;
        npc[i].job = j;
        npc[i].nr = i;
        npc[i].towne = (rand() % 5);
        if (npc[i].towne) {
            npc[i].bl = (rand() % 2);
            npc[i].bl++;
        } else
            npc[i].bl = 0;
        if (!npc[i].towne) {
            while (1) {
                npc[i].x = (rand() % 50) + 8;
                npc[i].y = (rand() % 20) + 8;
                if (npc[i].x == 48)
                    npc[i].x++;  // No hermit collisions
                for (x = 1, ut = 0; x < i + 1; x++) {
                    if ((npc[x].x == npc[i].x) && (npc[x].y == npc[i].y))
                        ut++;
                }
                if (map[npc[i].y][npc[i].x] != '0' && map[npc[i].y][npc[i].x] != '1' &&
                    map[npc[i].y][npc[i].x] != '2' && map[npc[i].y][npc[i].x] != 'm' &&
                    map[npc[i].y][npc[i].x] != 'w')
                    ut++;
                if (ut > 1)
                    break;
            }
        } else {
            npc[i].x = 0;
            npc[i].y = 0;
        }
        tx(".");
    }
    tx("%c", 12);

    /* FIXED */

    /* Garth - Reference Mission */
    npc[1].nr = 1;
    npc[1].job = 1;
    npc[1].towne = 1;
    npc[1].bl = 1;
    npc[1].x = npc[1].y = 0;

    /* Innkeeper town 1 */
    npc[25].nr = 25;
    npc[25].job = 14;
    npc[25].towne = 1;
    npc[25].bl = 2;
    npc[25].x = npc[25].y = 0;

    /* Innkeeper town 2 */
    npc[26].nr = 26;
    npc[26].job = 14;
    npc[26].towne = 2;
    npc[26].bl = 2;
    npc[26].x = npc[26].y = 0;

    /* Innkeeper town 3 */
    npc[27].nr = 27;
    npc[27].job = 14;
    npc[27].towne = 3;
    npc[27].bl = 2;
    npc[27].x = npc[27].y = 0;

    /* Innkeeper town 4 */
    npc[28].nr = 28;
    npc[28].job = 14;
    npc[28].towne = 4;
    npc[28].bl = 2;
    npc[28].x = npc[28].y = 0;

    /* Tkeeper town 1 */
    npc[29].nr = 29;
    npc[29].job = 15;
    npc[29].towne = 1;
    npc[29].bl = 1;
    npc[29].x = npc[29].y = 0;

    /* Tkeeper town 2 */
    npc[30].nr = 30;
    npc[30].job = 15;
    npc[30].towne = 2;
    npc[30].bl = 1;
    npc[30].x = npc[30].y = 0;

    /* Tkeeper town 3 */
    npc[31].nr = 31;
    npc[31].job = 15;
    npc[31].towne = 3;
    npc[31].bl = 1;
    npc[31].x = npc[31].y = 0;

    /* Tkeeper town 4 */
    npc[32].nr = 32;
    npc[32].job = 15;
    npc[32].towne = 4;
    npc[32].bl = 1;
    npc[32].x = npc[32].y = 0;

    /* Hermit somewhere in wilderness */
    npc[33].nr = 33;
    npc[33].job = 16;
    npc[33].towne = 0;
    npc[33].bl = 0;
    npc[33].x = 48;
    npc[33].y = 7;

    /* Bargirl Town 1 */
    npc[34].nr = 34;
    npc[34].job = 13;
    npc[34].towne = 1;
    npc[34].bl = 1;
    npc[34].x = npc[34].y = 0;

    /* Bargirl Town 3 */
    npc[35].nr = 35;
    npc[35].job = 13;
    npc[35].towne = 3;
    npc[35].bl = 1;
    npc[35].x = npc[35].y = 0;

    for (i = 36; i < 40; i++) {
        npc[i].nr = i;
        npc[i].job = 1;
        npc[i].towne = 0;
        npc[i].bl = npc[i].x = npc[i].y = 0;
    }

    for (i = 1; i < 40; i++) {
        for (j = 0; j < 26; j++) {
            npc[i].visited[j] = 0;
        }
    }

    if (!(fh = fopen("data/npc.dat", "wb")))
        cleanexit();
    for (i = 1; i < 40; i++) {
        fwrite((char *) &npc[i], sizeof(npc[i]), 1, fh);
    }
    fclose(fh);
}
