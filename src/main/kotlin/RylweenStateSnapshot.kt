fun renderRuntimeStateSnapshot(state: StateModel): String {
    check(state.kind == StateKind.RuntimeSnapshot) {
        "only runtime snapshots can be emitted as exact save DSL"
    }
    return RuntimeStateSnapshotWriter().render(state)
}

fun renderRuntimeStateSnapshots(states: List<StateModel>): String =
    states
        .filter { it.kind == StateKind.RuntimeSnapshot }
        .joinToString(separator = "\n") { renderRuntimeStateSnapshot(it) }

private class RuntimeStateSnapshotWriter {
    private val out = StringBuilder()
    private var indent = 0

    fun render(state: StateModel): String {
        line("runtimeState(${stateId(state.id)}) {")
        indented {
            realm(state.realm)
            locations(state.locations)
            npcs(state.npcs)
            state.parties.forEach(::party)
        }
        line("}")
        return out.toString()
    }

    private fun realm(realm: RealmState) {
        val rngState = checkNotNull(realm.rngState) {
            "runtime snapshot ${realm.activeParty.slug} requires rngState before emission"
        }
        line("realm {")
        indented {
            assignment("day", realm.day)
            assignment("hour", realm.hour)
            assignment("minute", realm.minute)
            line("rngState = ${rngState}L")
            line("activeParty = ${partyId(realm.activeParty)}")
            line("currentMap = ${worldMapId(realm.currentMap)}")
            line("currentMode = RuntimeMode.${realm.currentMode.name}")
            line("currentTown = ${realm.currentTown?.let(::townId) ?: "null"}")
            assignment("banished", realm.banished)
            assignment("lastPlayedDay", realm.lastPlayedDay)
            assignment("partyMemberMax", realm.partyMemberMax)
        }
        line("}")
    }

    private fun locations(locations: List<LocationPlacement>) {
        line("locations {")
        indented {
            locations.sortedWith(compareBy({ it.location.slug }, { it.point.x }, { it.point.y })).forEach {
                line("${locationId(it.location)} at ${point(it.point)}")
            }
        }
        line("}")
    }

    private fun npcs(npcs: List<NpcPlacement>) {
        line("npcs {")
        indented {
            npcs.sortedBy { it.npc.slug }.forEach {
                line("${npcId(it.npc)} at ${position(it.position)}")
            }
        }
        line("}")
    }

    private fun party(party: PartyState) {
        line("party(${partyId(party.id)}) {")
        indented {
            assignment("player", party.player)
            assignment("name", party.name)
            line("position = ${position(party.position)}")
            line("facing = ${party.facing?.let { "Facing.${it.name}" } ?: "null"}")
            resources(party.resources)
            counters(party.counters)
            flags(party.flags)
            milestones(party.milestones)
            inventory(party.inventory)
            members(party.members)
            visits(party.visitedNpcs)
            journal(party.journal)
        }
        line("}")
    }

    private fun resources(resources: PartyResources) {
        line("resources {")
        indented {
            assignment("gold", resources.gold)
            assignment("torches", resources.torches)
            assignment("food", resources.food)
            assignment("timeLeft", resources.timeLeft)
            assignment("restedHours", resources.restedHours)
        }
        line("}")
    }

    private fun counters(counters: PartyCounters) {
        line("counters {")
        indented {
            line("memberCount = ${counters.memberCount ?: "null"}")
            assignment("kills", counters.kills)
            assignment("waitingMail", counters.waitingMail)
            assignment("attacked", counters.attacked)
            line("attackToday {")
            indented {
                counters.attackedToday.sortedBy { it.party.slug }.forEach {
                    line("party(${partyId(it.party)}, attacked = ${it.attacked})")
                }
            }
            line("}")
        }
        line("}")
    }

    private fun flags(flags: List<FlagState>) {
        line("flags {")
        indented {
            flags.sortedWith(compareBy({ it.flag.slug }, { it.native })).forEach {
                line("mark(runtimeFlagState(${quote(it.flag.slug)}, ${quote(it.native)}))")
            }
        }
        line("}")
    }

    private fun milestones(milestones: List<GameMilestoneId>) {
        line("milestones {")
        indented {
            milestones.sortedBy { it.slug }.forEach {
                line("achieved(${milestoneId(it)})")
            }
        }
        line("}")
    }

    private fun inventory(inventory: List<InventorySlotState>) {
        line("inventory {")
        indented {
            inventory.sortedBy { it.slot }.forEach { slot ->
                val slotExpr = "slot(${slot.slot})"
                line(slot.item?.let { "$slotExpr.item(${itemId(it)})" } ?: "$slotExpr.empty()")
            }
        }
        line("}")
    }

    private fun members(members: List<PartyMemberState>) {
        line("members {")
        indented {
            members.forEach(::member)
        }
        line("}")
    }

    private fun member(member: PartyMemberState) {
        line("member(${quote(member.name)}) {")
        indented {
            line("gender = Gender.${member.gender.name}")
            line("race = Race.${member.race.name}")
            line("klass = PcClass.${member.pcClass.name}")
            assignment("level", member.level)
            assignment("health", member.health)
            line("hitPoints = ${member.hitPoints.current} of ${member.hitPoints.max}")
            assignment("weaponSkill", member.weaponSkill)
            assignment("wisdom", member.wisdom)
            line("mana = ${member.mana.current} manaOf ${member.mana.max}")
            assignment("charisma", member.charisma)
            equipment(member.equipment)
            assignment("experience", member.experience)
            line(if (member.dead) "dead()" else "alive()")
        }
        line("}")
    }

    private fun equipment(equipment: EquipmentState) {
        line("equipped {")
        indented {
            line("weapon = ${itemId(equipment.weapon)}")
            line("armor = ${itemId(equipment.armor)}")
        }
        line("}")
    }

    private fun visits(visits: List<NpcVisitState>) {
        line("npcVisits {")
        indented {
            visits.sortedBy { it.npc.slug }.forEach {
                line(if (it.visited) "visited(${npcId(it.npc)})" else "notVisited(${npcId(it.npc)})")
            }
        }
        line("}")
    }

    private fun journal(entries: List<JournalEntryState>) {
        line("journal {")
        indented {
            entries.forEach { entry ->
                when (entry) {
                    is JournalEntryState.Text -> line("entry(${textId(entry.id)})")
                    is JournalEntryState.Line -> line("line(${quote(entry.text)})")
                }
            }
        }
        line("}")
    }

    private fun position(position: WorldPosition): String =
        when (position) {
            is WorldPosition.Wilderness -> "Wilderness.xy(${position.point.x}, ${position.point.y})"
            is WorldPosition.Town -> "townPosition(${townId(position.town)}, ${buildingId(position.building)})"
        }

    private fun point(point: GridPoint): String = "xy(${point.x}, ${point.y})"
    private fun stateId(id: StateId): String = "stateId(${quote(id.slug)})"
    private fun partyId(id: PartyId): String = "partyId(${quote(id.slug)})"
    private fun worldMapId(id: WorldMapId): String = "worldMap(${quote(id.slug)})"
    private fun townId(id: TownId): String = "town(${quote(id.slug)})"
    private fun buildingId(id: BuildingId): String = "building(${quote(id.slug)})"
    private fun locationId(id: LocationId): String = "location(${quote(id.slug)})"
    private fun npcId(id: NpcId): String = "npc(${quote(id.slug)})"
    private fun itemId(id: ItemId): String = "item(${quote(id.slug)})"
    private fun milestoneId(id: GameMilestoneId): String = "milestone(${quote(id.slug)})"
    private fun textId(id: TextId): String = "text(${quote(id.id)})"

    private fun assignment(name: String, value: Int) = line("$name = $value")
    private fun assignment(name: String, value: Boolean) = line("$name = $value")
    private fun assignment(name: String, value: String) = line("$name = ${quote(value)}")

    private fun indented(body: () -> Unit) {
        indent += 1
        body()
        indent -= 1
    }

    private fun line(text: String) {
        out.append("    ".repeat(indent)).appendLine(text)
    }

    private fun quote(value: String): String =
        buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
}
