package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledPartyMember
import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeInputBinding
import rylween.compiled.ExternalRuntimeSystems
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.WildernessFatigueMatch

fun runtimeWildernessContextLines(
    content: CompiledContentBundle,
    systems: ExternalRuntimeSystems,
    runtime: RuntimeState,
    world: WorldMap,
): List<String> =
    buildList {
        add("Wilderness")
        world.terrainAt(runtime.party.position)?.let { terrain ->
            add("Terrain: ${content.runtimeSlug(terrain.id).readableSlug()}")
        }
        content.towns.firstOrNull { town -> town.position == runtime.party.position }?.let { town ->
            add("At: ${content.strings.value(town.nameString)}")
            systems.wildernessEnterCommand()?.enterPromptLine()?.let(::add)
        }
    }

fun RuntimeState.runtimeStatusLines(
    content: CompiledContentBundle,
    systems: ExternalRuntimeSystems,
): List<String> {
    val food = party.resources.food.coerceAtLeast(0)
    return buildList {
        add("TIME ${hour.mod(24).toString().padStart(2, '0')}:00")
        add("ST ${runtimeStatusLabel(systems)}")
        add("FD ${food.toString().padStart(4)}")
        add("MV ${party.resources.timeLeft.toString().padStart(3)}")
        add("AU ${party.resources.gold.toString().padStart(6)}")
        party.members.take(4).forEachIndexed { index, member ->
            add("${index + 1} ${member.runtimeStatusText(content)}")
        }
    }
}

fun RuntimeState.runtimeStatusLabel(systems: ExternalRuntimeSystems): String =
    runtimeFoodStatusLabel().takeUnless { label -> label == "OK" }
        ?: runtimeFatigueStatusLabel(systems)
        ?: "OK"

fun RuntimeState.runtimeFoodStatusLabel(): String {
    val food = party.resources.food.coerceAtLeast(0)
    if (food == 0) return "STARVING"
    if (food < party.members.count { member -> !member.dead }.coerceAtLeast(1) * 3) return "HUNGRY"
    return "OK"
}

private fun RuntimeState.runtimeFatigueStatusLabel(systems: ExternalRuntimeSystems): String? {
    val restedHours = party.resources.restedHours
    val fatigueRules =
        systems.realm.upkeep.fatigue
            .filter { rule -> rule.statusLabel != null }
    val belowRule =
        fatigueRules
            .filter { rule -> rule.match == WildernessFatigueMatch.LessThan && restedHours < rule.threshold }
            .minByOrNull { rule -> rule.threshold }
    val thresholdRule =
        fatigueRules
            .filter { rule -> rule.match == WildernessFatigueMatch.Equal && restedHours <= rule.threshold }
            .minByOrNull { rule -> rule.threshold }
    return belowRule?.statusLabel ?: thresholdRule?.statusLabel
}

fun CompiledPartyMember.runtimeStatusText(content: CompiledContentBundle): String =
    buildString {
        append(content.strings.value(nameString))
        append(' ')
        append(pcClass.runtimeShortLabel())
        append(' ')
        append("H")
        append(hitPoints.current)
        append('/')
        append(hitPoints.max)
        if (pcClass == PcClass.Mage || pcClass == PcClass.Priest) {
            append(" M")
            append(mana.current)
            append('/')
            append(mana.max)
        }
        if (dead) append(" DEAD")
    }

private fun ExternalRuntimeSystems.wildernessEnterCommand(): ExternalRuntimeCommand? =
    commands["wilderness"].orEmpty().firstOrNull { command -> command.action == "enter" }

private fun ExternalRuntimeCommand.enterPromptLine(): String {
    val label = label ?: binding.label
    return "Press ${binding.primary.uppercase()} to ${label.replaceFirstChar(Char::lowercaseChar)}."
}

fun ExternalRuntimeCommand.runtimeMnemonicLine(): String = binding.runtimeMnemonicLine(label ?: binding.label)

private fun ExternalRuntimeInputBinding.runtimeMnemonicLine(text: String): String {
    val key = label.takeIf { value -> value.isNotBlank() } ?: primary.uppercase()
    return "($key)${text.afterMnemonic(primary)}"
}

private fun String.afterMnemonic(key: String): String =
    removePrefix(key)
        .removePrefix(key.uppercase())
        .let { text -> if (text == this) " $this" else text }

private fun PcClass.runtimeShortLabel(): String =
    when (this) {
        PcClass.Fighter -> "FIG"
        PcClass.Thief -> "THF"
        PcClass.Mage -> "MAG"
        PcClass.Priest -> "PRI"
        PcClass.Bard -> "BRD"
        PcClass.Ranger -> "RAN"
    }

private fun CompiledContentBundle.runtimeSlug(ref: CompiledHandleRef): String = strings.value(handles.handle(ref).slugString)
