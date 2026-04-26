# Rylween Port Plan — Active and Reference Sections

This file collects every section of `port-plan.md` that is in-progress, planned, or pure reference / vision material. Completed lifts live in `port-plan-complete.md`; the cross-section index lives in the master `port-plan.md`. Section numbers and `§N` cross-references are preserved verbatim — sections appear in their original §-order regardless of which file they live in.

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

## 24. Logging + remote console (audit 2026-04-26)

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
  - **§24.A1** (1 session, queued): bulk-migrate the remaining
    ~75 `fprintf(stderr, …)` sites (res.c x44, driv.c x36,
    snapshot.c x28, party.c x10, json5.c x5, others) to
    `LOG_W` / `LOG_E` per severity judgment. Bless any drift
    from the `[lvl][mod]` prefix in one wave (none of the
    current fixtures fire stderr today, so likely a no-op
    bless).
  - **§24.B** (1 session): file + null sinks. Add the env-var
    parser; default still stderr.
  - **§24.C** (1 session): TCP listener sink. Bind to
    `127.0.0.1` only by default; explicit
    `RYL_LOG=tcp:0.0.0.0:9999` opts out. Non-blocking writes
    so a stuck reader can't stall the engine. Document the
    rcon-style protocol in `docs/log.md`.
  - **§24.D** (optional, 0.5 session): syslog sink behind
    `RYL_LOG=syslog`. Skip if no demand surfaces.
  - **§24.E** (0.5 session): replay-harness opt-in to
    `RYL_LOG=null` per fixture. Re-bless to drop noise lines
    from baselines.

**Estimated total.** ~3-4 sessions A+B+C, plus optional D+E.
None of this gates §23 work — they're independent files, no
conflicts. §24.A can land between any two §23 commits.

**Bar per session.** Same as §22 / §23: `make check` +
`make testasan` green at every commit.

## 24bis. `id: <number>` → slug-keyed state (deferred, post-§25)

**Survey (2026-04-26 #3).** 205 occurrences of `id: <int>`
across `data/` JSON5 shards: `world.json5` (39 NPCs in
`npcs.<key>` + ~15 per enum across `enums.{job,race,
pc_class,gender,...}`), `world/monsters/monsters.json5`,
and seven item shards (`weapons.json5`, `armour.json5`,
`gear.json5`, `potions.json5`, `specials.json5`,
`treasures.json5`, `magic_items.json5`). Each `id:` is the
bridge between data and an int-keyed C subsystem:

  - `pa_inventory[slot]` stores item ids as ints; lookups
    via `res_item(id)` / `object(id)` index off the int.
  - `state.npc[slot]` is indexed by NPC id; `res_npc_slot
    (sym)` reads the entry's `id:` to map slug → slot.
  - `pm_cl(pm)` / `pm_ra(pm)` return int slots compared
    against `PC_*` constants in `state.h`; `state.p[].cl/
    ra/job` are uint8_t slots.
  - Save format (`current-world.json5`) writes the int
    ids directly — slug-keying state would change the
    save format strictly.

**Why deferred.** §23.B-style migrations (this session)
keep the int storage and only move the *dispatcher* out
of C. Eliminating the int entirely is a different shape:
every state accessor (`pm_cl`, `pa_inventory`,
`state.npc`), every save/snapshot path, and every C-side
comparison (`if (cl == PC_FIGHTER)`) has to switch to
slug strings. That's a coordinated cross-cutting refactor
touching ~every C file, with no incremental win until the
last call site flips.

**Sketch of the work (one possible path).** Three phases:

  1. **State layer flip.** Replace the int slot in
     `state.p[].cl/ra/job`, `state.npc[]`, `pa_inventory[]`
     with a slug-string-id (or a stable interned-symbol
     handle). Provide `pa_class_sym(slot)` / `pa_race_sym
     (slot)` / etc. helpers so accessors aren't bare
     comparisons.
  2. **Comparison-site sweep.** Every `if (cl ==
     PC_FIGHTER)` becomes `if (pa_class_is(cl, "fighter"))`.
     Largest churn site — driv.c, battle.c, subs.c are
     full of these.
  3. **Data-side cleanup.** Drop `id:` from all the
     enum/NPC/item records once nothing reads them.
     `res_npc_slot` becomes `res_npc_handle`; the int slot
     mapping retires.

**Sequencing.** Should land after §25 (1-indexed → 0-indexed)
because that pass also touches every state accessor — bundling
the two sweeps avoids re-touching the same call sites twice.
Probably a 4-6 session block, with one "halt all other work"
period because every C file changes.

**Caveat.** Save format is the strict boundary. Either bump
the save schema and document the break, or write a one-shot
migration that translates legacy int-keyed saves to the
slug-keyed form on first load. The latter is roughly one
session of careful schema-walker code.

## 25. 1-indexed → 0-indexed cleanup (low-priority, post-§22-§24)

**Problem.** A lot of the codebase carries the original 1-based
index discipline through verbatim:

  - Party members: `for (i = 1; i <= res_party_member_max(); i++)`,
    `pa_inventory(slot)` taking `1..INVENTORY_SLOT_MAX`.
  - Monsters: `for (x = 1; x < noc + 1; x++)`, `mh[1..noc]`,
    `deadc[1..noc]` (slot 0 unused).
  - NPCs: `state.npc[slot]` with `slot ∈ 1..npc_slot_max()`,
    slot 0 unused.
  - Towns: `towns[1..n_towns]`; `towns[0]` is the JSON `null`
    sentinel left zeroed (the §24.A0 setup-logging off-by-one
    landed precisely because of this convention).
  - Map / dungeon coordinates use 1-indexed rows in some
    interfaces, 0-indexed in others — driv.c::position_locations
    and wld_tile_at(x,y) don't agree on which is which.
  - DSL ops: `for_member`, `for_monster`, `for_each_alive_*`
    iterate 1..max with `member_idx` / `monster_idx` /
    `target_idx` bound to 1..N. The DSL inherited the C
    convention; the JSON5 bodies all do `{ var: 'target_idx' }`
    expecting 1-based indexing.

**Why it bites.** Three failure modes:

  1. Off-by-one when a new caller picks the wrong convention
     (the §24.A0 `towns[n->towne - 1]` mistake).
  2. `slot 0` reserved bytes — every fixed-size array carries a
     one-element waste, which costs nothing at scale but
     sometimes hides bugs (zero-init looks like "valid empty
     slot 0").
  3. Range-loop noise — `for (i = 1; i <= max; i++)` is the
     uglier C99 cousin of `for (i = 0; i < max; i++)`. Any
     half-step migration leaves some loops one-based and some
     zero-based, multiplying the cognitive load.

**Why it's hard.** The DSL JSON5 shards reference the 1-based
indices directly:

  - `{ member: { var: 'target_idx' }, field: 'hp' }` with
    `target_idx` ∈ 1..4. Lifting to 0-based means rewriting
    every `do:` body that indexes a member or monster, AND every
    fixture's `.expect_snapshot` (snapshots embed slot indices).
    Existing fixtures: ~12 lane-A spell fixtures + monster-magic
    + everything in `data/world/scripts/`.
  - Some C engine-bind functions (`pa_name`, `pa_is_alive`,
    `pm_cur`) take 1-based slot args. Lifting them flips every
    caller too.

**Proposed shape.** Two-step migration, neither attempted before
§22-§24 lift work concludes:

  1. **§25.A — abstract the indices behind iterators / accessor
     helpers.** Stop writing `for (i = 1; i <= max; i++)`
     anywhere; introduce `for_each_party_member(pm)` /
     `for_each_alive_monster(idx, deadc, noc)` macros (or
     proper iterator structs) and let the body see `idx` 1-
     based. Doesn't change semantics — just centralizes the
     loop convention so a future flip is one helper away. ~1
     session per major axis (party / monster / npc / town); 4
     sessions total.

  2. **§25.B — flip to 0-indexed.** Start with the leafmost
     callers (e.g. one fixture's worth at a time). Each step:
     change the C engine-bind to accept 0-based, change the
     JSON5 bodies to compute / pass 0-based, re-bless the
     fixture. The `RYL_FAKE_SEED` order has to stay identical,
     so any draw-loop that did `random(1, 4)` becomes
     `random(0, 3)` — the value space is the same, the
     semantic-of-the-number changes. Lots of small commits, each
     bracketed by one re-bless. Estimated 8-12 sessions
     depending on how aggressive we are about flipping engine
     APIs vs leaving compat shims.

**When to do it.** **Low priority, late.** Don't start until:

  - §22 / §23 lift is complete (no in-flight DSL ops migrating
    around the 1-indexed convention).
  - §24 logging is wired (§25 will surface noisy bless waves —
    `LOG_A` is the right place to record the 1-vs-0 flip
    boundaries for an audit trail).
  - Any future-directions multi-party work (see
    `plans/future-directions.md`) hasn't started — it'll add new
    callers and we don't want to flip them mid-design.

**Scope discipline.** The temptation will be to convert one or
two loops in passing every time someone touches a file. **Do
not.** A half-converted codebase is strictly worse than the
fully-1-indexed one. §25.B lands as a single coherent wave or
not at all; ~1 day of focused work, not a long-tail nibble.

**Estimated total.** ~12-16 sessions over a few weeks once
started. The visible win is small (cleaner loops, slightly
fewer off-by-one surprises) but it permanently eliminates a
class of bugs the §24.A0 commit just demonstrated.

**Bar per session.** Same as elsewhere: `make check` +
`make testasan` green at every commit.

## Session 2026-04-26 closure + continuation prompt

This session shipped five commits on `lagergren/port1`:

  - `ce7af68` — §23.A fixture: combat-monster-magic
    byte-identity baseline + texts-path bug fix in
    `data/world/spells/monster.json5`. The `do:` bodies were
    referencing `monster.frost.name` etc., but the texts live
    at `battle.monster.*`; resolve_text_id short-circuits on
    names containing `.` (treats them as absolute), so the
    lookups silently failed and every monster cast was
    emitting blank bytes. Invisible because no prior fixture
    exercised the dispatcher.
  - `5dfa961` — §24.A0: `src/log.{c,h}` with `LOG_D/I/W/E/A`
    macros, default threshold WARN. `ryl_log()` retired, two
    callers migrated to `LOG_A`. `res_realize_npcs` grew
    descriptive `LOG_I` lines + cosmetic delay 600ms→40ms
    (campaign setup ~14s → ~1s with the dot pacing intact).
  - `d3047d2` — port-plan split: master `port-plan.md` (45
    lines, TOC only) + `port-plan-complete.md` +
    `port-plan-incomplete.md`. New §24 (logging plan) + §25
    (1-indexed cleanup) sections drafted.
    `plans/future-directions.md` (multi-party / MUD /
    shared-realm scoping) created.
  - This commit — session closure + continuation prompt.

**State at end of session.** §23.A complete with fixture
coverage (Hold + Cloud-of-Fire pinned at seed 42; Frost +
Confusion share the same machinery). §24.A0 (minimal logging
API) landed; the 75-site `fprintf(stderr, …)` migration is
queued as §24.A1. `plans/future-directions.md` captures the
multi-party scoping the unfinished "version 3" started
exploring. `make check` + `make testasan` green at HEAD
(`d3047d2`).

==== CONTINUATION PROMPT (paste verbatim next session) ====

Read `port-plan-incomplete.md`'s "Session 2026-04-26 closure
+ continuation prompt" section. Five commits since the last
continuation prompt; the §23 lookahead in §22.14 is still
relevant for everything beyond §23.A.

Recommended sequencing for next session(s):

  1. **§24.A1** (~1 session, mechanical). Bulk-migrate the
     remaining ~75 `fprintf(stderr, …)` callsites to
     `LOG_W` / `LOG_E` per severity. Distribution: res.c x44,
     driv.c x36, snapshot.c x28, party.c x10, json5.c x5,
     primitives.c x2, ryl.c x3, inventory.c x3. None of them
     fire under existing fixtures today (zero stderr bytes
     during fixture playback), so the migration is bless-free
     in expectation. Validates the logging API end-to-end
     before more §23 / §24 work stacks callers on top of the
     legacy pattern. Author rule: severity is `LOG_E` for
     "couldn't proceed, returned default" sites
     (`res_formula_int: world not loaded`, `res: oom`);
     `LOG_W` for "shape was wrong, falling back" sites
     (unknown enum tag, slot out of range, malformed record).

  2. **§23.B** (~2 sessions). battle.c item-spell switch
     (cases 100..111, lines 194-403, ~210 LOC). Biggest §23
     piece remaining. Pattern: `data/world/items/*.json5`
     grows per-id `effect:` specs; the case dispatch becomes
     `apply_item_effect(id, ctx)` (or the data-shape
     equivalent). Rides on §23.A's prep ops + combat-ctx
     bindings already landed. After §24.A1 lands, any new
     diagnostic logging in §23.B uses `LOG_*` directly.

  3. **§23.C / §23.D in parallel** (~1 session each,
     mechanical). Combat-constants sweep + driv.c
     char-creation thresholds. Small.

  4. **§24.B** (~1 session). File + null sinks behind
     `RYL_LOG=file:…` / `RYL_LOG=null`. The null sink lets
     fixtures opt out of stderr noise once §24.A1 has
     migrated everything; the file sink replaces the
     implicit `~/.rylween/rylween.log` write with an explicit
     env-var path. After §24.A1 + §24.B land, the replay
     harness can default to `RYL_LOG=null` per fixture and a
     re-bless drops any residual noise lines.

  5. **§23.F + §23.E** (closeouts). ydarg stairs (already
     partly done in `e61be95`; close out the remaining
     branches) + bestiary special-encounter survey (likely
     closes as documentation).

  6. **Later: §24.C** (TCP listener — telnet-friendly remote
     console; bind to 127.0.0.1 by default). **§25**
     (1-indexed → 0-indexed cleanup, low priority, post-§22-
     §24). **`plans/future-directions.md`** multi-party MUD
     work — only after §22 / §23 / §24 are fully closed.

Pick (1) for a mechanical warm-up; the prompt for (2) is
already in §22.14 of `port-plan-incomplete.md` ("§23.B —
battle.c item-spell switch"). (1) before (2) avoids
re-touching the same callsites; either order is otherwise
fine.

Pre-flight notes:

  - `make check` + `make testasan` both green at HEAD
    (`d3047d2`).
  - All four §23.A monster-spell bodies share machinery; the
    seed-42 fixture exercises Hold + Cloud-of-Fire. Frost
    and Confusion are not byte-pinned by any current
    fixture — the dispatcher coverage they get is
    structural, not byte-identity. If §23.B introduces any
    monster-side logic that depends on Frost / Confusion
    firing, add a second seed/encounter at that time.
  - §24.A0 default threshold is WARN. Set
    `RYL_LOG_LEVEL=info` to see the realm-realization trace.
    AUDIT bypasses the threshold (writes to
    `data/rylween.log`).
  - `port-plan.md` is now a 45-line master TOC. Active queue
    lives in `port-plan-incomplete.md`. When a section
    closes, move its body across to `port-plan-complete.md`
    and update the master TOC's File column.
  - `plans/future-directions.md` is deliberately *outside*
    the port plan — don't merge it back; it captures
    multi-party MUD scoping that's not on any critical path.

Bar per session: `make check` + `make testasan` both green
at every commit.

==== END CONTINUATION PROMPT ====

## Session 2026-04-26 #2 — Mac Terminal rendering + DSL polish

This session detoured from §23.B into a chain of UI / DSL fixes
prompted by visual bugs the user hit while playing the build in
Mac Terminal.app. §23.B itself remains queued (start of session
work was a survey + design pass, no code landed).

Nine commits on `lagergren/port1`, all green at HEAD (`051557d`):

  1. `24ea66e`  title-menu (B): split `driv.menu.header` /
     `driv.menu.common` into per-line records so each
     `tx0`/`tx_fmt` call gets its own ESC[<x>C cursor jump.
     Author rule for menu records that target the right text
     window: each visible row is its own 1-line record.

  2. `d632da6`  title-menu: dropped the redundant white banner
     `driv.menu.banner` ("ADVENTURERS of RYLWEEN" lowercase
     `of`). The all-caps green banner from
     `data/ansi/screen.ans` is the canonical title; menu data
     no longer re-emits it. Owner line ("{name}'s copy.") is
     now the first row of the menu's right-window content.

  3. `d69664e`  primitives: `tx0`/`tx` re-emit ESC[<x>C between
     lines. Multi-line text records (`join_lines_crlf`) put
     `\r\n` between lines; the original code only emitted the
     column prefix once at the head of the buffer, so lines
     2+ rendered at column 1 instead of inside the text
     window. First version of the fix.

  4. `7c5f033`  primitives: robust per-line cursor positioning.
     Replaced the bare `ESC[<x>C` (relative cursor-right) with
     `\r ESC[<x>C` so the CR forces cursor to column 1 before
     the relative jump. `c(x, y)` also switched from
     `ESC[<y>;0H` to `ESC[<y>;1H` (col=0 has terminal-
     dependent behavior; col=1 is unambiguous).

  5. `c73652a`  primitives: `cl()` emits `ESC[2J ESC[H` instead
     of bare form feed (0x0c). On Mac Terminal / iTerm2 form
     feed is treated as LF only (no clear, no home), so the
     screen.ans's own ESC[2J cleared but didn't home the
     cursor — picture rendered shifted to wherever the prior
     `tx` had left the cursor.

  6. `22856cb`  primitives: `cl()` / `disptext` use explicit
     `ESC[1;1H`. Defense-in-depth: `disptext` itself prepends
     `ESC[1;1H` before dumping the .ans, so a picture always
     starts rendering from a known (1,1) state regardless of
     upstream cursor history.

  7. `089277e`  towne.church: fix unbound 'who' in E/R/H
     branches. Real game bug surfaced by the user — the three
     church-service branches each `let: 'member_idx', value:
     { sub: [{ var: 'who_key' }, 48] }` but the body
     downstream reads `{ var: 'who' }` (25 references). The
     DSL aborts at runtime with "unbound 'who'" the moment the
     player picks E/R/H + a digit. Partial rename. Smaller fix:
     rename the 3 lets back to `'who'`. The 4th identical-
     looking line in tavern story is correctly named, left
     alone.

  8. `b5cda77`  scripts: `48` -> `'0'` in who_key digit
     arithmetic across 6 sites. The DSL's expr evaluator
     accepts a single-char string in int position and returns
     its ASCII int. `'0'` reads at a glance; bare `48` doesn't.

  9. `051557d`  script: `digit_range` `index_var` auto-bind,
     kill who_key indirection. Extended `digit_range` to
     accept an optional `index_var:` mirroring the long-
     standing `letter_range` pattern. On match the engine
     binds the digit's int value (`ch - '0'`) into `rt_env`
     directly. Six callsites lose the `key_var: 'who_key'` on
     menu_branch and the `let` arithmetic line. The `'0'`
     subtraction (which only worked because `lo` happens to be
     1) is gone from the script layer entirely — the engine
     knows the menu's bounds and computes the slot itself.

State at HEAD:
  - make check + make testasan green at `051557d`.
  - port-plan-complete.md unchanged (no plan section closed).
  - port-plan.md (45-line master TOC) unchanged.
  - port-plan-incomplete.md: this section appended.

==== CONTINUATION PROMPT (paste verbatim next session) ====

Read port-plan-incomplete.md's "Session 2026-04-26 #2" section
above this prompt. Nine commits since the last continuation
prompt — see the list there for context. The §23 lookahead in
§22.14 / §23.B is still relevant for the queued work.

Recommended sequencing for next session(s):

  1. **§23.B — battle.c item-spell switch** (~2 sessions,
     biggest §23 piece remaining). cases 100..111 at lines
     194-403, ~210 LOC. Survey was done early in the previous
     session and recorded informally in conversation; key
     groupings:
       - Group A (narration-only): 102 (gem of confusion),
         103 (gem of blindness), 111 (flute of madness).
         Easiest. Validates the dispatcher infrastructure
         with no RNG complications.
       - Group B (first-alive-monster): 100 (frost wand),
         104 (drums of death). Need a `pick_first_alive_
         monster` engine_int.
       - Group C (AOE foes): 101 (fire wand), 109 (kael's
         axe), 110 (holy handgrenade). Use the existing
         `for_each_alive_monster` op.
       - Group D (target-prompt): 105 (staff of healing),
         108 (gem of resurrection). Need a digit-key prompt
         that binds target_idx — the new `digit_range +
         index_var:` shape from `051557d` covers this; reuse
         it.
       - Group E (party-wide): 106 (blessing scroll),
         107 (staff of curing). 106 needs a new
         `for_each_member` op (alive+dead, distinct from
         `for_each_alive_player`); 107 can use the existing
         alive iterator.
     Data shape: each `data/world/items/magic_items.json5`
     entry grows an `effect:` body (the `flute_of_madness`
     entry already has a placeholder). Lookup helper:
     `res_magic_item_effect_node(int oc)` parallel to
     `res_monster_spell_node`. Dispatcher: keep inv-letter
     read in C, look up `oc`, call `play_spell_body(...)`.
     Inventory consumption pattern: most cases consume up-
     front; case 108 only on success — handle in C around
     the play_spell_body call. RNG identity preserved per
     case (only 100/101/109 draw RNG).

  2. **§24.A1 — bulk fprintf-to-LOG_* migration** (~1 session,
     mechanical). ~75 callsites: res.c x44, driv.c x36,
     snapshot.c x28, party.c x10, json5.c x5, primitives.c
     x2, ryl.c x3, inventory.c x3. None fire under existing
     fixtures (zero stderr bytes during fixture playback), so
     bless-free in expectation. Author rule: LOG_E for
     "couldn't proceed, returned default"; LOG_W for "shape
     was wrong, falling back". Either before or after §23.B
     — both orderings fine.

  3. **§23.C / §23.D in parallel** (~1 session each,
     mechanical). Combat-constants sweep + driv.c
     char-creation thresholds. Small.

  4. **§24.B** (~1 session). File + null sinks behind
     `RYL_LOG=file:…` / `RYL_LOG=null`. Replay harness
     defaults to `RYL_LOG=null` after this lands; re-bless
     drops residual stderr noise.

  5. **§23.F + §23.E** (closeouts). ydarg stairs (already
     partly done in `e61be95`) + bestiary special-encounter
     survey (likely closes as documentation).

  6. **Later: §24.C** (TCP listener). **§25** (1-indexed
     cleanup, low priority). `plans/future-directions.md`
     multi-party MUD work — only after §22 / §23 / §24 are
     fully closed.

Pre-flight:
  - `make check` + `make testasan` green at HEAD (`051557d`).
  - 9 commits this session on `lagergren/port1`. All pushed
    to origin.
  - The §22.5 spell fixtures + §23.A combat-monster-magic
    fixture pin the player-side combat path byte-for-byte.
    Frost and Confusion are not byte-pinned by any current
    fixture — the dispatcher coverage they get is structural.
  - §24.A0 default threshold is WARN. `RYL_LOG_LEVEL=info`
    shows the realm-realization trace. AUDIT bypasses
    threshold (writes to `data/rylween.log`).
  - `tx0`/`tx` now re-emit `\r ESC[<x>C` per line. New code
    that emits multi-line text into the right text window
    just works; no need to split records. The earlier B-side
    (`24ea66e`) per-line driv.menu records still work — they
    could be merged back to multi-line shape but no
    incentive to.
  - `digit_range` now supports `index_var:` (`051557d`).
    Future menu_branch sites that read a 1..N digit pick can
    use that shape directly — no `key_var` indirection, no
    `let` arithmetic. Pattern:
        { digit_range: { lo: 1, hi: <max> },
          index_var: '<name>', then: [...] }
  - Mac Terminal renders block-char-edge gaps weirdly
    (Apple's font glyph metrics for U+258x). Pure rendering
    cosmetic; ignore.
  - port-plan.md is the 45-line master TOC. New section work
    appends to port-plan-incomplete.md; close moves to
    port-plan-complete.md.
  - plans/future-directions.md is outside the port plan —
    multi-party MUD scoping, not on the critical path.

Bar per session: `make check` + `make testasan` both green
at every commit.

==== END CONTINUATION PROMPT ====

## Session 2026-04-26 #3 — §23.B closed

§23.B (battle.c item-spell switch, cases 100..111) is now
fully migrated to data. The legacy switch is gone; every
magic item carries an `effect:` DSL body in
`data/world/items/magic_items.json5` and battle.c's
Use-Item branch dispatches through `play_spell_body` only.
See port-plan-complete.md for the full closure detail.

Five group commits + one closeout commit on
`lagergren/port1`:

  1. `4fc6962` Group A — gem_of_confusion / gem_of_blindness
     / flute_of_madness (RNG-free narration). Validated the
     dispatcher infra (res_magic_item_effect_node + the
     per-item `effect:` body shape).
  2. `4e2eead` Group B — frost_wand / drums_of_death.
     pick_first_alive_monster engine_int.
  3. `f19b5f1` Group C — fire_wand / kaels_axe /
     holy_handgrenade. for_monster +
     for_each_alive_monster cover the AOE shapes.
  4. `2537f4a` Group D — staff_of_healing /
     gem_of_resurrection. Body-owned inventory consume
     (pa_set_inventory engine_call) so 108 can defer to
     its success branch; action_retry battle_flag bumps
     108-fail back to the action menu.
  5. `3768f2e` Group E + closeout — blessing_scroll /
     staff_of_curing. Switch removed; dispatcher now the
     only Use-Item path.

`make check` + `make testasan` green at each commit.

**Followups identified during the work:**

  - Survey for `id: <number>` patterns showed 205 hits
    across world.json5 / monsters / items / NPCs. All are
    load-bearing today (each id is the int the C state
    layer stores in pa_inventory / state.npc / state.cl).
    Eliminating them is a slug-keyed-state refactor —
    out of scope for §23, queue as §25+ if/when wanted.
  - Numeric `case <int>:` dispatches still appear in
    driv.c (race switch, lines 1540-1575), ydarg.c
    (scenario switches, multiple), speak.c (small).
    Same shape as §23.B was — keep the int storage,
    move the dispatch to data. Worth queueing as
    §23.G (driv race), §23.H (ydarg scenarios), etc.

**Remaining work in §23 / §24 / §25** is unchanged from
the §23.B-pre continuation prompt: §23.C (combat
constants), §23.D (driv char-creation thresholds),
§24.A1 (LOG_* migration), §24.B (file/null log sinks),
§23.F (ydarg stairs), §23.E (bestiary survey), §24.C
(TCP listener), §25 (1-indexed cleanup).

==== CONTINUATION PROMPT (paste verbatim next session) ====

Read port-plan-incomplete.md's "Session 2026-04-26 #3"
section above this prompt — the previous session closed
§23.B in five group commits + one closeout commit. Plan-
file move (the doc-only closure entry in
port-plan-complete.md) is already done.

Recommended sequencing for the next session(s):

  1. **§23.C / §23.D in parallel** (~1 session each,
     mechanical). Combat-constants sweep + driv.c
     char-creation thresholds. The shape is just lifting
     literals into res_combat_int / res_driv_int lookups;
     no new DSL surface needed.

  2. **§24.A1 — bulk fprintf-to-LOG_* migration**
     (~1 session, mechanical). ~75 callsites: res.c x44,
     driv.c x36, snapshot.c x28, party.c x10, json5.c x5,
     primitives.c x2, ryl.c x3, inventory.c x3. Author rule:
     LOG_E for "couldn't proceed, returned default";
     LOG_W for "shape was wrong, falling back".

  3. **§23.G / §23.H — race + ydarg-scenario dispatcher
     migrations** (~1 session each). Same shape as §23.B
     — `switch (n) { case 1: ...; case 2: ... }` becomes
     a data-driven dispatch with bodies in JSON5. driv.c
     race switch (1540-1575) + ydarg.c scenario switches
     are the targets; both keep the int-keyed state, just
     move the dispatch table out.

  4. **§24.B** (~1 session). File + null sinks behind
     `RYL_LOG=file:…` / `RYL_LOG=null`. Replay harness
     defaults to `RYL_LOG=null` after this lands;
     re-bless drops residual stderr noise.

  5. **§23.F + §23.E** (closeouts). ydarg stairs (already
     partly done) + bestiary special-encounter survey
     (likely closes as documentation).

  6. **Later: §24.C** (TCP listener). **§25** (1-indexed
     cleanup, low priority). `plans/future-directions.md`
     multi-party MUD work — only after §22-§24 are
     fully closed.

Pre-flight:
  - `make check` + `make testasan` green at HEAD
    (3768f2e or whatever the §23.B closeout commit was).
  - 6 commits this session on `lagergren/port1`, all
    pushed to origin.
  - The §22.5 spell fixtures + §23.A combat-monster-magic
    fixture pin the player-side combat path byte-for-byte.
    Group A/B/C/D/E magic-item bodies are not byte-pinned
    by any current fixture — the dispatcher coverage they
    get is structural. (A combat-magic-item fixture
    covering one or two of the items would be a low-cost
    addition.)
  - The per-item-effect dispatcher in battle.c is now
    the only Use-Item path. A bad oc with no effect body
    fires `ryl_die("magic_items: no effect body for id N")`
    — instructive for authoring errors but never reaches
    today (every item has a body).
  - Body-owned inventory consume: every magic-item body
    starts with `engine_call: 'pa_set_inventory', args: [
    { var: 'inv_slot' }, 0 ]`. gem_of_resurrection is the
    one exception that defers the consume to its success
    branch. New magic items must follow the same pattern.
  - script_combat_ctx.action_retry: a body sets it via
    `mutate { battle_flag: 'action_retry' } = 1` to bump
    battle.c back to the action menu without consuming
    a turn. Used by 108-fail today; available to any
    future body that needs the same shape.
  - port-plan.md is the 45-line master TOC. Section work
    appends to port-plan-incomplete.md; close moves to
    port-plan-complete.md.
  - plans/future-directions.md is outside the port plan —
    multi-party MUD scoping, not on the critical path.

Bar per session: `make check` + `make testasan` both green
at every commit.

==== END CONTINUATION PROMPT ====
