package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundAccessPolicyTest {
    @Test
    fun `recognizes Transsion family manufacturers`() {
        assertTrue(BackgroundAccessPolicy.isTranssionFamily("TECNO"))
        assertTrue(BackgroundAccessPolicy.isTranssionFamily("Infinix Mobility Limited"))
        assertTrue(BackgroundAccessPolicy.isTranssionFamily("itel-mobile"))
        assertTrue(BackgroundAccessPolicy.isTranssionFamily(" Transsion "))
        assertTrue(BackgroundAccessPolicy.isTranssionFamily("unknown", brand = "TECNO"))
    }

    @Test
    fun `does not treat unrelated manufacturers as Transsion family`() {
        assertFalse(BackgroundAccessPolicy.isTranssionFamily("Samsung"))
        assertFalse(BackgroundAccessPolicy.isTranssionFamily("Google"))
        assertFalse(BackgroundAccessPolicy.isTranssionFamily(""))
    }

    @Test
    fun `requests notification permission only when Android requires it`() {
        assertFalse(
            BackgroundAccessPolicy.shouldRequestNotificationPermission(
                sdkInt = 32,
                permissionGranted = false,
                requestedVersion = 0,
            )
        )
        assertTrue(
            BackgroundAccessPolicy.shouldRequestNotificationPermission(
                sdkInt = 33,
                permissionGranted = false,
                requestedVersion = 0,
            )
        )
        assertFalse(
            BackgroundAccessPolicy.shouldRequestNotificationPermission(
                sdkInt = 34,
                permissionGranted = true,
                requestedVersion = 0,
            )
        )
        assertFalse(
            BackgroundAccessPolicy.shouldRequestNotificationPermission(
                sdkInt = 34,
                permissionGranted = false,
                requestedVersion = BackgroundAccessPolicy.NOTIFICATION_PROMPT_VERSION,
            )
        )
    }

    @Test
    fun `shows current guidance only for ready connector configuration`() {
        assertTrue(
            BackgroundAccessPolicy.shouldShowGuidance(
                setupCompleted = true,
                wifiReconnectPending = false,
                connectorConfigReady = true,
                shownVersion = 0,
                nowMs = 1_000L,
                deferredUntilMs = 0L,
            )
        )
        assertFalse(
            BackgroundAccessPolicy.shouldShowGuidance(
                setupCompleted = true,
                wifiReconnectPending = true,
                connectorConfigReady = true,
                shownVersion = 0,
                nowMs = 1_000L,
                deferredUntilMs = 0L,
            )
        )
        assertFalse(
            BackgroundAccessPolicy.shouldShowGuidance(
                setupCompleted = true,
                wifiReconnectPending = false,
                connectorConfigReady = false,
                shownVersion = 0,
                nowMs = 1_000L,
                deferredUntilMs = 0L,
            )
        )
        assertFalse(
            BackgroundAccessPolicy.shouldShowGuidance(
                setupCompleted = true,
                wifiReconnectPending = false,
                connectorConfigReady = true,
                shownVersion = BackgroundAccessPolicy.GUIDANCE_VERSION,
                nowMs = 1_000L,
                deferredUntilMs = 0L,
            )
        )
    }

    @Test
    fun `v1 guidance acknowledgement does not suppress bumped guidance`() {
        assertTrue(
            BackgroundAccessPolicy.shouldShowGuidance(
                setupCompleted = true,
                wifiReconnectPending = false,
                connectorConfigReady = true,
                shownVersion = 1,
                nowMs = 1_000L,
                deferredUntilMs = 0L,
            )
        )
    }

    @Test
    fun `guidance cooldown defers prompt only until deferred timestamp`() {
        val now = 1_000L
        val deferredUntil = BackgroundAccessPolicy.nextGuidanceDeferredUntil(now)

        assertFalse(
            BackgroundAccessPolicy.shouldShowGuidance(
                setupCompleted = true,
                wifiReconnectPending = false,
                connectorConfigReady = true,
                shownVersion = 1,
                nowMs = deferredUntil - 1L,
                deferredUntilMs = deferredUntil,
            )
        )
        assertTrue(
            BackgroundAccessPolicy.shouldShowGuidance(
                setupCompleted = true,
                wifiReconnectPending = false,
                connectorConfigReady = true,
                shownVersion = 1,
                nowMs = deferredUntil,
                deferredUntilMs = deferredUntil,
            )
        )
    }

    @Test
    fun `guidance actions defer without marking guidance as confirmed`() {
        val now = 5_000L
        val expectedDeferredUntil = now + BackgroundAccessPolicy.GUIDANCE_DEFER_MS

        val later = BackgroundAccessPolicy.guidancePersistenceForAction(
            BackgroundAccessGuidanceAction.LATER,
            now,
        )
        assertEquals(expectedDeferredUntil, later.deferredUntilMs)

        val launched = BackgroundAccessPolicy.guidancePersistenceForAction(
            BackgroundAccessGuidanceAction.SETTINGS_LAUNCHED,
            now,
        )
        assertEquals(expectedDeferredUntil, launched.deferredUntilMs)

        val failed = BackgroundAccessPolicy.guidancePersistenceForAction(
            BackgroundAccessGuidanceAction.SETTINGS_LAUNCH_FAILED,
            now,
        )
        assertNull(failed.deferredUntilMs)

        assertTrue(
            BackgroundAccessPolicy.shouldShowGuidance(
                setupCompleted = true,
                wifiReconnectPending = false,
                connectorConfigReady = true,
                shownVersion = 1,
                nowMs = expectedDeferredUntil,
                deferredUntilMs = later.deferredUntilMs ?: 0L,
            )
        )
    }

    @Test
    fun `guidance defer clamps invalid time and saturates overflow`() {
        assertEquals(
            BackgroundAccessPolicy.GUIDANCE_DEFER_MS,
            BackgroundAccessPolicy.nextGuidanceDeferredUntil(-1L),
        )
        assertEquals(
            Long.MAX_VALUE,
            BackgroundAccessPolicy.nextGuidanceDeferredUntil(Long.MAX_VALUE),
        )
    }
}
