# Game

`:game` contains the authored Adventurers of Rylween content.

Important areas:

- `src/main/kotlin/rylween/game/RylweenGame.kt` is the main DSL-authored game
  script.
- `RylweenCompiledGame.kt` exposes the compiled in-process game facade used by
  runtimes and tests.
- `RylweenTestScenarios.kt` contains replay fixtures and authored runtime
  states for verification. It stays in `main` because those named state images
  are compiled into the playable bundle and selected by runtime smoke/playtest
  launchers such as `--state fresh-replay` and `--state strong-replay`.
- `src/main/resources/tiles` contains ANSI tile art.
- `src/main/resources/bitmap` contains bitmap assets used by the bitmap web
  runtime.

For authoring syntax, extension examples, and DSL/runtime ownership rules, see
the [DSL authoring guide](../docs/dsl-authoring.md).

Reusable DSL, compiler, validation, runtime, and renderer code should not be
added here. This module should stay focused on game data, scripts, fixtures, and
assets.
