package com.maxcorp.gosha.mobile

import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RobotJsonRpcProxyTest {
    @Before
    fun resetCoordinator() {
        LocalRobotProbeCoordinator.resetForTests()
    }

    @Test
    fun `notify waits for active diagnostic probe before opening websocket`() = runBlocking {
        val robot = websocketServer()
        try {
            val activeStarted = CompletableDeferred<Unit>()
            val releaseActive = CompletableDeferred<Unit>()
            val active = async(Dispatchers.IO) {
                LocalRobotProbeCoordinator.runServiceProbe(
                    source = "ConnectorForegroundService.probeRobotWs",
                    minIntervalMs = 0L,
                ) {
                    activeStarted.complete(Unit)
                    releaseActive.await()
                }
            }
            activeStarted.await()

            val notify = async(Dispatchers.IO) {
                RobotJsonRpcProxy.notify(
                    http = OkHttpClient(),
                    robotWsUrl = wsUrl(robot.server),
                    payload = JSONObject().put("method", "self.test"),
                    expectedDeviceId = "aa:bb:cc:dd:ee:ff",
                    timeoutMs = 2_000L,
                )
            }

            assertNull(robot.server.takeRequest(200, TimeUnit.MILLISECONDS))
            releaseActive.complete(Unit)
            assertNotNull(robot.server.takeRequest(2, TimeUnit.SECONDS))
            notify.await()
            active.await()
            waitForMessages(robot.messages, 2)
            Unit
        } finally {
            robot.server.shutdown()
        }
    }

    @Test
    fun `notify ignores diagnostic service rate limit`() = runBlocking {
        val robot = websocketServer()
        try {
            LocalRobotProbeCoordinator.runServiceProbe(
                source = "ConnectorForegroundService.probeRobotWs",
                minIntervalMs = 10_000L,
            ) {
                Unit
            }

            withTimeout(800L) {
                RobotJsonRpcProxy.notify(
                    http = OkHttpClient(),
                    robotWsUrl = wsUrl(robot.server),
                    payload = JSONObject().put("method", "self.test"),
                    expectedDeviceId = "aa:bb:cc:dd:ee:ff",
                    timeoutMs = 2_000L,
                )
            }
            assertNotNull(robot.server.takeRequest(1, TimeUnit.SECONDS))
            waitForMessages(robot.messages, 2)
            Unit
        } finally {
            robot.server.shutdown()
        }
    }

    @Test
    fun `notify verifies identity then sends notify on same websocket session`() = runBlocking {
        val robot = websocketServer()
        try {
            RobotJsonRpcProxy.notify(
                http = OkHttpClient(),
                robotWsUrl = wsUrl(robot.server),
                payload = JSONObject().put("method", "self.test"),
                expectedDeviceId = "aa:bb:cc:dd:ee:ff",
                timeoutMs = 2_000L,
            )

            val messages = waitForMessages(robot.messages, 2)
            assertEquals(1, robot.server.requestCount)
            assertEquals("gosha.identity.get", JSONObject(messages[0]).getString("type"))
            assertEquals("self.test", JSONObject(messages[1]).getString("method"))
        } finally {
            robot.server.shutdown()
        }
    }

    @Test
    fun `call verifies identity then sends request on same websocket session`() = runBlocking {
        val robot = websocketServer(respondToCommand = true)
        try {
            val response = RobotJsonRpcProxy.call(
                http = OkHttpClient(),
                robotWsUrl = wsUrl(robot.server),
                payload = JSONObject()
                    .put("id", "request-1")
                    .put("method", "self.test"),
                expectedDeviceId = "aa:bb:cc:dd:ee:ff",
                timeoutMs = 2_000L,
            )

            val messages = waitForMessages(robot.messages, 2)
            assertEquals(1, robot.server.requestCount)
            assertEquals("request-1", response.getString("id"))
            assertEquals("gosha.identity.get", JSONObject(messages[0]).getString("type"))
            assertEquals("self.test", JSONObject(messages[1]).getString("method"))
        } finally {
            robot.server.shutdown()
        }
    }

    @Test
    fun `notify rejects missing expected device id before opening websocket`() = runBlocking {
        val robot = websocketServer()
        try {
            val error = expectIoFailure {
                RobotJsonRpcProxy.notify(
                    http = OkHttpClient(),
                    robotWsUrl = wsUrl(robot.server),
                    payload = JSONObject().put("method", "self.test"),
                    expectedDeviceId = "",
                    timeoutMs = 2_000L,
                )
            }

            assertEquals("expected_device_id_missing", error.message)
            assertNull(robot.server.takeRequest(200, TimeUnit.MILLISECONDS))
        } finally {
            robot.server.shutdown()
        }
    }

    @Test
    fun `notify rejects mismatched device identity before command message`() = runBlocking {
        val robot = websocketServer(identityDeviceId = "11:22:33:44:55:66")
        try {
            val error = expectIoFailure {
                RobotJsonRpcProxy.notify(
                    http = OkHttpClient(),
                    robotWsUrl = wsUrl(robot.server),
                    payload = JSONObject().put("method", "self.test"),
                    expectedDeviceId = "aa:bb:cc:dd:ee:ff",
                    timeoutMs = 2_000L,
                )
            }

            val messages = waitForMessages(robot.messages, 1)
            assertEquals("device identity mismatch", error.message)
            assertEquals(1, messages.size)
            assertEquals("gosha.identity.get", JSONObject(messages.single()).getString("type"))
        } finally {
            robot.server.shutdown()
        }
    }

    @Test
    fun `call rejects mismatched device identity before command message`() = runBlocking {
        val robot = websocketServer(identityDeviceId = "11:22:33:44:55:66")
        try {
            val error = expectIoFailure {
                RobotJsonRpcProxy.call(
                    http = OkHttpClient(),
                    robotWsUrl = wsUrl(robot.server),
                    payload = JSONObject()
                        .put("id", "request-1")
                        .put("method", "self.test"),
                    expectedDeviceId = "aa:bb:cc:dd:ee:ff",
                    timeoutMs = 2_000L,
                )
            }

            val messages = waitForMessages(robot.messages, 1)
            assertEquals("device identity mismatch", error.message)
            assertEquals(1, messages.size)
            assertEquals("gosha.identity.get", JSONObject(messages.single()).getString("type"))
        } finally {
            robot.server.shutdown()
        }
    }

    private data class TestRobotWsServer(
        val server: MockWebServer,
        val messages: MutableList<String>,
    )

    private fun websocketServer(
        identityDeviceId: String = "aa:bb:cc:dd:ee:ff",
        respondToCommand: Boolean = false,
    ): TestRobotWsServer {
        val messages = Collections.synchronizedList(mutableListOf<String>())
        val server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    messages.add(text)
                    val obj = runCatching { JSONObject(text) }.getOrNull() ?: return
                    if (obj.optString("type") == "gosha.identity.get") {
                        webSocket.send(identityResult(identityDeviceId).toString())
                        return
                    }
                    if (respondToCommand) {
                        webSocket.send(
                            JSONObject()
                                .put("id", obj.opt("id"))
                                .put("result", JSONObject().put("ok", true))
                                .toString()
                        )
                    } else {
                        webSocket.close(1000, "ok")
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }
            })
        )
        server.start()
        return TestRobotWsServer(server, messages)
    }

    private fun identityResult(deviceId: String): JSONObject {
        return JSONObject()
            .put("type", "gosha.identity.result")
            .put("protocol_version", 1)
            .put("ok", true)
            .put("id", "gosha-mobile-command-identity-probe")
            .put("identity", JSONObject().put("device_id", deviceId))
    }

    private fun wsUrl(server: MockWebServer): String {
        return server.url("/ws").toString().replaceFirst("http://", "ws://")
    }

    private suspend fun waitForMessages(messages: MutableList<String>, count: Int): List<String> {
        withTimeout(2_000L) {
            while (messages.size < count) {
                delay(10L)
            }
        }
        return messages.toList()
    }

    private suspend fun expectIoFailure(block: suspend () -> Unit): IOException {
        return try {
            block()
            fail("Expected IOException")
            error("unreachable")
        } catch (exc: IOException) {
            exc
        }
    }
}
