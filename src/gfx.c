#include "ryl.h"

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
