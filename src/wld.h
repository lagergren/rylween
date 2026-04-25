#ifndef WLD_H__
#define WLD_H__

/* World-map helpers. Centralise the state.map[y][x] reads that are
   currently scattered across driv.c (wilderness movement, position
   prep) and subs.c (cave / encounter dispatch). The data dimensions
   (42 rows × 82 cols) are pinned by data/map.dat at load time;
   §20.14 S10 lifts those into world.json5 — when that lands, the
   wld_* layer is the one place those constants need to update. */

/* Tile byte at (x, y) in the loaded world map, or 0 for any
   coordinate outside the bounds. The tile-class meaning matches
   data/world.json5 terrain: keys (e.g. 's' plainland, '0' towne,
   'm' mountain). */
char wld_tile_at(int x, int y);

/* 1 if the party may step onto (x, y), 0 otherwise. The legacy
   passable check is "not 'm' (mountain) and not 'w' (water)";
   out-of-bounds coords return 0 (treat the off-map void as a
   wall). */
int wld_is_passable(int x, int y);

#endif
