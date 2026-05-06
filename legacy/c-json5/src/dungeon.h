#ifndef DUNGEON_H__
#define DUNGEON_H__

#include <stdint.h>

/* Dungeon exploration loop, entered from cave / crypt tiles. */

/* §20 L2: scenario is a symbolic key into dungeons.scenarios in
   world.json5 (e.g. "fire_shield_cave", "thun_temple"); NULL means
   random cave (no quest placements). */
void dungeon(int32_t levels, const char *scenario);

#endif
