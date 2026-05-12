package rylween.dsl

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

/**
 * IntelliJ/Kotlin script definition for authored Rylween game files.
 *
 * Files ending in `.dsl.kts`, such as `game/src/main/dsl/RylweenGame.dsl.kts`,
 * are inputs to the game-content compiler rather than normal Kotlin source.
 * Registering this template lets the editor resolve the DSL imports and apply
 * script semantics without putting those scripts in a Kotlin source root.
 */
@KotlinScript(
    displayName = "Rylween Game DSL",
    fileExtension = "dsl.kts",
    compilationConfiguration = RylweenDslScriptConfiguration::class,
)
abstract class RylweenDslScript(
    val args: Array<String>,
)

/** Compilation classpath and default imports used by the Rylween DSL editor. */
object RylweenDslScriptConfiguration : ScriptCompilationConfiguration(
    {
        defaultImports(
            "rylween.dsl.*",
            "rylween.dsl.impl.*",
            "rylween.game.*",
        )
        jvm {
            dependenciesFromClassContext(RylweenDslScript::class, wholeClasspath = true)
            dependenciesFromCurrentContext(wholeClasspath = true)
        }
    },
)
