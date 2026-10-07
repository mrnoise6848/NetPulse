package com.noise.netpulse.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noise.netpulse.domain.engine.LatencyAnalyzer
import com.noise.netpulse.domain.engine.LatencyGrade
import com.noise.netpulse.domain.model.ConnectionState
import com.noise.netpulse.domain.model.DiagnosticReport
import com.noise.netpulse.domain.model.DiagnosticStep
import com.noise.netpulse.domain.model.LocalNetworkInfo
import com.noise.netpulse.domain.model.NetworkSnapshot
import com.noise.netpulse.domain.model.label

@Composable
fun MainScreen(
    network: NetworkSnapshot?,
    localInfo: LocalNetworkInfo,
    running: Boolean,
    currentStep: DiagnosticStep?,
    report: DiagnosticReport?,
    onRunDiagnostic: () -> Unit,
    onCancelDiagnostic: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "NetPulse", style = MaterialTheme.typography.headlineMedium)

        HealthScoreCard(report)

        NetworkCard(network, localInfo)

        MetricRowsCard(report)

        when {
            running -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    Text(
                        text = "Running diagnostic… " + when (currentStep) {
                            DiagnosticStep.GATEWAY -> "Gateway check"
                            DiagnosticStep.DNS -> "DNS check"
                            DiagnosticStep.INTERNET -> "Internet check"
                            DiagnosticStep.RELIABILITY -> "Reliability check"
                            else -> "Preparing"
                        },
                    )
                    OutlinedButton(onClick = onCancelDiagnostic) { Text("Cancel") }
                }
            }
            else -> Button(onClick = onRunDiagnostic) { Text("Run Full Diagnostic") }
        }

        if (report != null && !running) {
            OutlinedButton(onClick = onOpenDetails) { Text("View details") }
        }

        report?.summary?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HealthScoreCard(report: DiagnosticReport?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Network Health",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report?.healthScore == null) {
                Text(
                    text = "Run a diagnostic to measure",
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                val score = report.healthScore!!
                Text(
                    text = "${score.total} / ${score.maxTotal}",
                    style = MaterialTheme.typography.displaySmall,
                )
                score.components.forEach { component ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = component.name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${component.score}/${component.max}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkCard(network: NetworkSnapshot?, localInfo: LocalNetworkInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Network",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = network?.type?.label() ?: "No active network",
                style = MaterialTheme.typography.titleMedium,
            )
            val stateText = when (network?.state) {
                ConnectionState.CONNECTED -> "Connected"
                ConnectionState.CONNECTED_NO_INTERNET -> "Connected (no internet)"
                ConnectionState.DISCONNECTED -> "Disconnected"
                ConnectionState.UNKNOWN -> "Unknown"
                null -> "Unknown"
            }
            Text(text = stateText, style = MaterialTheme.typography.bodyMedium)

            if (localInfo != LocalNetworkInfo()) {
                InfoRow("Local IP", localInfo.localIp ?: "Unavailable on this device")
                InfoRow("Gateway", formatGateway(localInfo))
                InfoRow(
                    "DNS",
                    if (localInfo.dnsServers.isEmpty()) "Unavailable on this device"
                    else localInfo.dnsServers.joinToString("\n"),
                )
            }

            AdvancedDetails(localInfo)
        }
    }
}

private fun formatGateway(localInfo: LocalNetworkInfo): String = when {
    localInfo.gateway == null -> "Unavailable on this device"
    localInfo.gatewaySource == LocalNetworkInfo.GatewaySource.DERIVED_PREFIX ->
        "${localInfo.gateway} (derived, may differ)"
    else -> localInfo.gateway
}

@Composable
private fun MetricRowsCard(report: DiagnosticReport?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Last diagnostic",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report == null) {
                Text(
                    text = "No diagnostic run yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val gateway = report.gateway
                InfoRow(
                    "Gateway",
                    when {
                        gateway == null -> "Unavailable on this device"
                        gateway.reachable ->
                            "✓ ${gateway.latencyMs} ms " +
                                "(${LatencyAnalyzer.label(LatencyAnalyzer.classifyLocal(gateway.latencyMs))})"
                        else -> "✕ Not reachable"
                    },
                )
                val dnsLatency = report.dns?.systemResolution?.latencyMs
                val dnsOk = report.dns?.systemResolution?.success == true
                InfoRow(
                    "DNS",
                    when {
                        report.dns == null -> "Unavailable on this device"
                        dnsOk -> "✓ $dnsLatency ms " +
                            "(${LatencyAnalyzer.label(LatencyAnalyzer.classifyLocal(dnsLatency))})"
                        else -> "✕ Resolution failed"
                    },
                )
                InfoRow(
                    "Internet",
                    when {
                        report.internet == null -> "Unavailable on this device"
                        report.internet.reachable -> "✓ Reachable"
                        else -> "✕ Not reachable"
                    },
                )
                InfoRow(
                    "HTTPS",
                    when {
                        report.https == null -> "Unavailable on this device"
                        report.https.works -> "✓ Works"
                        else -> "✕ Fails"
                    },
                )
                val reliability = report.reliability
                InfoRow(
                    "Reliability",
                    when {
                        reliability == null -> "Unavailable on this device"
                        else -> "${reliability.successful}/${reliability.requests} requests" +
                            (reliability.successRatePercent?.let { " · ${formatPercent(it)}" } ?: "")
                    },
                )
            }
        }
    }
}

private fun formatPercent(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded % 1.0 == 0.0) "${rounded.toInt()}%" else "$rounded%"
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdvancedDetails(localInfo: LocalNetworkInfo) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.animateContentSize()) {
        Text(
            text = if (expanded) "Advanced details ▲" else "Advanced details ▼",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable { expanded = !expanded },
        )

        if (expanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                InfoRow("Interface", localInfo.interfaceName ?: "Unavailable on this device")
                InfoRow("Transports", localInfo.transports.joinToString(", ").ifEmpty { "Unavailable" })
                InfoRow(
                    "Link addresses",
                    localInfo.linkAddresses.joinToString("\n").ifEmpty { "Unavailable on this device" },
                )
                InfoRow("MTU", localInfo.mtu?.toString() ?: "Unavailable on this device")
                InfoRow("Domain", localInfo.domainName ?: "Unavailable on this device")
            }
        }
    }
}
