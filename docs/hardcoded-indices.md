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
- [ ] **P2 — Monster / encounter IDs**
      (~23 `encounter(N, M)` calls in dungeon.c + a handful elsewhere)
- [ ] **P3 — Class-ID comparisons**
      (~5–8 sites; enum already exists in `state.h`, just unused)
- [ ] **P4 — Magic-item case labels in battle.c**
      (16 `case 100..111:` labels; §17-N already flagged these)
- [ ] **P5 — NPC slot literals**
      (~12 sites: `state.npc[13]`, `state.npc[15]` — semi-documented)
- [ ] **P6 — Job-ID bare comparisons**
      (2 sites in speak.c: `job == 8` bard, `job == 13` bargirl —
      bargirl chitchat already moved to gender in §17-R; lodging-
      offer and bard Q&A remain)

### P1 follow-ups (not blocking)

- `mission_spec` tables in `src/speak.c` still use `.flag = N,
  .when = K` literals. They parallel the now-tag-based scripts and
  should flip to `.flag_tag = "silver_key", .when = "note_found"`
  — mechanical, no behavior change. Not done in §17-S because the
  struct-shape change touches every mission_spec[] instantiation
  and is easier in a dedicated pass.
- `src/missions.c` has one fresh `pa_flag_is` site but the other
  trigger guards still live in a table of `.flag = N` ints. Same
  pattern as above; same follow-up.
- The save loader still accepts the legacy `flag: [...]` byte-array
  shape for round-tripping pre-§17-S saves. Once no such save exists,
  that branch can be deleted.

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

## P2 — Monster / encounter IDs

`encounter(N, M)` calls use a raw monster-id as `N`. Monster table
entries are already in `data/world/monsters/` but C callers still
pass the int. Observed counts:

- [ ] dungeon.c — 23 `encounter(N, M)` calls
- [ ] subs.c — 4 `encounter(...)` calls (camp/wilderness hooks)
- [ ] battle.c — 1 site
- [ ] missions.c — 0 direct calls (already DSL-authored)

Approach: either expose `res_monster_id("gnoll")` and take the symbol
at the call site, or migrate the caller to DSL (for mission-scripted
scenes — overlaps with §17 A.4 slice C). Mechanical-cousin of §17-J's
`n2_lv_div` field that let `foe == 31` become `m->n2_lv_div > 0`.

- [ ] Expose `res_monster_id(const char *sym)` in res.c
- [ ] Migrate dungeon.c encounter sites (biggest win)
- [ ] Migrate subs.c camp encounter
- [ ] Migrate battle.c stray

## P3 — Class-ID comparisons

`state.p[...].cl[m] == 3` (mage) / `== 4` (priest) etc. Enum exists at
`src/state.h` (`PC_FIGHTER`, `PC_MAGE`, `PC_PRIEST`, `PC_BARD`,
`PC_RANGER`) — the sites just don't use it.

Concentration: driv.c (~4), subs.c (~2), battle.c (~1).

- [ ] Audit sites: `grep -n '\.cl\[.*\] ==' src/*.c`
- [ ] Replace bare `== N` with `== PC_MAGE` etc.
- [ ] Consider `pm_is_mage(pm)` / `pm_is_priest(pm)` predicates
      (parallels `item_is_*` shape from §17-M)

## P4 — Magic-item case labels (battle.c)

`case 100:` … `case 111:` in battle.c's (U)se-item switch. §17-N
already flagged items 100..104 + 109..111 as the "nine remaining
item-use cases" — part of the battle.c sweep (queue item 4).

- [ ] Fold into battle.c state.p sweep (§17-N queue item 4) —
      rather than dispatch on int, look up the item shard and
      route through its DSL effect body (like reward op / spells
      already do).

## P5 — NPC slot literals

`state.npc[5]`, `state.npc[13]`, `state.npc[15]` in towne.c /
speak.c. The slot corresponds to a specific NPC (Nolrim, Erethor,
etc.). These are semi-documented — the slot is baked into the quest
design — but should still go through a symbolic accessor so the
intent is readable.

- [ ] Add `res_npc_by_name("Erethor")` or expose an `NPC_*` enum
      for the quest-critical slots
- [ ] Migrate towne.c (6 sites)
- [ ] Migrate speak.c (6 sites)

## P6 — Job-ID bare comparisons

Two sites left in speak.c:

- [ ] `job == 8` (Bard) — used in `speak.people.j8` formatting.
      Replace with `res_enum_id("jobname", "Bard")` or a named
      constant.
- [ ] `job == 13` (Bargirl) — lodging-offer branch. This one is
      profession-specific so it stays on `job`, but the literal
      13 should become a symbolic constant.

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

## Out of scope for this doc

- Hardcoded mission-prose pronouns (him/her in fixed-scene NPC text)
  — eleven sites in dungeon.c; tracked as part of §17 A.4 slice C
  text-extraction. See §17-Q commit body.
- `flag[1..8]` where `mission_spec` tables already name them —
  those are the target shape, not a leak.
