/**
 * Runtime snapshot loading, parsing diagnostics, and compatibility checks.
 *
 * This is the read-side counterpart to `RuntimeSave.kt`; it uses
 * `StateSnapshot.kt` to parse saves, [validateRuntimeStateSnapshotForLoad] to
 * check authored-content references, and `SaveCompatibility.kt` metadata to
 * reject stale snapshots. Some snapshot records exist for engine
 * execution-boundary round trips and tests; the frontend/session layer still
 * decides which runtime modes expose player-visible saves.
 */
package rylween.save

import rylween.dsl.GameModel
import rylween.dsl.ItemId
import rylween.dsl.ItemUseContext
import rylween.dsl.NpcId
import rylween.dsl.RuntimeMode
import rylween.dsl.RylweenValidationException
import rylween.dsl.StateModel
import rylween.dsl.ValidationDiagnostic
import rylween.dsl.validateStates
import rylween.dsl.withDiagnosticPathPrefix
import rylween.runtime.CastleSavedStep
import rylween.runtime.CastleSessionSnapshot
import rylween.runtime.RuntimeModeSnapshot
import rylween.runtime.RuntimeSnapshotModel
import rylween.runtime.ScriptContinuationSnapshot
import rylween.runtime.ScriptRootKind
import rylween.runtime.ScriptRootRef
import rylween.runtime.battle.BattleSavedStep
import rylween.runtime.battle.BattleSessionSnapshot
import rylween.runtime.battle.CombatScriptSnapshot
import rylween.runtime.battle.CombatScriptSourceKind
import rylween.runtime.reward.RandomItemOfferSavedStep
import rylween.runtime.reward.RandomItemOfferSessionSnapshot
import rylween.runtime.reward.RewardGrantSavedStep
import rylween.runtime.reward.RewardGrantSessionSnapshot
import rylween.runtime.town.ShopServiceSavedStep
import rylween.runtime.town.ShopServiceSessionSnapshot
import rylween.runtime.town.SpeakSavedStep
import rylween.runtime.town.SpeakSessionSnapshot
import rylween.runtime.town.shopServicesByBuilding
import java.io.File

/** Loads a save file and returns its validated runtime state. */
fun loadRuntimeStateSnapshot(
    file: File,
    content: GameModel,
    sourceName: String = "save file ${file.path}",
    compiledBundleHash: String? = null,
): StateModel = parseRuntimeStateSnapshotForLoad(file.readText(), content, sourceName, compiledBundleHash)

/** Loads a save file and returns its validated full runtime snapshot. */
fun loadRuntimeSnapshot(
    file: File,
    content: GameModel,
    sourceName: String = "save file ${file.path}",
    compiledBundleHash: String? = null,
): RuntimeSnapshotModel = parseRuntimeSnapshotForLoad(file.readText(), content, sourceName, compiledBundleHash)

/** Parses snapshot text for loading and returns only the validated state. */
fun parseRuntimeStateSnapshotForLoad(
    source: String,
    content: GameModel,
    sourceName: String = "runtime snapshot",
    compiledBundleHash: String? = null,
): StateModel = parseRuntimeSnapshotForLoad(source, content, sourceName, compiledBundleHash).state

/** Parses snapshot text for loading and returns the validated full snapshot. */
fun parseRuntimeSnapshotForLoad(
    source: String,
    content: GameModel,
    sourceName: String = "runtime snapshot",
    compiledBundleHash: String? = null,
): RuntimeSnapshotModel =
    validateRuntimeStateSnapshotForLoad(
        snapshot = parseRuntimeSnapshotWithDiagnostics(source, sourceName),
        content = content,
        sourceName = sourceName,
        compiledBundleHash = compiledBundleHash,
    )

/** Validates a parsed runtime state against current authored [content]. */
fun validateRuntimeStateSnapshotForLoad(
    state: StateModel,
    content: GameModel,
    sourceName: String = "runtime snapshot",
): StateModel = validateRuntimeStateSnapshotForLoad(RuntimeSnapshotModel(state), content, sourceName).state

/** Validates a full runtime snapshot, including metadata and nested mode state. */
fun validateRuntimeStateSnapshotForLoad(
    snapshot: RuntimeSnapshotModel,
    content: GameModel,
    sourceName: String = "runtime snapshot",
    compiledBundleHash: String? = null,
): RuntimeSnapshotModel {
    try {
        validateStates(listOf(snapshot.state), content)
    } catch (error: RylweenValidationException) {
        throw error.withDiagnosticPathPrefix(sourceName)
    } catch (error: IllegalStateException) {
        throw runtimeLoadFailure(sourceName, error.message ?: "runtime snapshot is not valid")
    }
    validateSnapshotMetadata(snapshot, content, sourceName, compiledBundleHash)
    validateCompiledContinuationBundle(snapshot, sourceName, compiledBundleHash)
    validateModeSnapshot(snapshot, content, sourceName)
    return snapshot
}

private fun parseRuntimeSnapshotWithDiagnostics(
    source: String,
    sourceName: String,
): RuntimeSnapshotModel =
    try {
        parseRuntimeSnapshot(source)
    } catch (error: RuntimeException) {
        throw runtimeLoadFailure(sourceName, "could not parse runtime snapshot: ${error.message ?: error::class.simpleName}")
    }

private fun validateSnapshotMetadata(
    snapshot: RuntimeSnapshotModel,
    content: GameModel,
    sourceName: String,
    compiledBundleHash: String?,
) {
    val metadata = snapshot.metadata ?: return
    if (metadata.formatVersion != RUNTIME_SNAPSHOT_FORMAT_VERSION) {
        throw runtimeLoadFailure(
            sourceName,
            "runtime snapshot format version ${metadata.formatVersion} is not supported; " +
                "expected $RUNTIME_SNAPSHOT_FORMAT_VERSION",
        )
    }
    val universe = content.metadata.universe
    if (metadata.universeId != null && metadata.universeId != universe.compatibilityId) {
        throw runtimeLoadFailure(
            sourceName,
            "runtime snapshot universe ${metadata.universeId} does not match this game universe " +
                universe.compatibilityId,
        )
    }
    if (metadata.universeVersion != null && metadata.universeVersion != universe.version) {
        throw runtimeLoadFailure(
            sourceName,
            "runtime snapshot universe version ${metadata.universeVersion} does not match this game version " +
                universe.version,
        )
    }
    val expectedHash = content.runtimeContentHash()
    if (metadata.contentHash != null && metadata.contentHash != expectedHash) {
        throw runtimeLoadFailure(sourceName, "runtime snapshot content hash does not match this game build")
    }
}

private fun validateCompiledContinuationBundle(
    snapshot: RuntimeSnapshotModel,
    sourceName: String,
    compiledBundleHash: String?,
) {
    if (!snapshot.hasCompiledVmContinuation()) {
        return
    }
    val savedHash =
        snapshot.metadata?.compiledBundleHash
            ?: throw runtimeLoadFailure(sourceName, "compiled VM continuation requires compiled bundle hash metadata")
    if (compiledBundleHash == null) {
        throw runtimeLoadFailure(sourceName, "compiled VM continuation requires a verified compiled bundle hash")
    }
    if (savedHash != compiledBundleHash) {
        throw runtimeLoadFailure(sourceName, "runtime snapshot compiled bundle hash does not match this game bundle")
    }
}

private fun RuntimeSnapshotModel.hasCompiledVmContinuation(): Boolean =
    when (val activeMode = mode) {
        is RuntimeModeSnapshot.Dungeon -> activeMode.postDungeon?.continuation?.compiledVmContinuation != null
        is RuntimeModeSnapshot.Castle -> activeMode.session.scriptContinuation?.compiledVmContinuation != null
        null -> false
    }

private fun validateModeSnapshot(
    snapshot: RuntimeSnapshotModel,
    content: GameModel,
    sourceName: String,
) {
    when (snapshot.state.realm.currentMode) {
        RuntimeMode.Castle -> {
            val castle =
                snapshot.mode as? RuntimeModeSnapshot.Castle
                    ?: throw runtimeLoadFailure(sourceName, "Castle runtime snapshot requires sessions { castle { ... } }")
            validateCastleSnapshot(castle, content, sourceName)
        }
        RuntimeMode.Dungeon -> {
            val dungeon =
                snapshot.mode as? RuntimeModeSnapshot.Dungeon
                    ?: throw runtimeLoadFailure(sourceName, "Dungeon runtime snapshot requires sessions { dungeon { ... } }")
            validateDungeonSnapshot(dungeon, content, sourceName)
        }
        RuntimeMode.Battle -> validateBattleModeSnapshot(snapshot.mode, content, sourceName)
        else -> {
            if (snapshot.mode != null) {
                throw runtimeLoadFailure(
                    sourceName,
                    "session snapshot ${snapshot.mode.javaClass.simpleName} does not match " +
                        "runtime mode ${snapshot.state.realm.currentMode.name}",
                )
            }
        }
    }
}

private fun validateBattleModeSnapshot(
    mode: RuntimeModeSnapshot?,
    content: GameModel,
    sourceName: String,
) {
    when (mode) {
        is RuntimeModeSnapshot.Castle -> {
            if (mode.session.scriptContinuation?.battleSession == null) {
                throw runtimeLoadFailure(sourceName, "Battle runtime snapshot requires an active castle battle continuation")
            }
            validateCastleSnapshot(mode, content, sourceName)
        }
        is RuntimeModeSnapshot.Dungeon -> {
            if (mode.postDungeon?.continuation?.battleSession == null) {
                throw runtimeLoadFailure(sourceName, "Battle runtime snapshot requires an active dungeon battle continuation")
            }
            validateDungeonSnapshot(mode, content, sourceName)
        }
        null -> throw runtimeLoadFailure(sourceName, "Battle runtime snapshot requires a parent session snapshot")
    }
}

private fun validateCastleSnapshot(
    castle: RuntimeModeSnapshot.Castle,
    content: GameModel,
    sourceName: String,
) {
    val session = castle.session
    val castleContent =
        content.castles.singleOrNull { candidate -> candidate.id == session.castle }
            ?: throw runtimeLoadFailure(sourceName, "Castle session references unknown castle ${session.castle.slug}")
    if (session.floor !in 1..castleContent.stairs.floorCount) {
        throw runtimeLoadFailure(sourceName, "Castle session floor must be between 1 and ${castleContent.stairs.floorCount}")
    }
    if (session.point.x !in 1..castleContent.mapSize || session.point.y !in 1..castleContent.mapSize) {
        throw runtimeLoadFailure(sourceName, "Castle session point is outside the ${castleContent.mapSize}x${castleContent.mapSize} map")
    }
    session.rooms.keys.forEach { point ->
        if (point.x !in 1..castleContent.mapSize || point.y !in 1..castleContent.mapSize) {
            throw runtimeLoadFailure(sourceName, "Castle session room ${point.x}x${point.y} is outside the castle map")
        }
    }
    if (session.step == CastleSavedStep.TreasureOffer && session.treasureItem == null) {
        throw runtimeLoadFailure(sourceName, "Castle treasure-offer session requires treasureItem")
    }
    if (session.step == CastleSavedStep.Script) {
        validateCastleScriptSnapshot(session, content, sourceName)
    }
}

private fun validateCastleScriptSnapshot(
    session: CastleSessionSnapshot,
    content: GameModel,
    sourceName: String,
) {
    val scene = session.scriptScene ?: throw runtimeLoadFailure(sourceName, "Castle script session requires scriptScene")
    if (content.scenes.none { candidate -> candidate.id == scene }) {
        throw runtimeLoadFailure(sourceName, "Castle script session references unknown scene ${scene.slug}")
    }
    val continuation =
        session.scriptContinuation
            ?: throw runtimeLoadFailure(sourceName, "Castle script session requires scriptContinuation")
    validateScriptContinuationSnapshot(continuation, content, sourceName, "Castle script continuation")
    if (session.scriptNext == null) {
        throw runtimeLoadFailure(sourceName, "Castle script session requires scriptNext")
    }
}

private fun validateDungeonSnapshot(
    dungeon: RuntimeModeSnapshot.Dungeon,
    content: GameModel,
    sourceName: String,
) {
    val session = dungeon.session
    if (session.maxDepth < 1) {
        throw runtimeLoadFailure(sourceName, "Dungeon session maxDepth must be at least 1")
    }
    if (session.depth !in 1..session.maxDepth) {
        throw runtimeLoadFailure(sourceName, "Dungeon session depth must be between 1 and maxDepth")
    }
    if (content.dungeons.scenarios.none { it.dungeon == session.dungeon }) {
        throw runtimeLoadFailure(sourceName, "Dungeon session references unknown dungeon ${session.dungeon.slug}")
    }
    val eventIds =
        content.dungeons.events
            .map { it.id }
            .toSet()
    session.levelEvents.forEach { (level, event) ->
        if (level !in 1..session.maxDepth) {
            throw runtimeLoadFailure(sourceName, "Dungeon level event $level is outside maxDepth ${session.maxDepth}")
        }
        if (event !in eventIds) {
            throw runtimeLoadFailure(sourceName, "Dungeon level event references unknown event ${event.slug}")
        }
    }
    dungeon.postDungeon?.let { postDungeon ->
        if (content.missions.none { it.id == postDungeon.mission }) {
            throw runtimeLoadFailure(sourceName, "post-dungeon continuation references unknown mission ${postDungeon.mission.slug}")
        }
        if (content.scenes.none { it.id == postDungeon.scene }) {
            throw runtimeLoadFailure(sourceName, "post-dungeon continuation references unknown scene ${postDungeon.scene.slug}")
        }
        validateScriptContinuationSnapshot(postDungeon.continuation, content, sourceName, "post-dungeon continuation")
    }
}

private fun validateScriptContinuationSnapshot(
    continuation: ScriptContinuationSnapshot,
    content: GameModel,
    sourceName: String,
    label: String,
) {
    if (continuation.stack.isEmpty()) {
        throw runtimeLoadFailure(sourceName, "$label stack must not be empty")
    }
    continuation.speakSession?.let { speak ->
        validateSpeakSessionSnapshot(speak, content, sourceName)
    }
    continuation.battleSession?.let { battle ->
        validateBattleSessionSnapshot(battle, content, sourceName)
    }
    continuation.randomItemOfferSession?.let { offer ->
        validateRandomItemOfferSessionSnapshot(offer, content, sourceName)
    }
    continuation.rewardGrantSession?.let { reward ->
        validateRewardGrantSessionSnapshot(reward, content, sourceName)
    }
    continuation.shopServiceSession?.let { shop ->
        validateShopServiceSessionSnapshot(shop, content, sourceName)
    }
}

private fun validateSpeakSessionSnapshot(
    speak: SpeakSessionSnapshot,
    content: GameModel,
    sourceName: String,
) {
    val npcIds = content.characters.map { character -> character.id }.toSet()
    speak.selectedNpc?.let { npc ->
        if (npc !in npcIds) {
            throw runtimeLoadFailure(sourceName, "speak session references unknown selected NPC ${npc.slug}")
        }
    }
    validateSpeakStepSnapshot(speak.step, npcIds, content, sourceName)
}

private fun validateSpeakStepSnapshot(
    step: SpeakSavedStep,
    npcIds: Set<NpcId>,
    content: GameModel,
    sourceName: String,
) {
    when (step) {
        SpeakSavedStep.ChooseNpc,
        SpeakSavedStep.Finished,
        -> Unit
        is SpeakSavedStep.ScriptBranch -> {
            validateKnownNpc(step.npc, npcIds, "speak script branch", sourceName)
            val rootExists =
                when (step.root.kind) {
                    ScriptRootKind.DialogueBranch ->
                        content.dialogue.any { dialogue ->
                            dialogue.npc == step.npc &&
                                dialogue.branches.any { branch ->
                                    ScriptRootRef.dialogueBranch(step.npc, branch).sameRoot(step.root)
                                }
                        }
                    ScriptRootKind.QuestionAction ->
                        content.speak.questionActions.any { action ->
                            ScriptRootRef.questionAction(action).sameRoot(step.root)
                        }
                    else -> false
                }
            if (!rootExists) {
                throw runtimeLoadFailure(sourceName, "speak session references unknown script root ${step.root.kind}.${step.root.slug}")
            }
            step.continuation?.let { continuation ->
                validateScriptContinuationSnapshot(continuation, content, sourceName, "nested speak script continuation")
            }
            validateSpeakStepSnapshot(step.next, npcIds, content, sourceName)
        }
        is SpeakSavedStep.MainMenu -> validateKnownNpc(step.npc, npcIds, "speak main menu", sourceName)
        is SpeakSavedStep.QuestionMenu -> validateKnownNpc(step.npc, npcIds, "speak question menu", sourceName)
        is SpeakSavedStep.Message -> validateSpeakStepSnapshot(step.next, npcIds, content, sourceName)
    }
}

private fun validateKnownNpc(
    npc: NpcId,
    npcIds: Set<NpcId>,
    label: String,
    sourceName: String,
) {
    if (npc !in npcIds) {
        throw runtimeLoadFailure(sourceName, "$label references unknown NPC ${npc.slug}")
    }
}

private fun validateRandomItemOfferSessionSnapshot(
    offer: RandomItemOfferSessionSnapshot,
    content: GameModel,
    sourceName: String,
) {
    val itemIds = content.itemCatalog.map { item -> item.id }.toSet()
    val referencedItems =
        buildList {
            add(offer.item)
            addAll(offer.offer.pool.items)
            offer.lastResult?.item?.let(::add)
            addAll(randomItemOfferStepItems(offer.step))
        }
    referencedItems.forEach { item ->
        if (item !in itemIds) {
            throw runtimeLoadFailure(sourceName, "random item offer session references unknown item ${item.slug}")
        }
    }
}

private fun randomItemOfferStepItems(step: RandomItemOfferSavedStep): List<ItemId> =
    when (step) {
        is RandomItemOfferSavedStep.Choice -> listOf(step.item)
        is RandomItemOfferSavedStep.DropSlot -> listOf(step.item, step.result.item)
        is RandomItemOfferSavedStep.Message -> listOf(step.result.item) + randomItemOfferStepItems(step.next)
        is RandomItemOfferSavedStep.Finished -> listOf(step.result.item)
    }

private fun validateShopServiceSessionSnapshot(
    shop: ShopServiceSessionSnapshot,
    content: GameModel,
    sourceName: String,
) {
    val model =
        content.scenes.shopServicesByBuilding()[shop.building]
            ?: throw runtimeLoadFailure(sourceName, "shop service session references unknown shop ${shop.building.slug}")
    val itemIds = content.itemCatalog.map { item -> item.id }.toSet()
    shopStepItems(shop.step).forEach { item ->
        if (item !in itemIds) {
            throw runtimeLoadFailure(sourceName, "shop service step references unknown item ${item.slug}")
        }
    }
    val resultItems =
        buildList {
            shop.lastTransaction?.item?.let(::add)
            shop.lastSellQuote?.item?.let(::add)
        }
    resultItems.forEach { item ->
        if (item !in itemIds) {
            throw runtimeLoadFailure(sourceName, "shop service result references unknown item ${item.slug}")
        }
    }
    if (model.building != shop.building) {
        throw runtimeLoadFailure(sourceName, "shop service session building mismatch for ${shop.building.slug}")
    }
}

private fun shopStepItems(step: ShopServiceSavedStep): List<ItemId> =
    when (step) {
        ShopServiceSavedStep.Intro,
        ShopServiceSavedStep.MainMenu,
        ShopServiceSavedStep.SelectCatalog,
        ShopServiceSavedStep.SellSlot,
        ShopServiceSavedStep.Finished,
        -> emptyList()
        is ShopServiceSavedStep.BuyItem -> emptyList()
        is ShopServiceSavedStep.Quantity -> listOf(step.item)
        is ShopServiceSavedStep.ConfirmPurchase -> listOf(step.item)
        is ShopServiceSavedStep.DropSlot -> listOfNotNull(step.item, step.result.item)
        is ShopServiceSavedStep.ConfirmSale -> listOfNotNull(step.quote.item)
    }

private fun validateRewardGrantSessionSnapshot(
    reward: RewardGrantSessionSnapshot,
    content: GameModel,
    sourceName: String,
) {
    val itemIds = content.itemCatalog.map { item -> item.id }.toSet()
    (reward.reward.items + reward.pendingItems).forEach { item ->
        if (item !in itemIds) {
            throw runtimeLoadFailure(sourceName, "reward grant session references unknown item ${item.slug}")
        }
    }
    validateRewardGrantStepSnapshot(reward.step, itemIds, sourceName)
}

private fun validateRewardGrantStepSnapshot(
    step: RewardGrantSavedStep,
    itemIds: Set<ItemId>,
    sourceName: String,
) {
    val item =
        when (step) {
            is RewardGrantSavedStep.DropSlot -> step.item
            is RewardGrantSavedStep.Message -> step.result.item
            is RewardGrantSavedStep.Finished -> step.result.item
        }
    if (item != null && item !in itemIds) {
        throw runtimeLoadFailure(sourceName, "reward grant step references unknown item ${item.slug}")
    }
}

private fun validateBattleSessionSnapshot(
    battle: BattleSessionSnapshot,
    content: GameModel,
    sourceName: String,
) {
    if (content.combat.standardBattle == null) {
        throw runtimeLoadFailure(sourceName, "battle session requires configured standard battle rules")
    }
    if (content.monsters.none { monster -> monster.id == battle.monster }) {
        throw runtimeLoadFailure(sourceName, "battle session references unknown monster ${battle.monster.slug}")
    }
    if (battle.hitPoints.any { hitPoints -> hitPoints !in 0..battle.hitPointMax }) {
        throw runtimeLoadFailure(sourceName, "battle session hit points must be between zero and hitPointMax")
    }
    battle.lastResult.monster?.let { monster ->
        if (content.monsters.none { candidate -> candidate.id == monster }) {
            throw runtimeLoadFailure(sourceName, "battle result references unknown monster ${monster.slug}")
        }
    }
    validateBattleStepSnapshot(battle.step, content, sourceName)
}

private fun validateBattleStepSnapshot(
    step: BattleSavedStep,
    content: GameModel,
    sourceName: String,
) {
    when (step) {
        is BattleSavedStep.Message -> validateBattleStepSnapshot(step.next, content, sourceName)
        is BattleSavedStep.ScriptMonsterTarget -> validateCombatScriptSnapshot(step.script, content, sourceName)
        is BattleSavedStep.MemberTarget -> validateCombatScriptSnapshot(step.script, content, sourceName)
        is BattleSavedStep.PlayerAction -> validateNonNegative(step.memberIndex, "battle player member index", sourceName)
        is BattleSavedStep.MonsterTarget -> validateNonNegative(step.memberIndex, "battle target member index", sourceName)
        is BattleSavedStep.MagicItemSlot -> validateNonNegative(step.memberIndex, "battle magic item member index", sourceName)
        is BattleSavedStep.SpellChoice -> validateNonNegative(step.memberIndex, "battle spell member index", sourceName)
        is BattleSavedStep.Opening,
        is BattleSavedStep.RoundOpening,
        BattleSavedStep.MonsterRound,
        BattleSavedStep.Finished,
        -> Unit
    }
}

private fun validateCombatScriptSnapshot(
    script: CombatScriptSnapshot,
    content: GameModel,
    sourceName: String,
) {
    val found =
        when (script.sourceKind) {
            CombatScriptSourceKind.Spell,
            CombatScriptSourceKind.MonsterSpell,
            -> content.spells.any { spell -> spell.id.slug == script.sourceSlug }
            CombatScriptSourceKind.MagicItem ->
                content.itemUses.any { use ->
                    use.id.slug == script.sourceSlug && ItemUseContext.Combat in use.contexts
                }
        }
    if (!found) {
        throw runtimeLoadFailure(
            sourceName,
            "battle script references unknown ${script.sourceKind.name} script ${script.sourceSlug}",
        )
    }
    validateNonNegative(script.nextIndex, "battle script nextIndex", sourceName)
}

private fun validateNonNegative(
    value: Int,
    label: String,
    sourceName: String,
) {
    if (value < 0) {
        throw runtimeLoadFailure(sourceName, "$label must be non-negative")
    }
}

private fun runtimeLoadFailure(
    sourceName: String,
    message: String,
): RylweenValidationException = RylweenValidationException(listOf(ValidationDiagnostic(sourceName, message)))
