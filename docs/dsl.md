# Phase 6d DSL: a shared condition/effect language for dialogue and spells

## 1. Motivation

Phase 6c extracted static text to `data/resources.jsonl`. Phase 6d replaces the
remaining *behavioural* code in `speak.c` (dialogue branching on mission flags,
conditional rewards) and in the spell effect bodies of `subs.c` / `strid.c`
with data. Both domains boil down to the same shape — *gate on state, mutate
state, emit narration* — so one small DSL replaces two ad-hoc interpreters and
keeps the port-plan's ~90% primitive overlap between dialogue and spells
(port-plan.md §9, "Spell effects share this DSL").

## 2. Design goals

- **Single interpreter, two domains.** Dialogue effects and spell effects run
  through the same `eval_cond` / `eval_effect` pair.
- **Determinism.** `rand` routes through `ryl_srand` / `ryl_rand` so that
  `RYL_FAKE_SEED` pins replay output byte-for-byte (see commit 5c75637).
- **No new parser.** The surface syntax is JSON arrays ("s-expr in JSON"):
  `["+", "target.hp", 10]`. JSONL already parses; the DSL is just a tree walker
  over parsed arrays. Hand-editing stays readable because arrays nest shallowly.
- **Byte-identity with the C reference.** Phase 6d migrates one record at a
  time; replay harness (`tools/replay.sh`) must stay green after each.
- **No side-channel state.** Every read and write is a named path
  (`caster.ma`, `state.tid`, `party.gold`, `npc[1].flag[2]`). No hidden
  globals — `ryl_state *s` is the interpreter's single root.
- **Missing ids are loud.** An unresolved `say` id, flag name, or path is a
  fatal load-time error, not a silent empty string. 6c policy; 6d inherits it.

## 3. Grammar (BNF)

```bnf
record        ::= '{' "kind" ':' kind-tag ',' "id" ':' string
                      [ ',' "when"   ':' cond   ]
                      [ ',' "do"     ':' effect ]
                      [ ',' record-specific-fields ] '}'

kind-tag      ::= '"dialogue"' | '"spell"' | '"mission_stage"'

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
flag-ref      ::= '"' ident ('.' ident)* '"'    ; symbolic, resolved via res_flag()
actor-ref     ::= '"caster"' | '"target"' | ('"m"' int)  ; party slot 1..4
text-id       ::= '"' ident ('.' ident)* '"'
item-id       ::= int | '"' ident '"'
int-expr      ::= expr constrained to integers
```

One page. The JSON framing (`[op, arg, arg, …]`) is the whole surface syntax;
everything else is a naming convention enforced by the interpreter.

## 4. Semantics

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
is the NPC (the node's `"npc"` field) and `caster` is unbound — use `"self"`
to refer to the party-side `p[cpl]` root, or reference `party.*` directly.

**Determinism and `rand`.** `["rand", lo, hi]` lowers to `ryl_rand()` (added
in commit 5c75637), which honours `RYL_FAKE_SEED` + `RYL_FAKE_EPOCH`. Two
runs with the same env and the same input keystrokes produce identical
output — the precondition `tools/replay.sh` already depends on. The DSL
adds no new entropy source.

**Missing ids.** `say "priest.heal.unknown"` with no matching
`{"kind":"text","id":"priest.heal.unknown",...}` record aborts at
`res_load()` time (link-style resolution). Same for `flag_eq` against an
unregistered flag name. No fallback to empty string, no "warn and
continue"; this matches the 6c decision to make content errors fatal so
they surface in CI rather than in a player's save.

**Integer width.** All numeric state is 32-bit `int` on today's port
(`struct party` has `int hp[5]` etc.). The DSL keeps everything in `int32_t`
and does not expose longs. `gold` is `int` too — matches existing struct.

**Side effects outside the tree.** The interpreter may call `tx()`-equivalent
primitives (via `say`, `pause`, `clw`), mutate `ryl_state`, and call into
the inventory helpers (`give_item` goes through the same "drop one to make
room" flow `drop_option()` already implements in `speak.c` — extraction
preserves that behaviour). No direct file I/O, no process control.

## 5. Worked examples

Each shows the DSL, what it expresses in one sentence, and the C function it
replaces.

### 5.1 priest.heal (town church) — corresponds to `church()` H-branch in `towne.c:661-695`

```jsonl
{"kind":"dialogue","id":"church.heal.offer","npc":"church.priestess","do":
  ["seq",
    ["ask_target","party_alive"],
    ["if",["eq","target.hp","target.h"],
      ["seq",["say","church.heal.already_full"],["pause"]],
      ["seq",
        ["say","church.heal.quote"],
        ["if",["gold_gte",["*",7,["-","target.h","target.hp"]]],
          ["seq",
            ["take_gold",["*",7,["-","target.h","target.hp"]]],
            ["set","target.hp","target.h"],
            ["say","church.heal.success"]],
          ["say","church.heal.no_gold"]]]]]}
```

The priestess heals one party member for `7 × (missing hp)` gold. Exact
formula from `towne.c:673-674`: `cost = (h - hp) * 7`. Narration ids
(`church.heal.quote`, `church.heal.success`, `church.heal.no_gold`) live in
text records.

### 5.2 priest.banish (combat spell, priest slot 4) — corresponds to `case 4:` in `subs.c:848-879`

```jsonl
{"kind":"spell","id":"priest.banish","class":"priest","slot":4,"base_cost":85,"do":
  ["seq",
    ["require",["gte","caster.ma",["+","base_cost","caster.lv"]]],
    ["require",["in_dungeon"]],
    ["sub","caster.ma",["+","base_cost","caster.lv"]],
    ["say","spell.priest.banish.narration"],
    ["pause"],
    ["set","state.banished",1]]}
```

Costs `85 + caster.lv` MP, only in dungeons, raises the party-wide
`state.banished` flag that combat code reads. `base_cost` is a sugar name
for the record's own `base_cost` field — saves repeating `85` twice.

### 5.3 merchant.greeting (first-visit vs repeat)

The current `speak()` (`speak.c:124-131`) already branches on
`npc[talk].visited[cpl]` to pick "welcomes you" vs "nods as he recognizes
you". The DSL expresses the same pattern explicitly, and generalises it to
any NPC record:

```jsonl
{"kind":"dialogue","id":"merchant.greeting","npc":"merchant","do":
  ["seq",
    ["if",["flag_eq","speaker.visited",0],
      ["seq",
        ["say","merchant.greet.first"],
        ["set","speaker.visited",1]],
      ["say","merchant.greet.repeat"]]]}
```

First visit: long welcome line, set visited. Repeat: one-liner. This also
removes the hardcoded gendered `he/she recognizes you` split — the `say`
targets can fork on `speaker.job == bargirl` inside the text record via a
different id, or the text itself can embed `{pronoun}` and the interpreter
resolves via `sextext`-equivalent.

### 5.4 garth.refund.accept — corresponds to `case 1:` flag branch in `speak.c:401-447` (flag[1] == 2)

Note: Garth is NPC 1 (a *merchant*, per `person.c:76-81` and `ryl.c:45`), not
the innkeeper. The "refund" is the bandit-loot-return mission: player recovers
the merchant's cargo, merchant pays 2000 gold plus a ruby.

```jsonl
{"kind":"dialogue","id":"garth.refund.accept","npc":"garth",
  "when":["flag_eq","garth.bandits",2],"do":
  ["seq",
    ["say","garth.refund.grateful"],
    ["give_item","item.ruby"],
    ["give_gold",2000],
    ["pause"],
    ["set_flag","garth.bandits",3]]}
```

Triggered when the player returns with flag 2 (cargo recovered). Gives the
ruby via the same `give_item` helper that handles the "inventory full → drop
one?" flow already in `speak.c:426-440`. Advances the flag to 3 (mission
closed).

### 5.5 mage.frost (combat) — corresponds to `d == '1'` block in `strid.c:442-478`

```jsonl
{"kind":"spell","id":"mage.frost","class":"mage","slot":1,"base_cost":8,"do":
  ["seq",
    ["require",["not",["flag_eq","encounter.immune",1]]],
    ["require",["gte","caster.ma",["+","base_cost","caster.lv"]]],
    ["sub","caster.ma",["+","base_cost","caster.lv"]],
    ["ask_target","monster_alive"],
    ["set","$dmg",["*","caster.lv",["+",["rand",0,7],2]]],
    ["say","spell.mage.frost.hit"],
    ["sub","target.hp","$dmg"],
    ["if",["lt","target.hp",1],
      ["seq",["set","target.hp",0],["set","target.dead",1],["say","common.killed"]]]]}
```

Costs `8 + caster.lv` MP, fizzles if the encounter is flagged immune,
damages a single target for `caster.lv × (rand(0..6) + 2)` (exact match for
`strid.c:464`: `p[cpl].lv[g] * ((rand() % 7) + 2)`). Kills the target if
hp ≤ 0. The `$dmg` local is a temporary binding scoped to this `seq` — the
narration needs the computed value twice (once to display, once to apply),
so we compute once and store.

## 6. Open questions (defer to interpreter)

1. **Temporaries / let-bindings.** Example 5.5 uses `$dmg`. The grammar
   above doesn't formalise a `let` — it just says `set "$dmg", …`. Cleaner
   would be `["let", [["dmg", expr]], effect]`. Punt until the second
   example actually needs it.
2. **`require` vs `if`.** `require` aborts the enclosing `seq`. If someone
   wants a non-local exit past two levels of `seq`, we need a labelled
   version. Probably don't.
3. **Pronoun / sextext resolution.** The C code threads gender through
   `sextext(who, case)` (see `subs.c:254`, cases in `subs.c:841-843`). Open
   question: does `say` auto-template `{he}/{she}`, or does every narration
   record carry its own gender-bearing id? Lean toward the former
   (one record, runtime fills), but defer until 6e.
4. **Inventory-full handling.** `give_item` needs to preserve the exact
   "you can't carry it / drop option" user flow from `speak.c:428-440`.
   Either the DSL exposes `drop_option` as a primitive, or `give_item` is
   opinionated and implements it internally. Latter is less flexible but
   matches today's behaviour 1:1.
5. **Mission state machines.** §9 of the port plan sketches
   `{"kind":"mission","stages":[…]}`. Missions are the *reason* flags exist
   but they are not themselves DSL expressions; they reference dialogue
   and spell records by id. 6d can ship without a mission record format —
   flags stay integer-valued, and the dialogue records gate on them
   directly, as the examples above do.
6. **Ordering of `do` vs `when`.** Every dialogue record can have a `when`
   guard that the engine evaluates before choosing which line to speak.
   Multiple records with the same `npc` + overlapping `when` need a
   priority rule. Simplest: file order wins. Formalise when 6d's second
   NPC has overlapping branches — `speak.c:case 3` already does (flag[3]
   == 0 vs flag[3] == 3).
7. **Action costs in dialogue.** The `garth.refund` pays gold; the
   `church.heal` takes gold. Should "take_gold + fail if short" be one
   primitive? Could replace the `gold_gte` + `take_gold` pair with
   `["charge", N, else-effect]`. Defer — two uses isn't enough to abstract.
8. **Types.** Today the DSL is untyped (values are `int`-or-string). A
   typed IR would catch `say` vs `set_flag` mix-ups at load time. If the
   resource-lint tool (`tools/replay.sh`'s sibling) ever grows, a typed
   load pass is the cheapest place to add it.
