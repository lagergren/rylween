# Docker Deployment

`deploy/docker/` contains Docker packaging for the web runtimes.

- `Dockerfile` builds the browser-served ANSI web runtime image used by
  `bin/run-server.sh --build`.
- `Dockerfile.production` builds the production-oriented ANSI web runtime image.
- `Dockerfile.bitmap-production` builds the production-oriented bitmap web
  runtime image.

Build commands still use the repository root as the Docker context so Gradle,
source modules, resources, and `.dockerignore` are all visible:

```sh
docker build -f deploy/docker/Dockerfile.production -t rylween-web-prod .
docker build -f deploy/docker/Dockerfile.bitmap-production -t rylween-bitmap-prod .
```

The production images serve plain HTTP inside the container. Bind the container
to loopback or a private container network, terminate HTTPS at nginx, Caddy,
HAProxy, a platform load balancer, or another reverse proxy, and set
`RYLWEEN_AUTH_SECURE_COOKIES=true` when traffic reaches users over HTTPS.

## Compose

The root `compose.yaml` builds `Dockerfile.bitmap-production` and runs the
bitmap game with its data directory on a persistent volume:

```sh
docker compose up --build -d     # or: podman compose up --build -d
docker compose logs -f rylween
docker compose down              # keeps the volume
```

On macOS with Podman, start the VM first with `podman machine start`.

Saves, accounts, and logs are written to `/data` in the container, backed by
the `rylween_data` named volume. They survive `down`, rebuilds, and image
updates; only `down -v` or `volume rm` deletes them. To keep the data in a
host directory instead, for example to back it up with
`bin/backup-web-data.sh`, create the directory and point
`RYLWEEN_DATA_VOLUME` at it:

```sh
mkdir -p ~/.rylween/compose-data
RYLWEEN_DATA_VOLUME=~/.rylween/compose-data docker compose up -d
```

The container publishes on `127.0.0.1:8081` by default. To let other machines
on the local network connect, run compose through the deploy script, which
detects this machine's private LAN address and publishes on it:

```sh
./deploy-rylween.sh --compose
```

Or set `RYLWEEN_PUBLISH_HOST` yourself, to a LAN address such as `192.168.x.y`
or to `0.0.0.0`. `RYLWEEN_HOST_PORT` changes the port.
Unlike the plain image, compose turns secure cookies off so logins work over
plain HTTP; set `RYLWEEN_AUTH_SECURE_COOKIES=true` behind an HTTPS proxy. All
settings are listed at the top of `compose.yaml` and can be kept in an
uncommitted `.env` file next to it.

The root `.dockerignore` intentionally remains at the repository root because
Docker reads ignore rules relative to the build context, not relative to the
Dockerfile path.
