# Rylween Compiled Bundle Boundary Formats

This file documents the current external compiled-bundle boundary checks for
the Kotlin-authored game: the J0 VM smoke artifact in this directory and the
active J1 strict JSON artifact emitted under `build/compiled`.

The producer is `CompiledGameBundle.writeNativeSmokeArtifact(...)` in
`src/main/kotlin/rylween/compiled/NativeSmokeArtifact.kt`. The same file also
contains the Kotlin smoke reader used by tests, and the C reader is
`rylween_bundle_smoke.c`. Gradle wires the C check through:

```sh
./gradlew checkNativeSmokeBundle --console=plain
```

`check` depends on that task.

The active J1 artifact is strict deterministic JSON across compiled
content/state table groups, stable runtime-system data, and the Kotlin runtime
facade. The producer is
`RylweenModel.writeExternalBundleArtifact(...)` in
`src/main/kotlin/rylween/compiled/ExternalBundleArtifact.kt`. Gradle wires the
Kotlin JSON reader and inspection check through:

```sh
./gradlew checkExternalCompiledBundle --console=plain
./gradlew inspectExternalCompiledBundle --console=plain
./gradlew checkKotlinAnsiBundleBoot --console=plain
./gradlew checkNativeJsonBundle --console=plain
```

`check` depends on these JSON checks. `checkKotlinAnsiBundleBoot` emits the JSON
bundle, installs the ANSI launcher, and runs the title screen through
`RylweenAnsiMain --bundle <file>` after the launcher has built its runtime model
from that bundle. `checkNativeJsonBundle` compiles a small C reader that reuses
the legacy `json5.c` parser source with a compatibility shim, then validates the
same JSON artifact structurally.

## Current Runtime Status

The Kotlin ANSI runtime accepts `--bundle <file>`, materializes the J1 JSON into
the runtime-facing `RylweenModel`, validates that the embedded runtime facade
recompiles to the same typed `CompiledGameBundle` sections, verifies the
artifact hash/section summary, and then boots from the loaded model. Gameplay
still runs through the backend-neutral Kotlin session state machines and
authored `Op` interpreter. That closes the Kotlin artifact-backed boot path, but
it is not a compiled-VM execution path.

`CompiledGameBundle.loadCompiledGame()` is a Kotlin in-memory loader/validator:
it validates cross-table references and builds string, handle, and script lookup
maps from an already-materialized `CompiledGameBundle`.

`ByteArray.readNativeSmokeArtifact()` is the Kotlin binary parser for this
`.rsmoke` artifact. The C smoke loader parses the same emitted bytes. Their job
is to catch schema/API boundary outages: bad magic/version, bad enum ranges, bad
string ids, bad handle refs, malformed nested operands, and unsupported VM
operand shapes.

J1 is closed for the current artifact-backed runtime target. Kotlin emits,
reads, hashes, inspects, materializes, and boots from the strict JSON artifact.
Schema version 3 also contains `runtimeSystems`, a stable non-reflective section
for the first runtime-facing systems a non-Kotlin loader should consume
directly: realm balance, reusable prompts, terminal/layout/static presentation,
command bindings, wilderness events, town/speak runtime boundaries, and Ydarg as
a `castles[]` entry. The native C reader validates schema version, section
order, canonical `sections` SHA-256, core row counts, and the `runtimeSystems`
command/prompt/castle contract.

The Kotlin `runtimeFacade` remains intentionally Kotlin-shaped because it is the
complete model consumed by the Kotlin ANSI and headless hosts. Do not duplicate
that whole object graph into another "stable" section speculatively. Add a new
schema-versioned direct section only when a real non-Kotlin playable runtime
needs data that is not already represented by `runtimeSystems` or the typed
compiled table sections.

J2 is the separate task for executing compiled ops in a standalone VM. Its
tests should reuse the same kind of playthrough/scenario inputs as the Kotlin
scenario harness so native behavior is compared against the Kotlin runtime, not
only against structural loading. This J0 smoke format only proves that the
current typed operation surface can be parsed.

J3 is the host/view conformance task. The JSON bundle is not a whole executable
game: every playable runtime still needs input, rendering, resources, RNG/time,
persistence, session/state mutation, and either the Kotlin authored-op
interpreter or the J2 compiled-op VM. The current C code is not playable; it is
only a schema/API smoke reader.

## J1 JSON Artifact

The canonical external bundle is:

```json
{
  "format": "rylween.compiled.bundle",
  "schemaVersion": 3,
  "bundleContentHash": "sha256:...",
  "sectionOrder": ["universe", "limits", "..."],
  "sections": {
    "universe": {
      "name": "Rylween",
      "version": "kotlin-dsl",
      "compatibilityId": "rylween"
    }
  }
}
```

`bundleContentHash` is SHA-256 over the canonical compact JSON bytes for the
`sections` object. The reader validates the format, schema version, fixed
section order, section object order, and content hash. `inspectExternalCompiledBundle`
writes `build/compiled/rylween-bundle.inspect.txt`, a readable summary with
section row counts, byte counts, and FNV-1a checksums.

The section names are:

```text
universe
limits
strings
handles
flags
items
itemPools
formulas
rewards
terrain
worldMaps
images
staticScreens
screenRegions
towns
buildingServices
characters
monsters
missions
spells
itemUses
dungeonEvents
dungeonScenarios
scripts
runtimeSystems
runtimeFacade
states
```

## Stable Runtime Systems Section

`runtimeSystems` is the first stable non-Kotlin system section. It deliberately
does not use Kotlin reflective `_type` markers; entries use explicit `kind`,
`op`, `id`, `slug`, and enum-string fields instead. Its top-level shape is:

```json
{
  "schemaVersion": 1,
  "realm": {},
  "prompts": {},
  "presentation": {},
  "commands": {},
  "wilderness": {},
  "dungeon": {},
  "town": {},
  "speak": {},
  "castles": [
    { "id": "ydarg", "kind": "gridCastle" }
  ]
}
```

`readExternalRuntimeSystemsSummary()` validates this section on the Kotlin side
and rejects reflective markers. `src/native/bundle-json/rylween_bundle_json.c`
validates the same section on the C side before any future gameplay execution.

The remaining sections document the J0 binary smoke artifact.

## Scalar Encoding

All integers are little-endian 32-bit values.

- `u32`: unsigned 32-bit little-endian
- `i32`: signed 32-bit little-endian, encoded with the same four bytes as `u32`
- `bool`: `u32`, where `0` is false and `1` is true
- `list<T>`: `u32 count`, followed by `count` encoded entries
- `nullable<T>`: `bool present`, followed by the value when present
- `stringId`: `u32` id into the string table, one-based
- `inlineString`: `u32 byteLength`, followed by UTF-8 bytes
- `handleRef`: `u32 handleKindOrdinal`, `u32 id`
- `tagged<T>`: `u32 tag`, followed by tag-specific payload

String id `0` is invalid in places that require a real string reference.
Schema tags are named in both readers/writers, not repeated as call-site magic
numbers: see the `NativeSmoke*Tag` enums in `NativeSmokeArtifact.kt` and the
matching `*Tag` enums in `rylween_bundle_smoke.c`.

## File Layout

The current artifact layout is:

```text
magic                  8 bytes, ASCII "RYLSMOKE"
version                u32, currently 1
handleKindCount        u32, must match CompiledHandleKind.entries.size
opcodeCount            u32, must match CompiledOpcode.entries.size
runtimeTextPartCount   u32, must match CompiledRuntimeTextPart.entries.size
strings                string table
handles                handle table
scripts                compiled script roots
stateCount             u32
```

The `stateCount` field is a placeholder boundary check. J0 does not serialize
state images. Full state-image serialization belongs to J1.

## String Table

```text
strings:
    count              u32
    entries[count]:
        byteLength     u32
        bytes          UTF-8
```

Runtime ids are one-based by table order: first string is id `1`.

## Handle Table

Handles are written sorted by `denseId`.

```text
handles:
    count              u32
    entries[count]:
        ref            handleRef
        denseId        u32
        slugString     stringId
```

`CompiledHandleKind` ordinals are the ABI for `handleRef.kind` in this smoke
format:

```text
0 Building
1 Dungeon
2 DungeonEvent
3 EncounterTier
4 Flag
5 FlagState
6 Formula
7 Image
8 Item
9 ItemPool
10 KeyAction
11 Location
12 LootTable
13 MapLayer
14 Milestone
15 Mission
16 Monster
17 NativeOp
18 Npc
19 NpcJob
20 PaletteColor
21 Party
22 RewardBundle
23 Role
24 Rumour
25 RumourTable
26 Scene
27 ScreenRegion
28 ScriptRoot
29 Spell
30 State
31 StaticScreen
32 Terrain
33 TerrainTag
34 Text
35 Town
36 WorldMap
```

## Script Programs

Scripts are written sorted by root handle kind ordinal and root handle id.

```text
scripts:
    count              u32
    entries[count]:
        rootHandle     handleRef, kind must be ScriptRoot
        instructions   instructionList

instructionList:
    count              u32
    entries[count]:
        opcode         u32 CompiledOpcode ordinal
        operands       operandList

operandList:
    count              u32
    entries[count]:
        operand        tagged operand
```

## Opcode Set

The current opcodes are:

```text
0 Screen
1 Emit
2 EmitIndexed
3 WaitInput
4 Pause
5 View
6 LetInt
7 BindText
8 Set
9 Add
10 Sub
11 AddClamped
12 SetFlag
13 AppendJournal
14 If
15 Loop
16 Break
17 Stop
18 Switch
19 Menu
20 AskKey
21 AskMember
22 AskInventorySlot
23 AskText
24 AskMonster
25 ForEachMember
26 ForEachMonster
27 Speak
28 TellRumour
29 RunScene
30 Encounter
31 EnterDungeon
32 ConsumeActiveItem
33 ClearInventorySlot
34 ConsumeInventoryPool
35 ConsumeTorch
36 TeleportParty
37 DiscoverCave
38 GrantReward
39 RandomItemOffer
40 ShopService
41 AchieveMilestone
42 NativeCall
```

The compiler emits operands in this order:

```text
Screen(textBlock)
Emit(textHandle)
EmitIndexed(patternString, valueIntExpr)
WaitInput(reasonString?)
Pause(milliseconds)
View(viewCommand)
LetInt(localNameString, valueIntExpr)
BindText(localNameString, valueStringExpr)
Set(targetLValue, valueIntExpr)
Add(targetLValue, valueIntExpr)
Sub(targetLValue, valueIntExpr)
AddClamped(targetLValue, valueIntExpr, maxIntExpr)
SetFlag(flagHandle, flagStateHandle)
AppendJournal(textBlock)
If(conditionBoolExpr, thenInstructions, elseInstructions)
Loop(bodyInstructions)
Break(reasonString?, signalEnumName)
Stop(reasonString?, signalEnumName)
Switch(valueIntExpr, switchCases)
Menu(promptTextBlock, menuOptions, defaultInstructions)
AskKey(promptTextBlock, varNameString, inputBindings)
AskMember(promptTextBlock, varNameString, filterEnumName, acceptingEnumName)
AskInventorySlot(promptTextBlock, varNameString, firstKeyChar, firstSlot, lastSlot, filterEnumName)
AskText(promptTextBlock, textAnswer)
AskMonster(promptTextBlock, varNameString)
ForEachMember(varNameString, includeDeadBool, bodyInstructions)
ForEachMonster(varNameString, includeDeadBool, bodyInstructions)
Speak(buildingHandle)
TellRumour(rumourTableHandle)
RunScene(sceneHandle, flatBindingStringPairs)
Encounter(monsterHandle, mandatoryBool, encounterTierHandle?)
EnterDungeon(dungeonHandle, levels)
ConsumeActiveItem()
ClearInventorySlot(slotIntExpr)
ConsumeInventoryPool(itemPoolHandle, quantityIntExpr)
ConsumeTorch()
TeleportParty(directionKeyIntExpr, distanceIntExpr)
DiscoverCave(dungeonHandle, levelsIntExpr, moveCostIntExpr)
GrantReward(scriptReward)
RandomItemOffer(randomItemOffer)
ShopService(shopService)
AchieveMilestone(milestoneHandle)
NativeCall(pathString, argStrings)
```

## Operand Tags

```text
0 Integer(i32)
1 BooleanValue(bool)
2 Character(u32 Unicode scalar value)
3 StringId(stringId)
4 StringIdList(list<stringId>)
5 Handle(handleRef)
6 HandleList(list<handleRef>)
7 TextBlockValue(textBlock)
8 IntExprValue(intExpr)
9 BoolExprValue(boolExpr)
10 StringExprValue(stringExpr)
11 LValueValue(lValue)
12 ViewCommandValue(viewCommand)
13 Instructions(instructionList)
14 SwitchCases(list<switchCase>)
15 MenuOptions(list<menuOption>)
16 InputBindings(list<inputBinding>)
17 TextAnswer(textAnswer)
18 Reward(scriptReward)
19 RandomItemOffer(randomItemOffer)
20 ShopService(shopService)
21 EnumName(inlineString)
```

## Nested VM Records

```text
switchCase:
    match              nullable<i32>
    body               instructionList

menuOption:
    binding            inputBinding
    body               instructionList

inputBinding:
    primaryString      stringId
    aliasStrings       list<stringId>
    labelString        stringId

textAnswer:
    acceptedStrings    list<stringId>
    matchOps           instructionList
    missOps            instructionList
    caseSensitive      bool
    trim               bool
    maxLength          nullable<i32>
```

## Text Records

```text
textBlock:
    tag 0 Ref(textHandle)
    tag 1 Inline(lineStrings: list<stringId>)
    tag 2 Structured(lines: list<textLine>)

textLine:
    parts              list<textPart>

textPart:
    tag 0 Literal(stringId)
    tag 1 IntValue(intExpr)
    tag 2 StringValue(stringExpr)
    tag 3 RoleName(roleHandle)
    tag 4 RoleLocation(roleHandle)
    tag 5 TownName(townHandle)
    tag 6 FuzzyTownLocation(townHandle, radius: i32)
    tag 7 LocationCoordinate(locationHandle)
    tag 8 Runtime(runtimeTextPartOrdinal: u32)
```

`CompiledRuntimeTextPart` ordinals must be less than the header
`runtimeTextPartCount`.

## View Commands

```text
viewCommand:
    tag 0 ClearText
    tag 1 ClearMapWindow
    tag 2 RenderMap
    tag 3 StatusLine
    tag 4 ShowImage(imageHandle)
```

A VM should route these through a renderer boundary. They are not terminal
cursor operations.

## Expressions

```text
intExpr:
    tag 0 Literal(i32)
    tag 1 Local(nameString)
    tag 2 Binary(left: intExpr, op: enumName, right: intExpr)
    tag 3 If(cond: boolExpr, thenValue: intExpr, elseValue: intExpr)
    tag 4 Random(lo: intExpr, hi: intExpr)
    tag 5 MemberField(member: intExpr, fieldString)
    tag 6 MonsterField(monster: intExpr, fieldString)
    tag 7 BattleFlag(nameString)
    tag 8 BattleQuery(nameString)
    tag 9 RealmScalar(nameString)
    tag 10 PartyScalar(nameString)
    tag 11 InventoryPoolCount(itemPoolHandle)
    tag 12 InventorySlotCost(slot: intExpr)
    tag 13 NativeInt(pathString, argStrings: list<stringId>)

boolExpr:
    tag 0 Cmp(left: intExpr, op: enumName, right: intExpr)
    tag 1 Not(expr: boolExpr)
    tag 2 And(left: boolExpr, right: boolExpr)
    tag 3 Or(left: boolExpr, right: boolExpr)
    tag 4 FlagIs(flagHandle, flagStateHandle)
    tag 5 MilestoneAchieved(milestoneHandle)
    tag 6 MemberFlag(member: intExpr, nameString)
    tag 7 MonsterFlag(monster: intExpr, nameString)
    tag 8 BattleFlag(nameString)
    tag 9 RuntimeFlag(nameString)

stringExpr:
    tag 0 NativeString(pathString, args: list<nativeStringArg>)

nativeStringArg:
    tag 0 Literal(stringId)
    tag 1 IntValue(intExpr)

lValue:
    tag 0 RealmScalar(nameString)
    tag 1 PartyScalar(nameString)
    tag 2 MemberField(member: intExpr, fieldString)
    tag 3 MonsterField(monster: intExpr, fieldString)
    tag 4 BattleFlag(nameString)
```

`NativeInt`, `NativeString`, and `NativeCall` are narrow implementation hooks.
The loader should reject unknown paths once J1 includes the native-op allowlist.

## Reward And Shop Records

```text
scriptReward:
    gold               nullable<intExpr>
    items              list<handleRef>
    application        rewardApplication
    messages           rewardMessages

rewardApplication:
    regularItems       enumName
    overflow           enumName
    specialItems       enumName

rewardMessages:
    cantCarry          nullable<textBlock>
    pickedUp           nullable<textBlock>

randomItemOffer:
    pool               itemPoolHandle
    offer              textBlock
    choicePrompt       textBlock
    broke              textBlock
    deal               textBlock
    declined           nullable<textBlock>
    application        rewardApplication
    messages           rewardMessages
    buy                inputBinding
    decline            inputBinding
    priceOverride      nullable<i32>
    brokePauseMillis   i32
    fullPauseMillis    i32
    dealPauseMillis    i32
    declinedPauseMillis i32
```

```text
shopService:
    building           buildingHandle
    image              imageHandle
    closed             textBlock
    intro              textBlock
    menu               textBlock
    buyKey             inputBinding
    sellKey            inputBinding
    leaveKey           inputBinding
    goodbye            textBlock
    buy                nullable<shopBuy>
    sell               nullable<shopSell>
    interlude          nullable<shopInterlude>

shopBuy:
    prompt             textBlock
    catalogs           list<shopCatalog>
    tooExpensive       textBlock
    cantCarry          textBlock
    deal               textBlock
    unitPrice          nullable<textBlock>
    singlePrice        nullable<textBlock>
    payPrompt          textBlock
    payKey             inputBinding
    declineKey         inputBinding
    inventoryApplication rewardApplication

shopCatalog:
    binding            inputBinding
    titleString        stringId
    intro              nullable<textBlock>
    pages              list<shopCatalogPage>

shopCatalogPage:
    entries            list<shopItemOffer>
    footer             nullable<textBlock>

shopItemOffer:
    binding            inputBinding
    item               itemHandle
    labelString        nullable<stringId>
    description        nullable<textBlock>
    price              shopPrice
    purchase           shopPurchase

shopPrice:
    tag 0 ItemCost
    tag 1 HalfItemCost
    tag 2 Fixed(amount: i32)
    tag 3 LeaderLevelPlus(addend: i32)
    tag 4 ItemCostPlusLeaderLevelTimes(multiplier: i32)

shopPurchase:
    tag 0 InventoryItem
    tag 1 Bulk(resourceEnumName, quantityPrompt: textBlock, cap: shopQuantityCap,
               tooMany: textBlock, zeroQuantityDealEnumName)

shopQuantityCap:
    tag 0 FixedAmount(resourceEnumName, amount: i32)
    tag 1 CarryCapacity(resourceEnumName)

shopSell:
    prompt             textBlock
    acceptedKinds      list<enumName>
    acceptedItems      list<handleRef>
    rejectedItems      list<handleRef>
    wrongKind          textBlock
    offer              textBlock
    sold               nullable<textBlock>
    price              shopPrice
    bargain            nullable<shopBargain>
    acceptKey          inputBinding
    declineKey         inputBinding

shopBargain:
    bonuses            list<(rollGreaterThan: i32, addCurrentDivisor: i32)>
    rejectRollBelow    nullable<i32>

shopInterlude:
    chancePercent      i32
    offer              textBlock
    accept             inputBinding
    decline            inputBinding
    accepted           list<textBlock>
    declined           textBlock
    runsRumourAfterAccept bool
```

## Minimum VM Services

A standalone compiled-op VM must provide these services before it can execute
the current script set and run parity playthroughs:

- string and handle lookup by dense/native ids
- immutable content tables for items, pools, monsters, terrain, images, towns,
  buildings, scenes, rumours, spells, dungeons, rewards, and milestones
- mutable realm, party, member, inventory, journal, NPC, dungeon, Ydarg, and
  battle state storage
- deterministic RNG compatible with authored state snapshots
- expression evaluation over locals, realm fields, party fields, member fields,
  monster fields, flags, milestones, inventory pools, and battle queries
- input continuations for keys, member selection, inventory slots, text answers,
  monster targets, menus, and wait/pause points
- child-session handoffs for speaking, rumour selection, encounters/battles,
  dungeon entry, rewards, random-item offers, and shop services
- typed renderer commands for text, backdrops, map rendering, status lines, and
  static/full-screen views
- save/resume frames for script roots, nested branches, loops, menus, waits, and
  child sessions
- a native-op registry that is allowlisted, typed, and validated at load time
- a scenario driver that can feed the same semantic inputs/checkpoints used by
  the Kotlin playthrough harness, or an emitted equivalent scenario tape

Until J2 exists, these semantics are still provided by the Kotlin session
runtime and authored `Op` interpreter.
