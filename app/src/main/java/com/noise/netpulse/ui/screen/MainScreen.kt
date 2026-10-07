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
import com.noise.netpulse.domain.model.ConnectionState
import com.noise.netpulse.domain.model.LocalNetworkInfo
import com.noise.netpulse.domain.model.NetworkSnapshot
import com.noise.netpulse.domain.model.label

@Composable
fun MainScreen(
    network: NetworkSnapshot?,
    localInfo: LocalNetworkInfo,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "NetPulse", style = MaterialTheme.typography.headlineMedium)

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
}

private fun formatGateway(localInfo: LocalNetworkInfo): String = when {
    localInfo.gateway == null -> "Unavailable on this device"
    localInfo.gatewaySource == LocalNetworkInfo.GatewaySource.DERIVED_PREFIX ->
        "${localInfo.gateway} (derived, may differ)"
    else -> localInfo.gateway
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
