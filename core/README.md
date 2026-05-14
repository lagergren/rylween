# Core

`:core` owns the renderer-agnostic game authoring and bundle contracts.

It contains:

- Kotlin DSL surface and immutable content models.
- Validation for authored content.
- Compiled bundle structures consumed by runtimes and tools.
- External bundle artifact encoding/decoding.
- Runtime-system metadata exported from authored DSL into portable tables.

This module must not depend on terminal, browser, or concrete runtime UI code.
If a concept is game content, renderer-neutral runtime metadata, compiled VM
state, or validation, it belongs here. If it is a transport, screen renderer, or
process launcher, it belongs in a runtime module.
