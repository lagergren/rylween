#ifndef LOG_H__
#define LOG_H__

#include <stdarg.h>

/* §24.A unified logging API. Replaces scattered `fprintf(stderr, …)`
   call-sites and the legacy `ryl_log()` audit-trail helper.

   Severity levels:
     LOG_LVL_DEBUG   trace-level, off by default
     LOG_LVL_INFO    descriptive ("realizing 23 NPCs across 5 towns")
     LOG_LVL_WARN    expected-rare diagnostics (bad config, recoverable)
     LOG_LVL_ERR     unrecoverable but non-panic (caller still returns)
     LOG_LVL_AUDIT   user-visible history ("party entered the realm")

   Default threshold is WARN — INFO/DEBUG are silent unless
   `RYL_LOG_LEVEL=info|debug` is set in the env. AUDIT bypasses the
   threshold (it's history, not diagnostic) and always fires.

   `RYL_LOG` is a comma-separated list of sink directives — every
   WARN/ERR/INFO/DEBUG line is teed to every enabled sink. AUDIT
   always writes data/rylween.log regardless of `RYL_LOG`.
     unset / `RYL_LOG=stderr`     stderr (the historical default)
     `RYL_LOG=null`               drop the line (alone in the list)
     `RYL_LOG=file:<path>`        append to <path>, resolved through
                                  ryl_state_path
     `RYL_LOG=tcp:[host:]port`    bind a TCP listener; every connected
                                  client (`telnet host port`) gets a
                                  live tail. Default bind is loopback;
                                  pass `tcp:0.0.0.0:<port>` to expose
                                  on every interface. Writes are
                                  non-blocking — slow readers drop
                                  lines rather than stalling the
                                  engine. See docs/log.md.
     `RYL_LOG=syslog[:<ident>]`   route through syslog(3) with a
                                  level → priority mapping (default
                                  ident "rylween", facility LOG_USER).
     `RYL_LOG=stderr,file:foo,tcp:9999`
                                  tees to all three at once.

   `module` is a short ident ("res", "party", "battle") so a log line
   stays self-locating without `__FILE__` noise. Pick the
   component owning the message; for cross-cutting helpers, name the
   feature ("snapshot", "world"). */

typedef enum {
    LOG_LVL_DEBUG = 0,
    LOG_LVL_INFO,
    LOG_LVL_WARN,
    LOG_LVL_ERR,
    LOG_LVL_AUDIT,
} log_level;

void log_msg(log_level lvl, const char *module, const char *fmt, ...)
    __attribute__((format(printf, 3, 4)));

#define LOG_D(mod, ...) log_msg(LOG_LVL_DEBUG, mod, __VA_ARGS__)
#define LOG_I(mod, ...) log_msg(LOG_LVL_INFO, mod, __VA_ARGS__)
#define LOG_W(mod, ...) log_msg(LOG_LVL_WARN, mod, __VA_ARGS__)
#define LOG_E(mod, ...) log_msg(LOG_LVL_ERR, mod, __VA_ARGS__)
#define LOG_A(mod, ...) log_msg(LOG_LVL_AUDIT, mod, __VA_ARGS__)

#endif
