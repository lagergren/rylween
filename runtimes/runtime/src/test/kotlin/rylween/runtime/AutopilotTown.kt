/**
 * Town errands for the autopilot.
 *
 * A town visit works through a queue of errands, such as buying torches or
 * talking to a quest character. Each screen is recognized by the choices it
 * offers, so the visit picks options by what they say ("Heal person",
 * "Torch 1 au", "Garth, Merchant") rather than by remembered keys.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle

/**
 * Whether the town service [service], named as its menu entry such as "Smith", is open at [hour], by its building's
 * authored hours. Services without hours are always open.
 */
internal fun CompiledContentBundle.serviceOpenAt(
    service: String,
    hour: Int,
): Boolean = buildingServices.firstOrNull { building -> slug(building.building) == service.lowercase() }?.isOpenAt(hour) ?: true

/** Something to get done inside a town. */
internal sealed interface AutopilotErrand {
    /** The town menu entry that leads to this errand, such as "Church". */
    val service: String

    data class Heal(
        val member: AutopilotMember,
    ) : AutopilotErrand {
        override val service = "Church"
    }

    data class Raise(
        val member: AutopilotMember,
    ) : AutopilotErrand {
        override val service = "Church"
    }

    data class Energize(
        val member: AutopilotMember,
    ) : AutopilotErrand {
        override val service = "Church"
    }

    /** Buys [quantity] units of the merchant's bulk good labelled [label], such as "Torch". */
    data class BuyBulk(
        val label: String,
        val quantity: Int,
    ) : AutopilotErrand {
        override val service = "Merchant"
    }

    /** Buys one merchant item labelled [label], such as "Tent". */
    data class BuyItem(
        val label: String,
    ) : AutopilotErrand {
        override val service = "Merchant"
    }

    /** Buys the smith's [gear] labelled [label], such as "Sword". */
    data class BuyGear(
        val gear: AutopilotGear,
        val label: String,
    ) : AutopilotErrand {
        override val service = "Smith"
    }

    /** Sells the inventory items named [items] at [shop] ("Smith" for weapons and armor, "Merchant" for the rest). */
    data class Sell(
        val shop: String,
        val items: List<String>,
    ) : AutopilotErrand {
        override val service = shop
    }

    /** Takes a room at the inn: rested again, and some health back. */
    data object Lodge : AutopilotErrand {
        override val service = "Inn"
    }

    /** Tries a town pastime the run has not tried yet, such as a drink at the tavern, at the [service] that offers it. */
    data class Pastime(
        val pastime: String,
        override val service: String,
    ) : AutopilotErrand

    /**
     * Talks to [npc] in the inn or tavern, answering the conversation's questions with [answers]: the first
     * offered option whose label contains one of them, in order of preference.
     */
    data class Talk(
        val npc: String,
        val tavern: Boolean,
        val answers: List<String> = listOf("Yes", "Accept", "Buy"),
    ) : AutopilotErrand {
        override val service = if (tavern) "Tavern" else "Inn"
    }
}

/**
 * Drives one stay in a town: picks each errand's service from the town menu, answers that service's screens,
 * and leaves when the queue is empty, readying gear on the way out when the plan asks for it.
 */
internal class AutopilotTownVisit(
    errands: List<AutopilotErrand>,
    /** The member to arm, or armor, at the "ready any weapons/armors before leaving?" prompts; `null` declines. */
    private val readyOnLeave: (AutopilotGear) -> Pair<AutopilotMember, String>?,
    /** What the run has used, so each pastime is tried once and counted. */
    private val coverage: AutopilotCoverage,
    private val party: AutopilotParty,
) {
    private val queue = ArrayDeque(errands)
    private var current: AutopilotErrand? = null
    private var bulkLeft = 0
    private var gearMoreTried = false
    private var readyPending: Pair<AutopilotMember, String>? = null

    /** Errands left, including the one in progress. */
    val remaining: Int get() = queue.size + if (current == null) 0 else 1

    /** Whether the visit has left the town menu for good; prompts it no longer recognizes then belong to the wilderness. */
    var left: Boolean = false
        private set

    fun respond(view: AutopilotView): Char? {
        val menu = AutopilotMenu.parse(view.text)
        val text = view.text.joinToString(" ")
        return when {
            // A full inventory offers to drop something for the new item; RETURN declines and keeps the inventory.
            text.contains("Drop an item?") -> '\n'
            menu.find("Pay to Enter") != null -> menu.find("Pay to Enter")?.key
            text.contains("ready any weapons") -> readyAnswer(AutopilotGear.Weapon)
            text.contains("ready any armors") -> readyAnswer(AutopilotGear.Armor)
            text.contains("Ready what?") -> readyPending?.second?.let { name -> menu.findItem(name)?.key } ?: '\n'
            readyPending != null && text.contains("Who?") -> readyPending?.first?.digit
            menu.find("Merchant") != null && menu.find("Leave") != null && menu.find("Inn") != null -> townMenu(menu)
            else -> current?.let { errand -> service(errand, view, menu, text) }
        }
    }

    private fun townMenu(menu: AutopilotMenu): Char? {
        current = null
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            val option = menu.find(next.service) ?: continue
            current = next
            servicePaid = false
            sold = 0
            bulkLeft = (next as? AutopilotErrand.BuyBulk)?.quantity ?: 0
            gearMoreTried = false
            return option.key
        }
        left = true
        return menu.find("Leave")?.key
    }

    private fun readyAnswer(gear: AutopilotGear): Char {
        readyPending = readyOnLeave(gear)?.takeIf { pair -> readied.add(pair.first.number to pair.second) }
        return if (readyPending != null) 'Y' else 'N'
    }

    private val readied = mutableSetOf<Pair<Int, String>>()

    private fun service(
        errand: AutopilotErrand,
        view: AutopilotView,
        menu: AutopilotMenu,
        text: String,
    ): Char? =
        when (errand) {
            is AutopilotErrand.Heal -> church(menu, text, "Heal person", errand.member)
            is AutopilotErrand.Raise -> church(menu, text, "Raise dead", errand.member)
            is AutopilotErrand.Energize -> church(menu, text, "Energize", errand.member)
            is AutopilotErrand.BuyBulk -> merchantBulk(errand, view, menu)
            is AutopilotErrand.BuyItem -> merchantItem(errand, menu)
            is AutopilotErrand.BuyGear -> smith(errand, menu, text)
            is AutopilotErrand.Sell -> sell(errand, menu, text)
            AutopilotErrand.Lodge -> inn(view, menu, text, lodge = true, talk = null)
            is AutopilotErrand.Talk -> if (errand.tavern) tavern(errand, menu, text) else inn(view, menu, text, lodge = false, talk = errand)
            is AutopilotErrand.Pastime -> pastime(errand, view, menu, text)
        }

    private fun pending(pastime: String): Boolean = !coverage.used(AutopilotCoverage.PASTIME, pastime)

    private fun tried(pastime: String) = coverage.record(AutopilotCoverage.PASTIME, pastime)

    /** The smith's beer: accepted the first time, so the run hears his news once, and declined after that. */
    private fun beer(menu: AutopilotMenu): Char? =
        if (pending(SMITH_BEER)) {
            tried(SMITH_BEER)
            menu.find("Accept")?.key
        } else {
            menu.find("Decline")?.key
        }

    /**
     * Pastime screens. The tavern's drink, story, and rumors are one key each from its menu; the barmaid is a guest
     * to speak with, whose lodging question leads upstairs; the guild only says it is closed; the inn's supper and
     * drink are answered in [inn].
     */
    private fun pastime(
        errand: AutopilotErrand.Pastime,
        view: AutopilotView,
        menu: AutopilotMenu,
        text: String,
    ): Char? {
        val chosen = errand.pastime
        return when {
            errand.service == "Inn" -> inn(view, menu, text, lodge = false, talk = null)
            errand.service == "Guild" -> {
                tried(chosen)
                leaveService(menu)
            }
            menu.find("Buy it") != null -> menu.find("No thanks")?.key
            menu.find("Order drink") != null ->
                if (servicePaid) {
                    leaveService(menu)
                } else {
                    when (chosen) {
                        TAVERN_DRINK -> menu.find("Order drink")?.key
                        TAVERN_STORY -> menu.find("Tell story")?.key
                        TAVERN_RUMORS -> menu.find("Rumors")?.key.also { servicePaid = true }.also { tried(chosen) }
                        else -> menu.find("Speak")?.key
                    }
                }
            text.contains("Order:") -> menu.find("Ale")?.key.also { servicePaid = true }.also { tried(chosen) }
            text.contains("tell a story") -> party.living(view.state.party).firstOrNull()?.digit
            menu.find("True story") != null -> menu.find("True story")?.key.also { servicePaid = true }.also { tried(chosen) }
            text.contains("Speak with whom?") -> menu.options.firstOrNull { option -> option.label.endsWith(BARGIRL, ignoreCase = true) }?.key
            text.contains("upstairs for") -> 'Y'.also { servicePaid = true }.also { tried(chosen) }
            menu.find("Lodging") != null -> menu.find("Lodging")?.key
            menu.find("Bye") != null -> if (servicePaid) menu.find("Bye")?.key else menu.find("Question")?.key
            else -> null
        }
    }


    /** Church screens: pick the service, the member, pay, then leave once the service is done or refused. */
    private fun church(
        menu: AutopilotMenu,
        text: String,
        option: String,
        member: AutopilotMember,
    ): Char? {
        if (churchRefusals.any { refusal -> refusal in text }) servicePaid = true
        return when {
            menu.find(option) != null && current != null && !servicePaid -> menu.find(option)?.key
            menu.find("Heal person") != null -> leaveService(menu)
            text.contains("Who?") -> member.digit
            menu.find("Pay") != null -> {
                servicePaid = true
                menu.find("Pay")?.key
            }
            else -> null
        }
    }

    private var servicePaid = false

    /** Church answers that end a service without payment. */
    private val churchRefusals =
        listOf("Not a spellcaster", "needs no", "seems very alive", "don't have", "no spell points to be restored")

    private fun leaveService(menu: AutopilotMenu): Char? {
        servicePaid = false
        current = null
        return menu.find("Leave")?.key
    }

    private fun merchantBulk(
        errand: AutopilotErrand.BuyBulk,
        view: AutopilotView,
        menu: AutopilotMenu,
    ): Char? =
        when {
            view.request == AutopilotRequest.Number -> {
                val units = bulkLeft.coerceIn(0, 9)
                bulkLeft -= units
                units.digitToChar()
            }
            menu.find("Buy item") != null -> if (bulkLeft > 0) menu.find("Buy item")?.key else leaveService(menu)
            menu.find("Leave") != null && menu.findItem(errand.label) != null -> menu.findItem(errand.label)?.key
            menu.find("Pay") != null -> menu.find("Pay")?.key
            else -> null
        }

    private fun merchantItem(
        errand: AutopilotErrand.BuyItem,
        menu: AutopilotMenu,
    ): Char? =
        when {
            menu.find("Buy item") != null -> if (!servicePaid) menu.find("Buy item")?.key else leaveService(menu)
            menu.find("Leave") != null && menu.findItem(errand.label) != null -> menu.findItem(errand.label)?.key
            menu.find("Pay") != null -> {
                servicePaid = true
                menu.find("Pay")?.key
            }
            else -> null
        }

    private fun smith(
        errand: AutopilotErrand.BuyGear,
        menu: AutopilotMenu,
        text: String,
    ): Char? =
        when {
            menu.find("Decline") != null -> beer(menu)
            menu.find("Buy") != null && menu.find("Sell") != null -> if (!servicePaid) menu.find("Buy")?.key else leaveService(menu)
            text.contains("Armor or Weapon?") -> menu.find(if (errand.gear == AutopilotGear.Weapon) "Weapon" else "Armor")?.key
            menu.find("Pay") != null -> {
                servicePaid = true
                menu.find("Pay")?.key
            }
            menu.findItem(errand.label) != null -> menu.findItem(errand.label)?.key
            menu.find("More") != null && !gearMoreTried -> {
                gearMoreTried = true
                menu.find("More")?.key
            }
            menu.find("Leave") != null -> {
                servicePaid = true
                menu.find("Leave")?.key
            }
            else -> null
        }

    private var sold = 0

    /** Sells [errand]'s items one at a time: Sell, pick the item, accept the offer, until all are gone. */
    private fun sell(
        errand: AutopilotErrand.Sell,
        menu: AutopilotMenu,
        text: String,
    ): Char? {
        val pending = errand.items.drop(sold)
        val sellOption = menu.find("Sell item") ?: menu.find("Sell")
        return when {
            menu.find("Decline") != null -> beer(menu)
            sellOption != null && menu.find("Leave") != null -> if (pending.isNotEmpty()) sellOption.key else leaveService(menu)
            text.contains("Sell what?") -> {
                sold++
                pending.firstOrNull()?.let { name -> menu.findInventoryItem(name) }?.key ?: '\n'
            }
            // Haggling once tries the smith's patience; the offer comes back, higher or the same, or he says no thanks.
            menu.find("Haggle") != null && pending(SMITH_HAGGLE) -> menu.find("Haggle")?.key.also { tried(SMITH_HAGGLE) }
            menu.find("Yes") != null && menu.find("No") != null -> menu.find("Yes")?.key
            else -> null
        }
    }

    /** Inn screens: supper and a drink once each while the party can pay, a room when [lodge], and [talk]'s guest. */
    private fun inn(
        view: AutopilotView,
        menu: AutopilotMenu,
        text: String,
        lodge: Boolean,
        talk: AutopilotErrand.Talk?,
    ): Char? {
        val level = view.state.party.members.firstOrNull()?.level ?: 1
        val gold = view.state.party.resources.gold
        return when {
            text.contains("supper") ->
                if (pending(INN_SUPPER) && gold >= level * level * INN_PRICE_PER_LEVEL_SQUARED + PASTIME_GOLD) 'Y'.also { tried(INN_SUPPER) } else 'N'
            text.contains("Anything to drink?") -> if (pending(INN_DRINK) && gold >= PASTIME_GOLD) 'Y' else 'N'
            text.contains("What'll it be?") -> menu.find("Ale")?.key.also { tried(INN_DRINK) }
            text.contains("of lodging") -> if (lodge) 'Y' else 'N'
            menu.find("Take it") != null -> {
                current = null
                tried(INN_LODGING)
                menu.find("Take it")?.key
            }
            menu.find("Chitchat with guests") != null ->
                if (talk != null && !servicePaid) {
                    menu.find("Chitchat with guests")?.key
                } else {
                    leaveService(menu)
                }
            talk != null -> conversation(talk, menu, text)
            else -> null
        }
    }

    /**
     * Tavern screens: a stranger's offer to sell something is declined until the party has picked [talk]'s character,
     * whose own offer, such as a map, the conversation answers.
     */
    private fun tavern(
        talk: AutopilotErrand.Talk,
        menu: AutopilotMenu,
        text: String,
    ): Char? =
        when {
            menu.find("Buy it") != null && !servicePaid -> menu.find("No thanks")?.key
            menu.find("Order drink") != null -> if (!servicePaid) menu.find("Speak")?.key else leaveService(menu)
            else -> conversation(talk, menu, text)
        }

    /**
     * The conversation menu's options the run has not tried yet, once each: chitchat, each question topic, and
     * rumors, before saying goodbye.
     */
    private fun smallTalk(menu: AutopilotMenu): Char? {
        val speak = "speak"
        val questions = "speak.questions"
        val topics = listOf("question_lodging" to "Lodging", "question_magic" to "Magic", "question_people" to "People", "question_nothing" to "Nothing")
        if (menu.find("Lodging") != null) {
            return topics.firstOrNull { (action, _) -> !coverage.used(questions, action) }?.let { (_, label) -> menu.find(label)?.key }
                ?: menu.find("Nothing")?.key
        }
        return when {
            !coverage.used(speak, "speak_chitchat") -> menu.find("Chitchat")?.key
            topics.any { (action, _) -> !coverage.used(questions, action) } -> menu.find("Question")?.key
            !coverage.used(speak, "speak_rumor") -> menu.find("Rumors")?.key
            else -> null
        }
    }

    /** The speak flow: pick [talk]'s character, answer the dialogue, then say goodbye. */
    private fun conversation(
        talk: AutopilotErrand.Talk,
        menu: AutopilotMenu,
        text: String,
    ): Char? =
        when {
            text.contains("Speak with whom?") -> {
                servicePaid = true
                menu.options.firstOrNull { option -> option.label.substringBefore(',') == talk.npc }?.key
            }
            menu.find("Bye") != null -> smallTalk(menu) ?: menu.find("Bye")?.key
            menu.find("Nothing") != null && menu.find("Lodging") != null -> smallTalk(menu)
            !menu.isEmpty() -> talk.answers.firstNotNullOfOrNull { answer -> menu.find(answer) }?.key ?: menu.options.first().key
            else -> null
        }

    private companion object {
        const val TAVERN_DRINK = "tavern drink"
        const val TAVERN_STORY = "tavern story"
        const val TAVERN_RUMORS = "tavern rumors"
        const val INN_SUPPER = "inn supper"
        const val INN_DRINK = "inn drink"
        const val INN_LODGING = "inn lodging"
        const val SMITH_BEER = "smith beer"
        const val SMITH_HAGGLE = "smith haggle"
        const val BARGIRL = "Bargirl"
        const val INN_PRICE_PER_LEVEL_SQUARED = 15

        /** Gold a pastime may not cut into: the party never spends its last coins on pleasure. */
        const val PASTIME_GOLD = 300
    }
}
