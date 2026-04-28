Continuation prompt (start of session 2026-04-28 #12)

Branch lagergren/port1, HEAD 6ba194c, pushed to origin. 44 replay
fixtures green on make check + make testasan; format clean.
Working tree clean. Session #11 closed ten commits
(467e4c6..6ba194c):

  - 467e4c6 §12.3: state.p[26] triple cap-flip — PARTY_SLOT_HARD_MAX
    + new res_party_slot_max() runtime accessor (world.json5
    limits.party_slot_max knob, clamped). Replaces raw `26` literals
    across struct person.visited / struct party.attack_today /
    pm_cur assert / driv.c create_party gate / driv.c save+load
    attack_today width arg / subs.c::scorelist seven local arrays.
  - d374c8d §12.3: map[42][82] cap-flip — MAP_ROWS_HARD_MAX /
    MAP_COLS_HARD_MAX defines in state.h; wld.c WLD_ROWS / WLD_COLS
    sourced from them; replay_knobs.c bounds check uses them too.
  - 93548c4 fixture: ydarg-arrow-keys covers ydarg.c ESC[A/B/C/D
    translation. Mirrors ydarg-confrontation but pipes ESC[C/B/etc
    instead of `66662222`. .expect / .expect_snapshot byte-identical
    to the digit fixture. 43 → 44 fixtures.
  - c758874 ydarg agent 1/4: silverkey gate → MG_SPECIAL_EQ
    triggers. New mission-trigger guard kind combining tile-at-pos
    with pa_special() equality. The two early-return arms at the
    top of ydarg() are gone; trigger table now dispatches to
    ydarg.ruin / ydarg.no_key / ydarg() based on silverkey value.
  - fa7681d ydarg agent 2/4: intro narration → ydarg.intro DSL
    script.
  - f061616 ydarg agent 3/4: confrontation() → ydarg.confrontation
    DSL script + 3 new engine_call backers (ydarg_run_seq /
    ydarg_record_completion / ydarg_finalize) + new {narrate:}
    DSL op wrapping tx_narrate.
  - 914b8c1 ydarg agent 4/4: case 1 (monster room) →
    ydarg.room_monster script. src/ydarg.c 456 → 423 lines.
  - 3af207b §12.3: heap-allocate state.p[] off res_party_slot_max().
    Drops the BSS-static `struct party p[PARTY_SLOT_HARD_MAX]` from
    struct ryl_state. Mirrors state.npc[] heap-alloc pattern.
  - eb999d1 §12.3: heap-allocate state.map[][] off
    MAP_ROWS_HARD_MAX/MAP_COLS_HARD_MAX. char (*map)[MAP_COLS_HARD_MAX]
    heap-allocated to MAP_ROWS_HARD_MAX rows. Outer dim only — row
    width stays compile-time so `sizeof state.map[y]` still resolves
    to one row.
  - 6ba194c plan: 2026-04-28 #11 snapshot refresh in
    port-plan-incomplete.md.

Static caps remaining after #11: only the per-party byte arrays —
struct party.attack_today[PARTY_SLOT_HARD_MAX] and struct
person.visited[PARTY_SLOT_HARD_MAX]. Outer state.p[] / state.map[][] /
state.npc[] / towns[] are all heap-backed.

Pick one of these for #12. Listed in priority order from
port-plan-incomplete.md "Recommended next session":

1. §12.3 follow-up — flip the inner per-record byte arrays.
   struct party.attack_today + struct person.visited heap-allocate
   off res_party_slot_max() per record. Touches party_realize /
   res's NPC realize + driv.c save/load width args (currently
   PARTY_SLOT_HARD_MAX, would become res_party_slot_max()). Save
   format byte-identical at default cap; only changes if a world
   ever raises the cap. Unlocks limits.party_slot_max > 26 — the
   last static cap blocking dynamic sizing.

2. Ydarg DSL lift continuation (2-3 sessions remaining). Cases 2
   (pitfall), 3 (treasure), 50 (stairs) + the per-floor while(1)
   navigation driver + the character-command dispatch
   (T/P/L/W/R/Q/Z/V during ydarg movement). The agent's report
   flagged the blocker: function-local state (xl, yl, lvl,
   room[][]) needs hoisting to module-level OR threaded through
   new DSL state primitives like ydarg_consume_room_at /
   ydarg_set_lvl before any of those cases can move cleanly. Pick
   this up by deciding the hoist shape first, then case 2 / 3 / 50
   fall in sequence.

3. §20.14 S10 — map dimensions to data. With state.map[][]
   heap-allocated, the natural follow-up: replace MAP_ROWS_HARD_MAX
   / MAP_COLS_HARD_MAX with `world.json5 map.rows_max` /
   `map.cols_max` reads, sized at calloc time. Loosens the 42/82
   to a per-realm knob.

4. §20.14 S11 — formula DSL with binding maps (deferred at S11 in
   §20.14, last touched 2026-04-25). res_formula_int extension to
   accept a binding map (or res_eval_expr_with(name, bindings, n)).
   Required before lifting battle.c reward / encounter formulas to
   data; pattern then extends to dungeon.c chest-gold, hunt fdplus,
   subs.c starve damage, etc.

5. Smaller polish items (any-time, no sequencing constraint):
   - subs.c data-side temp-damage: `temp_low_damage: 5` and
     `temp_very_low_damage: 5` in data/world.json5 are intentionally
     captured as data but identical (matches plan §20.5's "possible
     bug" note from a year ago — confirmed in #11). Whether the
     very-low arm should hit harder is a balance/design question;
     would rebless wilderness-camp snapshot.
   - Optional: extend `class:` to unclassified terrain entries
     ('s' / 'f' / 'd' / 't' / 'g') if a callsite ever needs them.
     Currently none does.

Memory worth re-reading (auto-loaded into context):
  - feedback_state_abstraction.md — pa_*/pm_*/inv_*/res_* helpers,
    not raw state.p[...]. Critical for #1.
  - feedback_background_agents.md — for any background work,
    /tmp + patch, never worktree isolation. The #11 ydarg agent
    used this pattern correctly (4 commits + a clean patch).
  - feedback_clangd_real_vs_stale.md — verify clangd via cc
    -Werror=... before deflecting to "stale cache". #11 saw stale
    pm_lv / pm_hp / pm_cl warnings throughout that were post-#8
    rename leftovers.
  - project_per_member_shape.md — struct party_member AoS shape;
    full-word fields; offsetof dispatch.

Reading order in repo:
  - port-plan.md TOC — section index, status column.
  - port-plan-incomplete.md "Top of queue (status snapshot,
    2026-04-28 #11)" + "Recommended next session".
  - port-plan-complete.md — bodies for the #10 closures (§-anchor
    cross-references still resolve). #11 commit-message bodies
    will move here on next session-end sweep.

Per-session bar: make check + make testasan + make format all
green at every commit. Per AGENTS.md: never push without explicit
user approval; no Co-Authored-By lines; inline single-newline file
endings; fix root causes, not symptoms; don't add speculative
changes.

Begin by reading port-plan-incomplete.md "Top of queue" and
"Recommended next session", then ask which item to take, or pick
#1 (inner per-record byte arrays heap-allocate) as the default
since it closes §12.3 entirely.
