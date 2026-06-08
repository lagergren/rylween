/** Runtime pause prompt flavor used by text screens and paged views. */

package rylween.runtime

import rylween.compiled.ExternalRuntimePrompts
import kotlin.random.Random

class PausePrompts(
    private val verbs: List<String>,
    private val returnKeyName: String,
    private val selectVerbIndex: (Int) -> Int = { size -> Random.nextInt(size) },
) {
    constructor(prompts: ExternalRuntimePrompts) : this(prompts.pauseVerbs, prompts.returnKeyName)

    fun line(action: String? = null): String {
        val verb =
            verbs
                .takeIf(List<String>::isNotEmpty)
                ?.let { options -> options[selectVerbIndex(options.size).mod(options.size)] }
                ?: "Press"
        return listOfNotNull(verb, returnKeyName, action)
            .joinToString(separator = " ", prefix = "(", postfix = ")")
    }
}

internal fun List<String>.withPausePrompt(pausePrompts: PausePrompts): List<String> =
    dropLastWhile(String::isBlank) + "" + pausePrompts.line()
