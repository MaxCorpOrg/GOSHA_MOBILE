package com.maxcorp.gosha.mobile

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object RobotPortalClient {
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
        val url = normalizeUrl(target)
        return open(context, url, "GET", null)
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
            return open(context, full, "GET", null)
        }
        return open(
            context = context,
            url = url,
            method = method.uppercase(),
            body = formEncode(fields).toByteArray(Charsets.UTF_8),
        )
    }

    private fun open(
        context: Context,
        url: String,
        method: String,
        body: ByteArray?,
    ): PortalResponse {
        val network = RobotWifiConnector.preferredNetwork(context)
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
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                outputStream.use { it.write(body) }
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
        val contentType = connection.contentType.orEmpty()
        connection.disconnect()
        return PortalResponse(
            url = resolvedUrl,
            code = code,
            contentType = contentType,
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

    private fun formEncode(fields: Map<String, String>): String {
        return fields.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
