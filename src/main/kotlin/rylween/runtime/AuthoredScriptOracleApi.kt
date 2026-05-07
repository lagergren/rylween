/**
 * Marker for APIs that execute authored `Op` scripts outside the compiled VM.
 *
 * The J2 runtime contract is the compiled bundle plus `CompiledVm`. APIs marked
 * with [AuthoredScriptOracleApi] are retained for migration diagnostics and
 * parity tests while the old `Op` world is retired from production runtime
 * paths. Production launchers should route script roots through the compiled VM.
 */

package rylween.runtime

/**
 * Marks an API as part of the authored-script interpreter/oracle surface.
 *
 * New runtime hosts must not depend on marked declarations as their portable
 * execution contract. Use `rylween.runtime.vm.CompiledVm` and the compiled
 * bundle loader for production VM execution. The current owner of this surface
 * is the parity/oracle test suite that compares compiled behavior against the
 * previous authored interpreter.
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class AuthoredScriptOracleApi
