/**
 * Audio authoring builders.
 *
 * A game declares its sound effects and music tracks here and maps them to
 * engine events, items, monsters, spells, terrain and places. The resources
 * stay game-authored data; runtimes only learn which id to play, and renderers
 * that cannot play audio ignore it.
 */
package rylween.dsl

import rylween.dsl.impl.AreaMusicModel
import rylween.dsl.impl.AudioCueModel
import rylween.dsl.impl.AudioModel
import rylween.dsl.impl.CastleMusicModel
import rylween.dsl.impl.DungeonMusicModel
import rylween.dsl.impl.ItemSoundsModel
import rylween.dsl.impl.ItemUseSoundModel
import rylween.dsl.impl.MonsterSoundsModel
import rylween.dsl.impl.MusicTrackModel
import rylween.dsl.impl.SceneMusicModel
import rylween.dsl.impl.SoundAssetModel
import rylween.dsl.impl.SpellSoundModel
import rylween.dsl.impl.TerrainStepSoundModel

/** Engine events the runtime can voice. Each one maps to sounds with [AudioBuilder.cue]. */
enum class AudioCue {
    EncounterStart,
    Ambush,
    Fight,
    PlayerHit,
    PlayerMiss,
    MonsterHit,
    MonsterMiss,
    MonsterDeath,
    MemberDeath,
    Defend,
    BattleCry,
    RunAway,
    RunFail,
    Victory,
    Defeat,
    LevelUp,
    SpellFizzle,
    MonsterCast,
    ReadyWeapon,
    UnreadyWeapon,
    ReadyArmor,
    UnreadyArmor,
    Drop,
    Quaff,
    ItemTaken,
    GoldGained,
    GoldSpent,
    NotEnoughGold,
    Impassable,
    EnterTown,
    LeaveTown,
    CaveFound,
    EnterDungeon,
    LeaveDungeon,
    StairsDown,
    StairsUp,
    TorchLit,
    EnterCastle,
    CastleTrap,
    CastleTrapDisarmed,
    CastleTreasure,
    Teleport,
    Camp,
    Rest,
    ColdNight,
    Wait,
    Dawn,
    Nightfall,
    HuntSuccess,
    HuntFail,
    EndOfMoves,
    Hunger,
    Fatigue,
    StatRoll,
    CharacterCreated,
    QuestItem,
    JournalEntry,
    InvalidChoice,
    NewParty,
    GameLoaded,
    GameSaved,
    Quit,
    PageTurn,
}

/** Places whose background music the runtime picks. Scenes, dungeons and castles can override it. */
enum class MusicArea {
    Title,
    CharacterCreation,
    Wilderness,
    Town,
    Dungeon,
    Castle,
    Battle,
    GameOver,
}

/**
 * Receiver for `audio { ... }`.
 *
 * Several `audio` blocks, for example from included sections, add up rather
 * than replace each other.
 */
@RylNextDsl
class AudioBuilder(
    initial: AudioModel = AudioModel(),
) {
    private val sounds = initial.sounds.toMutableList()
    private val music = initial.music.toMutableList()
    private val cues = initial.cues.toMutableList()
    private val items = initial.items.toMutableList()
    private val monsters = initial.monsters.toMutableList()
    private val spells = initial.spells.toMutableList()
    private val itemUses = initial.itemUses.toMutableList()
    private val steps = initial.steps.toMutableList()
    private val areas = initial.areas.toMutableList()
    private val scenes = initial.scenes.toMutableList()
    private val dungeons = initial.dungeons.toMutableList()
    private val castles = initial.castles.toMutableList()

    /** Declares sound effects whose files live in one classpath directory. */
    fun sounds(
        resourceDirectory: String,
        body: SoundGroupBuilder.() -> Unit,
    ) {
        sounds += SoundGroupBuilder(resourceDirectory).apply(body).build()
    }

    /** Declares looping music or ambience tracks whose files live in one classpath directory. */
    fun music(
        resourceDirectory: String,
        body: MusicGroupBuilder.() -> Unit,
    ) {
        music += MusicGroupBuilder(resourceDirectory).apply(body).build()
    }

    fun cue(
        cue: AudioCue,
        vararg sounds: SoundId,
    ) {
        cues += AudioCueModel(cue, sounds.toList())
    }

    /** Weapon or armor sounds that replace the generic hit, miss and ready cues for this item. */
    fun item(
        item: ItemId,
        body: ItemSoundsBuilder.() -> Unit,
    ) {
        items += ItemSoundsBuilder(item).apply(body).build()
    }

    /** Applies the same sounds to several items, such as every blade. */
    fun items(
        vararg items: ItemId,
        body: ItemSoundsBuilder.() -> Unit,
    ) {
        items.forEach { item(it, body) }
    }

    fun monster(
        monster: MonsterId,
        body: MonsterSoundsBuilder.() -> Unit,
    ) {
        monsters += MonsterSoundsBuilder(monster).apply(body).build()
    }

    fun monsters(
        vararg monsters: MonsterId,
        body: MonsterSoundsBuilder.() -> Unit,
    ) {
        monsters.forEach { monster(it, body) }
    }

    /** Plays when the spell is cast, before its effect text. */
    fun spell(
        spell: SpellId,
        vararg sounds: SoundId,
    ) {
        spells += SpellSoundModel(spell, sounds.toList())
    }

    /** Plays when the item is used: a potion is quaffed or a magic item is used in battle. */
    fun itemUse(
        item: ItemId,
        vararg sounds: SoundId,
    ) {
        itemUses += ItemUseSoundModel(item, sounds.toList())
    }

    /** Plays when the party steps onto this terrain in the wilderness. */
    fun step(
        terrain: TerrainId,
        vararg sounds: SoundId,
    ) {
        steps += TerrainStepSoundModel(terrain, sounds.toList())
    }

    fun area(
        area: MusicArea,
        music: MusicId,
    ) {
        areas += AreaMusicModel(area, music)
    }

    /** Music for an area during certain hours, inclusive and wrapping past midnight. */
    fun area(
        area: MusicArea,
        music: MusicId,
        fromHour: Int,
        toHour: Int,
    ) {
        areas += AreaMusicModel(area, music, fromHour, toHour)
    }

    /** Music while a scene runs, such as a town service or the ending. */
    fun scene(
        scene: SceneId,
        music: MusicId,
    ) {
        scenes += SceneMusicModel(scene, music)
    }

    fun dungeon(
        dungeon: DungeonId,
        music: MusicId,
    ) {
        dungeons += DungeonMusicModel(dungeon, music)
    }

    fun castle(
        castle: CastleId,
        music: MusicId,
    ) {
        castles += CastleMusicModel(castle, music)
    }

    fun build(): AudioModel =
        AudioModel(
            sounds = sounds.toList(),
            music = music.toList(),
            cues = cues.toList(),
            items = items.toList(),
            monsters = monsters.toList(),
            spells = spells.toList(),
            itemUses = itemUses.toList(),
            steps = steps.toList(),
            areas = areas.toList(),
            scenes = scenes.toList(),
            dungeons = dungeons.toList(),
            castles = castles.toList(),
        )
}

@RylNextDsl
class SoundGroupBuilder internal constructor(
    resourceDirectory: String,
) {
    private val directory = resourceDirectory.trimEnd('/')
    private val sounds = mutableListOf<SoundAssetModel>()

    fun sound(
        id: SoundId,
        fileName: String,
    ) {
        sounds += SoundAssetModel(id, "$directory/${fileName.trimStart('/')}")
    }

    internal fun build(): List<SoundAssetModel> = sounds.toList()
}

@RylNextDsl
class MusicGroupBuilder internal constructor(
    resourceDirectory: String,
) {
    private val directory = resourceDirectory.trimEnd('/')
    private val tracks = mutableListOf<MusicTrackModel>()

    fun track(
        id: MusicId,
        fileName: String,
    ) {
        tracks += MusicTrackModel(id, "$directory/${fileName.trimStart('/')}")
    }

    internal fun build(): List<MusicTrackModel> = tracks.toList()
}

@RylNextDsl
class ItemSoundsBuilder internal constructor(
    private val item: ItemId,
) {
    private var hit = emptyList<SoundId>()
    private var miss = emptyList<SoundId>()
    private var ready = emptyList<SoundId>()
    private var unready = emptyList<SoundId>()
    private var struck = emptyList<SoundId>()

    /** A weapon hit that connects. */
    fun hit(vararg sounds: SoundId) {
        hit = sounds.toList()
    }

    /** A weapon attack that misses. */
    fun miss(vararg sounds: SoundId) {
        miss = sounds.toList()
    }

    fun ready(vararg sounds: SoundId) {
        ready = sounds.toList()
    }

    fun unready(vararg sounds: SoundId) {
        unready = sounds.toList()
    }

    /** Armor hit by a monster's blow. */
    fun struck(vararg sounds: SoundId) {
        struck = sounds.toList()
    }

    internal fun build(): ItemSoundsModel = ItemSoundsModel(item, hit, miss, ready, unready, struck)
}

@RylNextDsl
class MonsterSoundsBuilder internal constructor(
    private val monster: MonsterId,
) {
    private var attack = emptyList<SoundId>()
    private var death = emptyList<SoundId>()

    /** The monster's blow landing on a party member. */
    fun attack(vararg sounds: SoundId) {
        attack = sounds.toList()
    }

    fun death(vararg sounds: SoundId) {
        death = sounds.toList()
    }

    internal fun build(): MonsterSoundsModel = MonsterSoundsModel(monster, attack, death)
}
