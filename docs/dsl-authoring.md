# Rylween DSL Authoring Guide

This guide describes the game-authoring surface used by Adventurers of Rylween.
It is for changing the game world, not for changing a terminal, browser, or
server runtime.

The current authoring source is regular Kotlin:

- `game/src/main/kotlin/rylween/game/RylweenGame.kt` contains the game content,
  scripts, runtime-system text, fixtures, and image declarations.
- `game/src/main/kotlin/rylween/game/RylweenAudio.kt` declares the sound
  effects, music, and ambient loops, and is pulled into the game with
  `include(...)`.
- `game/src/main/kotlin/rylween/game/RylweenTestScenarios.kt` declares the
  new-game start state and the replay fixtures.
- `core/src/main/kotlin/rylween/dsl` contains the reusable DSL, validation, and
  source-script IR.
- `core/src/main/kotlin/rylween/compiled` lowers the authored model into the
  compiled bundle consumed by runtimes.
- `runtimes/runtime/src/main/kotlin` consumes compiled bundle rows. It should not import
  authored source-script IR or generated game handle catalogs.

Run this after changing authoring or DSL code:

```sh
./gradlew ktlintFormat --console=plain
./gradlew check --console=plain
```

## Mental Model

The DSL has three layers:

1. `content { ... }` declares the static world: items, monsters, terrain,
   maps, towns, services, images, text, scripts, missions, dungeons, castles,
   combat rules, and reusable runtime-system prompts.
2. `stateFixture(...) { ... }` declares a bootable mutable state. The new-game
   start and the replay fixtures live in `RylweenTestScenarios.kt`. Runtime
   saves serialize the same kind of state, but with every persisted field
   written explicitly.
3. Script bodies such as `scene { ... }`, `dialogue { ... }`, `spell { ... }`,
   and `itemUse { ... }` record source-script statements. They do not execute
   when Kotlin evaluates the file. Validation and bundle compilation lower them
   into compiled VM instructions.

The runtime contract is the compiled bundle, not the Kotlin DSL objects. This
is what lets ANSI, bitmap, server, or future native runtimes share the same game
content without importing the game source.

## Current Hierarchy

The top-level opening tags are nested like this in the current game source, in
the order `RylweenGame.kt` uses them:

```text
rylween
  content
    include(section)                 // RylweenAudio.kt's audio { ... }
    metadata
    limits
    flags
      story / encounter
    secrets
      admin / victim
    visuals
      images / staticScreens
    layouts
    terminal
    terrain
    worldMap
      layer
    realm
      upkeep / hunt / camp
    castle
      treasure / pitfall / stairs / scripts / text
    prompts
      titleMenu / priest / inventoryUse / equipment / save / characterCreation
      town / party / wilderness / dungeon
    pacing
      battle / characterCreation / shop / speak / wilderness
    items
      pool
    gameProgress
      milestone / winningConfiguration
    leveling
      classLevelUp
    characterCreation
      start / playerClass
    formulas
    rewards
    combat
      standardBattle
    wilderness
      keys / events / terrainTransitions
    dungeons
      keys / event / randomEvents / scenario
    speak
      standardConversation / textTables / questionAction
    towns
    townSystem
      image / entryTax / menu / buildingServices
    characters
      randomPlacementPolicy / character
    monsters
    missions
      mission
    scene                            // towns, castle, and missions alike
    spell
    itemUse
    dialogue
      npc
        whenState / default

withStateModule                      // RylweenTestScenarios.kt
  stateFixture / runtimeState
    realm
    locations
    npcs
    party
      resources / counters / flags / milestones / inventory / members / journal
```

Script bodies appear under `scene`, `dialogue` branches, spell and item-use
effects, dungeon events, and castle hooks. Those bodies can use text/view
actions, local control flow, prompts, state mutations, and child-session
handoffs such as `encounter(...)`, `enterDungeon(...)`, `speak(...)`, or
`shopService(...)`.

## Syntax Rules

The DSL is hosted by Kotlin, but game authoring should read like structured game
notes:

- Use generated handles such as `Items.fireWand`, `Scenes.Missions.bandits`,
  `Towns.thord`, and `Terrain.forest` instead of raw strings.
- Put game prose in `writeText`, `appendText`, `showTextAndWait`, or
  runtime-system prompt rows. Text comes in two kinds: `flowText(...)` is prose
  the renderer wraps to its own width (a plain string is flow text; in its
  strings a newline is a space, and a blank line or `\n\n` starts a new
  paragraph), and `literalText(...)` is lines kept exactly as written. Use
  literal lines only when each row is an intentional menu, layout, sign or verse
  row; `literalText { line("You find ", amount) }` mixes in runtime values the
  same way `flowText("You find ", amount, " gold.\n\nThe chest is empty.")` does.
- Use block helpers such as `localInt { ... }`, `ask.member { ... }`,
  `ask.monsterAlive { ... }`, and `withCurrentCasterCost { ... }` instead of
  raw Kotlin temporaries in script bodies.
- Use DSL control flow such as `whenever(cond) { } otherwise { }`, `choice`,
  `choose(expr) { case(n) { }; default { } }`, `loop`, `breakLoop`,
  `forEachMember`, and `forEachMonster` inside scripts.
- Keep normal runtime mode loops out of scripts. Wilderness, dungeon, castle,
  shop, and battle loops are host systems that consume authored tables and
  compiled script hooks.

Preferred script text vocabulary:

```kotlin
scene(Scenes.Missions.bandits) {
    showBackdrop(Images.Backdrop.encounterBanner1)
    showTextAndWait("Bandits rush from the trees.")

    choice.yesNo(
        "Stand and fight?",
        "(Y)es",
        "(N)o",
        yes = {
            encounter(Monsters.bandit)
            mark(Story.garth.bandits.recovered)
        },
        no = {
            showTextAndWait("You slip away into the wilderness.")
        },
    )
}
```

`writeText(...)` starts a fresh page in the text window, so it needs no
`clearText()` before it, and `appendText(...)` adds below what the page already
shows; appended flow text gets a blank line above it, appended literal lines
continue directly below. `showTextAndWait(...)` is the read-and-dismiss page
verb: it writes text, waits for Return/Enter, and clears the text window.

Text must stay on screen until something holds it: a `waitInput()`, a pause,
or a menu whose prompt only lists its choices (such a menu keeps the page above
them). The content check rejects a script that writes a fresh page, clears the
window, shows a prompt with its own text, starts a battle or other handoff, or
ends while text it showed is still unheld, because the player would never get
to read it. Split long speeches into pages at sentence or paragraph breaks,
each held by a key press.

A beat that belongs on the same page goes in the text as a `{pause}` marker:
`{pause}` is a default beat of 1.5 seconds, and `{pause 600ms}` or
`{pause 2s}` set the length. `writeText` and `showTextAndWait` show the page up
to the marker, pause, and then show it again with the rest, so the text grows
instead of being replaced:

```kotlin
writeText(
    """
    Poisonous gas hisses out through hidden holes. {pause}

    The party coughs and retches. {pause 600ms}

    Finally. The gas clears.
    """,
)
writeText(flowText(partyName(caster), " utters three syllables in an archaic tongue... {pause 2s} and the ground cracks open!"))
```

A marker at the very end of the text holds the page that long, so a short
notice is one call: `writeText("No money - No drink! {pause 1s}")`. That is
also the only place `appendText` takes one. Any other text that holds a marker,
such as a menu prompt or a journal entry, is rejected. `pause(ms)` is left for a
beat between two other things, such as a sound cue and the line after it.

A line or paragraph that starts with `{red}` is shown in red, in the terminal
and in the browser alike; `{green}`, `{yellow}`, `{blue}`, `{magenta}` and
`{cyan}` work the same way. The color covers the whole line, so the marker can
only start one: `line("{red}", partyName(target), " DIES!")`. Text never holds
terminal escape codes itself; the content check rejects them.

Literal lines are rows, not hand-wrapped prose: put each sentence on one line
and let the renderer wrap it to the window, and break the line only where a new
sentence, speech or option row starts. Historical script aliases such as
`say(...)`, text-output `screen(...)`, and `readScreen(...)` have been removed
from the current authoring API; reusable/static screen declarations still use
their own `screen(...)` builders under `visuals { staticScreens { ... } }` and
`speak { textTables { ... } }`.

## Prompt And Input Taxonomy

Prompt helpers share the same compiled continuation plumbing, but they should
not be flattened into one generic prompt primitive. Each shape has a different
input contract, validation rule, UI affordance, and resume payload.

| Shape | Use It For | Resume Payload |
| --- | --- | --- |
| `menu(...)` | Local one-key script branches with authored menu rows. Use it when the script owns the labels and branch commands. | The chosen keyed branch, with an optional default branch for invalid input. |
| `choice.yesNo(...)` | Yes/no confirmations that should use the standard Y/N bindings. | The yes or no branch selected by the shared yes/no key model. |
| `choice.enterOrLeave(...)` | Short enter/leave prompts such as cave and service entry decisions. | The enter or leave branch. |
| `choice.takeOrLeave(...)` | Short take/leave prompts for authored offers. | The take or leave branch. |
| `choice.payNoThanks(...)` | Payment prompts that also need the standard broke/paid handling. | The paid branch after a successful debit, the broke branch on insufficient gold, or no action for no-thanks. |
| `choice.randomItemOffer(...)` | Random authored item offers with shared offer text and purchase behavior. | The generated offer result and its buy/leave handling. |
| `choice.member(...)` / `ask.member(...)` | Party member selection. Use `MemberChoiceFilter` for semantic intent such as living, dead, or spellcaster members, and `accepting` when old-game behavior needs a different accepted set. | A `MemberRef` backed by the selected one-based party member number. |
| `choice.inventorySlot(...)` / `ask.inventorySlot(...)` | Inventory slot selection for item, equipment, drop, and offer flows. | An `InventorySlotRef` backed by the chosen inventory slot. |
| `ask.monsterAlive(...)` | Battle target selection from live encounter slots. Do not use it for party or non-battle selection. | A `MonsterRef` tied to the selected living monster slot. |
| `ask.digit(...)` | Small numeric prompts where each accepted value is a digit key. | An integer expression for the chosen digit. |
| `ask.wildernessDirection(...)` | Direction prompts that should follow the wilderness movement key model. | An integer expression for the chosen direction command. |
| `choice.textInput(...)` | Free-form string entry such as passwords, riddles, or future name-like prompts. Use `acceptedText(...)`, trimming, case, and max-length policy instead of key-menu branches. | The accepted or rejected branch after comparing the submitted string. |

Menu rows are written `(K) Label`, as in `"(A) Ale         2 au"`, and the
runtime keys its own lists the same way (`(A) Wild Dog`, `(1) Aragorn`). Do not
add a lettered `(N)one` or `(N)othing` row to back out: end the menu with a
`"RETURN leaves."` line instead. A script `menu(...)` that shows that line and
has no `default { }` branch is left when the player presses RETURN.

`writeText(...)` and `showTextAndWait(...)` only display text; they are not
input prompts by themselves. Runtime and domain prompts for shops, battles,
dungeons, towns, command help, and global menus should live in their
`runtimeSystems` rows or owning domain runners rather than in a generic script
prompt shape.

## Script Loops And Runtime Loops

`loop { ... }` is authored script control flow. It lowers to
`CompiledOpcode.Loop` and is appropriate for local scripted menus or repeated
story/service branches that run inside a scene, dialogue branch, spell effect,
item use, dungeon event, or castle hook. Leave it with `breakLoop(...)` when the
authored branch is finished.

Runtime mode loops are different. Wilderness/title, town, dungeon, castle,
shop, and battle loops are host-owned state machines. They own live input
cadence, rendering, save/load boundaries, RNG advancement, command help,
targeting, catalog paging, room movement, and handoff to compiled script roots.
They must consume authored text, menu bindings, formulas, rewards, balance
constants, and policies from compiled content or `runtimeSystems` rows. If a
new runtime loop needs player-facing text, menu policy, rewards, or balance
data, add an explicit authored model/export row first instead of hardcoding
those facts in runtime code.

Use this split when deciding where a repeated flow belongs:

- If it is a local branch in one authored scene or service script, use
  `loop { ... }` and `breakLoop(...)`.
- If it is a reusable gameplay mode with persistence, redraws, command help,
  input cadence, random encounters, shops, battles, dungeons, castles, or town
  services, keep the loop in runtime code and feed it from compiled rows.
- If the loop belongs in runtime code but needs new game facts, model those
  facts in the DSL before the runtime consumes them.

## Adding Content

Add content in the matching `content { ... }` section of `RylweenGame.kt`.
Generated handle catalogs are updated by Gradle from the authored source.

### Item

```kotlin
items {
    magicItem(Items.moonCharm, "Moon Charm", cost = 1_200)
}

itemUse(Items.moonCharm) {
    inventory()
    consume = ItemConsumePolicy.NeverConsumes

    effect {
        showTextAndWait("The charm glows softly.")
        gainFood(10)
    }
}
```

An item record supplies inventory/shop/save metadata. An `itemUse` script
supplies behavior when the player activates it. Every item use works in combat;
`inventory()` also makes it usable from the inventory. `consume` is `BeforeUse` by default, which uses the
item up before the effect runs; `ScriptControlled` leaves it to the effect to
call `consumeActiveItem()`, and `NeverConsumes` keeps it.

### Rewards And Resources

Use `reward { ... }` for player-facing gold and inventory grants:

```kotlin
reward {
    gold(2_000)
    items(Items.redGem)
    promptDrop(
        cantCarry = "You can't carry the gem.",
        pickedUp = "You pick up the gem.",
    )
}
```

A reward of several items can name each one it has no room for with `itemName`:
`promptDrop(cantCarry = flowText("You can't carry the ", itemName, "."))`.

For simple player-facing resource gains, use the narrow helpers:

```kotlin
gainGold(random(40..339))
gainTorches(2)
gainFood(10)
```

Members and monsters take damage with `wound(amount)`, which also kills them at
zero hit points; `kill()` and `revive()` do the rest. The runtime keeps the
party's count of living members, so `party.memberCount` is only read:

```kotlin
target.wound(damage)
whenever(target.dead) {
    appendText { line("{red}", partyName(target), " DIES! {pause 1s}") }
}
```

Use direct state slots for local mechanics such as costs, damage, timers, and
script counters:

```kotlin
pay(
    amount = lit(10_000),
    broke = { showTextAndWait("Come back when you have the gold.") },
    ok = { mark(Story.bracelets.thun.mapBought) },
)
```

Secret debug/sysop grants live under `secrets { ... }`, in an `admin { ... }`
block for the sysop and a `victim { ... }` block for an account the sysop gods
punish, with methods such as `addGold(...)` and `grantItems(...)`. They are not
normal game rewards. The game never names the accounts: each server sets them
with `RYLWEEN_ADMIN_ACCOUNT` (default `admin`) and `RYLWEEN_VICTIM_ACCOUNT`
(default nobody).

### Monster

```kotlin
monsters {
    monster(Monsters.cryptWight) {
        name = "Crypt Wight"
        count = 1..3
        hitPoints = 90..150
        weaponSkill = 65
        tier = EncounterTiers.deep
        encounterImage(Images.Encounter.undead)
    }
}
```

A monster can also set `spellPower`, `immune`, and `countMaxLevelDivisor`.
Monsters become available to missions, dungeon events, random event pools, and
battle encounter hooks through their typed handles.

### Scene And Mission

The mission chains the game has today are listed in
[Appendix: Mission Chains](#appendix-mission-chains).

```kotlin
scene(Scenes.Missions.cryptDoor) {
    showBackdrop(Images.Backdrop.cave)
    showTextAndWait("A cold door blocks the path.")

    whenever(achieved(Milestones.silverKey)) {
        showTextAndWait("The Silver Key turns by itself.")
        enterDungeon(Dungeons.reebarTomb, levels = 20)
    } otherwise {
        showTextAndWait("The lock does not yield.")
    }
}

missions {
    mission(Missions.cryptDoor) {
        at(xy(32, 15))
        requires(Story.garth.bandits.closed)
        runs(Scenes.Missions.cryptDoor)
    }
}
```

The mission owns when a scene starts. The scene owns what the player sees and
what game state changes. A mission triggers `at(...)` a square or a
`Locations.*` placement, on `randomStep(percent = ...)` in the wilderness, or
`onTerrain(...)`, and `requires(...)` a story flag state or a condition before
it runs.

Text that tells the player where a mission waits names it rather than copying
its square: `flowText("The orcs have a cave at ", mission(Missions.husbandCave).coordinates, ".")`
shows the mission's `at(...)` square, or its location's square wherever this
game placed it, so the text follows the mission when it moves.

### Dialogue Branch

```kotlin
dialogue {
    npc(Npcs.garth) {
        whenState(Story.garth.bandits.none) {
            writeText("- Bandits trouble the road.")
            mark(Story.garth.bandits.accepted)
            journal("Garth asked us to defeat the bandits.")
            waitInput("after Garth's request")
        }

        default {
            showTextAndWait("- Keep your blades sharp.")
        }
    }
}
```

Dialogue branches should use typed flags and text bindings rather than string
placeholders. The standard conversation runner chooses the branch; the branch
only authors the response and state mutation.

### Shop

```kotlin
scene(Scenes.Towne.smith) {
    shopService(Buildings.smith) {
        image = Images.Scene.smith
        intro("- What do you want?", "")
        menu("(B)uy", "(S)ell", "(L)eave")

        buy {
            prompt("Armor or Weapon?", "", "(A)rmor", "(W)eapon", "RETURN leaves.")
            catalog('A', "Armor") {
                item('1', Items.chainMail, label = "Chain Mail")
            }
            catalog('W', "Weapons") {
                item('1', Items.sword, label = "Sword")
                item('2', Items.longsword, label = "Longsword")
            }
        }

        sell {
            prompt("Sell what?", "")
            acceptsKinds(ItemKind.Weapon, ItemKind.Armor)
            price = ShopPrices.formula(Formulas.sellPrice)
            offer { line("Ok! Give ya ", price, "!") }
        }
    }
}
```

`shopService` is a script verb, so a shop lives in its building's scene. A
catalog too long for one screen splits into `page { ... }` blocks. Shop
catalogs, sell policy, haggle text, paging, and purchase keys are authored.
The runtime service loop owns live input, inventory mutation, and paging state.
A price is the item's cost (`ShopPrices.itemCost`, the default for buying), a
fixed amount (`ShopPrices.fixed(n)`), or a formula
(`ShopPrices.formula(Formulas.rationPrice)`) that sees the item's `itemCost` and
the leader's `firstLevel`. A sell section names its price.

### Runtime-System Prompt

Runtime-system rows are for reusable host behavior that still needs authored
wording or labels.

```kotlin
prompts {
    inventoryUse {
        invalidSlot {
            line("Inventory slot ", InventoryText.slotName)
            line("does not exist.")
        }
    }
}
```

Use this pattern when a runtime loop owns the input cadence but the text is
player-facing game prose. Values only the runtime knows come in through
placeholder objects: `InventoryText` (`slotName`, `itemName`), `PartyText`
(`memberCount`, `memberMax`), `CampText` (`guardName`), `CastleText`
(`thiefName`), `HuntText`, `UpkeepText`, `TownText`, and `BattleText` (with
`roundNumber` for the page each round opens with). Each message is authored
whole, the runtime only filling in its values: the castle's
`trapTriesDisarm = literalText { line(CastleText.thiefName, " tries to disarm it...") }`,
not a fragment the runtime joins to a name. Where the runtime lists choices it
knows, such as party members or the stairs items the castle guardian takes, the
prompt holds only the question and the runtime adds the choices and the RETURN
line.

### Formulas

A game rule that works something out, rather than naming a fixed number, is a
formula, so the game author can change it without touching a runtime:

```kotlin
formulas {
    formula(Formulas.spellCost) {
        value = spellBaseCost + level
    }
    formula(Formulas.monsterDamage) {
        value = (monsterHitPoints / 6 + random(0..9) - armorClass) atLeast 1
    }
}

realm { spellCost = Formulas.spellCost }
```

The system that uses a formula binds its variables: the member it is about
(`weaponSkill`, `weaponDamage`, `hitPoints`, `armorClass`, `level`, `charisma`,
`dexterity`), the monster (`monsterWeaponSkill`, `monsterHitPoints`,
`monsterSpellPower`), the encounter, the party (`firstLevel`, `livingMembers`,
`gold`), a town's `taxPercent`, a shop item's `itemCost`, or a spell's
`spellBaseCost`. `atLeast` and `atMost` clamp a value, `whenValue(condition, a, b)`
picks one, and `random(lo..hi)` draws from the game's own random numbers. A
chance is a percentage the runtime rolls 0-99 against.

This game's rules that are formulas: what a spell costs (`realm { spellCost }`),
a party member's and a monster's chance to hit and damage, a monster's chance to
cast, the hit points a defending member regains, how encounters grow with the
leader's level (`encounterFoeCountMaxFormula`, `encounterWeaponSkillFormula`,
and the hit point range), battle experience and gold, level-up hit points, the
food the party eats (`upkeep { foodCost }`), the town entry tax
(`entryTax { amount }`; a party with less gold than the tax is shown the
`entryTaxShortfall` prompt, with `TownText.taxShortfall`, and stays outside),
shop prices, the hunting and trap-disarm chances, and the haggle bonus. Inside a
priest spell, `withCurrentCasterCost { caster, cost -> }`
hands over the cost the formula works out; the spell's `baseCost` is declared
once, in its `spell(...)` header.

### Map Or Terrain

```kotlin
terrain {
    terrain(Terrain.ashWastes, mapCode = 'a')
    terrain(Terrain.lavaField, mapCode = 'l') { blocksMovement() }
}

worldMap(Maps.wilderness) {
    layer(MapLayers.terrain) {
        rows(
            "ssssssss",
            "ssaaasss",
            "ssaa0sss",
        )
    }
}
```

A terrain is passable unless it calls `blocksMovement()`, and
`settlement(TerrainSettlement.Town)` (or `Hut`, `Castle`) marks squares the
party can enter. Terrain has no movement cost to author. Map dimensions and cell counts are derived from the rows. Do not
duplicate them with separate width, height, capacity, or cell-count
declarations.

### Content In Its Own File

Any part of `content { ... }` can live in its own source file under
`game/src/main/kotlin/rylween/game` as a `gameSection`, and be pulled in with
`include(...)`. A section uses the same blocks as `content`, and applies exactly
where it is included. Blocks that add up, such as `visuals` and `audio`, can be
continued from several sections.

```kotlin
// RylweenAudio.kt
val rylweenAudio: GameSection =
    gameSection {
        audio { ... }
    }

// RylweenGame.kt
content {
    include(rylweenAudio)
    ...
}
```

The handle generator reads every file in that directory, so handles such as
`Sounds.Combat.miss1` used only in a section are generated too.

### Sound And Music

Sounds are one-shot effects; music tracks are seamless loops, which covers
background music and ambient loops alike. Files live under
`game/src/main/resources/audio` and may be MP3, Ogg, WAV, or M4A; the game ships
MP3 because every browser can decode it. `support/audio` synthesizes the
shipped files: run `uv run --project support/audio support/audio/render_all.py`
from the repository root.

```kotlin
audio {
    sounds("audio/sfx/combat") {
        sound(Sounds.Combat.miss1, "miss-1.mp3")
        sound(Sounds.Combat.hitSlash1, "hit-slash-1.mp3")
    }
    music("audio/music") {
        track(Music.wildernessDay, "wilderness-day.mp3")
        track(Music.wildernessNight, "wilderness-night.mp3")
    }

    // Engine events; several sounds play in rotation.
    cue(AudioCue.PlayerMiss, Sounds.Combat.miss1, Sounds.Combat.miss2)

    // Per-item, per-monster, per-spell and per-terrain sounds replace the
    // generic cue for that event.
    item(Items.sword) {
        hit(Sounds.Combat.hitSlash1)
        ready(Sounds.Item.readyBlade)
    }
    monster(Monsters.worg) { attack(Sounds.Combat.monsterBite) }
    spell(Spells.Mage.frost, Sounds.Spell.frost)
    itemUse(Items.redPotion, Sounds.Spell.heal)
    step(Terrain.forest, Sounds.Explore.stepForest)

    // Music by place; a track with hours wins during those hours.
    area(MusicArea.Wilderness, Music.wildernessDay)
    area(MusicArea.Wilderness, Music.wildernessNight, fromHour = 18, toHour = 5)
    scene(Scenes.Towne.tavern, Music.tavernCrowd)
    dungeon(Dungeons.thunTemple, Music.temple)
}
```

Scripts can play sounds and change the music themselves:

```kotlin
playSound(Sounds.Explore.chestOpen)
playMusic(Music.ending)
```

Validation rejects undeclared sound or music ids, unknown items, monsters,
spells, terrain, scenes, dungeons and castles, duplicate declarations, and hours
outside 0..23. Runtimes that cannot play audio ignore it; the bitmap web runtime
plays it in the browser, with a Sound switch and a volume slider in Preferences.

### Dungeon Event

```kotlin
dungeons {
    event(DungeonEvents.cryptWarning) {
        script {
            showTextAndWait("A whisper passes through the stone.")
        }
    }

    event(DungeonEvents.weakEncounter) {
        recurringUntilWon()
        randomEncounter(EncounterTiers.weak)
    }

    event(DungeonEvents.goldChest) {
        recurring()
        script {
            menu("You find a chest!", "", "(O)pen chest", "(L)eave") {
                option('O') {
                    realm.levelCleared set true
                    gainGold(random(320..419))
                }
                option('L') {}
            }
        }
    }

    // The mix every scenario draws its other levels from, unless it names its own.
    randomEvents(
        DungeonEvents.weakEncounter to 2,
        DungeonEvents.goldChest to 1,
    )

    scenario(Dungeons.reebarTomb) {
        name("Reebar Tomb")
        levels(20)
        level(2, DungeonEvents.cryptWarning)
        level(13..19, DungeonEvents.colorRiddle)
    }
}
```

A scenario's own `randomEvents(...)` replaces the dungeons' mix for that
dungeon, and `level(3, 6, 9, event = ...)` puts one event on several levels.

An event's disposition decides whether it is still on its level when the party
comes back:

- By default an event runs once and is gone, like gold or torches found on a
  level.
- `recurring()` runs it on every visit, like a message on the wall.
- `recurringUntil(flag)` runs it on every visit until the story flag is set.
- `recurringUntilWon()` runs it on every visit until the party wins the battle
  it starts. Beaten monsters stay beaten, and monsters the party fled from are
  still there.

A recurring event's script can take itself off its level with
`realm.levelCleared set true`, as the chest does once it is opened; a chest left
closed is still there next time. `realm.banished set true`, which Banish Evil
uses, clears the random encounters from every level but the last.

Dungeon event planning is authored; the dungeon runtime owns movement, torch
checks, save/load boundaries, random event selection, and utility commands.

## State And Fixtures

States live in `game/src/main/kotlin/rylween/game/RylweenTestScenarios.kt`,
inside `withStateModule { ... }`. Use `stateFixture(...)` for the new-game
start (`fresh-replay`), named starts, and replay setup; the `States.*` handles
are generated from `stateFixture` and `runtimeState` declarations. Keep runtime
saves exact and expanded; helper shortcuts are fine for authored test fixtures
but not for save output.

```kotlin
stateFixture(States.strongReplay) {
    realm {
        activeParty = Parties.playtest
        wilderness(Maps.wilderness)
    }
    locations {
        questLocations.forEach { (location, point) -> location at point }
    }
    npcs { placeQuestContactsInInns() }
    party(Parties.playtest) {
        position = Wilderness.xy(12, 9)
        resources {
            gold = 10_000
            food = 200
            torches = 12
        }
        members {
            member("Aragorn") {
                klass = PcClass.Ranger
                race = Race.Human
                level = 8
            }
        }
    }
}
```

Every fixture places the quest locations from the shared `questLocations` table,
so they stand on the same squares in every game.

## Where A Change Belongs

Use this placement rule during design review:

- `RylweenGame.kt`: current-game facts, prose, scenes, item effects, dialogue,
  shop rows, mission triggers, maps, assets, and runtime-system wording.
- `RylweenAudio.kt`: sound, music, and ambient declarations.
- `RylweenTestScenarios.kt`: the new-game start state, replay fixtures, and
  quest location placements.
- `core/src/main/kotlin/rylween/dsl`: reusable authoring vocabulary, source
  script IR, typed models, and validation.
- `core/src/main/kotlin/rylween/compiled`: compiled-bundle lowering and the
  bundle readers.
- `runtimes/runtime/src/main/kotlin`: host behavior over compiled rows: input
  loops, rendering, RNG cadence, persistence, save/load, and live subsystem
  state.
- `runtimes/runtime-bitmap-web` or `runtimes/runtime-web`: frontend
  presentation, browser transport, preferences, and canvas/ANSI rendering.
- `support/tools`: artifact emission, readers, and non-playable probes.

A change to the shape of the `runtimeSystems` rows also bumps
`EXTERNAL_RUNTIME_SYSTEMS_VERSION` in
`core/src/main/kotlin/rylween/compiled/ExternalBundleArtifact.kt` (14 today), so
a runtime refuses a bundle written for another shape.

If a rule mentions Rylween story content, item names, NPCs, mission prose,
prices, spell costs, battle phrases, or map facts, it usually belongs in the
game DSL or a reusable runtime-system row. If it is a generic host concern such
as reading a key, rendering a page, persisting a save file, or stepping a
battle/shop loop, it belongs in runtime code that consumes compiled tables.

## Extending The DSL

When adding a new authoring construct:

1. Add the immutable model in the narrowest `core/src/main/kotlin/rylween/dsl`
   or `dsl/impl` file.
2. Add a builder receiver annotated with `@RylNextDsl`.
3. Add validation that reports game concepts rather than Kotlin internals.
4. Export it through the compiled bundle or `runtimeSystems` only when a
   runtime/tool consumes it.
5. Add at least one game-content use or focused unit test.
6. Add ledger coverage if the construct protects an architectural boundary.
7. Update this guide when the construct changes authoring practice.

Do not add speculative public builders. If no runtime, compiler, validator, or
game content consumes a field, track it as a plan item instead of leaving dead
surface area in the DSL.

## I10 Design Audit Notes

The I10 audit found the current high-level separation sound:

- Current production runtimes consume compiled bundle rows and stable
  `runtimeSystems`, not authored source-script IR.
- Current game script bodies use DSL block helpers for common temporaries
  instead of nested raw Kotlin `val` declarations.
- Script-control loops and runtime service loops now have separate documented
  ownership.
- Current-game text display uses `writeText` and `showTextAndWait`; the legacy
  aliases have since been removed.
- Current-facing comments and plan/spec text were corrected to point at
  `RylweenGame.kt` and the source-script IR instead of the retired
  `RylweenGame.dsl.kts`/authored `Op` terminology.

## Appendix: Mission Chains

This appendix describes the current authored mission chains in `RylweenGame.kt`.
Coordinates use the game map's `x,y` convention. Location handles such as
`Locations.borinCamp` are runtime state placements. Every state, including the
new-game state `fresh-replay`, places all of them from the `questLocations`
table in `RylweenTestScenarios.kt`, at the fixed squares listed here, and
authored prose renders their coordinates through
`location(...).coordinates`. Characters placed in the wilderness, such as Marcus
the hermit, are met by pressing Enter on their square, which opens the hut scene.

### Power-Item Path

The final Castle Ydarg victory path requires five power milestones:
`fireShield`, `masterCrown`, `footOfJazzy`, `braceletsOfThun`, and `silverKey`.
Castle Ydarg entry is also gated by the Silver Key, and completion marks
`ydargVictory` for Hall of Heroes reporting.

| Chain | Start | Mission trigger | Main flags | Rewards and unlocks |
|-------|-------|-----------------|------------|---------------------|
| Garth's cargo | Garth dialogue | Random wilderness step, 9 percent, after `garth.bandits.accepted` | `garth.bandits`: `none -> accepted -> recovered -> closed` | Bandit battle recovers cargo. Returning to Garth grants 2,000 gold and a red gem. |
| Fire Shield | Marcus the hermit dialogue | Random wilderness step, 9 percent, after `hermit.trollTooth.accepted`; then `Locations.hermitDungeon` | `hermit.trollTooth`: `none -> accepted -> extracted -> delivered -> fireShieldAcquired` | Troll battle yields a tooth. Marcus reveals the Fire Shield dungeon. The dungeon grants the Fire Shield milestone. Every game places `hermitDungeon` at `58,8`. |
| Bracelets of Thun | Harry sells a map | `Locations.thunTemple`, after `bracelets.thun.mapBought` | `bracelets.thun`: `none -> mapBought -> braceletsTaken` | Costs 10,000 gold for the map. Temple entry requires five altar gems from the authored altar-gem item pool. The dungeon grants the Bracelets of Thun milestone. Every game places `thunTemple` at `41,30`. |
| Master Crown | Groth points to Ithienn, then Ithienn's three tasks | `Locations.koboldVillage`, swamp terrain, `Locations.borinCamp`, `Locations.nolrimCrypt`, and `Locations.masterCrownDungeon` | `nymph.seeker`: `none -> told -> wizard3tActive`; `masterCrown`: `none -> task1Assigned -> task1Done -> task2Assigned -> task2Done -> task3Assigned -> borinMet -> daggerTaken -> borinPaid -> crownTold -> crownTaken` | Kobold village proves task 1. Skullweed on any swamp terrain proves task 2. Borin sends the party to Nolrim's crypt for Reebar, then grants 3,578 gold and red/blue/green gems. Ithienn reveals the Master Crown dungeon, which grants the Master Crown milestone. Every game places `koboldVillage` at `53,10`, `borinCamp` at `47,28`, `nolrimCrypt` at `8,27`, and `masterCrownDungeon` at `23,32`. |
| Foot of Jazzy | Leander trades information for an item worth at least 35 gold | `Locations.beastCave`, then any foothills terrain after the ogres are cleared | `jazzy.ogre`: `none -> accepted -> ogresCleared -> jazzyDefeated` | Ogre cave grants 5,645 gold and Mithril Mail, then a note points to the eastern peaks. The foothills event battles Jazzy's minions and Jazzy, then grants the Foot of Jazzy milestone. Every game places `beastCave` at `11,23`. |
| Silver Key | Bernak sells a map, then Gotharp and Azaaza reveal the theft trail | `Locations.silverKeyPit`, random wilderness step 9 percent for hobgoblin caravan, then `Locations.hobgoblinCave` | `silverKey`: `none -> mapBought -> noteFound -> thiefSqueezed -> smithConfessed -> caveLocated -> keyTaken` | Bernak's map costs 50,000 gold. The Silver Sanctum produces the clue note. Gotharp names the smith, Azaaza reveals the hobgoblin caravan, the random caravan reveals the cave, and the cave path grants the Silver Key milestone. Every game places `silverKeyPit` at `45,13` and `hobgoblinCave` at `46,13`. |

### Other Mission Chains

| Chain | Start | Trigger or target | Main flags | Rewards and unlocks |
|-------|-------|-------------------|------------|---------------------|
| Ranger letter | Nrim'to dialogue | Deliver to Marraga through normal NPC dialogue | `ranger.letter`: `none -> accepted -> delivered` | Marraga grants a Magic Sword. The recipient location is resolved through NPC role placement, so it may be authored/random rather than a fixed coordinate. |
| Areth's book | Areth, Gorm, Nevaak, then Areth again | Normal NPC dialogue branches | `fighter.priestBook`: `none -> priestTold -> priestVisited -> wizardRobbed -> fighterGrateful` | Nevaak gives up the book; returning to Areth grants the Dancing Sword. |
| Erethor's Lute | Tybalt dialogue | Fixed coordinate `6,20`; then return to Tybalt and Kaal | `erethor.lute`: `none -> rumorTold -> luteTaken -> noteReceived -> cloakReceived` | Erethor temple dungeon grants the Lute. Tybalt gives a note, and Kaal grants the Cloak of Gods. |
| Peasant's wife | Ron dialogue | Fixed coordinate `42,23`; then return to Ron | `peasant.wife`: `none -> accepted -> rescued -> paid` | The cave/dungeon rescue marks Tammy rescued. Ron grants 3,000 gold, Adamantium Mail, Holy Hand Grenade, amber potion, fire wand, and crystal bowl. |

### Support Flags

`smith.beerOffer.offered` makes the smith's beer offer come first until the
party has heard it once. `church.donationOnce` and the encounter-scoped
`encounter.immune` are declared, but no script uses them yet.
