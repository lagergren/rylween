#include "wld.h"

#include "res.h"
#include "state.h"

#include <string.h>

/* Map-array dimensions are sourced from world.json5 (§20.14 S10) via
   res_map_rows_max() / res_map_cols_max(); state.map is a flat 1D
   buffer of rows × cols bytes (tile (x, y) at offset y * cols + x).
   MAP_ROWS_HARD_MAX / MAP_COLS_HARD_MAX (state.h) are the defensive
   ceilings the data-layer clamps against. */

static int in_bounds(int x, int y) {
    return x >= 0 && x < res_map_cols_max() && y >= 0 &&
           y < res_map_rows_max();
}

int wld_rows(void) {
    return res_map_rows_max();
}

int wld_cols(void) {
    return res_map_cols_max();
}

char wld_tile_at(int x, int y) {
    if (!in_bounds(x, y)) {
        return 0;
    }
    return state.map[y * res_map_cols_max() + x];
}

void wld_set_tile_at(int x, int y, char c) {
    if (!in_bounds(x, y)) {
        return;
    }
    state.map[y * res_map_cols_max() + x] = c;
}

static int tile_class_is(char c, const char *want) {
    const char *cls = res_terrain_class(c);
    return cls && strcmp(cls, want) == 0;
}

int wld_tile_is_hut(char c) {
    return tile_class_is(c, "hut");
}

int wld_tile_is_town(char c) {
    return tile_class_is(c, "town");
}

int wld_tile_is_castle(char c) {
    return tile_class_is(c, "castle");
}

int wld_tile_is_settlement(char c) {
    return wld_tile_is_hut(c) || wld_tile_is_town(c) || wld_tile_is_castle(c);
}

int wld_tile_is_mountain(char c) {
    return tile_class_is(c, "mountain");
}

int wld_tile_is_water(char c) {
    return tile_class_is(c, "water");
}

int wld_tile_is_impassable(char c) {
    return wld_tile_is_mountain(c) || wld_tile_is_water(c);
}

int wld_is_passable(int x, int y) {
    if (!in_bounds(x, y)) {
        return 0;
    }
    return !wld_tile_is_impassable(state.map[y * res_map_cols_max() + x]);
}

void wld_load_row(int y, FILE *fh) {
    if (y < 0 || y >= res_map_rows_max()) {
        return;
    }
    int cols = res_map_cols_max();
    fgets(&state.map[y * cols], cols, fh);
}

static int location_in_range(int slot) {
    return slot >= 0 && slot < res_location_slot_max();
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

dir_t wld_dir_from_key(int ch) {
    switch (ch) {
        case '8':
            return DIR_NORTH;
        case '2':
            return DIR_SOUTH;
        case '6':
            return DIR_EAST;
        case '4':
            return DIR_WEST;
        default:
            return DIR_NONE;
    }
}

int wld_dir_dx(dir_t d) {
    switch (d) {
        case DIR_EAST:
            return 1;
        case DIR_WEST:
            return -1;
        default:
            return 0;
    }
}

int wld_dir_dy(dir_t d) {
    switch (d) {
        case DIR_SOUTH:
            return 1;
        case DIR_NORTH:
            return -1;
        default:
            return 0;
    }
}
