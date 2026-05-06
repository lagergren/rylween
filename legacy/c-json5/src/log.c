#include "log.h"

#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/socket.h>
#include <syslog.h>
#include <unistd.h>

#include "compat.h" /* clock_now, ryl_state_path */
#include "runtime_cfg.h"

#ifndef MSG_NOSIGNAL
#define MSG_NOSIGNAL 0
#endif

/* §24.A unified logging. WARN/ERR/INFO/DEBUG fan out to a *set* of
   sinks selected by `RYL_LOG=`; AUDIT (LOG_A) always goes to
   data/rylween.log regardless (it's history, not diagnostic — its sink
   is fixed). Threshold defaults to WARN; `RYL_LOG_LEVEL=info|debug`
   widens it. AUDIT bypasses the threshold and always fires.

   `RYL_LOG` is a comma-separated list of sink directives — every line
   is teed to every enabled sink. Recognized tokens:
     stderr                    → stderr (the historical default)
     null                      → no-op (emits nothing on its own; if it
                                  is the *only* token in the list, the
                                  effective mask is empty and lines drop)
     file:<leaf-or-path>       → append `[lvl][mod] body\n`. The path is
                                  resolved through ryl_state_path (so a
                                  bare leaf lands under $HOME/.rylween
                                  or data/, mirroring rylween.log).
                                  At most one file: sink per process —
                                  a second one emits a one-shot warning
                                  and is ignored.
     tcp:[host:]port           → bind a TCP listen socket and broadcast
                                  every emitted line to every connected
                                  client (rcon-style — `telnet host
                                  port` to tail). Accepted host forms:
                                    tcp:host:port
                                    tcp::port    (host = 127.0.0.1)
                                    tcp:port     (host = 127.0.0.1)
                                  Default bind is loopback; pass
                                  `tcp:0.0.0.0:<port>` explicitly to
                                  expose on every interface. Writes are
                                  non-blocking; a stuck reader gets its
                                  line dropped (EAGAIN) or its fd closed
                                  (EPIPE/short write) so the engine
                                  never stalls. At most one tcp: sink
                                  per process. See docs/log.md.
     syslog[:<ident>]          → openlog(ident, LOG_PID, LOG_USER) and
                                  route each line through syslog(3)
                                  with a level → priority mapping
                                  (DEBUG/INFO/WARN/ERR →
                                  LOG_DEBUG/INFO/WARNING/ERR). Default
                                  ident is "rylween". One syslog sink
                                  per process. The system facility's
                                  filtering / forwarding rules apply;
                                  on macOS see /var/log/system.log,
                                  on Linux journald or rsyslog.

   Examples:
     RYL_LOG=stderr,file:dev.log              tee to stderr + a file
     RYL_LOG=tcp:9999,file:run.log            telnet + a file
     RYL_LOG=null                             silent (drop everything)
     RYL_LOG  unset / empty                   stderr (compat default)

   Open errors emit a one-shot `[ERR][log] …` to stderr and degrade
   that token to a no-op; the rest of the list still applies. The
   audit file format (month-name + day + HH:MM + message) matches
   pre-§24 ryl_log output so existing rylween.log readers still
   parse. */

#define SINK_BIT_STDERR (1u << 0)
#define SINK_BIT_FILE   (1u << 1)
#define SINK_BIT_TCP    (1u << 2)
#define SINK_BIT_SYSLOG (1u << 3)

/* §24.C TCP fan-out. N=8 is plenty for the rcon use-case (one or two
   operators tailing a running engine). New connections are accepted
   lazily on each emit before the broadcast write; closed clients are
   pruned in place. Slot-table-full new accepts are closed immediately
   so a runaway connect loop cannot exhaust fds. */
#define TCP_MAX_CLIENTS 8
static int sink_tcp_listen_fd = -1;
static int sink_tcp_clients[TCP_MAX_CLIENTS];

static log_level threshold(void) {
    static int cached = -1;
    if (cached >= 0) {
        return (log_level)cached;
    }
    const char *env = rcfg_get("RYL_LOG_LEVEL");
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
        case LOG_LVL_DEBUG:
            return "DBG";
        case LOG_LVL_INFO:
            return "INF";
        case LOG_LVL_WARN:
            return "WRN";
        case LOG_LVL_ERR:
            return "ERR";
        case LOG_LVL_AUDIT:
            return "AUD";
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

/* §24.D syslog sink. The ident buffer is process-lifetime so closelog
   doesn't read freed memory if openlog stashed the pointer. macOS's
   syslog(3) does retain the pointer across calls; Linux glibc copies
   it, but assuming retention is the safe portable contract. */
static char syslog_ident[64];

static void sink_syslog_close(void) {
    closelog();
}

static int open_syslog_sink(const char *ident) {
    if (!ident || !*ident) {
        ident = "rylween";
    }
    strncpy(syslog_ident, ident, sizeof syslog_ident - 1);
    syslog_ident[sizeof syslog_ident - 1] = '\0';
    openlog(syslog_ident, LOG_PID, LOG_USER);
    if (atexit(sink_syslog_close) != 0) {
        fprintf(stderr,
                "[ERR][log] atexit registration failed for RYL_LOG=syslog\n");
        closelog();
        return -1;
    }
    return 0;
}

static int syslog_priority(log_level lvl) {
    switch (lvl) {
        case LOG_LVL_DEBUG:
            return LOG_DEBUG;
        case LOG_LVL_INFO:
            return LOG_INFO;
        case LOG_LVL_WARN:
            return LOG_WARNING;
        case LOG_LVL_ERR:
            return LOG_ERR;
        case LOG_LVL_AUDIT:
            return LOG_NOTICE; /* unreachable — AUDIT bypasses */
    }
    return LOG_INFO;
}

static void sink_tcp_close(void) {
    for (int i = 0; i < TCP_MAX_CLIENTS; ++i) {
        if (sink_tcp_clients[i] >= 0) {
            close(sink_tcp_clients[i]);
            sink_tcp_clients[i] = -1;
        }
    }
    if (sink_tcp_listen_fd >= 0) {
        close(sink_tcp_listen_fd);
        sink_tcp_listen_fd = -1;
    }
}

static int set_nonblock(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) {
        return -1;
    }
    return fcntl(fd, F_SETFL, flags | O_NONBLOCK);
}

/* Parse what followed `tcp:` into host + port. Accepts:
     "host:port" — split on the last colon; first half is host
     ":port"    — empty host → 127.0.0.1
     "port"     — bare port  → 127.0.0.1
   Returns 0 ok, -1 on any malformed input (non-numeric port, port out
   of [1,65535], host buffer overrun). The caller emits the one-shot
   diagnostic. IPv6 literals would require bracket parsing; not in
   scope for the rcon use-case. */
static int parse_tcp_spec(const char *spec, char *host, size_t host_sz,
                          int *port_out) {
    static const char loopback[] = "127.0.0.1";
    const char *colon = strrchr(spec, ':');
    const char *port_s;
    if (colon) {
        size_t hlen = (size_t)(colon - spec);
        if (hlen == 0) {
            if (sizeof loopback > host_sz) {
                return -1;
            }
            memcpy(host, loopback, sizeof loopback);
        } else {
            if (hlen + 1 > host_sz) {
                return -1;
            }
            memcpy(host, spec, hlen);
            host[hlen] = '\0';
        }
        port_s = colon + 1;
    } else {
        if (sizeof loopback > host_sz) {
            return -1;
        }
        memcpy(host, loopback, sizeof loopback);
        port_s = spec;
    }
    if (!*port_s) {
        return -1;
    }
    int port = 0;
    for (const char *q = port_s; *q; ++q) {
        if (*q < '0' || *q > '9') {
            return -1;
        }
        port = port * 10 + (*q - '0');
        if (port > 65535) {
            return -1;
        }
    }
    if (port < 1) {
        return -1;
    }
    *port_out = port;
    return 0;
}

/* Lazy bind of the TCP listener. SO_REUSEADDR so a quick restart after
   ^C doesn't trip TIME_WAIT; SO_NOSIGPIPE per accepted socket on BSD/
   macOS plus MSG_NOSIGNAL on Linux keeps a torn-down peer from killing
   the engine without a process-wide SIGPIPE override. Returns 0 ok;
   on any failure emits a one-shot `[ERR][log]` to stderr and the
   caller degrades to SINK_NULL. */
static int open_tcp_sink(const char *spec) {
    char host[64];
    int port;
    if (parse_tcp_spec(spec, host, sizeof host, &port) != 0) {
        fprintf(stderr,
                "[ERR][log] RYL_LOG=tcp:%s: cannot parse host:port "
                "(expected tcp:[host:]port)\n",
                spec);
        return -1;
    }
    struct in_addr addr;
    if (inet_pton(AF_INET, host, &addr) != 1) {
        fprintf(stderr, "[ERR][log] RYL_LOG=tcp:%s: bad host '%s'\n", spec,
                host);
        return -1;
    }
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        fprintf(stderr, "[ERR][log] RYL_LOG=tcp:%s: socket: %s\n", spec,
                strerror(errno));
        return -1;
    }
    int one = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof one);
    struct sockaddr_in sa;
    memset(&sa, 0, sizeof sa);
    sa.sin_family = AF_INET;
    sa.sin_addr = addr;
    sa.sin_port = htons((uint16_t)port);
    if (bind(fd, (struct sockaddr *)&sa, sizeof sa) != 0) {
        fprintf(stderr, "[ERR][log] RYL_LOG=tcp:%s: bind %s:%d: %s\n", spec,
                host, port, strerror(errno));
        close(fd);
        return -1;
    }
    if (listen(fd, TCP_MAX_CLIENTS) != 0) {
        fprintf(stderr, "[ERR][log] RYL_LOG=tcp:%s: listen: %s\n", spec,
                strerror(errno));
        close(fd);
        return -1;
    }
    if (set_nonblock(fd) != 0) {
        fprintf(stderr, "[ERR][log] RYL_LOG=tcp:%s: O_NONBLOCK: %s\n", spec,
                strerror(errno));
        close(fd);
        return -1;
    }
    if (atexit(sink_tcp_close) != 0) {
        fprintf(stderr, "[ERR][log] atexit registration failed for RYL_LOG\n");
        close(fd);
        return -1;
    }
    for (int i = 0; i < TCP_MAX_CLIENTS; ++i) {
        sink_tcp_clients[i] = -1;
    }
    sink_tcp_listen_fd = fd;
    return 0;
}

/* Drain pending accepts before each broadcast — log_msg is the only
   pump in this design (no separate event loop), so connections show up
   exactly when something gets emitted. Backlog past TCP_MAX_CLIENTS
   gets the new fd closed immediately rather than queueing forever. */
static void sink_tcp_accept_pending(void) {
    if (sink_tcp_listen_fd < 0) {
        return;
    }
    for (;;) {
        int fd = accept(sink_tcp_listen_fd, NULL, NULL);
        if (fd < 0) {
            return; /* EAGAIN — nothing more pending */
        }
        if (set_nonblock(fd) != 0) {
            close(fd);
            continue;
        }
#ifdef SO_NOSIGPIPE
        int one = 1;
        setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &one, sizeof one);
#endif
        int placed = 0;
        for (int i = 0; i < TCP_MAX_CLIENTS; ++i) {
            if (sink_tcp_clients[i] < 0) {
                sink_tcp_clients[i] = fd;
                placed = 1;
                break;
            }
        }
        if (!placed) {
            close(fd);
        }
    }
}

/* Fan a fully-formed line out to every connected client. Loss policy:
   EAGAIN/EWOULDBLOCK means the kernel's per-client buffer is full —
   drop this line for that client only. EPIPE / other send error or a
   short write means the client is gone or hopelessly backed up — close
   its fd and free the slot. The engine never blocks waiting for a
   reader. */
static void sink_tcp_emit(const char *line, size_t len) {
    sink_tcp_accept_pending();
    for (int i = 0; i < TCP_MAX_CLIENTS; ++i) {
        int fd = sink_tcp_clients[i];
        if (fd < 0) {
            continue;
        }
        ssize_t n = send(fd, line, len, MSG_NOSIGNAL);
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                continue;
            }
            close(fd);
            sink_tcp_clients[i] = -1;
            continue;
        }
        if ((size_t)n != len) {
            close(fd);
            sink_tcp_clients[i] = -1;
        }
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

/* Apply one parsed token to the in-progress mask. Unknown tokens emit
   a one-shot diagnostic to stderr and add the stderr bit so the
   developer at least sees the misconfig in their terminal. file:/tcp:
   are single-instance per process; a duplicate emits a diagnostic and
   is ignored, leaving the first instance intact. */
static void apply_sink_token(const char *tok, unsigned *mask) {
    if (!*tok) {
        return; /* tolerate stray commas: "stderr,,file:foo" */
    }
    if (!strcasecmp(tok, "stderr")) {
        *mask |= SINK_BIT_STDERR;
    } else if (!strcasecmp(tok, "null")) {
        /* explicit no-op — useful only as the sole token, where it
           leaves the mask empty and the engine emits nothing */
    } else if (!strncasecmp(tok, "file:", 5) && tok[5]) {
        if (*mask & SINK_BIT_FILE) {
            fprintf(stderr,
                    "[ERR][log] RYL_LOG=file:%s: a file: sink is already "
                    "open; ignored\n",
                    tok + 5);
            return;
        }
        if (open_file_sink(tok + 5)) {
            *mask |= SINK_BIT_FILE;
        }
    } else if (!strncasecmp(tok, "tcp:", 4) && tok[4]) {
        if (*mask & SINK_BIT_TCP) {
            fprintf(stderr,
                    "[ERR][log] RYL_LOG=tcp:%s: a tcp: sink is already "
                    "open; ignored\n",
                    tok + 4);
            return;
        }
        if (open_tcp_sink(tok + 4) == 0) {
            *mask |= SINK_BIT_TCP;
        }
    } else if (!strncasecmp(tok, "syslog", 6) &&
               (tok[6] == '\0' || tok[6] == ':')) {
        if (*mask & SINK_BIT_SYSLOG) {
            fprintf(stderr,
                    "[ERR][log] RYL_LOG=%s: a syslog sink is already "
                    "open; ignored\n",
                    tok);
            return;
        }
        const char *ident = (tok[6] == ':' && tok[7]) ? tok + 7 : NULL;
        if (open_syslog_sink(ident) == 0) {
            *mask |= SINK_BIT_SYSLOG;
        }
    } else {
        fprintf(stderr,
                "[ERR][log] unrecognized RYL_LOG token '%s' (expected "
                "stderr|null|file:<path>|tcp:[host:]port|"
                "syslog[:<ident>])\n",
                tok);
        *mask |= SINK_BIT_STDERR;
    }
}

static unsigned resolve_mask(void) {
    static int resolved = 0;
    static unsigned mask = 0;
    if (resolved) {
        return mask;
    }
    resolved = 1;
    const char *env = rcfg_get("RYL_LOG");
    if (!env || !*env) {
        mask = SINK_BIT_STDERR; /* compat default — historical stderr */
        return mask;
    }
    char tok[256];
    size_t tlen = 0;
    int overflow = 0;
    for (const char *p = env;; ++p) {
        if (*p == ',' || *p == '\0') {
            if (overflow) {
                fprintf(stderr,
                        "[ERR][log] RYL_LOG token longer than %zu bytes; "
                        "ignored\n",
                        sizeof tok - 1);
            } else if (tlen > 0) {
                tok[tlen] = '\0';
                apply_sink_token(tok, &mask);
            }
            tlen = 0;
            overflow = 0;
            if (*p == '\0') {
                break;
            }
            continue;
        }
        if (tlen + 1 < sizeof tok) {
            tok[tlen++] = *p;
        } else {
            overflow = 1;
        }
    }
    return mask;
}

static void emit_audit(const char *body) {
    /* Same format as the pre-§24 ryl_log() so existing
       data/rylween.log readers stay byte-compatible. clock_now() honors
       RYL_FAKE_EPOCH; the audit file is reproducible under replay. */
    static const char *months[13] = {
        "",
        "Jan",
        "Feb",
        "Mar",
        "Apr",
        "May",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Oct",
        "Nov",
        "Dec",
    };
    struct wall_clock c;
    clock_now(&c);
    int m = c.month;
    if (m < 1 || m > 12) {
        m = 0;
    }
    char path[512];
    ryl_state_path("rylween.log", path, sizeof path);
    FILE *fh = fopen(path, "a+");
    if (!fh) {
        return;
    }
    fprintf(fh, "%s %2d %2d:%02d %s\n", months[m], c.day, c.hour, c.minute,
            body);
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
    unsigned mask = resolve_mask();
    if (mask == 0) {
        return;
    }
    /* Format the line once into a shared buffer; tee it to every
       enabled sink. Order is stderr → file → tcp; only matters if
       multiple are active and the reader is correlating timestamps. */
    char line[1100];
    int written = snprintf(line, sizeof line, "[%s][%s] %s\n", lvl_tag(lvl),
                           module, body);
    if (written <= 0) {
        return;
    }
    size_t len = (size_t)written < sizeof line ? (size_t)written
                                               : sizeof line - 1;
    if (mask & SINK_BIT_STDERR) {
        fwrite(line, 1, len, stderr);
    }
    if ((mask & SINK_BIT_FILE) && sink_file) {
        fwrite(line, 1, len, sink_file);
        fflush(sink_file);
    }
    if (mask & SINK_BIT_TCP) {
        sink_tcp_emit(line, len);
    }
    if (mask & SINK_BIT_SYSLOG) {
        /* No trailing newline — syslog(3) framing handles it. The
           level tag is redundant with the syslog priority but kept
           so a `grep '\[INF\]'` over the system log behaves the
           same as over a file: sink. */
        syslog(syslog_priority(lvl), "[%s][%s] %s", lvl_tag(lvl), module,
               body);
    }
}
