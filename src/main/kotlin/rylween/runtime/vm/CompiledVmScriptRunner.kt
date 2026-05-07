/**
 * Script-root adapter for running loaded compiled programs through [CompiledVm].
 *
 * `compiled/CompiledLoader.kt` materializes validated J1 bundle scripts as
 * dense handle-indexed programs. This file adds the runtime-facing lookup seam
 * that headless, ANSI, server, and native hosts can share: callers name a
 * [rylween.runtime.ScriptRootRef], receive a [CompiledVmResult], and never need
 * to inspect `compiled/CompiledHandleRef` tables directly.
 */
package rylween.runtime.vm

import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledScriptProgram
import rylween.compiled.LoadedCompiledContent
import rylween.runtime.RuntimeSessionState
import rylween.runtime.ScriptRootRef

/**
 * Dense-id independent index over compiled script programs.
 *
 * Compiled bundles carry dense script-root ids for diagnostics and save files,
 * while runtime continuations can be reconstructed from stable kind/slug pairs.
 * This index intentionally keys by kind and slug so loaded roots can still be
 * found after crossing a frontend or save boundary that does not preserve the
 * optional dense id.
 */
class CompiledVmScriptProgramIndex(
    private val content: LoadedCompiledContent,
) {
    private val programsByRoot: Map<RootKey, CompiledScriptProgram> =
        content.scripts.values.associateBy { program -> RootKey(program.root) }

    /** Returns the compiled program for [root], ignoring optional dense ids. */
    fun program(root: ScriptRootRef): CompiledScriptProgram? = programsByRoot[RootKey(root)]

    /** Returns true when [root] is present in the loaded compiled script table. */
    fun contains(root: ScriptRootRef): Boolean = program(root) != null

    /** Returns the loaded script-root handle for [root], if the handle table contains one. */
    fun rootHandle(root: ScriptRootRef): CompiledHandleRef? = content.resolveHandle(CompiledHandleKind.ScriptRoot, root.compiledSlug())
}

/**
 * Root-oriented compiled VM runner for host adapters.
 *
 * This is the first J2a bridge between existing Kotlin session boundaries and
 * the standalone compiled opcode VM. It does not implement child sessions on
 * its own; unsupported opcode families still return the typed diagnostics from
 * [CompiledVm]. Once J2.4 lands, terminal/headless hosts can route their normal
 * script roots through this runner without a separate compiled-program lookup.
 */
class CompiledVmScriptRunner(
    content: LoadedCompiledContent,
    host: CompiledVmHost = RecordingCompiledVmHost(),
    nativeServices: NativeServiceCatalog = NativeServiceCatalog.default,
) {
    private val index = CompiledVmScriptProgramIndex(content)
    private val vm = CompiledVm(content, host, nativeServices)

    /** Returns the compiled program for [root], or `null` if the bundle does not contain it. */
    fun program(root: ScriptRootRef): CompiledScriptProgram? = index.program(root)

    /**
     * Runs [root] from [runtime] with optional VM locals and text bindings.
     *
     * Missing roots fail as [VmDiagnostic.MissingHandle]; present roots return
     * the underlying [CompiledVm] completion, blocked prompt, or diagnostic.
     */
    fun run(
        root: ScriptRootRef,
        runtime: RuntimeSessionState,
        locals: Map<String, Int> = emptyMap(),
        textBindings: Map<String, String> = emptyMap(),
    ): CompiledVmResult = run(root, CompiledVmStart(runtime, locals, textBindings))

    /**
     * Runs [root] from a complete [CompiledVmStart] state.
     *
     * Hosts that already track VM-local values should use this overload so the
     * authored-op interpreter can remain only as a parity oracle during J2a.
     */
    fun run(
        root: ScriptRootRef,
        start: CompiledVmStart,
    ): CompiledVmResult =
        index.program(root)?.let { program -> vm.run(program, start) }
            ?: CompiledVmResult.Failed(index.missingRootDiagnostic(root))

    /**
     * Resumes [continuation] with [input] by resolving the continuation root.
     *
     * The continuation itself remains the saveable VM state; this adapter only
     * finds the owning compiled program before delegating to [CompiledVm.resume].
     */
    fun resume(
        continuation: CompiledVmContinuation,
        input: CompiledVmInput,
    ): CompiledVmResult =
        index.program(continuation.root)?.let { program -> vm.resume(program, continuation, input) }
            ?: CompiledVmResult.Failed(index.missingRootDiagnostic(continuation.root))
}

private data class RootKey(
    val kind: rylween.runtime.ScriptRootKind,
    val slug: String,
) {
    constructor(root: ScriptRootRef) : this(root.kind, root.slug)
}

private fun CompiledVmScriptProgramIndex.missingRootDiagnostic(root: ScriptRootRef): VmDiagnostic =
    VmDiagnostic.MissingHandle(
        root = root,
        instructionIndex = 0,
        operandPath = emptyList(),
        handle = rootHandle(root) ?: CompiledHandleRef(CompiledHandleKind.ScriptRoot, id = -1),
        owner = "script root ${root.compiledSlug()}",
    )

private fun ScriptRootRef.compiledSlug(): String = "${kind.name.lowercase()}.$slug"
