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
                networkIsActive = true,
            )
        )
        assertFalse(
            RobotWifiConnector.shouldHandleNetworkLoss(
                deliveredAvailable = true,
                callbackIsActive = true,
                networkIsActive = false,
            )
        )
    }

    @Test
    fun `stale unavailable callback cannot release newer network request`() {
        assertTrue(RobotWifiConnector.shouldHandleUnavailableCallback(callbackIsActive = true))
        assertFalse(RobotWifiConnector.shouldHandleUnavailableCallback(callbackIsActive = false))
    }
}
