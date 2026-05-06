/**
 * Backend-neutral character creation state machine.
 *
 * `engine/Startup.kt` builds the pending runtime snapshot, this session gathers
 * party/member choices from a frontend, and `frontend/terminal/TerminalAdapters.kt`
 * renders the prompts for the terminal host.
 */
package rylween.runtime.town

import rylween.dsl.CharacterClassOptionModel
import rylween.dsl.CharacterCreationModel
import rylween.dsl.CharacterCreationStat
import rylween.dsl.CharacterRaceOptionModel
import rylween.dsl.DslDefaultItems
import rylween.dsl.EquipmentState
import rylween.dsl.Gender
import rylween.dsl.InventorySaveSlotMax
import rylween.dsl.InventorySlotState
import rylween.dsl.Mana
import rylween.dsl.PartyCounters
import rylween.dsl.PartyId
import rylween.dsl.PartyMemberState
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.Race
import rylween.dsl.RealmState
import rylween.dsl.RuntimeContent
import rylween.dsl.RuntimeMode
import rylween.dsl.TextBlock
import rylween.dsl.Wilderness
import rylween.dsl.of
import rylween.dsl.partyId
import rylween.dsl.partyName
import rylween.dsl.random
import rylween.runtime.CharacterCreationInput
import rylween.runtime.ReferenceRylweenRandom
import rylween.runtime.RuntimeSessionState
import rylween.runtime.RylweenRandomPort
import rylween.runtime.facing
import rylween.runtime.party

/** Rolled starting stats for one member candidate. */
data class CharacterCreationStats(
    val weaponSkill: Int,
    val health: Int,
    val wisdom: Int,
    val charisma: Int,
) {
    fun value(stat: CharacterCreationStat): Int =
        when (stat) {
            CharacterCreationStat.WeaponSkill -> weaponSkill
            CharacterCreationStat.Health -> health
            CharacterCreationStat.Wisdom -> wisdom
            CharacterCreationStat.Charisma -> charisma
        }
}

/** Stable result tokens emitted by [CharacterCreationSession]. */
enum class CharacterCreationStatus {
    Aborted,
    ClassRejected,
    MemberAccepted,
    Finished,
}

/** Last transition result for character creation. */
data class CharacterCreationResult(
    val status: CharacterCreationStatus,
    val memberNumber: Int? = null,
    val member: PartyMemberState? = null,
    val runtime: RuntimeSessionState? = null,
)

/** Current prompt or terminal state requested by [CharacterCreationSession]. */
sealed interface CharacterCreationStep {
    object PartyName : CharacterCreationStep

    data class Stats(
        val memberNumber: Int,
        val stats: CharacterCreationStats,
    ) : CharacterCreationStep

    data class MemberName(
        val memberNumber: Int,
        val stats: CharacterCreationStats,
    ) : CharacterCreationStep

    data class GenderPrompt(
        val memberNumber: Int,
        val stats: CharacterCreationStats,
        val memberName: String,
    ) : CharacterCreationStep

    data class RacePrompt(
        val memberNumber: Int,
        val stats: CharacterCreationStats,
        val memberName: String,
        val gender: Gender,
        val options: List<CharacterRaceOptionModel>,
    ) : CharacterCreationStep

    data class ClassPrompt(
        val memberNumber: Int,
        val stats: CharacterCreationStats,
        val memberName: String,
        val gender: Gender,
        val race: Race,
        val options: List<CharacterClassOptionModel>,
    ) : CharacterCreationStep

    data class ClassRejected(
        val text: TextBlock,
        val next: Stats,
    ) : CharacterCreationStep

    data class Finished(
        val runtime: RuntimeSessionState,
    ) : CharacterCreationStep

    object Aborted : CharacterCreationStep
}

/** Pure state machine that builds the initial party from authored creation rules. */
data class CharacterCreationSession(
    val partyId: PartyId,
    val playerName: String,
    val baseRealm: RealmState,
    val content: RuntimeContent,
    val rngState: Long?,
    val partyName: String? = null,
    val members: List<PartyMemberState> = emptyList(),
    val step: CharacterCreationStep = CharacterCreationStep.PartyName,
    val lastResult: CharacterCreationResult? = null,
    private val random: RylweenRandomPort = ReferenceRylweenRandom,
) {
    private val rules: CharacterCreationModel
        get() = content.characterCreation ?: error("runtime content has no character-creation rules")

    fun apply(input: CharacterCreationInput): CharacterCreationSession =
        when (input) {
            is CharacterCreationInput.PartyName -> choosePartyName(input.name)
            is CharacterCreationInput.RerollStats -> chooseReroll(input.reroll)
            is CharacterCreationInput.MemberName -> chooseMemberName(input.name)
            is CharacterCreationInput.GenderChoice -> chooseGender(input.gender)
            is CharacterCreationInput.RaceChoice -> chooseRace(input.race)
            is CharacterCreationInput.ClassChoice -> chooseClass(input.pcClass)
            CharacterCreationInput.Continue -> continueRejectedClass()
        }

    private fun choosePartyName(name: String): CharacterCreationSession {
        if (step !is CharacterCreationStep.PartyName) return this
        val normalized = name.normalizedCreationName()
        if (normalized.isBlank()) return abort()
        return nextStats(memberNumber = 1).copy(partyName = normalized)
    }

    private fun chooseReroll(reroll: Boolean): CharacterCreationSession {
        val current = step as? CharacterCreationStep.Stats ?: return this
        return if (reroll) {
            nextStats(current.memberNumber)
        } else {
            copy(step = CharacterCreationStep.MemberName(current.memberNumber, current.stats), lastResult = null)
        }
    }

    private fun chooseMemberName(name: String): CharacterCreationSession {
        val current = step as? CharacterCreationStep.MemberName ?: return this
        val normalized = name.normalizedCreationName()
        if (normalized.isBlank()) return abort()
        return copy(
            step = CharacterCreationStep.GenderPrompt(current.memberNumber, current.stats, normalized),
            lastResult = null,
        )
    }

    private fun chooseGender(gender: Gender): CharacterCreationSession {
        val current = step as? CharacterCreationStep.GenderPrompt ?: return this
        return copy(
            step =
                CharacterCreationStep.RacePrompt(
                    memberNumber = current.memberNumber,
                    stats = current.stats,
                    memberName = current.memberName,
                    gender = gender,
                    options = rules.races,
                ),
            lastResult = null,
        )
    }

    private fun chooseRace(race: Race): CharacterCreationSession {
        val current = step as? CharacterCreationStep.RacePrompt ?: return this
        if (rules.races.none { it.race == race }) return this
        return copy(
            step =
                CharacterCreationStep.ClassPrompt(
                    memberNumber = current.memberNumber,
                    stats = current.stats,
                    memberName = current.memberName,
                    gender = current.gender,
                    race = race,
                    options = rules.classes,
                ),
            lastResult = null,
        )
    }

    private fun chooseClass(pcClass: PcClass): CharacterCreationSession {
        val current = step as? CharacterCreationStep.ClassPrompt ?: return this
        val option = rules.classes.firstOrNull { it.klass == pcClass } ?: return this
        val failure = option.failureFor(current.stats)
        if (failure != null) {
            val next = nextStats(current.memberNumber)
            return next.copy(
                step = CharacterCreationStep.ClassRejected(failure, next.step as CharacterCreationStep.Stats),
                lastResult =
                    CharacterCreationResult(
                        status = CharacterCreationStatus.ClassRejected,
                        memberNumber = current.memberNumber,
                    ),
            )
        }

        val member =
            PartyMemberState(
                name = current.memberName,
                gender = current.gender,
                race = current.race,
                pcClass = pcClass,
                level = rules.start.level,
                health = current.stats.health,
                hitPoints = current.stats.health of current.stats.health,
                weaponSkill = current.stats.weaponSkill,
                wisdom = current.stats.wisdom,
                mana = initialMana(pcClass, current.stats.wisdom),
                charisma = current.stats.charisma,
                equipment = EquipmentState(DslDefaultItems.none, DslDefaultItems.none),
                experience = 0,
                dead = false,
            )
        val nextMembers = members + member
        if (nextMembers.size >= rules.start.partyMemberMax) {
            val runtime = allocateRuntime(nextMembers)
            return copy(
                members = nextMembers,
                step = CharacterCreationStep.Finished(runtime),
                rngState = runtime.realm.rngState,
                lastResult =
                    CharacterCreationResult(
                        status = CharacterCreationStatus.Finished,
                        memberNumber = current.memberNumber,
                        member = member,
                        runtime = runtime,
                    ),
            )
        }

        return nextStats(current.memberNumber + 1).copy(
            members = nextMembers,
            lastResult =
                CharacterCreationResult(
                    status = CharacterCreationStatus.MemberAccepted,
                    memberNumber = current.memberNumber,
                    member = member,
                ),
        )
    }

    private fun continueRejectedClass(): CharacterCreationSession {
        val current = step as? CharacterCreationStep.ClassRejected ?: return this
        return copy(step = current.next, lastResult = null)
    }

    private fun nextStats(memberNumber: Int): CharacterCreationSession {
        val draw = drawStats()
        return copy(
            rngState = draw.rngState,
            step = CharacterCreationStep.Stats(memberNumber, draw.stats),
            lastResult = null,
        )
    }

    private fun drawStats(): CharacterCreationStatsDraw {
        var seed = rngState

        fun draw(): Int {
            val currentSeed = seed ?: return rules.statRoll.min
            val result = random.draw(currentSeed, rules.statRoll.min, rules.statRoll.max)
            seed = result.nextSeed
            return result.value
        }
        return CharacterCreationStatsDraw(
            stats =
                CharacterCreationStats(
                    weaponSkill = draw(),
                    wisdom = draw(),
                    health = draw(),
                    charisma = draw(),
                ),
            rngState = seed,
        )
    }

    private fun allocateRuntime(createdMembers: List<PartyMemberState>): RuntimeSessionState {
        val party =
            PartyState(
                id = partyId,
                player = playerName,
                name = checkNotNull(partyName) { "character creation finished without a party name" },
                position = rules.start.position,
                facing = rules.start.facing,
                resources = rules.start.resources,
                counters =
                    PartyCounters(
                        memberCount = createdMembers.size,
                        kills = 0,
                        waitingMail = false,
                        attacked = false,
                        attackedToday = emptyList(),
                    ),
                flags = emptyList(),
                milestones = emptyList(),
                inventory = (0..InventorySaveSlotMax).map { slot -> InventorySlotState(slot, null) },
                members = createdMembers,
                visitedNpcs = emptyList(),
                journal = emptyList(),
            )
        return RuntimeSessionState(
            realm =
                baseRealm.copy(
                    rngState = rngState,
                    activeParty = partyId,
                    currentMap = rules.start.map,
                    currentMode = RuntimeMode.Wilderness,
                    currentTown = null,
                    partyMemberMax = rules.start.partyMemberMax,
                ),
            parties = listOf(party),
        )
    }

    private fun abort(): CharacterCreationSession =
        copy(
            step = CharacterCreationStep.Aborted,
            lastResult = CharacterCreationResult(CharacterCreationStatus.Aborted),
        )

    companion object {
        fun start(
            partyId: PartyId,
            playerName: String,
            baseRealm: RealmState,
            content: RuntimeContent,
            random: RylweenRandomPort = ReferenceRylweenRandom,
        ): CharacterCreationSession =
            CharacterCreationSession(
                partyId = partyId,
                playerName = playerName,
                baseRealm = baseRealm,
                content = content,
                rngState = baseRealm.rngState,
                random = random,
            )
    }
}

private data class CharacterCreationStatsDraw(
    val stats: CharacterCreationStats,
    val rngState: Long?,
)

private fun CharacterClassOptionModel.failureFor(stats: CharacterCreationStats): TextBlock? {
    val stat = requiredStat ?: return null
    val minimum = checkNotNull(minimum)
    return failureText.takeIf { stats.value(stat) < minimum }
}

private fun initialMana(
    pcClass: PcClass,
    wisdom: Int,
): Mana =
    when (pcClass) {
        PcClass.Mage,
        PcClass.Priest,
        -> wisdom of wisdom
        PcClass.Fighter,
        PcClass.Thief,
        PcClass.Bard,
        PcClass.Ranger,
        -> 0 of wisdom
    }

private fun String.normalizedCreationName(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.replaceFirstChar { it.uppercaseChar() }
}
