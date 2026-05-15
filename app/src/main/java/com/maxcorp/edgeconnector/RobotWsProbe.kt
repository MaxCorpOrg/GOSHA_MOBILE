package com.maxcorp.gosha.mobile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException

object RobotWsProbe {
    suspend fun probe(
        http: OkHttpClient,
        robotWsUrl: String,
        timeoutMs: Long = 4_000,
    ): Pair<Boolean, String> {
        val opened = CompletableDeferred<Unit>()
        val failed = CompletableDeferred<String>()
        val request = Request.Builder().url(robotWsUrl).build()

        val ws: WebSocket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!opened.isCompleted) opened.complete(Unit)
                webSocket.close(1000, "probe-ok")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!failed.isCompleted) {
                    failed.complete(t.message ?: t::class.java.simpleName)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!opened.isCompleted && !failed.isCompleted) {
                    failed.complete("robot websocket closed: $code/$reason")
                }
            }
        })

        return try {
            withTimeout(timeoutMs) { opened.await() }
            true to ""
        } catch (exc: Exception) {
            val message = try {
                withTimeout(250) { failed.await() }
            } catch (_: Exception) {
                exc.message ?: "probe timeout"
            }
            false to message
        } finally {
            ws.cancel()
        }
    }
}
