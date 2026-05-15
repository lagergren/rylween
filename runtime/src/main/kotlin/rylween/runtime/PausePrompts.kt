/** Runtime pause prompt rotation used by text screens and paged views. */

package rylween.runtime

import rylween.compiled.ExternalRuntimePrompts

class PausePrompts(
    private val verbs: List<String>,
    private val returnKeyName: String,
) {
    private var index = 0

    constructor(prompts: ExternalRuntimePrompts) : this(prompts.pauseVerbs, prompts.returnKeyName)

    fun line(action: String? = null): String {
        val verb = verbs.getOrNull(index.mod(verbs.size)) ?: "Press"
        index++
        return listOfNotNull(verb, returnKeyName, action)
            .joinToString(separator = " ", prefix = "(", postfix = ")")
    }
}

internal fun List<String>.withPausePrompt(pausePrompts: PausePrompts): List<String> =
    dropLastWhile(String::isBlank) + "" + pausePrompts.line()
