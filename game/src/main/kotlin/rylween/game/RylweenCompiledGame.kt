/**
 * RylweenCompiledGame -- thin facade over the authored game.
 *
 * Downstream tooling (`:tools`, ledger tests) references this object;
 * the authored content itself lives in RylweenGame.kt (with replay
 * fixtures spliced in by RylweenTestScenarios.kt).
*/

package rylween.game

import rylween.dsl.GameModel
import rylween.dsl.RylweenModel

object RylweenCompiledGame {
    val nextRylween: RylweenModel get() = authoredRylween
    val nextDsl: GameModel get() = authoredDsl
}
