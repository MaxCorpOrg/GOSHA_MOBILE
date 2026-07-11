package com.maxcorp.gosha.mobile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalRobotProbeCoordinatorTest {
    @Before
    fun resetCoordinator() {
        LocalRobotProbeCoordinator.resetForTests()
    }

    @Test
    fun `service probe is skipped while another local session is running`() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val first = async {
            LocalRobotProbeCoordinator.runMainActivitySearch(
                source = "MainActivity.discoverRobotLocally",
            ) {
                firstStarted.complete(Unit)
                releaseFirst.await()
                "first"
            }
        }
        firstStarted.await()

        val second = LocalRobotProbeCoordinator.runServiceProbe(
            source = "ConnectorForegroundService.probeRobotWs",
            minIntervalMs = 0L,
        ) {
            "second"
        }

        assertEquals("MainActivity.discoverRobotLocally", skippedRun(second).activeSource)

        releaseFirst.complete(Unit)
        assertEquals("first", executedValue(first.await()))
    }

    @Test
    fun `manual search waits for active session only and ignores service rate limit`() = runBlocking {
        var now = 1_000L
        val service = LocalRobotProbeCoordinator.runServiceProbe(
            source = "ConnectorForegroundService.probeRobotWs",
            minIntervalMs = 10_000L,
            nowMs = { now },
        ) {
            "service"
        }
        assertEquals("service", executedValue(service))

        now = 2_000L
        val manual = LocalRobotProbeCoordinator.runMainActivitySearch(
            source = "MainActivity.discoverRobotLocally",
            nowMs = { now },
        ) {
            "manual"
        }
        assertEquals("manual", executedValue(manual))

        now = 3_000L
        val nextService = LocalRobotProbeCoordinator.runServiceProbe(
            source = "ConnectorForegroundService.probeRobotWs",
            minIntervalMs = 10_000L,
            nowMs = { now },
        ) {
            "next-service"
        }
        assertEquals("MainActivity.discoverRobotLocally", skippedRun(nextService).activeSource)
    }

    @Test
    fun `manual search can reuse fresh successful service host`() = runBlocking {
        var now = 1_000L
        val service = LocalRobotProbeCoordinator.runServiceProbe(
            source = "ConnectorForegroundService.probeRobotWs",
            minIntervalMs = 10_000L,
            nowMs = { now },
        ) {
            LocalRobotProbeCoordinator.recordSuccessfulServiceHost(
                host = "192.168.1.159",
                source = "ConnectorForegroundService.probeRobotWs",
                nowMs = now,
            )
            "service"
        }
        assertEquals("service", executedValue(service))

        now = 1_700L
        val manual = LocalRobotProbeCoordinator.runMainActivitySearch(
            source = "MainActivity.discoverRobotLocally",
            nowMs = { now },
        ) {
            LocalRobotProbeCoordinator.freshSuccessfulServiceHost(
                subnetPrefix = "192.168.1",
                preferredHosts = listOf("192.168.1.159"),
                nowMs = { now },
            )?.host ?: "manual-scan"
        }

        assertEquals("192.168.1.159", executedValue(manual))
    }

    @Test
    fun `service host reuse requires fresh matching preferred host`() {
        LocalRobotProbeCoordinator.recordSuccessfulServiceHost(
            host = "192.168.1.159",
            source = "ConnectorForegroundService.probeRobotWs",
            nowMs = 1_000L,
        )

        assertNull(
            LocalRobotProbeCoordinator.freshSuccessfulServiceHost(
                subnetPrefix = "192.168.1",
                preferredHosts = listOf("192.168.1.160"),
                nowMs = { 1_500L },
            )
        )
        assertNull(
            LocalRobotProbeCoordinator.freshSuccessfulServiceHost(
                subnetPrefix = "192.168.2",
                preferredHosts = listOf("192.168.1.159"),
                nowMs = { 1_500L },
            )
        )
        assertNull(
            LocalRobotProbeCoordinator.freshSuccessfulServiceHost(
                subnetPrefix = "192.168.1",
                preferredHosts = listOf("192.168.1.159"),
                nowMs = { 7_000L },
            )
        )
    }

    @Test
    fun `waiting local session starts only after active session finishes`() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val first = async {
            LocalRobotProbeCoordinator.runServiceProbe(
                source = "ConnectorForegroundService.probeRobotWs",
                minIntervalMs = 0L,
            ) {
                firstStarted.complete(Unit)
                releaseFirst.await()
                "first"
            }
        }
        firstStarted.await()

        val secondStarted = CompletableDeferred<Unit>()
        val second = async {
            LocalRobotProbeCoordinator.runFunctionalCommand(
                source = "RobotJsonRpcProxy.notify",
            ) {
                secondStarted.complete(Unit)
                "second"
            }
        }

        assertNull(withTimeoutOrNull(150L) { secondStarted.await() })

        releaseFirst.complete(Unit)
        assertEquals("first", executedValue(first.await()))
        assertEquals("second", executedValue(second.await()))
    }

    @Test
    fun `service probe is skipped during service rate limit window`() = runBlocking {
        var now = 1_000L
        val first = LocalRobotProbeCoordinator.runServiceProbe(
            source = "ConnectorForegroundService.probeRobotWs",
            minIntervalMs = 10_000L,
            nowMs = { now },
        ) {
            "first"
        }

        assertEquals("first", executedValue(first))

        now = 2_000L
        val second = LocalRobotProbeCoordinator.runServiceProbe(
            source = "ConnectorForegroundService.probeRobotWs",
            minIntervalMs = 10_000L,
            nowMs = { now },
        ) {
            "second"
        }

        assertEquals(9_000L, skippedRun(second).retryAfterMs)

        now = 11_000L
        val third = LocalRobotProbeCoordinator.runServiceProbe(
            source = "ConnectorForegroundService.probeRobotWs",
            minIntervalMs = 10_000L,
            nowMs = { now },
        ) {
            "third"
        }

        assertEquals("third", executedValue(third))
    }

    private fun executedValue(run: LocalRobotProbeRun<String>): String {
        return when (run) {
            is LocalRobotProbeRun.Executed -> run.value
            is LocalRobotProbeRun.Skipped -> error("Expected executed probe, got skipped: ${run.reason}")
        }
    }

    private fun skippedRun(run: LocalRobotProbeRun<String>): LocalRobotProbeRun.Skipped {
        assertTrue(run is LocalRobotProbeRun.Skipped)
        return run as LocalRobotProbeRun.Skipped
    }
}
