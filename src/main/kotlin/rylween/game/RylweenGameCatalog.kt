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
// Current text handles.
// -----------------------------------------------------------------------------

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
