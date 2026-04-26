#include "log.h"

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

#include "compat.h" /* getclk, ryl_state_path */

/* §24.A unified logging. Default sink: stderr (WARN/ERR/INFO/DEBUG)
   plus an append-to-file rylween.log for AUDIT. Threshold defaults to
   WARN; AUDIT always fires regardless. §24.B/C will widen the sink
   selector to file:/tcp:/null:/syslog: behind the same `RYL_LOG=` env
   var. The split between stderr and file mirrors the legacy split:
   `fprintf(stderr, …)` was for engineering noise, `ryl_log()` was for
   user-visible history. AUDIT preserves the latter byte-for-byte; the
   audit file format (month-name + day + HH:MM + message) matches
   pre-§24 ryl_log output so existing rylween.log readers still parse. */

static log_level threshold(void) {
    static int cached = -1;
    if (cached >= 0) {
        return (log_level)cached;
    }
    const char *env = getenv("RYL_LOG_LEVEL");
    if (env && *env) {
        if (!strcasecmp(env, "debug")) {
            cached = LOG_LVL_DEBUG;
        } else if (!strcasecmp(env, "info")) {
            cached = LOG_LVL_INFO;
        } else if (!strcasecmp(env, "warn")) {
            cached = LOG_LVL_WARN;
        } else if (!strcasecmp(env, "err") || !strcasecmp(env, "error")) {
            cached = LOG_LVL_ERR;
        } else {
            cached = LOG_LVL_WARN;
        }
    } else {
        cached = LOG_LVL_WARN;
    }
    return (log_level)cached;
}

static const char *lvl_tag(log_level lvl) {
    switch (lvl) {
        case LOG_LVL_DEBUG: return "DBG";
        case LOG_LVL_INFO:  return "INF";
        case LOG_LVL_WARN:  return "WRN";
        case LOG_LVL_ERR:   return "ERR";
        case LOG_LVL_AUDIT: return "AUD";
    }
    return "???";
}

static void emit_audit(const char *body) {
    /* Same format as the pre-§24 ryl_log() so existing
       data/rylween.log readers stay byte-compatible. getclk() honors
       RYL_FAKE_EPOCH; the audit file is reproducible under replay. */
    static const char *months[13] = {
        "",    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    };
    char kl[8];
    getclk(kl);
    int m = kl[2];
    if (m < 1 || m > 12) {
        m = 0;
    }
    char path[512];
    ryl_state_path("rylween.log", path, sizeof path);
    FILE *fh = fopen(path, "a+");
    if (!fh) {
        return;
    }
    fprintf(fh, "%s %2d %2d:%02d %s\n", months[m], (int)kl[3], (int)kl[4],
            (int)kl[5], body);
    fclose(fh);
}

void log_msg(log_level lvl, const char *module, const char *fmt, ...) {
    if (lvl != LOG_LVL_AUDIT && lvl < threshold()) {
        return;
    }
    if (!module) {
        module = "?";
    }
    char body[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(body, sizeof body, fmt, ap);
    va_end(ap);
    if (lvl == LOG_LVL_AUDIT) {
        emit_audit(body);
        return;
    }
    fprintf(stderr, "[%s][%s] %s\n", lvl_tag(lvl), module, body);
}
