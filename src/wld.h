#ifndef WLD_H__
#define WLD_H__

#include <stdint.h>
#include <stdio.h>

/* World-map helpers. Centralise the state.map[y][x] reads that are
   currently scattered across driv.c (wilderness movement, position
   prep) and subs.c (cave / encounter dispatch). The data dimensions
   (42 rows × 82 cols) are pinned by data/map.dat at load time;
   §20.14 S10 lifts those into world.json5 — when that lands, the
   wld_* layer is the one place those constants need to update. */

/* Map dimensions. Today fixed at 42×82 by data/map.dat layout; future
   §20.14 S10 work makes them data-driven. wld_rows() / wld_cols() are
   the consumer-facing names used by snapshot emit / save loops; the
   in_bounds check in this module agrees with the same bounds. */
int wld_rows(void);
int wld_cols(void);

/* Tile byte at (x, y) in the loaded world map, or 0 for any
   coordinate outside the bounds. The tile-class meaning matches
   data/world.json5 terrain: keys (e.g. 's' plainland, '0' towne,
   'm' mountain). */
char wld_tile_at(int x, int y);

/* Tile write paired with wld_tile_at. Out-of-bounds coordinates are a
   no-op so callers don't have to bounds-check before stamping. */
void wld_set_tile_at(int x, int y, char c);

/* Tile-class predicates. Sourced from data/world.json5 `terrain.<c>.class`
   via res_terrain_class. Settlements ('town' / 'hut' / 'castle') are
   the tiles the towne.c E-key flow dispatches on; impassable tiles
   ('mountain' / 'water') block movement (wld_is_passable inverts the
   union). Tiles without a `class:` field return 0 from every
   predicate — they're plain open terrain. */
int wld_tile_is_hut(char c);
int wld_tile_is_town(char c);
int wld_tile_is_castle(char c);
int wld_tile_is_settlement(char c);
int wld_tile_is_mountain(char c);
int wld_tile_is_water(char c);
int wld_tile_is_impassable(char c);

/* 1 if the party may step onto (x, y), 0 otherwise. Equivalent to
   `!wld_tile_is_impassable(wld_tile_at(x, y))`. Out-of-bounds coords
   return 0 (treat the off-map void as a wall). */
int wld_is_passable(int x, int y);

/* Read one row of map terrain from `fh` via fgets into row `y` (0-based,
   < wld_rows()). Encapsulates the cold-start map.dat loader; out-of-
   range y is a no-op. */
void wld_load_row(int y, FILE *fh);

/* Random-location placements (state.dx[]/dy[]). WLD_LOCATION_SLOT_MAX
   slots, written by driv.c::position_locations on cold-start and read
   by mission triggers / speak bindings to identify "the dungeon a
   quest sends the party to". Slot indices are scenario-specific (see
   missions.c usage); helpers do an internal 0..MAX-1 bounds check and
   treat out-of-range slots as the (0,0) wilderness sentinel.

   WLD_LOCATION_SLOT_MAX is the array size (zero-init + save/load
   bound). WLD_RANDOM_LOCATION_MAX is the upper bound (inclusive) of
   the slots that get randomized at cold-start — slots 1..MAX get a
   random (x,y) inside `map.location_bounds`; slot 0 stays the (0,0)
   wilderness sentinel and slots above the random max stay at (0,0)
   too (reserved for static / unused placements). */
enum {
    WLD_LOCATION_SLOT_MAX = 15,
    WLD_RANDOM_LOCATION_MAX = 9,
};

int32_t wld_location_x(int slot);
int32_t wld_location_y(int slot);
void wld_location_set(int slot, int32_t x, int32_t y);

/* Direct array handles for save/load loops only. The size is
   WLD_LOCATION_SLOT_MAX (zero-init at startup). Game-code reads /
   writes go through wld_location_x / wld_location_y / wld_location_set;
   these pointers are only for the driv.c serializer to hand to
   wcw_i32_arr / rcw_i32_arr. */
int32_t *wld_locations_dx(void);
int32_t *wld_locations_dy(void);

/* Records the tile the party just stepped off — read by subs.c's
   random_wilderness terrain-transition check (`check_terrain(
   wld_last_terrain(), wld_tile_at(...))`). state.last_terrain is a
   single global, treated as wld bookkeeping rather than party
   state. */
char wld_last_terrain(void);
void wld_set_last_terrain(char tile);

/* Cardinal directions on the world grid. Single source of truth for
   "what direction did the player ask to move": both the driv.c main
   loop and the script.c priest-teleport binding parse the same numpad
   keys ('8'/'4'/'6'/'2' = N/W/E/S, the legacy keymap io_tty.c also
   maps arrow keys onto). DIR_NONE means "not a movement key". */
typedef enum {
    DIR_NONE = 0,
    DIR_NORTH,
    DIR_SOUTH,
    DIR_EAST,
    DIR_WEST,
} dir_t;

dir_t wld_dir_from_key(int ch);
int wld_dir_dx(dir_t d);
int wld_dir_dy(dir_t d);

#endif
