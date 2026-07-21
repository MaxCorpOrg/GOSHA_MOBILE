package com.maxcorp.gosha.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.net.SocketFactory

internal data class LocalRobotDiscoveryProbeHooks(
    val isPortOpen: suspend (SocketFactory?, String, Int, Long) -> Boolean,
    val isWsOpen: suspend (SocketFactory?, String, Long) -> Boolean,
) {
    companion object {
        val REAL = LocalRobotDiscoveryProbeHooks(
            isPortOpen = { socketFactory, host, port, timeoutMs ->
                LocalPortProbe.isOpen(socketFactory, host, port, timeoutMs)
            },
            isWsOpen = { socketFactory, host, timeoutMs ->
                LocalWsHandshakeProbe.isOpen(
                    socketFactory = socketFactory,
                    host = host,
                    timeoutMs = timeoutMs,
                )
            },
        )
    }
}

object LocalRobotDiscovery {
    private const val DISCOVERY_PARALLELISM = 16
    private const val TCP_PROBE_TIMEOUT_MS = 350L
    private const val PREFERRED_TCP_PROBE_TIMEOUT_MS = 550L
    private const val WS_PROBE_TIMEOUT_MS = 1_500L
    private const val PREFERRED_WS_PROBE_TIMEOUT_MS = 1_800L

    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun discover(
        subnetPrefix: String,
        socketFactory: SocketFactory? = null,
        preferredHosts: List<String> = emptyList(),
        probeHooks: LocalRobotDiscoveryProbeHooks = LocalRobotDiscoveryProbeHooks.REAL,
    ): Pair<String?, String> = coroutineScope {
        if (subnetPrefix.isBlank()) {
            return@coroutineScope null to "Телефон не подключен к Wi‑Fi"
        }

        val worker = Dispatchers.IO.limitedParallelism(DISCOVERY_PARALLELISM)
        val preferred = preferredHosts
            .mapNotNull { host -> host.substringAfterLast('.', "").toIntOrNull() }
            .filter { it in 1..254 }
            .distinct()

        for (last in preferred) {
            val host = "$subnetPrefix.$last"
            val ok = withContext(worker) {
                probeHost(
                    socketFactory = socketFactory,
                    host = host,
                    tcpTimeoutMs = PREFERRED_TCP_PROBE_TIMEOUT_MS,
                    wsTimeoutMs = PREFERRED_WS_PROBE_TIMEOUT_MS,
                    skipPortPrefilter = true,
                    probeHooks = probeHooks,
                )
            }
            if (ok) {
                return@coroutineScope host to ""
            }
        }

        // Для домашних DHCP-сетей чаще всего полезен широкий коридор 100..180.
        val commonRange = (100..180).toList() + listOf(10, 20, 30, 50, 60, 70, 80, 90, 190, 200, 210)
        val priority = (preferred + commonRange + (1..254)).distinct()
        val hosts = priority.chunked(DISCOVERY_PARALLELISM)

        for (batch in hosts) {
            val reachable = batch.map { last ->
                async(worker) {
                    val host = "$subnetPrefix.$last"
                    if (probeHooks.isPortOpen(socketFactory, host, 8080, TCP_PROBE_TIMEOUT_MS)) host else null
                }
            }

            val candidates = reachable.awaitAll().filterNotNull()
            for (host in candidates) {
                val confirmed = withContext(worker) {
                    probeHost(
                        socketFactory = socketFactory,
                        host = host,
                        tcpTimeoutMs = TCP_PROBE_TIMEOUT_MS,
                        wsTimeoutMs = WS_PROBE_TIMEOUT_MS,
                        skipPortPrefilter = false,
                        probeHooks = probeHooks,
                    )
                }
                if (confirmed) {
                    return@coroutineScope host to ""
                }
            }
        }

        null to "Робот не найден автоматически в сети $subnetPrefix.0/24"
    }

    private suspend fun probeHost(
        socketFactory: SocketFactory?,
        host: String,
        tcpTimeoutMs: Long,
        wsTimeoutMs: Long,
        skipPortPrefilter: Boolean,
        probeHooks: LocalRobotDiscoveryProbeHooks,
    ): Boolean {
        val factories = buildList {
            add(socketFactory)
            add(null)
        }.distinct()

        for (factory in factories) {
            if (!skipPortPrefilter && !probeHooks.isPortOpen(factory, host, 8080, tcpTimeoutMs)) {
                continue
            }
            if (probeHooks.isWsOpen(factory, host, wsTimeoutMs)) {
                return true
            }
        }
        return false
    }
}
