package rylween.frontend.terminal

import rylween.dsl.GameModel
import rylween.dsl.PcClass
import rylween.dsl.StaticScreenId
import rylween.dsl.StaticScreenRuntimeReport
import rylween.runtime.RuntimeSessionState
import rylween.runtime.score.HallOfHeroesReport
import rylween.runtime.score.ScoreReport
import rylween.runtime.score.hallOfHeroesReport
import rylween.runtime.score.scoreReport

private const val ScoreHeader: String = "ADVENTURERS OF RYLWEEN - PARTY STATUS AND SCORES"
private const val HallOfHeroesHeader: String = "ADVENTURERS OF RYLWEEN - HALL OF NATIONAL HEROES"
private const val ScoreWidth: Int = 76

fun GameModel.staticScreenRequiresRuntimeState(id: StaticScreenId): Boolean =
    visuals.staticScreens.singleOrNull { screen -> screen.id == id }?.runtimeReport != null

internal fun StaticScreenRuntimeReport.renderRuntimeStaticScreenPages(
    content: GameModel,
    runtime: RuntimeSessionState,
    maxLinesPerPage: Int,
): List<List<String>> =
    when (this) {
        StaticScreenRuntimeReport.Score -> runtime.scoreReport(content).summaryScoreLines().chunked(maxLinesPerPage)
        StaticScreenRuntimeReport.VerboseScore -> runtime.scoreReport(content).verboseScoreLines().chunked(maxLinesPerPage)
        StaticScreenRuntimeReport.HallOfHeroes -> runtime.hallOfHeroesReport(content)
            .hallOfHeroesLines()
            .chunked(maxLinesPerPage)
    }

private fun ScoreReport.summaryScoreLines(): List<String> =
    buildList {
        addAll(scoreHeaderLines())
        add("")
        add("        #     Party Name                    Experience       Level")
        add("")
        entries.forEach { entry ->
            add(
                "        ${entry.rank.toString().padStart(2)}.    " +
                    "${entry.partyName.padEnd(30)}  " +
                    "${entry.averageExperience.toString().padStart(6)}          " +
                    entry.averageLevel.toString().padStart(3),
            )
        }
        add("")
    }

private fun ScoreReport.verboseScoreLines(): List<String> =
    buildList {
        addAll(scoreHeaderLines())
        add("")
        entries.forEach { entry ->
            add("${entry.rank.toString().padStart(2)}. ${entry.partyName.padEnd(30)}")
            add(" +${"-".repeat(ScoreWidth - 2)}+")
            add("  Leader             : ${entry.leaderName}")
            add("  Classes            : ${entry.classes.joinToString(", ") { it.scoreName() }}")
            add("  Confirmed Kills    : ${entry.kills.toString().padEnd(4)}")
            add("  Average Experience : ${entry.averageExperience.toString().padEnd(6)} (level ${entry.averageLevel.toString().padEnd(3)})")
            add("")
            add("  Power Items        :")
            add("")
            if (entry.powerItems.isEmpty()) {
                add("  None.")
            } else {
                entry.powerItems.forEach { item -> add("  ${item.name}") }
            }
            add(" +${"-".repeat(ScoreWidth - 2)}+")
            add("")
        }
    }

private fun HallOfHeroesReport.hallOfHeroesLines(): List<String> =
    buildList {
        addAll(framedHeaderLines(HallOfHeroesHeader))
        add("")
        if (entries.isEmpty()) {
            add("  No party has defeated Baron Ydarg yet.")
        } else {
            entries.forEach { entry ->
                add("  ${entry.partyName} defeated Baron Ydarg")
            }
        }
        add("")
    }

private fun scoreHeaderLines(): List<String> =
    framedHeaderLines(ScoreHeader)

private fun framedHeaderLines(title: String): List<String> =
    listOf(
        " +${"=".repeat(ScoreWidth - 2)}+",
        " I${title.centered(ScoreWidth - 2)}I",
        " +${"=".repeat(ScoreWidth - 2)}+",
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
