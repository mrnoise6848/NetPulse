package com.noise.netpulse.domain.engine

/**
 * Latency classification (Phase 8).
 *
 * Thresholds are pragmatic heuristics, not universal standards, and are
 * documented in docs/network-diagnostics.md. Different scales apply to
 * local network hops (gateway, DNS) and to internet endpoints.
 */
enum class LatencyGrade {
    EXCELLENT,
    GOOD,
    FAIR,
    HIGH,
    VERY_HIGH,
}

object LatencyAnalyzer {

    /** Gateway / local DNS latencies. */
    fun classifyLocal(latencyMs: Long?): LatencyGrade? = classify(
        latencyMs,
        excellentAt = 20,
        goodAt = 50,
        fairAt = 100,
        highAt = 200,
    )

    /** Internet / HTTPS endpoint latencies. */
    fun classifyInternet(latencyMs: Long?): LatencyGrade? = classify(
        latencyMs,
        excellentAt = 50,
        goodAt = 100,
        fairAt = 200,
        highAt = 400,
    )

    fun label(grade: LatencyGrade?): String = when (grade) {
        LatencyGrade.EXCELLENT -> "Excellent"
        LatencyGrade.GOOD -> "Good"
        LatencyGrade.FAIR -> "Fair"
        LatencyGrade.HIGH -> "High"
        LatencyGrade.VERY_HIGH -> "Very High"
        null -> "Unavailable"
    }

    fun grade(latencyMs: Long?, internet: Boolean): LatencyGrade? =
        if (internet) classifyInternet(latencyMs) else classifyLocal(latencyMs)

    private fun classify(latencyMs: Long?, excellentAt: Long, goodAt: Long, fairAt: Long, highAt: Long): LatencyGrade? =
        when {
            latencyMs == null -> null
            latencyMs <= excellentAt -> LatencyGrade.EXCELLENT
            latencyMs <= goodAt -> LatencyGrade.GOOD
            latencyMs <= fairAt -> LatencyGrade.FAIR
            latencyMs <= highAt -> LatencyGrade.HIGH
            else -> LatencyGrade.VERY_HIGH
        }
}
