// RylweenGame.dsl.kts -- game-specific Rylween content source.
//
// This file should contain game declarations: flags, progress milestones,
// monsters, missions, scripts, and states. DSL implementation code belongs in
// src/main/kotlin/rylween; reusable current-game script helpers belong in
// src/game/kotlin/rylween/game/GameScripts.kt.
//
// Validate with:
//
//   ./gradlew check

import rylween.dsl.*
import rylween.game.*

// =============================================================================
// 06. Full-game authoring area
// =============================================================================
//
// This section contains the authored current game in the order below:
//
//   content:
//     metadata, limits, handles, visuals, layouts, terrain, maps, items,
//     monsters, spells, text, scenes, missions, towns, buildings, menus.
//
//   states:
//     authored start states first, then exact runtime-save-shaped fixtures.
//
// Add new game content here; reusable DSL/runtime implementation belongs in the
// packages named above, not in this game-authoring file.

val nextRylween: RylweenModel = rylween {
    content {
    metadata {
        universe("Rylween", version = "kotlin-dsl")
    }

    limits {
        maxWorldMaps = 1
        maxMapCells = 3120
        screenSize = cells(width = 80, height = 24)
    }

    flags {
        story(Story.garth.bandits) {
            states(
                Story.garth.bandits.none,
                Story.garth.bandits.accepted,
                Story.garth.bandits.recovered,
                Story.garth.bandits.closed,
            )
        }
        story(Story.ranger.letter) {
            states(
                Story.ranger.letter.none,
                Story.ranger.letter.accepted,
                Story.ranger.letter.delivered,
            )
        }
        story(Story.fighter.priestBook) {
            states(
                Story.fighter.priestBook.none,
                Story.fighter.priestBook.priestTold,
                Story.fighter.priestBook.priestVisited,
                Story.fighter.priestBook.wizardRobbed,
                Story.fighter.priestBook.fighterGrateful,
            )
        }
        story(Story.church.donationOnce) {
            states(
                Story.church.donationOnce.no,
                Story.church.donationOnce.yes,
            )
        }
        story(Story.smith.beerOffer) {
            states(Story.smith.beerOffer.offered)
        }
        story(Story.silverKey) {
            states(
                Story.silverKey.none,
                Story.silverKey.mapBought,
                Story.silverKey.noteFound,
                Story.silverKey.thiefSqueezed,
                Story.silverKey.smithConfessed,
                Story.silverKey.caveLocated,
                Story.silverKey.keyTaken,
            )
        }
        story(Story.erethor.lute) {
            states(
                Story.erethor.lute.none,
                Story.erethor.lute.rumourTold,
                Story.erethor.lute.luteTaken,
                Story.erethor.lute.noteReceived,
                Story.erethor.lute.cloakReceived,
            )
        }
        story(Story.bracelets.thun) {
            states(
                Story.bracelets.thun.none,
                Story.bracelets.thun.mapBought,
                Story.bracelets.thun.braceletsTaken,
            )
        }
        story(Story.nymph.seeker) {
            states(
                Story.nymph.seeker.none,
                Story.nymph.seeker.told,
                Story.nymph.seeker.wizard3tActive,
            )
        }
        story(Story.peasant.wife) {
            states(
                Story.peasant.wife.none,
                Story.peasant.wife.accepted,
                Story.peasant.wife.rescued,
                Story.peasant.wife.paid,
            )
        }
        story(Story.jazzy.ogre) {
            states(
                Story.jazzy.ogre.none,
                Story.jazzy.ogre.accepted,
                Story.jazzy.ogre.ogresCleared,
                Story.jazzy.ogre.jazzyDefeated,
            )
        }
        story(Story.masterCrown) {
            states(
                Story.masterCrown.none,
                Story.masterCrown.task1Assigned,
                Story.masterCrown.task1Done,
                Story.masterCrown.task2Assigned,
                Story.masterCrown.task2Done,
                Story.masterCrown.task3Assigned,
                Story.masterCrown.borinMet,
                Story.masterCrown.daggerTaken,
                Story.masterCrown.borinPaid,
                Story.masterCrown.crownTold,
                Story.masterCrown.crownTaken,
            )
        }
        story(Story.hermit.trollTooth) {
            states(
                Story.hermit.trollTooth.none,
                Story.hermit.trollTooth.accepted,
                Story.hermit.trollTooth.extracted,
                Story.hermit.trollTooth.delivered,
                Story.hermit.trollTooth.fireShieldAcquired,
            )
        }
        encounter(Story.encounter.immune) {
            states(
                Story.encounter.immune.no,
                Story.encounter.immune.yes,
            )
        }
    }

    visuals {
        images {
            ansi(Images.Static.screen, resource = "ansi/screen.ans", placement = ImagePlacement.FullScreen)
            ansi(Images.Static.title, resource = "ansi/title.ans", placement = ImagePlacement.FullScreen)

            ansi(Images.Scene.ydargCastle, resource = "tiles/ydarg-castle.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.tent, resource = "tiles/wilderness-tent.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.battleVictory, resource = "tiles/battle-victory.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.encounterBanner1, resource = "tiles/encounter-banner-1.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.encounterBanner2, resource = "tiles/encounter-banner-2.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.encounterBanner3, resource = "tiles/encounter-banner-3.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.encounterBanner4, resource = "tiles/encounter-banner-4.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.encounterBanner5, resource = "tiles/encounter-banner-5.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.cave, resource = "tiles/cave-entrance.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.insideCave, resource = "tiles/cave-interior.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.church, resource = "tiles/towne-church.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.smith, resource = "tiles/towne-smith.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.inn, resource = "tiles/towne-inn.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.merchant, resource = "tiles/towne-merchant.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.tavern, resource = "tiles/towne-tavern.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.hut, resource = "tiles/wilderness-hut.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.death, resource = "tiles/death.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.towneMainstreet, resource = "tiles/towne-mainstreet.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.wildernessNoTent, resource = "tiles/wilderness-no-tent.ans", placement = ImagePlacement.MapWindow)
            ansi(Images.Scene.towneGuild, resource = "tiles/towne-guild.ans", placement = ImagePlacement.MapWindow)
        }

        staticScreens {
            image(StaticScreens.frame, Images.Static.screen)
            image(StaticScreens.title, Images.Static.title)
            commandHelp(StaticScreens.help)
            resource(StaticScreens.manual, "adventurers-of-rylween.md")
            screen(StaticScreens.score) { scoreReport() }
            screen(StaticScreens.verboseScore) { verboseScoreReport() }
            screen(StaticScreens.heroes) { hallOfHeroesReport() }
        }
    }

    terrain {
        terrain(Terrain.mountain, mapCode = 'm') { blocksMovement() }
        terrain(Terrain.water, mapCode = 'w') { blocksMovement() }
        terrain(Terrain.plain, mapCode = 's')
        terrain(Terrain.town, mapCode = '0') { settlement(TerrainSettlement.Town) }
        terrain(Terrain.hut, mapCode = '1') { settlement(TerrainSettlement.Hut) }
        terrain(Terrain.foothills, mapCode = 'f')
        terrain(Terrain.desert, mapCode = 'd')
        terrain(Terrain.swamp, mapCode = 't')
        terrain(Terrain.forest, mapCode = 'g')
        terrain(Terrain.castle, mapCode = '2') { settlement(TerrainSettlement.Castle) }
    }

    secrets {
        player("Marcus Lagergren") {
            addTimeLeft('/', amount = 50, refreshStatus = true)
            setInventory('(', slot = 12, item = Items.drumsOfDeath)
            setInventory(')', slot = 11, item = Items.gemOfResurrection)
            setInventory('&', slot = 10, item = Items.kaelsAxe)
            setInventory('*', slot = 9, item = Items.holyHandgrenade)
            addGold('+', amount = 1000, refreshStatus = true)
        }
        player("Karl Skagerberg") {
            spawnEncounterEvery(everySteps = 101, monster = Monsters.skagerbergKiller)
        }
    }

    worldMap(Maps.wilderness) {
        size = cells(width = 78, height = 40)
        storageCapacity = 3120

        layer(MapLayers.terrain) {
            rows(
                "mmmmmmmmmmmmmmmmmmmmmmmmmmwmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm",
                "mmmmmmmmmmmmmmmmmmmmmmmmmmwmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm",
                "mmmmmmmmmmmmmmmmmmmmmmmmmmwwmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm",
                "mmmmmmmmmmmmmmmmmmmmmmmmmmmwmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm",
                "mmmmmmmmmmmmmmmmmmmmmmmmmmmwmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm",
                "mmmmmmsmsmmmmmsmssmsmmmmmmwmmmmmsmsfsssmsmmmtmmmtmmmmmmsmmfmmmfmmmmsffsmmmmmmm",
                "mmmmmssmffssmssmssfsmsfssmwmmmssfsfsfsfsfsmmttmtttsmffmsfmsfmfsffsmfsmfsfmmmmm",
                "mmmmmfsssssffssfsfsfsfsfwwwsmssfsfsssfsfsssmttsstssmsssttttsstsssttssttsmmmmmm",
                "mmmmmmfssssssfsssssssssswfssmsfsssssssssfssssttstssssttssssttstttssttffmmmmmmm",
                "mmmmmmfssssssssssssssssswsssssssssssssssssssstttsttttssttsttttssssstttfsmmmmmm",
                "mmmmmfssssss0sssssssssswwwwwwsssssssssssssssssttsttttssssssssstttstffsmmmmmmmm",
                "mmmmmmffssssssssssssssswsssswswwwwwsssssssssttss0tststtssststsssstsffssmmmmmmm",
                "mmmmmmmfsssfssssssssssswsssswwwssswsssssssstttsttssttttsttttstttsssffttmmmmmmm",
                "mmmmmfssssssssssssssssswwssssssssswsssssssttsssssttttsssttssssssssfssftttmmmmm",
                "mmmmmmmmfssffssssssssssswwwwssssswwwssssssssttttsssssssssttstssssmfmmmmmmmmmmm",
                "mmmmmmmfssfsssssssssssssssswssssswswsssssssssssssssssssssstsssssffsmmmmmmmmmmm",
                "mmmmmfssssssssssggsgsssssggwssgsssswssssssssssgsssssssssssssssssssffffsmmmmmmm",
                "mmmmmmmfsssfsssssgggssssssgwgggssggwggsssgggggggsssssssssssssssssssssfssfmmmmm",
                "mmmmmmmfssssssssssggssssssgggggggggggggssssssggsssssssssssssssssssssssfssmmmmm",
                "mmmmmmsfssfssssssssssssssssssssssgggggggsgg0gggggggsssssssssssssssssssfsmmmmmm",
                "mmmmmmfsssfssssssssssssssggggggggggfgfgfmggfggggggsssssssssssssssssssssfsmmmmm",
                "mmmmmsffsssssfsssssssgggggggggggggggggmmmmgmggggggggggssgssssssssssssfmmmmmmmm",
                "mmmmmmmmsssfsssssssggssssggggggggggfffgfmfgfgggggggsssssggggsssssssssfsmmmmmmm",
                "mmmmmmffssssssfsssssssssssssssssggggggggggggggggggggggsssggssssssssssfsfsmmmmm",
                "mmmmmmsfsssfsssssssssssssggggggggggggggggggggggggggggggssssssssssssssssmmmmmmm",
                "mmmmmmmmfsssfsssssssgggssssssssggggggggggggggssssssssssssssssssdssssfsfssmmmmm",
                "mmmmfffsfssssssssssssgsssssssssssssssssssssssssssssssssddsssssddsssdsmmmmmmmmm",
                "mmmmmmmmmmssssssssssssssssssssssswwsssssssssssssssddsddddddddddddddddfdmmmmmmm",
                "mmmmmssfsssssggsgsssssssssssssssswssssssssssssssssddddd0dddddddddddddfdmmmmmmm",
                "mmmmmmfsssssgg2ggssssssssssssssswwsmsssssssssddddddddddddddddddddddddfdmmmmmmm",
                "mmmmmmsfssssssggsssssssfsssssswwwsmmmssssddddddddddddddddddddddddddddfmmmmmmmm",
                "mmmmmmmmffssssssssssssfsfsfssswsssmsssssdddddddddddddddddddddddddddfdddmdmmmmm",
                "mmmmmmsffssffsfsfsfsffmfsfsmfswffsmfffssssfdfddfdddddfdfdddddffddfdfddmmmmmmmm",
                "mmmmmmmsffsmsfssmfsmssmmsssmmfwwsmmsmfsffmmmfmfmffdffdffdfdfffdffdddmmmmmmmmmm",
                "mmmmmmmmmmsmmsmmmsmmmsmmmmsmmmmwsmmsmsfsmmmmmmmmmmdmddmmmmmdmmdmmmmmmmmmmmmmmm",
                "mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmwmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm",
                "mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmwmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm",
                "mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmwmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm",
                "mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmwmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm",
                "mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmwmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm",
            )
        }
    }

    terminal {
        terrain(Terrain.mountain, glyph = "/\\", color = PaletteColors.white)
        terrain(Terrain.water, glyph = "~~", color = PaletteColors.blue)
        terrain(Terrain.plain, glyph = "..", color = PaletteColors.yellow)
        terrain(Terrain.town, glyph = "TT", color = PaletteColors.white)
        terrain(Terrain.hut, glyph = "HH", color = PaletteColors.white)
        terrain(Terrain.foothills, glyph = "^^", color = PaletteColors.white)
        terrain(Terrain.desert, glyph = "::", color = PaletteColors.red)
        terrain(Terrain.swamp, glyph = "%%", color = PaletteColors.magenta)
        terrain(Terrain.forest, glyph = "gg", color = PaletteColors.green)
        terrain(Terrain.castle, glyph = "CC", color = PaletteColors.white)
    }

    layouts {
        region(ScreenRegions.textWindow, x = 49, y = 6, width = 26, height = 11)
        region(ScreenRegions.topLeft, x = 0, y = 0)
        region(ScreenRegions.statuslineTop, x = 3, y = 21)
        region(ScreenRegions.statuslineBottom, x = 13, y = 22)
        region(ScreenRegions.mapWindow, x = 8, y = 6, width = 16, height = 9)
    }

    realm {
        baseResourceCapacity(Resource.Food, 150)
        upkeep {
            foodCostLeaderDiv = 4
            foodTickEvery = 5
            starveFoodThreshold = 0
            starveDamage = 2
            manaRegenChance = 50
            restedHoursCostPerTurn = 1
            fatigueAt(0, inlineText("You begin to feel tired"))
            fatigueAt(-4, inlineText("You are exhausted!!!"), drain = true)
            fatigueAt(-8, inlineText("You begin to feel weak", "from lack of sleep.", ""), drain = true)
            fatigueBelow(-8, inlineText("You faint from lack", "of well needed sleep."), drain = true)
        }
        hunt {
            successChancePercent = 45
            timeCost = 5
            foodMin = 10
            foodMax = 19
            whoPrompt = structuredText {
                line("Who? (1-", HuntText.partyMemberMax, ")")
            }
            dead = inlineText("Dead.")
            notRanger = structuredText {
                line(HuntText.memberSubjectPronoun, " is not a ranger.")
            }
            tooFewMoves = inlineText("Too few moves for that.")
            tries = structuredText {
                line(HuntText.memberName, " tries")
                line("to trap a rabbit...")
            }
            succeeds = structuredText {
                line("...and succeeds.")
                line("The party gains")
                line(HuntText.foodGained, " food rations.")
                line("")
            }
            fails = inlineText("...but fails.", "")
        }
        camp {
            healthGain = 15
            foodCostPerMember = 4
            hoursAdvanced = 8
            hoursRestedReset = 14
            temperatureMax = 15
            ambushChancePercent = 19
            ambushTier = EncounterTiers.weak
            notTired = inlineText("Not tired.")
            notEnoughFood = inlineText("Not enough food.")
            guardPrompt = inlineText("Camp.", "", "Post a guard?", "")
            guardMemberPrompt = inlineText("Who? (1-{party_max})")
            guardMemberDead = inlineText("Dead.")
            standsGuard = inlineText(
                "Guard stands watch.",
                "They won't get any",
                "health back during",
                "this rest.",
                "",
            )
            sleeping = inlineText("Zzz...")
            rested = inlineText("You wake up 8 hours", "later. Well rested.", "")
            ambush = inlineText("Ambush!!!")
            surpriseKill = inlineText(
                "A camper is dead",
                "before you can",
                "react!!!",
                "",
            )
            temperatureBand(
                band = TemperatureBand.Low,
                range = 6 until 10,
                damage = 5,
                message = inlineText("Low Temperature."),
                noShelter = inlineText("No good equipment,", "Party loses 5 health."),
            )
            temperatureBand(
                band = TemperatureBand.VeryLow,
                range = 10..15,
                damage = 5,
                message = inlineText("Very low temperature."),
                noShelter = inlineText("No good equipment,", "Party loses 10 health."),
            )
        }
    }

    castle(Castles.ydarg) {
        entry = xy(14, 30)
        image = Images.Backdrop.ydargCastle
        entryRequirement = Milestones.silverKey
        completionMilestone = Milestones.ydargVictory
        monsterTier = EncounterTiers.deep
        map(size = 5, roomTypeMax = 5)

        treasure {
            gateMax = 4
            regularKind(
                Items.sword, Items.axe, Items.bow, Items.club, Items.dart, Items.sling, Items.longsword,
                Items.trident, Items.halbard, Items.poleArm, Items.crossbow, Items.scythe, Items.scimitar,
                Items.twoHandSword,
            )
            regularKind(
                Items.leather, Items.chainMail, Items.plateMail, Items.ringMail, Items.elvinCloak,
                Items.mithrilMail, Items.admtMail,
            )
            regularKind(
                Items.torch, Items.ration, Items.blanket, Items.tent, Items.rope,
                Items.redPotion, Items.bluePotion, Items.amberPotion,
            )
            magic(
                Items.frostWand, Items.fireWand, Items.gemOfConfusion, Items.gemOfBlindness,
                Items.drumsOfDeath, Items.staffOfHealing, Items.blessingScroll, Items.staffOfCuring,
                Items.gemOfResurrection, Items.kaelsAxe, Items.holyHandgrenade, Items.fluteOfMadness,
            )
        }

        pitfall {
            damageDivisor = 6
            trap {
                lines("Arrows speed through", "the air and hits the", "party!", "")
            }
            trap {
                lines("Poison needles shoot", "out from the floor!", "")
            }
            trap {
                lines("A volley of shurikens", "is hurled from a hatch", "in the wall!", "")
            }
        }

        stairs {
            floorCount = 4
            item(1, Milestones.fireShield, "The Fire Shield", inlineText("You don't have it!"))
            item(2, Milestones.masterCrown, "The Master Crown", inlineText("You don't have it!"))
            item(3, Milestones.footOfJazzy, "The Foot of Jazzy", inlineText("You don't have it!"))
            item(4, Milestones.braceletsOfThun, "The Bracelets of Thun", inlineText("You don't have them!"))
        }

        scripts {
            alreadyCompleted = sceneId("castle.ydarg.already_completed")
            entryBlocked = sceneId("castle.ydarg.entry_blocked")
            entered = sceneId("castle.ydarg.entered")
            completed = sceneId("castle.ydarg.completed")
        }

        text {
            floorNames("(Ground Level)", "(First Floor)", "(Base of Tower)", "(Top of Tower)")
            roomMonster = inlineText("You are spotted by", "monsters!")
            trapTriesDisarmSuffix = inlineText("tries to", "disarm it...", "")
            trapDisarmSucceed = inlineText("AND SUCCEEDS!")
            trapDisarmFail = inlineText("BUT FAILS!")
            treasureMenu = inlineText("(T)ake it", "(L)eave it")
            treasureFull = inlineText("You can't carry more.")
            treasureTaken = inlineText("", "Ok.")
            moveNorth = inlineText("North.")
            moveSouth = inlineText("South.")
            moveEast = inlineText("East.")
            moveWest = inlineText("West.")
            stairsIntro = inlineText("You stand before the", "stairs to the next level.", "")
            stairsGuardianOffer = inlineText(
                "A mage imprisoned in a",
                "fiery sphere offers to",
                "open trapdoor at the top",
                "of the stairs if you",
                "give him an item of power!",
                "He also offers to teleport",
                "you out of the castle,",
                "for free.",
                "",
            )
            stairsMenu = inlineText("Choose:", "", "(T)eleport Out", "(G)ive him an item", "(L)eave")
            stairsLeave = inlineText("You take the nearest exit!")
            teleportVortex = inlineText("The world fades into a", "vortex of agonizing", "vertigo...", "")
            teleportOutside = inlineText("And you are outside...")
            giveMenu = inlineText(
                "Give him what?",
                "",
                "1. The Fire Shield",
                "2. The Master Crown",
                "3. The Foot of Jazzy",
                "4. The Bracelets of Thun",
                "5. Nothing",
            )
            stairsGuardianGrateful = inlineText(
                "The sphere fades away",
                "and the mage steps out.",
                "He is grateful. Before",
                "he teleports away, he",
                "casts an OPEN spell on the",
                "trapdoor above and speaks:",
                "- I will aid thee in the",
                "final battle!",
                "",
            )
        }
    }

    scene(sceneId("castle.ydarg.already_completed")) {
        showBackdrop(Images.Backdrop.ydargCastle)
        screen(
            "The once so mighty",
            "castle Zadraz is now",
            "nothing but a ruin.",
            "You have nothing else",
            "to do here.",
            "",
        )
    }

    scene(sceneId("castle.ydarg.entry_blocked")) {
        showBackdrop(Images.Backdrop.ydargCastle)
        screen(
            "A black and foreboding",
            "castle looms over the",
            "horizon. Its cold, black",
            "stone walls are decorated",
            "with complex symbols and",
            "archaic runes of long",
            "forgotten language.",
            "",
        )
        screen(
            "A shape of white mist",
            "slowly thickens to human",
            "shape. A transparent image",
            "of a beautiful woman",
            "forms in front of you.",
            "",
        )
        screen(
            "- Do not be afraid,",
            "adventurers. I am Jaara,",
            "guardian of the gate.",
            "Baron Ydarg has imprisoned",
            "me here forever. I can't",
            "hurt you, just stop you",
            "from entering if you don't",
            "have the Silver Key!",
            "",
        )
        screen(
            "- You don't have the",
            "Key. The gate will",
            "remain sealed.",
            "",
        )
    }

    scene(sceneId("castle.ydarg.entered")) {
        showBackdrop(Images.Backdrop.ydargCastle)
        screen(
            "A black and foreboding",
            "castle looms over the",
            "horizon. Its cold, black",
            "stone walls are decorated",
            "with complex symbols and",
            "archaic runes of long",
            "forgotten language.",
            "",
        )
        screen(
            "A shape of white mist",
            "slowly thickens to human",
            "shape. A transparent image",
            "of a beautiful woman",
            "forms in front of you.",
            "",
        )
        screen(
            "- Do not be afraid,",
            "adventurers. I am Jaara,",
            "guardian of the gate.",
            "Baron Ydarg has imprisoned",
            "me here forever. I can't",
            "hurt you, just stop you",
            "from entering if you don't",
            "have the Silver Key!",
            "",
        )
        screen(
            "You show the Silver Key to",
            "the ghost. Her eyes widen,",
            "as she sees it. She smiles",
            "and waves her hand towards",
            "the drawbridge. It falls",
            "to the ground with a",
            "resounding KRASH. Jaara",
            "slowly fades away until",
            "nothing remains.",
            "",
        )
        screen("You enter.", "")
        screen("The first door you enter", "slams shut behind you!")
    }

    scene(sceneId("castle.ydarg.completed")) {
        screen(
            "The last trapdoor",
            "slams shut behind you.",
            "You are high above the",
            "forest, on the roof",
            "of this fortress.",
            "Suddenly, a voice speaks:",
            "",
        )
        screen(
            "- Fools! You have gone",
            "long to come this far, but",
            "here ends your journey!",
            "",
            "The night sky above lights",
            "up with green flashes, and",
            "Ydargs minions rush",
            "to attack you!",
            "",
        )
        encounter(Monsters.cryptHorror, mandatory = true)
        encounter(Monsters.nightGaunt, mandatory = true)
        encounter(Monsters.thyraga, mandatory = true)
        encounter(Monsters.liche, mandatory = true)
        screen(
            "Corpses of the nightmare",
            "terrors are strewn all",
            "around. A dark cloaked",
            "figure appears before you.",
            "He takes a broad step over",
            "the dead and dying, and",
            "gazes at you with cold",
            "glowing green eyes.",
            "",
        )
        screen(
            "- So. You have completed",
            "the battle test. I must",
            "confess that you impress",
            "me. But now...",
            "",
            "(He waves a pointed stick",
            "and energy begins to form",
            "in the air before you.)",
            "",
        )
        screen(
            "A glowing energy sphere",
            "appears, and at a gesture",
            "from Ydarg's stick, it",
            "hurtles towards you!!!",
            "",
        )
        screen(
            "Suddenly! You feel someone",
            "else near. The four",
            "grey-clad mages raise",
            "their hands, and the",
            "sphere is absorbed by",
            "their hands!",
            "",
        )
        screen(
            "- CURSES! Ydarg growls.",
            "- So this is what has come",
            "to! Well, you got here by",
            "aid of cold steel, and",
            "that is the way you shall",
            "perish!",
            "",
        )
        screen(
            "He pulls a huge trident",
            "from his robe and utters",
            "a single syllable. Without",
            "warning he appears a foot",
            "before you!!!",
            "",
        )
        screen("Let the battle commence!", "")
        encounter(Monsters.baronYdarg, mandatory = true)
        screen(
            "Ydarg's trident clatters",
            "to the ground before you.",
            "He shudders and falls over.",
            "As you rush to kill him",
            "he screams:",
            "",
        )
        screen(
            "- It is not over! I shall",
            "see you in hell! In",
            "another world! A time",
            "to come! You have tainted",
            "Zadraz with your ancient",
            "items, but I will triumph",
            "again!!! Anew...",
            "",
        )
        screen("His cloak collapses, and", "when you tear it away,", "you reveal nothing but...", "")
        screen("dust...", "")
        screen(
            "CONGRATULATIONS!",
            "",
            "You have banished Ydarg",
            "from this dimension.",
            "Please ask your SysOp to",
            "reset the game.",
            "",
        )
        achieve(Milestones.ydargVictory)
        screen("Be ready for:", "HEROES OF RYLWEEN", "- The second coming.", "Soon online at a BBS", "near you!", "")
        screen("See Ya!!!", "", "     /Marcus", "")
    }

    prompts {
        pauseVerbs(
            "Smash", "Bash", "Slash", "Lunge at", "Crush",
            "Destroy", "Mutilate", "Thrash", "Stab", "Brain",
            "Pummel", "Whack", "Pulverize", "Annihilate", "Obliterate",
            "Demolish", "Hammer", "Pound", "Maul", "Wallop",
            "Cleave", "Mangle", "Skewer", "Eviscerate", "Vanquish",
            "Trample", "Batter", "Slam", "Smite", "Decapitate",
        )
        priest {
            who = structuredText { line("Who? (1-", HuntText.partyMemberMax, ")") }
            mageCombatOnly = inlineText("Sorry. Mage spells are", "combat-only.")
            wrongClass = inlineText("Wrong class.")
            dead = inlineText("Dead.")
            invalidCaster = inlineText("Not a party member.")
            invalidSpell = inlineText("Not possible.")
            noSpells = inlineText("No priest spells are", "declared.")
        }
        inventoryUse {
            use = inlineText("Use what?")
            quaff = inlineText("Quaff what?")
            noItems = inlineText("You are not carrying", "anything.")
            noItem = inlineText("There is nothing", "in that slot.")
            invalidSlotFirstPrefix = "Inventory slot "
            invalidSlotFirstSuffix = ""
            invalidSlotSecond = "does not exist."
            notUsableFirst = "Nothing happens"
            notUsableSecondPrefix = "with "
            notUsableSecondSuffix = "."
        }
        equipment {
            inventory = inlineText("Ready what?")
            who = inlineText("Who? (1-N)")
            readyWeapons = inlineText("Do you want to ready", "some weapons before", "leaving?", "")
            readyArmors = inlineText("Do you want to ready", "some armors before", "leaving?", "")
        }
        save {
            saving = inlineText("Saving position...")
            done = inlineText("Done.")
            failed = inlineText("Save failed.")
        }
        characterCreation {
            header = inlineText("Party Creation", "")
            partyName = inlineText("What is the name of your", "party?", "")
            statsPage {
                line("Stats for char: ", memberNumber)
                line("")
                line("Weapon Skill : ", weaponSkill)
                line("Health       : ", health)
                line("Wisdom       : ", wisdom)
                line("Charisma     : ", charisma)
                line("")
            }
            reroll = inlineText("Reroll (y/N) ")
            memberName {
                line("Give Character ", memberNumber, " a name:")
                line("")
            }
            gender {
                line("What gender is")
                line(memberName, "?")
                line("")
            }
            genderMaleLabel = "Male"
            genderFemaleLabel = "Female"
            race {
                line("What race is")
                line(memberName, "?")
                line("")
            }
            classMenu = inlineText(
                "Pick a class for this",
                "character...",
                "",
            )
            saving = inlineText("Saving...")
        }
        town {
            unavailableSuffix = " is not available here."
            closedSuffix = " is closed."
            entryWelcome = structuredText {
                line("Welcome to ", TownText.name)
            }
            entryTax = structuredText {
                line("Our current tax rate")
                line("is ", TownText.taxPercent, "% - In order to")
                line("enter, we must request")
                line("you pay ", TownText.taxAmount, " au.")
                line("")
            }
            entryTaxThanks = inlineText("Thank you!")
        }
        party {
            viewWho = structuredText { line("Who? (1-", HuntText.partyMemberMax, "/Q/A)") }
            journalHeader = inlineText(
                "",
                "TRAVELLER'S JOURNAL",
                "===================",
                "",
            )
            dropPrompt = inlineText("Drop what?")
            dropEmptySlot = inlineText("Nothing to drop.")
            dropAborted = inlineText("Aborted.")
            dropped {
                line("You drop the")
                line(itemName, ".")
            }
            dropCapacityReduced = inlineText("You are forced to", "leave some of your", "food behind...")
        }
        wilderness {
            pass = inlineText("Time passes...")
            noMoves = inlineText("No moves left.")
            outOfMoves = inlineText("You are out of moves", "for today.", "", "(S)ave and Quit", "(Q)uit (no save)")
            notInWilderness = inlineText("The active party is not in the wilderness.")
            nothingToEnter = inlineText("There is nothing to enter here.")
            quitConfirm = inlineText("Do you really want", "to quit?", "")
            dungeonHandoff {
                line("Entering ", dungeonName, ".")
                line("Levels: ", levels)
            }
        }
        dungeon {
            levelMenu {
                line("You are on level ", level, ".")
                line("")
                line("(D) Descend")
                line("(A) Ascend")
                line("")
            }
            enterLevel {
                line("You enter level ", level, ".")
            }
            atMaxDepth = inlineText("The cave doesn't", "go deeper.")
            noTorches = inlineText("You have used your last", "torch. You can't go", "any further.", "")
            leaveCavern = inlineText("You leave the", "hideous cavern!", "")
        }
    }

    items {
        // Equipment is unrestricted unless a weapon or armor declares allowed classes.
        // Example: weapon(Items.holyBlade, "Holy Blade", cost = 5000, damage = 40) { equippableBy(PcClass.Priest) }
        empty(Items.none)

        weapon(Items.sword, "Sword", cost = 160, damage = 6)
        weapon(Items.axe, "Axe", cost = 240, damage = 10)
        weapon(Items.bow, "Bow", cost = 160, damage = 7)
        weapon(Items.club, "Club", cost = 140, damage = 4)
        weapon(Items.dart, "Dart", cost = 40, damage = 3)
        weapon(Items.sling, "Sling", cost = 165, damage = 5)
        weapon(Items.longsword, "Longsword", cost = 280, damage = 9)
        weapon(Items.trident, "Trident", cost = 350, damage = 11)
        weapon(Items.halbard, "Halbard", cost = 500, damage = 14)
        weapon(Items.poleArm, "Pole arm", cost = 670, damage = 18)
        weapon(Items.crossbow, "Crossbow", cost = 900, damage = 21)
        weapon(Items.scythe, "Scythe", cost = 870, damage = 14)
        weapon(Items.scimitar, "Scimitar", cost = 600, damage = 8)
        weapon(Items.twoHandSword, "2H-Sword", cost = 1200, damage = 19)
        weapon(Items.magicDagger, "Magic Dagger", cost = 1800, damage = 24)
        weapon(Items.magicSword, "Magic Sword", cost = 3000, damage = 30)
        weapon(Items.magicAxe, "Magic Axe", cost = 4000, damage = 38)
        weapon(Items.dancingSword, "Dancing Sword", cost = 6000, damage = 50)
        weapon(Items.deathBlade, "Death Blade", cost = 10000, damage = 68)

        armor(Items.leather, "Leather", cost = 260, armorClass = 2)
        armor(Items.chainMail, "Chain Mail", cost = 600, armorClass = 5)
        armor(Items.plateMail, "Plate Mail", cost = 980, armorClass = 7)
        armor(Items.ringMail, "Ring Mail", cost = 1950, armorClass = 9)
        armor(Items.elvinCloak, "Elvin Cloak", cost = 3600, armorClass = 12)
        armor(Items.mithrilMail, "Mithril Mail", cost = 5800, armorClass = 14)
        armor(Items.admtMail, "Admt. Mail", cost = 9400, armorClass = 16)
        armor(Items.protSphere, "Prot. Sphere", cost = 15000, armorClass = 20)
        armor(Items.cloakOfGods, "Cloak of Gods", cost = 28000, armorClass = 30)

        gear(Items.torch, "Torch", cost = 1)
        gear(Items.ration, "Ration", cost = 2)
        gear(Items.blanket, "Blanket", cost = 30) {
            campProtection { protects(TemperatureBand.Low) }
        }
        gear(Items.tent, "Tent", cost = 80) {
            campImage(Images.Scene.tent)
            campProtection { protects(TemperatureBand.Low, TemperatureBand.VeryLow) }
        }
        gear(Items.rope, "Rope", cost = 15)

        potion(Items.redPotion, "Red Potion", cost = 30)
        potion(Items.bluePotion, "Blue Potion", cost = 100)
        potion(Items.amberPotion, "Amber Potion", cost = 500)

        treasure(Items.redGem, "Red Gem", cost = 1000)
        treasure(Items.blueGem, "Blue Gem", cost = 1200)
        treasure(Items.greenGem, "Green Gem", cost = 1900)
        treasure(Items.chalice, "Chalice", cost = 2340)
        treasure(Items.pendant, "Pendant", cost = 5000)
        treasure(Items.broche, "Broche", cost = 1234)
        treasure(Items.crystalBowl, "Crystal Bowl", cost = 4567)
        treasure(Items.necklace, "Necklace", cost = 987)
        treasure(Items.donkey, "Donkey", cost = 800) {
            resourceCapacityBonus(Resource.Food, 150)
        }

        special(Items.fireshield, "Fire Shield")
        special(Items.bracelets, "Bracelets of Thun")
        special(Items.mastercrown, "Master Crown")
        special(Items.jazzyfoot, "Jazzyfoot")
        special(Items.silverkey, "Silver Key")

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

        pool(ItemPools.battleSpoils) {
            items(
                Items.redPotion,
                Items.bluePotion,
                Items.amberPotion,
                Items.redGem,
                Items.blueGem,
                Items.greenGem,
                Items.chalice,
                Items.pendant,
                Items.broche,
                Items.crystalBowl,
                Items.necklace,
            )
        }

        pool(ItemPools.magicItems) {
            items(
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

        pool(ItemPools.thunAltarGems) {
            items(Items.redGem, Items.blueGem, Items.greenGem)
        }

        pool(ItemPools.dungeonWeaponFinds) {
            items(
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
            )
        }

        pool(ItemPools.dungeonPotionGemFinds) {
            items(
                Items.redPotion,
                Items.bluePotion,
                Items.amberPotion,
                Items.redGem,
                Items.blueGem,
                Items.greenGem,
                Items.chalice,
            )
        }

        pool(ItemPools.dungeonTreasureFinds) {
            items(
                Items.redGem,
                Items.blueGem,
                Items.greenGem,
                Items.chalice,
                Items.pendant,
                Items.broche,
                Items.crystalBowl,
                Items.necklace,
            )
        }
    }

    gameProgress {
        milestone(Milestones.fireShield) {
            showsAs(Items.fireshield)
            requiredToWin()
            sourcePartyField(SourceSpecialField.Fireshield)
        }

        milestone(Milestones.braceletsOfThun) {
            showsAs(Items.bracelets)
            requiredToWin()
            sourcePartyField(SourceSpecialField.Bracelets)
        }

        milestone(Milestones.masterCrown) {
            showsAs(Items.mastercrown)
            requiredToWin()
            sourcePartyField(SourceSpecialField.Mastercrown)
        }

        milestone(Milestones.footOfJazzy) {
            showsAs(Items.jazzyfoot)
            requiredToWin()
            sourcePartyField(SourceSpecialField.Jazzyfoot)
        }

        milestone(Milestones.silverKey) {
            showsAs(Items.silverkey)
            requiredToWin()
            sourcePartyField(SourceSpecialField.Silverkey)
        }

        milestone(Milestones.ydargVictory) {
            sourcePartyField(SourceSpecialField.Silverkey, achievedValue = 2)
        }

        winningConfiguration {
            requiresAllMilestonesMarkedRequired()
            completedBy(Milestones.ydargVictory)
            hallOfHeroes(
                empty = "No party has defeated Baron Ydarg yet.",
                entry = "{party} defeated Baron Ydarg",
            )
        }
    }

    leveling {
        maxLevel = 28
        hitPointFloor = Formulas.levelUpHitPointFloor
        xpThresholds(
            0, 0,
            2000, 4000, 8000, 12000, 20000, 28000, 36000, 48000, 54000,
            64000, 74000, 86000, 98000, 110000, 125000, 150000, 180000,
            240000, 300000, 400000, 550000, 700000, 900000, 1100000,
            1500000, 2000000, 2800000,
        )
        classLevelUp(PcClass.Fighter) {
            weaponSkill = 3
            health = 8
            wisdom = 1
            charisma = 1
        }
        classLevelUp(PcClass.Thief) {
            weaponSkill = 1
            health = 5
            wisdom = 1
            charisma = 2
        }
        classLevelUp(PcClass.Mage) {
            weaponSkill = 1
            health = 4
            wisdom = 7
            charisma = 2
        }
        classLevelUp(PcClass.Priest) {
            weaponSkill = 1
            health = 5
            wisdom = 8
            charisma = 3
        }
        classLevelUp(PcClass.Bard) {
            weaponSkill = 2
            health = 7
            wisdom = 2
            charisma = 6
        }
        classLevelUp(PcClass.Ranger) {
            weaponSkill = 2
            health = 8
            wisdom = 3
            charisma = 3
        }
    }

    characterCreation {
        statRoll(min = 15, max = 70)
        start {
            map = Maps.wilderness
            position = Wilderness.xy(12, 9)
            facing = Facing.North
            partyMemberMax = 4
            level = 1
            dailyTimeLeft = 175
            resources {
                gold = 1000
                torches = 5
                food = 100
                timeLeft = 200
                restedHours = 14
            }
        }

        race(Race.Human, "Human")
        race(Race.Dwarf, "Dwarf")
        race(Race.Elf, "Elf")
        race(Race.Halfling, "Halfling")

        playerClass(PcClass.Fighter, "Fighter") {
            requires(
                CharacterCreationStat.WeaponSkill,
                minimum = 50,
                failureText = inlineText("Weap. skill must be > 50."),
            )
        }
        playerClass(PcClass.Thief, "Thief")
        playerClass(PcClass.Mage, "Mage") {
            requires(CharacterCreationStat.Wisdom, minimum = 50, failureText = inlineText("Wisdom must be > 50."))
        }
        playerClass(PcClass.Priest, "Priest") {
            requires(CharacterCreationStat.Wisdom, minimum = 50, failureText = inlineText("Wisdom must be > 50."))
        }
        playerClass(PcClass.Bard, "Bard") {
            requires(CharacterCreationStat.Charisma, minimum = 50, failureText = inlineText("Charisma must be > 50."))
        }
        playerClass(PcClass.Ranger, "Ranger")
    }

    formulas {
        formula(Formulas.encounterH1Scaled) {
            value = encounterHitPointMinBase + partyAverage * 2 + whenValue(firstLevel gt 2, firstLevel * 2)
        }
        formula(Formulas.encounterH2Scaled) {
            value = encounterHitPointMaxBase + firstLevel * 3
        }
        formula(Formulas.rewardXpPerSurvivor) {
            value = encounterFoeCount *
                ((encounterHitPointMax / 4) * (monsterWeaponSkill / 4)) +
                monsterSpellPower * 3
        }
        formula(Formulas.rewardGoldParty) {
            value = encounterFoeCount *
                ((encounterHitPointMax / 7) * (monsterWeaponSkill / 4))
        }
        formula(Formulas.levelUpHitPointFloor) {
            value = (health * 2) / 3
        }
    }

    rewards {
        reward(Rewards.standardBattle) {
            experience = Formulas.rewardXpPerSurvivor
            gold = Formulas.rewardGoldParty

            itemPlacement {
                regularItems = RewardItemPlacement.FirstFreeInventorySlot
                overflow = RewardOverflowPolicy.SkipIfInventoryFull
                specialItems = RewardSpecialItemPolicy.ProjectMilestoneState
            }

            lootTable(LootTables.standardBattle) {
                dropChancePercent = 5
                treasureChancePercent = 59
                treasure = ItemPools.battleSpoils
                magic = ItemPools.magicItems
            }
        }
    }

    combat {
        standardBattle {
            roundLimit = 200
            encounterFoeCountMax = 8
            victoryImage(Images.Backdrop.battleVictory)

            opening {
                fleeChancePercent = 95
                desperateFleeChancePercent = 31
                mandatoryRun = MandatoryRunRule.NoEscape
                encounterBanners(
                    Images.Backdrop.encounterBanner1,
                    Images.Backdrop.encounterBanner2,
                    Images.Backdrop.encounterBanner3,
                    Images.Backdrop.encounterBanner4,
                    Images.Backdrop.encounterBanner5,
                )
            }

            text {
                encounterBanner { lines("ENCOUNTER!", "") }
                youFace { line("You face") }
                foeDisplayName { line(BattleText.foeDisplayName); line("") }
                stillFace { line("You still face") }
                fight { line("(F)ight") }
                run { line("(R)un") }
                bloodFlow { line("Blood shall flow!") }
                whatNow { lines("What now?", "") }
                targetMonsterPrompt()
                useWhat { lines("Use what?", "") }
                spellAbort { line("7. Abort") }
                runCoward { lines("Coward. You won't get", "any EXP for this battle.") }
                runDesperateFail { lines("You desperately try to", "get away, but fail!") }
                runFailAway { line("You fail to get away!") }
                runEscaped { line("You escaped...") }
                notPossible { line("Not possible.") }
                magicImmune { lines("The magic item doesn't seem", "to work on this enemy!!!") }
                spellFizzle { lines("The spell fizzles!", "this creature is immune", "to ordinary magic!!!") }
                notEnoughMagic { line("Not enough magic!") }
                restGainHits { lines("You gain some hits pts", "as you rest this round.") }
                defendStance { lines("You put up a defensive", "stance...") }
                battleCry {
                    lines(
                        "HI-YAAAA!",
                        "",
                        "(You manage to scare your",
                        " enemies enough to get",
                        " them off guard.)",
                    )
                }
                playerHit {
                    line(BattleText.attackerName, " hits for")
                    line(BattleText.damage, " damage pts!")
                }
                playerMiss { line(BattleText.attackerName, " misses!") }
                monsterHit {
                    line(BattleText.attackerName, " attacks")
                    line(BattleText.targetName, "!")
                    line("And hits for")
                    line(BattleText.damage, " damage pts!!!")
                }
                monsterMiss {
                    line(BattleText.attackerName, " attacks")
                    line(BattleText.targetName, "!")
                    line("Blow misses!")
                }
                memberDeath { line(BattleText.targetName, " DIES!!!") }
                monsterKilledLine()
                partyHeld { line("PARTY IS HELD!") }
                partyDefeated { line("The party has fallen.") }
                victoryBested { lines("Thou hast bested", "thy foes.", "") }
                victoryEachSurvivor { line("Each survivor gets") }
                victoryReward {
                    line(BattleText.experience, " experience pts")
                    line("and ", BattleText.gold, " gold pieces.")
                    line("")
                }
                victoryFoundObject { line("You find a") }
                victoryLoot { line(BattleText.lootName, ".") }
                levelGain {
                    line(BattleText.levelMemberName, " gains >", BattleText.levelsGained, "<")
                    line("experience levels.")
                }
            }

            playerActions {
                attack { line("(A)ttack") }
                defend { line("(D)efend") }
                useMagicItem { line("(U)se Item") }
                battleCry { line("(B)attle Cry") }
                castMageSpell { line("(C)ast Spell") }
            }

            playerRound {
                attack(hitBonus = 1, damageSpreadMax = 4, confusionDivisor = 3, hpDefenseDivisor = 12)
                defend(weaponSkillPenalty = 2, restThreshold = 5, cap = 250)
                battleCry(weaponSkillPenalty = 4)
            }

            monsterRound {
                spellChance(basePercent = 15, spellPowerDivisor = 2)
                melee(
                    hitBonus = 1,
                    damageDivisor = 6,
                    damageSpreadMax = 9,
                    damageMin = 1,
                    fallback = Scenes.Battle.monsterMelee,
                )
            }

            rewards = Rewards.standardBattle
        }
    }

    wilderness {
        keys {
            numberPadMovementWithArrows()
            pass = inputKey(" ", "SPACE", label = "Space")
            save = inputKey("S")
            readyWeapon = inputKey("W")
            readyArmor = inputKey("R")
            redraw = inputKey("!")
            journal = inputKey("J")
            help = inputKey("?")
            hunt = inputKey("H")
            viewParty = inputKey("V")
            removeArmor = inputKey("T")
            removeWeapon = inputKey("P")
            enter = inputKey("E")
            drop = inputKey("L")
            camp = inputKey("C")
            castSpell = inputKey("Z")
            quaff = inputKey("Q")
            quit = inputKey("X")
            cave(enter = inputKey("E"), leave = inputKey("L"))
            outOfMoves(save = inputKey("S"), quit = inputKey("Q"))
            quitConfirm(yes = inputKey("Y"), no = inputKey("N"))
            commandHint("Press ? for commands.")
        }

        events {
            caveChancePercent = 3
            quietChancePercent = 91
            encounterSplitPercent = 50
            easyEncounterTier = EncounterTiers.weak
            hardEncounterTier = EncounterTiers.wild
            caveDungeon = Dungeons.randomCave
            caveDepthRange = 3..16
            caveEntryCost = 10
            caveImage = Images.Backdrop.cave

            caveFoundPrompt {
                lines("You find a cave!", "", "(E)nter", "(L)eave")
            }

            caveNoMoves {
                lines(
                    "You don't have enough",
                    "moves left to explore",
                    "the cave...",
                    "",
                )
            }
        }

        terrainTransitions {
            chancePercent = 9

            terrain(Terrain.plain) {
                lines(
                    "The rich green plainland",
                    "of Rylween stretches far",
                    "in all the directions.",
                    "",
                )
            }

            terrain(Terrain.hut) {
                lines("You have found a hut.", "")
            }

            terrain(Terrain.town) {
                lines("You are close to a towne.", "")
            }

            terrain(Terrain.foothills) {
                lines(
                    "Small foothills make your",
                    "progress more arduous.",
                    "",
                )
            }

            terrain(Terrain.desert) {
                lines(
                    "You are in a hot and dry",
                    "desert. The sun fries",
                    "you mercilessly.",
                    "",
                )
            }

            terrain(Terrain.swamp) {
                lines(
                    "You are in the swampy",
                    "marshlands of Rylween.",
                    "Bubbles of gas rise to",
                    "the surface where they",
                    "burst and emit a foul",
                    "smelling odour.",
                    "",
                )
            }

            terrain(Terrain.forest) {
                lines(
                    "The ancient forest is",
                    "very tranquil. A soft",
                    "breeze rustles the",
                    "leaves of mighty oaks.",
                    "",
                )
            }

            terrain(Terrain.castle) {
                lines(
                    "You stand outside the",
                    "castle Zadraz. The",
                    "black steel building",
                    "seems to want to swallow",
                    "you whole.",
                    "",
                )
            }
        }
    }

    dungeons {
        keys {
            descend = inputKey("D")
            ascend = inputKey("A")
            help = inputKey("?")
            drop = inputKey("L")
            removeArmor = inputKey("T")
            removeWeapon = inputKey("P")
            readyWeapon = inputKey("W")
            readyArmor = inputKey("R")
            quaff = inputKey("Q")
            castSpell = inputKey("Z")
            viewParty = inputKey("V")
            save = inputKey("S")
            commandHint("Press ? for commands.")
        }

        backdrop(Images.Backdrop.insideCave)

        event(DungeonEvents.nothingSpecial) {
            run {
                readScreen("You find nothing", "special on this level.", "")
            }
        }

        event(DungeonEvents.emptyLevel) {
            run {
                readScreen("This level is empty.", "")
            }
        }

        event(DungeonEvents.spectrumMessage) {
            recurring()
            run {
                readScreen(
                    "A message on the wall",
                    "reads:",
                    "",
                    "'M U R T C E P S'",
                    "THiNgs aren't aLways iN",
                    "theIR naTuRAl oRDeR...",
                    "",
                )
            }
        }

        event(DungeonEvents.gold) {
            recurring()
            run {
                val amount = localInt(random(40..339))
                screen {
                    line("You find ", amount)
                    lines("gold pieces.", "")
                }
                party.gold += amount
                waitInput("after dungeon gold")
                clearText()
            }
        }

        event(DungeonEvents.torches) {
            recurring()
            run {
                val amount = localInt(random(2..4))
                screen {
                    line("You find ", amount, " torches.")
                    line("")
                }
                party.torches += amount
                waitInput("after dungeon torches")
                clearText()
            }
        }

        event(DungeonEvents.findObject) {
            recurring()
            run {
                findDungeonObject()
            }
        }

        event(DungeonEvents.goldChest) {
            recurring()
            run {
                menu(
                    "You find a chest!",
                    "",
                    "(O)pen chest",
                    "(L)eave",
                ) {
                    option('O') {
                        val trapChance = localInt(4)
                        val thiefFound = localInt(0)
                        forEachMember {
                            whenever((thiefFound eq 0) and it.isClass(PcClass.Thief)) {
                                clearText()
                                screen {
                                    line(partyName(it), " checks the")
                                    line("chest for traps.")
                                }
                                pause(1000)
                                trapChance set 2
                                thiefFound set 1
                            }
                        }

                        whenever(random(0..9) lt trapChance) {
                            clearText()
                            screen("A TRAP!")
                            pause(600)
                            choose(random(0..2)) {
                                case(0) {
                                    screen("Poisonous gas", "hisses out through", "hidden holes.", "")
                                    pause(1400)
                                    screen("The party coughs", "and retches.")
                                    pause(600)
                                    screen("Finally. The gas clears.", "")
                                }
                                case(1) {
                                    screen("A spiked pitfall", "opens in the floor", "beneath you.", "")
                                    pause(1400)
                                    screen("You climb out of it,", "badly wounded.", "")
                                }
                                case(2) {
                                    screen("A large boulder", "drops from above!!!")
                                    pause(1400)
                                    screen("Badly bruised, you", "try to stand up again.", "")
                                }
                            }
                            forEachMember {
                                it.hp -= it.level * 4
                                whenever(it.hp lt 1) {
                                    it.hp set 1
                                }
                            }
                            waitInput("after chest trap")
                        }

                        clearText()
                        val goldAmount = localInt(random(0..99) + 320 + (party.leader.experience / 500))
                        screen {
                            line("The chest contained")
                            line(goldAmount, " pieces of gold.")
                            line("")
                        }
                        party.gold += goldAmount
                        waitInput("after chest gold")
                        clearText()
                    }
                    option('L') {}
                    default {}
                }
            }
        }

        event(DungeonEvents.weakEncounter) {
            recurring()
            randomEncounter(EncounterTiers.weak)
        }

        event(DungeonEvents.deepEncounter) {
            recurring()
            randomEncounter(EncounterTiers.deep)
        }

        event(DungeonEvents.thunRiddle1) {
            run {
                thunRiddle(
                    intro = textLines(
                        "You stand before a",
                        "mage dressed entirenly",
                        "in red.",
                        "",
                        "- Answer this, fools, or",
                        "  die where you stand!",
                        "",
                    ),
                    riddle = textLines(
                        "'Between fire and ice,",
                        "who ends the fight?",
                        "Who stands between those",
                        "two? Not red, not white",
                        "but blue!",
                        "",
                    ),
                    answers = listOf("water"),
                    guardian = Monsters.norker,
                )
            }
        }

        event(DungeonEvents.thunRiddle2) {
            run {
                thunRiddle(
                    intro = textLines(
                        "You stand before a",
                        "mage dressed entirenly",
                        "in green.",
                        "",
                        "- Answer this, fools, or",
                        "  die where you stand!",
                        "",
                    ),
                    riddle = textLines(
                        "'Siblings we are,",
                        "I'm black and she's",
                        "white. Each dreary",
                        "evening, I swallow her",
                        "light.'",
                        "",
                    ),
                    answers = listOf("night", "darkness"),
                    guardian = Monsters.voodooLord,
                )
            }
        }

        event(DungeonEvents.thunRiddle3) {
            run {
                thunRiddle(
                    intro = textLines(
                        "You stand before a",
                        "mage dressed entirenly",
                        "in blue.",
                        "",
                        "- Answer this, fools, or",
                        "  die where you stand!",
                        "",
                    ),
                    riddle = textLines(
                        "'Tall as a house,",
                        "round as a cup, and",
                        "all the king's horses",
                        "can't draw me up! What",
                        "am I?'",
                        "",
                    ),
                    answers = listOf("well", "a well"),
                    guardian = Monsters.archdevil,
                )
            }
        }

        event(DungeonEvents.colourRiddle) {
            recurring()
            run {
                val antechamber = localInt(context.dungeonLevel - 12)
                readScreen {
                    line("A plaque here reads:")
                    line("ANTECHAMBER #", antechamber)
                    line("")
                }
                readScreen(
                    "Before you are seven",
                    "squares of 20x20 feet.",
                    "To get across this room",
                    "you will need to step",
                    "on one of them.",
                    "",
                )
                val pick = ask.digit(
                    textLines(
                        "1. Red Square",
                        "2. Orange Square",
                        "3. Yellow Square",
                        "4. Green Square",
                        "5. Blue Square",
                        "6. Indigo Square",
                        "7. Violet Square",
                    ),
                    1..7,
                )
                whenever(context.dungeonLevel + pick eq 20) {
                    readScreen("You feel a slight", "tingle in your bodies,", "but otherwise, nothing", "happens.", "")
                } otherwise {
                    readScreen(
                        "With a horrible scream",
                        "a shape of pure colour",
                        "forms in thin air before",
                        "you!",
                        "",
                    )
                    encounter(Monsters.rainbowDemon, mandatory = true)
                }
            }
        }

        event(DungeonEvents.fireShieldGuardian) {
            run {
                readScreen(
                    "As you enter this level",
                    "you hear a horrible",
                    "evil laughter.",
                    "Suddenly!",
                    "Out of the shadows,",
                    "something HIDEOUS",
                    "emerges.",
                    "",
                )
                encounter(Monsters.guardian, mandatory = true)
            }
        }

        event(DungeonEvents.fireShield) {
            run {
                whenever(!achieved(Milestones.fireShield)) {
                    readScreen(
                        "After you have bested",
                        "the foul thing, the",
                        "dungeon is strangely",
                        "quiet. Upon entering",
                        "this level, you find the",
                        "marvelous FIRE SHIELD on",
                        "a pedestal! You take it.",
                        "",
                    )
                    achieve(Milestones.fireShield)
                    mark(Story.hermit.trollTooth.fireShieldAcquired)
                }
            }
        }

        event(DungeonEvents.bracelets) {
            run {
                whenever(!achieved(Milestones.braceletsOfThun)) {
                    readScreen(
                        "This whole chamber",
                        "shines of a bright",
                        "white light! On a",
                        "table before you,",
                        "lie the fabled",
                        "Bracelets of Thun!",
                        "The glow fades as",
                        "you put them in your",
                        "backpack.",
                        "",
                    )
                    achieve(Milestones.braceletsOfThun)
                    mark(Story.bracelets.thun.braceletsTaken)
                }
            }
        }

        event(DungeonEvents.nolrimTomb) {
            run {
                whenever(!Story.masterCrown.daggerTaken) {
                    readScreen(
                        "Nolrim's body rests on",
                        "a stone slab in this room.",
                        "On his chest lies the",
                        "dagger you seek. You put",
                        "it in your backpack.",
                        "",
                    )
                    mark(Story.masterCrown.daggerTaken)
                }
            }
        }

        event(DungeonEvents.masterCrown) {
            run {
                whenever(!achieved(Milestones.masterCrown)) {
                    readScreen(
                        "FINALLY! In the hands of",
                        "a long dead warrior, who",
                        "is placed like a statue",
                        "by the east wall of this",
                        "chamber, is the Master",
                        "Crown. The most powerful",
                        "object in the realm.",
                        "It shines with an inner",
                        "light.",
                        "",
                    )
                    readScreen(
                        "...but as you take it,",
                        "THE WARRIOR COMES",
                        "TO LIFE!!!",
                        "",
                    )
                    encounter(Monsters.longDeadWarrior, mandatory = true)
                    achieve(Milestones.masterCrown)
                    mark(Story.masterCrown.crownTaken)
                }
            }
        }

        event(DungeonEvents.silverSanctum) {
            run {
                whenever(!Story.silverKey.noteFound) {
                    readScreen(
                        "This room shines like",
                        "silver. You can see your",
                        "mirror image in the",
                        "walls. After searching",
                        "the room thorougly, you",
                        "find nothing. No Silver",
                        "Key, no treasure.",
                        "",
                    )
                    readScreen(
                        "You curse the rouge who",
                        "tricked you and are just",
                        "about to leave...",
                        "",
                    )
                    readScreen(
                        "...when you spot a paper",
                        "of some kind in a crack",
                        "in the wall... You pull",
                        "it out and read...",
                        "",
                    )
                    readScreen {
                        line("'Guild Master: This is")
                        line("the proof that I, ", character(Roles.masterThief).name)
                        line("am worthy of the title")
                        line("Master Thief.'")
                        line("      - ", character(Roles.masterThief).name)
                        line("")
                    }
                    mark(Story.silverKey.noteFound)
                }
            }
        }

        event(DungeonEvents.hobgoblinPatrol) {
            recurring()
            run {
                readScreen(
                    "You bump into a hobgoblin",
                    "patrol. With shiny and",
                    "well used scimitars, they",
                    "attack you!",
                    "",
                )
                encounter(Monsters.hobgoblin, mandatory = true)
                whenever(chance(39)) {
                    readScreen(
                        "You find a scribbled",
                        "note on the leader:",
                        "Password: 'RISH EKKIN'",
                        "",
                    )
                }
            }
        }

        event(DungeonEvents.royalGuard1) {
            run {
                readScreen(
                    "As you enter this room",
                    "an army of hobgoblin",
                    "skeletons attack you!",
                    "",
                )
                encounter(Monsters.hobgoblinSkeleton, mandatory = true)
            }
        }

        event(DungeonEvents.royalGuard2) {
            run {
                readScreen(
                    "As you enter this room",
                    "an army of undead",
                    "hobgoblins, smelling",
                    "of rotting flesh and",
                    "brandising gleaming",
                    "tridents attack you.",
                    "",
                )
                encounter(Monsters.undeadHobgoblin, mandatory = true)
            }
        }

        event(DungeonEvents.hobgoblinChief) {
            run {
                whenever(!achieved(Milestones.silverKey)) {
                    readScreen(
                        "You stand before a",
                        "silk curtain. You hear",
                        "someone moving behind",
                        "it. A voice calls:",
                        "- What's the password?",
                        "",
                    )
                    choice.textAnswer(
                        prompt = "Password: ",
                        accepting = listOf("RISH EKKIN"),
                        maxLength = 15,
                        match = {
                            readScreen(
                                "- Step inside and identify",
                                "yourself. You do so. The",
                                "hobgoblin chief eyes you",
                                "suspiciously for a few",
                                "seconds. Not hestitating",
                                "a moment you attack.",
                                "",
                            )
                            encounter(Monsters.hobgoblinChief, mandatory = true)
                        },
                        miss = {
                            screen("- GUARDS! GUARDS!", "", "(Hobgoblin guards rush", " to confront you.)")
                            pause(2000)
                            encounter(Monsters.hobgoblin, mandatory = true)
                            encounter(Monsters.hobgoblinChief, mandatory = true)
                        },
                    )
                    readScreen(
                        "You have killed the chief",
                        "of the hobgoblin tribe.",
                        "The precious Silver Key",
                        "hangs around his neck.",
                        "With trembling hands",
                        "you pick it up!!!",
                        "",
                    )
                    achieve(Milestones.silverKey)
                    mark(Story.silverKey.keyTaken)
                }
            }
        }

        event(DungeonEvents.orcNest) {
            run {
                readScreen(
                    "Some orcs watch a trapdoor",
                    "in the floor here. As",
                    "you enter, they attack!",
                    "",
                )
                encounter(Monsters.orc, mandatory = true)
                readScreen(
                    "The last of the guardians",
                    "lie dead. Suddenly, more",
                    "orcs rush out of their",
                    "sleeping quarters,",
                    "disturbed by the noise.",
                    "",
                )
                encounter(Monsters.orc, mandatory = true)
                encounter(Monsters.orc, mandatory = true)
                readScreen("You have slain the orcs.", "")
            }
        }

        event(DungeonEvents.tammysPrison) {
            recurringUntil(Story.peasant.wife.rescued)
            run {
                whenever(Story.peasant.wife.accepted and !Story.peasant.wife.rescued) {
                    readScreen(
                        "The peasant's wife is in",
                        "this room. You untie",
                        "her bonds and let her",
                        "join your party!",
                        "",
                    )
                    mark(Story.peasant.wife.rescued)
                }
            }
        }

        event(DungeonEvents.erethorPriests) {
            recurring()
            run {
                readScreen(
                    "You are spotted by",
                    "some priests. They",
                    "attack you with blind",
                    "fury.",
                    "",
                )
                encounter(Monsters.erethorPriest, mandatory = true)
            }
        }

        event(DungeonEvents.luteChamber) {
            run {
                whenever(!Story.erethor.lute.luteTaken) {
                    readScreen(
                        "This is the altar chamber",
                        "of the temple. Hooded",
                        "shapes kneel at the",
                        "blood stained altar at",
                        "the end of this chamber.",
                        "",
                    )
                    readScreen("You rush to attack the", "priests...", "")
                    readScreen("They quickly turn around", "and draw their daggers.", "")
                    readScreen(
                        "The acolyte kneels before",
                        "the altar in silent prayer",
                        "while the others form a",
                        "circle around him to",
                        "protect him.",
                        "",
                    )
                    readScreen(
                        "Suddenly there is an",
                        "ear splitting bang",
                        "and a ghastly creature",
                        "emerges from the shadows",
                        "of this uncanny sanctuary.",
                        "",
                    )
                    encounter(Monsters.erethor, mandatory = true)
                    encounter(Monsters.erethorPriest, mandatory = true)
                    readScreen("More priests rush to", "attack you...", "")
                    encounter(Monsters.erethorPriest, mandatory = true)
                    readScreen(
                        "The last priest lies",
                        "dead. You stand still",
                        "for a moment, and then",
                        "start searching the",
                        "hall for the Lute.",
                        "",
                    )
                    val surprised = party.member(3)
                    readScreen {
                        line(partyName(surprised), " suddenly")
                        line("hears something behind")
                        line(pronoun(surprised, PronounCase.Object), "!")
                        line("")
                    }
                    readScreen(
                        "You scream in terror",
                        "when you see the source",
                        "of that sound! ERETHOR",
                        "HAS COME TO LIFE ONCE",
                        "AGAIN!!!",
                        "",
                    )
                    encounter(Monsters.erethor, mandatory = true)
                    readScreen(
                        "This time Erethor",
                        "remains dead. His flesh",
                        "blisters and bursts.",
                        "A rotten smell spreads",
                        "throughout the hall.",
                        "",
                    )
                    val finder = party.member(4)
                    readScreen {
                        line(partyName(finder), " finds the")
                        line("Lute on a pedestal,")
                        line("and you are all glad")
                        line("to leave this place.")
                        line("")
                    }
                    mark(Story.erethor.lute.luteTaken)
                }
            }
        }

        scenario(Dungeons.randomCave) {
            name("Random Cave")
            standardDungeonEvents()
        }

        scenario(Dungeons.fireShieldCave) {
            name("Fire Shield Cave")
            levels(20)
            standardDungeonEvents()
            level(19, DungeonEvents.fireShieldGuardian)
            level(20, DungeonEvents.fireShield)
        }

        scenario(Dungeons.thunTemple) {
            name("Thun Temple")
            levels(18)
            standardDungeonEvents()
            level(4, DungeonEvents.thunRiddle1)
            level(8, DungeonEvents.thunRiddle2)
            level(12, DungeonEvents.thunRiddle3)
            level(18, DungeonEvents.bracelets)
        }

        scenario(Dungeons.reebarTomb) {
            name("Reebar Tomb")
            levels(20)
            standardDungeonEvents()
            level(12, DungeonEvents.spectrumMessage)
            level(13, DungeonEvents.colourRiddle)
            level(14, DungeonEvents.colourRiddle)
            level(15, DungeonEvents.colourRiddle)
            level(16, DungeonEvents.colourRiddle)
            level(17, DungeonEvents.colourRiddle)
            level(18, DungeonEvents.colourRiddle)
            level(19, DungeonEvents.colourRiddle)
            level(20, DungeonEvents.nolrimTomb)
        }

        scenario(Dungeons.masterCrownDungeon) {
            name("Master Crown Dungeon")
            levels(10)
            standardDungeonEvents()
            level(10, DungeonEvents.masterCrown)
        }

        scenario(Dungeons.silverSanctum) {
            name("Silver Sanctum")
            levels(20)
            standardDungeonEvents()
            level(20, DungeonEvents.silverSanctum)
        }

        scenario(Dungeons.hobgoblinStronghold) {
            name("Hobgoblin Stronghold")
            levels(22)
            standardDungeonEvents()
            level(3, DungeonEvents.hobgoblinPatrol)
            level(6, DungeonEvents.hobgoblinPatrol)
            level(9, DungeonEvents.hobgoblinPatrol)
            level(14, DungeonEvents.hobgoblinPatrol)
            level(17, DungeonEvents.hobgoblinPatrol)
            level(19, DungeonEvents.hobgoblinPatrol)
            level(20, DungeonEvents.royalGuard1)
            level(21, DungeonEvents.royalGuard2)
            level(22, DungeonEvents.hobgoblinChief)
        }

        scenario(Dungeons.ogreCave) {
            name("Ogre Cave")
            levels(7)
            standardDungeonEvents()
            level(6, DungeonEvents.orcNest)
            level(7, DungeonEvents.tammysPrison)
        }

        scenario(Dungeons.erethorTemple) {
            name("Erethor Temple")
            levels(12)
            standardDungeonEvents()
            level(3, DungeonEvents.erethorPriests)
            level(7, DungeonEvents.erethorPriests)
            level(12, DungeonEvents.luteChamber)
        }
    }

    speak {
        standardConversation {
            npcSelection = ConversationNpcSelection.BuildingListOrWildernessLocation
            visitPolicy = NpcVisitPolicy.PerPartyVisitedBit
            branchSelection = DialogueBranchSelection.FirstMatchingStateThenDefault
            missionHints = MissionHintPolicy.BeforeEachMenu

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

            chitchat {
                subjects(
                    ChitchatSubject.Well,
                    ChitchatSubject.Weather,
                    ChitchatSubject.Health,
                )
                replies = ChitchatReplyPolicy.BySubjectAndNpcJob
            }

            rumour {
                usage = RumourUsagePolicy.OncePerConversationThenFallback
                source = RumourSourcePolicy.StandardTownRumours
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
                female {
                    lines("- You look well today,", "  miss...")
                }
                male {
                    lines("- You look well today,", "  Sir...")
                }
            }
            chitchatPrompt(ChitchatSubject.Weather) {
                any {
                    lines("- Nice weather we're", "  having...")
                }
            }
            chitchatPrompt(ChitchatSubject.Health) {
                female {
                    lines("- Is all well with", "  you, miss?")
                }
                male {
                    lines("- Is all well with", "  you, Sir?")
                }
            }

            chitchatReplies(ChitchatSubject.Well) {
                job(NpcJobs.merchant) {
                    lines("- Thanks, and the same", "to you!")
                }
                job(NpcJobs.ranger) {
                    lines("- I do? Really? Are", "you gay or smth'n?")
                }
                job(NpcJobs.fighter) {
                    lines("- Thank you. My health", "is slowly returning", "after a hard battle.")
                }
                job(NpcJobs.wizard) {
                    lines("- Aha?")
                }
                job(NpcJobs.priest) {
                    lines("- You too, my son.")
                }
                job(NpcJobs.thief) {
                    lines("- What? No! Sccch!", "They can hear you.")
                }
                job(NpcJobs.peasant) {
                    lines("- Thank you! You too.")
                }
                job(NpcJobs.bard) {
                    lines("- That inspires me.", "I'll compose a song", "about you!")
                }
                job(NpcJobs.drifter) {
                    lines("- YEAH? Take that!")
                }
                job(NpcJobs.halfOrc) {
                    lines("- ME NOT LOOK WELL!", "ME BIG AND UGLY!")
                }
                job(NpcJobs.halfElf) {
                    lines("- Oh...")
                }
                job(NpcJobs.smith) {
                    lines("- I thank thee, my lad!")
                }
                job(NpcJobs.bargirl) {
                    lines("- You too, honey!")
                }
                job(NpcJobs.innkeeper) {
                    lines("- The same to you.")
                }
                job(NpcJobs.tavernkeeper) {
                    lines("- You've had to much", "to drink, boy?")
                }
                job(NpcJobs.hermit) {
                    lines("- It is written in the", "scrolls of Skelos...")
                }
            }
            chitchatReplies(ChitchatSubject.Weather) {
                job(NpcJobs.merchant) {
                    lines("- I think it will rain.")
                }
                job(NpcJobs.ranger) {
                    lines("- Not a fine weather for", "tracking, mind you!")
                }
                job(NpcJobs.fighter) {
                    lines("- Yeah! But my armor", "gets quite rusty.")
                }
                job(NpcJobs.wizard) {
                    lines("- Really?")
                }
                job(NpcJobs.priest) {
                    lines("- The gods have been", "kind to us, my son!")
                }
                job(NpcJobs.thief) {
                    lines("- Perfect weather", "for stealing!")
                }
                job(NpcJobs.peasant) {
                    lines("- Yeah, the crops grow", "better than ever.")
                }
                job(NpcJobs.bard) {
                    lines("- This rain really", "inspires me. (He", "starts singing).")
                }
                job(NpcJobs.drifter) {
                    lines("- Hehehehe...")
                }
                job(NpcJobs.halfOrc) {
                    lines("- YEAH! NICE'N SLIMY!")
                }
                job(NpcJobs.halfElf) {
                    lines("- I find it depressing.")
                }
                job(NpcJobs.smith) {
                    lines("- Well, kinda good.")
                }
                job(NpcJobs.bargirl) {
                    lines("- Let's not talk about", "the weather, honey?")
                }
                job(NpcJobs.innkeeper) {
                    lines("- Well. Can't complain.")
                }
                job(NpcJobs.tavernkeeper) {
                    lines("- Want a drink, sonny?")
                }
                job(NpcJobs.hermit) {
                    lines("- Yes, a day like this", "is destined to come!")
                }
            }
            chitchatReplies(ChitchatSubject.Health) {
                job(NpcJobs.merchant) {
                    lines("- All's well, thanks.")
                }
                job(NpcJobs.ranger) {
                    lines("- I'm fine.")
                }
                job(NpcJobs.fighter) {
                    lines("- Yep.")
                }
                job(NpcJobs.wizard) {
                    lines("- Health... yes.")
                }
                job(NpcJobs.priest) {
                    lines("- All is well, my son.")
                }
                job(NpcJobs.thief) {
                    lines("- Yeh, quite nice.")
                }
                job(NpcJobs.peasant) {
                    lines("- Are you a fag?")
                }
                job(NpcJobs.bard) {
                    lines("- Yes. I will sing", "a song of health", "for you!")
                }
                job(NpcJobs.drifter) {
                    lines("(He chuckles under", "his breath).")
                }
                job(NpcJobs.halfOrc) {
                    lines("- ME JUST FINE!")
                }
                job(NpcJobs.halfElf) {
                    lines("- Very well, thanks.")
                }
                job(NpcJobs.smith) {
                    lines("- Fine, laddie!")
                }
                job(NpcJobs.bargirl) {
                    lines("- Sure, honey!")
                }
                job(NpcJobs.innkeeper) {
                    lines("- Well...")
                }
                job(NpcJobs.tavernkeeper) {
                    lines("- Just fine. Do you", "want something to", "drink?")
                }
                job(NpcJobs.hermit) {
                    lines("- Oh. Meneth Sabaoth.")
                }
            }

            questionReplies(QuestionTopic.People) {
                job(NpcJobs.merchant) {
                    lines("- Well, I don't know", "that many people.")
                }
                job(NpcJobs.ranger) {
                    lines("- No I'm a lone ranger.")
                }
                job(NpcJobs.fighter) {
                    lines("- Well, I met this", "merchant who seeks", "revenge on a band of", "bandits.")
                }
                job(NpcJobs.wizard) {
                    lines("- OH! Well... not really.")
                }
                job(NpcJobs.priest) {
                    lines("- I've heard of a hermit", "who lives in the swamps", "by Ollk. Perhaps he can", "help you.")
                }
                job(NpcJobs.thief) {
                    lines("- No. Sorry. Dunno.")
                }
                job(NpcJobs.peasant) {
                    lines("- I think it's time", "someone found the", "Foot of Jazzy and", "defeated Ydarg!")
                }
                job(NpcJobs.bard) {
                    lines {
                        line("- Rumour has it that a")
                        line("fighter called")
                        line(character(Roles.fighterIntro).name)
                        line("has a mission for you.")
                    }
                }
                job(NpcJobs.drifter) {
                    lines("- Don't know anyone.")
                }
                job(NpcJobs.halfOrc) {
                    lines("- ME ALONE AND STRONG!")
                }
                job(NpcJobs.halfElf) {
                    lines("- There are many huts", "in the Wilderness.")
                }
                job(NpcJobs.smith) {
                    lines("- Sorry, can't help ya.")
                }
                job(NpcJobs.bargirl) {
                    lines("- Well...")
                }
                job(NpcJobs.innkeeper) {
                    lines("- Want to meet interesting", "travellers? Try the", "inn or the tavern.")
                }
                job(NpcJobs.tavernkeeper) {
                    lines("- Don't know about", "anyone particular.")
                }
                job(NpcJobs.hermit) {
                    lines("- Many faces shine", "through the mist.")
                }
            }
            questionReplies(QuestionTopic.Magic) {
                job(NpcJobs.merchant) {
                    lines("- It is said that the", "Master Crown is the", "most powerful object", "in the realm.")
                }
                job(NpcJobs.ranger) {
                    lines("- You will need five", "magical items to", "defeat baron Ydarg.")
                }
                job(NpcJobs.fighter) {
                    lines("- What? I don't believe", "in magic...")
                }
                job(NpcJobs.wizard) {
                    lines("- Magic... Yes, Magic.")
                }
                job(NpcJobs.priest) {
                    lines("- Yes my son, but we", "are all puny beings", "compared to the gods.")
                }
                job(NpcJobs.thief) {
                    lines("- I know nothing of it.")
                }
                job(NpcJobs.peasant) {
                    lines("- I don't know much about", "magic. Gems often have", "magical properties,", "though.")
                }
                job(NpcJobs.bard) {
                    lines("- Magic is the subject", "of many of my songs.", "Ballad of the Fire", "Shield, for instance.")
                }
                job(NpcJobs.drifter) {
                    lines("- GO AWAY!")
                }
                job(NpcJobs.halfOrc) {
                    lines("- ME HAVE TRUSTY SWORD!", "ME NOT CARE 'BOUT", "MAGIK!")
                }
                job(NpcJobs.halfElf) {
                    lines("- Yes, my people were", "created from the magic", "of mother earth.")
                }
                job(NpcJobs.smith) {
                    lines("- Rumour has it, that", "some ogres have the foot", "of Jazzy, a powerful", "item.")
                }
                job(NpcJobs.bargirl) {
                    lines("- I know nothing about", "that.")
                }
                job(NpcJobs.innkeeper) {
                    lines("- Magic? Well, they say", "that baron Ydarg can", "be defeated only by", "aid of the Silver Key")
                }
                job(NpcJobs.tavernkeeper) {
                    lines("- Sorry. Can't help you.")
                }
                job(NpcJobs.hermit) {
                    lines("- The Crown, the Shield,", "the Bracelets and the Foot", "is all you need.")
                }
            }
            questionReplies(QuestionTopic.Lodging) {
                job(NpcJobs.merchant) {
                    lines("- Well. The city inn is", "a good try.")
                }
                job(NpcJobs.ranger) {
                    lines("- I sleep under the open", "skies. Just beware of", "bandits if you do this!")
                }
                job(NpcJobs.fighter) {
                    lines("- Well. I usually stay at", "the inn when I'm in town.")
                }
                job(NpcJobs.wizard) {
                    lines("- Oh??? Eternal sleep!")
                }
                job(NpcJobs.priest) {
                    lines("- I hire a room at", "the inn to meditate!")
                }
                job(NpcJobs.thief) {
                    lines("- I sleep in the shadows.")
                }
                job(NpcJobs.peasant) {
                    lines("- I sleep in my own", "room at my farm.")
                }
                job(NpcJobs.bard) {
                    lines("- Imagine the beauty", "and danger to camp in", "the Wilderness these days.")
                }
                job(NpcJobs.drifter) {
                    lines("- Look, why don't you", "just _GO AWAY_???")
                }
                job(NpcJobs.halfOrc) {
                    lines("- ME NEVER GET TIRED!")
                }
                job(NpcJobs.halfElf) {
                    lines("- I rent a room at the inn!")
                }
                job(NpcJobs.smith) {
                    lines("- Well, sleeping at the", "inn is often nicer than", "in the Wilderness.")
                }
                job(NpcJobs.innkeeper) {
                    lines("- Well. My inn is the", "best in the realm!")
                }
                job(NpcJobs.tavernkeeper) {
                    lines("- I dunno, try my", "brother's inn.")
                }
                job(NpcJobs.hermit) {
                    lines("- Yes. You are indeed", "the one...")
                }
            }

            rumourTable(RumourTables.standardTown) {
                rumour {
                    lines("Baron Ydarg's castle", "Zadraz is hidden in a", "forest to the soutwest.")
                }
                rumour {
                    lines("The Silver Key unlocks", "the gate to Ydarg's", "dimension.")
                }
                rumour {
                    lines("You will need the Fire", "shield to defeat baron", "Ydarg.")
                }
                rumour {
                    lines("You will need the", "Master Crown to", "defeat baron Ydarg.")
                }
                rumour {
                    lines("You will need the", "Bracelets of Thun", "to resist the most", "evil of all spells...")
                }
                rumour {
                    lines("The scythe is a", "useless weapon.")
                }
                rumour {
                    lines("Some say that you", "need gems to enter", "the old Temple of", "Thun.")
                }
                rumour {
                    lines("Seek the Hermit if", "you want to know", "of the Fire Shield")
                }
                rumour {
                    lines("The Hermit lives in", "the swamp.")
                }
                rumour {
                    lines("Always post a guard", "when you camp in the", "Wilderness.")
                }
                rumour {
                    lines("The Foot of Jazzy is", "one of the objects", "Baron Ydarg can't", "control!")
                }
                rumour {
                    lines("Skullweed plant grows", "deep in the swamp. It is", "a powerful reagent.")
                }
                rumour(Rumours.banditsQuestGiver) {
                    excludeWhen(Story.garth.bandits.recovered, Story.garth.bandits.closed)
                    lines {
                        line("A man named ", character(Roles.banditsQuestGiver).name)
                        line("will pay you for")
                        line("killing some bandits.")
                    }
                }
                rumour(Rumours.rangerQuestGiver) {
                    lines {
                        line("A hunter called ", character(Roles.rangersQuestGiver).name)
                        line("seeks a brave party")
                        line("of adventurers.")
                    }
                }
                rumour {
                    lines("It is always wise to", "haggle.")
                }
                rumour {
                    lines("Cthulhu will rise again!")
                }
                rumour {
                    lines("Baron Ydarg's castle", "in this dimension lies", "far to the southwest.")
                }
                rumour {
                    lines("Exploring caves can", "be profitable.")
                }
                rumour {
                    lines("Flee from all beasts", "that you are uncertain", "of.")
                }
                rumour {
                    lines("Before tempering in", "the business of a God,", "be a demi god yourself.")
                }
                rumour {
                    lines("Jazzy is a mighty", "lesser god or a demon.")
                }
                rumour {
                    lines("Baron Ydargs castle", "Zadraz is a gate to", "another dimension.")
                }
                rumour {
                    lines("Never meddle in the", "affairs of wizards, for", "they are subtle, and", "swift to anger.")
                }
                rumour {
                    lines("Magical objects can be", "a great aid in battle.")
                }
                rumour {
                    lines("Seek the holy handgrenade!")
                }
                rumour(Rumours.crownSeer) {
                    lines {
                        line("A man named ", character(Roles.crownSeer).name)
                        line("knows of the Crown.")
                        line("He lives ", character(Roles.crownSeer).location, "!")
                    }
                }
                rumour {
                    lines {
                        line(town(Towns.murn).name, " is situated")
                        line("somewhere near ", town(Towns.murn).near(radius = 5), "!")
                    }
                }
                rumour(Rumours.templeOfThunSeer) {
                    lines {
                        line("Seek ", character(Roles.templeOfThunSeer).name, " if you")
                        line("search the Temple of")
                        line("Thun! He lives ", character(Roles.templeOfThunSeer).location)
                    }
                }
                rumour {
                    lines("The more powerful", "creatures, the more", "experience.")
                }
                rumour {
                    lines("A good sword beats", "four aces!")
                }
                rumour {
                    lines("You need FIVE items,", "no less, no more", "to kill Ydarg!")
                }
                rumour {
                    lines {
                        line(town(Towns.wilor).name, " is situated")
                        line("at ", town(Towns.wilor).near(radius = 0), ".")
                    }
                }
                rumour {
                    lines {
                        line(town(Towns.ollk).name, " is situated")
                        line("somewhere near ", town(Towns.ollk).near(radius = 5), "!")
                    }
                }
            }
        }

        questionAction(QuestionTopic.Lodging, NpcJobs.bargirl) {
            val price = localInt(party.leader.level * 50, "bargirl_lodging_price")
            choice.yesNo(
                structuredText {
                    line("- Would you like to,")
                    line("  spend an hour")
                    line("  upstairs for ", price)
                    line("  gold coins?")
                    line("")
                    line("(Y)es")
                    line("(N)o")
                },
                yes = {
                    whenever(party.gold lt price) {
                        screen("- No money, eh?", "  don't bother", "  to come back!")
                    } otherwise {
                        party.gold -= price
                        forEachMember {
                            whenever(it.hp gt 1) {
                                it.hp -= 1
                            }
                        }
                        screen("You spend a very", "exhausting hour", "upstairs.", "(PERVERT!) ;-)", "")
                    }
                },
            )
        }
    }

    towns {
        town(Towns.thord) {
            name = "Thord"
            position = xy(12, 11)
            taxPercent = 7
            village()
            services {
                merchant()
                inn()
                tavern()
                smith()
                church()
                guild()
            }
        }

        town(Towns.ollk) {
            name = "Ollk"
            position = xy(48, 12)
            taxPercent = 10
            village()
            services {
                merchant()
                inn()
                tavern()
                smith()
                guild()
            }
        }

        town(Towns.wilor) {
            name = "Wilor"
            position = xy(43, 20)
            taxPercent = 9
            city()
            services {
                merchant()
                inn()
                tavern()
                smith()
                church()
                guild()
            }
        }

        town(Towns.murn) {
            name = "Murn"
            position = xy(55, 29)
            taxPercent = 8
            village()
            services {
                merchant()
                inn()
                tavern()
                smith()
                guild()
            }
        }
    }

    townSystem {
        leavePrompts(EquipmentCommand.ReadyWeapon, EquipmentCommand.ReadyArmor)
        entryTax {
            minimumLeaderLevel = 5
            pay = inputKey("P", label = "Pay to Enter")
            leave = inputKey("L", label = "Leave")
        }
        menu {
            church()
            smith()
            inn()
            merchant()
            tavern()
            guild()
            leave()
        }

        buildingServices {
            church {
                scene = Scenes.Towne.church
                alwaysOpen()
            }
            smith {
                scene = Scenes.Towne.smith
                hours = 6..18
            }
            inn {
                scene = Scenes.Towne.inn
                alwaysOpen()
            }
            merchant {
                scene = Scenes.Towne.merchant
                hours = 6..18
            }
            tavern {
                scene = Scenes.Towne.tavern
                hours = 6..1
            }
            guild {
                scene = Scenes.Towne.guild
                alwaysOpen()
            }
        }
    }

    characters {
        character(Npcs.garth, "Garth", NpcJobs.merchant) {
            fixed(Towns.thord.inn)
            role(Roles.banditsQuestGiver)
        }

        character(Npcs.nrimTo, "Nrim'to", NpcJobs.ranger) {
            randomPlacement()
            role(Roles.rangersQuestGiver)
        }

        character(Npcs.areth, "Areth", NpcJobs.fighter) {
            randomPlacement()
            role(Roles.fighterIntro)
        }

        character(Npcs.nevaak, "Nevaak", NpcJobs.wizard) {
            randomPlacement()
            role(Roles.wizardRobbed)
        }

        character(Npcs.gorm, "Gorm", NpcJobs.priest) {
            randomPlacement()
            role(Roles.priestWithBook)
        }

        character(Npcs.bernak, "Bernak", NpcJobs.thief) {
            randomPlacement()
            role(Roles.silverKeyVendor)
        }

        character(Npcs.matias, "Matias", NpcJobs.peasant) {
            randomPlacement()
        }

        character(Npcs.tybalt, "Tybalt", NpcJobs.bard) {
            randomPlacement()
            role(Roles.erethorLuteBard)
        }

        character(Npcs.romeo, "Romeo", NpcJobs.drifter) {
            randomPlacement()
        }

        character(Npcs.werra, "Werra", NpcJobs.halfOrc) {
            randomPlacement()
        }

        character(Npcs.arenjun, "Arenjun", NpcJobs.halfElf) {
            randomPlacement()
        }

        character(Npcs.oskar, "Oskar", NpcJobs.smith) {
            randomPlacement()
        }

        character(Npcs.harry, "Harry", NpcJobs.merchant) {
            randomPlacement()
            role(Roles.templeOfThunSeer)
        }

        character(Npcs.marraga, "Marraga", NpcJobs.ranger) {
            randomPlacement()
            role(Roles.rangerBrother)
        }

        character(Npcs.broth, "Broth", NpcJobs.fighter) {
            randomPlacement()
            role(Roles.crownSeer)
        }

        character(Npcs.ebond, "Ebond", NpcJobs.wizard) {
            randomPlacement()
        }

        character(Npcs.bolph, "Bolph", NpcJobs.priest) {
            randomPlacement()
        }

        character(Npcs.gotharp, "Gotharp", NpcJobs.thief) {
            randomPlacement()
            role(Roles.masterThief)
        }

        character(Npcs.ron, "Ron", NpcJobs.peasant) {
            randomPlacement()
            role(Roles.peasantHusband)
        }

        character(Npcs.kaal, "Kaal", NpcJobs.bard) {
            randomPlacement()
            role(Roles.minstrelCloak)
        }

        character(Npcs.daniel, "Daniel", NpcJobs.drifter) {
            randomPlacement()
        }

        character(Npcs.leander, "Leander", NpcJobs.halfOrc) {
            randomPlacement()
            role(Roles.jazzyOgreTrader)
        }

        character(Npcs.ithienn, "Ithienn", NpcJobs.halfElf) {
            randomPlacement()
            role(Roles.wizardThreeTasks)
        }

        character(Npcs.azaaza, "Azaaza", NpcJobs.smith) {
            randomPlacement()
            role(Roles.silverKeySmith)
        }

        character(Npcs.hassan, "Hassan", NpcJobs.innkeeper) {
            fixed(Towns.thord.inn)
        }

        character(Npcs.luqas, "Luqas", NpcJobs.innkeeper) {
            fixed(Towns.ollk.inn)
        }

        character(Npcs.darth, "Darth", NpcJobs.innkeeper) {
            fixed(Towns.wilor.inn)
        }

        character(Npcs.yog, "Yog", NpcJobs.innkeeper) {
            fixed(Towns.murn.inn)
        }

        character(Npcs.niklas, "Niklas", NpcJobs.tavernkeeper) {
            fixed(Towns.thord.tavern)
        }

        character(Npcs.tokawa, "Tokawa", NpcJobs.tavernkeeper) {
            fixed(Towns.ollk.tavern)
        }

        character(Npcs.miyaki, "Miyaki", NpcJobs.tavernkeeper) {
            fixed(Towns.wilor.tavern)
        }

        character(Npcs.dargoth, "Dargoth", NpcJobs.tavernkeeper) {
            fixed(Towns.murn.tavern)
        }

        character(Npcs.marcus, "Marcus", NpcJobs.hermit) {
            fixed(Wilderness.xy(48, 7))
            role(Roles.hermitTrollTooth)
        }

        character(Npcs.deanna, "Deanna", NpcJobs.bargirl) {
            gender = Gender.Female
            fixed(Towns.thord.tavern)
        }

        character(Npcs.jeanne, "Jeanne", NpcJobs.bargirl) {
            gender = Gender.Female
            fixed(Towns.wilor.tavern)
        }

        // The original C puts these trailing merchant slots in wilderness at
        // 0,0. Authoring should state intent instead of inheriting an implicit
        // origin hut, so keep them as wilderness-randomized people unless old
        // data archaeology proves explicit hut coordinates.
        character(Npcs.alex, "Alex", NpcJobs.merchant) {
            randomWilderness()
        }

        character(Npcs.galdon, "Galdon", NpcJobs.merchant) {
            randomWilderness()
        }

        character(Npcs.kosch, "Kosch", NpcJobs.merchant) {
            randomWilderness()
        }

        character(Npcs.ufarsin, "Ufarsin", NpcJobs.merchant) {
            randomWilderness()
        }
    }

    monsters {
        monster(Monsters.kobold) {
            name = "Kobold"
            count = 2..8
            hitPoints = 30..50
            weaponSkill = 35
            tier = EncounterTiers.weak
        }

        monster(Monsters.orc) {
            name = "Orc"
            count = 2..5
            hitPoints = 50..99
            weaponSkill = 50
            tier = EncounterTiers.weak
        }

        monster(Monsters.troll) {
            name = "Troll"
            count = 1..3
            hitPoints = 99..199
            weaponSkill = 55
            tier = EncounterTiers.weak
        }

        monster(Monsters.warrior) {
            name = "Warrior"
            count = 1..4
            hitPoints = 50..99
            weaponSkill = 60
            tier = EncounterTiers.weak
        }

        monster(Monsters.goblin) {
            name = "Goblin"
            count = 2..7
            hitPoints = 50..80
            weaponSkill = 30
            tier = EncounterTiers.weak
        }

        monster(Monsters.wildDog) {
            name = "Wild Dog"
            count = 1..3
            hitPoints = 15..40
            weaponSkill = 60
            tier = EncounterTiers.weak
        }

        monster(Monsters.knight) {
            name = "Knight"
            count = 1..5
            hitPoints = 25..99
            weaponSkill = 75
            tier = EncounterTiers.weak
        }

        monster(Monsters.bandit) {
            name = "Bandit"
            count = 2..6
            hitPoints = 50..99
            weaponSkill = 50
            tier = EncounterTiers.weak
        }

        monster(Monsters.hobgoblin) {
            name = "Hobgoblin"
            count = 2..5
            hitPoints = 20..70
            weaponSkill = 40
            spellPower = 5
            tier = EncounterTiers.weak
        }

        monster(Monsters.zombie) {
            name = "Zombie"
            count = 2..4
            hitPoints = 70..70
            weaponSkill = 65
            spellPower = 5
            tier = EncounterTiers.weak
        }

        monster(Monsters.stirge) {
            name = "Stirge"
            count = 6..8
            hitPoints = 10..20
            weaponSkill = 90
            tier = EncounterTiers.wild
        }

        monster(Monsters.guardian) {
            name = "Guardian"
            count = 1..1
            hitPoints = 250..350
            weaponSkill = 80
        }

        monster(Monsters.norker) {
            name = "Norker"
            count = 1..1
            hitPoints = 350..400
            weaponSkill = 90
        }

        monster(Monsters.voodooLord) {
            name = "Voodoo Lord"
            count = 1..1
            hitPoints = 300..350
            weaponSkill = 80
        }

        monster(Monsters.archdevil) {
            name = "Archdevil"
            count = 1..1
            hitPoints = 450..450
            weaponSkill = 85
            spellPower = 60
        }

        monster(Monsters.koboldHorde) {
            name = "Kobold"
            count = 6..8
            hitPoints = 30..45
            weaponSkill = 25
        }

        monster(Monsters.rainbowDemon) {
            name = "Rainbow Demon"
            count = 1..1
            hitPoints = 150..250
            weaponSkill = 95
            spellPower = 10
        }

        monster(Monsters.longDeadWarrior) {
            name = "Long Dead Warrior"
            count = 1..1
            hitPoints = 400..500
            weaponSkill = 80
        }

        monster(Monsters.ogre) {
            name = "Ogre"
            count = 4..7
            hitPoints = 90..190
            weaponSkill = 65
        }

        monster(Monsters.ogreLord) {
            name = "Ogre Lord"
            count = 1..1
            hitPoints = 190..270
            weaponSkill = 85
            spellPower = 25
        }

        monster(Monsters.wingedThing) {
            name = "Winged Thing"
            count = 7..8
            hitPoints = 150..200
            weaponSkill = 95
        }

        monster(Monsters.jazzy) {
            name = "Jazzy"
            count = 1..1
            hitPoints = 450..580
            weaponSkill = 95
            spellPower = 50
            immune = true
        }

        monster(Monsters.ridingHobgoblin) {
            name = "Riding Hobgoblin"
            count = 4..7
            hitPoints = 30..70
            weaponSkill = 40
            spellPower = 10
        }

        monster(Monsters.hobgoblinChief) {
            name = "Hobgoblin Chief"
            count = 1..1
            hitPoints = 150..230
            weaponSkill = 100
            spellPower = 40
        }

        monster(Monsters.hobgoblinSkeleton) {
            name = "Hobgoblin Skeleton"
            count = 5..6
            hitPoints = 50..60
            weaponSkill = 90
        }

        monster(Monsters.undeadHobgoblin) {
            name = "Undead Hobgoblin"
            count = 5..5
            hitPoints = 60..70
            weaponSkill = 95
        }

        monster(Monsters.cryptHorror) {
            name = "Crypt Horror"
            count = 3..4
            hitPoints = 100..200
            weaponSkill = 60
            spellPower = 5
        }

        monster(Monsters.nightGaunt) {
            name = "Night Gaunt"
            count = 1..2
            hitPoints = 200..350
            weaponSkill = 90
            spellPower = 10
        }

        monster(Monsters.thyraga) {
            name = "Thyraga"
            count = 1..2
            hitPoints = 250..300
            weaponSkill = 90
        }

        monster(Monsters.liche) {
            name = "Liche"
            count = 1..1
            hitPoints = 300..400
            weaponSkill = 85
        }

        monster(Monsters.baronYdarg) {
            name = "Baron Ydarg"
            count = 1..1
            hitPoints = 500..1000
            weaponSkill = 90
            spellPower = 5
            immune = true
        }

        monster(Monsters.gnoll) {
            name = "Gnoll"
            count = 2..2
            countMaxLevelDivisor = 2
            hitPoints = 60..80
            weaponSkill = 70
        }

        monster(Monsters.erethorPriest) {
            name = "Erethor Priest"
            count = 3..4
            hitPoints = 80..99
            weaponSkill = 70
            spellPower = 50
        }

        monster(Monsters.erethor) {
            name = "Erethor"
            count = 1..1
            hitPoints = 600..900
            weaponSkill = 80
            spellPower = 10
        }

        monster(Monsters.wanderMage) {
            name = "Wander Mage"
            count = 3..4
            hitPoints = 50..60
            weaponSkill = 20
            spellPower = 80
            tier = EncounterTiers.wild
        }

        monster(Monsters.hillGiant) {
            name = "Hill Giant"
            count = 1..2
            hitPoints = 100..190
            weaponSkill = 40
            tier = EncounterTiers.wild
        }

        monster(Monsters.wizard) {
            name = "Wizard"
            count = 2..4
            hitPoints = 30..50
            weaponSkill = 40
            spellPower = 70
            tier = EncounterTiers.wild
        }

        monster(Monsters.druid) {
            name = "Druid"
            count = 3..4
            hitPoints = 50..60
            weaponSkill = 60
            spellPower = 60
            tier = EncounterTiers.wild
        }

        monster(Monsters.sabreToothTiger) {
            name = "Sabre Tooth Tiger"
            count = 1..2
            hitPoints = 100..120
            weaponSkill = 95
            tier = EncounterTiers.wild
        }

        monster(Monsters.headHunter) {
            name = "Head Hunter"
            count = 3..5
            hitPoints = 60..80
            weaponSkill = 65
            tier = EncounterTiers.wild
        }

        monster(Monsters.skeleton) {
            name = "Skeleton"
            count = 3..6
            hitPoints = 20..60
            weaponSkill = 70
            tier = EncounterTiers.wild
        }

        monster(Monsters.vampireBat) {
            name = "Vampire Bat"
            count = 7..8
            hitPoints = 10..20
            weaponSkill = 55
            tier = EncounterTiers.wild
        }

        monster(Monsters.giantSnake) {
            name = "Giant Snake"
            count = 1..3
            hitPoints = 40..90
            weaponSkill = 60
            tier = EncounterTiers.wild
        }

        monster(Monsters.ghoul) {
            name = "Ghoul"
            count = 1..2
            hitPoints = 100..150
            weaponSkill = 35
            spellPower = 10
            tier = EncounterTiers.wild
        }

        monster(Monsters.spider) {
            name = "Spider"
            count = 5..8
            hitPoints = 10..20
            weaponSkill = 95
            tier = EncounterTiers.wild
        }

        monster(Monsters.hugeLizard) {
            name = "Huge Lizard"
            count = 3..5
            hitPoints = 30..60
            weaponSkill = 75
            tier = EncounterTiers.wild
        }

        monster(Monsters.dryder) {
            name = "Dryder"
            count = 1..5
            hitPoints = 80..130
            weaponSkill = 75
            spellPower = 20
            tier = EncounterTiers.wild
        }

        monster(Monsters.xvart) {
            name = "Xvart"
            count = 2..2
            hitPoints = 50..75
            weaponSkill = 80
            spellPower = 10
            tier = EncounterTiers.deep
        }

        monster(Monsters.jellyCube) {
            name = "Jelly Cube"
            count = 1..1
            hitPoints = 200..300
            weaponSkill = 50
            spellPower = 2
            tier = EncounterTiers.deep
        }

        monster(Monsters.troglodyte) {
            name = "Troglodyte"
            count = 2..4
            hitPoints = 40..60
            weaponSkill = 70
            spellPower = 5
            tier = EncounterTiers.deep
        }

        monster(Monsters.bugbear) {
            name = "Bugbear"
            count = 3..4
            hitPoints = 40..50
            weaponSkill = 70
            tier = EncounterTiers.deep
        }

        monster(Monsters.drowElf) {
            name = "Drow Elf"
            count = 2..4
            hitPoints = 80..100
            weaponSkill = 70
            spellPower = 50
            tier = EncounterTiers.deep
        }

        monster(Monsters.phantom) {
            name = "Phantom"
            count = 2..3
            hitPoints = 100..200
            weaponSkill = 80
            spellPower = 40
            tier = EncounterTiers.deep
        }

        monster(Monsters.worg) {
            name = "Worg"
            count = 2..4
            hitPoints = 80..100
            weaponSkill = 60
            spellPower = 20
            tier = EncounterTiers.deep
        }

        monster(Monsters.rustMonster) {
            name = "Rust Monster"
            count = 1..1
            hitPoints = 150..250
            weaponSkill = 70
            tier = EncounterTiers.deep
        }

        monster(Monsters.owlbear) {
            name = "Owlbear"
            count = 2..3
            hitPoints = 100..120
            weaponSkill = 70
            tier = EncounterTiers.deep
        }

        monster(Monsters.werewolf) {
            name = "Werewolf"
            count = 1..1
            hitPoints = 300..300
            weaponSkill = 90
            tier = EncounterTiers.deep
        }

        monster(Monsters.hookHorror) {
            name = "Hook Horror"
            count = 2..3
            hitPoints = 70..100
            weaponSkill = 85
            tier = EncounterTiers.deep
        }

        monster(Monsters.minotaur) {
            name = "Minotaur"
            count = 1..3
            hitPoints = 200..240
            weaponSkill = 80
            tier = EncounterTiers.deep
        }

        monster(Monsters.imp) {
            name = "Imp"
            count = 1..3
            hitPoints = 100..120
            weaponSkill = 90
            spellPower = 30
            tier = EncounterTiers.deep
        }

        monster(Monsters.svirfneblin) {
            name = "Svirfneblin"
            count = 2..3
            hitPoints = 100..100
            weaponSkill = 90
            spellPower = 20
            tier = EncounterTiers.deep
        }

        monster(Monsters.yeti) {
            name = "Yeti"
            count = 1..1
            hitPoints = 300..350
            weaponSkill = 60
            tier = EncounterTiers.deep
        }

        monster(Monsters.wight) {
            name = "Wight"
            count = 1..3
            hitPoints = 90..120
            weaponSkill = 95
            spellPower = 35
            tier = EncounterTiers.deep
        }

        monster(Monsters.wraith) {
            name = "Wraith"
            count = 2..4
            hitPoints = 80..110
            weaponSkill = 80
            spellPower = 25
            tier = EncounterTiers.deep
        }

        monster(Monsters.basilisk) {
            name = "Basilisk"
            count = 1..1
            hitPoints = 240..300
            weaponSkill = 60
            spellPower = 20
            tier = EncounterTiers.deep
        }

        monster(Monsters.wyvern) {
            name = "Wyvern"
            count = 1..1
            hitPoints = 350..450
            weaponSkill = 80
            tier = EncounterTiers.deep
        }

        monster(Monsters.tunnelWorm) {
            name = "Tunnel Worm"
            count = 1..1
            hitPoints = 300..350
            weaponSkill = 45
            tier = EncounterTiers.deep
        }

        monster(Monsters.skagerbergKiller) {
            name = "Skagerberg Killer"
            count = 1..1
            hitPoints = 2000..3500
            weaponSkill = 100
            spellPower = 20
            immune = true
        }
    }

    missions {
        mission(Missions.erethorEntry) {
            at(xy(6, 20))
            requires(Story.erethor.lute.rumourTold)
            runs(Scenes.Missions.erethorEntry)
        }

        mission(Missions.husbandCave) {
            at(xy(42, 23))
            requires(Story.peasant.wife.accepted)
            runs(Scenes.Missions.husbandCave)
        }

        mission(Missions.bandits) {
            randomStep(percent = 9)
            requires(Story.garth.bandits.accepted)
            runs(Scenes.Missions.bandits)
        }

        mission(Missions.trollTooth) {
            randomStep(percent = 9)
            requires(Story.hermit.trollTooth.accepted)
            runs(Scenes.Missions.trollTooth)
        }

        mission(Missions.hermitDungeon) {
            at(Locations.hermitDungeon)
            requires(Story.hermit.trollTooth.delivered)
            runs(Scenes.Missions.hermitDungeon)
        }

        mission(Missions.thunTemple) {
            at(Locations.thunTemple)
            requires(Story.bracelets.thun.mapBought)
            runs(Scenes.Missions.thunTemple)
        }

        mission(Missions.koboldVillage) {
            at(Locations.koboldVillage)
            requires(Story.masterCrown.task1Assigned)
            runs(Scenes.Missions.koboldVillage)
        }

        mission(Missions.skullweed) {
            onTerrain(Terrain.swamp)
            requires(Story.masterCrown.task2Assigned)
            runs(Scenes.Missions.skullweed)
        }

        mission(Missions.borinMeet) {
            at(Locations.borinCamp)
            requires(Story.masterCrown.task3Assigned)
            runs(Scenes.Missions.borinMeet)
        }

        mission(Missions.nolrimCrypt) {
            at(Locations.nolrimCrypt)
            requires(Story.masterCrown.borinMet)
            runs(Scenes.Missions.nolrimCrypt)
        }

        mission(Missions.borinGift) {
            at(Locations.borinCamp)
            requires(Story.masterCrown.daggerTaken)
            runs(Scenes.Missions.borinGift)
        }

        mission(Missions.masterCrown) {
            at(Locations.masterCrownDungeon)
            requires(Story.masterCrown.crownTold)
            runs(Scenes.Missions.masterCrown)
        }

        mission(Missions.ogreCave) {
            at(Locations.beastCave)
            requires(Story.jazzy.ogre.accepted)
            runs(Scenes.Missions.ogreCave)
        }

        mission(Missions.jazzyFoot) {
            onTerrain(Terrain.foothills)
            requires(Story.jazzy.ogre.ogresCleared)
            runs(Scenes.Missions.jazzyFoot)
        }

        mission(Missions.silverKey) {
            at(Locations.silverKeyPit)
            requires(Story.silverKey.mapBought)
            runs(Scenes.Missions.silverKey)
        }

        mission(Missions.hobgoblinCaravan) {
            randomStep(percent = 9)
            requires(Story.silverKey.smithConfessed)
            runs(Scenes.Missions.hobgoblinCaravan)
        }

        mission(Missions.hobgoblinCave) {
            at(Locations.hobgoblinCave)
            requires(Story.silverKey.caveLocated)
            runs(Scenes.Missions.hobgoblinCave)
        }
    }

    scene(Scenes.Towne.church) {
        clearMapWindow()
        showBackdrop(Images.Scene.church)

        loop {
            clearText()
            statusLine()
            screen(
                "You enter the church",
                "of Dalina, a beautiful",
                "goddess. This is one of",
                "her two temples.",
                "",
            )

            val churchPartyMemberMax = realm.partyMemberMax
            val churchWhoPrompt = structuredText { line("Who? (1-", churchPartyMemberMax, ")") }

            menu(
                "(H)eal person",
                "(R)aise dead",
                "(E)nergize",
                "(L)eave",
            ) {
                option('H') {
                    clearText()
                    val m = choice.member(
                        churchWhoPrompt,
                        filter = MemberChoiceFilter.AliveOnly,
                        accepting = MemberChoiceFilter.Any,
                    )

                    whenever(m.dead) {
                        screen {
                            lines(
                                "- GET THAT CORPSE",
                                "OUTTA HERE!",
                                "(You are thrown out).",
                                "",
                            )
                        }
                        pause(1200)
                        breakLoop("corpse exits the church service")
                    } otherwise {
                        whenever(m.hp eq m.maxHp) {
                            screen {
                                line(partyName(m), " needs no healing.")
                            }
                            pause(1000)
                        } otherwise {
                            val price = localInt((m.maxHp - m.hp) * 7)
                            clearText()
                            screen {
                                line("- That'll be ", price)
                                line("gold coins, please.")
                                line("")
                            }

                            choice.payNoThanks(price,
                                broke = {
                                    screen {
                                        lines(
                                            "You don't have enough",
                                            "gold!",
                                        )
                                    }
                                    pause(1000)
                                },
                                paid = {
                                    clearText()
                                    m.hp set m.maxHp
                                    screen {
                                        line("The priestess heals")
                                        line(pronoun(m, PronounCase.Object), " fully.")
                                    }
                                    pause(1000)
                                },
                            )
                        }
                    }
                }

                option('E') {
                    clearText()
                    val m = choice.member(
                        churchWhoPrompt,
                        filter = MemberChoiceFilter.SpellcasterOnly,
                        accepting = MemberChoiceFilter.Any,
                    )

                    whenever(!m.spellcaster) {
                        clearText()
                        screen("Not a spellcaster!")
                        pause(1000)
                    } otherwise {
                        whenever(m.mp eq m.wisdom) {
                            clearText()
                            screen {
                                lines(
                                    "That character needs",
                                    "no spell points",
                                    "to be restored.",
                                )
                            }
                            pause(2000)
                        } otherwise {
                            clearText()
                            val price = localInt(m.level * (m.wisdom - m.mp) * 2)
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
                                    m.mp set m.wisdom
                                    clearText()
                                    screen {
                                        lines(
                                            "The priestess",
                                            "restores his energy.",
                                        )
                                    }
                                    pause(1000)
                                },
                            )
                        }
                    }
                }

                option('R') {
                    clearText()
                    val m = choice.member(
                        churchWhoPrompt,
                        filter = MemberChoiceFilter.DeadOnly,
                        accepting = MemberChoiceFilter.Any,
                    )

                    whenever(!m.dead) {
                        clearText()
                        screen {
                            line("- ", pronoun(m, PronounCase.Subject), " seems very alive")
                            line("at the moment.")
                        }
                        pause(1200)
                    } otherwise {
                        clearText()
                        val price = localInt(m.level * 650)
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
                                m.hp set 1
                                m.dead set false
                                party.memberCount += 1
                                clearText()
                                screen {
                                    line("The priestess raises")
                                    line(pronoun(m, PronounCase.Object), ".")
                                }
                                pause(1000)
                            },
                        )
                    }
                }

                option('L') {
                    clearText()
                    clearMapWindow()
                    breakLoop("leave church")
                }
            }
        }
    }

    scene(Scenes.Towne.smith) {
        shopService(Buildings.smith) {
            image = Images.Scene.smith
            closed("Shop Closed.")
            intro(
                "Smith.",
                "",
                "The smith welcomes you",
                "to his small shop.",
                "",
            )
            menu("(B)uy", "(S)ell", "(L)eave")
            goodbye("Well, bye then...")

            interlude(chancePercent = 9) {
                forceFirstUntil(Story.smith.beerOffer.offered)
                offer(
                    "The smith offers you some",
                    "beer, and the latest news",
                    "from the realm.",
                    "",
                )
                accepted("You accept the invitation.")
                accepted("After a while the smith", "leaves to get more beer.")
                accepted(
                    "When he returns, you ask",
                    "him for the latest news...",
                    "",
                )
                declined(
                    "The smith is disappointed.",
                    "- Maybe another time,",
                    "you tell him.",
                    "- Yeah, he answers.",
                    "",
                )
                runsRumourAfterAccept = true
            }

            buy {
                prompt(
                    "Armor or Weapon?",
                    "",
                    "(A)rmor",
                    "(W)eapon",
                    "(N)othing",
                )
                tooExpensive("Too expensive...")
                cantCarry("You cannot carry more.")
                deal("Deal!")

                catalog('A', "Armor") {
                    intro("We have...", "")
                    item('1', Items.leather, label = "Leather")
                    item('2', Items.chainMail, label = "Chain Mail")
                    item('3', Items.plateMail, label = "Plate Mail")
                    item('4', Items.ringMail, label = "Ring Mail")
                    item('5', Items.elvinCloak, label = "Elvin Cloak")
                    item('6', Items.mithrilMail, label = "Mithril Mail")
                }

                catalog('W', "Weapons") {
                    intro("We have...", "")
                    page {
                        nextPage()
                        item('1', Items.sword, label = "Sword")
                        item('2', Items.axe, label = "Axe")
                        item('3', Items.bow, label = "Bow")
                        item('4', Items.club, label = "Club")
                        item('5', Items.halbard, label = "Halbard")
                        item('6', Items.crossbow, label = "Crossbow")
                    }
                    page {
                        previousPage()
                        item('7', Items.longsword, label = "Longsword")
                        item('8', Items.twoHandSword, label = "2h-Sword")
                        item('9', Items.poleArm, label = "Pole Arm")
                        item('A', Items.trident, label = "Trident")
                        item('B', Items.magicDagger, label = "Mgk Dagger")
                        item('C', Items.magicSword, label = "Mgk Sword")
                    }
                }
            }

            sell {
                prompt("Sell what?", "")
                acceptsKinds(ItemKind.Weapon, ItemKind.Armor)
                wrongKind("Not weapon or armor.")
                offer {
                    line("Ok! Give ya ", price, "!")
                    line("")
                }
                declined("No thanks.")
                bargain {
                    bonus(rollGreaterThan = 90, addCurrentDivisor = 4)
                    bonus(rollGreaterThan = 75, addCurrentDivisor = 6)
                    rejectWhenRollBelow(76)
                }
            }
        }
    }

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

    scene(Scenes.Towne.merchant) {
        shopService(Buildings.merchant) {
            image = Images.Scene.merchant
            closed("Shop Closed.")
            intro(
                "Merchant",
                "",
                "Can I help you? I",
                "trade in the finest",
                "items in the realm!",
                "",
            )
            menu("(B)uy item", "(S)ell item", "(L)eave")
            goodbye("Well, bye then...")

            buy {
                prompt("Buy what?", "")
                tooExpensive("Too expensive!")
                cantCarry("You cannot carry more.")
                deal("Deal!")
                unitPrice {
                    line(price, " gold each, sir.")
                }
                singlePrice {
                    line("Price: ", price)
                    line("")
                }
                payPrompt("(P)ay", "(N)one")

                catalog('B', "General goods") {
                    resource(
                        'A',
                        Items.torch,
                        resource = ShopResources.torches,
                        cap = ShopQuantityCaps.fixed(Resource.Torches, 80),
                        tooMany = inlineText("You can carry no", "more than 80", "torches..."),
                        label = "Torch",
                        zeroQuantityDeal = ShopZeroQuantityDeal.always,
                    ) {
                        lines("Ah, the finest torches", "in the realm.")
                    }
                    resource(
                        'B',
                        Items.ration,
                        resource = ShopResources.food,
                        cap = ShopQuantityCaps.carryCapacity(Resource.Food),
                        tooMany = shopText {
                            line("You can carry no")
                            line("more than ", cap, " units!")
                        },
                        label = "Food Ration",
                        price = ShopPrices.leaderLevelPlus(1),
                    ) {
                        lines(
                            "Better food is not",
                            "availible anywhere else",
                            "in the entire realm.",
                        )
                    }
                    item('C', Items.blanket, label = "Blanket") {
                        lines("Our blankets are very", "warm and soft.")
                    }
                    item('D', Items.tent, label = "Tent") {
                        lines(
                            "Noone manufactures",
                            "better tents than",
                            "we do!",
                        )
                    }
                    item('E', Items.rope, label = "Rope") {
                        lines("The finest hemp", "ropes in the realm.")
                    }
                    item(
                        'F',
                        Items.redPotion,
                        label = "Healing Potion",
                        price = ShopPrices.itemCostPlusLeaderLevelTimes(3),
                    ) {
                        lines(
                            "The best healing",
                            "potions availible",
                            "for money!",
                        )
                    }
                    item('G', Items.donkey, label = "Donkey") {
                        lines(
                            "Our stout donkeys",
                            "are bred just for",
                            "carrying your food",
                            "and equipment.",
                        )
                    }
                }
            }

            sell {
                prompt("Sell what?", "")
                accepts(Items.ration, Items.blanket, Items.tent, Items.rope)
                acceptsKinds(ItemKind.Potion, ItemKind.Treasure, ItemKind.MagicItem)
                rejects(Items.donkey)
                wrongKind("- I don't deal in that", "  kind of stuff!")
                offer {
                    line("- Ok. Give ya ", price, "!")
                    line("")
                }
                declined("No thanks.")
                sold("Sold!")
            }
        }
    }

    scene(Scenes.Towne.tavern) {
        whenever((realm.hour lt 6) and (realm.hour gt 1)) {
            clearText()
            screen("Tavern Closed.")
            pause(1000)
            stop("tavern closed")
        }

        clearMapWindow()
        showBackdrop(Images.Scene.tavern)

        val priestBonus = localInt(0)
        forEachMember(includeDead = true) { member ->
            whenever(member.isClass(PcClass.Priest)) {
                priestBonus += member.charisma / 10
            }
        }

        whenever(chance(priestBonus + 5)) {
            clearText()
            screen(
                "Just as you are about",
                "to sit down at a,",
                "table, a scruffy",
                "little man, pulls",
                "at your sleeve...",
                "",
            )
            pause(3000)
            screen(
                "- Hey, hey, hey! You!",
                "  yes... you! I have",
                "  and offer that may",
                "  interest you...",
                "",
            )
            pause(3000)
            clearText()
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
        }

        val rumours = localInt(0)

        loop {
            clearText()
            screen {
                line("- Welcome! Have a seat")
                line(townNpcName(NpcJobs.tavernkeeper), ", the keeper")
                line("of the tavern greets")
                line("you...")
                line("")
            }

            menu(
                "(O)rder drink",
                "(S)peak",
                "(T)ell story",
                "(R)umors",
                "(L)eave",
            ) {
                option('L') {
                    clearText()
                    clearMapWindow()
                    breakLoop("leave tavern")
                }

                option('S') {
                    speak(Buildings.inn)
                }

                option('T') {
                    travellerStory()
                }

                option('R') {
                    clearText()
                    whenever(rumours lt 5) {
                        rumours += 1
                    }
                    whenever(rumours gt 4) {
                        screen {
                            lines(
                                "The tavern has suddenly",
                                "become very quiet.",
                                "",
                            )
                        }
                        waitInput("after quiet tavern rumour")
                    } otherwise {
                        choose(random(0..4)) {
                            case(0) {
                                screen {
                                    lines(
                                        "After listening a while",
                                        "you hear an old man say",
                                        "to another:",
                                        "",
                                    )
                                }
                            }
                            case(1) {
                                screen("A drunken bard bellows:", "")
                            }
                            case(2) {
                                screen {
                                    lines(
                                        "A halfling you rescued",
                                        "from a brawl whispers:",
                                        "",
                                    )
                                }
                            }
                            case(3) {
                                screen {
                                    lines(
                                        "The tavern keeper",
                                        "motions to you to",
                                        "come closer, and",
                                        "whispers :",
                                        "",
                                    )
                                }
                            }
                            case(4) {
                                screen("A bargirl tells you:", "")
                            }
                        }
                        pause(600)
                        tellRumour()
                        waitInput("after tavern rumour")
                    }
                }

                option('O') {
                    clearText()
                    screen("Order:", "")
                    menu(
                        "A. Ale       2 au",
                        "B. Mead      3 au",
                        "C. Wine      4 au",
                        "D. Fanta     1 au",
                        "E. Nothing",
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
                                    screen {
                                        lines(
                                            "AAH! (your health",
                                            "returns).",
                                        )
                                    }
                                    pause(1000)
                                    forEachMember(includeDead = true) {
                                        it.hp.addClamped((lit(2) / 2) + 1, it.maxHp)
                                    }
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
                                    screen {
                                        lines(
                                            "AAH! (your health",
                                            "returns).",
                                        )
                                    }
                                    pause(1000)
                                    forEachMember(includeDead = true) {
                                        it.hp.addClamped((lit(3) / 2) + 1, it.maxHp)
                                    }
                                },
                            )
                        }
                        option('C') {
                            pay(lit(4),
                                broke = {
                                    clearText()
                                    screen("No money - No drink!")
                                    pause(1000)
                                },
                                ok = {
                                    clearText()
                                    screen {
                                        lines(
                                            "AAH! (your health",
                                            "returns).",
                                        )
                                    }
                                    pause(1000)
                                    forEachMember(includeDead = true) {
                                        it.hp.addClamped((lit(4) / 2) + 1, it.maxHp)
                                    }
                                },
                            )
                        }
                        option('D') {
                            pay(lit(1),
                                broke = {
                                    clearText()
                                    screen("No money - No drink!")
                                    pause(1000)
                                },
                                ok = {
                                    clearText()
                                    screen {
                                        lines(
                                            "You are the laughing",
                                            "stock of the entire",
                                            "tavern...",
                                        )
                                    }
                                    pause(2000)
                                    forEachMember(includeDead = true) {
                                        it.hp.addClamped((lit(1) / 2) + 1, it.maxHp)
                                    }
                                },
                            )
                        }
                        option('E') {}
                    }
                }
            }
        }
    }

    scene(Scenes.Towne.guild) {
        clearMapWindow()
        showBackdrop(Images.Scene.towneGuild)

        loop {
            clearText()
            statusLine()
            screen(
                "Sorry. The Adventurers'",
                "Guild is permanently",
                "closed for renovation.",
                "",
                "Come back when Rylween",
                "v3 ships in 1994. Or",
                "thereabouts.",
                "",
            )

            menu(
                "(L)eave",
            ) {
                option('L') {
                    clearText()
                    clearMapWindow()
                    breakLoop("leave guild")
                }
            }
        }
    }

    scene(Scenes.Towne.hut) {
        clearMapWindow()
        showBackdrop(Images.Scene.hut)
        clearText()
        screen("You enter the hut.")
        pause(600)

        loop {
            clearText()
            menu("(T)alk", "(L)eave") {
                option('T') {
                    speak(Buildings.none)
                }
                option('L') {
                    clearText()
                    clearMapWindow()
                    renderMap()
                    breakLoop("leave hut")
                }
            }
        }
    }

    // Battle magic: the reusable battle runner owns turn order, menus,
    // casting cost, magic immunity, rewards, and monster AI. These blocks
    // declare this game's spell and combat-item effects.
    spell(Spells.Mage.frost, slot = 1, baseCost = 8, name = "Frost Finger") {
        val caster = currentCaster
        val target = askAliveBattleMonster()
        val damage = localInt(caster.level * random(2..8))

        clearText()
        screen {
            line(partyName(caster), " chills the")
            line("opponent for ", damage)
            line("damage pts.")
        }
        pause(1600)

        target.hp -= damage

        whenever(target.hp lte 0) {
            target.hp set 0
            target.dead set true
            showBattleKilled()
            pause(600)
        }
    }

    spell(Spells.Mage.fire, slot = 2, baseCost = 25, name = "Fire Cloud") {
        val caster = currentCaster

        forEachMonster(includeDead = true) { foe ->
            val damage = localInt(caster.level * random(1..4))

            whenever(!foe.dead) {
                clearText()
                screen {
                    line(partyName(caster), " burns ", monsterName(foe))
                    line("for ", damage, " pts!")
                }
                pause(1000)

                foe.hp -= damage

                whenever(foe.hp lte 0) {
                    foe.hp set 0
                    showBattleKilled()
                    pause(600)
                    foe.dead set true
                }
            }
        }
    }

    spell(Spells.Mage.confusion, slot = 3, baseCost = 17, name = "Confusion") {
        val caster = currentCaster

        clearText()
        screen {
            line(partyName(caster), " utters a word.")
            lines(
                "Your images wavers and",
                "blurs in front of your",
                "enemies",
            )
        }
        pause(2000)
        battle.monsterWeaponSkill /= 2
        battle.monsterConfused set true
    }

    spell(Spells.Mage.death, slot = 4, baseCost = 78, name = "Death Strike") {
        val target = askAliveBattleMonster()

        clearText()
        whenever(random(0..199) lt target.hp) {
            screen {
                lines(
                    "Through INCREDIBLE",
                    "resistance, the enemy",
                    "is still alive, badly",
                    "wounded.",
                )
            }
            pause(2000)
            target.hp set 1
        } otherwise {
            screen {
                lines(
                    "The enemy is wiped",
                    "straigt off the surface",
                    "of the earth!",
                )
            }
            pause(2000)
            target.dead set true
            target.hp set 0
        }
    }

    spell(Spells.Mage.hold, slot = 5, baseCost = 43, name = "Hold Enemies") {
        val caster = currentCaster

        clearText()
        screen {
            line(partyName(caster), " utters a word.")
            lines("The enemies freeze dead", "in their tracks.")
        }
        pause(2000)
        battle.monstersHeld set true
    }

    spell(Spells.Mage.antimagic, slot = 6, baseCost = 28, name = "Anti Magic") {
        val caster = currentCaster

        clearText()
        screen {
            line(partyName(caster), " utters a word.")
            lines(
                "The enemy is now",
                "stopped from using magic.",
                "against you this round.",
            )
        }
        pause(2800)
        battle.antimagic set true
    }

    spell(Spells.Monster.frost, slot = 1, baseCost = 0, name = "Frost Finger") {
        val target = battle.randomAliveMember()
        val damage = localInt((battle.encounterHitPointMax / 5) + random(0..3))

        screen("Frost Finger")
        screen {
            line("on ", partyName(target), "!")
        }
        pause(1000)
        screen {
            line("and does ", damage, " pts")
            line("of damage!")
        }
        pause(1000)
        target.hp -= damage
        whenever(target.hp lte 0) {
            target.hp set 0
            target.dead set true
            screen {
                line("\u001B[31m", partyName(target), " DIES!\u001B[37m")
            }
            pause(1000)
        }
    }

    spell(Spells.Monster.fire, slot = 2, baseCost = 0, name = "Cloud of Fire") {
        screen("Cloud of Fire!")
        pause(1000)
        forEachMember { target ->
            val damage = localInt((battle.encounterHitPointMax / 5) + random(0..4))
            clearText()
            screen {
                line(partyName(target), " is burnt for")
                line(damage, " damage pts!")
            }
            pause(1000)
            target.hp -= damage
            whenever(target.hp lte 0) {
                target.hp set 0
                target.dead set true
                screen {
                    line("\u001B[31m", partyName(target), " DIES!\u001B[37m")
                }
                pause(1000)
            }
        }
    }

    spell(Spells.Monster.hold, slot = 3, baseCost = 0, name = "Hold Creature") {
        screen("Hold Creature!")
        pause(600)
        screen("You realize with horror", "that you cannot move!")
        pause(1500)
        battle.partyHeld set true
    }

    spell(Spells.Monster.confusion, slot = 4, baseCost = 0, name = "Confusion") {
        screen("Confusion!")
        pause(600)
        screen("You are almost blinded", "by a colorful haze.")
        pause(1500)
        battle.partyConfused set true
    }

    // Priest spells are used by the wilderness/dungeon Z command. The reusable
    // runtime owns the "Who?", class gate, spell list, and abort key; these
    // blocks declare the current game's spell effects.
    spell(Spells.Priest.heal, slot = 1, baseCost = 8, name = "Healing") {
        val caster = currentCaster
        val cost = lit(8) + caster.level

        clearText()
        whenever(caster.mp gte cost) {
            caster.mp -= cost
            val maxMembers = realm.partyMemberMax
            val target = ask.member(
                structuredText {
                    line("Who? (1-", maxMembers, ")")
                },
                filter = MemberChoiceFilter.Any,
            )
            val healing = localInt(random(10..19) + (caster.level * 2))

            whenever(target.dead) {
                clearText()
                screen("Dead.")
                pause(600)
                clearText()
            } otherwise {
                target.hp.addClamped(healing, target.maxHp)
                clearText()
                screen {
                    line(partyName(caster), " heals")
                    line(partyName(target))
                    line("for ", healing, " hit points...")
                }
                pause(1500)
                clearText()
            }
        } otherwise {
            screen("Not enough spell pts!")
            pause(1000)
            clearText()
        }
    }

    spell(Spells.Priest.restoration, slot = 2, baseCost = 20, name = "Restoration") {
        val caster = currentCaster
        val cost = lit(20) + caster.level

        clearText()
        whenever(caster.mp gte cost) {
            caster.mp -= cost
            val maxMembers = realm.partyMemberMax
            val target = ask.member(
                structuredText {
                    line("Who? (1-", maxMembers, ")")
                },
                filter = MemberChoiceFilter.Any,
            )

            target.fullHeal()
            clearText()
            screen {
                line(partyName(caster), " heals")
                line(partyName(target))
                line("fully...")
            }
            pause(1500)
            clearText()
        } otherwise {
            screen("Not enough spell pts!")
            pause(1000)
            clearText()
        }
    }

    spell(Spells.Priest.rebirth, slot = 3, baseCost = 90, name = "Rebirth") {
        val caster = currentCaster
        val cost = lit(90) + caster.level

        clearText()
        whenever(caster.mp gte cost) {
            val maxMembers = realm.partyMemberMax
            val target = ask.member(
                structuredText {
                    line("Who? (1-", maxMembers, ")")
                },
                filter = MemberChoiceFilter.Any,
            )

            whenever(target.dead) {
                caster.mp -= cost
                target.dead set false
                party.memberCount += 1
                target.hp set 1
                clearText()
                screen {
                    line(partyName(target), "'s eyelids")
                    lines("flicker, and finally")
                    line(pronoun(target, PronounCase.Subject), " opens ", pronoun(target, PronounCase.Possessive), " eyes!")
                }
                pause(1700)
                clearText()
            } otherwise {
                screen {
                    line(partyName(target), " is alive and")
                    line("healthy.")
                }
                pause(1500)
            }
        } otherwise {
            screen("Not enough spell pts!")
            pause(1000)
            clearText()
        }
    }

    spell(Spells.Priest.banish, slot = 4, baseCost = 85, name = "Banish Evil") {
        val caster = currentCaster
        val cost = lit(85) + caster.level

        clearText()
        whenever(caster.mp gte cost) {
            whenever(context.inDungeon) {
                caster.mp -= cost
                clearText()
                screen {
                    line(partyName(caster), " raises ", pronoun(caster, PronounCase.Possessive), " hands.")
                    line(pronoun(caster, PronounCase.Subject), " gathers divine power")
                    line("between ", pronoun(caster, PronounCase.Possessive), " palms, and")
                    line("suddenly clasps them.")
                    line("together over ", pronoun(caster, PronounCase.Possessive), " head.")
                }
                screen("There is a deafening clap", "of thunder, and you hear", "beings scream in agony", "in the dungeon below...", "")
                waitInput("after banish evil")
                clearText()
                realm.banished set true
            } otherwise {
                screen("There is no particular", "target for this banishing.", "This spell is meant for", "use in dungeons and caves.", "")
                waitInput("after banish no target")
                clearText()
            }
        } otherwise {
            screen("Not enough spell pts!")
            pause(1000)
            clearText()
        }
    }

    spell(Spells.Priest.revelation, slot = 5, baseCost = 40, name = "Revelation") {
        val caster = currentCaster
        val cost = lit(40) + caster.level

        clearText()
        whenever(caster.mp gte cost) {
            whenever(context.inDungeon) {
                caster.mp -= cost
                whenever(chance(59)) {
                    clearText()
                    screen("There is nothing", "special near.", "")
                    waitInput("after revelation nothing near")
                    clearText()
                } otherwise {
                    clearText()
                    screen("Something special is", "near!")
                    pause(1500)
                    clearText()
                    choose(random(0..2)) {
                        case(0) { offerDungeonFind(ItemPools.dungeonWeaponFinds) }
                        case(1) { offerDungeonFind(ItemPools.dungeonPotionGemFinds) }
                        case(2) { offerDungeonFind(ItemPools.magicItems) }
                    }
                }
            } otherwise {
                screen("This spell can be used in", "dungeons only, where there", "is a limited radius for", "the caster to enchant.", "")
                waitInput("after revelation dungeon-only")
                clearText()
            }
        } otherwise {
            screen("Not enough spell pts!")
            pause(1000)
            clearText()
        }
    }

    spell(Spells.Priest.teleport, slot = 6, baseCost = 35, name = "Teleport") {
        priestTeleport(currentCaster)
    }

    spell(Spells.Priest.earthquake, slot = 7, baseCost = 55, name = "Earthquake") {
        val caster = currentCaster
        val cost = lit(55) + caster.level

        clearText()
        whenever(context.inDungeon) {
            screen("The roof would cave", "in on you!", "")
            waitInput("after earthquake dungeon warning")
            clearText()
        } otherwise {
            whenever(caster.mp gte cost) {
                caster.mp -= cost
                screen {
                    line(partyName(caster), " utters")
                    lines("three syllables in an", "archaic tongue...", "")
                }
                pause(2000)
                screen("and the ground cracks", "open!")
                pause(3000)
                clearMapWindow()
                showBackdrop(Images.Backdrop.cave)
                clearText()
                choice.enterOrLeave(
                    textLines("You find a cave!", "", "(E)nter", "(L)eave"),
                    enter = {
                        whenever(party.timeLeft gt 10) {
                            discoverCave(random(5..18), Dungeons.randomCave, lit(10))
                        } otherwise {
                            clearText()
                            screen("You don't have enough", "moves left to explore", "the cave...", "")
                            waitInput("after earthquake no moves")
                            clearMapWindow()
                            clearText()
                        }
                    },
                    leave = {
                        clearText()
                        clearMapWindow()
                        renderMap()
                    },
                )
            } otherwise {
                screen("Not enough spell pts!")
                pause(1000)
                clearText()
            }
        }
    }

    itemUse(Items.redPotion) {
        inventory()
        consume = ItemConsumePolicy.ScriptControlled

        effect {
            val maxMembers = realm.partyMemberMax
            val target = ask.member(
                structuredText {
                    line("Who? (1-", maxMembers, ")")
                },
                filter = MemberChoiceFilter.AliveOnly,
            )

            consumeActiveItem()
            target.hp.addClamped(15, target.maxHp)
            screen {
                line(partyName(target), " recovers")
                line("15 hit points.")
            }
            pause(1000)
        }
    }

    itemUse(Items.bluePotion) {
        inventory()
        consume = ItemConsumePolicy.ScriptControlled

        effect {
            val maxMembers = realm.partyMemberMax
            val target = ask.member(
                structuredText {
                    line("Who? (1-", maxMembers, ")")
                },
                filter = MemberChoiceFilter.AliveOnly,
            )

            consumeActiveItem()
            target.hp -= 15
            screen {
                line(partyName(target), " is poisoned")
                line("for 15 hit points.")
            }
            whenever(target.hp lte 0) {
                target.hp set 0
                target.dead set true
                party.memberCount -= 1
            }
            pause(1000)
        }
    }

    itemUse(Items.amberPotion) {
        inventory()
        consume = ItemConsumePolicy.ScriptControlled

        effect {
            val maxMembers = realm.partyMemberMax
            val target = ask.member(
                structuredText {
                    line("Who? (1-", maxMembers, ")")
                },
                filter = MemberChoiceFilter.SpellcasterOnly,
                accepting = MemberChoiceFilter.AliveOnly,
            )

            consumeActiveItem()
            target.mp.addClamped(15, target.maxMp)
            screen {
                line(partyName(target), " recovers")
                line("15 spell points.")
            }
            pause(1000)
        }
    }

    itemUse(Items.frostWand) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            val caster = currentCaster
            val target = battle.firstAliveMonster()
            val damage = localInt(random(5..11) + caster.level)

            clearText()
            screen {
                line(partyName(caster), " chills ", monsterName(target))
                line("for ", damage, " damage pts.")
            }
            pause(1000)

            target.hp -= damage

            whenever(target.hp lte 0) {
                target.hp set 0
                showBattleKilled()
                pause(600)
                target.dead set true
            }
        }
    }

    itemUse(Items.fireWand) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            val caster = currentCaster
            clearText()

            forEachMonster(includeDead = true) { foe ->
                val damage = localInt(caster.level * random(3..8))

                whenever(!foe.dead) {
                    clearText()
                    screen {
                        line(partyName(caster), " burns ", monsterName(foe))
                        line("for ", damage, " pts!")
                    }
                    pause(1000)

                    foe.hp -= damage

                    whenever(foe.hp lte 0) {
                        foe.hp set 0
                        showBattleKilled()
                        pause(600)
                        foe.dead set true
                    }
                }
            }
        }
    }

    itemUse(Items.gemOfConfusion) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            val caster = currentCaster

            clearText()
            screen {
                line(partyName(caster), " throws the")
                lines(
                    "at the enemy. It",
                    "breaks before them in",
                    "a rain of sparks.",
                    "They bump into each",
                    "other as they try to",
                    "see you.",
                    "",
                )
            }
            waitInput("after gem of confusion")
            clearText()
            battle.monsterWeaponSkill /= 2
        }
    }

    itemUse(Items.gemOfBlindness) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            val caster = currentCaster

            clearText()
            screen {
                line(partyName(caster), " throws the")
                lines(
                    "gem at the enemy.",
                    "It breaks, releasing",
                    "a brilliant cloud",
                    "of light which completely",
                    "surrounds them. As the",
                    "light fades, your enemies",
                    "are blind.",
                    "",
                )
            }
            waitInput("after gem of blindness")
            clearText()
            battle.monsterWeaponSkill set 5
        }
    }

    itemUse(Items.drumsOfDeath) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            val caster = currentCaster
            val target = battle.firstAliveMonster()

            clearText()
            screen {
                line(partyName(caster), " summons death")
                line("to take a ", monsterName(target))
                line("away...")
            }
            pause(2000)
            showBattleKilled()
            pause(600)
            target.dead set true
            target.hp set 0
            clearText()
        }
    }

    itemUse(Items.staffOfHealing) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            val maxMembers = realm.partyMemberMax
            val target = ask.member(
                structuredText {
                    line("On Whom? (1-", maxMembers, ")")
                },
                filter = MemberChoiceFilter.Any,
            )

            clearText()
            whenever(!target.dead) {
                target.fullHeal()
            }
            screen {
                line(partyName(target), " is fully")
                line("healed.")
            }
            pause(1000)
            clearText()
        }
    }

    itemUse(Items.blessingScroll) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            clearText()
            screen {
                lines(
                    "The party feels their",
                    "spirits grow. You are",
                    "all stronger than",
                    "before.",
                    "",
                )
            }
            waitInput("after blessing scroll")
            clearText()

            forEachMember(includeDead = true) { member ->
                member.weaponSkill += 3
                member.maxHp += 3
                member.charisma += 3
                member.wisdom += 3
            }
        }
    }

    itemUse(Items.staffOfCuring) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            clearText()
            screen("The party is fully", "healed!", "")
            waitInput("after staff of curing")
            clearText()

            forEachMember(includeDead = true) { member ->
                whenever(!member.dead) {
                    member.fullHeal()
                }
            }
        }
    }

    itemUse(Items.gemOfResurrection) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.ScriptControlled

        effect {
            val maxMembers = realm.partyMemberMax
            val target = ask.member(
                structuredText {
                    line("On Whom? (1-", maxMembers, ")")
                },
                filter = MemberChoiceFilter.DeadOnly,
                accepting = MemberChoiceFilter.Any,
            )

            clearText()
            whenever(target.dead) {
                screen {
                    line(partyName(target), " opens ", pronoun(target, PronounCase.Possessive), " eyes!")
                }
                pause(1000)
                target.dead set false
                target.hp set 1
                consumeActiveItem()
            } otherwise {
                screen {
                    line(partyName(target), " is not dead.")
                }
                pause(1000)
                battle.actionRetry set true
            }
        }
    }

    itemUse(Items.kaelsAxe) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            val caster = currentCaster

            clearText()
            screen {
                line(partyName(caster), " throws Kael's")
                lines("axe at the enemy!", "")
            }
            pause(1500)
            screen {
                lines(
                    "It wounds them severely",
                    "as it spins around,",
                    "wielded by hands you",
                    "cannot see!",
                )
            }
            pause(4000)
            clearText()
            screen {
                lines(
                    "With a raging battle",
                    "cry, you attack the",
                    "mortally wounded foes!",
                )
            }
            pause(3000)

            forEachMonster { foe ->
                foe.hp set random(1..3)
            }
        }
    }

    itemUse(Items.holyHandgrenade) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            val caster = currentCaster

            clearText()
            screen {
                line(partyName(caster), " pulls the")
                lines(
                    "holy pin, and lobbeth",
                    "the holy handgrenade",
                    "at the foes...",
                    "",
                )
            }
            pause(3000)
            screen("KA-BLAM!", "")
            pause(1500)
            clearText()

            forEachMonster { foe ->
                foe.hp /= 4
                foe.hp += 1
            }
        }
    }

    itemUse(Items.fluteOfMadness) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            val caster = currentCaster

            clearText()
            screen {
                line(partyName(caster), " plays the")
                lines(
                    "Flute of Madness.",
                    "It produces a shrill,",
                    "fragile sound, and breaks.",
                    "Nothing happens.",
                    "",
                )
            }
            waitInput("after flute of madness")
            clearText()
        }
    }

    // 4. Mission action scene: flag states are fluent typed markers, not strings.
    scene(Scenes.Missions.erethorEntry) {
        dungeonEntrance(Dungeons.erethorTemple, levels = 12) {
            line("Here is the temple of")
            line("the chaos lord Erethor.")
            line("A winding staircase")
            line("spirals down between")
            line("broken pillars.")
            line("")
            line("(E)nter")
            line("(L)eave")
        }
    }

    scene(Scenes.Missions.husbandCave) {
        dungeonEntrance(Dungeons.ogreCave, levels = 7) {
            line("There is a cave entrance")
            line("in the foothills to the")
            line("east. Is this the place")
            line(character(Roles.peasantHusband).name, " talked about?")
            line("")
            line("(E)nter")
            line("(L)eave")
        }
    }

    scene(Scenes.Missions.bandits) {
        clearText()
        readBanditsApproach()
        encounter(Monsters.bandit, mandatory = true)
        renderMap()
        clearText()
        readBanditsCargoRecovered()
        mark(Story.garth.bandits.recovered)
    }

    scene(Scenes.Missions.trollTooth) {
        encounter(Monsters.troll, mandatory = true)
        renderMap()
        clearText()
        screen(
            "You break off a troll",
            "tooth to give the hermit.",
            "After wiping the slime of",
            "it, you put it in your",
            "backpack.",
            "",
        )
        waitInput("after troll tooth")
        clearText()
        mark(Story.hermit.trollTooth.extracted)
    }

    scene(Scenes.Missions.hermitDungeon) {
        dungeonEntrance(Dungeons.fireShieldCave, levels = 20, leaveClearsText = false) {
            line("You have found a dungeon.")
            line("The entrance has been")
            line("cut out of some huge")
            line("animal's skull.")
            line("")
            line("(E)nter")
            line("(L)eave")
        }
    }

    scene(Scenes.Missions.thunTemple) {
        clearText()
        readScreen {
            line("You stand before the")
            line("ancient temple of Thun.")
            line("Around you are the ruins")
            line("of the once proud city")
            line("of Thun...")
            line("")
        }
        readScreen {
            line("You see an altar")
            line("with five sockets before")
            line("you. Each is roughly")
            line("gem-sized. An inscription")
            line("says: 'Insert the gems")
            line("and your path shall open'")
            line("")
        }
        menu(
            structuredText {
                line("Do you want to put")
                line("five gems in the sockets?")
                line("")
                line("(Y)es")
                line("(L)eave")
            },
        ) {
            defaultOption('Y') {
                whenever(party.inventory.count(ItemPools.thunAltarGems) lt 5) {
                    clearText()
                    readScreen {
                        line("You don't have five")
                        line("different gems.")
                        line("")
                    }
                } otherwise {
                    party.inventory.consume(ItemPools.thunAltarGems, 5)
                    clearText()
                    readScreen {
                        line("With a resounding 'CRUNG'")
                        line("the altar slides away")
                        line("to reveal a dark pit.")
                        line("You enter...")
                        line("")
                    }
                    enterDungeon(Dungeons.thunTemple, levels = 18)
                    clearMapWindow()
                    renderMap()
                }
            }
            option('L') {
                clearText()
            }
        }
    }

    scene(Scenes.Missions.koboldVillage) {
        clearText()
        menu(
            structuredText {
                line("The kobold village lies")
                line("ahead. You can hear the")
                line("noise from some kind of")
                line("party even at a distance")
                line("of 200 yards.")
                line("")
                line("(A)ttack now")
                line("(L)eave")
            },
        ) {
            option('A') {
                clearText()
                screen(
                    "You run screaming into",
                    "a large celebration of",
                    "some sort. There are",
                    "about forty kobolds here.",
                    "At first, they are very",
                    "surprised, and you manage",
                    "to slay many, before they",
                    "attack...",
                    "",
                )
                waitInput()
                clearText()
                encounter(Monsters.koboldHorde, mandatory = true)
                clearText()
                screen(
                    "Piles of dead kobolds are",
                    "lying all around. Still,",
                    "they keep coming!",
                    "",
                )
                waitInput()
                encounter(Monsters.koboldHorde, mandatory = true)
                clearText()
                screen(
                    "You have annihilated most",
                    "of the village. You pause",
                    "to wipe the blood of your",
                    "weapons, when suddenly,",
                    "some ORCS rush out to",
                    "attack you from nowhere.",
                    "",
                )
                waitInput()
                encounter(Monsters.orc, mandatory = true)
                renderMap()
                clearText()
                readScreen(
                    "The entire village is",
                    "wiped out. You snatch",
                    "an ugly pendant from the",
                    "head of the kobold chief",
                    "to prove the bloody deed.",
                    "",
                )
                mark(Story.masterCrown.task1Done)
            }
            option('L') {}
        }
    }

    scene(Scenes.Missions.skullweed) {
        clearText()
        screen(
            "On the ground before you",
            "grows the skullweed plant.",
            "You break off a few plants",
            "and put in your backpack.",
            "",
        )
        waitInput("after skullweed pickup")
        clearText()
        mark(Story.masterCrown.task2Done)
    }

    scene(Scenes.Missions.borinMeet) {
        clearText()
        readScreen(
            "You arrive at a village",
            "which looks typically",
            "dwarfish. As you approach",
            "the village square, an",
            "old dwarf who introduces",
            "himself as Borin greets",
            "you.",
            "",
        )
        readScreen(
            "You explain why you are",
            "here, and he frowns.",
            "- Well, he says, you",
            "actually COULD have a",
            "stone of that type",
            "if you do our tribe a",
            "favour...",
            "",
        )
        readScreen(
            "- Years have passed, and",
            "much of our knowledge",
            "about the making of",
            "arms is gone. We haven't",
            "practised it, generation",
            "after generation, and the",
            "arms we produce nowadays",
            "are not as unbeatable as",
            "they once were...",
            "",
        )
        readScreen(
            "- When our king Nolrim",
            "died, nine hundred years",
            "ago we buried him with his",
            "trusty dagger Reebar. If",
            "we had this to study",
            "we could once again become",
            "the greatest smiths that",
            "there are...",
            "",
        )
        readScreen(
            "- If you go to Nolrim's",
            "crypt, breach the traps my",
            "ancestors set to protect",
            "him against looters, and",
            "return Reebar, I will not",
            "only give you the stone,",
            "but also lots of gold,and",
            "some dwarven treasures.",
            "",
        )
        readScreen {
            line("- Nolrim's crypt is at")
            line(location(Locations.nolrimCrypt).coordinates, ". Be careful, and")
            line("fare the well.")
            line("")
        }
        journal {
            line("Nolrim's Crypt is at ", location(Locations.nolrimCrypt).coordinates, ".")
        }
        mark(Story.masterCrown.borinMet)
    }

    scene(Scenes.Missions.nolrimCrypt) {
        dungeonEntrance(Dungeons.reebarTomb, levels = 20) {
            line("Before you is the crypt")
            line("of Nolrim. After levering")
            line("a stone slab away, a dark")
            line("forboding passage leads")
            line("down to unknown depths.")
            line("")
            line("(E)nter the Crypt")
            line("(L)eave")
        }
    }

    scene(Scenes.Missions.masterCrown) {
        dungeonEntrance(Dungeons.masterCrownDungeon, levels = 10) {
            line("Before you is the dungeon")
            line("where the ancients hid the")
            line("Master Crown. Your long")
            line("trek has come to an end.")
            line("")
            line("(E)nter")
            line("(L)eave")
        }
    }

    scene(Scenes.Missions.ogreCave) {
        clearText()
        choice.enterOrLeave(
            structuredText {
                line("You find a the cave the")
                line("half-orc told you about.")
                line("As you get closer, you")
                line("become aware of a hideous")
                line("smell, emnating from the")
                line("cavern depths...")
                line("")
                line("(E)nter")
                line("(L)eave")
            },
            enter = {
                clearText()
                screen(
                    "You enter the cave.",
                    "piles of treasure are",
                    "thrown all around.",
                    "You note with terror",
                    "that some OGRES sit",
                    "among the gold and",
                    "and jewels! They spot",
                    "you and run screaming",
                    "to attack!",
                    "",
                )
                waitInput()
                encounter(Monsters.ogre, mandatory = true)
                renderMap()
                clearText()
                screen(
                    "You defeat the ogres and",
                    "start to search for the",
                    "fabled Foot of Jazzy.",
                    "Just as you begin to",
                    "wonder if the half-orc",
                    "lied to you, you hear a",
                    "sound and quickly turn",
                    "around. An OGRE LORD has",
                    "quietly entered the cave!",
                    "",
                )
                waitInput()
                encounter(Monsters.ogreLord, mandatory = true)
                renderMap()
                clearText()
                readScreen(
                    "You defeat the Ogre Lord.",
                    "You take all the gold",
                    "you find, 5645 gold coins!",
                    "You also find a Mithril",
                    "Mail some unfortunate",
                    "warrior has left here.",
                    "",
                )
                reward {
                    gold(5645)
                    items(Items.mithrilMail)
                }
                val noteFinder = party.member(2)
                readScreen {
                    line("...There is however no")
                    line("trace of the Foot. Just")
                    line("as you are about to give")
                    line("up, ", partyName(noteFinder), " finds a note!")
                    line("It is written in shaky")
                    line("hand with a strange")
                    line("brown ink...")
                    line("")
                }
                readScreen(
                    "'THEY SHALL NOT HAVE THE",
                    "FOOT! I'm dying and I'll",
                    "soon be dead, but I will",
                    "use my last strength to",
                    "teleport it away from",
                    "here to the eastern peaks,",
                    "Jazzy's regions.'",
                    "         - Kastagir.",
                    "",
                )
                readScreen(
                    "You leave the cave.",
                    "",
                )
                mark(Story.jazzy.ogre.ogresCleared)
            },
            leave = {
                clearText()
            },
        )
    }

    scene(Scenes.Missions.jazzyFoot) {
        clearText()
        screen(
            "Something about this whole",
            "area is weird. The air",
            "seems to be loaded with",
            "electricity. Suddenly you",
            "spot an object on the",
            "ground. It is a delicate",
            "ivory statue in the shape",
            "of a human foot. THIS IS",
            "THE FOOT OF JAZZY!",
            "",
        )
        waitInput("after finding the foot")
        clearText()
        screen(
            "Just as you touch it,",
            "the sky splits open and",
            "at the wailing of hundreds",
            "of tortured souls, Jazzy's",
            "minions attack you, for",
            "the heresy they find you",
            "guilty of...",
            "",
        )
        waitInput("before Jazzy minions")
        encounter(Monsters.wingedThing, mandatory = true)
        renderMap()
        clearText()
        screen(
            "Quiet settles again, as",
            "you destroy the foul",
            "creatures, one by one.",
            "You reach for the foot",
            "again, when suddenly,",
            "something EVEN MORE",
            "horrible happens...",
            "",
        )
        waitInput("after Jazzy minions")
        clearText()
        screen(
            "A twenty foot tall",
            "being materializes",
            "in front of you! You will",
            "have to fight Jazzy,",
            "himself!",
            "",
        )
        waitInput("before Jazzy")
        encounter(Monsters.jazzy, mandatory = true)
        renderMap()
        clearText()
        screen(
            "Today, you have killed a",
            "god. You watch silently as",
            "the huge body starts to",
            "melt. It fades away into",
            "Nothingness. This time,",
            "when you pick up the foot,",
            "nothing happens.",
            "",
        )
        waitInput("after Jazzy defeat")
        clearText()
        mark(Story.jazzy.ogre.jazzyDefeated)
        achieve(Milestones.footOfJazzy)
    }

    scene(Scenes.Missions.silverKey) {
        dungeonEntrance(Dungeons.silverSanctum, levels = 20) {
            line("Here lies the dungeon")
            line("of the Silver Key.")
            line("It is merely a pit in the")
            line("ground. You _hope_ that")
            line("that despicable thief")
            line("didn't trick you.")
            line("")
            line("(E)nter dungeon")
            line("(L)eave")
        }
    }

    scene(Scenes.Missions.hobgoblinCaravan) {
        clearText()
        readScreen(
            "Just before you is a",
            "caravan of hobgoblins on",
            "white, well-bred horses.",
            "",
        )
        readScreen(
            "It is unusual that these",
            "foul creatures have such",
            "beautiful animals. Hoping",
            "that they can give you a",
            "clue about the key, you",
            "emit a loud battle cry",
            "and attack!",
            "",
        )
        encounter(Monsters.ridingHobgoblin, mandatory = true)
        renderMap()
        clearText()
        readScreen {
            line("Before you kill the last")
            line("hobgoblin you ask him")
            line("about the key. The foul")
            line("creature blurts:")
            line("- Boss has it! In ze cave!")
            line("at home, yess, ", location(Locations.hobgoblinCave).coordinates, "...")
            line("pleeez sirs, don't kill")
            line("me...")
            line("")
        }
        readScreen(
            "(at this point you sever",
            " his carotid artery).",
            "",
        )
        journal {
            line("The hobgoblin cave is at ", location(Locations.hobgoblinCave).coordinates, ".")
        }
        mark(Story.silverKey.caveLocated)
    }

    scene(Scenes.Missions.hobgoblinCave) {
        dungeonEntrance(Dungeons.hobgoblinStronghold, levels = 22) {
            line("This is a hobgoblin cave.")
            line("Smoke drifts out of a pit")
            line("in the ground. A staircase")
            line("spirals around the inside")
            line("of the pit. When you see")
            line("the impaled human skulls")
            line("decorating the entrance,")
            line("you feel uneasy.")
            line("")
            line("(E)nter pit")
            line("(L)eave")
        }
    }

    // 7. Script reward: semantic items, typed placement policy, no numeric ids.
    scene(Scenes.Missions.borinGift) {
        clearText()
        readScreen(
            "Borin greets you. He",
            "can hardly believe his",
            "eyes when you show him",
            "the dagger. He motions",
            "to you to follow him,",
            "and guides you to his",
            "hut.",
            "",
        )
        readScreen(
            "Smiling, he offers you",
            "3578 gold coins and 3",
            "gems. You greedily accept",
            "the gift. He also hands",
            "you a purple dwarf stone.",
            "",
        )
        reward {
            gold(3578)
            items(Items.redGem, Items.blueGem, Items.greenGem)
        }
        mark(Story.masterCrown.borinPaid)
    }

    // 8. NPC dialogue: one-off text and rewards stay inline in the branch.
    dialogue {
        npc(Npcs.garth) {
            whenState(Story.garth.bandits.none) {
                say(
                    "In fact, I have a mission",
                    "for you. It will bring",
                    "you much gold!",
                    "",
                )
                say(
                    "I was robbed by bandits",
                    "just to the east of here.",
                    "If you find these bandits,",
                    "kill them, and return",
                    "my cargo here, I'll pay",
                    "you 2000 gold and a ruby",
                    "my grandfather gave me.",
                    "",
                )
                mark(Story.garth.bandits.accepted)
            }

            whenState(Story.garth.bandits.recovered) {
                say(
                    "The merchant is GRATEFUL!",
                    "He pays you 2000 gold and",
                    "hands you a red gem.",
                    "",
                )
                reward {
                    gold(2000)
                    items(Items.redGem)
                    promptDrop(
                        cantCarry = "You can't carry the gem.",
                        pickedUp = "You pick up the gem.",
                    )
                }
                mark(Story.garth.bandits.closed)
            }
        }

        npc(Npcs.nrimTo) {
            whenState(Story.ranger.letter.none) {
                say(
                    "- Greetings, adventurers.",
                    "I have a mission for you.",
                    "He hands you a letter.",
                    "",
                )
                say {
                    line("- If you deliver this")
                    line("to my brother, ", character(Roles.rangerBrother).name, ",")
                    line("who lives ", character(Roles.rangerBrother).location, ",")
                    line("he will give you")
                    line("a great weapon!")
                    line("")
                }
                journal {
                    line(character(Roles.rangerBrother).name, ", the Ranger lives ", character(Roles.rangerBrother).location)
                }
                mark(Story.ranger.letter.accepted)
            }
        }

        npc(Npcs.areth) {
            whenState(Story.fighter.priestBook.none) {
                say(
                    "- Greetings. Once upon",
                    "a time, I was a mighty",
                    "fighter. Before I retired",
                    "I managed to slay over",
                    "two hundred orcs. All",
                    "of this, because of my",
                    "Dancing Blade. I have",
                    "no use for this weapon",
                    "",
                )
                say {
                    line("anymore, and I will give")
                    line("it to you, if you do me a")
                    line("small favour. A priest")
                    line("named ", character(Roles.priestWithBook).name, " has got")
                    line("one of my books. We are")
                    line("old friends, and I lent")
                    line("it to him. However, I")
                    line("haven't heard much from")
                    line("him lately.")
                    line("")
                }
                mark(Story.fighter.priestBook.priestTold)
            }

            whenState(Story.fighter.priestBook.wizardRobbed) {
                say(
                    "- Oh! Thank you!!! He",
                    "removes a floor board",
                    "and gives you a WONDERFUL",
                    "blade. It sparkles in",
                    "the light!",
                    "",
                )
                mark(Story.fighter.priestBook.fighterGrateful)
                reward {
                    items(Items.dancingSword)
                    promptDrop(
                        cantCarry = "You can't carry it!!!.",
                        pickedUp = "You take the blade.",
                    )
                }
            }
        }

        npc(Npcs.nevaak) {
            whenState(Story.fighter.priestBook.priestVisited) {
                say(
                    "You persuade him to",
                    "give you the book.",
                    "After showing him your",
                    "sharp weapons, he",
                    "chickens out and pulls",
                    "it out of his robe.",
                    "He throws it at you and",
                    "flees...",
                    "",
                )
                mark(Story.fighter.priestBook.wizardRobbed)
            }
        }

        npc(Npcs.gorm) {
            whenState(Story.fighter.priestBook.priestTold) {
                say {
                    line("When you mention the")
                    line("book, he nods and looks")
                    line("a bit embarrased.")
                    line("- Yes, I know. I happened")
                    line("to lose it. A wizard")
                    line("called ", character(Roles.wizardRobbed).name, " stole it")
                    line("from my bedroom a week")
                    line("ago...")
                    line("")
                }
                say(
                    "He quickly fled, but I",
                    "know it's him, because",
                    "he made inquieries about",
                    "it, and tried to steal",
                    "it from me when I was",
                    "having dinner.",
                    "",
                )
                say {
                    line("I don't know his")
                    line("whereabouts, but please")
                    line("find the book and")
                    line("take it back to ", character(Roles.fighterIntro).name)
                    line("if you can.")
                    line("")
                }
                mark(Story.fighter.priestBook.priestVisited)
            }
        }

        npc(Npcs.bernak) {
            whenState(Story.silverKey.none) {
                say("- There is an item of", "great power, he hisses.", "")
                say(
                    "- It is called the",
                    "SILVER KEY. It has taken",
                    "me many years of research",
                    "to find its location, and",
                    "this only to be stopped",
                    "by a guardian telling me",
                    "that 'only those of pure",
                    "heart can enter'.",
                    "",
                )
                say("- I fled for my life as", "horned creatures chased", "me through the woods.", "")
                say(
                    "I have an offer to make",
                    "thee. I will sell you",
                    "a map to the location of",
                    "the SILVER KEY for 50000",
                    "gold coins. The price is",
                    "resonable. 10000 gold for",
                    "each of those fruitless",
                    "years.",
                    "",
                )
                menu("(B)uy it", "(N)o thanks") {
                    option('B') {
                        pay(
                            amount = lit(50000),
                            broke = {
                                say("- Sorry! 50000! No more", "no less. Come back when", "you have the gold.", "")
                            },
                            ok = {
                                say {
                                    line("He quietly slips you the")
                                    line("precious map. It")
                                    line("tells you that the Pit")
                                    line("of the Key is at")
                                    line(location(Locations.silverKeyPit).coordinates, "!")
                                    line("")
                                    line("")
                                }
                                journal {
                                    line("The Pit of the Silver Key is at ", location(Locations.silverKeyPit).coordinates, ".")
                                }
                                mark(Story.silverKey.mapBought)
                            },
                        )
                    }
                    option('N') {}
                    default {}
                }
            }
        }

        npc(Npcs.tybalt) {
            whenState(Story.erethor.lute.none) {
                say(
                    "- Rumor has it that",
                    "Erethor's Lute, a",
                    "musical instrument with",
                    "magical properties",
                    "exists somewhere in",
                    "the realm.",
                    "",
                )
                say("- Erethor is a mighty", "demon...", "")
                say(
                    "- I am a very poor man",
                    "myself, but I know of",
                    "the Cloak of Gods,",
                    "the most powerful of",
                    "all armors. I will",
                    "",
                )
                say(
                    "trade this information",
                    "if you find me Erethor's",
                    "Lute. One of Erethor's",
                    "temples, where mad",
                    "priests practise",
                    "blasphemous magic is",
                    "located at 6x20, near",
                    "the western peaks.",
                    "",
                )
                say(
                    "- This may be a dangerous",
                    "mission for such a",
                    "small reward, but the",
                    "Cloak will make it's",
                    "bearer almost",
                    "invulnerable.",
                    "",
                )
                journal("Erethor's temple is situated at 6x20.")
                mark(Story.erethor.lute.rumourTold)
            }

            whenState(Story.erethor.lute.luteTaken) {
                say("You hand him the", "golden lute...", "")
                say(
                    "- I can't believe you",
                    "actually made it!!!",
                    "- Now for my part of",
                    "the deal. Listen closely,",
                    "for I shall say this only",
                    "once.",
                    "",
                )
                say {
                    line("- Take this note here")
                    line("and seek out my friend")
                    line(character(Roles.minstrelCloak).name, ". He is also a")
                    line("minstrel.")
                    line(character(Roles.minstrelCloak).name, " lives ", character(Roles.minstrelCloak).location, ".")
                    line("")
                }
                journal {
                    line(character(Roles.minstrelCloak).name, " lives ", character(Roles.minstrelCloak).location, ".")
                }
                say(
                    "- We used to travel",
                    "together and he found",
                    "the Cloak in a long dead",
                    "hero's tomb. Now that",
                    "we have the Lute, the",
                    "Cloak is yours. Just",
                    "deliver this explaining",
                    "note!",
                    "",
                )
                say("He hands you a piece of", "paper and bids you", "farewell.", "")
                mark(Story.erethor.lute.noteReceived)
            }
        }

        npc(Npcs.harry) {
            whenState(Story.bracelets.thun.none) {
                say(
                    "- I have a map of the",
                    "road to the temple of",
                    "Thun! The legend says",
                    "it keeps the Bracelets",
                    "of Thun, which is one of",
                    "the objects that can",
                    "destroy baron Ydarg!",
                    "",
                )
                say("- I will sell it to you", "for 10000 gold pieces!", "")
                menu("(B)uy it", "(N)o thanks") {
                    option('B') {
                        pay(
                            amount = lit(10000),
                            broke = {
                                say("- Sorry! 10000! No more", "no less. Come back when", "you have the gold.", "")
                            },
                            ok = {
                                say {
                                    line("- Ok, here. He slowly")
                                    line("hands you an old map. It")
                                    line("tells you that the temple")
                                    line("of Thun is situated at")
                                    line(location(Locations.thunTemple).coordinates, "!")
                                    line("")
                                    line("")
                                }
                                journal {
                                    line("The Temple of Thun is at ", location(Locations.thunTemple).coordinates, ".")
                                }
                                mark(Story.bracelets.thun.mapBought)
                            },
                        )
                    }
                    option('N') {}
                    default {}
                }
            }
        }

        npc(Npcs.marraga) {
            whenState(Story.ranger.letter.accepted) {
                say(
                    "- OH! A message from my",
                    "brother! This is indeed",
                    "great news. I will give",
                    "you this sword for",
                    "delivering the letter!",
                    "",
                )
                say("He produces a sparkling", "sword and wishes you a", "happy journey.", "")
                reward {
                    items(Items.magicSword)
                    promptDrop(
                        cantCarry = "You can't carry it!!!.",
                        pickedUp = "You take the sword.",
                    )
                }
                mark(Story.ranger.letter.delivered)
            }
        }

        npc(Npcs.broth) {
            whenState(Story.nymph.seeker.none) {
                say {
                    line("I heard that a man called")
                    line(character(Roles.wizardThreeTasks).name, " knows of the")
                    line("Crown. I have roamed the")
                    line("land in search for him,")
                    line("since I want this item")
                    line("to be found. Here! Take")
                    line("this ring and show it")
                    line("to him. You are better fit")
                    line("for this mission than me.")
                    line("")
                }
                say {
                    line(character(Roles.wizardThreeTasks).name, " lives")
                    line(character(Roles.wizardThreeTasks).location)
                    line("")
                }
                journal {
                    line(character(Roles.wizardThreeTasks).name, " lives ", character(Roles.wizardThreeTasks).location, ".")
                }
                mark(Story.nymph.seeker.told)
            }
        }

        npc(Npcs.gotharp) {
            whenState(Story.silverKey.noteFound) {
                mark(Story.silverKey.thiefSqueezed)
                say(
                    "You push him up against",
                    "a wall and start asking",
                    "him questions. After",
                    "some 'mild' persuation",
                    "he tells you that he",
                    "sold the Key to a",
                    "travelling blacksmith",
                    "who was interested in",
                    "its metalurgic structure.",
                    "",
                )
                say {
                    line("After breaking a few")
                    line("of his teeth, he also")
                    line("willinly reveals that")
                    line("the smith went by the")
                    line("name of ", character(Roles.silverKeySmith).name, "...")
                    line("")
                }
                say("You let go of your grip", "of his collar, and he", "falls to the floor.", "")
            }
        }

        npc(Npcs.ron) {
            whenState(Story.peasant.wife.none) {
                say(
                    "- Oh, I'm so depressed.",
                    "Orcs have kidnapped my",
                    "wife, Tammy, and demand",
                    "a ransom for her. I will",
                    "give you my entire fortune",
                    "if you retrieve her.",
                    "",
                )
                say(
                    "About 3000 gold coins,",
                    "a fine armor, and",
                    "some strange items my",
                    "father left me, will be",
                    "yours if you rescue her.",
                    "",
                )
                say("The orcs have a cave", "at 42x23 in Lonewood.", "")
                journal("The orc cavern is at 42x23.")
                mark(Story.peasant.wife.accepted)
            }

            whenState(Story.peasant.wife.rescued) {
                say(
                    "When he sees Tammy enter",
                    "the room, he cries tears",
                    "of happiness and kisses",
                    "her. When they have had",
                    "this happy reunion, he",
                    "motions to you to follow.",
                    "",
                )
                say(
                    "He hands you 3000 gold,",
                    "an Adamantium Armor of",
                    "advanced craftmanship",
                    "and some other assorted",
                    "objects. (An amber potion",
                    "a holy handgrenade, a",
                    "fire wand and a crystal.",
                    "",
                )
                mark(Story.peasant.wife.paid)
                reward {
                    gold(3000)
                    items(Items.admtMail, Items.holyHandgrenade, Items.amberPotion, Items.fireWand, Items.crystalBowl)
                    promptDrop(cantCarry = "You can't carry all the")
                }
            }
        }

        npc(Npcs.kaal) {
            whenState(Story.erethor.lute.noteReceived) {
                say {
                    line("After reading the note")
                    line(character(Roles.minstrelCloak).name, " cries tears")
                    line("of happiness. He")
                    line("shakes all your hands")
                    line("and produces a purple")
                    line("cloak.")
                    line("")
                }
                reward {
                    items(Items.cloakOfGods)
                    promptDrop(
                        cantCarry = "You can't carry it!",
                        pickedUp = "You take the cloak.",
                    )
                }
                say {
                    line("He bids you farewell")
                    line("and tells you that")
                    line("he'll travel to")
                    line(character(Roles.erethorLuteBard).name, " the very")
                    line("next day...")
                    line("")
                }
                mark(Story.erethor.lute.cloakReceived)
            }
        }

        npc(Npcs.leander) {
            whenState(Story.jazzy.ogre.none) {
                say(
                    "- ME HEAR YOU LOOK",
                    "FOR POWER ITEMS!",
                    "",
                    "- ME VERY POOR! SELL",
                    "YOU INFORMATION ABOUT",
                    "FOOT OF JAZZY, YES?",
                    "",
                )
                say(
                    "- LET US TRADE! OFFER",
                    "ITEM AND I'LL TELL YA!",
                    "",
                    "(You offer him something).",
                    "",
                )
                val slot = ask.inventorySlot(inlineText("Offer what?"), filter = InventorySlotChoiceFilter.NonEmpty)
                whenever(party.inventory.itemCost(slot) lt 35) {
                    clearText()
                    say("- NAH, THINK NOT!.")
                    pause(1000)
                } otherwise {
                    clearText()
                    say {
                        line("- YEEES! NICE NIICE!")
                        line("GO TO CAVE AT ", location(Locations.beastCave).coordinates, "!")
                        line("BEAST HAS THE FOOT.")
                        line("")
                    }
                    journal {
                        line("The 'Beast Cave' is at ", location(Locations.beastCave).coordinates, ".")
                    }
                    mark(Story.jazzy.ogre.accepted)
                    waitInput("after Jazzy ogre trade")
                    clearText()
                    party.inventory.clear(slot)
                }
            }
        }

        npc(Npcs.ithienn) {
            whenState(Story.nymph.seeker.told) {
                whenever(Story.masterCrown.none) {
                    say(
                        "- Ah, a seeker! Yes, I",
                        "know about the Master",
                        "Crown, but since I'm",
                        "neither good nor evil,",
                        "I have no interest in it.",
                        "I will give you the",
                        "information if you do",
                        "three tasks for me.",
                        "",
                    )
                    say("(Since there is nothing", "else you can do,you agree", "to complete the three", "tasks).", "")
                    say {
                        line("- Firstly, I want you to")
                        line("go to the kobold lair at")
                        line(location(Locations.koboldVillage).coordinates, ", and kill all life")
                        line("there. It is close to my")
                        line("home village, and the foul")
                        line("creatures have many times")
                        line("attacked us. Farewell.")
                        line("")
                    }
                    journal {
                        line("The kobold lair is at ", location(Locations.koboldVillage).coordinates, ".")
                    }
                    mark(Story.nymph.seeker.wizard3tActive)
                    mark(Story.masterCrown.task1Assigned)
                }
            }

            whenState(Story.masterCrown.task1Done) {
                say("You hand him the pendant.", "He stares at you, almost", "admiring.", "- WELL DONE! I will now", "grant you the second", "quest.", "")
                say(
                    "- Being a magician, I'm",
                    "always in search for",
                    "powerful spell reagents.",
                    "There is one plant I need",
                    "but can't find. It is",
                    "called Skullweed, and is",
                    "purple with small green",
                    "twigs. Find this plant",
                    "plant for me!",
                    "",
                )
                mark(Story.masterCrown.task2Assigned)
            }

            whenState(Story.masterCrown.task2Done) {
                say(
                    "- Well done again,",
                    "adventurers. The third",
                    "of my tasks will perhaps",
                    "be the toughest one",
                    "of them all! Find me a",
                    "DWARF STONE. It is a",
                    "stone of magnetic iron",
                    "which gives the wielder",
                    "the power of levitation.",
                    "",
                )
                say {
                    line("- Begin your search in")
                    line("the dwarven colony, near")
                    line(location(Locations.borinCamp).coordinates, ". Be sure to treat")
                    line("the dwarves with respect,")
                    line("for they are swift to")
                    line("anger.")
                    line("")
                }
                journal {
                    line("The dwarf village is at ", location(Locations.borinCamp).coordinates, ".")
                }
                mark(Story.masterCrown.task3Assigned)
            }

            whenState(Story.masterCrown.borinPaid) {
                say {
                    line("- I JUST CAN'T TELL YOU")
                    line("HOW GRATEFUL I AM!")
                    line("He takes the stone and")
                    line("smiles broadly at you.")
                    line("- Very well, seekers! The")
                    line("Master Crown is hidden in")
                    line("a dungeon near ", location(Locations.masterCrownDungeon).coordinates, "!")
                    line("Fare thee well.")
                    line("")
                }
                journal {
                    line("The Master Crown dungeon is at ", location(Locations.masterCrownDungeon).coordinates, ".")
                }
                mark(Story.masterCrown.crownTold)
            }
        }

        npc(Npcs.azaaza) {
            whenState(Story.silverKey.thiefSqueezed) {
                say("You ask him if he still", "has the Key, prepared", "for the worst...", "")
                say(
                    "- Nah, sorry. I kinda",
                    "studied it enough and",
                    "was gonna bring it to",
                    "Ollk to sell it. However",
                    "on the road between Wilor",
                    "and Ollk I was caught by",
                    "filthy hobgoblins!",
                    "",
                )
                say(
                    "- I managed to get away,",
                    "but they followed me! Just",
                    "to the south-west of here",
                    "one of their arrows hit",
                    "my horse.",
                    "",
                )
                say(
                    "- They clubbed me",
                    "unconcious and took all",
                    "my belonings!!! Including",
                    "that key. Find them and",
                    "take it back. It's yours",
                    "if you get it.",
                    "",
                )
                mark(Story.silverKey.smithConfessed)
            }
        }

        npc(Npcs.marcus) {
            whenState(Story.hermit.trollTooth.none) {
                say(
                    "- Welcome, chosen ones!",
                    "the hermit cackles.",
                    "- I know of the Fire",
                    "Shield, an item of",
                    "great power.",
                    "Go find me teeth of",
                    "a troll, and I will",
                    "tell you about it.",
                    "",
                )
                mark(Story.hermit.trollTooth.accepted)
            }

            whenState(Story.hermit.trollTooth.extracted) {
                say {
                    line("You hand him the troll")
                    line("tooth.")
                    line("- Oh YES! He cackles.")
                    line("At areas uncharted, near")
                    line(location(Locations.hermitDungeon).coordinates, " is the cave you")
                    line("seek. Beware of the")
                    line("guardian the ancients")
                    line("gave birth too...")
                    line("")
                }
                journal {
                    line("The Fire Shield Dungeon is at ", location(Locations.hermitDungeon).coordinates, ".")
                }
                mark(Story.hermit.trollTooth.delivered)
            }
        }
    }

    }

    // Authored start state fixture. It can stay compact and readable.
    stateFixture(States.freshReplay) {
        realm {
            day = 14
            hour = 9
            rngState = 1L
            activeParty = Parties.replay
            wilderness(Maps.wilderness)
        }

        locations {
            Locations.silverKeyPit at xy(45, 13)
            Locations.hobgoblinCave at xy(46, 13)
            Locations.beastCave at xy(11, 23)
        }

        npcs {
            Npcs.garth at Towns.thord.inn
            Npcs.bernak at Towns.wilor.inn
            Npcs.azaaza at Towns.ollk.inn
        }

        party(Parties.replay) {
            player = "Replay"
            name = "Replay"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 1000
                torches = 5
                food = 100
                timeLeft = 200
                restedHours = 14
            }

            flags {
                mark(Story.garth.bandits.recovered)
                mark(Story.silverKey.noteFound)
            }

            milestones {
                // No special win-state milestones in this authored fixture yet.
            }

            inventory {
                slot(1).empty()
                slot(2).item(Items.fireWand)
            }

            members {
                member("A") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Thief
                    level = 1
                    health = 68
                    hitPoints = 68 of 68
                    weaponSkill = 27
                    wisdom = 27
                    mana = 0 of 27
                    charisma = 49
                    equipped {
                        weapon = Items.none
                        armor = Items.none
                    }
                    experience = 0
                    alive()
                }

                member("B") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Thief
                    level = 1
                    health = 46
                    hitPoints = 46 of 46
                    weaponSkill = 42
                    wisdom = 48
                    mana = 0 of 48
                    charisma = 64
                    equipped {
                        weapon = Items.none
                        armor = Items.none
                    }
                    experience = 0
                    alive()
                }
            }

            visited(Npcs.garth, Npcs.bernak)
            journal { banditsCargoRecovered() }
        }
    }

    // Playtest fixture for real-runtime exploration. It starts at the normal
    // new-game wilderness position outside Thord, but with a complete strong
    // level-1 party and enough resources to exercise deeper systems quickly.
    stateFixture(States.strongReplay) {
        realm {
            day = 14
            hour = 9
            rngState = 1L
            activeParty = Parties.playtest
            wilderness(Maps.wilderness)
        }

        locations {
            Locations.silverKeyPit at xy(45, 13)
            Locations.hobgoblinCave at xy(46, 13)
            Locations.beastCave at xy(11, 23)
        }

        npcs {
            Npcs.garth at Towns.thord.inn
            Npcs.bernak at Towns.wilor.inn
            Npcs.azaaza at Towns.ollk.inn
        }

        party(Parties.playtest) {
            player = "playtest"
            name = "Playtest"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 10000
                torches = 40
                food = 150
                timeLeft = 200
                restedHours = 14
            }

            inventory {
                slot(1).item(Items.redPotion)
                slot(2).item(Items.bluePotion)
                slot(3).item(Items.blanket)
                slot(4).item(Items.tent)
                slot(5).item(Items.rope)
                slot(6).item(Items.fireWand)
                slot(7).item(Items.staffOfHealing)
                slot(8).item(Items.magicSword)
                slot(9).item(Items.mithrilMail)
                slot(10).item(Items.crossbow)
                slot(11).item(Items.torch)
                slot(12).item(Items.ration)
            }

            members {
                member("Aragorn") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Fighter
                    level = 1
                    health = 88
                    hitPoints = 88 of 88
                    weaponSkill = 95
                    wisdom = 72
                    mana = 0 of 72
                    charisma = 78
                    equipped {
                        weapon = Items.magicSword
                        armor = Items.mithrilMail
                    }
                    experience = 0
                    alive()
                }

                member("Legolas") {
                    gender = Gender.Female
                    race = Race.Elf
                    klass = PcClass.Ranger
                    level = 1
                    health = 84
                    hitPoints = 84 of 84
                    weaponSkill = 88
                    wisdom = 74
                    mana = 0 of 74
                    charisma = 86
                    equipped {
                        weapon = Items.crossbow
                        armor = Items.elvinCloak
                    }
                    experience = 0
                    alive()
                }

                member("Gandalf") {
                    gender = Gender.Male
                    race = Race.Elf
                    klass = PcClass.Mage
                    level = 1
                    health = 76
                    hitPoints = 76 of 76
                    weaponSkill = 72
                    wisdom = 92
                    mana = 92 of 92
                    charisma = 74
                    equipped {
                        weapon = Items.magicDagger
                        armor = Items.leather
                    }
                    experience = 0
                    alive()
                }

                member("Mira") {
                    gender = Gender.Female
                    race = Race.Human
                    klass = PcClass.Priest
                    level = 1
                    health = 82
                    hitPoints = 82 of 82
                    weaponSkill = 74
                    wisdom = 90
                    mana = 90 of 90
                    charisma = 80
                    equipped {
                        weapon = Items.magicAxe
                        armor = Items.chainMail
                    }
                    experience = 0
                    alive()
                }
            }
        }
    }

    // Focused Thord fixture for compiled-runtime service coverage. It starts
    // directly on the town tile so replay seeds are reserved for town systems
    // such as smith haggling, tavern offers, rumours, and chitchat.
    stateFixture(stateId("town-services-replay")) {
        realm {
            day = 14
            hour = 9
            rngState = 1L
            activeParty = Parties.playtest
            wilderness(Maps.wilderness)
        }

        party(Parties.playtest) {
            player = "playtest"
            name = "Playtest"
            position = Wilderness.xy(12, 11)
            facing = Facing.North

            resources {
                gold = 10000
                torches = 40
                food = 140
                timeLeft = 200
                restedHours = 14
            }

            inventory {
                slot(1).item(Items.redPotion)
                slot(2).item(Items.blanket)
                slot(3).item(Items.mithrilMail)
                slot(4).item(Items.crossbow)
                slot(5).item(Items.fireWand)
                slot(6).item(Items.ration)
                slot(7).empty()
                slot(8).empty()
            }

            members {
                member("Aragorn") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Fighter
                    level = 1
                    health = 88
                    hitPoints = 40 of 88
                    weaponSkill = 95
                    wisdom = 72
                    mana = 0 of 72
                    charisma = 78
                    equipped {
                        weapon = Items.magicSword
                        armor = Items.mithrilMail
                    }
                    experience = 0
                    alive()
                }

                member("Legolas") {
                    gender = Gender.Female
                    race = Race.Elf
                    klass = PcClass.Ranger
                    level = 1
                    health = 84
                    hitPoints = 0 of 84
                    weaponSkill = 88
                    wisdom = 74
                    mana = 0 of 74
                    charisma = 86
                    equipped {
                        weapon = Items.crossbow
                        armor = Items.elvinCloak
                    }
                    experience = 0
                    dead()
                }

                member("Gandalf") {
                    gender = Gender.Male
                    race = Race.Elf
                    klass = PcClass.Mage
                    level = 1
                    health = 76
                    hitPoints = 76 of 76
                    weaponSkill = 72
                    wisdom = 92
                    mana = 10 of 92
                    charisma = 74
                    equipped {
                        weapon = Items.magicDagger
                        armor = Items.leather
                    }
                    experience = 0
                    alive()
                }

                member("Mira") {
                    gender = Gender.Female
                    race = Race.Human
                    klass = PcClass.Priest
                    level = 1
                    health = 82
                    hitPoints = 82 of 82
                    weaponSkill = 74
                    wisdom = 90
                    mana = 90 of 90
                    charisma = 80
                    equipped {
                        weapon = Items.magicAxe
                        armor = Items.chainMail
                    }
                    experience = 0
                    alive()
                }
            }
        }
    }

    // Deterministic town-dialogue fixture for the standalone compiled-runtime
    // replay planner. It fixes role-bearing NPCs in known inn slots and leaves
    // story flags unset so "none" dialogue branches are exercised through the
    // compiled bundle instead of pre-seeding quest progress.
    stateFixture(States.townMissionReplay) {
        realm {
            day = 14
            hour = 14
            activeParty = Parties.playtest
            wilderness(Maps.wilderness)
        }

        locations {
            Locations.silverKeyPit at xy(45, 13)
            Locations.hobgoblinCave at xy(46, 13)
            Locations.beastCave at xy(11, 23)
            Locations.hermitDungeon at xy(45, 13)
            Locations.thunTemple at xy(41, 30)
            Locations.koboldVillage at xy(53, 10)
            Locations.borinCamp at xy(47, 28)
            Locations.nolrimCrypt at xy(11, 23)
            Locations.masterCrownDungeon at xy(23, 32)
        }

        npcs {
            Npcs.garth at Towns.thord.inn
            Npcs.nrimTo at Towns.thord.inn
            Npcs.areth at Towns.thord.inn
            Npcs.nevaak at Towns.thord.inn
            Npcs.gorm at Towns.thord.inn
            Npcs.leander at Towns.thord.inn
            Npcs.marraga at Towns.thord.inn
            Npcs.bernak at Towns.wilor.inn
            Npcs.tybalt at Towns.wilor.inn
            Npcs.harry at Towns.wilor.inn
            Npcs.broth at Towns.wilor.inn
            Npcs.ron at Towns.wilor.inn
            Npcs.ithienn at Towns.wilor.inn
            Npcs.gotharp at Towns.ollk.inn
            Npcs.azaaza at Towns.ollk.inn
        }

        party(Parties.playtest) {
            player = "playtest"
            name = "Playtest"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 100000
                torches = 40
                food = 150
                timeLeft = 500
                restedHours = 500
            }

            inventory {
                slot(1).item(Items.redGem)
                slot(2).item(Items.fireWand)
                slot(3).empty()
                slot(4).empty()
            }

            members {
                member("Fighter") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Fighter
                    level = 8
                    health = 188
                    hitPoints = 188 of 188
                    weaponSkill = 95
                    wisdom = 72
                    mana = 0 of 72
                    charisma = 78
                    equipped {
                        weapon = Items.magicSword
                        armor = Items.mithrilMail
                    }
                    experience = 0
                    alive()
                }

                member("Ranger") {
                    gender = Gender.Female
                    race = Race.Elf
                    klass = PcClass.Ranger
                    level = 8
                    health = 184
                    hitPoints = 184 of 184
                    weaponSkill = 88
                    wisdom = 74
                    mana = 0 of 74
                    charisma = 86
                    equipped {
                        weapon = Items.crossbow
                        armor = Items.elvinCloak
                    }
                    experience = 0
                    alive()
                }
            }
        }
    }

    // Follow-up town-dialogue fixture for the Silver Key chain after the
    // Silver Sanctum note has been found.
    stateFixture(States.townSilverKeyReplay) {
        realm {
            day = 14
            hour = 14
            activeParty = Parties.playtest
            wilderness(Maps.wilderness)
        }

        npcs {
            Npcs.gotharp at Towns.ollk.inn
            Npcs.azaaza at Towns.ollk.inn
        }

        party(Parties.playtest) {
            player = "playtest"
            name = "Playtest"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 100000
                torches = 40
                food = 150
                timeLeft = 500
                restedHours = 500
            }

            flags {
                mark(Story.silverKey.noteFound)
            }

            inventory {
                slot(1).item(Items.fireWand)
            }

            members {
                member("Fighter") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Fighter
                    level = 8
                    health = 188
                    hitPoints = 188 of 188
                    weaponSkill = 95
                    wisdom = 72
                    mana = 0 of 72
                    charisma = 78
                    equipped {
                        weapon = Items.magicSword
                        armor = Items.mithrilMail
                    }
                    experience = 0
                    alive()
                }
            }
        }
    }

    // Random-step mission fixture for the standalone compiled-runtime replay.
    // The Gradle check supplies the seed explicitly so this fixture only states
    // the authored story precondition: the smith has confessed where the key
    // went, making the hobgoblin caravan eligible on the next wilderness step.
    stateFixture(States.hobgoblinCaravanReplay) {
        realm {
            day = 14
            hour = 9
            activeParty = Parties.playtest
            wilderness(Maps.wilderness)
        }

        locations {
            Locations.hobgoblinCave at xy(46, 13)
        }

        party(Parties.playtest) {
            player = "playtest"
            name = "Playtest"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 100000
                torches = 40
                food = 150
                timeLeft = 500
                restedHours = 14
            }

            flags {
                mark(Story.silverKey.smithConfessed)
            }

            inventory {
                slot(1).item(Items.fireWand)
            }

            members {
                member("Fighter") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Fighter
                    level = 28
                    health = 50000
                    hitPoints = 50000 of 50000
                    weaponSkill = 100
                    wisdom = 72
                    mana = 0 of 72
                    charisma = 90
                    equipped {
                        weapon = Items.deathBlade
                        armor = Items.admtMail
                    }
                    experience = 0
                    alive()
                }
            }
        }
    }

    // Long deterministic Master Crown chain fixture for the standalone
    // compiled-runtime replay planner. It keeps the story clean but gives the
    // party enough stamina, inventory room, and combat power to drive the
    // kobold, skullweed, Borin, and Nolrim mission legs as one route.
    stateFixture(States.masterCrownChainReplay) {
        realm {
            day = 14
            hour = 20
            activeParty = Parties.playtest
            wilderness(Maps.wilderness)
        }

        locations {
            Locations.koboldVillage at xy(53, 10)
            Locations.borinCamp at xy(47, 28)
            Locations.nolrimCrypt at xy(11, 23)
            Locations.masterCrownDungeon at xy(23, 32)
        }

        npcs {
            Npcs.bernak at Towns.wilor.inn
            Npcs.tybalt at Towns.wilor.inn
            Npcs.harry at Towns.wilor.inn
            Npcs.broth at Towns.wilor.inn
            Npcs.ron at Towns.wilor.inn
            Npcs.ithienn at Towns.wilor.inn
        }

        party(Parties.playtest) {
            player = "playtest"
            name = "Playtest"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 100000
                torches = 80
                food = 500
                timeLeft = 1000
                restedHours = 1000
            }

            inventory {
                slot(1).item(Items.fireWand)
                slot(2).empty()
                slot(3).empty()
                slot(4).empty()
                slot(5).empty()
                slot(6).empty()
                slot(7).empty()
                slot(8).empty()
            }

            members {
                member("Fighter") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Fighter
                    level = 28
                    health = 50000
                    hitPoints = 50000 of 50000
                    weaponSkill = 100
                    wisdom = 72
                    mana = 0 of 72
                    charisma = 90
                    equipped {
                        weapon = Items.deathBlade
                        armor = Items.admtMail
                    }
                    experience = 0
                    alive()
                }
            }
        }
    }

    // Standalone compiled-runtime acceptance fixture. It starts at the same
    // wilderness point as a normal replay and has no castle power milestones,
    // but it carries the known quest-location placements and prerequisite
    // story state needed for a deterministic compiled-only power-item route.
    stateFixture(States.acceptanceReplay) {
        realm {
            day = 14
            hour = 9
            activeParty = Parties.playtest
            wilderness(Maps.wilderness)
        }

        locations {
            Locations.silverKeyPit at xy(45, 13)
            Locations.hobgoblinCave at xy(46, 13)
            Locations.beastCave at xy(11, 23)
            Locations.hermitDungeon at xy(45, 13)
            Locations.thunTemple at xy(41, 30)
            Locations.koboldVillage at xy(53, 10)
            Locations.borinCamp at xy(47, 28)
            Locations.nolrimCrypt at xy(11, 23)
            Locations.masterCrownDungeon at xy(23, 32)
        }

        npcs {
            Npcs.garth at Towns.thord.inn
            Npcs.bernak at Towns.wilor.inn
            Npcs.azaaza at Towns.ollk.inn
        }

        party(Parties.playtest) {
            player = "playtest"
            name = "Playtest"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 50000
                torches = 120
                food = 500
                timeLeft = 1200
                restedHours = 500
            }

            flags {
                mark(Story.hermit.trollTooth.delivered)
                mark(Story.bracelets.thun.mapBought)
                mark(Story.masterCrown.crownTold)
                mark(Story.jazzy.ogre.accepted)
                mark(Story.silverKey.caveLocated)
            }

            inventory {
                slot(1).item(Items.redGem)
                slot(2).item(Items.blueGem)
                slot(3).item(Items.greenGem)
                slot(4).item(Items.redGem)
                slot(5).item(Items.blueGem)
                slot(6).item(Items.fireWand)
                slot(7).item(Items.staffOfHealing)
                slot(8).item(Items.magicSword)
                slot(9).item(Items.mithrilMail)
                slot(10).item(Items.crossbow)
                slot(11).item(Items.torch)
                slot(12).item(Items.ration)
            }

            members {
                member("Priest") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Priest
                    level = 28
                    health = 5000000
                    hitPoints = 5000000 of 5000000
                    weaponSkill = 100
                    wisdom = 5000
                    mana = 5000 of 5000
                    charisma = 90
                    equipped {
                        weapon = Items.deathBlade
                        armor = Items.admtMail
                    }
                    experience = 0
                    alive()
                }

                member("Thief") {
                    gender = Gender.Female
                    race = Race.Halfling
                    klass = PcClass.Thief
                    level = 28
                    health = 50000
                    hitPoints = 0 of 50000
                    weaponSkill = 100
                    wisdom = 90
                    mana = 0 of 90
                    charisma = 90
                    equipped {
                        weapon = Items.deathBlade
                        armor = Items.admtMail
                    }
                    experience = 0
                    dead()
                }
            }

            visited(Npcs.garth, Npcs.bernak)
            journal { banditsCargoRecovered() }
        }
    }

    // Near-winning authored fixture: all required special milestones are present,
    // but the Ydarg completion marker is not.
    stateFixture(States.nearWinningReplay) {
        realm {
            day = 14
            hour = 22
            activeParty = Parties.replay
            wilderness(Maps.wilderness)
        }

        locations {
            Locations.silverKeyPit at xy(45, 13)
            Locations.hobgoblinCave at xy(46, 13)
            Locations.beastCave at xy(11, 23)
        }

        npcs {
            Npcs.garth at Towns.thord.inn
            Npcs.bernak at Towns.wilor.inn
            Npcs.azaaza at Towns.ollk.inn
        }

        party(Parties.replay) {
            player = "Replay"
            name = "Replay"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 1000
                torches = 40
                food = 150
                timeLeft = 200
                restedHours = 14
            }

            flags {
                mark(Story.garth.bandits.recovered)
                mark(Story.silverKey.keyTaken)
                mark(Story.bracelets.thun.braceletsTaken)
                mark(Story.masterCrown.crownTaken)
                mark(Story.jazzy.ogre.jazzyDefeated)
                mark(Story.hermit.trollTooth.fireShieldAcquired)
            }

            milestones {
                achieved(Milestones.fireShield)
                achieved(Milestones.braceletsOfThun)
                achieved(Milestones.masterCrown)
                achieved(Milestones.footOfJazzy)
                achieved(Milestones.silverKey)
            }

            inventory {
                slot(1).empty()
                slot(2).item(Items.fireWand)
            }

            members {
                member("A") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Fighter
                    level = 28
                    health = 50000
                    hitPoints = 50000 of 50000
                    weaponSkill = 100
                    wisdom = 70
                    mana = 0 of 70
                    charisma = 49
                    equipped {
                        weapon = Items.deathBlade
                        armor = Items.admtMail
                    }
                    experience = 0
                    alive()
                }
            }

            visited(Npcs.garth, Npcs.bernak)
            journal { banditsCargoRecovered() }
        }
    }

    // Runtime snapshot fixture. This is deliberately fuller than authored
    // states: persisted fields are present instead of implied by defaults.
    runtimeState(States.winningReplay) {
        realm {
            day = 14
            hour = 23
            minute = 0
            rngState = 12648430L
            activeParty = Parties.replay
            gameOver(Maps.wilderness)
            banished = false
            lastPlayedDay = 14
            partyMemberMax = 2
        }

        locations {
            Locations.silverKeyPit at xy(45, 13)
            Locations.hobgoblinCave at xy(46, 13)
            Locations.beastCave at xy(11, 23)
        }

        npcs {
            Npcs.garth at Towns.thord.inn
            Npcs.bernak at Towns.wilor.inn
            Npcs.azaaza at Towns.ollk.inn
        }

        party(Parties.replay) {
            player = "Replay"
            name = "Replay"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 1000
                torches = 5
                food = 100
                timeLeft = 0
                restedHours = 6
            }

            counters {
                memberCount = 2
                kills = 1
                waitingMail = false
                attacked = false
                attackToday {
                    clear(Parties.replay)
                }
            }

            flags {
                mark(Story.garth.bandits.recovered)
                mark(Story.silverKey.keyTaken)
                mark(Story.bracelets.thun.braceletsTaken)
                mark(Story.masterCrown.crownTaken)
                mark(Story.jazzy.ogre.jazzyDefeated)
                mark(Story.hermit.trollTooth.fireShieldAcquired)
            }

            milestones {
                achieved(Milestones.fireShield)
                achieved(Milestones.braceletsOfThun)
                achieved(Milestones.masterCrown)
                achieved(Milestones.footOfJazzy)
                achieved(Milestones.silverKey)
                achieved(Milestones.ydargVictory)
            }

            inventory {
                slot(0).empty()
                slot(1).empty()
                slot(2).item(Items.fireWand)
                slot(3).empty()
                slot(4).empty()
                slot(5).empty()
                slot(6).empty()
                slot(7).empty()
                slot(8).empty()
                slot(9).empty()
                slot(10).empty()
                slot(11).empty()
                slot(12).empty()
                slot(13).empty()
                slot(14).empty()
                slot(15).empty()
                slot(16).empty()
            }

            members {
                member("A") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Thief
                    level = 1
                    health = 68
                    hitPoints = 68 of 68
                    weaponSkill = 27
                    wisdom = 27
                    mana = 0 of 27
                    charisma = 49
                    equipped {
                        weapon = Items.none
                        armor = Items.none
                    }
                    experience = 0
                    alive()
                }

                member("B") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Thief
                    level = 1
                    health = 46
                    hitPoints = 46 of 46
                    weaponSkill = 42
                    wisdom = 48
                    mana = 0 of 48
                    charisma = 64
                    equipped {
                        weapon = Items.none
                        armor = Items.none
                    }
                    experience = 0
                    alive()
                }
            }

            npcVisits {
                visited(Npcs.garth)
                visited(Npcs.bernak)
                notVisited(Npcs.azaaza)
            }

            journal {
                banditsCargoRecovered()
                line("Castle Ydarg has fallen.")
            }
        }
    }
}

val nextDsl = nextRylween.content
