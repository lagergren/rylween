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

/* Wall-clock helpers. Replace the legacy Amiga getclk() + kl[N] array
   contract with named fields. clock_now() takes one localtime() snapshot
   into the struct so multi-field reads see consistent values; the
   single-field shorthands (clock_day, etc.) call localtime each time
   and are intended for sites that read just one field.

   Honors RYL_FAKE_EPOCH (set in fixture .env files) for deterministic
   replay. clock_year() returns the full 4-digit year (e.g. 2026); the
   legacy kl[1] year-1980 encoding is gone. */
struct wall_clock {
    int year;    /* full 4-digit (e.g. 2026) */
    int month;   /* 1..12 */
    int day;     /* 1..31 */
    int hour;    /* 0..23 */
    int minute;  /* 0..59 */
    int second;  /* 0..59 */
    int weekday; /* 0..6, Sunday=0 */
};
void clock_now(struct wall_clock *out);
int clock_year(void);
int clock_month(void);
int clock_day(void);
int clock_hour(void);
int clock_minute(void);

/* The old porting.h aliased stricmp -> strcmp, which broke case-insensitive
   menu handling. Route to strcasecmp instead. */
#define stricmp strcasecmp

/* Bounded append: dst must already be NUL-terminated within [0, dst_sz). */
void str_append(char *dst, size_t dst_sz, const char *src);

/* Path for runtime state file <leaf> under $HOME/.rylween/ (dir created 0700
   on first call). Falls back to "data/<leaf>" if HOME is unset or mkdir fails.
   Result is written to out (capped to out_sz); caller owns the buffer. */
void ryl_state_path(const char *leaf, char *out, size_t out_sz);

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
