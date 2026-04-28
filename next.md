# Continuation prompt — paste this into a fresh session

Two commits on top of `4984144` (the prior session's HEAD).
HEAD is now the plan-refresh commit. 39 fixtures green on
`make check` + `make testasan` at every commit.

## What landed this session

**§17.A.4 slice D — subs.c text extraction.** The §17.1 plan
table sized subs.c at 1310 LOC; HEAD was 811 LOC because earlier
§20 / §22 work had already migrated the camp / rest / hunt
bodies (all `play_script` wrappers into `rest.json5` /
`hunt.json5`) and the priest spell-cast handoff
(`play_spell_body`). Forty inline `tx(...)` calls collapse to
seventeen text records under `subs.*`; three records are shared
across multiple callsites — `subs.who_prompt` (six "Who? (1-N)"
sites), `subs.dead` (four early-outs), `subs.no_room` (two
no-slot returns). One small static helper `tx_who_prompt(void)`
(7 lines) folds the six "Who?" callsites; records that take a
substitution use `{name}` / `{object}` / `{pronoun}` bindings.
The column-aligned priest spell-list rows at `subs.c::spell`
(line 608 per-slot row + line 611 static "8. Abort" trailer)
stay inline per the §20 L6 / slice B convention.

The §17.4 `scorelist` caveat ("don't fold the §14.4 sort rewrite
into this slice") **did not apply**: `scorelist()` writes
`data/vscore.txt` / `data/score.txt` via `fopen` + `fputs`, not
via `tx` / `tx0` / `tx_fmt` — there's no terminal text to lift.
The §14.4 sort-of-indices rewrite stays in its own queue.

**§17.A.4 closes end-to-end.** Slices A + B + C + D all landed;
slice E (DSL `menu_branch` + `engine_call` ops) is closed
without doing it — slices A-D didn't surface the ≥3 near-
identical C blocks the §17.2 recommendation gated DSL widening
on. **§17 closes end-to-end with it** — body relocated from
`port-plan-incomplete.md` to `port-plan-complete.md` (alongside
the four slice-closure bodies); `port-plan.md` TOC row flipped
to `complete`.

**Audit.** Greps for every lifted literal across
`tools/replay/*.expect_snapshot` returned zero hits, so the
slice is bytes-only safe; no `.expect_snapshot` rebless required.
39 fixtures byte-identical on both gates, including
`wilderness-cave-descent` (45445 bytes — slice C's fixture) and
`wilderness-camp` / `wilderness-hunt` (the rest / hunt fixtures
whose subs.c wrappers `play_script` into `rest.json5` /
`hunt.json5`). subs.c size 811 → 821 LOC (the small bump comes
from `tx_fmt` binding-list formatting; partially offset by the
`tx_who_prompt` helper).

Two commits on this branch:

```
c0de648 §17.A.4 slice D: lift subs.c text out of subs.c
13038ec plan: §17.A.4 slice D closed; §17 closed end-to-end; top-of-queue → 2026-04-28 #2
```

## What to do next

Read `port-plan-incomplete.md` from the top — the **"Top of
queue (status snapshot, 2026-04-28 #2)"** section is the single
source of truth. With §17.A.4 / §17 / §23.A / §25.B all closed
end-to-end, the next-up items are no longer the headline
text-extraction queue; the recommended-next-session block now
points at:

  1. **State-abstraction sweep** (§25.A.1 #6 footer; tracker:
     `feedback_state_abstraction.md`). 17 modules touch `state.*`
     directly today. Natural homes are `state.h` / `party.c` /
     `res.c` / `snapshot.c`. Migration debt is small and
     scattered — inventory.c (~5 sites), missions.c
     (`MB_PLAYER_NAME`), subs.c::scorelist (cross-party access —
     needs a `pa_at(int party_idx)` by-index helper first),
     speak.c (`state.npc` walks), towne.c (2 raw
     `state.p[cpl]`). Ride-along strategy; no speculative
     refactors. ~3-5 sessions.
  2. **§24bis** — `id: <int>` → slug-keyed state. Now unblocked
     post-§25.B; bundled to avoid double-touching every state
     accessor. ~4-6 session block touching every C file.
  3. **§12.3** — dynamic sizing from `world.json5` `limits:`
     subtree. Cap-driven heap-backed flex arrays for `struct
     party` / `struct ryl_state`. Save-format license already
     granted; remaining static caps are `state.p[26]`,
     `map[42][82]`, party-inventory `[17]`,
     `attack_today[26]`.
  4. **§12.1** — Dockerfile + GitHub CI. Reproducible build for
     non-mac contributors. ~1 session.
  5. **Ydarg-as-mission** (§12.4 residual). End-game boss into
     a regular mission record. Blocked on the DSL evaluator
     learning mission triggers + item-flag paths.

State at HEAD (`lagergren/port1`, **not pushed yet — local
commits only**): clean working tree, 39 fixtures green on both
gates.

### Recommended next session: pick a state-abstraction ride-along

The state-abstraction sweep is the cheapest next move because
every callsite has a small blast radius and lands as a single
helper-name swap. The natural starting point is the file with
the highest density of raw `state.p[i].FIELD` access that *also*
already has a `pm_*` / `pa_*` / `inv_*` / `res_*` analogue
defined — i.e. the migration is purely "rename the access
pattern, not invent a new one". Audit candidates:

```sh
grep -nE 'state\.p\[' src/inventory.c src/missions.c src/speak.c src/towne.c src/subs.c | head -40
grep -nE 'state\.npc\[' src/speak.c src/missions.c src/driv.c | head -20
```

Pick the file with the most "`state.p[cpl].FIELD` where a
`pa_field()` / `pm_field()` already exists" hits, lift those
sites to the helpers, and close the file. Then move on. No
speculative refactors and no mass-rewrites — one file per
commit, both gates green.

The one place that needs a *new* helper before it can migrate
is `subs.c::scorelist` (cross-party access reads
`state.p[i].{exp,lv,kills,cl,name}` via 0-indexed mmax-bound
loops; needs `pa_at(int party_idx)` to return a party-handle the
existing `pa_*` accessors take). That's a one-helper add in
`party.c` + `state.h` plus a callsite migration in `subs.c`.
Do this *before* picking up §24bis — without `pa_at` the
slug-keyed state migration in §24bis would have to invent the
same helper anyway.

### Other ride-along opportunities

Each of these can be closed in <1 session if the appetite is
there:

- **`driv.c::terminate` visited-grid shift bug** (line ~619).
  Inner `for (j = 1; j < num_parties; j++)` body never
  references `j`; just rewrites `visited[x] = visited[x+1]`
  `num_parties-1` times (idempotent). Likely the intended
  logic is `for (j = x; j < state.num_parties; j++) {
  npc_visited_set(n, j, npc_visited(n, j + 1)); }` —
  compact the visited grid down by one slot when party `x` is
  removed. Likely fixture-unreachable today (no fixture
  exercises terminate-realm-then-keep-playing); needs an audit
  pass + a one-line fix + a fixture if reachable.
- **`towne.c::enter` `state.twn == 2` fork**. City/village
  text-id pick is hardcoded to `state.twn == 2` (Wilor post-
  §25.B step 3b). Each town record should carry its own
  settlement-class string-id (or a `class: 'city' | 'village'`
  enum) and the C lookup vanishes. Queues under §24bis
  naturally — fold this in when §24bis touches towne.c.

### Move-out queue (plan-files tidy pass)

§22 and §25 bodies still live in `port-plan-incomplete.md`'s
"Closed but body still in this file (move-out queue)" block
even though both closed end-to-end weeks ago (§22 has its own
closure block in `-complete.md`; §25 has its step-closures
block). Whenever the plan files get a tidy pass next, those
sections drop into `port-plan-complete.md`. §17's body went
out cleanly with the slice D closure in this session, so the
move-out queue is one item shorter than before.

## Pattern (closed): stale §17.1 estimates

Every slice in §17.A.4 was substantially smaller than the
§17.1 table claimed:

| Slice          | §17.1 LOC | LOC at HEAD                                                                  | Result                                |
|----------------|----------:|-----------------------------------------------------------------------------:|---------------------------------------|
| A (missions.c) |       583 | 292 (15/16 blocks already migrated)                                          | 1 inline block lifted (Block 5)       |
| B (interact.c) |       428 | 69 (drop/swap/HP/rest already migrated; journal moved in; check_terrain wrap)| closed empty                          |
| C (dungeon.c)  |       917 | 175 (all per-event encounter narration already migrated)                     | dispatcher-loop UI lifted             |
| D (subs.c)     |      1310 | 811 (camp/rest/hunt/priest already migrated)                                 | dispatcher narration lifted, 40→17 ids|

Pattern held end-to-end. The reason: §17.A.4 was scoped before
§16 / §22 / §23 closed, and those parent passes carried most of
the prose into data along the way. Doesn't apply to any of the
remaining queued work — state-abstraction sweep / §24bis / §12.3
were all sized after their gating dependencies closed.

## Bar per session + memory notes

`make check` + `make testasan` green at every commit. Push only
on explicit user approval. Memory notes worth re-reading first:
`feedback_state_abstraction.md` (next-up sweep is the topic),
`feedback_preexisting_bug_fixes.md`, `feedback_replay_knobs_centralized.md`,
`feedback_script_text_wiring.md`. The cap-driven heap-allocation
guiding principle from §25.B (named static cap in `state.h`,
heap-alloc off the world.json5 record count at `res_load_world`
time, free in `res_shutdown`) still applies for any new work
touching a still-static array — `state.p[26]` etc. are §12.3's
remaining targets.
