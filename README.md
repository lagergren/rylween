# Adventurers of Rylween
Kotlin port and runtime workspace for Adventurers of Rylween, a MUD
style game for BBSes that I wrote in 1992-1993.

The current runnable Kotlin game is the `:runtime` project. Shared
DSL/compiler and compiled-bundle contracts live under `:core`, authored game
DSL and resources live under `:game`, bundle emitters and native probes live
under `:tools`, and terminal transport/protocol code lives under `:terminal`.

Module guides:

- [DSL authoring guide](docs/dsl-authoring.md) - how to modify or extend the
  authored game.
- [`:core`](core/README.md) - DSL, validation, compiled bundle contracts.
- [`:game`](game/README.md) - authored Rylween game script, fixtures, assets.
- [`:runtime`](runtimes/runtime/README.md) - shared game runtime and direct ANSI terminal.
- [`:runtime-web`](runtimes/runtime-web/README.md) - browser-served ANSI runtime.
- [`:runtime-bitmap-web`](runtimes/runtime-bitmap-web/README.md) - canvas bitmap runtime.
- [`:web-common`](support/web-common/README.md) - shared web HTTP, WebSocket, auth, status.
- [`:terminal`](support/terminal/README.md) - ANSI terminal primitives.
- [`:tools`](support/tools/README.md) - compiled-bundle artifact tools and native probes.
- [`build-logic`](build-logic/README.md) - Gradle conventions and custom tasks.
- [`bin`](bin/README.md) - local developer/operator launcher scripts.
- [`deploy/docker`](deploy/docker/README.md) - Docker images and reverse-proxy deployment notes.

The original game text and manual live in
[`adventurers-of-rylween.md`](game/src/main/resources/adventurers-of-rylween.md).

The old interpreted Kotlin ANSI project and historical C/JSON5 port are
removed from the active checkout; their final tree is zipped at
`scratch/legacy-archive.zip`.

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

## Runtime commands

Build and run the direct runtime:

```sh
./gradlew :tools:emitExternalCompiledBundle :runtime:installRuntime --console=plain
./bin/run.sh --build --seed 1
./bin/run.sh --build --name Strong
./bin/run.sh --build --name StrongEndgame
runtimes/runtime/build/install/rylween/bin/rylween --bundle build/compiled/rylween-bundle.json --user local --state fresh-replay --seed 1
```

The `bin/run.sh` wrapper is now the closest current equivalent to
`rylween.exe`. It builds `build/compiled/rylween-bundle.json` and
`:runtime:installRuntime` when needed, injects the bundle path,
and runs outside Gradle so Gradle progress output and key echo do not share the
game screen.

`bin/run.sh` uses `~/.rylween/data` by default. It stores the shared save at
`~/.rylween/data/saves/world.rsave` and logs to
`~/.rylween/data/logs/runtime/latest.log` unless `RYLWEEN_DATA_DIR`,
`RYLWEEN_SAVE_FILE`, or `RYLWEEN_LOG_FILE` override those paths.

`bin/run.sh` shows the title flow by default. Press RETURN at the splash screen,
then `C` to create a new party. Use `--name Strong` to load the seeded Strong
party outside Thord, or `--name StrongEndgame` to load a Strong party two steps
north of Castle Ydarg with the five required power artifacts. Use `--no-title`
only when you intentionally want to boot a state or feed a direct script.
Use `--log <file>` to keep a named capture instead of replacing `latest.log`.
For manual forensics, `--debug-log` flashes the latest log line below the game
frame, and `--debug-secrets` enables hidden wilderness triggers: `[` forces a
cave discovery and `]` forces a random encounter.

```sh
./bin/run.sh --screen title
./bin/run.sh --no-title --script 22SX --save build/saves/runtime.rsave
./bin/run.sh --load build/saves/runtime.rsave
./bin/run.sh --name Strong --debug-log --debug-secrets
```

Run the browser ANSI proof of concept in Docker:

```sh
./bin/run-server.sh --build
./bin/run-server.sh --build --name Strong
./bin/run-server.sh --build --name StrongEndgame
./bin/run-server.sh --account Strong --email strong@example.test --password 'change-this-password'
```

The named forms seed `~/.rylween/data/saves/world.rsave` before starting the
container and use the local development auth bypass. The account form creates
or verifies a persisted web account before the server starts.

Run the bitmap web runtime locally:

```sh
./bin/run-bitmap-server.sh --build
./bin/run-bitmap-server.sh --build --name Strong
./bin/run-bitmap-server.sh --build --name StrongEndgame
./bin/run-bitmap-server.sh --account Strong --email strong@example.test --password 'change-this-password'
```

Back up web game state and users as one artifact:

```sh
./bin/backup-web-data.sh --data ~/.rylween/data --output rylween-backup.tar.gz
```

The data directory is split by ownership: `saves/world.rsave` is game state,
`auth/users` is account state, and `logs` is operational history. The exact file
layout and field meanings are documented in
[`support/web-common/README.md`](support/web-common/README.md) and
[`runtimes/runtime/README.md`](runtimes/runtime/README.md).

Build the production-oriented ANSI web image for reverse-proxy deployment:

```sh
docker build -f deploy/docker/Dockerfile.production -t rylween-web-prod .
```

Build the bitmap production image:

```sh
docker build -f deploy/docker/Dockerfile.bitmap-production -t rylween-bitmap-prod .
```

Or build and run it with a persistent data volume through
`deploy/compose.yaml` (see [`deploy/docker/README.md`](deploy/docker/README.md#compose)):

```sh
docker compose -f deploy/compose.yaml up --build -d     # or: podman compose ...
```

The production image still serves plain HTTP internally. Bind it to loopback or
a private container network, terminate HTTPS at nginx/Caddy/HAProxy/a load
balancer, and keep `RYLWEEN_AUTH_SECURE_COOKIES=true`. See
[`support/web-common/README.md`](support/web-common/README.md) for the auth model and production
hardening checklist.

The runtime boots directly from the compiled bundle, renders static screens and
wilderness frames, runs wilderness/town/services/battle/dungeon and castle flows
from compiled tables, handles direct save/load with bundle-hash validation, and
is verified by `:runtime:check`. Long acceptance routes are generated from
test-only replay plans and fed to the installed runtime through `--script`;
the shipped launcher does not expose a replay-planning mode.



## Verification

Run the normal verification graph:

```sh
./gradlew check --console=plain
```

`check` also runs `checkDependencyUpdates`, which only reports newer dependency
and plugin versions from `gradle/libs.versions.toml`. To apply those catalog
updates intentionally, run:

```sh
./gradlew updateDependencyCatalog --console=plain
```

## Cleanup

Generated files are confined to Gradle build output, local Kotlin compiler
caches, and runtime save/log directories under `build/`. From the repo root:

```sh
./gradlew clean --console=plain
```

This removes project build directories, the included `build-logic/build`
directory, and regenerated local `.kotlin` session/error caches.
