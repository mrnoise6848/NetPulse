package com.noise.netpulse.domain.model

/**
 * Reliability analysis result (Phase 9).
 *
 * Repeats a lightweight probe and reports application-level request
 * reliability. This is explicitly *not* called packet loss: it measures
 * HTTP request success, which can be affected by TLS, DNS and server
 * behaviour in addition to actual path loss.
 */
data class ReliabilityResult(
    val requests: Int,
    val successful: Int,
    val failed: Int,
    val timeouts: Int,
    /** Percent of successful requests, or null when nothing could run. */
    val successRatePercent: Double?,
    /** Standard deviation of successful request latencies in ms. */
    val jitterMs: Double?,
    /** Median successful request latency in ms. */
    val medianLatencyMs: Long?,
    /** True when every request timed out (suggests, not proves, path issues). */
    val allTimeouts: Boolean,
)
