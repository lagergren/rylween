#include "interact.h"
#include "party.h"
#include "primitives.h"
#include "res.h"
#include "script.h"
#include "state.h"

#include <stdarg.h>
#include <stdio.h>

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

void read_journal() {
    size_t n = pa_journal_n();
    if (n == 0) {
        return;
    }

    tx0(res_text("driv.journal.header"));

    unsigned char line = 0;
    for (size_t i = 0; i < n; i++) {
        tx("- %s\r\n", pa_journal_line(i));
        line++;
        if (line == 16) {
            line = 0;
            tx("\r\n");
            ryl_pause();
            cl();
        }
    }
    ryl_pause();
}

/* vsnprintf'd wrapper over party_journal_add. Accepts call-site formats
   that carry a trailing \n (the file-backed predecessor required it);
   that byte is stripped before the line reaches storage. */
void journalf(const char *fmt, ...) {
    char line[144];
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(line, sizeof line, fmt, ap);
    va_end(ap);
    if (n <= 0) {
        return;
    }
    if (n >= (int)sizeof line) {
        n = (int)sizeof line - 1;
    }
    if (line[n - 1] == '\n') {
        line[n - 1] = '\0';
    }
    party_journal_add(party_current(), line);
}
