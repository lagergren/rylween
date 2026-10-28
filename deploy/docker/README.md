# Docker Deployment

`deploy/docker/` contains Docker packaging for the web runtimes.

- `Dockerfile` builds the browser-served ANSI web runtime image used by
  `bin/run-server.sh --build`.
- `Dockerfile.production` builds the production-oriented ANSI web runtime image.
- `Dockerfile.bitmap-production` builds the production-oriented bitmap web
  runtime image.

`Dockerfile` and `Dockerfile.production` serve the ANSI web runtime on port
8080; `Dockerfile.bitmap-production` serves the bitmap runtime on port 8081. All
keep saves, accounts, and logs under the `/data` volume. Run directly, the
bitmap image uses an 8x8 wilderness view at 64 px per tile, while compose and the
bitmap launcher default to 6x6 at 68 px.

Build commands still use the repository root as the Docker context so Gradle,
source modules, resources, and `.dockerignore` are all visible:

```sh
docker build -f deploy/docker/Dockerfile.production -t rylween-web-prod .
docker build -f deploy/docker/Dockerfile.bitmap-production -t rylween-bitmap-prod .
```

The production images serve plain HTTP inside the container. Bind the container
to loopback or a private container network and terminate HTTPS at nginx, Caddy,
HAProxy, a platform load balancer, or another reverse proxy. Both production
images set `RYLWEEN_AUTH_SECURE_COOKIES=true`, so browsers keep the login cookie
only over HTTPS; set it to `false` only when players reach the server over plain
HTTP.

## Compose

`deploy/compose.yaml` builds `Dockerfile.bitmap-production` from the
repository root and runs the bitmap game with its data directory on a
persistent volume. Run compose from `deploy/`, or pass
`-f deploy/compose.yaml` from the repository root:

```sh
cd deploy
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
`RYLWEEN_DATA_VOLUME` at it in `deploy/.env`, so every later deploy uses it
too. From the repository root:

```sh
mkdir -p ~/.rylween/compose-data
echo 'RYLWEEN_DATA_VOLUME=~/.rylween/compose-data' >> deploy/.env
./deploy.sh
bin/backup-web-data.sh --data ~/.rylween/compose-data --output rylween-backup.tar.gz
```

On first start the server creates the sysop account (`RYLWEEN_ADMIN_ACCOUNT`,
default `admin`), which players cannot register. Unless `RYLWEEN_ADMIN_PASSWORD`
is set, its generated password is saved in `/data/auth/admin-password.txt`:

```sh
docker compose exec rylween cat /data/auth/admin-password.txt
```

The container publishes on `127.0.0.1:8081` by default. To let other machines
on the local network connect, run compose through the deploy script, which
builds the image, detects this machine's private LAN address, publishes on it,
waits until the server answers, and prints its address with the commands for
the sysop password, the log, and `down`:

```sh
./deploy.sh                      # from the repository root; a link to deploy/deploy-rylween.sh
./deploy-rylween.sh              # from deploy/; the same script
```

Or set `RYLWEEN_PUBLISH_HOST` yourself, to a LAN address such as `192.168.x.y`
or to `0.0.0.0`. `RYLWEEN_HOST_PORT` changes the port.
Unlike the production image run with `docker run`, compose sets
`RYLWEEN_AUTH_SECURE_COOKIES=false` so logins work over plain HTTP; set it to
`true` behind an HTTPS proxy. When `deploy/.env` sets `RYLWEEN_PUBLISH_HOST`,
the deploy script uses that address instead of detecting the LAN address. All
settings are listed at the top of `compose.yaml` and can be kept in an
uncommitted `deploy/.env` file next to it; copy `deploy/.env.example`, which
shows each one at its default. A relative `RYLWEEN_DATA_VOLUME` such as
`./data` is relative to `deploy/`.

`./deploy.sh --launcher` skips Docker: it starts the installed bitmap launcher
in the background on the LAN address, port 8081, with its data in
`~/.rylween/deploy` and its log in `/tmp/rylween-bitmap-web-deploy.log`,
stopping a server it started earlier. Build the launcher first with
`./gradlew :runtime-bitmap-web:installRuntimeBitmapWeb`.

The root `.dockerignore` intentionally remains at the repository root because
Docker reads ignore rules relative to the build context, not relative to the
Dockerfile path.
