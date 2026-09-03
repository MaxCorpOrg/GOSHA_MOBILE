package com.maxcorp.gosha.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotWifiConnectorPolicyTest {
    @Test
    fun `active callback handles loss of its delivered network`() {
        assertTrue(
            RobotWifiConnector.shouldHandleNetworkLoss(
                deliveredAvailable = true,
                callbackIsActive = true,
                requestIsActive = true,
                networkIsActive = true,
            )
        )
    }

    @Test
    fun `stale callback cannot release newer network request`() {
        assertFalse(
            RobotWifiConnector.shouldHandleNetworkLoss(
                deliveredAvailable = true,
                callbackIsActive = false,
                requestIsActive = true,
                networkIsActive = true,
            )
        )
        assertFalse(
            RobotWifiConnector.shouldHandleNetworkLoss(
                deliveredAvailable = true,
                callbackIsActive = true,
                requestIsActive = true,
                networkIsActive = false,
            )
        )
    }

    @Test
    fun `stale available callback cannot publish older network`() {
        assertTrue(
            RobotWifiConnector.shouldHandleAvailableCallback(
                callbackIsActive = true,
                requestIsActive = true,
            )
        )
        assertFalse(
            RobotWifiConnector.shouldHandleAvailableCallback(
                callbackIsActive = true,
                requestIsActive = false,
            )
        )
        assertFalse(
            RobotWifiConnector.shouldHandleAvailableCallback(
                callbackIsActive = false,
                requestIsActive = true,
            )
        )
    }

    @Test
    fun `stale generation cannot release newer network request on lost`() {
        assertFalse(
            RobotWifiConnector.shouldHandleNetworkLoss(
                deliveredAvailable = true,
                callbackIsActive = true,
                requestIsActive = false,
                networkIsActive = true,
            )
        )
    }

    @Test
    fun `stale unavailable callback cannot release newer network request`() {
        assertTrue(
            RobotWifiConnector.shouldHandleUnavailableCallback(
                callbackIsActive = true,
                requestIsActive = true,
            )
        )
        assertFalse(
            RobotWifiConnector.shouldHandleUnavailableCallback(
                callbackIsActive = false,
                requestIsActive = true,
            )
        )
        assertFalse(
            RobotWifiConnector.shouldHandleUnavailableCallback(
                callbackIsActive = true,
                requestIsActive = false,
            )
        )
    }
}
