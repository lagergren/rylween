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
