# Rylween Port Plan — Completed Sections

This file collects the sections of `port-plan.md` whose work has fully landed (or which document a finished design / closed argument). New work happens in `port-plan-incomplete.md`; the cross-section index lives in the master `port-plan.md`. Section numbers and `§N` cross-references are preserved verbatim — sections appear in their original §-order regardless of which file they live in.

## 2. External dependencies you'd have to replace or delete

| Dependency | Where | Fix |
|---|---|---|
| `XCB`, `XCBD`, `XenolinkBase` callbacks | `ryl.c`, `pcryl.c`, scattered | Hard-wire `cli = 1`, delete `!cli` branches. |
| Amiga volume paths `Doors:Rylween/...`, `Doors:RYLWEEN/...` | ~18 files, ~120 sites | Swap for relative paths under a `data/` dir, or a `RYLWEEN_HOME` env var. |
| Include path `dh0:cprogs/z/ryl_cli/ryl.h` | every `.c` | Replace with `#include "ryl.h"`. |
| `getclk()` (Amiga DOS clock) | `ryl.c`, `pcryl.c`, `subs.c` | Replace with `localtime(time())` into the same 8-byte array shape. |
| `getch()` (DOS/conio) | `hotkey()` in `ryl.c`/`pcryl.c` | termios raw-mode one-char read. |
| `gets()` (removed in C11) | `ryl.c`, `pcryl.c`, `scan.c` | `fgets` with buffer + strip `\n`. |
| `Delay(n)` → `#define Delay sleep` in `porting.h` | everywhere | **This is wrong.** Amiga ticks are 50/sec, and the code calls `Delay(4)`, `Delay(20)`, `Delay(300)`. Mapping to `sleep(n)` gives multi-minute pauses. Needs to become `usleep(n * 20000)` or similar (20 ms per tick). |
| `stricmp` → `strcmp` in `porting.h` | everywhere | **Also wrong** — kills case-insensitive menu handling. Use `strcasecmp`. |
| `void main` | `ryl.c`, `pcryl.c`, `optdata.c`, `scan.c` | `int main`. |
| Implicit function decls | scattered | Add prototypes; compiler warnings today are errors by default on clang. |
| `log()` function name | `ryl.c` | Collides with `<math.h>` `log`. Rename. |
| `Rylween.REG` checksum gate | `driv.c init()` | Gate behind a `#define REGISTERED 1` or strip — no more registration endpoint. |

## 3. Already-ANSI is a big deal

Every screen-painting routine is literally `tx("\x1b[%d;0H", y)` plus
`tx("[33m...")` sequences and character-art in `gfx.c` (look at `src/gfx.c:46+`).
Modern terminals render this as-is — and have for 30 years. **You do not need
ncurses.** ncurses would be a step sideways: it manages a back-buffer and wants
to own the screen; this code paints unilaterally with raw escapes. Shoehorning
ncurses in means rewriting every `c()`/`tx()`/`clw()`/`blitgfx()` call site.

The same argument applies to notcurses / termbox2 / libtickit — they're all the
same back-buffer paradigm, and the library benefit (diff-based updates) doesn't
apply to a turn-based game that paints sparsely.

The `.ans` files in `src/ansi/` and `distro/*.ANS` are already raw CP437/ANSI
art — they just need to be sent to the terminal. On modern macOS Terminal /
iTerm2 / Linux xterm, the CP437 glyphs won't render correctly by default;
either:
- set the terminal to CP437 / "VGA" font (simplest), or
- pre-convert CP437 → UTF-8 box-drawing chars in a one-time pass (see §5).

## 4. Abstract the I/O boundary, not the renderer

The Xenolink-vs-CLI split is currently modeled as `if (!cli) XCB->Foo(...) else
printf(...)` littered across 10 files. That **is** an abstraction, just a badly
factored one. Replacing it with a real interface is cleaner whether or not you
ever add a second backend — and the diff isn't much larger than "delete the
`if(!cli)` arms".

There are two axes the abstraction could live on, and they're independent:

**Axis A — transport** (where do the bytes go?). Values: local terminal, SSH
server, telnet, WebSocket → xterm.js in a browser, file (record/replay). All of
these speak ANSI natively, so the bytes are identical — only the fd differs.

**Axis B — rendering model** (what generates the bytes?). Values: raw-ANSI
stream (status quo), cell-grid back-buffer (ncurses-style).

Abstract Axis A. Leave Axis B as raw ANSI. Every interesting backend on Axis A
(ssh, web, record) works fine with ANSI streaming, so there's no forcing
function to move on Axis B. If you ever decide you need a cell buffer later —
for mouse, sixel graphics, or fancy layout — you can add it as a second layer
*above* the I/O interface, without touching transport code.

### The I/O interface

```c
/* io.h */
typedef struct io_backend {
    const char *name;                       /* "tty", "ssh", "ws", "file"   */
    void  (*write)(const char *b, size_t n);
    int   (*getch)(void);                   /* blocking, one byte            */
    int   (*getline)(char *buf, size_t n);  /* returns len, strips newline   */
    int   (*carrier_ok)(void);              /* ssh: session alive; tty: 1    */
    void  (*dump_file)(const char *path);   /* sendfile-ish fast path        */
    void  (*shutdown)(void);                /* restore termios, close fds    */
} io_backend;

extern io_backend *io;   /* chosen once at main() */

void io_init_tty(void);  /* sets up termios, registers atexit, assigns io */
```

Every existing primitive becomes a thin wrapper:

```c
ULONG tx(UBYTE *fmt, ...) {
    char buf[256], jump[16];
    int n = snprintf(jump, sizeof jump, "\x1b[%dC", global_x);
    io->write(jump, n);
    va_list ap; va_start(ap, fmt);
    n = vsnprintf(buf, sizeof buf, (char*)fmt, ap);
    va_end(ap);
    io->write(buf, n);
    return n;
}

UBYTE hotkey(void)    { char c = io->getch(); return toupper(c) == 'Y'; }
char  key(void)       { return io->getch(); }
char* getstr(UBYTE l) { static char b[90]; io->getline(b, l < 90 ? l+1 : 90); return b; }
UBYTE carrier(void)   { return io->carrier_ok(); }
void  disptext(char *n){ io->dump_file(n); }
```

The `if (cli) ...` branches collapse entirely; `global_name` comes from argv
or from a backend-specific `io_tty_init(argv)`. The `XCB`, `XCBD`,
`XenolinkBase` globals go away. Header `doors/*` gets deleted.

Initial `io_tty.c` is ~100 lines: `tcgetattr`/`tcsetattr` with `ICANON|ECHO`
cleared, blocking `read(0, ...)` for `getch`, `fgets(stdin)` for `getline`,
`carrier_ok` returns 1, `dump_file` is `open` + `sendfile`/`write` loop.
Future backends (`io_ssh.c`, `io_ws.c`) implement the same vtable and
nothing in game code changes.

## 5. Extract content from code

All the inline ANSI *art* in the source — as distinct from color codes mixed
into text — lives in exactly one place: `blitgfx()` in `src/gfx.c` (a switch
with ~24 cases, each painting a 16x9 CP437 tile into the map window).
Everything else in the codebase is plain text with `\x1b[..m` color codes; that
stays inline because it's formatting, not art.

Audit confirmation: of all `tx(...)` calls in the codebase that contain
non-ASCII bytes (CP437 box/shade/block chars), **169 are in `gfx.c` and 0 are
anywhere else.**

### What to extract

1. **`blitgfx()` tiles** — 24 x 16x9 CP437 tiles, currently C string literals.
   Extract each case into `data/tiles/NN_name.ans` (e.g. `01_inn.ans`,
   `02_tavern.ans`, `40_encounter.ans`). `blitgfx(n)` becomes:

   ```c
   void blitgfx(UBYTE pic) {
       char path[64];
       snprintf(path, sizeof path, "data/tiles/%02d.ans", pic);
       clr_map_window();
       render_at(path, 8, 6);    /* origin of the tile window */
       c(49, 6);
   }
   ```

   A one-off Python/awk script can walk `gfx.c` and dump each case block.

   **Rendering contract — the tile window is bounded.** The map window is a
   fixed 16×9 rectangle at origin `(8,6)..(23,14)`, and the current code
   preserves this by issuing `c(8, y)` before every single row of tile art
   (see `src/gfx.c:52-62`). A tile file can't just be dumped with `cat` — the
   rows would flow from the current cursor position and smear across the
   rest of the screen.

   The file format must therefore be **one tile-row per line**, and the
   renderer is responsible for re-positioning the cursor at the start of
   each row. Concretely:

   ```c
   /* tile file: 9 lines, each line = one row's worth of ANSI (colors + glyphs),
      no trailing CSI that moves the cursor, ends with \n */
   void render_at(const char *path, int x, int y) {
       FILE *fh = fopen(path, "rb");
       if (!fh) return;
       char line[512];
       int row = 0;
       while (row < 9 && fgets(line, sizeof line, fh)) {
           size_t n = strlen(line);
           while (n && (line[n-1] == '\n' || line[n-1] == '\r')) line[--n] = 0;
           char pos[16];
           int pn = snprintf(pos, sizeof pos, "\x1b[%d;%dH", y + row, x);
           io->write(pos, pn);
           io->write(line, n);
           row++;
       }
       io->write("\x1b[0m", 4);   /* defensive SGR reset */
       fclose(fh);
   }
   ```

   Notes on the format:
   - **Newlines are row delimiters**, stripped at render time; they never
     reach the terminal. This is the opposite of the full-screen `.ans`
     files, which are dumped verbatim.
   - **"16 cells wide" is visual columns, not bytes.** After UTF-8
     conversion, box-drawing chars are multi-byte; the renderer doesn't count
     — it just re-positions per row, so overflow is a data-authoring concern,
     not a renderer concern.
   - Tile rows can contain **relative cursor moves** (`\x1b[NC` for "advance
     N"), e.g. `src/gfx.c:69` does `[4C[43m� �   �[0m���` — these are fine
     because each row starts from a known column (`x`), so relative moves
     are deterministic.
   - The extractor must **not** emit the leading `c(8,y)` CSI into the file
     — that's the renderer's job and is the whole point of extraction. It
     *must* keep all other `\x1b[...]m` color codes inline; they're part of
     the art.
   - End every file with `\x1b[0m` to neutralize SGR state for whatever
     renders next. The sample renderer above does this defensively regardless.

2. **Full-screen scenes** are *already* data-driven via `disptext()` loading
   `Doors:Rylween/SCREEN.ANS` etc. `src/ansi/*.ans` and `distro/*.ANS` are the
   same files under different volume names. Consolidate under `data/ansi/`.

3. **Help/journal text, title credits, README-ish screens** — some of these
   are `tx()`'d inline (e.g. the credits block at `src/driv.c:19-28`, the
   unregistered-game nag at `src/subs.c:85-138`). Low priority. Extract if
   you want to localize or theme later; leave alone if not.

### What NOT to extract

- Dynamic status line (`driv.c:219`) — content depends on game state; it's
  code, not data.
- Combat / dialogue strings with embedded `%s`, `%d` format specifiers —
  these are code templates. Extracting them becomes "build a localization
  system", which is a separate project (and not what you're asking for).
- The inline `[32m`, `[33m` color codes inside text strings — they're part
  of the string, not separate resources.

### File format

Plain raw ANSI — same as the existing `.ans` files. No wrapper, no header, no
index. The game reads them with `open`/`read` and streams them to
`io->write`. One file per tile. Simpler than a pack file and trivially
editable with any ANSI editor (MysticView, PabloDraw) if you ever want to
update the art.

Convert to UTF-8 **in the same one-off pass** that handles the other `.ans`
files. After conversion, "set terminal to CP437 font" goes away as a user
requirement — files render correctly in any UTF-8 terminal.

## 7. A concrete port plan

Estimated effort: **2-4 focused days** to get it booting and playable,
with the I/O abstraction and content extraction in place. Phase numbers
don't depend on the language choice; the per-step details do.

### Phase 0 — clean the tree
1. Delete `src/pcryl.c` and `src/pcdriv.c`. They are byte-doubled / blank-line-
   bloated duplicates of `ryl.c` and `driv.c` (diff them). Keep one set.
2. Delete `src/doors/` once `XCB->…` is gone — it's the Xenolink SDK.
3. Global sed: `#include "dh0:cprogs/z/ryl_cli/ryl.h"` → `#include "ryl.h"`.
4. Global sed: `"Doors:Rylween/..."` / `"Doors:RYLWEEN/..."` →
   `"data/..."` (case-fold). Copy `distro/*.DAT`, `distro/Map.DAT`,
   `distro/NPC.DAT`, the ANSI files, etc. into `data/`.

### Phase 1 — introduce the I/O abstraction
5. Add `src/io.h` with the `io_backend` struct (see §4). Add `src/io_tty.c`
   implementing the tty backend: termios raw mode in/out, `read(0,…)` for
   `getch`, `fgets(stdin)` for `getline`, `carrier_ok` returns 1,
   `dump_file` is `open` + `read`/`write` loop.
6. Rewrite `src/porting.h` as a `compat.h` that keeps the `UBYTE`/`ULONG`
   typedefs but drops the wrong `Delay`/`stricmp` macros. Add `compat.c`
   with `Delay(ticks)` (≈20 ms each), `getclk(char*)` (time+localtime),
   `strcasecmp` wrapper if needed.
7. Rewrite `tx()`, `c()`, `cl()`, `clw()`, `hotkey()`, `key()`, `getstr()`,
   `carrier()`, `cleanexit()`, `disptext()` as thin wrappers over `io->…`.
   All currently live in `ryl.c`; move them to a new `src/primitives.c`.
8. Delete the `XCB`/`XCBD`/`XenolinkBase` globals and every `if (!cli) …`
   else-branch. `cli` the variable also goes away.

### Phase 2 — make it compile & fix baseline bugs
9. `void main` → `int main` everywhere.
10. `gets(x)` → `fgets(x, sizeof x, stdin); strip_nl(x);`.
11. Rename `log()` → `ryl_log()` (collides with libm).
12. Add missing prototypes (most of them are already in `ryl.h`).
13. **§6 shared baseline:** `sprintf` → `snprintf` everywhere, audit every
    fixed-size `char buf[N]`, fix `getstr()` stack-pointer return
    (`ryl.c:123`), ensure `object()` returns on every path
    (`towne.c:307` falls through past `case 111`).
14. Add a `Makefile`. Flags depend on the §6 language choice:
    - **C path:** `cc -std=c99 -O2 -Wall -Wextra -Werror=format-security
      -Wno-parentheses *.c -o rylween`
    - **C++ path:** `g++ -std=c++17 -O2 -Wall -Wextra
      -Werror=format-security -Wno-parentheses -Wno-write-strings *.c*
      -o rylween`. Rename sources to `.cc` if you want clearer intent.

### Phase 3 — extract `gfx.c` tile art to data
15. Write a one-off extractor (python/awk) that walks `gfx.c`'s `blitgfx()`
    switch and for each `case N: ...` block produces `data/tiles/NN.ans`:
    - One line per `tx(...)` call's payload (the string literal contents).
    - Strip the `c(8, y)` cursor-positioning call; the renderer re-emits it.
    - Strip the leading `\x1b[` byte style (already literal in the source).
    - Preserve all other inline SGR codes verbatim — they're part of the art.
    - Append `\x1b[0m` at end of file.
16. Replace `blitgfx()` body with `render_at("data/tiles/NN.ans", 8, 6)`
    (see §5 for the `render_at` sketch).
17. Delete the giant switch. `gfx.c` shrinks from ~300 lines to ~30.

### Phase 4 — make it run
18. Fix `Delay`: Amiga tick ≈ 20 ms. Audit any `Delay(>500)` call sites and
    confirm they're loading/transition pauses, not "sleep for the night"
    (they're the former; this is the pacing in `speak.c` / `strid.c`).
19. Confirm `getclk` call sites write the indices the callers read:
    `[2]` month 1-12, `[3]` mday, `[4]` hour, `[5]` min, `[7]` sec — see
    `ryl.c:215` and `subs.c:76`.
20. Smoke-test: `./rylween "Test User"` (argv is just the player name now
    that the `CLI` keyword is gone).

### Phase 5 — polish
21. Convert CP437 → UTF-8 once for both the tile files **and** full-screen
    `.ans` files in one pass (iconv + a small lookup table for the CP437
    block/shade glyphs `\xDB`, `\xB0`, `\xB1`, `\xB2`, `\xDC`, `\xDF`, etc.).
    After this step, the game runs correctly on a default UTF-8 terminal
    with no font configuration.
22. Strip `Rylween.REG` checksum gate in `driv.c init()` — hard-code
    `reg = 1` so the game doesn't nag / slow-mode.
23. `SIGWINCH` handler: the game assumes 80x25. On resize, trigger the
    existing `!` redraw path.
24. Persist saves to `~/.rylween/` so `data/` can be read-only.

### Phase 5b — lift globals into a `ryl_state` struct

The code is globals-heavy by BBS-door convention: one process, one
party, all state file-scope. That convention was fine for Xenolink;
it fights every modernization goal after it. Concretely:

- Phase 6 (data-driven engine) reads and writes state on every call;
  a threaded state pointer is what makes the resource loader, the
  dialogue interpreter, and the mission state machines expressible
  without touching `extern` soup.
- Save/load today is `fwrite(p, sizeof p, 1, f)` across 25 party
  slots plus side-band globals. A single `fwrite(state, sizeof
  *state, 1, f)` collapses the format — but only once the side-band
  globals are *in* the struct.
- Testability: a `ryl_state *` the test harness can construct and
  inspect is the difference between "I played it and it seemed to
  work" and regression tests.
- Future backends (SSH server, WebSocket) will want to run multiple
  sessions in one process. That's impossible with file-scope state;
  trivial with a per-session struct.

**Scope.** Touches roughly every function in `ryl.h` (~120
prototypes) and ~1500 call sites (706 `p[cpl]` refs plus ~885 scalar-
global refs as of 2026-04-21). Net LOC delta is small — it's
a signature/parameter shuffle, not logic — but it touches every file.

**Why between Phase 5 and Phase 6, not earlier or later.** Doing it
before the game boots (Phase 4) means finishing the port twice:
once with globals to prove the game runs, once without. Doing it
after Phase 6 means the resource loader, dialogue interpreter, and
mission tables all get written *around* the globals and then
retrofitted. The only time this is cheap is when the game runs,
the call sites haven't yet been touched by §9 work, and there are no
active feature branches.

**Struct shape.** One struct, three regions:

```c
typedef struct ryl_state {
    /* --- Persistent: serialized to save file --- */
    struct party  p[26];
    struct person npc[41];
    char          map[42][82];
    int           dx[15], dy[15];
    int           antal_spelare;
    int           senaste_dagen;
    int           day_at_game_start;

    /* --- Session runtime: reset per login --- */
    int           cpl;          /* current party index 1..25        */
    int           tid;          /* hour of day 0..24                */
    int           twn;          /* current town id or 0             */
    int           hours_pigg;
    int           banished;
    int           reg;          /* registered? (Phase 5 hard-codes 1) */
    char          global_name[30];
    char          last_terrain;

    /* --- Render cursor: fed from primitives.c --- */
    int           global_x;

    /* --- I/O backend (moves here from primitives.c file scope) --- */
    io_backend   *io;
} ryl_state;
```

Keep the save region first so `fwrite(state, offsetof(ryl_state,
cpl), 1, f)` still produces a flat persistent blob — or introduce a
split save struct and a `state->save` sub-struct. Pick whichever is
cheaper when you get there; the shape above favors the `offsetof`
trick.

**Threading strategy.**

1. Define `ryl_state` in a new `src/state.h`. Allocate one in
   `main()`: `ryl_state S = {0}; ryl_state *s = &S;`.
2. Add `ryl_state *s` as the *first* parameter to every function in
   `ryl.h` — machine-edit with a semantic patch (`coccinelle`) or
   a scripted sed + clang-format. Signature convention: `s` first,
   game args after.
3. Replace every reference to `cpl`, `tid`, `p[...]`, `npc[...]`,
   `map[...]`, etc. with `s->cpl`, `s->tid`, `s->p[...]`, and so on.
   Again: scripted. The identifier set is small (14 scalars + 5
   arrays/tables) and non-overlapping with local variables.
4. Delete the `extern` declarations from `ryl.h` and the
   definitions from `ryl.c:6-14`.
5. Audit for accidental shadows: several functions use `int cpl`
   as a *local* — harmless today because the local shadows the
   global, but after the refactor `s->cpl` is the only `cpl`, so
   rename the locals first to avoid silent semantic changes.
6. Primitives (`tx`, `c`, `cl`, `clw`, `hotkey`, …) also take `s`
   — they need `s->io` and `s->global_x`. This collapses the
   `io_backend *io` file-scope pointer currently in
   `primitives.c`.

**Compatibility.** Save format: `p[26]` today is `fwrite`d as an
array; after the refactor it's a field inside `ryl_state`. Either
(a) keep a `write_save(state)` wrapper that writes the same byte
layout as today (just `fwrite(state->p, sizeof state->p, 1, f)`),
or (b) bump the save format version and stop pretending old Amiga
`REALM.DAT` files should load. (a) is cheaper and preserves any
field-tested save files; (b) is cleaner once §9 data work lands.
Decision can wait until the refactor is in flight.

**Tooling.** Coccinelle is the right weight for this:

```cocci
@@ identifier f; @@
- f(
+ f(ryl_state *s,
  ...
) { ... }

@@ @@
- cpl
+ s->cpl
```

…one rule per identifier. An LLM-assisted sed pass works too, but
Coccinelle understands C scoping and won't rewrite `int cpl` locals
unless you ask it to.

**Test strategy.** No existing test suite. Before starting:

1. Record a scripted input run (a deterministic seed + a fixed
   sequence of keystrokes) that exercises login, movement, combat,
   town interaction, save/load. Capture the screen-output byte
   stream.
2. After the refactor, replay the same input and diff the output.
   Zero diff = refactor didn't change behavior. Non-zero diff =
   bisect the regression.

This also pays for itself in Phase 6.

**Estimated effort:** 2-3 days. Most of the time is scripting the
mechanical edit and auditing shadows; the rest is the input-replay
harness.

### Phase 5c — party-member view abstraction (deferred migration)

Introduced alongside Phase 6b spell extraction: `pm_t` in `ryl.h` —
a by-value view `{struct party *, int m}` plus inline accessors
(`pm_cl`, `pm_lv`, `pm_ma`, `pm_hp`, `pm_h`, `pm_dead`, `pm_name`,
`pm_is_priest`, `pm_is_mage`, `pm_is_class`) and class-ID constants
(`PC_FIGHTER`..`PC_RANGER`). New code uses these; legacy call sites
still read `state.p[state.cpl].lv[m]` etc.

**Why deferred.** ~706 `p[cpl]` refs and ~30 `cl[.] == 3/4` magic-number
comparisons across the tree. Migrating them in one pass is phase-5b-sized
churn, and each migrated block needs a smoke-test to confirm
byte-identity. Doing it alongside content extraction mixes two concerns
and makes PR review painful.

**When to run the pass.** Between Phase 6 sub-phases, whenever a module
is already being opened for content work. E.g.: 6c touches `subs.c` and
`driv.c` text — migrate those modules' `p[cpl]` refs in the same pass.
By the time 6f finishes, the whole tree is on `pm_*`.

**Save-format compatibility.** `pm_t` is purely a view over the existing
SoA `struct party`; nothing on disk changes. A later AoS layout
(array-of-party_member_t instead of parallel arrays) would be a separate
decision and would touch the save format.

## 10. Why not port to Kotlin (or Rust, or …)?

You *could*. It would be a fun rewrite. But:

- The code is ~12k LOC of tangled globals. A faithful port is a week or two
  of drudgery; a clean rewrite where you actually redesign the state is
  **4-8 weeks**, and you risk losing quirks that make it feel like the
  original.
- The UI layer is raw ANSI — Kotlin would push you toward JLine / Lanterna,
  which either (a) own the screen (so you still rewrite every draw call), or
  (b) you pipe ANSI through, which is what the C already does.
- The only moving parts that actually *benefit* from a higher-level language
  are save-file I/O (fixed-size `fwrite(&struct)` blobs are not
  cross-platform) and string handling. Both are small.

**Recommendation:** do the minimal C fix first. It's a couple of days and
gets you something you can actually play. If the itch to modernize is still
there afterward, you have a working reference to port against, which is
enormously easier than porting from a non-running source.

## 11. Rough diff budget for Phase 0-5

| Change | Files touched | Lines |
|---|---|---|
| Include path sed | ~20 | ~20 |
| Data path sed | ~18 | ~120 |
| Delete duplicates (`pcryl.c`, `pcdriv.c`) | 2 | -3400 |
| Delete `src/doors/` SDK | whole dir | -several kLOC |
| `porting.h` → `compat.{h,c}` | 2 new, 1 replaced | +150 |
| `io.h` + `io_tty.c` (§4) | 2 new | +250 |
| Move primitives to `primitives.c` | 1 new, 1 edited | ±0 |
| Delete XCB branches + globals | ~10 | -400 |
| `void main` / `gets` / `log` rename | ~4 | ~20 |
| §6 baseline: `snprintf` sweep, `getstr` fix, buffer audit | ~15 | +50 |
| Makefile | 1 new | +15 |
| Tile extraction: `gfx.c` → `data/tiles/*.ans` (§5) | 1 edited, 24 new | -250 code, +24 data files |
| Phase 4/5 polish (`Delay` audit, `getclk`, UTF-8 conv, SIGWINCH) | ~5 | +100 |
| Phase 5b: globals → `ryl_state` (signature + ref rewrite) | ~14 | ±0 logic, ~1500 site touches |
| **Net** | | **roughly -3800 code, +550 code, +24 data files** |

Most of the "work" is deletions. C++17 path adds ~1 day of C-as-C++
compile fixes; no meaningful line-count delta.

Phase 6+ (content extraction to JSONL, §9) has its own budget — roughly
-4000 code lines, +2500 lines of JSONL, +500 lines of loader code. Do it
as a separate pass after Phase 5 ships.

## 13. Leak inventory (Commit A of §12.2)

Spec pass for the teardown work: every `malloc` / `realloc` /
`calloc` / `strdup` call reachable from `main`, catalogued with its
owning field and its current free path (or lack of one). Gathered
by grepping `src/*.c` for those four functions and reading each
call site. Node-tree allocations are grouped once under
`node_new` / `node_free` rather than listed per call — the parser
dispatches every N_STR / N_OBJ / N_ARR / N_PATH payload through
that surface.

### 13.1 Allocation sites

| # | Site | What is allocated | Where the pointer is stored | Free path today |
|--|--|--|--|--|
| 1 | `json5.c:30` `node_new` (calloc) | one `node` struct | threaded through `world_tree` (and transient parser subtrees / import wrappers) | `node_free` walks and frees recursively — but only runs on (a) the wrapper swapped out by `resolve_imports`, (b) the `wieldable_by` N_ARR swapped to N_INT in `fold_items`. `world_tree` itself is never freed. |
| 2 | `json5.c:142` `p_ident_raw` (malloc) | NUL-terminated identifier | returned as object key (stored in `obj.items[].key`) or as a keyword scratch string (`true` / `false` / `null`) | object-key case: freed by `node_free` when the owning N_OBJ is freed (but see #1 — top-level tree never frees). Keyword scratch: freed inline (`json5.c:411/417/423/426`). Error-path `free(key)` at `json5.c:359` is dead today (pdie exits). |
| 3 | `json5.c:162+171` `p_quoted_string` (malloc + realloc) | decoded string payload | N_STR node's `u.sv`, or transient object key | N_STR path: freed by `node_free`'s `case N_STR` — but only on transient subtrees (see #1). `compile_string_to_path` (`eval.c:229-234`) also frees `u.sv` when it swaps N_STR→N_PATH in place, so DSL path strings don't leak even without `node_free(world_tree)`. |
| 4 | `json5.c:317` `p_array` (realloc) | N_ARR `items[]` pointer array | `node.u.arr.items` | `node_free`'s `case N_ARR` — but only on transient subtrees (see #1). |
| 5 | `json5.c:367` `p_object` (realloc) | N_OBJ `items[]` key/val array | `node.u.obj.items` | `node_free`'s `case N_OBJ` — but only on transient subtrees (see #1). |
| 6 | `json5.c:447` `slurp_file` (malloc) | whole file read buffer | local `src` in `json5_parse_file` | freed at `json5.c:471` before return. Clean. |
| 7 | `eval.c:140,147` `parse_path` (strdup) | local-binding name (`"$dmg"` / `"base_cost"`) | N_PATH `u.path.local` | `node_free`'s `case N_PATH` — same caveat as #1. |
| 8 | `eval.c:536` `env_set_local` (realloc) | `env_t.locals[]` array | `env_t.locals` | `env_free` at `eval.c:523`. Called by battle/talk callers when `env_t` goes out of scope. Clean. |
| 9 | `eval.c:544` `env_set_local` (strdup) | local-binding name | `env_t.locals[i].name` | `env_free` at `eval.c:521`. Clean. |
| 10 | `res.c:296` `fold_flags` (realloc) | `flag_rec[]` backing array | `flags_tab` (file-scope) | **never freed**. |
| 11 | `res.c:302` `fold_flags` (strdup) | flag id (e.g. `"guild_member"`) | `flags_tab[i].id` | **never freed**. |
| 12 | `res.c:306` `fold_flags` (malloc) | `char *[]` values array | `flags_tab[i].values` | **never freed**. |
| 13 | `res.c:315` `fold_flags` (strdup) | one value string | `flags_tab[i].values[j]` | **never freed**. |
| 14 | `res.c:383` `fold_items_section` (strdup) | item display name | `items[id].name` (fixed-size `items[112]`) | **never freed**. Defensive `free((void *)it->name)` at `res.c:381` makes re-load safe but is dead today (world loads once). |
| 15 | `res.c:437` `fold_monsters` (strdup) | monster display name | `monsters[id].name` (fixed-size `monsters[91]`) | **never freed**. Defensive `free` at `res.c:435`, same story as #14. |
| 16 | `res.c:461` `emit_text` (realloc) | `text_rec[]` backing array | `texts` (file-scope) | **never freed**. |
| 17 | `res.c:467-468` `emit_text` (strdup x2) | text id + payload | `texts[i].id`, `texts[i].text` | **never freed**. |
| 18 | `res.c:482` `join_lines_crlf` (malloc) | CRLF-joined scratch buffer | local `out` in caller | freed at `res.c:510` after `emit_text` strdups it. Clean. |
| 19 | `res.c:609-610` `fold_spells` (strdup x2) | spell id + name | `priest_spells[slot].id/.name` or `mage_spells[slot].id/.name` (fixed-size `[8]` each) | **never freed**. Defensive `free` at `res.c:607/608`. |
| 20 | `res.c:659` `fold_terrain` (strdup) | glyph string (one or two chars) | `glyph_tab[byte]` (fixed-size `[256]`) | **never freed**. Defensive `free(glyph_tab[g])` at `res.c:658`. |
| 21 | `res.c:704/706/708` `fold_pronoun` (strdup x3) | subject / object / possessive form | `pronoun_tab[sex][1..3]` (fixed-size `[3][4]`) | **never freed**. Defensive `free` at `res.c:703/705/707`. |
| 22 | `res.c:728` `fold_enums` (realloc) | `enum_rec[]` backing array | `enums` (file-scope) | **never freed**. |
| 23 | `res.c:734` `fold_enums` (strdup) | enum id (e.g. `"jobname"`) | `enums[i].id` | **never freed**. |
| 24 | `res.c:736` `fold_enums` (malloc) | `char *[]` values array | `enums[i].values` | **never freed**. |
| 25 | `res.c:745` `fold_enums` (strdup) | one enum value string | `enums[i].values[j]` | **never freed**. |

### 13.2 What's leaked at exit

On process exit today we drop (roughly in size order):

- The entire `world_tree` node graph from `json5_parse_file` — every
  N_OBJ / N_ARR / N_STR / N_PATH node plus their `u.sv` / `u.path.local`
  / `items[]` / `items[].key` allocations. This is the biggest single
  leak by node count; the tree contains every `texts`, `flags`,
  `items`, `monsters`, `spells`, `terrain`, `pronoun`, `enums`
  subtree that the folds only read from, plus every `do`/`cond` body
  the evaluator walks at dispatch. Note that `compile_string_to_path`
  has already converted DSL path-position N_STRs to N_PATHs, so the
  tree's path strings live at `u.path.local` rather than `u.sv` —
  `node_free`'s N_PATH case covers that, no fix needed.
- `flags_tab` + each row's `id`, `values[]`, and each `values[j]`.
- `enums` + each row's `id`, `values[]`, and each `values[j]`.
- `texts` + each row's `id` and `text`.
- `items[0..111].name` (non-NULL slots only).
- `monsters[0..90].name` (non-NULL slots only).
- `priest_spells[1..7].id/.name` and `mage_spells[1..7].id/.name`
  (non-NULL slots only).
- `glyph_tab[0..255]` (non-NULL slots only).
- `pronoun_tab[1..2][1..3]` (non-NULL slots only).

No stack-owned `env_t` leaks observed — every `env_init` caller
pairs with `env_free` before its scope exits. The `slurp_file` and
`join_lines_crlf` buffers are both bounded to their caller's scope
and freed there.

### 13.3 Design constraints for Commit B

Captured from the §12.2 conversation while building this inventory;
these shape the `res_shutdown` implementation:

- **Counts are the parser's job.** `N_OBJ.u.obj.n` and
  `N_ARR.u.arr.n` are already the authoritative cardinality of
  every subtree — the JSON5 parser fills them as it reads. Neither
  the schema (no `"count": N` field in `world.json5`) nor the fold
  (no parallel `n_items_loaded` / `n_monsters_loaded` counter)
  needs to re-derive what the parse tree already knows. The
  `flags` / `enums` subtrees give you `flags_node->u.obj.n` /
  `enums_node->u.obj.n`; the `items` sections give you
  `section->u.obj.n` (summed across sections); `terrain` gives
  you `terrain_node->u.obj.n`; `pronoun` gives you
  `pronoun_node->u.obj.n`; each class's `spells` array gives you
  `list->u.arr.n`. The fold uses those counts as loop bounds
  already; `res_shutdown` should read the same values off
  `world_tree` rather than maintain a mirror.
- **Free loops iterate by parser-reported count, not by sentinel
  scan.** The dynamic tables (`flags_tab`, `enums`, `texts`) still
  need their own row counts because the fold copies strings out of
  the tree into a flat table — but `n_flags_tab` / `n_enums` /
  `n_texts` already track that. The fixed-slot tables (`items`,
  `monsters`, `priest_spells`, `mage_spells`, `glyph_tab`,
  `pronoun_tab`) are sparse by design: `items` is indexed by
  item-id, not by insertion order. `res_shutdown` should walk
  `world_tree` (`items_node`, `monsters_node`, `terrain_node`,
  `pronoun_node`, `classes_node`) and free the matching slots in
  the legacy tables — that way the `NULL`-scan disappears and no
  call site hardcodes `0..111`, `0..90`, `0..255`, `1..2 × 1..3`,
  or `1..7`. Upshot: `res_shutdown` frees the legacy tables
  *before* it frees `world_tree`.
- **`make test` is the standardized gate task.** Landed in
  `42f4400`: `make test` runs `tools/replay.sh --all` +
  `tools/check_world.sh`. Commit C's ASan replay leg landed
  separately as `tools/replay_asan.sh` + `make testasan` — same
  fixture set under `$(BIN_ASAN)`, no byte-identity check, fails
  on `AddressSanitizer` / `LeakSanitizer` markers in the captured
  output. Contributors and CI have one canonical task name per
  gate (`make test` for byte-identity, `make testasan` for the
  sanitizer scorecard).

### 13.4 Commit B: implementation notes (2026-04-22)

Landed as `9574a57`. `res_shutdown()` in `res.c` frees all 16 leak
sites from §13.1; `world_tree` goes last via `node_free`. Registered
via `atexit()` at the end of `res_load_world` (one-shot guarded) and
called explicitly from `cleanexit()` (idempotent). Nearby cleanup:
`color_default`'s literal `36` + `color_code_from_name`'s raw
`30..37` returns now route through a local `ANSI_FG_*` enum. Gate
byte-identical.

Also in this pass: the stranded `.claude/worktrees/agent-a4fa40c7/`
git worktree (source of the `strid.c` / `sextext` / `kar` ghost
diagnostics) was removed via `git worktree remove -f -f` plus
`git branch -D worktree-agent-a4fa40c7`. Clangd diagnostics now
only reflect the real tree.

### 13.5 Out of scope for §13

- `struct ryl_state state` (file-scope in `ryl.c`) holds no
  heap pointers itself; nothing to free. Party/npc name arrays
  are fixed `char[30]` in `struct person`, not heap-owned.
- `eval.c:232` `free(s)` inside `compile_string_to_path` is
  already a correct pair against `json5.c:162`'s strdup/malloc
  for the N_STR payload it's rewriting — no leak fix needed
  there; the in-place N_STR→N_PATH swap is clean.
- Static caches like the color_tab `[256]` `int32_t` array and
  the `color_tab_set` flag array live in `.bss` — no malloc
  involved, nothing to free.

## 14. State hygiene audit (follow-up to §13)

Goal: catalogue every file-scope global, every in-function
`static`, and every hardcoded stack buffer across `src/*.c`, and
classify each as belonging to one of the state buckets the user
already distinguishes — **world state** (authored content
loaded once from `data/world.json5`, immutable after load),
**game state** (the `struct ryl_state state` singleton — party
table, npcs, map cursor, realm/npc save targets), **ephemeral
state** (per-session/per-step values like time-of-day,
steps-left, carrier status), or **process state** (tty
restoration, PRNG, signal flags; legitimately process-scoped,
not game). Anything left over is a smell.

Methodology: `grep -nE '^(static[[:space:]])?...'` for file-scope
decls; `grep -nE '^[[:space:]]+static[[:space:]]'` for
in-function statics; `grep -nE '^[[:space:]]+(char|uint8_t) [a-z]+\[[0-9]+\]'`
for stack char buffers.

### 14.1 File-scope globals

All file-scope decls in `src/*.c`, with classification:

| Module | Symbol | Kind | Classification | Notes |
|--|--|--|--|--|
| `ryl.c` | `struct ryl_state state` | extern | **game state** | The authoritative singleton. By design. |
| `ryl.c` | `char *_version_string` | extern const | **process state** | Build banner literal. Fine. |
| `res.c` | `enums`, `n_enums` | static | **world state** | Fold targets for `enums` subtree. |
| `res.c` | `texts`, `n_texts`, `texts_sorted` | static | **world state** | Fold targets for `texts` subtree. |
| `res.c` | `items[112]` | static | **world state** | Fold target for `items` subtree. |
| `res.c` | `monsters[91]` | static | **world state** | Fold target for `monsters` subtree. |
| `res.c` | `priest_spells[8]`, `mage_spells[8]` | static | **world state** | Fold targets for `spells` subtree. |
| `res.c` | `glyph_tab[256]`, `color_tab[256]`, `color_tab_set[256]`, `color_default` | static | **world state** | Fold targets for `terrain` subtree. |
| `res.c` | `pronoun_tab[3][4]` | static | **world state** | Fold target for `pronoun` subtree. |
| `res.c` | `flags_tab`, `n_flags_tab` | static | **world state** | Fold targets for `flags` subtree. |
| `res.c` | `world_tree` | static | **world state** | Root of the authored node tree. |
| `io_tty.c` | `io_winch_pending` | extern volatile | **process state** | Signal handler flag. Must be extern-visible for the handler to set it. Fine. |
| `io_tty.c` | `saved_termios`, `termios_saved` | static | **process state** | TTY cooked-mode restoration. Correct. |
| `io_tty.c` | `tty_backend` | static | **process state** | Vtable literal for `io`. Const after init. |
| `io_tty.c` | `io` | extern | **process state** | Vtable pointer. Extern-visible because every module calls `io->write/getch`. Fine. |
| `compat.c` | `rng_state` | static | **process state** | Xorshift PRNG. Per-process; replay determinism comes from `RYL_FAKE_SEED`, not from save-game serialization. |

**Verdict.** The only globals that arguably belong in a richer
container are the `res.c` world-state block. That block IS the
payload of the `ryl_world` / `ryl_context` context struct queued
in §12.2 item #1 — the "instance-allocated state" bullet. Rolling
res.c's file-scope globals into `ryl_world *w` (passed to every
`res_*` accessor, or — cheaper churn — kept as a single static
pointer set at load time) lets tests and a future reload path
coexist. This commit does not do that work; §12.2 #1 tracks it.

Nothing unexpected sits outside the four buckets. No loose
integer counters or transient per-encounter state escaping into
file-scope namespace.

### 14.2 In-function statics

| Site | Kind | Classification | Verdict |
|--|--|--|--|
| `compat.c:38-39` in `ryl_delay` | `resolved`, `suppressed` — `RYL_NO_DELAY` env cache | **process state** | Fine. |
| `compat.c:63-65` in `ryl_time` | `resolved`, `fixed`, `fixed_val` — `RYL_FAKE_EPOCH` env cache | **process state** | Fine. |
| `compat.c:162-164` in `ryl_state_path` | `buf[512]`, `base[480]`, `state` int8_t | **process state + returned-static-buffer footgun** | See below. |
| `primitives.c:75` in `getstr` | `char svar[90]` returned to caller | **returned-static-buffer footgun** | See below. |
| `primitives.c:150` in `ryl_pause` | `static const char *verbs[]` | read-only literal | Fine. |
| `primitives.c:212` in `ryl_log` | `static const char *months[13]` | read-only literal | Fine. |
| `res.c:802` in `res_load_world` | `atexit_registered` | one-shot sentinel | Fine (Commit B addition). |

**Two real landmines:**

- **`getstr(uint8_t lg)` returns a pointer to `static char svar[90]`.**
  Every caller must finish using the returned buffer — or copy out —
  before the next `getstr` call, or the second call stomps the
  first's result. This is the classic C returned-static-buffer
  footgun. Fix: change the signature to
  `void getstr(uint8_t lg, char *out, size_t out_sz)`; caller
  owns the buffer. ~15 call sites. Gate byte-identical on replay.
- **`ryl_state_path(const char *leaf)` returns a pointer to
  `static char buf[512]`.** Same footgun, worse because the result
  is a *path* passed into `fopen` — if two nested call sites both
  resolve a path, the inner one stomps the outer one's. Not known
  to be exercised today but fragile. Fix: same pattern —
  `void ryl_state_path(const char *leaf, char *out, size_t out_sz)`.

Both fixes are pure signature/call-site rewrites; no behavior
change; gate on replay byte-identical. Recommend doing these as
their own commits before §12.2 item #1 lands, since #1 might
otherwise rewrite the same call sites again.

### 14.3 Stack buffers worth a look

Of the 43 stack char-buffer sites, most are bounded `snprintf`/`tx`
scratch (`char buffer[512]`, `char str[80]`, `char pos[16]`) and
fine. Three stand out:

| Site | Shape | Issue |
|--|--|--|
| `driv.c:1042-1044` in `show` | `char race[5][15]; char clss[7][15]` then 6 `strcpy`s per call | Rebuilt on every `show()` call. Should be `static const char *const races[]`/`classes[]` at file scope, or (better) read from the `enums` subtree via `res_enum("race", ra)` / `res_enum("class", cl)`. The authored data already has these under `enums`. |
| `driv.c:1028` in `invlist` | `char item[20][80]` — uses slots 1..16, 9 bytes each | 1600 bytes stack; 170 used. Could shrink to `char item[17][10]`. Cosmetic. |
| `subs.c:1142` in `scorelist` | `char pname[26][30], lname[26][30], temp_string[30]; uint8_t cls[26][5], item[26][6], pnr[26]` | ~3100 bytes on stack for a local sort scratch. Swapping 30-byte strings via `strcpy` in a bubble sort is unnecessary — sort an index array (`pnr[]`) and read `state.p[pnr[i]].partyname` at render time. Reduces stack footprint and eliminates 30-byte `strcpy`s that lose data if a name ever hits exactly 30 chars without the current always-NUL-terminated constraint. |

None of the 43 sites currently write past their bounds (every
`strncpy`/`snprintf` I sampled caps correctly), so this isn't a
security audit finding — just cleanup the user has asked for.

### 14.4 Recommended order

Small, replay-gated, standalone:

1. **`getstr` buffer-out-parameter rewrite** (~15 call sites).
   Kills the biggest footgun. One commit.
2. **`ryl_state_path` buffer-out-parameter rewrite** (fewer
   callers, same pattern). One commit.
3. **`show()` race/class tables → `static const char *const`
   or `res_enum(...)`** — small, isolated. One commit.
4. **`scorelist()` sort on indices, not string swaps** —
   self-contained cleanup inside one function. One commit.

Larger, deferred under §12.2:

5. **`ryl_world` context struct** folds the whole res.c
   file-scope block (§12.2 #1). Needs a design pass on whether
   to keep `res_*` as free functions with an implicit static
   context pointer, or thread `ryl_world *w` through every
   call site. The latter is cleaner but a bigger diff.

Nothing on this list is load-bearing for Commit C (ASan leg)
of §12.2 — ASan catches leaks/UAF/overflow regardless of how
the state is shaped. These are hygiene commits that can slot in
before or after C as the replay-gate schedule allows.

## 15. Realm persistence: current-world.json5 (concrete §12.3 design)

Captured 2026-04-22. Turns the save-file license from §12.3 into a
concrete file/schema/algorithm design; replaces the existing
`realm.dat` + `npc.dat` binary blobs with a single human-readable
JSON5 artifact.

### 15.1 Two-tier file model

Today the persistent-state story is: `data/world.json5` (spec,
read-only) + `$HOME/.rylween/{realm.dat, npc.dat, entry.dat}` (three
opaque binary blobs that mix world realization with per-party
state).

Collapse to two tiers:

| Tier | Path | Writer | Semantics |
|--|--|--|--|
| **Spec** | `data/world.json5` (shipped) | never at runtime | immutable |
| **Current realm** | `$HOME/.rylween/current-world.json5` | cold-start realization + every `save()` | absent ⇒ sysop reset |

Everything that survives across game sessions — NPC placements,
rolled town/dungeon coordinates, every party's roster + inventory +
progress flags + per-NPC visited bits — lives in
`current-world.json5`. One file. One source of truth. `rm` it to
reset.

`entry.dat` (the daily per-player-name replay-cap file in
`driv.c:565/592`) can either fold into the same artifact (an
`entries:` array keyed by date) or stay out as a throw-away
per-day bucket. Lean toward folding in — keeps the "sysop reset =
one `rm`" story clean.

### 15.2 Detect-and-realize on boot

```
init():
  stat($HOME/.rylween/current-world.json5)
  ├─ absent  → realize_world()
  │             → write current-world.json5
  │             → continue with antal_spelare = 0
  └─ present → parse current-world.json5 into state.*
               → continue
```

`realize_world()` replaces today's `position_locations()` +
`place_npcs()`:

1. Read `data/world.json5` `npcs:` subtree (see §15.3).
2. For each NPC with `towne:` explicit → honor it.
3. For each NPC with `towne:` absent → run a **balanced shuffle**
   (§15.4) against the towns list from `data/world.json5`.
4. For each NPC with `towne: 0` (wilderness) → random `{x, y}`
   with existing hermit-collision and map-type guards.
5. Roll town/castle/catacomb coordinates (`state.dx[]` /
   `state.dy[]`), same logic as today's `position_locations()`.
6. Write the result to `current-world.json5`.

Post-realization, `state.antal_spelare == 0` (no parties yet);
first party added via menu `C` → `createparty()` triggers the
first real `save()`.

### 15.3 Declarative NPC entries in world.json5

Move the 40-slot hand-coded block in `person.c:86-178` into
`data/world.json5`:

```json5
npcs: {
  // Fixed NPCs: towne/bl always present; x/y only meaningful
  // when towne == 0 (wilderness).
  garth:     { slot:  1, job: 1,  towne: 1, bl: 1 },
  innkeep1:  { slot: 25, job: 14, towne: 1, bl: 2 },
  innkeep2:  { slot: 26, job: 14, towne: 2, bl: 2 },
  // ... tkeepers 29-32, bargirls 34/35 ...
  hermit:    { slot: 33, job: 16, towne: 0, x: 48, y: 7 },

  // Random NPCs: no towne/bl/x/y. The realizer rolls them.
  // job cycles 1..12 across the 24 random slots (two per job),
  // matching the current person.c:48 j-chain behavior.
  wanderer_1:  { slot:  2, job:  2 },
  wanderer_2:  { slot:  3, job:  3 },
  // ...
},
```

Two surprises in today's code to surface explicitly as design
decisions, not accidents:

- **Bargirls only in towns 1 and 3** (no 2, no 4). Today this is
  an artifact of how the fixed block is written. If it's
  intentional, the world.json5 entries lock it in; if it's a bug,
  now's the time to add bargirls 36/37 for towns 2/4.
- **NPC 1 is randomized then overwritten.** The random loop runs
  for `i=1..24`, then the Garth block at `person.c:88-93`
  clobbers slot 1. The new shape makes Garth an explicit fixed
  NPC (`slot: 1`) and the random loop starts at slot 2; the dead
  code disappears.

### 15.4 Balanced-shuffle heuristic

For the N random NPCs across T towns (today N=24 mostly, T=5), a
uniform `ryl_random(0, T-1)` has σ ≈ √(N·(T-1)/T²) ≈ 1.96 per
bin, so single-realm variance regularly produces 8-in-one-town /
2-in-another. The memory behind this port: "heuristic so not too
many are in one particular town".

Recommended algorithm — **capacity-capped draw**:

```
target = N / T                   # e.g. 24/5 = 4 (+ 4 leftover)
cap    = ceil(target) + slack    # slack=1 ⇒ max 6 per town
counts = [0] * T

for each random NPC in declaration order:
  towne = ryl_random(0, T-1)
  while counts[towne] >= cap:
    towne = (towne + 1) % T      # linear probe to next eligible
  counts[towne]++
  npc.towne = towne
```

Linear-probe keeps the roll order deterministic relative to the
seed, so RYL_FAKE_SEED still produces reproducible realms. Slack
is tunable in `world.json5` (e.g. `limits.npc_slack: 1`).

Alternative: Fisher-Yates over a multiset `[t×cap for t in
0..T-1]` truncated to N. Cleaner, but loses the "draw in
declaration order" property that makes realization traces
readable — the capacity-cap version prints a diff that's easy to
eyeball-verify against seed+data.

### 15.5 Per-party state → current-world.json5

`state.p[i]` is today serialized via `fwrite(&state.p[i],
sizeof(state.p[i]), 1, fh)` — a raw struct blob written to
`realm.dat`. Every field becomes a named JSON5 key under
`parties[].p` (`name`, `partyname`, `hp`, `food`, `gold`,
`inventory`, `wp`, `ar`, `dead`, `cl`, `ra`, `sex`, `h`, `ws`,
`wi`, `ch`, `lv`, `exp`, `ma`, `pl`, `tid_kvar`, `kills`,
`fireshield`, `bracelets`, `mastercrown`, `silverkey`,
`jazzyfoot`, `y`, `x`, `player`, `flag`).

The `state.npc[i].visited[party_idx]` grid **moves out of the
NPC subtree** into each party's own block as `visited_npcs:`
(40-element bool array indexed by NPC slot). Side effects:

- `driv.c:670-675`: the index-shift dance when deleting a party
  ("shift visited[] column down") vanishes — each party owns its
  own slice, deleting a party just drops an array element.
- NPC identity (placement) is realm-scope, unchanged across saves.
  Party visit state is per-party, rewritten on every save.
- `state.p[i].flag[]` (mission flags) stays per-party as today.

### 15.6 Top-level schema

```json5
{
  // Cold-start roll: town/castle/catacomb coordinates (legacy
  // state.dx[1..14] / state.dy[1..14]).
  locations: {
    towns:     [{ x: 12, y: 8 }, ...],        // indices 1..N_TOWNS
    castle:    { x: .., y: .. },
    catacombs: [{ x: .., y: .. }, ...],
  },

  // Realized NPC placements. Indexed by slot to preserve legacy
  // `state.npc[i]` semantics; entries are populated from the
  // world.json5 `npcs:` spec after realize_world() runs.
  npcs: [
    null,                                      // slot 0 unused
    { slot: 1, job: 1, towne: 1, bl: 1 },      // Garth
    // ...
  ],

  // All parties that have ever entered this realm.
  parties: [
    {
      partyname: "The Bold",
      leader:    "Grinsby",
      // ... full state.p[i] payload ...
      visited_npcs: [false, true, false, ...], // 40 bools
    },
    // ...
  ],

  // State.senaste_dagen + anything else realm-scope ephemeral.
  realm: {
    last_played_day: 17,
  },
}
```

`state.antal_spelare` is derived from `parties.length` — no
separate count field. Same for NPC count.

### 15.7 Implementation slicing

Replay-gated commit order:

1. **Schema + writer** — add `res_write_current_world()` that
   serializes `state.*` to the new path. Keep reading from
   `realm.dat`/`npc.dat` for now. Gate: replay byte-identical
   (no read-path change yet).
2. **Reader** — add `res_load_current_world()`; switch `init()`
   to read from `current-world.json5` if present, fall back to
   `realm.dat`/`npc.dat` if absent (migration-friendly). Gate:
   replay byte-identical on both branches.
3. **Realizer rewrite** — move NPC spec from `person.c` to
   `data/world.json5` `npcs:`, implement `realize_world()` in
   `res.c`, delete `place_npcs()`. Gate: replay byte-identical
   against the original random-seed tests (RYL_FAKE_SEED pins
   the roll, so the realized placements are deterministic).
4. **Balanced shuffle** — swap uniform `ryl_random(0, T-1)` for
   capacity-capped draw. Gate: replay **not** byte-identical
   (this is a behavior change); rebless fixtures under the new
   distribution and document in the commit.
5. **Drop legacy readers** — delete `.dat` read paths from
   `init()` / `save()` / `load()`, delete the index-shift dance
   for party deletion. Gate: replay byte-identical.

Steps 1-3 are pure refactor (byte-identical); step 4 is the one
behavior change and gets its own rebless. Step 5 is the cleanup
after the new path is load-bearing.

### 15.8 Open questions

- **Name.** `current-world.json5` is descriptive; alternatives
  `realm.json5` / `campaign.json5` / `save.json5`. Settle before
  step 1.
- **Pretty-print vs. compact.** JSON5 output with 2-space indent
  is readable (50kb-ish for 25 parties) but slow to rewrite.
  Compact one-line is 10x faster to write but unreadable. Start
  pretty; switch to compact only if profiling shows save() in the
  hot path.
- **Backward-compat with existing `.dat` blobs.** Per §12.3
  user-granted save-file license (2026-04-22), drop them on
  first run of the new build. No migration code.
- **Stable NPC ids.** The `slot` field mirrors today's
  `state.npc[i].nr`. If a future world.json5 edits add/remove
  NPCs, do existing `current-world.json5` files still
  round-trip? Probably not — a changed spec should be treated as
  a version bump that triggers a sysop-reset prompt.

## 16. Dialogue + mission DSL (follow-up to A.2 speak.c)

Raised during the A.2 speak.c text-extraction session (2026-04-22).
The pattern that emerged — `tx0(res_text(...))` chains interrupted
by format-bearing `tx("%s...", ...)` tails, `_pre`/`_post` key
splits, hardcoded `clw()`+`ryl_pause()` boundaries, and inline
reward/flag mutations — points at a declarative "screen script"
DSL that should replace the handwritten C flow eventually. Five
deficiencies to address, in rising order of ambition:

### 16.1 Shared menu-prompt / keypress dispatcher

Every dialog screen that takes input follows the same shape:
a text block, a set of allowed letters (often with ESC/B/Q as
default escape), a single `key()` + `toupper()`, and a branch per
letter. Examples today:

- `speak()` main: `(C)hitchat / (Q)uestion / (R)umors / (B)ye`
- `speak()` Q-submenu: `(L)odging / (M)agic / (P)eople / (N)othing`
- `silver_key` + `thun` maps: `(B)uy it / (N)o thanks`
- Lodge j13 (whore branch): yes/no via `common.yes`/`common.no`
- Castle mage: `(T)eleport / (G)ive item / (L)eave`
- Stairs treasure: `(T)ake / (L)eave`
- Countless y/N confirmations across driv.c / dungeon.c

Propose a single helper:

```c
typedef struct {
    char        letter;      /* 'Y', 'N', 'B', 0 for wildcard */
    const char *label;       /* text-id for the "(X)etc" row, or NULL */
    int         result;      /* returned to caller */
} ryl_menu_opt;

int ryl_menu(const char *screen_text_id, const ryl_menu_opt *opts);
```

which emits the screen, prints each option row, calls `key()`,
and returns the `result`. Escape/cancel handling lives in one
place. The option list is either inline C or — better (§16.4) —
shipped in the same JSON5 entry as the screen text.

### 16.2 Dynamic enum tables (kill `subjects[3]`)

`reply_chitchat` currently carries a tiny hardcoded C lookup:

```c
static const char *const subjects[3] = {"well", "weather", "health"};
txi("speak.chit.reply.%s.j%d", subjects[subj], jb);
```

These are three strings that already appear as keys in
`speak.chit.reply.*` and as code-side guards for the f/m prompt
variants. Two ways to de-hardcode:

1. **Ordered string-list resource.** Add a `res_string_list(id)`
   that returns a `const char * const *` pointing at an N-sized
   table authored as a JSON5 array. `subj_name = list[subj]`.
2. **Text-record convention.** Author
   `speak.chit.subjects.s0 / .s1 / .s2` as plain strings (fold_texts
   already supports strings at leaves). `res_text("speak.chit.subjects.s0")`
   yields `"well"`. Drop-in replacement with no new loader kind.

Option 2 is cheaper (no new resource kind) and mechanical;
option 1 is cleaner if we grow many such tables (mission-reward
item-lists, town shop inventories, etc.). Prefer option 1 if and
only if §16.5 lands, since mission-reward lists will want the
same facility.

### 16.3 Embedded format parameters (kill `_pre`/`_post` splits)

Every `*_pre` / `*_post` key pair in speak.json5 exists because
the line sitting between them contains a `%s` or `%dx%d` that can
only be resolved at runtime. Today that forces the call site
into three calls and leaks the layout into C:

```c
tx0(res_text("speak.ranger.deliver_pre"));
tx("to my brother, %s,\r\n", res_enum("name", 14));
tx("who lives %s,\r\n", str);
tx0(res_text("speak.ranger.deliver_post"));
```

The text should be one continuous record with named placeholders:

```json5
deliver: [
  '- If you deliver this',
  'to my brother, {ranger_name},',
  'who lives {ranger_loc},',
  'he will give you',
  'a great weapon!',
  '',
],
```

and a `tx_formatted(id, ...)` that resolves `{ranger_name}` /
`{ranger_loc}` from a caller-supplied binding table. The binding
table is a small `{ "ranger_name": "res_enum.name.14",
"ranger_loc": "$str" }` map — literals, `res_enum` references,
or C-local strings bound at call time. This kills every
`_pre`/`_post` split we've got in driv.json5, ydarg.json5,
speak.json5 and eliminates the coupling between text layout and
C control flow. Bindings become part of the "script" (§16.4)
rather than hand-written C.

### 16.4 Screen-script DSL (pause + clw markers)

NPC dialogue is a chain of screens separated by `ryl_pause()` +
`clw()`. Today that's:

```c
clw();
tx0(res_text("speak.erethor.rumour"));
ryl_pause();
clw();
tx0(res_text("speak.erethor.demon"));
ryl_pause();
clw();
...
```

Authoring this in JSON5 as a chain — where `'<PAUSE>'` or a
dedicated object element marks a page-break — gets rid of the C
boilerplate and makes the screens co-located with their text:

```json5
erethor: {
  first_visit: [
    { screen: 'rumour' },      // tx0 + ryl_pause + clw
    { screen: 'demon' },
    { screen: 'poor_man' },
    { screen: 'trade' },
    { screen: 'dangerous' },
    { journal: "Erethor's temple is situated at 6x20." },
    { set_flag: 8, value: 1 },
  ],
  delivered_lute: [ ... ],
},
```

A small interpreter walks the list, dispatching `screen` →
`tx0(res_text("speak.erethor.<name>")) + ryl_pause + clw`,
`journal` → `journal(...)`, `set_flag` → `flag[n] = v`. The case
body in `npc_mission_hint` collapses to one call:
`play_script("speak.erethor.first_visit");`.

### 16.5 Mission state machines + rewards as data

Most of `npc_mission_hint` is: "when flag[N] is at stage S, emit
these screens, add gold G, drop items I1..Ik into inventory,
advance flag[N] to S+1". Today every stage hardcodes the reward
math in C (see `case 19` husband-reward: `+= 3000` gold and five
inventory slots patched to items 26/110/38/101/45). A natural
extension of §16.4 is to attach these side-effects to the script:

```json5
husband: {
  rescued: [
    { screen: 'reunion' },
    { screen: 'gives' },
    { reward: { gold: 3000, items: [26, 110, 38, 101, 45] } },
    // The "You can't carry all the items" fallback text is part
    // of the reward-failure path — the interpreter emits it when
    // drop_option() doesn't free enough slots.
    { set_flag: 19, value: 3 },
  ],
},
```

with a unified `apply_reward()` that walks the item list, tries
to carry each (respecting inventory slot limits), invokes
`drop_option()` + the "can't carry" dialogue when full, and
tallies gold. Items 26/110/38/101/45 stop being bare integers
and become symbolic ids resolved through `res_item`.

### 16.6 End state

With §16.1–§16.5 in place, `speak.c` loses ~90% of its body.
`flaggkoll`/`npc_mission_hint`, the chitchat switches, and the
entire P/M/L fan-out become tiny dispatchers that look up a
script by key and call `play_script`. The engine's hand-written
C is limited to primitives: `key()`, `clw()`, `ryl_pause()`,
`drop_option()`, inventory slot search, gold arithmetic. All of
`towne.c` (A.3, 135 tx() sites with shop/inn/tavern menus),
most of `missions.c`, and the pause-heavy screens in `driv.c` /
`ydarg.c` reduce the same way.

### 16.7 Slicing

Order matters because later items build on earlier ones:

1. **§16.1 menu helper.** Self-contained, no data-format
   changes, lands with A.3 towne.c since that's where the
   shop/inn menus will multiply the savings.
2. **§16.3 embedded `{placeholders}` + binding table.** Needs
   a tiny expander on top of `res_text`. Retroactively kills
   every `_pre`/`_post` pair already shipped (driv.json5,
   ydarg.json5, speak.json5). Pure win; no new concepts for
   consumers.
3. **§16.4 screen-script interpreter.** Builds on §16.3. Lands
   as a new `res_script` resource kind and a `play_script()`
   emitter. Ship alongside an A.2 follow-up that migrates
   speak.c's mission cases to scripts.
4. **§16.2 dynamic enum tables.** Either folds into §16.3's
   binding table (if literals can reference resources) or lands
   as a new `res_string_list`. Don't do this standalone — pick
   the shape once §16.3 is concrete.
5. **§16.5 reward dispatcher.** Needs symbolic item ids, which
   means §16.4 scripts can reference items by name. Gates on a
   small `res_item_id("ruby") -> 39` helper first.

### 16.8 Things to watch

- The interpreter must not grow into a general scripting
  language. Keep the op list flat: `screen`, `pause`, `clw`,
  `journal`, `set_flag`, `reward`, `menu` (for §16.1). No
  arithmetic, no loops, no conditionals except flag-guarded
  branches (which the outer dispatcher already handles by
  picking the right script by flag value).
- Bindings (§16.3) must be resolvable at script-emit time, not
  stored globally — the caller passes a transient map. Otherwise
  we end up with a mutable global "dialogue context" which is
  exactly the kind of thing port-plan §5b is trying to kill.
- Replay fixtures only cover driv.c paths today. A.2 was
  collapsed fixture-blind; §16.3's retroactive kill of
  driv.json5 `_pre`/`_post` pairs **will** need a rebless even
  though the on-terminal output should be byte-identical (the
  CSI-right jumps around the %-format tx()s may shuffle). Plan
  on that or carefully audit the byte stream first.
- `ryl_state.global_x` (the CSI-right prefix state) interacts
  with how `tx0` vs. multiple `tx()` calls emit cursor jumps.
  Any helper that merges screens must preserve or deliberately
  drop the per-line jumps — document the choice in the §16.4
  interpreter.

## 19. Post-§17 follow-ups surfaced during the hardcoded-indices sweep

Captured 2026-04-24 after the §17-T..§17-Z commits closed P1-P3
and P5-P6 on `docs/hardcoded-indices.md`. These three items are
structural, not mechanical symbol swaps, and each wants its own
scoped pass. Sequence them D → B → C: fixture coverage first so
subsequent refactors are caught by `make test` instead of eye-
balling replay output.

**Status (2026-04-24).** D starter bank, B, and C all landed.
Sequence D → B → C complete.

- §19.1 (D) — starter bank of 11 Thord-centred fixtures landed.
  Broader coverage (other towns, huts, remaining mission stages)
  deferred; see §19.1 for the list.
- §19.2 (B) — per-party in-memory journal with save/load, all
  four `journalf` / `do_journal` / `eval.c journal` write paths
  migrated, `data/journal` file retired. Gated by a new
  silver-key-map replay fixture (fixture bank grew to 15).
- §19.3 (C) — data-driven party size landed in four step-commits
  plus a Makefile prelude. Per-member arrays on `struct party`
  are now heap-allocated off `res_party_member_max()`;
  `limits.party_member_max` in world.json5 overrides the
  default (with a `RYL_PARTY_MEMBER_MAX` env shim for fixtures).
  Save/load is strict-match on a new `realm.party_member_max`
  header. UI bounds use `pm_digit_in_range()` and a `{party_max}`
  engine-provided text binding. Gated by all 15 existing
  fixtures byte-identical at the default N=4 plus one new :5
  smoke fixture (bank now 16).

### 19.1 (D) Town-level replay fixtures — starter bank landed (2026-04-24)

Landed in `cc92304` (§19-D) and `822b456` (§19-D.2). Eleven
Thord-centred fixtures cover every building menu plus the four
parametric rumour branches and one full mission_dispatch stage:

    towne-thord-enter-leave        bare town enter/leave
    towne-thord-merchant           M + L on merchant (bye path)
    towne-thord-smith              S + L on smith (bye path)
    towne-thord-church             C + L on church
    towne-thord-inn                I + N/N/N + L (decline all)
    towne-thord-tavern-rumour      T + R + L (seed-42 static rumour)
    towne-thord-tavern-rumour-r12  force r12, Garth / bandits_quest_giver
    towne-thord-tavern-rumour-r13  force r13, Nrim'to / rangers_quest_giver
    towne-thord-tavern-rumour-r25  force r25, Broth / crown_seer + npc_loc_str
    towne-thord-tavern-rumour-r27  force r27, Harry / temple_of_thun_seer
    towne-thord-tavern-speak       T + S, pick Garth, run first_visit
                                   stage, confirm
                                   `flags=garth.bandits=accepted`

Infrastructure landed with the bank:

- `RYL_REPLAY_RUMOUR=<id>` env hook in `towne.c rumour()` — pins
  the rumour roll so the parametric fixtures reach the exact
  branch they name. No-op when unset; production never sets it.
- **Bug fix**: `cleanexit()` in `primitives.c` used to call
  `res_shutdown()` explicitly before `exit(0)`, freeing
  `flags_tab` before the atexit-registered `snapshot_write`
  ran. Any fixture with a non-zero flag slot emitted
  `flags=?=?` from freed memory. Drop the explicit call —
  atexit covers it, LIFO order then puts snapshot_write
  before res_shutdown. Surfaced by the speak-garth fixture.

Deferred from §19-D (noted but not landed):

- **Other towns** (Ollk/Wilor/Murn). From the spawn point
  (12,9) these are 36+ move paths, each move rolling
  `random_wilderness`. Seed 42 is clean to Thord (2 moves)
  but longer paths pick up caves/encounters that drag the
  input stream out of phase. Options: (a) find a seed where
  the wilderness stays quiet on the target path;
  (b) canned `current-world.json5` that places the party on
  the town tile and skips the travel; (c) `RYL_REPLAY_HOOKS`
  style gate to force `random_wilderness` quiet during
  replay.
- **Hut visits** — same long-path problem. Marcus at (48,7)
  would give a mission_dispatch into `hermit_troll_tooth`.
- **Other parametric mission stages** — only
  `stages_merchant[0]` is covered. Every other entry in
  the §17-Z `mission_table` (14 role-bound NPCs, ~25
  stages) is still untouched by fixtures.

**Problem.** `tools/replay/` had exactly three fixtures:
`title-create-party-combat`, `title-create-party-exit`,
`title-menu-exit`. All three stop at the title menu or combat
initialization. There is **no coverage** of:

- Entering a town (Thord / Ollk / Wilor / Murn).
- Inn sleep / tavern rumour / shop buy/sell / smith repair.
- `speak()` — the entire chitchat / rumour / Q&A / mission_dispatch
  flow for every NPC.
- Quest progression: flag transitions end-to-end (none of the
  `pa_flag_set` sites in §17-S/Z fire in the current fixtures).
- Coordinate reveals (silver-key pit, Temple of Thun, Beast Cave,
  hermit dungeon, Crown chain).
- Journal writes (see §19.2 — both the 4 `journalf` sites and the
  ~11 `{ journal: ... }` DSL ops would benefit).

The §17 refactors ran green on three tiny fixtures because those
paths don't touch speak.c / towne.c / missions.c / dungeon.c quest
logic at all. Behavior-preserving claims stay optimistic until the
fixture net catches them.

**What to build.** A town-interaction replay bank, each fixture
scripted to enter a town, step a specific NPC flow to a
well-defined terminal state, and snapshot both the transcript and
the `state` dump. Starter set:

- `towne-thord-merchant-bandits` — Garth: rumour → accept → camp
  loop (flag `garth.bandits` goes `none` → `accepted`).
- `towne-thord-ranger-letter` — Nrim'to: accept ranger quest +
  brother sword delivery (`ranger.letter` chain).
- `towne-ollk-priest-book` — Gorm → Nevaak → Areth handoff on
  `fighter.priest_book`.
- `towne-wilor-silver-key-map` — Bernak map buy (`silver_key` →
  `map_bought`, journal entry for Pit coords).
- `towne-murn-jazzy-ogre-trade` — Leander invlist item trade
  (`jazzy_ogre` → `accepted`, journal "Beast Cave" entry).
- `towne-rumours` — rumour bank sweep: R_* deterministic via a
  seeded `ryl_random` driver so every parametric rumour
  (bandits/hunter/crown/temple) exercises its binding and
  `res_npc_role` / `npc_loc_str` path.
- `towne-hermit-troll-tooth` — Marcus tooth trade and Fire Shield
  reveal (`hermit.troll_tooth` chain).

**Infrastructure gaps to fill first.**

- `replay.sh` drives the game via `stdin` + an `.env` for seeds.
  The current seed knob is coarse (one `RYL_SEED`); rumour-bank
  coverage wants either per-invocation seeds or a per-rumour
  deterministic path. Cheapest: add a "rumour debug" dev-only
  override that forces `pick` so the fixture covers a specific
  rumour id. Guard it behind `#ifdef RYL_REPLAY_HOOKS`.
- Some NPC flows gate on being at the NPC's towne tile
  (`speak()` looks up `state.npc[i].x/y`). Fixtures will need a
  deterministic realizer seed so NPC placements are stable.
  Currently `res_realize_npcs` runs from scratch on first boot;
  a fixture-mode flag that loads a canned `current-world.json5`
  instead of realizing is cleanest. This overlaps with §15.

- `.expect_snapshot` diff today is whole-file; per-fixture we
  might want to snapshot only the `[party 1].flags=` /
  `[party 1].journal=` fields rather than the full map blob, so
  a trivial NPC realizer tweak doesn't invalidate every town
  fixture. `tools/replay.sh` supports this today if we pass a
  subset list.

**Sequencing inside D.** Land infrastructure (seeded rumour pick,
canned realizer load) as one commit. Then one fixture at a time
so each landing gives a clean `make test` and a reviewable diff.

### 19.2 (B) Journal into party state / save — landed (2026-04-24)

Landed in four step-commits (§19.2.1..§19.2.4):

- `12ceb3e` §19.2.1 — plumbing: `char **journal`, `journal_n`,
  `journal_cap` on `struct party`; `party_journal_add`
  (case-insensitive dedup, matches the file-backed `stricmp`
  predecessor); `free_journal` tears down. No callers migrated
  yet, file-backed `journal()` still authoritative.
- `efd95ba` §19.2.2 — migrate every writer: `journalf` vsnprintf's
  and delegates to `party_journal_add(party_current(), …)`; DSL
  `do_journal` in script.c and the effect-DSL `journal` op in
  eval.c both stop composing the cpl prefix + `\n`; three
  `speak.c` sites drop the `"%d "` and `"\n"` from their format
  strings. `journal()` / `clrjr()` and the `data/journal`
  file-open loops are gone. `read_journal` iterates the in-memory
  list. Party-terminate flow calls `free_journal` before the
  memmove that shifts higher slots down, then zeros the
  duplicated tail pointers so save sees no aliased journals.
- `d708b61` §19.2.3 — persist through save: `write_current_world`
  emits `journal: ['line', ...]` per party; `read_current_world`
  `free_journal`s and re-appends each entry (missing key →
  empty list, so pre-§19.2 saves load clean).
  `snapshot.c emit_party` adds a `journal_n` header plus
  `journal[i]=<line>` rows, gated on `journal_n > 0` so every
  pre-§19.2 fixture baseline stays diff-clean.
- `1e33da3` §19.2.4 — gate: new fixture
  `towne-thord-tavern-bernak-map`. Seed 1 keeps Bernak in Thord's
  inn (default seed 42 shuffles him to Wilor, forcing a 42-square
  overland detour); tavern → S → C reaches him. A new test-only
  env hook `RYL_STARTING_GOLD` preloads the bank (clamped
  `>= 1000`, production never sets it, same shape as
  `RYL_REPLAY_RUMOUR`). Snapshot asserts
  `journal[0]=The Pit of the Silver Key is at 8x8`.

**Behaviour deltas from the file-backed shape.**

- Journals no longer cross parties on the same machine. The
  `"%d "` cpl prefix was a display-time quirk of the shared file;
  with per-party storage the filtering is automatic. This is the
  intended fix, not a regression.
- Dedup remains case-insensitive (`stricmp` parity). Tightening is
  a follow-up if we want it.
- `driv.journal.header` text is still authored as-is.

**Build-system footgun surfaced.** The Makefile has no header
dependency tracking, so editing `state.h` and running `make build`
only rebuilds the `.c` files whose source changed — the rest link
against the old struct layout. During §19.2.2 this produced the
"HP 68/64" phantom: `snapshot.c` and `statusline()` read from
stale offsets after `struct party` grew. `make clean && make
build` fixed it. Worth a proper `-MMD -MP` pass in a future
infrastructure commit; for now, a clean rebuild after any
`state.h` change is the mitigation.

### 19.3 (C) Data-driven party size — landed (2026-04-24)

Landed in five commits off `lagergren/port1`:

- `87b6d21` — prelude: Makefile `-MMD -MP` header deps so
  `state.h` edits trigger full rebuilds (retires the §19.2.2
  "HP 68/64" phantom footgun).
- `af288ed` §19.3.1 — heap-allocate per-member arrays on
  `struct party`. `char (*name)[30]`, `int32_t *ws`,
  `int32_t *h`, `*hp`, `*wi`, `*ma`, `*ch`, `*cl`, `*ra`,
  `*lv`, `*ar`, `*wp`, `uint32_t *exp`, `int32_t *dead`,
  `unsigned char *gender` — all sized off
  `res_party_member_max() + 1` at realize/load. Three new
  helpers in `party.c`:
  - `party_realize(p)` — allocate every member buffer zero-
    filled; idempotent (frees any prior buffer first).
  - `party_release_members(p)` — symmetrical free.
  - `party_clear_heap_pointers(p)` — NULL every heap pointer
    (members + journal) without freeing; called on the
    terminate-memmove tail so the duplicated pointer in the
    two slots doesn't alias.

  Hooked at `createparty` (after `num_parties++`),
  `read_current_world` (per party before the per-member
  `rcw_i32_arr` reads), and the terminate path (before the
  `memmove`, with tail-clear afterwards). Journal lifecycle
  stays independent — `free_journal` is load-called to clear-
  before-reappend, and `party_release_members` deliberately
  doesn't touch it.

- `63c40d8` §19.3.2 — save/load thread member_max. Writer
  emits `realm.party_member_max = res_party_member_max()` and
  every per-member array at dynamic length. Reader does
  strict equality against the current `limits.party_member_max`
  and `exit(1)` with a descriptive diagnostic on mismatch
  (no production saves exist, so reshape-on-load isn't worth
  the complexity). `res.c`'s `fold_limits` grew a
  `party_member_max` knob with min 1, no upper clamp.

- `b16e5e4` §19.3.3 — UI bounds dynamic.
  `pm_digit_in_range(c)` inline helper in state.h replaces
  every `c >= '1' && c <= '4'` guard across driv.c / battle.c /
  dungeon.c / subs.c / ydarg.c. The "(1-4)" prompts also
  dynamize:
  - C-hardcoded `tx(...)` literals grow `%d` +
    `res_party_member_max()`.
  - res_text-backed prompts (`driv.view.who`,
    `battle.prompt.on_whom`, `ydarg.nav.view_who`,
    `towne.*.who`, `towne.tavern.story_who`) get
    `{party_max}` placeholders; callers switch from
    `tx0(res_text(...))` to `tx_fmt(..., NULL)` since
    `bind_expand` now auto-provides `party_max` and
    `inventory_max` as engine-level bindings (caller
    bindings still win). DSL `emit` already routes through
    `tx_fmt` → the text edits pick up the new binding with
    no script changes. `key_range: ['1','4']` literals in
    DSL scripts stay as-is (deferred per
    script.c:1527's existing annotation).
  - `pm_t`'s `int m; /* 1..4 */` → `/* 1..N, where
    N = res_party_member_max() */`, and `pm_of`'s bounds
    assert now checks the data knob.

- `47db2bc` §19.3.4 — synthetic :5 smoke fixture
  `title-create-party-5-exit`, run under
  `RYL_PARTY_MEMBER_MAX=5` (a new env-override shim on
  `res_party_member_max` that wins over the limits: knob and
  the #define default — convenient for a single-fixture flex
  without authoring a parallel world shard). Fixture pulls 5
  member blocks off the seeded RNG stream and byte-matches
  `title-create-party-exit`'s first 4 rolls. Two createparty
  stragglers from §19.3.3's sweep also fold in: the stat-roll
  `while (g < 5)` → `while (g <= res_party_member_max())`,
  and `state.p[a].pl = 4;` →
  `(unsigned char)res_party_member_max()`.

  Snapshot.c's per-member dump loop switches from
  `PARTY_MEMBER_MAX` to `res_party_member_max()` so the
  replay snapshot grows with N; the default-4 fixtures stay
  byte-identical.

**Fixture bank.** 16 fixtures total (15 pre-§19.3 + the new
:5 smoke). `make test` runs every fixture at the default
N=4; all 15 pre-§19.3 fixtures are byte-identical; the :5
fixture is a true smoke test (byte-identity not required per
design — just that the dynamic path doesn't wedge).
`make testasan` is also green across all 16.

**Watch-outs (stand at §19.3 close).**

- DSL `key_range: ['1','4']` literals in
  `data/world/scripts/towne.json5` stay hardcoded; the DSL
  doesn't yet express "char range over
  `res_party_member_max()`". Three or four sites. For N > 4
  the player has to press '1'..'4' (the extra slot isn't
  reachable via those menus). See script.c:1527. Lift if a
  prompt ever needs a non-1..4 range or the smoke test grows
  to exercise in-town flows.
- Story / combat / damage tables still bake 4-member
  assumptions in subtle places (level-up ladders, spell
  target counts, specific HP thresholds). Don't bump the
  default. N != 4 is experimental.
- Save-format break is strict: any save authored with a
  different `limits.party_member_max` refuses to load. The
  error message points the sysop at the world knob. No
  reshape path is planned — this would matter if/when
  production saves become a thing.
- The createparty flow only populates 1..N; existing class-
  balance checks on the starter screen (WS >= 50 for fighter,
  etc.) apply identically at every slot, so a party of N=5
  still has to roll stats that pass each member's chosen
  class. Nothing new here beyond what N=4 already required.

