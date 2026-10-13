/**
 * Audio content models.
 *
 * Sounds are one-shot effects and music tracks are seamless loops, which
 * covers background music and ambient loops alike. The remaining rows map
 * engine events, items, monsters, spells, terrain and places to those assets,
 * so runtimes only ever learn which id to play.
 */
package rylween.dsl.impl

import rylween.dsl.AudioCue
import rylween.dsl.CastleId
import rylween.dsl.DungeonId
import rylween.dsl.ItemId
import rylween.dsl.MonsterId
import rylween.dsl.MusicArea
import rylween.dsl.MusicId
import rylween.dsl.SceneId
import rylween.dsl.SoundId
import rylween.dsl.SpellId
import rylween.dsl.TerrainId

data class AudioModel(
    val sounds: List<SoundAssetModel> = emptyList(),
    val music: List<MusicTrackModel> = emptyList(),
    val cues: List<AudioCueModel> = emptyList(),
    val items: List<ItemSoundsModel> = emptyList(),
    val monsters: List<MonsterSoundsModel> = emptyList(),
    val spells: List<SpellSoundModel> = emptyList(),
    val itemUses: List<ItemUseSoundModel> = emptyList(),
    val steps: List<TerrainStepSoundModel> = emptyList(),
    val areas: List<AreaMusicModel> = emptyList(),
    val scenes: List<SceneMusicModel> = emptyList(),
    val dungeons: List<DungeonMusicModel> = emptyList(),
    val castles: List<CastleMusicModel> = emptyList(),
)

data class SoundAssetModel(
    val id: SoundId,
    val resource: String,
)

data class MusicTrackModel(
    val id: MusicId,
    val resource: String,
)

/** Sounds for one engine event; the runtime rotates through them for variety. */
data class AudioCueModel(
    val cue: AudioCue,
    val sounds: List<SoundId>,
)

/** Weapon and armor sounds; empty lists fall back to the matching engine cue. */
data class ItemSoundsModel(
    val item: ItemId,
    val hit: List<SoundId> = emptyList(),
    val miss: List<SoundId> = emptyList(),
    val ready: List<SoundId> = emptyList(),
    val unready: List<SoundId> = emptyList(),
    val struck: List<SoundId> = emptyList(),
)

data class MonsterSoundsModel(
    val monster: MonsterId,
    val attack: List<SoundId> = emptyList(),
    val death: List<SoundId> = emptyList(),
)

data class SpellSoundModel(
    val spell: SpellId,
    val sounds: List<SoundId>,
)

data class ItemUseSoundModel(
    val item: ItemId,
    val sounds: List<SoundId>,
)

data class TerrainStepSoundModel(
    val terrain: TerrainId,
    val sounds: List<SoundId>,
)

/**
 * Music for a place. A track with hours plays only from [fromHour] through
 * [toHour] inclusive, wrapping past midnight when [fromHour] is later, and wins
 * over the same area's track without hours.
 */
data class AreaMusicModel(
    val area: MusicArea,
    val music: MusicId,
    val fromHour: Int? = null,
    val toHour: Int? = null,
)

data class SceneMusicModel(
    val scene: SceneId,
    val music: MusicId,
)

data class DungeonMusicModel(
    val dungeon: DungeonId,
    val music: MusicId,
)

data class CastleMusicModel(
    val castle: CastleId,
    val music: MusicId,
)
