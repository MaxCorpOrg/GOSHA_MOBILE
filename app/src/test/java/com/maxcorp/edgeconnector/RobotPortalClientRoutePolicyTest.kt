package com.maxcorp.gosha.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotPortalClientRoutePolicyTest {
    @Test
    fun `robot portal skips default route when robot network is available`() {
        assertFalse(
            RobotPortalClient.shouldIncludeDefaultNetworkCandidate(
                url = "http://192.168.4.1/submit",
                hasRobotNetworkCandidate = true,
            )
        )
    }

    @Test
    fun `robot portal keeps default route when robot network is unknown`() {
        assertTrue(
            RobotPortalClient.shouldIncludeDefaultNetworkCandidate(
                url = "http://192.168.4.1/submit",
                hasRobotNetworkCandidate = false,
            )
        )
    }

    @Test
    fun `external url keeps default route even with robot network`() {
        assertTrue(
            RobotPortalClient.shouldIncludeDefaultNetworkCandidate(
                url = "http://151.241.228.232:18876/api/mobile/robots/gosha-main/runtime",
                hasRobotNetworkCandidate = true,
            )
        )
    }
}
