// rylween-full-scratch.dsl.kts -- game-specific Rylween scratch source.
//
// This file should contain game declarations: flags, progress milestones,
// monsters, missions, scripts, and states. DSL implementation code belongs in
// src/main/kotlin; reusable current-game script helpers belong in
// src/game/kotlin/RylweenGameScripts.kt.
//
// Validate with:
//
//   ./gradlew check

// =============================================================================
// 06. Full-game scratch authoring area
// =============================================================================
//
// This section should eventually contain the whole game in the order below:
//
//   content:
//     metadata, limits, handles, visuals, layouts, terrain, maps, items,
//     monsters, spells, text, scenes, missions, towns, buildings, menus.
//
//   states:
//     authored start states first, then exact runtime-save-shaped fixtures.
//
// For now, it keeps the six hard-case readability probes from dsl-next.kts and
// the first state fixture. New migration passes should add real content here
// incrementally instead of creating another full transcription file.

val nextRylween = rylween {
    content {
    // Content tables are still skeletal in this first scratch pass. The next
    // passes should fill in visuals, layouts, terrain, maps, items, spells,
    // text declarations, menus, and the rest of the monster/mission tables here.

    flags {
        story(Story.garth.bandits)
        story(Story.ranger.letter)
        story(Story.fighter.priestBook)
        story(Story.church.donationOnce)
        story(Story.silverKey)
        story(Story.erethor.lute)
        story(Story.bracelets.thun)
        story(Story.nymph.seeker)
        story(Story.peasant.wife)
        story(Story.jazzy.ogre)
        story(Story.masterCrown)
        story(Story.hermit.trollTooth)
        encounter(Story.encounter.immune)
    }

    gameProgress {
        milestone(Milestones.fireShield) {
            showsAs(Items.fireshield)
            requiredToWin()
            legacyPartyField(LegacySpecialField.Fireshield)
        }

        milestone(Milestones.braceletsOfThun) {
            showsAs(Items.bracelets)
            requiredToWin()
            legacyPartyField(LegacySpecialField.Bracelets)
        }

        milestone(Milestones.masterCrown) {
            showsAs(Items.mastercrown)
            requiredToWin()
            legacyPartyField(LegacySpecialField.Mastercrown)
        }

        milestone(Milestones.footOfJazzy) {
            showsAs(Items.jazzyfoot)
            requiredToWin()
            legacyPartyField(LegacySpecialField.Jazzyfoot)
        }

        milestone(Milestones.silverKey) {
            showsAs(Items.silverkey)
            requiredToWin()
            legacyPartyField(LegacySpecialField.Silverkey)
        }

        milestone(Milestones.ydargVictory) {
            legacyPartyField(LegacySpecialField.Silverkey, achievedValue = 2)
        }

        winningConfiguration {
            requiresAllMilestonesMarkedRequired()
            completedBy(Milestones.ydargVictory)
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
            encounterFoeCountMax = 8

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

            playerActions {
                attack()
                defend()
                useMagicItem()
                battleCry()
                castMageSpell()
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
        events {
            caveChancePercent = 3
            quietChancePercent = 91
            encounterSplitPercent = 50
            easyEncounterTier = EncounterTiers.weak
            hardEncounterTier = EncounterTiers.wild
            caveDungeon = Dungeons.randomCave
            caveDepthRange = 3..16
            caveEntryCost = 10

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
            screen(T.Speak.recognizePre) {
                lines("{npc_name} nods as")
            }
            screen(T.Speak.recognizeHe) {
                lines("he recognizes you.")
            }
            screen(T.Speak.recognizeShe) {
                lines("she recognizes you.")
            }
            screen(T.Speak.tellsYouPre) {
                lines("{npc_name} tells you:", "")
            }
            screen(T.Speak.chatting) {
                lines("Chatting with", "{npc_name}:", "")
            }
            screen(T.Speak.menu) {
                lines("(C)hitchat", "(Q)uestion", "(R)umors", "(B)ye")
            }
            screen(T.Speak.question) {
                lines("(L)odging", "(M)agic", "(P)eople", "(N)othing")
            }
            screen(T.Speak.noRumour) {
                lines("- Sorry. There's", "nothing new I can", "tell you.", "")
            }
            screen(T.Speak.Lodge.bargirlOffer) {
                lines("- Would you like to,", "  spend an hour", "  upstairs for {price}", "  gold coins?", "")
            }
            screen(T.Speak.Lodge.bargirlBroke) {
                lines("- No money, eh?", "  don't bother", "  to come back!")
            }
            screen(T.Speak.Lodge.bargirlSpent) {
                lines("You spend a very", "exhausting hour", "upstairs.", "(PERVERT!) ;-)", "")
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
                // Bargirl lodging is an interactive service branch; its shared
                // screens are declared above under T.Speak.Lodge.
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
    }

    towns {
        town("thord") {
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
            }
        }

        town("ollk") {
            name = "Ollk"
            position = xy(48, 12)
            taxPercent = 10
            village()
            services {
                merchant()
                inn()
                tavern()
                smith()
            }
        }

        town("wilor") {
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
            }
        }

        town("murn") {
            name = "Murn"
            position = xy(55, 29)
            taxPercent = 8
            village()
            services {
                merchant()
                inn()
                tavern()
                smith()
            }
        }
    }

    townSystem {
        menu {
            church()
            smith()
            inn()
            merchant()
            tavern()
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
        monster(Monsters.bandit) {
            name = "Bandit"
            count = 2..6
            hitPoints = 50..99
            weaponSkill = 50
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
    }

    missions {
        mission(Missions.bandits) {
            randomStep(percent = 9)
            requires(Story.garth.bandits.accepted)
            runs(Scenes.Missions.bandits)
        }
    }

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
                    val m = choice.member(T.Towne.Church.who)

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
                    }

                    whenever(m.hp eq m.maxHp) {
                        screen {
                            line(pronoun(m, PronounCase.Subject), " needs no healing.")
                        }
                        pause(1000)
                        stop("already fully healed")
                    }

                    val price = localInt((m.maxHp - m.hp) * 7)
                    native.screen.clearText()
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
                            native.screen.clearText()
                            m.hp set m.maxHp
                            screen {
                                line("The priestess heals")
                                line(pronoun(m, PronounCase.Object), " fully.")
                            }
                            pause(1000)
                        },
                    )
                }

                option('E') {
                    native.screen.clearText()
                    val m = choice.member(T.Towne.Church.who)

                    whenever(!m.spellcaster) {
                        native.screen.clearText()
                        screen("Not a spellcaster!")
                        pause(1000)
                        stop("not a spellcaster")
                    }

                    whenever(m.mp eq m.wisdom) {
                        native.screen.clearText()
                        screen {
                            lines(
                                "That character needs",
                                "no spell points",
                                "to be restored.",
                            )
                        }
                        pause(2000)
                        stop("spell points already restored")
                    }

                    native.screen.clearText()
                    val price = localInt(m.level * (m.wisdom - m.mp) * 2)
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
                            m.mp set m.wisdom
                            native.screen.clearText()
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

                option('R') {
                    native.screen.clearText()
                    val m = choice.member(T.Towne.Church.who)

                    whenever(!m.dead) {
                        native.screen.clearText()
                        screen {
                            line("- ", pronoun(m, PronounCase.Subject), " seems very alive")
                            line("at the moment.")
                        }
                        pause(1200)
                        stop("already alive")
                    }

                    native.screen.clearText()
                    val price = localInt(m.level * 650)
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
                            m.hp set 1
                            m.dead set false
                            party.memberCount += 1
                            native.screen.clearText()
                            screen {
                                line("The priestess raises")
                                line(pronoun(m, PronounCase.Object), ".")
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
                    page("N. Next Page") {
                        item('1', Items.sword, label = "Sword")
                        item('2', Items.axe, label = "Axe")
                        item('3', Items.bow, label = "Bow")
                        item('4', Items.club, label = "Club")
                        item('5', Items.halbard, label = "Halbard")
                        item('6', Items.crossbow, label = "Crossbow")
                    }
                    page("P. Previous Page") {
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
                smithHaggle()
            }
        }
    }

    scene(Scenes.Towne.inn) {
        native.screen.clearMapWindow()
        native.screen.showImage(Images.Scene.inn)

        loop {
            val cost = localInt((party.leader.level * party.leader.level) * 15)

            native.screen.clearText()
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
                        native.screen.clearText()
                        screen {
                            line("Sorry, supper is ", cost)
                            line("gold.")
                        }
                        pause(1000)
                    } otherwise {
                        party.gold -= cost
                        native.screen.clearText()
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

            native.screen.clearText()
            choice.yesNo("Anything to drink?", "",
                yes = {
                    native.screen.clearText()
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
                                    native.screen.clearText()
                                    screen("No money - No drink!")
                                    pause(1000)
                                },
                                ok = {
                                    native.screen.clearText()
                                    screen("Ahh...!")
                                    pause(1000)
                                    healLivingMembers((lit(2) / 2) + 1)
                                },
                            )
                        }
                        option('B') {
                            pay(lit(3),
                                broke = {
                                    native.screen.clearText()
                                    screen("No money - No drink!")
                                    pause(1000)
                                },
                                ok = {
                                    native.screen.clearText()
                                    screen("Ahh...!")
                                    pause(1000)
                                    healLivingMembers((lit(3) / 2) + 1)
                                },
                            )
                        }
                        option('C') {
                            pay(lit(5),
                                broke = {
                                    native.screen.clearText()
                                    screen("No money - No drink!")
                                    pause(1000)
                                },
                                ok = {
                                    native.screen.clearText()
                                    screen("Ahh...!")
                                    pause(1000)
                                    healLivingMembers((lit(5) / 2) + 1)
                                },
                            )
                        }
                        option('D') {
                            pay(lit(7),
                                broke = {
                                    native.screen.clearText()
                                    screen("No money - No drink!")
                                    pause(1000)
                                },
                                ok = {
                                    native.screen.clearText()
                                    screen("Ahh...!")
                                    pause(1000)
                                    healLivingMembers((lit(7) / 2) + 1)
                                },
                            )
                        }
                        option('E') {
                            pay(lit(1),
                                broke = {
                                    native.screen.clearText()
                                    screen("No money - No drink!")
                                    pause(1000)
                                },
                                ok = {
                                    native.screen.clearText()
                                    screen("The other guests giggle.")
                                    pause(1000)
                                    healLivingMembers((lit(1) / 2) + 1)
                                },
                            )
                        }
                    }
                },
            )

            native.screen.clearText()
            choice.yesNo("Are you in need", "of lodging?", "",
                yes = {
                    native.screen.clearText()
                    screen {
                        line("A room is ", cost, " gold/night.")
                        line("")
                    }

                    choice.takeOrLeave(
                        inlineText("(T)ake it", "(L)eave"),
                        take = {
                            whenever(party.gold lt cost) {
                                native.screen.clearText()
                                screen {
                                    lines(
                                        "- AHA! No gold!",
                                        "You are thrown out.",
                                    )
                                }
                                pause(2000)
                                native.screen.clearMapWindow()
                                breakLoop("thrown out of inn")
                            } otherwise {
                                party.gold -= cost
                                native.screen.clearText()
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
                                native.screen.clearMapWindow()
                                breakLoop("slept at inn")
                            }
                        },
                        leave = {
                            native.screen.clearText()
                            native.screen.clearMapWindow()
                            breakLoop("leave inn")
                        },
                    )
                },
                no = {
                    loop {
                        native.screen.clearText()
                        screen("The innkeeper leaves.", "")
                        menu("(C)hitchat with guests", "(L)eave") {
                            option('C') {
                                speak(Buildings.tavern)
                            }
                            option('L') {
                                native.screen.clearText()
                                native.screen.clearMapWindow()
                                breakLoop("leave inn chitchat")
                            }
                            default {
                                native.screen.clearText()
                                native.screen.clearMapWindow()
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
                        cap = ShopQuantityCaps.torchLimit80,
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
                        cap = ShopQuantityCaps.foodByDonkeys,
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
                sold("Sold!")
            }
        }
    }

    scene(Scenes.Towne.tavern) {
        whenever((realm.hour lt 6) and (realm.hour gt 1)) {
            native.screen.clearText()
            screen("Tavern Closed.")
            pause(1000)
            stop("tavern closed")
        }

        native.screen.clearMapWindow()
        native.screen.showImage(Images.Scene.tavern)

        val priestBonus = localInt(0)
        forEachMember(includeDead = true) { member ->
            whenever(member.isClass(PcClass.Priest)) {
                priestBonus += member.charisma / 10
            }
        }

        whenever(chance(priestBonus + 5)) {
            native.screen.clearText()
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
            native.screen.clearText()
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
            native.screen.clearText()
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
                    native.screen.clearText()
                    native.screen.clearMapWindow()
                    breakLoop("leave tavern")
                }

                option('S') {
                    speak(Buildings.inn)
                }

                option('T') {
                    travellerStory()
                }

                option('R') {
                    native.screen.clearText()
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
                        run(Scenes.rumour)
                        waitInput("after tavern rumour")
                    }
                }

                option('O') {
                    native.screen.clearText()
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
                                    native.screen.clearText()
                                    screen("No money - No drink!")
                                    pause(1000)
                                },
                                ok = {
                                    native.screen.clearText()
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
                                    native.screen.clearText()
                                    screen("No money - No drink!")
                                    pause(1000)
                                },
                                ok = {
                                    native.screen.clearText()
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
                                    native.screen.clearText()
                                    screen("No money - No drink!")
                                    pause(1000)
                                },
                                ok = {
                                    native.screen.clearText()
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
                                    native.screen.clearText()
                                    screen("No money - No drink!")
                                    pause(1000)
                                },
                                ok = {
                                    native.screen.clearText()
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

    // Battle magic: the reusable battle runner owns turn order, menus,
    // casting cost, magic immunity, rewards, and monster AI. These blocks
    // declare this game's spell and combat-item effects.
    spell(Spells.mageFrost, slot = 1, baseCost = 8, name = "Frost Finger") {
        val caster = currentCaster
        val target = ask.monsterAlive(T.Battle.Prompt.monster)
        val damage = localInt(caster.level * random(2..8))

        native.screen.clearText()
        screen {
            line("Frost bites the target")
            line("for ", damage, " damage pts.")
        }
        pause(1000)

        target.hp -= damage

        whenever(target.hp lte 0) {
            target.hp set 0
            target.dead set true
            emit(T.Battle.killed)
            pause(600)
        }
    }

    spell(Spells.Mage.fire, slot = 2, baseCost = 25, name = "Fire Cloud") {
        val caster = currentCaster

        forEachMonster(includeDead = true) { foe ->
            val damage = localInt(caster.level * random(1..4))

            whenever(!foe.dead) {
                native.screen.clearText()
                screen {
                    line("Fire burns a foe")
                    line("for ", damage, " pts!")
                }
                pause(1000)

                foe.hp -= damage

                whenever(foe.hp lte 0) {
                    foe.hp set 0
                    emit(T.Battle.killed)
                    pause(600)
                    foe.dead set true
                }
            }
        }
    }

    spell(Spells.Mage.confusion, slot = 3, baseCost = 17, name = "Confusion") {
        native.screen.clearText()
        screen {
            lines(
                "Your images waver and",
                "blur in front of your",
                "enemies.",
            )
        }
        pause(2000)
        battle.monsterWeaponSkill /= 2
        battle.monsterConfused set true
    }

    spell(Spells.Mage.death, slot = 4, baseCost = 78, name = "Death Strike") {
        val target = ask.monsterAlive(T.Battle.Prompt.monster)

        native.screen.clearText()
        whenever(random(0..199) lt target.hp) {
            screen {
                lines(
                    "Through incredible",
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
                    "straight off the surface",
                    "of the earth!",
                )
            }
            pause(2000)
            target.dead set true
            target.hp set 0
        }
    }

    spell(Spells.Mage.hold, slot = 5, baseCost = 43, name = "Hold Enemies") {
        native.screen.clearText()
        screen {
            lines("The enemies freeze dead", "in their tracks.")
        }
        pause(2000)
        battle.monstersHeld set true
    }

    spell(Spells.Mage.antimagic, slot = 6, baseCost = 28, name = "Anti Magic") {
        native.screen.clearText()
        screen {
            lines(
                "The enemy is now",
                "stopped from using magic",
                "against you this round.",
            )
        }
        pause(2800)
        battle.antimagic set true
    }

    spell(Spells.Monster.frost, slot = 1, baseCost = 0, name = "Frost Finger") {
        val target = battle.randomAliveMember()
        val damage = localInt((battle.encounterHitPointMax / 5) + random(0..3))

        screen {
            line("Frost Finger")
            line("does ", damage, " pts")
            line("of damage!")
        }
        pause(1000)
        target.hp -= damage
        whenever(target.hp lte 0) {
            target.hp set 0
            target.dead set true
            screen("The target dies!")
            pause(1000)
        }
    }

    spell(Spells.Monster.fire, slot = 2, baseCost = 0, name = "Cloud of Fire") {
        screen("Cloud of Fire!")
        pause(1000)
        forEachMember { target ->
            val damage = localInt((battle.encounterHitPointMax / 5) + random(0..4))
            native.screen.clearText()
            screen {
                line("A party member is burnt")
                line("for ", damage, " damage pts!")
            }
            pause(1000)
            target.hp -= damage
            whenever(target.hp lte 0) {
                target.hp set 0
                target.dead set true
                screen("The target dies!")
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

    magicItem(Items.frostWand) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            val caster = currentCaster
            val target = battle.firstAliveMonster()
            val damage = localInt(random(5..11) + caster.level)

            native.screen.clearText()
            screen {
                line("The frost wand chills")
                line("for ", damage, " damage pts.")
            }
            pause(1000)

            target.hp -= damage

            whenever(target.hp lte 0) {
                target.hp set 0
                emit(T.Battle.killed)
                pause(600)
                target.dead set true
            }
        }
    }

    magicItem(Items.fireWand) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            val caster = currentCaster
            native.screen.clearText()

            forEachMonster(includeDead = true) { foe ->
                val damage = localInt(caster.level * random(3..8))

                whenever(!foe.dead) {
                    native.screen.clearText()
                    screen {
                        line("The fire wand burns")
                        line("for ", damage, " pts!")
                    }
                    pause(1000)

                    foe.hp -= damage

                    whenever(foe.hp lte 0) {
                        foe.hp set 0
                        emit(T.Battle.killed)
                        pause(600)
                        foe.dead set true
                    }
                }
            }
        }
    }

    magicItem(Items.gemOfConfusion) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            native.screen.clearText()
            screen {
                lines(
                    "The gem breaks before",
                    "the enemies in a rain",
                    "of sparks.",
                    "They bump into each",
                    "other as they try to",
                    "see you.",
                )
            }
            waitInput("after gem of confusion")
            battle.monsterWeaponSkill /= 2
        }
    }

    magicItem(Items.gemOfBlindness) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            native.screen.clearText()
            screen {
                lines(
                    "The gem releases a",
                    "brilliant cloud of light",
                    "around the enemies.",
                )
            }
            waitInput("after gem of blindness")
            battle.monsterWeaponSkill set 5
        }
    }

    magicItem(Items.drumsOfDeath) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            val target = battle.firstAliveMonster()

            native.screen.clearText()
            screen("The drums summon death", "to take a foe away...")
            pause(2000)
            emit(T.Battle.killed)
            pause(600)
            target.dead set true
            target.hp set 0
        }
    }

    magicItem(Items.staffOfHealing) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            val maxMembers = realm.partyMemberMax
            val target = ask.member(
                structuredText {
                    line("On Whom? (1-", maxMembers, ")")
                },
                filter = MemberChoiceFilter.AliveOnly,
            )

            native.screen.clearText()
            whenever(!target.dead) {
                target.fullHeal()
            }
            screen("The target is fully", "healed.")
            pause(1000)
        }
    }

    magicItem(Items.blessingScroll) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            native.screen.clearText()
            screen {
                lines(
                    "The party feels their",
                    "spirits grow. You are",
                    "all stronger than",
                    "before.",
                )
            }
            waitInput("after blessing scroll")

            forEachMember(includeDead = true) { member ->
                member.weaponSkill += 3
                member.maxHp += 3
                member.charisma += 3
                member.wisdom += 3
            }
        }
    }

    magicItem(Items.staffOfCuring) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            native.screen.clearText()
            screen("The party is fully", "healed!")
            waitInput("after staff of curing")

            forEachMember(includeDead = true) { member ->
                whenever(!member.dead) {
                    member.fullHeal()
                }
            }
        }
    }

    magicItem(Items.gemOfResurrection) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.ScriptControlled

        effect {
            val maxMembers = realm.partyMemberMax
            val target = ask.member(
                structuredText {
                    line("On Whom? (1-", maxMembers, ")")
                },
            )

            native.screen.clearText()
            whenever(target.dead) {
                screen("The target opens", "their eyes!")
                pause(1000)
                target.dead set false
                target.hp set 1
                consumeActiveItem()
            } otherwise {
                screen("The target is not dead.")
                pause(1000)
                battle.actionRetry set true
            }
        }
    }

    magicItem(Items.kaelsAxe) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            native.screen.clearText()
            screen("Kael's axe wounds", "the enemies severely.")
            pause(4000)

            forEachMonster { foe ->
                foe.hp set random(1..3)
            }
        }
    }

    magicItem(Items.holyHandgrenade) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            native.screen.clearText()
            screen("KA-BLAM!")
            pause(1500)

            forEachMonster { foe ->
                foe.hp /= 4
                foe.hp += 1
            }
        }
    }

    magicItem(Items.fluteOfMadness) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            native.screen.clearText()
            screen {
                lines(
                    "The flute produces a",
                    "shrill, fragile sound,",
                    "and breaks.",
                    "Nothing happens.",
                )
            }
            waitInput("after flute of madness")
        }
    }

    // 4. Rumour: switch is readable, role bindings are typed handles.
    scene(Scenes.rumour) {
        val pick = localInt(native.rumour.roll())

        choose(pick) {
            case(12) {
                bind("name1", npcName(Roles.banditsQuestGiver))
                emit(T.Towne.Rumour.r12)
            }
            case(13) {
                bind("name2", npcName(Roles.rangersQuestGiver))
                emit(T.Towne.Rumour.r13)
            }
            case(25) {
                bind("name15", npcName(Roles.crownSeer))
                bind("loc15", npcLocation(Roles.crownSeer))
                emit(T.Towne.Rumour.r25)
            }
            case(27) {
                bind("name13", npcName(Roles.templeOfThunSeer))
                bind("loc13", npcLocation(Roles.templeOfThunSeer))
                emit(T.Towne.Rumour.r27)
            }
            default {
                emitIndexed(T.Towne.Rumour.indexed, pick)
            }
        }
    }

    // 5. Ydarg main: the native island is explicit and narrow.
    scene(Scenes.ydargMain) {
        native.ydarg.init()
        waitInput("after castle intro")

        loop {
            native.screen.clearText()

            val room = localInt(native.ydarg.roomAtCurrent())
            choose(room) {
                case(1) {
                    native.ydarg.clearRoomAtCurrent()
                    run(Scenes.ydargRoomMonster)
                }
                case(2) {
                    run(Scenes.ydargPitfall)
                }
                case(3) {
                    run(Scenes.ydargTreasure)
                }
                case(50) {
                    run(Scenes.ydargStairs)
                    whenever(native.ydarg.exitRequested()) {
                        breakLoop("stairs requested exit")
                    }
                }
            }

            run(Scenes.ydargNavRender)
            val key = localInt(native.input.keyUpper())
            native.ydarg.tryMove(key)
            run(Scenes.ydargCommandDispatch, "key" to key)
        }
    }

    // 6. Mission action scene: flag states are fluent typed markers, not strings.
    scene(Scenes.Missions.bandits) {
        native.screen.clearText()
        screen(T.MissionsText.Bandits.spotted)
        screen(T.MissionsText.Bandits.warned)
        encounter(Monsters.bandit, mandatory = true)
        native.screen.redrawMap()
        native.screen.clearText()
        screen(T.MissionsText.Bandits.recovered)
        mark(Story.garth.bandits.recovered)
    }

    // 7. Script reward: semantic items, typed placement policy, no numeric ids.
    scene(Scenes.Missions.borinGift) {
        native.screen.clearText()
        screen(
            "Borin greets you. He",
            "can hardly believe his",
            "eyes when you show him",
            "the dagger. He motions",
            "to you to follow him,",
            "and guides you to his",
            "hut.",
            "",
        )
        screen(
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
    }

    }

    // Authored start state fixture. It can stay compact and readable.
    stateFixture(States.freshReplay) {
        realm {
            day = 14
            hour = 9
            rngState = 1L
            activeParty = Parties.replay
            currentMap = Maps.wilderness
            currentMode = RuntimeMode.Wilderness
        }

        locations {
            Locations.silverKeyPit at xy(45, 13)
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
                    mana = 0 manaOf 27
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
                    mana = 0 manaOf 48
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
            journal(T.MissionsText.Bandits.recovered)
        }
    }

    // Near-winning authored fixture: all required special milestones are present,
    // but the Ydarg completion marker is not.
    stateFixture(States.nearWinningReplay) {
        realm {
            day = 14
            hour = 22
            activeParty = Parties.replay
            currentMap = Maps.wilderness
            currentMode = RuntimeMode.Wilderness
        }

        locations {
            Locations.silverKeyPit at xy(45, 13)
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
                timeLeft = 25
                restedHours = 6
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
                    klass = PcClass.Thief
                    level = 1
                    health = 68
                    hitPoints = 68 of 68
                    weaponSkill = 27
                    wisdom = 27
                    mana = 0 manaOf 27
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
                    mana = 0 manaOf 48
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
            journal(T.MissionsText.Bandits.recovered)
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
            currentMap = Maps.wilderness
            currentMode = RuntimeMode.GameOver
            currentTown = null
            banished = false
            lastPlayedDay = 14
            partyMemberMax = 2
        }

        locations {
            Locations.silverKeyPit at xy(45, 13)
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
                    mana = 0 manaOf 27
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
                    mana = 0 manaOf 48
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
                entry(T.MissionsText.Bandits.recovered)
                line("Castle Ydarg has fallen.")
            }
        }
    }
}

val nextDsl = nextRylween.content

check(nextDsl.flags.size == 13)
check(nextDsl.authoredFlags.size == 13)
check(ItemCatalog.all.size == 63)
check(ItemCatalog.all.single { it.id == Items.redPotion }.potionEffect?.amount == 15)
check(nextDsl.progress.milestones.size == 6)
check(nextDsl.progress.win?.required?.size == 5)
check(nextDsl.leveling?.maxLevel == 28)
check(nextDsl.leveling?.classGrowth?.size == 6)
check(nextDsl.formulas.size == 5)
check(nextDsl.rewards.size == 1)
check(nextDsl.rewards.single().application.overflow == RewardOverflowPolicy.SkipIfInventoryFull)
check(nextDsl.combat.standardBattle?.actions?.size == 5)
check(nextDsl.combat.standardBattle?.actions?.map { it.key } == listOf('A', 'D', 'U', 'B', 'C'))
check(nextDsl.combat.standardBattle?.playerRound?.attack?.confusionDivisor == 3)
check(nextDsl.combat.standardBattle?.playerRound?.attack?.hpDefenseDivisor == 12)
check(nextDsl.combat.standardBattle?.playerRound?.defend?.cap == 250)
check(nextDsl.combat.standardBattle?.monsterRound?.melee?.damageDivisor == 6)
check(nextDsl.combat.standardBattle?.encounterFoeCountMax == 8)
check(nextDsl.combat.standardBattle?.rewards == Rewards.standardBattle)
check(nextDsl.speak.standardConversation?.menu?.actions?.map { it.key } == listOf('C', 'Q', 'R', 'B'))
check(nextDsl.speak.standardConversation?.menu?.questions?.map { it.key } == listOf('L', 'M', 'P', 'N'))
check(nextDsl.speak.standardConversation?.branchSelection == DialogueBranchSelection.FirstMatchingStateThenDefault)
check(nextDsl.speak.standardConversation?.rumour?.usage == RumourUsagePolicy.OncePerConversationThenFallback)
check(nextDsl.speak.textTables.screens.size == 12)
check(nextDsl.speak.textTables.chitchatPrompts.size == 3)
check(nextDsl.speak.textTables.chitchatReplies.size == 3)
check(nextDsl.speak.textTables.questionReplies.size == 3)
check(nextDsl.speak.textTables.chitchatReplies.all { it.replies.size == NpcJobs.all.size })
check(nextDsl.speak.textTables.questionReplies.single { it.topic == QuestionTopic.People }.replies.size == NpcJobs.all.size)
check(nextDsl.speak.textTables.questionReplies.single { it.topic == QuestionTopic.Magic }.replies.size == NpcJobs.all.size)
check(nextDsl.speak.textTables.questionReplies.single { it.topic == QuestionTopic.Lodging }.replies.size == NpcJobs.all.size - 1)
check(nextDsl.speak.textTables.rumourTables.single { it.id == RumourTables.standardTown }.entries.size == 33)
check(nextDsl.speak.textTables.rumourTables.single().entries.count { it.id == null } == 29)
check(nextDsl.speak.textTables.rumourTables.single().entries.any { it.id == Rumours.crownSeer })
check(nextDsl.speak.textTables.rumourTables.single().entries.single {
    it.id == Rumours.banditsQuestGiver
}.excludedBy == listOf(Story.garth.bandits.recovered, Story.garth.bandits.closed))
check(nextDsl.towns.size == 4)
check(nextDsl.towns.single { it.id == Towns.murn.id }.position == xy(55, 29))
check(nextDsl.towns.single { it.id == Towns.wilor.id }.kind == TownKind.City)
check(nextDsl.townSystem?.menu?.map { it.key } == listOf('C', 'S', 'I', 'M', 'T', 'L'))
check(nextDsl.townSystem?.menu?.single { it.key == 'C' }?.building == Buildings.church)
check(nextDsl.townSystem?.menu?.single { it.action == KeyActions.leaveTown }?.endsMenu == true)
check(nextDsl.townSystem?.buildingServices?.size == 5)
check(nextDsl.townSystem?.buildingServices?.single { it.building == Buildings.merchant }?.opensAt == 6)
check(nextDsl.townSystem?.buildingServices?.single { it.building == Buildings.tavern }?.closesAt == 1)
check(nextDsl.characters.size == 39)
check(nextDsl.characters.single { Roles.crownSeer in it.roles }.placement == CharacterPlacementModel.Randomized)
check(nextDsl.characters.single { it.id == Npcs.marcus }.placement == CharacterPlacementModel.Fixed(Wilderness.xy(48, 7)))
check(listOf(Npcs.alex, Npcs.galdon, Npcs.kosch, Npcs.ufarsin).all { npc ->
    nextDsl.characters.single { it.id == npc }.placement == CharacterPlacementModel.RandomizedWilderness
})
check(nextDsl.characters.flatMap { it.roles }.toSet() == NpcRoles.all.toSet())
check(nextDsl.monsters.size == 3)
check(nextDsl.scenes.size == 9)
val churchScene = nextDsl.scenes.single { it.id == Scenes.Towne.church }
val churchMenu = churchScene.ops.filterIsInstance<Op.Loop>().single().body.filterIsInstance<Op.Menu>().single()
check(churchMenu.options.map { it.key } == listOf('H', 'E', 'R', 'L'))
check(churchScene.ops.none { it is Op.NativeCall && it.path == NativeAllowlist.townEnterService.path })
val smithScene = nextDsl.scenes.single { it.id == Scenes.Towne.smith }
check(smithScene.ops.none { it is Op.NativeCall && it.path == NativeAllowlist.townEnterService.path })
val smithShop = smithScene.ops.filterIsInstance<Op.ShopService>().single().shop
check(smithShop.building == Buildings.smith)
check(smithShop.buy?.inventoryApplication?.overflow == RewardOverflowPolicy.PromptDropThenPlace)
check(smithShop.buy?.catalogs?.single { it.key == 'A' }?.pages?.single()?.entries?.map { it.item } == listOf(
    Items.leather,
    Items.chainMail,
    Items.plateMail,
    Items.ringMail,
    Items.elvinCloak,
    Items.mithrilMail,
))
check(smithShop.sell?.acceptedKinds == setOf(ItemKind.Weapon, ItemKind.Armor))
check(smithShop.sell?.bargain == ShopSellBargainPolicy.SmithHaggle)
val innScene = nextDsl.scenes.single { it.id == Scenes.Towne.inn }
check(innScene.ops.none { it is Op.NativeCall && it.path == NativeAllowlist.townEnterService.path })
check(innScene.ops.filterIsInstance<Op.Loop>().single().body.any { it is Op.Menu })
val merchantScene = nextDsl.scenes.single { it.id == Scenes.Towne.merchant }
check(merchantScene.ops.none { it is Op.NativeCall && it.path == NativeAllowlist.townEnterService.path })
val merchantShop = merchantScene.ops.filterIsInstance<Op.ShopService>().single().shop
check(merchantShop.building == Buildings.merchant)
check(merchantShop.buy?.inventoryApplication?.overflow == RewardOverflowPolicy.PromptDropThenPlace)
check(merchantShop.buy?.catalogs?.single()?.pages?.single()?.entries?.map { it.item } == listOf(
    Items.torch,
    Items.ration,
    Items.blanket,
    Items.tent,
    Items.rope,
    Items.redPotion,
    Items.donkey,
))
check(merchantShop.sell?.acceptedItems == setOf(Items.ration, Items.blanket, Items.tent, Items.rope))
check(merchantShop.sell?.rejectedItems == setOf(Items.donkey))
val tavernScene = nextDsl.scenes.single { it.id == Scenes.Towne.tavern }
check(tavernScene.ops.none { it is Op.NativeCall && it.path == NativeAllowlist.townEnterService.path })
check(ItemPools.tavernScruffySpecials.items == listOf(
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
))
check(tavernScene.ops.any { op ->
    op is Op.If && op.thenOps.any {
        it is Op.RandomItemOffer &&
                it.offer.pool == ItemPools.tavernScruffySpecials &&
                it.offer.application.overflow == RewardOverflowPolicy.PromptDropThenPlace
    }
})
val tavernMenu = tavernScene.ops.filterIsInstance<Op.Loop>().single().body.filterIsInstance<Op.Menu>().single()
check(tavernMenu.options.map { it.key } == listOf('L', 'S', 'T', 'R', 'O'))
check(nextDsl.scenes.single { it.id == Scenes.Missions.borinGift }.ops.any { it is Op.GrantReward })
check(nextDsl.dialogue.size == 1)
check(nextDsl.dialogue.single().branches.size == 2)
check(nextDsl.dialogue.single().branches.any { branch ->
    branch.whenState == Story.garth.bandits.recovered &&
            branch.ops.any { op ->
                op is Op.GrantReward &&
                        op.reward.application.overflow == RewardOverflowPolicy.PromptDropThenPlace
            }
})
check(nextDsl.spells.size == 10)
check(nextDsl.magicItems.size == 12)
check(nextDsl.magicItems.single { it.id == Items.fireWand }.consume == ItemConsumePolicy.BeforeUse)
check(nextDsl.magicItems.single { it.id == Items.gemOfResurrection }.consume == ItemConsumePolicy.ScriptControlled)
check(nextDsl.missions.size == 1)
check(nextRylween.states.size == 3)

val freshParty = nextRylween.states.single { it.id == States.freshReplay }.parties.single()
val nearWinningParty = nextRylween.states.single { it.id == States.nearWinningReplay }.parties.single()
val winningParty = nextRylween.states.single { it.id == States.winningReplay }.parties.single()
val winningSnapshotDsl = renderRuntimeStateSnapshot(nextRylween.states.single { it.id == States.winningReplay })

check(freshParty.members.size == 2)
check(specialInventoryItems(nextDsl, freshParty).isEmpty())
check(!isWinningConfiguration(nextDsl, freshParty))
check(specialInventoryItems(nextDsl, nearWinningParty).size == 5)
check(!isWinningConfiguration(nextDsl, nearWinningParty))
check(specialInventoryItems(nextDsl, winningParty).size == 5)
check(isWinningConfiguration(nextDsl, winningParty))
check(winningSnapshotDsl.contains("runtimeState(stateId(\"winning-replay\"))"))
check(winningSnapshotDsl.contains("slot(16).empty()"))
check(winningSnapshotDsl.contains("mark(runtimeFlagState(\"garth.bandits\", \"recovered\"))"))
validateFlagCatalog(nextDsl.flags)
validateAuthoredContent(nextDsl)
validateGameProgress(nextDsl.progress)
validateStates(nextRylween.states, nextDsl)
check((nativePathsIn(nextDsl) - NativeAllowlist.byPath.keys).isEmpty())
