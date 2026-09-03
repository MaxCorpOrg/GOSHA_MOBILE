package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServiceRobotWsProbeTrackerTest {
    @Test
    fun `fresh skipped cache is diagnostic only and does not publish presence`() {
        val tracker = ServiceRobotWsProbeTracker(cacheTtlMs = 10_000L, baseMinIntervalMs = 10_000L)
        val executed = tracker.recordExecuted(ok = true, error = "", nowMs = 1_000L)
        assertEquals(ServiceRobotWsProbeState.EXECUTED, executed.state)
        assertEquals(1L, executed.executedCount)

        val skipped = tracker.recordSkipped(
            run = LocalRobotProbeRun.Skipped(
                reason = "local websocket service rate limited",
                retryAfterMs = 4_000L,
                activeSource = "ConnectorForegroundService.probeRobotWs",
            ),
            nowMs = 6_000L,
        )

        assertEquals(ServiceRobotWsProbeState.SKIPPED, skipped.state)
        assertEquals(true, skipped.ok)
        assertEquals(5_000L, skipped.cachedAgeMs)
        assertEquals(1L, skipped.skippedCount)
        assertFalse(skipped.canPublishPresence)
    }

    @Test
    fun `stale cache is not reported as home wifi local`() {
        val tracker = ServiceRobotWsProbeTracker(cacheTtlMs = 10_000L, baseMinIntervalMs = 10_000L)
        tracker.recordExecuted(ok = true, error = "", nowMs = 1_000L)

        val stale = tracker.recordSkipped(
            run = LocalRobotProbeRun.Skipped(
                reason = "local websocket service rate limited",
                retryAfterMs = 4_000L,
                activeSource = "ConnectorForegroundService.probeRobotWs",
            ),
            nowMs = 12_500L,
        )

        assertEquals(ServiceRobotWsProbeState.STALE, stale.state)
        assertEquals(false, stale.ok)
        assertEquals(11_500L, stale.cachedAgeMs)
        assertEquals(1L, stale.staleCount)
        assertFalse(stale.canPublishPresence)
    }

    @Test
    fun `foreground presence is not published after identity mismatch`() {
        val tracker = ServiceRobotWsProbeTracker(cacheTtlMs = 10_000L, baseMinIntervalMs = 10_000L)

        val mismatch = tracker.recordExecuted(
            ok = false,
            error = "device identity mismatch",
            nowMs = 1_000L,
        )

        assertEquals(ServiceRobotWsProbeState.EXECUTED, mismatch.state)
        assertEquals(false, mismatch.ok)
        assertFalse(mismatch.canPublishPresence)
    }

    @Test
    fun `foreground presence is published only after executed identity match`() {
        val tracker = ServiceRobotWsProbeTracker(cacheTtlMs = 10_000L, baseMinIntervalMs = 10_000L)

        val verified = tracker.recordExecuted(ok = true, error = "", nowMs = 1_000L)

        assertEquals(ServiceRobotWsProbeState.EXECUTED, verified.state)
        assertEquals(true, verified.ok)
        assertEquals(true, verified.canPublishPresence)
    }

    @Test
    fun `executed failures increase service probe pause and success resets it`() {
        val tracker = ServiceRobotWsProbeTracker(cacheTtlMs = 10_000L, baseMinIntervalMs = 10_000L)

        val firstFailure = tracker.recordExecuted(ok = false, error = "timeout", nowMs = 1_000L)
        assertEquals(20_000L, firstFailure.serviceMinIntervalMs)

        val secondFailure = tracker.recordExecuted(ok = false, error = "timeout", nowMs = 30_000L)
        assertEquals(40_000L, secondFailure.serviceMinIntervalMs)
        assertEquals(2L, secondFailure.executedCount)

        val success = tracker.recordExecuted(ok = true, error = "", nowMs = 90_000L)
        assertEquals(10_000L, success.serviceMinIntervalMs)
        assertEquals(3L, success.executedCount)
    }
}
