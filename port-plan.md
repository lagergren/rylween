# Rylween Port Plan

A plan for getting `Adventurers of Rylween` (1992-93, ~12k LOC of Amiga/SAS-C for
the Xenolink BBS) running as a self-bootstrapping terminal app on a modern
Unix-ish box (macOS, Linux).

## How this is organized

The plan grew large enough that contributors were hitting closed lifts before reaching the live queue. It is now split across three files:

- **`port-plan.md` (this file)** — master overview only. The §1..§25 section index below names every section, its current status, and the file it lives in. No section bodies live here.
- **`port-plan-complete.md`** — every section whose lift has fully landed (and the closed reference / decision sections that have no follow-up work). Bodies are verbatim from the original `port-plan.md`.
- **`port-plan-incomplete.md`** — every section that is in-progress, planned, or pure reference / vision material that stays evergreen. Active engineering queues (§20, §25) live here. Bodies are verbatim from the original `port-plan.md`.

Section numbers are preserved across the split — every `§N` cross-reference in either file still points to the same section it always did. New work usually appends inside `port-plan-incomplete.md`; once a section closes, move it across to `port-plan-complete.md` (or split it if only part of it closed).

## Section index

| §  | Title                                                                        | Status      | File                       |
|----|------------------------------------------------------------------------------|-------------|----------------------------|
|  1 | What the code actually is                                                    | reference   | port-plan-incomplete.md    |
|  2 | External dependencies you'd have to replace or delete                        | complete    | port-plan-complete.md      |
|  3 | Already-ANSI is a big deal                                                   | complete    | port-plan-complete.md      |
|  4 | Abstract the I/O boundary, not the renderer                                  | complete    | port-plan-complete.md      |
|  5 | Extract content from code                                                    | complete    | port-plan-complete.md      |
|  6 | Language: modernize to C99 vs C++17 subset — decision deferred               | in-progress | port-plan-incomplete.md    |
|  7 | A concrete port plan (Phases 0-5c)                                           | complete    | port-plan-complete.md      |
|  8 | Things that will bite you                                                    | reference   | port-plan-incomplete.md    |
|  9 | End-state vision: a data-driven engine                                       | reference   | port-plan-incomplete.md    |
| 10 | Why not port to Kotlin (or Rust, or …)?                                      | complete    | port-plan-complete.md      |
| 11 | Rough diff budget for Phase 0-5                                              | complete    | port-plan-complete.md      |
| 12 | Follow-ups queued for after Phase 6d module splits                           | in-progress | port-plan-incomplete.md    |
| 13 | Leak inventory (Commit A of §12.2)                                           | complete    | port-plan-complete.md      |
| 14 | State hygiene audit (follow-up to §13)                                       | complete    | port-plan-complete.md      |
| 15 | Realm persistence: current-world.json5 (concrete §12.3 design)               | complete    | port-plan-complete.md      |
| 16 | Dialogue + mission DSL (follow-up to A.2 speak.c)                            | complete    | port-plan-complete.md      |
| 17 | A.4 slice: mission-control + support modules                                 | in-progress | port-plan-incomplete.md    |
| 18 | ANSI art EAW / narrow-cell rendering — pending                               | planned     | port-plan-incomplete.md    |
| 19 | Post-§17 follow-ups surfaced during the hardcoded-indices sweep              | complete    | port-plan-complete.md      |
| 20 | Code-as-data extraction queue (audit 2026-04-25)                             | in-progress | port-plan-incomplete.md    |
| 21 | Reentrancy / thread-safety plan (audit 2026-04-25)                           | planned     | port-plan-incomplete.md    |
| 22 | DSL unification plan (audit 2026-04-25)                                      | complete    | port-plan-complete.md      |
| 23 | Engine→data extraction (post-§22 planning)                                   | complete    | port-plan-complete.md      |
| 24 | Logging + remote console (audit 2026-04-26)                                  | complete    | port-plan-complete.md      |
| 24bis | `id:` → slug-keyed state (deferred, post-§25)                              | deferred    | port-plan-incomplete.md    |
| 25 | 1-indexed → 0-indexed cleanup — §25.A iterator abstractions + §25.B four-axis flip closed end-to-end | complete    | port-plan-complete.md      |
