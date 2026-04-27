# Rylween logging — sinks & operator notes

Authoritative reference for the unified logging API landed in §24.
The in-source companion is `src/log.h` (the `LOG_D` / `LOG_I` /
`LOG_W` / `LOG_E` / `LOG_A` macros and the `log_msg` declaration);
this doc covers the *runtime* side: thresholds, sinks, and the
rcon-style TCP listener.

---

## 1. Severity and threshold

Every emitted line carries one of five severities. The first four
are diagnostics; AUDIT (`LOG_A`) is user-visible game history and is
treated separately.

| level    | macro   | meaning                                              |
|----------|---------|------------------------------------------------------|
| DEBUG    | `LOG_D` | trace-level; off by default                          |
| INFO     | `LOG_I` | descriptive (e.g. "realizing 23 NPCs across 5 towns")|
| WARN     | `LOG_W` | expected-rare diagnostic; recoverable                |
| ERR      | `LOG_E` | unrecoverable but non-panic                          |
| AUDIT    | `LOG_A` | user-visible history ("party entered the realm")     |

Default threshold is **WARN** — `LOG_I` and `LOG_D` are silent
unless the operator widens the threshold:

```sh
RYL_LOG_LEVEL=info  ./rylween "Test User"
RYL_LOG_LEVEL=debug ./rylween "Test User"
```

Accepted values are `debug`, `info`, `warn`, `err` (alias `error`).
Anything unrecognized falls back to `warn` silently. AUDIT bypasses
the threshold and **always** fires.

---

## 2. Sink dispatch (`RYL_LOG`)

`RYL_LOG` is a *comma-separated list* of sink directives. Every
WARN/ERR/INFO/DEBUG line is teed to every enabled sink. AUDIT
(`LOG_A`) always writes `data/rylween.log` regardless of `RYL_LOG`
— the audit trail's destination is fixed because it's history, not
diagnostic. Its on-disk format (`Mon  D HH:MM body`) is byte-
compatible with the pre-§24 `ryl_log()` output so existing readers
keep parsing.

Recognized tokens:

| token                  | effect                                            |
|------------------------|---------------------------------------------------|
| `stderr`               | write to stderr (the historical default)          |
| `null`                 | no-op token; the only-token-in-list form silences |
| `file:<leaf-or-path>`  | append `[lvl][mod] body\n` to a file              |
| `tcp:[host:]port`      | bind a TCP listener; broadcast to every client    |

Worked examples:

```sh
# unset / empty                     stderr (compat default)
RYL_LOG=stderr                      # explicit form of the default
RYL_LOG=null                        # silent — drop every diag line
RYL_LOG=file:dev.log                # append to $HOME/.rylween/dev.log
RYL_LOG=tcp:9999                    # listen on 127.0.0.1:9999
RYL_LOG=tcp:0.0.0.0:9999            # listen on every interface
RYL_LOG=stderr,file:dev.log         # tee to terminal AND file
RYL_LOG=tcp:9999,file:run.log       # rcon AND a file
RYL_LOG_LEVEL=debug RYL_LOG=tcp:9999 ./rylween "Test User"
```

Open errors emit a one-shot `[ERR][log] …` to stderr and degrade
that token to a no-op; remaining tokens still apply. Unknown tokens
also add the stderr bit so the misconfig is visible.

`file:` and `tcp:` are each single-instance per process — a second
`file:` (or second `tcp:`) directive in the list is ignored with a
diagnostic. If you need two files, use `file:` plus an external
`tee`, or use `tcp:` + `nc -k -l <port> >> second.log` on the
listener side.

The default sink composition is preserved by the replay harness in
`tools/replay.sh` and `tools/replay_asan.sh`: every fixture pins
`RYL_LOG=null` in the per-fixture subshell *before* sourcing the
optional `<name>.env`, so a fixture opts back in to stderr (or
`file:` / `tcp:`) by exporting `RYL_LOG=…` in its sidecar —
symmetric with how `RYL_FORCE_*` and `RYL_FAKE_EPOCH` compose.

---

## 3. The TCP listener (`tcp:` sink)

Bind forms:

```
tcp:host:port    host explicit (e.g. tcp:127.0.0.1:9999, tcp:0.0.0.0:9999)
tcp::port        empty host → 127.0.0.1
tcp:port         no colon    → 127.0.0.1
```

`port` is decimal, `1..65535`. IPv6 literals are not supported (no
bracket parsing); pass an IPv4 address or use `0.0.0.0`. Bind
failures emit `[ERR][log] RYL_LOG=tcp:…` to stderr and the engine
keeps running with that sink dropped.

### Bind discipline

Default bind is loopback (`127.0.0.1`) — a `tcp:9999` or `tcp::9999`
listener is reachable only from the same host. Exposing the log
stream on every interface requires the explicit `tcp:0.0.0.0:<port>`
form. There is no authentication; treat the stream as a development
diagnostic, not an operations-grade log shipper.

`SO_REUSEADDR` is set on the listener so a quick restart after `^C`
doesn't trip TIME_WAIT.

### Wire format

Every line written to a connected client is exactly what other sinks
see:

```
[WRN][party] body
[INF][res] realized 23 NPCs across 5 towns
```

`[lvl][mod] body\n` — three-letter level tag (`DBG`/`INF`/`WRN`/
`ERR`), short module ident, body, single LF. No timestamps in the
on-wire format; if you want them, pipe through `ts(1)` or capture
`file:` instead.

### Tailing the stream

```sh
# terminal A
RYL_LOG=tcp:9999 RYL_LOG_LEVEL=info ./rylween "Test User"

# terminal B (or wherever)
telnet localhost 9999
# or:
nc localhost 9999
```

Up to **8 simultaneous clients** are supported (the `clients[]` slot
table is sized for the rcon use-case, which is one or two operators
tailing a running engine — not a fleet). A 9th connection while the
table is full is `accept()`-ed and immediately closed.

### Loss policy on slow readers

The engine is the only pump — log lines are written from inside
`log_msg`, with no separate event loop. To keep a stuck telnet
session from stalling the game:

* Every accepted client fd is set non-blocking.
* On `EAGAIN` / `EWOULDBLOCK` (the kernel buffer is momentarily
  full), the line is **dropped for that client only** and the
  engine moves on.
* On `EPIPE` / other send error, or any short write, the client's
  fd is closed and its slot freed.
* The listener fd is also non-blocking; pending connects are
  accepted lazily on each emit before the broadcast write.

Net effect: a backgrounded telnet, a closed terminal, or a frozen
SSH tunnel will silently lose lines (or get disconnected) but
cannot block the engine. Clients that were attached during a
quiet stretch will simply see the next emit.

### Lifetime

The listener fd plus every connected client fd are closed via an
`atexit` hook on normal exit. There is no graceful "broadcast a
goodbye line" — readers see EOF.

---

## 4. Runtime config file (`runtime.json5`)

`RYL_LOG`, `RYL_LOG_LEVEL` and the rest of the always-on `RYL_*`
knobs all flow through a tiny lookup helper (`rcfg_get`) that
consults, in order:

1. the environment variable (wins if non-empty)
2. `$HOME/.rylween/runtime.json5` (personal default)
3. `data/runtime.json5` (project shared default — not present in
   the repo by default; only loaded if you commit one)
4. the hard-coded fallback baked into each callsite

Schema is a flat JSON5 object whose keys are the existing `RYL_*`
names and whose values are scalars (strings, ints, or bools — ints
and bools are stringified at load time):

```json5
// $HOME/.rylween/runtime.json5
{
    // Tee log to a TCP listener and a file; never silent.
    RYL_LOG:        'tcp:9999,file:dev.log',
    RYL_LOG_LEVEL:  'info',

    // Always run with the artificial-delay loops short-circuited;
    // pin the RNG seed for byte-stable replays during dev work.
    RYL_NO_DELAY:   1,
    RYL_FAKE_SEED:  12345,

    // Reproducible audit/log timestamps:
    RYL_FAKE_EPOCH: 1717200000,
}
```

Recognised keys (the always-on bucket):

| key                     | shape                  |
|-------------------------|------------------------|
| `RYL_LOG`               | sink list (§2)         |
| `RYL_LOG_LEVEL`         | `debug`/`info`/`warn`/`err` |
| `RYL_NO_DELAY`          | non-zero ⇒ skip waits  |
| `RYL_FAKE_EPOCH`        | unix seconds (int)     |
| `RYL_FAKE_SEED`         | RNG seed (int)         |
| `RYL_PARTY_MEMBER_MAX`  | 1..100 (int)           |
| `RYL_REPLAY_RUMOUR`     | 0..32 (int)            |
| `RYL_SNAPSHOT`          | output path            |

The replay harness pins env vars per-fixture; env always wins, so
fixture determinism is unaffected by whatever is in your personal
config. Replay runs also use `HOME=$(mktemp -d)`, so the personal
config is unreachable during `make check` / `make testasan`.

The **one-shot fixture knobs** in `src/replay_knobs.c`
(`RYL_FORCE_*`, `RYL_STARTING_GOLD`, etc.) are intentionally NOT
plumbed through `rcfg_get` — they `unsetenv()` themselves on first
read and are by nature per-fixture. If you find yourself wanting one
in a personal config, that's a hint the knob has graduated from
fixture-only to general-purpose; in that case migrate it to the
always-on bucket first, then add it to the table above.

Missing file is silently OK; a malformed file errors loudly through
the same `res_die` path the rest of the JSON5 loader uses.
Non-scalar values get a one-shot `[WRN][rcfg]` and are skipped.

---

## 5. AUDIT (`LOG_A`)

`LOG_A` is for user-visible game history and routes unconditionally
to `data/rylween.log` (or `$HOME/.rylween/rylween.log` under the
replay harness's isolated `HOME`). It bypasses the `RYL_LOG`
dispatch and the `RYL_LOG_LEVEL` threshold. Format is the historical
`Mon  D HH:MM body` shape from the pre-§24 `ryl_log()` so legacy
log readers keep parsing. `getclk()` honors `RYL_FAKE_EPOCH`, so the
audit file is reproducible under replay.

Use `LOG_A` only for things that belong in the player-visible
history ("party entered the realm", "Calladin perished"). Operator
diagnostics belong in `LOG_W` / `LOG_E`.
