# Compiled VM Runtime Implementation Specification

This document defines what a runtime must implement when it consumes the
compiled JSON bundle directly. It is the implementation contract behind the
J2/J3 work in [j2-compiled-vm-plan.md](j2-compiled-vm-plan.md) and the runtime
tasks tracked in [../dsl-improvements.md](../dsl-improvements.md).

The compiled bundle is the runtime product. A conforming runtime must not need
the authored Kotlin `Op` tree, generated Kotlin game catalog, or old JVM session
classes to run game content. JVM runtimes may keep those implementations as
test or comparison oracles, but a standalone runtime is expected to boot from
the bundle plus its own host implementation.

## Runtime Responsibilities

A compiled-bundle runtime has five responsibilities:

1. Load and validate the bundle before execution.
2. Resolve bundle tables into runtime handles without relying on source names.
3. Execute compiled script programs and continuations.
4. Provide host services requested by scripts and sessions.
5. Persist and restore the runtime state allowed by the game.

The split is intentional. The VM is a script executor, not a terminal frontend
and not the entire game shell. A full game runtime still needs wilderness,
town, shop, dungeon, castle, battle, save/load, score, and rendering systems.
Those systems may be implemented in any language as long as they consume the
compiled tables and expose the services the VM requires.

## Current JVM Runtime Split

There are currently two Kotlin runtime paths in the repository:

- `run.sh` installs and runs `rylween-bundle`, whose entry point is
  `ansi-runtime/src/main/kotlin/rylween/app/RylweenAnsiMain.kt`. This is the mature hybrid
  ANSI product path. It requires the J1 JSON bundle, verifies the artifact, and
  executes script roots through the J2 compiled VM, but it still materializes
  the bundle into the Kotlin runtime-facing model and drives gameplay through
  `RylweenKotlinEngine`, `src/main/kotlin/rylween/runtime/**`, shared terminal
  rendering helpers, and the ANSI/session adapters in `ansi-runtime`. The
  launcher/install/check tasks live in the `:ansi-runtime` Gradle subproject so
  the classic host and standalone compiled consumer are not root source sets.
- `run-compiled.sh` installs and runs `rylween-compiled`, whose entry point is
  `compiled-runtime/src/main/kotlin/rylween/compiledruntime/RylweenCompiledRuntimeMain.kt`.
  This is the standalone compiled-bundle consumer under construction. It reads
  `CompiledGameBundle` plus stable `runtimeSystems` rows directly and must not
  depend on `RylweenKotlinEngine`, old session classes such as
  `WildernessSession`/`ShopSession`, terminal adapters, generated game handles,
  or authored `Op` execution.

The old Kotlin sessions may remain as the reference implementation and parity
oracle until the standalone consumer reaches full conformance. They are not the
portable runtime contract. New host behavior should move into the standalone
compiled consumer or the compiled VM/host boundary, not into an additional
special case in the hybrid session graph.

## Bundle Loading

A runtime must parse the compiled JSON bundle and reject it before execution if
the bundle is not supported.

Required loader checks:

- `format` and schema version match the runtime's supported contract.
- the universe id, bundle hash, table counts, and dense handle ids are stable
  and internally consistent.
- string references, handle references, and script roots resolve to existing
  table entries.
- every compiled instruction uses a known opcode.
- every instruction operand list matches `CompiledOpcodeContract`.
- every nested instruction list, switch case, menu option, and text-answer
  branch is validated recursively.
- every native operation path is present in the runtime's native service
  catalog if the runtime supports native calls.
- the runtime fails closed when a required table, service, or opcode is absent.

The JVM reference pieces for this are:

- `src/main/kotlin/rylween/compiled/CompiledBundle.kt`
- `src/main/kotlin/rylween/compiled/CompiledLoader.kt`
- `src/main/kotlin/rylween/compiled/CompiledOpcodeContract.kt`
- `src/main/kotlin/rylween/compiled/ExternalBundleArtifact.kt`

Non-JVM runtimes should mirror the observable checks, not the Kotlin class
layout.

## Runtime Tables

The JSON bundle gives the runtime all authored game data as tables. Runtime
code should treat dense ids and handle refs as the execution identity. Slugs are
for diagnostics, save readability, and operator tooling.

Important table families:

- content handles: items, monsters, towns, buildings, dungeons, castles,
  scenes, scripts, states, parties, terrain, maps, layouts, images, texts, and
  milestones.
- authored script programs: roots plus instruction lists.
- runtime states: bootable named state fixtures.
- presentation tables: terminal terrain glyphs, screen regions, static screens,
  and image assets.
- gameplay tables: shop catalogs, encounters, spells, rewards, missions,
  dungeon scenarios/events, castle content, NPCs, rumours, and text blocks.

A runtime may index these tables into native structures at load time, but it
must preserve the bundle hash and table identities for diagnostics and saves.

## VM Execution Model

The compiled VM executes a `CompiledScriptProgram` root. It does not execute
the authored Kotlin `Op` AST.

Required VM state:

- current script root reference.
- frame stack with instruction list identity and instruction index.
- loop/menu/switch branch state where needed for resumption.
- runtime locals and text bindings.
- pending input metadata when blocked.
- pending child session metadata when the script hands control to a host
  system.
- diagnostic location: script root, instruction index, and operand path.

The VM must support deterministic execution. Given the same bundle, state,
host replies, and RNG stream, it must produce the same runtime state and output
events.

The JVM reference VM is:

- `src/main/kotlin/rylween/runtime/vm/CompiledVm.kt`
- `src/main/kotlin/rylween/runtime/vm/CompiledVmScriptRunner.kt`
- `src/main/kotlin/rylween/runtime/vm/CompiledVmContinuationSnapshot.kt`
- `src/main/kotlin/rylween/runtime/vm/VmDiagnostic.kt`

Standalone runtimes do not need to copy the JVM state classes, but they must
implement equivalent semantics.

## Host Services

Compiled scripts are intentionally smaller than the whole game. They call out
to host services for systems that own long-lived state or interaction loops.

A full runtime must provide host systems for:

- text output, screen selection, pause, and view commands.
- key, menu, member, and text input.
- deterministic RNG.
- realm and party state mutation.
- flags, milestones, journal entries, score, and Hall-of-Heroes reporting.
- inventory, equipment, item use, resource accounting, and party-member fields.
- town entry, service routing, speak/rumour/chitchat, shops, inns, churches,
  taverns, guilds, and huts.
- wilderness movement, terrain effects, random missions, NPC huts, caves, and
  dungeon/castle entry.
- battle encounters, spell casting, rewards, loot, death, victory, and level-up.
- dungeon event plans, dungeon save/resume hooks, fixed dungeon scenarios, and
  post-dungeon continuations.
- castle scenarios, room hooks, win conditions, and victory persistence.
- save/load at the player-visible save points allowed by the game.

The host API does not have to be named exactly like the JVM `CompiledVmHost`,
but the boundary should be equivalent: the VM asks for a typed service, the
host either performs it and returns a typed result or reports a typed
diagnostic.

## Full Game Implementor Checklist

A new runtime that wants to run the full compiled JSON game bundle must provide
the following pieces. The names do not have to match the Kotlin implementation,
but the behavior and state boundaries must be equivalent.

### Bundle And Table Layer

Implement:

- JSON reader for the J1 artifact envelope: `format`, `schemaVersion`,
  `universe`, `bundleContentHash`, `sections`, and section summaries.
- Canonical `sections` hashing or an equivalent trusted verification path.
- Dense handle table with lookup by kind and id.
- String table resolver.
- Typed readers for content/state/script tables used by gameplay:
  terrain, maps, images, layouts, static screens, items, monsters, towns,
  buildings, dungeons, castles, encounters, rewards, scripts, and states.
- Typed reader for stable `runtimeSystems`: command bindings, prompts,
  terminal presentation, realm rules, wilderness rules, town/speak/dungeon
  systems, and castle rows.
- Loader validation matching the JVM loader: handle refs, string refs, opcode
  contract, native-service catalog, table cardinality, and required runtime
  systems.

### Runtime State Layer

Implement:

- Realm state: hour/time, active party, current mode, current map/town,
  random seed, milestones, flags, cave discoveries, mission/location state, and
  score/victory state.
- Party state: id, name, position, resources, inventory slots, equipment,
  members, living/dead status, stats, class/race, level, health, wounds, and
  per-party journal/milestones as represented by the bundle/state schema.
- Deterministic RNG with all runtime draws recorded through state mutation.
- Resource accounting for gold, food, torches, movement time, item capacity,
  and item-provided resource capacity bonuses.
- Save/load records tied to universe id, schema version, bundle hash, runtime
  mode, party/realm state, and any allowed suspended host session or VM
  continuation.

### Presentation And Input Layer

Implement:

- Static screen rendering from compiled image/static-screen/layout rows.
- ANSI or equivalent frontend renderer for the classic frame, map window, text
  window, status lines, prompts, and command echo.
- Terrain glyph/color rendering from `runtimeSystems.presentation`.
- Text block renderer for literal strings, string refs, int/string expressions,
  runtime text parts, role/town/location bindings, and shop/battle/reward
  placeholders.
- Input normalization for authored `InputBinding` primary keys and aliases,
  including arrow keys, classic numeric movement, yes/no prompts, menus,
  member selection, inventory slots, and text answers.
- Scripted-input mode that uses the same logical command path as interactive
  input.

### Compiled VM Layer

Implement:

- Instruction dispatch for every opcode emitted in the bundle.
- Operand decoding and diagnostics with script root, instruction index, and
  operand path.
- Int/bool/string expression evaluation.
- Runtime lvalues for realm, party, member, battle, monster, locals, and flags.
- Locals and text bindings.
- Branching, switch/menu matching, loops, `Break`, `Stop`, `ForEachMember`, and
  `ForEachMonster`.
- Blocking input continuations for wait, menu, key, member, monster,
  inventory-slot, and text prompts.
- Child-session continuation records for speak, rumour, reward, random-item
  offer, shop service, encounter, and dungeon entry.
- Native int/string/effect service registry with arity and argument-slot
  validation.

### Game Shell Layer

Implement:

- Title/static-screen shell and boot-state selection.
- Character creation if the runtime supports new-game boot.
- Wilderness loop: movement, terrain cost, pass/wait, map redraw, help,
  party/journal/equipment/item/spell commands, town/cave/castle/dungeon entry,
  random mission polling, NPC huts, terrain transition text, upkeep, death,
  out-of-moves handling, and save commands allowed by game policy.
- Town loop: entry tax, town menus, service hours, church, smith, merchant, inn,
  tavern, guild, speak/chitchat/rumour services, and leave-to-wilderness state.
- Shop service: catalog selection and paging, buy/sell confirmation, bulk
  resources, inventory overflow policy, bargain/rejection RNG, interlude text,
  and all runtime text placeholders.
- Speak service: NPC selection, visited state, role/town text bindings,
  dialogue branch dispatch, questions, chitchat, rumours, and no-NPC/no-rumour
  fallbacks.
- Reward/random-offer services: gold/items/application policy, special item
  milestone behavior, full-inventory prompts, decline/broke/deal text, and RNG.
- Battle loop: encounter setup, opening choice, combat rounds, member/monster
  targeting, spells, item use, morale/run/death/victory, rewards, level-up, and
  game-over state.
- Dungeon loop: depth movement, torch checks, event plan generation,
  fixed/random events, random encounters, equipment/item/spell commands, saves
  at allowed dungeon boundaries, leave/ascend/descend behavior, and
  post-dungeon continuation resume.
- Castle loop: grid movement, room hooks, stairs, traps, treasure, encounters,
  utility commands, generic castle scenarios, scripted win conditions, victory
  milestone/score updates, and save/resume for allowed castle boundaries.
- Score and Hall-of-Heroes rendering from persisted runtime state.

### Conformance Layer

Implement:

- Loader/hash tests against the canonical emitted bundle.
- VM tests for pure roots, prompt roots, child-session roots, native-service
  validation, diagnostics, and continuation round trips.
- Runtime conformance scenarios equivalent to the JVM J3 suites:
  bundle boot, static screens, wilderness navigation, town services, dungeon
  flow, castle flow, battle flow, save/load, and view commands.
- A full acceptance playthrough in compiled-bundle mode with no authored-op or
  old-session fallback.

Only after all checklist sections pass may a runtime be described as a full
compiled-bundle game runtime. Partial implementations should be called boundary
probes, VM probes, or standalone runtime slices according to the conformance
level they actually satisfy.

## Snapshots And Saves

There are two different snapshot concerns:

- VM continuation snapshots are execution-boundary records. They let a script
  resume after input or a child session.
- Player save snapshots are game-policy records. Rylween may restrict where the
  player can save even if the VM can technically snapshot more places.

Every saved continuation or game snapshot must include:

- bundle universe id, schema version, and content hash.
- root or session identity.
- instruction/frame state when a script is suspended.
- host-session state when the player is inside an allowed resumable system.
- party, realm, inventory, flags, milestones, RNG, score, and location state
  required to resume.

A runtime must reject a save whose bundle hash or schema is incompatible.

## Rendering And Input

The VM contract is UI-neutral, but a playable ANSI runtime has additional
requirements:

- render static screens from compiled presentation tables.
- render the classic frame, map viewport, status lines, text window, and command
  areas using compiled layout and image rows.
- position the cursor and control ANSI state through shared named constants,
  not ad hoc numeric escape literals.
- normalize arrow keys and classic keypad commands into game input tokens.
- keep scripted input and interactive input on the same logical command path.

The current JVM ANSI helpers live in:

- `ansi-terminal/src/main/kotlin/rylween/frontend/ansi/AnsiCodes.kt`
- `ansi-terminal/src/main/kotlin/rylween/frontend/ansi/AnsiInput.kt`
- `compiled-runtime/src/main/kotlin/rylween/compiledruntime/CompiledAnsiScreen.kt`
- `compiled-runtime/src/main/kotlin/rylween/compiledruntime/CompiledAnsiTerminal.kt`

Other runtimes may implement their own terminal layer, but the same commands
and presentation tables must drive the game.

## Diagnostics

A conforming runtime must fail with structured diagnostics rather than generic
exceptions wherever possible.

Required diagnostic categories:

- bad opcode.
- bad operand.
- missing handle.
- missing native operation.
- invalid lvalue.
- bad continuation frame.
- unsupported host service.

Diagnostics must identify the script root, instruction index, and operand path
when the failure is script-related. Host systems should include their own
session and table identity when the failure is outside the VM.

## Conformance Levels

The practical implementation levels are:

- loader: parse, validate, and inspect the bundle.
- script VM: execute non-interactive scripts and state mutations.
- interactive VM: support input, menus, text answers, and continuations.
- hosted script runtime: run all service, mission, dungeon, castle, and battle
  scripts through compiled programs.
- standalone playable runtime: implement all game shell systems directly from
  compiled tables with no authored-op or old-session fallback.
- non-JVM runtime: repeat the standalone contract in another language and pass
  the same bundle/hash/diagnostic checks.

The current target is the standalone playable runtime, then a small real
non-JVM target after the contract is proven.

## Minimal Boot Sequence

A runtime boot should follow this shape:

1. Read the JSON bundle.
2. Validate schema, tables, scripts, operands, and native-service catalog.
3. Select a boot state by slug or dense state id.
4. Materialize runtime state from compiled state tables.
5. Initialize host systems and deterministic RNG.
6. Render the first presentation state.
7. Enter the game shell loop.
8. Dispatch scripts through compiled VM roots when a scene or event requires
   script execution.
9. Snapshot only at VM or game-policy boundaries the runtime supports.
10. Write diagnostics with bundle and script identity when anything fails.

## Things A Runtime Must Not Depend On

A standalone compiled runtime must not require:

- `src/game/kotlin` generated handles.
- authored Kotlin `Op` instances.
- the old Kotlin runtime/session classes.
- Kotlin-specific enum names as save identity.
- source-order assumptions that are not represented by dense ids or explicit
  bundle fields.

Those JVM pieces can remain as reference implementations and conformance
oracles, but the bundle contract is the portable runtime API.
