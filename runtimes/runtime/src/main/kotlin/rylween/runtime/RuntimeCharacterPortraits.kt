/** Runtime character-portrait selection helpers for creation and party views. */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.ExternalCharacterCreationPortrait
import rylween.compiled.ExternalRuntimeCharacterCreation
import rylween.dsl.impl.Gender
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.Race

internal fun ExternalRuntimeCharacterCreation.creationPortraitPreview(
    memberNumber: Int,
    memberName: String?,
    gender: Gender?,
    race: Race?,
    pcClass: PcClass?,
): RuntimeCharacterPortraitPreview? {
    val portrait = portraitFor(gender, race, pcClass) ?: return null
    return RuntimeCharacterPortraitPreview(
        memberNumber = memberNumber,
        memberName = memberName,
        gender = gender,
        genderLabel = gender?.runtimeLabel(),
        race = race,
        raceLabel = race?.let(::raceLabel),
        pcClass = pcClass,
        classLabel = pcClass?.let(::classLabel),
        imageSlug = portrait.image,
    )
}

internal fun ExternalRuntimeCharacterCreation.partyPortraitPreview(
    content: CompiledContentBundle,
    party: PartyState,
    memberIndex: Int,
): RuntimeCharacterPortraitPreview? {
    val member = party.members.getOrNull(memberIndex) ?: return null
    val portrait = portraitFor(member.gender, member.race, member.pcClass) ?: return null
    return RuntimeCharacterPortraitPreview(
        memberNumber = memberIndex + 1,
        memberName = party.memberNames.getOrNull(memberIndex) ?: content.strings.value(member.nameString),
        gender = member.gender,
        genderLabel = member.gender.runtimeLabel(),
        race = member.race,
        raceLabel = raceLabel(member.race),
        pcClass = member.pcClass,
        classLabel = classLabel(member.pcClass),
        imageSlug = portrait.image,
    )
}

private fun ExternalRuntimeCharacterCreation.portraitFor(
    gender: Gender?,
    race: Race?,
    pcClass: PcClass?,
): ExternalCharacterCreationPortrait? =
    portraits
        .asSequence()
        .filter { portrait -> portrait.matches(gender, race, pcClass) }
        .maxByOrNull { portrait -> portrait.specificity() }

private fun ExternalCharacterCreationPortrait.matches(
    gender: Gender?,
    race: Race?,
    pcClass: PcClass?,
): Boolean =
    selectorMatches(this.gender, gender) &&
        selectorMatches(this.race, race) &&
        selectorMatches(this.pcClass, pcClass)

private fun selectorMatches(
    selector: Any?,
    value: Any?,
): Boolean = selector == null || selector == value

private fun ExternalCharacterCreationPortrait.specificity(): Int =
    listOfNotNull(
        gender?.let { 1 },
        race?.let { 2 },
        pcClass?.let { 4 },
    ).sum()

private fun ExternalRuntimeCharacterCreation.raceLabel(race: Race): String =
    races.firstOrNull { option -> option.race == race }?.label ?: race.name.readableSlug()

private fun ExternalRuntimeCharacterCreation.classLabel(pcClass: PcClass): String =
    classes.firstOrNull { option -> option.pcClass == pcClass }?.label ?: pcClass.name.readableSlug()

private fun Gender.runtimeLabel(): String = name.readableSlug()
