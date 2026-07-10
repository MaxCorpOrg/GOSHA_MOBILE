package com.maxcorp.gosha.mobile

import org.junit.Assert.assertFalse
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
            )
        )
        assertFalse(
            BackgroundAccessPolicy.shouldShowGuidance(
                setupCompleted = true,
                wifiReconnectPending = true,
                connectorConfigReady = true,
                shownVersion = 0,
            )
        )
        assertFalse(
            BackgroundAccessPolicy.shouldShowGuidance(
                setupCompleted = true,
                wifiReconnectPending = false,
                connectorConfigReady = false,
                shownVersion = 0,
            )
        )
        assertFalse(
            BackgroundAccessPolicy.shouldShowGuidance(
                setupCompleted = true,
                wifiReconnectPending = false,
                connectorConfigReady = true,
                shownVersion = BackgroundAccessPolicy.GUIDANCE_VERSION,
            )
        )
    }
}
