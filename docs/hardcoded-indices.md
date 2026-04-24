# Hardcoded-index cleanup

Living checklist of places in `src/` that still compare or write raw
integer IDs where a symbolic tag should be used instead. Audit captured
post-§17-R (2026-04-24); spot-check the file:line references before
acting — they may have shifted.

## The rule

- **State layer** (`src/state.h`: `struct party`, `struct person`) holds
  raw ints — that matches the save format and stays that way.
- **Translation layer** (`src/res.c`, `src/inventory.c`) owns the
  int↔tag mapping: `item_is_donkey(id)`, `res_enum_id("jobname",
  "Bargirl")`, `res_npc_gender(slot)`, etc.
- **Gameplay layer** (`subs.c`, `battle.c`, `dungeon.c`, `missions.c`,
  `towne.c`, `interact.c`, `speak.c`, `driv.c`) should speak tags only.
  Literal comparisons like `foe == 31`, `inventory[X] == 47`, `flag[23]
  = 7`, `job == 13`, `cl[g] == 3` are the leaks this doc tracks.

## Priority order

- [x] **P1 — Flag-index names** (landed §17-S.1..S.4)
      Flags are now a data dictionary in `data/world.json5` + DSL tag
      strings + `pa_flag_is`/`pa_flag_set` at C sites + a JSON5 map
      at the save/snapshot layer. See "Reference: already-migrated
      patterns" below.
- [x] **P2 — Monster / encounter IDs** (landed §17-T + §17-U)
      `res_monster_id("<symbol>")` + `res_monster_tier_pick("<tier>")`
      added; every `encounter(...)` call in dungeon.c / ydarg.c /
      subs.c now passes either a symbol or a tier. Monster records
      carry a `tier: 'weak' | 'wild' | 'deep'` keyword; wilderness
      easy/hard pools are configured in `world.json5` via
      `wilderness.encounter_{easy,hard}_tier`. Quest/boss/mission
      monsters stay tier-less and are summoned by name only.
- [x] **P3 — Class-ID comparisons** (landed §17-V)
      Six sites migrated to `pm_is_<class>(pm_cur(m))` /
      `pm_is_spellcaster(pm_of(&state.p[a], g))` predicates in
      state.h; no bare `cl[x] == N` left. See the P3 section
      below for details.
- [ ] **P4 — Magic-item case labels in battle.c**
      (16 `case 100..111:` labels; §17-N already flagged these)
- [x] **P5 — NPC slot literals** (landed §17-W)
      Every literal `state.npc[N]` and `res_enum("name", N)` now
      goes through `res_npc_role("<role>")`. Role bindings live in
      `world.json5`'s new `npc_roles:` subtree; C never mentions a
      character's name, and renaming a character or reassigning a
      quest to a different character is a pure data change. See
      the P5 section below.
- [x] **P6 — Job-ID bare comparisons** (landed §17-X)
      speak.c's two `job == N` checks now compare against
      `res_enum_id("jobname", "Bard"|"Bargirl")`. While there,
      the bare `gender == 2` checks in speak.c / driv.c are now
      `res_enum_id("gender", "female")`, and a latent
      `job != 13` being used as a gender proxy at the
      "recognize_he/she" branch has been corrected to a real
      gender check (works identically today because all bargirls
      are female, but wouldn't survive a non-bargirl female NPC).

### P1 follow-ups

- [x] `mission_spec` tables in `src/speak.c` — flipped in §17-Z to
      `.flag_tag = "silver_key", .when_state = "note_found"` shape;
      `.npc_slot` also became `.npc_role`, keyed on world.json5's
      `npc_roles:` subtree, so C names the quest role not the
      character.
- [x] `src/missions.c` mission_trigger table — same shape
      migration (§17-Z). Dispatch uses pa_flag_is.
- [x] mission_binding NPC binds (`MB_NPC_NAME`, `BIND_NPC_NAME`,
      `BIND_NPC_LOC`) — `.arg = <slot>` replaced by
      `.npc_role = "<role>"`, resolved via res_npc_role (§17-Z).
- [ ] The save loader still accepts the legacy `flag: [...]`
      byte-array shape for round-tripping pre-§17-S saves. Once no
      such save exists, that branch can be deleted.

---

## P1 — Flag indices (DONE §17-S.1..S.4)

Landed end-to-end as a four-commit series:

1. **§17-S.1** — extended `data/world.json5` `flags:` with every live
   tag (garth.bandits, ranger.letter, fighter.priest_book, silver_key,
   erethor.lute, bracelets.thun, nymph.seeker, peasant_wife,
   jazzy_ogre, mastercrown, hermit.troll_tooth). Each has a named
   `values:` array covering the observed state machine.
2. **§17-S.2** — flipped every `{ set_flag: N, value: K }` in
   `data/world/scripts/*.json5` to `{ set_flag: '<tag>', value:
   '<state>' }`. DSL extended (`do_set_flag`, `eval_cond`) to accept
   string values via `res_flag_value_id()`; typos now die at load.
3. **§17-S.3** — added `pa_flag_get`/`pa_flag_is`/`pa_flag_set` in
   `src/party.c` and migrated all 18 raw `state.p[cpl].flag[N]` reads
   and writes in `src/speak.c`, `src/dungeon.c`, `src/missions.c`.
4. **§17-S.4** — save format + fixture snapshot now emit
   `flags: { tag: 'state', ... }` (sparse map) instead of a 40-byte
   hex blob. Reader accepts both shapes. Struct layout unchanged
   (still `unsigned char flag[40]`); wire shape is tag-based.

## P2 — Monster / encounter IDs (DONE §17-T + §17-U)

Single-monster summons via symbolic id (§17-T):

- [x] `res_monster_id(const char *sym)` in res.c / res.h
- [x] dungeon.c — 20 sites (guardian, norker, voodoo_lord,
      archdevil, rainbow_demon, long_dead_warrior, hobgoblin,
      hobgoblin_skeleton, undead_hobgoblin, hobgoblin_chief,
      orc x3, erethor_priest x3, erethor x2)
- [x] ydarg.c — 5 sites (crypt_horror, night_gaunt, thyraga,
      liche, baron_ydarg)
- [x] subs.c — 1 site (skagerberg_killer)
- [x] battle.c — no literal call sites (only the `encounter()`
      definition lives here; the doc's earlier count was wrong)

Tier-based pools via `tier` keyword on each monster (§17-U):

- [x] Every monster in `data/world/monsters/monsters.json5`
      tagged `tier: 'weak' | 'wild' | 'deep'` (quest/boss/mission
      monsters stay tier-less so they can only be summoned by
      name).
- [x] `res_monster_tier_pick(const char *tier)` in res.c / res.h;
      fatal on empty tier (silent fallback would bury caller bugs).
- [x] `res_str_path(...)` sibling of res_int_path, so the
      wilderness dispatch can read a tier name straight from
      world.json5.
- [x] dungeon.c:172/178 -> tier 'weak'; dungeon.c:184 -> 'deep'.
- [x] ydarg.c:97 -> 'deep'.
- [x] subs.c:572 (camp ambush) -> 'weak'.
- [x] `wilderness.encounter_{easy,hard}_{lo,hi}` replaced by
      `wilderness.encounter_{easy,hard}_tier` (weak / wild);
      subs.c:175ff rewritten to the tier shape.

## P3 — Class-ID comparisons (DONE §17-V)

Extended the `pm_is_*` predicate family in `src/state.h` (fighter,
thief, bard, ranger joined the existing mage/priest) plus a
`pm_is_spellcaster` convenience for the mage-or-priest branches.
Every bare `cl[x] == N` comparison is gone:

- [x] dungeon.c:212 — thief trap check (chest trap)
- [x] ydarg.c:111 — thief disarm on pitfall room
- [x] battle.c:146 — mage "(C)ast Spell" menu item
- [x] driv.c:1461 — spellcaster mana seed at level-up
- [x] subs.c:653 — spellcaster potion #38 (mana restoration)
- [x] subs.c:688 — `!pm_is_ranger(...)` for hunt command

Each site takes a pm_t via `pm_cur(m)`, `pm_from_digit(c)`, or
`pm_of(&state.p[a], m)` — no direct `.cl[]` reads remain at the
comparison sites.

## P4 — Magic-item case labels (battle.c)

`case 100:` … `case 111:` in battle.c's (U)se-item switch. §17-N
already flagged items 100..104 + 109..111 as the "nine remaining
item-use cases" — part of the battle.c sweep (queue item 4).

- [ ] Fold into battle.c state.p sweep (§17-N queue item 4) —
      rather than dispatch on int, look up the item shard and
      route through its DSL effect body (like reward op / spells
      already do).

## P5 — NPC slot literals (DONE §17-W)

Two layers of indirection:

- `res_npc_slot(const char *shard_key)` — "character name -> slot",
  intended for places where the character's shard key is the actual
  identity (the realizer, tests).
- `res_npc_role(const char *role)` — "quest role -> slot", via a new
  `npc_roles:` subtree in `data/world.json5` that binds each role
  to a character shard key. This is the call C should reach for at
  mission / rumour / quest sites.

Every literal-int `state.npc[N]` and every `res_enum("name", N)` in
the non-iteration path has been migrated:

- [x] towne.c rumour 12 (bandits_quest_giver)
- [x] towne.c rumour 13 (rangers_quest_giver)
- [x] towne.c rumour 25 (crown_seer)
- [x] towne.c rumour 27 (temple_of_thun_seer)
- [x] dungeon.c silver-key note (master_thief)
- [x] speak.c npc_mission_hint — switch collapsed into an if/else
      chain keyed on res_npc_role("<role>") instead of slot-literal
      case labels (fighter_intro / silver_key_vendor /
      temple_of_thun_seer / jazzy_ogre_trader)
- [x] speak.c bard chitchat (fighter_intro)
- [x] speak.c fighter intro's priest_with_book lookup

The remaining `state.npc[i]` sites in driv.c / res.c / towne.c are
all iteration loops (`for (i = 1; i <= res_npc_slot_max(); i++)`)
or computed-slot sites — not slot literals.

Follow-up, not in §17-W (deliberately scoped out):

- Rumour case labels in towne.c (r12/r13/r25/r27 as int) and the
  `if (npc == res_npc_role(...))` chain in speak.c both still do
  C-side dispatch on data IDs. A data-authored "rumour has these
  binds / mission dispatches here" table would collapse both into
  generic iteration. Bigger engine change — separate pass.

## P6 — Job-ID bare comparisons (DONE §17-X)

- [x] speak.c bard Q&A (`job == 8`) now compares to
      `res_enum_id("jobname", "Bard")`.
- [x] speak.c bargirl lodging offer (`job == 13`) now compares to
      `res_enum_id("jobname", "Bargirl")`.
- [x] speak.c recognize_he/she branch was gating on `job != 13` —
      gender proxy, not intent. Fixed to
      `gender == res_enum_id("gender", "female")`.
- [x] speak.c chit.well_ / chit.health_ and driv.c pronoun() clamp
      — bare `gender == 2` folded into
      `res_enum_id("gender", "female")` so the numeric enum slot
      for "female" doesn't leak into C.

---

## Reference: already-migrated patterns

These are the shape "done" looks like — keep adding to this list as
items above land.

- `item_is_weapon(id)` / `item_is_armour(id)` / `item_is_gear(id)` /
  `item_is_potion(id)` / `item_is_treasure(id)` /
  `item_is_magic_item(id)` — §17-M
- `res_item_kind(id)` — §17-M
- `res_enum("jobname", n)` / `res_enum_id(enum_id, sym)` — §15
- `res_combat_int("drop_tables.default.treasure_chance")` /
  `res_int_path("upkeep.food_cost_leader_div")` — §17-J / §17-L
- `party_award_special(PS_FIRESHIELD)` et al. — §17-G / §17-I
- `pm_from_digit('1')` / `inv_slot_from_letter('A')` — §17-L
- `res_formula_int("donkey_food_cap", "y", y)` — §17-O
- `res_npc_gender` / `state.npc[].gender` — §17-R
- `res_xp_threshold(lv)` / `res_class_levelup(cls, stat)` — §17-I
- `pa_flag_is("silver_key", "note_found")` /
  `pa_flag_set("peasant_wife", "rescued")` — §17-S
- `res_flag_slot("tag")` / `res_flag_value_id("tag", "state")` /
  `res_flag_value("tag", idx)` / `res_flag_id_for_slot(slot)` — §17-S
- `res_monster_id("hobgoblin_chief")` — §17-T
- `res_monster_tier_pick("weak")` / monster `tier:` keyword /
  `res_str_path("wilderness.encounter_easy_tier")` — §17-U
- `pm_is_fighter` / `pm_is_thief` / `pm_is_bard` / `pm_is_ranger` /
  `pm_is_spellcaster` — §17-V
- `res_npc_slot("<shard_key>")` / `res_npc_role("<role>")` /
  `npc_roles:` subtree in world.json5 — §17-W
- `res_enum_id("jobname", "Bard")` / `res_enum_id("gender", "female")`
  at speak.c + driv.c — §17-X
- `npc_t` handle + `npc_at_slot` / `npc_for_role` /
  `npc_name` / `npc_job` / `npc_gender` / `npc_towne` / `npc_x` /
  `npc_y` / `npc_loc_str(n, buf, sz)` — §17-Y
- mission_trigger / mission_cond `.flag_tag` + `.when_state` /
  mission_spec `.npc_role` / mission_binding `.npc_role` — §17-Z
  (P1 follow-ups)

## Out of scope for this doc

- Hardcoded mission-prose pronouns (him/her in fixed-scene NPC text)
  — eleven sites in dungeon.c; tracked as part of §17 A.4 slice C
  text-extraction. See §17-Q commit body.
- `flag[1..8]` where `mission_spec` tables already name them —
  those are the target shape, not a leak.
