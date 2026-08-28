package com.maxcorp.gosha.mobile

internal data class MenuRecoveryRetryPlan(
    val attempt: Int,
    val delayMs: Long,
)

internal object MenuRecoveryRetryPolicy {
    const val LIMIT = 3
    private const val BASE_DELAY_MS = 3_000L

    fun next(
        completedAttempts: Int,
        recoveryNeeded: Boolean,
        hasHomeWifi: Boolean,
    ): MenuRecoveryRetryPlan? {
        if (!recoveryNeeded || !hasHomeWifi || completedAttempts >= LIMIT) {
            return null
        }
        val attempt = completedAttempts.coerceAtLeast(0) + 1
        return MenuRecoveryRetryPlan(
            attempt = attempt,
            delayMs = BASE_DELAY_MS * attempt,
        )
    }

    fun exhausted(
        completedAttempts: Int,
        recoveryNeeded: Boolean,
        hasHomeWifi: Boolean,
    ): Boolean {
        return recoveryNeeded && hasHomeWifi && completedAttempts >= LIMIT
    }
}
