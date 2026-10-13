/**
 * Town errands for the autopilot.
 *
 * A town visit works through a queue of errands, such as buying torches or
 * talking to a quest character. Each screen is recognized by the choices it
 * offers, so the visit picks options by what they say ("Heal person",
 * "Torch 1 au", "Garth, Merchant") rather than by remembered keys.
 */

package rylween.runtime

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
            AutopilotErrand.Lodge -> inn(menu, text, lodge = true, talk = null)
            is AutopilotErrand.Talk -> if (errand.tavern) tavern(errand, menu, text) else inn(menu, text, lodge = false, talk = errand)
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
            menu.find("None") != null && menu.findItem(errand.label) != null -> menu.findItem(errand.label)?.key
            menu.find("Pay") != null -> menu.find("Pay")?.key
            else -> null
        }

    private fun merchantItem(
        errand: AutopilotErrand.BuyItem,
        menu: AutopilotMenu,
    ): Char? =
        when {
            menu.find("Buy item") != null -> if (!servicePaid) menu.find("Buy item")?.key else leaveService(menu)
            menu.find("None") != null && menu.findItem(errand.label) != null -> menu.findItem(errand.label)?.key
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
            menu.find("Decline") != null -> menu.find("Decline")?.key
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
            menu.find("None") != null -> {
                servicePaid = true
                menu.find("None")?.key
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
            menu.find("Decline") != null -> menu.find("Decline")?.key
            sellOption != null && menu.find("Leave") != null -> if (pending.isNotEmpty()) sellOption.key else leaveService(menu)
            text.contains("Sell what?") -> {
                sold++
                pending.firstOrNull()?.let { name -> menu.findInventoryItem(name) }?.key ?: '\n'
            }
            menu.find("Yes") != null && menu.find("No") != null -> menu.find("Yes")?.key
            else -> null
        }
    }

    private fun inn(
        menu: AutopilotMenu,
        text: String,
        lodge: Boolean,
        talk: AutopilotErrand.Talk?,
    ): Char? =
        when {
            text.contains("supper") || text.contains("Anything to drink?") -> 'N'
            text.contains("of lodging") -> if (lodge) 'Y' else 'N'
            menu.find("Take it") != null -> {
                current = null
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
            menu.find("Bye") != null -> menu.find("Bye")?.key
            !menu.isEmpty() -> talk.answers.firstNotNullOfOrNull { answer -> menu.find(answer) }?.key ?: menu.options.first().key
            else -> null
        }
}
