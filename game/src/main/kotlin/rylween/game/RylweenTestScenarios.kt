// RylweenTestScenarios.kt -- optional runtime replay fixtures.
//
// Designer note: this file contains deterministic replay seeds used by
// build tooling for smoke, save/load, and replay coverage. It is woven
// into the authored game via RylweenModel.withReplayScenarios(), which
// is called at the end of RylweenGame.kt.

package rylween.game

import rylween.dsl.*
import rylween.dsl.impl.*

val replayRandomNpcIds =
    listOf(
        Npcs.nrimTo,
        Npcs.areth,
        Npcs.nevaak,
        Npcs.gorm,
        Npcs.bernak,
        Npcs.matias,
        Npcs.tybalt,
        Npcs.romeo,
        Npcs.werra,
        Npcs.arenjun,
        Npcs.oskar,
        Npcs.harry,
        Npcs.marraga,
        Npcs.broth,
        Npcs.ebond,
        Npcs.bolph,
        Npcs.gotharp,
        Npcs.ron,
        Npcs.kaal,
        Npcs.daniel,
        Npcs.leander,
        Npcs.ithienn,
        Npcs.azaaza,
    )

fun NpcStateBuilder.stageRandomNpcsAwayFromTownServices(vararg keepPlaced: NpcId) {
    val keep = keepPlaced.toSet()
    replayRandomNpcIds
        .filterNot(keep::contains)
        .forEachIndexed { index, npc ->
            npc at Wilderness.xy(8 + index, 8)
        }
}

fun RylweenModel.withReplayScenarios(): RylweenModel = withStateModule {
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
                gold = 1_000
                torches = 5
                food = 100
                timeLeft = 200
                restedHours = 14
            }

            flags {
                // Fresh replay starts with no quest progress.
            }

            milestones {
                // No special win-state milestones in this authored fixture yet.
            }

            inventory {
                slot(1).empty()
            }

            members {
                member("Aragorn") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Fighter
                    level = 1
                    health = 68
                    hitPoints = 68 of 68
                    weaponSkill = 64
                    wisdom = 32
                    mana = 0 of 32
                    charisma = 46
                    equipped {
                        weapon = Items.none
                        armor = Items.none
                    }
                    experience = 0
                    alive()
                }

                member("Legolas") {
                    gender = Gender.Female
                    race = Race.Elf
                    klass = PcClass.Ranger
                    level = 1
                    health = 62
                    hitPoints = 62 of 62
                    weaponSkill = 58
                    wisdom = 48
                    mana = 0 of 48
                    charisma = 56
                    equipped {
                        weapon = Items.none
                        armor = Items.none
                    }
                    experience = 0
                    alive()
                }

                member("Gandalf") {
                    gender = Gender.Male
                    race = Race.Elf
                    klass = PcClass.Mage
                    level = 1
                    health = 44
                    hitPoints = 44 of 44
                    weaponSkill = 31
                    wisdom = 67
                    mana = 67 of 67
                    charisma = 52
                    equipped {
                        weapon = Items.none
                        armor = Items.none
                    }
                    experience = 0
                    alive()
                }

                member("Mira") {
                    gender = Gender.Female
                    race = Race.Human
                    klass = PcClass.Priest
                    level = 1
                    health = 54
                    hitPoints = 54 of 54
                    weaponSkill = 39
                    wisdom = 64
                    mana = 64 of 64
                    charisma = 61
                    equipped {
                        weapon = Items.none
                        armor = Items.none
                    }
                    experience = 0
                    alive()
                }
            }
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
            player = "Strong"
            name = "Strong"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 10_000
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

    // Focused Thord fixture for runtime service coverage. It starts
    // directly on the town tile so replay seeds are reserved for town systems
    // such as smith haggling, tavern offers, rumors, and chitchat.
    stateFixture(stateId("town-services-replay")) {
        realm {
            day = 14
            hour = 9
            rngState = 1L
            activeParty = Parties.playtest
            wilderness(Maps.wilderness)
        }

        npcs {
            Npcs.garth at Towns.thord.inn
            stageRandomNpcsAwayFromTownServices()
        }

        party(Parties.playtest) {
            player = "playtest"
            name = "Playtest"
            position = Wilderness.xy(12, 11)
            facing = Facing.North

            resources {
                gold = 10_000
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

    // Deterministic town-dialogue fixture for the runtime
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
            stageRandomNpcsAwayFromTownServices(
                Npcs.nrimTo,
                Npcs.areth,
                Npcs.nevaak,
                Npcs.gorm,
                Npcs.bernak,
                Npcs.tybalt,
                Npcs.harry,
                Npcs.marraga,
                Npcs.broth,
                Npcs.gotharp,
                Npcs.ron,
                Npcs.leander,
                Npcs.ithienn,
                Npcs.azaaza,
            )
        }

        party(Parties.playtest) {
            player = "playtest"
            name = "Playtest"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 100_000
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
            stageRandomNpcsAwayFromTownServices(Npcs.gotharp, Npcs.azaaza)
        }

        party(Parties.playtest) {
            player = "playtest"
            name = "Playtest"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 100_000
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

    // Random-step mission fixture for the runtime replay.
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
                gold = 100_000
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

    // Long deterministic Master Crown chain fixture for the runtime
    // runtime replay planner. It keeps the story clean but gives the
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
            stageRandomNpcsAwayFromTownServices(
                Npcs.bernak,
                Npcs.tybalt,
                Npcs.harry,
                Npcs.broth,
                Npcs.ron,
                Npcs.ithienn,
            )
        }

        party(Parties.playtest) {
            player = "playtest"
            name = "Playtest"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 100_000
                torches = 80
                food = 500
                timeLeft = 1_000
                restedHours = 1_000
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
                    health = 50_000
                    hitPoints = 50_000 of 50_000
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

    // Runtime acceptance fixture. It starts at the same
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
            stageRandomNpcsAwayFromTownServices(Npcs.bernak, Npcs.azaaza)
        }

        party(Parties.playtest) {
            player = "playtest"
            name = "Playtest"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 50_000
                torches = 120
                food = 500
                timeLeft = 1_200
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
                member("Mira") {
                    gender = Gender.Female
                    race = Race.Human
                    klass = PcClass.Priest
                    level = 28
                    health = 5_000_000
                    hitPoints = 5_000_000 of 5_000_000
                    weaponSkill = 100
                    wisdom = 5_000
                    mana = 5_000 of 5_000
                    charisma = 90
                    equipped {
                        weapon = Items.deathBlade
                        armor = Items.admtMail
                    }
                    experience = 0
                    alive()
                }

                member("Shade") {
                    gender = Gender.Female
                    race = Race.Halfling
                    klass = PcClass.Thief
                    level = 28
                    health = 50_000
                    hitPoints = 0 of 50_000
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

                member("Gandalf") {
                    gender = Gender.Male
                    race = Race.Elf
                    klass = PcClass.Mage
                    level = 28
                    health = 50_000
                    hitPoints = 0 of 50_000
                    weaponSkill = 100
                    wisdom = 5_000
                    mana = 0 of 5_000
                    charisma = 90
                    equipped {
                        weapon = Items.deathBlade
                        armor = Items.admtMail
                    }
                    experience = 0
                    dead()
                }

                member("Aragorn") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Fighter
                    level = 28
                    health = 50_000
                    hitPoints = 0 of 50_000
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
            journal {
                line("You find the cargo")
                line("the merchant wanted")
                line("you to bring him.")
            }
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
            stageRandomNpcsAwayFromTownServices(Npcs.bernak, Npcs.azaaza)
        }

        party(Parties.replay) {
            player = "Replay"
            name = "Replay"
            position = Wilderness.xy(12, 9)
            facing = Facing.North

            resources {
                gold = 1_000
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
                member("Aragorn") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Fighter
                    level = 28
                    health = 50_000
                    hitPoints = 50_000 of 50_000
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

                member("Legolas") {
                    gender = Gender.Female
                    race = Race.Elf
                    klass = PcClass.Ranger
                    level = 28
                    health = 50_000
                    hitPoints = 0 of 50_000
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

                member("Gandalf") {
                    gender = Gender.Male
                    race = Race.Elf
                    klass = PcClass.Mage
                    level = 28
                    health = 50_000
                    hitPoints = 0 of 50_000
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

                member("Mira") {
                    gender = Gender.Female
                    race = Race.Human
                    klass = PcClass.Priest
                    level = 28
                    health = 50_000
                    hitPoints = 0 of 50_000
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
            journal {
                line("You find the cargo")
                line("the merchant wanted")
                line("you to bring him.")
            }
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
            partyMemberMax = 4
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
                gold = 1_000
                torches = 5
                food = 100
                timeLeft = 0
                restedHours = 6
            }

            counters {
                memberCount = 1
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
                member("Aragorn") {
                    gender = Gender.Male
                    race = Race.Human
                    klass = PcClass.Fighter
                    level = 28
                    health = 50_000
                    hitPoints = 50_000 of 50_000
                    weaponSkill = 100
                    wisdom = 90
                    mana = 0 of 90
                    charisma = 90
                    equipped {
                        weapon = Items.deathBlade
                        armor = Items.admtMail
                    }
                    experience = 0
                    alive()
                }

                member("Legolas") {
                    gender = Gender.Female
                    race = Race.Elf
                    klass = PcClass.Ranger
                    level = 28
                    health = 50_000
                    hitPoints = 50_000 of 50_000
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

                member("Gandalf") {
                    gender = Gender.Male
                    race = Race.Elf
                    klass = PcClass.Mage
                    level = 28
                    health = 50_000
                    hitPoints = 0 of 50_000
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

                member("Mira") {
                    gender = Gender.Female
                    race = Race.Human
                    klass = PcClass.Priest
                    level = 28
                    health = 50_000
                    hitPoints = 0 of 50_000
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

            npcVisits {
                visited(Npcs.garth)
                visited(Npcs.bernak)
                notVisited(Npcs.azaaza)
            }

            journal {
                line("You find the cargo")
                line("the merchant wanted")
                line("you to bring him.")
                line("Castle Ydarg has fallen.")
            }
        }
    }
}
