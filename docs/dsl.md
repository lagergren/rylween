# Phase 6d DSL: hierarchical world definition + shared effect language

Third pass. Supersedes the two earlier drafts (commits `1f0b59f` flat-record
sketch and `99e3602` first hierarchical sketch). The inner expression
language (conditions / effects / paths) is unchanged from those drafts and
is locked at §6 below; this pass rewrites the **outer world-file shape**
to fold in classes-as-spell-owners, town inhabitants, mission triggers
beyond `when`-guards, and a small import mechanism so map data, large
dialogue blocks, and shard files don't have to inline into the root.

---

## 1. Goal

Make the game world *data*: one JSON5 tree the C runtime walks, with all
behaviour expressed as small array-form effect programs. Phase 6c already
extracted static text into `data/resources.jsonl`; Phase 6d takes the next
step and replaces the *behavioural* C in `speak.c`, `subs.c`, `strid.c`,
`towne.c`, `missions.c` with data the same loader produces.

Two concrete wins:

- A designer can change a mission, an NPC's lines, or a spell's damage
  formula by editing one tree node and re-running. No C edit, no rebuild.
- The replay harness (`tools/replay.sh`) pins byte-identity across the
  entire stream — RNG is single-source (commit landing in this same series),
  delays are zero under `RYL_NO_DELAY`, and the DSL adds no new entropy
  beyond `["rand", lo, hi]` which routes through `ryl_random`.

---

## 2. World file: one tree, with imports

The world lives at `data/world.json5`. JSON5 because designers hand-edit it:
`// comments`, trailing commas, unquoted keys, single-or-double quotes.

The root has one fixed shape — a small set of named subtrees, each owning
one ontological domain:

```json5
{
  meta:    { ... },   // version, schema id
  flags:   { ... },   // symbolic name -> integer slot, value enum
  items:   { ... },   // id-keyed item records
  classes: { ... },   // class name -> stat ranges, ability list, spell list
  spells:  { ... },   // spell id -> effect program (referenced by classes)
  npcs:    { ... },   // npc id -> dialogue tree
  towns:   { ... },   // town id -> coords, services, NPC inhabitants
  monsters: { ... },  // foe id -> stats (folds in the {"kind":"monster"} JSONL)
  encounters: { ... }, // wilderness/dungeon roll tables
  missions: { ... },  // mission id -> stages, triggers, rewards
  map:     { ... },   // map grid; usually an `$import` to a side file
  texts:   { ... },   // text id -> string (folds in {"kind":"text"} JSONL)
}
```

### 2.1 Imports

Any subtree value may be replaced by an **import directive** — an object
with one key, `$import`, whose value is a path relative to `data/`:

```json5
{
  // ...
  map: { $import: "world/map.json5" },
  npcs: {
    garth: { $import: "world/npcs/garth.json5" },
    nrim:  { $import: "world/npcs/nrim.json5" },
    // small NPCs can stay inline:
    rumour_bartender: { dialogue: { ... } },
  },
}
```

Semantics:
- The loader resolves `$import` recursively at load time, before the eval
  loop starts. The runtime tree is one merged structure; the engine never
  sees `$import`.
- Cycles are a fatal load-time error.
- `$import` of `*.dat` (e.g. the legacy `data/map.dat` 80×40 char grid) is
  recognised as raw bytes and decoded by a per-target adapter — see §3.7.
- One file = one tree. No partial-merge inside a file.

This is the "single big JSON5 with file imports" pattern the task asks for:
the *file layout* can be sharded for editor sanity; the *runtime tree* is
one structure.

### 2.2 What stays in JSONL

`data/resources.jsonl` keeps **per-save player state** that doesn't belong
in a designer-authored world file: blessed replay output, score tables, etc.
World-DSL records (`{"kind":"text"}`, `{"kind":"monster"}`, `{"kind":"item"}`)
migrate into `world.json5` under their home subtree; the loader reads both
during the transition window with JSON5 records shadowing JSONL records of
the same id. Once a kind has fully migrated, the JSONL records for it can be
deleted.

Deliberately *not* in the world file: live `state.p[*]`, `state.npc[*]`
runtime fields. Those are persisted to `realm.dat` / `npc.dat` exactly as
today; the world file is the *schema and content*, not the save.

---

## 3. Subtree shapes

Each subtree has a small, fixed schema. Cross-tree references are by id
(usually the path component itself) — no string compares at runtime; the
loader resolves them once into integer indices.

### 3.1 `flags`

```json5
flags: {
  "garth.bandits":         { slot: 1,  values: ["none", "accepted", "recovered", "closed"] },
  "hermit.troll_tooth":    { slot: 33, values: ["none", "accepted", "extracted", "delivered"] },
  "church.donation.once":  { slot: 5,  values: ["no", "yes"] },
  "encounter.immune":      { slot: 0, scope: "encounter", values: ["no", "yes"] },
}
```

`slot` is the integer index in `state.p[cpl].flag[]` (party scope) or in
the encounter's transient flag set (`scope: "encounter"`). The string
identifier is what every other subtree uses; the integer slot only exists
for save-format compatibility.

### 3.2 `items`

```json5
items: {
  ruby:        { id: 111, kind: "treasure", cost: 0,     damage: 0 },
  fireshield:  { id:  27, kind: "armour",  cost: 15000, ac: 20,
                 wieldable_by: ["fighter", "ranger", "priest"], slot: "armour",
                 effect: ["set", "party.fireshield", 1] },
  potion_heal: { id:  36, kind: "consumable", cost: 200,
                 effect: ["seq",
                   ["ask_target", "party_alive"],
                   ["add", "target.hp", ["+", 15, "target.lv"]],
                   ["set", "target.hp", ["min", "target.hp", "target.h"]]] },
  flute_madness: { id: 109, kind: "magic_item", cost: 0,
                   wieldable_by: ["bard"], slot: "consumable",
                   effect: ["seq",
                     ["say", "strid.item.flute_of_madness"],
                     ["set_flag", "encounter.confused", 1]] },
}
```

Fields, by `kind`:

- `weapon`: `damage` (int), `wieldable_by` (class id list), `slot: "weapon"`
- `armour`: `ac` (int), `wieldable_by`, `slot: "armour"`
- `consumable`: `effect` (effect program), `slot: "consumable"`
- `magic_item`: `effect`, optional `wieldable_by`, `slot`
- `treasure`: bare `cost`; no effect

`wieldable_by` references class ids (`classes.<id>`). `effect` is an effect
program (§6) — that's how "what does this potion do" is expressed.

### 3.3 `classes`

A class names the stat prereqs, what items its members may wield, and which
spells they can cast. The spells themselves live in the `spells` subtree —
the class only references them.

```json5
classes: {
  fighter: {
    prereq: { ws_gte: 50 },
    starting: { weapon: "shortsword", armour: "leather" },
    spells: [],
  },
  mage: {
    prereq: { wi_gte: 50 },
    starting: { weapon: "dagger", armour: null },
    spells: ["mage.frost", "mage.fire", "mage.hold", "mage.confusion",
             "mage.annihilate"],
    casts_in: ["combat"],   // mage spells are combat-only
  },
  priest: {
    prereq: { wi_gte: 50 },
    starting: { weapon: "mace", armour: "leather" },
    spells: ["priest.heal", "priest.banish", "priest.teleport",
             "priest.earthquake", "priest.silence", "priest.morale"],
    casts_in: ["combat", "exploration"],
  },
  bard:    { prereq: { ch_gte: 50 }, spells: [], casts_in: [] },
  thief:   { prereq: {}, spells: [], casts_in: [] },
  ranger:  { prereq: {}, spells: [], casts_in: [] },
}
```

`prereq` keys are `ws_gte`, `wi_gte`, `h_gte`, `ch_gte` (matches the four
rolled stats). `casts_in` controls which UI exposes the spell menu —
`combat` = strid.c's `(C)ast Spell`, `exploration` = driv.c's `Z` key.
Empty list = no menu offered.

### 3.4 `spells`

Each spell is one record with `slot`, `base_cost`, and a `do` effect
program. Class membership lives in `classes.<id>.spells` (§3.3).

```json5
spells: {
  "mage.frost": {
    slot: 1, base_cost: 8,
    do: ["seq",
      ["require", ["not", ["flag_eq", "encounter.immune", 1]]],
      ["require", ["gte", "caster.ma", ["+", "base_cost", "caster.lv"]]],
      ["sub", "caster.ma", ["+", "base_cost", "caster.lv"]],
      ["ask_target", "monster_alive"],
      ["set", "$dmg", ["*", "caster.lv", ["+", ["rand", 0, 6], 2]]],
      ["say", "spell.mage.frost.hit"],
      ["sub", "target.hp", "$dmg"],
      ["if", ["lt", "target.hp", 1],
        ["seq", ["set", "target.hp", 0], ["set", "target.dead", 1],
               ["say", "common.killed"]]]],
  },
  "priest.banish": {
    slot: 4, base_cost: 85,
    do: ["seq",
      ["require", ["gte", "caster.ma", ["+", "base_cost", "caster.lv"]]],
      ["require", ["in_dungeon"]],
      ["sub", "caster.ma", ["+", "base_cost", "caster.lv"]],
      ["say", "spell.priest.banish.narration"],
      ["pause"],
      ["set", "state.banished", 1]],
  },
}
```

`base_cost` is a sugar name accessible as a path inside the spell's own `do`
body — saves repeating the literal twice. `slot` is the menu number rendered
for that spell within its class menu.

### 3.5 `npcs`

An NPC is one dialogue tree. Each branch is keyed by id; the engine picks
the matching branch by evaluating `when` guards in file order and falling
back to `default`.

```json5
npcs: {
  garth: {
    id: 1,             // matches person.c:76-81 / ryl.c:45 numbering
    job: "merchant",
    home: "thord",     // back-reference to towns.thord (§3.6)
    dialogue: {
      greeting: {
        // First-visit vs repeat — same pattern as speak.c:124-131.
        do: ["if", ["flag_eq", "speaker.visited", 0],
              ["seq", ["say", "garth.greet.first"],
                     ["set", "speaker.visited", 1]],
              ["say", "garth.greet.repeat"]],
      },
      "refund.accept": {
        when: ["flag_eq", "garth.bandits", 2],
        do: ["seq",
          ["say", "garth.refund.grateful"],
          ["give_item", "ruby"],
          ["give_gold", 2000],
          ["pause"],
          ["set_flag", "garth.bandits", 3]],
      },
      "rumour": {
        when: ["flag_eq", "garth.bandits", 0],   // tells rumours only before mission
        do: ["call", "lib.rumour"],              // shared library node, see §3.10
      },
    },
  },
  // hermit, priestess, smith, ...
}
```

Implicit binding: inside an NPC's effect, `speaker` is the NPC node itself
(so `speaker.visited` reads `npc[1].visited[cpl]`); `caster` is unbound.
Use `party.*` to talk about the party-side state and `target` after an
`ask_target`.

### 3.6 `towns`

A town is its coordinates plus a list of services and inhabitants.
Inhabitants are NPC ids; the dialogue lives under `npcs.*` and is referenced
here so "who lives in Thord" is one place.

```json5
towns: {
  thord: {
    id: 1, coord: [12, 11],
    inhabitants: ["garth", "thord_innkeeper", "thord_priestess",
                  "thord_smith", "thord_tavernkeeper", "thord_bartender"],
    services: {
      church: { npc: "thord_priestess",  do: ["call", "lib.church.menu"] },
      inn:    { npc: "thord_innkeeper",  do: ["call", "lib.inn.menu"]    },
      smith:  { npc: "thord_smith",      do: ["call", "lib.smith.menu"]  },
      tavern: { npc: "thord_tavernkeeper", do: ["call", "lib.tavern.menu"] },
      shop:   { npc: "garth",            do: ["call", "lib.merchant.menu"] },
    },
  },
  ollk:  { id: 2, coord: [48, 12], inhabitants: [...], services: {...} },
  wilor: { id: 3, coord: [43, 20], inhabitants: [...], services: {...} },
  murn:  { id: 4, coord: [55, 29], inhabitants: [...], services: {...} },
}
```

`coord` matches the existing hard-coded `(state.p[x].x == 12 && y == 11)`
literals in `interact.c:144-156` — those become a single `find_town(x, y)`
helper backed by the `towns` subtree.

`services.<key>.do` is the effect program that runs when the player picks
that service from the town menu. Most are `["call", "lib.…"]` into a
shared library (§3.10) because every town's church/inn/smith/tavern is the
same flow with NPC-specific narration.

### 3.7 `map`

The map is an 80×41 grid of terrain characters. Two ways to express it:

```json5
// inline (only viable for tiny test maps):
map: {
  width: 80, height: 41,
  legend: { s: "plains", f: "foothills", m: "mountain", w: "water",
            d: "desert", t: "swamp", g: "forest", "0": "town", "1": "hut",
            "2": "castle" },
  grid: ["smffss...", "ssss...", ...],
}

// production: external file via $import
map: { $import: "world/map.json5" }
```

The legacy `data/map.dat` (raw bytes, 41 × 81-byte lines) is loaded by a
shim adapter when the import path ends in `.dat`:

```json5
map: { $import: "map.dat", format: "amiga-grid", width: 80, height: 41 }
```

Same runtime tree shape after import; the file format choice is invisible
to the rest of the world.

### 3.8 `monsters` and `encounters`

Monsters are stat blocks; encounter tables map a roll bucket to a monster
id. Today these are split across the `{"kind":"monster"}` JSONL records and
the `(rand() % 14) + 40` style ranges in `subs.c` slumpvis. The DSL
collapses both:

```json5
monsters: {
  warrior: { id:  4, name: "Warrior", n: [1, 4], h: [50, 99], ws: 60 },
  troll:   { id:  3, name: "Troll",   n: [1, 3], h: [99, 199], ws: 55 },
  // ...
},

encounters: {
  // wilderness (slumpvis):
  wild_low:  { roll: ["rand", 1, 10], table: [
    [1, "kobold"], [2, "orc"], [3, "troll"], [4, "warrior"],
    [5, "goblin"], [6, "wild_dog"], [7, "knight"], [8, "bandit"],
    [9, "hobgoblin"], [10, "zombie"]] },
  wild_high: { roll: ["rand", 40, 53], table: [...] },
  ydarg:     { roll: ["rand", 60, 79], table: [...] },
},
```

Slumpvis becomes:

```json5
exploration: {
  // every successful move in the wilderness fires this
  on_step: ["seq",
    ["set", "$r", ["rand", 0, 99]],
    ["if", ["lt", "$r", 3],
      ["call", "lib.cave_found_prompt"],
      ["if", ["gte", "$r", 94],
        ["if", ["chance", 50],
          ["spawn_encounter", "wild_low"],
          ["spawn_encounter", "wild_high"]],
        ["call", "lib.terrain_msg"]]]],
}
```

(The literal r-thresholds `<3 / >=94` are kept verbatim from `subs.c:109,114`
to preserve the encounter probability.)

### 3.9 `missions`

A mission is a state machine, with stages, **triggers** that drive
transitions, and rewards.

```json5
missions: {
  bandits: {
    flag: "garth.bandits",
    given_by: "garth",
    accepted_by: "garth.dialogue.refund.accept_quest",   // dialogue branch id
    stages: {
      none:      { },
      accepted:  {
        triggers: [
          // wilderness encounter that completes the find-the-cargo step
          { on: "wilderness_encounter", chance: 9, foe: "bandit",
            do: ["seq",
              ["say", "mission.bandits.attacked"],
              ["pause"],
              ["spawn_encounter", { foe: "bandit", n: 8, mandatory: true }],
              ["say", "mission.bandits.cargo_found"],
              ["pause"],
              ["set_flag", "garth.bandits", 2]] },
        ],
      },
      recovered: {
        // turn-in via dialogue; see npcs.garth.dialogue."refund.accept"
        rewards: { gold: 2000, item: "ruby" },
      },
      closed: { },
    },
  },

  troll_tooth: {
    flag: "hermit.troll_tooth",
    given_by: "hermit",
    stages: {
      accepted:  {
        triggers: [
          { on: "wilderness_encounter", chance: 9, foe: "troll",
            do: ["seq",
              ["spawn_encounter", { foe: "troll", mandatory: true }],
              ["say", "mission.troll_tooth.extracted"],
              ["set_flag", "hermit.troll_tooth", 2]] },
        ],
      },
      extracted: {
        triggers: [
          { on: "step_on_coord", coord: ["dx[1]", "dy[1]"],
            do: ["call", "lib.dungeon_skull_prompt"] },
        ],
      },
      delivered: { rewards: { gold: 5000 } },
    },
  },

  // example trigger types beyond the two above:
  example_terrain_event: {
    flag: "example.flag",
    stages: {
      accepted: {
        triggers: [
          // First time the party steps on a mountain tile after the mission
          // is accepted. State carried in `mission_state.example.mountain_seen`.
          { on: "first_terrain_step", terrain: "m",
            do: ["seq",
              ["say", "example.first_mountain"],
              ["set_flag", "example.flag", 2]] },
          { on: "step_on_coord", coord: [42, 23],
            do: ["call", "lib.cave_entrance"] },
          { on: "stage_entered",       // fires once when stage becomes active
            do: ["say", "example.go_find_x"] },
          { on: "stage_exited",        // fires once when stage advances
            do: ["say", "example.farewell"] },
          { on: "day_passes", count: 5,   // fires every 5 in-game days
            do: ["say", "example.urgency"] },
          { on: "talk_to_npc", npc: "hermit",
            do: ["seq", ["say", "example.hint"], ["pause"]] },
        ],
      },
    },
  },
}
```

**Trigger types** (the closed set the engine knows how to evaluate):

| `on` | fired when | extra fields |
|------|-----------|--------------|
| `wilderness_encounter` | a slumpvis-driven combat begins | `chance` (% override), `foe` (filter) |
| `step_on_coord` | party x/y matches | `coord: [x, y]` (path or literal) |
| `step_on_terrain` | party moves onto matching tile | `terrain: <legend-key>` |
| `first_terrain_step` | first matching tile since stage started | `terrain` |
| `stage_entered` | stage becomes active (flag transitions in) | — |
| `stage_exited` | stage becomes inactive (flag transitions out) | — |
| `talk_to_npc` | dialogue branch entered | `npc`, optional `branch` |
| `day_passes` | in-game day rollover | `count` (every N) |

Engine maintains `mission_state.<id>.<bookkeeping>` to back the
"first/since" predicates (e.g. a bit set per terrain glyph for
`first_terrain_step`). Bookkeeping is per-party, persisted alongside
`p[i].flag[]`.

### 3.10 `lib`: shared subtrees

For the inevitable "every town has the same church menu" duplication,
declare a top-level `lib` and reference it from anywhere:

```json5
lib: {
  "rumour": { do: ["seq", /* the 33-case switch from towne.c rumour() */] },
  "church.menu": { do: ["seq", /* the heal/resurrect/bless menu */] },
  "inn.menu":    { do: ["seq", /* room rental + camp prompt */] },
  // ...
},
```

`["call", "lib.<id>"]` is an effect that evaluates the named library node's
`do` body in the current binding environment (so `speaker` and `target`
flow in). Loader resolves the lib id at link time; missing id is fatal.

---

## 4. Loader

```
res_load("data/world.json5")
  -> parse JSON5 into in-memory tree (any C JSON5 parser)
  -> resolve every $import recursively
  -> link cross-tree refs (item ids, flag ids, lib ids, npc ids)
     fail loudly on dangling reference
  -> fold in legacy resources.jsonl records under their home subtrees
  -> hand resolved tree to the runtime
```

After `res_load`, the engine never re-parses or string-compares. All
lookups are O(1) into integer-indexed tables built at load time. The world
tree is read-only; mutable state lives in `ryl_state`.

---

## 5. Runtime APIs

The interpreter has four entry points, each consuming one tree path:

```c
// Dialogue: pick the matching branch under npcs.<npc>.dialogue and run it.
void res_dialogue(const char *npc_id, const char *branch_id);

// Spell: run classes.<class>.spells.<spell>.do (or spells.<id>.do).
void res_spell(const char *class_id, int slot);

// Town service: run towns.<town>.services.<service>.do.
void res_service(const char *town_id, const char *service_id);

// Mission trigger sweep: called on every move and every encounter start;
// walks active stages and fires matching triggers. State machine.
void res_mission_tick(int trigger_kind, /* event-specific payload */);
```

Each entry point pushes its own binding frame (`caster`, `speaker`,
`target`, `$locals`) and tears it down on return.

---

## 6. Inner expression language (LOCKED — verbatim from prior drafts)

```bnf
; conditions
cond  ::= ["and",  cond, cond, …]
        | ["or",   cond, cond, …]
        | ["not",  cond]
        | ["flag_eq",   flag-ref, int]
        | ["has_item",  item-id [, count]]
        | ["gold_gte",  int-expr]
        | ["in_dungeon"] | ["in_town"] | ["in_wilderness"]
        | ["class_is",  actor-ref, class-name]
        | ["alive",     actor-ref]
        | ["chance",    int]                        ; pct, true with prob pct/100
        | ["eq", expr, expr] | ["lt", expr, expr] | ["gte", expr, expr]

; effects
effect ::= ["seq", effect, effect, …]
         | ["if",  cond, effect [, effect]]         ; then [else]
         | ["say", text-id]
         | ["pause"] | ["clw"]
         | ["set_flag", flag-ref, int]
         | ["give_gold", int-expr] | ["take_gold", int-expr]
         | ["give_item", item-id [, count]] | ["take_item", item-id [, count]]
         | ["set", path, expr] | ["add", path, expr] | ["sub", path, expr]
         | ["ask_target", target-kind]              ; binds `target`
         | ["require", cond]                        ; aborts enclosing seq on false
         | ["journal", text-id]
         | ["spawn_encounter", encounter-spec]      ; id or {foe,n,mandatory}
         | ["call", lib-id]                         ; inline a lib node's do

; expressions
expr ::= int | string | path
       | ["+", expr, expr, …] | ["-", expr, expr]
       | ["*", expr, expr, …] | ["/", expr, expr]
       | ["min", expr, …] | ["max", expr, …] | ["clamp", expr, expr, expr]
       | ["rand", int, int]                         ; inclusive both ends
       | ["cond", cond, expr, expr]                 ; ternary

; references
path  ::= actor "." field | "state." state-field | "party." party-field
        | "npc[" int "]." npc-field | "$" ident    ; $ident = local binding
actor ::= "caster" | "target" | "speaker" | "self"
field ::= "hp" | "h" | "ma" | "wi" | "lv" | "ac" | "dead" | "name"
        | "cl" | "gold" | "inventory[" int "]" | "visited"
flag-ref  ::= "ident.ident…"     ; symbolic, resolved via flags subtree
text-id   ::= "ident.ident…"
item-id   ::= int | "ident"      ; ident resolved via items subtree
target-kind ::= "party_alive" | "party_any" | "party_dead"
              | "monster_alive" | "monster_any" | "self"
```

Targeting, evaluation order, short-circuit, `require` semantics, missing-id
fatality, integer width — all unchanged from the prior draft (commit
`99e3602` §6); see that history for the rationale.

`["chance", N]` is new sugar over `["lt", ["rand", 0, 99], N]` matching the
runtime `ryl_chance(N)` helper. Same probability, less line noise.

---

## 7. Worked examples

### 7.1 Church heal (`towne.c:661-695`)

Tree path: `lib.church.menu.do` (referenced by every town's `services.church.do`).

```json5
do: ["seq",
  ["ask_target", "party_alive"],
  ["if", ["eq", "target.hp", "target.h"],
    ["seq", ["say", "church.heal.already_full"], ["pause"]],
    ["seq",
      ["say", "church.heal.quote"],
      ["set", "$cost", ["*", 7, ["-", "target.h", "target.hp"]]],
      ["if", ["gold_gte", "$cost"],
        ["seq", ["take_gold", "$cost"],
                ["set", "target.hp", "target.h"],
                ["say", "church.heal.success"]],
        ["say", "church.heal.no_gold"]]]]]
```

### 7.2 Garth refund (`speak.c:401-447`, `flag[1] == 2`)

Tree path: `npcs.garth.dialogue."refund.accept"`. Garth is npc 1 = merchant
(per `person.c:76-81` and `ryl.c:45`); the "refund" is the bandit-cargo
recovery payoff.

```json5
"refund.accept": {
  when: ["flag_eq", "garth.bandits", 2],
  do: ["seq",
    ["say", "garth.refund.grateful"],
    ["give_item", "ruby"],
    ["give_gold", 2000],
    ["pause"],
    ["set_flag", "garth.bandits", 3]],
}
```

### 7.3 mage.frost (`strid.c:442-478`, `d == '1'`)

Tree path: `spells."mage.frost"`.

```json5
do: ["seq",
  ["require", ["not", ["flag_eq", "encounter.immune", 1]]],
  ["require", ["gte", "caster.ma", ["+", "base_cost", "caster.lv"]]],
  ["sub", "caster.ma", ["+", "base_cost", "caster.lv"]],
  ["ask_target", "monster_alive"],
  ["set", "$dmg", ["*", "caster.lv", ["+", ["rand", 0, 6], 2]]],
  ["say", "spell.mage.frost.hit"],
  ["sub", "target.hp", "$dmg"],
  ["if", ["lt", "target.hp", 1],
    ["seq", ["set", "target.hp", 0], ["set", "target.dead", 1],
           ["say", "common.killed"]]]]
```

### 7.4 Bandits mission's "wilderness intercept"

Trigger evaluated by `res_mission_tick(WILDERNESS_ENCOUNTER, ...)` once per
spawned encounter, before strid.c displays the foe.

```json5
missions.bandits.stages.accepted.triggers: [
  { on: "wilderness_encounter", chance: 9,
    do: ["seq",
      ["say", "mission.bandits.attacked"],
      ["pause"],
      ["spawn_encounter", { foe: "bandit", n: 8, mandatory: true }],
      ["say", "mission.bandits.cargo_found"],
      ["pause"],
      ["set_flag", "garth.bandits", 2]] },
]
```

`chance: 9` matches `missions.c:52` (`rand() % 100 > 90`); the engine evaluates
the trigger only after a base wilderness encounter has been picked, then
overrides it.

### 7.5 First-mountain trigger

Demonstrates `first_terrain_step` and the `mission_state` bookkeeping the
engine carries:

```json5
missions.example.stages.accepted.triggers: [
  { on: "first_terrain_step", terrain: "m",
    do: ["seq",
      ["clw"],
      ["say", "example.first_mountain.flavor"],
      ["pause"],
      ["set_flag", "example.flag", 2]] },
]
```

After the trigger fires once, the engine sets
`mission_state.example.first_terrain_step.m = 1` so a subsequent step on
mountain doesn't re-fire.

---

## 8. Boundaries

Things deliberately **out of scope** for the world DSL:

- **UI layout.** The four-corner status line, map window, MORE prompt
  rendering — staying in C primitives. The DSL only emits text; layout is
  the engine's job.
- **Save format.** `realm.dat` / `npc.dat` byte layout is unchanged. The
  DSL describes content; the save snapshots state.
- **RNG implementation.** `ryl_random_init` / `ryl_random` / `ryl_chance`
  live in C. `["rand", lo, hi]` and `["chance", pct]` are leaves that call
  into them; the DSL never seeds or branches on RNG implementation
  details.
- **Networking, file I/O.** No `["http_get"]`, no `["fopen"]`. The
  interpreter is pure with respect to the outside world; only the engine
  owns side channels.

Things deliberately **in scope** but not yet specified:

- **Sextext templating.** `say` records will eventually carry `{he}/{she}`
  slots filled per-target. Current draft punts; narration records are
  pre-rendered with hard-coded pronouns until 6e.
- **Inventory-full handling.** `give_item` to a full pack must reproduce
  the existing "drop one to make room?" prompt (`speak.c:428-440`). DSL
  exposes `give_item` as one primitive; engine implements the prompt.
- **Active mission stages.** Today the `missions` subtree gates progress
  but doesn't itself emit narration on stage transitions beyond the
  trigger-list approach above. A later pass can lift more dialogue branches
  *into* the mission tree if that proves more readable.

---

## 9. Migration plan

1. **Parser + import resolver land first** as a standalone commit on top
   of the existing JSONL loader. Both parsers coexist; loader reads JSON5
   then JSONL; record kinds shadow.
2. **Migrate one record at a time**, preferring leaf-level ports
   (mage.frost first — exercises require/ask_target/rand/sub/kill-on-zero
   in ~10 nodes; byte-identity verified against `strid.c` output via
   `tools/replay.sh title-create-party-combat`).
3. **Per-domain sweeps** once the interpreter handles all primitives a
   domain needs: spells (strid.c + subs.c), then dialogue (speak.c), then
   town services (towne.c), then missions (missions.c + interact.c
   wilderness branch).
4. **Drop `resources.jsonl`** when every record has migrated. Keep the
   per-save persistence files (`realm.dat`, `npc.dat`).
5. **Map externalisation last**. The 6c `map.dat` byte loader works fine;
   replacing it with `map: { $import: "world/map.json5", format: ... }`
   is mechanical once the import resolver exists. No rush.

Each step preserves byte-identity by construction: the C reference and the
DSL-driven runtime emit the same text and consume the same input. The
replay harness gates every commit.

---

## 10. Open questions (deferred)

1. **Let-bindings.** Examples 7.1 and 7.3 use `$locals`. Formalise as
   `["let", [[name, expr], …], effect]` if a third site needs it.
2. **`require` non-local exit.** Aborts enclosing `seq` only. If a deeper
   exit is ever needed, add `["exit", level]`. Probably not.
3. **Multiple matching `when` guards.** File order wins. Codify when the
   first conflict appears.
4. **Trigger frequency knobs.** `day_passes` / `step_on_terrain` could
   gain a `cooldown` field; defer until used.
5. **Typed load pass.** A schema validator (e.g. mismatched arg counts on
   effects, wrong-typed paths) would catch a lot of hand-edit bugs at
   load. Add when a designer asks.
6. **Text record templating.** Sextext, capitalisation, list-joining
   helpers. Defer to 6e.
7. **Library node parameters.** `["call", "lib.x"]` runs in the caller's
   environment. If a lib needs explicit args, generalise to
   `["call", "lib.x", { arg: val, … }]`. Defer.
8. **Auto line-wrap for paragraph-form texts.** Today every text record
   is either a string (verbatim) or an array of lines joined with `\r\n`
   and a trailing `\r\n` — authors control every break. `meta.text_window`
   (`max_lines` / `max_cols`) records the target geometry but doesn't
   drive formatting yet. When migration is complete and byte-identity is
   no longer the correctness gate, introduce a third leaf form: a single
   long string (JSON5 multiline OK) that the loader word-wraps at
   `max_cols`, pushing breaks at spaces and emitting `\r\n`. Array form
   remains as an escape hatch for author-forced breaks. Rationale: the
   existing breaks were authorial judgment calls the original runtime
   can't reproduce from a wrapper; enforcing wrap now would re-bless
   every replay baseline, which we only want to do once the evaluator
   is actually generating most of the text on its own.
9. **Longer text conveniences.** Once (8) lands: accept JSON5 multiline
   string literals for records that span many visual lines; allow
   `{ wrap: false, text: "..." }` form to opt a specific record back
   out of wrapping; allow `{ lines: [...] }` as an explicit-lines form
   for symmetry with wrap-enabled records. None of this needs to land
   before the world + save split; all three are compatible additions
   to the existing array-of-strings convention.
