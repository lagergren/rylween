# Adventurers of Rylween
Old code archive and potential ports for Adventurers of Rylween, a MUD style games for BBSes that I wrote in 1992-1993.

The current runnable Kotlin game is the standalone compiled-bundle consumer in
`compiled-runtime`. Shared DSL/compiler and compiled-bundle contracts live under
`src/main`, shared ANSI terminal protocol primitives live under `ansi-terminal`,
and the authored game lives under `src/game`. The old interpreted Kotlin ANSI
project is archived under `legacy/ansi-runtime` and is no longer an active
Gradle project. The historical C/JSON5 port and replay fixtures remain under
`legacy/c-json5` for reference and parity checks.

## Terminal compatibility

The game streams ANSI escape codes (color, cursor positioning) and uses
ambiguous-width Unicode characters in some art. How those characters are
sized depends on the terminal:

- **macOS `Terminal.app`** renders East-Asian-Width-ambiguous characters
  as 2 cells wide. Some art and layouts drift as a result. This isn't a
  bug in the game — `Terminal.app` makes the wrong call for the
  character set the game uses.
- Recommended terminals that get this right out of the box:
  **iTerm2**, **Ghostty**, **Alacritty**, **Kitty**, and **WezTerm**.
  Any of these renders the art as intended on macOS, Linux, and BSD.

If you must run in `Terminal.app`, expect occasional one-cell drift in
borders and decorative art. Gameplay text is unaffected.

## Compiled runtime commands

Build and run the direct compiled-bundle runtime:

```sh
./gradlew :compiled-runtime:installCompiledRuntime --console=plain
./run.sh --state fresh-replay --seed 1
./run.sh --build --state fresh-replay --seed 1
compiled-runtime/build/install/rylween-compiled-runtime/bin/rylween-compiled --bundle build/compiled/rylween-bundle.json --state fresh-replay --seed 1
```

The root `run.sh` wrapper is now the closest current equivalent to
`rylween.exe`. It builds `build/compiled/rylween-bundle.json` and
`:compiled-runtime:installCompiledRuntime` when needed, injects the bundle path,
and runs outside Gradle so Gradle progress output and key echo do not share the
game screen.

Interactive runs without `--load`, `--script`, or `--replay` show the title
flow first. Press any key at the splash screen, then `N` to create a new party.
Each run writes a forensic log to `build/logs/compiled-runtime/latest.log`; use
`--log <file>` to keep a named capture instead of replacing `latest.log`.
For manual forensics, `--debug-log` flashes the latest log line below the game
frame, and `--debug-secrets` enables hidden wilderness triggers: `[` forces a
cave discovery and `]` forces a random encounter.

```sh
./run.sh --screen title
./run.sh --script 22SX --save build/saves/compiled-runtime.rsave
./run.sh --load build/saves/compiled-runtime.rsave
./run.sh --state strong-replay --seed 1 --debug-log --debug-secrets
```

The compiled runtime boots directly from the bundle, renders compiled static
screens and wilderness frames, runs wilderness/town/services/battle/dungeon and
castle flows from compiled tables, handles direct save/load with bundle-hash
validation, and is verified by the `:compiled-runtime:check` replay suite.
