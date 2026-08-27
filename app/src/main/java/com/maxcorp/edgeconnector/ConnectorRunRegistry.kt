package com.maxcorp.gosha.mobile

import kotlinx.coroutines.Job
import okhttp3.Call

internal data class ConnectorRunCancellation(
    val job: Job?,
    val panelCalls: List<Call>,
)

internal enum class RuntimeProbeSideEffect {
    CLAIMED,
    FLUSH_ONLY,
    STALE,
}

internal class ConnectorRunRegistry {
    private val lock = Any()
    private var activeConfig: ConnectorConfig? = null
    private var activeStartId: Int = 0
    private var activeJob: Job? = null
    private val panelCalls = LinkedHashSet<Call>()
    private var runtimeProbeSignature: String = ""

    fun activate(config: ConnectorConfig, startId: Int, job: Job): ConnectorRunCancellation {
        return synchronized(lock) {
            val cancellation = clearLocked()
            activeConfig = config
            activeStartId = startId
            activeJob = job
            runtimeProbeSignature = ""
            cancellation
        }
    }

    fun clear(): ConnectorRunCancellation {
        return synchronized(lock) {
            clearLocked()
        }
    }

    fun clearIfCurrent(
        config: ConnectorConfig,
        startId: Int,
        job: Job,
    ): ConnectorRunCancellation {
        return synchronized(lock) {
            if (isCurrentLocked(config, startId, job) { true }) {
                clearLocked()
            } else {
                ConnectorRunCancellation(job = null, panelCalls = emptyList())
            }
        }
    }

    fun isCurrent(
        config: ConnectorConfig,
        startId: Int,
        job: Job,
        identityMatches: () -> Boolean,
    ): Boolean {
        return synchronized(lock) {
            isCurrentLocked(config, startId, job, identityMatches)
        }
    }

    fun runIfCurrent(
        config: ConnectorConfig,
        startId: Int,
        job: Job,
        identityMatches: () -> Boolean,
        action: () -> Unit,
    ): Boolean {
        return synchronized(lock) {
            if (!isCurrentLocked(config, startId, job, identityMatches)) {
                false
            } else {
                action()
                true
            }
        }
    }

    fun registerPanelCallIfCurrent(
        config: ConnectorConfig,
        startId: Int,
        job: Job,
        call: Call,
        identityMatches: () -> Boolean,
    ): Boolean {
        return synchronized(lock) {
            if (!isCurrentLocked(config, startId, job, identityMatches)) {
                false
            } else {
                panelCalls.add(call)
                true
            }
        }
    }

    fun unregisterPanelCall(call: Call) {
        synchronized(lock) {
            panelCalls.remove(call)
        }
    }

    fun claimRuntimeProbeIfCurrent(
        config: ConnectorConfig,
        startId: Int,
        job: Job,
        signature: String,
        identityMatches: () -> Boolean,
    ): RuntimeProbeSideEffect {
        return synchronized(lock) {
            if (!isCurrentLocked(config, startId, job, identityMatches)) {
                RuntimeProbeSideEffect.STALE
            } else if (signature == runtimeProbeSignature) {
                RuntimeProbeSideEffect.FLUSH_ONLY
            } else {
                runtimeProbeSignature = signature
                RuntimeProbeSideEffect.CLAIMED
            }
        }
    }

    fun clearRuntimeProbeSignatureIfRun(
        config: ConnectorConfig,
        startId: Int,
        job: Job,
        signature: String,
    ): Boolean {
        return synchronized(lock) {
            if (
                activeConfig === config &&
                activeStartId == startId &&
                activeJob === job &&
                runtimeProbeSignature == signature
            ) {
                runtimeProbeSignature = ""
                true
            } else {
                false
            }
        }
    }

    fun currentRuntimeProbeSignatureForTest(): String {
        return synchronized(lock) {
            runtimeProbeSignature
        }
    }

    private fun isCurrentLocked(
        config: ConnectorConfig,
        startId: Int,
        job: Job,
        identityMatches: () -> Boolean,
    ): Boolean {
        return job.isActive &&
            activeConfig === config &&
            activeStartId == startId &&
            activeJob === job &&
            identityMatches()
    }

    private fun clearLocked(): ConnectorRunCancellation {
        val cancellation = ConnectorRunCancellation(
            job = activeJob,
            panelCalls = panelCalls.toList(),
        )
        activeConfig = null
        activeStartId = 0
        activeJob = null
        panelCalls.clear()
        runtimeProbeSignature = ""
        return cancellation
    }
}
