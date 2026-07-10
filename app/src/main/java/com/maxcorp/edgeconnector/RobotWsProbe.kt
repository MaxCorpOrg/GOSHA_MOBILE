package com.maxcorp.gosha.mobile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

object RobotWsProbe {
    private const val CLOSE_GRACE_MS = 500L

    suspend fun probe(
        http: OkHttpClient,
        robotWsUrl: String,
        timeoutMs: Long = 4_000,
    ): Pair<Boolean, String> {
        val opened = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        val failed = CompletableDeferred<String>()
        val request = Request.Builder().url(robotWsUrl).build()

        val ws: WebSocket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!opened.isCompleted) opened.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!failed.isCompleted) {
                    failed.complete(t.message ?: t::class.java.simpleName)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed.isCompleted) closed.complete(Unit)
                if (!opened.isCompleted && !failed.isCompleted) {
                    failed.complete("robot websocket closed: $code/$reason")
                }
            }
        })

        return try {
            withTimeout(timeoutMs) { opened.await() }
            // Даем OkHttp отправить корректный маскированный close-frame до cancel().
            // При немедленном cancel ESP32 видел оборванный кадр закрытия и не мог
            // штатно удалить эту проверочную WebSocket-сессию.
            ws.close(1000, "probe-ok")
            withTimeoutOrNull(CLOSE_GRACE_MS) { closed.await() }
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
