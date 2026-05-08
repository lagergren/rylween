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

- `src/main/kotlin/rylween/frontend/ansi/AnsiCodes.kt`
- `src/main/kotlin/rylween/frontend/ansi/AnsiInput.kt`
- `src/compiledRuntime/kotlin/rylween/compiledruntime/CompiledAnsiScreen.kt`
- `src/compiledRuntime/kotlin/rylween/compiledruntime/CompiledAnsiTerminal.kt`

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
