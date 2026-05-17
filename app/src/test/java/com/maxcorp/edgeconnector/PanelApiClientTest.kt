package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelApiClientTest {
    @Test
    fun `runtime snapshot does not treat configured control as connected without live evidence`() {
        val snapshot = buildSnapshot(
            diagnosticsTarget = "ws://151.241.228.232:18080/mcp/?token=test&robot_id=gosha-main",
            diagnosticsMode = "cloud-mcp",
            transportState = "configured",
            controlTransport = "cloud-mcp",
            cloudBoardName = "gosha-v1",
            cloudAppVersion = "2.2.2",
        )

        assertFalse(snapshot.connected)
        assertEquals("", snapshot.connectivityEvidence)
    }

    @Test
    fun `runtime snapshot trusts verified probe from panel connectivity summary`() {
        val snapshot = buildSnapshot(
            diagnosticsMode = "cloud-mcp",
            transportState = "configured",
            connectivityHasConnected = true,
            connectivityConnected = true,
            connectivityEvidence = "probe_verified",
            connectivityVerifiedNow = true,
            connectivityLastSeenIso = "2026-05-17T05:42:37Z",
            connectivityBoardName = "gosha-v1",
            connectivityAppVersion = "2.2.2",
        )

        assertTrue(snapshot.connected)
        assertTrue(snapshot.verifiedNow)
        assertEquals("probe_verified", snapshot.connectivityEvidence)
        assertEquals("gosha-v1", snapshot.boardName)
    }

    @Test
    fun `runtime snapshot trusts fresh self-hosted device contact only when server says so`() {
        val snapshot = buildSnapshot(
            diagnosticsTarget = "ws://151.241.228.232:18080/mcp/?token=test&robot_id=gosha-main",
            diagnosticsMode = "cloud-mcp",
            transportState = "configured",
            connectivityHasConnected = true,
            connectivityConnected = true,
            connectivityEvidence = "fresh_device_contact",
            connectivityFreshDeviceContact = true,
            connectivityLastSeenIso = "2026-05-17T05:42:37Z",
        )

        assertTrue(snapshot.connected)
        assertTrue(snapshot.freshDeviceContact)
        assertEquals("fresh_device_contact", snapshot.connectivityEvidence)
    }

    private fun buildSnapshot(
        diagnosticsTarget: String = "",
        fallbackWsUrl: String = "",
        diagnosticsMode: String = "",
        controlTransport: String = "",
        transportState: String = "",
        connectivityHasConnected: Boolean = false,
        connectivityConnected: Boolean = false,
        connectivityLocalHost: String = "",
        connectivityEvidence: String = "",
        connectivityVerifiedNow: Boolean = false,
        connectivityFreshDeviceContact: Boolean = false,
        connectivityLastSeenIso: String = "",
        connectivityBoardName: String = "",
        connectivityAppVersion: String = "",
        cloudLastSeenIso: String = "",
        cloudBoardName: String = "",
        cloudAppVersion: String = "",
    ): RobotRuntimeSnapshot {
        return PanelApiClient.buildRobotRuntimeSnapshot(
            robotId = "gosha-main",
            diagnosticsTarget = diagnosticsTarget,
            fallbackWsUrl = fallbackWsUrl,
            diagnosticsMode = diagnosticsMode,
            controlTransport = controlTransport,
            transportState = transportState,
            connectivityHasConnected = connectivityHasConnected,
            connectivityConnected = connectivityConnected,
            connectivityLocalHost = connectivityLocalHost,
            connectivityEvidence = connectivityEvidence,
            connectivityVerifiedNow = connectivityVerifiedNow,
            connectivityFreshDeviceContact = connectivityFreshDeviceContact,
            connectivityLastSeenIso = connectivityLastSeenIso,
            connectivityBoardName = connectivityBoardName,
            connectivityAppVersion = connectivityAppVersion,
            cloudLastSeenIso = cloudLastSeenIso,
            cloudBoardName = cloudBoardName,
            cloudAppVersion = cloudAppVersion,
        )
    }
}
