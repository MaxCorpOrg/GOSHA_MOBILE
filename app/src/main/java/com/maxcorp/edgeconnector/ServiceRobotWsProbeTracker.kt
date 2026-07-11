package com.maxcorp.gosha.mobile

internal enum class ServiceRobotWsProbeState(val wireValue: String) {
    EXECUTED("executed"),
    SKIPPED("skipped"),
    STALE("stale"),
}

internal data class ServiceRobotWsProbeResult(
    val state: ServiceRobotWsProbeState,
    val ok: Boolean,
    val error: String,
    val retryAfterMs: Long,
    val activeSource: String,
    val cachedAgeMs: Long?,
    val executedCount: Long,
    val skippedCount: Long,
    val staleCount: Long,
    val serviceMinIntervalMs: Long,
) {
    val canPublishPresence: Boolean
        get() = state == ServiceRobotWsProbeState.EXECUTED && ok
}

internal class ServiceRobotWsProbeTracker(
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val baseMinIntervalMs: Long = LocalRobotProbeCoordinator.DEFAULT_SERVICE_MIN_INTERVAL_MS,
    private val maxMinIntervalMs: Long = DEFAULT_MAX_MIN_INTERVAL_MS,
) {
    private val monitor = Any()
    private var cached: CachedRobotWsResult? = null
    private var executedCount = 0L
    private var skippedCount = 0L
    private var staleCount = 0L
    private var currentMinIntervalMs = baseMinIntervalMs

    fun serviceMinIntervalMs(): Long = synchronized(monitor) {
        currentMinIntervalMs
    }

    fun recordExecuted(ok: Boolean, error: String, nowMs: Long): ServiceRobotWsProbeResult {
        synchronized(monitor) {
            cached = CachedRobotWsResult(ok = ok, error = error, recordedAtMs = nowMs)
            executedCount += 1
            currentMinIntervalMs = if (ok) {
                baseMinIntervalMs
            } else {
                (currentMinIntervalMs * 2).coerceAtLeast(baseMinIntervalMs * 2).coerceAtMost(maxMinIntervalMs)
            }
            return snapshotLocked(
                state = ServiceRobotWsProbeState.EXECUTED,
                ok = ok,
                error = error,
                retryAfterMs = 0L,
                activeSource = "",
                cachedAgeMs = 0L,
            )
        }
    }

    fun recordSkipped(run: LocalRobotProbeRun.Skipped, nowMs: Long): ServiceRobotWsProbeResult {
        synchronized(monitor) {
            val cachedResult = cached
            val ageMs = cachedResult?.let { (nowMs - it.recordedAtMs).coerceAtLeast(0L) }
            return if (cachedResult != null && ageMs != null && ageMs <= cacheTtlMs) {
                skippedCount += 1
                snapshotLocked(
                    state = ServiceRobotWsProbeState.SKIPPED,
                    ok = cachedResult.ok,
                    error = cachedResult.error.ifBlank { run.reason },
                    retryAfterMs = run.retryAfterMs,
                    activeSource = run.activeSource,
                    cachedAgeMs = ageMs,
                )
            } else {
                staleCount += 1
                snapshotLocked(
                    state = ServiceRobotWsProbeState.STALE,
                    ok = false,
                    error = if (cachedResult == null) run.reason else "cached robot websocket probe is stale",
                    retryAfterMs = run.retryAfterMs,
                    activeSource = run.activeSource,
                    cachedAgeMs = ageMs,
                )
            }
        }
    }

    fun recordExternalObservation(ok: Boolean, error: String, nowMs: Long) {
        synchronized(monitor) {
            cached = CachedRobotWsResult(ok = ok, error = error, recordedAtMs = nowMs)
        }
    }

    private fun snapshotLocked(
        state: ServiceRobotWsProbeState,
        ok: Boolean,
        error: String,
        retryAfterMs: Long,
        activeSource: String,
        cachedAgeMs: Long?,
    ): ServiceRobotWsProbeResult {
        return ServiceRobotWsProbeResult(
            state = state,
            ok = ok,
            error = error,
            retryAfterMs = retryAfterMs,
            activeSource = activeSource,
            cachedAgeMs = cachedAgeMs,
            executedCount = executedCount,
            skippedCount = skippedCount,
            staleCount = staleCount,
            serviceMinIntervalMs = currentMinIntervalMs,
        )
    }

    private data class CachedRobotWsResult(
        val ok: Boolean,
        val error: String,
        val recordedAtMs: Long,
    )

    companion object {
        internal const val DEFAULT_CACHE_TTL_MS = 15_000L
        private const val DEFAULT_MAX_MIN_INTERVAL_MS = 60_000L
    }
}
