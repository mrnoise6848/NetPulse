package com.noise.netpulse.data.network

import com.noise.netpulse.domain.model.DnsResolverResult
import com.noise.netpulse.domain.model.DnsResult
import com.noise.netpulse.domain.model.DnsSystemResult
import com.noise.netpulse.domain.model.LocalNetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.coroutines.coroutineContext

/**
 * DNS diagnostics (Phase 5).
 *
 * Two complementary measurements:
 * 1. [resolveViaSystem] — what apps actually experience: the system resolver chain.
 * 2. [probeResolvers] — a direct UDP query to each resolver advertised in
 *    [LinkProperties], isolating individual resolvers from the system path.
 */
class DnsChecker(
    private val localInfoProvider: suspend () -> LocalNetworkInfo,
) {

    suspend fun check(hostname: String = DEFAULT_HOSTNAME, timeoutMs: Int = 2_000): DnsResult =
        withContext(Dispatchers.IO) {
            val info = localInfoProvider()
            val resolverResults = probeResolvers(info, hostname, timeoutMs)
            ensureActive()
            val systemResult = resolveViaSystem(hostname)
            DnsResult(
                hostname = hostname,
                resolvers = resolverResults,
                systemResolution = systemResult,
            )
        }

    private suspend fun probeResolvers(
        info: LocalNetworkInfo,
        hostname: String,
        timeoutMs: Int,
    ): List<DnsResolverResult> {
        if (info.dnsServers.isEmpty()) return emptyList()
        return info.dnsServers.map { server ->
            coroutineContext.ensureActive()
            val address = runCatching { InetAddress.getByName(server) }.getOrNull()
            if (address == null) {
                DnsResolverResult(server, success = false, latencyMs = null, error = "Invalid resolver address")
            } else {
                when (val outcome = DnsUdpClient.queryA(address, hostname, timeoutMs)) {
                    is DnsUdpClient.QueryOutcome.Success -> DnsResolverResult(
                        address = server,
                        success = true,
                        latencyMs = outcome.latencyMs,
                        resolvedIps = outcome.ips,
                    )
                    is DnsUdpClient.QueryOutcome.Failure -> DnsResolverResult(
                        address = server,
                        success = false,
                        latencyMs = outcome.latencyMs,
                        error = outcome.error,
                    )
                }
            }
        }
    }

    private fun resolveViaSystem(hostname: String): DnsSystemResult {
        val start = System.nanoTime()
        return try {
            val addresses = InetAddress.getAllByName(hostname)
            DnsSystemResult(
                success = addresses.isNotEmpty(),
                latencyMs = (System.nanoTime() - start) / 1_000_000,
                resolvedCount = addresses.size,
            )
        } catch (e: UnknownHostException) {
            DnsSystemResult(
                success = false,
                latencyMs = (System.nanoTime() - start) / 1_000_000,
                resolvedCount = 0,
                error = e.message ?: "Unknown host",
            )
        }
    }

    private companion object {
        const val DEFAULT_HOSTNAME = "connectivitycheck.gstatic.com"
    }
}
