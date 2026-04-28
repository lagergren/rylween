Continuation prompt (start of session 2026-04-28 #13)

Branch lagergren/port1, HEAD a70feab (plus an unpushed plan + this
next.md commit landing at session-end). 44 replay fixtures green on
make check + make testasan; format clean. Working tree clean.
Session #12 closed two commits (16e0ee3..a70feab):

  - 16e0ee3 §12.3: heap-allocate inner per-record byte arrays off
    res_party_slot_max(). struct party.attack_today and struct
    person.visited flip from `[PARTY_SLOT_HARD_MAX]` inline arrays
    to `unsigned char *` heap pointers. attack_today via
    party_realize / party_release_members / party_clear_heap_pointers
    (sized to res_party_slot_max()). visited via per-NPC alloc loop
    inside res_load_world right after `state.npc = calloc(...)`,
    and a symmetric free loop at the head of res_shutdown (runs
    before npc_slot_max_invalidate so for_each_npc still resolves).
    npc_visited / npc_visited_set asserts swap from
    `sizeof n.p->visited` (was 1, became 8 after the pointer flip)
    to `res_party_slot_max()`. driv.c's two save/load width args
    (PARTY_SLOT_HARD_MAX → res_party_slot_max()). 44 fixtures
    byte-identical at the default cap (26 == PARTY_SLOT_HARD_MAX);
    save format only changes if a future world raises
    limits.party_slot_max past it. Closes §12.3 cap-flip queue on
    the storage side.
  - a70feab clangd unused-includes follow-up — compat.c, primitives.c.
    Same shape as 7671ea5 / 83814b7 from earlier sessions. compat.c
    drops `ryl.h` (only depends on compat.h + runtime_cfg.h's
    rcfg_get); pulls `<assert.h>` directly to replace what the now-
    gone ryl.h chain transited. primitives.c drops `ryl.h` and pulls
    `compat.h` directly so ryl_random / stricmp resolve. ryl.h now
    keeps only its real users: battle.c / dungeon.c / subs.c /
    towne.c / ydarg.c (each reaches for the pic constants) plus
    ryl.c (defines _version_string).

Static-cap landscape after #12: every party-slot-indexed BUFFER is
heap-allocated off res_party_slot_max(). What still ties the data
knob to the compile-time PARTY_SLOT_HARD_MAX is the on-stack
locals + bounds checks: subs.c::scorelist seven local arrays
(pts/lvl/kills/pname/lname/cls/item/pnr/temp_ubyte all sized to
PARTY_SLOT_HARD_MAX); party.c::party_at's range check;
state.h:303 `assert(state.cpl < PARTY_SLOT_HARD_MAX)`; snapshot.c's
party-slot guard; and the fold_limits clamp itself
(`psm->u.iv > PARTY_SLOT_HARD_MAX → res_die`). Until those flip,
limits.party_slot_max can't go above 26 in practice.

Pick one of these for #13. Listed in priority order from
port-plan-incomplete.md "Recommended next session":

1. Ydarg DSL lift continuation (2-3 sessions remaining). Cases 2
   (pitfall), 3 (treasure), 50 (stairs) + the per-floor while(1)
   navigation driver + the character-command dispatch
   (T/P/L/W/R/Q/Z/V during ydarg movement). The #11 agent's report
   flagged the blocker: function-local state (xl, yl, lvl,
   room[][]) needs hoisting to module-level OR threaded through
   new DSL state primitives like ydarg_consume_room_at /
   ydarg_set_lvl before any of those cases can move cleanly. Pick
   this up by deciding the hoist shape first, then cases 2 / 3 / 50
   fall in sequence. Last open piece from §12.4 ydarg residual.

2. §20.14 S10 — map dimensions to data. With state.map[][]
   heap-allocated (since #11), the natural follow-up: replace
   MAP_ROWS_HARD_MAX / MAP_COLS_HARD_MAX with world.json5 map.rows_max
   / map.cols_max reads, sized at calloc time. Type-shape catch:
   state.map's current type is `char (*map)[MAP_COLS_HARD_MAX]` —
   the inner row width is type-level, so making cols dynamic
   requires either (a) flat 1D buffer with manual stride threaded
   through every wld.c access site, or (b) flexible-array-member
   trick. All current map accesses already go through wld.c
   helpers, so the change is localized — wld_tile_at, wld_set_tile_at,
   wld_load_row are the only sites needing the stride argument.

3. §12.3 final cap-flip — limits.party_slot_max > 26 unlock
   (post-#12 follow-up). To make the data knob actually accept >26,
   lift the remaining PARTY_SLOT_HARD_MAX-shaped sites:
   subs.c::scorelist's seven on-stack arrays (pts/lvl/kills + the
   four [N][30] / [N][5] / [N][6] strings + pnr + temp_ubyte) →
   heap-allocate off res_party_slot_max(); the bounds checks in
   party.c::party_at and snapshot.c → flip to res_party_slot_max();
   state.h:303 cpl assert → same; fold_limits clamp → drop the
   upper-bound res_die. Niche unlock — no fixture exercises >26
   today — but it closes the conceptual loop.

4. §20.14 S11 — formula DSL with binding maps (deferred at S11 in
   §20.14, last touched 2026-04-25). res_formula_int extension to
   accept a binding map (or res_eval_expr_with(name, bindings, n)).
   Required before lifting battle.c reward / encounter formulas
   to data; pattern then extends to dungeon.c chest-gold, hunt
   fdplus, subs.c starve damage, etc.

5. Smaller polish items (any-time, no sequencing constraint):
   - subs.c data-side temp-damage: `temp_low_damage: 5` and
     `temp_very_low_damage: 5` in data/world.json5 are intentionally
     captured as data but identical (matches plan §20.5's "possible
     bug" note from a year ago). Whether the very-low arm should
     hit harder is a balance/design question; would rebless
     wilderness-camp snapshot.
   - Extend `class:` to unclassified terrain entries ('s' / 'f' /
     'd' / 't' / 'g') if a callsite ever needs them. Currently none
     does.

Memory worth re-reading (auto-loaded into context):
  - feedback_state_abstraction.md — pa_*/pm_*/inv_*/res_* helpers,
    not raw state.p[...]. Critical for any new C touching parties.
  - feedback_background_agents.md — for background agents in this
    repo, /tmp + patch, NEVER worktree isolation. The #11 ydarg
    agent used this pattern correctly (4 commits + clean patch).
  - feedback_clangd_real_vs_stale.md — verify clangd via cc
    -Werror=... before deflecting. #12 saw the same stale pm_lv
    / pm_hp / pm_cl warnings (post-#8 rename leftovers); the
    compat.c / primitives.c [unused-includes] hits, by contrast,
    were real and got swept (a70feab).
  - project_per_member_shape.md — struct party_member AoS shape;
    full-word fields; offsetof dispatch.

Reading order in repo:
  - port-plan.md TOC — section index, status column.
  - port-plan-incomplete.md "Top of queue (status snapshot,
    2026-04-28 #12)" + "Recommended next session".
  - port-plan-complete.md — bodies for the #10 closures
    (§-anchor cross-references still resolve). #11 + #12 commit-
    message bodies move here on next session-end sweep.

Per-session bar: make check + make testasan + make format all
green at every commit. Per AGENTS.md: never push without explicit
user approval; no Co-Authored-By lines; inline single-newline file
endings; fix root causes, not symptoms; don't add speculative
changes.

Begin by reading port-plan-incomplete.md "Top of queue" and
"Recommended next session", then ask which item to take, or pick
#1 (Ydarg DSL lift continuation) as the default since it's the
last open piece of §12.4 and the #11 agent left a clear hand-off
in its report. If #1 is too design-heavy for the session length,
fall through to #2 (map dims to data — concrete and localized).
