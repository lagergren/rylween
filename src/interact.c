#include "ryl.h"
#include "interact.h"
#include "primitives.h"
#include "script.h"

/* §20 T1: terrain-transition narration. The previous switch + chance
   gate + clw moved into data/world/scripts/terrain.json5; this
   wrapper just hands the two tile bytes to the script as int
   bindings. Cast to unsigned char before printing so a signed-char
   platform doesn't widen high-bit bytes into negative ints. */
void check_terrain(char old, char new) {
    char old_s[8], new_s[8];
    snprintf(old_s, sizeof old_s, "%d", (int)(unsigned char)old);
    snprintf(new_s, sizeof new_s, "%d", (int)(unsigned char)new);
    play_script("terrain.transition", (ryl_bind[]){
                                          {"old", old_s},
                                          {"new", new_s},
                                          {0},
                                      });
}
