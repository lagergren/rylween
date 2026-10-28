/**
 * Typed flag declarations and references.
 *
 * The game DSL uses these records to describe party and encounter flags without
 * exposing legacy numeric slots. Validation and compilation turn the typed
 * references into dense runtime ids, while save/load keeps the authored slug and
 * state names as the stable contract.
 */
package rylween.dsl.impl

import rylween.dsl.BoolAst
import rylween.dsl.BoolExpr

/** Runtime storage scope for authored story flags. */
enum class FlagScope { Party, Encounter }

/** Base class for generated or hand-authored typed flag references. */
abstract class FlagRef(
    val slug: String,
    val scope: FlagScope = FlagScope.Party,
) {
    private val declaredStates = mutableListOf<FlagState>()
    val states: List<FlagState> get() = declaredStates

    protected fun state(native: String): FlagState = FlagState(this, native).also { declaredStates += it }
}

/** One native state value belonging to a typed [FlagRef]. */
open class FlagState internal constructor(
    val flag: FlagRef,
    val native: String,
) : BoolExpr() {
    override val ast: BoolAst = BoolAst.FlagIs(flag.slug, native)
}

private val flagRegistry = mutableListOf<FlagRef>()

/** Process-local registry populated by generated flag declarations. */
val declaredFlags: List<FlagRef> get() = flagRegistry

/** Registers a generated or hand-authored flag reference for validation and save parsing. */
fun <F : FlagRef> F.declareFlag(): F = also { flagRegistry += it }
