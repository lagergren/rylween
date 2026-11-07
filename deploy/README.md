# Running Rylween

`./rylween`, in the repository root, runs the game every way it runs: the ANSI
game in a terminal, and either game as a server for browsers, on this machine,
your local network, or the internet over HTTPS. It is a link to
`deploy/rylween`, which lives here with the compose files and images it uses.

## The `rylween` Command

| Command | What it does |
|---|---|
| `./rylween` | Plays the ANSI game in this terminal. |
| `./rylween serve` | Hosts the bitmap game for browsers on this machine, at `http://127.0.0.1:8081/`, in a container built from the checkout. |
| `./rylween serve --lan` | The same, for every machine on your local network. |
| `./rylween serve --https` | The released bitmap game on the internet, behind Caddy and HTTPS; see [On The Internet, Over HTTPS](#on-the-internet-over-https). |
| `./rylween stop` | Stops whatever `serve` started. |
| `./rylween backup` | Archives saves and accounts; see [Backups](#backups). |
| `./rylween help` | Lists every option. |

`serve` takes one choice from each row; with none given it uses the defaults:

| Choice | Options | Default |
|---|---|---|
| The game | `--bitmap`, the painted version, or `--ansi`, the ANSI version in the browser | `--bitmap` |
| Where it runs | `--docker`, in a container (Docker or Podman), or `--no-docker`, with the launcher built from the checkout, which needs a JDK | `--docker` |
| The protocol | `--http`, or `--https` for the internet | `--http` |
| Who can connect | `--local`, this machine only; `--lan`, the local network, on this machine's LAN address; or `--host ADDRESS` | `--local` |
| The port | `--port PORT` | 8081 for the bitmap game, 8080 for the ANSI game |

`--local` is the default so that nothing is reachable from other machines
unless you ask for it with `--lan`. (The `./deploy.sh` this command replaces
published on the LAN by default.) `serve` returns once the server answers and
prints its address; `--foreground` stays attached to the server's output
instead, and Ctrl-C stops it. `--https` works with the bitmap game in
containers only, for now.

Without a checkout there is no `rylween` command, and none is needed: the
[README](../README.md#with-only-docker-or-podman) runs the released game with
one `docker run`, each release's terminal zip plays with its `bin/rylween`
(this same script, which there only plays), and an HTTPS server needs only
[`compose.https.yaml`](#4-get-the-compose-file).

## What Is Here

- **`rylween`**, the command above.
- **`compose.yaml`** builds and runs the game for `serve` over plain HTTP: the
  bitmap game, and the ANSI game in its `ansi` profile. Its header lists every
  setting.
- **`compose.https.yaml`** runs the released bitmap image behind Caddy for
  `serve --https`. Each GitHub release attaches a copy pinned to that release, so
  an HTTPS server needs no checkout.
- **`.env.example`** shows the settings both compose files read from a `.env`
  file here.
- **`docker/Dockerfile.bitmap`** builds the bitmap game, serving port 8081.
  Releases publish it as `ghcr.io/lagergren/rylween:X.Y.Z` and `:latest`, for
  linux/amd64 and linux/arm64. Its builder stage runs on the build machine's
  own platform, so an image for another platform needs no emulation.
- **`docker/Dockerfile.ansi`** builds the ANSI game for the browser, serving
  port 8080.

Both images serve plain HTTP inside the container and keep saves, accounts, and
logs under their `/data` volume. They mark the login cookie `Secure`, for
HTTPS (`RYLWEEN_AUTH_SECURE_COOKIES=true`), so browsers keep it only over HTTPS;
the plain HTTP setups here set it to `false`. Once the bitmap server accepts
connections it prints the link to open, taken from `RYLWEEN_PUBLIC_URL`, or
`localhost` and its port. Build an image by hand from the repository root, which
Gradle, the sources, and the root `.dockerignore` need as the build context:

```sh
docker build -f deploy/docker/Dockerfile.bitmap -t rylween-bitmap .
```

## Playing In A Terminal

```sh
./rylween
./rylween --name Strong
./rylween --name StrongEndgame
```

The first run builds the game, `build/compiled/rylween-bundle.json` and
`:runtime:installRuntime`, and `--build` rebuilds it; it needs JDK 23 or newer,
and Gradle downloads the JDK 25 toolchain the modules build with. The game runs
outside Gradle, from a private copy of the install, so Gradle's output does not
share the game screen and a rebuild cannot pull jars from under a running game.
The shared save is `~/.rylween/data/saves/world.rsave` and the log
`~/.rylween/data/logs/runtime/latest.log`, unless `RYLWEEN_DATA_DIR`,
`RYLWEEN_SAVE_FILE`, or `RYLWEEN_LOG_FILE` say otherwise.

It shows the title screen by default. `--name Strong` loads a seeded party
outside Thord, and `--name StrongEndgame` one two steps north of Castle Ydarg
with the five power artifacts. `--no-title` skips the title screen to boot a
state or feed a script. Other options go to the game: `--seed N`, `--state SLUG`,
`--script KEYS`, `--load FILE`, `--save FILE`, and `--log FILE`, which keeps a
named capture instead of replacing `latest.log`. For forensics, `--debug-log`
flashes the latest log line below the game frame, and `--debug-secrets` enables
hidden wilderness keys: `[` forces a cave discovery, `]` a random encounter, and
`M` shows the whole world map.

```sh
./rylween --screen title
./rylween --no-title --script 22SX --save build/saves/runtime.rsave
./rylween --name Strong --debug-log --debug-secrets
```

The installed launcher also runs by itself, with every option spelled out:

```sh
runtimes/runtime/build/install/rylween/bin/rylween --bundle build/compiled/rylween-bundle.json --user local --state fresh-replay --seed 1
```

## On This Machine Or Your Network, Over HTTP

### In A Container: The Default

```sh
./rylween serve              # this machine only
./rylween serve --lan        # every machine on the local network
./rylween serve --ansi       # the ANSI game for the browser, on port 8080
```

`serve` builds the image from the checkout with Docker Compose (Podman Compose
when Docker is missing), starts it with `compose.yaml`, waits until the server
answers, and prints the address with the commands for the sysop password, the
log, and stopping it:

```text
Rylween is up on http://127.0.0.1:8081/
  Sysop password: docker compose -f /path/to/rylween/deploy/compose.yaml exec rylween cat /data/auth/admin-password.txt
  Server log:     docker compose -f /path/to/rylween/deploy/compose.yaml logs -f rylween
  Stop:           /path/to/rylween/rylween stop
```

The build runs inside the image, so the machine needs only Docker or Podman,
not a JDK. Run `serve` again after pulling changes to rebuild and restart. On
macOS with Podman, start its VM first with `podman machine start`.

`--lan` publishes on this machine's private LAN address, for example
`http://192.168.1.20:8081/`, and refuses to pick a public one. `--host ADDRESS`
publishes on the address given, such as `0.0.0.0` for every interface, and
`--port PORT` on another port. Without any of them, `RYLWEEN_PUBLISH_HOST` in
the shell or `deploy/.env` decides, and otherwise `127.0.0.1`.

Settings come from `deploy/.env`, which `compose.yaml`'s header and
`.env.example` describe: copy `.env.example` to start one. Saves, accounts,
and logs live on the `rylween_data` volume (`rylween_ansi-data` for the ANSI
game). They survive restarts, rebuilds, and image updates; only
`docker compose down -v` or `volume rm` deletes them. To keep them in a host
directory instead, point `RYLWEEN_DATA_VOLUME` at it; a relative path such as
`./data` is relative to `deploy/`. Compose can also run directly from `deploy/`:

```sh
cd deploy
docker compose up --build -d     # or: podman compose up --build -d
docker compose logs -f rylween
docker compose down              # keeps the volumes
```

On first start the server creates the sysop account (`RYLWEEN_ADMIN_ACCOUNT`,
default `admin`), which gets the game's secret wilderness keys and which
players cannot register. Unless `RYLWEEN_ADMIN_PASSWORD` is set, its generated
password is saved in `/data/auth/admin-password.txt`, which the
`Sysop password` command prints.

### Without Docker: `--no-docker`

```sh
./rylween serve --no-docker
./rylween serve --no-docker --name StrongEndgame --debug-secrets
./rylween serve --no-docker --ansi --name Strong
./rylween serve --no-docker --account Strong --email strong@example.test --password 'change-this-password'
```

This runs the launcher built from the checkout, `--build` rebuilds it, and it
needs a JDK. It keeps its data in `~/.rylween/data`, the terminal game's, or in
`--data DIR`, and its output in `logs/serve-bitmap.console.log` (or
`serve-ansi`) there. `--name Strong` or `--name StrongEndgame` seeds that test
party into the shared save first and logs the browser in as it, without an
account. `--account` with `--password` (and optionally `--email`) creates or
verifies an account before the server starts. Other options go to the server,
such as `--debug-secrets`, `--map-tiles N`, and `--tile-size PX` for the bitmap
game; `--help` on the installed launchers lists them all. `--lan`, `--host`, and
`--port` work as with containers. The server keeps running in the background
until `./rylween stop`, and a new `serve` replaces the earlier one.

## Backups

```sh
./rylween backup                                   # ~/.rylween/data: the terminal game and --no-docker
./rylween backup --volume rylween_data             # ./rylween serve
./rylween backup --volume rylween-https_data       # ./rylween serve --https
./rylween backup --data DIR --output rylween-backup.tar.gz --include-logs
```

The archive holds the shared save (`saves/`) and the accounts (`auth/users/`),
and with `--include-logs` the logs, which can be large and record players'
activity. The generated sysop password is left out; the sysop account itself
survives a restore. A volume is read through a throwaway `alpine` container.
Restore by unpacking the archive into the data directory, or into the volume,
while the server is stopped. [`runtimes/web-common/README.md`](../runtimes/web-common/README.md)
describes what each file holds.

## On The Internet, Over HTTPS

This puts the game on a machine you control, reachable at a name such as
`https://rylween.example.net/`, with a real certificate and nothing sent in
plain text. `compose.https.yaml` runs two containers:

```text
players' browsers
   │  HTTPS, port 443          (port 80 only redirects to HTTPS
   ▼                            and answers Let's Encrypt's check)
Caddy ── gets and renews the certificate, adds security headers
   │  plain HTTP on a private container network
   ▼
rylween ── the game server, no port published, data on the `data` volume
```

The game server only speaks plain HTTP. Caddy, a web server that handles
certificates on its own, terminates HTTPS in front of it. Both run from images,
so the machine needs only Docker or Podman.

### What Is Already Provided

The pieces:

- **`compose.https.yaml`** runs the released game image behind Caddy. The game
  publishes no port; only Caddy listens on 80 and 443. Its header lists every
  setting.
- **`./rylween serve --https`**, from a checkout, pulls the latest release, starts
  `compose.https.yaml`, waits until the site answers over HTTPS with a valid
  certificate, and prints the address and the commands to manage it.
- **A `compose.https.yaml` attached to each release**, pinned to that release's
  image: download one file and write a `.env`.

What the game server does, with the settings `compose.https.yaml` gives it:

- **Secure cookies.** The login cookie is `HttpOnly`, `SameSite=Lax`, and
  `Secure`, so browsers only ever send it over HTTPS.
- **Encrypted game connection.** The page opens the game's WebSocket as
  `wss://` whenever it was loaded over HTTPS.
- **Same-origin check.** Any POST, such as a login or a registration, and the
  game connection are refused with 403 when another site's page sends them.
  The site is `RYLWEEN_PUBLIC_URL`, which the compose file sets from
  `RYLWEEN_DOMAIN`.
- **Failed-login limit.** After 10 failed logins from one client within 15
  minutes, that client's logins are refused with 429 and a `Retry-After` header
  until 15 minutes after the first failure. A successful login clears the
  count. The counts are kept in memory, so restarting the game clears them too.
- **Real client addresses.** The server believes `X-Forwarded-For` only from the
  proxies in `RYLWEEN_TRUSTED_PROXIES`. The compose file trusts the private
  address ranges, since only Caddy can reach the game. Every request and every
  failed login is logged with `client=<address>`.
- **Passwords** are stored hashed with PBKDF2-HMAC-SHA256. The sysop account is
  reserved: players cannot register its name.
- **`GET /healthz`** answers `ok` without a login.

What Caddy does:

- Gets a certificate for `RYLWEEN_DOMAIN` from Let's Encrypt the first time it
  starts, and renews it on its own well before it expires.
- Redirects every plain HTTP request to HTTPS (308).
- Adds `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, and
  `Referrer-Policy: no-referrer` to every response.
- Serves HTTP/2, and HTTP/3 on UDP 443.
- Passes the game's WebSocket through, and keeps an access log.

### What You Need

1. **A machine that stays on.** A Linux server, a rented virtual server (VPS),
   or a home server such as a Raspberry Pi 4 or 5. Both amd64 and arm64 work.
   The game uses about 250 MB of memory and Caddy about 15 MB, so 1 GB of memory
   is plenty. The images take about 1 GB of disk, plus room for saves and logs.
   A Mac or Windows machine works for trying it out, but a server should run
   Linux.
2. **A public IP address.** A VPS comes with one. At home, your router's
   internet-side (WAN) address must be public. Compare the WAN address your
   router shows with what `curl -4 https://ifconfig.me` prints on the machine.
   If they differ, or the router's starts with `100.64` to `100.127`, your
   provider shares one address among many customers (CGNAT). Then nothing from
   the internet can reach you; use a VPS, or see
   [Tailscale](#without-a-public-address-tailscale). A home address that
   changes now and then needs a dynamic DNS name (item 3).
3. **A domain name** for players to open, such as `rylween.example.net`: a name
   or subdomain at any domain registrar, or a free dynamic DNS name, such as one
   from DuckDNS. Its DNS `A` record must point at the public IPv4 address. Add an
   `AAAA` record only if the machine also takes IPv6 connections on ports 80 and
   443.
4. **Ports 80 and 443 open.** TCP 80 and 443, and optionally UDP 443 for
   HTTP/3, must reach the machine. At home, forward them on the router. On a VPS,
   open them in the provider's firewall. On the machine, allow them in its
   firewall. Open nothing else for Rylween, and never port 8081.
5. **Docker or Podman.** Docker Engine with the Compose plugin is the
   recommended choice: run as root, it lets the game see players' real
   addresses (see [step 9](#9-check-players-addresses)). Podman 4 or newer, with
   `podman compose`, works too. git is needed only for `./rylween serve --https` from
   a checkout.
6. **Certificates: nothing to do.** You don't buy, create, or copy any. Caddy
   gets them from Let's Encrypt, which first checks that you control the name by
   connecting to it on port 80 or 443. That is why DNS and both ports must be
   right before the first start. The certificates live on Caddy's `caddy-data`
   volume. Keep it: Let's Encrypt issues only a few certificates for the same
   name per week.

### Step By Step

#### 1. Point the name at the machine

At your registrar or DNS provider, create an `A` record from
`rylween.example.net` to the machine's public IPv4 address. Wait until it
resolves, ideally checking from another network:

```sh
dig +short rylween.example.net     # prints the public address
```

#### 2. Open ports 80 and 443

Forward TCP 80 and 443 (and UDP 443) from the router to the machine, or open
them in the VPS provider's firewall. Then allow them on the machine. With `ufw`
on Debian or Ubuntu:

```sh
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
```

[Router and network notes](#router-and-network-notes) shows the forwarding on a
MikroTik (RouterOS) router, and what to do about IPv6.

#### 3. Install Docker

On Debian, Ubuntu, Fedora, Raspberry Pi OS, and most other Linux
distributions, Docker's install script sets up Docker Engine with the Compose
plugin:

```sh
curl -fsSL https://get.docker.com | sudo sh
sudo systemctl enable --now docker
docker compose version
```

With Podman instead, rootless Podman may not listen on ports below 1024.
Allow it once:

```sh
echo 'net.ipv4.ip_unprivileged_port_start=80' | sudo tee /etc/sysctl.d/90-rylween.conf
sudo sysctl --system
```

#### 4. Get the compose file

Without a checkout, download the release's copy into a directory of its own:

```sh
mkdir -p ~/rylween && cd ~/rylween
curl -fLO https://github.com/lagergren/rylween/releases/latest/download/compose.https.yaml
```

Releases attach it from 0.9.3 on. Before that, take it from `main`:
`https://raw.githubusercontent.com/lagergren/rylween/main/deploy/compose.https.yaml`.
From a checkout, it is `deploy/compose.https.yaml`, and the steps below run in
`deploy/`.

#### 5. Write the settings

Put a `.env` file next to the compose file, readable only by you. It holds the
sysop password, so keep it out of shell history and version control:

```sh
umask 077
cat > .env <<'EOF'
RYLWEEN_DOMAIN=rylween.example.net
RYLWEEN_ADMIN_PASSWORD=replace-with-a-long-unique-secret
EOF
```

`RYLWEEN_ADMIN_PASSWORD` applies only when the sysop account is created, on
the first start. Without it, the server generates a password and saves it on
the data volume. The header of `compose.https.yaml` lists the other settings,
such as `RYLWEEN_VERSION` to pin a release. In a checkout, `deploy/.env` serves
both compose files, so a setting both read, such as `RYLWEEN_DATA_VOLUME`,
applies to both.

#### 6. Start it

```sh
docker compose -f compose.https.yaml up -d
```

Or, from a checkout, `./rylween serve --https`. It pulls the latest release,
starts both containers, waits up to three minutes for a valid certificate, and
prints:

```text
Rylween is up on https://rylween.example.net/
  Sysop password: docker compose -f .../deploy/compose.https.yaml exec rylween cat /data/auth/admin-password.txt
  Server log:     docker compose -f .../deploy/compose.https.yaml logs -f rylween
  Proxy log:      docker compose -f .../deploy/compose.https.yaml logs -f caddy
  Stop:           .../rylween stop
```

#### 7. Watch the certificate arrive

```sh
docker compose -f compose.https.yaml logs -f caddy
```

Within a minute Caddy logs `certificate obtained successfully`. Then check
from any machine:

```sh
curl -I https://rylween.example.net/healthz    # HTTP/2 200
curl -I http://rylween.example.net/            # 308, to https://rylween.example.net/
```

#### 8. Play

Open `https://rylween.example.net/` and register an account. The game's log
prints the link too:

```sh
docker compose -f compose.https.yaml logs rylween | grep -A2 'is up'
```

The sysop account is `admin`, unless `RYLWEEN_ADMIN_ACCOUNT` names another,
with the password from `.env`, or the generated one:

```sh
docker compose -f compose.https.yaml exec rylween cat /data/auth/admin-password.txt
```

#### 9. Check players' addresses

The failed-login limit counts per client address, so the game must see real
ones. Log in from a phone on mobile data, then:

```sh
docker compose -f compose.https.yaml logs rylween | grep -o 'client=[^ ]*' | sort | uniq -c
```

The phone's public address should be listed. If every line shows the same
private address instead (`10.…`, `172.16.…` to `172.31.…`, or `192.168.…`), the
container engine hides where connections come from. Rootless Podman does that,
and so does Docker when its userland proxy handles the ports. The limit then
counts every player as one client, and ten failed logins by anyone lock out
everyone for 15 minutes. Run Docker or Podman as root to fix it.

### Running It

- **Upgrades.** Pull and restart. Accounts, saves, and certificates stay on
  their volumes:

  ```sh
  docker compose -f compose.https.yaml pull
  docker compose -f compose.https.yaml up -d
  ```

  `RYLWEEN_VERSION=0.9.3` in `.env` stays on one release until you change it.
- **Restarts.** Both containers restart on their own (`unless-stopped`),
  including after a reboot once Docker starts at boot
  (`sudo systemctl enable docker`).
- **Logs.** `docker compose -f compose.https.yaml logs rylween` shows the game's
  server log. The volume also keeps it, and each session's own log, under
  `/data/logs/runtime-bitmap-web/`. `docker compose -f compose.https.yaml logs caddy`
  shows Caddy's access log.
- **Backups.** Archive the game's volume, which Compose names
  `rylween-https_data`: from a checkout with
  `./rylween backup --volume rylween-https_data` (see [Backups](#backups)), or
  without one:

  ```sh
  docker run --rm -v rylween-https_data:/data:ro -v "$PWD":/backup alpine \
      tar czf /backup/rylween-$(date +%F).tar.gz -C /data saves auth/users
  ```

- **Stopping.** `docker compose -f compose.https.yaml down` stops both and keeps
  the volumes. `down -v` deletes them, with every account, save, and
  certificate.
- **Banning guessers.** For more than the built-in limit, point fail2ban or a
  similar tool at the game's `bitmap.auth.login_failed ... client=<address>`
  lines.

### What This Protects, And What It Does Not

It protects:

- Passwords, cookies, and play from being read or changed on the network.
- Players' sessions from other websites' pages.
- Accounts from password guessing from one address.

It does not cover:

- Guessing spread over many addresses. The limit is per client, so long, unique
  passwords still matter, the sysop's most of all.
- Failed-login counts across restarts. They live in memory.
- A second factor. Accounts are a name, an email, and a password.
- The ANSI game for the browser (`docker/Dockerfile.ansi`). It has none of
  the same-origin check, the login limit, or the proxy trust, so put the bitmap
  game on the internet instead.
- The machine itself. Keep it updated, log in with SSH keys, and open no ports
  beyond 80 and 443.

### Troubleshooting

- **No certificate, or `./rylween serve --https` times out.** Caddy's log says why:
  `docker compose -f compose.https.yaml logs caddy`. Usually DNS does not yet
  point at the machine (`dig +short rylween.example.net`), or port 80 or 443
  does not reach it. Check from outside your network, for example
  `curl -I http://rylween.example.net/` on a phone's mobile data. After repeated
  failures Let's Encrypt refuses for a while; fix the cause and wait an hour.
- **`cannot expose privileged port 80`.** Rootless Podman; see step 3.
- **`cross-origin request refused` when logging in.** `RYLWEEN_DOMAIN` must be
  exactly the name in the browser's address bar.
- **Logged in, but sent back to the login page.** The browser used `http://`,
  which never gets the secure cookie. Open `https://`; Caddy redirects plain
  HTTP there unless something in front of it stops it.
- **`Too many failed logins`.** Wait the time the page names, or clear all
  counts by restarting the game: `docker compose -f compose.https.yaml restart rylween`.

### Router And Network Notes

On a MikroTik router running RouterOS, with interface lists named `WAN` and
`LAN`, and the machine running the compose file at the LAN address
`192.168.88.20`, forward both ports to it:

```routeros
/ip firewall nat
add chain=dstnat in-interface-list=WAN protocol=tcp dst-port=443 \
    action=dst-nat to-addresses=192.168.88.20 to-ports=443 \
    comment="Rylween HTTPS to Caddy"
add chain=dstnat in-interface-list=WAN protocol=tcp dst-port=80 \
    action=dst-nat to-addresses=192.168.88.20 to-ports=80 \
    comment="Rylween HTTP redirect and Let's Encrypt to Caddy"
```

Many RouterOS default configurations already accept forwarded traffic that
was destination-NATed. If your forward chain ends in a drop rule, add a narrow
accept rule before it, after the `established,related` and `invalid` rules:

```routeros
/ip firewall filter
add chain=forward action=accept in-interface-list=WAN \
    connection-nat-state=dstnat connection-state=new protocol=tcp \
    dst-address=192.168.88.20 dst-port=80,443 \
    comment="Allow Rylween through to Caddy"
```

Do not add a broad rule such as "accept all from WAN to the server", and do not
add `input` rules for Rylween: those are for services on the router itself.

IPv6 is different from IPv4: a machine with an IPv6 address may be reachable
from the internet with no NAT in between, so IPv4 port forwarding protects
nothing there. If the machine has one, allow only TCP 80 and 443 (and UDP 443)
to it in the router's IPv6 firewall, and add the `AAAA` record only then.

A separate network, VLAN, or router port for the server makes for a stricter
setup, but a small deployment does without one when:

- the game is reachable only through Caddy, as `compose.https.yaml` sets it up,
- only Caddy listens on the machine's network address,
- the router forwards only ports 80 and 443 to that machine,
- the machine accepts administrative logins, such as SSH, only from your LAN
  or a VPN,
- the machine, Docker, the images, and the router are kept updated,
- the game's data volume is backed up.

### Behind A Proxy You Already Run

With nginx, HAProxy, Traefik, or a load balancer already in front, run the
image without Caddy, published only to `127.0.0.1` or a private network, and:

- forward WebSocket upgrades for `/ws/play`, which nginx, for one, needs told;
- set `RYLWEEN_PUBLIC_URL` to the address players open, such as
  `https://rylween.example.net`;
- set `RYLWEEN_TRUSTED_PROXIES` to the proxy's address, as it connects to the
  game, so the game believes its `X-Forwarded-For`;
- keep `RYLWEEN_AUTH_SECURE_COOKIES=true`, the image's default.

### The ANSI Game In The Browser

The ANSI game for the browser lets one player at a time play, and has none of
the bitmap server's same-origin check, failed-login limit, or proxy trust, so
keep it to players you know. To serve it over HTTPS, run it on this machine
only, with secure cookies, by setting `RYLWEEN_AUTH_SECURE_COOKIES=true` in
`deploy/.env` (which applies to the bitmap game in `compose.yaml` too):

```sh
./rylween serve --ansi       # publishes on 127.0.0.1:8080
```

Then put Caddy, installed on the machine, in front of it, with a `Caddyfile`
such as:

```caddyfile
rylween.example.net {
    encode zstd gzip
    reverse_proxy 127.0.0.1:8080
    header {
        Strict-Transport-Security "max-age=31536000"
        X-Content-Type-Options "nosniff"
        Referrer-Policy "no-referrer"
    }
}
```

Its server log is `/data/logs/web-server.log`, and each session writes
`/data/logs/<account>-<epoch-millis>.log`. Both are echoed to its standard
output, which `docker compose -f deploy/compose.yaml logs rylween-ansi` shows.

### Without A Public Address: Tailscale

If the machine cannot be reached from the internet, for example behind CGNAT,
and the players are people you can invite, Tailscale can carry the game
instead. It gives the machine an HTTPS name in your tailnet, such as
`https://rylween-host.example-tailnet.ts.net/`, with a certificate, without a
domain or open ports, and `tailscale serve` forwards it to the game on the
machine. Run the game with `RYLWEEN_AUTH_SECURE_COOKIES=true` and
`RYLWEEN_PUBLIC_URL` set to that name. This repository does not script or test
that setup; see Tailscale's documentation on `tailscale serve`.
