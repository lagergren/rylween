# Rylween scripting DSL — single-doc reference

Authoritative reference for the script DSL as it stands after §22.F.
The C header `src/script.h` is the in-source companion; this doc
covers the same surface in one place plus worked examples and the
text-binding rules.

The DSL is a JSON5 tree consumed by the interpreter in `src/script.c`
(`play_script`, `play_spell_body`, `play_formula_int`). Every script
is an array of one-op objects; every op is an object with a single
identifying key (`screen`, `if`, `mutate`, …) plus its parameters.
There is no array-form effect language anywhere in the runtime — the
historical `["seq", ...]` shape from earlier design drafts has been
fully retired.

---

## 1. World file & shards

The world tree lives at `data/world.json5`. It is one JSON5 object
the loader (`res_load`) parses, resolves `$import` directives in, and
hands to the runtime read-only. All scripts live under the `scripts:`
subtree, sharded by top-level prefix:

```json5
scripts: {
  speak:           { $import: 'world/scripts/speak.json5' },
  speak_missions:  { $import: 'world/scripts/speak_missions.json5' },
  missions:        { $import: 'world/scripts/missions.json5' },
  towne:           { $import: 'world/scripts/towne.json5' },
  terrain:         { $import: 'world/scripts/terrain.json5' },
  dungeon:         { $import: 'world/scripts/dungeon.json5' },
  hunt:            { $import: 'world/scripts/hunt.json5' },
  rest:            { $import: 'world/scripts/rest.json5' },
  lodge:           { $import: 'world/scripts/lodge.json5' },
}
```

`$import` is recursive (cycles fatal). The loader walks every script
once at world load (`res.c::fold_scripts_walk`) and runs sniff-tests
on op shapes — unknown `engine_call`/`engine_int`/`engine_bind` fns,
unknown flag tags, unknown enum / item / pic / monster / dungeon-
scenario / building symbols all surface as `res: <path>:<line>` fatal
errors at world load, before the engine starts. Typos die loudly.

---

## 2. Script anatomy

A script is an `N_ARR` of op objects, addressed by dotted id. The
runtime entry point `play_script("speak.merchant.first_visit", binds)`
walks `scripts.speak.merchant.first_visit` in order, dispatching each
op via `dispatch_op`.

The dotted id also derives the **parent** used by every text-emitting
op to resolve relative names: `speak.merchant.first_visit` → parent
`speak.merchant`, so `{ screen: 'offer' }` resolves to the text id
`speak.merchant.offer`. `resolve_text_id` two-step:
1. If the name already contains a dot, use it verbatim.
2. Else probe `<script_id>.<name>` (full id, e.g.
   `speak.merchant.first_visit.offer`); on hit, use it.
3. Otherwise fall back to `<parent>.<name>` (one segment stripped,
   e.g. `speak.merchant.offer`).

This lets shared text atoms (like `speak.recognize_he`) live one
level up and be reused from every nested speak.* script without
copying.

Three runtime entry points, all in `src/script.h`:

- `play_script(id, bindings)` — dispatch ops as above.
- `play_spell_body(spell_id, body_node, bindings, combat_ctx)` —
  same machinery but takes a pre-resolved body node and exposes the
  combat context (monster HP, per-encounter flags) for the spell-
  effect ops below. `combat_ctx` may be NULL for out-of-combat
  casters (priest spells via subs.c); `monster_*` and `battle_flag`
  reads/writes die fatally in that case.
- `play_formula_int(formula_id, body_node, bindings)` — evaluate a
  single expression body and return its int. Used by
  `res_formula_int_n` for damage / reward formulas.

---

## 3. Ops

Every op below is one entry in a script's array. Ops that produce a
value into the runtime environment do so via `let` or `engine_bind` /
`engine_int` results; ops that mutate game state use `mutate`.

Control flow:

- `{ if: { cond: <expr>, then: [...], else?: [...] } }` —
  `then` fires when `cond` evaluates truthy (!= 0). `else` is
  optional. Probability gate: `cond: { chance: <expr> }`.
- `{ switch: { expr: <expr>, cases: [...] } }` — first-match-wins.
  Each case is one of:
  - `{ match: <expr>,        then: [...] }` — single-value match
  - `{ in: [<expr>, ...],    then: [...] }` — match-any-of
  - `{ default: true,        then: [...] }` — fallback (must be the
    bool literal `true`)
- `{ loop: [...] }` — infinite body; exits via inner `{ break: true }`,
  restarts via `{ continue: true }` or natural fall-off. Both
  `break` / `continue` are fatal outside a `loop`.
- `{ for_member: { var: '<name>', then: [...] } }` —
  iterate `1..res_party_member_max()` with the current member index
  bound into `rt_env` under `var` (RT_INT). Body sees the var via
  `{ var: '<name>' }` in expressions or `{<name>}` in text.
- `{ for_inventory: { var: '<name>', then: [...] } }` —
  iterate `1..res_inventory_slot_max()`. Guard on
  `{ inventory: <var> }` if you only care about occupied slots.
- `{ for_monster: { var: '<name>', then: [...] } }` —
  iterate `1..combat_ctx.noc`. Fatal outside a combat ctx.
- `{ for_each_alive_monster: { then: [...] } }` —
  iterate `1..combat_ctx.noc`, skipping slots where `deadc[i]` is set.
  Body sees the current slot via `{ var: 'monster_idx' }` (fixed name —
  no `var:` parameter). RNG and side effects only fire on alive
  iterations, so this is **not** a drop-in for `for_monster` when
  byte-identity with a noc-wide legacy loop matters; use it for new
  combat bodies (§23.A monster-spell dispatch) where an alive-only
  pass is the intent. Fatal outside a combat ctx.
- `{ for_each_alive_player: { then: [...] } }` —
  iterate `1..res_party_member_max()`, skipping members with
  `pm_dead` set. Body sees the current slot via `{ var: 'target_idx' }`
  — the same name `ask_target` uses, so single-target ops compose
  unchanged inside the loop. No combat ctx required.

Text + journal:

- `{ screen: '<name>' }` — `tx_fmt(parent.<name>, bindings)` then
  `ryl_pause()` then `clw()`. The bundled pause + clw is the right
  pacing for the typical "narrate, wait for RETURN, clear" beat.
- `{ emit: '<name>' }` — `tx_fmt` only (no pause, no clw). Use when
  text interleaves with `engine_call` side effects whose redraws
  would conflict with the bundled clw.
- `{ emit_indexed: { fmt: '<name>{}<rest>', value: <expr> } }` —
  substitutes `<value>` (decimal) into the single `{}` placeholder
  in the template name, resolves to a text id under the script's
  parent, emits via `tx_fmt`. Strict — the resolved id must exist.
- `{ journal: '<text>' }` — `bind_expand`s the literal, prepends
  `"<cpl> "` and appends `"\n"`, forwards to `journal()`. Use
  `{<placeholder>}` for binding interpolation just like screens.

Pause / input:

- `{ pause: <int-ms> }` — `ryl_delay(N)` (engine-paced delay; the
  no-delay test mode short-circuits this).
- `{ wait_input: true }` — `ryl_pause()` (prompt + block until
  RETURN). Distinct from `pause` (delay) and `screen`'s bundled pause.
- `{ menu_branch: { prompt?: '<screen>', key_var?: '<name>',
                    options: [...], default?: [...] } }` —
  one `ryl_menu`-style key read. Each option:
  - `{ key: 'K',                 then: [...] }`
  - `{ keys: ['A', 'B', ...],    then: [...] }`
  - `{ key_range: ['LO', 'HI'],  then: [...] }`
  Optional `key_var` binds the matched (uppercased) key into rt_env
  as a one-char string. Unmatched keys fall to `default:` if present.
- `{ input_number: { prompt_screen?: '<name>', lg: N, var: '<name>',
                     min: <expr>, max: <expr> } }` —
  read a numeric line via `getstr(N)`, clamp into `[min, max]`,
  bind under `var`. Non-numeric / empty input clamps to `min`.
- `{ input_string: { lg: N, accept: '<sym>'|['<sym>'...],
                     match?: [...], miss?: [...] } }` —
  read `getstr(lg)`, case-insensitive compare against `accept`
  (single string or array), play `match` or `miss` as appropriate.
  `lg` ∈ 1..63.
- `{ input_item: { slot_var: '<name>', item_var: '<name>',
                   then?: [...] } }` —
  read one key (uppercased), validate as inventory letter A..P
  (slot 1..16). On hit: bind `slot_var` to the slot, `item_var` to
  the item id at that slot, run `then`. On miss: skip silently.

State:

- `{ set_flag: '<tag>', value: '<state>' }` —
  `state.p[cpl].flag[res_flag_slot(tag)] = res_flag_value_id(tag, state)`.
  Both args symbolic; data is all-string. Legacy int forms are still
  accepted but every shipped script uses symbols.
- `{ let: '<name>', value: <expr> }` — evaluate `value`, bind into
  rt_env as RT_INT under `<name>`. Read back via `{ var: '<name>' }`
  in expressions or `{<name>}` in text.
- `{ mutate: { target: <lvalue>, op: 'set'|'add'|'sub'|'mul'|'div',
              value: <expr>, clamp?: { min?: <expr>, max?: <expr> } } }`
  Read the lvalue, apply `op` against `value`, optionally clamp
  result, write back. Lvalues:
  - `{ gold }`, `{ torches }`, `{ food }`, `{ pl }` — party scalars
  - `{ flag: <tag-or-slot-expr> }` — party flag at that slot
  - `{ inventory: <slot-expr> }` — party inventory[1..N]
  - `{ member_field: { idx: <expr>, field: '<name>' } }` —
    `state.p[cpl].<field>[idx]`; field is one of `lv`/`hp`/`h`/`ma`/
    `wi`/`ws`/`ch`/`cl`/`ra`/`dead`/`ar`/`wp`/`exp`/`gender`.
  - `{ hours_rested }`, `{ hour_of_day }`, `{ time_left }` —
    party-rest / wall-clock counters.
  - `{ monster_hp: <slot-expr> }`, `{ monster_dead: <slot-expr> }` —
    combat-ctx mh[i] / deadc[i]; fatal w/o combat ctx.
  - `{ battle_flag: '<name>' }` — combat-ctx flag pointer; name ∈
    `confused`/`held`/`antimagic`/`ws`. Fatal w/o ctx or NULL ptr.
  - `{ state_banished: <any> }` — `state.banished` (`<any>` is just
    a marker; mutate.value is what's written).

Reward / inventory / dispatch:

- `{ reward: { gold?: <int>, items?: [<int|sym>...],
              cant_carry_screen?: '<name>',
              picked_up_screen?: '<name>' } }` —
  add `gold` to party, place each item into the first empty
  inventory slot. On full inventory: `ryl_delay(800)` ↦
  emit `cant_carry_screen` ↦ `drop_option()` ↦ on success emit
  `picked_up_screen` then `ryl_pause()`. Item entries are literal
  ids OR shard symbols (`'red_gem'`) resolved via `res_item_id`.
- `{ inv_add: { item: <expr>, added?: [...], full?: [...] } }` —
  `inv_add(item)` returns slot. Slot != 0 plays `added`, slot == 0
  plays `full`. Distinct from `reward`'s `cant_carry` flow because
  the find-object path doesn't offer drop-to-make-room.
- `{ play_script: '<dotted-id>' }` — invoke a nested script.
  Caller's bindings + rt_env entries are forwarded as the nested
  call's `ryl_bind` list (rt_env first so it shadows caller). The
  nested script gets a fresh rt_env. Used by the speak_missions
  dispatcher scripts to fan out into the existing `speak.<x>.<y>`
  bodies after gating on flags + populating role-keyed bindings.
- `{ ask_target: '<kind>', var: '<name>' }` — interactive picker.
  `kind` ∈ `monster_alive` / `party_member`. The first redraws the
  combat ctx until the player picks a live monster letter; the
  second prints `"Who? (1-N)"` and loops on `pm_digit_in_range`.
  Result (1-based index) lands in rt_env under `var`.
- `{ engine_call: '<fn>', args: [<int-expr>...] }` — call a void C
  entry point with literal int args. See §6 for the registry.
- `{ engine_bind: { fn: '<fn>', args: [<expr-or-str>...],
                    var: '<name>' } }` —
  call a string-returning C entry point and stash the result into
  rt_env as RT_STR under `var`. `args` positions are typed by the
  registry's sig string: `'i'` is an int expression, `'s'` is a
  string literal (authoring-time constant). See §6.
- `{ encounter: { foe: '<sym>'|<int>, n?: <expr>, mandatory?: <bool>,
                  tier?: '<sym>' } }` — wrapper around `encounter()`.
  Symbolic `foe` resolves via `res_monster_id`; symbolic `tier`
  validates against the bestiary tier list.
- `{ dungeon: { levels: <expr>, scenario?: '<sym>' } }` — wrapper
  around `dungeon(levels, scenario)`. Symbolic scenario validates
  against `dungeons.scenarios`.
- `{ speak: { building: '<sym>'|<int>, town?: <expr> } }` — wrapper
  around `speak(bld, tow)`.

`engine_int` is **not** an op — it's an expression form. It returns
an int and is read inside any `<expr>` slot.

---

## 4. Expressions

Anywhere the grammar above says `<expr>`, the expression evaluator
(`eval_expr`) accepts:

Literals:
- `<int>` — integer literal.
- `<bool>` — JSON5 `true`/`false` (1/0).
- One-char string — ASCII int (`'1'` ≡ 49). Multi-char strings are
  fatal in int positions to catch accidental typos.

State reads:
- `{ flag: <tag-or-slot-expr> }` — party flag value (int).
- `{ gold }`, `{ torches }`, `{ food }`, `{ pl }`, `{ twn }`,
  `{ hour_of_day }`, `{ hours_rested }`, `{ time_left }`, `{ cpl }`
  — party + state scalars.
- `{ inventory: <slot-expr> }` — party inventory item id at slot.
- `{ member: <idx-expr>, field: '<name>' }` — party member field
  (same field list as the mutate `member_field` lvalue).
- `{ var: '<name>' }` — read an rt_env binding (RT_INT only — RT_STR
  is fatal here, those are text-only).
- `{ monster_count }` — combat ctx `noc`. Fatal w/o ctx.
- `{ monster_hp: <slot-expr> }`, `{ monster_dead: <slot-expr> }` —
  combat ctx mh[i] / deadc[i]. Fatal w/o ctx or out-of-range idx.
- `{ battle_flag: '<name>' }` — combat ctx flag value. Fatal w/o ctx.
- `{ state_banished }` — `state.banished` (paired with the lvalue).

RNG:
- `{ random: [<lo-expr>, <hi-expr>] }` — `ryl_random(lo, hi)`,
  inclusive both ends. Fatal if `lo > hi`.
- `{ chance: <pct-expr> }` — `ryl_chance(pct)` → 0/1.

Arithmetic / logic (all arity-2 unless noted):
- `{ add | sub | mul | div | mod | min | max: [a, b] }`
- `{ eq | ne | lt | lte | gt | gte: [a, b] }`
- `{ and | or: [a, b, ...] }` — variadic, short-circuit; result is
  always 0 or 1.
- `{ not: <expr> }` — 0 or 1.

Symbol resolution:
- `{ item: '<sym>'|<int> }` — `res_item_id` for strings, validate
  via `res_item` for ints. Die-on-unknown.
- `{ pic: '<sym>'|<int> }` — `res_enum_id("pic", sym)` for strings.
- `{ enum: ['<table>', '<value>'] }` — generic enums lookup.
- `{ flag_value: ['<tag>', '<value>'] }` —
  `res_flag_value_id(tag, value)`; pairs with `{ flag: <tag> }`
  to compose symbolic flag comparisons.
- `{ obcost: <id-expr> }` / `{ obdamage: <id-expr> }` —
  `res_item(id)->cost` / `->damage`.
- `{ engine_int: '<fn>', args: [<expr>...] }` — call an int-
  returning C entry point. Some args are tagged with an enum (§6.2)
  so a multi-char string literal in that slot is auto-resolved
  before reaching `eval_expr`.

---

## 5. Bindings & rt_env

There are two kinds of bindings the interpreter merges:

- **Caller `ryl_bind[]`** — passed to `play_script` from C
  (e.g., towne wrappers that `snprintf` a price into
  `{"price", price_str}` before invoking a script). Read-only
  inside the script.
- **`rt_env`** — runtime binding store filled by `let`,
  `input_number`, `input_string`, `input_item`,
  `menu_branch.key_var`, `for_member`/`for_inventory`/`for_monster`
  loop vars, `for_each_alive_monster` (`monster_idx`) /
  `for_each_alive_player` (`target_idx`),
  `engine_bind`, `ask_target`. Bindings have two
  kinds: RT_INT (every source above except `engine_bind`) and
  RT_STR (`engine_bind` results only). RT_STR is text-only —
  using one in `{ var: ... }` is fatal.

Order: `rt_env` shadows caller bindings (rt_env-first when the
runtime merges them into the `ryl_bind*` for nested calls). Within
rt_env, newest entry wins on duplicate names — this is what makes
nested `for_*` loops with the same loop var name shadow cleanly.

**Automatic bindings.** `bind_expand` (used by every text-emitting
op for `{...}` interpolation) recognises three engine-provided
names without any caller plumbing:

- `{party_max}` → `res_party_member_max()`
- `{inventory_max}` → `res_inventory_slot_max()`
- `{cost:<itemkey>}` → `res_item(res_item_id(<itemkey>))->cost`

These let prompts read `Who? (1-{party_max})` and shop screens read
`The price is {cost:torch} gold.` without any per-call binding
preamble. They are unbound names so caller / rt_env bindings of the
same name take precedence.

**Combat-ctx bindings.** When `play_spell_body` is called with a
non-NULL `script_combat_ctx`, the entry pushes one rt_env binding
before running the body:

- `h2` → `ctx->h2` (per-encounter monster max-HP scalar). Spells
  read it as `{ var: 'h2' }`; expressions like `{ add: [{ div: [
  { var: 'h2' }, 5 ]}, { random: [0, 3] } ]}` express the legacy
  `h2/5 + random(0,3)` monster-spell damage formula without a C
  local. Out-of-combat callers (priest spells via `subs.c`) leave
  ctx NULL — `{ var: 'h2' }` there dies with the standard "expr
  var: unbound" diagnostic.

---

## 6. Engine registries

The C↔DSL boundary is three explicit registries in `src/script.c`.
Each registry's argc / sig is enforced both at world load (via
`script_engine_*_argc` from `script.h`) and at runtime (via
`do_engine_*` in `script.c`). Adding a new fn means appending to
the matching tbl — see the "Adding a registry entry" recipe below.

### 6.1 `engine_call` — void void(int32_t* args)

Op form: `{ engine_call: '<name>', args: [<int-expr>...] }`. Args are
literal ints from `eval_expr`. Current registry:

| name              | argc | side effect                              |
|-------------------|-----:|------------------------------------------|
| `update_map`      | 0    | `update_map()` redraw                    |
| `clr_map_window`  | 0    | clear map area only                      |
| `blitgfx`         | 1    | `blitgfx((uint8_t)pic_id)`               |
| `statusline`      | 0    | redraw status line                       |
| `clw`             | 0    | clear window                             |
| `advance_hour`    | 0    | `advance_hour()` tick                    |
| `weaponr`         | 0    | wield-weapon prompt                      |
| `armourr`         | 0    | wield-armour prompt                      |
| `invlist`         | 0    | inventory listing                        |
| `showparty`       | 0    | party stats panel                        |
| `drop_option`     | 0    | drop-from-inventory picker               |
| `rumour`          | 0    | one rumour line emitted                  |
| `pm_add_hp`       | 2    | `pm_add_hp(pm_cur(member), delta)`       |
| `pm_hp_floor`     | 2    | `pm_hp_floor(pm_cur(member), floor)`     |
| `pa_teleport_step`| 2    | priest.teleport movement loop            |

### 6.2 `engine_int` — int32_t int(int32_t* args)

Expression form: `{ engine_int: '<name>', args: [<expr>...] }`.
Registry:

| name                     | argc | arg-enum tags          |
|--------------------------|-----:|------------------------|
| `pa_is_alive`            | 1    | —                      |
| `pa_is_dead`             | 1    | —                      |
| `pa_is_spellcaster`      | 1    | —                      |
| `pa_is_class`            | 2    | —, `pc_class`          |
| `pa_first_alive`         | 0    | —                      |
| `pa_alive_count`         | 0    | —                      |
| `pa_first_level`         | 0    | —                      |
| `pa_count_class`         | 1    | `pc_class`             |
| `pa_count_alive_class`   | 1    | `pc_class`             |
| `inv_find_empty`         | 0    | —                      |
| `inv_find_item`          | 1    | `item`                 |
| `inv_find_in_range`      | 2    | `item`, `item`         |
| `inv_count_in_range`     | 2    | `item`, `item`         |
| `pa_party_member_max`    | 0    | —                      |
| `inv_slot_max`           | 0    | —                      |
| `wld_location_x`         | 1    | `world_locations`      |
| `wld_location_y`         | 1    | `world_locations`      |

Args tagged with an enum (`pc_class`, `world_locations`, …) accept
a multi-char string literal in that position; the loader resolves
the symbol via `res_enum_id` (or `res_item_id` for the special
`item` tag) before `eval_expr` sees the int. Single-char strings
still take the ASCII-int path.

### 6.3 `engine_bind` — void(int32_t* ints, char** strs, char* out, size_t)

Op form: `{ engine_bind: { fn: '<name>', args: [<expr-or-str>...],
                           var: '<name>' } }`. Each arg position is
an `'i'` (int expression) or `'s'` (N_STR literal) per the registry's
sig string.

| name               | sig | result                                       |
|--------------------|-----|----------------------------------------------|
| `object`           | `i` | `object(item_id)` display name               |
| `pronoun`          | `is`| `pronoun(gender, case)` — case ∈ subject/object/possessive |
| `res_town_name`    | `i` | town name by id                              |
| `res_enum`         | `si`| `res_enum(table, id)` display string         |
| `npc_in_town`      | `si`| first NPC in town with given job symbol      |
| `res_npc_role`     | `s` | numeric string of `res_npc_role(role)` slot  |
| `pa_name`          | `i` | party member name (slot 1..party_max)        |
| `npc_name_at_role` | `s` | display name of the NPC bound to `role`      |
| `npc_loc_at_role`  | `s` | NPC location string ("in <town>" / "at NxN") |

Result strings cap at `RT_SV_CAP` chars (currently 47); overflow is
fatal so a too-long registry result fails loudly.

### 6.4 Adding a registry entry

1. Define the C entry point. It takes the raw int / str arrays and
   either returns an int (`engine_int`), writes a string into `out`
   (`engine_bind`), or returns void with side effects (`engine_call`).
2. Append the row to the matching tbl in `src/script.c`.
3. The world-load validator already pulls argc / sig via
   `script_engine_*_argc` — no extra registration needed.

---

## 7. Error contract

Every fatal error inside the interpreter goes through `die_at` and
prints

```
play_script '<id>' op <N>: <message>
```

where `<id>` is the dotted script id and `<N>` is the (zero-based)
index of the offending op in the script body. Sub-clauses (cond
inside an `if`, expr inside a `mutate`, etc.) report against the
enclosing op's index. Spell bodies report as
`spells.<class>.<name> op <N>:`.

World-load errors use `res: <path>:<line>: <message>`. The validator
in `res.c::fold_scripts_walk` runs the sniff-tests at fold time, so
typos in flag names, engine fns, item / enum / pic symbols all fail
before the engine starts.

---

## 8. Worked examples

### 8.1 Small spell — `mage.frost` (excerpt)

The mana-cost deduction is done in C before the body runs (battle.c
checks `pm_ma >= base_cost + lv` and debits via `pm_add_ma`); the
spell body just renders narration and applies damage. Caller binding
is `caster_idx` (1..party_max).

```json5
frost: {
  slot: 1, base_cost: 8, name: 'Frost Finger',
  do: [
    { ask_target: 'monster_alive', var: 'target_idx' },
    { let: 'dmg', value: { mul: [
        { member: { var: 'caster_idx' }, field: 'lv' },
        { random: [2, 8] },
    ]}},
    { engine_call: 'clw' },
    { engine_bind: { fn: 'pa_name', args: [{ var: 'caster_idx' }],
                     var: 'caster' } },
    { emit: 'battle.spell.frost.chills' },
    { emit: 'battle.spell.frost.damage' },
    { mutate: { target: { monster_hp: { var: 'target_idx' } },
                op: 'sub', value: { var: 'dmg' } }},
    { if: { cond: { lt: [{ monster_hp: { var: 'target_idx' } }, 1] },
            then: [
              { mutate: { target: { monster_hp: { var: 'target_idx' } },
                          op: 'set', value: 0 }},
              { mutate: { target: { monster_dead: { var: 'target_idx' } },
                          op: 'set', value: 1 }},
              { emit: 'battle.killed' },
            ] }},
  ],
}
```

Note the absolute (non-parent-relative) text ids `battle.spell.
frost.chills` / `battle.killed` — `emit` and `screen` accept either
shape. Parent-relative is preferred; absolute is used here when
referring to a separate texts shard.

### 8.2 Small towne flow — speak_missions dispatcher

A `speak_missions.<role>` script gates a stage on flag values, sets
up role-keyed bindings, and fans into the matching `speak.<x>.<y>`
body via `play_script`:

```json5
rangers_quest_giver: [
  { if: { cond: { eq: [{ flag: 'ranger.letter' },
                       { flag_value: ['ranger.letter', 'none'] }] },
          then: [
            { engine_bind: { fn: 'npc_name_at_role',
                             args: ['ranger_brother'],
                             var: 'ranger_name' } },
            { engine_bind: { fn: 'npc_loc_at_role',
                             args: ['ranger_brother'],
                             var: 'ranger_loc' } },
            { play_script: 'speak.ranger.first_visit' },
          ] } },
],
```

The nested `speak.ranger.first_visit` reads `{ranger_name}` and
`{ranger_loc}` in its screen / journal text — both come through the
forwarded rt_env entries.

### 8.3 Formula — encounter HP scaling

Formulas live under the top-level `formulas:` subtree. The value
of a formula key is **directly** an expression node (no wrapper
object) evaluated via `play_formula_int`. Caller bindings carry
the input variables:

```json5
formulas: {
  // h2 = h2_base + first_level * 3
  encounter_h2_scaled: { add: [
    { var: 'h2_base' },
    { mul: [{ var: 'first_level' }, 3] },
  ]},
}
```

C side (battle.c) calls `res_formula_int_n("encounter_h2_scaled",
binds, 2)` with `binds = [{name:"h2_base", value:...},
{name:"first_level", value:...}]`.

---

## 9. Cross-references

- `src/script.h` — in-source op + expression contract; this doc
  expands its grammar into a single human-readable reference.
- `src/script.c` — interpreter implementation; `dispatch_op` is the
  one entry point that names every op listed in §3.
- `src/res.c::fold_scripts_walk` — world-load validator; the place
  to add a new sniff-test if a future op gains a symbol arg that
  should fail at load instead of at first fire.
- `data/world/scripts/*.json5` — every shipped script. Browse
  `speak.json5` for typical NPC dialogue, `dungeon.json5` for the
  largest single body (the level-event switch), `towne.json5` for
  shop / inn / smith / tavern flows, `speak_missions.json5` for the
  per-role mission-stage dispatchers.
