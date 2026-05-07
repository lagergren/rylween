/**
 * DSL model and builders for authored grid-castle crawls.
 *
 * Authors declare `castle(id) { ... }` blocks, the wilderness runtime enters
 * the castle whose authored entry matches the map tile, and
 * `runtime/castle/CastleSession.kt` consumes only this content model rather
 * than hard-coded game tables. Terminal presentation remains in
 * `frontend/terminal/CastleSessionTerminalAdapter.kt`.
 */
@file:Suppress("unused", "MemberVisibilityCanBePrivate", "CanBeParameter", "KotlinExplicitApi")

package rylween.dsl

/** Room-event categories encoded in a generated castle room map. */
enum class CastleRoomEvent { Empty, Monster, Pitfall, Treasure }

/** Authored key bindings used by castle navigation and prompts. */
data class CastleKeysModel(
    val moveNorth: InputBindingModel = inputKey("8"),
    val moveSouth: InputBindingModel = inputKey("2"),
    val moveWest: InputBindingModel = inputKey("4"),
    val moveEast: InputBindingModel = inputKey("6"),
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
    val roomMonster: TextBlock = inlineText("You are spotted by", "monsters!"),
    val trapTriesDisarmSuffix: TextBlock = inlineText("tries to", "disarm it...", ""),
    val trapDisarmSucceed: TextBlock = inlineText("AND SUCCEEDS!"),
    val trapDisarmFail: TextBlock = inlineText("BUT FAILS!"),
    val treasureMenu: TextBlock = inlineText("(T)ake it", "(L)eave it"),
    val treasureFull: TextBlock = inlineText("You can't carry more."),
    val treasureTaken: TextBlock = inlineText("", "Ok."),
    val moveNorth: TextBlock = inlineText("North."),
    val moveSouth: TextBlock = inlineText("South."),
    val moveEast: TextBlock = inlineText("East."),
    val moveWest: TextBlock = inlineText("West."),
    val stairsIntro: TextBlock = inlineText("You stand before the", "stairs to the next level.", ""),
    val stairsGuardianOffer: TextBlock = inlineText("A guardian offers passage.", ""),
    val stairsMenu: TextBlock = inlineText("Choose:", "", "(T)eleport Out", "(G)ive him an item", "(L)eave"),
    val stairsLeave: TextBlock = inlineText("You take the nearest exit!"),
    val teleportVortex: TextBlock = inlineText("The world fades into a", "vortex of agonizing", "vertigo...", ""),
    val teleportOutside: TextBlock = inlineText("And you are outside..."),
    val giveMenu: TextBlock = inlineText("Give him what?", "", "5. Nothing"),
    val stairsGuardianGrateful: TextBlock = inlineText("The guardian opens the way.", ""),
    val floorNames: List<String> = emptyList(),
)

/** Complete authored grid-castle model consumed by `runtime/castle/CastleSession.kt`. */
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
    var entry: GridPoint = GridPoint(14, 30)
    var image: ImageId? = null
    var entryRequirement: GameMilestoneId? = null
    var completionMilestone: GameMilestoneId? = null
    var mapSize: Int = 5
    var roomTypeMax: Int = 5
    var monsterTier: EncounterTier? = null
    private val roomEvents =
        mutableMapOf(
            1 to CastleRoomEvent.Monster,
            2 to CastleRoomEvent.Pitfall,
            3 to CastleRoomEvent.Treasure,
        )
    private var treasureModel = CastleTreasureBuilder().build()
    private var pitfallModel = CastlePitfallBuilder().build()
    private var stairsModel = CastleStairsBuilder().build()
    private var scriptsModel = CastleScriptsBuilder().build()
    private var keysModel = CastleKeysModel()
    private var textModel = CastleTextBuilder().build()

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

    fun build(): CastleContentModel =
        CastleContentModel(
            id = id,
            entry = entry,
            image = image,
            entryRequirement = entryRequirement,
            completionMilestone = completionMilestone,
            mapSize = mapSize,
            roomTypeMax = roomTypeMax,
            roomEvents = roomEvents.toMap(),
            monsterTier = checkNotNull(monsterTier) { "castle ${id.slug} requires monsterTier" },
            treasure = treasureModel,
            pitfall = pitfallModel,
            stairs = stairsModel,
            scripts = scriptsModel,
            keys = keysModel,
            text = textModel,
        )
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
    var treasureTake: InputBindingModel = initial.treasureTake
    var treasureLeave: InputBindingModel = initial.treasureLeave
    var stairsTeleport: InputBindingModel = initial.stairsTeleport
    var stairsGive: InputBindingModel = initial.stairsGive
    var stairsLeave: InputBindingModel = initial.stairsLeave

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
    var treasureMenu: TextBlock = initial.treasureMenu
    var treasureFull: TextBlock = initial.treasureFull
    var treasureTaken: TextBlock = initial.treasureTaken
    var moveNorth: TextBlock = initial.moveNorth
    var moveSouth: TextBlock = initial.moveSouth
    var moveEast: TextBlock = initial.moveEast
    var moveWest: TextBlock = initial.moveWest
    var stairsIntro: TextBlock = initial.stairsIntro
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
            treasureMenu = treasureMenu,
            treasureFull = treasureFull,
            treasureTaken = treasureTaken,
            moveNorth = moveNorth,
            moveSouth = moveSouth,
            moveEast = moveEast,
            moveWest = moveWest,
            stairsIntro = stairsIntro,
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
