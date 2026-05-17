/**
 * Item catalog and realm-system authoring builders.
 *
 * These builders describe inventory rows, random item pools, party resource
 * capacities, wilderness upkeep, camp/rest behavior, and hunting rules. They
 * stay in the public DSL package, but live outside `Dsl.kt` because they are
 * content-system declarations rather than the generic script recording kernel.
 */
package rylween.dsl

import rylween.dsl.impl.CampRulesModel
import rylween.dsl.impl.HuntRulesModel
import rylween.dsl.impl.ItemKind
import rylween.dsl.impl.ItemRecordModel
import rylween.dsl.impl.ItemStorageKind
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.RealmContentModel
import rylween.dsl.impl.Resource
import rylween.dsl.impl.RewardBundleModel
import rylween.dsl.impl.ScriptRewardModel
import rylween.dsl.impl.SpeakSystemModel
import rylween.dsl.impl.TemperatureBand
import rylween.dsl.impl.TemperatureBandRange
import rylween.dsl.impl.TimeOfDayBandModel
import rylween.dsl.impl.WildernessFatigueMatch
import rylween.dsl.impl.WildernessFatigueRule
import rylween.dsl.impl.WildernessUpkeepRules
import rylween.dsl.impl.defaultTimeOfDayBands

/**
 * Internal result produced by [ItemCatalogBuilder].
 *
 * `GameBuilder.items` consumes both concrete item records and named item pools
 * so later scripts can refer to pools before the final compatibility handles
 * are normalized.
 */
data class ItemCatalogDeclaration(
    val records: List<ItemRecordModel>,
    val pools: List<ItemPoolId>,
)

/**
 * Receiver for declaring the members of one item pool.
 *
 * Designers use this when a random reward or offer should draw from a named
 * group of item ids instead of listing those ids at every use site.
 */
@RylNextDsl
class ItemPoolBuilder {
    private val items = mutableListOf<ItemId>()

    fun item(id: ItemId) {
        items += id
    }

    fun items(vararg ids: ItemId) {
        items += ids
    }

    internal fun build(): List<ItemId> = items.toList()
}

/**
 * Receiver for camp-gear protection metadata on an item record.
 *
 * Designers use `protects(...)` to declare which temperature bands a carried
 * tent, blanket, or similar item shields against during camp/rest resolution.
 */
@RylNextDsl
class CampProtectionBuilder {
    private val bands = mutableSetOf<TemperatureBand>()

    fun protects(vararg bands: TemperatureBand) {
        this.bands += bands
    }

    internal fun build(): Set<TemperatureBand> = bands.toSet()
}

/**
 * Optional customization receiver for a single item catalog row.
 *
 * Designers use it from weapon/armor/gear/treasure declarations to add class
 * restrictions, camp images, camp protection, or party-resource capacity
 * bonuses without changing the base item kind.
 */
@RylNextDsl
class ItemRecordBuilder {
    private var image: ImageId? = null
    private var classes: Set<PcClass> = emptySet()
    private var protection: Set<TemperatureBand> = emptySet()
    private val capacityBonuses = mutableMapOf<Resource, Int>()

    fun equippableBy(vararg classes: PcClass) {
        this.classes = classes.toSet()
    }

    fun campImage(image: ImageId) {
        this.image = image
    }

    fun campProtection(body: CampProtectionBuilder.() -> Unit) {
        protection = CampProtectionBuilder().apply(body).build()
    }

    fun resourceCapacityBonus(
        resource: Resource,
        amount: Int,
    ) {
        capacityBonuses[resource] = amount
    }

    internal fun equippableByValue(): Set<PcClass> = classes

    internal fun campImageValue(): ImageId? = image

    internal fun campProtectionValue(): Set<TemperatureBand> = protection

    internal fun resourceCapacityBonusValue(): Map<Resource, Int> = capacityBonuses.toMap()
}

/**
 * Receiver for realm-level runtime balance and prose.
 *
 * Designers use this from `realm { ... }` to declare shared party resources,
 * wilderness upkeep, camp/rest behavior, hunting rules, and status-line time
 * bands. Runtime sessions read the resulting data rather than hardcoding those
 * policies.
 */
@RylNextDsl
class RealmContentBuilder {
    private val baseCapacity = mutableMapOf<Resource, Int>()
    private var upkeep = WildernessUpkeepRules()
    private var camp = CampRulesModel()
    private var hunt: HuntRulesModel? = null
    private var timeOfDayBands = defaultTimeOfDayBands()

    fun baseResourceCapacity(
        resource: Resource,
        amount: Int,
    ) {
        baseCapacity[resource] = amount
    }

    fun upkeep(body: WildernessUpkeepRulesBuilder.() -> Unit) {
        upkeep = WildernessUpkeepRulesBuilder(upkeep).apply(body).build()
    }

    fun camp(body: CampRulesBuilder.() -> Unit) {
        camp = CampRulesBuilder().apply(body).build()
    }

    fun hunt(body: HuntRulesBuilder.() -> Unit) {
        hunt = HuntRulesBuilder().apply(body).build()
    }

    fun timeOfDay(body: TimeOfDayBandsBuilder.() -> Unit) {
        timeOfDayBands = TimeOfDayBandsBuilder().apply(body).build()
    }

    internal fun build(): RealmContentModel =
        RealmContentModel(
            baseResourceCapacity = baseCapacity.toMap(),
            upkeep = upkeep,
            camp = camp,
            hunt = hunt,
            timeOfDay = timeOfDayBands,
        )
}

/**
 * Receiver for status-line time-of-day labels.
 *
 * Each band maps an inclusive hour range to the label displayed by the runtime
 * status line, allowing the game source to own the vocabulary for time.
 */
@RylNextDsl
class TimeOfDayBandsBuilder {
    private val bands = mutableListOf<TimeOfDayBandModel>()

    fun band(
        hours: IntRange,
        label: String,
    ) {
        bands += TimeOfDayBandModel(hours, label)
    }

    internal fun build(): List<TimeOfDayBandModel> = bands.toList()
}

/**
 * Receiver for wilderness turn-upkeep rules.
 *
 * Designers use it to author food cadence, starvation thresholds/damage/death
 * text, mana recovery chance, rested-hour drain, and fatigue messages while
 * the runtime owns applying those rules on each wilderness turn.
 */
@RylNextDsl
class WildernessUpkeepRulesBuilder(
    initial: WildernessUpkeepRules = WildernessUpkeepRules(),
) {
    var foodCostLeaderDiv: Int = initial.foodCostLeaderDiv
    var foodTickEvery: Int = initial.foodTickEvery
    var starveFoodThreshold: Int = initial.starveFoodThreshold
    var starveDamage: Int = initial.starveDamage
    var starvationDeath: TextBlock = initial.starvationDeath
    var manaRegenChance: Int = initial.manaRegenChance
    var restedHoursCostPerTurn: Int = initial.restedHoursCostPerTurn
    private val fatigue = initial.fatigue.toMutableList()

    /** Defines authored prose for a member death caused by wilderness starvation upkeep. */
    fun starvationDeath(body: StructuredTextBlockBuilder.() -> Unit) {
        starvationDeath = structuredText(body)
    }

    fun fatigueAt(
        restedHours: Int,
        text: TextBlock,
        drain: Boolean = false,
        statusLabel: String? = null,
    ) {
        fatigue +=
            WildernessFatigueRule(
                threshold = restedHours,
                text = text,
                drain = drain,
                statusLabel = statusLabel,
            )
    }

    fun fatigueBelow(
        restedHours: Int,
        text: TextBlock,
        drain: Boolean = false,
        statusLabel: String? = null,
    ) {
        fatigue +=
            WildernessFatigueRule(
                threshold = restedHours,
                text = text,
                match = WildernessFatigueMatch.LessThan,
                drain = drain,
                statusLabel = statusLabel,
            )
    }

    internal fun build(): WildernessUpkeepRules =
        WildernessUpkeepRules(
            foodCostLeaderDiv = foodCostLeaderDiv,
            foodTickEvery = foodTickEvery,
            starveFoodThreshold = starveFoodThreshold,
            starveDamage = starveDamage,
            starvationDeath = starvationDeath,
            manaRegenChance = manaRegenChance,
            restedHoursCostPerTurn = restedHoursCostPerTurn,
            fatigue = fatigue.toList(),
        )
}

/**
 * Receiver for camp and rest balance.
 *
 * Designers author healing, food cost, time advancement, temperature bands,
 * shelter text, guard prompts, and ambush settings here; the camp runtime
 * applies the resulting data to live party state.
 */
@RylNextDsl
class CampRulesBuilder {
    var healthGain: Int = 0
    var foodCostPerMember: Int = 0
    var hoursAdvanced: Int = 0
    var hoursRestedReset: Int = 0
    var temperatureMax: Int = 0
    var ambushChancePercent: Int = 0
    var ambushTier: EncounterTier? = null
    var notTired: TextBlock = inlineText()
    var notEnoughFood: TextBlock = inlineText()
    var guardPrompt: TextBlock = inlineText()
    var guardMemberPrompt: TextBlock = inlineText()
    var guardMemberDead: TextBlock = inlineText()
    var standsGuard: TextBlock = inlineText()
    var sleeping: TextBlock = inlineText()
    var rested: TextBlock = inlineText()
    var ambush: TextBlock = inlineText()
    var surpriseKill: TextBlock = inlineText()
    private val bands = mutableListOf<TemperatureBandRange>()
    private val damage = mutableMapOf<TemperatureBand, Int>()
    private val temperatureMessage = mutableMapOf<TemperatureBand, TextBlock>()
    private val noShelterMessage = mutableMapOf<TemperatureBand, TextBlock>()

    fun temperatureBand(
        band: TemperatureBand,
        range: IntRange,
        damage: Int,
        message: TextBlock,
        noShelter: TextBlock,
    ) {
        bands += TemperatureBandRange(band, range)
        this.damage[band] = damage
        temperatureMessage[band] = message
        noShelterMessage[band] = noShelter
    }

    internal fun build(): CampRulesModel =
        CampRulesModel(
            healthGain = healthGain,
            foodCostPerMember = foodCostPerMember,
            hoursAdvanced = hoursAdvanced,
            hoursRestedReset = hoursRestedReset,
            temperatureMax = temperatureMax,
            temperatureBands = bands.toList(),
            temperatureDamage = damage.toMap(),
            ambushChancePercent = ambushChancePercent,
            ambushTier = ambushTier,
            notTired = notTired,
            notEnoughFood = notEnoughFood,
            guardPrompt = guardPrompt,
            guardMemberPrompt = guardMemberPrompt,
            guardMemberDead = guardMemberDead,
            standsGuard = standsGuard,
            sleeping = sleeping,
            rested = rested,
            ambush = ambush,
            surpriseKill = surpriseKill,
            temperatureMessage = temperatureMessage.toMap(),
            noShelterMessage = noShelterMessage.toMap(),
        )
}

/**
 * Receiver for wilderness hunting balance and prose.
 *
 * Designers use it to set ranger success chance, move cost, food gain range,
 * and the prompts/messages shown by the reusable hunt action.
 */
@RylNextDsl
class HuntRulesBuilder {
    var successChancePercent: Int = 0
    var timeCost: Int = 0
    var foodMin: Int = 0
    var foodMax: Int = 0
    var whoPrompt: TextBlock = inlineText()
    var dead: TextBlock = inlineText()
    var notRanger: TextBlock = inlineText()
    var tooFewMoves: TextBlock = inlineText()
    var tries: TextBlock = inlineText()
    var succeeds: TextBlock = inlineText()
    var fails: TextBlock = inlineText()

    internal fun build(): HuntRulesModel =
        HuntRulesModel(
            successChancePercent = successChancePercent,
            timeCost = timeCost,
            foodMin = foodMin,
            foodMax = foodMax,
            whoPrompt = whoPrompt,
            dead = dead,
            notRanger = notRanger,
            tooFewMoves = tooFewMoves,
            tries = tries,
            succeeds = succeeds,
            fails = fails,
        )
}

/**
 * Receiver for the global item catalog.
 *
 * Designers use this from `items { ... }` to declare every item row and item
 * pool used by inventory, shops, rewards, saves, equipment, and scripted random
 * offers.
 */
@RylNextDsl
class ItemCatalogBuilder {
    private val records = mutableListOf<ItemRecordModel>()
    private val pools = mutableListOf<ItemPoolId>()

    fun empty(
        id: ItemId,
        name: String = "-",
        weaponDamage: Int = 1,
    ) {
        record(
            id = id,
            kind = ItemKind.Empty,
            name = name,
            cost = 0,
            storage = ItemStorageKind.EmptySentinel,
            weaponDamage = weaponDamage,
        )
    }

    fun weapon(
        id: ItemId,
        name: String,
        cost: Int,
        damage: Int,
    ) {
        record(id, ItemKind.Weapon, name, cost, weaponDamage = damage)
    }

    fun weapon(
        id: ItemId,
        name: String,
        cost: Int,
        damage: Int,
        body: ItemRecordBuilder.() -> Unit,
    ) {
        customizable(id, ItemKind.Weapon, name, cost, body, weaponDamage = damage)
    }

    fun armor(
        id: ItemId,
        name: String,
        cost: Int,
        armorClass: Int,
    ) {
        record(id, ItemKind.Armor, name, cost, armorClass = armorClass)
    }

    fun armor(
        id: ItemId,
        name: String,
        cost: Int,
        armorClass: Int,
        body: ItemRecordBuilder.() -> Unit,
    ) {
        customizable(id, ItemKind.Armor, name, cost, body, armorClass = armorClass)
    }

    fun gear(
        id: ItemId,
        name: String,
        cost: Int,
    ) {
        record(id, ItemKind.Gear, name, cost)
    }

    fun gear(
        id: ItemId,
        name: String,
        cost: Int,
        body: ItemRecordBuilder.() -> Unit,
    ) {
        customizable(id, ItemKind.Gear, name, cost, body)
    }

    fun potion(
        id: ItemId,
        name: String,
        cost: Int,
    ) {
        record(
            id = id,
            kind = ItemKind.Potion,
            name = name,
            cost = cost,
        )
    }

    fun treasure(
        id: ItemId,
        name: String,
        cost: Int,
    ) {
        record(id, ItemKind.Treasure, name, cost)
    }

    fun treasure(
        id: ItemId,
        name: String,
        cost: Int,
        body: ItemRecordBuilder.() -> Unit,
    ) {
        customizable(id, ItemKind.Treasure, name, cost, body)
    }

    private fun customizable(
        id: ItemId,
        kind: ItemKind,
        name: String,
        cost: Int,
        body: ItemRecordBuilder.() -> Unit,
        weaponDamage: Int? = null,
        armorClass: Int? = null,
    ) {
        val builder = ItemRecordBuilder().apply(body)
        record(
            id = id,
            kind = kind,
            name = name,
            cost = cost,
            weaponDamage = weaponDamage,
            armorClass = armorClass,
            equippableBy = builder.equippableByValue(),
            campImage = builder.campImageValue(),
            campProtection = builder.campProtectionValue(),
            resourceCapacityBonus = builder.resourceCapacityBonusValue(),
        )
    }

    fun special(
        id: ItemId,
        name: String,
    ) {
        record(id, ItemKind.Special, name, cost = 0, storage = ItemStorageKind.SpecialMilestone)
    }

    fun magicItem(
        id: ItemId,
        name: String,
        cost: Int,
    ) {
        record(id, ItemKind.MagicItem, name, cost)
    }

    fun pool(
        id: ItemPoolId,
        vararg items: ItemId,
    ) {
        pool(id, items.toList())
    }

    fun pool(
        id: ItemPoolId,
        items: List<ItemId>,
    ) {
        pools += itemPool(id.slug, items)
    }

    fun pool(
        id: ItemPoolId,
        body: ItemPoolBuilder.() -> Unit,
    ) {
        pool(id, ItemPoolBuilder().apply(body).build())
    }

    private fun record(
        id: ItemId,
        kind: ItemKind,
        name: String,
        cost: Int,
        storage: ItemStorageKind = ItemStorageKind.SlottedInventory,
        weaponDamage: Int? = null,
        armorClass: Int? = null,
        equippableBy: Set<PcClass> = emptySet(),
        campImage: ImageId? = null,
        campProtection: Set<TemperatureBand> = emptySet(),
        resourceCapacityBonus: Map<Resource, Int> = emptyMap(),
    ) {
        records +=
            ItemRecordModel(
                id = id,
                kind = kind,
                displayName = name,
                cost = cost,
                storage = storage,
                weaponDamage = weaponDamage,
                armorClass = armorClass,
                equippableBy = equippableBy,
                campImage = campImage,
                campProtection = campProtection,
                resourceCapacityBonus = resourceCapacityBonus,
            )
    }

    internal fun build(): ItemCatalogDeclaration = ItemCatalogDeclaration(records.toList(), pools.toList())
}

internal fun RewardBundleModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): RewardBundleModel =
    copy(loot = loot.copy(treasure = resolve(loot.treasure), magic = resolve(loot.magic)))

internal fun SceneModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): SceneModel = copy(script = script.resolveItemPools(resolve))

internal fun DialogueModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): DialogueModel =
    copy(branches = branches.map { it.copy(script = it.script.resolveItemPools(resolve)) })

internal fun SpeakSystemModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): SpeakSystemModel =
    copy(questionActions = questionActions.map { it.copy(script = it.script.resolveItemPools(resolve)) })

internal fun SpellModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): SpellModel = copy(script = script.resolveItemPools(resolve))

internal fun ItemUseModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): ItemUseModel =
    copy(script = script.resolveItemPools(resolve))

internal fun DungeonSystemModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): DungeonSystemModel =
    copy(events = events.map { it.copy(script = it.script.resolveItemPools(resolve)) })

private fun AuthoredScript.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): AuthoredScript =
    AuthoredScript(statementNodes.resolveItemPools(resolve))

private fun List<ScriptStatementNode>.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): List<ScriptStatementNode> =
    map {
        it.resolveItemPools(resolve)
    }

private fun List<SwitchCase>.resolveSwitchItemPools(resolve: (ItemPoolId) -> ItemPoolId): List<SwitchCase> =
    map { it.copy(body = it.body.resolveItemPools(resolve)) }

private fun List<MenuOption>.resolveMenuItemPools(resolve: (ItemPoolId) -> ItemPoolId): List<MenuOption> =
    map { it.copy(body = it.body.resolveItemPools(resolve)) }

private fun ScriptStatementNode.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): ScriptStatementNode =
    when (this) {
        is ScriptStatementNode.LetInt -> copy(value = value.resolveItemPools(resolve))
        is ScriptStatementNode.Set -> copy(target = target.resolveItemPools(resolve), value = value.resolveItemPools(resolve))
        is ScriptStatementNode.Add -> copy(target = target.resolveItemPools(resolve), value = value.resolveItemPools(resolve))
        is ScriptStatementNode.Sub -> copy(target = target.resolveItemPools(resolve), value = value.resolveItemPools(resolve))
        is ScriptStatementNode.AddClamped ->
            copy(
                target = target.resolveItemPools(resolve),
                value = value.resolveItemPools(resolve),
                max = max.resolveItemPools(resolve),
            )
        is ScriptStatementNode.If ->
            copy(
                cond = cond.resolveItemPools(resolve),
                thenStatements = thenStatements.resolveItemPools(resolve),
                elseStatements = elseStatements.resolveItemPools(resolve),
            )
        is ScriptStatementNode.Loop -> copy(body = body.resolveItemPools(resolve))
        is ScriptStatementNode.Switch -> copy(expr = expr.resolveItemPools(resolve), cases = cases.resolveSwitchItemPools(resolve))
        is ScriptStatementNode.Menu ->
            copy(
                options = options.resolveMenuItemPools(resolve),
                defaultStatements = defaultStatements.resolveItemPools(resolve),
            )
        is ScriptStatementNode.AskKey -> copy()
        is ScriptStatementNode.AskMember -> copy()
        is ScriptStatementNode.AskInventorySlot -> copy()
        is ScriptStatementNode.AskText ->
            copy(
                matchStatements = matchStatements.resolveItemPools(resolve),
                missStatements = missStatements.resolveItemPools(resolve),
            )
        is ScriptStatementNode.ForEachMember -> copy(body = body.resolveItemPools(resolve))
        is ScriptStatementNode.ForEachMonster -> copy(body = body.resolveItemPools(resolve))
        is ScriptStatementNode.ConsumeInventoryPool -> copy(pool = resolve(pool), quantity = quantity.resolveItemPools(resolve))
        is ScriptStatementNode.TeleportParty ->
            copy(
                directionKey = directionKey.resolveItemPools(resolve),
                distance = distance.resolveItemPools(resolve),
            )
        is ScriptStatementNode.DiscoverCave ->
            copy(
                levels = levels.resolveItemPools(resolve),
                moveCost = moveCost.resolveItemPools(resolve),
            )
        is ScriptStatementNode.GrantReward -> copy(reward = reward.resolveItemPools(resolve))
        is ScriptStatementNode.RandomItemOffer -> copy(offer = offer.copy(pool = resolve(offer.pool)))
        else -> this
    }

private fun ScriptRewardModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): ScriptRewardModel =
    copy(gold = gold?.resolveItemPools(resolve))

private fun LValueAst.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): LValueAst =
    when (this) {
        is LValueAst.MemberField -> copy(member = member.resolveItemPools(resolve))
        is LValueAst.MonsterField -> copy(monster = monster.resolveItemPools(resolve))
        else -> this
    }

private fun IntAst.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): IntAst =
    when (this) {
        is IntAst.Binary -> copy(left = left.resolveItemPools(resolve), right = right.resolveItemPools(resolve))
        is IntAst.If ->
            copy(
                cond = cond.resolveItemPools(resolve),
                thenValue = thenValue.resolveItemPools(resolve),
                elseValue = elseValue.resolveItemPools(resolve),
            )
        is IntAst.Random -> copy(lo = lo.resolveItemPools(resolve), hi = hi.resolveItemPools(resolve))
        is IntAst.MemberField -> copy(member = member.resolveItemPools(resolve))
        is IntAst.MonsterField -> copy(monster = monster.resolveItemPools(resolve))
        is IntAst.InventoryPoolCount -> copy(pool = resolve(pool))
        is IntAst.InventorySlotCost -> copy(slot = slot.resolveItemPools(resolve))
        else -> this
    }

private fun BoolAst.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): BoolAst =
    when (this) {
        is BoolAst.Cmp -> copy(left = left.resolveItemPools(resolve), right = right.resolveItemPools(resolve))
        is BoolAst.Not -> copy(expr = expr.resolveItemPools(resolve))
        is BoolAst.And -> copy(left = left.resolveItemPools(resolve), right = right.resolveItemPools(resolve))
        is BoolAst.Or -> copy(left = left.resolveItemPools(resolve), right = right.resolveItemPools(resolve))
        is BoolAst.MemberFlag -> copy(member = member.resolveItemPools(resolve))
        is BoolAst.MonsterFlag -> copy(monster = monster.resolveItemPools(resolve))
        is BoolAst.RuntimeFlag -> this
        else -> this
    }
