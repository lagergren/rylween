Continuation prompt (start of session 2026-04-29 #16)

Branch lagergren/port1, HEAD <pending push>, pushed to origin
post-#15. 44 replay fixtures green on make check + make
testasan; format clean. Working tree clean.

Session #15 added two commits (44500bd..29e4c70) via two
parallel background agents (/tmp clones + patch handoff per
feedback_background_agents.md):

  - 44500bd B6: towne.c::enter keystroke dispatcher →
    world.json5 town_keys[]. 6-arm if/else cascade
    (C/S/I/M/T/L) lifted to a {key, cond?, action} array.
    New res_town_keys_count / res_town_key_at accessors;
    new town_dispatch_key(c, has_church) shim walks the
    table and calls the existing church/smith/inn/merchant/
    tavern wrappers (which still own hour-advance + script
    bindings). The 'L' arm folds into a 'leave_town' action.

  - 29e4c70 B9+C4: sysop backdoors → world.json5 secrets:.
    Marcus Lagergren key dispatch (primitives.c) and
    Karl-Skagerberg every-101-steps spawn (subs.c) both
    lifted to a slug-keyed `secrets:` subtree (marcus.keys[]
    + karl.step_trigger). Magic item ids 104/108/109/110
    flipped to slug tags (drums_of_death /
    gem_of_resurrection / kaels_axe / holy_handgrenade)
    resolved via res_item_id; cadence (101) and encounter
    monster slug (skagerberg_killer) are now data. New
    secret_action enum + struct in res.h with
    res_secrets_check_key / res_secrets_step_trigger
    accessors. Runtime tree walks (no fold-time
    pre-resolution — cold-path dispatch).

Status snapshot — every closed §-anchor and the open frontier:

  - §12.3 cap-flip queue: fully closed.
  - §20.14 S10 (map dims) / S11 (formula DSL): closed.
  - §12.4 ydarg residual: closed (one-line trampoline).
  - §20 L2 dungeon: confirmed closed + 2 latent bugs fixed.
  - §24bis phase-3 NPC follow-up: closed (b6b8191).
  - ryl.h unused-includes sweep: complete.
  - Tier R naming conventions: closed (R1 armour→armor +
    R2 function snake_case).
  - Tier B6 (towne keystrokes): closed in #15.
  - Tier B9 + Tier C4 (sysop secrets): closed in #15.

Open frontier (port-plan-incomplete.md "Recommended next
session" Tier 0 / A / B / C / D / E):

  - Tier 0 (biggest single win): missions.c::mission_triggers[]
    16-entry static C struct → world/missions.json5. Pure
    scenario data masquerading as code.

  - Tier A: hardcoded UI layer. A1 terminal positions
    `c(x, y)` → screen: subtree, A2 statusline content
    (TIME / HUNGRY-STARVING-OK), A3 view/show / show_party,
    A4 create_party UI strings + race/class menu bounds.

  - Tier B: dispatch / cascades. B2 equipment-swap UI flows
    (subs.c), B4 speak chitchat 3-way switch, B5 speak prose
    strings, B7 driv main-loop keystroke dispatch (15+ arms),
    B8 battle combat-key dispatch (multi-context — needs
    scoping decisions before lift).

  - Tier C: engine state with hardcoded shape. C1 exp_check
    sub-items (4: add[5] cap, max-level 28, pc_class_key map,
    hp_floor formula), C2 encounter pic count, C3 food_handler
    fatigue ladder, C5 save-file emit shape.

  - Tier D: structural cap-flips. D1 struct party.flag[40], D2
    NPC_SLOT_HARD_MAX (deferred — RNG-identity gated), D3
    TOWN_SLOT_HARD_MAX (deferred).

  - Tier E: script-side audit. E1 repeat-pattern collapse, E2
    dungeon.json5 refinements.

Save game compatibility note: ~/.rylween/ holds the heroic
sample party from #14a (Aragorn / Gandalf / Mira / Legolas;
65-85 hp). Save format accepts both `armor` and legacy
`armour` keys. The new `secrets:` subtree is engine-only at
fold time — no save-format change.

Memory worth re-reading (auto-loaded into context):
  - feedback_state_abstraction.md — pa_*/pm_*/inv_*/res_*
    helpers, never raw state.p[...]. Critical for any new C
    touching parties.
  - feedback_background_agents.md — for background agents in
    this repo, /tmp + patch, NEVER worktree isolation.
    Sessions #11 / #13 / #13a / #13b / #14a / #14b / #15 all
    used this pattern; all applied cleanly via git am.
  - feedback_clangd_real_vs_stale.md — verify clangd via
    `cc -fsyntax-only -Werror=...` before deflecting. #15
    saw a fresh leak (old-name diagnostics from /tmp clones
    bleeding into the workspace's view) — confirmed stale,
    nothing acted on.
  - feedback_battle_data_extraction.md — CLOSED catalog
    (historical reference).

Reading order in repo:
  - port-plan.md TOC.
  - port-plan-incomplete.md "Top of queue" + "Closed since
    the previous snapshot (2026-04-29 #15)" + "Recommended
    next session" (Tier 0 / A / B / C / D / E).
  - port-plan-complete.md — bodies for all closed §-anchors.

Per-session bar: make check + make testasan + make format all
green at every commit. Per AGENTS.md: never push without
explicit user approval; no Co-Authored-By lines; inline
single-newline file endings; fix root causes, not symptoms;
don't add speculative changes.

Begin: pick from the post-#15 queue.

  **Default pick: Tier 0 (mission_triggers → missions.json5)**
  — the biggest single content-in-C item remaining. Multi-
  session; start with the schema design + fold loader.

  **Alternative: B7 (driv main-loop keystroke dispatch → data)**
  — largest single-function content extraction outside Tier 0;
  unlocks data-driven world authoring of the player's primary
  input loop. The B6 lift in #15 is the template (table-walk
  + small dispatch shim); B7 is ~15 keys vs B6's 6.

  **Alternative: A1 (terminal positions → screen: subtree)**
  — pervasive but mechanical; ~12 sites in driv.c + ~10 in
  gfx.c.

  **Alternative: B8 (battle combat-key dispatch)** — first
  needs a scoping decision: battle.c has multiple distinct
  dispatch contexts (F/R prompts, U/D/A/B/C menus, A/B/D
  combat actions) in different states. Each could lift
  independently or share a context-aware table.
