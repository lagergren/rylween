@file:Suppress("ktlint:standard:no-wildcard-imports")

// RylweenGame.kt -- game-specific Rylween authored content.
//
// Designer note: this file is the single place to add new game content
// (flags, missions, monsters, items, screens, scripts, ...). It compiles
// as regular Kotlin source so IntelliJ resolves DSL imports directly;
// reusable DSL/runtime implementation lives in core/src/main/kotlin/rylween.
//
// Validate with: ./gradlew check

package rylween.game

import rylween.dsl.*

// =============================================================================
// 06. Full-game authoring area
// =============================================================================
//
// This section contains the authored current game in the order below:
//
//   rylween
//     content
//       global identity/state: metadata, limits, flags, secrets
//       presentation: visuals/staticScreens, layouts, terminal
//       world and systems: terrain, worldMap, realm, castle, prompts, pacing
//       catalogs/progression: items, gameProgress, leveling
//       authored rules: characterCreation, formulas, rewards, combat, wilderness
//       content tables: dungeons, speak, towns, townSystem, characters, monsters
//       behavior: missions, castle scenes, town scenes, mission scenes, dialogue
//     state/runtimeState/stateFixture
//       realm, locations, npcs, parties, inventory, members, journal
//     script bodies
//       scene, dialogue branch, spell, itemUse, dungeon event, castle hook
//
// Add new game content here; reusable DSL/runtime implementation belongs in the
// packages named above, not in this game-authoring file.

val authoredRylween: RylweenModel =
    rylween {
        // Static content is the compiled game definition: catalogs, maps,
        // reusable system rules, scripts, and all player-visible prose.
        content {
            // Sound effects, music and ambient loops live in RylweenAudio.kt.
            include(rylweenAudio)

            // Metadata names the universe/version used in bundle compatibility
            // checks and save-file identity.
            metadata {
                universe("Rylween", version = "kotlin-dsl")
            }

            // Limits are non-derived allocation caps. Keep derived quantities,
            // such as map width/height/cell count, on the data that owns them.
            limits {
                maxWorldMaps = 1
            }

            // Flags model story and encounter state machines. Each state listed
            // here can be referenced by scripts, dialogue branches, and saves.
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
                        Story.erethor.lute.rumorTold,
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

            // Secrets are explicit sysop/debug hooks from the original game.
            // Keeping them authored prevents hidden runtime-only special cases.
            // The server picks the accounts: RYLWEEN_ADMIN_ACCOUNT (default
            // admin) is the sysop, RYLWEEN_VICTIM_ACCOUNT (default nobody) the
            // victim.
            secrets {
                admin {
                    alwaysRunFromNonMandatoryEncounters()
                    addTimeLeft('/', amount = 50, refreshStatus = true)
                    grantItems('*', ItemPools.magicItems)
                    addGold('+', amount = 1_000, refreshStatus = true)
                }
                victim {
                    spawnEncounterEvery(everySteps = 101, monster = Monsters.sysopSmiter)
                }
            }

            // Visuals bind logical image/static-screen ids to concrete assets.
            // Frontends choose the ANSI or bitmap representation for an id.
            visuals {
                // Image rows keep the game-facing id stable while allowing
                // multiple asset kinds, placements, and transitions per frontend.
                images {
                    ansiFullScreen(Images.Static.screen, "ansi/screen.ans")
                    ansiFullScreen(Images.Static.title, "ansi/title.ans")

                    ansiMapWindow(Images.Scene.ydargCastle, "tiles/ydarg-castle.ans")
                    ansiMapWindow(Images.Scene.tent, "tiles/wilderness-tent.ans")
                    ansiMapWindow(Images.Scene.battleVictory, "tiles/battle-victory.ans")
                    ansiMapWindow(Images.Scene.encounterBanner1, "tiles/encounter-banner-1.ans")
                    ansiMapWindow(Images.Scene.encounterBanner2, "tiles/encounter-banner-2.ans")
                    ansiMapWindow(Images.Scene.encounterBanner3, "tiles/encounter-banner-3.ans")
                    ansiMapWindow(Images.Scene.encounterBanner4, "tiles/encounter-banner-4.ans")
                    ansiMapWindow(Images.Scene.encounterBanner5, "tiles/encounter-banner-5.ans")
                    ansiMapWindow(Images.Encounter.goblinoid, "tiles/encounter-banner-1.ans")
                    ansiMapWindow(Images.Encounter.undead, "tiles/encounter-banner-3.ans")
                    ansiMapWindow(Images.Encounter.beast, "tiles/encounter-banner-4.ans")
                    ansiMapWindow(Images.Encounter.flying, "tiles/encounter-banner-2.ans")
                    ansiMapWindow(Images.Encounter.vermin, "tiles/encounter-banner-5.ans")
                    ansiMapWindow(Images.Encounter.brute, "tiles/encounter-banner-1.ans")
                    ansiMapWindow(Images.Encounter.humanoidMagic, "tiles/encounter-banner-3.ans")
                    ansiMapWindow(Images.Encounter.boss, "tiles/encounter-banner-5.ans")
                    ansiMapWindow(Images.Scene.cave, "tiles/cave-entrance.ans")
                    ansiMapWindow(Images.Scene.insideCave, "tiles/cave-interior.ans")
                    ansiMapWindow(Images.Scene.church, "tiles/towne-church.ans")
                    ansiMapWindow(Images.Scene.smith, "tiles/towne-smith.ans")
                    ansiMapWindow(Images.Scene.inn, "tiles/towne-inn.ans")
                    ansiMapWindow(Images.Scene.merchant, "tiles/towne-merchant.ans")
                    ansiMapWindow(Images.Scene.tavern, "tiles/towne-tavern.ans")
                    ansiMapWindow(Images.Scene.hut, "tiles/wilderness-hut.ans")
                    ansiMapWindow(Images.Scene.death, "tiles/death.ans")
                    ansiMapWindow(Images.Scene.towneMainstreet, "tiles/towne-mainstreet.ans")
                    ansiMapWindow(Images.Scene.wildernessNoTent, "tiles/wilderness-no-tent.ans")
                    ansiMapWindow(Images.Scene.towneGuild, "tiles/towne-guild.ans")

                    bitmapMapWindow(Images.Scene.ydargCastle, "bitmap/assets/scenes/scene-ydarg-castle.png")
                    bitmapMapWindow(Images.Scene.tent, "bitmap/assets/scenes/scene-camp-tent.png")
                    bitmapMapWindow(Images.Scene.battleVictory, "bitmap/assets/scenes/scene-battle-victory.png")
                    bitmapMapWindow(Images.Scene.encounterBanner1, "bitmap/assets/monsters/monster-goblinoid.png")
                    bitmapMapWindow(Images.Scene.encounterBanner2, "bitmap/assets/monsters/monster-flying.png")
                    bitmapMapWindow(Images.Scene.encounterBanner3, "bitmap/assets/monsters/monster-undead.png")
                    bitmapMapWindow(Images.Scene.encounterBanner4, "bitmap/assets/monsters/monster-beast.png")
                    bitmapMapWindow(Images.Scene.encounterBanner5, "bitmap/assets/monsters/monster-vermin.png")
                    bitmapMapWindow(Images.Encounter.goblinoid, "bitmap/assets/monsters/monster-goblinoid.png")
                    bitmapMapWindow(Images.Encounter.undead, "bitmap/assets/monsters/monster-undead.png")
                    bitmapMapWindow(Images.Encounter.beast, "bitmap/assets/monsters/monster-beast.png")
                    bitmapMapWindow(Images.Encounter.flying, "bitmap/assets/monsters/monster-flying.png")
                    bitmapMapWindow(Images.Encounter.vermin, "bitmap/assets/monsters/monster-vermin.png")
                    bitmapMapWindow(Images.Encounter.brute, "bitmap/assets/monsters/monster-brute.png")
                    bitmapMapWindow(Images.Encounter.humanoidMagic, "bitmap/assets/monsters/monster-humanoid-magic.png")
                    bitmapMapWindow(Images.Encounter.boss, "bitmap/assets/monsters/monster-boss.png")
                    bitmapMapWindow(Images.Scene.cave, "bitmap/assets/scenes/scene-cave-entrance.png")
                    bitmapMapWindow(Images.Scene.insideCave, "bitmap/assets/scenes/scene-cave-interior.png")
                    bitmapMapWindow(Images.Scene.church, "bitmap/assets/scenes/scene-church.png")
                    bitmapMapWindow(Images.Scene.smith, "bitmap/assets/scenes/scene-smith.png")
                    bitmapMapWindow(Images.Scene.inn, "bitmap/assets/scenes/scene-inn.png")
                    bitmapMapWindow(Images.Scene.merchant, "bitmap/assets/scenes/scene-merchant.png")
                    bitmapMapWindow(Images.Scene.tavern, "bitmap/assets/scenes/scene-tavern.png")
                    bitmapMapWindow(Images.Scene.hut, "bitmap/assets/scenes/scene-wilderness-hut.png")
                    bitmapMapWindow(Images.Scene.death, "bitmap/assets/scenes/scene-death.png")
                    bitmapMapWindow(Images.Scene.towneMainstreet, "bitmap/assets/scenes/scene-thord-mainstreet.png")
                    bitmapMapWindow(Images.Scene.wildernessNoTent, "bitmap/assets/scenes/scene-camp-night.png")
                    bitmapMapWindow(Images.Scene.towneGuild, "bitmap/assets/scenes/scene-guild-closed.png")
                    bitmapMapWindow(Images.Dungeon.chest, "bitmap/assets/scenes/scene-dungeon-chest.png")
                    bitmapMapWindow(Images.Dungeon.trap, "bitmap/assets/scenes/scene-dungeon-trap.png")
                    bitmapMapWindow(Images.Castle.treasure, "bitmap/assets/scenes/scene-dungeon-chest.png")
                    bitmapMapWindow(Images.Castle.pitfall, "bitmap/assets/scenes/scene-dungeon-trap.png")
                    bitmapMapWindow(Images.Castle.jaaraGate, "bitmap/assets/scenes/scene-jaara-gate.png")
                    bitmapMapWindow(Images.Castle.stairsGuardians, "bitmap/assets/scenes/scene-castle-stairs-guardians.png")
                    bitmapMapWindow(Images.Mission.skullweedSwamp, "bitmap/assets/scenes/scene-skullweed-swamp.png")

                    bitmapMapWindowGroup("bitmap/assets/party/portraits") {
                        image(Images.Portrait.default, "portrait-default.png")
                        image(Images.Portrait.fighter, "portrait-fighter.png")
                        image(Images.Portrait.thief, "portrait-thief.png")
                        image(Images.Portrait.mage, "portrait-mage.png")
                        image(Images.Portrait.priest, "portrait-priest.png")
                        image(Images.Portrait.bard, "portrait-bard.png")
                        image(Images.Portrait.ranger, "portrait-ranger.png")
                        image(Images.Portrait.femaleDefault, "portrait-female-default.png")
                        image(Images.Portrait.femaleFighter, "portrait-female-fighter.png")
                        image(Images.Portrait.femaleThief, "portrait-female-thief.png")
                        image(Images.Portrait.femaleMage, "portrait-female-mage.png")
                        image(Images.Portrait.femalePriest, "portrait-female-priest.png")
                        image(Images.Portrait.femaleBard, "portrait-female-bard.png")
                        image(Images.Portrait.femaleRanger, "portrait-female-ranger.png")
                        image(Images.Portrait.maleHuman, "portrait-male-human.png")
                        image(Images.Portrait.maleDwarf, "portrait-male-dwarf.png")
                        image(Images.Portrait.maleElf, "portrait-male-elf.png")
                        image(Images.Portrait.maleHalfling, "portrait-male-halfling.png")
                        image(Images.Portrait.femaleHuman, "portrait-female-human.png")
                        image(Images.Portrait.femaleDwarf, "portrait-female-dwarf.png")
                        image(Images.Portrait.femaleElf, "portrait-female-elf.png")
                        image(Images.Portrait.femaleHalfling, "portrait-female-halfling.png")
                    }
                }

                // Static screens are named document/report routes. Some are
                // resource files; score/heroes screens are generated reports.
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

            // Layout regions describe the classic screen geometry. Runtime text
            // wrapping uses these dimensions instead of hardcoded window sizes.
            layouts {
                region(ScreenRegions.textWindow, x = 49, y = 5, width = 26, height = 11)
                region(ScreenRegions.topLeft, x = 0, y = 0)
                region(ScreenRegions.statuslineTop, x = 3, y = 21)
                region(ScreenRegions.statuslineBottom, x = 13, y = 22)
                region(ScreenRegions.mapWindow, x = 8, y = 6, width = 16, height = 9)
            }

            // Terminal presentation maps terrain to ANSI-era glyphs and colors.
            // Bitmap/web frontends can ignore this without changing game rules.
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

            // Terrain declares what each world-map character means. Movement,
            // settlement entry, and NPC placement all read these semantics.
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

            // World maps are authored as terrain rows. Size and storage counts
            // are derived from these strings by validation and compilation.
            worldMap(Maps.wilderness) {
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

            // Realm rules are global balance and survival systems: resource
            // capacity, turn upkeep, hunting, camping, fatigue, and starvation.
            realm {
                baseResourceCapacity(Resource.Food, 150)
                spellCost = Formulas.spellCost
                // Upkeep runs during wilderness turns. These fields control food
                // ticking, fatigue labels/drain, mana recovery, and starvation.
                upkeep {
                    foodCost = Formulas.foodUpkeep
                    foodTickEvery = 5
                    starveFoodThreshold = 0
                    starveDamage = 2
                    manaRegenChance = 50
                    restedHoursCostPerTurn = 1
                    starvationDeath {
                        line(UpkeepText.memberName, " starved to death!!!")
                    }
                    fatigueAt(0, literalText("You begin to feel tired"), statusLabel = "TIRED")
                    fatigueAt(-4, literalText("You are exhausted!!!"), drain = true, statusLabel = "EXHAUSTED")
                    fatigueAt(-8, flowText("You begin to feel weak from lack of sleep."), drain = true, statusLabel = "WEAK")
                    fatigueBelow(-8, flowText("You faint from lack of much-needed sleep."), drain = true, statusLabel = "FAINT")
                }
                // Hunt is a reusable ranger action. The fields define chance,
                // move cost, food range, target prompts, and result text.
                hunt {
                    successChance = Formulas.huntSuccessChance
                    timeCost = 5
                    foodMin = 10
                    foodMax = 19
                    whoPrompt =
                        literalText {
                            line("Who? (1-", PartyText.memberCount, ")")
                        }
                    dead = literalText("Dead.")
                    notRanger =
                        literalText {
                            line(HuntText.memberSubjectPronoun, " is not a ranger.")
                        }
                    tooFewMoves = literalText("Too few moves for that.")
                    tries =
                        literalText {
                            line(HuntText.memberName, " tries to trap a rabbit...")
                        }
                    succeeds =
                        literalText {
                            line("...and succeeds.")
                            line("The party gains ", HuntText.foodGained, " food rations.")
                            line("")
                        }
                    fails = literalText("...but fails.", "")
                }
                // Camp controls rest healing, food cost, time advance, guard
                // behavior, temperature damage, and ambush chance.
                camp {
                    healthGain = 15
                    foodCostPerMember = 4
                    hoursAdvanced = 8
                    hoursRestedReset = 14
                    temperatureMax = 15
                    ambushChancePercent = 19
                    ambushTier = EncounterTiers.weak
                    notTired = literalText("Not tired.")
                    notEnoughFood = literalText("Not enough food.")
                    guardPrompt = literalText("Camp.", "", "Post a guard?", "", "(Y)es", "(N)o")
                    guardMemberPrompt =
                        literalText {
                            line("Who? (1-", PartyText.memberCount, ")")
                        }
                    guardMemberDead = literalText("Dead.")
                    standsGuard =
                        flowText(
                            CampText.guardName,
                            " stands guard and won't get any health back during this rest.",
                        )
                    sleeping = literalText("Zzz...")
                    rested = flowText("You wake up 8 hours later. Well rested.")
                    ambush = literalText("Ambush!!!")
                    surpriseKill =
                        flowText("A camper is dead before you can react!!!")
                    temperatureBand(
                        band = TemperatureBand.Low,
                        range = 6 until 10,
                        damage = 5,
                        message = literalText("Low Temperature."),
                        noShelter = flowText("No good equipment, Party loses 5 health."),
                    )
                    temperatureBand(
                        band = TemperatureBand.VeryLow,
                        range = 10..15,
                        damage = 5,
                        message = literalText("Very low temperature."),
                        noShelter = flowText("No good equipment, Party loses 10 health."),
                    )
                }
            }

            // Castles use a reusable grid-castle model. This block supplies the
            // current castle's entry gate, rewards, traps, stairs, text, scripts.
            castle(Castles.ydarg) {
                entry = xy(14, 30)
                image = Images.Backdrop.ydargCastle
                entryRequirement = Milestones.silverKey
                completionMilestone = Milestones.ydargVictory
                monsterTier = EncounterTiers.deep
                map(size = 5, roomTypeMax = 5)

                // Treasure separates ordinary room loot from the magic-item pool.
                // `gateMax` caps which room kinds can roll treasure.
                treasure {
                    gateMax = 4
                    regularKind(
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
                    )
                    regularKind(
                        Items.leather,
                        Items.chainMail,
                        Items.plateMail,
                        Items.ringMail,
                        Items.elvenCloak,
                        Items.mithrilMail,
                        Items.admtMail,
                    )
                    regularKind(
                        Items.torch,
                        Items.ration,
                        Items.blanket,
                        Items.tent,
                        Items.rope,
                        Items.redPotion,
                        Items.bluePotion,
                        Items.amberPotion,
                    )
                    magic(
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
                    )
                }

                // Pitfall text, damage, and the thief's disarm chance are authored
                // here; the castle runner chooses and applies traps through the
                // generic room system.
                pitfall {
                    damageDivisor = 6
                    disarmChance = Formulas.castleTrapDisarmChance
                    trap(flowText("Arrows speed through the air and hit the party!"))
                    trap(flowText("Poison needles shoot out from the floor!"))
                    trap(flowText("A volley of shurikens is hurled from a hatch in the wall!"))
                }

                // Stairs define each power-item gate required to climb higher in
                // Zadraz, including the text shown when the item is missing.
                stairs {
                    floorCount = 4
                    item(1, Milestones.fireShield, "The Fire Shield", literalText("You don't have it!"))
                    item(2, Milestones.masterCrown, "The Master Crown", literalText("You don't have it!"))
                    item(3, Milestones.footOfJazzy, "The Foot of Jazzy", literalText("You don't have it!"))
                    item(4, Milestones.braceletsOfThun, "The Bracelets of Thun", literalText("You don't have them!"))
                }

                // Script ids are story hooks invoked by the castle runner at
                // entry, completion, and blocked/already-complete branches.
                scripts {
                    alreadyCompleted = Scenes.Castle.ydargAlreadyCompleted
                    entryBlocked = Scenes.Castle.ydargEntryBlocked
                    entered = Scenes.Castle.ydargEntered
                    completed = Scenes.Castle.ydargCompleted
                }

                // Text rows are castle-specific prompts, menus, labels, images,
                // and narration consumed by the reusable castle runtime.
                text {
                    floorNames("(Ground Level)", "(First Floor)", "(Base of Tower)", "(Top of Tower)")
                    roomMonster = flowText("You are spotted by monsters!")
                    trapTriesDisarm =
                        literalText {
                            line(CastleText.thiefName, " tries to disarm it...")
                            line("")
                        }
                    trapDisarmSucceed = literalText("AND SUCCEEDS!")
                    trapDisarmFail = literalText("BUT FAILS!")
                    treasureIllustration = Images.Castle.treasure
                    treasureMenu = literalText("(T)ake it", "(L)eave it")
                    treasureFull = literalText("You can't carry more.")
                    treasureTaken = literalText("", "Ok.")
                    moveNorth = literalText("North.")
                    moveSouth = literalText("South.")
                    moveEast = literalText("East.")
                    moveWest = literalText("West.")
                    stairsIntro = flowText("You stand before the stairs to the next level.")
                    stairsIllustration = Images.Castle.stairsGuardians
                    pitfallIllustration = Images.Castle.pitfall
                    stairsGuardianOffer =
                        flowText(
                            """
                            A mage imprisoned in a fiery sphere offers to open the trapdoor at the top of the stairs if you give him an item of power!

                            He also offers to teleport you out of the castle, for free.
                            """,
                        )
                    stairsMenu = literalText("Choose:", "", "(T)eleport Out", "(G)ive him an item", "(L)eave")
                    stairsLeave = literalText("You take the nearest exit!")
                    teleportVortex = flowText("The world fades into a vortex of agonizing vertigo...")
                    teleportOutside = literalText("And you are outside...")
                    givePrompt = literalText("Give him what?", "")
                    stairsGuardianGrateful =
                        flowText(
                            """
                            The sphere fades away and the mage steps out. He is grateful. Before he teleports away, he casts an OPEN spell on the trapdoor above and speaks:

                            - I will aid thee in the final battle!
                            """,
                        )
                }
            }

            // Prompts are reusable runtime text and input bindings. Keep wording
            // here when multiple runners/frontends need the same player-facing line.
            prompts {
                // Pause verbs feed the old randomized "hit return" combat flavor
                // text. The runtime chooses one; content owns the vocabulary.
                pauseVerbs(
                    "Smash",
                    "Bash",
                    "Slash",
                    "Lunge at",
                    "Crush",
                    "Destroy",
                    "Mutilate",
                    "Thrash",
                    "Stab",
                    "Brain",
                    "Pummel",
                    "Whack",
                    "Pulverize",
                    "Annihilate",
                    "Obliterate",
                    "Demolish",
                    "Hammer",
                    "Pound",
                    "Maul",
                    "Wallop",
                    "Cleave",
                    "Mangle",
                    "Skewer",
                    "Eviscerate",
                    "Vanquish",
                    "Trample",
                    "Batter",
                    "Slam",
                    "Smite",
                    "Decapitate",
                )
                // Title-menu bindings are semantic choices, not just letters.
                // Labels feed help/status views in ANSI and bitmap runtimes.
                titleMenu {
                    header = literalText("Rylween", "")
                    createNewParty = inputKey("N", label = "New Party")
                    continueSavedGame = inputKey("L", label = "Load Game")
                    terminateParty = inputKey("D", label = "Delete Save")
                    scoreboard = inputKey("S", label = "Scores")
                    hallOfFame = inputKey("H", label = "Heroes")
                    documentation = inputKey("M", label = "Manual")
                    leave = inputKey("E", label = "Exit")
                    continueUnavailableReasonLabel = "no save"
                    continueUnavailable = flowText("There is no saved party for this account.")
                    overwriteConfirm =
                        literalText(
                            "Creating a new party will overwrite the saved party for this account.",
                            "",
                            "(Y)es",
                            "(N)o",
                        )
                    terminateConfirm = literalText("Terminate this party?", "", "(Y)es", "(N)o")
                    terminated = literalText("Party terminated.")
                    terminateFailed = literalText("No party to terminate.")
                    invalidInput = literalText("Choose an option.")
                }
                priest {
                    who = literalText { line("Who? (1-", PartyText.memberCount, ")") }
                    mageCombatOnly = flowText("Sorry. Mage spells are combat-only.")
                    wrongClass = literalText("Not a spellcaster!")
                    dead = literalText("Dead.")
                    invalidCaster = literalText("Not a party member.")
                    invalidSpell = literalText("Not possible.")
                    noSpells = flowText("No priest spells are declared.")
                }
                inventoryUse {
                    usePrompt = literalText("Use what?")
                    quaff = literalText("Quaff what?")
                    noItems = flowText("You are not carrying anything.")
                    noItem = flowText("There is nothing in that slot.")
                    invalidSlot {
                        line("Inventory slot ", InventoryText.slotName, " does not exist.")
                    }
                    notUsable = flowText("Nothing happens with ", InventoryText.itemName, ".")
                }
                equipment {
                    inventory = literalText("Ready what?")
                    who = literalText { line("Who? (1-", PartyText.memberCount, ")") }
                    readyWeapons = literalText("Do you want to ready any weapons before leaving?", "", "(Y)es", "(N)o")
                    readyArmors = literalText("Do you want to ready any armors before leaving?", "", "(Y)es", "(N)o")
                }
                save {
                    saving = literalText("Saving position...")
                    done = literalText("Game saved.")
                    failed = literalText("Save failed.")
                }
                characterCreation {
                    header = literalText("Party Creation", "")
                    method =
                        literalText(
                            "Choose your party:",
                            "Pre-rolled members:",
                        )
                    manualLabel = "Roll your own"
                    prerolledLabel = "Pre-rolled"
                    partyName = flowText("What is the name of your party?")
                    statsPage {
                        line("Stats for char: ", memberNumber)
                        line("")
                        line("Weapon Skill : ", weaponSkill)
                        line("Health       : ", health)
                        line("Wisdom       : ", wisdom)
                        line("Charisma     : ", charisma)
                        line("Dexterity    : ", dexterity)
                        line("")
                    }
                    reroll = literalText("Reroll (y/N) ")
                    memberName {
                        line("Give Character ", memberNumber, " a name:")
                        line("")
                    }
                    gender {
                        line("What gender is ", memberName, "?")
                        line("")
                    }
                    genderMaleLabel = "Male"
                    genderFemaleLabel = "Female"
                    race {
                        line("What race is ", memberName, "?")
                        line("")
                    }
                    classMenu =
                        flowText("Pick a class for this character...")
                    invalidInput = literalText("Invalid input.")
                    saving = literalText("Saving...")
                    saveConfirmation = literalText("OK")
                }
                town {
                    entryWelcome =
                        literalText {
                            line("Welcome to ", TownText.name)
                        }
                    entryTax =
                        flowText(
                            "Our current tax rate is ",
                            TownText.taxPercent,
                            "% - In order to enter, we must request you pay ",
                            TownText.taxAmount,
                            " au.",
                        )
                    entryTaxThanks = literalText("Thank you!")
                }
                party {
                    journalHeader =
                        literalText(
                            "",
                            "TRAVELER'S JOURNAL",
                            "===================",
                            "",
                        )
                    dropPrompt = literalText("Drop what?")
                    dropEmptySlot = literalText("Nothing to drop.")
                    dropAborted = literalText("Aborted.")
                    dropped(flowText("You drop the ", InventoryText.itemName, "."))
                    dropCapacityReduced = flowText("You are forced to leave some of your food behind...")
                    killsHeader = literalText("MONSTERS SLAIN", "")
                    killsEmpty = literalText("No monsters slain yet.")
                }
                wilderness {
                    pass = literalText("You wait for an hour...")
                    noMoves = literalText("No moves left.")
                    outOfMoves =
                        literalText(
                            """
                            You are out of moves for today.

                            (S)ave and Quit
                            (Q)uit (no save)
                            """,
                        )
                    notInWilderness = literalText("The active party is not in the wilderness.")
                    nothingToEnter = literalText("There is nothing to enter here.")
                    impassable = literalText("Impassable.")
                    quitConfirm =
                        literalText(
                            """
                            Do you really want to quit?

                            (Y)es
                            (N)o
                            """,
                        )
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
                    atMaxDepth = flowText("The cave doesn't go deeper.")
                    noTorches = flowText("You have used your last torch. You can't go any further.")
                    leaveCavern = flowText("You leave the hideous cavern!")
                }
            }

            // Pacing controls authored pause defaults. Browser preferences can
            // scale these delays, but the base timing still belongs to content.
            pacing {
                battle {
                    messagePauseMs = 900
                    openingPauseMs = 1_200
                    victoryPauseMs = 4_000
                    battleCryPauseMs = 450
                }
                characterCreation {
                    classRejectionPauseMs = 1_000
                    saveConfirmationPauseMs = 2_000
                }
                shop {
                    interludePagePauseMs = 2_000
                    noticePauseMs = 1_000
                }
                speak {
                    promptReplyPauseMs = 650
                    responsePauseMs = 1_600
                }
                wilderness {
                    campMessagePauseMs = 1_300
                }
            }

            // Items are the catalog used by shops, rewards, inventory, equipment,
            // camp protection, food capacity, and battle/item-use scripts.
            items {
                // Equipment is unrestricted unless a weapon or armor declares allowed classes.
                // Example: weapon(Items.holyBlade, "Holy Blade", cost = 5_000, damage = 40) { equippableBy(PcClass.Priest) }
                empty(Items.none)

                weapon(Items.sword, "Sword", cost = 160, damage = 6)
                weapon(Items.axe, "Axe", cost = 240, damage = 10)
                weapon(Items.bow, "Bow", cost = 160, damage = 7)
                weapon(Items.club, "Club", cost = 140, damage = 4)
                weapon(Items.dart, "Dart", cost = 40, damage = 3)
                weapon(Items.sling, "Sling", cost = 165, damage = 5)
                weapon(Items.longsword, "Longsword", cost = 280, damage = 9)
                weapon(Items.trident, "Trident", cost = 350, damage = 11)
                weapon(Items.halberd, "Halberd", cost = 500, damage = 14)
                weapon(Items.poleArm, "Pole Arm", cost = 670, damage = 18)
                weapon(Items.crossbow, "Crossbow", cost = 900, damage = 21)
                weapon(Items.scythe, "Scythe", cost = 870, damage = 14)
                weapon(Items.scimitar, "Scimitar", cost = 600, damage = 8)
                weapon(Items.twoHandSword, "2H-Sword", cost = 1_200, damage = 19)
                weapon(Items.magicDagger, "Magic Dagger", cost = 1_800, damage = 24)
                weapon(Items.magicSword, "Magic Sword", cost = 3_000, damage = 30)
                weapon(Items.magicAxe, "Magic Axe", cost = 4_000, damage = 38)
                weapon(Items.dancingSword, "Dancing Sword", cost = 6_000, damage = 50)
                weapon(Items.deathBlade, "Death Blade", cost = 10_000, damage = 68)

                armor(Items.leather, "Leather", cost = 260, armorClass = 2)
                armor(Items.chainMail, "Chain Mail", cost = 600, armorClass = 5)
                armor(Items.plateMail, "Plate Mail", cost = 980, armorClass = 7)
                armor(Items.ringMail, "Ring Mail", cost = 1_950, armorClass = 9)
                armor(Items.elvenCloak, "Elven Cloak", cost = 3_600, armorClass = 12)
                armor(Items.mithrilMail, "Mithril Mail", cost = 5_800, armorClass = 14)
                armor(Items.admtMail, "Admt. Mail", cost = 9_400, armorClass = 16)
                armor(Items.protSphere, "Prot. Sphere", cost = 15_000, armorClass = 20)
                armor(Items.cloakOfGods, "Cloak of Gods", cost = 28_000, armorClass = 30)

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

                treasure(Items.redGem, "Red Gem", cost = 1_000)
                treasure(Items.blueGem, "Blue Gem", cost = 1_200)
                treasure(Items.greenGem, "Green Gem", cost = 1_900)
                treasure(Items.chalice, "Chalice", cost = 2_340)
                treasure(Items.pendant, "Pendant", cost = 5_000)
                treasure(Items.brooch, "Brooch", cost = 1_234)
                treasure(Items.crystalBowl, "Crystal Bowl", cost = 4_567)
                treasure(Items.necklace, "Necklace", cost = 987)
                treasure(Items.donkey, "Donkey", cost = 800) {
                    resourceCapacityBonus(Resource.Food, 150)
                }

                special(Items.fireshield, "Fire Shield")
                special(Items.bracelets, "Bracelets of Thun")
                special(Items.mastercrown, "Master Crown")
                special(Items.jazzyfoot, "Jazzyfoot")
                special(Items.silverkey, "Silver Key")

                magicItem(Items.frostWand, "Frost Wand", cost = 1_000)
                magicItem(Items.fireWand, "Fire Wand", cost = 3_000)
                magicItem(Items.gemOfConfusion, "Gem of Confusion", cost = 1_560)
                magicItem(Items.gemOfBlindness, "Gem of Blindness", cost = 4_900)
                magicItem(Items.drumsOfDeath, "Drums of Death", cost = 4_500)
                magicItem(Items.staffOfHealing, "Staff of Healing", cost = 670)
                magicItem(Items.blessingScroll, "Blessing Scroll", cost = 2_000)
                magicItem(Items.staffOfCuring, "Staff of Curing", cost = 4_343)
                magicItem(Items.gemOfResurrection, "Gem of Resurrection", cost = 9_000)
                magicItem(Items.kaelsAxe, "Kael's Axe", cost = 14_000)
                magicItem(Items.holyHandGrenade, "Holy Hand Grenade", cost = 5_600)
                magicItem(Items.fluteOfMadness, "Flute of Madness", cost = 9_000)

                // Pools are reusable item sets for loot, random offers, dungeon
                // finds, and scripted reward choices.
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
                        Items.brooch,
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
                        Items.holyHandGrenade,
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
                        Items.holyHandGrenade,
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
                        Items.halberd,
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
                        Items.brooch,
                        Items.crystalBowl,
                        Items.necklace,
                    )
                }
            }

            // Game progress maps story achievements to power items and the win
            // condition. Hall-of-Heroes text also comes from this model.
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

            // Leveling declares the XP thresholds and per-class stat growth.
            // Runtime level-up uses these rows rather than embedded class rules.
            leveling {
                maxLevel = 28
                hitPointFloor = Formulas.levelUpHitPointFloor
                // XP thresholds are indexed by level: slot 0 is unused and
                // level 1 is always 0 XP. The current curve keeps the classic
                // low-level doubling feel, then widens high-level bands
                // smoothly so the cap stays in the original 2.8M range.
                // Keep this as explicit balance data; a future class-specific
                // formula should still compile down to the same table shape.
                xpThresholds(
                    0,
                    0,
                    2_000,
                    4_000,
                    8_000,
                    12_000,
                    20_000,
                    28_000,
                    38_000,
                    48_000,
                    60_000,
                    77_000,
                    98_000,
                    123_000,
                    156_000,
                    198_000,
                    252_000,
                    319_000,
                    403_000,
                    507_000,
                    632_000,
                    782_000,
                    962_000,
                    1_171_000,
                    1_413_000,
                    1_693_000,
                    2_015_000,
                    2_382_000,
                    2_800_000,
                )
                classLevelUp(PcClass.Fighter) {
                    weaponSkill = 3
                    health = 8
                    wisdom = 1
                    charisma = 1
                    dexterity = 1
                }
                classLevelUp(PcClass.Thief) {
                    weaponSkill = 1
                    health = 5
                    wisdom = 1
                    charisma = 2
                    dexterity = 3
                }
                classLevelUp(PcClass.Mage) {
                    weaponSkill = 1
                    health = 4
                    wisdom = 7
                    charisma = 2
                    dexterity = 1
                }
                classLevelUp(PcClass.Priest) {
                    weaponSkill = 1
                    health = 5
                    wisdom = 8
                    charisma = 3
                    dexterity = 1
                }
                classLevelUp(PcClass.Bard) {
                    weaponSkill = 2
                    health = 7
                    wisdom = 2
                    charisma = 6
                    dexterity = 2
                }
                classLevelUp(PcClass.Ranger) {
                    weaponSkill = 2
                    health = 8
                    wisdom = 3
                    charisma = 3
                    dexterity = 4
                }
            }

            // Character creation defines roll ranges, starting resources,
            // selectable races/classes, class gates, and portrait matching.
            characterCreation {
                statRoll(min = 15, max = 70)
                // Start is the initial runtime state for a newly created party:
                // map position, facing, member cap, level, moves, and resources.
                start {
                    map = Maps.wilderness
                    position = Wilderness.xy(12, 9)
                    facing = Facing.South
                    partyMemberMax = 4
                    level = 1
                    dailyTimeLeft = 175
                    resources {
                        gold = 2_000
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

                // Class rows supply display labels and optional stat gates.
                // Failure text is shown when a roll cannot choose that class.
                playerClass(PcClass.Fighter, "Fighter") {
                    requires(
                        CharacterCreationStat.WeaponSkill,
                        minimum = 50,
                        failureText = flowText("Weapon skill must be at least 50."),
                    )
                }
                playerClass(PcClass.Thief, "Thief") {
                    requires(
                        CharacterCreationStat.Dexterity,
                        minimum = 50,
                        failureText = flowText("Dexterity must be at least 50."),
                    )
                }
                playerClass(PcClass.Mage, "Mage") {
                    requires(
                        CharacterCreationStat.Wisdom,
                        minimum = 50,
                        failureText = flowText("Wisdom must be at least 50."),
                    )
                }
                playerClass(PcClass.Priest, "Priest") {
                    requires(
                        CharacterCreationStat.Wisdom,
                        minimum = 50,
                        failureText = flowText("Wisdom must be at least 50."),
                    )
                }
                playerClass(PcClass.Bard, "Bard") {
                    requires(
                        CharacterCreationStat.Charisma,
                        minimum = 50,
                        failureText = flowText("Charisma must be at least 50."),
                    )
                }
                playerClass(PcClass.Ranger, "Ranger") {
                    requires(
                        CharacterCreationStat.Dexterity,
                        minimum = 50,
                        failureText = flowText("Dexterity must be at least 50."),
                    )
                }

                prerolledMember("Ronan", Gender.Male, Race.Human, PcClass.Fighter)
                prerolledMember("Brynja", Gender.Female, Race.Dwarf, PcClass.Ranger)
                prerolledMember("Elowen", Gender.Female, Race.Elf, PcClass.Mage)
                prerolledMember("Walter", Gender.Male, Race.Human, PcClass.Priest)

                portrait(Images.Portrait.default)
                portrait(Images.Portrait.fighter, klass = PcClass.Fighter)
                portrait(Images.Portrait.thief, klass = PcClass.Thief)
                portrait(Images.Portrait.mage, klass = PcClass.Mage)
                portrait(Images.Portrait.priest, klass = PcClass.Priest)
                portrait(Images.Portrait.bard, klass = PcClass.Bard)
                portrait(Images.Portrait.ranger, klass = PcClass.Ranger)
                portrait(Images.Portrait.femaleDefault, gender = Gender.Female)
                portrait(Images.Portrait.femaleFighter, gender = Gender.Female, klass = PcClass.Fighter)
                portrait(Images.Portrait.femaleThief, gender = Gender.Female, klass = PcClass.Thief)
                portrait(Images.Portrait.femaleMage, gender = Gender.Female, klass = PcClass.Mage)
                portrait(Images.Portrait.femalePriest, gender = Gender.Female, klass = PcClass.Priest)
                portrait(Images.Portrait.femaleBard, gender = Gender.Female, klass = PcClass.Bard)
                portrait(Images.Portrait.femaleRanger, gender = Gender.Female, klass = PcClass.Ranger)
                portrait(Images.Portrait.maleHuman, gender = Gender.Male, race = Race.Human)
                portrait(Images.Portrait.maleDwarf, gender = Gender.Male, race = Race.Dwarf)
                portrait(Images.Portrait.maleElf, gender = Gender.Male, race = Race.Elf)
                portrait(Images.Portrait.maleHalfling, gender = Gender.Male, race = Race.Halfling)
                portrait(Images.Portrait.femaleHuman, gender = Gender.Female, race = Race.Human)
                portrait(Images.Portrait.femaleDwarf, gender = Gender.Female, race = Race.Dwarf)
                portrait(Images.Portrait.femaleElf, gender = Gender.Female, race = Race.Elf)
                portrait(Images.Portrait.femaleHalfling, gender = Gender.Female, race = Race.Halfling)
            }

            // Formulas are named integer expressions shared by encounters,
            // rewards, and leveling. Runtimes evaluate ids, not Kotlin lambdas.
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
                // Dexterity and charisma chances are anchored at the class
                // minimum of 50: hunting is 45% for a dexterity-50 ranger, a
                // castle trap is disarmed at the thief's dexterity in percent,
                // and the best charisma moves the haggle roll by half its
                // distance from 50. Stats keep growing with levels, so each
                // is capped: hunting and disarming at 95%, and the haggle
                // bonus at +20, which leaves the smith refusing at least 56%
                // of haggles.
                formula(Formulas.huntSuccessChance) {
                    value = (dexterity - 5) atMost 95
                }
                formula(Formulas.castleTrapDisarmChance) {
                    value = dexterity atMost 95
                }
                formula(Formulas.haggleRollBonus) {
                    value = ((charisma - 50) / 2) atMost 20
                }
                // A spell costs its base cost plus the caster's level, for mage
                // spells in battle and priest spells alike.
                // Every food tick each living member eats a ration, and the
                // leader one more for every four levels.
                formula(Formulas.foodUpkeep) {
                    value = livingMembers + firstLevel / 4
                }
                // Towns tax a party whose leader has reached level 5: a share
                // of the gold it carries, at the town's rate.
                formula(Formulas.townEntryTax) {
                    value = whenValue(firstLevel gte 5, gold * taxPercent / 100)
                }
                // Shop prices: a ration costs a gold piece more than the
                // leader's level, a healing potion three per level on top of
                // its cost, and shops buy at half an item's cost.
                formula(Formulas.rationPrice) {
                    value = firstLevel + 1
                }
                formula(Formulas.healingPotionPrice) {
                    value = itemCost + firstLevel * 3
                }
                formula(Formulas.sellPrice) {
                    value = itemCost / 2
                }
                formula(Formulas.spellCost) {
                    value = spellBaseCost + level
                }
                // Battle arithmetic. A chance is a percentage, rolled against
                // 0-99. A confused party fights at a third of its weapon skill,
                // and a member's own hit points add to the damage they deal.
                formula(Formulas.playerHitChance) {
                    value = whenValue(partyConfused, weaponSkill / 3, weaponSkill) + 1
                }
                formula(Formulas.playerDamage) {
                    value = weaponDamage + random(0..4) + hitPoints / 12
                }
                formula(Formulas.defendHealing) {
                    value = level
                }
                // A wounded monster hits less hard, and armor takes damage off
                // every blow, down to a single point.
                formula(Formulas.monsterHitChance) {
                    value = monsterWeaponSkill + 1
                }
                formula(Formulas.monsterDamage) {
                    value = (monsterHitPoints / 6 + random(0..9) - armorClass) atLeast 1
                }
                formula(Formulas.monsterSpellChance) {
                    value = 15 + monsterSpellPower / 2
                }
                // Encounters grow with the party: monsters gain the leader's
                // level in weapon skill, and some come in bigger groups.
                formula(Formulas.encounterWeaponSkill) {
                    value = monsterWeaponSkill + firstLevel
                }
                formula(Formulas.encounterFoeCountMax) {
                    value = monsterCountMax + whenValue(countMaxLevelDivisor gt 0, firstLevel / countMaxLevelDivisor)
                }
            }

            // Reward bundles define how XP, gold, loot, and special milestones
            // are granted after reusable flows such as the standard battle.
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

            // Combat configures the reusable standard battle runner: commands,
            // text, formulas, round rules, images, rewards, and monster turns.
            combat {
                // Standard battle is table-driven. Scripted spells/items below
                // plug into this shared turn engine.
                standardBattle {
                    roundLimit = 200
                    encounterFoeCountMax = 8
                    encounterHitPointMinFormula = Formulas.encounterH1Scaled
                    encounterHitPointMaxFormula = Formulas.encounterH2Scaled
                    encounterFoeCountMaxFormula = Formulas.encounterFoeCountMax
                    encounterWeaponSkillFormula = Formulas.encounterWeaponSkill
                    victoryImage(Images.Backdrop.battleVictory)
                    defeatImage(Images.Backdrop.death)

                    // Opening fields decide fight/run odds, mandatory-run policy,
                    // and the encounter banner art pool.
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

                    // Opening keys are the first prompt before individual party
                    // member actions are collected.
                    openingKeys {
                        fight = inputKey('F')
                        flee = inputKey('R')
                    }

                    // Battle text uses typed placeholders such as attackerName
                    // and damage so the runtime does not assemble prose itself.
                    text {
                        encounter {
                            lines("ENCOUNTER!", "")
                            line("You face")
                            line(BattleText.foeDisplayName)
                            line("")
                        }
                        nextRound {
                            line("Round ", BattleText.roundNumber)
                            line("")
                            line("You still face")
                            line(BattleText.foeDisplayName)
                            line("")
                        }
                        fight { line("(F)ight") }
                        fleeOption { line("(R)un") }
                        bloodFlow { line("Blood shall flow!") }
                        whatNow { lines("What now?", "") }
                        targetPrompt { lines("Who?", "") }
                        useWhat { lines("Use what?", "") }
                        spellAbort { line("RETURN leaves.") }
                        runCoward(flowText("Coward. You won't get any EXP for this battle."))
                        runDesperateFail(flowText("You desperately try to get away, but fail!"))
                        runFailAway { line("You fail to get away!") }
                        runEscaped { line("You escaped...") }
                        notPossible { line("Not possible.") }
                        magicImmune(flowText("The magic item doesn't seem to work on this enemy!!!"))
                        spellFizzle(flowText("The spell fizzles! This creature is immune to ordinary magic!!!"))
                        notEnoughMagic { line("Not enough magic!") }
                        restGainHits(flowText("You gain some hit pts as you rest this round."))
                        defendStance(flowText("You put up a defensive stance..."))
                        battleCry {
                            line("HI-YAAAH!")
                            line("You scare them enough to catch them off guard.")
                        }
                        playerHit {
                            line(BattleText.attackerName, " hits for ", BattleText.damage, " damage pts!")
                        }
                        playerMiss { line(BattleText.attackerName, " misses!") }
                        monsterHit {
                            line(BattleText.attackerName, " attacks ", BattleText.targetName, "!")
                            line("And hits for ", BattleText.damage, " damage pts!!!")
                        }
                        monsterMiss {
                            line(BattleText.attackerName, " attacks ", BattleText.targetName, "!")
                            line("Blow misses!")
                        }
                        memberDeath { line(BattleText.targetName, " DIES!!!") }
                        killed { line("...KILLED!") }
                        partyHeld { line("PARTY IS HELD!") }
                        partyDefeated { line("The party has fallen.") }
                        victoryBested(flowText("Thou hast bested thy foes."))
                        victoryReward {
                            line("Each survivor gets")
                            line(BattleText.experience, " experience pts and ", BattleText.gold, " gold pieces.")
                            line("")
                        }
                        victoryLoot {
                            line("You find a")
                            line(BattleText.lootName, ".")
                        }
                        levelGain {
                            line(BattleText.levelMemberName, " gains >", BattleText.levelsGained, "< experience levels.")
                        }
                    }

                    // Player actions define the per-member combat menu and map
                    // input keys to reusable action kinds.
                    playerActions {
                        attack('A') { line("(A)ttack") }
                        defend('D') { line("(D)efend") }
                        useMagicItem('U') { line("(U)se Item") }
                        battleCry('B') { line("(B)attle Cry") }
                        castMageSpell('C') { line("(C)ast Spell") }
                    }

                    // The player round's hit and damage math lives in the formulas
                    // above; defending and a battle cry also wear the foes down.
                    playerRound {
                        attack(hitChance = Formulas.playerHitChance, damage = Formulas.playerDamage)
                        defend(healing = Formulas.defendHealing, weaponSkillPenalty = 2, restThreshold = 5, cap = 250)
                        battleCry(weaponSkillPenalty = 4)
                    }

                    // A monster casts a spell or, failing that, attacks in melee.
                    monsterRound {
                        spellChance = Formulas.monsterSpellChance
                        melee(
                            hitChance = Formulas.monsterHitChance,
                            damage = Formulas.monsterDamage,
                            fallback = Scenes.Battle.monsterMelee,
                        )
                    }

                    rewards = Rewards.standardBattle
                }
            }

            // Wilderness owns the open-map command bindings, random events, cave
            // discovery rules, and terrain narration for movement feedback.
            wilderness {
                // Keys define the wilderness command contract. Labels are reused
                // by live help and frontend status/prompt displays.
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

                // Events cover random caves, quiet turns, encounter split, cave
                // depth/cost, and player-facing cave discovery prompts.
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

                    caveNoMoves(flowText("You don't have enough moves left to explore the cave..."))
                }

                // Terrain transitions are the short flavor messages that can
                // appear when the party steps onto a terrain type.
                terrainTransitions {
                    chancePercent = 9

                    terrain(Terrain.plain, flowText("The rich green plainland of Rylween stretches far in all the directions."))

                    terrain(Terrain.hut, flowText("You have found a hut."))

                    terrain(Terrain.town, flowText("You are close to a towne."))

                    terrain(Terrain.foothills, flowText("Small foothills make your progress more arduous."))

                    terrain(Terrain.desert, flowText("You are in a hot and dry desert. The sun fries you mercilessly."))

                    terrain(
                        Terrain.swamp,
                        flowText(
                            """
                            You are in the swampy marshlands of Rylween. Bubbles of
                            gas rise to the surface where they burst and emit a
                            foul-smelling odor.
                            """,
                        ),
                    )

                    terrain(
                        Terrain.forest,
                        flowText(
                            """
                            The ancient forest is very tranquil. A soft breeze
                            rustles the leaves of mighty oaks.
                            """,
                        ),
                    )

                    terrain(
                        Terrain.castle,
                        flowText(
                            """
                            You stand outside the castle Zadraz. The black steel
                            building seems to want to swallow you whole.
                            """,
                        ),
                    )
                }
            }

            // Dungeons define reusable command keys, event scripts, and scenario
            // tables. Scenarios combine random event mixes with fixed levels.
            dungeons {
                // Dungeon keys are shared by all caves/dungeons and drive both
                // input dispatch and generated command help.
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

                // This backdrop is the default map-window art while underground.
                backdrop(Images.Backdrop.insideCave)

                // Events are reusable level outcomes. Some are recurring random
                // events; others are fixed story levels in named scenarios.
                event(DungeonEvents.nothingSpecial) {
                    script {
                        showTextAndWait("You find nothing special on this level.")
                    }
                }

                event(DungeonEvents.emptyLevel) {
                    script {
                        showTextAndWait("This level is empty.")
                    }
                }

                event(DungeonEvents.spectrumMessage) {
                    recurring()
                    script {
                        showTextAndWait(
                            literalText(
                                """
                                A message on the wall
                                reads:

                                'M U R T C E P S'
                                THiNgs aren't aLways iN
                                theIR naTuRAl oRDeR...
                                """,
                            ),
                        )
                    }
                }

                event(DungeonEvents.gold) {
                    script {
                        localInt(random(40..339)) { amount ->
                            writeText(flowText("You find ", amount, " gold pieces."))
                            gainGold(amount)
                            waitInput("after dungeon gold")
                            clearText()
                        }
                    }
                }

                event(DungeonEvents.torches) {
                    script {
                        localInt(random(2..4)) { amount ->
                            writeText {
                                line("You find ", amount, " torches.")
                                line("")
                            }
                            gainTorches(amount)
                            waitInput("after dungeon torches")
                            clearText()
                        }
                    }
                }

                event(DungeonEvents.findObject) {
                    script {
                        randomDungeonItemFind(
                            commonPools =
                                itemPools(
                                    ItemPools.dungeonWeaponFinds,
                                    ItemPools.dungeonPotionGemFinds,
                                    ItemPools.dungeonTreasureFinds,
                                ),
                            rarePool = ItemPools.magicItems,
                        )
                    }
                }

                // A chest left closed is still there on the next visit; an opened one is gone, as in the original.
                event(DungeonEvents.goldChest) {
                    recurring()
                    script {
                        showBackdrop(Images.Dungeon.chest)
                        menu(
                            "You find a chest!",
                            "",
                            "(O)pen chest",
                            "(L)eave",
                        ) {
                            option('O') {
                                playSound(Sounds.Explore.chestOpen)
                                realm.levelCleared set true
                                // The trap chance is 40% unchecked. A thief's check cuts it
                                // by dexterity: 20% at the class minimum of 50, 12% at 70,
                                // and never below 4%.
                                localInt(40) { trapChance ->
                                    localInt(0) { thiefFound ->
                                        forEachMember {
                                            whenever((thiefFound eq 0) and it.isClass(PcClass.Thief)) {
                                                writeText(flowText(partyName(it), " checks the chest for traps. {pause 1s}"))
                                                trapChance set lit(40) - it.dexterity * 2 / 5
                                                whenever(trapChance lt 4) {
                                                    trapChance set 4
                                                }
                                                thiefFound set 1
                                            }
                                        }

                                        whenever(random(0..99) lt trapChance) {
                                            showBackdrop(Images.Dungeon.trap)
                                            writeText("A TRAP! {pause 600ms}")
                                            choose(random(0..2)) {
                                                case(0) {
                                                    playSound(Sounds.Explore.trapGas)
                                                    writeText(
                                                        """
                                                        Poisonous gas hisses out through hidden holes. {pause}

                                                        The party coughs and retches. {pause 600ms}

                                                        Finally. The gas clears.
                                                        """,
                                                    )
                                                }
                                                case(1) {
                                                    playSound(Sounds.Explore.trapPit)
                                                    writeText(
                                                        """
                                                        A spiked pitfall opens in the floor beneath you. {pause}

                                                        You climb out of it, badly wounded.
                                                        """,
                                                    )
                                                }
                                                case(2) {
                                                    playSound(Sounds.Explore.trapBoulder)
                                                    writeText(
                                                        """
                                                        A large boulder drops from above!!! {pause}

                                                        Badly bruised, you try to stand up again.
                                                        """,
                                                    )
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

                                        localInt(random(0..99) + 320 + (party.leader.experience / 500)) { goldAmount ->
                                            writeText(flowText("The chest contained ", goldAmount, " pieces of gold."))
                                            gainGold(goldAmount)
                                            waitInput("after chest gold")
                                            clearText()
                                        }
                                    }
                                }
                            }
                            option('L') {}
                            default {}
                        }
                    }
                }

                // Beaten monsters stay beaten when the party comes back through their level; monsters it
                // fled from are still there. The original made every visit fight them again.
                event(DungeonEvents.weakEncounter) {
                    recurringUntilWon()
                    randomEncounter(EncounterTiers.weak)
                }

                event(DungeonEvents.deepEncounter) {
                    recurringUntilWon()
                    randomEncounter(EncounterTiers.deep)
                }

                event(DungeonEvents.thunRiddle1) {
                    script {
                        showTextAndWait(
                            """
                            You stand before a mage dressed entirely in red.

                            - Answer this, fools, or die where you stand!
                            """,
                        )
                        choice.textInput(
                            prompt =
                                combineTextBlocks(
                                    literalText(
                                        """
                                        'Between fire and ice,
                                        who ends the fight?
                                        Who stands between those
                                        two? Not red, not white
                                        but blue!
                                        """,
                                    ),
                                    literalText("", "Answer: "),
                                ),
                            acceptedInputs = acceptedText("water"),
                            maxLength = 15,
                            onAccepted = {
                                playSound(Sounds.Explore.riddleRight)
                                showTextAndWait("- Well done. Pass.")
                            },
                            onRejected = {
                                playSound(Sounds.Explore.riddleWrong)
                                showTextAndWait(
                                    """
                                    - FOOLS! Meet thy doom! At a gesture from him,
                                    something evil appears.
                                    """,
                                )
                                encounter(Monsters.norker, mandatory = true)
                            },
                        )
                        clearText()
                    }
                }

                event(DungeonEvents.thunRiddle2) {
                    script {
                        showTextAndWait(
                            """
                            You stand before a mage dressed entirely in green.

                            - Answer this, fools, or die where you stand!
                            """,
                        )
                        choice.textInput(
                            prompt =
                                combineTextBlocks(
                                    literalText(
                                        """
                                        'Siblings we are,
                                        I'm black and she's
                                        white. Each dreary
                                        evening, I swallow her
                                        light.'
                                        """,
                                    ),
                                    literalText("", "Answer: "),
                                ),
                            acceptedInputs = acceptedText("night", "darkness"),
                            maxLength = 15,
                            onAccepted = {
                                playSound(Sounds.Explore.riddleRight)
                                showTextAndWait("- Well done. Pass.")
                            },
                            onRejected = {
                                playSound(Sounds.Explore.riddleWrong)
                                showTextAndWait(
                                    """
                                    - FOOLS! Meet thy doom! At a gesture from him,
                                    something evil appears.
                                    """,
                                )
                                encounter(Monsters.voodooLord, mandatory = true)
                            },
                        )
                        clearText()
                    }
                }

                event(DungeonEvents.thunRiddle3) {
                    script {
                        showTextAndWait(
                            """
                            You stand before a mage dressed entirely in blue.

                            - Answer this, fools, or die where you stand!
                            """,
                        )
                        choice.textInput(
                            prompt =
                                combineTextBlocks(
                                    literalText(
                                        """
                                        'Tall as a house,
                                        round as a cup, and
                                        all the king's horses
                                        can't draw me up! What
                                        am I?'
                                        """,
                                    ),
                                    literalText("", "Answer: "),
                                ),
                            acceptedInputs = acceptedText("well", "a well"),
                            maxLength = 15,
                            onAccepted = {
                                playSound(Sounds.Explore.riddleRight)
                                showTextAndWait("- Well done. Pass.")
                            },
                            onRejected = {
                                playSound(Sounds.Explore.riddleWrong)
                                showTextAndWait(
                                    """
                                    - FOOLS! Meet thy doom! At a gesture from him,
                                    something evil appears.
                                    """,
                                )
                                encounter(Monsters.archdevil, mandatory = true)
                            },
                        )
                        clearText()
                    }
                }

                event(DungeonEvents.colorRiddle) {
                    recurring()
                    script {
                        localInt(context.dungeonLevel - 12) { antechamber ->
                            showTextAndWait {
                                line("A plaque here reads:")
                                line("ANTECHAMBER #", antechamber)
                                line("")
                            }
                        }
                        showTextAndWait(
                            """
                            Before you are seven squares of 20x20 feet. To get
                            across this room you will need to step on one of them.
                            """,
                        )
                        ask.digit(
                            literalText(
                                "1. Red Square",
                                "2. Orange Square",
                                "3. Yellow Square",
                                "4. Green Square",
                                "5. Blue Square",
                                "6. Indigo Square",
                                "7. Violet Square",
                            ),
                            1..7,
                        ) { pick ->
                            whenever(context.dungeonLevel + pick eq 20) {
                                showTextAndWait(
                                    """
                                    You feel a slight tingle in your bodies, but
                                    otherwise, nothing happens.
                                    """,
                                )
                            } otherwise {
                                showTextAndWait(
                                    """
                                    With a horrible scream a shape of pure color forms
                                    in thin air before you!
                                    """,
                                )
                                encounter(Monsters.rainbowDemon, mandatory = true)
                            }
                        }
                    }
                }

                event(DungeonEvents.fireShieldGuardian) {
                    script {
                        showTextAndWait(
                            """
                            As you enter this level you hear horrible, evil laughter.
                            Suddenly! Out of the shadows, something HIDEOUS emerges.
                            """,
                        )
                        encounter(Monsters.guardian, mandatory = true)
                    }
                }

                event(DungeonEvents.fireShield) {
                    script {
                        whenever(!achieved(Milestones.fireShield)) {
                            showTextAndWait(
                                """
                                After you have bested the foul thing, the dungeon is
                                strangely quiet. Upon entering this level, you find
                                the marvelous FIRE SHIELD on a pedestal! You take it.
                                """,
                            )
                            achieve(Milestones.fireShield)
                            mark(Story.hermit.trollTooth.fireShieldAcquired)
                        }
                    }
                }

                event(DungeonEvents.bracelets) {
                    script {
                        whenever(!achieved(Milestones.braceletsOfThun)) {
                            showTextAndWait(
                                """
                                This whole chamber shines with a bright white light!
                                On a table before you, lie the fabled Bracelets of
                                Thun! The glow fades as you put them in your backpack.
                                """,
                            )
                            achieve(Milestones.braceletsOfThun)
                            mark(Story.bracelets.thun.braceletsTaken)
                        }
                    }
                }

                event(DungeonEvents.nolrimTomb) {
                    script {
                        whenever(!Story.masterCrown.daggerTaken) {
                            showTextAndWait(
                                """
                                Nolrim's body rests on a stone slab in this room.
                                On his chest lies the dagger you seek. You put it
                                in your backpack.
                                """,
                            )
                            mark(Story.masterCrown.daggerTaken)
                        }
                    }
                }

                event(DungeonEvents.masterCrown) {
                    script {
                        whenever(!achieved(Milestones.masterCrown)) {
                            showTextAndWait(
                                """
                                FINALLY! In the hands of a long dead warrior, who is
                                placed like a statue by the east wall of this chamber,
                                is the Master Crown. The most powerful object in the
                                realm. It shines with an inner light.
                                """,
                            )
                            showTextAndWait("...but as you take it, THE WARRIOR COMES TO LIFE!!!")
                            encounter(Monsters.longDeadWarrior, mandatory = true)
                            achieve(Milestones.masterCrown)
                            mark(Story.masterCrown.crownTaken)
                        }
                    }
                }

                event(DungeonEvents.silverSanctum) {
                    script {
                        whenever(!Story.silverKey.noteFound) {
                            showTextAndWait(
                                """
                                This room shines like silver. You can see your mirror
                                image in the walls. After searching the room thoroughly,
                                you find nothing. No Silver Key, no treasure.
                                """,
                            )
                            showTextAndWait(
                                """
                                You curse the rogue who tricked you and are just
                                about to leave...
                                """,
                            )
                            showTextAndWait(
                                """
                                ...when you spot a paper of some kind in a crack in
                                the wall... You pull it out and read...
                                """,
                            )
                            showTextAndWait {
                                line("'Guild Master: This is")
                                line("the proof that I, ", character(Roles.masterThief).name, ",")
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
                    recurringUntilWon()
                    script {
                        showTextAndWait(
                            """
                            You bump into a hobgoblin patrol. With shiny and well
                            used scimitars, they attack you!
                            """,
                        )
                        encounter(Monsters.hobgoblin, mandatory = true)
                        whenever(chance(39)) {
                            showTextAndWait(
                                """
                                You find a scribbled note on the leader:

                                Password: 'RISH EKKIN'
                                """,
                            )
                        }
                    }
                }

                event(DungeonEvents.royalGuard1) {
                    script {
                        showTextAndWait(
                            "As you enter this room an army of hobgoblin skeletons attack you!",
                        )
                        encounter(Monsters.hobgoblinSkeleton, mandatory = true)
                    }
                }

                event(DungeonEvents.royalGuard2) {
                    script {
                        showTextAndWait(
                            """
                            As you enter this room an army of undead hobgoblins,
                            smelling of rotting flesh and brandishing gleaming
                            tridents attack you.
                            """,
                        )
                        encounter(Monsters.undeadHobgoblin, mandatory = true)
                    }
                }

                event(DungeonEvents.hobgoblinChief) {
                    script {
                        whenever(!achieved(Milestones.silverKey)) {
                            showTextAndWait(
                                """
                                You stand before a silk curtain. You hear someone
                                moving behind it. A voice calls:

                                - What's the password?
                                """,
                            )
                            choice.textInput(
                                prompt = "Password: ",
                                acceptedInputs = acceptedText("RISH EKKIN"),
                                maxLength = 15,
                                onAccepted = {
                                    showTextAndWait(
                                        """
                                        - Step inside and identify yourself. You do so.
                                        The hobgoblin chief eyes you suspiciously for a
                                        few seconds. Not hesitating a moment you attack.
                                        """,
                                    )
                                    encounter(Monsters.hobgoblinChief, mandatory = true)
                                },
                                onRejected = {
                                    writeText(
                                        """
                                        - GUARDS! GUARDS!

                                        (Hobgoblin guards rush to confront you.) {pause 2s}
                                        """,
                                    )
                                    encounter(Monsters.hobgoblin, mandatory = true)
                                    encounter(Monsters.hobgoblinChief, mandatory = true)
                                },
                            )
                            showTextAndWait(
                                """
                                You have killed the chief of the hobgoblin tribe.
                                The precious Silver Key hangs around his neck. With
                                trembling hands you pick it up!!!
                                """,
                            )
                            achieve(Milestones.silverKey)
                            mark(Story.silverKey.keyTaken)
                        }
                    }
                }

                event(DungeonEvents.orcNest) {
                    script {
                        showTextAndWait(
                            """
                            Some orcs watch a trapdoor in the floor here. As you
                            enter, they attack!
                            """,
                        )
                        encounter(Monsters.orc, mandatory = true)
                        showTextAndWait(
                            """
                            The last of the guardians lie dead. Suddenly, more orcs
                            rush out of their sleeping quarters, disturbed by the noise.
                            """,
                        )
                        encounter(Monsters.orc, mandatory = true)
                        encounter(Monsters.orc, mandatory = true)
                        showTextAndWait("You have slain the orcs.")
                    }
                }

                event(DungeonEvents.tammysPrison) {
                    recurringUntil(Story.peasant.wife.rescued)
                    script {
                        whenever(Story.peasant.wife.accepted and !Story.peasant.wife.rescued) {
                            showTextAndWait(
                                """
                                The peasant's wife is in this room. You untie her
                                bonds and let her join your party!
                                """,
                            )
                            mark(Story.peasant.wife.rescued)
                        }
                    }
                }

                event(DungeonEvents.erethorPriests) {
                    recurringUntilWon()
                    script {
                        showTextAndWait(
                            "You are spotted by some priests. They attack you with blind fury.",
                        )
                        encounter(Monsters.erethorPriest, mandatory = true)
                    }
                }

                event(DungeonEvents.luteChamber) {
                    script {
                        whenever(!Story.erethor.lute.luteTaken) {
                            showTextAndWait(
                                """
                                This is the altar chamber of the temple. Hooded shapes
                                kneel at the blood-stained altar at the end of this chamber.
                                """,
                            )
                            showTextAndWait("You rush to attack the priests...")
                            showTextAndWait("They quickly turn around and draw their daggers.")
                            showTextAndWait(
                                """
                                The acolyte kneels before the altar in silent prayer
                                while the others form a circle around him to protect him.
                                """,
                            )
                            playSound(Sounds.Explore.explosion)
                            showTextAndWait(
                                """
                                Suddenly there is an ear-splitting bang and a ghastly
                                creature emerges from the shadows of this uncanny sanctuary.
                                """,
                            )
                            encounter(Monsters.erethor, mandatory = true)
                            encounter(Monsters.erethorPriest, mandatory = true)
                            showTextAndWait("More priests rush to attack you...")
                            encounter(Monsters.erethorPriest, mandatory = true)
                            showTextAndWait(
                                """
                                The last priest lies dead. You stand still for a moment,
                                and then start searching the hall for the Lute.
                                """,
                            )
                            party.randomMember(MemberChoiceFilter.AliveOnly) { surprised ->
                                showTextAndWait(
                                    flowText(
                                        partyName(surprised),
                                        " suddenly hears something behind ",
                                        pronoun(surprised, PronounCase.Object),
                                        "!",
                                    ),
                                )
                            }
                            showTextAndWait(
                                """
                                You scream in terror when you see the source of that sound!
                                ERETHOR HAS COME TO LIFE ONCE AGAIN!!!
                                """,
                            )
                            encounter(Monsters.erethor, mandatory = true)
                            showTextAndWait(
                                """
                                This time Erethor remains dead. His flesh blisters and
                                bursts. A rotten smell spreads throughout the hall.
                                """,
                            )
                            party.randomMember(MemberChoiceFilter.AliveOnly) { finder ->
                                showTextAndWait(
                                    flowText(partyName(finder), " finds the Lute on a pedestal, and you are all glad to leave this place."),
                                )
                            }
                            mark(Story.erethor.lute.luteTaken)
                        }
                    }
                }

                // Every dungeon draws the levels without a fixed event from this
                // weighted mix, unless its scenario names a mix of its own.
                randomEvents(
                    DungeonEvents.findObject to 1,
                    DungeonEvents.gold to 1,
                    DungeonEvents.weakEncounter to 2,
                    DungeonEvents.deepEncounter to 1,
                    DungeonEvents.nothingSpecial to 1,
                    DungeonEvents.emptyLevel to 1,
                    DungeonEvents.goldChest to 1,
                    DungeonEvents.torches to 1,
                )

                // Scenarios map a dungeon id to depth and the fixed levels that
                // override the random mix.
                scenario(Dungeons.randomCave) {
                    name("Random Cave")
                }

                scenario(Dungeons.fireShieldCave) {
                    name("Fire Shield Cave")
                    levels(20)
                    level(19, DungeonEvents.fireShieldGuardian)
                    level(20, DungeonEvents.fireShield)
                }

                scenario(Dungeons.thunTemple) {
                    name("Thun Temple")
                    levels(18)
                    level(4, DungeonEvents.thunRiddle1)
                    level(8, DungeonEvents.thunRiddle2)
                    level(12, DungeonEvents.thunRiddle3)
                    level(18, DungeonEvents.bracelets)
                }

                scenario(Dungeons.reebarTomb) {
                    name("Reebar Tomb")
                    levels(20)
                    level(12, DungeonEvents.spectrumMessage)
                    level(13..19, DungeonEvents.colorRiddle)
                    level(20, DungeonEvents.nolrimTomb)
                }

                scenario(Dungeons.masterCrownDungeon) {
                    name("Master Crown Dungeon")
                    levels(10)
                    level(10, DungeonEvents.masterCrown)
                }

                scenario(Dungeons.silverSanctum) {
                    name("Silver Sanctum")
                    levels(20)
                    level(20, DungeonEvents.silverSanctum)
                }

                scenario(Dungeons.hobgoblinStronghold) {
                    name("Hobgoblin Stronghold")
                    levels(22)
                    level(3, 6, 9, 14, 17, 19, event = DungeonEvents.hobgoblinPatrol)
                    level(20, DungeonEvents.royalGuard1)
                    level(21, DungeonEvents.royalGuard2)
                    level(22, DungeonEvents.hobgoblinChief)
                }

                scenario(Dungeons.ogreCave) {
                    name("Ogre Cave")
                    levels(7)
                    level(6, DungeonEvents.orcNest)
                    level(7, DungeonEvents.tammysPrison)
                }

                scenario(Dungeons.erethorTemple) {
                    name("Erethor Temple")
                    levels(12)
                    level(3, 7, event = DungeonEvents.erethorPriests)
                    level(12, DungeonEvents.luteChamber)
                }
            }

            // Speak describes the generic conversation system plus the current
            // game's chitchat, questions, rumors, and special question actions.
            speak {
                // Standard conversation controls NPC selection, visit tracking,
                // branch selection, menu shape, chitchat, and rumor policy.
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
                        rumor()
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

                    rumor {
                        usage = RumorUsagePolicy.OncePerConversationThenFallback
                        source = RumorSourcePolicy.StandardTownRumors
                    }

                    prompts {
                        chooseNpc = literalText("Speak with whom?", "")
                        mainMenu {
                            line("Chatting with ", npcName, ":")
                            line("")
                        }
                    }
                }

                // Text tables hold reusable conversation prose by subject, job,
                // topic, and rumor id. Dialogue branches below handle story arcs.
                textTables {
                    screen(T.Speak.noNpc, flowText("No one is here!"))
                    screen(T.Speak.noRumor, flowText("- Sorry. There's nothing new I can tell you."))

                    chitchatPrompt(ChitchatSubject.Well) {
                        female(flowText("- You look well today, miss..."))
                        male(flowText("- You look well today, sir..."))
                    }
                    chitchatPrompt(ChitchatSubject.Weather) {
                        any(flowText("- Nice weather we're having..."))
                    }
                    chitchatPrompt(ChitchatSubject.Health) {
                        female(flowText("- Is all well with you, miss?"))
                        male(flowText("- Is all well with you, sir?"))
                    }

                    chitchatReplies(ChitchatSubject.Well) {
                        job(NpcJobs.merchant, flowText("- Thanks, and the same to you!"))
                        job(NpcJobs.ranger, flowText("- I do? Really? Are you joking?"))
                        job(NpcJobs.fighter, flowText("- Thank you. My health is slowly returning after a hard battle."))
                        job(NpcJobs.wizard, flowText("- Aha?"))
                        job(NpcJobs.priest, flowText("- You too, my son."))
                        job(NpcJobs.thief, flowText("- What? No! Shhh! They can hear you."))
                        job(NpcJobs.peasant, flowText("- Thank you! You too."))
                        job(NpcJobs.bard, flowText("- That inspires me. I'll compose a song about you!"))
                        job(NpcJobs.drifter, flowText("- YEAH? Take that!"))
                        job(NpcJobs.halfOrc, flowText("- ME NOT LOOK WELL! ME BIG AND UGLY!"))
                        job(NpcJobs.halfElf, flowText("- Oh..."))
                        job(NpcJobs.smith, flowText("- I thank thee, my lad!"))
                        job(NpcJobs.bargirl, flowText("- You too, honey!"))
                        job(NpcJobs.innkeeper, flowText("- The same to you."))
                        job(NpcJobs.tavernkeeper, flowText("- You've had too much to drink, boy?"))
                        job(NpcJobs.hermit, flowText("- It is written in the scrolls of Skelos..."))
                    }
                    chitchatReplies(ChitchatSubject.Weather) {
                        job(NpcJobs.merchant, flowText("- I think it will rain."))
                        job(NpcJobs.ranger, flowText("- Not fine weather for tracking, mind you!"))
                        job(NpcJobs.fighter, flowText("- Yeah! But my armor gets quite rusty."))
                        job(NpcJobs.wizard, flowText("- Really?"))
                        job(NpcJobs.priest, flowText("- The gods have been kind to us, my son!"))
                        job(NpcJobs.thief, flowText("- Perfect weather for stealing!"))
                        job(NpcJobs.peasant, flowText("- Yeah, the crops grow better than ever."))
                        job(NpcJobs.bard, flowText("- This rain really inspires me. (He starts singing)."))
                        job(NpcJobs.drifter, flowText("- Hehehehe..."))
                        job(NpcJobs.halfOrc, flowText("- YEAH! NICE'N SLIMY!"))
                        job(NpcJobs.halfElf, flowText("- I find it depressing."))
                        job(NpcJobs.smith, flowText("- Well, kinda good."))
                        job(NpcJobs.bargirl, flowText("- Let's not talk about the weather, honey?"))
                        job(NpcJobs.innkeeper, flowText("- Well. Can't complain."))
                        job(NpcJobs.tavernkeeper, flowText("- Want a drink, sonny?"))
                        job(NpcJobs.hermit, flowText("- Yes, a day like this is destined to come!"))
                    }
                    chitchatReplies(ChitchatSubject.Health) {
                        job(NpcJobs.merchant, flowText("- All's well, thanks."))
                        job(NpcJobs.ranger, flowText("- I'm fine."))
                        job(NpcJobs.fighter, flowText("- Yep."))
                        job(NpcJobs.wizard, flowText("- Health... yes."))
                        job(NpcJobs.priest, flowText("- All is well, my son."))
                        job(NpcJobs.thief, flowText("- Yeh, quite nice."))
                        job(NpcJobs.peasant, flowText("- Are you kidding?"))
                        job(NpcJobs.bard, flowText("- Yes. I will sing a song of health for you!"))
                        job(NpcJobs.drifter, flowText("(He chuckles under his breath)."))
                        job(NpcJobs.halfOrc, flowText("- ME JUST FINE!"))
                        job(NpcJobs.halfElf, flowText("- Very well, thanks."))
                        job(NpcJobs.smith, flowText("- Fine, laddie!"))
                        job(NpcJobs.bargirl, flowText("- Sure, honey!"))
                        job(NpcJobs.innkeeper, flowText("- Well..."))
                        job(NpcJobs.tavernkeeper, flowText("- Just fine. Do you want something to drink?"))
                        job(NpcJobs.hermit, flowText("- Oh. Meneth Sabaoth."))
                    }

                    questionReplies(QuestionTopic.People) {
                        job(NpcJobs.merchant, flowText("- Well, I don't know that many people."))
                        job(NpcJobs.ranger, flowText("- No I'm a lone ranger."))
                        job(
                            NpcJobs.fighter,
                            flowText(
                                "- Well, I met this ",
                                character(Roles.banditsQuestGiver).job,
                                " who seeks revenge on a band of bandits.",
                            ),
                        )
                        job(NpcJobs.wizard, flowText("- OH! Well... not really."))
                        job(
                            NpcJobs.priest,
                            flowText(
                                "- I've heard of a ",
                                character(Roles.hermitTrollTooth).job,
                                " who lives in the swamps by ",
                                town(Towns.ollk).name,
                                ". Perhaps he can help you.",
                            ),
                        )
                        job(NpcJobs.thief, flowText("- No. Sorry. Dunno."))
                        job(NpcJobs.peasant, flowText("- I think it's time someone found the Foot of Jazzy and defeated Ydarg!"))
                        job(
                            NpcJobs.bard,
                            flowText(
                                "- Rumor has it that a ",
                                character(Roles.fighterIntro).job,
                                " called ",
                                character(Roles.fighterIntro).name,
                                " has a mission for you.",
                            ),
                        )
                        job(NpcJobs.drifter, flowText("- Don't know anyone."))
                        job(NpcJobs.halfOrc, flowText("- ME ALONE AND STRONG!"))
                        job(NpcJobs.halfElf, flowText("- There are many huts in the Wilderness."))
                        job(NpcJobs.smith, flowText("- Sorry, can't help ya."))
                        job(NpcJobs.bargirl, flowText("- Well..."))
                        job(NpcJobs.innkeeper, flowText("- Want to meet interesting travelers? Try the inn or the tavern."))
                        job(NpcJobs.tavernkeeper, flowText("- Don't know about anyone in particular."))
                        job(NpcJobs.hermit, flowText("- Many faces shine through the mist."))
                    }
                    questionReplies(QuestionTopic.Magic) {
                        job(NpcJobs.merchant, flowText("- It is said that the Master Crown is the most powerful object in the realm."))
                        job(NpcJobs.ranger, flowText("- You will need five magical items to defeat Baron Ydarg."))
                        job(NpcJobs.fighter, flowText("- What? I don't believe in magic..."))
                        job(NpcJobs.wizard, flowText("- Magic... Yes, Magic."))
                        job(NpcJobs.priest, flowText("- Yes, my son, but we are all puny beings compared to the gods."))
                        job(NpcJobs.thief, flowText("- I know nothing of it."))
                        job(NpcJobs.peasant, flowText("- I don't know much about magic. Gems often have magical properties, though."))
                        job(NpcJobs.bard, flowText("- Magic is the subject of many of my songs. Ballad of the Fire Shield, for instance."))
                        job(NpcJobs.drifter, flowText("- GO AWAY!"))
                        job(NpcJobs.halfOrc, flowText("- ME HAVE TRUSTY SWORD! ME NOT CARE 'BOUT MAGIK!"))
                        job(NpcJobs.halfElf, flowText("- Yes, my people were created from the magic of Mother Earth."))
                        job(NpcJobs.smith, flowText("- Rumor has it that some ogres have the Foot of Jazzy, a powerful item."))
                        job(NpcJobs.bargirl, flowText("- I know nothing about that."))
                        job(
                            NpcJobs.innkeeper,
                            flowText("- Magic? Well, they say that Baron Ydarg can be defeated only with the Silver Key."),
                        )
                        job(NpcJobs.tavernkeeper, flowText("- Sorry. Can't help you."))
                        job(NpcJobs.hermit, flowText("- The Crown, the Shield, the Bracelets and the Foot are all you need."))
                    }
                    questionReplies(QuestionTopic.Lodging) {
                        job(NpcJobs.merchant, flowText("- Well. The city inn is a good try."))
                        job(NpcJobs.ranger, flowText("- I sleep under the open skies. Just beware of bandits if you do this!"))
                        job(NpcJobs.fighter, flowText("- Well. I usually stay at the inn when I'm in town."))
                        job(NpcJobs.wizard, flowText("- Oh??? Eternal sleep!"))
                        job(NpcJobs.priest, flowText("- I hire a room at the inn to meditate!"))
                        job(NpcJobs.thief, flowText("- I sleep in the shadows."))
                        job(NpcJobs.peasant, flowText("- I sleep in my own room at my farm."))
                        job(NpcJobs.bard, flowText("- Imagine the beauty and danger to camp in the Wilderness these days."))
                        job(NpcJobs.drifter, flowText("- Look, why don't you just _GO AWAY_???"))
                        job(NpcJobs.halfOrc, flowText("- ME NEVER GET TIRED!"))
                        job(NpcJobs.halfElf, flowText("- I rent a room at the inn!"))
                        job(NpcJobs.smith, flowText("- Well, sleeping at the inn is often nicer than in the Wilderness."))
                        job(NpcJobs.innkeeper, flowText("- Well. My inn is the best in the realm!"))
                        job(NpcJobs.tavernkeeper, flowText("- I dunno, try my brother's inn."))
                        job(NpcJobs.hermit, flowText("- Yes. You are indeed the one..."))
                    }

                    rumorTable(RumorTables.standardTown) {
                        rumor(flowText("Baron Ydarg's castle Zadraz is hidden in a forest to the southwest."))
                        rumor(flowText("The Silver Key unlocks the gate to Ydarg's dimension."))
                        rumor(flowText("You will need the Fire Shield to defeat Baron Ydarg."))
                        rumor(flowText("You will need the Master Crown to defeat Baron Ydarg."))
                        rumor(flowText("You will need the Bracelets of Thun to resist the most evil of all spells..."))
                        rumor(flowText("The scythe is a useless weapon."))
                        rumor(flowText("Some say that you need gems to enter the old Temple of Thun."))
                        rumor(flowText("Seek the ", character(Roles.hermitTrollTooth).job, " if you want to know of the Fire Shield"))
                        rumor(flowText("The ", character(Roles.hermitTrollTooth).job, " lives in the swamp."))
                        rumor(flowText("Always post a guard when you camp in the Wilderness."))
                        rumor(flowText("The Foot of Jazzy is one of the objects Baron Ydarg can't control!"))
                        rumor(flowText("Skullweed plant grows deep in the swamp. It is a powerful reagent."))
                        rumor(Rumors.banditsQuestGiver) {
                            excludeWhen(Story.garth.bandits.recovered, Story.garth.bandits.closed)
                            text(
                                flowText(
                                    "A ",
                                    character(Roles.banditsQuestGiver).job,
                                    " named ",
                                    character(Roles.banditsQuestGiver).name,
                                    " will pay you for killing some bandits.",
                                ),
                            )
                        }
                        rumor(Rumors.rangerQuestGiver) {
                            text(
                                flowText(
                                    "A ",
                                    character(Roles.rangersQuestGiver).job,
                                    " called ",
                                    character(Roles.rangersQuestGiver).name,
                                    " seeks a brave party of adventurers.",
                                ),
                            )
                        }
                        rumor(flowText("It is always wise to haggle."))
                        rumor(flowText("Cthulhu will rise again!"))
                        rumor(flowText("Baron Ydarg's castle in this dimension lies far to the southwest."))
                        rumor(flowText("Exploring caves can be profitable."))
                        rumor(flowText("Flee from all beasts that you are uncertain of."))
                        rumor(flowText("Before meddling in the business of a god, be a demigod yourself."))
                        rumor(flowText("Jazzy is a mighty lesser god or a demon."))
                        rumor(flowText("Baron Ydarg's castle Zadraz is a gate to another dimension."))
                        rumor(flowText("Never meddle in the affairs of wizards, for they are subtle, and swift to anger."))
                        rumor(flowText("Magical objects can be a great aid in battle."))
                        rumor(flowText("Seek the holy hand grenade!"))
                        rumor(
                            Rumors.crownSeer,
                            flowText(
                                "A ",
                                character(Roles.crownSeer).job,
                                " named ",
                                character(Roles.crownSeer).name,
                                " knows of the Crown. He lives ",
                                character(Roles.crownSeer).location,
                                "!",
                            ),
                        )
                        rumor(flowText(town(Towns.murn).name, " is situated somewhere near ", town(Towns.murn).near(), "!"))
                        rumor(Rumors.templeOfThunSeer) {
                            text(
                                flowText(
                                    "Seek ",
                                    character(Roles.templeOfThunSeer).name,
                                    " if you search the Temple of Thun! He lives ",
                                    character(Roles.templeOfThunSeer).location,
                                ),
                            )
                        }
                        rumor(flowText("The more powerful creatures, the more experience."))
                        rumor(flowText("A good sword beats four aces!"))
                        rumor(flowText("You need FIVE items, no less, no more to kill Ydarg!"))
                        rumor(flowText(town(Towns.wilor).name, " is situated at ", town(Towns.wilor).near(radius = 0), "."))
                        rumor(flowText(town(Towns.ollk).name, " is situated somewhere near ", town(Towns.ollk).near(), "!"))
                    }
                }

                // Question actions attach scripted side effects to a generic
                // question/topic pair while keeping the speak runner reusable.
                questionAction(QuestionTopic.Lodging, NpcJobs.bargirl) {
                    localInt(party.leader.level * 50, "bargirl_lodging_price") { price ->
                        choice.yesNo(
                            literalText {
                                line("- Would you like to spend an hour upstairs for ", price, " gold coins?")
                                line("")
                                line("(Y)es")
                                line("(N)o")
                            },
                            yes = {
                                pay(
                                    price,
                                    broke = {
                                        writeText("- No money, eh? Don't bother to come back! {pause 2s}")
                                    },
                                    ok = {
                                        forEachMember {
                                            whenever(it.hp gt 1) {
                                                it.hp -= 1
                                            }
                                        }
                                        writeText(
                                            """
                                            You spend a very exhausting hour upstairs.

                                            (PERVERT!) ;-) {pause 2s}
                                            """,
                                        )
                                    },
                                )
                            },
                        )
                    }
                }
            }

            // Towns are world locations with coordinates, tax policy, and the
            // services present in that settlement.
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

            // Town system data is shared by all towns: menu order, entry tax,
            // leave prompts, service scene ids, and opening-hour policy.
            townSystem {
                leavePrompts(EquipmentCommand.ReadyWeapon, EquipmentCommand.ReadyArmor)
                // The street view behind the town menu and the entry tax.
                image = Images.Scene.towneMainstreet
                // Entry tax applies before town entry for qualifying parties.
                // The bindings are semantic actions used by the town prompt.
                entryTax {
                    amount = Formulas.townEntryTax
                    pay = inputKey("P", label = "Pay to Enter")
                    leave = inputKey("L", label = "Leave")
                }
                // Menu order is authored separately from each town's service list
                // so missing services can be hidden consistently.
                menu {
                    church()
                    smith()
                    inn()
                    merchant()
                    tavern()
                    guild()
                    leave()
                }

                // Building services connect menu entries to scenes and hours.
                // Closed wording belongs to authored service text, not menu suffixes.
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
                        openHours(opensAt = 6, closesAt = 1)
                    }
                    guild {
                        scene = Scenes.Towne.guild
                        alwaysOpen()
                    }
                }
            }

            // Characters are NPC records. Placement decides where they can be
            // met; roles are story handles used by dialogue and dynamic text.
            characters {
                randomPlacementPolicy {
                    wilderness(8..57, 8..27) {
                        replaceX(48, 49)
                    }
                    townServices(Towns.thord, Buildings.tavern, Buildings.inn)
                    townServices(Towns.ollk, Buildings.tavern, Buildings.inn)
                    townServices(Towns.wilor, Buildings.tavern, Buildings.inn)
                    townServices(Towns.murn, Buildings.tavern, Buildings.inn)
                }

                character(Npcs.garth, "Garth", NpcJobs.merchant) {
                    fixed(Towns.thord.tavern)
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

                character(Npcs.groth, "Groth", NpcJobs.fighter) {
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
                    fixedNear(Towns.ollk, gridOffset(0, -5), requiredTerrain = Terrain.swamp)
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

                // The original NPC.DAT keeps these trailing merchant records at
                // 0,0 as inactive table slots. Model that as an explicit absence
                // rather than a playable wilderness location.
                character(Npcs.alex, "Alex", NpcJobs.merchant) {
                    unplaced()
                }

                character(Npcs.galdon, "Galdon", NpcJobs.merchant) {
                    unplaced()
                }

                character(Npcs.kosch, "Kosch", NpcJobs.merchant) {
                    unplaced()
                }

                character(Npcs.ufarsin, "Ufarsin", NpcJobs.merchant) {
                    unplaced()
                }
            }

            // Monsters supply encounter stats and presentation tags. Tier drives
            // random encounter pools; fixed boss fights can omit a tier.
            monsters {
                monster(Monsters.kobold) {
                    name = "Kobold"
                    count = 2..8
                    hitPoints = 30..50
                    weaponSkill = 35
                    tier = EncounterTiers.weak
                    encounterImage(Images.Encounter.goblinoid)
                }

                monster(Monsters.orc) {
                    name = "Orc"
                    count = 2..5
                    hitPoints = 50..99
                    weaponSkill = 50
                    tier = EncounterTiers.weak
                    encounterImage(Images.Encounter.goblinoid)
                }

                monster(Monsters.troll) {
                    name = "Troll"
                    count = 1..3
                    hitPoints = 99..199
                    weaponSkill = 55
                    tier = EncounterTiers.weak
                    encounterImage(Images.Encounter.brute)
                }

                monster(Monsters.warrior) {
                    name = "Warrior"
                    count = 1..4
                    hitPoints = 50..99
                    weaponSkill = 60
                    tier = EncounterTiers.weak
                    encounterImage(Images.Encounter.humanoidMagic)
                }

                monster(Monsters.goblin) {
                    name = "Goblin"
                    count = 2..7
                    hitPoints = 50..80
                    weaponSkill = 30
                    tier = EncounterTiers.weak
                    encounterImage(Images.Encounter.goblinoid)
                }

                monster(Monsters.wildDog) {
                    name = "Wild Dog"
                    count = 1..3
                    hitPoints = 15..40
                    weaponSkill = 60
                    tier = EncounterTiers.weak
                    encounterImage(Images.Encounter.beast)
                }

                monster(Monsters.knight) {
                    name = "Knight"
                    count = 1..5
                    hitPoints = 25..99
                    weaponSkill = 75
                    tier = EncounterTiers.weak
                    encounterImage(Images.Encounter.humanoidMagic)
                }

                monster(Monsters.bandit) {
                    name = "Bandit"
                    count = 2..6
                    hitPoints = 50..99
                    weaponSkill = 50
                    tier = EncounterTiers.weak
                    encounterImage(Images.Encounter.humanoidMagic)
                }

                monster(Monsters.hobgoblin) {
                    name = "Hobgoblin"
                    count = 2..5
                    hitPoints = 20..70
                    weaponSkill = 40
                    spellPower = 5
                    tier = EncounterTiers.weak
                    encounterImage(Images.Encounter.goblinoid)
                }

                monster(Monsters.zombie) {
                    name = "Zombie"
                    count = 2..4
                    hitPoints = 70..70
                    weaponSkill = 65
                    spellPower = 5
                    tier = EncounterTiers.weak
                    encounterImage(Images.Encounter.undead)
                }

                monster(Monsters.stirge) {
                    name = "Stirge"
                    count = 6..8
                    hitPoints = 10..20
                    weaponSkill = 90
                    tier = EncounterTiers.wild
                    encounterImage(Images.Encounter.flying)
                }

                monster(Monsters.guardian) {
                    name = "Guardian"
                    count = 1..1
                    hitPoints = 250..350
                    weaponSkill = 80
                    encounterImage(Images.Encounter.boss)
                }

                monster(Monsters.norker) {
                    name = "Norker"
                    count = 1..1
                    hitPoints = 350..400
                    weaponSkill = 90
                    encounterImage(Images.Encounter.goblinoid)
                }

                monster(Monsters.voodooLord) {
                    name = "Voodoo Lord"
                    count = 1..1
                    hitPoints = 300..350
                    weaponSkill = 80
                    encounterImage(Images.Encounter.humanoidMagic)
                }

                monster(Monsters.archdevil) {
                    name = "Archdevil"
                    count = 1..1
                    hitPoints = 450..450
                    weaponSkill = 85
                    spellPower = 60
                    encounterImage(Images.Encounter.boss)
                }

                monster(Monsters.koboldHorde) {
                    name = "Kobold"
                    count = 6..8
                    hitPoints = 30..45
                    weaponSkill = 25
                    encounterImage(Images.Encounter.goblinoid)
                }

                monster(Monsters.rainbowDemon) {
                    name = "Rainbow Demon"
                    count = 1..1
                    hitPoints = 150..250
                    weaponSkill = 95
                    spellPower = 10
                    encounterImage(Images.Encounter.boss)
                }

                monster(Monsters.longDeadWarrior) {
                    name = "Long Dead Warrior"
                    count = 1..1
                    hitPoints = 400..500
                    weaponSkill = 80
                    encounterImage(Images.Encounter.undead)
                }

                monster(Monsters.ogre) {
                    name = "Ogre"
                    count = 4..7
                    hitPoints = 90..190
                    weaponSkill = 65
                    encounterImage(Images.Encounter.brute)
                }

                monster(Monsters.ogreLord) {
                    name = "Ogre Lord"
                    count = 1..1
                    hitPoints = 190..270
                    weaponSkill = 85
                    spellPower = 25
                    encounterImage(Images.Encounter.brute)
                }

                // Jazzy's minions came seven or eight at a time in the original. That many, right before
                // Jazzy himself, beat a normal party nearly every time at levels 15 to 24; five or six leave
                // the fight with Jazzy a hard but fair one.
                monster(Monsters.wingedThing) {
                    name = "Winged Thing"
                    count = 5..6
                    hitPoints = 150..200
                    weaponSkill = 95
                    encounterImage(Images.Encounter.flying)
                }

                monster(Monsters.jazzy) {
                    name = "Jazzy"
                    count = 1..1
                    hitPoints = 450..580
                    weaponSkill = 95
                    spellPower = 50
                    immune = true
                    encounterImage(Images.Encounter.boss)
                }

                monster(Monsters.ridingHobgoblin) {
                    name = "Riding Hobgoblin"
                    count = 4..7
                    hitPoints = 30..70
                    weaponSkill = 40
                    spellPower = 10
                    encounterImage(Images.Encounter.goblinoid)
                }

                monster(Monsters.hobgoblinChief) {
                    name = "Hobgoblin Chief"
                    count = 1..1
                    hitPoints = 150..230
                    weaponSkill = 100
                    spellPower = 40
                    encounterImage(Images.Encounter.goblinoid)
                }

                monster(Monsters.hobgoblinSkeleton) {
                    name = "Hobgoblin Skeleton"
                    count = 5..6
                    hitPoints = 50..60
                    weaponSkill = 90
                    encounterImage(Images.Encounter.undead)
                }

                monster(Monsters.undeadHobgoblin) {
                    name = "Undead Hobgoblin"
                    count = 5..5
                    hitPoints = 60..70
                    weaponSkill = 95
                    encounterImage(Images.Encounter.undead)
                }

                monster(Monsters.cryptHorror) {
                    name = "Crypt Horror"
                    count = 3..4
                    hitPoints = 100..200
                    weaponSkill = 60
                    spellPower = 5
                    encounterImage(Images.Encounter.undead)
                }

                monster(Monsters.nightGaunt) {
                    name = "Night Gaunt"
                    count = 1..2
                    hitPoints = 200..350
                    weaponSkill = 90
                    spellPower = 10
                    encounterImage(Images.Encounter.flying)
                }

                monster(Monsters.thyraga) {
                    name = "Thyraga"
                    count = 1..2
                    hitPoints = 250..300
                    weaponSkill = 90
                    encounterImage(Images.Encounter.boss)
                }

                monster(Monsters.liche) {
                    name = "Liche"
                    count = 1..1
                    hitPoints = 300..400
                    weaponSkill = 85
                    encounterImage(Images.Encounter.undead)
                }

                monster(Monsters.baronYdarg) {
                    name = "Baron Ydarg"
                    count = 1..1
                    hitPoints = 500..1_000
                    weaponSkill = 90
                    spellPower = 5
                    immune = true
                    encounterImage(Images.Encounter.boss)
                }

                monster(Monsters.gnoll) {
                    name = "Gnoll"
                    count = 2..2
                    countMaxLevelDivisor = 2
                    hitPoints = 60..80
                    weaponSkill = 70
                    encounterImage(Images.Encounter.goblinoid)
                }

                // Erethor's temple ends in four fights in a row. With priests three or four at a time and Erethor at
                // 600 to 900 hit points, whose spells strike for a fifth of that, a level-24 party at full strength
                // survived it 2 times in 30; with these numbers it survives about 3 times in 4.
                monster(Monsters.erethorPriest) {
                    name = "Erethor Priest"
                    count = 2..3
                    hitPoints = 80..99
                    weaponSkill = 70
                    spellPower = 50
                    encounterImage(Images.Encounter.humanoidMagic)
                }

                monster(Monsters.erethor) {
                    name = "Erethor"
                    count = 1..1
                    hitPoints = 400..600
                    weaponSkill = 80
                    spellPower = 10
                    encounterImage(Images.Encounter.boss)
                }

                monster(Monsters.wanderMage) {
                    name = "Wander Mage"
                    count = 3..4
                    hitPoints = 50..60
                    weaponSkill = 20
                    spellPower = 80
                    tier = EncounterTiers.wild
                    encounterImage(Images.Encounter.humanoidMagic)
                }

                monster(Monsters.hillGiant) {
                    name = "Hill Giant"
                    count = 1..2
                    hitPoints = 100..190
                    weaponSkill = 40
                    tier = EncounterTiers.wild
                    encounterImage(Images.Encounter.brute)
                }

                monster(Monsters.wizard) {
                    name = "Wizard"
                    count = 2..4
                    hitPoints = 30..50
                    weaponSkill = 40
                    spellPower = 70
                    tier = EncounterTiers.wild
                    encounterImage(Images.Encounter.humanoidMagic)
                }

                monster(Monsters.druid) {
                    name = "Druid"
                    count = 3..4
                    hitPoints = 50..60
                    weaponSkill = 60
                    spellPower = 60
                    tier = EncounterTiers.wild
                    encounterImage(Images.Encounter.humanoidMagic)
                }

                monster(Monsters.saberToothTiger) {
                    name = "Saber Tooth Tiger"
                    count = 1..2
                    hitPoints = 100..120
                    weaponSkill = 95
                    tier = EncounterTiers.wild
                    encounterImage(Images.Encounter.beast)
                }

                monster(Monsters.headHunter) {
                    name = "Head Hunter"
                    count = 3..5
                    hitPoints = 60..80
                    weaponSkill = 65
                    tier = EncounterTiers.wild
                    encounterImage(Images.Encounter.humanoidMagic)
                }

                monster(Monsters.skeleton) {
                    name = "Skeleton"
                    count = 3..6
                    hitPoints = 20..60
                    weaponSkill = 70
                    tier = EncounterTiers.wild
                    encounterImage(Images.Encounter.undead)
                }

                monster(Monsters.vampireBat) {
                    name = "Vampire Bat"
                    count = 7..8
                    hitPoints = 10..20
                    weaponSkill = 55
                    tier = EncounterTiers.wild
                    encounterImage(Images.Encounter.flying)
                }

                monster(Monsters.giantSnake) {
                    name = "Giant Snake"
                    count = 1..3
                    hitPoints = 40..90
                    weaponSkill = 60
                    tier = EncounterTiers.wild
                    encounterImage(Images.Encounter.vermin)
                }

                monster(Monsters.ghoul) {
                    name = "Ghoul"
                    count = 1..2
                    hitPoints = 100..150
                    weaponSkill = 35
                    spellPower = 10
                    tier = EncounterTiers.wild
                    encounterImage(Images.Encounter.undead)
                }

                monster(Monsters.spider) {
                    name = "Spider"
                    count = 5..8
                    hitPoints = 10..20
                    weaponSkill = 95
                    tier = EncounterTiers.wild
                    encounterImage(Images.Encounter.vermin)
                }

                monster(Monsters.hugeLizard) {
                    name = "Huge Lizard"
                    count = 3..5
                    hitPoints = 30..60
                    weaponSkill = 75
                    tier = EncounterTiers.wild
                    encounterImage(Images.Encounter.vermin)
                }

                monster(Monsters.drider) {
                    name = "Drider"
                    count = 1..5
                    hitPoints = 80..130
                    weaponSkill = 75
                    spellPower = 20
                    tier = EncounterTiers.wild
                    encounterImage(Images.Encounter.vermin)
                }

                monster(Monsters.xvart) {
                    name = "Xvart"
                    count = 2..2
                    hitPoints = 50..75
                    weaponSkill = 80
                    spellPower = 10
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.goblinoid)
                }

                monster(Monsters.jellyCube) {
                    name = "Jelly Cube"
                    count = 1..1
                    hitPoints = 200..300
                    weaponSkill = 50
                    spellPower = 2
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.vermin)
                }

                monster(Monsters.troglodyte) {
                    name = "Troglodyte"
                    count = 2..4
                    hitPoints = 40..60
                    weaponSkill = 70
                    spellPower = 5
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.goblinoid)
                }

                monster(Monsters.bugbear) {
                    name = "Bugbear"
                    count = 3..4
                    hitPoints = 40..50
                    weaponSkill = 70
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.goblinoid)
                }

                monster(Monsters.drowElf) {
                    name = "Drow Elf"
                    count = 2..4
                    hitPoints = 80..100
                    weaponSkill = 70
                    spellPower = 50
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.humanoidMagic)
                }

                monster(Monsters.phantom) {
                    name = "Phantom"
                    count = 2..3
                    hitPoints = 100..200
                    weaponSkill = 80
                    spellPower = 40
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.undead)
                }

                monster(Monsters.worg) {
                    name = "Worg"
                    count = 2..4
                    hitPoints = 80..100
                    weaponSkill = 60
                    spellPower = 20
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.beast)
                }

                monster(Monsters.rustMonster) {
                    name = "Rust Monster"
                    count = 1..1
                    hitPoints = 150..250
                    weaponSkill = 70
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.vermin)
                }

                monster(Monsters.owlbear) {
                    name = "Owlbear"
                    count = 2..3
                    hitPoints = 100..120
                    weaponSkill = 70
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.beast)
                }

                monster(Monsters.werewolf) {
                    name = "Werewolf"
                    count = 1..1
                    hitPoints = 300..300
                    weaponSkill = 90
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.beast)
                }

                monster(Monsters.hookHorror) {
                    name = "Hook Horror"
                    count = 2..3
                    hitPoints = 70..100
                    weaponSkill = 85
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.vermin)
                }

                monster(Monsters.minotaur) {
                    name = "Minotaur"
                    count = 1..3
                    hitPoints = 200..240
                    weaponSkill = 80
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.brute)
                }

                monster(Monsters.imp) {
                    name = "Imp"
                    count = 1..3
                    hitPoints = 100..120
                    weaponSkill = 90
                    spellPower = 30
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.boss)
                }

                monster(Monsters.svirfneblin) {
                    name = "Svirfneblin"
                    count = 2..3
                    hitPoints = 100..100
                    weaponSkill = 90
                    spellPower = 20
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.humanoidMagic)
                }

                monster(Monsters.yeti) {
                    name = "Yeti"
                    count = 1..1
                    hitPoints = 300..350
                    weaponSkill = 60
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.beast)
                }

                monster(Monsters.wight) {
                    name = "Wight"
                    count = 1..3
                    hitPoints = 90..120
                    weaponSkill = 95
                    spellPower = 35
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.undead)
                }

                monster(Monsters.wraith) {
                    name = "Wraith"
                    count = 2..4
                    hitPoints = 80..110
                    weaponSkill = 80
                    spellPower = 25
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.undead)
                }

                monster(Monsters.basilisk) {
                    name = "Basilisk"
                    count = 1..1
                    hitPoints = 240..300
                    weaponSkill = 60
                    spellPower = 20
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.vermin)
                }

                monster(Monsters.wyvern) {
                    name = "Wyvern"
                    count = 1..1
                    hitPoints = 350..450
                    weaponSkill = 80
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.flying)
                }

                monster(Monsters.tunnelWorm) {
                    name = "Tunnel Worm"
                    count = 1..1
                    hitPoints = 300..350
                    weaponSkill = 45
                    tier = EncounterTiers.deep
                    encounterImage(Images.Encounter.vermin)
                }

                // Sent by the sysop gods after the victim account; nothing else meets it.
                monster(Monsters.sysopSmiter) {
                    name = "Smiter of the Sysop Gods"
                    count = 1..1
                    hitPoints = 2_000..3_500
                    weaponSkill = 100
                    spellPower = 20
                    immune = true
                    encounterImage(Images.Encounter.boss)
                }
            }

            // Missions are wilderness triggers. A mission declares when it can
            // fire, the required state, and the scene that executes the result.
            missions {
                mission(Missions.erethorEntry) {
                    at(xy(6, 20))
                    requires(Story.erethor.lute.rumorTold)
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

            // Castle scenes are script roots named from the castle model above.
            // They keep the story beats authored while the castle runner stays generic.
            scene(Scenes.Castle.ydargAlreadyCompleted) {
                showBackdrop(Images.Backdrop.ydargCastle)
                writeText(
                    flowText(
                        """
                        The once mighty castle Zadraz is now nothing but a ruin. You have nothing else to do here.
                        """,
                    ),
                )
                waitInput("after the ruined castle")
            }

            scene(Scenes.Castle.ydargEntryBlocked) {
                showBackdrop(Images.Backdrop.ydargCastle)
                showBackdrop(Images.Castle.jaaraGate)
                writeText(
                    flowText(
                        """
                        At the gate of Zadraz, white mist thickens into the shape of a young woman.

                        - Do not be afraid, adventurers. I am Jaara, guardian of the gate. Baron Ydarg has imprisoned me here forever.
                        """,
                    ),
                )
                waitInput("after Jaara appears without the key")
                writeText(
                    flowText(
                        """
                        - You don't have the Silver Key. The gate will remain sealed.
                        """,
                    ),
                )
                waitInput("after Jaara blocks the gate")
            }

            scene(Scenes.Castle.ydargEntered) {
                showBackdrop(Images.Backdrop.ydargCastle)
                showBackdrop(Images.Castle.jaaraGate)
                writeText(
                    """
                    At the gate of Zadraz, white mist thickens into the shape of Jaara.

                    - Do not be afraid, adventurers. I am Jaara, guardian of the gate. Baron Ydarg has imprisoned me here forever.
                    """,
                )
                waitInput("after Jaara appears with the key")
                writeText(
                    """
                    You show the Silver Key to Jaara. Her eyes widen, then she smiles and waves towards the drawbridge.
                    """,
                )
                waitInput("after showing Jaara the key")
                writeText(
                    """
                    It falls to the ground with a resounding ***KRASH***. Jaara slowly fades away until nothing remains.
                    """,
                )
                waitInput("after Jaara opens the gate")
                writeText(
                    """
                    You enter.

                    The first door you enter slams shut behind you!
                    """,
                )
                waitInput("after entering castle Ydarg")
            }

            scene(Scenes.Castle.ydargCompleted) {
                showTextAndWait(
                    """
                    The last trapdoor slams shut behind you. You are high above the forest, on the roof of this fortress. Suddenly, a voice speaks:
                    """,
                )
                showTextAndWait(
                    """
                    - Fools! You have come far, but here ends your journey!

                    The night sky above lights up with green flashes, and Ydarg's minions rush to attack you!
                    """,
                )
                encounter(Monsters.cryptHorror, mandatory = true)
                encounter(Monsters.nightGaunt, mandatory = true)
                encounter(Monsters.thyraga, mandatory = true)
                encounter(Monsters.liche, mandatory = true)
                showTextAndWait(
                    """
                    Corpses of the nightmare terrors are strewn all around. A dark cloaked figure appears before you. He takes a broad step over the dead and dying, and gazes at you with cold glowing green eyes.
                    """,
                )
                showTextAndWait(
                    """
                    - So. You have completed the battle test. I must confess that you impress me. But now...

                    (He waves a pointed stick and energy begins to form in the air before you.)
                    """,
                )
                writeText(
                    """
                    A glowing energy sphere appears, and at a gesture from Ydarg's stick, it hurtles towards you!!! {pause 3s}
                    """,
                )
                showTextAndWait(
                    """
                    Suddenly! You feel someone else near. The four gray-clad mages raise their hands, and the sphere is absorbed by their hands!
                    """,
                )
                showTextAndWait(
                    """
                    - CURSES! Ydarg growls. - So this is what it has come to! You got here by cold steel, and that is the way you shall perish!
                    """,
                )
                writeText(
                    """
                    He pulls a huge trident from his robe and utters a single syllable. Without warning he appears a foot before you!!! {pause}

                    Let the battle commence!
                    """,
                )
                waitInput("before the battle with Ydarg")
                encounter(Monsters.baronYdarg, mandatory = true)
                showTextAndWait(
                    """
                    Ydarg's trident clatters to the ground before you. He shudders and falls over. As you rush to kill him he screams:
                    """,
                )
                showTextAndWait(
                    """
                    - It is not over! I shall see you in hell! In another world! A time to come! You have tainted Zadraz with your ancient items, but I will triumph again!!! Anew...
                    """,
                )
                writeText(
                    """
                    His cloak collapses, and when you tear it away, you reveal
                    nothing but...
                    """,
                )
                playSound(Sounds.Combat.monsterDissolve)
                pause(2_000)
                appendText(literalText("dust..."))
                waitInput("after Ydarg turns to dust")
                writeText(
                    """
                    CONGRATULATIONS!

                    You have banished Ydarg
                    from this dimension.
                    Please ask your SysOp to
                    reset the game.
                    """,
                )
                achieve(Milestones.ydargVictory)
                waitInput("after the congratulations")
                showTextAndWait(
                    literalText(
                        """
                        Be ready for:
                        HEROES OF RYLWEEN
                        - The second coming.
                        Soon online at a BBS
                        near you!
                        """,
                    ),
                )
                showTextAndWait(
                    literalText(
                        """
                        See Ya!!!

                             /Marcus
                        """,
                    ),
                )
            }

            // Town service scenes are normal scripts. Reusable helpers such as
            // shopService, speak, choice, and pay keep common mechanics shared.
            scene(Scenes.Towne.church) {
                clearMapWindow()
                showBackdrop(Images.Scene.church)

                loop {
                    clearText()
                    statusLine()
                    writeText(
                        flowText(
                            """
                            You enter the church of Dalina, a beautiful goddess. This is one of her two temples.
                            """,
                        ),
                    )

                    menu(
                        "(H)eal person",
                        "(R)aise dead",
                        "(E)nergize",
                        "(L)eave",
                    ) {
                        option('H') {
                            clearText()
                            choice.member(
                                literalText { line("Who? (1-", PartyText.memberCount, ")") },
                                filter = MemberChoiceFilter.AliveOnly,
                                accepting = MemberChoiceFilter.Any,
                            ) { m ->
                                whenever(m.dead) {
                                    playSound(Sounds.Town.thrownOut)
                                    writeText("- GET THAT CORPSE OUTTA HERE! (You are thrown out). {pause}")
                                    breakLoop("corpse exits the church service")
                                } otherwise {
                                    whenever(m.hp eq m.maxHp) {
                                        writeText {
                                            line(partyName(m), " needs no healing. {pause 1s}")
                                        }
                                    } otherwise {
                                        localInt((m.maxHp - m.hp) * 7) { price ->
                                            writeText(flowText("- That'll be ", price, " gold coins, please."))

                                            choice.payNoThanks(
                                                price,
                                                broke = {
                                                    writeText("You don't have enough gold! {pause 1s}")
                                                },
                                                paid = {
                                                    m.hp set m.maxHp
                                                    playSound(Sounds.Spell.heal)
                                                    writeText(
                                                        flowText(
                                                            "The priestess heals ",
                                                            pronoun(m, PronounCase.Object),
                                                            " fully. {pause 1s}",
                                                        ),
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        option('E') {
                            clearText()
                            choice.member(
                                literalText { line("Who? (1-", PartyText.memberCount, ")") },
                                filter = MemberChoiceFilter.SpellcasterOnly,
                                accepting = MemberChoiceFilter.Any,
                            ) { m ->
                                whenever(!m.spellcaster) {
                                    writeText("Not a spellcaster! {pause 1s}")
                                } otherwise {
                                    whenever(m.mp eq m.wisdom) {
                                        writeText("That character needs no spell points to be restored. {pause 2s}")
                                    } otherwise {
                                        localInt(m.level * (m.wisdom - m.mp) * 2) { price ->
                                            writeText(flowText("- That will be ", price, " gold coins, please."))

                                            choice.payNoThanks(
                                                price,
                                                broke = {
                                                    writeText("You don't have the gold! {pause 1s}")
                                                },
                                                paid = {
                                                    m.mp set m.wisdom
                                                    playSound(Sounds.Spell.mana)
                                                    writeText(
                                                        flowText(
                                                            "The priestess restores ",
                                                            pronoun(m, PronounCase.Possessive),
                                                            " energy. {pause 1s}",
                                                        ),
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        option('R') {
                            clearText()
                            choice.member(
                                literalText { line("Who? (1-", PartyText.memberCount, ")") },
                                filter = MemberChoiceFilter.DeadOnly,
                                accepting = MemberChoiceFilter.Any,
                            ) { m ->
                                whenever(!m.dead) {
                                    writeText(flowText("- ", partyName(m), " seems very alive at the moment. {pause}"))
                                } otherwise {
                                    localInt(m.level * 650) { price ->
                                        writeText(flowText("- That will be ", price, " gold coins, please."))

                                        choice.payNoThanks(
                                            price,
                                            broke = {
                                                writeText("You don't have the gold! {pause 1s}")
                                            },
                                            paid = {
                                                m.revive()
                                                playSound(Sounds.Spell.rebirth)
                                                writeText(flowText("The priestess raises ", pronoun(m, PronounCase.Object), ". {pause 1s}"))
                                            },
                                        )
                                    }
                                }
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

            // Shop services declare catalog pages, prices, haggle policy, and
            // prose; the runtime owns the common buy/sell loop and inventory edits.
            scene(Scenes.Towne.smith) {
                shopService(Buildings.smith) {
                    image = Images.Scene.smith
                    closed("Shop Closed.")
                    intro(flowText("Smith. The smith welcomes you to his small shop."))
                    menu("(B)uy", "(S)ell", "(L)eave")
                    goodbye("Well, bye then...")

                    interlude(chancePercent = 9) {
                        forceFirstUntil(Story.smith.beerOffer.offered)
                        offer(
                            flowText(
                                """
                            The smith offers you some beer, and the latest news from the realm.
                            """,
                            ),
                        )
                        accepted(flowText("You accept."))
                        accepted(
                            flowText(
                                """
                            After a while the smith leaves to get more beer.
                            """,
                            ),
                        )
                        accepted(
                            flowText(
                                """
                            When he returns, you ask him for the latest news...
                            """,
                            ),
                        )
                        declined(
                            flowText(
                                """
                                The smith is disappointed.

                                - Maybe another time, you tell him.

                                - Yeah, he answers.
                                """,
                            ),
                        )
                        runsRumorAfterAccept = true
                    }

                    buy {
                        prompt(
                            "Armor or Weapon?",
                            "",
                            "(A)rmor",
                            "(W)eapon",
                            "RETURN leaves.",
                        )
                        tooExpensive("Too expensive...")
                        cantCarry("You cannot carry more.")
                        deal("Deal!")
                        bought {
                            line("Bought ", itemName, ".")
                        }
                        singlePrice {
                            line("Price: ", price)
                            line("")
                        }

                        catalog('A', "Armor") {
                            intro("We have...", "")
                            item('1', Items.leather, label = "Leather")
                            item('2', Items.chainMail, label = "Chain Mail")
                            item('3', Items.plateMail, label = "Plate Mail")
                            item('4', Items.ringMail, label = "Ring Mail")
                            item('5', Items.elvenCloak, label = "Elven Cloak")
                            item('6', Items.mithrilMail, label = "Mithril Mail")
                        }

                        catalog('W', "Weapons") {
                            intro("We have...", "")
                            page {
                                nextPage('M', label = "More")
                                item('1', Items.sword, label = "Sword")
                                item('2', Items.axe, label = "Axe")
                                item('3', Items.bow, label = "Bow")
                                item('4', Items.club, label = "Club")
                                item('5', Items.halberd, label = "Halberd")
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
                        price = ShopPrices.formula(Formulas.sellPrice)
                        wrongKind("Not weapon or armor.")
                        offer {
                            line("Ok! Give ya ", price, "!")
                            line("")
                        }
                        declined("No thanks.")
                        sold("Sold!")
                        haggle {
                            bonus(rollGreaterThan = 90, addCurrentDivisor = 4)
                            bonus(rollGreaterThan = 75, addCurrentDivisor = 6)
                            rejectWhenRollBelow(76)
                            rollBonus(Formulas.haggleRollBonus)
                        }
                    }
                }
            }

            scene(Scenes.Towne.inn) {
                clearMapWindow()
                showBackdrop(Images.Scene.inn)

                loop {
                    localInt((party.leader.level * party.leader.level) * 15) { cost ->
                        writeText(
                            flowText {
                                paragraph(townNpcName(NpcJobs.innkeeper), ", the Innkeeper welcomes you.")
                                paragraph("Do you want supper, travelers?")
                            },
                        )
                        choice.yesNo(
                            "(Y)es",
                            "(N)o",
                            yes = {
                                pay(
                                    cost,
                                    broke = {
                                        writeText(flowText("Sorry, supper is ", cost, " gold. {pause 1s}"))
                                    },
                                    ok = {
                                        clearText()
                                        playSound(Sounds.Town.meal)
                                        healLivingMembers(6)
                                        writeText(
                                            """
                                            You are served a tasty dinner.
                                            Some of your health returns. {pause 2s}
                                            """,
                                        )
                                    },
                                )
                            },
                        )

                        clearText()
                        choice.yesNo(
                            "Anything to drink?",
                            "",
                            "(Y)es",
                            "(N)o",
                            yes = {
                                clearText()
                                menu(
                                    "What'll it be?",
                                    "",
                                    "(A) Ale         2 au",
                                    "(B) Mead        3 au",
                                    "(C) Wine        5 au",
                                    "(D) Whiskey     7 au",
                                    "(E) Fanta       1 au",
                                    "RETURN leaves.",
                                ) {
                                    option('A') {
                                        pay(
                                            lit(2),
                                            broke = {
                                                writeText("No money - No drink! {pause 1s}")
                                            },
                                            ok = {
                                                playSound(Sounds.Town.drink)
                                                writeText("Ahh...! {pause 1s}")
                                                healLivingMembers((lit(2) / 2) + 1)
                                            },
                                        )
                                    }
                                    option('B') {
                                        pay(
                                            lit(3),
                                            broke = {
                                                writeText("No money - No drink! {pause 1s}")
                                            },
                                            ok = {
                                                playSound(Sounds.Town.drink)
                                                writeText("Ahh...! {pause 1s}")
                                                healLivingMembers((lit(3) / 2) + 1)
                                            },
                                        )
                                    }
                                    option('C') {
                                        pay(
                                            lit(5),
                                            broke = {
                                                writeText("No money - No drink! {pause 1s}")
                                            },
                                            ok = {
                                                playSound(Sounds.Town.drink)
                                                writeText("Ahh...! {pause 1s}")
                                                healLivingMembers((lit(5) / 2) + 1)
                                            },
                                        )
                                    }
                                    option('D') {
                                        pay(
                                            lit(7),
                                            broke = {
                                                writeText("No money - No drink! {pause 1s}")
                                            },
                                            ok = {
                                                playSound(Sounds.Town.drink)
                                                writeText("Ahh...! {pause 1s}")
                                                healLivingMembers((lit(7) / 2) + 1)
                                            },
                                        )
                                    }
                                    option('E') {
                                        pay(
                                            lit(1),
                                            broke = {
                                                writeText("No money - No drink! {pause 1s}")
                                            },
                                            ok = {
                                                writeText("The other guests giggle. {pause 1s}")
                                                healLivingMembers((lit(1) / 2) + 1)
                                            },
                                        )
                                    }
                                }
                            },
                        )

                        clearText()
                        choice.yesNo(
                            "Are you in need of lodging?",
                            "",
                            "(Y)es",
                            "(N)o",
                            yes = {
                                writeText {
                                    line("A room is ", cost, " gold/night.")
                                    line("")
                                }

                                choice.takeOrLeave(
                                    literalText("(T)ake it", "(L)eave"),
                                    take = {
                                        pay(
                                            cost,
                                            broke = {
                                                playSound(Sounds.Town.thrownOut)
                                                writeText("- AHA! No gold! You are thrown out. {pause 2s}")
                                                clearMapWindow()
                                                breakLoop("thrown out of inn")
                                            },
                                            ok = {
                                                writeText("You go to bed... {pause 1s}")
                                                playSound(Sounds.Explore.rest)
                                                party.restedHours set 14
                                                writeText("Zzzz... {pause}")
                                                writeText(
                                                    flowText(
                                                        """
                                                        You wake up a little before dawn, well rested.

                                                        You have breakfast and leave the inn.
                                                        """,
                                                    ),
                                                )
                                                waitInput("after inn lodging")
                                                realm.hour set 5
                                                forEachMember {
                                                    it.hp.addClamped(it.maxHp / 4, it.maxHp)
                                                }
                                                clearMapWindow()
                                                breakLoop("slept at inn")
                                            },
                                        )
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
                                    writeText("The innkeeper leaves.")
                                    menu("(C)hitchat with guests", "(L)eave") {
                                        option('C') {
                                            speak(Buildings.inn)
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
            }

            scene(Scenes.Towne.merchant) {
                shopService(Buildings.merchant) {
                    image = Images.Scene.merchant
                    closed("Shop Closed.")
                    intro(flowText("Merchant. Can I help you? I trade in the finest items in the realm!"))
                    menu("(B)uy item", "(S)ell item", "(L)eave")
                    goodbye("Well, bye then...")

                    buy {
                        prompt("Buy what?", "")
                        tooExpensive("Too expensive!")
                        cantCarry("You cannot carry more.")
                        deal("Deal!")
                        bought {
                            line("Bought ", itemName, ".")
                        }
                        unitPrice {
                            line(price, " gold each, sir.")
                        }
                        singlePrice {
                            line("Price: ", price)
                            line("")
                        }
                        payPrompt("(P)ay")

                        catalog('B', "General goods") {
                            resource(
                                'A',
                                Items.torch,
                                resource = ShopResources.torches,
                                cap = ShopQuantityCaps.fixed(Resource.Torches, 80),
                                tooMany = flowText("You can carry no more than 80 torches..."),
                                label = "Torch",
                                zeroQuantityDeal = ShopZeroQuantityDeal.always,
                                description = flowText("Ah, the finest torches in the realm."),
                            )
                            resource(
                                'B',
                                Items.ration,
                                resource = ShopResources.food,
                                cap = ShopQuantityCaps.carryCapacity(Resource.Food),
                                tooMany =
                                    shopText {
                                        line("You can carry no more than ", cap, " units!")
                                    },
                                label = "Food Ration",
                                price = ShopPrices.formula(Formulas.rationPrice),
                                description = flowText("Better food is not available anywhere else in the entire realm."),
                            )
                            item('C', Items.blanket, label = "Blanket", description = flowText("Our blankets are very warm and soft."))
                            item('D', Items.tent, label = "Tent", description = flowText("No one manufactures better tents than we do!"))
                            item('E', Items.rope, label = "Rope", description = flowText("The finest hemp ropes in the realm."))
                            item(
                                'F',
                                Items.redPotion,
                                label = "Healing Potion",
                                price = ShopPrices.formula(Formulas.healingPotionPrice),
                                description = flowText("The best healing potions available for money!"),
                            )
                            item(
                                'G',
                                Items.donkey,
                                label = "Donkey",
                                description = flowText("Our stout donkeys are bred just for carrying your food and equipment."),
                            )
                        }
                    }

                    sell {
                        prompt("Sell what?", "")
                        accepts(Items.ration, Items.blanket, Items.tent, Items.rope)
                        acceptsKinds(ItemKind.Potion, ItemKind.Treasure, ItemKind.MagicItem)
                        rejects(Items.donkey)
                        price = ShopPrices.formula(Formulas.sellPrice)
                        wrongKind(flowText("- I don't deal in that kind of stuff!"))
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
                    writeText("Tavern Closed. {pause 1s}")
                    stop("tavern closed")
                }

                clearMapWindow()
                showBackdrop(Images.Scene.tavern)

                localInt(0) { priestBonus ->
                    forEachMember(includeDead = true) { member ->
                        whenever(member.isClass(PcClass.Priest)) {
                            priestBonus += member.charisma / 10
                        }
                    }

                    whenever(chance(priestBonus + 5)) {
                        writeText(
                            """
                            Just as you are about to sit down at a table,
                            a scruffy little man pulls at your sleeve... {pause 3s}

                            - Hey, hey, hey! You! Yes... you! I have an offer
                            that may interest you... {pause 3s}
                            """,
                        )
                        clearText()
                        choice.randomItemOffer(ItemPools.tavernScruffySpecials) {
                            offer {
                                line("- I will sell you this ", itemName, " for the ridiculously low price of ", itemPrice, " Aurum!")
                                line("")
                            }
                            broke(flowText("- You don't have that much gold!"))
                            full("- You can't carry it!")
                            deal("- Deal!!!")
                            declined(flowText("- Fine, I can live with that."))
                        }
                    }
                }

                localInt(0) { rumors ->
                    loop {
                        writeText(
                            flowText(
                                "- Welcome! Have a seat ",
                                townNpcName(NpcJobs.tavernkeeper),
                                ", the keeper of the tavern greets you...",
                            ),
                        )

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
                                speak(Buildings.tavern)
                            }

                            option('T') {
                                clearText()
                                choice.member(
                                    literalText {
                                        line("Who wants to tell a story? (1-", PartyText.memberCount, ")")
                                    },
                                    filter = MemberChoiceFilter.AliveOnly,
                                    accepting = MemberChoiceFilter.Any,
                                ) { storyteller ->
                                    whenever(storyteller.dead) {
                                        writeText("Dead men tell no stories... {pause 2s}")
                                    } otherwise {
                                        writeText(
                                            flowText {
                                                paragraph(partyName(storyteller), " stands up and screams:")
                                                paragraph("- ANYONE WHO WANNA HEAR A TRAVELER'S STORY? {pause 2s}")
                                            },
                                        )

                                        playSound(Sounds.Town.crowdCheer)
                                        writeText("- YEAAH! Roars the crowd! {pause 2s}")

                                        localInt(0) { isBoast ->
                                            menu("(T)rue story", "(B)oast") {
                                                option('T') {}
                                                option('B') {
                                                    isBoast set 1
                                                }
                                            }

                                            localInt(0) { score ->
                                                whenever(isBoast eq 1) {
                                                    score += 100
                                                }
                                                whenever(storyteller.isClass(PcClass.Bard)) {
                                                    score += 100
                                                }
                                                whenever(storyteller.charisma gt 90) {
                                                    score += 50
                                                }
                                                whenever(storyteller.charisma gt 70) {
                                                    score += 50
                                                }
                                                score += random(1..150)

                                                whenever(isBoast eq 1) {
                                                    whenever(random(0..99) gt storyteller.charisma) {
                                                        writeText("- Out with the liar! {pause 2s}")
                                                        playSound(Sounds.Town.thrownOut)
                                                        appendText("You are thrown out. {pause}")
                                                        whenever(storyteller.charisma gte 10) {
                                                            storyteller.charisma -= 10
                                                        } otherwise {
                                                            storyteller.charisma set 0
                                                        }
                                                        clearMapWindow()
                                                        breakLoop("liar thrown out of tavern")
                                                    }
                                                }

                                                clearText()
                                                whenever(score gt 400) {
                                                    writeText(
                                                        flowText(
                                                            "Superb Story! Let's give 'em some ale, lads! You are given a whole tray of ale! {pause 2s}",
                                                        ),
                                                    )
                                                    storyteller.charisma += 3
                                                }
                                                whenever((score gt 300) and (score lt 401)) {
                                                    writeText("Very good! You've been through much! {pause 2s}")
                                                    storyteller.charisma += 1
                                                }
                                                whenever((score gt 150) and (score lt 301)) {
                                                    writeText("Nice story, sir! {pause 2s}")
                                                }
                                                whenever((score gt 100) and (score lt 151)) {
                                                    writeText("The mob is bored. {pause 2s}")
                                                    whenever(storyteller.charisma gt 0) {
                                                        storyteller.charisma -= 1
                                                    }
                                                }
                                                whenever(score lt 101) {
                                                    writeText(
                                                        """
                                                        Most of the crowd falls asleep.
                                                        The rest beg you to stop. {pause 2s}
                                                        """,
                                                    )
                                                    whenever(storyteller.charisma gte 3) {
                                                        storyteller.charisma -= 3
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            option('R') {
                                clearText()
                                whenever(rumors lt 5) {
                                    rumors += 1
                                }
                                whenever(rumors gt 4) {
                                    writeText("The tavern has suddenly become very quiet.")
                                    waitInput("after quiet tavern rumor")
                                } otherwise {
                                    choose(random(0..4)) {
                                        case(0) {
                                            writeText(
                                                """
                                                After listening a while you hear an old
                                                man say to another: {pause 600ms}
                                                """,
                                            )
                                        }
                                        case(1) {
                                            writeText("A drunken bard bellows: {pause 600ms}")
                                        }
                                        case(2) {
                                            writeText("A halfling you rescued from a brawl whispers: {pause 600ms}")
                                        }
                                        case(3) {
                                            writeText(
                                                """
                                                The tavern keeper motions to you to come
                                                closer, and whispers: {pause 600ms}
                                                """,
                                            )
                                        }
                                        case(4) {
                                            writeText("A bargirl tells you: {pause 600ms}")
                                        }
                                    }
                                    tellRumor()
                                    waitInput("after tavern rumor")
                                }
                            }

                            option('O') {
                                clearText()
                                menu(
                                    "Order:",
                                    "",
                                    "(A) Ale       2 au",
                                    "(B) Mead      3 au",
                                    "(C) Wine      4 au",
                                    "(D) Fanta     1 au",
                                    "RETURN leaves.",
                                ) {
                                    option('A') {
                                        pay(
                                            lit(2),
                                            broke = {
                                                writeText("No money - No drink! {pause 1s}")
                                            },
                                            ok = {
                                                playSound(Sounds.Town.drink)
                                                writeText("AAH! (your health returns). {pause 1s}")
                                                forEachMember(includeDead = true) {
                                                    it.hp.addClamped((lit(2) / 2) + 1, it.maxHp)
                                                }
                                            },
                                        )
                                    }
                                    option('B') {
                                        pay(
                                            lit(3),
                                            broke = {
                                                writeText("No money - No drink! {pause 1s}")
                                            },
                                            ok = {
                                                playSound(Sounds.Town.drink)
                                                writeText("AAH! (your health returns). {pause 1s}")
                                                forEachMember(includeDead = true) {
                                                    it.hp.addClamped((lit(3) / 2) + 1, it.maxHp)
                                                }
                                            },
                                        )
                                    }
                                    option('C') {
                                        pay(
                                            lit(4),
                                            broke = {
                                                writeText("No money - No drink! {pause 1s}")
                                            },
                                            ok = {
                                                playSound(Sounds.Town.drink)
                                                writeText("AAH! (your health returns). {pause 1s}")
                                                forEachMember(includeDead = true) {
                                                    it.hp.addClamped((lit(4) / 2) + 1, it.maxHp)
                                                }
                                            },
                                        )
                                    }
                                    option('D') {
                                        pay(
                                            lit(1),
                                            broke = {
                                                writeText("No money - No drink! {pause 1s}")
                                            },
                                            ok = {
                                                writeText("You are the laughing stock of the entire tavern... {pause 2s}")
                                                forEachMember(includeDead = true) {
                                                    it.hp.addClamped((lit(1) / 2) + 1, it.maxHp)
                                                }
                                            },
                                        )
                                    }
                                }
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
                    // Flow-mode demo: designer writes natural prose, the wrap
                    // helper splits to fit the text-window width. Paragraphs are
                    // separated by blank lines.
                    playSound(Sounds.Explore.locked)
                    writeText(
                        """
                        Sorry. The Adventurers' Guild is permanently closed for renovation.

                        Come back when Rylween v3 ships in 1994. Or thereabouts.
                        """,
                    )

                    menu(
                        "",
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
                playSound(Sounds.Explore.doorOpen)
                writeText("You enter the hut. {pause 600ms}")

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
                withCurrentCaster { caster ->
                    ask.monsterAlive(literalText("Who?", "")) { target ->
                        localInt(caster.level * random(2..8)) { damage ->
                            writeText(flowText(partyName(caster), " chills the opponent for ", damage, " damage pts. {pause 2s}"))

                            target.wound(damage)
                            whenever(target.dead) {
                                writeText("...KILLED! {pause 600ms}")
                            }
                        }
                    }
                }
            }

            spell(Spells.Mage.fire, slot = 2, baseCost = 25, name = "Fire Cloud") {
                withCurrentCaster { caster ->
                    burnEveryLivingMonster(caster, damageRoll = 1..4)
                }
            }

            spell(Spells.Mage.confusion, slot = 3, baseCost = 17, name = "Confusion") {
                withCurrentCaster { caster ->
                    writeText {
                        line(partyName(caster), " utters a word.")
                    }
                    appendText("Your images waver and blur in front of your enemies {pause 2s}")
                    battle.monsterWeaponSkill /= 2
                    battle.monsterConfused set true
                }
            }

            spell(Spells.Mage.death, slot = 4, baseCost = 78, name = "Death Strike") {
                ask.monsterAlive(literalText("Who?", "")) { target ->
                    clearText()
                    whenever(random(0..199) lt target.hp) {
                        writeText(
                            """
                            Through INCREDIBLE resistance, the enemy is still
                            alive, badly wounded. {pause 2s}
                            """,
                        )
                        target.hp set 1
                    } otherwise {
                        writeText(
                            """
                            The enemy is wiped straight off the surface of the earth! {pause 2s}
                            """,
                        )
                        target.kill()
                    }
                }
            }

            spell(Spells.Mage.hold, slot = 5, baseCost = 43, name = "Hold Enemies") {
                withCurrentCaster { caster ->
                    writeText {
                        line(partyName(caster), " utters a word.")
                    }
                    appendText("The enemies freeze dead in their tracks. {pause 2s}")
                    battle.monstersHeld set true
                }
            }

            spell(Spells.Mage.antimagic, slot = 6, baseCost = 28, name = "Anti Magic") {
                withCurrentCaster { caster ->
                    writeText {
                        line(partyName(caster), " utters a word.")
                    }
                    appendText(
                        """
                        The enemy is now stopped from using magic against you this round. {pause 3s}
                        """,
                    )
                    battle.antimagic set true
                }
            }

            spell(Spells.Monster.frost, slot = 1, baseCost = 0, name = "Frost Finger") {
                battle.randomAliveMember { target ->
                    localInt((battle.encounterHitPointMax / 5) + random(0..3)) { damage ->
                        writeText {
                            line("Frost Finger on ", partyName(target), "! {pause 1s}")
                            line("and does ", damage, " pts of damage! {pause 1s}")
                        }
                        target.wound(damage)
                        whenever(target.dead) {
                            appendText {
                                line("{red}", partyName(target), " DIES! {pause 1s}")
                            }
                        }
                    }
                }
            }

            spell(Spells.Monster.fire, slot = 2, baseCost = 0, name = "Cloud of Fire") {
                writeText("Cloud of Fire! {pause 1s}")
                forEachMember { target ->
                    localInt((battle.encounterHitPointMax / 5) + random(0..4)) { damage ->
                        writeText(flowText(partyName(target), " is burnt for ", damage, " damage pts! {pause 1s}"))
                        target.wound(damage)
                        whenever(target.dead) {
                            writeText {
                                line("{red}", partyName(target), " DIES! {pause 1s}")
                            }
                        }
                    }
                }
            }

            spell(Spells.Monster.hold, slot = 3, baseCost = 0, name = "Hold Creature") {
                writeText(
                    """
                    Hold Creature! {pause 600ms}

                    You realize with horror that you cannot move! {pause}
                    """,
                )
                battle.partyHeld set true
            }

            spell(Spells.Monster.confusion, slot = 4, baseCost = 0, name = "Confusion") {
                writeText(
                    """
                    Confusion! {pause 600ms}

                    You are almost blinded by a colorful haze. {pause}
                    """,
                )
                battle.partyConfused set true
            }

            // Priest spells are used by the wilderness/dungeon Z command. The reusable
            // runtime owns the "Who?", class gate, spell list, and abort key; these
            // blocks declare the current game's spell effects.
            spell(Spells.Priest.heal, slot = 1, baseCost = 8, name = "Healing") {
                withCurrentCasterCost { caster, cost ->
                    clearText()
                    whenever(caster.mp gte cost) {
                        caster.mp -= cost
                        ask.member(
                            literalText {
                                line("Who? (1-", PartyText.memberCount, ")")
                            },
                            filter = MemberChoiceFilter.Any,
                        ) { target ->
                            localInt(random(10..19) + (caster.level * 2)) { healing ->
                                whenever(target.dead) {
                                    writeText("Dead. {pause 600ms}")
                                    clearText()
                                } otherwise {
                                    target.hp.addClamped(healing, target.maxHp)
                                    writeText(
                                        flowText(
                                            partyName(caster),
                                            " heals ",
                                            partyName(target),
                                            " for ",
                                            healing,
                                            " hit points... {pause}",
                                        ),
                                    )
                                    clearText()
                                }
                            }
                        }
                    } otherwise {
                        writeText("Not enough spell pts! {pause 1s}")
                        clearText()
                    }
                }
            }

            spell(Spells.Priest.restoration, slot = 2, baseCost = 20, name = "Restoration") {
                withCurrentCasterCost { caster, cost ->
                    clearText()
                    whenever(caster.mp gte cost) {
                        caster.mp -= cost
                        ask.member(
                            literalText {
                                line("Who? (1-", PartyText.memberCount, ")")
                            },
                            filter = MemberChoiceFilter.Any,
                        ) { target ->
                            target.fullHeal()
                            writeText(flowText(partyName(caster), " heals ", partyName(target), " fully... {pause}"))
                            clearText()
                        }
                    } otherwise {
                        writeText("Not enough spell pts! {pause 1s}")
                        clearText()
                    }
                }
            }

            spell(Spells.Priest.rebirth, slot = 3, baseCost = 90, name = "Rebirth") {
                withCurrentCasterCost { caster, cost ->
                    clearText()
                    whenever(caster.mp gte cost) {
                        ask.member(
                            literalText {
                                line("Who? (1-", PartyText.memberCount, ")")
                            },
                            filter = MemberChoiceFilter.Any,
                        ) { target ->
                            whenever(target.dead) {
                                caster.mp -= cost
                                target.revive()
                                writeText(
                                    flowText(
                                        partyName(target),
                                        "'s eyelids flicker, and finally ",
                                        pronoun(target, PronounCase.Subject),
                                        " opens ",
                                        pronoun(target, PronounCase.Possessive),
                                        " eyes! {pause 2s}",
                                    ),
                                )
                                clearText()
                            } otherwise {
                                writeText(flowText(partyName(target), " is alive and healthy. {pause}"))
                            }
                        }
                    } otherwise {
                        writeText("Not enough spell pts! {pause 1s}")
                        clearText()
                    }
                }
            }

            spell(Spells.Priest.banish, slot = 4, baseCost = 85, name = "Banish Evil") {
                withCurrentCasterCost { caster, cost ->
                    clearText()
                    whenever(caster.mp gte cost) {
                        whenever(context.inDungeon) {
                            caster.mp -= cost
                            writeText(
                                flowText(
                                    partyName(caster),
                                    " raises ",
                                    pronoun(caster, PronounCase.Possessive),
                                    " hands. ",
                                    pronoun(caster, PronounCase.Subject),
                                    " gathers divine power between ",
                                    pronoun(caster, PronounCase.Possessive),
                                    " palms, and suddenly clasps them together over ",
                                    pronoun(caster, PronounCase.Possessive),
                                    " head. {pause 2s}",
                                ),
                            )
                            showTextAndWait(
                                flowText(
                                    """
                                    There is a deafening clap of thunder, and you hear
                                    beings scream in agony in the dungeon below...
                                    """,
                                ),
                            )
                            realm.banished set true
                        } otherwise {
                            showTextAndWait(
                                flowText(
                                    """
                                    There is no particular target for this banishing.
                                    This spell is meant for use in dungeons and caves.
                                    """,
                                ),
                            )
                        }
                    } otherwise {
                        writeText("Not enough spell pts! {pause 1s}")
                        clearText()
                    }
                }
            }

            spell(Spells.Priest.revelation, slot = 5, baseCost = 40, name = "Revelation") {
                withCurrentCasterCost { caster, cost ->
                    clearText()
                    whenever(caster.mp gte cost) {
                        whenever(context.inDungeon) {
                            caster.mp -= cost
                            whenever(chance(59)) {
                                showTextAndWait("There is nothing special near.")
                            } otherwise {
                                writeText("Something special is near! {pause}")
                                clearText()
                                choose(random(0..2)) {
                                    case(0) { dungeonItemFind(ItemPools.dungeonWeaponFinds) }
                                    case(1) { dungeonItemFind(ItemPools.dungeonPotionGemFinds) }
                                    case(2) { dungeonItemFind(ItemPools.magicItems) }
                                }
                            }
                        } otherwise {
                            showTextAndWait(
                                flowText(
                                    """
                                    This spell can be used in dungeons only, where there
                                    is a limited radius for the caster to enchant.
                                    """,
                                ),
                            )
                        }
                    } otherwise {
                        writeText("Not enough spell pts! {pause 1s}")
                        clearText()
                    }
                }
            }

            spell(Spells.Priest.teleport, slot = 6, baseCost = 35, name = "Teleport") {
                withCurrentCasterCost { caster, cost ->
                    whenever(caster.mp gte cost) {
                        caster.mp -= cost
                        writeText(
                            flowText(
                                partyName(caster),
                                " carefully prepares the ritual of Teleportation. ",
                                pronoun(caster, PronounCase.Subject),
                                " spends an hour chanting mantras and burning blue candles.",
                            ),
                        )
                        realm.hour += 1
                        waitInput("after teleport ritual")
                        showTextAndWait(
                            flowText(
                                pronoun(caster, PronounCase.Subject),
                                " picks up a stone slab from ",
                                pronoun(caster, PronounCase.Possessive),
                                " satchel and uses a special instrument to scratch the direction ",
                                pronoun(caster, PronounCase.Subject),
                                " wants you to teleport to.",
                            ),
                        )
                        ask.wildernessDirection(
                            literalText(
                                "Teleport :",
                                "",
                                "   8",
                                " 4-+-6",
                                "   2",
                                "",
                                "(or arrow keys)",
                            ),
                        ) { direction ->
                            clearText()
                            ask.digit(flowText("How many miles do you want to travel? (1-9)"), 1..9) { distance ->
                                teleportParty(direction, distance)
                                renderMap()
                                writeText("You appear to be somewhere else. {pause}")
                                clearText()
                            }
                        }
                    } otherwise {
                        writeText("Not enough spell pts! {pause 1s}")
                        clearText()
                    }
                }
            }

            spell(Spells.Priest.earthquake, slot = 7, baseCost = 55, name = "Earthquake") {
                withCurrentCasterCost { caster, cost ->
                    clearText()
                    whenever(context.inDungeon or context.inCastle) {
                        showTextAndWait("The roof would cave in on you!")
                    } otherwise {
                        whenever(caster.mp gte cost) {
                            caster.mp -= cost
                            writeText(
                                flowText(
                                    partyName(caster),
                                    " utters three syllables in an archaic tongue... {pause 2s} and the ground cracks open! {pause 3s}",
                                ),
                            )
                            clearMapWindow()
                            showBackdrop(Images.Backdrop.cave)
                            clearText()
                            choice.enterOrLeave(
                                literalText("You find a cave!", "", "(E)nter", "(L)eave"),
                                enter = {
                                    whenever(party.timeLeft gt 10) {
                                        discoverCave(random(5..18), Dungeons.randomCave, lit(10))
                                    } otherwise {
                                        writeText(
                                            flowText(
                                                """
                                                You don't have enough moves left to explore
                                                the cave...
                                                """,
                                            ),
                                        )
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
                            writeText("Not enough spell pts! {pause 1s}")
                            clearText()
                        }
                    }
                }
            }

            itemUse(Items.redPotion) {
                inventory()
                consume = ItemConsumePolicy.ScriptControlled

                effect {
                    ask.member(
                        literalText {
                            line("Who? (1-", PartyText.memberCount, ")")
                        },
                        filter = MemberChoiceFilter.AliveOnly,
                    ) { target ->
                        consumeActiveItem()
                        localInt(potionPoints(target)) { points ->
                            target.hp.addClamped(points, target.maxHp)
                            writeText(flowText(partyName(target), " recovers ", points, " hit points. {pause 1s}"))
                        }
                    }
                }
            }

            itemUse(Items.bluePotion) {
                inventory()
                consume = ItemConsumePolicy.ScriptControlled

                effect {
                    ask.member(
                        literalText {
                            line("Who? (1-", PartyText.memberCount, ")")
                        },
                        filter = MemberChoiceFilter.AliveOnly,
                    ) { target ->
                        consumeActiveItem()
                        localInt(potionPoints(target)) { points ->
                            target.wound(points)
                            writeText(flowText(partyName(target), " is poisoned for ", points, " hit points. {pause 1s}"))
                        }
                    }
                }
            }

            itemUse(Items.amberPotion) {
                inventory()
                consume = ItemConsumePolicy.ScriptControlled

                effect {
                    ask.member(
                        literalText {
                            line("Who? (1-", PartyText.memberCount, ")")
                        },
                        filter = MemberChoiceFilter.SpellcasterOnly,
                        accepting = MemberChoiceFilter.AliveOnly,
                    ) { target ->
                        consumeActiveItem()
                        localInt(potionPoints(target)) { points ->
                            target.mp.addClamped(points, target.maxMp)
                            writeText(flowText(partyName(target), " recovers ", points, " spell points. {pause 1s}"))
                        }
                    }
                }
            }

            itemUse(Items.frostWand) {
                usableIn(ItemUseContext.Combat)
                consume = ItemConsumePolicy.BeforeUse

                effect {
                    withCurrentCaster { caster ->
                        battle.firstAliveMonster { target ->
                            localInt(random(5..11) + caster.level) { damage ->
                                writeText(
                                    flowText(
                                        partyName(caster),
                                        " chills ",
                                        monsterName(target),
                                        " for ",
                                        damage,
                                        " damage pts. {pause 1s}",
                                    ),
                                )

                                target.wound(damage)
                                whenever(target.dead) {
                                    writeText("...KILLED! {pause 600ms}")
                                }
                            }
                        }
                    }
                }
            }

            itemUse(Items.fireWand) {
                usableIn(ItemUseContext.Combat)
                consume = ItemConsumePolicy.BeforeUse

                effect {
                    withCurrentCaster { caster ->
                        clearText()
                        burnEveryLivingMonster(caster, damageRoll = 3..8)
                    }
                }
            }

            itemUse(Items.gemOfConfusion) {
                usableIn(ItemUseContext.Combat)
                consume = ItemConsumePolicy.BeforeUse

                effect {
                    withCurrentCaster { caster ->
                        showTextAndWait(
                            flowText(
                                partyName(caster),
                                " throws the gem at the enemy. It breaks before them in a rain of sparks.",
                                " They bump into each other as they try to see you.",
                            ),
                        )
                        battle.monsterWeaponSkill /= 2
                    }
                }
            }

            itemUse(Items.gemOfBlindness) {
                usableIn(ItemUseContext.Combat)
                consume = ItemConsumePolicy.BeforeUse

                effect {
                    withCurrentCaster { caster ->
                        showTextAndWait(
                            flowText(
                                partyName(caster),
                                " throws the gem at the enemy. It breaks, releasing a brilliant cloud of light which completely surrounds them.",
                                " As the light fades, your enemies are blind.",
                            ),
                        )
                        battle.monsterWeaponSkill set 5
                    }
                }
            }

            itemUse(Items.drumsOfDeath) {
                usableIn(ItemUseContext.Combat)
                consume = ItemConsumePolicy.BeforeUse

                effect {
                    withCurrentCaster { caster ->
                        battle.firstAliveMonster { target ->
                            writeText(flowText(partyName(caster), " summons death to take a ", monsterName(target), " away... {pause 2s}"))
                            writeText("...KILLED! {pause 600ms}")
                            target.kill()
                            clearText()
                        }
                    }
                }
            }

            itemUse(Items.staffOfHealing) {
                usableIn(ItemUseContext.Combat)
                consume = ItemConsumePolicy.BeforeUse

                effect {
                    ask.member(
                        literalText {
                            line("On Whom? (1-", PartyText.memberCount, ")")
                        },
                        filter = MemberChoiceFilter.Any,
                    ) { target ->
                        clearText()
                        whenever(!target.dead) {
                            target.fullHeal()
                        }
                        writeText(flowText(partyName(target), " is fully healed. {pause 1s}"))
                        clearText()
                    }
                }
            }

            itemUse(Items.blessingScroll) {
                usableIn(ItemUseContext.Combat)
                consume = ItemConsumePolicy.BeforeUse

                effect {
                    showTextAndWait(
                        """
                        The party feels their spirits grow. You are all stronger
                        than before.
                        """,
                    )

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
                    showTextAndWait("The party is fully healed!")

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
                    ask.member(
                        literalText {
                            line("On Whom? (1-", PartyText.memberCount, ")")
                        },
                        filter = MemberChoiceFilter.DeadOnly,
                        accepting = MemberChoiceFilter.Any,
                    ) { target ->
                        clearText()
                        whenever(target.dead) {
                            writeText {
                                line(partyName(target), " opens ", pronoun(target, PronounCase.Possessive), " eyes! {pause 1s}")
                            }
                            target.revive()
                            consumeActiveItem()
                        } otherwise {
                            writeText {
                                line(partyName(target), " is not dead. {pause 1s}")
                            }
                            battle.actionRetry set true
                        }
                    }
                }
            }

            itemUse(Items.kaelsAxe) {
                usableIn(ItemUseContext.Combat)
                consume = ItemConsumePolicy.BeforeUse

                effect {
                    withCurrentCaster { caster ->
                        writeText(flowText(partyName(caster), " throws Kael's axe at the enemy! {pause}"))
                        writeText(
                            """
                            It wounds them severely as it spins around, wielded by
                            hands you cannot see! {pause 4s}
                            """,
                        )
                        writeText(
                            """
                            With a raging battle cry, you attack the mortally
                            wounded foes! {pause 3s}
                            """,
                        )

                        forEachMonster { foe ->
                            foe.hp set random(1..3)
                        }
                    }
                }
            }

            itemUse(Items.holyHandGrenade) {
                usableIn(ItemUseContext.Combat)
                consume = ItemConsumePolicy.BeforeUse

                effect {
                    withCurrentCaster { caster ->
                        writeText(
                            flowText(partyName(caster), " pulls the holy pin, and lobbeth the holy hand grenade at the foes... {pause 3s}"),
                        )
                        writeText("KA-BLAM! {pause}")
                        clearText()

                        forEachMonster { foe ->
                            foe.hp /= 4
                            foe.hp += 1
                        }
                    }
                }
            }

            itemUse(Items.fluteOfMadness) {
                usableIn(ItemUseContext.Combat)
                consume = ItemConsumePolicy.BeforeUse

                effect {
                    withCurrentCaster { caster ->
                        showTextAndWait(
                            flowText(
                                partyName(caster),
                                " plays the Flute of Madness. It produces a shrill, fragile sound, and breaks. Nothing happens.",
                            ),
                        )
                    }
                }
            }

            // 4. Mission action scene: flag states are fluent typed markers, not strings.
            // Mission scenes are the script bodies reached by the mission table.
            // They own one-off prose/rewards while reusable handoffs stay semantic.
            scene(Scenes.Missions.erethorEntry) {
                dungeonEntrance(Dungeons.erethorTemple, levels = 12) {
                    line("Here is the temple of the chaos lord Erethor.")
                    line("A winding staircase spirals down between broken pillars.")
                    line("")
                    line("(E)nter")
                    line("(L)eave")
                }
            }

            scene(Scenes.Missions.husbandCave) {
                dungeonEntrance(Dungeons.ogreCave, levels = 7) {
                    line(
                        "There is a cave entrance in the foothills to the east. Is this the place ",
                        character(Roles.peasantHusband).name,
                        " talked about?",
                    )
                    line("")
                    line("(E)nter")
                    line("(L)eave")
                }
            }

            scene(Scenes.Missions.bandits) {
                showTextAndWait(
                    """
                    You come across the camp of the bandits. One of them spots you
                    and they attack!
                    """,
                )
                encounter(Monsters.bandit, mandatory = true)
                renderMap()
                showTextAndWait(flowText("You find the cargo the ", character(Roles.banditsQuestGiver).job, " wanted you to bring him."))
                mark(Story.garth.bandits.recovered)
            }

            scene(Scenes.Missions.trollTooth) {
                encounter(Monsters.troll, mandatory = true)
                renderMap()
                showTextAndWait(
                    flowText(
                        "You break off a troll tooth to give the ",
                        character(Roles.hermitTrollTooth).job,
                        ". After wiping the slime off it, you put it in your backpack.",
                    ),
                )
                mark(Story.hermit.trollTooth.extracted)
            }

            scene(Scenes.Missions.hermitDungeon) {
                dungeonEntrance(Dungeons.fireShieldCave, levels = 20, leaveClearsText = false) {
                    line("You have found a dungeon.")
                    line("The entrance has been cut out of some huge animal's skull.")
                    line("")
                    line("(E)nter")
                    line("(L)eave")
                }
            }

            scene(Scenes.Missions.thunTemple) {
                showTextAndWait(
                    """
                    You stand before the ancient temple of Thun. Around you are
                    the ruins of the once proud city of Thun...
                    """,
                )
                showTextAndWait(
                    """
                    You see an altar with five sockets before you. Each is roughly
                    gem-sized. An inscription says: 'Insert the gems and your path shall open'
                    """,
                )
                menu(
                    literalText {
                        line("Do you want to put five gems in the sockets?")
                        line("")
                        line("(Y)es")
                        line("(L)eave")
                    },
                ) {
                    defaultOption('Y') {
                        whenever(party.inventory.count(ItemPools.thunAltarGems) lt 5) {
                            showTextAndWait("You don't have five different gems.")
                        } otherwise {
                            party.inventory.consume(ItemPools.thunAltarGems, 5)
                            showTextAndWait(
                                """
                                With a resounding 'CRUNG' the altar slides away to
                                reveal a dark pit. You enter...
                                """,
                            )
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
                    literalText {
                        line(
                            "The kobold village lies ahead. You can hear the noise from some kind of party even at a distance of 200 ",
                            "yards.",
                        )
                        line("")
                        line("(A)ttack now")
                        line("(L)eave")
                    },
                ) {
                    option('A') {
                        showTextAndWait(
                            """
                            You run screaming into a large celebration of some sort.
                            There are about forty kobolds here. At first, they are
                            very surprised, and you manage to slay many, before they attack...
                            """,
                        )
                        encounter(Monsters.koboldHorde, mandatory = true)
                        writeText(
                            """
                            Piles of dead kobolds are lying all around. Still, they
                            keep coming!
                            """,
                        )
                        waitInput()
                        encounter(Monsters.koboldHorde, mandatory = true)
                        writeText(
                            """
                            You have annihilated most of the village. You pause to wipe
                            the blood off your weapons, when suddenly, some ORCS rush out
                            to attack you from nowhere.
                            """,
                        )
                        waitInput()
                        encounter(Monsters.orc, mandatory = true)
                        renderMap()
                        showTextAndWait(
                            """
                            The entire village is wiped out. You snatch an ugly pendant
                            from the head of the kobold chief to prove the bloody deed.
                            """,
                        )
                        mark(Story.masterCrown.task1Done)
                    }
                    option('L') {}
                }
            }

            scene(Scenes.Missions.skullweed) {
                showBackdrop(Images.Mission.skullweedSwamp)
                showTextAndWait(
                    """
                    On the ground before you grows the skullweed plant. You break
                    off a few plants and put them in your backpack.
                    """,
                )
                mark(Story.masterCrown.task2Done)
            }

            scene(Scenes.Missions.borinMeet) {
                showTextAndWait(
                    """
                    You arrive at a village which looks typically dwarfish. As you
                    approach the village square, an old dwarf who introduces himself
                    as Borin greets you.
                    """,
                )
                showTextAndWait(
                    """
                    You explain why you are here, and he frowns.

                    - Well, he says, you actually COULD have a stone of that type
                    if you do our tribe a favor...
                    """,
                )
                showTextAndWait(
                    """
                    - Years have passed, and much of our knowledge about the making
                    of arms is gone. We haven't practiced it for generations, and
                    the arms we produce nowadays are not as unbeatable as they once were...
                    """,
                )
                showTextAndWait(
                    """
                    - When our king Nolrim died, nine hundred years ago, we buried
                    him with his trusty dagger Reebar. If we had this to study we
                    could once again become the greatest smiths that there are...
                    """,
                )
                showTextAndWait(
                    """
                    - If you go to Nolrim's crypt, breach the traps my ancestors
                    set to protect him against looters, and return Reebar, I will
                    not only give you the stone, but also lots of gold, and some
                    dwarven treasures.
                    """,
                )
                showTextAndWait(
                    flowText("- Nolrim's crypt is at ", location(Locations.nolrimCrypt).coordinates, ". Be careful, and fare thee well."),
                )
                journal {
                    line("Nolrim's Crypt is at ", location(Locations.nolrimCrypt).coordinates, ".")
                }
                mark(Story.masterCrown.borinMet)
            }

            scene(Scenes.Missions.nolrimCrypt) {
                dungeonEntrance(Dungeons.reebarTomb, levels = 20) {
                    line(
                        "Before you is the crypt of Nolrim. After levering a stone slab away, a dark foreboding passage leads down to ",
                        "unknown depths.",
                    )
                    line("")
                    line("(E)nter the Crypt")
                    line("(L)eave")
                }
            }

            scene(Scenes.Missions.masterCrown) {
                dungeonEntrance(Dungeons.masterCrownDungeon, levels = 10) {
                    line("Before you is the dungeon where the ancients hid the Master Crown. Your long trek has come to an end.")
                    line("")
                    line("(E)nter")
                    line("(L)eave")
                }
            }

            scene(Scenes.Missions.ogreCave) {
                clearText()
                choice.enterOrLeave(
                    literalText {
                        line("You find the cave the half-orc told you about.")
                        line("As you get closer, you become aware of a hideous smell, emanating from the cavern depths...")
                        line("")
                        line("(E)nter")
                        line("(L)eave")
                    },
                    enter = {
                        writeText(
                            """
                            You enter the cave. Piles of treasure are thrown all around.
                            You note with terror that some OGRES sit among the gold and
                            jewels! They spot you and run screaming to attack!
                            """,
                        )
                        waitInput()
                        encounter(Monsters.ogre, mandatory = true)
                        renderMap()
                        writeText(
                            """
                            You defeat the ogres and start searching for the fabled
                            Foot of Jazzy. Just as you begin to wonder if the half-orc
                            lied to you, you hear a sound and quickly turn around.
                            An OGRE LORD has quietly entered the cave!
                            """,
                        )
                        waitInput()
                        encounter(Monsters.ogreLord, mandatory = true)
                        renderMap()
                        showTextAndWait(
                            """
                            You defeat the Ogre Lord. You take all the gold you find,
                            5,645 gold coins! You also find a Mithril Mail some
                            unfortunate warrior has left here.
                            """,
                        )
                        reward {
                            gold(5_645)
                            items(Items.mithrilMail)
                        }
                        party.randomMember(MemberChoiceFilter.AliveOnly) { noteFinder ->
                            showTextAndWait(
                                flowText(
                                    "...There is however no trace of the Foot. Just as you are about to give up, ",
                                    partyName(noteFinder),
                                    " finds a note! It is written in shaky hand with a strange brown ink...",
                                ),
                            )
                        }
                        showTextAndWait(
                            literalText(
                                """
                                'THEY SHALL NOT HAVE THE
                                FOOT! I'm dying and I'll
                                soon be dead, but I will
                                use my last strength to
                                teleport it away from
                                here to the eastern peaks,
                                Jazzy's regions.'
                                         - Kastagir.
                                """,
                            ),
                        )
                        showTextAndWait("You leave the cave.")
                        mark(Story.jazzy.ogre.ogresCleared)
                    },
                    leave = {
                        clearText()
                    },
                )
            }

            scene(Scenes.Missions.jazzyFoot) {
                showTextAndWait(
                    """
                    Something about this whole area is weird. The air seems to be
                    loaded with electricity. Suddenly you spot an object on the ground.
                    It is a delicate ivory statue in the shape of a human foot.
                    THIS IS THE FOOT OF JAZZY!
                    """,
                )
                writeText(
                    """
                    Just as you touch it, the sky splits open and amid the wailing
                    of hundreds of tortured souls, Jazzy's minions attack you, for
                    the heresy they find you guilty of...
                    """,
                )
                waitInput("before Jazzy minions")
                encounter(Monsters.wingedThing, mandatory = true)
                renderMap()
                showTextAndWait(
                    """
                    Quiet settles again, as you destroy the foul creatures, one by one.
                    You reach for the foot again, when suddenly, something EVEN MORE
                    horrible happens...
                    """,
                )
                writeText(
                    """
                    A twenty-foot-tall being materializes in front of you! You will
                    have to fight Jazzy, himself!
                    """,
                )
                waitInput("before Jazzy")
                encounter(Monsters.jazzy, mandatory = true)
                renderMap()
                showTextAndWait(
                    """
                    Today, you have killed a god. You watch silently as the huge
                    body starts to melt. It fades away into Nothingness. This time,
                    when you pick up the foot, nothing happens.
                    """,
                )
                mark(Story.jazzy.ogre.jazzyDefeated)
                achieve(Milestones.footOfJazzy)
            }

            scene(Scenes.Missions.silverKey) {
                dungeonEntrance(Dungeons.silverSanctum, levels = 20) {
                    line("Here lies the dungeon of the Silver Key.")
                    line("It is merely a pit in the ground. You _hope_ that that despicable thief didn't trick you.")
                    line("")
                    line("(E)nter dungeon")
                    line("(L)eave")
                }
            }

            scene(Scenes.Missions.hobgoblinCaravan) {
                showTextAndWait(
                    "Just before you is a caravan of hobgoblins on white, well-bred horses.",
                )
                showTextAndWait(
                    """
                    It is unusual that these foul creatures have such beautiful animals.
                    Hoping that they can give you a clue about the key, you emit a loud
                    battle cry and attack!
                    """,
                )
                encounter(Monsters.ridingHobgoblin, mandatory = true)
                renderMap()
                showTextAndWait(
                    flowText {
                        paragraph("Before you kill the last hobgoblin you ask him about the key. The foul creature blurts:")
                        paragraph(
                            "- Boss has it! In ze cave! at home, yess, ",
                            location(Locations.hobgoblinCave).coordinates,
                            "... pleeez sirs, don't kill me...",
                        )
                    },
                )
                showTextAndWait(
                    "(at this point you sever his carotid artery).",
                )
                journal {
                    line("The hobgoblin cave is at ", location(Locations.hobgoblinCave).coordinates, ".")
                }
                mark(Story.silverKey.caveLocated)
            }

            scene(Scenes.Missions.hobgoblinCave) {
                dungeonEntrance(Dungeons.hobgoblinStronghold, levels = 22) {
                    line("This is a hobgoblin cave.")
                    line(
                        "Smoke drifts out of a pit in the ground. A staircase spirals around the inside of the pit. When you see the ",
                        "impaled human skulls decorating the entrance, you feel uneasy.",
                    )
                    line("")
                    line("(E)nter pit")
                    line("(L)eave")
                }
            }

            // Script rewards use semantic item ids and placement policy, not
            // numeric inventory ids or runtime-specific reward shortcuts.
            scene(Scenes.Missions.borinGift) {
                showTextAndWait(
                    """
                    Borin greets you. He can hardly believe his eyes when you show
                    him the dagger. He motions to you to follow him, and guides you
                    to his hut.
                    """,
                )
                showTextAndWait(
                    """
                    Smiling, he offers you 3,578 gold coins and 3 gems. You greedily
                    accept the gift. He also hands you a purple dwarf stone.
                    """,
                )
                reward {
                    gold(3_578)
                    items(Items.redGem, Items.blueGem, Items.greenGem)
                }
                mark(Story.masterCrown.borinPaid)
            }

            // Dialogue branches are selected by NPC and story state. One-off
            // prose/rewards stay inline; shared conversation menus live in speak.
            dialogue {
                npc(Npcs.garth) {
                    whenState(Story.garth.bandits.none) {
                        showTextAndWait(
                            """
                            In fact, I have a mission for you. It will bring you much gold!
                            """,
                        )
                        showTextAndWait(
                            """
                            I was robbed by bandits just to the east of here. If you find
                            these bandits, kill them, and return my cargo here, I'll pay
                            you 2,000 gold and a ruby my grandfather gave me.
                            """,
                        )
                        mark(Story.garth.bandits.accepted)
                    }

                    whenState(Story.garth.bandits.accepted) {
                        showTextAndWait(
                            """
                            Find those bandits and bring my cargo back here. Then I'll pay you.
                            """,
                        )
                    }

                    whenState(Story.garth.bandits.recovered) {
                        showTextAndWait(
                            flowText(
                                "The ",
                                character(Roles.banditsQuestGiver).job,
                                " is GRATEFUL! He pays you 2,000 gold and hands you a red gem.",
                            ),
                        )
                        reward {
                            gold(2_000)
                            items(Items.redGem)
                            promptDrop(
                                cantCarry = "You can't carry the gem.",
                                pickedUp = "You pick up the gem.",
                            )
                        }
                        mark(Story.garth.bandits.closed)
                        waitInput("after Garth gem")
                        clearText()
                    }
                }

                npc(Npcs.nrimTo) {
                    whenState(Story.ranger.letter.none) {
                        showTextAndWait(
                            """
                            - Greetings, adventurers. I have a mission for you.

                            He hands you a letter.
                            """,
                        )
                        writeText(
                            flowText(
                                "- If you deliver this to my brother, ",
                                character(Roles.rangerBrother).name,
                                ", who lives ",
                                character(Roles.rangerBrother).location,
                                ", he will give you a great weapon!",
                            ),
                        )
                        journal {
                            line(
                                character(Roles.rangerBrother).name,
                                ", the ",
                                character(Roles.rangerBrother).job,
                                " lives ",
                                character(Roles.rangerBrother).location,
                            )
                        }
                        mark(Story.ranger.letter.accepted)
                        waitInput("after Nrim'to letter")
                    }
                }

                npc(Npcs.areth) {
                    whenState(Story.fighter.priestBook.none) {
                        showTextAndWait(
                            flowText(
                                "- Greetings. Once upon a time, I was a mighty ",
                                character(Npcs.areth).job,
                                ". Before I retired I managed to slay over two hundred orcs.",
                                " All of this, because of my Dancing Sword.",
                            ),
                        )
                        showTextAndWait(
                            flowText(
                                "I have no use for this weapon anymore, and I will give it to you, if you do me a small favor. A ",
                                character(Roles.priestWithBook).job,
                                " named ",
                                character(Roles.priestWithBook).name,
                                " has got one of my books.",
                            ),
                        )
                        writeText("We are old friends, and I lent it to him. However, I haven't heard much from him lately.")
                        mark(Story.fighter.priestBook.priestTold)
                        waitInput("after Areth asks for the book")
                    }

                    whenState(Story.fighter.priestBook.wizardRobbed) {
                        showTextAndWait(
                            """
                            - Oh! Thank you!!!

                            He removes a floor board and gives you a WONDERFUL blade. It sparkles in the light!
                            """,
                        )
                        mark(Story.fighter.priestBook.fighterGrateful)
                        reward {
                            items(Items.dancingSword)
                            promptDrop(
                                cantCarry = "You can't carry it!",
                                pickedUp = "You take the blade.",
                            )
                        }
                        waitInput("after Areth's blade")
                    }
                }

                npc(Npcs.nevaak) {
                    whenState(Story.fighter.priestBook.priestVisited) {
                        writeText(
                            """
                            You persuade him to give you the book. After showing him your
                            sharp weapons, he chickens out and pulls it out of his robe.
                            He throws it at you and flees...
                            """,
                        )
                        mark(Story.fighter.priestBook.wizardRobbed)
                        waitInput("after Nevaak flees")
                    }
                }

                npc(Npcs.gorm) {
                    whenState(Story.fighter.priestBook.priestTold) {
                        showTextAndWait(
                            flowText {
                                paragraph("When you mention the book, he nods and looks a bit embarrassed.")
                                paragraph(
                                    "- Yes, I know. I happened to lose it. A ",
                                    character(Roles.wizardRobbed).job,
                                    " called ",
                                    character(Roles.wizardRobbed).name,
                                    " stole it from my bedroom a week ago...",
                                )
                            },
                        )
                        showTextAndWait(
                            """
                            He quickly fled, but I know it's him, because he made
                            inquiries about it, and tried to steal it from me when I
                            was having dinner.
                            """,
                        )
                        writeText(
                            flowText(
                                "I don't know his whereabouts, but please find the book and take it back to ",
                                character(Roles.fighterIntro).name,
                                " if you can.",
                            ),
                        )
                        mark(Story.fighter.priestBook.priestVisited)
                        waitInput("after Gorm asks for the book")
                    }
                }

                npc(Npcs.bernak) {
                    whenState(Story.silverKey.none) {
                        writeText("- There is an item of great power, he hisses. {pause 2s}")
                        showTextAndWait(
                            """
                            - It is called the SILVER KEY. It has taken me many years
                            of research to find its location, and this only to be
                            stopped by a guardian telling me that 'only those of pure
                            heart can enter'.
                            """,
                        )
                        showTextAndWait(
                            """
                            - I fled for my life as horned creatures chased me
                            through the woods.

                            I have an offer to make thee.
                            """,
                        )
                        writeText(
                            """
                            I will sell you a map to the location of the SILVER KEY for
                            50,000 gold coins. The price is reasonable. 10,000 gold for
                            each of those fruitless years.
                            """,
                        )
                        appendText(literalText("- Deal?"))
                        menu("(B)uy it", "(N)o thanks") {
                            option('B') {
                                pay(
                                    amount = lit(50_000),
                                    broke = {
                                        writeText(
                                            """
                                            - Sorry! 50,000! No more no less.
                                            Come back when you have the gold.
                                            """,
                                        )
                                        waitInput("after Bernak turns you away")
                                    },
                                    ok = {
                                        writeText(
                                            flowText(
                                                "He quietly slips you the precious map. It tells you that the Pit of the Key is at ",
                                                location(Locations.silverKeyPit).coordinates,
                                                "!",
                                            ),
                                        )
                                        journal {
                                            line("The Pit of the Silver Key is at ", location(Locations.silverKeyPit).coordinates, ".")
                                        }
                                        mark(Story.silverKey.mapBought)
                                        waitInput("after Bernak's map")
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
                        showTextAndWait(
                            """
                            - Rumor has it that Erethor's Lute, a musical instrument
                            with magical properties exists somewhere in the realm. {pause}

                            - Erethor is a mighty demon...
                            """,
                        )
                        showTextAndWait(
                            """
                            - I am a very poor man myself, but I know of the Cloak of
                            Gods, the most powerful of all armors. I will trade this
                            information if you find me Erethor's Lute.
                            """,
                        )
                        showTextAndWait(
                            """
                            One of Erethor's temples, where mad priests practice
                            blasphemous magic, is located at 6x20, near the western peaks.
                            """,
                        )
                        writeText(
                            """
                            - This may be a dangerous mission for such a small reward,
                            but the Cloak will make its bearer almost invulnerable.
                            """,
                        )
                        journal("Erethor's temple is situated at 6x20.")
                        mark(Story.erethor.lute.rumorTold)
                        waitInput("after Tybalt's offer")
                    }

                    whenState(Story.erethor.lute.luteTaken) {
                        writeText("You hand him the golden lute... {pause}")
                        showTextAndWait(
                            """
                            - I can't believe you actually made it!!!

                            - Now for my part of the deal. Listen closely, for I shall
                            say this only once.
                            """,
                        )
                        writeText(
                            flowText(
                                "- Take this note here and seek out my friend ",
                                character(Roles.minstrelCloak).name,
                                ". He is also a minstrel. ",
                                character(Roles.minstrelCloak).name,
                                " lives ",
                                character(Roles.minstrelCloak).location,
                                ".",
                            ),
                        )
                        journal {
                            line(character(Roles.minstrelCloak).name, " lives ", character(Roles.minstrelCloak).location, ".")
                        }
                        waitInput("after Tybalt names the minstrel")
                        showTextAndWait(
                            """
                            - We used to travel together, and he found the Cloak in a
                            long dead hero's tomb. Now that we have the Lute, the Cloak
                            is yours. Just deliver this explanatory note!
                            """,
                        )
                        writeText("He hands you a piece of paper and bids you farewell.")
                        mark(Story.erethor.lute.noteReceived)
                        waitInput("after Tybalt's note")
                    }
                }

                npc(Npcs.harry) {
                    whenState(Story.bracelets.thun.none) {
                        showTextAndWait(
                            """
                            - I have a map of the road to the temple of Thun! The legend
                            says it keeps the Bracelets of Thun, which are one of the
                            objects that can destroy Baron Ydarg!
                            """,
                        )
                        writeText("- I will sell it to you for 10,000 gold pieces!")
                        menu("(B)uy it", "(N)o thanks") {
                            option('B') {
                                pay(
                                    amount = lit(10_000),
                                    broke = {
                                        writeText(
                                            """
                                            - Sorry! 10,000! No more no less.
                                            Come back when you have the gold.
                                            """,
                                        )
                                        waitInput("after Harry turns you away")
                                    },
                                    ok = {
                                        writeText(
                                            flowText(
                                                "- Ok, here. He slowly hands you an old map.",
                                                " It tells you that the temple of Thun is situated at ",
                                                location(Locations.thunTemple).coordinates,
                                                "!",
                                            ),
                                        )
                                        journal {
                                            line("The Temple of Thun is at ", location(Locations.thunTemple).coordinates, ".")
                                        }
                                        mark(Story.bracelets.thun.mapBought)
                                        waitInput("after Harry's map")
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
                        writeText(
                            """
                            - OH! A message from my brother! This is indeed great news.
                            I will give you this sword for delivering the letter!
                            """,
                        )
                        appendText(
                            """
                            He produces a sparkling sword and wishes you a happy
                            journey.
                            """,
                        )
                        waitInput("after Marraga's sword")
                        clearText()
                        reward {
                            items(Items.magicSword)
                            promptDrop(
                                cantCarry = "You can't carry it!",
                                pickedUp = "You take the sword.",
                            )
                        }
                        mark(Story.ranger.letter.delivered)
                        waitInput("after Marraga's sword is taken")
                    }
                }

                npc(Npcs.groth) {
                    whenState(Story.nymph.seeker.none) {
                        showTextAndWait(
                            flowText(
                                "I heard that a ",
                                character(Roles.wizardThreeTasks).job,
                                " called ",
                                character(Roles.wizardThreeTasks).name,
                                " knows of the Crown. I have roamed the land in search of him,",
                                " since I want this item to be found.",
                            ),
                        )
                        writeText("Here! Take this ring and show it to him. You are better suited for this than I am.")
                        appendText(
                            flowText(character(Roles.wizardThreeTasks).name, " lives ", character(Roles.wizardThreeTasks).location, "."),
                        )
                        journal {
                            line(character(Roles.wizardThreeTasks).name, " lives ", character(Roles.wizardThreeTasks).location, ".")
                        }
                        mark(Story.nymph.seeker.told)
                        waitInput("after Groth's ring")
                    }
                }

                npc(Npcs.gotharp) {
                    whenState(Story.silverKey.noteFound) {
                        mark(Story.silverKey.thiefSqueezed)
                        showTextAndWait(
                            flowText(
                                "You push him up against a wall and start asking him questions.",
                                " After some 'mild' persuasion he tells you that he sold the Key to a traveling ",
                                character(Roles.silverKeySmith).job,
                                " who was interested in its metallurgical structure.",
                            ),
                        )
                        writeText(
                            flowText {
                                paragraph(
                                    "After breaking a few of his teeth, he also willingly reveals that the ",
                                    character(Roles.silverKeySmith).job,
                                    " went by the name of ",
                                    character(Roles.silverKeySmith).name,
                                    "... {pause}",
                                )
                                paragraph("You let go of your grip on his collar, and he falls to the floor.")
                            },
                        )
                        waitInput("after Gotharp talks")
                    }
                }

                npc(Npcs.ron) {
                    whenState(Story.peasant.wife.none) {
                        writeText(
                            """
                            - Oh, I'm so depressed. Orcs have kidnapped my wife, Tammy,
                            and demand a ransom for her. I will give you my entire
                            fortune if you retrieve her.
                            """,
                        )
                        waitInput("after Ron's plea")
                        writeText(
                            """
                            About 3,000 gold coins, a fine armor, and some strange items
                            my father left me, will be yours if you rescue her.
                            """,
                        )
                        appendText("The orcs have a cave at 42x23 in Lonewood.")
                        journal("The orc cavern is at 42x23.")
                        mark(Story.peasant.wife.accepted)
                        waitInput("after Ron names the orc cave")
                    }

                    whenState(Story.peasant.wife.rescued) {
                        showTextAndWait(
                            """
                            When he sees Tammy enter the room, he cries tears of happiness
                            and kisses her. When they have had this happy reunion, he
                            motions to you to follow.
                            """,
                        )
                        writeText(
                            """
                            He hands you 3,000 gold, an Adamantium Armor of advanced
                            craftsmanship and some other assorted objects: an amber
                            potion, a holy hand grenade, a fire wand, and a crystal.
                            """,
                        )
                        mark(Story.peasant.wife.paid)
                        reward {
                            gold(3_000)
                            items(Items.admtMail, Items.holyHandGrenade, Items.amberPotion, Items.fireWand, Items.crystalBowl)
                            promptDrop(cantCarry = flowText("You can't carry the ", itemName, "."))
                        }
                        waitInput("after Ron's reward")
                    }
                }

                npc(Npcs.kaal) {
                    whenState(Story.erethor.lute.noteReceived) {
                        showTextAndWait(
                            flowText(
                                "After reading the note ",
                                character(Roles.minstrelCloak).name,
                                " cries tears of happiness. He shakes all your hands",
                                " and produces a purple cloak.",
                            ),
                        )
                        reward {
                            items(Items.cloakOfGods)
                            promptDrop(
                                cantCarry = "You can't carry it!",
                                pickedUp = "You take the cloak.",
                            )
                        }
                        appendText(
                            flowText(
                                "He bids you farewell and tells you that he'll travel to ",
                                character(Roles.erethorLuteBard).name,
                                " the very next day...",
                            ),
                        )
                        mark(Story.erethor.lute.cloakReceived)
                        waitInput("after Kaal's farewell")
                    }
                }

                npc(Npcs.leander) {
                    whenState(Story.jazzy.ogre.none) {
                        showTextAndWait(
                            """
                            - ME HEAR YOU LOOK FOR POWER ITEMS!

                            - ME VERY POOR! SELL YOU INFORMATION ABOUT FOOT OF JAZZY, YES?
                            """,
                        )
                        showTextAndWait(
                            """
                            - LET US TRADE! OFFER ITEM AND I'LL TELL YA!

                            (You offer him something).
                            """,
                        )
                        ask.inventorySlot(literalText("Offer what?"), filter = InventorySlotChoiceFilter.NonEmpty) { slot ->
                            whenever(party.inventory.itemCost(slot) lt 35) {
                                writeText("- NAH, THINK NOT! {pause 1s}")
                            } otherwise {
                                writeText(
                                    flowText(
                                        "- YEEES! NICE NIICE! GO TO CAVE AT ",
                                        location(Locations.beastCave).coordinates,
                                        "! BEAST HAS THE FOOT.",
                                    ),
                                )
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
                }

                npc(Npcs.ithienn) {
                    whenState(Story.nymph.seeker.told) {
                        whenever(Story.masterCrown.none) {
                            showTextAndWait(
                                """
                                - Ah, a seeker! Yes, I know about the Master Crown, but
                                since I'm neither good nor evil, I have no interest in it.
                                I will give you the information if you do three tasks for me.
                                """,
                            )
                            showTextAndWait(
                                """
                                (Since there is nothing else you can do, you agree
                                to complete the three tasks).
                                """,
                            )
                            writeText(
                                flowText(
                                    "- Firstly, I want you to go to the kobold lair at ",
                                    location(Locations.koboldVillage).coordinates,
                                    ", and kill all life there. It is close to my home village,",
                                    " and the foul creatures have many times attacked us. Farewell.",
                                ),
                            )
                            journal {
                                line("The kobold lair is at ", location(Locations.koboldVillage).coordinates, ".")
                            }
                            mark(Story.nymph.seeker.wizard3tActive)
                            mark(Story.masterCrown.task1Assigned)
                            waitInput("after Ithienn's first task")
                        }
                    }

                    whenState(Story.masterCrown.task1Done) {
                        showTextAndWait(
                            """
                            You hand him the pendant. He stares at you, almost admiring.

                            - WELL DONE! I will now grant you the second quest.
                            """,
                        )
                        writeText(
                            """
                            - Being a magician, I'm always searching for powerful spell
                            reagents. There is one plant I need but can't find. It is
                            called Skullweed, and is purple with small green twigs.
                            Find this plant for me, and be rewarded!
                            """,
                        )
                        mark(Story.masterCrown.task2Assigned)
                        waitInput("after Ithienn's second task")
                    }

                    whenState(Story.masterCrown.task2Done) {
                        showTextAndWait(
                            """
                            - Well done again, adventurers. The third of my tasks will
                            perhaps be the toughest one of them all! Find me a DWARF
                            STONE. It is a stone of magnetic iron which gives the
                            wielder the power of levitation.
                            """,
                        )
                        writeText(
                            flowText(
                                "- Begin your search in the dwarven colony, near ",
                                location(Locations.borinCamp).coordinates,
                                ". Be sure to treat the dwarves with respect, for they are swift to anger.",
                            ),
                        )
                        journal {
                            line("The dwarf village is at ", location(Locations.borinCamp).coordinates, ".")
                        }
                        mark(Story.masterCrown.task3Assigned)
                        waitInput("after Ithienn's third task")
                    }

                    whenState(Story.masterCrown.borinPaid) {
                        writeText(
                            flowText {
                                paragraph("- I JUST CAN'T TELL YOU HOW GRATEFUL I AM! He takes the stone and smiles broadly at you.")
                                paragraph(
                                    "- Very well, seekers! The Master Crown is hidden in a dungeon near ",
                                    location(Locations.masterCrownDungeon).coordinates,
                                    "! Fare thee well.",
                                )
                            },
                        )
                        journal {
                            line("The Master Crown dungeon is at ", location(Locations.masterCrownDungeon).coordinates, ".")
                        }
                        mark(Story.masterCrown.crownTold)
                        waitInput("after Ithienn names the crown dungeon")
                    }
                }

                npc(Npcs.azaaza) {
                    whenState(Story.silverKey.thiefSqueezed) {
                        writeText(
                            """
                            You ask him if he still has the Key, prepared for the
                            worst... {pause 2s}
                            """,
                        )
                        showTextAndWait(
                            """
                            - Nah, sorry. I kinda studied it enough and was gonna bring
                            it to Ollk to sell it. However on the road between Wilor
                            and Ollk I was caught by filthy hobgoblins!
                            """,
                        )
                        showTextAndWait(
                            """
                            - I managed to get away, but they followed me! Just to the
                            southwest of here one of their arrows hit my horse.
                            """,
                        )
                        writeText(
                            """
                            - They clubbed me unconscious and took all my belongings!!!
                            Including that key. Find them and take it back. It's yours
                            if you get it.
                            """,
                        )
                        mark(Story.silverKey.smithConfessed)
                        waitInput("after Azaaza's story")
                    }
                }

                npc(Npcs.marcus) {
                    whenState(Story.hermit.trollTooth.none) {
                        writeText(
                            flowText(
                                "- Welcome, chosen ones! the ",
                                character(Npcs.marcus).job,
                                " cackles. - I know of the Fire Shield, an item of great power.",
                                " Go find me teeth of a troll, and I will tell you about it.",
                            ),
                        )
                        mark(Story.hermit.trollTooth.accepted)
                        waitInput("after the hermit's request")
                    }

                    whenState(Story.hermit.trollTooth.extracted) {
                        writeText(
                            flowText {
                                paragraph("You hand him the troll tooth.")
                                paragraph(
                                    "- Oh YES! He cackles. At areas uncharted, near ",
                                    location(Locations.hermitDungeon).coordinates,
                                    " is the cave you seek. Beware of the guardian the ancients gave birth to...",
                                )
                            },
                        )
                        journal {
                            line("The Fire Shield Dungeon is at ", location(Locations.hermitDungeon).coordinates, ".")
                        }
                        mark(Story.hermit.trollTooth.delivered)
                        waitInput("after the hermit names the cave")
                    }
                }
            }
        }
    }.withReplayScenarios()

// Replay scenarios are test/verification fixtures layered on the same content
// model so routes can boot from known deterministic states.
// Convenience handle for callers that need only the immutable game content.
val authoredDsl: GameModel = authoredRylween.content

// Points a potion restores or, for the poison potion, takes: 15 at level 1 as in
// the original game, growing with the drinker's level so potions stay worth
// carrying once hit points and spell points run into the hundreds.
private fun potionPoints(target: MemberRef): IntExpr = target.level * 3 + 12

// Fire damage shared by the Fire Cloud spell and the fire wand: every monster
// still standing takes caster.level * damageRoll points.
private fun ScriptScope.burnEveryLivingMonster(
    caster: MemberRef,
    damageRoll: IntRange,
) {
    forEachMonster(includeDead = true) { foe ->
        localInt(caster.level * random(damageRoll)) { damage ->
            whenever(!foe.dead) {
                writeText(flowText(partyName(caster), " burns ", monsterName(foe), " for ", damage, " pts! {pause 1s}"))

                foe.wound(damage)
                whenever(foe.dead) {
                    writeText("...KILLED! {pause 600ms}")
                }
            }
        }
    }
}
