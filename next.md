# Continuation prompt — paste this into a fresh session

Eight commits pushed on `lagergren/port1`. HEAD `606c9ae`. 39 fixtures green on `make check` + `make testasan` at every commit.

## What landed this session

Three closures on top of `ba0c3d1`:

**§17.A.4 slice A — `b44732e..f4f7bb4`** (Block 5 / Thun gem-altar text-extraction). Slice landed much smaller than the §17.3 plan implied — 15 of the 16 historical mission-control trigger blocks had already migrated during §16.5/§22 work; only the gem-altar's `inv_count_in_range(39, 41) >= 5` guard kept it inline. Five `tx("...")` runs lifted into `missions.thun_temple.{temple_arrival, altar_inscription, gem_prompt, no_gems, altar_opens}`; bundled `gem_prompt` absorbed the previous `common.yes` call + the `(L)eave` line. Block 5 is fixture-unreachable. No rebless.

**§17.A.4 slice B — `a32cdc7`** (interact.c, closed empty). The §17.1-table 428 LOC had become 69 LOC by the time slice B was picked up. The two remaining `tx(...)` calls in `read_journal()` are dynamic-format bullet line + literal blank separator, both match the column-aligned/pass-through inline convention from the §20 L6 closure. No source changes; closure shape mirrors §17.A.5.

**§23.A follow-up — `4468739..fcc1dc8`** (monster-melee fallback arm lifted to DSL — closes §23.A end-to-end). The plan's "two helpers needed" claim was stale: `for_each_alive_player`, `pick_random_alive_player`, `monster_hp` / `battle_flag` / `member_field` / `chance` / `obdamage` all shipped during §23.A's spell-DSL work; only a `{ combat_int: '<path>' }` expression op was missing (17-line addition mirroring `obdamage` shape). The melee body lifted to `monster_spells.melee.do` (sibling of the spells: array). Two pre-existing C-level quirks dropped per project policy: the `damage_div > 0` div-by-zero protect (constant is 6 and won't change) and the `pa_pl()` death-decrement guard (outer loop already gates on `pa_alive_count() > 0`); both unreachable in practice. RNG identity preserved exactly. **§23.A closed end-to-end** — both numbers (combat.monster_attack subtree, 2026-04-27 #8) and structure (this commit) now in data.

**§17.A.4 slice C — `6f63350..606c9ae`** (dungeon.c dispatcher-loop UI). Pattern continues: §17.1 sized at 917 LOC, HEAD was 175 LOC. Twelve dispatcher-loop `tx()` calls collapse to six text records under a new `dungeon.loop.*` namespace (`level_menu` / `enter_level` / `who_prompt` are `tx_fmt`'d with `{level}` / `{max}` bindings; `no_torches` / `at_max_depth` / `leave_cavern` are static `tx0(res_text)`). `wilderness-cave-descent` (the fixture that exercises the dispatcher loop) stays at 45445 bytes — `emit_with_col_jump` emits one CSI prefix per `\n`-line, so the bundled `level_menu` produces the same 5 prefixes as the original 3 `tx()` calls.

Eight commits in chronological order:

```
b44732e §17.A.4 slice A: lift Block 5 (Thun gem-altar) text out of missions.c
f4f7bb4 plan: §17.A.4 slice A closed; top-of-queue → 2026-04-27 #14
a32cdc7 plan: §17.A.4 slice B closed empty; top-of-queue → 2026-04-27 #15
4468739 script: add { combat_int: '<path>' } expression op
b99de4e §23.A follow-up: lift monster-melee fallback arm to DSL do: body
fcc1dc8 plan: §23.A closed end-to-end; top-of-queue → 2026-04-27 #16
6f63350 §17.A.4 slice C: lift dispatcher-loop UI text out of dungeon.c
606c9ae plan: §17.A.4 slice C closed; top-of-queue → 2026-04-28
```

## What to do next

Read `port-plan-incomplete.md` from the top — the "Top of queue (status snapshot, 2026-04-28)" section is the single source of truth. **§17.A.4 slice D (subs.c)** is the last open slice; **§23.A is closed end-to-end**; §25.B closed end-to-end in the prior session.

State at HEAD (`lagergren/port1`, pushed): clean working tree, 39 fixtures green on both gates.

### Recommended next session: §17.A.4 slice D — subs.c

The §17.1 plan-table sized subs.c at 1310 LOC with the §14.4 scorelist overlap as the rebless interaction. Audit subs.c at HEAD before estimating — the slice A/B/C pattern strongly suggests the file is much smaller now and most prose has already migrated. Run:

```sh
wc -l src/subs.c
grep -c "^\s*tx(" src/subs.c       # remaining inline tx() calls
grep -rln "subs\." data/world/texts/ data/world/scripts/   # existing extraction
```

Sequence:
1. Audit subs.c at HEAD; identify which blocks already migrated and which still inline `tx(...)`.
2. Audit fixture reach per block (`grep -rln '<prose>' tools/replay/`). Title-flow fixtures touch parts of subs.c.
3. Lift each remaining run, byte-for-byte. Watch for the `emit_with_col_jump` line-prefix counting (one CSI per `\n`-terminated line) — bundling `tx()` calls that all end in `\r\n` preserves byte-identity, but bundling unterminated calls shifts bytes.
4. **§14.4 scorelist overlap.** Per §17.4: "subs.c's scorelist (§14.4) uses a ~3100-byte stack sort with 30-byte `strcpy` swaps. §14 has its own decomposition plan (sort an index array). Don't fold that rewrite into a text-extraction slice — the rebless signals are too different." So if scorelist text shows up as a residual, lift the text but leave the sort rewrite for a separate §14.4 pass. If the decision is to do them together, sequence so only one fixture rebless is needed.
5. Run `make check` + `make testasan` after each block; bar is green at every commit.
6. Plan refresh: append slice D closure body to `port-plan-complete.md` "§17.A.4 slice closures" section; bump top-of-queue snapshot to 2026-04-28 #2 (or the day's first snapshot of next session); narrow Active block — slice D's closure also closes the §17.A.4 parent (slices A + B + C + D all done), so move §17 itself to "complete" in the master TOC and out of `-incomplete.md`.

### After slice D, in roughly increasing cost order

- **State-abstraction sweep** — 17 modules touch `state.*` directly. Ride along when next touching one of these files; no speculative refactors. ~3-5 sessions.
- **§24bis** — `id: <int>` → slug-keyed state, now unblocked. ~4-6 session block touching every C file.
- Later: §12.3 (dynamic sizing — remaining caps `state.p[26]`, `map[42][82]`, party-inventory `[17]`, `attack_today[26]`), §12.1 (Dockerfile + CI), Ydarg-as-mission.

### Mechanical residuals tracked in -incomplete.md (do when convenient)

- `driv.c::terminate` visited-grid shift bug (line ~619): inner `for (j = 1; j < num_parties; j++)` body never references `j`; just rewrites `visited[x] = visited[x+1]` `num_parties-1` times. Likely fixture-unreachable; needs audit + one-line fix + a fixture if reachable.
- `towne.c::enter` `state.twn == 2` city/village fork: hardcoded to Wilor (`towns[2]` post-§25.B step 3b). Out of scope for slice D; queues under §24bis.

### Move-out queue

§22 and §25 bodies still live in `-incomplete.md` despite both being closed end-to-end. Whenever the plan files get a tidy pass next, those sections drop into `port-plan-complete.md` (§22 already has its own closure block; §25 already has its step-closures block). Slice D's closure also closes §17 and triggers another move-out (the §17 body itself is in `-incomplete.md`).

## Pattern: stale §17.1 estimates

Every slice in the §17.A.4 queue has been substantially smaller than the §17.1 table claimed:

| Slice | §17.1 LOC | LOC at HEAD | Result |
|-------|----------:|------------:|--------|
| A (missions.c) | 583 | 292 (15/16 blocks already migrated) | 1 inline block lifted (Block 5) |
| B (interact.c) | 428 | 69 (drop/swap/HP/rest already migrated; journal moved in from driv.c; check_terrain became a wrapper) | closed empty |
| C (dungeon.c) | 917 | 175 (all per-event encounter narration already migrated) | dispatcher-loop UI lifted |
| D (subs.c) | 1310 | TBD — audit at HEAD before sizing | TBD |

The pattern holds because §17.A.4 was scoped before §22 / §23 / §16 closed, and those parent passes carried most of the prose into data along the way. Don't trust the §17.1 sizing on slice D either — start with the audit.

## Bar per session + memory notes

`make check` + `make testasan` green at every commit. Push only on explicit user approval. Memory notes worth re-reading first: `feedback_state_abstraction.md`, `feedback_preexisting_bug_fixes.md`, `feedback_replay_knobs_centralized.md`, `feedback_script_text_wiring.md`. The cap-driven heap-allocation guiding principle from §25.B (named static cap in `state.h`, heap-alloc off the world.json5 record count at `res_load_world` time, free in `res_shutdown`) still applies for any new work touching a still-static array.
