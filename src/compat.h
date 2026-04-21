#ifndef COMPAT_H__
#define COMPAT_H__

#include <stddef.h>
#include <stdint.h>
#include <strings.h>
#include <unistd.h>

/* Amiga dos.library Delay() counts 50 ticks/sec, i.e. 20 ms per tick.
   Mapping to sleep(n) gave multi-minute pauses; use usleep instead. */
void Delay(uint32_t ticks);

/* Amiga getclk() populated an 8-byte array. Index contract (audited):
     kl[1] = year - 1980   (preserves "19%d" + kl[1]+80 idiom; Y2K cosmetic)
     kl[2] = month 1..12
     kl[3] = day of month 1..31
     kl[4] = hour 0..23
     kl[5] = minute 0..59
     kl[7] = second 0..59
   Other slots are zero-filled. */
void getclk(char *kl);

/* The old porting.h aliased stricmp -> strcmp, which broke case-insensitive
   menu handling. Route to strcasecmp instead. */
#define stricmp strcasecmp

/* Bounded append: dst must already be NUL-terminated within [0, dst_sz). */
void str_append(char *dst, size_t dst_sz, const char *src);

/* Path for runtime state file <leaf> under $HOME/.rylween/ (dir created 0700
   on first call). Falls back to "data/<leaf>" if HOME is unset or mkdir fails.
   Returns a pointer to a static buffer; each call overwrites the prior one. */
char *ryl_state_path(const char *leaf);

#endif
