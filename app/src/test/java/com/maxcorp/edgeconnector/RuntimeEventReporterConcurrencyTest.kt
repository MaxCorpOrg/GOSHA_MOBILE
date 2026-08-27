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
        val preferences = InMemorySharedPreferences()
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
        val preferences = InMemorySharedPreferences()
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

    private class InMemorySharedPreferences : SharedPreferences {
        private val lock = Any()
        private val values = LinkedHashMap<String, Any>()
        private val listeners = LinkedHashSet<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = synchronized(lock) {
            LinkedHashMap(values)
        }

        override fun getString(key: String?, defValue: String?): String? = synchronized(lock) {
            values[key] as? String ?: defValue
        }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            synchronized(lock) {
                @Suppress("UNCHECKED_CAST")
                val stored = values[key] as? Set<String>
                stored?.toMutableSet() ?: defValues
            }

        override fun getInt(key: String?, defValue: Int): Int = synchronized(lock) {
            values[key] as? Int ?: defValue
        }

        override fun getLong(key: String?, defValue: Long): Long = synchronized(lock) {
            values[key] as? Long ?: defValue
        }

        override fun getFloat(key: String?, defValue: Float): Float = synchronized(lock) {
            values[key] as? Float ?: defValue
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = synchronized(lock) {
            values[key] as? Boolean ?: defValue
        }

        override fun contains(key: String?): Boolean = synchronized(lock) {
            values.containsKey(key)
        }

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {
            if (listener == null) return
            synchronized(lock) {
                listeners.add(listener)
            }
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {
            synchronized(lock) {
                listeners.remove(listener)
            }
        }

        private inner class Editor : SharedPreferences.Editor {
            private val changes = LinkedHashMap<String, Any>()
            private val removals = LinkedHashSet<String>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                stage(key, value)
            }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?,
            ): SharedPreferences.Editor = apply {
                stage(key, values?.toSet())
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                stage(key, value)
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                stage(key, value)
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                stage(key, value)
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                stage(key, value)
            }

            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key == null) return@apply
                changes.remove(key)
                removals.add(key)
            }

            override fun clear(): SharedPreferences.Editor = apply {
                clearRequested = true
                changes.clear()
                removals.clear()
            }

            override fun commit(): Boolean {
                applyChanges()
                return true
            }

            override fun apply() {
                applyChanges()
            }

            private fun stage(key: String?, value: Any?) {
                if (key == null) return
                if (value == null) {
                    changes.remove(key)
                    removals.add(key)
                } else {
                    removals.remove(key)
                    changes[key] = value
                }
            }

            private fun applyChanges() {
                val changedKeys: Set<String>
                val listenersSnapshot: List<SharedPreferences.OnSharedPreferenceChangeListener>
                synchronized(lock) {
                    val changed = LinkedHashSet<String>()
                    if (clearRequested) {
                        changed.addAll(values.keys)
                        values.clear()
                    }
                    for (key in removals) {
                        if (values.remove(key) != null) {
                            changed.add(key)
                        }
                    }
                    for ((key, value) in changes) {
                        if (values[key] != value) {
                            changed.add(key)
                        }
                        values[key] = value
                    }
                    changedKeys = changed
                    listenersSnapshot = listeners.toList()
                }
                for (key in changedKeys) {
                    for (listener in listenersSnapshot) {
                        listener.onSharedPreferenceChanged(this@InMemorySharedPreferences, key)
                    }
                }
            }
        }
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 2_000L
    }
}
