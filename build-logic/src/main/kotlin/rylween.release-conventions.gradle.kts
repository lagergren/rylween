// Registers the release tasks on the root project. releaseDist starts the release workflow, which moves
// rylween.version with setReleaseVersion and setNextSnapshotVersion; see rylween/gradle/RylweenReleaseTasks.kt.
import rylween.gradle.RYLWEEN_VERSION_PROPERTY
import rylween.gradle.RylweenVersionStep
import rylween.gradle.SetRylweenVersionTask
import rylween.gradle.StartReleaseWorkflowTask

val versionFile: RegularFile = layout.projectDirectory.file("gradle.properties")

tasks.register<SetRylweenVersionTask>("setReleaseVersion") {
    group = "release"
    description = "Moves rylween.version in gradle.properties from X.Y.Z-SNAPSHOT to X.Y.Z. The release workflow runs it."
    propertiesFile.set(versionFile)
    step.set(RylweenVersionStep.Release)
}

tasks.register<SetRylweenVersionTask>("setNextSnapshotVersion") {
    group = "release"
    description = "Moves rylween.version in gradle.properties from X.Y.Z to X.Y.(Z+1)-SNAPSHOT. The release workflow runs it."
    propertiesFile.set(versionFile)
    step.set(RylweenVersionStep.NextSnapshot)
}

tasks.register<StartReleaseWorkflowTask>("releaseDist") {
    group = "release"
    description =
        "Starts the release workflow on main, which releases its X.Y.Z-SNAPSHOT as X.Y.Z and moves main to " +
            "X.Y.(Z+1)-SNAPSHOT. -Prylween.release.dryRun=true builds and smoke-tests everything but publishes nothing."
    localVersion.set(providers.gradleProperty(RYLWEEN_VERSION_PROPERTY))
    workflow.set("release.yml")
    branch.set("main")
    dryRun.set(providers.gradleProperty("rylween.release.dryRun").map(String::toBoolean).orElse(false))
}
