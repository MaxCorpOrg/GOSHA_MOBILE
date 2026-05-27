package com.maxcorp.gosha.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.PatternMatcher
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.Inet4Address

object RobotWifiConnector {
    private const val LOG_TAG = "GoshaRobotWifi"
    private var activeCallback: ConnectivityManager.NetworkCallback? = null
    private var activeManager: ConnectivityManager? = null
    private var activeNetwork: Network? = null

    fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return permissions.toTypedArray()
    }

    fun hasRequiredPermissions(context: Context): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun connect(
        context: Context,
        onConnected: () -> Unit,
        onError: (String) -> Unit,
    ) {
        Log.w(LOG_TAG, "connect()")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            onError("На этом Android автоподключение внутри приложения не поддерживается. Откройте Wi‑Fi телефона и выберите сеть робота вручную.")
            return
        }

        release()
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val prefixes = RobotBranding.acceptedWifiPrefixes()

        fun tryPrefix(index: Int) {
            if (index >= prefixes.size) {
                release()
                onError("Не удалось автоматически подключиться к Wi‑Fi робота. Проверьте, что сеть ${RobotBranding.displayWifiHint()} видна и робот в режиме настройки. Если телефон уже подключен к этой сети вручную, просто нажмите основную кнопку подключения еще раз.")
                return
            }

            val prefix = prefixes[index]
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsidPattern(PatternMatcher(prefix, PatternMatcher.PATTERN_PREFIX))
                .build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            var deliveredAvailable = false
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    deliveredAvailable = true
                    Log.w(LOG_TAG, "onAvailable($network) for prefix $prefix")
                    activeManager = manager
                    activeCallback = this
                    activeNetwork = network
                    manager.bindProcessToNetwork(network)
                    onConnected()
                }

                override fun onUnavailable() {
                    Log.w(LOG_TAG, "onUnavailable() for prefix $prefix")
                    release()
                    tryPrefix(index + 1)
                }

                override fun onLost(network: Network) {
                    Log.w(LOG_TAG, "onLost($network), deliveredAvailable=$deliveredAvailable, prefix=$prefix")
                    if (!deliveredAvailable) {
                        return
                    }
                    release()
                }
            }

            activeManager = manager
            activeCallback = callback
            try {
                Log.w(LOG_TAG, "requestNetwork() for prefix $prefix")
                manager.requestNetwork(request, callback)
            } catch (exc: SecurityException) {
                release()
                onError("Android запретил автоподключение к Wi‑Fi робота: ${exc.message ?: "нет прав"}")
            } catch (exc: Exception) {
                release()
                onError("Не удалось запросить подключение к Wi‑Fi робота: ${exc.message ?: "неизвестная ошибка"}")
            }
        }

        tryPrefix(0)
    }

    fun bindToCurrentRobotWifi(context: Context): Boolean {
        Log.w(LOG_TAG, "bindToCurrentRobotWifi()")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val requestedRobotNetwork = activeNetwork?.takeIf { network ->
            val ssid = networkSsid(manager, network)
            RobotBranding.isRobotWifiSsid(ssid)
        }
        if (requestedRobotNetwork != null) {
            return try {
                activeManager = manager
                manager.bindProcessToNetwork(requestedRobotNetwork)
                Log.w(LOG_TAG, "bindToCurrentRobotWifi reused requested network $requestedRobotNetwork")
                true
            } catch (exc: Exception) {
                Log.w(LOG_TAG, "bindToCurrentRobotWifi reuse failed: ${exc.message}")
                release()
                false
            }
        }

        val robotNetwork = findRobotWifiNetwork(manager)
        if (robotNetwork == null) {
            Log.w(LOG_TAG, "bindToCurrentRobotWifi could not find robot network. active=${manager.activeNetwork}, all=${manager.allNetworks.joinToString()}")
            return false
        }

        val shouldReleaseStaleRequest = activeCallback != null && activeNetwork != null && activeNetwork != robotNetwork
        if (shouldReleaseStaleRequest) {
            Log.w(LOG_TAG, "bindToCurrentRobotWifi releasing stale requested network $activeNetwork")
            release()
        }

        return try {
            activeManager = manager
            activeNetwork = robotNetwork
            manager.bindProcessToNetwork(robotNetwork)
            Log.w(LOG_TAG, "bindToCurrentRobotWifi bound to discovered robot network $robotNetwork")
            true
        } catch (exc: Exception) {
            Log.w(LOG_TAG, "bindToCurrentRobotWifi failed: ${exc.message}")
            release()
            false
        }
    }

    fun release() {
        Log.w(LOG_TAG, "release()")
        try {
            activeCallback?.let { callback ->
                activeManager?.unregisterNetworkCallback(callback)
            }
        } catch (_: Exception) {
        }
        try {
            activeManager?.bindProcessToNetwork(null)
        } catch (_: Exception) {
        }
        activeCallback = null
        activeManager = null
        activeNetwork = null
    }

    fun preferredNetwork(context: Context): Network? {
        activeNetwork?.let {
            Log.w(LOG_TAG, "preferredNetwork() using active requested network $it")
            return it
        }
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        findRobotWifiNetwork(manager)?.let {
            Log.w(LOG_TAG, "preferredNetwork() discovered robot network $it")
            return it
        }
        val fallback = manager.activeNetwork
        Log.w(LOG_TAG, "preferredNetwork() fallback activeNetwork=$fallback")
        return fallback
    }

    fun currentRobotWifiNetwork(context: Context): Network? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return findRobotWifiNetwork(manager)
    }

    private fun findRobotWifiNetwork(manager: ConnectivityManager): Network? {
        return manager.allNetworks.firstOrNull { network ->
            val capabilities = manager.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
                return@firstOrNull false
            }
            val ssid = networkSsid(manager, network)
            if (RobotBranding.isRobotWifiSsid(ssid)) {
                return@firstOrNull true
            }
            networkIpv4Address(manager, network).startsWith("192.168.4.")
        }
    }

    private fun networkSsid(manager: ConnectivityManager, network: Network): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return ""
        }
        val capabilities = manager.getNetworkCapabilities(network)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            return ""
        }
        val wifiInfo = capabilities.transportInfo as? android.net.wifi.WifiInfo
        return wifiInfo?.ssid.orEmpty().removePrefix("\"").removeSuffix("\"")
    }

    private fun networkIpv4Address(manager: ConnectivityManager, network: Network): String {
        return manager.getLinkProperties(network)
            ?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?.address
            ?.hostAddress
            .orEmpty()
    }
}
