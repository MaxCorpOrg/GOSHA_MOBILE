package com.maxcorp.gosha.mobile

class PortalWifiReconnectPolicy(
    private val initialCooldownMs: Long = DEFAULT_INITIAL_COOLDOWN_MS,
    private val maxCooldownMs: Long = DEFAULT_MAX_COOLDOWN_MS,
) {
    enum class WifiState {
        Enabled,
        Enabling,
        Disabled,
        Disabling,
        Unknown,
    }

    enum class Action {
        StartRequest,
        CoalesceActiveRequest,
        WaitForWifiEnabled,
        WaitForCooldown,
        BlockedAfterPortalFinished,
    }

    data class Decision(
        val action: Action,
        val retryDelayMs: Long = 0L,
    )

    private var activeRequest = false
    private var failedAttempts = 0
    private var nextAllowedRequestAtMs = 0L
    private var portalSubmitted = false
    private var portalCompleted = false
    private var wifiState = WifiState.Unknown

    fun requestNeeded(nowMs: Long, wifiState: WifiState): Decision {
        this.wifiState = wifiState
        if (portalSubmitted || portalCompleted) {
            activeRequest = false
            return Decision(Action.BlockedAfterPortalFinished)
        }
        if (activeRequest) {
            return Decision(Action.CoalesceActiveRequest)
        }
        if (!wifiState.canRequestNetwork) {
            return Decision(Action.WaitForWifiEnabled)
        }
        val waitMs = nextAllowedRequestAtMs - nowMs
        if (waitMs > 0L) {
            return Decision(Action.WaitForCooldown, retryDelayMs = waitMs)
        }
        activeRequest = true
        return Decision(Action.StartRequest)
    }

    fun onAvailable() {
        activeRequest = false
        failedAttempts = 0
        nextAllowedRequestAtMs = 0L
        wifiState = WifiState.Enabled
    }

    fun onLost(nowMs: Long, wifiState: WifiState) {
        activeRequest = false
        this.wifiState = wifiState
        if (wifiState.canRequestNetwork) {
            nextAllowedRequestAtMs = nowMs
        }
    }

    fun onUnavailable(nowMs: Long, wifiState: WifiState): Decision {
        activeRequest = false
        this.wifiState = wifiState
        if (portalSubmitted || portalCompleted) {
            return Decision(Action.BlockedAfterPortalFinished)
        }
        if (!wifiState.canRequestNetwork) {
            nextAllowedRequestAtMs = nowMs
            return Decision(Action.WaitForWifiEnabled)
        }
        failedAttempts += 1
        val cooldownMs = cooldownForAttempt(failedAttempts)
        nextAllowedRequestAtMs = nowMs + cooldownMs
        return Decision(Action.WaitForCooldown, retryDelayMs = cooldownMs)
    }

    fun onRequestNotStarted() {
        activeRequest = false
    }

    fun onWifiEnabled(nowMs: Long) {
        wifiState = WifiState.Enabled
        if (!activeRequest) {
            failedAttempts = 0
            nextAllowedRequestAtMs = nowMs
        }
    }

    fun onWifiDisabled() {
        wifiState = WifiState.Disabled
        activeRequest = false
        nextAllowedRequestAtMs = 0L
    }

    fun onPortalSubmitted() {
        portalSubmitted = true
        activeRequest = false
        nextAllowedRequestAtMs = 0L
    }

    fun onPortalCompleted() {
        portalCompleted = true
        portalSubmitted = true
        activeRequest = false
        nextAllowedRequestAtMs = 0L
    }

    fun onLifecycleReset(wifiState: WifiState = this.wifiState) {
        activeRequest = false
        failedAttempts = 0
        nextAllowedRequestAtMs = 0L
        portalSubmitted = false
        portalCompleted = false
        this.wifiState = wifiState
    }

    private fun cooldownForAttempt(attempt: Int): Long {
        var cooldownMs = initialCooldownMs.coerceAtLeast(0L)
        repeat((attempt - 1).coerceAtLeast(0)) {
            cooldownMs = (cooldownMs * 2L).coerceAtMost(maxCooldownMs)
        }
        return cooldownMs.coerceAtMost(maxCooldownMs)
    }

    private val WifiState.canRequestNetwork: Boolean
        get() = this == WifiState.Enabled || this == WifiState.Unknown

    companion object {
        private const val DEFAULT_INITIAL_COOLDOWN_MS = 5_000L
        private const val DEFAULT_MAX_COOLDOWN_MS = 30_000L
    }
}
