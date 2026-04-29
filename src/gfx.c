#include "ansi.h"
#include "gfx.h"
#include "primitives.h"
#include "res.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>

void blitgfx(uint8_t pic) {
    char path[64];

    clr_map_window();
    snprintf(path, sizeof path, "data/tiles/%02d.ans", pic);
    int x, y;
    res_screen_xy(SCR_MAP_WINDOW, &x, &y);
    render_at(path, (uint8_t)x, (uint8_t)y);
    tx(ANSI_FG_WHITE ANSI_BG_BLACK);
    screen_goto(SCR_TEXT_WINDOW);
}

void clr_map_window() {
    int w, h;
    res_screen_wh(SCR_MAP_WINDOW, &w, &h);
    char blank[64];
    int len = (w > 0 && w < (int)sizeof blank) ? w : (int)sizeof blank - 1;
    memset(blank, ' ', (size_t)len);
    blank[len] = '\0';
    tx(ANSI_FG_WHITE ANSI_BG_BLACK);
    for (int row = 0; row < h; row++) {
        screen_goto_map_row(row);
        tx("%s", blank);
    }
}
