package com.noise.netpulse.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.noise.netpulse.domain.model.ConnectionState
import com.noise.netpulse.domain.model.NetworkSnapshot
import com.noise.netpulse.domain.model.NetworkType

/**
 * Reads the current active network from the platform [ConnectivityManager].
 * Uses modern APIs (NetworkCapabilities) only — no deprecated APIs.
 */
class NetworkStateDataSource(context: Context) {

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    /** Returns the current [NetworkSnapshot], never null (a null active network maps to NONE). */
    fun currentSnapshot(): NetworkSnapshot {
        val network = connectivityManager.activeNetwork
            ?: return NetworkSnapshot(NetworkType.NONE, ConnectionState.DISCONNECTED)
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return NetworkSnapshot(NetworkType.UNKNOWN, ConnectionState.DISCONNECTED)

        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.UNKNOWN
        }

        val state = when {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ->
                ConnectionState.CONNECTED
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ->
                ConnectionState.CONNECTED_NO_INTERNET
            else -> ConnectionState.CONNECTED_NO_INTERNET
        }

        return NetworkSnapshot(type = type, state = state)
    }
}
