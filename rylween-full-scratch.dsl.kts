// rylween-full-scratch.dsl.kts -- game-specific Rylween scratch source.
//
// This file should contain game declarations: flags, progress milestones,
// monsters, missions, scripts, and states. DSL implementation code belongs in
// rylween-dsl.kt.
//
// Validate with:
//
//   kotlinc rylween-dsl.kt -d /tmp/rylween-dsl.jar
//   kotlinc -classpath /tmp/rylween-dsl.jar -script rylween-full-scratch.dsl.kts

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

    // 3. Fire wand: body owns item consumption and preserves per-slot RNG.
    magicItem(Items.fireWand) {
        val caster = currentCaster
        take(Items.fireWand)
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
check(nextDsl.progress.milestones.size == 6)
check(nextDsl.progress.win?.required?.size == 5)
check(nextDsl.rewards.size == 1)
check(nextDsl.combat.standardBattle?.actions?.size == 5)
check(nextDsl.combat.standardBattle?.playerRound?.attack?.confusionDivisor == 3)
check(nextDsl.combat.standardBattle?.monsterRound?.melee?.damageDivisor == 6)
check(nextDsl.combat.standardBattle?.rewards == Rewards.standardBattle)
check(nextDsl.monsters.size == 2)
check(nextDsl.scenes.size == 4)
check(nextDsl.spells.size == 1)
check(nextDsl.magicItems.size == 1)
check(nextDsl.missions.size == 1)
check(nextRylween.states.size == 3)

val freshParty = nextRylween.states.single { it.id == States.freshReplay }.parties.single()
val nearWinningParty = nextRylween.states.single { it.id == States.nearWinningReplay }.parties.single()
val winningParty = nextRylween.states.single { it.id == States.winningReplay }.parties.single()

check(freshParty.members.size == 2)
check(specialInventoryItems(nextDsl, freshParty).isEmpty())
check(!isWinningConfiguration(nextDsl, freshParty))
check(specialInventoryItems(nextDsl, nearWinningParty).size == 5)
check(!isWinningConfiguration(nextDsl, nearWinningParty))
check(specialInventoryItems(nextDsl, winningParty).size == 5)
check(isWinningConfiguration(nextDsl, winningParty))
validateFlagCatalog(nextDsl.flags)
validateAuthoredContent(nextDsl)
validateGameProgress(nextDsl.progress)
validateStates(nextRylween.states, nextDsl)
check((nativePathsIn(nextDsl) - NativeAllowlist.byPath.keys).isEmpty())
