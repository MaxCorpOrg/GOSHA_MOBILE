package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingCoordinatorTest {
    @Test
    fun `permission request for provision opens wifi step`() {
        val transition = OnboardingCoordinator.permissionRequest(PermissionRequestPurpose.PROVISION)

        assertEquals(PermissionRequestPresentation.WIFI_STEP, transition.presentation)
    }

    @Test
    fun `permission request for existing robot opens loading state`() {
        val transition = OnboardingCoordinator.permissionRequest(PermissionRequestPurpose.RESOLVE_EXISTING)

        assertEquals(PermissionRequestPresentation.LOADING, transition.presentation)
    }

    @Test
    fun `phone on robot wifi opens reconnect step`() {
        val transition = OnboardingCoordinator.visibility(
            decision = RobotConnectivityDecision(
                type = RobotConnectivityDecisionType.PHONE_ON_ROBOT_WIFI,
                robotSsid = "GOSHA-A-5B09",
            ),
            presentation = WifiPresentationMode.OPEN_RECONNECT_STEP,
        )

        requireNotNull(transition)
        assertEquals(WifiInstructionMode.RECONNECT, transition.instructionMode)
        assertEquals(WifiMessageKind.CONTINUE_ON_ROBOT_NETWORK, transition.messageKind)
    }

    @Test
    fun `nearby robot visibility can update current wifi step without changing instruction`() {
        val transition = OnboardingCoordinator.visibility(
            decision = RobotConnectivityDecision(
                type = RobotConnectivityDecisionType.ROBOT_VISIBLE_NEARBY,
                robotSsid = "GOSHA-A-5B09",
            ),
            presentation = WifiPresentationMode.UPDATE_CURRENT_STEP,
        )

        requireNotNull(transition)
        assertEquals(WifiInstructionMode.KEEP_CURRENT, transition.instructionMode)
        assertEquals(WifiMessageKind.ROBOT_VISIBLE_NEARBY, transition.messageKind)
    }

    @Test
    fun `connected local host opens menu with local route`() {
        val transition = OnboardingCoordinator.connectedMenu(
            RobotConnectivityDecision(
                type = RobotConnectivityDecisionType.CONNECTED_LOCALLY,
                localHost = "192.168.0.103",
            )
        )

        requireNotNull(transition)
        assertEquals(ConnectedMenuRoute.LOCAL_HOST, transition.route)
        assertEquals("192.168.0.103", transition.localHost)
    }

    @Test
    fun `connected via panel opens menu without host`() {
        val transition = OnboardingCoordinator.connectedMenu(
            RobotConnectivityDecision(type = RobotConnectivityDecisionType.CONNECTED_VIA_PANEL)
        )

        requireNotNull(transition)
        assertEquals(ConnectedMenuRoute.PANEL_ONLY, transition.route)
    }

    @Test
    fun `unknown decision produces no visibility transition`() {
        val transition = OnboardingCoordinator.visibility(
            decision = RobotConnectivityDecision(type = RobotConnectivityDecisionType.UNKNOWN),
            presentation = WifiPresentationMode.OPEN_RECONNECT_STEP,
        )

        assertNull(transition)
    }
}
