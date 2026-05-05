package rylween

import rylween.dsl.*
import rylween.frontend.terminal.*
import rylween.game.*
import rylween.runtime.*
import rylween.runtime.town.TownSession
import rylween.runtime.wilderness.WildernessSession
import rylween.runtime.wilderness.WildernessSessionResultStatus
import rylween.runtime.wilderness.WildernessSessionStep
import rylween.runtime.wilderness.worldMapGrid
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
}

private class CheatedLongPlaythrough {
    private val game = RylweenCompiledGame.nextDsl
    private val renderer = TerminalTextRenderer(
        towns = game.towns,
        characters = game.characters,
        dungeons = game.dungeons,
        prompts = game.prompts,
    )
    private val worldMap = checkNotNull(game.worldMapGrid(Maps.wilderness))
    private val baseLocations = RylweenCompiledGame.nextRylween.states
        .single { it.id == States.freshReplay }
        .locations
    private val playthroughLocations = (
        baseLocations + listOf(
            LocationPlacement(Locations.hermitDungeon, GridPoint(45, 13)),
            LocationPlacement(Locations.thunTemple, GridPoint(41, 30)),
            LocationPlacement(Locations.koboldVillage, GridPoint(53, 10)),
            LocationPlacement(Locations.borinCamp, GridPoint(47, 28)),
            LocationPlacement(Locations.nolrimCrypt, GridPoint(11, 23)),
            LocationPlacement(Locations.masterCrownDungeon, GridPoint(22, 32)),
        )
    ).distinctBy { it.location }

    private val runtimeContent = game.runtimeContent().cheatedForLongPlaythrough()

    var runtime: RuntimeSessionState = RylweenCompiledGame.nextRylween.states
        .single { it.id == States.strongReplay }
        .toRuntimeSessionState()
        .withActiveParty(
            RylweenCompiledGame.nextRylween.states
                .single { it.id == States.strongReplay }
                .parties
                .single()
                .cheatedForLongPlaythrough(),
        )
        .copy(locations = playthroughLocations)
        private set

    val coveredBuildings = linkedSetOf<BuildingId>()
    val coveredMissions = linkedSetOf<MissionId>()
    val coveredDungeonHandoffs = linkedSetOf<DungeonId>()
    var ydargCompleted = false
        private set

    val allTownBuildings: Set<BuildingId> =
        game.towns.single { it.id == Towns.thord.id }.services.mapTo(linkedSetOf()) { it.building }

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

    fun visitTownService(building: BuildingId, inputs: List<String>, expectedText: String) {
        val terminal = ScenarioTerminalPort(*(listOf("P") + inputs).toTypedArray())
        val result = TownSessionTerminalAdapter(terminal, renderer).runUntilBlocked(
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
        val routeAndTownInputs = listOf(
            "2", "2", "E", "P",
            "M", "B", "A", "1", "P", "L",
            "S", "B", "W", "1", "P", "L",
            "I", "N", "N", "N", "L",
            "T", "N", "T", "1", "T", "", "L",
            "C", "L",
            "L",
            "X", "Y",
        )
        val terminal = ScenarioTerminalPort(*routeAndTownInputs.toTypedArray())
        val result = WildernessSessionTerminalAdapter(terminal, renderer).runUntilBlocked(
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

    private fun runMission(leg: MissionLeg) {
        val mission = game.missions.single { it.id == leg.id }
        val scene = game.scenes.single { it.id == mission.action }
        val terminal = ScenarioTerminalPort(*leg.inputs.toTypedArray())
        val result = WildernessSessionTerminalAdapter(terminal, renderer).runUntilBlocked(
            WildernessSession(
                runtime = runtime
                    .withActiveParty(runtime.activeParty.cheatedForLongPlaythrough())
                    .asWilderness(),
                worldMap = worldMap,
                content = runtimeContent,
                step = WildernessSessionStep.Mission(mission, scene),
            ),
        )

        assertTrue(
            result.status is TerminalRunStatus.Waiting || result.status is TerminalRunStatus.Finished,
            "mission ${leg.id.slug} ended with ${result.status}",
        )
        assertFalse(
            result.session.step is WildernessSessionStep.Mission,
            "mission ${leg.id.slug} is still waiting inside its script continuation; " +
                "status=${result.status}; step=${result.session.step}; output=${terminal.output.takeLast(30)}",
        )
        assertEquals(leg.id, result.session.lastResult?.mission, "mission ${leg.id.slug} result did not keep id")
        leg.expectedText?.let { text ->
            assertTrue(
                terminal.output.any { text in it },
                "mission ${leg.id.slug} did not render '$text'; output=${terminal.output.takeLast(40)}",
            )
        }
        assertNoUnresolvedText("mission ${leg.id.slug}", terminal.output)

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

        runtime = result.session.runtime.withActiveParty(result.session.party.cheatedForLongPlaythrough())
        coveredMissions += leg.id
    }

    fun finishYdarg() {
        val party = runtime.activeParty
            .cheatedForLongPlaythrough()
            .withMilestones(
                Milestones.silverKey,
                Milestones.fireShield,
                Milestones.masterCrown,
                Milestones.footOfJazzy,
                Milestones.braceletsOfThun,
            )
        val startRuntime = RuntimeSessionState.singleParty(
            party = party,
            hour = 12,
            currentTown = null,
            rngState = 1L,
            locations = playthroughLocations,
        )
        val session = YdargSession.start(startRuntime, runtimeContent).copy(
            step = YdargSessionStep.StairGive,
            floor = 4,
            point = GridPoint(5, 5),
        )
        val terminal = ScenarioTerminalPort(
            "4",
            "F", "A", "A",
            "F", "A", "A",
            "F", "A", "A",
            "F", "A", "A",
            "F", "A", "A",
        )
        val result = YdargSessionTerminalAdapter(terminal, renderer).runUntilBlocked(session)

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(YdargSessionStatus.Victory, result.session.lastResult?.status)
        assertTrue(terminal.output.any { "CONGRATULATIONS!" in it })
        assertNoUnresolvedText("Ydarg finale", terminal.output)
        runtime = result.session.runtime
        ydargCompleted = true
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

    private fun battleInputs(prefixReturns: Int = 0, suffixReturns: Int = 0): List<String> =
        List(prefixReturns) { "" } + listOf("F", "A", "A") + List(suffixReturns) { "" }

    private fun assertNoUnresolvedText(label: String, output: List<String>) {
        val unresolved = output.filter { "{" in it || "}" in it }
        assertTrue(unresolved.isEmpty(), "$label rendered unresolved text placeholders: $unresolved")
    }
}

private data class MissionLeg(
    val id: MissionId,
    val inputs: List<String>,
    val expectedEnd: MissionEnd,
    val expectedText: String? = null,
)

private sealed interface MissionEnd {
    object Completed : MissionEnd
    data class DungeonHandoff(val dungeon: DungeonId, val levels: Int) : MissionEnd
}

private fun RuntimeContent.cheatedForLongPlaythrough(): RuntimeContent =
    copy(
        monsters = monsters.map { monster ->
            monster.copy(
                count = 1..1,
                hitPoints = 1..1,
                weaponSkill = 0,
                spellPower = 0,
                immune = false,
            )
        },
        formulas = formulas.map { formula ->
            when (formula.id) {
                Formulas.rewardGoldParty,
                Formulas.rewardXpPerSurvivor -> formula.copy(value = IntAst.Lit(0))
                else -> formula
            }
        },
        rewards = rewards.map { reward ->
            reward.copy(loot = reward.loot.copy(dropChancePercent = 0))
        },
    )

private fun PartyState.cheatedForLongPlaythrough(): PartyState =
    copy(
        resources = resources.copy(
            gold = 50_000,
            torches = 80,
            food = 500,
            timeLeft = 500,
            restedHours = 14,
        ),
        inventory = (1..InventorySaveSlotMax).map { slot ->
            InventorySlotState(
                slot = slot,
                item = when (slot) {
                    1 -> Items.redGem
                    2 -> Items.blueGem
                    3 -> Items.greenGem
                    4 -> Items.redGem
                    5 -> Items.blueGem
                    else -> null
                },
            )
        },
        members = members.mapIndexed { index, member ->
            member.copy(
                level = maxOf(member.level, 28),
                health = 5000,
                hitPoints = 5000 of 5000,
                weaponSkill = 100,
                wisdom = maxOf(member.wisdom, 90),
                mana = 5000 of 5000,
                charisma = maxOf(member.charisma, 90),
                equipment = EquipmentState(
                    weapon = if (index == 0) Items.deathBlade else member.weapon,
                    armor = Items.admtMail,
                ),
                dead = false,
            )
        },
    )

private fun PartyState.withMilestones(vararg extra: GameMilestoneId): PartyState =
    copy(milestones = (milestones + extra).distinct())

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

private fun RuntimeSessionState.withHour(hour: Int): RuntimeSessionState =
    withRealm(realm.copy(hour = hour))
