#include "log.h"

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

#include "compat.h" /* getclk, ryl_state_path */

/* §24.A unified logging. WARN/ERR/INFO/DEBUG go to a sink selected by
   `RYL_LOG=`; AUDIT (LOG_A) always goes to data/rylween.log regardless
   (it's history, not diagnostic — its sink is fixed). Threshold defaults
   to WARN; `RYL_LOG_LEVEL=info|debug` widens it. AUDIT bypasses the
   threshold and always fires.

   Sinks landed in §24.B:
     unset / `RYL_LOG=stderr`        → stderr (the historical default)
     `RYL_LOG=null`                  → drop the line
     `RYL_LOG=file:<leaf-or-path>`   → append `[lvl][mod] body\n`. The
                                        path is resolved through
                                        ryl_state_path (so a bare leaf
                                        lands under $HOME/.rylween or
                                        data/, mirroring rylween.log).
                                        Open is lazy; on failure we emit
                                        a one-shot LOG_E to stderr and
                                        degrade to NULL for the rest of
                                        the run. Closed at exit.

   §24.C will add `tcp:<host>:<port>` behind the same dispatch. The audit
   file format (month-name + day + HH:MM + message) matches pre-§24
   ryl_log output so existing rylween.log readers still parse. */

typedef enum { SINK_STDERR = 0, SINK_NULL, SINK_FILE } sink_kind;

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

static FILE *sink_file = NULL;

static void sink_file_close(void) {
    if (sink_file) {
        fclose(sink_file);
        sink_file = NULL;
    }
}

/* Resolve and lazily open the file: sink. Returns the FILE* on success,
   NULL on failure (the caller degrades to SINK_NULL and we emit a
   one-shot diagnostic to stderr first). `leaf` is what followed
   `RYL_LOG=file:` — either a bare name like "session.log" or a full
   path. ryl_state_path turns either into the canonical state-dir path.
   atexit is wired here so the close happens even on normal exit; the
   file is opened in append mode so concurrent processes appending to
   the same path don't truncate each other. */
static FILE *open_file_sink(const char *leaf) {
    char path[512];
    ryl_state_path(leaf, path, sizeof path);
    FILE *fh = fopen(path, "a");
    if (!fh) {
        fprintf(stderr, "[ERR][log] RYL_LOG=file:%s: cannot open %s\n", leaf,
                path);
        return NULL;
    }
    if (atexit(sink_file_close) != 0) {
        fclose(fh);
        fprintf(stderr, "[ERR][log] atexit registration failed for RYL_LOG\n");
        return NULL;
    }
    sink_file = fh;
    return fh;
}

static sink_kind resolve_sink(void) {
    static int cached = -1; /* sink_kind | -1 */
    if (cached >= 0) {
        return (sink_kind)cached;
    }
    const char *env = getenv("RYL_LOG");
    if (!env || !*env || !strcasecmp(env, "stderr")) {
        cached = SINK_STDERR;
    } else if (!strcasecmp(env, "null")) {
        cached = SINK_NULL;
    } else if (!strncasecmp(env, "file:", 5) && env[5]) {
        if (open_file_sink(env + 5)) {
            cached = SINK_FILE;
        } else {
            cached = SINK_NULL; /* degrade silently after the one-shot warning */
        }
    } else {
        fprintf(stderr,
                "[ERR][log] unrecognized RYL_LOG=%s (expected stderr|null|"
                "file:<path>)\n",
                env);
        cached = SINK_STDERR;
    }
    return (sink_kind)cached;
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
    sink_kind sink = resolve_sink();
    if (sink == SINK_NULL) {
        return;
    }
    if (sink == SINK_FILE && sink_file) {
        fprintf(sink_file, "[%s][%s] %s\n", lvl_tag(lvl), module, body);
        fflush(sink_file);
        return;
    }
    fprintf(stderr, "[%s][%s] %s\n", lvl_tag(lvl), module, body);
}
