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
