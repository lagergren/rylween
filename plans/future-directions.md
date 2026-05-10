# Rylween Future Directions

A scoping document for things Rylween could become *after* the §22-§24 port-plan
work lands. Nothing here is a commitment, and nothing here is on the critical
path. The point is to record the shape of the ideas so that current decisions
keep the door open for them — and so that the unfinished "version 3"
multi-party scoping work isn't lost.

The original code is a single-process, single-party Xenolink BBS door. The
current port keeps that shape: one binary, one party, one runtime save slot by
default (`build/saves/runtime.rsave`), one writer. Authored game content and
ANSI resources are packaged through `:game` resources; there is no mutable root
`data/` world directory in the active checkout. Everything below describes what
would have to change to make the runtime more than that, and in what order.

## 1. Single-world, multi-party shared state

The smallest interesting step beyond today's shape is one realm with several
parties, each unaware of the others *as actors* but reading the same realized
world. Same NPCs, same towns, same prices, same mission state, same dungeon
layout. Two parties walking through Theorin see the same blacksmith and the
same rumours; if party A finishes the smuggler quest, party B walks in and the
smuggler is gone.

Today this doesn't work. The active runtime save format is deliberately
single-party: it stores one party, one location/mode, one RNG stream, and the
continuation state needed to resume that party. No shared world-state store or
party identifier exists. Two runtimes pointed at the same save would race on
every write and silently clobber one another's state.

The lightweight shape for v0 would be a split between *world state* and *party
state*. World state is what every party sees: NPC roster (post-realization),
town inventories, mission flags, world clock. Party state is what one party
carries: roster, inventory, location, journal, per-party mission progress.
Concretely:

  - `<saves-dir>/realms/<realm-id>/world.json5` — the realized, mutable world.
    One writer at a time, guarded by a file lock (`flock(LOCK_EX)` is enough
    for a single-host MUD).
  - `<saves-dir>/realms/<realm-id>/parties/<party-id>.json5` — one file per party.
    Owned exclusively by that party; never touched by anyone else.

A party's session reads world.json5 + its own party file at startup, mutates
both in memory, and on save writes the party file unconditionally and the
world file *only* with a lock + read-modify-write under the lock. The merge
strategy for world.json5 stays primitive: last-writer-wins per top-level key.
We don't need CRDTs — most world mutations are coarse (NPC dies, item bought,
quest cleared) and infrequent. If a real conflict shows up later, that's the
moment to add per-key versioning.

The schema work today (§15 realm persistence, §22 DSL unification) should
already keep this in mind. When adding a new persisted field, the question is
*per-party or per-world?* — and the answer should be a one-line comment in the
JSON5 next to the field. Inventory is per-party. Whether the smuggler is dead
is per-world. Whether *this* party knows the smuggler is dead is per-party.
Most fields fall out cleanly once you ask the question.

Party id is a string; assignment is at first-run and stored in the party file.
No global registry needed at v0 — the filename is the registry.

## 2. Inter-party interactions

Once parties share a world, the interesting layer is letting them notice each
other. Five wedges, in roughly increasing implementation cost:

**Bulletin board.** A persistent text store anchored to existing UI surfaces
(inn, tavern, lodge). Anyone posts, anyone reads, content survives across
sessions. Cross-party by definition. This is the cheapest cross-party feature
because it needs no live coordination — it's a file with append semantics.
Detailed in §3.

**Direct messages between parties.** A step up from the bulletin board: posts
addressed to a specific party id, delivered next time that party visits a town.
Same storage shape, just with a recipient field. The interesting design choice
is *delivery surface* — does the message pop up on town entry, or does the
party have to visit the post office? The latter is more in keeping with the
existing town-centric UI rhythm.

**Tracing and sighting.** When party A camps or passes through a wilderness
hex, leave a faint "tracks" record in the world file at that hex with a
timestamp. Party B walking through later sees `Recent tracks lead north.` and
can follow them — at the cost of a turn or a check. Tracks decay (drop after
N world-clock days). This is the first feature that makes the wilderness feel
*populated* rather than static.

**Ambush.** A hook already exists in the authored game rules: camped parties
have an ambush chance and the rest text includes the `Ambush!` string. Today
this fires monster ambushes at camp. The generalization is: when two parties
camp within K hexes of each other on the same world-night, roll the same ambush
chance and let one party surprise the other. The combat surface is the harder
part — see below.

**Combat vs. another party.** The existing battle loop in `battle.c` is
monster-shaped: opponent slots come from the monster table, AI is the
nasty-magic / spell-pick logic that §23 is currently lifting to data. A party
opponent would slot into the same shape if the opposing party's roster can be
projected into the battle struct. That projection is the work: an enemy party
is six PCs, each with full inventory and spell list, not a row from
the compiled monster table. The right time to attempt this is after the battle
loop is fully data-driven on the monster side — at that point adding a second
source of opponents (read from another party-file) is a contained change.

None of these need a live network. All of them are *eventually-consistent*
through the shared filesystem. That's the design property we're protecting.

## 3. Bulletin board as a first concrete shippable

The smallest cross-party feature worth building first. Scope:

  - One file per realm: `<saves-dir>/realms/<realm-id>/bulletin.json5`.
  - Append-only at the API level (we never edit or delete an entry from
    inside the game; a sysop can hand-edit the file).
  - Anchored to the inn (or a new "notice board" location in the town menu).
    The existing towne.c surface for "talk to the innkeeper" is the right
    insertion point — same screen layout, same text-window dimensions.
  - Each entry carries: poster party id, poster party name, world-clock
    timestamp, body (capped at e.g. 240 chars), optional recipient party id
    for the DM use case.
  - Reading: a paginated list, newest first, scoped to "public posts +
    posts addressed to me". Other parties' DMs are not visible, but the
    storage isn't encrypted — this is a MUD with friends, not a security
    boundary.
  - Retention: keep the last N entries (say 200) per realm. On post, if
    over the cap, drop the oldest public entry. DMs persist until read by
    the recipient, then drop on next compaction.
  - Length cap: 240 chars is the right starting point — fits the existing
    text window, discourages long-form posts that the UI can't render
    well anyway.

Identity at v0 is "whatever is in the party file" — no authentication. A bad
actor could edit their party file to spoof a name; that's fine. The point is to
get the surface right and learn from using it. If/when authentication matters
(§4), the bulletin board becomes the first feature that gets a real identity
layer underneath it.

The bulletin board lays the foundation for direct messages, tracing/sighting
metadata, and any other "leave a note for someone, somewhere" mechanic, without
needing a server. It is the right first step.

## 4. Server / MUD mode

Rylween today is a TTY app — one process, one stdin, one stdout, one user. The
MUD shape is "many users, each on a TCP connection, talking to one rylween
session each, all sharing the realm files in §1." The user has flagged this as
a real possibility ("we may run rylween as a parallel MUD on a server or
server farm"), and the architecture falls out cleanly because the original
Xenolink build *was* already a multi-user door — every BBS door is. The
single-user TTY shape is the special case, not the general one.

Concretely:

  - One rylween binary, two run modes: `rylween` (today's interactive TTY)
    and `rylween --listen :2323` (TCP server).
  - In `--listen` mode, the parent process binds the port and `accept(2)`s.
    Each connection forks (or threads — but the codebase is globals-heavy,
    so fork is simpler) and the child runs the existing game loop with the
    socket as stdin/stdout. The TTY-vs-Xenolink abstraction described in
    port-plan §4 is exactly this seam — the I/O backend `io->{write,getch,
    getline}` swaps from the local termios backend to a socket backend, and
    nothing in game code changes.
  - World access goes through the §1 file-locking discipline. Each child
    process is a normal §1 client of the realm files. No central
    coordinator needed at v0 — the filesystem *is* the coordinator.
  - The §24 logging plan's TCP-listener pattern (`RYL_LOG=tcp:host:port`,
    accept connections, fan-out write) is the exact mechanism we'd reuse
    for this, except the data flow is bidirectional. The accept loop, the
    per-client buffer, the carrier_ok story — all of that is already
    sketched in §24, just in the read-only direction. Going bidirectional
    is a per-connection pty-style read pump on top.

Authentication is the genuinely hard part. v0 is a flat
`htpasswd`-style file: `<saves-dir>/realms/<realm-id>/users.txt`, one
`username:bcrypt-hash:party-id` per line, login prompt at session start. v1 is
something nicer (SSH-key-based via `authorized_keys` in front of an
`authorized_command`; or a real ACL file with capability flags). The v0 choice
is forced by "we want this on a server with friends connecting from anywhere"
— without authentication, you can't safely expose the port at all.

A note on telnet vs. SSH: telnet is the natural fit (the engine already speaks
ANSI, telnet passes ANSI through, BBS doors all spoke this). SSH adds
encryption and host-key trust for free, at the cost of needing to live behind
sshd's `ForceCommand` or a per-user `authorized_keys` `command=` directive.
Both work; ship telnet first because it's a single binary with a single port,
SSH second when someone asks.

The single-binary, swap-stdio-for-socket discipline is the key constraint.
Every step away from it (a separate world coordinator daemon, a database, a
web frontend) is justifiable later but multiplies the operational surface.
Keep the door open; don't walk through it yet.

### Concrete non-JVM product target

The previously scoped Rust ANSI TCP server/runtime has been deferred and
removed from the active checkout. The idea still belongs here, but it should
return as a new plan only when server/MUD work becomes the next priority.

This keeps the C code in its current role as a compact loader/VM boundary
probe, while giving the server/MUD idea a real product surface: a static
non-JVM binary that reads the J1 JSON bundle, executes scripts through the J2
compiled VM, owns authentication and realm/party file locking, and passes the
same conformance suites as the Kotlin host before it is called playable.

Rust is the right first product language because the server target needs
long-running TCP sessions, safe parsing, structured diagnostics, concurrency,
and file-locking discipline. A C terminal clone would prove less of the
future-directions architecture and would be too easy to confuse with the
existing native probe.

## 5. Persistent scoreboard / hall of heroes

The current Kotlin runtime computes the score and Hall-of-Heroes screens
dynamically from runtime state on every render — `scoreReport()` /
`verboseScoreReport()` / `hallOfHeroesReport()` are DSL screen builders,
not file readers. That's correct for the *active* party view. The piece
that's missing is a *persistent* end-of-game record: when a party wins
(or all members die), an entry should be appended to a hall-of-heroes
file the title menu can read back.

The 1993 Amiga distribution did this with `Doors:Rylween/Score.TXT` —
plaintext, append-only, one line per finished party. The original file list is
preserved in `scratch/legacy-archive.zip` under `legacy/distro-amiga/Files.TXT`.
The Kotlin port briefly inherited two empty score stubs from the legacy data
layout, but nothing wrote or read them, so they were dropped.

The right shape:

  - The hall-of-heroes file lives next to save files, not in game resources.
    Packaged game resources are read-only source; the scoreboard is mutable
    runtime output. Concrete path: `<saves-dir>/heroes.json5`, where
    `<saves-dir>` is the same `--saves-dir` / `RYL_SAVES_DIR` directory
    the launcher already understands. Default `build/saves/heroes.json5`.

  - Append on session-end: when a party is terminated (won, wiped, or
    user-terminated via the title-menu T command), the launcher writes
    one structured entry — party name, member roster + classes + final
    levels, days elapsed, end cause (won/dead/terminated), realm seed.
    Use JSON5 like the save format so it stays readable and lock-free
    appendable.

  - Read on title-menu Scores / Heroes: the existing
    `StaticScreens.score` / `StaticScreens.heroes` runtime-content screens
    take a list-of-entries argument from the launcher and render it.
    The DSL builders stay pure (no I/O); the launcher is the boundary
    that reads `heroes.json5` and passes the parsed list in.

  - `--reset-all` already wipes the saves directory, which would catch
    the heroes file too. `--reset` should leave the heroes file alone
    (it's not party-specific). One-line tweak in the existing reset
    paths.

  - Multi-realm story: when §1 lands, each realm gets its own heroes
    file under `<saves-dir>/<realm-id>/heroes.json5`. Until then, one
    file is fine.

Cost: small. ~150 lines for the writer + reader + serialization, plus
wiring into the three end-of-game paths and the title menu. Not
on the §22-§24 critical path; queue after the J2 compiled-VM work
unless a registered user asks.

## 6. What this means for the port plan today

None of §1-§5 is on the §22-§24 critical path. The port-plan work in flight is
about getting today's single-party, single-binary game fully data-driven,
testable, and modern-Unix-clean. Multi-party is what becomes *possible* when
that work finishes; it's not what unblocks it.

The two practical implications for current work:

  - When adding new persistence (rest, lodge, journal, post-mission state),
    decide explicitly whether the field is per-party or per-world, and put
    the answer in the JSON5 schema comment. Today the question is moot
    because there's only one of each. Tomorrow it's the difference between
    a clean split and a migration nightmare.
  - Keep `current-world.json5` schema flexible enough that adding a
    `party_id` discriminator at the top level later is a one-pass
    transform, not a rewrite. The §15 realm-persistence design already
    treats the file as a tree of named sections — that's the right shape.
    The §22 DSL unification work is also helpful here because it pushes
    behavior into data, and data migrates cleaner than code.

If a §22 or §23 design choice has a fork between "one shape that's cleaner
today" and "another shape that's cleaner if there are multiple parties later",
prefer the multi-party-friendly fork only when the cost is small. Don't bend
current designs out of shape for hypothetical futures — but when the shapes
are equal-cost, pick the one that doesn't paint us into a corner.

## 7. Open questions

A short list of things to resolve before any of §1-§5 lands. These are not
blockers for current work; they're questions to bring into the design
conversation when (if) the multi-party path opens.

**Identity.** How does a party prove who it is across sessions? v0 answer is
the party-file path plus an unenforced name. v1 answer is some kind of
per-party secret (a token in the party file, checked against the realm's
users.txt). v2 answer is real auth (§4). The bulletin board (§3) is the
forcing function — until then the question is academic.

**Time.** Does a server tick advance for all parties together, or does each
party see its own clock? The original game's clock advances on rest, travel,
and certain in-town actions — it's party-driven, not wall-clock-driven.
Easiest answer: each party has its own clock, world clock is the max of all
party clocks, and any world-clock-gated event (NPC schedules, tracking decay)
reads the world clock. Open question: what happens when party A's clock is
day 50 and party B's is day 20 and they both want to talk to the same NPC?
Probably "the NPC is whatever the world clock says it is" — i.e. party B
might find the NPC dead from a quest party A finished in their future.
Surprising but defensible.

**Persistence.** File-per-realm with `flock` is the default. Single global
file with a lock works too and is simpler at very small scale, but doesn't
extend to "many realms on one host." File-per-realm is the right starting
point. SQLite is the obvious next step *if* contention or transactional needs
show up — neither is likely at MUD-with-friends scale.

**Cheating.** Probably not worth caring about. This is a MUD with friends, not
a leaderboard ladder. Treat the threat model as "an unmodified rylween binary,
operated in good faith." If someone hand-edits their party file to add gold,
that's a social problem, not an engineering problem. Designing for adversarial
players would multiply the engineering cost without adding much value at this
scale; punt until there's evidence we need it.

None of these have to be answered now. The point of the list is to mark the
load-bearing decisions so that when one of them comes up in implementation,
we recognize it as load-bearing.
