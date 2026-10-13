import org.gradle.api.tasks.testing.Test
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

// Precompiled script plugins get no type-safe `libs` accessors, so look the catalog up by name.
private val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
private val kotlinSourceDirs = listOf("src/main/kotlin", "src/app/kotlin", "src/test/kotlin")

ktlint {
    version.set(libs.findVersion("ktlint").get().requiredVersion)
    ignoreFailures.set(true)
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
    }
    filter {
        // Skip generated catalog/harness sources (we don't author those).
        exclude { entry -> entry.file.toString().contains("${File.separator}build${File.separator}generated${File.separator}") }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val cpdClasspath: Configuration = configurations.create("cpdClasspath")

dependencies {
    cpdClasspath(libs.findLibrary("pmd-cli").get())
    cpdClasspath(libs.findLibrary("pmd-kotlin").get())
    "testImplementation"(kotlin("test"))
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

val cpdCheck: TaskProvider<JavaExec> =
    tasks.register<JavaExec>("cpdCheck") {
        group = "verification"
        description = "Runs PMD CPD duplicate-detection over all Kotlin source sets."
        classpath = cpdClasspath
        mainClass.set("net.sourceforge.pmd.cli.PmdCli")
        val reportFile = layout.buildDirectory.file("reports/cpd/cpdCheck.text").get().asFile
        val sources = kotlinSourceDirs
            .map { layout.projectDirectory.dir(it).asFile }
            .filter { it.exists() }
        inputs.files(sources).withPropertyName("sources")
        outputs.file(reportFile).withPropertyName("report")
        doFirst { reportFile.parentFile.mkdirs() }
        args(
            "cpd",
            "--language",
            "kotlin",
            "--minimum-tokens",
            "80",
            "--format",
            "text",
            "--no-fail-on-violation",
            "--report-file",
            reportFile.path,
        )
        sources.forEach { args("--dir", it.path) }
        isIgnoreExitValue = true
    }

val ktlintCheck: TaskProvider<Task> = tasks.named("ktlintCheck")

val qaCheck: TaskProvider<Task> =
    tasks.register("qaCheck") {
        group = "verification"
        description = "Runs all code-quality checks (ktlint + cpd)."
        dependsOn(ktlintCheck, cpdCheck)
    }
