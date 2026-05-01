plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "org.rylween"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val kotlinCompiler by configurations.creating
val scratchRuntime by configurations.creating

dependencies {
    val kotlinVersion = libs.versions.kotlin.get()
    kotlinCompiler("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    kotlinCompiler("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$kotlinVersion")
    kotlinCompiler("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
    scratchRuntime("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    scratchRuntime("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion")
    scratchRuntime("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
}

tasks.jar {
    archiveBaseName = "rylween-dsl"
}

val validateScratch by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Compiles and executes the scratch Kotlin DSL source against the built DSL jar."

    dependsOn(tasks.jar)
    classpath = kotlinCompiler
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    val scratchClasspath = files(tasks.jar.flatMap { it.archiveFile }) + scratchRuntime
    args(
        "-no-stdlib",
        "-no-reflect",
        "-classpath",
        scratchClasspath.asPath,
        "-script",
        layout.projectDirectory.file("rylween-full-scratch.dsl.kts").asFile.absolutePath,
    )
}

tasks.check {
    dependsOn(validateScratch)
}
