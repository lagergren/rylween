# K1 Rust Server/MUD Runtime Plan

## Decision

The first real non-JVM target should be a Rust ANSI TCP server runtime,
tentatively `rylween-mud`.

This is deliberately not the existing C probe. The C code should stay a small
J0/J1/J2 boundary verifier. A playable product needs a safer implementation
surface for JSON parsing, VM execution, TCP sessions, persistence locking,
diagnostics, and long-running process behavior. Rust gives us a static
non-JVM binary, strong data modeling, memory safety, straightforward TCP/file
locking libraries, and enough ecosystem support to implement a server without
turning the C probe into a second full game codebase.

The target is also not a native single-user terminal clone. A native terminal
would mostly duplicate the active Kotlin compiled runtime while proving less of
the future architecture. A server/MUD target exercises the parts that matter
for future directions: many parties, shared realm state, persistent scoreboard,
authentication, and host surfaces that cannot rely on JVM implementation
classes.

## Product Shape

`rylween-mud` is a single binary with two startup modes:

- `rylween-mud --bundle build/compiled/rylween-bundle.json --tty`
  starts one local ANSI terminal session for debugging.
- `rylween-mud --bundle build/compiled/rylween-bundle.json --listen :2323 --realm-dir data/realms/default`
  starts a TCP ANSI server where each connection owns one party session.

The runtime consumes only stable artifacts and runtime files:

- J1 strict JSON bundle for content, state templates, scripts, handles, and
  runtime-system tables.
- J2 compiled VM contract for script execution.
- K-series realm files for mutable world state, party state, users, and
  persistent heroes/scoreboard data.

It does not load Kotlin DSL files, generated Kotlin objects, or the Kotlin
authored `Op` interpreter.

## Why This Target

This target is the highest-signal non-JVM product because it proves three
independent claims at once:

- The J1/J2 artifact is a real portable runtime contract, not just Kotlin
  serialization.
- The runtime host boundary is complete enough to implement gameplay services
  outside the JVM.
- The server/multi-party path in `plans/future-directions.md` can be reached
  without changing the authored DSL model again.

It also has a clearer product reason than a C terminal clone: remote play with
friends, persistent shared realm files, and a Hall-of-Heroes surface that
survives across parties.

## Non-Goals

- No web UI in K1.
- No database in K1; use realm files plus file locks first.
- No adversarial anti-cheat model; assume trusted friends and an unmodified
  binary.
- No native graphics beyond ANSI/text rendering and view-command logging.
- No direct dependency on Kotlin runtime/session classes.
- No expansion of the C probe into a product runtime.

## Architecture

### Crates

Start with a small workspace:

- `rylween_bundle`: strict J1 JSON reader, canonical hash verification, typed
  handle/string/script/runtime-system tables.
- `rylween_vm`: J2 compiled VM, diagnostics, continuation snapshots, native
  service catalog validation.
- `rylween_host`: runtime state, realm/party persistence, command/session
  stepping, host services for the VM.
- `rylween_mud`: TCP listener, TTY adapter, authentication, ANSI rendering,
  CLI, logging.

Keep crate boundaries aligned with the Kotlin boundaries so conformance
fixtures can target the same concepts.

### Runtime State Split

K1 should start with the future-directions split:

- `realms/<realm-id>/world.json5`
  shared world state: realized NPCs, world flags, town inventories, world
  clock, persistent heroes.
- `realms/<realm-id>/parties/<party-id>.json5`
  party state: roster, inventory, location, journal, per-party flags, active
  VM/session continuation.
- `realms/<realm-id>/users.txt`
  v0 authentication: `username:bcrypt-hash:party-id`.

All writes use an exclusive file lock. Party writes lock only the party file.
World writes lock `world.json5` and perform read-modify-write under the lock.

### Host Surface

The Rust host must declare and satisfy the same J3 surface:

- read and verify the J1 bundle
- boot runtime state from bundle state templates or party files
- mutate runtime state through typed host services
- execute scripts through the J2 VM only
- handle semantic input, RNG/time, persistence, diagnostics, view commands,
  text rendering, image/resource references, static screens, and save/load
- implement wilderness, town, dungeon, castle, battle, shop, and speak session
  stepping without Kotlin classes

The implementation can land incrementally, but it should not be called
playable until all named J3 suites and the full acceptance playthrough pass.

## Milestones

### K1.0 Product Skeleton

- Create the Rust workspace and `rylween-mud` CLI.
- Read the J1 JSON bundle and verify schema version/hash.
- Print bundle summary and runtime-system summary.
- Add Gradle tasks to build and smoke-run the binary when Rust is available.

Exit: `rylween-mud --bundle ... inspect` matches Kotlin bundle counts and hash.

Status: complete. The dependency-free Rust inspector now parses the J1 bundle,
validates schema and section order, recomputes the canonical compact `sections`
SHA-256, emits per-section row/byte/FNV summaries, and the Gradle smoke compares
those summaries against the Kotlin inspector.

### K1.1 VM Parity Core

- Port the J2 VM instruction model and operand schema.
- Execute pure roots, prompt-shaped roots, native-service validation, and state
  mutation roots from the same probe cases used by `checkNativeCompiledVmProbe`.
- Emit diagnostics with root, instruction index, operand path, and continuation
  frame coordinates.

Exit: Rust VM probe passes the same narrow cases as the C probe.

### K1.2 Conformance Tape Format

- Export a backend-neutral conformance tape from Kotlin tests: initial bundle
  hash, state id, inputs, expected semantic events, expected state deltas, and
  expected rendered text fragments.
- Teach the Rust runner to execute tapes and report mismatches.

Exit: one static-screen tape, one town-service tape, one prompt/resume tape,
and one save/load tape pass in Rust.

### K1.3 Host Sessions

- Implement runtime state, command bindings, text rendering, view-command
  logging, RNG, and persistence.
- Implement enough town/wilderness session stepping to drive the current
  compiled VM bridge scenarios.
- Add shop, speak, reward, battle, dungeon, and castle child-session host
  services behind explicit interfaces.

Exit: Rust host declares an experimental J3 surface and passes a growing subset
of the named conformance suites.

### K1.4 TCP Server

- Add `--listen host:port`.
- Add login against `users.txt`.
- Bind one authenticated connection to one party file.
- Render ANSI screens over TCP and accept line/character input.
- Add audit logging for connect, login, state load, save, and disconnect.

Exit: two simultaneous local TCP clients can log in as different parties and
save without clobbering each other.

### K1.5 Shared Realm Features

- Split world/party persistence fully.
- Add persistent Hall-of-Heroes writes and title-screen reads.
- Add bulletin-board storage from `plans/future-directions.md`.
- Add locking tests for concurrent party save plus world mutation.

Exit: separate parties share world state and scoreboard entries while keeping
their inventory/location/journal separate.

### K1.6 Playable Closeout

- Pass every J3 conformance suite.
- Pass the full acceptance playthrough through Rust.
- Document deployment, realm directory layout, backup/restore, and upgrade
  rules.
- Decide whether to keep file storage or graduate to SQLite based on measured
  contention.

Exit: `rylween-mud` can be called a playable non-JVM runtime.

## Open Decisions

- Telnet first, SSH wrapper first, or both? Default: telnet first for a single
  binary, SSH wrapper later.
- How strict should the first auth file be? Default: bcrypt user file with a
  generated party id.
- Do we port session state machines directly or generate more host tables from
  Kotlin? Default: port behavior directly until a repeated table need appears.
- Should conformance tapes be JSON or a stricter line protocol? Default: JSON
  because the J1 tooling already owns canonical JSON.
- Should the Rust binary live in this repo from K1.0? Default: yes, because
  CI/Gradle wiring and artifact compatibility need to move together.

## First Implementation Step

Do not start by writing gameplay code. Start with K1.0:

1. Add `rust/rylween-mud/` workspace skeleton.
2. Implement bundle inspect with canonical hash verification.
3. Add a Gradle `checkRustMudBundleInspect` task that is skipped when Cargo is
   unavailable unless explicitly requested.
4. Add a ledger regression that the Rust target stays classified as
   experimental until it passes the full J3 surface.

Smallest viable K1.0 slice: closed. The workspace, `inspect` command, optional
Gradle smoke task, canonical `sections` hash recomputation, and Kotlin/Rust
section-summary parity are in place. Continue with K1.1 VM parity core.
