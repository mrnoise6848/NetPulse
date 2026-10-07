package com.noise.netpulse.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noise.netpulse.data.network.ConnectivityMonitor
import com.noise.netpulse.data.network.DnsChecker
import com.noise.netpulse.data.network.GatewayChecker
import com.noise.netpulse.data.network.HttpsChecker
import com.noise.netpulse.data.network.NetworkInfoProvider
import com.noise.netpulse.domain.engine.DiagnosticEngine
import com.noise.netpulse.domain.engine.ReliabilityAnalyzer
import com.noise.netpulse.domain.model.ConnectionState
import com.noise.netpulse.domain.model.DiagnosticReport
import com.noise.netpulse.domain.model.DiagnosticStep
import com.noise.netpulse.domain.model.DnsResult
import com.noise.netpulse.domain.model.EndpointProbe
import com.noise.netpulse.domain.model.GatewayResult
import com.noise.netpulse.domain.model.HttpErrorType
import com.noise.netpulse.domain.model.HttpsResult
import com.noise.netpulse.domain.model.InternetResult
import com.noise.netpulse.domain.model.LocalNetworkInfo
import com.noise.netpulse.domain.model.NetworkSnapshot
import com.noise.netpulse.domain.model.NetworkType
import com.noise.netpulse.domain.model.ReliabilityResult
import com.noise.netpulse.domain.scoring.HealthScoreCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** Top-level state shown on the main screen. */
data class MainUiState(
    val network: NetworkSnapshot? = null,
    val localInfo: LocalNetworkInfo = LocalNetworkInfo(),
    val running: Boolean = false,
    /** The step currently executing, so progress shows real work. */
    val currentStep: DiagnosticStep? = null,
    val report: DiagnosticReport? = null,
    val history: List<com.noise.netpulse.data.history.DiagnosticRepository.HistoryEntry> = emptyList(),
)

/**
 * Presentation state holder for NetPulse: live network state, the diagnostic
 * pipeline with real progress and cancellation, and the latest report.
 */
class DiagnosticViewModel(application: Application) : AndroidViewModel(application) {

    private val connectivityMonitor = ConnectivityMonitor(application, viewModelScope)
    private val networkInfoProvider = NetworkInfoProvider(application)
    private val gatewayChecker = GatewayChecker()
    private val httpsChecker = HttpsChecker()
    private val dnsChecker = DnsChecker { currentLocalInfo() }
    private val reliabilityAnalyzer = ReliabilityAnalyzer()
    private val historyRepository = com.noise.netpulse.data.history.DiagnosticRepository(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null

    /** Network identity captured when the run started; used to detect mid-run changes. */
    private var runNetworkKey: String? = null

    /** Simple in-app navigation state (no navigation dependency needed). */
    private val _screen = MutableStateFlow(com.noise.netpulse.AppScreen.MAIN)
    val screen: StateFlow<com.noise.netpulse.AppScreen> = _screen.asStateFlow()

    fun openDetails() {
        _screen.value = com.noise.netpulse.AppScreen.DETAIL
    }

    fun openMain() {
        _screen.value = com.noise.netpulse.AppScreen.MAIN
        _uiState.update { it.copy(history = historyRepository.entries()) }
    }

    fun openHistory() {
        _screen.value = com.noise.netpulse.AppScreen.HISTORY
        _uiState.update { it.copy(history = historyRepository.entries()) }
    }

    fun deleteHistoryEntry(id: String) {
        historyRepository.delete(id)
        _uiState.update { it.copy(history = historyRepository.entries()) }
    }

    fun clearHistory() {
        historyRepository.clear()
        _uiState.update { it.copy(history = emptyList()) }
    }

    init {
        connectivityMonitor.snapshot
            .onEach { snapshot ->
                val info = if (snapshot.state == ConnectionState.DISCONNECTED) {
                    LocalNetworkInfo()
                } else {
                    withContext(Dispatchers.Default) { networkInfoProvider.currentLocalInfo() }
                }
                _uiState.update { it.copy(network = snapshot, localInfo = info) }
                handleNetworkChangeWhileRunning(snapshot, info)
            }
            .launchIn(viewModelScope)

        connectivityMonitor.start()
    }

    /** Runs the full diagnostic pipeline. Progress reflects real steps only. */
    fun runDiagnostic() {
        if (runJob?.isActive == true) return
        runJob = viewModelScope.launch {
            _uiState.update { it.copy(running = true, currentStep = null) }
            try {
                val report = executePipeline()
                historyRepository.save(report)
                _uiState.update {
                    it.copy(
                        running = false,
                        currentStep = null,
                        report = report,
                        history = historyRepository.entries(),
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.update { it.copy(running = false, currentStep = null) }
                throw e
            } catch (e: Exception) {
                // A failed step must never crash the whole diagnostic.
                _uiState.update { it.copy(running = false, currentStep = null) }
            }
        }
    }

    fun cancelDiagnostic() {
        runJob?.cancel()
        runJob = null
        _uiState.update { it.copy(running = false, currentStep = null) }
    }

    private suspend fun executePipeline(): DiagnosticReport {
        val startedAtEpochMs = System.currentTimeMillis()
        val startNanos = System.nanoTime()

        val network = connectivityMonitor.snapshot.value
        val localInfo = currentLocalInfo()
        runNetworkKey = networkKey(network, localInfo)

        fun step(next: DiagnosticStep) {
            _uiState.update { it.copy(currentStep = next) }
        }

        var gateway: GatewayResult? = null
        var dns: DnsResult? = null
        var https: HttpsResult? = null
        var reliability: ReliabilityResult? = null

        val connected = network != null &&
            network.state != ConnectionState.DISCONNECTED &&
            network.type != NetworkType.NONE

        if (connected) {
            if (localInfo.gateway != null && network.type in GATEWAY_PROBEABLE) {
                step(DiagnosticStep.GATEWAY)
                gateway = gatewayChecker.probe(localInfo.gateway)
            }

            step(DiagnosticStep.DNS)
            dns = dnsChecker.check()

            step(DiagnosticStep.INTERNET)
            https = httpsChecker.check()

            step(DiagnosticStep.RELIABILITY)
            reliability = reliabilityAnalyzer.run(requests = 10) {
                val probe: EndpointProbe = httpsChecker.probe(RELIABILITY_ENDPOINT)
                val latency = probe.latencyMs?.takeIf { probe.success }
                latency to (probe.errorType == HttpErrorType.TIMEOUT)
            }
        }

        val durationMs = (System.nanoTime() - startNanos) / 1_000_000
        val report = DiagnosticEngine.diagnose(
            network = network,
            gateway = gateway,
            dns = dns,
            internet = https?.let {
                InternetResult(
                    probes = it.probes,
                    successCount = it.successCount,
                    failureCount = it.failureCount,
                    bestLatencyMs = it.bestLatencyMs,
                )
            },
            https = https,
            reliability = reliability,
            epochNowMs = System.currentTimeMillis(),
            durationMs = durationMs,
        )
        return report.copy(healthScore = HealthScoreCalculator.calculate(network, gateway, dns, https, reliability))
    }

    private suspend fun currentLocalInfo(): LocalNetworkInfo =
        withContext(Dispatchers.Default) { networkInfoProvider.currentLocalInfo() }

    /**
     * Phase 15 groundwork: if the active network changes while a diagnostic is
     * running, cancel the run — measurements taken on the old network would be stale.
     */
    private fun handleNetworkChangeWhileRunning(snapshot: NetworkSnapshot, info: LocalNetworkInfo) {
        val job = runJob ?: return
        val key = runNetworkKey ?: return
        if (!job.isActive || _uiState.value.currentStep == null) return
        val newKey = networkKey(snapshot, info)
        if (newKey != key) {
            job.cancel()
            runJob = null
            runNetworkKey = null
            val report = DiagnosticEngine.diagnose(
                network = snapshot,
                gateway = null,
                dns = null,
                internet = null,
                https = null,
                reliability = null,
                epochNowMs = System.currentTimeMillis(),
                durationMs = 0,
                abortedDueToNetworkChange = true,
            )
            _uiState.update {
                it.copy(running = false, currentStep = null, report = report)
            }
        }
    }

    private fun networkKey(snapshot: NetworkSnapshot?, info: LocalNetworkInfo): String =
        "${snapshot?.type}-${snapshot?.state}-${info.interfaceName}-${info.localIp}"

    override fun onCleared() {
        runJob?.cancel()
        connectivityMonitor.stop()
    }

    private companion object {
        val GATEWAY_PROBEABLE = setOf(NetworkType.WIFI, NetworkType.ETHERNET)
        const val RELIABILITY_ENDPOINT = "https://connectivitycheck.gstatic.com/generate_204"
    }
}
