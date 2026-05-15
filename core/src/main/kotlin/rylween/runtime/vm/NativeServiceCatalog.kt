/**
 * Native-service allowlist and signature catalog for compiled script execution.
 *
 * `compiled/CompiledLoader.kt` uses these descriptors to reject unknown or
 * malformed native string expressions before runtime hosts see a bundle.
 * Other-language runtimes should treat this as the native-service ABI: path and
 * argument-slot kinds must match before a service call is allowed to run.
 */
package rylween.runtime.vm

import rylween.dsl.NativeAllowlist

/**
 * Operand shape accepted by a native service argument position.
 *
 * `StringValue` is used by literal string arguments. `IntExprValue` is used by
 * compiled native string arguments that evaluate integer expressions before
 * host formatting.
 */
enum class NativeServiceArgSlot {
    StringValue,
    IntExprValue,
}

/**
 * Allowlisted native service signature.
 *
 * [path] is the stable service id emitted in compiled bundles, [argSlots] pins
 * arity and argument kinds, and [reason] is a short authoring/runtime
 * explanation copied from [NativeAllowlist].
 */
data class NativeServiceDescriptor(
    val path: String,
    val argSlots: List<NativeServiceArgSlot>,
    val reason: String,
)

/**
 * Native-service allowlist used by the compiled loader and VM host.
 *
 * The loader uses [descriptor] to reject unknown native string paths before a
 * runtime sees the bundle so Kotlin, native, server, and bitmap frontends agree
 * on path, arity, and argument-slot semantics. If a non-Kotlin host adds a
 * service, it should add a descriptor here and a loader conformance test at the
 * same time.
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
                ).associateBy { descriptor -> descriptor.path },
            )

        private fun stringValue(
            path: String,
            argSlots: List<NativeServiceArgSlot>,
            reason: String,
        ): NativeServiceDescriptor =
            NativeServiceDescriptor(
                path = path,
                argSlots = argSlots,
                reason = reason,
            )
    }
}
