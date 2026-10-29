package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import rylween.compiled.CompiledGameBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledIntExpr
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TownEntryTaxTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))

    @TempDir
    private lateinit var testTmp: File
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content
    private val town = content.towns.first()
    private val townName = content.strings.value(town.nameString)

    // The game's tax is a share of the gold carried, so only a fixed tax can ask for more than the party has.
    @Test
    fun aPartyThatCannotPayTheTaxIsToldWhatItLacksAndStaysOutside() {
        val screen = enterTown("tax-short", tax = 2_500, gold = 2_000)

        val pages = screen.screens.map { lines -> lines.joinToString(" ") }
        val shortfall = "There is a tax of ${town.taxPercent}% to enter $townName. You need 500 additional au to be able to pay."
        assertTrue(pages.any { page -> shortfall in page }, "$pages")
        assertFalse(pages.any { page -> "(P)ay to Enter" in page }, "a party that can't pay is not asked to: $pages")
        assertFalse(screen.screens.flatten().any { line -> line.startsWith("Entering ") }, "the town menu stays closed: $pages")
    }

    @Test
    fun aPartyThatCanPayTheTaxIsAskedToPayIt() {
        val screen = enterTown("tax-paid", tax = 1_500, gold = 2_000)

        val pages = screen.screens.map { lines -> lines.joinToString(" ") }
        assertTrue(pages.any { page -> "we must request you pay 1500 au." in page && "(P)ay to Enter" in page }, "$pages")
        assertTrue(pages.any { page -> "Thank you!" in page && "Entering $townName" in page }, "$pages")
    }

    /** Starts a new party with [gold], moves it onto the first town, and enters it under a fixed [tax], paying if asked. */
    private fun enterTown(
        name: String,
        tax: Int,
        gold: Int,
    ): TextScreen {
        val factory = RuntimeSessionFactory.load(bundle.withTownTax(tax), systems, bundleFile.readExternalCompiledBundleContentHash())
        val saveFile = testTmp.resolve("$name.rsave").also(File::delete)
        runSession(factory, name, ScriptedInput(""), NoOpRuntimeScreen(), saveFile, loadFile = null)

        val values = SaveCodec.decodeValues(saveFile.readLines())
        val prefix = values.localUserPrefix()
        saveFile.writeText(
            SaveCodec.encodeValues(
                values +
                    mapOf(
                        "${prefix}party.x" to town.position.x.toString(),
                        "${prefix}party.y" to town.position.y.toString(),
                        "${prefix}party.gold" to gold.toString(),
                    ),
            ),
        )

        val screen = TextScreen()
        runSession(factory, "$name-enter", ScriptedInput("EP"), screen, saveFile, loadFile = saveFile)
        return screen
    }

    private fun CompiledGameBundle.withTownTax(tax: Int): CompiledGameBundle {
        val formula = content.ref(CompiledHandleKind.Formula, checkNotNull(systems.town?.entryTax?.amount))
        val formulas =
            content.formulas.map { current ->
                if (current.id ==
                    formula
                ) {
                    current.copy(value = CompiledIntExpr.Literal(tax))
                } else {
                    current
                }
            }
        return copy(content = content.copy(formulas = formulas))
    }

    private fun runSession(
        factory: RuntimeSessionFactory,
        name: String,
        input: RuntimeInputPort,
        screen: RuntimeScreenPort,
        saveFile: File,
        loadFile: File?,
    ) {
        RuntimeLog.open(testTmp.resolve("$name.log")).use { log ->
            factory.start(
                input = input,
                screen = screen,
                config =
                    RuntimeSessionConfig(
                        userId = "local",
                        stateSlug = "fresh-replay",
                        loadFile = loadFile,
                        saveFile = saveFile,
                        sessionDate = LocalDate.of(2026, 5, 11),
                    ),
                log = log,
            )
        }
    }

    private fun Map<String, String>.localUserPrefix(): String =
        (1..getValue("users.count").toInt())
            .map { index -> "user.$index." }
            .first { prefix -> get("${prefix}id") == "local" }

    /** Records each text screen the runtime writes. */
    private class TextScreen : NoOpRuntimeScreen() {
        val screens = mutableListOf<List<String>>()

        override fun writeText(lines: List<String>) {
            screens += lines
        }
    }
}
