package rylween

import rylween.dsl.BoolAst
import rylween.dsl.BuildingId
import rylween.dsl.DungeonId
import rylween.dsl.EquipmentState
import rylween.dsl.FlagState
import rylween.dsl.GameMilestoneId
import rylween.dsl.GridPoint
import rylween.dsl.IntAst
import rylween.dsl.InventorySaveSlotMax
import rylween.dsl.InventorySlotState
import rylween.dsl.LocationPlacement
import rylween.dsl.MissionId
import rylween.dsl.MissionModel
import rylween.dsl.PartyState
import rylween.dsl.RuntimeContent
import rylween.dsl.RuntimeMode
import rylween.dsl.StaticScreenId
import rylween.dsl.TerrainId
import rylween.dsl.TownId
import rylween.dsl.Trigger
import rylween.dsl.Wilderness
import rylween.dsl.WorldPosition
import rylween.dsl.of
import rylween.dsl.runtimeContent
import rylween.frontend.terminal.TerminalAdapterResult
import rylween.frontend.terminal.TerminalRunStatus
import rylween.frontend.terminal.TerminalTextContext
import rylween.frontend.terminal.TerminalTextRenderer
import rylween.frontend.terminal.TownSessionTerminalAdapter
import rylween.frontend.terminal.WildernessSessionTerminalAdapter
import rylween.frontend.terminal.YdargSessionTerminalAdapter
import rylween.frontend.terminal.renderTerminalStaticScreen
import rylween.game.Buildings
import rylween.game.Dungeons
import rylween.game.Formulas
import rylween.game.Items
import rylween.game.Locations
import rylween.game.Maps
import rylween.game.Milestones
import rylween.game.Missions
import rylween.game.RylweenCompiledGame
import rylween.game.States
import rylween.game.StaticScreens
import rylween.game.Story
import rylween.game.Towns
import rylween.runtime.ReferenceRylweenRandom
import rylween.runtime.RuntimeSessionState
import rylween.runtime.WildernessDirection
import rylween.runtime.delta
import rylween.runtime.hasFlagState
import rylween.runtime.toRuntimeSessionState
import rylween.runtime.town.TownSession
import rylween.runtime.wilderness.WildernessSession
import rylween.runtime.wilderness.WildernessSessionResultStatus
import rylween.runtime.wilderness.WildernessSessionStep
import rylween.runtime.wilderness.worldMapGrid
import rylween.runtime.withFlagState
import rylween.runtime.ydarg.YdargSession
import rylween.runtime.ydarg.YdargSessionStatus
import rylween.runtime.ydarg.YdargSessionStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LongPlaythroughTest {
    @Test
    fun cheatedGoldenPathCoversTownServicesMissionsDungeonsAndYdarg() {
        val playthrough = CheatedLongPlaythrough()

        playthrough.visitTownService(
            building = Buildings.merchant,
            inputs = listOf("M", "B", "A", "1", "P", "L", "L"),
            expectedText = "Deal!",
        )
        playthrough.visitTownService(
            building = Buildings.smith,
            inputs = listOf("S", "B", "W", "1", "P", "L", "L"),
            expectedText = "Armor or Weapon?",
        )
        playthrough.visitTownService(
            building = Buildings.inn,
            inputs = listOf("I", "N", "N", "N", "L", "L"),
            expectedText = "Innkeeper",
        )
        playthrough.visitTownService(
            building = Buildings.tavern,
            inputs = listOf("T", "T", "1", "T", "", "L", "L"),
            expectedText = "Who wants to tell",
        )
        playthrough.visitTownService(
            building = Buildings.church,
            inputs = listOf("C", "L", "L"),
            expectedText = "church",
        )
        playthrough.visitTownService(
            building = Buildings.guild,
            inputs = listOf("G", "L", "L"),
            expectedText = "Guild",
        )

        playthrough.runAllMissionLegs()
        playthrough.finishYdarg()

        assertEquals(playthrough.allTownBuildings, playthrough.coveredBuildings)
        assertEquals(playthrough.allMissions, playthrough.coveredMissions)
        assertEquals(playthrough.expectedDungeonHandoffs, playthrough.coveredDungeonHandoffs)
        assertTrue(playthrough.ydargCompleted)
        assertTrue(Milestones.ydargVictory in playthrough.runtime.activeParty.milestones)
    }

    @Test
    fun cheatedWildernessRouteEntersThordAndVisitsEveryTownService() {
        val playthrough = CheatedLongPlaythrough()

        val terminal = playthrough.visitEveryThordServiceThroughWilderness()

        assertTrue(terminal.output.any { "At: Thord" in it })
        assertTrue(terminal.output.any { "Village of Thord" in it })
        assertTrue(terminal.output.any { "Deal!" in it })
        assertTrue(terminal.output.any { "Armor or Weapon?" in it })
        assertTrue(terminal.output.any { "Innkeeper" in it })
        assertTrue(terminal.output.any { "Who wants to tell" in it })
        assertTrue(terminal.output.any { "church" in it })
        assertEquals(playthrough.allTownBuildings, playthrough.coveredBuildings)
    }

    @Test
    fun cheatedWildernessTriggersEveryMissionThroughNormalMovement() {
        val playthrough = CheatedLongPlaythrough()

        playthrough.runAllMissionLegsThroughWildernessTriggers()

        assertEquals(playthrough.allMissions, playthrough.coveredMissions)
        assertEquals(playthrough.expectedDungeonHandoffs, playthrough.coveredDungeonHandoffs)
    }

    @Test
    fun cheatedMissionDungeonHandoffsResumeAfterLeavingDungeon() {
        val playthrough = CheatedLongPlaythrough()

        playthrough.runAllDungeonHandoffLegsThroughWildernessAndReturn()

        assertEquals(playthrough.dungeonHandoffMissions, playthrough.coveredMissions)
        assertEquals(playthrough.expectedDungeonHandoffs, playthrough.coveredDungeonHandoffs)
        assertEquals(playthrough.dungeonHandoffMissions, playthrough.coveredPostDungeonContinuations)
    }

    @Test
    fun cheatedMissionDungeonsReachFixedRewardsAndResumeAfterExit() {
        val playthrough = CheatedLongPlaythrough()

        playthrough.runAllFixedDungeonCompletionsThroughWilderness()

        assertEquals(playthrough.expectedDungeonHandoffs, playthrough.coveredFixedDungeonCompletions)
        assertEquals(playthrough.dungeonHandoffMissions, playthrough.coveredPostDungeonContinuations)
    }

    @Test
    fun cheatedAcceptancePlaythroughReachesYdargVictoryAndHeroesReport() {
        val playthrough = CheatedLongPlaythrough()

        val reports = playthrough.runAcceptancePlaythrough()

        assertEquals(playthrough.allTownBuildings, playthrough.coveredBuildings)
        assertEquals(playthrough.allMissions, playthrough.coveredMissions)
        assertEquals(playthrough.expectedDungeonHandoffs, playthrough.coveredFixedDungeonCompletions)
        assertEquals(playthrough.dungeonHandoffMissions, playthrough.coveredPostDungeonContinuations)
        assertTrue(playthrough.ydargCompleted)
        assertTrue(Milestones.ydargVictory in playthrough.runtime.activeParty.milestones)
        assertTrue(reports.scoreLines.any { line -> "Playtest" in line })
        assertTrue(reports.verboseScoreLines.any { line -> line == "  Leader             : Fighter" })
        assertTrue(reports.heroesLines.any { line -> line == "  Playtest defeated Baron Ydarg" })
    }
}

private class CheatedLongPlaythrough {
    private val game = RylweenCompiledGame.nextDsl
    private val renderer =
        TerminalTextRenderer(
            towns = game.towns,
            characters = game.characters,
            dungeons = game.dungeons,
            prompts = game.prompts,
        )
    private val worldMap = checkNotNull(game.worldMapGrid(Maps.wilderness))
    private val baseLocations =
        RylweenCompiledGame.nextRylween.states
            .single { it.id == States.freshReplay }
            .locations
    private val strongReplayState =
        RylweenCompiledGame.nextRylween.states
            .single { it.id == States.strongReplay }
    private val baseCheatedParty =
        strongReplayState
            .parties
            .single()
            .cheatedForLongPlaythrough()
    private val playthroughLocations =
        (
            baseLocations +
                listOf(
                    LocationPlacement(Locations.hermitDungeon, GridPoint(45, 13)),
                    LocationPlacement(Locations.thunTemple, GridPoint(41, 30)),
                    LocationPlacement(Locations.koboldVillage, GridPoint(53, 10)),
                    LocationPlacement(Locations.borinCamp, GridPoint(47, 28)),
                    LocationPlacement(Locations.nolrimCrypt, GridPoint(11, 23)),
                    LocationPlacement(Locations.masterCrownDungeon, GridPoint(22, 32)),
                )
        ).distinctBy { it.location }

    private val runtimeContent = game.runtimeContent().cheatedForLongPlaythrough()

    var runtime: RuntimeSessionState =
        strongReplayState
            .toRuntimeSessionState()
            .withActiveParty(baseCheatedParty)
            .copy(locations = playthroughLocations)
        private set

    val coveredBuildings = linkedSetOf<BuildingId>()
    val coveredMissions = linkedSetOf<MissionId>()
    val coveredDungeonHandoffs = linkedSetOf<DungeonId>()
    val coveredPostDungeonContinuations = linkedSetOf<MissionId>()
    val coveredFixedDungeonCompletions = linkedSetOf<DungeonId>()
    var ydargCompleted = false
        private set

    val allTownBuildings: Set<BuildingId> =
        game.towns
            .single { it.id == Towns.thord.id }
            .services
            .mapTo(linkedSetOf()) { it.building }

    val allMissions: Set<MissionId> =
        game.missions.mapTo(linkedSetOf()) { it.id }

    val expectedDungeonHandoffs: Set<DungeonId> =
        linkedSetOf(
            Dungeons.erethorTemple,
            Dungeons.ogreCave,
            Dungeons.fireShieldCave,
            Dungeons.thunTemple,
            Dungeons.reebarTomb,
            Dungeons.masterCrownDungeon,
            Dungeons.silverSanctum,
            Dungeons.hobgoblinStronghold,
        )

    val dungeonHandoffMissions: Set<MissionId>
        get() =
            missionLegs()
                .filter { leg -> leg.expectedEnd is MissionEnd.DungeonHandoff }
                .mapTo(linkedSetOf()) { leg -> leg.id }

    fun visitTownService(
        building: BuildingId,
        inputs: List<String>,
        expectedText: String,
    ) {
        val terminal = ScenarioTerminalPort(*(listOf("P") + inputs).toTypedArray())
        val result =
            TownSessionTerminalAdapter(terminal, renderer).runUntilBlocked(
                TownSession.start(
                    town = game.towns.single { it.id == Towns.thord.id },
                    runtime = runtime.asTown(Towns.thord.id).withHour(12),
                    content = runtimeContent,
                ),
            )

        assertTrue(
            result.status is TerminalRunStatus.Finished,
            "town service ${building.slug} did not finish; status=${result.status}; " +
                "step=${result.session.step}; output=${terminal.output.takeLast(20)}",
        )
        assertTrue(
            terminal.output.any { expectedText in it },
            "town service ${building.slug} did not render '$expectedText'",
        )
        assertNoUnresolvedText("town service ${building.slug}", terminal.output)
        runtime = result.session.runtime.withActiveParty(result.session.party.cheatedForLongPlaythrough())
        coveredBuildings += building
    }

    fun visitEveryThordServiceThroughWilderness(): ScenarioTerminalPort {
        val routeAndTownInputs =
            listOf(
                "2",
                "2",
                "E",
                "P",
                "M",
                "B",
                "A",
                "1",
                "P",
                "L",
                "S",
                "B",
                "W",
                "1",
                "P",
                "L",
                "I",
                "N",
                "N",
                "N",
                "L",
                "T",
                "N",
                "T",
                "1",
                "T",
                "",
                "L",
                "C",
                "L",
                "L",
                "X",
                "Y",
            )
        val terminal = ScenarioTerminalPort(*routeAndTownInputs.toTypedArray())
        val result =
            WildernessSessionTerminalAdapter(terminal, renderer).runUntilBlocked(
                WildernessSession.start(
                    content = runtimeContent,
                    runtime = runtime.withHour(12).asWilderness(),
                    worldMap = worldMap,
                ),
            )

        assertTrue(
            result.status is TerminalRunStatus.Finished,
            "wilderness Thord service circuit did not finish; status=${result.status}; " +
                "step=${result.session.step}; output=${terminal.output.takeLast(40)}",
        )
        assertEquals(Wilderness.xy(12, 11), result.session.party.position)
        assertNoUnresolvedText("wilderness Thord service circuit", terminal.output)
        runtime = result.session.runtime.withActiveParty(result.session.party.cheatedForLongPlaythrough())
        coveredBuildings += allTownBuildings
        return terminal
    }

    fun runAllMissionLegs() {
        missionLegs().forEach(::runMission)
    }

    fun runAllMissionLegsThroughWildernessTriggers() {
        missionLegs().forEach(::runTriggeredMission)
    }

    fun runAllDungeonHandoffLegsThroughWildernessAndReturn() {
        missionLegs()
            .filter { leg -> leg.expectedEnd is MissionEnd.DungeonHandoff }
            .forEach(::runTriggeredMissionThroughDungeonReturn)
    }

    fun runAllFixedDungeonCompletionsThroughWilderness() {
        fixedDungeonCompletionLegs().forEach(::runFixedDungeonCompletion)
    }

    fun runAcceptancePlaythrough(): AcceptanceReports {
        visitEveryThordServiceThroughWilderness()

        val fixedDungeonCompletions = fixedDungeonCompletionLegs().associateBy(DungeonCompletionLeg::mission)
        missionLegs().forEach { leg ->
            val completion = fixedDungeonCompletions[leg.id]
            if (completion == null) {
                runAcceptanceTriggeredMission(leg)
            } else {
                runFixedDungeonCompletion(completion, startParty = runtime.activeParty, applyRuntime = true)
            }
        }

        assertTrue(
            requiredYdargMilestones.all { milestone -> milestone in runtime.activeParty.milestones },
            "acceptance playthrough reached Ydarg without all power milestones; " +
                "milestones=${runtime.activeParty.milestones.map(GameMilestoneId::slug)}",
        )
        finishYdarg(grantPowerMilestones = false)
        return renderRuntimeReports()
    }

    private fun runMission(leg: MissionLeg) {
        val mission = game.missions.single { it.id == leg.id }
        val scene = game.scenes.single { it.id == mission.action }
        val terminal = ScenarioTerminalPort(*leg.inputs.toTypedArray())
        val result =
            WildernessSessionTerminalAdapter(terminal, renderer).runUntilBlocked(
                WildernessSession(
                    runtime =
                        runtime
                            .withActiveParty(runtime.activeParty.cheatedForLongPlaythrough())
                            .asWilderness(),
                    worldMap = worldMap,
                    content = runtimeContent,
                    step = WildernessSessionStep.Mission(mission, scene),
                ),
            )

        verifyMissionRun(leg, result, terminal.output)
    }

    private fun runTriggeredMission(leg: MissionLeg) {
        val mission = game.missions.single { it.id == leg.id }
        val route = routeToTrigger(mission.trigger)
        val terminalRun = runTriggeredMission(leg, mission, route)

        verifyMissionRun(leg, terminalRun.result, terminalRun.output, applyRuntime = false)
    }

    private fun runAcceptanceTriggeredMission(leg: MissionLeg) {
        val mission = game.missions.single { it.id == leg.id }
        val route = routeToTrigger(mission.trigger)
        val terminalRun =
            runTriggeredMission(
                leg = leg,
                mission = mission,
                route = route,
                startParty = runtime.activeParty,
            )

        verifyMissionRun(leg, terminalRun.result, terminalRun.output)
    }

    private fun runTriggeredMissionThroughDungeonReturn(leg: MissionLeg) {
        val expectedEnd =
            leg.expectedEnd as? MissionEnd.DungeonHandoff
                ?: error("mission ${leg.id.slug} does not hand off to a dungeon")
        val mission = game.missions.single { it.id == leg.id }
        val route = routeToTrigger(mission.trigger).copy(rngState = firstStandardDungeonMessageSeed())
        val terminalRun =
            runTriggeredMission(
                leg = leg,
                mission = mission,
                route = route,
                extraInputs = listOf("", "A"),
            )

        verifyPostDungeonContinuation(leg, expectedEnd, terminalRun.result, terminalRun.output)
    }

    private fun runFixedDungeonCompletion(
        completion: DungeonCompletionLeg,
        startParty: PartyState = baseCheatedParty,
        applyRuntime: Boolean = false,
    ) {
        val leg = missionLegs().single { it.id == completion.mission }
        val mission = game.missions.single { it.id == leg.id }
        val route = routeToTrigger(mission.trigger)
        val terminalRun =
            runTriggeredMission(
                leg = leg,
                mission = mission,
                route = route,
                extraInputs = completion.inputs,
                content = runtimeContent.withFixedDungeonEventsOnly(),
                startParty = startParty,
            )

        verifyPostDungeonContinuation(leg, completion.expectedEnd, terminalRun.result, terminalRun.output)
        completion.expectedFlags.forEach { flag ->
            assertTrue(
                terminalRun.result.session.party
                    .hasFlagState(flag),
                "mission ${leg.id.slug} did not set ${flag.flag.slug}=${flag.native}",
            )
        }
        completion.expectedMilestones.forEach { milestone ->
            assertTrue(
                milestone in terminalRun.result.session.party.milestones,
                "mission ${leg.id.slug} did not achieve ${milestone.slug}",
            )
        }
        completion.expectedText?.let { text ->
            assertTrue(
                terminalRun.output.any { text in it },
                "mission ${leg.id.slug} did not render fixed dungeon text '$text'",
            )
        }
        coveredFixedDungeonCompletions += completion.expectedEnd.dungeon
        if (applyRuntime) {
            runtime =
                terminalRun.result.session.runtime
                    .withActiveParty(
                        terminalRun.result.session.party
                            .cheatedForLongPlaythrough(),
                    )
        }
    }

    private fun runTriggeredMission(
        leg: MissionLeg,
        mission: MissionModel,
        route: TriggerRoute,
        extraInputs: List<String> = emptyList(),
        content: RuntimeContent = runtimeContent,
        startParty: PartyState = baseCheatedParty,
    ): MissionTerminalRun {
        val party =
            startParty
                .cheatedForLongPlaythrough()
                .copy(position = WorldPosition.Wilderness(route.start))
                .withRequirement(mission.requires)
        val startRuntime =
            runtime
                .withActiveParty(party)
                .withRealm(
                    runtime.realm.copy(
                        hour = 12,
                        rngState = route.rngState,
                        currentMap = Maps.wilderness,
                        currentMode = RuntimeMode.Wilderness,
                        currentTown = null,
                    ),
                ).copy(locations = playthroughLocations)
        val terminal = ScenarioTerminalPort(*(route.inputs + leg.inputs + extraInputs).toTypedArray())
        val result =
            WildernessSessionTerminalAdapter(terminal, renderer).runUntilBlocked(
                WildernessSession.start(
                    content = content,
                    runtime = startRuntime.asWilderness(),
                    worldMap = worldMap,
                ),
            )

        return MissionTerminalRun(result, terminal.output)
    }

    private fun verifyMissionRun(
        leg: MissionLeg,
        result: TerminalAdapterResult<WildernessSession>,
        output: List<String>,
        applyRuntime: Boolean = true,
    ) {
        assertTrue(
            result.status is TerminalRunStatus.Waiting || result.status is TerminalRunStatus.Finished,
            "mission ${leg.id.slug} ended with ${result.status}",
        )
        assertFalse(
            result.session.step is WildernessSessionStep.Mission,
            "mission ${leg.id.slug} is still waiting inside its script continuation; " +
                "status=${result.status}; step=${result.session.step}; output=${output.takeLast(30)}",
        )
        assertEquals(leg.id, result.session.lastResult?.mission, "mission ${leg.id.slug} result did not keep id")
        leg.expectedText?.let { text ->
            assertTrue(
                output.any { text in it },
                "mission ${leg.id.slug} did not render '$text'; output=${output.takeLast(40)}",
            )
        }
        assertNoUnresolvedText("mission ${leg.id.slug}", output)

        when (leg.expectedEnd) {
            is MissionEnd.Completed ->
                assertEquals(WildernessSessionResultStatus.MissionCompleted, result.session.lastResult?.status)
            is MissionEnd.DungeonHandoff -> {
                assertEquals(WildernessSessionResultStatus.DungeonHandoff, result.session.lastResult?.status)
                assertEquals(leg.expectedEnd.dungeon, result.session.lastResult?.dungeon)
                assertEquals(leg.expectedEnd.levels, result.session.lastResult?.dungeonLevels)
                coveredDungeonHandoffs += leg.expectedEnd.dungeon
            }
        }

        if (applyRuntime) {
            runtime = result.session.runtime.withActiveParty(result.session.party.cheatedForLongPlaythrough())
        }
        coveredMissions += leg.id
    }

    private fun verifyPostDungeonContinuation(
        leg: MissionLeg,
        expectedEnd: MissionEnd.DungeonHandoff,
        result: TerminalAdapterResult<WildernessSession>,
        output: List<String>,
    ) {
        assertTrue(
            result.status is TerminalRunStatus.Waiting,
            "mission ${leg.id.slug} post-dungeon run did not stop at wilderness input; " +
                "status=${result.status}; step=${result.session.step}; output=${output.takeLast(40)}",
        )
        assertTrue(
            result.session.step is WildernessSessionStep.Explore,
            "mission ${leg.id.slug} post-dungeon run did not return to wilderness explore; " +
                "step=${result.session.step}; output=${output.takeLast(40)}",
        )
        assertEquals(RuntimeMode.Wilderness, result.session.runtime.realm.currentMode)
        assertEquals(WildernessSessionResultStatus.MissionCompleted, result.session.lastResult?.status)
        assertEquals(leg.id, result.session.lastResult?.mission)
        assertTrue(
            output.any { "You leave the" in it },
            "mission ${leg.id.slug} did not visibly leave dungeon ${expectedEnd.dungeon.slug}",
        )
        assertNoUnresolvedText("mission ${leg.id.slug} post-dungeon continuation", output)

        coveredDungeonHandoffs += expectedEnd.dungeon
        coveredMissions += leg.id
        coveredPostDungeonContinuations += leg.id
    }

    fun finishYdarg(grantPowerMilestones: Boolean = true) {
        val party =
            runtime.activeParty
                .cheatedForLongPlaythrough()
                .let { party ->
                    if (grantPowerMilestones) {
                        party.withMilestones(*requiredYdargMilestones.toTypedArray())
                    } else {
                        party
                    }
                }
        val startRuntime =
            RuntimeSessionState.singleParty(
                party = party,
                hour = 12,
                currentTown = null,
                rngState = 1L,
                locations = playthroughLocations,
            )
        val session =
            YdargSession.start(startRuntime, runtimeContent).copy(
                step = YdargSessionStep.StairGive,
                floor = 4,
                point = GridPoint(5, 5),
            )
        val terminal =
            ScenarioTerminalPort(
                "4",
                "F",
                "A",
                "A",
                "F",
                "A",
                "A",
                "F",
                "A",
                "A",
                "F",
                "A",
                "A",
                "F",
                "A",
                "A",
            )
        val result = YdargSessionTerminalAdapter(terminal, renderer).runUntilBlocked(session)

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(YdargSessionStatus.Victory, result.session.lastResult?.status)
        assertTrue(terminal.output.any { "CONGRATULATIONS!" in it })
        assertNoUnresolvedText("Ydarg finale", terminal.output)
        runtime = result.session.runtime
        ydargCompleted = true
    }

    private fun renderRuntimeReports(): AcceptanceReports {
        val context = TerminalTextContext(runtime = runtime)

        fun render(
            screen: StaticScreenId,
            label: String,
        ): List<String> {
            val rendered =
                checkNotNull(
                    game.renderTerminalStaticScreen(
                        id = screen,
                        renderer = renderer,
                        resourceText = { error("$label should render from runtime state") },
                        context = context,
                    ),
                )
            val lines = rendered.pages.flatten()
            assertNoUnresolvedText(label, lines)
            return lines
        }

        return AcceptanceReports(
            scoreLines = render(StaticScreens.score, "acceptance score report"),
            verboseScoreLines = render(StaticScreens.verboseScore, "acceptance verbose score report"),
            heroesLines = render(StaticScreens.heroes, "acceptance heroes report"),
        )
    }

    private fun missionLegs(): List<MissionLeg> =
        listOf(
            MissionLeg(
                Missions.erethorEntry,
                listOf("E"),
                MissionEnd.DungeonHandoff(Dungeons.erethorTemple, levels = 12),
                expectedText = "Here is the temple",
            ),
            MissionLeg(
                Missions.husbandCave,
                listOf("E"),
                MissionEnd.DungeonHandoff(Dungeons.ogreCave, levels = 7),
                expectedText = "There is a cave entrance",
            ),
            MissionLeg(
                Missions.bandits,
                battleInputs(prefixReturns = 1, suffixReturns = 2),
                MissionEnd.Completed,
                expectedText = "cargo",
            ),
            MissionLeg(
                Missions.trollTooth,
                battleInputs(suffixReturns = 1),
                MissionEnd.Completed,
                expectedText = "troll",
            ),
            MissionLeg(
                Missions.hermitDungeon,
                listOf("E"),
                MissionEnd.DungeonHandoff(Dungeons.fireShieldCave, levels = 20),
                expectedText = "You have found a dungeon",
            ),
            MissionLeg(
                Missions.thunTemple,
                listOf("", "", "Y", ""),
                MissionEnd.DungeonHandoff(Dungeons.thunTemple, levels = 18),
                expectedText = "CRUNG",
            ),
            MissionLeg(
                Missions.koboldVillage,
                listOf("A", "") + battleInputs() + listOf("") + battleInputs() + listOf("") + battleInputs() + listOf(""),
                MissionEnd.Completed,
                expectedText = "ugly pendant",
            ),
            MissionLeg(
                Missions.skullweed,
                listOf(""),
                MissionEnd.Completed,
                expectedText = "skullweed",
            ),
            MissionLeg(
                Missions.borinMeet,
                List(6) { "" },
                MissionEnd.Completed,
                expectedText = "Nolrim's crypt",
            ),
            MissionLeg(
                Missions.nolrimCrypt,
                listOf("E"),
                MissionEnd.DungeonHandoff(Dungeons.reebarTomb, levels = 20),
                expectedText = "crypt",
            ),
            MissionLeg(
                Missions.borinGift,
                listOf("", ""),
                MissionEnd.Completed,
                expectedText = "3578 gold",
            ),
            MissionLeg(
                Missions.masterCrown,
                listOf("E"),
                MissionEnd.DungeonHandoff(Dungeons.masterCrownDungeon, levels = 10),
                expectedText = "Master Crown",
            ),
            MissionLeg(
                Missions.ogreCave,
                listOf("E", "") + battleInputs() + listOf("") + battleInputs() + List(4) { "" },
                MissionEnd.Completed,
                expectedText = "Kastagir",
            ),
            MissionLeg(
                Missions.jazzyFoot,
                listOf("", "") + battleInputs() + listOf("", "") + battleInputs() + listOf(""),
                MissionEnd.Completed,
                expectedText = "Today, you have killed",
            ),
            MissionLeg(
                Missions.silverKey,
                listOf("E"),
                MissionEnd.DungeonHandoff(Dungeons.silverSanctum, levels = 20),
                expectedText = "Silver Key",
            ),
            MissionLeg(
                Missions.hobgoblinCaravan,
                List(2) { "" } + battleInputs() + List(2) { "" },
                MissionEnd.Completed,
                expectedText = "Boss has it",
            ),
            MissionLeg(
                Missions.hobgoblinCave,
                listOf("E"),
                MissionEnd.DungeonHandoff(Dungeons.hobgoblinStronghold, levels = 22),
                expectedText = "hobgoblin cave",
            ),
        )

    private fun fixedDungeonCompletionLegs(): List<DungeonCompletionLeg> =
        listOf(
            DungeonCompletionLeg(
                mission = Missions.erethorEntry,
                expectedEnd = MissionEnd.DungeonHandoff(Dungeons.erethorTemple, levels = 12),
                inputs =
                    completeDungeon(
                        levels = 12,
                        descendEvents =
                            mapOf(
                                3 to erethorPriestsInputs(),
                                7 to erethorPriestsInputs(),
                                12 to luteChamberInputs(),
                            ),
                        ascendRecurringEvents =
                            mapOf(
                                7 to erethorPriestsInputs(),
                                3 to erethorPriestsInputs(),
                            ),
                    ),
                expectedFlags = listOf(Story.erethor.lute.luteTaken),
                expectedText = "Lute",
            ),
            DungeonCompletionLeg(
                mission = Missions.husbandCave,
                expectedEnd = MissionEnd.DungeonHandoff(Dungeons.ogreCave, levels = 7),
                inputs =
                    completeDungeon(
                        levels = 7,
                        descendEvents =
                            mapOf(
                                6 to orcNestInputs(),
                                7 to listOf(""),
                            ),
                    ),
                expectedFlags = listOf(Story.peasant.wife.rescued),
                expectedText = "wife is in",
            ),
            DungeonCompletionLeg(
                mission = Missions.hermitDungeon,
                expectedEnd = MissionEnd.DungeonHandoff(Dungeons.fireShieldCave, levels = 20),
                inputs =
                    completeDungeon(
                        levels = 20,
                        descendEvents =
                            mapOf(
                                19 to (listOf("") + battleInputs()),
                                20 to listOf(""),
                            ),
                    ),
                expectedFlags = listOf(Story.hermit.trollTooth.fireShieldAcquired),
                expectedMilestones = listOf(Milestones.fireShield),
                expectedText = "FIRE SHIELD",
            ),
            DungeonCompletionLeg(
                mission = Missions.thunTemple,
                expectedEnd = MissionEnd.DungeonHandoff(Dungeons.thunTemple, levels = 18),
                inputs =
                    completeDungeon(
                        levels = 18,
                        descendEvents =
                            mapOf(
                                4 to thunRiddleInputs("water"),
                                8 to thunRiddleInputs("night"),
                                12 to thunRiddleInputs("a well"),
                                18 to listOf(""),
                            ),
                    ),
                expectedFlags = listOf(Story.bracelets.thun.braceletsTaken),
                expectedMilestones = listOf(Milestones.braceletsOfThun),
                expectedText = "Bracelets of Thun",
            ),
            DungeonCompletionLeg(
                mission = Missions.nolrimCrypt,
                expectedEnd = MissionEnd.DungeonHandoff(Dungeons.reebarTomb, levels = 20),
                inputs =
                    completeDungeon(
                        levels = 20,
                        descendEvents =
                            mapOf(
                                12 to listOf(""),
                                13 to colourRiddleInputs(13),
                                14 to colourRiddleInputs(14),
                                15 to colourRiddleInputs(15),
                                16 to colourRiddleInputs(16),
                                17 to colourRiddleInputs(17),
                                18 to colourRiddleInputs(18),
                                19 to colourRiddleInputs(19),
                                20 to listOf(""),
                            ),
                        ascendRecurringEvents =
                            mapOf(
                                19 to colourRiddleInputs(19),
                                18 to colourRiddleInputs(18),
                                17 to colourRiddleInputs(17),
                                16 to colourRiddleInputs(16),
                                15 to colourRiddleInputs(15),
                                14 to colourRiddleInputs(14),
                                13 to colourRiddleInputs(13),
                                12 to listOf(""),
                            ),
                    ),
                expectedFlags = listOf(Story.masterCrown.daggerTaken),
                expectedText = "dagger you seek",
            ),
            DungeonCompletionLeg(
                mission = Missions.masterCrown,
                expectedEnd = MissionEnd.DungeonHandoff(Dungeons.masterCrownDungeon, levels = 10),
                inputs =
                    completeDungeon(
                        levels = 10,
                        descendEvents =
                            mapOf(
                                10 to (listOf("", "") + battleInputs()),
                            ),
                    ),
                expectedFlags = listOf(Story.masterCrown.crownTaken),
                expectedMilestones = listOf(Milestones.masterCrown),
                expectedText = "Master Crown",
            ),
            DungeonCompletionLeg(
                mission = Missions.silverKey,
                expectedEnd = MissionEnd.DungeonHandoff(Dungeons.silverSanctum, levels = 20),
                inputs =
                    completeDungeon(
                        levels = 20,
                        descendEvents =
                            mapOf(
                                20 to List(4) { "" },
                            ),
                    ),
                expectedFlags = listOf(Story.silverKey.noteFound),
                expectedText = "Master Thief",
            ),
            DungeonCompletionLeg(
                mission = Missions.hobgoblinCave,
                expectedEnd = MissionEnd.DungeonHandoff(Dungeons.hobgoblinStronghold, levels = 22),
                inputs =
                    completeDungeon(
                        levels = 22,
                        descendEvents =
                            mapOf(
                                3 to hobgoblinPatrolInputs(note = true),
                                6 to hobgoblinPatrolInputs(note = true),
                                9 to hobgoblinPatrolInputs(note = true),
                                14 to hobgoblinPatrolInputs(),
                                17 to hobgoblinPatrolInputs(),
                                19 to hobgoblinPatrolInputs(),
                                20 to (listOf("") + battleInputs()),
                                21 to (listOf("") + battleInputs()),
                                22 to hobgoblinChiefInputs(),
                            ),
                        ascendRecurringEvents =
                            mapOf(
                                19 to hobgoblinPatrolInputs(),
                                17 to hobgoblinPatrolInputs(),
                                14 to hobgoblinPatrolInputs(note = true),
                                9 to hobgoblinPatrolInputs(),
                                6 to hobgoblinPatrolInputs(),
                                3 to hobgoblinPatrolInputs(),
                            ),
                    ),
                expectedFlags = listOf(Story.silverKey.keyTaken),
                expectedMilestones = listOf(Milestones.silverKey),
                expectedText = "precious Silver Key",
            ),
        )

    private fun battleInputs(
        prefixReturns: Int = 0,
        suffixReturns: Int = 0,
    ): List<String> = List(prefixReturns) { "" } + listOf("F", "A", "A") + List(suffixReturns) { "" }

    private fun completeDungeon(
        levels: Int,
        descendEvents: Map<Int, List<String>>,
        ascendRecurringEvents: Map<Int, List<String>> = emptyMap(),
    ): List<String> =
        buildList {
            (2..levels).forEach { level ->
                add("D")
                addAll(descendEvents[level].orEmpty())
            }
            (levels - 1 downTo 1).forEach { level ->
                add("A")
                addAll(ascendRecurringEvents[level].orEmpty())
            }
            add("A")
        }

    private fun erethorPriestsInputs(): List<String> = listOf("") + battleInputs()

    private fun luteChamberInputs(): List<String> =
        List(5) { "" } +
            battleInputs() +
            battleInputs() +
            listOf("") +
            battleInputs() +
            List(3) { "" } +
            battleInputs() +
            List(2) { "" }

    private fun orcNestInputs(): List<String> = listOf("") + battleInputs() + listOf("") + battleInputs() + battleInputs() + listOf("")

    private fun thunRiddleInputs(answer: String): List<String> = listOf("", answer, "")

    private fun colourRiddleInputs(level: Int): List<String> = listOf("", "", (20 - level).toString(), "")

    private fun hobgoblinPatrolInputs(note: Boolean = false): List<String> =
        listOf("") + battleInputs() + if (note) listOf("") else emptyList()

    private fun hobgoblinChiefInputs(): List<String> = listOf("", "RISH EKKIN", "") + battleInputs() + listOf("")

    private fun routeToTrigger(trigger: Trigger): TriggerRoute =
        when (trigger) {
            is Trigger.AtPosition -> routeToPoint(trigger.point)
            is Trigger.AtLocation ->
                routeToPoint(
                    checkNotNull(playthroughLocations.firstOrNull { it.location == trigger.location }?.point) {
                        "mission trigger location ${trigger.location.slug} is not placed in the playthrough state"
                    },
                )
            is Trigger.OnTerrain -> routeToTerrain(trigger.terrain)
            is Trigger.RandomStep -> randomStepRoute(trigger.percent)
        }

    private fun routeToTerrain(terrain: TerrainId): TriggerRoute {
        val route =
            worldMap.rows.indices
                .asSequence()
                .flatMap { y ->
                    val row = worldMap.rows[y]
                    row.indices.asSequence().map { x -> GridPoint(x, y) }
                }.firstNotNullOfOrNull { point ->
                    if (worldMap.isPassable(point) && point.terrainId() == terrain) {
                        routeToPointOrNull(point)
                    } else {
                        null
                    }
                }
        return checkNotNull(route) { "no reachable terrain tile for mission trigger ${terrain.slug}" }
    }

    private fun randomStepRoute(percent: Int): TriggerRoute {
        val route = routeToPoint(GridPoint(12, 10))
        return route.copy(rngState = firstRandomStepSeed(percent))
    }

    private fun routeToPoint(point: GridPoint): TriggerRoute =
        checkNotNull(routeToPointOrNull(point)) { "no adjacent passable route to ${point.x}x${point.y}" }

    private fun routeToPointOrNull(point: GridPoint): TriggerRoute? {
        if (!worldMap.isPassable(point)) return null
        return DirectionRoute.entries.firstNotNullOfOrNull { route ->
            val delta = route.direction.delta()
            val start = GridPoint(point.x - delta.x, point.y - delta.y)
            TriggerRoute(start, listOf(route.input)).takeIf { worldMap.isPassable(start) }
        }
    }

    private fun GridPoint.terrainId(): TerrainId? = worldMap.tileAt(this)?.let { tile -> worldMap.terrain.byMapCode(tile)?.id }

    private fun firstRandomStepSeed(percent: Int): Long =
        generateSequence(1L) { it + 1L }
            .first { seed -> ReferenceRylweenRandom.draw(seed, 0, 99).value < percent }

    private fun firstStandardDungeonMessageSeed(): Long =
        generateSequence(1L) { it + 1L }
            .first { seed -> ReferenceRylweenRandom.draw(seed, 1, 9).value in 6..7 }

    private val requiredYdargMilestones: Set<GameMilestoneId> =
        linkedSetOf(
            Milestones.silverKey,
            Milestones.fireShield,
            Milestones.masterCrown,
            Milestones.footOfJazzy,
            Milestones.braceletsOfThun,
        )

    private fun assertNoUnresolvedText(
        label: String,
        output: List<String>,
    ) {
        val unresolved = output.filter { "{" in it || "}" in it }
        assertTrue(unresolved.isEmpty(), "$label rendered unresolved text placeholders: $unresolved")
    }
}

private data class TriggerRoute(
    val start: GridPoint,
    val inputs: List<String>,
    val rngState: Long = 1L,
)

private data class MissionTerminalRun(
    val result: TerminalAdapterResult<WildernessSession>,
    val output: List<String>,
)

private data class AcceptanceReports(
    val scoreLines: List<String>,
    val verboseScoreLines: List<String>,
    val heroesLines: List<String>,
)

private data class DungeonCompletionLeg(
    val mission: MissionId,
    val expectedEnd: MissionEnd.DungeonHandoff,
    val inputs: List<String>,
    val expectedFlags: List<FlagState> = emptyList(),
    val expectedMilestones: List<GameMilestoneId> = emptyList(),
    val expectedText: String? = null,
)

private enum class DirectionRoute(
    val direction: WildernessDirection,
    val input: String,
) {
    South(WildernessDirection.South, "2"),
    North(WildernessDirection.North, "8"),
    East(WildernessDirection.East, "6"),
    West(WildernessDirection.West, "4"),
}

private data class MissionLeg(
    val id: MissionId,
    val inputs: List<String>,
    val expectedEnd: MissionEnd,
    val expectedText: String? = null,
)

private sealed interface MissionEnd {
    object Completed : MissionEnd

    data class DungeonHandoff(
        val dungeon: DungeonId,
        val levels: Int,
    ) : MissionEnd
}

private fun RuntimeContent.cheatedForLongPlaythrough(): RuntimeContent =
    copy(
        monsters =
            monsters.map { monster ->
                monster.copy(
                    count = 1..1,
                    hitPoints = 1..1,
                    weaponSkill = 0,
                    spellPower = 0,
                    immune = false,
                )
            },
        formulas =
            formulas.map { formula ->
                when (formula.id) {
                    Formulas.rewardGoldParty,
                    Formulas.rewardXpPerSurvivor,
                    -> formula.copy(value = IntAst.Lit(0))
                    else -> formula
                }
            },
        rewards =
            rewards.map { reward ->
                reward.copy(loot = reward.loot.copy(dropChancePercent = 0))
            },
    )

private fun RuntimeContent.withFixedDungeonEventsOnly(): RuntimeContent =
    copy(
        dungeons =
            dungeons.copy(
                scenarios =
                    dungeons.scenarios.map { scenario ->
                        scenario.copy(randomEvents = emptyList())
                    },
            ),
    )

private fun PartyState.cheatedForLongPlaythrough(): PartyState =
    copy(
        resources =
            resources.copy(
                gold = 50_000,
                torches = 80,
                food = 500,
                timeLeft = 500,
                restedHours = 14,
            ),
        inventory =
            (1..InventorySaveSlotMax).map { slot ->
                InventorySlotState(
                    slot = slot,
                    item =
                        when (slot) {
                            1 -> Items.redGem
                            2 -> Items.blueGem
                            3 -> Items.greenGem
                            4 -> Items.redGem
                            5 -> Items.blueGem
                            else -> null
                        },
                )
            },
        members =
            members.mapIndexed { index, member ->
                member.copy(
                    level = maxOf(member.level, 28),
                    health = 5000,
                    hitPoints = 5000 of 5000,
                    weaponSkill = 100,
                    wisdom = maxOf(member.wisdom, 90),
                    mana = 5000 of 5000,
                    charisma = maxOf(member.charisma, 90),
                    equipment =
                        EquipmentState(
                            weapon = if (index == 0) Items.deathBlade else member.weapon,
                            armor = Items.admtMail,
                        ),
                    dead = false,
                )
            },
    )

private fun PartyState.withMilestones(vararg extra: GameMilestoneId): PartyState = copy(milestones = (milestones + extra).distinct())

private fun PartyState.withRequirement(requirement: BoolAst?): PartyState =
    when (requirement) {
        null -> this
        is BoolAst.And -> withRequirement(requirement.left).withRequirement(requirement.right)
        is BoolAst.FlagIs -> withFlagState(requirement.slug, requirement.nativeValue)
        is BoolAst.MilestoneAchieved -> withMilestones(requirement.milestone)
        else -> error("mission trigger playthrough cannot synthesize requirement $requirement")
    }

private fun RuntimeSessionState.asTown(town: TownId): RuntimeSessionState =
    withRealm(
        realm.copy(
            currentMode = RuntimeMode.Town,
            currentTown = town,
        ),
    )

private fun RuntimeSessionState.asWilderness(): RuntimeSessionState =
    withRealm(
        realm.copy(
            currentMode = RuntimeMode.Wilderness,
            currentTown = null,
            currentMap = Maps.wilderness,
        ),
    )

private fun RuntimeSessionState.withHour(hour: Int): RuntimeSessionState = withRealm(realm.copy(hour = hour))
