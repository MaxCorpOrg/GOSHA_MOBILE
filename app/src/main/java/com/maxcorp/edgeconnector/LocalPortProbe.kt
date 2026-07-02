package com.maxcorp.gosha.mobile

import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

object LocalPortProbe {
    fun isOpen(
        socketFactory: SocketFactory?,
        host: String,
        port: Int,
        timeoutMs: Long,
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
                it.connect(InetSocketAddress(host, port), timeout)
                true
            }.getOrDefault(false)
        }
    }
}
