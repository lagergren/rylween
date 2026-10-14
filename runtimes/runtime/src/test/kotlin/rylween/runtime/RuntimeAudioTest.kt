package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.ExternalAreaMusic
import rylween.compiled.ExternalItemSounds
import rylween.compiled.ExternalMonsterSounds
import rylween.compiled.ExternalRuntimeAudio
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.AudioCue
import rylween.dsl.MusicArea
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuntimeAudioTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))

    @TempDir
    private lateinit var testTmp: File
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content

    @Test
    fun cueVariantsPlayInRotation() {
        val screen = AudioRecordingScreen()
        val audio = RuntimeAudio(ExternalRuntimeAudio(cues = mapOf("PlayerMiss" to listOf("a", "b", "c"))), screen)

        repeat(4) { audio.cue(AudioCue.PlayerMiss) }

        assertEquals(listOf("a", "b", "c", "a"), screen.sounds)
    }

    @Test
    fun itemAndMonsterSoundsFallBackToTheGenericCues() {
        val screen = AudioRecordingScreen()
        val audio =
            RuntimeAudio(
                ExternalRuntimeAudio(
                    cues =
                        mapOf(
                            "PlayerHit" to listOf("cue.hit"),
                            "MonsterHit" to listOf("cue.blow"),
                            "MonsterDeath" to listOf("cue.death"),
                        ),
                    items =
                        mapOf(
                            "sword" to ExternalItemSounds(hit = listOf("sword.hit")),
                            "plate" to ExternalItemSounds(struck = listOf("plate.clank")),
                        ),
                    monsters = mapOf("wolf" to ExternalMonsterSounds(attack = listOf("wolf.bite"))),
                ),
                screen,
            )

        audio.weaponHit("sword")
        audio.weaponHit("club")
        audio.weaponMiss("sword")
        audio.monsterHit("wolf", "plate")
        audio.monsterHit("orc", null)
        audio.monsterDeath("wolf")

        assertEquals(listOf("sword.hit", "cue.hit", "wolf.bite", "plate.clank", "cue.blow", "cue.death"), screen.sounds)
    }

    @Test
    fun timedAreaMusicWrapsPastMidnightAndWinsOverTheUntimedTrack() {
        val audio =
            RuntimeAudio(
                ExternalRuntimeAudio(
                    areas =
                        listOf(
                            ExternalAreaMusic(area = "Wilderness", music = "day"),
                            ExternalAreaMusic(area = "Wilderness", music = "night", fromHour = 20, toHour = 4),
                        ),
                ),
                AudioRecordingScreen(),
            )

        assertEquals("day", audio.musicFor(MusicArea.Wilderness))
        assertEquals("day", audio.musicFor(MusicArea.Wilderness, 12))
        assertEquals("night", audio.musicFor(MusicArea.Wilderness, 20))
        assertEquals("night", audio.musicFor(MusicArea.Wilderness, 2))
        assertEquals("day", audio.musicFor(MusicArea.Wilderness, 5))
        assertNull(audio.musicFor(MusicArea.Town))
    }

    @Test
    fun sceneMusicPlaysOnlyForScenesThatHaveIt() {
        val screen = AudioRecordingScreen()
        val audio = RuntimeAudio(ExternalRuntimeAudio(scenes = mapOf("towne.church" to "chant")), screen)

        assertEquals("chant", audio.sceneMusic("towne.church"))
        assertNull(audio.sceneMusic("towne.smith"))
        assertEquals(listOf<String?>("chant"), screen.music)
    }

    @Test
    fun aMissedSwingPlaysTheWeaponsMissSoundAfterTheBattleMusicStarts() {
        val sword = item("sword")
        val base = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == "strong-replay" }, content)
        val runtime =
            base.updateMember(0) { member -> member.copy(weaponSkill = -200, equipment = member.equipment.copy(weapon = sword)) }
        val audio =
            ExternalRuntimeAudio(
                cues = mapOf("EncounterStart" to listOf("test.encounter")),
                items = mapOf("sword" to ExternalItemSounds(miss = listOf("test.sword.miss"))),
                areas = listOf(ExternalAreaMusic(area = "Battle", music = "test.battle")),
            )
        val screen = AudioRecordingScreen()
        val input = ScriptedInput(("F" + "AA".repeat(runtime.party.members.size)).repeat(ROUNDS))

        RuntimeLog.open(testTmp.resolve("audio-battle-miss.log")).use { log ->
            BattleRunner(content, systems.combat, systems.pacing.battle, input, screen, log, audioData = audio)
                .runMonster(runtime, content.ref(CompiledHandleKind.Monster, "hobgoblin_chief"), mandatory = true)
        }

        assertEquals("test.battle", screen.music.first())
        assertEquals("test.encounter", screen.sounds.first())
        assertTrue("test.sword.miss" in screen.sounds, screen.sounds.toString())
    }

    @Test
    fun readyingArmorPlaysItsReadySoundAndQuaffingPlaysThePotionsSound() {
        val leather = item("leather")
        val potion = item("red_potion")
        val audio =
            ExternalRuntimeAudio(
                cues = mapOf("Quaff" to listOf("test.quaff")),
                items = mapOf("leather" to ExternalItemSounds(ready = listOf("test.leather.ready"))),
                itemUses = mapOf("red_potion" to listOf("test.heal")),
            )
        val screen = AudioRecordingScreen()
        val runtime = runtimeWithInventory(leather, potion)

        RuntimeLog.open(testTmp.resolve("audio-utility.log")).use { log ->
            val actions = UtilityActions(content, systems.copy(audio = audio), ScriptedInput("1A" + "B1"), screen, log)
            actions.quaff(actions.readyArmor(runtime))
        }

        assertEquals(listOf("test.leather.ready", "test.quaff", "test.heal"), screen.sounds)
    }

    @Test
    fun theTitleMenuPlaysTitleMusic() {
        val screen = runSession("audio-title", showTitleMenu = true)

        assertEquals(listOf<String?>("test.title"), screen.music)
    }

    @Test
    fun theWildernessPlaysTheTrackForTheCurrentHour() {
        val screen = runSession("audio-wilderness", showTitleMenu = false)

        assertEquals(listOf<String?>("test.now"), screen.music)
        assertEquals(emptyList(), screen.sounds)
    }

    /** Runs a session with no input under audio rows whose only timed wilderness track covers the state's hour. */
    private fun runSession(
        name: String,
        showTitleMenu: Boolean,
    ): AudioRecordingScreen {
        val stateSlug = "fresh-replay"
        val hour = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == stateSlug }, content).hour
        val audio =
            ExternalRuntimeAudio(
                cues = mapOf("Dawn" to listOf("test.dawn"), "Nightfall" to listOf("test.nightfall")),
                areas =
                    listOf(
                        ExternalAreaMusic(area = "Title", music = "test.title"),
                        ExternalAreaMusic(area = "Wilderness", music = "test.day"),
                        ExternalAreaMusic(area = "Wilderness", music = "test.now", fromHour = hour, toHour = hour),
                    ),
            )
        val factory =
            RuntimeSessionFactory.load(bundle, systems.copy(audio = audio), bundleFile.readBytes().readExternalCompiledBundleContentHash())
        val screen = AudioRecordingScreen()
        val saveFile = testTmp.resolve("$name.rsave").also(File::delete)
        RuntimeLog.open(testTmp.resolve("$name.log")).use { log ->
            factory.start(
                ScriptedInput(""),
                screen,
                RuntimeSessionConfig(
                    userId = "audio-user",
                    stateSlug = stateSlug,
                    saveFile = saveFile,
                    showTitleMenu = showTitleMenu,
                    sessionDate = LocalDate.of(2026, 5, 11),
                ),
                log,
            )
        }
        return screen
    }

    private fun item(slug: String): CompiledHandleRef = content.ref(CompiledHandleKind.Item, slug)

    private fun runtimeWithInventory(vararg items: CompiledHandleRef?): RuntimeState {
        val runtime = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == "fresh-replay" }, content)
        return runtime.withParty(
            runtime.party.copy(
                inventory = (1..16).map { index -> CompiledInventorySlot(index, items.getOrNull(index - 1)) },
            ),
        )
    }

    private companion object {
        const val ROUNDS = 20
    }
}
