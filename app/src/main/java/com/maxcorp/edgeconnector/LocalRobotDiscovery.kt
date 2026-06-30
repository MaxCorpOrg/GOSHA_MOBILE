package com.maxcorp.gosha.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient

object LocalRobotDiscovery {
    private const val DISCOVERY_PARALLELISM = 48
    private const val PROBE_TIMEOUT_MS = 1_000L
    private const val PREFERRED_PROBE_TIMEOUT_MS = 700L

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun discover(
        http: OkHttpClient,
        subnetPrefix: String,
        preferredHosts: List<String> = emptyList(),
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
            val ok = RobotWsProbe.probe(
                http,
                "ws://$host:8080/ws",
                timeoutMs = PREFERRED_PROBE_TIMEOUT_MS,
            ).first
            if (ok) {
                return@coroutineScope host to ""
            }
        }

        val commonRange = (100..140).toList() + listOf(10, 20, 30, 50, 60, 70, 80, 90, 150, 160, 170, 180, 190, 200, 210)
        val priority = (preferred + commonRange + (1..254)).distinct()
        val hosts = priority.chunked(DISCOVERY_PARALLELISM)

        for (batch in hosts) {
            val probes = batch.map { last ->
                async(worker) {
                    val host = "$subnetPrefix.$last"
                    val ok = RobotWsProbe.probe(
                        http,
                        "ws://$host:8080/ws",
                        timeoutMs = PROBE_TIMEOUT_MS
                    ).first
                    if (ok) host else null
                }
            }
            val found = probes.awaitAll().firstOrNull { !it.isNullOrBlank() }
            if (!found.isNullOrBlank()) {
                return@coroutineScope found to ""
            }
        }

        null to "Робот не найден автоматически в сети $subnetPrefix.0/24"
    }
}
