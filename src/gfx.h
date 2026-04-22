#ifndef GFX_H__
#define GFX_H__

#include <stdint.h>

/* Blit the fixed scene art (inn/tavern/etc.) and clear the map window. */

void blitgfx(uint8_t);
void clr_map_window(void);

#endif
