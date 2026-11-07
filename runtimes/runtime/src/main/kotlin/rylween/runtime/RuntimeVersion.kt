/** The version the build stamps into the runtime from `rylween.version` in gradle.properties. */

package rylween.runtime

private const val VERSION_RESOURCE = "rylween/runtime/version.txt"

/** The version: `X.Y.Z` in a release, `X.Y.Z-SNAPSHOT` between releases. */
val rylweenVersion: String = runtimeResourceText(VERSION_RESOURCE).trim()

/** The version as players see it, `v` and [rylweenVersion]. */
val rylweenVersionLabel: String = "v$rylweenVersion"
