// Top-level town session: filters the C/S/I/M/T/L menu to currently-available
// services, applies opening hours, routes shops/native services, and runs
// the town-leave equipment prompts.

package rylween.runtime

import rylween.dsl.*

enum class TownSessionResultStatus {
    EntryTaxPaid,
    EntryTaxDeclined,
    EnteredShop,
    EnteredNativeService,
    ServiceNotOffered,
    ServiceClosed,
    ShopFinished,
    NativeServiceFinished,
}

data class TownSessionResult(
    val status: TownSessionResultStatus,
    val party: PartyState,
    val building: BuildingId? = null,
)

sealed interface TownLeaveEquipmentStep {
    data class ReadyQuestion(val command: EquipmentCommand, val prompt: TextBlock) : TownLeaveEquipmentStep
    data class Equipment(val session: EquipmentPromptSession) : TownLeaveEquipmentStep
    data class Finished(val party: PartyState) : TownLeaveEquipmentStep
}

data class TownLeaveEquipmentSession(
    val party: PartyState,
    val step: TownLeaveEquipmentStep,
    val itemRecords: List<ItemRecordModel> = emptyList(),
    val prompts: EquipmentPromptsModel = EquipmentPromptsModel(),
    val readyCommands: List<EquipmentCommand> = emptyList(),
    val phase: Int = 0,
    val lastResult: EquipmentActionResult? = null,
) {
    fun apply(input: TownLeaveEquipmentInput): TownLeaveEquipmentSession =
        when (val current = step) {
            is TownLeaveEquipmentStep.ReadyQuestion -> {
                val answer = (input as? TownLeaveEquipmentInput.ReadyAnswer)
                    ?: error("town-leave equipment session is asking a ready question")
                if (answer.yes) {
                    copy(
                        step = TownLeaveEquipmentStep.Equipment(
                            EquipmentPromptSession.start(current.command, party, itemRecords, prompts),
                        ),
                    )
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
                NextTownLeaveStep(index, TownLeaveEquipmentStep.ReadyQuestion(command, command.townLeavePrompt(prompts)))
            }
            ?: NextTownLeaveStep(readyCommands.size, TownLeaveEquipmentStep.Finished(nextParty))

    private data class NextTownLeaveStep(val phase: Int, val step: TownLeaveEquipmentStep)

    companion object {
        fun start(
            party: PartyState,
            itemRecords: List<ItemRecordModel> = emptyList(),
            prompts: EquipmentPromptsModel = EquipmentPromptsModel(),
            readyCommands: List<EquipmentCommand> = emptyList(),
        ): TownLeaveEquipmentSession {
            val first = readyCommands
                .withIndex()
                .firstOrNull { (_, command) -> party.needsTownLeaveReadyPrompt(command, itemRecords) }
            return TownLeaveEquipmentSession(
                party = party,
                phase = first?.index ?: readyCommands.size,
                itemRecords = itemRecords,
                prompts = prompts,
                readyCommands = readyCommands,
                step = first?.value?.let { TownLeaveEquipmentStep.ReadyQuestion(it, it.townLeavePrompt(prompts)) }
                    ?: TownLeaveEquipmentStep.Finished(party),
            )
        }
    }
}

sealed interface TownSessionStep {
    data class EntryTax(
        val prompt: TextBlock,
        val amount: Int,
        val pay: InputBindingModel,
        val leave: InputBindingModel,
    ) : TownSessionStep
    data class EntryTaxThanks(
        val text: TextBlock,
        val next: Menu,
    ) : TownSessionStep
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
        val continuation: ScriptContinuation? = null,
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
    val content: RuntimeContent = RuntimeContent(),
    val step: TownSessionStep = TownSessionStep.Menu(town, availableTownActions(town, townSystem)),
    val lastResult: TownSessionResult? = null,
) {
    val itemRecords: List<ItemRecordModel> get() = content.itemRecords
    val realmContent: RealmContentModel get() = content.realm
    val realm: RealmState get() = runtime.realm
    val party: PartyState get() = runtime.activeParty
    val currentHour: Int get() = runtime.realm.hour

    fun apply(input: TownSessionInput): TownSession =
        when (input) {
            is TownSessionInput.EntryTax -> applyEntryTax(input.pay)
            is TownSessionInput.MenuKey -> chooseMenu(input.key)
            is TownSessionInput.Shop -> applyShop(input.input)
            is TownSessionInput.Equipment -> applyEquipment(input.input)
            is TownSessionInput.FinishNativeService -> finishNative(input.runtime)
        }

    fun continueEntryTaxThanks(): TownSession {
        val current = step as? TownSessionStep.EntryTaxThanks
            ?: error("town session is not showing the entry-tax thanks prompt")
        return copy(step = current.next)
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

    fun replaceNativeServiceContinuation(continuation: ScriptContinuation): TownSession {
        val current = step as? TownSessionStep.NativeService
            ?: error("town session is not running a native service")
        return copy(
            runtime = continuation.runtime,
            step = current.copy(continuation = continuation),
        )
    }

    private fun applyEntryTax(pay: Boolean): TownSession {
        val current = step as? TownSessionStep.EntryTax
            ?: error("town session is not asking for entry tax")
        if (!pay) {
            return copy(
                step = TownSessionStep.Finished(runtime),
                lastResult = TownSessionResult(TownSessionResultStatus.EntryTaxDeclined, party),
            )
        }

        val nextRuntime = runtime.withActiveParty(party.withGold(party.resources.gold - current.amount))
        return copy(
            runtime = nextRuntime,
            step = TownSessionStep.EntryTaxThanks(content.prompts.town.entryTaxThanks, menuStep()),
            lastResult = TownSessionResult(TownSessionResultStatus.EntryTaxPaid, nextRuntime.activeParty),
        )
    }

    private fun chooseMenu(key: Char): TownSession {
        val action = townSystem.menu.firstOrNull { it.key == key.uppercaseChar() }
            ?: return copy(lastResult = null)
        if (action.endsMenu) {
            val equipment = TownLeaveEquipmentSession.start(
                party = party,
                itemRecords = itemRecords,
                prompts = content.prompts.equipment,
                readyCommands = townSystem.leavePrompts,
            )
            return if (equipment.step is TownLeaveEquipmentStep.Finished) {
                copy(step = TownSessionStep.Finished(runtime))
            } else {
                copy(step = TownSessionStep.LeaveEquipment(equipment), lastResult = null)
            }
        }

        val building = checkNotNull(action.building) { "town service action ${action.action.slug} has no building" }
        if (!town.offers(building)) {
            return notice(
                content.prompts.town.unavailable(building),
                TownSessionResult(TownSessionResultStatus.ServiceNotOffered, party, building),
            )
        }

        val service = requireNotNull(townSystem.buildingServices.firstOrNull { it.building == building }) {
            "town ${town.id.slug} offers ${building.slug} but no building service is declared"
        }
        val scene = requireNotNull(serviceScenes[service.scene]) {
            "town service ${building.slug} references missing scene ${service.scene.slug}"
        }
        val shop = shopServices[building]
        if (!service.isOpenAt(currentHour)) {
            return notice(
                shop?.closed ?: content.prompts.town.closed(building),
                TownSessionResult(TownSessionResultStatus.ServiceClosed, party, building),
            )
        }

        return if (shop != null) {
            copy(
                step = TownSessionStep.Shop(service, ShopServiceSession.start(shop, party, content)),
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
        copy(
            runtime = runtime.withActiveParty(session.party),
            step = menuStep(),
            lastResult = TownSessionResult(TownSessionResultStatus.ShopFinished, session.party, building),
        )

    private fun notice(text: TextBlock, result: TownSessionResult): TownSession =
        copy(step = TownSessionStep.Notice(text, menuStep()), lastResult = result)

    private fun menuStep(): TownSessionStep.Menu =
        TownSessionStep.Menu(town, availableTownActions(town, townSystem))

    private fun withEntryTaxPromptIfNeeded(): TownSession {
        val tax = townSystem.entryTax
        val leaderLevel = party.members.firstOrNull()?.level ?: 0
        if (leaderLevel < tax.minimumLeaderLevel) return this

        val amount = (party.resources.gold * town.taxPercent) / 100
        return copy(
            step = TownSessionStep.EntryTax(
                prompt = content.prompts.town.renderEntryTaxPrompt(town.name, town.taxPercent, amount),
                amount = amount,
                pay = tax.pay,
                leave = tax.leave,
            ),
        )
    }

    companion object {
        fun start(
            town: TownModel,
            runtime: RuntimeSessionState,
            content: RuntimeContent = RuntimeContent(),
        ): TownSession =
            TownSession(
                town = town,
                townSystem = requireNotNull(content.townSystem) { "town system is not configured" },
                runtime = runtime,
                shopServices = content.scenes.shopServicesByBuilding(),
                serviceScenes = content.scenes.associateBy { scene -> scene.id },
                content = content,
            ).withEntryTaxPromptIfNeeded()

        fun start(
            town: TownModel,
            party: PartyState,
            currentHour: Int,
            content: RuntimeContent = RuntimeContent(),
            rngState: Long? = null,
            npcs: List<NpcPlacement> = emptyList(),
        ): TownSession =
            start(
                town = town,
                runtime = RuntimeSessionState.singleParty(
                    party = party,
                    hour = currentHour,
                    currentTown = town.id,
                    rngState = rngState,
                    npcs = npcs,
                ),
                content = content,
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
            realmContent: RealmContentModel = RealmContentModel(),
        ): TownSession =
            start(
                town = town,
                party = party,
                currentHour = currentHour,
                rngState = rngState,
                npcs = npcs,
                content = RuntimeContent(
                    townSystem = townSystem,
                    scenes = scenes,
                    itemRecords = itemRecords,
                    realm = realmContent,
                ),
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
