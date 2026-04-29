Continuation prompt (start of session 2026-04-30 #19)

Branch lagergren/port1, HEAD on the #18 plan-refresh commit (will
be the bottom commit after this session's plan-refresh lands).
**45 replay fixtures** green on `make check` + `make testasan`;
`make format` clean; `tools/check_world.sh` green. Default Makefile
carries the strict warning set (-Wshadow / -Wstrict-prototypes /
-Wmissing-prototypes / -Wpointer-arith / -Wundef / -Wcast-align /
-Wnull-dereference) and the build emits zero warnings.

Session #18 added 5 commits on lagergren/port1
(04f2695..1ceadfa). The default-pick path from #17's continuation
prompt landed end-to-end PLUS A2 + A3 + A4 (the user asked to do
"everything but B8 and C1 in a single shot"):

  - 04f2695 B4+B5: speak.c chitchat 3-way switch + recognize/tells_
    you prose → DSL/text. The (C)hitchat arm's ryl_random(0,2) +
    3-way switch + reply_chitchat() collapses to a 9-line play_
    script("speak.chitchat", ...) trampoline. New speak.chitchat
    script rolls subj via { random: [0, 2] }, branches on the female
    binding via if/else, delays 1s, then play_script's the existing
    speak.chit_reply for the per-(subj, job) reply text. Two inline
    tx() prose lines (lines 161, 191) move to speak.recognize_pre /
    speak.tells_you_pre text records. Static reply_chitchat helper
    retires; unused `x` local retires. New speak.chit.added_blank:
    [''] text record carries the two \r\n separators.

  - 73eb2a8 A2: statusline TIME + status-bucket labels → data.
    driv.c::status_line's four hardcoded TIME-bucket snprintfs
    collapse to one res_status_time_label(session_hour()) lookup;
    three pa_food()-bucketed status labels become res_text against
    constant ids. New world.json5 status.time_buckets table data-
    drives the hour ranges (lo > hi wraps midnight, mirrors
    is_hour_in_range); new driv.statusline.{time,status}.<bucket>
    text records carry the literal labels (with embedded ANSI color
    + width-padding). Food classification keeps its pa_pl()*3
    threshold inline (per-party formula); only labels move to
    data. H1..H4 hit-points line stays inline. Adding a fifth
    time bucket = one entry + one text record.

  - c7dc6b1 A3: show + show_party UI strings → text records.
    driv.c::show()'s 13 inline labeled tx() calls collapse to two
    tx_fmt blocks (driv.show.first_page / second_page); show_
    party()'s top torches/rations/gold counts collapse to one
    tx_fmt("driv.party.counts", ...). Int fields snprintf'd into
    16-char locals before binding (tx_fmt placeholders are string-
    only). New driv.show.status_{dead,alive} atoms replace inline
    strcpy(str, "Dead.") / "Alive." selection.

  - f2f1370 A4: create_party UI strings + race/class menu bounds
    → data. Per-character stat-roll page collapses to one tx_fmt
    ("driv.create.stats_page", ...) block; "Give Character %d a
    name!" becomes a one-line tx_fmt. New res_enum_count(id)
    accessor in res.c/res.h. The hardcoded '1'..'4' race / '1'..'6'
    class digit-prompt bounds derive from '0' + res_enum_count(...)
    once before the per-member loop — adding a fourth race or
    seventh class is now a pure data edit.

  - 1ceadfa B2 fixture coverage: wilderness-equipment-swap.
    The Tier B2 lift in #17 (commit 57c2ba1) had no fixture
    coverage. New wilderness-equipment-swap exercises the unequip
    no-weapon early-return path: P keystroke + member 1 / no weapon
    hits the `cond: { eq: [{ member: ..., field: 'weapon' }, 0] }`
    arm, emits subs.equipment.no_weapon, pauses 800ms, clw +
    return. Confirms the resolve_field_name DSL extension (#17,
    5c082f1) end-to-end. Fixture count 44 → 45.

Three patterns formalized in #18:

  - **Plain-string text records** (no array → no trailing CRLF) for
    fragments concatenated with other content. Used by driv.
    statusline.{time,status}.<bucket>. Existing array-of-strings
    shape always added a trailing CRLF; the plain-string form is
    the right shape when the C side is building a single-line
    buffer.

  - **tx_fmt with int-snprintf'd locals** for multi-line content
    with mixed string + int placeholders. tx_fmt placeholders are
    string-only; int fields get snprintf'd into 8-16-char locals
    before binding. Pattern used in driv.show (8 int fields) and
    driv.create.stats_page (4 int fields). Cleaner than per-line
    tx() chains when bindings overlap or page break is a clean
    ryl_pause boundary.

  - **res_enum_count(id) for UI menu bounds**. New res.h accessor;
    returns the n_values for positional-array enums OR the
    keyed-record count, 0 for unknown. Pattern: derive the digit-
    prompt's ASCII upper bound as '0' + res_enum_count("<id>")
    once before the loop. Generalizable to any "press 1-N" prompt
    where N is enum-driven.

Status snapshot — every closed §-anchor and the open frontier
post-#18:

  - §12.3 / §20.14 S10/S11 / §12.4 ydarg / §20 L2 / §24bis
    phase-3 / ryl.h sweep / Tier R: all closed (pre-#16).
  - Tier B6 + Tier B9 + Tier C4 (sysop secrets): closed in #15.
  - Tier 0 (mission_triggers[] → JSON5): closed in #16.
  - Tier B6 followup + Tier B7 + Tier B7 followup: closed in #16.
  - Tier A1 (terminal positions → screen:): closed in #16.
  - gfx.c retired; merged into primitives.c (#16).
  - Strict-warnings cleanup: closed in #16.
  - Tier B2 (subs.c equipment-swap UI flows): closed in #17.
  - DSL extension — `field:` accepts {var:'name'}: closed in #17.
  - **Tier A2 (statusline TIME + status labels): closed in #18.**
  - **Tier A3 (show + show_party UI strings): closed in #18.**
  - **Tier A4 (create_party UI + race/class menu bounds): closed
    in #18.** Plus res_enum_count is a reusable knob.
  - **Tier B4 (speak.c chitchat 3-way switch): closed in #18.**
  - **Tier B5 (speak.c recognize/tells_you prose): closed in #18.**
  - **Tier B2 fixture coverage: closed in #18.** Replay set
    44 → 45.

Open frontier (port-plan-incomplete.md "Recommended next session",
post-#18):

  - **Tier B (dispatch / cascades)** — B8 battle combat-key
    dispatch (multi-context — needs scoping decisions before lift;
    F/R prompts vs U/D/A/B/C menus vs A/B/D combat actions).
  - **Tier C (engine state with hardcoded shape)** — C1 exp_check
    sub-items (4: add[5] cap, max-level 28, pc_class_key map,
    hp_floor formula), C2 encounter pic count (`balance.combat
    .encounter_pic_count` knob — trivial), C3 food_handler fatigue
    ladder (subs.c if-cascade), C5 save-file emit shape (~500 LOC,
    multi-session).
  - **Tier D (structural cap-flips)** — D1 struct party.flag[40]
    (last byte-array on struct party). D2 NPC_SLOT_HARD_MAX and D3
    TOWN_SLOT_HARD_MAX deferred (RNG-identity gated).
  - **Tier E (script-side audit)** — E1 repeat-pattern collapse,
    E2 dungeon.json5 refinements.

What's unique about the post-#18 frontier: every Tier A item is
closed. The remaining open work is dispatch tables (B8 — needs
scoping), engine-state extraction (C/D), and script-side polish
(E). No more "lift the labels" / "lift the strings" type work —
the next round is structurally different.

Memory worth re-reading (auto-loaded into context):

  - feedback_state_abstraction.md — pa_*/pm_*/inv_*/res_* helpers,
    never raw state.p[...].
  - feedback_background_agents.md — for background agents in this
    repo, /tmp + patch OR worktree isolation. Both patterns
    validated.
  - feedback_clangd_real_vs_stale.md — verify clangd via
    `cc -fsyntax-only -Werror=...` before deflecting. Stale gfx.h
    diagnostics post-#16 merge are a known false positive.
  - feedback_replay_knobs_centralized.md — new test-only env knobs
    go in src/replay_knobs.{c,h} as rk_* helpers.
  - feedback_script_text_wiring.md — new shards need world.json5
    $import + matching top-level shape.

Reading order in repo:

  - port-plan.md TOC.
  - port-plan-incomplete.md "Top of queue" (snapshot date now
    2026-04-29 #18) + the Tier 0 / A / B / C / D / E breakdown.
    Tier A all struck through; Tier B2/B4/B5 struck through;
    Tier B8 + Tier C1 are the lead candidates for #19.
  - port-plan-complete.md — bodies for all closed §-anchors.

Per-session bar: `make check` + `make testasan` + `make format`
all green at every commit. Per AGENTS.md: never push without
explicit user approval; no Co-Authored-By lines; inline single-
newline file endings; fix root causes, not symptoms; don't add
speculative changes.

Begin: pick from the post-#18 queue.

  Default pick: **Tier B8 (battle combat-key dispatch)**. Larger
  session — needs scoping first. battle.c has multiple distinct
  context-aware keypress prompts: F/R fight-or-run, U/D/A/B/C
  use-item / defend / attack menus, A/B/D mid-combat actions.
  Decide whether each lifts as a separate `combat_keys_<context>:`
  table or a single `combat_keys:` with a `context:` filter, then
  lift. Bring scoping notes to the user before committing.

  Alternative: **Tier C1 (exp_check level-up dispatcher)**. Four
  content-in-C items in one function: add[5] cap → res_party
  _member_max() + 1; max-level 28 → res_int_path("class.max
  _level") or similar; pc_class_key[] int → slug map → single
  res_enum_sym("pc_class", cl_id) call (legacy boundary; storage
  is already slug-pointer); hp_floor formula `(h * 2) / 3` →
  named formula `level_up.hp_floor`. Session-sized; touches
  formulas/world.json5 + battle.c. Smaller scope than B8, no
  scoping pass needed.

  Alternative: **Tier C2 (encounter pic count)**. Trivial knob
  flip — battle.c:41 `blitgfx(encounterpic + ryl_random(0, 4))`
  to `blitgfx(encounterpic + ryl_random(0, res_int_path("balance
  .combat.encounter_pic_count") - 1))`. Bundles with anything;
  no fixture risk.

  Alternative: **Tier D1 (struct party.flag[40] cap-flip)**.
  Last hardcoded byte-array on struct party. Save-format
  coordination needed (flags persisted by slot id). Pattern:
  same as the #11/#12 cap-flips — name the cap in state.h, heap-
  allocate off world.json5 limits.party_flag_max at party_realize,
  free in party_release_members. Session-sized.

  Alternative: **Tier E1/E2 (script-side audit)**. Walk
  data/world/scripts/*.json5 and dungeon.json5 for repeat-pattern
  candidates (ask_target → cost-check → mutate → emit cascades;
  if member-stat < N gates) and inline RNG ranges that should be
  formulas. Low-pressure cleanup; bring findings to the user
  rather than committing blind.

Same content is in next.md (committed in this session) so the
next session auto-picks it up.
