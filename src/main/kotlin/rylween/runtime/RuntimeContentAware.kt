package rylween.runtime

import rylween.dsl.CombatSystemModel
import rylween.dsl.DungeonSystemModel
import rylween.dsl.FormulaModel
import rylween.dsl.GameProgressModel
import rylween.dsl.ItemUseModel
import rylween.dsl.LevelingModel
import rylween.dsl.MonsterModel
import rylween.dsl.RealmContentModel
import rylween.dsl.RewardBundleModel
import rylween.dsl.RuntimeContent
import rylween.dsl.SpellModel

interface RuntimeContentAware {
    val content: RuntimeContent

    val dungeons: DungeonSystemModel get() = content.dungeons
    val combat: CombatSystemModel get() = content.combat
    val monsters: List<MonsterModel> get() = content.monsters
    val formulas: List<FormulaModel> get() = content.formulas
    val rewards: List<RewardBundleModel> get() = content.rewards
    val leveling: LevelingModel? get() = content.leveling
    val spells: List<SpellModel> get() = content.spells
    val itemUses: List<ItemUseModel> get() = content.itemUses
    val progress: GameProgressModel get() = content.progress
    val realm: RealmContentModel get() = content.realm
}
