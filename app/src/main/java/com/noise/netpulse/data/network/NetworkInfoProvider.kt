package com.noise.netpulse.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.noise.netpulse.domain.model.LocalNetworkInfo
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Locale

/**
 * Extracts local link details from [LinkProperties] and (for Wi-Fi) DHCP info.
 * Only device-local information is read; nothing leaves the device.
 */
class NetworkInfoProvider(context: Context) {

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager: WifiManager =
        context.getSystemService(WifiManager::class.java)

    /** Reads local network info for the current default network. */
    fun currentLocalInfo(): LocalNetworkInfo {
        val network = connectivityManager.activeNetwork ?: return LocalNetworkInfo()
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return LocalNetworkInfo()
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        val transports = mutableListOf<String>()
        capabilities?.let { caps ->
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transports += "VPN"
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports += "Wi-Fi"
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports += "Cellular"
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports += "Ethernet"
        }

        val gateway = discoverGateway(network, linkProperties, capabilities)

        return LocalNetworkInfo(
            interfaceName = linkProperties.interfaceName,
            localIp = firstIpv4(linkProperties)?.hostAddress,
            gateway = gateway?.first,
            gatewaySource = gateway?.second ?: LocalNetworkInfo.GatewaySource.UNAVAILABLE,
            dnsServers = linkProperties.dnsServers.map { it.hostAddress ?: "?" },
            domainName = linkProperties.domains?.takeIf { it.isNotBlank() },
            mtu = linkProperties.mtu.takeIf { it > 0 },
            transports = transports,
            linkAddresses = linkProperties.linkAddresses.mapNotNull { addr ->
                addr.address.hostAddress?.let { "${it}/${addr.prefixLength}" }
            },
            vpnUnderlyingNetworks = null,
            validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }

    /** Returns (gatewayAddress, source) or null when not determinable. */
    private fun discoverGateway(
        network: Network,
        linkProperties: LinkProperties,
        capabilities: NetworkCapabilities?,
    ): Pair<String, LocalNetworkInfo.GatewaySource>? {
        // Wi-Fi: DhcpInfo gives the authoritative DHCP-provided gateway.
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val dhcp = runCatching { wifiManager.dhcpInfo }.getOrNull()
            if (dhcp != null && dhcp.gateway != 0) {
                val addr = intToIp(dhcp.gateway)
                if (addr != null) return addr to LocalNetworkInfo.GatewaySource.DHCP
            }
        }

        // Heuristic fallback: the first usable host of the IPv4 prefix is the
        // conventional gateway. Explicitly flagged as derived, never asserted.
        val ipv4 = firstIpv4(linkProperties) ?: return null
        val prefix = firstIpv4Prefix(linkProperties) ?: return null
        if (prefix <= 0 || prefix >= 32) return null
        val bytes = ipv4.address
        val hostBits = 32 - prefix
        val networkInt = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
        val base = networkInt and (-(1 shl hostBits))
        val gatewayInt = base + 1
        val addr = String.format(
            Locale.ROOT,
            "%d.%d.%d.%d",
            (gatewayInt ushr 24) and 0xFF,
            (gatewayInt ushr 16) and 0xFF,
            (gatewayInt ushr 8) and 0xFF,
            gatewayInt and 0xFF,
        )
        return addr to LocalNetworkInfo.GatewaySource.DERIVED_PREFIX
    }

    private fun firstIpv4(linkProperties: LinkProperties): Inet4Address? =
        linkProperties.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull()

    private fun firstIpv4Prefix(linkProperties: LinkProperties): Int? =
        linkProperties.linkAddresses
            .firstOrNull { it.address is Inet4Address }
            ?.prefixLength

    private fun intToIp(lease: Int): String? {
        val a = (lease ushr 0) and 0xFF
        val b = (lease ushr 8) and 0xFF
        val c = (lease ushr 16) and 0xFF
        val d = (lease ushr 24) and 0xFF
        if (a == 0 && b == 0 && c == 0 && d == 0) return null
        return "$a.$b.$c.$d"
    }
}
