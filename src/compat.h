#ifndef COMPAT_H__
#define COMPAT_H__

#include <stddef.h>
#include <stdint.h>
#include <strings.h>
#include <time.h>
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

/* Entropy hooks for deterministic replay. When RYL_FAKE_EPOCH / RYL_FAKE_SEED
   are set in the environment, these return the fixed values instead of reading
   the wall clock / using the caller's seed. When unset (production), they are
   thin wrappers over time(3) / srand(3). Both envs parse as unsigned long.

   ryl_seed_now() is the common idiom — "seed from now, mixed with a caller
   salt (often a loop index)". Collapses the time()+srand() pair and hides the
   time_t -> unsigned int narrowing inside. */
time_t ryl_time(time_t *out);
void   ryl_srand(unsigned long seed);
void   ryl_seed_now(long salt);

#endif
