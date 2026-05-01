import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
                rngSeed = 1234L,
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
        assertTrue("""rngSeed = 1234L""" in snapshot)
        assertTrue("""mark(runtimeFlagState("garth.bandits", "recovered"))""" in snapshot)
        assertTrue("""slot(16).empty()""" in snapshot)
        assertTrue("""line("Castle Ydarg has fallen.")""" in snapshot)
    }

    @Test
    fun traceEngineRecordsNativeBoundaryRequests() {
        val keyRequest = EngineRequest(NativeAllowlist.inputKeyUpper.path)
        val engine = TraceEnginePort(ints = mapOf(keyRequest to 82))
        val runner = ScriptTraceRunner(engine)

        runner.run(
            listOf(
                Op.NativeCall(NativeAllowlist.screenClearText.path, emptyList()),
                Op.LetInt("key", IntAst.NativeInt(keyRequest.path, keyRequest.args)),
                Op.EmitIndexed(TextPattern("key.{}"), IntAst.Var("key")),
            ),
        )

        assertEquals(
            EngineTraceEvent.NativeEffect(EngineRequest(NativeAllowlist.screenClearText.path)),
            engine.events[0],
        )
        assertEquals(EngineTraceEvent.NativeInt(keyRequest, 82), engine.events[1])
        assertTrue(engine.events[2] is EngineTraceEvent.EmitIndexed)
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
    fun randomItemOfferValidatesAndTracesAsReusableRuntimeAction() {
        val content = GameBuilder().apply {
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

        validateScriptRewards(content)
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
            ItemPools.tavernScruffySpecials.items,
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
    fun shopServiceValidatesAndDefaultsInventoryPurchasesToPromptDrop() {
        val content = GameBuilder().apply {
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
                                cap = ShopQuantityCaps.torchLimit80,
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

        validateScriptRewards(content)

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
        val interpreter = ShopServiceInterpreter()

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
        val interpreter = ShopServiceInterpreter()

        val boughtNoDonkeyFood = interpreter.buy(shop, noDonkeyParty, Items.ration, quantity = 999)
        val boughtDonkeyFood = interpreter.buy(shop, donkeyParty, Items.ration, quantity = 999)
        val boughtTorches = interpreter.buy(shop, testParty(gold = 10, torches = 78), Items.torch, quantity = 99)

        assertEquals(150, testParty(inventoryItems = mapOf(0 to Items.donkey)).foodCapacityByDonkeys())
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
        val interpreter = ShopServiceInterpreter()

        val bought = interpreter.buy(shop, fullParty, Items.blanket, dropSlot = 1)

        assertEquals(ShopTransactionStatus.Bought, bought.status)
        assertEquals(Items.blanket, bought.party.inventoryItem(1))
        assertEquals(150, bought.party.foodCapacityByDonkeys())
        assertEquals(150, bought.party.resources.food)
    }

    @Test
    fun campGearEffectReflectsTentAndBlanketProtection() {
        val noGear = testParty().campGearEffect()
        val blanket = testParty(inventoryItems = mapOf(1 to Items.blanket)).campGearEffect()
        val tent = testParty(inventoryItems = mapOf(1 to Items.tent)).campGearEffect()
        val reservedSlotTent = testParty(inventoryItems = mapOf(0 to Items.tent)).campGearEffect()

        assertEquals(Images.Scene.noTent, noGear.image)
        assertEquals(false, noGear.protectsLowTemperature)
        assertEquals(false, noGear.protectsVeryLowTemperature)
        assertEquals(Images.Scene.noTent, blanket.image)
        assertEquals(true, blanket.protectsLowTemperature)
        assertEquals(false, blanket.protectsVeryLowTemperature)
        assertEquals(Images.Scene.tent, tent.image)
        assertEquals(true, tent.protectsLowTemperature)
        assertEquals(true, tent.protectsVeryLowTemperature)
        assertEquals(Images.Scene.noTent, reservedSlotTent.image)
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

        var session = ShopServiceSession.start(shop, party).continueIntro()

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
    fun shopSessionQuotesSmithHaggleBeforeSale() {
        val shop = testSmithShop()
        var session = ShopServiceSession.start(
            shop,
            testParty(gold = 0, inventoryItems = mapOf(1 to Items.sword, 2 to Items.axe)),
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
            ShopServiceSession.start(shop, testParty(gold = 10, torches = 78)),
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
        assertEquals(EngineTraceEvent.Screen((buy.offerFor(Items.torch)!!.purchase as ShopPurchaseModel.Resource).quantityPrompt), engine.events[3])
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
            ShopServiceSession.start(shop, party),
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
            ShopServiceSession.start(testMerchantShop(), testParty(gold = 10, torches = 78)),
        )

        assertEquals(80, buySession.party.resources.torches)
        assertEquals(8, buySession.party.resources.gold)
        assertTrue(buySession.step is ShopServiceStep.Finished)
        assertTrue(buyTerminal.output.any { "Options: B/S/L" in it })
        assertTrue(buyTerminal.output.any { "(A) torch" in it })

        val saleTerminal = FakeTerminalPort("S", "A", "Y", "L")
        val saleSession = ShopServiceTerminalAdapter(saleTerminal).run(
            ShopServiceSession.start(
                testMerchantShop(),
                testParty(gold = 10, inventoryItems = mapOf(1 to Items.blanket)),
            ),
        )

        assertEquals(25, saleSession.party.resources.gold)
        assertEquals(null, saleSession.party.inventoryItem(1))
        assertTrue(saleTerminal.output.any { "Offer: 15" in it })
    }

    @Test
    fun shopInterpreterSellsAcceptedItemsAndRejectsWrongShopItems() {
        val shop = testMerchantShop()
        val party = testParty(
            gold = 10,
            inventoryItems = mapOf(1 to Items.blanket, 2 to Items.sword),
        )
        val interpreter = ShopServiceInterpreter()

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

        val equippedSword = party.equipFromInventory(memberNumber = 1, slot = 1, kind = EquipmentKind.Weapon)
                as EquipmentActionResult.Equipped
        val equippedAxe = equippedSword.party.equipFromInventory(
            memberNumber = 1,
            slot = 2,
            kind = EquipmentKind.Weapon,
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

        val readied = party.applyEquipmentCommand(EquipmentCommand.ReadyWeapon, memberNumber = 1, inventorySlot = 1)
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
            ),
            listOf(EquipmentPromptInput.InventorySlot(1), EquipmentPromptInput.Member(1)),
        )
        val removeEngine = TraceEnginePort()
        val removed = EquipmentPromptTraceRunner(removeEngine).run(
            EquipmentPromptSession.start(
                EquipmentCommand.RemoveWeapon,
                testParty(equipment = EquipmentState(Items.sword, Items.none)),
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

        val session = runner.run(
            TownLeaveEquipmentSession.start(
                testParty(inventoryItems = mapOf(1 to Items.sword, 2 to Items.leather)),
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
        assertEquals(
            listOf(EquipmentPromptText.readyWeapons, EquipmentPromptText.readyArmors),
            engine.events.filterIsInstance<EngineTraceEvent.Menu>().map { it.prompt },
        )
        assertEquals(2, engine.events.filterIsInstance<EngineTraceEvent.EquipmentAction>().size)
    }

    @Test
    fun equipmentTerminalAdaptersRunReadyOffAndTownLeavePrompts() {
        val readyTerminal = FakeTerminalPort("A", "1")
        val ready = EquipmentPromptTerminalAdapter(readyTerminal).run(
            EquipmentPromptSession.start(
                EquipmentCommand.ReadyWeapon,
                testParty(inventoryItems = mapOf(1 to Items.sword)),
            ),
        )
        val offTerminal = FakeTerminalPort("1")
        val removed = EquipmentPromptTerminalAdapter(offTerminal).run(
            EquipmentPromptSession.start(
                EquipmentCommand.RemoveWeapon,
                testParty(equipment = EquipmentState(Items.sword, Items.none)),
            ),
        )
        val leaveTerminal = FakeTerminalPort("Y", "A", "1", "Y", "B", "1")
        val leave = TownLeaveEquipmentTerminalAdapter(leaveTerminal).run(
            TownLeaveEquipmentSession.start(
                testParty(inventoryItems = mapOf(1 to Items.sword, 2 to Items.leather)),
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
    fun terminalAdaptersReturnUnfinishedSessionOnInvalidInput() {
        val invalidShop = ShopServiceTerminalAdapter(FakeTerminalPort("X")).runUntilBlocked(
            ShopServiceSession.start(testMerchantShop(), testParty(gold = 10)),
        )
        val missingShopInput = ShopServiceTerminalAdapter(FakeTerminalPort()).runUntilBlocked(
            ShopServiceSession.start(testMerchantShop(), testParty(gold = 10)),
        )
        val invalidTownLeave = TownLeaveEquipmentTerminalAdapter(FakeTerminalPort("X")).runUntilBlocked(
            TownLeaveEquipmentSession.start(testParty(inventoryItems = mapOf(1 to Items.sword))),
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
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertTrue(session.session.step is TownSessionStep.Finished)
        assertEquals(80, session.session.party.resources.torches)
        assertEquals(8, session.session.party.resources.gold)
        assertTrue(terminal.output.any { "(M) Merchant" in it })
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
                            Op.NativeCall(NativeAllowlist.screenClearText.path, emptyList()),
                        ),
                    ),
                ),
                party = testParty(gold = 10),
                currentHour = 12,
            ),
        )

        assertTrue(session.status is TerminalRunStatus.Finished)
        assertTrue(session.session.step is TownSessionStep.Finished)
        assertTrue(terminal.output.any { "Service: church" in it })
        assertTrue(terminal.output.any { "Church body" in it })
        assertTrue(terminal.output.any { "Native: screen.clear_text" in it })
        assertEquals(TownSessionResultStatus.NativeServiceFinished, session.session.lastResult?.status)
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

        val blocked = TownSessionTerminalAdapter(FakeTerminalPort("C")).runUntilBlocked(start)
        val blockedStep = blocked.session.step as TownSessionStep.NativeService

        assertTrue(blocked.status is TerminalRunStatus.Waiting)
        assertEquals(6, blocked.session.realm.hour)
        assertEquals(6, blockedStep.continuation?.runtime?.realm?.hour)

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
                        cap = ShopQuantityCaps.torchLimit80,
                        tooMany = inlineText("Too many torches."),
                        zeroQuantityDeal = ShopZeroQuantityDeal.always,
                    )
                    resource(
                        'B',
                        Items.ration,
                        resource = ShopResources.food,
                        cap = ShopQuantityCaps.foodByDonkeys,
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

    private fun testChurchScene(): SceneModel =
        GameBuilder().apply {
            scene(Scenes.Towne.church) {
                native.screen.clearMapWindow()
                native.screen.showImage(Images.Scene.church)

                loop {
                    native.screen.clearText()
                    native.screen.statusLine()
                    screen(T.Towne.Church.intro)

                    menu(T.Towne.Church.menu) {
                        option('H') {
                            native.screen.clearText()
                            val member = choice.member(T.Towne.Church.who)

                            when_(member.dead) {
                                screen("- GET THAT CORPSE", "OUTTA HERE!", "(You are thrown out).", "")
                                pause(1200)
                                breakLoop("corpse exits the church service")
                            }

                            when_(member.hp eq member.maxHp) {
                                screen {
                                    line(pronoun(member, PronounCase.Subject), " needs no healing.")
                                }
                                pause(1000)
                                stop("already fully healed")
                            }

                            val price = localInt((member.maxHp - member.hp) * 7)
                            native.screen.clearText()
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
                                    native.screen.clearText()
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
                            native.screen.clearText()
                            val member = choice.member(T.Towne.Church.who)

                            when_(!member.spellcaster) {
                                native.screen.clearText()
                                screen("Not a spellcaster!")
                                pause(1000)
                                stop("not a spellcaster")
                            }

                            when_(member.mp eq member.wisdom) {
                                native.screen.clearText()
                                screen("That character needs", "no spell points", "to be restored.")
                                pause(2000)
                                stop("spell points already restored")
                            }

                            native.screen.clearText()
                            val price = localInt(member.level * (member.wisdom - member.mp) * 2)
                            screen {
                                line("- That will be ", price)
                                line("gold coins, please.")
                                line("")
                            }

                            choice.payNoThanks(price,
                                broke = {
                                    native.screen.clearText()
                                    screen("You don't have the gold!")
                                    pause(1000)
                                },
                                paid = {
                                    member.mp set member.wisdom
                                    native.screen.clearText()
                                    screen("The priestess", "restores his energy.")
                                    pause(1000)
                                },
                            )
                        }

                        option('R') {
                            native.screen.clearText()
                            val member = choice.member(T.Towne.Church.who)

                            when_(!member.dead) {
                                native.screen.clearText()
                                screen {
                                    line("- ", pronoun(member, PronounCase.Subject), " seems very alive")
                                    line("at the moment.")
                                }
                                pause(1200)
                                stop("already alive")
                            }

                            native.screen.clearText()
                            val price = localInt(member.level * 650)
                            screen {
                                line("- That will be ", price)
                                line("gold coins, please.")
                                line("")
                            }

                            choice.payNoThanks(price,
                                broke = {
                                    native.screen.clearText()
                                    screen("You don't have the gold!")
                                    pause(1000)
                                },
                                paid = {
                                    member.hp set 1
                                    member.dead set false
                                    party.memberCount += 1
                                    native.screen.clearText()
                                    screen {
                                        line("The priestess raises")
                                        line(pronoun(member, PronounCase.Object), ".")
                                    }
                                    pause(1000)
                                },
                            )
                        }

                        option('L') {
                            native.screen.clearText()
                            native.screen.clearMapWindow()
                            breakLoop("leave church")
                        }
                    }
                }
            }
        }.build().scenes.single()

    private fun testParty(
        gold: Int = 0,
        level: Int = 1,
        torches: Int = 0,
        food: Int = 0,
        hitPoints: Int = 10,
        health: Int = 10,
        mana: Int = 0,
        wisdom: Int = 10,
        pcClass: PcClass = PcClass.Fighter,
        dead: Boolean = false,
        memberCount: Int = 1,
        equipment: EquipmentState = EquipmentState(Items.none, Items.none),
        inventoryItems: Map<Int, ItemId> = emptyMap(),
    ): PartyState =
        PartyState(
            id = partyId("test"),
            player = "Test",
            name = "Test",
            position = Wilderness.xy(0, 0),
            facing = Facing.South,
            resources = PartyResources(gold = gold, torches = torches, food = food, timeLeft = 0, restedHours = 0),
            counters = PartyCounters(
                memberCount = memberCount,
                kills = 0,
                waitingMail = false,
                attacked = false,
                attackedToday = emptyList(),
            ),
            flags = emptyList(),
            milestones = emptyList(),
            inventory = (0..InventorySaveSlotMax).map { slot ->
                InventorySlotState(slot, inventoryItems[slot])
            },
            members = listOf(
                PartyMemberState(
                    name = "Tester",
                    gender = Gender.Male,
                    race = Race.Human,
                    pcClass = pcClass,
                    level = level,
                    health = health,
                    hitPoints = hitPoints of health,
                    weaponSkill = 10,
                    wisdom = wisdom,
                    mana = mana manaOf wisdom,
                    charisma = 10,
                    equipment = equipment,
                    experience = 0,
                    dead = dead,
                ),
            ),
            visitedNpcs = emptyList(),
            journal = emptyList(),
        )

    private class FakeTerminalPort(vararg inputs: String) : TerminalPort {
        private val queue = ArrayDeque(inputs.toList())
        val output = mutableListOf<String>()

        override fun write(lines: List<String>) {
            output += lines
        }

        override fun readLine(): String? =
            queue.removeFirstOrNull()
    }
}
