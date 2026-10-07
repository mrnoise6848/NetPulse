package com.noise.netpulse.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noise.netpulse.domain.model.ConnectionState
import com.noise.netpulse.domain.model.label

@Composable
fun MainScreen(
    network: com.noise.netpulse.domain.model.NetworkSnapshot?,
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Network",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = network?.type?.label() ?: "No active network",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                val stateText = when (network?.state) {
                    ConnectionState.CONNECTED -> "Connected"
                    ConnectionState.CONNECTED_NO_INTERNET -> "Connected (no internet)"
                    ConnectionState.DISCONNECTED -> "Disconnected"
                    ConnectionState.UNKNOWN -> "Unknown"
                    null -> "Unknown"
                }
                Text(text = stateText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
