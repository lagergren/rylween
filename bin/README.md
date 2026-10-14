# Launcher Scripts

`bin/` contains local developer and operator entrypoints for running Rylween
from this checkout.

- `run.sh` launches the direct ANSI terminal runtime, building the bundle and
  install first with `--build` or when they are missing.
- `run-server.sh` runs the browser-served ANSI web runtime in Docker on
  `127.0.0.1:8080`; `--build` rebuilds the `deploy/docker/Dockerfile` image
  first.
- `run-bitmap-server.sh` launches the bitmap web runtime locally on
  `127.0.0.1:8081`, building the install first with `--build` or when it is
  missing.
- `backup-web-data.sh` archives saves and web auth records from the data
  directory; `--include-logs` adds the logs.

All three launchers take `--name Strong` or `--name StrongEndgame` to seed and
load a named save, and keep their data under `~/.rylween/data`
(`RYLWEEN_DATA_DIR`, or `RYLWEEN_HOST_DATA_DIR` for `run-server.sh`).

These scripts are intentionally not under `deploy/`: they are normal checkout
commands, useful for development, smoke testing, local play, and simple
operations. Production packaging lives under `deploy/docker/`. Container and
LAN rollout lives in `deploy/`: `compose.yaml`, `deploy-rylween.sh`, and
`deploy-rylween-docker.sh`, with the root `./deploy.sh` as a symlink to the
last.

Run them from the repository root.

## Terminal Game

```sh
./bin/run.sh --build --seed 1
./bin/run.sh --build --name Strong
./bin/run.sh --build --name StrongEndgame
```

`run.sh` builds `build/compiled/rylween-bundle.json` and
`:runtime:installRuntime` when needed, injects the bundle path, and runs outside
Gradle so Gradle progress output and key echo do not share the game screen. The
installed launcher can also be run directly:

```sh
./gradlew :tools:emitExternalCompiledBundle :runtime:installRuntime --console=plain
runtimes/runtime/build/install/rylween/bin/rylween --bundle build/compiled/rylween-bundle.json --user local --state fresh-replay --seed 1
```

`run.sh` stores the shared save at `~/.rylween/data/saves/world.rsave` and logs
to `~/.rylween/data/logs/runtime/latest.log` unless `RYLWEEN_DATA_DIR`,
`RYLWEEN_SAVE_FILE`, or `RYLWEEN_LOG_FILE` override those paths.

It shows the title flow by default. Use `--name Strong` to load the seeded
Strong party outside Thord, or `--name StrongEndgame` to load a Strong party two
steps north of Castle Ydarg with the five required power artifacts. Use
`--no-title` only when you intentionally want to boot a state or feed a direct
script. Use `--log <file>` to keep a named capture instead of replacing
`latest.log`. For manual forensics, `--debug-log` flashes the latest log line
below the game frame, and `--debug-secrets` enables hidden wilderness keys: `[`
forces a cave discovery, `]` forces a random encounter, and `M` shows the whole
world map.

```sh
./bin/run.sh --screen title
./bin/run.sh --no-title --script 22SX --save build/saves/runtime.rsave
./bin/run.sh --load build/saves/runtime.rsave
./bin/run.sh --name Strong --debug-log --debug-secrets
```

## Web Servers

```sh
./bin/run-server.sh --build
./bin/run-server.sh --build --name Strong
./bin/run-server.sh --account Strong --email strong@example.test --password 'change-this-password'

./bin/run-bitmap-server.sh --build
./bin/run-bitmap-server.sh --build --name StrongEndgame
./bin/run-bitmap-server.sh --account Strong --email strong@example.test --password 'change-this-password'
```

The ANSI server publishes on http://127.0.0.1:8080/ and the bitmap server on
http://127.0.0.1:8081/. The named forms seed `~/.rylween/data/saves/world.rsave`
before starting and use the local development auth bypass. The account form
creates or verifies a persisted web account before the server starts.

## Backup

```sh
./bin/backup-web-data.sh --data ~/.rylween/data --output rylween-backup.tar.gz
```

The data directory is split by ownership: `saves/world.rsave` is game state,
`auth/users` is account state, and `logs` is operational history. The exact file
layout and field meanings are documented in
[`support/web-common/README.md`](../support/web-common/README.md) and
[`runtimes/runtime/README.md`](../runtimes/runtime/README.md).
