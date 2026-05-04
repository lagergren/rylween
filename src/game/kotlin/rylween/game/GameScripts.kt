// GameScripts.kt -- reusable script helpers for this Rylween game.
//
// This layer is game-specific authoring support. It can contain reusable
// scripted behavior for the current game world, but it must not become DSL
// kernel machinery or engine runtime code.

@file:Suppress(
    "unused",
)

package rylween.game

import rylween.dsl.*

private fun TextLineSink.standardBattleTargetPromptLines() {
    line("Who?")
    line("")
}

private fun TextLineSink.standardBattleKilledLines() {
    line("...KILLED!")
}

private fun banditsCargoRecoveredLines(write: (String) -> Unit) {
    write("You find the cargo")
    write("the merchant wanted")
    write("you to bring him.")
    write("")
}

fun BattleTextBuilder.targetMonsterPrompt() {
    targetPrompt { standardBattleTargetPromptLines() }
}

fun BattleTextBuilder.monsterKilledLine() {
    killed { standardBattleKilledLines() }
}

fun ScriptScope.askAliveBattleMonster(): MonsterRef =
    ask.monsterAlive(structuredText { standardBattleTargetPromptLines() })

fun ScriptScope.showBattleKilled() {
    screen { standardBattleKilledLines() }
}

fun ScriptScope.readBanditsApproach() {
    readScreen(
        "You come across the",
        "camp of the bandits.",
        "One of them spots you",
        "and they attack!",
        "",
    )
}

fun ScriptScope.readBanditsCargoRecovered() {
    readScreen {
        banditsCargoRecoveredLines(::line)
    }
}

fun JournalBuilder.banditsCargoRecovered() {
    banditsCargoRecoveredLines { entry ->
        if (entry.isNotBlank()) {
            line(entry)
        }
    }
}

fun ScriptScope.dungeonEntrance(
    dungeon: DungeonId,
    levels: Int,
    leaveClearsText: Boolean = true,
    prompt: StructuredTextBlockBuilder.() -> Unit,
) {
    clearText()
    choice.enterOrLeave(
        structuredText(prompt),
        enter = {
            enterDungeon(dungeon, levels)
            clearMapWindow()
            renderMap()
        },
        leave = {
            if (leaveClearsText) {
                clearText()
            }
        },
    )
}

fun DungeonScenarioBuilder.standardDungeonEvents() {
    randomEvents {
        event(DungeonEvents.findObject)
        event(DungeonEvents.gold)
        event(DungeonEvents.weakEncounter, weight = 2)
        event(DungeonEvents.deepEncounter)
        event(DungeonEvents.nothingSpecial)
        event(DungeonEvents.emptyLevel)
        event(DungeonEvents.goldChest)
        event(DungeonEvents.torches)
    }
}

fun ScriptScope.offerDungeonFind(pool: ItemPoolId) {
    choice.randomItemOffer(pool) {
        free()
        choiceKeys(buy = inputKey("T"), decline = inputKey("L"))
        offer {
            line("You find a")
            line(itemName, ".")
            line("")
        }
        choicePrompt("(T)ake it", "(L)eave it")
        broke("No gold changes hands.")
        full("You can't carry more.")
        exactFullInventoryReject()
        deal("", "Ok.")
    }
}

fun ScriptScope.findDungeonObject() {
    val roll = localInt(random(0..4))
    whenever(roll gt 0) {
        choose(random(0..2)) {
            case(0) { offerDungeonFind(ItemPools.dungeonWeaponFinds) }
            case(1) { offerDungeonFind(ItemPools.dungeonPotionGemFinds) }
            case(2) { offerDungeonFind(ItemPools.dungeonTreasureFinds) }
        }
    } otherwise {
        offerDungeonFind(ItemPools.magicItems)
    }
}

fun ScriptScope.thunRiddle(
    intro: TextBlock,
    riddle: TextBlock,
    answers: List<String>,
    guardian: MonsterId,
) {
    readScreen(intro)
    screen(riddle)
    choice.textAnswer(
        prompt = "Answer: ",
        accepting = answers,
        maxLength = 15,
        match = {
            readScreen("- Well done. Pass.", "")
        },
        miss = {
            readScreen(
                "- FOOLS! Meet thy doom!",
                "at a gesture from him,",
                "something evil appears.",
                "",
            )
            encounter(guardian, mandatory = true)
        },
    )
    clearText()
}

fun ScriptScope.readScreen(vararg lines: String) {
    screen(*lines)
    waitInput()
    clearText()
}

fun ScriptScope.readScreen(text: TextBlock) {
    screen(text)
    waitInput()
    clearText()
}

fun ScriptScope.readScreen(body: StructuredTextBlockBuilder.() -> Unit) {
    screen(body)
    waitInput()
    clearText()
}

fun ScriptScope.notEnoughSpellPoints() {
    screen("Not enough spell pts!")
    pause(1000)
    clearText()
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
        clearText()
        screen {
            line(partyName(caster), " carefully")
            lines("prepares the ritual of")
            line("Teleportation. ", pronoun(caster, PronounCase.Subject), " spends")
            lines("an hour chanting mantras", "and burning blue candles.", "")
        }
        realm.hour += 1
        waitInput("after teleport ritual")
        clearText()
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
        clearText()
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
        clearText()
        val distance = ask.digit(textLines("How many miles do you", "want to travel? (1-9)"), 1..9)
        teleportParty(direction, distance)
        screen("You appear to be", "somewhere else.")
        renderMap()
        pause(1200)
        clearText()
    }
}

fun ScriptScope.travellerStory() {
    clearText()
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
        clearText()
        screen {
            lines(
                "Dead men do not",
                "tell any great",
                "stories...",
            )
        }
        pause(2000)
    } otherwise {
        clearText()
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
                clearText()
                screen("- Out with the liar!")
                pause(2000)
                screen("You are thrown out.")
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
