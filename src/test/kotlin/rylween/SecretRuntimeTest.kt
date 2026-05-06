package rylween

import rylween.dsl.RuntimeMode
import rylween.dsl.SecretActionModel
import rylween.dsl.SecretPlayerModel
import rylween.dsl.Wilderness
import rylween.dsl.WorldPosition
import rylween.dsl.item
import rylween.dsl.matches
import rylween.dsl.monster
import rylween.dsl.runtimeContent
import rylween.dsl.worldMap
import rylween.dsl.xy
import rylween.game.Items
import rylween.game.Maps
import rylween.game.Monsters
import rylween.game.RylweenCompiledGame
import rylween.game.States
import rylween.runtime.RuntimeSessionState
import rylween.runtime.WildernessDirection
import rylween.runtime.inputForToken
import rylween.runtime.inventoryItem
import rylween.runtime.party
import rylween.runtime.wilderness.WildernessSession
import rylween.runtime.wilderness.WildernessSessionInput
import rylween.runtime.wilderness.WildernessSessionResultStatus
import rylween.runtime.wilderness.WildernessSessionStep
import rylween.runtime.wilderness.WorldMapGrid
import rylween.runtime.wilderness.exploreCommands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RylweenSecretRuntimeTest {
    @Test
    fun compiledSecretsDeclareReferenceActions() {
        val secrets = RylweenCompiledGame.nextDsl.secrets
        val marcus = secrets.players.single { it.playerName == "Marcus Lagergren" }
        val karl = secrets.players.single { it.playerName == "Karl Skagerberg" }

        assertEquals(6, marcus.keyActions.size)
        assertEquals(SecretActionModel.AddTimeLeft(amount = 50, refreshStatus = true), marcus.actionFor("/"))
        assertEquals(SecretActionModel.SetInventory(slot = 12, item = Items.drumsOfDeath), marcus.actionFor("("))
        assertEquals(SecretActionModel.SetInventory(slot = 11, item = Items.gemOfResurrection), marcus.actionFor(")"))
        assertEquals(SecretActionModel.SetInventory(slot = 10, item = Items.kaelsAxe), marcus.actionFor("&"))
        assertEquals(SecretActionModel.SetInventory(slot = 9, item = Items.holyHandgrenade), marcus.actionFor("*"))
        assertEquals(SecretActionModel.AddGold(amount = 1000, refreshStatus = true), marcus.actionFor("+"))
        assertEquals(101, karl.stepTrigger?.everySteps)
        assertEquals(SecretActionModel.SpawnEncounter(Monsters.skagerbergKiller), karl.stepTrigger?.action)
    }

    @Test
    fun marcusSecretKeysMutatePartyResourcesAndInventory() {
        val session = secretSession(playerName = "Marcus Lagergren", timeLeft = 100, gold = 25)

        val afterTime = session.apply(assertNotNull(session.exploreCommands().inputForToken("/")))
        val afterGold = afterTime.apply(assertNotNull(afterTime.exploreCommands().inputForToken("+")))
        val afterItem = afterGold.apply(assertNotNull(afterGold.exploreCommands().inputForToken("(")))

        assertEquals(150, afterItem.party.resources.timeLeft)
        assertEquals(1025, afterItem.party.resources.gold)
        assertEquals(Items.drumsOfDeath, afterItem.party.inventoryItem(12))
        assertEquals(WildernessSessionResultStatus.SecretApplied, afterItem.lastResult?.status)
    }

    @Test
    fun karlStepTriggerStartsMandatorySkagerbergEncounter() {
        val session = secretSession(playerName = "Karl Skagerberg", timeLeft = 102, rngState = 1L)
        val moved = session.apply(WildernessSessionInput.Move(WildernessDirection.North))
        val battle = assertIs<WildernessSessionStep.Battle>(moved.step)

        assertEquals(WildernessSessionResultStatus.EncounterHandoff, moved.lastResult?.status)
        assertEquals(101, moved.party.resources.timeLeft)
        assertEquals(Monsters.skagerbergKiller, battle.session.encounter.monster.id)
        assertTrue(battle.session.encounter.mandatory)
    }

    private fun SecretPlayerModel.actionFor(token: String): SecretActionModel =
        assertNotNull(keyActions.firstOrNull { it.binding.matches(token) }?.action)

    private fun secretSession(
        playerName: String,
        timeLeft: Int,
        gold: Int = 0,
        rngState: Long? = null,
    ): WildernessSession {
        val content = RylweenCompiledGame.nextDsl.runtimeContent().copy(missions = emptyList())
        val baseState = RylweenCompiledGame.nextRylween.states.single { it.id == States.freshReplay }
        val sourceParty = baseState.parties.single()
        val party =
            sourceParty.copy(
                player = playerName,
                position = WorldPosition.Wilderness(xy(2, 2)),
                resources =
                    sourceParty.resources.copy(
                        gold = gold,
                        timeLeft = timeLeft,
                    ),
            )
        val runtime =
            RuntimeSessionState(
                realm =
                    baseState.realm.copy(
                        currentMode = RuntimeMode.Wilderness,
                        currentTown = null,
                        currentMap = Maps.wilderness,
                        rngState = rngState,
                        activeParty = party.id,
                    ),
                parties = listOf(party),
                npcs = baseState.npcs,
                locations = baseState.locations,
            )
        return WildernessSession(
            runtime = runtime,
            worldMap = WorldMapGrid.fromSourceRows(List(4) { "ssss" }),
            content = content,
            step = WildernessSessionStep.Explore,
        )
    }
}
