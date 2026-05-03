package rylween.app

import rylween.dsl.RylweenValidationException

internal inline fun runRylweenCli(block: () -> Unit) {
    try {
        block()
    } catch (error: RylweenValidationException) {
        System.err.println("Rylween startup failed:")
        error.diagnostics.forEach { diagnostic ->
            System.err.println("- $diagnostic")
        }
        kotlin.system.exitProcess(1)
    } catch (error: IllegalArgumentException) {
        printStartupFailure(error)
    } catch (error: IllegalStateException) {
        printStartupFailure(error)
    } catch (error: java.io.IOException) {
        printStartupFailure(error)
    }
}

private fun printStartupFailure(error: Exception): Nothing {
    System.err.println("Rylween startup failed: ${error.message ?: error::class.simpleName}")
    kotlin.system.exitProcess(1)
}
