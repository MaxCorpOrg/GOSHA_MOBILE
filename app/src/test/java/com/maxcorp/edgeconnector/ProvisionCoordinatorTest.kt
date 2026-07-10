package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisionCoordinatorTest {
    private val robotWifiPrefix = RobotBranding.PRIMARY_WIFI_PREFIX

    @Test
    fun `return check waits until robot portal closes`() {
        assertFalse(
            ProvisionCoordinator.shouldStartReturnCheck(
                awaitingRobotProvision = true,
                robotWifiPortalActive = true,
                returnCheckRunning = false,
            )
        )
        assertTrue(
            ProvisionCoordinator.shouldStartReturnCheck(
                awaitingRobotProvision = true,
                robotWifiPortalActive = false,
                returnCheckRunning = false,
            )
        )
    }

    @Test
    fun `return check does not start twice or outside provisioning`() {
        assertFalse(
            ProvisionCoordinator.shouldStartReturnCheck(
                awaitingRobotProvision = true,
                robotWifiPortalActive = false,
                returnCheckRunning = true,
            )
        )
        assertFalse(
            ProvisionCoordinator.shouldStartReturnCheck(
                awaitingRobotProvision = false,
                robotWifiPortalActive = false,
                returnCheckRunning = false,
            )
        )
    }

    @Test
    fun `stays on robot wifi first without manual hint`() {
        val plan = ProvisionCoordinator.planAttempt(
            index = 0,
            totalAttempts = 32,
            settleAttempts = 6,
            currentSsid = "GOSHA-A-5B09",
            visibleRobotSsid = "GOSHA-A-5B09",
            hasHomeSubnet = true,
            robotWifiPrefix = robotWifiPrefix,
        )

        require(plan is ProvisionAttemptPlan.WaitOnRobotWifi)
        assertFalse(plan.showManualSwitchHint)
    }

    @Test
    fun `stays on robot wifi with manual hint after several attempts`() {
        val plan = ProvisionCoordinator.planAttempt(
            index = 4,
            totalAttempts = 32,
            settleAttempts = 6,
            currentSsid = "GOSHA-A-5B09",
            visibleRobotSsid = "",
            hasHomeSubnet = true,
            robotWifiPrefix = robotWifiPrefix,
        )

        require(plan is ProvisionAttemptPlan.WaitOnRobotWifi)
        assertTrue(plan.showManualSwitchHint)
    }

    @Test
    fun `without home subnet coordinator schedules panel check on defined attempts`() {
        val plan = ProvisionCoordinator.planAttempt(
            index = 11,
            totalAttempts = 32,
            settleAttempts = 6,
            currentSsid = "4G-CPE-1884",
            visibleRobotSsid = "",
            hasHomeSubnet = false,
            robotWifiPrefix = robotWifiPrefix,
        )

        require(plan is ProvisionAttemptPlan.WaitForHomeWifi)
        assertTrue(plan.shouldCheckPanel)
    }

    @Test
    fun `settle phase checks panel early only when robot is hidden`() {
        val hiddenPlan = ProvisionCoordinator.planAttempt(
            index = 2,
            totalAttempts = 32,
            settleAttempts = 6,
            currentSsid = "4G-CPE-1884",
            visibleRobotSsid = "",
            hasHomeSubnet = true,
            robotWifiPrefix = robotWifiPrefix,
        )
        require(hiddenPlan is ProvisionAttemptPlan.SettleOnHomeWifi)
        assertTrue(hiddenPlan.shouldCheckPanelBeforeWait)

        val visiblePlan = ProvisionCoordinator.planAttempt(
            index = 2,
            totalAttempts = 32,
            settleAttempts = 6,
            currentSsid = "4G-CPE-1884",
            visibleRobotSsid = "GOSHA-A-5B09",
            hasHomeSubnet = true,
            robotWifiPrefix = robotWifiPrefix,
        )
        require(visiblePlan is ProvisionAttemptPlan.SettleOnHomeWifi)
        assertFalse(visiblePlan.shouldCheckPanelBeforeWait)
    }

    @Test
    fun `discovery phase computes display counters and periodic panel checks`() {
        val plan = ProvisionCoordinator.planAttempt(
            index = 17,
            totalAttempts = 32,
            settleAttempts = 6,
            currentSsid = "4G-CPE-1884",
            visibleRobotSsid = "",
            hasHomeSubnet = true,
            robotWifiPrefix = robotWifiPrefix,
        )

        require(plan is ProvisionAttemptPlan.DiscoverOnHomeWifi)
        assertEquals(12, plan.displayAttempt)
        assertEquals(26, plan.displayTotal)
        assertTrue(plan.shouldCheckPanelAfterDiscovery)
    }
}
