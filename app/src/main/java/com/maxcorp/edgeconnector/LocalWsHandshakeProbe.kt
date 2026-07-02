package com.maxcorp.gosha.mobile

import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

object LocalWsHandshakeProbe {
    fun isOpen(
        socketFactory: SocketFactory?,
        host: String,
        port: Int = 8080,
        path: String = "/ws",
        timeoutMs: Long = 1_500L,
    ): Boolean {
        val timeout = timeoutMs.coerceAtLeast(100L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val socket = runCatching {
            (socketFactory ?: SocketFactory.getDefault()).createSocket() as Socket
        }.getOrElse {
            return false
        }

        return socket.use {
            runCatching {
                it.tcpNoDelay = true
                it.soTimeout = timeout
                it.connect(InetSocketAddress(host, port), timeout)
                val request = buildString {
                    append("GET ")
                    append(path)
                    append(" HTTP/1.1\r\n")
                    append("Host: ")
                    append(host)
                    append(':')
                    append(port)
                    append("\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n")
                    append("Sec-WebSocket-Version: 13\r\n\r\n")
                }
                val output = it.getOutputStream()
                output.write(request.toByteArray(Charsets.UTF_8))
                output.flush()
                val buffer = ByteArray(1024)
                val bytes = BufferedInputStream(it.getInputStream()).read(buffer)
                if (bytes <= 0) {
                    false
                } else {
                    val response = String(buffer, 0, bytes, Charsets.UTF_8)
                    response.startsWith("HTTP/1.1 101") &&
                        response.contains("Upgrade: websocket", ignoreCase = true)
                }
            }.getOrElse { error ->
                false
            }
        }
    }
}
