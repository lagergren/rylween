@file:Suppress("SpellCheckingInspection", "ktlint:standard:no-wildcard-imports")

// RylweenTestScenarios.kt -- optional runtime replay fixtures.
//
// Designer note: this file contains deterministic replay seeds used by
// build tooling for smoke, save/load, and replay coverage. It is woven
// into the authored game via RylweenModel.withReplayScenarios(), which
// is called at the end of RylweenGame.kt.

package rylween.game

import rylween.dsl.*

private val replayRandomNpcIds: List<NpcId> =
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
        Npcs.groth,
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

private fun NpcStateBuilder.stageRandomNpcsAwayFromTownServices(vararg keepPlaced: NpcId) {
    val keep = keepPlaced.toSet()
    replayRandomNpcIds
        .filterNot(keep::contains)
        .forEachIndexed { index, npc ->
            npc at Wilderness.xy(8 + index, 8)
        }
}

// Pins the bandits quest giver and the Silver Key vendor and smith to their inns.
private fun NpcStateBuilder.placeQuestContactsInInns() {
    Npcs.garth at Towns.thord.inn
    Npcs.bernak at Towns.wilor.inn
    Npcs.azaaza at Towns.ollk.inn
}

// Where every quest location stands. Every state places all of them, the new-game
// state included, because missions only fire on a placed location and nothing places
// one later. Each sits on its own reachable square, clear of towns, the castle, fixed
// mission squares, and wilderness characters.
private val questLocations: Map<LocationId, GridPoint> =
    mapOf(
        Locations.silverKeyPit to xy(45, 13),
        Locations.hobgoblinCave to xy(46, 13),
        Locations.beastCave to xy(11, 23),
        Locations.hermitDungeon to xy(58, 8),
        Locations.thunTemple to xy(41, 30),
        Locations.koboldVillage to xy(53, 10),
        Locations.borinCamp to xy(47, 28),
        Locations.nolrimCrypt to xy(8, 27),
        Locations.masterCrownDungeon to xy(23, 32),
    )

// Story progress of a party holding all five artifacts needed to challenge
// Baron Ydarg, one milestone per artifact.
private val allArtifactsStoryFlags: List<FlagState> =
    listOf(
        Story.garth.bandits.recovered,
        Story.silverKey.keyTaken,
        Story.bracelets.thun.braceletsTaken,
        Story.masterCrown.crownTaken,
        Story.jazzy.ogre.jazzyDefeated,
        Story.hermit.trollTooth.fireShieldAcquired,
    )

private val allArtifactsMilestones: List<GameMilestoneId> =
    listOf(
        Milestones.fireShield,
        Milestones.braceletsOfThun,
        Milestones.masterCrown,
        Milestones.footOfJazzy,
        Milestones.silverKey,
    )

private data class ReplayMemberFixture(
    val name: String,
    val gender: Gender,
    val race: Race,
    val klass: PcClass,
    val level: Int,
    val health: Int,
    val hitPoints: HitPoints,
    val weaponSkill: Int,
    val wisdom: Int,
    val mana: Mana,
    val charisma: Int,
    val weapon: ItemId,
    val armor: ItemId,
    val experience: Int = 0,
    val dead: Boolean = false,
    val dexterity: Int = 50,
)

private fun replayMember(
    name: String,
    gender: Gender,
    race: Race,
    klass: PcClass,
    level: Int,
    health: Int,
    hitPoints: HitPoints,
    weaponSkill: Int,
    wisdom: Int,
    mana: Mana,
    charisma: Int,
    weapon: ItemId = Items.none,
    armor: ItemId = Items.none,
    experience: Int = 0,
    dead: Boolean = false,
    dexterity: Int = 50,
): ReplayMemberFixture =
    ReplayMemberFixture(
        name = name,
        gender = gender,
        race = race,
        klass = klass,
        level = level,
        health = health,
        hitPoints = hitPoints,
        weaponSkill = weaponSkill,
        wisdom = wisdom,
        mana = mana,
        charisma = charisma,
        weapon = weapon,
        armor = armor,
        experience = experience,
        dead = dead,
        dexterity = dexterity,
    )

private fun overpoweredMember(
    name: String,
    gender: Gender,
    race: Race,
    klass: PcClass,
    health: Int = 50_000,
    hitPoints: HitPoints = health of health,
    wisdom: Int = 90,
    mana: Mana = 0 of wisdom,
    charisma: Int = 90,
    dead: Boolean = false,
    dexterity: Int = 90,
): ReplayMemberFixture =
    replayMember(
        name = name,
        gender = gender,
        race = race,
        klass = klass,
        level = 28,
        health = health,
        hitPoints = hitPoints,
        weaponSkill = 100,
        wisdom = wisdom,
        mana = mana,
        charisma = charisma,
        weapon = Items.deathBlade,
        armor = Items.admtMail,
        dead = dead,
        dexterity = dexterity,
    )

private fun PartyMembersBuilder.member(fixture: ReplayMemberFixture): Unit =
    member(fixture.name) {
        gender = fixture.gender
        race = fixture.race
        klass = fixture.klass
        level = fixture.level
        health = fixture.health
        hitPoints = fixture.hitPoints
        weaponSkill = fixture.weaponSkill
        wisdom = fixture.wisdom
        mana = fixture.mana
        charisma = fixture.charisma
        dexterity = fixture.dexterity
        equipped {
            weapon = fixture.weapon
            armor = fixture.armor
        }
        experience = fixture.experience
        if (fixture.dead) dead() else alive()
    }

private fun PartyMembersBuilder.members(fixtures: List<ReplayMemberFixture>) {
    fixtures.forEach(::member)
}

private val freshReplayMembers: List<ReplayMemberFixture> =
    listOf(
        replayMember("Aragorn", Gender.Male, Race.Human, PcClass.Fighter, 1, 68, 68 of 68, 64, 32, 0 of 32, 46),
        replayMember("Legolas", Gender.Female, Race.Elf, PcClass.Ranger, 1, 62, 62 of 62, 58, 48, 0 of 48, 56),
        replayMember("Gandalf", Gender.Male, Race.Elf, PcClass.Mage, 1, 44, 44 of 44, 31, 67, 67 of 67, 52),
        replayMember("Mira", Gender.Female, Race.Human, PcClass.Priest, 1, 54, 54 of 54, 39, 64, 64 of 64, 61),
    )

private val strongReplayMembers: List<ReplayMemberFixture> =
    listOf(
        replayMember(
            "Aragorn",
            Gender.Male,
            Race.Human,
            PcClass.Fighter,
            1,
            88,
            88 of 88,
            95,
            72,
            0 of 72,
            78,
            Items.magicSword,
            Items.mithrilMail,
            dexterity = 82,
        ),
        replayMember(
            "Legolas",
            Gender.Female,
            Race.Elf,
            PcClass.Ranger,
            1,
            84,
            84 of 84,
            88,
            74,
            0 of 74,
            86,
            Items.crossbow,
            Items.elvenCloak,
            dexterity = 92,
        ),
        replayMember(
            "Gandalf",
            Gender.Male,
            Race.Elf,
            PcClass.Mage,
            1,
            76,
            76 of 76,
            72,
            92,
            92 of 92,
            74,
            Items.magicDagger,
            Items.leather,
            dexterity = 76,
        ),
        replayMember(
            "Mira",
            Gender.Female,
            Race.Human,
            PcClass.Priest,
            1,
            82,
            82 of 82,
            74,
            90,
            90 of 90,
            80,
            Items.magicAxe,
            Items.chainMail,
            dexterity = 72,
        ),
    )

private val strongEndgameReplayMembers: List<ReplayMemberFixture> =
    listOf(
        overpoweredMember("Aragorn", Gender.Male, Race.Human, PcClass.Fighter, wisdom = 500, mana = 0 of 500),
        overpoweredMember("Legolas", Gender.Female, Race.Elf, PcClass.Ranger, wisdom = 500, mana = 0 of 500),
        overpoweredMember("Gandalf", Gender.Male, Race.Elf, PcClass.Mage, wisdom = 5_000, mana = 5_000 of 5_000),
        overpoweredMember("Mira", Gender.Female, Race.Human, PcClass.Priest, wisdom = 5_000, mana = 5_000 of 5_000),
    )

private val townServiceReplayMembers: List<ReplayMemberFixture> =
    listOf(
        strongReplayMembers[0].copy(hitPoints = 40 of 88),
        strongReplayMembers[1].copy(hitPoints = 0 of 84, dead = true),
        strongReplayMembers[2].copy(mana = 10 of 92),
        strongReplayMembers[3],
    )

fun RylweenModel.withReplayScenarios(): RylweenModel =
    withStateModule {
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
                questLocations.forEach { (location, point) -> location at point }
            }

            npcs { placeQuestContactsInInns() }

            party(Parties.replay) {
                player = "Replay"
                name = "Replay"
                position = Wilderness.xy(12, 9)
                facing = Facing.South

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

                members { members(freshReplayMembers) }
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
                questLocations.forEach { (location, point) -> location at point }
            }

            npcs { placeQuestContactsInInns() }

            party(Parties.playtest) {
                player = "Strong"
                name = "Strong"
                position = Wilderness.xy(12, 9)
                facing = Facing.South

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

                members { members(strongReplayMembers) }
            }
        }

        // Endgame playtest fixture for manually exercising Castle Ydarg. The
        // party starts two steps north of the castle with every required power
        // milestone and visible special artifact, but without the final victory
        // milestone.
        stateFixture(States.strongEndgameReplay) {
            realm {
                day = 14
                hour = 22
                rngState = 1L
                activeParty = Parties.playtest
                wilderness(Maps.wilderness)
            }

            locations {
                questLocations.forEach { (location, point) -> location at point }
            }

            npcs {
                placeQuestContactsInInns()
                stageRandomNpcsAwayFromTownServices(Npcs.garth, Npcs.bernak, Npcs.azaaza)
            }

            party(Parties.playtest) {
                player = "StrongEndgame"
                name = "Strong Endgame"
                position = Wilderness.xy(14, 28)
                facing = Facing.South

                resources {
                    gold = 100_000
                    torches = 120
                    food = 500
                    timeLeft = 1_200
                    restedHours = 500
                }

                flags { allArtifactsStoryFlags.forEach(::mark) }

                milestones { allArtifactsMilestones.forEach(::achieved) }

                inventory {
                    slot(1).item(Items.fireWand)
                    slot(2).item(Items.staffOfHealing)
                    slot(3).item(Items.gemOfResurrection)
                    slot(4).item(Items.redPotion)
                    slot(5).item(Items.bluePotion)
                    slot(6).item(Items.torch)
                    slot(7).item(Items.ration)
                }

                members { members(strongEndgameReplayMembers) }

                visited(Npcs.garth, Npcs.bernak)
                journal {
                    line("All five artifacts")
                    line("needed to challenge")
                    line("Baron Ydarg are held.")
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

            locations {
                questLocations.forEach { (location, point) -> location at point }
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

                members { members(townServiceReplayMembers) }
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
                questLocations.forEach { (location, point) -> location at point }
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
                Npcs.groth at Towns.wilor.inn
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
                    Npcs.groth,
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
                    member(
                        replayMember(
                            "Fighter",
                            Gender.Male,
                            Race.Human,
                            PcClass.Fighter,
                            8,
                            188,
                            188 of 188,
                            95,
                            72,
                            0 of 72,
                            78,
                            Items.magicSword,
                            Items.mithrilMail,
                        ),
                    )
                    member(
                        replayMember(
                            "Ranger",
                            Gender.Female,
                            Race.Elf,
                            PcClass.Ranger,
                            8,
                            184,
                            184 of 184,
                            88,
                            74,
                            0 of 74,
                            86,
                            Items.crossbow,
                            Items.elvenCloak,
                        ),
                    )
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

            locations {
                questLocations.forEach { (location, point) -> location at point }
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
                    member(
                        replayMember(
                            "Fighter",
                            Gender.Male,
                            Race.Human,
                            PcClass.Fighter,
                            8,
                            188,
                            188 of 188,
                            95,
                            72,
                            0 of 72,
                            78,
                            Items.magicSword,
                            Items.mithrilMail,
                        ),
                    )
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
                questLocations.forEach { (location, point) -> location at point }
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
                    member(overpoweredMember("Fighter", Gender.Male, Race.Human, PcClass.Fighter, wisdom = 72, mana = 0 of 72))
                }
            }
        }

        // Long deterministic Master Crown chain fixture for the runtime replay
        // planner. It keeps the story clean but gives the
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
                questLocations.forEach { (location, point) -> location at point }
            }

            npcs {
                Npcs.bernak at Towns.wilor.inn
                Npcs.tybalt at Towns.wilor.inn
                Npcs.harry at Towns.wilor.inn
                Npcs.groth at Towns.wilor.inn
                Npcs.ron at Towns.wilor.inn
                Npcs.ithienn at Towns.wilor.inn
                stageRandomNpcsAwayFromTownServices(
                    Npcs.bernak,
                    Npcs.tybalt,
                    Npcs.harry,
                    Npcs.groth,
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
                    member(overpoweredMember("Fighter", Gender.Male, Race.Human, PcClass.Fighter, wisdom = 72, mana = 0 of 72))
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
                questLocations.forEach { (location, point) -> location at point }
            }

            npcs {
                placeQuestContactsInInns()
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
                    member(
                        overpoweredMember(
                            "Mira",
                            Gender.Female,
                            Race.Human,
                            PcClass.Priest,
                            health = 5_000_000,
                            wisdom = 5_000,
                            mana =
                                5_000 of 5_000,
                        ),
                    )
                    member(overpoweredMember("Shade", Gender.Female, Race.Halfling, PcClass.Thief, hitPoints = 0 of 50_000, dead = true))
                    member(
                        overpoweredMember(
                            "Gandalf",
                            Gender.Male,
                            Race.Elf,
                            PcClass.Mage,
                            hitPoints = 0 of 50_000,
                            wisdom = 5_000,
                            mana =
                                0 of 5_000,
                            dead = true,
                        ),
                    )
                    member(overpoweredMember("Aragorn", Gender.Male, Race.Human, PcClass.Fighter, hitPoints = 0 of 50_000, dead = true))
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
                questLocations.forEach { (location, point) -> location at point }
            }

            npcs {
                placeQuestContactsInInns()
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

                flags { allArtifactsStoryFlags.forEach(::mark) }

                milestones { allArtifactsMilestones.forEach(::achieved) }

                inventory {
                    slot(1).empty()
                    slot(2).item(Items.fireWand)
                }

                members {
                    member(
                        overpoweredMember("Aragorn", Gender.Male, Race.Human, PcClass.Fighter, wisdom = 70, mana = 0 of 70, charisma = 49),
                    )
                    member(overpoweredMember("Legolas", Gender.Female, Race.Elf, PcClass.Ranger))
                    member(overpoweredMember("Gandalf", Gender.Male, Race.Elf, PcClass.Mage))
                    // One dead member keeps alive-vs-dead handling in play through the endgame battles.
                    member(overpoweredMember("Mira", Gender.Female, Race.Human, PcClass.Priest, hitPoints = 0 of 50_000, dead = true))
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
                questLocations.forEach { (location, point) -> location at point }
            }

            npcs { placeQuestContactsInInns() }

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

                flags { allArtifactsStoryFlags.forEach(::mark) }

                milestones {
                    allArtifactsMilestones.forEach(::achieved)
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
                    member(overpoweredMember("Aragorn", Gender.Male, Race.Human, PcClass.Fighter))
                    member(overpoweredMember("Legolas", Gender.Female, Race.Elf, PcClass.Ranger, dead = true))
                    member(overpoweredMember("Gandalf", Gender.Male, Race.Elf, PcClass.Mage, hitPoints = 0 of 50_000, dead = true))
                    member(overpoweredMember("Mira", Gender.Female, Race.Human, PcClass.Priest, hitPoints = 0 of 50_000, dead = true))
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
