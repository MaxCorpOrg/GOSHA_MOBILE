package com.maxcorp.gosha.mobile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException

object RobotJsonRpcProxy {
    suspend fun call(
        http: OkHttpClient,
        robotWsUrl: String,
        payload: JSONObject,
        timeoutMs: Long = 8_000,
    ): JSONObject {
        val reqId = normalizeId(payload.opt("id"))
            ?: throw IOException("jsonrpc payload missing id")

        val result = CompletableDeferred<JSONObject>()
        val request = Request.Builder().url(robotWsUrl).build()

        val ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val sent = webSocket.send(payload.toString())
                if (!sent && !result.isCompleted) {
                    result.completeExceptionally(IOException("failed to send payload to robot"))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    return
                }
                val incomingId = normalizeId(obj.opt("id"))
                if (incomingId == reqId && !result.isCompleted) {
                    result.complete(obj)
                    webSocket.close(1000, "done")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!result.isCompleted) {
                    result.completeExceptionally(t)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!result.isCompleted) {
                    result.completeExceptionally(IOException("robot websocket closed: $code/$reason"))
                }
            }
        })

        return try {
            withTimeout(timeoutMs) {
                result.await()
            }
        } finally {
            ws.cancel()
        }
    }

    suspend fun notify(
        http: OkHttpClient,
        robotWsUrl: String,
        payload: JSONObject,
        timeoutMs: Long = 4_000,
    ) {
        val done = CompletableDeferred<Unit>()
        val request = Request.Builder().url(robotWsUrl).build()
        val ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val sent = webSocket.send(payload.toString())
                if (!sent) {
                    if (!done.isCompleted) done.completeExceptionally(IOException("failed to send notify"))
                    return
                }
                if (!done.isCompleted) done.complete(Unit)
                webSocket.close(1000, "notify-sent")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!done.isCompleted) done.completeExceptionally(t)
            }
        })

        try {
            withTimeout(timeoutMs) { done.await() }
        } finally {
            ws.cancel()
        }
    }

    private fun normalizeId(any: Any?): String? {
        return when (any) {
            null -> null
            is Number -> any.toString()
            is String -> any
            is Boolean -> any.toString()
            else -> null
        }
    }
}
