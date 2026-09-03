package com.maxcorp.gosha.mobile

internal data class WifiRecoveryNetworkDecision(
    val correlationId: String,
    val taskStatus: String?,
)

internal data class WifiRecoveryCompletionDecision(
    val correlationId: String,
    val taskStatus: String,
    val nextCorrelationId: String,
)

internal data class WifiRecoveryTimeoutDecision(
    val correlationId: String,
    val taskStatus: String,
    val nextCorrelationId: String,
)

internal object WifiRecoveryRuntimePolicy {
    fun onNetworkTransition(
        currentCorrelationId: String,
        networkLost: Boolean,
        networkRecovered: Boolean,
        newCorrelationId: String,
    ): WifiRecoveryNetworkDecision {
        val activeCorrelationId = currentCorrelationId.trim().ifBlank {
            if (networkLost) newCorrelationId.trim() else ""
        }
        val taskStatus = when {
            activeCorrelationId.isBlank() -> null
            networkLost || networkRecovered -> "running"
            else -> "running"
        }
        return WifiRecoveryNetworkDecision(
            correlationId = activeCorrelationId,
            taskStatus = taskStatus,
        )
    }

    fun onLocalRobotVerified(
        currentCorrelationId: String,
    ): WifiRecoveryCompletionDecision? {
        val correlationId = currentCorrelationId.trim()
        if (correlationId.isBlank()) return null
        return WifiRecoveryCompletionDecision(
            correlationId = correlationId,
            taskStatus = "completed",
            nextCorrelationId = "",
        )
    }

    /**
     * A retry limit closes the current task. A later local discovery still
     * updates connectivity, but must not rewrite this terminal task as
     * completed without a new recovery correlation.
     */
    fun onRetryLimitReached(
        currentCorrelationId: String,
    ): WifiRecoveryTimeoutDecision? {
        val correlationId = currentCorrelationId.trim()
        if (correlationId.isBlank()) return null
        return WifiRecoveryTimeoutDecision(
            correlationId = correlationId,
            taskStatus = "timed_out",
            nextCorrelationId = "",
        )
    }
}
