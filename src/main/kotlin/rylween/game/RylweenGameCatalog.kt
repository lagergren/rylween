// RylweenGameCatalog.kt -- transitional current-game handles.
//
// This is ordinary Kotlin source for now, not generated metadata. It is kept in
// main only for handle families that have not yet moved to generated,
// declaration-derived current-game handles.

@file:Suppress(
    "unused",
    "MemberVisibilityCanBePrivate",
    "RedundantVisibilityModifier",
    "UNUSED_PARAMETER",
    "CanBeParameter",
    "KotlinExplicitApi",
    "PropertyName",
)

package rylween.game

import rylween.dsl.*

// -----------------------------------------------------------------------------
// Current combat, map, visual, layout, text, scene, and native handles.
// -----------------------------------------------------------------------------

class TerrainTagRefs {
    val mountain = terrainTag("mountain")
    val water = terrainTag("water")
    val town = terrainTag("town")
    val hut = terrainTag("hut")
    val castle = terrainTag("castle")
    val settlement = terrainTag("settlement")
    val impassable = terrainTag("impassable")
}
val TerrainTags = TerrainTagRefs()

class PaletteRefs {
    val terminal16 = palette("terminal16")
}
val Palettes = PaletteRefs()

class PaletteColorRefs {
    val black = paletteColor("black")
    val red = paletteColor("red")
    val green = paletteColor("green")
    val yellow = paletteColor("yellow")
    val blue = paletteColor("blue")
    val magenta = paletteColor("magenta")
    val cyan = paletteColor("cyan")
    val white = paletteColor("white")
}
val PaletteColors = PaletteColorRefs()

class KeyActionRefs {
    val pass = keyAction("pass")
    val save = keyAction("save")
    val weaponReady = keyAction("weapon_ready")
    val armorReady = keyAction("armor_ready")
    val redraw = keyAction("redraw")
    val journal = keyAction("journal")
    val help = keyAction("help")
    val hunt = keyAction("hunt")
    val viewParty = keyAction("view_party")
    val armorOff = keyAction("armor_off")
    val weaponOff = keyAction("weapon_off")
    val enter = keyAction("enter")
    val drop = keyAction("drop")
    val camp = keyAction("camp")
    val castSpell = keyAction("cast_spell")
    val quaff = keyAction("quaff")
    val quitConfirm = keyAction("quit_confirm")
    val enterChurch = keyAction("enter_church")
    val enterSmith = keyAction("enter_smith")
    val enterInn = keyAction("enter_inn")
    val enterMerchant = keyAction("enter_merchant")
    val enterTavern = keyAction("enter_tavern")
    val leaveTown = keyAction("leave_town")
    val useItem = keyAction("use_item")
    val defend = keyAction("defend")
    val attack = keyAction("attack")
    val battleCry = keyAction("battle_cry")
    val speakChitchat = keyAction("speak_chitchat")
    val speakQuestion = keyAction("speak_question")
    val speakRumour = keyAction("speak_rumour")
    val speakBye = keyAction("speak_bye")
    val questionLodging = keyAction("question_lodging")
    val questionMagic = keyAction("question_magic")
    val questionPeople = keyAction("question_people")
    val questionNothing = keyAction("question_nothing")
}
val KeyActions = KeyActionRefs()

class StateRefs {
    val freshReplay = stateId("fresh-replay")
    val nearWinningReplay = stateId("near-winning-replay")
    val winningReplay = stateId("winning-replay")
}
val States = StateRefs()

class PartyRefs {
    val replay = partyId("replay")
}
val Parties = PartyRefs()

class TextRefs {
    val Title = TitleTextRefs()
    val Driv = DrivTextRefs()
    val Towne = TowneRefs()
    val Speak = SpeakRefs()
    val Battle = BattleRefs()
    val MissionsText = MissionTextRefs()
    val Ydarg = YdargTextRefs()
    val Dungeon = DungeonTextRefs()
    val Terrain = TerrainTextRefs()
    val Common = CommonTextRefs()
}

class TitleTextRefs {
    val root = text("title")
}

class DrivTextRefs {
    val statusline = text("driv.statusline")
}

class TowneRefs {
    val Church = ChurchRefs()
    val Rumour = RumourRefs()
}

class ChurchRefs {
    val intro = text("towne.church.intro")
    val menu = text("towne.church.menu")
    val who = text("towne.church.who")
}

class RumourRefs {
    val r12 = text("towne.rumour.r12")
    val r13 = text("towne.rumour.r13")
    val r25 = text("towne.rumour.r25")
    val r27 = text("towne.rumour.r27")
    val indexed = textPattern("towne.rumour.r{}")
}

class SpeakRefs {
    val noNpc = text("speak.no_npc")
    val noRumour = text("speak.no_rumour")
}

class BattleRefs {
    val Prompt = BattlePromptRefs()
    val Frost = FrostRefs()
    val FireWand = FireWandRefs()
    val killed = text("battle.killed")
}

class BattlePromptRefs {
    val monster = text("battle.prompt.who")
}

class FrostRefs {
    val chills = text("battle.spell.frost.chills")
    val damage = text("battle.spell.frost.damage")
    val damagePts = text("battle.spell.frost.damage_pts")
}

class FireWandRefs {
    val burns = text("battle.item.fire_wand.burns")
    val points = text("battle.item.fire_wand.points")
}

class MissionTextRefs {
    val Bandits = BanditsTextRefs()
}

class BanditsTextRefs {
    val spotted = text("missions.bandits.spotted")
    val warned = text("missions.bandits.warned")
    val recovered = text("missions.bandits.recovered")
}

class YdargTextRefs {
    val noKey = text("ydarg.no_key")
    val ruin = text("ydarg.ruin")
}

class DungeonTextRefs {
    val findObject = text("dungeon.find_object")
}

class TerrainTextRefs {
    val transition = text("terrain.transition")
}

class CommonTextRefs {
    val pause = text("common.pause")
}
val Texts = TextRefs()
val T = Texts
