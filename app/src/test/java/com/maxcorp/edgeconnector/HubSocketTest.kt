package com.maxcorp.gosha.mobile

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubSocketTest {
    @Test
    fun `connect waits for matching agent ready before returning`() = runBlocking {
        val serverSocket = CompletableDeferred<WebSocket>()
        val server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    serverSocket.complete(webSocket)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }
            })
        )
        server.start()

        try {
            val wsUrl = server.url("/mcp/agent/gosha-main?token=test").toString()
                .replaceFirst("http://", "ws://")
            val connection = async {
                HubSocket.connect(
                    http = OkHttpClient(),
                    url = wsUrl,
                    expectedRobotId = "gosha-main",
                    timeoutMs = 2_000,
                )
            }

            val ws = serverSocket.await()
            delay(100L)
            assertFalse(connection.isCompleted)

            ws.send(
                JSONObject()
                    .put("type", "agent_ready")
                    .put("robot_id", "gosha-main")
                    .toString()
            )

            connection.await().close()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `connect rejects agent ready for another robot`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(
                        JSONObject()
                            .put("type", "agent_ready")
                            .put("robot_id", "other-robot")
                            .toString()
                    )
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }
            })
        )
        server.start()

        try {
            val wsUrl = server.url("/mcp/agent/gosha-main?token=test").toString()
                .replaceFirst("http://", "ws://")
            val result = runCatching {
                HubSocket.connect(
                    http = OkHttpClient(),
                    url = wsUrl,
                    expectedRobotId = "gosha-main",
                    timeoutMs = 1_000,
                )
            }

            assertTrue(result.exceptionOrNull() is IOException)
        } finally {
            server.shutdown()
        }
    }
}
