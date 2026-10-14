package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.dsl.impl.PcClass
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Saves written by older runtimes still load, with what has changed since brought up to date. */
class OldSaveCompatibilityTest {
    @TempDir
    private lateinit var testTmp: File
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val content = bundle.content

    @Test
    fun aCastersSpellPointsFromBeforeTheyGrewWithWisdomAreRaisedToWisdom() {
        val runtime = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == "near-winning-replay" }, content)
        val priest = runtime.party.members.indexOfFirst { member -> member.pcClass == PcClass.Priest }
        val save = testTmp.resolve("old-spell-points.rsave")
        SaveFile.write(save, runtime, bundleFile.readExternalCompiledBundleContentHash(), content, "veteran", null)
        val maxKey = Regex("""(member\.${priest + 1}\.magicMax=)\d+""")
        val old = save.readText()
        save.writeText(old.replace(maxKey) { match -> "${match.groupValues[1]}$OLD_MAXIMUM" })
        assertTrue(save.readText() != old, "the save has no spell-point maximum for member ${priest + 1}")

        val loaded =
            SaveFile.read(
                save,
                content,
                { slug -> bundle.states.single { state -> content.slug(state.state) == slug } },
                "veteran",
            )

        val member = loaded.party.members[priest]
        assertEquals(member.wisdom, member.mana.max)
    }

    private companion object {
        const val OLD_MAXIMUM = 30
    }
}
