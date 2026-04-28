Continuation prompt (start of session 2026-04-28 #14)

Branch lagergren/port1, HEAD 72d8cc8 (plus an unpushed plan +
this next.md commit landing at session-end). 44 replay fixtures
green on make check + make testasan; format clean. Working tree
clean.

Session #13 was massive — fourteen commits closed five major
work items end-to-end (`376f6bf..72d8cc8`):

  - 376f6bf §20.14 S10: map dims to data — map.rows_max /
    map.cols_max in world.json5. state.map flips from
    `char (*map)[MAP_COLS_HARD_MAX]` to flat `char *map` 1D
    buffer of res_map_rows_max() × res_map_cols_max() bytes.
    wld.c is the sole consumer that knows the stride.
  - bfd2265 §12.3 final cap-flip: limits.party_slot_max
    unbounded above. fold_limits clamp drops to `>= 2` only.
    subs.c::scorelist heap-alloc with cleanup-label pattern;
    party_at / snapshot.c / state.h:317 cpl assert all flip to
    res_party_slot_max(). Smoke-tested with party_slot_max=30
    — boots cleanly, fixtures byte-identical.
  - 1ea80b2 plan move-out: §12.3 + §20.14 S10/S11 closure
    section appended to port-plan-complete.md.
  - d9b9c6b fold_map_dims defensive validation
    (`map.rows >= map.rows_max` -> res_die) + clangd cleanup
    (drops unused ryl.h from gfx.c, interact.c).
  - 5778669 driv.c: lift `time_left = 175` daily-rollover
    budget to data via new driv.daily.time_left knob (sibling
    of existing driv.start.time_left: 200). Last item from
    feedback_battle_data_extraction.md queue.
  - c3ea341..db43423 Ydarg full DSL lift — eight commits via
    background agent. Hoists xl/yl/lvl/room[][] to module-
    level `struct ydarg_state g_ydarg`; lifts cases 2/3/50,
    nav-render, cmd_dispatch (T/P/L/W/R/Q/Z/V), and the per-
    floor while(1) driver into DSL scripts. **`ydarg()` is
    now a one-line trampoline** `play_script("ydarg.main",
    NULL)`. src/ydarg.c 423 → 354 lines.
  - 72d8cc8 ydarg.c clangd unused-includes sweep post-lift.
    Drops ryl.h + towne.h (zero pic-constant + zero
    enter/rumour/object/obdamage callsites after the lift);
    pulls compat.h + <ctype.h> directly. ryl.h now retains
    only its real users: battle.c / dungeon.c / subs.c /
    towne.c (each reaches for the pic constants) plus ryl.c
    itself (defines _version_string).

Status after #13:

§12.3 cap-flip queue: **fully closed**. Every party-slot-
indexed buffer is heap-allocated off res_party_slot_max();
state.map sized off res_map_*_max(); fold_limits clamps to
`>= 2` only. PARTY_SLOT_HARD_MAX is now just the default
value, not a ceiling. NPC_SLOT_HARD_MAX still functions as a
defensive ceiling because random-NPC RNG identity depends on
the bin count.

§20.14: S10 done, S11 done. The remaining S* entries are
small (S6-S9) or already done (S5).

§12.4 ydarg residual: **fully closed**. `ydarg()` is a
trampoline; all gameplay logic in DSL.

ryl.h sweep: complete. Files that include ryl.h all
genuinely use pic constants (battle.c, dungeon.c, subs.c,
towne.c) or define _version_string (ryl.c).

Pick one for #14. The queue is greenfield — no in-flight
work blocks anything below:

1. **WLD_LOCATION_SLOT_MAX = 15 / WLD_RANDOM_LOCATION_MAX = 9
   cap-flip** (small; same shape as §12.3). Heap-allocate
   state.dx[] / state.dy[] off a new
   limits.location_slot_max knob (default 15), and source
   WLD_RANDOM_LOCATION_MAX (driv.c position_locations loop
   bound) from limits.random_location_max (default 9). Five
   callsites in driv.c + two in wld.c. Niche unlock today —
   no in-flight feature blocks on it — but closes the last
   of the static caps in struct ryl_state.

2. **L2 — dungeon.c level-event switch** (port-plan §20
   LARGE entry; multi-session epic). dungeon.c:117-803 is
   the ~700-LOC switch (lvl[cl]) over 0..38 dispatching per-
   encounter logic. Already partially data-driven:
   res_enum("dungeon_encounter", ...) and play_script
   ("dungeon.<sym>", ...) are wired (commit 1962bd9). The
   remaining inline arms are per-encounter parameters (gold
   ranges, monster spawns, riddle text, item drops). Worth
   surveying which arms still have inline logic vs already
   in DSL — that survey is the first session's deliverable.

3. **subs.c::rumour() table → data**. ryl_random(0, 32)
   then a 33-case switch over rumour ids, each emitting
   bespoke text + var bindings (NPC names / locations).
   Texts are already sharded; lifting to a single DSL
   `rumour.dispatch` script + a `rumours: [{id, text,
   bindings}, ...]` data table compresses the C body. The
   numbered rumour-id enum already exists in towne.c
   (RUMOUR_HARRY_TEMPLE = 27 etc.).

4. **subs.c::spell priest-spell list rendering** (low
   priority). Two column-aligned tx() lines emit the spell
   list as a fixed grid; lift to a `spell.priest.list`
   script that walks res_class_spells("priest"). Wait until
   a generalisation surfaces.

5. **Smaller polish items (any-time, no sequencing):**
   - balance.rest.temp_low_damage: 5 vs temp_very_low_damage:
     5 — port-plan §20.5's "possible bug" note; balance/
     design question. Whether the very-low arm should hit
     harder is a user judgement call; would rebless
     wilderness-camp snapshot.
   - Extend `class:` to unclassified terrain entries
     ('s' / 'f' / 'd' / 't' / 'g') if a callsite ever needs
     them. Currently none does.

Memory worth re-reading (auto-loaded into context):
  - feedback_state_abstraction.md — pa_*/pm_*/inv_*/res_*
    helpers, never raw state.p[...]. Critical for any new C
    touching parties.
  - feedback_background_agents.md — for background agents in
    this repo, /tmp + patch, NEVER worktree isolation.
    Sessions #11 (4 commits) and #13 (8 commits) both used
    this pattern for ydarg lifts; both applied cleanly via
    git am.
  - feedback_clangd_real_vs_stale.md — verify clangd via cc
    -Werror=... before deflecting. #13 saw clangd hallucinate
    `ydarg_state_init` / `ydarg_try_move` references that
    didn't exist in the file — verified with cc syntax-only,
    were stale cache.
  - feedback_battle_data_extraction.md — catalog of code-as-
    data extraction targets. After #13 every catalog item is
    closed (last one was time_left = 175).

Reading order in repo:
  - port-plan.md TOC.
  - port-plan-incomplete.md "Top of queue (status snapshot,
    2026-04-28 #13)" + "Recommended next session".
  - port-plan-complete.md — bodies for the §12.3 + §20.14 +
    Ydarg full DSL closures (all in the tail of the file).

Per-session bar: make check + make testasan + make format
all green at every commit. Per AGENTS.md: never push without
explicit user approval; no Co-Authored-By lines; inline
single-newline file endings; fix root causes, not symptoms;
don't add speculative changes.

Begin by reading port-plan-incomplete.md "Top of queue" and
"Recommended next session", then ask which item to take.
Default pick: **#1 (WLD_LOCATION_SLOT_MAX cap-flip)** — small
session-sized task that completes the §12.3 pattern across
the last static cap in struct ryl_state. **#2 (L2 dungeon
extraction)** is the higher-impact alternative if the user
wants to start a multi-session epic.
