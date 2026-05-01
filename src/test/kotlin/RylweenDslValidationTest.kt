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

    private fun testParty(
        gold: Int = 0,
        level: Int = 1,
        torches: Int = 0,
        food: Int = 0,
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
                memberCount = 1,
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
                    pcClass = PcClass.Fighter,
                    level = level,
                    health = 10,
                    hitPoints = 10 of 10,
                    weaponSkill = 10,
                    wisdom = 10,
                    mana = 0 manaOf 10,
                    charisma = 10,
                    equipment = equipment,
                    experience = 0,
                    dead = false,
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
