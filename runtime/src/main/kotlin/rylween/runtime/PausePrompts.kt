package rylween.runtime

import rylween.compiled.ExternalRuntimePrompts

internal class PausePrompts(
    private val verbs: List<String>,
    private val returnKeyName: String,
) {
    private var index = 0

    constructor(prompts: ExternalRuntimePrompts) : this(prompts.pauseVerbs, prompts.returnKeyName)

    fun line(): String {
        val verb = verbs.getOrNull(index.mod(verbs.size)) ?: "Press"
        index++
        return "($verb $returnKeyName)"
    }
}

internal fun List<String>.withPausePrompt(pausePrompts: PausePrompts): List<String> =
    withoutAuthoredAnyKeyPrompt() + "" + pausePrompts.line()

private fun List<String>.withoutAuthoredAnyKeyPrompt(): List<String> {
    var lines = this
    while (lines.lastOrNull()?.isBlank() == true) lines = lines.dropLast(1)
    return if (lines.lastOrNull()?.trim() in authoredAnyKeyPrompts) {
        lines.dropLast(1)
    } else {
        lines
    }
}

private val authoredAnyKeyPrompts = setOf("Press any key.", "Press any key")
