package com.maxcorp.gosha.mobile

import android.content.Context
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress

object WifiBoundHttp {
    fun forCurrentWifi(context: Context, baseClient: OkHttpClient): OkHttpClient {
        val wifiNetwork = WifiInfoHelper.currentWifiNetwork(context) ?: return baseClient
        return try {
            baseClient.newBuilder()
                .socketFactory(wifiNetwork.socketFactory)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        return wifiNetwork.getAllByName(hostname).toList()
                    }
                })
                .build()
        } catch (_: Exception) {
            baseClient
        }
    }
}
