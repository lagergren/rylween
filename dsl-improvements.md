# Kotlin DSL plan

This is the current direction for the Rylween DSL work. It supersedes the
earlier "tighten the JSON5-ish Kotlin transcription" plan.

The target is not "Kotlin that writes JSON5." The target is a Kotlin DSL that is
the canonical game source: immutable content, mutable game state, authored start
states, and runtime saves all use the same typed model. JSON5 remains useful as
reference material during migration, but it is not the design endpoint.

## Core direction

The ideal source shape is:

```kotlin
rylween {
    content {
        flags { ... }
        worldMap { ... }
        terrain { ... }
        visuals { ... }
        layouts { ... }
        items { ... }
        monsters { ... }
        texts { ... }
        scenes { ... }
        spells { ... }
        missions { ... }
    }

    state("fresh-replay") {
        realm { ... }
        locations { ... }
        npcs { ... }
        party(Parties.replay) { ... }
    }
}
```

`content {}` defines the static world. `state {}` defines a concrete world
state that the engine can boot from. A save game is a materialized `state {}`
snapshot in the same DSL family, not a separate JSON serializer format.

For hand-authored start states, ergonomic defaults are acceptable. For runtime
saves, the output should be exact and non-DRY: every persisted field is present,
with no hidden defaults, loops, random helpers, or derived values. It should be
possible to paste a saved state block back into a Kotlin source file and launch
the game from it.

## Typed authoring model

Use typed handles everywhere practical:

- flags and flag states
- items
- monsters
- encounter tiers
- map ids, map layers, terrain ids, terrain classes
- visual assets, render styles, screen regions, layout ids
- NPCs and NPC roles
- locations
- scenes
- spells
- missions
- text ids
- text placeholder bindings
- native engine calls

The Kotlin author should not write symbolic resource strings inside gameplay
logic. Native C may still store and report slugs for diagnostics and saves, but
the authoring surface should be typed.

## Authoring readability

The DSL should be readable by someone who does not know much Kotlin. Use Kotlin
as the host language, but keep the authoring style close to structured game
notes:

- prefer domain words over programming words
- prefer explicit blocks over clever higher-order helpers
- avoid ordering tricks, delegated properties, reflection, and hidden global
  mutation at authoring sites
- generate typed handle catalogs so authors write `Items.fireWand`, not strings
- keep runtime saves exact and fully expanded, even when authored content uses
  small helpers
- make validation errors describe the game concept, not Kotlin internals

Good:

```kotlin
party(Parties.replay) {
    position = Wilderness.xy(12, 9)
    flags {
        mark(Story.garth.bandits.recovered)
    }
}
```

Avoid authoring surfaces that require understanding Kotlin mechanics before the
game rule is visible.

Keep implementation and authoring separate:

- DSL/kernel code can be idiomatic Kotlin with builders, immutable models, and
  validation helpers.
- Generated handle catalogs can be Kotlin because authors should not edit them.
- Game-specific content should be declared in plain domain blocks. Adding a
  monster, flag, mission, milestone, text, or scene should mean adding a content
  record, not writing a Kotlin class.
- The Kotlin DSL project is now a small Gradle build named `rylween`. Runtime
  DSL/kernel code lives under `src/main/kotlin`: `rylween-dsl.kt` for the
  authoring model, `rylween-dsl-validation.kt` for validation, and
  `rylween-engine.kt` for the backend-neutral engine boundary. The
  game-specific scratch source remains `rylween-full-scratch.dsl.kts`.
  Validate it with:

```sh
./gradlew check
```

Example authoring shape:

```kotlin
content {
    flags {
        story(Story.garth.bandits)
        encounter(Story.encounter.immune)
    }

    monsters {
        monster(Monsters.bandit) {
            name = "Bandit"
            count = 2..6
            hitPoints = 50..99
            weaponSkill = 50
            tier = EncounterTiers.weak
        }
    }

    missions {
        mission(Missions.bandits) {
            randomStep(percent = 9)
            requires(Story.garth.bandits.accepted)
            runs(Scenes.Missions.bandits)
        }
    }
}
```

## Working migration checklist

Use this as the task board for turning the inventory below into reviewed DSL
changes. A task is only done when the current C/JSON5 owner has been audited,
the DSL shape is represented in `dsl-next.kts` or its successor, mutable data
has save-state coverage, native calls are named and typed, and at least one
parity check or replay fixture is planned.

The "DSL delta" text is the concrete thing to look for in the Kotlin diff.

### A. Foundation and authoring surface

- [x] A0. Seed typed DSL direction. DSL delta: `rylween { content { ... } state { ... } }`, typed handles, typed flags, first party-state model. Native decision: JSON5 is reference only, not the target runtime format.
- [ ] A1. Split the prototype into clear authoring sections. DSL delta: stable `content`, `state`, `texts`, `scripts`, and handle catalog sections. Native decision: content compiler owns handle generation. Current scratch status: partial; `rylween-dsl.kt` now holds the DSL implementation, and `rylween-full-scratch.dsl.kts` holds the game authoring area with declarative `flags`, `gameProgress`, `monsters`, `missions`, scripts, and `state` blocks.
- [ ] A2. Define the generated handle catalogs. DSL delta: `Items.*`, `Flags.*`, `Npcs.*`, `Maps.*`, `Terrain.*`, `Texts.*`, `Scenes.*`, `NativeOps.*`. Native decision: C receives dense ids plus diagnostic slugs. Current scratch status: partial in `rylween-dsl.kt`; hand-sketched catalogs now cover the listed handle families plus visuals, layouts, screen regions, roles, spells, missions, key actions, dungeons, towns/buildings, locations, and encounter tiers, but these catalogs are treated as generated output rather than game-designer code.
- [ ] A3. Define typed native-op declarations. DSL delta: `nativeOps { op(NativeOps.renderMap) { ... } }` or equivalent. Native decision: no raw string function names in authored scripts.
- [ ] A4. Define validation error style. DSL delta: validation helpers report game concepts such as missing terrain, text binding, or illegal flag transition. Native decision: loader can reject compiled bundles defensively but author-facing validation happens before C.

### Current scratch decisions and open questions

Decisions from the first `rylween-full-scratch.dsl.kts` pass:

- `rylween-full-scratch.dsl.kts` is the successor scratch source copied from `dsl-next.kts`; `dsl-next.kts` remains the small language/prototype lab.
- The Gradle project root is named `rylween` and uses the Gradle 9.5.0 wrapper with Kotlin JVM 2.3.21. `src/main/kotlin/rylween-dsl.kt` is the Kotlin implementation of the scratch DSL, `src/main/kotlin/rylween-dsl-validation.kt` contains validation, and `src/main/kotlin/rylween-engine.kt` contains the backend-neutral engine port. `rylween-full-scratch.dsl.kts` is only the game-specific declaration source.
- Keep a hard boundary between DSL implementation and game authoring. Kotlin classes, builders, validators, and generated handles are implementation/compiler scaffolding; the game designer should add game-specific content through declaration blocks such as `flags { ... }`, `monsters { ... }`, `missions { ... }`, `gameProgress { ... }`, and `state { ... }`.
- Adding a new monster, flag, mission, special milestone, text, or similar game-specific concept should not require writing a Kotlin class. The authoring source declares the concept in game terms; the content compiler can generate typed handles from those declarations.
- Common Rylween engine/state concepts such as party state, member stats, inventory slots, positions, and validation helpers may remain normal Kotlin DSL implementation types because authors consume them rather than extend them.
- Common gameplay systems such as battle rounds, fight/run menus, spell selection/casting, item use, reward application, and encounter orchestration should become reusable Kotlin DSL/runtime-library concepts. Game authors should declare the content those systems consume, plus explicit hooks or overrides when needed; they should not hand-write bespoke battle loops for normal monsters or spells. Those reusable Kotlin systems may call a narrow native surface for input, rendering, RNG, persistence, and low-level storage.
- The runtime boundary is backend-neutral: `RylweenEnginePort` can be implemented by the current C engine bridge, a pure Kotlin JVM runtime, a server process, or a future native executable. The DSL/runtime layer should depend on the port contract, not on a hard-coded C implementation.
- `Flags` aliases the existing `Story` flag catalog so scripts can keep the readable `Story.garth.bandits.accepted` form while A2 has a canonical `Flags.*` entry point.
- `Items.none` is an author-facing alias for the current id-0 `blank` item. The prototype-only `garth_cargo` item was not carried forward because it is not present in the current item shards.
- `Images.Scene.*` names the `blitgfx` map-window pictures semantically from `src/ryl.h` and `world.json5`'s `enums.pic` table, with comments preserving the legacy `data/tiles/NN.ans` filenames. Avoid `tile01`-style handles in authoring code.
- `Images.Static.*` is reserved for full/static ANSI assets under `data/ansi`, separate from map-window scene pictures under `data/tiles`.
- `NativeOps.*` is a typed catalog, but in `.kts` it must currently be represented as a class plus top-level value rather than a Kotlin `object`; script objects that call script-level helpers trigger a Kotlin script capture error.
- DSL implementation code should be idiomatic Kotlin: prefer expression-bodied helpers, `buildList`, `apply(...).build()`, and collection operations over local scratch variables and imperative loops. Authored game blocks should stay readable as game notes.
- B7 ownership decision: `fireshield`, `bracelets`, `mastercrown`, `jazzyfoot`, and `silverkey` are persisted game-state milestones. Each current milestone also projects into the special-inventory view, but that is a property of the milestone, not a hard-coded rule that every win milestone is a special item.
- `silverkey == 2` in the legacy C engine is not a Silver Key item state. It becomes a separate `Milestones.ydargVictory` completion marker so "has Silver Key" and "finished Castle Ydarg / won" are distinct states.
- The future engine should validate winning configurations at startup and save-load time: a party is in a winning configuration only when all content-defined required milestones are achieved and the configured completion marker is present.
- First-pass B1-B9 runtime-state shape now distinguishes `stateFixture(...)` from strict `runtimeState(...)`. Fixtures can stay compact; runtime snapshots are validated as fuller save-shaped records with explicit realm runtime fields, member counts, full inventory slots, attack-today records, declared NPC visit bits, and literal journal lines where needed.
- Equipment is modeled separately from slotted inventory in member `equipped { weapon; armor }` blocks. This mirrors current save coverage, where equipment is per member, while preserving the rule that milestone-projected special items are not slotted inventory.
- Legacy special-field import mapping: `fireshield = 1`, `bracelets = 1`, `mastercrown = 1`, `jazzyfoot = 1`, and `silverkey = 1` map to their matching ownership milestones. Legacy `silverkey = 2` maps to `Milestones.ydargVictory`, not to an upgraded Silver Key item state.
- First-pass combat-system shape is `combat { standardBattle { ... } }`. It names the reusable battle framework from the game author's point of view: opening flee rules, encounter banners, player actions, monster-round policy, and the reward bundle to apply. The actual XP/gold/loot declaration now lives in `rewards { reward(Rewards.standardBattle) { ... } }`, so the reusable battle loop can point at a shared reward bundle instead of embedding one-off reward rules.
- Loot tables use semantic item pools such as `ItemPools.battleSpoils` and `ItemPools.magicItems`; legacy numeric item-id ranges stay in generated implementation/runtime metadata, not authoring. The implementation model also records the narrow native services the standard battle framework needs: keyboard input, rendering, RNG, combat context storage, inventory storage, party-state mutation, and text formatting.
- Current battle loot pool mapping is exact to the legacy C/JSON5 ranges: `ItemPools.battleSpoils` maps ids `36..46` to red/blue/amber potions plus red/blue/green gems, chalice, pendant, broche, crystal bowl, and necklace. `ItemPools.magicItems` maps ids `100..111` to frost wand, fire wand, gem of confusion, gem of blindness, drums of death, staff of healing, blessing scroll, staff of curing, gem of resurrection, Kael's axe, holy handgrenade, and flute of madness.
- First-pass dialogue shape is `dialogue { npc(Npcs.garth) { whenState(...) { ... } } }`. One-off NPC interactions such as Garth's merchant reward can keep their text, reward, prompt/drop handling, and flag transition inline in the branch; reusable screens/text remain a separate future text-asset concern and do not force a scene id for every dialogue branch.
- First-pass speak dispatcher shape is `speak { standardConversation { ... } }`. It declares the reusable NPC selection, visited-bit recognition, branch-selection policy, C/Q/R/B top-level menu, L/M/P/N question submenu, well/weather/health chitchat roll, once-per-conversation rumour behavior, and the narrow native services the reusable runner needs. Game authors still provide per-NPC branch content in `dialogue { ... }` rather than writing the conversation loop.
- Reusable speak text now has its own `speak { textTables { ... } }` shape for shared screens, chitchat prompt/reply tables, question reply tables, and town rumour rolls. This is intentionally separate from inline mission dialogue: table text is reusable system content, while one-off NPC quest branches can remain local structured blocks.
- Reusable text tables use structured `lines { line(...) }` blocks when the content benefits from one line per source row, and `lines("...", "...")` as a compact equivalent for short exact legacy rows. The authoring surface should converge on line-oriented text blocks rather than mixed vararg-only screen APIs.
- Character references in reusable text should point at semantic roles, not placeholder strings such as `{name15}` or `{loc15}`. The runtime resolves `character(Roles.crownSeer).name` and `.location` from the character currently bound to that role, so fixed and randomized NPC placement both produce correct dialogue/rumour text. Legacy rumour roll numbers live on generated `Rumours.*` metadata, not in authoring rows. Rumours can also declare story-state exclusions, such as hiding the bandit-merchant rumour once `Story.garth.bandits` is recovered or closed.
- Town declarations should introduce their own authored ids, e.g. `town("ollk") { name = "Ollk"; ... }`. Generated `Towns.*` handles are compiler output used by other content, state, scripts, character placement, and text references; authors should not need to predeclare or manually maintain the handle catalog before declaring a town.
- Legacy `main` C code confirms the original NPC layout: slots 1-24 are generated random people, slot 33 is Marcus the fixed Hermit at wilderness `48,7`, slots 34-35 are fixed tavern women, and slots 36-39 are Alex/Galdon/Kosch/Ufarsin as merchant wilderness records with implicit `0,0`. The DSL should not expose implicit wilderness-origin placement as an authoring concept; characters should be declared as `randomPlacement()`, `randomWilderness()`, or `fixed(Wilderness.xy(...))`.
- Generated `Items.*` handles now carry legacy item ids from `data/world/items/*.json5` in Kotlin implementation metadata. Item-pool validation checks both the expected range length and the exact ordered ids of the referenced handles, so author-facing pools stay semantic while migration mistakes in numeric carryover fail validation.
- Confirmed design decision: item ids should appear only in generated metadata, if they appear at all. The public author-facing `item("slug")` helper cannot attach a legacy id; generated catalog code uses the internal generated-handle path.
- Generated item metadata now includes item kind, display name, cost, storage behavior, and weapon/armor combat values from `data/world/items/*.json5`. Validation checks unique handles/legacy ids, required combat values, and that special items are milestone-backed rather than slotted inventory.
- Potion metadata now carries the current generated potion effects from `data/world/items/potions.json5`: red potion heals 15 HP, blue potion poisons 15 HP, and amber potion restores mana.
- Magic item use is now wrapped in explicit item-use metadata: usable contexts, consume policy, and an effect body. Fire wand uses the reusable `BeforeUse` consume policy instead of hand-authoring an inventory-slot clear in its effect body.
- Text/screens have two paths: reusable records can stay as `TextId` handles, while one-off dialogue can be written inline as structured text blocks inside the owning dialogue/mission branch. Do not mint a global text or scene id just to hold a one-off NPC response. The Borin gift scene now uses inline text for its local narration.
- Reward bundles and scripted rewards now name item-placement semantics separately from loot membership. Standard battle uses first-free inventory slot placement and exact legacy behavior `SkipIfInventoryFull`; scripted rewards can use the same typed policy shape for overflow prompts without custom inventory loops in authored content. The Borin gift reward now grants gold and semantic gem handles without numeric ids.
- Standard battle framework rules now carry the exact player/monster combat tunables from `data/world.json5`: attack hit bonus `1`, attack damage spread max `4`, confusion divisor `3`, battle-cry weapon-skill penalty `4`, defend weapon-skill penalty `2`, defend rest threshold `5`, monster-melee hit bonus `1`, damage divisor `6`, damage spread max `9`, and damage minimum `1`. These are reusable Kotlin DSL/runtime rules, not game-specific custom battle loops.

Open design questions:

- Decide whether final generated catalogs should mirror JSON5 shard names exactly or use more author-facing groups such as `Images.Static` / `Images.Scene`.
- Decide scene slug normalization before broad script porting. The scratch file starts grouping scene handles by domain, but the migration needs one canonical mapping from legacy script ids to typed `Scenes.*`.

### B. Exact runtime state and savegames

- [x] B1. Realm/session state. DSL delta: `state { realm { day/hour/minute, rng, currentMode, currentMap } }`. Native decision: only active frame pointers and transient loop state remain native. Current scratch status: first-pass `RealmState` covers day/hour/minute, RNG seed, active party, current map/mode/town, banishment, last-played day, and party-member max.
- [x] B2. Party identity and position. DSL delta: `party(Parties.main) { position = Wilderness.xy(...); facing = ... }`. Native decision: coordinate storage and bounds checks remain native. Current scratch status: party id/player/name, world position, and optional facing are modeled.
- [x] B3. Party resources and counters. DSL delta: `resources { gold, food, fatigue, timeOfDay, upkeepDue }`. Native decision: primitive numeric mutation only. Current scratch status: resources cover gold, torches, food, time left, and rested hours; counters cover member count, kills, mail/attack notices, and attack-today records.
- [x] B4. Party members. DSL delta: `member(1) { name; race; class; weaponSkill; charisma; wisdom; health; hitPoints; mana }`. Native decision: field storage and target selection only. Current scratch status: member records include identity, class/race/gender, level, stats, HP/mana, equipment, experience, and dead/alive state.
- [x] B5. Derived/depletable stat rules. DSL delta: validation for `hitPoints.max == health` and `mana.max == wisdom`, plus rules that modify max/current values. Native decision: no hidden C recalculation except typed helper ops. Current scratch status: validators enforce HP and mana max consistency.
- [x] B6. Inventory slots and equipment. DSL delta: `inventory { slot(1).item(...) }` plus per-member `equipped { weapon = ...; armor = ... }`. Native decision: low-level insert/remove/first-free-slot helpers. Current scratch status: slotted inventory is separate from per-member equipment because current save coverage stores weapon/armor per member; runtime snapshots must list slots `0..16`.
- [x] B7. Legacy special fields. DSL delta: `gameProgress { milestone(...) { showsAs(...); requiredToWin(); legacyPartyField(...) } winningConfiguration { ... } }` plus `party { milestones { achieved(...) } }`. Native decision: old positional fields are migration-only; native state should store dense milestone ids, derive special-inventory display from content metadata, and treat legacy `silverkey == 2` as a separate completion marker. Current scratch status: near-winning and winning fixtures exercise required milestones, `Milestones.ydargVictory`, special inventory projection, and generic win checking.
- [x] B8. Journal and visited records. DSL delta: `journal { entry(...) }` and `visited(Npcs...)`. Native decision: persistent string/id list storage. Current scratch status: authored states can use compact `visited(...)` and text-id journal entries; runtime snapshots can list true/false NPC visit bits and literal journal lines.
- [ ] B9. Savegame emission. DSL delta: exact fully expanded `state {}` output with no hidden defaults or helper loops. Native decision: filesystem write/read for new snapshots; old-save import is not required unless explicitly requested. Current scratch status: `runtimeState(...)` and validation discipline exist, but no deterministic writer/loader has been built yet.

B1-B9 first-pass notes:

- `stateFixture(...)` is the authoring/test fixture form. It may stay compact and ergonomic.
- `runtimeState(...)` is the future save snapshot subset. Validation currently requires explicit RNG seed, member count, full inventory slots `0..16`, attack-today records for every party in the state, and explicit visit bits for every NPC declared in that state.
- Old-save reading is no longer part of the near-term DSL design. Legacy fields are still documented so current data can be mapped correctly, but the next save/load work should target exact new DSL snapshots first.
- Current C save coverage audited for this pass: realm header (`last_played_day`, party-member max, current session fields from snapshots), locations, NPC placement, party identity, flags, special fields, `pl`, position, resources, inventory, member records/equipment, `attack_today`, `waiting_mail`, `attacked`, `kills`, `visited_npcs`, and `journal`.

### C. World maps, terrain, visuals, and layout

- [ ] C1. World metadata and limits. DSL delta: `meta { ... }` and `limits { ... }`. Native decision: allocation caps and defensive bounds checks.
- [ ] C2. Map dimensions and storage. DSL delta: `worldMap(Maps.wilderness) { size = cells(...); storageCapacity = ... }`. Native decision: map array allocation and coordinate bounds.
- [ ] C3. Map cells and layers. DSL delta: typed terrain rows/layers instead of raw `map.dat` bytes. Native decision: optional legacy map-byte loader during migration.
- [ ] C4. Terrain semantics. DSL delta: `terrain(Terrain.forest) { movement; passability; tags; events }`. Native decision: fast predicate tables generated from terrain ids.
- [ ] C5. Terrain visuals. DSL delta: terminal glyph/color and future bitmap tile styles live under terrain render styles. Native decision: renderer consumes a style id, not terrain game rules.
- [ ] C6. Visual assets. DSL delta: `visuals { images { image(..., kind = Ansi|Bitmap) } palettes { ... } }`. Native decision: asset decoding and backend texture/glyph storage.
- [ ] C7. Screen regions and layouts. DSL delta: `layouts { region(ScreenRegion.map/text/status, ...) }`. Native decision: cursor/viewport implementation only.
- [ ] C8. Static screens and documents. DSL delta: title, help, manual, score display, and ANSI/static screens declared as assets/documents. Native decision: file/asset rendering backend.

### D. Text, dialogue, NPCs, and story state

- [ ] D1. Text records and local bindings. DSL delta: `texts { text(Texts.foo) { bind(LocalText.partyLeader, ...) } }`. Native decision: wrapping and final placeholder substitution.
- [ ] D2. Pronouns and name bindings. DSL delta: typed binding names and pronoun cases instead of symbolic strings. Native decision: lookup by member/npc id.
- [ ] D3. NPC definitions and roles. DSL delta: `characters { character(Npcs.garth) { name; job; fixed(...); role(...) } }`. Native decision: role lookup and placement storage. Current scratch status: all 39 legacy NPC names/jobs are declared in the scratch file, including the 17 current semantic story roles used by text/rumours.
- [ ] D4. NPC placement and visited state. DSL delta: content placement plus mutable `state { npcs { at(...) } visited(...) }`. Native decision: runtime coordinates and visited bitsets. Current scratch status: character content distinguishes `randomPlacement()`, `randomWilderness()`, and `fixed(...)`; runtime fixtures still carry explicit realized NPC locations and visited bits. Legacy C's trailing `0,0` wilderness merchant slots are documented as an import/migration concern, not an author-facing placement mode.
- [ ] D5. Flags and story progress. DSL delta: typed flag families with explicit states and setters. Native decision: dense flag storage and save serialization.
- [ ] D6. Missions and triggers. DSL delta: `mission(...) { trigger { ... } action { ... } }`. Native decision: trigger polling and typed op dispatch.
- [ ] D7. Dialogue and chitchat. DSL delta: `speak { standardConversation { ... } textTables { ... } }` plus `dialogue { npc(Npcs.garth) { whenState(...) { say(...) } } }`. Native decision: NPC lookup/visited bits, input, rendering, RNG, and text formatting stay narrow services behind a reusable Kotlin/runtime conversation runner. Current scratch status: Garth's merchant offer and grateful reward are first-class inline dialogue branches with exact legacy text, reward, prompt/drop behavior, and flag transitions. The standard conversation shape now pins C/Q/R/B and L/M/P/N menu structure, first-matching dialogue branch dispatch, visited recognition, well/weather/health chitchat, once-per-conversation rumour fallback, and representative reusable speak screens/chitchat/question reply tables copied from `data/world/texts/speak.json5`.
- [ ] D8. Rumours. DSL delta: rumour tables with anonymous inline entries by default, semantic `Rumours.*` only for externally referenced/gated entries, role-based character text references, town-location text references, and optional `excludeWhen(...)` story-state filters. Native decision: RNG selection only plus filtering against typed party state; legacy roll numbers belong to generated/import metadata, not authoring rows. Current scratch status: `RumourTables.standardTown` exists under `speak { textTables { ... } }` with exact entries from `data/world/texts/towne.json5`; authoring uses `character(Roles.crownSeer).name/location`, `town(Towns.murn).near(radius = 5)`, and anonymous `rumour { ... }` rows rather than roll numbers or `{name15}` placeholders, and the bandit-merchant rumour is excluded after the cargo is recovered or the reward is closed.

### E. Towns, buildings, wilderness, and daily actions

- [ ] E1. Towns and buildings. DSL delta: `towns { town("ollk") { name; position; taxPercent; services { ... } } }` and future `building(...) { enter { ... } }`. Native decision: current-location state and generic enter dispatcher. Current scratch status: the four current towns are declared with exact names, coordinates, tax rates, settlement class, church availability, and service hours from `data/world.json5`; building enter scripts are still pending.
- [ ] E2. Keymaps and menus. DSL delta: typed `wildernessKeys`, `townKeys`, and `combatKeys` declarations. Native decision: keyboard input and dispatch execution.
- [ ] E3. Shops and services. DSL delta: smith, merchant, inn, tavern, church, hut, and lodge as building scripts/tables. Native decision: menu input/render primitives, temporary pricing helpers if needed.
- [ ] E4. Rest, camp, hunt, and lodge. DSL delta: action scripts with costs, risks, time passage, food/fatigue effects. Native decision: RNG and state mutation primitives.
- [ ] E5. Upkeep and time. DSL delta: `upkeep { daily { ... } hourly { ... } }` with balance tables. Native decision: clock/tick scheduling and deterministic RNG.
- [ ] E6. Wilderness events and transitions. DSL delta: terrain transition scripts and random wilderness event tables. Native decision: random draw and event dispatch runner.
- [ ] E7. Character creation. DSL delta: start-state builder plus class/race/stat roll rules. Native decision: input, RNG, and state allocation.

### F. Items, spells, combat, monsters, and rewards

- [ ] F1. Item records. DSL delta: typed item definitions for mundane, equipment, potion, magic, and quest items. Native decision: id lookup and inventory storage. Current scratch status: generated `Items.*` handles and `ItemCatalog` records carry legacy ids, display names, costs, item kinds, storage behavior, and weapon/armor combat values from the JSON5 item shards; item use effects are still pending.
- [ ] F2. Item use effects. DSL delta: item scripts for potions and magic items. Native decision: consume/remove primitive and typed stat mutation helpers. Current scratch status: generated potion effects are modeled from JSON5, and magic item effects now carry explicit context/consume metadata; fire wand is the first migrated example.
- [ ] F3. Equipment rules. DSL delta: ready/remove actions and derived combat values in DSL. Native decision: equipment storage and target/member access.
- [ ] F4. Spell records. DSL delta: mage, priest, and monster spell definitions with typed costs, targets, effects, and text. Native decision: spell picker and cost-payment primitive. Current scratch status: one mage spell body exists; `standardBattle` now records that spell selection is part of the reusable battle framework, not per-encounter authoring.
- [ ] F5. Monster records and tiers. DSL delta: typed monsters, encounter tiers, behavior hooks, and rewards. Native decision: encounter instance allocation. Current scratch status: skeletal monster records exist and `standardBattle` now points at the shared encounter-sizing/runtime service boundary.
- [ ] F6. Combat loop. DSL delta: typed combat actions, turn phases, flee/death/reward rules. Native decision: combat context storage, target UI, and typed op execution. Current scratch status: first-pass `combat { standardBattle { opening; playerActions; playerRound; monsterRound; rewards = Rewards.standardBattle } }` declaration exists with validation and exact JSON5/C tunables for standard attack, defend, battle cry, and monster melee, but the C runner has not been replaced.
- [ ] F7. Monster AI and attacks. DSL delta: monster behavior scripts for melee/spells/specials. Native decision: RNG and target choice helpers. Current scratch status: `standardBattle.monsterRound` models spell chance and melee fallback; full monster ability records are still pending.
- [ ] F8. Rewards and loot. DSL delta: reward DSL for gold, experience, items, flags, and journal updates. Native decision: first-free-slot helper and overflow prompt. Current scratch status: top-level `rewards { reward(Rewards.standardBattle) { experience; gold; itemPlacement; lootTable(...) } }` declares the standard battle reward bundle using semantic item pools and exact full-inventory behavior. Script-level `reward { gold(...); items(...) }` now covers Borin's skip-if-full gift and Garth's prompt/drop merchant reward with semantic item handles and inline narration.
- [ ] F9. Leveling and experience. DSL delta: class progression tables and level-up scripts. Native decision: applying member stat changes.

### G. Dungeons, Ydarg, secrets, and bespoke systems

- [ ] G1. Dungeon scenarios. DSL delta: `dungeon(...) { levels; rooms; events; encounters; exits }`. Native decision: dungeon instance storage and optional map generation primitive.
- [ ] G2. Cave/location events. DSL delta: special caves, pits, sanctums, and location scripts move from C/JSON5 into typed scenes. Native decision: enter/exit dispatch only.
- [ ] G3. Ydarg castle. DSL delta: model rooms, mutable room state, events, and rewards in DSL. Native decision: temporary native island until all state is represented.
- [ ] G4. Secrets and sysop/debug actions. DSL delta: explicit optional debug/secret block with permission gates. Native decision: input matching and test-only gates.
- [ ] G5. Score/vscore. DSL delta: decide whether scores are content/state or a native-only external feature. Native decision: file I/O and display backend.
- [ ] G6. Replay/test knobs. DSL delta: test profiles and fixture states replace game-rule env knobs where possible. Native decision: test harness overrides only.

### H. Engine replacement path

- [ ] H1. Compiled content bundle. DSL delta: define generated bundle schema from Kotlin content. Native decision: C loads typed tables, not JSON nodes.
- [ ] H2. Compiled state image. DSL delta: define generated state schema and exact save snapshot schema. Native decision: C stores mutable ids/arrays and can write exact DSL state.
- [ ] H3. String interning and hash tables. DSL delta: every handle has slug plus dense id. Native decision: stable lookup tables for diagnostics, migration, and saves.
- [ ] H4. Typed script/op VM. DSL delta: scripts compile to typed ops with checked operands. Native decision: VM executes ids and typed values only.
- [ ] H5. Renderer boundary. DSL delta: scripts use `showBackdrop`, `renderMap`, `say`, `status`, not terminal cursor calls. Native decision: backend-specific terminal/image drawing.
- [ ] H6. Remove JSON5 runtime dependency. DSL delta: all inventory rows are modeled and emitted from Kotlin. Native decision: JSON5 reader deleted or kept only as offline migration tool.

## Complete migration inventory

This inventory is the tracking list for "everything in the game." Every current
JSON5 subtree and every game-owning C module should land in one of these rows.
Rows marked "DSL" should become Kotlin content, state, or script. Rows marked
"native" should remain generic engine service. Rows marked "temporary native"
are allowed during migration but should have an explicit shrinking path.

| Area | Current owner | Target DSL owner | Native layer that remains |
|------|---------------|------------------|---------------------------|
| Program boot | `ryl.c`, `runtime_cfg.c` | launch profile selecting content + state | argument parsing, process setup, runtime config, exit codes |
| Resource loading | `res.c`, JSON5 imports | Kotlin content compiler + native bundle | bundle reader, validation runner, string interner/hash tables |
| World metadata / limits | `world.json5 meta`, `limits` | `meta {}` / `limits {}` content | memory allocation bounds, defensive loader checks |
| Save/load | `driv.c current-world.json5`, legacy `.dat` | exact `state {}` snapshots | filesystem I/O for new snapshots; legacy importer only if requested later |
| Realm/session state | `struct ryl_state` | `state { realm { ... } }` | active-state pointer, transient runtime frame |
| Parties | `party.c`, `driv.c` save/load | `party(...) { ... }` state | storage, bounds checks, state mutation primitives |
| Party members | `struct party_member` | `member(...) { health/hitPoints/wisdom/mana/... }` | primitive field access in typed VM |
| Inventory slots | `inventory.c`, party array | `inventory { slot(n).item(...) }` state + item scripts | slot storage, low-level insert/remove helpers |
| Special party fields | `fireshield`, `bracelets`, `mastercrown`, `jazzyfoot`, `silverkey` | `gameProgress` milestones with optional special-inventory projection and win-condition metadata | dense milestone storage; legacy field importer/exporter only during migration |
| Journal | `interact.c`, party journal | typed journal entries in state and scripts | string persistence/render append primitive |
| NPC definitions | `world.json5 npcs`, `res.c` | `npcs { ... }` content | id lookup, runtime placement storage |
| NPC visited bits | `struct person.visited` | `visited(Npcs...)` state | bitset/set storage keyed by npc id |
| NPC roles | `world.json5 npc_roles` | `roles { ... }` content | role-to-npc lookup by id |
| Towns/buildings | `world.json5 towns/buildings`, `towne.c` | `towns {}` / `buildings {}` content + scripts | current-location state and generic enter dispatcher |
| Town keymaps | `town_keys` | typed menu/key action declarations | keyboard input and dispatch execution |
| Wilderness keymaps | `wilderness_keys`, `driv.c` | typed key action declarations | keyboard input and dispatch execution |
| Combat keymaps | `combat_keys`, `battle.c` | typed combat action declarations | keyboard input and dispatch execution |
| Map dimensions | `world.json5 map`, `wld.c` | `worldMap { size/storageCapacity }` | allocation, coordinate bounds |
| Map cells | `data/map.dat`, `state.map` | typed map layers/cells | loaded cell arrays, optional legacy map.dat reader |
| Terrain semantics | `world.json5 terrain.class`, `wld_tile_is_*` | `terrain { movement/tags/events }` | fast terrain-id predicates |
| Terrain visuals | `terrain.glyph/color`, ANSI renderer | terrain render styles | renderer backend consumes glyphs/images |
| Screen/layout regions | `world.json5 screen`, `primitives.c` | `layouts { region(...) }` | backend cursor/viewport implementation |
| Backdrops/pics/assets | `enums.pic`, `data/tiles/*.ans`, `blitgfx` | `visuals { images { ... } }` | asset decoding and render backend |
| Text records | `world/texts/*.json5`, `res_text` | `texts { ... }` with typed placeholders | final text rendering and wrapping |
| Text bindings | `ryl_bind`, `engine_bind` string names | typed binding handles | conversion to renderable strings |
| Pronouns | `world.json5 pronoun`, `res_pronoun` | typed pronoun table | lookup by gender/case |
| Prompts/pause verbs | `prompts.pause_verbs` | content table | random selection primitive |
| Status line/time labels | `status.time_buckets`, `status_line` | status layout/content DSL | render current status from state |
| Balance/upkeep | `upkeep`, `balance`, `driv.daily/start` | content tables + upkeep scripts | clock/tick scheduling, deterministic RNG |
| Time/food/fatigue | `subs.c time_handler/food_handler`, fatigue levels | typed upkeep rules/scripts | state mutation primitives and RNG |
| Wilderness events | `random_wilderness`, `wilderness` config | typed event table/scripts | RNG draw and event dispatch runner |
| Terrain transitions | `interact.c`, `scripts/terrain` | terrain transition scripts | last-terrain state field |
| Rest/camp/hunt/lodge | `subs.c`, `scripts/rest/hunt/lodge` | typed action scripts and balance tables | input, RNG, state mutation primitives |
| Character creation | `driv.c create_party`, class/race tables | creation script + class/race content | input, RNG/stat roll primitive, state allocation |
| Class/race/enums | `enums`, `res_enum*` | typed enums/catalogs | id lookup and migration aliases |
| Leveling/xp | `xp_thresholds`, `levelup`, `battle.c exp_check` | class progression rules | applying member stat changes |
| Items | `world/items/*.json5` | typed item records | inventory storage and item id lookup |
| Equipment | `weapon_ready/off`, `armor_ready/off` | equipment actions/scripts | applying equip state and derived combat values |
| Potions | `inventory.c apply_potion` | potion effect scripts | low-level stat mutation helpers |
| Magic items | `world/items/magic_items.json5` | item use scripts | inventory consume/remove primitive |
| Rewards/loot | `reward` op, battle/dungeon scripts | typed reward DSL | first-empty-slot helper and overflow UI primitive |
| Monsters | `world/monsters/monsters.json5` | typed monster records/tier tables | encounter instance allocation |
| Encounters/combat loop | `battle.c` | combat rules/actions/scripts + monster AI scripts | turn runner, target selection UI, combat context storage |
| Monster spells/melee | `world/spells/monster.json5`, battle fallback | monster ability scripts | combat context access primitives |
| Mage/priest spells | `world/spells/*.json5`, `subs.c spell` | typed spell records/effect scripts | spell picker input and cost/payment primitive |
| Dungeons/caves | `dungeon.c`, `world/dungeons`, `scripts/dungeon` | dungeon scenarios, levels, events, placements | dungeon instance storage and map generation primitive |
| Ydarg castle | `ydarg.c`, `scripts/ydarg` | Ydarg map/state/content/scripts | temporary native island until room state is modeled |
| Missions/triggers | `world/missions.json5`, `missions.c` | typed trigger/action/binding DSL | trigger polling and dispatch execution |
| Dialogue/speak | `speak.c`, `scripts/speak*`, text shards | dialogue scripts, role bindings, chitchat tables | NPC selection/input and dispatcher |
| Rumours | `towne.rumour`, role bindings | typed rumour table/script | RNG roll primitive only |
| Shops/church/inn/tavern/merchant/smith/hut | `towne.c`, scripts/towne | building scripts and shop tables | input/render primitives, maybe pricing helpers during migration |
| Secrets/sysop actions | `world.json5 secrets` | optional debug/secret content block | permission/input matching, test-only gates |
| Score/vscore | `score_list`, data files | score state/content if retained | file I/O and display backend |
| Help/manual/title/static screens | `data/rylween.doc`, `entry.dat`, ANSI title | typed static documents/assets | file/asset render backend |
| Replay/test knobs | `replay_knobs.c`, env vars | test profiles/state fixtures where possible | test harness/env overrides |
| Logging/snapshots | `log.c`, `snapshot.c` | not game content | native diagnostics and test artifacts |
| JSON5 parser/imports | `json5.c` | transitional only | remove from runtime once Kotlin bundle lands |

No subsystem should be considered fully migrated until this inventory row has:

- a Kotlin DSL representation
- a native id/storage representation
- save-state coverage if it mutates over time
- validation rules
- parity tests or replay fixtures

Audit anchors for this inventory:

- `data/world.json5`: `meta`, `limits`, `prompts`, `flags`, `items`,
  `monsters`, `dungeons`, `texts`, `scripts`, `upkeep`, `map`, `wilderness`,
  `balance`, `driv`, `combat`, `xp_thresholds`, `formulas`, `spells`,
  `monster_spells`, `terrain`, `pronoun`, `towns`, `buildings`, `town_keys`,
  `combat_keys`, `wilderness_keys`, `missions`, `screen`, `status`, `ydarg`,
  `secrets`, `npcs`, `npc_roles`, and `enums`.
- `data` side files: `map.dat`, legacy `.dat` state files, `current-world.json5`,
  ANSI/static screens, tile/backdrop assets, score files, and manual/help text.
- C game modules: `driv`, `party`, `inventory`, `wld`, `subs`, `towne`,
  `speak`, `missions`, `battle`, `dungeon`, `ydarg`, `interact`, `script`,
  and `res`.
- C engine/service modules: `ryl`, `compat`, `io_tty`, `primitives`, `json5`,
  `runtime_cfg`, `log`, and `snapshot`.

## World maps, terrain, and visuals

The map/rendering layer should be part of `content {}`. Today the game has
these pieces split across `data/map.dat`, `world.json5 terrain`, sparse `pic`
ids, ANSI tile files, and screen-region records. The DSL should make those
first-class without baking in terminal ASCII as the only presentation.

The content model should distinguish:

- **map data**: dimensions, layers, cells, generated placement bounds
- **terrain semantics**: passability, enterability, encounter modifiers,
  transition scripts, movement cost, tags/classes
- **rendering presentation**: terminal glyphs, colors, image/tile sprites,
  animation frames, fallback styles
- **screen/layout regions**: named panes such as text, map, status, backdrop
- **backdrop/scene assets**: cave, town, inn, church, encounter banners, etc.

Do not model a terrain tile as "the glyph." Model it as terrain with semantics
and one or more render styles:

```kotlin
terrain {
    plain = terrain("plain") {
        mapCode = 's'
        movement = Movement.Open
        terminal {
            glyph = ".."
            color = Palette.Yellow
        }
        tile {
            image = Images.Terrain.plain
        }
    }

    mountain = terrain("mountain") {
        mapCode = 'm'
        movement = Movement.Blocked
        tags(TerrainTag.Mountain)
        terminal {
            glyph = "/\\"
            color = Palette.White
        }
        tile {
            image = Images.Terrain.mountain
        }
    }

    town = terrain("town") {
        mapCode = '0'
        movement = Movement.Enterable(EnterKind.Town)
        terminal {
            glyph = "TT"
            color = Palette.White
        }
        tile {
            image = Images.Terrain.town
        }
    }
}
```

For the current terminal renderer, `terminal { glyph, color }` is enough. For a
future renderer, `tile { image = ... }` or `sprite { frames = ... }` becomes the
primary presentation. The engine chooses the active render style; game logic
only sees terrain ids, tags, and movement semantics.

Map authoring should likewise avoid hardwiring `data/map.dat` bytes as the
ideal form:

```kotlin
worldMap(Maps.wilderness) {
    size(width = 82, height = 40)
    storageCapacity(width = 82, height = 42)

    generatedLocations {
        bounds(x = 5 until 74, y = 5 until 34)
        avoid(Terrain.water, Terrain.mountain, Terrain.town, Terrain.hut, Terrain.castle)
    }

    layer(MapLayer.terrain) {
        rows(
            "mmmm....",
            "mss0....",
        )
    }
}
```

For migrated legacy maps, accepting compact rows is fine, but the row characters
must resolve through typed terrain declarations. Future authored maps could use
tile painters, run-length helpers, imported image masks, or generated regions,
as long as the compiled model is explicit cells/layers.

Visual assets should be typed handles too:

```kotlin
visuals {
    palette("terminal16") {
        color(Palette.White, ansi = 7)
        color(Palette.Yellow, ansi = 3)
    }

    images {
        image(Images.Backdrop.church, "tiles/church.ans") {
            kind = ImageKind.Ansi
            size = cells(16, 9)
        }

        image(Images.Terrain.plain, "terrain/plain.png") {
            kind = ImageKind.Bitmap
            size = pixels(32, 32)
        }
    }
}
```

Screen regions should be layout data, not scattered native cursor constants:

```kotlin
layouts {
    layout(Layouts.classicTerminal) {
        region(ScreenRegion.text, x = 49, y = 6, width = 26, height = 11)
        region(ScreenRegion.map, x = 8, y = 6, width = 16, height = 9)
        region(ScreenRegion.statusTop, x = 3, y = 21)
        region(ScreenRegion.statusBottom, x = 13, y = 22)
    }
}
```

Scripts should talk in game/render verbs, not terminal primitives:

```kotlin
showBackdrop(Images.Backdrop.church)
redraw(Maps.wilderness)
clear(ScreenRegion.text)
```

The terminal backend can implement those as `clr_map_window`, `blitgfx`, and
ANSI glyph draws. A future image backend can paste bitmap tiles or sprites into
the same regions.

## Flags

Flags are typed state machines grouped by story domain:

```kotlin
requires = Story.garth.bandits.accepted

mark(Story.garth.bandits.recovered)
```

Prefer the explicit setter form `mark(...)` over function-call state setters.
The call site reads as a state transition and avoids making a predicate object
look like a command.

Boolean-ish flags should not force awkward `yes` / `no` authoring:

```kotlin
when_(Story.church.donation.done) { ... }

mark(Story.church.donation.done)
```

Native state names can still be `yes` / `no` or `0` / `1` internally if the C
model wants that, but the DSL should expose the meaning.

Composite predicates are allowed and useful:

```kotlin
Story.silverKey.trailStarted
Story.masterCrown.activeTask
```

Those are typed boolean expressions, not persisted states. They remove repeated
condition spelling without adding new native flag values.

## Legacy slots

`legacySlot` is not an ideal Kotlin authoring concept.

Today, party flags are stored in C as a byte array:

```c
state.p[state.cpl].flag[slot] = value_index;
```

`data/world.json5` maps a symbolic flag tag to a fixed slot and a `values[]`
array. Save writing already reverses that back into `{ tag: "state" }`, and save
loading already prefers the tag/state shape while accepting old positional
arrays.

In the target design:

- Kotlin flags declare slugs and typed states, not slots.
- the engine assigns dense internal `flag_id` values while loading content.
- old slots exist only in a transitional legacy-save reader.
- new saves are written as state DSL snapshots using typed flag handles or
their canonical slug/state spelling.

## State DSL and saves

Game state belongs in the DSL too. The state model should cover at least:

- realm fields: day, hour, active party, current town, banishment/global state
- world locations and generated coordinates
- mutable map overlays if gameplay changes terrain or reveals tiles
- active layout / renderer preference if the frontend supports more than one
  presentation
- NPC runtime placement and visited status
- parties
- party position
- party resources: gold, torches, food, time left, rested hours
- party story flags
- game-state milestones currently represented as fields such as `fireshield`,
  `bracelets`, `mastercrown`, `jazzyfoot`, and `silverkey`; these may project
  into special inventory display without occupying inventory slots
- inventory slots
- party members and all member stats
- per-party attack/mail/kills state
- journal entries

Example shape:

```kotlin
state("recovered-bandit-cargo") {
    realm {
        day = 14
        hour = 9
        activeParty = Parties.replay
    }

    party(Parties.replay) {
        player = "Replay"
        name = "Replay"
        position = Wilderness.xy(12, 9)

        resources {
            gold = 1000
            torches = 5
            food = 100
            timeLeft = 200
            restedHours = 14
        }

        flags {
            mark(Story.garth.bandits.recovered)
            mark(Story.silverKey.noteFound)
        }

        inventory {
            slot(1).empty()
            slot(2).item(Items.fireWand)
        }

        members {
            member("A") {
                gender = Gender.Male
                race = Race.Human
                klass = Class.Thief
                level = 1
                health = 68
                hitPoints = 68 of 68
                weaponSkill = 27
                wisdom = 27
                mana = 0 manaOf 27
                charisma = 49
                armor = Items.none
                weapon = Items.none
                experience = 0
                alive()
            }
        }

        visited(Npcs.garth, Npcs.bernak)
        journal()
    }
}
```

Party member abilities should preserve the distinction between base abilities
and depletable pools:

- `health` is the base ability; `hitPoints` is the current/max pool derived
  from it.
- `wisdom` is the base ability; `mana` is the current/max pool derived from it.
- `weaponSkill` and `charisma` are plain abilities unless future rules uncover
  a depleted/current counterpart.

The state validator should reject a member whose `hitPoints.max != health` or
whose `mana.max != wisdom`, unless we later discover a legitimate temporary max
modifier that needs to be modeled explicitly.

Runtime save output should be deterministic and fully expanded. Authored test
states can use helpers, but persisted saves should be boring text.

The C runtime does not need to interpret arbitrary Kotlin. There are two
reasonable load paths:

1. The Kotlin DSL compiler records `content {}` and authored `state {}` blocks
   into a native content/state bundle loaded by C.
2. Runtime saves are emitted in a strict state-DSL snapshot subset. C can parse
   that subset directly, or a tiny launcher/tool can compile it to the same
   native state bundle before the engine boots.

The important point is that JSON5 is not the canonical save shape. The state DSL
model is.

## Typed text bindings

Text bindings should be typed instead of string-keyed:

```kotlin
screen(T.Towne.Church.cost, B.cost(price))

emit(
    T.Battle.FireWand.points,
    B.caster(partyName(caster)),
    B.monsterName(monsterName(foe)),
    B.dmg(damage),
)
```

The text definition should declare or infer its required placeholders. The DSL
validator should reject:

- missing placeholder bindings
- unknown placeholder bindings
- duplicate bindings with incompatible types
- passing a text id to a binding shape it does not accept

Bindings should carry typed values, not just strings:

- integer
- string
- item name
- monster name
- party member name
- pronoun
- location name
- NPC name/location

C can still format text with strings at the final rendering step, but the DSL and
compiled script model should preserve typed binding ids until that point.

## Native layer

Native calls should be allowlisted and typed. A script should not be able to
invent a raw engine function name.

The native layer is for generic engine service:

- loading a compiled content/state bundle
- string interning and slug-to-id hash tables
- dense storage for content-defined game-state milestones and their save/load
  serialization
- deterministic RNG, wall-clock access, and delay/timer services
- keyboard/input and line-input services
- filesystem I/O for saves, logs, snapshots, and static assets
- renderer backend primitives hidden behind typed render verbs
- memory allocation and bounds checks
- the typed script/op VM
- low-level state storage and mutation primitives
- diagnostics, crash/fatal reporting, replay/snapshot plumbing

The native layer is not the long-term home for game rules. These should move to
Kotlin DSL content/state/scripts:

- story progression and flags
- milestone meaning, special-inventory projection, and win-condition membership
- map triggers and mission conditions
- terrain semantics and movement rules
- town/building menus
- shop inventories/prices/open hours
- dialogue/chitchat/rumours
- spells and item effects
- combat actions, monster behavior, rewards, and leveling rules
- dungeon event content
- Ydarg room/event rules once its state is modeled
- text placeholder names and binding decisions
- static assets/layout declarations

Every native entry should have:

- a stable native id
- a typed signature
- a short reason
- an owning subsystem
- a status: permanent engine service, temporary legacy bridge, or migration shim

Game rules should move out of native calls and into typed DSL verbs as they are
understood.

Reusable gameplay frameworks belong in the Kotlin DSL/runtime layer, not in
game-specific authoring blocks and not as large native black boxes. For example,
the standard battle loop can be a shared Kotlin-defined flow that runs fight/run
selection, rounds, casting, item use, monster turns, death/reward handling, and
text hooks. The game source then declares monsters, spells, items, rewards, and
optional hooks; native calls remain small services such as input, render, RNG,
and state mutation primitives. The same applies to speaking with NPCs: the
standard conversation loop is reusable runtime code over authored dialogue,
topic tables, and text hooks; native supplies only NPC lookup/visited bits,
input, rendering, RNG, and formatting services.

Temporary native bridges currently expected during migration:

- old JSON5/current-world loader
- legacy `data/map.dat` map-byte loader
- terminal ANSI renderer
- Ydarg mutable room state
- generic `engine_call`/`engine_int`/`engine_bind` registry while typed ops are
  replacing it
- legacy save readers for positional arrays and old `.dat` files
- replay/environment knobs used only by tests

Permanent native services should be small and domain-neutral. For example,
`render_map(Maps.wilderness, ScreenRegion.map)` is acceptable; `do_silver_key`
or `shop_smith_beer_offer` is not.

For B7, native should not model a hard-coded "special item" family. It should
store achieved milestone ids, expose generic milestone set/clear/test ops to the
typed VM, derive any special-inventory rows from milestone metadata, and run the
content-defined winning-configuration predicate when loading a state or booting
from an authored fixture. Legacy save import can translate `fireshield = 1`,
`bracelets = 1`, `mastercrown = 1`, `jazzyfoot = 1`, and `silverkey = 1` into
their matching milestones; legacy `silverkey = 2` maps to the separate Ydarg
victory/completion milestone.

## C engine rewrite plan

The engine should move from "parse JSON5 nodes and interpret stringly ops" to
"load a compiled typed model and execute ids."

### 1. Add a content/state model

Introduce two top-level runtime objects:

```c
struct ryl_content;
struct ryl_state_image;
```

`ryl_content` owns immutable world definitions: flags, states, maps, map
layers, terrain, terrain classes/tags, visual assets, palettes, layouts, texts,
items, monsters, roles, scenes, scripts, spells, missions, locations, and native
op metadata.

`ryl_state_image` owns mutable world state: realm fields, active party, NPC
placement, party records, flags, inventory, members, visited NPCs, journal, and
per-party runtime counters.

### 2. Add string interning and hash lookup

Add a small C string interner plus open-addressed or chained hash tables for
load-time lookup:

- flag slug -> `flag_id`
- flag state slug -> `state_id`
- item slug -> `item_id`
- monster slug -> `monster_id`
- map slug -> `map_id`
- map layer slug -> `map_layer_id`
- terrain slug / legacy map code -> `terrain_id`
- terrain tag slug -> `terrain_tag_id`
- image/asset slug -> `asset_id`
- layout slug -> `layout_id`
- screen region slug -> `region_id`
- text slug -> `text_id`
- NPC slug -> `npc_id`
- role slug -> `role_id`
- scene slug -> `scene_id`
- placeholder slug -> `placeholder_id`
- native op slug -> `native_id`

Runtime gameplay should use ids. Slugs stay available for diagnostics,
debugging, save text, and migration tooling.

### 2b. Replace map bytes with terrain ids

Keep a migration loader for `data/map.dat`, but compile map bytes into terrain
ids at load time. The runtime world map should be something like:

```c
struct ryl_map_layer {
    map_layer_id id;
    terrain_id *cells;
    int width;
    int height;
};
```

Legacy predicates such as `wld_tile_is_water(char c)` should become typed
terrain predicates:

```c
int terrain_has_tag(terrain_id id, terrain_tag_id tag);
int terrain_is_passable(terrain_id id);
int terrain_enter_kind(terrain_id id);
```

During migration, `wld_tile_at(x, y)` can still expose the original map code for
old C call sites, but new code should ask for `terrain_id`.

### 3. Replace slot-based flags

Replace public script/runtime flag access with id-based access:

```c
int pa_flag_get(flag_id id);
int pa_flag_is(flag_id id, flag_state_id state);
void pa_flag_set(flag_id id, flag_state_id state);
```

Internally, `party.flags` can still be a byte array indexed by dense `flag_id`,
but it should no longer be indexed by old hand-authored slots. A transitional
legacy loader can map old slots to ids, then disappear when old saves are no
longer relevant.

### 4. Replace the JSON node script interpreter

Replace `script.c`'s generic node walker with a typed op evaluator:

```c
enum ryl_op_kind {
    OP_SCREEN,
    OP_EMIT,
    OP_IF,
    OP_SWITCH,
    OP_SET_FLAG,
    OP_SET_MEMBER_FIELD,
    OP_ENCOUNTER,
    OP_NATIVE,
    ...
};
```

Each op variant should carry ids and typed operands, not arbitrary strings.
Expressions should likewise become typed expression nodes or compact bytecode:

- literals
- variables
- arithmetic
- comparisons
- member fields
- monster fields
- party fields
- flag predicates
- native int calls from the allowlist

This removes most runtime validation from the hot path. The Kotlin compiler /
content loader validates references before gameplay starts.

### 5. Replace text binding strings with ids

Replace `ryl_bind { const char *name, const char *value }` as the primary script
formatting model.

Use:

```c
struct ryl_binding {
    placeholder_id id;
    enum ryl_value_kind kind;
    union ryl_value value;
};
```

Text records should store parsed placeholder ids. Formatting becomes a lookup by
placeholder id, with final conversion to display strings at render time.

Keep string-based formatting as a compatibility shim while migrating existing
text records.

### 6. Introduce a typed renderer boundary

Replace direct script usage of terminal-specific calls such as `clr_map_window`,
`blitgfx`, and raw ANSI map drawing with typed render commands:

```c
void render_clear(region_id region);
void render_backdrop(asset_id image, region_id region);
void render_map(map_id map, region_id region, render_style_id style);
void render_text(text_id text, const struct ryl_binding *bindings);
```

The first backend can still be the current terminal/ANSI backend. It maps
terrain render styles to glyph/color pairs and maps backdrop assets to `.ans`
files. A future bitmap backend can map the same terrain ids to image tiles and
the same backdrop ids to PNG/WebP/etc. Scripts and game rules should not change
when the backend changes.

The renderer should consume:

- `terrain_id` and render-style records for map cells
- `asset_id` for backdrops and scene images
- `region_id` for target panes
- parsed text ids and typed bindings for text

This makes "paste ASCII glyphs" and "paste image tiles" two implementations of
one model, not two different game definitions.

### 7. Move special party fields into typed state

Fields such as `fireshield`, `bracelets`, `mastercrown`, `jazzyfoot`, and
`silverkey` are story progress leaking into the native party struct. Model them
as content-defined game-state milestones:

```kotlin
gameProgress {
    milestone(Milestones.silverKey) {
        showsAs(Items.silverkey)
        requiredToWin()
        legacyPartyField(LegacySpecialField.Silverkey)
    }

    milestone(Milestones.ydargVictory) {
        legacyPartyField(LegacySpecialField.Silverkey, achievedValue = 2)
    }

    winningConfiguration {
        requiresAllMilestonesMarkedRequired()
        completedBy(Milestones.ydargVictory)
    }
}
```

A milestone can have multiple roles: persisted story state, special-inventory
display row, prerequisite for winning, capability gate, or migration alias.
Those roles are metadata on the milestone, not separate C fields and not
slotted inventory.

C should store the compiled dense milestone ids in party state, not five
special-case bytes. The renderer/inventory view can ask content metadata which
achieved milestones project as special items. The loader should validate the
content-defined winning predicate at startup, and legacy `silverkey == 2` should
map to a separate completion milestone rather than overloading the Silver Key
state.

### 8. Load and save state DSL snapshots

Add a deterministic writer for full `state {}` snapshots. It should write every
persisted field in a stable order.

Add a loader for the same state model. The loader can consume either:

- the compiled state bundle produced from Kotlin-authored states, or
- the strict runtime snapshot subset written by the save system.

New saves should not require legacy positional arrays. Old JSON5/current-world
loading can stay temporarily as a migration path.

### 9. Keep native engine operators narrow

The compiled model should contain native op ids only from the allowlist. The C
registry should reject unknown native ids at load time and should reject wrong
argument types before the script can run.

Native calls should shrink over time as game rules become DSL operations.

## Validation requirements

The Kotlin DSL compiler or loader should validate:

- every referenced handle exists
- no duplicate slugs in a resource table
- no duplicate flag states within a flag
- all flag transitions use states from that flag
- every map cell resolves to a terrain id
- terrain declarations have semantic movement/passability independent of
  presentation
- terrain render styles reference valid palette colors or image assets
- every visual asset has a known kind, dimensions, and backend-compatible source
- every screen/layout region referenced by scripts or render commands exists
- all text placeholders are satisfied with typed bindings
- all scene/script variables are in scope
- all native calls are allowlisted and type-correct
- all authored states satisfy the content schema
- runtime save snapshots are complete and deterministic

## Migration phases

1. Keep current JSON5/C data as reference.
2. Grow `dsl-next.kts` into a representative typed content/state source.
3. Define the native bundle structs and ids.
4. Add C string interning and resource hash tables.
5. Model maps, terrain, visual assets, screen regions, and render styles.
6. Add state DSL snapshot writer/reader.
7. Replace flag storage with dense `flag_id` indexing.
8. Replace map bytes with terrain ids.
9. Replace text placeholder strings with placeholder ids.
10. Add the typed renderer boundary over the current ANSI backend.
11. Replace the JSON node script interpreter with typed ops.
12. Port scenes/spells/items/missions/maps from JSON5 into Kotlin DSL.
13. Remove JSON5 from the runtime path after parity tests pass.

The final state is: Kotlin DSL is the canonical source, C consumes compiled
typed content/state, and runtime saves are exact state DSL snapshots.
