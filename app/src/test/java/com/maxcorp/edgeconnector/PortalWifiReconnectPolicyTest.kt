package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class PortalWifiReconnectPolicyTest {
    @Test
    fun `repeated scan while wifi is disabled does not start request`() {
        val policy = testPolicy()

        policy.onWifiDisabled()

        assertEquals(
            PortalWifiReconnectPolicy.Action.WaitForWifiEnabled,
            policy.requestNeeded(nowMs = 0L, wifiState = PortalWifiReconnectPolicy.WifiState.Disabled).action,
        )
        assertEquals(
            PortalWifiReconnectPolicy.Action.WaitForWifiEnabled,
            policy.requestNeeded(nowMs = 5_000L, wifiState = PortalWifiReconnectPolicy.WifiState.Disabled).action,
        )
    }

    @Test
    fun `active request coalesces repeated attempts`() {
        val policy = testPolicy()

        assertEquals(
            PortalWifiReconnectPolicy.Action.StartRequest,
            policy.requestNeeded(nowMs = 0L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled).action,
        )
        assertEquals(
            PortalWifiReconnectPolicy.Action.CoalesceActiveRequest,
            policy.requestNeeded(nowMs = 100L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled).action,
        )
    }

    @Test
    fun `unavailable schedules bounded backoff`() {
        val policy = testPolicy(initialCooldownMs = 1_000L, maxCooldownMs = 2_500L)

        assertEquals(
            PortalWifiReconnectPolicy.Action.StartRequest,
            policy.requestNeeded(nowMs = 0L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled).action,
        )
        assertEquals(
            PortalWifiReconnectPolicy.Decision(
                PortalWifiReconnectPolicy.Action.WaitForCooldown,
                retryDelayMs = 1_000L,
            ),
            policy.onUnavailable(nowMs = 100L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled),
        )
        assertEquals(
            PortalWifiReconnectPolicy.Decision(
                PortalWifiReconnectPolicy.Action.WaitForCooldown,
                retryDelayMs = 600L,
            ),
            policy.requestNeeded(nowMs = 500L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled),
        )
        assertEquals(
            PortalWifiReconnectPolicy.Action.StartRequest,
            policy.requestNeeded(nowMs = 1_100L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled).action,
        )

        assertEquals(2_000L, policy.onUnavailable(1_200L, PortalWifiReconnectPolicy.WifiState.Enabled).retryDelayMs)
        assertEquals(
            PortalWifiReconnectPolicy.Action.StartRequest,
            policy.requestNeeded(nowMs = 3_200L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled).action,
        )
        assertEquals(2_500L, policy.onUnavailable(3_300L, PortalWifiReconnectPolicy.WifiState.Enabled).retryDelayMs)
    }

    @Test
    fun `available resets cooldown and failure counter`() {
        val policy = testPolicy(initialCooldownMs = 1_000L, maxCooldownMs = 8_000L)

        policy.requestNeeded(nowMs = 0L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled)
        policy.onUnavailable(nowMs = 100L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled)
        policy.onAvailable()

        assertEquals(
            PortalWifiReconnectPolicy.Action.StartRequest,
            policy.requestNeeded(nowMs = 200L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled).action,
        )
        assertEquals(1_000L, policy.onUnavailable(300L, PortalWifiReconnectPolicy.WifiState.Enabled).retryDelayMs)
    }

    @Test
    fun `lost before submit allows immediate recovery through policy`() {
        val policy = testPolicy()

        policy.requestNeeded(nowMs = 0L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled)
        policy.onAvailable()
        policy.onLost(nowMs = 100L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled)

        assertEquals(
            PortalWifiReconnectPolicy.Action.StartRequest,
            policy.requestNeeded(nowMs = 100L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled).action,
        )
    }

    @Test
    fun `wifi enabled clears disabled blocking and cooldown for immediate start`() {
        val policy = testPolicy(initialCooldownMs = 5_000L)

        policy.requestNeeded(nowMs = 0L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled)
        policy.onUnavailable(nowMs = 100L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled)
        policy.onWifiDisabled()

        assertEquals(
            PortalWifiReconnectPolicy.Action.WaitForWifiEnabled,
            policy.requestNeeded(nowMs = 200L, wifiState = PortalWifiReconnectPolicy.WifiState.Disabled).action,
        )

        policy.onWifiEnabled(nowMs = 300L)

        assertEquals(
            PortalWifiReconnectPolicy.Action.StartRequest,
            policy.requestNeeded(nowMs = 300L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled).action,
        )
    }

    @Test
    fun `wifi enabled event keeps active request coalesced`() {
        val policy = testPolicy()

        policy.requestNeeded(nowMs = 0L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled)
        policy.onWifiEnabled(nowMs = 100L)

        assertEquals(
            PortalWifiReconnectPolicy.Action.CoalesceActiveRequest,
            policy.requestNeeded(nowMs = 100L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled).action,
        )
    }

    @Test
    fun `submitted and completed always block new dialog`() {
        val submittedPolicy = testPolicy()
        submittedPolicy.onPortalSubmitted()

        assertEquals(
            PortalWifiReconnectPolicy.Action.BlockedAfterPortalFinished,
            submittedPolicy.requestNeeded(nowMs = 0L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled).action,
        )

        val completedPolicy = testPolicy()
        completedPolicy.onPortalCompleted()

        assertEquals(
            PortalWifiReconnectPolicy.Action.BlockedAfterPortalFinished,
            completedPolicy.requestNeeded(nowMs = 0L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled).action,
        )
    }

    @Test
    fun `lifecycle reset clears active request and delays`() {
        val policy = testPolicy(initialCooldownMs = 2_000L)

        policy.requestNeeded(nowMs = 0L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled)
        assertEquals(
            PortalWifiReconnectPolicy.Action.CoalesceActiveRequest,
            policy.requestNeeded(nowMs = 100L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled).action,
        )
        policy.onUnavailable(nowMs = 200L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled)

        policy.onLifecycleReset(PortalWifiReconnectPolicy.WifiState.Enabled)

        assertEquals(
            PortalWifiReconnectPolicy.Action.StartRequest,
            policy.requestNeeded(nowMs = 300L, wifiState = PortalWifiReconnectPolicy.WifiState.Enabled).action,
        )
    }

    private fun testPolicy(
        initialCooldownMs: Long = 1_000L,
        maxCooldownMs: Long = 8_000L,
    ): PortalWifiReconnectPolicy {
        return PortalWifiReconnectPolicy(
            initialCooldownMs = initialCooldownMs,
            maxCooldownMs = maxCooldownMs,
        )
    }
}
