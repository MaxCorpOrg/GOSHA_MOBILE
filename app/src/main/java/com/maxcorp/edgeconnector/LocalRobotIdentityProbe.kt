package com.maxcorp.gosha.mobile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

object LocalRobotIdentityProbe {
    private const val REQUEST_ID = "gosha-mobile-identity-probe"
    private const val MESSAGE_TYPE_REQUEST = "gosha.identity.get"
    private const val MESSAGE_TYPE_RESULT = "gosha.identity.result"

    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun matches(
        socketFactory: SocketFactory?,
        host: String,
        expectedDeviceId: String,
        port: Int = 8080,
        path: String = "/ws",
        timeoutMs: Long = 2_500L,
    ): Boolean {
        val expected = normalizeDeviceId(expectedDeviceId)
        if (expected.isBlank()) return false
        val timeout = timeoutMs.coerceAtLeast(200L).coerceAtMost(Int.MAX_VALUE.toLong())
        val client = clientFor(socketFactory, timeout)
        val wsUrl = buildWsUrl(host, port, path)
        val result = CompletableDeferred<Boolean>()
        val request = Request.Builder().url(wsUrl).build()

        val ws: WebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = JSONObject()
                    .put("type", MESSAGE_TYPE_REQUEST)
                    .put("protocol_version", 1)
                    .put("id", REQUEST_ID)
                if (!webSocket.send(payload.toString()) && !result.isCompleted) {
                    result.complete(false)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val root = runCatching { JSONObject(text) }.getOrNull() ?: return
                val incomingId = normalizeId(root.opt("id"))
                if (incomingId != null && incomingId != REQUEST_ID) return
                if (root.optString("type", "") != MESSAGE_TYPE_RESULT) return
                if (!root.optBoolean("ok", false)) {
                    if (!result.isCompleted) result.complete(false)
                    webSocket.close(1000, "identity-probe-done")
                    return
                }
                val actual = normalizeDeviceId(extractDeviceId(root))
                if (!result.isCompleted) {
                    result.complete(actual == expected)
                    webSocket.close(1000, "identity-probe-done")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!result.isCompleted) result.complete(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!result.isCompleted) result.complete(false)
            }
        })

        return try {
            withTimeout(timeout) { result.await() }
        } catch (_: TimeoutCancellationException) {
            false
        } catch (exc: CancellationException) {
            throw exc
        } catch (_: Exception) {
            false
        } finally {
            ws.cancel()
        }
    }

    internal fun extractDeviceId(root: JSONObject): String {
        val result = root.optJSONObject("result") ?: root
        val identity = result.optJSONObject("identity") ?: root.optJSONObject("identity")
        return firstNonBlank(
            identity?.optString("device_id", "").orEmpty(),
            identity?.optString("deviceId", "").orEmpty(),
            identity?.optString("mac_address", "").orEmpty(),
            result.optString("device_id", ""),
            result.optString("deviceId", ""),
            result.optString("mac_address", ""),
            root.optString("device_id", ""),
            root.optString("deviceId", ""),
            root.optString("mac_address", ""),
        )
    }

    internal fun normalizeDeviceId(value: String): String {
        return value.trim().lowercase()
    }

    private fun firstNonBlank(vararg values: String): String {
        return values.firstOrNull { it.trim().isNotBlank() }?.trim().orEmpty()
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

    private fun clientFor(socketFactory: SocketFactory?, timeoutMs: Long): OkHttpClient {
        val builder = baseClient.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
        if (socketFactory != null) {
            builder.socketFactory(socketFactory)
        }
        return builder.build()
    }

    private fun buildWsUrl(host: String, port: Int, path: String): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return "ws://$host:$port$normalizedPath"
    }
}
