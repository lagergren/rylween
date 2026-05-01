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
}
