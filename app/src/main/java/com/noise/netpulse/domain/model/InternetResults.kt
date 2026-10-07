package com.noise.netpulse.domain.model

/** Result of one HTTPS endpoint probe (Phases 6–7). */
data class EndpointProbe(
    val url: String,
    val success: Boolean,
    val statusCode: Int?,
    val latencyMs: Long?,
    val errorType: HttpErrorType? = null,
    val error: String? = null,
)

/** Lightweight reachability summary over a small set of stable HTTPS endpoints. */
data class InternetResult(
    val probes: List<EndpointProbe>,
    val successCount: Int,
    val failureCount: Int,
    val bestLatencyMs: Long?,
) {
    val reachable: Boolean get() = successCount > 0
}

/** HTTPS diagnostics with classified failure types. */
data class HttpsResult(
    val probes: List<EndpointProbe>,
    val successCount: Int,
    val failureCount: Int,
    val bestLatencyMs: Long?,
    val primaryFailure: HttpErrorType?,
) {
    val works: Boolean get() = successCount > 0
}

/** Classified HTTPS failure reasons — never cert-bypass hints. */
enum class HttpErrorType {
    DNS_FAILURE,
    TIMEOUT,
    CONNECTION_REFUSED,
    TLS_FAILURE,
    UNEXPECTED_STATUS,
    UNKNOWN,
}
