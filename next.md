# Continuation prompt — paste this into a fresh session

Five commits on top of `dfbaf08` (the prior session's HEAD —
the §17.A.4 slice D continuation-prompt commit). HEAD is now
the plan-refresh commit. 39 fixtures green on `make check` +
`make testasan` at every commit. Local commits only — not
pushed.

## What landed this session

**State-abstraction ride-along sweep — four files lifted off
raw `state.p[state.cpl].FIELD` / `talker.p->visited[…]` access**
through the `pa_*` / `npc_visited` helpers that were already in
use elsewhere. No new abstraction added in this batch — every
call site swaps a struct-field read/write for the existing
helper one-liner. By file (one commit each):

  - **`towne.c` (`75fd006`)** — 2 hits in `enter_tax_preamble`
    (gold read for the percentage calc + decrement on payment)
    → `pa_gold()` / `pa_add_gold(-amount)`. `state.p[cpl]` count
    in towne.c: 2 → 0.
  - **`missions.c` (`7cd5c2f`)** — 3 hits: `guard_matches` reads
    x/y for the MG_POS / MG_DUNGEON_IDX / MG_TERRAIN matching →
    `pa_x()` / `pa_y()`; `resolve_bindings` reads `name[b->arg]`
    for the MB_PLAYER_NAME binding → `pa_name(b->arg)`. The
    single MB_PLAYER_NAME callsite passes `arg=2` so pa_name's
    NULL-on-out-of-range guard never trips. Code-side count: 3
    → 0 (the remaining 3 grep matches in missions.c are inside
    descriptive comments at lines 40 / 56 / 127).
  - **`speak.c` (`e179647`)** — 2 hits in `speak()` (recognition
    arm read + first-meeting write on `talker.p->visited[
    state.cpl]`) → `npc_visited(talker, state.cpl)` /
    `npc_visited_set(talker, state.cpl, 1)`. Both helpers were
    already in use in driv.c — this extends them to the second
    consumer. Note: clangd flagged `ryl.h` as no-longer-used
    after the lift, but I left it in — `ryl.h` is the
    kitchen-sink include used as a transitive carrier for
    ctype.h / stdio.h / compat.h / state.h across the whole
    codebase; cleaning it up file-by-file is its own queue and
    would break compile in ~all migrated files without parallel
    explicit-include additions.
  - **`res.c` (`4ec26b1`)** — 1 hit in
    `res_dungeon_encounter_recurring`'s `until_flag` arm →
    `pa_flag(slot)`. The `(unsigned char)` cast on `want` is
    dropped since pa_flag returns int and want is already
    int32_t. Adds party.h include (no cycle: party.h pulls
    state.h, res.h does not pull party.h).

39 fixtures byte-identical on `make check` + `make testasan` at
every commit; no `.expect_snapshot` rebless required.

## What to do next

Read `port-plan-incomplete.md` from the top — the "Top of queue
(status snapshot, 2026-04-28 #3)" section is the source of
truth. The state-abstraction sweep took down the cheapest tier
this session (every site was a one-line helper swap with the
helper already existing). The remaining state-abstraction work
all needs a new abstraction first:

1. **`pa_at(int party_idx)` by-index helper + subs.c::scorelist
   migration**. The single state-abstraction target left
   outside the documented abstraction-home files (party.c /
   primitives.c / inventory.c / replay_knobs.c). 11 cross-party
   reads in `scorelist()` (subs.c lines 658-672 — `state.p[i].{
   exp[j],lv[j],kills,party_name,name[0],fireshield,bracelets,
   mastercrown,silverkey,jazzyfoot,cl[j]}`). Shape: introduce a
   by-index party handle (`struct party *party_at(int i)` in
   party.c, distinct from the current-party-only `pa_*` family)
   and lift the 11 reads through it. Same helper unblocks the
   driv.c newparty/migration block (cross-party reads at
   driv.c:400 / 424 / 429 / 432) and is the prerequisite §24bis
   would otherwise have to invent. ~1-2 sessions including the
   driv.c follow-up.
2. **§24bis — `id: <int>` → slug-keyed state**. Now unblocked.
   ~4-6 session block touching every C file. Best to do `pa_at`
   first.
3. **driv.c::terminate visited-grid shift bug** (mechanical
   residual surfaced 2026-04-27 #11; likely fixture-unreachable;
   one-line fix + maybe a fixture).
4. **towne.c::enter `state.twn == 2` city/village fork**
   (mechanical residual surfaced 2026-04-27 #12; folds into
   §24bis when that touches towne.c).
5. **§12.3** dynamic sizing from world.json5 — remaining static
   caps `state.p[26]`, `map[42][82]`, party-inventory `[17]`,
   `attack_today[26]`.
6. **§12.1** Dockerfile + GitHub CI. ~1 session.
7. **Ydarg-as-mission** (§12.4 residual). Blocked on DSL
   evaluator learning mission triggers + item-flag paths.

State at HEAD (lagergren/port1, not pushed): clean working
tree, 39 fixtures green.

## Recommended next session

**Pick `pa_at` + scorelist migration.** It's the natural
next step — every other state-abstraction target outside the
abstraction-home files needs this helper, §24bis would have to
invent it anyway, and scorelist is a bounded, single-file
migration with easy verification (the file it writes is
fixture-unused, so there's no replay risk). Audit:

  grep -nE '^[^/*]*state\.p\[' src/subs.c
  grep -nE '^[^/*]*state\.p\[' src/driv.c
  grep -n 'state\.p\[i\]' src/*.c

After landing the helper + scorelist lift, sweep driv.c's
cross-party reads — same shape, different file. Both gates
green per commit.

## Pattern (closed): state.p[cpl] sweep — cheap tier exhausted

Held end-to-end across all four files this session:

  ┌────────────┬────────────┬───────────┬────────────────────────┐
  │    File    │ Hits before│ Hits after│        Helper          │
  ├────────────┼────────────┼───────────┼────────────────────────┤
  │ towne.c    │      2     │     0     │ pa_gold/pa_add_gold    │
  ├────────────┼────────────┼───────────┼────────────────────────┤
  │ missions.c │      3     │     0     │ pa_x/pa_y/pa_name      │
  ├────────────┼────────────┼───────────┼────────────────────────┤
  │ speak.c    │      2     │     0     │ npc_visited[_set]      │
  ├────────────┼────────────┼───────────┼────────────────────────┤
  │ res.c      │      1     │     0     │ pa_flag                │
  └────────────┴────────────┴───────────┴────────────────────────┘

Reason: the cheap tier of the State-abstraction sweep was every
file where the existing `pa_*` / `npc_visited` helpers already
covered the field being read. Doesn't apply to remaining work —
scorelist and driv.c migration block both need a new by-index
helper first.

## Bar per session + memory notes

`make check` + `make testasan` green at every commit. Push only
on explicit user approval. Memory notes worth re-reading:
`feedback_state_abstraction.md` (the next-up topic; the in-line
recommendation to add `pa_at` is now load-bearing for both
scorelist and driv.c cross-party reads),
`feedback_preexisting_bug_fixes.md` (driv.c terminate
visited-grid shift bug applies),
`feedback_replay_knobs_centralized.md`,
`feedback_script_text_wiring.md`. Cap-driven heap-allocation
principle from §25.B still applies for §12.3's remaining
targets (`state.p[26]` etc.).
