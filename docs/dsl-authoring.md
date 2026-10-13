# Rylween DSL Authoring Guide

This guide describes the game-authoring surface used by Adventurers of Rylween.
It is for changing the game world, not for changing a terminal, browser, or
server runtime.

The current authoring source is regular Kotlin:

- `game/src/main/kotlin/rylween/game/RylweenGame.kt` contains the game content,
  scripts, runtime-system text, fixtures, and asset declarations.
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
2. `state { ... }` declares a bootable mutable state. Start states and replay
   fixtures live here. Runtime saves serialize the same kind of state, but with
   every persisted field written explicitly.
3. Script bodies such as `scene { ... }`, `dialogue { ... }`, `spell { ... }`,
   and `itemUse { ... }` record source-script statements. They do not execute
   when Kotlin evaluates the file. Validation and bundle compilation lower them
   into compiled VM instructions.

The runtime contract is the compiled bundle, not the Kotlin DSL objects. This
is what lets ANSI, bitmap, server, or future native runtimes share the same game
content without importing the game source.

## Current Hierarchy

The top-level opening tags are nested like this in the current game source:

```text
rylween
  content
    metadata
    limits
    flags
      story / encounter
    visuals
      images
      staticScreens
    layouts
    terrain
    terminal
    worldMap
      layer
    realm
      upkeep / camp / hunt / timeOfDay
    items
    itemPools
    castles
      castle
        rooms / text / hooks
    runtimeSystems
      titleMenu / magic / inventoryUse / equipment / save / characterCreation
      town / partyViews / wilderness / dungeons / commandHelp
    gameProgress
    winningConfiguration
    leveling
    characterCreation
    rewards
    monsters
    combat
    dungeons
      event
        script
      scenario
    towns
    townSystem
    missions
      mission
    scenes
      scene
    dialogue
      npc
        whenState
  state / runtimeState / stateFixture
    realm
    locations
    npcs
    parties
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
- Put game prose in `writeText`, `showTextAndWait`, `literalText`,
  `structuredText`, `flowText`, or runtime-system prompt rows. Use
  `flowText("""...""")` for natural paragraphs that the renderer should wrap;
  use literal line lists only when each row is an intentional menu or layout row.
- Use block helpers such as `localInt { ... }`, `ask.member { ... }`,
  `ask.monsterAlive { ... }`, and `withCurrentCasterCost { ... }` instead of
  raw Kotlin temporaries in script bodies.
- Use DSL control flow such as `ifThen`, `choice`, `switch`, `loop`,
  `breakLoop`, `forEachMember`, and `forEachMonster` inside scripts.
- Keep normal runtime mode loops out of scripts. Wilderness, dungeon, castle,
  shop, and battle loops are host systems that consume authored tables and
  compiled script hooks.

Preferred script text vocabulary:

```kotlin
scene(Scenes.Missions.bandits) {
    showBackdrop(Images.Backdrop.encounterBestiary)
    writeText("Bandits rush from the trees.", "")

    choice.yesNo(literalText("Stand and fight?")) {
        yes {
            encounter(EncounterTiers.weak, Monsters.bandit)
            achieve(Milestones.banditsDefeated)
        }
        no {
            writeText("You slip away into the wilderness.", "")
        }
    }
}
```

`writeText(...)` is the only normal script verb for text output.
`showTextAndWait(...)` is the read-and-dismiss page verb: it writes text, waits
for Return/Enter, and clears the text window. Historical script aliases such as
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
    magicItem(Items.moonCharm, "Moon Charm", 1200)
}

itemUse(Items.moonCharm, consume = ItemConsumePolicy.Keep) {
    showTextAndWait("The charm glows softly.", "")
    gainFood(10)
}
```

An item record supplies inventory/shop/save metadata. An `itemUse` script
supplies behavior when the player activates it.

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

For simple player-facing resource gains, use the narrow helpers:

```kotlin
gainGold(random(40..339))
gainTorches(2)
gainFood(10)
```

Use direct state slots for local mechanics such as costs, damage, timers, and
script counters:

```kotlin
pay(
    amount = lit(10_000),
    broke = { showTextAndWait("Come back when you have the gold.", "") },
    ok = { mark(Story.bracelets.thun.mapBought) },
)
```

Secret debug/sysop grants live under `secrets { ... }` with methods such as
`addGold(...)`; they are not normal game rewards.

### Monster

```kotlin
monsters {
    monster(Monsters.cryptWight) {
        name = "Crypt Wight"
        count = 1..3
        hitPoints = 90..150
        weaponSkill = 65
        dexterity = 50
        tier = EncounterTiers.deep
    }
}
```

Monsters become available to missions, dungeon events, random event pools, and
battle encounter hooks through their typed handles.

### Scene And Mission

```kotlin
scene(Scenes.Missions.cryptDoor) {
    showBackdrop(Images.Backdrop.caveEntrance)
    showTextAndWait("A cold door blocks the path.", "")

    whenever(achieved(Milestones.silverKey)) {
        writeText("The Silver Key turns by itself.", "")
        enterDungeon(Dungeons.nolrimCrypt)
    } otherwise {
        writeText("The lock does not yield.", "")
    }
}

missions {
    mission(Missions.cryptDoor) {
        at(Wilderness.xy(32, 15))
        runs(Scenes.Missions.cryptDoor)
    }
}
```

The mission owns when a scene starts. The scene owns what the player sees and
what game state changes.

### Dialogue Branch

```kotlin
dialogue {
    npc(Npcs.garth) {
        whenState(Story.garth.bandits.none) {
            writeText("- Bandits trouble the road.", "")
            mark(Story.garth.bandits.accepted)
            journal("Garth asked us to defeat the bandits.")
        }

        default {
            writeText("- Keep your blades sharp.", "")
        }
    }
}
```

Dialogue branches should use typed flags and text bindings rather than string
placeholders. The standard conversation runner chooses the branch; the branch
only authors the response and state mutation.

### Shop

```kotlin
shopService(Buildings.smith) {
    image = Images.Scene.smith
    intro("- What do you want?", "")
    menu("(B)uy", "(S)ell", "(L)eave")

    buy {
        prompt("Buy what?")
        catalog {
            page {
                item(Items.longSword)
                item(Items.chainMail)
            }
        }
    }

    sell {
        prompt("Sell what?")
        acceptsKinds(ItemKind.Weapon, ItemKind.Armor)
    }
}
```

Shop catalogs, sell policy, haggle text, paging, and purchase keys are authored.
The runtime service loop owns live input, inventory mutation, and paging state.

### Runtime-System Prompt

Runtime-system rows are for reusable host behavior that still needs authored
wording or labels.

```kotlin
prompts {
    inventoryUse {
        invalidSlot {
            line("There is no item in ", InventoryText.slotName, ".")
        }
    }
}
```

Use this pattern when a runtime loop owns the input cadence but the text is
player-facing game prose.

### Map Or Terrain

```kotlin
terrain {
    terrain(Terrain.ashWastes, mapCode = 'a') {
        movementCost = 2
    }
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

Map dimensions and cell counts are derived from the rows. Do not duplicate them
with separate width, height, capacity, or cell-count declarations.

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

    scenario(Dungeons.nolrimCrypt) {
        levels(20)
        randomEvents(
            DungeonEvents.weakEncounter to 2,
            DungeonEvents.goldChest to 1,
        )
        level(2, DungeonEvents.cryptWarning)
    }
}
```

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

Use `state { ... }` for test fixtures, named starts, and replay setup. Keep
runtime saves exact and expanded; helper shortcuts are fine for authored test
fixtures but not for save output.

```kotlin
state(States.strong) {
    realm {
        activeParty = Parties.strong
        wilderness(map = Maps.wilderness)
    }
    party(Parties.strong) {
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

## Where A Change Belongs

Use this placement rule during design review:

- `RylweenGame.kt`: current-game facts, prose, scenes, item effects, dialogue,
  shop rows, mission triggers, maps, assets, and runtime-system wording.
- `core/src/main/kotlin/rylween/dsl`: reusable authoring vocabulary, source
  script IR, typed models, validation, and compiled-bundle lowering.
- `runtimes/runtime/src/main/kotlin`: host behavior over compiled rows: input loops,
  rendering, RNG cadence, persistence, save/load, and live subsystem state.
- `runtime-bitmap-web` or `runtime-web`: frontend presentation, browser
  transport, preferences, and canvas/ANSI rendering.
- `tools`: artifact emission, readers, and non-playable probes.

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
- Current-game text display uses `writeText` and `showTextAndWait`; legacy
  aliases remain compatibility surface, not preferred authoring vocabulary.
- Current-facing comments and plan/spec text were corrected to point at
  `RylweenGame.kt` and the source-script IR instead of the retired
  `RylweenGame.dsl.kts`/authored `Op` terminology.
