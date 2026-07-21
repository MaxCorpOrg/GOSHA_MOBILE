package com.maxcorp.gosha.mobile

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RobotJsonRpcProxyTest {
    @Before
    fun resetCoordinator() {
        LocalRobotProbeCoordinator.resetForTests()
    }

    @Test
    fun `notify waits for active diagnostic probe before opening websocket`() = runBlocking {
        val server = websocketServer()
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
                    robotWsUrl = wsUrl(server),
                    payload = JSONObject().put("method", "self.test"),
                    timeoutMs = 2_000L,
                )
            }

            assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
            releaseActive.complete(Unit)
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            notify.await()
            active.await()
            Unit
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `notify ignores diagnostic service rate limit`() = runBlocking {
        val server = websocketServer()
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
                    robotWsUrl = wsUrl(server),
                    payload = JSONObject().put("method", "self.test"),
                    timeoutMs = 2_000L,
                )
            }
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            Unit
        } finally {
            server.shutdown()
        }
    }

    private fun websocketServer(): MockWebServer {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    webSocket.close(1000, "ok")
                }
            })
        )
        server.start()
        return server
    }

    private fun wsUrl(server: MockWebServer): String {
        return server.url("/ws").toString().replaceFirst("http://", "ws://")
    }
}
