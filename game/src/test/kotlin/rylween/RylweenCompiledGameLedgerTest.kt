package rylween

import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId
import rylween.compiled.readExternalRuntimeSystems
import rylween.compiled.toExternalBundleArtifact
import rylween.dsl.BoolAst
import rylween.dsl.BuildingId
import rylween.dsl.DungeonEventDisposition
import rylween.dsl.DungeonId
import rylween.dsl.InputAliases
import rylween.dsl.IntAst
import rylween.dsl.ItemPoolId
import rylween.dsl.MonsterId
import rylween.dsl.NativeAllowlist
import rylween.dsl.ScriptStatementNode
import rylween.dsl.SpellId
import rylween.dsl.TextBlock
import rylween.dsl.TextPart
import rylween.dsl.TownHandle
import rylween.dsl.ViewCommand
import rylween.dsl.analyzeScriptSources
import rylween.dsl.fallbackText
import rylween.dsl.impl.CharacterCreationStat
import rylween.dsl.impl.CharacterCreationStatRollModel
import rylween.dsl.impl.CharacterPlacementModel
import rylween.dsl.impl.DialogueBranchSelection
import rylween.dsl.impl.EquipmentCommand
import rylween.dsl.impl.Facing
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.ImageAssetKind
import rylween.dsl.impl.ItemConsumePolicy
import rylween.dsl.impl.ItemKind
import rylween.dsl.impl.ItemUseContext
import rylween.dsl.impl.PartyResources
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.QuestionTopic
import rylween.dsl.impl.Race
import rylween.dsl.impl.RewardOverflowPolicy
import rylween.dsl.impl.RumorUsagePolicy
import rylween.dsl.impl.SecretPlayerMatcherModel
import rylween.dsl.impl.ShopHaggleBonusModel
import rylween.dsl.impl.ShopSellHaggleModel
import rylween.dsl.impl.StateKind
import rylween.dsl.impl.StaticScreenRuntimeReport
import rylween.dsl.impl.TemperatureBand
import rylween.dsl.impl.TemperatureBandRange
import rylween.dsl.impl.TerrainSettlement
import rylween.dsl.impl.TownKind
import rylween.dsl.impl.Trigger
import rylween.dsl.impl.Wilderness
import rylween.dsl.impl.cells
import rylween.dsl.impl.gridOffset
import rylween.dsl.impl.itemPool
import rylween.dsl.impl.xy
import rylween.dsl.isWinningConfiguration
import rylween.dsl.keyAction
import rylween.dsl.line
import rylween.dsl.lines
import rylween.dsl.literalText
import rylween.dsl.matches
import rylween.dsl.specialInventoryItems
import rylween.dsl.structuredText
import rylween.dsl.validateAuthoredContent
import rylween.dsl.validateFlagCatalog
import rylween.dsl.validateGameProgress
import rylween.dsl.validateStates
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
import rylween.game.RumorTables
import rylween.game.Rumors
import rylween.game.RylweenCompiledGame
import rylween.game.Scenes
import rylween.game.ScreenRegions
import rylween.game.Spells
import rylween.game.States
import rylween.game.StaticScreens
import rylween.game.Story
import rylween.game.Terrain
import rylween.game.Towns
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
        val externalBundle = nextRylween.toExternalBundleArtifact()
        val externalBundleText = externalBundle.decodeToString()
        val runtimeSystems = externalBundle.readExternalRuntimeSystems()

        check(nextDsl.flags.size == 14)
        check(nextDsl.authoredFlags.size == 14)
        check(nextDsl.itemCatalog.size == 63)
        check(nextDsl.itemCatalog.single { it.id == Items.redPotion }.kind == ItemKind.Potion)
        check(nextDsl.itemPools.size == 7)
        check(
            nextDsl.secrets.players.map { it.matcher } ==
                listOf(
                    SecretPlayerMatcherModel.Named(listOf("Marcus Lagergren", "Marcus")),
                    SecretPlayerMatcherModel.Named(listOf("Karl Skagerberg")),
                ),
        )
        check(
            nextDsl.secrets.players
                .first()
                .alwaysRunFromNonMandatoryEncounters,
        )
        check(!nextDsl.secrets.players[1].alwaysRunFromNonMandatoryEncounters)
        check(nextDsl.progress.milestones.size == 6)
        check(
            nextDsl.progress.win
                ?.required
                ?.size == 5,
        )
        check(leveling.maxLevel == 28)
        check(leveling.xpThresholds[1] == 0 && leveling.xpThresholds[10] == 60_000 && leveling.xpThresholds[28] == 2_800_000)
        check(
            (2..leveling.maxLevel).all { level -> leveling.xpThresholds[level] > leveling.xpThresholds[level - 1] },
        )
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
        check(standardBattle.text.spellAbort.fallbackText() == "(N)one")
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
        check(standardConversation.rumor.usage == RumorUsagePolicy.OncePerConversationThenFallback)
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
            nextDsl.speak.textTables.rumorTables
                .single { it.id == RumorTables.standardTown }
                .entries.size == 33,
        )
        check(
            nextDsl.speak.textTables.rumorTables
                .single()
                .entries
                .count { it.id == null } == 29,
        )
        check(
            nextDsl.speak.textTables.rumorTables
                .single()
                .entries
                .any { it.id == Rumors.crownSeer },
        )
        check(
            nextDsl.speak.textTables.rumorTables
                .single()
                .entries
                .single {
                    it.id == Rumors.banditsQuestGiver
                }.excludedBy == listOf(Story.garth.bandits.recovered, Story.garth.bandits.closed),
        )
        check(
            nextDsl.speak.textTables.rumorTables
                .single()
                .entries
                .single { it.id == Rumors.banditsQuestGiver }
                .text
                .textParts()
                .contains(TextPart.RoleJob(Roles.banditsQuestGiver)),
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
                .matches(InputAliases.ARROW_UP),
        )
        check(nextDsl.characters.size == 39)
        check(nextDsl.characters.single { Roles.crownSeer in it.roles }.placement is CharacterPlacementModel.Randomized)
        check(
            nextDsl.characters.single { it.id == Npcs.marcus }.placement ==
                CharacterPlacementModel.FixedNearTown(Towns.ollk.id, gridOffset(0, -5), Terrain.swamp),
        )
        check(
            listOf(Npcs.alex, Npcs.galdon, Npcs.kosch, Npcs.ufarsin).all { npc ->
                nextDsl.characters.single { it.id == npc }.placement == CharacterPlacementModel.Unplaced
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
                    Monsters.saberToothTiger,
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
        check(castleContent.text.stairsIllustration?.slug == "blitgfx.castle_stairs_guardians")
        check(
            runtimeSystems.castleRows
                .single { castle -> castle.id == "ydarg" }
                .text
                .stairsIllustration == "blitgfx.castle_stairs_guardians",
        )
        check(
            runtimeSystems.presentation.images.any { image ->
                image.id == "blitgfx.castle_stairs_guardians" &&
                    image.kind == "Bitmap" &&
                    image.resource == "bitmap/assets/scene-castle-stairs-guardians.png"
            },
        )
        check(
            runtimeSystems.presentation.images.any { image ->
                image.id == "blitgfx.jaara_gate" &&
                    image.kind == "Bitmap" &&
                    image.resource == "bitmap/assets/scene-jaara-gate.png"
            },
        )
        check(
            runtimeSystems.presentation.images.any { image ->
                image.id == "blitgfx.skullweed_swamp" &&
                    image.kind == "Bitmap" &&
                    image.resource == "bitmap/assets/scene-skullweed-swamp.png"
            },
        )
        check(
            runtimeSystems.commands
                .getValue("castle.ydarg.navigation")
                .take(4)
                .map { command -> command.label } ==
                listOf("Move North", "Move South", "Move West", "Move East"),
        )
        check(
            runtimeSystems.commands
                .getValue(RuntimeCommandGroupId.Dungeon.id)
                .associate { command -> command.action to command.label } ==
                mapOf(
                    RuntimeCommandActionId.Descend.id to "Descend",
                    RuntimeCommandActionId.Ascend.id to "Ascend",
                    RuntimeCommandActionId.Help.id to "View this help",
                    RuntimeCommandActionId.Drop.id to "Leave (Discard) Item",
                    RuntimeCommandActionId.RemoveArmor.id to "Take off armor",
                    RuntimeCommandActionId.RemoveWeapon.id to "Put down weapon",
                    RuntimeCommandActionId.ReadyWeapon.id to "Wield Weapon",
                    RuntimeCommandActionId.ReadyArmor.id to "Ready Armor",
                    RuntimeCommandActionId.Quaff.id to "Quaff Potion",
                    RuntimeCommandActionId.CastSpell.id to "Cast Spell",
                    RuntimeCommandActionId.ViewParty.id to "View Party",
                    RuntimeCommandActionId.Save.id to "Save Game",
                ),
        )
        listOf("castle.ydarg.entry_blocked", "castle.ydarg.entered").forEach { sceneSlug ->
            val scene = nextDsl.scenes.single { it.id.slug == sceneSlug }
            val firstScreen = scene.statementNodes.first { op -> op is ScriptStatementNode.Screen } as ScriptStatementNode.Screen
            check("Jaara" in firstScreen.text.fallbackText()) {
                "$sceneSlug must show Jaara immediately at the castle gate"
            }
            check(scene.statementNodes.hasShowImage("blitgfx.jaara_gate")) {
                "$sceneSlug must show the Jaara gate illustration"
            }
            check(scene.statementNodes.any { op -> op is ScriptStatementNode.WaitInput }) {
                "$sceneSlug must page gate prose instead of blasting through all screens"
            }
        }
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.skullweed }
                .statementNodes
                .hasShowImage("blitgfx.skullweed_swamp"),
        )
        check(
            nextDsl.scenes
                .single { it.id.slug == "castle.ydarg.completed" }
                .statementNodes
                .filterIsInstance<ScriptStatementNode.Encounter>()
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

        fun List<ScriptStatementNode>.anyNested(predicate: (ScriptStatementNode) -> Boolean): Boolean =
            any { op ->
                predicate(op) ||
                    when (op) {
                        is ScriptStatementNode.If -> op.thenStatements.anyNested(predicate) || op.elseStatements.anyNested(predicate)
                        is ScriptStatementNode.Loop -> op.body.anyNested(predicate)
                        is ScriptStatementNode.Switch -> op.cases.any { it.body.anyNested(predicate) }
                        is ScriptStatementNode.Menu ->
                            op.options.any { it.body.anyNested(predicate) } ||
                                op.defaultStatements.anyNested(predicate)
                        is ScriptStatementNode.AskText -> op.matchStatements.anyNested(predicate) || op.missStatements.anyNested(predicate)
                        is ScriptStatementNode.ForEachMember -> op.body.anyNested(predicate)
                        is ScriptStatementNode.ForEachMonster -> op.body.anyNested(predicate)
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
            check(spell.statementNodes.isNotEmpty())
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
        check(checkNotNull(spellsById[Spells.Priest.teleport]).statementNodes.anyNested { it is ScriptStatementNode.TeleportParty })
        val earthquakeSpell = checkNotNull(spellsById[Spells.Priest.earthquake])
        check(earthquakeSpell.statementNodes.anyNested { it is ScriptStatementNode.DiscoverCave })
        check(
            earthquakeSpell.statementNodes.anyNested { op ->
                op is ScriptStatementNode.If &&
                    setOf("in_dungeon", "in_castle").all { flag -> flag in op.cond.runtimeFlags() }
            },
        )

        fun List<ScriptStatementNode>.containsEnterDungeon(
            dungeon: DungeonId,
            levels: Int,
        ): Boolean =
            anyNested { op ->
                op is ScriptStatementNode.EnterDungeon && op.dungeon == dungeon && op.levels == levels
            }

        fun List<ScriptStatementNode>.containsConsumeInventoryPool(
            pool: ItemPoolId,
            quantity: Int,
        ): Boolean =
            anyNested { op ->
                op is ScriptStatementNode.ConsumeInventoryPool && op.pool == pool && op.quantity == IntAst.Lit(quantity)
            }

        fun List<ScriptStatementNode>.containsEncounter(monster: MonsterId): Boolean =
            anyNested { op ->
                op is ScriptStatementNode.Encounter && op.foe == monster
            }

        val churchScene = nextDsl.scenes.single { it.id == Scenes.Towne.church }
        val churchMenu =
            churchScene.statementNodes
                .filterIsInstance<ScriptStatementNode.Loop>()
                .single()
                .body
                .filterIsInstance<ScriptStatementNode.Menu>()
                .single()
        check(churchMenu.options.map { it.key } == listOf('H', 'E', 'R', 'L'))
        val smithScene = nextDsl.scenes.single { it.id == Scenes.Towne.smith }
        val smithShop =
            smithScene.statementNodes
                .filterIsInstance<ScriptStatementNode.ShopService>()
                .single()
                .shop
        val smithBuy = checkNotNull(smithShop.buy)
        val smithSell = checkNotNull(smithShop.sell)
        check(smithShop.building == Buildings.smith)
        check(smithBuy.inventoryApplication.overflow == RewardOverflowPolicy.PromptDropThenPlace)
        check(smithBuy.paymentKeys.decline.primaryChar == 'N')
        check(smithBuy.singlePrice != null)
        check(smithBuy.bought != null)
        check(smithSell.declined != null)
        check(smithSell.sold != null)
        check(
            smithBuy.catalogs
                .single { it.key == 'W' }
                .pages
                .first()
                .navigation
                .next
                ?.primaryChar == 'M',
        )
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
            smithSell.haggle ==
                ShopSellHaggleModel(
                    bonuses =
                        listOf(
                            ShopHaggleBonusModel(rollGreaterThan = 90, addCurrentDivisor = 4),
                            ShopHaggleBonusModel(rollGreaterThan = 75, addCurrentDivisor = 6),
                        ),
                    rejectRollBelow = 76,
                ),
        )
        val innScene = nextDsl.scenes.single { it.id == Scenes.Towne.inn }
        check(
            innScene.statementNodes
                .filterIsInstance<ScriptStatementNode.Loop>()
                .single()
                .body
                .any { it is ScriptStatementNode.Menu },
        )
        val merchantScene = nextDsl.scenes.single { it.id == Scenes.Towne.merchant }
        val merchantShop =
            merchantScene.statementNodes
                .filterIsInstance<ScriptStatementNode.ShopService>()
                .single()
                .shop
        val merchantBuy = checkNotNull(merchantShop.buy)
        val merchantSell = checkNotNull(merchantShop.sell)
        check(merchantShop.building == Buildings.merchant)
        check(merchantBuy.inventoryApplication.overflow == RewardOverflowPolicy.PromptDropThenPlace)
        check(merchantBuy.unitPrice != null)
        check(merchantBuy.singlePrice != null)
        check(merchantBuy.bought != null)
        check(merchantSell.declined != null)
        check(merchantSell.sold != null)
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
        val hutScene = nextDsl.scenes.single { it.id == Scenes.Towne.hut }
        check(
            hutScene.statementNodes
                .filterIsInstance<ScriptStatementNode.Loop>()
                .single()
                .body
                .filterIsInstance<ScriptStatementNode.Menu>()
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
                    Items.holyHandGrenade,
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
                    Items.halberd,
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
                    Items.brooch,
                    Items.crystalBowl,
                    Items.necklace,
                ),
        )
        check(
            tavernScene.statementNodes.any { op ->
                op is ScriptStatementNode.If &&
                    op.thenStatements.any {
                        it is ScriptStatementNode.RandomItemOffer &&
                            it.offer.pool == ItemPools.tavernScruffySpecials &&
                            it.offer.application.overflow == RewardOverflowPolicy.PromptDropThenPlace
                    }
            },
        )
        val tavernMenu =
            tavernScene.statementNodes
                .filterIsInstance<ScriptStatementNode.Loop>()
                .single()
                .body
                .filterIsInstance<ScriptStatementNode.Menu>()
                .single()
        check(tavernMenu.options.map { it.key } == listOf('L', 'S', 'T', 'R', 'O'))
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.borinGift }
                .statementNodes
                .any { it is ScriptStatementNode.GrantReward },
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.jazzyFoot }
                .statementNodes
                .any { it == ScriptStatementNode.AchieveMilestone(Milestones.footOfJazzy) },
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.erethorEntry }
                .statementNodes
                .containsEnterDungeon(Dungeons.erethorTemple, 12),
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.thunTemple }
                .statementNodes
                .containsEnterDungeon(Dungeons.thunTemple, 18),
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.thunTemple }
                .statementNodes
                .containsConsumeInventoryPool(ItemPools.thunAltarGems, 5),
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.silverKey }
                .statementNodes
                .containsEnterDungeon(Dungeons.silverSanctum, 20),
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.borinMeet }
                .statementNodes
                .any { it is ScriptStatementNode.AppendJournal },
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.hobgoblinCaravan }
                .statementNodes
                .any { it is ScriptStatementNode.AppendJournal },
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.koboldVillage }
                .statementNodes
                .containsEncounter(Monsters.orc),
        )
        check(
            nextDsl.scenes
                .single { it.id == Scenes.Missions.ogreCave }
                .statementNodes
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
        check(dungeonSystem.scenarios.single { it.dungeon == Dungeons.reebarTomb }.fixedLevelEvents[19] == DungeonEvents.colorRiddle)
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
                .statementNodes
                .containsEncounter(Monsters.erethor),
        )
        check(
            dungeonSystem.events
                .single { it.id == DungeonEvents.hobgoblinChief }
                .statementNodes
                .containsEncounter(Monsters.hobgoblinChief),
        )
        check(
            nextDsl.dialogue
                .single { it.npc == Npcs.garth }
                .branches.size == 3,
        )
        check(
            nextDsl.dialogue
                .single { it.npc == Npcs.areth }
                .branches
                .flatMap { branch -> branch.statementNodes }
                .anyNested { op ->
                    op is ScriptStatementNode.Screen && TextPart.NpcJob(Npcs.areth) in op.text.textParts()
                },
        )
        check(
            nextDsl.dialogue.single { it.npc == Npcs.garth }.branches.any { branch ->
                branch.whenState == Story.garth.bandits.recovered &&
                    branch.statementNodes.any { op ->
                        op is ScriptStatementNode.GrantReward &&
                            op.reward.application.overflow == RewardOverflowPolicy.PromptDropThenPlace
                    }
            },
        )
        check(nextDsl.spells.size == 17)
        val revelationSpell = nextDsl.spells.single { it.id == Spells.Priest.revelation }
        check(
            listOf(ItemPools.dungeonWeaponFinds, ItemPools.dungeonPotionGemFinds, ItemPools.magicItems).all { pool ->
                revelationSpell.statementNodes.anyNested { op -> op is ScriptStatementNode.RandomItemOffer && op.offer.pool == pool }
            },
        )
        check(nextDsl.itemUses.size == 15)
        check(nextDsl.itemUses.count { ItemUseContext.Inventory in it.contexts } == 3)
        check(
            nextDsl.itemUses
                .single { it.id == Items.redPotion }
                .statementNodes
                .any { it is ScriptStatementNode.AskMember },
        )
        check(nextDsl.itemUses.single { it.id == Items.redPotion }.consume == ItemConsumePolicy.ScriptControlled)
        check(nextDsl.itemUses.single { it.id == Items.fireWand }.consume == ItemConsumePolicy.BeforeUse)
        check(nextDsl.itemUses.single { it.id == Items.gemOfResurrection }.consume == ItemConsumePolicy.ScriptControlled)
        check(nextDsl.prompts.pauseVerbs.size == 30)
        check(nextDsl.prompts.pauseVerbs.first() == "Smash")
        check(nextDsl.prompts.pauseVerbs.last() == "Decapitate")
        check(
            nextDsl.prompts.priest.none
                .fallbackText() == "(N)one",
        )
        check(nextDsl.prompts.priest.noneKey == 'N')
        check(nextDsl.prompts.priest.firstSpellKey == 'A')
        check(
            nextDsl.prompts.priest.wrongClass
                .fallbackText() == "Not a spellcaster!",
        )
        check(nextDsl.prompts.inventoryUse.quaff == literalText("Quaff what?"))
        check(
            nextDsl.prompts.inventoryUse.invalidSlot
                .fallbackText() == "Inventory slot {inventory_slot}\ndoes not exist.",
        )
        check(
            nextDsl.prompts.inventoryUse.notUsable
                .fallbackText() == "Nothing happens\nwith {selected_item_name}.",
        )
        check(
            runtimeSystems.prompts.inventoryUse.invalidSlot.render { kind ->
                if (kind == "inventorySlotName") "A" else null
            } == listOf("Inventory slot A", "does not exist."),
        )
        check(
            runtimeSystems.prompts.inventoryUse.notUsable.render { kind ->
                if (kind == "selectedItemName") "Fire Wand" else null
            } == listOf("Nothing happens", "with Fire Wand."),
        )
        check(
            nextDsl.prompts.party.journalEmpty
                .fallbackText() == "No journal entries.",
        )
        check(
            runtimeSystems.prompts.party.journalEmpty
                .render { null } ==
                listOf("No journal entries."),
        )
        check(
            nextDsl.prompts.party.inventoryHeader
                .fallbackText() == "Inventory:",
        )
        check(
            runtimeSystems.prompts.party.inventoryHeader
                .render { null } ==
                listOf("Inventory:"),
        )
        check(
            nextDsl.prompts.save.done
                .fallbackText() == "Game saved.",
        )
        check(
            runtimeSystems.prompts.save.done
                .render { null } == listOf("Game saved."),
        )
        check(nextDsl.prompts.titleMenu.continueUnavailableReasonLabel == "no save")
        check(runtimeSystems.prompts.titleMenu.continueUnavailableReasonLabel == "no save")
        check(
            nextDsl.prompts.characterCreation.invalidInput
                .fallbackText() == "Invalid input.",
        )
        check(
            runtimeSystems.prompts.characterCreation.invalidInput
                .render { null } == listOf("Invalid input."),
        )
        check(
            nextDsl.prompts.characterCreation.saveConfirmation
                .fallbackText() == "OK",
        )
        check(
            runtimeSystems.prompts.characterCreation.saveConfirmation
                .render { null } == listOf("OK"),
        )
        check("[missing prompt:" !in externalBundleText) {
            "external runtime bundle must not ship placeholder prompt rows"
        }
        listOf("saveConfirmationSuffix", "unavailableSuffix", "closedSuffix").forEach { staleField ->
            check(staleField !in externalBundleText) {
                "external runtime bundle must not ship stale prompt field $staleField"
            }
        }
        check(nextDsl.prompts.equipment.readyArmors == literalText("Do you want to ready", "any armors before", "leaving?", ""))
        check(nextDsl.realm.upkeep.foodTickEvery == 5)
        check(nextDsl.realm.upkeep.restedHoursCostPerTurn == 1)
        check(
            nextDsl.realm.upkeep.starvationDeath
                .fallbackText() == "{upkeep_member_name} starved\nto death!!!",
        )
        check(nextDsl.realm.upkeep.fatigue.size == 4)
        check(
            nextDsl.realm.upkeep.fatigue
                .map { it.statusLabel } == listOf("TIRED", "EXHAUSTED", "WEAK", "FAINT"),
        )
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
        check(
            nextDsl.visuals.images
                .single { it.id == Images.Static.screen && it.kind == ImageAssetKind.Ansi }
                .resource == "ansi/screen.ans",
        )
        check(
            nextDsl.visuals.images
                .single { it.id == Images.Scene.tavern && it.kind == ImageAssetKind.Ansi }
                .resource == "tiles/towne-tavern.ans",
        )
        check(
            nextDsl.visuals.images
                .single { it.id == Images.Scene.tavern && it.kind == ImageAssetKind.Bitmap }
                .resource == "bitmap/assets/scene-tavern.png",
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
        val wildernessMap = nextDsl.worldMaps.single { it.id == Maps.wilderness }
        check(wildernessMap.size == cells(width = 78, height = 40))
        check(wildernessMap.cellCount == 3_120)
        val wildernessTerrainRows =
            wildernessMap
                .layers
                .single { it.id == MapLayers.terrain }
                .rows

        fun wildernessMapCodeAt(point: GridPoint): Char = wildernessTerrainRows[point.y - 1][point.x]
        val hermitPoint = xy(48, 7)
        val ollkPoint = townsById.getValue(Towns.ollk.id).position
        check(wildernessMapCodeAt(hermitPoint) == 't')
        check(wildernessMapCodeAt(ollkPoint) == '0')
        check(hermitPoint.x == ollkPoint.x && ollkPoint.y - hermitPoint.y == 5)
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
        check(nextRylween.states.size == 11)

        val freshParty =
            nextRylween.states
                .single { it.id == States.freshReplay }
                .parties
                .single()
        val acceptanceParty =
            nextRylween.states
                .single { it.id == States.acceptanceReplay }
                .parties
                .single()
        val strongParty =
            nextRylween.states
                .single { it.id == States.strongReplay }
                .parties
                .single()
        val strongEndgameParty =
            nextRylween.states
                .single { it.id == States.strongEndgameReplay }
                .parties
                .single()
        val townMissionParty =
            nextRylween.states
                .single { it.id == States.townMissionReplay }
                .parties
                .single()
        val townSilverKeyParty =
            nextRylween.states
                .single { it.id == States.townSilverKeyReplay }
                .parties
                .single()
        val hobgoblinCaravanParty =
            nextRylween.states
                .single { it.id == States.hobgoblinCaravanReplay }
                .parties
                .single()
        val masterCrownChainParty =
            nextRylween.states
                .single { it.id == States.masterCrownChainReplay }
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
        val winningState = nextRylween.states.single { it.id == States.winningReplay }

        check(freshParty.members.size == 4)
        check(freshParty.members.map { it.name } == listOf("Aragorn", "Legolas", "Gandalf", "Mira"))
        check(freshParty.members.map { it.pcClass } == listOf(PcClass.Fighter, PcClass.Ranger, PcClass.Mage, PcClass.Priest))
        check(freshParty.flags.isEmpty())
        check(freshParty.journal.isEmpty())
        check(freshParty.visitedNpcs.isEmpty())
        check(specialInventoryItems(nextDsl, freshParty).isEmpty())
        check(!isWinningConfiguration(nextDsl, freshParty))
        check(acceptanceParty.player == "playtest")
        check(acceptanceParty.position == Wilderness.xy(12, 9))
        check(acceptanceParty.members.size == 4)
        check(acceptanceParty.members.first().pcClass == PcClass.Priest)
        check(acceptanceParty.members.first().level == 28)
        check(acceptanceParty.members.count { it.dead } == 3)
        check(Story.silverKey.caveLocated in acceptanceParty.flags)
        check(specialInventoryItems(nextDsl, acceptanceParty).isEmpty())
        check(!isWinningConfiguration(nextDsl, acceptanceParty))
        check(strongParty.player == "Strong")
        check(strongParty.name == "Strong")
        check(strongParty.position == Wilderness.xy(12, 9))
        check(strongParty.flags.isEmpty())
        check(strongParty.journal.isEmpty())
        check(strongParty.visitedNpcs.isEmpty())
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
        check(townMissionParty.flags.isEmpty())
        check(townMissionParty.resources.gold == 100000)
        check(townMissionParty.inventory.single { it.slot == 1 }.item == Items.redGem)
        check(Story.silverKey.noteFound in townSilverKeyParty.flags)
        check(townSilverKeyParty.position == Wilderness.xy(12, 9))
        check(Story.silverKey.smithConfessed in hobgoblinCaravanParty.flags)
        check(hobgoblinCaravanParty.position == Wilderness.xy(12, 9))
        check(
            hobgoblinCaravanParty.members
                .single()
                .equipment.weapon == Items.deathBlade,
        )
        check(masterCrownChainParty.flags.isEmpty())
        check(masterCrownChainParty.resources.torches == 80)
        check(masterCrownChainParty.position == Wilderness.xy(12, 9))
        check(
            masterCrownChainParty.members
                .single()
                .equipment.weapon == Items.deathBlade,
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
        check(strongEndgameParty.player == "StrongEndgame")
        check(strongEndgameParty.name == "Strong Endgame")
        check(strongEndgameParty.position == Wilderness.xy(14, 28))
        check(strongEndgameParty.facing == Facing.South)
        check(
            specialInventoryItems(nextDsl, strongEndgameParty).toSet() ==
                setOf(
                    Items.fireshield,
                    Items.bracelets,
                    Items.mastercrown,
                    Items.jazzyfoot,
                    Items.silverkey,
                ),
        )
        check(!isWinningConfiguration(nextDsl, strongEndgameParty))
        check(Milestones.ydargVictory !in strongEndgameParty.milestones)
        check(specialInventoryItems(nextDsl, nearWinningParty).size == 5)
        check(!isWinningConfiguration(nextDsl, nearWinningParty))
        check(specialInventoryItems(nextDsl, winningParty).size == 5)
        check(isWinningConfiguration(nextDsl, winningParty))
        check(winningState.kind == StateKind.RuntimeSnapshot)
        check(winningParty.inventory.single { slot -> slot.slot == 16 }.item == null)
        check(Story.garth.bandits.recovered in winningParty.flags)
        validateFlagCatalog(nextDsl.flags)
        validateAuthoredContent(nextDsl)
        validateGameProgress(nextDsl.progress, nextDsl.itemCatalog)
        validateStates(nextRylween.states, nextDsl)
        val authoredNativePaths = analyzeScriptSources(nextDsl).nativePaths
        check(authoredNativePaths == NativeAllowlist.byPath.keys) {
            "native allowlist must match authored helper use: authored=$authoredNativePaths allowlist=${NativeAllowlist.byPath.keys}"
        }
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
                "RYL_REPLAY_RUMOR",
                "RYL_PARTY_MEMBER_MAX",
                "RYL_FAKE_EPOCH",
                "RYL_FAKE_SEED",
                "RYL_NO_DELAY",
                "RYL_SNAPSHOT",
            )
        val runtimeSourceRoots =
            listOf(
                "core/src/main/kotlin",
                "terminal/src/main/kotlin",
                "runtime/src/main/kotlin",
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
    fun retiredAnsiRuntimeIsAbsentFromActiveGradleProjects() {
        val rootBuild = File("build.gradle.kts").readText()
        val settings = File("settings.gradle.kts").readText()
        val coreBuild = File("core/build.gradle.kts").readText()
        val launcher = File("bin/run.sh").readText()
        val serverLauncher = File("bin/run-server.sh").readText()
        val dockerfile = File("deploy/docker/Dockerfile").readText()
        val gitIgnore = File(".gitignore").readText()
        val dockerIgnore = File(".dockerignore").readText()
        val gameBuild = File("game/build.gradle.kts").readText()
        val gameDsl = File("game/src/main/kotlin/rylween/game/RylweenGame.kt").readText()
        val runtimeMain =
            File("runtime/src/main/kotlin/rylween/runtime/RylweenRuntimeMain.kt").readText()

        check(!File("legacy").exists()) {
            "legacy source tree must not remain in the active repository checkout"
        }
        check(!File("ansi-runtime/build.gradle.kts").exists()) {
            "old interpreted ANSI runtime must not remain as an active top-level project directory"
        }
        check(!File("src/test/kotlin/rylween/ScenarioHarness.kt").exists()) {
            "old interpreted/runtime-oracle scenario harness must not remain in the active test source set"
        }
        check(!File("data").exists()) {
            "game assets must live under game/src/main/resources, not a root data directory"
        }
        check(File("game/src/main/resources/adventurers-of-rylween.md").isFile) {
            "manual document must be packaged as a standard game resource"
        }
        check(File("game/src/main/resources/ansi/title.ans").isFile) {
            "title ANSI must be packaged as a standard game resource"
        }
        check(File("game/src/main/resources/tiles/towne-mainstreet.ans").isFile) {
            "tile ANSI assets must be packaged as standard game resources"
        }
        check("idea" in gameBuild && "name = \"rylween.game\"" in gameBuild) {
            "IDEA import must name :game consistently with sibling modules"
        }
        check("`java-library`" in gameBuild && "api(project(\":core\"))" in gameBuild) {
            "game module must expose core DSL types to editor consumers"
        }
        check(
            File("game/src/main/kotlin/rylween/game/RylweenGame.kt").isFile &&
                File("game/src/main/kotlin/rylween/game/RylweenTestScenarios.kt").isFile &&
                File("game/src/main/kotlin/rylween/game/RylweenCompiledGame.kt").isFile &&
                !File("game/src/main/dsl").exists() &&
                "src/main/dsl" !in gameBuild &&
                "kotlin.plugin.scripting" !in gameBuild &&
                "kotlin.plugin.scripting" !in coreBuild,
        ) {
            "authored game content must live as three regular Kotlin sources under " +
                "game/src/main/kotlin/rylween/game/ (RylweenGame.kt for designer content, " +
                "RylweenTestScenarios.kt for replay fixtures, RylweenCompiledGame.kt as the facade). " +
                "The script template / .dsl.kts paradigm was removed because K2 IDE cannot analyze " +
                "scripts in source roots; see KT-52735 / KTIJ-28064"
        }
        check(".dat" !in gitIgnore && "data/" !in gitIgnore) {
            ".gitignore must not preserve stale root data/dat artifact patterns"
        }
        check(".dat" !in dockerIgnore && "data/" !in dockerIgnore) {
            ".dockerignore must not preserve stale root data/dat artifact patterns"
        }
        check(
            """include("ansi-runtime")""" !in settings &&
                "project(\":ansi-runtime\")" !in rootBuild &&
                "dependsOn(\":ansi-runtime" !in rootBuild,
        ) {
            "legacy ANSI runtime must be outside the active Gradle graph"
        }
        check(
            """include("terminal")""" in settings &&
                """include("runtime")""" in settings &&
                "dependsOn(\":terminal:check\")" in rootBuild &&
                "dependsOn(\":runtime:check\")" in rootBuild,
        ) {
            "active Gradle graph must keep only shared terminal primitives and the runtime as runtime subprojects"
        }
        check(
            ":runtime:installRuntime" in launcher &&
                "runtime/build/install/rylween" in launcher &&
                "RUN_LAUNCHER" in launcher,
        ) {
            "bin/run.sh must launch the runtime"
        }
        check(listOf("installBundleAnsi", "installKotlinAnsi", "ansi-runtime/").none { token -> token in launcher }) {
            "bin/run.sh must not fall back to the archived interpreted ANSI runtime"
        }
        check(
            ":runtime-web:installRuntimeWeb" in dockerfile &&
                "/src/runtime-web/build/install/rylween-web" in dockerfile &&
                "/app/rylween-web/bin/rylween-web --bundle" in dockerfile,
        ) {
            "Dockerfile must run the current Kotlin web runtime install distribution"
        }
        check(listOf("installBundleAnsi", "installKotlinAnsi", "ansi-runtime/").none { token -> token in dockerfile }) {
            "Dockerfile must not wire archived or probe launchers into the playable image"
        }
        check("--title" in launcher && "--no-title" in launcher && "title_seen=false" in launcher) {
            "bin/run.sh must show the title screen by default and require --no-title to skip it"
        }
        check("--name" in launcher && "RYLWEEN_DATA_DIR" in launcher && "world.rsave" in launcher && "strong-endgame-replay" in launcher) {
            "bin/run.sh must expose named local save loading from the default data directory"
        }
        check("--name" in serverLauncher && "RYLWEEN_NAME" in serverLauncher && "bin/run.sh\" --build --name" in serverLauncher) {
            "bin/run-server.sh must seed named local saves through the same bin/run.sh path"
        }
        check("--no-title" in runtimeMain && "skipTitleMenu" in runtimeMain && "!skipTitleMenu" in runtimeMain) {
            "runtime must support an explicit --no-title title-screen skip"
        }
        check(
            "TITLE_COMMAND_GROUP" in runtimeMain &&
                "systems.prompts.titleMenu.header" in runtimeMain &&
                "systems.prompts.titleMenu.continueUnavailableReasonLabel" in runtimeMain &&
                "commands.menuLine(RuntimeCommandActionId.CreateNewParty)" in runtimeMain,
        ) {
            "runtime title menu must render compiled DSL prompts and command labels"
        }
        check(
            "titleMenu" in gameDsl &&
                "createNewParty = inputKey(\"N\", label = \"New Party\")" in gameDsl &&
                "continueSavedGame = inputKey(\"L\", label = \"Load Game\")" in gameDsl &&
                "continueUnavailableReasonLabel = \"no save\"" in gameDsl &&
                "leave = inputKey(\"E\", label = \"Exit\")" in gameDsl &&
                "E(x)it" !in gameDsl,
        ) {
            "game DSL must own the title menu labels with first-letter title commands"
        }
    }

    @Test
    fun authoredDslImportsUseRootFacadeInsteadOfImplWildcard() {
        val facade = File("core/src/main/kotlin/rylween/dsl/AuthoringFacade.kt").readText()
        val generatedCatalogTask = File("build-logic/src/main/kotlin/rylween/gradle/RylweenBuildTasks.kt").readText()
        val authoredSources =
            listOf(
                "game/src/main/kotlin/rylween/game/RylweenGame.kt",
                "game/src/main/kotlin/rylween/game/RylweenTestScenarios.kt",
                "game/src/main/kotlin/rylween/game/RylweenCompiledGame.kt",
            )
        val implWildcardImports =
            authoredSources.filter { path ->
                "import rylween.dsl.impl.*" in File(path).readText()
            }

        check("Explicit authoring facade for game content sources" in facade) {
            "AuthoringFacade.kt must document why the root DSL package re-exports author-facing impl concepts"
        }
        check("do not re-export the whole" in facade && "import rylween.dsl.*" in facade) {
            "AuthoringFacade.kt must explain the import boundary and why the facade stays intentionally narrow"
        }
        check(implWildcardImports.isEmpty()) {
            "authored game content must import the root DSL facade instead of impl wildcard imports: " +
                implWildcardImports.joinToString()
        }
        check("import rylween.dsl.impl.*" !in generatedCatalogTask) {
            "generated game handle catalogs must compile through the root DSL facade, not impl wildcard imports"
        }
    }

    @Test
    fun inputBindingOwnershipStaysSemanticAtRuntime() {
        val scriptKernel = File("core/src/main/kotlin/rylween/dsl/ScriptKernelDsl.kt").readText()
        val commandIds = File("core/src/main/kotlin/rylween/compiled/RuntimeCommandIds.kt").readText()
        val externalBundle = File("core/src/main/kotlin/rylween/compiled/ExternalBundleArtifact.kt").readText()
        val runtimeMain = File("runtime/src/main/kotlin/rylween/runtime/RylweenRuntimeMain.kt").readText()
        val dungeonRuntime = File("runtime/src/main/kotlin/rylween/runtime/DungeonRuntime.kt").readText()
        val castleRuntime = File("runtime/src/main/kotlin/rylween/runtime/CastleRuntime.kt").readText()
        val wildernessActions = File("runtime/src/main/kotlin/rylween/runtime/WildernessActions.kt").readText()
        val wildernessCommands = File("runtime/src/main/kotlin/rylween/runtime/WildernessCommands.kt").readText()
        val gameDsl = File("game/src/main/kotlin/rylween/game/RylweenGame.kt").readText()
        val scriptKernelText = scriptKernel.replace(Regex("\\s+"), " ")

        check(
            "Authored default input profile row" in scriptKernelText &&
                "not a runtime session input" in scriptKernelText &&
                "consume compiled commands by semantic action id" in scriptKernelText &&
                "add a profile layer" in scriptKernelText &&
                "semantic command ids" in scriptKernelText,
        ) {
            "InputBindingModel must document that physical keys are authored defaults, not runtime command identities"
        }
        check("runtime-observable dispatch contract" in commandIds) {
            "RuntimeCommandActionId must remain documented as the semantic runtime dispatch contract"
        }
        check(
            "runtimeCommand(RuntimeCommandActionId.MoveNorth" in externalBundle &&
                "runtimeCommand(RuntimeCommandActionId.CreateNewParty" in externalBundle &&
                "runtimeCommand(RuntimeCommandActionId.Enter, game.wilderness.keys.caveEnter" in externalBundle,
        ) {
            "external bundle generation must attach authored bindings to semantic command action ids"
        }
        check(
            "commands.matches(RuntimeCommandActionId.MoveNorth, command)" in runtimeMain &&
                "matches(RuntimeCommandActionId.CreateNewParty, command)" in runtimeMain &&
                "commands.menuLine(RuntimeCommandActionId.CreateNewParty)" in runtimeMain &&
                "command == '8'" !in runtimeMain &&
                "command == 'N'" !in runtimeMain,
        ) {
            "title and wilderness command loops must use compiled semantic bindings instead of repeated physical keys"
        }
        check(
            "commands.matches(RuntimeCommandActionId.Descend, command)" in dungeonRuntime &&
                "RuntimeCommandActionId.CastSpell" in dungeonRuntime &&
                "command == 'D'" !in dungeonRuntime &&
                "command == 'Z'" !in dungeonRuntime,
        ) {
            "dungeon command loop must dispatch through semantic command ids"
        }
        check(
            "navigation.matches(RuntimeCommandActionId.MoveNorth, command)" in castleRuntime &&
                "treasureCommands.matches(RuntimeCommandActionId.Take, command)" in castleRuntime &&
                "stairsCommands.matches(RuntimeCommandActionId.TeleportOut, command)" in castleRuntime &&
                "command == '8'" !in castleRuntime,
        ) {
            "castle command loop must dispatch through semantic command ids"
        }
        check(
            "caveCommands.matches(RuntimeCommandActionId.Enter, choice)" in wildernessActions &&
                "caveCommands.matches(RuntimeCommandActionId.Leave, choice)" in wildernessActions &&
                "command == 'E'" !in wildernessActions &&
                "command == 'L'" !in wildernessActions,
        ) {
            "wilderness prompt commands must dispatch through compiled semantic cave actions"
        }
        check(
            "fun List<ExternalRuntimeCommand>.matches" in wildernessCommands &&
                "RuntimeCommandActionId" in wildernessCommands &&
                "matchesCommand(command)" in wildernessCommands,
        ) {
            "runtime command binding matching must stay centralized behind compiled command ids"
        }
        check(
            "numberPadMovementWithArrows()" in gameDsl &&
                "createNewParty = inputKey(\"N\", label = \"New Party\")" in gameDsl,
        ) {
            "authored game DSL should still own the current default keyboard profile and labels"
        }
    }

    @Test
    fun bitmapWebRuntimeConsumesCompiledBitmapAssets() {
        val runtimeSystems = RylweenCompiledGame.nextRylween.toExternalBundleArtifact().readExternalRuntimeSystems()
        val bitmapFrameSource = File("runtime-bitmap-web/src/main/kotlin/rylween/bitmapweb/BitmapFrame.kt").readText()
        val bitmapPortsSource = File("runtime-bitmap-web/src/main/kotlin/rylween/bitmapweb/BitmapRuntimePorts.kt").readText()
        val bitmapServerSource =
            File("runtime-bitmap-web/src/main/kotlin/rylween/bitmapweb/RylweenBitmapWebRuntimeMain.kt").readText()
        val bitmapClientSource = File("runtime-bitmap-web/src/main/resources/bitmap-web/client.ts").readText()
        val bitmapBuild = File("runtime-bitmap-web/build.gradle.kts").readText()
        val compiledBitmapAssets = runtimeSystems.presentation.images.filter { image -> image.kind == "Bitmap" }
        val requiredCompiledImages =
            listOf(
                "blitgfx.towne_mainstreet" to "bitmap/assets/scene-thord-mainstreet.png",
                "blitgfx.merchant" to "bitmap/assets/scene-merchant.png",
                "blitgfx.monster_goblinoid" to "bitmap/assets/monster-goblinoid.png",
                "blitgfx.dungeon_chest" to "bitmap/assets/scene-dungeon-chest.png",
                "blitgfx.jaara_gate" to "bitmap/assets/scene-jaara-gate.png",
            )

        requiredCompiledImages.forEach { (id, resource) ->
            val image =
                compiledBitmapAssets.singleOrNull { candidate -> candidate.id == id }
                    ?: error("missing compiled bitmap asset $id")
            check(image.resource == resource) {
                "compiled bitmap asset $id must use $resource, was ${image.resource}"
            }
            check(File("game/src/main/resources/${image.resource}").isFile) {
                "compiled bitmap asset $id must be packaged at ${image.resource}"
            }
        }
        check("enum class BitmapVisualTransition" in bitmapFrameSource && "enum class BitmapImagePlacement" in bitmapFrameSource) {
            "bitmap frame protocol should keep visual transition and placement typed on the Kotlin side"
        }
        check("val imageAssets: List<BitmapImageAsset>" in bitmapFrameSource && "\"imageAssets\"" in bitmapFrameSource) {
            "bitmap frames must expose compiled image asset metadata to the browser client"
        }
        check("imageAssets = bitmapImageAssets" in bitmapPortsSource) {
            "bitmap runtime must attach the compiled bitmap image table to outgoing frames"
        }
        check(
            "validateBitmapImageResources()" in bitmapServerSource &&
                "javaClass.classLoader.getResource(image.resource)" in bitmapServerSource &&
                "image.placement.toBitmapImagePlacement(image.id)" in bitmapServerSource &&
                "image.transition.toBitmapVisualTransition(image.id)" in bitmapServerSource,
        ) {
            "bitmap server must validate compiled resource presence and typed placement/transition rows before serving"
        }
        check(
            "recordCompiledImageAssets(nextFrame.imageAssets || [])" in bitmapClientSource &&
                "const compiledBitmapAssets = new Map<string, BitmapImageAsset>()" in bitmapClientSource &&
                "const compiled = compiledGameImage(kind)" in bitmapClientSource,
        ) {
            "bitmap client must cache compiled image metadata and prefer compiled game images for scene rendering"
        }
        check("if (kind.startsWith(\"blitgfx.\")) return gameImageAsset(kind);" !in bitmapClientSource) {
            "bitmap client must not infer arbitrary blitgfx assets without a compiled bitmap declaration"
        }
        check(
            "alias(libs.plugins.node)" in bitmapBuild &&
                "download.set(true)" in bitmapBuild &&
                "commandLine(\"npm\"" !in bitmapBuild,
        ) {
            "bitmap web TypeScript build must use Gradle-managed Node/npm instead of an OS-installed npm"
        }
    }

    @Test
    fun wildernessDebugSecretCanRenderAnnotatedWorldMap() {
        val runtimeContentDsl =
            File("core/src/main/kotlin/rylween/dsl/impl/RuntimeContentDsl.kt").readText()
        val externalBundle =
            File("core/src/main/kotlin/rylween/compiled/ExternalBundleArtifact.kt").readText()
        val commandIds =
            File("core/src/main/kotlin/rylween/compiled/RuntimeCommandIds.kt").readText()
        val runtimeMain =
            File("runtime/src/main/kotlin/rylween/runtime/RylweenRuntimeMain.kt").readText()
        val runtimeScreen =
            File("runtime/src/main/kotlin/rylween/runtime/RuntimeScreen.kt").readText()

        check(
            "debugShowMap" in runtimeContentDsl &&
                "RuntimeCommandActionId.ShowMap" in externalBundle &&
                "ShowMap(\"showMap\")" in commandIds,
        ) {
            "wilderness debug map key must be compiled into the external command bundle"
        }
        check("debugSecrets && debugCommands.matches(RuntimeCommandActionId.ShowMap, command)" in runtimeMain) {
            "runtime must dispatch the compiled debug map command only through the debug secret path"
        }
        check(
            "showDebugWorldMap" in runtimeScreen &&
                "content.towns.forEach" in runtimeScreen &&
                "TerrainSettlement.Hut" in runtimeScreen &&
                "TerrainSettlement.Castle" in runtimeScreen &&
                "annotated.mark(partyPosition, '@')" in runtimeScreen,
        ) {
            "debug map screen must annotate towns, huts, castles, and the party position"
        }
    }

    @Test
    fun battleTextIsAuthoredInGameDslNotDslImplementation() {
        val gameDsl = File("game/src/main/kotlin/rylween/game/RylweenGame.kt").readText()
        val dslImplementation =
            File("core/src/main/kotlin/rylween/dsl")
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "kt" }
                .joinToString("\n") { file -> file.readText() }
        val battleTextTokens =
            listOf(
                "Blood shall flow!",
                "Coward. You won't get",
                "any EXP for this battle.",
                "The spell fizzles!",
                "HI-YAAAA!",
                " damage pts!!!",
                "(A)ttack",
                "(U)se Item",
            )

        check(battleTextTokens.all { token -> token in gameDsl }) {
            "Rylween battle prose and menu labels must be authored in the game DSL"
        }
        check(battleTextTokens.none { token -> token in dslImplementation }) {
            "Reusable DSL implementation must not contain Rylween-specific battle prose or menu labels"
        }
    }

    @Test
    fun textInputDslStaysGeneric() {
        val gameDsl = File("game/src/main/kotlin/rylween/game/RylweenGame.kt").readText()
        val dslImplementation =
            File("core/src/main/kotlin/rylween/dsl")
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "kt" }
                .joinToString("\n") { file -> file.readText() }

        check("choice.textInput(" in gameDsl && "acceptedText(\"RISH EKKIN\")" in gameDsl) {
            "password/riddle scenes must use generic text input with authored accepted strings"
        }
        check("choice.textAnswer(" !in gameDsl && "textAnswers(" !in gameDsl) {
            "authored game source should use the generic text-input vocabulary"
        }
        check("riddleGate" !in dslImplementation) {
            "Reusable DSL implementation must not expose a game-specific riddle gate primitive"
        }
    }

    @Test
    fun textDisplayDslUsesClearAuthoringVocabulary() {
        val gameDsl = File("game/src/main/kotlin/rylween/game/RylweenGame.kt").readText()
        val scriptDsl = File("core/src/main/kotlin/rylween/dsl/Dsl.kt").readText()
        val textDsl = File("core/src/main/kotlin/rylween/dsl/TextDsl.kt").readText()

        check(
            "fun literalText(" in textDsl &&
                "fun structuredText(" in textDsl &&
                "fun flowText(" in textDsl,
        ) {
            "text data constructors must distinguish literal, structured, and deferred-wrap text"
        }
        check("fun writeText(" in scriptDsl && "fun showTextAndWait(" in scriptDsl) {
            "script display actions must name write-only text and write/wait/clear text explicitly"
        }
        check("fun say(" !in scriptDsl && "fun readScreen(" !in scriptDsl && "fun screen(" !in scriptDsl) {
            "legacy script display aliases must stay retired from ScriptScope"
        }
        check("fun inlineText(" !in textDsl && "fun textLines(" !in textDsl && "fun flow(" !in textDsl) {
            "legacy text constructor aliases must stay retired from the authoring API"
        }
        check("literalText(" in gameDsl && "flowText(" in gameDsl && "showTextAndWait(" in gameDsl) {
            "authored game source must use the preferred text constructor and read-and-dismiss vocabulary"
        }
        check(
            "inlineText(" !in gameDsl &&
                "textLines(" !in gameDsl &&
                "flow(" !in gameDsl &&
                "readScreen(" !in gameDsl,
        ) {
            "authored game source must not keep legacy text constructor or read-screen names"
        }
        check(!Regex("""\bsay\s*[\({]""").containsMatchIn(gameDsl)) {
            "dialogue text should use writeText so speech is content, not a separate display action"
        }
        check(!Regex("\\bwriteText\\(\\s*\"\"\"").containsMatchIn(gameDsl)) {
            "triple-quoted prose should enter writeText through flowText unless it is an explicit structured line block"
        }
        check("screen(\"" !in gameDsl && "screen {" !in gameDsl) {
            "screen() should remain for static/reusable screen declarations, not script text writes"
        }
    }

    @Test
    fun runtimeRenderedTextLiteralsStayClassified() {
        val runtimeSources =
            File("runtime/src/main/kotlin/rylween/runtime")
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "kt" }
                .joinToString("\n") { file -> "// ${file.path}\n${file.readText()}" }

        check(
            "Game prose, prompts, reusable labels, and gameplay failure text should come" in runtimeSources &&
                "runtimeDiagnosticText(" in runtimeSources &&
                "runtimeChromeText(" in runtimeSources &&
                "runtimeGeneratedDetailText(" in runtimeSources,
        ) {
            "runtime-owned rendered strings must be explicitly classified as diagnostics, chrome, or generated detail"
        }
        check(
            !Regex("screen\\.(?:writeText|showMessage)\\(\\s*listOf\\(\\s*\"")
                .containsMatchIn(runtimeSources),
        ) {
            "runtime screen writes must not inline literal listOf(...) prose; use authored prompts or a classification helper"
        }
        check(!Regex("listOf\\(\\s*\"\\[text:").containsMatchIn(runtimeSources)) {
            "compiled text-ref placeholders rendered to users must stay classified as runtime diagnostics"
        }
    }

    @Test
    fun jvmToolchainVersionComesFromVersionCatalog() {
        val catalog = File("gradle/libs.versions.toml").readText()
        val gradleFiles =
            listOf(
                "build.gradle.kts",
                "core/build.gradle.kts",
                "game/build.gradle.kts",
                "tools/build.gradle.kts",
                "terminal/build.gradle.kts",
                "runtime/build.gradle.kts",
            )
        val hardcodedVersion = Regex("""(jvmToolchain|JavaLanguageVersion\.of)\(\s*\d+\s*\)""")
        val offenders =
            gradleFiles.flatMap { path ->
                hardcodedVersion
                    .findAll(File(path).readText())
                    .map { match -> "$path: ${match.value}" }
                    .toList()
            }

        check("jdk = \"25\"" in catalog) {
            "Gradle JVM toolchain version must live in gradle/libs.versions.toml"
        }
        check(offenders.isEmpty()) {
            "Gradle JVM toolchain version must not be hard-coded: ${offenders.joinToString()}"
        }
    }

    @Test
    fun compiledBundleDiagnosticsStayToolOwned() {
        val toolsBuild = File("tools/build.gradle.kts").readText()
        val toolMain = File("tools/src/main/kotlin/rylween/tools/CompiledBundleArtifactMain.kt").readText()
        val compiledCoreSources =
            File("core/src/main/kotlin/rylween/compiled")
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "kt" }
                .joinToString("\n") { file -> file.readText() }

        check("implementation(project(\":runtime\"))" in toolsBuild) {
            "bundle tools must use the existing RuntimeLog sink instead of growing a core logger"
        }
        check("import rylween.runtime.RuntimeLog" in toolMain && "RYLWEEN_TOOL_LOG" in toolMain && "\"--log\"" in toolMain) {
            "bundle tools must expose opt-in diagnostics through configured RuntimeLog sinks"
        }
        check("tool.bundle." in toolMain && "compileSummary()" in toolMain) {
            "bundle tools must emit compile summaries only through the opt-in tool log"
        }
        check("RuntimeLog" !in compiledCoreSources && "logger" !in compiledCoreSources) {
            "core compiled-bundle code must stay quiet and must not grow an independent logger API"
        }
    }

    @Test
    fun runtimeDoesNotComposeOldKotlinSessions() {
        val sources =
            File("runtime/src/main/kotlin")
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
        val runtimeBuild = File("runtime/build.gradle.kts").readText()

        check(sources.isNotEmpty()) {
            "runtime subproject source must exist"
        }
        check(offenders.isEmpty()) {
            "runtime must not compose old Kotlin session/runtime adapters: " +
                offenders.joinToString()
        }
        check("""include("runtime")""" in settings) {
            "runtime must stay in a dedicated Gradle subproject"
        }
        check("dependsOn(\":runtime:check\")" in build) {
            "root check must keep the runtime subproject in verification"
        }
        check("implementation(project(\":terminal\"))" in runtimeBuild) {
            "runtime may share ANSI primitives only through :terminal"
        }
        check("runtimeOnly(project(\":game\"))" in runtimeBuild) {
            "runtime must receive game resources through the normal :game runtime artifact"
        }
        check("resources.srcDir(project(\":game\")" !in runtimeBuild && "resources.srcDir" !in runtimeBuild) {
            "runtime must not graft game resources into its own source set"
        }
        check(
            listOf(
                "checkRuntimeBundleBoot",
                "checkRuntimeNewPartyCreation",
                "checkRuntimeTownPromptScript",
                "checkRuntimeDungeonScript",
                "checkRuntimeCastleScript",
                "checkRuntimeHeroesScreen",
                "checkRuntimeFullPlaythrough",
                "checkRuntimeFullPlaythroughHeroesScreen",
            ).all { taskName -> taskName in runtimeBuild },
        ) {
            "runtime subproject must keep its own verification tasks"
        }
        check("checkRuntimeSaveScript" in runtimeBuild && "checkRuntimeLoadScript" in runtimeBuild) {
            "runtime subproject must verify direct save/load"
        }
        check("checkRuntimePartyJournalScript" in runtimeBuild) {
            "runtime subproject must verify direct party/journal views"
        }
        check("checkRuntimeWildernessActionsScript" in runtimeBuild) {
            "runtime subproject must verify direct hunt/camp/upkeep actions"
        }
        check("replay.set(" !in runtimeBuild && "--replay" !in runtimeBuild) {
            "runtime Gradle smoke tasks must not ship test replay planners through the product CLI"
        }
        val runtimeMainSource =
            File("runtime/src/main/kotlin/rylween/runtime/RylweenRuntimeMain.kt")
                .readText()
        check("--replay" !in runtimeMainSource && "ReplayPlans" !in runtimeMainSource) {
            "runtime product CLI must not expose test-only replay plans"
        }
        check(File("runtime/src/main/kotlin/rylween/runtime/SaveFile.kt").isFile) {
            "runtime must own its direct save/load adapter"
        }
        check("internal object SaveCodec" in File("runtime/src/main/kotlin/rylween/runtime/SaveFile.kt").readText()) {
            "runtime save escaping must stay in the save adapter, not terminal input/color code"
        }
        check(File("runtime/src/main/kotlin/rylween/runtime/PartyPages.kt").isFile) {
            "runtime must own direct party and journal pages"
        }
        check(File("runtime/src/main/kotlin/rylween/runtime/WildernessCommands.kt").isFile) {
            "runtime must read wilderness commands from runtime systems"
        }
        check(File("runtime/src/main/kotlin/rylween/runtime/WildernessActions.kt").isFile) {
            "runtime must own direct hunt, camp, and upkeep actions"
        }
        check(File("runtime/src/main/kotlin/rylween/runtime/BattleRunner.kt").isFile) {
            "runtime must own a direct compiled standard-battle runner"
        }
        check(File("runtime/src/main/kotlin/rylween/runtime/ScriptExecutor.kt").isFile) {
            "runtime must own a direct compiled-script executor for scene roots"
        }
        check(File("runtime/src/main/kotlin/rylween/runtime/DungeonRuntime.kt").isFile) {
            "runtime must own direct dungeon mode execution"
        }
        check(File("runtime/src/main/kotlin/rylween/runtime/CastleRuntime.kt").isFile) {
            "runtime must own generic direct castle mode execution"
        }
        check(File("runtime/src/main/kotlin/rylween/runtime/UtilityActions.kt").isFile) {
            "runtime must own direct inventory/equipment/priest utility commands"
        }
        val replayPlanSource =
            File("runtime/src/test/kotlin/rylween/runtime/ReplayPlan.kt")
                .readText()
        val replayPlanTestSource =
            File("runtime/src/test/kotlin/rylween/runtime/RuntimeReplayPlanTest.kt")
                .readText()
        check("ReplayPlans.build" in replayPlanTestSource && "--script" in replayPlanTestSource) {
            "runtime replay acceptance routes must be generated by tests and fed to the installed launcher as scripts"
        }
        listOf(
            "ReplayComponent",
            "acceptancePowerRoute",
            "townGarthRewardSmoke",
            "townMissionIntroRoute",
            "townSilverKeyChainRoute",
            "silverKeyDiscoveryRoute",
            "hobgoblinCaravanRoute",
            "masterCrownDiscoveryRoute",
            "town.thord_garth_reward_dialogue",
            "town.thord_mission_dialogues",
            "town.wilor_mission_dialogues",
            "town.ollk_silver_key_chain",
            "mission.silver_sanctum_note",
            "mission.hobgoblin_caravan_random_step",
            "mission.kobold_village",
            "mission.nolrim_crypt",
            "routeToTerrain",
            "systems.castleRows.single().entry",
            "runtime replay component",
        ).forEach { token ->
            check(token in replayPlanSource) {
                "runtime must compose acceptance replays from named components: missing $token"
            }
        }
        val scriptExecutorSource =
            File("runtime/src/main/kotlin/rylween/runtime/ScriptExecutor.kt")
                .readText()
        listOf(
            "CompiledOpcode.If",
            "CompiledOpcode.Loop",
            "CompiledOpcode.ForEachMember",
            "CompiledOpcode.SetFlag",
            "CompiledOpcode.Speak",
            "CompiledOpcode.TellRumor",
            "CompiledOpcode.GrantReward",
            "CompiledOpcode.RandomItemOffer",
            "ScriptContinuationState",
            "executeSpeak",
            "writeRumor",
            "applyReward",
            "runRandomItemOffer",
        ).forEach { token ->
            check(token in scriptExecutorSource) {
                "runtime script executor must handle $token directly"
            }
        }
        val dungeonRuntimeSource =
            File("runtime/src/main/kotlin/rylween/runtime/DungeonRuntime.kt")
                .readText()
        listOf("enterDungeon", "runDepthEvent", "CompiledDungeonRandomEvent", "dungeon_level", "postDungeon").forEach { token ->
            check(token in dungeonRuntimeSource) {
                "runtime dungeon runtime must implement $token directly from compiled rows"
            }
        }
        val castleRuntimeSource =
            File("runtime/src/main/kotlin/rylween/runtime/CastleRuntime.kt")
                .readText()
        listOf(
            "ExternalRuntimeCastle",
            "seedRooms",
            "completeCastle",
            "RuntimeCommandGroupId.castleNavigation(castle.id)",
        ).forEach { token ->
            check(token in castleRuntimeSource) {
                "runtime castle runtime must stay generic and runtime-system backed: missing $token"
            }
        }
        val utilitySource =
            File("runtime/src/main/kotlin/rylween/runtime/UtilityActions.kt")
                .readText()
        listOf("readyWeapon", "readyArmor", "quaff", "castPriestSpell", "ItemUseContext.Inventory").forEach { token ->
            check(token in utilitySource) {
                "runtime utility commands must handle $token without old party sessions"
            }
        }
        val externalRuntimeSystemsSource = File("core/src/main/kotlin/rylween/compiled/ExternalBundleArtifact.kt").readText()
        listOf(
            "ExternalRuntimeDungeon",
            "ExternalRuntimeCastle",
            "ExternalRuntimeScore",
            "ExternalRuntimeSecrets",
            "readCastle",
            "readDungeon",
            "readScore",
            "readSecrets",
        ).forEach { token ->
            check(token in externalRuntimeSystemsSource) {
                "runtimeSystems must expose typed dungeon/castle rows for direct runtimes: missing $token"
            }
        }
        val battleRunnerSource =
            File("runtime/src/main/kotlin/rylween/runtime/BattleRunner.kt")
                .readText()
        check("runBattleScript" in battleRunnerSource && "CompiledOpcode.AskMonster" in battleRunnerSource) {
            "battle runner must execute compiled combat item/spell scripts, not just a hard-coded melee loop"
        }
        check(!File("runtime/src/main/kotlin/rylween/runtime/BattleHost.kt").exists()) {
            "runtime must not grow a one-off hard-coded battle host"
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
            "runtime battle text must come from compiled standard-battle content: " +
                battleNarrationOffenders.joinToString()
        }
        val statusTextOffenders =
            sources.flatMap { file ->
                val source = file.readText()
                listOf("not implemented in runtime", "host is not implemented")
                    .filter { text -> text in source }
                    .map { text -> "${file.invariantSeparatorsPath}: $text" }
            }
        check(statusTextOffenders.isEmpty()) {
            "runtime must not leak implementation status text into player screens: " +
                statusTextOffenders.joinToString()
        }
        check(File("bin/run.sh").readText().contains(":runtime:installRuntime")) {
            "bin/run.sh must launch the runtime"
        }
    }

    @Test
    fun rootCleanCoversGeneratedBuildOutputs() {
        val rootBuild = File("build.gradle.kts").readText()

        listOf(
            "subprojects.map { project -> project.layout.buildDirectory }",
            "build-logic/build",
            ".kotlin",
            "build-logic/.kotlin",
            "Kotlin/Gradle recreates these local compiler/session/error caches on demand.",
        ).forEach { token ->
            check(token in rootBuild) {
                "root clean must remove generated output/cache path: missing $token"
            }
        }
    }

    @Test
    fun authoredInterpreterApisStayOutOfActiveSources() {
        val activeRuntimeFiles =
            listOf(
                "core/src/main/kotlin/rylween/runtime/AuthoredScriptOracleApi.kt",
                "core/src/main/kotlin/rylween/engine/ScriptTrace.kt",
                "core/src/main/kotlin/rylween/runtime/dungeon/DungeonCommands.kt",
                "core/src/main/kotlin/rylween/runtime/dungeon/DungeonSession.kt",
                "core/src/main/kotlin/rylween/runtime/town/TownSession.kt",
                "core/src/main/kotlin/rylween/runtime/wilderness/WildernessSession.kt",
                "core/src/main/kotlin/rylween/runtime/battle/BattleSessionSnapshot.kt",
                "core/src/main/kotlin/rylween/runtime/reward/RandomItemOfferSessionSnapshot.kt",
                "core/src/main/kotlin/rylween/runtime/reward/RewardSessionSnapshot.kt",
                "core/src/main/kotlin/rylween/runtime/town/ShopServiceSessionSnapshot.kt",
                "core/src/main/kotlin/rylween/runtime/town/SpeakSessionSnapshot.kt",
                "core/src/main/kotlin/rylween/runtime/vm/CompiledVmContinuationSnapshot.kt",
                "core/src/main/kotlin/rylween/runtime/wilderness/WildernessDungeonHandoff.kt",
            )
        val stillActive = activeRuntimeFiles.filter { path -> File(path).exists() }

        check(stillActive.isEmpty()) {
            "old live reference runtime files must not return to active source: " +
                stillActive.joinToString()
        }
    }

    @Test
    fun productionRuntimesDoNotImportAuthoredSourceIr() {
        val productionRuntimeRoots =
            listOf(
                "core/src/main/kotlin/rylween/runtime",
                "runtime/src/main/kotlin",
                "runtime-web/src/main/kotlin",
                "runtime-bitmap-web/src/main/kotlin",
                "terminal/src/main/kotlin",
                "web-common/src/main/kotlin",
            )
        val bannedTokens =
            listOf(
                "import rylween.dsl.*",
                "import rylween.dsl.AuthoredScript",
                "import rylween.dsl.ScriptBlock",
                "import rylween.dsl.ScriptBlockBuilder",
                "import rylween.dsl.ScriptStatement",
                "import rylween.dsl.ScriptSourceAnalysis",
                "import rylween.dsl.ScriptSourceRoot",
                "import rylween.dsl.ScriptSourceRootId",
                "import rylween.dsl.ScriptSourceStatement",
                "import rylween.dsl.ScriptStatementNode",
                "import rylween.dsl.Op",
                "import rylween.dsl.analyzeScriptSources",
                "import rylween.dsl.scriptSourceRoots",
                "import rylween.dsl.visitScriptStatements",
                "rylween.dsl.AuthoredScript",
                "rylween.dsl.ScriptStatement",
                "rylween.dsl.ScriptSourceStatement",
                "rylween.dsl.ScriptStatementNode",
                "rylween.dsl.Op",
            )
        val violations =
            productionRuntimeRoots
                .flatMap { root -> File(root).walkTopDown().filter { file -> file.isFile && file.extension == "kt" } }
                .flatMap { file ->
                    val text = file.readText()
                    bannedTokens
                        .filter { token -> token in text }
                        .map { token -> "${file.path}: $token" }
                }

        check(violations.isEmpty()) {
            "production runtime code must consume compiled bundle rows, not authored source IR: " +
                violations.joinToString()
        }
    }

    @Test
    fun scriptSourceAnalysisOwnsNativePathWalking() {
        val dslSource = File("core/src/main/kotlin/rylween/dsl/Dsl.kt").readText()
        val kernelSource = File("core/src/main/kotlin/rylween/dsl/ScriptKernelDsl.kt").readText()
        val sourceModel = File("core/src/main/kotlin/rylween/dsl/ScriptSourceModel.kt").readText()
        val validationSource = File("core/src/main/kotlin/rylween/dsl/DslValidation.kt").readText()
        val analysisSource = File("core/src/main/kotlin/rylween/dsl/ScriptSourceAnalysis.kt").readText()
        val compiledBundleSource = File("core/src/main/kotlin/rylween/compiled/CompiledBundle.kt").readText()
        val worldContentSource = File("core/src/main/kotlin/rylween/dsl/impl/WorldContentDsl.kt").readText()
        val speakDslSource = File("core/src/main/kotlin/rylween/dsl/impl/SpeakDsl.kt").readText()
        val requiredTokens =
            listOf(
                "remaining authoring/compiler boundary",
                "enum class ScriptSourceRootKind",
                "data class ScriptSourceStatement",
                "fun ScriptSourceRoot.visitScriptStatements",
                "fun ScriptSourceRoot.anyScriptStatement",
                "fun visitScriptStatements(",
                "fun analyzeScriptSources(model: GameModel)",
            )
        val missing = requiredTokens.filterNot { token -> token in analysisSource }

        check("fun nativePathsIn(model: GameModel)" !in dslSource) {
            "native path walking must stay out of Dsl.kt and under the source-analysis boundary"
        }
        check("fun visitOps(" !in validationSource) {
            "DslValidation.kt must use ScriptSourceAnalysis traversal instead of owning recursive op walkers"
        }
        check("visitScriptOps" !in validationSource && "sourceOp" !in validationSource) {
            "DslValidation.kt diagnostics must use source-statement terminology"
        }
        check(
            "val ops:" !in sourceModel &&
                "fromOps" !in sourceModel &&
                "toOps" !in sourceModel &&
                "recordScript(" !in dslSource &&
                ".ops" !in dslSource &&
                ".ops" !in kernelSource &&
                ".ops" !in analysisSource,
        ) {
            "authored source IR must not expose the old raw ops bridge names"
        }
        val retiredBranchFieldNames =
            listOf("thenOps", "elseOps", "defaultOps", "matchOps", "missOps")
                .filter { token -> token in dslSource || token in kernelSource || token in analysisSource }
        check(retiredBranchFieldNames.isEmpty()) {
            "authored source IR branch fields must use statement terminology: " +
                retiredBranchFieldNames.joinToString()
        }
        val retiredOperationNameUsages =
            listOf(
                "core/src/main/kotlin",
                "game/src/main/kotlin",
                "runtime/src/main/kotlin",
                "runtime-web/src/main/kotlin",
                "runtime-bitmap-web/src/main/kotlin",
                "terminal/src/main/kotlin",
                "web-common/src/main/kotlin",
            ).flatMap { root ->
                File(root)
                    .walkTopDown()
                    .filter { file -> file.isFile && file.extension == "kt" }
                    .filter { file -> Regex("""\bOp\b""").containsMatchIn(file.readText()) }
                    .map { file -> file.path }
            }
        check(retiredOperationNameUsages.isEmpty()) {
            "active Kotlin sources must use ScriptStatementNode instead of the retired Op name: " +
                retiredOperationNameUsages.joinToString()
        }
        check(
            "data class AuthoredScript" in sourceModel &&
                "data class ScriptBlock" in sourceModel &&
                "class ScriptBlockBuilder" in sourceModel &&
                "fun record(statement: ScriptStatement)" in sourceModel &&
                "sealed interface ScriptStatement" in sourceModel &&
                "sealed interface TextView" in sourceModel &&
                "sealed interface ControlFlow" in sourceModel &&
                "sealed interface Input" in sourceModel &&
                "sealed interface StateMutation" in sourceModel &&
                "sealed interface GameplayHandoff" in sourceModel,
        ) {
            "I9 source model must expose authored scripts, blocks, and ownership categories"
        }
        check(
            "sealed interface ScriptStatementNode : ScriptStatement" in kernelSource &&
                "ScriptStatement.TextView" in kernelSource &&
                "ScriptStatement.Input" in kernelSource &&
                "ScriptStatement.ControlFlow" in kernelSource &&
                "ScriptStatement.StateMutation" in kernelSource &&
                "ScriptStatement.GameplayHandoff" in kernelSource,
        ) {
            "concrete ScriptStatementNode nodes must be classified as source statements while I9 migration is in progress"
        }
        check("val script: AuthoredScript" in analysisSource && "get() = script.statementNodes" in analysisSource) {
            "script source roots must carry the new AuthoredScript wrapper while preserving ScriptStatementNode traversal"
        }
        check(
            "private val block: ScriptBlockBuilder" in dslSource &&
                "fun record(statement: ScriptStatement)" in dslSource &&
                "internal fun recordAuthoredScript" in dslSource &&
                "internal fun recordScriptBlock" in dslSource &&
                "recordAuthoredScript(ctx, body).statementNodes" in dslSource,
        ) {
            "ScriptScope must record ScriptStatement blocks while exposing nested ScriptStatementNode captures"
        }
        check(
            "data class SceneModel" in kernelSource &&
                "data class DialogueBranchModel" in kernelSource &&
                "data class SpellModel" in kernelSource &&
                "data class ItemUseModel" in kernelSource &&
                "data class DungeonEventModel" in kernelSource &&
                "val script: AuthoredScript" in kernelSource &&
                "val statementNodes: List<ScriptStatementNode> get() = script.statementNodes" in kernelSource,
        ) {
            "script root models must own AuthoredScript and expose source-statement-node views without legacy accessors"
        }
        check(
            "data class QuestionActionScriptModel" in worldContentSource &&
                "val script: AuthoredScript" in worldContentSource &&
                "recordAuthoredScript(body)" in speakDslSource,
        ) {
            "question-action script roots must also own AuthoredScript"
        }
        check(
            "SceneModel(id, recordAuthoredScript(body))" in dslSource &&
                "SpellModel(id, slot, baseCost, name, recordAuthoredScript(body))" in dslSource &&
                "DialogueBranchModel(state, recordAuthoredScript(body))" in dslSource &&
                "DialogueBranchModel(null, recordAuthoredScript(body))" in dslSource &&
                "ItemUseModel(id, contexts.toSet(), consume, script)" in dslSource,
        ) {
            "root builders must produce AuthoredScript directly instead of raw ScriptStatementNode lists"
        }
        check(
            "compileScript(script: AuthoredScript)" in compiledBundleSource &&
                "compileStatement(statement: ScriptStatement)" in compiledBundleSource &&
                "compileOps(" !in compiledBundleSource &&
                "compileOp(" !in compiledBundleSource &&
                "collectScript(script: AuthoredScript)" in compiledBundleSource &&
                "collectStatements(statements: List<ScriptStatement>)" in compiledBundleSource,
        ) {
            "CompiledBundle.kt must lower authored source scripts through ScriptStatement terminology"
        }
        check(missing.isEmpty()) {
            "script source analysis must keep native-path/source-map migration hooks visible; missing: " +
                missing.joinToString()
        }
    }

    @Test
    fun scriptAndRuntimeLoopOwnershipStaysExplicit() {
        val dslSource = File("core/src/main/kotlin/rylween/dsl/Dsl.kt").readText()
        val kernelSource = File("core/src/main/kotlin/rylween/dsl/ScriptKernelDsl.kt").readText()
        val sourceModel = File("core/src/main/kotlin/rylween/dsl/ScriptSourceModel.kt").readText()
        val compiledBundleSource = File("core/src/main/kotlin/rylween/compiled/CompiledBundle.kt").readText()
        val scriptExecutorSource = File("runtime/src/main/kotlin/rylween/runtime/ScriptExecutor.kt").readText()
        val gameSource = File("game/src/main/kotlin/rylween/game/RylweenGame.kt").readText()
        val planSource = File("plans/dsl-improvements.md").readText()
        val runtimeLoopDocs =
            mapOf(
                "runtime/src/main/kotlin/rylween/runtime/RylweenRuntimeMain.kt" to "runtime mode loops",
                "runtime/src/main/kotlin/rylween/runtime/DungeonRuntime.kt" to "runtime mode loop",
                "runtime/src/main/kotlin/rylween/runtime/CastleRuntime.kt" to "runtime mode loop",
                "runtime/src/main/kotlin/rylween/runtime/BattleRunner.kt" to "runtime service loop",
                "runtime/src/main/kotlin/rylween/runtime/ShopService.kt" to "runtime service loops",
            )

        check("Script-control loops recorded here" in sourceModel && "mode/service loops" in sourceModel) {
            "ScriptSourceModel must document that authored script loops are not runtime mode/service loops"
        }
        check(
            "fun loop(body: ScriptScope.() -> Unit)" in dslSource &&
                "Use this for local scripted menus or retry prompts." in dslSource &&
                "Do not use it to model" in dslSource &&
                "engine cadence loops" in dslSource &&
                "fun forEachMonster(" in dslSource &&
                "fun forEachMember(" in dslSource,
        ) {
            "ScriptScope loop DSL must describe local scripted flow, not runtime loop ownership"
        }
        check(
            "Authored script-control loop." in kernelSource &&
                "The compiler lowers this node to `CompiledOpcode.Loop`" in kernelSource &&
                "not the standard battle loop" in kernelSource,
        ) {
            "ScriptStatementNode loop variants must keep source-loop ownership documented"
        }
        check(
            "is ScriptStatementNode.Loop -> instruction(CompiledOpcode.Loop" in compiledBundleSource &&
                "CompiledOpcode.Loop -> executeLoop" in scriptExecutorSource,
        ) {
            "authored script-control loops must lower to and execute through the compiled script VM path"
        }
        check(!Regex("""\b(?:for|while)\s*\(""").containsMatchIn(gameSource)) {
            "authored game source must use DSL script iteration constructs instead of raw Kotlin loops"
        }
        val missingRuntimeDocs =
            runtimeLoopDocs
                .filterNot { (path, requiredText) -> requiredText in File(path).readText() }
                .keys
        check(missingRuntimeDocs.isEmpty()) {
            "runtime loop owners must explicitly document native loop ownership: " +
                missingRuntimeDocs.joinToString()
        }
        check("- [x] I9g. Document and enforce loop ownership." in planSource) {
            "plans/dsl-improvements.md must record I9g loop-ownership closeout"
        }
    }

    @Test
    fun authoredGameScriptsUseDslTemporaryBlocks() {
        val dslSource = File("core/src/main/kotlin/rylween/dsl/Dsl.kt").readText()
        val expressionSource = File("core/src/main/kotlin/rylween/dsl/ExpressionDsl.kt").readText()
        val gameSource = File("game/src/main/kotlin/rylween/game/RylweenGame.kt").readText()
        val planSource = File("plans/dsl-improvements.md").readText()
        val rawScriptVals =
            gameSource
                .lineSequence()
                .withIndex()
                .filter { (_, line) -> Regex("""^\s{20,}val\s+\w+\s*=""").containsMatchIn(line) }
                .map { (index, line) -> "${index + 1}: ${line.trim()}" }
                .toList()

        check(
            "body: ScriptScope.(LocalIntRef) -> Unit" in dslSource &&
                "fun withCurrentCaster(" in dslSource &&
                "fun withCurrentCasterCost(" in dslSource &&
                "script.body(monsterAlive(prompt))" in dslSource &&
                "script.body(wildernessDirection(prompt))" in dslSource &&
                "script.body(inventorySlot(prompt, firstKey, slots, filter))" in dslSource,
        ) {
            "ScriptScope/AskScope must expose block helpers for common script temporaries"
        }
        check(
            "script.body(member(index))" in expressionSource &&
                "script.body(firstAliveMonster())" in expressionSource &&
                "script.body(randomAliveMember())" in expressionSource,
        ) {
            "expression helper refs must expose block forms for authored game scripts"
        }
        check(rawScriptVals.isEmpty()) {
            "authored game script bodies must use DSL temporary blocks instead of raw Kotlin val declarations: " +
                rawScriptVals.joinToString()
        }
        check("- [x] I9h. Remove raw Kotlin locals from authored game scripts." in planSource) {
            "plans/dsl-improvements.md must record I9h temporary-block closeout"
        }
    }

    @Test
    fun filteredPartyMemberSelectorsAreTypedAndUsedForNarration() {
        val kernelSource = File("core/src/main/kotlin/rylween/dsl/ScriptKernelDsl.kt").readText()
        val expressionSource = File("core/src/main/kotlin/rylween/dsl/ExpressionDsl.kt").readText()
        val compiledBundleSource = File("core/src/main/kotlin/rylween/compiled/CompiledBundle.kt").readText()
        val scriptExecutorSource = File("runtime/src/main/kotlin/rylween/runtime/ScriptExecutor.kt").readText()
        val selectorTestSource = File("runtime/src/test/kotlin/rylween/runtime/PartyMemberSelectorsTest.kt").readText()
        val gameSource = File("game/src/main/kotlin/rylween/game/RylweenGame.kt").readText()
        val planSource = File("plans/dsl-improvements.md").readText()
        val hardcodedNarrationMembers =
            Regex("""party\.member\(\d+\)""")
                .findAll(gameSource)
                .map { match -> match.value }
                .toSet()

        check("enum class PartyMemberSelection" in kernelSource && "data class PartyMemberQuery(" in kernelSource) {
            "non-interactive party member selectors must compile as typed source queries"
        }
        check(
            "fun firstMember(" in expressionSource &&
                "fun randomMember(" in expressionSource &&
                "IntAst.PartyMemberQuery" in expressionSource,
        ) {
            "PartyRef must expose filtered first/random member selector helpers"
        }
        check("CompiledIntExpr.PartyMemberQuery" in compiledBundleSource && "partyMemberQuery(" in scriptExecutorSource) {
            "filtered party member selectors must lower into compiled runtime execution"
        }
        check("party.randomMember(MemberChoiceFilter.AliveOnly)" in gameSource && hardcodedNarrationMembers.isEmpty()) {
            "authored narration must use filtered selectors instead of fixed member numbers: $hardcodedNarrationMembers"
        }
        check(
            "memberChoicesUseOneBasedPartyNumbersAndFiltersDeadMembers" in selectorTestSource &&
                "partyMemberSelectionReturnsZeroWhenNoMemberMatches" in selectorTestSource,
        ) {
            "selector tests must cover one-based choices and empty-match behavior"
        }
        check("- [x] I14. Add filtered non-interactive party-member selectors." in planSource) {
            "plans/dsl-improvements.md must record I14 selector closeout"
        }
    }

    @Test
    fun rewardVocabularySeparatesPlayerGainsCostsAndDebugGrants() {
        val scriptDsl = File("core/src/main/kotlin/rylween/dsl/Dsl.kt").readText()
        val gameSource = File("game/src/main/kotlin/rylween/game/RylweenGame.kt").readText()
        val guide = File("docs/dsl-authoring.md").readText()
        val planSource = File("plans/dsl-improvements.md").readText()
        val directPlayerGains =
            Regex("""party\.(gold|torches|food)\s*\+=""")
                .findAll(gameSource)
                .map { match -> match.value }
                .toSet()

        check("fun gainGold(" in scriptDsl && "reward { gold(amount) }" in scriptDsl) {
            "player-facing gold gains must compile through the reward vocabulary"
        }
        check("fun gainTorches(" in scriptDsl && "fun gainFood(" in scriptDsl) {
            "simple resource gains should have named helpers instead of authored direct scalar increments"
        }
        check("Debug/sysop-only gold grant" in scriptDsl && "fun addGold(" in scriptDsl) {
            "secret addGold must be documented as a debug/sysop action, not normal reward authoring"
        }
        check(directPlayerGains.isEmpty()) {
            "authored game player-facing resource gains should use gain helpers or reward blocks, not direct increments: " +
                directPlayerGains.joinToString()
        }
        check("gainGold(amount)" in gameSource && "gainTorches(amount)" in gameSource && "gainGold(goldAmount)" in gameSource) {
            "current dungeon gold/torch finds must use the named gain vocabulary"
        }
        check("### Rewards And Resources" in guide && "Secret debug/sysop grants" in guide) {
            "DSL guide must document reward/resource/debug ownership"
        }
        check("- [x] I15. Unify reward and direct resource mutation vocabulary." in planSource) {
            "plans/dsl-improvements.md must record I15 closeout"
        }
        check("Resolved pairs:" in planSource && "I14" in planSource && "I15" in planSource) {
            "I19 duplicate-mechanism register must mark selector and reward/resource pairs resolved"
        }
    }

    @Test
    fun currentGameDslUsesAuthorFirstTopLevelOrder() {
        val gameSource = File("game/src/main/kotlin/rylween/game/RylweenGame.kt").readText()
        val planSource = File("plans/dsl-improvements.md").readText()

        fun topLevelIndex(token: String): Int =
            Regex("""(?m)^ {12}${Regex.escape(token)}""")
                .find(gameSource)
                ?.range
                ?.first
                ?: -1

        val orderedTopLevelTokens =
            listOf(
                "metadata {",
                "limits {",
                "flags {",
                "secrets {",
                "visuals {",
                "layouts {",
                "terminal {",
                "terrain {",
                "worldMap(Maps.wilderness) {",
                "realm {",
                "castle(Castles.ydarg) {",
                "prompts {",
                "pacing {",
                "items {",
                "gameProgress {",
                "leveling {",
                "characterCreation {",
                "formulas {",
                "rewards {",
                "combat {",
                "wilderness {",
                "dungeons {",
                "speak {",
                "towns {",
                "townSystem {",
                "characters {",
                "monsters {",
                "missions {",
                "scene(sceneId(\"castle.ydarg.already_completed\")) {",
                "scene(Scenes.Towne.church) {",
                "scene(Scenes.Missions.erethorEntry) {",
                "dialogue {",
            )

        orderedTopLevelTokens
            .map { token ->
                token to
                    topLevelIndex(token).also { index ->
                        check(index >= 0) { "current game DSL is missing top-level token: $token" }
                    }
            }.zipWithNext()
            .forEach { (left, right) ->
                check(left.second < right.second) {
                    "current game DSL top-level order is wrong: ${left.first.trim()} should precede ${right.first.trim()}"
                }
            }
        check(
            "global identity/state: metadata, limits, flags, secrets" in gameSource &&
                "behavior: missions, castle scenes, town scenes, mission scenes, dialogue" in gameSource,
        ) {
            "RylweenGame.kt must document the author-first top-level hierarchy"
        }
        check("- [x] I16. Reorder the current game DSL into an author-first hierarchy." in planSource) {
            "plans/dsl-improvements.md must record I16 closeout"
        }
    }

    @Test
    fun repeatedTextBuilderCallShapesUseSharedHelpersWithoutChangingRows() {
        val scriptDsl = File("core/src/main/kotlin/rylween/dsl/Dsl.kt").readText()
        val shopDsl = File("core/src/main/kotlin/rylween/dsl/impl/ShopDsl.kt").readText()
        val planSource = File("plans/dsl-improvements.md").readText()
        val directRandomOfferBuilders =
            Regex("""RandomItemOfferTextBuilder\(\)\.apply\(body\)\.build\(\)""")
                .findAll(scriptDsl)
                .count()
        val directShopTextBuilders =
            Regex("""ShopTextBuilder\(\)\.apply\(body\)\.build\(\)""")
                .findAll(shopDsl)
                .count()
        val smithShop =
            RylweenCompiledGame.nextDsl.scenes
                .single { it.id == Scenes.Towne.smith }
                .statementNodes
                .filterIsInstance<ScriptStatementNode.ShopService>()
                .single()
                .shop
        val merchantShop =
            RylweenCompiledGame.nextDsl.scenes
                .single { it.id == Scenes.Towne.merchant }
                .statementNodes
                .filterIsInstance<ScriptStatementNode.ShopService>()
                .single()
                .shop
        val tavernRandomOffer =
            RylweenCompiledGame.nextDsl.scenes
                .single { it.id == Scenes.Towne.tavern }
                .statementNodes
                .filterIsInstance<ScriptStatementNode.If>()
                .flatMap { op -> op.thenStatements }
                .filterIsInstance<ScriptStatementNode.RandomItemOffer>()
                .single { op -> op.offer.pool == ItemPools.tavernScruffySpecials }

        check("private fun randomItemOfferText(" in scriptDsl && directRandomOfferBuilders == 1) {
            "random item offer DSL should centralize custom text-builder construction"
        }
        check("fun shopText(" in shopDsl && directShopTextBuilders == 1) {
            "shop DSL should centralize custom shop text-builder construction"
        }
        check(checkNotNull(smithShop.buy).bought?.fallbackText() == "Bought {selected_item_name}.")
        check(checkNotNull(smithShop.buy).singlePrice?.fallbackText() == "Price: {price}\n")
        check(smithShop.sell?.offer?.fallbackText() == "Ok! Give ya {price}!\n")
        check(checkNotNull(merchantShop.buy).unitPrice?.fallbackText() == "{price} gold each, sir.")
        check(merchantShop.sell?.offer?.fallbackText() == "- Ok. Give ya {price}!\n")
        check("{item_name}" in tavernRandomOffer.offer.offer.fallbackText())
        check("{item_price}" in tavernRandomOffer.offer.offer.fallbackText())
        check("- [x] I17. Audit repeated painful call shapes and collapse only real" in planSource) {
            "plans/dsl-improvements.md must record I17 closeout"
        }
    }

    @Test
    fun nearDuplicateMechanismRegisterIsClosedWithRetirementGuards() {
        val planSource = File("plans/dsl-improvements.md").readText()
        val guide = File("docs/dsl-authoring.md").readText()
        val scriptDsl = File("core/src/main/kotlin/rylween/dsl/Dsl.kt").readText()
        val textDsl = File("core/src/main/kotlin/rylween/dsl/TextDsl.kt").readText()

        check("- [x] I19. Keep a near-duplicate mechanism register and retire split shapes" in planSource) {
            "plans/dsl-improvements.md must record I19 closeout"
        }
        check(
            "Retired split shapes:" in planSource &&
                "script `screen`/`readScreen`/" in planSource &&
                "`say` aliases" in planSource &&
                "`inlineText`/`textLines`/`flow` aliases" in planSource,
        ) {
            "I19 must document the concrete duplicate mechanisms retired"
        }
        check("Historical aliases" !in guide && "compatibility bridges for old scripts" !in guide) {
            "DSL guide must not advertise retired script aliases"
        }
        check("Deprecated(" !in scriptDsl && "Deprecated(" !in textDsl) {
            "retired text/display aliases should not remain as deprecated bridges"
        }
    }

    @Test
    fun dslAuthoringGuideDocumentsCurrentSourceModelAndAudit() {
        val guide = File("docs/dsl-authoring.md").readText()
        val rootReadme = File("README.md").readText()
        val gameReadme = File("game/README.md").readText()
        val coreReadme = File("core/README.md").readText()
        val planSource = File("plans/dsl-improvements.md").readText()
        val compiledRuntimeSpec = File("plans/compiled-vm-runtime-spec.md").readText()
        val currentSourcePath = "game/src/main/kotlin/rylween/game/RylweenGame.kt"

        listOf(
            "Mental Model",
            "Syntax Rules",
            "Adding Content",
            "Where A Change Belongs",
            "Extending The DSL",
            "I10 Design Audit Notes",
            "scene(Scenes.Missions.bandits)",
            "shopService(Buildings.smith)",
            "prompts {",
            "worldMap(Maps.wilderness)",
            "dungeons {",
            "state(States.strong)",
            "runtimeSystems",
            "source-script IR",
            currentSourcePath,
        ).forEach { token ->
            check(token in guide) {
                "DSL authoring guide must document $token"
            }
        }
        check("docs/dsl-authoring.md" in rootReadme) {
            "root README must link the DSL authoring guide"
        }
        check("../docs/dsl-authoring.md" in gameReadme && "../docs/dsl-authoring.md" in coreReadme) {
            "module READMEs must link the DSL authoring guide"
        }
        check("- [x] I10. Author-facing DSL guide and design consistency audit." in planSource) {
            "plans/dsl-improvements.md must record I10 closeout"
        }
        check(
            currentSourcePath in planSource &&
                "authored Kotlin source-script IR" in compiledRuntimeSpec &&
                "authored `Op`" !in compiledRuntimeSpec,
        ) {
            "current-facing docs must describe RylweenGame.kt and source-script IR, not the retired DSL script/op model"
        }
    }
}

private fun TextBlock.textParts(): List<TextPart> =
    when (this) {
        is TextBlock.Ref -> emptyList()
        is TextBlock.Inline -> emptyList()
        is TextBlock.Structured -> lines.flatMap { line -> line.parts }
        is TextBlock.Flow -> paragraphs.flatMap { line -> line.parts }
    }

private fun TextBlock.fallbackText(): String =
    when (this) {
        is TextBlock.Ref -> id.id
        is TextBlock.Inline -> lines.joinToString("\n")
        is TextBlock.Structured -> lines.joinToString("\n") { line -> line.parts.joinToString("", transform = TextPart::fallbackText) }
        is TextBlock.Flow ->
            paragraphs.joinToString("\n\n") { line ->
                line.parts.joinToString("", transform = TextPart::fallbackText)
            }
    }

private fun List<ScriptStatementNode>.hasShowImage(slug: String): Boolean =
    any { op ->
        val command = (op as? ScriptStatementNode.View)?.command as? ViewCommand.ShowImage
        command?.image?.slug == slug
    }

private fun BoolAst.runtimeFlags(): Set<String> =
    when (this) {
        is BoolAst.And -> left.runtimeFlags() + right.runtimeFlags()
        is BoolAst.Or -> left.runtimeFlags() + right.runtimeFlags()
        is BoolAst.Not -> expr.runtimeFlags()
        is BoolAst.RuntimeFlag -> setOf(name)
        is BoolAst.BattleFlag,
        is BoolAst.Cmp,
        is BoolAst.FlagIs,
        is BoolAst.MemberFlag,
        is BoolAst.MilestoneAchieved,
        is BoolAst.MonsterFlag,
        -> emptySet()
    }
