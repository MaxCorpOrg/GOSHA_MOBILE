package com.maxcorp.gosha.mobile

import android.content.SharedPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class RuntimeEventReporterConcurrencyTest {
    @Test
    fun `shared outbox flush preserves concurrent enqueue without duplicate delivery`() = runBlocking {
        val preferences = TestSharedPreferences()
        val reporterA = RuntimeEventReporter(preferences, OkHttpClient())
        val reporterB = RuntimeEventReporter(preferences, OkHttpClient())
        val target = RuntimeEventTarget(
            baseUrl = "http://panel.example.test",
            robotId = "gosha-main",
        )
        val eventA = event("event-A")
        val eventB = event("event-B")
        assertTrue(
            reporterA.enqueueFromCurrentRun(
                target = target,
                event = eventA,
                isCurrent = { true },
            )
        )

        val firstSenderEntered = CompletableDeferred<Unit>()
        val releaseFirstSender = CompletableDeferred<Unit>()
        val deliveredIds = Collections.synchronizedList(mutableListOf<String>())
        val firstFlush = async(Dispatchers.Default) {
            reporterA.flush(target) { pending ->
                val id = pending.optString("event_id")
                deliveredIds.add(id)
                if (id == "event-A") {
                    firstSenderEntered.complete(Unit)
                    releaseFirstSender.await()
                }
                RuntimeEventDeliveryResult.Delivered
            }
        }
        withTimeout(TEST_TIMEOUT_MS) {
            firstSenderEntered.await()
        }

        val secondPublish = async(Dispatchers.Default) {
            reporterB.publish(target, eventB)
        }
        withTimeout(TEST_TIMEOUT_MS) {
            while (!outboxIds(preferences, target.robotId).contains("event-B")) {
                delay(5)
            }
        }

        releaseFirstSender.complete(Unit)

        assertTrue(withTimeout(TEST_TIMEOUT_MS) { firstFlush.await() })
        assertTrue(withTimeout(TEST_TIMEOUT_MS) { secondPublish.await() })
        val deliveredSnapshot = synchronized(deliveredIds) {
            deliveredIds.toList()
        }
        assertEquals(2, deliveredSnapshot.size)
        assertEquals(setOf("event-A", "event-B"), deliveredSnapshot.toSet())
        assertEquals(emptyList<String>(), outboxIds(preferences, target.robotId))
    }

    @Test
    fun `stale enqueue rejection removes event and rolls back claimed signature`() = runBlocking {
        val preferences = TestSharedPreferences()
        val reporter = RuntimeEventReporter(preferences, OkHttpClient())
        val registry = ConnectorRunRegistry()
        val config = config(host = "192.168.1.159")
        val job = SupervisorJob()
        registry.activate(config, startId = 60, job = job)
        val target = RuntimeEventTarget(
            baseUrl = "http://panel.example.test",
            robotId = config.robotId,
        )

        val postCheckSignature = "true|executed|hub_ready"
        assertEquals(
            RuntimeProbeSideEffect.CLAIMED,
            registry.claimRuntimeProbeIfCurrent(
                config = config,
                startId = 60,
                job = job,
                signature = postCheckSignature,
                identityMatches = { true },
            )
        )
        var currentChecks = 0
        val rejectedAfterEnqueue = reporter.enqueueFromCurrentRun(
            target = target,
            event = event("stale-after-enqueue"),
            isCurrent = { currentChecks++ == 0 },
        )
        if (!rejectedAfterEnqueue) {
            registry.clearRuntimeProbeSignatureIfRun(config, 60, job, postCheckSignature)
        }

        assertFalse(rejectedAfterEnqueue)
        assertEquals(2, currentChecks)
        assertEquals(emptyList<String>(), outboxIds(preferences, target.robotId))
        assertEquals("", registry.currentRuntimeProbeSignatureForTest())

        val initialCheckSignature = "false|executed|hub_error"
        assertEquals(
            RuntimeProbeSideEffect.CLAIMED,
            registry.claimRuntimeProbeIfCurrent(
                config = config,
                startId = 60,
                job = job,
                signature = initialCheckSignature,
                identityMatches = { true },
            )
        )
        val rejectedBeforeEnqueue = reporter.enqueueFromCurrentRun(
            target = target,
            event = event("stale-before-enqueue"),
            isCurrent = { false },
        )
        if (!rejectedBeforeEnqueue) {
            registry.clearRuntimeProbeSignatureIfRun(config, 60, job, initialCheckSignature)
        }

        assertFalse(rejectedBeforeEnqueue)
        assertEquals(emptyList<String>(), outboxIds(preferences, target.robotId))
        assertEquals("", registry.currentRuntimeProbeSignatureForTest())
    }

    private fun event(id: String): JSONObject =
        JSONObject()
            .put("schema_version", RuntimeEventReporter.SCHEMA_VERSION)
            .put("event_id", id)
            .put("event_type", "mobile.robot_link.changed")

    private fun outboxIds(preferences: SharedPreferences, robotId: String): List<String> {
        val pending = JSONArray(preferences.getString("outbox_$robotId", "[]").orEmpty())
        return (0 until pending.length()).mapNotNull { index ->
            pending.optJSONObject(index)?.optString("event_id", "")
        }
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

    private companion object {
        const val TEST_TIMEOUT_MS = 2_000L
    }
}
