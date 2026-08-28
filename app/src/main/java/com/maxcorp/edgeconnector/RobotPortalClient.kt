package com.maxcorp.gosha.mobile

import android.content.Context
import android.net.Network
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object RobotPortalClient {
    private const val LOG_TAG = "RobotPortalClient"
    private const val PORTAL_BASE_URL = "http://192.168.4.1"

    data class PortalResponse(
        val url: String,
        val code: Int,
        val contentType: String,
        val bodyBytes: ByteArray,
    ) {
        fun bodyText(): String = bodyBytes.toString(Charsets.UTF_8)
        fun bodyStream(): InputStream = ByteArrayInputStream(bodyBytes)
    }

    fun fetch(context: Context, target: String): PortalResponse {
        return request(context, target, "GET")
    }

    fun request(
        context: Context,
        target: String,
        method: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ): PortalResponse {
        val url = normalizeUrl(target)
        val normalizedMethod = method.uppercase()
        val normalizedBody = if (normalizedMethod == "GET" || normalizedMethod == "HEAD") {
            null
        } else {
            body
        }
        return open(context, url, normalizedMethod, normalizedBody, contentType)
    }

    fun submit(
        context: Context,
        action: String,
        method: String,
        fields: Map<String, String>,
    ): PortalResponse {
        val url = normalizeUrl(action)
        if (method.equals("GET", ignoreCase = true)) {
            val query = formEncode(fields)
            val full = if (query.isBlank()) url else if (url.contains("?")) "$url&$query" else "$url?$query"
            return open(context, full, "GET", null, null)
        }
        return request(
            context = context,
            target = url,
            method = method.uppercase(),
            body = formEncode(fields).toByteArray(Charsets.UTF_8),
            contentType = "application/x-www-form-urlencoded",
        )
    }

    fun submitJson(
        context: Context,
        action: String,
        jsonBody: String,
    ): PortalResponse {
        return request(
            context = context,
            target = action,
            method = "POST",
            body = jsonBody.toByteArray(Charsets.UTF_8),
            contentType = "application/json",
        )
    }

    fun postEmpty(
        context: Context,
        action: String,
    ): PortalResponse {
        return request(
            context = context,
            target = action,
            method = "POST",
            body = ByteArray(0),
            contentType = "application/json",
        )
    }

    private fun open(
        context: Context,
        url: String,
        method: String,
        body: ByteArray?,
        contentType: String?,
    ): PortalResponse {
        val robotCandidates = buildList {
            RobotWifiConnector.preferredRobotWifiNetwork(context)?.let(::add)
            RobotWifiConnector.currentRobotWifiNetwork(context)?.let(::add)
        }.distinct()
        val candidates = buildList<Network?> {
            addAll(robotCandidates)
            if (shouldIncludeDefaultNetworkCandidate(url, robotCandidates.isNotEmpty())) {
                add(null)
            }
        }

        var lastResponse: PortalResponse? = null
        var lastError: Exception? = null
        for (network in candidates) {
            try {
                val response = openOnce(url, method, body, contentType, network)
                Log.d(
                    LOG_TAG,
                    "Portal request candidate=${network ?: "default"} url=$url code=${response.code} type=${response.contentType} bytes=${response.bodyBytes.size}"
                )
                if (response.code != 0 || response.bodyBytes.isNotEmpty()) {
                    return response
                }
                lastResponse = response
            } catch (error: Exception) {
                lastError = error
                Log.w(
                    LOG_TAG,
                    "Portal request failed on candidate=${network ?: "default"} for $method $url: ${error.message}",
                    error
                )
            }
        }

        return lastResponse ?: throw lastError ?: IllegalStateException("No portal route available")
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun shouldIncludeDefaultNetworkCandidate(
        url: String,
        hasRobotNetworkCandidate: Boolean,
    ): Boolean {
        return !isRobotPortalUrl(url)
    }

    private fun openOnce(
        url: String,
        method: String,
        body: ByteArray?,
        contentType: String?,
        network: Network?,
    ): PortalResponse {
        val connection = ((network?.openConnection(URL(url)) ?: URL(url).openConnection()) as HttpURLConnection).apply {
            connectTimeout = 2500
            readTimeout = 4000
            instanceFollowRedirects = true
            requestMethod = method
            useCaches = false
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
            setRequestProperty("User-Agent", "Gosha-Connector/1.0")
            if (body != null) {
                doOutput = true
                if (!contentType.isNullOrBlank()) {
                    setRequestProperty("Content-Type", contentType)
                }
                outputStream.use {
                    if (body.isNotEmpty()) {
                        it.write(body)
                    }
                }
            }
        }
        val bytes = try {
            connection.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            connection.errorStream?.use { it.readBytes() } ?: ByteArray(0)
        }
        val resolvedUrl = connection.url.toString()
        val code = try {
            connection.responseCode
        } catch (_: Exception) {
            0
        }
        val responseContentType = connection.contentType.orEmpty()
        connection.disconnect()
        return PortalResponse(
            url = resolvedUrl,
            code = code,
            contentType = responseContentType,
            bodyBytes = bytes,
        )
    }

    private fun normalizeUrl(target: String): String {
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target.replace("https://robot.local", PORTAL_BASE_URL).replace("http://robot.local", PORTAL_BASE_URL)
        }
        return when {
            target.startsWith("/") -> PORTAL_BASE_URL + target
            else -> "$PORTAL_BASE_URL/$target"
        }
    }

    private fun isRobotPortalUrl(url: String): Boolean {
        val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
        return host == "192.168.4.1" || host == "robot.local"
    }

    private fun formEncode(fields: Map<String, String>): String {
        return fields.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
