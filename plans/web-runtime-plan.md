# Web Runtime Plan

This plan scopes the smallest useful browser-playable Rylween while keeping the
real target in sight: a web app with better graphics, persistent accounts, and
several simultaneous players served from one deployable container.

The browser should not become a separate game implementation. It should consume
the same compiled JSON bundle and runtime rules as the current JVM runtime.

## Current Runtime Facts

- `runtime/src/main/kotlin/rylween/runtime/RylweenRuntimeMain.kt` is the active
  playable runtime entry point. `GameRuntime.load(...)` reads:
  `CompiledGameBundle`, `ExternalRuntimeSystems`, and the bundle content hash
  from `build/compiled/rylween-bundle.json`.
- The runtime already owns direct gameplay for wilderness, town services,
  battle, dungeon, castle, utility commands, script execution, character
  creation, save/load, and static screens without composing old session
  adapters.
- `terminal/src/main/kotlin/rylween/terminal/AnsiTerminal.kt` is the narrow
  transport interface: raw text output, flush, key token input, text-line input,
  and pause.
- `RuntimeScreen` is still ANSI-specific. It reads presentation rows from
  `ExternalRuntimeSystems.presentation`, resolves `.ans` resources from
  `game/src/main/resources`, writes cursor-control sequences, and renders the
  80-column frame, map window, text window, status lines, and full-screen static
  routes.
- `SaveFile` is already runtime-owned and validates the compiled bundle hash
  before restoring state. Today it writes one save file per runtime invocation.
- `./gradlew :runtime:check --console=plain` passes against the current direct
  runtime and installed launcher.

## Product Target

Ship a container that starts one web server. A user opens the site, creates or
logs into an account, and plays a persisted Rylween party in the browser. Several
users can play at the same time. For now, each user's save is independent, and
global conflicts such as two users winning at the same time are explicitly out
of scope.

The desired final browser experience is not just ANSI in a web terminal. The
world should remain tile-based, but the tile and scene renderers should use
modern bitmap assets: terrain tiles for mountains, water, plains, towns, huts,
castles, forests, swamps, deserts, and foothills; richer scene art for shops,
encounters, dungeons, caves, tents, death, victory, and castle views; and a
browser-native layout for status, party, inventory, journal, and prompts.

ANSI remains valuable as:

- the fastest proof that the existing game loop can run through a browser
  transport,
- a fallback/debug rendering mode,
- reference art for sprite and scene-asset production.

## First Implementation Decision

Build the web app first, using ANSI-in-browser as the proof of concept. Do not
start by replacing the renderer. The smallest path that exercises the whole game
is:

1. Make the current runtime loop accept an injected terminal/session transport
   instead of opening `AnsiTerminals.open(...)` internally.
2. Add a small JVM web module that provides a WebSocket-backed `AnsiTerminal`.
3. Render the stream with xterm.js in the browser so cursor positioning, window
   clearing, colors, and full-screen ANSI art work without writing a custom ANSI
   parser.
4. Persist accounts and per-account save files under a mounted server data
   directory.

This proves web login, concurrent sessions, server persistence, and game-loop
fit before investing in the better graphics layer.

Avoid launching the current CLI runtime as a subprocess for the first web build.
It looks tempting because it avoids refactoring, but the current launcher uses
JLine raw terminal behavior and is not designed for pipe-backed interactive I/O.
An injected `AnsiTerminal` is a smaller and cleaner change.

## Minimal Server Shape

Add a new `:web-runtime` JVM module.

Recommended dependencies:

- `:runtime` for the direct game loop.
- `:core` transitively for bundle types.
- `:game` at runtime for packaged ANSI/manual resources.
- Ktor Netty with WebSockets and static file serving.

Server responsibilities:

- Load the compiled bundle once on startup.
- Serve `/` with a small login/register page.
- Serve `/play` with the browser client after authentication.
- Serve `GET /healthz`.
- Accept `WS /ws/play` for authenticated game sessions.
- Store mutable server data under `RYLWEEN_DATA`, defaulting to `/data`.
- Keep one active runtime session per account at a time.
- Give each account its own save file, for example
  `/data/realms/default/parties/<account-id>.rsave`.

The first account store can be file-based to keep the container small:

- `/data/users/<username>.properties`
- PBKDF2-HMAC-SHA256 password hashes using JDK crypto.
- A random account id and party save path per user.
- A process-local active-session lock to reject a second live browser session
  for the same account.

SQLite is the obvious next step once account metadata, sessions, Hall-of-Heroes,
or shared-world state need real queries or transactions. It is not required for
the first browser-playable container.

## Runtime Refactor Needed

Keep the CLI behavior intact, but introduce a programmatic session API.

Target shape:

```kotlin
class RuntimeSessionFactory {
    fun load(bundleFile: File): RuntimeSessionFactory

    fun start(
        terminal: AnsiTerminal,
        stateSlug: String = "fresh-replay",
        loadFile: File? = null,
        saveFile: File,
        seed: Long? = null,
        showTitleMenu: Boolean = false,
        log: RuntimeLog,
    ): RuntimeSessionResult
}
```

Exact names can change, but the important behavior is:

- no CLI parsing in the reusable API,
- no hardcoded `AnsiTerminals.open(...)` in the reusable loop,
- no `System.out` ownership outside the CLI wrapper,
- web callers can choose `loadFile`, `saveFile`, seed, title behavior, and log
  destination,
- the CLI keeps using the same API with the existing JLine/stdout terminals.

Some current `internal` runtime types may need to move behind a public API or
remain hidden behind the session factory. Prefer hiding them unless the web
renderer genuinely needs semantic state.

## ANSI Web POC

Browser:

- Use xterm.js in an 80x25 fixed terminal surface.
- Send key presses over WebSocket as the same normalized command tokens the
  runtime already consumes.
- Send text input lines for character/account prompts.
- Provide simple mobile/touch controls for movement and common commands after
  the keyboard path works.

Server:

- Implement `WebSocketAnsiTerminal`.
- `writeRaw(text)` sends an output frame to the socket.
- `readKeyToken()` blocks/suspends on an input channel.
- `readTextLine(maxLength)` switches the browser into text-entry mode and waits
  for a submitted line.
- `pause(ms)` should be cancellable and should not block the event loop thread.

This POC should support title flow, new party creation, load/save, wilderness,
towns, dungeons, castle, and battle because it is still the same runtime.

## Bitmap Web Renderer

After the ANSI POC works, split presentation from terminal transport.

Introduce a semantic view boundary that `RuntimeScreen` and future web renderers
can both implement. The browser renderer should receive structured events or
snapshots, not ANSI bytes:

- full-screen route changed,
- active scene image changed,
- text window lines changed,
- status/party resources changed,
- world viewport changed,
- prompt/input mode changed,
- command menu changed,
- battle/encounter state changed.

Keep `RuntimeScreen` as `AnsiRuntimeView` for CLI and debug mode. Add
`WebRuntimeView` for JSON/WebSocket events.

The web renderer should use:

- a canvas or WebGL-backed map layer for terrain tiles,
- deterministic tile variation by `(terrain slug, x, y, bundle hash)` so visual
  variety is stable without saving per-cell art choices,
- CSS/HTML panels for status, text, command buttons, party, inventory, journal,
  account controls, and modal prompts,
- scene images keyed by the existing image slugs from `ExternalImageAsset`,
- fallback rendering from parsed ANSI resources until a bitmap exists.

The compiled bundle already exports terrain slugs, screen regions, image slugs,
image resources, static screens, and command groups. The renderer should consume
those ids instead of hardcoding game concepts in JavaScript.

## Asset Path

Add bitmap alternatives to the authored visual declarations without deleting ANSI
assets:

```kotlin
visuals {
    images {
        ansi(Images.Scene.towneSmith, resource = "tiles/towne-smith.ans", placement = ImagePlacement.MapWindow)
        bitmap(Images.Scene.towneSmith, resource = "web/scenes/towne-smith.webp", placement = ImagePlacement.MapWindow)
    }
}
```

The exact DSL may need a different shape, but the bundle should expose enough
metadata for a runtime to choose the best supported asset kind:

1. Prefer bitmap/web resource in browser.
2. Fall back to ANSI rendered through an ANSI-cell canvas adapter.
3. Fall back to xterm/debug ANSI if semantic rendering is unavailable.

Terrain should get a first-class web tileset declaration. Example concepts:

- `terrainTile(Terrain.mountain, "web/tiles/mountain-*.webp")`
- variation count, animation flag, and passability inherited from terrain data,
- optional overlay for towns, huts, castles, cave entrances, and current party.

## Persistence Model

First web version:

- Account identity is server-owned.
- One account maps to one active party/save file.
- Saves remain the current runtime save format and keep bundle-hash validation.
- A user can create a new party if no save exists, or continue an existing save.
- Save on explicit in-game save command.
- Add autosave-on-disconnect only after the runtime session API can safely expose
  the current state at interruption points.

Later shared-world version:

- Split per-party state from realm/world state.
- Store global Hall-of-Heroes separately from party saves.
- Add file locks or SQLite transactions around shared state.
- Keep two-simultaneous-winners as a later product rule, not a blocker for the
  first web container.

## Container Target

Add a web runtime image that:

- builds `:tools:emitExternalCompiledBundle`,
- builds/install-runs `:web-runtime`,
- copies `rylween-bundle.json`,
- copies packaged game resources,
- exposes port `8080`,
- uses `/data` as a volume for users, saves, logs, and server secrets,
- starts with something like:

```sh
java -jar /app/rylween-web-runtime.jar --bundle /app/rylween-bundle.json --data /data --host 0.0.0.0 --port 8080
```

The current CLI Dockerfile can stay as the terminal product image. Add a
separate web Dockerfile or a multi-target Dockerfile so the two products are not
confused.

## Verification

Keep `:runtime:check` as the baseline. Add web-specific checks in this order:

1. Unit tests for account creation, login hash verification, and duplicate
   active-session rejection.
2. A fake WebSocket terminal test that drives title/static-screen output through
   the injected terminal API.
3. A scripted web session smoke that creates or loads two separate users and
   verifies their saves do not collide.
4. Browser tests for xterm/cursor clearing once the static client exists.
5. Browser screenshot/canvas tests for tile rendering once the bitmap renderer
   exists.

## Milestones

1. Runtime API extraction: CLI still passes `:runtime:check`.
2. Web ANSI POC: one authenticated user can play in the browser and save.
3. Concurrent web POC: two users can play at once with independent saves.
4. Web container: build and run with `/data` persistence.
5. Semantic view boundary: ANSI CLI and web JSON renderer share one runtime
   loop.
6. Bitmap terrain renderer: wilderness map uses real terrain tiles with stable
   variation.
7. Bitmap scene renderer: town/shop/encounter/dungeon/castle views use modern
   scene art with ANSI fallback.
8. Account hardening: password reset/admin tools, session expiry, logs, and
   backup-friendly data layout.
