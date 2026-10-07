package com.noise.netpulse.domain.model

import java.net.Inet4Address

/** Local link information for the active network. Empty values mean "unavailable on this device". */
data class LocalNetworkInfo(
    val interfaceName: String? = null,
    val localIp: String? = null,
    val gateway: String? = null,
    /** True when the gateway came from DHCP; false when it is a documented heuristic. */
    val gatewaySource: GatewaySource = GatewaySource.UNAVAILABLE,
    val dnsServers: List<String> = emptyList(),
    val domainName: String? = null,
    val mtu: Int? = null,
    val transports: List<String> = emptyList(),
    val linkAddresses: List<String> = emptyList(),
    /** Upstream networks under a VPN, when visible. */
    val vpnUnderlyingNetworks: Int? = null,
    /** True when the OS considers this network private (no captive portal detected). */
    val validated: Boolean? = null,
) {
    enum class GatewaySource { DHCP, DERIVED_PREFIX, UNAVAILABLE }
}

/**
 * Gateway candidates for reachability probing (Phase 4).
 * A gateway is not a published Android API, so this derives candidates from
 * available information and documents its confidence.
 */
data class GatewayInfo(
    val address: String?,
    val source: LocalNetworkInfo.GatewaySource,
) {
    companion object {
        /** Best-effort gateway discovery for IPv4 networks. */
        fun from(localInfo: LocalNetworkInfo): GatewayInfo = GatewayInfo(
            address = localInfo.gateway,
            source = localInfo.gatewaySource,
        )
    }
}
