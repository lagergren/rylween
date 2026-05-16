/**
 * Castle authoring model and builders.
 *
 * This file owns the reusable grid-castle DSL surface: castle room event
 * categories, navigation key contracts, treasure/pitfall/stair models, and the
 * `castle { ... }` builder used from the authored game DSL. It deliberately
 * contains only generic castle mechanics; Castle Ydarg coordinates, prose,
 * item names, trap text, and completion scripts belong in
 * `game/src/main/dsl/RylweenGame.dsl.kts`.
 */
package rylween.dsl.impl

import rylween.dsl.CastleId
import rylween.dsl.EncounterTier
import rylween.dsl.GameMilestoneId
import rylween.dsl.ImageId
import rylween.dsl.InputAliases
import rylween.dsl.InputBindingModel
import rylween.dsl.ItemId
import rylween.dsl.RylNextDsl
import rylween.dsl.SceneId
import rylween.dsl.StructuredTextBlockBuilder
import rylween.dsl.TextBlock
import rylween.dsl.inlineText
import rylween.dsl.inputKey
import rylween.dsl.structuredText

/** Room-event categories encoded in a generated castle room map. */
enum class CastleRoomEvent { Empty, Monster, Pitfall, Treasure }

/** Authored key bindings used by castle navigation and prompts. */
data class CastleKeysModel(
    val moveNorth: InputBindingModel = inputKey("8", InputAliases.ARROW_UP, "UP", "ARROW_UP", label = "8/↑"),
    val moveSouth: InputBindingModel = inputKey("2", InputAliases.ARROW_DOWN, "DOWN", "ARROW_DOWN", label = "2/↓"),
    val moveWest: InputBindingModel = inputKey("4", InputAliases.ARROW_LEFT, "LEFT", "ARROW_LEFT", label = "4/←"),
    val moveEast: InputBindingModel = inputKey("6", InputAliases.ARROW_RIGHT, "RIGHT", "ARROW_RIGHT", label = "6/→"),
    val save: InputBindingModel = inputKey("S"),
    val help: InputBindingModel = inputKey("?"),
    val viewParty: InputBindingModel = inputKey("V"),
    val removeArmor: InputBindingModel = inputKey("T"),
    val removeWeapon: InputBindingModel = inputKey("P"),
    val drop: InputBindingModel = inputKey("L"),
    val readyWeapon: InputBindingModel = inputKey("W"),
    val readyArmor: InputBindingModel = inputKey("R"),
    val quaff: InputBindingModel = inputKey("Q"),
    val castSpell: InputBindingModel = inputKey("Z"),
    val treasureTake: InputBindingModel = inputKey("T"),
    val treasureLeave: InputBindingModel = inputKey("L"),
    val stairsTeleport: InputBindingModel = inputKey("T"),
    val stairsGive: InputBindingModel = inputKey("G"),
    val stairsLeave: InputBindingModel = inputKey("L"),
    val commandLabels: CastleCommandLabelsModel = CastleCommandLabelsModel(),
)

/** Descriptive labels exported with castle command bindings for all renderers. */
data class CastleCommandLabelsModel(
    val moveNorth: String = "Move North",
    val moveSouth: String = "Move South",
    val moveWest: String = "Move West",
    val moveEast: String = "Move East",
    val save: String = "Save Game",
    val help: String = "View this help",
    val viewParty: String = "View Party",
    val removeArmor: String = "Take off armor",
    val removeWeapon: String = "Put down weapon",
    val drop: String = "Leave (Discard) Item",
    val readyWeapon: String = "Wield Weapon",
    val readyArmor: String = "Ready Armor",
    val quaff: String = "Quaff Potion",
    val castSpell: String = "Cast Spell",
    val treasureTake: String = "Take Treasure",
    val treasureLeave: String = "Leave Treasure",
    val stairsTeleport: String = "Teleport Out",
    val stairsGive: String = "Give Item",
    val stairsLeave: String = "Leave",
)

/** Treasure-generation tables and gates for castle rooms. */
data class CastleTreasureModel(
    val gateMax: Int = 4,
    val regularKinds: List<List<ItemId>> = emptyList(),
    val magic: List<ItemId> = emptyList(),
)

/** Pitfall damage and narration configuration for castle trap rooms. */
data class CastlePitfallModel(
    val damageDivisor: Int = 6,
    val trapTypes: List<TextBlock> = emptyList(),
)

/** Milestone-backed item accepted by a castle stair guardian. */
data class CastleStairItemModel(
    val digit: Int,
    val milestone: GameMilestoneId,
    val itemName: String,
    val reject: TextBlock,
)

/** Stair progression and required item configuration for a castle. */
data class CastleStairsModel(
    val floorCount: Int = 4,
    val items: List<CastleStairItemModel> = emptyList(),
)

/** Authored scripts attached to generic castle lifecycle hooks. */
data class CastleScriptsModel(
    val alreadyCompleted: SceneId? = null,
    val entryBlocked: SceneId? = null,
    val entered: SceneId? = null,
    val completed: SceneId? = null,
)

/** Text prompts and narration used by the castle runtime session. */
data class CastleTextModel(
    val roomMonster: TextBlock = inlineText(),
    val trapTriesDisarmSuffix: TextBlock = inlineText(),
    val trapDisarmSucceed: TextBlock = inlineText(),
    val trapDisarmFail: TextBlock = inlineText(),
    val pitfallIllustration: ImageId? = null,
    val treasureIllustration: ImageId? = null,
    val treasureMenu: TextBlock = inlineText(),
    val treasureFull: TextBlock = inlineText(),
    val treasureTaken: TextBlock = inlineText(),
    val moveNorth: TextBlock = inlineText(),
    val moveSouth: TextBlock = inlineText(),
    val moveEast: TextBlock = inlineText(),
    val moveWest: TextBlock = inlineText(),
    val stairsIntro: TextBlock = inlineText(),
    val stairsIllustration: ImageId? = null,
    val stairsGuardianOffer: TextBlock = inlineText(),
    val stairsMenu: TextBlock = inlineText(),
    val stairsLeave: TextBlock = inlineText(),
    val teleportVortex: TextBlock = inlineText(),
    val teleportOutside: TextBlock = inlineText(),
    val giveMenu: TextBlock = inlineText(),
    val stairsGuardianGrateful: TextBlock = inlineText(),
    val floorNames: List<String> = emptyList(),
)

/** Complete authored grid-castle model consumed by the runtime. */
data class CastleContentModel(
    val id: CastleId,
    val entry: GridPoint,
    val image: ImageId?,
    val entryRequirement: GameMilestoneId?,
    val completionMilestone: GameMilestoneId?,
    val mapSize: Int,
    val roomTypeMax: Int,
    val roomEvents: Map<Int, CastleRoomEvent>,
    val monsterTier: EncounterTier,
    val treasure: CastleTreasureModel,
    val pitfall: CastlePitfallModel,
    val stairs: CastleStairsModel,
    val scripts: CastleScriptsModel,
    val keys: CastleKeysModel,
    val text: CastleTextModel,
)

/** Builder for one authored castle block in the game DSL. */
@RylNextDsl
class CastleBuilder(
    private val id: CastleId,
) {
    private var entryConfigured = false
    var entry: GridPoint = GridPoint(0, 0)
        set(value) {
            field = value
            entryConfigured = true
        }
    var image: ImageId? = null
    var entryRequirement: GameMilestoneId? = null
    var completionMilestone: GameMilestoneId? = null
    var mapSize: Int = 5
    var roomTypeMax: Int = 5
    var monsterTier: EncounterTier? = null
    private val roomEvents = mutableMapOf<Int, CastleRoomEvent>()
    private var treasureModel = CastleTreasureBuilder().build()
    private var pitfallModel = CastlePitfallBuilder().build()
    private var stairsModel = CastleStairsBuilder().build()
    private var scriptsModel = CastleScriptsBuilder().build()
    private var keysModel = CastleKeysModel()
    private var textModel = CastleTextBuilder().build()

    init {
        roomEvent(1, CastleRoomEvent.Monster)
        roomEvent(2, CastleRoomEvent.Pitfall)
        roomEvent(3, CastleRoomEvent.Treasure)
    }

    fun map(
        size: Int,
        roomTypeMax: Int = this.roomTypeMax,
    ) {
        this.mapSize = size
        this.roomTypeMax = roomTypeMax
    }

    fun roomEvent(
        roll: Int,
        event: CastleRoomEvent,
    ) {
        roomEvents[roll] = event
    }

    fun treasure(body: CastleTreasureBuilder.() -> Unit) {
        treasureModel = CastleTreasureBuilder(treasureModel).apply(body).build()
    }

    fun pitfall(body: CastlePitfallBuilder.() -> Unit) {
        pitfallModel = CastlePitfallBuilder(pitfallModel).apply(body).build()
    }

    fun stairs(body: CastleStairsBuilder.() -> Unit) {
        stairsModel = CastleStairsBuilder(stairsModel).apply(body).build()
    }

    fun scripts(body: CastleScriptsBuilder.() -> Unit) {
        scriptsModel = CastleScriptsBuilder(scriptsModel).apply(body).build()
    }

    fun keys(body: CastleKeysBuilder.() -> Unit) {
        keysModel = CastleKeysBuilder(keysModel).apply(body).build()
    }

    fun text(body: CastleTextBuilder.() -> Unit) {
        textModel = CastleTextBuilder(textModel).apply(body).build()
    }

    fun build(): CastleContentModel {
        check(entryConfigured) { "castle ${id.slug} requires entry" }
        return CastleContentModel(
            id = id,
            entry = entry,
            image = image,
            entryRequirement = entryRequirement,
            completionMilestone = completionMilestone,
            mapSize = mapSize,
            roomTypeMax = roomTypeMax,
            roomEvents = roomEvents.filterValues { event -> event != CastleRoomEvent.Empty },
            monsterTier = checkNotNull(monsterTier) { "castle ${id.slug} requires monsterTier" },
            treasure = treasureModel,
            pitfall = pitfallModel,
            stairs = stairsModel,
            scripts = scriptsModel,
            keys = keysModel,
            text = textModel,
        )
    }
}

@RylNextDsl
class CastleTreasureBuilder(
    initial: CastleTreasureModel = CastleTreasureModel(),
) {
    var gateMax: Int = initial.gateMax
    private val regularKinds = initial.regularKinds.toMutableList()
    private val magic = initial.magic.toMutableList()

    fun regularKind(vararg items: ItemId) {
        regularKinds += items.toList()
    }

    fun magic(vararg items: ItemId) {
        magic.clear()
        magic += items
    }

    fun build(): CastleTreasureModel =
        CastleTreasureModel(
            gateMax = gateMax,
            regularKinds = regularKinds.toList(),
            magic = magic.toList(),
        )
}

@RylNextDsl
class CastlePitfallBuilder(
    initial: CastlePitfallModel = CastlePitfallModel(),
) {
    var damageDivisor: Int = initial.damageDivisor
    private val trapTypes = initial.trapTypes.toMutableList()

    fun trap(body: StructuredTextBlockBuilder.() -> Unit) {
        trapTypes += structuredText(body)
    }

    fun build(): CastlePitfallModel =
        CastlePitfallModel(
            damageDivisor = damageDivisor,
            trapTypes = trapTypes.toList(),
        )
}

@RylNextDsl
class CastleStairsBuilder(
    initial: CastleStairsModel = CastleStairsModel(),
) {
    var floorCount: Int = initial.floorCount
    private val items = initial.items.toMutableList()

    fun item(
        digit: Int,
        milestone: GameMilestoneId,
        itemName: String,
        reject: TextBlock,
    ) {
        items += CastleStairItemModel(digit, milestone, itemName, reject)
    }

    fun build(): CastleStairsModel =
        CastleStairsModel(
            floorCount = floorCount,
            items = items.toList(),
        )
}

@RylNextDsl
class CastleScriptsBuilder(
    initial: CastleScriptsModel = CastleScriptsModel(),
) {
    var alreadyCompleted: SceneId? = initial.alreadyCompleted
    var entryBlocked: SceneId? = initial.entryBlocked
    var entered: SceneId? = initial.entered
    var completed: SceneId? = initial.completed

    fun build(): CastleScriptsModel =
        CastleScriptsModel(
            alreadyCompleted = alreadyCompleted,
            entryBlocked = entryBlocked,
            entered = entered,
            completed = completed,
        )
}

@RylNextDsl
class CastleKeysBuilder(
    initial: CastleKeysModel = CastleKeysModel(),
) {
    var moveNorth: InputBindingModel = initial.moveNorth
    var moveSouth: InputBindingModel = initial.moveSouth
    var moveWest: InputBindingModel = initial.moveWest
    var moveEast: InputBindingModel = initial.moveEast
    var save: InputBindingModel = initial.save
    var help: InputBindingModel = initial.help
    var viewParty: InputBindingModel = initial.viewParty
    var removeArmor: InputBindingModel = initial.removeArmor
    var removeWeapon: InputBindingModel = initial.removeWeapon
    var drop: InputBindingModel = initial.drop
    var readyWeapon: InputBindingModel = initial.readyWeapon
    var readyArmor: InputBindingModel = initial.readyArmor
    var quaff: InputBindingModel = initial.quaff
    var castSpell: InputBindingModel = initial.castSpell
    private var treasureTake = initial.treasureTake
    private var treasureLeave = initial.treasureLeave
    private var stairsTeleport = initial.stairsTeleport
    private var stairsGive = initial.stairsGive
    private var stairsLeave = initial.stairsLeave
    private var commandLabels: CastleCommandLabelsModel = initial.commandLabels

    /** Replaces the treasure-room take/leave bindings. */
    fun treasure(
        take: InputBindingModel = treasureTake,
        leave: InputBindingModel = treasureLeave,
    ) {
        treasureTake = take
        treasureLeave = leave
    }

    /** Replaces the treasure-room take/leave bindings from raw key characters. */
    fun treasure(
        take: Char,
        leave: Char,
    ) {
        treasure(inputKey(take), inputKey(leave))
    }

    /** Replaces the stair-room teleport/give/leave bindings. */
    fun stairs(
        teleport: InputBindingModel = stairsTeleport,
        give: InputBindingModel = stairsGive,
        leave: InputBindingModel = stairsLeave,
    ) {
        stairsTeleport = teleport
        stairsGive = give
        stairsLeave = leave
    }

    /** Replaces the stair-room teleport/give/leave bindings from raw key characters. */
    fun stairs(
        teleport: Char,
        give: Char,
        leave: Char,
    ) {
        stairs(inputKey(teleport), inputKey(give), inputKey(leave))
    }

    fun build(): CastleKeysModel =
        CastleKeysModel(
            moveNorth = moveNorth,
            moveSouth = moveSouth,
            moveWest = moveWest,
            moveEast = moveEast,
            save = save,
            help = help,
            viewParty = viewParty,
            removeArmor = removeArmor,
            removeWeapon = removeWeapon,
            drop = drop,
            readyWeapon = readyWeapon,
            readyArmor = readyArmor,
            quaff = quaff,
            castSpell = castSpell,
            treasureTake = treasureTake,
            treasureLeave = treasureLeave,
            stairsTeleport = stairsTeleport,
            stairsGive = stairsGive,
            stairsLeave = stairsLeave,
            commandLabels = commandLabels,
        )
}

@RylNextDsl
class CastleTextBuilder(
    initial: CastleTextModel = CastleTextModel(),
) {
    var roomMonster: TextBlock = initial.roomMonster
    var trapTriesDisarmSuffix: TextBlock = initial.trapTriesDisarmSuffix
    var trapDisarmSucceed: TextBlock = initial.trapDisarmSucceed
    var trapDisarmFail: TextBlock = initial.trapDisarmFail
    var pitfallIllustration: ImageId? = initial.pitfallIllustration
    var treasureIllustration: ImageId? = initial.treasureIllustration
    var treasureMenu: TextBlock = initial.treasureMenu
    var treasureFull: TextBlock = initial.treasureFull
    var treasureTaken: TextBlock = initial.treasureTaken
    var moveNorth: TextBlock = initial.moveNorth
    var moveSouth: TextBlock = initial.moveSouth
    var moveEast: TextBlock = initial.moveEast
    var moveWest: TextBlock = initial.moveWest
    var stairsIntro: TextBlock = initial.stairsIntro
    var stairsIllustration: ImageId? = initial.stairsIllustration
    var stairsGuardianOffer: TextBlock = initial.stairsGuardianOffer
    var stairsMenu: TextBlock = initial.stairsMenu
    var stairsLeave: TextBlock = initial.stairsLeave
    var teleportVortex: TextBlock = initial.teleportVortex
    var teleportOutside: TextBlock = initial.teleportOutside
    var giveMenu: TextBlock = initial.giveMenu
    var stairsGuardianGrateful: TextBlock = initial.stairsGuardianGrateful
    private val floorNames = initial.floorNames.toMutableList()

    fun floorNames(vararg names: String) {
        floorNames.clear()
        floorNames += names
    }

    fun build(): CastleTextModel =
        CastleTextModel(
            roomMonster = roomMonster,
            trapTriesDisarmSuffix = trapTriesDisarmSuffix,
            trapDisarmSucceed = trapDisarmSucceed,
            trapDisarmFail = trapDisarmFail,
            pitfallIllustration = pitfallIllustration,
            treasureIllustration = treasureIllustration,
            treasureMenu = treasureMenu,
            treasureFull = treasureFull,
            treasureTaken = treasureTaken,
            moveNorth = moveNorth,
            moveSouth = moveSouth,
            moveEast = moveEast,
            moveWest = moveWest,
            stairsIntro = stairsIntro,
            stairsIllustration = stairsIllustration,
            stairsGuardianOffer = stairsGuardianOffer,
            stairsMenu = stairsMenu,
            stairsLeave = stairsLeave,
            teleportVortex = teleportVortex,
            teleportOutside = teleportOutside,
            giveMenu = giveMenu,
            stairsGuardianGrateful = stairsGuardianGrateful,
            floorNames = floorNames.toList(),
        )
}
