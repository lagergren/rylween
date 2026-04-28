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

int wld_rows(void) {
    return WLD_ROWS;
}

int wld_cols(void) {
    return WLD_COLS;
}

char wld_tile_at(int x, int y) {
    if (!in_bounds(x, y)) {
        return 0;
    }
    return state.map[y][x];
}

void wld_set_tile_at(int x, int y, char c) {
    if (!in_bounds(x, y)) {
        return;
    }
    state.map[y][x] = c;
}

int wld_is_passable(int x, int y) {
    if (!in_bounds(x, y)) {
        return 0;
    }
    char c = state.map[y][x];
    return c != 'm' && c != 'w';
}

void wld_load_row(int y, FILE *fh) {
    if (y < 0 || y >= WLD_ROWS) {
        return;
    }
    fgets((char *)state.map[y], sizeof state.map[y], fh);
}

static int location_in_range(int slot) {
    return slot >= 0 && slot < WLD_LOCATION_SLOT_MAX;
}

int32_t wld_location_x(int slot) {
    return location_in_range(slot) ? state.dx[slot] : 0;
}

int32_t wld_location_y(int slot) {
    return location_in_range(slot) ? state.dy[slot] : 0;
}

void wld_location_set(int slot, int32_t x, int32_t y) {
    if (!location_in_range(slot)) {
        return;
    }
    state.dx[slot] = x;
    state.dy[slot] = y;
}

int32_t *wld_locations_dx(void) {
    return state.dx;
}

int32_t *wld_locations_dy(void) {
    return state.dy;
}

char wld_last_terrain(void) {
    return state.last_terrain;
}

void wld_set_last_terrain(char tile) {
    state.last_terrain = tile;
}
