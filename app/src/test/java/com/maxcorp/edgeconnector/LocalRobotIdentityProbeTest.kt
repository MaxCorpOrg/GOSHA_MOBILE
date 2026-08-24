package com.maxcorp.gosha.mobile

import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRobotIdentityProbeTest {
    @Test
    fun `identity probe sends local identity request and matches expected device id`() = runBlocking {
        val receivedMessages = mutableListOf<String>()
        val server = identityServer(
            JSONObject()
                .put("type", "gosha.identity.result")
                .put("protocol_version", 1)
                .put("ok", true)
                .put("id", "gosha-mobile-identity-probe")
                .put(
                    "identity",
                    JSONObject()
                        .put("device_id", "AA:BB:CC:DD:EE:FF")
                        .put("client_id", "client-1")
                )
        ) { text -> receivedMessages.add(text) }

        try {
            val matched = LocalRobotIdentityProbe.matches(
                socketFactory = null,
                host = server.hostName,
                expectedDeviceId = "aa:bb:cc:dd:ee:ff",
                port = server.port,
                timeoutMs = 2_000,
            )

            assertTrue(matched)
            val request = JSONObject(receivedMessages.single())
            assertEquals("gosha.identity.get", request.getString("type"))
            assertEquals(1, request.getInt("protocol_version"))
            assertEquals("gosha-mobile-identity-probe", request.getString("id"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `identity probe accepts mac address fallback`() = runBlocking {
        val server = identityServer(
            JSONObject()
                .put("type", "gosha.identity.result")
                .put("ok", true)
                .put("id", "gosha-mobile-identity-probe")
                .put("identity", JSONObject().put("mac_address", "AA:BB:CC:DD:EE:FF"))
        )

        try {
            val matched = LocalRobotIdentityProbe.matches(
                socketFactory = null,
                host = server.hostName,
                expectedDeviceId = "aa:bb:cc:dd:ee:ff",
                port = server.port,
                timeoutMs = 2_000,
            )

            assertTrue(matched)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `identity probe rejects another device id`() = runBlocking {
        val server = identityServer(
            JSONObject()
                .put("type", "gosha.identity.result")
                .put("ok", true)
                .put("id", "gosha-mobile-identity-probe")
                .put("identity", JSONObject().put("device_id", "11:22:33:44:55:66"))
        )

        try {
            val matched = LocalRobotIdentityProbe.matches(
                socketFactory = null,
                host = server.hostName,
                expectedDeviceId = "aa:bb:cc:dd:ee:ff",
                port = server.port,
                timeoutMs = 2_000,
            )

            assertFalse(matched)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract device id accepts nested identity fields`() {
        val root = JSONObject()
            .put("identity", JSONObject().put("device_id", "AA:BB:CC:DD:EE:FF"))

        assertEquals("AA:BB:CC:DD:EE:FF", LocalRobotIdentityProbe.extractDeviceId(root))
    }

    private fun identityServer(
        responsePayload: JSONObject,
        onRequest: (String) -> Unit = {},
    ): MockWebServer {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    onRequest(text)
                    webSocket.send(responsePayload.toString())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onOpen(webSocket: WebSocket, response: Response) = Unit
            })
        )
        server.start()
        return server
    }
}
