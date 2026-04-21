#include "ryl.h"

void reganim() {
    uint8_t i;
    time_t t;

    time(&t);
    srand((unsigned int) t);

    /* Please Register */
    /* Blit 15 times with a 30 tick delay */

    if (rand() % 2) {
        return;
    }

    cl();

    for (i = 0; i < 15; i++) {
        c(31, 12);
        tx("\x1b[3%dmPLEASE REGISTER!", (rand() % 6) + 1);
        Delay(4);
    }
    c(31, 12);
    tx("\x1b[31mPLEASE REGISTER!");
    Delay(150);
    c(30, 12);
    tx("\x1b[32m█");
    Delay(10);
    for (i = 30; i < 48; i++) {
        c(i, 11);
        if (i % 2) {
            tx("\x1b[32m█");
        } else {
            tx("\x1b[33m█");
        }
        Delay(10);
    }
    c(47, 12);
    tx("\x1b[33m█");
    Delay(10);
    for (i = 47; i > 29; i--) {
        c(i, 13);
        if (i % 2) {
            tx("\x1b[32m█");
        } else {
            tx("\x1b[33m█");
        }
        Delay(10);
    }
    Delay(100);

    cl();
    disptext("data/ansi/screen.ans");
}

void blitgfx(uint8_t pic) {
    char path[64];

    clr_map_window();
    snprintf(path, sizeof path, "data/tiles/%02d.ans", pic);
    render_at(path, 8, 6);
    tx("\x1b[37m\x1b[40m");
    c(49, 6);
}

void clr_map_window() {
    tx("\x1b[37m\x1b[40m");
    c(8, 6);
    tx("                ");
    c(8, 7);
    tx("                ");
    c(8, 8);
    tx("                ");
    c(8, 9);
    tx("                ");
    c(8, 10);
    tx("                ");
    c(8, 11);
    tx("                ");
    c(8, 12);
    tx("                ");
    c(8, 13);
    tx("                ");
    c(8, 14);
    tx("                ");
}
