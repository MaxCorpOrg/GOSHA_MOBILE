package com.maxcorp.gosha.mobile

import java.util.Locale

internal object BackgroundAccessPolicy {
    const val GUIDANCE_VERSION = 1
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
    ): Boolean =
        setupCompleted &&
            !wifiReconnectPending &&
            connectorConfigReady &&
            shownVersion < GUIDANCE_VERSION
}
