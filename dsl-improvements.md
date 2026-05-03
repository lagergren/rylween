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
- Temporary handle catalogs can be Kotlin while the authoring compiler is not
  finished, but game content should not drift into a hand-maintained metadata
  file. The target source of truth is the game DSL: designers should be able to
  add an item, monster, NPC, mission, dungeon, town, scene, or item pool by
  adding a local declaration in the game source. The compiler/model layer can
  derive typed handles, slugs, dense ids, catalogs, and backend bundles from
  those declarations; authors should not normally write slug strings or touch
  runtime code for content changes.
- Game-specific content should be declared in plain domain blocks. Adding a
  monster, flag, mission, milestone, text, or scene should mean adding a content
  record, not writing a Kotlin class.
- Game-specific reusable behavior needs its own authored-script layer. If the
  current Rylween game wants a reusable "traveller story" or "temple ritual"
  helper, that belongs in `src/game/kotlin/rylween/game/RylweenGameScripts.kt` or a future
  game-script library, not in the Kotlin DSL kernel. The kernel should provide
  only the generic verbs those helpers need.
- The Kotlin DSL project is now a small Gradle build named `rylween` with
  source packages split by responsibility: `rylween.dsl` under
  `src/main/kotlin/rylween/dsl` owns the authoring model and validation,
  `rylween.runtime` under `src/main/kotlin/rylween/runtime` owns reusable
  sessions/state machines, `rylween.engine` under
  `src/main/kotlin/rylween/engine` owns backend-neutral ports/traces/boot
  facade, `rylween.frontend.terminal` under
  `src/main/kotlin/rylween/frontend/terminal` owns line-terminal prompt
  rendering/adapters, and `rylween.save` under `src/main/kotlin/rylween/save`
  owns save-shaped snapshot parsing/emission. Current-game handles are now
  generated under `build/generated/gameCatalog/kotlin` from
  `src/game/kotlin/RylweenGame.dsl.kts`, so no handwritten main-source game
  catalog remains. Reusable current-game script helpers live in
  `src/game/kotlin/rylween/game/RylweenGameScripts.kt`. The content source
  lives at `src/game/kotlin/RylweenGame.dsl.kts`.
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
the DSL shape is represented in `src/game/kotlin/RylweenGame.dsl.kts`
or the smaller `dsl-next.kts` prototype lab where appropriate, mutable data
has save-state coverage, native calls are named and typed, and at least one
parity check or replay fixture is planned.

The "DSL delta" text is the concrete thing to look for in the Kotlin diff.

## Coverage Ledger

Keep two separate counts while migrating:

- DSL/runtime capability coverage: whether the language/runtime can express,
  validate, execute, and save a subsystem.
- Full-game content coverage: how much of the actual current Rylween game data
  has been authored in the Kotlin game source.

Current status:

| Area | DSL/runtime capability | Full-game content in game source | Not yet fully implemented |
|------|------------------------|------------------------------|---------------------------|
| Project/build | Gradle wrapper, Kotlin JVM build, tests, game DSL validation, validation split, generated current-game handle catalog, current-game script helper source set, generated compiled-game test harness, app source set, packaged app resources for ANSI art/tile art/static documents, runnable Kotlin town, wilderness, and ANSI runtime tasks, first runtime source splits for shared state/RNG/NPC placement and battle/combat scripting | N/A | publishing/package layout; compiled bundle emission beyond JVM-generated Kotlin; continue splitting oversized runtime sources by responsibility |
| Runtime state/save | `stateFixture`, strict `runtimeState`, validation, deterministic text writer, strict snapshot parser, runtime session wrapper for `RealmState` plus parties, realized NPC placements, and realized quest locations, persisted `lastTerrain`, first save-shaped town-service continuation stack, seeded RNG state mutation, turn-start wilderness upkeep mutations, active Ydarg/dungeon mode snapshot blocks, and save metadata with snapshot format plus compiled-content hash | 3 state fixtures, including near-winning and winning runtime snapshot | filesystem save-slot policy, multi-party real saves, full exact snapshot coverage from live engine |
| Flags/milestones/win | typed flag states, milestone projection, generic win predicate | 13 current story/encounter flags, 6 milestone records | full transition graph validation, generated catalog from source data |
| Items/equipment | declaration-first `items { ... }` records and pools in the game DSL, slotted inventory, separate equipment, authored semantic item pools, generic `itemUse(...)` scripts with contexts/consume policy, generic inventory item-use session, pure Kotlin ready/off mutations, reusable equipment prompt commands, town-leave ready prompt session, terminal-style item/equipment adapters, first gear/resource side-effect helpers, battle item-use script execution, battle weapon-damage and armor-class effects | 63 current item records and 7 item pools are authored in the game DSL; red/blue/amber potion behavior is scripted as inventory item use; all 12 current battle magic-item effects are authored item uses; the wilderness shell exposes the original Q quaff potion action plus W/R/T/P equipment actions where currently supported | content compiler that can derive handles/catalogs from declarations, wieldability/class restrictions, full rest/camp/dungeon item effects |
| Towns/services | town declarations, service availability, reusable town menu/service shape, exact service hours, first typed service scripts, reusable shop service and random-item offer helpers, first Kotlin shop interpreter/session plus scripted trace and terminal adapters, top-level town terminal session, first `RylweenKotlinEngine` town-session boot facade, runnable `runKotlinTown` terminal task, resumable stateful service-script bridge with real realm scalar and RNG mutation, visible `waitInput`/pause handling, first runnable church, inn, hut, speak/rumour, scruffy-offer, tavern order/story, bargirl lodging, and rumour quiet-limit service parity, compiled-game runtime harness for authored town-service tests, shared authored input bindings for static town/shop/script choices | 4 current towns, C/S/I/M/T/L menu, 5 town building services plus wilderness hut scene; church, smith, inn, merchant, tavern, and hut first-pass scripts with typed text, reusable choices, shop catalogs, scruffy random-item purchase, inline traveller-story content, authored bargirl lodging question action, and the current 30-entry pause verb pool | C/server adapters for town/shop/speak sessions; tax preamble, broader realm/session transitions, and line-by-line small-screen/pause parity against the C/JSON5 replay snapshots |
| NPCs/roles/visits | character declarations, roles, fixed/random placement, runtime NPC placement realizer, map-backed wilderness placement policy, runtime NPC hut terrain projection, visited bits | 39 current NPCs, 17 story roles, representative runtime placements, authored dialogue branches for all current role-bearing quest NPCs | generated NPC catalog, broader parity checks for NPC branch control |
| Speak/chitchat/rumours | standard conversation model, role/town text refs, anonymous rumours, exclusions, first reusable speak session with NPC selection from realized runtime placements, visited bits, automatic first-matching dialogue branch dispatch, authored question-action dispatch, chitchat/question lookup, seeded chitchat/rumour selection, runtime role/town text binding, and standard rumour filtering | no-NPC/no-rumour fallback screens, chitchat/question tables, authored bargirl lodging question action, 33 town rumour entries, role-bearing NPC dialogue branches, Garth branch execution through normal speak selection | explicit branch leave-vs-menu control, richer rumour selection policy, optional authored wording fields for conversation prompts if terminal-derived labels stop being enough |
| Combat/rewards | reusable standard battle shape, action list, tunables, authored prompt/menu text, authored static fight/run and action input bindings, reward bundle, formula catalog, leveling table, reusable `BattleSession` for encounter allocation, fight/run, per-member player actions, target prompts, mage spell casting, combat magic-item use, monster melee/spells, death, victory rewards, loot, and level-up application/narration; reusable out-of-combat priest spell picker/session | standard battle tunables/text, all 68 current monster records with weak/wild/deep tiers, 6 mage battle spells, 4 monster spells, 7 priest spells, 12 battle magic items, battle loot/reward shape, and authored combat/priest text/effect bodies | Revelation dungeon effect, broader parity traces |
| Missions/scenes/scripts | typed scenes/op AST, dialogue branches, rewards, reusable choices, explicit control signals, native allowlist, current-game script helper layer, wilderness random-step/terrain/position/location mission polling, reusable `enterDungeon(...)` handoff op, script journal append, runtime location-coordinate text refs, inventory-pool count/consume ops, resumable mission-scene execution with nested battle continuations, and persisted post-dungeon mission continuations for dungeon saves | 17 authored wilderness missions: bandits, troll tooth, Thun gem altar, kobold village first battle handoff, skullweed, Borin meet with journal, Borin gift, Ogre cave first battle handoff, Foot of Jazzy first battle handoff, Hobgoblin caravan first battle handoff, and seven current dungeon-entry mission prompts/handoffs; Garth dialogue, rumour/church/Ydarg probes, tavern traveller-story helper | broader parity tests for C edge cases, typed compiled fragment identity when persistence/debugging/bundles need it |
| World/map/terrain/visual/layout | typed handles exist for many concepts; `terminal { ... }` declares the current two-character terminal terrain glyph/color table and classic screen regions; `visuals { staticScreens { ... } }` declares frame/title/help/manual/score assets; `worldMap(Maps.wilderness) { layer(MapLayers.terrain) { rows(...) } }` now provides the wilderness map rows; `WorldMapGrid` remains the reusable runtime representation preserving world coordinates, passability/settlements, NPC hut projection, terminal viewport rendering, authored wilderness input bindings, and terrain-transition narration | four current town coordinates, 10 current terminal terrain glyphs, 5 classic screen regions, wilderness movement key/arrow aliases, static screen/document asset references, authored wilderness map rows, and eight current terrain-transition texts are exercised by the compiled game runtime | bitmap terrain/assets, runtime navigation/display flows for manual/score/title where needed, broader screen-layout parity |
| Dungeons/Ydarg/secrets/score | narrow native islands and handles plus cave-found and authored-script dungeon handoff shapes; reusable `DungeonSession` with run-local level event tables, authored event execution, D/A/?/equipment/quaff/priest-spell routing, torch descent checks, dungeon leave handoff, semantic `teleportParty(...)` and `discoverCave(...)` ops, semantic `context.inDungeon`/`context.dungeonLevel` script predicates, text-answer prompts, game-authored cave/dungeon backdrops, first compiled dungeon playthrough checks, validated authored `dungeons { event(...) scenario(...) }` content table, authored `YdargContentModel`, and reusable `YdargSession` handoff | random-cave discovery prompt/cost/depth band is authored; fixed Erethor/husband/hermit/Nolrim/Master Crown/Silver Key/Hobgoblin dungeon entries now hand off to semantic dungeon handles; random dungeon fill now includes semantic found-object pools and gold chests; authored special events now cover Thun riddles, Reebar colour riddles, Silver Sanctum, Hobgoblin patrol/guards/chief, Ogre/Orc/Tammy, and Erethor priests/Lute chamber; fixed scenarios explicitly use the same standard random dungeon event mix plus their special placements; priest Teleport and Earthquake first-pass effects use authored script plus semantic runtime ops; first Thun/Reebar/Silver Key playthrough checks cover riddles, `context.inDungeon`/`context.dungeonLevel`, and password reward; dungeon `S` save/load now resumes active dungeon depth, level-event plan, and post-dungeon mission continuation; Ydarg no-key/key entry, stair gift, shared utility-command checks, `S` save, and save/load resume for floor/point/room state cover the semantic session path; post-dungeon continuations resume mission scripts | deeper dungeon treasure/secrets, Revelation dungeon effect, secrets/sysop actions, score/vscore |

Rule for future checklist updates: every pass should say both what DSL/runtime
capability moved and what real game content count moved. If either side did not
move, say that explicitly.

### A. Foundation and authoring surface

- [x] A0. Seed typed DSL direction. DSL delta: `rylween { content { ... } state { ... } }`, typed handles, typed flags, first party-state model. Native decision: JSON5 is reference only, not the target runtime format.
- [x] A1. Split the prototype into clear authoring sections. DSL delta: stable `content`, `state`, `texts`, `scripts`, and handle/catalog sections. Native decision: content compiler owns handle derivation. Current status: implementation source is now physically and logically grouped into `rylween.dsl` (`src/main/kotlin/rylween/dsl`), `rylween.runtime` (`src/main/kotlin/rylween/runtime`), `rylween.engine` (`src/main/kotlin/rylween/engine`), `rylween.frontend.terminal` (`src/main/kotlin/rylween/frontend/terminal`), and `rylween.save` (`src/main/kotlin/rylween/save`). App launchers live in `rylween.app` under `src/app/kotlin/rylween/app`; reusable current-game script helpers live in `src/game/kotlin/rylween/game/RylweenGameScripts.kt`; `src/game/kotlin/RylweenGame.dsl.kts` holds the game authoring area; generated current-game handles live under `build/generated/gameCatalog/kotlin`.
- [x] A2. Replace hand-maintained catalog scaffolding with declaration-first content. DSL delta: declarations such as `items { treasure("Red Gem") { cost = 1000 } }`, `monsters { monster("Orc") { ... } }`, `dungeons { dungeon("Temple of Thun") { ... } }`, `characters { character("Garth") { ... } }`, and `missions { mission("Bandits") { ... } }` should be enough to introduce new world content; generated typed accessors can be emitted under `build/generated` when scripts need symbols. Native decision: compiled bundles can receive dense ids plus diagnostic slugs derived from declarations, but slugs/ids are compiler/runtime artifacts, not normal authoring vocabulary. Current game source status: all current-game handles are generated from `src/game/kotlin/RylweenGame.dsl.kts`; reusable `rylween.dsl`, `rylween.runtime`, `rylween.engine`, `rylween.save`, and `rylween.frontend.terminal` do not import `rylween.game`.
- [ ] A3. Define typed native-op declarations. DSL delta: `nativeOps { op(NativeOps.renderMap) { ... } }` or equivalent. Native decision: no raw string function names in authored scripts.
- [ ] A4. Define validation error style. DSL delta: validation helpers report game concepts such as missing terrain, text binding, or illegal flag transition. Native decision: loader can reject compiled bundles defensively but author-facing validation happens before C.
- [ ] A5. Define reusable script-fragment identity only when it has a runtime need. DSL delta: keep Kotlin game-script helper functions such as `travellerStory()` for local reuse until persistence, debugging, trace reporting, or compiled-bundle emission needs a stable fragment id. Future shape should use typed generated handles such as `GameScripts.travellerStory`, not stringly authored ids such as `scriptHelper("traveller_story")`.
- [x] A6. Compile the game model for tests. DSL delta: generate a test-only `RylweenCompiledGame` wrapper from `src/game/kotlin/RylweenGame.dsl.kts` so tests can access `nextRylween` and `nextDsl` directly. Native decision: this is a JVM test harness only; C/server/native compiled bundles remain separate H-series work.
- [x] A7. Split oversized runtime sources without moving game semantics into Kotlin. DSL delta: none directly; this is architecture hygiene so reusable engines stay understandable. Native/runtime decision: split the old monolithic runtime into focused `rylween.runtime` sources for battle, camp, dungeon, hunt, shop, inventory/equipment state ops, rewards/random offers, priest/item-use, speak, town, world map, shared runtime state, and shared session inputs. Engine ports/traces moved to `rylween.engine`, terminal prompt/render adapters moved to `rylween.frontend.terminal`, and save snapshot code moved to `rylween.save`. Caveat noted by A7's second clause: the split itself did not lift every world-specific constant/rule out of Kotlin; that work is tracked under the **A8 minimization plan** below.
- [ ] A8. Minimization and integration plan: move config-able Kotlin rules into the authored DSL, keep reusable runners backend-neutral, and consolidate runtime files only when it pays for itself. Current status: Track A's original Kotlin-to-DSL lift list is complete; Track C has several content-fill items landed; the active risk is integration drift where a new service feature gets hardcoded into a reusable runtime instead of authored as game content.

  **Track A — Kotlin → DSL migration completed (so the game designer can change rules without touching Kotlin):**
  - [x] `RylweenInventoryOps.kt:foodCapacityByDonkeys()` — lifted to realm base resource capacity plus item-record resource-capacity bonuses on `Items.donkey`.
  - [x] `RylweenInventoryOps.kt:campGearEffect()` — lifted to item-record camp-protection/image metadata for tent and blanket.
  - [x] `RylweenShopRuntime.kt:ShopQuantityCap` — generalized to authored resource-capacity cap sources instead of fixed enum cases.
  - [x] `RylweenShopRuntime.kt:bargainPrice()` smith haggle — lifted to authored shop bargain policy.
  - [x] Prompt-text objects in Kotlin: `PriestSpellPromptText`, `InventoryUsePromptText`, `EquipmentPromptText`, `TownPromptText` — converted to authored `prompts { ... }` data consumed by reusable sessions.
  - [x] `WildernessUpkeepRules` food/fatigue/starvation thresholds — promoted to authored `realm { upkeep { ... } }` balance tables.
  - [x] `TownLeaveEquipmentSession.readyCommands = [ReadyWeapon, ReadyArmor]` ordering — declared in `townSystem { leavePrompts(...) }`.

  **Track A follow-up — remaining prompt/content islands exposed by the latest audit:**
  - Active authoring-boundary cleanup checklist:
    - [x] Move compiled-game self-check scaffolding out of `RylweenGame.dsl.kts` and into tests/validation.
    - [x] Hide remaining `native.*` implementation hooks behind semantic game-script verbs where the game source still calls them directly.
    - [x] Centralize temporary unavailable/status messages so runtime/frontend adapters do not carry parallel copies.
    - [x] Share text-part fallback rendering between terminal/script/combat renderers instead of adding each token in multiple `when` blocks.
    - [x] Remove player-facing "not ported yet" placeholder text from the authored Revelation spell path.
    - [x] Delete `RylweenGameCatalog.kt` after declaration-derived handles replace it.
  - [x] `WildernessPromptText` and `DungeonPromptText` removed. Real wilderness/dungeon prompts now live in authored `prompts { wilderness { ... }; dungeon { ... } }` tables with domain tokens for dungeon display names/levels; runtime/frontend adapters no longer carry player-facing "not ported" placeholder strings.
  - `SpeakSessionTraceRunner` renders NPC picker and conversation prompts from the standard conversation prompt model plus authored `InputBindingModel`s; the stale speak menu/question text-table mirrors are gone. If authors need different wording, add explicit prompt fields to the standard conversation model instead of reintroducing parallel screens.
  - [x] Do not add service-specific branches to `SpeakRuntime`. Bargirl lodge behavior is now an authored speak `questionAction(QuestionTopic.Lodging, NpcJobs.bargirl)` script; `SpeakRuntime` only dispatches generic dialogue/question script branches.

  **Track B — Final redundancy / consolidation (only when it pays for itself):**
  - Per A7's bucket list (shop, inventory/equipment state ops, random offers/rewards, priest/item-use, speak, town/interpreter), priest and inventory item-use sit in two files and could be merged into a single `RylweenItemActionRuntime.kt` if a future change touches both. Not urgent.
  - [x] The generic script-continuation file is now `RylweenScriptContinuation.kt`; script continuation/frame/block types use generic script names because they are shared by town services, speak, priest, inventory item-use, and mission resumes.
  - `src/main/kotlin/rylween/game/RylweenGameCatalog.kt` has been deleted. Current-game handles are generated from authored declarations/usages under `build/generated/gameCatalog/kotlin`; reusable main packages still do not import `rylween.game`. Default slugs needed by reusable builders/runtimes live internally in `src/main/kotlin/rylween/dsl/RylweenDslDefaults.kt`.
  - The old `RylweenEngine.kt` has been deleted and split by role: `rylween.engine` owns `RylweenEnginePort`, trace runners, and `RylweenKotlinEngine`; `rylween.frontend.terminal` owns `TerminalPort`, `TerminalTextRenderer`, terminal input helpers, prompt rendering policy, line-terminal session adapters, and the stateful terminal script bridge. Generic script continuation naming is now in place, and trace-only terminal child runners sit outside the stateful bridge implementation.
  - The current frontend boundary is usable but not final. The reusable session state machines (`WildernessSession`, `TownSession`, `BattleSession`, etc.) are backend-neutral, and line-terminal adapters now sit outside the core engine. Authored script rendering now emits typed `ViewCommand` events instead of raw native screen requests; remaining frontend polish is about richer view layouts and session-specific render flows, not rule ownership.
  - [x] Collapse duplicated menu rendering in line-terminal adapters. Static options now come from authored `InputBindingModel`s or runtime-generated keys and pass through shared `TerminalTextRenderer`/`TerminalPort` prompt helpers. The frontend suppresses the generated `Options: ...` footer when the prompt already contains all key markers, so prompts such as `(P)ay/(N)one` stay single-sourced.
  - [x] Keep command help DRY. `visuals { staticScreens { commandHelp(...) } }` is only a static-screen route; live `?` help for wilderness, dungeon, and Ydarg is generated from the same typed command binding lists that drive input dispatch. Optional authored `helpPages { ... }` remains an override, not the default copy.
  - [x] Prepare the map/rendering boundary for bitmap frontends. `WorldMapGrid.viewport()` now returns `WorldMapViewport` cells with points, terrain tiles, and center metadata; terminal character-row rendering lives in `TerminalTextRenderer`, and ANSI tile/glyph rendering lives in the ANSI adapter.
  - [x] Replace `native.screen.*` with typed view commands. Authoring now uses `clearText`, `clearMapWindow`, `showBackdrop(ImageId)`, `renderMap`, and `statusLine`; trace/session output carries `EngineTraceEvent.View(ViewCommand.*)`, and the ANSI frontend implements those commands without parsing native request strings.
  - [x] Resolve current-world text through the frontend boundary instead of leaking native placeholders. `townNpcName(...)`, role names, role locations, town names, fuzzy locations, and coordinates now flow through shared terminal world-text resolution used by script events and direct text rendering.
  - [x] Collapse repeated terminal wait/apply input handling and bridge-event rendering. Line adapters share `TerminalInput.sessionOrWait(...)` and `TerminalPort.writeScriptEvents(...)`; script scalar/member lookups are table-driven instead of repeated `when (name)` / `when (field)` branches.

  **Track B/C — Vertical content fill (existing APIs, missing data):**
  - [x] Hunt runner: authored `realm { hunt { ... } }` balance and a reusable hunt session now cover success chance, time cost, food gain, narration, and state mutation.
  - Missions: full multi-battle completion bodies for Kobold/Ogre/Foot of Jazzy/Hobgoblin caravan run through `enterDungeon(...)` + post-dungeon resume; `runs(...)` slots already exist (per D6/G2).
  - [x] Dialogue: role-bearing NPC quest branches now have authored `dialogue { npc(...) { whenState(...) { ... } } }` bodies; remaining work is branch-control polish and parity coverage.
  - Dungeons/Ydarg: Revelation priest spell radius/effect still needs room-radius parity; `DungeonSession` already runs authored events and post-dungeon continuations now resume. Ydarg room/state/content/session handoff has landed; its secondary in-castle utility commands now reuse the shared party/equipment/drop/item/spell child-session boundary, and Ydarg `S` saves/resumes floor, point, room state, and resume step through the runtime snapshot.
  - [x] Static screens: frame/title/help/manual/score/vscore are declared as `visuals { staticScreens { ... } }` assets; static command help stays a screen-resource route while live session help is generated from command bindings; the Kotlin line and ANSI launchers can now render authored static screens/documents through the frontend boundary with `--screen`.
  - [x] Map: `worldMap(Maps.wilderness) { layer(MapLayers.terrain) { rows(...) } }` replaces the runtime byte-loader default while retaining `WorldMapGrid` as the reusable representation.
  - [x] Camp/rest runner (E4): authored camp balance, item-record protection metadata, food gate, time advance, healing, temperature damage, and ambush handoff are implemented as a reusable runtime session over authored data.
  - [x] Save reader (B9): the strict-runtime snapshot subset now has a parser/reader to pair with the writer. Filesystem save/load policy is still open.
  - [x] Hut/lodge town services (E3): `Scenes.Towne.hut` runs through the normal scene/speak bridge for wilderness-position NPCs, and bargirl lodging now lives in authored speak content instead of `SpeakRuntime` special-case logic.
  - Score/vscore (G5): decide content vs. native-only first.

  **Current tie-together queue before the next feature commit:**
  - [x] Rework the dirty hut/lodge patch so `Scenes.Towne.hut` runs through the existing scene framework, wilderness hut entry resumes through the normal service bridge, and wilderness-position NPC lookup stays in reusable speak content.
  - [x] Replace the hardcoded bargirl branch currently sketched in `SpeakRuntime` with authored lodge content: price `party.leader.level * 50`, Y/N prompt, broke/spent text, gold mutation, and per-member HP drain now live in the game DSL question-action script.
  - [x] Add tests that prove hut entry can talk to a wilderness NPC and that bargirl lodging mutates gold/HP from authored content.
  - [x] Ydarg follow-up: persist in-progress `YdargSession` state through the save snapshot and add final-confrontation scenario coverage. ANSI parity traces remain under D6/H9.
  - [x] After hut/lodge lands, update this ledger in the same commit so "not yet implemented" does not drift again.

  **Debt elimination burn-down — dependency order for getting to zero known migration debt:**
  - [x] D0. Land the current authoring-boundary cleanup as one commit after validation. Scope: moved compiled-game ledger test, shared text fallback rendering, centralized unavailable status text, and Revelation placeholder removal. This is the baseline before starting larger debt removal.
  - [x] D1. Build declaration-derived handle generation and delete `RylweenGameCatalog.kt`. Exit criteria met: game source imports generated handles, reusable `main` packages still do not import `rylween.game`, and `src/main/kotlin/rylween/game/RylweenGameCatalog.kt` is gone.
    - [x] D1a. Generate `Items` and `ItemPools` from the authored `items { ... }` declarations and remove their handwritten block from `RylweenGameCatalog.kt`.
    - [x] D1b. Generate story flag, milestone, formula, and reward handles from declarations, then delete that handwritten catalog slice.
    - [x] D1c. Continue through terrain/images/maps/layout/static screens, towns/buildings/NPC jobs/roles/rumours, monsters/encounter tiers/spells, scenes/missions/dungeons/Ydarg, small leftover handles, and text refs until the transitional catalog file is gone.
      - [x] D1c-visuals. Generate maps, map layers, terrain, images, static screens, and screen regions from declarations/usages; remove dead `Layouts` scaffolding that had no model or callers.
      - [x] D1c-social. Generate buildings, towns, NPC jobs, NPC roles, rumour tables/handles, NPC ids, and location ids from authored declarations/usages; remove those handwritten catalog blocks.
      - [x] D1c-combat. Generate monsters, encounter tiers, grouped spell handles, and loot tables from authored declarations/usages; remove dead flat spell and combat-loot aliases.
      - [x] D1c-scenes. Generate grouped scene handles, mission handles, dungeon ids, and dungeon-event ids; remove dead scene/mission aliases plus the temporary `thunTemple` extension handles from game-script helpers.
      - [x] D1c-small-handles. Generate palette colors plus state/party ids; remove dead current-game `KeyActions`, `TerrainTags`, and `Palettes` wrappers.
      - [x] D1c-text. Generate the `Texts`/`T` hierarchy, including text-pattern leaves and legacy id overrides; delete `RylweenGameCatalog.kt`.
  - [x] D2. Remove author-visible implementation vocabulary. Scope: raw slug constructors in normal authoring, generic `native.*` escapes, `let("name", ...)` when a typed/local helper exists, implementation-only prefix/suffix naming, and any remaining placeholder text that describes runtime port status. Exit criteria met for normal game content and scripts; remaining runtime-state fixture vocabulary belongs to D4/B9 save-state work.
    - [x] D2a. Town content declarations now use generated `Towns.*` handles instead of raw `town("...")` slugs.
    - [x] D2b. Tavern rumour content now uses typed `tellRumour(...)` over authored rumour tables; the legacy `towne.rumour` switch scene, `{nameN}`/`{locN}` binds, `rumour.roll()` helper, and bridge raw-scene special case are gone.
  - [ ] D3. Finish core runtime parity gaps before widening architecture. Scope: real Revelation dungeon-radius state/effect, dungeon room/secret semantics if the C parity pass proves there is more than authored level events, castle-entry parity, score/clean-exit behavior, character creation, secrets/sysop/debug policy, equipment wieldability/class restrictions, and any monster-specific behavior hooks uncovered by parity tests.
    - [x] D3a. Mission scripts that call `enterDungeon(...)` now keep a post-dungeon continuation and resume after the dungeon session finishes instead of returning directly to wilderness.
    - [x] D3b. Authored Kobold village, Ogre cave, Foot of Jazzy, and Hobgoblin caravan mission scenes now have full mission-level regression coverage that resumes through child battles and verifies final flags, rewards, milestones, and journal effects.
    - [x] D3c. Priest-spell adapters now run authored spell scripts with full `RuntimeContent`, so Revelation's successful dungeon find-object/take branch can resolve item records and mutate inventory instead of failing at the frontend boundary.
    - [x] D3d. Wilderness, dungeon, and Ydarg now share reusable party-view and inventory-drop sessions. Dungeon/Ydarg utility keys no longer own local placeholder text or separate drop/view implementations; drop uses item-record display names and resource-capacity metadata, so dropping a donkey clamps food through authored capacity rules. Ydarg W/R/T/P/Q/Z now routes through the shared equipment, item-use, and priest-spell child sessions instead of the old unsupported-command placeholder.
    - [x] D3e. Wilderness `J` now runs a reusable party-journal session over persisted journal entries and authored `prompts { party { journalHeader } }` text; terminal and ANSI adapters render that semantic session instead of carrying a "not wired" placeholder.
    - [x] D3f. Wilderness `S` now emits a backend-neutral save step and the terminal/ANSI frontends satisfy it through a `RuntimeSavePort`. The installed ANSI launcher and line wilderness shell can write strict runtime snapshot files with `--save`, boot one with `--load`, and the out-of-moves `S` path saves before quitting.
  - [ ] D4. Persist or reject every active runtime mode. Scope: live-engine full snapshot write/load where useful, active child sessions where needed, and universe id/version/hash compatibility checks. Player-facing save policy does not have to expose a save command in every mode; old-C-style wilderness-only save points are compatible with exact injectable engine snapshots. First file-backed snapshot write/load exists for active wilderness state via `RuntimeSavePort`, `FileRuntimeSavePort`, `--save`, and `--load`; `--save-slot`/`--load-slot` now resolve safe names under `build/saves`; new file saves include snapshot-format/content-hash metadata and loads reject unsupported formats or mismatched compiled content; Ydarg navigation now has `S = Save Game`, writes a mode snapshot with floor/point/room-state/resume-step data, resumes through wilderness startup, and rejects lossy `YdargCastle` saves that omit the session block; dungeon `S = Save Game` now writes dungeon/depth/level-event/post-dungeon continuation snapshots, resumes through wilderness startup, and rejects lossy `Dungeon` saves without a session block. `FileRuntimeSavePort` now rejects unsupported active modes (`TitleMenu`, `Town`, `Battle`, `GameOver`) before writing a lossy state-only file. Exit criteria: save/load can round-trip intentionally saveable wilderness, dungeon, and Ydarg flows; additional modes either gain exact snapshots for injection/debug/frontends or explicitly reject before data loss.
  - [ ] D5. Replace runtime source dependencies with compiled content/bundles. Scope: `meta {}` / `limits {}`, explicit map dimensions where needed, terrain semantics/visual styles, image/palette/layout asset records, compiled content bundle schema, compiled state image schema, string interning/dense ids for loaders, typed script/op VM, and removal of normal runtime reads from JSON5 or `data/map.dat`. Reference readers may remain only as offline migration/debug tools.
  - [ ] D6. Finish the replaceable frontend boundary. Scope: keep ANSI, line-terminal, future bitmap, C bridge, server, and headless adapters over the same semantic session inputs and view/state events; route nested town/battle/item/dungeon flows through layout-aware rendering rather than modal text dumps; add direct battle banner/victory image rendering; wire save prompts; keep command help generated from bindings without overflowing the text window.
    - [x] D6a. Standard battle visuals now cross the same frontend boundary as scripted scene visuals: authored encounter banners emit `ViewCommand` image events from `BattleSessionStep.Opening`, the game DSL declares the standard victory image, victory messages carry semantic clear/show-image commands, and terminal/ANSI frontends render them through `TerminalPort.writeViewCommand` rather than local battle-specific image shortcuts.
  - [ ] D7. Consolidate remaining naming and duplication debt. Scope: keep generic script-continuation naming, merge priest/inventory item-action runtime only if it reduces real coupling, keep trace-only runners separate from stateful execution, run recurring duplicate-code/unused-symbol sweeps in terminal adapters/script bridge/engine/runtime, and remove temporary fallback strings as soon as their features land.
    - [x] D7a. Removed the temporary player-facing runtime fallback object and the town "service is not implemented yet" prompt; missing hunt rules, town service bodies, hut scenes, and Ydarg castle content now fail as content consistency errors instead of leaking port status text. Standard conversation menu headings and action labels now live in the reusable standard-conversation model rather than being duplicated in the script bridge, and terminal child-session handoff plumbing is shared across camp, town, dungeon, wilderness, and Ydarg adapters.
    - [x] D7b. Renamed the generic resumable script continuation model out of town-service vocabulary, renamed the terminal script bridge API to `ScriptBridge`, split trace-only child runners and script scalar/member bindings out of `RylweenTerminalScriptBridge.kt`, and removed the remaining script-bridge `when(name)` scalar dispatch tables from the bridge file.
    - [x] D7c. Moved shared authored-script runtime/member/party/realm binding tables from the terminal frontend into `rylween.runtime`, rewired wilderness mission requirement evaluation and combat scripts to use them, and table-drove battle-specific monster fields, battle flags, battle queries, and monster flags so `when(name)` dispatch is gone from main runtime/frontend sources.
  - [ ] D8. Make validation author-facing and exhaustive. Scope: validation error style, flag transition graph validation, generated-handle/source-map diagnostics, terrain/text binding validation, mission/dungeon/scenario consistency checks, and startup/save-load validation that names game concepts rather than Kotlin implementation types.
    - [x] D8a. Added structured `ValidationDiagnostic`/`RylweenValidationException` output and `collectAuthoredContentDiagnostics(...)`, so authored content validation reports section paths and aggregates independent content failures instead of stopping at the first subsystem. Story-state references are now checked through scripts, mission requirements, dialogue branches, rumour exclusions, and runtime/save-state party flags; script screens and journal text validate role/town bindings through the same text checker used by speak/rumours.
    - [x] D8b. Added inferred story-flag transition validation. Dialogue `whenState(...)`, mission `requires(...)`, and positive `whenever(flagState)` branches now establish a source state for same-flag writes, and validation rejects regressive transitions such as moving an accepted quest back to none while still allowing cross-flag dependency updates.
    - [x] D8c. Generated game-catalog handles now keep source offsets while scanning `RylweenGame.dsl.kts`, so missing/unclosed declaration blocks, duplicate declarations, generated-id collisions, invalid terrain map codes, unknown scene groups, flat scene references, and text leaf/group conflicts fail with `src/game/kotlin/RylweenGame.dsl.kts:line:column` diagnostics before Kotlin reports errors from generated code.
    - [x] D8d. Terrain/text binding validation now checks that terrain transitions, terrain-triggered missions, and runtime-state `lastTerrain` values bind to declared terminal terrain styles and map tile codes where needed. Runtime-only text parts such as shop price, random-offer item/price, selected item name, hunt member values, dungeon handoff fields, dungeon depth, and conversation NPC name are now accepted only in text blocks rendered with the matching runtime binding context.
    - [x] D8e. Mission and script validation now catches semantic runtime-content cross references before play: dungeon handoff and cave-discovery ops require authored dungeon scenarios, dungeon random-encounter tiers must have authored monsters, scripted encounters must name authored monsters, nested `run(...)` scene calls must target authored scenes, and milestone predicates/achievements must target declared game milestones.
    - [x] D8f. Startup and save-load diagnostics now use the same authored validation path as compiled content: `--load` snapshots are parsed with `RylweenValidationException` diagnostics, loaded states are validated against current authored content before boot, wilderness launchers reject non-wilderness runtime modes explicitly, unknown startup states/towns list available authored handles, and terminal/ANSI launchers print concise startup failures instead of raw Kotlin stack traces.
  - [ ] D9. Create a reusable scenario/playthrough harness. Scope: named fixtures with start state, seed, semantic or terminal-like inputs, expected checkpoints, rendered text/view traces, and parity coverage for town services, wilderness events, dungeon runs, Ydarg, battle defeat/victory, save/load, and static screens. Exit criteria: broad regressions are caught by scenarios rather than ad hoc one-off tests.
    - [x] D9a. Added a reusable test-scope `TerminalScenario` harness with named fixtures, start-state/seed metadata, scripted terminal inputs, captured text/view/image traces, and named checkpoints. Migrated the compiled Thun/Reebar/Hobgoblin dungeon playthrough checks plus Kobold/Ogre/Jazzy/Hobgoblin mission-completion playthroughs onto the harness so future parity coverage can add scenarios instead of bespoke terminal-run boilerplate.
    - [x] D9b. Broadened the harness to shared runtime flows: scenario runs now expose reusable output/image assertions, and the wilderness save/out-of-moves save flows, Ydarg gate/entry handoffs, battle run escape, first battle round, and battle victory reward/loot checks run as named scenarios with fixture metadata and checkpoint-labeled failures.
    - [x] D9c. Added a compiled-town scenario helper over the same harness and migrated real compiled town/service flows: merchant torch purchase, smith sale, tavern scruffy offer, tavern drink, traveller story, seeded tavern paths, rumour quiet-limit, and the wilderness-to-town-to-wilderness shell handoff. Wilderness scenarios can now inject a stateful service bridge instead of hand-wiring one-off terminal ports.
    - [x] D9d. Added view-command assertions and a compiled-Ydarg scenario helper. Migrated file save/load boot validation plus authored image/view-event checks onto scenarios, added Ydarg navigation/treasure/pitfall/final-confrontation scenarios, added battle party-defeat scenario coverage, and fixed the battle runtime so party-defeat hit/death text is emitted before `Finished`.
  - [ ] D10. Retire historical port-plan debt once Kotlin DSL/runtime owns the surface. Scope: reconcile `port-plan-incomplete.md`, `docs/dsl.md`, and old JSON5/C scripting references against the Kotlin DSL plan; move completed historical sections to reference/complete docs; keep `plans/future-directions.md` explicitly outside the zero-debt target unless multi-party/MUD work is scheduled.

### Current game source decisions and open questions

Decisions from the first `RylweenGame.dsl.kts` pass:

- `src/game/kotlin/RylweenGame.dsl.kts` is the successor game source copied from `dsl-next.kts`; `dsl-next.kts` remains the small language/prototype lab.
- The Gradle project root is named `rylween` and uses the Gradle 9.5.0 wrapper with Kotlin JVM 2.3.21. Source packages now mark responsibility boundaries: `rylween.dsl` (`src/main/kotlin/rylween/dsl`) is the reusable DSL kernel/validation layer; `rylween.runtime` (`src/main/kotlin/rylween/runtime`) contains shared live state, RNG/NPC placement, reusable sessions, and pure state ops; `rylween.engine` (`src/main/kotlin/rylween/engine`) contains the backend-neutral engine port, trace runners, and Kotlin boot facade; `rylween.frontend.terminal` (`src/main/kotlin/rylween/frontend/terminal`) contains terminal prompt rendering and line-terminal adapters; `rylween.save` (`src/main/kotlin/rylween/save`) contains save-shaped state emission/parsing. `src/game/kotlin/rylween/game/RylweenGameScripts.kt` contains reusable current-game script helpers, `src/game/kotlin/RylweenGame.dsl.kts` is the game-specific declaration source, and generated current-game handles live under `build/generated/gameCatalog/kotlin`.
- Keep a hard boundary between DSL implementation and game authoring. Kotlin classes, builders, validators, and generated handles are implementation/compiler scaffolding; the game designer should add game-specific content through declaration blocks such as `flags { ... }`, `monsters { ... }`, `missions { ... }`, `gameProgress { ... }`, and `state { ... }`.
- Adding a new monster, flag, mission, special milestone, text, or similar game-specific concept should not require writing a Kotlin class. The authoring source declares the concept in game terms; the content compiler can generate typed handles from those declarations.
- Common Rylween engine/state concepts such as party state, member stats, inventory slots, positions, and validation helpers may remain normal Kotlin DSL implementation types because authors consume them rather than extend them.
- Common gameplay systems such as battle rounds, fight/run menus, spell selection/casting, item use, reward application, and encounter orchestration should become reusable Kotlin DSL/runtime-library concepts. Game authors should declare the content those systems consume, plus explicit hooks or overrides when needed; they should not hand-write bespoke battle loops for normal monsters or spells. Those reusable Kotlin systems may call a narrow native surface for input, rendering, RNG, persistence, and low-level storage.
- Author-facing scripts should use semantic context predicates such as `context.inDungeon` / `context.underground`, not encoded runtime scalars such as `v("place") eq 1`. Raw continuation locals may exist inside interpreters while a session is resumable, but they are not part of the game-authoring vocabulary.
- Menu and command input should be modeled as semantic choices with reusable input bindings, not as one hard-coded character per action. `InputBindingModel` carries a primary input, aliases, and a display label. Ownership rule: static authored choices own bindings in the model because authors may want aliases, labels, controller mappings, or server input mapping for those choices; runtime-sized choices such as party member numbers, inventory slot letters, monster target letters, and spell-slot digits are generated by the runtime/adapter from current state and immediately translated into semantic session inputs. Current static model-owned bindings include wilderness commands, dungeon commands, Ydarg navigation/treasure/stairs commands, town-entry menu actions, speak/question menu actions, shop main/purchase/sale/interlude/catalog/item choices, random-item offer buy/decline, battle fight/run, battle player actions, and script `menu` options. Session inputs such as `TownSessionInput`, `InventoryItemUseInput`, `ShopServiceInput`, `SpeakInput`, and `BattleSessionInput` remain semantic and do not own terminal keys. Terminal adapters consume reusable `InputCommand` lists where keysets are command-shaped; displaying an `Options: ...` footer is frontend policy, but the labels and key markers come from these authored/runtime bindings through the shared terminal prompt renderer so the same choices are not re-authored per adapter.
- The runtime boundary is backend-neutral: `RylweenEnginePort` plus focused runtime ports such as `RylweenRandomPort` can be implemented by the current C engine bridge, a pure Kotlin JVM runtime, a server process, or a future native executable. The DSL/runtime layer should depend on the port contracts, not on a hard-coded C implementation.
- Content changes should not require runtime-code changes. During migration we may rebuild the JVM project because content and runtime sit in one Gradle build, but architecturally a new item, mission, dungeon, NPC, monster, town, scene, or map row should compile into the game model/bundle and run on the same Kotlin/C/server/native runtime. The only compatibility requirement is save/world compatibility: runtime snapshots should record a game-universe id/version/hash, and loading should reject incompatible worlds or run an explicit future migration. We are not carrying old C/JSON save compatibility as a design constraint.
- A Kotlin ANSI frontend/runtime shell is a useful near-term backend as long as it stays isolated as a replaceable view/input adapter. It should own terminal modes, ANSI cursor/window drawing, asset/text rendering, input decoding, logging/socket fan-out, and file persistence, while consuming the same compiled game model and session APIs as a bitmap frontend, C bridge, server runtime, or headless test runner. It should not contain world-specific game rules. First pass: `installKotlinAnsi` uses Gradle's built-in start-script generation to install `build/install/rylween-kotlin/bin/rylween`; the direct launcher uses JLine, packaged `ansi/**`, `tiles/**`, and static document resources, authored wilderness map rows, authored terminal terrain glyph/color records, authored classic screen regions, raw key reads where a real terminal supports them, a typed `ViewCommand` rendering hook for script-owned screen actions, and `RYL_LOG=stderr|file:<path>|tcp:<port>` logging sinks. Do not use Gradle's `JavaExec` path for interactive ANSI play because Gradle's console/progress output shares the screen.
- Runtime design is model/session first and view adapter second. Game rules and state transitions live in the compiled game model plus reusable sessions (`WildernessSession`, `TownSession`, `BattleSession`, future dungeon/Ydarg sessions). Frontends translate device input into semantic session inputs and render view-neutral state/events. Replacing ANSI terminal graphics with bitmap graphics must not require changes to game content, rules, save state, or session logic.
- The existing line-oriented terminal adapters are not throwaway. They are the headless/test-friendly frontend family: type a command, press return, inspect rendered lines/events. They should remain architecturally compatible with the future ANSI frontend and any bitmap frontend by sharing semantic inputs, deterministic RNG, state snapshots, and view-neutral output/events.
- Full playthrough testing should use scripted scenarios over compiled game models: choose a start state, fixed random seed, semantic or terminal-like inputs, and expected checkpoints such as party state, flags/milestones, inventory, current mode, journal entries, and rendered text/event traces. This should evolve like the previous C/JSON5 scenario approach, but without baking test-only rules into runtime code.
- Small visible service beats are game semantics, not disposable UI polish. C/JSON5 `wait_input`/`ryl_pause` screens, pause delays, one-line confirmations such as inn drinks, and the random aggressive `(<verb> RETURN key)` prompt must be preserved in the authored game content plus reusable runtime hooks. The current 30 pause verbs live once in `RylweenGame.dsl.kts` under `prompts { pauseVerbs(...) }`; Kotlin runtime code may render and consume that model, but must not carry a duplicate current-game list.
- Hand-maintained current-game catalog source is gone; catalog handles are generated from declarations/usages. Runtime code should stay split by reusable state machine/pure state op. When a split exposes a current-game rule such as an item effect, spell cost, dungeon image, or monster behavior, the rule should move to `RylweenGame.dsl.kts` or a compiled model field rather than into another Kotlin singleton.
- `RylweenWorldMap.kt` is acceptable as a reusable runtime subsystem. `RylweenGame.dsl.kts` now provides a readable `worldMap(Maps.wilderness) { layer(MapLayers.terrain) { rows(...) } }` declaration so the game can boot without binary map loading, while `WorldMapGrid` remains the reusable runtime representation. A reference `data/map.dat` reader may stay only as migration/debug tooling, not the normal game-source path.
- Current-game script helpers are the right home for long, repeated, or mechanically noisy authored behavior when the helper is still game content. `priestTeleport(currentCaster)` lives in `src/game/kotlin/rylween/game/RylweenGameScripts.kt` because it combines current-game narration with reusable semantic ops (`ask.wildernessDirection`, `ask.digit`, `teleportParty`) and should not expand the generic DSL kernel.
- Dungeon migration should start with readable authored event/scenario tables, then wire the runtime to execute them. `dungeons { event(...) scenario(...) }` is the first pass: event names are semantic, weighted duplicate events preserve old probabilities without exposing old numeric ids, and generated cave depth remains owned by the entry effect (`3..16` wilderness cave discovery, `5..18` Earthquake) instead of being duplicated in the random-cave scenario table.
- Dungeon level event clearing is run-local, matching the old loop's local `lvl[]` table. Do not add saved per-level dungeon-cleared state unless a real game behavior needs it. Cross-session reward idempotence belongs in authored story/milestone state, for example special rooms guarded by `achieved(Milestones.fireShield)` or a story flag.
- Dungeon riddles/passwords use a reusable text-answer script op. The game file declares accepted strings and match/miss branches; terminal/ANSI/C/server adapters own how text is collected. This keeps the authoring shape close to the original game text without requiring the game author to implement input loops.
- The random dungeon found-object slot is authored through semantic item pools (`dungeonWeaponFinds`, `dungeonPotionGemFinds`, `dungeonTreasureFinds`, `magicItems`). These pools are the ported meaning of the reference item ranges, but the numeric ranges are not part of the game-authoring API.
- Current Kotlin runtime commands:
  `./gradlew installKotlinAnsi --console=plain` then `build/install/rylween-kotlin/bin/rylween --state fresh-replay --seed 1` for the executable-style ANSI shell,
  `./gradlew runKotlinGame --args="--state fresh-replay --seed 1"` for the line-oriented wilderness shell, and
  `./gradlew runKotlinTown --args="--town thord --hour 12 --seed 1"` for the town-only line shell.
  the direct ANSI launcher also supports `RYL_LOG=stderr`, `RYL_LOG=file:build/rylween.log`, and `RYL_LOG=tcp:9999,file:build/rylween.log`.
- Layering rule for future passes:
  1. `src/main/kotlin/rylween/dsl/RylweenDsl.kt` owns the generic authoring language, immutable models, and AST builders. It must not contain current-world story text, tavern-specific scoring rules, NPC quest prose, or one-off Rylween plot branches. Reusable default handle slugs live in `src/main/kotlin/rylween/dsl/RylweenDslDefaults.kt`; do not add imports from reusable packages back to `rylween.game`.
  2. Current-game handles are generated from `RylweenGame.dsl.kts`; new game content should move toward declarations there, and any slug or dense-id assignment should be derived by compiler/runtime tooling.
  3. `src/game/kotlin/rylween/game/RylweenGameScripts.kt` owns reusable script helpers that are specific to this Rylween game, such as traveller storytelling. These helpers can keep the main game source readable, but they are still game content, not kernel/runtime machinery.
  4. `src/game/kotlin/RylweenGame.dsl.kts` owns the current game's content and inline script bodies: towns, services, stories, rumours, missions, item effects, and branch text.
  5. `src/main/kotlin/rylween/engine` owns backend-neutral ports, trace runners, and boot facades; `src/main/kotlin/rylween/runtime` owns reusable interpreters, resumable sessions, and pure state operations; `src/main/kotlin/rylween/frontend/terminal` owns text-terminal rendering/input adapters. A C bridge, pure Kotlin runtime, server runtime, bitmap frontend, or native executable should all be able to implement or consume the same narrow input/render/RNG/persistence boundary.
  6. Tests may use small capability fixtures, but exact full-game parity tests should execute the compiled game model through `RylweenCompiledGame` where possible. Unit fixtures are not a second source of game content truth.
  7. Visible authoring should minimize raw slug strings. Names, display text, categories, prices, visuals, and behavior belong in declaration blocks; slugs and dense ids are compiler/runtime identities.
- Reusable current-game script helpers should stay as typed Kotlin extension
  functions while their only purpose is readability and local reuse. Do not add
  a string-keyed helper registry. Revisit typed script fragments when a runtime
  feature needs identity across the helper boundary: persisted continuation call
  frames, debugger/trace locations, compiled content bundle chunks, source maps,
  or C/server/native backends invoking reusable script bodies independently.
  The preferred future shape is generated typed handles such as
  `GameScripts.travellerStory` with `run(GameScripts.travellerStory)`, not
  author-maintained string ids.
- `Flags` aliases the existing `Story` flag catalog so scripts can keep the readable `Story.garth.bandits.accepted` form while A2 has a canonical `Flags.*` entry point.
- `Items.none` is an author-facing alias for the current id-0 `blank` item. The prototype-only `garth_cargo` item was not carried forward because it is not present in the current item shards.
- `Images.Scene.*` names the `blitgfx` map-window pictures semantically from `src/ryl.h` and `world.json5`'s `enums.pic` table, with comments preserving the reference `data/tiles/NN.ans` filenames. Avoid `tile01`-style handles in authoring code.
- `Images.Static.*` is reserved for full/static ANSI assets under `data/ansi`, separate from map-window scene pictures under `data/tiles`.
- `NativeOps.*` is a typed catalog in `RylweenDsl.kt`, but in `.kts` it must currently be represented as a class plus top-level value rather than a Kotlin `object`; script objects that call script-level helpers trigger a Kotlin script capture error.
- DSL implementation code should be idiomatic Kotlin: prefer expression-bodied helpers, `buildList`, `apply(...).build()`, and collection operations over temporary variables and imperative loops. Authored game blocks should stay readable as game notes.
- B7 ownership decision: `fireshield`, `bracelets`, `mastercrown`, `jazzyfoot`, and `silverkey` are persisted game-state milestones. Each current milestone also projects into the special-inventory view, but that is a property of the milestone, not a hard-coded rule that every win milestone is a special item.
- `silverkey == 2` in the reference C engine is not a Silver Key item state. It becomes a separate `Milestones.ydargVictory` completion marker so "has Silver Key" and "finished Castle Ydarg / won" are distinct states.
- The future engine should validate winning configurations at startup and save-load time: a party is in a winning configuration only when all content-defined required milestones are achieved and the configured completion marker is present.
- First-pass B1-B9 runtime-state shape now distinguishes `stateFixture(...)` from strict `runtimeState(...)`. Fixtures can stay compact; runtime snapshots are validated as fuller save-shaped records with explicit realm runtime fields, member counts, full inventory slots, attack-today records, declared NPC visit bits, and literal journal lines where needed.
- Equipment is modeled separately from slotted inventory in member `equipped { weapon; armor }` blocks. This mirrors current save coverage, where equipment is per member, while preserving the rule that milestone-projected special items are not slotted inventory.
- Reference special-field import mapping: `fireshield = 1`, `bracelets = 1`, `mastercrown = 1`, `jazzyfoot = 1`, and `silverkey = 1` map to their matching ownership milestones. Reference `silverkey = 2` maps to `Milestones.ydargVictory`, not to an upgraded Silver Key item state.
- Runtime save emission now has a first deterministic writer for strict `runtimeState(...)` snapshots. It emits pasteable Kotlin DSL with literal handle constructors such as `stateId("...")`, `partyId("...")`, and `item("...")`; saved flag values use `runtimeFlagState("flag.slug", "native_value")` so exact runtime data does not require generated authoring handles.
- First-pass combat-system shape is `combat { standardBattle { ... } }`. It names the reusable battle framework from the game author's point of view: opening flee rules, encounter banners, player actions, monster-round policy, and the reward bundle to apply. The actual XP/gold/loot declaration now lives in `rewards { reward(Rewards.standardBattle) { ... } }`, so the reusable battle loop can point at a shared reward bundle instead of embedding one-off reward rules.
- Loot tables use semantic item pools such as `ItemPools.battleSpoils` and `ItemPools.magicItems`. Numeric item-id ranges from the C/JSON5 reference are porting audit material only; they should not live in authoring or runtime formats. The implementation model also records the narrow native services the standard battle framework needs: keyboard input, rendering, RNG, combat context storage, inventory storage, party-state mutation, and text formatting.
- Current battle loot pool membership is carried forward as semantic item lists, not as persisted/generated numeric ranges. Authors should see pools such as `ItemPools.battleSpoils` and `ItemPools.magicItems`; migration audits can compare against old C/JSON5 outside the runtime model if needed.
- First-pass dialogue shape is `dialogue { npc(Npcs.garth) { whenState(...) { ... } } }`. One-off NPC interactions such as Garth's merchant reward can keep their text, reward, prompt/drop handling, and flag transition inline in the branch; reusable screens/text remain a separate future text-asset concern and do not force a scene id for every dialogue branch.
- First-pass speak dispatcher shape is `speak { standardConversation { ... } }`. It declares the reusable NPC selection, visited-bit recognition, branch-selection policy, C/Q/R/B top-level menu, L/M/P/N question submenu, well/weather/health chitchat roll, once-per-conversation rumour behavior, and the narrow native services the reusable runner needs. Game authors still provide per-NPC branch content in `dialogue { ... }` rather than writing the conversation loop.
- Reusable speak text now has its own `speak { textTables { ... } }` shape for shared screens, chitchat prompt/reply tables, question reply tables, and town rumour rolls. This is intentionally separate from inline mission dialogue: table text is reusable system content, while one-off NPC quest branches can remain local structured blocks.
- `SpeakSession` is the first reusable runtime runner for the standard conversation shape. It selects NPCs in the requested building at the current town from realized runtime placements, falls back to fixed character declarations when a fixture has not realized NPC state, marks per-party visited bits, automatically runs the first matching `dialogue { npc(...) { whenState(...) { ... } } }` branch before showing the reusable menu, runs C/Q/R/B plus L/M/P/N menus, resolves chitchat and question replies by NPC job, applies once-per-conversation rumour fallback, and filters standard town rumours with `excludeWhen(...)`. Dialogue branches execute through the same stateful script runner as town services, so rewards, flags, milestones, waits, and drop prompts can resume without replaying side effects. The service bridge can now run or resume `Op.Speak` as a nested session, and service scripts can call `tellRumour()` to draw from the same standard rumour table. Runtime text rendering resolves role names, realized/fixed role locations, town names, and deterministic fuzzy town coordinates. Explicit branch leave-vs-menu control and richer rumour selection policy are still pending.
- `screen(...)` is a script action; `line(...)` and `lines(...)` are reusable text-building extensions. `TextLineSink` is the shared receiver for structured text in screens, menus, rumours, shop prompts, random-item offers, dungeon entrance prompts, and other future text-bearing blocks, so helper-specific text builders should expose domain parts such as `itemName` or `price` without reimplementing their own line DSL. Reusable text tables use structured `lines { line(...) }` blocks when the content benefits from one line per source row, and `lines("...", "...")` as a compact equivalent for short exact reference rows. One-off literal `screen("...", "...")` remains authoring sugar that records the same structured text shape.
- Dungeon entry is modeled as a generic `enterDungeon(Dungeons.*, levels = n)` script operation that a Kotlin/server/C backend can interpret as a runtime handoff. The current game's repeated E/L entrance convention lives in `src/game/kotlin/rylween/game/RylweenGameScripts.kt` as `dungeonEntrance(...)`; that helper is game-script reuse, not DSL kernel behavior. Dungeon handoff now starts a `DungeonSession` that builds a fresh per-run level event table, and mission-triggered dungeon handoffs carry a post-dungeon script continuation so the original `clearMapWindow`/`renderMap` tail operations resume after leaving the dungeon. Those continuations now persist in strict runtime save snapshots when the active dungeon is saved.
- Mission journal updates are now first-class script actions via `journal { ... }`. Authored scripts should use typed text refs such as `location(Locations.nolrimCrypt).coordinates` instead of placeholder strings like `{dx5}` or generated numeric binding tables. The current-game `readScreen(...)` helper means "show this screen, wait for input, clear text" and belongs in `RylweenGameScripts.kt` because it is a Rylween mission-script convention, not a generic kernel rule.
- Character references in reusable text should point at semantic roles, not placeholder strings such as `{name15}` or `{loc15}`. The terminal renderer now resolves `character(Roles.crownSeer).name` and `.location` from compiled character declarations plus realized NPC placement state. The Kotlin runtime can now populate that state from fixed, random town-or-wilderness, and random-wilderness character declarations. Rumour handles are semantic and generated handles do not carry old switch positions or selection numbers. Rumours can also declare story-state exclusions, such as hiding the bandit-merchant rumour once `Story.garth.bandits` is recovered or closed.
- Town declarations use generated `Towns.*` handles, e.g. `town(Towns.ollk) { name = "Ollk"; ... }`. The source scanner derives those handles from declaration usage, so authors do not maintain a handwritten catalog; slugs and dense ids remain compiler/runtime identities.
- Town service availability and reusable building behavior are separate. `towns { ... services { inn(); tavern() } }` says which services a town offers; `townSystem { menu { ... } buildingServices { ... } }` says how the reusable town-entry loop maps keys to services, scenes, and opening hours. Current service hours are exact to `data/world.json5`: smith and merchant `6..18`, tavern `6..1`, inn/church always open.
- Building service scenes can migrate one service at a time. Church is now an inline typed script for its H/E/R/L menu, heal, energize, and raise branches; inn is an inline typed script for supper, drinks, lodging, rest, and guest chitchat; tavern is an inline typed script for opening hours, the scruffy random-item dealer, speak, storytelling, rumours, and drink orders; smith and merchant now use the reusable `shopService(...)` model instead of the native town-service trampoline.
- Author-facing conditionals should use `whenever(...) { ... } otherwise { ... }`. The older `when_(...)` spelling remains only as a compatibility alias because Kotlin reserves `when`.
- Authoring should not create lookup handles for one-off branch text. Reusable service prompts such as church intro/menu/who can stay as `TextId` handles, but local results such as "corpse", "not spellcaster", "raised", and payment prompts should be inline structured text in the owning script unless they are reused by another action or screen.
- Choice flows should use reusable helpers once their shape is understood. `choice.payNoThanks(...)` is the first helper; `choice.yesNo(...)` is the standard authored Y/N branch helper backed by `StandardYesNoKeys`; `choice.member(...)` records a typed member picker with filters such as `Any`, `AliveOnly`, `DeadOnly`, and `SpellcasterOnly`; and `choice.inventorySlot(...)` records the A-through-slot-limit inventory picker shape for future item/equipment scripts. Member prompts now separate semantic intent from accepted input: `filter = AliveOnly` says what the prompt is for, while `accepting = Any` lets authored branches keep original wrong-status text such as the church corpse rejection or tavern dead-story joke.
- Script refs such as `member.hp`, `member.dead`, `party.gold`, and `localInt(...)` are AST refs, not ownership of an output list. `ScriptContext` keeps the active recording target, so a member selected outside a nested menu can be safely mutated inside `paid = { ... }` or another nested branch without prematurely recording that mutation before the menu.
- The inn pass adds `choice.yesNo(...)` and `choice.takeOrLeave(...)` as reusable menu shapes, plus `forEachMember`, `healLivingMembers`, and clamped stat mutation for party-wide recovery effects.
- The tavern pass adds reusable typed support for structured inline prompts (`structuredText { ... }`), class checks such as `member.isClass(PcClass.Bard)`, member charisma mutation, realm values such as `realm.partyMemberMax`, and hidden integer locals via `localInt(...)`. Use `localInt` for authored counters, prices, rolls, and temporary script values; keep raw `let("name", ...)` as an implementation/import escape hatch where a backend or generated script really needs a stable symbolic name.
- The scruffy dealer is now expressed as `choice.randomItemOffer(ItemPools.tavernScruffySpecials) { ... }`. The item pool is semantic and ordered in the authored `items { ... }` declaration, matching the reference scruffy pool without exposing numeric item ids in authoring. `RandomItemOfferSession` owns item name/price lookup from compiled `GameModel.itemCatalog`, B/N purchase input, gold check, first-free inventory placement, prompt/drop overflow, and resumable continuation state. The same reusable offer session can run free item offers for dungeon finds via a price override while still keeping item selection semantic. `StatefulScriptBridge` now performs the seeded pool draw before starting the session and stores the advanced RNG state in `RealmState`, while `rngState = null` fixtures keep deterministic first-entry behavior.
- Tavern traveller storytelling is current-game content, not DSL kernel functionality. Its exact text, scoring thresholds, Bard/charisma bonuses, liar penalty, `waitInput`, and charisma adjustments now live in `src/game/kotlin/rylween/game/RylweenGameScripts.kt` as `travellerStory()`, while the tavern script calls that helper inline. Keep this as a Kotlin game-script helper until stable fragment identity is needed for persisted continuations, debugger traces, source maps, or compiled bundles. If that happens, promote it to a typed generated script-fragment handle rather than a string id. If the shape becomes reusable across Rylween worlds, introduce a game-script-library mechanism or a parameterized generic contest helper; do not put one world's story body in `RylweenDsl.kt`.
- Random-item offers and normal shop inventory purchases reuse the same reward overflow policy vocabulary as mission rewards, and default to the reusable drop-something-and-place flow. Exact reference "can't carry it" rejection remains an explicit opt-out for byte-parity fixtures.
- Smith and merchant are modeled with `shopService(...)`: authoring declares the service image, local text, optional chance interlude, buy catalogs, resource buys, sell policy, and bargain policy; Kotlin/runtime owns the shop loop, menu dispatch, item-cost lookup, dynamic level-based prices, first-free-slot placement, and prompt/drop overflow.
- The first runnable shop runtime is `ShopServiceInterpreter`, a pure Kotlin transaction interpreter over immutable `PartyState`. It executes semantic buy/sell requests from a `ShopServiceModel`, including item-cost lookup, level-based merchant food/potion prices, torch/food caps, first-free-slot placement, prompt/drop overflow, merchant sell policy, and smith haggle math. It stays independent of concrete terminal, C, server, or native UI implementations.
- `ShopServiceSession` wraps the transaction interpreter in a backend-neutral prompt state machine: intro, main menu, buy item, quantity, pay/decline, drop-slot overflow, sell slot, sale quote, sale accept/decline, and leave. `ShopServiceTraceRunner` consumes scripted `ShopServiceInput` values for tests/traces; `ShopServiceTerminalAdapter` consumes line-oriented `TerminalPort` input over the same session contract. C and server adapters should translate their own input events into this same semantic session shape.
- Terminal-style adapters must not silently turn invalid or missing input into gameplay choices such as "no" or "leave". Their `runUntilBlocked(...)` result returns the unchanged unfinished session with `TerminalRunStatus.Waiting`, optionally carrying a `TerminalInputRejection`; an outer demo runner may reprompt, quit, or surface the rejection depending on frontend policy.
- `RuntimeSessionState` is the first runtime wrapper for live execution. It owns the current `RealmState`, parties, and realized NPC placements, and exposes `activeParty` from `realm.activeParty`; town, shop, equipment, service runners, and text rendering now update or consume this wrapper rather than carrying only an isolated party and an hour integer.
- `realizeNpcPlacements(...)` is the first Kotlin cold-start NPC realizer. `fixed(...)` placements are copied into runtime state, `randomPlacement()` distributes NPCs across wilderness plus declared towns with capacity balancing and random inn/tavern assignment, and `randomWilderness()` generates real wilderness hut coordinates instead of preserving reference `0,0` placeholders. Exact runtime starts consume and persist `rngState`; authored fixtures with `rngState = null` use a deterministic fixture seed and restore `null` afterward. The wilderness point policy is pluggable; once map/terrain content is in the DSL it should receive the passable non-settlement point set and apply the generated hut tile mutation.
- `RylweenKotlinEngine` is the first small pure-Kotlin boot facade over compiled content. It can create an initial runtime from a full `StateModel` without dropping party position, realize NPCs, boot `TownSession` and `WildernessSession`, and expose matching speak/text runtime content. `./gradlew runKotlinTown --args="--town thord --hour 12"` runs a town-only terminal shell; `./gradlew runKotlinGame --args="--state fresh-replay"` runs the first wilderness shell over the same compiled game, reference map loader, NPC realization, town-session adapter, original wilderness command bindings, Q quaff potion adapter, W/R/T/P equipment adapters, and seeded encounter entry into `BattleSession`. Both shells leave game rules in the shared runtime/session layer. C, server, terminal, and future native runtimes should feed or consume the same state/session contracts rather than forking game rules.
- `WorldMapGrid` is the first Kotlin world-map runtime object. It now normally receives authored map rows from the compiled game model while preserving the same world-coordinate convention as the 1992 C implementation: source row 1 is world `y = 1`, while world row 0 stays empty. It exposes passability, town/hut/castle settlement checks, NPC-placement point selection, terminal viewport rendering, and generated NPC hut projection after `realizeNpcPlacements(...)`. The runtime contract stays backend-neutral; byte-map loading is migration support only.
- `wilderness { events { ... } terrainTransitions { ... } }` is the first authored wilderness event table in the game DSL. It carries the current 3% cave / 91% quiet / encounter remainder split, 50/50 weak-vs-wild encounter tier split, random cave depth band 3..16, cave entry cost 10, cave prompt/no-moves text, and the current terrain-transition narration texts. The runtime keeps terminal/server/native adapters out of the rules: a successful move dispatches through `WildernessSession`, which can produce `CaveFound`, `DungeonHandoff`, a nested `BattleSession`, or terrain-message steps. Authored fixtures with `rngState = null` intentionally stay quiet; exact/runtime snapshots with seeds exercise random wilderness events. `freshReplay` now carries `rngState = 1L` so the runnable wilderness shell reaches seeded terrain/cave/encounter dispatch by default, while tests can still override to `null` for quiet fixture behavior.
- `TownSession` is the first top-level town runtime shell. It filters the C/S/I/M/T/L menu to services available in the current town, checks reusable service hours from `runtime.realm.hour`, routes scenes containing `Op.ShopService` into `ShopServiceSession`, resolves other service scenes into `NativeService` handoff steps, and runs `TownLeaveEquipmentSession` before final town exit. `TownSessionTerminalAdapter` composes the existing shop and equipment terminal adapters, so nested invalid input still returns a waiting session instead of defaulting a choice.
- `ScriptBridge` is the resumable script-execution seam for service scenes, speak branches, mission resumes, priest effects, and inventory item-use. `StatefulScriptBridge` consumes terminal menu/member/wait/reward-drop input, executes the current loop/if/switch/local-int/for-each-member subset, and mutates party/member/story state such as gold, hit points, mana, charisma, death state, member count, rested hours, flags, and milestones. It can pause a script at `encounter(...)`, hand the outer adapter a `BattleSession`, and resume the script after the battle finishes. `stop(...)` inside a loop now stops the current branch/iteration and returns to the loop, while `breakLoop(...)` exits the service loop. Blocked scripts return a `ScriptContinuation` containing runtime state, locals, unknown realm scalar storage, nested sessions, and a cursor stack made of script block references plus next-op indexes, so a script can resume without replaying prior side effects. Known realm integer fields such as hour, minute, day, banishment, last-played day, and party-member max now mutate the persisted runtime realm; trace-only child runners and scalar/member binding tables are split out of the stateful bridge file.
- Church H/E/R/L is covered as a runnable authored-service fixture for healing, broke payment, energizing, raising, and stop-then-return-to-menu behavior. Inn supper, drinks, lodging, and chitchat are covered through the top-level town terminal adapter, including local service cost, pay/broke branches, `waitInput`, living-member clamped healing, rested-hours mutation, dead-member exclusion, persisted `realm.hour` mutation, nested speak sessions, and visited-bit updates. Tavern speak/question, standard-rumour, scruffy random-item purchase, drink order, deterministic traveller-story loss, seeded traveller-story score, seeded scruffy item draw, seeded rumour selection, role/town-bound rumour rendering, and rumour quiet-limit branches now have first runtime coverage, including drop-slot resume without replaying the purchase choice. `RylweenCompiledGame` lets tests run against the authored `nextRylween` model; current compiled-game coverage enters the real Thord church, merchant torch purchase, smith sale, tavern scruffy-offer/order/story/rumour-limit paths, seeded tavern scruffy/story paths, compiled role/town rumour rendering, realized-NPC speak lookup, direct Garth reward dialogue branch, Garth reward through normal speak selection, and weak/wild battle sessions with attacks, spells, item use, monster rounds, victory, and defeat. Unsupported service concepts such as full traveller-story score distribution coverage, explicit dialogue branch leave-vs-menu control, and broader realm/session transitions are still traced or held as future bridge/interpreter work.
- `ReferenceRylweenRandom` mirrors the C `ryl_random` xorshift64* stream and treats `realm.rngState` as the current stream state for exact-save continuation. Runtime fixtures with `rngState = null` intentionally keep deterministic lower-bound/first-entry selection for local choices and keep random wilderness event dispatch quiet, so authored examples can stay stable until they are promoted to exact snapshots.
- Merchant gear/resource effects are explicit but not overstated. `Items.torch` purchases mutate the torch resource and cap at 80; a reusable `consumeTorch()` action now decrements one torch or reports `NoTorches`, and the dungeon transition runner should call it after deciding that descent actually happens. `Items.ration` purchases mutate food and cap at `150 * (donkeys + 1)`. `Items.donkey` remains an inventory item whose carried count expands food capacity; dropping or replacing a donkey clamps food to the remaining capacity, matching reference `drop()` behavior. `Items.tent` and `Items.blanket` expose reusable camp-gear protection metadata for the future rest runner: tent selects the tent backdrop and protects both low and very-low temperature bands, while blanket protects only the low band. Tent is not consumed.
- Equipment ready/off now has pure Kotlin state mutations, prompt command metadata, and reusable prompt sessions. `equipFromInventory(memberNumber, slot, kind)` swaps a valid inventory weapon/armor with the member's equipped item, `unequipToInventory(memberNumber, kind)` moves equipped gear to the first free slot, and `EquipmentCommand.ReadyWeapon/ReadyArmor/RemoveWeapon/RemoveArmor` exposes the member and inventory filters a frontend should ask for. `EquipmentPromptSession` covers direct ready/off actions; `TownLeaveEquipmentSession` asks the reference leave-town weapon prompt, then armor prompt, only when the party carries that equipment kind and a living member lacks it. Trace and terminal-style adapters both feed semantic inputs into those sessions. `InventoryItemUseSession` lists all slotted inventory items; selected weapons and armor enter the ready flow instead of requiring fake item-use effects. `BattleSession` consumes generated weapon damage and armor class from readied equipment for player attacks and monster melee.
- When porting more scripts, look for the old `let("slug", ...)` and `bind("slug", ...)` shapes. Numeric locals should usually become hidden `localInt(...)` refs; repeated string placeholders should become typed text parts or reusable text-table binding metadata rather than ad hoc author-maintained names.
- Inline text should carry typed values directly, e.g. `line("- That'll be ", price)` or `line(pronoun(member, PronounCase.Object), ".")`, instead of embedding `{cost}` or `{pro_obj}` placeholders in local string literals. Placeholder strings are still acceptable for imported reference `TextId` tables until those records get typed binding metadata.
- Service-local conversations should be typed script ops, not raw native trampolines. Inn guest chitchat now records `speak(Buildings.tavern)` as a reusable conversation action that a Kotlin runtime, C bridge, server, or native executable can implement through the same engine port.
- `breakLoop(...)` and `stop(...)` are explicit script-control signals. Menu, switch, and if bodies propagate those signals to their surrounding script runner; validation rejects `breakLoop(...)` outside a loop.
- Reference `main` C code confirms the original NPC layout: slots 1-24 are generated random people, slot 33 is Marcus the fixed Hermit at wilderness `48,7`, slots 34-35 are fixed tavern women, and slots 36-39 are Alex/Galdon/Kosch/Ufarsin as merchant wilderness records with implicit `0,0`. The DSL should not expose implicit wilderness-origin placement as an authoring concept; characters should be declared as `randomPlacement()`, `randomWilderness()`, or `fixed(Wilderness.xy(...))`.
- `Items.*` handles no longer carry reference/source ids. Item pools are semantic item lists, and the new runtime/bundle/save formats should not depend on reference numbering.
- Confirmed design decision: item ids should be compiler/runtime identities only, if needed at all. Game authors should describe the item in an `items { ... }` declaration and refer to a typed symbol afterward; raw `item("slug")` / `itemPool("slug", ...)` calls are scaffolding/implementation escape hatches, not the target authoring style.
- Item records now describe what an item is: kind, display name, cost, storage behavior, and equipment combat values. They do not describe what using the item does.
- Potion effects are authored as ordinary `itemUse(...)` scripts. Red Potion asks `Who?`, filters living members, consumes the active item, and heals HP; Blue Potion asks `Who?`, filters living members, consumes the active item, and applies poison damage/death state; Amber Potion asks `Who?`, filters spellcasters with alive acceptance, consumes the active item, and restores mana. A future Purple Potion should be added by declaring the item in `items { ... }` and adding an `itemUse(...) { inventory(); effect { ... } }` body, not by adding Kotlin enum cases or C/native branches.
- Item use is wrapped in explicit metadata: usable contexts, consume policy, and an effect body. `InventoryItemUseSession` runs non-combat `itemUse(... Inventory ...)` scripts through the same resumable script machinery as service/mission flows, including `consumeActiveItem()` for the selected slot. The current battle magic items use reusable consume policies: most consume before use, while the resurrection gem is script-controlled so a failed living-member target can retry without consuming the item.
- Text/screens have two paths: reusable records can stay as `TextId` handles, while one-off dialogue can be written inline as structured text blocks inside the owning dialogue/mission branch. Do not mint a global text or scene id just to hold a one-off NPC response. `screen` should stay the action that displays a `TextBlock`; `line`/`lines` should stay the shared construction vocabulary available to reusable text receivers. The Borin gift scene now uses inline text for its local narration.
- Reward bundles and scripted rewards now name item-placement semantics separately from loot membership. Standard battle uses first-free inventory slot placement and exact reference behavior `SkipIfInventoryFull`; scripted rewards use the same typed policy shape for overflow prompts without custom inventory loops in authored content. `RewardGrantSession` applies gold and slotted items, prompts for drop-slot overflow, resumes without replaying already-applied gold, and leaves special inventory projections to explicit milestone state such as `achieve(Milestones.silverKey)`. The Borin gift reward now grants gold and semantic gem handles without numeric ids.
- Standard battle framework rules now carry the player/monster combat tunables from `data/world.json5`: attack hit bonus `1`, attack damage spread max `4`, confusion divisor `3`, attack HP divisor `12`, battle-cry weapon-skill penalty `4`, defend weapon-skill penalty `2`, defend rest threshold `5`, monster-melee hit bonus `1`, damage divisor `6`, damage spread max `9`, and damage minimum `1`. These are reusable Kotlin DSL/runtime rules, not game-specific custom battle loops.
- `BattleSession` is the reusable combat runtime slice. It is backend-neutral: terminal, C, server, and future native adapters feed semantic inputs and render `TextBlock`s, while the session owns encounter allocation from authored monster tiers, seeded foe count and HP rolls, runtime mode, flee success/failure, mandatory-run denial, per-member action prompts, attack/defend/battle cry, mage spell choice and cost payment, combat item-use choice/use, monster melee/spell turns, party defeat, victory rewards, loot, and level-up application/narration. The game declaration keeps game-specific behavior author-facing: `standardBattle { ... }` declares reusable rules and prompt/menu text, `formulas { ... }` declares encounter/reward/level formulas, `monsters { ... }` declares this game's encounter catalog, and `spell(...)` / `itemUse(...)` blocks declare this game's effects and inline battle text. Combat script text now supports typed party/monster names and pronouns directly, so authored battle prose does not need placeholder strings.

Open design questions:

- Decide whether final generated catalogs should mirror JSON5 shard names exactly or use more author-facing groups such as `Images.Static` / `Images.Scene`.
- Decide scene slug normalization before broad script porting. The game source starts grouping scene handles by domain, but the migration needs one canonical mapping from reference script ids to typed `Scenes.*`.

### B. Exact runtime state and savegames

- [x] B1. Realm/session state. DSL delta: `state { realm { day/hour/minute, rng, currentMode, currentMap } }`. Native decision: only active frame pointers and transient loop state remain native. Current game source status: first-pass `RealmState` covers day/hour/minute, current RNG stream state, active party, current map/mode/town, banishment, last-played day, and party-member max; `RuntimeSessionState` now carries live realm, parties, realized NPC placements, and realized quest locations for town/wilderness execution and text rendering, and advances `rngState` for seeded runtime draws, including cold-start NPC placement.
- [x] B2. Party identity and position. DSL delta: `party(Parties.main) { position = Wilderness.xy(...); facing = ... }`. Native decision: coordinate storage and bounds checks remain native. Current game source status: party id/player/name, world position, and optional facing are modeled.
- [x] B3. Party resources and counters. DSL delta: `resources { gold, food, fatigue, timeOfDay, upkeepDue }`. Native decision: primitive numeric mutation only. Current game source status: resources cover gold, torches, food, time left, and rested hours; counters cover member count, kills, mail/attack notices, and attack-today records.
- [x] B4. Party members. DSL delta: `member(1) { name; race; class; weaponSkill; charisma; wisdom; health; hitPoints; mana }`. Native decision: field storage and target selection only. Current game source status: member records include identity, class/race/gender, level, stats, HP/mana, equipment, experience, and dead/alive state.
- [x] B5. Derived/depletable stat rules. DSL delta: validation for `hitPoints.max == health` and `mana.max == wisdom`, plus rules that modify max/current values. Native decision: no hidden C recalculation except typed helper ops. Current game source status: validators enforce HP and mana max consistency.
- [x] B6. Inventory slots and equipment. DSL delta: `inventory { slot(1).item(...) }` plus per-member `equipped { weapon = ...; armor = ... }`. Native decision: low-level insert/remove/first-free-slot helpers. Current game source status: slotted inventory is separate from per-member equipment because current save coverage stores weapon/armor per member; runtime snapshots must list slots `0..16`.
- [x] B7. Reference special fields. DSL delta: `gameProgress { milestone(...) { showsAs(...); requiredToWin(); sourcePartyField(...) } winningConfiguration { ... } }` plus `party { milestones { achieved(...) } }`. Native decision: old positional fields are migration-only; native state should store dense milestone ids, derive special-inventory display from content metadata, and treat reference `silverkey == 2` as a separate completion marker. Current game source status: near-winning and winning fixtures exercise required milestones, `Milestones.ydargVictory`, special inventory projection, and generic win checking. Runtime status: `achieve(...)` now mutates party milestones in stateful scripts.
- [x] B8. Journal and visited records. DSL delta: `journal { entry(...) }` and `visited(Npcs...)`. Native decision: persistent string/id list storage. Current game source status: authored states can use compact `visited(...)` and text-id journal entries; runtime snapshots can list true/false NPC visit bits and literal journal lines.
- [ ] B9. Savegame snapshots. DSL delta: exact fully expanded `state {}` output with no hidden defaults or helper loops. Native decision: filesystem write/read for new snapshots; old-save import is not required unless explicitly requested. Current game source status: `runtimeState(...)` validation discipline exists, `renderRuntimeStateSnapshot(...)` emits deterministic pasteable Kotlin DSL for strict runtime snapshots, `parseRuntimeStateSnapshot(...)` reads the strict writer-emitted subset back into runtime state, and `FileRuntimeSavePort` gives ANSI/line frontends first-pass file-backed `--save`/`--load` plus named `--save-slot`/`--load-slot` over active wilderness snapshots and exact Ydarg/dungeon mode snapshots; new file saves carry snapshot-format/content-hash metadata and load validation rejects unsupported save formats or mismatched compiled content. Remaining work is deciding which additional active modes deserve exact injectable snapshots versus explicit unsupported-save rejection.

B1-B9 first-pass notes:

- `stateFixture(...)` is the authoring/test fixture form. It may stay compact and ergonomic.
- `runtimeState(...)` is the future save snapshot subset. Validation currently requires explicit RNG seed, member count, full inventory slots `0..16`, attack-today records for every party in the state, and explicit visit bits for every NPC declared in that state.
- `renderRuntimeStateSnapshot(...)` and `parseRuntimeStateSnapshot(...)` are the first writer/reader pair for that subset. `RuntimeSavePort`/`FileRuntimeSavePort` add the first filesystem policy for the Kotlin frontends: one explicit snapshot target path, strict parse on load, and no old-save importer.
- Old-save reading is no longer part of the near-term DSL design. Reference fields are still documented so current data can be mapped correctly, but the next save/load work should target exact new DSL snapshots first.
- Current C save coverage audited for this pass: realm header (`last_played_day`, party-member max, current session fields from snapshots), locations, NPC placement, party identity, flags, special fields, `pl`, position, resources, inventory, member records/equipment, `attack_today`, `waiting_mail`, `attacked`, `kills`, `visited_npcs`, and `journal`.

### C. World maps, terrain, visuals, and layout

- [ ] C1. World metadata and limits. DSL delta: `meta { ... }` and `limits { ... }`. Native decision: allocation caps and defensive bounds checks.
- [ ] C2. Map dimensions and storage. DSL delta: `worldMap(Maps.wilderness) { size = cells(...); storageCapacity = ... }`. Native decision: map array allocation and coordinate bounds. Current status: the compiled game now supplies the wilderness map through authored rows while `WorldMapGrid` preserves the reference coordinate convention; explicit size/capacity metadata can wait until a second map or generated bundle needs it.
- [x] C3. Map cells and layers. DSL delta: typed terrain rows/layers instead of raw `map.dat` bytes. Native decision: optional reference map-byte loader during migration. Current status: `RylweenGame.dsl.kts` authors the wilderness terrain layer with `worldMap(Maps.wilderness) { layer(MapLayers.terrain) { rows(...) } }`, and Kotlin launchers default to that compiled map.
- [ ] C4. Terrain semantics. DSL delta: `terrain(Terrain.forest) { movement; passability; tags; events }`. Native decision: fast predicate tables generated from terrain ids. Current game source status: terrain-transition narration is authored against semantic terrain handles. Generated terrain handles now distinguish `Terrain.foothills` for map byte `f` from `Terrain.forest` for map byte `g`; `Terrain.grass` remains an alias for migration compatibility.
- [ ] C5. Terrain visuals. DSL delta: terminal glyph/color and future bitmap tile styles live under terrain render styles. Native decision: renderer consumes a style id, not terrain game rules.
- [ ] C6. Visual assets. DSL delta: `visuals { images { image(..., kind = Ansi|Bitmap) } palettes { ... } }`. Native decision: asset decoding and backend texture/glyph storage.
- [ ] C7. Screen regions and layouts. DSL delta: `layouts { region(ScreenRegion.map/text/status, ...) }`. Native decision: cursor/viewport implementation only.
- [x] C8. Static screens and documents. DSL delta: title, help, manual, score display, and ANSI/static screens declared as assets/documents. Native decision: file/asset rendering backend. Current status: `visuals { staticScreens { ... } }` declares frame/title/help/manual/score/vscore assets, help reuses authored command-help pages, the app packages the document resources, and both Kotlin frontends expose `--screen title|help|manual|score|vscore` via a shared terminal static-screen renderer. Score/vscore are still static packaged display resources; mutable score-file policy remains open under G5.

### D. Text, dialogue, NPCs, and story state

- [ ] D1. Text records and local bindings. DSL delta: `texts { text(Texts.foo) { bind(LocalText.partyLeader, ...) } }`. Native decision: wrapping and final placeholder substitution. Current game source status: inline structured text can now include typed integer and string values directly, so local text should use `line("- That'll be ", price)` rather than `{cost}` placeholders; script-local integer state should use `localInt(...)` instead of duplicated Kotlin variable plus string slug names. `TextLineSink` now makes `line`/`lines` shared extensions across structured screens, menus, rumours, shop prompts, and random-item offer text instead of each helper inventing its own line builder.
- [ ] D2. Pronouns and name bindings. DSL delta: typed binding names and pronoun cases instead of symbolic strings. Native decision: lookup by member/npc id. Current game source status: inline church branch text now uses typed `pronoun(member, PronounCase.*)` parts rather than `{pro_sub}` / `{pro_obj}` string placeholders.
- [ ] D3. NPC definitions and roles. DSL delta: `characters { character(Npcs.garth) { name; job; fixed(...); role(...) } }`. Native decision: role lookup and placement storage. Current game source status: all 39 reference NPC names/jobs are declared in the game source, including the 17 current semantic story roles used by text/rumours.
- [ ] D4. NPC placement and visited state. DSL delta: content placement plus mutable `state { npcs { at(...) } visited(...) }`. Native decision: runtime coordinates and visited bitsets. Current game source status: character content distinguishes `randomPlacement()`, `randomWilderness()`, and `fixed(...)`; `realizeNpcPlacements(...)` can populate runtime NPC state from those declarations; `SpeakSession` and text rendering consume realized placements; visited bits remain per party. Reference C's trailing `0,0` wilderness merchant slots are documented as an import/migration concern, not an author-facing placement mode. Runtime status: authored map rows now provide passable non-settlement wilderness points to NPC realization and project realized wilderness NPCs back into the runtime map as hut terrain. Remaining work is the generated NPC catalog and broader NPC placement/dialogue parity coverage.
- [ ] D5. Flags and story progress. DSL delta: typed flag families with explicit states and setters. Native decision: dense flag storage and save serialization. Runtime status: `mark(...)` now mutates party flag state in `StatefulScriptBridge`; transition graph validation remains pending.
- [ ] D6. Missions and triggers. DSL delta: `mission(...) { randomStep(percent = ...); at(...); onTerrain(...); requires(...); runs(...) }`. Native decision: trigger polling and typed op dispatch. Current runtime status: `WildernessSession` polls authored random-step, fixed-position, realized-location, and terrain missions after successful seeded wilderness movement, evaluates typed requirements against party state, consumes/persists RNG only for active random mission rolls, and hands triggered action scenes to the same resumable script bridge used by town services. Authored mission content now covers 17 current wilderness missions: bandits, troll tooth, Thun gem altar, skullweed, Borin meet/gift, Kobold/Ogre/Jazzy/Hobgoblin combat chains, and seven dungeon-entry prompts/handoffs; mission-triggered dungeon handoffs now resume their owning script after the dungeon session exits. Full mission-level resume/completion tests cover the Kobold village, Ogre cave, Foot of Jazzy, and Hobgoblin caravan scenes with deterministic combat fixtures. Remaining work is trigger/catalog cleanup that falls out of the dungeon/runtime parity passes.
- [ ] D7. Dialogue and chitchat. DSL delta: `speak { standardConversation { ... } textTables { ... } questionAction(...) { ... } }` plus `dialogue { npc(Npcs.garth) { whenState(...) { say(...) } } }`. Native decision: NPC lookup/visited bits, input, rendering, RNG, and text formatting stay narrow services behind a reusable Kotlin/runtime conversation runner. Current game source status: all role-bearing quest NPCs now have authored dialogue branches, including Garth's merchant offer and grateful reward with exact reference text, reward, prompt/drop behavior, and flag transitions. The standard conversation shape pins C/Q/R/B and L/M/P/N menu structure, first-matching dialogue branch dispatch, visited recognition, well/weather/health chitchat, once-per-conversation rumour fallback, no-NPC/no-rumour fallback screens, and representative chitchat/question reply tables copied from `data/world/texts/speak.json5`; bargirl lodging is an authored lodging question action. Runtime status: `SpeakSession` executes realized/fixed-building/wilderness-location NPC selection, visited-bit mutation, automatic first-matching dialogue branch scripts, authored question-action scripts, seeded chitchat subject selection, chitchat replies, question replies, seeded rumour selection, role/town text rendering, and nested town-service continuation for `Op.Speak`; Garth's recovered branch is covered both directly and through normal speak selection, and bargirl lodging is covered through normal speak selection. Explicit branch leave-vs-menu control and full branch parity coverage remain pending.
- [ ] D8. Rumours. DSL delta: rumour tables with anonymous inline entries by default, semantic `Rumours.*` only for externally referenced/gated entries, role-based character text references, town-location text references, and optional `excludeWhen(...)` story-state filters. Native decision: RNG selection only plus filtering against typed party state; old C switch positions stay out of authoring and generated runtime metadata. Current game source status: `RumourTables.standardTown` exists under `speak { textTables { ... } }` with exact entries from `data/world/texts/towne.json5`; authoring uses `character(Roles.crownSeer).name/location`, `town(Towns.murn).near(radius = 5)`, and anonymous `rumour { ... }` rows rather than numeric selection ids or `{name15}` placeholders, and the bandit-merchant rumour is excluded after the cargo is recovered or the reward is closed. Runtime status: standard rumours now filter `excludeWhen(...)` against party flags and can serve seeded selection from the semantically available list for both `SpeakSession` rumour choices and `tellRumour()` in service scripts; terminal rendering resolves role names, realized/fixed role locations, town names, and deterministic fuzzy town coordinates for authored rumour text. Richer rumour weighting/policy remains pending.

### E. Towns, buildings, wilderness, and daily actions

- [ ] E1. Towns and buildings. DSL delta: `towns { town(Towns.ollk) { name; position; taxPercent; services { ... } } }` plus `townSystem { buildingServices { ... } }`. Native decision: current-location state and generic enter dispatcher. Current game source status: the four current towns are declared with exact names, coordinates, tax rates, settlement class, and service availability from `data/world.json5`; reusable building service records now carry exact service hours and scene targets.
- [ ] E2. Keymaps and menus. DSL delta: typed semantic input bindings for authored/static choices, allowing multiple inputs per choice. Native decision: keyboard input and dispatch execution. Current game source status: the town-entry keymap is modeled under `townSystem { menu { church(); smith(); inn(); merchant(); tavern(); leave() } }`, preserving C/S/I/M/T/L order and leave-menu semantics; script menus now have reusable choice helpers and explicit `breakLoop`/`stop` propagation semantics. `wilderness { keys { ... } }` declares the current 8/2/4/6 plus arrow-alias movement, Space/S/W/R/!/J/?/H/V/T/P/E/L/C/Z/Q/X actions, E/L cave prompt, S/Q out-of-moves prompt, and Y/N quit confirmation. `DungeonKeysModel` declares the first dungeon-loop D/A plus shared ?/L/T/P/W/R/Q/Z/V bindings for the reusable dungeon runner. `YdargKeysModel` declares in-castle navigation, treasure, and stair-offer bindings. Wilderness, dungeon, and Ydarg runtime sessions expose typed `InputCommand` lists that both dispatch input and generate bounded `?` help pages, eliminating the old parallel help copies. Town-entry, speak/question, shop main/purchase/sale/interlude/catalog/item, random-offer, battle fight/run/action, wilderness, dungeon, Ydarg, and script menu options now store `InputBindingModel` rather than a bare `Char`, while `.key` remains a compatibility view of the primary key for traces/tests. Terminal-style adapters consume those bindings through `readChoice(...)`; dynamic member/inventory/target/spell prompts still generate their key ranges from current state. Runtime-owned Y/N prompts use the shared `StandardYesNoKeys`/`readYesNo()` Boolean path, while authored scripts use `choice.yesNo(...)` to branch on the result. Remaining work: expose nicer author-facing presets for common aliases and feed these bindings through non-terminal C/server adapters.
- [ ] E3. Shops and services. DSL delta: smith, merchant, inn, tavern, church, hut, and lodge as building scripts/tables. Native decision: menu input/render primitives, reusable price/item metadata, first-free-slot placement, drop-prompt overflow UI, resumable script continuations, and visible wait/pause prompts. Current game source status: town building services for church, smith, inn, merchant, and tavern now have scene targets and exact opening hours; church has a first-pass typed H/E/R/L script, inn has a first-pass typed supper/drink/lodging/chitchat script, smith and merchant have first-pass `shopService(...)` catalogs/sell policies plus runnable transaction interpreter/session, scripted trace adapter, and terminal-style adapter, tavern has a first-pass typed scruffy random-item offer plus speak/story/rumour/order script, and hut runs as a normal scene that can invoke speak for wilderness-position NPCs. `RylweenKotlinEngine` can boot compiled content into a town or wilderness session with realized NPCs, and `runKotlinTown` drives town sessions from a real terminal. `TownSession` and wilderness hut entry now dispatch available services/scenes from `RuntimeSessionState`, reusable shop sessions, resumable script bridge handoffs, leave-town equipment prompts, nested speak sessions, nested random-item offer sessions, and scripted reward grants. `StatefulScriptBridge` can execute service menus/member prompts plus core party/member/realm/story/RNG mutations without replaying side effects after blocked input; authored church H/E/R/L, inn supper/drink/lodging/chitchat, tavern speak/rumour/scruffy-offer/order/story/rumour-limit, hut speak, bargirl lodging, and Garth's reward branch now run through runtime adapters. `GamePromptsModel` plus terminal pause hooks now render the authored `(<verb> RETURN key)` prompt and consume Return explicitly in the ANSI adapter. The compiled-game harness now covers real authored church, merchant, smith, tavern, hut speak, bargirl lodging, seeded tavern scruffy/story paths, direct Garth reward, and Garth reward through normal speak selection flows. Remaining work is broader parity, tax/realm transitions, and C/server adapters rather than hut/lodge ownership.
- [ ] E4. Rest, camp, hunt, and lodge. DSL delta: action scripts with costs, risks, time passage, food/fatigue effects. Native decision: RNG and state mutation primitives. Current runtime status: camp/rest and hunt now have authored balance data plus reusable runtime sessions: camp owns guard selection, food gate, time advance, healing, temperature damage by authored item protection, and ambush handoff; hunt owns member prompt, success chance, time cost, food gain, and narration. Bargirl lodging now follows the authored-content rule via a speak question-action script with price, prompt, broke/spent text, gold mutation, and per-member HP drain. Remaining lodge work is only broader parity/generalization if other lodge-like services need a reusable model.
- [x] E5. Upkeep and time. DSL delta: `upkeep { daily { ... } hourly { ... } }` with balance tables. Native decision: clock/tick scheduling and deterministic RNG. Current runtime status: `WildernessSession` has an explicit `TurnStart` step that advances the hour once per wilderness command cycle, regenerates mage/priest mana by seeded RNG, applies authored food tick/cost rules, applies authored starvation thresholds/damage/death text, decrements rested hours, and applies the authored fatigue ladder with sleep-deprivation drain. Broader save/load scheduling policy remains future work, but the current numeric rules are no longer hardcoded Kotlin balance.
- [ ] E6. Wilderness events and transitions. DSL delta: terrain transition scripts, random wilderness event tables, and authored wilderness input bindings. Native decision: random draw and event dispatch runner. Current runtime status: `WildernessSession` can move the active party across the authored `WorldMapGrid` using authored number-key or arrow-key bindings, reject impassable terrain/out-of-bounds movement, spend one move per successful step, pass time with the Space command, enter declared towns from town tiles, run the reusable town session, enter NPC huts through the normal scene/speak bridge, return to wilderness, run original C wilderness key bindings for Space/S/W/R/!/J/?/H/V/T/P/E/L/C/Z/Q/X, route S to a backend-neutral save step fulfilled by frontend save ports, route J to the shared party-journal session, route V to shared party view, route L to shared inventory drop, route Q to quaff potion item-use, route W/R/T/P to equipment prompt sessions, route Z to a reusable priest-spell prompt/session over authored spell effects, route C to camp, route H to hunt, model the reusable X quit-confirmation flow with cancel/finish outcomes, model the S/Q out-of-moves prompt with save-before-quit behavior, poll authored random-step missions such as bandits/troll tooth, terrain missions such as skullweed/Foot of Jazzy, fixed-position missions such as Erethor/husband cave, and realized-location missions such as Silver Key before generic wilderness events, dispatch authored terrain-transition narration on seeded quiet moves, prompt E/L on cave discovery, apply cave entry cost/no-moves checks, hand off random-cave and authored mission dungeon starts, run a reusable `BattleSession` for random encounters by authored encounter tier, execute priest Teleport through semantic direction/distance prompts plus `teleportParty(...)`, execute priest Earthquake cave discovery through `discoverCave(...)`, and execute Revelation's authored dungeon find-object branch with real item metadata. Torch spending is available as reusable `consumeTorch()` and `tryDungeonDescent(currentDepth, maxDepth)` actions used by the first dungeon-loop runner. Remaining work: secret checks inside random wilderness dispatch, castle-entry parity, and score/clean-exit adapter behavior.
- [ ] E7. Character creation. DSL delta: start-state builder plus class/race/stat roll rules. Native decision: input, RNG, and state allocation.

### F. Items, spells, combat, monsters, and rewards

- [x] F1. Item records. DSL delta: typed item definitions for mundane, equipment, potion, magic, and quest items. Native decision: id lookup and inventory storage. Current game source status: all 63 current item records and 7 semantic item pools are declared in `items { ... }`; generated handles expose `Items` and `ItemPools`, while compiled `GameModel.itemCatalog` and `GameModel.itemPools` are the runtime/validation sources of truth.
- [x] F2. Item use effects. DSL delta: item scripts for potions and magic items. Native decision: consume/remove primitive and typed stat mutation helpers. Current game source status: `itemUse(...)` handles reusable context/consume/effect metadata; red, blue, and amber potions are scripted inventory item uses with member prompts/filters, script-controlled slot consumption, and typed HP/mana mutations, and all 12 current battle magic items carry explicit combat context/consume metadata plus authored effect bodies. Runtime notes: battle item use is runnable; `InventoryItemUseSession` is the generic non-combat inventory item-use menu/session and is wired into the wilderness terminal shell; selected weapons/armor route into the equipment ready session, while non-usable gear/treasure reports no effect. Service/dialogue reward and random-offer runners receive model item records. Torches and food are party resources rather than slotted item-use actions, reusable torch consumption is a dungeon-transition action, donkey food capacity is enforced on inventory loss, and tent/blanket rest protection is available for the upcoming camp runner.
- [ ] F3. Equipment rules. DSL delta: ready/remove actions and derived combat values in DSL. Native decision: equipment storage and target/member access. Current runtime status: pure Kotlin `equipFromInventory(...)`, `unequipToInventory(...)`, and `applyEquipmentCommand(...)` implement the reference swap/free-slot state changes against `PartyState`; `EquipmentPromptSession` and `TownLeaveEquipmentSession` cover direct ready/off and leave-town ready prompts with trace and terminal-style adapters. `BattleSession` now uses readied weapon damage for player attacks and readied armor class for monster melee. Remaining work: wieldability/class restrictions and any non-combat equipment effects.
- [ ] F4. Spell records. DSL delta: mage, priest, and monster spell definitions with typed costs, targets, effects, and text. Native decision: spell picker and cost-payment primitive. Current game source status: all 6 mage battle spells, all 4 monster battle spells, and all 7 priest spell records are authored in the game DSL with inline typed text, party/monster/member bindings, target choice, damage/healing formulas, combat flag mutation, semantic runtime context checks, Teleport/Earthquake runtime ops, and Revelation's successful dungeon find-object/take branch over authored item pools. Remaining work: broader priest parity checks, moving any remaining balance constants into authored data where they prove world-specific, and deciding whether a future room-radius Revelation model is needed once dungeon rooms/secrets exist.
- [ ] F5. Monster records and tiers. DSL delta: typed monsters, encounter tiers, behavior hooks, and rewards. Native decision: encounter instance allocation. Current game source status: all 68 current monster records are declared in the game DSL, including weak/wild/deep tier membership, immune flags, spell power, weapon skill, HP/count ranges, and Gnoll's level-scaled count maximum. `BattleSession` allocates monsters from authored tiers, rolls foe count with `encounterFoeCountMax = 8`, and rolls scaled HP from authored formulas.
- [ ] F6. Combat loop. DSL delta: typed combat actions, turn phases, flee/death/reward rules, and authored prompt/menu text. Native decision: combat context storage, target UI, and typed op execution. Current game source status: `combat { standardBattle { opening; text; playerActions; playerRound; monsterRound; rewards = Rewards.standardBattle } }` declares the reusable battle framework with current-game menu/status text and tunables for attack, defend, battle cry, monster spell chance, and monster melee. Runtime status: `BattleSession` executes fight/run, per-member rounds, attack target prompts, defend, battle cry, mage spell selection/costs, combat item selection/effects, monster melee/spells, party defeat, victory, formula-driven XP/gold, display-name loot, and level-up application/narration. Remaining work is broader parity traces.
- [ ] F7. Monster AI and attacks. DSL delta: monster behavior scripts for melee/spells/specials. Native decision: RNG and target choice helpers. Current game source status: `standardBattle.monsterRound` models spell chance and melee fallback; authored monster spell scripts cover frost, fire, hold, and confusion. Monster-specific behavior hooks remain future work if the remaining game port uncovers a monster that needs one.
- [ ] F8. Rewards and loot. DSL delta: reward DSL for gold, experience, items, flags, and journal updates. Native decision: first-free-slot helper and overflow prompt. Current game source status: top-level `rewards { reward(Rewards.standardBattle) { experience; gold; itemPlacement; lootTable(...) } }` declares the standard battle reward bundle using semantic item pools and exact full-inventory behavior. Script-level `reward { gold(...); items(...) }` covers Borin's skip-if-full gift, the Ogre cave Mithril Mail reward, and Garth's prompt/drop merchant reward; `journal { ... }` covers Borin/Hobgoblin location notes with typed runtime location coordinates; shop and random-item purchase helpers default to prompt/drop inventory placement with semantic item handles. Runtime status: `BattleSession` applies standard battle XP/gold/loot rewards from authored formulas; `RandomItemOfferSession` executes scruffy-style item offers with item cost/name lookup, gold payment, first-free-slot placement, and prompt/drop overflow; generic `Op.GrantReward` applies gold and slotted items through `RewardGrantSession`, prompts for drop-slot overflow, and resumes without replaying already-applied rewards. Special inventory projections remain milestone state, so scripts should use `achieve(...)` rather than granting special items as slotted rewards.
- [ ] F9. Leveling and experience. DSL delta: class progression tables and level-up formulas. Native decision: applying member stat changes. Current status: authored class XP thresholds/growth and hit-point-floor formula are in the game DSL, and battle victory applies and narrates level-ups after XP rewards.

### G. Dungeons, Ydarg, secrets, and bespoke systems

- [ ] G1. Dungeon scenarios. DSL delta: `dungeons { event(...) { ... } scenario(...) { name(...); levels; events } }` first, then richer `dungeon(...) { levels; rooms; events; encounters; exits }` only when the model needs maps/rooms. Native decision: dungeon instance storage and optional map generation primitive. Current game source status: the authored event/scenario table covers display names, empty-level text, found objects, gold/torches, gold chests/traps, weighted weak/deep random encounters, Fire Shield Guardian/award, Bracelets award, Nolrim tomb, Spectrum plaque, Thun riddles, Reebar colour riddles, Master Crown battle/award, Silver Sanctum note, Hobgoblin patrol/guards/chief/Silver Key, Ogre cave orcs/Tammy, Erethor priests/Lute chamber, random-cave weights, all current fixed placement scenarios, and the standard cave/dungeon backdrops. Fixed scenarios explicitly include the standard random dungeon event mix before overlaying special levels. Current runtime status: script and wilderness sessions hand off semantic dungeon ids plus level counts into a reusable `DungeonSession`; the runner builds a fresh per-session level event table, executes authored screen/reward/milestone/flag/random-encounter/text-answer events, clears non-recurring events only inside that session, preserves recurring events for revisits, supports level menu rendering, D/A traversal, torch spending and no-torch/max-depth/leave text, shared party view/drop/equipment prompts, Q potion use, Z priest spell dispatch including Revelation's item-find branch, return to wilderness, post-dungeon mission-script resume, and first compiled playthrough checks for Thun riddles/Bracelets, Reebar colour-riddle level context, Hobgoblin password/Silver Key reward, and utility drop/view commands. Remaining work: verify whether "dungeon room/secret actions" means anything beyond the authored level-event bodies in `src/dungeon.c`, broader parity/playthrough traces for each authored dungeon, and any room-radius Revelation behavior that falls out of that parity pass.
- [ ] G2. Cave/location events. DSL delta: special caves, pits, sanctums, and location scripts move from C/JSON5 into typed scenes. Native decision: enter/exit dispatch only. Current game source status: seven current dungeon-entry mission prompts are authored with semantic triggers and `enterDungeon(...)` handoffs; Thun's gem altar consumes any five authored altar-gem pool items before handing off to the temple; Borin, Kobold village, Ogre cave, Foot of Jazzy, and Hobgoblin caravan location bodies are authored through typed scenes with mission-level resume/completion tests. The standard state fixtures now place `Locations.hobgoblinCave` so Hobgoblin caravan text and journal entries resolve semantic coordinates instead of falling back to placeholder text. Remaining location work belongs to broader dungeon room/secret parity and playthrough coverage.
- [ ] G3. Ydarg castle. DSL delta: model rooms, mutable room state, events, rewards, entry gating, and victory flow in DSL/content. Current status: `YdargContentModel` now declares castle entry, backdrop, key/victory milestones, map size, room roll table, monster tier, treasure tables, pitfall rules, stairs item trades, confrontation sequence, keys, and authored narration. `YdargSession` owns seeded room state, current floor/position, movement, room clearing, pitfall damage/disarm, treasure placement, stairs teleport/item-give flow, nested battle handoff, final confrontation battles, final `Milestones.ydargVictory` mutation, shared child-session routing for in-castle party view, drop, equipment, quaff, and priest spells, plus save/resume snapshots for navigation state. Wilderness castle entry now hands off semantically to `WildernessSessionStep.Ydarg`; the old `native.ydarg.*` game-source island and native allowlist surface are removed. Remaining work: broader parity/playthrough traces for treasure, traps, final battle defeat/victory, and ANSI behavior.
- [ ] G4. Secrets and sysop/debug actions. DSL delta: explicit optional debug/secret block with permission gates. Native decision: input matching and test-only gates.
- [ ] G5. Score/vscore. DSL delta: decide whether scores are content/state or a native-only external feature. Native decision: file I/O and display backend.
- [ ] G6. Replay/test knobs. DSL delta: test profiles and fixture states replace game-rule env knobs where possible. Native decision: test harness overrides only.

### H. Engine replacement path

- [ ] H1. Compiled content bundle. DSL delta: define generated bundle schema from Kotlin content. Native decision: C loads typed tables, not JSON nodes.
- [ ] H2. Compiled state image. DSL delta: define generated state schema and exact save snapshot schema. Native decision: C stores mutable ids/arrays and can write exact DSL state.
- [ ] H3. String interning and hash tables. DSL delta: every handle has slug plus dense id. Native decision: stable lookup tables for diagnostics, migration, and saves.
- [ ] H4. Typed script/op VM. DSL delta: scripts compile to typed ops with checked operands. Native decision: VM executes ids and typed values only.
- [x] H5. Renderer boundary. DSL delta: scripts use `showBackdrop`, `renderMap`, `say`, and status helpers rather than terminal cursor calls. Native decision: backend-specific terminal/image drawing. Current status: terminal prompt rendering and line-terminal adapters are split out of the core engine, menu footers are centralized over authored/runtime bindings, script rendering emits typed `ViewCommand` events, the ANSI frontend implements those events, and the reusable map viewport returns terrain cells instead of terminal rows. Remaining visual work belongs under H8/H9: classic-layout polish, battle banner/victory image selection, and broader typed render events where future frontends need more than the current five commands.
- [ ] H6. Remove JSON5/binary-data runtime dependency. DSL delta: inventory records, world metadata, and map rows/layers are modeled and emitted from game DSL/content bundles; `data/map.dat` and JSON5 stay source-reference/migration material only. Native decision: readers for those files are deleted or kept only as offline migration tools.
- [ ] H7. Reusable script-fragment identity. DSL delta: typed generated handles for reusable script fragments only if compiled bundles, saved continuations, debug traces, source maps, or non-Kotlin engine adapters need to name fragment boundaries. Native decision: persisted call frames and bundle debug metadata must refer to dense ids plus diagnostic slugs; authoring should still avoid string ids.
- [ ] H8. Replaceable frontend/runtime shell. DSL delta: boot compiled content through `RylweenKotlinEngine`, realize start state, run town/wilderness/service/combat/dungeon sessions behind semantic input/output adapters. Current runtime status: `runKotlinTown` boots the compiled game, realizes NPCs with authored-map-backed wilderness placement, and drives the town session through stdin/stdout. `runKotlinGame` now boots the selected state, uses the authored wilderness map from compiled content by default, projects NPC huts, advances turn-start upkeep, moves the party on the wilderness map using authored number-key or arrow-key bindings, enters Thord/Ollk/Wilor/Murn from their declared coordinates, runs the existing town loop, returns to wilderness, handles Space pass, routes C to camp, routes H to hunt, handles seeded terrain/cave/encounter wilderness events, routes encounters into `BattleSession` fight/run opening, routes dungeon handoffs into the first `DungeonSession` shell, handles X/Y/N quit confirmation, and handles the out-of-moves S/Q prompt inside `WildernessSession`. `installKotlinAnsi` installs a direct `build/install/rylween-kotlin/bin/rylween` launcher for the same compiled game through a first executable-style ANSI frontend: packaged static ANSI/doc resources, authored map/resource loading from compiled content, fixed text/status/map regions, `clw`-style text-window clearing, status-line redraw, two-character terrain glyph/color map rendering, raw-key input through JLine where supported, typed `ViewCommand` dispatch for script rendering primitives, and app-level logging sinks. Native/view decision: ANSI terminal, bitmap, C bridge, server, and headless test adapters must be replaceable frontends over the same model/session contracts, not owners of game rules.
- [ ] H9. Kotlin ANSI frontend adapter. DSL delta: `terminal { terrain(...); region(...) }`, authored wilderness cave image, and authored dungeon backdrop declare the current terminal presentation data from `data/world.json5`/`data/tiles` without making raw map bytes visible in gameplay rendering. Native/view decision: isolate ANSI terminal mode, cursor addressing, window drawing, text/map/image/status rendering, key decoding, line input for text-answer prompts, logging/socket fan-out, and save-file prompts behind an adapter API so a bitmap frontend can replace it without changing game rules, state, or content. Current status: the ANSI shell handles full-screen `ansi/*.ans`, map-window `tiles/*.ans` backdrops for town/service scripts, cave discovery, and dungeon exploration via typed `ViewCommand` events; line-terminal prompt rendering is centralized in `src/main/kotlin/rylween/frontend/terminal/RylweenTerminalFrontend.kt`, and session adapters are split into `src/main/kotlin/rylween/frontend/terminal/RylweenTerminalAdapters.kt`. Current gaps: nested town/battle/item/dungeon sessions still use modal text rendering rather than a polished classic-layout flow, encounter-banner and victory image selection need direct battle-session rendering, and save prompts remain unwired.
- [ ] H10. Game universe compatibility. DSL delta: compiled content declares or derives a universe id/version/hash. Native decision: save/load checks snapshots against that id and rejects incompatible worlds or invokes explicit future migrations. No old C/JSON save compatibility is required by default.
- [ ] H11. Scenario and playthrough testing. DSL delta: optional test/scenario declarations or fixtures can name start states, RNG seeds, scripted semantic inputs, and checkpoint assertions without changing runtime rules. Native/test decision: line-terminal, ANSI, bitmap, server, and headless adapters should all be driveable from the same scenario harness where practical; broad playthrough tests should run through compiled content and session APIs, not direct unit-only shortcuts. Current status: a reusable test-scope `TerminalScenario` harness now carries named fixtures, seed metadata, scripted inputs, captured text/view/image traces, output/image assertions, view-command assertions, and checkpoint-labeled failures. Current compiled scenarios cover Thun/Reebar/Hobgoblin dungeon playthrough checks, multi-battle mission completion bodies, wilderness save/out-of-moves and file save/load boot validation, authored image/view-event traces, Ydarg gate/entry/navigation/treasure/pitfall/final-confrontation flows, battle run/first-round/victory/defeat flows, merchant/smith/tavern town-service flows, the wilderness-to-town shell handoff, and terminal static-screen/resource rendering. Remaining work: future non-terminal adapter scenarios; consider authored scenario declarations only after test-only fixtures prove the shape.

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
| Save/load | `driv.c current-world.json5`, reference `.dat` | exact `state {}` snapshots | filesystem I/O for new snapshots; reference importer only if requested later |
| Realm/session state | `struct ryl_state` | `state { realm { ... } }` | active-state pointer, transient runtime frame |
| Parties | `party.c`, `driv.c` save/load | `party(...) { ... }` state | storage, bounds checks, state mutation primitives |
| Party members | `struct party_member` | `member(...) { health/hitPoints/wisdom/mana/... }` | primitive field access in typed VM |
| Inventory slots | `inventory.c`, party array | `inventory { slot(n).item(...) }` state + item scripts | slot storage, low-level insert/remove helpers |
| Special party fields | `fireshield`, `bracelets`, `mastercrown`, `jazzyfoot`, `silverkey` | `gameProgress` milestones with optional special-inventory projection and win-condition metadata | dense milestone storage; reference field importer/exporter only during migration |
| Journal | `interact.c`, party journal | typed journal entries in state and scripts | string persistence/render append primitive |
| NPC definitions | `world.json5 npcs`, `res.c` | `npcs { ... }` content | id lookup, runtime placement storage |
| NPC visited bits | `struct person.visited` | `visited(Npcs...)` state | bitset/set storage keyed by npc id |
| NPC roles | `world.json5 npc_roles` | `roles { ... }` content | role-to-npc lookup by id |
| Towns/buildings | `world.json5 towns/buildings`, `towne.c` | `towns {}` / `townSystem { buildingServices { ... } }` content + scripts | current-location state and generic enter dispatcher |
| Town keymaps | `town_keys` | `townSystem { menu { ... } }` typed menu/key declarations | keyboard input and dispatch execution |
| Wilderness keymaps | `wilderness_keys`, `driv.c` | `wilderness { keys { ... } }` semantic input bindings with aliases | keyboard input and dispatch execution |
| Combat keymaps | `combat_keys`, `battle.c` | typed combat action declarations | keyboard input and dispatch execution |
| Map dimensions | `world.json5 map`, `wld.c` | `worldMap { size/storageCapacity }` | allocation, coordinate bounds |
| Map cells | `data/map.dat`, `state.map` | typed map layers/cells | loaded cell arrays, optional reference map.dat reader |
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
| Equipment | `weapon_ready/off`, `armor_ready/off` | equipment actions/scripts | prompt wrappers and derived combat values |
| Potions | `inventory.c apply_potion` | potion effect scripts | low-level stat mutation helpers |
| Magic items | `world/items/magic_items.json5` | item use scripts | inventory consume/remove primitive |
| Rewards/loot | `reward` op, battle/dungeon scripts | typed reward DSL | first-empty-slot helper and overflow UI primitive |
| Monsters | `world/monsters/monsters.json5` | typed monster records/tier tables | encounter instance allocation |
| Encounters/combat loop | `battle.c` | combat rules/actions/scripts + monster AI scripts | turn runner, target selection UI, combat context storage |
| Monster spells/melee | `world/spells/monster.json5`, battle fallback | monster ability scripts | combat context access primitives |
| Mage/priest spells | `world/spells/*.json5`, `subs.c spell` | typed spell records/effect scripts | spell picker input and cost/payment primitive |
| Dungeons/caves | `dungeon.c`, `world/dungeons`, `scripts/dungeon` | dungeon scenarios, levels, events, placements | dungeon instance storage and map generation primitive |
| Ydarg castle | `ydarg.c`, `scripts/ydarg` | Ydarg map/state/content/session, shared secondary in-castle utility command sessions, navigation save/resume state | parity traces |
| Missions/triggers | `world/missions.json5`, `missions.c` | typed trigger/action/binding DSL | trigger polling and dispatch execution |
| Dialogue/speak | `speak.c`, `scripts/speak*`, text shards | dialogue scripts, role bindings, chitchat tables | NPC selection/input and dispatcher |
| Rumours | `tellRumour(...)`, typed rumour table entries | typed rumour table/script | RNG draw only |
| Shops/church/inn/tavern/merchant/smith/hut | `towne.c`, scripts/towne | building scripts and shop tables | input/render primitives, price/item metadata lookup, first-free-slot and drop-prompt overflow helpers |
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
- `data` side files: `map.dat`, reference `.dat` state files, `current-world.json5`,
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

For migrated reference maps, accepting compact rows is fine, but the row characters
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
whenever(Story.church.donation.done) { ... }

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

## Reference Flag Slots

Reference flag array positions are not a Kotlin authoring concept.

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
- old slots exist only in a transitional reference-save reader.
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
screen {
    line("- That'll be ", price)
    line("gold coins, please.")
    line("")
}

screen {
    line("The priestess raises")
    line(pronoun(member, PronounCase.Object), ".")
}

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
- a status: permanent engine service, temporary reference bridge, or migration shim

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

Temporary reference/import helpers currently expected during migration:

- JSON5/current-world import or comparison tooling, only where it helps port content
- `data/map.dat` map-byte reader only as migration/debug tooling; normal Kotlin launchers use authored map rows
- terminal ANSI renderer
- generic `engine_call`/`engine_int`/`engine_bind` registry while typed ops are
  replacing it
- save importers for positional arrays or `.dat` files only if explicitly requested
- replay/environment knobs used only by tests

Permanent native services should be small and domain-neutral. For example,
`render_map(Maps.wilderness, ScreenRegion.map)` is acceptable; `do_silver_key`
or `shop_smith_beer_offer` is not.

For B7, native should not model a hard-coded "special item" family. It should
store achieved milestone ids, expose generic milestone set/clear/test ops to the
typed VM, derive any special-inventory rows from milestone metadata, and run the
content-defined winning-configuration predicate when loading a state or booting
from an authored fixture. Reference save import can translate `fireshield = 1`,
`bracelets = 1`, `mastercrown = 1`, `jazzyfoot = 1`, and `silverkey = 1` into
their matching milestones; reference `silverkey = 2` maps to the separate Ydarg
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
- terrain slug / reference map code -> `terrain_id`
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

Keep a migration loader for `data/map.dat` only if it remains useful for
audits, but normal runtime loading should compile authored map rows into
terrain ids. The runtime world map should be something like:

```c
struct ryl_map_layer {
    map_layer_id id;
    terrain_id *cells;
    int width;
    int height;
};
```

Reference predicates such as `wld_tile_is_water(char c)` should become typed
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
reference loader can map old slots to ids, then disappear when old saves are no
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
        sourcePartyField(SourceSpecialField.Silverkey)
    }

    milestone(Milestones.ydargVictory) {
        sourcePartyField(SourceSpecialField.Silverkey, achievedValue = 2)
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
content-defined winning predicate at startup, and reference `silverkey == 2` should
map to a separate completion milestone rather than overloading the Silver Key
state.

### 8. Load and save state DSL snapshots

Add a deterministic writer for full `state {}` snapshots. It should write every
persisted field in a stable order.

Add a loader for the same state model. The loader can consume either:

- the compiled state bundle produced from Kotlin-authored states, or
- the strict runtime snapshot subset written by the save system.

New saves should not require reference positional arrays. Old JSON5/current-world
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
2. Grow `src/game/kotlin/RylweenGame.dsl.kts` into a representative typed content/state source while keeping `dsl-next.kts` as the small prototype lab.
3. Keep reusable script bodies as Kotlin game-script helpers until stable
   fragment identity is required by persistence, debugging, source maps, or
   compiled bundles.
4. Define the native bundle structs and ids, including typed script-fragment ids
   only if the phase-3 triggers have appeared.
5. Add C string interning and resource hash tables.
6. Model maps, terrain, visual assets, screen regions, and render styles.
7. Add state DSL snapshot writer/reader.
8. Replace flag storage with dense `flag_id` indexing.
9. Replace map bytes with terrain ids.
10. Replace text placeholder strings with placeholder ids.
11. Add the typed renderer boundary over the current ANSI backend.
12. Replace the JSON node script interpreter with typed ops.
13. Port scenes/spells/items/missions/maps from JSON5 into Kotlin DSL.
14. Remove JSON5 from the runtime path after parity tests pass.

The final state is: Kotlin DSL is the canonical source, C consumes compiled
typed content/state, and runtime saves are exact state DSL snapshots.
