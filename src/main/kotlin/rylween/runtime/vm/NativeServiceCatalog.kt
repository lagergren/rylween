package rylween.runtime.vm

import rylween.dsl.NativeAllowlist

enum class NativeServiceKind {
    Effect,
    IntValue,
    StringValue,
}

enum class NativeServiceArgSlot {
    StringValue,
    IntExprValue,
}

data class NativeServiceDescriptor(
    val path: String,
    val kind: NativeServiceKind,
    val argSlots: List<NativeServiceArgSlot>,
    val reason: String,
) {
    val arity: Int get() = argSlots.size
}

data class NativeServiceCatalog(
    val entries: Map<String, NativeServiceDescriptor>,
) {
    init {
        require(entries.keys.all { it.isNotBlank() }) { "native service paths must be non-blank" }
        require(entries.all { (path, descriptor) -> path == descriptor.path }) {
            "native service catalog keys must match descriptor paths"
        }
    }

    fun descriptor(path: String): NativeServiceDescriptor? = entries[path]

    companion object {
        val default: NativeServiceCatalog =
            NativeServiceCatalog(
                listOf(
                    effect(
                        path = NativeAllowlist.inventoryAdd.path,
                        argSlots = listOf(NativeServiceArgSlot.StringValue),
                        reason = NativeAllowlist.inventoryAdd.reason,
                    ),
                    effect(
                        path = NativeAllowlist.townEnterService.path,
                        argSlots = listOf(NativeServiceArgSlot.StringValue),
                        reason = NativeAllowlist.townEnterService.reason,
                    ),
                    intValue(
                        path = NativeAllowlist.inputKeyUpper.path,
                        argSlots = emptyList(),
                        reason = NativeAllowlist.inputKeyUpper.reason,
                    ),
                    stringValue(
                        path = NativeAllowlist.partyMemberName.path,
                        argSlots = listOf(NativeServiceArgSlot.IntExprValue),
                        reason = NativeAllowlist.partyMemberName.reason,
                    ),
                    stringValue(
                        path = NativeAllowlist.combatMonsterName.path,
                        argSlots = listOf(NativeServiceArgSlot.IntExprValue),
                        reason = NativeAllowlist.combatMonsterName.reason,
                    ),
                    stringValue(
                        path = NativeAllowlist.partyPronounSubject.path,
                        argSlots = listOf(NativeServiceArgSlot.IntExprValue),
                        reason = NativeAllowlist.partyPronounSubject.reason,
                    ),
                    stringValue(
                        path = NativeAllowlist.partyPronounObject.path,
                        argSlots = listOf(NativeServiceArgSlot.IntExprValue),
                        reason = NativeAllowlist.partyPronounObject.reason,
                    ),
                    stringValue(
                        path = NativeAllowlist.partyPronounPossessive.path,
                        argSlots = listOf(NativeServiceArgSlot.IntExprValue),
                        reason = NativeAllowlist.partyPronounPossessive.reason,
                    ),
                    stringValue(
                        path = NativeAllowlist.townNpcName.path,
                        argSlots = listOf(NativeServiceArgSlot.StringValue),
                        reason = NativeAllowlist.townNpcName.reason,
                    ),
                    stringValue(
                        path = NativeAllowlist.npcRoleName.path,
                        argSlots = listOf(NativeServiceArgSlot.StringValue),
                        reason = NativeAllowlist.npcRoleName.reason,
                    ),
                    stringValue(
                        path = NativeAllowlist.npcRoleLocation.path,
                        argSlots = listOf(NativeServiceArgSlot.StringValue),
                        reason = NativeAllowlist.npcRoleLocation.reason,
                    ),
                ).associateBy { descriptor -> descriptor.path },
            )

        private fun effect(
            path: String,
            argSlots: List<NativeServiceArgSlot>,
            reason: String,
        ): NativeServiceDescriptor =
            NativeServiceDescriptor(
                path = path,
                kind = NativeServiceKind.Effect,
                argSlots = argSlots,
                reason = reason,
            )

        private fun intValue(
            path: String,
            argSlots: List<NativeServiceArgSlot>,
            reason: String,
        ): NativeServiceDescriptor =
            NativeServiceDescriptor(
                path = path,
                kind = NativeServiceKind.IntValue,
                argSlots = argSlots,
                reason = reason,
            )

        private fun stringValue(
            path: String,
            argSlots: List<NativeServiceArgSlot>,
            reason: String,
        ): NativeServiceDescriptor =
            NativeServiceDescriptor(
                path = path,
                kind = NativeServiceKind.StringValue,
                argSlots = argSlots,
                reason = reason,
            )
    }
}
