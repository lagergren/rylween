package rylween.compiled

/**
 * Stable command group ids emitted in the external runtime-system bundle.
 *
 * The bundle still stores command groups as strings so native and web clients do
 * not need schema churn, but Kotlin producers and consumers should use these
 * ids instead of repeating string literals. Castle groups are instance-specific
 * because the authored castle id is part of the command-group path.
 */
enum class RuntimeCommandGroupId(
    val id: String,
) {
    Title("title"),
    Wilderness("wilderness"),
    WildernessDebug("wilderness.debug"),
    WildernessCave("wilderness.cave"),
    WildernessOutOfMoves("wilderness.outOfMoves"),
    WildernessQuitConfirm("wilderness.quitConfirm"),
    Dungeon("dungeon"),
    Town("town"),
    Speak("speak"),
    SpeakQuestions("speak.questions"),
    ;

    companion object {
        fun castleNavigation(castleId: String): String = "castle.$castleId.navigation"

        fun castleTreasure(castleId: String): String = "castle.$castleId.treasure"

        fun castleStairs(castleId: String): String = "castle.$castleId.stairs"
    }
}

/**
 * Stable command action ids emitted in the external runtime-system bundle.
 *
 * The values are intentionally broad enough to cover title, wilderness,
 * dungeon, castle, town, and reusable speak commands. Authored town/speak
 * menu models still own their labels and bindings; this enum names the
 * runtime-observable dispatch contract that Kotlin code depends on.
 */
enum class RuntimeCommandActionId(
    val id: String,
) {
    CreateNewParty("createNewParty"),
    ContinueSavedGame("continueSavedGame"),
    TerminateParty("terminateParty"),
    Scoreboard("scoreboard"),
    HallOfFame("hallOfFame"),
    Documentation("documentation"),
    Leave("leave"),
    MoveNorth("moveNorth"),
    MoveSouth("moveSouth"),
    MoveWest("moveWest"),
    MoveEast("moveEast"),
    Pass("pass"),
    Save("save"),
    ReadyWeapon("readyWeapon"),
    ReadyArmor("readyArmor"),
    Redraw("redraw"),
    Journal("journal"),
    Help("help"),
    Hunt("hunt"),
    ViewParty("viewParty"),
    RemoveArmor("removeArmor"),
    RemoveWeapon("removeWeapon"),
    Enter("enter"),
    Drop("drop"),
    Camp("camp"),
    CastSpell("castSpell"),
    Quaff("quaff"),
    Quit("quit"),
    ForceCave("forceCave"),
    ForceEncounter("forceEncounter"),
    ShowMap("showMap"),
    Yes("yes"),
    No("no"),
    Descend("descend"),
    Ascend("ascend"),
    Take("take"),
    TeleportOut("teleportOut"),
    GiveItem("giveItem"),
    LeaveTown("leave_town"),
    SpeakChitchat("speak_chitchat"),
    SpeakQuestion("speak_question"),
    SpeakRumor("speak_rumor"),
    SpeakBye("speak_bye"),
    QuestionLodging("question_lodging"),
    QuestionMagic("question_magic"),
    QuestionPeople("question_people"),
    QuestionNothing("question_nothing"),
}
