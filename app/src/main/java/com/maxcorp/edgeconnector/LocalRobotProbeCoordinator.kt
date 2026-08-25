package com.maxcorp.gosha.mobile

import kotlinx.coroutines.delay

sealed class LocalRobotProbeRun<out T> {
    data class Executed<T>(val value: T) : LocalRobotProbeRun<T>()
    data class Skipped(
        val reason: String,
        val retryAfterMs: Long,
        val activeSource: String,
    ) : LocalRobotProbeRun<Nothing>()
}

internal data class FreshRobotWsSuccess(
    val host: String,
    val source: String,
    val ageMs: Long,
    val expectedDeviceId: String,
)

object LocalRobotProbeCoordinator {
    internal const val DEFAULT_SERVICE_MIN_INTERVAL_MS = 10_000L
    internal const val DEFAULT_SERVICE_SUCCESS_REUSE_TTL_MS = 5_000L

    private val monitor = Any()
    private var activeLease: ProbeLease? = null
    private var nextLeaseId = 1L
    private var lastStartedAtMs: Long? = null
    private var lastStartedSource: String = ""
    private var lastSuccessfulServiceHost: SuccessfulServiceHost? = null

    suspend fun <T> runMainActivitySearch(
        source: String,
        nowMs: () -> Long = { System.currentTimeMillis() },
        block: suspend () -> T,
    ): LocalRobotProbeRun<T> {
        return runExclusive(
            source = source,
            waitForTurn = true,
            minIntervalMs = 0L,
            nowMs = nowMs,
            block = block,
        )
    }

    suspend fun <T> runServiceProbe(
        source: String,
        minIntervalMs: Long = DEFAULT_SERVICE_MIN_INTERVAL_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
        block: suspend () -> T,
    ): LocalRobotProbeRun<T> {
        return runExclusive(
            source = source,
            waitForTurn = false,
            minIntervalMs = minIntervalMs,
            nowMs = nowMs,
            block = block,
        )
    }

    suspend fun <T> runFunctionalCommand(
        source: String,
        nowMs: () -> Long = { System.currentTimeMillis() },
        block: suspend () -> T,
    ): LocalRobotProbeRun<T> {
        return runExclusive(
            source = source,
            waitForTurn = true,
            minIntervalMs = 0L,
            nowMs = nowMs,
            block = block,
        )
    }

    internal suspend fun <T> runExclusive(
        source: String,
        waitForTurn: Boolean,
        minIntervalMs: Long,
        nowMs: () -> Long = { System.currentTimeMillis() },
        block: suspend () -> T,
    ): LocalRobotProbeRun<T> {
        var lease: ProbeLease
        while (true) {
            when (val decision = tryAcquire(source, minIntervalMs, nowMs())) {
                is AcquireDecision.Acquired -> {
                    lease = decision.lease
                    break
                }
                is AcquireDecision.Denied -> {
                    if (!waitForTurn) {
                        return LocalRobotProbeRun.Skipped(
                            reason = decision.reason,
                            retryAfterMs = decision.retryAfterMs,
                            activeSource = decision.activeSource,
                        )
                    }
                    delay(decision.retryAfterMs.coerceIn(50L, 1_000L))
                }
            }
        }

        return try {
            LocalRobotProbeRun.Executed(block())
        } finally {
            release(lease)
        }
    }

    internal fun resetForTests() {
        synchronized(monitor) {
            activeLease = null
            nextLeaseId = 1L
            lastStartedAtMs = null
            lastStartedSource = ""
            lastSuccessfulServiceHost = null
        }
    }

    internal fun recordSuccessfulServiceHost(
        host: String,
        source: String,
        expectedDeviceId: String,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val normalizedHost = host.trim()
        val normalizedExpectedDeviceId = LocalRobotIdentityProbe.normalizeDeviceId(expectedDeviceId)
        if (normalizedHost.isBlank() || normalizedExpectedDeviceId.isBlank()) {
            return
        }

        synchronized(monitor) {
            lastSuccessfulServiceHost = SuccessfulServiceHost(
                host = normalizedHost,
                source = source,
                expectedDeviceId = normalizedExpectedDeviceId,
                recordedAtMs = nowMs,
            )
        }
    }

    internal fun freshSuccessfulServiceHost(
        subnetPrefix: String,
        preferredHosts: List<String>,
        expectedDeviceId: String,
        maxAgeMs: Long = DEFAULT_SERVICE_SUCCESS_REUSE_TTL_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
    ): FreshRobotWsSuccess? {
        val normalizedSubnet = subnetPrefix.trim()
        val normalizedExpectedDeviceId = LocalRobotIdentityProbe.normalizeDeviceId(expectedDeviceId)
        val preferred = preferredHosts
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (normalizedSubnet.isBlank() || normalizedExpectedDeviceId.isBlank() || preferred.isEmpty()) {
            return null
        }

        val now = nowMs()
        synchronized(monitor) {
            val success = lastSuccessfulServiceHost ?: return null
            val ageMs = now - success.recordedAtMs
            if (ageMs < 0L || ageMs > maxAgeMs) {
                return null
            }
            if (!success.host.startsWith("$normalizedSubnet.")) {
                return null
            }
            if (success.host !in preferred) {
                return null
            }
            if (success.expectedDeviceId != normalizedExpectedDeviceId) {
                return null
            }
            return FreshRobotWsSuccess(
                host = success.host,
                source = success.source,
                ageMs = ageMs,
                expectedDeviceId = success.expectedDeviceId,
            )
        }
    }

    private fun tryAcquire(
        source: String,
        minIntervalMs: Long,
        nowMs: Long,
    ): AcquireDecision {
        synchronized(monitor) {
            val currentLease = activeLease
            if (currentLease != null) {
                return AcquireDecision.Denied(
                    reason = "local websocket session already running",
                    retryAfterMs = 250L,
                    activeSource = currentLease.source,
                )
            }

            val lastStarted = lastStartedAtMs
            val elapsedMs = if (lastStarted == null) Long.MAX_VALUE else nowMs - lastStarted
            if (elapsedMs < minIntervalMs) {
                return AcquireDecision.Denied(
                    reason = "local websocket service rate limited",
                    retryAfterMs = (minIntervalMs - elapsedMs).coerceAtLeast(50L),
                    activeSource = lastStartedSource,
                )
            }

            val lease = ProbeLease(
                id = nextLeaseId++,
                source = source,
            )
            activeLease = lease
            lastStartedAtMs = nowMs
            lastStartedSource = source
            return AcquireDecision.Acquired(lease)
        }
    }

    private fun release(lease: ProbeLease) {
        synchronized(monitor) {
            if (activeLease?.id == lease.id) {
                activeLease = null
            }
        }
    }

    private data class ProbeLease(
        val id: Long,
        val source: String,
    )

    private data class SuccessfulServiceHost(
        val host: String,
        val source: String,
        val expectedDeviceId: String,
        val recordedAtMs: Long,
    )

    private sealed class AcquireDecision {
        data class Acquired(val lease: ProbeLease) : AcquireDecision()
        data class Denied(
            val reason: String,
            val retryAfterMs: Long,
            val activeSource: String,
        ) : AcquireDecision()
    }
}
