# Tools

`:tools` contains build-time and verification utilities for compiled bundles.

It emits and validates the external Rylween bundle artifact consumed by runtime
launchers and Docker images, and it owns the narrow native smoke readers used to
prove that the artifact is portable outside Kotlin.

This module can depend on `:core` and `:game` because it turns authored content
into artifacts. It should not contain playable runtime behavior.
