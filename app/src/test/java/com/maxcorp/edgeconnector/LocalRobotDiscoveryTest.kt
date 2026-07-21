package com.maxcorp.gosha.mobile

import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRobotDiscoveryTest {
    @Test
    fun `tcp prefilter stays parallel while websocket confirmations are sequential`() = runBlocking {
        val tcpInFlight = AtomicInteger(0)
        val maxTcpInFlight = AtomicInteger(0)
        val wsInFlight = AtomicInteger(0)
        val maxWsInFlight = AtomicInteger(0)
        val wsHosts = Collections.synchronizedList(mutableListOf<String>())
        val candidateSuffixes = setOf(".100", ".101", ".102")

        val hooks = LocalRobotDiscoveryProbeHooks(
            isPortOpen = { _, host, _, _ ->
                val current = tcpInFlight.incrementAndGet()
                maxTcpInFlight.updateAndGet { previous -> maxOf(previous, current) }
                delay(25L)
                tcpInFlight.decrementAndGet()
                candidateSuffixes.any { host.endsWith(it) }
            },
            isWsOpen = { _, host, _ ->
                val current = wsInFlight.incrementAndGet()
                maxWsInFlight.updateAndGet { previous -> maxOf(previous, current) }
                wsHosts.add(host)
                delay(25L)
                wsInFlight.decrementAndGet()
                host.endsWith(".102")
            },
        )

        val result = LocalRobotDiscovery.discover(
            subnetPrefix = "192.168.1",
            probeHooks = hooks,
        )

        assertEquals("192.168.1.102", result.first)
        assertTrue("TCP prefilter was not parallel", maxTcpInFlight.get() > 1)
        assertEquals(1, maxWsInFlight.get())
        assertEquals(
            listOf("192.168.1.100", "192.168.1.101", "192.168.1.102"),
            wsHosts.toList(),
        )
    }
}
