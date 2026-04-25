#include "wld.h"

#include "state.h"

/* Map-array dimensions match struct ryl_state.map[42][82] in
   state.h. Hardcoded here because state.h does not export macros
   today; §20.14 S10 makes these data-driven, at which point both
   reads route through the same world.json5 lookup. */
enum {
    WLD_ROWS = 42,
    WLD_COLS = 82,
};

static int in_bounds(int x, int y) {
    return x >= 0 && x < WLD_COLS && y >= 0 && y < WLD_ROWS;
}

char wld_tile_at(int x, int y) {
    if (!in_bounds(x, y)) {
        return 0;
    }
    return state.map[y][x];
}

int wld_is_passable(int x, int y) {
    if (!in_bounds(x, y)) {
        return 0;
    }
    char c = state.map[y][x];
    return c != 'm' && c != 'w';
}
