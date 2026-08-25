package com.maxcorp.gosha.mobile

import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRobotDiscoveryTest {
    @Test
    fun `tcp prefilter stays parallel while identity confirmations are sequential`() = runBlocking {
        val tcpInFlight = AtomicInteger(0)
        val maxTcpInFlight = AtomicInteger(0)
        val identityInFlight = AtomicInteger(0)
        val maxIdentityInFlight = AtomicInteger(0)
        val identityHosts = Collections.synchronizedList(mutableListOf<String>())
        val candidateSuffixes = setOf(".100", ".101", ".102")

        val hooks = LocalRobotDiscoveryProbeHooks(
            isPortOpen = { _, host, _, _ ->
                val current = tcpInFlight.incrementAndGet()
                maxTcpInFlight.updateAndGet { previous -> maxOf(previous, current) }
                delay(25L)
                tcpInFlight.decrementAndGet()
                candidateSuffixes.any { host.endsWith(it) }
            },
            isWsOpen = { _, _, _ -> error("identity-aware discovery must not use ws-only confirmation") },
            matchesDeviceIdentity = { _, host, expectedDeviceId, _ ->
                val current = identityInFlight.incrementAndGet()
                maxIdentityInFlight.updateAndGet { previous -> maxOf(previous, current) }
                identityHosts.add("$host:$expectedDeviceId")
                delay(25L)
                identityInFlight.decrementAndGet()
                host.endsWith(".102") && expectedDeviceId == "aa:bb:cc:dd:ee:ff"
            },
        )

        val result = LocalRobotDiscovery.discover(
            subnetPrefix = "192.168.1",
            probeHooks = hooks,
            allowGenericSweep = true,
            expectedDeviceId = "aa:bb:cc:dd:ee:ff",
        )

        assertEquals("192.168.1.102", result.first)
        assertTrue("TCP prefilter was not parallel", maxTcpInFlight.get() > 1)
        assertEquals(1, maxIdentityInFlight.get())
        assertEquals(
            listOf(
                "192.168.1.100:aa:bb:cc:dd:ee:ff",
                "192.168.1.101:aa:bb:cc:dd:ee:ff",
                "192.168.1.102:aa:bb:cc:dd:ee:ff",
            ),
            identityHosts.toList(),
        )
    }

    @Test
    fun `generic subnet sweep runs only when explicitly enabled`() = runBlocking {
        val tcpCalls = AtomicInteger(0)
        val hooks = LocalRobotDiscoveryProbeHooks(
            isPortOpen = { _, host, _, _ ->
                tcpCalls.incrementAndGet()
                host.endsWith(".102")
            },
            isWsOpen = { _, _, _ -> error("generic identity-aware discovery must not use ws-only confirmation") },
            matchesDeviceIdentity = { _, host, expectedDeviceId, _ ->
                host.endsWith(".102") && expectedDeviceId == "aa:bb:cc:dd:ee:ff"
            },
        )

        val locked = LocalRobotDiscovery.discover(
            subnetPrefix = "192.168.1",
            probeHooks = hooks,
            allowGenericSweep = false,
        )

        assertNull(locked.first)
        assertEquals(0, tcpCalls.get())

        val generic = LocalRobotDiscovery.discover(
            subnetPrefix = "192.168.1",
            probeHooks = hooks,
            allowGenericSweep = true,
            expectedDeviceId = "aa:bb:cc:dd:ee:ff",
        )

        assertEquals("192.168.1.102", generic.first)
        assertTrue(tcpCalls.get() > 0)
    }

    @Test
    fun `preferred host requires matching device identity while generic sweep remains disabled`() = runBlocking {
        val checkedHosts = Collections.synchronizedList(mutableListOf<String>())
        val hooks = LocalRobotDiscoveryProbeHooks(
            isPortOpen = { _, _, _, _ -> error("TCP prefilter must not run without generic sweep") },
            isWsOpen = { _, _, _ -> error("preferred identity-aware discovery must not use ws-only confirmation") },
            matchesDeviceIdentity = { _, host, expectedDeviceId, _ ->
                checkedHosts.add("$host:$expectedDeviceId")
                host.endsWith(".159") && expectedDeviceId == "aa:bb:cc:dd:ee:ff"
            },
        )

        val result = LocalRobotDiscovery.discover(
            subnetPrefix = "192.168.1",
            preferredHosts = listOf("192.168.1.159"),
            probeHooks = hooks,
            allowGenericSweep = false,
            expectedDeviceId = "aa:bb:cc:dd:ee:ff",
        )

        assertEquals("192.168.1.159", result.first)
        assertEquals(listOf("192.168.1.159:aa:bb:cc:dd:ee:ff"), checkedHosts.toList())
    }

    @Test
    fun `preferred host rejects mismatched device identity without generic sweep`() = runBlocking {
        val identityHosts = Collections.synchronizedList(mutableListOf<String>())
        val hooks = LocalRobotDiscoveryProbeHooks(
            isPortOpen = { _, _, _, _ -> error("TCP prefilter must not run without generic sweep") },
            isWsOpen = { _, _, _ -> error("preferred identity-aware discovery must not use ws-only confirmation") },
            matchesDeviceIdentity = { _, host, expectedDeviceId, _ ->
                identityHosts.add("$host:$expectedDeviceId")
                false
            },
        )

        val result = LocalRobotDiscovery.discover(
            subnetPrefix = "192.168.1",
            preferredHosts = listOf("192.168.1.159"),
            probeHooks = hooks,
            allowGenericSweep = false,
            expectedDeviceId = "aa:bb:cc:dd:ee:ff",
        )

        assertNull(result.first)
        assertEquals(listOf("192.168.1.159:aa:bb:cc:dd:ee:ff"), identityHosts.toList())
    }

    @Test
    fun `local discovery requires expected device id`() = runBlocking {
        val tcpCalls = AtomicInteger(0)
        val hooks = LocalRobotDiscoveryProbeHooks(
            isPortOpen = { _, _, _, _ ->
                tcpCalls.incrementAndGet()
                true
            },
            isWsOpen = { _, _, _ -> true },
        )

        val result = LocalRobotDiscovery.discover(
            subnetPrefix = "192.168.1",
            preferredHosts = listOf("192.168.1.159"),
            probeHooks = hooks,
            allowGenericSweep = true,
            expectedDeviceId = "",
        )

        assertNull(result.first)
        assertEquals(0, tcpCalls.get())
    }

    @Test
    fun `generic subnet sweep rejects host with mismatched device identity`() = runBlocking {
        val identityHosts = Collections.synchronizedList(mutableListOf<String>())
        val hooks = LocalRobotDiscoveryProbeHooks(
            isPortOpen = { _, host, _, _ ->
                host.endsWith(".102") || host.endsWith(".103")
            },
            isWsOpen = { _, _, _ -> error("generic identity-aware discovery must not use ws-only confirmation") },
            matchesDeviceIdentity = { _, host, expectedDeviceId, _ ->
                identityHosts.add("$host:$expectedDeviceId")
                host.endsWith(".103") && expectedDeviceId == "aa:bb:cc:dd:ee:ff"
            },
        )

        val result = LocalRobotDiscovery.discover(
            subnetPrefix = "192.168.1",
            probeHooks = hooks,
            allowGenericSweep = true,
            expectedDeviceId = "aa:bb:cc:dd:ee:ff",
        )

        assertEquals("192.168.1.103", result.first)
        assertEquals(
            listOf("192.168.1.102:aa:bb:cc:dd:ee:ff", "192.168.1.103:aa:bb:cc:dd:ee:ff"),
            identityHosts.toList(),
        )
    }
}
