package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorConfigTest {
    @Test
    fun `parse cloud endpoint extracts hub token and robot id`() {
        val parts = parseCloudEndpoint("ws://151.241.228.232:18080/mcp/?token=test-token&robot_id=gosha-main")

        assertEquals("ws://151.241.228.232:18080/mcp/", parts.hubBaseUrl)
        assertEquals("test-token", parts.token)
        assertEquals("gosha-main", parts.robotId)
    }

    @Test
    fun `parse cloud endpoint falls back to base url when query is absent`() {
        val parts = parseCloudEndpoint("ws://151.241.228.232:18080/mcp/")

        assertEquals("ws://151.241.228.232:18080/mcp/", parts.hubBaseUrl)
        assertEquals("", parts.token)
        assertEquals("", parts.robotId)
    }

    @Test
    fun `connector config can run presence without edge hub token`() {
        val config = OnboardingDraft(
            robotId = "gosha-main",
            expectedDeviceId = "aa:bb:cc:dd:ee:ff",
            robotHost = "192.168.1.159",
            hubBaseUrl = "",
            token = "",
        ).toConnectorConfigOrNull()

        requireNotNull(config)
        assertEquals("gosha-main", config.robotId)
        assertEquals("aa:bb:cc:dd:ee:ff", config.expectedDeviceId)
        assertEquals("192.168.1.159", config.robotHost)
        assertFalse(config.canRunEdgeHub())
        assertFalse(config.toDebugJson().contains("aa:bb:cc:dd:ee:ff"))
        assertFalse(config.toDebugJson().contains("expectedDeviceId"))
    }

    @Test
    fun `edge hub readiness requires websocket hub token and local host`() {
        val config = ConnectorConfig(
            hubBaseUrl = "ws://edge.example/mcp/",
            robotId = "gosha-main",
            expectedDeviceId = "aa:bb:cc:dd:ee:ff",
            token = "edge-token",
            robotHost = "192.168.1.159",
            robotPort = 8080,
            robotPath = "/ws",
        )

        assertTrue(config.canRunEdgeHub())
        assertEquals(
            "ws://edge.example/mcp/agent/gosha-main?token=edge-token&client=android-app&version=0.1.0",
            config.agentUrl(),
        )
    }

    @Test
    fun `edge hub readiness requires expected device id`() {
        val config = ConnectorConfig(
            hubBaseUrl = "ws://edge.example/mcp/",
            robotId = "gosha-main",
            expectedDeviceId = "",
            token = "edge-token",
            robotHost = "192.168.1.159",
            robotPort = 8080,
            robotPath = "/ws",
        )

        assertFalse(config.canRunEdgeHub())
    }

    @Test
    fun `panel runtime device id wins over stale saved id for local discovery`() {
        val draft = OnboardingDraft(
            robotId = "gosha-main",
            expectedDeviceId = "old-device",
        )
        val panel = RobotRuntimeSnapshot(
            robotId = "gosha-main",
            deviceId = "new-device",
            connected = true,
            mode = "cloud-mcp",
            transportState = "configured",
            target = "",
            localHost = "",
            localHostHint = "192.168.1.159",
            connectivityEvidence = "fresh_device_contact",
            verifiedNow = false,
            freshDeviceContact = true,
            lastSeenIso = "",
            boardName = "",
            appVersion = "",
        )

        val resolved = resolveDiscoveryExpectedDeviceIdentity(
            panelSnapshot = panel,
            draft = draft,
            savedDeviceIdIsAuthoritative = false,
        )

        assertFalse(resolved.hasConflict)
        assertEquals("new-device", resolved.deviceId)
    }

    @Test
    fun `panel runtime device id conflicts with authoritative bundle or current claim id`() {
        val draft = OnboardingDraft(
            robotId = "gosha-main",
            expectedDeviceId = "old-device",
        )
        val panel = RobotRuntimeSnapshot(
            robotId = "gosha-main",
            deviceId = "new-device",
            connected = true,
            mode = "cloud-mcp",
            transportState = "configured",
            target = "",
            localHost = "",
            localHostHint = "192.168.1.159",
            connectivityEvidence = "fresh_device_contact",
            verifiedNow = false,
            freshDeviceContact = true,
            lastSeenIso = "",
            boardName = "",
            appVersion = "",
        )

        val bundleConflict = resolveDiscoveryExpectedDeviceIdentity(
            panelSnapshot = panel,
            draft = draft,
            bundleDeviceId = "bundle-device",
            savedDeviceIdIsAuthoritative = false,
        )
        val currentConflict = resolveDiscoveryExpectedDeviceIdentity(
            panelSnapshot = panel,
            draft = draft,
            savedDeviceIdIsAuthoritative = true,
        )

        assertTrue(bundleConflict.hasConflict)
        assertEquals("bundle-device", bundleConflict.deviceId)
        assertTrue(currentConflict.hasConflict)
        assertEquals("old-device", currentConflict.deviceId)
    }

    @Test
    fun `verified local device id replaces stale or blank saved id`() {
        val stale = OnboardingDraft(
            robotId = "gosha-main",
            expectedDeviceId = "old-device",
        )
        val blank = OnboardingDraft(
            robotId = "gosha-main",
            expectedDeviceId = "",
        )

        assertEquals(
            "new-device",
            resolveSavedExpectedDeviceIdentity(
                current = stale,
                bundleDeviceId = "",
                verifiedLocalDeviceId = "new-device",
                savedDeviceIdIsAuthoritative = false,
            ).deviceId,
        )
        assertEquals(
            "new-device",
            resolveSavedExpectedDeviceIdentity(
                current = blank,
                bundleDeviceId = "",
                verifiedLocalDeviceId = "new-device",
            ).deviceId,
        )
    }

    @Test
    fun `verified local device id cannot replace authoritative bundle or current claim id`() {
        val draft = OnboardingDraft(
            robotId = "gosha-main",
            expectedDeviceId = "old-device",
        )

        val bundleConflict = resolveSavedExpectedDeviceIdentity(
            current = draft,
            bundleDeviceId = "bundle-device",
            verifiedLocalDeviceId = "new-device",
            savedDeviceIdIsAuthoritative = false,
        )
        val currentConflict = resolveSavedExpectedDeviceIdentity(
            current = draft,
            bundleDeviceId = "",
            verifiedLocalDeviceId = "new-device",
            savedDeviceIdIsAuthoritative = true,
        )

        assertTrue(bundleConflict.hasConflict)
        assertEquals("bundle-device", bundleConflict.deviceId)
        assertTrue(currentConflict.hasConflict)
        assertEquals("old-device", currentConflict.deviceId)
    }

    @Test
    fun `runtime url migration preserves token and device identity without relay literals`() {
        val current = OnboardingDraft(
            panelBaseUrl = "https://relay.example.test",
            hubBaseUrl = "wss://relay-hub.example.test/mcp/",
            cloudEndpoint = "wss://relay-voice.example.test/mcp/?token=runtime-token&robot_id=gosha-main",
            robotId = "gosha-main",
            expectedDeviceId = "aa:bb:cc:dd:ee:ff",
            token = "runtime-token",
            panelClientToken = "mobile-token",
            onboardingCode = "claim-code",
            robotHost = "192.168.1.159",
        )

        val runtime = mergeRuntimeEndpointConfig(
            current = current,
            panelBaseUrl = "https://future.example.test",
            edgeHubUrl = "wss://future-hub.example.test/mcp/",
            cloudEndpoint = "wss://future-voice.example.test/mcp/",
            robotId = "",
        )
        val migrated = current.copy(
            panelBaseUrl = runtime.panelBaseUrl,
            hubBaseUrl = runtime.hubBaseUrl,
            cloudEndpoint = runtime.cloudEndpoint,
            robotId = runtime.robotId,
            token = runtime.token,
        )

        assertEquals("https://future.example.test", migrated.panelBaseUrl)
        assertEquals("wss://future-hub.example.test/mcp/", migrated.hubBaseUrl)
        assertEquals("wss://future-voice.example.test/mcp/", migrated.cloudEndpoint)
        assertEquals("gosha-main", migrated.robotId)
        assertEquals("aa:bb:cc:dd:ee:ff", migrated.expectedDeviceId)
        assertEquals("runtime-token", migrated.token)
        assertEquals("mobile-token", migrated.panelClientToken)
        assertEquals("claim-code", migrated.onboardingCode)
        assertEquals("192.168.1.159", migrated.robotHost)
        assertTrue(
            listOf(migrated.panelBaseUrl, migrated.hubBaseUrl, migrated.cloudEndpoint)
                .none { it.contains("relay.example.test") }
        )
    }
}
