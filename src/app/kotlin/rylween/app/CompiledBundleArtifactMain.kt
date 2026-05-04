@file:Suppress("ReplacePrintlnWithLogging", "UseOfSystemOutOrSystemErr")

package rylween.app

import rylween.compiled.loadCompiledGame
import rylween.compiled.toCompiledGameBundle
import rylween.compiled.writeNativeSmokeArtifact
import rylween.game.RylweenCompiledGame
import java.io.File

object RylweenCompiledBundleArtifactMain {
    @JvmStatic
    fun main(args: Array<String>): Unit = runRylweenCli {
        val output = File(args.singleOrNull() ?: "build/compiled/rylween-native-smoke.rsmoke")
        val bundle = RylweenCompiledGame.nextRylween.toCompiledGameBundle()
        bundle.loadCompiledGame()
        bundle.writeNativeSmokeArtifact(output)
        println("Wrote native smoke bundle: ${output.path}")
    }
}
