/**
 * Visits the title menu's screens in a session after a run: [visits] are the
 * title commands to choose, in order, such as the scores, the hall of heroes,
 * and the manual, then loading the saved game, or deleting the party and
 * leaving. Paged screens are paged by the autopilot's own rules.
 */

package rylween.runtime

import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId

internal class AutopilotTitleTour(
    systems: ExternalRuntimeSystems,
    private val coverage: AutopilotCoverage,
    visits: List<RuntimeCommandActionId>,
) {
    private val commands = systems.commands.getValue(RuntimeCommandGroupId.Title.id)
    private val labels = commands.mapNotNull { command -> command.label }
    private val queue = ArrayDeque(visits)

    /** The key for the title splash or menu, or the deletion confirmation; `null` for any other screen. */
    fun key(view: AutopilotView): Char? {
        val text = view.text.joinToString(" ")
        val menu = AutopilotMenu.parse(view.text)
        val titleMenu = menu.options.count { option -> labels.any { label -> option.label.startsWith(label) } } >= TITLE_MENU_OPTIONS
        return when {
            "Terminate this party?" in text -> menu.find("Yes")?.key
            queue.isEmpty() -> null
            // The splash shows no text and takes its first key as a menu choice.
            view.text.none(String::isNotBlank) && !started || titleMenu -> next()
            else -> null
        }
    }

    private var started = false

    private fun next(): Char {
        started = true
        val action = queue.removeFirst()
        coverage.record(RuntimeCommandGroupId.Title.id, action.id)
        return commands.key(action)
    }

    private companion object {
        const val TITLE_MENU_OPTIONS = 3
    }
}
