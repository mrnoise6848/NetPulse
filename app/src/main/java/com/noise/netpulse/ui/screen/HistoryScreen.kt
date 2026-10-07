package com.noise.netpulse.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noise.netpulse.data.history.DiagnosticRepository
import com.noise.netpulse.domain.model.ConnectionState
import com.noise.netpulse.domain.model.NetworkType
import com.noise.netpulse.domain.model.label
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic history (Phase 14): recent summaries only, deletable per entry
 * or entirely. No sensitive network information is stored or shown.
 */
@Composable
fun HistoryScreen(
    entries: List<DiagnosticRepository.HistoryEntry>,
    onDeleteEntry: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "History", style = MaterialTheme.typography.headlineSmall)

        if (entries.isEmpty()) {
            Text(
                text = "No history yet. Run a diagnostic.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEach { entry ->
                HistoryEntryCard(entry, onDeleteEntry)
            }
            Button(onClick = onClearHistory) { Text("Delete all history") }
        }
    }
}

private fun entryDateFormat(): SimpleDateFormat =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

@Composable
private fun HistoryEntryCard(
    entry: DiagnosticRepository.HistoryEntry,
    onDeleteEntry: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${entryDateFormat().format(Date(entry.timestampEpochMs))} — " +
                        entry.networkType.label(),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = when {
                        entry.aborted -> "Interrupted (network changed)"
                        entry.scoreTotal != null -> "${entry.scoreTotal} / ${entry.scoreMax}"
                        else -> "No score"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                entry.summary.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { onDeleteEntry(entry.id) }) { Text("Delete") }
        }
    }
}
