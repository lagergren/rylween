# Adventurers of Rylween
Old code archive and potential ports for Adventurers of Rylween, a MUD style games for BBSes that I wrote in 1992-1993.

The current runnable ports are split by Gradle module: shared DSL/compiler and
reference runtime contracts live under `src/main`, shared ANSI terminal
protocol primitives under `ansi-terminal`, the authored game under `src/game`,
the classic bundle-backed ANSI product under `ansi-runtime`, and the
standalone compiled-bundle consumer under `compiled-runtime`. The
historical C/JSON5 port and its replay fixtures live under `legacy/c-json5` for
reference and parity checks.

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
./gradlew :ansi-runtime:installKotlinAnsi --console=plain
./run.sh --state fresh-replay --seed 1
./run.sh --build --state fresh-replay --seed 1
ansi-runtime/build/install/rylween-kotlin/bin/rylween --state fresh-replay --seed 1
```

The root `run.sh` wrapper and generated `ansi-runtime/build/install/rylween-kotlin/bin/rylween`
script are the closest current equivalents to `rylween.exe`. Use them for interactive ANSI play; they run
outside Gradle so Gradle progress output and key echo do not share the game
screen. It loads the compiled DSL game, packaged `ansi/**` resources, the
authored wilderness map, the classic screen frame, text/status/map regions, and
the terminal terrain glyph table. Pass `--build` when you want to refresh the
installed runtime with `./gradlew :ansi-runtime:installKotlinAnsi --console=plain` before
launching; `run.sh` also bootstraps that install if the generated launcher is
missing.

The line-oriented shells are still useful for testing:

```sh
./gradlew :ansi-runtime:runKotlinGame --args="--state fresh-replay --seed 1"
./gradlew :ansi-runtime:runKotlinTown --args="--town thord --hour 12 --seed 1"
```

Useful ANSI logging forms:

```sh
RYL_LOG=stderr ansi-runtime/build/install/rylween-kotlin/bin/rylween --state fresh-replay
RYL_LOG=file:build/rylween.log ansi-runtime/build/install/rylween-kotlin/bin/rylween --state fresh-replay
RYL_LOG=tcp:9999,file:build/rylween.log ansi-runtime/build/install/rylween-kotlin/bin/rylween --state fresh-replay
RYL_LOG_LEVEL=debug RYL_LOG=file:build/rylween.log ./run.sh --state fresh-replay
./run.sh --serve 127.0.0.1:2323 --state fresh-replay
```

`--serve [host:]port` waits for one socket/telnet client and renders the same
ANSI UI to that socket instead of the local terminal. Logs still use `RYL_LOG`,
so server diagnostics stay off the player render stream. `RYL_LOG_LEVEL=debug`
adds formula-evaluation and authored-op execution traces.

## Standalone compiled runtime commands

The standalone compiled-bundle consumer is intentionally separate from the
classic ANSI host. It reads `build/compiled/rylween-bundle.json` and owns its
own runtime state under `compiled-runtime`.

```sh
./run-compiled.sh --screen title
./run-compiled.sh --script 22SX --save build/saves/compiled-runtime.rsave
./run-compiled.sh --load build/saves/compiled-runtime.rsave
```

This runtime is still being completed. It currently boots directly from the
compiled bundle, renders compiled static screens and wilderness frames, runs
the direct wilderness loop, enters town services, handles compiled shop catalog
buy/sell paging, renders direct party/journal pages, reads wilderness command
bindings from the bundle, and writes/loads its standalone wilderness save format
with bundle-hash validation.
