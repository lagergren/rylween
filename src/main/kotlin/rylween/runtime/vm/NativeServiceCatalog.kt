/**
 * Native-service allowlist and signature catalog for compiled script execution.
 *
 * `compiled/CompiledLoader.kt` uses these descriptors to reject unknown or
 * malformed native calls, and `CompiledVm.kt` uses the same descriptors before
 * invoking host callbacks. Other-language runtimes should treat this as the
 * native-service ABI: path, kind, arity, and argument-slot kinds must all match
 * before a service call is allowed to run.
 */
package rylween.runtime.vm

import rylween.dsl.NativeAllowlist

/**
 * Execution family for a native service path.
 *
 * The catalog is consumed by `compiled/CompiledLoader.kt` for `NativeCall`
 * validation and by `CompiledVm.kt` for native effect/int/string execution.
 * The source of truth for currently authored paths remains [NativeAllowlist].
 */
enum class NativeServiceKind {
    Effect,
    IntValue,
    StringValue,
}

/**
 * Operand shape accepted by a native service argument position.
 *
 * `StringValue` is used by `NativeCall` string args and literal string
 * arguments. `IntExprValue` is used by compiled native string arguments that
 * evaluate integer expressions before host formatting.
 */
enum class NativeServiceArgSlot {
    StringValue,
    IntExprValue,
}

/**
 * Allowlisted native service signature.
 *
 * [path] is the stable service id emitted in compiled bundles, [kind] decides
 * which VM/native call site may use it, [argSlots] pins arity and argument
 * kinds, and [reason] is a short authoring/runtime explanation copied from
 * [NativeAllowlist].
 */
data class NativeServiceDescriptor(
    val path: String,
    val kind: NativeServiceKind,
    val argSlots: List<NativeServiceArgSlot>,
    val reason: String,
) {
    /** Number of arguments required by this service. */
    val arity: Int get() = argSlots.size
}

/**
 * Native-service allowlist used by the compiled loader and VM host.
 *
 * The loader uses [descriptor] to reject unknown `NativeCall` paths before a
 * runtime sees the bundle. The VM uses the same catalog at execution time so
 * Kotlin, native, server, and bitmap frontends agree on path, arity, and
 * argument-slot semantics. If a non-Kotlin host adds a service, it should add a
 * descriptor here and a loader/VM conformance test at the same time.
 */
data class NativeServiceCatalog(
    val entries: Map<String, NativeServiceDescriptor>,
) {
    init {
        require(entries.keys.all { it.isNotBlank() }) { "native service paths must be non-blank" }
        require(entries.all { (path, descriptor) -> path == descriptor.path }) {
            "native service catalog keys must match descriptor paths"
        }
    }

    /** Returns the descriptor for [path], or `null` when the path is not allowlisted. */
    fun descriptor(path: String): NativeServiceDescriptor? = entries[path]

    companion object {
        /**
         * Catalog for the current authored runtime boundary.
         *
         * Entries are derived from [NativeAllowlist] so the DSL helper surface
         * and compiled-bundle loader cannot drift into separate path lists.
         */
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
