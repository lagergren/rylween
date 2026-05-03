package rylween.runtime

import rylween.dsl.TextBlock
import rylween.dsl.inlineText

object RuntimeUnavailableText {
    fun unknownTown(): TextBlock =
        inlineText("This town has no town declaration yet.")

    fun hutSceneMissing(): TextBlock =
        inlineText("This hut has no scene declaration yet.")

    fun castlePending(): TextBlock =
        inlineText("Castle entry is not ported yet.")

    fun savePending(): TextBlock =
        inlineText("Save is not wired", "in the Kotlin runtime yet.")

    fun journalPending(): TextBlock =
        inlineText("Journal viewing is not", "ported yet.")

    fun huntPending(): TextBlock =
        inlineText("Hunting is not", "ported yet.")

}
