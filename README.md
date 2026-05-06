# Adventurers of Rylween
Old code archive and potential ports for Adventurers of Rylween, a MUD style games for BBSes that I wrote in 1992-1993.

The current runnable port is the Kotlin DSL/runtime under `src/main`,
`src/game`, and `src/app`. The historical C/JSON5 port and its replay
fixtures live under `legacy/c-json5` for reference and parity checks.

## Terminal compatibility

The game streams ANSI escape codes (colour, cursor positioning) and uses
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

## Kotlin runtime commands

Build the direct ANSI launcher first:

```sh
./gradlew installKotlinAnsi --console=plain
./run.sh --state fresh-replay --seed 1
./run.sh --build --state fresh-replay --seed 1
build/install/rylween-kotlin/bin/rylween --state fresh-replay --seed 1
build/install/rylween-kotlin/bin/rylween --build --state fresh-replay --seed 1
```

The root `run.sh` wrapper and generated `build/install/rylween-kotlin/bin/rylween`
script are the closest current equivalents to `rylween.exe`. Use them for interactive ANSI play; they run
outside Gradle so Gradle progress output and key echo do not share the game
screen. It loads the compiled DSL game, packaged `ansi/**` resources, the
authored wilderness map, the classic screen frame, text/status/map regions, and
the terminal terrain glyph table. Pass `--build` when you want to refresh the
installed runtime with `./gradlew installKotlinAnsi --console=plain` before
launching; `run.sh` also bootstraps that install if the generated launcher is
missing.

The line-oriented shells are still useful for testing:

```sh
./gradlew runKotlinGame --args="--state fresh-replay --seed 1"
./gradlew runKotlinTown --args="--town thord --hour 12 --seed 1"
```

Useful ANSI logging forms:

```sh
RYL_LOG=stderr build/install/rylween-kotlin/bin/rylween --state fresh-replay
RYL_LOG=file:build/rylween.log build/install/rylween-kotlin/bin/rylween --state fresh-replay
RYL_LOG=tcp:9999,file:build/rylween.log build/install/rylween-kotlin/bin/rylween --state fresh-replay
RYL_LOG_LEVEL=debug RYL_LOG=file:build/rylween.log ./run.sh --state fresh-replay
./run.sh --serve 127.0.0.1:2323 --state fresh-replay
```

`--serve [host:]port` waits for one socket/telnet client and renders the same
ANSI UI to that socket instead of the local terminal. Logs still use `RYL_LOG`,
so server diagnostics stay off the player render stream. `RYL_LOG_LEVEL=debug`
adds formula-evaluation and authored-op execution traces.
