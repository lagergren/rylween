# Rylween Port Plan

A plan for getting `Adventurers of Rylween` (1992-93, ~12k LOC of Amiga/SAS-C for
the Xenolink BBS) running as a self-bootstrapping terminal app on a modern
Unix-ish box (macOS, Linux).

## 1. What the code actually is

- **Language:** K&R-ish C, single-threaded, globals-heavy, heavy `tx()`/`c()`
  printf-wrapper style.
- **Target:** Amiga SAS/C, linked against the Xenolink door library.
- **UI:** Already **ANSI escape codes** (`\x1b[...m`, cursor positioning via
  `c(x,y)`). It does not paint a character grid — it just streams ANSI to a
  modem. That is fundamentally compatible with every modern terminal.
- **Two builds, nearly identical:**
  - `ryl.c` — Amiga Xenolink door version.
  - `pcryl.c` — PC/CLI variant (same source, slightly different formatting —
    likely produced by a broken line-ending round-trip; see §7).
- **Two main entry points already coexist in every module:**
  `if (cli) { printf/gets/getch } else { XCB->DisplayText/GetLine/GetCharacter }`.
  Roughly 80% of the Xenolink dependency is already gated behind `!cli`.
  This is the key finding — a CLI port is partially done.

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

## 6. Language: modernize to C99 vs C++17 subset — decision deferred

Before starting Phase 0, pick a target language. Both paths are viable;
they diverge in cost and ergonomics, not capability. The decision can wait
until you've diffed the pcryl/ryl duplicates and scoped the work, but it
shouldn't wait past Phase 1 — mid-project migration is painful. The
existing code compiles as either.

### Shared baseline (required on either path)

These are bug fixes, not style choices. Do them during Phase 2 regardless
of which language you pick.

- `getstr()` at `src/ryl.c:123` returns a pointer to a stack-local
  `char svar[90]`. Undefined behaviour — callers happen to copy before
  the frame goes away. Fix via static buffer, out-param, or (C++)
  return `std::string`.
- `sprintf` / `strcpy` / `strcat` — ~30 call sites with no bounds check.
  Replace with `snprintf` (C) or `std::string` / `fmt::format` (C++17).
  Audit every fixed-size buffer while you're there — `char pos[16]`,
  `char svar[90]`, `char str[144]` are sized by guess.
- `gets()` — removed in C11, Phase 2 already swaps it.
- Compile with warnings-as-errors on format strings:
  - C: `-std=c99 -Wall -Wextra -Werror=format-security -Wno-parentheses`.
  - C++: `-std=c++17 -Wall -Wextra -Werror=format-security
    -Wno-parentheses -Wno-write-strings`.

Roughly one day of work regardless of path.

### Path A — C99 / C11

Keep everything as C. After the baseline pass, you have a safer BBS door
with the same idioms.

**Pros**
- Zero toolchain change. `cc` on any Unix.
- No risk of C-as-C++ subtleties. Smaller diff.
- POD save structs trivially portable.
- Smaller binary, shorter compile times.

**Cons**
- Resource system (§9) needs a hashmap. Either roll your own, pull in
  uthash / klib (single-header), or scan a flat JSONL array linearly
  (fine at this scale — ~500 records).
- Dialogue interpreter (§9) wants tagged unions. C unions work, just
  more verbose.
- String handling stays manual. Every format call is a size audit.
- JSON parser: cJSON (single file, drop in).

**Cost on top of baseline**: 0 extra. The baseline is the modernization.

### Path B — C++17 subset

Compile with `g++ -std=c++17`. Use a narrow subset — "better C", not
idiomatic C++.

**Allowed**
- `std::string`, `std::string_view`.
- `std::vector`, `std::array`.
- `std::unordered_map`, `std::map`.
- `std::variant`, `std::optional`.
- RAII for file handles, termios state, allocations.
- References and `const` correctness where they clearly help.
- `constexpr` for compile-time tables.
- `nlohmann/json` (single header) for the resource loader.

**Forbidden (this is still a port, not a rewrite)**
- Exceptions. Use return codes or `std::optional`.
- Templates beyond using STL.
- `<iostream>`. Keep `stdio.h` — matches the existing `tx()`/`printf`
  style, and streams are slow and verbose.
- Boost, ranges, concepts, modules, coroutines.
- Inheritance / virtual / polymorphism. The `io_backend` struct-of-
  function-pointers in §4 stays exactly as it is.
- Operator overloading beyond what STL expects.

**Pros**
- `std::string` removes ~90% of the buffer-audit work in the baseline.
- `std::unordered_map<std::string, Record>` is the resource index.
- `std::variant` + `std::optional` make the dialogue AST ergonomic
  without a hand-rolled tagged-union type system.
- RAII restores termios / closes files on every error path
  automatically — big win for TTY state hygiene.
- Existing code compiles as C++ almost unchanged.

**Cons**
- `g++` instead of `gcc`. Minor.
- Compile-time hunt for C-as-C++ issues:
  - Implicit `void*` → typed casts. `malloc` return needs `(char*)`.
  - `char *` vs `const char *` — string literals become `const char*`
    in C++; the code frequently passes them as `char *`. A
    `-Wno-write-strings` suppresses this, or you do the cleanup pass.
  - Enum vs int implicit conversions.
  - `new`, `class`, `template`, `this`, `friend` as identifiers. A
    quick scan showed none in `ryl.h` / `driv.c`, but check.
  - Functions missing a `return` on non-void paths (warning in C,
    error in C++). I spotted one: `object()` at `src/towne.c:307`
    falls off the end past `case 111`.
- POD save structs stay POD. Do **not** put `std::string` inside
  `struct party` — keep `char name[5][30]`. The save format
  (`fwrite(&p[i], sizeof p[i], …)`) is a hard compatibility
  constraint, and you probably want to keep existing `REALM.DAT`
  files loadable.
- Slightly larger binary, slightly longer compile.

**Cost on top of baseline**: 1 extra day for the initial C-as-C++
compile pass. Thereafter cheaper than C for every new file you write.

### Decision matrix

| Endpoint | Pick |
|---|---|
| "Modernized BBS door, Phases 0-5 only." | C99 |
| "Data-driven engine + resources (§8, §9)." | C++17 subset |
| Uncertain / want to decide later. | C99 now, migrate at Phase 6 if needed. The baseline work is identical either way. |

Migrating C→C++ after Phase 5 costs ~2 days (mostly the same C-as-C++
compile pass, plus adopting `std::string` in new code going forward).
It's not free, but it's not a trap either. The main risk is writing
"too-C++" code and regretting the subset boundaries later — hence the
explicit Forbidden list above.

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

## 8. Things that will bite you

- `getstr()` stack-pointer return (`ryl.c:123`), unchecked `sprintf`,
  unchecked `strcpy`/`strcat`, and guess-sized fixed buffers are covered
  by §6's shared baseline (Phase 2 step 13). Don't skip that pass — the
  bugs are real, they just haven't fired because callers happen to copy
  before the stack frame dies.
- Amiga-only helper sources (`reveal.c`, `npclist.c`, `optdata.c`, `scan.c`,
  `rylreg.c`) were deleted in Phase 5 step 25 — each had its own `main()` and
  was never linked into the game.
- `speak.c` (1166 lines) and `strid.c` (933 lines) have the most `Delay()` and
  `pause()` pacing — the places where wrong `Delay` granularity will be most
  visible.
- Character encoding: comments in `subs.c` use Swedish (`"slumpvis"` =
  "randomly", `"spelare"` = "players"). Source is ISO-8859-1 / Amiga. Add
  `-finput-charset=latin1` or convert files to UTF-8 in one pass.
- `char` signedness: a few spots use `char` values as array indices. On ARM
  macOS (`-fsigned-char` vs `-funsigned-char`), audit with `-Wchar-subscripts`.

## 9. End-state vision: a data-driven engine

The bigger architectural goal this port is pointing at: turn the source
tree into **a small generic engine plus a content archive**, not a
monolithic game in C. "Rylween" becomes one set of resource files that
runs on top of an engine; the engine could in principle drive another
text-RPG given different content.

That's an ambitious target. The good news is it's the natural end state of
the abstractions you're already stacking up:

1. §4 — Abstract the I/O boundary → engine is independent of transport.
2. §5 — Extract ANSI tile art → art is data.
3. §7 Phase 5b — Lift globals into a `ryl_state` struct → engine is
   re-entrant and testable.
4. §9 — Extract text, items, spells, NPCs, towns, missions → content is
   data.

What's left in code after all three is the **engine**: turn loop, map
navigation, combat math, pathfinding, save/load, resource loader, renderer
primitives. Maybe 2-3 kLOC, compared to the current ~12 kLOC.

### Inventory — what's currently code but should be data

Five categories, ordered roughly by extraction difficulty.

**1. Static string arrays** — trivial:

- `char *jobname[]` — 16 entries, `src/ryl.c:23`.
- `char *name[]` — 40 entries, `src/ryl.c:43`.

**2. Functions that are really tables** — mechanical:

- `object(int nr)` — 47 items across 3 ID ranges, `src/towne.c:307`.
- `obcost(int nr)` / `obdamage(int nr)` — item cost/damage lookups.
- `sextext(nr, xx)` — pronoun lookup, `src/driv.c:722`.
- `kar(char t)` — character-class display name, `src/subs.c:254`.
- `cf(int)` — another lookup.
- Spell name/cost/description tables scattered in `subs.c`.
- Monster encounter definitions in `strid.c` (68 case-statements).

**3. NPC, town, and world-location data** — structured, worth pulling out
as first-class records:

- **Fixed NPCs** — `place_npcs()` in `src/person.c:75-164` hardcodes NPC
  slots 25-35 (innkeepers × 4 towns, tavernkeepers × 4, bargirls, the
  hermit at `(48,7)`, etc.). Each is a `{job, town, building, x, y}`
  record. Should be 11 JSONL `{"kind":"npc_fixed", ...}` records.
- **Random-NPC generation rules** — `person.c:43-71` places NPCs 1-24 by
  randomizing job=(i mod 12) + town/building/coords. The rules (valid
  coord range, collision avoidance with hermit at col 48, forbidden
  terrain chars `m`/`w`/`0`/`1`/`2`) should live in a `{"kind":
  "npc_spawn_rules", ...}` record.
- **Towns** — 4 towns, each with a fixed set of buildings (inn, tavern,
  smith, church, merchant, guild, houses). Currently embedded in
  `towne.c`'s navigation code. Should be `{"kind":"town","id":1,
  "name":"...","buildings":[{"type":"inn","keeper_npc":25}, ...]}` records.
- **World landmarks** — `position_locations()` in `driv.c:245` seeds
  `dx[1..9]`, `dy[1..9]` at campaign start as dungeon/cave locations.
  The *count* and *placement rules* are data; the *specific coords* per
  campaign stay in the save file. So: `{"kind":"landmarks","count":9,
  "place":{"x":[5,75],"y":[5,35],"forbid_terrain":"wm012"}}`.
- **Map** — `data/Map.DAT` is already a file. Either keep it or fold it
  in as a `{"kind":"map","rows":[...]}` record for uniformity.
- **Terrain glyphs → meaning** — single-char map codes (`m`=mountain,
  `w`=water, `0`/`1`/`2`=town types, etc.) are hardcoded in checks
  scattered across `driv.c` and `subs.c`. Should be a
  `{"kind":"terrain","glyph":"m","name":"mountain","passable":false,
  "enter":null}` record set.
- **Dungeon/cave structure** — `dungeon(int,int)` in `dungeon.c` has the
  monster/treasure generation rules. Parameter: dungeon depth tier, loot
  tables. Extractable.

**4. Missions / quest flags** — `missions.c` (14 kB) and flag refs
(`p[cpl].flag[40]`) scattered everywhere. Each flag has implicit semantic
meaning (flag 1 = Garth's mission accepted, flag 2 = gold found, etc.).
Missions are state machines defined implicitly by who sets/reads which
flag. Moving to data means:

- Naming every flag: `{"kind":"flag","id":1,"name":"garth.quest.accepted"}`.
- Defining each mission as a sequence of states with triggers:
  `{"kind":"mission","id":"garth.refund","stages":[{"accept":{"npc":1,
  "set":1}},{"complete":{"encounter_drops":"gold","set":2}},{"reward":{
  "npc":1,"need":2,"give_gold":500,"set":3}}]}`.

This is the **hardest** category and the one that most resembles
"writing a small DSL". Leave for last.

**5. Dialogue trees** — `speak.c` (37 kB, 1166 lines). The biggest single
piece. Each NPC's dialogue branches on mission flags and gives
conditional responses. Naturally expressible as:

```jsonl
{"kind":"dialogue","npc":"garth","if":{"flag":"garth.quest.accepted","eq":0},"lines":["Hail traveler.","I was robbed of 500 gold..."],"offer":{"mission":"garth.refund"}}
{"kind":"dialogue","npc":"garth","if":{"flag":"garth.quest.accepted","eq":1,"and":{"flag":"garth.refund.complete","eq":0}},"lines":["Any luck finding my gold?"]}
{"kind":"dialogue","npc":"garth","if":{"flag":"garth.refund.complete","eq":1},"lines":["You have my thanks.","Here's your reward."],"give_gold":200,"set_flag":"garth.refund.paid"}
```

A simple condition language (`if`, `and`, `or`, `flag_eq`, `has_item`) plus
a simple effect language (`set_flag`, `give_gold`, `give_item`, `goto`,
`encounter`) covers essentially all of `speak.c`. The engine gets a ~200-
line interpreter; `speak.c` goes from 1166 lines to ~0.

**Spell effects share this DSL.** The 7 priest spells (`subs.c spell()`,
lines 799-1156) and 6 mage spells (`strid.c` combat block,
lines 406-599) are effect machines with the same shape as dialogue
effects: target selection + context gate + mutate game state + say
flavor text. Design the DSL once so both dialogue and spells run on the
same interpreter:

```jsonl
{"kind":"spell","id":"priest.heal","class":"priest","slot":1,"name":"Healing","base_cost":8,"effect":{
  "ask_target":"party_alive",
  "mutate":[{"set":"target.hp","val":{"+":["target.hp","10","(rand 0 9)","(* caster.lv 2)"],"clamp_to":"target.h"}}],
  "say":"spell.priest.heal.success"
}}
{"kind":"spell","id":"priest.banish","class":"priest","slot":4,"name":"Banish Evil","base_cost":85,"effect":{
  "require":[{"context":"dungeon"}],
  "mutate":[{"set":"state.banished","val":1}],
  "say":"spell.priest.banish.narration"
}}
{"kind":"spell","id":"mage.frost","class":"mage","slot":1,"name":"Frost Finger","base_cost":8,"effect":{
  "require":[{"not":{"target":"immune"}}],
  "ask_target":"monster_alive",
  "mutate":[{"set":"target.hp","val":{"-":["target.hp",{"*":["caster.lv",{"+":["(rand 0 6)","2"]}]}]}}],
  "say":"spell.mage.frost.success"
}}
```

The primitives needed for spells (`target.hp`, `caster.lv`, `state.banished`,
`state.tid`, `rand`, `clamp_to`, `context`) overlap ~90% with what dialogue
needs (`flag_eq`, `has_item`, `party.gold`, `set_flag`, `give_gold`).
Designing them in one coherent grammar avoids two half-compatible DSLs.

**Phase sequence for spells:**
- **Phase 6b (now):** extract spell metadata (name, slot, class, base_cost)
  as data. Use it only for the menu and cost formula. Keep effect bodies
  as static C functions (`do_heal`, `do_frost`, …) — preserves the exact
  call order and flavor text each spell depends on. Sets up the record
  shape the DSL will consume later. ~80-100 line reduction.
- **Phase 6d (when dialogue DSL lands):** replace each `do_*` body with
  `eval_effect(spell->effect_tree)` one at a time. Effect trees are
  authored in the resource file alongside the metadata. Effect text
  moves to `{"kind":"text",...}` records at that point.

This ordering keeps every phase byte-identity-verifiable with the smoke
harness: 6b changes data layout without touching behavior; 6d migrates
behavior one spell at a time with the DSL interpreter validating against
the preserved reference output.

**6. String literals in remaining `tx()` calls** — large volume, low risk
once patterns are established: menu prompts, status messages, title
credits, nag screens, combat flavor, help text, spell chants.

### JSONL schema

One JSON object per line, discriminated by `kind`. The C side has a tiny
loader that indexes records by `(kind, id)` into a hashmap on startup.

```jsonl
{"kind":"enum","id":"jobname","values":["","Merchant","Ranger","Fighter","Wizard","Priest","Thief","Peasant","Bard","Drifter","Half-Orc","Half-Elf","Smith","Bargirl","Innkeeper","Tavernkeeper","Hermit"]}
{"kind":"enum","id":"npcname","values":["","Garth","Nrim'to","Areth","Nevaak","Gorm","Bernak","Matias"]}
{"kind":"item","id":1,"cat":"weapon","name":"Sword","cost":50,"damage":4}
{"kind":"item","id":15,"cat":"weapon","name":"Magic Dagger","cost":500,"damage":6}
{"kind":"item","id":31,"cat":"misc","name":"Torch","cost":10}
{"kind":"item","id":100,"cat":"combat","name":"Frost Wand","cost":0,"damage":20,"uses":1}
{"kind":"text","id":"title.prog_by","text":"Programming & Design by:"}
{"kind":"text","id":"nag.gnolls.intro","text":"Suddenly some gnolls\nappear out of nowhere.\nThe leader speaks:\n- Tell ze sysop to\n  register, or we'll tear\n  yar guts out!!!\n"}
{"kind":"dialogue","id":"merchant.greeting","speaker":"merchant","lines":["Welcome, traveler.","What can I get you?"]}
{"kind":"spell","id":3,"name":"Banish","mp_cost_base":85,"mp_cost_per_lv":1,"target":"dungeon"}
{"kind":"encounter","id":1,"name":"Gnoll Pack","min_hp":8,"max_hp":14,"dmg":[1,4]}
```

Keep **format specifiers** in the text strings where they exist (`%s`,
`%d`) — the C side still calls `printf`/`tx` with the retrieved string and
the runtime args. The schema doesn't need to know about them; it just
preserves them as literal characters.

Keep **inline SGR color codes** (`\x1b[33m`) in the strings where they
exist — they're part of the content, not formatting to be re-introduced.

### Loader side

Two options:

- **cJSON** (single `.c` + `.h`, public domain, ~2kLOC). Drop in, call
  `cJSON_Parse` per line, done. Zero dependencies beyond libc.
- **Tiny custom parser** — JSONL's per-line structure plus the flat shape
  of these records means a ~150-line parser handles everything you need
  (no nested objects except arrays of strings/ints). Slightly more work,
  zero deps, compiles faster.

Default to cJSON. It's not worth reinventing for this.

**Format choice: JSONL vs JSON5.** Phase 6a shipped on JSONL with the
tiny custom parser (which also silently accepts `#` and `//` line
comments, so section annotations work without JSON5). Reconsidered at
Phase 6b entry: JSON5 (single document, trailing commas, unquoted keys,
inline comments, multi-line strings) is nicer for hand-editing nested
structures, but buying it costs either a dependency or a 500–1000-line
parser extension, and most of the ergonomic win (comments) is already
there. Append-only growth also fits JSONL — phases 6c–f will add
hundreds of records, and one-record-per-line keeps PR diffs clean.
**Decision: stay on JSONL through 6c; revisit at 6d** when nested
dialogue records would actually benefit from multi-line strings and
structural grouping. Switching then is cheap because the loader is ours.

Loader API:

```c
void  res_load(const char *path);          /* resources.jsonl, called at init */
const char *res_text(const char *id);      /* "title.prog_by" -> string        */
const char *res_enum(const char *id, int); /* "jobname", 3 -> "Fighter"        */
const item_t *res_item(int id);            /* items are hot, use a direct array */
```

At call sites:

```c
/* Before */
tx("    Marcus Lagergren\r\n\r\n");

/* After */
tx("%s\r\n\r\n", res_text("title.prog_name"));

/* Before */
switch(nr) { case 1: return "Sword"; case 2: return "Axe"; ... }

/* After */
char* object(int nr) { const item_t *it = res_item(nr); return it ? it->name : "-"; }
```

### What NOT to extract

- **Computed status strings** like `sprintf(stline, "TIME: ...%2d ...")` in
  `driv.c:219`. They're format templates, not content. You'd just end up
  with a string table that's 1:1 with the format call sites.
- **Save-file struct layouts** — those stay binary (`REALM.DAT`,
  `OptData`'s writeout format) because save compatibility matters and
  they're consumed by the game code, not humans.
- **Control-flow "text"** — any string that branches on content (e.g.
  parsed with `sscanf`). Keep inline; externalizing would decouple data
  from the parser silently.
- **The `.ans` scene files** — already data, different format, don't
  collapse into JSONL.

### Eternal vs extractable data files

Your observation is right: `REALM.DAT` and the full-screen `.ans` files are
the only "eternal" runtime state. After extraction:

| File | Status | Format |
|---|---|---|
| `data/resources.jsonl` | **new** | JSONL, all static content. |
| `data/ansi/*.ans` | keep | Raw CP437/UTF-8 ANSI, full-screen scenes. |
| `data/tiles/NN.ans` | **new** (from §5) | One-row-per-line ANSI tiles. |
| `data/Map.DAT` | keep (for now) | ASCII grid; could fold into resources.jsonl as a `{"kind":"map", "rows":[...]}` record if you want total uniformity, but it's working as-is. |
| `data/NPC.DAT` | generated at runtime | Binary blob from `place_npcs()`. Static *templates* for NPCs move to resources.jsonl; runtime placement stays binary. |
| `~/.rylween/REALM.DAT` | save data | Binary struct array, per-party. Stays binary. |
| `distro/OptData` | utility | The save-file cleanup tool; moves or dies in Phase 0. |

### Suggested sub-phases

Do this as a separate pass *after* Phase 5, not mixed into the port. Each
sub-phase is independently committable and testable against the baseline.

- **Phase 6a** — static arrays (`jobname`, `name`). ~2 hours.
- **Phase 6b** — table-functions: `object`, `obcost`, `obdamage`, `kar`,
  `sextext`, spell tables, encounter tables. ~1 day.
- **Phase 6c** — `driv.c` and `subs.c` text. ~1 day.
- **Phase 6d** — `speak.c` dialogue. ~2 days. Biggest single chunk.
- **Phase 6e** — `strid.c` combat text. ~1 day.
- **Phase 6f** — everything else. ~0.5 day.

Total: ~5-7 days of focused work. Much of it mechanical (copy string,
write record, replace with lookup call). An LLM-assisted pass can do the
bulk edit if you want, with humans reviewing diffs.

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

## 12. Follow-ups queued for after Phase 6d module splits

The `json5.[ch]` / `state.h` / per-module-header series (commits
`baf768f`, `c2bf863`, `168ff9c`) landed three structural cuts. Open
work after that:

### 12.1 Dockerfile + GitHub CI

**Goal.** Contributors without a native toolchain (or with a
non-macOS platform) should be able to build, run `tools/replay.sh
--all`, and run `tools/check_world.sh` in a single `docker build`
invocation.

**Dockerfile shape** (multi-stage):

- Stage 1 (`builder`): `debian:stable-slim` + `build-essential`
  + `make`. `COPY .` then `make clean && make`.
- Stage 2 (`tester`): same base, copy the rylween binary +
  `tools/` + `data/`, run `tools/replay.sh --all` and
  `tools/check_world.sh` as `RUN` steps so image build fails on
  regression.
- Stage 3 (`runtime`): slim image with binary + data for
  `docker run -it rylween`.

**CI shape** (`.github/workflows/ci.yml`):

- Trigger: push + PR.
- Matrix: `ubuntu-latest` (gcc + clang), optionally
  `macos-latest`. `ubuntu-24.04` gives clang-format 17 to
  verify `.clang-format` cleanliness.
- Steps: `make`, `tools/replay.sh --all`,
  `tools/check_world.sh`, `clang-format --dry-run --Werror src/*.{c,h}`.

### 12.2 Global-state teardown + allocation audit

Lookup tables (`glyph_tab`, `color_tab`, `color_tab_set`, `enums`,
`texts`, `flags_tab`, `pronoun_tab`, `priest_spells`,
`mage_spells`, `items`, `monsters`) and `world_tree` currently
live as file-scope globals in `res.c`. `struct ryl_state state`
is similarly a file-scope global in `ryl.c`. On process exit
nothing is freed — we rely on the OS to reap. That's workable
but the user wants:

1. **Instance-allocated state.** A `ryl_context` (or equivalent)
   owns the tables + world tree + `struct ryl_state`; callers
   acquire it at init and release it on shutdown. Tests and any
   future "reload world" path become tractable.
2. **Teardown hook.** An `atexit` handler (or explicit
   `ryl_shutdown()` called from `main`'s normal exit paths and
   from `cleanexit()`) frees every resource the context owns.
3. **Leak audit.** Every `malloc`/`realloc`/`strdup` site in
   `res.c`, `json5.c`, and `eval.c` gets paired with a free
   path. Verify under macOS `leaks -atExit -- ./rylween …` and
   `-fsanitize=address` on a CI leg.

Known suspect sites:
- `fold_texts` / `fold_flags` / `fold_enums` allocate names +
  value arrays, never freed.
- `node_free` only runs on `resolve_imports` replacement
  children; `world_tree` itself survives process lifetime.
- `compile_world_tree` replaces `N_STR` nodes in place with
  `N_PATH` — verify the replaced strings are freed.
- `pronoun_tab[sex][xx]` / `glyph_tab[g]` get `strdup`'d on
  every re-fold; currently only run once but any future reload
  leaks without freeing the prior value.

Gate this work the same way (replay byte-identical + check_world
ok), and add an ASan-enabled replay leg to CI once the hook is
in.

### 12.3 Dynamic sizing from world.json5

Currently `struct party` has `name[5][30]`, `hp[5]`, …
`inventory[17]`, `attack_today[26]`; `struct ryl_state` has
`p[26]` and `npc[41]`; `map[42][82]`. These are all legacy
1-indexed "max+1" allocations. The user wants:

- `limits` subtree in `world.json5` carrying
  `{ party_members_min, party_members_max, inventory_slots,
     npcs, parties, map_w, map_h, … }`.
- The runtime allocates on boot from those values (requires
  splitting `struct party` / `struct ryl_state` into heap-
  backed flex arrays or pointer fields; the save format
  changes too).
- Audit every `[5]` / `[17]` / `[26]` / `[41]` / `[82]` / `[42]`
  at the call sites and replace with `<limit>` lookups or
  per-instance caps carried in the context.

Two load-bearing consequences:

- **Save-file compatibility.** `load()` / `save()` in `driv.c`
  currently write the fixed-size structs as raw bytes. Flex-
  array layouts break that. Either version the save format
  (header with limits, body with the actual count) or keep
  reading the legacy fixed layout into an intermediate buffer
  and copy into the new struct.
- **1-indexing.** Many call sites iterate `for (m=1; m<=4; m++)`
  and assume slot 0 is unused. Flipping to 0-indexed is a
  separate, bigger cut. For a first pass, keep 1-indexing and
  only make the *cap* dynamic — arrays become `[max+1]` sized.

Sequence the work as:

1. Add the `limits` subtree + fold into a `ryl_limits` struct
   owned by the res module (read-only after load).
2. Audit the hardcoded-size call sites, annotate each with
   the `limits.*` source. No runtime change yet; just the
   catalog.
3. Cut over the easy ones (inventory, party_members) to
   dynamic caps; keep the 1-indexed layout.
4. Save-format migration (deferred — needs its own design).

