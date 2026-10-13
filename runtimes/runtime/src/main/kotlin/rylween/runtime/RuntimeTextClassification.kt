/**
 * Runtime-owned text classification helpers.
 *
 * Game prose, prompts, reusable labels, and gameplay failure text should come
 * from authored content or runtime-system prompt rows. These helpers mark the
 * few strings that the runtime may still construct directly because they are
 * shell chrome, generated details, or developer diagnostics.
 */
package rylween.runtime

/** Developer-facing diagnostics for missing or inconsistent compiled bundle data. */
internal fun runtimeDiagnosticText(vararg lines: String): List<String> = lines.toList()

/** Runtime shell/chrome text that belongs to the host rather than the game world. */
internal fun runtimeChromeText(vararg lines: String): List<String> = lines.toList()

/** Generated operational detail such as the key a player pressed. */
internal fun runtimeGeneratedDetailText(vararg lines: String): List<String> = lines.toList()
