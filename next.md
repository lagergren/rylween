# Continuation prompt — paste this into a fresh session

Seven new commits on top of `c9f8d10` (the prior session's HEAD —
the state-abstraction ride-along + plan-refresh continuation
prompt). HEAD is now the continuation-prompt commit; the prior
six commits are the state-abstraction sweep closure + plan-file
tidy. 39 fixtures green on `make check` + `make testasan` at
every commit; commits pushed to `origin/lagergren/port1` at
session close.

## What landed this session

**State-abstraction sweep effectively closed.** Adds the
`party_at(int idx)` by-index handle in party.c / party.h and
lifts every cross-party `state.p[]` callsite outside the
abstraction-home files through it. Five commits:

  - **`8d3abbd`** — `party_at(int idx)` helper added (party.c
    body + party.h declaration; ~10 lines incl. bounds-check)
    and `subs.c::scorelist`'s 11 cross-party reads collapsed
    via `const struct party *p = party_at(i)` bound at the top
    of the per-party loop body. State.p[] hits in subs.c: 11 →
    0. The shape (struct party * return) was preferred over
    by-field by-index helpers (pa_kills_at(i) / etc.) because
    scorelist pulls 11 different fields per party — adding 11
    helpers for one call site is heavier than reading struct
    fields after a single party_at call.
  - **`4419128`** — driv.c: 50+ state.p[] sites lifted across
    six call patterns. createparty's per-field setup
    parameterized by `a = state.num_parties` lifts via `struct
    party *p = party_at(a)` bound once after `a` is set, with
    all 45 inline `state.p[a].FIELD` reads/writes becoming
    `p->FIELD`. The_game's initial-time-left loop, the
    owner-name match in read_current_world, the silverkey-sweep
    menu loop, and the per-party loops in
    write_current_world / read_current_world all swap to
    party_at. Terminate's free + memmove + clear-tail block
    also lifts; the memmove keeps its `sizeof(struct party)`
    spelling for clarity (sizeof doesn't evaluate the operand,
    so `sizeof *party_at(0)` would also work but is less
    readable).
  - **`026317d`** — replay_knobs.c: 11 sites across the six
    `rk_apply_*` helpers (rk_apply_party_lv / rk_apply_pos /
    rk_apply_ydarg_grants / rk_apply_party_wp /
    rk_apply_party_hp_full / rk_apply_dead_slots) all bind
    `struct party *p = party_at(a)` once at the top of the
    helper. rk_apply_dead_slots binds as `party` instead of
    `p` because it already has a `const char *p = env`
    env-string walker.
  - **`0dd1580`** — primitives.c: 5 sysop backdoor writes
    (Marcus Lagergren-gated debug shortcuts in the input
    handler) lift via existing pa_* helpers — pa_add_time_left,
    pa_set_inventory, pa_add_gold. No party_at needed since
    these are current-party. Adds party.h include
    (primitives.c already pulls driv.h, so the layering is
    already loose).
  - **`92bc418`** — plan refresh: top-of-queue → 2026-04-28 #4,
    state-abstraction bullet flipped from 'work in flight' to
    'effectively closed', recommended-next pivots to §24bis.
  - **`618b600`** — plan-file tidy: relocate the §25 body from
    `port-plan-incomplete.md` to `port-plan-complete.md`
    alongside the per-step closures (the parent body had been
    stranded in -incomplete.md since §25.B step 4 closed §25
    end-to-end on 2026-04-27 #13). Replace the body with a
    CLOSED-stub matching the §22 / §24 pattern. Update the
    '§25.B step closures' opening blurb to reflect the parent
    body now sitting alongside it. Drop the move-out-queue's
    §22 / §25 bullets — every §-numbered body is now in the
    right file.

After this session the only remaining `state.p[]` code-side
hits are inside party.c (the abstraction home) and inventory.c
(the inventory abstraction layer's 5 sites in inv_find_empty /
inv_find_item / inv_find_in_range / inv_count_in_range / inv_add
where direct access is the intended shape). The headline
state-abstraction work is done. Plan files are tidy: every
closed §-numbered body lives in `port-plan-complete.md`;
`port-plan-incomplete.md` carries only stubs + the active /
deferred / reference / vision sections + session-journal
entries (which are evergreen migration notes referenced from
continuation prompts).

## What to do next

Read `port-plan-incomplete.md` from the top — the "Top of queue
(status snapshot, 2026-04-28 #4)" section is the source of
truth. With the state-abstraction sweep effectively closed,
the next-up shifts to §24bis:

1. **§24bis — `id: <int>` → slug-keyed state**. Now fully
   unblocked. ~4-6 session block touching every C file. The
   per-field pa_* / pm_* / inv_* / res_* abstractions are in
   place, party_at covers the cross-party access §24bis would
   have needed, and the §25.B axis flips already moved every
   numeric-id consumer to its slot equivalent. Best entry
   point is probably the save format (realm.dat / npc.dat
   migrate to JSON5 keyed by slug — save-format license was
   user-granted 2026-04-22) or the world.json5 `id: <int>`
   fields themselves; the §24bis body has the sequencing.
2. **driv.c::terminate visited-grid shift bug** (mechanical
   residual). Likely fixture-unreachable; one-line fix + maybe
   a fixture.
3. **towne.c::enter `state.twn == 2` city/village fork**
   (mechanical residual). Folds into §24bis when that touches
   towne.c.
4. **§12.3** dynamic sizing from world.json5 — remaining
   static caps `state.p[26]`, `map[42][82]`, party-inventory
   `[17]`, `attack_today[26]`. The party_at helper lands a
   piece of §12.3's groundwork (callers don't reach into
   state.p[] directly, so a future heap-allocated state.p
   only touches party.c).
5. **§12.1** Dockerfile + GitHub CI. ~1 session.
6. **Ydarg-as-mission** (§12.4 residual). Blocked on DSL
   evaluator learning mission triggers + item-flag paths.

State at HEAD (lagergren/port1, pushed to
origin/lagergren/port1): clean working tree, 39 fixtures
green.

## Recommended next session

**Pick §24bis.** The state-abstraction sweep made it cheaper:
party_at + the per-field abstractions mean §24bis's "every
state accessor changes shape" cost is now mostly contained to
party.c + the JSON5 schema. Audit:

  grep -nlE 'id: *[0-9]+' data/world.json5 data/world/**/*.json5
  grep -n 'res_(monster|item|town|npc|spell)_id' src/*.c | head
  grep -nE '"id":' data/world/**/*.json5 | head

If §24bis feels too big for one session, the mechanical
residuals (driv.c::terminate visited-grid, towne.c twn==2)
are each <1 hour and fold naturally under §24bis when that
touches their files.

## Pattern (closed): state-abstraction sweep — full closure

Held end-to-end across two sessions:

  ┌─────────────────┬────────────┬───────────┬─────────────────────────────┐
  │      File       │ Hits before│ Hits after│           Helper            │
  ├─────────────────┼────────────┼───────────┼─────────────────────────────┤
  │ towne.c         │      2     │     0     │ pa_gold/pa_add_gold         │
  │ missions.c      │      3     │     0     │ pa_x/pa_y/pa_name           │
  │ speak.c         │      2     │     0     │ npc_visited[_set]           │
  │ res.c           │      1     │     0     │ pa_flag                     │
  │ subs.c          │     11     │     0     │ party_at (NEW)              │
  │ driv.c          │     50+    │     0     │ party_at (NEW)              │
  │ replay_knobs.c  │     11     │     0     │ party_at (NEW)              │
  │ primitives.c    │      5     │     0     │ pa_add_time_left/etc.       │
  └─────────────────┴────────────┴───────────┴─────────────────────────────┘

Reason: ~85 state.p[] callsites across 8 files migrated; the
helpers now cover every cross-party + current-party access
shape outside the abstraction-home files. Remaining state.p[]
access lives only in party.c (the abstraction home) and
inventory.c (the inventory abstraction). Doesn't apply to the
remaining queued work — §24bis is a different axis (numeric
id → slug), §12.3 is heap-allocation cap-driven sizing, both
benefit from the encapsulation but neither IS state-abstraction
work.

## Bar per session + memory notes

`make check` + `make testasan` green at every commit. Push only
on explicit user approval. Memory notes worth re-reading:
`feedback_state_abstraction.md` (now updated to reflect
closure — the rule for new code is "use pa_* / party_at,
never state.p[…] outside party.c"),
`feedback_preexisting_bug_fixes.md` (driv.c terminate
visited-grid shift bug applies),
`feedback_replay_knobs_centralized.md`,
`feedback_script_text_wiring.md`. Cap-driven heap-allocation
principle from §25.B still applies for §12.3's remaining
targets (`state.p[26]` etc.) — and party_at's design now
contains the callsite-side cost of that future migration.
