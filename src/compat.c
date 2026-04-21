#include "compat.h"

#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

void Delay(uint32_t ticks) {
    useconds_t us = (useconds_t) (ticks * 20000UL);
    while (us > 0) {
        useconds_t chunk = us > 900000U ? 900000U : us;
        if (usleep(chunk) == 0) {
            us -= chunk;
            continue;
        }
        if (errno == EINTR)
            continue;
        break;
    }
}

void getclk(char *kl) {
    time_t now = time(NULL);
    struct tm *tm = localtime(&now);

    memset(kl, 0, 8);
    if (!tm)
        return;

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
    if (n + 1 < dst_sz)
        snprintf(dst + n, dst_sz - n, "%s", src);
}
