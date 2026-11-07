# Tools

`:tools` contains build-time and verification utilities for compiled bundles.

It emits, validates, and inspects the external bundle artifact that the
`:runtime` and `:runtime-web` launchers and the ANSI web Docker images load. The
bitmap runtime compiles the game in-process unless it is given `--bundle`.

- `:tools:emitExternalCompiledBundle` writes `build/compiled/rylween-bundle.json`.
- `:tools:checkExternalCompiledBundle` reads it back and fails on empty tables.
- `:tools:inspectExternalCompiledBundle` writes
  `build/compiled/rylween-bundle.inspect.txt`.
- `:tools:check` runs the last two.

This module depends on `:core` and `:game` because it turns authored content
into artifacts, and on `:runtime` only for `RuntimeLog` diagnostics (`--log` or
`RYLWEEN_TOOL_LOG`). It should not contain playable runtime behavior.
