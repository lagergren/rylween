/**
 * Command-line entry points for writing, checking, and inspecting compiled
 * bundle artifacts. These tools bridge `game/RylweenCompiledGame` to the
 * compiled artifact readers in `compiled/ExternalBundleArtifact.kt` and the
 * smoke artifact in `compiled/NativeSmokeArtifact.kt`.
 */
@file:Suppress("ReplacePrintlnWithLogging", "UseOfSystemOutOrSystemErr")

package rylween.app

import rylween.compiled.ExternalBundleSectionId
import rylween.compiled.inspectExternalBundleArtifact
import rylween.compiled.loadCompiledGame
import rylween.compiled.readExternalBundleArtifact
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeGame
import rylween.compiled.readExternalRuntimeSystemsSummary
import rylween.compiled.toCompiledGameBundle
import rylween.compiled.writeExternalBundleArtifact
import rylween.compiled.writeNativeSmokeArtifact
import rylween.game.RylweenCompiledGame
import java.io.File

object RylweenCompiledBundleArtifactMain {
    @JvmStatic
    fun main(args: Array<String>): Unit =
        runRylweenCli {
            val output = File(args.singleOrNull() ?: "build/compiled/rylween-native-smoke.rsmoke")
            val bundle = RylweenCompiledGame.nextRylween.toCompiledGameBundle()
            bundle.loadCompiledGame()
            bundle.writeNativeSmokeArtifact(output)
            println("Wrote native smoke bundle: ${output.path}")
        }
}

object RylweenExternalBundleArtifactMain {
    @JvmStatic
    fun main(args: Array<String>): Unit =
        runRylweenCli {
            val output = File(args.singleOrNull() ?: "build/compiled/rylween-bundle.json")
            val game = RylweenCompiledGame.nextRylween
            game.toCompiledGameBundle().loadCompiledGame()
            game.writeExternalBundleArtifact(output)
            println("Wrote external compiled bundle: ${output.path}")
        }
}

object RylweenExternalBundleCheckMain {
    @JvmStatic
    fun main(args: Array<String>): Unit =
        runRylweenCli {
            val input = File(args.singleOrNull() ?: "build/compiled/rylween-bundle.json")
            val summary = input.readExternalBundleArtifact()
            val runtimeSystems = input.readExternalRuntimeSystemsSummary()
            val materialized = input.readExternalCompiledGameBundle()
            val runtimeGame = input.readExternalRuntimeGame()
            println(
                "compiled bundle ok: strings=${summary.section(ExternalBundleSectionId.Strings).rowCount} " +
                    "handles=${summary.section(ExternalBundleSectionId.Handles).rowCount} " +
                    "scripts=${materialized.content.scripts.roots.size} " +
                    "states=${materialized.states.size} " +
                    "runtimeStates=${runtimeGame.states.size} " +
                    "runtimeSystems=${runtimeSystems.schemaVersion} " +
                    "castles=${runtimeSystems.castles.size}",
            )
        }
}

object RylweenExternalBundleInspectMain {
    @JvmStatic
    fun main(args: Array<String>): Unit =
        runRylweenCli {
            val input = File(args.getOrNull(0) ?: "build/compiled/rylween-bundle.json")
            val output = args.getOrNull(1)?.let(::File)
            val inspection = input.inspectExternalBundleArtifact()
            if (output == null) {
                print(inspection)
            } else {
                output.parentFile?.mkdirs()
                output.writeText(inspection)
                println("Wrote external compiled bundle inspection: ${output.path}")
            }
        }
}
