package rylween.dsl

/**
 * Authored script source before validation and compiled bundle lowering.
 *
 * This is the authoring/compiler IR. It is intentionally separate from the
 * runtime VM contract in `rylween.compiled`: source scripts keep designer
 * structure, labels, branch shape, and source-map paths, while compiled bundles
 * keep dense opcodes and operands for runtimes. Existing [Op] nodes implement
 * [ScriptStatement] during the migration so I9 can move builders, validation,
 * and compilation in small behavior-preserving slices.
 *
 * Production runtimes should consume compiled bundle rows, not these source
 * records. Authored-source execution belongs only in explicit oracle/parity
 * tests while this IR remains useful for validation and diagnostics.
 */
data class AuthoredScript(
    val block: ScriptBlock,
) {
    constructor(ops: List<Op>) : this(ScriptBlock.fromOps(ops))

    /** Compatibility view used while existing compiler paths still accept [Op]. */
    val ops: List<Op> get() = block.ops
}

/**
 * One lexical block in an authored script.
 *
 * Blocks are explicit so nested control flow, menu choices, text-input success
 * and failure branches, and future source-map spans can be named as script
 * structure instead of anonymous `List<Op>` values.
 */
data class ScriptBlock(
    val statements: List<ScriptStatement>,
) {
    /** Compatibility view used while I9b/I9c migrate builders and walkers. */
    val ops: List<Op>
        get() =
            statements.map { statement ->
                statement as? Op ?: error("script block contains a non-Op statement before I9 lowering supports it")
            }

    companion object {
        fun fromOps(ops: List<Op>): ScriptBlock = ScriptBlock(ops)
    }
}

/**
 * Mutable recorder for one authored [ScriptBlock].
 *
 * Builders write [ScriptStatement] here first. The [toBlock] result is the new
 * source IR, while [toOps] is the temporary compatibility bridge for compiler
 * and validation paths that still consume legacy [Op] lists.
 */
class ScriptBlockBuilder {
    private val statements = mutableListOf<ScriptStatement>()

    val size: Int get() = statements.size

    fun record(statement: ScriptStatement) {
        statements += statement
    }

    operator fun set(
        index: Int,
        statement: ScriptStatement,
    ) {
        statements[index] = statement
    }

    fun toBlock(): ScriptBlock = ScriptBlock(statements.toList())

    fun toOps(): List<Op> = toBlock().ops
}

/**
 * Source statement categories for authored scripts.
 *
 * The categories are deliberately about authoring ownership, not VM opcodes:
 * text/view statements describe what is shown, control statements describe
 * source flow, input statements describe authored prompts and selections, state
 * mutation statements change party/world/script state, and gameplay handoff
 * statements delegate to engine services such as battle, shops, or dungeons.
 */
sealed interface ScriptStatement {
    /** Text, journal, image, and other view-output side effects. */
    sealed interface TextView : ScriptStatement

    /** Authored control flow such as branches, loops, stops, and iteration. */
    sealed interface ControlFlow : ScriptStatement

    /** Authored prompts, menu choices, pauses for input, and text entry. */
    sealed interface Input : ScriptStatement

    /** State changes against locals, party/world state, inventory, or progress. */
    sealed interface StateMutation : ScriptStatement

    /** Handoffs from source script to a runtime gameplay subsystem. */
    sealed interface GameplayHandoff : ScriptStatement
}
