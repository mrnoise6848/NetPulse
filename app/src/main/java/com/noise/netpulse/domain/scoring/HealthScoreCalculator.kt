package com.noise.netpulse.domain.scoring

import com.noise.netpulse.domain.engine.LatencyAnalyzer
import com.noise.netpulse.domain.engine.LatencyGrade
import com.noise.netpulse.domain.model.ReliabilityResult
import com.noise.netpulse.domain.model.ConnectionState
import com.noise.netpulse.domain.model.DiagnosticReport
import com.noise.netpulse.domain.model.GatewayResult
import com.noise.netpulse.domain.model.DnsResult
import com.noise.netpulse.domain.model.HealthScore
import com.noise.netpulse.domain.model.HttpsResult
import com.noise.netpulse.domain.model.NetworkType
import com.noise.netpulse.domain.model.NetworkSnapshot

/**
 * Network health score (Phase 11).
 *
 * Deterministic and explainable — every component's contribution is listed.
 * Formula (documented in docs/diagnostic-engine.md):
 *
 *   Connectivity  /25  — active network + OS validation state
 *   Gateway       /15  — local first hop reachability & latency
 *   DNS           /20  — resolution success & latency
 *   Latency       /20  — best internet endpoint latency
 *   HTTPS         /20  — request reliability over repeated probes
 *
 * Unverifiable components receive a neutral partial credit, never a fake
 * perfect score; the components list shows exactly why.
 */
object HealthScoreCalculator {

    fun calculate(
        network: NetworkSnapshot?,
        gateway: GatewayResult?,
        dns: DnsResult?,
        https: HttpsResult?,
        reliability: ReliabilityResult?,
    ): HealthScore {
        val components = listOf(
            connectivityScore(network),
            gatewayScore(network, gateway),
            dnsScore(dns),
            latencyScore(https),
            httpsScore(reliability),
        )
        val total = components.sumOf { it.score }
        return HealthScore(total = total, maxTotal = components.sumOf { it.max }, components = components)
    }

    fun calculate(report: DiagnosticReport): HealthScore =
        calculate(
            network = NetworkSnapshot(report.networkType, report.connectionState),
            gateway = report.gateway,
            dns = report.dns,
            https = report.https,
            reliability = report.reliability,
        )

    private fun connectivityScore(network: NetworkSnapshot?): HealthScore.Component {
        val score = when {
            network == null || network.state == ConnectionState.DISCONNECTED -> 0
            network.state == ConnectionState.CONNECTED -> 25
            else -> 15
        }
        return HealthScore.Component("Connectivity", score, 25)
    }

    private fun gatewayScore(network: NetworkSnapshot?, gateway: GatewayResult?): HealthScore.Component {
        val score = when {
            // Mobile networks do not expose a probeable gateway: neutral credit, not a penalty.
            network != null && network.type == NetworkType.MOBILE -> 10
            gateway == null -> 10
            !gateway.reachable -> 0
            LatencyAnalyzer.classifyLocal(gateway.latencyMs) in setOf(
                LatencyGrade.EXCELLENT,
                LatencyGrade.GOOD,
                LatencyGrade.FAIR,
            ) -> 15
            else -> 10
        }
        return HealthScore.Component("Gateway", score, 15)
    }

    private fun dnsScore(dns: DnsResult?): HealthScore.Component {
        val system = dns?.systemResolution
        val score = when {
            dns == null || system == null -> 10
            !system.success && dns.resolvers.none { it.success } -> 0
            !system.success -> 8
            else -> {
                val latency = system.latencyMs
                when (LatencyAnalyzer.classifyInternet(latency)) {
                    LatencyGrade.EXCELLENT, LatencyGrade.GOOD, null -> 20
                    LatencyGrade.FAIR -> 16
                    LatencyGrade.HIGH -> 10
                    LatencyGrade.VERY_HIGH -> 4
                    else -> 12
                }
            }
        }
        return HealthScore.Component("DNS", score, 20)
    }

    private fun latencyScore(https: HttpsResult?): HealthScore.Component {
        val score = when {
            // Internet probes ran and all failed: latency genuinely failed, not "unverifiable".
            https != null && !https.works -> 0
            else -> when (LatencyAnalyzer.classifyInternet(https?.bestLatencyMs)) {
                LatencyGrade.EXCELLENT -> 20
                LatencyGrade.GOOD -> 17
                LatencyGrade.FAIR -> 12
                LatencyGrade.HIGH -> 6
                LatencyGrade.VERY_HIGH -> 0
                null -> 10
            }
        }
        return HealthScore.Component("Latency", score, 20)
    }

    private fun httpsScore(reliability: ReliabilityResult?): HealthScore.Component {
        val score = when {
            reliability == null -> 10
            reliability.allTimeouts -> 0
            else -> when (val rate = reliability.successRatePercent ?: 0.0) {
                in 95.0..100.0 -> 20
                in 80.0..<95.0 -> 14
                in 50.0..<80.0 -> 8
                in 0.0..<50.0 -> 4
                else -> 4
            }
        }
        return HealthScore.Component("HTTPS reliability", score, 20)
    }
}
