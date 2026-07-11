package com.maxcorp.gosha.mobile

import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

object LocalWsHandshakeProbe {
    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun isOpen(
        socketFactory: SocketFactory?,
        host: String,
        port: Int = 8080,
        path: String = "/ws",
        timeoutMs: Long = 1_500L,
    ): Boolean {
        val timeout = timeoutMs.coerceAtLeast(100L).coerceAtMost(Int.MAX_VALUE.toLong())
        val client = clientFor(socketFactory, timeout)
        val wsUrl = buildWsUrl(host, port, path)

        return try {
            RobotWsProbe.probe(client, wsUrl, timeoutMs = timeout).first
        } catch (exc: CancellationException) {
            throw exc
        } catch (_: Exception) {
            false
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
