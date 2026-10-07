package com.noise.netpulse.domain.model

/**
 * Result of the gateway reachability probe (Phase 4).
 * `method` records how reachability was actually determined, e.g.
 * "ICMP echo" or "TCP connect :443", so the UI can explain the technique used.
 */
data class GatewayResult(
    val address: String?,
    val reachable: Boolean,
    val latencyMs: Long?,
    val method: String? = null,
    val error: String? = null,
)
