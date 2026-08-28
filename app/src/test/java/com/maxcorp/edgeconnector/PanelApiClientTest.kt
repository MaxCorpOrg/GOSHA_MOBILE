package com.maxcorp.gosha.mobile

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelApiClientTest {
    @Test
    fun `runtime outbox drops only permanently invalid events`() {
        assertTrue(isPermanentRuntimeEventRejection(PanelHttpException(413, "too large")))
        assertTrue(isPermanentRuntimeEventRejection(PanelHttpException(422, "unsupported")))
        assertFalse(isPermanentRuntimeEventRejection(PanelHttpException(400, "temporary old server response")))
        assertFalse(isPermanentRuntimeEventRejection(PanelHttpException(401, "rotate credentials")))
        assertFalse(isPermanentRuntimeEventRejection(PanelHttpException(404, "rolling deployment")))
    }

    @Test
    fun `runtime outbox keeps newest bounded events in delivery order`() {
        val source = JSONArray()
        for (index in 0 until 105) {
            source.put(JSONObject().put("event_id", "event-$index"))
        }
        val trimmed = trimRuntimeEventOutbox(source, maxEvents = 100)
        assertEquals(100, trimmed.length())
        assertEquals("event-5", trimmed.getJSONObject(0).getString("event_id"))
        assertEquals("event-104", trimmed.getJSONObject(99).getString("event_id"))
    }

    @Test
    fun `runtime event uses robot scoped mobile route and access header`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"ok\":true}"))
        server.start()
        try {
            val event = JSONObject()
                .put("schema_version", RuntimeEventReporter.SCHEMA_VERSION)
                .put("event_id", "event-1")
                .put("event_type", "mobile.network.state_changed")
                .put("source", JSONObject().put("id", "mobile-installation-1"))
            PanelApiClient.publishRuntimeEvent(
                http = OkHttpClient(),
                baseUrl = server.url("/").toString(),
                robotId = "robot-01",
                event = event,
                panelClientToken = "mobile-access-token",
            )
            val request = server.takeRequest()
            assertEquals("/api/mobile/robots/robot-01/events", request.path)
            assertEquals("mobile-access-token", request.getHeader("X-Mobile-Token"))
            assertEquals("mobile.network.state_changed", JSONObject(request.body.readUtf8()).getString("event_type"))
        } finally {
            server.shutdown()
        }
    }

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

    @Test
    fun `runtime snapshot keeps board ip as local host hint when direct local host is absent`() {
        val snapshot = buildSnapshot(
            diagnosticsTarget = "ws://151.241.228.232:18080/mcp/?token=test&robot_id=gosha-main",
            diagnosticsMode = "cloud-mcp",
            transportState = "configured",
            cloudBoardIp = "192.168.1.159",
        )

        assertEquals("", snapshot.localHost)
        assertEquals("192.168.1.159", snapshot.localHostHint)
        assertFalse(snapshot.connected)
    }

    @Test
    fun `runtime snapshot exposes claimed cloud device id`() {
        val snapshot = buildSnapshot(
            cloudDeviceId = "aa:bb:cc:dd:ee:ff",
        )

        assertEquals("aa:bb:cc:dd:ee:ff", snapshot.deviceId)
    }

    @Test
    fun `presence payload sends local host only for home wifi local state`() {
        val payload = PanelApiClient.buildMobilePresencePayloadData(
            state = MobilePresenceState.HOME_WIFI_LOCAL,
            localHost = "192.168.0.103",
        )

        assertEquals("home_wifi_local", payload.state)
        assertEquals("android_local_discovery", payload.source)
        assertEquals("192.168.0.103", payload.localHost)
    }

    @Test
    fun `presence payload ignores local host for non local states`() {
        val payload = PanelApiClient.buildMobilePresencePayloadData(
            state = MobilePresenceState.ROBOT_HOTSPOT_VISIBLE,
            localHost = "192.168.0.103",
        )

        assertEquals("robot_hotspot_visible", payload.state)
        assertEquals("android_local_discovery", payload.source)
        assertEquals("", payload.localHost)
    }

    @Test
    fun `onboarding bundle keeps edge hub separate from mcp endpoint`() {
        val bundle = PanelApiClient.parseOnboardingBundle(
            JSONObject()
                .put("robot_id", "gosha-main")
                .put("edge_hub_url", "ws://edge.example/mcp")
                .put("cloud_endpoint", "ws://mcp.example/mcp/?token=mcp-token&robot_id=gosha-main")
                .put(
                    "selfhost_xiaozhi",
                    JSONObject()
                        .put("provider", "selfhost_xiaozhi")
                        .put("device_id", "aa:bb:cc:dd:ee:ff")
                )
                .put(
                    "mobile_profile",
                    JSONObject()
                        .put("mcp_endpoint_base", "ws://mcp.example/mcp/")
                        .put("websocket_url", "ws://voice.example/xiaozhi/v1/")
                )
        )

        assertEquals("ws://edge.example/mcp", bundle.edgeHubUrl)
        assertEquals("ws://mcp.example/mcp/?token=mcp-token&robot_id=gosha-main", bundle.cloudEndpoint)
        assertEquals("aa:bb:cc:dd:ee:ff", bundle.selfhostXiaozhi?.deviceId)
        assertEquals("ws://mcp.example/mcp/", bundle.mobileProfile?.mcpEndpointBase)
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
        connectivityBoardIp: String = "",
        connectivityEvidence: String = "",
        connectivityVerifiedNow: Boolean = false,
        connectivityFreshDeviceContact: Boolean = false,
        connectivityLastSeenIso: String = "",
        connectivityBoardName: String = "",
        connectivityAppVersion: String = "",
        cloudDeviceId: String = "",
        cloudLastSeenIso: String = "",
        cloudBoardName: String = "",
        cloudBoardIp: String = "",
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
            connectivityBoardIp = connectivityBoardIp,
            connectivityEvidence = connectivityEvidence,
            connectivityVerifiedNow = connectivityVerifiedNow,
            connectivityFreshDeviceContact = connectivityFreshDeviceContact,
            connectivityLastSeenIso = connectivityLastSeenIso,
            connectivityBoardName = connectivityBoardName,
            connectivityAppVersion = connectivityAppVersion,
            cloudDeviceId = cloudDeviceId,
            cloudLastSeenIso = cloudLastSeenIso,
            cloudBoardName = cloudBoardName,
            cloudBoardIp = cloudBoardIp,
            cloudAppVersion = cloudAppVersion,
        )
    }
}
