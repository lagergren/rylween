# Core

`:core` owns the renderer-agnostic game authoring and bundle contracts.

It contains:

- Kotlin DSL surface and immutable content models: `rylween.dsl` is the
  authoring surface, and `rylween.dsl.impl` holds the model graph and
  subsystem builders.
- Validation for authored content.
- The compiler that lowers the DSL model into compiled bundle tables, and the
  loader that checks a bundle (opcode operand contract, native-service
  allowlist) before runtimes and tools use it.
- Compiled bundle structures consumed by runtimes and tools.
- External bundle artifact encoding/decoding.
- Runtime-system metadata exported from authored DSL into portable tables.

The public authoring model is documented in the
[DSL authoring guide](../docs/dsl-authoring.md).

This module must not depend on terminal, browser, or concrete runtime UI code.
If a concept is game content, renderer-neutral runtime metadata, compiled VM
state, or validation, it belongs here. If it is a transport, screen renderer, or
process launcher, it belongs in a runtime module.
