package com.noise.netpulse.data.network

import com.noise.netpulse.domain.model.GatewayResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Gateway reachability probe.
 *
 * ICMP is not universally available to unprivileged apps, so the checker:
 * 1. attempts ICMP via [InetAddress.isReachable] (works where the OS permits it),
 * 2. otherwise falls back to TCP connects on common router ports (53, 80, 443).
 *
 * The method actually used is recorded in the result and shown to the user.
 */
class GatewayChecker {

    suspend fun probe(gatewayAddress: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): GatewayResult =
        withContext(Dispatchers.IO) {
            val address = runCatching { InetAddress.getByName(gatewayAddress) }.getOrNull()
                ?: return@withContext GatewayResult(
                    address = gatewayAddress,
                    reachable = false,
                    latencyMs = null,
                    method = null,
                    error = "Address could not be resolved",
                )

            val icmp = icmpProbe(address, timeoutMs)
            if (icmp != null) {
                return@withContext GatewayResult(
                    address = gatewayAddress,
                    reachable = true,
                    latencyMs = icmp,
                    method = "ICMP echo",
                )
            }

            coroutineContext.ensureActive()
            tcpProbe(gatewayAddress, timeoutMs)
        }

    /** Returns ICMP latency in ms when the echo worked, otherwise null. */
    private fun icmpProbe(address: InetAddress, timeoutMs: Int): Long? {
        val start = System.nanoTime()
        val reachable = runCatching { address.isReachable(timeoutMs) }.getOrDefault(false)
        val elapsed = (System.nanoTime() - start) / 1_000_000
        return if (reachable) elapsed else null
    }

    private suspend fun tcpProbe(address: String, timeoutMs: Int): GatewayResult {
        var lastError: String? = null
        for (port in TCP_PORTS) {
            coroutineContext.ensureActive()
            val socket = Socket()
            try {
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(address, port), timeoutMs)
                val elapsed = (System.nanoTime() - start) / 1_000_000
                return GatewayResult(
                    address = address,
                    reachable = true,
                    latencyMs = elapsed,
                    method = "TCP connect :$port",
                )
            } catch (_: SocketTimeoutException) {
                lastError = "Timeout connecting to port $port"
            } catch (e: Exception) {
                lastError = e.message ?: "Connection to port $port refused"
            } finally {
                runCatching { socket.close() }
            }
        }
        return GatewayResult(
            address = address,
            reachable = false,
            latencyMs = null,
            method = "TCP connect (ports ${TCP_PORTS.joinToString("/")})",
            error = lastError,
        )
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 2_000
        val TCP_PORTS = intArrayOf(53, 80, 443)
    }
}
