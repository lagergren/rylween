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
layout. The production image (`deploy/docker/Dockerfile.bitmap-production`)
still sets 8 and 64 when run with `docker run`; `deploy/compose.yaml` sets 6 and
68.

The 2D wilderness renderer keeps authored terrain cells unchanged and adds a
visual-only transition pass: where a neighbouring cell's terrain ranks higher,
the client draws that terrain's edge or corner overlay, rotated into place. The
overlays are transparent PNGs under `bitmap-web/wilderness/transitions`, painted
from the terrain atlas texture and cut to irregular edge/corner alpha masks.
They are classpath resources in the bitmap runtime jar and Docker image; they do
not require an unpacked server-side asset tree.

For local inspection, start the server with `--debug-secrets` or
`RYLWEEN_BITMAP_DEBUG_SECRETS=true`; then press `M` in the wilderness to show
the annotated full world map with towns, huts, and other locations.

Run bare, the server plays the game built into its jar unless `--bundle` or
`RYLWEEN_BITMAP_BUNDLE` names an emitted bundle, and keeps its data in
`build/rylween-data`.

## Play Page

The title bar reads "Adventurers of Rylween (v0.9.1)", with the version from
`/api/status`, which the Status panel also lists; the title menu shows the
version in its bottom-right corner.

Up and Down move a highlight through a menu and Enter picks it; letters and
clicks still work, and the mouse moves the same highlight. A menu that ends
with "RETURN leaves." opens with that line highlighted, so a stray Enter just
leaves. The arrows keep walking on the map: the wilderness only counts as a
menu while it shows one, dungeons and castles only for menus that RETURN leaves,
and party pages keep paging.

Quitting goes back to the title screen with the browser still connected, where
Load Game picks up the save. With `--no-title` (`RYLWEEN_BITMAP_NO_TITLE=true`),
after a game over, or on disconnect, the connection closes.

The View selector switches the wilderness between the 2D map and an
experimental 3D view, remembered per browser. In 3D, Up/8 steps forward,
Down/numpad 2 steps back, and Left/4 and Right/6 turn.

Sound effects and music changes arrive on the play socket as their own `sound`
and `music` messages, so a new track starts at once; frames also carry the
current track so a reconnect resumes it.

The bitmap art is the 2026-10-13 revamp. Scenes, monsters, portraits, title
art, the UI frame, and atlases are `:game` resources under `bitmap/assets`;
wilderness tiles and transition overlays are this module's
`bitmap-web/wilderness` resources. Assets are fetched with a `?v=` version
(`assetVersion` in `client.ts` and `toolAssetVersion` in
`wilderness-perspective.ts`), and the pages load their scripts with their own
`?v=` in `index.html` and `wilderness-perspective.html`. Bump the matching
version when art or a client script changes so browsers drop stale copies.

## Tools

`/tools/wilderness-perspective` renders any wilderness position (X, Y, facing)
in 2D or 3D; its "3D view" checkbox switches between the map and 3D (the top-row
digit keys belong to the game and do not switch views). `/tools/art-gallery`
lists every static bitmap asset and compiled game image the server serves. Both
require a login unless the server runs with the `--user` development bypass.

## Deploy Script

On Linux and macOS, `./deploy.sh --launcher` (the root `deploy.sh` links to
`deploy/deploy-rylween.sh`) starts the installed bitmap runtime in the
background with an isolated data directory under `~/.rylween/deploy`. By
default it binds to the private LAN IPv4 address of the interface carrying the
default route, so other machines on the local network can reach the server
without it listening on every network interface:

```sh
./gradlew :runtime-bitmap-web:installRuntimeBitmapWeb --console=plain
./deploy.sh --launcher
```

It finds the installed launcher relative to the repository, so it can be run
from any directory, and refuses to start when the launcher is not installed.
Rerunning it stops the server it started
earlier before starting a new one. On Linux it uses `ip`, `/proc`, and
`setsid`; on macOS it falls back to `route`, `ifconfig`, `ps`, and `nohup`. It
prints the detected server URL, for example `http://192.168.1.20:8081`. It
refuses to pick a public address automatically. Override the bind address when
needed:

```sh
RYLWEEN_DEPLOY_HOST=127.0.0.1 ./deploy.sh --launcher
RYLWEEN_DEPLOY_HOST=192.168.x.y ./deploy.sh --launcher
```

`RYLWEEN_DEPLOY_PORT`, `RYLWEEN_DEPLOY_DATA_DIR`, and `RYLWEEN_DEPLOY_LOG_FILE`
override the port (default `8081`), data directory, and log file (default
`/tmp/rylween-bitmap-web-deploy.log`), and `RYLWEEN_DEPLOY_APP` points it at
another installed launcher. On first start the server creates the sysop account
`admin`; unless `RYLWEEN_ADMIN_PASSWORD` is set, its generated password is in
`~/.rylween/deploy/auth/admin-password.txt`.

Without `--launcher`, `./deploy.sh` runs the production image through
`deploy/compose.yaml` instead, published on the same detected LAN address (see
[`deploy/docker/README.md`](../../deploy/docker/README.md#compose)). There
`RYLWEEN_DEPLOY_HOST` still picks the address; the port, data, and log settings
are replaced by compose's `RYLWEEN_HOST_PORT` and `RYLWEEN_DATA_VOLUME`.

Do not use `RYLWEEN_DEPLOY_HOST=0.0.0.0` unless the server should listen on all
IPv4 interfaces.

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
compiled `/assets/game-image/<id>` resources, decodes a sound and a music track,
walks into Thord and out again, and checks the 3D wilderness view and the
wilderness perspective tool. Screenshots (`bitmap-first-frame.png`,
`bitmap-wilderness-3d.png`, `bitmap-wilderness-perspective-tool.png`) and server
output go to `runtimes/runtime-bitmap-web/build/reports/bitmap-browser-smoke/`;
see [`src/browserSmoke/README.md`](src/browserSmoke/README.md).
