# J2 Compiled VM Plan

This plan describes the work needed to turn the J1 compiled JSON bundle from a
validated data artifact into an executable script/runtime contract. Today the
Kotlin ANSI and headless hosts read the J1 bundle, materialize the Kotlin
runtime-facing model, and execute scripts through the existing authored `Op`
interpreter plus reusable session state machines. J2 is the path where a host
can instead execute compiled script programs from the bundle and honestly
declare `RuntimeScriptExecutionStrategy.CompiledVm`.

## Goal

Make compiled script execution a first-class, tested runtime path.

A J2-compliant runtime:

- reads and verifies the J1 JSON bundle
- executes `CompiledScriptProgram` roots, not Kotlin `Op` AST roots
- preserves the same observable script behavior as the current Kotlin
  authored-op interpreter
- saves and resumes compiled VM continuations at blocking points
- exposes the same host/view/session capabilities declared by J3
- passes the existing runtime conformance suites and the long acceptance
  playthrough with `CompiledVm` as its script execution strategy

## Non-goals

J2 is not a rewrite of the whole game runtime.

The reusable Kotlin sessions for wilderness, town, dungeon, Ydarg/castle,
battle, shop, speak, rewards, random item offers, inventory, equipment, and
save/load remain the mature gameplay state machines. The first J2 target should
replace script execution inside those boundaries, not replace every session
loop at once.

J2 is also not a commitment to make C playable immediately. Native work starts
as a focused probe after the Kotlin compiled VM path is green.

## Existing Pieces

Already in the repo:

- `CompiledScriptProgram`, `CompiledInstruction`, `CompiledOpcode`, and typed
  operands in `src/main/kotlin/rylween/compiled/CompiledBundle.kt`
- strict deterministic J1 JSON emission and Kotlin/C readers
- native smoke validation for opcode and operand shapes
- `RuntimeHostContract` with `CompiledVm` as an explicit script execution
  strategy
- playable Kotlin ANSI and Kotlin headless hosts using the J1 bundle
- conformance suites for bundle boot, static screens, wilderness, town,
  dungeon, castle/Ydarg, battle, save/load, and view commands
- a full acceptance playthrough that reaches Ydarg victory and renders
  score/heroes reports

Missing:

- a compiled VM execution kernel
- expression and lvalue evaluation over compiled operand records
- blocking input and continuation frame support for compiled instructions
- child-session handoffs from compiled instructions
- compiled VM save/resume serialization
- conformance mode that runs script roots through `CompiledVm`
- any native executor beyond bundle validation

## Architecture Shape

### VM Kernel

Introduce a backend-neutral compiled script executor in `rylween.runtime` or a
focused sibling package such as `rylween.runtime.vm`.

Core model:

- `CompiledVmProgramSet`: root lookup over `CompiledScriptProgram`
- `CompiledVmState`: root, frame stack, instruction pointer, locals, text
  bindings, active child session, and current runtime state
- `CompiledVmResult`: completed, blocked for input, blocked on child session,
  stopped with a control signal, or failed with a typed diagnostic
- `CompiledVmHost`: services the VM can request from the containing session,
  including rendering, input, child sessions, RNG/runtime mutation, native-op
  registry, and diagnostics

The first implementation can use the Kotlin `CompiledGameBundle` object loaded
from J1. Native runtimes can later mirror the same state and host contracts in
C/C++/Rust.

### Session Boundary

Do not wire the VM directly to terminal adapters first. Put it behind the same
logical boundary used by current script runners:

- service/town scripts
- mission scenes
- dungeon events
- spell effects
- item-use effects
- dialogue branches
- question actions

That keeps wilderness/town/dungeon/battle routing in the existing session
machines while replacing the script body interpreter underneath.

### Save Boundary

Compiled VM save frames must name:

- bundle universe/version/hash
- script root handle or `ScriptRootRef`
- frame stack with block identity and instruction pointer
- loop/menu/branch state
- local variables and text bindings
- pending input kind and prompt metadata
- active child session snapshot, if any

The current `ScriptContinuation` can either be extended with a compiled-frame
variant, or a parallel `CompiledScriptContinuation` can be introduced and then
normalized into runtime snapshots. The safer first step is a parallel type so
the existing authored-op path remains stable while J2 is being proven.

## Work Packages

### J2.0 Contract Freeze

Write the executable VM contract before implementation spreads.

Tasks:

- document opcode arity and operand types in generated or checked form
- add tests that every `CompiledOpcode` has exactly one arity/type rule
- reject unknown enum names and malformed operand combinations in the loader
- define stable VM diagnostics for bad opcode, bad operand, missing handle,
  missing native op, invalid lvalue, bad continuation frame, and unsupported
  host service
- define the native-service registry shape and require all `NativeCall` paths
  to be allowlisted at bundle load or VM host construction time

Exit criteria:

- a malformed compiled program fails before execution
- native smoke/J1 loader checks still pass
- the VM contract is visible from one implementation-neutral document or test

Estimated size: 2-3 days.

### J2.1 VM Kernel And Pure Program Execution

Build the minimal executor with no user input and no child sessions.

Opcode families:

- `Screen`, `Emit`, `EmitIndexed`
- `View`, `Pause` as non-interactive host events at first
- `LetInt`, `BindText`
- `Set`, `Add`, `Sub`, `AddClamped`
- `SetFlag`, `AchieveMilestone`, `AppendJournal`
- `If`, `Switch`, `Loop`, `Break`, `Stop`
- `ForEachMember`

Tasks:

- resolve strings, handles, text blocks, and text parts from compiled tables
- render text through the existing terminal text renderer boundary
- evaluate locals and text bindings
- mutate `RuntimeSessionState` immutably
- add unit tests that compare compiled VM output/state against current
  authored-op execution for small synthetic roots

Exit criteria:

- pure scene roots can execute to completion
- tests compare VM results against the current interpreter for representative
  state mutations and text output

Estimated size: 4-6 days.

### J2.2 Compiled Expressions And LValues

Port expression semantics from the authored-op interpreter to compiled records.

Scope:

- `CompiledIntExpr`
- `CompiledBoolExpr`
- `CompiledStringExpr`
- `CompiledLValue`
- runtime locals
- realm fields
- party resources and counters
- member fields and indexed member access
- inventory slot and inventory pool queries
- flag and milestone predicates
- dungeon-level/context predicates
- battle/monster fields needed by current scripts

Tasks:

- add a shared evaluator that does not depend on Kotlin `Op`
- keep expression logging hooks compatible with existing debug logs
- add parity tests for formulas and script expressions used by current content

Exit criteria:

- every compiled expression shape emitted by the current bundle evaluates
- expression diagnostics include root, instruction index, and operand path

Estimated size: 4-7 days.

### J2.3 Blocking Input And Continuations

Implement waits and prompts as resumable VM states.

Opcode families:

- `WaitInput`
- `Menu`
- `AskKey`
- `AskMember`
- `AskInventorySlot`
- `AskText`
- `AskMonster`
- loop, branch, menu, and text-answer frame persistence

Tasks:

- model pending input as typed VM state, not terminal text
- reuse existing input binding semantics and key aliases
- ensure prompts render once and side effects do not replay after resume
- add continuation serialization tests for each blocking shape
- add rejection behavior for invalid input without advancing VM frames

Exit criteria:

- VM resumes correctly after every prompt class
- save/load during a prompt returns to the same pending input with no repeated
  side effects

Estimated size: 5-8 days.

### J2.4 Child Session Handoffs

Connect compiled instructions to existing reusable sessions.

Opcode families:

- `Speak`
- `TellRumour`
- `RunScene`
- `Encounter`
- `EnterDungeon`
- `GrantReward`
- `RandomItemOffer`
- `ShopService`
- `AskMonster`
- `ForEachMonster`
- `NativeCall`

Tasks:

- define child-session request and completion records
- let the parent VM block while a child session runs
- pass child-session results back into the VM without replaying parent ops
- map `EnterDungeon` to the existing post-dungeon continuation mechanism
- map battle completion to the same runtime mutation semantics used today
- decide which legacy `NativeCall` hooks can be deleted, replaced with semantic
  opcodes, or kept behind a typed allowlist

Exit criteria:

- mission scenes can hand off to battle and dungeon sessions through the VM
- town services can run shop, speak, random item offer, and reward children
- child-session save/load round trips preserve parent VM frames

Estimated size: 7-12 days.

### J2.5 Kotlin Headless Compiled-VM Host

Prove the VM path in the smallest playable host first.

Tasks:

- add a `kotlin-headless-compiled-vm` host surface
- route headless script execution through the VM for scene/event/spell/item
  roots
- keep the current authored-op host surface as the oracle
- run J3 conformance suites against both host surfaces
- add diff-style assertions where both hosts execute the same scenario

Exit criteria:

- headless host declares `RuntimeScriptExecutionStrategy.CompiledVm`
- all J3 conformance suites pass in compiled-VM mode
- no fallback to authored `Op` execution occurs for compiled roots

Estimated size: 4-7 days.

### J2.6 Kotlin ANSI Compiled-VM Host

Move the interactive frontend onto the same VM path.

Tasks:

- add an ANSI `--script-vm compiled` flag or test-only host switch
- route ANSI script roots through the same VM executor
- run `checkKotlinAnsiBundleBoot`
- run targeted ANSI/script prompt regressions for town, tavern story, shops,
  dungeon events, and Ydarg-adjacent flows

Exit criteria:

- Kotlin ANSI can boot from the J1 bundle and run script roots through
  `CompiledVm`
- the full acceptance playthrough passes in compiled-VM mode

Estimated size: 3-5 days.

### J2.7 Save/Load Closeout

Make compiled VM continuations durable.

Tasks:

- add compiled VM continuation records to the strict runtime snapshot format
- reject loading a compiled VM continuation under a mismatched bundle hash
- preserve active child session snapshots
- add snapshot parse/render tests
- add live save/load tests for waits, menus, loops, child battles, dungeon
  post-continuations, and town services

Exit criteria:

- save/load works at every script blocking point covered by existing authored-op
  tests
- compiled VM continuation snapshots are deterministic and validated

Estimated size: 5-8 days.

### J2.8 Native VM Probe

After Kotlin compiled VM parity is green, extend the native side enough to
prove the contract is portable.

Tasks:

- keep the current C JSON reader as the loader layer
- add a small C VM executor for pure roots first
- implement string/handle/text rendering to a simple event log
- implement expression and state mutation for a narrow scenario
- add a native scenario tape generated from Kotlin conformance fixtures
- run the native probe from Gradle as a verification task

Exit criteria:

- the C probe executes at least one pure scene root and one prompt-shaped root
  from the J1 bundle
- failures report bundle root, opcode, operand, and continuation position
- the C code remains classified as a probe unless it implements the full J3 host
  surface

Estimated size: 1-2 weeks.

### J2.9 Product Decision: Full Native Runtime Or Stop

Do not accidentally drift from probe to product.

If a non-JVM playable runtime is scheduled, create a new product task for that
runtime. It must implement:

- J1 bundle loading and hash verification
- J2 compiled VM execution
- J3 host requirements
- frontend-specific rendering
- persistence and diagnostics
- all conformance suites
- the full acceptance playthrough

Estimated size for full native playable runtime: 2-3 months, depending on UI
ambition.

## Recommended Sequence

Do this depth first through Kotlin before widening native:

1. J2.0 contract tests
2. J2.1 pure VM kernel
3. J2.2 expressions/lvalues
4. J2.3 input continuations
5. J2.4 child sessions
6. J2.5 headless compiled-VM host
7. J2.7 save/load closeout
8. J2.6 ANSI compiled-VM host
9. J2.8 native VM probe
10. J2.9 decide whether to schedule a full native runtime

The order intentionally puts headless before ANSI because headless gives the
same game semantics with less terminal noise. ANSI should consume the same VM
path after the semantic behavior is already locked.

## Risk Register

**Continuation replay bugs.** The VM must not rerun side-effecting instructions
after a prompt or child session returns. Mitigation: frame-level tests for every
blocking opcode and save/load tests around side effects.

**Child-session impedance mismatch.** Existing sessions carry rich Kotlin state.
Mitigation: use typed child request/result records and keep the session objects
behind host services.

**Expression drift.** Current expression semantics exist in several runtime
paths. Mitigation: centralize compiled expression evaluation and compare against
current interpreter output for every emitted expression shape.

**Native scope creep.** A C executor can become a half-runtime quickly.
Mitigation: keep native J2 as a probe until a real product task is approved.

**Bundle schema churn.** J2 may need more stable direct data than J1b currently
emits. Mitigation: add schema-versioned sections only when an executor needs
them, and keep old schema validation strict.

## Size Summary

| Slice | Estimate |
| --- | --- |
| Contract freeze | 2-3 days |
| Pure VM kernel | 4-6 days |
| Expressions/lvalues | 4-7 days |
| Input continuations | 5-8 days |
| Child sessions | 7-12 days |
| Headless compiled-VM host | 4-7 days |
| ANSI compiled-VM host | 3-5 days |
| Save/load closeout | 5-8 days |
| Native VM probe | 1-2 weeks |

Total for Kotlin J2/J2a parity: roughly 3-5 weeks.

Total including a useful native probe: roughly 4-7 weeks.

Total for a fully playable native runtime: likely 2-3 months beyond the probe.

## Closeout Checklist

- [ ] opcode arity/type contract is tested
- [ ] compiled expression and lvalue evaluators cover every emitted shape
- [ ] pure VM roots execute without authored `Op` fallback
- [ ] prompts and menus resume without replaying side effects
- [ ] child sessions block and resume through typed request/result records
- [ ] compiled VM continuations save/load under strict snapshot validation
- [ ] Kotlin headless declares `CompiledVm` and passes J3 suites
- [ ] Kotlin ANSI can opt into `CompiledVm`
- [ ] full acceptance playthrough passes in compiled-VM mode
- [ ] C/native probe executes a narrow compiled scenario from the J1 bundle
- [ ] ledger distinguishes completed J2 pieces from optional native product work
