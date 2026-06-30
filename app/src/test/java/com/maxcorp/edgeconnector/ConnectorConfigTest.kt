package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
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
}
