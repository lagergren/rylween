#include <stdio.h>
#include <stdlib.h>

void savefile(void);
void editplrs(void);
void editdate(void);
void menu(void);
void main(void);

int i, antal_spelare, senaste_dagen, dx[15], dy[15];

struct party {
    char player[30];
    char partyname[30];
    char name[5][30];

    int flag[40];

    int fireshield;
    int bracelets;
    int mastercrown;
    int jazzyfoot;
    int silverkey;

    int pl; /* How many are they */
    int x;
    int y;
    unsigned long gold;
    int torches;
    int food;
    int tid_kvar;

    int inventory[17];

    int ws[5]; /* Wisdom */
    int h[5];  /* Health */
    int hp[5]; /* HitPts */
    int wi[5]; /* Wisdom */
    int ma[5]; /* Magic  */
    int ch[5]; /* Charis */

    int cl[5]; /* Class  */
    int ra[5]; /* Race   */
    int lv[5]; /* Level  */

    int ar[5]; /* Ready Armour */
    int wp[5]; /* Ready Weapon */
    unsigned long exp[5];

    int dead[5]; /* Dead | Alive */
};
struct party p[26];

void main() {
    FILE *fh;

    if (!(fh = fopen("data/realm.dat", "rb")))
        exit(10);
    fread((char *) &antal_spelare, sizeof(antal_spelare), 1, fh);
    fread((char *) &senaste_dagen, sizeof(senaste_dagen), 1, fh);
    for (i = 1; i < 15; i++)
        fread((char *) &dx[i], sizeof(dx[i]), 1, fh);
    for (i = 1; i < 15; i++)
        fread((char *) &dy[i], sizeof(dy[i]), 1, fh);
    for (i = 1; i < antal_spelare + 1; i++) {
        fread((char *) &p[i], sizeof(p[i]), 1, fh);
    }

    fclose(fh);
    menu();
}
void menu() {
    char str[80];

    while (1) {
        printf("\n1. Edit latest play date\n");
        printf("2. Edit a player\n");
        printf("3. Save User File\n");
        printf("4. Exit\n\n: ");
        gets(str);
        switch (str[0]) {
            case 0:
                exit(0);
            case '1':
                editdate();
            case '2':
                editplrs();
            case '3':
                savefile();
            case '4':
                exit(0);
        }
    }
}
void editdate() {
}
void editplrs() {
    int i, nr;
    char str[80];

    printf("%cEdit Player:\n\n", 12);
    for (i = 1; i < antal_spelare + 1; i++)
        printf("%2d. %-20s (%-20s)\n", i, p[i].partyname, p[i].player);
    printf("\n: ");
    gets(str);
    if (str[0] == 0)
        return;
    if (atoi(str[0]) > antal_spelare || atoi(str[0]) < 1)
        return;
    nr = atoi(str[0]);

    while (1) {
        printf("Party : %-20s   Player : %-20s\n\n", p[nr].partyname, p[nr].player);
        for (i = 1; i < 5; i++) {
            printf("Player %d\n-----------\n", i);
            printf("Ws: %3d  Hp/H: %3d/%3d  Wi: %3d  Ma: %3d  Ch: %3d\n", p[nr].ws[i], p[nr].hp[i],
                   p[nr].h[i], p[nr].wi[i], p[nr].ma[i], p[nr].ch[i]);
        }
    }
}
void savefile() {
}
