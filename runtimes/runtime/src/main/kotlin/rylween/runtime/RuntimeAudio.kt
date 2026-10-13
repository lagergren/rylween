package rylween.runtime

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
        play("hit.$weapon", audio.items[weapon]?.hit.orEmpty().ifEmpty { cueSounds(AudioCue.PlayerHit) })
    }

    fun weaponMiss(weapon: String?) {
        play("miss.$weapon", audio.items[weapon]?.miss.orEmpty().ifEmpty { cueSounds(AudioCue.PlayerMiss) })
    }

    fun ready(
        item: String,
        fallback: AudioCue,
    ) {
        play("ready.$item", audio.items[item]?.ready.orEmpty().ifEmpty { cueSounds(fallback) })
    }

    fun unready(
        item: String,
        fallback: AudioCue,
    ) {
        play("unready.$item", audio.items[item]?.unready.orEmpty().ifEmpty { cueSounds(fallback) })
    }

    /** A monster's blow landing, followed by the struck member's armor ringing when it has a sound. */
    fun monsterHit(
        monster: String,
        armor: String?,
    ) {
        play("attack.$monster", audio.monsters[monster]?.attack.orEmpty().ifEmpty { cueSounds(AudioCue.MonsterHit) })
        play("struck.$armor", audio.items[armor]?.struck.orEmpty())
    }

    fun monsterDeath(monster: String) {
        play("death.$monster", audio.monsters[monster]?.death.orEmpty().ifEmpty { cueSounds(AudioCue.MonsterDeath) })
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

    /** Plays a scene's own music and returns true, or returns false when the scene has none. */
    fun sceneMusic(scene: String): Boolean {
        val music = audio.scenes[scene] ?: return false
        screen.playMusic(music)
        return true
    }

    fun dungeonMusic(dungeon: String?) {
        screen.playMusic(dungeon?.let(audio.dungeons::get) ?: musicFor(MusicArea.Dungeon))
    }

    fun castleMusic(castle: String) {
        screen.playMusic(audio.castles[castle] ?: musicFor(MusicArea.Castle))
    }

    private fun cueSounds(cue: AudioCue): List<String> = audio.cues[cue.name].orEmpty()

    private fun play(
        key: String,
        sounds: List<String>,
    ) {
        if (sounds.isEmpty()) return
        val next = rotation.getOrDefault(key, 0)
        rotation[key] = next + 1
        screen.playSound(sounds[next % sounds.size])
    }
}
