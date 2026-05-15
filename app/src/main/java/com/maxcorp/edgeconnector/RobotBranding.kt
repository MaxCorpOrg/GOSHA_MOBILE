package com.maxcorp.gosha.mobile

object RobotBranding {
    const val BRAND_NAME = "Гоша"
    const val PRIMARY_WIFI_PREFIX = "GOSHA-"
    private val LEGACY_WIFI_PREFIXES = listOf("Xiaozhi-")

    fun acceptedWifiPrefixes(primaryPrefix: String = PRIMARY_WIFI_PREFIX): List<String> {
        val items = linkedSetOf<String>()
        val normalizedPrimary = primaryPrefix.trim()
        if (normalizedPrimary.isNotBlank()) {
            items += normalizedPrimary
        }
        for (prefix in LEGACY_WIFI_PREFIXES) {
            val normalized = prefix.trim()
            if (normalized.isNotBlank()) {
                items += normalized
            }
        }
        return items.toList()
    }

    fun isRobotWifiSsid(rawSsid: String, primaryPrefix: String = PRIMARY_WIFI_PREFIX): Boolean {
        val normalized = normalizeSsid(rawSsid)
        if (normalized.isBlank()) return false
        return acceptedWifiPrefixes(primaryPrefix).any { normalized.startsWith(it, ignoreCase = true) }
    }

    fun displayWifiHint(primaryPrefix: String = PRIMARY_WIFI_PREFIX): String {
        val normalized = primaryPrefix.trim().ifBlank { PRIMARY_WIFI_PREFIX }
        return "${normalized}..."
    }

    fun normalizeSsid(rawSsid: String): String = rawSsid.trim().removePrefix("\"").removeSuffix("\"")
}
