#include "compat.h"
#include "ryl.h"
#include "runtime_cfg.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

int pm_class_id(const char *name) {
    if (!name) {
        return -1;
    }
    if (strcmp(name, "fighter") == 0) {
        return PC_FIGHTER;
    }
    if (strcmp(name, "thief") == 0) {
        return PC_THIEF;
    }
    if (strcmp(name, "mage") == 0 || strcmp(name, "wizard") == 0) {
        return PC_MAGE;
    }
    if (strcmp(name, "priest") == 0) {
        return PC_PRIEST;
    }
    if (strcmp(name, "bard") == 0) {
        return PC_BARD;
    }
    if (strcmp(name, "ranger") == 0) {
        return PC_RANGER;
    }
    return -1;
}

void ryl_delay(unsigned ms) {
    static int resolved = 0;
    static int suppressed = 0;
    if (!resolved) {
        resolved = 1;
        const char *e = rcfg_get("RYL_NO_DELAY");
        suppressed = (e && *e && *e != '0');
    }
    if (suppressed) {
        return;
    }
    useconds_t us = (useconds_t)ms * 1000U;
    while (us > 0) {
        useconds_t chunk = us > 900000U ? 900000U : us;
        if (usleep(chunk) == 0) {
            us -= chunk;
            continue;
        }
        if (errno == EINTR) {
            continue;
        }
        break;
    }
}

static time_t ryl_time(time_t *out) {
    static int resolved = 0;
    static int fixed = 0;
    static time_t fixed_val = 0;

    if (!resolved) {
        resolved = 1;
        const char *e = rcfg_get("RYL_FAKE_EPOCH");
        if (e && *e) {
            char *end;
            unsigned long v = strtoul(e, &end, 10);
            if (end != e) {
                fixed = 1;
                fixed_val = (time_t)v;
            }
        }
    }

    time_t now = fixed ? fixed_val : time(NULL);
    if (out) {
        *out = now;
    }
    return now;
}

/* xorshift64* — portable, fast, fixed across libc versions so byte-identity
   replays are valid on macOS, glibc, and musl alike. */
static uint64_t rng_state = 1;

void ryl_random_init(void) {
    const char *e = rcfg_get("RYL_FAKE_SEED");
    if (e && *e) {
        char *end;
        unsigned long v = strtoul(e, &end, 10);
        if (end != e) {
            rng_state = (uint64_t)v;
            if (rng_state == 0) {
                rng_state = 1;
            }
            return;
        }
    }
    rng_state = (uint64_t)ryl_time(NULL) ^ ((uint64_t)getpid() << 16);
    if (rng_state == 0) {
        rng_state = 1;
    }
}

static uint64_t rng_next(void) {
    uint64_t x = rng_state;
    x ^= x >> 12;
    x ^= x << 25;
    x ^= x >> 27;
    rng_state = x;
    return x * 0x2545F4914F6CDD1DULL;
}

int ryl_random(int lo, int hi) {
    assert(rng_state != 0 && "ryl_random_init() not called");
    assert(lo <= hi);
    uint64_t span = (uint64_t)(hi - lo) + 1U;
    return lo + (int)(rng_next() % span);
}

int ryl_chance(int pct) {
    if (pct <= 0) {
        return 0;
    }
    if (pct >= 100) {
        return 1;
    }
    return ryl_random(1, 100) <= pct;
}

void clock_now(struct wall_clock *out) {
    *out = (struct wall_clock){0};
    time_t now = ryl_time(NULL);
    struct tm *tm = localtime(&now);
    if (!tm) {
        return;
    }
    out->year = tm->tm_year + 1900;
    out->month = tm->tm_mon + 1;
    out->day = tm->tm_mday;
    out->hour = tm->tm_hour;
    out->minute = tm->tm_min;
    out->second = tm->tm_sec;
    out->weekday = tm->tm_wday;
}

int clock_year(void) {
    struct wall_clock c;
    clock_now(&c);
    return c.year;
}
int clock_month(void) {
    struct wall_clock c;
    clock_now(&c);
    return c.month;
}
int clock_day(void) {
    struct wall_clock c;
    clock_now(&c);
    return c.day;
}
int clock_hour(void) {
    struct wall_clock c;
    clock_now(&c);
    return c.hour;
}
int clock_minute(void) {
    struct wall_clock c;
    clock_now(&c);
    return c.minute;
}

void str_append(char *dst, size_t dst_sz, const char *src) {
    size_t n = strlen(dst);
    if (n + 1 < dst_sz) {
        snprintf(dst + n, dst_sz - n, "%s", src);
    }
}

/* ryl_state_path: build the on-disk path for a persistent-state leaf
   file into the caller's buffer. The $HOME/.rylween root is resolved
   (and mkdir'd) on first call and cached in a static, so subsequent
   calls just format into out. The cache is read-only after init, so
   there is no returned-static-buffer footgun. */
void ryl_state_path(const char *leaf, char *out, size_t out_sz) {
    static char base[480];
    static int8_t state = -1; /* -1 uninit, 0 fallback to data/, 1 home-dir ready */

    if (out_sz == 0) {
        return;
    }

    if (state < 0) {
        const char *home = getenv("HOME");
        state = 0;
        if (home && *home) {
            int n = snprintf(base, sizeof base, "%s/.rylween", home);
            if (n > 0 && (size_t)n < sizeof base) {
                if (mkdir(base, 0700) == 0 || errno == EEXIST) {
                    state = 1;
                }
            }
        }
    }

    if (state == 1) {
        snprintf(out, out_sz, "%s/%s", base, leaf);
    } else {
        snprintf(out, out_sz, "data/%s", leaf);
    }
}
