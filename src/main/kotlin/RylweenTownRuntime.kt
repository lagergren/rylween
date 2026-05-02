// Top-level town session: filters the C/S/I/M/T/L menu to currently-available
// services, applies opening hours, routes shops/native services, and runs
// the town-leave equipment prompts.

enum class TownSessionResultStatus {
    EnteredShop,
    EnteredNativeService,
    ServiceNotOffered,
    ServiceClosed,
    MissingService,
    ShopFinished,
    NativeServiceFinished,
}

data class TownSessionResult(
    val status: TownSessionResultStatus,
    val party: PartyState,
    val building: BuildingId? = null,
)

object TownPromptText {
    fun menu(town: TownModel): TextBlock =
        inlineText(town.name, "")

    fun unavailable(building: BuildingId): TextBlock =
        inlineText("${building.slug} is not available here.")

    fun missingService(building: BuildingId): TextBlock =
        inlineText("${building.slug} service is not implemented yet.")

    fun closed(building: BuildingId): TextBlock =
        inlineText("${building.slug} is closed.")
}

sealed interface TownLeaveEquipmentStep {
    data class ReadyQuestion(val command: EquipmentCommand, val prompt: TextBlock) : TownLeaveEquipmentStep
    data class Equipment(val session: EquipmentPromptSession) : TownLeaveEquipmentStep
    data class Finished(val party: PartyState) : TownLeaveEquipmentStep
}

data class TownLeaveEquipmentSession(
    val party: PartyState,
    val step: TownLeaveEquipmentStep,
    val itemRecords: List<ItemRecordModel> = emptyList(),
    val phase: Int = 0,
    val lastResult: EquipmentActionResult? = null,
) {
    fun apply(input: TownLeaveEquipmentInput): TownLeaveEquipmentSession =
        when (val current = step) {
            is TownLeaveEquipmentStep.ReadyQuestion -> {
                val answer = (input as? TownLeaveEquipmentInput.ReadyAnswer)
                    ?: error("town-leave equipment session is asking a ready question")
                if (answer.yes) {
                    copy(step = TownLeaveEquipmentStep.Equipment(EquipmentPromptSession.start(current.command, party, itemRecords)))
                } else {
                    advance(party, lastResult = null)
                }
            }
            is TownLeaveEquipmentStep.Equipment -> {
                val equipment = (input as? TownLeaveEquipmentInput.Equipment)
                    ?: error("town-leave equipment session is running equipment prompts")
                val updated = current.session.apply(equipment.input)
                when (val finished = updated.step) {
                    is EquipmentPromptStep.Finished ->
                        advance(updated.party, finished.result)
                    else -> copy(step = TownLeaveEquipmentStep.Equipment(updated), party = updated.party)
                }
            }
            is TownLeaveEquipmentStep.Finished ->
                error("town-leave equipment session is already finished: $current")
        }

    private fun advance(nextParty: PartyState, lastResult: EquipmentActionResult?): TownLeaveEquipmentSession {
        val next = nextStep(nextParty, phase + 1)
        return copy(
            party = nextParty,
            phase = next.phase,
            lastResult = lastResult,
            step = next.step,
        )
    }

    private fun nextStep(nextParty: PartyState, nextPhase: Int): NextTownLeaveStep =
        readyCommands
            .withIndex()
            .drop(nextPhase)
            .firstOrNull { (_, command) -> nextParty.needsTownLeaveReadyPrompt(command, itemRecords) }
            ?.let { (index, command) ->
                NextTownLeaveStep(index, TownLeaveEquipmentStep.ReadyQuestion(command, command.townLeavePrompt()))
            }
            ?: NextTownLeaveStep(readyCommands.size, TownLeaveEquipmentStep.Finished(nextParty))

    private data class NextTownLeaveStep(val phase: Int, val step: TownLeaveEquipmentStep)

    companion object {
        private val readyCommands = listOf(EquipmentCommand.ReadyWeapon, EquipmentCommand.ReadyArmor)

        fun start(
            party: PartyState,
            itemRecords: List<ItemRecordModel> = emptyList(),
        ): TownLeaveEquipmentSession {
            val first = readyCommands
                .withIndex()
                .firstOrNull { (_, command) -> party.needsTownLeaveReadyPrompt(command, itemRecords) }
            return TownLeaveEquipmentSession(
                party = party,
                phase = first?.index ?: readyCommands.size,
                itemRecords = itemRecords,
                step = first?.value?.let { TownLeaveEquipmentStep.ReadyQuestion(it, it.townLeavePrompt()) }
                    ?: TownLeaveEquipmentStep.Finished(party),
            )
        }
    }
}

sealed interface TownSessionStep {
    data class Menu(
        val town: TownModel,
        val actions: List<TownMenuActionModel>,
    ) : TownSessionStep
    data class Notice(
        val text: TextBlock,
        val next: Menu,
    ) : TownSessionStep
    data class Shop(
        val service: BuildingServiceModel,
        val session: ShopServiceSession,
    ) : TownSessionStep
    data class NativeService(
        val service: BuildingServiceModel,
        val scene: SceneModel,
        val continuation: TownServiceContinuation? = null,
    ) : TownSessionStep
    data class LeaveEquipment(
        val session: TownLeaveEquipmentSession,
    ) : TownSessionStep
    data class Finished(
        val runtime: RuntimeSessionState,
    ) : TownSessionStep {
        val party: PartyState get() = runtime.activeParty
    }
}

data class TownSession(
    val town: TownModel,
    val townSystem: TownSystemModel,
    val runtime: RuntimeSessionState,
    val shopServices: Map<BuildingId, ShopServiceModel>,
    val serviceScenes: Map<SceneId, SceneModel>,
    val itemRecords: List<ItemRecordModel> = emptyList(),
    val step: TownSessionStep = TownSessionStep.Menu(town, availableTownActions(town, townSystem)),
    val lastResult: TownSessionResult? = null,
) {
    val realm: RealmState get() = runtime.realm
    val party: PartyState get() = runtime.activeParty
    val currentHour: Int get() = runtime.realm.hour

    fun apply(input: TownSessionInput): TownSession =
        when (input) {
            is TownSessionInput.MenuKey -> chooseMenu(input.key)
            is TownSessionInput.Shop -> applyShop(input.input)
            is TownSessionInput.Equipment -> applyEquipment(input.input)
            is TownSessionInput.FinishNativeService -> finishNative(input.runtime)
        }

    fun continueNotice(): TownSession {
        val current = step as? TownSessionStep.Notice
            ?: error("town session is not showing a notice")
        return copy(step = current.next)
    }

    fun replaceShopSession(session: ShopServiceSession): TownSession {
        val current = step as? TownSessionStep.Shop
            ?: error("town session is not running a shop")
        return if (session.step is ShopServiceStep.Finished) {
            finishShop(session, current.service.building)
        } else {
            copy(runtime = runtime.withActiveParty(session.party), step = current.copy(session = session))
        }
    }

    fun replaceLeaveEquipmentSession(session: TownLeaveEquipmentSession): TownSession {
        require(step is TownSessionStep.LeaveEquipment) { "town session is not running leave equipment prompts" }
        return if (session.step is TownLeaveEquipmentStep.Finished) {
            val nextRuntime = runtime.withActiveParty(session.party)
            copy(runtime = nextRuntime, step = TownSessionStep.Finished(nextRuntime))
        } else {
            copy(runtime = runtime.withActiveParty(session.party), step = TownSessionStep.LeaveEquipment(session))
        }
    }

    fun replaceNativeServiceContinuation(continuation: TownServiceContinuation): TownSession {
        val current = step as? TownSessionStep.NativeService
            ?: error("town session is not running a native service")
        return copy(
            runtime = continuation.runtime,
            step = current.copy(continuation = continuation),
        )
    }

    private fun chooseMenu(key: Char): TownSession {
        val action = townSystem.menu.firstOrNull { it.key == key.uppercaseChar() }
            ?: return copy(lastResult = null)
        if (action.endsMenu) {
            val equipment = TownLeaveEquipmentSession.start(party, itemRecords)
            return if (equipment.step is TownLeaveEquipmentStep.Finished) {
                copy(step = TownSessionStep.Finished(runtime))
            } else {
                copy(step = TownSessionStep.LeaveEquipment(equipment), lastResult = null)
            }
        }

        val building = checkNotNull(action.building) { "town service action ${action.action.slug} has no building" }
        if (!town.offers(building)) {
            return notice(
                TownPromptText.unavailable(building),
                TownSessionResult(TownSessionResultStatus.ServiceNotOffered, party, building),
            )
        }

        val service = townSystem.buildingServices.firstOrNull { it.building == building }
            ?: return notice(
                TownPromptText.missingService(building),
                TownSessionResult(TownSessionResultStatus.MissingService, party, building),
            )
        val scene = serviceScenes[service.scene]
            ?: return notice(
                TownPromptText.missingService(building),
                TownSessionResult(TownSessionResultStatus.MissingService, party, building),
            )
        val shop = shopServices[building]
        if (!service.isOpenAt(currentHour)) {
            return notice(
                shop?.closed ?: TownPromptText.closed(building),
                TownSessionResult(TownSessionResultStatus.ServiceClosed, party, building),
            )
        }

        return if (shop != null) {
            copy(
                step = TownSessionStep.Shop(service, ShopServiceSession.start(shop, party, itemRecords)),
                lastResult = TownSessionResult(TownSessionResultStatus.EnteredShop, party, building),
            )
        } else {
            copy(
                step = TownSessionStep.NativeService(service, scene),
                lastResult = TownSessionResult(TownSessionResultStatus.EnteredNativeService, party, building),
            )
        }
    }

    private fun applyShop(input: ShopServiceInput): TownSession {
        val current = step as? TownSessionStep.Shop
            ?: error("town session is not running a shop")
        return replaceShopSession(applyShopInput(current.session, input))
    }

    private fun applyEquipment(input: TownLeaveEquipmentInput): TownSession {
        val current = step as? TownSessionStep.LeaveEquipment
            ?: error("town session is not running leave equipment prompts")
        return replaceLeaveEquipmentSession(current.session.apply(input))
    }

    private fun finishNative(nextRuntime: RuntimeSessionState): TownSession {
        val current = step as? TownSessionStep.NativeService
            ?: error("town session is not running a native service")
        val nextParty = nextRuntime.activeParty
        return copy(
            runtime = nextRuntime,
            step = menuStep(),
            lastResult = TownSessionResult(TownSessionResultStatus.NativeServiceFinished, nextParty, current.service.building),
        )
    }

    private fun finishShop(session: ShopServiceSession, building: BuildingId): TownSession =
        runtime.withActiveParty(session.party).let { nextRuntime ->
            copy(
                runtime = nextRuntime,
                step = menuStep(),
                lastResult = TownSessionResult(TownSessionResultStatus.ShopFinished, session.party, building),
            )
        }

    private fun notice(text: TextBlock, result: TownSessionResult): TownSession =
        copy(step = TownSessionStep.Notice(text, menuStep()), lastResult = result)

    private fun menuStep(): TownSessionStep.Menu =
        TownSessionStep.Menu(town, availableTownActions(town, townSystem))

    companion object {
        fun start(
            town: TownModel,
            townSystem: TownSystemModel,
            scenes: List<SceneModel>,
            runtime: RuntimeSessionState,
            itemRecords: List<ItemRecordModel> = emptyList(),
        ): TownSession =
            TownSession(
                town = town,
                townSystem = townSystem,
                runtime = runtime,
                shopServices = scenes.shopServicesByBuilding(),
                serviceScenes = scenes.associateBy { scene -> scene.id },
                itemRecords = itemRecords,
            )

        fun start(
            town: TownModel,
            townSystem: TownSystemModel,
            scenes: List<SceneModel>,
            party: PartyState,
            currentHour: Int,
            rngState: Long? = null,
            npcs: List<NpcPlacement> = emptyList(),
            itemRecords: List<ItemRecordModel> = emptyList(),
        ): TownSession =
            start(
                town = town,
                townSystem = townSystem,
                scenes = scenes,
                runtime = RuntimeSessionState.singleParty(
                    party = party,
                    hour = currentHour,
                    currentTown = town.id,
                    rngState = rngState,
                    npcs = npcs,
                ),
                itemRecords = itemRecords,
            )
    }
}

private fun availableTownActions(
    town: TownModel,
    townSystem: TownSystemModel,
): List<TownMenuActionModel> =
    townSystem.menu.filter { action -> action.endsMenu || action.building?.let(town::offers) == true }

private fun TownModel.offers(building: BuildingId): Boolean =
    services.any { it.building == building }
