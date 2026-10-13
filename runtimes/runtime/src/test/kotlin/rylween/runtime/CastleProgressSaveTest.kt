package rylween.runtime

import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class CastleProgressSaveTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val content = bundle.content

    @Test
    fun theFloorsAPartyHasOpenedInACastleSurviveASave() {
        val base = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == "near-winning-replay" }, content)
        val runtime = base.withParty(base.party.copy(castleFloorsOpened = mapOf("ydarg" to 2)))
        val save = File(requiredRuntimeTestProperty("rylween.runtime.testTmp")).also(File::mkdirs).resolve("castle-progress.rsave").also(File::delete)

        SaveFile.write(save, runtime, bundleFile.readExternalCompiledBundleContentHash(), content, "castle", null)
        val loaded = SaveFile.read(save, content, { slug -> bundle.states.single { state -> content.slug(state.state) == slug } }, "castle")

        assertEquals(mapOf("ydarg" to 2), loaded.party.castleFloorsOpened)
    }
}
