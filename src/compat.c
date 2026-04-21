#include "compat.h"
#include "ryl.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

int pm_class_id(const char *name) {
    if (!name) return -1;
    if (strcmp(name, "fighter") == 0) return PC_FIGHTER;
    if (strcmp(name, "thief")   == 0) return PC_THIEF;
    if (strcmp(name, "mage")    == 0 || strcmp(name, "wizard") == 0) return PC_MAGE;
    if (strcmp(name, "priest")  == 0) return PC_PRIEST;
    if (strcmp(name, "bard")    == 0) return PC_BARD;
    if (strcmp(name, "ranger")  == 0) return PC_RANGER;
    return -1;
}

void Delay(uint32_t ticks) {
    useconds_t us = (useconds_t) (ticks * 20000UL);
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

time_t ryl_time(time_t *out) {
    static int   resolved = 0;
    static int   fixed    = 0;
    static time_t fixed_val = 0;

    if (!resolved) {
        resolved = 1;
        const char *e = getenv("RYL_FAKE_EPOCH");
        if (e && *e) {
            char *end;
            unsigned long v = strtoul(e, &end, 10);
            if (end != e) {
                fixed     = 1;
                fixed_val = (time_t) v;
            }
        }
    }

    time_t now = fixed ? fixed_val : time(NULL);
    if (out) *out = now;
    return now;
}

void ryl_srand(unsigned long seed) {
    static int          resolved = 0;
    static int          fixed    = 0;
    static unsigned int fixed_seed = 0;

    if (!resolved) {
        resolved = 1;
        const char *e = getenv("RYL_FAKE_SEED");
        if (e && *e) {
            char *end;
            unsigned long v = strtoul(e, &end, 10);
            if (end != e) {
                fixed      = 1;
                fixed_seed = (unsigned int) v;
            }
        }
    }

    srand(fixed ? fixed_seed : (unsigned int) seed);
}

void ryl_seed_now(long salt) {
    time_t t = ryl_time(NULL);
    ryl_srand((unsigned long) (t + salt));
}

void getclk(char *kl) {
    time_t now = ryl_time(NULL);
    struct tm *tm = localtime(&now);

    memset(kl, 0, 8);
    if (!tm) {
        return;
    }

    kl[1] = (char) (tm->tm_year - 80);
    kl[2] = (char) (tm->tm_mon + 1);
    kl[3] = (char) tm->tm_mday;
    kl[4] = (char) tm->tm_hour;
    kl[5] = (char) tm->tm_min;
    kl[6] = (char) tm->tm_wday;
    kl[7] = (char) tm->tm_sec;
}

void str_append(char *dst, size_t dst_sz, const char *src) {
    size_t n = strlen(dst);
    if (n + 1 < dst_sz) {
        snprintf(dst + n, dst_sz - n, "%s", src);
    }
}

char *ryl_state_path(const char *leaf) {
    static char buf[512];
    static char base[480];
    static int8_t state = -1; /* -1 uninit, 0 fallback to data/, 1 home-dir ready */

    if (state < 0) {
        const char *home = getenv("HOME");
        state = 0;
        if (home && *home) {
            int n = snprintf(base, sizeof base, "%s/.rylween", home);
            if (n > 0 && (size_t) n < sizeof base) {
                if (mkdir(base, 0700) == 0 || errno == EEXIST) {
                    state = 1;
                }
            }
        }
    }

    if (state == 1) {
        snprintf(buf, sizeof buf, "%s/%s", base, leaf);
    } else {
        snprintf(buf, sizeof buf, "data/%s", leaf);
    }
    return buf;
}
