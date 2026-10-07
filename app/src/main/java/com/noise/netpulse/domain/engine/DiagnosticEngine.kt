package com.noise.netpulse.domain.engine

import com.noise.netpulse.domain.model.ConnectionState
import com.noise.netpulse.domain.model.DiagnosticFinding
import com.noise.netpulse.domain.model.DiagnosticReport
import com.noise.netpulse.domain.model.DiagnosticStep
import com.noise.netpulse.domain.model.DnsResult
import com.noise.netpulse.domain.model.GatewayResult
import com.noise.netpulse.domain.model.HttpErrorType
import com.noise.netpulse.domain.model.HttpsResult
import com.noise.netpulse.domain.model.InternetResult
import com.noise.netpulse.domain.model.NetworkType
import com.noise.netpulse.domain.model.NetworkSnapshot
import com.noise.netpulse.domain.model.ReliabilityResult
import com.noise.netpulse.domain.model.Severity
import com.noise.netpulse.domain.model.StepStatus

/**
 * Deterministic diagnostic engine (Phase 10).
 *
 * Combines measured evidence from all checkers into findings using fixed rules.
 * Every finding states [DiagnosticFinding.evidence] and a hedged
 * [DiagnosticFinding.possibleCause]; the engine never asserts certainty that
 * the evidence does not support. Pure domain logic — no Android imports.
 */
object DiagnosticEngine {

    /** Latencies above this are considered "high" for DNS responses. */
    private const val HIGH_DNS_LATENCY_MS = 200L

    /** Request success rate below this is considered unreliable. */
    private const val LOW_SUCCESS_RATE_PERCENT = 90.0

    fun diagnose(
        network: NetworkSnapshot?,
        gateway: GatewayResult?,
        dns: DnsResult?,
        internet: InternetResult?,
        https: HttpsResult?,
        reliability: ReliabilityResult?,
        epochNowMs: Long,
        durationMs: Long,
        abortedDueToNetworkChange: Boolean = false,
    ): DiagnosticReport {
        val findings = mutableListOf<DiagnosticFinding>()
        val statuses = mutableMapOf<DiagnosticStep, StepStatus>()

        val connected = network != null &&
            network.state != ConnectionState.DISCONNECTED &&
            network.type != NetworkType.NONE

        if (!connected) {
            statuses[DiagnosticStep.CONNECTION] = StepStatus.FAILED
            findings += noNetworkFinding()
            // Remaining steps cannot run without a network.
            DiagnosticStep.entries
                .filter { it != DiagnosticStep.CONNECTION }
                .forEach { statuses[it] = StepStatus.SKIPPED }
            return assemble(
                network = network,
                gateway = null,
                dns = null,
                internet = null,
                https = null,
                reliability = null,
                findings = findings,
                statuses = statuses,
                epochNowMs = epochNowMs,
                durationMs = durationMs,
                abortedDueToNetworkChange = abortedDueToNetworkChange,
            )
        }

        statuses[DiagnosticStep.CONNECTION] = if (network.state == ConnectionState.CONNECTED) {
            StepStatus.PASSED
        } else {
            StepStatus.WARNING
        }

        evaluateGateway(gateway, findings, statuses)
        evaluateDns(dns, findings, statuses)
        evaluateInternet(internet, findings, statuses)
        evaluateHttps(https, findings, statuses)
        evaluateLatency(gateway, dns, https, findings, statuses)
        evaluateReliability(reliability, findings, statuses)

        return assemble(
            network = network,
            gateway = gateway,
            dns = dns,
            internet = internet,
            https = https,
            reliability = reliability,
            findings = findings,
            statuses = statuses,
            epochNowMs = epochNowMs,
            durationMs = durationMs,
            abortedDueToNetworkChange = abortedDueToNetworkChange,
        )
    }

    private fun noNetworkFinding() = DiagnosticFinding(
        step = DiagnosticStep.CONNECTION,
        severity = Severity.ERROR,
        title = "No active network connection",
        evidence = "Android reports no active network interface.",
        possibleCause = "Wi-Fi or mobile data may be turned off, or airplane mode may be enabled.",
    )

    private fun evaluateGateway(
        gateway: GatewayResult?,
        findings: MutableList<DiagnosticFinding>,
        statuses: MutableMap<DiagnosticStep, StepStatus>,
    ) {
        if (gateway == null) {
            statuses[DiagnosticStep.GATEWAY] = StepStatus.UNAVAILABLE
            return
        }
        val grade = LatencyAnalyzer.classifyLocal(gateway.latencyMs)
        when {
            !gateway.reachable -> {
                statuses[DiagnosticStep.GATEWAY] = StepStatus.FAILED
                findings += DiagnosticFinding(
                    step = DiagnosticStep.GATEWAY,
                    severity = Severity.ERROR,
                    title = "Gateway not reachable",
                    evidence = buildString {
                        append("Could not reach gateway ${gateway.address ?: "?"} via ${gateway.method ?: "any method"}")
                        gateway.error?.let { append(" — $it") }
                    },
                    possibleCause = "Possible local network or router connectivity problem.",
                    detail = "Reachability method: ${gateway.method ?: "none succeeded"}. " +
                        "Note: some networks do not answer these probes, so this suggests rather than proves a problem.",
                )
            }
            grade == LatencyGrade.HIGH || grade == LatencyGrade.VERY_HIGH -> {
                statuses[DiagnosticStep.GATEWAY] = StepStatus.WARNING
                findings += DiagnosticFinding(
                    step = DiagnosticStep.GATEWAY,
                    severity = Severity.WARNING,
                    title = "High gateway latency",
                    evidence = "Gateway ${gateway.address}: ${gateway.latencyMs} ms (${gateway.method}).",
                    possibleCause = "Possible local network congestion or router load.",
                )
            }
            else -> {
                statuses[DiagnosticStep.GATEWAY] = StepStatus.PASSED
                findings += DiagnosticFinding(
                    step = DiagnosticStep.GATEWAY,
                    severity = Severity.OK,
                    title = "Gateway reachable",
                    evidence = "Gateway ${gateway.address}: ${gateway.latencyMs} ms (${gateway.method}).",
                    possibleCause = "—",
                )
            }
        }
    }

    private fun evaluateDns(
        dns: DnsResult?,
        findings: MutableList<DiagnosticFinding>,
        statuses: MutableMap<DiagnosticStep, StepStatus>,
    ) {
        if (dns == null) {
            statuses[DiagnosticStep.DNS] = StepStatus.UNAVAILABLE
            return
        }
        val system = dns.systemResolution
        val resolverSuccesses = dns.resolvers.filter { it.success }
        val anyResolverOk = resolverSuccesses.isNotEmpty()

        when {
            (system == null || !system.success) && !anyResolverOk -> {
                statuses[DiagnosticStep.DNS] = StepStatus.FAILED
                findings += DiagnosticFinding(
                    step = DiagnosticStep.DNS,
                    severity = Severity.ERROR,
                    title = "DNS resolution failed",
                    evidence = buildString {
                        append("Hostname ${dns.hostname} did not resolve")
                        system?.error?.let { append(" — $it") }
                        if (dns.resolvers.isNotEmpty()) {
                            append("; ${dns.resolvers.size} configured resolver(s) did not answer")
                        }
                    },
                    possibleCause = "Possible DNS service unavailability or DNS misconfiguration on the network.",
                )
            }
            (system == null || !system.success) && anyResolverOk -> {
                statuses[DiagnosticStep.DNS] = StepStatus.WARNING
                findings += DiagnosticFinding(
                    step = DiagnosticStep.DNS,
                    severity = Severity.WARNING,
                    title = "System DNS path failed, but a direct resolver responded",
                    evidence = "Direct query succeeded on ${resolverSuccesses.map { it.address }.joinToString()}, " +
                        "system resolution failed.",
                    possibleCause = "Suggests a DNS configuration issue on this device or network rather than a dead resolver.",
                )
            }
            else -> {
                val latency = system?.latencyMs
                val grade = LatencyAnalyzer.classifyInternet(latency)
                if (grade == LatencyGrade.HIGH || grade == LatencyGrade.VERY_HIGH ||
                    (latency ?: 0L) > HIGH_DNS_LATENCY_MS
                ) {
                    statuses[DiagnosticStep.DNS] = StepStatus.WARNING
                    findings += DiagnosticFinding(
                        step = DiagnosticStep.DNS,
                        severity = Severity.WARNING,
                        title = "High DNS latency",
                        evidence = "DNS response: $latency ms",
                        possibleCause = "Possible slow or overloaded DNS resolver.",
                    )
                } else {
                    statuses[DiagnosticStep.DNS] = StepStatus.PASSED
                    findings += DiagnosticFinding(
                        step = DiagnosticStep.DNS,
                        severity = Severity.OK,
                        title = "DNS resolution works",
                        evidence = "DNS response: $latency ms; " +
                            "${resolverSuccesses.size}/${dns.resolvers.size} configured resolvers responded.",
                        possibleCause = "—",
                    )
                }
            }
        }
    }

    private fun evaluateInternet(
        internet: InternetResult?,
        findings: MutableList<DiagnosticFinding>,
        statuses: MutableMap<DiagnosticStep, StepStatus>,
    ) {
        if (internet == null) {
            statuses[DiagnosticStep.INTERNET] = StepStatus.UNAVAILABLE
            return
        }
        val total = internet.probes.size
        when {
            !internet.reachable -> {
                statuses[DiagnosticStep.INTERNET] = StepStatus.FAILED
                findings += DiagnosticFinding(
                    step = DiagnosticStep.INTERNET,
                    severity = Severity.ERROR,
                    title = "No reachable internet endpoint",
                    evidence = "0 of $total endpoints responded.",
                    possibleCause = "Possible upstream connectivity problem between the local network and the internet.",
                )
            }
            internet.successCount < total -> {
                statuses[DiagnosticStep.INTERNET] = StepStatus.WARNING
                findings += DiagnosticFinding(
                    step = DiagnosticStep.INTERNET,
                    severity = Severity.WARNING,
                    title = "Some internet endpoints unreachable",
                    evidence = "${internet.successCount} of $total endpoints responded.",
                    possibleCause = "Possible filtering, captive portal, or a problem with one endpoint; local connectivity may be fine.",
                )
            }
            else -> {
                statuses[DiagnosticStep.INTERNET] = StepStatus.PASSED
            }
        }
    }

    private fun evaluateHttps(
        https: HttpsResult?,
        findings: MutableList<DiagnosticFinding>,
        statuses: MutableMap<DiagnosticStep, StepStatus>,
    ) {
        if (https == null) {
            statuses[DiagnosticStep.HTTPS] = StepStatus.UNAVAILABLE
            return
        }
        when {
            !https.works -> {
                statuses[DiagnosticStep.HTTPS] = StepStatus.FAILED
                findings += DiagnosticFinding(
                    step = DiagnosticStep.HTTPS,
                    severity = Severity.ERROR,
                    title = when (https.primaryFailure) {
                        HttpErrorType.TLS_FAILURE -> "HTTPS connections fail with TLS errors"
                        HttpErrorType.DNS_FAILURE -> "HTTPS connections fail at DNS"
                        HttpErrorType.TIMEOUT -> "HTTPS connections time out"
                        else -> "HTTPS connections fail"
                    },
                    evidence = "0 of ${https.probes.size} HTTPS endpoints succeeded" +
                        (https.primaryFailure?.let { " — failure type: $it" } ?: ""),
                    possibleCause = when (https.primaryFailure) {
                        HttpErrorType.TLS_FAILURE ->
                            "Possible TLS/certificate problem, e.g. interception or wrong system clock."
                        HttpErrorType.DNS_FAILURE ->
                            "Possible DNS problem affecting hostname resolution for HTTPS."
                        else ->
                            "Possible network filtering or connectivity problem after DNS."
                    },
                    detail = "Connection appears locally established, but secure connections fail — " +
                        "the problem is most likely above the DNS layer.",
                )
            }
            https.failureCount > 0 -> {
                statuses[DiagnosticStep.HTTPS] = StepStatus.WARNING
                findings += DiagnosticFinding(
                    step = DiagnosticStep.HTTPS,
                    severity = Severity.WARNING,
                    title = "Intermittent HTTPS failures",
                    evidence = "${https.successCount} of ${https.probes.size} HTTPS endpoints succeeded.",
                    possibleCause = "Possible unstable connection or selective filtering.",
                )
            }
            else -> {
                statuses[DiagnosticStep.HTTPS] = StepStatus.PASSED
            }
        }
    }

    private fun evaluateLatency(
        gateway: GatewayResult?,
        dns: DnsResult?,
        https: HttpsResult?,
        findings: MutableList<DiagnosticFinding>,
        statuses: MutableMap<DiagnosticStep, StepStatus>,
    ) {
        val internetLatency = https?.bestLatencyMs
        val internetGrade = LatencyAnalyzer.classifyInternet(internetLatency)
        val gatewayGrade = LatencyAnalyzer.classifyLocal(gateway?.latencyMs)
        val dnsLatency = dns?.systemResolution?.latencyMs
        val dnsGrade = LatencyAnalyzer.classifyLocal(dnsLatency)

        if (gateway == null && dns == null && https == null) {
            statuses[DiagnosticStep.LATENCY] = StepStatus.UNAVAILABLE
            return
        }

        val badGrades = listOf(gatewayGrade, dnsGrade, internetGrade).filter {
            it == LatencyGrade.HIGH || it == LatencyGrade.VERY_HIGH
        }
        val measured = buildString {
            append("Gateway: ${gateway?.latencyMs?.toString() ?: "n/a"} ms, ")
            append("DNS: ${dnsLatency?.toString() ?: "n/a"} ms, ")
            append("Internet: ${internetLatency?.toString() ?: "n/a"} ms")
        }

        if (badGrades.isNotEmpty()) {
            statuses[DiagnosticStep.LATENCY] = StepStatus.WARNING
            findings += DiagnosticFinding(
                step = DiagnosticStep.LATENCY,
                severity = Severity.WARNING,
                title = "High latency detected",
                evidence = measured,
                possibleCause = "Possible network congestion or a long, slow route to the tested endpoints.",
            )
        } else {
            statuses[DiagnosticStep.LATENCY] = StepStatus.PASSED
        }
    }

    private fun evaluateReliability(
        reliability: ReliabilityResult?,
        findings: MutableList<DiagnosticFinding>,
        statuses: MutableMap<DiagnosticStep, StepStatus>,
    ) {
        if (reliability == null) {
            statuses[DiagnosticStep.RELIABILITY] = StepStatus.UNAVAILABLE
            return
        }
        val rate = reliability.successRatePercent ?: 0.0
        when {
            reliability.allTimeouts -> {
                statuses[DiagnosticStep.RELIABILITY] = StepStatus.WARNING
                findings += DiagnosticFinding(
                    step = DiagnosticStep.RELIABILITY,
                    severity = Severity.WARNING,
                    title = "All repeated requests timed out",
                    evidence = "0 of ${reliability.requests} repeated requests succeeded.",
                    possibleCause = "Suggests unstable connectivity or aggressive filtering; " +
                        "this is application-level, not measured packet loss.",
                )
            }
            rate < LOW_SUCCESS_RATE_PERCENT -> {
                statuses[DiagnosticStep.RELIABILITY] = StepStatus.WARNING
                findings += DiagnosticFinding(
                    step = DiagnosticStep.RELIABILITY,
                    severity = Severity.WARNING,
                    title = "Unstable request reliability",
                    evidence = "${reliability.successful} of ${reliability.requests} requests succeeded " +
                        "(${formatPercent(rate)}), ${reliability.timeouts} timed out.",
                    possibleCause = "Possible intermittent connectivity. " +
                        "Note: request failures can also come from TLS or server behaviour, not only path loss.",
                )
            }
            else -> {
                statuses[DiagnosticStep.RELIABILITY] = StepStatus.PASSED
            }
        }
    }

    private fun assemble(
        network: NetworkSnapshot?,
        gateway: GatewayResult?,
        dns: DnsResult?,
        internet: InternetResult?,
        https: HttpsResult?,
        reliability: ReliabilityResult?,
        findings: List<DiagnosticFinding>,
        statuses: Map<DiagnosticStep, StepStatus>,
        epochNowMs: Long,
        durationMs: Long,
        abortedDueToNetworkChange: Boolean,
    ): DiagnosticReport {
        val summary = when {
            abortedDueToNetworkChange ->
                "Diagnostic was interrupted: the network changed during the test."
            findings.none { it.severity == Severity.ERROR || it.severity == Severity.WARNING } ->
                "Local and internet connectivity look healthy."
            else -> {
                val worst = findings.firstOrNull { it.severity == Severity.ERROR }
                    ?: findings.firstOrNull { it.severity == Severity.WARNING }
                when (worst?.severity) {
                    Severity.ERROR -> "Problem detected: ${worst.title.lowercase()}."
                    Severity.WARNING -> "${worst.title}: ${worst.possibleCause}"
                    else -> "Local and internet connectivity look healthy."
                }
            }
        }
        return DiagnosticReport(
            startedAtEpochMs = epochNowMs - durationMs,
            durationMs = durationMs,
            networkType = network?.type ?: NetworkType.UNKNOWN,
            connectionState = network?.state ?: ConnectionState.UNKNOWN,
            gateway = gateway,
            dns = dns,
            internet = internet,
            https = https,
            reliability = reliability,
            findings = findings,
            summary = summary,
            stepStatuses = statuses,
            abortedDueToNetworkChange = abortedDueToNetworkChange,
        )
    }

    private fun formatPercent(value: Double): String {
        val rounded = (value * 10).toLong() / 10.0
        return if (rounded % 1.0 == 0.0) "${rounded.toInt()}%" else "$rounded%"
    }
}
