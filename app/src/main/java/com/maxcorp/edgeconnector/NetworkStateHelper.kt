package com.maxcorp.gosha.mobile

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkStateHelper {
    fun isVpnActive(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return try {
            val activeNetwork = manager.activeNetwork
            if (activeNetwork != null && isRealVpn(manager.getNetworkCapabilities(activeNetwork))) {
                return true
            }

            manager.allNetworks.any { network ->
                network != activeNetwork && isRealVpn(manager.getNetworkCapabilities(network))
            }
        } catch (_: SecurityException) {
            false
        }
    }

    private fun isRealVpn(capabilities: NetworkCapabilities?): Boolean {
        if (capabilities == null) return false
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }
}
