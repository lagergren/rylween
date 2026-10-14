# Build Logic

`build-logic` is the included Gradle build that defines project conventions and
custom tasks. It declares no versions of its own; it reads the root
`gradle/libs.versions.toml`.

It contains:

- `rylween.qa-conventions`, applied by every module: ktlint, which fails
  `check` on violations (`build/generated/` is excluded); report-only PMD CPD
  (`cpdCheck`, not part of `check`); the `qaCheck` aggregate of both; and JUnit
  Platform with `kotlin-test` for tests.
- `RunInstalledLauncherWithJavaTask` in `src/main/kotlin/rylween/gradle/RylweenBuildTasks.kt`,
  behind `:runtime`'s `checkRuntime*` tasks. It runs an installed launcher
  against a compiled bundle on a toolchain `JavaLauncher`, passed to the start
  script as `JAVA_HOME`, with exactly one of a static `screen` or a key `script`.
  It writes the runtime log to `build/logs/runtime/<task>.log` and the
  launcher's output to `build/logs/runtime/<task>.out`, and fails on a non-zero
  exit or missing expected text, printing the end of the output.
- `GenerateGameCatalogHandlesTask`, behind `:game:generateGameCatalogHandles`,
  which generates the typed game-catalog handles from the game sources.

Keep reusable Gradle task code here instead of scattering stringly task
configuration through individual module builds.
