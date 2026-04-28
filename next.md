Continuation prompt (start of session 2026-04-28 #15)

Branch lagergren/port1, HEAD <pending push>, pushed to origin
post-#14b. 44 replay fixtures green on make check + make
testasan; format clean. Working tree clean.

Sessions #13b + #14a + #14b together pushed 18 commits
(855c138..<HEAD>). #13b (855c138..e2e33bc, 8 commits) closed
every named §-anchor from the prior queue. #14a (8f9fb9c..
38eb871, 8 commits) tackled bug fixes + the §24bis NPC
follow-up + Wilderness rename + a deeper plan refresh. #14b
(7eb1c9e..<HEAD>, 2 commits + plan/next.md) finished the
cosmetic-rename sweep:

  - 7eb1c9e armour → armor (American spelling) sweep —
    background agent. 113 occurrences flipped across src/
    (12 files) + data/ (11 files including
    `data/world/items/armour.json5` → `armor.json5`) +
    tools/replay/ (43 fixture pairs reblessed once via
    `tools/replay.sh --all --bless`). Save-format reader at
    driv.c:1275-1276 accepts both `armor` (canonical) and
    legacy `armour` keys via dual-`rcw_int` fallback.

  - 1ffba39 19 functions → snake_case + American spelling
    (Tier R2). Mechanical sweep via word-boundary sed (BSD
    `[[:<:]]` / `[[:>:]]`). Renames:
      showparty   → show_party
      weaponoff   → weapon_off
      armoroff    → armor_off
      weaponr     → weapon_ready
      armorr      → armor_ready
      read_journal → show_journal (semantic rename)
      journalf    → journal_add
      expcheck    → exp_check
      createparty → create_party
      invlist     → inv_list
      losegame    → lose_game
      scorelist   → score_list
      timehandler → time_handler
      foodhandler → food_handler
      cleanexit   → clean_exit
      statusline  → status_line
      disptext    → disp_text
      getstr      → get_str
      hotkey      → hot_key
    35 files touched (231 lines flipped). Touches definitions
    + every callsite + script.c engine_call dispatch table +
    every `engine_call: '<name>'` reference in
    data/world/scripts/spells/items. Single clang-format
    realignment auto-applied in speak.c.

Status snapshot — every closed §-anchor and the open frontier:

  - §12.3 cap-flip queue: fully closed.
  - §20.14 S10 (map dims) / S11 (formula DSL): closed.
  - §12.4 ydarg residual: closed (one-line trampoline).
  - §20 L2 dungeon: confirmed closed + 2 latent bugs fixed.
  - §24bis phase-3 NPC follow-up: closed (b6b8191).
  - ryl.h unused-includes sweep: complete.
  - Tier R naming conventions: closed (R1 armour→armor +
    R2 function snake_case).

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
    strings, B6 towne keystroke dispatch (user-flagged), B7
    driv main-loop keystroke dispatch (15+ arms), B8 battle
    combat-key dispatch, B9 primitives sysop backdoor.

  - Tier C: engine state with hardcoded shape. C1 exp_check
    sub-items (4: add[5] cap, max-level 28, pc_class_key map,
    hp_floor formula), C2 encounter pic count, C3 food_handler
    fatigue ladder, C4 Karl-Skagerberg easter egg, C5
    save-file emit shape.

  - Tier D: structural cap-flips. D1 struct party.flag[40], D2
    NPC_SLOT_HARD_MAX (deferred — RNG-identity gated), D3
    TOWN_SLOT_HARD_MAX (deferred).

  - Tier E: script-side audit. E1 repeat-pattern collapse, E2
    dungeon.json5 refinements.

Save game compatibility note: the user's ~/.rylween/ was
overwritten with the fresh sample-save in #14a (backed up to
~/.rylween.bak.<timestamp>). Aragorn (Fighter/Human, 65 hp),
Gandalf (Mage/Elf, 85 hp), Mira (Priest/Halfling, 72 hp),
Legolas (Ranger/Elf, 73 hp). Stats heroic (65-85 range). Save
format now accepts both `armor` and legacy `armour` keys via
dual-`rcw_int` fallback.

Memory worth re-reading (auto-loaded into context):
  - feedback_state_abstraction.md — pa_*/pm_*/inv_*/res_*
    helpers, never raw state.p[...]. Critical for any new C
    touching parties.
  - feedback_background_agents.md — for background agents in
    this repo, /tmp + patch, NEVER worktree isolation. Sessions
    #11 / #13 / #13a / #13b / #14a / #14b all used this
    pattern; all applied cleanly via git am.
  - feedback_clangd_real_vs_stale.md — verify clangd via
    `cc -fsyntax-only -Werror=...` before deflecting. #14b
    saw extensive clangd hallucination from agent /tmp
    checkouts leaking diagnostics into the workspace's view.
  - feedback_battle_data_extraction.md — CLOSED catalog
    (historical reference).

Reading order in repo:
  - port-plan.md TOC.
  - port-plan-incomplete.md "Top of queue (status snapshot,
    2026-04-28 #13b)" + "Recommended next session" (the
    six-tier engine→data inventory + closed Tier R).
  - port-plan-complete.md — bodies for all closed §-anchors.

Per-session bar: make check + make testasan + make format all
green at every commit. Per AGENTS.md: never push without
explicit user approval; no Co-Authored-By lines; inline
single-newline file endings; fix root causes, not symptoms;
don't add speculative changes.

Begin: pick from the post-#14b queue.

  **Default pick: Tier 0 (mission_triggers → missions.json5)**
  — the biggest single content-in-C item remaining. Multi-
  session; start with the schema design + fold loader.

  **Alternative: B6 (towne keystroke dispatch → data)** —
  small session-sized lift the user specifically flagged in
  #14a.

  **Alternative: A1 (terminal positions → screen: subtree)**
  — pervasive but mechanical; ~12 sites in driv.c + ~10 in
  gfx.c.

  **Alternative: B7 (driv main-loop keystroke dispatch → data)**
  — largest single-function content extraction outside Tier 0;
  unlocks data-driven world authoring of the player's primary
  input loop.
