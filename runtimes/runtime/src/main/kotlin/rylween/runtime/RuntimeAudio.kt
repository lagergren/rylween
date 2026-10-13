package rylween.runtime

import rylween.compiled.ExternalItemSounds
import rylween.compiled.ExternalMonsterSounds
import rylween.compiled.ExternalRuntimeAudio
import rylween.dsl.AudioCue
import rylween.dsl.MusicArea

/**
 * Turns game events into the game's authored sound and music ids and hands
 * them to the screen. Renderers without audio ignore the calls.
 *
 * When an event has several sound variants they play in rotation rather than
 * at random, so audio never draws from the game's random numbers and replays
 * stay identical.
 */
internal class RuntimeAudio(
    private val audio: ExternalRuntimeAudio,
    private val screen: RuntimeScreenPort,
) {
    private val rotation = mutableMapOf<String, Int>()

    fun cue(cue: AudioCue) {
        play("cue.${cue.name}", cueSounds(cue))
    }

    /** A party member's weapon connecting; the weapon's own sounds win over the generic cue. */
    fun weaponHit(weapon: String?) {
        play("hit.$weapon", itemSounds(weapon) { it.hit }, AudioCue.PlayerHit)
    }

    fun weaponMiss(weapon: String?) {
        play("miss.$weapon", itemSounds(weapon) { it.miss }, AudioCue.PlayerMiss)
    }

    fun ready(
        item: String,
        fallback: AudioCue,
    ) {
        play("ready.$item", itemSounds(item) { it.ready }, fallback)
    }

    fun unready(
        item: String,
        fallback: AudioCue,
    ) {
        play("unready.$item", itemSounds(item) { it.unready }, fallback)
    }

    /** A monster's blow landing, followed by the struck member's armor ringing when it has a sound. */
    fun monsterHit(
        monster: String,
        armor: String?,
    ) {
        play("attack.$monster", monsterSounds(monster) { it.attack }, AudioCue.MonsterHit)
        play("struck.$armor", itemSounds(armor) { it.struck })
    }

    fun monsterDeath(monster: String) {
        play("death.$monster", monsterSounds(monster) { it.death }, AudioCue.MonsterDeath)
    }

    fun spell(spell: String) {
        play("spell.$spell", audio.spells[spell].orEmpty())
    }

    fun itemUse(item: String) {
        play("use.$item", audio.itemUses[item].orEmpty())
    }

    fun step(terrain: String) {
        play("step.$terrain", audio.steps[terrain].orEmpty())
    }

    /** The track for an area at an hour: a track whose hours include it, otherwise the area's untimed track. */
    fun musicFor(
        area: MusicArea,
        hour: Int? = null,
    ): String? {
        val tracks = audio.areas.filter { it.area == area.name }
        val timed =
            hour?.let { now ->
                tracks.firstOrNull { track ->
                    val from = track.fromHour ?: return@firstOrNull false
                    val to = track.toHour ?: return@firstOrNull false
                    if (from <= to) now in from..to else now >= from || now <= to
                }
            }
        return (timed ?: tracks.firstOrNull { it.fromHour == null })?.music
    }

    fun music(
        area: MusicArea,
        hour: Int? = null,
    ) {
        screen.playMusic(musicFor(area, hour))
    }

    /** Plays a scene's own music and returns it, or returns null when the scene has none. */
    fun sceneMusic(scene: String): String? {
        val music = audio.scenes[scene] ?: return null
        screen.playMusic(music)
        return music
    }

    fun dungeonMusic(dungeon: String?) {
        screen.playMusic(dungeon?.let(audio.dungeons::get) ?: musicFor(MusicArea.Dungeon))
    }

    fun castleMusic(castle: String) {
        screen.playMusic(audio.castles[castle] ?: musicFor(MusicArea.Castle))
    }

    private fun cueSounds(cue: AudioCue): List<String> = audio.cues[cue.name].orEmpty()

    private fun itemSounds(
        item: String?,
        pick: (ExternalItemSounds) -> List<String>,
    ): List<String> = audio.items[item]?.let(pick).orEmpty()

    private fun monsterSounds(
        monster: String,
        pick: (ExternalMonsterSounds) -> List<String>,
    ): List<String> = audio.monsters[monster]?.let(pick).orEmpty()

    /** Plays the next of [sounds] in rotation, or of the [fallback] cue's sounds when there are none. */
    private fun play(
        key: String,
        sounds: List<String>,
        fallback: AudioCue? = null,
    ) {
        val choices = sounds.ifEmpty { fallback?.let(::cueSounds).orEmpty() }
        if (choices.isEmpty()) return
        val next = rotation.getOrDefault(key, 0)
        rotation[key] = next + 1
        screen.playSound(choices[next % choices.size])
    }
}
