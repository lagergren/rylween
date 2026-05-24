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
- `GET /api/status` returns `WebServerStatus` JSON for the top-bar status
  popover in each web UI.

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

The bootstrap path does not create a logged-in browser session. Users still log
in through the normal form unless the runtime is explicitly using a local
development bypass.

Do not use command-line passwords in production. Shell history, process tables,
container metadata, and deployment logs can expose them. Use a hosting secret or
secret manager to set `RYLWEEN_AUTH_PASSWORD`.

## Data Directory Layout

All web runtimes are configured around one data directory. Local scripts default
to `~/.rylween/data`; containers default to `/data`.

```text
<data-dir>/
  saves/
    world.rsave
  auth/
    users/
      by-slug/<slug>.properties
      email-index.properties
  logs/
    web-server.log
    runtimes/runtime-bitmap-web/
      server.log
      <user>-<timestamp>.log
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
- `bundleHash`, which prevents loading a save against the wrong compiled game
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

Logs are append-only text files written through the runtime log framework.

Server logs record:

- HTTP route requests/responses
- login/register/logout attempts and results
- bootstrap account creation or verification
- websocket open/close/reject events
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
because they can grow and may contain operational or gameplay activity.

## Passwords And Cookies

Passwords are never stored directly. `WebUserStore` hashes them with
`PBKDF2WithHmacSHA256`, a random salt, and constant-time verification through
`MessageDigest.isEqual`.

Cookies are:

- `HttpOnly`
- `SameSite=Lax`
- `Path=/`
- 12-hour max age

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

- server type, such as `bitmap` or `ansi`
- whether authentication is required
- number of connected users
- active user slugs

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

5. Move bootstrap credentials to deployment secrets. Do not pass production
   passwords on shell command lines. Prefer an orchestrator secret, systemd
   environment file with locked permissions, Docker/Kubernetes secret, or cloud
   secret manager.

6. Add CSRF protection for `POST /api/login`, `POST /api/register`, and
   `POST /api/logout`. `SameSite=Lax` is useful, but commercial deployments
   should use explicit CSRF tokens for state-changing forms.

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
