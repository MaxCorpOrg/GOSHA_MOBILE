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
        onLost: () -> Unit = {},
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
                    // Удерживаем сеть робота для адресных локальных запросов,
                    // но не переводим на нее весь процесс. Иначе панель и другие
                    // внешние вызовы теряют интернет-маршрут, пока телефон сидит
                    // в локальной точке доступа робота без выхода наружу.
                    onConnected()
                }

                override fun onUnavailable() {
                    val callbackIsActive = activeCallback === this
                    Log.w(LOG_TAG, "onUnavailable() for prefix $prefix, callbackIsActive=$callbackIsActive")
                    if (!shouldHandleUnavailableCallback(callbackIsActive)) {
                        return
                    }
                    release()
                    tryPrefix(index + 1)
                }

                override fun onLost(network: Network) {
                    val callbackIsActive = activeCallback === this
                    val networkIsActive = activeNetwork == network
                    Log.w(
                        LOG_TAG,
                        "onLost($network), deliveredAvailable=$deliveredAvailable, callbackIsActive=$callbackIsActive, networkIsActive=$networkIsActive, prefix=$prefix",
                    )
                    if (
                        !shouldHandleNetworkLoss(
                            deliveredAvailable = deliveredAvailable,
                            callbackIsActive = callbackIsActive,
                            networkIsActive = networkIsActive,
                        )
                    ) {
                        return
                    }
                    release()
                    onLost()
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

    internal fun shouldHandleNetworkLoss(
        deliveredAvailable: Boolean,
        callbackIsActive: Boolean,
        networkIsActive: Boolean,
    ): Boolean {
        return deliveredAvailable && callbackIsActive && networkIsActive
    }

    internal fun shouldHandleUnavailableCallback(callbackIsActive: Boolean): Boolean {
        return callbackIsActive
    }

    fun bindToCurrentRobotWifi(context: Context): Boolean {
        Log.w(LOG_TAG, "bindToCurrentRobotWifi()")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        clearStaleActiveNetwork(manager, "bindToCurrentRobotWifi")

        val requestedRobotNetwork = activeNetwork?.takeIf { network ->
            val ssid = networkSsid(manager, network)
            RobotBranding.isRobotWifiSsid(ssid)
        }
        if (requestedRobotNetwork != null) {
            activeManager = manager
            activeNetwork = requestedRobotNetwork
            Log.w(LOG_TAG, "bindToCurrentRobotWifi reused requested network $requestedRobotNetwork")
            return true
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

        activeManager = manager
        activeNetwork = robotNetwork
        Log.w(LOG_TAG, "bindToCurrentRobotWifi remembered discovered robot network $robotNetwork")
        return true
    }

    fun release() {
        Log.w(LOG_TAG, "release()")
        try {
            activeCallback?.let { callback ->
                activeManager?.unregisterNetworkCallback(callback)
            }
        } catch (_: Exception) {
        }
        activeCallback = null
        activeManager = null
        activeNetwork = null
    }

    fun preferredNetwork(context: Context): Network? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        clearStaleActiveNetwork(manager, "preferredNetwork")
        activeNetwork?.let {
            Log.w(LOG_TAG, "preferredNetwork() using active requested network $it")
            return it
        }
        findRobotWifiNetwork(manager)?.let {
            Log.w(LOG_TAG, "preferredNetwork() discovered robot network $it")
            return it
        }
        val fallback = manager.activeNetwork
        Log.w(LOG_TAG, "preferredNetwork() fallback activeNetwork=$fallback")
        return fallback
    }

    fun preferredRobotWifiNetwork(context: Context): Network? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        clearStaleActiveNetwork(manager, "preferredRobotWifiNetwork")
        activeNetwork?.takeIf { network ->
            isRobotWifiNetwork(manager, network)
        }?.let {
            Log.w(LOG_TAG, "preferredRobotWifiNetwork() using active requested network $it")
            return it
        }
        findRobotWifiNetwork(manager)?.let {
            Log.w(LOG_TAG, "preferredRobotWifiNetwork() discovered robot network $it")
            return it
        }
        Log.w(LOG_TAG, "preferredRobotWifiNetwork() no robot network")
        return null
    }

    fun currentRobotWifiNetwork(context: Context): Network? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        clearStaleActiveNetwork(manager, "currentRobotWifiNetwork")
        return findRobotWifiNetwork(manager)
    }

    private fun clearStaleActiveNetwork(manager: ConnectivityManager, reason: String) {
        val rememberedNetwork = activeNetwork ?: return
        if (isRobotWifiNetwork(manager, rememberedNetwork)) {
            return
        }
        Log.w(LOG_TAG, "$reason cleared stale robot network $rememberedNetwork")
        release()
    }

    private fun findRobotWifiNetwork(manager: ConnectivityManager): Network? {
        return manager.allNetworks.firstOrNull { network -> isRobotWifiNetwork(manager, network) }
    }

    private fun isRobotWifiNetwork(manager: ConnectivityManager, network: Network): Boolean {
        val capabilities = manager.getNetworkCapabilities(network)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            return false
        }
        val ssid = networkSsid(manager, network)
        if (RobotBranding.isRobotWifiSsid(ssid)) {
            return true
        }
        return networkIpv4Address(manager, network).startsWith("192.168.4.")
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
