package com.noise.netpulse.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noise.netpulse.domain.model.DiagnosticReport
import com.noise.netpulse.domain.model.DiagnosticStep
import com.noise.netpulse.domain.model.StepStatus

/**
 * Diagnostic detail screen (Phase 13): every pipeline step with its verdict;
 * tapping a step expands the underlying measurements.
 */
@Composable
fun DetailScreen(report: DiagnosticReport?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Network Diagnostic", style = MaterialTheme.typography.headlineSmall)

        if (report == null) {
            Text(
                text = "Run a diagnostic first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        DiagnosticStep.entries.forEach { step ->
            val status = report.stepStatuses[step] ?: StepStatus.UNAVAILABLE
            StepCard(step, status, report)
        }
    }
}

@Composable
private fun StepCard(step: DiagnosticStep, status: StepStatus, report: DiagnosticReport) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = statusSymbol(status) + " " + stepTitle(step),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (expanded) {
                StepDetails(step, status, report)
            }
        }
    }
}

private fun statusSymbol(status: StepStatus): String = when (status) {
    StepStatus.PASSED -> "✓"
    StepStatus.WARNING -> "⚠"
    StepStatus.FAILED -> "✕"
    StepStatus.UNAVAILABLE -> "–"
    StepStatus.SKIPPED -> "…"
}

private fun stepTitle(step: DiagnosticStep): String = when (step) {
    DiagnosticStep.CONNECTION -> "Connection"
    DiagnosticStep.GATEWAY -> "Gateway"
    DiagnosticStep.DNS -> "DNS"
    DiagnosticStep.INTERNET -> "Internet"
    DiagnosticStep.HTTPS -> "HTTPS"
    DiagnosticStep.LATENCY -> "Latency"
    DiagnosticStep.RELIABILITY -> "Reliability"
}

@Composable
private fun StepDetails(step: DiagnosticStep, status: StepStatus, report: DiagnosticReport) {
    val body = when (step) {
        DiagnosticStep.CONNECTION -> connectionDetails(report, status)
        DiagnosticStep.GATEWAY -> gatewayDetails(report, status)
        DiagnosticStep.DNS -> dnsDetails(report, status)
        DiagnosticStep.INTERNET -> internetDetails(report, status)
        DiagnosticStep.HTTPS -> httpsDetails(report, status)
        DiagnosticStep.LATENCY -> latencyDetails(report, status)
        DiagnosticStep.RELIABILITY -> reliabilityDetails(report, status)
    }
    Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun connectionDetails(report: DiagnosticReport, status: StepStatus): String {
    if (status == StepStatus.SKIPPED || status == StepStatus.UNAVAILABLE) {
        return "Could not be determined on this device."
    }
    return "Network type: ${report.networkType}. " +
        "State: ${report.connectionState}. " +
        "Duration of diagnostic run: ${report.durationMs} ms."
}

private fun gatewayDetails(report: DiagnosticReport, status: StepStatus): String {
    val gateway = report.gateway ?: return "Unavailable on this device " +
        "(some networks, e.g. mobile, do not expose a probeable gateway)."
    return buildString {
        append("Address: ${gateway.address ?: "unknown"}. Method: ${gateway.method ?: "none"}.\n")
        append("Reachable: ${gateway.reachable}. Latency: ${gateway.latencyMs ?: "n/a"} ms.\n")
        gateway.error?.let { append("Error: $it.\n") }
        report.findings.filter { it.step == com.noise.netpulse.domain.model.DiagnosticStep.GATEWAY }
            .forEach { append("→ ${it.title}: ${it.evidence} ${it.possibleCause}\n") }
    }.trim()
}

private fun dnsDetails(report: DiagnosticReport, status: StepStatus): String {
    val dns = report.dns ?: return "Unavailable on this device."
    return buildString {
        append("Tested hostname: ${dns.hostname}.\n")
        dns.systemResolution?.let {
            append("System resolution: ${if (it.success) "✓" else "✕"} ${it.latencyMs ?: "?"} ms, " +
                "${it.resolvedCount} address(es). ${it.error ?: ""}\n")
        }
        dns.resolvers.forEach { r ->
            append("Resolver ${r.address}: ${if (r.success) "✓" else "✕"} " +
                "${r.latencyMs ?: "?"} ms${r.resolvedIps.takeIf { it.isNotEmpty() }?.let { " → ${it.joinToString()}" } ?: ""}" +
                "${r.error?.let { " ($it)" } ?: ""}\n")
        }
    }.trim()
}

private fun internetDetails(report: DiagnosticReport, status: StepStatus): String {
    val internet = report.internet ?: return "Unavailable on this device."
    return internet.probes.joinToString("\n") { p ->
        "${if (p.success) "✓" else "✕"} ${p.url} — ${p.statusCode ?: "-"} ${p.latencyMs ?: "?"} ms${p.error?.let { " ($it)" } ?: ""}"
    }
}

private fun httpsDetails(report: DiagnosticReport, status: StepStatus): String {
    val https = report.https ?: return "Unavailable on this device."
    return https.probes.joinToString("\n") { p ->
        "${if (p.success) "✓" else "✕"} ${p.url} — ${p.statusCode ?: "-"} ${p.latencyMs ?: "?"} ms" +
            "${p.errorType?.let { " [${it.name}]" } ?: ""}${p.error?.let { " ($it)" } ?: ""}"
    }
}

private fun latencyDetails(report: DiagnosticReport, status: StepStatus): String = buildString {
    append("Gateway: ${report.gateway?.latencyMs ?: "n/a"} ms\n")
    append("DNS: ${report.dns?.systemResolution?.latencyMs ?: "n/a"} ms\n")
    append("Internet (best endpoint): ${report.https?.bestLatencyMs ?: "n/a"} ms\n")
    append("Thresholds are heuristics, documented in docs/network-diagnostics.md.")
}

private fun reliabilityDetails(report: DiagnosticReport, status: StepStatus): String {
    val r = report.reliability ?: return "Unavailable on this device."
    return buildString {
        append("Requests: ${r.requests}\n")
        append("Successful: ${r.successful}\n")
        append("Failed: ${r.failed} (${r.timeouts} timeouts)\n")
        append("Success rate: ${r.successRatePercent ?: "n/a"}%\n")
        append("Jitter (stddev of successful requests): ${r.jitterMs ?: "n/a"} ms\n")
        append("Median latency: ${r.medianLatencyMs ?: "n/a"} ms\n")
        append("Note: this is application-level request reliability, not packet loss.")
    }.trim()
}
