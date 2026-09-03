package com.maxcorp.gosha.mobile

import java.util.Locale

internal object BackgroundAccessPolicy {
    const val GUIDANCE_VERSION = 2
    const val GUIDANCE_DEFER_MS = 24L * 60L * 60L * 1000L
    const val NOTIFICATION_PROMPT_VERSION = 1

    private val transsionManufacturerPrefixes = listOf(
        "tecno",
        "infinix",
        "itel",
        "transsion",
    )

    fun isTranssionFamily(manufacturer: String, brand: String = ""): Boolean {
        return listOf(manufacturer, brand).any { value ->
            val normalized = value.trim().lowercase(Locale.ROOT)
            transsionManufacturerPrefixes.any { prefix ->
                normalized == prefix ||
                    normalized.startsWith("$prefix ") ||
                    normalized.startsWith("$prefix-") ||
                    normalized.startsWith("${prefix}_")
            }
        }
    }

    fun shouldRequestNotificationPermission(
        sdkInt: Int,
        permissionGranted: Boolean,
        requestedVersion: Int,
    ): Boolean =
        sdkInt >= 33 &&
            !permissionGranted &&
            requestedVersion < NOTIFICATION_PROMPT_VERSION

    fun shouldShowGuidance(
        setupCompleted: Boolean,
        wifiReconnectPending: Boolean,
        connectorConfigReady: Boolean,
        shownVersion: Int,
        nowMs: Long,
        deferredUntilMs: Long,
    ): Boolean =
        setupCompleted &&
            !wifiReconnectPending &&
            connectorConfigReady &&
            shownVersion < GUIDANCE_VERSION &&
            nowMs >= deferredUntilMs

    fun guidancePersistenceForAction(
        action: BackgroundAccessGuidanceAction,
        nowMs: Long,
    ): BackgroundAccessGuidancePersistence {
        return when (action) {
            BackgroundAccessGuidanceAction.LATER,
            BackgroundAccessGuidanceAction.SETTINGS_LAUNCHED -> BackgroundAccessGuidancePersistence(
                deferredUntilMs = nextGuidanceDeferredUntil(nowMs),
            )
            BackgroundAccessGuidanceAction.SETTINGS_LAUNCH_FAILED -> BackgroundAccessGuidancePersistence()
        }
    }

    fun nextGuidanceDeferredUntil(nowMs: Long): Long {
        val normalizedNowMs = nowMs.coerceAtLeast(0L)
        return if (normalizedNowMs > Long.MAX_VALUE - GUIDANCE_DEFER_MS) {
            Long.MAX_VALUE
        } else {
            normalizedNowMs + GUIDANCE_DEFER_MS
        }
    }
}

internal enum class BackgroundAccessGuidanceAction {
    LATER,
    SETTINGS_LAUNCHED,
    SETTINGS_LAUNCH_FAILED,
}

internal data class BackgroundAccessGuidancePersistence(
    val deferredUntilMs: Long? = null,
)
