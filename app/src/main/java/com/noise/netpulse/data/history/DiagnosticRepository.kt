package com.noise.netpulse.data.history

import android.content.Context
import com.noise.netpulse.domain.model.ConnectionState
import com.noise.netpulse.domain.model.DiagnosticReport
import com.noise.netpulse.domain.model.HealthScore
import com.noise.netpulse.domain.model.NetworkType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local diagnostic history (Phase 14).
 *
 * Stores compact summaries only — timestamp, score, summary sentence, network
 * type/state. No local IPs, gateway or DNS configuration, SSIDs, or user
 * identifiers are persisted (privacy principle: local-first, minimal data).
 */
class DiagnosticRepository(context: Context) {

    private val file = File(context.filesDir, HISTORY_FILE)

    data class HistoryEntry(
        val id: String,
        val timestampEpochMs: Long,
        val scoreTotal: Int?,
        val scoreMax: Int,
        val summary: String,
        val networkType: NetworkType,
        val connectionState: ConnectionState,
        val durationMs: Long,
        val aborted: Boolean,
    )

    fun save(report: DiagnosticReport): HistoryEntry {
        val entry = HistoryEntry(
            id = report.startedAtEpochMs.toString(),
            timestampEpochMs = report.startedAtEpochMs,
            scoreTotal = report.healthScore?.total,
            scoreMax = report.healthScore?.maxTotal ?: 100,
            summary = report.summary,
            networkType = report.networkType,
            connectionState = report.connectionState,
            durationMs = report.durationMs,
            aborted = report.abortedDueToNetworkChange,
        )
        val entries = entries().filterNot { it.id == entry.id } + entry
        persist(entries)
        return entry
    }

    /** Newest first. */
    fun entries(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { obj ->
                    HistoryEntry(
                        id = obj.getString(KEY_ID),
                        timestampEpochMs = obj.getLong(KEY_TIMESTAMP),
                        scoreTotal = obj.optInt(KEY_SCORE, -1).takeIf { it >= 0 },
                        scoreMax = obj.optInt(KEY_SCORE_MAX, 100),
                        summary = obj.optString(KEY_SUMMARY, ""),
                        networkType = runCatching {
                            NetworkType.valueOf(obj.getString(KEY_NETWORK_TYPE))
                        }.getOrDefault(NetworkType.UNKNOWN),
                        connectionState = runCatching {
                            ConnectionState.valueOf(obj.getString(KEY_STATE))
                        }.getOrDefault(ConnectionState.UNKNOWN),
                        durationMs = obj.optLong(KEY_DURATION, 0),
                        aborted = obj.optBoolean(KEY_ABORTED, false),
                    )
                }
            }
        }.getOrDefault(emptyList()).sortedByDescending { it.timestampEpochMs }
    }

    fun delete(id: String) {
        persist(entries().filterNot { it.id == id })
    }

    fun clear() {
        persist(emptyList())
    }

    private fun persist(entries: List<HistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put(KEY_ID, entry.id)
                    .put(KEY_TIMESTAMP, entry.timestampEpochMs)
                    .put(KEY_SCORE, entry.scoreTotal ?: -1)
                    .put(KEY_SCORE_MAX, entry.scoreMax)
                    .put(KEY_SUMMARY, entry.summary)
                    .put(KEY_NETWORK_TYPE, entry.networkType.name)
                    .put(KEY_STATE, entry.connectionState.name)
                    .put(KEY_DURATION, entry.durationMs)
                    .put(KEY_ABORTED, entry.aborted),
            )
        }
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(array.toString())
        }
    }

    private companion object {
        const val HISTORY_FILE = "diagnostic_history.json"
        const val KEY_ID = "id"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_SCORE = "score"
        const val KEY_SCORE_MAX = "scoreMax"
        const val KEY_SUMMARY = "summary"
        const val KEY_NETWORK_TYPE = "networkType"
        const val KEY_STATE = "state"
        const val KEY_DURATION = "durationMs"
        const val KEY_ABORTED = "aborted"
    }
}
