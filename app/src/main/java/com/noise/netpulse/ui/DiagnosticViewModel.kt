package com.noise.netpulse.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noise.netpulse.data.network.ConnectivityMonitor
import com.noise.netpulse.data.network.NetworkInfoProvider
import com.noise.netpulse.domain.model.LocalNetworkInfo
import com.noise.netpulse.domain.model.NetworkSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Top-level state shown on the main screen. */
data class MainUiState(
    val network: NetworkSnapshot? = null,
    val localInfo: LocalNetworkInfo = LocalNetworkInfo(),
    val running: Boolean = false,
)

/**
 * Presentation state holder for NetPulse. Owns the [ConnectivityMonitor] lifecycle
 * and, in later phases, the diagnostic pipeline and history.
 */
class DiagnosticViewModel(application: Application) : AndroidViewModel(application) {

    private val connectivityMonitor = ConnectivityMonitor(application, viewModelScope)
    private val networkInfoProvider = NetworkInfoProvider(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        connectivityMonitor.snapshot
            .onEach { snapshot ->
                val info = if (snapshot.state == com.noise.netpulse.domain.model.ConnectionState.DISCONNECTED) {
                    LocalNetworkInfo()
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        networkInfoProvider.currentLocalInfo()
                    }
                }
                _uiState.update { it.copy(network = snapshot, localInfo = info) }
            }
            .launchIn(viewModelScope)

        connectivityMonitor.start()
    }

    override fun onCleared() {
        connectivityMonitor.stop()
    }
}
