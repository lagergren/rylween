# Runtime Bitmap Web

`:runtime-bitmap-web` is the browser canvas bitmap runtime.

It is a peer runtime to `:runtime-web`, not a layer on top of the ANSI terminal
view. It reuses the shared compiled-bundle backend from `:runtime`, the authored
game/assets from `:game`, and web auth/protocol helpers from `:web-common`.

It contains:

- the bitmap HTTP/WebSocket launcher,
- the renderer-neutral runtime port implementation that emits bitmap frames,
- the canvas client TypeScript, HTML, and CSS resources,
- bitmap frame protocol data classes,
- the installed `rylween-bitmap-web` launcher used by `run-bitmap-server.sh`.

Renderer-specific layout, art selection, transitions, and canvas presentation
belong here. Shared gameplay behavior, command semantics, battle targeting,
save/load, and script execution belong in `:runtime`.
