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
            robotHost = "192.168.1.159",
            hubBaseUrl = "",
            token = "",
        ).toConnectorConfigOrNull()

        requireNotNull(config)
        assertEquals("gosha-main", config.robotId)
        assertEquals("192.168.1.159", config.robotHost)
        assertFalse(config.canRunEdgeHub())
    }

    @Test
    fun `edge hub readiness requires websocket hub token and local host`() {
        val config = ConnectorConfig(
            hubBaseUrl = "ws://edge.example/mcp/",
            robotId = "gosha-main",
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
}
