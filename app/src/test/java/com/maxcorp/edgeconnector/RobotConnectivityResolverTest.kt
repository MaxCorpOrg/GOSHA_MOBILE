package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class RobotConnectivityResolverTest {
    private val robotWifiPrefix = RobotBranding.PRIMARY_WIFI_PREFIX

    @Test
    fun `phone on robot wifi beats other signals`() {
        val decision = RobotConnectivityResolver.resolve(
            currentSsid = "\"GOSHA-A-5B09\"",
            nearbyRobotSsid = "GOSHA-A-5B09",
            panelSnapshot = panelSnapshot(connected = true, localHost = "192.168.0.55"),
            robotWifiPrefix = robotWifiPrefix,
        )

        assertEquals(RobotConnectivityDecisionType.PHONE_ON_ROBOT_WIFI, decision.type)
        assertEquals("GOSHA-A-5B09", decision.robotSsid)
    }

    @Test
    fun `nearby robot wifi beats panel connected state`() {
        val decision = RobotConnectivityResolver.resolve(
            currentSsid = "4G-CPE-1884",
            nearbyRobotSsid = "GOSHA-A-5B09",
            panelSnapshot = panelSnapshot(connected = true, localHost = ""),
            robotWifiPrefix = robotWifiPrefix,
        )

        assertEquals(RobotConnectivityDecisionType.ROBOT_VISIBLE_NEARBY, decision.type)
        assertEquals("GOSHA-A-5B09", decision.robotSsid)
    }

    @Test
    fun `legacy xiaozhi prefix is still accepted during transition`() {
        val decision = RobotConnectivityResolver.resolve(
            currentSsid = "\"Xiaozhi-5B09\"",
            nearbyRobotSsid = "Xiaozhi-5B09",
            panelSnapshot = panelSnapshot(connected = false, localHost = ""),
            robotWifiPrefix = robotWifiPrefix,
        )

        assertEquals(RobotConnectivityDecisionType.PHONE_ON_ROBOT_WIFI, decision.type)
        assertEquals("Xiaozhi-5B09", decision.robotSsid)
    }

    @Test
    fun `local panel host resolves to local connection`() {
        val decision = RobotConnectivityResolver.resolve(
            panelSnapshot = panelSnapshot(connected = true, localHost = "192.168.0.103"),
            robotWifiPrefix = robotWifiPrefix,
        )

        assertEquals(RobotConnectivityDecisionType.CONNECTED_LOCALLY, decision.type)
        assertEquals("192.168.0.103", decision.localHost)
    }

    @Test
    fun `cloud panel connection resolves to panel route when local host is absent`() {
        val decision = RobotConnectivityResolver.resolve(
            panelSnapshot = panelSnapshot(connected = true, localHost = ""),
            robotWifiPrefix = robotWifiPrefix,
        )

        assertEquals(RobotConnectivityDecisionType.CONNECTED_VIA_PANEL, decision.type)
    }

    @Test
    fun `returns unknown when no connection signals are present`() {
        val decision = RobotConnectivityResolver.resolve(robotWifiPrefix = robotWifiPrefix)

        assertEquals(RobotConnectivityDecisionType.UNKNOWN, decision.type)
    }

    private fun panelSnapshot(
        connected: Boolean,
        localHost: String,
    ) = RobotRuntimeSnapshot(
        robotId = "jarvis-01",
        connected = connected,
        mode = "cloud-mcp",
        transportState = "reachable",
        target = "",
        localHost = localHost,
    )
}
