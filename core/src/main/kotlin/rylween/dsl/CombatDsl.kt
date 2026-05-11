/**
 * Combat authoring model and builders.
 *
 * This file defines the reusable standard-battle DSL: action declarations,
 * combat text slots, run/fight keys, formulas, loot/reward hooks, and builders
 * for mage/monster/priest combat script bodies. The concrete battle menu text,
 * spell names, cries, and Rylween-specific balance values are authored in the
 * game DSL and compiled through these generic records.
 */
package rylween.dsl

enum class MandatoryRunRule { NoEscape }

enum class CombatActionKind { Attack, Defend, UseMagicItem, BattleCry, CastMageSpell }

enum class CombatActionRequirement { Always, Mage }

data class EncounterOpeningModel(
    val fleeChancePercent: Int,
    val desperateFleeChancePercent: Int,
    val mandatoryRun: MandatoryRunRule,
    val banners: List<ImageId>,
)

data class CombatActionModel(
    val binding: InputBindingModel,
    val action: KeyActionId,
    val kind: CombatActionKind,
    val requirement: CombatActionRequirement,
    val prompt: TextBlock,
) {
    constructor(
        key: Char,
        action: KeyActionId,
        kind: CombatActionKind,
        requirement: CombatActionRequirement,
        prompt: TextBlock,
    ) : this(inputKey(key), action, kind, requirement, prompt)

    val key: Char get() = binding.primaryChar
}

data class BattleOpeningKeysModel(
    val fight: InputBindingModel,
    val run: InputBindingModel,
)

data class BattleTextModel(
    val encounterBanner: TextBlock,
    val youFace: TextBlock,
    val foeDisplayName: TextBlock,
    val stillFace: TextBlock,
    val fight: TextBlock,
    val run: TextBlock,
    val bloodFlow: TextBlock,
    val whatNow: TextBlock,
    val targetPrompt: TextBlock,
    val useWhat: TextBlock,
    val spellAbort: TextBlock,
    val runCoward: TextBlock,
    val runDesperateFail: TextBlock,
    val runFailAway: TextBlock,
    val runEscaped: TextBlock,
    val notPossible: TextBlock,
    val magicImmune: TextBlock,
    val spellFizzle: TextBlock,
    val notEnoughMagic: TextBlock,
    val restGainHits: TextBlock,
    val defendStance: TextBlock,
    val battleCry: TextBlock,
    val playerHit: TextBlock,
    val playerMiss: TextBlock,
    val monsterHit: TextBlock,
    val monsterMiss: TextBlock,
    val memberDeath: TextBlock,
    val killed: TextBlock,
    val partyHeld: TextBlock,
    val partyDefeated: TextBlock,
    val victoryBested: TextBlock,
    val victoryEachSurvivor: TextBlock,
    val victoryReward: TextBlock,
    val victoryFoundObject: TextBlock,
    val victoryLoot: TextBlock,
    val levelGain: TextBlock,
)

data class PlayerAttackRulesModel(
    val hitBonus: Int,
    val damageSpreadMax: Int,
    val confusionDivisor: Int,
    val hpDefenseDivisor: Int,
)

data class DefendRulesModel(
    val weaponSkillPenalty: Int,
    val restThreshold: Int,
    val cap: Int,
)

data class BattleCryRulesModel(
    val weaponSkillPenalty: Int,
)

data class PlayerRoundModel(
    val attack: PlayerAttackRulesModel,
    val defend: DefendRulesModel,
    val battleCry: BattleCryRulesModel,
)

data class MonsterMeleeRulesModel(
    val hitBonus: Int,
    val damageDivisor: Int,
    val damageSpreadMax: Int,
    val damageMin: Int,
    val fallback: SceneId,
)

data class MonsterRoundModel(
    val spellChanceBasePercent: Int,
    val spellPowerDivisor: Int,
    val melee: MonsterMeleeRulesModel,
)

data class StandardBattleModel(
    val opening: EncounterOpeningModel,
    val openingKeys: BattleOpeningKeysModel,
    val actions: List<CombatActionModel>,
    val playerRound: PlayerRoundModel,
    val monsterRound: MonsterRoundModel,
    val text: BattleTextModel,
    val rewards: RewardBundleId,
    val roundLimit: Int,
    val encounterFoeCountMax: Int,
    val encounterHitPointMinFormula: FormulaId,
    val encounterHitPointMaxFormula: FormulaId,
    val victoryImage: ImageId?,
    val nativeServices: List<EngineServiceUse>,
)

data class CombatSystemModel(
    val standardBattle: StandardBattleModel?,
)

object BattleText {
    val foeDisplayName: TextPart = TextPart.BattleFoeDisplayName
    val attackerName: TextPart = TextPart.BattleAttackerName
    val targetName: TextPart = TextPart.BattleTargetName
    val damage: TextPart = TextPart.BattleDamage
    val experience: TextPart = TextPart.BattleExperience
    val gold: TextPart = TextPart.BattleGold
    val lootName: TextPart = TextPart.BattleLootName
    val levelMemberName: TextPart = TextPart.BattleLevelMemberName
    val levelsGained: TextPart = TextPart.BattleLevelsGained
}

@RylNextDsl
class CombatBuilder {
    private var standardBattleModel: StandardBattleModel? = null

    fun standardBattle(body: StandardBattleBuilder.() -> Unit) {
        standardBattleModel = StandardBattleBuilder().apply(body).build()
    }

    fun build(): CombatSystemModel = CombatSystemModel(standardBattleModel)
}

@RylNextDsl
class StandardBattleBuilder {
    private var openingModel: EncounterOpeningModel? = null
    private var openingKeysModel: BattleOpeningKeysModel? = null
    private var actionModels: List<CombatActionModel>? = null
    private var playerRoundModel: PlayerRoundModel? = null
    private var monsterRoundModel: MonsterRoundModel? = null
    private var textModel: BattleTextModel? = null
    var rewards: RewardBundleId? = null
    var roundLimit: Int? = null
    var encounterFoeCountMax: Int? = null
    var encounterHitPointMinFormula: FormulaId? = null
    var encounterHitPointMaxFormula: FormulaId? = null
    private var victoryImage: ImageId? = null

    fun text(body: BattleTextBuilder.() -> Unit) {
        textModel = BattleTextBuilder(textModel).apply(body).build()
    }

    fun opening(body: EncounterOpeningBuilder.() -> Unit) {
        openingModel = EncounterOpeningBuilder(openingModel).apply(body).build()
    }

    fun openingKeys(body: BattleOpeningKeysBuilder.() -> Unit) {
        openingKeysModel = BattleOpeningKeysBuilder(openingKeysModel).apply(body).build()
    }

    fun playerActions(body: PlayerCombatActionsBuilder.() -> Unit) {
        actionModels = PlayerCombatActionsBuilder().apply(body).build()
    }

    fun playerRound(body: PlayerRoundBuilder.() -> Unit) {
        playerRoundModel = PlayerRoundBuilder(playerRoundModel).apply(body).build()
    }

    fun monsterRound(body: MonsterRoundBuilder.() -> Unit) {
        monsterRoundModel = MonsterRoundBuilder(monsterRoundModel).apply(body).build()
    }

    fun victoryImage(image: ImageId?) {
        victoryImage = image
    }

    fun build(): StandardBattleModel =
        StandardBattleModel(
            opening = requiredStandardBattle(openingModel, "opening { ... }"),
            openingKeys = requiredStandardBattle(openingKeysModel, "openingKeys { ... }"),
            actions = requiredCombatActions(actionModels),
            playerRound = requiredStandardBattle(playerRoundModel, "playerRound { ... }"),
            monsterRound = requiredStandardBattle(monsterRoundModel, "monsterRound { ... }"),
            text = requiredStandardBattle(textModel, "text { ... }"),
            rewards = requiredStandardBattle(rewards, "rewards = ..."),
            roundLimit = requiredStandardBattle(roundLimit, "roundLimit = ..."),
            encounterFoeCountMax = requiredStandardBattle(encounterFoeCountMax, "encounterFoeCountMax = ..."),
            encounterHitPointMinFormula =
                requiredStandardBattle(encounterHitPointMinFormula, "encounterHitPointMinFormula = ..."),
            encounterHitPointMaxFormula =
                requiredStandardBattle(encounterHitPointMaxFormula, "encounterHitPointMaxFormula = ..."),
            victoryImage = victoryImage,
            nativeServices = defaultNativeServices,
        )

    companion object {
        private val defaultNativeServices =
            listOf(
                EngineServiceUse(EngineService.KeyboardInput, "fight/run, action, spell, item, and target selection"),
                EngineServiceUse(EngineService.Rendering, "combat banners, text panes, map-window art, and status refresh"),
                EngineServiceUse(EngineService.RandomNumbers, "encounter sizing, hit rolls, damage rolls, fleeing, loot, and AI"),
                EngineServiceUse(EngineService.CombatContextStorage, "live monster hit points, death bits, and round status effects"),
                EngineServiceUse(EngineService.InventoryStorage, "item use, consume, loot placement, and first-free-slot lookup"),
                EngineServiceUse(EngineService.PartyStateMutation, "HP, mana, XP, gold, kills, and death-state updates"),
                EngineServiceUse(EngineService.TextFormatting, "party, monster, item, pronoun, and reward bindings"),
            )
    }
}

@RylNextDsl
class BattleOpeningKeysBuilder(
    initial: BattleOpeningKeysModel? = null,
) {
    var fight: InputBindingModel? = initial?.fight
    var run: InputBindingModel? = initial?.run

    fun build(): BattleOpeningKeysModel =
        BattleOpeningKeysModel(
            fight = requiredStandardBattle(fight, "openingKeys.fight = ..."),
            run = requiredStandardBattle(run, "openingKeys.run = ..."),
        )
}

@RylNextDsl
class BattleTextBuilder(
    initial: BattleTextModel? = null,
) {
    private var encounterBannerText = initial?.encounterBanner
    private var youFaceText = initial?.youFace
    private var foeDisplayNameText = initial?.foeDisplayName
    private var stillFaceText = initial?.stillFace
    private var fightText = initial?.fight
    private var runText = initial?.run
    private var bloodFlowText = initial?.bloodFlow
    private var whatNowText = initial?.whatNow
    private var targetPromptText = initial?.targetPrompt
    private var useWhatText = initial?.useWhat
    private var spellAbortText = initial?.spellAbort
    private var runCowardText = initial?.runCoward
    private var runDesperateFailText = initial?.runDesperateFail
    private var runFailAwayText = initial?.runFailAway
    private var runEscapedText = initial?.runEscaped
    private var notPossibleText = initial?.notPossible
    private var magicImmuneText = initial?.magicImmune
    private var spellFizzleText = initial?.spellFizzle
    private var notEnoughMagicText = initial?.notEnoughMagic
    private var restGainHitsText = initial?.restGainHits
    private var defendStanceText = initial?.defendStance
    private var battleCryText = initial?.battleCry
    private var playerHitText = initial?.playerHit
    private var playerMissText = initial?.playerMiss
    private var monsterHitText = initial?.monsterHit
    private var monsterMissText = initial?.monsterMiss
    private var memberDeathText = initial?.memberDeath
    private var killedText = initial?.killed
    private var partyHeldText = initial?.partyHeld
    private var partyDefeatedText = initial?.partyDefeated
    private var victoryBestedText = initial?.victoryBested
    private var victoryEachSurvivorText = initial?.victoryEachSurvivor
    private var victoryRewardText = initial?.victoryReward
    private var victoryFoundObjectText = initial?.victoryFoundObject
    private var victoryLootText = initial?.victoryLoot
    private var levelGainText = initial?.levelGain

    fun encounterBanner(body: StructuredTextBlockBuilder.() -> Unit) {
        encounterBannerText = structuredText(body)
    }

    fun youFace(body: StructuredTextBlockBuilder.() -> Unit) {
        youFaceText = structuredText(body)
    }

    fun foeDisplayName(body: StructuredTextBlockBuilder.() -> Unit) {
        foeDisplayNameText = structuredText(body)
    }

    fun stillFace(body: StructuredTextBlockBuilder.() -> Unit) {
        stillFaceText = structuredText(body)
    }

    fun fight(body: StructuredTextBlockBuilder.() -> Unit) {
        fightText = structuredText(body)
    }

    fun run(body: StructuredTextBlockBuilder.() -> Unit) {
        runText = structuredText(body)
    }

    fun bloodFlow(body: StructuredTextBlockBuilder.() -> Unit) {
        bloodFlowText = structuredText(body)
    }

    fun whatNow(body: StructuredTextBlockBuilder.() -> Unit) {
        whatNowText = structuredText(body)
    }

    fun targetPrompt(body: StructuredTextBlockBuilder.() -> Unit) {
        targetPromptText = structuredText(body)
    }

    fun targetPrompt(text: TextBlock) {
        targetPromptText = text
    }

    fun useWhat(body: StructuredTextBlockBuilder.() -> Unit) {
        useWhatText = structuredText(body)
    }

    fun spellAbort(body: StructuredTextBlockBuilder.() -> Unit) {
        spellAbortText = structuredText(body)
    }

    fun runCoward(body: StructuredTextBlockBuilder.() -> Unit) {
        runCowardText = structuredText(body)
    }

    fun runDesperateFail(body: StructuredTextBlockBuilder.() -> Unit) {
        runDesperateFailText = structuredText(body)
    }

    fun runFailAway(body: StructuredTextBlockBuilder.() -> Unit) {
        runFailAwayText = structuredText(body)
    }

    fun runEscaped(body: StructuredTextBlockBuilder.() -> Unit) {
        runEscapedText = structuredText(body)
    }

    fun notPossible(body: StructuredTextBlockBuilder.() -> Unit) {
        notPossibleText = structuredText(body)
    }

    fun magicImmune(body: StructuredTextBlockBuilder.() -> Unit) {
        magicImmuneText = structuredText(body)
    }

    fun spellFizzle(body: StructuredTextBlockBuilder.() -> Unit) {
        spellFizzleText = structuredText(body)
    }

    fun notEnoughMagic(body: StructuredTextBlockBuilder.() -> Unit) {
        notEnoughMagicText = structuredText(body)
    }

    fun restGainHits(body: StructuredTextBlockBuilder.() -> Unit) {
        restGainHitsText = structuredText(body)
    }

    fun defendStance(body: StructuredTextBlockBuilder.() -> Unit) {
        defendStanceText = structuredText(body)
    }

    fun battleCry(body: StructuredTextBlockBuilder.() -> Unit) {
        battleCryText = structuredText(body)
    }

    fun playerHit(body: StructuredTextBlockBuilder.() -> Unit) {
        playerHitText = structuredText(body)
    }

    fun playerMiss(body: StructuredTextBlockBuilder.() -> Unit) {
        playerMissText = structuredText(body)
    }

    fun monsterHit(body: StructuredTextBlockBuilder.() -> Unit) {
        monsterHitText = structuredText(body)
    }

    fun monsterMiss(body: StructuredTextBlockBuilder.() -> Unit) {
        monsterMissText = structuredText(body)
    }

    fun memberDeath(body: StructuredTextBlockBuilder.() -> Unit) {
        memberDeathText = structuredText(body)
    }

    fun killed(body: StructuredTextBlockBuilder.() -> Unit) {
        killedText = structuredText(body)
    }

    fun killed(text: TextBlock) {
        killedText = text
    }

    fun partyHeld(body: StructuredTextBlockBuilder.() -> Unit) {
        partyHeldText = structuredText(body)
    }

    fun partyDefeated(body: StructuredTextBlockBuilder.() -> Unit) {
        partyDefeatedText = structuredText(body)
    }

    fun victoryBested(body: StructuredTextBlockBuilder.() -> Unit) {
        victoryBestedText = structuredText(body)
    }

    fun victoryEachSurvivor(body: StructuredTextBlockBuilder.() -> Unit) {
        victoryEachSurvivorText = structuredText(body)
    }

    fun victoryReward(body: StructuredTextBlockBuilder.() -> Unit) {
        victoryRewardText = structuredText(body)
    }

    fun victoryFoundObject(body: StructuredTextBlockBuilder.() -> Unit) {
        victoryFoundObjectText = structuredText(body)
    }

    fun victoryLoot(body: StructuredTextBlockBuilder.() -> Unit) {
        victoryLootText = structuredText(body)
    }

    fun levelGain(body: StructuredTextBlockBuilder.() -> Unit) {
        levelGainText = structuredText(body)
    }

    fun build(): BattleTextModel =
        BattleTextModel(
            encounterBanner = requiredStandardBattleText(encounterBannerText, "encounterBanner"),
            youFace = requiredStandardBattleText(youFaceText, "youFace"),
            foeDisplayName = requiredStandardBattleText(foeDisplayNameText, "foeDisplayName"),
            stillFace = requiredStandardBattleText(stillFaceText, "stillFace"),
            fight = requiredStandardBattleText(fightText, "fight"),
            run = requiredStandardBattleText(runText, "run"),
            bloodFlow = requiredStandardBattleText(bloodFlowText, "bloodFlow"),
            whatNow = requiredStandardBattleText(whatNowText, "whatNow"),
            targetPrompt = requiredStandardBattleText(targetPromptText, "targetPrompt"),
            useWhat = requiredStandardBattleText(useWhatText, "useWhat"),
            spellAbort = requiredStandardBattleText(spellAbortText, "spellAbort"),
            runCoward = requiredStandardBattleText(runCowardText, "runCoward"),
            runDesperateFail = requiredStandardBattleText(runDesperateFailText, "runDesperateFail"),
            runFailAway = requiredStandardBattleText(runFailAwayText, "runFailAway"),
            runEscaped = requiredStandardBattleText(runEscapedText, "runEscaped"),
            notPossible = requiredStandardBattleText(notPossibleText, "notPossible"),
            magicImmune = requiredStandardBattleText(magicImmuneText, "magicImmune"),
            spellFizzle = requiredStandardBattleText(spellFizzleText, "spellFizzle"),
            notEnoughMagic = requiredStandardBattleText(notEnoughMagicText, "notEnoughMagic"),
            restGainHits = requiredStandardBattleText(restGainHitsText, "restGainHits"),
            defendStance = requiredStandardBattleText(defendStanceText, "defendStance"),
            battleCry = requiredStandardBattleText(battleCryText, "battleCry"),
            playerHit = requiredStandardBattleText(playerHitText, "playerHit"),
            playerMiss = requiredStandardBattleText(playerMissText, "playerMiss"),
            monsterHit = requiredStandardBattleText(monsterHitText, "monsterHit"),
            monsterMiss = requiredStandardBattleText(monsterMissText, "monsterMiss"),
            memberDeath = requiredStandardBattleText(memberDeathText, "memberDeath"),
            killed = requiredStandardBattleText(killedText, "killed"),
            partyHeld = requiredStandardBattleText(partyHeldText, "partyHeld"),
            partyDefeated = requiredStandardBattleText(partyDefeatedText, "partyDefeated"),
            victoryBested = requiredStandardBattleText(victoryBestedText, "victoryBested"),
            victoryEachSurvivor = requiredStandardBattleText(victoryEachSurvivorText, "victoryEachSurvivor"),
            victoryReward = requiredStandardBattleText(victoryRewardText, "victoryReward"),
            victoryFoundObject = requiredStandardBattleText(victoryFoundObjectText, "victoryFoundObject"),
            victoryLoot = requiredStandardBattleText(victoryLootText, "victoryLoot"),
            levelGain = requiredStandardBattleText(levelGainText, "levelGain"),
        )
}

@RylNextDsl
class EncounterOpeningBuilder(
    initial: EncounterOpeningModel? = null,
) {
    var fleeChancePercent: Int? = initial?.fleeChancePercent
    var desperateFleeChancePercent: Int? = initial?.desperateFleeChancePercent
    var mandatoryRun: MandatoryRunRule? = initial?.mandatoryRun
    private val banners = initial?.banners?.toMutableList() ?: mutableListOf()

    fun encounterBanners(vararg images: ImageId) {
        banners.clear()
        banners += images
    }

    fun build(): EncounterOpeningModel {
        require(banners.isNotEmpty()) { "standardBattle.opening requires encounterBanners(...)" }
        return EncounterOpeningModel(
            fleeChancePercent = requiredStandardBattle(fleeChancePercent, "opening.fleeChancePercent = ..."),
            desperateFleeChancePercent =
                requiredStandardBattle(desperateFleeChancePercent, "opening.desperateFleeChancePercent = ..."),
            mandatoryRun = requiredStandardBattle(mandatoryRun, "opening.mandatoryRun = ..."),
            banners = banners.toList(),
        )
    }
}

@RylNextDsl
class PlayerCombatActionsBuilder {
    private val actions = mutableListOf<CombatActionModel>()

    fun attack(
        key: Char,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        attack(inputKey(key), DslDefaultKeyActions.attack, prompt)
    }

    fun attack(
        binding: InputBindingModel,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        attack(binding, DslDefaultKeyActions.attack, prompt)
    }

    fun attack(
        binding: InputBindingModel,
        action: KeyActionId,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        action(binding, action, CombatActionKind.Attack, CombatActionRequirement.Always, prompt)
    }

    fun defend(
        key: Char,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        defend(inputKey(key), DslDefaultKeyActions.defend, prompt)
    }

    fun defend(
        binding: InputBindingModel,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        defend(binding, DslDefaultKeyActions.defend, prompt)
    }

    fun defend(
        binding: InputBindingModel,
        action: KeyActionId,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        action(binding, action, CombatActionKind.Defend, CombatActionRequirement.Always, prompt)
    }

    fun useMagicItem(
        key: Char,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        useMagicItem(inputKey(key), DslDefaultKeyActions.useItem, prompt)
    }

    fun useMagicItem(
        binding: InputBindingModel,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        useMagicItem(binding, DslDefaultKeyActions.useItem, prompt)
    }

    fun useMagicItem(
        binding: InputBindingModel,
        action: KeyActionId,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        action(binding, action, CombatActionKind.UseMagicItem, CombatActionRequirement.Always, prompt)
    }

    fun battleCry(
        key: Char,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        battleCry(inputKey(key), DslDefaultKeyActions.battleCry, prompt)
    }

    fun battleCry(
        binding: InputBindingModel,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        battleCry(binding, DslDefaultKeyActions.battleCry, prompt)
    }

    fun battleCry(
        binding: InputBindingModel,
        action: KeyActionId,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        action(binding, action, CombatActionKind.BattleCry, CombatActionRequirement.Always, prompt)
    }

    fun castMageSpell(
        key: Char,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        castMageSpell(inputKey(key), DslDefaultKeyActions.castSpell, prompt)
    }

    fun castMageSpell(
        binding: InputBindingModel,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        castMageSpell(binding, DslDefaultKeyActions.castSpell, prompt)
    }

    fun castMageSpell(
        binding: InputBindingModel,
        action: KeyActionId,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        action(binding, action, CombatActionKind.CastMageSpell, CombatActionRequirement.Mage, prompt)
    }

    fun build(): List<CombatActionModel> = actions.toList()

    private fun action(
        binding: InputBindingModel,
        action: KeyActionId,
        kind: CombatActionKind,
        requirement: CombatActionRequirement,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        actions += CombatActionModel(binding, action, kind, requirement, structuredText(prompt))
    }
}

@RylNextDsl
class PlayerRoundBuilder(
    initial: PlayerRoundModel? = null,
) {
    private var attackRules = initial?.attack
    private var defendRules = initial?.defend
    private var battleCryRules = initial?.battleCry

    fun attack(
        hitBonus: Int,
        damageSpreadMax: Int,
        confusionDivisor: Int,
        hpDefenseDivisor: Int,
    ) {
        attackRules = PlayerAttackRulesModel(hitBonus, damageSpreadMax, confusionDivisor, hpDefenseDivisor)
    }

    fun defend(
        weaponSkillPenalty: Int,
        restThreshold: Int,
        cap: Int,
    ) {
        defendRules = DefendRulesModel(weaponSkillPenalty, restThreshold, cap)
    }

    fun battleCry(weaponSkillPenalty: Int) {
        battleCryRules = BattleCryRulesModel(weaponSkillPenalty)
    }

    fun build(): PlayerRoundModel =
        PlayerRoundModel(
            attack = requiredStandardBattle(attackRules, "playerRound.attack(...)"),
            defend = requiredStandardBattle(defendRules, "playerRound.defend(...)"),
            battleCry = requiredStandardBattle(battleCryRules, "playerRound.battleCry(...)"),
        )
}

@RylNextDsl
class MonsterRoundBuilder(
    initial: MonsterRoundModel? = null,
) {
    private var spellChanceBasePercent = initial?.spellChanceBasePercent
    private var spellPowerDivisor = initial?.spellPowerDivisor
    private var meleeRules = initial?.melee

    fun spellChance(
        basePercent: Int,
        spellPowerDivisor: Int,
    ) {
        spellChanceBasePercent = basePercent
        this.spellPowerDivisor = spellPowerDivisor
    }

    fun melee(
        hitBonus: Int,
        damageDivisor: Int,
        damageSpreadMax: Int,
        damageMin: Int,
        fallback: SceneId,
    ) {
        meleeRules = MonsterMeleeRulesModel(hitBonus, damageDivisor, damageSpreadMax, damageMin, fallback)
    }

    fun build(): MonsterRoundModel =
        MonsterRoundModel(
            spellChanceBasePercent = requiredStandardBattle(spellChanceBasePercent, "monsterRound.spellChance(...)"),
            spellPowerDivisor = requiredStandardBattle(spellPowerDivisor, "monsterRound.spellChance(...)"),
            melee = requiredStandardBattle(meleeRules, "monsterRound.melee(...)"),
        )
}

private fun requiredCombatActions(actions: List<CombatActionModel>?): List<CombatActionModel> {
    val required = requiredStandardBattle(actions, "playerActions { ... }")
    require(required.isNotEmpty()) { "standardBattle requires at least one player action" }
    return required
}

private fun requiredStandardBattleText(
    text: TextBlock?,
    name: String,
): TextBlock = requiredStandardBattle(text, "text.$name { ... }")

private fun <T : Any> requiredStandardBattle(
    value: T?,
    declaration: String,
): T = checkNotNull(value) { "standardBattle requires $declaration" }
