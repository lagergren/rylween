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


## 22. DSL unification plan (audit 2026-04-25)

Earlier subsections complete; outstanding work below (§22 itself closed per the §22.14 note inline; the embedded §23 lookahead at lines 4820-5015 is the active engine→data extraction queue and stays in this file).

Goal: a reader who has never seen the C source should be able
to reproduce the entire game by reading the data shards alone.
Today the data is partway there — `data/world/items/`,
`data/world/monsters/`, `data/world/scripts/`,
`data/world/texts/` already cover most of the gameplay surface
— but multiple inconsistencies and parallel mechanisms get in
the way. This section catalogues every place the DSL fails the
"same patterns look the same" test, with a sequenced plan to
collapse them.

The principle, restated: **one DSL, one expression model, one
binding model, one engine registry, one set of enum records
keyed by lowercase symbol with a `name:` field for display**.
The shards are the program; the engine is just the runtime.

### 22.1 Audit findings (sorted by leverage)

#### Tier 1 — different DSLs for similar things

The single biggest thing in the way of "read the data alone"
is that there are three independent DSLs in the data tree, with
incompatible syntaxes, vocabularies, and evaluators.

  1. **Script DSL** — `src/script.c` (~3000 lines), object-shaped
     JSON5. Owns dialogue / mission / town-service / dungeon
     scripts. Vocabulary: `{ if: { cond: ..., then: [...] } }`,
     `{ mutate: { target: ..., op: ..., value: ... } }`,
     `{ engine_call: 'name', args: [...] }`,
     `{ for_member: { var: 'm', then: [...] } }`,
     `{ switch: { expr: ..., cases: [{match: V, then: [...]}] } }`,
     etc. Expression grammar: `{ add | sub | mul | div: [a,b] }`,
     `{ eq | ne | lt | gt: [a,b] }`, `{ var: 'name' }`,
     `{ member: <expr>, field: '<name>' }`, etc. This is the
     mature, growing surface — driven by the L2/L7/§17-F arc.

  2. **Spell DSL** — `src/eval.c` (~1350 lines), Lisp-style
     S-expressions. Owns spell effect bodies under `do:`.
     Vocabulary: `['seq', ...]`, `['if', cond, then]`, `['set',
     '$dmg', expr]`, `['*', 'caster.lv', ['rand', 2, 8]]`,
     `['say', 'battle.spell.frost.damage', '$dmg']`. Path syntax
     is `'caster.hp'` / `'target.dead'` / `'$dmg'` (string-as-path),
     vs the script DSL's `{ member: <expr>, field: 'hp' }` shape.
     Operator names diverge: `'rand'` vs `'random'`, `'*'` vs
     `'mul'`, positional `'sub'` (binary subtract) vs script's
     `op: 'sub'` (mutate's subtract-into-slot).

     Today: `mage.frost` is the only spell with a `do:` body.
     Every other spell carries `legacy_effect: '<name>'` pointing
     at the SE_* case still resident in `battle.c`. The spell
     DSL was designed to receive those migrations but the
     battle.c dispatcher hasn't yet been routed.

  3. **speak_missions DSL** — `data/world/scripts/speak_missions.json5`,
     a third micro-DSL. Vocabulary:
     `conds: [{flag: '<tag>', when: '<state>'}]` for AND-of-N
     condition matching, `binds: [{name, kind, npc_role|arg}]`
     with hardcoded kinds (`npc_name`, `npc_loc`, `dx_str`,
     `dy_str`) for resolved bindings. Both shapes parallel script
     DSL primitives but use entirely different keys: the cond
     shape parallels `if.cond`, the bind shape parallels
     `engine_bind`, the kind enum parallels `engine_bind_tbl`'s
     fn registry.

#### Tier 2 — symbol/identity hygiene

  4. **Enums shaped as positional CapitalCased arrays.**

     ```json5
     pc_class: ['', 'Fighter', 'Thief', 'Mage', 'Priest', 'Bard', 'Ranger'],
     race:     ['', 'Human', 'Dwarf', 'Elf', 'Halfling'],
     jobname:  ['', 'Merchant', 'Ranger', 'Fighter', 'Wizard', ...],
     ```

     Two coupled smells: (a) the script symbol IS the display
     string ('Priest' is both what the script says and what the
     UI renders), so renaming "Priest" to "Cleric" silently
     re-points every dispatch; (b) the case style is inconsistent
     across enums — `pc_class`/`race`/`jobname` are CapitalCased,
     `building`/`gender`/`pic` are lowercase symbols, `name`/
     `towne` are proper nouns. Scripts can't tell which to expect.

     Map-keyed records resolve both — the lowercase key is the
     symbol, an explicit `name:` field is the display string.

  5. **Map-keyed records over positional arrays everywhere
     identity matters.** Items shards (`weapons`, `armour`,
     `magic_items`, `specials`, `treasures`, `gear`, `potions`),
     `monsters`, `dungeons`, `flags`, `terrain`, `towns`,
     `buildings` (subtree, not the enum) are all keyed correctly
     today — the `id:` field is just the runtime int, the key
     carries the identity. The remaining offenders are the three
     enums above. Apply the same shape.

#### Tier 3 — magic ints leaking into the data DSL

  6. **No symbolic registry for `wld_location_*` slots.** Today:
     `{ engine_int: 'wld_location_x', args: [7] }`. Slot 7 is the
     beast cave (per `world.json5:56` comment) but the data
     doesn't say so. Add a `world_locations:` registry with
     symbolic keys (`fire_shield_cave`, `beast_cave`, `silver_pit`,
     ...) carrying `slot:` fields, then tag
     `wld_location_x.arg[0]` and `wld_location_y.arg[0]` with the
     new enum via the `arg_enums[]` shortcut landed in §20.20 #1.
     Scripts read `args: ['beast_cave']`.

  7. **Engine_int item-id args still bare ints.** Sites:
     `inv_find_item(34)` (tent), `inv_find_in_range(33, 34)`
     (blanket..tent). Tag those arg positions with `'item'` in the
     registry; scripts read `args: ['tent']` and `args: ['blanket',
     'tent']`. Same mechanism as #6, applied to existing enum.

  8. **ASCII-offset arithmetic in inventory key handling.**
     Today scripts decode the captured letter back into a slot
     index by hand:

     ```json5
     { let: 'slot',    value: { sub: [{ var: 'sell_key' }, 64] } },
     { let: 'item_id', value: { sub: [{ var: 'buy_key' },  34] } },
     ```

     Bare 64 and 34 are ASCII offsets ('A'-1 and 'A'-31
     respectively). Smelly. Plus `key_range: ['A', 'P']` hardcodes
     16 inventory slots — but the inventory bound is dynamic
     (`INVENTORY_SLOT_MAX` / `res_inventory_slot_max()`). The
     `digit_range: { lo:1, hi:{engine_int:'pa_party_member_max'} }`
     pattern proves the dynamic-bound model works; needs a
     `letter_range` analogue plus an automatic slot-index binding.

     Proposed shape:

     ```json5
     { letter_range: { lo: 'A', count: { engine_int: 'inv_slot_max' } },
       index_var:  'slot',     // 1..N, slot index
       letter_var: 'sell_key', // optional, the actual letter
       then: [...] }
     ```

     Affects 4 sites in `towne.json5` (Sell shop, generic-buy,
     spell-buy at line ~1398, and one more). Drops two `sub:N`
     conversions and 4 hardcoded ranges; ties everything to a
     single `INVENTORY_SLOT_MAX` source of truth.

#### Tier 4 — op-shape consistency

  9. **Legacy shorthand in `if.cond` should go.** Today `if.cond`
     accepts `{ flag: 'tag', eq: 'value' }` (legacy) AND
     `{ eq: [{ flag: 'tag' }, 'value'] }` (modern expr form). Two
     ways to write the same thing. Sweep all data sites onto the
     modern form, drop legacy in `eval_cond`.

 10. **Unify `chance:` and `if:` shape.** Today `chance` is its
     own control-flow op:
     `{ chance: { pct: N, then: [...], else: [...] } }`.
     But `chance` is already an expression form (§17-F:
     `{ chance: <expr> }` returns 0/1). So the op-shape collapses
     to `{ if: { cond: { chance: N }, then: [...], else: [...] } }`
     — drops one op from the surface; control flow goes through
     one primitive.

 11. **Three engine registries with different arg shapes.**
     `engine_call` (void return, args evaluated as exprs),
     `engine_int` (int return, args evaluated as exprs, optional
     `arg_enums[]` for symbolic resolution), `engine_bind` (string
     return, mixed string/expr args, output via `var:`). One
     registry with `kind: void|int|str` per entry would let a
     uniform call shape land at every site:

     ```json5
     { engine: 'pa_is_class', args: [{var:'who'}, 'Ranger'] }   // returns int
     { engine: 'invlist' }                                      // returns void
     { engine: 'pa_name', args: [{var:'who'}], var: 'who_name' }// returns str
     ```

     One vocab to learn instead of three. Cosmetic — defer if
     items 1-10 are enough.

 12. **Op-shape micro-inconsistencies.** `set_flag: '<tag>',
     value: '<state>'` is a flat 2-key op; everything else nests
     under one key. `pause: 1000` is a raw int next to
     `wait_input: true`. `keys: ['A', 'B']` is array-shaped vs
     `key: 'A'` scalar. These are minor — document the rule "one
     obvious arg takes it directly, multiple args nest in an
     object" and call it done.

#### Tier 5 — case/style consistency

 13. **Lowercase symbol style across all enums.** Falls out of #4
     automatically — once enums are keyed records, the *key* is
     always lowercase symbol, the *display* is whatever `name`
     field carries. The case-style smell goes away.

#### Tier 6 — documentation

 14. **Single canonical DSL reference.** `src/eval.c:1` references
     `docs/dsl.md §6` but the file doesn't exist. `src/script.h`
     header has the script DSL ops table. `speak_missions.json5`
     header documents its mini-DSL. Three sources, one missing.
     After tiers 1+2+3 land, write `docs/dsl.md` as the single
     reference covering control flow, expressions, bindings,
     enum shape, and the registry. The shards plus the doc =
     the reproducible game spec.

### 22.2 Sequenced execution plan

**A. Spell DSL → script DSL convergence** (tier 1, item 1).
**LANDED 2026-04-25.** Collapsed two evaluators into one. Retired
`src/eval.c` + `src/eval.h` (~1480 lines), the `SE_*` enum,
`se_from_legacy_effect`, the `effect` field on `ryl_spell`, the
`res_spell_node`-vs-fallback dispatch in battle.c / subs.c, and
`res_lib_effect` (no callers). Migrated all 12 spell behaviours
(mage frost / fire / confusion / death / hold / antimagic;
priest heal / restoration / rebirth / banish / revelation /
teleport / earthquake) plus the six `formulas:` records to the
script DSL. New script.c surface: `play_spell_body`,
`play_formula_int`, `script_combat_ctx` (mh/deadc/noc/fn +
encounter-flag pointers), `ask_target` (monster_alive +
party_member), `for_monster`, `monster_hp` / `monster_dead` /
`monster_count` / `battle_flag` / `state_banished` exprs and
matching mutate lvalues, `pa_teleport_step` engine_call (the
one-spell-special movement loop kept in C).

  Landed (commits, oldest first): `8a25b20` engine surface,
  `3fe6792` mage.frost, `4e651d8` priest dispatch shim +
  priest.heal, `5e4d9a3` priest.restoration, `c2bcc32`
  priest.rebirth, `9bf262f` priest.banish, `06158ba`
  priest.revelation, `73225fe` priest.teleport, `79e4fcd`
  priest.earthquake (last priest legacy_effect), `d8a6e86`
  mage.fire + for_monster op, `91405c1` mage.confusion,
  `b8a5406` mage.hold, `6e72948` mage.antimagic, `b65a18c`
  mage.death + dispatch cleanup, `70c7c98` formulas migration,
  `<this commit>` eval.c retirement + cleanup.

  Coverage gap captured: §22.5 plans the spell-cast fixture
  queue. The 22 existing fixtures cover no spell path, so the
  migrations rest on code review + RNG identity reasoning +
  ASan clean. Adding the queue is the natural follow-up.

**B. Enum keyed records with display split** (tier 2, items 4-5).
**LANDED 2026-04-25.** Concurrent with A — touches different files.

  Steps:
   - Engine: `res_enum_id()` accepts both array-of-string and
     object-of-records shapes (transition-friendly); add
     `res_enum_name(table, id)` returning `.name` field for the
     new shape, falling back to slot-string for old.
   - Migrate `pc_class` first (6 entries, prove the path; fold
     existing `classes:` `levelup:` records into the same object).
     Sweep ~30 callsites across C and ~10 across data
     (`'Priest'` → `'priest'`).
   - `race` next (4 entries), then `jobname` (16 entries).
   - Tighten `fold_enums` to warn on positional-array shape once
     all three are converted.

  Landed (commits, oldest first): `97f235e` (pc_class), `5e41f5f`
  (race), `bcf771c` (jobname), `dfeacae` + `1469f05` + `8cc2d6d` +
  `78984ae` (npcs subtree + retiring `enums.name` /
  `innkeepers` / `tavernkeepers` positional arrays). Plus
  consistency rename `jobname` enum id → `job` (the field on NPC
  records is `job:`, so the enum id matches now). All three
  CapitalCased enums are keyed records with explicit `name:` for
  display; lowercase key is the script-stable symbol. NPC records
  use lowercase keys and explicit `name:` field. New helpers:
  `res_npc_name(slot)`, `res_npc_sym(slot)`,
  `res_npc_in_town(twn, job_sym)`. New string-engine fn:
  `npc_in_town(job_sym, twn)`. `res_enum("name", idx)` is a thin
  shim that routes through `res_npc_name`, so DSL bindings via
  `{fn:'res_enum',args:['name',...]}` need no edits.

**C. Symbolic args sweep** (tier 3, items 6-7). **LANDED 2026-04-25.**
Small focused changes after A+B; can do per-shard.

  Steps:
   - Item 6 needs new data: a `world_locations:` registry mapping
     symbolic name → slot. C side: `wld_location_x/y` accept a
     symbolic key (or keep int slots; resolve at engine_int
     dispatch via arg_enums).
   - Item 7 is a one-line registry edit (`arg_enums: {'item'}` on
     the three inv_* fns) plus a 3-site data sweep.

  Landed: `world_locations:` keyed-records enum added to
  world.json5 (9 slots: hermit_dungeon, thun_temple,
  kobold_village, borin_camp, nolrim_crypt,
  master_crown_dungeon, beast_cave, silver_key_pit,
  hobgoblin_cave). `wld_location_x/y.arg[0]` tagged
  `"world_locations"`; `inv_find_item.arg[0]`,
  `inv_find_in_range.arg[0,1]`, `inv_count_in_range.arg[0,1]`
  tagged `"item"`. The arg_enums dispatcher learned to route
  `"item"` through `res_item_id` (items live in their own
  shards, not enums:); other tags still go via res_enum_id.
  Sweep: 1 site in scripts/speak.json5 (jazzy_ogre.beast_cave),
  3 sites in scripts/rest.json5 (tent / blanket+tent / tent).
  C-side bare-int callsites in speak.c/missions.c (slots 2/8 +
  t->a/b->arg) deferred — they're a separate raw-int leak in
  C, not in the data DSL.

**D. Inventory letter-range dynamic bound** (tier 3, item 8).
**LANDED 2026-04-25.** Engine extension: add `letter_range`
op-option with `count: <expr>`, `index_var:`, `letter_var:`.
Sweep 4 towne.json5 sites; drop two `sub:N` conversions.

  Landed: new `letter_range: { lo: 'A', count: <expr> }` option
  variant on `menu_branch` (parallel to `digit_range`), with
  optional sibling `index_var:` that binds the matched 1-based
  slot (`ch - lo + 1`) into rt_env. New `engine_int: 'inv_slot_max'`
  exposes `res_inventory_slot_max()` so scripts read the dynamic
  bound directly. Sweep: 2 sell-shop sites in towne.json5 (the
  bargain-flow and the merchant `(S)ell` arm) — both lose their
  `key_range:['A','P']` + `sub:[sell_key,64]` pair and read
  `slot` from index_var. The buy-menu `key_range:['A','G']` site
  stays as-is — its A..G letters map to specific item ids via a
  fixed table, not to inventory slots, so letter_range doesn't
  apply. Briefing's "4 sites" was an overcount; only the two
  ['A','P'] sites are inventory-slot decoders.

**E. Op-shape consistency** (tier 4, items 9-12). **LANDED 2026-04-25.**

  Steps:
   - Item 9 (drop legacy `if.cond` shorthand): grep for `flag:.*eq:`,
     `flag:.*ne:`, `gold:.*gte:`, `gold:.*lt:`; rewrite each onto
     expression form; remove the legacy branch from `eval_cond`.
   - Item 10 (collapse `chance:` into `if {cond:{chance:...}}`):
     grep for `{ chance: { pct:`, rewrite, remove the `do_chance`
     control-flow op (keep the `chance` expression form).
   - Item 11 (engine registry unification) — defer or do; write
     up the shape and decide.
   - Item 12 (micro-inconsistencies): document the rule, no code
     changes.

  Landed: items 9 + 10. Item 9 — only one `flag:.*eq:` callsite
  remained in the corpus (`dungeon.json5` tammys_prison) and
  zero `gold:.*(gte|lt):` callsites; the briefing's "30-50"
  estimate predated several earlier sweeps that had already
  migrated callers. New `flag_value: ['<tag>', '<value>']`
  expression primitive (mirrors `enum:`) makes symbolic
  flag-value comparisons compose in expression form. Legacy
  branch retired from `eval_cond`, which is now a thin
  truthy-check around `eval_expr`. The `eval_expr` ambiguity
  guard for `{flag, eq|ne}` stays as a redirect diagnostic.
  Item 10 — four `{ chance: { pct, then, else } }` op-form
  callsites collapsed to `{ if: { cond: { chance: <expr> },
  ... } }`; `do_chance` plus its dispatch arm retired (~25
  lines). Items 11 + 12 deferred — engine registry unification
  is a separate scoped sweep, and the micro-inconsistency rule
  set reduces to "everything new flows through eval_expr"
  which is now self-evident from the engine code.

**F. `speak_missions.json5` rewrite** (tier 1, item 3). Depends
on A landing (script DSL has loops/switch/conds rich enough
already). Convert each NPC role's stages into a script that
fires the right play_script with the right bindings. Remove
the speak_missions micro-DSL evaluator from `speak.c`.

**G. Single-doc DSL reference** (tier 6, item 14). After A-F.
Write `docs/dsl.md` covering: control flow, expressions,
bindings, lvalues, enum shape, registry vocabulary, error
contract, examples. This is what makes "read the data alone"
actually true.

### 22.3 What lands when

Tier 1 is the strategic capstone — the rest is mechanical or
narrowly-scoped extensions of existing patterns. The right
order to land them: A (spell DSL convergence) and B (enum
shape) can run in parallel; both unlock C+D+E. F depends on
A. G is the documentation pass after the surface stabilises.

B, C, D, E, A have landed (2026-04-25). Remaining: F, G. F
depends on A (now landed); G is the documentation pass after
the surface stabilises. The post-Lane-A coverage queue
(§22.5) and the reward-gold display fix (§22.6) are tracked
as follow-ups, not blockers.

Estimated remaining work: ~0.5 session for F, ~0.5 session
for G, plus ~3-5 hours for §22.5 and ~30 minutes for §22.6.
Cumulative ~1.5-2 sessions if done sequentially.

### 22.4 Non-goals

  - **Not a rewrite.** Every existing op stays; this is sweep
    + collapse + retire, not redesign.
  - **Not a typing overhaul.** No formal type system on the
    DSL; `eval_expr` still returns `int32_t` and dies on
    mismatches. The `arg_enums[]` shortcut is the only new
    mechanism, and it's already shipping.
  - **Not a graphical layer.** Texts stay as
    `data/world/texts/*.json5`; the `{cost:itemkey}` placeholder
    pattern can grow more typed forms (`{name:itemkey}`,
    `{damage:itemkey}`) as needed but no general template engine.
  - **Not changing the byte-identity contract.** Every
    migration step must keep `make check` green at 22 fixtures
    + check_world.

### 22.5 Battle / spell fixture queue (post-Lane-A coverage gap)

**Problem.** The 22 existing fixtures cover terrain narration,
towne services, hunt, rest, lodge, the encounter-and-flee path
in `title-create-party-combat`, and the cave-entry / dungeon-
discovery path in `wilderness-cave-descent`. None exercises:

  - `(A)ttack` to combat completion (win or loss)
  - `(C)ast Spell` from the combat menu (mage spells)
  - `subs.c::spell()` out-of-combat priest casts
  - any of the 12 spells migrated in Lane A (mage frost / fire /
    confusion / death / hold / antimagic; priest heal /
    restoration / rebirth / banish / revelation / teleport /
    earthquake)

So Lane A's 12 spell migrations rest on code review + RNG-
identity reasoning + ASan clean. `make check` green confirms
the surrounding paths weren't disturbed; it does NOT confirm
that `mage.frost.do` produces the same bytes as the old SE_FIRE
case did. A wrong-index in a `monster_hp` write, a missed `clw`
or `pause`, a swapped `ws`/`confused` mutate target, or an
off-by-one in `for_monster` — none of those would be caught by
the current gate. That is a real outage risk for combat play.

**Sequencing.** Add fixtures in roughly this priority order;
each commit blesses one new fixture and runs the full gate
green. None of these depends on Lane A landing in full — they
can and should be added now to retroactively cover the spell
migrations.

  1. **`combat-attack-win`** (highest leverage). Build a party
     that can win an early fight; (F)ight a mandatory encounter,
     (A)ttack to completion. Catches: encounter scaling
     formulas (already covered today), reward formula renders,
     `pa_add_kills` count, exp/gold display, item-drop roll,
     `expcheck` level-ups. This single fixture closes the
     biggest part of the gap because every spell fixture below
     reuses its setup pattern.

  2. **`combat-mage-frost`**. Same shape as #1 but with a
     mage in the party; cast Frost Finger on slot A. Tests
     `play_spell_body` end-to-end, `ask_target monster_alive`,
     `engine_bind 'pa_name'` for `{caster}`, `{dmg}` from a
     `let`, `monster_hp` mutate via the `sub` op, the kill
     branch + `battle.killed` emit. Most of the script-DSL
     spell engine surface burns through this one cast.

  3. **`combat-mage-fire`**. AOE — covers `for_monster`, the
     per-iteration RNG draw on dead slots (regression-trap for
     anyone who later "optimises" the loop to skip dead
     slots), and the single-CSI-prefix-per-emit shape for
     `{caster}`/`{monster_name}`/`{dmg}` lines.

  4. **`combat-mage-flag-trio`** (one fixture, three casts).
     Confusion → Hold → Antimagic in succession. Verifies the
     `battle_flag` lvalues for all four flag pointers
     (`confused` / `held` / `antimagic` / `ws`) and the shared
     `battle.spell.utters_word` text shard. The combined
     fixture is cheaper than three separate ones since the
     setup is identical.

  5. **`combat-mage-death`**. Resist + kill arms in one cast
     each? — actually two fixtures (or one fixture that fires
     death twice with different targets). Tests
     `ryl_random(0, 199) < monster_hp` resist roll and the
     `monster_dead`-then-`monster_hp` write order in the kill
     arm.

  6. **`towne-priest-heal`**. Out-of-combat priest cast from
     the towne flow. Tests the subs.c dispatch shim, the
     `ask_target party_member` digit prompt, the `pm_dead` /
     `pm_h` clamp, and the `{caster} heals / {target} / for
     {x} hit points...` narration. Covers the simplest priest
     spell.

  7. **`towne-priest-restoration`** + **`towne-priest-rebirth`**
     (one fixture each — rebirth needs a dead member to test
     the success arm AND a live member for the alive-and-
     healthy early-out, so two casts in one fixture).

  8. **`dungeon-priest-banish`** + **`dungeon-priest-revelation`**
     + **`dungeon-priest-teleport`** + **`towne-priest-earthquake`**.
     Each needs to be set in the right place context (banish /
     revelation only fire in dungeon; earthquake only fires NOT
     in dungeon). Teleport is the engine-call-encapsulated
     `pa_teleport_step` exerciser; revelation hits the
     three-category random-object switch + inv_add menu_branch.

**Per-fixture pattern.** Each is a `tools/replay/<name>.{in,
expect,expect_snapshot,env}` quartet. The `.env` pins
`RYL_FAKE_EPOCH` / `RYL_FAKE_SEED` / `RYL_NO_DELAY=1`. The
`.in` constructs a party (Replay name + 1-line per member),
puts them in the right combat / towne / dungeon position,
fires the cast keypress, and exits cleanly. Bless once with
`tools/replay.sh <name> --bless`; subsequent migrations gate
on byte identity.

**Estimated effort.** ~1 fixture per 20-30 minutes once the
first one is shaped. Total: ~3-5 hours of fixture-authoring
and blessing for the full set, plus ~30 minutes per re-run
under future engine changes. Reasonable for the safety it
buys; the alternative is "code review is the contract" for
all 12 spell paths and any future spell-related refactors.

**Non-goals here.** Not a generic property-based combat
fuzzer; not a coverage-tracker; not a CI coverage threshold.
Just twelve fixtures that pin one canonical execution per
spell.

### 22.6 Reward gold display mismatch (separate from Lane A)

**Symptom.** `formulas.reward_gold_per_survivor` is the
divided-by-4 figure shown in the `Each survivor gets %d gold
pieces.\r\n\r\n` victory line. `formulas.reward_gold_party` is
the un-divided figure handed to `party_add_gold`. So the
displayed number is exactly 1/4 of what actually arrives in
the kitty.

**Origin.** Inherited verbatim from the original Rylween C
source (battle.c's `gold_display = ...per_survivor` vs
`gold_gain = ...party`). Likely a long-standing oversight —
"survivor" naming implies a 1/4 split, but the actual award
skips the divide. Pre-Lane-A migrations preserved the legacy
on the data-as-spec principle so byte-identity holds; both
combat fixtures cover the math today and would diff under a
fix.

**Plan.** Single follow-up commit, scoped tightly:

  1. Decide which number is canonical. Default: the un-
     divided one is right (survivors share the kitty, they
     don't each get 1/4 of an already-1/4'd amount). Confirm
     by gameplay reasoning — at level 1 with `noc=2`, `h2=10`,
     `ws=10`, the legacy display says "Each survivor gets 1
     gold piece" and the party gets 5. Five gold is the
     intended reward; one is the bug.
  2. Drop `reward_gold_per_survivor` from `formulas:`. The
     display reads `reward_gold_party` directly (renaming to
     `reward_gold_total` would be cosmetic; defer).
  3. Update battle.c's victory render: the `gold_display`
     local goes away; the `tx("...gets %d gold pieces.\r\n",
     gold_display)` call uses `gold_gain` (already computed
     two lines down).
  4. Bless the two affected combat fixtures (`title-create-
     party-combat`, `wilderness-cave-descent`) — both will
     diff because the displayed number changes from N to 4N.
  5. Note in the commit message that the **actual** gold
     awarded is unchanged; only the display alignment shifts.

**Risk.** None to gameplay logic — `party.gold` arithmetic
is identical. The only diff is one printed integer per
victory.

**Why a separate commit.** Lane A's contract is "every spell
migrates byte-identically through the DSL; legacy bugs are
preserved." Folding a player-facing display fix into Lane A
would muddy that contract and force re-blessing the combat
fixtures during the migration. Better to land the fix as a
clearly-labelled "balance fix" commit AFTER Lane A retires
eval.c, when the gate is stable.

**Estimated effort.** ~30 minutes including re-blessing the
two combat fixtures and writing the commit message. The
hardest part is deciding whether the canonical number is the
divided or un-divided one; the rest is mechanical.

### 22.7 Continuation prompt — what remains and in what order

This is the literal handoff for the next session. Lane A
landed in 16 commits between `8a25b20` and `ca8aaa0` (2026-04-25);
the engine now speaks one DSL and `eval.c` is gone. The list
below is what stays — in the order I'd actually do it:

**1. Reward-gold display fix (§22.6).** ~30 min. Single
   commit. Lowest cost, highest player-visible payoff. Done
   first because it has no dependencies and doesn't compete
   with the fixture work. Steps:
   - Decide canonical number — almost certainly the un-divided
     `reward_gold_party` (the displayed-1, awarded-5 example
     in §22.6 makes "5 is correct" the obvious read).
   - Drop `reward_gold_per_survivor` from `formulas:`.
   - In `battle.c::encounter` victory render, the
     `gold_display` local goes away; use `gold_gain`
     (`reward_gold_party`) directly in the `tx("...gets %d
     gold pieces.\r\n", ...)` call.
   - Re-bless `title-create-party-combat` and
     `wilderness-cave-descent` — both will diff because the
     displayed gold quadruples.
   - Commit message clearly labelled "balance fix" with
     before/after numbers in the body.

**2. Combat-attack-win fixture (§22.5 #1).** ~30-60 min.
   Single commit. Highest leverage of the §22.5 queue because
   it pins the encounter / reward / expcheck path that every
   spell fixture below will reuse. Without this, the spell
   fixtures land on top of an unpinned baseline and any drift
   in encounter rendering masquerades as spell drift. Steps:
   - Author `tools/replay/combat-attack-win.{in,env}` —
     party with a fighter that can finish a low-HP foe in
     ~3 attacks; pick a target (A), attack, repeat until
     the WIN screen + reward render fires.
   - `tools/replay.sh combat-attack-win --bless` to capture
     `.expect` + `.expect_snapshot`.
   - Verify the fixture fires the encounter scaling formulas
     (already covered) AND the new `gold_gain` display from
     §22.6 above.

**3. Spell fixtures (§22.5 #2–#8).** ~3-4 hours. One commit
   per fixture or grouped where natural (the flag-trio is
   one fixture firing three casts; rebirth is one fixture
   firing alive + dead). Order: mage frost → mage fire → mage
   flag-trio (confusion + hold + antimagic) → mage death →
   priest heal → priest restoration + rebirth → priest banish
   + revelation → priest teleport + earthquake. Each fixture
   captures an `.expect_snapshot` so future engine changes
   gate on byte identity.

   - Skip if you'd rather batch these into one big "spell
     fixture pass" commit; the per-fixture grain is a
     review-cost decision, not a correctness one. The data
     authoring is the bulk of the work.

**4. §22.F — `speak_missions.json5` rewrite.** ~0.5 session.
   Depends on Lane A (now landed). Convert each NPC role's
   mission stages from the speak.c micro-DSL into a script
   DSL `play_script` that fires the right body with bindings.
   Retire the `mission_spec_` parser arm in res.c and the
   speak.c interpreter loop. Touches: `data/world/scripts/
   speak_missions.json5`, `src/speak.c`, `src/res.c`,
   `src/missions.c`. Make sure `make check` is green at every
   step; the towne speak fixtures cover most of the affected
   surface.

**5. §22.G — `docs/dsl.md` single-doc reference.** ~0.5
   session. Last because it crystallises the surface. Cover:
   - Control flow (if / loop / break / continue / for_*
     family / switch).
   - Expressions (literals, arithmetic, comparisons, flag /
     gold / member / inventory / monster_* reads, var,
     engine_int, engine_bind, item / pic / enum /
     flag_value / state_banished).
   - Mutate lvalues (gold / torches / food / pl / flag /
     inventory / member_field / hours_rested / hour_of_day /
     time_left / monster_hp / monster_dead / battle_flag /
     state_banished).
   - Bindings (caller ryl_bind, rt_env, automatic
     {party_max} / {inventory_max} / {cost:itemkey}).
   - Op registry (engine_call, engine_int, engine_bind —
     names + signatures).
   - Error contract (die_at format, fatal-on-typo).
   - Examples — small spell, small towne flow, formula.
   - Cross-link from `src/script.h` header.

**Skipped permanently / not worth doing here:**
   - Item 11 (engine registry unification — `engine_call` /
     `engine_int` / `engine_bind` collapsed into one). Already
     deferred in §22.E; the three-registry shape works and
     unification is cosmetic.
   - Item 12 (op-shape micro-inconsistencies). Same reason —
     the rule "everything new flows through eval_expr" is
     self-evident from the engine code now.

**Bar per session.** `make check` green at every commit,
`make testasan` clean for any commit that touches engine
memory (none of the §22.6 / §22.F / §22.G commits should —
they're data and docs). Each step from #1-#3 retroactively
covers Lane A's spell paths; #4 retires another DSL; #5
makes "read the data alone" actually true.

**Estimated total.** ~1-2 sessions for #1+#2+#3,
~0.5 session for #4, ~0.5 session for #5. Cumulative
~2-3 sessions to fully close §22.

### 22.8 Continuation prompt — what shipped, what's next

This is the second handoff for §22 — the literal next-session
brief after the second wave of commits. Between `c4183ae` (the
§22.6 fix) and the head of this branch, nine commits landed:

  - `c4183ae` §22.6 reward-gold display fix.
  - `a0a580f` §22.5 #1 combat-attack-win fixture, plus the
    RYL_FORCE_ENCOUNTER + RYL_FORCE_ENCOUNTER_NOC scaffold
    that makes the rest of the §22.5 list authoring-tractable.
  - `b8b2f52` §22.5 #2 mage-frost.
  - `eafb648` §22.5 #3 mage-fire.
  - `e877b5f` §22.5 #4a mage-confusion-hold.
  - `1b9acd9` §22.5 #4b mage-antimagic.
  - `9826d35` §22.5 #6 priest-heal.
  - `dac0e00` §22.5 #7a priest-restoration.
  - `2c194b0` §22.5 #8d priest-earthquake.

**What's done — by spell.** Mage frost, fire, confusion, hold,
antimagic. Priest heal, restoration, earthquake. Plus the
combat-attack-win baseline. Nine fixtures, all gating `make
check` + `make testasan` green. Eight of the twelve spell
paths Lane A migrated are now byte-pinned.

**What's not done — by spell.** Mage death; priest rebirth,
banish, revelation, teleport. Five fixtures left.

**The single root cause for three of the five.** Mage death
(cost 79), priest rebirth (91), priest banish (86) all exceed
the spellcaster's lv-1 ma cap (= wi, max ~70 from the
ryl_random(15,70) roll, observed 67 at the seeds we've used).
Every "got blocked on cost" fixture below shares this single
gate. The root fix is a small env knob:

```
RYL_FORCE_PARTY_LV=<n>     # clamp every member.lv to <n> after
                            # createparty's lv=1 init in driv.c
```

Approach is identical to the existing replay-only env knobs
(see RYL_STARTING_GOLD at driv.c:1583, RYL_FORCE_ENCOUNTER
in subs.c::random_wilderness, RYL_FORCE_ENCOUNTER_NOC in
battle.c::encounter): single-shot getenv read inside the
party-creation lv loop, unsetenv after consumption,
production never sets it. Caster's `ma` is set to `wi` at
class assignment (createparty.c:1570 if pm_is_spellcaster);
the lv bump alone is enough since per-spell cost = base + lv
grows slower than the post-class ma cap. At lv 5 a priest
has ma >= 90 → both rebirth and banish fit; at lv 3 a mage
has ma >= 70+ → death (cost 81) just fits.

**The other two — dungeon-context fixtures.** Priest revelation
and priest teleport need place=1 (the int binding subs.c::
spell passes when called from dungeon.c's Z-key). Two paths
into a dungeon work:

  1. **Walk to a wild-roll cave**, then E to enter. The seed
     determines whether a cave appears in the first ~30 moves
     (seed 5 yes, seed 2 no — slot 1 priest needs wi >= 50,
     so seed-shopping is constrained). The trap I hit on the
     first try: the cave-found prompt loops on key() until L
     or E, eating every walk byte until a valid letter shows
     up. So the natural `82828282...828282E` pattern works,
     but `8282...82E + spaces + Z + 1 + 5` does NOT — the
     dungeon-level-1 random-fill script's `wait_input`
     consumes one of the spaces, and then the menu's key()
     loop reads bytes one at a time. Off-by-one on key
     accounting drops Z into the wrong slot.

  2. **Cast earthquake first**, take the (E)nter arm of the
     cave_found_prompt, descend into the ad-hoc dungeon.
     Earthquake guarantees the cave; no seed-shopping. But
     it spends ~58 ma on the casting priest, so the *same*
     priest can't follow up with revelation (cost 41). The
     two-priest trick: party has slot 1 priest casts
     earthquake, slot 2 priest casts revelation/teleport in
     dungeon. Seeds 2 and 31 both have wi >= 50 in slots 1
     AND 2; either works.

  Path 1 is simpler if you bookkeep the keys carefully (one
  ' ' absorber per dungeon-level script's wait_input, then
  Z 2 5, and one A to ascend out). Path 2 is more robust
  but needs the two-priest party + the earthquake bridge in
  every dungeon-context fixture. Pick path 1 for revelation/
  teleport (single fixture each); reach for path 2 if the
  cave roll for the seed needed elsewhere doesn't fire.

**For banish.** Path 2 with two priests, slot 2 raised to lv 5
via RYL_FORCE_PARTY_LV. Slot 2's ma cap = wi+28 = 91+
floor(28×0.5) ≈ 91 if seed 2 wi=63 → 63+28=91. base 85 + lv 5
= 90. Just fits.

**Recommended order — what to do next.**

  1. **Add RYL_FORCE_PARTY_LV knob.** ~10 lines in driv.c
     createparty (before the `state.p[a].lv[i] = 1;` loop at
     line 1607, read the env var, parse, clamp). Single-shot
     unsetenv after the loop. Test by running an existing
     fixture with the var set — every member's lv should
     bump in the snapshot.

  2. **Land mage-death (§22.5 #5).** RYL_FORCE_PARTY_LV=3 +
     seed 2 mage in slot 1. Death (cost 81 at lv 3, mage ma
     cap = wi+14×3 ≈ 88). Fixture cribs combat-mage-frost's
     pattern — F + C4A (target slot 1) + AA*3 (slots 2-4
     attack); the resist roll fires off ryl_random(0, 199)
     vs monster_hp. With h2_scaled ~43 from #1 and rng draw
     deterministic at this point, the fixture pins one
     specific arm — if the canonical roll lands in the
     resist arm, great; if it lands in the kill arm, also
     great. The arm not pinned by this fixture lands by a
     follow-up fixture with a different RYL_FORCE_ENCOUNTER
     monster (different h2 → different pivot threshold).

  3. **Land priest-rebirth (§22.5 #7b).** RYL_FORCE_PARTY_LV=4
     + seed 2 priest in slot 1. The fixture needs both arms
     of the alive-vs-dead branch. Two casts in one fixture:
     the first targets a healthy member (alive arm — emits
     "alive and healthy", no mana cost — legacy quirk
     preserved verbatim); the second targets a dead member
     (dead arm — eyelids/flicker/opens_eyes + ma debit).
     Death needs to come from somewhere — pick a fixture
     where one combat fight kills a slot, then cast rebirth.
     Or cheap path: have the engine's losegame check skipped
     (no, too invasive). Or: write a tiny RYL_FORCE_DEAD_SLOT
     knob that flags one member dead at party-create time,
     unsetenv'd after. Smallest change for the rebirth fixture
     to land both arms.

  4. **Solve dungeon-context, then land revelation + teleport
     (§22.5 #8b, #8c).** Pick path 1 (wild-roll cave) at
     seed 5 (slot 1 wi=52 → priest qualifies, cave triggers
     within ~30 moves). The key accounting:
     - Walk: `82` × N until a cave rolls; the cave-found
       prompt eats walk bytes until L/E, so end the walk
       with `E`.
     - Inside the dungeon, the level-1 script's `wait_input`
       consumes one byte; absorb with a single ` ` (space).
     - Menu reads `Z` → spell(1) → `1` (caster) → `5`
       (revelation) or `6` (teleport). For revelation, the
       chance(59) gate has two arms — the "nothing" arm has
       a wait_input absorber; the "find object" arm has a
       menu_branch (T or anything). Append spaces for the
       absorbers.
     - For teleport, the compass + how-far prompts use loop
       + menu_branch with key_var; their accepted keys are
       8/2/4/6 + 1-9 (typical map-DSL conventions — confirm
       at the call site). One byte per prompt.
     - End with `A` (ascend to cl=1 → leaves cave) and `X`
       to quit.

  5. **Land priest-banish (§22.5 #8a).** Path 2 (earthquake
     bridge), RYL_FORCE_PARTY_LV=5, two priests (slot 1 +
     slot 2). Slot 1 casts earthquake (Z 1 7 E) → spawns
     cave, enters new dungeon. Slot 2 casts banish (Z 2 4)
     in the dungeon — sets state.banished, the success arm
     ends with the state_banished mutate. Trickiest fixture
     of the lot because both casters are on cooldown after
     their cast (no further casts in the same combat) AND
     because the earthquake-spawned dungeon has 5-18 levels
     (random) — its level 1 random fill is a separate lvl[1]
     roll that's not easy to predict.

  6. **§22.F speak_missions.json5 rewrite + parser retire.**
     Untouched this session. The plan remains as in §22.7:
     convert each NPC role's mission stages from the speak.c
     micro-DSL into a script DSL `play_script` that fires
     the right body with bindings. Retire the
     `mission_spec_` parser arm in res.c and the speak.c
     interpreter loop. Touches: `data/world/scripts/
     speak_missions.json5`, `src/speak.c`, `src/res.c`,
     `src/missions.c`. The towne speak fixtures cover most
     of the affected surface so make check is the gate;
     do this before §22.G so the doc reflects the final
     surface.

  7. **§22.G docs/dsl.md single-doc reference.** Untouched.
     Cover everything from §22.7's spec (control flow,
     expressions, mutate lvalues, bindings, op registry,
     error contract, examples, cross-link from
     `src/script.h`).

**What I learned about authoring fixtures (worth keeping).**

  - **Spellcaster qualification by seed.** Slot 1 wi >= 50
    is the blocker. Useful seeds: 2 (slot 1 wi=67, slot 2
    wi=63 — best for two-priest trick), 5 (slot 1 wi=52,
    cave triggers in ~30 moves at seed-5 walk), 31 (slot 1
    wi=66, slot 2 wi=63 — alternative for the two-priest
    trick), 17 (slot 1 wi=53, ch=63 — bard-qualifying as a
    bonus for any future bard fixture).

  - **Combat round key shape.** Per round: `F` (the F/R
    re-prompt fires after each round) + 4 × (action +
    target). Action is `A` for plain attack, `C<digit>` for
    combat-cast. Target is one byte per attack (A-D for
    monster slot). With NOC > 1 use the `AB` fallback target
    pattern — the first byte tries slot A, the second is
    consumed only if A is dead (the in-range-and-not-deadc
    check rejects A and reads the next byte). 16 round_n
    patterns generously covers any 4-attacker × 1-2-noc
    fight; the surplus consumes silently as no-ops at the
    world map after the win.

  - **Combat menu cast layout.** `(C)ast Spell` only fires
    for pm_is_mage. The menu is six lines (frost / fire /
    confusion / death / hold / antimagic = slots 1-6) plus
    `7. Abort`. Selector loop accepts '1'-'7'. After the
    digit, the spell DSL takes over; ask_target prompts use
    the same letter-by-monster-slot shape as the combat
    target prompt.

  - **Priest cast dispatch.** subs.c::spell(place) is the
    entry; `Z` from driv.c (place=0, wilderness) or
    dungeon.c (place=1, dungeon). Dispatch prompts "Who?"
    (digit caster, gates pm_is_priest), then the priest
    spell menu (1-7 + 8 Abort). After the digit, play_spell_
    body for that spell runs; ma debit lives in the DSL,
    not C — different from mage spells.

  - **Wait_input absorbers.** Every dungeon level script
    that emits + waits has one ryl_pause that consumes a
    keypress. Same for the cave-no-moves arm. Use single
    ` ` (space) bytes for these — innocuous everywhere.

  - **Key-accounting trap (the dungeon-context one).** The
    cave-found prompt at the wild-roll path loops key() on
    every byte of the input stream until L or E. So
    appending `E` at the end of a long walk pattern enters
    the cave; appending `Z` would be ignored (Z != L/E).
    This is why dungeon-context fixtures need `walk + 'E'`
    as the cave entry, not `walk + spaces + Z + ...`.

  - **Stats the seed gives.** Snapshot all four members'
    ws/wi/ch/h on the first fresh-stats roll (no rerolls)
    by writing a tiny probe `.in` file with `N\nReplay\nNA\n
    1\n1\n2\n` repeated four times and reading the resulting
    snapshot. Useful when a fixture needs a specific class
    per slot.

**Estimated remaining effort.** With the lv knob landed
(~15 min), mage-death is ~30 min, priest-rebirth is ~45 min
(two-arm fixture), revelation + teleport ~45 min each,
banish ~60 min (two-priest setup). Total spell-fixture
remaining: ~3-4 hours. §22.F is ~0.5-1 session; §22.G is
~0.5 session. Cumulative ~1-2 more sessions to fully close
§22.

**Bar per session.** `make check` and `make testasan` both
green at every commit. The lv knob commit is the only one
that touches engine code directly; every other commit is
data + .in/.expect/.expect_snapshot quartets.

§22.9 — replay-knobs centralization + spell fixtures #5/#7b

Continuation of §22.8. Four commits this micro-session, all
gates green at each step:

  - `eac3317` §22.5 RYL_FORCE_PARTY_LV knob in createparty
    (~36 lines added inline at the time). Bumps lv plus the
    per-class wi/h/ws/ch deltas (n-1 times) and resets ma to
    the new wi cap for spellcasters. Verified at seed 2 with
    LV=3: slot-1 mage 67→81 wi/ma — death cost 78+3=81 just
    fits.

  - `bb8eee8` §22.5 #5 combat-mage-death fixture. Same shape
    as combat-mage-frost, swapped C1→C4 for the death menu
    slot, env adds RYL_FORCE_PARTY_LV=3. The deterministic
    RNG draw lands the kill arm (random(0,199) >= 27 monster
    hp ≈ 87% odds), pinning the monster_dead/monster_hp=0
    mutate path. The resist arm would need a different
    encounter to flip the gate — open follow-up if/when we
    want both arms.

  - `675a73d` refactor: extract replay-mode env knobs into
    src/replay_knobs.{c,h}. Centralizes all five one-shot
    knobs (RYL_STARTING_GOLD, RYL_FORCE_ENCOUNTER,
    RYL_FORCE_ENCOUNTER_NOC, RYL_FORCE_PARTY_LV,
    RYL_FORCE_DEAD_SLOT) — previously inlined across driv.c
    (3), subs.c (1), battle.c (1) — into one TU. Call sites
    collapse to one-line `rk_*` helpers. Pure refactor; every
    fixture passes byte-identical. The always-on infra knobs
    (RYL_FAKE_EPOCH / RYL_FAKE_SEED / RYL_NO_DELAY /
    RYL_SNAPSHOT) are NOT routed through this file — they're
    consumed every call by lower-level primitives (ryl.c /
    io_tty.c / snapshot.c). Header notes the split.

  - `86346e3` §22.5 #7b wilderness-priest-rebirth fixture.
    First fixture to use the new RYL_FORCE_DEAD_SLOT knob.
    Both arms of rebirth's alive-vs-dead branch in a single
    wilderness session — no encounter, no combat. Slot 1
    priest at lv 5 (post-knob wi = 67 + 8×4 = 99, ma cap =
    99) just covers cost (90 + lv 5 = 95). Cast order: alive
    first (no debit), then dead (debits 95 → 4). Reverse
    order would trip the not_enough_pts gate. Snapshot
    confirms slot-1 ma=4 (one debit) and slot-3 hp=1, dead=0
    (the dead-arm mutates ran).

**New convention — test-only env knobs.** New replay knobs
go in `src/replay_knobs.{c,h}`, NOT inlined at the call site.
The pattern: declare an `rk_*` helper in the header, impl in
the .c file (with the `getenv` + validate + `unsetenv` + apply/
return logic), and the consumer adds `#include "replay_knobs.h"`
plus a one-line invocation. This keeps the inventory of test-
only gameplay state greppable in one TU rather than scattered
across the codebase. Always-on infra knobs stay where they are.

**What's not done — by spell.** Three fixtures left:
priest revelation (#8b), priest teleport (#8c), priest banish
(#8a). The §22.8 plan applies verbatim — path 1 (wild-roll
cave at seed 5) for revelation + teleport; path 2 (earthquake
bridge with two priests) for banish. The lv knob is now
landed, so banish is unblocked the moment the two-priest
setup is wired.

**Recommended order — what to do next.**

  1. **Land priest-revelation (§22.5 #8b)** via path 1. Seed
     5 wild-roll cave, slot 1 priest, walk pattern + `E` to
     enter, then `Z 1 5` (cast slot 5 = revelation) with
     space absorbers per the §22.8 key-accounting notes. The
     chance(59) gate has two arms — the "nothing" arm and
     the "find object" arm. Whichever arm fires at this seed
     pins one of the two paths.

  2. **Land priest-teleport (§22.5 #8c)** via path 1. Same
     seed, same dungeon entry. `Z 1 6` (slot 6 = teleport),
     then the compass + how-far prompts (8/2/4/6 + 1-9 keys).
     One byte per prompt — confirm at the call site.

  3. **Land priest-banish (§22.5 #8a)** via path 2.
     RYL_FORCE_PARTY_LV=5, two priests in slots 1 + 2 (seed
     2 has wi 67 / 63 in slots 1 / 2 — both qualify). Slot 1
     casts earthquake (`Z 1 7 E`) → spawns cave, enters new
     dungeon. Slot 2 casts banish (`Z 2 4`) — sets
     state.banished, the success arm ends with the
     state_banished mutate. Trickiest fixture: both casters
     on cooldown after their own cast (no further casts in
     same combat) AND the earthquake-spawned dungeon has
     5-18 levels (random). Pin a seed where the level-1
     random fill is reproducible.

  4. **§22.F speak_missions.json5 rewrite + parser retire.**
     Untouched. Plan unchanged from §22.7/§22.8.

  5. **§22.G docs/dsl.md single-doc reference.** Untouched.
     Should follow §22.F so the doc reflects the post-
     migration surface.

**Estimated remaining effort.** Revelation ~45 min, teleport
~45 min, banish ~60 min = ~2.5 hours of spell-fixture work
remaining. §22.F ~0.5-1 session; §22.G ~0.5 session.
Cumulative ~1-2 more sessions to fully close §22.

**Bar per session.** Same as §22.8: `make check` and
`make testasan` both green at every commit.

§22.10 — wilderness-priest-revelation fixture (#8b)

Continuation of §22.9. One commit this micro-session, both
gates green:

  - §22.5 #8b wilderness-priest-revelation fixture. First
    dungeon-context spell fixture, exercising path 1 from §22.8
    (walk to wild-roll cave at seed 5, enter with E, cast
    revelation from inside the cavern). No lv knob needed —
    revelation is the cheapest dungeon-only priest spell
    (base 40 + lv 1 = 41) and seed-5 slot-1 priest wi=52
    just covers it.

**The `wait_input` absorber rule — single newline, not space.**
The trap I hit on this fixture: `wait_input: true` lowers to
`ryl_pause()`, which loops on `io->getch()` until it sees `\r`
(13) or `\n` (10). Any other byte (space, digit, letter) is
consumed and the loop continues. So absorbers for `wait_input`
have to be a literal newline in the `.in` file — using a space
makes the absorber eat the rest of the input until the next
real newline, which silently swallows the cast keys. The two
spell fixtures that already shipped (combat-mage-* in towne)
all happen to use `\n` because their .in lines naturally end at
that point; this dungeon-context fixture is the first where I
tried to inline the absorber on the same line and discovered
the constraint. Worth flagging for the remaining spell fixtures
(#8c teleport, #8a banish) — anywhere a wait_input fires inside
a dungeon-context flow, the absorber is `\n`, not ` `.

**What's pinned by this fixture.** The revelation cast itself:
cost-check passes (52 >= 41), `mutate ma -= 41` debits to
ma=11, `chance(59)` lands the find-object arm at this seed,
the category roll picks weapons (cat=0), the inner roll picks
Halbard (id 9), and the `T` byte after `Z15` takes the
`(T)ake` branch of the menu_branch — `inv_add` writes id 9
into inventory slot 1.

**What's not pinned (deferred).** The `chance(59)` nothing arm
and the `(L)eave` branch of the menu_branch are both reachable
only from a different RNG state. A follow-up fixture at a
different seed (or with different prior RNG draws) can pin the
nothing arm. The `(L)eave` arm is trivial — same fixture with
the `T` byte replaced by anything else (or omitted, letting the
following `\n` fall through as the menu_branch byte) — but it
adds no new mutations, so it's not worth a separate fixture
unless we later care about explicit menu_branch coverage.

**What's not done — by spell.** Two fixtures left from the
§22.8 list: priest teleport (#8c), priest banish (#8a). The
teleport plan is unchanged (path 1, same seed-5 cave entry,
`Z 1 6` with the compass + how-far prompts). Banish is
unchanged (path 2, two priests + earthquake bridge,
RYL_FORCE_PARTY_LV=5).

**Recommended order — what to do next.**

  1. **Land priest-teleport (§22.5 #8c)** via path 1. Reuse
     the seed-5 walk pattern + cave entry from this fixture
     verbatim — `C82828282828282E\n` then `\n` absorber for
     find_torches, then `Z16` for slot-6 teleport, then the
     compass + how-far prompts. Compass keys are 8/2/4/6 (the
     wilderness movement set) and how-far is a 1-9 digit; one
     byte per prompt. Confirm at the call site whether either
     prompt has a wait_input absorber after — if so, single
     `\n` per absorber.

  2. **Land priest-banish (§22.5 #8a)** via path 2.
     RYL_FORCE_PARTY_LV=5, two priests in slots 1+2 (seed 2
     wi 67 / 63 — both qualify). Slot 1 casts earthquake
     (`Z 1 7 E`) → spawns cave, enters new dungeon. Slot 2
     casts banish (`Z 2 4`) — sets state.banished, success
     arm ends with state_banished mutate. The earthquake-
     spawned dungeon has 5-18 levels (random); pin a seed
     where the level-1 random fill is reproducible.

  3. **§22.F speak_missions.json5 rewrite + parser retire.**
     Untouched. Plan unchanged from §22.7/§22.8/§22.9.

  4. **§22.G docs/dsl.md single-doc reference.** Untouched.
     Should follow §22.F so the doc reflects the post-
     migration surface.

**Cleanup follow-up — `dungeon_encounter` reserved slots.**
The `dungeon_encounter` enum in `data/world.json5` (line ~631)
has slots 10..19 marked as empty strings — reserved holes
between the random-fill range (1..9) and the quest-encounter
range (20..38). They're a dead band: `res_dungeon_apply_placements`
overlays the quest ids at 20+, the random fill rolls only 1..9,
and slot 0 is the "visited" sentinel. Worth confirming nothing
indexes into 10..19 (a grep for `lvl[`/`dungeon_encounter`-
adjacent reads should rule it out), then collapsing the enum to
just 0..9 + 20..38 — or keeping the holes if any C-side numeric
constants assume the 20-offset. Not blocking §22; flag for the
post-§22 hardcoded-indices cleanup pass.

**Estimated remaining effort.** Teleport ~45 min, banish ~60
min = ~1.75 hours of spell-fixture work remaining. §22.F is
~0.5-1 session; §22.G is ~0.5 session. Cumulative ~1-1.5
more sessions to fully close §22.

**Bar per session.** Same as §22.8/§22.9: `make check` and
`make testasan` both green at every commit.

§22.11 — wilderness-priest-teleport fixture (#8c)

Continuation of §22.10. One commit this micro-session, both
gates green:

  - §22.5 #8c wilderness-priest-teleport fixture. Reuses the
    seed-5 walk-to-cave entry from #8b verbatim
    (`C82828282828282E\n`), then casts teleport from inside the
    cavern. Slot 1 priest at lv 1 (seed 5 wi=52, ma=52) covers
    cost (35 + lv 1 = 36) without RYL_FORCE_PARTY_LV.

**The wait_input layout for teleport.** Two ryl_pause-gated
narration blocks back-to-back inside the spell body
(carefully/prepares/spends/chanting/candles + advance_hour, then
picks_slab/satchel/wants_you/to). Each needs a single literal `\n`
absorber per the §22.10 rule — split across separate lines in
the .in file so each ends as a clean newline byte. The compass
and how-far prompts are loop+menu_branch reads, so `2` (south)
and `1` are inlined on one line as `21\n`; the trailing `\n`
falls through the dungeon level loop's D/A re-prompt before the
final `A` ascend.

**Direction choice — terrain matters.** First bless used dir 8
(north): pa_teleport_step's `state.map[y-1][x] != 'm'` guard
hit a mountain at (12, 7) and the call no-oped, leaving y=8
unchanged. Switched to dir 2 (south) — (12, 9) and (12, 10)
are both grass — so the snapshot now pins an actual coord
change (12,8) → (12,10). Worth flagging for any future
teleport fixture: pick a direction with no mountain blocker at
the cave's wilderness coords, or the side-effect under test
silently disappears.

**What's pinned by this fixture.** Cost check passes (52 >= 36),
mutate `ma -= (35 + lv)` debits 36 (52 → 16 in the snapshot).
Both wait_input blocks fire in order, compass/how-far menu_
branches read 2/1, pa_teleport_step iterates `distance * 2 = 2`
times adding 1 to y each iteration ((12,8) → (12,10), both
walkable grass), advance_hour fires once (snapshot
hour_of_day=13 — same as #8b, which also advances once during
cast). update_map repaints the new tile.

**What's not pinned (deferred).** The not_enough_pts else-arm
(ma < 36 + lv) needs a different fixture seed or cast order. A
mountain-blocked direction (no-op move) would be a separate
fixture if we ever care to assert the bounds-check explicitly;
not worth a dedicated case today.

**What's not done — by spell.** One fixture left from the §22.8
list: priest banish (#8a). The plan is unchanged from §22.10
— path 2 (earthquake bridge), two priests in slots 1+2 (seed 2
wi 67/63 — both qualify with RYL_FORCE_PARTY_LV=5), slot 1
casts earthquake to spawn a cave + dive into a 5..18-level
dungeon, slot 2 casts banish to set state.banished and trip
the level-3..5 lvl[] zero-out in dungeon.c.

**Recommended order — what to do next.**

  1. **Land priest-banish (§22.5 #8a)** via path 2.
     RYL_FORCE_PARTY_LV=5, two priests in slots 1+2 (seed 2
     wi 67/63 — both qualify). Slot 1 casts earthquake
     (`Z 1 7 E`) → spawns cave, enters new dungeon. Slot 2
     casts banish (`Z 2 4`) — sets state.banished, success
     arm ends with state_banished mutate. The earthquake-
     spawned dungeon has 5..18 levels (random); pin a seed
     where the level-1 random fill is reproducible.

  2. **§22.F speak_missions.json5 rewrite + parser retire.**
     Untouched. Plan unchanged from §22.7/§22.8/§22.9/§22.10.

  3. **§22.G docs/dsl.md single-doc reference.** Untouched.
     Should follow §22.F so the doc reflects the post-
     migration surface.

**Cleanup follow-up — `dungeon_encounter` reserved slots.**
Carry-over from §22.10. Still not blocking; flag for the post-
§22 hardcoded-indices cleanup pass.

**Estimated remaining effort.** Banish ~60 min of spell-fixture
work remaining. §22.F is ~0.5-1 session; §22.G is ~0.5 session.
Cumulative ~1 more session to fully close §22.

**Bar per session.** Same as §22.8/§22.9/§22.10: `make check`
and `make testasan` both green at every commit.

§22.12 — Open-work audit (post-§22.11 snapshot, 2026-04-26)

The plan is a long historical record (4800+ lines) and the
running narrative makes it hard to tell at a glance what's
actually shipped vs still open. This subsection is a status
snapshot — pure pointers into the existing sections, no new
plan content. Use it to answer "is X done?" without reading
4000 lines of context.

**DONE — shipped and locked by fixtures.**

  - §3 phases 1-5b: full engine port to ANSI C / portable
    build. (See §3 commit list.)
  - §16 hardcoded-indices sweep: P1..P6 priority list closed
    or consciously deferred. (See `project_hardcoded_indices_doc.md`
    in memory; §16.2 is closed per memory.)
  - §17 DSL groundwork (play_script, screen / journal /
    set_flag / emit / mutate / engine_call). §17-F migrations
    (towne smith / tavern / inn / merchant). (See §17.)
  - §18 monster bestiary + tier system. (See §18.)
  - §19.1 town-level replay fixtures starter bank.
    §19.2 journal into party state / save.
    §19.3 data-driven party size + RYL_PARTY_MEMBER_MAX
    smoke fixture.
  - §20.L1 dungeon special-encounter switch (commit 1962bd9).
  - §20.L2 dungeon level-event switch (915 → 171 LOC,
    commits 28f4d40..fc599b3).
  - §20.L7 camp / hunt / lodge flow lifts (4 commits e355ef8
    .. 03d2eff). §20.18 has the closure write-up.
  - §20 TINY bucket: T1 (interact.c → terrain.json5,
    src/interact.c is now 20 lines), T2 (towne.c has_church
    → world.json5 town record). T3/T4 status pending — see
    PARTIAL below.
  - §20 SMALL bucket S1 (helper additions), S5 (combat
    balance constants in world.json5 — encounter_split,
    flee_chance, defend_cap, hp_defense_div, drop_tables,
    encounter_foe_count_max all present), S11 (formula
    lifts: encounter_h1_scaled, encounter_h2_scaled,
    reward_xp_per_survivor, reward_gold_party all live).
    S2 mostly done per §20.17 (see PARTIAL for residual).
  - §20.16 fold-time script-body symbol validator
    (commit 55d0b1f).
  - §20.19 fixture queue: wilderness-hunt, wilderness-camp,
    towne-thord-tavern-bargirl-lodge all landed.
  - §20.20 #2 (`emit_indexed` / `txi`-style format-id-by-int).
  - §22.E op-shape consistency: items 9 + 10 landed; items
    11 + 12 consciously skipped (engine registry unification
    is cosmetic).
  - §22.5 spell-fixture queue: #1 attack, #2 frost, #3 fire,
    #4a confusion-hold, #4b antimagic, #5 mage-death, #6
    priest-heal, #7a restoration, #7b rebirth, #8b
    revelation, #8c teleport, #8d earthquake — twelve of
    thirteen fixtures. (#8a banish remains; see NOT DONE.)
  - §22.6 reward-gold display fix.
  - §22.9 replay-knobs centralization (RYL_FORCE_PARTY_LV,
    RYL_FORCE_DEAD_SLOT, RYL_FORCE_ENCOUNTER, etc. now in
    src/replay_knobs.{c,h}).

**PARTIAL — landed in part, residual debt tracked.**

  - **Phase 5c (§7) — `pm_t` / `pa_*` migration.** Per
    §20.17's table, every file except driv.c has helpers
    outnumbering raw `state.*` access. Current raw
    `state.p[cpl]` / `state.p[state.cpl]` counts (post-§22.11):

        | file        | raw state.p[*] hits |
        |-------------|---------------------|
        | driv.c      | 37                  |
        | ydarg.c     | 13                  |
        | script.c    | 8                   |
        | inventory.c | 6                   |
        | missions.c  | 6                   |
        | primitives.c| 6 (abstraction home — fine) |
        | towne.c     | 5                   |
        | res.c       | 2                   |
        | party.c     | 45 (abstraction home — fine) |

    driv.c remains the H2 target per §20.17's prose — its
    hits are mostly main-loop dispatch reading
    `state.last_terrain` / `state.p[cpl].time_left`.
    ydarg.c is the second-largest residual. The other files
    are cosmetic.

  - **§20 TINY T3/T4 (driv.c hardcoded paths).** T3
    `disptext("data/score.txt")` etc. — at least four
    sites still in driv.c (lines 481/495/511/523). T4
    `fopen("data/map.dat", "rb")` — fopen with literal
    `path` strings still appears at driv.c:372/412/550/580/858.
    Neither has been swept; both are quick wins.

  - **§20 SMALL S6 (wilderness/rest balance constants).**
    Most landed during §20.L7 (rest.json5, hunt.json5
    constants). Verify residuals during banish-fixture
    work — anything still hardcoded inside subs.c is
    fair game.

**NOT DONE — explicitly queued.**

  - **§22.5 #8a banish fixture.** Last remaining lane-A
    spell fixture. Plan unchanged from §22.10/§22.11
    (path 2, two priests, RYL_FORCE_PARTY_LV=5).
  - **§22.F speak_missions.json5 rewrite.** The
    speak_missions micro-DSL evaluator in `speak.c`
    still alive; rewrite plan in §22.7. Untouched
    through §22.11.
  - **§22.G docs/dsl.md single-doc reference.** Spec in
    §22.7. Untouched. Should follow §22.F.
  - **§20 LARGE L3 (battle.c item-effect switch
    cases 100-111).** The switch is alive at battle.c:
    194-403. Same surface as §23.B in the next section.
  - **§20 LARGE L5 (monster magic table).** Alive at
    battle.c:625-740 (the `g==5` block). Same surface
    as §23.A.
  - **§20 LARGE L6 (battle narration sweep).** Hardcoded
    `tx("English text\\r\\n")` calls in battle.c outside
    the §23.A/B targets — probably ~20-30 sites. Not yet
    swept; consider folding into §23.A/B as those
    extractions go.
  - **§20.20 #1 / #3 / #4 (DSL ergonomics).** Class-id
    symbol args, `{path: '…'}` expr form, default-`{0}`
    speak op town arg — all deferred until a flow with
    a clear win surfaces.
  - **§22.10 cleanup — `dungeon_encounter` reserved
    slots 10..19.** Carry-over follow-up; not blocking.
    Confirm nothing indexes into 10..19, then collapse
    if safe.

**NEEDS VERIFICATION — stale plan, may be done.**

  - **§20.12 unwired DSL hooks (`spawn_encounter`,
    `call`).** Plan refers to `eval.c:1267-1271` but
    src/eval.c no longer exists. Greps for
    `spawn_encounter`, `"call"`, and `rdie` in src/ all
    come up empty — the ops appear to have been removed
    from the DSL surface entirely, not wired. Treat
    §20.12 as superseded; if a script ever needs
    `spawn_encounter` again, it'd be a new design.

**CONSCIOUSLY SKIPPED — not coming back unless requirements change.**

  - **§21 reentrancy / `ryl_ctx`.** §21.6 verdict was
    "worth it only if a real multi-tenant use case
    materialises." None has. Treat as documentation
    only.
  - **§22.E items 11 + 12 (engine registry unification,
    op-shape micro-inconsistencies).** Cosmetic; the
    rule "everything new flows through eval_expr" is
    self-evident from current code.
  - **§3 #6 C99 vs C++17 subset.** Decision deferred
    indefinitely; the C99 path is working.

**Summary at a glance.** §22 closes after #8a + §22.F +
§22.G land — ~1 more session. The pre-§22 debt that
genuinely affects code shape is (a) Phase 5c residuals
in driv.c / ydarg.c, (b) the §20.LARGE combat extractions
that were already re-scoped into §23.A/B, (c) the four
small driv.c path-literal sweeps (§20 T3/T4). Everything
else is either shipped or consciously deferred.

§23 — engine→data extraction (post-§22 planning)

This section is a planning lookahead, not an active queue.
§22 finishes the lane-A spell migration and the speak/missions/
docs cleanup; once those land there are still meaningful
chunks of native C that *should* live on the data side. The
asymmetry is most visible in `battle.c`: the player-facing
spell flows are now fully scripted (priest.json5,
mage.json5), but the **monster** behaviour — including
the spells monsters cast and how much damage they do — is
still a hardcoded native switch. Same shape on the item-
spell side: `(U)se Item` dispatches to a 12-arm switch that
is verbatim 1980s-style native code. The goal of §23 is to
finish closing that gap so the engine ends up looking like
a script *runtime* (input + RNG + bindings + emit/mutate
opcodes) and not a script *implementation*.

**§23.A — battle.c monster nasty-magic block (lines 625-740,
~115 LOC).** The single highest-leverage extraction. Today
this is:

```
if (g == 5) {
  if (!antimagic && sp && ryl_chance(15 + sp/2)) {
    switch (ryl_random(0, 3)) {
      case 0: /* Frost Finger */ dp = h2/5 + ryl_random(0,3); ...
      case 1: /* Cloud of Fire */ dp = h2/5 + ryl_random(0,4); ...
      case 2: /* Hold Creature */ playerheld = 1;
      case 3: /* Confusion    */ playerconfused = 1;
    }
  } else {
    /* plain melee — ws hit-roll + mh[x]/6 + ryl_random(0,9) */
  }
}
```

Every probability and every damage formula is a literal in
the source: `15 + sp/2` for the cast gate, `h2/5 + ryl_random
(0,3)` for frost, `h2/5 + ryl_random(0,4)` for fire, the
4-way uniform pick across the spell list, the implicit
"every monster knows the same four spells" assumption. The
right shape for this on the data side mirrors what we
already have for player spells: a `monster_spells.json5`
shard with `frost`/`fire`/`hold`/`confuse` entries (or
whatever name set we want — the bestiary could plausibly
gain more), each with a `do:` body in the existing DSL.
The cast-gate formula and the spell-pick weights both
become res-resolved formulas. The `g == 5` block then
becomes: `for each undead monster, run res_monster_turn(...)`
where the runtime walks the data-side decision tree. The
melee-attack arm should also extract — same `ryl_chance(ws+1)`
gate and `mh[x]/6 + ryl_random(0,9) - obdamage(armour)`
formula now lives in C but is structurally identical to a
spell `do:` body.

A nice side effect: per-monster spell sets become possible
without touching C. The bestiary already has `sp` as the
"spellpower" hint; today it only modulates the cast gate.
With `monster_spells` keyed by tier or by monster name, the
designer surface widens significantly without engine work.
Effort estimate: ~2-3 sessions to migrate the four spells +
the gate + the melee arm, with one fixture per arm
(combat-monster-{frost,fire,hold,confuse,melee}) to pin
RNG state across the boundary. Likely needs a small DSL
extension for monster-side targeting (`pick_alive_player`
helper + `damage_all_alive_players` loop), but both fit
the existing emit/mutate vocabulary.

**§23.B — battle.c item-spell switch (cases 100..111, lines
194-403, ~210 LOC).** Twelve hardcoded magic-item effects
dispatched on `oc` (object code). About half already use
`res_text(...)` for narration but the **dispatch table**
and all damage formulae are native — `ryl_random(5,11) +
pm_lv(...)` for case 100 (chill), `pm_lv(...) * ryl_random
(3,8)` for case 101 (burn), etc. Each case is structurally
a tiny spell: take target / pick alive / mutate hp /
maybe tx0 narration / pause. The right shape: a
`magic_items.json5` shard keyed by object code with `do:`
bodies, indexed via `res_magic_item(oc)` analogous to
`res_spell("priest", slot)`. The C dispatcher reduces to:
read inv letter, look up oc, `play_spell_body(...)`. Cases
105/108 (heal-target / rebirth-target) need a target
prompt + key read — the DSL already has menu_branch +
key_var so this is straightforward; cases 109/110 (axe,
handgrenade) need a "damage all foes" loop that mirrors
the monster-fire arm above so §23.A and §23.B share that
helper. Effort: ~2 sessions; can be sequenced after §23.A
so the monster-loop helper is in place. Three or four
fixtures cover the per-arm shapes (single-target damage,
all-target damage, target-prompt heal, target-prompt
rebirth, narration-only fizzle).

**§23.C — battle.c hit-roll and miscellaneous combat
constants.** Smaller items that carry no large data shape
of their own, but are stylistic eyesores once §23.A/B
land. The player-attack hit-roll `ryl_chance(x + 1)` and
damage `obdamage(wp) + ryl_random(0,4) + pm_hp/hpd` are
half data-driven (`hp_defense_div` is in res) and half
literal. The confusion divisor `x /= 3` (line 534), the
battle-cry `ws -= 4` (482), the defend `ws -= 2` (508),
the desperate-flee `ryl_chance(31)` (776), and the
`pm_lv` defend heal at 490-494 are all literals that
belong in `combat.json5`. None require new DSL — just
res-int lookups. Effort: ~1 session of mechanical sweeps,
no fixture additions needed (existing combat fixtures
pin the formulas).

**§23.D — driv.c character-creation thresholds.** Class-
passing thresholds (lines 1528-1567) are 50/50/50 literal
checks against `ws`/`wi`/`ch` per class. The class table
in `world.json5` already names classes — extending each
record with `min_stat: { name: 'wi', value: 50 }` (or a
list for multi-stat classes if we ever want them) closes
this. The `ryl_random(15, 70)` stat-roll range (lines
1463-1467) and the starting-state init block (1580-1609:
x=12, y=9, gold=1000, torches=5, food=100, time_left=200,
hours_rested=14) are all numeric literals that should be
in `driv.start.*` or `world.start.*`. Effort: ~0.5 session;
existing title-create-party fixtures pin the values.

**§23.E — bestiary special-encounter switches (driv.c lines
~478, 688 outside the create-party block; dungeon.c level-
loop key dispatch).** Survey-only at this point — the
patterns look more like UI dispatchers than data, but worth
a once-over after §23.A/B to confirm there's no extraction
opportunity hiding in the wilderness/dungeon command
handlers. The dungeon level-loop `if (c == 'D') ... if
(c == 'A') ...` chain (dungeon.c:71-169) is fundamentally
input dispatch and probably stays in C; the descend/ascend
*actions* are short enough that a data table buys little.

**§23.G — driv.c race switch (queued 2026-04-26 #3).** The
character-creation race-pick block at driv.c:1540-1575 is a
six-arm `switch (state.p[a].ra)` that dispatches on the int
race id (1=human..6=??). Each arm sets per-race stat caps /
class restrictions / a couple of localized adjustments. Same
shape as §23.B was — keep state.p[].ra as an int (it's
load-bearing across saves and pm_ra accessors), but lift the
switch body into `world.json5`'s existing `enums.race.<key>`
records as a `class_caps:`/`stat_adj:`/etc. body, dispatched
through a new `res_race_dispatch(ra)` (or a script DSL
`do:` body if the per-race logic warrants it). Effort: ~1
session if the per-race bodies are simple enough to be plain
data; ~1.5 sessions if they need DSL bodies. Existing
title-create-party fixtures cover all six paths byte-for-byte.

**§23.H — ydarg.c scenario / monster-type switches (queued
2026-04-26 #3).** Several numeric `switch` blocks across
ydarg.c — case 1=Monster / case 2=Pitfall / case 3=...
(line 94-150 chunk), case 50=??? (line 192), the multi-arm
encounter switch around line 237-310, the level-key chain
around 515-540. Same migration shape as §23.B — the int
switch arms become data-driven dispatch, with the int still
stored in state but the per-case body authored in JSON5.
Some arms are very thin (single tx0 + delay); those collapse
to a single text shard. The encounter-type and level-key
arms need DSL bodies. Worth a survey pass first (one session)
to decide which arms are worth migrating vs. which stay in C
because the C body is genuinely UI dispatch (input-key
matching that doesn't read like data). Effort: 1 survey
session + 1-2 migration sessions, depending on how much of
ydarg.c's switch surface qualifies. Cave-descent /
wilderness-camp / wilderness-cave-descent fixtures cover
some paths; new fixtures may be needed for the
special-encounter arms.

**§23.I — Multi-screen narration helper (queued 2026-04-26
#4).** Pervasive pattern across ydarg.c / towne.c / dungeon.c
(sample at ydarg.c::confrontation lines 432-491): runs of
`tx0(res_text(id)) + ryl_pause() + clw()` where each screen
is its own text key, even though the C-side ceremony is
identical for every step. ydarg.conf alone has 14 such
keys split into 4 narration runs (act1: trapdoor_slam →
fools, 2 screens; act2: corpses → impress → sphere → mages
[bind p1_name] → curses → trident → commence, 7 screens;
act3: trident_falls → not_over → cloak → dust → congrats,
5 screens; act4: heroes → signoff, 2 screens) — the boundaries
fall on encounters / state mutations, not on rendering shape.

**Shape A** (sugar) — flat list-of-lists; transition is
`pause + clw` between, no trailing pause:

    prelude: [
      ['The last trapdoor', 'slams shut...', ''],
      ['- Fools! ...', ''],
    ],

**Shape B** (annotated) — when transitions vary
(`ryl_delay` between screens, "no pause" handoff, etc.):

    prelude: [
      { lines: [...], next: 'pause' },           // default; can omit
      { lines: [...], next: { delay: 2000 } },
      { lines: [...], next: 'none' },
    ],

C-side surface: one new helper `tx_narrate(const char *id,
const ryl_bind *binds)` that walks the array, applies the
default transition (or per-screen `next:` override), and
threads `binds` through tx_fmt for screens that interpolate
(`mages: '{p1_name}'`). A bare `[...]` is sugar for
`{ lines: [...] }` with default transition.

Where it pays off: ydarg.confrontation collapses ~15
tx0/pause/clw triplets into 4 tx_narrate calls + 5
encounter/state lines; ydarg() approach run (approach →
jaara_form → jaara_speech) collapses 1:1; assorted
towne.c and dungeon.c openers similarly.

Tradeoff: once a screen is referenced from elsewhere
(shared atom), it has to stay a top-level key. Audit each
candidate run for cross-references before folding. None of
ydarg.conf's screens look shared.

Effort: ~0.5 session for the helper + ydarg.conf
conversion as the first user; further conversions roll in
opportunistically as adjacent work touches each call site.
Independent of §23.G/H — can land before, between, or
after.

**Cross-cutting — DSL gaps.** §23.A and §23.B will likely
surface two genuinely missing primitives:

  - **target-set iteration** with a predicate
    (`for_each_alive_player`, `for_each_alive_monster`).
    Today individual spells like priest.heal use `menu_
    branch` + `pm_*` accessors per slot; a fire-hits-all-
    party shape needs a loop over the bound combat ctx.
    Already half-present in `mage.json5` via the combat
    ctx struct; needs to formalize into a DSL op.
  - **typed RNG bindings for combat** — frost damage is
    `h2/5 + random(0,3)` where `h2` is the encounter HP
    cap, currently a C local. The combat ctx already
    plumbs `mh[]`, `noc`, `ws`, etc. through to scripted
    spells; extending the binding set with `h2` /
    `monster_idx` / `target_idx` and exposing them via
    `{ var: 'h2' }` lookups in the DSL covers this.

Neither is a deep change; both already have analogues in
the §22 lane-A migration. Plan to land the DSL pieces in a
prep commit before §23.A so the monster spells can use the
real ops on day one.

**Recommended sequencing.** §23.A first (highest leverage,
forces the DSL prep), then §23.B (rides on §23.A's helpers),
then §23.C/D in parallel as small mechanical sweeps. §23.E
as a survey only, possibly closed without code changes.

**Estimated total effort.** ~6-8 sessions across §23.A-D.
The result: `battle.c` shrinks from ~850 LOC to perhaps
350-400 LOC of pure runtime (input loop, RNG plumbing, ctx
binding, victory/loss handoff). The bestiary, item-spell
table, monster-spell table, and class threshold table all
become first-class data shards a designer can edit without
a recompile. That's the same shape player spells have today
post-§22.

**Why post-§22.** §22 is mid-flight and the spell-fixture
queue (#8a banish + §22.F + §22.G) needs to land first to
keep the lane-A migration coherent. §23 starts after §22
closes; sequencing the other way would mean rewriting fixtures
mid-extraction. The §22.5 fixture set (combat-mage-* and
combat-attack-win) already pins the player-side combat path
byte-for-byte, so §23.A's monster extractions land against
known-good baselines.

**§23.F — ydarg "stairs" item-trade flow.** The Baron Ydarg
endgame in `src/ydarg.c` includes a mage-room subflow where
the player descends a staircase, picks one of five quest
items to surrender, and on success the wizard awards a stat
boost. Currently this is a `switch (d - 48)` over the four
special-item arms (fireshield / mastercrown / jazzyfoot /
bracelets) plus a transparent "T" teleport-out branch — raw
ASCII-digit math, four near-identical case bodies, all
inline. The shape mirrors a small dungeon: keypress prompt,
per-arm gating + state mutation, success/failure narration.
The right lift is into `data/world/scripts/ydarg.json5`
(new shard) as a `menu_branch` over `keys: ['1','2','3','4',
'5','T']` with each `then:` body checking the matching
`pa_special` via expr (or via a new flag) and using `mutate
state_*` lvalues to clear it on success. Replaces the raw
`d - 48` switch with the same pattern §20 L7 used for the
camp/hunt/lodge flows. Effort: ~0.5 session; can land any
time after §22 closes — independent of §23.A/B's combat-side
work since this is exploration UI, not combat.

§22.13 — Continuation prompt (session close, 2026-04-26)

Continuation of §22.11. Three commits this session, both
gates green at every commit:

  - `b62d8a7` §22.5 #8c wilderness-priest-teleport fixture.
    Path 1 (seed-5 wild-roll cave entry, reused verbatim
    from #8b revelation), slot 1 priest at lv 1 covers
    cost (35 + 1 = 36, ma 52 → 16). Two literal-`\n`
    absorbers feed the back-to-back ryl_pause narration
    blocks. Compass=2 + how-far=1 drives an actual coord
    change ((12,8) → (12,10)) — direction 8 (north) was
    a no-op due to a mountain at (12,7); env documents
    the terrain dependency for future teleport fixtures.
    §22.11 captures the full write-up.

  - `493485e` §23 post-§22 engine→data extraction analysis.
    Lookahead-only — no code changes. Five sub-sections:
    §23.A monster nasty-magic block (battle.c g==5,
    ~115 LOC), §23.B item-spell switch (cases 100-111,
    ~210 LOC), §23.C combat-constants sweep, §23.D driv.c
    character-creation thresholds, §23.E dispatcher survey.
    Calls out two DSL gaps to land before §23.A starts
    (target-set iteration with predicate, typed combat-ctx
    bindings). Estimated ~6-8 sessions across §23.A-D;
    battle.c expected to shrink from ~850 to ~350-400
    LOC of pure runtime.

  - `12445d2` §22.12 open-work audit. Pre-§23 status check
    so the 4800-line plan is tractable. Five buckets
    (DONE / PARTIAL / NOT DONE / NEEDS VERIFICATION /
    SKIPPED). Pre-§22 stragglers that genuinely matter:
    Phase 5c residuals (driv.c 37, ydarg.c 13 raw
    `state.p[cpl]` hits), §20 TINY T3/T4 (driv.c
    hardcoded `disptext("data/...")` and `fopen(...)`
    literals, 8+ sites), and the §20 LARGE L3/L5 combat
    extractions that §23.A/B already absorbs. §20.12's
    eval.c reference is stale — `spawn_encounter` /
    `call` no longer exist in the source tree; treat
    as superseded.

**What's left for §22.** One spell fixture (#8a banish)
plus two cleanup tasks (§22.F speak_missions rewrite,
§22.G docs/dsl.md). ~1 session total. Plan unchanged
from §22.11:

  1. **Land priest-banish (§22.5 #8a)** via path 2.
     RYL_FORCE_PARTY_LV=5, two priests in slots 1+2
     (seed 2 wi 67/63 — both qualify). Slot 1 casts
     earthquake (`Z 1 7 E`) → spawns cave + dives into
     the new dungeon. Slot 2 casts banish (`Z 2 4`) —
     sets `state.banished`, success arm ends with the
     state_banished mutate and the level-3..5 lvl[]
     zero-out in dungeon.c (battle.c → dungeon.c bridge
     lives at battle.c:99-105 still — banish flips the
     flag, the next dungeon iteration sees it and
     prunes). The earthquake-spawned dungeon has 5..18
     levels (random); pin a seed where the level-1
     random fill is reproducible.

  2. **§22.F speak_missions.json5 rewrite + parser
     retire.** Plan in §22.7. The speak_missions
     micro-DSL evaluator in `speak.c` still alive;
     convert each NPC role's stages to a script that
     fires the right play_script. Removes the parser
     entirely.

  3. **§22.G docs/dsl.md single-doc reference.** Spec
     in §22.7. Should follow §22.F so the doc reflects
     the post-migration surface (no speak_missions
     micro-DSL listed).

After §22.G, the milestone is closed. §23 picks up next
— start with the DSL prep commit (target-set iteration +
typed combat-ctx bindings) before §23.A's monster spells.

**Pre-§22 stragglers worth flagging.** Per §22.12:

  - **Phase 5c (driv.c, ydarg.c).** ~50 raw `state.p[cpl]`
    hits between the two files. driv.c is the H2 target
    per §20.17. Mechanical sweep, no fixtures needed —
    existing fixtures pin the byte-stream.

  - **§20 TINY T3/T4.** driv.c `disptext("data/score.txt")`
    / `fopen("data/map.dat")` literals at lines
    372/412/481/495/511/523/550/580/858. Add `res_ansi_path()`
    + `res_map_path()` helpers; sweep. Quick win.

  - **§22.10 dungeon_encounter slots 10..19 cleanup.**
    Confirm nothing indexes into the reserved hole, then
    collapse the enum if safe. Carry-over follow-up.

These don't block §22 close. They're candidates for a
hygiene session between §22.G and §23.A.

**Bar per session.** Same as §22.8/§22.9/§22.10/§22.11:
`make check` and `make testasan` both green at every
commit.

§22.14 — §22 closure + hygiene session (2026-04-26)

Continuation of §22.13. Six commits this session, both gates
green at every commit:

  - `63a7a80` §22.F speak_missions micro-DSL retired. Each
    NPC role's stages now lives as a `speak_missions.<role>`
    dispatch script (`scripts:` subtree) — an if-chain over
    flag values that resolves npc_name / npc_loc / dx / dy
    bindings via two new engine_bind helpers (`npc_name_at_
    role`, `npc_loc_at_role`) and `let` over the existing
    `wld_location_x/y` engine_int, then `play_script`s the
    matching `speak.<x>.<y>` body. The new `play_script` op
    forwards merged caller bindings + rt_env entries to the
    nested call. Retires `mission_dispatch` + `mission_resolve_
    bindings` in `speak.c`, `mission_spec_` / `mission_stage`
    / `mission_binding` types in `speak.h`, and `fold_speak_
    missions` + `parse_bind_kind` in `res.c`. New helper
    `res_role_for_slot(slot)` reverses `npc_roles:` so
    `speak.c::npc_mission_hint` only needs the talker's slot.

  - `ee8ba68` §22.G docs/dsl.md single-doc reference. Replaces
    the historical Phase-6d design narrative with the post-§22
    surface: worldfile shape, script anatomy + text-id
    resolution, every op, expression vocabulary, mutate
    lvalues, the three engine registries (engine_call /
    engine_int / engine_bind names + sigs), automatic
    placeholder bindings ({party_max} / {inventory_max} /
    {cost:itemkey}), die_at error contract, three worked
    examples (small spell, speak_missions dispatcher, formula).
    `src/script.h` cross-links to docs/dsl.md as the user-
    facing reference.

  - `5371e5b` Phase 5c residuals — driv.c + ydarg.c. Sweep
    the last 50 raw `state.p[state.cpl].*` hits between the
    two files through pa_*/pm_* helpers. New helpers in
    party.h/c: `pa_party_name()`, `pa_journal_n()` /
    `pa_journal_line(i)`, and a parameterised `pa_special(int
    which)` / `pa_set_special(int which, int v)` keyed by
    the existing PS_* enum (covers ydarg's silverkey=2
    state-bump that party_award_special's set-to-1 doesn't
    express). driv.c + ydarg.c residual count drops from
    50 to 0.

  - `e61be95` port-plan §23.F. New §23 lookahead entry for
    the ydarg "stairs" item-trade flow — a `switch (d - 48)`
    over four special-item give-arms surfaced during the
    Phase-5c sweep. Mechanically identical shape to the
    §20 L7 camp/hunt/lodge flows; ~0.5 session lift into
    a new `data/world/scripts/ydarg.json5` shard.

  - `3271b14` §20 T3/T4 hardcoded data/ paths. New
    `res_data_path(leaf, out, out_sz)` helper alongside
    `res_ansi_path` / `res_map_path`. Sweeps the eight
    remaining `disptext("data/...")` and `fopen("data/...")`
    literals (driv.c S/D/H menus + ydarg.c heroes.txt log).
    Asset-root rename is now one edit.

  - `b258cb0` §22.10 dungeon_encounter slots 10..19 collapse.
    Reserved hole was Amiga-numbering ballast; verified
    nothing indexes into 10..19 (lvl[]'s only numeric range
    check is `>= 3 && <= 5`, below the gap; placements use
    symbols; lvl[] isn't persisted). Quest events shift from
    20..38 to 10..28. Updated dungeon.c + dungeons.json5
    docstrings to match.

  - `d4c06d2` §22.5 #8a wilderness-priest-banish fixture.
    Path 2 (earthquake bridge): two priests in slots 1+2,
    RYL_FORCE_PARTY_LV=5 brings both to ma 99/95. Slot 1
    casts earthquake (`Z17E`) → cave + dungeon descend; on
    level 1 the random fill rolls `gold` (lvl[1]=2: 132 gp);
    its wait_input is absorbed by a single \n. Slot 2 then
    casts banish (`Z24`) — six-screen incantation, success
    arm ends with state_banished=1, dungeon.c's post-spell
    hook zeroes lvl[i] in 3..5. Snapshot pins
    state.banished=1, slot 1 ma=39 (99-60), slot 2 ma=6
    (95-90 + 1 from a foodhandler mana_regen tick during
    earthquake's advance_hour beat). Closes §22.5 — 13/13
    lane-A spell fixtures landed.

**§22 status — CLOSED.** All bullets from §22.13's "what's
left" list shipped:
  - #8a banish fixture: landed.
  - §22.F speak_missions rewrite: landed (parser retired).
  - §22.G docs/dsl.md: landed (cross-linked from script.h).

The pre-§22 stragglers from §22.12 also closed:
  - Phase 5c driv.c/ydarg.c residuals: 0 raw hits remaining.
  - §20 TINY T3/T4 path literals: lifted via res_data_path.
  - §22.10 dungeon_encounter 10..19 reserved slots: collapsed.

**Continuation prompt — what to do next session.**

The repo is in a good rest state: §22 closed, the post-§22
hygiene queue from §22.12 cleared, and §23 lookahead already
written (§23.A monster nasty-magic, §23.B item-spell switch,
§23.C combat constants, §23.D char-creation thresholds, §23.E
dispatcher survey, §23.F ydarg stairs). Sequencing per §23's
"recommended sequencing" prose:

  1. **DSL prep commit before §23.A.** §23 calls out two DSL
     gaps that should land first: (a) target-set iteration
     with a predicate (`for_each_alive_player`,
     `for_each_alive_monster`); (b) typed RNG / combat-ctx
     bindings — frost damage `h2/5 + random(0,3)` reads `h2`
     as a C local today, the combat ctx already plumbs
     `mh[] / noc / ws / etc.` so extending the binding set
     with `h2 / monster_idx / target_idx` and exposing them
     via `{ var: 'h2' }` is the natural shape. Both have
     analogues in the §22 lane-A migration; neither is a
     deep change. ~1 session.

  2. **§23.A — battle.c monster nasty-magic block** (lines
     625-740, ~115 LOC). Highest-leverage extraction: the
     `g==5` undead block (cast-gate `15 + sp/2`, four-spell
     pick across frost/fire/hold/confuse, melee fallback
     with `mh[x]/6 + random(0,9) - obdamage(armour)`). Lift
     into `monster_spells.json5` shard with `do:` bodies
     and a `monster_turn` runtime call. Per-monster spell
     sets become a data edit. Forces the DSL prep. ~2-3
     sessions.

  3. **§23.B — item-spell switch** (battle.c:194-403, cases
     100..111, ~210 LOC). Rides on §23.A's helpers (target
     iteration + combat-ctx bindings already extended).
     Items.json5 grows per-id `effect:` specs; the case
     dispatch becomes `apply_item_effect(id, ctx)`. ~2
     sessions.

  4. **§23.C/D in parallel** — combat-constants sweep and
     driv.c char-creation thresholds. Mechanical, small.
     ~1 session each.

  5. **§23.F (or §22.5 #8a-style fixture for it).** ydarg
     stairs item-trade lift to data/world/scripts/ydarg.json5
     — `menu_branch` over keys ['1'..'5','T'] with per-arm
     `pa_special` gates and clears via `pa_set_special`
     mutate-style. Independent of §23.A/B's combat work,
     so it's a good warm-up commit if a session wants
     something contained. ~0.5 session.

  6. **§23.E survey.** Confirm there's no extraction
     opportunity in driv.c wilderness-loop / dungeon level-
     loop dispatchers; close as documentation if the
     conclusion is "input dispatch, stays in C". ~0.25 session.

**Estimated total for §23.** ~6-8 sessions A-D + §23.F's
0.5 + §23.E's 0.25 ≈ ~7-9 sessions. The result: `battle.c`
shrinks from ~850 LOC to ~350-400 of pure runtime; the
bestiary, item-spell table, monster-spell table, and class
threshold table all become first-class data shards.

**Pre-§23 pre-flight notes:**

  - `make check` + `make testasan` both green at HEAD
    (commit `d4c06d2`). The §22 fixture set (12 lane-A
    spell fixtures + the wilderness/towne battery) pins
    every player-facing combat path byte-for-byte, so §23.A's
    monster extractions land against known-good baselines.
  - The new `play_script` op in §22.F is genuinely useful
    beyond speak_missions — any future flow that wants to
    fan out into nested scripts after gating gets the same
    `forward bindings` mechanic for free. Worth keeping in
    mind for §23.A's monster_turn.
  - docs/dsl.md is now the single doc reference for the DSL.
    Keep it in sync as §23 adds ops (target iteration,
    combat-ctx bindings) — author rule: every new
    op/expr/lvalue/registry entry gets a one-paragraph
    addition there at the same commit it lands.

**Bar per session.** Same as §22.8/§22.9/§22.10/§22.11/§22.12/
§22.13: `make check` and `make testasan` both green at every
commit.

## §23.B closed (2026-04-26 #3)

`battle.c`'s 12-arm magic-item switch (cases 100..111,
~210 LOC at lines 194-403) is now data-driven. Every
magic item carries an `effect:` DSL body in
`data/world/items/magic_items.json5`; the Use-Item
branch in `battle.c` looks up the body via
`res_magic_item_effect_node(oc, &slug)` and dispatches
through `play_spell_body`. No `case <int>:` for magic
items remains in C.

**Five group commits + one closeout commit on
`lagergren/port1`:**

  1. `4fc6962` Group A — gem_of_confusion / gem_of_blindness
     / flute_of_madness. RNG-free narration items, used to
     prove out the dispatcher infra (resolver +
     play_spell_body call site) before the harder cases
     landed. Pre-existing flute_of_madness placeholder
     (an inert `['seq', ...]` from the pre-§22 era) was
     replaced with the real DSL body.

  2. `4e2eead` Group B — frost_wand / drums_of_death.
     New `pick_first_alive_monster` engine_int (mirrors
     the legacy `for (i=1, who=0; i<=noc; i++) if
     (!deadc[i]) { who=i; break; }` idiom shared by both
     items). frost_wand's pre-existing `tx("...KILLED!")`
     missing-`\r\n` bug was fixed inline by routing
     through `battle.killed`, matching every other kill
     site in the file.

  3. `f19b5f1` Group C — fire_wand / kaels_axe /
     holy_handgrenade. Reused `for_monster` (RNG advances
     on dead too — fire_wand's identity) and
     `for_each_alive_monster` (RNG only on alive — axe and
     handgrenade); no new engine ops. Mul-order preserved
     for fire_wand's `pm_lv(...) * ryl_random(3, 8)`.

  4. `2537f4a` Group D — staff_of_healing /
     gem_of_resurrection. Drove a small refactor of the
     dispatcher shape: every body now owns its inventory
     consume via the new `pa_set_inventory` engine_call
     (with the dispatcher binding `inv_slot`), so 108 can
     defer the consume to its success branch — preserving
     the legacy "no turn spent on a typo" semantics.
     Group A/B/C bodies updated to start with
     `engine_call: 'pa_set_inventory', args: [{ var:
     'inv_slot' }, 0]` so all twelve items share one
     mechanism. New `pm_full_heal` / `pm_set_dead`
     engine_calls and a new `script_combat_ctx.action_retry`
     flag (105 doesn't use it; 108 sets it on the
     not-dead branch and battle.c's dispatcher checks
     after `play_spell_body` returns and `goto combat`s
     if set).

  5. `3768f2e` Group E + closeout — blessing_scroll /
     staff_of_curing. `for_member` covers both bodies
     (party-wide stat boost / party-wide full heal). The
     entire `else switch (oc) { case <N>: ... }` block
     is gone — the dispatcher block is the only Use-Item
     handler, with `ryl_die("magic_items: no effect body
     for id N")` if a future authoring bug introduces an
     item without a body.

**Net count.** battle.c lost ~210 LOC from the switch +
~10 LOC of related locals; magic_items.json5 grew by
the equivalent (12 effect: bodies, 8 of which are
substantial). Net wash on byte count; substantial win
on data-vs-code separation.

**Engine surface added in §23.B:**

  - `res_magic_item_effect_node(oc, &slug)` (res.c +
    res.h). Walks `magic_items.<key>` for the entry whose
    `id:` matches `oc`, returns the `effect:` body and
    yields the slug for diagnostic ids.
  - `pick_first_alive_monster` engine_int.
  - `pa_set_inventory` / `pm_full_heal` / `pm_set_dead`
    engine_calls.
  - `battle_flag: 'action_retry'` (read + mutate paths) +
    `script_combat_ctx.action_retry` field.

**Fixture state.** No new fixtures added in §23.B itself —
the existing combat fixtures (combat-attack-win,
combat-mage-*, combat-monster-magic) cover the player- and
monster-side combat paths; magic-item triggering isn't
exercised by any current fixture. Adding one or two
combat-magic-item fixtures is queued as a follow-up but
not strictly required (the dispatcher coverage is
structural — body-shape errors fail at world load via
fold_scripts).

**Cross-cutting wins.** Body-owned inventory consume +
the action_retry flag are reusable shapes for future
script work — any new script body can claim an
inventory slot or signal "abort, re-prompt" without
adding more engine surface. The slug-yielding resolver
pattern (the `&slug_out` parameter on
`res_magic_item_effect_node`) is also a small but
reusable convention worth re-applying when other
data-keyed-by-int subsystems migrate.

## §23 closures (session 2026-04-26 #5) — six items in one pass

This session walked the §23 queue end-to-end. Each item below
landed as its own commit on `lagergren/port1`; the section is
otherwise closed except for §23.J's fixture authoring (knobs
landed; fixture deferred).

**§23.D — driv char-creation thresholds.** Class min-stat
checks (50/50/50 + per-stat fail texts), stat-roll range
(15-70), and the seven starting-state fields (x=12, y=9,
gold=1000, torches=5, food=100, time_left=200,
hours_rested=14) all moved into world.json5. New:
`enums.pc_class.<key>.min_stat: { name, value, fail_text }`
records gate the createparty class prompt;
`res_class_min_stat_check(cl_id, ws, wi, ch, &fail_text)`
dispatches the gate from C. `driv.stat_roll.{min,max}` +
`driv.start.*` provide the rest. Commit `4960a34`.

**§23.C — battle.c combat constants.** Lifted seven literals
into `combat.*` subtrees: `attack.{hit_bonus,
damage_spread_max, confusion_div}` for the player hit-roll;
`battle_cry.ws_penalty` and `defend.{ws_penalty,
rest_threshold}` for the post-action ws penalties;
`run.desperate_chance` for the (R)un branch's
ryl_chance(31). The paired `ws > N` guard + `ws -= N`
penalty stay tied to a single `ws_penalty` field so they
move in lockstep. Commit `e6282bf`.

**§23.F — ydarg stairs item-trade.** The case-50 stairs
G-prompt's digit-1..4 → PS_FIRESHIELD/etc dispatch lifted to
`ydarg.stairs.items[]`, each entry carrying a
`{ digit, special_tag, reject_msg }` triple. New helpers:
`inv_special_id_for_tag(tag)` (public version of the
inventory-internal tag-to-PS_* mapping) and
`res_ydarg_stairs_item(digit, &tag, &reject)`. The four
near-identical case arms collapsed to one path that resolves
the tag to a flag, checks ownership, deducts on success, and
concatenates `"ydarg.room.stairs." + reject_msg` for the
sing/plur rejection text. Commit `1dca812`.

**§23.G — driv race switch.** Closed as a planning artifact:
no race switch exists in the codebase. The §23.G plan entry
described a six-arm switch on `state.p[a].ra` at
driv.c:1540-1575, but inspection (and the original 1980s
source at 75e6b0e) confirmed no such block — race is just
stored on input and looked up via `res_enum("race",
pm_ra(pm))` for display. The plan likely conflated this with
the class switch already closed by §23.D. No commit.

**§23.I — multi-screen narration helper.** Added
`tx_narrate(acts_path, binds)` to primitives — walks a
string-array of text ids and emits each with the standard
clw + tx_fmt(id, binds) + ryl_pause cycle, no leading or
trailing clw. ydarg.confrontation is the first user; the
four narration runs (conf_act1..conf_act4) lift to
`ydarg.narration.*` arrays of existing text ids. The
~60-line tx0/clw/ryl_pause chain in confrontation()
collapses to four tx_narrate calls plus the inter-act side
effects. Byte-for-byte preserved including the original
"no clw before heroes" asymmetry — by dropping the explicit
pre-save clw, tx_narrate's leading clw lands in the same
position the original's pre-save clw did (save() emits no
terminal bytes). Commit `a6430bb`.

**§23.H — ydarg room dispatcher leftovers.** The largest
pieces (case 1 monster encounter, case 3 treasure drop
table, case 50 stairs) were already data-driven by recent
commits. What remained: `ydarg.room.type_max` (the upper
bound of `ryl_random(1, N)` at room init),
`ydarg.pitfall.trap_types[]` (the 3-arm switch on
`ryl_random(0, 2)` for arrows/poison/shurikens), and
`ydarg.pitfall.damage_div` (the `pm_hp / 6` party damage
divisor). What stays in C: the case-1/2/3 frame itself
(genuine UI dispatch), the thief-disarm probe
(`pm_is_thief` + `ryl_chance(pm_wi(pm))`), and the
trap-spring random target (already bounded by
res_party_member_max). Commit `b9d183a`.

**§23.E — bestiary special-encounter survey.** Closed as
documentation. dungeon.c's level-loop key dispatch
(D/A/L/T/P/W/R/Q/Z/V/?), driv.c's title menu key dispatch
(N/C/X/T/S/H/D), and subs.c's wilderness-encounter handler
are all UI dispatchers, not data extraction targets —
confirmed per the §23.E plan note. The wilderness side is
already data-driven via `wilderness.cave_chance`,
`encounter_split`, `encounter_easy_tier/hard_tier`. No
commit.

**§23.J — full ydarg walk-through fixture.** Closed across
three commits — knob bundle in `e480e05`, additional combat-
readiness knobs in `78e12ac`, fixture authored in `757e3dd`.

Knob landscape:

  - `RYL_FORCE_YDARG_GRANTS=1` — applied at end of party
    creation, sets all four quest-item flags + silverkey so
    each floor's stairs G-prompt has a valid donation and
    the gate accepts the party. One-shot consume.
  - `RYL_FORCE_YDARG_EASY_ROOMS=1` — read every time
    `ydarg()` enters (NOT consumed), fills every
    non-stairs slot with case 4 (no-op) so the keystroke
    stream is not at the mercy of room-content RNG.
  - `RYL_FORCE_PARTY_WP=<slug>` — sets every member's wp
    to the slug-named items.<section>.<slug> id (resolved
    via res_item_id). Without this the party deals
    obdamage(0) damage and even crypt_horror is a 60+
    round fight.
  - `RYL_FORCE_PARTY_HP_FULL=1` — heals hp up to h after
    the lv bump. rk_apply_party_lv intentionally leaves hp
    at the lv-1 roll for fixture-snapshot stability; the
    confrontation fixture climbs to lv 28 and would have
    every char one-shot in the first monster round without
    scaled hp.

Fixture: `ydarg-confrontation`. 30th replay fixture. Pins
the full setup path (4× class-2 thieves, warp to (14,30),
'E' to castle, four floors of nav + give-item) plus the
confrontation entry (act_1 narration + crypt_horror fight,
all 4 monsters dead, victory rewards). Bless captured a
loss path during fight #2 (Night Gaunt's Cloud of Fire
AOE one-shots three chars after the party kills the first
of two gaunts) — pinned per the §23.J authoring note
("pin whichever path the bless captures"). Coverage of
conf_act2/3/4 narration + the baron_ydarg fight + the
post-victory heroes-file write is left for a follow-up
that either tunes the spell-cast roll out of the path or
adds a (D)efend-driven survival pattern; both are
mechanical extensions of the existing fixture and don't
require new world.json5 entries.

**Cross-cutting:**

  - The slug-yielding resolver pattern (callee fills both
    a body pointer and a slug string) reused twice this
    session (`res_class_min_stat_check` doesn't return a
    slug but does fill a fail-text string; same shape).
    Cementing this as the convention for any future
    int-keyed → data-keyed dispatcher.
  - `tx_narrate` is the third "screen-iterator" helper
    after `tx_fmt` and `ryl_menu` — they're starting to
    form a natural family of "one helper, one prompt
    pattern" surfaces in primitives.
  - The combat-constants block (`combat.attack.*`,
    `combat.battle_cry.*`, `combat.defend.*`,
    `combat.run.*`) is the pattern other small-literal
    sweeps should follow: group by gameplay-concept,
    not by file or function.

## 24. Logging + remote console (audit 2026-04-26)

Closed end-to-end 2026-04-27 #5: A0 / A1 / B / C / D / E / F all
landed in seven sub-commits across four sessions. Body verbatim
from `port-plan-incomplete.md` for archival.

Two related complaints surfaced together — they share infra so
they get planned as one section:

  1. The "Setting up new campaign… Please Wait" dot loop takes
     ~14 seconds. Confirmed cause: `res.c::res_realize_npcs`
     emits one `ryl_delay(600)` per random NPC (line 1246) and
     one `tx(".")` per NPC (line 1280); with N≈23 random NPCs
     that's ~14s of purely cosmetic delay. The pacing is a
     port-of-port artifact (the original Atari/Amiga build was
     hiding disk loads behind it). Modern build has no disk to
     hide — the delay loop is dead weight.
  2. ~75 `fprintf(stderr, …)` sites + 1 `ryl_log()` (writes to
     `data/rylween.log` via stale-format snprintf) + a few
     scattered `tx()` debug atoms. Three different sinks, no
     severity, no toggle, no remote read. We want a single
     `log_*` API that any module calls, with the sink chosen
     at runtime.

### 24.1 Loading-cosmetics fix (one-liner, lands first)

Either drop `ryl_delay(600)` at `res.c:1246` outright, or gate
it behind the existing `RYL_NO_DELAY` env knob (`io_tty.c`'s
`ryl_delay` already short-circuits on it for replay). Default
to "no delay, ever" — the dots are still visible because each
NPC realization writes one `tx(".")`, just at the speed of the
JSON5 walk rather than wallclock. Confirm `make check` +
`make testasan` stay green (no fixture pins this delay; the
replay harness already sets `RYL_NO_DELAY=1`). One commit, no
follow-up.

### 24.2 Inventory of current emitters

| Source                       | Count | Routes to                  |
|------------------------------|------:|----------------------------|
| `fprintf(stderr, …)`         |    75 | terminal stderr (mixed in with the game UI on the same TTY) |
| `ryl_log(char *)`            |     2 | `~/.rylween/rylween.log` append-only |
| `ryl_die(msg)`               |   ~30 | stderr + abort             |
| stray `tx("debug…")`         |   few | the game's text window     |

Severity is unwritten — every `fprintf(stderr,…)` site is some
mix of warn/error/audit. The replay harness captures stderr
into `.expect` (it does `2>&1`), which is *good* (it freezes
warnings into baselines) but also *bad* (a new warning blows
every baseline by ~one line). Today there's no way to silence
log output without preprocessor work.

### 24.3 Industry-standard mechanisms worth borrowing

The space is well-trodden; the question is which standard fits
a single-process, single-binary roguelike that does not run on
a server farm. Options, in roughly increasing complexity:

  1. **POSIX syslog (RFC 5424).** `openlog("rylween",
     LOG_PID, LOG_USER)` + `syslog(LOG_INFO, fmt, …)`. Built
     into libc. Remote sink falls out for free — `rsyslog` /
     `syslog-ng` / `journald` all forward to network targets.
     Cost: zero new code, zero deps. Limitation: macOS routes
     it to `log show --predicate 'process == "rylween"'` (Mac
     Console app), which is fine but not portable to "telnet
     in and watch".
  2. **Plain file + `tail -f`.** `RYL_LOG_FILE=/tmp/ryl.log`,
     `tail -f` in another window. Same pattern as `ryl_log()`
     today, generalized. Trivial to implement; trivial for the
     user to read. Doesn't solve "remote machine" but covers
     90% of the dev workflow.
  3. **Unix domain socket listener.** `nc -U /tmp/ryl.sock` to
     stream live. ~30 LOC of boilerplate (socket / bind / listen
     / accept / non-blocking write fan-out). Local-only;
     filesystem perms gate access. Telnet-style from one
     machine, no network exposure.
  4. **TCP listener ("rcon" pattern).** `RYL_LOG_LISTEN=:9999`
     binds a TCP port; new line-broadcast to every client.
     `telnet localhost 9999` reads live. Same pattern Quake/
     Half-Life/Source-engine games shipped for ~25 years.
     ~50 LOC. Adds a remote-read story — bind to `127.0.0.1`
     by default, document the loopback discipline so a stray
     `0.0.0.0` doesn't ship a debug surface to the internet.
  5. **GELF / JSON-lines / OTel logs.** Modern structured-log
     formats. Useful if rylween were one component of a fleet,
     but a single-process roguelike doesn't justify the
     dependency surface. Skip.
  6. **named FIFO** (`mkfifo`). Mentioned for completeness;
     fits between #2 and #3. Reader has to be running before
     the writer or writes block. Rarely the right answer.

### 24.4 Recommendation

Land options **2 + 4 layered**, exposed via a single
`RYL_LOG=` env var, with **stderr as the default** so behavior
is unchanged unless the user opts in. Concretely:

```
RYL_LOG=stderr           (default — current behavior)
RYL_LOG=file:/tmp/r.log  (append, no rotation)
RYL_LOG=tcp:127.0.0.1:9999  (listen; broadcast every line)
RYL_LOG=null             (drop everything — useful for replay)
```

Skip syslog as a first cut (#1) — a useful POSIX integration,
but most dev workflows would rather `tail -f` a file. If/when
someone runs rylween on a fleet, syslog is a 10-line addition
behind `RYL_LOG=syslog`.

Format: human-readable single-line, `[YYYY-MM-DD HH:MM:SS]
[LVL] [module] message`. One line per log call. No JSON
unless we hit a structured-ingest need — keep it grep-friendly.

Severity levels: `LOG_DEBUG / LOG_INFO / LOG_WARN / LOG_ERR`
plus a `LOG_AUDIT` for `ryl_log()`'s "campaign started" /
"party died" trail (kept distinct because it's user-visible
history, not engineering noise). Filter via
`RYL_LOG_LEVEL=warn` etc.; default `info`.

### 24.5 Replay-test interaction

Replay tests today rely on `2>&1` redirection. Two ways the new
logger can live with that:

  - Default `RYL_LOG=stderr` keeps every existing baseline
    valid byte-for-byte. The migration commit doesn't change
    any `.expect` file.
  - `tools/replay.sh` learns to set `RYL_LOG=null` per script
    (or per-script via `<name>.env`), which removes the warning
    noise from baselines. Future fixture commits can opt in.
    Don't do this in the migration commit — it's a separate
    bless wave once the `null` sink lands.

### 24.6 API + module layout

New file pair `src/log.{c,h}`. Three categories of caller:

```c
// log.h
typedef enum {
    LOG_DEBUG, LOG_INFO, LOG_WARN, LOG_ERR, LOG_AUDIT,
} log_level;

void log_init(void);     // reads RYL_LOG / RYL_LOG_LEVEL once at startup
void log_msg(log_level lvl, const char *module,
             const char *fmt, ...);
void log_shutdown(void); // close fd, drop listeners

#define LOG_D(mod, ...) log_msg(LOG_DEBUG, mod, __VA_ARGS__)
#define LOG_I(mod, ...) log_msg(LOG_INFO,  mod, __VA_ARGS__)
#define LOG_W(mod, ...) log_msg(LOG_WARN,  mod, __VA_ARGS__)
#define LOG_E(mod, ...) log_msg(LOG_ERR,   mod, __VA_ARGS__)
#define LOG_A(mod, ...) log_msg(LOG_AUDIT, mod, __VA_ARGS__)
```

`module` is a short string ("res" / "party" / "battle") so a
log line stays self-locating. `__FILE__` / `__LINE__` get
folded in for `LOG_E` only — most warns are fine without.

`ryl_die(msg)` stays — it's a panic, not a log; route it
through `LOG_E` on the way to `abort()` so the panic lands in
whatever sink is configured.

### 24.7 Migration sequence

  - **§24.A0** (landed): minimal API + audit migration + setup
    logging + delay reduction. `src/log.{c,h}` with stderr sink
    (WARN/ERR/INFO/DEBUG) + append-to-file sink (AUDIT). Default
    threshold WARN — INFO/DEBUG silent unless RYL_LOG_LEVEL is
    set, so existing replay baselines stay byte-identical.
    `ryl_log()` (2 callers in driv.c + res.c) routed through
    `LOG_A`; `primitives.c::ryl_log` body removed.
    `res_realize_npcs` grew descriptive `LOG_I` lines (one per
    realized NPC + a header + a footer). Per-NPC ryl_delay(600)
    cut to 40ms — campaign setup drops from ~14s to ~1s with
    the visual dot-pacing intact.
  - **§24.A1** (landed 2026-04-27): bulk-migrate the
    `fprintf(stderr, …)` panic + warning sites to `LOG_W` /
    `LOG_E` per severity judgment. 72 callsites total flipped
    across 7 files — res.c 43, party.c 10, driv.c 7, json5.c
    5, snapshot.c 3, inventory.c 3, primitives.c 1. All but
    one (snapshot.c fopen recovery → LOG_W) were panic paths
    followed by `exit(1)` and went to LOG_E. Module names use
    file stems except primitives.c::ryl_die which kept module
    "ryl" to match its public-API "ryl: <msg>" prefix
    contract. ryl.c three remaining stderr sites — the
    `--check-world` "world: ok" success line and the two argv
    usage messages — intentionally stayed as fprintf since
    they're CLI output, not diagnostics. log.c:97 itself is
    the LOG_* emitter, not migrated. No fixture drift — none
    of the migrated paths fire under the 30-fixture replay
    set today.
  - **§24.B** (landed 2026-04-27): file + null sinks. Added
    the `RYL_LOG=` env-var dispatch in `src/log.c`. Recognized
    values: unset / `stderr` (default), `null` (drop
    WARN/ERR/INFO/DEBUG), `file:<path>` (append, resolved
    through `ryl_state_path`). AUDIT path unchanged.
  - **§24.C** (landed 2026-04-27): TCP listener sink **plus**
    multi-sink fan-out. `RYL_LOG=tcp:[host:]port` binds a
    non-blocking listener; every connected client (`telnet
    host port`) gets a live tail of every WARN/ERR/INFO/DEBUG
    line in the `[lvl][mod] body\n` shape the file/stderr
    sinks already use. Default bind is loopback; explicit
    `tcp:0.0.0.0:<port>` exposes on every interface. Up to 8
    simultaneous clients (the `clients[]` slot table); a 9th
    connect while the table is full is `accept`-ed and
    immediately closed. Loss policy: `EAGAIN` drops the line
    for that client only, `EPIPE`/short-write closes the fd
    and frees the slot — the engine never blocks waiting for
    a reader. SIGPIPE is dodged via `MSG_NOSIGNAL` on Linux
    plus `SO_NOSIGPIPE` per accepted socket on macOS/BSD, so
    no process-wide signal override. `atexit(sink_tcp_close)`
    closes the listener fd + every client fd on normal exit.
    Parser: `tcp:host:port`, `tcp::port`, `tcp:port` (last
    two default host to 127.0.0.1).
    During the same session `RYL_LOG` was generalised from a
    single sink to a **comma-separated list** — every
    enabled sink gets every line. Examples:
    `RYL_LOG=stderr,file:dev.log` (tee to terminal + file),
    `RYL_LOG=tcp:9999,file:run.log` (rcon + file). `null` is
    a no-op token (alone in the list ⇒ silent, preserving
    pre-existing `RYL_LOG=null` semantics). `file:` and
    `tcp:` are single-instance; a duplicate emits a one-shot
    diagnostic and is ignored. Open errors degrade only the
    failing token, the rest of the list still applies.
    `make check` + `make testasan` green at the §24.C commit
    — fixture set is unchanged because every fixture pins
    `RYL_LOG=null` post-§24.E, so the new arm only fires
    when a developer opts in. New file: `docs/log.md`
    documents the rcon-style protocol (port, bind discipline,
    line format, loss policy on slow readers, `telnet
    localhost <port>` usage). End-to-end smoke verified by
    running `RYL_LOG=stderr,file:smoke.log,tcp:<port>` under
    a fixture: stderr saw 25 LOG_I lines, the file saw the
    same 25 lines clean, and an `nc 127.0.0.1 <port>` client
    captured the 18 lines emitted *after* it connected
    (lazy-accept, as designed).
  - **§24.D** (landed 2026-04-27): syslog sink behind
    `RYL_LOG=syslog[:<ident>]`. `openlog(ident, LOG_PID,
    LOG_USER)` at first emit; level → priority mapping
    (DEBUG/INFO/WARN/ERR → LOG_DEBUG/INFO/WARNING/ERR);
    body keeps the `[lvl][mod] body` shape so grepping a
    system log behaves the same as grepping a `file:` sink.
    Composes with other sinks via the §24.C list parser.
    Custom ident useful for parallel runs (`syslog:rylween-A`
    + `syslog:rylween-B`). Default ident `rylween`. macOS's
    unified-log UI filters non-Apple subsystems aggressively;
    `tcp:` / `file:` are usually more useful there.
  - **§24.F** (landed 2026-04-27): JSON5 runtime config for
    the always-on `RYL_*` knobs. New `src/runtime_cfg.{c,h}`
    exposes a single `rcfg_get(key)` helper with this lookup
    order: env var (wins if non-empty) → `$HOME/.rylween/
    runtime.json5` → `data/runtime.json5` (optional, not in
    the repo) → callsite default. Schema is a flat JSON5
    object whose keys are `RYL_*` names and whose values are
    scalars (string/int/bool — ints/bools stringified at
    load). Routed through `rcfg_get`: `RYL_LOG`,
    `RYL_LOG_LEVEL`, `RYL_NO_DELAY`, `RYL_FAKE_EPOCH`,
    `RYL_FAKE_SEED`, `RYL_PARTY_MEMBER_MAX`,
    `RYL_REPLAY_RUMOUR`, `RYL_SNAPSHOT`. The one-shot fixture
    knobs in `replay_knobs.c` (`RYL_FORCE_*`,
    `RYL_STARTING_GOLD`) intentionally stay on raw `getenv` /
    `unsetenv` because their per-fixture semantics are
    incompatible with a personal-config default. Replay
    harness runs with `HOME=$(mktemp -d)` so the personal
    path is unreachable; `data/runtime.json5` is absent from
    the repo to avoid leaking project-wide defaults into
    fixtures. Cache is process-lifetime, freed via
    `atexit(rcfg_close)` for LSan cleanliness on Linux. New
    `docs/log.md` "Runtime config file" section documents
    the schema and recognised keys.
  - **§24.E** (landed 2026-04-27): replay-harness default
    `RYL_LOG=null`. `tools/replay.sh` and `tools/replay_asan.sh`
    set `RYL_LOG=null` in the per-fixture subshell before
    sourcing the optional `<name>.env`, so a fixture can opt
    back in to stderr (or file:/tcp:) by setting `RYL_LOG=…`
    in its sidecar — symmetric with how `RYL_FORCE_*` /
    `RYL_FAKE_EPOCH` compose. AUDIT (LOG_A) is unaffected,
    still writing to `data/rylween.log`. No fixture re-bless
    needed — none of the LOG_W/LOG_E paths fire under the
    current fixture set, so baselines were already noise-free.

**Estimated total.** ~3-4 sessions A+B+C, plus D+E+F.
**§24 closed end-to-end** as of 2026-04-27 #5: A0/A1/B/C/D/E/F
all landed, no items remaining in the logging plan.
None of this gated §23 work — independent files, no
conflicts.

**Bar per session.** Same as §22 / §23: `make check` +
`make testasan` green at every commit.

## §25.B step closures (in flight; substeps move here as they land)

§25.B's overall goal — flip the codebase from 1-indexed to
0-indexed across monster, party-member, town, and NPC axes — is
documented in §25 of `port-plan-incomplete.md`. The substeps below
are individually closed and shipped; the parent §25 section stays
in `-incomplete.md` until the remaining axes (town + NPC) finish.

### §25.B step 1 — monster-slot axis (2026-04-27 #10, `684b7e5`)

`for_each_monster` macro flipped to `idx = 0; idx < noc`. Letter
mapping (`64+i` ↔ `'A'+i`, `d - 64` ↔ `d - 'A'`) updated at the
(A)ttack flow in battle.c and `ask_target monster_alive` in
script.c. `for_monster` / `for_each_alive_monster` DSL ops pass
`(0, noc - 1)`. `pick_first_alive_monster` engine_int sentinel
changed from 0 to -1 (slot 0 is now valid alive);
magic_items.json5's `frost_wand` + `drums_of_death` flipped their
`gt: [target, 0]` gate to `gte: [target, 0]`. RNG-stream identity
preserved (same draws, same order, same value space — only the
slot numbering changes). All 39 fixtures byte-identical, no
re-bless needed.

### §25.B step 2 — party-member axis (2026-04-27 #10, `2ff078f`)

`for_each_party_member` macro flipped; per-member SoA arrays drop
the slot-0 reservation byte (sized `N` now, not `N+1`); `pm_cur` /
`pm_of` / `pm_from_digit` ('1' → slot 0) / `pa_*` bounds + range
guards all flip to `[0, N)`; stale `dead[0]` door-counter
doc-comment removed. Player-facing prompts ("Who? (1-N)", "Stats
for char: 1") still display 1-based — the digit→slot conversion is
internal. New `pa_lv_avg()` helper replaces the hardcoded `(pm_lv(1)
+ ... + pm_lv(4)) / 4` in battle.c. battle.c combat round `g` flips
to 0-based (end-of-round test `g == 5` → `g == res_party_member_max()`).
subs.c::scorelist's hardcoded `[1..4]` fanouts plus the `/ 4` average
now use `mmax = res_party_member_max()`. ydarg.c `ryl_random(1, 4)`
→ `ryl_random(0, 3)`. script.c::menu_branch `digit_range` bind
formula now `ch - '0' - lo_v` (0-based offset from `lo`); `expr
member` / `mutate member_field` range checks flip to `[0, max)`;
`do_for_member` / `do_for_each_alive_player` pass `(0, max - 1)`.
`ie_pick_random_alive_player` draws `ryl_random(0, max - 1)` (was
`1, max`). DSL data: `rest.json5` "no guard" sentinel flipped
`0 → -1`; surprise-kill victim `random[0, 3]`. Replay knobs:
`RYL_FORCE_DEAD_SLOT` stays 1-based externally (matches the
player-prompt convention) and converts to 0-based on entry. All
38 non-`title-menu-exit` fixtures' `.expect_snapshot` re-blessed
mechanically — `member[1..4] → member[0..3]`, every other byte
identical. `.expect` (player-facing) byte-identical across all 39
fixtures.

### §25.B input → slot LOG_D traces (2026-04-27 #10, `4e4b6fa`)

Six observability traces fired through the §24 LOG_D sink under
module `input`: `ask_target party_member` / `ask_target
monster_alive`, `menu_branch index_var` (digit_range / letter_range
bind), `subs.c::cast` priest caster pick, `subs.c::weaponoff` /
`armouroff`, `driv.c` / `dungeon.c` / `ydarg.c` (P)layer-stat show.
Off by default; visible with `RYL_LOG_LEVEL=debug`. Not strictly an
axis flip — bundled with steps 1-2 because the same call sites
were touched.

### §25.B step 3a — NPC abstraction + wilderness-sentinel lift (2026-04-27 #11, four commits `0056881..d445b99`)

Decoupled the wilderness sentinel from `state.npc[i].towne == 0`
so the upcoming step 3b town-axis flip can repurpose town slot 0
as a real town. Bundled with the NPC abstraction lift the user
asked for in the same session: un-hardcode the `npc[41]` /
`p[26]` literals, heap-allocate `state.npc`, and migrate raw
`state.npc[i].FIELD` callsites onto `npc_*` helpers.

  - **C1 (`0056881`) — name the static array bounds.** Replaced
    `state.npc[41]` and `state.p[26]` with `NPC_SLOT_HARD_MAX`
    (41) and `PARTY_SLOT_HARD_MAX` (26) declared in `state.h`
    with reasoned doc-comments (runtime-active count comes from
    `res_npc_slot_max()` / `state.num_parties`; static caps are
    upper bounds). Tied call sites: `snapshot.c` party-emit guard
    `state.cpl <= 25` → `< PARTY_SLOT_HARD_MAX`;
    `driv.c::create_party` "full" check `num_parties == 25` →
    `>= PARTY_SLOT_HARD_MAX - 1`. `res.c::npc_slot_max` now
    `LOG_E + exit(1)` if a world declares an `id:` overflowing
    the cap.
  - **C2 (`3e73424`) — heap-allocate `state.npc`.** Switched the
    field type from inline `struct person[NPC_SLOT_HARD_MAX]` to
    `struct person *` allocated in `res_load_world` once
    `res_npc_slot_max()` is callable, right-sized to `(slot_max
    + 1)` entries, idempotent across `res_shutdown` / reload.
    `res_shutdown` now `free()`s and clears the pointer. No
    syntactic changes at indexing call sites — pointer indexing
    matches array indexing.
  - **C3 (`54e3e81`) — `npc_*` helpers + migrate raw accesses.**
    Added `npc_building` / `npc_nr` / `npc_visited` /
    `npc_visited_set` / `npc_is_at` to the `npc_t` handle in
    `state.h`. Migrated five raw `state.npc[i].FIELD` callsites:
    `driv.c::init` wilderness map-mark, `driv.c::terminate`
    visited shift (preserved verbatim — see follow-up bug below),
    `speak.c::speak` building-filter listing + interlocutor-by-
    position fallback, `towne.c::hut` occupant-by-position
    lookup. Remaining direct `state.npc[]` accesses are confined
    to layout owners (`driv.c::{write,read}_current_world`,
    `res.c::res_realize_npcs`) per the state-abstraction memory
    rule "only one place should know the layout of state".
  - **C4 (`d445b99`) — lift `WILDERNESS_TOWN` sentinel.** Added
    `WILDERNESS_TOWN (-1)` constant + `npc_in_wilderness()`
    helper in `state.h`. `res.c::res_realize_npcs` FIXED branch
    maps `enums.towne[0]` (the symbolic "wilderness") to
    `WILDERNESS_TOWN` before storing; RANDOM branch keeps the
    bin draw `ryl_random(0, T-1)` and linear probe **untouched**
    (RNG identity preserved), only post-mapping `bin 0 →
    WILDERNESS_TOWN`. `npc_loc_str` + `driv.c::init` map-mark
    route through `npc_in_wilderness`. **Save format byte-
    identical**: `driv.c::wcw_enum` callsite folds
    `WILDERNESS_TOWN → 0` on write so the file keeps the symbolic
    `towne: "wilderness"` string; `driv.c::rcw_enum` callsite
    folds `0 → WILDERNESS_TOWN` on read. Snapshots are
    NPC-state-free (party + map only); the wilderness-NPC
    map-plant set is unchanged, so `map[]` is byte-identical too.
    Header docs in `res.h` (towns subtree + `for_each_npc` macro)
    updated.

**Bar:** 39 fixtures green at every commit on `make check` +
`make testasan`; no ASan leak markers.

**Follow-up tracked in `port-plan-incomplete.md` mechanical
residuals** (surfaced during the C3 migration but out of scope):

  - `driv.c::terminate` visited-grid shift bug (~line 619): inner
    `for (j = 1; j < num_parties; j++)` body never references
    `j`; just rewrites `npc_visited_set(n, x, npc_visited(n,
    x+1))` `num_parties-1` times. Likely intent was
    `for (j = x; j < num_parties; j++) { npc_visited_set(n, j,
    npc_visited(n, j+1)); }` to compact the visited grid down by
    one slot when party `x` is removed. Probably fixture-
    unreachable today; needs audit + one-line fix + fixture if
    reachable.
  - `driv.c::read_current_world` hardcoded `slot > 39` (line
    1267) should swap to `> res_npc_slot_max()` when §25.B step
    4 (NPC axis flip) lands so the writer's `<=
    res_npc_slot_max()` loop and the reader bound become
    symmetric.

### §25.B step 3b — town-axis flip + heap-alloc + enum-wilderness lift (2026-04-27 #12, four commits `bedd842..f66247a`)

Closes the third of four §25.B axes: the towns array flips from
1-indexed (with a leading null wilderness sentinel) to 0-indexed
end-to-end, the static `ryl_town towns[5]` is heap-allocated off
the world.json5 array length, and `enums.towne` drops its leading
'wilderness' entry — the runtime sentinel `WILDERNESS_TOWN` (-1)
is now bridged by the literal string `"wilderness"` at the four
callsites that span the enum boundary, never an enum entry. After
this commit the towns table and the towne enum line up
entry-for-entry, so adding a town to `data/world.json5` no longer
requires juggling slot-0 reservations or enum-prefix entries.

  - **C1 (`bedd842`) — name the town static cap.** Added
    `TOWN_SLOT_HARD_MAX` (5) in `state.h` next to
    `NPC_SLOT_HARD_MAX`; declared `towns[]` off the new name and
    swapped the `sizeof towns / sizeof towns[0]` fold-time check
    for it. Lifted the bare `4` upper bounds in `res_town_name`
    / `_x` / `_y` / `_tax` / `_has_church` to `(int)n_towns` so
    the per-callsite guards became data-driven (substitution
    identity-only — `n_towns` folded to 4 against today's data).
  - **C2 (`78bcac3`) — heap-allocate towns.** Replaced the
    static `ryl_town towns[TOWN_SLOT_HARD_MAX]` array with a
    `ryl_town *towns` pointer + `towns_cap` size shadow, sized
    by `fold_towns` to the world.json5 array length and freed
    in `reset_towns`. Mirrors the `state.npc` heap-alloc shape
    from §25.B step 3a/C2. The fold-time overflow check is the
    only place the static cap is consulted now; bumping the cap
    is a one-line change. The `int counts[]` placement-bin
    tally in `res_realize_npcs` swapped its `sizeof towns` size
    hint for a literal `TOWN_SLOT_HARD_MAX` bound (the active
    part is bins 0..T-1; cap is the upper bound on T).
  - **C3 (`ac5943e`) — flip the town axis to 0-indexed.**
    `for_each_town` macro to `slot = 0; slot < res_town_count()`.
    `fold_towns` drops the leading-null skip and writes
    `towns[0..n-1]`; `n_towns` becomes the array length
    directly. Bounds in `res_town_*` shift from `twn < 1 ||
    twn > n_towns` to `twn < 0 || twn >= n_towns`;
    `res_town_at` returns `WILDERNESS_TOWN` on no-match.
    `towne.c::enter` sets `state.twn = WILDERNESS_TOWN` at top
    and tests against the sentinel in place of `!state.twn`;
    the `state.twn == 3` city/village fork shifted to
    `state.twn == 2` (Wilor moved from slot 3 to slot 2).
    `speak.c::speak`'s `if (tow)` wilderness-vs-town selector
    became `if (tow != WILDERNESS_TOWN)` so the now-valid
    `towns[0]` doesn't fall through to the at-location path
    (the load-bearing fix that turned up under fixture replay).
    `driv.c::wcw_enum` / `rcw_enum` save-format bridge kept the
    symbolic `'wilderness'` string on disk via a temporary
    `+1` / `-1` offset against the still-1-indexed
    `enums.towne` — that intermediate offset is what C4 retired.
    `res.c::res_realize_npcs` FIXED + RANDOM apply the same
    shift; `res_npc_in_town` runs the town string through
    `res_enum_id` and shifts the result down by 1 before
    compare. RNG identity preserved across both placement
    paths — bin draws, count, and linear probe are unchanged;
    only the bin → slot result mapping shifts. 17 fixtures'
    `.expect_snapshot` diffed on the top-line `twn=N` only
    (Thord 1→0, wilderness 0→-1); re-blessed in this commit.
  - **C4 (`f66247a`) — lift `enums.towne` wilderness sentinel.**
    Dropped `'wilderness'` from `enums.towne[0]`. The four
    enum-spanning callsites (`driv.c::wcw_enum` /
    `driv.c::rcw_enum` towne field, `res.c::res_realize_npcs`
    FIXED, `res.c::res_npc_in_town`) now match the literal
    string `"wilderness"` against the runtime
    `WILDERNESS_TOWN` sentinel before falling through to the
    enum lookup. Real towns now share the
    `enums.towne` ↔ `towns[]` index space directly with no
    offset. Save-format wire shape unchanged: a wilderness NPC
    still serializes as `towne: "wilderness"`, a Thord NPC as
    `towne: "Thord"`, so existing fixture `.expect_snapshot`
    files are byte-identical. The visible payoff is that the
    enum table no longer has implicit knowledge of the
    sentinel — adding a 5th town is a pure
    `data/world.json5` edit (append to `towns:` and
    `enums.towne`); no slot juggling required.

**Bar:** 39 fixtures green at every commit on `make check` +
`make testasan`; byte-identical save format and snapshot on C4.

**End-goal alignment** (per the user's "add a town/NPC purely
via JSON5 → it just works" directive): the towns axis is now
fully data-driven. Adding a 5th/6th town requires only the
`world.json5` edit; the heap-alloc absorbs the cap, the bridges
read the enum-without-offset, and the runtime sentinel is named
inline at the boundary. The remaining static caps —
`state.p[26]`, `map[42][82]`, party-inventory `[17]`, and
`attack_today[26]` — stay queued under §12.3.

### §25.B step 4 — NPC axis flip (2026-04-27 #13, `404b1e8`)

Closes the fourth and final §25.B axis: `state.npc[]` flips from
`1..res_npc_slot_max()` to `0..res_npc_slot_max()-1` end-to-end.
Slot 0 is the first realized NPC (Garth) — the dormant zero-init
sentinel that shadowed the towne == 0 wilderness conflation in
the legacy code is gone, and wilderness NPCs are still identified
by `towne == WILDERNESS_TOWN` (lifted in step 3a/C4).

`res_npc_slot_max()` switches semantics from "largest declared
`id:`" to "count of NPC records"; `npc_slot_max()` validates that
ids are 0..count-1 contiguous (a typo or a gap fails loudly at
world load) and that count <= NPC_SLOT_HARD_MAX. The realizer's
slot validation (`slot < 1 || slot > slot_max` →
`slot < 0 || slot >= slot_count`), the wilderness collision loop
(`for x = 1; x < slot+1` → `for x = 0; x < slot+1`), the heap-
alloc sizing (drops the `+1`), and the speak.c / towne.c "no
NPC at this square" sentinel (`talk = 0` / `who = 0` →
`-1` with a `< 0` test, since slot 0 now collides with a valid
NPC) all flip together. The save format's `id: <int>` decrements
by 1 and the visited_npcs[] array shortens by one entry; the
mechanical-residual `slot > 39` literal in `read_current_world`
gets lifted to `slot >= res_npc_slot_max()` along the way.

RNG identity preserved across both placement paths: the bin draw
range, count, and linear probe are unchanged, and the wilderness
collision loop walks the same set of state.npc entries (just
shifted by one slot index). All 39 fixtures byte-identical on
`make check` + `make testasan` with no `.expect_snapshot` rebless
required (the snapshot doesn't dump NPC state).

`data/world.json5` decremented `npcs.<key>.id` 1..39 → 0..38.
Doc comments throughout — `state.h` `NPC_SLOT_HARD_MAX` framing,
`res.h` `for_each_npc` macro and `res_npc_slot_max` semantics,
`state.h` `state.npc` allocation, `driv.c::read_current_world`
schema note — refreshed to drop the "slot 0 wilderness sentinel"
framing.

**Bar:** 39 fixtures green on `make check` + `make testasan`;
byte-identical fixture output across the entire suite (no rebless
needed).

**§25.B closed end-to-end** with this commit. The four axis flips
are: monster-slot (step 1), party-member (step 2), town (step 3b),
NPC (step 4). All four iterate from 0 with `for_each_*` macros
that the data layer drives, and the underlying state arrays are
heap-allocated off the world.json5 record counts (with `state.p`
/ `map` / inventory / `attack_today` as the remaining static caps
queued under §12.3). The 1-indexed → 0-indexed migration that §25
described is done.

## §17.A.4 slice closures (in flight; slices move here as they land)

§17.A.4's overall goal — Phase-6-class text-extraction across
`missions.c`, `interact.c`, `dungeon.c`, `subs.c` (slices A-D)
— is documented in §17 of `port-plan-incomplete.md`. Slices ship
individually; the parent §17 section stays in `-incomplete.md`
until all of A-D close.

### §17.A.4 slice A — missions.c (2026-04-27 #14)

The slice landed smaller than the §17.3 plan implied. Of the 16
historical mission-control trigger blocks, 15 (Blocks 1, 2, 3, 4,
4b, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16) had already migrated
during the §16.5 / §22 mission-state-machine work — they live as
declarative `mission_trigger` rows whose `script` field hands off
to `play_script`, with text shards in
`data/world/texts/missions.json5` and op bodies in
`data/world/scripts/missions.json5`. Only **Block 5** (the temple
of Thun gem altar at `flag[bracelets.thun]==map_bought` /
`wld_location 2`) remained inline in `missions.c::mission_control`
because its `inv_count_in_range(39, 41) >= 5` guard isn't
expressible in the current DSL.

Slice A lifts Block 5's five inline text screens
(`temple_arrival`, `altar_inscription`, `gem_prompt`, `no_gems`,
`altar_opens`) into `data/world/texts/missions.json5` under a new
`thun_temple` entry and replaces the consecutive `tx("...")` runs
with `tx0(res_text("missions.thun_temple.<screen>"))`. Block 5's
control flow stays in C — the `(Y)es`/`(L)eave` keypress, the
`inv_count_in_range` check, the `dungeon(18, "thun_temple")`
handoff, and the `clr_map_window`/`update_map` cleanup are
unchanged. The bundled `gem_prompt` record absorbed the previous
`tx0(res_text("common.yes"))` call along with the `(L)eave` line,
matching the menu-bundling convention used by the other prompt
records in the file (Block 1's `erethor_entry`, Block 4b's
`hermit_dungeon`, etc.).

**Audit.** Block 5 is fixture-unreachable: the only `Thun` /
`bracelets` references in any of the 39 fixtures are the
unrelated tavern rumour text in `towne-thord-tavern-rumour-r27`
(`Thun! He lives at 27x14`) and the special-item inventory line
in `ydarg-confrontation` (`The Bracelets of Thun` as item 4).
No fixture exercises the gem-altar block, so the migration is
bytes-only safe and no `.expect_snapshot` rebless was required.

**Bar:** 39 fixtures green on `make check` + `make testasan`;
byte-identical fixture output across the entire suite.

Slice B (interact.c) is the next active item — same
mechanical-Option-A shape, smaller footprint per the §17.3 table.
