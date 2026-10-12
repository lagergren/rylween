# Runtime Bitmap Web

`:runtime-bitmap-web` is the browser canvas bitmap runtime.

It is a peer runtime to `:runtime-web`, not a layer on top of the ANSI terminal
view. It reuses the shared compiled-bundle backend from `:runtime`, the authored
game/assets from `:game`, and web auth/protocol helpers from `:web-common`.

It contains:

- the bitmap HTTP/WebSocket launcher,
- the renderer-neutral runtime port implementation that emits bitmap frames,
- the canvas client TypeScript, HTML, and CSS resources,
- bitmap frame protocol data classes,
- the installed `rylween-bitmap-web` launcher used by
  `bin/run-bitmap-server.sh`.

Renderer-specific layout, art selection, transitions, and canvas presentation
belong here. Shared gameplay behavior, command semantics, battle targeting,
save/load, and script execution belong in `:runtime`.

## Wilderness Viewport

The bitmap wilderness map defaults to a 6x6 viewport at 68 px per tile. The
server keeps this configurable for quick visual tuning:

```sh
./bin/run-bitmap-server.sh --build --name Strong --map-tiles 8 --tile-size 64
```

The same settings can be supplied with `RYLWEEN_BITMAP_MAP_TILES` and
`RYLWEEN_BITMAP_TILE_SIZE`. Use `8` and `64` to return to the previous 8x8
layout.

For local inspection, start the server with `--debug-secrets` or
`RYLWEEN_BITMAP_DEBUG_SECRETS=true`; then press `M` in the wilderness to show
the annotated full world map with towns, huts, and other locations.

## Deploy Script

On Linux and macOS, the root `deploy-rylween.sh` helper starts the installed
bitmap runtime in the background with an isolated data directory under
`~/.rylween/deploy`. By default it binds to the private LAN IPv4 address of the
interface carrying the default route, so other machines on the local network
can reach the server without it listening on every network interface:

```sh
./gradlew :runtime-bitmap-web:installRuntimeBitmapWeb --console=plain
./deploy-rylween.sh
```

Run it from the repository root. Rerunning it stops the server it started
earlier before starting a new one. On Linux it uses `ip`, `/proc`, and
`setsid`; on macOS it falls back to `route`, `ifconfig`, `ps`, and `nohup`. It
prints the detected server URL, for example `http://192.168.1.20:8081`. It
refuses to pick a public address automatically. Override the bind address when
needed:

```sh
RYLWEEN_DEPLOY_HOST=127.0.0.1 ./deploy-rylween.sh
RYLWEEN_DEPLOY_HOST=192.168.x.y ./deploy-rylween.sh
```

`RYLWEEN_DEPLOY_PORT`, `RYLWEEN_DEPLOY_DATA_DIR`, and `RYLWEEN_DEPLOY_LOG_FILE`
override the port (default `8081`), data directory, and log file (default
`/tmp/rylween-bitmap-web-deploy.log`).

Do not use `RYLWEEN_DEPLOY_HOST=0.0.0.0` unless the server should listen on all
IPv4 interfaces.

The 2D wilderness renderer keeps authored terrain cells unchanged and adds a
visual-only transition pass with bundled transparent PNG overlays under
`bitmap-web/wilderness/transitions`. These overlays are drawn from the same
terrain atlas texture as the map tiles, then clipped to irregular edge/corner
alpha masks. Those resources are classpath assets in the runtime web jar and
Docker image; they do not require an unpacked server-side asset tree.

## Browser Smoke

Run the browser canvas smoke with:

```sh
./gradlew :runtime-bitmap-web:checkBitmapBrowserSmoke --console=plain
```

The task resolves Playwright through Gradle and downloads the managed browser
under `runtimes/runtime-bitmap-web/build/playwright-browsers`; it does not use or install
a system Chrome/Chromium. The default browser is Chromium. To try another
Playwright browser:

```sh
./gradlew :runtime-bitmap-web:checkBitmapBrowserSmoke -Prylween.browserSmoke.browser=firefox --console=plain
```

The smoke seeds a Strong party, starts the installed bitmap server on a local
ephemeral port, waits for the first rendered canvas frame, samples representative
compiled `/assets/game-image/<id>` resources, and writes a screenshot to
`runtimes/runtime-bitmap-web/build/reports/bitmap-browser-smoke/bitmap-first-frame.png`.
