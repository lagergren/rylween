package rylween.dsl

/**
 * Authored script source before validation and compiled bundle lowering.
 *
 * This is the authoring/compiler IR. It is intentionally separate from the
 * runtime VM contract in `rylween.compiled`: source scripts keep designer
 * structure, labels, branch shape, and source-map paths, while compiled bundles
 * keep dense opcodes and operands for runtimes. Concrete [ScriptStatementNode]
 * nodes implement [ScriptStatement] during the migration so I9 can move
 * builders, validation, and compilation in small behavior-preserving slices.
 *
 * Production runtimes should consume compiled bundle rows, not these source
 * records. Authored-source execution belongs only in explicit oracle/parity
 * tests while this IR remains useful for validation and diagnostics.
 *
 * Script-control loops recorded here are authoring constructs that lower to
 * compiled script instructions. They are not the same thing as runtime
 * mode/service loops such as wilderness, town, dungeon, castle, shop, or battle
 * loops, which own live input cadence, rendering, persistence, RNG, and
 * subsystem state.
 */
data class AuthoredScript(
    val block: ScriptBlock,
) {
    constructor(statementNodes: List<ScriptStatementNode>) : this(ScriptBlock.fromStatementNodes(statementNodes))

    /** Concrete source-node view used by compiler and diagnostics while I9 keeps legacy node implementations. */
    val statementNodes: List<ScriptStatementNode> get() = block.statementNodes
}

/**
 * One lexical block in an authored script.
 *
 * Blocks are explicit so nested control flow, menu choices, text-input success
 * and failure branches, and future source-map spans can be named as script
 * structure instead of anonymous `List<ScriptStatementNode>` values.
 */
data class ScriptBlock(
    val statements: List<ScriptStatement>,
) {
    /** Concrete source-node view used while all current statement implementations are [ScriptStatementNode]. */
    val statementNodes: List<ScriptStatementNode>
        get() =
            statements.map { statement ->
                statement as? ScriptStatementNode
                    ?: error("script block contains a non-ScriptStatementNode statement before I9 lowering supports it")
            }

    companion object {
        fun fromStatementNodes(statementNodes: List<ScriptStatementNode>): ScriptBlock = ScriptBlock(statementNodes)
    }
}

/**
 * Mutable recorder for one authored [ScriptBlock].
 *
 * Builders write [ScriptStatement] here first. The [toBlock] result is the new
 * source IR, while [toStatementNodes] exposes the concrete node list still used
 * by builder helpers that assemble nested branches.
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

    fun toStatementNodes(): List<ScriptStatementNode> = toBlock().statementNodes
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

    /** Authored script-control flow such as branches, script loops, stops, and iteration. */
    sealed interface ControlFlow : ScriptStatement

    /** Authored prompts, menu choices, pauses for input, and text entry. */
    sealed interface Input : ScriptStatement

    /** State changes against locals, party/world state, inventory, or progress. */
    sealed interface StateMutation : ScriptStatement

    /** Handoffs from source script to a runtime gameplay subsystem. */
    sealed interface GameplayHandoff : ScriptStatement
}
