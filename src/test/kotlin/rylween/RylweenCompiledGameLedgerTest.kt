package rylween

import rylween.dsl.BuildingId
import rylween.dsl.CharacterCreationStat
import rylween.dsl.CharacterCreationStatRollModel
import rylween.dsl.CharacterPlacementModel
import rylween.dsl.DialogueBranchSelection
import rylween.dsl.DungeonEventDisposition
import rylween.dsl.DungeonId
import rylween.dsl.EquipmentCommand
import rylween.dsl.Facing
import rylween.dsl.InputAliases
import rylween.dsl.IntAst
import rylween.dsl.ItemConsumePolicy
import rylween.dsl.ItemKind
import rylween.dsl.ItemPoolId
import rylween.dsl.ItemUseContext
import rylween.dsl.MonsterId
import rylween.dsl.NativeAllowlist
import rylween.dsl.Op
import rylween.dsl.PartyResources
import rylween.dsl.PcClass
import rylween.dsl.QuestionTopic
import rylween.dsl.Race
import rylween.dsl.RewardOverflowPolicy
import rylween.dsl.RumourUsagePolicy
import rylween.dsl.ShopBargainBonusModel
import rylween.dsl.ShopSellBargainModel
import rylween.dsl.SpellId
import rylween.dsl.StaticScreenRuntimeReport
import rylween.dsl.TemperatureBand
import rylween.dsl.TemperatureBandRange
import rylween.dsl.TerrainSettlement
import rylween.dsl.TownHandle
import rylween.dsl.TownKind
import rylween.dsl.Trigger
import rylween.dsl.Wilderness
import rylween.dsl.analyzeScriptSources
import rylween.dsl.cells
import rylween.dsl.inlineText
import rylween.dsl.isWinningConfiguration
import rylween.dsl.itemPool
import rylween.dsl.keyAction
import rylween.dsl.line
import rylween.dsl.lines
import rylween.dsl.matches
import rylween.dsl.specialInventoryItems
import rylween.dsl.structuredText
import rylween.dsl.validateAuthoredContent
import rylween.dsl.validateFlagCatalog
import rylween.dsl.validateGameProgress
import rylween.dsl.validateStates
import rylween.dsl.xy
import rylween.game.Buildings
import rylween.game.Castles
import rylween.game.DungeonEvents
import rylween.game.Dungeons
import rylween.game.EncounterTiers
import rylween.game.Images
import rylween.game.ItemPools
import rylween.game.Items
import rylween.game.Locations
import rylween.game.MapLayers
import rylween.game.Maps
import rylween.game.Milestones
import rylween.game.Missions
import rylween.game.Monsters
import rylween.game.NpcJobs
import rylween.game.NpcRoles
import rylween.game.Npcs
import rylween.game.Rewards
import rylween.game.Roles
import rylween.game.RumourTables
import rylween.game.Rumours
import rylween.game.RylweenCompiledGame
import rylween.game.Scenes
import rylween.game.ScreenRegions
import rylween.game.Spells
import rylween.game.States
import rylween.game.StaticScreens
import rylween.game.Story
import rylween.game.Terrain
import rylween.game.Towns
import rylween.save.renderRuntimeStateSnapshot
import java.io.File
import kotlin.test.Test

class RylweenCompiledGameLedgerTest {
    @Test
    fun compiledGameMatchesMigrationLedger() {
        val nextRylween = RylweenCompiledGame.nextRylween
        val nextDsl = RylweenCompiledGame.nextDsl
        val leveling = checkNotNull(nextDsl.leveling)
        val characterCreation = checkNotNull(nextDsl.characterCreation)
        val standardBattle = checkNotNull(nextDsl.combat.standardBattle)
        val standardConversation = checkNotNull(nextDsl.speak.standardConversation)
        val townSystem = checkNotNull(nextDsl.townSystem)
        val castleContent = nextDsl.castles.single { it.id == Castles.ydarg }

        check(nextDsl.flags.size == 13)
        check(nextDsl.authoredFlags.size == 13)
        check(nextDsl.itemCatalog.size == 63)
        check(nextDsl.itemCatalog.single { it.id == Items.redPotion }.kind == ItemKind.Potion)
        check(nextDsl.itemPools.size == 7)
        check(nextDsl.progress.milestones.size == 6)
        check(
            nextDsl.progress.win
                ?.required
                ?.size == 5,
        )
        check(leveling.maxLevel == 28)
        check(leveling.classGrowth.size == 6)
        check(characterCreation.statRoll == CharacterCreationStatRollModel(min = 15, max = 70))
        check(characterCreation.start.map == Maps.wilderness)
        check(characterCreation.start.position == Wilderness.xy(12, 9))
        check(characterCreation.start.facing == Facing.North)
        check(characterCreation.start.partyMemberMax == 4)
        check(characterCreation.start.level == 1)
        check(characterCreation.start.dailyTimeLeft == 175)
        check(
            characterCreation.start.resources ==
                PartyResources(
                    gold = 1000,
                    torches = 5,
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                ),
        )
        check(characterCreation.races.map { it.race } == listOf(Race.Human, Race.Dwarf, Race.Elf, Race.Halfling))
        check(characterCreation.races.map { it.label } == listOf("Human", "Dwarf", "Elf", "Halfling"))
        check(
            characterCreation.classes.map { it.klass } ==
                listOf(
                    PcClass.Fighter,
                    PcClass.Thief,
                    PcClass.Mage,
                    PcClass.Priest,
                    PcClass.Bard,
                    PcClass.Ranger,
                ),
        )
        check(
            characterCreation.classes.single { it.klass == PcClass.Fighter }.let {
                it.requiredStat == CharacterCreationStat.WeaponSkill && it.minimum == 50
            },
        )
        check(
            characterCreation.classes.single { it.klass == PcClass.Mage }.let {
                it.requiredStat == CharacterCreationStat.Wisdom && it.minimum == 50
            },
        )
        check(
            characterCreation.classes.single { it.klass == PcClass.Bard }.let {
                it.requiredStat == CharacterCreationStat.Charisma && it.minimum == 50
            },
        )
        check(nextDsl.formulas.size == 5)
        check(nextDsl.rewards.size == 1)
        check(
            nextDsl.rewards
                .single()
                .application.overflow == RewardOverflowPolicy.SkipIfInventoryFull,
        )
        check(standardBattle.actions.size == 5)
        check(standardBattle.actions.map { it.key } == listOf('A', 'D', 'U', 'B', 'C'))
        check(standardBattle.actions.map { it.prompt }.first() == structuredText { line("(A)ttack") })
        check(standardBattle.text.useWhat == structuredText { lines("Use what?", "") })
        check(standardBattle.playerRound.attack.confusionDivisor == 3)
        check(standardBattle.playerRound.attack.hpDefenseDivisor == 12)
        check(standardBattle.playerRound.defend.cap == 250)
        check(standardBattle.monsterRound.melee.damageDivisor == 6)
        check(standardBattle.roundLimit == 200)
        check(standardBattle.encounterFoeCountMax == 8)
        check(standardBattle.victoryImage == Images.Backdrop.battleVictory)
        check(standardBattle.rewards == Rewards.standardBattle)
        check(standardConversation.menu.actions.map { it.key } == listOf('C', 'Q', 'R', 'B'))
        check(standardConversation.menu.questions.map { it.key } == listOf('L', 'M', 'P', 'N'))
        check(standardConversation.branchSelection == DialogueBranchSelection.FirstMatchingStateThenDefault)
        check(standardConversation.rumour.usage == RumourUsagePolicy.OncePerConversationThenFallback)
        check(nextDsl.speak.textTables.screens.size == 2)
        check(nextDsl.speak.questionActions.size == 1)
        check(
            nextDsl.speak.questionActions
                .single()
                .topic == QuestionTopic.Lodging,
        )
        check(
            nextDsl.speak.questionActions
                .single()
                .job == NpcJobs.bargirl,
        )
        check(nextDsl.speak.textTables.chitchatPrompts.size == 3)
        check(nextDsl.speak.textTables.chitchatReplies.size == 3)
        check(nextDsl.speak.textTables.questionReplies.size == 3)
        check(
            nextDsl.speak.textTables.chitchatReplies
                .all { it.replies.size == NpcJobs.all.size },
        )
        check(
            nextDsl.speak.textTables.questionReplies
                .single { it.topic == QuestionTopic.People }
                .replies.size == NpcJobs.all.size,
        )
        check(
            nextDsl.speak.textTables.questionReplies
                .single { it.topic == QuestionTopic.Magic }
                .replies.size == NpcJobs.all.size,
        )
        check(
            nextDsl.speak.textTables.questionReplies
                .single { it.topic == QuestionTopic.Lodging }
                .replies.size == NpcJobs.all.size - 1,
        )
        check(
            nextDsl.speak.textTables.rumourTables
                .single { it.id == RumourTables.standardTown }
                .entries.size == 33,
        )
        check(
            nextDsl.speak.textTables.rumourTables
                .single()
                .entries
                .count { it.id == null } == 29,
        )
        check(
            nextDsl.speak.textTables.rumourTables
                .single()
                .entries
                .any { it.id == Rumours.crownSeer },
        )
        check(
            nextDsl.speak.textTables.rumourTables
                .single()
                .entries
                .single {
                    it.id == Rumours.banditsQuestGiver
                }.excludedBy == listOf(Story.garth.bandits.recovered, Story.garth.bandits.closed),
        )
        check(nextDsl.dialogue.size == NpcRoles.all.size)
        check(
            nextDsl.dialogue
                .single { it.npc == Npcs.leander }
                .branches
                .single()
                .whenState == Story.jazzy.ogre.none,
        )
        check(
            nextDsl.dialogue
                .single { it.npc == Npcs.ithienn }
                .branches.size == 4,
        )
        check(nextDsl.towns.size == 4)
        val townsById = nextDsl.towns.associateBy { it.id }

        fun townServices(town: TownHandle): List<BuildingId> = townsById.getValue(town.id).services.map { it.building }
        check(
            townsById.getValue(Towns.thord.id).let {
                it.name == "Thord" && it.position == xy(12, 11) && it.taxPercent == 7 && it.kind == TownKind.Village
            },
        )
        check(
            townsById.getValue(Towns.ollk.id).let {
                it.name == "Ollk" && it.position == xy(48, 12) && it.taxPercent == 10 && it.kind == TownKind.Village
            },
        )
        check(
            townsById.getValue(Towns.wilor.id).let {
                it.name == "Wilor" && it.position == xy(43, 20) && it.taxPercent == 9 && it.kind == TownKind.City
            },
        )
        check(
            townsById.getValue(Towns.murn.id).let {
                it.name == "Murn" && it.position == xy(55, 29) && it.taxPercent == 8 && it.kind == TownKind.Village
            },
        )
        check(
            townServices(Towns.thord) ==
                listOf(
                    Buildings.merchant,
                    Buildings.inn,
                    Buildings.tavern,
                    Buildings.smith,
                    Buildings.church,
                    Buildings.guild,
                ),
        )
        check(townServices(Towns.ollk) == listOf(Buildings.merchant, Buildings.inn, Buildings.tavern, Buildings.smith, Buildings.guild))
        check(
            townServices(Towns.wilor) ==
                listOf(
                    Buildings.merchant,
                    Buildings.inn,
                    Buildings.tavern,
                    Buildings.smith,
                    Buildings.church,
                    Buildings.guild,
                ),
        )
        check(townServices(Towns.murn) == listOf(Buildings.merchant, Buildings.inn, Buildings.tavern, Buildings.smith, Buildings.guild))
        check(nextDsl.towns.single { it.id == Towns.murn.id }.position == xy(55, 29))
        check(nextDsl.towns.single { it.id == Towns.wilor.id }.kind == TownKind.City)
        check(townSystem.menu.map { it.key } == listOf('C', 'S', 'I', 'M', 'T', 'G', 'L'))
        check(townSystem.menu.single { it.key == 'C' }.building == Buildings.church)
        check(townSystem.menu.single { it.action == keyAction("leave_town") }.endsMenu)
        check(townSystem.leavePrompts == listOf(EquipmentCommand.ReadyWeapon, EquipmentCommand.ReadyArmor))
        check(townSystem.entryTax.minimumLeaderLevel == 5)
        check(townSystem.entryTax.pay.primary == "P")
        check(townSystem.entryTax.pay.label == "Pay to Enter")
        check(townSystem.entryTax.leave.primary == "L")
        check(
            townSystem.buildingServices.map { it.building } ==
                listOf(
                    Buildings.church,
                    Buildings.smith,
                    Buildings.inn,
                    Buildings.merchant,
                    Buildings.tavern,
                    Buildings.guild,
                ),
        )
        check(
            townSystem.buildingServices.associate { it.building to it.scene } ==
                mapOf(
                    Buildings.church to Scenes.Towne.church,
                    Buildings.smith to Scenes.Towne.smith,
                    Buildings.inn to Scenes.Towne.inn,
                    Buildings.merchant to Scenes.Towne.merchant,
                    Buildings.tavern to Scenes.Towne.tavern,
                    Buildings.guild to Scenes.Towne.guild,
                ),
        )
        check(
            townSystem.buildingServices.associate { it.building to (it.opensAt to it.closesAt) } ==
                mapOf(
                    Buildings.church to (null to null),
                    Buildings.smith to (6 to 18),
                    Buildings.inn to (null to null),
                    Buildings.merchant to (6 to 18),
                    Buildings.tavern to (6 to 1),
                    Buildings.guild to (null to null),
                ),
        )
        check(
            nextDsl.wilderness.keys.moveNorth
                .matches("8"),
        )
        check(
            nextDsl.wilderness.keys.moveNorth
                .matches("UP"),
        )
        check(
            nextDsl.wilderness.keys.moveNorth
                .matches(InputAliases.ArrowUp),
        )
        check(nextDsl.characters.size == 39)
        check(nextDsl.characters.single { Roles.crownSeer in it.roles }.placement == CharacterPlacementModel.Randomized)
        check(nextDsl.characters.single { it.id == Npcs.marcus }.placement == CharacterPlacementModel.Fixed(Wilderness.xy(48, 7)))
        check(
            listOf(Npcs.alex, Npcs.galdon, Npcs.kosch, Npcs.ufarsin).all { npc ->
                nextDsl.characters.single { it.id == npc }.placement == CharacterPlacementModel.RandomizedWilderness
            },
        )
        check(nextDsl.characters.flatMap { it.roles }.toSet() == NpcRoles.all.toSet())
        check(nextDsl.monsters.size == 68)
        check(nextDsl.monsters.count { it.tier == EncounterTiers.weak } == 10)
        check(nextDsl.monsters.count { it.tier == EncounterTiers.wild } == 14)
        check(nextDsl.monsters.count { it.tier == EncounterTiers.deep } == 20)
        check(nextDsl.monsters.single { it.id == Monsters.gnoll }.countMaxLevelDivisor == 2)
        check(
            nextDsl.monsters.filter { it.tier == EncounterTiers.weak }.map { it.id } ==
                listOf(
                    Monsters.kobold,
                    Monsters.orc,
                    Monsters.troll,
                    Monsters.warrior,
                    Monsters.goblin,
                    Monsters.wildDog,
                    Monsters.knight,
                    Monsters.bandit,
                    Monsters.hobgoblin,
                    Monsters.zombie,
                ),
        )
        check(
            nextDsl.monsters.filter { it.tier == EncounterTiers.wild }.map { it.id } ==
                listOf(
                    Monsters.stirge,
                    Monsters.wanderMage,
                    Monsters.hillGiant,
                    Monsters.wizard,
                    Monsters.druid,
                    Monsters.sabreToothTiger,
                    Monsters.headHunter,
                    Monsters.skeleton,
                    Monsters.vampireBat,
                    Monsters.giantSnake,
                    Monsters.ghoul,
                    Monsters.spider,
                    Monsters.hugeLizard,
                    Monsters.dryder,
                ),
        )
        check(
            nextDsl.monsters.filter { it.tier == EncounterTiers.deep }.map { it.id } ==
                listOf(
                    Monsters.xvart,
                    Monsters.jellyCube,
                    Monsters.troglodyte,
                    Monsters.bugbear,
                    Monsters.drowElf,
                    Monsters.phantom,
                    Monsters.worg,
                    Monsters.rustMonster,
                    Monsters.owlbear,
                    Monsters.werewolf,
                    Monsters.hookHorror,
                    Monsters.minotaur,
                    Monsters.imp,
                    Monsters.svirfneblin,
                    Monsters.yeti,
                    Monsters.wight,
                    Monsters.wraith,
                    Monsters.basilisk,
                    Monsters.wyvern,
                    Monsters.tunnelWorm,
                ),
        )
        check(nextDsl.monsters.single { it.id == Monsters.jazzy }.immune)
        check(nextDsl.monsters.single { it.id == Monsters.baronYdarg }.immune)
        check(nextDsl.monsters.single { it.id == Monsters.skagerbergKiller }.immune)
        check(nextDsl.castles.size == 1)
        check(castleContent.entry == xy(14, 30))
        check(castleContent.entryRequirement == Milestones.silverKey)
        check(castleContent.completionMilestone == Milestones.ydargVictory)
        check(castleContent.scripts.completed?.slug == "castle.ydarg.completed")
        check(
            nextDsl.scenes
                .single { it.id.slug == "castle.ydarg.completed" }
                .ops
                .filterIsInstance<Op.Encounter>()
                .map { it.foe } ==
                listOf(
                    Monsters.cryptHorror,
                    Monsters.nightGaunt,
                    Monsters.thyraga,
                    Monsters.liche,
                    Monsters.baronYdarg,
                ),
        )
        check(nextDsl.scenes.size == 28)

        fun List<Op>.anyNested(predicate: (Op) -> Boolean): Boolean =
            any { op ->
                predicate(op) ||
                    when (op) {
                        is Op.If -> op.thenOps.anyNested(predicate) || op.elseOps.anyNested(predicate)
                        is Op.Loop -> op.body.anyNested(predicate)
                        is Op.Switch -> op.cases.any { it.body.anyNested(predicate) }
                        is Op.Menu ->
                            op.options.any { it.body.anyNested(predicate) } ||
                                op.defaultOps.anyNested(predicate)
                        is Op.AskText -> op.matchOps.anyNested(predicate) || op.missOps.anyNested(predicate)
                        is Op.ForEachMember -> op.body.anyNested(predicate)
                        is Op.ForEachMonster -> op.body.anyNested(predicate)
                        else -> false
                    }
            }

        val spellsById = nextDsl.spells.associateBy { it.id }

        fun requireSpell(
            id: SpellId,
            slot: Int,
            baseCost: Int,
            name: String,
        ) {
            val spell = checkNotNull(spellsById[id])
            check(spell.slot == slot)
            check(spell.baseCost == baseCost)
            check(spell.name == name)
            check(spell.ops.isNotEmpty())
            check(!spell.ops.anyNested { it is Op.NativeCall })
        }

        check(
            nextDsl.spells.filter { it.id.slug.startsWith("mage.") }.map { it.id } ==
                listOf(
                    Spells.Mage.frost,
                    Spells.Mage.fire,
                    Spells.Mage.confusion,
                    Spells.Mage.death,
                    Spells.Mage.hold,
                    Spells.Mage.antimagic,
                ),
        )
        requireSpell(Spells.Mage.frost, slot = 1, baseCost = 8, name = "Frost Finger")
        requireSpell(Spells.Mage.fire, slot = 2, baseCost = 25, name = "Fire Cloud")
        requireSpell(Spells.Mage.confusion, slot = 3, baseCost = 17, name = "Confusion")
        requireSpell(Spells.Mage.death, slot = 4, baseCost = 78, name = "Death Strike")
        requireSpell(Spells.Mage.hold, slot = 5, baseCost = 43, name = "Hold Enemies")
        requireSpell(Spells.Mage.antimagic, slot = 6, baseCost = 28, name = "Anti Magic")

        check(
            nextDsl.spells.filter { it.id.slug.startsWith("monster.") }.map { it.id } ==
                listOf(
                    Spells.Monster.frost,
                    Spells.Monster.fire,
                    Spells.Monster.hold,
                    Spells.Monster.confusion,
                ),
        )
        requireSpell(Spells.Monster.frost, slot = 1, baseCost = 0, name = "Frost Finger")
        requireSpell(Spells.Monster.fire, slot = 2, baseCost = 0, name = "Cloud of Fire")
        requireSpell(Spells.Monster.hold, slot = 3, baseCost = 0, name = "Hold Creature")
        requireSpell(Spells.Monster.confusion, slot = 4, baseCost = 0, name = "Confusion")

        check(
            nextDsl.spells.filter { it.id.slug.startsWith("priest.") }.map { it.id } ==
                listOf(
                    Spells.Priest.heal,
                    Spells.Priest.restoration,
                    Spells.Priest.rebirth,
                    Spells.Priest.banish,
                    Spells.Priest.revelation,
                    Spells.Priest.teleport,
                    Spells.Priest.earthquake,
                ),
        )
        requireSpell(Spells.Priest.heal, slot = 1, baseCost = 8, name = "Healing")
        requireSpell(Spells.Priest.restoration, slot = 2, baseCost = 20, name = "Restoration")
        requireSpell(Spells.Priest.rebirth, slot = 3, baseCost = 90, name = "Rebirth")
        requireSpell(Spells.Priest.banish, slot = 4, baseCost = 85, name = "Banish Evil")
        requireSpell(Spells.Priest.revelation, slot = 5, baseCost = 40, name = "Revelation")
        requireSpell(Spells.Priest.teleport, slot = 6, baseCost = 35, name = "Teleport")
        requireSpell(Spells.Priest.earthquake, slot = 7, baseCost = 55, name = "Earthquake")
        check(checkNotNull(spellsById[Spells.Priest.teleport]).ops.anyNested { it is Op.TeleportParty })
        check(checkNotNull(spellsById[Spells.Priest.earthquake]).ops.anyNested { it is Op.DiscoverCave })

        fun List<Op>.containsEnterDungeon(
            dungeon: DungeonId,
            levels: Int,
        ): Boolean =
            anyNested { op ->
                op is Op.EnterDungeon && op.dungeon == dungeon && op.levels == levels
            }

        fun List<Op>.containsConsumeInventoryPool(
            pool: ItemPoolId,
            quantity: Int,
        ): Boolean =
            anyNested { op ->
                op is Op.ConsumeInventoryPool && op.pool == pool && op.quantity == IntAst.Lit(quantity)
            }

        fun List<Op>.containsEncounter(monster: MonsterId): Boolean =
            anyNested { op ->
                op is Op.Encounter && op.foe == monster
            }

        val churchScene = nextDsl.scenes.single { it.id == Scenes.Towne.church }
        val churchMenu =
            churchScene.ops
                .filterIsInstance<Op.Loop>()
                .single()
                .body
                .filterIsInstance<Op.Menu>()
                .single()
        check(churchMenu.options.map { it.key } == listOf('H', 'E', 'R', 'L'))
        check(churchScene.ops.none { it is Op.NativeCall && it.path == NativeAllowlist.townEnterService.path })
        val smithScene = nextDsl.scenes.single { it.id == Scenes.Towne.smith }
        check(smithScene.ops.none { it is Op.NativeCall && it.path == NativeAllowlist.townEnterService.path })
        val smithShop =
            smithScene.ops
                .filterIsInstance<Op.ShopService>()
                .single()
                .shop
        val smithBuy = checkNotNull(smithShop.buy)
        val smithSell = checkNotNull(smithShop.sell)
        check(smithShop.building == Buildings.smith)
        check(smithBuy.inventoryApplication.overflow == RewardOverflowPolicy.PromptDropThenPlace)
        check(
            smithBuy.catalogs
                .single { it.key == 'A' }
                .pages
                .single()
                .entries
                .map { it.item } ==
                listOf(
                    Items.leather,
                    Items.chainMail,
                    Items.plateMail,
                    Items.ringMail,
                    Items.elvinCloak,
                    Items.mithrilMail,
                ),
        )
        check(smithSell.acceptedKinds == setOf(ItemKind.Weapon, ItemKind.Armor))
        check(
            smithSell.bargain ==
                ShopSellBargainModel(
                    bonuses =
                        listOf(
                            ShopBargainBonusModel(rollGreaterThan = 90, addCurrentDivisor = 4),
                            ShopBargainBonusModel(rollGreaterThan = 75, addCurrentDivisor = 6),
                        ),
                    rejectRollBelow = 76,
                ),
        )
        val innScene = nextDsl.scenes.single { it.id == Scenes.Towne.inn }
        check(innScene.ops.none { it is Op.NativeCall && it.path == NativeAllowlist.townEnterService.path })
        check(
            innScene.ops
                .filterIsInstance<Op.Loop>()
                .single()
                .body
                .any { it is Op.Menu },
        )
        val merchantScene = nextDsl.scenes.single { it.id == Scenes.Towne.merchant }
        check(merchantScene.ops.none { it is Op.NativeCall && it.path == NativeAllowlist.townEnterService.path })
        val merchantShop =
            merchantScene.ops
                .filterIsInstance<Op.ShopService>()
                .single()
                .shop
        val merchantBuy = checkNotNull(merchantShop.buy)
        val merchantSell = checkNotNull(merchantShop.sell)
        check(merchantShop.building == Buildings.merchant)
        check(merchantBuy.inventoryApplication.overflow == RewardOverflowPolicy.PromptDropThenPlace)
        check(
            merchantBuy.catalogs
                .single()
                .pages
                .single()
                .entries
                .map { it.item } ==
                listOf(
                    Items.torch,
                    Items.ration,
                    Items.blanket,
                    Items.tent,
                    Items.rope,
                    Items.redPotion,
                    Items.donkey,
                ),
        )
        check(merchantSell.acceptedItems == setOf(Items.ration, Items.blanket, Items.tent, Items.rope))
        check(merchantSell.rejectedItems == setOf(Items.donkey))
        val tavernScene = nextDsl.scenes.single { it.id == Scenes.Towne.tavern }
        check(tavernScene.ops.none { it is Op.NativeCall && it.path == NativeAllowlist.townEnterService.path })
        val hutScene = nextDsl.scenes.single { it.id == Scenes.Towne.hut }
        check(
            hutScene.ops
                .filterIsInstance<Op.Loop>()
                .single()
                .body
                .filterIsInstance<Op.Menu>()
                .single()
                .options
                .map { it.key } ==
                listOf('T', 'L'),
        )
        check(
            nextDsl.itemPool(ItemPools.tavernScruffySpecials).items ==
                listOf(
                    Items.magicDagger,
                    Items.magicAxe,
                    Items.admtMail,
                    Items.amberPotion,
                    Items.greenGem,
                    Items.frostWand,
                    Items.fireWand,
                    Items.gemOfConfusion,
                    Items.gemOfBlindness,
                    Items.drumsOfDeath,
                    Items.staffOfHealing,
                    Items.blessingScroll,
                    Items.staffOfCuring,
                    Items.gemOfResurrection,
                    Items.kaelsAxe,
                    Items.holyHandgrenade,
                    Items.fluteOfMadness,
                ),
        )
        check(
            nextDsl.itemPool(ItemPools.dungeonWeaponFinds).items ==
                listOf(
                    Items.sword,
                    Items.axe,
                    Items.bow,
                    Items.club,
                    Items.dart,
                    Items.sling,
                    Items.longsword,
                    Items.trident,
                    Items.halbard,
                    Items.poleArm,
                    Items.crossbow,
                    Items.scythe,
                    Items.scimitar,
                    Items.twoHandSword,
                ),
        )
        check(
            nextDsl.itemPool(ItemPools.dungeonPotionGemFinds).items ==
                listOf(
                    Items.redPotion,
                    Items.bluePotion,
                    Items.amberPotion,
                    Items.redGem,
                    Items.blueGem,
                    Items.greenGem,
                    Items.chalice,
                ),
        )
        check(
            nextDsl.itemPool(ItemPools.dungeonTreasureFinds).items ==
                listOf(
                    Items.redGem,
                    Items.blueGem,
                    Items.greenGem,
                    Items.chalice,
                    Items.pendant,
                    Items.broche,
                    Items.crystalBowl,
                    Items.necklace,
                ),
        )
        check(
            tavernScene.ops.any { op ->
                op is Op.If &&
                    op.thenOps.any {
                        it is Op.RandomItemOffer &&
                            it.offer.pool == ItemPools.tavernScruffySpecials &&
                            it.offer.application.overflow == RewardOverflowPolicy.PromptDropThenPlace
                    }
            },
        )
        val tavernMenu =
            tavernScene.ops
                .filterIsInstance<Op.Loop>()
                .single()
                .body
                .filterIsInstance<Op.Menu>()
                .single()
        check(tavernMenu.options.map { it.key } == listOf('L', 'S', 'T', 'R', 'O'))
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.borinGift }
                .ops
                .any { it is Op.GrantReward },
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.jazzyFoot }
                .ops
                .any { it == Op.AchieveMilestone(Milestones.footOfJazzy) },
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.erethorEntry }
                .ops
                .containsEnterDungeon(Dungeons.erethorTemple, 12),
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.thunTemple }
                .ops
                .containsEnterDungeon(Dungeons.thunTemple, 18),
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.thunTemple }
                .ops
                .containsConsumeInventoryPool(ItemPools.thunAltarGems, 5),
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.silverKey }
                .ops
                .containsEnterDungeon(Dungeons.silverSanctum, 20),
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.borinMeet }
                .ops
                .any { it is Op.AppendJournal },
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.hobgoblinCaravan }
                .ops
                .any { it is Op.AppendJournal },
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.koboldVillage }
                .ops
                .containsEncounter(Monsters.orc),
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.ogreCave }
                .ops
                .containsEncounter(Monsters.ogreLord),
        )
        val dungeonSystem = nextDsl.dungeons
        check(nextDsl.wilderness.events.caveImage == Images.Backdrop.cave)
        check(dungeonSystem.backdrop == Images.Backdrop.insideCave)
        check(
            dungeonSystem.scenarios
                .single { it.dungeon == Dungeons.randomCave }
                .randomEvents
                .map { it.event } ==
                listOf(
                    DungeonEvents.findObject,
                    DungeonEvents.gold,
                    DungeonEvents.weakEncounter,
                    DungeonEvents.deepEncounter,
                    DungeonEvents.nothingSpecial,
                    DungeonEvents.emptyLevel,
                    DungeonEvents.goldChest,
                    DungeonEvents.torches,
                ),
        )
        check(dungeonSystem.scenarios.single { it.dungeon == Dungeons.thunTemple }.fixedLevelEvents[4] == DungeonEvents.thunRiddle1)
        check(dungeonSystem.scenarios.single { it.dungeon == Dungeons.thunTemple }.fixedLevelEvents[12] == DungeonEvents.thunRiddle3)
        check(dungeonSystem.scenarios.single { it.dungeon == Dungeons.reebarTomb }.fixedLevelEvents[19] == DungeonEvents.colourRiddle)
        check(
            dungeonSystem.scenarios.single { it.dungeon == Dungeons.hobgoblinStronghold }.fixedLevelEvents[22] ==
                DungeonEvents.hobgoblinChief,
        )
        check(dungeonSystem.scenarios.single { it.dungeon == Dungeons.ogreCave }.fixedLevelEvents[7] == DungeonEvents.tammysPrison)
        check(dungeonSystem.scenarios.single { it.dungeon == Dungeons.erethorTemple }.fixedLevelEvents[12] == DungeonEvents.luteChamber)
        check(dungeonSystem.events.single { it.id == DungeonEvents.hobgoblinPatrol }.disposition == DungeonEventDisposition.Recurring)
        check(dungeonSystem.events.single { it.id == DungeonEvents.erethorPriests }.disposition == DungeonEventDisposition.Recurring)
        check(
            dungeonSystem.events
                .single { it.id == DungeonEvents.luteChamber }
                .ops
                .containsEncounter(Monsters.erethor),
        )
        check(
            dungeonSystem.events
                .single { it.id == DungeonEvents.hobgoblinChief }
                .ops
                .containsEncounter(Monsters.hobgoblinChief),
        )
        check(
            nextDsl.dialogue
                .single { it.npc == Npcs.garth }
                .branches.size == 2,
        )
        check(
            nextDsl.dialogue.single { it.npc == Npcs.garth }.branches.any { branch ->
                branch.whenState == Story.garth.bandits.recovered &&
                    branch.ops.any { op ->
                        op is Op.GrantReward &&
                            op.reward.application.overflow == RewardOverflowPolicy.PromptDropThenPlace
                    }
            },
        )
        check(nextDsl.spells.size == 17)
        val revelationSpell = nextDsl.spells.single { it.id == Spells.Priest.revelation }
        check(
            listOf(ItemPools.dungeonWeaponFinds, ItemPools.dungeonPotionGemFinds, ItemPools.magicItems).all { pool ->
                revelationSpell.ops.anyNested { op -> op is Op.RandomItemOffer && op.offer.pool == pool }
            },
        )
        check(!revelationSpell.ops.anyNested { it is Op.NativeCall })
        check(nextDsl.itemUses.size == 15)
        check(nextDsl.itemUses.count { ItemUseContext.Inventory in it.contexts } == 3)
        check(
            nextDsl.itemUses
                .single { it.id == Items.redPotion }
                .ops
                .any { it is Op.AskMember },
        )
        check(nextDsl.itemUses.single { it.id == Items.redPotion }.consume == ItemConsumePolicy.ScriptControlled)
        check(nextDsl.itemUses.single { it.id == Items.fireWand }.consume == ItemConsumePolicy.BeforeUse)
        check(nextDsl.itemUses.single { it.id == Items.gemOfResurrection }.consume == ItemConsumePolicy.ScriptControlled)
        check(nextDsl.prompts.pauseVerbs.size == 30)
        check(nextDsl.prompts.pauseVerbs.first() == "Smash")
        check(nextDsl.prompts.pauseVerbs.last() == "Decapitate")
        check(nextDsl.prompts.inventoryUse.quaff == inlineText("Quaff what?"))
        check(nextDsl.prompts.equipment.readyArmors == inlineText("Do you want to ready", "some armors before", "leaving?", ""))
        check(nextDsl.prompts.town.closed(Buildings.inn) == inlineText("inn is closed."))
        check(nextDsl.realm.upkeep.foodTickEvery == 5)
        check(nextDsl.realm.upkeep.restedHoursCostPerTurn == 1)
        check(nextDsl.realm.upkeep.fatigue.size == 4)
        check(
            nextDsl.realm.hunt?.let {
                it.successChancePercent == 45 && it.timeCost == 5 && it.foodMin == 10 && it.foodMax == 19
            } == true,
        )
        check(nextDsl.realm.camp.healthGain == 15)
        check(nextDsl.realm.camp.foodCostPerMember == 4)
        check(nextDsl.realm.camp.hoursAdvanced == 8)
        check(nextDsl.realm.camp.hoursRestedReset == 14)
        check(
            nextDsl.realm.camp.temperatureBands ==
                listOf(
                    TemperatureBandRange(TemperatureBand.Low, 6 until 10),
                    TemperatureBandRange(TemperatureBand.VeryLow, 10..15),
                ),
        )
        check(nextDsl.realm.camp.temperatureDamage == mapOf(TemperatureBand.Low to 5, TemperatureBand.VeryLow to 5))
        check(nextDsl.realm.camp.ambushChancePercent == 19)
        check(nextDsl.realm.camp.ambushTier == EncounterTiers.weak)
        check(nextDsl.limits.maxWorldMaps == 1)
        check(nextDsl.limits.maxMapCells == 3120)
        check(nextDsl.limits.screenSize == cells(width = 80, height = 24))
        check(
            nextDsl.visuals.images
                .single { it.id == Images.Static.screen }
                .resource == "ansi/screen.ans",
        )
        check(
            nextDsl.visuals.images
                .single { it.id == Images.Scene.tavern }
                .resource == "tiles/towne-tavern.ans",
        )
        check(nextDsl.visuals.staticScreens.size == 7)
        check(
            nextDsl.visuals.staticScreens
                .single { it.id == StaticScreens.frame }
                .image == Images.Static.screen,
        )
        check(
            nextDsl.visuals.staticScreens
                .single { it.id == StaticScreens.title }
                .image == Images.Static.title,
        )
        check(
            nextDsl.visuals.staticScreens
                .single { it.id == StaticScreens.help }
                .commandHelp,
        )
        check(
            nextDsl.visuals.staticScreens
                .single { it.id == StaticScreens.manual }
                .resource == "adventurers-of-rylween.md",
        )
        check(
            nextDsl.visuals.staticScreens
                .single { it.id == StaticScreens.heroes }
                .runtimeReport == StaticScreenRuntimeReport.HallOfHeroes,
        )
        check(
            nextDsl.worldMaps
                .single { it.id == Maps.wilderness }
                .layers
                .single { it.id == MapLayers.terrain }
                .rows.size == 40,
        )
        check(
            nextDsl.worldMaps
                .single { it.id == Maps.wilderness }
                .layers
                .single { it.id == MapLayers.terrain }
                .rows
                .first()
                .length == 78,
        )
        check(
            !nextDsl.terrain.terrain
                .single { it.id == Terrain.mountain }
                .passable,
        )
        check(
            nextDsl.terrain.terrain
                .single { it.id == Terrain.town }
                .settlement == TerrainSettlement.Town,
        )
        check(nextDsl.terminal.terrain.size == 10)
        check(
            nextDsl.terminal.terrain
                .single { it.terrain == Terrain.town }
                .glyph == "TT",
        )
        check(
            nextDsl.layouts.regions
                .single { it.id == ScreenRegions.textWindow }
                .width == 26,
        )
        check(nextDsl.missions.size == 17)
        check(nextDsl.missions.single { it.id == Missions.erethorEntry }.trigger == Trigger.AtPosition(xy(6, 20)))
        check(nextDsl.missions.single { it.id == Missions.hermitDungeon }.trigger == Trigger.AtLocation(Locations.hermitDungeon))
        check(nextDsl.missions.single { it.id == Missions.thunTemple }.trigger == Trigger.AtLocation(Locations.thunTemple))
        check(nextDsl.missions.single { it.id == Missions.koboldVillage }.trigger == Trigger.AtLocation(Locations.koboldVillage))
        check(nextDsl.missions.single { it.id == Missions.borinMeet }.trigger == Trigger.AtLocation(Locations.borinCamp))
        check(nextDsl.missions.single { it.id == Missions.skullweed }.trigger == Trigger.OnTerrain(Terrain.swamp))
        check(nextDsl.missions.single { it.id == Missions.jazzyFoot }.trigger == Trigger.OnTerrain(Terrain.foothills))
        check(nextDsl.missions.single { it.id == Missions.silverKey }.trigger == Trigger.AtLocation(Locations.silverKeyPit))
        check(nextDsl.missions.single { it.id == Missions.hobgoblinCaravan }.trigger == Trigger.RandomStep(9))
        check(nextRylween.states.size == 4)

        val freshParty =
            nextRylween.states
                .single { it.id == States.freshReplay }
                .parties
                .single()
        val strongParty =
            nextRylween.states
                .single { it.id == States.strongReplay }
                .parties
                .single()
        val nearWinningParty =
            nextRylween.states
                .single { it.id == States.nearWinningReplay }
                .parties
                .single()
        val winningParty =
            nextRylween.states
                .single { it.id == States.winningReplay }
                .parties
                .single()
        val winningSnapshotDsl = renderRuntimeStateSnapshot(nextRylween.states.single { it.id == States.winningReplay })

        check(freshParty.members.size == 2)
        check(specialInventoryItems(nextDsl, freshParty).isEmpty())
        check(!isWinningConfiguration(nextDsl, freshParty))
        check(strongParty.player == "playtest")
        check(strongParty.name == "Playtest")
        check(strongParty.position == Wilderness.xy(12, 9))
        check(
            strongParty.resources ==
                PartyResources(
                    gold = 10000,
                    torches = 40,
                    food = 150,
                    timeLeft = 200,
                    restedHours = 14,
                ),
        )
        check(strongParty.members.map { it.pcClass } == listOf(PcClass.Fighter, PcClass.Ranger, PcClass.Mage, PcClass.Priest))
        check(
            strongParty.members.all { member ->
                listOf(member.health, member.weaponSkill, member.wisdom, member.charisma).all { it >= 70 }
            },
        )
        check(strongParty.members.first().weaponSkill == 95)
        check(strongParty.members[1].weaponSkill >= 70 && strongParty.members[1].charisma >= 70)
        check(strongParty.members[2].wisdom >= 70 && strongParty.members[3].wisdom >= 70)
        check(strongParty.members.map { it.level }.all { it == 1 })
        check(
            strongParty.members.map { it.equipment.weapon } ==
                listOf(
                    Items.magicSword,
                    Items.crossbow,
                    Items.magicDagger,
                    Items.magicAxe,
                ),
        )
        check(
            strongParty.members.map { it.equipment.armor } ==
                listOf(
                    Items.mithrilMail,
                    Items.elvinCloak,
                    Items.leather,
                    Items.chainMail,
                ),
        )
        check(
            setOf(
                Items.redPotion,
                Items.bluePotion,
                Items.blanket,
                Items.tent,
                Items.rope,
                Items.fireWand,
                Items.staffOfHealing,
                Items.magicSword,
                Items.mithrilMail,
                Items.crossbow,
                Items.torch,
                Items.ration,
            ).all { item -> strongParty.inventory.any { slot -> slot.item == item } },
        )
        check(!isWinningConfiguration(nextDsl, strongParty))
        check(specialInventoryItems(nextDsl, nearWinningParty).size == 5)
        check(!isWinningConfiguration(nextDsl, nearWinningParty))
        check(specialInventoryItems(nextDsl, winningParty).size == 5)
        check(isWinningConfiguration(nextDsl, winningParty))
        check(winningSnapshotDsl.contains("runtimeState(stateId(\"winning-replay\"))"))
        check(winningSnapshotDsl.contains("slot(16).empty()"))
        check(winningSnapshotDsl.contains("mark(runtimeFlagState(\"garth.bandits\", \"recovered\"))"))
        validateFlagCatalog(nextDsl.flags)
        validateAuthoredContent(nextDsl)
        validateGameProgress(nextDsl.progress, nextDsl.itemCatalog)
        validateStates(nextRylween.states, nextDsl)
        check((analyzeScriptSources(nextDsl).nativePaths - NativeAllowlist.byPath.keys).isEmpty())
    }

    @Test
    fun kotlinRuntimeKeepsLegacyReplayKnobsOutOfGameCode() {
        val legacyReplayKnobs =
            listOf(
                "RYL_STARTING_GOLD",
                "RYL_STAT_ROLL_MIN",
                "RYL_STAT_ROLL_MAX",
                "RYL_FORCE_ENCOUNTER",
                "RYL_FORCE_ENCOUNTER_NOC",
                "RYL_FORCE_PARTY_LV",
                "RYL_FORCE_DEAD_SLOT",
                "RYL_FORCE_POS",
                "RYL_FORCE_YDARG_GRANTS",
                "RYL_FORCE_YDARG_EASY_ROOMS",
                "RYL_FORCE_PARTY_WP",
                "RYL_FORCE_PARTY_HP_FULL",
                "RYL_REPLAY_RUMOUR",
                "RYL_PARTY_MEMBER_MAX",
                "RYL_FAKE_EPOCH",
                "RYL_FAKE_SEED",
                "RYL_NO_DELAY",
                "RYL_SNAPSHOT",
            )
        val runtimeSourceRoots =
            listOf(
                "src/main/kotlin",
                "ansi-terminal/src/main/kotlin",
                "ansi-runtime/src/main/kotlin",
                "compiled-runtime/src/main/kotlin",
                "src/game/kotlin",
            )
        val offenders =
            runtimeSourceRoots.flatMap { root ->
                File(root)
                    .walkTopDown()
                    .filter { file -> file.isFile && file.extension in setOf("kt", "kts") }
                    .flatMap { file ->
                        val text = file.readText()
                        legacyReplayKnobs
                            .filter { knob -> knob in text }
                            .map { knob -> "${file.invariantSeparatorsPath}: $knob" }
                    }
            }

        check(offenders.isEmpty()) {
            "legacy replay env knobs must stay in the C replay harness or Kotlin tests, not Kotlin game/runtime code: " +
                offenders.joinToString()
        }
    }

    @Test
    fun ansiRuntimeDoesNotDropScriptBridgeForScriptBearingChildAdapters() {
        val source = File("ansi-runtime/src/main/kotlin/rylween/app/RylweenAnsiMain.kt").readText()
        val forbiddenAdapterCalls =
            mapOf(
                "dungeon child adapter" to
                    Regex(
                        """DungeonSessionTerminalAdapter\([\s\S]*?postDungeon = step\.postDungeon,\s*\)""",
                    ),
                "castle child adapter" to
                    Regex("""CastleSessionTerminalAdapter\(port,\s*renderer,\s*savePort\)"""),
                "inventory item-use child adapter" to
                    Regex("""InventoryItemUseTerminalAdapter\(port,\s*renderer\)\.runUntilBlocked"""),
                "priest-spell child adapter" to
                    Regex(
                        """PriestSpellTerminalAdapter\(\s*port,\s*renderer,\s*content = session\.content,\s*worldMap = session\.worldMap,\s*\)""",
                        setOf(RegexOption.DOT_MATCHES_ALL),
                    ),
            )
        val offenders =
            forbiddenAdapterCalls
                .filterValues { pattern -> pattern.containsMatchIn(source) }
                .keys

        check(offenders.isEmpty()) {
            "ANSI compiled-VM mode must pass the selected ScriptBridge into script-bearing child adapters: " +
                offenders.joinToString()
        }
    }

    @Test
    fun reusableTerminalAdaptersDoNotConstructAuthoredScriptFallbacks() {
        val forbidden = listOf("StatefulScriptBridge(", "StatefulScriptServiceRunner(", "serviceBridge ?:", "scriptBridge?.let")
        val offenders =
            listOf(
                "ansi-runtime/src/main/kotlin/rylween/frontend/terminal/TerminalAdapters.kt",
                "ansi-runtime/src/main/kotlin/rylween/frontend/terminal/CastleSessionTerminalAdapter.kt",
            ).flatMap { path ->
                val source = File(path).readText()
                forbidden
                    .filter { token -> token in source }
                    .map { token -> "$path: $token" }
            }

        check(offenders.isEmpty()) {
            "reusable terminal adapters must require explicit ScriptBridge composition instead of authored fallback: " +
                offenders.joinToString()
        }
    }

    @Test
    fun lineTerminalRuntimeUsesCompiledVmOnly() {
        val source = File("ansi-runtime/src/main/kotlin/rylween/app/TerminalMain.kt").readText()
        val requiredTokens =
            listOf(
                "--script-vm",
                "requireCompiledScriptVmMode",
                "compiledVmTerminalScriptBridge",
                "buildTerminalScriptBridge",
                "authored Op execution is a test-only oracle",
            )
        val forbidden = listOf("StatefulScriptBridge", "TerminalScriptVmMode", "expected authored or compiled")
        val missing = requiredTokens.filterNot { token -> token in source }
        val offenders = forbidden.filter { token -> token in source }

        check(missing.isEmpty()) {
            "line-terminal runtime must keep the production script path on CompiledVm; missing: " +
                missing.joinToString()
        }
        check(offenders.isEmpty()) {
            "line-terminal runtime must not expose the authored interpreter as a production mode: " +
                offenders.joinToString()
        }
    }

    @Test
    fun productionLaunchersDoNotComposeAuthoredInterpreter() {
        val forbidden =
            listOf(
                "StatefulScriptBridge",
                "StatefulScriptServiceRunner",
                "ScriptVmMode.Authored",
                "AnsiScriptVmMode.Authored",
                "defaults to authored",
                "authored|compiled",
            )
        val offenders =
            listOf(
                "ansi-runtime/src/main/kotlin/rylween/app/RylweenAnsiMain.kt",
                "ansi-runtime/src/main/kotlin/rylween/app/TerminalMain.kt",
                "build.gradle.kts",
                "ansi-runtime/build.gradle.kts",
            ).flatMap { path ->
                val source = File(path).readText()
                forbidden
                    .filter { token -> token in source }
                    .map { token -> "$path: $token" }
            }

        check(offenders.isEmpty()) {
            "production launchers and CI checks must run compiled VM scripts, not authored-op fallbacks: " +
                offenders.joinToString()
        }
    }

    @Test
    fun bundleAnsiRuntimeIsGeneratedGameFreeAtLauncherBoundary() {
        val ansiSource = File("ansi-runtime/src/main/kotlin/rylween/app/RylweenAnsiMain.kt").readText()
        val rootBuild = File("build.gradle.kts").readText()
        val ansiBuild = File("ansi-runtime/build.gradle.kts").readText()
        val settings = File("settings.gradle.kts").readText()
        val launcher = File("run.sh").readText()
        val requiredBuildTokens =
            listOf(
                "val bundleApp: SourceSet",
                "implementation(project(\":\"))",
                "implementation(project(\":ansi-terminal\"))",
                "rylween/frontend/terminal/**",
                "archiveBaseName.set(\"rylween-bundle-ansi\")",
                "val installBundleAnsi",
                "val checkBundleAnsiBundleBoot",
            )
        val forbiddenAnsiTokens = listOf("import rylween.game", "RylweenCompiledGame")
        val forbiddenBundleBuildTokens = listOf("rootGameSourceSet.output")
        val missing = requiredBuildTokens.filterNot { token -> token in ansiBuild }
        val offenders = forbiddenAnsiTokens.filter { token -> token in ansiSource }
        val bundleSourceSetBlock = ansiBuild.substringAfter("val bundleApp: SourceSet").substringBefore("val bundleAppSourceSet")
        val buildOffenders = forbiddenBundleBuildTokens.filter { token -> token in bundleSourceSetBlock }

        check(missing.isEmpty()) {
            "bundle-only ANSI runtime must keep its generated-game-free source-set/build boundary; missing: " +
                missing.joinToString()
        }
        check(
            """include("ansi-runtime")""" in settings &&
                """include("ansi-terminal")""" in settings &&
                "dependsOn(\":ansi-runtime:check\")" in rootBuild &&
                "dependsOn(\":ansi-terminal:check\")" in rootBuild,
        ) {
            "classic ANSI runtime and shared ANSI primitives must stay in dedicated Gradle subprojects and root check must verify them"
        }
        check(offenders.isEmpty()) {
            "bundle-only ANSI launcher must load content only from the J1 JSON bundle, not generated game classes: " +
                offenders.joinToString()
        }
        check(buildOffenders.isEmpty()) {
            "bundle-only ANSI source set must not compile against generated game output: " +
                buildOffenders.joinToString()
        }
        check("installBundleAnsi" in launcher && "build/compiled/rylween-bundle.json" in launcher) {
            "run.sh must launch the bundle-only ANSI install with the emitted JSON bundle"
        }
        check("installKotlinAnsi" !in launcher) {
            "run.sh must not fall back to the generated-game ANSI install"
        }
    }

    @Test
    fun standaloneCompiledRuntimeDoesNotComposeOldKotlinSessions() {
        val sources =
            File("compiled-runtime/src/main/kotlin")
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "kt" }
                .toList()
        val forbidden =
            listOf(
                "import rylween.engine",
                "import rylween.frontend.terminal",
                "import rylween.runtime.wilderness",
                "import rylween.runtime.town",
                "import rylween.runtime.dungeon",
                "import rylween.runtime.castle",
                "import rylween.runtime.battle",
                "readExternalRuntimeGame",
                "RylweenKotlinEngine",
                "WildernessSession",
                "TownSession",
                "DungeonSession",
                "CastleSession",
                "BattleSession",
                "TerminalAdapter",
            )
        val offenders =
            sources.flatMap { file ->
                val source = file.readText()
                forbidden
                    .filter { token -> token in source }
                    .map { token -> "${file.invariantSeparatorsPath}: $token" }
            }
        val build = File("build.gradle.kts").readText()
        val settings = File("settings.gradle.kts").readText()
        val compiledRuntimeBuild = File("compiled-runtime/build.gradle.kts").readText()

        check(sources.isNotEmpty()) {
            "standalone compiled runtime subproject source must exist"
        }
        check(offenders.isEmpty()) {
            "standalone compiled runtime must not compose old Kotlin session/runtime adapters: " +
                offenders.joinToString()
        }
        check("""include("compiled-runtime")""" in settings) {
            "standalone compiled runtime must stay in a dedicated Gradle subproject"
        }
        check("dependsOn(\":compiled-runtime:check\")" in build) {
            "root check must keep the standalone compiled runtime subproject in verification"
        }
        check("implementation(project(\":ansi-terminal\"))" in compiledRuntimeBuild) {
            "standalone compiled runtime may share ANSI primitives only through :ansi-terminal"
        }
        check("checkCompiledRuntimeBundleBoot" in compiledRuntimeBuild && "checkCompiledRuntimeTownScript" in compiledRuntimeBuild) {
            "standalone compiled runtime subproject must keep its own verification tasks"
        }
        check("checkCompiledRuntimeSaveScript" in compiledRuntimeBuild && "checkCompiledRuntimeLoadScript" in compiledRuntimeBuild) {
            "standalone compiled runtime subproject must verify direct save/load"
        }
        check("checkCompiledRuntimePartyJournalScript" in compiledRuntimeBuild) {
            "standalone compiled runtime subproject must verify direct party/journal views"
        }
        check("checkCompiledRuntimeWildernessActionsScript" in compiledRuntimeBuild) {
            "standalone compiled runtime subproject must verify direct hunt/camp/upkeep actions"
        }
        check(File("compiled-runtime/src/main/kotlin/rylween/compiledruntime/StandaloneCompiledSave.kt").isFile) {
            "standalone compiled runtime must own its direct save/load adapter"
        }
        check(File("compiled-runtime/src/main/kotlin/rylween/compiledruntime/StandalonePartyPages.kt").isFile) {
            "standalone compiled runtime must own direct party and journal pages"
        }
        check(File("compiled-runtime/src/main/kotlin/rylween/compiledruntime/StandaloneWildernessCommands.kt").isFile) {
            "standalone compiled runtime must read wilderness commands from runtime systems"
        }
        check(File("compiled-runtime/src/main/kotlin/rylween/compiledruntime/StandaloneWildernessActions.kt").isFile) {
            "standalone compiled runtime must own direct hunt, camp, and upkeep actions"
        }
        check(File("compiled-runtime/src/main/kotlin/rylween/compiledruntime/StandaloneCompiledBattleRunner.kt").isFile) {
            "standalone compiled runtime must own a direct compiled standard-battle runner"
        }
        check(File("compiled-runtime/src/main/kotlin/rylween/compiledruntime/StandaloneScriptExecutor.kt").isFile) {
            "standalone compiled runtime must own a direct compiled-script executor for scene roots"
        }
        val scriptExecutorSource =
            File("compiled-runtime/src/main/kotlin/rylween/compiledruntime/StandaloneScriptExecutor.kt")
                .readText()
        listOf(
            "CompiledOpcode.If",
            "CompiledOpcode.Loop",
            "CompiledOpcode.ForEachMember",
            "CompiledOpcode.SetFlag",
            "CompiledOpcode.Speak",
            "CompiledOpcode.TellRumour",
            "CompiledOpcode.GrantReward",
            "CompiledOpcode.RandomItemOffer",
            "executeSpeak",
            "tellRumour",
            "applyReward",
            "runRandomItemOffer",
        ).forEach { token ->
            check(token in scriptExecutorSource) {
                "standalone compiled runtime script executor must handle $token directly"
            }
        }
        val battleRunnerSource =
            File("compiled-runtime/src/main/kotlin/rylween/compiledruntime/StandaloneCompiledBattleRunner.kt")
                .readText()
        check("runBattleScript" in battleRunnerSource && "CompiledOpcode.AskMonster" in battleRunnerSource) {
            "standalone compiled battle runner must execute compiled combat item/spell scripts, not just a hard-coded melee loop"
        }
        check(!File("compiled-runtime/src/main/kotlin/rylween/compiledruntime/StandaloneBattleHost.kt").exists()) {
            "standalone compiled runtime must not grow a one-off hard-coded battle host"
        }
        val hardCodedBattleNarration =
            listOf(
                "Thou hast bested",
                "Each survivor gets",
                "party deals",
                "Foes deal",
            )
        val battleNarrationOffenders =
            sources.flatMap { file ->
                val source = file.readText()
                hardCodedBattleNarration
                    .filter { text -> text in source }
                    .map { text -> "${file.invariantSeparatorsPath}: $text" }
            }
        check(battleNarrationOffenders.isEmpty()) {
            "standalone compiled runtime battle text must come from compiled standard-battle content: " +
                battleNarrationOffenders.joinToString()
        }
        val statusTextOffenders =
            sources.flatMap { file ->
                val source = file.readText()
                listOf("not implemented in standalone runtime", "host is not implemented")
                    .filter { text -> text in source }
                    .map { text -> "${file.invariantSeparatorsPath}: $text" }
            }
        check(statusTextOffenders.isEmpty()) {
            "standalone compiled runtime must not leak implementation status text into player screens: " +
                statusTextOffenders.joinToString()
        }
        val runtimeSpec = File("plans/compiled-vm-runtime-spec.md").readText()
        check(
            "compiled standard-battle" in runtimeSpec &&
                "runtimeSystems.combat.standardBattle" in runtimeSpec &&
                "contract and authored text/reward/formula/item/spell rows" in runtimeSpec &&
                "one-off combat loops are not a conforming battle host" in runtimeSpec,
        ) {
            "compiled runtime spec must keep the battle host boundary explicit"
        }
        check(File("run-compiled.sh").readText().contains(":compiled-runtime:installCompiledRuntime")) {
            "run-compiled.sh must launch the standalone compiled runtime"
        }
    }

    @Test
    fun authoredInterpreterApisAreMarkedAsOracleOnly() {
        val requiredMarkers =
            mapOf(
                "src/main/kotlin/rylween/runtime/AuthoredScriptOracleApi.kt" to
                    listOf("annotation class AuthoredScriptOracleApi", "CompiledVm", "parity/oracle test suite"),
                "src/main/kotlin/rylween/engine/ScriptTrace.kt" to
                    listOf("@AuthoredScriptOracleApi\nclass ScriptTraceRunner"),
                "ansi-runtime/src/main/kotlin/rylween/frontend/terminal/TerminalScriptBridge.kt" to
                    listOf(
                        "@AuthoredScriptOracleApi\nclass StatefulScriptBridge",
                        "@AuthoredScriptOracleApi\ndata class StatefulScriptResult",
                        "@AuthoredScriptOracleApi\nclass StatefulScriptServiceRunner",
                        "production launchers compose",
                    ),
                "ansi-runtime/src/main/kotlin/rylween/frontend/terminal/TerminalScriptTraceRunners.kt" to
                    listOf("@file:rylween.runtime.AuthoredScriptOracleApi", "CompiledVmChildRequest"),
            )
        val missing =
            requiredMarkers.flatMap { (path, tokens) ->
                val source = File(path).readText()
                tokens
                    .filterNot { token -> token in source }
                    .map { token -> "$path: $token" }
            }

        check(missing.isEmpty()) {
            "authored interpreter/oracle APIs must stay visibly marked outside the production VM contract: " +
                missing.joinToString()
        }
    }

    @Test
    fun scriptSourceAnalysisOwnsNativePathWalking() {
        val dslSource = File("src/main/kotlin/rylween/dsl/Dsl.kt").readText()
        val validationSource = File("src/main/kotlin/rylween/dsl/DslValidation.kt").readText()
        val analysisSource = File("src/main/kotlin/rylween/dsl/ScriptSourceAnalysis.kt").readText()
        val requiredTokens =
            listOf(
                "remaining authoring/compiler boundary",
                "enum class ScriptSourceRootKind",
                "data class ScriptSourceOp",
                "fun ScriptSourceRoot.visitScriptOps",
                "fun analyzeScriptSources(model: GameModel)",
                "fun nativePathsIn(model: GameModel)",
            )
        val missing = requiredTokens.filterNot { token -> token in analysisSource }

        check("fun nativePathsIn(model: GameModel)" !in dslSource) {
            "native path walking must stay out of Dsl.kt and under the source-analysis boundary"
        }
        check("fun visitOps(" !in validationSource) {
            "DslValidation.kt must use ScriptSourceAnalysis traversal instead of owning recursive op walkers"
        }
        check(missing.isEmpty()) {
            "script source analysis must keep native-path/source-map migration hooks visible; missing: " +
                missing.joinToString()
        }
    }

    @Test
    fun rustMudTargetStaysExperimentalUntilConformanceCloseout() {
        val ledger = File("dsl-improvements.md").readText()
        val plan = File("plans/k1-rust-server-mud-plan.md").readText()
        val build = File("build.gradle.kts").readText()
        val manifest = File("rust/rylween-mud/Cargo.toml").readText()
        val main = File("rust/rylween-mud/src/main.rs").readText()
        val requiredMarkers =
            listOf(
                ledger to "not be called playable until it passes all J3 conformance suites",
                ledger to "Remaining closeout: recompute the canonical `sections` SHA-256",
                plan to "do not mark K1.0 closed until",
                build to "checkRustMudBundleInspect",
                manifest to "publish = false",
                main to "status=experimental-k1.0-skeleton",
            )
        val missing =
            requiredMarkers
                .filterNot { (source, marker) -> marker in source }
                .map { (_, marker) -> marker }

        check(missing.isEmpty()) {
            "Rust MUD target must stay visibly experimental until K1/J3 closeout; missing: " +
                missing.joinToString()
        }
    }
}
