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

internal sealed interface ProvisionPanelSignalPlan {
    data class CompleteWithLocalHost(
        val localHost: String,
    ) : ProvisionPanelSignalPlan

    data class ContinueLocalDiscovery(
        val localHostHint: String,
    ) : ProvisionPanelSignalPlan

    object NoPanelSignal : ProvisionPanelSignalPlan
}

internal object ProvisionCoordinator {
    private val noSubnetPanelCheckIndices = setOf(5, 11, 17)
    private val earlySettlePanelCheckIndices = setOf(0, 2, 4)
    private val discoveryPanelCheckIndices = setOf(9, 17, 25)

    fun shouldStartReturnCheck(
        awaitingRobotProvision: Boolean,
        robotWifiPortalActive: Boolean,
        returnCheckRunning: Boolean,
    ): Boolean {
        return awaitingRobotProvision && !robotWifiPortalActive && !returnCheckRunning
    }

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

    fun planPanelSignal(decision: RobotConnectivityDecision): ProvisionPanelSignalPlan {
        return when (decision.type) {
            RobotConnectivityDecisionType.CONNECTED_LOCALLY -> {
                val localHost = decision.localHost.trim()
                if (localHost.isNotBlank()) {
                    ProvisionPanelSignalPlan.CompleteWithLocalHost(localHost)
                } else {
                    ProvisionPanelSignalPlan.NoPanelSignal
                }
            }

            RobotConnectivityDecisionType.CONNECTED_VIA_PANEL ->
                ProvisionPanelSignalPlan.ContinueLocalDiscovery(decision.localHostHint.trim())

            else -> ProvisionPanelSignalPlan.NoPanelSignal
        }
    }
}
