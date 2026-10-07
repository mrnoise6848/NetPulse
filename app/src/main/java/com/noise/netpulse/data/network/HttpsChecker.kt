package com.noise.netpulse.data.network

import com.noise.netpulse.domain.model.EndpointProbe
import com.noise.netpulse.domain.model.HttpErrorType
import com.noise.netpulse.domain.model.HttpsResult
import com.noise.netpulse.domain.model.InternetResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.coroutineContext

/**
 * HTTPS diagnostics (Phases 6–7).
 *
 * Probes a small set of independent, stable HTTPS endpoints that return tiny
 * responses (204/200, no meaningful body) — no large downloads for reachability.
 * TLS certificate verification is always left at its secure defaults; there is
 * no insecure fallback. Failures are classified for the diagnostic engine.
 */
class HttpsChecker(
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
) {

    suspend fun check(timeoutMs: Int = DEFAULT_TIMEOUT_MS): HttpsResult =
        withContext(Dispatchers.IO) {
            val probes = endpoints.map { url ->
                ensureActive()
                probe(url, timeoutMs)
            }
            HttpsResult(
                probes = probes,
                successCount = probes.count { it.success },
                failureCount = probes.count { !it.success },
                bestLatencyMs = probes.mapNotNull { it.latencyMs }.minOrNull(),
                primaryFailure = probes.firstNotNullOfOrNull { it.errorType },
            )
        }

    /** Lightweight reachability-only check over the same endpoint set (Phase 6). */
    suspend fun checkReachability(timeoutMs: Int = DEFAULT_TIMEOUT_MS): InternetResult {
        val result = check(timeoutMs)
        return InternetResult(
            probes = result.probes,
            successCount = result.successCount,
            failureCount = result.failureCount,
            bestLatencyMs = result.bestLatencyMs,
        )
    }

    suspend fun probe(url: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): EndpointProbe =
        withContext(Dispatchers.IO) {
            val connection = runCatching { openConnection(url) }.getOrNull()
                ?: return@withContext EndpointProbe(
                    url = url,
                    success = false,
                    statusCode = null,
                    latencyMs = null,
                    errorType = HttpErrorType.UNKNOWN,
                    error = "Invalid endpoint URL",
                )

            try {
                ensureActive()
                val start = System.nanoTime()
                val statusCode = connection.responseCode
                val latencyMs = (System.nanoTime() - start) / 1_000_000

                // Drain a bounded amount of the response body to free the connection.
                runCatching {
                    connection.inputStream.use { stream ->
                        val buffer = ByteArray(2_048)
                        var total = 0
                        while (total < MAX_BODY_BYTES) {
                            val read = stream.read(buffer)
                            if (read < 0) break
                            total += read
                        }
                    }
                }

                val success = statusCode in 200..299 || statusCode == 204
                EndpointProbe(
                    url = url,
                    success = success,
                    statusCode = statusCode,
                    latencyMs = latencyMs,
                    errorType = if (success) null else HttpErrorType.UNEXPECTED_STATUS,
                    error = if (success) null else "HTTP status $statusCode",
                )
            } catch (e: SSLHandshakeException) {
                EndpointProbe(url, false, null, null, HttpErrorType.TLS_FAILURE, "TLS handshake failed: ${e.message}")
            } catch (e: CertificateException) {
                EndpointProbe(url, false, null, null, HttpErrorType.TLS_FAILURE, "Certificate error: ${e.message}")
            } catch (e: UnknownHostException) {
                EndpointProbe(url, false, null, null, HttpErrorType.DNS_FAILURE, "DNS lookup failed")
            } catch (e: SocketTimeoutException) {
                EndpointProbe(url, false, null, null, HttpErrorType.TIMEOUT, "Timed out after ${timeoutMs}ms")
            } catch (e: ConnectException) {
                EndpointProbe(url, false, null, null, HttpErrorType.CONNECTION_REFUSED, e.message ?: "Connection refused")
            } catch (e: IOException) {
                EndpointProbe(url, false, null, null, HttpErrorType.UNKNOWN, e.message ?: "I/O error")
            } finally {
                runCatching { connection.disconnect() }
            }
        }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = (java.net.URI(url).toURL().openConnection()) as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = DEFAULT_TIMEOUT_MS
        connection.readTimeout = DEFAULT_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        // Platform TLS verification is left exactly as-is: no trust-all, no insecure fallback.
        return connection
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000
        const val MAX_BODY_BYTES = 4_096

        /** Independent, stable endpoints that answer with tiny or empty bodies. */
        val DEFAULT_ENDPOINTS = listOf(
            "https://connectivitycheck.gstatic.com/generate_204",
            "https://www.msftconnecttest.com/connecttest.txt",
            "https://cp.cloudflare.com/generate_204",
        )
    }
}
