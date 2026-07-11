package com.maxcorp.gosha.mobile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

object RobotWsProbe {
    private const val CLOSE_GRACE_MS = 1_500L

    suspend fun probe(
        http: OkHttpClient,
        robotWsUrl: String,
        timeoutMs: Long = 4_000,
    ): Pair<Boolean, String> {
        val opening = CompletableDeferred<Pair<Boolean, String>>()
        val closed = CompletableDeferred<Unit>()
        val request = Request.Builder().url(robotWsUrl).build()

        val ws: WebSocket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!opening.isCompleted) opening.complete(true to "")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!opening.isCompleted) {
                    opening.complete(false to (t.message ?: t::class.java.simpleName))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed.isCompleted) closed.complete(Unit)
                if (!opening.isCompleted) {
                    opening.complete(false to "robot websocket closed: $code/$reason")
                }
            }
        })

        return try {
            val (opened, error) = withTimeout(timeoutMs) { opening.await() }
            if (!opened) {
                return false to error
            }
            // Даем OkHttp отправить корректный маскированный close-frame до cancel().
            // При немедленном cancel ESP32 видел оборванный кадр закрытия и не мог
            // штатно удалить эту проверочную WebSocket-сессию.
            ws.close(1000, "probe-ok")
            withTimeoutOrNull(CLOSE_GRACE_MS) { closed.await() }
            true to ""
        } catch (_: TimeoutCancellationException) {
            false to "probe timeout"
        } catch (exc: CancellationException) {
            throw exc
        } catch (exc: Exception) {
            false to (exc.message ?: "probe timeout")
        } finally {
            ws.cancel()
        }
    }
}
