Continuation prompt (start of session 2026-04-30 #17)

Branch lagergren/port1, HEAD 6c02f98, pushed to origin post-#16.
44 replay fixtures green on `make check` + `make testasan`;
`make format` clean. Default Makefile now carries the strict
warning flag set (-Wshadow / -Wstrict-prototypes / -Wmissing-
prototypes / -Wpointer-arith / -Wundef / -Wcast-align /
-Wnull-dereference) and the build emits zero warnings.

Session #16 added 8 commits via a mix of foreground work + one
background-agent run (Tier 0). All pushed on lagergren/port1:

  - 3fdfae0 B7: driv.c::the_game keystroke dispatcher →
    world.json5 wilderness_keys[]. 17-arm switch (' '/S/W/R/!/J/
    ?/H/V/T/P/E/L/C/Z/Q/X) lifted to a {key, cond?, action}
    array in declared order, matching B6's town_keys shape.
    Movement keys still pre-route through wld_dir_from_key.
    Multi-statement arms (pass / save / redraw / journal /
    view_party / quit_confirm) collapse to small static helpers
    in driv.c.
  - f20f200 B7 followup: dispatch strcmp chain →
    function-pointer table. The first cut of driv_dispatch_key
    was a 17-arm if/else strcmp cascade. Collapsed to a
    {name, fn} table walked once per matching keystroke. Two
    arms need void(void) wrappers to fit the table signature:
    drop() returns uint8_t (driv_drop discards) and
    spell(int32_t) takes the cast-mode arg (driv_cast_spell
    passes 0). User explicitly preferred this pattern over the
    chain.
  - 665004c Tier 0.1: missions.c::mission_triggers[] →
    world/missions.json5. 19-row static const C array (16
    historical wilderness blocks + 3 castle-Ydarg E-key
    entries) lifted to a JSON5 shard imported from world.json5
    next to town_keys. The mission_trigger / mission_binding
    structs and mission_* enums migrated from missions.c to
    missions.h with struct tags so res.h can forward-decl. New
    fold_missions / reset_missions in res.c (~280 lines) wired
    into the existing fold/reset cascades. New
    res_missions_count / res_mission_at accessors.
    Author-friendly slugs (kind: 'pos', action: 'script', etc.)
    parse to the existing C enums; c_fn slugs resolve at
    fold time through a tiny mission_resolve_c_fn() lookup
    that lives in missions.c (only "ydarg" wired today).
    Produced via background agent in
    .claude/worktrees/agent-a5f785fff8476e5c2 (worktree
    cleaned up after extraction). +683/-173 across 6 files.
  - 97ce6c0 A1: terminal positions c(x,y) → world.json5
    screen: subtree. 26 hardcoded c(x, y) call sites across
    driv.c / primitives.c / gfx.c (now in primitives.c —
    see b231607) lifted to a `screen:` subtree with five
    named regions (text_window, top_left, statusline_top,
    statusline_bottom, map_window). New screen_region enum +
    res_screen_xy / res_screen_wh accessors in res.h; new
    screen_goto(region) / screen_goto_map_row(row) helpers
    in primitives.c wrap c(). blitgfx's render_at(8, 6) and
    the 9-row map-window erase loop in clr_map_window now
    read width/height/anchor from data.
  - 5215df7 DRY init() menu helper. Init's S / D / H arms (and
    the verbose-S follow-up) repeated the same 10-line block:
    cl + res_data_path + disp_text + driv.more + key + cl +
    screen_goto(text_window) + clw + res_ansi_path +
    disp_text. Lifted to a single helper
    driv_show_data_more(file). ~30 lines saved.
  - 11e68af Warnings cleanup: enable -Wshadow /
    -Wstrict-prototypes / -Wcast-align in default Makefile.
    Surfaced 77 warnings under stricter flags; fixed all four
    buckets:
      • K&R `void foo()` → `void foo(void)` across 38 sites
        in 7 files (-Wstrict-prototypes).
      • -Wshadow renames: removed dead function-scope `i`
        from battle.c::encounter; renamed loot int → loot_id
        and inner for_each_monster(x, noc) loop var → m;
        driv.c npc-slot loop var i → s; dungeon.c
        party-slot loop var i → s; res.c local `items`
        shadowing global → items_node / magic_items_node
        (3 sites).
      • -Wcast-align in script.c::pm_field_read_at /
        pm_field_write_at: alignment-increasing casts on
        offsetof base pointer rewritten to memcpy. Compiler
        lowers fixed-size memcpy to the same load/store as
        the cast — no runtime cost.
      • _Static_assert C11-extension: replaced with a
        portable C99 macro RYL_STATIC_ASSERT in state.h
        using the negative-array-size trick.
    Plus mh[15] / deadc[15] in battle.c::encounter lifted
    to VLAs sized by foe_max (already read from
    combat.encounter_foe_count_max), retiring the static 15.
    User explicitly chose function-pointer-table over
    if/else and asked for portable C-99 static_assert.
  - ec53bb7 B6 followup: town_dispatch_key strcmp chain →
    function-pointer table. Same shape as f20f200 (B7
    followup). The 6-arm if/else strcmp cascade in
    towne.c::town_dispatch_key collapses to a
    {name, fn, ends_menu} table. The leave_town arm's
    post-menu cleanup folds into a town_leave wrapper so
    every entry shares the void(void) signature; the
    "this arm ends the menu loop" signal moves to a
    per-entry ends_menu flag.
  - 6c02f98 Merge gfx.c into primitives.c. After A1
    (97ce6c0) lifted gfx.c's hardcoded c(x, y) anchors and
    erase-loop dimensions to data, the file was reduced to
    two short functions doing the same "position + tx" work
    as primitives.c. Moved blitgfx + clr_map_window to
    primitives.c next to render_at, exposed them through
    primitives.h, dropped src/gfx.{c,h}, dropped src/gfx.c
    from the Makefile SRC list, stripped seven `#include
    "gfx.h"` lines from callers (battle / dungeon /
    missions / script / subs / towne / ydarg). One fewer
    translation unit.

Three new patterns introduced / formalized in #16:

  - **Function-pointer dispatch table for keystroke dispatch.**
    The B6/B7 model first cut was an N-arm if/else strcmp
    cascade walked from the {key, cond?, action} data table.
    User pushed back; both refactored to {name, fn} (or
    {name, fn, ends_menu}) function-pointer tables. Going
    forward, lift any new keystroke dispatcher to the
    table-driven shape from the start.

  - **screen: subtree + screen_goto helper.** Named regions
    in data/world.json5 screen:, addressed by enum, fronted
    by screen_goto(SCR_REGION) and screen_goto_map_row(row)
    helpers in primitives.c. Replaces every literal c(49, 6)
    / c(0, 0) / c(8, N) site. New regions go: enum value in
    res.h, JSON5 record in world.json5, callsite swap.

  - **Default Makefile carries the strict warning set.** The
    flags -Wshadow / -Wstrict-prototypes / -Wmissing-prototypes
    / -Wpointer-arith / -Wundef / -Wcast-align /
    -Wnull-dereference are now part of CFLAGS. Every TU emits
    zero warnings as of 11e68af; the build catches regressions
    going forward.

Status snapshot — every closed §-anchor and the open frontier
post-#16:

  - §12.3 / §20.14 S10/S11 / §12.4 ydarg / §20 L2 / §24bis
    phase-3 / ryl.h sweep / Tier R: all closed (pre-#16).
  - Tier B6 + Tier B9 + Tier C4 (sysop secrets): closed in #15.
  - **Tier 0 (missions.c::mission_triggers[] → JSON5): closed
    in #16** (665004c).
  - **Tier B6 followup + Tier B7 + Tier B7 followup: closed
    in #16** (3fdfae0 / f20f200 / ec53bb7).
  - **Tier A1 (terminal positions → screen:): closed in #16**
    (97ce6c0).
  - **gfx.c retired**; merged into primitives.c (6c02f98).
  - **Strict-warnings cleanup landed**; default build is
    zero-warning (11e68af).

Open frontier (port-plan-incomplete.md "Recommended next
session" Tier A / B / C / D / E, minus what's closed):

  - **Tier A (UI layer)** — A2 statusline content (TIME / HUNGRY-
    STARVING-OK time-bucket labels), A3 view/show/show_party
    DSL'ification, A4 create_party UI strings + race/class
    menu bounds derived from res_enum_count.
  - **Tier B (dispatch / cascades)** — B2 equipment-swap UI flows
    (subs.c, ~160 LOC across 4 near-identical bodies), B4
    speak.c chitchat 3-way switch, B5 speak.c prose strings,
    B8 battle combat-key dispatch (multi-context — needs
    scoping decisions before lift; F/R prompts vs U/D/A/B/C
    menus vs A/B/D combat actions).
  - **Tier C (engine state with hardcoded shape)** — C1 exp_check
    sub-items (4: add[5] cap, max-level 28, pc_class_key map,
    hp_floor formula), C2 encounter pic count, C3
    food_handler fatigue ladder, C5 save-file emit shape.
  - **Tier D (structural cap-flips)** — D1 struct party.flag[40].
    D2 NPC_SLOT_HARD_MAX and D3 TOWN_SLOT_HARD_MAX deferred
    (RNG-identity gated).
  - **Tier E (script-side audit)** — E1 repeat-pattern collapse,
    E2 dungeon.json5 refinements.

Memory worth re-reading (auto-loaded into context):

  - feedback_state_abstraction.md — pa_*/pm_*/inv_*/res_*
    helpers, never raw state.p[...].
  - feedback_background_agents.md — for background agents in
    this repo, /tmp + patch OR worktree isolation. Session #16
    used worktree isolation successfully (after two
    permission-denied attempts on plain /tmp clone — the
    sub-agent permission gate is tighter for fresh clones than
    for worktrees in this setup). Either pattern is now
    validated.
  - feedback_clangd_real_vs_stale.md — verify clangd via
    `cc -fsyntax-only -Werror=...` before deflecting. Stale
    diagnostics from /tmp agent clones / worktrees consistently
    leak into the workspace's clangd view; restart clangd if
    it recurs.
  - feedback_replay_knobs_centralized.md — new test-only env
    knobs go in src/replay_knobs.{c,h} as rk_* helpers; never
    inlined.
  - feedback_script_text_wiring.md — new shards need world.json5
    $import + matching top-level shape.

Reading order in repo:

  - port-plan.md TOC.
  - port-plan-incomplete.md "Top of queue" + the Tier 0 / A /
    B / C / D / E breakdown. (Plan-refresh commit not yet
    landed for #16; the lifts above are complete but the
    incomplete-plan markers haven't been updated. Worth a
    plan-refresh commit early in #17 before starting new
    work.)
  - port-plan-complete.md — bodies for all closed §-anchors.

Per-session bar: `make check` + `make testasan` + `make format`
all green at every commit. Per AGENTS.md: never push without
explicit user approval; no Co-Authored-By lines; inline
single-newline file endings; fix root causes, not symptoms;
don't add speculative changes.

Begin: pick from the post-#16 queue.

  Default pick: **plan refresh + Tier B2 (equipment-swap UI
  flows)**. Plan-refresh first (mark Tier 0 / A1 / B6 followup
  / B7 / B7 followup closed in port-plan-incomplete.md, add a
  "Closed since the previous snapshot (2026-04-29 #16)"
  entry). Then B2: subs.c::weaponoff / armouroff / weaponr /
  armourr are four near-identical 40-LOC bodies that should
  collapse to a shared subs.equipment.swap DSL script with
  bindings ({slot, side, kind}); ~160 LOC saved.

  Alternative: **Tier A2 + A3 + A4** (one session's worth of
  UI-string / time-bucket / race-class-menu-count cleanup).
  All small, all in driv.c, all pure content.

  Alternative: **Tier B4 + B5** (speak.c chitchat + prose).
  Small bundle, clean-up rather than big lift. Closes the
  speak.c residuals.

  Alternative: **Tier B8 (battle combat-key dispatch)** — the
  one big remaining keystroke dispatcher. Needs scoping first
  (battle.c has multiple distinct context-aware keypress
  prompts: F/R fight-or-run, U/D/A/B/C use-item / defend /
  attack menus, A/B/D mid-combat actions). Decide whether
  each lifts as a separate `combat_keys_<context>:` table or a
  single `combat_keys:` with a `context:` filter, then lift.

Same content is in next.md (committed in this session) so the
next session auto-picks it up.
