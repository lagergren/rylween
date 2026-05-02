// RylweenGameScripts.kt -- reusable script helpers for this Rylween game.
//
// This layer is game-specific authoring support. It can contain reusable
// scripted behavior for the current game world, but it must not become DSL
// kernel machinery or engine runtime code.

@file:Suppress(
    "unused",
)

val MissionRefs.thunTemple: MissionId
    get() = missionId("missions.thun_temple")

val MissionSceneRefs.thunTemple: SceneId
    get() = sceneId("missions.thun_temple")

fun ScriptScope.dungeonEntrance(
    dungeon: DungeonId,
    levels: Int,
    leaveClearsText: Boolean = true,
    prompt: StructuredTextBlockBuilder.() -> Unit,
) {
    native.screen.clearText()
    choice.enterOrLeave(
        structuredText(prompt),
        enter = {
            enterDungeon(dungeon, levels)
            native.screen.clearMapWindow()
            native.screen.redrawMap()
        },
        leave = {
            if (leaveClearsText) {
                native.screen.clearText()
            }
        },
    )
}

fun DungeonScenarioBuilder.standardDungeonEvents() {
    randomEvents {
        event(DungeonEvents.gold)
        event(DungeonEvents.weakEncounter, weight = 2)
        event(DungeonEvents.deepEncounter)
        event(DungeonEvents.nothingSpecial)
        event(DungeonEvents.emptyLevel)
        event(DungeonEvents.torches)
    }
}

fun ScriptScope.readScreen(vararg lines: String) {
    screen(*lines)
    waitInput()
    native.screen.clearText()
}

fun ScriptScope.readScreen(body: StructuredTextBlockBuilder.() -> Unit) {
    screen(body)
    waitInput()
    native.screen.clearText()
}

fun ScriptScope.notEnoughSpellPoints() {
    screen("Not enough spell pts!")
    pause(1000)
    native.screen.clearText()
}

fun ScriptScope.withPriestSpellPoints(caster: MemberRef, baseCost: Int, body: ScriptScope.() -> Unit) {
    val cost = lit(baseCost) + caster.level
    whenever(caster.mp gte cost) {
        caster.mp -= cost
        body()
    } otherwise {
        notEnoughSpellPoints()
    }
}

fun ScriptScope.priestTeleport(caster: MemberRef) {
    withPriestSpellPoints(caster, baseCost = 35) {
        native.screen.clearText()
        screen {
            line(partyName(caster), " carefully")
            lines("prepares the ritual of")
            line("Teleportation. ", pronoun(caster, PronounCase.Subject), " spends")
            lines("an hour chanting mantras", "and burning blue candles.", "")
        }
        realm.hour += 1
        waitInput("after teleport ritual")
        native.screen.clearText()
        screen {
            line(pronoun(caster, PronounCase.Subject), " picks up a stone slab")
            lines(
                "from his satchel and",
                "uses a special instrument",
                "to scratch the direction",
            )
            line(pronoun(caster, PronounCase.Subject), " wants you to teleport")
            lines("to.", "")
        }
        waitInput("after teleport slab")
        native.screen.clearText()
        val direction = ask.wildernessDirection(
            textLines(
                "Teleport :",
                "",
                "   8",
                " 4-+-6",
                "   2",
                "",
            ),
        )
        native.screen.clearText()
        val distance = ask.digit(textLines("How many miles do you", "want to travel? (1-9)"), 1..9)
        teleportParty(direction, distance)
        screen("You appear to be", "somewhere else.")
        native.screen.redrawMap()
        pause(1200)
        native.screen.clearText()
    }
}

fun ScriptScope.travellerStory() {
    native.screen.clearText()
    val maxMembers = realm.partyMemberMax
    val storyteller = choice.member(
        structuredText {
            line("Who wants to tell")
            line("a story? (1-", maxMembers, ")")
        },
        filter = MemberChoiceFilter.AliveOnly,
        accepting = MemberChoiceFilter.Any,
    )

    whenever(storyteller.dead) {
        native.screen.clearText()
        screen {
            lines(
                "Dead men do not",
                "tell any great",
                "stories...",
            )
        }
        pause(2000)
    } otherwise {
        native.screen.clearText()
        screen {
            line(partyName(storyteller), " stands up")
            line("and screams:")
            line("- ANYONE WHO WANNA HEAR")
            line("  A TRAVELLER'S STORY?")
            line("")
        }
        pause(2000)

        screen("- YEAAH! Roars the crowd!", "")
        pause(2000)

        val isBoast = localInt(0)
        menu("(T)rue story", "(B)oast") {
            option('T') {}
            option('B') {
                isBoast set 1
            }
        }

        val score = localInt(0)
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
                native.screen.clearText()
                screen("- Out with the liar!")
                pause(2000)
                screen("You are thrown out.")
                whenever(storyteller.charisma gte 10) {
                    storyteller.charisma -= 10
                } otherwise {
                    storyteller.charisma set 0
                }
                native.screen.clearMapWindow()
                breakLoop("liar thrown out of tavern")
            }
        }

        native.screen.clearText()
        whenever(score gt 400) {
            screen {
                lines(
                    "Superb Story! Let's",
                    "give 'em some ale, lads!",
                    "You are given a whole",
                    "tray of ale!",
                    "",
                )
            }
            waitInput("after superb tavern story")
            storyteller.charisma += 3
        }
        whenever((score gt 300) and (score lt 401)) {
            screen {
                lines(
                    "Very good! You've been",
                    "through much!",
                    "",
                )
            }
            waitInput("after good tavern story")
            storyteller.charisma += 1
        }
        whenever((score gt 150) and (score lt 301)) {
            screen("Nice story, Sir!", "")
            waitInput("after nice tavern story")
        }
        whenever((score gt 100) and (score lt 151)) {
            screen("The mob is bored.", "")
            waitInput("after boring tavern story")
            whenever(storyteller.charisma gt 0) {
                storyteller.charisma -= 1
            }
        }
        whenever(score lt 101) {
            screen {
                lines(
                    "Most of the crowd",
                    "falls asleep. The rest",
                    "beg you to stop.",
                    "",
                )
            }
            waitInput("after terrible tavern story")
            whenever(storyteller.charisma gte 3) {
                storyteller.charisma -= 3
            }
        }
    }
}
