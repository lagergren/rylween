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
- The installed `rylween` launcher used by `bin/run.sh` and tests.

The bitmap and browser ANSI servers reuse this backend. Keep game mechanics and
input-command semantics here when they should behave identically across views.
Concrete browser DOM/canvas code belongs in web runtime modules.

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

Web account credentials are deliberately not stored in the save file. They live
under `<data-dir>/auth` and are documented in
[`web-common`](../../support/web-common/README.md).
