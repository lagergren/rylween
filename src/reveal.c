#include <stdio.h>
#include <stdlib.h>

main() {
    int a, i;
    int dx[15], dy[15];
    FILE *fh;

    if (!(fh = fopen("data/realm.dat", "rb")))
        exit(10);

    fread((char *) &a, sizeof(a), 1, fh);
    fread((char *) &a, sizeof(a), 1, fh);
    for (i = 1; i < 15; i++)
        fread((char *) &dx[i], sizeof(dx[i]), 1, fh);
    for (i = 1; i < 15; i++)
        fread((char *) &dy[i], sizeof(dy[i]), 1, fh);

    fclose(fh);

    for (i = 1; i < 10; i++) {
        printf("%2d. x:%2d y:%2d\n", i, dx[i], dy[i]);
    }
}
