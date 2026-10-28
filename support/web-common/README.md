# Rylween Web Common

`web-common` contains browser-server plumbing shared by the bitmap and ANSI web
runtimes. Server-specific modules keep their renderer, launcher policy, save
loading, and game-loop wiring local, but reuse this module for HTTP parsing,
WebSocket handshakes, authentication, session cookies, shared auth pages, and
status responses.

## Authentication Flow

Both web servers expose the same account flow:

- `GET /` redirects to `/login` when authentication is required, or to `/play`
  when the request already has a valid session.
- `GET /login` and `GET /register` render the shared login/register page from
  `rylween.webcommon.auth.webAuthPageHtml`.
- `POST /api/register` creates an account and starts a session.
- `POST /api/login` verifies an existing account and starts a session.
- `POST /api/logout` clears the session cookie.
- `GET /api/auth/availability?name=&email=` reports whether an adventurer name
  and email are still free; the register form checks as the player types.
- `GET /api/status` returns `WebServerStatus` JSON for the top-bar status
  popover in each web UI, or 401 without a session.
- `GET /healthz` returns `ok` and needs no session.
- `/ws/play` is the game WebSocket; it needs a session, and a second connection
  for an account that is already playing is turned away.
- `GET /auth.css` serves the shared auth page stylesheet.

`/login` and `/register` redirect to `/play` when the request already has a
session or the server runs with a development bypass user. Registration rejects
the sysop's name, names that already have a party in `world.rsave`, and
passwords shorter than 8 characters.

The auth page is renderer-neutral. The bitmap server passes the title-rider
image as its background; the ANSI web server can use the same form without
coupling auth to either renderer.

## Bootstrap Accounts

Both web servers can create or verify a startup account before accepting
connections:

```sh
./bin/run-bitmap-server.sh --account Strong --email strong@example.test --password 'change-this-password'
./bin/run-server.sh --account Strong --email strong@example.test --password 'change-this-password'
```

Equivalent environment variables are:

```text
RYLWEEN_AUTH_ACCOUNT
RYLWEEN_AUTH_EMAIL
RYLWEEN_AUTH_PASSWORD
```

Bootstrap is idempotent:

- if the account does not exist, it is created;
- if it already exists, the supplied password must verify;
- if the account exists with another password, startup fails.

Players cannot register the sysop account (`RYLWEEN_ADMIN_ACCOUNT`, default
`admin`), which gets the game's secret wilderness keys. The server creates it at
startup, after any bootstrap account, when it is missing: with
`RYLWEEN_ADMIN_PASSWORD`, or else with a generated password saved in
`auth/admin-password.txt` under the data directory, readable only by the
server's user. The bootstrap rules above do not apply to it: an existing sysop
account is left alone, and `RYLWEEN_ADMIN_PASSWORD` only applies when the
account is created. A bootstrap account with the sysop's name becomes the sysop
account. `RYLWEEN_VICTIM_ACCOUNT` names an account the sysop gods hunt down every
101 moves; unset means nobody.

The bootstrap path does not create a logged-in browser session. Users still log
in through the normal form unless the runtime is explicitly using a local
development bypass.

Do not use command-line passwords in production. Shell history, process tables,
container metadata, and deployment logs can expose them. Use a hosting secret or
secret manager to set `RYLWEEN_AUTH_PASSWORD` and `RYLWEEN_ADMIN_PASSWORD`.

## Data Directory Layout

All web runtimes are configured around one data directory. The `bin/` scripts
default to `~/.rylween/data`, `./deploy.sh --launcher` to `~/.rylween/deploy`,
and containers (including `deploy/compose.yaml`) to `/data`. Run bare, the
`rylween-bitmap-web` launcher uses `build/rylween-data`.

```text
<data-dir>/
  saves/
    world.rsave
  auth/
    admin-password.txt        (only when the server generated the sysop password)
    users/
      by-slug/<slug>.properties
      email-index.properties
  logs/
    web-server.log            (ANSI web server)
    <slug>-<timestamp>.log    (ANSI web sessions)
    runtime-bitmap-web/
      server.log
      <slug>-<timestamp>.log
```

`saves/world.rsave` is game state. It is owned by `:runtime`.

`auth/users` is account state. It is owned by `:web-common`.

`logs` is operational history. It is owned by the concrete server/runtime that
creates each log file.

`WebSessionStore` is not persisted. Browser sessions live only in memory and
are lost when a server process restarts.

## Game State

The shared save file is a text key/value file encoded by `SaveFile` in
`:runtime`. It is intentionally game data, not web auth data.

Top-level rows include:

- `format`, `version`, and `saveModel`
- `bundleHash`, which records the compiled game that wrote the save; a mismatch
  is logged as `save.bundle_changed`, and the save still loads as long as the
  content it names still exists
- shared realm clock fields such as day/hour/minute
- shared world placements such as NPC and location positions
- shared completed mission ids
- `heroes.count` and `hero.N.*` Hall-of-Heroes entries
- `users.count` plus `user.N.*` records for saved parties

Each `user.N.*` record contains one saved player party:

- user id
- current map/mode/town/terrain
- party name, coordinates, facing, resources, flags, milestones, and journal
- inventory slots
- party member names, attributes, HP/MP, equipment, XP, class/race/gender, and
  dead/alive state
- current dungeon/castle continuation when applicable

Score and verbose score screens are derived from saved party records. The save
does not duplicate score totals as a separate table. Hall-of-Heroes entries are
persisted because they are shared world history: a party that completes the win
condition remains listed even when another user is active.

The save is still not a credential store. Do not put password hashes or session
tokens into `world.rsave`.

## Account State

`WebUserStore` persists users under the configured data directory:

```text
<data-dir>/auth/
  users/
    by-slug/<slug>.properties
    email-index.properties
```

User records store:

- stable account slug
- display name
- normalized email address
- `emailConfirmed=true`
- PBKDF2 password metadata and hash
- creation/update timestamps

`email-index.properties` maps normalized email addresses to account slugs so
users can log in with either email or slug without scanning every user file.

Email confirmation is currently skipped. The persisted `emailConfirmed` flag is
present so a later pluggable mail-confirmation flow can be added without
reshaping saved user records.

`WebSessionStore` is in-memory only. Sessions use a random token in the
`rylween_session` cookie with `HttpOnly`, `SameSite=Lax`, `Path=/`, and a
12-hour max age. Restarting a web server logs users out but leaves accounts and
saved games intact.

## Log State

Logs are text files written through the runtime log framework, one timestamped
event per line, each file opening with a `log.open ... version=<release>` line.
Each server start rewrites its server log, so copy it first if you need the
previous run; each session writes a new timestamped file. Both servers also echo
server-log lines to standard output.

Server logs record:

- HTTP route requests/responses
- login/register/logout attempts and results
- bootstrap account creation or verification
- sysop account creation, with the password file's path when one was generated
- websocket open/close/reject events and every input message
- connected user/session information

Per-session runtime logs record:

- runtime boot/load/save decisions
- game input tokens and text/int prompt answers
- RNG draws
- battle, town, dungeon, castle, wilderness, and script events

Logs are useful for operations and debugging, but they are not authoritative
game state. They may contain user names, account emails, input values, and
gameplay activity. Do not include them in routine backups unless you explicitly
want audit history.

## Why Auth Is Not In The Save File

The shared world save remains game state. Web account records stay in
`<data-dir>/auth` because password hashes, account recovery, email
confirmation, and future security migrations evolve differently from party and
world data.

Putting password hashes directly in `world.rsave` would make every save copy,
bug-report attachment, and save-format migration security-sensitive. It would
also make the core runtime save format own web-server policy. Instead, use the
backup helper from the repo root when you want one portable artifact:

```sh
./bin/backup-web-data.sh --data ~/.rylween/data --output rylween-backup.tar.gz
./bin/backup-web-data.sh --include-logs --output rylween-backup-with-logs.tar.gz
```

The default archive includes `saves/` and `auth/users/`. Logs are optional
because they can grow and may contain operational or gameplay activity. The
generated `auth/admin-password.txt` is not archived; the sysop account itself
survives a restore.

## Passwords And Cookies

Account passwords are never stored directly. `WebUserStore` hashes them with
`PBKDF2WithHmacSHA256`, a random salt, and constant-time verification through
`MessageDigest.isEqual`. The one exception is the generated sysop password in
`auth/admin-password.txt`, readable only by the server's user, which exists only
when `RYLWEEN_ADMIN_PASSWORD` was unset. The server never reads it back, so you
can delete it once you have recorded the password.

Cookies are:

- `rylween_session`: the session token; `HttpOnly`, `SameSite=Lax`, `Path=/`,
  12-hour max age.
- `rylween_login`: the account email from the last login or registration, used
  to prefill the login form; `SameSite=Lax`, `Path=/`, one-year max age, not
  `HttpOnly`. Logging out leaves it in place.

Both get `Secure` when secure cookies are enabled. Both production images
default `RYLWEEN_AUTH_SECURE_COOKIES` to `true`; `deploy/compose.yaml` sets it to
`false` unless told otherwise, because it serves plain HTTP by default.

For HTTPS deployments, start the server with secure cookies enabled:

```sh
RYLWEEN_AUTH_SECURE_COOKIES=true ./bin/run-bitmap-server.sh
./bin/run-bitmap-server.sh --secure-cookies
```

Local plain-HTTP development must not use `Secure` cookies because browsers will
not send them back over `http://`.

## Runtime Policy

`web-common` does not decide which game state to load. Each web runtime maps an
authenticated account to its own runtime policy:

- Normal browser sessions authenticate through the shared account store.
- Existing local shortcuts keep working through a dev bypass user:
  - bitmap: `bin/run-bitmap-server.sh --name Strong` and `--name StrongEndgame`
    pass the selected name as `--user`
  - ANSI web: `bin/run-server.sh --name Strong` and `--name StrongEndgame`
    forward the selected name as `RYLWEEN_AUTH_DEV_USER`
- The web runtime then decides whether to show the title screen or resume a
  saved user party.
- On quit, the bitmap server returns the player to the title screen and keeps
  the WebSocket open while the title screen is enabled (the default;
  `--no-title` or `RYLWEEN_BITMAP_NO_TITLE=true` turns it off, and then quitting
  ends the session). The ANSI web server closes the connection when the player
  quits.

This keeps account/session handling common while preserving separate renderer
and save-loading behavior in each server.

## Logging

Authentication and session events are logged by the concrete server using the
runtime log framework. `web-common` validates and stores credentials, but it
does not own a log sink. Server modules should log:

- login/register/logout attempts and results
- bootstrap account creation/verification
- session open/close
- rejected unauthenticated websocket requests
- duplicate active-user connections
- game input tokens and text/int prompt answers

Never log plaintext passwords, session tokens, password hashes, or full cookie
headers.

## Status Popover

`WebServerStatus` is the shared response model for the clickable server badge in
the browser UI. It currently reports:

- server type, `ansi` or `bitmap` (`serverType`)
- release version (`version`), which the bitmap UI shows in its title bar and
  popover
- whether authentication is required (`authRequired`)
- the requesting account's display name (`currentUser`)
- number of connected users (`connectedUsers`)
- active user slugs (`activeUsers`)

The popover is UI-specific, but the response shape belongs here so both web
runtimes report the same operational information.

## Production Hardening

The current implementation is suitable for local development and small
self-hosted testing. To turn it into a commercial-strength HTTPS web app, do the
following before exposing it publicly.

1. Put the Kotlin server behind a real reverse proxy such as nginx, Caddy,
   HAProxy, or a managed cloud load balancer. Bind Rylween itself to
   `127.0.0.1` or a private container network.

2. Terminate TLS at the proxy with publicly trusted certificates. Use Let's
   Encrypt, your cloud certificate manager, or another automated certificate
   issuer. Use the Mozilla SSL Configuration Generator as the baseline for TLS
   versions, ciphers, and certificate settings:
   <https://ssl-config.mozilla.org/>.

3. Redirect HTTP to HTTPS at the proxy. Once HTTPS is stable, add HSTS at the
   proxy and keep it there rather than inside the game server. OWASP's TLS and
   HSTS guidance are the baseline references:
   <https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html>
   and
   <https://cheatsheetseries.owasp.org/cheatsheets/HTTP_Strict_Transport_Security_Cheat_Sheet.html>.

4. Start Rylween with `RYLWEEN_AUTH_SECURE_COOKIES=true` or `--secure-cookies`
   so the session cookie includes the `Secure` attribute.

5. Move bootstrap credentials, including `RYLWEEN_ADMIN_PASSWORD`, to deployment
   secrets. Do not pass production
   passwords on shell command lines. Prefer an orchestrator secret, systemd
   environment file with locked permissions, Docker/Kubernetes secret, or cloud
   secret manager.

6. Add CSRF protection for `POST /api/login`, `POST /api/register`, and
   `POST /api/logout`. `SameSite=Lax` is useful, but commercial deployments
   should use explicit CSRF tokens for state-changing forms. The routes do not
   check the HTTP method today, so `GET /api/logout` also logs out.

7. Add login throttling and abuse controls: per-IP and per-account rate limits,
   temporary lockout/backoff, and alerting for repeated failures. OWASP's
   authentication guidance calls out protections against automated attacks:
   <https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html>.

8. Replace skipped email confirmation with a pluggable mail provider. Store
   short-lived signed confirmation tokens server-side or hashed at rest. Add
   password reset and change-password flows with re-authentication for sensitive
   changes.

9. Consider modern password hashing upgrades. PBKDF2-HMAC-SHA256 is portable
   and acceptable for this local implementation, but Argon2id or bcrypt with a
   deployment-tuned work factor is usually preferable for a public service. Keep
   per-user hash metadata so accounts can migrate on next login.

10. Add MFA support for administrative or paid accounts. TOTP or passkeys are
    reasonable first targets.

11. Persist sessions in a real store if you need horizontal scaling or planned
    restarts without logging everyone out. Keep session tokens random, rotate on
    login, and expire idle/old sessions. OWASP's session guidance is here:
    <https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html>.

12. Move account storage to a database when you need concurrent writes, admin
    tooling, account recovery, payments, or analytics. Keep `world.rsave` as the
    game-world artifact; back it up together with the auth database, not inside
    the save file.

13. Add operational monitoring: structured logs, metrics for active users and
    websocket sessions, failed login counters, disk-space alerts, backup
    verification, and uptime checks.

14. Run with least privilege. The server process should only be able to read its
    bundle/assets and read/write the configured data directory. File permissions
    on `<data-dir>/auth` should restrict access to the service account.

15. Add dependency and image scanning in CI. Keep the JDK, base image, and
    Gradle dependencies patched.

16. Add browser security headers at the reverse proxy: `Content-Security-Policy`,
    `X-Content-Type-Options`, `Referrer-Policy`, and frame-embedding policy.
    Tune CSP once the bitmap and ANSI web UIs have stable asset needs.
