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

## Browser Smoke

Run the browser canvas smoke with:

```sh
./gradlew :runtime-bitmap-web:checkBitmapBrowserSmoke --console=plain
```

The task resolves Playwright through Gradle and downloads the managed browser
under `runtime-bitmap-web/build/playwright-browsers`; it does not use or install
a system Chrome/Chromium. The default browser is Chromium. To try another
Playwright browser:

```sh
./gradlew :runtime-bitmap-web:checkBitmapBrowserSmoke -Prylween.browserSmoke.browser=firefox --console=plain
```

The smoke seeds a Strong party, starts the installed bitmap server on a local
ephemeral port, waits for the first rendered canvas frame, samples representative
compiled `/assets/game-image/<id>` resources, and writes a screenshot to
`runtime-bitmap-web/build/reports/bitmap-browser-smoke/bitmap-first-frame.png`.
