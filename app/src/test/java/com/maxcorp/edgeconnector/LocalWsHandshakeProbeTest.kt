package com.maxcorp.gosha.mobile

import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalWsHandshakeProbeTest {
    @Test
    fun `successful local handshake completes websocket close frame`() = runBlocking {
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
            val ok = LocalWsHandshakeProbe.isOpen(
                socketFactory = null,
                host = server.hostName,
                port = server.port,
                path = "/ws",
                timeoutMs = 2_000,
            )

            assertTrue(ok)
            assertTrue(closeObserved.await(2, TimeUnit.SECONDS))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `successful local handshake uses provided socket factory`() = runBlocking {
        val closeObserved = CountDownLatch(1)
        val socketFactory = RecordingSocketFactory()
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
            val ok = LocalWsHandshakeProbe.isOpen(
                socketFactory = socketFactory,
                host = server.hostName,
                port = server.port,
                path = "/ws",
                timeoutMs = 2_000,
            )

            assertTrue(ok)
            assertTrue(socketFactory.createSocketCalls.get() > 0)
            assertTrue(closeObserved.await(2, TimeUnit.SECONDS))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `local handshake propagates coroutine cancellation`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.start()

        try {
            val probe = async(Dispatchers.IO) {
                LocalWsHandshakeProbe.isOpen(
                    socketFactory = null,
                    host = server.hostName,
                    port = server.port,
                    path = "/ws",
                    timeoutMs = 30_000,
                )
            }

            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            probe.cancel()

            val result = runCatching { probe.await() }
            assertTrue(result.exceptionOrNull() is CancellationException)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `failed local websocket upgrade is not reported open`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        server.start()

        try {
            val ok = LocalWsHandshakeProbe.isOpen(
                socketFactory = null,
                host = server.hostName,
                port = server.port,
                path = "/ws",
                timeoutMs = 1_000,
            )

            assertFalse(ok)
        } finally {
            server.shutdown()
        }
    }

    private class RecordingSocketFactory(
        private val delegate: SocketFactory = SocketFactory.getDefault(),
    ) : SocketFactory() {
        val createSocketCalls = AtomicInteger(0)

        override fun createSocket(): Socket =
            delegate.createSocket().also { createSocketCalls.incrementAndGet() }

        override fun createSocket(host: String, port: Int): Socket =
            delegate.createSocket(host, port).also { createSocketCalls.incrementAndGet() }

        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            delegate.createSocket(host, port, localHost, localPort)
                .also { createSocketCalls.incrementAndGet() }

        override fun createSocket(host: InetAddress, port: Int): Socket =
            delegate.createSocket(host, port).also { createSocketCalls.incrementAndGet() }

        override fun createSocket(
            address: InetAddress,
            port: Int,
            localAddress: InetAddress,
            localPort: Int,
        ): Socket =
            delegate.createSocket(address, port, localAddress, localPort)
                .also { createSocketCalls.incrementAndGet() }
    }
}
