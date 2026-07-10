package com.maxcorp.gosha.mobile

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotWsProbeTest {
    @Test
    fun `successful probe completes websocket close handshake`() = runBlocking {
        val closeObserved = CountDownLatch(1)
        val server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    closeObserved.countDown()
                    webSocket.close(code, reason)
                }
            })
        )
        server.start()

        try {
            val wsUrl = server.url("/ws").toString().replaceFirst("http://", "ws://")
            val result = RobotWsProbe.probe(OkHttpClient(), wsUrl, timeoutMs = 2_000)

            assertTrue(result.first)
            assertTrue(closeObserved.await(2, TimeUnit.SECONDS))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `failed websocket upgrade is reported as unreachable`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        server.start()

        try {
            val wsUrl = server.url("/ws").toString().replaceFirst("http://", "ws://")
            val result = RobotWsProbe.probe(OkHttpClient(), wsUrl, timeoutMs = 1_000)

            assertFalse(result.first)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `opened probe returns after close grace when peer does not answer close`() = runBlocking {
        val closeObserved = CountDownLatch(1)
        val socketEnded = CountDownLatch(1)
        val server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    closeObserved.countDown()
                    Thread.sleep(750)
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    socketEnded.countDown()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    socketEnded.countDown()
                }
            })
        )
        server.start()

        try {
            val wsUrl = server.url("/ws").toString().replaceFirst("http://", "ws://")
            val startedAt = System.nanoTime()
            val result = RobotWsProbe.probe(OkHttpClient(), wsUrl, timeoutMs = 2_000)
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

            assertTrue(result.first)
            assertTrue(closeObserved.await(2, TimeUnit.SECONDS))
            assertTrue("probe exceeded close grace: ${elapsedMs}ms", elapsedMs < 1_500)
            assertTrue(socketEnded.await(2, TimeUnit.SECONDS))
        } finally {
            server.shutdown()
        }
    }
}
