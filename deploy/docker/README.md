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

The root `.dockerignore` intentionally remains at the repository root because
Docker reads ignore rules relative to the build context, not relative to the
Dockerfile path.
