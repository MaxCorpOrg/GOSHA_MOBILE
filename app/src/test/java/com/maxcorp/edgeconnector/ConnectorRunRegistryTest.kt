package com.maxcorp.gosha.mobile

import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorRunRegistryTest {
    @Test
    fun `superseded presence call is cancelled and cannot register late`() {
        val registry = ConnectorRunRegistry()
        val oldConfig = config(host = "192.168.1.159")
        val oldJob = SupervisorJob()
        registry.activate(oldConfig, startId = 10, job = oldJob)

        val presenceCall = testCall("/presence")
        assertTrue(
            registry.registerPanelCallIfCurrent(
                config = oldConfig,
                startId = 10,
                job = oldJob,
                call = presenceCall,
                identityMatches = { true },
            )
        )

        val newJob = SupervisorJob()
        val cancellation = registry.activate(
            config = config(host = "192.168.1.160"),
            startId = 11,
            job = newJob,
        )
        cancellation.job?.cancel()
        cancellation.panelCalls.forEach { it.cancel() }

        assertSame(oldJob, cancellation.job)
        assertEquals(listOf(presenceCall), cancellation.panelCalls)
        assertTrue(oldJob.isCancelled)
        assertTrue(presenceCall.isCanceled())

        val latePresenceCall = testCall("/presence-late")
        assertFalse(
            registry.registerPanelCallIfCurrent(
                config = oldConfig,
                startId = 10,
                job = oldJob,
                call = latePresenceCall,
                identityMatches = { true },
            )
        )
    }

    @Test
    fun `superseded runtime event does not enqueue or advance signature`() {
        val registry = ConnectorRunRegistry()
        val oldConfig = config(host = "192.168.1.159")
        val oldJob = SupervisorJob()
        registry.activate(oldConfig, startId = 20, job = oldJob)
        registry.activate(config(host = "192.168.1.160"), startId = 21, job = SupervisorJob())

        var enqueueCount = 0
        val stale = registry.claimRuntimeProbeIfCurrent(
            config = oldConfig,
            startId = 20,
            job = oldJob,
            signature = "true|executed|hub_ready",
            identityMatches = { true },
        )

        assertEquals(RuntimeProbeSideEffect.STALE, stale)
        assertEquals(0, enqueueCount)
        assertEquals("", registry.currentRuntimeProbeSignatureForTest())
    }

    @Test
    fun `current runtime signature enqueues once then only flushes`() {
        val registry = ConnectorRunRegistry()
        val currentConfig = config(host = "192.168.1.159")
        val currentJob = SupervisorJob()
        registry.activate(currentConfig, startId = 30, job = currentJob)

        var enqueueCount = 0
        val first = registry.claimRuntimeProbeIfCurrent(
            config = currentConfig,
            startId = 30,
            job = currentJob,
            signature = "true|executed|hub_ready",
            identityMatches = { true },
        )
        if (first == RuntimeProbeSideEffect.CLAIMED) {
            enqueueCount += 1
        }
        val second = registry.claimRuntimeProbeIfCurrent(
            config = currentConfig,
            startId = 30,
            job = currentJob,
            signature = "true|executed|hub_ready",
            identityMatches = { true },
        )

        assertEquals(RuntimeProbeSideEffect.CLAIMED, first)
        assertEquals(RuntimeProbeSideEffect.FLUSH_ONLY, second)
        assertEquals(1, enqueueCount)
        assertEquals("true|executed|hub_ready", registry.currentRuntimeProbeSignatureForTest())
    }

    @Test
    fun `stale status side effect is skipped after supersede`() {
        val registry = ConnectorRunRegistry()
        val oldConfig = config(host = "192.168.1.159")
        val oldJob = SupervisorJob()
        registry.activate(oldConfig, startId = 40, job = oldJob)
        val newConfig = config(host = "192.168.1.160")
        val newJob = SupervisorJob()
        registry.activate(newConfig, startId = 41, job = newJob)

        var statusWrites = 0
        val staleRan = registry.runIfCurrent(
            config = oldConfig,
            startId = 40,
            job = oldJob,
            identityMatches = { true },
        ) {
            statusWrites += 1
        }
        val currentRan = registry.runIfCurrent(
            config = newConfig,
            startId = 41,
            job = newJob,
            identityMatches = { true },
        ) {
            statusWrites += 1
        }

        assertFalse(staleRan)
        assertTrue(currentRan)
        assertEquals(1, statusWrites)
    }

    @Test
    fun `registration and status use current identity after captured read becomes stale`() {
        val registry = ConnectorRunRegistry()
        val currentConfig = config(host = "192.168.1.159")
        val currentJob = SupervisorJob()
        registry.activate(currentConfig, startId = 50, job = currentJob)

        var currentIdentityMatches = true
        val capturedIdentityMatches = currentIdentityMatches
        currentIdentityMatches = false

        val presenceCall = testCall("/presence")
        val registered = registry.registerPanelCallIfCurrent(
            config = currentConfig,
            startId = 50,
            job = currentJob,
            call = presenceCall,
            identityMatches = { currentIdentityMatches },
        )
        var statusWrites = 0
        val statusRan = registry.runIfCurrent(
            config = currentConfig,
            startId = 50,
            job = currentJob,
            identityMatches = { currentIdentityMatches },
        ) {
            statusWrites += 1
        }
        val runtimeClaim = registry.claimRuntimeProbeIfCurrent(
            config = currentConfig,
            startId = 50,
            job = currentJob,
            signature = "true|executed|hub_ready",
            identityMatches = { currentIdentityMatches },
        )

        assertTrue(capturedIdentityMatches)
        assertFalse(registered)
        assertFalse(statusRan)
        assertEquals(0, statusWrites)
        assertEquals(RuntimeProbeSideEffect.STALE, runtimeClaim)
        assertEquals("", registry.currentRuntimeProbeSignatureForTest())
    }

    private fun config(host: String): ConnectorConfig =
        ConnectorConfig(
            hubBaseUrl = "ws://hub.example.test/mcp",
            robotId = "gosha-main",
            expectedDeviceId = "aa:bb:cc:dd:ee:ff",
            token = "token",
            robotHost = host,
            robotPort = 8080,
            robotPath = "/ws",
        )

    private fun testCall(path: String) =
        OkHttpClient().newCall(
            Request.Builder()
                .url("http://127.0.0.1$path")
                .build()
        )
}
