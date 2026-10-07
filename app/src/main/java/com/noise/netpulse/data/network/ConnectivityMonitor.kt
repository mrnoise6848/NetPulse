package com.noise.netpulse.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.noise.netpulse.domain.model.NetworkSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps an up-to-date [NetworkSnapshot] using a default network callback, so the UI
 * never shows stale connectivity state (Phase 15 groundwork: every state change —
 * Wi-Fi/mobile switch, disconnect, VPN change — triggers a fresh read).
 */
class ConnectivityMonitor(context: Context, private val scope: CoroutineScope) {

    private val dataSource = NetworkStateDataSource(context)
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _snapshot = MutableStateFlow(dataSource.currentSnapshot())
    val snapshot: StateFlow<NetworkSnapshot> = _snapshot.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refresh()
        }

        override fun onLost(network: Network) {
            refresh()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            refresh()
        }
    }

    fun start() {
        connectivityManager.registerDefaultNetworkCallback(callback, mainHandler)
        refresh()
    }

    fun stop() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun refresh() {
        scope.launch {
            // Active-network reads are safe on any thread but must not race UI updates.
            val current = withContext(Dispatchers.Default) { dataSource.currentSnapshot() }
            _snapshot.value = current
        }
    }
}
