# Runtime

`:runtime` is the shared compiled-bundle game runtime and the direct ANSI
terminal product.

It contains:

- The battle, wilderness, town, dungeon, castle, shop, script, and party-page
  services that execute compiled game content.
- Save/load support for the shared-world save file.
- Renderer-neutral input/screen ports used by both terminal and bitmap web
  hosts.
- The ANSI `RuntimeScreen` implementation for the local terminal runtime.
- The ANSI terminal primitives in `rylween.terminal`; see
  [Terminal Primitives](#terminal-primitives).
- The installed `rylween` launcher (`:runtime:installRuntime`, under
  `runtimes/runtime/build/install/rylween`) used by `bin/run.sh`, the scripted
  `checkRuntime*` checks, the runtime tests, and the bitmap browser smoke's seed
  step. It, and the terminal game's release zip, run with a copy of `:game`'s
  jar without the bitmap paintings, which the terminal never shows.

The build stamps `rylween.version` from `gradle.properties` into the runtime.
The title menu shows it as `vX.Y.Z` (`vX.Y.Z-SNAPSHOT` between releases), and
every runtime log opens with `log.open path=... version=X.Y.Z`.

The bitmap and browser ANSI servers reuse this backend. Keep game mechanics and
input-command semantics here when they should behave identically across views.
Concrete browser DOM/canvas code belongs in web runtime modules.

The runtime boots directly from the compiled bundle, renders static screens and
wilderness frames, and runs the wilderness, town, service, battle, dungeon, and
castle flows from compiled tables. Long acceptance routes are generated from
test-only replay plans and fed to the installed launcher through `--script`; the
shipped launcher has no replay-planning mode.

## Terminal Primitives

The `rylween.terminal` package holds the terminal-shaped IO that the direct
terminal runtime and the browser ANSI runtime share: the `AnsiTerminal` I/O
contract, with a JLine raw-mode implementation for play and a plain stdout one
for scripted runs, ANSI escape constants (`AnsiCodes`), and raw key and line
decoding (`AnsiInput`, which maps arrow keys to the keypad movement digits).
Keep it small: game rules, screen layout decisions, and browser rendering do
not belong there.

## Save File

The shared world save is written by `SaveFile` as a text key/value file. The
line and continuation escaping helpers live inside the same save adapter so the
persistence syntax is kept away from terminal and web protocol code. Local and
web launchers usually store it at:

```text
<data-dir>/saves/world.rsave
```

The file contains game state only:

- save metadata and compiled bundle hash
- shared world clock and generated NPC/location placements
- completed shared missions
- persisted Hall-of-Heroes entries
- one `user.N.*` party record per saved player

Each user party record stores current location/mode, party resources, flags,
milestones, journal, inventory, member stats/equipment/XP, and active
dungeon/castle continuation state.

A save from an older bundle still loads; the runtime logs the bundle hash
change.

Writes are serialized across threads and processes through a `world.rsave.lock`
sidecar file and replace the save atomically, so a failed write leaves the
previous save intact. Run without `--save`, the bare `rylween` launcher writes
`build/saves/runtime.rsave` relative to its working directory.

Web account credentials are deliberately not stored in the save file. They live
under `<data-dir>/auth` and are documented in
[`web-common`](../web-common/README.md).

## Checks And Test Logs

`./gradlew :runtime:check --console=plain` runs the unit tests, ktlint, and the
scripted `checkRuntime*` tasks. Each scripted check runs the installed `rylween`
launcher against `build/compiled/rylween-bundle.json` on the toolchain JDK and
keeps two files under `runtimes/runtime/build/logs/runtime/`:

- `<task>.log`, the runtime's forensic log;
- `<task>.out`, everything the launcher printed, ANSI screens included.

A passing check prints nothing; a failing one names `<task>.out` and prints its
last 4,000 characters.

Runtime tests keep their files. Every `@TempDir` lands in
`runtimes/runtime/build/test-logs/<TestClass>/<testMethod>/` (see
`TestLogDirFactory`; a second directory gets a `-2` suffix) and is kept after
the run, so logs, saves, and autopilot reports can be read afterwards. Each real
test run empties `build/test-logs` first; a result taken from the build cache
restores it. CI uploads both directories as the `test-logs` artifact.
`-Prylween.autopilot.seeds=1,2,3` makes `AutopilotFullGameTest` play several
seeds.
