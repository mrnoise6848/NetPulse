package com.noise.netpulse.domain.model

/** Result of a direct DNS query against one resolver (Phase 5). */
data class DnsResolverResult(
    val address: String,
    val success: Boolean,
    val latencyMs: Long?,
    val resolvedIps: List<String> = emptyList(),
    val error: String? = null,
)

/** Result of resolving through the system resolver path (getaddrinfo). */
data class DnsSystemResult(
    val success: Boolean,
    val latencyMs: Long?,
    val resolvedCount: Int,
    val error: String? = null,
)

/** Aggregate DNS diagnostics for one hostname. */
data class DnsResult(
    val hostname: String,
    val resolvers: List<DnsResolverResult>,
    val systemResolution: DnsSystemResult?,
)
