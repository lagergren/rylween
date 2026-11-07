/**
 * Release versioning. `rylween.version` in gradle.properties is the one place the version is set. Between releases it
 * is the snapshot of the next patch release, `X.Y.Z-SNAPSHOT`. Releasing it publishes `X.Y.Z` and moves on to
 * `X.Y.(Z+1)-SNAPSHOT`. The release workflow (.github/workflows/release.yml) makes both moves with
 * [SetRylweenVersionTask], and `./gradlew releaseDist` starts that workflow with [StartReleaseWorkflowTask].
 */

package rylween.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/** The gradle.properties key that sets the version. */
const val RYLWEEN_VERSION_PROPERTY = "rylween.version"

/** A `major.minor.patch` release, or the `-SNAPSHOT` that leads up to it. */
data class RylweenVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val snapshot: Boolean,
) {
    /** The release this snapshot leads up to. Fails for a version that is already a release. */
    fun release(): RylweenVersion {
        check(snapshot) { "$this is a release version, but releases are cut from $SNAPSHOT_SUFFIX versions" }
        return copy(snapshot = false)
    }

    /** The snapshot of the patch release after this one. Fails for a snapshot. */
    fun nextSnapshot(): RylweenVersion {
        check(!snapshot) { "$this is already a snapshot; only a release moves on to the next snapshot" }
        return copy(patch = patch + 1, snapshot = true)
    }

    override fun toString(): String = "$major.$minor.$patch" + if (snapshot) SNAPSHOT_SUFFIX else ""

    companion object {
        const val SNAPSHOT_SUFFIX = "-SNAPSHOT"

        // Numbers without leading zeros, so every version prints back exactly as it was written.
        private val format = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)($SNAPSHOT_SUFFIX)?""")

        /** Parses `X.Y.Z` or `X.Y.Z-SNAPSHOT`, and fails for anything else. */
        fun parse(text: String): RylweenVersion =
            format
                .matchEntire(text)
                ?.destructured
                ?.let { (major, minor, patch, suffix) ->
                    RylweenVersion(major.toInt(), minor.toInt(), patch.toInt(), snapshot = suffix.isNotEmpty())
                }
                ?: throw IllegalArgumentException("version \"$text\" is neither X.Y.Z nor X.Y.Z$SNAPSHOT_SUFFIX")
    }
}

/** One step of the release cycle, from one version to the next. */
enum class RylweenVersionStep(
    private val move: (RylweenVersion) -> RylweenVersion,
) {
    /** From `X.Y.Z-SNAPSHOT` to `X.Y.Z`. */
    Release(RylweenVersion::release),

    /** From `X.Y.Z` to `X.Y.(Z+1)-SNAPSHOT`. */
    NextSnapshot(RylweenVersion::nextSnapshot),
    ;

    fun from(version: RylweenVersion): RylweenVersion = move(version)
}

/**
 * Moves `rylween.version` in [propertiesFile] one [step], rewriting only that line. The build that runs this has
 * already read the old version, so anything that should see the new one runs in a later Gradle invocation.
 */
abstract class SetRylweenVersionTask : DefaultTask() {
    /** gradle.properties, read and rewritten in place: neither an input nor an output, so the task always runs. */
    @get:Internal
    abstract val propertiesFile: RegularFileProperty

    @get:Input
    abstract val step: Property<RylweenVersionStep>

    @TaskAction
    fun moveVersion() {
        val file = propertiesFile.get().asFile
        val prefix = "$RYLWEEN_VERSION_PROPERTY="
        val lines = file.readLines()
        val current =
            lines
                .singleOrNull { line -> line.startsWith(prefix) }
                ?.let { line -> RylweenVersion.parse(line.removePrefix(prefix)) }
                ?: throw GradleException("${file.path} must set $RYLWEEN_VERSION_PROPERTY exactly once")
        val next = step.get().from(current)
        file.writeText(
            lines.joinToString(separator = "\n", postfix = "\n") { line ->
                if (line.startsWith(prefix)) "$prefix$next" else line
            },
        )
        logger.lifecycle("$RYLWEEN_VERSION_PROPERTY: $current -> $next")
    }
}

/**
 * Starts the release [workflow] on [branch] with the GitHub CLI, `gh`, which must be installed and logged in. It fails
 * first when [localVersion] is not a snapshot, so a mistake shows up here rather than minutes into the workflow. The
 * workflow checks the branch's own version again, and makes every version move itself.
 */
abstract class StartReleaseWorkflowTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        /** `rylween.version` in this checkout. */
        @get:Input
        abstract val localVersion: Property<String>

        /** The workflow file name under .github/workflows. */
        @get:Input
        abstract val workflow: Property<String>

        @get:Input
        abstract val branch: Property<String>

        /** Builds and smoke-tests everything but pushes and publishes nothing. */
        @get:Input
        abstract val dryRun: Property<Boolean>

        @TaskAction
        fun start() {
            val release = RylweenVersion.parse(localVersion.get()).release()
            execOperations.exec {
                commandLine(
                    "gh",
                    "workflow",
                    "run",
                    workflow.get(),
                    "--ref",
                    branch.get(),
                    "--raw-field",
                    "dry-run=${dryRun.get()}",
                )
            }
            logger.lifecycle(
                "Started ${if (dryRun.get()) "a dry run of " else ""}the release of $release from ${branch.get()}. " +
                    "Follow it with `gh run watch`.",
            )
        }
    }
