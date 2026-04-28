Continuation prompt (start of session 2026-04-28 #14)

Branch lagergren/port1, HEAD e7cbd71 (plus an unpushed plan + this
next.md commit landing at session-end). 44 replay fixtures green
on make check + make testasan; format clean. Working tree clean.

Session #13 was massive (twenty commits cumulative,
`376f6bf..e7cbd71`). #13 closed §12.3 fully + §20.14 S10/S11 +
ydarg DSL lift end-to-end + time_left data lift; #13b closed
every item from #13's recommended-next-session queue (#1..#5):

  - 855c138 WLD_LOCATION_SLOT_MAX / RANDOM_LOCATION_MAX cap-flip.
    state.dx[] / state.dy[] flip from inline `int32_t [15]` to
    heap pointers off res_location_slot_max(). New
    `limits.location_slot_max` (default 15) +
    `limits.random_location_max` (default 9) knobs in world.json5.
    The wld.h enum constants retired entirely. Smoke-tested with
    location_slot_max=20 / random_location_max=12. **No struct
    ryl_state field is sized to a compile-time constant after
    this commit.**

  - fdb3d42 subs.c::spell: priest-spell list bound from data. New
    res_class_spell_max(class_name) walks the priest_spells /
    mage_spells tables; the loop bound + abort-key follow it.

  - 21c9821..c7454ef rumour: 33-case switch → DSL (background
    agent, 2 commits). New `rumour_roll` engine_int (preserves
    RYL_REPLAY_RUMOUR semantics + the same RNG draw). New
    towne.rumour script with emit_indexed default arm + 4 bound
    arms reusing existing npc_name_at_role / npc_loc_at_role
    helpers. **rumour() is a 3-line trampoline** down from 64
    lines. The agent dropped runtime_cfg.h from towne.c too.

  - fdcae1d temp_low_damage / temp_very_low_damage doc
    clarification. Settles port-plan §20.5's "possible bug"
    tracking — it's authored-equal-by-design.

  - e7cbd71 §20 L2 closure (background agent's findings). The
    L2 lift was already finished by `fc599b3` on 2026-04-25 — 29
    cases all in DSL, src/dungeon.c is 182 LOC of pure dispatch.
    Survey at /tmp/dungeon-l2-survey.md (kept outside the repo).
    **Two latent bugs caught and fixed inline**:
    (a) `enums.dungeon_encounter[1]` was 'object' but the script
        is `find_object` → dispatcher would have died on slot 1
        (unreachable in fixtures because seed 42 rolls slot 2).
    (b) The script's `emit: 'find_object.found'` etc. had leading
        dots that bypassed the <script_id>.<name> two-step,
        rendering as blank screens. Stripped the redundant
        prefix.

Status after #13 + #13b — every named §-anchor in the prior
queue is now closed:

§12.3 cap-flip queue: **fully closed**. Every party-slot-indexed
buffer + map grid + state.dx/dy is heap-allocated off res_*_max();
fold_limits clamps to `>= 2` only. PARTY_SLOT_HARD_MAX,
MAP_*_HARD_MAX are defaults / defensive ceilings, not active caps.

§20.14: S10 done, S11 done.

§12.4 ydarg residual: **fully closed**. ydarg() is a one-line
trampoline.

§20 L2 dungeon-encounter lift: **fully closed** (was already
done; this session confirmed + fixed two latent bugs).

ryl.h sweep: complete. Files including ryl.h all genuinely use
pic constants (battle / dungeon / subs / towne) or define
_version_string (ryl.c).

`feedback_battle_data_extraction.md` queue: fully closed.

The recommended-next-session queue is now genuinely greenfield.
Pick from the deeper backlog or new user-driven asks:

1. **Magic terminal positions (`c(x, y)` in driv.c / gfx.c)**.
   Per `feedback_battle_data_extraction.md` — `c(49, 6)`,
   `c(13, 22)`, `clr_map_window`'s 8×9 grid, etc. are layout
   constants tied to ANSI art. Could lift to `data/world.json5`
   `screen:` subtree. Multi-callsite sweep across driv.c +
   gfx.c + primitives.c. Session-sized.

2. **`struct party.flag[40]` cap-flip**. Last hardcoded
   byte-array on struct party. Sized to 40 mission-flag slots;
   `world.json5 limits.party_flag_max` could drive it. Would
   need careful save-format coordination since flags are
   persisted by slot id.

3. **NPC_SLOT_HARD_MAX cap-flip** (gated on RNG identity).
   Lifting the static 41 to data requires re-rolling random NPC
   placement RNG identity (different bin counts shift the
   stream); every fixture using random NPC arrangement would
   need a rebless. Defer until > 41 NPCs has a real demand.

4. **TOWN_SLOT_HARD_MAX cap-flip** (also RNG-identity gated).
   `int counts[TOWN_SLOT_HARD_MAX]` in res_realize_npcs is a
   small heap-alloc lift, but bumping the cap shifts the random
   NPC town-bin draw. Defer.

5. **L2 — dungeon.c residual scripting refinements**. The 28
   scripts in dungeon.json5 have been live since 2026-04-25 but
   aren't audited for code-as-data opportunities (inline RNG
   ranges that could become formulas, duplicated emit patterns
   that could collapse to shared sub-scripts). Worth a survey
   if a fresh extraction agenda surfaces.

6. **Survey data/world/scripts/*.json5 for repeat-pattern
   collapse**. Now that ydarg + dungeon + speak + towne are all
   DSL-resident, recurring snippets (e.g. "ask_target →
   cost-check → mutate → emit-result" cascades) might benefit
   from new shared DSL ops. Low-pressure cleanup.

Memory worth re-reading (auto-loaded into context):
  - feedback_state_abstraction.md — pa_*/pm_*/inv_*/res_* helpers,
    never raw state.p[...]. Critical for any new C touching parties.
  - feedback_background_agents.md — for background agents in this
    repo, /tmp + patch, NEVER worktree isolation. Sessions #11
    (4 commits), #13 (8 commits ydarg), #13b (2 + 1 commits
    rumour + L2) all used this pattern; all applied cleanly via
    git am.
  - feedback_clangd_real_vs_stale.md — verify clangd via cc
    -fsyntax-only -Werror=... before deflecting. #13b saw clangd
    hallucinate references to functions that didn't exist in the
    file (the agent's WIP /tmp checkout was leaking diagnostics
    into the workspace's view).
  - feedback_battle_data_extraction.md — catalog CLOSED after
    #13 (last item was time_left = 175). Keep the memory as
    historical reference; new extractions will need a fresh
    catalog.

Reading order in repo:
  - port-plan.md TOC.
  - port-plan-incomplete.md "Top of queue (status snapshot,
    2026-04-28 #13b)" + "Recommended next session".
  - port-plan-complete.md — bodies for §12.3 + §20.14 + Ydarg
    full DSL closures (tail of file).

Per-session bar: make check + make testasan + make format all
green at every commit. Per AGENTS.md: never push without explicit
user approval; no Co-Authored-By lines; inline single-newline
file endings; fix root causes, not symptoms; don't add
speculative changes.

Begin by reading port-plan-incomplete.md "Top of queue" and
"Recommended next session", then ask which item to take. The
queue is greenfield — every item is its own scoped session, no
in-flight work blocks anything. **Default pick: #1 (Magic
terminal positions to data)** — concrete sweep, session-sized,
removes the last bucket of obvious "this should be in data" hits
in the C code. **#2 (party.flag[40] cap-flip)** is the
alternative if you want another structural cap-flip in the
§12.3 family.
