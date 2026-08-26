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
    private const val IDENTITY_REQUEST_ID = "gosha-mobile-command-identity-probe"
    private const val IDENTITY_REQUEST_TYPE = "gosha.identity.get"
    private const val IDENTITY_RESULT_TYPE = "gosha.identity.result"

    suspend fun call(
        http: OkHttpClient,
        robotWsUrl: String,
        payload: JSONObject,
        expectedDeviceId: String = "",
        timeoutMs: Long = 8_000,
        isCurrentRun: () -> Boolean = { true },
    ): JSONObject {
        val normalizedExpectedDeviceId = requireExpectedDeviceId(expectedDeviceId)
        return when (
            val run = LocalRobotProbeCoordinator.runFunctionalCommand(
                source = "RobotJsonRpcProxy.call",
            ) {
                callDirect(http, robotWsUrl, payload, normalizedExpectedDeviceId, timeoutMs, isCurrentRun)
            }
        ) {
            is LocalRobotProbeRun.Executed -> run.value
            is LocalRobotProbeRun.Skipped -> throw IOException("local websocket command skipped: ${run.reason}")
        }
    }

    private suspend fun callDirect(
        http: OkHttpClient,
        robotWsUrl: String,
        payload: JSONObject,
        expectedDeviceId: String,
        timeoutMs: Long,
        isCurrentRun: () -> Boolean,
    ): JSONObject {
        requireCurrentRun(isCurrentRun)
        val reqId = normalizeId(payload.opt("id"))
            ?: throw IOException("jsonrpc payload missing id")

        val result = CompletableDeferred<JSONObject>()
        val request = Request.Builder().url(robotWsUrl).build()
        val identityRequest = identityRequestPayload()
        var identityVerified = false

        val ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!completeIfStale(result, webSocket, isCurrentRun)) return
                if (!webSocket.send(identityRequest.toString()) && !result.isCompleted) {
                    result.completeExceptionally(IOException("failed to send identity probe to robot"))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    return
                }
                if (!identityVerified) {
                    val identityMatches = identityMatch(obj, expectedDeviceId) ?: return
                    if (!identityMatches) {
                        if (!result.isCompleted) {
                            result.completeExceptionally(IOException("device identity mismatch"))
                        }
                        webSocket.close(1000, "identity-mismatch")
                        return
                    }
                    identityVerified = true
                    if (!completeIfStale(result, webSocket, isCurrentRun)) return
                    if (!webSocket.send(payload.toString()) && !result.isCompleted) {
                        result.completeExceptionally(IOException("failed to send payload to robot"))
                    }
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
        expectedDeviceId: String = "",
        timeoutMs: Long = 4_000,
        isCurrentRun: () -> Boolean = { true },
    ) {
        val normalizedExpectedDeviceId = requireExpectedDeviceId(expectedDeviceId)
        when (
            val run = LocalRobotProbeCoordinator.runFunctionalCommand(
                source = "RobotJsonRpcProxy.notify",
            ) {
                notifyDirect(http, robotWsUrl, payload, normalizedExpectedDeviceId, timeoutMs, isCurrentRun)
            }
        ) {
            is LocalRobotProbeRun.Executed -> return
            is LocalRobotProbeRun.Skipped -> throw IOException("local websocket command skipped: ${run.reason}")
        }
    }

    private suspend fun notifyDirect(
        http: OkHttpClient,
        robotWsUrl: String,
        payload: JSONObject,
        expectedDeviceId: String,
        timeoutMs: Long,
        isCurrentRun: () -> Boolean,
    ) {
        requireCurrentRun(isCurrentRun)
        val done = CompletableDeferred<Unit>()
        val request = Request.Builder().url(robotWsUrl).build()
        val identityRequest = identityRequestPayload()
        var identityVerified = false
        val ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!completeIfStale(done, webSocket, isCurrentRun)) return
                if (!webSocket.send(identityRequest.toString()) && !done.isCompleted) {
                    done.completeExceptionally(IOException("failed to send identity probe to robot"))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    return
                }
                if (!identityVerified) {
                    val identityMatches = identityMatch(obj, expectedDeviceId) ?: return
                    if (!identityMatches) {
                        if (!done.isCompleted) {
                            done.completeExceptionally(IOException("device identity mismatch"))
                        }
                        webSocket.close(1000, "identity-mismatch")
                        return
                    }
                    identityVerified = true
                    if (!completeIfStale(done, webSocket, isCurrentRun)) return
                    val sent = webSocket.send(payload.toString())
                    if (!sent) {
                        if (!done.isCompleted) done.completeExceptionally(IOException("failed to send notify"))
                        return
                    }
                    if (!webSocket.close(1000, "notify-sent") && !done.isCompleted) {
                        done.completeExceptionally(IOException("failed to close notify websocket"))
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!done.isCompleted) done.completeExceptionally(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (identityVerified && code == 1000) {
                    if (!done.isCompleted) done.complete(Unit)
                } else if (!done.isCompleted) {
                    done.completeExceptionally(IOException("robot websocket closed: $code/$reason"))
                }
            }
        })

        try {
            withTimeout(timeoutMs) { done.await() }
        } finally {
            ws.cancel()
        }
    }

    private fun requireExpectedDeviceId(expectedDeviceId: String): String {
        val normalized = LocalRobotIdentityProbe.normalizeDeviceId(expectedDeviceId)
        if (normalized.isBlank()) {
            throw IOException("expected_device_id_missing")
        }
        return normalized
    }

    private fun requireCurrentRun(isCurrentRun: () -> Boolean) {
        if (!isCurrentRun()) {
            throw IOException(STALE_RUN_MESSAGE)
        }
    }

    private fun completeIfStale(
        result: CompletableDeferred<*>,
        webSocket: WebSocket,
        isCurrentRun: () -> Boolean,
    ): Boolean {
        if (isCurrentRun()) return true
        if (!result.isCompleted) {
            result.completeExceptionally(IOException(STALE_RUN_MESSAGE))
        }
        webSocket.close(1000, "run-superseded")
        return false
    }

    private fun identityRequestPayload(): JSONObject {
        return JSONObject()
            .put("type", IDENTITY_REQUEST_TYPE)
            .put("protocol_version", 1)
            .put("id", IDENTITY_REQUEST_ID)
    }

    private fun identityMatch(obj: JSONObject, expectedDeviceId: String): Boolean? {
        val incomingId = normalizeId(obj.opt("id"))
        if (incomingId != null && incomingId != IDENTITY_REQUEST_ID) return null
        if (obj.optString("type", "") != IDENTITY_RESULT_TYPE) return null
        if (!obj.optBoolean("ok", false)) return false
        val actual = LocalRobotIdentityProbe.normalizeDeviceId(
            LocalRobotIdentityProbe.extractDeviceId(obj)
        )
        return actual == expectedDeviceId
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

    private const val STALE_RUN_MESSAGE = "connector run superseded"
}
