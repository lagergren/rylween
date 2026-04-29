Continuation prompt (start of session 2026-04-30 #21)

Branch lagergren/port1, HEAD ae0df85 (pushed). 45 replay fixtures
green on make check + make testasan; make format clean; tools/
check_world.sh green. Docker tester (debian-slim + LLVM 22 + LSan)
also green end-to-end — second consecutive session with full Linux
LSan coverage clean.

Session #20 added 2 commits on lagergren/port1
(7dca0e3..ae0df85), both done in-session (no background agent):

  - f7944d1 — C3: food_handler fatigue ladder → balance.fatigue.
    levels table. The four-arm if-cascade in subs.c::food_handler
    (hours_rested == 0 / == -4 / == -8 / < -8) lifts to a data-
    driven dispatch table walked by a new `run_fatigue_ladder`
    helper. Each row is `{threshold, match?, text_id, drain?,
    pause_after?}`; the engine fires the first matching row's arm
    (clw + tx + optional drain + optional pause+clw) and stops.
    `balance.fatigue` reshape: drops the unused `exhausted: -4 /
    weak: -8` stub in favour of `levels: [...]` (4 rows mirroring
    the legacy arms 1:1). New `ryl_fatigue_level` +
    `ryl_fatigue_match` in res.h plus `res_fatigue_levels_count`
    / `res_fatigue_level_at` accessors backed by
    `fold_fatigue_levels` / `reset_fatigue_levels` in res.c
    (parallels the combat_keys / town_keys fold pattern; strict
    schema, unknown match slugs hard-fail at fold time). Adding
    a fifth fatigue tier — or moving an existing one — is now a
    pure world.json5 edit.

  - ae0df85 — D1: struct party.flag[40] cap-flip →
    res_party_flag_max(). Last hardcoded byte-array on struct
    party. Storage flips from inline `unsigned char [40]` to a
    heap pointer sized to res_party_flag_max() at party_realize /
    party_release_members time; PARTY_FLAG_HARD_MAX (40, the
    legacy value) becomes a defensive ceiling that fold_flags
    rejects per-flag-slot declarations beyond. New
    `limits.party_flag_max` knob in world.json5 (default 40,
    clamped to [1, PARTY_FLAG_HARD_MAX]); fold_limits cross-checks
    every party-scope flag's declared slot fits before any
    party_realize runs (misconfigured cap fails loud at world
    load). Save format unchanged: the on-disk shape (`flags: {
    tag: "value", ... }` in current-world.json5) is tag-keyed so
    cap-bumps don't affect old saves. Smoke-tested at cap=34 (one
    above slot 33, the highest declared) — byte-identical, no
    rebless. Tested misconfiguration path at cap=30 — fails loud.
    **After this commit no `struct party` field is sized to a
    compile-time constant.**

Three patterns reinforced in #20:

  - **Data-table dispatch with shape-variant rows**. The C3
    fatigue ladder is the first lift where each table row carries
    a different action *shape* (drain on/off, pause_after on/off)
    instead of just data — the C dispatcher walks the table and
    composes `clw → tx → drain? → pause_after?` per row. Cleaner
    than a DSL script with N hardcoded if-arms when the row count
    is small (4 here), each row's action is a small composition
    of existing primitives, and the goal is "adding a 5th level
    is data-only". Reach for this shape when the alternative is
    a multi-arm DSL if-cascade where each arm calls the same 1-3
    engine_calls in a different combination.

  - **Cap-flip with cross-validation in fold_limits**. D1 follows
    the existing party_slot_max / inventory_slot_max cap-flip
    pattern (HARD_MAX define + runtime accessor + heap-allocate
    in party_realize + free in party_release_members + NULL in
    party_clear_heap_pointers + fold_limits clamp), but adds a
    new wrinkle: fold_limits walks the already-folded flags_tab
    at the end of its body to assert every declared slot fits
    inside party_flag_max. The "Order is not load-bearing"
    invariant of the fold ordering means fold_limits sees
    flags_tab populated; placing the cross-check there means a
    misconfigured cap fails loud at world load rather than at
    runtime when the high-slot flag is first read. Pattern
    generalizes for any future cap whose value must be at least
    the max of some other already-folded table's slot column.

  - **Tag-keyed save format = cap-bumps are free**. The flag[]
    cap-flip didn't need a save-format migration because
    wcw_party_flags / rcw_party_flags emit/read `flags: { tag:
    "value", ... }` (not a positional array). Same shape we
    deliberately moved to in §3.1's flag-tag work pre-§12.3.
    When designing future saves: tag-keyed shapes pay back
    immediately every time the cap moves.

Status snapshot — every closed §-anchor and the open frontier
post-#20:

  - All pre-#20 closures still hold (see #19's continuation
    prompt for the full list).
  - **Tier C3 (food_handler fatigue ladder): closed in #20.**
  - **Tier D1 (struct party.flag[40] cap-flip): closed in #20.**

Open frontier (post-#20):

  - **Tier B** — B3 NPC slot-id → slug-key migration (still in
    flight via background agent from #14a). B8 Sites 1 + 3 left
    in C deliberately (no leverage from a table).
  - **Tier C** — C5 save-file emit shape (~500 LOC, multi-
    session). C1/C2/C3/C4 all closed.
  - **Tier D** — D2/D3 deferred (RNG-identity gated). D1 closed.
  - **Tier E** — E1 repeat-pattern collapse, E2 dungeon.json5
    refinements.

Per-session bar:
  - make check + make testasan + make format — every commit
  - **docker build --target tester** — at least once per session
    when work touches state lifecycle / heap allocation; covers
    the LSan gap on apple-clang. ~3-4 minute build.

Per AGENTS.md: never push without explicit user approval; no
Co-Authored-By lines; inline single-newline file endings; fix
root causes, not symptoms; don't add speculative changes.

Reading order in repo:
  - port-plan.md TOC.
  - port-plan-incomplete.md "Top of queue" (snapshot date now
    2026-04-29 #18 — needs refresh for #20 / #21; C2/C1/B8 from
    #19 + C3/D1 from #20 not yet recorded).
  - port-plan-complete.md.

Begin: pick from the post-#20 queue.

  Default pick: **plan refresh + memory update**. Tier C2/C1/B8
  + CI fix from #19 plus C3/D1 from #20 not yet recorded in
  port-plan-incomplete.md or port-plan-complete.md; "Top of
  queue" snapshot still says 2026-04-29 #18 (two sessions
  stale). Single bookkeeping commit; pair with a new feedback
  memory about the data-table dispatch pattern from C3 and the
  fold_limits cross-validation pattern from D1. Low-risk
  session opener; clears the bookkeeping debt before tackling
  another lift.

  Alternative: **Tier E1/E2 script-side audit**. Walk
  data/world/scripts/*.json5 + dungeon.json5 for repeat-pattern
  candidates and inline RNG ranges that should be formulas.
  Bring findings to the user rather than committing blind — the
  audit is exploratory; the "what's worth lifting" judgment is
  the user's call. Session-sized; mirrors the survey pattern
  from #13b's L2-closure.

  Alternative: **Tier C5 save-file emit shape** (driv.c::
  write_current_world / read_current_world ~500 LOC hand-coded
  fputs serializer). Could share `snapshot.c` plumbing. Bigger
  lift; multi-session — would need scoping first (which subset
  is in scope, fixture/rebless plan). Don't start without an
  explicit user OK.

  Alternative: **Tier B3 status check-in**. Background agent
  from #14a was running the NPC slot-id → slug-key migration.
  See if any patches arrived; pull and review if so.
