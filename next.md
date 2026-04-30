Continuation prompt (start of session 2026-05-01 #22)

Branch lagergren/port1, HEAD 03aa00c (pushed). 45 replay fixtures
green on make check + make testasan; make format clean; tools/
check_world.sh green. Docker tester (debian-slim + LLVM 22 + LSan)
also green end-to-end — third consecutive session with full Linux
LSan coverage clean.

Session #21 was a single bookkeeping commit (03aa00c, foreground):

  - **plan refresh**. Recorded sessions #19 (C2/C1/B8 + the
    res_shutdown LSan CI fix) and #20 (C3/D1) in port-plan-
    incomplete.md. Top-of-queue snapshot date moved from 2026-04-29
    #18 → 2026-04-30 #21 with new "Closed since the previous
    snapshot" entries covering the six commits in 935a19b..ae0df85.
    Tier-catalog strike-throughs: B8 (combat-key dispatch), C1
    (exp_check level-up dispatcher), C2 (encounter pic count), C3
    (food_handler fatigue ladder), D1 (party.flag[40] cap-flip).
    Pick order rewritten post-#21: frontier is C5 → E1/E2 → B3
    status check. Three patterns reinforced in #20 documented
    inline; the first two persisted to memory under
    feedback_data_table_dispatch_patterns.md.

Status snapshot — every closed §-anchor and the open frontier
post-#21:

  - All pre-#21 closures still hold (see #20's continuation
    prompt for the full list).
  - **Plan bookkeeping caught up.** Every Tier-0/A/B/C/D §-anchor
    closed except B3 (in flight) and C5 (multi-session, needs
    scoping).

Open frontier (post-#21):

  - **Tier B** — B3 NPC slot-id → slug-key migration (still in
    flight via background agent from #14a). Other B sites
    deliberately closed.
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
  - port-plan-incomplete.md "Top of queue" (snapshot date is now
    2026-04-30 #21, current).
  - port-plan-complete.md.

Begin: pick from the post-#21 queue.

  Default pick: **Tier E1/E2 script-side audit**. With every
  named §-anchor below B3/C5 closed, the next leverage tier is
  the data-side. Walk data/world/scripts/*.json5 +
  data/world/scripts/dungeon.json5 (and the other
  scripts/<file>.json5 shards) for repeat-pattern candidates
  (ask_target → cost-check → mutate → emit cascades; if-
  member-stat < N gates) and inline RNG ranges that should be
  formulas. **Bring findings to the user before committing** —
  the audit is exploratory; the "what's worth lifting" judgment
  is the user's call. Session-sized; mirrors the survey pattern
  from #13b's L2-closure and the post-§17.1 / post-§22 surveys
  upstream.

  Alternative: **Tier C5 save-file emit shape** (driv.c::
  write_current_world / read_current_world ~500 LOC hand-coded
  fputs serializer). Could share `snapshot.c` plumbing. Bigger
  lift; multi-session — would need scoping first (which subset
  is in scope, fixture/rebless plan, save-format compatibility
  policy). Don't start without an explicit user OK.

  Alternative: **Tier B3 status check-in**. Background agent
  from #14a was running the NPC slot-id → slug-key migration.
  See if any patches arrived in /tmp; pull and review if so.

  Alternative: **content-as-code re-survey**. Tier 0/A/B/C/D
  named items are closed; one source of new candidates is a
  fresh src/ pass — every .c file scanned for any data table,
  dispatch list, hardcoded UI string, magic threshold, or
  scenario layout that an author would want to edit without
  recompiling. The previous survey was post-§13b; six months
  of lifts later, the residual list is presumably much shorter
  but might surface a new C5-class item or a Tier-F boundary
  reclassification. Bring findings to the user.
