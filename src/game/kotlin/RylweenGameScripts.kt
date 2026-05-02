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

val ItemPoolRefs.thunAltarGems: ItemPoolId
    get() = CurrentGameItemPools.thunAltarGems

private object CurrentGameItemPools {
    val thunAltarGems = itemPool(
        "thun_altar_gems",
        items = listOf(
            Items.redGem,
            Items.blueGem,
            Items.greenGem,
        ),
    )
}

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
