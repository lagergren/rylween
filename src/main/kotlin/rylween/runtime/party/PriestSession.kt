// Reusable runtime session for picking a priest spell out of combat.
// Resolves the caster, picks a spell, and hands a script continuation to the
// outer service-script runner. Combat priest spells use the battle session.

package rylween.runtime.party

import rylween.runtime.*
import rylween.runtime.wilderness.WildernessDungeonHandoff

import rylween.dsl.*

enum class PriestSpellPlace(val code: Int) {
    Wilderness(0),
    Dungeon(1),
}

enum class PriestSpellStatus {
    Cast,
    Aborted,
    MageCombatOnly,
    WrongClass,
    Dead,
    InvalidCaster,
    InvalidSpell,
    NoSpells,
}

fun <T> PriestSpellStatus?.toParentStatus(cast: T, unavailable: T): T =
    when (this) {
        PriestSpellStatus.Cast -> cast
        else -> unavailable
    }

data class PriestSpellResult(
    val status: PriestSpellStatus,
    val runtime: RuntimeSessionState,
    val casterNumber: Int? = null,
    val spell: SpellId? = null,
    val dungeonHandoff: WildernessDungeonHandoff? = null,
)

sealed interface PriestSpellStep {
    data class ChooseCaster(val prompt: TextBlock, val casterKeys: List<Char>) : PriestSpellStep
    data class ChooseSpell(
        val casterNumber: Int,
        val prompt: TextBlock,
        val spellKeys: List<Char>,
    ) : PriestSpellStep
    data class Script(
        val spell: SpellModel,
        val casterNumber: Int,
        val continuation: ScriptContinuation,
    ) : PriestSpellStep
    data class Message(val text: TextBlock, val next: PriestSpellStep) : PriestSpellStep
    data class Finished(val result: PriestSpellResult) : PriestSpellStep {
        val runtime: RuntimeSessionState get() = result.runtime
    }
}

data class PriestSpellSession(
    val runtime: RuntimeSessionState,
    val spells: List<SpellModel>,
    val place: PriestSpellPlace,
    val prompts: PriestSpellPromptsModel = PriestSpellPromptsModel(),
    val step: PriestSpellStep,
    val lastResult: PriestSpellResult? = null,
) {
    val party: PartyState get() = runtime.activeParty
    private val priestSpells: List<SpellModel>
        get() = spells.filter { it.id.slug.startsWith("priest.") }.sortedBy { it.slot }

    fun apply(input: PriestSpellInput): PriestSpellSession =
        when (val current = step) {
            is PriestSpellStep.ChooseCaster -> {
                val caster = input as? PriestSpellInput.Caster
                    ?: error("priest spell session is choosing a caster")
                chooseCaster(caster.memberNumber)
            }
            is PriestSpellStep.ChooseSpell -> {
                val spell = input as? PriestSpellInput.SpellSlot
                    ?: error("priest spell session is choosing a spell")
                chooseSpell(current.casterNumber, spell.slot)
            }
            is PriestSpellStep.Message -> {
                require(input is PriestSpellInput.Continue) { "priest spell session is showing a message" }
                val nextRuntime = (current.next as? PriestSpellStep.Finished)?.runtime ?: runtime
                copy(runtime = nextRuntime, step = current.next)
            }
            is PriestSpellStep.Script -> error("priest spell script must be driven by a script runner")
            is PriestSpellStep.Finished -> error("priest spell session is already finished: $current")
        }

    fun replaceScriptContinuation(continuation: ScriptContinuation): PriestSpellSession {
        val current = step as? PriestSpellStep.Script
            ?: error("priest spell session is not running a script")
        return copy(
            runtime = continuation.runtime,
            step = current.copy(continuation = continuation),
        )
    }

    fun finishScript(
        nextRuntime: RuntimeSessionState,
        dungeonHandoff: WildernessDungeonHandoff? = null,
    ): PriestSpellSession {
        val current = step as? PriestSpellStep.Script
            ?: error("priest spell session is not running a script")
        return finish(PriestSpellStatus.Cast, nextRuntime, current.casterNumber, current.spell.id, dungeonHandoff)
    }

    private fun chooseCaster(memberNumber: Int): PriestSpellSession {
        val member = party.members.getOrNull(memberNumber - 1)
            ?: return message(PriestSpellStatus.InvalidCaster, prompts.invalidCaster, memberNumber)
        return when {
            member.pcClass == PcClass.Mage ->
                message(PriestSpellStatus.MageCombatOnly, prompts.mageCombatOnly, memberNumber)
            member.pcClass != PcClass.Priest ->
                message(PriestSpellStatus.WrongClass, prompts.wrongClass, memberNumber)
            member.dead ->
                message(PriestSpellStatus.Dead, prompts.dead, memberNumber)
            priestSpells.isEmpty() ->
                message(PriestSpellStatus.NoSpells, prompts.noSpells, memberNumber)
            else -> copy(
                step = PriestSpellStep.ChooseSpell(
                    casterNumber = memberNumber,
                    prompt = prompts.spellList(member, priestSpells, abortSlot()),
                    spellKeys = spellKeys(),
                ),
            )
        }
    }

    private fun chooseSpell(casterNumber: Int, slot: Int): PriestSpellSession {
        if (slot == abortSlot()) {
            return finish(PriestSpellStatus.Aborted, runtime, casterNumber)
        }
        val spell = priestSpells.firstOrNull { it.slot == slot }
            ?: return message(PriestSpellStatus.InvalidSpell, prompts.invalidSpell, casterNumber)
        return copy(
            step = PriestSpellStep.Script(
                spell = spell,
                casterNumber = casterNumber,
                continuation = ScriptContinuation.start(runtime, ScriptRootRef.spell(spell.id)).copy(
                    locals = mapOf(
                        "caster_idx" to casterNumber,
                    ),
                ),
            ),
        )
    }

    private fun message(
        status: PriestSpellStatus,
        text: TextBlock,
        casterNumber: Int? = null,
    ): PriestSpellSession {
        val finished = PriestSpellStep.Finished(result(status, runtime, casterNumber))
        return copy(step = PriestSpellStep.Message(text, finished), lastResult = finished.result)
    }

    private fun finish(
        status: PriestSpellStatus,
        nextRuntime: RuntimeSessionState,
        casterNumber: Int? = null,
        spell: SpellId? = null,
        dungeonHandoff: WildernessDungeonHandoff? = null,
    ): PriestSpellSession {
        val result = result(status, nextRuntime, casterNumber, spell, dungeonHandoff)
        return copy(runtime = nextRuntime, step = PriestSpellStep.Finished(result), lastResult = result)
    }

    private fun result(
        status: PriestSpellStatus,
        nextRuntime: RuntimeSessionState,
        casterNumber: Int? = null,
        spell: SpellId? = null,
        dungeonHandoff: WildernessDungeonHandoff? = null,
    ): PriestSpellResult =
        PriestSpellResult(status, nextRuntime, casterNumber, spell, dungeonHandoff)

    private fun abortSlot(): Int =
        priestSpells.maxOfOrNull { it.slot }?.plus(1) ?: 1

    private fun spellKeys(): List<Char> =
        (priestSpells.map { it.slot } + abortSlot()).map(Int::digitKey)

    companion object {
        fun start(
            runtime: RuntimeSessionState,
            spells: List<SpellModel>,
            place: PriestSpellPlace,
            prompts: PriestSpellPromptsModel = PriestSpellPromptsModel(),
        ): PriestSpellSession =
            PriestSpellSession(
                runtime = runtime,
                spells = spells,
                place = place,
                prompts = prompts,
                step = PriestSpellStep.ChooseCaster(
                    prompts.who,
                    runtime.activeParty.members.indices.map { (it + 1).digitKey() },
                ),
            )
    }
}
