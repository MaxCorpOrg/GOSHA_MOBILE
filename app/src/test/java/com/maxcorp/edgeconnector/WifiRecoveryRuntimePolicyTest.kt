package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiRecoveryRuntimePolicyTest {
    @Test
    fun `network return keeps recovery running until robot is verified`() {
        val lost = WifiRecoveryRuntimePolicy.onNetworkTransition(
            currentCorrelationId = "",
            networkLost = true,
            networkRecovered = false,
            newCorrelationId = "wifi-recovery-1",
        )
        val networkReturned = WifiRecoveryRuntimePolicy.onNetworkTransition(
            currentCorrelationId = lost.correlationId,
            networkLost = false,
            networkRecovered = true,
            newCorrelationId = "unused",
        )

        assertEquals("wifi-recovery-1", lost.correlationId)
        assertEquals("running", lost.taskStatus)
        assertEquals("wifi-recovery-1", networkReturned.correlationId)
        assertEquals("running", networkReturned.taskStatus)
    }

    @Test
    fun `visibility changes do not close active recovery`() {
        val decision = WifiRecoveryRuntimePolicy.onNetworkTransition(
            currentCorrelationId = "wifi-recovery-1",
            networkLost = false,
            networkRecovered = false,
            newCorrelationId = "unused",
        )

        assertEquals("wifi-recovery-1", decision.correlationId)
        assertEquals("running", decision.taskStatus)
    }

    @Test
    fun `local robot verification completes and clears recovery`() {
        val completion = WifiRecoveryRuntimePolicy.onLocalRobotVerified("wifi-recovery-1")

        requireNotNull(completion)
        assertEquals("wifi-recovery-1", completion.correlationId)
        assertEquals("completed", completion.taskStatus)
        assertEquals("", completion.nextCorrelationId)
    }

    @Test
    fun `ordinary network appearance does not create recovery task`() {
        val decision = WifiRecoveryRuntimePolicy.onNetworkTransition(
            currentCorrelationId = "",
            networkLost = false,
            networkRecovered = true,
            newCorrelationId = "unused",
        )

        assertEquals("", decision.correlationId)
        assertNull(decision.taskStatus)
        assertNull(WifiRecoveryRuntimePolicy.onLocalRobotVerified(""))
    }

    @Test
    fun `retry timeout is terminal and late visibility does not rewrite task`() {
        val timeout = WifiRecoveryRuntimePolicy.onRetryLimitReached("wifi-recovery-1")

        requireNotNull(timeout)
        assertEquals("wifi-recovery-1", timeout.correlationId)
        assertEquals("timed_out", timeout.taskStatus)
        assertEquals("", timeout.nextCorrelationId)
        assertNull(WifiRecoveryRuntimePolicy.onLocalRobotVerified(timeout.nextCorrelationId))
        assertNull(WifiRecoveryRuntimePolicy.onRetryLimitReached(""))
    }
}
