# Kotlin DSL plan

This is the current direction for the Rylween DSL work. It supersedes the
earlier "tighten the JSON5-ish Kotlin transcription" plan.

The target is not "Kotlin that writes JSON5." The target is a Kotlin DSL that is
the canonical game source: immutable content, mutable game state, authored start
states, and runtime saves all use the same typed model. JSON5 remains useful as
reference material during migration, but it is not the design endpoint.

Current checkout note: the active tree has no `legacy/` directory. Historical
sources are preserved in `scratch/legacy-archive.zip`. The active runtime is
`:runtime`; shared ANSI escape/key primitives are in `:terminal`; reusable DSL,
compiled-bundle, and snapshot/VM diagnostic contracts live under
`core/src/main/kotlin`; authored game DSL and resources live under `:game`; and
bundle/native-probe tooling lives under `:tools`.

Current cleanup track:

- Continue splitting `core/src/main/kotlin/rylween/dsl/Dsl.kt` into cohesive
  source files that keep the same `rylween.dsl` authoring package. The split is
  architectural, not a new authoring surface: reusable DSL/kernel definitions
  stay in core, while Rylween-specific prose, menu labels, battle text,
  service scripts, NPC placement facts, and scenario content stay in
  `game/src/main/kotlin/rylween/game/RylweenGame.kt`.
- Clean up `RuntimeSnapshotModel.kt`. It is still used by compiled bundle state
  compilation and save/session snapshot contracts, but its current
  `rylween.runtime` package/path reads like live runtime machinery. Keep the
  portable DTO contract, then move or rename it only if that reduces coupling:
  likely toward a snapshot/state contract package owned by `:core`, with no
  semantic save-format change.
- Run a Kotlin idiom pass across active source. Remove nullable fields and
  parameters where the model always has a real value, prefer explicit default
  sinks/empty records over `null`, use immutable collections at model
  boundaries, replace imperative accumulation with `buildList`/`buildMap` where
  it makes code shorter and clearer, and use `require`/`check` or small domain
  exceptions for invariants instead of ad hoc nullable error paths. Long
  parameter trains should collapse into narrow context/value objects only when
  that makes ownership and serialization boundaries clearer.
- Audit all `@Suppress` usage after the DSL split settles. Every suppressed
  warning should have a current reason: public DSL surface, generated-catalog
  entry point, reflective/serialized contract, test oracle, or deliberate
  compatibility hook. If a warning points at dead code, stale enum values,
  unreachable helpers, or a missing implementation path, remove or fix the code
  instead of broadening suppression. Treat newly exposed warnings as a bug-finding
  pass, not just IDE noise.
- Keep future-facing DSL affordances as explicit checklist work instead of
  leaving unused public builders in the active authoring surface. Before
  reintroducing an affordance, identify the runtime/frontend/compiler consumer,
  add validation, and add at least one game-content or focused unit test that
  proves the affordance is actually wired.

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
- Game-specific content should be declared in plain domain blocks. Adding a
  monster, flag, mission, milestone, text, or scene should mean adding a content
  record, not writing a Kotlin class.
- Game-specific behavior belongs in the authored DSL unless it is truly generic
  runtime machinery. Short screens, handoffs, riddle/password scenes, random
  item-offer mechanics, and current long-form flows such as traveller
  storytelling and priest teleport should be explicit script content in
  `RylweenGame.kt`; reusable mechanics belong in the DSL/runtime layer.
- The Kotlin DSL project is now a small Gradle build named `rylween` with
  source packages split by responsibility: `rylween.dsl` under
  `core/src/main/kotlin/rylween/dsl` owns the authoring model and validation,
  `rylween.compiled` under `core/src/main/kotlin/rylween/compiled` owns compiled
  bundle contracts/loaders/emitters, and the remaining `rylween.runtime`
  sources only hold snapshot/VM diagnostic contracts. The live session graph,
  old engine facade, old save parser/renderer, and terminal adapters are no
  longer active source; they live only in `scratch/legacy-archive.zip`.
  Current-game handles are now
  generated under `build/generated/gameCatalog/kotlin` from
  `game/src/main/kotlin/rylween/game/RylweenGame.kt`, so no handwritten
  main-source game catalog remains. The content source lives at
  `game/src/main/kotlin/rylween/game/RylweenGame.kt`.
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
the DSL shape is represented in `game/src/main/kotlin/rylween/game/RylweenGame.kt`
or the archived `legacy/scratch/dsl-next.kts` prototype lab where appropriate,
mutable data has save-state coverage, native calls are named and typed, and at
least one parity check or replay fixture is planned.

The "DSL delta" text is the concrete thing to look for in the Kotlin diff.

## Coverage Ledger

This is a migration ledger, so older checked-off rows preserve historical
implementation notes. Treat the "Current checkout note" above and later
closeout rows as the source of truth for the live repository shape.

Keep two separate counts while migrating:

- DSL/runtime capability coverage: whether the language/runtime can express,
  validate, execute, and save a subsystem.
- Full-game content coverage: how much of the actual current Rylween game data
  has been authored in the Kotlin game source.

Current status:

| Area | DSL/runtime capability | Full-game content in game source | Not yet fully implemented |
|------|------------------------|------------------------------|---------------------------|
| Project/build | Gradle wrapper, Kotlin JVM build, tests, game DSL validation, validation split, generated current-game handle catalog, current-game script helper source set, generated compiled-game test harness, app source set, packaged app resources for ANSI art/tile art/static documents, runnable Kotlin town, wilderness, and ANSI runtime tasks, first runtime source splits for shared state/RNG/NPC placement and battle/combat scripting | N/A | publishing/package layout; compiled bundle emission beyond JVM-generated Kotlin; continue splitting oversized runtime sources by responsibility |
| Runtime state/save | `stateFixture`, strict `runtimeState`, validation, deterministic text writer, strict snapshot parser, runtime session wrapper for `RealmState` plus parties, realized NPC placements, and realized quest locations, persisted `lastTerrain`, first save-shaped town-service continuation stack, seeded RNG state mutation, turn-start wilderness upkeep mutations, active castle/dungeon mode snapshot blocks, save metadata with snapshot format, universe id/version, compiled-content hash, and verified compiled-bundle hash for VM continuations, live save normalization for living member count/full inventory/NPC visit bits, plus typed test-scenario fixture metadata. J2 continuation snapshots are engine execution-boundary records and do not by themselves widen player-visible save points. | 3 state fixtures, including near-winning and winning runtime snapshot; Kotlin scenarios use typed `StateId` plus seed metadata instead of replay env state names; ANSI title flow provides the current single-slot BBS-style N/C/T/S/H/D/X menu over named save slots | true multi-party BBS roster if scheduled, plus full exact snapshot coverage from live town/battle/title modes if scheduled |
| Flags/milestones/win | typed flag states, milestone projection, generic win predicate | 13 current story/encounter flags, 6 milestone records | full transition graph validation, generated catalog from source data |
| Items/equipment | declaration-first `items { ... }` records and pools in the game DSL, slotted inventory, separate equipment, authored semantic item pools, generic `itemUse(...)` scripts with contexts/consume policy, generic inventory item-use session, pure Kotlin ready/off mutations, optional authored equipment class restrictions, reusable equipment prompt commands, town-leave ready prompt session, terminal-style item/equipment adapters, first gear/resource side-effect helpers, battle item-use script execution, battle weapon-damage and armor-class effects | 63 current item records and 7 item pools are authored in the game DSL; red/blue/amber potion behavior is scripted as inventory item use; all 12 current battle magic-item effects are authored item uses; the wilderness shell exposes the original Q quaff potion action plus W/R/T/P equipment actions where currently supported | content compiler that can derive handles/catalogs from declarations, full rest/camp/dungeon item effects |
| Towns/services | town declarations, service availability, reusable town menu/service shape, exact service hours, authored town-entry tax preamble, first typed service scripts, reusable shop service and random-item offer helpers, first Kotlin shop interpreter/session plus scripted trace and terminal adapters, top-level town terminal session, first `RylweenKotlinEngine` town-session boot facade, runnable `runKotlinTown` terminal task, resumable stateful service-script bridge with real realm scalar and RNG mutation, visible `waitInput`/pause handling, first runnable church, inn, hut, speak/rumour, scruffy-offer, tavern order/story, bargirl lodging, and rumour quiet-limit service parity, compiled-game runtime harness for authored town-service tests, shared authored input bindings for static town/shop/script choices | 4 current towns, C/S/I/M/T/L menu, 5 town building services plus wilderness hut scene; church, smith, inn, merchant, tavern, and hut first-pass scripts with typed text, reusable choices, shop catalogs, scruffy random-item purchase, inline traveller-story content, authored bargirl lodging question action, authored entry-tax level/key/text policy, and the current 30-entry pause verb pool | C/server adapters for town/shop/speak sessions plus broader realm/session and line-by-line small-screen parity against the C/JSON5 replay snapshots belong under H8/H11 |
| NPCs/roles/visits | character declarations, roles, fixed/random placement, runtime NPC placement realizer, map-backed wilderness placement policy, runtime NPC hut terrain projection, visited bits | 39 current NPCs, 17 story roles, representative runtime placements, authored dialogue branches for all current role-bearing quest NPCs | generated NPC catalog, broader parity checks for NPC branch control |
| Speak/chitchat/rumours | standard conversation model, role/town text refs, anonymous rumours, exclusions, first reusable speak session with NPC selection from realized runtime placements, visited bits, automatic first-matching dialogue branch dispatch, authored question-action dispatch, chitchat/question lookup, seeded chitchat/rumour selection, runtime role/town text binding, and standard rumour filtering | no-NPC/no-rumour fallback screens, chitchat/question tables, authored bargirl lodging question action, 33 town rumour entries, role-bearing NPC dialogue branches, Garth branch execution through normal speak selection | explicit branch leave-vs-menu control, richer rumour selection policy, optional authored wording fields for conversation prompts if terminal-derived labels stop being enough |
| Combat/rewards | reusable standard battle shape, action list, tunables, authored prompt/menu text, authored static fight/run and action input bindings, reward bundle, formula catalog, leveling table, reusable `BattleSession` for encounter allocation, fight/run, per-member player actions, target prompts, mage spell casting, combat magic-item use, monster melee/spells, death, victory rewards, loot, and level-up application/narration; reusable out-of-combat priest spell picker/session | standard battle tunables/text, all 68 current monster records with weak/wild/deep tiers, 6 mage battle spells, 4 monster spells, 7 priest spells, 12 battle magic items, battle loot/reward shape, authored combat/priest text/effect bodies, and compiled scenarios for the standard battle loop, rewards, loot, and level-up path | no known Kotlin-port content gap; broader adapter/playthrough traces belong under H11 and any newly discovered monster-specific behavior should reopen as a concrete content task |
| Missions/scenes/scripts | typed scenes/source-script AST, dialogue branches, rewards, reusable choices, explicit control signals, native allowlist, current-game script helper layer, wilderness random-step/terrain/position/location mission polling, reusable `enterDungeon(...)` handoff statement, script journal append, runtime location-coordinate text refs, inventory-pool count/consume operations, resumable mission-scene execution with nested battle continuations, persisted post-dungeon mission continuations for dungeon saves, and dense runtime script-root identity for continuations/debug metadata | 17 authored wilderness missions: bandits, troll tooth, Thun gem altar, kobold village first battle handoff, skullweed, Borin meet with journal, Borin gift, Ogre cave first battle handoff, Foot of Jazzy first battle handoff, Hobgoblin caravan first battle handoff, and seven current dungeon-entry mission prompts/handoffs; Garth dialogue, rumour/church/Ydarg probes, tavern traveller-story helper | broader parity tests for C edge cases and future non-inlined helper-fragment handles if compiled bundles/source maps need them |
| World/map/terrain/visual/layout | typed handles exist for many concepts; `metadata { ... }` plus `limits { ... }` declare universe/allocation limits that are not derivable from authored content; `terrain { ... }` declares map codes, passability, movement cost, NPC-placement permission, and settlement semantics; `terminal { ... }` declares the current two-character terminal terrain glyph/color table; `layouts { ... }` declares classic screen regions; `visuals { images { ... } staticScreens { ... } }` declares ANSI/full-screen and map-window resources plus frame/title/help/manual/score/vscore/heroes routes; `worldMap(Maps.wilderness) { layer(MapLayers.terrain) { rows(...) } }` provides exact wilderness rows, and `WorldMapModel` exposes derived size/cell-count queries from those rows; `WorldMapGrid` consumes authored terrain semantics for passability/settlements/NPC huts while preserving reference coordinates | four current town coordinates, 10 current terrain semantic declarations, 10 current terminal terrain glyphs, 18 authored ANSI image resources, 5 classic screen regions, wilderness movement key/arrow aliases, static screen/document routes, authored wilderness map rows with derived 78x40/3,120 dimensions, runtime score/vscore/heroes rendering, and eight current terrain-transition texts are exercised by the compiled game runtime | bitmap frontend-specific asset decoding and broader non-terminal adapter parity |
| Dungeons/castles/secrets | narrow native islands and handles plus cave-found and authored-script dungeon handoff shapes; reusable `DungeonSession` with run-local level event tables, authored event execution, conditional recurring clear, D/A/?/equipment/quaff/priest-spell routing, torch descent checks, dungeon leave handoff, semantic `teleportParty(...)` and `discoverCave(...)` ops, semantic `context.inDungeon`/`context.dungeonLevel` script predicates, generic text-input prompts, game-authored cave/dungeon backdrops, compiled dungeon playthrough checks, validated authored `dungeons { event(...) scenario(...) }` content table, authored `CastleContentModel`, reusable `CastleSession` handoff, authored name-gated sysop/secrets, and Kotlin test scenarios that construct their target state directly instead of `RYL_FORCE_*` knobs | random-cave discovery prompt/cost/depth band is authored; fixed Erethor/husband/hermit/Nolrim/Master Crown/Silver Key/Hobgoblin dungeon entries now hand off to semantic dungeon handles; random dungeon fill now includes semantic found-object pools and gold chests; authored special events now cover Thun riddles, Reebar colour riddles, Silver Sanctum, Hobgoblin patrol/guards/chief, Ogre/Orc/Tammy, and Erethor priests/Lute chamber; fixed scenarios explicitly use the same standard random dungeon event mix plus their special placements; compiled tests verify all nine fixed dungeon scenario placement tables, every authored fixed dungeon event body through the terminal adapter, weak/deep random-encounter tier handoff, Spectrum recurrence, and Tammy's rescue-clear condition; priest Teleport, Earthquake, and Revelation first-pass effects use authored script plus semantic runtime ops; find-object/Revelation full-inventory behavior now matches the reference no-drop-prompt rejection; first Thun/Reebar/Silver Key playthrough checks cover riddles, `context.inDungeon`/`context.dungeonLevel`, and password reward; dungeon `S` save/load now resumes active dungeon depth, level-event plan, and post-dungeon mission continuation; Castle Ydarg no-key/key entry, stair gift, shared utility-command checks, `S` save, floor/point/room resume, and compiled castle-hook strict snapshot round trips cover the semantic session path without making battle/reward prompts user-saveable; castle victory mutates the persisted completion milestone consumed by the authored Hall-of-Heroes runtime report; post-dungeon continuations resume mission scripts; Marcus/Karl reference sysop actions are declared in `secrets { ... }` and consumed by wilderness key/step dispatch | richer room/secret model only if future reference behavior requires it |

Rule for future checklist updates: every pass should say both what DSL/runtime
capability moved and what real game content count moved. If either side did not
move, say that explicitly.

### A. Foundation and authoring surface

- [x] A0. Seed typed DSL direction. DSL delta: `rylween { content { ... } state { ... } }`, typed handles, typed flags, first party-state model. Native decision: JSON5 is reference only, not the target runtime format.
- [x] A1. Split the prototype into clear authoring sections. DSL delta: stable `content`, `state`, `texts`, `scripts`, and handle/catalog sections. Native decision: content compiler owns handle derivation. Current status: active reusable source is physically and logically grouped into `rylween.dsl` (`core/src/main/kotlin/rylween/dsl`), `rylween.compiled` (`core/src/main/kotlin/rylween/compiled`), and the small snapshot/VM diagnostic contracts under `core/src/main/kotlin/rylween/runtime`. The old interpreted ANSI app launchers, session adapters, engine facade, save parser/renderer, and terminal frontend helpers are archived outside the active Gradle graph inside `scratch/legacy-archive.zip`; shared low-level terminal protocol primitives live in `:terminal`; bundle-emission tools live under `tools/src/main/kotlin`; normal Kotlin sources under `game/src/main/kotlin/rylween/game/` hold the game authoring area; generated current-game handles live under `build/generated/gameCatalog/kotlin`.
- [x] A2. Replace hand-maintained catalog scaffolding with declaration-first content. DSL delta: declarations such as `items { treasure("Red Gem") { cost = 1000 } }`, `monsters { monster("Orc") { ... } }`, `dungeons { dungeon("Temple of Thun") { ... } }`, `characters { character("Garth") { ... } }`, and `missions { mission("Bandits") { ... } }` should be enough to introduce new world content; generated typed accessors can be emitted under `build/generated` when scripts need symbols. Native decision: compiled bundles can receive dense ids plus diagnostic slugs derived from declarations, but slugs/ids are compiler/runtime artifacts, not normal authoring vocabulary. Current game source status: all current-game handles are generated from `game/src/main/kotlin/rylween/game/RylweenGame.kt` and companion scenario source; reusable `rylween.dsl`, `rylween.runtime`, `rylween.engine`, `rylween.save`, and `rylween.frontend.terminal` do not import `rylween.game`.
- [x] A3. Define typed native-op declarations. DSL delta: reusable implementation hooks are declared once as typed `NativeOps`/`NativeAllowlist` entries in the DSL kernel, while normal current-game authoring calls semantic helpers and typed view/script operations instead of raw native path strings. Native decision: `EngineRequest.path` remains an implementation-port detail for the few reusable runtime hooks that still need it; authored game content in `RylweenGame.kt` does not name native paths.
- [x] A4. Define validation error style. DSL delta: validation helpers report game concepts such as missing terrain, text binding, illegal flag transitions, missing dungeon handoffs, invalid startup states, and save/load compatibility failures through `ValidationDiagnostic`/`RylweenValidationException`. Native decision: the compiled-bundle loader still rejects bad bundles defensively, but author-facing content validation happens before runtime/native loading.
- [x] A5. Define reusable script-fragment identity only when it has a runtime need. DSL delta: keep authored script bodies in `RylweenGame.kt`; persistence/debugging/bundle metadata names resumable script roots through runtime `ScriptRootRef` records, not author-written string ids. If a future script fragment genuinely needs runtime identity, add a typed fragment model rather than a string-keyed helper registry.
- [x] A6. Compile the game model for tests. DSL delta: expose `RylweenCompiledGame` from normal game sources so tests can access `nextRylween` and `nextDsl` directly. Native decision: this is a JVM test harness only; C/server/native compiled bundles remain separate H-series work.
- [x] A7. Split oversized runtime sources without moving game semantics into Kotlin. DSL delta: none directly; this is architecture hygiene so reusable engines stay understandable. Native/runtime decision: split the old monolithic runtime into focused `rylween.runtime` sources for battle, camp, dungeon, hunt, shop, inventory/equipment state ops, rewards/random offers, priest/item-use, speak, town, world map, shared runtime state, and shared session inputs. Engine ports/traces moved to `rylween.engine`, terminal prompt/render adapters moved to `rylween.frontend.terminal`, and save snapshot code moved to `rylween.save`. Caveat noted by A7's second clause: the split itself did not lift every world-specific constant/rule out of Kotlin; that work is tracked under the **A8 minimization plan** below.
- [x] A8. Minimization and integration plan: move config-able Kotlin rules into the authored DSL, keep reusable runners backend-neutral, and consolidate runtime files only when it pays for itself. Current status: closed as a migration gate. Track A's original Kotlin-to-DSL lift list is complete; Track C's content-fill items are landed in their owning E/F/G/H rows; known frontend/runtime duplication was handled by the D burn-down and H view-boundary work. Future drift is ordinary code-health work and is tracked by I1 or by reopening a concrete owning row, not by keeping A8 open forever.
- [x] A9. DSL package boundary and facade cleanup. DSL delta: keep the game-author import surface readable while implementation-heavy subsystem files live under `core/src/main/kotlin/rylween/dsl/impl`. Current status: the physical source split has moved castle/combat/content/flag/runtime/shop/speak/state/world subsystem files into `dsl/impl` with a real `rylween.dsl.impl` package. The root `rylween.dsl` directory now contains `AuthoringFacade.kt`, whose KDoc explains why authored game files and generated game catalogs import `rylween.dsl.*` instead of implementation packages. The facade re-exports the current author-facing concepts and helper entry points, authored game/scenario sources no longer use `import rylween.dsl.impl.*`, generated handle imports compile through the facade, and ledger coverage guards that boundary.

  **Track A — Kotlin → DSL migration completed (so the game designer can change rules without touching Kotlin):**
  - [x] `InventoryOps.kt:foodCapacityByDonkeys()` — lifted to realm base resource capacity plus item-record resource-capacity bonuses on `Items.donkey`.
  - [x] `InventoryOps.kt:campGearEffect()` — lifted to item-record camp-protection/image metadata for tent and blanket.
  - [x] `ShopSession.kt:ShopQuantityCap` — generalized to authored resource-capacity cap sources instead of fixed enum cases.
  - [x] `ShopSession.kt:bargainPrice()` smith haggle — lifted to authored shop bargain policy.
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
  - Per A7's bucket list (shop, inventory/equipment state ops, random offers/rewards, priest/item-use, speak, town/interpreter), priest and inventory item-use sit in two files and could be merged into a single `ItemActionRuntime.kt` if a future change touches both. Not urgent.
  - [x] The generic script-continuation file is now `ScriptContinuation.kt`; script continuation/frame/block types use generic script names because they are shared by town services, speak, priest, inventory item-use, and mission resumes.
  - `src/main/kotlin/rylween/game/RylweenGameCatalog.kt` has been deleted. Current-game handles are generated from authored declarations/usages under `build/generated/gameCatalog/kotlin`; reusable main packages still do not import `rylween.game`. Default slugs needed by reusable builders/runtimes live internally in `core/src/main/kotlin/rylween/dsl/DslDefaults.kt`.
  - Historical result: the old `RylweenEngine.kt` was first split by role into engine, frontend, runtime, and save packages, and those pieces are now source-reference material inside `scratch/legacy-archive.zip`. Active production code no longer composes that session graph.
  - The current frontend boundary is the runtime. It owns ANSI rendering and host stepping directly from compiled tables, while `:terminal` provides only shared escape/input primitives.
  - [x] Collapse duplicated menu rendering in line-terminal adapters. Static options now come from authored `InputBindingModel`s or runtime-generated keys and pass through shared `TerminalTextRenderer`/`TerminalPort` prompt helpers. The frontend suppresses the generated `Options: ...` footer when the prompt already contains all key markers, so prompts such as `(P)ay/(N)one` stay single-sourced.
  - [x] Keep command help DRY. `visuals { staticScreens { commandHelp(...) } }` is only a static-screen route; live `?` help for wilderness, dungeon, and castle sessions is generated from the same typed command binding lists that drive input dispatch. Optional authored `helpPages { ... }` remains an override, not the default copy.
  - [x] Prepare the map/rendering boundary for bitmap frontends. `WorldMapGrid.viewport()` now returns `WorldMapViewport` cells with points, terrain tiles, and center metadata; terminal character-row rendering lives in `TerminalTextRenderer`, and ANSI tile/glyph rendering lives in the ANSI adapter.
  - [x] Replace `native.screen.*` with typed view commands. Authoring now uses `clearText`, `clearMapWindow`, `showBackdrop(ImageId)`, `renderMap`, and `statusLine`; trace/session output carries `EngineTraceEvent.View(ViewCommand.*)`, and the ANSI frontend implements those commands without parsing native request strings.
  - [x] Resolve current-world text through the frontend boundary instead of leaking native placeholders. `townNpcName(...)`, role names, role locations, town names, fuzzy locations, and coordinates now flow through shared terminal world-text resolution used by script events and direct text rendering.
  - [x] Collapse repeated terminal wait/apply input handling and bridge-event rendering. Line adapters share `TerminalInput.sessionOrWait(...)` and `TerminalPort.writeScriptEvents(...)`; script scalar/member lookups are table-driven instead of repeated `when (name)` / `when (field)` branches.

  **Track B/C — Vertical content fill (existing APIs, missing data):**
  - [x] Hunt runner: authored `realm { hunt { ... } }` balance and a reusable hunt session now cover success chance, time cost, food gain, narration, and state mutation.
  - Missions: full multi-battle completion bodies for Kobold/Ogre/Foot of Jazzy/Hobgoblin caravan run through `enterDungeon(...)` + post-dungeon resume; `runs(...)` slots already exist (per G2).
  - [x] Dialogue: role-bearing NPC quest branches now have authored `dialogue { npc(...) { whenState(...) { ... } } }` bodies, generic speak/question dispatch, and compiled scenario coverage for the current branch shapes.
  - [x] Dungeons/castles: Revelation's current C/JSON5 reference shape is implemented as a dungeon-only authored item-find branch, including the exact no-drop-prompt full-inventory rejection; the audit found no separate room-radius model in the current reference. `DungeonSession` runs authored events and post-dungeon continuations, fixed scenario placements and event bodies are covered by compiled playthrough smoke tests, Spectrum recurrence and Tammy rescue-clearing match the reference, and castle `S` saves/resumes castle id, floor, point, room state, and resume step through the runtime snapshot.
  - [x] Static screens: frame/title/help/manual/score/vscore/heroes are declared as `visuals { staticScreens { ... } }` routes; static command help stays a screen-resource route while live session help is generated from command bindings; score/vscore/heroes are runtime-backed reports over loaded state; the Kotlin line and ANSI launchers can now render authored static screens/documents and runtime reports through the frontend boundary with `--screen`.
  - [x] Map: `worldMap(Maps.wilderness) { layer(MapLayers.terrain) { rows(...) } }` replaces the runtime byte-loader default while retaining `WorldMapGrid` as the reusable representation.
  - [x] Camp/rest runner (E4): authored camp balance, item-record protection metadata, food gate, time advance, healing, temperature damage, and ambush handoff are implemented as a reusable runtime session over authored data.
  - [x] Save reader (B9): the strict-runtime snapshot subset now has a parser/reader to pair with the writer, file-backed runtime save/load ports, content compatibility checks, and named save slots.
  - [x] Hut/lodge town services (E3): `Scenes.Towne.hut` runs through the normal scene/speak bridge for wilderness-position NPCs, and bargirl lodging now lives in authored speak content instead of `SpeakRuntime` special-case logic.
  - [x] Score/vscore (G5): score/vscore are runtime reports over `RuntimeSessionState` and compiled item/milestone metadata, not mutable authored text files.

  **Current tie-together queue before the next feature commit:**
  - [x] Rework the dirty hut/lodge patch so `Scenes.Towne.hut` runs through the existing scene framework, wilderness hut entry resumes through the normal service bridge, and wilderness-position NPC lookup stays in reusable speak content.
  - [x] Replace the hardcoded bargirl branch currently sketched in `SpeakRuntime` with authored lodge content: price `party.leader.level * 50`, Y/N prompt, broke/spent text, gold mutation, and per-member HP drain now live in the game DSL question-action script.
  - [x] Add tests that prove hut entry can talk to a wilderness NPC and that bargirl lodging mutates gold/HP from authored content.
  - [x] Castle follow-up: persist in-progress `CastleSession` state through the save snapshot, add authored castle-completion scenario coverage, and route in-castle navigation through the frontend view boundary for castle backdrop/status redraws.
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
      - [x] D1c-scenes. Generate grouped scene handles, mission handles, dungeon ids, and dungeon-event ids; remove dead scene/mission aliases plus the temporary `thunTemple` extension handles.
      - [x] D1c-small-handles. Generate palette colors plus state/party ids; remove dead current-game `KeyActions`, `TerrainTags`, and `Palettes` wrappers.
      - [x] D1c-text. Generate the `Texts`/`T` hierarchy, including text-pattern leaves and legacy id overrides; delete `RylweenGameCatalog.kt`.
  - [x] D2. Remove author-visible implementation vocabulary. Scope: raw slug constructors in normal authoring, generic `native.*` escapes, `let("name", ...)` when a typed/local helper exists, implementation-only prefix/suffix naming, and any remaining placeholder text that describes runtime port status. Exit criteria met for normal game content and scripts; remaining runtime-state fixture vocabulary belongs to D4/B9 save-state work.
    - [x] D2a. Town content declarations now use generated `Towns.*` handles instead of raw `town("...")` slugs.
    - [x] D2b. Tavern rumour content now uses typed `tellRumour(...)` over authored rumour tables; the legacy `towne.rumour` switch scene, `{nameN}`/`{locN}` binds, `rumour.roll()` helper, and bridge raw-scene special case are gone.
  - [x] D3. Finish core runtime parity gaps before widening architecture. Closed as an architecture gate: the core handoff/session gaps that blocked reusable runtime work are gone, and broad parity leftovers are tracked in their owning inventory rows instead of keeping D open forever. Residuals: richer dungeon room/secret modeling lives in G1 only if future reference behavior requires it; castle parity lives in G3; character creation lives in E7; secrets/sysop/debug policy lives in G4; monster-specific behavior hooks should reopen as named content tasks only if future playthrough parity uncovers one.
    - [x] D3a. Mission scripts that call `enterDungeon(...)` now keep a post-dungeon continuation and resume after the dungeon session finishes instead of returning directly to wilderness.
    - [x] D3b. Authored Kobold village, Ogre cave, Foot of Jazzy, and Hobgoblin caravan mission scenes now have full mission-level regression coverage that resumes through child battles and verifies final flags, rewards, milestones, and journal effects.
    - [x] D3c. Priest-spell adapters now run authored spell scripts with full `RuntimeContent`, so Revelation's successful dungeon find-object/take branch can resolve item records and mutate inventory instead of failing at the frontend boundary.
    - [x] D3d. Wilderness, dungeon, and castle sessions now share reusable party-view and inventory-drop sessions. Dungeon/castle utility keys no longer own local placeholder text or separate drop/view implementations; drop uses item-record display names and resource-capacity metadata, so dropping a donkey clamps food through authored capacity rules. Castle W/R/T/P/Q/Z routes through the shared equipment, item-use, and priest-spell child sessions instead of the old unsupported-command placeholder.
    - [x] D3e. Wilderness `J` now runs a reusable party-journal session over persisted journal entries and authored `prompts { party { journalHeader } }` text; terminal and ANSI adapters render that semantic session instead of carrying a "not wired" placeholder.
    - [x] D3f. Wilderness `S` now emits a backend-neutral save step and the terminal/ANSI frontends satisfy it through a `RuntimeSavePort`. The installed ANSI launcher and line wilderness shell can write strict runtime snapshot files with `--save`, boot one with `--load`, and the out-of-moves `S` path saves before quitting.
  - [x] D4. Persist or reject every active runtime mode. Scope completed for the current player/runtime policy: live file-backed snapshot write/load exists for intentionally saveable wilderness, dungeon, and castle flows; unsupported active modes reject before lossy writes; saves carry snapshot-format/content-hash metadata; and `--save-slot`/`--load-slot` provide safe named files under `build/saves`. Future exact snapshots for town/battle/title are optional injection/debug/frontend work, not D debt.
  - [x] D5. Replace runtime source dependencies with compiled content/bundles. Closed for the Kotlin runtime boundary: normal Kotlin play boots compiled DSL content, authored map rows, generated handles, typed scripts, and model records instead of reading JSON5 or `data/map.dat` at runtime. Native bundle emission, broad dense handle ids/string interning, and a non-Kotlin typed script VM are explicitly H1-H4/H10 work, not D debt; H7 has closed the runtime script-root identity piece.
  - [x] D6. Finish the replaceable frontend boundary. Closed for the D burn-down: ANSI, line-terminal, and headless scenarios consume semantic session inputs plus text/view/state events; live help is generated from bindings; script and battle visuals cross `ViewCommand`; map viewport rendering is view-neutral; and save steps route through `RuntimeSavePort`. Remaining classic-layout polish, bitmap/server/C adapter breadth, and richer render events live under H8/H9.
    - [x] D6a. Standard battle visuals now cross the same frontend boundary as scripted scene visuals: authored encounter banners emit `ViewCommand` image events from `BattleSessionStep.Opening`, the game DSL declares the standard victory image, victory messages carry semantic clear/show-image commands, and terminal/ANSI frontends render them through `TerminalPort.writeViewCommand` rather than local battle-specific image shortcuts.
  - [x] D7. Consolidate remaining naming and duplication debt. Closed for known D debt: generic script-continuation naming is in place, terminal/script/runtime binding tables are shared, trace-only runners are separated from stateful execution, old bridge `when(name)` tables are gone, and player-facing temporary fallback strings were removed. Future duplicate-code sweeps are normal maintenance.
    - [x] D7a. Removed the temporary player-facing runtime fallback object and the town "service is not implemented yet" prompt; missing hunt rules, town service bodies, hut scenes, and castle content now fail as content consistency errors instead of leaking port status text. Standard conversation menu headings and action labels now live in the reusable standard-conversation model rather than being duplicated in the script bridge, and terminal child-session handoff plumbing is shared across camp, town, dungeon, wilderness, and castle adapters.
    - [x] D7b. Renamed the generic resumable script continuation model out of town-service vocabulary, renamed the terminal script bridge API to `ScriptBridge`, split trace-only child runners and script scalar/member bindings out of `TerminalScriptBridge.kt`, and removed the remaining script-bridge `when(name)` scalar dispatch tables from the bridge file.
    - [x] D7c. Moved shared authored-script runtime/member/party/realm binding tables from the terminal frontend into `rylween.runtime`, rewired wilderness mission requirement evaluation and combat scripts to use them, and table-drove battle-specific monster fields, battle flags, battle queries, and monster flags so `when(name)` dispatch is gone from main runtime/frontend sources.
  - [x] D8. Make validation author-facing and exhaustive. Closed for the current authored model: validation aggregates structured diagnostics, checks flag transitions, source-maps generated handles, validates terrain/text/script/mission/dungeon references, and reports startup/save-load failures as game concepts.
    - [x] D8a. Added structured `ValidationDiagnostic`/`RylweenValidationException` output and `collectAuthoredContentDiagnostics(...)`, so authored content validation reports section paths and aggregates independent content failures instead of stopping at the first subsystem. Story-state references are now checked through scripts, mission requirements, dialogue branches, rumour exclusions, and runtime/save-state party flags; script screens and journal text validate role/town bindings through the same text checker used by speak/rumours.
    - [x] D8b. Added inferred story-flag transition validation. Dialogue `whenState(...)`, mission `requires(...)`, and positive `whenever(flagState)` branches now establish a source state for same-flag writes, and validation rejects regressive transitions such as moving an accepted quest back to none while still allowing cross-flag dependency updates.
    - [x] D8c. Generated game-catalog handles now keep source offsets while scanning `RylweenGame.dsl.kts`, so missing/unclosed declaration blocks, duplicate declarations, generated-id collisions, invalid terrain map codes, unknown scene groups, flat scene references, and text leaf/group conflicts fail with `game/src/main/dsl/RylweenGame.dsl.kts:line:column` diagnostics before Kotlin reports errors from generated code.
    - [x] D8d. Terrain/text binding validation now checks that terrain transitions, terrain-triggered missions, and runtime-state `lastTerrain` values bind to declared terminal terrain styles and map tile codes where needed. Runtime-only text parts such as shop price, random-offer item/price, selected item name, hunt member values, dungeon handoff fields, dungeon depth, and conversation NPC name are now accepted only in text blocks rendered with the matching runtime binding context.
    - [x] D8e. Mission and script validation now catches semantic runtime-content cross references before play: dungeon handoff and cave-discovery ops require authored dungeon scenarios, dungeon random-encounter tiers must have authored monsters, scripted encounters must name authored monsters, nested `run(...)` scene calls must target authored scenes, and milestone predicates/achievements must target declared game milestones.
    - [x] D8f. Startup and save-load diagnostics now use the same authored validation path as compiled content: `--load` snapshots are parsed with `RylweenValidationException` diagnostics, loaded states are validated against current authored content before boot, wilderness launchers reject non-wilderness runtime modes explicitly, unknown startup states/towns list available authored handles, and terminal/ANSI launchers print concise startup failures instead of raw Kotlin stack traces.
  - [x] D9. Create a reusable scenario/playthrough harness. Closed: named terminal scenarios carry fixture metadata, inputs, output/view/image traces, checkpoints, and compiled-game coverage for town services, wilderness, dungeons, Ydarg, battle, save/load, and static screens. Broader adapter coverage belongs under H11.
    - [x] D9a. Added a reusable test-scope `TerminalScenario` harness with named fixtures, start-state/seed metadata, scripted terminal inputs, captured text/view/image traces, and named checkpoints. Migrated the compiled Thun/Reebar/Hobgoblin dungeon playthrough checks plus Kobold/Ogre/Jazzy/Hobgoblin mission-completion playthroughs onto the harness so future parity coverage can add scenarios instead of bespoke terminal-run boilerplate.
    - [x] D9b. Broadened the harness to shared runtime flows: scenario runs now expose reusable output/image assertions, and the wilderness save/out-of-moves save flows, castle entry hook handoffs, battle run escape, first battle round, and battle victory reward/loot checks run as named scenarios with fixture metadata and checkpoint-labeled failures.
    - [x] D9c. Added a compiled-town scenario helper over the same harness and migrated real compiled town/service flows: merchant torch purchase, smith sale, tavern scruffy offer, tavern drink, traveller story, seeded tavern paths, rumour quiet-limit, and the wilderness-to-town-to-wilderness shell handoff. Wilderness scenarios can now inject a stateful service bridge instead of hand-wiring one-off terminal ports.
    - [x] D9d. Added view-command assertions and a compiled-castle scenario helper. Migrated file save/load boot validation plus authored image/view-event checks onto scenarios, added castle navigation/treasure/pitfall/completion scenarios, added battle party-defeat scenario coverage, and fixed the battle runtime so party-defeat hit/death text is emitted before `Finished`.
  - [x] D10. Retire historical port-plan debt once Kotlin DSL/runtime owns the surface. Closed for the Kotlin plan: the C/JSON5 source, data, replay tools, and reference docs now live inside `scratch/legacy-archive.zip` under `legacy/c-json5`; `legacy/c-json5/docs/dsl.md` and `port-plan-incomplete.md` identify themselves as legacy C/JSON5 reference material, `dsl-improvements.md` is the current typed Kotlin DSL/runtime ledger, and `plans/future-directions.md` remains explicitly outside the zero-debt target unless multi-party/MUD work is scheduled.

  **D closeout rule:** D0-D10 is closed. Do not add new work to this burn-down. Remaining implementation belongs to the owning inventory rows: E/F/G for game parity/content, H for compiled bundles/frontends/native replacement, and B9/D4 notes only for save-format maintenance.

### Current game source decisions and open questions

Decisions from the first `RylweenGame.dsl.kts` pass:

- `game/src/main/dsl/RylweenGame.dsl.kts` is the successor game source copied from the archived `legacy/scratch/dsl-next.kts`; the archived `legacy/scratch/dsl-next.kts` remains the small language/prototype lab.
- The Gradle project root is named `rylween` and uses the Gradle 9.5.1 wrapper with Kotlin JVM 2.3.21, declared through `gradle/libs.versions.toml`. Source packages now mark responsibility boundaries: `rylween.dsl` (`core/src/main/kotlin/rylween/dsl`) is the reusable DSL kernel/validation layer; `rylween.compiled` (`core/src/main/kotlin/rylween/compiled`) contains bundle contracts/loaders/emitters; `rylween.runtime` now contains only snapshot and VM diagnostic contracts. The old interpreted ANSI app/session code and old JVM reference runtime are archived outside the active Gradle graph inside `scratch/legacy-archive.zip`, low-level terminal escape/key primitives live in `:terminal`, runtime direct-bundle code lives in `:runtime`, `game/src/main/dsl/RylweenGame.dsl.kts` is the game-specific declaration source, and generated current-game handles live under `build/generated/gameCatalog/kotlin`.
- Keep a hard boundary between DSL implementation and game authoring. Kotlin classes, builders, validators, and generated handles are implementation/compiler scaffolding; the game designer should add game-specific content through declaration blocks such as `flags { ... }`, `monsters { ... }`, `missions { ... }`, `gameProgress { ... }`, and `state { ... }`.
- Adding a new monster, flag, mission, special milestone, text, or similar game-specific concept should not require writing a Kotlin class. The authoring source declares the concept in game terms; the content compiler can generate typed handles from those declarations.
- Common Rylween engine/state concepts such as party state, member stats, inventory slots, positions, and validation helpers may remain normal Kotlin DSL implementation types because authors consume them rather than extend them.
- Common gameplay systems such as battle rounds, fight/run menus, spell selection/casting, item use, reward application, and encounter orchestration should become reusable Kotlin DSL/runtime-library concepts. Game authors should declare the content those systems consume, plus explicit hooks or overrides when needed; they should not hand-write bespoke battle loops for normal monsters or spells. Those reusable Kotlin systems may call a narrow native surface for input, rendering, RNG, persistence, and low-level storage.
- Author-facing scripts should use semantic context predicates such as `context.inDungeon` / `context.underground`, not encoded runtime scalars such as `v("place") eq 1`. Raw continuation locals may exist inside interpreters while a session is resumable, but they are not part of the game-authoring vocabulary.
- Menu and command input should be modeled as semantic choices with reusable input bindings, not as one hard-coded character per action. `InputBindingModel` carries a primary input, aliases, and a display label. Ownership rule: static authored choices own bindings in the model because authors may want aliases, labels, controller mappings, or server input mapping for those choices; runtime-sized choices such as party member numbers, inventory slot letters, monster target letters, and spell-slot digits are generated by the runtime/adapter from current state and immediately translated into semantic session inputs. Current static model-owned bindings include wilderness commands, dungeon commands, castle navigation/treasure/stairs commands, town-entry menu actions, speak/question menu actions, shop main/purchase/sale/interlude/catalog/item choices, random-item offer buy/decline, battle fight/run, battle player actions, and script `menu` options. Session inputs such as `TownSessionInput`, `InventoryItemUseInput`, `ShopServiceInput`, `SpeakInput`, and `BattleSessionInput` remain semantic and do not own terminal keys. Terminal adapters consume reusable `InputCommand` lists where keysets are command-shaped; displaying an `Options: ...` footer is frontend policy, but the labels and key markers come from these authored/runtime bindings through the shared terminal prompt renderer so the same choices are not re-authored per adapter.
- The runtime boundary is backend-neutral: `RylweenEnginePort` plus focused runtime ports such as `RylweenRandomPort` can be implemented by the current C engine bridge, a pure Kotlin JVM runtime, a server process, or a future native executable. The DSL/runtime layer should depend on the port contracts, not on a hard-coded C implementation.
- Content changes should not require runtime-code changes. During migration we may rebuild the JVM project because content and runtime sit in one Gradle build, but architecturally a new item, mission, dungeon, NPC, monster, town, scene, or map row should compile into the game model/bundle and run on the same Kotlin/C/server/native runtime. The only compatibility requirement is save/world compatibility: runtime snapshots should record a game-universe id/version/hash, and loading should reject incompatible worlds or run an explicit future migration. We are not carrying old C/JSON save compatibility as a design constraint.
- A Kotlin ANSI frontend/runtime shell is a useful near-term backend as long as it stays isolated as a replaceable view/input adapter. It should own terminal modes, ANSI cursor/window drawing, asset/text rendering, input decoding, logging/socket fan-out, and file persistence, while consuming the same compiled game model and session APIs as a bitmap frontend, C bridge, server runtime, or headless test runner. It should not contain world-specific game rules. Current shape: the old interpreted/hybrid ANSI product has been archived inside `scratch/legacy-archive.zip` under `legacy/ansi-runtime` and is no longer an active Gradle project. Shared terminal escape/key primitives remain in `:terminal` so the runtime can reuse protocol constants without depending on the classic host. The active playable Kotlin path is `:runtime:installRuntime` plus `bin/run.sh`, which reads `build/compiled/rylween-bundle.json` and runs outside Gradle so Gradle console/progress output does not share the game screen.
- Runtime design is compiled-bundle first and host/view second. Game rules and state transitions should flow from authored DSL into compiled tables and runtime-system rows; the active runtime consumes those tables directly through `WildernessActions`, `ScriptExecutor`, `BattleRunner`, `DungeonRuntime`, `CastleRuntime`, and related host code. Replacing ANSI terminal graphics with another frontend must not require changes to game content, rules, or save state.
- Persistence should converge toward "state is state" rather than a second hand-authored save schema. The current key/value `SaveFile` adapter remains acceptable while it is the shortest safe boundary, but a future typed hierarchical state model serialized directly to JSON or another deterministic data format should be preferred if it deletes parser code and reduces bugs. Do not add this just because serialization is available; add it only when authored initial state, generated runtime world state, account/user state, party state, and global win/reset status become simpler as one explicit data model with clear ownership.
- The old line-oriented terminal adapters are archived reference material. Current tests drive the runtime through installed launcher smoke tasks, test-only replay plans that feed the shipped launcher with `--script`, direct scripts, and bundle/native checks.
- Full playthrough testing should use scripted scenarios over compiled game models: choose a start state, fixed random seed, semantic or terminal-like inputs, and expected checkpoints such as party state, flags/milestones, inventory, current mode, journal entries, and rendered text/event traces. This should evolve like the previous C/JSON5 scenario approach, but without baking test-only rules into runtime code.
- Small visible service beats are game semantics, not disposable UI polish. C/JSON5 `wait_input`/`ryl_pause` screens, pause delays, one-line confirmations such as inn drinks, and the random aggressive `(<verb> RETURN key)` prompt must be preserved in the authored game content plus reusable runtime hooks. The current 30 pause verbs live once in `RylweenGame.dsl.kts` under `prompts { pauseVerbs(...) }`; Kotlin runtime code may render and consume that model, but must not carry a duplicate current-game list.
- Hand-maintained current-game catalog source is gone; catalog handles are generated from declarations/usages. Runtime code should stay split by reusable state machine/pure state op. When a split exposes a current-game rule such as an item effect, spell cost, dungeon image, or monster behavior, the rule should move to `RylweenGame.dsl.kts` or a compiled model field rather than into another Kotlin singleton.
- The old `WildernessSession.kt` is no longer active source. `RylweenGame.dsl.kts` provides a readable `worldMap(Maps.wilderness) { layer(MapLayers.terrain) { rows(...) } }` declaration so the game boots without binary map loading, and the active runtime consumes the authored map through compiled bundle tables rather than reading `data/map.dat`.
- Current-game script bodies should live in `RylweenGame.dsl.kts` unless they become reusable runtime mechanics. Priest teleport and tavern traveller storytelling are authored game content: their prose, prompts, rolls, costs, and state changes are now inline in the owning spell/service script, while reusable verbs such as `ask.wildernessDirection`, `ask.digit`, `teleportParty`, `choice.member`, and `localInt` stay in the DSL/runtime layer.
- Dungeon migration should start with readable authored event/scenario tables, then wire the runtime to execute them. `dungeons { event(...) scenario(...) }` is the first pass: event names are semantic, weighted duplicate events preserve old probabilities without exposing old numeric ids, and generated cave depth remains owned by the entry effect (`3..16` wilderness cave discovery, `5..18` Earthquake) instead of being duplicated in the random-cave scenario table.
- Dungeon level event clearing is run-local, matching the old loop's local `lvl[]` table. Do not add saved per-level dungeon-cleared state unless a real game behavior needs it. Cross-session reward idempotence belongs in authored story/milestone state, for example special rooms guarded by `achieved(Milestones.fireShield)` or a story flag.
- Dungeon riddles/passwords use the reusable text-input script op. The game file
  declares the story context, accepted strings, and accepted/rejected branches;
  terminal/ANSI/C/server adapters own how keyboard text is collected. This keeps
  the authoring shape close to the original game text without requiring the game
  author to implement input loops or exposing a generic `riddleGate` primitive.
- The random dungeon found-object slot is authored through semantic item pools (`dungeonWeaponFinds`, `dungeonPotionGemFinds`, `dungeonTreasureFinds`, `magicItems`). These pools are the ported meaning of the reference item ranges, but the numeric ranges are not part of the game-authoring API.
- Current Kotlin runtime command:
  `./gradlew emitExternalCompiledBundle :runtime:installRuntime --console=plain` then `runtime/build/install/rylween/bin/rylween --bundle build/compiled/rylween-bundle.json --state fresh-replay --seed 1`, or simply `./bin/run.sh --build --state fresh-replay --seed 1`.
  The archived interpreted ANSI launcher remains in `scratch/legacy-archive.zip` under `legacy/ansi-runtime` for comparison only and is intentionally not part of the active Gradle project graph.
- Layering rule for future passes:
  1. `core/src/main/kotlin/rylween/dsl/Dsl.kt` owns the generic authoring language, immutable models, and AST builders. It must not contain current-world story text, tavern-specific scoring rules, NPC quest prose, or one-off Rylween plot branches. Reusable default handle slugs live in `core/src/main/kotlin/rylween/dsl/DslDefaults.kt`; do not add imports from reusable packages back to `rylween.game`.
  2. Current-game handles are generated from `RylweenGame.dsl.kts`; new game content should move toward declarations there, and any slug or dense-id assignment should be derived by compiler/runtime tooling.
  3. `game/src/main/dsl/RylweenGame.dsl.kts` owns the current game's content and inline script bodies: towns, services, stories, rumours, missions, item effects, and branch text.
  4. Add a typed reusable script fragment or runtime action only when the behavior has a real reusable identity or generic mechanical contract; do not hide one-off game prose or scoring rules outside the authored DSL.
  5. `core/src/main/kotlin/rylween/dsl` owns authoring and validation; `core/src/main/kotlin/rylween/compiled` owns bundle contracts/loaders/emitters; `core/src/main/kotlin/rylween/runtime` owns only snapshot and VM diagnostic contracts; `:runtime` owns the active Kotlin runtime; `:terminal` owns shared terminal escape/key primitives. The archived classic ANSI/session adapters live inside `scratch/legacy-archive.zip` under `legacy/ansi-runtime` and must not be wired back into the active project graph. A C bridge, server runtime, bitmap frontend, or native executable should all be able to implement or consume the same narrow input/render/RNG/persistence boundary.
  6. Tests may use small capability fixtures, but exact full-game parity tests should execute the compiled game model through `RylweenCompiledGame` where possible. Unit fixtures are not a second source of game content truth.
  7. Visible authoring should minimize raw slug strings. Names, display text, categories, prices, visuals, and behavior belong in declaration blocks; slugs and dense ids are compiler/runtime identities.
- Reusable script fragments should wait until a runtime feature needs identity
  across the fragment boundary: persisted continuation call frames,
  debugger/trace locations, compiled content bundle chunks, source maps, or
  C/server/native backends invoking reusable script bodies independently. Until
  then, authored game behavior stays in `RylweenGame.dsl.kts`; do not add a
  string-keyed helper registry.
- `Flags` aliases the existing `Story` flag catalog so scripts can keep the readable `Story.garth.bandits.accepted` form while A2 has a canonical `Flags.*` entry point.
- `Items.none` is an author-facing alias for the current id-0 `blank` item. The prototype-only `garth_cargo` item was not carried forward because it is not present in the current item shards.
- `Images.Scene.*` names the `blitgfx` map-window pictures semantically from `src/ryl.h` and `world.json5`'s `enums.pic` table, with comments preserving the reference tile filenames. Avoid `tile01`-style handles in authoring code.
- `Images.Static.*` is reserved for full/static ANSI assets under `game/src/main/resources/ansi`, separate from map-window scene pictures under `game/src/main/resources/tiles`.
- `NativeOps.*` is a typed catalog in `Dsl.kt`, but in `.kts` it must currently be represented as a class plus top-level value rather than a Kotlin `object`; script objects that call script-level helpers trigger a Kotlin script capture error.
- DSL implementation code should be idiomatic Kotlin: prefer expression-bodied helpers, `buildList`, `apply(...).build()`, and collection operations over temporary variables and imperative loops. Authored game blocks should stay readable as game notes.
- B7 ownership decision: `fireshield`, `bracelets`, `mastercrown`, `jazzyfoot`, and `silverkey` are persisted game-state milestones. Each current milestone also projects into the special-inventory view, but that is a property of the milestone, not a hard-coded rule that every win milestone is a special item.
- `silverkey == 2` in the reference C engine is not a Silver Key item state. It becomes a separate `Milestones.ydargVictory` completion marker so "has Silver Key" and "finished Castle Ydarg / won" are distinct states.
- The future engine should validate winning configurations at startup and save-load time: a party is in a winning configuration only when all content-defined required milestones are achieved and the configured completion marker is present.
- First-pass B1-B9 runtime-state shape now distinguishes `stateFixture(...)` from strict `runtimeState(...)`. Fixtures can stay compact; runtime snapshots are validated as fuller save-shaped records with explicit realm runtime fields, member counts, full inventory slots, attack-today records, declared NPC visit bits, and literal journal lines where needed.
- Equipment is modeled separately from slotted inventory in member `equipped { weapon; armor }` blocks. This mirrors current save coverage, where equipment is per member, while preserving the rule that milestone-projected special items are not slotted inventory.
- Reference special-field import mapping: `fireshield = 1`, `bracelets = 1`, `mastercrown = 1`, `jazzyfoot = 1`, and `silverkey = 1` map to their matching ownership milestones. Reference `silverkey = 2` maps to `Milestones.ydargVictory`, not to an upgraded Silver Key item state.
- Runtime save emission now has a first deterministic writer for strict `runtimeState(...)` snapshots. It emits pasteable Kotlin DSL with literal handle constructors such as `stateId("...")`, `partyId("...")`, and `item("...")`; saved flag values use `runtimeFlagState("flag.slug", "native_value")` so exact runtime data does not require generated authoring handles.
- Member stat pools use one authoring/readback phrase: `hitPoints = 68 of 68` and `mana = 0 of 27`. `parseRuntimeStateSnapshot(...)` still accepts older `manaOf` saves as a compatibility token, but the current DSL surface exposes only `of`.
- First-pass combat-system shape is `combat { standardBattle { ... } }`. It names the reusable battle framework from the game author's point of view: opening flee rules, encounter banners, player actions, monster-round policy, and the reward bundle to apply. The actual XP/gold/loot declaration now lives in `rewards { reward(Rewards.standardBattle) { ... } }`, so the reusable battle loop can point at a shared reward bundle instead of embedding one-off reward rules.
- Loot tables use semantic item pools such as `ItemPools.battleSpoils` and `ItemPools.magicItems`. Numeric item-id ranges from the C/JSON5 reference are porting audit material only; they should not live in authoring or runtime formats. The implementation model also records the narrow native services the standard battle framework needs: keyboard input, rendering, RNG, combat context storage, inventory storage, party-state mutation, and text formatting.
- Current battle loot pool membership is carried forward as semantic item lists, not as persisted/generated numeric ranges. Authors should see pools such as `ItemPools.battleSpoils` and `ItemPools.magicItems`; migration audits can compare against old C/JSON5 outside the runtime model if needed.
- First-pass dialogue shape is `dialogue { npc(Npcs.garth) { whenState(...) { ... } } }`. One-off NPC interactions such as Garth's merchant reward can keep their text, reward, prompt/drop handling, and flag transition inline in the branch; reusable screens/text remain a separate future text-asset concern and do not force a scene id for every dialogue branch.
- First-pass speak dispatcher shape is `speak { standardConversation { ... } }`. It declares the reusable NPC selection, visited-bit recognition, branch-selection policy, C/Q/R/B top-level menu, L/M/P/N question submenu, well/weather/health chitchat roll, once-per-conversation rumour behavior, and the narrow native services the reusable runner needs. Game authors still provide per-NPC branch content in `dialogue { ... }` rather than writing the conversation loop.
- Reusable speak text now has its own `speak { textTables { ... } }` shape for shared screens, chitchat prompt/reply tables, question reply tables, and town rumour rolls. This is intentionally separate from inline mission dialogue: table text is reusable system content, while one-off NPC quest branches can remain local structured blocks.
- `SpeakSession` is the first reusable runtime runner for the standard conversation shape. It selects NPCs in the requested building at the current town from realized runtime placements, falls back to fixed character declarations when a fixture has not realized NPC state, marks per-party visited bits, automatically runs the first matching `dialogue { npc(...) { whenState(...) { ... } } }` branch before showing the reusable menu, runs C/Q/R/B plus L/M/P/N menus, resolves chitchat and question replies by NPC job, applies once-per-conversation rumour fallback, and filters standard town rumours with `excludeWhen(...)`. Dialogue branches execute through the same stateful script runner as town services, so rewards, flags, milestones, waits, and drop prompts can resume without replaying side effects. The service bridge can now run or resume the source `Speak` statement as a nested session, and service scripts can call `tellRumour()` to draw from the same standard rumour table. Runtime text rendering resolves role names, realized/fixed role locations, town names, and deterministic fuzzy town coordinates. Explicit branch leave-vs-menu control and richer rumour selection policy are still pending.
- `writeText(...)` is the script action for text-window output, and `showTextAndWait(...)` is the write/wait/clear page action. `screen(...)` is reserved for reusable/static screen declarations such as static reports and speak text-table pages. `line(...)` and `lines(...)` are reusable text-building extensions. `TextLineSink` is the shared receiver for structured text in screens, menus, rumours, shop prompts, random-item offers, dungeon entrance prompts, and other future text-bearing blocks, so helper-specific text builders should expose domain parts such as `itemName` or `price` without reimplementing their own line DSL. Reusable text tables use structured `lines { line(...) }` blocks when the content benefits from one line per source row, and `lines("...", "...")` as a compact equivalent for short exact reference rows.
- Dungeon entry is modeled as a generic `enterDungeon(Dungeons.*, levels = n)` script operation that a Kotlin/server/C backend can interpret as a runtime handoff. The repeated E/L prompt shape lives in the reusable DSL as `dungeonEntrance(...)`, while the game source supplies the dungeon handle, level count, and prompt text. Dungeon handoff now starts a `DungeonSession` that builds a fresh per-run level event table, and mission-triggered dungeon handoffs carry a post-dungeon script continuation so the original `clearMapWindow`/`renderMap` tail operations resume after leaving the dungeon. Those continuations now persist in strict runtime save snapshots when the active dungeon is saved.
- Mission journal updates are now first-class script actions via `journal { ... }`. Authored scripts should use typed text refs such as `location(Locations.nolrimCrypt).coordinates` instead of placeholder strings like `{dx5}` or generated numeric binding tables. Use `showTextAndWait(...)` for "show this screen, wait for input, clear text"; game-specific prose stays inline in the owning scene or branch unless it is reused as declared text.
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
- Tavern traveller storytelling is current-game content, not DSL kernel functionality. Its exact text, scoring thresholds, Bard/charisma bonuses, liar penalty, `waitInput`, and charisma adjustments now live inline in the tavern script. The stable identity boundary is the containing authored script root, recorded by `ScriptRootRef` for continuations/debug metadata. If the shape becomes reusable across Rylween worlds, introduce a parameterized generic contest helper; do not put one world's story body in `Dsl.kt`.
- Random-item offers and normal shop inventory purchases reuse the same reward overflow policy vocabulary as mission rewards, and default to the reusable drop-something-and-place flow. Exact reference "can't carry it" rejection remains an explicit opt-out for byte-parity fixtures.
- Smith and merchant are modeled with `shopService(...)`: authoring declares the service image, local text, optional chance interlude, buy catalogs, resource buys, sell policy, and bargain policy; Kotlin/runtime owns the shop loop, menu dispatch, item-cost lookup, dynamic level-based prices, first-free-slot placement, and prompt/drop overflow.
- The first runnable shop runtime is `ShopServiceInterpreter`, a pure Kotlin transaction interpreter over immutable `PartyState`. It executes semantic buy/sell requests from a `ShopServiceModel`, including item-cost lookup, level-based merchant food/potion prices, torch/food caps, first-free-slot placement, prompt/drop overflow, merchant sell policy, and smith haggle math. It stays independent of concrete terminal, C, server, or native UI implementations.
- `ShopServiceSession` wraps the transaction interpreter in a backend-neutral prompt state machine: intro, main menu, buy item, quantity, pay/decline, drop-slot overflow, sell slot, sale quote, sale accept/decline, and leave. `ShopServiceTraceRunner` consumes scripted `ShopServiceInput` values for tests/traces; `ShopServiceTerminalAdapter` consumes line-oriented `TerminalPort` input over the same session contract. C and server adapters should translate their own input events into this same semantic session shape.
- Terminal-style adapters must not silently turn invalid or missing input into gameplay choices such as "no" or "leave". Their `runUntilBlocked(...)` result returns the unchanged unfinished session with `TerminalRunStatus.Waiting`, optionally carrying a `TerminalInputRejection`; an outer demo runner may reprompt, quit, or surface the rejection depending on frontend policy.
- `RuntimeSessionState` is the first runtime wrapper for live execution. It owns the current `RealmState`, parties, and realized NPC placements, and exposes `activeParty` from `realm.activeParty`; town, shop, equipment, service runners, and text rendering now update or consume this wrapper rather than carrying only an isolated party and an hour integer.
- `realizeNpcPlacements(...)` is the first Kotlin cold-start NPC realizer. `fixed(...)` placements are copied into runtime state, `randomPlacement()` distributes NPCs across wilderness plus declared towns with capacity balancing and random inn/tavern assignment, `fixedNear(...)` places authored wilderness NPCs relative to named towns with terrain validation, and `unplaced()` models inactive reference table slots explicitly. Exact runtime starts consume and persist `rngState`; authored fixtures with `rngState = null` use a deterministic fixture seed and restore `null` afterward. The wilderness point policy is pluggable; once map/terrain content is in the DSL it should receive the passable non-settlement point set and apply the generated hut tile mutation.
- `RylweenKotlinEngine` was the first small pure-Kotlin boot facade over compiled content. It can create an initial runtime from a full `StateModel` without dropping party position, realize NPCs, boot `TownSession` and `WildernessSession`, and expose matching speak/text runtime content. The old line-oriented town/wilderness shells that drove those sessions are now archived inside `scratch/legacy-archive.zip` under `legacy/ansi-runtime`; current playable work is the direct runtime in `:runtime`. C, server, terminal, and future native runtimes should feed or consume the same state/session contracts rather than forking game rules.
- `WorldMapGrid` is the first Kotlin world-map runtime object. It now normally receives authored map rows from the compiled game model while preserving the same world-coordinate convention as the 1992 C implementation: source row 1 is world `y = 1`, while world row 0 stays empty. It exposes passability, town/hut/castle settlement checks, NPC-placement point selection, terminal viewport rendering, and generated NPC hut projection after `realizeNpcPlacements(...)`. The runtime contract stays backend-neutral; byte-map loading is migration support only.
- `wilderness { events { ... } terrainTransitions { ... } }` is the first authored wilderness event table in the game DSL. It carries the current 3% cave / 91% quiet / encounter remainder split, 50/50 weak-vs-wild encounter tier split, random cave depth band 3..16, cave entry cost 10, cave prompt/no-moves text, and the current terrain-transition narration texts. The runtime keeps terminal/server/native adapters out of the rules: a successful move dispatches through `WildernessSession`, which can produce `CaveFound`, `DungeonHandoff`, a nested `BattleSession`, or terrain-message steps. Authored fixtures with `rngState = null` intentionally stay quiet; exact/runtime snapshots with seeds exercise random wilderness events. `freshReplay` now carries `rngState = 1L` so the runnable wilderness shell reaches seeded terrain/cave/encounter dispatch by default, while tests can still override to `null` for quiet fixture behavior.
- `TownSession` is the first top-level town runtime shell. It filters the C/S/I/M/T/L menu to services available in the current town, checks reusable service hours from `runtime.realm.hour`, routes scenes containing the source `ShopService` statement into `ShopServiceSession`, resolves other service scenes into `NativeService` handoff steps, and runs `TownLeaveEquipmentSession` before final town exit. `TownSessionTerminalAdapter` composes the existing shop and equipment terminal adapters, so nested invalid input still returns a waiting session instead of defaulting a choice.
- `ScriptBridge` is the resumable script-execution seam for service scenes, speak branches, mission resumes, priest effects, and inventory item-use. `StatefulScriptBridge` consumes terminal menu/member/wait/reward-drop input, executes the current loop/if/switch/local-int/for-each-member subset, and mutates party/member/story state such as gold, hit points, mana, charisma, death state, member count, rested hours, flags, and milestones. It can pause a script at `encounter(...)`, hand the outer adapter a `BattleSession`, and resume the script after the battle finishes. `stop(...)` inside a loop now stops the current branch/iteration and returns to the loop, while `breakLoop(...)` exits the service loop. Blocked scripts return a `ScriptContinuation` containing runtime state, locals, unknown realm scalar storage, nested sessions, and a cursor stack made of script block references plus next-op indexes, so a script can resume without replaying prior side effects. Known realm integer fields such as hour, minute, day, banishment, last-played day, and party-member max now mutate the persisted runtime realm; trace-only child runners and scalar/member binding tables are split out of the stateful bridge file.
- Church H/E/R/L is covered as a runnable authored-service fixture for healing, broke payment, energizing, raising, and stop-then-return-to-menu behavior. Inn supper, drinks, lodging, and chitchat are covered through the top-level town terminal adapter, including local service cost, pay/broke branches, `waitInput`, living-member clamped healing, rested-hours mutation, dead-member exclusion, persisted `realm.hour` mutation, nested speak sessions, and visited-bit updates. Tavern speak/question, standard-rumour, scruffy random-item purchase, drink order, deterministic traveller-story loss, seeded traveller-story score, seeded scruffy item draw, seeded rumour selection, role/town-bound rumour rendering, and rumour quiet-limit branches now have first runtime coverage, including drop-slot resume without replaying the purchase choice. `RylweenCompiledGame` lets tests run against the authored `nextRylween` model; current compiled-game coverage enters the real Thord church, merchant torch purchase, smith sale, tavern scruffy-offer/order/story/rumour-limit paths, seeded tavern scruffy/story paths, compiled role/town rumour rendering, realized-NPC speak lookup, direct Garth reward dialogue branch, Garth reward through normal speak selection, and weak/wild battle sessions with attacks, spells, item use, monster rounds, victory, and defeat. Unsupported service concepts such as full traveller-story score distribution coverage, explicit dialogue branch leave-vs-menu control, and broader realm/session transitions are still traced or held as future bridge/interpreter work.
- `ReferenceRylweenRandom` mirrors the C `ryl_random` xorshift64* stream and treats `realm.rngState` as the current stream state for exact-save continuation. Runtime fixtures with `rngState = null` intentionally keep deterministic lower-bound/first-entry selection for local choices and keep random wilderness event dispatch quiet, so authored examples can stay stable until they are promoted to exact snapshots.
- Merchant gear/resource effects are explicit but not overstated. `Items.torch` purchases mutate the torch resource and cap at 80; dungeon descent owns torch spending as a runtime mechanic instead of exposing an unused generic script helper. `Items.ration` purchases mutate food and cap at `150 * (donkeys + 1)`. `Items.donkey` remains an inventory item whose carried count expands food capacity; dropping or replacing a donkey clamps food to the remaining capacity, matching reference `drop()` behavior. `Items.tent` and `Items.blanket` expose reusable camp-gear protection metadata for the future rest runner: tent selects the tent backdrop and protects both low and very-low temperature bands, while blanket protects only the low band. Tent is not consumed.
- Equipment ready/off now has pure Kotlin state mutations, prompt command metadata, and reusable prompt sessions. `equipFromInventory(memberNumber, slot, kind)` swaps a valid inventory weapon/armor with the member's equipped item, `unequipToInventory(memberNumber, kind)` moves equipped gear to the first free slot, and `EquipmentCommand.ReadyWeapon/ReadyArmor/RemoveWeapon/RemoveArmor` exposes the member and inventory filters a frontend should ask for. Optional `equippableBy(PcClass...)` blocks on authored weapon/armor records restrict which classes can ready that equipment; omitted restrictions mean every class can equip it, which matches the current reference item data. `EquipmentPromptSession` covers direct ready/off actions; `TownLeaveEquipmentSession` asks the reference leave-town weapon prompt, then armor prompt, only when the party carries a readyable equipment kind for a living member. Trace and terminal-style adapters both feed semantic inputs into those sessions. `InventoryItemUseSession` lists all slotted inventory items; selected weapons and armor enter the ready flow instead of requiring fake item-use effects. `BattleSession` consumes generated weapon damage and armor class from readied equipment for player attacks and monster melee.
- When porting more scripts, look for the old `let("slug", ...)` and `bind("slug", ...)` shapes. Numeric locals should usually become hidden `localInt(...)` refs; repeated string placeholders should become typed text parts or reusable text-table binding metadata rather than ad hoc author-maintained names.
- Inline text should carry typed values directly, e.g. `line("- That'll be ", price)` or `line(pronoun(member, PronounCase.Object), ".")`, instead of embedding `{cost}` or `{pro_obj}` placeholders in local string literals. Placeholder strings are still acceptable for imported reference `TextId` tables until those records get typed binding metadata.
- Service-local conversations should be typed script ops, not raw native trampolines. Inn guest chitchat now records `speak(Buildings.tavern)` as a reusable conversation action that a Kotlin runtime, C bridge, server, or native executable can implement through the same engine port.
- `breakLoop(...)` and `stop(...)` are explicit script-control signals. Menu, switch, and if bodies propagate those signals to their surrounding script runner; validation rejects `breakLoop(...)` outside a loop.
- Reference `main` C code confirms the original NPC layout: slots 1-24 are generated random people, slot 33 is Marcus the fixed Hermit near Ollk, slots 34-35 are fixed tavern women, and slots 36-39 are Alex/Galdon/Kosch/Ufarsin as merchant wilderness records with implicit `0,0`. The DSL should not expose implicit wilderness-origin placement as an authoring concept; characters should be declared as `randomPlacement()`, `fixed(...)`, `fixedNear(...)`, or `unplaced()`.
- `Items.*` handles no longer carry reference/source ids. Item pools are semantic item lists, and the new runtime/bundle/save formats should not depend on reference numbering.
- Confirmed design decision: item ids should be compiler/runtime identities only, if needed at all. Game authors should describe the item in an `items { ... }` declaration and refer to a typed symbol afterward; raw `item("slug")` / `itemPool("slug", ...)` calls are scaffolding/implementation escape hatches, not the target authoring style.
- Item records now describe what an item is: kind, display name, cost, storage behavior, and equipment combat values. They do not describe what using the item does.
- Potion effects are authored as ordinary `itemUse(...)` scripts. Red Potion asks `Who?`, filters living members, consumes the active item, and heals HP; Blue Potion asks `Who?`, filters living members, consumes the active item, and applies poison damage/death state; Amber Potion asks `Who?`, filters spellcasters with alive acceptance, consumes the active item, and restores mana. A future Purple Potion should be added by declaring the item in `items { ... }` and adding an `itemUse(...) { inventory(); effect { ... } }` body, not by adding Kotlin enum cases or C/native branches.
- Item use is wrapped in explicit metadata: usable contexts, consume policy, and an effect body. `InventoryItemUseSession` runs non-combat `itemUse(... Inventory ...)` scripts through the same resumable script machinery as service/mission flows, including `consumeActiveItem()` for the selected slot. The current battle magic items use reusable consume policies: most consume before use, while the resurrection gem is script-controlled so a failed living-member target can retry without consuming the item.
- Text/screens have two paths: reusable records can stay as `TextId` handles, while one-off dialogue can be written inline as structured text blocks inside the owning dialogue/mission branch. Do not mint a global text or scene id just to hold a one-off NPC response. `writeText` displays a `TextBlock`; `screen` declares reusable/static screen routes; `line`/`lines` stay the shared construction vocabulary available to reusable text receivers. The Borin gift scene now uses inline text for its local narration.
- Reward bundles and scripted rewards now name item-placement semantics separately from loot membership. Standard battle uses first-free inventory slot placement and exact reference behavior `SkipIfInventoryFull`; scripted rewards use the same typed policy shape for overflow prompts without custom inventory loops in authored content. `RewardGrantSession` applies gold and slotted items, prompts for drop-slot overflow, resumes without replaying already-applied gold, and leaves special inventory projections to explicit milestone state such as `achieve(Milestones.silverKey)`. The Borin gift reward now grants gold and semantic gem handles without numeric ids.
- Standard battle framework rules now carry the player/monster combat tunables from `data/world.json5`: attack hit bonus `1`, attack damage spread max `4`, confusion divisor `3`, attack HP divisor `12`, battle-cry weapon-skill penalty `4`, defend weapon-skill penalty `2`, defend rest threshold `5`, monster-melee hit bonus `1`, damage divisor `6`, damage spread max `9`, and damage minimum `1`. These are reusable Kotlin DSL/runtime rules, not game-specific custom battle loops.
- `BattleSession` is the reusable combat runtime slice. It is backend-neutral: terminal, C, server, and future native adapters feed semantic inputs and render `TextBlock`s, while the session owns encounter allocation from authored monster tiers, seeded foe count and HP rolls, runtime mode, flee success/failure, mandatory-run denial, per-member action prompts, attack/defend/battle cry, mage spell choice and cost payment, combat item-use choice/use, monster melee/spell turns, party defeat, victory rewards, loot, and level-up application/narration. The game declaration keeps game-specific behavior author-facing: `standardBattle { ... }` declares reusable rules and prompt/menu text, `formulas { ... }` declares encounter/reward/level formulas, `monsters { ... }` declares this game's encounter catalog, and `spell(...)` / `itemUse(...)` blocks declare this game's effects and inline battle text. Combat script text now supports typed party/monster names and pronouns directly, so authored battle prose does not need placeholder strings.

Resolved design decisions:

- Generated catalogs follow authored declarations and source usage rather than JSON5 shard names. Grouped handles such as `Images.Scene`, `Images.Backdrop`, and `Scenes.Towne` are generated compiler/runtime conveniences; game content remains declaration-first.
- Scene slug normalization is owned by generated handles and script-root metadata. Authored scenes use typed generated `Scenes.*` handles, while persistence/debug paths name resumable script roots through `ScriptRootRef`.

### B. Exact runtime state and savegames

- [x] B1. Realm/session state. DSL delta: `state { realm { day/hour/minute, rng, currentMode, currentMap } }`. Native decision: only active frame pointers and transient loop state remain native. Current game source status: first-pass `RealmState` covers day/hour/minute, current RNG stream state, active party, current map/mode/town, banishment, last-played day, and party-member max; `RuntimeSessionState` now carries live realm, parties, realized NPC placements, and realized quest locations for town/wilderness execution and text rendering, and advances `rngState` for seeded runtime draws, including cold-start NPC placement.
- [x] B2. Party identity and position. DSL delta: `party(Parties.main) { position = Wilderness.xy(...); facing = ... }`. Native decision: coordinate storage and bounds checks remain native. Current game source status: party id/player/name, world position, and optional facing are modeled.
- [x] B3. Party resources and counters. DSL delta: `resources { gold, food, fatigue, timeOfDay, upkeepDue }`. Native decision: primitive numeric mutation only. Current game source status: resources cover gold, torches, food, time left, and rested hours; counters cover member count, kills, mail/attack notices, and attack-today records.
- [x] B4. Party members. DSL delta: `member(1) { name; race; class; weaponSkill; charisma; wisdom; health; hitPoints; mana }`. Native decision: field storage and target selection only. Current game source status: member records include identity, class/race/gender, level, stats, HP/mana, equipment, experience, and dead/alive state.
- [x] B5. Derived/depletable stat rules. DSL delta: validation for `hitPoints.max == health` and `mana.max == wisdom`, plus rules that modify max/current values. Native decision: no hidden C recalculation except typed helper ops. Current game source status: validators enforce HP and mana max consistency.
- [x] B6. Inventory slots and equipment. DSL delta: `inventory { slot(1).item(...) }` plus per-member `equipped { weapon = ...; armor = ... }`. Native decision: low-level insert/remove/first-free-slot helpers. Current game source status: slotted inventory is separate from per-member equipment because current save coverage stores weapon/armor per member; runtime snapshots must list slots `0..16`.
- [x] B7. Reference special fields. DSL delta: `gameProgress { milestone(...) { showsAs(...); requiredToWin(); sourcePartyField(...) } winningConfiguration { ... } }` plus `party { milestones { achieved(...) } }`. Native decision: old positional fields are migration-only; native state should store dense milestone ids, derive special-inventory display from content metadata, and treat reference `silverkey == 2` as a separate completion marker. Current game source status: near-winning and winning fixtures exercise required milestones, `Milestones.ydargVictory`, special inventory projection, and generic win checking. Runtime status: `achieve(...)` now mutates party milestones in stateful scripts.
- [x] B8. Journal and visited records. DSL delta: `journal { entry(...) }` and `visited(Npcs...)`. Native decision: persistent string/id list storage. Current game source status: authored states can use compact `visited(...)` and text-id journal entries; runtime snapshots can list true/false NPC visit bits and literal journal lines.
- [x] B9. Savegame snapshots. DSL delta: exact fully expanded `state {}` output with no hidden defaults or helper loops. Native decision: filesystem write/read for new snapshots; old-save import is not required unless explicitly requested. Current game source status: `runtimeState(...)` validation discipline exists, `renderRuntimeStateSnapshot(...)` emits deterministic pasteable Kotlin DSL for strict runtime snapshots, `parseRuntimeStateSnapshot(...)` reads the strict writer-emitted subset back into runtime state, and `FileRuntimeSavePort` gives ANSI/line frontends file-backed `--save`/`--load` plus named `--save-slot`/`--load-slot` over active wilderness snapshots and exact castle/dungeon mode snapshots; new file saves carry snapshot-format, game-universe/version, and content-hash metadata, and load validation rejects unsupported save formats or mismatched universes/builds. Current save policy is explicit: wilderness, dungeon, and castle are saveable because they are player runtime modes in the Kotlin port; town, battle, title, and game-over snapshots reject before writing unless a future injectable-debug or frontend task deliberately adds them.

B1-B9 first-pass notes:

- `stateFixture(...)` is the authoring/test fixture form. It may stay compact and ergonomic.
- `runtimeState(...)` is the future save snapshot subset. Validation currently requires explicit RNG seed, member count, full inventory slots `0..16`, attack-today records for every party in the state, and explicit visit bits for every NPC declared in that state.
- `renderRuntimeStateSnapshot(...)` and `parseRuntimeStateSnapshot(...)` are the first writer/reader pair for that subset. `RuntimeSavePort`/`FileRuntimeSavePort` add the first filesystem policy for the Kotlin frontends: one explicit snapshot target path, strict parse on load, and no old-save importer.
- Old-save reading is no longer part of the near-term DSL design. Reference fields are still documented so current data can be mapped correctly, but the next save/load work should target exact new DSL snapshots first.
- Current C save coverage audited for this pass: realm header (`last_played_day`, party-member max, current session fields from snapshots), locations, NPC placement, party identity, flags, special fields, `pl`, position, resources, inventory, member records/equipment, `attack_today`, `waiting_mail`, `attacked`, `kills`, `visited_npcs`, and `journal`.

### C. World maps, terrain, visuals, and layout

- [x] C1. World metadata and limits. DSL delta: `metadata { ... }` and `limits { ... }`. Native decision: only non-derivable allocation caps should be authored. Current status: compiled content declares universe/version metadata and the remaining max-world-map guard; the old authored max-map-cells and screen-size fields were removed because map rows and layout regions are the source of truth.
- [x] C2. Map dimensions and storage. DSL delta: `worldMap(Maps.wilderness) { layer(MapLayers.terrain) { rows(...) } }`. Native decision: map array dimensions and cell counts are derived from authored rows. Current status: the compiled game authors wilderness terrain rows only; validation rejects missing, blank, ragged, or unknown-tile rows; `WorldMapModel` exposes derived `size`, `width`, `height`, and `cellCount` queries; `WorldMapGrid` preserves the reference coordinate convention for runtime navigation.
- [x] C3. Map cells and layers. DSL delta: typed terrain rows/layers instead of raw `map.dat` bytes. Native decision: source/reference migration may keep legacy data files outside the Kotlin runtime path, but runtime content and tests consume authored map rows. Current status: `RylweenGame.dsl.kts` authors the wilderness terrain layer with `worldMap(Maps.wilderness) { layer(MapLayers.terrain) { rows(...) } }`, and Kotlin launchers boot only from that compiled map.
- [x] C4. Terrain semantics. DSL delta: `terrain { terrain(Terrain.forest, mapCode = ...) { movement; passability; settlement } }`. Native decision: fast predicate tables generated from terrain ids. Current status: the authored terrain catalog owns map codes, passability, movement cost, settlement identity, and random-NPC placement policy; `WorldMapGrid` consumes those semantics for passability, town/hut/castle checks, NPC hut projection, and Teleport openness. Terrain-transition narration remains authored against semantic terrain handles.
- [x] C5. Terrain visuals. DSL delta: terminal glyph/color and future bitmap tile styles live under terrain render styles. Native decision: renderer consumes a style id, not terrain game rules. Current status: terminal terrain glyph/color styles remain authored in `terminal { terrain(...) }` and are validated against declared terrain semantics; ANSI rendering consumes the style table rather than hardcoded tile glyphs.
- [x] C6. Visual assets. DSL delta: `visuals { images { ... } }`. Native decision: asset decoding and backend texture/glyph storage. Current status: full-screen ANSI images and map-window tile images are authored as image assets with resource path and placement; static screens, scripts, battle banners, dungeon/cave/castle backdrops, shop scenes, and camp gear image references are validated against the declared image assets. The ANSI app no longer owns a hardcoded image-resource table.
- [x] C7. Screen regions and layouts. DSL delta: `layouts { region(ScreenRegion.map/text/status, ...) }`. Native decision: cursor/viewport implementation only. Current status: classic text/map/status regions live in `layouts { ... }`, generated `ScreenRegions` handles come from that block, validation checks region dimensions and screen bounds, and the ANSI frontend consumes the layout model while retaining only cursor/window drawing mechanics.
- [x] C8. Static screens and documents. DSL delta: title, help, manual, score display, Hall of Heroes, and ANSI/static screens declared as assets/documents. Native decision: file/asset rendering backend. Current status: `visuals { staticScreens { ... } }` declares frame/title/help/manual/score/vscore/heroes routes, help reuses authored command-help pages, the app packages the document resources, and both Kotlin frontends expose `--screen title|help|manual|score|vscore|heroes` via a shared terminal static-screen renderer. Score/vscore/heroes are dynamic runtime reports when launchers provide loaded state.
- [x] C9. Bitmap visual assets and web tile rendering. DSL delta: reintroduce bitmap asset declarations only when a real frontend consumes them, such as `visuals { images { bitmap(...) } }` plus tile/sprite style metadata for terrain, NPCs, monsters, and scenes. Native/view decision: `runtime-web` and compiled bundles must load, validate, cache, and render those resources instead of treating bitmap declarations as dead DSL. Current status: authored bitmap assets under `visuals { images { bitmap(...) } }` are shipped as classpath resources in the game jar, organized under `bitmap/assets/{scenes,monsters,party/portraits,...}`, the bitmap web server validates every compiled bitmap row for `bitmap/assets/` placement, packaged resource presence, typed placement, and typed transition before serving `/assets/game-image/<id>`, and outgoing bitmap frames include the compiled bitmap image table. The browser client records that table and prefers compiled `/assets/game-image/<id>` scene/portrait resources before falling back to renderer-owned static atlases. Ledger coverage asserts representative service, monster, dungeon, and castle art ids/resources, verifies the Kotlin frame protocol exposes typed image metadata, and rejects arbitrary `blitgfx.*` image inference without a compiled bitmap declaration. The remaining browser-pixel proof is not DSL debt; it is tracked under K1/web-runtime verification.
- [ ] C10. Terrain tags/classes only with a concrete consumer. DSL delta: add `tags(...)` or a clearer terrain-class authoring verb only after a renderer, validator, mission trigger, or gameplay grouping reads it. Native/runtime decision: tags must compile into a table that some runtime path uses; they are not decorative metadata. Exit criteria: at least one authored terrain tag/class is consumed by code and covered by validation/test, otherwise the public builder stays absent.

### D. Text, dialogue, NPCs, and story state

- [x] D1. Text records and local bindings. DSL delta: typed structured text and text refs with context-aware rendering. Native decision: wrapping and final placeholder substitution stay in the frontend boundary. Current game source status: inline structured text can include typed integer/string values directly, local script integers use `localInt(...)`, runtime text parts are validated against the contexts that can render them, and `TextLineSink` shares `line`/`lines` across structured screens, menus, rumours, shop prompts, and random-item offer text. Remaining wrapping/layout polish belongs under H9.
- [x] D2. Pronouns and name bindings. DSL delta: typed binding names and pronoun cases instead of symbolic strings. Native decision: lookup by member/npc id. Current game source status: authored script text uses typed `pronoun(member, PronounCase.*)`, `partyName(...)`, NPC roles, town refs, and location refs instead of `{pro_sub}`, `{pro_obj}`, `{nameN}`, or `{locN}` placeholder strings.
- [x] D3. NPC definitions and roles. DSL delta: `characters { character(Npcs.garth) { name; job; fixed(...); role(...) } }`. Native decision: role lookup and placement storage. Current game source status: all 39 reference NPC names/jobs are declared in the game source, generated handles cover NPC ids/jobs/roles, and the current semantic story roles used by text/rumours are authored.
- [x] D4. NPC placement and visited state. DSL delta: content placement plus mutable `state { npcs { at(...) } visited(...) }`. Native decision: runtime coordinates and visited bitsets. Current game source status: character content distinguishes `randomPlacement()`, `fixed(...)`, `fixedNear(...)`, and `unplaced()`; `realizeNpcPlacements(...)` populates runtime NPC state from declarations; `SpeakSession` and text rendering consume realized placements; visited bits remain per party; authored map rows provide passable non-settlement wilderness points and project realized wilderness NPCs back into the runtime map as hut terrain. Broader NPC-placement/dialogue parity coverage belongs under H11.
- [x] D5. Flags and story progress. DSL delta: typed flag families with explicit states and setters. Native decision: dense flag storage and save serialization. Runtime status: `mark(...)` mutates party flag state in `StatefulScriptBridge`, runtime/save-state validation checks declared flags, inferred transition graph validation rejects regressive same-flag transitions, and strict runtime snapshots persist party flag state.
- [x] D6. Missions and triggers. DSL delta: `mission(...) { randomStep(percent = ...); at(...); onTerrain(...); requires(...); runs(...) }`. Native decision: trigger polling and typed op dispatch. Current runtime status: `WildernessSession` polls authored random-step, fixed-position, realized-location, and terrain missions after successful seeded movement, evaluates typed requirements, persists RNG only for active random mission rolls, and hands triggered scenes to the same resumable script bridge used by services. Authored mission content covers 17 current wilderness missions and seven dungeon-entry prompts/handoffs; mission-triggered dungeon handoffs resume their owning script after dungeon exit; mission-level scenarios cover the Kobold/Ogre/Jazzy/Hobgoblin completion chains.
- [x] D7. Dialogue and chitchat. DSL delta: `speak { standardConversation { ... } textTables { ... } questionAction(...) { ... } }` plus `dialogue { npc(Npcs.garth) { whenState(...) { writeText(...) } } }`. Native decision: NPC lookup/visited bits, input, rendering, RNG, and text formatting stay narrow services behind a reusable runtime conversation runner. Current status: role-bearing quest NPCs have authored dialogue branches; the standard conversation shape owns C/Q/R/B and L/M/P/N menus, first-matching branch dispatch, visited recognition, chitchat/question replies, once-per-conversation rumour fallback, no-NPC/no-rumour fallback screens, and bargirl lodging as an authored question action. `SpeakSession` runs realized/fixed/wilderness NPC selection, visited-bit mutation, dialogue scripts, question-action scripts, seeded chitchat/rumour selection, role/town text rendering, and nested service continuations. Full branch parity coverage belongs under H11.
- [x] D8. Rumours. DSL delta: rumour tables with anonymous inline entries by default, semantic `Rumours.*` only for externally referenced/gated entries, role/town text refs, and optional `excludeWhen(...)` filters. Native decision: RNG selection plus filtering against typed party state; old C switch positions stay out of authoring. Current status: `RumourTables.standardTown` carries the current town-rumour content, authoring uses role/town refs rather than numeric selection ids or `{name15}` placeholders, `excludeWhen(...)` gates recovered/closed rumours, and seeded standard rumours render through the same terminal world-text resolver used by `SpeakSession` and `tellRumour()`. Richer rumour weighting/policy is future content polish, not D debt.
- [x] D9. Retire or justify wilderness-only randomized NPC placement. Closed: the no-caller `RandomizedWilderness` model/runtime branch and compiled-loader support were removed. Wilderness-only generated placement is still expressible through a normal `randomPlacement { wilderness(...) }` policy if real authored content ever needs it, but there is no separate no-effect default authoring concept or compiled/runtime variant.

### E. Towns, buildings, wilderness, and daily actions

- [x] E1. Towns and buildings. DSL delta: `towns { town(Towns.ollk) { name; position; taxPercent; services { ... } } }` plus `townSystem { buildingServices { ... } entryTax { ... } }`. Native decision: current-location state, generic enter dispatcher, and semantic tax-payment choice. Current game source status: the four current towns are declared with exact names, coordinates, tax rates, settlement class, and service availability from `data/world.json5`; reusable building service records carry exact service hours and scene targets; the high-level entry-tax threshold, pay/leave bindings, and tax prompt text are authored. Runtime status: wilderness town entry starts `TownSession`, high-level parties see the authored tax preamble, pay deducts the authored town percentage, and decline returns without entering the town menu. Closeout coverage locks exact town data, service lists, service hours/scenes, and tax pay/decline behavior.
- [x] E2. Keymaps and menus. DSL delta: typed semantic input bindings for authored/static choices, allowing multiple inputs per choice. Native decision: keyboard input and dispatch execution. Current game source status: the town-entry keymap is modeled under `townSystem { menu { church(); smith(); inn(); merchant(); tavern(); leave() } }`, preserving C/S/I/M/T/L order and leave-menu semantics; script menus now have reusable choice helpers and explicit `breakLoop`/`stop` propagation semantics. `wilderness { keys { ... } }` declares the current 8/2/4/6 plus arrow-alias movement, Space/S/W/R/!/J/?/H/V/T/P/E/L/C/Z/Q/X actions, E/L cave prompt, S/Q out-of-moves prompt, and Y/N quit confirmation. `DungeonKeysModel` declares the first dungeon-loop D/A plus shared ?/L/T/P/W/R/Q/Z/V bindings for the reusable dungeon runner. `CastleKeysModel` declares in-castle navigation, treasure, and stair-offer bindings. Wilderness, dungeon, and castle runtime sessions expose typed `InputCommand` lists that both dispatch input and generate bounded `?` help pages, eliminating the old parallel help copies. Town-entry, town-tax, speak/question, shop main/purchase/sale/interlude/catalog/item, random-offer, battle fight/run/action, wilderness, dungeon, castle, and script menu options now store `InputBindingModel` rather than a bare `Char`, while `.key` remains a compatibility view of the primary key for traces/tests. Terminal-style adapters consume those bindings through `readChoice(...)`; dynamic member/inventory/target/spell prompts still generate their key ranges from current state. Runtime-owned Y/N prompts use the shared `StandardYesNoKeys`/`readYesNo()` Boolean path, while authored scripts use `choice.yesNo(...)` to branch on the result. Non-terminal adapter consumption of the same bindings is H8/H11 frontend breadth, not E debt.
- [x] E3. Shops and services. DSL delta: smith, merchant, inn, tavern, church, hut, and lodge as building scripts/tables. Native decision: menu input/render primitives, reusable price/item metadata, first-free-slot placement, drop-prompt overflow UI, resumable script continuations, and visible wait/pause prompts. Current game source status: town building services for church, smith, inn, merchant, and tavern now have scene targets and exact opening hours; church has a typed H/E/R/L script, inn has typed supper/drink/lodging/chitchat, smith and merchant have `shopService(...)` catalogs/sell policies plus runnable transaction interpreter/session, scripted trace adapter, and terminal-style adapter, tavern has typed scruffy random-item offer plus speak/story/rumour/order script, and hut runs as a normal scene that can invoke speak for wilderness-position NPCs. `RylweenKotlinEngine` can still boot compiled content into reference town/wilderness sessions with realized NPCs, while the old terminal shell that drove those sessions is archived inside `scratch/legacy-archive.zip` under `legacy/ansi-runtime`. `TownSession` and wilderness hut entry dispatch available services/scenes from `RuntimeSessionState`, entry tax, reusable shop sessions, resumable script bridge handoffs, leave-town equipment prompts, nested speak sessions, nested random-item offer sessions, and scripted reward grants. `StatefulScriptBridge` executes service menus/member prompts plus core party/member/realm/story/RNG mutations without replaying side effects after blocked input; authored church H/E/R/L, inn supper/drink/lodging/chitchat, tavern speak/rumour/scruffy-offer/order/story/rumour-limit, hut speak, bargirl lodging, and Garth's reward branch run through runtime adapters. `GamePromptsModel` plus terminal pause hooks render the authored `(<verb> RETURN key)` prompt and consume Return explicitly in the ANSI adapter. The compiled-game harness covers real authored church, merchant, smith, tavern, hut speak, bargirl lodging, seeded tavern scruffy/story paths, direct Garth reward, and Garth reward through normal speak selection flows; `:runtime` now carries the current playable service implementation.
- [x] E4. Rest, camp, hunt, and lodge. DSL delta: action scripts with costs, risks, time passage, food/fatigue effects. Native decision: RNG and state mutation primitives. Current runtime status: camp/rest and hunt have authored balance data plus reusable runtime sessions: camp owns guard selection, food gate, time advance, healing, temperature damage by authored item protection, surprise-kill, and ambush handoff; hunt owns member prompt, ranger/dead/move checks, success chance, time cost, food gain, and narration. Bargirl lodging follows the authored-content rule via a speak question-action script with price, prompt, broke/spent text, gold mutation, and per-member HP drain. Closeout coverage locks hunt balance, camp balance/temperature/ambush settings, camp item protection, hunt runtime mutation, camp runtime mutation, wilderness hunt wiring, hut speak, and bargirl lodging mutation. Any future lodge-like abstraction should be opened as new service-model work only when another concrete service needs it.
- [x] E5. Upkeep and time. DSL delta: `upkeep { daily { ... } hourly { ... } }` with balance tables. Native decision: clock/tick scheduling and deterministic RNG. Current runtime status: `WildernessSession` has an explicit `TurnStart` step that advances the hour once per wilderness command cycle, regenerates mage/priest mana by seeded RNG, applies authored food tick/cost rules, applies authored starvation thresholds/damage/death text, decrements rested hours, and applies the authored fatigue ladder with sleep-deprivation drain. Broader save/load scheduling policy remains future work, but the current numeric rules are no longer hardcoded Kotlin balance.
- [x] E6. Wilderness events and transitions. DSL delta: terrain transition scripts, random wilderness event tables, and authored wilderness input bindings. Native decision: random draw and event dispatch runner. Current runtime status: `WildernessSession` can move the active party across the authored `WorldMapGrid` using authored number-key or arrow-key bindings, reject impassable terrain/out-of-bounds movement, spend one move per successful step, pass time with the Space command, enter declared towns from town tiles, run the reusable town session, enter NPC huts through the normal scene/speak bridge, return to wilderness, run original C wilderness key bindings for Space/S/W/R/!/J/?/H/V/T/P/E/L/C/Z/Q/X, route S to a backend-neutral save step fulfilled by frontend save ports, route J to the shared party-journal session, route V to shared party view, route L to shared inventory drop, route Q to quaff potion item-use, route W/R/T/P to equipment prompt sessions, route Z to a reusable priest-spell prompt/session over authored spell effects, route C to camp, route H to hunt, model the reusable X quit-confirmation flow with cancel/finish outcomes, model the S/Q out-of-moves prompt with save-before-quit behavior, poll authored random-step missions such as bandits/troll tooth, terrain missions such as skullweed/Foot of Jazzy, fixed-position missions such as Erethor/husband cave, and realized-location missions such as Silver Key before generic wilderness events, dispatch authored terrain-transition narration on seeded quiet moves, check authored name-gated step secrets before generic random events, prompt E/L on cave discovery, apply cave entry cost/no-moves checks, hand off random-cave and authored mission dungeon starts, run a reusable `BattleSession` for random encounters by authored encounter tier, execute priest Teleport through semantic direction/distance prompts plus `teleportParty(...)`, execute priest Earthquake cave discovery through `discoverCave(...)`, execute Revelation's authored dungeon find-object branch with real item metadata, and dispatch authored castle entry by matching castle entry coordinates and running optional castle hook scripts. Torch spending is owned by dungeon descent/runtime mechanics rather than an unused generic script helper. The authored Ydarg entry coordinate is validated against the castle tile on the world map.
- [x] E7. Character creation. DSL delta: `characterCreation { statRoll(...); start { ... }; race(...); playerClass(...) { requires(...) } }` plus the existing typed `stateFixture`/`runtimeState` start-state builders. Native decision: backend-neutral semantic input, RNG/stat roll primitive, class-gate validation, and state allocation. Current game source status: authored creation content now covers the reference 15..70 stat range, 12/9 wilderness start, starting resources, party size, daily move budget, Human/Dwarf/Elf/Halfling race order, Fighter/Thief/Mage/Priest/Bard/Ranger class order, and Fighter/Mage/Priest/Bard minimum-stat rejection text. Runtime status: `CharacterCreationSession` owns party-name/member-name/gender/race/class semantic input, reroll decisions, deterministic stat rolls, class rejection with slot reroll, caster starting mana, empty inventory allocation, and final `RuntimeSessionState` boot into wilderness. Closeout coverage locks the authored ledger and the allocate/reject session paths. Title-menu/new-party terminal integration belongs under H8/H11 frontend shell work, not E content/rule debt.
- [ ] E8. Only add timing/configuration knobs for services when content needs them. DSL delta: configurable random-item offer pause durations or service-specific wait timings should be explicit authored model fields only after a current service varies them. Native/runtime decision: default pauses can remain implementation defaults while all current services share them. Exit criteria: if a script needs different timing, add the field, validation, and a test that proves the runtime uses it; otherwise keep unused `pauses(...)`-style builder methods out of the DSL.

### F. Items, spells, combat, monsters, and rewards

- [x] F1. Item records. DSL delta: typed item definitions for mundane, equipment, potion, magic, and quest items. Native decision: id lookup and inventory storage. Current game source status: all 63 current item records and 7 semantic item pools are declared in `items { ... }`; generated handles expose `Items` and `ItemPools`, while compiled `GameModel.itemCatalog` and `GameModel.itemPools` are the runtime/validation sources of truth.
- [x] F2. Item use effects. DSL delta: item scripts for potions and magic items. Native decision: consume/remove primitive and typed stat mutation helpers. Current game source status: `itemUse(...)` handles reusable context/consume/effect metadata; red, blue, and amber potions are scripted inventory item uses with member prompts/filters, script-controlled slot consumption, and typed HP/mana mutations, and all 12 current battle magic items carry explicit combat context/consume metadata plus authored effect bodies. Runtime notes: battle item use is runnable; `InventoryItemUseSession` is the generic non-combat inventory item-use menu/session and is wired into the wilderness terminal shell; selected weapons/armor route into the equipment ready session, while non-usable gear/treasure reports no effect. Service/dialogue reward and random-offer runners receive model item records. Torches and food are party resources rather than slotted item-use actions, reusable torch consumption is a dungeon-transition action, donkey food capacity is enforced on inventory loss, and tent/blanket rest protection is available for the upcoming camp runner.
- [x] F3. Equipment rules. DSL delta: ready/remove actions, optional equipment class restrictions, and derived combat values in DSL. Native decision: equipment storage and target/member access. Current runtime status: pure Kotlin `equipFromInventory(...)`, `unequipToInventory(...)`, and `applyEquipmentCommand(...)` implement the reference swap/free-slot state changes against `PartyState`; `EquipmentPromptSession` and `TownLeaveEquipmentSession` cover direct ready/off and leave-town ready prompts with trace and terminal-style adapters. `BattleSession` uses readied weapon damage for player attacks and readied armor class for monster melee. `ItemRecordModel.equippableBy` plus weapon/armor `equippableBy(...)` authoring enforce class restrictions when declared; current reference `weapons.json5` only exposes the optional `wieldable_by` loader hook and declares no active restrictions, and armor declares none. No remaining current-reference non-combat equipment effects are known outside item use, resource capacity, and camp protection, which are tracked in their owning item/camp rows.
- [x] F4. Spell records. DSL delta: mage, priest, and monster spell definitions with typed costs, targets, effects, and text. Native decision: spell picker, cost-payment primitive, semantic spell-only runtime operations, and backend-neutral script continuation. Current game source status: all 6 mage battle spells, all 4 monster battle spells, and all 7 priest spell records are authored in the game DSL with inline typed text, party/monster/member bindings, target choice, damage/healing formulas, combat flag mutation, semantic runtime context checks, Teleport/Earthquake runtime ops, and Revelation's successful dungeon find-object/take branch over authored item pools, including exact full-inventory rejection. Closeout coverage now asserts the exact authored spell books, costs, slots, names, non-native bodies, Teleport movement op, Earthquake cave-discovery op, and runtime priest paths for Healing, Restoration, Rebirth, Banish Evil, Revelation, and Earthquake. Rebirth and poison-style inventory effects explicitly update party living count alongside the authored dead-flag mutation. A richer Revelation room model is not implemented because the current reference exposes only the random find branch already ported; richer room/secret modeling stays in G1 only if future reference behavior requires it.
- [x] F5. Monster records and tiers. DSL delta: typed monsters, encounter tiers, behavior hooks, and reward-facing encounter identity. Native decision: encounter instance allocation, seeded monster pick/count/HP rolls, and battle handoff storage. Current game source status: all 68 current monster records are declared in the game DSL, including exact weak/wild/deep tier membership, tierless mission/boss monsters, immune flags, spell power, weapon skill, HP/count ranges, and Gnoll's level-scaled count maximum. `BattleSession` allocates monsters from authored tiers, rolls foe count with `encounterFoeCountMax = 8`, and rolls scaled HP from authored formulas. Closeout coverage now locks the exact tier lists and immune bosses; monster-specific behavior hooks remain intentionally tracked under F7 instead of keeping the data-record task open.
- [x] F6. Combat loop. DSL delta: typed combat actions, turn phases, flee/death/reward rules, and authored prompt/menu text. Native decision: combat context storage, target UI, and typed op execution. Current game source status: `combat { standardBattle { opening; text; playerActions; playerRound; monsterRound; rewards = Rewards.standardBattle } }` declares the reusable battle framework with current-game menu/status text and tunables for attack, defend, battle cry, monster spell chance, and monster melee. Runtime status: `BattleSession` executes fight/run, per-member rounds, attack target prompts, defend, battle cry, mage spell selection/costs, combat item selection/effects, monster melee/spells, party defeat, victory, formula-driven XP/gold, display-name loot, and level-up application/narration. Closeout coverage runs the compiled standard battle loop through escape, first attack/monster round, defeat, authored input aliases, readied weapon/armor effects, mage spell casting, combat item retry, victory art, rewards, loot, and level-up narration. Broader non-terminal adapter/playthrough parity now belongs under H11 instead of keeping F open.
- [x] F7. Monster AI and attacks. DSL delta: monster behavior scripts for melee/spells/specials. Native decision: RNG and target choice helpers. Current game source status: `standardBattle.monsterRound` models the current reference behavior as spell chance plus melee fallback; authored monster spell scripts cover frost, fire, hold, and confusion. Runtime coverage locks both the readied-armor melee fallback and authored monster-spell execution. No current reference monster needs an extra bespoke behavior hook; if one is discovered during broader playthrough parity, it should reopen as a named monster/content task instead of living as an open generic F item.
- [x] F8. Rewards and loot. DSL delta: reward DSL for gold, experience, items, flags, and journal updates. Native decision: first-free-slot helper and overflow prompt. Current game source status: top-level `rewards { reward(Rewards.standardBattle) { experience; gold; itemPlacement; lootTable(...) } }` declares the standard battle reward bundle using semantic item pools and exact full-inventory behavior. Script-level `reward { gold(...); items(...) }` covers Borin's skip-if-full gift, the Ogre cave Mithril Mail reward, and Garth's prompt/drop merchant reward; `journal { ... }` covers Borin/Hobgoblin location notes with typed runtime location coordinates; shop and random-item purchase helpers default to prompt/drop inventory placement with semantic item handles. Runtime status: `BattleSession` applies standard battle XP/gold/loot rewards from authored formulas; `RandomItemOfferSession` executes scruffy-style item offers with item cost/name lookup, gold payment, first-free-slot placement, and prompt/drop overflow; the generic source `GrantReward` statement applies gold and slotted items through `RewardGrantSession`, prompts for drop-slot overflow, and resumes without replaying already-applied rewards. Closeout coverage asserts battle XP/gold/loot, reward-bundle configuration, mission rewards, Garth reward resume, and random-offer overflow behavior. Special inventory projections remain intentionally modeled as milestone state, so scripts use `achieve(...)` rather than granting special items as slotted rewards.
- [x] F9. Leveling and experience. DSL delta: class progression tables and level-up formulas. Native decision: applying member stat changes. Current status: authored class XP thresholds/growth and hit-point-floor formula are in the game DSL, and battle victory applies and narrates level-ups after XP rewards. Closeout coverage now drives a compiled battle victory across an XP threshold and asserts the authored Fighter growth, hit-point-floor formula, final XP, and level-up narration.
- [ ] F10. Keep item-use convenience aliases out until authored content needs them. DSL delta: use `usableIn(ItemUseContext.Combat, ItemUseContext.Inventory)` as the canonical multi-context form until a concrete game script benefits from a named alias. Native decision: aliases such as `combatAndInventory()` should not exist just to mirror likely future content. Exit criteria: either keep no alias, or add one with at least one authored use and test coverage.

### G. Dungeons, castles, secrets, and bespoke systems

- [x] G1. Dungeon scenarios. DSL delta: `dungeons { event(...) { ... } scenario(...) { name(...); levels; events } }` first, with richer `dungeon(...) { levels; rooms; events; encounters; exits }` deferred until an actual reference room model appears. Native decision: dungeon instance storage stays in reusable `DungeonSession`; optional map generation is not needed by the current reference. Current game source status: the authored event/scenario table covers display names, empty-level text, found objects, gold/torches, gold chests/traps, weighted weak/deep random encounters, Fire Shield Guardian/award, Bracelets award, Nolrim tomb, Spectrum plaque, Thun riddles, Reebar colour riddles, Master Crown battle/award, Silver Sanctum note, Hobgoblin patrol/guards/chief/Silver Key, Ogre cave orcs/Tammy, Erethor priests/Lute chamber, random-cave weights, all current fixed placement scenarios, and the standard cave/dungeon backdrops. Fixed scenarios explicitly include the standard random dungeon event mix before overlaying special levels. Current runtime status: script and wilderness sessions hand off semantic dungeon ids plus level counts into a reusable `DungeonSession`; the runner builds a fresh per-session level event table, executes authored screen/reward/milestone/flag/random-encounter/text-input events, clears non-recurring events, supports conditional recurring clear for Tammy's prison, preserves recurring events such as Spectrum/colour-riddle/patrols for revisits, supports level menu rendering, D/A traversal, torch spending and no-torch/max-depth/leave text, shared party view/drop/equipment prompts, Q potion use, Z priest spell dispatch including Revelation's item-find branch, return to wilderness, and post-dungeon mission-script resume. Current audit status: `src/dungeon.c` is only the loop/menu driver; level-event bodies are in `data/world/scripts/dungeon.json5` and match the authored DSL placement table. Concrete mismatches closed: find-object uses the exact `random(0..4)` common/rare split, leave is silent, find-object/Revelation full inventory rejects without a drop prompt, Spectrum is recurring, and Tammy clears only after rescue. Coverage: compiled tests assert every fixed scenario placement table, every authored fixed dungeon event body through the terminal adapter, weak/deep random-encounter tier handoff, Thun/Reebar/Silver Key playthrough branches, dungeon utility commands, and active-dungeon save/resume.
- [x] G2. Cave/location events. DSL delta: special caves, pits, sanctums, and location scripts moved from C/JSON5 into typed scenes. Native decision: enter/exit dispatch only. Current game source status: seven current dungeon-entry mission prompts are authored with semantic triggers and `enterDungeon(...)` handoffs; Thun's gem altar consumes any five authored altar-gem pool items before handing off to the temple; Borin, Kobold village, Ogre cave, Foot of Jazzy, and Hobgoblin caravan location bodies are authored through typed scenes with mission-level resume/completion tests. The standard state fixtures place `Locations.hobgoblinCave` so Hobgoblin caravan text and journal entries resolve semantic coordinates. The dungeon/cave audit found no separate C-owned room action layer beyond the authored dungeon event scripts and cave/location scene handoffs.
- [x] G3. Generic grid castles. DSL delta: model rooms, mutable room state, events, rewards, optional entry/completion milestones, and lifecycle hook scripts in `castle(id) { ... }` content. Current status: `CastleContentModel` declares castle entry, backdrop, optional entry/completion markers, map size, room roll table, monster tier, treasure tables, pitfall rules, stairs item trades, lifecycle script hooks, keys, and authored movement/prompt text. `CastleSession` owns seeded room state, current floor/position, movement, room clearing, pitfall damage/disarm, treasure placement, stairs teleport/item-give flow, generic script hook handoff/resume, shared child-session routing for in-castle party view, drop, equipment, quaff, and priest spells, plus save/resume snapshots for navigation state. Wilderness castle entry resolves `content.castles` by authored entry point and hands off the matching castle model semantically for entry-blocked, entered, completion, and already-completed hook states; save snapshots persist the castle id and resume by `CastleId` instead of assuming a singleton alias. The old `native.ydarg.*` game-source island, Ydarg-named runtime/session/snapshot/adapter classes, built-in gatekeeper/final-boss topology, and hard-coded Hall-of-Heroes Baron Ydarg text are removed; the current game still authors Castle Ydarg as `castle(Castles.ydarg) { ... }`, with Jaara, the multi-battle finale, the win milestone mutation, and Hall-of-Heroes copy in game DSL scenes/content. Compiled scenarios now cover entry-blocked/entered/already-completed hooks, in-castle utility commands, save/resume, treasure take/leave/full-inventory branches, pitfall damage plus thief disarm success/failure, authored completion victory/defeat, and the ANSI-facing navigation view boundary that restores the castle backdrop and current status line after modal child flows. A second castle should require new game DSL content, not Kotlin runtime special cases.
- [x] G4. Secrets and sysop/debug actions. DSL delta: explicit optional `secrets { player("...") { ... } }` block with name gates. Native decision: input matching stays in frontend/session command dispatch, while mutations stay in backend-neutral runtime helpers. Current status: the Marcus reference keys add moves/gold or set inventory slots from authored item handles, the Karl every-101-moves trigger starts the authored Skagerberg Killer as a mandatory wilderness battle before generic random events, the ANSI and terminal adapters consume the same hidden session commands, and validation checks duplicate players/keys plus item and monster references.
- [x] G5. Score/vscore. DSL delta: `screen(...) { scoreReport() }` and `screen(...) { verboseScoreReport() }` declare dynamic static-screen routes, not authored score-file text. Native decision: ranking/report formatting belongs to the runtime/frontend boundary over loaded `RuntimeSessionState`, with compiled item/milestone metadata supplying power-item names. Current status: line and ANSI launchers load/validate the selected runtime state before `--screen score|vscore`, render sorted party summaries and verbose leader/classes/kills/average/power-item reports, and no longer require mutable `data/score.txt` or `data/vscore.txt` generation.
- [x] G6. Replay/test knobs. DSL delta: typed `stateFixture(...)`/`runtimeState(...)` start states and typed test-scenario fixture metadata replace game-rule replay env knobs in Kotlin. Native decision: test-only overrides stay in the scenario harness or explicit test setup, not runtime/game code. Current status: Kotlin launchers expose `--state`, `--load`, `--seed`, and save slots instead of `RYL_FORCE_*` gameplay env vars; `ScenarioFixture` now carries a typed `StateId` plus seed metadata; compiled scenarios construct forced positions, party levels, equipment, dead members, encounters, and Ydarg setup directly as `RuntimeSessionState`/session inputs; a ledger regression rejects legacy C replay env tokens in active Kotlin source roots. The old C replay harness can keep `RYL_*` knobs until that native path is retired.
- [x] G7. Hall of Heroes and title-shell completion parity. DSL delta: Hall of Heroes is current game content as `screen(StaticScreens.heroes) { hallOfHeroesReport() }`; Ydarg completion remains the backend-neutral `Milestones.ydargVictory` runtime/save-state marker instead of an unmanaged `heroes.txt` append. Native decision: the old BBS `entry.dat` daily-entry counter is intentionally retired as multi-user door policy; the persistent world role is the strict runtime snapshot save slot. Current status: the ANSI launcher now defaults to the title shell when no `--state`, `--load`, `--new`, or `--screen` override is supplied, shows the authored title screen, loads `build/saves/main.kts` when present, initializes a clean world from authored content when absent, offers N/C/T/S/H/D/X as appropriate, runs authored character creation from `N`, saves the newly initialized runtime world including realized NPC placements, continues saved games from `C`, deletes the main save after `T` confirmation, and routes S/H/D through the same runtime/static-screen frontend boundary used by `--screen score|vscore|heroes|manual`. Old BBS per-day turn counting remains out of scope unless multi-user door semantics are reopened explicitly.

### H. Engine replacement path

- [x] H1. Compiled content bundle. DSL delta: define generated bundle schema from Kotlin content. Native decision: C/native/server loaders consume typed tables, not JSON nodes. Current status: `rylween.compiled` exposes `CompiledGameBundle`/`CompiledContentBundle` from the authored Kotlin game with typed tables for universe metadata, limits, flags, items, item pools, formulas, rewards/loot, terrain, authored world maps/layers, visual assets, static screens, screen regions, towns/building services, characters, monsters, missions, spells, item-use scripts, dungeon events/scenarios, and script roots. `CompiledBundleLoader` validates cross-table references and materializes handle/string/script lookup maps without reading `GameModel`.
- [x] H2. Compiled state image. DSL delta: define generated state schema and exact save snapshot schema. Native decision: C/native/server stores mutable ids/arrays and can write exact DSL state. Current status: compiled state images cover state id/kind, runtime metadata, realm time/mode/current map/town/party, location and NPC placements, party resources/counters/flags/milestones/inventory/members/visited NPCs/journal, dungeon/castle mode snapshots, and post-dungeon continuation references. Runtime save/load remains the strict DSL snapshot format; external binary/state persistence is future adapter work, not required by the Kotlin runner.
- [x] H3. String interning and hash tables. DSL delta: every handle has slug plus dense id. Native decision: stable lookup tables for diagnostics, migration, and saves. Current status: compiled bundles intern all strings, assign per-kind handle ids plus global dense ids, expose string and handle hash entries, and the loader builds hash buckets and slug-to-handle lookup for diagnostic/native access.
- [x] H4. Typed script/op VM input. DSL delta: scripts compile to typed ops with checked operands. Native decision: future VM executes ids and typed values only. Current status: authored scene, dungeon-event, spell, item-use, dialogue, and question-action roots compile to `CompiledScriptProgram` with typed opcodes, typed operands, typed expressions/lvalues/text/view commands, and recursive branch/menu/text-input bodies. At H4 closeout the Kotlin runtime still executed the backend-neutral session state machines and authored source tree directly; J2a/I9 later retired that path from production and kept it as a parity oracle. Callable helper fragments are not current debt while helpers inline into script roots; if a helper becomes a real call target it must be promoted under H13 with a generated function id, call op, continuation frame, and source-map/debug boundary.
- [x] H5. Renderer boundary. DSL delta: scripts use `showBackdrop`, `renderMap`, `say`, and status helpers rather than terminal cursor calls. Native decision: backend-specific terminal/image drawing. Current status: terminal prompt rendering and line-terminal adapters are split out of the core engine, menu footers are centralized over authored/runtime bindings, script rendering emits typed `ViewCommand` events, standard battle banners/victory art emit the same view events, the ANSI frontend implements those events, and the reusable map viewport returns terrain cells instead of terminal rows. Remaining visual work belongs under H8/H9: classic-layout polish and broader typed render events where future frontends need more than the current five commands.
- [x] H6. Remove JSON5/binary-data runtime dependency. DSL delta: inventory records, world metadata, map rows/layers, terrain, text, scripts, static-screen routes, and visual resource handles are modeled in the game DSL/content bundle; legacy JSON5 and `map.dat` stay source-reference material only inside `scratch/legacy-archive.zip` under `legacy/c-json5/data`. Native/runtime decision: Kotlin launchers no longer accept `--map`, no app/runtime/game/test source reads JSON5 or `map.dat`, and `WorldMapGrid` no longer owns a reference `map.dat` reader. Packaged `game/src/main/resources/ansi/**`, `game/src/main/resources/tiles/**`, and `game/src/main/resources/adventurers-of-rylween.md` remain frontend/user-facing assets, not content loaders.
- [x] H7. Reusable script-fragment identity. DSL delta: game authors still declare scenes/events/spells/item effects/dialogue in normal game language, while runtime continuations attach a `ScriptRootRef` with root kind, dense id where available from the compiled content catalog, and diagnostic slug. Native decision: persisted script continuations now render/parse the root alongside the relative block stack; terminal/service/dungeon/priest/item-use/speak runners stamp roots at their adapter boundary, and `RuntimeContent.scriptRootCatalog()` produces dense ids for authored script roots without exposing slug strings in `RylweenGame.dsl.kts`. Non-inlined helper-fragment handles remain future bundle/source-map work if a helper becomes a real callable fragment instead of inline Kotlin authoring sugar.
- [x] H8. Replaceable frontend/runtime shell. DSL delta: boot compiled content through `RylweenKotlinEngine`, realize start state, run town/wilderness/service/combat/dungeon sessions behind semantic input/output adapters. Current runtime status: this hybrid interpreted/session shell is retired from the active Gradle graph and archived inside `scratch/legacy-archive.zip` under `legacy/ansi-runtime`. Its old line and ANSI launchers proved the semantic input/output adapter boundary, including town/wilderness/service/combat/dungeon flows, authored map/resource loading, raw key reads where a real terminal supports them, and typed `ViewCommand` dispatch. Native/view decision: ANSI terminal, bitmap, C bridge, server, and headless test adapters are replaceable frontends over the same model/session contracts, not owners of game rules; the active playable implementation is now the direct runtime in `:runtime`.
- [x] H9. Kotlin ANSI frontend adapter. DSL delta: frontend adapters consume `terminal { terrain(...) }`, `layouts { region(...) }`, and `visuals { images(...) }` declarations instead of owning presentation facts. Native/view decision: isolate ANSI terminal mode, cursor addressing, window drawing, text/map/image/status rendering, key decoding, line input for text-input prompts, logging/socket fan-out, and save-file prompts behind an adapter API so a bitmap frontend can replace it without changing game rules, state, or content. Current status: the archived ANSI shell inside `scratch/legacy-archive.zip` under `legacy/ansi-runtime` resolved full-screen `ansi/*.ans` and map-window `tiles/*.ans` resources through authored visual assets, consumed authored classic regions, handled cave discovery, dungeon exploration, castle navigation, and standard battle banner/victory art via typed `ViewCommand` events. The old line-terminal prompt renderer now lives with the archived reference runtime inside `scratch/legacy-archive.zip` under `legacy/reference-runtime`, while active terminal escape/key primitives live in `terminal/src/main/kotlin/rylween/terminal/`. The active runtime consumes those shared low-level primitives directly without depending on the archived session adapters.
- [x] H10. Game universe compatibility. DSL delta: compiled content declares or derives a universe id/version/hash. Native decision: save/load checks snapshots against that id and rejects incompatible worlds or invokes explicit future migrations. Current status: `content { metadata { universe(...) } }` declares the game universe/version, runtime save metadata writes universe id/version plus content hash, and load validation rejects mismatched format versions, universes, versions, or compiled-content hashes before booting state. No old C/JSON save compatibility is required by default.
- [x] H11. Scenario and playthrough testing. DSL delta: optional test/scenario declarations or fixtures can name start states, RNG seeds, scripted semantic inputs, and checkpoint assertions without changing runtime rules. Native/test decision: broad playthrough tests run through compiled content and session APIs, not direct unit-only shortcuts; each replaceable frontend/runtime adapter should be driveable from the shared scenario fixture/checkpoint shape when that adapter exists. Current status: the reusable test-scope scenario harness carries named fixtures, seed metadata, scripted inputs or semantic headless steps, captured text/view/image traces, output/image assertions, view-command assertions, and checkpoint-labeled failures. Current compiled scenarios cover authored character creation through the terminal adapter, Thun/Reebar/Hobgoblin dungeon playthrough checks, multi-battle mission completion bodies, wilderness save/out-of-moves and file save/load boot validation, authored image/view-event traces, Ydarg gate/entry/navigation view events/treasure take/leave/full, pitfall damage/disarm/failure, authored castle-completion victory/defeat flows, battle run/first-round/victory/defeat flows, merchant/smith/tavern town-service flows, the wilderness-to-town shell handoff, terminal static-screen/resource rendering, and headless semantic-input scenarios for wilderness town handoff, dungeon navigation/leave, and Ydarg navigation. A cheated golden-path playthrough now uses real compiled content and session adapters to cover every Thord service, every authored mission id, all expected dungeon handoffs, Ydarg victory, deterministic quest-location text rendering, and unresolved-placeholder detection while keeping monsters/resources safe enough for full-game regression. A first real-route slice drives the same Thord building circuit from the wilderness shell: move to the authored town coordinate, enter, pay tax, visit merchant/smith/inn/tavern/church through the nested town session, leave town, and exit. A second real-route slice starts independent cheated parties at adjacent wilderness coordinates and verifies every authored mission through the normal movement trigger path, including position, location, terrain, and seeded random-step polling before the existing mission-body checks run. A third real-route slice takes every dungeon-handoff mission through normal movement trigger, enters the real dungeon child session, leaves through the dungeon loop, and verifies the post-dungeon mission continuation completes back in wilderness mode. A fourth real-route slice now drives every dungeon-handoff mission through normal movement trigger, enters the fixed dungeon, walks all authored fixed event depths, completes the authored reward/flag/milestone body, ascends back out, and verifies the post-dungeon continuation completes back in wilderness mode; random standard dungeon fill is disabled only for this deterministic full-depth slice. A final acceptance scenario carries one cheated party state through the Thord service circuit, every mission trigger, every fixed mission-dungeon completion, pre-Ydarg power-item readiness, Ydarg victory, and runtime score/verbose-score/heroes rendering. No separate authored scenario DSL is needed yet; future bitmap/server adapters should add concrete scenario drivers under their own frontend tasks rather than keeping H11 open as abstract debt.
- [x] H12. Serialized compiled-bundle artifact and external loader. Closed for the current Kotlin functionality target: `CompiledGameBundle` plus `CompiledBundleLoader` already provides the typed compiled-content/state boundary, validates cross-table references, and materializes dense string/handle/script lookup maps without reading `GameModel`. A stable external file/binary/JSON artifact is not needed by the Kotlin ANSI, line-terminal, or headless runtimes; if a real non-JVM runtime product is scheduled, emit/read that external artifact under J1 rather than keeping H open.
- [x] H13. Compiled-op executor. Historical closeout for the old Kotlin interpreter path. The current active runtime executes compiled tables directly; a separate non-JVM opcode VM should be built only under a new non-JVM product plan, not treated as hidden Kotlin-runtime debt.
- [x] H14. Audit compiled/script op remnants after authoring cleanup. DSL delta: every public script helper should have an authored path and every emitted opcode should have a current compiler/runtime reason. Native/runtime decision: indexed text emission, generic script-level torch consumption, generic native effect calls, native int probes, and role-name/location native string hooks were removed from the DSL AST, compiler, opcode contract, runtime handlers, native smoke schema, and C smoke reader because current authored content does not produce them. Runtime torch tracking remains the party `torches` resource, with spending owned by dungeon descent/runtime mechanics rather than a generic script helper. The remaining native boundary is the authored string-binding helper set (`partyName`, `monsterName`, pronouns, and current-town NPC name), and the ledger now asserts the allowlist exactly matches authored helper use.
- [x] H15. Compiled-bundle diagnostics through existing logging. DSL delta: none for game authors. Native/tool decision: bundle artifact tools now accept `--log <file>` or `RYLWEEN_TOOL_LOG=<file>` and route opt-in emit/check/inspect diagnostics plus compile summaries through the existing `RuntimeLog` sink. Default Gradle/tool execution remains quiet except for the existing CLI success/failure lines, and `:core` compiled-bundle code still has no logger API or logging dependency.
- [x] H16. Runtime/DSL responsibility and duplication audit. Scope: inspect duplicated or split ownership between authored DSL/content and runtime code, not only duplicated loops. This includes control flow (`loop`/`menu`/choice ops versus domain runners), command/action identifiers, prompt text, fallback text, key/cancel policy, pricing/reward policy, pacing, presentation labels, and host-only stringly contracts. Decision rule: domain mechanics that are pure runtime state machines can remain host-owned, but any runner that owns authored text, menu policy, balance, rewards, service sequencing, or externally meaningful command contracts should either route through existing semantic DSL records (`ShopService`, `RandomItemOffer`, `Ask*`, `Menu`, runtime systems, etc.) or get a new explicit DSL/runtime-system model. Current status: low-risk duplicated user-facing policy is either authored in existing DSL/runtime-system rows or explicitly classified below. External command ids remain string values in the artifact for cross-runtime compatibility, but Kotlin producers/consumers now use typed ids. Runtime-only mechanics such as inventory slot indexing, compiled-catalog filtering, RNG, state mutation, persistence, and frontend window chrome remain host-owned unless a future feature needs them as authored content.
  - [x] H16a. Current low-risk duplication cleanup. Runtime utility prompts now derive the "return leaves" cancel line from the authored runtime prompt `returnKeyName`, and current smith/merchant shop sell/buy content authors the sold and price text that the runtime previously supplied as fallback strings. DSL validation now rejects current-style shop buy/sell sections that would fall back to runtime-owned price/sold/declined text. Hunt and shop-interlude rumor fallbacks are now validated as authored runtime-system requirements, and impossible shop sell slot drift is treated as a developer invariant instead of user text.
  - [x] H16b. Command/action identity audit. `RuntimeCommandGroupId` and `RuntimeCommandActionId` now name the stable Kotlin-side command contract for title, wilderness, dungeon, castle, town, and speak dispatch. The external runtime-system bundle still emits string ids, but exporter/runtime code no longer repeats those string literals; castle group ids are generated through typed helpers from the authored castle id.
  - [x] H16c. Utility action prompt/key ownership audit. Priest cancel and first-spell keys are authored/exported with the priest prompts and consumed by `UtilityActions`. Inventory slot letters remain runtime-only presentation of persisted inventory slots, and the priest spell id-prefix filter remains a bridge-boundary shortcut until spell school/category becomes authored metadata.
  - [x] H16d. Runtime fallback/error text audit. Current shop inventory purchase success text is now authored through `ShopService.buy { bought { ... } }` with the selected-item binding, and validation rejects inventory-buy shops that omit it. Remaining missing-bundle/service errors, save/manual/help shell strings, debug-only wilderness text, and impossible-state failures are classified as developer diagnostics or frontend shell text rather than game prose.
  - [x] H16e. Presentation-label ownership audit. Party journal empty text and inventory headers now come from runtime-system prompts instead of generated runtime labels. Status-line abbreviations, document popup titles, and score/high-score window chrome remain frontend presentation chrome; promote any of them into runtime systems only when they become game-authored prose or cross-runtime content policy.

### I. Kotlin implementation health

- [x] I1. Whole-tree Kotlin idiom, duplication, and ownership sweep. Scope at the time: Kotlin under `src/main/kotlin`, the now-archived ANSI runtime, `runtime/src/main/kotlin`, `game/src/main/dsl`, and `src/test/kotlin`. Current status: closed for the diagnostic debt known at the time of the pass. Real dead code was removed from reward grants, inventory ops, script bindings/root helpers, speak rumours, wilderness/world-map commands, shop/town/runtime accessors, and terminal prompt helpers; Ydarg mandatory battle startup and pitfall damage were simplified; save-string quoting is shared between compatibility hashing and state snapshots; CLI startup handling now uses `runCatching`; duplicate line-terminal town/wilderness driver loops were collapsed into one adapter loop; intentional CLI `print`/`println` usage and authored Ydarg DSL surface are explicitly suppressed rather than left as bogus warnings; and `RylweenGame.dsl.kts` moved to `game/src/main/dsl` so IntelliJ no longer treats the authored script as an ignored Kotlin source-root file. `RylweenCompiledGame` is now generated into the `game` source set under its package path, so runtime/test source sets depend on the game output instead of carrying the compiled-game harness as their own generated source. Exit validation is now the active `./gradlew check --console=plain` graph plus `git diff --check`.
- [x] I2. Runtime package/domain consolidation. Scope: make runtime ownership readable without merging everything into oversized files. Current status: root `rylween.runtime` now holds shared primitives only: live state/RNG, snapshots, command binding helpers, navigation/direction, inventory/equipment state ops, script bindings/continuations, typed expression evaluation, and common session input enums. Domain sessions moved into explicit packages: `runtime.battle`, `runtime.wilderness`, `runtime.dungeon`, `runtime.town`, `runtime.party`, `runtime.reward`, `runtime.score`, and `runtime.castle`. The stale empty `src/main/kotlin/rylween/game` source directory was removed; the generated compiled-game harness remains under `build/generated`. Shared cleanup landed with the move: wilderness/castle movement commands use one cardinal-direction helper, generic typed expression evaluation moved out of battle, flag state helpers moved out of speak, and parent-session status mapping for equipment, inventory item use, priest spell, and drop flows is centralized instead of repeated in wilderness/dungeon/castle.
- [x] I3. Source naming and crawl-domain convergence. Scope: reduce redundant `Rylween*` source names now that package ownership is explicit, and decide which tiny files should merge by cohesive runtime role rather than historical port step. Current status: source filenames under `src/main/kotlin/rylween`, the now-archived ANSI runtime, `runtime/src/main/kotlin`, and `src/test/kotlin/rylween` now use package-local concept names such as `WildernessSession.kt`, `ShopSession.kt`, `TerminalFrontend.kt`, `RuntimeSave.kt`, and `DslValidationTest.kt` instead of repeating the project name. The temporary authored-script Kotlin file was removed after its remaining flows moved into `RylweenGame.dsl.kts`. Public type names are intentionally unchanged so API consumers do not need aliases. The castle terminal adapter moved through the normal terminal frontend package before the old host was archived; Ydarg remains authored castle/crawl content only, while existing shared movement command, child-session, save, script hook, and view helpers carry the common behavior. Tiny-file audit decision: command-binding files, save helpers, and session files remain separate when they represent one cohesive role; no merge is currently worth trading away locality. Exit validation is now the active compile/test/check graph.
- [x] I4. Warning-driven cleanup pass across active source. Scope: inspect IDE/compiler warnings as design signals, not cosmetic noise. Current status: closed for the current warning surface. Real dead/stale warnings found during this pass were fixed or moved into smaller source files; remaining suppressions are intentional public DSL/API compatibility surface: script/menu/choice receivers, reward/random-offer authoring overloads, alternate combat input-binding overloads, secret/equipment prompt builders, and party prompt rows consumed through `runtimeSystems`. The remaining ktlint wildcard-import reports are existing file-level compatibility suppressions in broad model/compiler files, not new warnings introduced by this pass.
  - [x] I4a. DSL unused-suppression audit. The current `core/src/main/kotlin/rylween/dsl/impl` sweep found no `data class` declarations suppressed as unused, and no implementation data class isolated to its own declaration file only. The actual unused suppressions are on authoring receiver/builder APIs in `Dsl.kt` plus the alternate `InputBindingModel` combat-action overloads in `CombatDsl.kt`; these are public DSL surface, not missing runtime data models.
  - [x] I4b. Narrow or remove broad DSL receiver suppressions before closing I4. Current status: removed broad `unused`/`MemberVisibilityCanBePrivate` suppressions from the equipment-kind prompt builder, character builder, formula builder, and wilderness prompt builder after their public DSL use was clear from current game authoring. Remaining suppressions are limited to the script/menu/choice kernel receivers, reward/random-offer authoring overloads, and generated/public prompt rows where the declaration is intentionally exposed for authored DSL syntax or compatibility coverage.
  - [x] I4c. Removed obvious Kotlin helper contamination from current game authoring. The visuals image table no longer declares local `fun` helpers inside `RylweenGame.kt`; reusable DSL helpers `ansiFullScreen`, `ansiMapWindow`, and `bitmapMapWindow` carry that repetition instead. Current `listOf(...)` calls in the game source were replaced with domain helpers such as `itemPools(...)` and `acceptedText(...)`. Remaining raw `val` script locals are tracked under I9 because they require source-script language work, not just cleanup.
  - [x] I4d. Rechecked the `promptDrop`, `journalEmpty`, `inventoryHeader`, and command-label warning surface. `promptDrop` remains an intentional public authoring overload for structured reward/drop prompts, while the party prompt rows are consumed through `runtimeSystems.prompts.party` by party pages and now have ledger coverage. Dungeon/castle command-label builder fields are private because current command labels are model defaults emitted into `runtimeSystems.commands`, not authored receiver overrides.
  - [x] I4e. Continue the `Dsl.kt` split by moving cohesive authoring domains into smaller source files without changing their public package. Current status: closed for this warning-driven pass. Visual/static-screen, metadata/terrain/layout, item/realm, and character/mission/progress builders now live in focused same-package files. Runtime prompt/key builders remain in `Dsl.kt` for now because their current warnings are covered by runtime-system prompt/command ledger checks rather than a missing implementation path; split them only when a prompt-schema change makes the move pay for itself.
    - [x] I4e.1. Split visual asset and static-screen builders into `VisualsDsl.kt`, keeping the same `rylween.dsl` package and public authoring API.
    - [x] I4e.2. Split metadata, limits, terminal terrain, screen layout, and terrain semantics builders into `ContentCatalogDsl.kt`, keeping them as plain public content builders outside the script/kernel file.
    - [x] I4e.3. Continue with the larger item/realm builder block once its compatibility item-pool resolution helpers can move cleanly without obscuring the `GameBuilder` assembly flow. Closed by moving item catalog, item-pool, realm/upkeep/camp/hunt/time-of-day builders, and the item-pool normalization helpers into `ItemRealmDsl.kt` in the same public package. `GameBuilder` now reads as the assembly flow again while item-pool compatibility rewriting remains adjacent to the item/realm authoring surface.
    - [x] I4e.4. Split character, NPC placement, mission, progress, leveling, and character-creation builders into `CharacterMissionProgressDsl.kt`, keeping the same public `rylween.dsl` API while moving another cohesive content-authoring domain out of the script-kernel file.
- [x] I5. Readability and brevity pass after the DSL impl move. Scope: once package/facade cleanup is stable, examine small repeated call shapes and helper candidates such as repeated `requireValue(key, value)` checks in save parsing. Current status: save parsing already had the concrete `requireValue(key, expected)` helper for the compatible-save header check, so no extra abstraction was added there. The pass added `oneBasedRecordPrefix(...)` in `SaveFile.kt` for the repeated one-based location/NPC/journal/user/hero save record keys, which shortens current call sites without hiding any save invariant and is covered by the existing save/load runtime checks.
- [x] I6. Stabilize the authored game DSL editor/source model. Scope: make authored game content and companion scenario source resolve cleanly from a fresh Gradle import without a temporary IntelliJ-only workaround. Current status: the chosen exit is normal Kotlin source, not `.dsl.kts`; authored content lives in `game/src/main/kotlin/rylween/game/RylweenGame.kt`, replay fixtures live in `RylweenTestScenarios.kt`, and `RylweenCompiledGame.kt` is the stable facade. `game/build.gradle.kts` has no `src/main/dsl` source root and neither `:game` nor `:core` applies `kotlin.plugin.scripting`. The game module exposes `api(project(":core"))` for editor consumers, generated handles are marked as IDEA generated sources, and the ledger test documents this source model while rejecting a return of the script-template path.
- [x] I7. Text-window wrap width source of truth. Scope: keep the authored layout region as the source of the classic 26-column text window. Current status: removed the parallel `DEFAULT_TEXT_WINDOW_COLUMNS = 26` runtime fallback; direct runtime text rendering now passes the active `RuntimeScreenPort.textColumns` into flow/block rendering, while free-standing artifact/report rendering either accepts an explicit width or deliberately leaves flow unwrapped. The remaining active-code `26` is the authored `ScreenRegions.textWindow` layout width and its ledger assertion. Exit validation: `./gradlew ktlintFormat --console=plain`, `./gradlew check --console=plain`, and `git diff --check`.
- [x] I8. Authored text and prompt ownership cleanup before the source-IR rename. Scope: tighten the boundary found by the follow-up audit: `RylweenGame.kt` mixes `screen("...")`, `say("...")`, `screen { line(...) }`, `lines(...)`, `flow(...)`, and runtime-system prompt blocks; runtime code still has a few player-visible fallback strings among legitimate diagnostics/window chrome. Current status: true game prose/prompts touched by this audit now route through authored text or runtime systems, residual runtime-owned strings are classified as chrome/diagnostics/generated detail, and the first hard-wrapped prose pass converted obvious ANSI-width prose to renderer-wrapped `flowText`.
  - [x] I8a. Runtime player-facing string classification. Audit `runtime/src/main/kotlin` rendered string literals and classify each as authored content, frontend chrome, diagnostic, or invariant. Move true game prose into runtime systems or compiled scripts, starting with starvation/upkeep text in `WildernessActions`; keep missing compiled monster/scene/service text as diagnostics unless it can occur in normal play.
    - [x] I8a.1. Starvation death prose is now authored on `WildernessUpkeepRules.starvationDeath`, uses the typed `UpkeepText.memberName` binding, emits through `runtimeSystems.realm.upkeep.starvationDeath`, and renders in `WildernessActions` with only the live member name supplied by runtime state. This bumped the stable `runtimeSystems` schema to version 4 and added ledger/runtime coverage so the old hardcoded `"starved" / "to death!!!"` lines do not return as runtime-owned prose.
    - [x] I8a.2. Save-command completion text now renders authored `prompts.save.done`, and character-creation invalid-choice text now renders authored `prompts.characterCreation.invalidInput`; the runtime still owns generated key/path detail lines as frontend diagnostics. This advanced the stable `runtimeSystems` schema to version 7 for the added character-creation prompt row.
    - [x] I8a.3. The title-screen unavailable-save reason is now an authored `titleMenu.continueUnavailableReasonLabel` row consumed by the runtime menu renderer instead of a hardcoded runtime string. This bumped the stable `runtimeSystems` schema to version 8 and keeps the menu label/checkpoint text under the same prompt ownership as the rest of the title menu.
  - [x] I8b. Prompt schema cleanup. Replace phrase-fragment prompt fields such as invalid-slot prefixes/suffixes, town closed/unavailable suffixes, and save/creation suffixes with structured prompt rows or typed label fields where a runtime currently assembles player prose. Keep mechanical values such as inventory slot letters, save file paths, and score/status column labels host-owned unless they become authored prose.
    - [x] I8b.1. Inventory-use invalid-slot and not-usable prompt fragments are now full structured prompt rows (`invalidSlot`, `notUsable`) using typed `InventoryText.slotName` and `InventoryText.itemName` bindings. The stable `runtimeSystems` schema is version 5 for this prompt-row shape, and the old prefix/suffix prompt fields were removed from the DSL model and artifact reader/writer.
    - [x] I8b.2. The unused town unavailable/closed suffix prompt fields were removed from the DSL/runtime-system prompt schema, and character-creation save confirmation changed from the string fragment `saveConfirmationSuffix` to the authored text row `saveConfirmation`. This advanced the stable `runtimeSystems` schema during the cleanup, and the ledger rejects the old field names in emitted artifacts.
    - [x] I8b.3. Title-menu disabled-choice reason text now uses the typed authored label `continueUnavailableReasonLabel`; generated save-file paths and pressed-key details remain runtime-owned operational detail.
  - [x] I8c. Game DSL text-shape rules. Current status: reusable text data now
    uses the preferred constructor vocabulary `literalText`, `structuredText`,
    and `flowText`; script display uses `writeText`; read-and-dismiss pages use
    `showTextAndWait`. The older `inlineText`, `textLines`, `flow`, text-output
    `screen`, `say`, and `readScreen` aliases were retired by I19 after the
    authored game source and implementation code stopped using them.
  - [x] I8c.1. Text display vocabulary cleanup. Audited the author-facing text
    constructor and display action surface (`inlineText`, `textLines`,
    `structuredText`, `flow`, `screen`, `say`, `readScreen`, `emit`, and related
    helpers) and settled on explicit names that distinguish text data from text
    display side effects. `TextDsl.kt` now documents literal/structured/flow
    data constructors, `ScriptScope` exposes `writeText` and
    `showTextAndWait`, and the compiled/runtime model remains stable without
    legacy display aliases. The compiled-game ledger rejects legacy constructor
    and read-screen vocabulary in current game authoring, rejects the retired
    alias functions in the authoring API, and keeps `screen(...)` reserved for
    static/reusable screen declarations rather than script writes.
  - [x] I8c.2. Hard-wrapped prose cleanup. Converted the obvious manual
    26-column prose in realm fatigue/camp prompts, castle runtime text, title
    and utility prompts, and Castle Ydarg scene narration to `flowText` so
    ANSI, bitmap, and future renderers own wrapping. Left menus, command
    choices, status/title cards, tables, and dynamic structured prompts as
    explicit lines because their line breaks are presentation semantics rather
    than old wrapping.
  - [x] I8d. Guardrails. Add ledger or validation coverage that the current emitted game has no `[missing prompt: ...]` rows, that new runtime-rendered player prose goes through authored text/prompt models, and that any remaining runtime literals are documented as diagnostics/chrome instead of accidental game strings.
    - [x] I8d.1. The compiled-game ledger now rejects emitted external bundle artifacts containing `[missing prompt:` so runtime-system prompts cannot silently fall back to placeholder prose in the shipped game.
    - [x] I8d.2. Runtime-owned rendered strings now use explicit classification
      helpers for developer diagnostics, host chrome, and generated operational
      details. The compiled-game ledger rejects direct `screen.writeText` /
      `screen.showMessage` literal `listOf(...)` calls and unclassified compiled
      text-ref placeholders, forcing future game prose through authored content
      or runtime-system prompts.
  - [x] I8e. Input-binding ownership audit. Recheck whether `Dsl.kt` and
    `dsl/impl` should expose literal key presses for static commands/prompts,
    or whether the authored model should name semantic commands and labels while
    a separate input profile supplies default keyboard bindings for ANSI,
    bitmap/server, and future controller/frontends. Preserve current C-style
    keys as the default profile and keep runtime session inputs semantic, but
    avoid making physical key choices part of reusable game-rule declarations
    where a frontend-specific binding layer would be cleaner.
    Decision: keep `InputBindingModel` as the authored default input-profile
    row for current static commands because the legacy key and display label are
    part of visible help/prompt content. Runtime command loops continue to
    dispatch through compiled `RuntimeCommandActionId` values and centralized
    binding matchers rather than repeated physical keys. If a later controller,
    accessibility, or frontend-specific profile is needed, add that as a layer
    over semantic command ids instead of moving key literals into runtime loops.
    `InputBindingModel` now documents this ownership split, and the
    compiled-game ledger checks the title, wilderness, dungeon, castle, and cave
    command paths for semantic dispatch.
- [x] I9. Replace the ambiguous authored source-node model with an explicit script-source IR. Scope: keep an IR, but make it obviously authoring/compiler-owned rather than runtime bytecode. The runtime contract remains the compiled bundle (`CompiledOpcode` plus typed operands and tables); the DSL should not emit compiled instructions directly because validation, diagnostics, source paths, and the oracle need high-level structure.
  - [x] I9a. Introduce the new source model. Add `AuthoredScript`, `ScriptBlock`, and a sealed `ScriptStatement` model split by ownership categories: text/view, control flow, input, state mutation, and child/gameplay handoff. Reuse the existing expression/lvalue/text models initially; do not widen behavior in this slice.
    Introduced `ScriptSourceModel.kt` with authored script/block/source
    statement categories, classified existing concrete source nodes as source statements,
    and made `ScriptSourceRoot` carry `AuthoredScript` while preserving the
    initial concrete-node compatibility view for validation and bundle lowering.
    The compiled-game ledger now guards the source model and category markers.
  - [x] I9b. Migrate builders without changing emitted bundles. Have `ScriptScope` record `ScriptStatement` blocks, keep a temporary compatibility bridge for existing call sites/tests, and prove generated compiled bundle output stays byte-for-byte or table-equivalent for the current game.
    `ScriptScope` now records through `ScriptBlockBuilder` as
    `ScriptStatement` values, while `recordStatementNodes(...)` remains the
    narrow concrete-node capture used by nested builders and bundle lowering.
    This keeps emitted runtime artifacts table-equivalent while I9c migrates
    walkers/compiler code to source-statement names.
  - [x] I9c. Move script-owning content models to the new source IR. Replace
    raw concrete-node list ownership on script root models (`SceneModel`,
    `DialogueBranchModel`, `SpellModel`, `ItemUseModel`,
    `DungeonEventModel`, question actions, and similar roots) with
    `AuthoredScript`/`ScriptBlock` storage plus short-lived concrete-node
    accessors. Builders should produce source scripts directly; those accessors
    should be marked as migration bridges and kept only until I9f.
    Scene, dialogue branch, spell, item-use, dungeon-event, and question-action
    models now store `AuthoredScript`. Their `statementNodes` properties are
    concrete-node views only, and root builders call `recordAuthoredScript(...)`
    or build `ScriptBlock` values directly. Source-root analysis now reads the
    `script` field instead of constructing roots from raw concrete-node lists.
  - [x] I9d. Move analysis/compiler/validation to the new names. Update
    `ScriptSourceAnalysis.kt`, `DslValidation.kt`, and `CompiledBundle.kt` to
    walk/lower `ScriptStatement` through a small visitor or category-specific
    helpers so source analysis does not depend on one large concrete-node bucket.
    `ScriptSourceStatement`/`visitScriptStatements` now own the source traversal
    vocabulary, validation consumes that shared walker, and `CompiledBundle.kt`
    lowers root scripts through `compileScript`/`compileStatement` while
    `ScriptStatementNode` remains the concrete authored node implementation.
    - [x] I9d.1. Rename source walking types/functions from operation wording to
      statement wording (`ScriptSourceStatement`, `visitScriptStatements`,
      `anyScriptStatement`) while preserving source-map paths and branch labels.
    - [x] I9d.2. Split nested traversal by source structure: control-flow
      branches, input/menu branches, text-input match/miss branches, and member
      or monster loops. Keep runtime service loops out of this walker.
    - [x] I9d.3. Lower `AuthoredScript`/`ScriptStatement` to
      `CompiledInstruction` in `CompiledBundle.kt` through `compileStatement`.
      The compiler may still pattern-match concrete legacy statement classes in
      this slice, but the public compiler boundary should be source-script
      terminology rather than `compileOps`.
    - [x] I9d.4. Update validation to describe diagnostics in source-statement
      terms and push shared recursive walking into source-analysis helpers
      instead of each validator owning ad hoc concrete-node recursion.
  - [x] I9e. Keep the oracle explicit. The authored interpreter/parity surface
    should consume the new source IR only under `AuthoredScriptOracleApi`/tests,
    while production runtime code continues to consume compiled bundle rows. Add
    or keep architectural tests that reject production imports of the authored
    interpreter/source IR.
    The old active authored interpreter files are already absent. The ledger now
    additionally scans production runtime source roots and rejects imports or
    qualified references to `AuthoredScript`, `ScriptStatement`, source-analysis
    walkers, or the retired source-node name, keeping the new source IR under
    DSL/compiler/tests.
  - [x] I9f. Retire the old source-node name and compatibility bridges. Builders,
    compiler, validation, source analysis, and oracle tests now use the new
    source-script names; active Kotlin sources no longer expose `List<Op>`,
    `.ops`, or `fromOps`/`toOps` bridges. Docs/plan text now says "source script
    IR" for authoring and "compiled opcodes" for runtime, preserving true
    compiled-payload `*Ops` fields where those are still the schema names.
    - [x] I9f.1. Retire the active Kotlin `Op` type name. The concrete authored
      source node family is now `ScriptStatementNode`, and the ledger rejects
      reintroducing the standalone `Op` name in active Kotlin source roots.
    - [x] I9f.2. Remove or rename the remaining root/block compatibility
      bridge surface: `ops` accessors are now `statementNodes`,
      `recordScript(...)` is now `recordStatementNodes(...)`, and
      `fromOps`/`toOps` are now `fromStatementNodes`/`toStatementNodes`. The
      remaining private compiler cast is named `asStatementNode` so it describes
      the current concrete-node bridge instead of reviving operation vocabulary.
    - [x] I9f.3. Rename source-branch fields such as `thenOps`, `defaultOps`,
      `matchOps`, and `missOps` without disturbing true compiled-payload
      `*Ops` fields.
    - [x] I9f.4. Clean historical plan/docs wording that still described authored
      source nodes as operations, while preserving true compiled-opcode references.
  - [x] I9g. Document and enforce loop ownership. Use names/docs/tests that
    distinguish authored script-control loops (`loop`, `breakLoop`,
    `forEachMember`, `forEachMonster`, lowered to compiled script instructions)
    from runtime mode/service loops (wilderness, town, dungeon, castle, standard
    battle). Runtime service loops may stay native when they own engine cadence,
    input, rendering, RNG, persistence, or live subsystem state, but any
    Rylween-specific prose, prompt wording, reusable choice labels, formulas,
    rewards, or gameplay policy inside those loops should be authored
    content/runtimeSystems instead.
    `ScriptSourceModel`, `ScriptScope`, and `ScriptStatementNode` now document
    authored script-control loops as compiled VM flow only. `GameRuntime`,
    `DungeonRuntime`, `CastleRuntime`, `BattleRunner`, and `ShopService` now
    document their native runtime mode/service loop ownership, and the ledger
    guards those comments, the compiled `Loop` lowering/execution path, and the
    absence of raw Kotlin `for`/`while` loops in the authored game source.
  - [x] I9h. Remove raw Kotlin locals from authored game scripts. Replace
    `val amount = localInt(...)`, `val caster = currentCaster`, and similar
    imperative declarations with source-script DSL constructs that still compile
    to explicit IR, so game content authors do not need Kotlin
    variable-declaration knowledge for common temporary values.
    `localInt(...)`, current-caster, current-caster-cost, ask/choice member,
    inventory-slot, wilderness-direction, and battle-target helpers now expose
    block forms. Authored game script bodies use those DSL blocks instead of raw
    nested Kotlin `val` temporaries, and the ledger guards against reintroducing
    deep script-local `val` declarations in `RylweenGame.kt`.
- [x] I10. Author-facing DSL guide and design consistency audit. Run this after
  the I9 source-IR cleanup so the docs describe the settled model. First audit
  the game DSL for inconsistent shapes, misplaced implementation concepts, and
  repeated mechanisms that should be unified; then write an author guide covering
  DSL syntax, major constructs, how to extend or modify the game, and examples
  that demonstrate dynamic authoring such as adding scenes, prompts, shops,
  dialogue, rewards, maps, events, and runtime-system text without touching
  runtime implementation code.
  Current status: `docs/dsl-authoring.md` now documents the live
  `RylweenGame.kt` source model, syntax rules, extension workflow, examples for
  scenes/missions/dialogue/shops/prompts/maps/dungeon events/state fixtures, and
  placement rules for game DSL vs. reusable DSL vs. runtime/frontend/tooling.
  The audit corrected current-facing `.dsl.kts` and authored `Op` wording in
  comments/plan/spec text and leaves the remaining implementation follow-ups
  tracked under I4b, I4e.3, I5, and I8a/I8b.
- [x] I11. Straighten the current XP thresholds. Inconsistency found: the
  authored `xpThresholds(...)` row was monotonic but not cadence-consistent
  around level 10, and the index/level mapping was implicit. Fix applied:
  current-game XP now keeps level 1 at index 1 with 0 XP, raises level 10 to a
  smooth 60,000 XP point, and uses steadily widening high-level bands ending at
  the existing 2,800,000 XP cap. `LevelingBuilder.xpThresholds` now documents
  the index mapping and why runtimes consume an explicit compiled table instead
  of deriving a hidden formula. Validation rejects non-positive level-1 mapping
  and non-increasing level thresholds. Future formula work should add a typed
  authoring model only when class-specific or multi-game progression needs it,
  and should still compile to the explicit threshold table consumed by runtimes.
- [x] I12. Move private image-id objects out of the current game authoring
  surface. Inconsistency found: generated `Images.Static/Scene/Backdrop`
  handles already existed, but encounter, portrait, castle, dungeon, and
  mission image ids were hand-written as private Kotlin objects in
  `RylweenGame.kt`. Fix applied: the catalog generator now recognizes
  `Images.Encounter`, `Images.Portrait`, `Images.Castle`, `Images.Dungeon`,
  and `Images.Mission` groups and emits their exact slugs, so game authoring
  uses one generated image-handle mechanism instead of private Kotlin handle
  objects.
- [x] I13. Consolidate text display and prompt-building shapes. Inconsistency
  found: current DSL code still has many near-equivalent text entry points:
  `writeText`, `showTextAndWait`, `say`, compatibility `screen/readScreen`,
  prompt builders with `vararg String`, prompt builders with
  `StructuredTextBlockBuilder`, `literalText`, `structuredText`, `flowText`,
  and implementation defaults still using `inlineText`. Fix applied:
  `writeText(text)` is the canonical immediate display verb,
  `showTextAndWait(text)` is the canonical write/wait/clear page verb, and
  `literalText`/`structuredText`/`flowText` are the canonical text-data
  constructors. I19 later retired `say`, script `screen`, `readScreen`,
  `inlineText`, `textLines`, and old `flow` compatibility bridges after the
  current game source and implementation code stopped calling them.
  Implementation defaults now use `literalText` rather than `inlineText`, and
  open-coded structured-text builder calls were collapsed to `structuredText`
  where the builder was not a specialized receiver. Ledger coverage now rejects
  current-game `readScreen`, script `say`, legacy text constructors, retired
  alias functions in the authoring API, and direct triple-quoted prose passed to
  `writeText`; prose-shaped triple-quoted text should enter through
  `flowText("""...""")` unless line breaks are intentional structured layout
  rows. Remaining repeated `vararg`/structured prompt setter boilerplate is
  tracked separately under I17/I19 so it can be collapsed by builder family
  without removing useful domain method names.
- [x] I14. Add filtered non-interactive party-member selectors. Inconsistency
  found: `MemberChoiceFilter` already exists for player prompts and battle has
  `battle.randomAliveMember`, but non-combat authored scripts still sometimes
  use fixed `party.member(3)` or `party.member(4)` for narration. This can name
  a dead member or a missing member. Fix applied:
  `PartyRef.firstMember(...)` and `PartyRef.randomMember(...)` now compile to a
  typed `PartyMemberQuery(selection, filter)` source/compiled expression rather
  than the stringly battle-query path. Normal script execution returns one-based
  member numbers for `PartyRef`/text bindings; battle execution adapts the same
  query to the battle runner's existing zero-based member-slot convention, while
  `battle.randomAliveMember` remains the explicit combat selector. The Erethor
  Lute chamber and Ogre Lord note narration now use
  `party.randomMember(MemberChoiceFilter.AliveOnly)`. Runtime selector tests
  cover one-based filtering, dead-member exclusion, spellcaster filtering, random
  choice indexing, and the empty-match `0` fallback.
- [x] I15. Unify reward and direct resource mutation vocabulary. Inconsistency
  found: normal authored scripts can use `reward { gold(...); items(...) }`,
  direct state slots such as `party.gold += ...`, inventory-pool consumption
  helpers, and secret/debug-only `addGold(...)`. The runtime implementation
  applies rewards through one compiled opcode, while some service/debug flows
  still looked like separate mechanisms to authors. Fix applied:
  `reward { ... }` is documented as the player-facing gold/inventory grant
  vocabulary, `gainGold(...)` compiles simple player-facing gold finds through
  that same reward opcode, and `gainTorches(...)`/`gainFood(...)` name simple
  resource gains without exposing raw scalar increments in authored content.
  Direct state slots remain appropriate for costs, damage, timers, counters, and
  local mechanics; `pay(...)` remains the preferred gold-cost helper; and
  `secrets { addGold(...) }` is documented as debug/sysop-only. Current dungeon
  gold and torch finds now use the named gain helpers, while item-bearing mission
  rewards still use `reward { ... }` with placement/drop policy. Ledger coverage
  rejects direct current-game `party.gold +=`, `party.torches +=`, and
  `party.food +=` player-facing increments.
- [x] I16. Reorder the current game DSL into an author-first hierarchy.
  Inconsistency found: the current file order is historically accreted, so
  realm configuration, creation rules, progress/leveling, and other global
  systems do not all appear before lower-level content. Fix plan: reorder
  `RylweenGame.kt` conservatively into global identity/balance/progress,
  presentation/layout, catalogs, systems, world/map, missions/scenes/dialogue,
  and fixtures. Keep generated handle names stable and run bundle/ledger
  equivalence checks so this is source readability only, not a content change.
  Fix applied: the top-level current-game source now documents and follows an
  author-first order: identity/state (`metadata`, `limits`, `flags`, `secrets`),
  presentation (`visuals`, `layouts`, `terminal`), world/systems (`terrain`,
  `worldMap`, `realm`, `castle`, prompts, pacing), catalogs/progression, rules,
  content tables, and behavior. The move was deliberately conservative: small
  self-contained debug/presentation blocks were lifted earlier, and castle hook
  scenes were moved down with the other behavior scripts. Ledger coverage now
  asserts the top-level source order and hierarchy comment.
- [x] I17. Audit repeated painful call shapes and collapse only real
  duplication. Inconsistency found: builders repeat patterns like
  `foo(vararg lines)`, `foo { line(...) }`, nullable `fooText` storage,
  `TextLineSink` adapters, and repeated menu/prompt/key field groups. Fix plan:
  inventory these shapes by builder family, add one or two shared internal
  receiver helpers where they remove boilerplate without changing authoring
  syntax, and leave explicit domain names when they carry different gameplay
  meaning. Each cleanup should have a ledger test proving the current game
  still emits the same prompt rows. Fix applied: the audit separated real
  construction duplication from intentionally distinct author verbs. Repeated
  `RandomItemOfferTextBuilder().apply(body).build()` call sites now go through
  one internal `randomItemOfferText(...)` helper, and shop dynamic text setters
  now reuse the existing `shopText(...)` helper. Domain methods such as
  `offer`, `bought`, `unitPrice`, `singlePrice`, `sold`, and `declined` remain
  explicit because they name different gameplay rows even when their setter
  mechanics match. Ledger coverage asserts the helper consolidation and locks
  the current smith, merchant, and tavern dynamic text rows.
- [x] I18. Document current mission chains. Inconsistency found: missions,
  dialogue-started quest flags, random-step triggers, location handles, and
  power-item unlocks were spread across the authored source and tests. Fix
  applied: `docs/MISSIONS.md` now lists each current mission chain, trigger
  style, main flags, coordinates or runtime-placement notes, rewards, and
  unlocks, and calls out the hardcoded member-number narration debt tracked by
  I14.
- [x] I19. Keep a near-duplicate mechanism register and retire split shapes
  deliberately. Rule: if two DSL/runtime constructs do nearly the same thing but
  differ in naming, owner, renderer, continuation behavior, or validation, add
  the pair here before refactoring it. Resolved pairs: I13/I19 closed
  `writeText`/`showTextAndWait`/text-output `screen`/`readScreen`/`say` vs.
  reusable `TextBlock` data by making `writeText` the only normal text-output
  script verb, `showTextAndWait` the read-and-dismiss page verb, and reserving
  `screen(...)` for reusable/static screen declarations. I13/I19 also closed
  `inlineText`/`textLines`/old `flow` vs. `literalText`/`flowText`/
  `structuredText` by retiring the legacy constructor aliases after current
  authoring stopped using them. I14 closed prompt member filters vs. battle-only
  random alive-member selection vs. hardcoded `party.member(n)` narration by
  adding typed filtered non-combat selectors. I15 closed `reward { ... }` vs.
  direct player-facing resource gains vs. secret/debug `addGold` by documenting
  ownership and adding current-game gain helpers. I12 closed generated image
  handles vs. private image-id objects by generating all current image handle
  groups. I8e/H16/J1b closed authored command/key bindings vs. runtime fallback
  defaults by moving active command contracts into runtime-system rows while
  keeping frontend key decoding and diagnostics host-owned. G5/G7/C8 closed
  manual/journal/score/heroes popup overlap by classifying static resources,
  runtime-backed reports, and the mutable party journal separately. I17 closed
  the repeated random-offer/shop dynamic text-builder construction while keeping
  domain row names explicit. Retired split shapes: script `screen`/`readScreen`/
  `say` aliases and `inlineText`/`textLines`/`flow` aliases were removed from
  the authoring API; ledger coverage rejects their return. Remaining similar
  shapes are intentional: one-key choices and text-input prompts share compiled
  continuation machinery but keep different input contracts; authored script
  loops and native service loops both compile/execute looping behavior but live
  at different ownership levels, with compiled script loops used for authored
  control flow and domain runners used for reusable battle/shop/town/dungeon
  state machines. Future duplicate-looking constructs should be registered here
  before refactoring, but this item is closed for the current known set.
- [x] I20. Document the prompt/input taxonomy and add guardrails for future
  prompt shapes. Follow-up discovered during I19: one-key menu choices, Y/N
  helpers, member/inventory/monster pickers, and free-form text input all share
  compiled continuation plumbing, but they should not collapse into one generic
  "prompt" DSL because their validation, accepted inputs, UI affordances, and
  resume payloads differ. Fix plan: add a concise table to
  `docs/dsl-authoring.md` explaining when to use `menu`, `choice.yesNo`,
  `choice.member`, `choice.inventorySlot`, `ask.monsterAlive`, and
  `choice.textInput`; then add ledger coverage that new current-game prompt
  forms are either in that taxonomy or explicitly documented before use. Fix
  applied: `docs/dsl-authoring.md` now has a Prompt And Input Taxonomy table
  covering script menus, Y/N and short menu helpers, member/inventory/monster
  pickers, digit/direction prompts, random item offers, and free-form text
  input. The guide also calls out display-only text actions and runtime/domain
  prompts as separate ownership areas. Ledger coverage now fails if a
  current-game `choice.*` or `ask.*` prompt form appears without being listed in
  that taxonomy.
- [x] I21. Document and guard authored script loops versus reusable domain
  runner loops. Follow-up discovered during I19: both source-script `loop { ... }`
  and runtime services such as shops, battles, towns, dungeons, and castles can
  loop and block/resume, but they operate at different ownership levels. Fix
  plan: document that source loops are authored control flow compiled into the
  VM, while domain runners are host-owned state machines parameterized by
  authored content/runtime-system rows. Add an architectural ledger check that
  new runtime loops with player-facing text, menu policy, rewards, or balance
  must consume existing authored models or introduce a new explicit model rather
  than hardcoding those facts. Fix applied: `docs/dsl-authoring.md` now has a
  Script Loops And Runtime Loops section that separates `loop { ... }` /
  `breakLoop(...)` compiled script control flow from host-owned wilderness,
  town, dungeon, castle, shop, and battle state machines. Ledger coverage checks
  that the guide, source-script APIs, and runtime-loop implementations keep this
  ownership boundary documented, and rejects representative old gameplay text
  from being hardcoded in runtime mode loops.
- [x] I22. Revisit repeated text-field setter overloads only when another
  concrete edit creates real churn. Follow-up discovered during I17/I19: many
  builders intentionally expose domain methods with matching
  `vararg String`/structured-text overload pairs. The public method names should
  remain domain-specific, but the implementation can still use shared private
  helpers when repeated setter mechanics become noisy. Fix plan: on the next
  prompt-builder change, either add a small internal helper/delegate for
  assigning `TextBlock` fields, with row-equivalence tests, or explicitly record
  why the duplicate is clearer left local. Fix applied: the I23 prose pass was
  the concrete churn point. Conversation screens, chitchat prompts, job replies,
  rumors, castle pitfall traps, and terrain transition text now have narrow
  `TextBlock` overloads so plain prose can use `flowText(...)` directly. These
  stay as local domain methods rather than a generic setter delegate because the
  builders append to different model collections and the public call sites read
  better when the method name still says what game content is being authored.
- [x] I23. Continue the literal-line prose audit. Follow-up discovered during
  I21: some `lines(...)` calls still preserve old ANSI-era wrapping where the
  text is plain prose and could be `flowText(...)` so each runtime wraps it
  naturally. Fix plan: keep literal line lists for menus, tables, fixed layouts,
  and dynamic placeholder composition; convert plain prose fields to `flowText`
  or a domain text-field overload; and add narrow ledger coverage for each
  converted area. First pass applied during I21: merchant catalog item
  descriptions now use `flowText(...)` instead of trailing literal line arrays.
  Follow-up applied: church/inn/tavern prose, mage/priest/item-effect prose,
  conversation table text, rumors, castle pitfall text, and terrain transition
  prose now use `flowText(...)` through direct `TextBlock` overloads. Remaining
  game-source `lines(...)` calls are intentional fixed prompts, menus, battle
  status rows, or dynamic structured composition. Ledger coverage guards
  representative old ANSI-split prose from returning.

### J. Optional non-JVM runtime productization

J-series work is the convergence path if we decide that multiple runtimes must
run the same compiled game artifact. "Same interpreter" means the same compiled
VM contract: identical bundle bytes, opcode/operand schema, native-service
allowlist, continuation/save-frame model, and conformance scenarios. Kotlin, C,
server, or bitmap frontends may have language-specific implementations, but a
runtime does not count as J-compliant unless it reads the shared J1 artifact and
passes the J2 VM/playthrough conformance suite.

Detailed J2 implementation plan: [j2-compiled-vm-plan.md](j2-compiled-vm-plan.md).
Portable runtime implementation contract:
[compiled-vm-runtime-spec.md](compiled-vm-runtime-spec.md).

Native executable bundles are allowed as deployable runtimes, but they are not
the canonical content contract unless we deliberately add a generated-source or
ABI-stable native artifact layer. The safer API boundary is content/state data
plus a versioned host/VM contract: native C/C++/Rust binaries implement that
contract, while the authored game remains portable JSON/data.

- [x] J0. Native compiled-boundary smoke. Current status: `emitNativeSmokeBundle` writes a deterministic `build/compiled/rylween-native-smoke.rsmoke` artifact from the current `CompiledGameBundle` after the in-process compiled loader validates it. The artifact carries the compiled string table, handle table, script roots, typed opcodes, typed operand trees, and state count. `NativeSmokeArtifact.kt` owns the Kotlin writer plus the schema tag constants mirrored by `compileNativeSmokeLoader`, which builds the matching minimal C loader from `tools/src/main/c/compiled-smoke/rylween_bundle_smoke.c`; `checkNativeSmokeBundle` runs the C loader as part of `check`. The readers validate the artifact header, string ids, handle refs, opcode ranges, runtime text-part ranges, nested instruction bodies, and all currently emitted VM operand shapes. Schema tag values are named enums/constants in both Kotlin and C rather than repeated as magic numbers. This is a boundary/API outage detector, not a full native game runtime.
- [x] J1. External compiled-bundle artifact. Closed for the current artifact-backed runtime target. Scope delivered: deterministic emitted artifact for all `CompiledGameBundle` content/state tables, stable schema/version/hash metadata, a Kotlin external artifact reader, a C/native external artifact reader over the same bytes, Kotlin launcher/test entry points that boot from the external artifact instead of generated in-memory objects, round-trip validation against the in-process compiled loader, and compatibility tests. Format decision: the canonical J1 artifact is strict deterministic JSON (`build/compiled/rylween-bundle.json`), not a custom binary. The in-memory `CompiledGameBundle` remains the Kotlin compiler/test oracle, and the J0 binary smoke remains a narrow VM-boundary outage detector. Current status: `emitExternalCompiledBundle` writes schema-version 3 JSON for every compiled content/state table group plus stable `runtimeSystems` data and the Kotlin `runtimeFacade`, fixed section order, universe metadata, and a SHA-256 hash over the sections object; `ByteArray.readExternalBundleArtifact()` parses and validates that JSON; `ByteArray.readExternalRuntimeSystemsSummary()` validates the non-reflective stable runtime-system section; `ByteArray.readExternalCompiledGameBundle()` materializes the artifact back into a typed `CompiledGameBundle` and validates it through `CompiledBundleLoader`; `ByteArray.readExternalRuntimeGame()` materializes the Kotlin runtime-facing `RylweenModel`, recompiles it, and rejects facade/table drift; `checkExternalCompiledBundle` validates the materialized artifact and runtime systems; `inspectExternalCompiledBundle` writes a readable section/count/checksum disassembly; ANSI `--bundle` boots from the loaded runtime facade and `checkKotlinAnsiBundleBoot` runs the title-screen launcher through that path; `KotlinHeadlessHost` boots from the same artifact; `checkNativeJsonBundle` compiles and runs a C reader over the same JSON, including native canonical `sections` SHA-256 verification. Closeout decision: do not widen stable direct schemas speculatively beyond `runtimeSystems`; add new schema-versioned sections only when a real non-Kotlin playable runtime needs direct consumption instead of the Kotlin `runtimeFacade`. Native JSON reader decision: the first C reader reuses the legacy JSON5 parser source with a small compatibility shim instead of adding another JSON parser.
  - [x] J1.1. JSON artifact frame, Kotlin reader, and inspector. Kotlin emits and validates the all-table strict JSON artifact, hashes the canonical sections object, and Gradle writes a readable inspection summary. The retired binary section manifest is no longer the product artifact.
  - [x] J1.2. Kotlin JSON materializer. The strict JSON artifact now round-trips into the typed `CompiledGameBundle` model, including content tables, scripts, and state images, and the artifact check runs the existing compiled-bundle validator over the loaded object.
  - [x] J1a. Kotlin compiled-bundle ANSI boot path. `RylweenAnsiMain --bundle <file>` reads the J1 JSON through the Kotlin artifact reader, builds the runtime-facing `RylweenModel`/`GameModel` facade from that file, validates universe/version/content hash by re-emitting the same artifact, and boots existing Kotlin sessions from the loaded content instead of `RylweenCompiledGame.nextDsl`. The deterministic test asserts JSON -> runtime facade -> JSON byte stability, compiled-table equality, loaded-world-map session construction, prompts/terminal/realm presence, and authored castle content presence. `checkKotlinAnsiBundleBoot` emits the JSON bundle, installs the ANSI launcher, and opens the title screen through `--bundle`.
    - [x] J1a.1. ANSI artifact compatibility gate. `RylweenAnsiMain --bundle <file>` reads the strict JSON bundle, validates its hash/section summary against the loaded runtime facade, audits the verified hash, and then boots normally.
    - [x] J1a.2. Runtime facade in the bundle. Schema version 2 added `runtimeFacade`, a deterministic JSON encoding of the complete Kotlin runtime-facing game graph: authored content, prompts, terminal presentation, realm balance, castle content, and start states. The loader recompiles that facade and requires it to match the normalized compiled sections, so the facade cannot silently diverge from the dense table bundle.
  - [x] J1b. Stable non-Kotlin system schemas. Closed for the current boundary. The current `runtimeFacade` is complete for the Kotlin runtime and remains intentionally Kotlin-shaped; the stable non-Kotlin surface is `runtimeSystems` plus the already typed compiled table sections (`strings`, `handles`, `terrain`, `worldMaps`, `scripts`, `states`, etc.). C/C++/Rust/server runtimes should consume stable typed sections first and add schema-versioned direct system sections only when they become real playable consumers; they should not force a duplicate of the whole Kotlin object graph by reflex.
    - [x] J1b.1. Stable runtime-system section. Bundle schema version 3 added `runtimeSystems`, a non-reflective section with its own schema version; the current runtime-system schema is version 8 after adding authored wilderness starvation death text under `realm.upkeep`, structured inventory-use invalid-slot/not-usable prompt rows, structured character-creation save-confirmation text, character-creation invalid-input text, and the authored title-menu disabled-save reason label. It carries stable JSON for realm upkeep/camp/hunt balance, reusable prompts, terminal presentation/layout/static resources, wilderness/dungeon/town/speak command bindings, wilderness event/transition rules, town service boundaries, score/heroes metadata, speak text tables and question-action script roots, and stable `castles[]` entries with `kind = "gridCastle"`. The Kotlin reader rejects reflective `_type` markers in this section, the deterministic bundle test asserts key prompt/command/castle presence, and `checkExternalCompiledBundle` reports the active runtime-system schema plus castle count.
    - [x] J1b.2. Dungeon key ownership cleanup. `DungeonSystemModel` now owns `DungeonKeysModel`, the current game authors the dungeon bindings under `dungeons { keys { ... } }`, dungeon sessions start/resume with `content.dungeons.keys`, and `runtimeSystems` emits those authored keys instead of constructing `DungeonKeysModel()` defaults inside the bundle writer.
    - [x] J1b.3. Stable direct schemas for any remaining non-Kotlin-owned systems. Closeout audit found no current non-Kotlin-owned runtime system needing another direct schema beyond `runtimeSystems`. The C reader validates the active stable boundary, and Kotlin ANSI/headless boot from the complete `runtimeFacade`; future native/bitmap/server runtimes that need more direct data should add a schema-versioned section under their implementation task rather than reopening J1 for speculative duplication.
  - [x] J1c. C/native JSON reader scaffold. `compileNativeJsonBundleReader` builds `tools/src/main/c/bundle-json/rylween_bundle_json.c` together with the legacy `json5.c` parser and a small native shim, and `checkNativeJsonBundle` runs it against `build/compiled/rylween-bundle.json` as part of `check`. The reader validates format, schema version 3, section order, canonical `sections` SHA-256 hash, non-empty core table row counts, `runtimeSystems.schemaVersion`, required command/prompt groups, and each `castles[]` row as a `gridCastle` with matching command groups.
  - [x] J1d. Native canonical hash verification. The C reader now streams the parsed `sections` node through the same compact JSON form used by the Kotlin writer and verifies `bundleContentHash` with an internal SHA-256 implementation. The native reader can therefore reject tampered bundle content without relying on the Kotlin reader as a trust boundary.
- [x] J2. Compiled-op executor and conformance suite. Active branch plan: [j2-compiled-vm-plan.md](j2-compiled-vm-plan.md). Scope: opcode dispatch, typed operands/expressions, locals, waits, menus/input continuations, child session calls, saved frames, native service registry validation, a Kotlin compiled-op executor or adapter that can run the same VM contract, and a native/server scenario driver that can run the same semantic playthrough/checkpoint shape as the Kotlin scenario harness or an emitted equivalent tape. Exit criteria: all runtimes claiming runtime parity read the J1 artifact, execute the J2 VM contract rather than private gameplay scripts, and pass the shared conformance scenarios. J0/J1 are closed as artifact and loader contracts; J2 consumes them rather than reopening artifact format work. Closed decision: Kotlin production hosts now execute script roots through the compiled VM; native remains a boundary/probe unless a separate product task is opened for a playable non-JVM or server runtime.
  - [x] J2.0 #1. Opcode arity/type contract. `CompiledOpcodeContract` is now the single executable schema for every `CompiledOpcode`, including optional-operand shapes. `RylweenOpcodeContractTest` asserts schema completeness, slot mapping coverage, and that every emitted instruction in the current J1 bundle matches its declared operand shape.
  - [x] J2.0 #2. Loader-side contract enforcement. `CompiledBundleLoader` now runs `CompiledOpcodeContract.validateInstruction` during recursive script validation and rejects malformed compiled instructions with `UnknownOpcode` or `WrongShape` diagnostics before any runtime path can execute them. A regression mutates a real emitted bundle by replacing an operand with the wrong slot kind and asserts `loadCompiledGame()` rejects it.
  - [x] J2.0 #3. Stable public VM diagnostics taxonomy. `rylween.runtime.vm.VmDiagnostic` defines the VM failure surface with stable `VmDiagnosticKind` variants for bad opcode, bad operand, missing handle, missing native op, invalid lvalue, bad continuation frame, and unsupported host service. Every variant carries `ScriptRootRef`, instruction index, and `List<Int>` operand path; the compiled-bundle loader now maps opcode-contract failures through `BadOpcode`/`BadOperand` diagnostics while preserving the `UnknownOpcode`/`WrongShape` reasons in the failure message.
  - [x] J2.0 #4. Native-service registry shape. `NativeServiceCatalog` defines effect/int/string native service descriptors keyed by path, with arity and argument slot kinds derived from the existing `NativeAllowlist` surface. `CompiledBundleLoader` accepts a catalog, defaults to the shared catalog, and rejects `NativeCall` instructions whose path is not cataloged, whose descriptor is not an effect service, or whose string-argument shape does not match the descriptor. A regression mutates a real emitted bundle into an unknown `NativeCall` path and asserts the loader rejects it with `MissingNativeOp`.
  - [x] J2.1. Pure compiled VM kernel. `rylween.runtime.vm.CompiledVm` now executes validated compiled scene programs for the non-interactive opcode slice (`Screen`/`Emit`/`EmitIndexed`, view/pause events, locals/text bindings, state mutation, flags/milestones/journal, branches/loops/control signals, and member iteration) through a backend-neutral `CompiledVmHost`. A parity test runs one authored pure scene through both `StatefulScriptServiceRunner` and the compiled VM, comparing semantic trace events and runtime-state deltas; a second test asserts unsupported interactive opcodes fail with `UnsupportedHostService` diagnostics instead of falling through.
  - [x] J2.2. Compiled expression and lvalue evaluator. `CompiledVm` now evaluates the compiled int/bool/string expression surface used by the current bundle, including locals, arithmetic/comparison logic, random ranges, realm/party/member fields, runtime flags, inventory pool counts, inventory slot costs, flag/milestone predicates, native int/string expressions, battle queries, monster fields/flags, and battle-scoped integer/boolean flags. Native expression evaluation validates `NativeServiceCatalog` descriptors before calling hosts and emits the same native trace events as the authored interpreter; battle and monster expressions/lvalues route through explicit `CompiledVmHost` hooks. Regressions cover authored parity for inventory/native formulas, unknown native expression diagnostics, native string shape diagnostics, battle host hooks, and nested expression operand paths.
  - [x] J2.3. Blocking input and continuations. `WaitInput` returns `CompiledVmResult.Blocked` with a typed pending prompt, a resumable `CompiledVmContinuation`, and a `CompiledVmInput.Continue` resume path that advances past the wait without replaying prior trace events or state mutations. `Menu` now blocks with a typed pending menu, records accepted primary keys, resumes the selected option or authored default branch, and rejects invalid keys without advancing. `AskKey` now blocks with rendered prompt plus authored input bindings, matches aliases on resume, stores the same integer binding value as the terminal bridge, and rejects invalid keys without advancing. `AskMember` now blocks with display and accepting filters, exposes the accepted one-based member numbers, stores the selected member number on resume, and rejects invalid members without advancing. `AskInventorySlot` now blocks with slot range/filter metadata, exposes accepted key-to-slot choices from current inventory state, stores the selected slot number on resume, and rejects invalid slot keys without advancing. `AskText` now blocks with accepted-answer metadata, applies the same trim/case/max-length normalization as the terminal bridge, and resumes into either the match or miss branch without replaying prior effects. `AskMonster` now blocks with rendered prompt plus host-supplied target keys, stores the selected zero-based monster target index on resume, and rejects invalid targets without advancing. Loop and `ForEachMember` body continuations now persist their iteration frame kind, resume the current body without replaying prior effects, and continue into the remaining iterations before returning to the outer block. `CompiledVmContinuationSnapshot` now provides a save-shaped, runtime-external snapshot record for root/locals/text bindings/frames/pending input, and every blocking VM test resumes through a snapshot round trip. J2.3 is closed; strict runtime save-file parse/render integration remains J2.7.
  - [x] J2.4. Child session handoffs. `CompiledVm` now exposes typed child requests for `Speak`, `TellRumour`, `Encounter`, `EnterDungeon`, `GrantReward`, `RandomItemOffer`, and `ShopService`, plus `CompiledVmChildCompletion` and a save-shaped `ChildSession` pending continuation. Child opcodes trace the same semantic event family, preserve parent VM frames, round-trip through `CompiledVmContinuationSnapshot`, and resume from a host-supplied child runtime without replaying parent effects. `EnterDungeon` mutates the runtime into dungeon mode before blocking so hosts receive the same handoff state as the authored bridge. `RunScene` emits the semantic trace event directly, `NativeCall` validates effect descriptors through `NativeServiceCatalog`, and `ForEachMonster` now executes through an explicit battle-host monster iteration boundary with resumable body frames. `RylweenCompiledVmChildSessionTest` covers each child request shape, snapshot round trips, post-child resume behavior, and native-effect execution; `RylweenCompiledVmTest` covers `ForEachMonster` continuation behavior. J2.4 is closed for the compiled-VM contract; running the actual child sessions from compiled payloads belongs to the J2a/J2.5 host-adapter work.
  - [x] J2a. Kotlin ANSI compiled-VM parity path. The Kotlin ANSI production launcher now runs script roots through `CompiledVm` by default; Kotlin session state machines still own host gameplay systems such as wilderness/town/dungeon/castle/battle, but authored source execution is retained only as an annotated parity/test oracle. First slice: `CompiledVmScriptRunner` indexes validated loaded bundle scripts by stable `ScriptRootRef`, runs supported roots through `CompiledVm`, and reports missing roots or unsupported opcode families as typed VM diagnostics without falling back to the authored interpreter. J2.5 headless slice now adds a `kotlin-headless-compiled-vm` surface, stores validated compiled content on the explicit compiled host factory, and exposes scene/dungeon-event/spell/item-use root helpers that execute through the VM while the plain headless surface remains the authored-source oracle. Current J2a adapter slice adds `CompiledVmTerminalScriptBridge`, stores blocked VM frames inside `ScriptContinuation.compiledVmContinuation`, lets town/wilderness terminal adapters receive a compiled `serviceBridge`, and proves a real `TownSessionTerminalAdapter` native-service path can block and resume through the VM without falling back to `StatefulScriptServiceRunner`. The first child-adapter slice now completes compiled `TellRumour` child requests in the terminal bridge by reusing `SpeakRuntimeContent` rumour selection, tracing the selected text, and resuming the parent VM frame with `CompiledVmInput.ChildSessionCompleted`. The second child-adapter slice routes compiled `Speak` requests through `SpeakSessionTraceRunner`, stores active `SpeakSession` state next to the blocked compiled VM frame when conversation input waits, and resumes the parent VM only after the conversation finishes. The third child-adapter slice routes compiled reward grants, random-item offers, and shop services through their existing terminal child sessions; reward/offer/shop waits now store host-owned child state alongside the compiled VM frame, and parent VM execution resumes only after the child reports its updated party/runtime. The fourth child-adapter slice maps compiled `Encounter` and `EnterDungeon` requests: battle waits carry the existing `BattleSession` next to the blocked VM frame, game-over battles finish without replaying parent effects, dungeon handoffs return the existing `WildernessDungeonHandoff`, and post-dungeon continuations resume the parent VM from the stored frame. The conformance slice now runs every named J3 suite against the `kotlin-headless-compiled-vm` surface, with the town-services suite driving the real Thord church service through `CompiledVmTerminalScriptBridge` and asserting VM trace events. The ANSI host slices injected the same `ScriptBridge` boundary into ANSI town, hut, and mission service roots, and `./gradlew check` drives a real socket ANSI town/shop route through the compiled VM. The next coverage slice makes `LongPlaythroughTest` parameterize its service bridge, proves compiled mode can run a real wilderness hut scene and all mission dungeon handoff/post-dungeon continuations, preserves structured role/location text parts through VM output so terminal renderers resolve them with runtime context, and implements compiled `ConsumeInventoryPool` for the Thun altar path. The ANSI closeout slice generalizes `ScriptBridge` into a root-level invocation boundary, routes dungeon events plus item-use and priest-spell script roots through the compiled bridge from wilderness, dungeon, and Ydarg/castle child sessions, gives terminal compiled waits the same prompt RNG behavior as the authored terminal path, keys resumed child state to the exact pending VM instruction, and proves the full Ydarg-victory acceptance playthrough in compiled-VM mode. The bundle-runtime closeout fills the remaining emitted runtime-mutation opcodes (`ConsumeActiveItem`, `ClearInventorySlot`, `ConsumeTorch`, `TeleportParty`, and `DiscoverCave`) in `CompiledVm`, carries the active inventory slot through VM continuations/snapshots, and lets the terminal host use the loaded world map for collision-aware teleportation. Strict save/load child-boundary work is now closed; remaining J2a work starts at native probe and the longer source-IR retirement decision rather than ANSI script-root coverage.
    - [x] J2.6 ANSI compiled-VM closeout. Route every ANSI script-root boundary through `CompiledVm` when `--script-vm compiled` is selected, add targeted regressions for tavern traveller-story prompts, shops, dungeon events, Ydarg-adjacent scripted flows, and any remaining mission/hut service roots, and run the full acceptance playthrough in compiled-VM mode with no authored-source fallback.
    - [x] J2a retirement path for the authored source emitter/interpreter. Closed for production runtime ownership: non-test Kotlin hosts run script roots through `CompiledVm`, strict save/load covers compiled waits and child sessions, and the old authored interpreter is documented as a parity/test oracle. The authored source IR remains compiler-owned for validation, source diagnostics, and parity coverage; deleting it entirely would be a separate compiler-IR replacement task, not a hidden production-runtime retirement task.
      - [x] J2a-retire.0 authored-source usage audit. Current classification: `core/src/main/kotlin/rylween/dsl`, `core/src/main/kotlin/rylween/compiled/CompiledBundle.kt`, and `ScriptSourceAnalysis.kt` still need source-script IR for validation, lowering, diagnostics, traversal, and native-path analysis; `legacy/reference-runtime/src/main/kotlin/rylween/engine/ScriptTrace.kt` plus parity tests remain useful as an oracle surface. Production retirement targets were `legacy/ansi-runtime/src/main/kotlin/rylween/frontend/terminal/TerminalScriptBridge.kt` (`StatefulScriptBridge`/`StatefulScriptServiceRunner`), null-bridge fallbacks in `TerminalAdapters.kt` for item-use, priest-spell, dungeon-event, town-service, wilderness-mission, and hut roots, the castle fallback in `CastleSessionTerminalAdapter.kt`, line-terminal launches in `legacy/ansi-runtime/src/main/kotlin/rylween/app/TerminalMain.kt`, and the explicit `--script-vm authored` mode in `legacy/ansi-runtime/src/main/kotlin/rylween/app/RylweenAnsiMain.kt`; all production launcher/adapters now route compiled-mode roots explicitly.
      - [x] J2a-retire.1 no-silent-authored-fallback guard. Compiled-mode hosts and adapters must fail with a typed diagnostic when a compiled bridge/root cannot be supplied instead of constructing `StatefulScriptBridge` or `StatefulScriptServiceRunner` implicitly. Add an architectural regression that catches new production `StatefulScriptBridge`/`StatefulScriptServiceRunner` call sites outside explicit authored/test-oracle paths.
        - [x] ANSI custom-loop bridge propagation. `RylweenAnsiMain.kt` now builds the selected script bridge for dungeon, castle, item-use, priest-spell, town, hut, and mission roots instead of leaving script-bearing child adapters to fall back internally; `RylweenCompiledGameLedgerTest` guards against reintroducing the old bridge-less ANSI child adapter calls.
        - [x] Adapter-level guard. Reusable terminal adapters now default to `MissingScriptBridge`, which lets non-script paths run but fails immediately if a script root is reached without explicit composition. Test helpers and authored-mode hosts now pass `StatefulScriptBridge` intentionally, while compiled-mode hosts pass `CompiledVmTerminalScriptBridge`.
      - [x] J2a-retire.2 bridge-injection cleanup. Move fallback decisions to application composition: `TerminalAdapters.kt` and `CastleSessionTerminalAdapter.kt` now receive a required `ScriptBridge`/`ServiceBridge` for script roots in compiled runs, and missing composition fails through `MissingScriptBridge` instead of silently constructing the authored interpreter. `TerminalMain.kt` and `RylweenAnsiMain.kt` now use `CompiledVmTerminalScriptBridge` for production roots; authored execution remains under annotated oracle APIs and tests.
      - [x] J2a-retire.3 isolate authored interpreter. Make `StatefulScriptServiceRunner` and `ScriptTraceRunner` visibly test/oracle or authored-mode-only APIs, with KDoc and package/test placement that prevents them from looking like the production runtime contract. `AuthoredScriptOracleApi` now marks the authored source interpreter and trace runners, KDoc points new runtime hosts at `CompiledVm`, and a ledger regression keeps those declarations visibly outside the production VM contract. Keep them only while parity tests still need an independent semantic oracle.
      - [x] J2a-retire.4 remove validation/source-map blockers. Replace direct concrete-node validation and native-path walking with compiler diagnostics or compiled source maps before deleting the source-script IR entirely. Closed for the current authored-IR stage: validation may still inspect individual `ScriptStatementNode` variants for author-facing semantics, but recursive source traversal, root ids, branch labels, condition context, source-map-style numeric paths, and native-service path discovery now live under `ScriptSourceAnalysis.kt` instead of ad hoc validation/runtime walkers.
        - [x] Native-path source analysis. `ScriptSourceAnalysis.kt` now owns authored script/formula root enumeration, source-root ids, and native-service path discovery; `Dsl.kt` no longer contains the recursive native path walker, and tests consult `analyzeScriptSources(nextDsl).nativePaths` so this data can grow into compiler/source-map diagnostics.
        - [x] Validation source-map migration. `DslValidation.kt` now drives script reward checks, image references, flag transitions, and consume-active-item detection through `ScriptSourceRoot.visitScriptStatements`/`visitScriptStatements`; the shared walker carries branch labels, loop depth, condition context, and numeric paths for future compiled source maps.
      - [x] J2a-retire.5 closeout. Production compiled mode and CI acceptance should prove every script root runs through `CompiledVm` with no implicit authored fallback; the remaining authored interpreter is either deleted or explicitly retained as a test-only oracle with a documented owner. Closed for Kotlin production hosts: `RylweenAnsiMain.kt` and `TerminalMain.kt` no longer compose `StatefulScriptBridge`, production `--script-vm` accepts only compiled aliases, Gradle ANSI server checks use `AnsiScriptVmMode.Compiled`, `kotlin-ansi` declares `RuntimeScriptExecutionStrategy.CompiledVm`, and the annotated `AuthoredScriptOracleApi` surface documents the parity/oracle test suite as the owner of the old authored source interpreter.
    - [x] J2.7 strict snapshot closeout. Add compiled VM continuation records to the runtime snapshot format, bind them to the verified bundle hash/universe, preserve active child-session execution-boundary snapshots, reject mismatched bundle loads, and add deterministic parse/render plus live round-trip tests for waits, menus, loops, child battles, dungeon post-continuations, castle hook scripts, and town services. First slice landed: strict snapshot schema version 2 carries compiled VM continuation frames for authored script continuations, runtime saves can store the verified compiled bundle hash, load rejects compiled continuations without the matching bundle hash, and a live compiled-VM post-dungeon continuation saves, parses, resumes, and completes through the compiled bridge. The castle-continuation slice upgraded the strict snapshot schema to include castle hook script continuations so a castle entry/completion script can round-trip without falling back to an unsaveable mode. The child-boundary closeout adds engine-level battle, reward-grant, speak, random-item offer, and shop-service snapshots inside `ScriptContinuationSnapshot`, round-trips nested `battleSession { ... }`, `rewardGrantSession { ... }`, `speakSession { ... }`, `randomItemOfferSession { ... }`, and `shopServiceSession { ... }` blocks through the strict parser/writer, validates referenced monster/combat-script/reward/NPC/item/shop content on load, permits Battle-mode snapshots only when a parent castle/dungeon continuation owns the battle child, and covers compiled castle hooks or direct continuation restores that cross authored encounter, reward drop-slot, random-offer drop-slot, shop prompt, and speak prompt boundaries before restoring the child session and resuming the parent VM. J2.7 is closed for strict execution-boundary snapshots; this does not change which commands the player can use to save.
    - [x] J2.8 native VM probe. Keep the C JSON reader as the loader boundary, add a small native compiled-VM executor for one pure root and one prompt-shaped root from the J1 bundle, render events to a simple log, report root/opcode/operand/continuation positions on failure, and wire the probe into Gradle verification without declaring it a playable runtime. Current status: `checkNativeCompiledVmProbe` reuses the J1 C JSON reader executable with `--vm-probe`, runs after `checkNativeJsonBundle`, executes `Scene.castle.ydarg.already_completed` through native `View`/`Screen` event logging, blocks `Scene.missions.erethor_entry` at a native `Menu` prompt with root/instruction/operand/continuation coordinates, and executes a narrow dialogue-branch state mutation via native `SetFlag` logging. This remains a portability probe, not a C/native playable runtime.
    - [x] J2.9 native product decision. Decision: do not schedule a full native playable runtime as part of J2. The current C code stays a loader/VM portability probe because it validates the J1/J2 contract without also owning frontend rendering, persistence, save/load, host services, diagnostics, and full acceptance conformance. The previous Rust server/MUD skeleton has been deferred and removed from the active checkout; a future non-JVM product should restart from a fresh plan. This preserves the future-directions server/multi-party path without letting a probe become an under-specified product.
  - [x] J2 closeout checklist, mirrored from [j2-compiled-vm-plan.md](j2-compiled-vm-plan.md). Current status: [x] opcode arity/type contract is tested; [x] compiled expression and lvalue evaluators cover current emitted shapes; [x] pure VM roots execute without authored-source fallback; [x] prompts and menus resume without replaying side effects; [x] child sessions block and resume through typed request/result records; [x] compiled VM continuations save/load under strict snapshot validation; [x] Kotlin headless declares `CompiledVm` and passes J3 suites; [x] Kotlin ANSI production launchers run script roots through `CompiledVm`; [x] full acceptance playthrough passes in compiled-VM mode; [x] C/native probe executes a narrow compiled scenario from the J1 bundle; [x] ledger distinguishes completed J2 pieces from optional native product work.
- [x] J3. Runtime host/view capability conformance. Closed for the current artifact-backed host target. The JSON bundle is the replaceable content/state contract, not a complete game executable; a full runtime still has to provide input, view rendering, image/resource loading, RNG/time, persistence, diagnostics logging, state mutation/session storage, battle/town/wilderness/dungeon/castle stepping, and a J2 compiled-op VM strategy for production scripts. Current status: Kotlin ANSI and Kotlin headless are both declared playable J1-bundle hosts; Kotlin ANSI advertises compiled-VM script execution, while the plain Kotlin headless host remains the authored-source oracle beside the compiled-VM headless surface. The conformance tests cover every named suite from the J1 artifact. The current C code remains intentionally classified as a boundary probe because it only validates the J0/J1/J2 readers and narrow VM execution, and does not reproduce the legacy graphical door/game presentation or implement gameplay host services. Future native, server, or bitmap playable runtimes should get their own product task and must declare the same host surface and pass the same conformance suite before being called playable.
  - [x] J3a. Host surface manifest. `RuntimeHostContract.kt` declares the stable host requirements, view-command kinds, script-execution strategy, artifact source, and conformance-suite names. The Kotlin ANSI surface is declared as a playable J1-bundle host using the compiled VM for production script roots; the native JSON bundle reader is declared as a boundary probe, not a playable runtime. `RuntimeHostContractTest` validates both declarations and runs a wilderness-help terminal scenario from a model loaded through the J1 JSON artifact rather than `RylweenCompiledGame.nextDsl`.
  - [x] J3b. Conformance-suite expansion. `ScenarioHarness` now carries optional `RuntimeConformanceSuite` metadata on terminal and headless scenarios, and `RuntimeHostContractTest` runs a bundle-loaded Kotlin host suite covering every named J3 suite: bundle boot, static screens, wilderness navigation, town services, dungeon flow, Ydarg/castle flow, battle flow, save/load, and typed view commands. The scenarios are adapter-shaped so future bitmap/server/native hosts can run equivalent suite definitions without copying Kotlin generated-content shortcuts.
  - [x] J3c. Second implementation proof. `KotlinHeadlessHost` is a non-ANSI host that loads the J1 JSON artifact into the runtime-facing model and exposes semantic session entry points for character creation, wilderness, town, shop, speak, dungeon, Ydarg/castle, battle, static-screen/image lookup, and runtime state boot without terminal adapters. `RylweenRuntimeHostContract.kotlinHeadless` declares the same playable host surface as ANSI, and `RuntimeHostContractTest` runs every named J3 conformance suite through the bundle-loaded headless host. The C JSON reader still remains a boundary probe, not a playable runtime.
  - [x] J3d. Bundle-only Kotlin ANSI product boundary. Historical result: `RylweenAnsiMain` required a J1 JSON bundle, materialized content through `readExternalRuntimeGame()`, verified the hash against the active compiled artifact, and had no generated `rylween.game` imports or `RylweenCompiledGame` fallback. This classic interpreted/hybrid host has now been archived inside `scratch/legacy-archive.zip` under `legacy/ansi-runtime` and removed from the active Gradle graph after the runtime became the current playable path. Bundle-emission tools remain under `tools/src/main/kotlin`, so generated-game artifact/tooling remains available without putting app launchers back in the root source set.
- [x] J3e. Runtime product track. Goal: a parallel Kotlin implementation that consumes only direct J1/J2 bundle tables and stable `runtimeSystems` rows, not `RylweenKotlinEngine`, old `WildernessSession`/town/dungeon/castle/battle sessions, or terminal adapters. Current slice is isolated in the `:runtime` Gradle subproject under `runtime/src/main/kotlin`: `RylweenRuntimeMain` loads `CompiledGameBundle` plus typed `ExternalRuntimeSystems`, renders compiled static screens, boots a compiled state image directly, owns a real ANSI screen layer over compiled frame/layout/image/terrain rows, and runs the first direct wilderness loop plus town-service scene dispatch. The old interpreted/hybrid ANSI host is archived outside the active Gradle graph in `scratch/legacy-archive.zip` under `legacy/ansi-runtime`; the old JVM session/engine/save reference implementation is physically archived inside `scratch/legacy-archive.zip` under `legacy/reference-runtime`; shared terminal escape/key primitives are isolated in `:terminal`; the root project remains a DSL/compiler/data-only runtime snapshot contract and bundle-tooling workspace. The first real town host service is now direct compiled-table `ShopService`: the runtime renders shop images/text/catalog rows with `{price}`/`{cap}` runtime placeholders, reads compiled input bindings plus multi-digit numeric input, mutates gold, inventory, food, and torches on the runtime party model, reads realm base resource capacity from `runtimeSystems.realm`, and carries deterministic RNG state for bargain/interlude decisions without importing old sessions. Shop catalog paging is now explicit DSL/compiled schema (`ShopCatalogPageNavigationModel` / `CompiledShopCatalogPageNavigation`) instead of display-footer parsing, and the runtime consumes those bindings page-by-page. Direct save/load is now implemented for wilderness, dungeon, and castle boundaries: the save file stores portable runtime ids, party resources/counters/flags/milestones/inventory/member vitality, realm time/map/mode, active dungeon depth/event plan, active castle floor/point/room grid, and post-dungeon compiled-script continuation frames, rejects mismatched compiled bundle hashes before restore, and is smoked by `checkRuntimeSaveScript` plus `checkRuntimeLoadScript`. Direct party and journal wilderness views render compiled party/member/equipment/item/journal rows without importing old terminal utility commands, and `checkRuntimePartyJournalScript` smokes those pages. Wilderness command dispatch now reads `runtimeSystems.commands.wilderness` rows from the bundle instead of literal Kotlin key constants; shared `:terminal` tests prove arrow escape sequences decode to the numeric movement bindings consumed by that command table. The runtime now also consumes `runtimeSystems.realm.upkeep`, `camp`, `hunt`, and `wilderness` event data directly for movement-turn upkeep, mana regen/starvation/fatigue, hunting prompts/results, camping rest/temperature/ambush text, terrain-transition narration, cave-found prompts, random-cave entry, and seeded random-event dispatch without importing `WildernessSession`, `HuntSession`, or `CampSession`. The J1/J2 `runtimeSystems.combat` schema now exports `combat { standardBattle { ... } }`, including fight/run keys, player action bindings, battle text templates, reward/formula ids, round limit/tunables, encounter HP scaling formula ids, victory image, and leveling rows. `runtimeSystems.score` now exports Hall-of-Heroes completion metadata and score power-item rows, and `checkRuntimeHeroesScreen` renders the runtime-backed heroes route from the runtime without the Kotlin terminal static-screen adapter. `BattleRunner` consumes that contract plus compiled monsters/items/rewards/formulas, handles wilderness/camp/script/dungeon/castle encounters without importing the old battle runtime, renders normal battle narration from authored battle text tokens instead of Kotlin string copies, and executes compiled combat item, mage-spell, and monster-spell script roots through a direct battle-context instruction runner for monster/member fields, battle flags, target prompts, active-item consumption, and RNG. The runtime now owns `ScriptExecutor` for scene roots: it runs compiled branches/loops/switches/menus, local int/text bindings, member iteration, scalar/member/flag/journal mutations, item consumption, teleport/cave opcodes, encounters, shops, standard conversation `Speak`, `TellRumour`, dialogue/question script roots, compiled `GrantReward`/`RandomItemOffer` payloads, and saveable post-dungeon continuation resumes directly from the bundle instead of printing host-placeholder text. `ExternalRuntimeSystems` now exposes typed `runtimeSystems.speak`, `runtimeSystems.dungeon`, `runtimeSystems.score`, and `runtimeSystems.castles[]` rows, so direct runtimes can render authored conversation, dungeon prompt, score/heroes reports, and generic castle data without the Kotlin runtime facade. `DungeonRuntime` consumes compiled dungeon scenarios/events, fixed/random event plans, authored depth prompts, torch-gated descent, random encounters, dungeon scripts, post-dungeon continuation resumes, and direct save/utility commands; fixed programmed levels take precedence before random event planning, so authored dungeon-purpose levels cannot be hidden by optional random encounters or item offers. `CastleRuntime` consumes generic grid-castle rows for entry hooks, seeded rooms, movement, traps, treasure, stairs, completion scripts, and no Ydarg-named runtime classes; `UtilityActions` owns direct drop/equipment/quaff/priest-spell commands from compiled item/spell/script rows. Runtime verification now splits product smoke checks from replay planning: Gradle smoke tasks buy twelve torches, buy and sell a blanket through the merchant, page the smith weapon catalog, buy a second-page weapon from the compiled catalog, drive direct dungeon/castle/save/static-screen paths, and run a near-winning route through the full castle victory script. `RuntimeReplayPlanTest` owns the named acceptance routes in test source, builds their command streams from compiled bundle tables, feeds the shipped installed launcher with `--script`, and covers town service/dialogue paths plus the `acceptance-power-route` that earns Fire Shield, Foot of Jazzy, Silver Key, Bracelets of Thun, and Master Crown before winning the castle and loading the saved result into Hall-of-Heroes. The ANSI escape constants and raw-key decoder are shared through `:terminal` so the runtime does not fork terminal protocol tables from the archived ANSI launcher and does not depend on `legacy/ansi-runtime`. `SaveCodec` keeps the save-file escaping at the persistence boundary rather than mixing it with terminal input/color handling. `bin/run.sh`, `:runtime:installRuntime`, `:runtime:checkRuntimeBundleBoot`, the runtime command/save/screen smoke tasks, and `RuntimeReplayPlanTest` install and verify this runtime; `RylweenCompiledGameLedgerTest` guards the new subproject against importing old session/runtime adapter packages while allowing shared low-level ANSI primitives and asserts the old ANSI project plus old reference runtime remain legacy-only. Closeout decision: J3e is closed as a runtime product track for the current Kotlin direct bundle consumer; future work starts as normal feature hardening or a future non-JVM server runtime, not as more hidden fallback removal.

  Current follow-up coverage: `ScriptExecutor` and `CompiledVm` now treat an absent story flag as the authored `none` state, so compiled dialogue and branch predicates can execute mission-intro content from clean fixtures. The runtime verification adds `town-mission-intro-route` for fixed Thord/Wilor mission-giver branches, `town-silver-key-chain-route` for the Gotharp/Azaaza Silver Key follow-up, `silver-key-discovery-route` for the uninterrupted map purchase -> Silver Sanctum note -> smith confession chain, `hobgoblin-caravan-route` for the seeded random-step caravan mission, and `master-crown-discovery-route` for the Broth/Ithienn -> kobold village -> skullweed -> Borin -> Nolrim's crypt -> Master Crown location chain, alongside the existing town social, Garth reward, acceptance power-route, castle-victory, save/load, and Hall-of-Heroes checks.

- [ ] K. Web/server runtime product. Deferred as implementation, now scoped in
  [web-runtime-plan.md](web-runtime-plan.md). The first product direction is a
  JVM web container that reuses the current compiled-bundle Kotlin runtime,
  proves browser play with ANSI-over-WebSocket against one shared world save,
  then grows a semantic bitmap renderer, accounts, and true multi-party shared
  state. The container should build only the compiled bundle into the image;
  mutable world creation, generated NPC placement, date-based move resets, logs,
  users, and saves belong under the configured data directory at runtime. Any
  future non-JVM server/MUD runtime should still read the J1 JSON bundle, execute
  script roots through the J2 compiled VM, own multi-party persistence, and pass
  the same runtime replay/conformance suites as `:runtime` before it is called
  playable.
  - [x] K1. Browser bitmap visual acceptance. Scope: add a browser-level
    screenshot/canvas smoke for the current bitmap renderer that boots the
    Strong party, waits for the first rendered frame, and proves representative
    compiled classpath assets such as service, monster, dungeon, and castle art
    produce nonblank pixels from `/assets/game-image/<id>`. Ownership:
    `runtime-bitmap-web` / web-runtime verification, not the DSL image
    declaration row. Current status: `:runtime-bitmap-web:checkBitmapBrowserSmoke`
    resolves Playwright through Gradle, downloads the selected managed browser
    under `runtime-bitmap-web/build/playwright-browsers`, seeds Strong, starts
    the installed bitmap server, verifies the first canvas frame has nonblank
    varied pixels, samples service/monster/dungeon/castle compiled
    `/assets/game-image/<id>` resources in the browser, and writes a screenshot
    report. It does not require a system-installed Chrome/Chromium or an
    unpacked resource tree beside the server install.
  - [x] K2. Optional pseudo-3D wilderness map view for the bitmap/browser
    renderer. Scope: keep the existing tiled 2D wilderness map as the default
    and add a toggleable forward-facing presentation mode that consumes the same
    authored wilderness map terrain ids. The feature should be renderer/UI
    state first, not a gameplay rule unless the 3D view is active: tiled 2D
    movement still uses absolute north/west/east/south direction keys, while
    the 3D presentation uses crawler controls where up/8 steps forward in the
    current facing, down/numpad 2 steps back, left/4 rotates counterclockwise,
    and right/6 rotates clockwise. Finished slice: bitmap frames now carry
    server-computed projections for all four facings, the browser can toggle
    2D/3D wilderness display from Preferences or separate top-row `3`/`2`
    view keys, rotation has a
    short transition, a compass shows the current facing, and the view now
    renders an Eye-of-the-Beholder-style continuous perspective ground grid
    from the same map cells instead of isolated terrain cards. Follow-up polish
    shortened the visible depth, fills side gaps from neighboring terrain PNGs
    instead of darkness, removed the vector-looking drawn ridge/tree/grass
    details, and fades in the appropriate town/hut/castle backdrop when the
    party is standing on that wilderness settlement cell but has not entered it.
    The tiled 2D bitmap map now defaults to a tighter 6x6 viewport at 68 px per
    tile while retaining `--map-tiles` / `--tile-size` and matching
    `RYLWEEN_BITMAP_MAP_TILES` / `RYLWEEN_BITMAP_TILE_SIZE` environment
    overrides, documented in `runtime-bitmap-web/README.md`, so testers can
    switch back to the old 8x8/64 px view.
    `/tools/wilderness-perspective` loads the real authored map outside Thord
    at 12x10 facing south, so Thord is visible directly ahead, for isolated
    walking/rotation checks without a save or party state.
    - [x] K2a. Build the projection test tool first. Given a world coordinate,
      a facing direction, and a requested perspective grid/depth, produce an
      ordered list of visible terrain cells with world coordinates, relative
      forward/side offsets, terrain id, distance band, scale, destination
      rectangle, and z-order. Cover all four facings, map edges/out-of-bounds
      behavior, and a hand-checked terrain patch from the real wilderness map.
      The projection includes side cells on the party row so first-person
      renderers can fill a continuous near ground plane instead of leaving side
      voids.
      This tool should not depend on browser rendering; it is the correctness
      oracle for the later blitter. Implemented by `WildernessPerspective` and
      `WildernessPerspectiveTest`; the standalone browser tool consumes the
      same projection API rather than duplicating coordinate math.
    - [x] K2b. Define the bitmap terrain sprite set and asset policy. Generate
      or draw bundled classpath images for each current terrain type, with a few
      coordinate-stable variants where variation improves the view. Assets must
      stay under the existing game resources / jar / Docker packaging path so a
      server install does not need an unpacked image tree. Closed as generated
      transparent PNG terrain panels served from bundled `bitmap-web`
      resources; they are bundled in the runtime web jar/Docker image and do
      not require an unpacked server-side asset tree.
    - [x] K2c. Implement the pseudo-3D renderer as deterministic blitting, not
      a physics/3D engine. Use the K2a projection output, draw far-to-near
      perspective trapezoids for the ground cells, then scale landmark sprites
      by distance so nearer forest, mountain, town, and similar markers occlude
      farther ones naturally. Keep this isolated from the current 2D tile
      renderer so the two modes can be compared and toggled.
    - [x] K2d. Add semantic view-mode and facing controls. Preserve keyboard
      accessibility and keep the controls represented as semantic actions
      rather than hardcoded browser-only key checks. Browser implementation
      uses Preferences, top-row `3` for 3D, and top-row `2` to return to the
      tiled map in wilderness frames. In 3D mode, up/8 sends movement in the
      current facing, down/numpad 2 sends the opposite movement, and left/4 plus
      right/6 rotate the current facing with a short slide/fade transition. In
      2D mode, those same direction keys retain the current absolute map
      movement semantics.
    - [x] K2e. Persist only the right state. Store the user's preferred map view
      mode in browser preferences. Treat facing as session/display state
      initialized from the last confirmed movement direction or a stable default
      unless playtesting shows it needs to survive save/load.
    - [x] K2f. Add visual acceptance coverage. Extend the bitmap browser smoke
      to toggle into 3D mode, rotate at least once, and prove the canvas changes
      while the same party/world coordinate remains unchanged. Include a
      screenshot artifact so terrain scale/overlap regressions are easy to
      inspect. The smoke also opens `/tools/wilderness-perspective`, verifies
      the outside-Thord start position facing south with Thord directly ahead,
      unshifted left/right rotation, forward/backward arrow and numpad walking,
      top-row `2` returning to 2D, and then resets the tool screenshot to the
      south-facing 3D Thord view.
    - [ ] K2g. Plan and implement natural 2D terrain transitions without
      changing the authored wilderness map. The renderer should compute a
      neighbor mask for each visible cell and draw bundled PNG alpha overlays
      for terrain-family edges/corners such as forest-to-plain,
      swamp-to-plain/forest, desert-to-plain/foothill, mountain/foothill
      edges, and water shorelines. Keep the base tile id and gameplay terrain
      unchanged; the transition layer is visual only. Add a small deterministic
      render/projection test or smoke screenshot that proves a mixed terrain
      patch blends naturally and that returning to square 8x8-style settings is
      still just a viewport configuration change.

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
| Map dimensions | `world.json5 map`, `wld.c` | `worldMap { rows(...) }` with derived size/cell-count queries | allocation, coordinate bounds |
| Map cells | `data/map.dat`, `state.map` | typed map layers/cells | loaded cell arrays; any legacy `map.dat` migration reader stays outside Kotlin runtime/app/game/test source |
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
| Character creation | `driv.c create_party`, class/race tables | `characterCreation { ... }` start allocation plus class/race/stat rules | reusable creation input session, RNG/stat rolls, class gates, and party state allocation |
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
| Castles | `ydarg.c`, `scripts/ydarg` | generic castle grid/state/content/session, optional hook scripts, shared secondary in-castle utility command sessions, navigation save/resume state, ANSI-facing navigation view events | future C-trace discrepancies only |
| Missions/triggers | `world/missions.json5`, `missions.c` | typed trigger/action/binding DSL | trigger polling and dispatch execution |
| Dialogue/speak | `speak.c`, `scripts/speak*`, text shards | dialogue scripts, role bindings, chitchat tables | NPC selection/input and dispatcher |
| Rumours | `tellRumour(...)`, typed rumour table entries | typed rumour table/script | RNG draw only |
| Shops/church/inn/tavern/merchant/smith/hut | `towne.c`, scripts/towne | building scripts and shop tables | input/render primitives, price/item metadata lookup, first-free-slot and drop-prompt overflow helpers |
| Secrets/sysop actions | `world.json5 secrets` | optional name-gated debug/secret content block | session/frontend input matching plus backend-neutral runtime mutation |
| Score/vscore/Hall of Heroes | `score_list`, `heroes.txt`, data files | dynamic static-screen routes plus milestone/item metadata | runtime report formatting and frontend display |
| Help/manual/title/static screens | `data/adventurers-of-rylween.md`, ANSI title | typed static documents/assets | file/asset render backend |
| Title shell/world entry | `driv.c` title menu, `current-world.json5`, `entry.dat` | runtime snapshot save slot for persistent world state; ANSI title shell for N/C/T/S/H/D/X | old `entry.dat` daily BBS turn-count gate intentionally retired unless multi-user door semantics return |
| Replay/test knobs | `replay_knobs.c`, env vars | typed start-state fixtures and test-scenario metadata | test harness overrides only |
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
  ANSI/static screens, tile/backdrop assets, legacy score-file references, and manual/help text.
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
                mana = 0 of 27
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
- castle room/event rules if future traces require richer room-specific modeling
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
- no `data/map.dat` map-byte reader in Kotlin runtime/app/game/test source; normal Kotlin launchers use authored map rows
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

Keep any future migration loader for `data/map.dat` outside the normal runtime
path and only if it remains useful for audits. Normal runtime loading should
compile authored map rows into terrain ids. The runtime world map should be
something like:

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
2. Grow `game/src/main/dsl/RylweenGame.dsl.kts` into a representative typed content/state source while keeping the archived `legacy/scratch/dsl-next.kts` as the small prototype lab.
3. Keep game-specific script bodies in the authored DSL; continuations/debug
   metadata name their containing roots through `ScriptRootRef`.
4. Define the native bundle structs and ids, including fragment ids only if a
   fragment becomes a real non-inlined callable unit.
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

## Appendix: J2 compiled JSON op shape

The J1 bundle stores scripts under `sections.scripts[]`. Each script root has a
stable `ScriptRootRef`, a compiled script-root handle, and an ordered
`instructions` array. Production J2 runtimes should execute these
`CompiledInstruction` records, not the Kotlin authored source-script tree.

```json
{
  "_type": "CompiledScriptProgram",
  "root": {
    "_type": "ScriptRootRef",
    "denseId": 3,
    "kind": "Scene",
    "slug": "castle.ydarg.entered"
  },
  "rootHandle": { "_type": "CompiledHandleRef", "id": 3, "kind": "ScriptRoot" },
  "instructions": []
}
```

A view operation carries a typed view-command operand plus a dense handle ref.
For example, current generated bundles emit the Castle Ydarg backdrop as an
image handle that resolves through `sections.handles` to
`blitgfx.ydarg_castle`.

```json
{
  "_type": "CompiledInstruction",
  "opcode": "View",
  "operands": [
    {
      "_type": "ViewCommandValue",
      "value": {
        "_type": "ShowImage",
        "image": { "_type": "CompiledHandleRef", "id": 3, "kind": "Image" }
      }
    }
  ]
}
```

Text is stored as structured blocks with literal string ids into
`sections.strings`. A runtime resolves the ids, renders text parts through its
frontend, and keeps the compiled instruction stream independent from Kotlin
string objects.

```json
{
  "_type": "CompiledInstruction",
  "opcode": "Screen",
  "operands": [
    {
      "_type": "TextBlockValue",
      "value": {
        "_type": "Structured",
        "lines": [
          {
            "_type": "CompiledTextLine",
            "parts": [
              { "_type": "Literal", "string": 571 }
            ]
          }
        ]
      }
    }
  ]
}
```

Child sessions are explicit VM/host boundaries. An authored `encounter(...)`
becomes an `Encounter` instruction with a monster handle and a mandatory flag.
The VM blocks with a child request; the host runs the battle session and resumes
the VM with the updated runtime state.

```json
{
  "_type": "CompiledInstruction",
  "opcode": "Encounter",
  "operands": [
    {
      "_type": "Handle",
      "ref": { "_type": "CompiledHandleRef", "id": 27, "kind": "Monster" }
    },
    {
      "_type": "BooleanValue",
      "value": true
    }
  ]
}
```

Nested control flow stores child instruction arrays directly inside typed
operands. Menus, branches, loops, text-input branches, and switches therefore remain
fully inspectable by non-Kotlin loaders without rebuilding a Kotlin AST.

```json
{
  "_type": "CompiledInstruction",
  "opcode": "Menu",
  "operands": [
    { "_type": "TextBlockValue", "value": { "_type": "Structured", "lines": [] } },
    {
      "_type": "MenuOptions",
      "options": [
        {
          "_type": "CompiledMenuOption",
          "binding": {
            "_type": "CompiledInputBinding",
            "labelString": 719,
            "primaryString": 719,
            "aliasStrings": []
          },
          "body": [
            {
              "_type": "CompiledInstruction",
              "opcode": "EnterDungeon",
              "operands": [
                { "_type": "Handle", "ref": { "id": 9, "kind": "Dungeon" } },
                { "_type": "Integer", "value": 12 }
              ]
            }
          ]
        }
      ]
    },
    { "_type": "Instructions", "instructions": [] }
  ]
}
```

State mutation is represented by a typed lvalue operand and a typed expression
operand. The VM owns evaluating these records against `RuntimeSessionState`;
hosts should not re-enter the authored source interpreter in compiled mode.

```json
{
  "_type": "CompiledInstruction",
  "opcode": "Add",
  "operands": [
    {
      "_type": "LValueValue",
      "value": { "_type": "PartyScalar", "nameString": 398 }
    },
    {
      "_type": "IntExprValue",
      "value": { "_type": "Local", "nameString": 818 }
    }
  ]
}
```

## Landed: deferred-wrap `TextBlock.Flow` variant

`flowText(...)` emits a deferred-wrap text variant instead of baking the classic
26-column ANSI text-window width into authored prose. The current model is:

```kotlin
sealed interface TextBlock {
    data class Ref(...)
    data class Inline(...)
    data class Structured(...)
    data class Flow(val paragraphs: List<TextLine>)
}
```

The compiled bundle and native smoke artifact carry `CompiledTextBlock.Flow`.
The external JSON artifact carries `_type = "Flow"` in compiled sections, and
the stable `runtimeSystems` reader also accepts `kind = "flow"` for future
runtime prompt rows. Renderers can now choose their own wrap discipline:

- ANSI: current direct-runtime helpers wrap flow paragraphs to the classic
  `DEFAULT_TEXT_WINDOW_COLUMNS = 26` width, preserving existing output.
- Browser/CSS: emit `<p>` tags and let the viewport flow.
- Bitmap font: measure glyph advance widths and wrap pixel-by-pixel.

Call-site syntax now lets the author write `writeText("""...""")` and
`showTextAndWait("""...""")` for plain prose; those overloads route through
`flowText(...)` automatically. Dynamic prose can use
`flowText("literal", character(...).name, "...")` or `flowText { paragraph(...)
}` so role names, coordinates, selected item names, and other placeholders can
still wrap at runtime without returning to hand-broken ANSI line arrays. Keep
explicit `literalText(...)`, `line(...)`, or `lines(...)` only for true fixed
layout: menus, ASCII diagrams, poetry/riddles with intentional line breaks,
status tables, and short combat/token layouts.

Closed follow-up under this banner:

- The authored game source has been swept beyond the first shop/conversation
  pass: long single-string `writeText("...")` and `showTextAndWait("...")`
  prose now uses triple-quoted flow text, long static `flowText("...")`
  literals were converted or split, and dynamic prose uses `flowText(...)`
  with typed placeholders. Remaining explicit `line(...)`/`lines(...)`
  blocks are intentional fixed-layout cases such as menus/prompts, ASCII
  diagrams, riddles/quoted notes, combat token layouts, short status strings,
  and dynamic numeric layouts that control line breaks.

Remaining follow-up:

- Decide whether `writeText` should reserve a 1-column right margin by
  default. A previous attempt at this broke verbatim-substring asserts in
  `RuntimeReplayPlanTest` for lines that are exactly 26 chars long
  ("The smith is disappointed."). The fix is either DSL-side shortening
  via `flowText`, or test assertions taught to tolerate wrapped output.

## Active: bitmap runtime polish and abstraction cleanup

Continuation checklist from the bitmap web runtime session:

- [x] Fix common command help rendering and dungeon/castle command labels so
  help shows descriptions, not key/key duplicates.
- [x] Make title-menu commands use natural unique first-letter labels and
  bindings: New, Load, Delete, Scores, Heroes, Manual, Exit.
- [x] Fix battle target selection so monster keys stay tied to original
  encounter slots while dead monsters remain visible and unselectable.
- [x] Clean up party-member text placeholders: remove `{party_max}` ad hoc
  strings where practical, distinguish max party size from current party
  member count, and keep shared runtime text values centralized.
- [x] Wire town-entry tax metadata through typed runtime systems and charge
  Strong-level parties on town entry. Investigation found the DSL emits
  `runtimeSystems.town.entryTax`, and the direct runtime now reads and applies
  that row with authored pay/leave bindings.
- [x] Finish wilderness informative-message behavior: pass message, cave exit
  lingering, terrain/fatigue messages staying visible.
- [x] Fix battle cry pagination and add a small pause where appropriate without
  bitmap-specific hacks.
- [x] Finish bitmap status polish: faster text reveal, spelled-out status
  chips, torches/moves labels, and inactive mana bars for non-spellcasters.
- [x] Clean repo root layout: move local developer/operator launchers to
  `bin/`, keep Docker/reverse-proxy packaging under `deploy/docker/`, and
  update docs and references. Reserve `deploy/scripts/` for future scripts that
  are consumed by production deployment machinery rather than normal local
  launcher entrypoints.
- [x] Add a repo-wide documentation/header sweep: inspect all `build.gradle.kts`
  and `*.kt` files for missing KDoc file/class purpose comments, and apply the
  agreed Marcus Lagergren 1992-1994, 2016 copyright header consistently if it
  fits existing repo style.
- [x] Run formatter, compile checks, and the relevant replay/scenario tests
  before restarting any server.
- [x] Restart bitmap server for Strong party verification only after tests are
  green.

No remaining active items in this checklist.

Completed in this UI polish follow-up:

- Moved the text reveal, story-font, font-scale, reveal-speed, and pause-delay
  controls into a single Preferences popover instead of keeping them in the top
  bar.
- Renamed the player-facing bitmap runtime label to "Status" at the top of the
  screen, moved connected-player details into that status popover, and left the
  internal `bitmap` runtime/server identifier unchanged.
- Enlarged the bottom status HUD chips, party names, and health/mana bars so the
  visible time/food/move/gold entries and character rows use more of the
  available dark space.
- Enlarged the bitmap document viewer, compacted Markdown resource documents
  into paragraph-shaped pages, and added simple document styling for headings,
  quotes, and fenced text/code blocks. The shared ANSI/static-screen path stays
  plain text.
- Made document paging explicit: RETURN/ENTER advances, ESC exits, and other
  keys are ignored. The same central bitmap document frame is now used for
  score screens, Hall of Heroes, manual pages, and the adventurer journal.
- Added a browser preference for the fantasy fixed-space text font, including a
  checkbox to fall back to the default system monospace font and a scale slider
  for experimenting with the story text size.
- Added a pause delay scale preference. The browser sends the selected scale to
  the bitmap WebSocket session, and the bitmap runtime input adapter applies it
  to future authored/game pause sleeps with `1.00x` as the default.
- Kept the previously fixed text-reveal continuation behavior so newly appended
  pause replies reveal without redrawing already-revealed text.

Completed in this follow-up:

- Inspected all `build.gradle.kts` and `*.kt` files for existing comment/header
  style. Added conservative module/file-purpose comments to Gradle scripts and
  runtime/web support files that lacked orientation. Left tests and already
  documented DSL/compiler/game-authoring sources unchanged where file names,
  existing KDoc, or local comments already described the purpose.
- Decided not to apply a Marcus Lagergren 1992-1994, 2016 copyright source
  header: current Kotlin and Gradle files do not use copyright headers, while
  Marcus/copyright text appears as authored game/manual material rather than
  repository source-file style.
- Re-ran `./gradlew ktlintFormat --console=plain` after comment edits, then
  `./gradlew check --console=plain`; both completed successfully.
- Restarted the bitmap server with the new launcher path after green checks:
  `env RYLWEEN_DATA_DIR=build/bitmap-smoke-data RYLWEEN_BITMAP_PORT=8091
  ./bin/run-bitmap-server.sh --build --name Strong`. Verified `/healthz`,
  `/api/status`, `/play`, and `/static/client.js`, then opened `/ws/play`,
  sent `L`, and observed the Strong gameplay frame render with Aragorn,
  Legolas, Gandalf, and Mira, `condition=OK`, `gold=10000`, 21 commands, and
  an 8-row wilderness map.

Completed in this pass:

- Runtime validation now treats ANSI and bitmap image assets as distinct by
  `(image id, kind)`, and member-choice prompts can use the same typed party
  count/max runtime text bindings as the rest of the runtime UI.
- The deterministic runtime playthrough script was tightened so endgame boss
  fights do not leave stale target-selection input that can interfere with the
  final save.
- Runtime launcher assertions now match the compiled mnemonic command help
  format, including `(Space) Pass` and `(Z) Cast Spell`.
- Removed redundant authored world-map size/capacity fields from the DSL and
  compiled bundle. Wilderness dimensions and cell count are now derived from
  authored map rows, and empty world-map rows fail fast instead of silently
  reporting width zero.
- Closed I7 by removing the parallel 26-column runtime text-wrap fallback.
  Runtime renderers pass their active text-window width into flow rendering,
  while artifact/report rendering remains explicitly unwrapped unless a width
  is supplied.
- Closed K1 with `:runtime-bitmap-web:checkBitmapBrowserSmoke`: the Gradle task
  downloads a managed Playwright browser under the module build directory,
  boots Strong in the bitmap server, verifies the first browser canvas frame,
  samples representative compiled `/assets/game-image/<id>` resources, and
  writes a screenshot report.
- Closed K2 with the optional bitmap wilderness perspective view: server frames
  include all four forward-facing terrain projections, the browser switches
  view from Preferences, top-row `3` for 3D, or top-row `2` for the map, the 3D
  view uses crawler controls (up/8 forward, down/numpad 2 back, left/4 rotate
  counterclockwise, right/6 rotate clockwise) with a compass/facing indicator,
  tiled 2D keeps absolute movement while updating the session facing from
  confirmed travel, standing on a settlement cell fades in the matching
  settlement scene before entry, the pseudo-3D background uses bundled PNG
  terrain assets rather than drawn ridge/tree glyphs, the 2D map viewport
  defaults to 6x6 at 68 px tiles but remains configurable back to 8x8/64 px
  through `--map-tiles`, `--tile-size`, `RYLWEEN_BITMAP_MAP_TILES`, and
  `RYLWEEN_BITMAP_TILE_SIZE`, and `/tools/wilderness-perspective`
  walks/rotates outside Thord without save or party state.
- Documented the next 2D map visual polish step under K2g: keep authored terrain
  cells unchanged, compute a renderer-only neighbor mask, and use bundled PNG
  transition overlays so forest, swamp, desert, foothill/mountain, and water
  boundaries blend naturally instead of cutting hard between square tiles.
- Fixed bitmap/utility spell-list readability by rendering priest spell menu
  entries with a space after the mnemonic, e.g. `(A) Healing`.
- Tightened portrait selection for the party view and character preview: class
  portraits now outrank race-only fallbacks, so a female human priest resolves
  to the priest portrait instead of the generic female-human image.
- Continued the text-output consistency sweep in the authored priest spells:
  prose-shaped spell narration now uses `flowText(...)`, including dynamic
  party names/pronouns, while fixed-layout prompts and command diagrams remain
  structured/literal text blocks.
- Fixed modal party-view input handling: invalid keys no longer redraw the
  character/party page, while RETURN/ESC exits, arrow/numpad navigation changes
  pages, and `!` remains the explicit redraw key. Existing document/journal
  pages already use the shared RETURN/ESC-only modal input helper.
- Closed a runtime battle presentation polish item: dead monster target rows now
  keep the target-list alignment while replacing the live target letter with
  leading space, the bitmap text panel dims those disabled target rows, and
  victory/reward pages wait for RETURN/ENTER instead of advancing on a timer.
- Track a follow-up DSL/runtime boundary cleanup from the bitmap prompt work:
  town entry currently mixes authored town prompt text, a hardcoded runtime
  main-street image constant, and renderer-specific scene fading decisions.
  Replace the runtime constant with an authored town/wilderness presentation
  row, so "standing on a town and deciding Enter/Leave" can be represented as
  semantic settlement context rather than bitmap-client heuristics.
- Track a follow-up input-abstraction cleanup from the 3D wilderness view:
  browser-local presentation controls now interpret arrow/numpad keys
  differently depending on the current map view. Decide whether display facing,
  view-mode controls, and crawler-vs-tiled input policy should stay client
  preferences or become explicit semantic UI actions emitted by runtime
  systems, without turning presentation facing into saved gameplay state unless
  the design changes.
- Track deferred sound design for the bitmap/runtime product. Add authored
  sound assets and a semantic `playSound(soundId, waitUntilFinished = true)`
  display/script command that hosts may honor or ignore. Candidate first use:
  a surprise-encounter scream before the monster reveal. The design must keep
  ANSI/headless hosts functional with a no-sound trace or text-only fallback,
  bundle sound files in the jar/Docker image like bitmap assets, and avoid
  browser-only asset paths in game logic.
- Track deferred thief/trap/dexterity design as a DSL abstraction proof. Add a
  `dexterity` party-member trait through the game DSL/state schema, expose
  reusable authored roll expressions such as `member.dexterityRoll(...)`, and
  model dungeon/cave trap events so a `Disarm` choice is shown only when a
  living thief is present. The trap outcome should be authored data/scripts and
  compiled runtime-system rows, not hardcoded dungeon Kotlin. Use this as a
  test case that new traits and mechanics can be added through DSL/compiler
  contracts without patching every runtime by hand.
- Track a game DSL documentation/examples section. The docs should show how to
  add, modify, and remove game content using author-facing syntax: for example
  adding a purple potion item plus an `itemUse` script that grants a temporary
  Dexterity boon or reveals nearby traps, then wiring Dexterity into thief trap
  disarming. These examples should double as design checks that the authoring
  surface is abstract enough for new content and mechanics.
- Track a follow-up presentation cleanup for the pseudo-3D wilderness renderer:
  time-of-day sky/distant-terrain backdrops should come from reusable renderer
  theme data keyed by the authored realm time bands, not one-off canvas drawing
  code. This should keep the game DSL owning the time bands while the bitmap
  frontend owns the artwork.
- Track a follow-up authoring-surface cleanup for repeated prompt/display
  shapes still visible during playtesting: town tax/menu prompts, wilderness
  settlement prompts, static documents, story text, and scene image changes all
  route through slightly different code paths. Consolidate them around a small
  set of semantic display nodes so text, image, wait, prompt, and fade behavior
  is consistent across ANSI and bitmap runtimes.
- Track the remaining deferred-wrap text sweep beyond direct script writes:
  shop prose, conversation reply/rumor rows, mission scenes, spells, item
  effects, and dynamic dialogue can use flow text with typed dynamic parts when
  the line breaks are not semantically important. Keep explicit line arrays for
  fixed-layout cases such as menus, riddles/quoted notes, combat token layouts,
  command diagrams, and numeric/status displays. If repeated dynamic prose still
  feels verbose, add a small typed flow builder convenience instead of falling
  back to manual `line(...)` rows.
- Verification completed for the current working pass with `./gradlew
  ktlintFormat :runtime:test :game:test --tests
  rylween.RylweenCompiledGameLedgerTest --console=plain` and `./gradlew check
  --console=plain`.
