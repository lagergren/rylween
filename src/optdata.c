#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <clib/dos_protos.h>
#include <fcntl.h>
#include <fctype.h>
#include <exec/types.h>
#include <exec/ports.h>
#include <exec/semaphores.h>
#include <libraries/dos.h>
#include <time.h>

struct party {
    char player[30];
    char partyname[30];
    char name[5][30];

    int32_t flag[40];

    int32_t fireshield;
    int32_t bracelets;
    int32_t mastercrown;
    int32_t jazzyfoot;
    int32_t silverkey;

    int32_t pl; /* How many are they */
    int32_t x;
    int32_t y;
    unsigned long gold;
    int32_t torches;
    int32_t food;
    int32_t tid_kvar;

    int32_t inventory[17];

    int32_t ws[5]; /* Wisdom */
    int32_t h[5];  /* Health */
    int32_t hp[5]; /* HitPts */
    int32_t wi[5]; /* Wisdom */
    int32_t ma[5]; /* Magic  */
    int32_t ch[5]; /* Charis */

    int32_t cl[5]; /* Class  */
    int32_t ra[5]; /* Race   */
    int32_t lv[5]; /* Level  */

    int32_t ar[5]; /* Ready Armour */
    int32_t wp[5]; /* Ready Weapon */
    unsigned long exp[5];

    int32_t dead[5]; /* Dead | Alive */
};

struct party p[26];

void main(void);
void clrjr(UBYTE);

int32_t antal_spelare = 0, senaste_dagen = 0;

void main() {
    FILE *fh;
    int32_t killed[26];
    int32_t dx[16], dy[15];
    int32_t i;
    int32_t dec = 0;

    for (i = 0; i < 26; i++) {
        killed[i] = 0;
    }

    printf("OptData - For Adventurers of Rylween\n");
    if (!(fh = fopen("data/rylween.reg", "rb"))) {
        printf("Please consider registering.\n");
        Delay(5 * 50);
    } else {
        fclose(fh);
    }

    if (!(fh = fopen("data/realm.dat", "rb"))) {
        printf("Error: Could not locate Rylween player data.\n");
        return;
    }

    fread((char *) &antal_spelare, sizeof(antal_spelare), 1, fh);
    fread((char *) &senaste_dagen, sizeof(senaste_dagen), 1, fh);
    for (i = 1; i < 15; i++) {
        fread((char *) &dx[i], sizeof(dx[i]), 1, fh);
    }
    for (i = 1; i < 15; i++) {
        fread((char *) &dy[i], sizeof(dy[i]), 1, fh);
    }

    if (antal_spelare) {
        for (i = 1; i < antal_spelare + 1; i++) {
            fread((char *) &p[i], sizeof(p[i]), 1, fh);
        }
    }
    fclose(fh);

    printf("Found %d player records.\n\n", antal_spelare);
    Delay(40);

    for (i = 1; i < antal_spelare + 1; i++) {
        if (p[i].exp[1] + p[i].exp[2] + p[i].exp[3] + p[i].exp[4] == 0) {
            printf("#%2d %-20s - played by %-20s - added...\n", i, p[i].partyname, p[i].player);
            killed[i] = 1;
            dec++;
        }
    }

    for (i = 1; i < antal_spelare + 1; i++) {
        if (killed[i]) {
            clrjr(i);
        }
    }

    printf("\nDeleting %d records...\n\n", dec);
    Delay(30);
    if (!(fh = fopen("data/realm.dat", "wb"))) {
        printf("Error: Can't write to userfile.\n");
        exit(1);
    }
    antal_spelare -= dec;
    fwrite((char *) &antal_spelare, sizeof(antal_spelare), 1, fh);
    fwrite((char *) &senaste_dagen, sizeof(senaste_dagen), 1, fh);
    for (i = 1; i < 15; i++) {
        fwrite((char *) &dx[i], sizeof(dx[i]), 1, fh);
    }
    for (i = 1; i < 15; i++) {
        fwrite((char *) &dy[i], sizeof(dy[i]), 1, fh);
    }
    for (i = 1; i < antal_spelare + 1 + dec; i++) {
        if (!killed[i]) {
            fwrite((char *) &p[i], sizeof(p[i]), 1, fh);
            printf("Re-saving #%2d %s...\n", i, p[i].partyname);
        }
    }
    fclose(fh);

    printf("\nFinished!!!\n");
}
void clrjr(UBYTE nr) {
    FILE *fh;
    char str[144], a[10], b[20], temp[4];
    ULONG byte[50];
    UBYTE i, j, k, current;

    if (!(fh = fopen("data/journal", "r"))) {
        exit(0);
    }

    i = 1;

    while (1) {
        str[0] = 0;
        fgets((char *) str, sizeof(str), fh);
        if (str[0] == 0) {
            break;
        }
        sscanf(str, "%s %s", a, b);
        if (atoi(a) == nr) {
            byte[i] = ftell(fh);
            byte[i] -= strlen(str);
            printf("%d\n", byte[i]);
            i++;
        }
    }

    if (!(fh = fopen("data/journal", "r+"))) {
        exit(0);
    }

    for (j = 1; j < i + 1; j++) {
        fseek(fh, byte[j], 0);
        fputc('*', fh);
        fputc('*', fh);
    }

    fclose(fh);

    /* Och nu skall alla som hade h|gre spelarnummer {n 'nr' minskas ett steg */
    /* Till{ggas kan att det blir ett helvete */

    current = nr + 1;

    if (!(fh = fopen("data/journal", "r"))) {
        exit(0);
    }

    for (k = current; k < antal_spelare + 1; k++) {
        i = 1;

        while (1) {
            str[0] = 0;
            fgets((char *) str, sizeof(str), fh);
            if (str[0] == 0) {
                break;
            }
            sscanf(str, "%s %s", a, b);
            if (atoi(a) == current) {
                byte[i] = ftell(fh);
                byte[i] -= strlen(str);
                printf("%d\n", byte[i]);
                i++;
            }
        }

        if (!(fh = fopen("data/journal", "r+"))) {
            exit(0);
        }

        for (j = 1; j < i + 1; j++) {
            fseek(fh, byte[j], 0);
            if (current <= 10) {
                sprintf(temp, "%d", current - 1);
                fputc(temp[0], fh);
                fputc(32, fh);
            } else {
                sprintf(temp, "%d", current - 1);
                fputc(temp[0], fh);
                fputc(temp[1], fh);
            }
        }
        fclose(fh);
    }
}
