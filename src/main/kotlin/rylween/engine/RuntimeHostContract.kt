/**
 * Backend-neutral capability contract for runtime hosts.
 *
 * `KotlinHeadlessHost.kt`, `RylweenKotlinEngine.kt`, and future server/native
 * hosts declare their surfaces here so J3 conformance and J2 compiled-VM work
 * can compare implementation coverage without depending on frontend classes.
 */
package rylween.engine

/**
 * Runtime-host capabilities a replaceable Rylween implementation must provide.
 *
 * The J1 bundle is content/state data, not a process. A playable host still has
 * to provide input, rendering, persistence, session stepping, and a script
 * execution strategy. This contract is intentionally declared in stable terms
 * so a terminal frontend, bitmap frontend, server, or native process can be
 * compared without depending on Kotlin implementation classes.
 */

enum class RuntimeHostRequirement {
    ReadJ1Bundle,
    VerifyJ1BundleHash,
    BootRuntimeState,
    MutateRuntimeState,
    SemanticInput,
    TextRendering,
    ViewCommandRendering,
    ImageResourceLoading,
    StaticScreenRendering,
    WorldMapViewport,
    RngAndTime,
    Persistence,
    SaveLoad,
    DiagnosticsLogging,
    CharacterCreationSession,
    WildernessSession,
    TownSession,
    DungeonSession,
    CastleSession,
    BattleSession,
    ShopSession,
    SpeakSession,
    ScriptExecution,
    ScenarioDriver,
}

/** View-command categories a host frontend may need to render. */
enum class RuntimeViewCommandKind {
    ClearText,
    ClearMapWindow,
    RenderMap,
    StatusLine,
    ShowImage,
}

/** Script execution strategy advertised by a runtime host. */
enum class RuntimeScriptExecutionStrategy {
    None,
    KotlinAuthoredOpInterpreter,
    CompiledVm,
}

/** Bundle/artifact source consumed by a runtime host. */
enum class RuntimeHostArtifactSource {
    J1JsonBundle,
}

/** Maturity/status bucket for a runtime host surface. */
enum class RuntimeHostStatus {
    BoundaryProbe,
    PlayableRuntime,
}

/** Conformance suite expected for playable runtime hosts. */
enum class RuntimeConformanceSuite {
    BundleBoot,
    StaticScreens,
    WildernessNavigation,
    TownServices,
    DungeonFlow,
    CastleFlow,
    BattleFlow,
    SaveLoad,
    ViewCommands,
}

/** Declared capability surface for one runtime host implementation. */
data class RuntimeHostSurface(
    val id: String,
    val status: RuntimeHostStatus,
    val artifactSource: RuntimeHostArtifactSource,
    val scriptExecution: RuntimeScriptExecutionStrategy,
    val requirements: Set<RuntimeHostRequirement>,
    val viewCommands: Set<RuntimeViewCommandKind> = emptySet(),
    val conformanceSuites: Set<RuntimeConformanceSuite> = emptySet(),
)

/** Failure categories emitted while validating a host surface. */
enum class RuntimeHostDiagnosticKind {
    MissingRequirement,
    MissingViewCommand,
    MissingConformanceSuite,
    MissingScriptExecution,
    MissingBundleArtifact,
}

/** One host-contract validation failure. */
data class RuntimeHostDiagnostic(
    val hostId: String,
    val kind: RuntimeHostDiagnosticKind,
    val detail: String,
)

/** Required capability set used to validate runtime host surfaces. */
data class RuntimeHostContract(
    val playableRequirements: Set<RuntimeHostRequirement>,
    val viewCommands: Set<RuntimeViewCommandKind>,
    val conformanceSuites: Set<RuntimeConformanceSuite>,
) {
    fun validate(surface: RuntimeHostSurface): List<RuntimeHostDiagnostic> {
        if (surface.status != RuntimeHostStatus.PlayableRuntime) {
            return emptyList()
        }
        return buildList {
            (playableRequirements - surface.requirements).forEach { requirement ->
                add(surface.diagnostic(RuntimeHostDiagnosticKind.MissingRequirement, requirement.name))
            }
            (viewCommands - surface.viewCommands).forEach { command ->
                add(surface.diagnostic(RuntimeHostDiagnosticKind.MissingViewCommand, command.name))
            }
            (conformanceSuites - surface.conformanceSuites).forEach { suite ->
                add(surface.diagnostic(RuntimeHostDiagnosticKind.MissingConformanceSuite, suite.name))
            }
            if (surface.scriptExecution == RuntimeScriptExecutionStrategy.None) {
                add(surface.diagnostic(RuntimeHostDiagnosticKind.MissingScriptExecution, "script execution"))
            }
            if (surface.artifactSource != RuntimeHostArtifactSource.J1JsonBundle) {
                add(surface.diagnostic(RuntimeHostDiagnosticKind.MissingBundleArtifact, "J1 JSON bundle"))
            }
        }
    }

    fun requirePlayable(surface: RuntimeHostSurface) {
        val diagnostics = validate(surface)
        require(diagnostics.isEmpty()) {
            diagnostics.joinToString(separator = "\n") { diagnostic ->
                "${diagnostic.hostId}: ${diagnostic.kind}: ${diagnostic.detail}"
            }
        }
    }
}

/** Standard host contract and currently known host declarations. */
object RylweenRuntimeHostContract {
    val standard: RuntimeHostContract =
        RuntimeHostContract(
            playableRequirements = RuntimeHostRequirement.entries.toSet(),
            viewCommands = RuntimeViewCommandKind.entries.toSet(),
            conformanceSuites = RuntimeConformanceSuite.entries.toSet(),
        )

    val kotlinAnsi: RuntimeHostSurface =
        playableKotlinHost("kotlin-ansi")

    val kotlinHeadless: RuntimeHostSurface =
        playableKotlinHost("kotlin-headless")

    val nativeJsonBundleReader: RuntimeHostSurface =
        RuntimeHostSurface(
            id = "native-json-bundle-reader",
            status = RuntimeHostStatus.BoundaryProbe,
            artifactSource = RuntimeHostArtifactSource.J1JsonBundle,
            scriptExecution = RuntimeScriptExecutionStrategy.None,
            requirements =
                setOf(
                    RuntimeHostRequirement.ReadJ1Bundle,
                    RuntimeHostRequirement.VerifyJ1BundleHash,
                ),
        )

    private fun playableKotlinHost(id: String): RuntimeHostSurface =
        RuntimeHostSurface(
            id = id,
            status = RuntimeHostStatus.PlayableRuntime,
            artifactSource = RuntimeHostArtifactSource.J1JsonBundle,
            scriptExecution = RuntimeScriptExecutionStrategy.KotlinAuthoredOpInterpreter,
            requirements = RuntimeHostRequirement.entries.toSet(),
            viewCommands = RuntimeViewCommandKind.entries.toSet(),
            conformanceSuites = RuntimeConformanceSuite.entries.toSet(),
        )
}

private fun RuntimeHostSurface.diagnostic(
    kind: RuntimeHostDiagnosticKind,
    detail: String,
): RuntimeHostDiagnostic = RuntimeHostDiagnostic(id, kind, detail)
