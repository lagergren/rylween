# Build Logic

`build-logic` is the included Gradle build that defines project conventions and
custom tasks.

It contains:

- QA and Kotlin/JVM build conventions.
- Custom task classes used by module build files.
- Generated game-catalog handle support for `:game`.
- Helpers for running installed launchers during verification.

Keep reusable Gradle task code here instead of scattering stringly task
configuration through individual module builds.
