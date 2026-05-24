/** Static screen, score report, and hall-of-heroes presentation helpers. */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalStaticScreen
import rylween.dsl.impl.PcClass

private const val SCORE_WIDTH = 76
private const val SCORE_REPORT = "Score"
private const val VERBOSE_SCORE_REPORT = "VerboseScore"
private const val HALL_OF_HEROES_REPORT = "HallOfHeroes"

fun runtimeScreenSlug(value: String): String =
    when (val normalized = value.lowercase().replace('-', '_')) {
        "vscore", "verbose", "verbose_score" -> "verbose_score"
        "hall", "hall_of_heroes" -> "heroes"
        else -> normalized
    }

fun runtimeStaticScreenText(
    content: CompiledContentBundle,
    systems: ExternalRuntimeSystems,
    screen: ExternalStaticScreen,
    runtime: RuntimeState,
): List<String> =
    buildList {
        screen.resource?.let { resource -> addAll(runtimeResourceText(resource).lines()) }
        screen.runtimeReport?.let { report -> addAll(runtime.runtimeReportLines(content, systems, report)) }
        if (screen.commandHelp) {
            add("Compiled command groups: ${systems.commandGroups.joinToString()}")
        }
    }

fun runtimeResourceText(resource: String): String {
    val stream =
        Thread.currentThread().contextClassLoader.getResourceAsStream(resource)
            ?: error("missing runtime resource $resource")
    return stream.bufferedReader().readText()
}

private fun RuntimeState.runtimeReportLines(
    content: CompiledContentBundle,
    systems: ExternalRuntimeSystems,
    report: String,
): List<String> =
    when (report) {
        SCORE_REPORT -> scoreEntries(content, systems).summaryScoreLines()
        VERBOSE_SCORE_REPORT -> scoreEntries(content, systems).verboseScoreLines()
        HALL_OF_HEROES_REPORT -> hallOfHeroesLines(content, systems)
        else -> emptyList()
    }

private fun RuntimeState.scoreEntries(
    content: CompiledContentBundle,
    systems: ExternalRuntimeSystems,
): List<ScoreEntry> =
    scoreSourceParties()
        .sortedWith(
            compareByDescending<RuntimeScoreParty> {
                it.averageExperience
            }.thenByDescending { it.averageLevel }.thenBy { it.partyName },
        ).mapIndexed { index, party ->
            ScoreEntry(
                rank = index + 1,
                partyName = party.partyName,
                leaderName = party.leaderName,
                classes = party.classes,
                kills = party.kills,
                averageExperience = party.averageExperience,
                averageLevel = party.averageLevel,
                powerItems =
                    systems.score.powerItems
                        .filter { item -> party.milestones.any { milestone -> content.slug(milestone) == item.milestone } }
                        .sortedWith(compareBy({ it.sourceOrder ?: Int.MAX_VALUE }, { it.itemName }))
                        .map { item -> item.itemName },
            )
        }

private fun RuntimeState.scoreSourceParties(): List<RuntimeScoreParty> =
    scoreParties.takeIf(List<RuntimeScoreParty>::isNotEmpty)
        ?: listOf(party.toRuntimeScoreParty(activeUserId ?: "current"))

private fun List<ScoreEntry>.summaryScoreLines(): List<String> =
    buildList {
        addAll(scoreHeaderLines("ADVENTURERS OF RYLWEEN - PARTY STATUS AND SCORES"))
        add("")
        add("        #     Party Name                    Experience       Level")
        add("")
        this@summaryScoreLines.forEach { entry ->
            add(
                "        ${entry.rank.toString().padStart(2)}.    " +
                    "${entry.partyName.padEnd(30)}  " +
                    "${entry.averageExperience.toString().padStart(6)}          " +
                    entry.averageLevel.toString().padStart(3),
            )
        }
        add("")
    }

private fun List<ScoreEntry>.verboseScoreLines(): List<String> =
    buildList {
        addAll(scoreHeaderLines("ADVENTURERS OF RYLWEEN - PARTY STATUS AND SCORES"))
        add("")
        this@verboseScoreLines.forEach { entry ->
            add("${entry.rank.toString().padStart(2)}. ${entry.partyName.padEnd(30)}")
            add(" +${"-".repeat(SCORE_WIDTH - 2)}+")
            add("  Leader             : ${entry.leaderName}")
            add("  Classes            : ${entry.classes.joinToString(", ") { it.scoreName() }}")
            add("  Confirmed Kills    : ${entry.kills.toString().padEnd(4)}")
            add(
                "  Average Experience : ${
                    entry.averageExperience.toString().padEnd(
                        6,
                    )
                } (level ${entry.averageLevel.toString().padEnd(3)})",
            )
            add("")
            add("  Power Items        :")
            add("")
            if (entry.powerItems.isEmpty()) add("  None.") else entry.powerItems.forEach { item -> add("  $item") }
            add(" +${"-".repeat(SCORE_WIDTH - 2)}+")
            add("")
        }
    }

private fun RuntimeState.hallOfHeroesLines(
    content: CompiledContentBundle,
    systems: ExternalRuntimeSystems,
): List<String> =
    buildList {
        addAll(scoreHeaderLines("ADVENTURERS OF RYLWEEN - HALL OF NATIONAL HEROES"))
        add("")
        val completedBy = systems.score.completedBy
        val hasCompleted = completedBy != null && party.milestones.any { milestone -> content.slug(milestone) == completedBy }
        val heroes =
            hallOfHeroes.takeIf(List<RuntimeHeroEntry>::isNotEmpty)
                ?: if (hasCompleted) listOf(RuntimeHeroEntry(activeUserId ?: "current", party.name)) else emptyList()
        if (heroes.isNotEmpty()) {
            heroes.forEach { hero ->
                add("  ${systems.score.hallOfHeroesEntryText.replace("{party}", hero.partyName)}")
            }
        } else {
            add("  ${systems.score.hallOfHeroesEmptyText}")
        }
        add("")
    }

private fun scoreHeaderLines(title: String): List<String> =
    listOf(
        " +${"=".repeat(SCORE_WIDTH - 2)}+",
        " I${title.centered(SCORE_WIDTH - 2)}I",
        " +${"=".repeat(SCORE_WIDTH - 2)}+",
    )

private fun String.centered(width: Int): String {
    if (length >= width) return take(width)
    val left = (width - length) / 2
    val right = width - length - left
    return " ".repeat(left) + this + " ".repeat(right)
}

private fun PcClass.scoreName(): String =
    when (this) {
        PcClass.Fighter -> "Fighter"
        PcClass.Thief -> "Thief"
        PcClass.Mage -> "Mage"
        PcClass.Priest -> "Priest"
        PcClass.Bard -> "Bard"
        PcClass.Ranger -> "Ranger"
    }

private data class ScoreEntry(
    val rank: Int,
    val partyName: String,
    val leaderName: String,
    val classes: List<PcClass>,
    val kills: Int,
    val averageExperience: Int,
    val averageLevel: Int,
    val powerItems: List<String>,
)
