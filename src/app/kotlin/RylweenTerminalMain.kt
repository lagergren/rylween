import java.io.File

private data class TerminalConfig(
    val town: TownId = Towns.thord.id,
    val hour: Int? = null,
    val seed: Long? = null,
    val state: StateId = States.freshReplay,
    val mapFile: File = File("data/map.dat"),
)

private class StdioTerminalPort : TerminalPort {
    override fun write(lines: List<String>) {
        lines.forEach(::println)
    }

    override fun readLine(): String? {
        print("> ")
        return kotlin.io.readlnOrNull()
    }
}

fun main(args: Array<String>) {
    val config = parseTerminalConfig(args.toList(), ::printTownUsage)
    val game = RylweenCompiledGame.nextRylween
    val content = RylweenCompiledGame.nextDsl
    val state = game.states.singleOrNull { it.id == config.state }
        ?: error("unknown state ${config.state.slug}")
    val party = state.parties.firstOrNull { it.id == state.realm.activeParty }
        ?: error("state ${state.id.slug} is missing active party ${state.realm.activeParty.slug}")
    val terminal = StdioTerminalPort()
    val engine = RylweenKotlinEngine(
        content = content,
        npcPlacementConfig = NpcPlacementRealizerConfig(
            wilderness = wildernessPlacementPolicy(config.mapFile),
        ),
    )
    var session = engine.townSession(
        party = party,
        town = config.town,
        hour = config.hour ?: state.realm.hour,
        rngState = config.seed ?: state.realm.rngState,
    )
    val adapter = TownSessionTerminalAdapter(
        terminal = terminal,
        renderer = engine.textRenderer(),
        serviceBridge = StatefulTownServiceBridge(terminal, speakContent = engine.speakContent()),
    )

    terminal.write(
        listOf(
            "Rylween Kotlin town runtime",
            "State: ${state.id.slug}",
            "Town: ${content.towns.single { it.id == config.town }.name}",
            "Ctrl-D exits.",
            "",
        ),
    )

    while (true) {
        val result = adapter.runUntilBlocked(session)
        session = result.session
        when (val status = result.status) {
            TerminalRunStatus.Finished -> {
                terminal.write(listOf("", "Town session finished."))
                return
            }
            TerminalRunStatus.StepLimitReached -> {
                terminal.write(listOf("", "Step limit reached."))
                return
            }
            is TerminalRunStatus.Waiting -> {
                val rejection = status.rejection
                if (rejection == null) {
                    terminal.write(listOf("", "Input unavailable."))
                    return
                }
                terminal.write(
                    listOf(
                        "Invalid input '${rejection.input}'.",
                        "Expected: ${rejection.expected.joinToString(", ")}",
                        "",
                    ),
                )
            }
        }
    }
}

object RylweenGameTerminalMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = parseTerminalConfig(args.toList(), ::printGameUsage)
        val game = RylweenCompiledGame.nextRylween
        val content = RylweenCompiledGame.nextDsl
        val baseState = game.states.singleOrNull { it.id == config.state }
            ?: error("unknown state ${config.state.slug}")
        val state = config.hour?.let { baseState.copy(realm = baseState.realm.copy(hour = it)) } ?: baseState
        val terminal = StdioTerminalPort()
        val worldMap = WorldMapGrid.loadReferenceMapDat(config.mapFile)
        val engine = RylweenKotlinEngine(
            content = content,
            npcPlacementConfig = NpcPlacementRealizerConfig(
                wilderness = WildernessNpcPlacementPolicy(allowedPoints = worldMap.passableNpcPoints()),
            ),
        )
        var session = engine.wildernessSession(
            state = state,
            worldMap = worldMap,
            rngState = config.seed ?: state.realm.rngState,
        )
        val adapter = WildernessSessionTerminalAdapter(
            terminal = terminal,
            renderer = engine.textRenderer(),
            serviceBridge = StatefulTownServiceBridge(terminal, speakContent = engine.speakContent()),
        )

        terminal.write(
            listOf(
                "Rylween Kotlin wilderness runtime",
                "State: ${state.id.slug}",
                "Ctrl-D exits.",
            ),
        )

        while (true) {
            val result = adapter.runUntilBlocked(session)
            session = result.session
            when (val status = result.status) {
                TerminalRunStatus.Finished -> {
                    terminal.write(listOf("", "Wilderness session finished."))
                    return
                }
                TerminalRunStatus.StepLimitReached -> {
                    terminal.write(listOf("", "Step limit reached."))
                    return
                }
                is TerminalRunStatus.Waiting -> {
                    val rejection = status.rejection
                    if (rejection == null) {
                        terminal.write(listOf("", "Input unavailable."))
                        return
                    }
                    terminal.write(
                        listOf(
                            "Invalid input '${rejection.input}'.",
                            "Expected: ${rejection.expected.joinToString(", ")}",
                            "",
                        ),
                    )
                }
            }
        }
    }
}

private fun parseTerminalConfig(args: List<String>, printUsage: () -> Unit): TerminalConfig {
    var config = TerminalConfig()
    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--town" -> {
                config = config.copy(town = townArg(args.valueAfter(index, arg)))
                index += 2
            }
            "--hour" -> {
                config = config.copy(hour = args.valueAfter(index, arg).toInt().mod(24))
                index += 2
            }
            "--seed" -> {
                config = config.copy(seed = args.valueAfter(index, arg).toLong())
                index += 2
            }
            "--state" -> {
                config = config.copy(state = stateId(args.valueAfter(index, arg)))
                index += 2
            }
            "--map" -> {
                config = config.copy(mapFile = File(args.valueAfter(index, arg)))
                index += 2
            }
            "--help", "-h" -> {
                printUsage()
                kotlin.system.exitProcess(0)
            }
            else -> error("unknown argument $arg")
        }
    }
    return config
}

private fun List<String>.valueAfter(index: Int, option: String): String =
    getOrNull(index + 1) ?: error("$option requires a value")

private fun townArg(value: String): TownId =
    when (value.lowercase()) {
        "thord" -> Towns.thord.id
        "ollk" -> Towns.ollk.id
        "wilor" -> Towns.wilor.id
        "murn" -> Towns.murn.id
        else -> town(value)
    }

private fun wildernessPlacementPolicy(mapFile: File): WildernessNpcPlacementPolicy =
    WildernessNpcPlacementPolicy(
        allowedPoints = if (mapFile.isFile) WorldMapGrid.loadReferenceMapDat(mapFile).passableNpcPoints() else null,
    )

private fun printTownUsage() {
    println(
        """
        |Usage: ./gradlew runKotlinTown --args="--town thord --hour 12 --seed 1"
        |
        |Options:
        |  --town   thord, ollk, wilor, murn, or a raw town slug
        |  --hour   0..23, defaults to the selected state hour
        |  --seed   runtime RNG state; omitted means deterministic fixture placement/events
        |  --state  state slug, defaults to fresh-replay
        |  --map    map file for wilderness NPC placement, defaults to data/map.dat
        """.trimMargin(),
    )
}

private fun printGameUsage() {
    println(
        """
        |Usage: ./gradlew runKotlinGame --args="--state fresh-replay --seed 1"
        |
        |Options:
        |  --state  state slug, defaults to fresh-replay
        |  --seed   runtime RNG state; omitted means deterministic fixture placement/events
        |  --hour   0..23, overrides the selected state hour
        |  --map    map file for wilderness movement and NPC hut projection, defaults to data/map.dat
        """.trimMargin(),
    )
}
