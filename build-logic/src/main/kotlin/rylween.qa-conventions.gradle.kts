import org.gradle.api.tasks.testing.Test
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

private val ktlintToolVersion = "1.6.0"
private val pmdVersion = "7.13.0"
private val kotlinSourceDirs = listOf("src/main/kotlin", "src/app/kotlin", "src/test/kotlin")

ktlint {
    version.set(ktlintToolVersion)
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

val cpdClasspath: Configuration by configurations.creating

dependencies {
    cpdClasspath("net.sourceforge.pmd:pmd-cli:$pmdVersion")
    cpdClasspath("net.sourceforge.pmd:pmd-kotlin:$pmdVersion")
}

val cpdCheck: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
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

val qaCheck: TaskProvider<Task> by tasks.registering {
    group = "verification"
    description = "Runs all code-quality checks (ktlint + cpd)."
    dependsOn(tasks.named("ktlintCheck"), cpdCheck)
}
