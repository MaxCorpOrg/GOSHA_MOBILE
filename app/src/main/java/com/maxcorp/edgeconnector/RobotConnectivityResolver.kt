package com.maxcorp.gosha.mobile

internal enum class RobotConnectivityDecisionType {
    PHONE_ON_ROBOT_WIFI,
    ROBOT_VISIBLE_NEARBY,
    CONNECTED_LOCALLY,
    CONNECTED_VIA_PANEL,
    UNKNOWN,
}

internal data class RobotConnectivityDecision(
    val type: RobotConnectivityDecisionType,
    val robotSsid: String = "",
    val localHost: String = "",
)

internal object RobotConnectivityResolver {
    fun visibleRobotSsid(
        currentSsid: String,
        nearbyRobotSsid: String,
        robotWifiPrefix: String,
    ): String {
        val normalizedCurrent = normalizeSsid(currentSsid)
        if (RobotBranding.isRobotWifiSsid(normalizedCurrent, robotWifiPrefix)) {
            return normalizedCurrent
        }

        val normalizedNearby = normalizeSsid(nearbyRobotSsid)
        if (RobotBranding.isRobotWifiSsid(normalizedNearby, robotWifiPrefix)) {
            return normalizedNearby
        }

        return ""
    }

    fun resolve(
        currentSsid: String = "",
        nearbyRobotSsid: String = "",
        panelSnapshot: RobotRuntimeSnapshot? = null,
        robotWifiPrefix: String,
    ): RobotConnectivityDecision {
        val normalizedCurrent = normalizeSsid(currentSsid)
        if (RobotBranding.isRobotWifiSsid(normalizedCurrent, robotWifiPrefix)) {
            return RobotConnectivityDecision(
                type = RobotConnectivityDecisionType.PHONE_ON_ROBOT_WIFI,
                robotSsid = normalizedCurrent,
            )
        }

        val normalizedNearby = normalizeSsid(nearbyRobotSsid)
        if (RobotBranding.isRobotWifiSsid(normalizedNearby, robotWifiPrefix)) {
            return RobotConnectivityDecision(
                type = RobotConnectivityDecisionType.ROBOT_VISIBLE_NEARBY,
                robotSsid = normalizedNearby,
            )
        }

        if (panelSnapshot?.connected == true) {
            val localHost = panelSnapshot.localHost.trim()
            if (localHost.isNotBlank()) {
                return RobotConnectivityDecision(
                    type = RobotConnectivityDecisionType.CONNECTED_LOCALLY,
                    localHost = localHost,
                )
            }
            return RobotConnectivityDecision(type = RobotConnectivityDecisionType.CONNECTED_VIA_PANEL)
        }

        return RobotConnectivityDecision(type = RobotConnectivityDecisionType.UNKNOWN)
    }

    private fun normalizeSsid(raw: String): String = raw.trim().removePrefix("\"").removeSuffix("\"")
}
