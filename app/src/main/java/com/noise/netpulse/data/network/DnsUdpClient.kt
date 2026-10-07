package com.noise.netpulse.data.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.random.Random

/**
 * Minimal RFC-1035 DNS A-query over UDP, used to probe each configured resolver
 * directly (unlike InetAddress, which goes through the system resolver chain).
 *
 * Implementation notes:
 * - One question, type A (1), class IN (1), recursion desired.
 * - Success requires RCODE 0 and at least one answer A record; NXDOMAIN or
 *   any parse/transport failure is reported as a failed query.
 */
object DnsUdpClient {

    private const val TYPE_A = 1
    private const val RCODE_NO_ERROR = 0

    sealed interface QueryOutcome {
        data class Success(val latencyMs: Long, val ips: List<String>) : QueryOutcome
        data class Failure(val error: String, val latencyMs: Long) : QueryOutcome
    }

    fun queryA(resolverAddress: InetAddress, hostname: String, timeoutMs: Int = 2_000): QueryOutcome {
        val id = Random.nextInt(0, 65_536)
        val query = buildQuery(id, hostname)
        val start = System.nanoTime()
        val socket = DatagramSocket()
        try {
            socket.soTimeout = timeoutMs
            socket.send(
                DatagramPacket(query, query.size, resolverAddress, 53),
            )
            val buffer = ByteArray(1_500)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val latencyMs = (System.nanoTime() - start) / 1_000_000
            return parseResponse(buffer, response.length, id, latencyMs)
        } catch (_: SocketTimeoutException) {
            return QueryOutcome.Failure(
                error = "No response within ${timeoutMs}ms",
                latencyMs = (System.nanoTime() - start) / 1_000_000,
            )
        } catch (e: Exception) {
            return QueryOutcome.Failure(
                error = e.message ?: "DNS query failed",
                latencyMs = (System.nanoTime() - start) / 1_000_000,
            )
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun buildQuery(id: Int, hostname: String): ByteArray {
        val labels = hostname.trim().trimEnd('.').split('.')
        val body = mutableListOf<Byte>()
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            if (bytes.isEmpty() || bytes.size > 63) throw IllegalArgumentException("Invalid label in $hostname")
            body += bytes.size.toByte()
            body += bytes.toList()
        }
        body += 0.toByte()

        val packet = ByteArray(12 + body.size + 4)
        // Header: ID, flags (recursion desired), QDCOUNT=1, rest 0.
        packet[0] = ((id ushr 8) and 0xFF).toByte()
        packet[1] = (id and 0xFF).toByte()
        packet[2] = 0x01
        packet[3] = 0x00
        packet[4] = 0
        packet[5] = 1
        var offset = 12
        for (b in body) packet[offset++] = b
        // QTYPE = A, QCLASS = IN
        packet[offset++] = 0
        packet[offset++] = TYPE_A.toByte()
        packet[offset++] = 0
        packet[offset++] = 1
        return packet
    }

    private fun parseResponse(data: ByteArray, length: Int, expectedId: Int, latencyMs: Long): QueryOutcome {
        if (length < 12) return QueryOutcome.Failure("Response too short", latencyMs)
        val id = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        if (id != expectedId) return QueryOutcome.Failure("Transaction ID mismatch", latencyMs)
        val flags = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        val isResponse = (flags and 0x8000) != 0
        if (!isResponse) return QueryOutcome.Failure("Not a DNS response", latencyMs)
        val rcode = flags and 0x000F
        if (rcode != RCODE_NO_ERROR) {
            return QueryOutcome.Failure("DNS error code $rcode", latencyMs)
        }
        val answerCount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        if (answerCount == 0) return QueryOutcome.Failure("No answer records", latencyMs)

        var offset = 12
        val questionCount = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        repeat(questionCount) {
            offset = skipName(data, offset)
            offset += 4 // QTYPE + QCLASS
        }

        val ips = mutableListOf<String>()
        repeat(answerCount) {
            offset = skipName(data, offset)
            val type = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            val rdLength = ((data[offset + 8].toInt() and 0xFF) shl 8) or (data[offset + 9].toInt() and 0xFF)
            val rdataStart = offset + 10
            if (type == TYPE_A && rdLength == 4 && rdataStart + 4 <= length) {
                ips += "${data[rdataStart].toInt() and 0xFF}.${data[rdataStart + 1].toInt() and 0xFF}." +
                    "${data[rdataStart + 2].toInt() and 0xFF}.${data[rdataStart + 3].toInt() and 0xFF}"
            }
            offset = rdataStart + rdLength
        }

        return if (ips.isEmpty()) {
            QueryOutcome.Failure("No A records in answer", latencyMs)
        } else {
            QueryOutcome.Success(latencyMs, ips)
        }
    }

    /** Skips a DNS name (handles compression pointers), returns offset after it. */
    private fun skipName(data: ByteArray, start: Int): Int {
        var offset = start
        var jumped = false
        var endOffset = start
        var guard = 0
        while (true) {
            check(guard++ < 64) { "Malformed name" }
            val len = data[offset].toInt() and 0xFF
            if (len == 0) {
                if (!jumped) endOffset = offset + 1
                return endOffset
            }
            if (len and 0xC0 == 0xC0) {
                if (!jumped) endOffset = offset + 2
                return endOffset
            }
            if (!jumped) offset += 1 + len
        }
    }
}
