/** Release version the build stamps into the runtime from `rylween.version` in gradle.properties. */

package rylween.runtime

private const val VERSION_RESOURCE = "rylween/runtime/version.txt"

/** The release version, e.g. `0.9`. */
val rylweenVersion: String = runtimeResourceText(VERSION_RESOURCE).trim()

/** The release version as players see it, e.g. `v0.9`. */
val rylweenVersionLabel: String = "v$rylweenVersion"
