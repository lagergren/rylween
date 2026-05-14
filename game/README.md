# Game

`:game` contains the authored Adventurers of Rylween content.

Important areas:

- `src/main/kotlin/rylween/game/RylweenGame.kt` is the main DSL-authored game
  script.
- `RylweenCompiledGame.kt` exposes the compiled in-process game facade used by
  runtimes and tests.
- `RylweenTestScenarios.kt` contains replay fixtures and authored runtime
  states for verification.
- `src/main/resources/tiles` contains ANSI tile art.
- `src/main/resources/bitmap` contains bitmap assets used by the bitmap web
  runtime.

Reusable DSL, compiler, validation, runtime, and renderer code should not be
added here. This module should stay focused on game data, scripts, fixtures, and
assets.
