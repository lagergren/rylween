# Runtime Web

`:runtime-web` serves the existing ANSI runtime to a browser.

It wraps the shared `:runtime` backend with:

- a small HTTP/WebSocket server,
- authentication/session wiring from `:web-common`,
- `BrowserAnsiTerminal`, which forwards ANSI output to a browser terminal view,
- the installed `rylween-web` launcher used by the Docker image and
  `./rylween serve --ansi --no-docker`.

This module should not own game mechanics, key-command meaning, password
storage, or generic WebSocket protocol code. Those belong in `:runtime` or
`:web-common`.

## Running It Locally

Build the bundle and the launcher, then run the launcher against the bundle:

```sh
./gradlew :tools:emitExternalCompiledBundle :runtime-web:installRuntimeWeb --console=plain
runtimes/runtime-web/build/install/rylween-web/bin/rylween-web \
  --bundle build/compiled/rylween-bundle.json --data ~/.rylween/data --host 127.0.0.1
```

The launcher's defaults are the container's: bundle `/app/rylween-bundle.json`,
data directory `/data`, host `0.0.0.0`, port `8080`, and state `fresh-replay`.
`rylween-web --help` lists every flag and environment variable, including
`RYLWEEN_BUNDLE`, `RYLWEEN_DATA_DIR`, `RYLWEEN_HOST`, `RYLWEEN_PORT`, the
`RYLWEEN_AUTH_*` settings, and the sysop settings described below.
`./rylween serve --ansi` runs the same server in a container, and
`./rylween serve --ansi --no-docker` runs this launcher for you.

## Sessions

Only one ANSI web session plays the shared world at a time; other players see a
waiting message until it ends. A second connection for an account that is
already playing is turned away. Quitting ends the session and closes the
connection, so the player reloads the page to play again. (The bitmap server
returns to its title screen instead.)

## Running It On A Server

[`deploy/README.md`](../../deploy/README.md#the-ansi-game-in-the-browser) covers
its image, `deploy/docker/Dockerfile.ansi`, and putting a
server on the internet behind HTTPS, with port forwarding and network advice.
