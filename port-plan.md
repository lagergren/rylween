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
- Steps, all as `make` targets so the gate stays in one place:
  `make test` (build + `tools/replay.sh --all` +
  `tools/check_world.sh`), `make testasan` (ASan-built replay
  fixtures, fails on AddressSanitizer / LeakSanitizer markers)
  on `ubuntu-latest` where LSan ships enabled — macOS
  Apple-clang ASan is missing LSan, so the Ubuntu leg is the
  authoritative leak gate; macOS ASan stays a scorecard.
  `make format` for clang-format dry-run.

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
in. **Commit C landed**: `tools/replay_asan.sh` + `make testasan`
run the fixture set under `$(BIN_ASAN)` and fail on
`AddressSanitizer` / `LeakSanitizer` markers. See §13.3.

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
  backed flex arrays or pointer fields).
- Audit every `[5]` / `[17]` / `[26]` / `[41]` / `[82]` / `[42]`
  at the call sites and replace with `<limit>` lookups or
  per-instance caps carried in the context.

**Save-file license (user-granted, 2026-04-22).** The legacy raw
`fwrite(&struct_party, sizeof, 1, fh)` layout in `save()`/`load()`
may be retired. Target: replace `realm.dat` and `npc.dat` with
JSON5 written under `$HOME/.rylween/`. When encoding a list, the
record carries its length explicitly (`"parties": [...]`), so
dynamic caps come for free. Not required to be human-readable —
we can swap in a binary encoding later if the write path becomes
a bottleneck, but no need to preserve binary compatibility with
existing saves.

Files in scope:
- `realm.dat` → `realm.json5` (party table + session cursors).
- `npc.dat` → `npc.json5` (state.npc[]).
- `data/map.dat` (42×82 terrain bytes, read-only authored
  content) can fold into `world.json5` as a dedicated shard —
  same $import mechanism the loader already supports. Keep
  `.dat` as a fallback only if the diff churn is too big.
- `entry.dat` (high-score entry text) stays plain text.
- `data/journal` stays plain text (see §12.4, where the journal
  code itself moves owners).

**1-indexing note.** Many call sites iterate
`for (m = 1; m <= 4; m++)` and assume slot 0 is unused. Flipping
to 0-indexed is a separate, bigger cut. For a first pass, keep
1-indexing and only make the *cap* dynamic — arrays become
`[max+1]` sized.

Sequence the work as:

1. Add the `limits` subtree + fold into a `ryl_limits` struct
   owned by the res module (read-only after load).
2. Audit the hardcoded-size call sites, annotate each with
   the `limits.*` source. No runtime change yet; just the
   catalog.
3. Cut over the easy ones (inventory, party_members) to
   dynamic caps; keep the 1-indexed layout.
4. Replace `realm.dat` / `npc.dat` with JSON5 writers/readers.
   Since we have the loader infrastructure already, reuse it
   for reads; write via a straightforward emit pass.
5. Fold `data/map.dat` into `world.json5` (as a shard).

### 12.4 Residual cleanup queued across sessions

Small items surfaced in passing during the Phase 6d series that
aren't worth their own §. Clear when the relevant tree is already
open:

- **`title-menu-exit.env` sidecar with `RYL_NO_DELAY=1`.** Should
  drop the replay suite from ~15s to ~3s. Needs a bless if any
  delay-dependent byte leaks into stdout; suspected clean but
  re-verify against the golden.
- **Ydarg-as-mission.** End-game boss gated on
  jazzyfoot/mastercrown/silverkey/bracelets should move from the
  bespoke `ydarg()` / `confrontation()` path into a regular
  mission with trigger + item-flag conditions. Blocked on the
  DSL evaluator learning mission triggers + item-flag paths.
- **`.git-blame-ignore-revs`.** Record `33ffc52` (the
  clang-format reformat) so `git blame -w --ignore-revs-file
  .git-blame-ignore-revs` skips it. Easy win, do next time
  `.git*` files are open.
- **Journal code lives in the wrong module.** §19.2 shrank
  this: the file-backed `journal()` / `clrjr()` helpers are
  gone, only `read_journal()` (viewer) and `journalf()`
  (single-line writer, thin wrapper over
  `party_journal_add`) still live in `driv.c`. Still fits
  better alongside guild / mail / trade in `interact.c`, but
  the move is now ~40 lines, not the BBS-era sprawl it used
  to be.
- **Makefile header dependency tracking (`-MMD -MP`).**
  Surfaced during §19.2.2: editing `state.h` and running
  `make build` only rebuilt the `.c` files whose source
  changed, so `snapshot.o` / `driv.o` / etc. linked against
  the old `struct party` layout and produced garbage
  snapshots. Current mitigation is `make clean` before any
  build that touches a widely-included header; proper fix is
  a one-liner switch to `-MMD -MP` plus `include $(OBJ:.o=.d)`
  so make tracks header deps automatically. Keep it tight: one
  Makefile edit, verify incremental rebuild on a `state.h`
  edit covers every dependent `.c`, re-bless not required.
- **Clangd cache drift.** Background `<new-diagnostics>`
  reminders sometimes reference symbols that no longer exist
  (e.g. `sextext`, `kar`, `ryl_seed_now`, `strid.c`). The
  source tree is clean; the ghost file lives in a stranded
  Claude-agent worktree at `.claude/worktrees/agent-a4fa40c7/`
  that clangd indexes. `make clean` doesn't touch it. Gate on
  `make` + `tools/replay.sh --all` + `tools/check_world.sh`;
  stale diagnostics are cosmetic. Optional: remove the
  stranded worktree once we're sure the Claude Code runtime
  isn't still pointing at it.
- **ANSI SGR escapes hardcoded in tx() strings.** `gfx.c`,
  `primitives.c`, and `subs.c` embed raw `"\x1b[3Nm"` /
  `"\x1b[4Nm"` sequences inline (e.g. `tx("\x1b[30m\x1b[ p\x1b[37m")`
  in `cleanexit`, `tx("\x1b[37m")` throughout). `res.c` now
  has a local `ANSI_FG_*` enum (Commit B of §12.2) — lift it
  to a shared header (or introduce escape-string defines like
  `ANSI_FG_CYAN_ESC "\x1b[36m"`) so every site reads in
  English. Keep the scope tight: one header, pure textual
  substitution, no behavior change. Gate byte-identical on
  replay.

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

## 17. A.4 slice: mission-control + support modules

**Status:** pending — raised 2026-04-23 during the §16.5 mission-state-
machine session. Remaining Phase-6-class text-extraction targets after
A.2 (speak.c, done), A.3 (towne.c, done), A.5 (driv.c / ydarg.c
residuals — closed empty; slices A/B/C of §16.3 caught everything).

### 17.1 Scope

| File        | LOC  | Shape | Fixture-reach |
|---|---|---|---|
| missions.c  | 583  | Map-position triggers: (E)nter / (L)eave dungeon prompts at fixed (x,y) coords, flag-guarded. Calls `dungeon(N,M)` + `update_map()` on enter. 11 trigger blocks. | Audit first. |
| dungeon.c   | 917  | Dungeon-level exploration UI: room descriptions, loot, stairs, NPC cameos. | Likely some. Biggest fixture-rebless risk. |
| interact.c  | 428  | Party-level menus: drop / swap item flows, HP viewing, rest prompts. | Likely some. |
| subs.c      | 1310 | Character-creation helpers, scorelist, misc utilities. Overlaps with §14.4's scorelist decomposition plan. | Title-flow fixtures touch parts. |

### 17.2 Infrastructure deltas

§16.4's `play_script` op list (`screen` / `journal` / `set_flag` /
`reward`) does not cover missions.c's shape — it's menu-driven
(E)nter/(L)eave prompts followed by a C-level `dungeon(N,M)` handoff
that has no script-level analogue. Two paths:

**Option A — C-level refactor only.** Keep control flow in C. Use
`ryl_menu` for the keypress and inline the `dungeon(N,M)` call; use
`tx_fmt` / `tx0(res_text(...))` for screen text. Mechanical text
extraction, no new script ops. Recommended starting point.

**Option B — extend the DSL.** Add:
 - `menu_branch` op — wraps `ryl_menu` plus per-option sub-scripts.
   Each option has a `then: [...]` nested op-list (re-enters
   `play_script` recursively) or a `pass` terminal.
 - `engine_call` op — invokes a registered C function by symbol with
   literal-int args (e.g. `{ engine_call: 'dungeon', args: [12, 8] }`).
   Registry is a tiny table in `script.c`; keeps the DSL from
   stringifying every C entry point.

**Recommendation: start with Option A.** Only pursue Option B if A.4
slices A-D surface ≥3 near-identical C blocks that would justify the
new ops. Don't add them speculatively — §16.8's "interpreter must not
grow into a general scripting language" applies.

### 17.3 Slicing

- **A.4 slice A — missions.c.** 11 map-trigger blocks. Option-A style
  (ryl_menu + inline `dungeon(N,M)` + text records). Fixture audit
  first; most blocks are flag-guarded on mid-game quest state so
  fixture-unreachable, but verify before migrating.
- **A.4 slice B — interact.c.** Same shape, smaller footprint.
- **A.4 slice C — dungeon.c.** Biggest slice (917 lines, likely
  fixture-reachable). Expect a rebless; budget the CSI-right-prefix
  delta arithmetic per §16.8.
- **A.4 slice D — subs.c.** Overlaps with §14.4's scorelist
  decomposition. Ship the two together or sequence them so only one
  fixture rebless is needed.
- **A.4 slice E (conditional) — `menu_branch` + `engine_call` ops.**
  Only if slices A-D accumulated enough repetition to justify the
  DSL growth. Otherwise close without doing it.

### 17.4 Things to watch

- **Fixture reachability is per-block, not per-file.** Within
  missions.c each of the 11 trigger blocks has its own (x, y, flag[N]
  == K) gate — audit each individually. Within dungeon.c, certain
  rooms are fixture-hit and others aren't. Before migrating a block,
  know whether it's on a replay path.
- **CSI-right-prefix consolidation shifts bytes by N × 5 per site.**
  Same arithmetic as the §16.3 slices. Decompose the delta in the
  commit for each reblessed fixture so future debugging doesn't have
  to recompute.
- **subs.c's scorelist** (§14.4) uses a ~3100-byte stack sort with
  30-byte `strcpy` swaps. §14 has its own decomposition plan (sort
  an index array). Don't fold that rewrite into a text-extraction
  slice — the rebless signals are too different.
- **Cross-slice rebless interference.** If a fixture's byte stream
  touches both missions.c and dungeon.c text, prefer reblessing
  after the LAST slice on that path rather than letting the fixture
  drift slice-by-slice. Makes bisecting a regression easier.
- **missions.c's `ryl_chance()` guards** (e.g. the 9% scruffy-merchant
  appearance) are runtime-random imperative logic. Leave them in C
  even under Option B — a `chance` script op would need the same
  deterministic-seed plumbing A.2 fought through and it's not worth
  the churn for three call sites.

## 18. ANSI art EAW / narrow-cell rendering — pending

**Status:** pending — raised 2026-04-23 after the §17-F smith
migration. The `data/ansi/*.ans` (full-screen art: title, screen,
death, etc.) and `data/tiles/*.ans` (in-game sprites blitted via
`blitgfx` → `render_at`) are authored with Unicode block-drawing
characters in the U+2580..U+259F range: `▀▄▌▐█░▒▓` plus the
geometric shapes `■▬`. Every one of those is Unicode EAW class "A"
(Ambiguous). Terminals differ on whether to render them as 1 cell
(narrow) or 2 cells (wide). The art is authored for 1-cell-wide
rendering (its Amiga lineage), so wide-cell terminals stretch each
glyph horizontally and the layout drifts diagonally (each row
ends up further right than the previous).

### 18.1 Who's affected

- **macOS Terminal.app** — renders EAW=A as 2 cells by default for
  CJK-locale parity. No UI toggle exposes narrow-cell mode.
- **iTerm2, Ghostty, Alacritty, Kitty, WezTerm** — default to
  1-cell rendering for EAW=A. Art looks clean. iTerm2 also
  exposes an explicit toggle (Profiles → Text → "Treat
  ambiguous-width characters as double-width" — off).
- **Linux consoles** (gnome-terminal, konsole, xterm) — typically
  1-cell by default; vary by locale.

### 18.2 Why the obvious fix doesn't work

A failed attempt on 2026-04-23: rewrite `render_at` and a new
`.ans` branch of `disptext` to anchor every visible glyph to its
authored column via `ESC[<col>G` (CHA — Cursor Horizontal
Absolute) emitted after each glyph write. The walk parses CSI
escapes (passes SGR through, syncs the tracker on
CUF/CUB/CHA/CUP) and UTF-8 glyphs.

The approach works for symmetric block chars — `█▓▒░`. A wide-
rendered `█` occupies two cells with visually-equivalent halves;
overwriting the right half with the next glyph crops the render
cleanly and alignment holds.

It fails for the asymmetric half-block family — `▐▌▀▄`. A
right-half block `▐` rendered at 2 cells has its visible mark in
the **right** cell of the pair; my fix overwrites that right cell
with the next glyph and leaves the (empty) left cell behind. The
reverse holds for `▌` (left-half, visible in the left cell of the
2-cell render — which survives; but its right-hand neighbour now
starts on what used to be the right half of ▌'s 2-cell render,
overlapping visually). The same asymmetry applies to `▀` (top-
half) and `▄` (bottom-half) — while their horizontal extent is
full, the 2-cell rendering puts the glyph in a specific half
that can't be cropped symmetrically.

Visible result: patches where a `▐`-heavy section looks shifted
leftward and overlaps neighbouring structure. The user
confirmed this with a screenshot showing the second purple row
of title.ans overlapping the axe shape. Reverted the whole
attempt and the 3x fixture rebless; tree is back to the pre-
attempt state.

The code can't recover per-glyph which-half-is-visible without
knowing how the terminal chose to scale each specific
codepoint. Unicode's EAW table doesn't expose that, and
terminals don't report it.

### 18.3 Viable paths

**A. Document and defer (current state).** Recommend iTerm2 /
Ghostty / Alacritty / Kitty / WezTerm in the README. Zero code
change. Clean result on any terminal that renders EAW=A as
narrow.

**B. Rewrite the art to symmetric-only blocks.** Drop the
asymmetric `▐▌▀▄` and redraw every `.ans` file with
`█░▒▓` + spaces. The symmetric-only art would render correctly
even under wide-cell terminals using approach 18.2. Big art job;
loses detail because asymmetric blocks provide the fine edges
that make the adventurer silhouettes readable. Estimate the
rewrite at a day of careful art work per `.ans` file, ~15 files.

**C. Use escape-sequence cell geometry.** There exists an
experimental `DECSWL` / `DECDWL` pair that sets per-line
single-vs-double-width — but that applies to the **terminal's**
cell width, not the rendered-char width, and isn't widely
supported. Not viable.

**D. Switch from Unicode blocks to Sixel / Kitty-graphics
protocol.** The art becomes bitmap images embedded in the
terminal stream; terminals that support the graphics protocol
render them pixel-accurately; those that don't fall back to
some approximation. Biggest scope of all — effectively
replacing the art engine, plus per-terminal compatibility
testing.

### 18.4 Recommendation

Keep path **A** (documented limitation) for now. The blast
radius of the art is a cosmetic issue only; the game plays
identically in all terminals. When someone wants to pick up
path B, budget it as an art project separate from porting
work — its gate isn't `make test` (byte-identical bytes don't
help; the render changes are authorial) but a side-by-side
visual diff against the pre-rewrite version in a narrow-cell
terminal.

### 18.5 Add to README (follow-up)

One paragraph in `README.md` under "Running": "The ANSI art
assumes 1-cell-wide rendering of Unicode block characters; use
iTerm2, Ghostty, Alacritty, Kitty, or WezTerm. macOS Terminal.app
renders block glyphs as 2 cells and the layout drifts. See
port-plan §18 for the details."

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

## 20. Code-as-data extraction queue (audit 2026-04-25)

Comprehensive audit of every src/*.c file outside the engine plumbing
(script.c, json5.c, snapshot.c, eval.c, primitives.c, ryl.c,
compat.c, gfx.c, io_tty.c, res.c are excluded — those are mechanism,
not content). Goal: list every place in the codebase where scenario
content, balance numbers, narrative text, or hardcoded layouts live
in C source instead of JSON5. Plus the related state-coupling debt:
direct `state.p[state.cpl].FIELD` access bypassing the `pa_*` /
`pm_*` / `inv_*` / `res_*` abstractions.

This section is a punch list, not a sequencing plan — the order
inside each bucket is the natural read order, not the work order.
Sequencing notes are at the bottom.

Related memory:
- `feedback_state_abstraction.md` — abstraction-routing rule
- `feedback_battle_data_extraction.md` — earlier sketch (folded
  into this section but still relevant for the "why")
- `project_full_game_gate.md` — the migration → snapshot → longplay
  → harness end goal that this work feeds into

### 20.1 Categories used

For each file we list offenders by category:

  A. **Scenario-as-switch / literal table** — switch arms where the
     body is "set field X = literal Y" (data, not control flow), or
     `static const T table[] = {...}` records baking scenario specs
     into C source.
  B. **Magic balance numbers** — numeric literals representing
     balance or scenario choices, not engine mechanics. Multipliers,
     thresholds, percentages, magic dimensions.
  C. **Direct `state.*` access** — sites that read or mutate
     `state.p[state.cpl].FIELD` (or `state.<global>`) directly
     instead of going through `pa_*` / `pm_*` / `inv_*` / `res_*`.
  D. **Hardcoded narration** — `tx("English text\r\n")` calls in C
     instead of `res_text("namespaced.id")` lookups.
  E. **Hardcoded paths** — `fopen("data/...")` / `disptext("data/...")`
     literals that should go through a `res_path()` helper.
  F. **Unwired DSL hooks** — `rdie(...)` / "not yet wired" markers
     for engine ops the DSL surface promises but the runtime
     refuses.

### 20.2 battle.c (890 lines, ~70 raw `state.*` sites)

A. Scenario tables masquerading as switches:
- battle.c:181-390 — `switch (oc)` item-use dispatch in combat.
  11 magic items (ids 100..111), each arm hardcodes inventory slot
  consumption, damage range, target effect, and narration text.
  Migration: items.json5 entries get an `effect: {...}` payload
  describing damage/target/text. Engine becomes generic `apply_item_effect`.
- battle.c:449-524 — `switch (s->effect)` spell effect dispatch
  (SE_FIRE, SE_CONFUSION, SE_DEATH, SE_HOLD, SE_ANTIMAGIC). DSL
  spell-effect path already exists (lines 436-447); finish it and
  retire the switch.
- battle.c:685-744 — `switch (ryl_random(0, 3))` monster-magic
  dispatch (frost/fire/hold/confuse). Belongs in monsters.json5
  per-monster spell pool with damage scaling expression.

B. Magic balance numbers:
- battle.c:65 — `((lv[1]+lv[2]+lv[3]+lv[4])/4) * 2` party-avg HP
  scaling
- battle.c:67 — `lv[1] * 2`, line 69 `lv[1] * 3` leader HP scaling
- battle.c:74-75 — `noc > 8` foe-count cap
- battle.c:80 — `ws += lv[1]` weapon-skill bonus
- battle.c:191, 209, 452 — spell damage multipliers (1-4x, 3-8x)
- battle.c:629-630 — victory rewards `(noc * h2/4 * ws/4)` exp,
  `(noc * h2/7 * ws/4)/4` gold
- battle.c:696 — `h2 / 5` monster magic damage divisor
- All extract to `data/world/balance/combat.json5` keyed by
  formula name (encounter_hp_party_factor, leader_hp_factor,
  foe_count_cap, weapon_skill_leader_bonus, etc.).

C. Worst raw-state offenders:
- battle.c:65-80 (encounter scaling, repeated `lv[1..4]` reads)
- battle.c:138-143 (combat loop, per-member name/dead/cls reads)
- battle.c:181-390 (item arms, per-slot inventory mutations)
- battle.c:542-600 (spell casting, target indexing)
- Already-pattern: lines 397-443 use `pm_cur(g)` + `pm_*` —
  generalise outward from there.

D. Hardcoded narration (sample, not exhaustive):
- battle.c:37 "ENCOUNTER!\r\n", line 97 "You face\r\n"
- battle.c:132 "Blood shall flow!\r\n"
- battle.c:141 "What now?\r\n", line 160 "Use what?\r\n"
- battle.c:383-386 Flute of Madness narration
- battle.c:687 "Frost Finger\r\n", line 710 "Cloud of Fire!\r\n",
  line 731 "Hold Creature!\r\n", line 738 "Confusion!\r\n"
- battle.c:763 "And hits for\r\n", line 777 "Blow misses!\r\n"
- All move to `data/world/scripts/battle.json5` text subtree
  (model: existing `battle.run.coward` / `battle.run.fail_away`
  res_text usage at lines 112-125).

E. None.

F. None.

### 20.3 dungeon.c (915 lines) — the worst offender

A. Scenario tables:
- dungeon.c:30-78 — **the canonical worst case.** `switch (flag)`
  mapping scenario flag (1..8) to which dungeon levels carry which
  encounter id. Pure scenario layout:
    flag 1 → Fire Shield Guardian (lvl[19]=20) + Fire Shield (lvl[20]=21)
    flag 2 → Thun riddles on lvl[4,8,12,18] + Bracelets (lvl[18]=25)
    flag 3 → Nolrim's Tomb chain (lvl[12..20])
    flag 4 → Master Crown (lvl[10]=29)
    flag 5 → Silver Sanctum (lvl[20]=30)
    flag 6 → Hobgoblin chain (9 level assignments)
    flag 7 → Orc Nest + Tammy's Prison (lvl[6,7])
    flag 8 → Erethor + Lute chain (lvl[3,7,12])
  Migration: `data/world/dungeons.json5` array of scenario
  descriptors, each with `levels: N` + `encounters: [{level, id}]`.
  Engine `dungeon(flag)` becomes a one-line lookup + dispatch.

- dungeon.c:117-803 — `switch (lvl[cl])` over encounter ids 0..38.
  Each case is a per-encounter scenario event:
    1: find an object (3 item-pool branches with id ranges
       1..14 / 36..42 / 39..46 / 100..111)
    2: find gold
    3..5: encounter
    6,7: nothing
    8: chest with `ryl_random(40, 339)` gold
    9: torches
    20: Fire Shield Guardian
    21: Fire Shield
    22..24: Thun riddles
    25: Bracelets of Thun
    26: Spectrum message
    27: Colour riddle
    28: Nolrim's Tomb + Reebar
    29: Master Crown
    30: Silver Sanctum
    31: Hobgoblin patrol
    32-34: Royal Guard chain
    35: Orc Nest
    36: Tammy's Prison
    37: Priests of Erethor
    38: Chamber of the Lute
  Migration: `data/world/scripts/dungeon.json5` with conditional
  branches (`if lvl == 20 then ...`). The encounter id space (0..38)
  itself becomes a `res_enum("dungeon_encounter", "fire_shield")`
  set, retiring the numeric soup.

B. Magic balance:
- dungeon.c:28 — `ryl_random(1, 9)` per-level encounter roll
- dungeon.c:163 — `ryl_random(40, 339)` chest gold range
- dungeon.c:210, 217 — trap-disarm thief chance (4-in-10, 2-in-10)
- dungeon.c:225-264 — trap damage `4 * lv[i]`, chest gold
  `ryl_random(0, 99) + 20 + exp[1]/500 + 300`
- dungeon.c:473-475 — colour-puzzle hardcoded answer binding

C. Raw-state hot spots:
- dungeon.c:166, 267, 277 — `state.p[cpl].gold` mutations
- dungeon.c:214, 253, 264 — `lv[i]` reads in trap scaling
- dungeon.c:675-795 — `name[i]` / `dead[i]` reads in narrative

D. Massive narration blocks (lines 97, 142, 192, 227-250, 286-793).
   Migrate alongside the level switch — separating text from logic
   is what the DSL exists for.

E. None.

F. None directly, but the level switch is a `play_script` migration
   target — once §20.7 lands, replace the dispatch wholesale.

### 20.4 driv.c (1704 lines)

A. None pure-scenario (the main-loop switch at 90-242 is dispatcher
   logic — keep). Menu text lines 93/112/126/140 should use res_text.

B. Magic numbers:
- driv.c:24 — `hours_rested = 14` initial
- driv.c:176 — `c(49, 6)` magic cursor coords (one of multiple)
- driv.c:294-303 — time-of-day boundaries (dawn 6-11, midday
  12-17, twilight 18-23, midnight 24-5)
- driv.c:305, 311 — hunger threshold `food < pl * 3`
- driv.c:341-342 — `ryl_random(5, 74)` x-bound, `ryl_random(5, 34)`
  y-bound for `position_locations()`
- driv.c:343-344 — tile blacklist literal chars `'w'/'m'/'0'/'1'/'2'`
- driv.c:334-339 — `15` location slots, `1..9` placed
- driv.c:365 — `for (i = 1; i < 41; i++)` map row count
- driv.c:401 — `time_left = 175` daily play-time budget
- All extract to `data/world/balance/upkeep.json5` and a new
  `data/world/map.json5` (or extend world.json5) with map
  dimensions / safe-zone bounds / passable-tile class.

C. Direct state access (177 sites — highest count of any file).
   Most are unavoidable in main-loop dispatch. Targets:
- driv.c:96-148 movement arms repeat the `!= 'm' && != 'w'` tile
  blacklist — extract `wld_is_passable(tile)` helper.
- driv.c:101, 115, 129, 143 `state.map[y][x]` reads — wrap with
  `wld_tile_at(x, y)`.

D. Mostly already res_text. Remaining:
- driv.c:1430-1654 — character stats display (showparty / show)
  has `tx("Stats for char: %d\r\n", g)`, `tx("Weapon Skill : %2d\r\n", ...)`,
  etc. Move to res_text fmt strings.
- driv.c:1444 — `tx("Give Character %d a name!\r\n", g)`.

E. Hardcoded paths (12+ sites):
- driv.c:51, 167, 178, 384, 412, 464, 470, 477, 483, 492, 498, 503,
  509 — `disptext("data/ansi/<name>.ans")`
- driv.c:358 — `fopen("data/map.dat", "rb")`
- Wrap with `res_ansi_path("name")` and `res_map_path()`.

F. None.

### 20.5 subs.c (1271 lines, 123 raw `state.*` sites)

A. Scenario-as-switch:
- subs.c:628-667 — `switch (oc)` potion-use dispatch (3 cases:
  ids 36 healing, 37 poison, 38 mana restore). Each arm has its
  own effect formula and narration. Migration: items.json5 gains
  a `category: 'potion'` typed class with declarative `effect`
  spec — `{ kind: 'heal', amount: { add: [15, { var: 'lv' }] } }`
  etc. Engine `apply_potion()` becomes one generic call. Same
  pattern naturally extends to scrolls, food, torches.

B. Balance numbers:
- subs.c:495-527 — temperature damage thresholds (temp 6-9 low,
  ≥10 very low; 5 hp damage per tick — note line 522 may have
  the very-low arm using same `5` as low arm, possible bug)
- subs.c:535-539 — rest restore `hours_rested = 14`, food cost
  `4 * pl`
- subs.c:547-568 — ambush `ryl_chance(19)`, random death pick
  `ryl_random(1, 4)`
- subs.c:634, 646 — potion heal/poison `15 + level`
- subs.c:705-706 — hunt success `ryl_chance(45)`, food gain
  `ryl_random(10, 19) + lv[1]`
- All extract to `data/world/balance/{rest,wilderness,hunt}.json5`.

C. Direct-state hotspots:
- subs.c:72-106 — sleep cycle: `hours_rested--`, per-member
  `hp[i]--` damage. Use `pm_add_hp(slot, delta)` once it exists.
- subs.c:244-297 — drop/swap weapon/armour: direct `wp[i] = 0`,
  `ar[i] = 0`, `inventory[x] = ...`. Use pm + inv abstractions.
- subs.c:396-412 — inventory item-use dispatch (food cap fix-up,
  torch consumption). Direct `inventory[d-64]`, `food = cap`.
- subs.c:432-440 — rest gate: `dead[1] && dead[2] && dead[3] && dead[4]`
  hardcoded 4-member loop (should be `pa_count_alive() == 0`).
- subs.c:506-524 — temperature damage loop: per-member hp
  decrement.

D. Narration (sample):
- subs.c:455 "Camp.", 490 "Post a guard?", 502 "Zzz...", 514
  "Horrible pain sears...", 556 "Ambush!!!", 572 "You wake up
  8 hours later..."
- Move to `data/world/scripts/{rest,hunt}.json5` text subtrees.

E. None.

F. None.

### 20.6 towne.c (389 lines)

A. towne.c:217 — `has_church = (state.twn == 1 || state.twn == 3)`
   hardcoded town ids. Migration: `data/world.json5` towns array
   gets a `has_church: true/false` per record. Same pattern for
   any other "only towns N and M have feature X" checks.

B. Magic numbers:
- towne.c:148 — `lv[1] < 5` minimum level for tax (parameterise)
- towne.c:151-152 — tax formula `(gold * tax) / 100` is fine
  (`tax` already res-driven), but the `100` divisor is implicit
  percent — could be res_path("balance.tax_divisor") for clarity.

C. Direct state: lines 174, 189, 194, 199-200 — `state.p[cpl].x/y`,
   `state.map[y][x]`, `state.twn` for town entry flow. The enter()
   migration depends on `pa_x()`, `pa_y()`, `wld_tile_at()`,
   `pa_first_level()` helpers landing first.

D. Mostly migrated.

E. None new.

F. None.

### 20.7 ydarg.c (554 lines)

A. ydarg.c:234-275 — `switch (d - 48)` four special-item arms
   (fireshield, mastercrown, jazzyfoot, bracelets). Each checks
   `state.p[state.cpl].FIELD == 0` and consumes the item.
   Migration: table of `[item_flag, gate_text, success_text]`
   tuples in `data/world/scripts/ydarg.json5`.

B. Quest-specific magic numbers throughout (item ids, room ids,
   gold rewards) — each will surface during the migration.

C. Heavy `state.p[cpl].FIELD` access (lines 105, 115, 142, 236-272).
   Same abstraction story as battle.c.

D. Mostly migrated to res_text.

E. None.

F. None.

### 20.8 speak.c (765 lines) — dialog DSL is mature, BUT

A. **Mission-stage tables baked as C arrays.** speak.c:355-399
   has `static const mission_stage stages_<role>[]` for merchant /
   ranger / fighter / wizard_robbed / priest / erethor_bard. Each
   record is a flag-condition + script-id + binding spec. Pure
   scenario data living in C source. Migration: `data/world/missions.json5`
   (or new `data/world/speak_missions.json5`) gains an array of
   stage records keyed by NPC role. The struct is well-formed
   enough for a near-mechanical lift.

A.2 **jazzy_ogre_trader inline flow.** speak.c:720-758 —
   hardcoded inventory key range `'A'..'P'` (line 736), magic
   item-id threshold `oc < 35` (line 738), magic location index
   `state.dx[7]` / `state.dy[7]` for "Beast Cave" (lines 746-747),
   direct `state.p[cpl].inventory[d-64]` mutation (line 757),
   hardcoded English in `journalf("The 'Beast Cave' is at %dx%d.", ...)`
   (line 755). Should be a `speak.jazzy_ogre.*` script with a
   reward op (already exists in DSL).

A.3 speak.c outer dispatch (lines 179-259) hardcodes keypress
   chars (`'B'`, `'R'`, `'C'`, `'Q'`, `'P'`) — the dispatch shape
   itself is fine but should match the menu_branch shape used
   elsewhere.

B. speak.c:248 — lodge price `50 * lv[1]` (50 gold per leader
   level). Per-NPC pricing constants embedded throughout. Move
   to `data/world.json5` NPC role records.

C. Heavy `state.p[cpl].lv[1]` and `gold` reads in pricing logic
   (lines 121-145, 248-265).

D. Mostly migrated; speak.c:187 chitchat uses res_text.

E. None.

F. None in speak.c itself.

### 20.9 interact.c (59 lines) — entirely an offender

The whole file is `check_terrain(char old, char new)`:
- interact.c:5-59 — `switch (new)` over terrain chars
  (`'s'/'1'/'0'/'f'/'d'/'t'/'g'/'2'`), each arm writes 1-6 lines
  of hardcoded English narration.
- interact.c:9 — `ryl_chance(9)` 9% trigger rate (magic).
- interact.c:6 — only matches on transitions where `old != new`,
  but doesn't actually use `old` for narration shape.

**Extension idea (user-flagged):** the data model should support
`old → new` transition arcs, not just per-new descriptions.
Entering a desert from grasslands should be able to read
differently than entering from a forest. Schema: a sparse
transition matrix in `data/world/scripts/terrain.json5` —
default per-terrain narration plus optional `transitions: { from:
{ to: ... } }` overrides.

Migration: `data/world/scripts/terrain.json5`, with the trigger
rate as a `chance` field. Engine becomes a thin
`check_terrain(old, new)` that dispatches into the script.

### 20.10 missions.c (292 lines)

A. **Already gold-standard.** missions.c lines 78-143 use
   `mission_triggers[]` table + DSL dispatch. Use this as the
   reference shape for everything else in this section.

   But: missions.c:148 still has `switch (t->kind)` and 167
   `switch (b->kind)` that dispatch to per-trigger handlers —
   those are engine logic (interpreter), so keep.

B. Per-mission balance bits already in data.

C. Lower coupling than peer files but still some `state.*` —
   sweep on contact.

D. Mostly migrated.

E. None.

F. None directly. (See §20.12 for the unwired hooks in eval.c.)

### 20.11 party.c, inventory.c — abstraction layers

These are the abstraction layer the rest of the code should be
calling. No scenario data baked in. Used as the reference
pattern for `pa_*` / `inv_*` callers.

One nit: inventory.c:67-84 `tag_to_which()` hardcodes a small
enum map (fireshield → PS_FIRESHIELD etc.). Acceptable as a
bridge today; should auto-generate from items.json5 special
tags eventually.

### 20.12 Unwired DSL hooks (eval.c)

- eval.c:1267-1268 — `spawn_encounter`: rdie "engine hook not
  wired up". The DSL surface promises this op (eval.c:436, 469
  parser-side) but the runtime refuses.
- eval.c:1270-1271 — `call`: rdie "lib subtree not yet
  consulted". Library-routine dispatch, also promised but unwired.

These are honest TODOs in the runtime. Wiring them likely
unblocks several mission scripts that today have to fall back
to inline C.

### 20.13 Cross-file patterns

Recurrent literals worth a single-source-of-truth helper:

- `state.p[state.cpl].lv[1]` (leader level): 70+ sites across
  battle / dungeon / subs / speak / ydarg. Helper:
  `pa_first_level()` (party.h).
- `state.p[state.cpl].pl` (alive count): 15+ sites. Helper:
  `pa_alive_count()`.
- `state.p[state.cpl].gold`: 20+ sites. Helpers: `pa_gold()`,
  `pa_gold_add(delta)`.
- Food consumption `* state.p[cpl].pl`: subs.c:39, 305, 311, 536,
  539. Parameterise as `upkeep.food_cost_per_member`.
- Map-passable test `tile != 'm' && tile != 'w'`: driv.c:101,
  115, 129, 143; subs.c (terrain check). Helper:
  `wld_is_passable(tile)`.
- `state.p[cpl].x/y`: many sites. Helpers: `pa_x()`, `pa_y()`.
- `state.map[y][x]`: many sites. Helper: `wld_tile_at(x, y)`.
- Per-member equipment access (`wp[i]` / `ar[i]`): many sites.
  Helpers: `pm_has_weapon(slot)`, `pm_has_armour(slot)`.
- `ryl_chance(N)` percentage gates: 50+ sites total. Already
  data-driven where convenient (wilderness / missions); the
  remaining hardcodes should move to balance.json5 per-context.

### 20.14 Sequencing — TINY / SMALL / LARGE buckets

**TINY (single PR, no prerequisites):**

- T1. interact.c → `data/world/scripts/terrain.json5`. Whole file
  is 59 lines of pure data. Adds the optional `transitions: { from:
  { to: ... } }` shape.
- T2. towne.c:217 `has_church` → world.json5 town record field.
- T3. driv.c hardcoded ANSI paths (12+ sites) → `res_ansi_path()`
  helper.
- T4. driv.c `fopen("data/map.dat", "rb")` → `res_map_path()`.
- T5. inventory.c special-tag bridge (`tag_to_which`) — auto-gen
  hook, low priority.

**SMALL (1-2 sessions, may need a small abstraction first):**

- S1. **Add the missing `pa_*` / `pm_*` / `wld_*` helpers**:
  `pa_x`, `pa_y`, `pa_gold`, `pa_first_level`, `pa_alive_count`,
  `pm_has_weapon`, `pm_has_armour`, `pm_add_hp`, `wld_tile_at`,
  `wld_is_passable`. Pure-add commit, no behaviour change. Gates
  green via existing fixtures.
- S2. **State-coupling sweep** — retrofit battle.c, subs.c,
  dungeon.c, ydarg.c to use the S1 helpers instead of raw
  state.*. One commit per file. Byte-identical.
- S3. **enter() migration** — once S1 lands, the towne.c::enter()
  migration the user originally asked for becomes clean.
- S4. **Potion category in items.json5** — declarative effect
  spec for ids 36/37/38; replace subs.c:628-667 switch with
  generic `apply_potion`. Pattern then extends to scrolls / food.
- S5. **Battle balance constants** → combat.json5 (encounter
  HP scaling, foe-count cap, ws bonus, victory rewards).
- S6. **Wilderness / rest balance constants** (subs.c
  thresholds, ambush %, hunt %) → balance subtree.
- S7. **Wire `spawn_encounter` and `call` DSL ops** (eval.c:1267-1271).
  Each is its own commit; both unblock currently-inline mission
  flows.
- S8. **speak.c stages_*[] tables** → speak_missions.json5. The
  struct shape is already well-formed; near-mechanical lift.
- S9. **speak.c jazzy_ogre_trader flow** → speak.jazzy_ogre.*
  script with reward op.
- S10. **Map dimensions / passable tile class** → world.json5
  map subtree, replacing driv.c:341-365 magic 41/74/34 + tile
  blacklist.
- S11. **Lift battle reward + encounter formulas into `formulas:`
  subtree** (follow-up to S5). The eleven `reward_*` / `encounter_*`
  flat constants S5 added are individual knobs; the surrounding
  multiplication structure still lives in battle.c. Lift the full
  expressions into named formulas (`xp_award`, `gold_award`,
  `encounter_h1_scaled`, `encounter_h2_scaled`). Requires extending
  `res_formula_int` (currently single-var `$y`) to accept a binding
  map, or exposing eval.c's `env_set_local` via a new
  `res_eval_expr_with(name, bindings, n)`. Pattern extends to
  dungeon.c chest-gold, hunt fdplus, subs.c starve damage, etc.
  Depends on S5.

**LARGE (multi-session epic):**

- L1. **dungeon.c:30-78 special-encounter switch** →
  dungeons.json5 scenario descriptor array. Engine `dungeon(flag)`
  becomes a lookup + dispatch. **DONE 2026-04-25** (commit
  1962bd9 + dungeons.scenarios subtree; flag-int signature replaced
  by symbolic scenario name).
- L2. **dungeon.c:117-803 level-event switch** → dungeon.json5
  scripts with conditional branches. ~700 LOC of C → ~50 lines
  of JSON5. The mother of all extractions. Prerequisite: L1
  done, plus encounter-id enum (`res_enum("dungeon_encounter", ...)`).
  **DONE 2026-04-25** (commits 28f4d40..fc599b3, 7 steps; the
  switch is gone, dungeon.c is 870 → 171 lines, all 25 cases live
  in data/world/scripts/dungeon.json5).
- L3. **battle.c item-effect switch** (181-390, 11 magic items)
  → items.json5 effect specs + generic `apply_item_effect`.
  Prerequisite: DSL effect-syntax (damage formula, target
  selector, text template) per effect kind.
- L4. **Spell DSL completion** — finish migrating battle.c:449-524
  spell switch into the DSL spell-effect path that already exists
  at lines 436-447. Retire the switch.
- L5. **Monster magic table** (battle.c:685-744) →
  monsters.json5 per-monster spell pool with damage scaling
  expression.
- L6. **Battle narration sweep** → `data/world/scripts/battle.json5`
  text subtree (~30 hardcoded `tx()` strings).
- L7. **Camp / hunt / lodge / tavern flow lifts** — convert
  inline subs.c / towne.c flows into `data/world/scripts/{rest,
  hunt,lodge,tavern}.json5`. Last of these is the longplay-
  fixture prerequisite (per project_full_game_gate.md).
  **DONE 2026-04-25** (commits e355ef8 + d283423 + ecfd183 +
  03d2eff, 4 steps): every inline-narration flow named in L7's
  scope is now data-driven. See §20.18 for the full closure
  write-up; key landings:
    - `subs.c hunt()` 70 → 18 lines → `data/world/scripts/hunt.json5`
      + `data/world/texts/hunt.json5`.
    - `subs.c camp()` 164 → 35 lines → `data/world/scripts/rest.json5`
      + `data/world/texts/rest.json5`.
    - `speak.c` bargirl Q→L arm 35 → 13 lines → `data/world/scripts/lodge.json5`
      (`lodge.bargirl`); non-bargirl per-job static lodge text
      stays on `txi('speak.lodge.j%d', npc_job)`.
    - Tavern was already migrated in §17-F (towne.json5).
  Step 1 extended the DSL with two read-side expr forms
  (`{hours_rested:0}` / `{time_left:0}`), one mutate target
  (`{time_left:0}` → `pa_set_time_left`), two `engine_int`
  helpers (`pa_alive_count`, `pa_first_level`), and one
  `engine_call` (`advance_hour`).

### 20.15 Prerequisites & ordering

The cleanest dependency order is:

  1. T1, T2, T3, T4 (TINY, parallel) — buy quick clarity wins.
  2. S1 (helpers) — unblocks every later state-coupling reduction.
  3. S2 (sweep) and S7 (unwired hooks) in parallel — both improve
     the platform before the heavy lifts.
  4. S3 (enter migration) and S4 (potion category) — first uses
     of the new helpers in real migrations.
  5. S5, S6, S8, S9, S10 — incremental data extractions.
  6. L1 → L2 (dungeon stack) — sequential.
  7. L3 → L4 → L5 (combat data stack) — sequential.
  8. L6, L7 — independent of the others; can land any time after
     S1-S2.

The full-game gate (project_full_game_gate.md) wants L7's flow
lifts done before the longplay fixture, so any session focused
on that goal should pick L7 items.

### 20.16 Coverage + validator additions (2026-04-25, post-L2)

Two follow-ups from the L2-closing continuation prompt landed in a
short coverage / hygiene session:

- **wilderness-cave-descent replay fixture** (commit 06b7ec3).
  Closes the L1.5 / L2 coverage gap noted in the previous session
  marker — until now no fixture under tools/replay/ entered a cave.
  Walks the seed-42 entropy stream until the random cave-found
  prompt fires, enters via E, takes the level-1 generic gold
  encounter (193gp), descends to level 2 (consumes one torch),
  ascends back, ascends out of the cavern, exits on EOF. Exercises
  subs.c::random_wilderness cave dispatch + dungeon() level driver
  + (D)/(A) menu + the random-fill DSL scripts (gold here, with
  the emit + mutate + wait_input shape). 17 → 18 fixtures, byte-
  identical, deterministic across re-runs.

- **Fold-time script-body symbol validator** (commit 55d0b1f).
  New `fold_scripts` walks `data/world/scripts/*` at world load
  and resolves every symbol-typed field through the matching
  res_* table. Covers encounter.foe / encounter.tier /
  dungeon.scenario / speak.building / reward.items[] / set_flag
  tag+value / engine_call / engine_int / engine_bind.fn / enum /
  item / pic / flag. Diagnostic style matches fold_dungeons (`res:
  <path>:<line>: scripts.<breadcrumb>: unknown ...`). Two new
  helpers (`res_monster_tier_exists`, `res_dungeon_scenario_exists`)
  and three script-introspection wrappers
  (`script_engine_{call,int,bind}_argc`) so the walker doesn't
  duplicate the engine_tbl lists. Required publishing
  `world_tree` to res.c's static slot before the walk because the
  existing res_*_id helpers read off it; the path-compile pass
  that runs after still mutates `root` in place safely.

### 20.17 Single-ownership status (snapshot 2026-04-25 — post-L7)

Per `feedback_state_abstraction.md`, the long-running goal is one
owner per `state` field — every read/write should route through
`pa_*` / `pm_*` / `inv_*` / `wld_*` / `res_*` rather than indexing
`state.p[state.cpl].*` directly. Counts as of HEAD (post-L7):

  | file        | state.* | helpers (pa/pm/inv/wld/res) | notes                                  |
  |-------------|---------|-----------------------------|----------------------------------------|
  | battle.c    | 1       | 168                         | effectively single-ownership clean     |
  | subs.c      | 24      | 203                         | residual is map / global_name / hi-score |
  | driv.c      | 167     | 134                         | main-loop dispatch — H2 target         |
  | speak.c     | 6       | 53                          | residual is npc[i] direct-walk in speak() |
  | dungeon.c   | 2       | 9                           | DONE (residual = 2 sentinel reads)     |
  | towne.c     | 14      | 34                          | helpers lead                           |
  | missions.c  | 13      | 9                           | residual is mission stage table reads  |
  | ydarg.c     | 13      | 77                          | helpers dominate                       |
  | interact.c  | 0       | (file is 1 trivial wrapper) | DONE                                   |
  | script.c    | 17      | 162                         | DSL bridge layer (acceptable)          |

In every file except driv.c, helpers now outnumber raw `state.*`
accesses. battle.c / dungeon.c / interact.c are essentially fully
migrated; subs.c, speak.c, ydarg.c are predominantly through-helper.
driv.c is the only single-ownership debt of meaningful size — its
167 hits are mostly main-loop dispatch (movement arms reading
`state.last_terrain` / `state.p[cpl].time_left`). The real
reduction wedge there is `wld_*` / `pa_*` helpers for the movement
bookkeeping, not a DSL migration. driv.c retrofit is the **H2**
task — queued in the in-flight session continuation prompt; it
can land independently of any data extraction.

The script.c count is the engine bridge — `ie_*` / `sb_*` / `eng_*`
wrappers that read state on the DSL's behalf. Per the rule,
**these should themselves call `pa_*`/`pm_*` rather than read
state directly**; auditing the existing wrappers for compliance
is the next hygiene pass after the LARGE bucket.

(Counts above are first-cut greps — `[^a-zA-Z_]state\.` for the
state column and `(pa_|pm_|inv_|wld_|res_)[a-z_]+\(` for helpers.
Comments and string literals can inflate either side; the trend,
not the absolute count, is what matters.)

### 20.18 L7 closure write-up (2026-04-25)

L7 closed in four commits over a single session
(`e355ef8` → `d283423` → `ecfd183` → `03d2eff`). The session
exposed enough generalisable detail to warrant capturing as a
playbook for future flow lifts.

**DSL surface added in step 1** (`e355ef8`, src/script.c +30 LOC):

  - read-side expr forms `{hours_rested:0}` / `{time_left:0}`
    (mirror the existing mutate-target shapes — same convention
    `gold` / `torches` / `food` / `pl` / `hour_of_day` already
    use). Bare-int form means the value-arm `:0` is a placeholder.
  - mutate target `{time_left:0}` (LV_TIME_LEFT) — paired with
    `pa_set_time_left`. Closes the read+write pair so hunt's
    `pa_add_time_left(-cost)` ports cleanly to a sub op.
  - `engine_int pa_alive_count` (0-arg) — for the camp wipe-out
    early-out (`pa_alive_count() == 0` / `pa_count_alive()` was
    a pre-L7 follow-up note).
  - `engine_int pa_first_level` (0-arg) — for the hunt food-gain
    formula and the bargirl per-leader-level fee (50 * lv[1]).
  - `engine_call advance_hour` (0-arg) — camp's 8-iteration
    clock-bump loop.

  Pattern: any time a script needs a piece of party state the
  DSL can't see, the cheapest extension is one entry in the
  matching registry. Read-only counters → `engine_int`;
  read+write counters → expr form + LV_* + read_lv/write_lv;
  void-return engine surfaces → `engine_call`.

**C-wrapper convention (carries forward)**. The four wrappers
follow the same shape — towne.c smith / tavern / inn / merchant
established it in §17-F, and L7 reaffirms it for the subs.c side.

  ```c
  void <flow>() {
      char buf1[16], buf2[16], ...;
      snprintf(buf1, sizeof buf1, "%d", res_int_path("balance.<flow>.<key1>"));
      snprintf(buf2, sizeof buf2, "%d", res_int_path("balance.<flow>.<key2>"));
      ...
      play_script("<script_id>", (ryl_bind[]){
          {"<key1>", buf1},
          {"<key2>", buf2},
          ...
          {0},
      });
  }
  ```

  The C wrapper owns: balance constant resolution, environment-
  coupled values (opening hours via `res_building_hours`),
  any pre-formatted strings the script's text placeholders need.
  The DSL owns: every `tx*()` / `clw()` / `ryl_pause()` /
  `ryl_delay()`, all keypress dispatch, all party-state mutation.
  Splitting at this seam keeps `play_script` calls trivially
  reviewable and survives DSL evolution without churn.

**Cosmetic deltas (acceptable; documented in commit messages)**.
Three patterns came up across the four steps:

  - `tx("  %s", res_text("common.yes"))` (two-space indent) → bare
    `emit common.yes` in the DSL (no indent). speak.c's bargirl
    flow had this; matches inn / smith migrations from §17-F.
  - `tx("Very low temperature.\r\n")` printed without a leading
    `clw()` so it stacks under prior text in camp's temperature
    check; preserved literally — the rest.json5 script omits the
    `engine_call clw` in the `temp_very_low` arm.
  - DSL `emit` of an array-of-strings text record adds a final
    CRLF after the last line (the array→stream convention); some
    C tx() sites historically did not. Indistinguishable on the
    rendered terminal but visible in byte-stream diffs.

  None of L7's flows have replay fixtures today, so byte-identity
  isn't tested. Authoring fixtures (§20.19) will surface any of
  these deltas as expected diffs.

### 20.19 Fixture queue — replay coverage for L7 flows

Three new replay fixtures lock down byte-identity for the L7
surface. All three landed in the post-L7 fixture-author session
and fold into the existing `make check` aggregate; total replay
fixture count moves 18 → 21.

  1. **`wilderness-hunt`** [DONE] — exercises subs.c hunt() →
     hunt.json5. Four-Ranger party, H from spawn (12,9), member 1
     picked. Seed-42 lands on success → "20 food rations" trap
     (random[10,19]+pa_first_level=1 = 20). Validates: who-loop
     digit_range, pa_is_class enum lookup, time_left mutate sub,
     food mutate add, pa_first_level engine_int. The `-fail`
     variant is a follow-up — would need a different seed or
     enough RNG burn to push `ryl_chance(45)` past success.

  2. **`wilderness-camp`** [DONE] — exercises subs.c camp() →
     rest.json5. Burns 14 X+N (cancelled-quit) cycles to drain
     hours_rested 14→0 without firing random_wilderness
     encounters (movement keys would). C N to enter camp without
     guard. Seed-42 lands on temperature [6,10) → "Low
     Temperature" + "Party loses 5 health"; ambush_pct(19%) roll
     misses → well_rested wake-up; heal+15 clamps every member
     back to full hp from the temp damage. Validates: every L7
     DSL extension at once — hours_rested expr, advance_hour
     engine_call, pa_alive_count engine_int, food clamp:min,
     hp clamp:max, mutate set on hours_rested + hour_of_day. The
     `-ambush` and `-guard` variants are follow-ups; this one
     covers the largest single-fixture DSL surface today.

  3. **`towne-thord-tavern-bargirl-lodge`** [DONE] — exercises
     the speak.c bargirl Q→L arm → lodge.bargirl. Important:
     reach is via inn() chitchat (`I N N N C` then NPC letter),
     NOT tavern S — the tavern's S key still calls `speak(building='inn')`
     under the legacy bld=1/inn vs bld=2/tavern semantics
     (script.c:1287 documents this preserved Amiga-era cross-
     listing). The bargirl Deanna only shows in the inn's
     chitchat list. Letter E (after 3 random NPCs + Niklas at
     building=tavern). Q L Y → 50 gold deducted (gold 1000→950),
     each member -1 hp via the `gt: hp > 1` guard. Validates:
     gold mutate sub, member-field hp mutate sub with the gt
     guard, for_member loop. The `-broke` variant is a follow-up;
     hardest part is dropping gold below 50 (RYL_STARTING_GOLD
     env caps at ≥1000, so the variant needs ≥20 lodge-pay
     iterations or a merchant-spend prelude).

  Pre-flight wiring fix landed alongside this session: L7 steps
  2/3/4 added the script files (data/world/scripts/{hunt,rest,
  lodge}.json5) and texts (data/world/texts/{hunt,rest}.json5)
  but never added the matching `$import` lines to world.json5's
  scripts: and texts: subtrees. play_script("hunt"|"rest"|
  "lodge.bargirl") would die at lookup. Fix: import all three
  scripts and the two text shards; unwrap the `{ hunt: [...] }`
  / `{ rest: [...] }` outer keys so the imports flatten to ARR
  (play_script("hunt") needs scripts.hunt to BE the array, not
  contain a `.hunt` key). lodge.json5 keeps the outer wrapper —
  scripts.lodge needs to be an OBJ for the lodge.bargirl path.

### 20.20 DSL extensions discovered as nice-to-haves during L7

Items that would have made L7 cheaper but weren't worth doing
inline. None block subsequent migrations — they're ergonomics
investments.

  1. **`engine_int` class-id symbol args** (carried over from
     pre-L7 follow-up list). Today `pa_is_class` takes two ints,
     so the script writes
     `args: [{var:'who'}, {enum:['pc_class','Ranger']}]` to
     resolve the class-id symbolically. A nicer surface would
     accept `args: [{var:'who'}, 'Ranger']` directly and look
     the second positional up via `res_enum_id('pc_class', ...)`
     when the registry sig says it's a class id. Saves the
     `enum:` boilerplate at every class-gate callsite.

  2. **`txi`-style format-id-by-int op** — DONE 2026-04-25.
     `{ emit_indexed: { fmt: 'speak.lodge.j{}', value: <expr> } }`
     lives in src/script.c as `do_emit_indexed`. Strict variant —
     die_at if the resolved id has no text record (catches the
     silent-empty-line failure mode). Proving callsite is
     `lodge.nonbargirl` in data/world/scripts/lodge.json5; speak.c
     L-arm now dispatches between `lodge.bargirl` and
     `lodge.nonbargirl` based on `npc_job(talker) == Bargirl`.
     Remaining txi callsites (`speak.people.j%d`, `speak.magic.j%d`,
     `speak.chit.reply.<subj>.j%d`) can lift via the same pattern
     when the speak.c residual sweep happens — chitchat has two
     int axes, so it'd need a two-token fmt or a string-template
     extension; defer until a clear win shows up.

  3. **`{path: 'dotted.path'}` expr — DSL-side `res_int_path`
     reader**. L7's camp wrapper marshals 11 balance constants
     into 11 int bindings; hunt does 4. Inlining the lookup
     into the DSL via a `path:` expr form would let the script
     read e.g. `{ path: 'balance.rest.health_gain' }` directly
     and the C wrapper drops to a single-line `play_script(...)`
     call. Footgun: `res_int_path` returns 0 on missing paths
     today, so a typo silently breaks the flow. A strict variant
     (`die_at` on missing) plus a fold-time symbol resolver
     entry (already mostly in `fold_scripts`) covers this. Worth
     doing before the next balance-heavy migration; defer until
     a flow with 5+ tunables shows up again.

  4. **Default-`{0}`-binding for speak op town arg**. Carried
     over from the pre-L7 follow-up list — `{ speak: { building:
     'tavern', town: { twn: 0 } } }` is verbose for "use the
     party's current town". Either default `town:` to `state.twn`
     when omitted, or accept a literal `town: 'current'` symbol.
     Cosmetic; one callsite in towne.json5 (inn → C chitchat)
     and zero in L7 itself.

## 21. Reentrancy / thread-safety plan (audit 2026-04-25)

The user asked: can the gate tests run in different threads or
parallel processes? And, more strategically, what would it take
to make the engine reentrant — pass a context around, drop the
global state — so a single binary could host multiple independent
game instances?

### 21.1 Parallel-process tests — already done

`tools/replay.sh --all` forks one `rylween` subprocess per
fixture, waits for all of them, and prints buffered output in
script order so the summary is deterministic. Each fixture has
its own tmp `HOME`, env sidecar, stdin, RYL_SNAPSHOT path, and
stdout buffer. There is nothing to change: 22 fixtures already
run on whatever cores the OS hands out.

`make check` invokes `tools/replay.sh --all` and `tools/check_world.sh`
back-to-back. The fan-out lives inside the replay script; the
build itself is single-threaded `make` because the source set
is tiny enough that parallel cc isn't a wallclock win.

### 21.2 In-process threading — what blocks it today

Several pieces of process-singleton state would have to be lifted
into a per-instance context. Audited 2026-04-25:

  - **Game state** — `struct ryl_state state;` defined in
    `src/ryl.c` and read/written by every gameplay file. The big
    target: every `state.*` and helper (`pa_*` / `pm_*` / `inv_*`)
    becomes ctx-aware.

  - **RNG** — `static uint64_t rng_state` in `compat.c` (XorShift64*).
    Seeded at startup from `RYL_FAKE_SEED` or `time^pid`. Every
    `ryl_random` / `ryl_chance` advances it. Must be per-context;
    otherwise two threads draining the same generator would
    interleave non-deterministically and break replay.

  - **World tree** — `static node *world_tree` in `res.c`. The
    JSON5 AST that backs `res_text`, `res_int_path`, `res_enum_id`,
    `fold_scripts`, etc. *Read-only after `res_load_world`*, so
    in principle it can be shared across threads — but every
    dependent table (items, monsters, spells, glyphs, towns,
    flags, mission tables, enums) is also a `static` in res.c
    and they're all read-only post-fold. **Shareable as a unit**
    if we encode "world is immutable after load" as a discipline.

  - **Lazy env caches** — `compat.c` has function-statics for
    `RYL_NO_DELAY`, `RYL_FAKE_EPOCH`, and the resolved
    `$HOME/.rylween` path. Each one-shot at first call. Either
    move into ctx or accept that env-var reads are
    process-scoped (i.e., all threads share the same
    RYL_NO_DELAY setting); the latter is fine for testing as
    long as fixtures never need different env values
    concurrently, which today they always do (FAKE_SEED
    differs per fixture).

  - **I/O backend** — `io_backend *io = NULL;` in `io_tty.c`,
    plus `static struct termios saved_termios` and
    `volatile sig_atomic_t io_winch_pending`. The terminal is a
    process-singleton resource — can't have two threads driving
    raw mode on the same tty. For threaded tests, `io` must be
    per-context and configured for in-memory or
    pipe-backed I/O; the `tty_backend` would be a single-thread
    interactive-only path.

  - **atexit handlers** — three of them: `res_shutdown`,
    `tty_restore`, `snapshot_write`. atexit is process-global
    and runs in LIFO order at process exit. None of these are
    per-context-friendly. For threaded mode, replace with
    explicit `ryl_ctx_free(ctx)` lifecycle and skip atexit.

  - **SIGWINCH** — single signal handler shared by the process,
    sets `io_winch_pending`. Each context would need its own
    pending flag; but a process can only deliver a signal to one
    "place," so the handler dispatches to per-thread state via
    something like `pthread_self()` or a TLS slot. Practically:
    threaded engine instances probably don't care about
    SIGWINCH — only the interactive single-threaded session
    does. Make the handler conditional on "this ctx is the
    interactive one."

  - **stdio** — `printf` / `fprintf(stderr)` calls live in
    `primitives.c` (heavy), `res.c`, `snapshot.c`, `json5.c`.
    For threaded mode, every direct stdio call has to route
    through `ctx->io` instead. A grep + audit would find them;
    the non-error path is mostly already through tx/tx_fmt/clw,
    which themselves call into the io_backend.

  - **getenv reads at startup** — `RYL_FAKE_SEED`,
    `RYL_FAKE_EPOCH`, `RYL_NO_DELAY`, `RYL_SNAPSHOT`,
    `RYL_STARTING_GOLD`, `RYL_PARTY_MEMBER_MAX`,
    `RYL_REPLAY_RUMOUR`, `HOME`. Process-global today. Per-ctx
    config would override these via a `ryl_ctx_config` struct
    populated by the harness.

### 21.3 Target shape — `ryl_ctx`

  ```c
  struct ryl_ctx {
      struct ryl_state state;       /* the current state struct */
      uint64_t rng_state;
      io_backend *io;               /* may be tty-shared, in-mem, etc. */
      const node *world;            /* shared, read-only post-fold */

      /* config replacing env-var reads */
      uint64_t fake_seed;
      time_t   fake_epoch;
      int      no_delay;
      const char *snapshot_path;
      const char *home_dir;

      /* per-instance siblings of res.c's tables, IF we want each
         ctx to reload world (probably not — share it). Otherwise
         skip; world is immutable after load. */
  };
  ```

  Every gameplay function gains a `ryl_ctx *ctx` first parameter.
  Helpers (`pa_*`, `pm_*`, `inv_*`, `wld_*`, `res_*`) read from
  `ctx->state` / `ctx->world` instead of file-scope globals.
  `ryl_random(ctx, lo, hi)` advances `ctx->rng_state`.
  Engine bindings registered in `script.c` thread the ctx through
  to the C surfaces they call.

### 21.4 Sequencing — what to do first

  1. **Lift `struct ryl_state state` into ctx.** This is the
     biggest mechanical change but also the most contained: every
     `state.*` becomes `ctx->state.*` (or, better, the existing
     `pa_*`/`pm_*` helpers grow a `ctx` arg and stay the only
     readers). `feedback_state_abstraction.md` is the in-flight
     hygiene push for exactly this; finishing that work is the
     precondition for ctx-ifying the engine.

  2. **Lift the RNG.** Small, mechanical, deterministic. Adds a
     ctx arg to `ryl_random` / `ryl_chance` / `ryl_seed`. Once
     done, two ctxs can run pure-data scripts independently
     without RNG cross-talk.

  3. **Lift the env-var lazy caches.** Replace function-statics
     in `compat.c` with `ctx->no_delay` / `ctx->fake_epoch` /
     `ctx->home_dir`. Cheap, isolating change.

  4. **Decide world sharing.** Either keep `static` tables in
     res.c and document "world is process-shared, read-only
     post-fold" (cheapest), or duplicate every res.c table into
     ctx and reload per ctx (expensive, only matters if we ever
     want two ctxs running different world data — unlikely).
     Recommend: keep shared. Lock `world_tree` to read-only
     after `res_load_world` and assert no late writers exist.

  5. **Lift the I/O backend.** `io_backend *` becomes `ctx->io`.
     Add an in-memory backend (writes to a buffer instead of
     stdout, returns canned input) for headless testing. The
     replay harness would then be a Linux executable that
     spawns N threads, each with its own ctx and in-memory
     I/O, instead of N processes.

  6. **Drop atexit handlers** in favor of explicit
     `ryl_ctx_free()`. The single-process binary still calls
     `ryl_ctx_free()` from `main` (or from a thin
     `atexit(free_main_ctx)` shim).

  7. **Audit direct stdio.** Replace `fprintf(stderr, ...)` with
     `ctx_log(ctx, ...)` or similar. Same for fatal `ryl_die`
     paths. This is mostly mechanical but spreads across many
     files.

### 21.5 What's hard / will resist

  - **`ryl_die`** is currently `noreturn`-on-process-exit. In a
    threaded engine, dying the whole process kills sibling
    instances. Need a setjmp/longjmp escape to a per-ctx error
    handler, or convert the fatal contract to "raise this thread
    out of `ryl_ctx_run` with an error code." Affects every
    `die_at` site in `script.c` / `res.c`.

  - **Terminal raw mode** — fundamentally a process-singleton.
    Two ctxs can't both be the foreground tty driver. Threaded
    engine is for headless / scripted contexts only; the
    interactive game stays single-threaded.

  - **Signals** — see SIGWINCH note above. The model is "only
    one ctx is allowed to be the signal handler"; auxiliary
    ctxs install no-op handlers.

  - **atexit replacement** — needs a coordinated lifecycle, not
    just removing the calls. snapshot_write specifically
    *expects* to fire on process exit; in threaded mode we'd
    call it from `ryl_ctx_free` with a per-ctx path.

### 21.6 Verdict — when is this worth doing?

Process-level parallelism (already shipping) covers the
testing use case. The 22 fixtures take ~2s wall-clock under
`replay.sh --all`. Switching to threads would shave maybe
500ms by avoiding fork/exec and cold-start fold; not nothing,
but not load-bearing either.

The deeper reason to do this is **codebase health**: every
`static` global is a hidden coupling, and migrating to
explicit ctx-passing surfaces those couplings as type-checked
function signatures. The single-ownership work
(`feedback_state_abstraction.md`) is already the first leg of
this — `pa_*`/`pm_*`/`inv_*` helpers are the natural insertion
points for a `ctx` arg.

**Recommended sequencing**:
  - Finish single-ownership migration first (driv.c is the
    debt-bearer; H2 already queued).
  - Once helpers have one owner, add `ctx` to their signatures
    in one mechanical pass.
  - At that point `state` has effectively migrated; the
    remaining ctx work (RNG, env caches, I/O backend) is small.
  - World sharing stays as-is; document the "read-only
    post-fold" contract.

The thread-safety endgame is post-port — a "phase 6" cleanup
once every gameplay file routes through helpers. It's not
gating anything in flight, but the inventory above should be
re-read before any helper grows a non-trivial new global, so
the eventual ctx migration doesn't get worse over time.

## 22. DSL unification plan (audit 2026-04-25)

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
