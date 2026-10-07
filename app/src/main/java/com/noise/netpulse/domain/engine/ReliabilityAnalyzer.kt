package com.noise.netpulse.domain.engine

import com.noise.netpulse.domain.model.ReliabilityResult
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/**
 * Repeats a lightweight probe (see [ReliabilityResult]) and computes
 * success rate, timeout count, jitter and median latency.
 */
class ReliabilityAnalyzer {

    /**
     * Runs [requests] sequential probes. The probe returns the measured latency
     * in ms on success, or null on failure (with [isTimeout] telling the analyzer
     * whether that failure was a timeout).
     */
    suspend fun run(
        requests: Int,
        maxRequests: Int = MAX_REQUESTS,
        probe: suspend () -> Pair<Long?, Boolean>,
    ): ReliabilityResult {
        val count = requests.coerceIn(1, maxRequests)
        val successfulLatencies = mutableListOf<Long>()
        var failed = 0
        var timeouts = 0

        repeat(count) {
            coroutineContext.ensureActive()
            val (latency, wasTimeout) = probe()
            if (latency != null) {
                successfulLatencies += latency
            } else {
                failed++
                if (wasTimeout) timeouts++
            }
        }

        val successRate = if (count > 0) successfulLatencies.size * 100.0 / count else null
        val jitter = jitterOf(successfulLatencies)
        val median = medianOf(successfulLatencies)

        return ReliabilityResult(
            requests = count,
            successful = successfulLatencies.size,
            failed = failed,
            timeouts = timeouts,
            successRatePercent = successRate,
            jitterMs = jitter,
            medianLatencyMs = median,
            allTimeouts = timeouts == count && count > 0,
        )
    }

    private fun jitterOf(latencies: List<Long>): Double? {
        if (latencies.size < 2) return null
        val mean = latencies.average()
        val variance = latencies.sumOf { (it - mean) * (it - mean) } / (latencies.size - 1)
        return sqrt(variance)
    }

    private fun medianOf(latencies: List<Long>): Long? {
        if (latencies.isEmpty()) return null
        val sorted = latencies.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private companion object {
        const val MAX_REQUESTS = 20
    }
}
