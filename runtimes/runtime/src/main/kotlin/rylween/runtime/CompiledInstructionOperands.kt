/**
 * Typed operand accessors for compiled instructions.
 *
 * `ScriptExecutor.kt` and the combat script runner in `BattleRunner.kt` both
 * decode `CompiledInstruction` operands by position. Each accessor casts the
 * operand to the shape its opcode guarantees, so a malformed bundle fails at
 * the read instead of being silently reinterpreted.
 */

package rylween.runtime

import rylween.compiled.CompiledBoolExpr
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInputBinding
import rylween.compiled.CompiledInstruction
import rylween.compiled.CompiledIntExpr
import rylween.compiled.CompiledLValue
import rylween.compiled.CompiledOperand
import rylween.compiled.CompiledRandomItemOffer
import rylween.compiled.CompiledScriptReward
import rylween.compiled.CompiledShopService
import rylween.compiled.CompiledStringExpr
import rylween.compiled.CompiledSwitchCase
import rylween.compiled.CompiledTextAnswer
import rylween.compiled.CompiledTextBlock
import rylween.compiled.CompiledViewCommand

internal fun CompiledInstruction.textBlockOperand(index: Int): CompiledTextBlock = (operands[index] as CompiledOperand.TextBlockValue).value

internal fun CompiledInstruction.intOperand(index: Int): CompiledIntExpr = (operands[index] as CompiledOperand.IntExprValue).value

internal fun CompiledInstruction.boolOperand(index: Int): CompiledBoolExpr = (operands[index] as CompiledOperand.BoolExprValue).value

internal fun CompiledInstruction.lvalueOperand(index: Int): CompiledLValue = (operands[index] as CompiledOperand.LValueValue).value

internal fun CompiledInstruction.stringExprOperand(index: Int): CompiledStringExpr =
    (operands[index] as CompiledOperand.StringExprValue).value

internal fun CompiledInstruction.viewOperand(index: Int): CompiledViewCommand = (operands[index] as CompiledOperand.ViewCommandValue).value

internal fun CompiledInstruction.handleOperand(index: Int): CompiledHandleRef = (operands[index] as CompiledOperand.Handle).ref

internal fun CompiledInstruction.stringIdOperand(index: Int): Int = (operands[index] as CompiledOperand.StringId).id

internal fun CompiledInstruction.booleanOperand(index: Int): Boolean = (operands[index] as CompiledOperand.BooleanValue).value

internal fun CompiledInstruction.characterOperand(index: Int): Char = (operands[index] as CompiledOperand.Character).value

internal fun CompiledInstruction.integerOperand(index: Int): Int = (operands[index] as CompiledOperand.Integer).value

internal fun CompiledInstruction.enumOperand(index: Int): String = (operands[index] as CompiledOperand.EnumName).name

internal fun CompiledInstruction.instructionsOperand(index: Int): List<CompiledInstruction> =
    (operands[index] as CompiledOperand.Instructions).instructions

internal fun CompiledInstruction.switchCasesOperand(index: Int): List<CompiledSwitchCase> =
    (operands[index] as CompiledOperand.SwitchCases).cases

internal fun CompiledInstruction.menuOptionsOperand(index: Int) = (operands[index] as CompiledOperand.MenuOptions).options

internal fun CompiledInstruction.inputBindingsOperand(index: Int): List<CompiledInputBinding> =
    (operands[index] as CompiledOperand.InputBindings).bindings

internal fun CompiledInstruction.textAnswerOperand(index: Int): CompiledTextAnswer = (operands[index] as CompiledOperand.TextAnswer).answer

internal fun CompiledInstruction.rewardOperand(index: Int): CompiledScriptReward = (operands[index] as CompiledOperand.Reward).reward

internal fun CompiledInstruction.randomItemOfferOperand(index: Int): CompiledRandomItemOffer =
    (operands[index] as CompiledOperand.RandomItemOffer).offer

internal fun CompiledInstruction.shopServiceOperand(index: Int): CompiledShopService =
    (operands[index] as CompiledOperand.ShopService).service
