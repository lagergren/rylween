Continuation prompt (start of session 2026-04-30 #18)

Branch lagergren/port1, HEAD 57c2ba1, pushed to origin post-#17.
44 replay fixtures green on `make check` + `make testasan`;
`make format` clean; `tools/check_world.sh` green. Default
Makefile carries the strict warning set (-Wshadow / -Wstrict-
prototypes / -Wmissing-prototypes / -Wpointer-arith / -Wundef /
-Wcast-align / -Wnull-dereference) and the build emits zero
warnings.

Session #17 added 4 commits on lagergren/port1 (b2d0c21..57c2ba1).
The default-pick path from #17's continuation prompt landed end-
to-end:

  - b2d0c21 plan: refresh port-plan-incomplete.md with #16
    closures. Tier 0.1 / A1 / B6 followup / B7 + B7 followup
    struck through under the recommended-next-session catalog;
    new "Closed since the previous snapshot (2026-04-29 #16)"
    entry above the existing #15 entry covering the eight commits
    3fdfae0..6c02f98 and the three new patterns formalized in #16
    (function-pointer dispatch tables / screen: subtree / strict
    default Makefile). Pick order rewritten post-#16.
  - 5c082f1 DSL: `field:` arg accepts `{var: 'name'}` runtime
    references. expr_member_field (script.c:528-548) and
    parse_lv's LV_MEMBER_FIELD branch (script.c:2755-2776) both
    required field: to be N_STR — that blocks one-script-per-pair
    lifts where a kind binding from the call site should pick
    weapon vs armor. New helper resolve_field_name(node, ...)
    accepts either an N_STR literal or an N_OBJ {var: 'name'}
    reference; the var: form looks up the binding via env
    (RT_STR) or ryl_bind list (non-numeric value) and dies if
    unresolved. Both call sites route through it.
  - bc5ba01 script: three new engine_ints wired into
    int_engine_tbl: inv_slot_from_letter(letter) -> 1..N or 0
    (wraps state.h helper); item_is_weapon(id) -> 0/1 (wraps
    inventory.h); item_is_armor(id) -> 0/1. The first pairs with
    the existing key_upper engine_int to power letter-prompted
    inventory selection; the latter two are kind-aware predicates
    used by the upcoming B2 lift.
  - 57c2ba1 Tier B2: subs.c equipment-swap UI flows lifted to
    data/world/scripts/subs.json5. Four near-identical C bodies
    (weapon_off / armor_off / weapon_ready / armor_ready, ~38-40
    LOC each) collapse to two shared DSL scripts parameterized by
    a `kind` binding:
      - subs.equipment.unequip — replaces weapon_off + armor_off
        bodies. The kind binding picks the pm_field row (via the
        resolve_field_name DSL extension from 5c082f1) and the
        text-record suffix (subs.equipment.no_<kind> via
        emit_indexed). One body covers both.
      - subs.equipment.equip — replaces weapon_ready + armor_ready
        bodies. Bindings: { kind, kind_id: '1' | '2' }. kind_id
        is the int alias used for the item_is_equip_kind dispatch
        (DSL `eq` is int-only; routing through item_is_equip_kind
        avoids a string-eq op). New helper item_is_equip_kind
        (item_id, kind_id) added to int_engine_tbl alongside the
        lift; dispatches kind_id 1 -> item_is_weapon, 2 ->
        item_is_armor, default -> 0.
    The four C functions become 2-line play_script trampolines.
    Text records in data/world/texts/subs.json5 reorganized
    (weapon_off.no_weapon -> equipment.no_weapon;
    armor_off.no_armor -> equipment.no_armor;
    weapon_ready.{invalid,changed} ->
    equipment.{invalid,changed}_weapon; same for armor_ready).
    {pronoun} / {object} bindings come from existing engine_binds.
    {party_max} in subs.who_prompt resolves via primitives.c's
    auto-binding. ydarg.json5's four engine_calls (weapon_off /
    armor_off / weapon_ready / armor_ready) route through the
    trampolines transparently — caller-visible ABI unchanged.
    src/subs.c: 870 -> 737 LOC (-133); new DSL shard 166 LOC
    (with comments).

Three new patterns introduced / formalized in #17:

  - **Runtime field-name resolution in member_field DSL ops.**
    `field:` accepts either an N_STR literal (the historical
    shape) or `{var: 'name'}` resolving to a non-numeric string
    binding. Lets one script body cover multiple pm_field rows
    with a single kind binding from the call site. Pattern
    extends naturally to other field-keyed ops if a future lift
    needs them (battle_flag, etc.).

  - **Kind-paired bindings (kind + kind_id) for DSL int-vs-string
    dispatch.** DSL `eq` is int-only and `expr_var` dies on non-
    numeric string-bound values, so a script that needs to
    dispatch on a slug (weapon vs armor) gets two bindings: the
    slug `kind` for emit_indexed / member_field, and an int alias
    `kind_id` for the dispatcher engine_int. The dispatcher
    engine_int (item_is_equip_kind in B2's case) does the actual
    weapon/armor branch in C. Trampoline passes both:
        play_script("subs.equipment.equip",
                    (ryl_bind[]){{"kind", "weapon"},
                                 {"kind_id", "1"}, {0}});

  - **Trampoline shape for C-side wilderness/town keystroke
    handlers post-DSL-lift.** weapon_off / armor_off / weapon_
    ready / armor_ready follow the rumour() shape from #13b: a
    2-line play_script call into the data/world/scripts/<file>
    shard. Other wilderness_keys[] / town_keys[] entries that
    still own significant C-side bodies are good candidates for
    the same shape.

Status snapshot — every closed §-anchor and the open frontier
post-#17:

  - §12.3 / §20.14 S10/S11 / §12.4 ydarg / §20 L2 / §24bis
    phase-3 / ryl.h sweep / Tier R: all closed (pre-#16).
  - Tier B6 + Tier B9 + Tier C4 (sysop secrets): closed in #15.
  - Tier 0 (mission_triggers[] → JSON5): closed in #16.
  - Tier B6 followup + Tier B7 + Tier B7 followup: closed in #16.
  - Tier A1 (terminal positions → screen:): closed in #16.
  - gfx.c retired; merged into primitives.c (#16).
  - Strict-warnings cleanup: closed in #16.
  - **Tier B2 (subs.c equipment-swap UI flows): closed in #17**
    (57c2ba1).
  - **DSL extension — `field:` accepts {var:'name'}: closed in
    #17** (5c082f1). Dependency for any future lift that wants
    one-script-per-pair shape.

Open frontier (port-plan-incomplete.md "Recommended next
session", minus what's closed):

  - **Tier A (UI layer)** — A2 statusline content (TIME / HUNGRY-
    STARVING-OK time-bucket labels), A3 view/show/show_party
    DSL'ification, A4 create_party UI strings + race/class menu
    bounds derived from res_enum_count.
  - **Tier B (dispatch / cascades)** — B4 speak.c chitchat 3-way
    switch, B5 speak.c prose strings, B8 battle combat-key
    dispatch (multi-context — needs scoping decisions before lift;
    F/R prompts vs U/D/A/B/C menus vs A/B/D combat actions).
  - **Tier C (engine state with hardcoded shape)** — C1 exp_check
    sub-items (4: add[5] cap, max-level 28, pc_class_key map,
    hp_floor formula), C2 encounter pic count, C3 food_handler
    fatigue ladder, C5 save-file emit shape.
  - **Tier D (structural cap-flips)** — D1 struct party.flag[40].
    D2 NPC_SLOT_HARD_MAX and D3 TOWN_SLOT_HARD_MAX deferred (RNG-
    identity gated).
  - **Tier E (script-side audit)** — E1 repeat-pattern collapse,
    E2 dungeon.json5 refinements.

Coverage gap to be aware of:

  - **B2 fixture coverage**: none of the 44 fixtures exercise the
    P / T / W / R wilderness keystrokes that route through the
    new equipment.unequip / equipment.equip scripts. The lift's
    runtime behavior is validated against the C bodies via code
    review only; the next-session candidate "wilderness-
    equipment-swap" fixture (analogous in shape to wilderness-
    camp / wilderness-hunt) would gate the new path. Smallest
    viable: weapon_off on a member with no weapon (early-return
    path, no inventory state needed beyond a default party).

Memory worth re-reading (auto-loaded into context):

  - feedback_state_abstraction.md — pa_*/pm_*/inv_*/res_*
    helpers, never raw state.p[...].
  - feedback_background_agents.md — for background agents in
    this repo, /tmp + patch OR worktree isolation. Both patterns
    are validated; #16 used worktree isolation successfully.
  - feedback_clangd_real_vs_stale.md — verify clangd via
    `cc -fsyntax-only -Werror=...` before deflecting. The post-
    gfx-merge stale gfx.h diagnostics in #17 were a textbook
    example — reported as new but invalidated by a clean build.
  - feedback_replay_knobs_centralized.md — new test-only env
    knobs go in src/replay_knobs.{c,h} as rk_* helpers.
  - feedback_script_text_wiring.md — new shards need world.json5
    $import + matching top-level shape (the subs.json5 shard
    added in #17 follows this — `subs: { $import: ... }` next to
    rest / lodge / ydarg).

Reading order in repo:

  - port-plan.md TOC.
  - port-plan-incomplete.md "Top of queue" (snapshot date now
    2026-04-29 #16) + the Tier 0 / A / B / C / D / E breakdown.
    Plan-refresh commit b2d0c21 marked Tier 0.1 / A1 / B6
    followup / B7 + B7 followup closed; Tier B2 still shows as
    open (close it as part of the #18 plan-refresh).
  - port-plan-complete.md — bodies for all closed §-anchors.

Per-session bar: `make check` + `make testasan` + `make format`
all green at every commit. Per AGENTS.md: never push without
explicit user approval; no Co-Authored-By lines; inline single-
newline file endings; fix root causes, not symptoms; don't add
speculative changes.

Begin: pick from the post-#17 queue.

  Default pick: **plan refresh + Tier B4 + B5 (speak.c
  chitchat + prose)**. Plan-refresh first (mark Tier B2 closed
  in port-plan-incomplete.md, add a "Closed since the previous
  snapshot (2026-04-29 #17)" entry covering b2d0c21..57c2ba1
  with the three new patterns). Then B4: speak.c::speak chitchat
  C-key 3-way switch (lines 201-220) → speak.chitchat_subject
  DSL script that does the random pick + emit; bundle B5
  (speak.c "%s nods as" / "%s tells you:" prose lines 161, 191
  → speak.recognize_pre / speak.tells_you_pre text records).
  Closes the speak.c residuals; small bundle, well-defined.

  Alternative: **Tier A2 + A3 + A4** (one session's worth of UI-
  string / time-bucket / race-class-menu-count cleanup). All
  small, all in driv.c, all pure content. Bundles cleanly.

  Alternative: **B2 fixture coverage** — add wilderness-equipment-
  swap (no-weapon early-return path, default party state). Small,
  closes the only soft spot from #17. Could ride along with any
  other Tier B/A pick.

  Alternative: **Tier B8 (battle combat-key dispatch)** — the one
  big remaining keystroke dispatcher. Needs scoping first: battle.c
  has multiple distinct context-aware keypress prompts (F/R fight-
  or-run, U/D/A/B/C use-item / defend / attack menus, A/B/D mid-
  combat actions). Decide whether each lifts as a separate
  `combat_keys_<context>:` table or a single `combat_keys:` with a
  `context:` filter, then lift. Larger session; bring scoping
  notes to the user before committing.

  Alternative: **Tier C1 (battle.c::exp_check level-up
  dispatcher)**. Four content-in-C items in one function: add[5]
  cap (-> res_party_member_max() + 1), max-level 28 (-> data
  knob), pc_class_key[] int->slug map (-> res_enum_sym call),
  hp_floor formula (-> named formula). Session-sized; touches
  formulas/world.json5 + battle.c.

Same content is in next.md (committed in this session) so the
next session auto-picks it up.
