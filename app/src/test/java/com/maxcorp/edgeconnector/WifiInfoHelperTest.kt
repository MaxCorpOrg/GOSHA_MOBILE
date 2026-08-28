package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiInfoHelperTest {
    @Test
    fun `active vpn over wifi is skipped and real wifi from all networks is selected`() {
        val activeVpnUnderlay = "vpn-underlay"
        val realWifi = "home-wifi"
        val mobile = "mobile"
        val flags = mapOf(
            activeVpnUnderlay to WifiInfoHelper.NetworkTransportFlags(
                hasWifi = true,
                hasVpn = true,
            ),
            realWifi to WifiInfoHelper.NetworkTransportFlags(
                hasWifi = true,
                hasVpn = false,
            ),
            mobile to WifiInfoHelper.NetworkTransportFlags(
                hasWifi = false,
                hasVpn = false,
            ),
        )

        val selected = WifiInfoHelper.chooseWifiNetworkForPolicy(
            activeNetwork = activeVpnUnderlay,
            allNetworks = listOf(activeVpnUnderlay, mobile, realWifi),
        ) { flags[it] }

        assertEquals(realWifi, selected)
    }

    @Test
    fun `active real wifi wins before all networks fallback`() {
        val activeWifi = "active-home-wifi"
        val otherWifi = "other-home-wifi"

        val selected = WifiInfoHelper.chooseWifiNetworkForPolicy(
            activeNetwork = activeWifi,
            allNetworks = listOf(otherWifi),
        ) { network ->
            when (network) {
                activeWifi,
                otherWifi -> WifiInfoHelper.NetworkTransportFlags(
                    hasWifi = true,
                    hasVpn = false,
                )
                else -> null
            }
        }

        assertEquals(activeWifi, selected)
    }

    @Test
    fun `vpn only candidates do not count as usable wifi`() {
        val selected = WifiInfoHelper.chooseWifiNetworkForPolicy(
            activeNetwork = "active-vpn",
            allNetworks = listOf("active-vpn", "plain-vpn", "cellular"),
        ) { network ->
            when (network) {
                "active-vpn" -> WifiInfoHelper.NetworkTransportFlags(
                    hasWifi = true,
                    hasVpn = true,
                )
                "plain-vpn" -> WifiInfoHelper.NetworkTransportFlags(
                    hasWifi = false,
                    hasVpn = true,
                )
                "cellular" -> WifiInfoHelper.NetworkTransportFlags(
                    hasWifi = false,
                    hasVpn = false,
                )
                else -> null
            }
        }

        assertNull(selected)
    }
}
