package com.noise.netpulse.domain.model

/** Ordered diagnostic steps of the pipeline. */
enum class DiagnosticStep {
    CONNECTION,
    GATEWAY,
    DNS,
    INTERNET,
    HTTPS,
    LATENCY,
    RELIABILITY,
}

/** Per-step verdict shown in the detail screen. */
enum class StepStatus {
    PASSED,
    WARNING,
    FAILED,
    UNAVAILABLE,
    SKIPPED,
}

/** Severity of a diagnostic finding. */
enum class Severity { OK, INFO, WARNING, ERROR }

/**
 * One evidence-backed diagnostic observation.
 * `title` and `possibleCause` deliberately use hedged language
 * (possible / likely / suggests) — the engine never claims certainty.
 */
data class DiagnosticFinding(
    val step: DiagnosticStep,
    val severity: Severity,
    val title: String,
    val evidence: String,
    val possibleCause: String,
    val detail: String? = null,
)

/** Deterministic, explainable health score. */
data class HealthScore(
    val total: Int,
    val maxTotal: Int,
    val components: List<Component>,
) {
    data class Component(val name: String, val score: Int, val max: Int)
}

/** Complete result of one diagnostic run. */
data class DiagnosticReport(
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val networkType: NetworkType,
    val connectionState: ConnectionState = ConnectionState.UNKNOWN,
    val gateway: GatewayResult? = null,
    val dns: DnsResult? = null,
    val internet: InternetResult? = null,
    val https: HttpsResult? = null,
    val reliability: ReliabilityResult? = null,
    val findings: List<DiagnosticFinding> = emptyList(),
    val summary: String = "",
    val stepStatuses: Map<DiagnosticStep, StepStatus> = emptyMap(),
    val healthScore: HealthScore? = null,
    /** Set when the network changed mid-run and the run was aborted (Phase 15). */
    val abortedDueToNetworkChange: Boolean = false,
)
