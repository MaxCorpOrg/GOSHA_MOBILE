package com.maxcorp.gosha.mobile

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class RuntimeEventTarget(
    val baseUrl: String,
    val robotId: String,
    val panelClientToken: String = "",
    val onboardingCode: String = "",
)

internal sealed class RuntimeEventDeliveryResult {
    data object Delivered : RuntimeEventDeliveryResult()
    data object Stale : RuntimeEventDeliveryResult()
    data class Failed(val failure: Throwable?) : RuntimeEventDeliveryResult()
}

internal fun isPermanentRuntimeEventRejection(failure: Throwable?): Boolean =
    failure is PanelHttpException && failure.statusCode in setOf(413, 422)

internal fun trimRuntimeEventOutbox(source: JSONArray, maxEvents: Int = 100): JSONArray {
    val trimmed = JSONArray()
    val boundedMax = maxEvents.coerceAtLeast(1)
    val start = (source.length() - boundedMax).coerceAtLeast(0)
    for (index in start until source.length()) {
        source.optJSONObject(index)?.let(trimmed::put)
    }
    return trimmed
}

/**
 * Builds versioned mobile runtime events and keeps a bounded, secret-free
 * persistent outbox while the phone cannot reach the platform.
 */
class RuntimeEventReporter internal constructor(
    private val preferences: SharedPreferences,
    private val http: OkHttpClient,
) {
    private val sequence = AtomicLong(0)

    constructor(context: Context, http: OkHttpClient) : this(
        preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
        http = http,
    )

    val sourceId: String = synchronized(sourceIdLock) {
        preferences.getString(KEY_SOURCE_ID, "").orEmpty().ifBlank {
            "mobile-${UUID.randomUUID()}".also { created ->
                preferences.edit().putString(KEY_SOURCE_ID, created).commit()
            }
        }
    }
    val sessionId: String = "session-${UUID.randomUUID()}"

    fun event(
        eventType: String,
        severity: String = "info",
        state: JSONObject? = null,
        link: JSONObject? = null,
        task: JSONObject? = null,
        error: JSONObject? = null,
        metrics: JSONObject? = null,
        attributes: JSONObject? = null,
        correlationId: String = "",
        causationId: String = "",
    ): JSONObject {
        val trace = JSONObject().put("session_id", sessionId)
        if (correlationId.isNotBlank()) trace.put("correlation_id", correlationId)
        if (causationId.isNotBlank()) trace.put("causation_id", causationId)
        return JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("event_id", UUID.randomUUID().toString())
            .put("event_type", eventType)
            .put(
                "source",
                JSONObject()
                    .put("id", sourceId)
                    .put("instance_id", sessionId)
                    .put("app_version", BuildConfig.VERSION_NAME),
            )
            .put("trace", trace)
            .put("occurred_at", utcNow())
            .put("sequence", sequence.incrementAndGet())
            .put("severity", severity)
            .apply {
                state?.let { put("state", it) }
                link?.let { put("link", it) }
                task?.let { put("task", it) }
                error?.let { put("error", it) }
                metrics?.let { put("metrics", it) }
                attributes?.let { put("attributes", it) }
            }
    }

    suspend fun publish(target: RuntimeEventTarget, event: JSONObject): Boolean {
        if (!enqueue(target, event)) {
            return false
        }
        if (target.baseUrl.isBlank()) {
            return false
        }
        return flush(target)
    }

    suspend fun flush(target: RuntimeEventTarget): Boolean = flushMutex.withLock {
        flushLocked(target) { item ->
            sendRuntimeEvent(target, item)
        }
    }

    internal suspend fun flush(
        target: RuntimeEventTarget,
        sender: suspend (JSONObject) -> RuntimeEventDeliveryResult,
    ): Boolean = flushMutex.withLock {
        flushLocked(target, sender)
    }

    internal suspend fun enqueueFromCurrentRun(
        target: RuntimeEventTarget,
        event: JSONObject,
        isCurrent: () -> Boolean,
    ): Boolean {
        return flushMutex.withLock {
            synchronized(outboxLock) {
                if (target.robotId.isBlank() || !isCurrent()) {
                    false
                } else {
                    enqueueLocked(target, event)
                    if (isCurrent()) {
                        true
                    } else {
                        removeFirstOutboxEvent(target.robotId, event)
                        false
                    }
                }
            }
        }
    }

    private fun enqueue(target: RuntimeEventTarget, event: JSONObject): Boolean {
        return synchronized(outboxLock) {
            enqueueLocked(target, event)
        }
    }

    private fun enqueueLocked(target: RuntimeEventTarget, event: JSONObject): Boolean {
        if (target.robotId.isBlank()) return false
        val pending = readOutbox(target.robotId)
        pending.put(JSONObject(event.toString()))
        trimAndSaveOutbox(target.robotId, pending)
        return true
    }

    private suspend fun flushLocked(
        target: RuntimeEventTarget,
        sender: suspend (JSONObject) -> RuntimeEventDeliveryResult,
    ): Boolean {
        if (target.robotId.isBlank() || target.baseUrl.isBlank()) return false
        while (true) {
            val item = synchronized(outboxLock) {
                readOutbox(target.robotId).optJSONObject(0)
            } ?: return true
            when (val delivery = sender(item)) {
                RuntimeEventDeliveryResult.Delivered -> {
                    synchronized(outboxLock) {
                        removeFirstOutboxEvent(target.robotId, item)
                    }
                }
                RuntimeEventDeliveryResult.Stale -> return false
                is RuntimeEventDeliveryResult.Failed -> {
                    val permanentlyRejected = isPermanentRuntimeEventRejection(delivery.failure)
                    if (permanentlyRejected) {
                        synchronized(outboxLock) {
                            removeFirstOutboxEvent(target.robotId, item)
                        }
                    } else {
                        return false
                    }
                }
            }
        }
    }

    private suspend fun sendRuntimeEvent(
        target: RuntimeEventTarget,
        event: JSONObject,
    ): RuntimeEventDeliveryResult {
        return runCatching {
            PanelApiClient.publishRuntimeEvent(
                http = http,
                baseUrl = target.baseUrl,
                robotId = target.robotId,
                event = event,
                panelClientToken = target.panelClientToken,
                onboardingCode = target.onboardingCode,
            )
        }.fold(
            onSuccess = { RuntimeEventDeliveryResult.Delivered },
            onFailure = { RuntimeEventDeliveryResult.Failed(it) },
        )
    }

    private fun readOutbox(robotId: String): JSONArray {
        val raw = preferences.getString(outboxKey(robotId), "[]").orEmpty()
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }

    private fun trimAndSaveOutbox(robotId: String, source: JSONArray) {
        saveOutbox(robotId, trimRuntimeEventOutbox(source, MAX_OUTBOX_EVENTS))
    }

    private fun removeFirstOutboxEvent(robotId: String, expected: JSONObject): Boolean {
        val pending = readOutbox(robotId)
        val remaining = JSONArray()
        var removed = false
        val expectedId = expected.optString("event_id", "")
        for (index in 0 until pending.length()) {
            val item = pending.optJSONObject(index) ?: continue
            val matches = if (expectedId.isNotBlank()) {
                item.optString("event_id", "") == expectedId
            } else {
                item.toString() == expected.toString()
            }
            if (!removed && matches) {
                removed = true
            } else {
                remaining.put(item)
            }
        }
        saveOutbox(robotId, remaining)
        return removed
    }

    private fun saveOutbox(robotId: String, outbox: JSONArray) {
        preferences.edit().putString(outboxKey(robotId), outbox.toString()).apply()
    }

    private fun outboxKey(robotId: String): String = "${KEY_OUTBOX_PREFIX}${robotId}"

    private fun utcNow(): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US,
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    companion object {
        const val SCHEMA_VERSION = "gosha.runtime.event.v1"
        private const val PREFERENCES_NAME = "gosha_runtime_events"
        private const val KEY_SOURCE_ID = "source_id"
        private const val KEY_OUTBOX_PREFIX = "outbox_"
        private const val MAX_OUTBOX_EVENTS = 100
        private val sourceIdLock = Any()
        private val flushMutex = Mutex()
        private val outboxLock = Any()
    }
}
