package com.maxcorp.gosha.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.SupplicantState
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.net.Inet4Address

object WifiInfoHelper {
    private const val DEFAULT_SCAN_MAX_AGE_MS = 15_000L

    enum class SystemWifiState {
        Enabled,
        Enabling,
        Disabled,
        Disabling,
        Unknown,
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun currentSsid(context: Context): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return ""

        val activeWifi = manager?.let(::findWifiNetwork)
        val connectionInfo = wifiManager.connectionInfo
        val associationCompleted = connectionInfo?.supplicantState == SupplicantState.COMPLETED

        if (activeWifi != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val capabilities = manager.getNetworkCapabilities(activeWifi)
            val transportInfo = capabilities?.transportInfo as? WifiInfo
            val ssid = transportInfo?.ssid.orEmpty().trim()
            if (ssid.isNotBlank() && ssid != "<unknown ssid>") {
                return ssid.removePrefix("\"").removeSuffix("\"")
            }
        }

        if (activeWifi == null && !associationCompleted) {
            return ""
        }

        val raw = connectionInfo?.ssid.orEmpty().trim()
        if (raw.isBlank() || raw == "<unknown ssid>") return ""
        return raw.removePrefix("\"").removeSuffix("\"")
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun currentSubnetPrefix(context: Context): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return ""

        val activeWifi = manager?.let(::findWifiNetwork)
        if (activeWifi != null) {
            val prefix = manager.getLinkProperties(activeWifi).toIpv4Prefix()
            if (prefix.isNotBlank()) {
                return prefix
            }
        }

        // Не используем старый адрес из WifiManager, если Android уже не считает Wi‑Fi активной сетью.
        if (currentSsid(context).isBlank()) {
            return ""
        }

        val ip = wifiManager.connectionInfo?.ipAddress ?: 0
        if (ip == 0) return ""
        val a = ip and 0xff
        val b = ip shr 8 and 0xff
        val c = ip shr 16 and 0xff
        return "$a.$b.$c"
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun nearbySsidByPrefixes(context: Context, prefixes: List<String>, maxAgeMs: Long = DEFAULT_SCAN_MAX_AGE_MS): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return ""
        val normalizedPrefixes = prefixes.map { it.trim() }.filter { it.isNotBlank() }
        if (normalizedPrefixes.isEmpty()) return ""
        val nowUs = SystemClock.elapsedRealtimeNanos() / 1_000L

        return wifiManager.scanResults
            ?.asSequence()
            ?.mapNotNull { result ->
                val ssid = result.SSID.orEmpty().trim().removePrefix("\"").removeSuffix("\"")
                if (normalizedPrefixes.none { ssid.startsWith(it, ignoreCase = true) }) {
                    return@mapNotNull null
                }
                val ageMs = ((nowUs - result.timestamp).coerceAtLeast(0L)) / 1_000L
                ssid.takeIf { ageMs <= maxAgeMs }
            }
            ?.firstOrNull()
            .orEmpty()
    }

    fun nearbySsidByPrefix(context: Context, prefix: String, maxAgeMs: Long = DEFAULT_SCAN_MAX_AGE_MS): String =
        nearbySsidByPrefixes(context, listOf(prefix), maxAgeMs)

    @Suppress("DEPRECATION")
    fun systemWifiState(context: Context): SystemWifiState {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return SystemWifiState.Unknown
        return when (wifiManager.wifiState) {
            WifiManager.WIFI_STATE_ENABLED -> SystemWifiState.Enabled
            WifiManager.WIFI_STATE_ENABLING -> SystemWifiState.Enabling
            WifiManager.WIFI_STATE_DISABLED -> SystemWifiState.Disabled
            WifiManager.WIFI_STATE_DISABLING -> SystemWifiState.Disabling
            else -> SystemWifiState.Unknown
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun nearbySsidByPrefixesAnyAge(context: Context, prefixes: List<String>): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return ""
        val normalizedPrefixes = prefixes.map { it.trim() }.filter { it.isNotBlank() }
        if (normalizedPrefixes.isEmpty()) return ""

        return wifiManager.scanResults
            ?.asSequence()
            ?.mapNotNull { result ->
                val ssid = result.SSID.orEmpty().trim().removePrefix("\"").removeSuffix("\"")
                ssid.takeIf { candidate -> normalizedPrefixes.any { candidate.startsWith(it, ignoreCase = true) } }
            }
            ?.firstOrNull()
            .orEmpty()
    }

    fun nearbySsidByPrefixAnyAge(context: Context, prefix: String): String =
        nearbySsidByPrefixesAnyAge(context, listOf(prefix))

    fun currentWifiNetwork(context: Context): Network? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        return findWifiNetwork(manager)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun requestFreshScanIfPossible(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return false
        if (!hasScanPermissions(context)) return false
        return runCatching { wifiManager.startScan() }.getOrDefault(false)
    }

    private fun hasScanPermissions(context: Context): Boolean {
        val hasLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasLocation) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.NEARBY_WIFI_DEVICES
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun findWifiNetwork(manager: ConnectivityManager): Network? {
        val active = manager.activeNetwork
        if (active != null && manager.getNetworkCapabilities(active)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            return active
        }
        return manager.allNetworks.firstOrNull { network ->
            manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun LinkProperties?.toIpv4Prefix(): String {
        val ipv4 = this?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?.address
            ?.hostAddress
            .orEmpty()
        if (ipv4.isBlank()) return ""
        val parts = ipv4.split('.')
        if (parts.size < 3) return ""
        return parts.take(3).joinToString(".")
    }
}
