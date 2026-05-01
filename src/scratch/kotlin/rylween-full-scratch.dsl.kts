// rylween-full-scratch.dsl.kts -- game-specific Rylween scratch source.
//
// This file should contain game declarations: flags, progress milestones,
// monsters, missions, scripts, and states. DSL implementation code belongs in
// src/main/kotlin, with validation split into rylween-dsl-validation.kt.
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
                attack(hitBonus = 1, damageSpreadMax = 4, confusionDivisor = 3)
                defend(weaponSkillPenalty = 2, restThreshold = 5)
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

    // Scripts: six hard-case readability probes.

    scene(Scenes.Towne.church) {
        native.town.enterService(Buildings.church)
    }

    scene(Scenes.Towne.smith) {
        native.town.enterService(Buildings.smith)
    }

    scene(Scenes.Towne.inn) {
        native.town.enterService(Buildings.inn)
    }

    scene(Scenes.Towne.merchant) {
        native.town.enterService(Buildings.merchant)
    }

    scene(Scenes.Towne.tavern) {
        native.town.enterService(Buildings.tavern)
    }

    // 1. Church heal: typed member fields, typed flagless flow, no engine names.
    scene(Scenes.churchHeal) {
        native.screen.clearText()
        val m = ask.member(T.Towne.Church.who)

        when_(m.dead) {
            screen(T.Towne.Church.corpse)
            pause(1200)
            stop("corpse exits the heal service")
        }

        when_(m.hp eq m.maxHp) {
            bind("pro_sub", pronoun(m, PronounCase.Subject))
            screen(T.Towne.Church.noHealing)
            pause(1000)
            stop("already fully healed")
        }

        val price = let("cost", (m.maxHp - m.hp) * 7)
        screen(T.Towne.Church.cost)

        pay(price,
            broke = {
                screen(T.Towne.Church.noGold)
                pause(1000)
            },
            ok = {
                m.hp set m.maxHp
                bind("pro_obj", pronoun(m, PronounCase.Object))
                screen(T.Towne.Church.healed)
                pause(1000)
            },
        )
    }

    // 2. Mage frost: spell body is normal script, combat target is typed.
    spell(Spells.mageFrost, slot = 1, baseCost = 8, name = "Frost Finger") {
        val caster = currentCaster
        val target = ask.monsterAlive(T.Battle.Prompt.monster)
        val damage = let("dmg", caster.level * random(2..8))

        native.screen.clearText()
        bind("caster", partyName(caster))
        emit(T.Battle.Frost.chills)
        emit(T.Battle.Frost.damage)
        emit(T.Battle.Frost.damagePts)
        pause(1000)

        target.hp -= damage

        when_(target.hp lte 0) {
            target.hp set 0
            target.dead set true
            emit(T.Battle.killed)
            pause(600)
        }
    }

    // 3. Fire wand: item-use metadata owns consumption; the effect preserves per-slot RNG.
    magicItem(Items.fireWand) {
        usableIn(ItemUseContext.Combat)
        consume = ItemConsumePolicy.BeforeUse

        effect {
            val caster = currentCaster
            native.screen.clearText()
            bind("caster", partyName(caster))

            forEachMonster(includeDead = true) { foe ->
                val damage = let("dmg", caster.level * random(3..8))

                when_(!foe.dead) {
                    bind("monster_name", monsterName(foe))
                    emit(T.Battle.FireWand.burns)
                    emit(T.Battle.FireWand.points)
                    pause(1000)

                    foe.hp -= damage

                    when_(foe.hp lte 0) {
                        foe.hp set 0
                        emit(T.Battle.killed)
                        pause(600)
                        foe.dead set true
                    }
                }
            }
        }
    }

    // 4. Rumour: switch is readable, role bindings are typed handles.
    scene(Scenes.rumour) {
        val pick = let("pick", native.rumour.roll())

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

            val room = let("room", native.ydarg.roomAtCurrent())
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
                    when_(native.ydarg.exitRequested()) {
                        breakLoop("stairs requested exit")
                    }
                }
            }

            run(Scenes.ydargNavRender)
            val key = let("key", native.input.keyUpper())
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
            rngSeed = 12648430L
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
check(nextDsl.rewards.size == 1)
check(nextDsl.rewards.single().application.overflow == RewardOverflowPolicy.SkipIfInventoryFull)
check(nextDsl.combat.standardBattle?.actions?.size == 5)
check(nextDsl.combat.standardBattle?.playerRound?.attack?.confusionDivisor == 3)
check(nextDsl.combat.standardBattle?.monsterRound?.melee?.damageDivisor == 6)
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
check(nextDsl.monsters.size == 2)
check(nextDsl.scenes.size == 10)
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
check(nextDsl.spells.size == 1)
check(nextDsl.magicItems.size == 1)
check(nextDsl.magicItems.single().consume == ItemConsumePolicy.BeforeUse)
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
