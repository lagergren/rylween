# Rylween Port Plan — Active and Reference Sections

This file collects every section of `port-plan.md` that is in-progress, planned, or pure reference / vision material. Completed lifts live in `port-plan-complete.md`; the cross-section index lives in the master `port-plan.md`. Section numbers and `§N` cross-references are preserved verbatim — sections appear in their original §-order regardless of which file they live in.

## Top of queue (status snapshot, 2026-04-28)

A pointer-only triage of every open item below. The bodies still describe each task in detail — this list is just for "what should we pick up next?". Builds on the 2026-04-27 #16 snapshot (§23.A closed end-to-end), plus the §17.A.4 slice C closure (dungeon.c dispatcher-loop UI lifted into `dungeon.loop.*` — twelve inline `tx()` calls collapse to six text records; the §17.1 plan-table 917 LOC vs. 175 LOC at HEAD continues the pattern of stale slice estimates) in 2026-04-28.

### Guiding principle (re-affirmed 2026-04-27 #12)

**Adding a town / NPC / item / etc. should be a pure `data/world.json5` edit.** The cap-driven heap-allocation pattern that step 3a (`state.npc`) and step 3b (`towns`) follow is the working shape: name the static cap in `state.h`, heap-allocate off the world.json5 record count at `res_load_world` time, free in `res_shutdown`. §12.3 (dynamic sizing from `world.json5 limits:`) is the umbrella section; new work that touches a still-static array should ride along under that principle rather than baking in a new fixed cap. Remaining static caps after §25.B: `state.p[26]`, `map[42][82]`, party-inventory `[17]`, `attack_today[26]`.

### Active — work is in flight or fully unblocked

- **§17.A.4 slice D — text extraction in subs.c** (§17). Pending since 2026-04-23. Slice A (missions.c) closed in 2026-04-27 #14 — only Block 5 (Thun gem-altar) remained as inline `tx("...")` and that lifted bytes-only into `missions.thun_temple.*`. Slice B (interact.c) closed empty in 2026-04-27 #15 — the file had slimmed from the §17.1-table 428 LOC to 69 lines. Slice C (dungeon.c) closed in 2026-04-28 — the §17.1-table 917 LOC was 175 lines at HEAD; twelve dispatcher-loop `tx()` calls lifted to six records under a new `dungeon.loop.*` namespace; `wilderness-cave-descent` byte-identical (no rebless). Slice D (subs.c, §17.1-table 1310 lines; overlaps §14.4 scorelist — sequence carefully so only one fixture rebless is needed) is the last open slice; expect the same stale-estimate pattern (audit at HEAD before sizing the work). Slice E (DSL `menu_branch` + `engine_call` ops) is conditional — only if D shows enough repetition. (Note: the `menu_branch` and `engine_call` ops referenced as "conditional" in §17.2 already exist in `data/world/scripts/missions.json5`; slice E in the original plan was to add them. The conditional decision now is whether ANY further DSL widening — e.g. an inventory-count guard — is needed to absorb residuals like the inline Block 5 control flow that slice A left in C.)
- **State-abstraction sweep** (§25.A.1 #6 footer; tracker: `feedback_state_abstraction.md`). Untouched callsites still reach into raw `state.p[state.cpl].FIELD` outside §25.A's blast radius. Current `state.p[cpl]` counts (post-§25.B step 2): driv.c=0, ydarg.c=0, subs.c=0, inventory.c=6, primitives.c=6 (abstraction home — fine), towne.c=2 (one lifted via `pa_first_level()` in §25.B step 2), missions.c=6. Cross-party `state.p[i].FIELD` access in subs.c::scorelist now routes through `mmax`-bound 0-indexed loops (no longer hardcoded `1..4`) but still reads `state.p[i].{exp,lv,kills,cl,name}` directly — a `pa_at(i)` style by-index helper is the next step. Per the broader survey: 17 .c files touch `state.*` directly today (battle/driv/dungeon/interact/inventory/missions/party/primitives/replay_knobs/res/ryl/script/snapshot/speak/subs/towne/wld); only party.c / state.h / res.c are the natural homes. Strategy: ride along when next touching one of these files; no speculative refactors. Each callsite's blast radius is small.
<!-- §23.A structure-extraction follow-up closed in 2026-04-27 #16 — see "Closed since the previous snapshot (2026-04-27 #15)" below. -->

### Mechanical residuals (small, ready when convenient)

- **driv.c::terminate visited-grid shift bug** (surfaced in 2026-04-27 #11 during the §25.B step 3a / C3 NPC-abstraction migration). The party-removal path in `driv.c` around line 619 has `for_each_npc(i) { for (j = 1; j < state.num_parties; j++) { npc_visited_set(n, x, npc_visited(n, x + 1)); } }` — the inner loop iterates `j` but the body never references `j`; it just rewrites `visited[x] = visited[x+1]` `num_parties-1` times (idempotent). Looks like the intended logic is `for (j = x; j < state.num_parties; j++) { npc_visited_set(n, j, npc_visited(n, j + 1)); }` — i.e. compact the visited grid down by one slot when party `x` is removed. Likely fixture-unreachable today (none of the 39 fixtures exercise terminate-realm-then-keep-playing); needs an audit pass + a one-line fix + a fixture if reachable. Migration commit `54e3e81` preserved the existing behavior verbatim.
- **towne.c `state.twn == 2` city/village fork** (surfaced in 2026-04-27 #12 during the §25.B step 3b/C3 axis flip). The city/village text-id pick at `towne.c::enter` is hardcoded to `state.twn == 2` (Wilor post-flip); ideally each town record carries its own settlement-class string-id (or a `class: 'city' | 'village'` enum) and the C lookup vanishes. Out of scope for §25.B; queues under §17.A.4 / §24bis text-extraction work.

### Deferred — work is queued but explicitly post-something

- **§24bis — `id: <int>` → slug-keyed state**. Deferred until post-§25.B (now unblocked — §25.B closed end-to-end in 2026-04-27 #13); bundled to avoid double-touching every state accessor. ~4-6 session block touching every C file.
- **§12.3 — dynamic sizing from world.json5 (`limits` subtree)**. Cap-driven heap-backed flex arrays for `struct party` / `struct ryl_state`. §12.3 has the sequence; §12.2 leak audit (Commit C) already done. Safe-but-large; gated only on appetite. Save-format license (user-granted 2026-04-22) lets `realm.dat` / `npc.dat` migrate to JSON5.
- **§12.1 — Dockerfile + GitHub CI**. Reproducible build for non-mac contributors. No code changes blocking it; ~1 session.
- **Ydarg as a regular mission** (§12.4 residual). Move end-game boss from bespoke `ydarg()` / `confrontation()` into a regular mission with trigger + item-flag conditions. Blocked on the DSL evaluator learning mission triggers + item-flag paths.

### Documented & deferred — won't land unless requirements change

- **§18 — ANSI art EAW / narrow-cell rendering**. Path A shipped: README terminal-compat note documents the macOS Terminal.app drift and recommends iTerm2 / Ghostty / Alacritty / Kitty / WezTerm. Path B (rewrite art to symmetric blocks) is an art-rewrite job; Path D (Sixel/Kitty graphics) is a port-quality bump. Neither scheduled.
- **§21 — reentrancy / `ryl_ctx`**. Per §22.12 verdict: "worth it only if a real multi-tenant use case materialises." None has. Treat §21 body as documentation only.
- **§6 — C99 vs C++17 subset decision**. Decision deferred indefinitely; the C99 path is working.
- **§22.E items 11 + 12 — engine registry unification + op-shape micro-inconsistencies**. Cosmetic; consciously skipped per §22.12.
- **§20.12 — unwired DSL hooks (`spawn_encounter`, `call`)**. eval.c retired, ops gone from the DSL surface entirely. Superseded; if a script ever needs `spawn_encounter` again, it'd be a new design.
- **§20.20 #1 / #3 / #4 — DSL ergonomics**. Class-id symbol args, `{path: '…'}` expr form, default-`{0}` speak op town arg — deferred until a flow with a clear win surfaces.

### Closed but body still in this file (move-out queue)

- **§22 — DSL unification**. Closed end-to-end per §22.14 (2026-04-26): all three lane-A stragglers (§22.5 #8a banish, §22.F speak_missions, §22.G docs/dsl.md) shipped, plus pre-§22 stragglers (Phase 5c driv.c/ydarg.c residuals, §20 TINY T3/T4 path literals, §22.10 dungeon_encounter slots). Body should move to `port-plan-complete.md` like §23 / §24 already did; leave a stub for the master TOC.
- **§25 — 1-indexed → 0-indexed cleanup**. Closed end-to-end per §25.B step 4 (2026-04-27 #13): all four axis flips landed (monster, party-member, town, NPC) plus the §25.A iterator-abstraction prep work. Per-step closure bodies live under "§25.B step closures" in `port-plan-complete.md`; the parent §25 body in this file is now reference material for the migration approach and can move alongside §22.

### Closed since the previous snapshot (2026-04-27 #16)

- **§17.A.4 slice C — dungeon.c text extraction** (single commit, `6f63350`). Like slices A and B, the §17.1 plan table sized this slice much larger than reality: 917 LOC in the table, 175 LOC at HEAD. Earlier §17 / §22 work had migrated all per-event encounter narration into `data/world/scripts/dungeon.json5` + `data/world/texts/dungeon.json5`, leaving only the dispatcher-loop UI in `dungeon.c::dungeon` to lift. Twelve inline `tx()` calls collapse to six text records under a new `dungeon.loop.*` namespace (`level_menu` / `enter_level` / `who_prompt` are `tx_fmt`'d with `{level}` / `{max}` bindings; `no_torches` / `at_max_depth` / `leave_cavern` are static `tx0(res_text)`). 39 fixtures byte-identical on both gates including `wilderness-cave-descent` (45445 bytes, the fixture that exercises the dispatcher-loop UI); no `.expect_snapshot` rebless. Body in `port-plan-complete.md` "§17.A.4 slice closures" section.

### Closed since the previous snapshot (2026-04-27 #15)

- **§23.A follow-up — monster-melee fallback arm lifted to DSL** (two commits, `4468739..b99de4e`). The §23.A residual closure (2026-04-27 #8) lifted the four melee tuning constants into `combat.monster_attack` but left the surrounding C control flow inline; this closure lifts the whole arm into a script DSL `do:` body parallel to the four spell bodies under `monster_spells.spells[]`. The plan's "two helpers needed" claim was stale: `for_each_alive_player` / `pick_random_alive_player` / `monster_hp` / `battle_flag` / `member_field` / `chance` / `obdamage` all shipped during §23.A's spell-DSL work; only a `{ combat_int: '<path>' }` expression op was missing (added as the parent commit, 17 lines). The lift drops two pre-existing C-level quirks per project policy (the `damage_div > 0` div-by-zero protect and the `pa_pl()` death-decrement guard); both are unreachable in practice. RNG identity preserved exactly; 39 fixtures byte-identical on both gates. **§23.A closed end-to-end.** Body in `port-plan-complete.md` "§23.A follow-up closure" section.

### Closed since the previous snapshot (2026-04-27 #14)

- **§17.A.4 slice B — interact.c text extraction (closed empty)**. The §17.1 table sized interact.c at 428 LOC; by the time slice B was picked up, the file was 69 lines because earlier §17/§22 work had migrated the drop/swap/HP/rest flows, the §12.4 residuals pass had moved the journal helpers in from driv.c, and §20 T1 had reduced `check_terrain` to a `terrain.transition` script wrapper. The two remaining `tx(...)` calls inside `read_journal()` are dynamic-format (bullet prefix on a stored journal entry) and a literal blank-line separator — both match the column-aligned/pass-through inline convention noted in the §20 L6 closure for battle.c. No source changes; no fixtures touched. Body in `port-plan-complete.md` "§17.A.4 slice closures" section. Closure shape mirrors §17.A.5 (driv.c/ydarg.c residuals — closed empty per §17 footer).

### Closed since the previous snapshot (2026-04-27 #13)

- **§17.A.4 slice A — missions.c text extraction**. Block 5 (Thun gem-altar) was the only inline residual; 15 of the 16 historical trigger blocks had migrated during §16.5/§22 mission-state-machine work. Five `tx("...")` runs lifted into `missions.thun_temple.{temple_arrival, altar_inscription, gem_prompt, no_gems, altar_opens}`; bundled `gem_prompt` absorbed the previous `common.yes` call + the `(L)eave` line, matching the menu-bundling convention. C control flow (Y/L key, `inv_count_in_range(39, 41)` guard, `dungeon(18, "thun_temple")` handoff) unchanged. Block 5 is fixture-unreachable (only Thun/bracelet refs in fixtures are unrelated rumour text + special-item inventory line); no rebless required. 39 fixtures byte-identical on both gates. Body in `port-plan-complete.md` "§17.A.4 slice closures" section.

### Closed since the previous snapshot (2026-04-27 #12)

- **§25.B step 4 — NPC axis flip** (single commit, `404b1e8`). Closes §25.B end-to-end. Body moved to `port-plan-complete.md` "§25.B step closures" section. 39 fixtures byte-identical on both gates with no `.expect_snapshot` rebless required.

### Closed since the previous snapshot (2026-04-27 #11)

- **§25.B step 3b — town axis flip + heap-alloc + enums.towne wilderness lift** (four commits, `bedd842..f66247a`). Body moved to `port-plan-complete.md` "§25.B step closures" section.

### Closed since the previous snapshot (2026-04-27 #10)

- **§25.B step 3a — NPC abstraction + wilderness-sentinel lift** (four commits, `0056881..d445b99`). Body moved to `port-plan-complete.md` "§25.B step closures" section.

### Closed since the previous snapshot (2026-04-27 #9)

- **§25.B step 1 — monster-slot axis** (`684b7e5`). Body moved to `port-plan-complete.md` "§25.B step closures" section.
- **§25.B step 2 — party-member axis** (`2ff078f`). Body moved to `port-plan-complete.md` "§25.B step closures" section.
- **§25.B input → slot LOG_D traces** (`4e4b6fa`). Body moved to `port-plan-complete.md` "§25.B step closures" section.

### Closed since the previous snapshot (2026-04-27 #8)

- **§12.4 mechanical residuals — all four shipped** (2026-04-27 #9, four commits on top of `6a6ee89`):
  - `.git-blame-ignore-revs` records the `33ffc52` clang-format reformat so blame skips it (`88b33b5`).
  - `title-menu-exit.env` gained `RYL_NO_DELAY=1`; replay drops ~15s → ~50ms, byte-identical (`22ad817`).
  - `read_journal()` + `journalf()` lifted from `driv.c` to `interact.c` (43-line drop in driv.c); `speak.c` swapped its `driv.h` include for `interact.h` since `journalf` was the only driv.h symbol it used. Text id `driv.journal.header` left as-is (renaming would touch `data/world/texts/` + fixtures with no payoff). (`8442d03`).
  - README §18.5 terminal-compat paragraph (Path A copy) (`4bcc0d9`).

### Closed since the previous snapshot (2026-04-27 #7)

- **§20 L6 — battle.c narration sweep**. ~30 hardcoded `tx("English")` sites in battle.c lifted to data/world/texts/battle.json5 via tx0(res_text(...)) + tx_fmt with {placeholder} bindings. Five tx() remain inline as pure pass-through `%s\r\n` (monster/caster name) or column-aligned table rows — matches the speak.json5 / towne.json5 / driv.json5 convention. (97e341e)
- **§23.A residual — monster-melee formula constants**. The four numeric constants in the melee-fallback arm (hit_bonus / damage_div / damage_spread_max / damage_min) lifted to combat.monster_attack in world.json5; reads symmetrically with combat.attack which §23.C already lifted for the player side. Branch structure stays in C pending a `for_each_alive_player` DSL helper. (2f1911b)
- **§20 LARGE residuals — verified DONE**. L3 (battle.c item-effect switch ids 100..111) is now `res_magic_item_effect_node` + `play_spell_body`; L5 (monster magic table at battle.c:625-740) was lifted with §23.A — the `g==5` block is the data-driven dispatcher. Both confirmed by reading battle.c at HEAD. L6 closed in this session.
- **§20 TINY T3/T4 / §22.10 / Mechanical residuals — verified DONE**. T3/T4 path literals routed through `res_data_path` (§22.14 commit 3271b14). §22.10 dungeon_encounter slots 10..19 collapsed (commit b258cb0). Makefile already carries `-MMD -MP` (line 8). ANSI SGR macros live in `src/ansi.h`. The §22.12 / 2026-04-27 #7 snapshot wasn't refreshed after §22.14 / §24 closures — those bullets had been silently superseded. Cleared in this snapshot.

### Vision / reference (always stays in this file)

- **§1, §8, §9** — what the code is, what'll bite you, end-state vision.
- **`plans/future-directions.md`** — multi-party / MUD / shared-realm scoping. Long-term direction; no scheduled work.

### Recommended next session

**§17.A.4 slice C closed** in 2026-04-28 (twelve inline `tx()` calls lifted into `dungeon.loop.*`; byte-identical, no rebless). **§23.A closed end-to-end** in 2026-04-27 #16. **§17.A.4 slice A closed** in 2026-04-27 #14, **slice B closed empty** in 2026-04-27 #15. **§25.B closed end-to-end** in 2026-04-27 #13. Pick from the remaining open items in increasing cost order:

  - **§17.A.4 slice D — subs.c text extraction** (last open §17.A.4 slice; §17.1 table sized at 1310 LOC but expect the same stale-estimate pattern as A/B/C). Overlaps §14.4 scorelist — the scorelist fixture (`title-create-party-5-exit` and others) re-blesses are sensitive, so audit subs.c at HEAD before estimating; if scorelist is the bulk of the residuals, sequence the §14.4 sort-of-indices rewrite alongside so only one fixture rebless is needed. ~1 session if the slice is mostly already migrated like the others; up to 2 if scorelist needs the rewrite folded in.
  - **State-abstraction sweep**: 17 modules currently reach into `state.*` directly. The natural homes are state.h / party.c / res.c / snapshot.c (formatter); migration debt is in inventory.c (~5), primitives.c (~7 — actually fine, it IS a state-abstraction layer), missions.c (MB_PLAYER_NAME), subs.c::scorelist (cross-party access — needs a `pa_at(int party_idx)` by-index helper first), speak.c (state.npc walks), towne.c (2 raw `state.p[cpl]`). Ride along; no speculative refactors. ~3-5 sessions.
  - **§24bis** — `id: <int>` → slug-keyed state, now unblocked. ~4-6 session block touching every C file.
  - Later: §12.3 (dynamic sizing from world.json5 — the remaining static caps `state.p[26]`, `map[42][82]`, party-inventory `[17]`, `attack_today[26]`), §12.1 (Dockerfile + CI), Ydarg-as-mission.

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

## 22. DSL unification plan (audit 2026-04-25) — CLOSED, body moved

Closed end-to-end 2026-04-26 (§22.14); full body lives in
`port-plan-complete.md`. Stub left here so the master TOC's §22
anchor still resolves.

## 24. Logging + remote console (audit 2026-04-26) — CLOSED, body moved

Closed end-to-end 2026-04-27 #5; full body lives in
`port-plan-complete.md`. Stub left here so the master TOC's §24
anchor still resolves.

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

## Session 2026-04-26 #3 (cont) — plan polish + temple-R fixture

Two follow-on commits after the §23.B closeout:

  1. `178ec7f` plan polish — port-plan-complete.md gets the
     §23.B closure write-up; port-plan-incomplete.md gets
     this session's log + new §23.G (driv race switch),
     §23.H (ydarg scenario switches), §24bis (`id:` →
     slug-keyed state, deferred); port-plan.md TOC adds
     the §24bis row.
  2. `b1cf2d9` fixture — `towne-thord-church-resurrect`.
     Covers the temple (R)aise dead path that was broken
     and fixed earlier in the session (089277e + b5cda77 +
     051557d); without this fixture those fixes could
     regress unnoticed. Uses RYL_FORCE_DEAD_SLOT=3 to
     pre-seed a dead member without a combat detour.
     Test count: 28 → 29 fixtures.

State at HEAD (`b1cf2d9`):
  - 8 commits on `lagergren/port1` since the prior push
    point, all green on `make check` + `make testasan`.
  - The full session list, in order:
      4fc6962 §23.B Group A
      4e2eead §23.B Group B
      f19b5f1 §23.B Group C
      2537f4a §23.B Group D
      3768f2e §23.B Group E + closeout
      178ec7f plan polish (§23.G/H queued, §24bis deferred)
      b1cf2d9 towne-thord-church-resurrect fixture
      (this commit) session #3 (cont) closure + continuation
  - Fixture bank: 29 total. magic-item dispatcher is
    structural-only; a combat-magic-item fixture is the
    obvious follow-on — see continuation prompt below.

==== CONTINUATION PROMPT (paste verbatim next session) ====

Read this section of port-plan-incomplete.md (Session
2026-04-26 #3 — both halves) for the full §23.B closure
context. Eight commits on `lagergren/port1` since the
prior session push. HEAD is `b1cf2d9` plus the doc-commit
that wrote this prompt. `make check` + `make testasan`
green at every step.

**Where things stand right now (state worth keeping in
mind before you start):**

  - **Magic-item dispatcher pattern is established and
    reusable.** Resolver returns (body, slug); body owns
    its side effects (inventory consume,
    state mutations); a battle_flag (`action_retry`) on
    script_combat_ctx lets a body bump the dispatcher
    back to the action menu without consuming a turn.
    Same shape applies to §23.G (driv race switch) and
    §23.H (ydarg scenarios); reuse it instead of growing
    new ad-hoc dispatchers. The slug-yielding
    `&slug_out` parameter on `res_magic_item_effect_node`
    is the convention to copy when authoring future
    int-keyed → data-keyed resolvers.

  - **Body authoring rule: prefer mutate over
    engine_call when the lvalue exists.** Magic-item
    bodies use `mutate { target: { member_field: { idx,
    field } }, op: 'add', value: 3 }` to express stat
    bumps; an engine_call helper would have been a
    needless duplicate of an existing path. The minimal
    new engine surface §23.B added (pa_set_inventory /
    pm_full_heal / pm_set_dead) is shape-driven —
    pa_set_inventory because the body needs to clear the
    triggering slot; pm_full_heal because it has a
    dead-check guard the DSL doesn't reach for cleanly;
    pm_set_dead because dead is a u8 with bit-flag
    semantics. New engine_calls should clear the same
    bar.

  - **Iterator semantics matter for RNG identity.** The
    three combat iterators are NOT interchangeable:
      - `for_monster { var: 'i' }` — loops 1..noc
        unconditionally; RNG draws inside fire on dead
        slots too. Used by fire_wand to preserve the
        legacy `for (i=1; i<=noc; i++) { spelldamage =
        ...; if (!deadc[i]) ... }` byte stream.
      - `for_each_alive_monster` — skips dead slots;
        RNG draws only on alive. Used by kaels_axe /
        holy_handgrenade. Body sees `monster_idx` as a
        fixed-name binding.
      - `for_each_alive_player` — symmetric for
        party-side. Body sees `target_idx`.
      - `for_member { var: 'm' }` — loops the full party
        range; alive AND dead. Used by blessing_scroll
        (for stat boosts that hit dead members too).
    Pick by RNG identity first, then by intent.

  - **Body-owned consume + action_retry are reusable.**
    Any new script body that wants a "consume on success
    only" or "retry the prompt" semantics has the
    primitives in place. action_retry is currently only
    set by gem_of_resurrection but the scaffolding is
    there for any future case (e.g. a misclick on a
    scroll target).

  - **Pre-existing bugs surface during migrations** —
    the rule is to fix them inline (matches the existing
    "feedback_preexisting_bug_fixes" memory). §23.B Group
    B fixed `tx("...KILLED!")` (no \r\n) by routing
    through the standard battle.killed shard. Watch for
    this pattern in §23.G/H.

  - **Outstanding cleanup (low priority):**
      - clangd flags `ryl.h` / `towne.h` as unused
        includes in res.c, primitives.c, driv.c. Not
        -Werror, so non-blocking, but a quiet sweep
        through #include-cleanup is worth ~30 minutes.
      - DSL `key_range: ['1','4']` literals in
        data/world/scripts/towne.json5 don't track
        res_party_member_max(); three or four sites.
        Lift if a prompt ever needs N != 4.
      - The §22 lane-A "inert pre-DSL placeholder"
        cleanup — flute_of_madness was the last known
        such; nothing else is currently flagged but
        worth a grep for `['seq',\s*\['say'` / `set_flag`
        legacy shapes if a future migration surfaces one.

**Recommended sequencing for the next session(s):**

  1. **Combat-magic-item fixture** (~0.5 session). Pick
     one of the §23.B-migrated items (frost_wand is the
     most distinctive — single-target damage with RNG)
     and author a fixture that triggers it in combat.
     Pattern: createparty → seed 42 → wilderness encounter
     forced via RYL_FORCE_ENCOUNTER → in-combat (U)se
     Item → letter for slot containing the wand. Pin the
     RNG-identity stream + the H1..H4 / monster-HP block
     in the snapshot.
     This is the only §23.B coverage gap — the
     dispatcher works (make check passes today) but no
     fixture proves the body produces the right byte
     stream.

  2. **§23.C / §23.D** (~1 session each, mechanical).
     Combat-constants sweep + driv.c char-creation
     thresholds. Lift literals into res_combat_int /
     res_driv_int. No new DSL.

  3. **§24.A1 — fprintf-to-LOG_***  (~1 session,
     mechanical). ~75 callsites; LOG_E for "couldn't
     proceed", LOG_W for "shape was wrong, fallback".

  4. **§23.G / §23.H** (~1 session each). Apply the
     §23.B dispatcher pattern to driv.c race switch
     (1540-1575) + ydarg.c scenario switches. Reuse
     `res_<X>_effect_node(int, &slug)` resolver shape
     and the body-owns-side-effects rule.

  5. **§24.B** file/null log sinks (~1 session); replay
     harness default to RYL_LOG=null after this.

  6. **§23.F + §23.E closeouts** — finish ydarg stairs
     (partly done in e61be95) and survey bestiary
     special-encounter switches.

  7. **Later:** §24.C (TCP listener), §25 (1-indexed),
     §24bis (id: → slug, post-§25), then
     plans/future-directions.md.

Pre-flight:
  - HEAD passes `make check` + `make testasan`.
  - Magic-item dispatcher in battle.c is the only Use-
    Item path; new items must author an `effect:` body
    in magic_items.json5 (no C-side case arms).
  - Inventory consume is body-owned: every magic-item
    body opens with `engine_call: 'pa_set_inventory',
    args: [{ var: 'inv_slot' }, 0]`. gem_of_resurrection
    defers the consume to its success branch — pattern
    for any future conditional consumer.
  - action_retry battle_flag: set via
    `mutate { battle_flag: 'action_retry' } = 1` to bump
    battle.c back to the action menu. Reusable shape.
  - 29 fixtures in the suite. New fixtures append to
    tools/replay/<name>.{env,in,expect,expect_snapshot};
    `tools/replay.sh <name> --bless` writes the baselines.
  - port-plan.md is the 45-line master TOC. New work
    appends to port-plan-incomplete.md; close moves to
    port-plan-complete.md.

Bar per session: `make check` + `make testasan` both
green at every commit. Push at session close.

==== END CONTINUATION PROMPT ====

## Session 2026-04-26 #5 — §23 swept end-to-end

Walked the entire §23 queue in one session. Closures live in
`port-plan-complete.md` under "§23 closures". Status:

  - §23.D, §23.C, §23.F, §23.I, §23.H — all migrated.
    Six commits on `lagergren/port1`:
      4960a34 §23.D driv char-creation thresholds
      e6282bf §23.C battle.c combat constants
      1dca812 §23.F ydarg stairs item-trade
      a6430bb §23.I tx_narrate + ydarg.confrontation
      b9d183a §23.H ydarg room dispatcher leftovers
      e480e05 §23.J knobs (RYL_FORCE_YDARG_GRANTS +
              RYL_FORCE_YDARG_EASY_ROOMS)
  - §23.G — closed as planning artifact (no race switch
    exists in the codebase; plan conflated it with the
    class switch closed by §23.D).
  - §23.E — closed as documentation (UI dispatchers, not
    data targets, per the original §23.E plan note).
  - §23.J — knobs landed, fixture authoring deferred.

State at HEAD:
  - 6 commits on `lagergren/port1` since the prior push
    (b3d52b4); each green on `make check` + `make testasan`.
  - 29 fixtures total. No new fixtures this session.
  - `combat.attack.*` / `combat.battle_cry.*` /
    `combat.defend.*` / `combat.run.*` subtrees added;
    `enums.pc_class.<key>.min_stat` records added; `driv.*`
    top-level subtree added; `ydarg.{stairs,narration,
    pitfall,room}` subtrees added.

**Remaining §23 work is one item only:** the §23.J fixture
itself (ydarg-confrontation). Knobs `RYL_FORCE_YDARG_GRANTS`
+ `RYL_FORCE_YDARG_EASY_ROOMS` are wired and tested. The
fixture needs:

  1. RYL_FORCE_YDARG_GRANTS=1, RYL_FORCE_YDARG_EASY_ROOMS=1,
     RYL_FORCE_PARTY_LV=28, RYL_FORCE_POS=14,30 in env.
  2. Title sequence (party creation, 4 chars, all class 2
     thief — no min-stat gate, fastest).
  3. Wilderness E to enter castle.
  4. 5 ryl_pauses (approach, jaara_form, jaara_speech,
     key_accepted, enter), then the door-slam delay (no
     input), then the post-room-init pause.
  5. Per floor (×4): 8 moves to (5,5) (4× '6' east + 4×
     '2' south); each move triggers a no-op room body
     (case 4) + nav prompt. Then on stairs: G + digit
     (1..4 in floor order) + ryl_pause for mage_grateful.
  6. Confrontation: 2 ryl_pauses (act1), then 4-monster
     encounter sequence (~10 attack rounds each at LV=28),
     7 ryl_pauses (act2), baron_ydarg fight (~10 rounds),
     5 ryl_pauses (act3), 2 ryl_pauses (act4),
     cleanexit().

Estimated keystroke count: ~500 (the four pre-confrontation
monsters + baron_ydarg dominate). Fragile to RNG shifts in
combat (per-round damage rolls). The easy-rooms knob covers
ydarg's per-tile RNG; combat RNG is still an open exposure.

**Recommended next session order:**

  1. **§23.J fixture authoring + bless** (~1 sess). The
     only remaining §23 piece. Author with `--bless` after
     each act block to localize the diff if the keystroke
     stream is wrong. If a combat fight goes too many
     rounds and breaches the keystroke budget, consider
     RYL_FORCE_PARTY_LV=28 stacked with RYL_FORCE_GOLD or
     a hypothetical RYL_FORCE_WEAPON_DAMAGE knob — but
     defer those if not strictly needed.
  2. **§24.A1 — fprintf-to-LOG_*** (~1 sess, mechanical).
     ~75 callsites; LOG_E for "couldn't proceed", LOG_W
     for "shape was wrong, fallback".
  3. **§24.B** file/null log sinks (~1 sess); replay
     harness default to RYL_LOG=null after this.
  4. **Later:** §24.C (TCP listener), §25 (1-indexed),
     §24bis (id: → slug, post-§25), then
     plans/future-directions.md.

Bar per session: `make check` + `make testasan` both
green at every commit.

## Session 2026-04-27 #2 — §24.A1 closed

Bulk-migrated the `fprintf(stderr, …)` panic+warning sites to
the `LOG_W` / `LOG_E` API landed in §24.A0. 72 callsites flipped
across 7 files in 6 commits on `lagergren/port1`:

  - 1c64859 §24.A1: res.c (43)
  - ceccffa §24.A1: party.c (10)
  - 02a75a9 §24.A1: driv.c (7)
  - 4d04c02 §24.A1: json5.c (5)
  - acae7e5 §24.A1: snapshot.c (3)
  - a0129e6 §24.A1: inventory.c (3) + primitives.c::ryl_die (1)

All but one site was a panic followed by `exit(1)` and went to
LOG_E; the snapshot.c fopen-failure recovers (returns) and went
to LOG_W. Module names are file stems (`res`, `party`, `driv`,
`json5`, `snapshot`, `inventory`) — except primitives.c::ryl_die
kept module `ryl` to match its public-API "ryl: <msg>" prefix
contract that callers document.

Three remaining stderr writes in src/ are intentionally not
migrated:

  - **ryl.c x3** — the `--check-world` "world: ok" success
    line and the two argv usage messages. CLI output, not
    diagnostics; the LOG_W/LOG_E severity rule does not apply.
    Migrating them would either (a) silence "world: ok" under
    the default WARN threshold, breaking the visible CI signal,
    or (b) add `[INF][ryl]` prefix noise to a usage message.
  - **log.c:97** — the `LOG_*` emitter implementation itself.

No fixture drift. The 30-fixture replay set never exercises any
of the migrated paths — they're "world failed to load" / "scripts
contain a typo" / "save flag tag unknown" panics that a clean
fixture run never hits. `make check` + `make testasan` green at
every commit.

The redundant `"res: "` / `"snapshot: "` / `"inventory: "` etc.
file-tag prefixes inside the message bodies were dropped because
the new `[ERR][module]` prefix carries that information. Function
names like `res_formula_int:` / `pa_flag_set:` / `party_journal_add:`
were kept inside the message body because they identify the
specific call site, which the module tag alone cannot.

State at HEAD (a0129e6):
  - 30 fixtures green, both `make check` and `make testasan`
    clean. §23 fully closed; §24.A0 + §24.A1 closed. No
    open §23 / §24.A items.

**Recommended next session order:**

  1. **§24.B — file + null log sinks** (~1 sess). Add
     `RYL_LOG=file:<path>` and `RYL_LOG=null` parsers in
     `src/log.c`; default sink stays stderr. Once this lands,
     the replay harness can default to `RYL_LOG=null` per
     fixture so any future LOG_* drift doesn't propagate
     into baselines unless explicitly captured. Also adds
     a hook for §24.C (TCP) and §24.E (per-fixture opt-in).
  2. **§25 — 1-indexed → 0-indexed cleanup** (multi-sess).
     The §24 logging is now in place to surface noisy
     bless waves; §25 can land in measured slices.
  3. **Later:** §24.C (TCP listener — telnet-friendly remote
     log), §24bis (id: → slug, post-§25), then
     plans/future-directions.md (multi-party MUD work).

**Pre-flight notes for §24.B:**

  - The env-var parser slot is `RYL_LOG=...` (not the existing
    `RYL_LOG_LEVEL=...` which is for threshold). `RYL_LOG=null`
    drops everything; `RYL_LOG=file:<path>` writes
    [lvl][mod] body lines to the named file (resolve relative
    to `ryl_state_path` so the audit/log directory convention
    holds).
  - AUDIT writes (`LOG_A`) still go to `data/rylween.log`
    regardless of `RYL_LOG=…`. The audit file is "history,
    not diagnostic" per the §24.A0 docstring; its sink is
    fixed.
  - `RYL_LOG_LEVEL=…` still gates DEBUG/INFO. The two env
    vars compose: `RYL_LOG_LEVEL=info RYL_LOG=null` yields a
    silent-to-stderr but-still-info-threshold run, useful
    when capturing AUDIT to file without stderr spam.

Bar per session: `make check` + `make testasan` both green
at every commit.

## Session 2026-04-27 — §23.J fixture authored (loss path)

Authored the §23.J ydarg-confrontation fixture and added
the two remaining replay-only knobs needed for it. §23.J
is now closed end-to-end (closure note moved to
`port-plan-complete.md`). Two commits on `lagergren/port1`:

  - 78e12ac §23.J prep #2: RYL_FORCE_PARTY_WP +
    RYL_FORCE_PARTY_HP_FULL
  - 757e3dd §23.J: ydarg-confrontation fixture (loss path
    through fight #2)

`RYL_FORCE_PARTY_WP=<slug>` resolves the slug through
`res_item_id` and sets every member's wp; combined with
`RYL_FORCE_PARTY_HP_FULL=1` (hp = h after the lv bump),
a default-roll thief party at lv 28 deals ~65 dmg per
hit instead of the obdamage(0)-only ~7 dmg the lv-1-hp
path would give. Both knobs no-op when unset, so the
combat-mage-death / combat-monster-magic /
wilderness-priest-banish / wilderness-priest-rebirth
fixtures (which use RYL_FORCE_PARTY_LV without these
two) remain byte-identical.

The bless captured a loss path during fight #2 — the Night
Gaunt's Cloud of Fire AOE deals h2/5 + random(0,4) damage
(~90 dmg per char with the scaled h2=434), which one-shots
three of the four chars after the party kills the first of
two gaunts. Per the §23.J authoring note ("pin whichever
path the bless captures") the loss path is the fixture; a
follow-up could either stratify (D)efend rounds in to keep
hp above the 90-dmg threshold or add an explicit
`RYL_FORCE_NO_MONSTER_MAGIC=1` knob to suppress the
spell-cast roll entirely. Neither is needed for §23.J
itself — the loss path covers act_1 narration + crypt_horror
victory + monster-magic dispatch + losegame() narration,
which is enough to verify every §23.I tx_narrate call site
plus the §23.A monster nasty-magic dispatcher's spell-pick
draw.

State at HEAD (757e3dd):
  - 30 fixtures total (`ydarg-confrontation` is the new
    one). Both `make check` and `make testasan` green.
  - All §23 sub-items closed except for the conf_act2/3/4
    + baron_ydarg coverage extension above (optional;
    no §23 code is unverified, only the data is).

**Recommended next session order:**

  1. **§24.A1 — fprintf-to-LOG_*** (~1 sess, mechanical).
     ~75 callsites; LOG_E for "couldn't proceed", LOG_W
     for "shape was wrong, fallback".
  2. **§24.B** file/null log sinks (~1 sess); replay
     harness default to RYL_LOG=null after this.
  3. **Later:** §24.C (TCP listener), §25 (1-indexed),
     §24bis (id: → slug, post-§25), then
     plans/future-directions.md.

Optional follow-up: extend ydarg-confrontation to a
victory path (defend-driven survival or no-magic knob)
to pin act_2/3/4 narration + the heroes-file write. Not
load-bearing for any open §23 work; queue it if the
plain-text-pin coverage matters for later refactors of
ydarg.c::confrontation.

Bar per session: `make check` + `make testasan` both
green at every commit.

## Session 2026-04-27 #3 — §24.B closed

Added the `RYL_LOG=` sink selector to `src/log.c` alongside the
existing `RYL_LOG_LEVEL=` threshold knob. Three values land in
this commit; a fourth (`tcp:`) is queued as §24.C.

Recognized values (case-insensitive):
  - unset / `RYL_LOG=stderr` — stderr (the historical default)
  - `RYL_LOG=null` — drop every WARN/ERR/INFO/DEBUG line
  - `RYL_LOG=file:<path>` — append `[lvl][mod] body\n` to
    `<path>` resolved through `ryl_state_path` (so a bare leaf
    lands under `$HOME/.rylween/` or `data/`, mirroring how
    AUDIT resolves `rylween.log`)

AUDIT (LOG_A) writes are unchanged — they still go to
`data/rylween.log` regardless of `RYL_LOG=…`. Per the §24.A0
docstring, AUDIT is "history, not diagnostic" and its sink is
fixed. Only the WARN/ERR/INFO/DEBUG branch (the `fprintf` at
the bottom of `log_msg`) routes through the new dispatch.

Implementation notes:
  - The selector is parsed once on first emit and cached in a
    static `cached`, mirroring the existing `threshold()`
    helper. No per-line getenv hits.
  - The file: sink opens lazily with `fopen(path, "a")` and
    registers `atexit(sink_file_close)` once. On open failure
    we emit a one-shot `[ERR][log]` to stderr and degrade to
    NULL for the rest of the run (silent rather than infinite
    error spam).
  - An unrecognized value (e.g. `RYL_LOG=garbage` or the empty
    `file:`) emits a one-shot `[ERR][log] unrecognized RYL_LOG=…`
    diagnostic and falls back to STDERR — strict-but-helpful.
  - The `RYL_LOG=` parser is intentionally tiny — `strncasecmp`
    on a 5-char prefix. §24.C `tcp:host:port` will follow the
    same shape.

Smoke-tested (one-off rig built against `log.c` + `compat.c`)
across all five branches: default, `null`, `stderr`,
`file:smoke.log`, and the two error-path values. Each behaved
as documented; AUDIT continued writing through every variant.

State at HEAD:
  - 30 fixtures green, both `make check` and `make testasan`
    clean. §23 fully closed; §24.A0 + §24.A1 + §24.B closed.
    No open §23 / §24.A / §24.B items.
  - The replay harness still uses the default stderr sink.
    §24.E (per-fixture `RYL_LOG=null` opt-in) is next; it can
    land in a small follow-up commit independent of any
    further sink work.

**Recommended next session order:**

  1. **§24.E — replay-harness default `RYL_LOG=null`** (~0.5
     sess). Wire `tools/replay.sh` (and the per-fixture
     wrappers) to set `RYL_LOG=null` for each fixture so
     future LOG_* drift can't propagate into baselines unless
     a fixture explicitly captures it. Trivial; the
     `RYL_LOG_LEVEL` setup in those scripts is the same
     pattern.
  2. **§24.C — TCP listener sink** (~1 sess). Add
     `RYL_LOG=tcp:<host>:<port>`; same dispatch shape as
     §24.B. Telnet-friendly remote log for the eventual
     multi-party MUD work.
  3. **§25 — 1-indexed → 0-indexed cleanup** (multi-sess).
  4. **Later:** §24bis (id: → slug, post-§25), then
     plans/future-directions.md.

Bar per session: `make check` + `make testasan` both green
at every commit. Push at session close.

## Session 2026-04-27 #4 — §24.E closed

Defaulted `RYL_LOG=null` in the per-fixture subshell of both
replay harnesses (`tools/replay.sh` + `tools/replay_asan.sh`)
so any future LOG_W/LOG_E drift cannot propagate into the
baselines without a fixture explicitly opting in.

Shape:
  - The subshell sets `RYL_LOG=null` first under `set -a`,
    then sources the optional `<name>.env`, then runs the
    binary. Bash semantics → a `RYL_LOG=…` line in `.env`
    overrides the default; no `.env` (or one that omits
    `RYL_LOG`) keeps `null`.
  - This composes with the existing replay knobs
    (`RYL_FAKE_EPOCH`, `RYL_FAKE_SEED`, `RYL_FORCE_*`,
    `RYL_NO_DELAY`, `RYL_PARTY_MEMBER_MAX`) which are sourced
    the same way.
  - AUDIT (LOG_A) is unaffected — it still writes to
    `data/rylween.log` regardless of `RYL_LOG`. Each fixture
    runs under a fresh `HOME=$(mktemp -d)` so the audit log
    lands under `$home/.rylween/rylween.log` and is cleaned
    up by the existing trap; nothing leaks across runs.

The Makefile gate is unchanged on purpose — `make replay`
(and `make check`) still default `RYL_LOG=null` only via the
harness, so an interactive `./rylween` invocation outside the
fixture path keeps the historical stderr behavior. A fixture
that wants to capture stderr again only has to add
`RYL_LOG=stderr` to its `.env`.

No fixture re-bless was needed: none of the migrated
LOG_W/LOG_E paths fire under the 39-fixture replay set today,
so the baselines were already noise-free. Verified by
re-running `make check` (39 fixtures + snapshot diffs green)
and `make testasan` (39 fixtures, no AddressSanitizer/
LeakSanitizer markers).

State at HEAD:
  - 39 fixtures green, both `make check` and `make testasan`
    clean. §23 fully closed; §24.A0 + §24.A1 + §24.B + §24.E
    closed. No open §23 / §24.A / §24.B / §24.E items.
  - Logging plan remaining: §24.C (TCP listener sink) and the
    optional §24.D (syslog).

**Recommended next session order:**

  1. **§24.C — TCP listener sink** (~1 sess). Add
     `RYL_LOG=tcp:<host>:<port>`; same dispatch shape as
     §24.B. Telnet-friendly remote log for the eventual
     multi-party MUD work.
  2. **§25 — 1-indexed → 0-indexed cleanup** (multi-sess).
  3. **Later:** §24bis (id: → slug, post-§25), then
     plans/future-directions.md.

Bar per session: `make check` + `make testasan` both green
at every commit. Push at session close.

## Session 2026-04-27 #5 — §24.C closed (+ multi-sink fan-out)

Added the TCP listener sink to `src/log.c` and, in the same
session, generalised `RYL_LOG` from a single sink into a
comma-separated list so every enabled sink receives every
WARN/ERR/INFO/DEBUG line. Both pieces shipped together because
the multi-sink case is the only one where the rcon listener is
genuinely useful (you almost always also want the lines on
disk).

Shape:
  - `RYL_LOG=tcp:[host:]port` accepts `tcp:host:port`,
    `tcp::port`, and `tcp:port`. The last two default the host
    to `127.0.0.1`. IPv6 literals (bracket form) intentionally
    not parsed — out of scope for the rcon use-case; pass an
    IPv4 address or `0.0.0.0`.
  - Default bind is loopback. `tcp:0.0.0.0:<port>` is the
    explicit opt-out for "expose on every interface". No
    authentication; treat as a development diagnostic, not an
    operations-grade log shipper.
  - Up to 8 simultaneous clients. Pending `accept`s drained
    lazily inside `log_msg`'s emit branch (no separate event
    loop). A 9th connection while the slot table is full is
    accepted and immediately closed, so a runaway connect loop
    can't exhaust fds.
  - Loss policy on slow readers: `EAGAIN` / `EWOULDBLOCK` →
    drop the line for that one client; `EPIPE` / other send
    error / short write → close the fd and free its slot. The
    engine never blocks waiting for a reader. SIGPIPE dodged
    via `MSG_NOSIGNAL` on Linux + `SO_NOSIGPIPE` per accepted
    socket on macOS/BSD; no process-wide signal override.
  - `atexit(sink_tcp_close)` closes the listener fd plus every
    client fd on normal exit. `SO_REUSEADDR` set on the
    listener so a quick restart after `^C` doesn't trip
    TIME_WAIT.
  - Multi-sink list parser: comma-separated, tolerates stray
    commas, per-token max 255 chars. `null` is a no-op token —
    if it's the only entry, the effective mask is empty and
    the engine drops every diag line (preserving pre-existing
    `RYL_LOG=null` semantics). `file:` and `tcp:` are
    single-instance per process; a duplicate emits a one-shot
    `[ERR][log] …; ignored` and leaves the first instance
    alone. Open errors degrade only the failing token, the
    rest of the list still applies. Unknown tokens add the
    stderr bit so the misconfig is visible.
  - The fan-out point is `log_msg`: the line is `snprintf`-ed
    once into a 1100-byte buffer and teed in stderr → file →
    tcp order. AUDIT (`LOG_A`) is unchanged — still routed
    unconditionally to `data/rylween.log`, untouched by
    `RYL_LOG`.
  - New file: `docs/log.md` documents severity / threshold,
    the four sink tokens, bind discipline, on-wire format,
    loss policy, and `telnet localhost <port>` usage.

Smoke-tested end-to-end with
`RYL_LOG=stderr,file:smoke.log,tcp:<port>` running the
`title-create-party-5-exit` fixture: stderr + file each saw
the same 25 LOG_I lines clean; an `nc 127.0.0.1 <port>`
client captured the 18 lines emitted *after* it connected
(lazy-accept by design — earlier lines went out before the
client attached). The client got byte-identical
`[INF][realm] …` lines to the file sink — confirming the
single-format-once / tee-many path works.

Fixture impact: zero. Every fixture pins `RYL_LOG=null` after
§24.E, so the new dispatch arm only fires on developer
opt-in. `make check` (39 fixtures + snapshot diffs) and
`make testasan` (39 fixtures, no AddressSanitizer /
LeakSanitizer markers) both green at the §24.C commit and
again after this plan-update commit.

State at HEAD:
  - 39 fixtures green, `make check` + `make testasan` clean.
    §23 fully closed; §24.A0 + §24.A1 + §24.B + §24.C +
    §24.E closed. No open §23 / §24.A-C / §24.E items.
  - Logging plan remaining: only the optional §24.D (syslog
    sink). Skip if no demand surfaces.

**Recommended next session order:**

  1. **§25 — 1-indexed → 0-indexed cleanup** (multi-sess).
  2. **Later:** §24bis (id: → slug, post-§25), then
     plans/future-directions.md.

Bar per session: `make check` + `make testasan` both green
at every commit. Push at session close.

## Session 2026-04-27 #5 — §24.C + §24.F + §24.D closed (§24 done)

Three logging-plan items landed in one session, fully closing §24
ahead of the "optional D" estimate. Order:
  1. **§24.C** — TCP listener sink + `RYL_LOG` generalised to a
     comma-separated multi-sink list. (See the §24.C session note
     above for the full shape.)
  2. **§24.F** — `runtime.json5` config layer driving the always-on
     `RYL_*` knobs through a new `rcfg_get(name)` helper with
     env-overrides-file priority.
  3. **§24.D** — `RYL_LOG=syslog[:<ident>]` sink with a
     level → syslog-priority mapping.

Why bundled: §24.C's multi-sink fan-out is the load-bearing change;
once tee-to-many works, §24.D (one extra arm) and §24.F (one
indirection layer in front of `getenv`) are each tiny additions.
Splitting them into separate sessions would have meant three rounds
of "rebuild rylween-asan + replay all 39 fixtures + push" for what
amounts to one logical change to the logging surface.

Shape of §24.F (the only piece that touched callsites outside
`log.c`):
  - New `src/runtime_cfg.{c,h}`. Single public function
    `rcfg_get(name)` with this priority: env var (non-empty wins)
    → `$HOME/.rylween/runtime.json5` → `data/runtime.json5`
    (optional, absent from repo) → caller's hard-coded fallback.
  - Cache is a tiny first-writer-wins string table (`RCFG_MAX = 64`
    entries). Personal config loaded first, project-default loaded
    second — keys already in the table are skipped, preserving the
    priority order. Keys are `RYL_*` names; values are JSON5
    scalars (string / int / bool — ints/bools stringified at load).
    Non-scalar values get a one-shot `[WRN][rcfg]` and are skipped.
  - `init_once()` is lazy on first call, registers
    `atexit(rcfg_close)` for LSan cleanliness.
  - `json5_parse_file` is fatal on syntax/IO errors via `res_die`
    — same policy as world.json5 — so a malformed config is loud,
    not silently ignored. `access(path, F_OK)` gates the call so a
    missing file is silently OK.
  - Routed through `rcfg_get`: `RYL_LOG` (`log.c`),
    `RYL_LOG_LEVEL` (`log.c`), `RYL_NO_DELAY` (`compat.c`),
    `RYL_FAKE_EPOCH` (`compat.c`), `RYL_FAKE_SEED` (`compat.c`),
    `RYL_PARTY_MEMBER_MAX` (`res.c`), `RYL_REPLAY_RUMOUR`
    (`towne.c`), `RYL_SNAPSHOT` (`snapshot.c`).
  - **Not** routed through `rcfg_get`: every knob in
    `replay_knobs.c` (`RYL_FORCE_*`, `RYL_STARTING_GOLD`,
    `RYL_FORCE_ENCOUNTER`, etc.). These `unsetenv()` themselves
    on first read and are by nature per-fixture; a personal-config
    default would have terrible-surprise semantics
    ("every run starts with RYL_FORCE_PARTY_LV=10"). If a knob
    graduates from fixture-only to general-purpose, migrate it
    out of `replay_knobs.c` first, then add it to the always-on
    bucket.

Smoke-tested:
  * §24.C end-to-end with `RYL_LOG=stderr,file:smoke.log,
    tcp:<port>` running the title-create-party-5-exit fixture:
    stderr + file each got the same 25 LOG_I lines clean; an
    `nc 127.0.0.1 <port>` client captured the 18 lines emitted
    after it connected (lazy-accept, by design).
  * §24.F with a personal `runtime.json5` setting
    `RYL_LOG: 'file:cfg.log'` + `RYL_LOG_LEVEL: 'info'` and no
    env vars → 25 LOG_I lines into cfg.log under a fresh
    `HOME=$(mktemp -d)`. Same config with env override
    `RYL_LOG=null` → 0 LOG lines anywhere (env beat the config,
    confirming the priority contract).
  * §24.D fan-out with `RYL_LOG=syslog:rylween-test,file:syslog_
    smoke.log`: file got 25 lines clean, stderr stayed silent,
    syslog calls fired (verified by the sink-mask: every line
    that reached the file also went through `syslog(3)`).

State at HEAD:
  - 39 fixtures green, `make check` + `make testasan` clean.
    §23 fully closed; **§24 fully closed** (A0 / A1 / B / C / D /
    E / F all landed).
  - Logging plan has nothing remaining.

**Recommended next session order:**

  1. **§25 — 1-indexed → 0-indexed cleanup** (multi-sess).
  2. **Later:** §24bis (`id: <int>` → slug, deferred until
     after §25), then plans/future-directions.md (multi-party
     MUD work).

Bar per session: `make check` + `make testasan` both green
at every commit. Push at session close.

## Session 2026-04-27 #6 — §25.A.1 (party-member iterator)

First slice of §25.A: introduce a `for_each_party_member(slot)`
macro in `src/party.h` and migrate every `for (i = 1; i <=
res_party_member_max(); i++)` callsite to use it. The body keeps
`slot` 1-based — no semantic change. The macro is the single
point of truth that §25.B's eventual flip to 0-indexed will edit
(plus a fixture re-bless) instead of revisiting two dozen call
sites across nine .c files.

Three commits:

  - `571e47e` — `§25.A.1: for_each_party_member iterator macro`.
    Macro definition (just `for (int slot = 1; slot <=
    res_party_member_max(); slot++)` — calling the cached static
    res_party_member_max getter once per iteration is trivially
    cheap, no need to hoist the bound). 25 callsites migrated:
    party.c x4 (pa_first_alive / pa_count_class /
    pa_count_alive_class / pa_alive_count), subs.c x5 (foodhandler
    mana regen + starve damage + three sleep arms — the outer
    `int32_t i` and `int mm` cache retired), battle.c x4 (post-
    combat exp grant + expcheck level-up tally x3), driv.c x6
    (the_game 'A' show-all + createparty zero-init x5 — the
    outer `int32_t i` retired), dungeon.c x1, ydarg.c x3,
    towne.c x2 (the `y = 0` initializer lifted out of the
    for-init expression), snapshot.c x1, replay_knobs.c x3.
    party.h was added to snapshot.c and replay_knobs.c.

  - `f813e2b` — `§25.A.1 follow-up: collapse same-range loops +
    state abstraction`. Two adjacent cleanups surfaced during code
    review of the iterator landing:
      (1) **Loop-merge.** createparty was running five sequential
          `for_each_party_member(i)` loops over the same 1..N range
          to zero/init `lv` / `ar` / `exp` / `dead` (with `lv = 1`
          written twice — a duplicate the original C had carried
          forward through every refactor). One merged loop now
          handles all four fields. battle.c expcheck dropped its
          zero-init loop in favor of `int32_t add[5] = {0};` —
          the second loop was the only writer. subs.c foodhandler
          factored the three identical sleep-deprivation hp-drain
          loops (-4 / -8 / < -8 hours_rested arms) into a single
          `sleep_deprivation_drain()` helper.
      (2) **State abstraction.** Routed every migrated loop body
          through `pm_*` / `pa_*` instead of reaching back into
          raw `state.p[a].<field>[i]`. createparty now uses
          pm_set_lv / pm_set_ar / pm_set_exp / pm_set_dead.
          replay_knobs.c rk_apply_party_lv / wp / hp_full /
          dead_slots all use pm_set_* / pm_h / pm_dead. towne.c
          enter_ready_prompt uses pm_dead / pm_wp / pm_ar.
          battle.c expcheck reads exp via pm_exp(pm) instead of
          pm.party->exp[pm.m]. The "state owned by party.c, accessed
          via pm_*/pa_* everywhere else" rule is now honored across
          every callsite §25.A.1 touched. Pre-existing raw-state
          callers in inventory.c / primitives.c / missions.c /
          subs.c::scorelist remain queued under the
          `feedback_state_abstraction` memory note — out of scope
          for this session's iterator-axis migration.

  - `8353f16` — `§25.A.1 follow-up: lift hardcoded 15 / 10 in
    driv.c::position_locations`. Same-axis cleanup at the
    constant-bound layer rather than the loop-variable layer. Two
    new public enums in `src/wld.h`: `WLD_LOCATION_SLOT_MAX = 15`
    (total slot count — replaces the zero-init bound and the four
    save/load array sizes) and `WLD_RANDOM_LOCATION_MAX = 9`
    (inclusive upper bound for randomized slots — replaces the
    `for (i = 1; i < 10; i++)` randomize loop). Wld.h's docstring
    now spells out which slots are randomized vs reserved so the
    gap between the two has an explicit semantic. wld.c's
    file-static `WLD_LOCATION_SLOTS = 15` retired in favor of
    the public enum.

State at HEAD (`8353f16`):

  - 39 fixtures green; `make check` + `make testasan` clean at
    every commit.
  - §25.A.1 closed: party-member axis is now centralised behind
    `for_each_party_member`. The four-axis sweep (party → monster
    → npc → town) now has 1 of 4 done.
  - State abstraction inside §25.A.1's blast radius is clean.
    The broader audit (inventory.c x5, primitives.c x7, missions.c
    a few, subs.c::scorelist with hardcoded `[1..4]` fanouts) is
    queued under the existing memory feedback note —
    not blocking §25.A's remaining axes.

**Recommended next session order:**

  1. **§25.A.2 — monster-slot iterator** (~1 session, mechanical).
     Same shape as §25.A.1 but for `for (i = 1; i < noc + 1; i++)`
     loops over `mh[i] / deadc[i]` in battle.c (5 sites) and
     script.c (1 site at line 2976) plus the matching `for_monster`
     DSL op family. Macro lives in battle.h or a new combat.h, takes
     a combat ctx (`for_each_monster(idx, ctx)`) so the body sees
     `idx` 1-based. battle.c::expcheck is unrelated — it iterates
     party members, already done. Smaller blast radius than §25.A.1
     (two .c files vs nine).
  2. **§25.A.3 — NPC-slot iterator** (~1 session). Loops over
     `state.npc[1..res_npc_slot_max()]` in driv.c (3 sites) and
     res.c. Slot 0 is unused. Macro `for_each_npc(slot)` in res.h
     or a new npc.h. Touch is concentrated.
  3. **§25.A.4 — town-slot iterator** (~1 session). The smallest
     axis: `for (i = 1; i <= n_towns; i++)` in res.c / interact.c.
     `towns[0]` is the JSON `null` sentinel left zeroed (the
     §24.A0 setup-logging off-by-one landed precisely because of
     this). Macro `for_each_town(slot)` plus the town-zero-sentinel
     comment.
  4. **§25.B — flip to 0-indexed** (~8-12 sessions). Only after all
     four §25.A axes have iterators in place. Lots of fixture re-
     blesses; each step is one fixture's worth at a time. The
     RYL_FAKE_SEED order has to stay identical, so any draw-loop
     that did `random(1, 4)` becomes `random(0, 3)` — value space
     same, semantic-of-the-number changes. Land as a single
     coherent wave, not a long-tail nibble.
  5. **Later:** §24bis (`id: <int>` → slug, post-§25), then
     `plans/future-directions.md` multi-party MUD work.

Bar per session: `make check` + `make testasan` both green at
every commit. Push at session close.

## Session 2026-04-27 #7 — §25.A.2 + §25.A.3 + §25.A.4 closed

The remaining three §25.A axes — monster slots, NPC slots, town
slots — landed in a single session. Each axis got its own iterator
macro mirroring §25.A.1's `for_each_party_member` shape, with the
slot-bound parameterized where it varies per call (monsters: encounter-
local `noc`) and parameterless where it's a global (npc/town: derived
from data). Per the §25.A.1 precedent, follow-up commits lifted same-
shape hardcoded literal bounds (speak.c `< 40`).

Four commits:

  - `7411209` — `§25.A.2: for_each_monster iterator macro`. Macro in
    src/battle.h takes the `noc` bound as a free arg (encounter-local,
    not a global). 7 callsites migrated: battle.c x5 (encounter mh/
    deadc init, 'A' show-list, alive-check accumulator, monster-nasty
    `while (x < noc + 1)` turn → `for_each_monster(x, noc)` with the
    macro-scoped int x shadowing the outer int32_t x per §25.A.1 — no
    -Wshadow; "still face" survivor count) and script.c x2 (ie_pick-
    _first_alive_monster's `<= noc` form, ask_target monster_alive
    drops a redundant int i). The DSL `for_monster` op family
    (do_for_monster / do_for_each_alive_monster / do_for_range_alive)
    already routes through generic helpers that take the bound — no
    migration needed there. Two `x = 0` accumulators lifted out of
    for-init position to standalone statements.

  - `28fd1d9` — `§25.A.3: for_each_npc iterator macro`. Macro in
    src/res.h next to the existing `res_npc_slot_max()` decl. 5
    callsites migrated: driv.c x3 (the_game's hut/cottage map seed,
    init's party-shift visited-grid migration, write_current_world's
    persistence writer); res.c x1 (realize_npcs's visited[] zero-out;
    the local `slot_max` cache stays — still needed by the random-
    placement bin draw and the `slot < 1 || slot > slot_max` guards);
    towne.c::hut x1 (NPC-at-tile probe; outer `int32_t i` and `int
    nmax = res_npc_slot_max()` retired together). The save/load
    formal-zero loops (writer 0..slot_max, reader capped at slot_max
    + 1) keep their explicit shape — those are file-format walks, not
    slot iters.

  - `e825dc2` — `§25.A.3 follow-up: lift speak.c hardcoded `< 40``.
    Same-shape literal-bound cleanup at the loop-variable layer
    (precedent: §25.A.1's commit 8353f16 lifting 15/10 in driv.c::-
    position_locations). speak.c::speak's two NPC-slot loops (the
    "list NPCs in a town building" picker and the wilderness "who's
    on this tile" probe) used `i < 40` — half of state.npc[]'s 41-
    slot capacity. Bound has been semantically equivalent to "every
    realized NPC" since §15 step 3 (slots beyond res_npc_slot_max()
    are zero-init by static and never realized; both loop predicates
    require non-zero towne/building or a non-zero party position to
    match). Replaced both with `for_each_npc(i)` — same semantics in
    today's data, single source of truth for the iteration bound.
    Three accumulator inits (`j = 1, x = 0` picker, `talk = 0`
    probe) lifted out of for-init; outer `int32_t i` retired.

  - `5dcbe74` — `§25.A.4: for_each_town iterator macro`. Macro in
    src/res.h plus a public `res_town_count()` getter (file-static
    `n_towns` now externally visible — required so the macro's
    expansion can access the bound from any caller). One callsite
    migrated: `res_town_at`'s linear scan, which was the last
    `for (int i = 1; i <= 4; i++)` over `towns[i]` in the codebase —
    the hardcoded `4` retired in favor of n_towns. The `twn > 4`
    guards on `res_town_name` / `_x` / `_y` / `_tax` / `_has_church`
    keep the hardcoded 4: those mirror the towns[5] static-array
    capacity, not the iteration bound, and lifting them requires
    expanding the realm past four towns (a structural change). Doc
    note clarifies the towns[0] = JSON-null = wilderness convention
    and the link back to the §24.A0 setup-logging off-by-one (which
    landed precisely because of the bin-0-is-wilderness invariant).

State at HEAD (`5dcbe74`):

  - 39 fixtures green; `make check` + `make testasan` clean at every
    commit.
  - §25.A closed end-to-end. All four iteration axes (party member /
    monster / npc / town) are now centralised behind their respective
    `for_each_*` macros — single point of truth that §25.B will edit
    when the codebase flips to 0-indexed.
  - State abstraction inside §25.A's blast radius is clean across
    every axis (party in §25.A.1, monsters touch only encounter-
    local arrays not state, NPCs route through `state.npc[i]` reads
    that already use struct-direct access, towns route through
    `towns[i]` which is the file-static state). The broader audit
    queued under `feedback_state_abstraction` (inventory.c x5,
    primitives.c x7, missions.c MB_PLAYER_NAME, subs.c::scorelist
    `[1..4]` fanouts, towne.c::enter_tax_preamble) remains the only
    pending state-abstraction work; not blocking §25.B.

**Recommended next session order:**

  1. **§25.B — flip to 0-indexed** (~8-12 sessions). Now unblocked.
     The macros at party.h::for_each_party_member, battle.h::for_each-
     _monster, res.h::for_each_npc, res.h::for_each_town are the four
     edit points; flipping each from `for (int slot = 1; slot <= N;
     slot++)` to `for (int slot = 0; slot < N; slot++)` plus a
     fixture re-bless wave is the bulk of the work. Constraints:
       - The RYL_FAKE_SEED order has to stay identical — any draw-loop
         that did `random(1, 4)` becomes `random(0, 3)`; value space
         same, semantic-of-the-number changes.
       - towns[0] / state.npc[0] / monster slot 0 / party slot 0
         interpretations all change — towns[0] becomes a real town
         (not the wilderness sentinel); the wilderness bin in
         res_realize_npcs has to use a separate sentinel value
         (-1 or a parallel flag) instead of overloading slot 0.
         res_town_at / res_npc_sym / npc_at_slot bounds shift.
       - Save/load fixtures: every fixture that emits npcs[].id /
         visited_npcs[i] / etc. needs a re-bless. Bench with the
         smallest fixture first (towne-thord-inn or similar) before
         the wave.
     Land as a single coherent wave (one PR or commit chain), not a
     long-tail nibble — the half-flipped state is hard to reason
     about and fixture re-blesses are wasted if a later step
     reverses something.

  2. **State-abstraction sweep** outside §25.A's blast radius. The
     `feedback_state_abstraction` memory note tracks this; the next
     time someone touches one of these files, route the new code
     through `pm_*` / `pa_*` / `inv_*` / `res_*` helpers and leave
     the untouched callsites alone (no speculative refactors).
     Survey: inventory.c x5, primitives.c x7, subs.c::scorelist
     (~10 raw `state.p[i].{exp,lv,kills,name}[1..4]` reads with
     hardcoded 1..4 fanouts — the §25.A.1 #6 footer's call-out),
     towne.c::enter_tax_preamble, missions.c MB_PLAYER_NAME binder.
     Add helpers as needed; don't grow a parallel `pa_*_at(int slot,
     ...)` family unless a test-infra caller forces it (replay-
     _knobs.c::rk_apply_pos / rk_apply_ydarg_grants are the only
     current candidates).

  3. **Later:** §24bis (`id: <int>` → slug, post-§25), then
     `plans/future-directions.md` multi-party MUD work.

Bar per session: `make check` + `make testasan` both green at every
commit. Push at session close.
