package com.maxcorp.gosha.mobile

internal sealed interface ProvisionAttemptPlan {
    data class WaitOnRobotWifi(
        val showManualSwitchHint: Boolean,
    ) : ProvisionAttemptPlan

    data class WaitForHomeWifi(
        val shouldCheckPanel: Boolean,
    ) : ProvisionAttemptPlan

    data class SettleOnHomeWifi(
        val visibleRobotSsid: String = "",
        val shouldCheckPanelBeforeWait: Boolean,
    ) : ProvisionAttemptPlan

    data class DiscoverOnHomeWifi(
        val displayAttempt: Int,
        val displayTotal: Int,
        val shouldCheckPanelAfterDiscovery: Boolean,
    ) : ProvisionAttemptPlan
}

internal object ProvisionCoordinator {
    private val noSubnetPanelCheckIndices = setOf(5, 11, 17)
    private val earlySettlePanelCheckIndices = setOf(0, 2, 4)
    private val discoveryPanelCheckIndices = setOf(9, 17, 25)

    fun planAttempt(
        index: Int,
        totalAttempts: Int,
        settleAttempts: Int,
        currentSsid: String,
        visibleRobotSsid: String,
        hasHomeSubnet: Boolean,
        robotWifiPrefix: String,
    ): ProvisionAttemptPlan {
        if (RobotBranding.isRobotWifiSsid(currentSsid, robotWifiPrefix)) {
            return ProvisionAttemptPlan.WaitOnRobotWifi(
                showManualSwitchHint = index >= 4
            )
        }

        if (!hasHomeSubnet) {
            return ProvisionAttemptPlan.WaitForHomeWifi(
                shouldCheckPanel = index in noSubnetPanelCheckIndices
            )
        }

        if (index < settleAttempts) {
            return ProvisionAttemptPlan.SettleOnHomeWifi(
                visibleRobotSsid = visibleRobotSsid,
                shouldCheckPanelBeforeWait = visibleRobotSsid.isBlank() && index in earlySettlePanelCheckIndices,
            )
        }

        return ProvisionAttemptPlan.DiscoverOnHomeWifi(
            displayAttempt = index - settleAttempts + 1,
            displayTotal = totalAttempts - settleAttempts,
            shouldCheckPanelAfterDiscovery = index in discoveryPanelCheckIndices,
        )
    }
}
