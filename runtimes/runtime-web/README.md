# Runtime Web

`:runtime-web` serves the existing ANSI runtime to a browser.

It wraps the shared `:runtime` backend with:

- a small HTTP/WebSocket server,
- authentication/session wiring from `:web-common`,
- `BrowserAnsiTerminal`, which forwards ANSI output to a browser terminal view,
- the installed `rylween-web` launcher used by the Docker image and
  `bin/run-server.sh`.

This module should not own game mechanics, key-command meaning, password
storage, or generic WebSocket protocol code. Those belong in `:runtime` or
`:web-common`.

For production behind a reverse proxy, build
`deploy/docker/Dockerfile.production`, bind the container to loopback or a
private network, terminate HTTPS at the proxy, and keep
`RYLWEEN_AUTH_SECURE_COOKIES=true`.

## Running It Locally

Build the bundle and the launcher, then run the launcher against the bundle:

```sh
./gradlew :tools:emitExternalCompiledBundle :runtime-web:installRuntimeWeb --console=plain
runtimes/runtime-web/build/install/rylween-web/bin/rylween-web \
  --bundle build/compiled/rylween-bundle.json --data ~/.rylween/data --host 127.0.0.1
```

The launcher's defaults are the container's: bundle `/app/rylween-bundle.json`,
data directory `/data`, host `0.0.0.0`, port `8080`, and state `fresh-replay`.
`rylween-web --help` lists every flag and environment variable, including
`RYLWEEN_BUNDLE`, `RYLWEEN_DATA_DIR`, `RYLWEEN_HOST`, `RYLWEEN_PORT`, the
`RYLWEEN_AUTH_*` settings, and the sysop settings described below.
`bin/run-server.sh` runs the same server in Docker.

## Sessions

Only one ANSI web session plays the shared world at a time; other players see a
waiting message until it ends. A second connection for an account that is
already playing is turned away. Quitting ends the session and closes the
connection, so the player reloads the page to play again. (The bitmap server
returns to its title screen instead.)

## Exposing A Production Docker Deployment

The safe production shape is:

1. Rylween runs as the production Docker container on the deployment host.
2. The container publishes plain HTTP only to `127.0.0.1` or a private Docker
   network on that host.
3. A reverse proxy on the deployment host terminates public HTTPS and forwards
   to the local container port.
4. The internet router forwards only TCP 443, and optionally TCP 80 for ACME
   HTTP-01 certificates, to the reverse proxy host.

Do not expose the Rylween container port directly to the internet. Do not add
RouterOS `input` rules for Rylween unless the reverse proxy is actually running
on the router itself; normal port forwarding to a host behind the router is
`dstnat` plus `forward`, not `input`.

Example host-side run, using the ANSI production image:

```sh
docker build -f deploy/docker/Dockerfile.production -t rylween-web-prod .

mkdir -p /etc/rylween
chmod 700 /etc/rylween
$EDITOR /etc/rylween/rylween.env
chmod 600 /etc/rylween/rylween.env

docker run -d \
  --name rylween-web \
  --restart unless-stopped \
  --env-file /etc/rylween/rylween.env \
  -p 127.0.0.1:8080:8080 \
  -v rylween-data:/data \
  rylween-web-prod
```

Example `/etc/rylween/rylween.env`:

```sh
RYLWEEN_ADMIN_PASSWORD=replace-with-a-long-unique-secret
RYLWEEN_AUTH_SECURE_COOKIES=true
```

On first start the server creates the sysop account (`RYLWEEN_ADMIN_ACCOUNT`,
default `admin`), which gets the game's secret wilderness keys and which
players cannot register. `RYLWEEN_ADMIN_PASSWORD` only applies when that account
is created; without it the server generates a password into
`/data/auth/admin-password.txt`, readable with
`docker exec rylween-web cat /data/auth/admin-password.txt`.
`RYLWEEN_VICTIM_ACCOUNT` names an account the sysop gods hunt down every 101
moves; unset means nobody. `RYLWEEN_AUTH_ACCOUNT`, `RYLWEEN_AUTH_EMAIL`, and
`RYLWEEN_AUTH_PASSWORD` optionally create or verify one more account at startup;
giving it the sysop's name makes it the sysop account. The production image
already defaults `RYLWEEN_AUTH_SECURE_COOKIES` to `true`.

Do not pass production passwords on the command line. They can end up in shell
history, process listings, or operational logs. Prefer a locked-down environment
file, Docker secrets, or the deployment platform's secret manager.

Example Caddy reverse proxy on the deployment host:

```caddyfile
rylween.example.net {
    encode zstd gzip
    reverse_proxy 127.0.0.1:8080
    header {
        Strict-Transport-Security "max-age=31536000; includeSubDomains"
        X-Content-Type-Options "nosniff"
        Referrer-Policy "no-referrer"
    }
}
```

Caddy handles WebSocket upgrades for `reverse_proxy`. If you use nginx, HAProxy,
Traefik, or another proxy, make sure WebSocket upgrade headers are forwarded;
the game socket is `/ws/play`. `GET /healthz` returns `ok` without a login, for
health checks.

The server log is `/data/logs/web-server.log`, and each session writes
`/data/logs/<account>-<epoch-millis>.log`. Server log lines and session events
are also echoed to standard output, so `docker logs rylween-web` shows them.

### RouterOS Port Forwarding Example

Assumptions:

- RouterOS has interface lists named `WAN` and `LAN`.
- Public DNS for `rylween.example.net` points at the router's public address.
- The deployment host has a stable LAN address, `192.168.88.20`.
- The reverse proxy listens on `192.168.88.20:443`.
- The Rylween container itself is not reachable on the LAN or WAN; it is bound
  to `127.0.0.1:8080` on the deployment host.

Forward public HTTPS to the reverse proxy:

```routeros
/ip firewall nat
add chain=dstnat in-interface-list=WAN protocol=tcp dst-port=443 \
    action=dst-nat to-addresses=192.168.88.20 to-ports=443 \
    comment="Rylween HTTPS to reverse proxy"
```

If your reverse proxy uses ACME HTTP-01 certificate validation, also forward
TCP 80 to the reverse proxy. If you use DNS-01 validation or install
certificates out of band, leave TCP 80 closed.

```routeros
/ip firewall nat
add chain=dstnat in-interface-list=WAN protocol=tcp dst-port=80 \
    action=dst-nat to-addresses=192.168.88.20 to-ports=80 \
    comment="Rylween ACME HTTP-01 to reverse proxy"
```

Many RouterOS default firewall configurations already allow established
connections and drop new WAN traffic that is not destination-NATed. If your
forward chain ends in a drop rule, add a narrow accept rule before that final
drop:

```routeros
/ip firewall filter
add chain=forward action=accept in-interface-list=WAN \
    connection-nat-state=dstnat connection-state=new protocol=tcp \
    dst-address=192.168.88.20 dst-port=443 \
    comment="Allow Rylween HTTPS dstnat"
```

Add the same kind of narrow forward rule for TCP 80 only if you forwarded TCP 80
above. Keep existing `established,related` and `invalid` rules in front of the
final drop. A good baseline forward policy accepts established/related traffic,
drops invalid traffic, accepts the specific `dstnat` services you intend to
publish, and drops other new WAN-originated traffic.

Do not add a broad rule such as "accept all from WAN to server" and do not
forward port `8080` or `8081` from the internet. Those are internal application
ports used behind the reverse proxy.

### IPv6, CGNAT, And Network Isolation

IPv4 port forwarding only works when the router has a real public IPv4 address.
If the WAN address is in private space or `100.64.0.0/10`, the ISP is probably
using CGNAT and inbound IPv4 forwarding will not work without a public address,
a tunnel, or an external hosting endpoint.

IPv6 is different: hosts may be globally reachable without NAT. If the
deployment host receives an IPv6 address, add explicit IPv6 firewall rules that
allow only TCP 443, and optionally TCP 80 for ACME, to the reverse proxy host.
Do not assume IPv4 NAT protects IPv6.

A separate NIC, VLAN, or dedicated router port can be useful for a stricter
server network, but it is not required for a small deployment if these controls
are in place:

- the Docker app port is bound to loopback or a private container network,
- only the reverse proxy listens on the host LAN address,
- RouterOS forwards only the public HTTPS service to that host,
- host firewall rules allow administrative access only from trusted LAN or VPN
  addresses,
- the OS, Docker image, reverse proxy, and RouterOS are kept updated,
- `/data` is backed up and auth/save data is not stored in the image.

The bitmap production image follows the same exposure pattern. Its internal app
port defaults to `8081`, so keep it behind the reverse proxy just like the ANSI
web image and publish only public HTTPS at the router. `deploy/compose.yaml`
runs that image (`docker compose -f deploy/compose.yaml up --build -d`) and
publishes it on `127.0.0.1:8081` by default. Compose defaults
`RYLWEEN_AUTH_SECURE_COOKIES` to `false`, so set it to `true` in `deploy/.env`
behind an HTTPS proxy. `./deploy.sh` publishes on this machine's LAN address for
local-network play; for a reverse-proxy deployment set
`RYLWEEN_PUBLISH_HOST=127.0.0.1` or run compose directly.
