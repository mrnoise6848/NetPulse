package com.noise.netpulse.domain.model

/** Physical transport of the active network, as reported by Android APIs. */
enum class NetworkType {
    WIFI,
    MOBILE,
    ETHERNET,
    VPN,
    NONE,
    UNKNOWN,
}

/** High-level connectivity state derived from [android.net.NetworkCapabilities]. */
enum class ConnectionState {
    CONNECTED,
    CONNECTED_NO_INTERNET,
    DISCONNECTED,
    UNKNOWN,
}

/** Immutable view of the currently active network. Extended in later phases. */
data class NetworkSnapshot(
    val type: NetworkType,
    val state: ConnectionState,
)

/** Human-friendly label for a network type, used by the UI. */
fun NetworkType.label(): String = when (this) {
    NetworkType.WIFI -> "Wi-Fi"
    NetworkType.MOBILE -> "Mobile Data"
    NetworkType.ETHERNET -> "Ethernet"
    NetworkType.VPN -> "VPN"
    NetworkType.NONE -> "No active network"
    NetworkType.UNKNOWN -> "Unknown"
}
