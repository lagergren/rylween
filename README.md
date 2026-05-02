# Adventurers of Rylween
Old code archive and potential ports for Adventurers of Rylween, a MUD style games for BBSes that I wrote in 1992-1993.

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

The current Kotlin build has three runnable shells:

```sh
./gradlew runKotlinAnsi --args="--state fresh-replay --seed 1"
./gradlew runKotlinGame --args="--state fresh-replay --seed 1"
./gradlew runKotlinTown --args="--town thord --hour 12 --seed 1"
```

`runKotlinAnsi` is the closest current equivalent to `rylween.exe`: it loads
the compiled DSL game, packaged `ansi/**` resources, packaged `map.dat`, the
classic screen frame, text/status/map regions, and the terminal terrain glyph
table. Useful logging forms:

```sh
RYL_LOG=stderr ./gradlew runKotlinAnsi --args="--state fresh-replay"
RYL_LOG=file:build/rylween.log ./gradlew runKotlinAnsi --args="--state fresh-replay"
RYL_LOG=tcp:9999,file:build/rylween.log ./gradlew runKotlinAnsi --args="--state fresh-replay"
```
