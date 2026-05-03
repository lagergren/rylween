package rylween

import rylween.dsl.*
import rylween.engine.*
import rylween.frontend.terminal.*
import rylween.frontend.terminal.ydarg.*
import rylween.game.RylweenCompiledGame
import rylween.game.*
import rylween.runtime.*
import rylween.runtime.ydarg.*
import rylween.save.*

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun compiledItemRecords(): List<ItemRecordModel> =
    RylweenCompiledGame.nextDsl.itemCatalog

private fun compiledRealmContent(): RealmContentModel =
    RylweenCompiledGame.nextDsl.realm

private fun List<Op>.anyNested(predicate: (Op) -> Boolean): Boolean =
    any { op ->
        predicate(op) || when (op) {
            is Op.If -> op.thenOps.anyNested(predicate) || op.elseOps.anyNested(predicate)
            is Op.Loop -> op.body.anyNested(predicate)
            is Op.Switch -> op.cases.any { case -> case.body.anyNested(predicate) }
            is Op.Menu -> op.options.any { option -> option.body.anyNested(predicate) } ||
                op.defaultOps.anyNested(predicate)
            is Op.AskText -> op.matchOps.anyNested(predicate) || op.missOps.anyNested(predicate)
            is Op.ForEachMember -> op.body.anyNested(predicate)
            is Op.ForEachMonster -> op.body.anyNested(predicate)
            else -> false
        }
    }

private fun ItemCatalogBuilder.silverKeyTestItem() {
    special(Items.silverkey, "Silver Key")
}

private fun ItemCatalogBuilder.shopTestItems() {
    gear(Items.torch, "Torch", cost = 1)
    gear(Items.blanket, "Blanket", cost = 30)
}

private fun ItemCatalogBuilder.scruffyOfferTestItems() {
    weapon(Items.magicDagger, "Magic Dagger", cost = 1800, damage = 24)
    weapon(Items.magicAxe, "Magic Axe", cost = 4000, damage = 38)
    armor(Items.admtMail, "Admt. Mail", cost = 9400, armorClass = 16)
    potion(Items.amberPotion, "Amber Potion", cost = 500)
    treasure(Items.greenGem, "Green Gem", cost = 1900)
    magicItem(Items.frostWand, "Frost Wand", cost = 1000)
    magicItem(Items.fireWand, "Fire Wand", cost = 3000)
    magicItem(Items.gemOfConfusion, "Gem of Confusion", cost = 1560)
    magicItem(Items.gemOfBlindness, "Gem of Blindness", cost = 4900)
    magicItem(Items.drumsOfDeath, "Drums of Death", cost = 4500)
    magicItem(Items.staffOfHealing, "Staff of Healing", cost = 670)
    magicItem(Items.blessingScroll, "Blessing Scroll", cost = 2000)
    magicItem(Items.staffOfCuring, "Staff of Curing", cost = 4343)
    magicItem(Items.gemOfResurrection, "Gem of Resurrection", cost = 9000)
    magicItem(Items.kaelsAxe, "Kael's Axe", cost = 14000)
    magicItem(Items.holyHandgrenade, "Holy Handgrenade", cost = 5600)
    magicItem(Items.fluteOfMadness, "Flute of Madness", cost = 9000)

    pool(ItemPools.tavernScruffySpecials) {
        items(
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
        )
    }
}

class RylweenDslValidationTest {
    @Test
    fun townServicesValidateCharacterPlacement() {
        val towns = listOf(
            TownModel(
                id = Towns.murn.id,
                name = "Murn",
                position = xy(55, 29),
                taxPercent = 8,
                kind = TownKind.Village,
                services = listOf(
                    TownServiceModel(Buildings.inn),
                    TownServiceModel(Buildings.tavern),
                ),
            ),
        )

        val characters = listOf(
            CharacterModel(
                id = Npcs.yog,
                name = "Yog",
                job = NpcJobs.innkeeper,
                gender = Gender.Male,
                placement = CharacterPlacementModel.Fixed(Towns.murn.inn),
                roles = emptyList(),
            ),
        )

        validateTowns(towns)
        validateCharacters(characters, towns)
    }

    @Test
    fun unknownTownServicePlacementFailsValidation() {
        val towns = listOf(
            TownModel(
                id = Towns.murn.id,
                name = "Murn",
                position = xy(55, 29),
                taxPercent = 8,
                kind = TownKind.Village,
                services = listOf(TownServiceModel(Buildings.inn)),
            ),
        )

        val characters = listOf(
            CharacterModel(
                id = Npcs.dargoth,
                name = "Dargoth",
                job = NpcJobs.tavernkeeper,
                gender = Gender.Male,
                placement = CharacterPlacementModel.Fixed(Towns.murn.tavern),
                roles = emptyList(),
            ),
        )

        assertFailsWith<IllegalStateException> {
            validateCharacters(characters, towns)
        }
    }

    @Test
    fun townSystemValidatesMenuAndBuildingServices() {
        val towns = listOf(
            TownModel(
                id = Towns.murn.id,
                name = "Murn",
                position = xy(55, 29),
                taxPercent = 8,
                kind = TownKind.Village,
                services = listOf(TownServiceModel(Buildings.inn)),
            ),
        )
        val scene = SceneModel(Scenes.Towne.inn, emptyList())
        val townSystem = TownSystemBuilder().apply {
            menu {
                inn()
                leave()
            }
            buildingServices {
                inn()
            }
        }.build()

        validateTownSystem(townSystem, towns, listOf(scene))
    }

    @Test
    fun runtimeSnapshotEmitterWritesExactDslShape() {
        val party = partyId("replay")
        val state = StateModel(
            id = stateId("snapshot"),
            kind = StateKind.RuntimeSnapshot,
            realm = RealmState(
                day = 3,
                hour = 12,
                minute = 5,
                rngState = 1234L,
                activeParty = party,
                currentMap = Maps.wilderness,
                currentMode = RuntimeMode.Wilderness,
                currentTown = null,
                banished = false,
                lastPlayedDay = 3,
                partyMemberMax = 4,
            ),
            locations = emptyList(),
            npcs = listOf(NpcPlacement(Npcs.garth, Towns.thord.inn)),
            parties = listOf(
                PartyState(
                    id = party,
                    player = "Replay",
                    name = "Replay",
                    position = Wilderness.xy(12, 9),
                    facing = Facing.South,
                    resources = PartyResources(1000, 5, 100, 200, 14),
                    counters = PartyCounters(
                        memberCount = 1,
                        kills = 0,
                        waitingMail = false,
                        attacked = false,
                        attackedToday = listOf(PartyAttackRecord(party, false)),
                    ),
                    flags = listOf(Story.garth.bandits.recovered),
                    milestones = listOf(Milestones.silverKey),
                    inventory = (0..InventorySaveSlotMax).map { slot ->
                        InventorySlotState(slot, if (slot == 0) Items.redPotion else null)
                    },
                    members = listOf(
                        PartyMemberState(
                            name = "A",
                            gender = Gender.Male,
                            race = Race.Human,
                            pcClass = PcClass.Thief,
                            level = 1,
                            health = 68,
                            hitPoints = 68 of 68,
                            weaponSkill = 27,
                            wisdom = 27,
                            mana = 0 manaOf 27,
                            charisma = 49,
                            equipment = EquipmentState(Items.none, Items.none),
                            experience = 0,
                            dead = false,
                        ),
                    ),
                    visitedNpcs = listOf(NpcVisitState(Npcs.garth, true)),
                    journal = listOf(JournalEntryState.Line("Castle Ydarg has fallen.")),
                ),
            ),
        )

        val snapshot = renderRuntimeStateSnapshot(state)

        assertTrue("""runtimeState(stateId("snapshot"))""" in snapshot)
        assertTrue("""rngState = 1234L""" in snapshot)
        assertTrue("""mark(runtimeFlagState("garth.bandits", "recovered"))""" in snapshot)
        assertTrue("""slot(16).empty()""" in snapshot)
        assertTrue("""line("Castle Ydarg has fallen.")""" in snapshot)

        val parsed = parseRuntimeStateSnapshot(snapshot)
        assertEquals(snapshot, renderRuntimeStateSnapshot(parsed))
        assertEquals(state.id, parsed.id)
        assertEquals(Towns.thord.inn, parsed.npcs.single().position)
        assertEquals(Items.redPotion, parsed.parties.single().inventory.single { it.slot == 0 }.item)
        assertEquals("Castle Ydarg has fallen.", (parsed.parties.single().journal.single() as JournalEntryState.Line).text)
    }

    @Test
    fun traceEngineSeparatesViewCommandsFromNativeBoundaryRequests() {
        val keyRequest = EngineRequest(NativeAllowlist.inputKeyUpper.path)
        val engine = TraceEnginePort(ints = mapOf(keyRequest to 82))
        val runner = ScriptTraceRunner(engine)

        runner.run(
            listOf(
                Op.View(ViewCommand.ClearText),
                Op.LetInt("key", IntAst.NativeInt(keyRequest.path, keyRequest.args)),
                Op.EmitIndexed(TextPattern("key.{}"), IntAst.Var("key")),
            ),
        )

        assertEquals(
            EngineTraceEvent.View(ViewCommand.ClearText),
            engine.events[0],
        )
        assertEquals(EngineTraceEvent.NativeInt(keyRequest, 82), engine.events[1])
        assertTrue(engine.events[2] is EngineTraceEvent.EmitIndexed)
    }

    @Test
    fun compiledGameUsesTypedViewCommandsInsteadOfNativeScreenCalls() {
        val nativePaths = nativePathsIn(RylweenCompiledGame.nextDsl)

        assertFalse(
            nativePaths.any { it.startsWith("screen.") },
            "screen render calls should be typed ViewCommand events, not native paths: $nativePaths",
        )
    }

    @Test
    fun traceEngineRecordsInlineMenusWithoutChoosing() {
        val engine = TraceEnginePort()
        val runner = ScriptTraceRunner(engine)

        runner.run(
            listOf(
                Op.Menu(
                    TextBlock.Inline(listOf("(P)ay", "(N)o thanks")),
                    listOf(MenuOption('P', listOf(Op.Pause(1000))), MenuOption('N', emptyList())),
                    emptyList(),
                ),
            ),
        )

        assertEquals(
            EngineTraceEvent.Menu(TextBlock.Inline(listOf("(P)ay", "(N)o thanks")), listOf('P', 'N')),
            engine.events.single(),
        )
    }

    @Test
    fun structuredTextLineExtensionsAreSharedAcrossDomainBuilders() {
        assertEquals(structuredText { line("Hello") }, textLines("Hello"))

        val shop = shopText {
            line("Price: ", price)
        } as TextBlock.Structured
        val offer = RandomItemOfferTextBuilder().apply {
            line("Buy ", itemName, " for ", itemPrice)
        }.build() as TextBlock.Structured

        assertEquals(TextPart.ShopPrice, shop.lines.single().parts[1])
        assertEquals(TextPart.OfferedItemName, offer.lines.single().parts[1])
        assertEquals(TextPart.OfferedItemPrice, offer.lines.single().parts[3])
    }

    @Test
    fun terminalTextRendererCanInlineRuntimeLocationCoordinates() {
        val runtime = RuntimeSessionState.singleParty(
            party = testParty(),
            hour = 12,
            currentTown = null,
        ).copy(
            locations = listOf(LocationPlacement(Locations.nolrimCrypt, GridPoint(37, 14))),
        )

        val lines = TerminalTextRenderer().lines(
            structuredText {
                line("Nolrim's Crypt is at ", location(Locations.nolrimCrypt).coordinates, ".")
            },
            TerminalTextContext(runtime = runtime),
        )

        assertEquals(listOf("Nolrim's Crypt is at 37x14."), lines)
    }

    @Test
    fun townServiceBridgeAppendsRenderedJournalLines() {
        val scene = SceneModel(
            sceneId("test.journal_append"),
            listOf(
                Op.AppendJournal(
                    structuredText {
                        line("Nolrim's Crypt is at ", location(Locations.nolrimCrypt).coordinates, ".")
                    },
                ),
            ),
        )
        val service = BuildingServiceModel(
            building = Buildings.church,
            action = keyAction("enter_church"),
            scene = scene.id,
            opensAt = null,
            closesAt = null,
            nativeServices = emptyList(),
        )
        val runtime = RuntimeSessionState.singleParty(
            party = testParty(),
            hour = 12,
            currentTown = Towns.thord.id,
        ).copy(
            locations = listOf(LocationPlacement(Locations.nolrimCrypt, GridPoint(37, 14))),
        )

        val result = StatefulTownServiceBridge(
            FakeTerminalPort(),
            battleContent = RuntimeContent(itemRecords = RylweenCompiledGame.nextDsl.itemCatalog),
        ).run(
            service,
            scene,
            TownServiceContinuation.start(runtime),
        )

        assertTrue(result.status is TownServiceBridgeStatus.Finished)
        assertEquals(
            listOf(JournalEntryState.Line("Nolrim's Crypt is at 37x14.")),
            result.party.journal,
        )
        assertTrue(result.events.any { it is EngineTraceEvent.AppendJournal })
    }

    @Test
    fun traceEngineRecordsReusableChoices() {
        val engine = TraceEnginePort()
        val runner = ScriptTraceRunner(engine)

        runner.run(
            listOf(
                Op.AskMember(TextBlock.Ref(T.Towne.Church.who), "who", MemberChoiceFilter.DeadOnly),
                Op.AskInventorySlot(
                    TextBlock.Inline(listOf("Which item?")),
                    "slot",
                    'A',
                    1..InventorySaveSlotMax,
                    InventorySlotChoiceFilter.NonEmpty,
                ),
            ),
        )

        assertEquals(
            EngineTraceEvent.AskMember(TextBlock.Ref(T.Towne.Church.who), MemberChoiceFilter.DeadOnly),
            engine.events[0],
        )
        assertEquals(
            EngineTraceEvent.AskInventorySlot(
                TextBlock.Inline(listOf("Which item?")),
                'A',
                1..InventorySaveSlotMax,
                InventorySlotChoiceFilter.NonEmpty,
            ),
            engine.events[1],
        )
    }

    @Test
    fun traceEnginePropagatesStopSignals() {
        val engine = TraceEnginePort()
        val runner = ScriptTraceRunner(engine)

        runner.run(listOf(Op.Pause(1), Op.Stop("done"), Op.Pause(2)))

        assertEquals(EngineTraceEvent.Pause(1), engine.events[0])
        assertEquals(
            EngineTraceEvent.Stop("done", ScriptControlSignal.StopScript),
            engine.events[1],
        )
        assertEquals(2, engine.events.size)
    }

    @Test
    fun traceEngineRecordsReusableConversationAction() {
        val engine = TraceEnginePort()
        val runner = ScriptTraceRunner(engine)

        runner.run(listOf(Op.Speak(Buildings.tavern)))

        assertEquals(EngineTraceEvent.Speak(Buildings.tavern), engine.events.single())
    }

    @Test
    fun traceEngineRecordsReusableTorchConsumptionAction() {
        val content = GameBuilder().apply {
            scene(sceneId("test.consume_torch")) {
                consumeTorch()
            }
        }.build()
        val engine = TraceEnginePort()
        val runner = ScriptTraceRunner(engine)

        runner.run(content.scenes.single().ops)

        assertEquals(EngineTraceEvent.ConsumeTorch, engine.events.single())
    }

    @Test
    fun traceEngineRecordsReusableDungeonHandoffAction() {
        val content = GameBuilder().apply {
            scene(sceneId("test.enter_dungeon")) {
                enterDungeon(Dungeons.erethorTemple, levels = 12)
            }
        }.build()
        val engine = TraceEnginePort()
        val runner = ScriptTraceRunner(engine)

        runner.run(content.scenes.single().ops)

        assertEquals(
            EngineTraceEvent.DungeonHandoff(WildernessDungeonHandoff(Dungeons.erethorTemple, 12)),
            engine.events.single(),
        )
    }

    @Test
    fun scriptRewardsRejectMilestoneSpecialItems() {
        val content = GameBuilder().apply {
            items {
                silverKeyTestItem()
            }
            scene(sceneId("test.special_reward")) {
                reward {
                    items(Items.silverkey)
                }
            }
        }.build()

        assertFailsWith<IllegalStateException> {
            validateScriptRewards(content, content.itemCatalog)
        }
    }

    @Test
    fun randomItemOfferValidatesAndTracesAsReusableRuntimeAction() {
        val content = GameBuilder().apply {
            items {
                scruffyOfferTestItems()
            }
            scene(sceneId("test.random_offer")) {
                choice.randomItemOffer(ItemPools.tavernScruffySpecials) {
                    offer {
                        line("- I will sell you this")
                        line("  ", itemName)
                        line("  for ", itemPrice, " Aurum!")
                    }
                    broke("- You don't have that", "  much gold!")
                    full("- You can't carry it!")
                    deal("- Deal!!!")
                    declined("- Fine, I can live with", "  that.")
                }
            }
        }.build()

        validateScriptRewards(content, content.itemCatalog)
        assertEquals(
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
            content.itemPool(ItemPools.tavernScruffySpecials).items,
        )

        val offer = content.scenes.single().ops.single() as Op.RandomItemOffer
        assertEquals(RewardItemPlacement.FirstFreeInventorySlot, offer.offer.application.regularItems)
        assertEquals(RewardOverflowPolicy.PromptDropThenPlace, offer.offer.application.overflow)
        val text = offer.offer.offer as TextBlock.Structured
        assertTrue(text.lines.any { line -> TextPart.OfferedItemName in line.parts })
        assertTrue(text.lines.any { line -> TextPart.OfferedItemPrice in line.parts })

        val engine = TraceEnginePort()
        val runner = ScriptTraceRunner(engine)
        runner.run(listOf(offer))

        assertEquals(EngineTraceEvent.RandomItemOffer(offer.offer), engine.events.single())
    }

    @Test
    fun randomItemOfferSessionBuysSelectedItemUsingGeneratedItemCost() {
        val session = RandomItemOfferSession.start(
            testRandomItemOffer(),
            testParty(gold = 2000),
            content = RuntimeContent(itemRecords = compiledItemRecords()),
        )
        val bought = session
            .apply(RandomItemOfferInput.Buy(accept = true))
            .apply(RandomItemOfferInput.Continue)
        val result = (bought.step as RandomItemOfferStep.Finished).result

        assertEquals(RandomItemOfferStatus.Bought, result.status)
        assertEquals(Items.magicDagger, result.item)
        assertEquals(1800, result.price)
        assertEquals(1, result.slot)
        assertEquals(200, result.party.resources.gold)
        assertEquals(Items.magicDagger, result.party.inventoryItem(1))
    }

    @Test
    fun referenceRylweenRandomMatchesNativeXorshift64StarSequence() {
        val first = ReferenceRylweenRandom.draw(seed = 1L, lo = 0, hi = 16)
        val second = ReferenceRylweenRandom.draw(seed = first.nextSeed, lo = 0, hi = 16)

        assertEquals(4, first.value)
        assertEquals(33554433L, first.nextSeed)
        assertEquals(14, second.value)
        assertEquals(1126174793148417L, second.nextSeed)
    }

    @Test
    fun randomItemOfferSessionPromptsDropSlotOnInventoryOverflow() {
        val fullParty = testParty(
            gold = 2000,
            inventoryItems = (1..InventorySaveSlotMax).associateWith { Items.rope },
        )
        val dropPrompt = RandomItemOfferSession.start(
            testRandomItemOffer(),
            fullParty,
            content = RuntimeContent(itemRecords = compiledItemRecords()),
        )
            .apply(RandomItemOfferInput.Buy(accept = true))

        assertTrue(dropPrompt.step is RandomItemOfferStep.DropSlot)

        val bought = dropPrompt
            .apply(RandomItemOfferInput.DropSlot(1))
            .apply(RandomItemOfferInput.Continue)
        val result = (bought.step as RandomItemOfferStep.Finished).result

        assertEquals(RandomItemOfferStatus.Bought, result.status)
        assertEquals(1, result.slot)
        assertEquals(200, result.party.resources.gold)
        assertEquals(Items.magicDagger, result.party.inventoryItem(1))
    }

    @Test
    fun shopServiceValidatesAndDefaultsInventoryPurchasesToPromptDrop() {
        val content = GameBuilder().apply {
            items {
                shopTestItems()
            }
            scene(sceneId("test.shop")) {
                shopService(Buildings.merchant) {
                    image = Images.Scene.merchant
                    intro("Merchant", "")
                    menu("(B)uy item", "(S)ell item", "(L)eave")
                    buy {
                        prompt("Buy what?", "")
                        catalog('B', "Goods") {
                            resource(
                                'A',
                                Items.torch,
                                resource = ShopResources.torches,
                                cap = ShopQuantityCaps.fixed(Resource.Torches, 80),
                                tooMany = inlineText("Too many torches."),
                                zeroQuantityDeal = ShopZeroQuantityDeal.always,
                            )
                            item('B', Items.blanket)
                        }
                    }
                    sell {
                        prompt("Sell what?", "")
                        accepts(Items.blanket)
                        wrongKind("Wrong kind.")
                        offer {
                            line("Offer: ", price)
                        }
                    }
                }
            }
        }.build()

        validateScriptRewards(content, content.itemCatalog)

        val shop = (content.scenes.single().ops.single() as Op.ShopService).shop
        assertEquals(RewardOverflowPolicy.PromptDropThenPlace, shop.buy?.inventoryApplication?.overflow)
        assertEquals(
            listOf(Items.torch, Items.blanket),
            shop.buy?.catalogs?.single()?.pages?.single()?.entries?.map { it.item },
        )
        assertEquals(setOf(Items.blanket), shop.sell?.acceptedItems)

        val engine = TraceEnginePort()
        val runner = ScriptTraceRunner(engine)
        runner.run(listOf(Op.ShopService(shop)))

        assertEquals(EngineTraceEvent.ShopService(shop), engine.events.single())
    }

    @Test
    fun shopInterpreterBuysInventoryItemsWithPromptDropOverflow() {
        val shop = testMerchantShop()
        val fullParty = testParty(
            gold = 100,
            inventoryItems = (1..InventorySaveSlotMax).associateWith { Items.rope } + (1 to Items.redPotion),
        )
        val interpreter = ShopServiceInterpreter(compiledItemRecords(), compiledRealmContent())

        val needsDrop = interpreter.buy(shop, fullParty, Items.blanket)
        assertEquals(ShopTransactionStatus.NeedsDropSlot, needsDrop.status)

        val bought = interpreter.buy(shop, fullParty, Items.blanket, dropSlot = 1)

        assertEquals(ShopTransactionStatus.Bought, bought.status)
        assertEquals(Items.blanket, bought.party.inventoryItem(1))
        assertEquals(70, bought.party.resources.gold)
    }

    @Test
    fun shopInterpreterBuysResourcesWithCapsAndDynamicPrices() {
        val shop = testMerchantShop()
        val noDonkeyParty = testParty(
            gold = 1000,
            level = 2,
            food = 100,
        )
        val donkeyParty = testParty(
            gold = 1000,
            level = 2,
            food = 100,
            inventoryItems = mapOf(1 to Items.donkey),
        )
        val interpreter = ShopServiceInterpreter(compiledItemRecords(), compiledRealmContent())

        val boughtNoDonkeyFood = interpreter.buy(shop, noDonkeyParty, Items.ration, quantity = 999)
        val boughtDonkeyFood = interpreter.buy(shop, donkeyParty, Items.ration, quantity = 999)
        val boughtTorches = interpreter.buy(shop, testParty(gold = 10, torches = 78), Items.torch, quantity = 99)

        assertEquals(
            150,
            testParty(inventoryItems = mapOf(0 to Items.donkey))
                .resourceCapacity(compiledItemRecords(), Resource.Food, baseCapacity = 150),
        )
        assertEquals(ShopTransactionStatus.Bought, boughtNoDonkeyFood.status)
        assertEquals(50, boughtNoDonkeyFood.quantity)
        assertEquals(150, boughtNoDonkeyFood.party.resources.food)
        assertEquals(850, boughtNoDonkeyFood.party.resources.gold)
        assertEquals(ShopTransactionStatus.Bought, boughtDonkeyFood.status)
        assertEquals(200, boughtDonkeyFood.quantity)
        assertEquals(300, boughtDonkeyFood.party.resources.food)
        assertEquals(400, boughtDonkeyFood.party.resources.gold)
        assertEquals(ShopTransactionStatus.Bought, boughtTorches.status)
        assertEquals(2, boughtTorches.quantity)
        assertEquals(80, boughtTorches.party.resources.torches)
        assertEquals(8, boughtTorches.party.resources.gold)
    }

    @Test
    fun droppingDonkeyForInventoryOverflowClampsFoodToRemainingCapacity() {
        val shop = testMerchantShop()
        val fullParty = testParty(
            gold = 100,
            food = 300,
            inventoryItems = (1..InventorySaveSlotMax).associateWith { Items.rope } + (1 to Items.donkey),
        )
        val interpreter = ShopServiceInterpreter(compiledItemRecords(), compiledRealmContent())

        val bought = interpreter.buy(shop, fullParty, Items.blanket, dropSlot = 1)

        assertEquals(ShopTransactionStatus.Bought, bought.status)
        assertEquals(Items.blanket, bought.party.inventoryItem(1))
        assertEquals(
            150,
            bought.party.resourceCapacity(compiledItemRecords(), Resource.Food, baseCapacity = 150),
        )
        assertEquals(150, bought.party.resources.food)
    }

    @Test
    fun newCarrierAndShelterItemsContributePurelyFromDsl() {
        val horse = item("horse")
        val magicRobe = item("magic_robe")
        val arcticBand = TemperatureBand.VeryLow
        val records = listOf(
            ItemRecordModel(
                id = Items.donkey,
                kind = ItemKind.Treasure,
                displayName = "Donkey",
                cost = 800,
                storage = ItemStorageKind.SlottedInventory,
                resourceCapacityBonus = mapOf(Resource.Food to 150),
            ),
            ItemRecordModel(
                id = horse,
                kind = ItemKind.Treasure,
                displayName = "Horse",
                cost = 1500,
                storage = ItemStorageKind.SlottedInventory,
                resourceCapacityBonus = mapOf(Resource.Food to 250),
            ),
            ItemRecordModel(
                id = Items.tent,
                kind = ItemKind.Gear,
                displayName = "Tent",
                cost = 80,
                storage = ItemStorageKind.SlottedInventory,
                campProtection = setOf(TemperatureBand.Low, TemperatureBand.VeryLow),
            ),
            ItemRecordModel(
                id = magicRobe,
                kind = ItemKind.Gear,
                displayName = "Magic Robe",
                cost = 4000,
                storage = ItemStorageKind.SlottedInventory,
                campProtection = setOf(arcticBand),
            ),
        )

        val party = testParty(
            food = 0,
            inventoryItems = mapOf(1 to Items.donkey, 2 to horse, 3 to magicRobe),
        )

        assertEquals(550, party.resourceCapacity(records, Resource.Food, baseCapacity = 150))
        val fallback = image("blitgfx.no_tent")
        val effect = party.campGearEffect(records, fallbackImage = fallback)
        assertTrue(effect.protects(arcticBand))
        assertFalse(effect.protects(TemperatureBand.Low))
    }

    @Test
    fun campGearEffectReflectsTentAndBlanketProtection() {
        val records = compiledItemRecords()
        val fallback = image("blitgfx.no_tent")
        val noGear = testParty().campGearEffect(records, fallback)
        val blanket = testParty(inventoryItems = mapOf(1 to Items.blanket)).campGearEffect(records, fallback)
        val tent = testParty(inventoryItems = mapOf(1 to Items.tent)).campGearEffect(records, fallback)
        val reservedSlotTent = testParty(inventoryItems = mapOf(0 to Items.tent)).campGearEffect(records, fallback)

        assertEquals(fallback, noGear.image)
        assertFalse(noGear.protects(TemperatureBand.Low))
        assertFalse(noGear.protects(TemperatureBand.VeryLow))
        assertEquals(fallback, blanket.image)
        assertTrue(blanket.protects(TemperatureBand.Low))
        assertFalse(blanket.protects(TemperatureBand.VeryLow))
        assertEquals(Images.Scene.tent, tent.image)
        assertTrue(tent.protects(TemperatureBand.Low))
        assertTrue(tent.protects(TemperatureBand.VeryLow))
        assertEquals(fallback, reservedSlotTent.image)
    }

    @Test
    fun campSessionReportsNotTiredWhenRestedHoursAreNonZero() {
        val party = testParty(food = 100, restedHours = 4)
        val session = CampSession.start(
            runtime = RuntimeSessionState.singleParty(party = party, hour = 12, currentTown = null),
            content = RylweenCompiledGame.nextDsl.runtimeContent(),
        )

        val finished = session
            .apply(CampSessionInput.Continue)
            .step as CampSessionStep.Finished

        assertEquals(CampSessionStatus.NotTired, finished.result.status)
        assertEquals(4, finished.result.runtime.activeParty.resources.restedHours)
    }

    @Test
    fun campSessionRefusesWithInsufficientFoodAfterGuardChoice() {
        val party = testParty(food = 0, restedHours = 0)
        val session = CampSession.start(
            runtime = RuntimeSessionState.singleParty(party = party, hour = 12, currentTown = null),
            content = RylweenCompiledGame.nextDsl.runtimeContent(),
        )

        val finished = session
            .apply(CampSessionInput.GuardAnswer(yes = false))
            .apply(CampSessionInput.Continue)
            .step as CampSessionStep.Finished

        assertEquals(CampSessionStatus.NotEnoughFood, finished.result.status)
        assertEquals(0, finished.result.runtime.activeParty.resources.restedHours)
    }

    @Test
    fun campSessionAppliesFoodCostHealingAndAdvancesHourWhenRested() {
        val damaged = testParty(
            food = 100,
            restedHours = 0,
            inventoryItems = mapOf(1 to Items.tent),
            members = listOf(
                testMember(name = "A", hitPoints = 4, health = 30),
                testMember(name = "B", hitPoints = 5, health = 30),
            ),
        )
        val content = RylweenCompiledGame.nextDsl.runtimeContent().copy(
            realm = RylweenCompiledGame.nextDsl.realm.copy(
                camp = RylweenCompiledGame.nextDsl.realm.camp.copy(
                    ambushChancePercent = 0,
                    temperatureMax = 0,
                ),
            ),
        )
        val runtime = RuntimeSessionState.singleParty(
            party = damaged,
            hour = 10,
            currentTown = null,
        )
        val initial = CampSession.start(runtime, content)

        var session = initial.apply(CampSessionInput.GuardAnswer(yes = false))
        while (session.step is CampSessionStep.Messages) {
            session = session.apply(CampSessionInput.Continue)
        }
        val finished = session.step as CampSessionStep.Finished

        assertEquals(CampSessionStatus.Rested, finished.result.status)
        val rested = finished.result.runtime
        assertEquals(14, rested.activeParty.resources.restedHours)
        // food cost = foodCostPerMember (4) * 2 living = 8 → 100 - 8 = 92
        assertEquals(92, rested.activeParty.resources.food)
        // hour advanced by hoursAdvanced (8) → 10 + 8 = 18
        assertEquals(18, rested.realm.hour)
        // both members healed by healthGain (+15), clamped to per-member max
        assertEquals(19, rested.activeParty.members[0].hitPoints.current)
        assertEquals(20, rested.activeParty.members[1].hitPoints.current)
    }

    @Test
    fun compiledRealmAuthorsWildernessUpkeepBalance() {
        val upkeep = compiledRealmContent().upkeep

        assertEquals(4, upkeep.foodCostLeaderDiv)
        assertEquals(5, upkeep.foodTickEvery)
        assertEquals(0, upkeep.starveFoodThreshold)
        assertEquals(2, upkeep.starveDamage)
        assertEquals(50, upkeep.manaRegenChance)
        assertEquals(1, upkeep.restedHoursCostPerTurn)
        assertEquals(
            listOf(
                WildernessFatigueRule(0, inlineText("You begin to feel tired")),
                WildernessFatigueRule(-4, inlineText("You are exhausted!!!"), drain = true),
                WildernessFatigueRule(-8, inlineText("You begin to feel weak", "from lack of sleep.", ""), drain = true),
                WildernessFatigueRule(
                    -8,
                    inlineText("You faint from lack", "of well needed sleep."),
                    match = WildernessFatigueMatch.LessThan,
                    drain = true,
                ),
            ),
            upkeep.fatigue,
        )
    }

    @Test
    fun compiledRealmAuthorsHuntBalanceAndNarration() {
        val hunt = checkNotNull(compiledRealmContent().hunt)
        val runtime = RuntimeSessionState.singleParty(testParty(), hour = 12, currentTown = null)

        assertEquals(45, hunt.successChancePercent)
        assertEquals(5, hunt.timeCost)
        assertEquals(10, hunt.foodMin)
        assertEquals(19, hunt.foodMax)
        assertEquals(
            listOf("Who? (1-4)"),
            compiledTextRenderer().lines(hunt.whoPrompt, TerminalTextContext(runtime = runtime)),
        )
    }

    @Test
    fun compiledGameAuthorsStaticScreens() {
        val screens = RylweenCompiledGame.nextDsl.visuals.staticScreens.associateBy { it.id }
        val help = screens.getValue(StaticScreens.help)

        assertEquals(6, screens.size)
        assertEquals(Images.Static.screen, screens.getValue(StaticScreens.frame).image)
        assertEquals(Images.Static.title, screens.getValue(StaticScreens.title).image)
        assertTrue(help.commandHelp)
        assertEquals("rylween.doc", screens.getValue(StaticScreens.manual).resource)
        assertEquals("score.txt", screens.getValue(StaticScreens.score).resource)
        assertEquals("vscore.txt", screens.getValue(StaticScreens.verboseScore).resource)
        assertTrue(RylweenCompiledGame.nextDsl.wilderness.keys.helpPages.isEmpty())
        assertEquals(3, RylweenCompiledGame.nextDsl.wilderness.keys.effectiveHelpPages().size)
    }

    @Test
    fun compiledGameGeneratesCommandHelpFromBindings() {
        val renderer = RylweenCompiledGame.nextDsl.terminalTextRenderer()
        val wildernessPages = RylweenCompiledGame.nextDsl.wilderness.keys.effectiveHelpPages()
        val wildernessLines = wildernessPages.flatMap { page -> renderer.lines(page) }
        val dungeonPages = DungeonKeysModel().effectiveHelpPages()
        val dungeonLines = dungeonPages.flatMap { page -> renderer.lines(page) }
        val ydargPages = checkNotNull(RylweenCompiledGame.nextDsl.ydarg).keys.navigationHelpPages()
        val ydargLines = ydargPages.flatMap { page -> renderer.lines(page) }
        val textWindowWidth = checkNotNull(
            RylweenCompiledGame.nextDsl.terminal.regions.single { region ->
                region.id == ScreenRegions.textWindow
            }.width,
        )

        assertTrue(wildernessPages.all { page -> renderer.lines(page).size <= 7 })
        assertTrue(dungeonPages.all { page -> renderer.lines(page).size <= 7 })
        assertTrue(ydargPages.all { page -> renderer.lines(page).size <= 7 })
        assertTrue((wildernessLines + dungeonLines + ydargLines).all { line -> line.length <= textWindowWidth })
        assertTrue("C = Camp" in wildernessLines)
        assertTrue("8/Up = Move North" in wildernessLines)
        assertTrue("D = Descend" in dungeonLines)
        assertTrue("8 = Move North" in ydargLines)
        assertTrue("? = View Help" in ydargLines)
    }

    @Test
    fun compiledGameAuthorsWildernessMapRows() {
        val grid = checkNotNull(RylweenCompiledGame.nextDsl.worldMapGrid(Maps.wilderness))
        val reference = WorldMapGrid.loadReferenceMapDat(File("data/map.dat"))

        assertEquals(reference.rows, grid.rows)
        assertEquals(Maps.wilderness, grid.id)
        assertEquals(41, grid.height)
        assertEquals(78, grid.width)
    }

    @Test
    fun compiledRuntimeSnapshotRoundTripsThroughSaveReader() {
        val snapshot = renderRuntimeStateSnapshot(RylweenCompiledGame.nextRylween.states.single { it.id == States.winningReplay })
        val parsed = parseRuntimeStateSnapshot(snapshot)

        assertEquals(snapshot, renderRuntimeStateSnapshot(parsed))
        assertEquals(States.winningReplay, parsed.id)
        assertEquals(RuntimeMode.GameOver, parsed.realm.currentMode)
        assertEquals(2, parsed.parties.single().members.size)
    }

    @Test
    fun huntSessionSpendsMovesAndAddsFoodOnSuccess() {
        val rules = checkNotNull(compiledRealmContent().hunt).copy(
            successChancePercent = 100,
            foodMin = 10,
            foodMax = 10,
        )
        val party = testParty(
            food = 20,
            timeLeft = 20,
            members = listOf(testMember(name = "Risha", pcClass = PcClass.Ranger, level = 3)),
        )
        var session = HuntSession.start(
            runtime = RuntimeSessionState.singleParty(party, hour = 12, currentTown = null),
            rules = rules,
        ).apply(HuntSessionInput.Member(1))

        val messages = session.step as HuntSessionStep.Messages
        val context = TerminalTextContext(
            runtime = session.runtime,
            huntMemberNumber = messages.memberNumber,
            huntFoodGained = messages.foodGained,
        )
        assertEquals(
            listOf("Risha tries", "to trap a rabbit..."),
            compiledTextRenderer().lines(messages.blocks[0], context),
        )
        assertEquals(
            listOf("...and succeeds.", "The party gains", "13 food rations.", ""),
            compiledTextRenderer().lines(messages.blocks[1], context),
        )

        session = session.apply(HuntSessionInput.Continue)
        val finished = session.step as HuntSessionStep.Finished

        assertEquals(HuntSessionStatus.Succeeded, finished.result.status)
        assertEquals(15, finished.result.runtime.activeParty.resources.timeLeft)
        assertEquals(33, finished.result.runtime.activeParty.resources.food)
    }

    @Test
    fun wildernessTerminalAdapterRunsAuthoredHunt() {
        val terminal = FakeTerminalPort("H", "1")
        val party = testParty(
            food = 20,
            timeLeft = 20,
            members = listOf(testMember(name = "Risha", pcClass = PcClass.Ranger, level = 3)),
        )
        val session = WildernessSession(
            runtime = RuntimeSessionState.singleParty(party, hour = 12, currentTown = null),
            worldMap = WorldMapGrid.fromSourceRows((1..12).map { mapRow() }),
            content = RylweenCompiledGame.nextDsl.runtimeContent().copy(
                realm = RylweenCompiledGame.nextDsl.realm.copy(
                    upkeep = WildernessUpkeepRules(foodTickEvery = 0, fatigue = emptyList()),
                    hunt = checkNotNull(RylweenCompiledGame.nextDsl.realm.hunt).copy(
                        successChancePercent = 100,
                        foodMin = 10,
                        foodMax = 10,
                    ),
                ),
            ),
            step = WildernessSessionStep.Explore,
        )

        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(session)

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.HuntSucceeded, result.session.lastResult?.status)
        assertEquals(15, result.session.runtime.activeParty.resources.timeLeft)
        assertEquals(33, result.session.runtime.activeParty.resources.food)
        assertTrue(terminal.output.any { "Risha tries" in it })
        assertTrue(terminal.output.any { "13 food rations." in it })
    }

    @Test
    fun consumeTorchDecrementsResourceOrReportsNoTorches() {
        val lit = testParty(torches = 2).consumeTorch() as TorchActionResult.Consumed
        val last = testParty(torches = 1).consumeTorch() as TorchActionResult.Consumed
        val none = testParty(torches = 0).consumeTorch() as TorchActionResult.NoTorches

        assertEquals(1, lit.party.resources.torches)
        assertEquals(0, last.party.resources.torches)
        assertEquals(0, none.party.resources.torches)
    }

    @Test
    fun inventoryPoolCountAndConsumeUseSlottedItemsOnly() {
        val party = testParty(
            inventoryItems = mapOf(
                0 to Items.redGem,
                1 to Items.redGem,
                2 to Items.blueGem,
                3 to Items.rope,
                4 to Items.greenGem,
                5 to Items.redGem,
                6 to Items.blueGem,
                7 to Items.greenGem,
            ),
        )

        val gems = RylweenCompiledGame.nextDsl.itemPool(ItemPools.thunAltarGems)
        val consumed = party.consumeInventoryItems(gems, 5)

        assertEquals(6, party.countInventoryItems(gems))
        assertEquals(1, consumed.countInventoryItems(gems))
        assertEquals(Items.redGem, consumed.inventoryItem(0))
        assertEquals(null, consumed.inventoryItem(1))
        assertEquals(null, consumed.inventoryItem(2))
        assertEquals(Items.rope, consumed.inventoryItem(3))
        assertEquals(null, consumed.inventoryItem(4))
        assertEquals(null, consumed.inventoryItem(5))
        assertEquals(null, consumed.inventoryItem(6))
        assertEquals(Items.greenGem, consumed.inventoryItem(7))
    }

    @Test
    fun dungeonDescentConsumesTorchOnlyAfterTransitionChecksPass() {
        val descended = testParty(torches = 2).tryDungeonDescent(currentDepth = 1, maxDepth = 3)
                as DungeonDescentResult.Descended
        val noTorches = testParty(torches = 0).tryDungeonDescent(currentDepth = 1, maxDepth = 3)
                as DungeonDescentResult.NoTorches
        val atMaxDepth = testParty(torches = 2).tryDungeonDescent(currentDepth = 3, maxDepth = 3)
                as DungeonDescentResult.AtMaxDepth

        assertEquals(1, descended.party.resources.torches)
        assertEquals(0, noTorches.party.resources.torches)
        assertEquals(2, atMaxDepth.party.resources.torches)
    }

    @Test
    fun dungeonSessionDescendsWithTorchesAndReturnsToWilderness() {
        val map = WorldMapGrid.fromSourceRows((1..4).map { mapRow(width = 4) })
        val runtime = RuntimeSessionState.singleParty(
            party = testParty(torches = 2),
            hour = 12,
            currentTown = null,
        )
        val content = RuntimeContent(prompts = RylweenCompiledGame.nextDsl.prompts)
        val started = DungeonSession.start(runtime, map, Dungeons.randomCave, levels = 2, content = content)

        val descended = started.apply(DungeonSessionInput.Descend)
        val atDepthTwo = descended.apply(DungeonSessionInput.Continue)
        val atMax = atDepthTwo.apply(DungeonSessionInput.Descend)
        val ascended = atMax.apply(DungeonSessionInput.Continue).apply(DungeonSessionInput.Ascend)
        val atDepthOne = ascended.apply(DungeonSessionInput.Continue)
        val leaving = atDepthOne.apply(DungeonSessionInput.Ascend)
        val finished = leaving.apply(DungeonSessionInput.Continue)

        assertEquals(RuntimeMode.Dungeon, started.runtime.realm.currentMode)
        assertEquals(DungeonSessionResultStatus.Descended, descended.lastResult?.status)
        assertEquals(1, descended.party.resources.torches)
        assertEquals(2, descended.depth)
        val descentMessages = descended.step as DungeonSessionStep.Messages
        assertEquals(content.prompts.dungeon.renderEnterLevel(2), descentMessages.blocks.single())
        assertEquals(DungeonSessionResultStatus.AtMaxDepth, atMax.lastResult?.status)
        assertEquals(1, atMax.party.resources.torches)
        assertEquals(DungeonSessionResultStatus.LeftDungeon, leaving.lastResult?.status)
        val leavingMessages = leaving.step as DungeonSessionStep.Messages
        assertEquals(content.prompts.dungeon.leaveCavern, leavingMessages.blocks.single())
        assertTrue(finished.step is DungeonSessionStep.Finished)
        assertEquals(RuntimeMode.Wilderness, finished.runtime.realm.currentMode)
    }

    @Test
    fun dungeonEventsClearOnlyInsideCurrentSession() {
        val event = dungeonEvent("test.gold_once")
        val dungeons = DungeonSystemBuilder().apply {
            event(event) {
                run {
                    party.gold += 1
                }
            }
            scenario(Dungeons.randomCave) {
                level(1, event)
            }
        }.build()
        val map = WorldMapGrid.fromSourceRows((1..4).map { mapRow(width = 4) })
        val runtime = RuntimeSessionState.singleParty(testParty(), hour = 12, currentTown = null)

        val first = DungeonSessionTerminalAdapter(FakeTerminalPort()).runUntilBlocked(
            DungeonSession.start(runtime, map, Dungeons.randomCave, levels = 1, dungeons = dungeons),
        ).session
        val secondStart = DungeonSession.start(first.runtime, map, Dungeons.randomCave, levels = 1, dungeons = dungeons)

        assertEquals(1, first.party.resources.gold)
        assertEquals(emptyMap(), first.levelEvents)
        assertTrue(secondStart.step is DungeonSessionStep.Event)
    }

    @Test
    fun recurringDungeonEventsStayArmedInsideCurrentSession() {
        val event = dungeonEvent("test.recurring_gold")
        val dungeons = DungeonSystemBuilder().apply {
            event(event) {
                recurring()
                run {
                    party.gold += 1
                }
            }
            scenario(Dungeons.randomCave) {
                level(1, event)
            }
        }.build()
        val map = WorldMapGrid.fromSourceRows((1..4).map { mapRow(width = 4) })
        val runtime = RuntimeSessionState.singleParty(testParty(), hour = 12, currentTown = null)

        val session = DungeonSessionTerminalAdapter(FakeTerminalPort()).runUntilBlocked(
            DungeonSession.start(runtime, map, Dungeons.randomCave, levels = 1, dungeons = dungeons),
        ).session

        assertEquals(1, session.party.resources.gold)
        assertEquals(event, session.levelEvents[1])
    }

    @Test
    fun compiledSpecialDungeonRewardRoomsAreGuardedByPersistentStoryState() {
        val content = RylweenCompiledGame.nextDsl
        val fireShieldEvent = content.dungeons.events.single { it.id == DungeonEvents.fireShield }
        val dungeons = DungeonSystemModel(
            events = listOf(fireShieldEvent),
            scenarios = listOf(
                DungeonScenarioModel(
                    dungeon = Dungeons.fireShieldCave,
                    levelRange = 1..1,
                    fixedLevelEvents = mapOf(1 to DungeonEvents.fireShield),
                    randomEvents = emptyList(),
                ),
            ),
        )
        val map = WorldMapGrid.fromSourceRows((1..4).map { mapRow(width = 4) })
        val party = testParty(
            flags = listOf(Story.hermit.trollTooth.fireShieldAcquired),
        ).copy(milestones = listOf(Milestones.fireShield))
        val runtime = RuntimeSessionState.singleParty(party, hour = 12, currentTown = null)
        val terminal = FakeTerminalPort()

        val session = DungeonSessionTerminalAdapter(terminal).runUntilBlocked(
            DungeonSession.start(runtime, map, Dungeons.fireShieldCave, levels = 1, dungeons = dungeons),
        ).session

        assertEquals(emptyMap(), session.levelEvents)
        assertFalse(terminal.output.any { it.contains("FIRE SHIELD") })
    }

    @Test
    fun compiledDungeonPlaythroughSolvesThunRiddlesAndTakesBracelets() {
        val terminal = FakeTerminalPort(
            "",
            "water",
            "",
            "D",
            "",
            "night",
            "",
            "D",
            "",
            "a well",
            "",
            "D",
            "",
        )
        val result = DungeonSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledDungeonSession(
                dungeon = Dungeons.thunTemple,
                levels = 4,
                dungeons = fixedCompiledDungeon(
                    Dungeons.thunTemple,
                    1 to DungeonEvents.thunRiddle1,
                    2 to DungeonEvents.thunRiddle2,
                    3 to DungeonEvents.thunRiddle3,
                    4 to DungeonEvents.bracelets,
                ),
                party = testParty(torches = 4),
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(DungeonSessionResultStatus.EventCompleted, result.session.lastResult?.status)
        assertTrue(result.session.party.milestones.contains(Milestones.braceletsOfThun))
        assertTrue(result.session.party.hasFlagState(Story.bracelets.thun.braceletsTaken))
        assertTrue(terminal.output.any { "Bracelets of Thun" in it })
    }

    @Test
    fun compiledDungeonPlaythroughUsesDungeonLevelContextForColourRiddle() {
        val terminal = FakeTerminalPort(
            *(
                List(12) { "D" } +
                    listOf("", "", "7", "")
                ).toTypedArray(),
        )
        val result = DungeonSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledDungeonSession(
                dungeon = Dungeons.reebarTomb,
                levels = 13,
                dungeons = fixedCompiledDungeon(
                    Dungeons.reebarTomb,
                    13 to DungeonEvents.colourRiddle,
                ),
                party = testParty(torches = 12),
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(13, result.session.depth)
        assertEquals(DungeonSessionResultStatus.EventCompleted, result.session.lastResult?.status)
        assertTrue(result.session.step is DungeonSessionStep.Explore)
        assertTrue(terminal.output.any { "ANTECHAMBER #1" in it })
        assertTrue(terminal.output.any { "You feel a slight" in it })
    }

    @Test
    fun compiledDungeonPlaythroughCanTakeSilverKeyAfterPassword() {
        val terminal = FakeTerminalPort("", "RISH EKKIN", "", "")
        val result = DungeonSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledDungeonSession(
                dungeon = Dungeons.hobgoblinStronghold,
                levels = 1,
                dungeons = fixedCompiledDungeon(
                    Dungeons.hobgoblinStronghold,
                    1 to DungeonEvents.hobgoblinChief,
                ),
                party = testParty(),
                withCombat = false,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(DungeonSessionResultStatus.EventCompleted, result.session.lastResult?.status)
        assertTrue(result.session.party.milestones.contains(Milestones.silverKey))
        assertTrue(result.session.party.hasFlagState(Story.silverKey.keyTaken))
        assertTrue(terminal.output.any { "Silver Key" in it })
    }

    @Test
    fun terminalEventHookReceivesAuthoredAnsiScreenImages() {
        val terminal = RecordingTerminalPort("C", "L", "L")
        val result = TownSessionTerminalAdapter(
            terminal,
            renderer = compiledTextRenderer(),
            serviceBridge = StatefulTownServiceBridge(
                terminal,
                renderer = compiledTextRenderer(),
            ),
        ).runUntilBlocked(compiledTownSession(party = testParty(gold = 10)))

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertTrue(Images.Scene.church.slug in terminal.imageSlugs)
        assertTrue(ViewCommand.ClearMapWindow in terminal.viewCommands)
    }

    @Test
    fun terminalEventHookReceivesCaveAndDungeonBackdropImages() {
        val caveTerminal = RecordingTerminalPort("2", "L")
        WildernessSessionTerminalAdapter(caveTerminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(food = 100, timeLeft = 200, restedHours = 14)
                    .copy(position = Wilderness.xy(12, 9)),
                rngState = 21L,
            ),
        )

        val dungeonTerminal = RecordingTerminalPort()
        DungeonSessionTerminalAdapter(dungeonTerminal, compiledTextRenderer()).runUntilBlocked(
            compiledDungeonSession(
                dungeon = Dungeons.randomCave,
                levels = 1,
                dungeons = fixedCompiledDungeon(Dungeons.randomCave),
            ),
        )

        assertTrue(Images.Backdrop.cave.slug in caveTerminal.imageSlugs)
        assertTrue(Images.Backdrop.insideCave.slug in dungeonTerminal.imageSlugs)
    }

    @Test
    fun shopSessionRunsMerchantBuyDropSellAndRejectFlows() {
        val shop = testMerchantShop()
        val party = testParty(
            gold = 1000,
            level = 2,
            torches = 78,
            food = 100,
            inventoryItems = (1..InventorySaveSlotMax).associateWith { Items.rope } +
                    mapOf(1 to Items.blanket, 2 to Items.donkey, 3 to Items.sword),
        )

        var session = ShopServiceSession.start(shop, party, compiledItemRecords(), compiledRealmContent()).continueIntro()

        session = session.chooseMain(ShopMainChoice.Buy)
            .chooseBuyItem(Items.torch)
            .enterQuantity(99)
            .confirmPurchase(accept = true)
        assertEquals(ShopTransactionStatus.Bought, session.lastTransaction?.status)
        assertEquals(80, session.party.resources.torches)

        session = session.chooseMain(ShopMainChoice.Buy)
            .chooseBuyItem(Items.ration)
            .enterQuantity(999)
            .confirmPurchase(accept = true)
        assertEquals(300, session.party.resources.food)
        assertEquals(398, session.party.resources.gold)

        session = session.chooseMain(ShopMainChoice.Buy)
            .chooseBuyItem(Items.blanket)
            .confirmPurchase(accept = true)
        assertTrue(session.step is ShopServiceStep.DropSlot)

        session = session.chooseDropSlot(2)
        assertEquals(ShopTransactionStatus.Bought, session.lastTransaction?.status)
        assertEquals(Items.blanket, session.party.inventoryItem(2))
        assertEquals(150, session.party.resources.food)

        session = session.chooseMain(ShopMainChoice.Sell).chooseSellSlot(2)
        val quote = session.step as ShopServiceStep.ConfirmSale
        assertEquals(ShopSellQuoteStatus.Quoted, quote.quote.status)
        assertEquals(15, quote.quote.price)

        session = session.confirmSale(accept = true)
        assertEquals(ShopTransactionStatus.Sold, session.lastTransaction?.status)
        assertEquals(null, session.party.inventoryItem(2))

        session = session.chooseMain(ShopMainChoice.Sell).chooseSellSlot(3)
        assertEquals(ShopSellQuoteStatus.ItemNotAcceptedHere, session.lastSellQuote?.status)
    }

    @Test
    fun shopSessionSelectsCatalogBeforeMultiCatalogBuyItems() {
        val shop = testMultiCatalogSmithShop()
        var session = ShopServiceSession.start(
            shop,
            testParty(gold = 1000),
            compiledItemRecords(),
            compiledRealmContent(),
        ).continueIntro()

        session = session.chooseMain(ShopMainChoice.Buy)
        val select = session.step as ShopServiceStep.SelectCatalog
        assertEquals(listOf('A', 'W'), select.catalogs.map { it.key })

        session = session.chooseCatalog(select.catalogs.single { it.key == 'A' })
        val armor = session.step as ShopServiceStep.BuyItem
        assertEquals(listOf("Armor"), armor.catalogs.map { it.title })
        assertEquals(listOf(Items.leather), armor.catalogs.single().pages.single().entries.map { it.item })

        session = session.chooseBuyItem(Items.leather).confirmPurchase(accept = true)
        assertEquals(ShopTransactionStatus.Bought, session.lastTransaction?.status)
        assertEquals(Items.leather, session.party.inventoryItem(1))

        session = session.chooseMain(ShopMainChoice.Buy).chooseCatalog('N')
        assertTrue(session.step is ShopServiceStep.MainMenu)
    }

    @Test
    fun shopSessionQuotesSmithHaggleBeforeSale() {
        val shop = testSmithShop()
        var session = ShopServiceSession.start(
            shop,
            testParty(gold = 0, inventoryItems = mapOf(1 to Items.sword, 2 to Items.axe)),
            compiledItemRecords(),
            compiledRealmContent(),
        ).continueIntro()

        session = session.chooseMain(ShopMainChoice.Sell).chooseSellSlot(1, bargainRolls = listOf(91))

        val quote = session.step as ShopServiceStep.ConfirmSale
        assertEquals(ShopSellQuoteStatus.Quoted, quote.quote.status)
        assertEquals(116, quote.quote.price)

        session = session.confirmSale(accept = true)
        assertEquals(ShopTransactionStatus.Sold, session.lastTransaction?.status)
        assertEquals(116, session.party.resources.gold)
        assertEquals(null, session.party.inventoryItem(1))

        session = session.chooseMain(ShopMainChoice.Sell).chooseSellSlot(2, bargainRolls = listOf(75))
        assertEquals(ShopSellQuoteStatus.Declined, session.lastSellQuote?.status)
        assertEquals(116, session.party.resources.gold)
    }

    @Test
    fun shopTraceRunnerConsumesScriptedInputsAndEmitsPrompts() {
        val shop = testMerchantShop()
        val engine = TraceEnginePort()
        val runner = ShopServiceTraceRunner(engine)

        val session = runner.run(
            ShopServiceSession.start(shop, testParty(gold = 10, torches = 78), compiledItemRecords(), compiledRealmContent()),
            listOf(
                ShopServiceInput.ContinueIntro,
                ShopServiceInput.Main(ShopMainChoice.Buy),
                ShopServiceInput.BuyItem(Items.torch),
                ShopServiceInput.Quantity(99),
                ShopServiceInput.ConfirmPurchase(accept = true),
                ShopServiceInput.Main(ShopMainChoice.Leave),
            ),
        )

        assertEquals(80, session.party.resources.torches)
        assertEquals(8, session.party.resources.gold)
        assertTrue(session.step is ShopServiceStep.Finished)
        val buy = checkNotNull(shop.buy)
        assertEquals(EngineTraceEvent.Screen(shop.intro), engine.events[0])
        assertEquals(EngineTraceEvent.Menu(shop.menu, listOf('B', 'S', 'L')), engine.events[1])
        assertEquals(EngineTraceEvent.Menu(buy.prompt, listOf('A', 'B', 'C')), engine.events[2])
        assertEquals(EngineTraceEvent.Screen((buy.offerFor(Items.torch)!!.purchase as ShopPurchaseModel.Bulk).quantityPrompt), engine.events[3])
        assertEquals(EngineTraceEvent.Menu(buy.payPrompt, listOf('P', 'N')), engine.events[4])
        assertEquals(
            ShopTransactionStatus.Bought,
            engine.events.filterIsInstance<EngineTraceEvent.ShopTransaction>().single().result.status,
        )
        assertEquals(EngineTraceEvent.Screen(shop.goodbye), engine.events.last())
    }

    @Test
    fun shopTraceRunnerHandlesDropSlotAndSaleQuote() {
        val shop = testMerchantShop()
        val engine = TraceEnginePort()
        val runner = ShopServiceTraceRunner(engine)
        val party = testParty(
            gold = 100,
            inventoryItems = (1..InventorySaveSlotMax).associateWith { Items.rope } + (1 to Items.blanket),
        )

        val session = runner.run(
            ShopServiceSession.start(shop, party, compiledItemRecords(), compiledRealmContent()),
            listOf(
                ShopServiceInput.ContinueIntro,
                ShopServiceInput.Main(ShopMainChoice.Buy),
                ShopServiceInput.BuyItem(Items.blanket),
                ShopServiceInput.ConfirmPurchase(accept = true),
                ShopServiceInput.DropSlot(1),
                ShopServiceInput.Main(ShopMainChoice.Sell),
                ShopServiceInput.SellSlot(1),
                ShopServiceInput.ConfirmSale(accept = true),
            ),
        )

        val transactions = engine.events.filterIsInstance<EngineTraceEvent.ShopTransaction>()
        val quote = engine.events.filterIsInstance<EngineTraceEvent.ShopSellQuote>().single().quote

        assertEquals(ShopTransactionStatus.Sold, session.lastTransaction?.status)
        assertEquals(null, session.party.inventoryItem(1))
        assertEquals(ShopSellQuoteStatus.Quoted, quote.status)
        assertEquals(listOf(ShopTransactionStatus.Bought, ShopTransactionStatus.Sold), transactions.map { it.result.status })
        assertTrue(engine.events.any { it is EngineTraceEvent.AskInventorySlot })
    }

    @Test
    fun shopTerminalAdapterRunsBuyAndSaleFlows() {
        val buyTerminal = FakeTerminalPort("B", "A", "99", "P", "L")
        val buySession = ShopServiceTerminalAdapter(buyTerminal).run(
            ShopServiceSession.start(testMerchantShop(), testParty(gold = 10, torches = 78), compiledItemRecords(), compiledRealmContent()),
        )

        assertEquals(80, buySession.party.resources.torches)
        assertEquals(8, buySession.party.resources.gold)
        assertTrue(buySession.step is ShopServiceStep.Finished)
        assertFalse(buyTerminal.output.any { "Options: B/S/L" in it })
        assertFalse(buyTerminal.output.any { "Options: P/N" in it })
        assertTrue(buyTerminal.output.any { "(A) torch" in it })
        assertTrue(buyTerminal.output.any { "Deal!" in it })

        val saleTerminal = FakeTerminalPort("S", "A", "Y", "L")
        val saleSession = ShopServiceTerminalAdapter(saleTerminal).run(
            ShopServiceSession.start(
                testMerchantShop(),
                testParty(gold = 10, inventoryItems = mapOf(1 to Items.blanket)),
                compiledItemRecords(),
                compiledRealmContent(),
            ),
        )

        assertEquals(25, saleSession.party.resources.gold)
        assertEquals(null, saleSession.party.inventoryItem(1))
        assertTrue(saleTerminal.output.any { "Offer: 15" in it })
        assertTrue(saleTerminal.output.any { "Options: Y/N" in it })
    }

    @Test
    fun shopTerminalAdapterRendersSelectedCatalogOnly() {
        val terminal = FakeTerminalPort("B", "A", "1", "P", "L")
        val session = ShopServiceTerminalAdapter(terminal).run(
            ShopServiceSession.start(
                testMultiCatalogSmithShop(),
                testParty(gold = 1000),
                compiledItemRecords(),
                compiledRealmContent(),
            ),
        )

        assertTrue(session.step is ShopServiceStep.Finished)
        assertEquals(Items.leather, session.party.inventoryItem(1))
        assertTrue(terminal.output.any { "(A)rmor" in it })
        assertTrue(terminal.output.any { "(1) Leather" in it })
        assertFalse(terminal.output.any { "(1) Sword" in it })
        assertFalse(terminal.output.any { "Options: P/N" in it })
    }

    @Test
    fun shopTerminalAdapterAcceptsAuthoredInputAliases() {
        val base = testMerchantShop()
        val buy = checkNotNull(base.buy)
        val shop = base.copy(
            keys = ShopMenuKeysModel(
                buy = inputKey('B', "BUY"),
                sell = inputKey('S', "SELL"),
                leave = inputKey('L', "LEAVE"),
            ),
            buy = buy.copy(
                paymentKeys = ShopPurchaseKeysModel(
                    pay = inputKey('P', "PAY"),
                    decline = inputKey('N', "NONE"),
                ),
                catalogs = buy.catalogs.map { catalog ->
                    catalog.copy(
                        pages = catalog.pages.map { page ->
                            page.copy(
                                entries = page.entries.map { entry ->
                                    if (entry.item == Items.torch) {
                                        entry.copy(binding = inputKey(entry.key, "TORCH"))
                                    } else {
                                        entry
                                    }
                                },
                            )
                        },
                    )
                },
            ),
        )
        val terminal = FakeTerminalPort("BUY", "TORCH", "99", "PAY", "LEAVE")
        val session = ShopServiceTerminalAdapter(terminal).run(
            ShopServiceSession.start(shop, testParty(gold = 10, torches = 78), compiledItemRecords(), compiledRealmContent()),
        )

        assertTrue(session.step is ShopServiceStep.Finished)
        assertEquals(80, session.party.resources.torches)
        assertEquals(8, session.party.resources.gold)
        assertFalse(terminal.output.any { line -> line.startsWith("Options:") && "BUY" in line })
    }

    @Test
    fun shopInterpreterSellsAcceptedItemsAndRejectsWrongShopItems() {
        val shop = testMerchantShop()
        val party = testParty(
            gold = 10,
            inventoryItems = mapOf(1 to Items.blanket, 2 to Items.sword),
        )
        val interpreter = ShopServiceInterpreter(compiledItemRecords(), compiledRealmContent())

        val sold = interpreter.sell(shop, party, slot = 1)
        val rejected = interpreter.sell(shop, party, slot = 2)

        assertEquals(ShopTransactionStatus.Sold, sold.status)
        assertEquals(null, sold.party.inventoryItem(1))
        assertEquals(25, sold.party.resources.gold)
        assertEquals(ShopTransactionStatus.ItemNotAcceptedHere, rejected.status)
    }

    @Test
    fun equipmentActionsSwapInventoryAndEquippedItems() {
        val party = testParty(
            inventoryItems = mapOf(1 to Items.sword, 2 to Items.axe),
        )

        val equippedSword = party.equipFromInventory(
            memberNumber = 1,
            slot = 1,
            kind = EquipmentKind.Weapon,
            itemRecords = compiledItemRecords(),
        )
                as EquipmentActionResult.Equipped
        val equippedAxe = equippedSword.party.equipFromInventory(
            memberNumber = 1,
            slot = 2,
            kind = EquipmentKind.Weapon,
            itemRecords = compiledItemRecords(),
        ) as EquipmentActionResult.Equipped
        val unequipped = equippedAxe.party.unequipToInventory(memberNumber = 1, kind = EquipmentKind.Weapon)
                as EquipmentActionResult.Unequipped

        assertEquals(Items.sword, equippedSword.party.members.single().weapon)
        assertEquals(null, equippedSword.party.inventoryItem(1))
        assertEquals(Items.axe, equippedAxe.party.members.single().weapon)
        assertEquals(Items.sword, equippedAxe.party.inventoryItem(2))
        assertEquals(Items.none, unequipped.party.members.single().weapon)
        assertEquals(Items.axe, unequipped.party.inventoryItem(1))
    }

    @Test
    fun equipmentCommandsExposePromptShapeAndApplyActions() {
        val party = testParty(
            inventoryItems = mapOf(1 to Items.sword),
        )
        val readyPrompt = EquipmentCommand.ReadyWeapon.prompt()
        val removePrompt = EquipmentCommand.RemoveWeapon.prompt()

        val readied = party.applyEquipmentCommand(
            EquipmentCommand.ReadyWeapon,
            memberNumber = 1,
            inventorySlot = 1,
            itemRecords = compiledItemRecords(),
        )
                as EquipmentActionResult.Equipped
        val removed = readied.party.applyEquipmentCommand(EquipmentCommand.RemoveWeapon, memberNumber = 1)
                as EquipmentActionResult.Unequipped

        assertEquals(EquipmentKind.Weapon, readyPrompt.kind)
        assertEquals(MemberChoiceFilter.AliveOnly, readyPrompt.memberFilter)
        assertEquals(InventorySlotChoiceFilter.NonEmpty, readyPrompt.inventoryFilter)
        assertEquals(MemberChoiceFilter.Any, removePrompt.memberFilter)
        assertEquals(null, removePrompt.inventoryFilter)
        assertEquals(Items.sword, readied.party.members.single().weapon)
        assertEquals(Items.none, removed.party.members.single().weapon)
        assertEquals(Items.sword, removed.party.inventoryItem(1))
    }

    @Test
    fun equipmentPromptTraceRunnerRunsReadyAndRemoveCommands() {
        val readyEngine = TraceEnginePort()
        val ready = EquipmentPromptTraceRunner(readyEngine).run(
            EquipmentPromptSession.start(
                EquipmentCommand.ReadyWeapon,
                testParty(inventoryItems = mapOf(1 to Items.sword)),
                compiledItemRecords(),
            ),
            listOf(EquipmentPromptInput.InventorySlot(1), EquipmentPromptInput.Member(1)),
        )
        val removeEngine = TraceEnginePort()
        val removed = EquipmentPromptTraceRunner(removeEngine).run(
            EquipmentPromptSession.start(
                EquipmentCommand.RemoveWeapon,
                testParty(equipment = EquipmentState(Items.sword, Items.none)),
                compiledItemRecords(),
            ),
            listOf(EquipmentPromptInput.Member(1)),
        )

        assertEquals(Items.sword, ready.party.members.single().weapon)
        assertEquals(null, ready.party.inventoryItem(1))
        assertEquals(Items.none, removed.party.members.single().weapon)
        assertEquals(Items.sword, removed.party.inventoryItem(1))
        assertTrue(readyEngine.events[0] is EngineTraceEvent.AskInventorySlot)
        assertTrue(readyEngine.events[1] is EngineTraceEvent.AskMember)
        assertTrue(readyEngine.events[2] is EngineTraceEvent.EquipmentAction)
        assertTrue(removeEngine.events[0] is EngineTraceEvent.AskMember)
    }

    @Test
    fun townLeaveEquipmentPromptReadiesWeaponsThenArmor() {
        val engine = TraceEnginePort()
        val runner = TownLeaveEquipmentTraceRunner(engine)
        val readyCommands = checkNotNull(RylweenCompiledGame.nextDsl.townSystem).leavePrompts

        val session = runner.run(
            TownLeaveEquipmentSession.start(
                testParty(inventoryItems = mapOf(1 to Items.sword, 2 to Items.leather)),
                compiledItemRecords(),
                readyCommands = readyCommands,
            ),
            listOf(
                TownLeaveEquipmentInput.ReadyAnswer(true),
                TownLeaveEquipmentInput.Equipment(EquipmentPromptInput.InventorySlot(1)),
                TownLeaveEquipmentInput.Equipment(EquipmentPromptInput.Member(1)),
                TownLeaveEquipmentInput.ReadyAnswer(true),
                TownLeaveEquipmentInput.Equipment(EquipmentPromptInput.InventorySlot(2)),
                TownLeaveEquipmentInput.Equipment(EquipmentPromptInput.Member(1)),
            ),
        )

        assertTrue(session.step is TownLeaveEquipmentStep.Finished)
        assertEquals(Items.sword, session.party.members.single().weapon)
        assertEquals(Items.leather, session.party.members.single().armor)
        val prompts = EquipmentPromptsModel()
        assertEquals(
            listOf(prompts.readyWeapons, prompts.readyArmors),
            engine.events.filterIsInstance<EngineTraceEvent.Menu>().map { it.prompt },
        )
        assertEquals(2, engine.events.filterIsInstance<EngineTraceEvent.EquipmentAction>().size)
    }

    @Test
    fun townLeaveEquipmentPromptFollowsAuthoredCommandOrder() {
        val engine = TraceEnginePort()
        val runner = TownLeaveEquipmentTraceRunner(engine)
        val prompts = EquipmentPromptsModel()

        val session = runner.run(
            TownLeaveEquipmentSession.start(
                testParty(inventoryItems = mapOf(1 to Items.sword, 2 to Items.leather)),
                compiledItemRecords(),
                readyCommands = listOf(EquipmentCommand.ReadyArmor, EquipmentCommand.ReadyWeapon),
            ),
            listOf(
                TownLeaveEquipmentInput.ReadyAnswer(true),
                TownLeaveEquipmentInput.Equipment(EquipmentPromptInput.InventorySlot(2)),
                TownLeaveEquipmentInput.Equipment(EquipmentPromptInput.Member(1)),
                TownLeaveEquipmentInput.ReadyAnswer(true),
                TownLeaveEquipmentInput.Equipment(EquipmentPromptInput.InventorySlot(1)),
                TownLeaveEquipmentInput.Equipment(EquipmentPromptInput.Member(1)),
            ),
        )

        assertTrue(session.step is TownLeaveEquipmentStep.Finished)
        assertEquals(Items.sword, session.party.members.single().weapon)
        assertEquals(Items.leather, session.party.members.single().armor)
        assertEquals(
            listOf(prompts.readyArmors, prompts.readyWeapons),
            engine.events.filterIsInstance<EngineTraceEvent.Menu>().map { it.prompt },
        )
    }

    @Test
    fun equipmentTerminalAdaptersRunReadyOffAndTownLeavePrompts() {
        val readyCommands = checkNotNull(RylweenCompiledGame.nextDsl.townSystem).leavePrompts
        val readyTerminal = FakeTerminalPort("A", "1")
        val ready = EquipmentPromptTerminalAdapter(readyTerminal).run(
            EquipmentPromptSession.start(
                EquipmentCommand.ReadyWeapon,
                testParty(inventoryItems = mapOf(1 to Items.sword)),
                compiledItemRecords(),
            ),
        )
        val offTerminal = FakeTerminalPort("1")
        val removed = EquipmentPromptTerminalAdapter(offTerminal).run(
            EquipmentPromptSession.start(
                EquipmentCommand.RemoveWeapon,
                testParty(equipment = EquipmentState(Items.sword, Items.none)),
                compiledItemRecords(),
            ),
        )
        val leaveTerminal = FakeTerminalPort("Y", "A", "1", "Y", "B", "1")
        val leave = TownLeaveEquipmentTerminalAdapter(leaveTerminal).run(
            TownLeaveEquipmentSession.start(
                testParty(inventoryItems = mapOf(1 to Items.sword, 2 to Items.leather)),
                compiledItemRecords(),
                readyCommands = readyCommands,
            ),
        )

        assertEquals(Items.sword, ready.party.members.single().weapon)
        assertEquals(Items.none, removed.party.members.single().weapon)
        assertEquals(Items.sword, removed.party.inventoryItem(1))
        assertTrue(leave.step is TownLeaveEquipmentStep.Finished)
        assertEquals(Items.sword, leave.party.members.single().weapon)
        assertEquals(Items.leather, leave.party.members.single().armor)
        assertTrue(readyTerminal.output.any { "Ready what?" in it })
        assertTrue(readyTerminal.output.any { "Options: A" in it })
        assertTrue(offTerminal.output.any { "Who? (1-N)" in it })
        assertEquals(2, leaveTerminal.output.count { it == "Options: Y/N" })
    }

    @Test
    fun inventoryItemUseRunsPotionScriptAndConsumesAfterTarget() {
        val content = RylweenCompiledGame.nextDsl
        val terminal = FakeTerminalPort("A", "1")
        val result = InventoryItemUseTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            InventoryItemUseSession.start(
                runtime = RuntimeSessionState.singleParty(
                    party = testParty(
                        health = 30,
                        hitPoints = 10,
                        inventoryItems = mapOf(1 to Items.redPotion),
                    ),
                    hour = 12,
                    currentTown = null,
                ),
                itemUses = content.itemUses,
                itemRecords = content.itemCatalog,
            ),
        )

        val party = result.session.runtime.activeParty
        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(InventoryItemUseStatus.Used, result.session.lastResult?.status)
        assertEquals(null, party.inventoryItem(1))
        assertEquals(25, party.members.single().hitPoints.current)
        assertTrue(terminal.output.any { "A. Red Potion" in it })
        assertTrue(terminal.output.any { "Who? (1-4)" in it })
        assertTrue(terminal.output.any { "Tester recovers" in it })
    }

    @Test
    fun inventoryItemUseCanReadyWeaponFromSameMenu() {
        val terminal = FakeTerminalPort("A", "1")
        val result = InventoryItemUseTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            InventoryItemUseSession.start(
                runtime = RuntimeSessionState.singleParty(
                    party = testParty(inventoryItems = mapOf(1 to Items.sword)),
                    hour = 12,
                    currentTown = null,
                ),
                itemUses = RylweenCompiledGame.nextDsl.itemUses,
                itemRecords = compiledItemRecords(),
            ),
        )

        val party = result.session.runtime.activeParty
        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(InventoryItemUseStatus.Equipped, result.session.lastResult?.status)
        assertEquals(Items.sword, party.members.single().weapon)
        assertEquals(null, party.inventoryItem(1))
        assertTrue(terminal.output.any { "A. Sword" in it })
        assertTrue(terminal.output.any { "Who? (1-N)" in it })
    }

    @Test
    fun terminalAdaptersReturnUnfinishedSessionOnInvalidInput() {
        val invalidShop = ShopServiceTerminalAdapter(FakeTerminalPort("X")).runUntilBlocked(
            ShopServiceSession.start(testMerchantShop(), testParty(gold = 10), compiledItemRecords(), compiledRealmContent()),
        )
        val missingShopInput = ShopServiceTerminalAdapter(FakeTerminalPort()).runUntilBlocked(
            ShopServiceSession.start(testMerchantShop(), testParty(gold = 10), compiledItemRecords(), compiledRealmContent()),
        )
        val invalidTownLeave = TownLeaveEquipmentTerminalAdapter(FakeTerminalPort("X")).runUntilBlocked(
            TownLeaveEquipmentSession.start(
                testParty(inventoryItems = mapOf(1 to Items.sword)),
                compiledItemRecords(),
                readyCommands = checkNotNull(RylweenCompiledGame.nextDsl.townSystem).leavePrompts,
            ),
        )

        assertTrue(invalidShop.session.step is ShopServiceStep.MainMenu)
        assertEquals(
            TerminalInputRejection("X", listOf("B", "S", "L")),
            (invalidShop.status as TerminalRunStatus.Waiting).rejection,
        )
        assertTrue(missingShopInput.session.step is ShopServiceStep.MainMenu)
        assertEquals(null, (missingShopInput.status as TerminalRunStatus.Waiting).rejection)
        assertTrue(invalidTownLeave.session.step is TownLeaveEquipmentStep.ReadyQuestion)
        assertEquals(
            TerminalInputRejection("X", listOf("Y", "N")),
            (invalidTownLeave.status as TerminalRunStatus.Waiting).rejection,
        )
    }

    @Test
    fun townTerminalAdapterDispatchesShopAndLeaveFlows() {
        val terminal = FakeTerminalPort("M", "B", "A", "99", "P", "L", "L")
        val session = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.merchant),
                townSystem = testTownSystem(Buildings.merchant),
                scenes = listOf(SceneModel(Scenes.Towne.merchant, listOf(Op.ShopService(testMerchantShop())))),
                party = testParty(gold = 10, torches = 78),
                currentHour = 12,
                itemRecords = compiledItemRecords(),
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertTrue(session.session.step is TownSessionStep.Finished)
        assertEquals(80, session.session.party.resources.torches)
        assertEquals(8, session.session.party.resources.gold)
        assertTrue(terminal.output.any { "(M)erchant" in it })
        assertTrue(terminal.output.any { "(A) torch" in it })
    }

    @Test
    fun townTerminalAdapterRunsLeaveEquipmentPromptBeforeExit() {
        val terminal = FakeTerminalPort("L", "Y", "A", "1", "Y", "B", "1")
        val session = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.merchant),
                townSystem = testTownSystem(Buildings.merchant),
                scenes = emptyList(),
                party = testParty(inventoryItems = mapOf(1 to Items.sword, 2 to Items.leather)),
                currentHour = 12,
                itemRecords = compiledItemRecords(),
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertTrue(session.session.step is TownSessionStep.Finished)
        assertEquals(Items.sword, session.session.party.members.single().weapon)
        assertEquals(Items.leather, session.session.party.members.single().armor)
        assertEquals(2, terminal.output.count { it == "Options: Y/N" })
    }

    @Test
    fun townTerminalAdapterRunsNativeServiceBridgeAndReturnsToMenu() {
        val terminal = FakeTerminalPort("C", "L")
        val session = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.church),
                townSystem = testTownSystem(Buildings.church),
                scenes = listOf(
                    SceneModel(
                        Scenes.Towne.church,
                        listOf(
                            Op.Screen(inlineText("Church body")),
                            Op.View(ViewCommand.ClearText),
                        ),
                    ),
                ),
                party = testParty(gold = 10),
                currentHour = 12,
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertTrue(session.session.step is TownSessionStep.Finished)
        assertTrue(terminal.output.any { "Church body" in it })
        assertTrue(terminal.output.none { "Service: church" in it })
        assertTrue(terminal.output.none { it.startsWith("Native:") })
        assertEquals(TownSessionResultStatus.NativeServiceFinished, session.session.lastResult?.status)
    }

    @Test
    fun townServiceBridgeAcceptsAuthoredScriptMenuAliases() {
        val service = BuildingServiceModel(
            building = Buildings.church,
            action = keyAction("enter_church"),
            scene = sceneId("test.script_menu_alias"),
            opensAt = null,
            closesAt = null,
            nativeServices = emptyList(),
        )
        val scene = SceneModel(
            service.scene,
            listOf(
                Op.Menu(
                    prompt = inlineText("(P)ay"),
                    options = listOf(
                        MenuOption(
                            inputKey('P', "PAY"),
                            listOf(Op.Set(LValueAst.PartyScalar("gold"), IntAst.Lit(7))),
                        ),
                    ),
                    defaultOps = emptyList(),
                ),
            ),
        )
        val result = StatefulTownServiceBridge(FakeTerminalPort("PAY")).run(
            service,
            scene,
            TownServiceContinuation.start(
                RuntimeSessionState.singleParty(
                    party = testParty(gold = 1),
                    hour = 12,
                    currentTown = Towns.murn.id,
                ),
            ),
        )

        assertTrue(result.status is TownServiceBridgeStatus.Finished)
        assertEquals(7, result.party.resources.gold)
    }

    @Test
    fun townTerminalAdapterAcceptsAuthoredInputAliases() {
        val baseSystem = testTownSystem(Buildings.church)
        val townSystem = baseSystem.copy(
            menu = baseSystem.menu.map { action ->
                when {
                    action.building == Buildings.church -> action.copy(binding = inputKey(action.key, "CHURCH"))
                    action.endsMenu -> action.copy(binding = inputKey(action.key, "LEAVE"))
                    else -> action
                }
            },
        )
        val terminal = FakeTerminalPort("CHURCH", "LEAVE")
        val session = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.church),
                townSystem = townSystem,
                scenes = listOf(SceneModel(Scenes.Towne.church, listOf(Op.Screen(inlineText("Church body"))))),
                party = testParty(gold = 10),
                currentHour = 12,
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertTrue(session.session.step is TownSessionStep.Finished)
        assertTrue(terminal.output.any { "Church body" in it })
    }

    @Test
    fun townServiceBridgeConsumesServiceInputAndMutatesPartyState() {
        val terminal = FakeTerminalPort("C", "H", "1", "L", "L")
        val serviceScene = SceneModel(
            Scenes.Towne.church,
            listOf(
                Op.Loop(
                    listOf(
                        Op.Menu(
                            inlineText("Church menu"),
                            options = listOf(
                                MenuOption(
                                    'H',
                                    listOf(
                                        Op.AskMember(inlineText("Who?"), "member", MemberChoiceFilter.Any),
                                        Op.Set(
                                            LValueAst.MemberField(IntAst.Var("member"), "hit_points"),
                                            IntAst.Lit(10),
                                        ),
                                        Op.Sub(LValueAst.PartyScalar("gold"), IntAst.Lit(3)),
                                    ),
                                ),
                                MenuOption('L', listOf(Op.Break("leave test church"))),
                            ),
                            defaultOps = emptyList(),
                        ),
                    ),
                ),
            ),
        )

        val session = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.church),
                townSystem = testTownSystem(Buildings.church),
                scenes = listOf(serviceScene),
                party = testParty(gold = 10, hitPoints = 2),
                currentHour = 12,
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertEquals(10, session.session.party.members.single().hitPoints.current)
        assertEquals(7, session.session.party.resources.gold)
        assertEquals(TownSessionResultStatus.NativeServiceFinished, session.session.lastResult?.status)
    }

    @Test
    fun townServiceBridgeMapsInventoryPromptKeysToRequestedSlots() {
        val terminal = FakeTerminalPort("C", "B", "L")
        val serviceScene = SceneModel(
            Scenes.Towne.church,
            listOf(
                Op.AskInventorySlot(
                    inlineText("Which slot?"),
                    "slot",
                    firstKey = 'A',
                    slots = 3..5,
                    filter = InventorySlotChoiceFilter.NonEmpty,
                ),
                Op.Set(LValueAst.PartyScalar("gold"), IntAst.Var("slot")),
            ),
        )

        val session = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.church),
                townSystem = testTownSystem(Buildings.church),
                scenes = listOf(serviceScene),
                party = testParty(inventoryItems = mapOf(4 to Items.ration)),
                currentHour = 12,
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertEquals(4, session.session.party.resources.gold)
    }

    @Test
    fun townServiceBridgePersistsRealmMutationsInTownRuntime() {
        val terminal = FakeTerminalPort("C", "L")
        val serviceScene = SceneModel(
            Scenes.Towne.church,
            listOf(Op.Set(LValueAst.RealmScalar("hour"), IntAst.Lit(5))),
        )

        val session = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.church),
                townSystem = testTownSystem(Buildings.church),
                scenes = listOf(serviceScene),
                party = testParty(gold = 10),
                currentHour = 12,
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertEquals(5, session.session.realm.hour)
        assertEquals(5, (session.session.step as TownSessionStep.Finished).runtime.realm.hour)
    }

    @Test
    fun townTerminalAdapterRunsAuthoredChurchHealingBranch() {
        val session = TownSessionTerminalAdapter(FakeTerminalPort("C", "H", "1", "P", "L", "L")).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.church),
                townSystem = testTownSystem(Buildings.church),
                scenes = listOf(testChurchScene()),
                party = testParty(gold = 100, hitPoints = 2, health = 10),
                currentHour = 12,
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertEquals(10, session.session.party.members.single().hitPoints.current)
        assertEquals(44, session.session.party.resources.gold)
    }

    @Test
    fun townTerminalAdapterRunsAuthoredChurchHealingBrokeBranch() {
        val session = TownSessionTerminalAdapter(FakeTerminalPort("C", "H", "1", "P", "L", "L")).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.church),
                townSystem = testTownSystem(Buildings.church),
                scenes = listOf(testChurchScene()),
                party = testParty(gold = 10, hitPoints = 2, health = 10),
                currentHour = 12,
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertEquals(2, session.session.party.members.single().hitPoints.current)
        assertEquals(10, session.session.party.resources.gold)
    }

    @Test
    fun townTerminalAdapterRunsAuthoredChurchEnergizeBranch() {
        val session = TownSessionTerminalAdapter(FakeTerminalPort("C", "E", "1", "P", "L", "L")).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.church),
                townSystem = testTownSystem(Buildings.church),
                scenes = listOf(testChurchScene()),
                party = testParty(
                    gold = 100,
                    level = 2,
                    pcClass = PcClass.Mage,
                    mana = 3,
                    wisdom = 10,
                ),
                currentHour = 12,
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertEquals(10, session.session.party.members.single().mana.current)
        assertEquals(72, session.session.party.resources.gold)
    }

    @Test
    fun townTerminalAdapterRunsAuthoredChurchRaiseBranch() {
        val session = TownSessionTerminalAdapter(FakeTerminalPort("C", "R", "1", "P", "L", "L")).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.church),
                townSystem = testTownSystem(Buildings.church),
                scenes = listOf(testChurchScene()),
                party = testParty(
                    gold = 1000,
                    hitPoints = 0,
                    health = 10,
                    dead = true,
                    memberCount = 0,
                ),
                currentHour = 12,
            ),
        )

        val member = session.session.party.members.single()
        assertTrue(session.status is TerminalRunStatus.Finished)
        assertEquals(false, member.dead)
        assertEquals(1, member.hitPoints.current)
        assertEquals(1, session.session.party.counters.memberCount)
        assertEquals(350, session.session.party.resources.gold)
    }

    @Test
    fun townTerminalAdapterKeepsAuthoredChurchLoopAfterStopBranch() {
        val terminal = FakeTerminalPort("C", "H", "1", "L", "L")
        val session = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.church),
                townSystem = testTownSystem(Buildings.church),
                scenes = listOf(testChurchScene()),
                party = testParty(gold = 100, hitPoints = 10, health = 10),
                currentHour = 12,
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertEquals(2, terminal.output.count { it == "Options: H/E/R/L" })
        assertEquals(100, session.session.party.resources.gold)
    }

    @Test
    fun townTerminalAdapterRunsAuthoredInnSupperBranch() {
        val session = TownSessionTerminalAdapter(FakeTerminalPort("I", "Y", "", "N", "N", "L", "L"))
            .runUntilBlocked(
                TownSession.start(
                    town = testTown(Buildings.inn),
                    townSystem = testTownSystem(Buildings.inn),
                    scenes = listOf(testInnScene()),
                    party = testParty(gold = 100, level = 2, hitPoints = 1, health = 10),
                    currentHour = 12,
                ),
            )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertEquals(7, session.session.party.members.single().hitPoints.current)
        assertEquals(40, session.session.party.resources.gold)
    }

    @Test
    fun townTerminalAdapterRunsAuthoredInnDrinkBranch() {
        val session = TownSessionTerminalAdapter(FakeTerminalPort("I", "N", "Y", "D", "N", "L", "L"))
            .runUntilBlocked(
                TownSession.start(
                    town = testTown(Buildings.inn),
                    townSystem = testTownSystem(Buildings.inn),
                    scenes = listOf(testInnScene()),
                    party = testParty(gold = 20, hitPoints = 1, health = 10),
                    currentHour = 12,
                ),
            )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertEquals(5, session.session.party.members.single().hitPoints.current)
        assertEquals(13, session.session.party.resources.gold)
    }

    @Test
    fun townTerminalAdapterRunsAuthoredInnLodgingBranchWithRealmAndPartyRecovery() {
        val session = TownSessionTerminalAdapter(FakeTerminalPort("I", "N", "N", "Y", "T", "", "L"))
            .runUntilBlocked(
                TownSession.start(
                    town = testTown(Buildings.inn),
                    townSystem = testTownSystem(Buildings.inn),
                    scenes = listOf(testInnScene()),
                    party = testParty(
                        gold = 100,
                        members = listOf(
                            testMember(name = "A", hitPoints = 2, health = 10),
                            testMember(name = "B", hitPoints = 8, health = 12),
                            testMember(name = "C", hitPoints = 0, health = 10, dead = true),
                        ),
                    ),
                    currentHour = 23,
                ),
            )

        val members = session.session.party.members
        assertTrue(session.status is TerminalRunStatus.Finished)
        assertEquals(85, session.session.party.resources.gold)
        assertEquals(14, session.session.party.resources.restedHours)
        assertEquals(5, session.session.realm.hour)
        assertEquals(listOf(4, 11, 0), members.map { it.hitPoints.current })
        assertEquals(listOf(false, false, true), members.map { it.dead })
    }

    @Test
    fun townTerminalAdapterRunsInnChitchatThroughReusableSpeakSession() {
        val terminal = FakeTerminalPort("I", "N", "N", "N", "C", "1", "C", "B", "L", "L")
        val session = TownSessionTerminalAdapter(
            terminal,
            serviceBridge = StatefulTownServiceBridge(terminal, speakContent = testSpeakContent()),
        ).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.inn),
                townSystem = testTownSystem(Buildings.inn),
                scenes = listOf(testInnScene()),
                party = testParty(),
                currentHour = 12,
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertTrue(terminal.output.any { "Speak with whom?" in it })
        assertTrue(terminal.output.any { "1. Dargoth, tavernkeeper" in it })
        assertTrue(terminal.output.any { "- You look well today," in it })
        assertTrue(terminal.output.any { "- You've had too much" in it })
        assertEquals(listOf(NpcVisitState(Npcs.dargoth, true)), session.session.party.visitedNpcs)
    }

    @Test
    fun townTerminalAdapterRunsTavernSpeakQuestionThroughReusableSpeakSession() {
        val terminal = FakeTerminalPort("T", "S", "1", "Q", "P", "B", "L", "L")
        val session = TownSessionTerminalAdapter(
            terminal,
            serviceBridge = StatefulTownServiceBridge(terminal, speakContent = testSpeakContent()),
        ).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.tavern),
                townSystem = testTownSystem(Buildings.tavern),
                scenes = listOf(testTavernSpeakRumourScene()),
                party = testParty(),
                currentHour = 12,
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertTrue(terminal.output.any { "1. Yog, innkeeper" in it })
        assertTrue(terminal.output.any { "Ask people at the inn." in it })
        assertEquals(listOf(NpcVisitState(Npcs.yog, true)), session.session.party.visitedNpcs)
    }

    @Test
    fun compiledGameSpeakRunsAuthoredBargirlLodgingQuestionAction() {
        val content = RylweenCompiledGame.nextDsl
        val renderer = compiledTextRenderer()
        val terminal = FakeTerminalPort("1", "Q", "L", "Y", "B")
        val scene = GameBuilder().apply {
            scene(sceneId("test.bargirl_lodging")) {
                speak(Buildings.tavern)
            }
        }.build().scenes.single()
        val service = BuildingServiceModel(
            building = Buildings.tavern,
            action = keyAction("enter_tavern"),
            scene = scene.id,
            opensAt = null,
            closesAt = null,
            nativeServices = emptyList(),
        )
        val result = StatefulTownServiceBridge(
            terminal,
            speakContent = compiledSpeakContent(),
            battleContent = content.runtimeContent(),
            renderer = renderer,
        ).run(
            service,
            scene,
            TownServiceContinuation.start(
                RuntimeSessionState.singleParty(
                    party = testParty(gold = 200, level = 2, hitPoints = 5, health = 10),
                    hour = 12,
                    currentTown = Towns.thord.id,
                ),
            ),
        )

        terminal.writeScriptEvents(result.events, renderer, result.runtime)

        assertTrue(result.status is TownServiceBridgeStatus.Finished)
        assertEquals(100, result.party.resources.gold)
        assertEquals(4, result.party.members.single().hitPoints.current)
        assertTrue(terminal.output.any { "1. Deanna, bargirl" in it })
        assertTrue(terminal.output.any { "  upstairs for 100" in it })
        assertTrue(terminal.output.any { "You spend a very" in it })
    }

    @Test
    fun townTerminalAdapterRunsTavernRumourThroughStandardRumourTableExclusions() {
        val terminal = FakeTerminalPort("T", "R", "", "L", "L")
        val session = TownSessionTerminalAdapter(
            terminal,
            serviceBridge = StatefulTownServiceBridge(terminal, speakContent = testSpeakContent()),
        ).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.tavern),
                townSystem = testTownSystem(Buildings.tavern),
                scenes = listOf(testTavernSpeakRumourScene()),
                party = testParty(flags = listOf(Story.garth.bandits.recovered)),
                currentHour = 12,
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertTrue(terminal.output.any { "Ydarg waits in the south." in it })
        assertTrue(terminal.output.none { "A merchant needs bandits gone." in it })
    }

    @Test
    fun townTerminalAdapterUsesSeededRumourSelectionAndAdvancesRealmSeed() {
        val terminal = FakeTerminalPort("T", "R", "", "L", "L")
        val session = TownSessionTerminalAdapter(
            terminal,
            serviceBridge = StatefulTownServiceBridge(terminal, speakContent = testSpeakContent()),
        ).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.tavern),
                townSystem = testTownSystem(Buildings.tavern),
                scenes = listOf(testTavernSpeakRumourScene()),
                party = testParty(),
                currentHour = 12,
                rngState = 1L,
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertTrue(terminal.output.any { "Ydarg waits in the south." in it })
        assertTrue(terminal.output.none { "A merchant needs bandits gone." in it })
        assertEquals(33554433L, session.session.runtime.realm.rngState)
    }

    @Test
    fun townTerminalAdapterRunsTavernScruffyRandomItemOfferPurchase() {
        val terminal = FakeTerminalPort("T", "B", "L", "L", "N")
        val session = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.tavern),
                townSystem = testTownSystem(Buildings.tavern),
                scenes = listOf(testTavernRandomOfferScene()),
                party = testParty(gold = 2000),
                currentHour = 12,
                itemRecords = compiledItemRecords(),
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertEquals(200, session.session.party.resources.gold)
        assertEquals(Items.magicDagger, session.session.party.inventoryItem(1))
        assertTrue(terminal.output.any { "  Magic Dagger" in it })
        assertTrue(terminal.output.any { "  low price of 1800" in it })
        assertTrue(terminal.output.any { "- Deal!!!" in it })
    }

    @Test
    fun townTerminalAdapterUsesSeededScruffyItemSelectionAndAdvancesRealmSeed() {
        val terminal = FakeTerminalPort("T", "B", "L", "L", "N")
        val session = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.tavern),
                townSystem = testTownSystem(Buildings.tavern),
                scenes = listOf(testTavernRandomOfferScene()),
                party = testParty(gold = 2000),
                currentHour = 12,
                rngState = 1L,
                itemRecords = compiledItemRecords(),
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertEquals(100, session.session.party.resources.gold)
        assertEquals(Items.greenGem, session.session.party.inventoryItem(1))
        assertEquals(33554433L, session.session.runtime.realm.rngState)
        assertTrue(terminal.output.any { "  Green Gem" in it })
    }

    @Test
    fun townServiceBridgeResumesRandomItemOfferDropWithoutReplayingChoice() {
        val start = TownSession.start(
            town = testTown(Buildings.tavern),
            townSystem = testTownSystem(Buildings.tavern),
            scenes = listOf(testTavernRandomOfferScene()),
            party = testParty(
                gold = 2000,
                inventoryItems = (1..InventorySaveSlotMax).associateWith { Items.rope },
            ),
            currentHour = 12,
            itemRecords = compiledItemRecords(),
        )

        val blocked = TownSessionTerminalAdapter(FakeTerminalPort("T", "B")).runUntilBlocked(start)
        val blockedStep = blocked.session.step as TownSessionStep.NativeService

        assertTrue(blocked.status is TerminalRunStatus.Waiting)
        assertEquals(2000, blocked.session.party.resources.gold)
        assertEquals(Items.rope, blocked.session.party.inventoryItem(1))
        assertTrue(blockedStep.continuation?.randomItemOfferSession?.step is RandomItemOfferStep.DropSlot)

        val resumed = TownSessionTerminalAdapter(FakeTerminalPort("A", "L", "L", "N")).runUntilBlocked(blocked.session)

        assertTrue(resumed.status is TerminalRunStatus.Finished)
        assertEquals(200, resumed.session.party.resources.gold)
        assertEquals(Items.magicDagger, resumed.session.party.inventoryItem(1))
    }

    @Test
    fun townServiceBridgeReturnsWaitingOnInvalidServiceInput() {
        val terminal = FakeTerminalPort("C", "X")
        val serviceScene = SceneModel(
            Scenes.Towne.church,
            listOf(
                Op.Menu(
                    inlineText("Church menu"),
                    options = listOf(MenuOption('L', listOf(Op.Break("leave test church")))),
                    defaultOps = emptyList(),
                ),
            ),
        )

        val result = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.church),
                townSystem = testTownSystem(Buildings.church),
                scenes = listOf(serviceScene),
                party = testParty(gold = 10),
                currentHour = 12,
            ),
        )

        val nativeStep = result.session.step
        assertTrue(nativeStep is TownSessionStep.NativeService)
        assertTrue(nativeStep.continuation != null)
        assertEquals(
            TerminalInputRejection("X", listOf("L")),
            (result.status as TerminalRunStatus.Waiting).rejection,
        )
    }

    @Test
    fun townServiceBridgeResumesContinuationWithoutReplayingSideEffects() {
        val serviceScene = SceneModel(
            Scenes.Towne.church,
            listOf(
                Op.Sub(LValueAst.PartyScalar("gold"), IntAst.Lit(3)),
                Op.AskMember(inlineText("Who?"), "member", MemberChoiceFilter.Any),
                Op.WaitInput("pause after member choice"),
                Op.Set(
                    LValueAst.MemberField(IntAst.Var("member"), "hit_points"),
                    IntAst.Lit(8),
                ),
                Op.Sub(LValueAst.PartyScalar("gold"), IntAst.Lit(2)),
            ),
        )
        val start = TownSession.start(
            town = testTown(Buildings.church),
            townSystem = testTownSystem(Buildings.church),
            scenes = listOf(serviceScene),
            party = testParty(gold = 10, hitPoints = 2),
            currentHour = 12,
        )

        val blocked = TownSessionTerminalAdapter(FakeTerminalPort("C", "1")).runUntilBlocked(start)
        val blockedStep = blocked.session.step as TownSessionStep.NativeService

        assertTrue(blocked.status is TerminalRunStatus.Waiting)
        assertEquals(7, blocked.session.party.resources.gold)
        assertEquals(2, blocked.session.party.members.single().hitPoints.current)
        assertEquals(1, blockedStep.continuation?.locals?.get("member"))

        val resumed = TownSessionTerminalAdapter(FakeTerminalPort("", "L")).runUntilBlocked(blocked.session)

        assertTrue(resumed.status is TerminalRunStatus.Finished)
        assertEquals(5, resumed.session.party.resources.gold)
        assertEquals(8, resumed.session.party.members.single().hitPoints.current)
    }

    @Test
    fun townServiceBridgeResumesContinuationWithRealmMutations() {
        val serviceScene = SceneModel(
            Scenes.Towne.church,
            listOf(
                Op.Set(LValueAst.RealmScalar("hour"), IntAst.Lit(6)),
                Op.WaitInput("pause after hour change"),
                Op.Set(LValueAst.RealmScalar("hour"), IntAst.Lit(7)),
            ),
        )
        val start = TownSession.start(
            town = testTown(Buildings.church),
            townSystem = testTownSystem(Buildings.church),
            scenes = listOf(serviceScene),
            party = testParty(gold = 10),
            currentHour = 12,
        )

        val terminal = FakeTerminalPort("C")
        val blocked = TownSessionTerminalAdapter(
            terminal,
            renderer = TerminalTextRenderer(prompts = GamePromptsModel(pauseVerbs = listOf("Hammer"))),
        ).runUntilBlocked(start)
        val blockedStep = blocked.session.step as TownSessionStep.NativeService

        assertTrue(blocked.status is TerminalRunStatus.Waiting)
        assertEquals(6, blocked.session.realm.hour)
        assertEquals(6, blockedStep.continuation?.runtime?.realm?.hour)
        assertTrue("(Hammer RETURN key)" in terminal.output)

        val resumed = TownSessionTerminalAdapter(FakeTerminalPort("", "L")).runUntilBlocked(blocked.session)

        assertTrue(resumed.status is TerminalRunStatus.Finished)
        assertEquals(7, resumed.session.realm.hour)
    }

    @Test
    fun townTerminalAdapterReturnsUnchangedSessionOnInvalidMenuInput() {
        val session = TownSession.start(
            town = testTown(Buildings.merchant),
            townSystem = testTownSystem(Buildings.merchant),
            scenes = listOf(SceneModel(Scenes.Towne.merchant, listOf(Op.ShopService(testMerchantShop())))),
            party = testParty(gold = 10),
            currentHour = 12,
        )
        val result = TownSessionTerminalAdapter(FakeTerminalPort("X")).runUntilBlocked(session)

        assertTrue(result.session.step is TownSessionStep.Menu)
        assertEquals(session.party, result.session.party)
        assertEquals(
            TerminalInputRejection("X", listOf("M", "L")),
            (result.status as TerminalRunStatus.Waiting).rejection,
        )
    }

    @Test
    fun validationRejectsBreakOutsideLoop() {
        val content = GameBuilder().apply {
            scene(sceneId("bad.break")) {
                breakLoop("outside loop")
            }
        }.build()

        assertFailsWith<IllegalStateException> {
            validateAuthoredContent(content)
        }
    }

    @Test
    fun validationRejectsDuplicateStaticScreens() {
        val content = GameBuilder().apply {
            visuals {
                staticScreens {
                    image(StaticScreens.title, Images.Static.title)
                    resource(StaticScreens.title, "score.txt")
                }
            }
        }.build()

        assertFailsWith<IllegalStateException> {
            validateAuthoredContent(content)
        }
    }

    @Test
    fun validationRejectsStaticScreenWithoutSingleSource() {
        val empty = GameBuilder().apply {
            visuals {
                staticScreens {
                    screen(StaticScreens.title) {}
                }
            }
        }.build()
        val conflicting = GameBuilder().apply {
            visuals {
                staticScreens {
                    screen(StaticScreens.title) {
                        image(Images.Static.title)
                        resource("score.txt")
                    }
                }
            }
        }.build()

        assertFailsWith<IllegalStateException> {
            validateAuthoredContent(empty)
        }
        assertFailsWith<IllegalStateException> {
            validateAuthoredContent(conflicting)
        }
    }

    @Test
    fun validationRejectsMalformedWorldMaps() {
        val duplicate = GameBuilder().apply {
            terminal {
                terrain(Terrain.plain, glyph = "..", color = PaletteColors.yellow)
            }
            worldMap(Maps.wilderness) {
                layer(MapLayers.terrain) { rows("ss") }
            }
            worldMap(Maps.wilderness) {
                layer(MapLayers.terrain) { rows("ss") }
            }
        }.build()
        val unknownTile = GameBuilder().apply {
            terminal {
                terrain(Terrain.plain, glyph = "..", color = PaletteColors.yellow)
            }
            worldMap(Maps.wilderness) {
                layer(MapLayers.terrain) { rows("sx") }
            }
        }.build()

        assertFailsWith<IllegalStateException> {
            validateAuthoredContent(duplicate)
        }
        assertFailsWith<IllegalStateException> {
            validateAuthoredContent(unknownTile)
        }
    }

    @Test
    fun compiledGameHarnessExposesNextRylweenToRuntimeTests() {
        val game = RylweenCompiledGame.nextRylween
        val content = RylweenCompiledGame.nextDsl

        val result = TownSessionTerminalAdapter(FakeTerminalPort("C", "L", "L")).runUntilBlocked(
            compiledTownSession(party = game.states.single { it.id == States.freshReplay }.parties.single()),
        )

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(Towns.thord.id, result.session.realm.currentTown)
        assertEquals(Scenes.Towne.church, content.scenes.single { it.id == Scenes.Towne.church }.id)
    }

    @Test
    fun compiledGameDeclaresFirstDungeonEventScenarios() {
        val dungeons = RylweenCompiledGame.nextDsl.dungeons
        val randomCave = dungeons.scenarios.single { it.dungeon == Dungeons.randomCave }
        val fireShield = dungeons.scenarios.single { it.dungeon == Dungeons.fireShieldCave }
        val fireShieldEvent = dungeons.events.single { it.id == DungeonEvents.fireShield }
        val weakEncounter = dungeons.events.single { it.id == DungeonEvents.weakEncounter }

        assertEquals(null, randomCave.levelRange)
        assertEquals(2, randomCave.randomEvents.single { it.event == DungeonEvents.weakEncounter }.weight)
        assertEquals(2, fireShield.randomEvents.single { it.event == DungeonEvents.weakEncounter }.weight)
        assertEquals(DungeonEvents.fireShieldGuardian, fireShield.fixedLevelEvents[19])
        assertEquals(DungeonEvents.fireShield, fireShield.fixedLevelEvents[20])
        assertEquals(DungeonEventDisposition.Recurring, weakEncounter.disposition)
        assertEquals(EncounterTiers.weak, weakEncounter.randomEncounterTier)
        assertTrue(fireShieldEvent.ops.anyNested { op ->
            op is Op.AchieveMilestone && op.milestone == Milestones.fireShield
        })
        assertTrue(fireShieldEvent.ops.anyNested { op ->
            op is Op.SetFlag && op.slug == Story.hermit.trollTooth.slug &&
                op.nativeValue == Story.hermit.trollTooth.fireShieldAcquired.native
        })
    }

    @Test
    fun compiledPriestTeleportUsesSemanticPromptAndMovementOps() {
        val teleport = RylweenCompiledGame.nextDsl.spells.single { it.id == Spells.Priest.teleport }

        assertTrue(teleport.ops.anyNested { it is Op.AskKey })
        assertTrue(teleport.ops.anyNested { it is Op.TeleportParty })
    }

    @Test
    fun compiledGameTownRunsAuthoredMerchantTorchPurchase() {
        val terminal = FakeTerminalPort("M", "B", "A", "99", "P", "L", "L")
        val result = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            compiledTownSession(party = testParty(gold = 10, torches = 78)),
        )

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(80, result.session.party.resources.torches)
        assertEquals(8, result.session.party.resources.gold)
        assertTrue(terminal.output.any { "(A) Torch" in it })
        assertTrue(terminal.output.any { "Deal!" in it })
    }

    @Test
    fun compiledGameTownRunsAuthoredSmithSale() {
        val terminal = FakeTerminalPort("S", "S", "A", "Y", "L", "L")
        val result = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            compiledTownSession(party = testParty(inventoryItems = mapOf(1 to Items.sword))),
        )

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(null, result.session.party.inventoryItem(1))
        assertEquals(80, result.session.party.resources.gold)
        assertTrue(terminal.output.any { "Ok! Give ya 80!" in it })
    }

    @Test
    fun compiledGameTownRunsAuthoredTavernScruffyOffer() {
        val terminal = FakeTerminalPort("T", "B", "L", "L", "N")
        val result = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            compiledTownSession(party = testParty(gold = 2000)),
        )

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(200, result.session.party.resources.gold)
        assertEquals(Items.magicDagger, result.session.party.inventoryItem(1))
        assertTrue(terminal.output.any { "  Magic Dagger" in it })
    }

    @Test
    fun compiledGameTownRunsAuthoredTavernScruffyOfferWithSeededSelection() {
        val terminal = FakeTerminalPort("T", "B", "L", "L", "N")
        val result = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            compiledTownSession(party = testParty(gold = 2000), rngState = 126L),
        )

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(100, result.session.party.resources.gold)
        assertEquals(Items.greenGem, result.session.party.inventoryItem(1))
        assertEquals(141898022896021606L, result.session.runtime.realm.rngState)
        assertTrue(terminal.output.any { "  Green Gem" in it })
    }

    @Test
    fun compiledGameTownRunsAuthoredTavernDrinkOrder() {
        val terminal = FakeTerminalPort("T", "N", "O", "A", "L", "L")
        val result = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            compiledTownSession(
                party = testParty(
                    gold = 10,
                    members = listOf(
                        testMember(name = "Alive", hitPoints = 1, health = 10),
                        testMember(name = "Dead", hitPoints = 0, health = 10, dead = true),
                    ),
                ),
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(8, result.session.party.resources.gold)
        assertEquals(listOf(3, 2), result.session.party.members.map { it.hitPoints.current })
        assertEquals(listOf(false, true), result.session.party.members.map { it.dead })
        assertTrue(terminal.output.any { "AAH! (your health" in it })
    }

    @Test
    fun compiledGameTownRunsAuthoredTavernTravellerStory() {
        val terminal = FakeTerminalPort("T", "N", "T", "1", "T", "", "L", "L")
        val result = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            compiledTownSession(
                party = testParty(
                    members = listOf(testMember(name = "Borin", charisma = 10)),
                ),
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(7, result.session.party.members.single().charisma)
        assertTrue(terminal.output.any { "Niklas, the keeper" in it })
        assertTrue(terminal.output.any { "Who wants to tell" in it })
        assertTrue(terminal.output.any { "a story? (1-4)" in it })
        assertTrue(terminal.output.any { "Most of the crowd" in it })
        assertTrue(terminal.output.any { "beg you to stop." in it })
    }

    @Test
    fun compiledGameTownRunsAuthoredTavernTravellerStoryWithSeededScore() {
        val terminal = FakeTerminalPort("T", "T", "1", "T", "", "L", "L")
        val result = TownSessionTerminalAdapter(terminal).runUntilBlocked(
            compiledTownSession(
                party = testParty(
                    members = listOf(testMember(name = "Borin", charisma = 10)),
                ),
                rngState = 4L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(9, result.session.party.members.single().charisma)
        assertEquals(4504699139039237L, result.session.runtime.realm.rngState)
        assertTrue(terminal.output.any { "The mob is bored." in it })
    }

    @Test
    fun compiledGameTownRunsAuthoredTavernRumourQuietLimit() {
        val terminal = FakeTerminalPort("T", "N", "R", "", "R", "", "R", "", "R", "", "R", "", "L", "L")
        val result = TownSessionTerminalAdapter(
            terminal,
            serviceBridge = StatefulTownServiceBridge(
                terminal,
                speakContent = compiledSpeakContent(),
                battleContent = RuntimeContent(itemRecords = RylweenCompiledGame.nextDsl.itemCatalog),
                renderer = compiledTextRenderer(),
            ),
        ).runUntilBlocked(
            compiledTownSession(party = testParty()),
        )

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertTrue(terminal.output.any { "After listening a while" in it })
        assertTrue(terminal.output.any { "The tavern has suddenly" in it })
        assertTrue(terminal.output.any { "become very quiet." in it })
    }

    @Test
    fun townTerminalAdapterRendersCompiledRumourRoleBindings() {
        val terminal = FakeTerminalPort("T", "R", "", "L", "L")
        val result = TownSessionTerminalAdapter(
            terminal,
            renderer = compiledTextRenderer(),
            serviceBridge = StatefulTownServiceBridge(
                terminal,
                speakContent = compiledSpeakContent(),
                renderer = compiledTextRenderer(),
            ),
        ).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.tavern),
                townSystem = testTownSystem(Buildings.tavern),
                scenes = listOf(testTavernSpeakRumourScene()),
                party = testParty(),
                currentHour = 12,
                rngState = 54L,
                npcs = listOf(NpcPlacement(Npcs.broth, Towns.wilor.inn)),
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertTrue(terminal.output.any { "A man named Broth" in it })
        assertTrue(terminal.output.any { "He lives in Wilor!" in it })
        assertTrue(terminal.output.none { "{crown_seer" in it })
    }

    @Test
    fun townTerminalAdapterRendersCompiledRumourTownBindings() {
        val terminal = FakeTerminalPort("T", "R", "", "L", "L")
        val result = TownSessionTerminalAdapter(
            terminal,
            renderer = compiledTextRenderer(),
            serviceBridge = StatefulTownServiceBridge(
                terminal,
                speakContent = compiledSpeakContent(),
                renderer = compiledTextRenderer(),
            ),
        ).runUntilBlocked(
            TownSession.start(
                town = testTown(Buildings.tavern),
                townSystem = testTownSystem(Buildings.tavern),
                scenes = listOf(testTavernSpeakRumourScene()),
                party = testParty(),
                currentHour = 12,
                rngState = 17L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertTrue(terminal.output.any { "Murn is situated" in it })
        assertTrue(terminal.output.any { "somewhere near 52x27!" in it })
        assertTrue(terminal.output.none { "murn near" in it })
    }

    @Test
    fun compiledRuntimeRealizesAuthoredNpcPlacements() {
        val content = RylweenCompiledGame.nextDsl
        val runtime = compiledRuntimeWithRealizedNpcs(rngState = 1L)

        assertEquals(content.characters.size, runtime.npcs.size)
        assertEquals(Towns.thord.inn, runtime.npcs.single { it.npc == Npcs.garth }.position)
        assertEquals(Wilderness.xy(48, 7), runtime.npcs.single { it.npc == Npcs.marcus }.position)

        val wildernessMerchants = listOf(Npcs.alex, Npcs.galdon, Npcs.kosch, Npcs.ufarsin)
            .map { npc -> runtime.npcs.single { it.npc == npc }.position }
            .filterIsInstance<WorldPosition.Wilderness>()
            .map { it.point }

        assertEquals(4, wildernessMerchants.size)
        assertTrue(wildernessMerchants.none { it == GridPoint(0, 0) })
        assertEquals(wildernessMerchants.toSet().size, wildernessMerchants.size)
        assertTrue(runtime.randomizedTownNpc(content) != null)
    }

    @Test
    fun kotlinEngineBootsCompiledTownRuntimeWithRealizedNpcs() {
        val engine = RylweenKotlinEngine(RylweenCompiledGame.nextDsl)
        val session = engine.townSession(
            party = testParty(),
            town = Towns.thord.id,
            hour = 12,
        )

        assertEquals(Towns.thord.id, session.realm.currentTown)
        assertEquals(null, session.realm.rngState)
        assertEquals(39, session.runtime.npcs.size)
        assertTrue(session.runtime.randomizedTownNpc(RylweenCompiledGame.nextDsl) != null)
        assertTrue(engine.speakContent().charactersFor(session.runtime, Buildings.inn).isNotEmpty())
    }

    @Test
    fun referenceMapDatRowsUseWorldYCoordinates() {
        val map = WorldMapGrid.fromSourceRows(
            (1..11).map { y -> if (y == 11) mapRow(12 to '0') else mapRow() },
        )

        assertFalse(map.isTown(GridPoint(12, 10)))
        assertTrue(map.isTown(GridPoint(12, 11)))
        assertEquals('0', map.tileAt(GridPoint(12, 11)))
    }

    @Test
    fun wildernessMapProjectsRealizedNpcHuts() {
        val map = WorldMapGrid.fromSourceRows((1..12).map { mapRow() })
        val withHut = map.withNpcHuts(listOf(NpcPlacement(Npcs.marcus, Wilderness.xy(12, 11))))

        assertEquals('1', withHut.tileAt(GridPoint(12, 11)))
        assertFalse(GridPoint(12, 11) in withHut.passableNpcPoints())
    }

    @Test
    fun wildernessHutEntryRunsAuthoredSceneSpeakForWildernessNpc() {
        val hut = Wilderness.xy(12, 11)
        val terminal = FakeTerminalPort("E", "T", "1", "B", "L")
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(food = 100, timeLeft = 200, restedHours = 14).copy(position = hut),
                npcs = listOf(NpcPlacement(Npcs.marcus, hut)),
                worldMap = WorldMapGrid.fromSourceRows((1..12).map { mapRow() }),
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.EnteredHut, result.session.lastResult?.status)
        assertTrue(terminal.output.any { "You enter the hut." in it })
        assertTrue(terminal.output.any { "Speak with whom?" in it })
        assertTrue(terminal.output.any { "1. Marcus, hermit" in it })
        assertTrue(result.session.party.visitedNpcs.any { it.npc == Npcs.marcus && it.visited })
    }

    @Test
    fun wildernessTurnStartAppliesTimeFoodAndManaUpkeepOnce() {
        val session = compiledWildernessSession(
            party = testParty(
                food = 10,
                timeLeft = 200,
                restedHours = 14,
                members = listOf(
                    testMember(level = 4, pcClass = PcClass.Mage, wisdom = 10, mana = 8),
                    testMember(name = "Second"),
                ),
            ),
            hour = 23,
        )
        val result = WildernessSessionTerminalAdapter(FakeTerminalPort()).runUntilBlocked(session)
        val party = result.session.party

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertTrue(result.session.step is WildernessSessionStep.Explore)
        assertEquals(0, result.session.runtime.realm.hour)
        assertEquals(7, party.resources.food)
        assertEquals(13, party.resources.restedHours)
        assertEquals(9, party.members.first().mana.current)
    }

    @Test
    fun wildernessTurnStartAppliesFatigueDrain() {
        val terminal = FakeTerminalPort()
        val session = compiledWildernessSession(
            party = testParty(food = 10, timeLeft = 199, restedHours = -3),
            hour = 12,
        )
        val result = WildernessSessionTerminalAdapter(terminal).runUntilBlocked(session)

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(9, result.session.party.members.first().hitPoints.current)
        assertEquals(-4, result.session.party.resources.restedHours)
        assertTrue(terminal.output.any { "You are exhausted!!!" in it })
    }

    @Test
    fun wildernessOutOfMovesUsesSaveOrQuitPrompt() {
        val terminal = FakeTerminalPort("Q")
        val result = WildernessSessionTerminalAdapter(terminal).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(food = 10, timeLeft = 0, restedHours = 14),
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(WildernessSessionResultStatus.OutOfMovesQuitNoSave, result.session.lastResult?.status)
        assertTrue(terminal.output.any { "You are out of moves" in it })
        assertTrue(terminal.output.any { "(S)" in it })
        assertTrue(terminal.output.any { "(Q)" in it })
    }

    @Test
    fun wildernessMoveCanNarrateAuthoredTerrainTransition() {
        val terminal = FakeTerminalPort("2")
        val map = WorldMapGrid.fromSourceRows(
            (1..12).map { y -> if (y == 10) mapRow(12 to 'g') else mapRow() },
        )
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(food = 100, timeLeft = 200, restedHours = 14)
                    .copy(position = Wilderness.xy(12, 9)),
                worldMap = map,
                rngState = 4L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.TerrainNarrated, result.session.lastResult?.status)
        assertEquals(Terrain.plain, result.session.runtime.realm.lastTerrain)
        assertTrue(terminal.output.any { "The ancient forest is" in it })
    }

    @Test
    fun wildernessMovementAcceptsNumberKeysAndArrowAliases() {
        val arrowResult = WildernessSessionTerminalAdapter(FakeTerminalPort(InputAliases.ArrowDown)).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(food = 100, timeLeft = 200, restedHours = 14)
                    .copy(position = Wilderness.xy(12, 9)),
            ),
        )
        val wordAliasResult = WildernessSessionTerminalAdapter(FakeTerminalPort("UP")).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(food = 100, timeLeft = 200, restedHours = 14)
                    .copy(position = Wilderness.xy(12, 9)),
            ),
        )

        assertTrue(arrowResult.status is TerminalRunStatus.Waiting)
        assertEquals(GridPoint(12, 10), (arrowResult.session.party.position as WorldPosition.Wilderness).point)
        assertEquals(199, arrowResult.session.party.resources.timeLeft)
        assertTrue(wordAliasResult.status is TerminalRunStatus.Waiting)
        assertEquals(GridPoint(12, 8), (wordAliasResult.session.party.position as WorldPosition.Wilderness).point)
        assertEquals(199, wordAliasResult.session.party.resources.timeLeft)
    }

    @Test
    fun wildernessInventoryCommandRunsGenericItemUseSession() {
        val terminal = FakeTerminalPort("Q", "A", "1")
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    health = 30,
                    hitPoints = 10,
                    inventoryItems = mapOf(1 to Items.redPotion),
                ),
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertTrue(result.session.step is WildernessSessionStep.Explore)
        assertEquals(WildernessSessionResultStatus.InventoryUsed, result.session.lastResult?.status)
        assertEquals(null, result.session.party.inventoryItem(1))
        assertEquals(25, result.session.party.members.single().hitPoints.current)
        assertTrue(terminal.output.any { "Press ? for commands." in it })
        assertTrue(terminal.output.any { "Quaff what?" in it })
        assertTrue(terminal.output.any { "A. Red Potion" in it })
    }

    @Test
    fun wildernessCastSpellKeyRejectsNonPriest() {
        val terminal = FakeTerminalPort("Z", "1")
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(food = 100, timeLeft = 200, restedHours = 14),
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertTrue(result.session.step is WildernessSessionStep.Explore)
        assertEquals(WildernessSessionResultStatus.CastSpellUnavailable, result.session.lastResult?.status)
        assertTrue(terminal.output.any { "Who? (1-4)" in it })
        assertTrue(terminal.output.any { "Wrong class." in it })
    }

    @Test
    fun wildernessCastSpellKeyRunsPriestHealingScript() {
        val terminal = FakeTerminalPort("Z", "1", "1", "1")
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    pcClass = PcClass.Priest,
                    level = 1,
                    health = 30,
                    hitPoints = 10,
                    wisdom = 20,
                    mana = 20,
                ),
            ),
        )

        val member = result.session.party.members.single()
        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.CastSpellUsed, result.session.lastResult?.status)
        assertEquals(22, member.hitPoints.current)
        assertEquals(12, member.mana.current)
        assertTrue(terminal.output.any { "1. Healing" in it })
        assertTrue(terminal.output.any { "8. Abort" in it })
        assertTrue(terminal.output.any { "Tester heals" in it })
    }

    @Test
    fun wildernessMoveCanFindAndLeaveCave() {
        val terminal = FakeTerminalPort("2", "L")
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(food = 100, timeLeft = 200, restedHours = 14)
                    .copy(position = Wilderness.xy(12, 9)),
                rngState = 21L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.CaveLeft, result.session.lastResult?.status)
        assertEquals(GridPoint(12, 10), (result.session.party.position as WorldPosition.Wilderness).point)
        assertTrue(terminal.output.any { "You find a cave!" in it })
    }

    @Test
    fun wildernessCaveEntryHandsOffToDungeonRuntimeShape() {
        val terminal = FakeTerminalPort("2", "E")
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(food = 100, timeLeft = 200, restedHours = 14)
                    .copy(position = Wilderness.xy(12, 9)),
                rngState = 21L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.DungeonHandoff, result.session.lastResult?.status)
        assertEquals(Dungeons.randomCave, result.session.lastResult?.dungeon)
        assertEquals(189, result.session.party.resources.timeLeft)
        assertEquals(RuntimeMode.Dungeon, result.session.runtime.realm.currentMode)
        val dungeon = result.session.step as? WildernessSessionStep.Dungeon
            ?: error("expected dungeon session after cave handoff")
        assertEquals(Dungeons.randomCave, dungeon.session.dungeon)
        assertEquals(10, dungeon.session.maxDepth)
        assertDungeonHandoffOutput(terminal.output, Dungeons.randomCave, 10)
        assertTrue(dungeon.session.levelEvents.containsKey(1))
    }

    @Test
    fun wildernessCastleEntryWithoutSilverKeyRunsAuthoredYdargGate() {
        val terminal = RecordingTerminalPort("E")
        val map = WorldMapGrid.fromSourceRows(
            List(30) { y ->
                if (y == 29) mapRow(14 to '2', width = 20) else mapRow(width = 20)
            },
        )

        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(food = 100, timeLeft = 200, restedHours = 14)
                    .copy(position = Wilderness.xy(14, 30)),
                worldMap = map,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.LeftYdarg, result.session.lastResult?.status)
        assertTrue(terminal.imageSlugs.contains(Images.Backdrop.ydargCastle.slug))
        assertTrue(terminal.output.any { "guardian of the gate." in it })
        assertTrue(terminal.output.any { "remain sealed." in it })
        assertFalse(terminal.output.any { "Castle entry is not ported yet." in it })
    }

    @Test
    fun wildernessCastleEntryWithSilverKeyHandsOffToYdargSession() {
        val terminal = RecordingTerminalPort("E")
        val map = WorldMapGrid.fromSourceRows(
            List(30) { y ->
                if (y == 29) mapRow(14 to '2', width = 20) else mapRow(width = 20)
            },
        )

        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    milestones = listOf(Milestones.silverKey),
                ).copy(position = Wilderness.xy(14, 30)),
                worldMap = map,
                rngState = 1L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertTrue(result.session.step is WildernessSessionStep.Ydarg)
        assertEquals(RuntimeMode.Battle, result.session.runtime.realm.currentMode)
        assertTrue(terminal.output.any { "resounding KRASH" in it })
        assertTrue(terminal.output.any { "You are spotted by" in it })
        assertFalse(terminal.output.any { "Castle entry is not ported yet." in it })
    }

    @Test
    fun ydargStairGiftConsumesAuthoredMilestoneAndAdvancesFloor() {
        val content = RylweenCompiledGame.nextDsl
        val party = testParty(
            food = 100,
            timeLeft = 200,
            restedHours = 14,
            milestones = listOf(Milestones.silverKey, Milestones.fireShield),
        )
        val started = YdargSession.start(
            runtime = RuntimeSessionState.singleParty(party = party, hour = 12, currentTown = null),
            content = content.runtimeContent(),
        )

        val stairs = started.copy(
            step = YdargSessionStep.StairGive,
            floor = 1,
            point = GridPoint(5, 5),
        )
        val result = stairs.apply(YdargSessionInput.StairItem(1))

        assertEquals(2, result.floor)
        assertEquals(GridPoint(1, 1), result.point)
        assertFalse(Milestones.fireShield in result.party.milestones)
        assertTrue(result.step is YdargSessionStep.Messages)
    }

    @Test
    fun wildernessMoveCanEnterAuthoredPositionDungeonMission() {
        val terminal = FakeTerminalPort("2", "E")
        val map = WorldMapGrid.fromSourceRows((1..22).map { mapRow(width = 50) })
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    flags = listOf(Story.erethor.lute.rumourTold),
                ).copy(position = Wilderness.xy(6, 19)),
                worldMap = map,
                rngState = 21L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.DungeonHandoff, result.session.lastResult?.status)
        assertEquals(Missions.erethorEntry, result.session.lastResult?.mission)
        assertEquals(Dungeons.erethorTemple, result.session.lastResult?.dungeon)
        assertEquals(12, result.session.lastResult?.dungeonLevels)
        assertEquals(RuntimeMode.Dungeon, result.session.runtime.realm.currentMode)
        val dungeon = result.session.step as? WildernessSessionStep.Dungeon
            ?: error("expected dungeon session after mission handoff")
        assertEquals(Dungeons.erethorTemple, dungeon.session.dungeon)
        assertEquals(12, dungeon.session.maxDepth)
        assertTrue(terminal.output.any { "Here is the temple of" in it })
        assertDungeonHandoffOutput(terminal.output, Dungeons.erethorTemple, 12)
    }

    @Test
    fun wildernessMoveCanEnterAuthoredLocationDungeonMission() {
        val terminal = FakeTerminalPort("2", "E")
        val map = WorldMapGrid.fromSourceRows((1..12).map { mapRow(width = 20) })
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    flags = listOf(Story.silverKey.mapBought),
                ).copy(position = Wilderness.xy(12, 9)),
                locations = listOf(LocationPlacement(Locations.silverKeyPit, GridPoint(12, 10))),
                worldMap = map,
                rngState = 21L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.DungeonHandoff, result.session.lastResult?.status)
        assertEquals(Missions.silverKey, result.session.lastResult?.mission)
        assertEquals(Dungeons.silverSanctum, result.session.lastResult?.dungeon)
        assertEquals(20, result.session.lastResult?.dungeonLevels)
        assertEquals(RuntimeMode.Dungeon, result.session.runtime.realm.currentMode)
        val dungeon = result.session.step as? WildernessSessionStep.Dungeon
            ?: error("expected dungeon session after mission handoff")
        assertEquals(Dungeons.silverSanctum, dungeon.session.dungeon)
        assertEquals(20, dungeon.session.maxDepth)
        assertTrue(terminal.output.any { "Here lies the dungeon" in it })
        assertDungeonHandoffOutput(terminal.output, Dungeons.silverSanctum, 20)
    }

    @Test
    fun wildernessMoveCanOpenThunTempleBySpendingFiveGems() {
        val terminal = FakeTerminalPort("2", "", "", "Y", "")
        val map = WorldMapGrid.fromSourceRows((1..12).map { mapRow(width = 20) })
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    flags = listOf(Story.bracelets.thun.mapBought),
                    inventoryItems = mapOf(
                        1 to Items.redGem,
                        2 to Items.blueGem,
                        3 to Items.greenGem,
                        4 to Items.redGem,
                        5 to Items.blueGem,
                        6 to Items.rope,
                    ),
                ).copy(position = Wilderness.xy(12, 9)),
                locations = listOf(LocationPlacement(Locations.thunTemple, GridPoint(12, 10))),
                worldMap = map,
                rngState = 21L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.DungeonHandoff, result.session.lastResult?.status)
        assertEquals(Missions.thunTemple, result.session.lastResult?.mission)
        assertEquals(Dungeons.thunTemple, result.session.lastResult?.dungeon)
        assertEquals(18, result.session.lastResult?.dungeonLevels)
        assertEquals(RuntimeMode.Dungeon, result.session.runtime.realm.currentMode)
        val dungeon = result.session.step as? WildernessSessionStep.Dungeon
            ?: error("expected dungeon session after mission handoff")
        assertEquals(Dungeons.thunTemple, dungeon.session.dungeon)
        assertEquals(18, dungeon.session.maxDepth)
        assertEquals(0, result.session.party.countInventoryItems(RylweenCompiledGame.nextDsl.itemPool(ItemPools.thunAltarGems)))
        assertEquals(Items.rope, result.session.party.inventoryItem(6))
        assertTrue(terminal.output.any { "With a resounding 'CRUNG'" in it })
        assertDungeonHandoffOutput(terminal.output, Dungeons.thunTemple, 18)
    }

    @Test
    fun wildernessMoveToThunTempleWithoutFiveGemsDoesNotSpendOrEnter() {
        val terminal = FakeTerminalPort("2", "", "", "Y", "")
        val map = WorldMapGrid.fromSourceRows((1..12).map { mapRow(width = 20) })
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    flags = listOf(Story.bracelets.thun.mapBought),
                    inventoryItems = mapOf(
                        1 to Items.redGem,
                        2 to Items.blueGem,
                        3 to Items.greenGem,
                        4 to Items.rope,
                    ),
                ).copy(position = Wilderness.xy(12, 9)),
                locations = listOf(LocationPlacement(Locations.thunTemple, GridPoint(12, 10))),
                worldMap = map,
                rngState = 21L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.MissionCompleted, result.session.lastResult?.status)
        assertEquals(Missions.thunTemple, result.session.lastResult?.mission)
        assertEquals(3, result.session.party.countInventoryItems(RylweenCompiledGame.nextDsl.itemPool(ItemPools.thunAltarGems)))
        assertEquals(Items.rope, result.session.party.inventoryItem(4))
        assertTrue(terminal.output.any { "You don't have five" in it })
    }

    @Test
    fun wildernessCaveEntryRejectsWhenMovesAreTooLow() {
        val terminal = FakeTerminalPort("2", "E")
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(food = 100, timeLeft = 11, restedHours = 14)
                    .copy(position = Wilderness.xy(12, 9)),
                rngState = 21L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.CaveNoMoves, result.session.lastResult?.status)
        assertEquals(10, result.session.party.resources.timeLeft)
        assertTrue(terminal.output.any { "moves left to explore" in it })
    }

    @Test
    fun wildernessMoveCanHandOffEncounterByAuthoredTier() {
        val terminal = FakeTerminalPort("2")
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(food = 100, timeLeft = 200, restedHours = 14)
                    .copy(position = Wilderness.xy(12, 9)),
                rngState = 3L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.EncounterHandoff, result.session.lastResult?.status)
        assertEquals(EncounterTiers.weak, result.session.lastResult?.encounterTier)
        assertEquals(RuntimeMode.Battle, result.session.runtime.realm.currentMode)
        assertTrue(result.session.step is WildernessSessionStep.Battle)
        assertTrue(terminal.output.any { "ENCOUNTER!" in it })
        assertTrue(terminal.output.any { "You face" in it })
        assertTrue(terminal.output.any { "(F)" in it })
        assertTrue(terminal.output.any { "(R)" in it })
    }

    @Test
    fun wildernessMoveCanTriggerAuthoredBanditsMissionAndWaitInBattle() {
        val terminal = FakeTerminalPort("2")
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    flags = listOf(Story.garth.bandits.accepted),
                ).copy(position = Wilderness.xy(12, 9)),
                rngState = 21L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.MissionHandoff, result.session.lastResult?.status)
        assertEquals(Missions.bandits, result.session.lastResult?.mission)
        assertTrue(result.session.step is WildernessSessionStep.Mission)
        val mission = result.session.step
        assertTrue(mission.continuation?.battleSession?.encounter?.monster?.id == Monsters.bandit)
        assertFalse(result.session.party.hasFlagState(Story.garth.bandits.recovered))
        assertTrue(terminal.output.any { "bandits" in it.lowercase() })
        assertTrue(terminal.output.any { "ENCOUNTER!" in it })
    }

    @Test
    fun wildernessBanditsMissionResumesAfterMandatoryBattleAndMarksRecovered() {
        val hero = testMember(
            health = 1000,
            hitPoints = 1000,
            weaponSkill = 100,
            equipment = EquipmentState(Items.deathBlade, Items.none),
        )
        val terminal = FakeTerminalPort("2", "F", "A", "A", "F", "A", "B")
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    members = listOf(hero),
                    flags = listOf(Story.garth.bandits.accepted),
                ).copy(position = Wilderness.xy(12, 9)),
                rngState = 21L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertTrue(result.session.step is WildernessSessionStep.Explore)
        assertEquals(WildernessSessionResultStatus.MissionCompleted, result.session.lastResult?.status)
        assertEquals(Missions.bandits, result.session.lastResult?.mission)
        assertTrue(result.session.party.hasFlagState(Story.garth.bandits.recovered))
        assertEquals(RuntimeMode.Wilderness, result.session.runtime.realm.currentMode)
        assertTrue(terminal.output.any { "recovered" in it.lowercase() })
    }

    @Test
    fun wildernessMoveCanTriggerAuthoredTrollToothMissionAndWaitInBattle() {
        val terminal = FakeTerminalPort("2")
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    flags = listOf(Story.hermit.trollTooth.accepted),
                ).copy(position = Wilderness.xy(12, 9)),
                rngState = 21L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.MissionHandoff, result.session.lastResult?.status)
        assertEquals(Missions.trollTooth, result.session.lastResult?.mission)
        assertTrue(result.session.step is WildernessSessionStep.Mission)
        val mission = result.session.step
        assertTrue(mission.continuation?.battleSession?.encounter?.monster?.id == Monsters.troll)
        assertFalse(result.session.party.hasFlagState(Story.hermit.trollTooth.extracted))
        assertTrue(terminal.output.any { "ENCOUNTER!" in it })
    }

    @Test
    fun wildernessMoveCanCompleteAuthoredSkullweedTerrainMission() {
        val terminal = FakeTerminalPort("2", "")
        val map = WorldMapGrid.fromSourceRows(
            (1..12).map { y -> if (y == 10) mapRow(12 to 't') else mapRow() },
        )
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    flags = listOf(Story.masterCrown.task2Assigned),
                ).copy(position = Wilderness.xy(12, 9)),
                worldMap = map,
                rngState = 21L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertTrue(result.session.step is WildernessSessionStep.Explore)
        assertEquals(WildernessSessionResultStatus.MissionCompleted, result.session.lastResult?.status)
        assertEquals(Missions.skullweed, result.session.lastResult?.mission)
        assertTrue(result.session.party.hasFlagState(Story.masterCrown.task2Done))
        assertTrue(terminal.output.any { "skullweed" in it.lowercase() })
    }

    @Test
    fun wildernessMoveCanCompleteAuthoredBorinMeetMissionAndAppendJournal() {
        val terminal = FakeTerminalPort("2", "", "", "", "", "", "")
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    flags = listOf(Story.masterCrown.task3Assigned),
                ).copy(position = Wilderness.xy(12, 9)),
                locations = listOf(
                    LocationPlacement(Locations.borinCamp, GridPoint(12, 10)),
                    LocationPlacement(Locations.nolrimCrypt, GridPoint(37, 14)),
                ),
                rngState = 21L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertTrue(result.session.step is WildernessSessionStep.Explore)
        assertEquals(WildernessSessionResultStatus.MissionCompleted, result.session.lastResult?.status)
        assertEquals(Missions.borinMeet, result.session.lastResult?.mission)
        assertTrue(result.session.party.hasFlagState(Story.masterCrown.borinMet))
        assertEquals(
            listOf(JournalEntryState.Line("Nolrim's Crypt is at 37x14.")),
            result.session.party.journal,
        )
        assertTrue(terminal.output.any { "37x14. Be careful" in it })
    }

    @Test
    fun wildernessMoveCanTriggerAuthoredKoboldVillageMissionAndWaitInBattle() {
        val terminal = FakeTerminalPort("2", "A", "")
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    flags = listOf(Story.masterCrown.task1Assigned),
                ).copy(position = Wilderness.xy(12, 9)),
                locations = listOf(LocationPlacement(Locations.koboldVillage, GridPoint(12, 10))),
                rngState = 21L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.MissionHandoff, result.session.lastResult?.status)
        assertEquals(Missions.koboldVillage, result.session.lastResult?.mission)
        assertTrue(result.session.step is WildernessSessionStep.Mission)
        val mission = result.session.step
        assertTrue(mission.continuation?.battleSession?.encounter?.monster?.id == Monsters.koboldHorde)
        assertFalse(result.session.party.hasFlagState(Story.masterCrown.task1Done))
        assertTrue(terminal.output.any { "kobold village" in it.lowercase() })
        assertTrue(terminal.output.any { "ENCOUNTER!" in it })
    }

    @Test
    fun wildernessMoveCanTriggerAuthoredJazzyFootMissionAndWaitInFirstBattle() {
        val terminal = FakeTerminalPort("2", "", "")
        val map = WorldMapGrid.fromSourceRows(
            (1..12).map { y -> if (y == 10) mapRow(12 to 'f') else mapRow() },
        )
        val result = WildernessSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledWildernessSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    flags = listOf(Story.jazzy.ogre.ogresCleared),
                ).copy(position = Wilderness.xy(12, 9)),
                worldMap = map,
                rngState = 21L,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(WildernessSessionResultStatus.MissionHandoff, result.session.lastResult?.status)
        assertEquals(Missions.jazzyFoot, result.session.lastResult?.mission)
        assertTrue(result.session.step is WildernessSessionStep.Mission)
        val mission = result.session.step
        assertTrue(mission.continuation?.battleSession?.encounter?.monster?.id == Monsters.wingedThing)
        assertFalse(result.session.party.hasFlagState(Story.jazzy.ogre.jazzyDefeated))
        assertTrue(terminal.output.any { "FOOT OF JAZZY" in it })
        assertTrue(terminal.output.any { "ENCOUNTER!" in it })
    }

    @Test
    fun battleSessionRunCanEscapeFromOpeningPrompt() {
        val terminal = FakeTerminalPort("R")
        val result = BattleSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledBattleSession(tier = EncounterTiers.weak),
        )

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(BattleSessionResultStatus.RunEscaped, result.session.lastResult.status)
        assertEquals(Monsters.kobold, result.session.lastResult.monster)
        assertEquals(RuntimeMode.Wilderness, result.session.runtime.realm.currentMode)
        assertTrue(terminal.output.any { "2 Kobolds" in it })
        assertTrue(terminal.output.any { "Coward. You won't get" in it })
    }

    @Test
    fun battleSessionFightRunsFirstPlayerAttackAndMonsterRound() {
        val terminal = FakeTerminalPort("F", "A", "A")
        val result = BattleSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledBattleSession(
                tier = EncounterTiers.wild,
                party = testParty(food = 100, timeLeft = 200, restedHours = 14, health = 100, hitPoints = 100),
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(BattleSessionResultStatus.FightSelected, result.session.lastResult.status)
        assertEquals(Monsters.stirge, result.session.lastResult.monster)
        assertEquals(RuntimeMode.Battle, result.session.runtime.realm.currentMode)
        assertTrue(result.session.step is BattleSessionStep.RoundOpening)
        assertEquals(3, result.session.encounter.hitPoints.first())
        assertEquals(89, result.session.runtime.activeParty.members.single().hitPoints.current)
    }

    @Test
    fun battleTerminalAdapterAcceptsAuthoredInputAliases() {
        val start = compiledBattleSession(
            tier = EncounterTiers.wild,
            party = testParty(food = 100, timeLeft = 200, restedHours = 14, health = 100, hitPoints = 100),
        )
        val battle = start.battle.copy(
            openingKeys = BattleOpeningKeysModel(
                fight = inputKey('F', "FIGHT"),
                run = inputKey('R', "RUN"),
            ),
            actions = start.battle.actions.map { action ->
                if (action.kind == CombatActionKind.Attack) {
                    action.copy(binding = inputKey(action.key, "ATTACK"))
                } else {
                    action
                }
            },
        )
        val result = BattleSessionTerminalAdapter(FakeTerminalPort("FIGHT", "ATTACK", "A"), compiledTextRenderer())
            .runUntilBlocked(start.copy(battle = battle))

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(BattleSessionResultStatus.FightSelected, result.session.lastResult.status)
        assertTrue(result.session.step is BattleSessionStep.RoundOpening)
    }

    @Test
    fun battleSessionAttackUsesReadiedWeaponDamage() {
        fun runAttackWith(weapon: ItemId): TerminalAdapterResult<BattleSession> {
            val terminal = FakeTerminalPort("F", "A", "A")
            return BattleSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
                customBattleSession(
                    party = testParty(
                        food = 100,
                        timeLeft = 200,
                        restedHours = 14,
                        members = listOf(
                            testMember(
                                health = 100,
                                hitPoints = 100,
                                weaponSkill = 100,
                                equipment = EquipmentState(weapon, Items.none),
                            ),
                        ),
                    ),
                    monster = MonsterModel(
                        id = Monsters.bandit,
                        name = "Training Dummy",
                        count = 1..1,
                        countMaxLevelDivisor = null,
                        hitPoints = 100..100,
                        weaponSkill = 0,
                        spellPower = 0,
                        tier = EncounterTiers.weak,
                        immune = false,
                    ),
                ),
            )
        }

        val armed = runAttackWith(Items.deathBlade)
        val unarmed = runAttackWith(Items.none)
        val deathBladeDamage = checkNotNull(compiledItemRecords().single { it.id == Items.deathBlade }.weaponDamage)

        assertTrue(armed.status is TerminalRunStatus.Waiting)
        assertTrue(unarmed.status is TerminalRunStatus.Waiting)
        assertEquals(68, deathBladeDamage)
        assertEquals(
            deathBladeDamage - 1,
            unarmed.session.encounter.hitPoints.single() - armed.session.encounter.hitPoints.single(),
        )
    }

    @Test
    fun battleSessionDefendClampsHitPointsBeforeMonsterRound() {
        val terminal = FakeTerminalPort("F", "D")
        val result = BattleSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledBattleSession(
                tier = EncounterTiers.weak,
                party = testParty(food = 100, timeLeft = 200, restedHours = 14, health = 100, hitPoints = 99),
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertEquals(RuntimeMode.Battle, result.session.runtime.realm.currentMode)
        assertTrue(result.session.step is BattleSessionStep.RoundOpening)
        assertEquals(90, result.session.runtime.activeParty.members.single().hitPoints.current)
    }

    @Test
    fun battleSessionMonsterMeleeUsesReadiedArmorClass() {
        val terminal = FakeTerminalPort("F", "D")
        val result = BattleSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            customBattleSession(
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    health = 100,
                    hitPoints = 100,
                    equipment = EquipmentState(Items.none, Items.plateMail),
                ),
                monster = MonsterModel(
                    id = Monsters.bandit,
                    name = "Armored Bruiser",
                    count = 1..1,
                    countMaxLevelDivisor = null,
                    hitPoints = 60..60,
                    weaponSkill = 100,
                    spellPower = 0,
                    tier = EncounterTiers.weak,
                    immune = false,
                ),
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertTrue(result.session.step is BattleSessionStep.RoundOpening)
        assertEquals(97, result.session.runtime.activeParty.members.single().hitPoints.current)
        assertTrue(terminal.output.any { "3 damage pts!!!" in it })
    }

    @Test
    fun battleSessionVictoryAppliesStandardRewardsAndLoot() {
        val content = RylweenCompiledGame.nextDsl
        val terminal = FakeTerminalPort("F", "A", "A")
        val result = BattleSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            BattleSession.start(
                runtime = RuntimeSessionState.singleParty(
                    party = testParty(
                        food = 100,
                        timeLeft = 200,
                        restedHours = 14,
                        members = listOf(
                            testMember(
                                weaponSkill = 100,
                                equipment = EquipmentState(Items.deathBlade, Items.none),
                            ),
                        ),
                    ),
                    hour = 12,
                    currentTown = null,
                ),
                combat = content.combat,
                monsters = listOf(
                    MonsterModel(
                        id = Monsters.bandit,
                        name = "Bandit",
                        count = 1..1,
                        countMaxLevelDivisor = null,
                        hitPoints = 1..99,
                        weaponSkill = 50,
                        spellPower = 0,
                        tier = EncounterTiers.weak,
                        immune = false,
                    ),
                ),
                formulas = content.formulas,
                rewards = content.rewards,
                itemRecords = content.itemCatalog,
                leveling = content.leveling,
                spells = content.spells,
                itemUses = content.itemUses,
                tier = EncounterTiers.weak,
            ),
        )

        val party = result.session.runtime.activeParty
        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(BattleSessionResultStatus.Victory, result.session.lastResult.status)
        assertEquals(RuntimeMode.Wilderness, result.session.runtime.realm.currentMode)
        assertEquals(1, party.counters.kills)
        assertEquals(168, party.resources.gold)
        assertEquals(300, party.members.single().experience)
        assertEquals(Items.redPotion, party.inventoryItem(1))
    }

    @Test
    fun battleSessionMageFrostSpellSpendsManaAndDamagesTarget() {
        val terminal = FakeTerminalPort("F", "C", "1", "A")
        val result = BattleSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledBattleSession(
                tier = EncounterTiers.weak,
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    pcClass = PcClass.Mage,
                    level = 5,
                    health = 100,
                    hitPoints = 100,
                    wisdom = 100,
                    mana = 100,
                ),
            ),
        )

        val member = result.session.runtime.activeParty.members.single()
        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertTrue(result.session.step is BattleSessionStep.RoundOpening)
        assertEquals(87, member.mana.current)
        assertEquals(40, result.session.encounter.hitPoints.first())
        assertTrue(terminal.output.any { it == "Tester chills the" })
        assertFalse(terminal.output.any { "{string}" in it })
    }

    @Test
    fun battleSessionMagicItemCanRetryWithoutConsuming() {
        val terminal = FakeTerminalPort("F", "U", "A", "1")
        val result = BattleSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            compiledBattleSession(
                tier = EncounterTiers.weak,
                party = testParty(
                    food = 100,
                    timeLeft = 200,
                    restedHours = 14,
                    health = 100,
                    hitPoints = 100,
                    inventoryItems = mapOf(1 to Items.gemOfResurrection),
                ),
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertTrue(result.session.step is BattleSessionStep.PlayerAction)
        assertEquals(Items.gemOfResurrection, result.session.runtime.activeParty.inventoryItem(1))
        assertTrue(terminal.output.any { "A. Gem of Resurrection" in it })
        assertTrue(terminal.output.any { "not dead" in it })
    }

    @Test
    fun battleSessionMonsterSpellRunsFromAuthoredScript() {
        val content = RylweenCompiledGame.nextDsl
        val terminal = FakeTerminalPort("F", "D")
        val result = BattleSessionTerminalAdapter(terminal, compiledTextRenderer()).runUntilBlocked(
            BattleSession.start(
                runtime = RuntimeSessionState.singleParty(
                    party = testParty(food = 100, timeLeft = 200, restedHours = 14, health = 100, hitPoints = 100),
                    hour = 12,
                    currentTown = null,
                ),
                combat = content.combat,
                monsters = listOf(
                    MonsterModel(
                        id = Monsters.bandit,
                        name = "Spellcaster",
                        count = 1..1,
                        countMaxLevelDivisor = null,
                        hitPoints = 50..50,
                        weaponSkill = 0,
                        spellPower = 300,
                        tier = EncounterTiers.weak,
                        immune = false,
                    ),
                ),
                formulas = content.formulas,
                rewards = content.rewards,
                itemRecords = content.itemCatalog,
                leveling = content.leveling,
                spells = content.spells,
                itemUses = content.itemUses,
                tier = EncounterTiers.weak,
            ),
        )

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertTrue(result.session.step is BattleSessionStep.RoundOpening)
        assertEquals(90, result.session.runtime.activeParty.members.single().hitPoints.current)
        assertTrue(terminal.output.any { "Frost Finger" in it })
    }

    @Test
    fun wildernessTerminalMovesIntoCompiledTownSession() {
        val content = RylweenCompiledGame.nextDsl
        val state = RylweenCompiledGame.nextRylween.states.single { it.id == States.freshReplay }
        val map = WorldMapGrid.fromSourceRows(
            (1..11).map { y -> if (y == 11) mapRow(12 to '0') else mapRow() },
        )
        val engine = RylweenKotlinEngine(content)
        val terminal = FakeTerminalPort("2", "2", "E", "L", "X", "Y")
        val result = WildernessSessionTerminalAdapter(
            terminal = terminal,
            renderer = compiledTextRenderer(),
            serviceBridge = StatefulTownServiceBridge(
                terminal,
                speakContent = compiledSpeakContent(),
                renderer = compiledTextRenderer(),
            ),
        ).runUntilBlocked(
            engine.wildernessSession(state, map, rngState = null, realizeNpcs = false),
        )

        assertTrue(result.status is TerminalRunStatus.Finished)
        assertEquals(GridPoint(12, 11), (result.session.party.position as WorldPosition.Wilderness).point)
        assertEquals(198, result.session.party.resources.timeLeft)
        assertEquals(98, result.session.party.resources.food)
        assertEquals(RuntimeMode.Wilderness, result.session.runtime.realm.currentMode)
        assertEquals(null, result.session.runtime.realm.currentTown)
        assertTrue(terminal.output.any { "Thord" in it })
    }

    @Test
    fun wildernessQuitConfirmationCanCancelOrFinish() {
        val session = compiledWildernessSession()

        val cancelled = WildernessSessionTerminalAdapter(FakeTerminalPort("X", "N")).runUntilBlocked(session)
        val finished = WildernessSessionTerminalAdapter(FakeTerminalPort("X", "Y")).runUntilBlocked(cancelled.session)

        assertTrue(cancelled.status is TerminalRunStatus.Waiting)
        assertTrue(cancelled.session.step is WildernessSessionStep.Explore)
        assertEquals(WildernessSessionResultStatus.QuitCancelled, cancelled.session.lastResult?.status)
        assertTrue(finished.status is TerminalRunStatus.Finished)
        assertTrue(finished.session.step is WildernessSessionStep.Finished)
        assertEquals(WildernessSessionResultStatus.Finished, finished.session.lastResult?.status)
    }

    @Test
    fun wildernessQuitConfirmationBlocksForAnswer() {
        val session = compiledWildernessSession()
        val result = WildernessSessionTerminalAdapter(FakeTerminalPort("X")).runUntilBlocked(session)

        assertTrue(result.status is TerminalRunStatus.Waiting)
        assertTrue(result.session.step is WildernessSessionStep.QuitConfirm)
        assertEquals(WildernessSessionResultStatus.QuitRequested, result.session.lastResult?.status)
    }

    @Test
    fun speakSessionUsesRealizedRandomNpcTownPlacements() {
        val content = RylweenCompiledGame.nextDsl
        val runtime = compiledRuntimeWithRealizedNpcs(rngState = 1L)
        val placement = checkNotNull(runtime.randomizedTownNpc(content))
        val position = placement.position as WorldPosition.Town
        val townRuntime = runtime.withRealm(runtime.realm.copy(currentTown = position.town))
        val session = SpeakSession.start(compiledSpeakContent(), townRuntime, position.building)
        val step = session.step as SpeakStep.ChooseNpc

        assertTrue(step.npcs.any { it.id == placement.npc })
    }

    @Test
    fun compiledRumourRoleLocationsUseRealizedNpcPlacements() {
        val content = RylweenCompiledGame.nextDsl
        val runtime = compiledRuntimeWithRealizedNpcs(rngState = 1L)
        val rumour = content.speak.textTables.rumourTables
            .single { it.id == RumourTables.standardTown }
            .entries
            .single { it.id == Rumours.crownSeer }
            .text
        val expectedLocation = runtime.npcs.single { it.npc == Npcs.broth }.position.renderForTest(content.towns)
        val lines = compiledTextRenderer().lines(rumour, TerminalTextContext(runtime = runtime))

        assertTrue(lines.any { "A man named Broth" in it })
        assertTrue(lines.any { expectedLocation in it })
        assertTrue(lines.none { "{crown_seer" in it })
    }

    @Test
    fun compiledGameGarthRewardBranchAppliesAuthoredRewardAndFlag() {
        val (service, scene) = garthRecoveredDialogueService()
        val result = StatefulTownServiceBridge(
            FakeTerminalPort(),
            battleContent = RuntimeContent(itemRecords = RylweenCompiledGame.nextDsl.itemCatalog),
        ).run(
            service,
            scene,
            TownServiceContinuation.start(
                RuntimeSessionState.singleParty(
                    party = testParty(gold = 10, flags = listOf(Story.garth.bandits.recovered)),
                    hour = 12,
                    currentTown = Towns.thord.id,
                ),
            ),
        )

        assertTrue(result.status is TownServiceBridgeStatus.Finished)
        assertEquals(2010, result.party.resources.gold)
        assertEquals(Items.redGem, result.party.inventoryItem(1))
        assertTrue(result.party.hasFlagState(Story.garth.bandits.closed))
        assertTrue(result.events.any { it is EngineTraceEvent.GrantReward })
    }

    @Test
    fun compiledGameGarthRewardDropPromptResumesWithoutRegrantingGold() {
        val (service, scene) = garthRecoveredDialogueService()
        val runtime = RuntimeSessionState.singleParty(
            party = testParty(
                gold = 10,
                inventoryItems = (1..InventorySaveSlotMax).associateWith { Items.rope },
                flags = listOf(Story.garth.bandits.recovered),
            ),
            hour = 12,
            currentTown = Towns.thord.id,
        )
        val blocked = StatefulTownServiceBridge(
            FakeTerminalPort(),
            battleContent = RuntimeContent(itemRecords = RylweenCompiledGame.nextDsl.itemCatalog),
        ).run(
            service,
            scene,
            TownServiceContinuation.start(runtime),
        )

        assertTrue(blocked.status is TownServiceBridgeStatus.Waiting)
        assertEquals(2010, blocked.party.resources.gold)
        assertEquals(Items.rope, blocked.party.inventoryItem(1))
        assertTrue(blocked.continuation?.rewardGrantSession?.step is RewardGrantStep.DropSlot)

        val resumed = StatefulTownServiceBridge(
            FakeTerminalPort("A"),
            battleContent = RuntimeContent(itemRecords = RylweenCompiledGame.nextDsl.itemCatalog),
        ).run(
            service,
            scene,
            checkNotNull(blocked.continuation),
        )

        assertTrue(resumed.status is TownServiceBridgeStatus.Finished)
        assertEquals(2010, resumed.party.resources.gold)
        assertEquals(Items.redGem, resumed.party.inventoryItem(1))
        assertTrue(resumed.party.hasFlagState(Story.garth.bandits.closed))
        assertTrue(resumed.events.any { event ->
            event is EngineTraceEvent.Screen && event.text == inlineText("You pick up the gem.")
        })
    }

    @Test
    fun compiledGameSilverKeyVendorDialogueBuysAuthoredMap() {
        val (service, scene) = dialogueBranchService(Npcs.bernak, Story.silverKey.none)
        val result = StatefulTownServiceBridge(
            FakeTerminalPort("B"),
            battleContent = RuntimeContent(itemRecords = RylweenCompiledGame.nextDsl.itemCatalog),
            renderer = compiledTextRenderer(),
        ).run(
            service,
            scene,
            TownServiceContinuation.start(
                RuntimeSessionState.singleParty(
                    party = testParty(gold = 50000),
                    hour = 12,
                    currentTown = Towns.wilor.id,
                ).copy(locations = listOf(LocationPlacement(Locations.silverKeyPit, GridPoint(45, 13)))),
            ),
        )

        assertTrue(result.status is TownServiceBridgeStatus.Finished)
        assertEquals(0, result.party.resources.gold)
        assertTrue(result.party.hasFlagState(Story.silverKey.mapBought))
        assertEquals(listOf(JournalEntryState.Line("The Pit of the Silver Key is at 45x13.")), result.party.journal)
    }

    @Test
    fun compiledGameJazzyOgreTradeConsumesValuableItemAndMarksQuest() {
        val (service, scene) = dialogueBranchService(Npcs.leander, Story.jazzy.ogre.none)
        val result = StatefulTownServiceBridge(
            FakeTerminalPort("A", ""),
            battleContent = RuntimeContent(itemRecords = RylweenCompiledGame.nextDsl.itemCatalog),
            renderer = compiledTextRenderer(),
        ).run(
            service,
            scene,
            TownServiceContinuation.start(
                RuntimeSessionState.singleParty(
                    party = testParty(inventoryItems = mapOf(1 to Items.redGem)),
                    hour = 12,
                    currentTown = Towns.murn.id,
                ).copy(locations = listOf(LocationPlacement(Locations.beastCave, GridPoint(11, 23)))),
            ),
        )

        assertTrue(result.status is TownServiceBridgeStatus.Finished)
        assertTrue(result.party.hasFlagState(Story.jazzy.ogre.accepted))
        assertEquals(null, result.party.inventoryItem(1))
        assertEquals(listOf(JournalEntryState.Line("The 'Beast Cave' is at 11x23.")), result.party.journal)
    }

    @Test
    fun compiledGameSpeakRunsGarthDialogueBranchBeforeConversationMenu() {
        val service = BuildingServiceModel(
            building = Buildings.inn,
            action = keyAction("enter_inn"),
            scene = sceneId("test.speak_garth"),
            opensAt = null,
            closesAt = null,
            nativeServices = emptyList(),
        )
        val scene = SceneModel(service.scene, listOf(Op.Speak(Buildings.inn)))
        val result = StatefulTownServiceBridge(
            terminal = FakeTerminalPort("1", "B"),
            speakContent = compiledSpeakContent(),
            battleContent = RuntimeContent(itemRecords = RylweenCompiledGame.nextDsl.itemCatalog),
            renderer = compiledTextRenderer(),
        ).run(
            service,
            scene,
            TownServiceContinuation.start(
                RuntimeSessionState.singleParty(
                    party = testParty(gold = 10, flags = listOf(Story.garth.bandits.recovered)),
                    hour = 12,
                    currentTown = Towns.thord.id,
                ),
            ),
        )

        assertTrue(result.status is TownServiceBridgeStatus.Finished)
        assertEquals(2010, result.party.resources.gold)
        assertEquals(Items.redGem, result.party.inventoryItem(1))
        assertTrue(result.party.hasFlagState(Story.garth.bandits.closed))
        assertEquals(listOf(NpcVisitState(Npcs.garth, true)), result.party.visitedNpcs)
        assertTrue(result.events.any { event ->
            event is EngineTraceEvent.Menu && event.keys == listOf('C', 'Q', 'R', 'B')
        })
    }

    private fun garthRecoveredDialogueService(): Pair<BuildingServiceModel, SceneModel> {
        return dialogueBranchService(Npcs.garth, Story.garth.bandits.recovered)
    }

    private fun dialogueBranchService(npc: NpcId, state: FlagState): Pair<BuildingServiceModel, SceneModel> {
        val content = RylweenCompiledGame.nextDsl
        val branch = content.dialogue
            .single { it.npc == npc }
            .branches
            .single { it.whenState == state }
        val scene = SceneModel(sceneId("test.${npc.slug}.${state.native}"), branch.ops)
        val service = BuildingServiceModel(
            building = Buildings.inn,
            action = keyAction("enter_inn"),
            scene = scene.id,
            opensAt = null,
            closesAt = null,
            nativeServices = emptyList(),
        )
        return service to scene
    }

    private fun compiledSpeakContent(): SpeakRuntimeContent =
        RylweenCompiledGame.nextDsl.let { content ->
            SpeakRuntimeContent(
                speak = content.speak,
                characters = content.characters,
                dialogue = content.dialogue,
            )
        }

    private fun compiledTextRenderer(): TerminalTextRenderer =
        RylweenCompiledGame.nextDsl.let { content ->
            TerminalTextRenderer(
                towns = content.towns,
                characters = content.characters,
                dungeons = content.dungeons,
                prompts = content.prompts,
            )
        }

    private fun assertDungeonHandoffOutput(output: List<String>, dungeon: DungeonId, levels: Int) {
        val content = RylweenCompiledGame.nextDsl
        val handoffLines = compiledTextRenderer().lines(
            content.prompts.wilderness.renderDungeonHandoff(content.dungeons.displayName(dungeon), levels),
        )
        assertTrue(handoffLines.all { line -> line in output })
    }

    private fun compiledRuntimeWithRealizedNpcs(rngState: Long): RuntimeSessionState {
        val content = RylweenCompiledGame.nextDsl
        return RuntimeSessionState.singleParty(
            party = testParty(),
            hour = 12,
            currentTown = Towns.thord.id,
            rngState = rngState,
        ).realizeNpcPlacements(content.characters, content.towns)
    }

    private fun RuntimeSessionState.randomizedTownNpc(content: GameModel): NpcPlacement? =
        npcs.firstOrNull { placement ->
            content.characters.single { it.id == placement.npc }.placement == CharacterPlacementModel.Randomized &&
                placement.position is WorldPosition.Town
        }

    private fun WorldPosition.renderForTest(towns: List<TownModel>): String =
        when (this) {
            is WorldPosition.Town -> "in ${towns.single { it.id == town }.name}"
            is WorldPosition.Wilderness -> "at ${point.x}x${point.y}"
        }

    private fun compiledTownSession(
        party: PartyState,
        townId: TownId = Towns.thord.id,
        currentHour: Int = 12,
        rngState: Long? = null,
        npcs: List<NpcPlacement> = emptyList(),
    ): TownSession {
        val content = RylweenCompiledGame.nextDsl
        return TownSession.start(
            town = content.towns.single { it.id == townId },
            party = party,
            currentHour = currentHour,
            rngState = rngState,
            npcs = npcs,
            content = content.runtimeContent(),
        )
    }

    private fun compiledWildernessSession(
        party: PartyState? = null,
        hour: Int? = null,
        rngState: Long? = null,
        locations: List<LocationPlacement>? = null,
        npcs: List<NpcPlacement>? = null,
        worldMap: WorldMapGrid = WorldMapGrid.fromSourceRows((1..12).map { mapRow() }),
    ): WildernessSession {
        val content = RylweenCompiledGame.nextDsl
        val baseState = RylweenCompiledGame.nextRylween.states.single { it.id == States.freshReplay }
        val state = baseState.copy(
            realm = hour?.let { baseState.realm.copy(hour = it) } ?: baseState.realm,
            locations = locations ?: baseState.locations,
            parties = party?.let { listOf(it.copy(id = baseState.realm.activeParty)) } ?: baseState.parties,
            npcs = npcs ?: baseState.npcs,
        )
        return RylweenKotlinEngine(content).wildernessSession(
            state = state,
            worldMap = worldMap,
            rngState = rngState,
            realizeNpcs = false,
        )
    }

    private fun compiledBattleSession(
        tier: EncounterTier,
        party: PartyState = testParty(food = 100, timeLeft = 200, restedHours = 14),
    ): BattleSession {
        val content = RylweenCompiledGame.nextDsl
        return BattleSession.start(
            runtime = RuntimeSessionState.singleParty(
                party = party,
                hour = 12,
                currentTown = null,
            ),
            tier = tier,
            content = content.runtimeContent(),
        )
    }

    private fun compiledDungeonSession(
        dungeon: DungeonId,
        levels: Int,
        dungeons: DungeonSystemModel,
        party: PartyState = testParty(torches = levels),
        rngState: Long? = 1L,
        withCombat: Boolean = true,
    ): DungeonSession {
        val content = RylweenCompiledGame.nextDsl.runtimeContent().let {
            if (withCombat) it else it.copy(combat = CombatSystemModel(null))
        }
        return DungeonSession.start(
            runtime = RuntimeSessionState.singleParty(
                party = party,
                hour = 12,
                currentTown = null,
                rngState = rngState,
            ),
            worldMap = WorldMapGrid.fromSourceRows((1..4).map { mapRow(width = 4) }),
            dungeon = dungeon,
            levels = levels,
            content = content,
            dungeons = dungeons,
        )
    }

    private fun fixedCompiledDungeon(
        dungeon: DungeonId,
        vararg placements: Pair<Int, DungeonEventId>,
    ): DungeonSystemModel {
        val content = RylweenCompiledGame.nextDsl.dungeons
        val events = placements.map { it.second }.toSet()
        return content.copy(
            events = content.events.filter { it.id in events },
            scenarios = listOf(
                DungeonScenarioModel(
                    dungeon = dungeon,
                    levelRange = placements.minOfOrNull { it.first }?.let { lo ->
                        lo..checkNotNull(placements.maxOfOrNull { it.first })
                    },
                    fixedLevelEvents = placements.toMap(),
                    randomEvents = emptyList(),
                ),
            ),
        )
    }

    private fun customBattleSession(
        party: PartyState,
        monster: MonsterModel,
        tier: EncounterTier = EncounterTiers.weak,
    ): BattleSession =
        BattleSession.start(
            runtime = RuntimeSessionState.singleParty(
                party = party,
                hour = 12,
                currentTown = null,
            ),
            tier = tier,
            content = RylweenCompiledGame.nextDsl.runtimeContent().copy(monsters = listOf(monster)),
        )

    private fun testTown(vararg services: BuildingId): TownModel =
        TownModel(
            id = Towns.murn.id,
            name = "Murn",
            position = xy(55, 29),
            taxPercent = 8,
            kind = TownKind.Village,
            services = services.map { TownServiceModel(it) },
        )

    private fun testTownSystem(vararg services: BuildingId): TownSystemModel =
        TownSystemBuilder().apply {
            leavePrompts(EquipmentCommand.ReadyWeapon, EquipmentCommand.ReadyArmor)
            menu {
                services.forEach { building ->
                    when (building) {
                        Buildings.church -> church()
                        Buildings.smith -> smith()
                        Buildings.inn -> inn()
                        Buildings.merchant -> merchant()
                        Buildings.tavern -> tavern()
                        else -> service(building.slug.first().uppercaseChar(), building, keyAction("enter.${building.slug}"))
                    }
                }
                leave()
            }
            buildingServices {
                services.forEach { serviceBuilding ->
                    when (serviceBuilding) {
                        Buildings.church -> church()
                        Buildings.smith -> smith()
                        Buildings.inn -> inn()
                        Buildings.merchant -> merchant()
                        Buildings.tavern -> tavern()
                        else -> building(
                            serviceBuilding,
                            keyAction("enter.${serviceBuilding.slug}"),
                            sceneId("towne.${serviceBuilding.slug}"),
                        )
                    }
                }
            }
        }.build()

    private fun testMerchantShop(): ShopServiceModel =
        ShopServiceBuilder(Buildings.merchant).apply {
            image = Images.Scene.merchant
            intro("Merchant")
            menu("(B)uy item", "(S)ell item", "(L)eave")
            buy {
                prompt("Buy what?")
                catalog('B', "Goods") {
                    resource(
                        'A',
                        Items.torch,
                        resource = ShopResources.torches,
                        cap = ShopQuantityCaps.fixed(Resource.Torches, 80),
                        tooMany = inlineText("Too many torches."),
                        zeroQuantityDeal = ShopZeroQuantityDeal.always,
                    )
                    resource(
                        'B',
                        Items.ration,
                        resource = ShopResources.food,
                        cap = ShopQuantityCaps.carryCapacity(Resource.Food),
                        tooMany = inlineText("Too much food."),
                        price = ShopPrices.leaderLevelPlus(1),
                    )
                    item('C', Items.blanket)
                }
            }
            sell {
                prompt("Sell what?")
                accepts(Items.blanket)
                wrongKind("Wrong kind.")
                offer {
                    line("Offer: ", price)
                }
            }
        }.build()

    private fun testSmithShop(): ShopServiceModel =
        ShopServiceBuilder(Buildings.smith).apply {
            image = Images.Scene.smith
            intro("Smith")
            menu("(B)uy item", "(S)ell item", "(L)eave")
            buy {
                prompt("Buy what?")
                catalog('B', "Weapons") {
                    item('A', Items.sword)
                }
            }
            sell {
                prompt("Sell what?")
                acceptsKinds(ItemKind.Weapon, ItemKind.Armor)
                wrongKind("Wrong kind.")
                offer {
                    line("Offer: ", price)
                }
                smithHaggle()
            }
        }.build()

    private fun testMultiCatalogSmithShop(): ShopServiceModel =
        ShopServiceBuilder(Buildings.smith).apply {
            image = Images.Scene.smith
            intro("Smith")
            menu("(B)uy item", "(L)eave")
            buy {
                prompt(
                    "Armor or Weapon?",
                    "",
                    "(A)rmor",
                    "(W)eapon",
                    "(N)othing",
                )
                catalog('A', "Armor") {
                    intro("Armor list")
                    item('1', Items.leather, label = "Leather")
                }
                catalog('W', "Weapons") {
                    intro("Weapon list")
                    item('1', Items.sword, label = "Sword")
                }
            }
        }.build()

    private fun testRandomItemOffer(): RandomItemOfferModel =
        RandomItemOfferBuilder(RylweenCompiledGame.nextDsl.itemPool(ItemPools.tavernScruffySpecials)).apply {
            offer {
                line("- I will sell you this")
                line("  ", itemName)
                line("  for the ridiculously")
                line("  low price of ", itemPrice)
                line("  Aurum!")
                line("")
            }
            broke("- You don't have that", "  much gold!")
            full("- You can't carry it!")
            deal("- Deal!!!")
            declined("- Fine, I can live with", "  that.")
        }.build()

    private fun testChurchScene(): SceneModel =
        GameBuilder().apply {
            scene(Scenes.Towne.church) {
                clearMapWindow()
                showBackdrop(Images.Scene.church)

                loop {
                    clearText()
                    statusLine()
                    screen(T.Towne.Church.intro)

                    menu(T.Towne.Church.menu) {
                        option('H') {
                            clearText()
                            val member = choice.member(
                                T.Towne.Church.who,
                                filter = MemberChoiceFilter.AliveOnly,
                                accepting = MemberChoiceFilter.Any,
                            )

                            whenever(member.dead) {
                                screen("- GET THAT CORPSE", "OUTTA HERE!", "(You are thrown out).", "")
                                pause(1200)
                                breakLoop("corpse exits the church service")
                            }

                            whenever(member.hp eq member.maxHp) {
                                screen {
                                    line(pronoun(member, PronounCase.Subject), " needs no healing.")
                                }
                                pause(1000)
                                stop("already fully healed")
                            }

                            val price = localInt((member.maxHp - member.hp) * 7)
                            clearText()
                            screen {
                                line("- That'll be ", price)
                                line("gold coins, please.")
                                line("")
                            }

                            choice.payNoThanks(price,
                                broke = {
                                    screen("You don't have enough", "gold!")
                                    pause(1000)
                                },
                                paid = {
                                    clearText()
                                    member.hp set member.maxHp
                                    screen {
                                        line("The priestess heals")
                                        line(pronoun(member, PronounCase.Object), " fully.")
                                    }
                                    pause(1000)
                                },
                            )
                        }

                        option('E') {
                            clearText()
                            val member = choice.member(
                                T.Towne.Church.who,
                                filter = MemberChoiceFilter.SpellcasterOnly,
                                accepting = MemberChoiceFilter.Any,
                            )

                            whenever(!member.spellcaster) {
                                clearText()
                                screen("Not a spellcaster!")
                                pause(1000)
                                stop("not a spellcaster")
                            }

                            whenever(member.mp eq member.wisdom) {
                                clearText()
                                screen("That character needs", "no spell points", "to be restored.")
                                pause(2000)
                                stop("spell points already restored")
                            }

                            clearText()
                            val price = localInt(member.level * (member.wisdom - member.mp) * 2)
                            screen {
                                line("- That will be ", price)
                                line("gold coins, please.")
                                line("")
                            }

                            choice.payNoThanks(price,
                                broke = {
                                    clearText()
                                    screen("You don't have the gold!")
                                    pause(1000)
                                },
                                paid = {
                                    member.mp set member.wisdom
                                    clearText()
                                    screen("The priestess", "restores his energy.")
                                    pause(1000)
                                },
                            )
                        }

                        option('R') {
                            clearText()
                            val member = choice.member(
                                T.Towne.Church.who,
                                filter = MemberChoiceFilter.DeadOnly,
                                accepting = MemberChoiceFilter.Any,
                            )

                            whenever(!member.dead) {
                                clearText()
                                screen {
                                    line("- ", pronoun(member, PronounCase.Subject), " seems very alive")
                                    line("at the moment.")
                                }
                                pause(1200)
                                stop("already alive")
                            }

                            clearText()
                            val price = localInt(member.level * 650)
                            screen {
                                line("- That will be ", price)
                                line("gold coins, please.")
                                line("")
                            }

                            choice.payNoThanks(price,
                                broke = {
                                    clearText()
                                    screen("You don't have the gold!")
                                    pause(1000)
                                },
                                paid = {
                                    member.hp set 1
                                    member.dead set false
                                    party.memberCount += 1
                                    clearText()
                                    screen {
                                        line("The priestess raises")
                                        line(pronoun(member, PronounCase.Object), ".")
                                    }
                                    pause(1000)
                                },
                            )
                        }

                        option('L') {
                            clearText()
                            clearMapWindow()
                            breakLoop("leave church")
                        }
                    }
                }
            }
        }.build().scenes.single()

    private fun testTavernRandomOfferScene(): SceneModel =
        GameBuilder().apply {
            items {
                scruffyOfferTestItems()
            }
            scene(Scenes.Towne.tavern) {
                clearMapWindow()
                showBackdrop(Images.Scene.tavern)

                choice.randomItemOffer(ItemPools.tavernScruffySpecials) {
                    offer {
                        line("- I will sell you this")
                        line("  ", itemName)
                        line("  for the ridiculously")
                        line("  low price of ", itemPrice)
                        line("  Aurum!")
                        line("")
                    }
                    broke("- You don't have that", "  much gold!")
                    full("- You can't carry it!")
                    deal("- Deal!!!")
                    declined("- Fine, I can live with", "  that.")
                }

                loop {
                    clearText()
                    menu("(L)eave") {
                        option('L') {
                            clearText()
                            clearMapWindow()
                            breakLoop("leave tavern")
                        }
                    }
                }
            }
        }.build().scenes.single()

    private fun testInnScene(): SceneModel =
        GameBuilder().apply {
            scene(Scenes.Towne.inn) {
                clearMapWindow()
                showBackdrop(Images.Scene.inn)

                loop {
                    val cost = localInt((party.leader.level * party.leader.level) * 15)

                    clearText()
                    screen {
                        line(townNpcName(NpcJobs.innkeeper), ", the Innkeeper")
                        line("welcomes you.")
                        line("")
                        line("Do you want supper,")
                        line("travellers?")
                        line("")
                    }
                    choice.yesNo("(Y)es", "(N)o",
                        yes = {
                            whenever(party.gold lt cost) {
                                clearText()
                                screen {
                                    line("Sorry, supper is ", cost)
                                    line("gold.")
                                }
                                pause(1000)
                            } otherwise {
                                party.gold -= cost
                                clearText()
                                screen {
                                    lines(
                                        "You are served a well",
                                        "tasting dinner. Some",
                                        "of your health returns.",
                                        "",
                                    )
                                }
                                healLivingMembers(6)
                                waitInput("after inn supper")
                            }
                        },
                    )

                    clearText()
                    choice.yesNo("Anything to drink?", "",
                        yes = {
                            clearText()
                            menu(
                                "What'll it be?",
                                "",
                                "A. Ale         2 au",
                                "B. Mead        3 au",
                                "C. Wine        5 au",
                                "D. Whiskey     7 au",
                                "E. Fanta       1 au",
                            ) {
                                option('A') {
                                    pay(lit(2),
                                        broke = {
                                            clearText()
                                            screen("No money - No drink!")
                                            pause(1000)
                                        },
                                        ok = {
                                            clearText()
                                            screen("Ahh...!")
                                            pause(1000)
                                            healLivingMembers((lit(2) / 2) + 1)
                                        },
                                    )
                                }
                                option('B') {
                                    pay(lit(3),
                                        broke = {
                                            clearText()
                                            screen("No money - No drink!")
                                            pause(1000)
                                        },
                                        ok = {
                                            clearText()
                                            screen("Ahh...!")
                                            pause(1000)
                                            healLivingMembers((lit(3) / 2) + 1)
                                        },
                                    )
                                }
                                option('C') {
                                    pay(lit(5),
                                        broke = {
                                            clearText()
                                            screen("No money - No drink!")
                                            pause(1000)
                                        },
                                        ok = {
                                            clearText()
                                            screen("Ahh...!")
                                            pause(1000)
                                            healLivingMembers((lit(5) / 2) + 1)
                                        },
                                    )
                                }
                                option('D') {
                                    pay(lit(7),
                                        broke = {
                                            clearText()
                                            screen("No money - No drink!")
                                            pause(1000)
                                        },
                                        ok = {
                                            clearText()
                                            screen("Ahh...!")
                                            pause(1000)
                                            healLivingMembers((lit(7) / 2) + 1)
                                        },
                                    )
                                }
                                option('E') {
                                    pay(lit(1),
                                        broke = {
                                            clearText()
                                            screen("No money - No drink!")
                                            pause(1000)
                                        },
                                        ok = {
                                            clearText()
                                            screen("The other guests giggle.")
                                            pause(1000)
                                            healLivingMembers((lit(1) / 2) + 1)
                                        },
                                    )
                                }
                            }
                        },
                    )

                    clearText()
                    choice.yesNo("Are you in need", "of lodging?", "",
                        yes = {
                            clearText()
                            screen {
                                line("A room is ", cost, " gold/night.")
                                line("")
                            }

                            choice.takeOrLeave(
                                inlineText("(T)ake it", "(L)eave"),
                                take = {
                                    whenever(party.gold lt cost) {
                                        clearText()
                                        screen {
                                            lines(
                                                "- AHA! No gold!",
                                                "You are thrown out.",
                                            )
                                        }
                                        pause(2000)
                                        clearMapWindow()
                                        breakLoop("thrown out of inn")
                                    } otherwise {
                                        party.gold -= cost
                                        clearText()
                                        screen("You go to bed...")
                                        pause(1000)
                                        screen("Zzzz...", "")
                                        party.restedHours set 14
                                        pause(1500)
                                        screen {
                                            lines(
                                                "You wake up a little",
                                                "before dawn, well",
                                                "rested.",
                                                "",
                                                "You have breakfast",
                                                "and leave the inn.",
                                                "",
                                            )
                                        }
                                        waitInput("after inn lodging")
                                        realm.hour set 5
                                        forEachMember {
                                            it.hp.addClamped(it.maxHp / 4, it.maxHp)
                                        }
                                        clearMapWindow()
                                        breakLoop("slept at inn")
                                    }
                                },
                                leave = {
                                    clearText()
                                    clearMapWindow()
                                    breakLoop("leave inn")
                                },
                            )
                        },
                        no = {
                            loop {
                                clearText()
                                screen("The innkeeper leaves.", "")
                                menu("(C)hitchat with guests", "(L)eave") {
                                    option('C') {
                                        speak(Buildings.tavern)
                                    }
                                    option('L') {
                                        clearText()
                                        clearMapWindow()
                                        breakLoop("leave inn chitchat")
                                    }
                                    default {
                                        clearText()
                                        clearMapWindow()
                                        breakLoop("leave inn chitchat")
                                    }
                                }
                            }
                            breakLoop("leave inn after chitchat")
                        },
                    )
                }
            }
        }.build().scenes.single()

    private fun testTavernSpeakRumourScene(): SceneModel =
        GameBuilder().apply {
            scene(Scenes.Towne.tavern) {
                loop {
                    clearText()
                    menu("(S)peak", "(R)umors", "(L)eave") {
                        option('S') {
                            speak(Buildings.inn)
                        }
                        option('R') {
                            clearText()
                            run(Scenes.Towne.rumour)
                            waitInput("after tavern rumour")
                        }
                        option('L') {
                            clearText()
                            clearMapWindow()
                            breakLoop("leave tavern")
                        }
                    }
                }
            }
        }.build().scenes.single()

    private fun testSpeakContent(): SpeakRuntimeContent =
        SpeakRuntimeContent(
            speak = SpeakBuilder().apply {
                standardConversation {
                    menu {
                        chitchat()
                        question {
                            lodging()
                            magic()
                            people()
                            nothing()
                        }
                        rumour()
                        bye()
                    }
                }
                textTables {
                    screen(T.Speak.noNpc) {
                        lines("Noone is here!")
                    }
                    screen(T.Speak.noRumour) {
                        lines("- Sorry. There's", "nothing new I can", "tell you.", "")
                    }
                    chitchatPrompt(ChitchatSubject.Well) {
                        male {
                            lines("- You look well today,", "  Sir...")
                        }
                    }
                    chitchatReplies(ChitchatSubject.Well) {
                        job(NpcJobs.tavernkeeper) {
                            lines("- You've had too much", "to drink, boy?")
                        }
                        job(NpcJobs.innkeeper) {
                            lines("- The same to you.")
                        }
                    }
                    questionReplies(QuestionTopic.People) {
                        job(NpcJobs.innkeeper) {
                            lines("Ask people at the inn.")
                        }
                    }
                    rumourTable(RumourTables.standardTown) {
                        rumour {
                            lines("A merchant needs bandits gone.")
                            excludeWhen(Story.garth.bandits.recovered)
                        }
                        rumour {
                            lines("Ydarg waits in the south.")
                        }
                    }
                }
            }.build(),
            characters = listOf(
                CharacterModel(
                    id = Npcs.dargoth,
                    name = "Dargoth",
                    job = NpcJobs.tavernkeeper,
                    gender = Gender.Male,
                    placement = CharacterPlacementModel.Fixed(Towns.murn.tavern),
                    roles = emptyList(),
                ),
                CharacterModel(
                    id = Npcs.yog,
                    name = "Yog",
                    job = NpcJobs.innkeeper,
                    gender = Gender.Male,
                    placement = CharacterPlacementModel.Fixed(Towns.murn.inn),
                    roles = emptyList(),
                ),
            ),
        )

    private fun testMember(
        name: String = "Tester",
        gender: Gender = Gender.Male,
        race: Race = Race.Human,
        pcClass: PcClass = PcClass.Fighter,
        level: Int = 1,
        health: Int = 10,
        hitPoints: Int = 10,
        weaponSkill: Int = 10,
        wisdom: Int = 10,
        mana: Int = 0,
        charisma: Int = 10,
        equipment: EquipmentState = EquipmentState(Items.none, Items.none),
        experience: Int = 0,
        dead: Boolean = false,
    ): PartyMemberState =
        PartyMemberState(
            name = name,
            gender = gender,
            race = race,
            pcClass = pcClass,
            level = level,
            health = health,
            hitPoints = hitPoints of health,
            weaponSkill = weaponSkill,
            wisdom = wisdom,
            mana = mana manaOf wisdom,
            charisma = charisma,
            equipment = equipment,
            experience = experience,
            dead = dead,
        )

    private fun mapRow(vararg overrides: Pair<Int, Char>, width: Int = 20, fill: Char = 's'): String =
        CharArray(width) { fill }
            .apply {
                overrides.forEach { (x, tile) -> this[x] = tile }
            }
            .concatToString()

    private fun testParty(
        gold: Int = 0,
        level: Int = 1,
        torches: Int = 0,
        food: Int = 0,
        timeLeft: Int = 0,
        restedHours: Int = 0,
        hitPoints: Int = 10,
        health: Int = 10,
        mana: Int = 0,
        wisdom: Int = 10,
        pcClass: PcClass = PcClass.Fighter,
        dead: Boolean = false,
        memberCount: Int? = null,
        equipment: EquipmentState = EquipmentState(Items.none, Items.none),
        inventoryItems: Map<Int, ItemId> = emptyMap(),
        flags: List<FlagState> = emptyList(),
        milestones: List<GameMilestoneId> = emptyList(),
        members: List<PartyMemberState> = listOf(
            testMember(
                pcClass = pcClass,
                level = level,
                health = health,
                hitPoints = hitPoints,
                wisdom = wisdom,
                mana = mana,
                equipment = equipment,
                dead = dead,
            ),
        ),
    ): PartyState =
        PartyState(
            id = partyId("test"),
            player = "Test",
            name = "Test",
            position = Wilderness.xy(0, 0),
            facing = Facing.South,
            resources = PartyResources(
                gold = gold,
                torches = torches,
                food = food,
                timeLeft = timeLeft,
                restedHours = restedHours,
            ),
            counters = PartyCounters(
                memberCount = memberCount ?: members.count { !it.dead },
                kills = 0,
                waitingMail = false,
                attacked = false,
                attackedToday = emptyList(),
            ),
            flags = flags,
            milestones = milestones,
            inventory = (0..InventorySaveSlotMax).map { slot ->
                InventorySlotState(slot, inventoryItems[slot])
            },
            members = members,
            visitedNpcs = emptyList(),
            journal = emptyList(),
        )

    private open class FakeTerminalPort(vararg inputs: String) : TerminalPort {
        private val queue = ArrayDeque(inputs.toList())
        val output = mutableListOf<String>()

        override fun write(lines: List<String>) {
            output += lines
        }

        override fun readLine(): String? =
            queue.removeFirstOrNull()
    }

    private class RecordingTerminalPort(vararg inputs: String) : FakeTerminalPort(*inputs) {
        val imageSlugs = mutableListOf<String>()
        val viewCommands = mutableListOf<ViewCommand>()

        override fun writeViewCommand(
            command: ViewCommand,
            renderer: TerminalTextRenderer,
            context: TerminalTextContext,
        ): Boolean {
            viewCommands += command
            if (command is ViewCommand.ShowImage) {
                imageSlugs += command.image.slug
            }
            return true
        }
    }
}
