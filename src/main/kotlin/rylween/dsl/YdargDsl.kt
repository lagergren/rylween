@file:Suppress("unused", "MemberVisibilityCanBePrivate", "CanBeParameter", "KotlinExplicitApi")

package rylween.dsl

enum class YdargRoomEvent { Empty, Monster, Pitfall, Treasure }

data class YdargKeysModel(
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

data class YdargTreasureModel(
    val gateMax: Int = 4,
    val regularKinds: List<List<ItemId>> = emptyList(),
    val magic: List<ItemId> = emptyList(),
)

data class YdargPitfallModel(
    val damageDivisor: Int = 6,
    val trapTypes: List<TextBlock> = emptyList(),
)

data class YdargStairItemModel(
    val digit: Int,
    val milestone: GameMilestoneId,
    val itemName: String,
    val reject: TextBlock,
)

data class YdargStairsModel(
    val floorCount: Int = 4,
    val items: List<YdargStairItemModel> = emptyList(),
)

data class YdargConfrontationModel(
    val sequence: List<MonsterId> = emptyList(),
    val baron: MonsterId? = null,
)

data class YdargTextModel(
    val ruin: TextBlock = inlineText("Castle Ydarg is silent.", ""),
    val approach: TextBlock = inlineText("Castle Ydarg looms ahead.", ""),
    val jaaraForm: TextBlock = inlineText("A ghostly guardian appears.", ""),
    val jaaraSpeech: TextBlock = inlineText("The gate is sealed.", ""),
    val noKey: TextBlock = inlineText("You need the Silver Key.", ""),
    val keyAccepted: TextBlock = inlineText("The Silver Key opens the gate.", ""),
    val enter: TextBlock = inlineText("You enter.", ""),
    val doorSlams: TextBlock = inlineText("The door slams shut behind you."),
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
    val stairsMageOffer: TextBlock = inlineText("A mage offers passage.", ""),
    val stairsMenu: TextBlock = inlineText("Choose:", "", "(T)eleport Out", "(G)ive him an item", "(L)eave"),
    val stairsLeave: TextBlock = inlineText("You take the nearest exit!"),
    val teleportVortex: TextBlock = inlineText("The world fades into a", "vortex of agonizing", "vertigo...", ""),
    val teleportOutside: TextBlock = inlineText("And you are outside..."),
    val giveMenu: TextBlock = inlineText("Give him what?", "", "5. Nothing"),
    val mageGrateful: TextBlock = inlineText("The mage opens the way.", ""),
    val confrontationAct1: List<TextBlock> = emptyList(),
    val confrontationAct2: List<TextBlock> = emptyList(),
    val confrontationAct3: List<TextBlock> = emptyList(),
    val confrontationAct4: List<TextBlock> = emptyList(),
)

data class YdargContentModel(
    val entry: GridPoint,
    val image: ImageId?,
    val requiredKey: GameMilestoneId,
    val victoryMilestone: GameMilestoneId,
    val mapSize: Int,
    val roomTypeMax: Int,
    val roomEvents: Map<Int, YdargRoomEvent>,
    val monsterTier: EncounterTier,
    val treasure: YdargTreasureModel,
    val pitfall: YdargPitfallModel,
    val stairs: YdargStairsModel,
    val confrontation: YdargConfrontationModel,
    val keys: YdargKeysModel,
    val text: YdargTextModel,
)

@RylNextDsl
class YdargBuilder {
    var entry: GridPoint = GridPoint(14, 30)
    var image: ImageId? = null
    var requiredKey: GameMilestoneId? = null
    var victoryMilestone: GameMilestoneId? = null
    var mapSize: Int = 5
    var roomTypeMax: Int = 5
    var monsterTier: EncounterTier? = null
    private val roomEvents = mutableMapOf(
        1 to YdargRoomEvent.Monster,
        2 to YdargRoomEvent.Pitfall,
        3 to YdargRoomEvent.Treasure,
    )
    private var treasureModel = YdargTreasureBuilder().build()
    private var pitfallModel = YdargPitfallBuilder().build()
    private var stairsModel = YdargStairsBuilder().build()
    private var confrontationModel = YdargConfrontationBuilder().build()
    private var keysModel = YdargKeysModel()
    private var textModel = YdargTextBuilder().build()

    fun map(size: Int, roomTypeMax: Int = this.roomTypeMax) {
        this.mapSize = size
        this.roomTypeMax = roomTypeMax
    }

    fun roomEvent(roll: Int, event: YdargRoomEvent) {
        roomEvents[roll] = event
    }

    fun treasure(body: YdargTreasureBuilder.() -> Unit) {
        treasureModel = YdargTreasureBuilder(treasureModel).apply(body).build()
    }

    fun pitfall(body: YdargPitfallBuilder.() -> Unit) {
        pitfallModel = YdargPitfallBuilder(pitfallModel).apply(body).build()
    }

    fun stairs(body: YdargStairsBuilder.() -> Unit) {
        stairsModel = YdargStairsBuilder(stairsModel).apply(body).build()
    }

    fun confrontation(body: YdargConfrontationBuilder.() -> Unit) {
        confrontationModel = YdargConfrontationBuilder(confrontationModel).apply(body).build()
    }

    fun keys(body: YdargKeysBuilder.() -> Unit) {
        keysModel = YdargKeysBuilder(keysModel).apply(body).build()
    }

    fun text(body: YdargTextBuilder.() -> Unit) {
        textModel = YdargTextBuilder(textModel).apply(body).build()
    }

    fun build(): YdargContentModel =
        YdargContentModel(
            entry = entry,
            image = image,
            requiredKey = checkNotNull(requiredKey) { "ydarg requires requiredKey" },
            victoryMilestone = checkNotNull(victoryMilestone) { "ydarg requires victoryMilestone" },
            mapSize = mapSize,
            roomTypeMax = roomTypeMax,
            roomEvents = roomEvents.toMap(),
            monsterTier = checkNotNull(monsterTier) { "ydarg requires monsterTier" },
            treasure = treasureModel,
            pitfall = pitfallModel,
            stairs = stairsModel,
            confrontation = confrontationModel,
            keys = keysModel,
            text = textModel,
        )
}

@RylNextDsl
class YdargTreasureBuilder(initial: YdargTreasureModel = YdargTreasureModel()) {
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

    fun build(): YdargTreasureModel =
        YdargTreasureModel(
            gateMax = gateMax,
            regularKinds = regularKinds.toList(),
            magic = magic.toList(),
        )
}

@RylNextDsl
class YdargPitfallBuilder(initial: YdargPitfallModel = YdargPitfallModel()) {
    var damageDivisor: Int = initial.damageDivisor
    private val trapTypes = initial.trapTypes.toMutableList()

    fun trap(body: StructuredTextBlockBuilder.() -> Unit) {
        trapTypes += structuredText(body)
    }

    fun build(): YdargPitfallModel =
        YdargPitfallModel(
            damageDivisor = damageDivisor,
            trapTypes = trapTypes.toList(),
        )
}

@RylNextDsl
class YdargStairsBuilder(initial: YdargStairsModel = YdargStairsModel()) {
    var floorCount: Int = initial.floorCount
    private val items = initial.items.toMutableList()

    fun item(digit: Int, milestone: GameMilestoneId, itemName: String, reject: TextBlock) {
        items += YdargStairItemModel(digit, milestone, itemName, reject)
    }

    fun build(): YdargStairsModel =
        YdargStairsModel(
            floorCount = floorCount,
            items = items.toList(),
        )
}

@RylNextDsl
class YdargConfrontationBuilder(initial: YdargConfrontationModel = YdargConfrontationModel()) {
    private val sequence = initial.sequence.toMutableList()
    var baron: MonsterId? = initial.baron

    fun sequence(vararg monsters: MonsterId) {
        sequence.clear()
        sequence += monsters
    }

    fun build(): YdargConfrontationModel =
        YdargConfrontationModel(sequence = sequence.toList(), baron = baron)
}

@RylNextDsl
class YdargKeysBuilder(initial: YdargKeysModel = YdargKeysModel()) {
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

    fun build(): YdargKeysModel =
        YdargKeysModel(
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
class YdargTextBuilder(initial: YdargTextModel = YdargTextModel()) {
    var ruin: TextBlock = initial.ruin
    var approach: TextBlock = initial.approach
    var jaaraForm: TextBlock = initial.jaaraForm
    var jaaraSpeech: TextBlock = initial.jaaraSpeech
    var noKey: TextBlock = initial.noKey
    var keyAccepted: TextBlock = initial.keyAccepted
    var enter: TextBlock = initial.enter
    var doorSlams: TextBlock = initial.doorSlams
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
    var stairsMageOffer: TextBlock = initial.stairsMageOffer
    var stairsMenu: TextBlock = initial.stairsMenu
    var stairsLeave: TextBlock = initial.stairsLeave
    var teleportVortex: TextBlock = initial.teleportVortex
    var teleportOutside: TextBlock = initial.teleportOutside
    var giveMenu: TextBlock = initial.giveMenu
    var mageGrateful: TextBlock = initial.mageGrateful
    private val confrontationAct1 = initial.confrontationAct1.toMutableList()
    private val confrontationAct2 = initial.confrontationAct2.toMutableList()
    private val confrontationAct3 = initial.confrontationAct3.toMutableList()
    private val confrontationAct4 = initial.confrontationAct4.toMutableList()

    fun confrontationAct1(vararg blocks: TextBlock) {
        confrontationAct1.clear()
        confrontationAct1 += blocks
    }

    fun confrontationAct2(vararg blocks: TextBlock) {
        confrontationAct2.clear()
        confrontationAct2 += blocks
    }

    fun confrontationAct3(vararg blocks: TextBlock) {
        confrontationAct3.clear()
        confrontationAct3 += blocks
    }

    fun confrontationAct4(vararg blocks: TextBlock) {
        confrontationAct4.clear()
        confrontationAct4 += blocks
    }

    fun build(): YdargTextModel =
        YdargTextModel(
            ruin = ruin,
            approach = approach,
            jaaraForm = jaaraForm,
            jaaraSpeech = jaaraSpeech,
            noKey = noKey,
            keyAccepted = keyAccepted,
            enter = enter,
            doorSlams = doorSlams,
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
            stairsMageOffer = stairsMageOffer,
            stairsMenu = stairsMenu,
            stairsLeave = stairsLeave,
            teleportVortex = teleportVortex,
            teleportOutside = teleportOutside,
            giveMenu = giveMenu,
            mageGrateful = mageGrateful,
            confrontationAct1 = confrontationAct1.toList(),
            confrontationAct2 = confrontationAct2.toList(),
            confrontationAct3 = confrontationAct3.toList(),
            confrontationAct4 = confrontationAct4.toList(),
        )
}
