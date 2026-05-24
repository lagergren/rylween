# Bitmap Browser Smoke Tests

This source set contains the Playwright-backed browser smoke for the bitmap web
runtime. It is an integration smoke, not a JVM unit test: it builds the runtime
launchers, starts a real local bitmap HTTP/WebSocket server, drives a headless
browser, and checks that the canvas runtime renders usable game frames.

Run it explicitly with:

```sh
./gradlew :runtime-bitmap-web:checkBitmapBrowserSmoke --console=plain
```

The task is intentionally separate from the normal `check` lifecycle so routine
unit-test runs do not unexpectedly fetch browser binaries. When invoked, Gradle
resolves the Playwright Java dependency and runs the Playwright CLI task
`installBitmapSmokeBrowser`. That downloads the selected Playwright-managed
browser into:

```text
runtime-bitmap-web/build/playwright-browsers
```

This keeps the test self-contained with respect to browser dependencies. It does
not require a system Chrome, Chromium, Firefox, or WebKit install, and it does
not install browser files outside the module build directory.

The default browser is Chromium. To run the same smoke with another Playwright
browser:

```sh
./gradlew :runtime-bitmap-web:checkBitmapBrowserSmoke -Prylween.browserSmoke.browser=firefox --console=plain
```

Supported values are `chromium`, `firefox`, and `webkit`, matching the names
accepted by `BitmapBrowserSmoke.kt`.

The smoke test does the following:

- resets an isolated work directory under
  `runtime-bitmap-web/build/bitmap-browser-smoke`;
- seeds a Strong save game through the installed JVM runtime launcher;
- starts the installed bitmap web runtime on an ephemeral `127.0.0.1` port;
- waits for `/healthz` to report `ok`;
- opens `/play` in a headless Playwright browser;
- waits until the canvas is nonblank, has enough color variation, has command
  choices, and has terrain transition assets ready;
- probes representative compiled image assets served through
  `/assets/game-image/<id>`;
- drives wilderness movement and the town leave flow through keyboard input;
- opens `/tools/wilderness-perspective` and verifies both the 3D view and
  keyboard controls.

Reports and logs are written to:

```text
runtime-bitmap-web/build/reports/bitmap-browser-smoke
```

Useful artifacts include `bitmap-first-frame.png`,
`bitmap-wilderness-3d.png`, `bitmap-wilderness-perspective-tool.png`,
`server.stdout.log`, and `server.stderr.log`.
