#ifndef COMPAT_H__
#define COMPAT_H__

#include <stddef.h>
#include <stdint.h>
#include <strings.h>
#include <time.h>
#include <unistd.h>

/* Pause for `ms` milliseconds. Replaces the Amiga dos.library Delay() idiom
   (50 ticks/sec, i.e. 20 ms per tick); migrate Delay(N) -> ryl_delay(N*20). */
void ryl_delay(unsigned ms);

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

/* Wall-clock entropy hook. Returns RYL_FAKE_EPOCH when set in the environment;
   otherwise wraps time(3). Used for date stamps and the boot-time PRNG seed. */
time_t ryl_time(time_t *out);

/* Single-source PRNG. ryl_random_init() runs ONCE from main() before any
   draw. Seed is RYL_FAKE_SEED when set, else time^pid. There is no public
   reseed: every in-game roll draws from the same xorshift64* stream so the
   game state is reproducible end-to-end from the boot seed alone. */
void ryl_random_init(void);

/* Uniform draw in [lo, hi] inclusive. lo <= hi required. */
int ryl_random(int lo, int hi);

/* True with probability pct/100. pct outside [0,100] saturates. */
int ryl_chance(int pct);

#endif
