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

Minimum `/etc/rylween/rylween.env`:

```sh
RYLWEEN_AUTH_ACCOUNT=admin
RYLWEEN_AUTH_EMAIL=admin@example.net
RYLWEEN_AUTH_PASSWORD=replace-with-a-long-unique-secret
RYLWEEN_AUTH_SECURE_COOKIES=true
```

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
Traefik, or another proxy, make sure WebSocket upgrade headers are forwarded.

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
web image and publish only public HTTPS at the router.
