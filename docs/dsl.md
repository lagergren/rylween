# Phase 6d DSL: a shared condition/effect language for dialogue and spells

## 1. Motivation

Phase 6c extracted static text to `data/resources.jsonl`. Phase 6d replaces the
remaining *behavioural* code in `speak.c` (dialogue branching on mission flags,
conditional rewards) and in the spell effect bodies of `subs.c` / `strid.c`
with data. Both domains boil down to the same shape — *gate on state, mutate
state, emit narration* — so one small DSL replaces two ad-hoc interpreters and
keeps the port-plan's ~90% primitive overlap between dialogue and spells
(port-plan.md §9, "Spell effects share this DSL").

The game world is hierarchical: classes contain spells, NPCs own dialogue
branches, missions own stages, towns own services. Expressing that hierarchy
flatly — as thousands of sibling records with string ids cross-referencing
each other — loses structure that is load-bearing for a human editor. We
carry the hierarchy into the file.

## 2. World file: hierarchical JSON5

The world is one **JSON5 document** at `data/world.json5` (or a small set of
`data/world/*.json5` documents unioned at load). One top-level object, one
root per ontology branch:

```json5
{
  // --- static metadata ------------------------------------------------
  flags: {
    // Symbolic names for the integer flag slots scattered through
    // p[cpl].flag[...] and npc[...].flag[...]. Replaces the bare
    // integer indices the C port still uses.
    "garth.bandits":        { slot: 1, values: ["none", "accepted", "recovered", "closed"] },
    "church.donation.once": { slot: 5, values: ["no", "yes"] },
  },

  items: {
    // Parallel to the existing {"kind":"item"} records in resources.jsonl;
    // those get folded in at migration.
    ruby:       { id: 111, cost: 0,     damage: 0 },
    fireshield: { id:  27, cost: 15000, damage: 20 },
  },

  // --- behaviour ------------------------------------------------------
  classes: {
    mage: {
      prereq: { wi_gte: 50 },
      spells: {
        frost: {
          slot: 1, base_cost: 8,
          do: ["seq",
            ["require", ["not", ["flag_eq", "encounter.immune", 1]]],
            ["require", ["gte", "caster.ma", ["+", "base_cost", "caster.lv"]]],
            ["sub", "caster.ma", ["+", "base_cost", "caster.lv"]],
            ["ask_target", "monster_alive"],
            ["set", "$dmg", ["*", "caster.lv", ["+", ["rand", 0, 7], 2]]],
            ["say", "spell.mage.frost.hit"],
            ["sub", "target.hp", "$dmg"],
            ["if", ["lt", "target.hp", 1],
              ["seq", ["set", "target.hp", 0], ["set", "target.dead", 1],
                     ["say", "common.killed"]]]],
        },
        // fire, hold, confusion, annihilate, ...
      },
    },

    priest: {
      prereq: { wi_gte: 50 },
      spells: {
        banish: {
          slot: 4, base_cost: 85,
          do: ["seq",
            ["require", ["gte", "caster.ma", ["+", "base_cost", "caster.lv"]]],
            ["require", ["in_dungeon"]],
            ["sub", "caster.ma", ["+", "base_cost", "caster.lv"]],
            ["say", "spell.priest.banish.narration"],
            ["pause"],
            ["set", "state.banished", 1]],
        },
        // heal, teleport, earthquake, ...
      },
    },

    fighter: { prereq: { ws_gte: 50 }, spells: {} },
    thief:   { prereq: {},             spells: {} },
    bard:    { prereq: { ch_gte: 50 }, spells: {} },
    ranger:  { prereq: {},             spells: {} },
  },

  npcs: {
    garth: {
      id: 1, job: "merchant",           // per person.c:76-81, ryl.c:45
      dialogue: {
        greeting: {
          // First visit vs. repeat — same pattern speak.c:124-131 uses today.
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
      },
    },
    // nrim'to, areth, bernak, ...
  },

  towns: {
    // services live under a town because today's code keys on state.twn.
    tardos: {
      church: {
        priestess: {
          services: {
            heal: {
              do: ["seq",
                ["ask_target", "party_alive"],
                ["if", ["eq", "target.hp", "target.h"],
                  ["seq", ["say", "church.heal.already_full"], ["pause"]],
                  ["seq",
                    ["say", "church.heal.quote"],
                    ["if", ["gold_gte", ["*", 7, ["-", "target.h", "target.hp"]]],
                      ["seq",
                        ["take_gold", ["*", 7, ["-", "target.h", "target.hp"]]],
                        ["set", "target.hp", "target.h"],
                        ["say", "church.heal.success"]],
                      ["say", "church.heal.no_gold"]]]]],
            },
            // resurrect, bless, cure_curse, ...
          },
        },
      },
      // inn, merchant, tavern, smith, ...
    },
  },

  missions: {
    bandits: {
      flag: "garth.bandits",
      stages: {
        // Each stage is addressable by the flag value it gates on.
        accepted:  { reward: null },
        recovered: { reward: { gold: 2000, item: "ruby" } },
        closed:    { reward: null },
      },
    },
  },
}
```

Shape, not byte-count: every domain (classes, npcs, towns, missions) is one
subtree. A record's *path* (`classes.mage.spells.frost`) is its identity; no
string cross-reference needed inside the tree. Cross-tree references
(`"garth.bandits"`, `"ruby"`) still go through `flags` and `items`.

## 3. Why JSON5 over JSONL

The existing `data/resources.jsonl` is line-delimited and parses today with
`fgets` + `cJSON` (per port-plan §9, the 6b entry is already JSON5). The flat
format has run its course for 6d because:

- **Hierarchy is the type.** `classes.mage.spells.frost` *is* the spell's
  identity. A flat `{"kind":"spell","id":"mage.frost","class":"mage",...}`
  duplicates the hierarchy into strings and loses editor assists (fold the
  `mage` node, jump-to-definition, diff a whole subtree).
- **Comments and trailing commas.** Designers hand-edit this. JSON5
  (`// comments`, `/* … */`, trailing commas, unquoted keys, single or
  double quotes) is the minimum civil standard. JSONL gives you none of
  those.
- **Local context is visible.** Sibling spells are adjacent lines in the
  file, not scattered across records sorted by id. An editor can see the
  whole `priest` class in one screen.
- **Cost.** A single-file parser (JSON5 → tree) lands once; from then on,
  the loader walks the tree instead of dispatching on `"kind"` at every
  record. Interpreter stays a tree walker; the walker's input is just one
  tree instead of N records it has to index first.

The leaves stay as s-expr-in-JSON arrays (`["seq", ...]`, `["if", ...]`) so
the *expression* language has no new syntax — only the *world file* gains
structure. That's the important separation: hierarchy in JSON5, semantics in
array-ops.

**Parser choice.** Use an existing C JSON5 library (e.g. json5-c, json-c
with extensions, or hand-roll ~400 LOC on top of the current JSON parser;
JSON5's extensions over JSON are small). Not a new task parser, not a new
lexer. Byte-for-byte load determinism still holds because the emitter is us.

**Compatibility with existing records.** The 6a/6b records (items, classes,
enums) fold in under their home subtrees. Records not yet converted stay in
`resources.jsonl`; the loader reads both. No flag day.

## 4. Design goals

- **Single interpreter, two domains.** Dialogue effects and spell effects run
  through the same `eval_cond` / `eval_effect` pair.
- **Hierarchical world file.** The *data* reflects the game's ontology;
  cross-references are reserved for genuinely shared entities (flags, items,
  text ids).
- **Determinism.** `rand` routes through `ryl_srand` / `ryl_rand` so that
  `RYL_FAKE_SEED` pins replay output byte-for-byte (see commit 5c75637).
- **Small, known grammar.** The surface expression syntax is JSON arrays:
  `["+", "target.hp", 10]`. JSON5 wrapper + JSON-array leaves = a tree walker
  over parsed values. No new lexer, no new parser generator.
- **Byte-identity with the C reference.** Phase 6d migrates one record at a
  time; replay harness (`tools/replay.sh`) must stay green after each.
- **No side-channel state.** Every read and write is a named path
  (`caster.ma`, `state.tid`, `party.gold`, `npc[1].flag[2]`). No hidden
  globals — `ryl_state *s` is the interpreter's single root.
- **Missing ids are loud.** An unresolved `say` id, flag name, or path is a
  fatal load-time error, not a silent empty string. 6c policy; 6d inherits it.

## 5. Grammar

The grammar has two layers: the **outer world file** (JSON5 tree) and the
**inner expression language** (JSON arrays, unchanged from the flat-record
draft).

### 5.1 Outer: world-file shape (JSON5)

```bnf
world         ::= '{' top-entry (',' top-entry)* '}'
top-entry     ::= '"flags"'     ':' '{' flag-entry    (',' flag-entry)*    '}'
                | '"items"'     ':' '{' item-entry    (',' item-entry)*    '}'
                | '"classes"'   ':' '{' class-entry   (',' class-entry)*   '}'
                | '"npcs"'      ':' '{' npc-entry     (',' npc-entry)*     '}'
                | '"towns"'     ':' '{' town-entry    (',' town-entry)*    '}'
                | '"missions"'  ':' '{' mission-entry (',' mission-entry)* '}'

class-entry   ::= ident ':' '{' "prereq" ':' prereq ','
                                "spells" ':' '{' spell-entry* '}' '}'
spell-entry   ::= ident ':' '{' "slot" ':' int ',' "base_cost" ':' int ',' "do" ':' effect '}'
npc-entry     ::= ident ':' '{' "id" ':' int ',' "job" ':' string ','
                                "dialogue" ':' '{' diag-entry* '}' '}'
diag-entry    ::= ident ':' '{' [ "when" ':' cond ',' ] "do" ':' effect '}'
town-entry    ::= ident ':' '{' ( "church" | "inn" | "merchant"
                                | "tavern" | "smith" ) ':' service-node ',' ... '}'
service-node  ::= '{' ident ':' '{' "services" ':' '{' service-entry* '}' '}' '}'
service-entry ::= ident ':' '{' [ "when" ':' cond ',' ] "do" ':' effect '}'
mission-entry ::= ident ':' '{' "flag" ':' flag-ref ',' "stages" ':' '{' stage-entry* '}' '}'
stage-entry   ::= ident ':' '{' [ "reward" ':' reward ] '}'
prereq        ::= '{' ( ('ws_gte'|'wi_gte'|'h_gte'|'ch_gte') ':' int )* '}'
reward        ::= 'null' | '{' ( "gold" ':' int | "item" ':' item-id )* '}'
```

JSON5 accepts unquoted keys and trailing commas; the BNF above shows the
strict JSON form — real files lean into the JSON5 affordances.

### 5.2 Inner: expression language (unchanged from first draft)

```bnf
; --- conditions -----------------------------------------------------------
cond          ::= '["and"' ',' cond (',' cond)* ']'
                | '["or"'  ',' cond (',' cond)* ']'
                | '["not"' ',' cond ']'
                | '["flag_eq"' ',' flag-ref ',' int ']'
                | '["has_item"' ',' item-id [ ',' count ] ']'
                | '["gold_gte"' ',' int-expr ']'
                | '["in_dungeon"]' | '["in_town"]' | '["in_wilderness"]'
                | '["class_is"' ',' actor-ref ',' class-name ']'
                | '["alive"' ',' actor-ref ']'
                | '["eq"' ',' expr ',' expr ']'
                | '["lt"' ',' expr ',' expr ']' | '["gte"' ',' expr ',' expr ']'

; --- effects --------------------------------------------------------------
effect        ::= '["seq"' ',' effect (',' effect)* ']'
                | '["if"' ',' cond ',' effect [ ',' effect ] ']'   ; then [else]
                | '["say"' ',' text-id ']'
                | '["pause"]' | '["clw"]'
                | '["set_flag"' ',' flag-ref ',' int ']'
                | '["give_gold"' ',' int-expr ']'
                | '["take_gold"' ',' int-expr ']'
                | '["give_item"' ',' item-id [ ',' count ] ']'
                | '["take_item"' ',' item-id [ ',' count ] ']'
                | '["set"'    ',' path ',' expr ']'
                | '["add"'    ',' path ',' expr ']'
                | '["sub"'    ',' path ',' expr ']'
                | '["ask_target"' ',' target-kind ']'   ; binds `target`
                | '["require"' ',' cond ']'             ; aborts effect on false
                | '["journal"' ',' text-id ']'
                | '["spawn_encounter"' ',' encounter-id ']'

target-kind   ::= '"party_alive"' | '"party_any"' | '"party_dead"'
                | '"monster_alive"' | '"monster_any"' | '"self"'

; --- expressions ----------------------------------------------------------
expr          ::= int | string | path
                | '["+"' ',' expr (',' expr)+ ']'
                | '["-"' ',' expr ',' expr ']'
                | '["*"' ',' expr (',' expr)+ ']'
                | '["/"' ',' expr ',' expr ']'
                | '["min"' ',' expr (',' expr)+ ']'
                | '["max"' ',' expr (',' expr)+ ']'
                | '["clamp"' ',' expr ',' expr ',' expr ']'   ; x, lo, hi
                | '["rand"' ',' int ',' int ']'               ; inclusive-exclusive
                | '["cond"' ',' cond ',' expr ',' expr ']'    ; ternary

; --- references -----------------------------------------------------------
path          ::= actor '.' field | 'state.' state-field | 'party.' party-field
                | 'npc[' int ']' '.' npc-field
actor         ::= '"caster"' | '"target"' | '"speaker"' | '"self"'
field         ::= 'hp' | 'h' | 'ma' | 'wi' | 'lv' | 'ac' | 'dead' | 'name'
                | 'cl' | 'gold' | 'inventory[' int ']'
state-field   ::= 'banished' | 'tid' | 'day' | 'twn' | 'place'
                | 'last_terrain' | 'hours_pigg'
party-field   ::= 'gold' | 'flag[' int ']' | 'flag.' string
npc-field     ::= 'flag[' int ']' | 'visited[' int ']' | 'towne' | 'x' | 'y'
flag-ref      ::= '"' ident ('.' ident)* '"'    ; symbolic, resolved via flags subtree
actor-ref     ::= '"caster"' | '"target"' | ('"m"' int)  ; party slot 1..4
text-id       ::= '"' ident ('.' ident)* '"'
item-id       ::= int | '"' ident '"'           ; ident resolved via items subtree
int-expr      ::= expr constrained to integers
```

The JSON framing (`[op, arg, arg, …]`) is the whole surface syntax for
expressions; everything else is a naming convention enforced by the
interpreter.

## 6. Semantics

**Evaluation order.** `seq` is left-to-right. Arithmetic ops are evaluated
left-to-right with no operator precedence because the tree already makes
precedence explicit. `if` evaluates condition first; only the taken branch is
evaluated.

**Short-circuit.** `and` stops at the first false child; `or` at the first
true; both return the boolean, not the last evaluated expression.

**`require` semantics.** Inside an effect, `require` aborts the enclosing
`seq` (returns cleanly to the caller) when the condition is false. This is
the spell-level "not enough MP / wrong context" gate. It is **not** an
exception — interpreter is exception-free, same convention as the C++17
subset forbidden list (port-plan §6).

**Binding.** `ask_target` reads a keystroke through the existing `key()` path
(so replay scripts still drive it) and binds the resolved actor to the name
`target` in the expression environment. `caster` is bound by the spell entry
point (the party member whose turn chose this spell). In dialogue, `speaker`
is the NPC (the enclosing `npcs.<ident>` node) and `caster` is unbound — use
`"self"` to refer to the party-side `p[cpl]` root, or reference `party.*`
directly.

**Determinism and `rand`.** `["rand", lo, hi]` lowers to `ryl_rand()` (added
in commit 5c75637), which honours `RYL_FAKE_SEED` + `RYL_FAKE_EPOCH`. Two
runs with the same env and the same input keystrokes produce identical
output — the precondition `tools/replay.sh` already depends on. The DSL adds
no new entropy source.

**Missing ids.** `say "priest.heal.unknown"` with no matching text record
aborts at load time (link-style resolution). Same for `flag_eq` against an
unregistered flag name, `give_item "not_a_thing"`, or a cross-tree reference
with no target. No fallback to empty string, no "warn and continue"; this
matches the 6c decision to make content errors fatal so they surface in CI
rather than in a player's save.

**Integer width.** All numeric state is 32-bit `int` on today's port
(`struct party` has `int hp[5]` etc.). The DSL keeps everything in `int32_t`
and does not expose longs. `gold` is `int` too — matches existing struct.

**Side effects outside the tree.** The interpreter may call `tx()`-equivalent
primitives (via `say`, `pause`, `clw`), mutate `ryl_state`, and call into the
inventory helpers (`give_item` goes through the same "drop one to make room"
flow `drop_option()` already implements in `speak.c` — extraction preserves
that behaviour). No direct file I/O, no process control.

**Resolution of cross-tree references.** `"garth.bandits"` in a `flag_eq`
resolves via the `flags` subtree at load; `"ruby"` in `give_item` resolves
via the `items` subtree. The interpreter never does string compares at run
time — these are indices after load.

## 7. Worked examples

Each example shows the DSL leaf (the `do` body) and cites the C function it
replaces. The enclosing tree nodes appear in §2; here we zoom in.

### 7.1 Church heal — corresponds to `church()` H-branch in `towne.c:661-695`

```jsonl
["seq",
  ["ask_target", "party_alive"],
  ["if", ["eq", "target.hp", "target.h"],
    ["seq", ["say", "church.heal.already_full"], ["pause"]],
    ["seq",
      ["say", "church.heal.quote"],
      ["if", ["gold_gte", ["*", 7, ["-", "target.h", "target.hp"]]],
        ["seq",
          ["take_gold", ["*", 7, ["-", "target.h", "target.hp"]]],
          ["set", "target.hp", "target.h"],
          ["say", "church.heal.success"]],
        ["say", "church.heal.no_gold"]]]]]
```

Location in tree: `towns.tardos.church.priestess.services.heal.do`. The
priestess heals one party member for `7 × (missing hp)` gold. Formula from
`towne.c:673-674`: `cost = (h - hp) * 7`. Narration ids live in text records
(`church.heal.quote`, `church.heal.success`, `church.heal.no_gold`).

### 7.2 priest.banish — corresponds to `case 4:` in `subs.c:848-879`

```jsonl
["seq",
  ["require", ["gte", "caster.ma", ["+", "base_cost", "caster.lv"]]],
  ["require", ["in_dungeon"]],
  ["sub", "caster.ma", ["+", "base_cost", "caster.lv"]],
  ["say", "spell.priest.banish.narration"],
  ["pause"],
  ["set", "state.banished", 1]]
```

Location in tree: `classes.priest.spells.banish.do`. Costs `85 + caster.lv`
MP, dungeons only, raises the party-wide `state.banished` flag that combat
code reads. `base_cost` is a sugar name for the enclosing record's own
`base_cost` field — saves repeating `85` twice.

### 7.3 Garth greeting (first-visit vs repeat)

The current `speak()` (`speak.c:124-131`) branches on
`npc[talk].visited[cpl]` for the "welcomes you" vs "nods as he recognizes
you" split.

```jsonl
["if", ["flag_eq", "speaker.visited", 0],
  ["seq",
    ["say", "garth.greet.first"],
    ["set", "speaker.visited", 1]],
  ["say", "garth.greet.repeat"]]
```

Location in tree: `npcs.garth.dialogue.greeting.do`. Generalises to any NPC
record — `speaker` resolves to whichever `npcs.<ident>` node the engine
entered. Pronoun / sex text is left to the narration record or to a `{he}`
template slot (see open questions §8.3).

### 7.4 Garth refund accept — corresponds to `case 1:` flag branch in `speak.c:401-447` (flag[1] == 2)

**Caveat.** Garth is NPC 1 (*merchant*, per `person.c:76-81` and `ryl.c:45`),
not the innkeeper. The "refund" is the bandit-loot-return merchant mission:
player recovers the merchant's cargo, merchant pays 2000 gold plus a ruby.

```jsonl
// guard: {"when": ["flag_eq", "garth.bandits", 2]}
["seq",
  ["say", "garth.refund.grateful"],
  ["give_item", "ruby"],
  ["give_gold", 2000],
  ["pause"],
  ["set_flag", "garth.bandits", 3]]
```

Location in tree: `npcs.garth.dialogue."refund.accept"`. Triggered when the
player returns with flag 2 (cargo recovered). Gives the ruby via the same
`give_item` helper that handles the "inventory full → drop one?" flow
already in `speak.c:426-440`. Advances the flag to 3 (mission closed). The
mission subtree (`missions.bandits.stages.closed`) is the linkage to
progress reporting.

### 7.5 mage.frost — corresponds to `d == '1'` block in `strid.c:442-478`

```jsonl
["seq",
  ["require", ["not", ["flag_eq", "encounter.immune", 1]]],
  ["require", ["gte", "caster.ma", ["+", "base_cost", "caster.lv"]]],
  ["sub", "caster.ma", ["+", "base_cost", "caster.lv"]],
  ["ask_target", "monster_alive"],
  ["set", "$dmg", ["*", "caster.lv", ["+", ["rand", 0, 7], 2]]],
  ["say", "spell.mage.frost.hit"],
  ["sub", "target.hp", "$dmg"],
  ["if", ["lt", "target.hp", 1],
    ["seq", ["set", "target.hp", 0], ["set", "target.dead", 1],
           ["say", "common.killed"]]]]
```

Location in tree: `classes.mage.spells.frost.do`. Costs `8 + caster.lv` MP,
fizzles if the encounter is flagged immune, damages a single target for
`caster.lv × (rand(0..6) + 2)` (exact match for `strid.c:464`:
`p[cpl].lv[g] * ((rand() % 7) + 2)`). Kills the target if hp ≤ 0. The
`$dmg` local is a temporary binding scoped to this `seq`.

## 8. Open questions (defer to interpreter)

1. **Temporaries / let-bindings.** Example 7.5 uses `$dmg`. The grammar
   doesn't formalise a `let` — it just says `set "$dmg", …`. Cleaner would
   be `["let", [["dmg", expr]], effect]`. Punt until the second example
   actually needs it.
2. **`require` vs `if`.** `require` aborts the enclosing `seq`. If someone
   wants a non-local exit past two levels of `seq`, we need a labelled
   version. Probably don't.
3. **Pronoun / sextext resolution.** The C code threads gender through
   `sextext(who, case)` (see `subs.c:254`, cases in `subs.c:841-843`). Open
   question: does `say` auto-template `{he}/{she}`, or does every narration
   record carry its own gender-bearing id? Lean toward the former (one
   record, runtime fills), but defer until 6e.
4. **Inventory-full handling.** `give_item` needs to preserve the exact
   "you can't carry it / drop option" user flow from `speak.c:428-440`.
   Either the DSL exposes `drop_option` as a primitive, or `give_item` is
   opinionated and implements it internally. Latter is less flexible but
   matches today's behaviour 1:1.
5. **Mission state machines.** Missions are the *reason* flags exist but
   they are not themselves DSL expressions; they reference dialogue and
   spell records by tree path. The `missions` subtree (§2) is descriptive
   today — it gates nothing directly. A later pass can make stages *active*
   (trigger narration on advance, etc.).
6. **Ordering of `do` vs `when`.** A dialogue entry can have a `when` guard
   that the engine evaluates before choosing which line to speak. Multiple
   entries under the same NPC with overlapping `when` need a priority rule.
   Simplest: file order wins. Formalise when 6d's second NPC has
   overlapping branches — `speak.c:case 3` already does (flag[3] == 0 vs
   flag[3] == 3).
7. **Action costs in dialogue.** The `garth.refund` pays gold; the
   `church.heal` takes gold. Should "take_gold + fail if short" be one
   primitive? Could replace the `gold_gte` + `take_gold` pair with
   `["charge", N, else-effect]`. Defer — two uses isn't enough to abstract.
8. **Types.** Today the DSL is untyped (values are `int`-or-string). A
   typed IR would catch `say` vs `set_flag` mix-ups at load time. If the
   resource-lint tool (`tools/replay.sh`'s sibling) ever grows, a typed
   load pass is the cheapest place to add it.
9. **File split vs single file.** The whole world in one `data/world.json5`
   vs one-file-per-subtree (`data/world/classes.json5`,
   `data/world/npcs/garth.json5`, ...). Both load into the same tree.
   Start single-file; split when one node exceeds ~500 lines. Don't split
   preemptively — the reason to use a tree is to *see* siblings.
10. **Migration path from `resources.jsonl`.** The 6a/6b/6c records already
    live in JSONL. Migrate them into `world.json5` lazily — the loader
    reads both; a JSONL record shadows a JSON5 record of the same id only
    during the transition window. Delete `resources.jsonl` once every
    record has moved. (Not before: a flag-day migration fights byte-
    identity too hard.)
