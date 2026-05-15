# Launcher Scripts

`bin/` contains local developer and operator entrypoints for running Rylween
from this checkout.

- `run.sh` builds and launches the direct ANSI terminal runtime.
- `run-server.sh` builds or runs the browser-served ANSI web runtime in Docker.
- `run-bitmap-server.sh` builds and launches the bitmap web runtime locally.
- `backup-web-data.sh` archives saves and web auth records from the data
  directory.

These scripts are intentionally not under `deploy/`: they are normal checkout
commands, useful for development, smoke testing, local play, and simple
operations. Production packaging belongs under `deploy/docker/`; future
deployment-specific helper scripts can live under `deploy/scripts/` if they are
part of reverse-proxy/container rollout rather than day-to-day local launches.

Run them from the repository root, for example:

```sh
./bin/run.sh --build --name Strong
./bin/run-bitmap-server.sh --build --name Strong
```
