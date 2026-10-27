package rylween

import rylween.dsl.BoolAst
import rylween.dsl.IntAst
import rylween.dsl.MenuOption
import rylween.dsl.ScriptSourceRoot
import rylween.dsl.ScriptSourceRootId
import rylween.dsl.ScriptSourceRootKind
import rylween.dsl.ScriptStatementNode
import rylween.dsl.TextBlock
import rylween.dsl.ViewCommand
import rylween.dsl.inputKey
import rylween.dsl.literalText
import rylween.dsl.unreadTexts
import kotlin.test.Test
import kotlin.test.assertEquals

/** Text a script shows must stay up until something holds it: a key press, a pause, or a menu that keeps it. */
class UnreadScriptTextTest {
    private val wait = ScriptStatementNode.WaitInput()
    private val either = BoolAst.Cmp(IntAst.Lit(1), "eq", IntAst.Lit(1))

    @Test
    fun aPageWrittenOverAnotherBeforeAnyKeyPressLosesIt() {
        assertEquals(
            listOf("A -> \"B\""),
            unread(write("A"), write("B"), wait),
        )
    }

    @Test
    fun textStillUpWhenTheScriptEndsIsLost() {
        assertEquals(listOf("A -> the end of the script"), unread(write("A")))
    }

    @Test
    fun aKeyPressOrAPauseHoldsTheText() {
        assertEquals(emptyList(), unread(write("A"), wait, write("B"), ScriptStatementNode.Pause(1_000)))
    }

    @Test
    fun appendedTextStaysOnTheSamePage() {
        assertEquals(emptyList(), unread(write("A"), append("B"), wait))
    }

    @Test
    fun clearingUnreadTextLosesIt() {
        assertEquals(
            listOf("A -> clearText()"),
            unread(write("A"), ScriptStatementNode.View(ViewCommand.ClearText)),
        )
    }

    @Test
    fun aMenuThatOnlyListsItsChoicesKeepsTheTextAboveThem() {
        assertEquals(emptyList(), unread(write("A"), menu(literalText("(Y)es", "(N)o"))))
    }

    @Test
    fun aMenuWithItsOwnPromptReplacesTheText() {
        assertEquals(
            listOf("A -> the menu \"Sure? (Y)es (N)o\""),
            unread(write("A"), menu(literalText("Sure?", "(Y)es", "(N)o"))),
        )
    }

    @Test
    fun everyBranchIsFollowed() {
        assertEquals(
            listOf("A -> the end of the script"),
            unread(write("A"), ScriptStatementNode.If(either, listOf(wait), emptyList())),
        )
    }

    @Test
    fun aLoopCarriesItsLastTextIntoItsNextRound() {
        val round = ScriptStatementNode.If(either, listOf(ScriptStatementNode.Break()), emptyList())
        assertEquals(
            listOf("A -> \"A\"", "A -> the end of the script"),
            unread(ScriptStatementNode.Loop(listOf(write("A"), round))),
        )
    }

    private fun unread(vararg statements: ScriptStatementNode): List<String> =
        ScriptSourceRoot(ScriptSourceRootId(ScriptSourceRootKind.Scene, "test"), statements.toList(), "scene test")
            .unreadTexts()
            .map { finding -> "${finding.text} -> ${finding.replacedBy}" }

    private fun write(text: String) = ScriptStatementNode.Screen(literalText(text))

    private fun append(text: String) = ScriptStatementNode.Screen(literalText(text), append = true)

    private fun menu(prompt: TextBlock) =
        ScriptStatementNode.Menu(
            prompt = prompt,
            options = listOf(MenuOption(inputKey('Y'), listOf(wait)), MenuOption(inputKey('N'), listOf(wait))),
            defaultStatements = emptyList(),
        )
}
