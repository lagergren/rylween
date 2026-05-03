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

    fun viewPartyPending(): TextBlock =
        inlineText("Party viewing is not", "ported yet.")

    fun dropPending(): TextBlock =
        inlineText("Dropping items is not", "ported yet.")

    fun dungeonViewPartyPending(): TextBlock =
        inlineText("Dungeon party view is", "not ported yet.")

    fun dungeonDropPending(): TextBlock =
        inlineText("Dropping items is not", "ported yet.")

    fun wildernessStatusLine(status: WildernessSessionResultStatus): List<String>? =
        when (status) {
            WildernessSessionResultStatus.SaveUnavailable -> listOf("Save not wired yet.")
            WildernessSessionResultStatus.JournalUnavailable -> listOf("Journal not ported yet.")
            WildernessSessionResultStatus.HuntUnavailable -> listOf("Hunt not ported yet.")
            WildernessSessionResultStatus.ViewPartyUnavailable -> listOf("Party view not ported yet.")
            WildernessSessionResultStatus.DropUnavailable -> listOf("Drop not ported yet.")
            WildernessSessionResultStatus.CampUnavailable -> listOf("Camp not ported yet.")
            else -> null
        }
}
