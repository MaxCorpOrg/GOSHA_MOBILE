package com.maxcorp.gosha.mobile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException

class HubSocket private constructor(
    private val ws: WebSocket,
    val incoming: Channel<String>,
    private val closed: CompletableDeferred<String>,
) {
    fun send(text: String): Boolean = ws.send(text)

    fun close(code: Int = 1000, reason: String = "bye") {
        ws.close(code, reason)
        incoming.close()
    }

    suspend fun awaitClosed(timeoutMs: Long = 100): String {
        return try {
            withTimeout(timeoutMs) { closed.await() }
        } catch (_: Exception) {
            "closed"
        }
    }

    companion object {
        suspend fun connect(http: OkHttpClient, url: String, timeoutMs: Long = 10_000): HubSocket {
            val incoming = Channel<String>(Channel.UNLIMITED)
            val opened = CompletableDeferred<Unit>()
            val closed = CompletableDeferred<String>()

            val request = Request.Builder().url(url).build()
            lateinit var socket: WebSocket
            socket = http.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!opened.isCompleted) opened.complete(Unit)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    incoming.trySend(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!closed.isCompleted) closed.complete("$code/$reason")
                    incoming.close()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!opened.isCompleted) {
                        opened.completeExceptionally(t)
                    }
                    if (!closed.isCompleted) {
                        closed.complete("error: ${t.message ?: t::class.java.simpleName}")
                    }
                    incoming.close(t)
                }
            })

            try {
                withTimeout(timeoutMs) { opened.await() }
            } catch (exc: Exception) {
                socket.cancel()
                throw IOException("failed to connect to hub: ${exc.message}", exc)
            }

            return HubSocket(socket, incoming, closed)
        }
    }
}
