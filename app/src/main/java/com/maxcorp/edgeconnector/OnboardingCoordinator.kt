package com.maxcorp.gosha.mobile

internal enum class PermissionRequestPurpose {
    PROVISION,
    RESOLVE_EXISTING,
}

internal enum class PermissionRequestPresentation {
    WIFI_STEP,
    LOADING,
}

internal data class PermissionRequestTransition(
    val presentation: PermissionRequestPresentation,
)

internal enum class WifiInstructionMode {
    KEEP_CURRENT,
    MAIN,
    RECONNECT,
}

internal enum class WifiPresentationMode {
    UPDATE_CURRENT_STEP,
    OPEN_RECONNECT_STEP,
}

internal enum class WifiMessageKind {
    CONTINUE_ON_ROBOT_NETWORK,
    ROBOT_VISIBLE_NEARBY,
    RECONNECT_WAIT_MODE,
    CONNECT_TIMEOUT_RETRY,
}

internal enum class LocalDiagnosticsKind {
    WAIT_ROBOT_WIFI,
    ROBOT_VISIBLE_NEARBY,
    ROBOT_WIFI_HIDDEN,
}

internal enum class PanelDiagnosticsKind {
    SKIPPED_ROBOT_WIFI,
    SKIPPED_ROBOT_VISIBLE,
    SKIPPED_RECONNECT_PENDING,
}

internal data class WifiStepTransition(
    val presentation: WifiPresentationMode,
    val instructionMode: WifiInstructionMode,
    val messageKind: WifiMessageKind,
    val localDiagnosticsKind: LocalDiagnosticsKind,
    val panelDiagnosticsKind: PanelDiagnosticsKind,
    val robotSsid: String = "",
)

internal enum class ConnectedMenuRoute {
    LOCAL_HOST,
    PANEL_ONLY,
}

internal data class ConnectedMenuTransition(
    val route: ConnectedMenuRoute,
    val localHost: String = "",
    val localHostHint: String = "",
)

internal object OnboardingCoordinator {
    fun permissionRequest(purpose: PermissionRequestPurpose): PermissionRequestTransition {
        return PermissionRequestTransition(
            presentation = when (purpose) {
                PermissionRequestPurpose.PROVISION -> PermissionRequestPresentation.WIFI_STEP
                PermissionRequestPurpose.RESOLVE_EXISTING -> PermissionRequestPresentation.LOADING
            }
        )
    }

    fun visibility(
        decision: RobotConnectivityDecision,
        presentation: WifiPresentationMode,
    ): WifiStepTransition? {
        return when (decision.type) {
            RobotConnectivityDecisionType.PHONE_ON_ROBOT_WIFI -> WifiStepTransition(
                presentation = presentation,
                instructionMode = instructionModeFor(presentation),
                messageKind = WifiMessageKind.CONTINUE_ON_ROBOT_NETWORK,
                localDiagnosticsKind = LocalDiagnosticsKind.WAIT_ROBOT_WIFI,
                panelDiagnosticsKind = PanelDiagnosticsKind.SKIPPED_ROBOT_WIFI,
                robotSsid = decision.robotSsid,
            )

            RobotConnectivityDecisionType.ROBOT_VISIBLE_NEARBY -> WifiStepTransition(
                presentation = presentation,
                instructionMode = instructionModeFor(presentation),
                messageKind = WifiMessageKind.ROBOT_VISIBLE_NEARBY,
                localDiagnosticsKind = LocalDiagnosticsKind.ROBOT_VISIBLE_NEARBY,
                panelDiagnosticsKind = PanelDiagnosticsKind.SKIPPED_ROBOT_VISIBLE,
                robotSsid = decision.robotSsid,
            )

            else -> null
        }
    }

    fun menuVisibility(
        decision: RobotConnectivityDecision,
        setupCompleted: Boolean,
    ): WifiStepTransition? {
        if (setupCompleted) {
            return null
        }
        return visibility(
            decision = decision,
            presentation = WifiPresentationMode.OPEN_RECONNECT_STEP,
        )
    }

    fun reconnectWaiting(): WifiStepTransition {
        return WifiStepTransition(
            presentation = WifiPresentationMode.OPEN_RECONNECT_STEP,
            instructionMode = WifiInstructionMode.RECONNECT,
            messageKind = WifiMessageKind.RECONNECT_WAIT_MODE,
            localDiagnosticsKind = LocalDiagnosticsKind.ROBOT_WIFI_HIDDEN,
            panelDiagnosticsKind = PanelDiagnosticsKind.SKIPPED_RECONNECT_PENDING,
        )
    }

    fun connectTimeout(): WifiStepTransition {
        return WifiStepTransition(
            presentation = WifiPresentationMode.OPEN_RECONNECT_STEP,
            instructionMode = WifiInstructionMode.RECONNECT,
            messageKind = WifiMessageKind.CONNECT_TIMEOUT_RETRY,
            localDiagnosticsKind = LocalDiagnosticsKind.ROBOT_WIFI_HIDDEN,
            panelDiagnosticsKind = PanelDiagnosticsKind.SKIPPED_RECONNECT_PENDING,
        )
    }

    fun connectedMenu(decision: RobotConnectivityDecision): ConnectedMenuTransition? {
        return when (decision.type) {
            RobotConnectivityDecisionType.CONNECTED_LOCALLY -> ConnectedMenuTransition(
                route = ConnectedMenuRoute.LOCAL_HOST,
                localHost = decision.localHost,
                localHostHint = decision.localHostHint,
            )

            RobotConnectivityDecisionType.CONNECTED_VIA_PANEL -> ConnectedMenuTransition(
                route = ConnectedMenuRoute.PANEL_ONLY,
                localHostHint = decision.localHostHint,
            )

            else -> null
        }
    }

    private fun instructionModeFor(presentation: WifiPresentationMode): WifiInstructionMode {
        return when (presentation) {
            WifiPresentationMode.UPDATE_CURRENT_STEP -> WifiInstructionMode.KEEP_CURRENT
            WifiPresentationMode.OPEN_RECONNECT_STEP -> WifiInstructionMode.RECONNECT
        }
    }
}
