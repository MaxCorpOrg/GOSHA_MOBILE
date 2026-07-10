package com.maxcorp.gosha.mobile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

private const val PREFS = "gosha_mobile_prefs"

private const val K_HUB_URL = "hub_url"
private const val K_ROBOT_ID = "robot_id"
private const val K_TOKEN = "token"
private const val K_ROBOT_HOST = "robot_host"
private const val K_ROBOT_PORT = "robot_port"
private const val K_ROBOT_PATH = "robot_path"
private const val K_STATUS = "status"
private const val K_STATUS_TS = "status_ts"
private const val K_PANEL_URL = "panel_url"
private const val K_ROBOT_NAME = "robot_name"
private const val K_CLOUD_ENDPOINT = "cloud_endpoint"
private const val K_OWNER_NAME = "owner_name"
private const val K_OWNER_EMAIL = "owner_email"
private const val K_OWNER_PHONE = "owner_phone"
private const val K_CLIENT_COMPANY = "client_company"
private const val K_CLIENT_CONTACT = "client_contact"
private const val K_CLIENT_COMMENT = "client_comment"
private const val K_PLAN_CODE = "plan_code"
private const val K_PLAN_NAME = "plan_name"
private const val K_BILLING_START = "billing_start"
private const val K_BILLING_END = "billing_end"
private const val K_PAYMENT_STATUS = "payment_status"
private const val K_SUBSCRIPTION_NOTE = "subscription_note"
private const val K_ONBOARDING_CODE = "onboarding_code"
private const val K_PANEL_CLIENT_TOKEN = "panel_client_token"
private const val K_WIFI_RECONNECT_PENDING = "wifi_reconnect_pending"
private const val K_SETUP_COMPLETED = "setup_completed"
private const val K_MOBILE_BRAND = "mobile_brand"
private const val K_PORTAL_URL = "portal_url"
private const val K_MOBILE_WEBSOCKET_URL = "mobile_websocket_url"
private const val K_PREFERRED_BACKEND_MODE = "preferred_backend_mode"
private const val K_ROBOT_WIFI_PREFIXES = "robot_wifi_prefixes"
private const val K_CONNECTOR_HUB_URL = "connector_hub_url"
private const val K_CONNECTOR_ROBOT_ID = "connector_robot_id"
private const val K_CONNECTOR_TOKEN = "connector_token"
private const val K_CONNECTOR_ROBOT_HOST = "connector_robot_host"
private const val K_CONNECTOR_ROBOT_PORT = "connector_robot_port"
private const val K_CONNECTOR_ROBOT_PATH = "connector_robot_path"
private const val K_BACKGROUND_ACCESS_GUIDANCE_VERSION = "background_access_guidance_version"
private const val K_NOTIFICATION_PERMISSION_PROMPT_VERSION = "notification_permission_prompt_version"

private const val CLIENT_NAME = "android-app"
private const val CLIENT_VERSION = "0.1.0"

private fun sanitizeRobotHost(rawHost: String): String {
    val host = rawHost.trim()
    val normalized = host.lowercase()
    if (normalized.isBlank()) return ""
    if (normalized == "localhost" || normalized == "0.0.0.0" || normalized.startsWith("127.")) return ""
    return host
}

data class CloudEndpointParts(
    val hubBaseUrl: String = "",
    val token: String = "",
    val robotId: String = "",
)

internal fun parseCloudEndpoint(rawUrl: String): CloudEndpointParts {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return CloudEndpointParts()

    return try {
        val uri = URI(trimmed)
        val hubBaseUrl = URI(
            uri.scheme,
            uri.userInfo,
            uri.host,
            uri.port,
            uri.path,
            null,
            null,
        ).toString()
        val query = uri.rawQuery.orEmpty()
        val params = query
            .split('&')
            .mapNotNull { item ->
                if (item.isBlank()) {
                    null
                } else {
                    val key = item.substringBefore('=')
                    val value = item.substringAfter('=', "")
                    URLDecoder.decode(key, Charsets.UTF_8.name()) to
                        URLDecoder.decode(value, Charsets.UTF_8.name())
                }
            }
            .toMap()
        CloudEndpointParts(
            hubBaseUrl = hubBaseUrl.ifBlank { trimmed.substringBefore('?') },
            token = params["token"].orEmpty(),
            robotId = params["robot_id"].orEmpty(),
        )
    } catch (_: Exception) {
        CloudEndpointParts(hubBaseUrl = trimmed.substringBefore('?'))
    }
}

data class ConnectorConfig(
    val hubBaseUrl: String,
    val robotId: String,
    val token: String,
    val robotHost: String,
    val robotPort: Int,
    val robotPath: String,
) {
    fun agentUrl(): String {
        val base = URI(hubBaseUrl.trim())
        val pathPrefix = (base.path ?: "").trimEnd('/')
        val path = "$pathPrefix/agent/${encodePath(robotId)}"
        val query = "token=${enc(token)}&client=${enc(CLIENT_NAME)}&version=${enc(CLIENT_VERSION)}"
        return URI(base.scheme, base.userInfo, base.host, base.port, normalizePath(path), query, null).toString()
    }

    fun robotWsUrl(): String {
        val normalizedPath = normalizePath(if (robotPath.isBlank()) "/ws" else robotPath)
        return URI("ws", null, robotHost.trim(), robotPort, normalizedPath, null, null).toString()
    }

    fun toIntent(intent: Intent): Intent {
        return intent
            .putExtra(ConnectorForegroundService.EXTRA_HUB_URL, hubBaseUrl)
            .putExtra(ConnectorForegroundService.EXTRA_ROBOT_ID, robotId)
            .putExtra(ConnectorForegroundService.EXTRA_TOKEN, token)
            .putExtra(ConnectorForegroundService.EXTRA_ROBOT_HOST, robotHost)
            .putExtra(ConnectorForegroundService.EXTRA_ROBOT_PORT, robotPort)
            .putExtra(ConnectorForegroundService.EXTRA_ROBOT_PATH, robotPath)
    }

    fun toDebugJson(): String {
        val json = JSONObject()
            .put("hub", hubBaseUrl)
            .put("robot_id", robotId)
            .put("robot_host", robotHost)
            .put("robot_port", robotPort)
            .put("robot_path", robotPath)
        return json.toString(2)
    }

    companion object {
        fun fromIntent(intent: Intent): ConnectorConfig? {
            val hubUrl = intent.getStringExtra(ConnectorForegroundService.EXTRA_HUB_URL).orEmpty()
            val robotId = intent.getStringExtra(ConnectorForegroundService.EXTRA_ROBOT_ID).orEmpty()
            val token = intent.getStringExtra(ConnectorForegroundService.EXTRA_TOKEN).orEmpty()
            val robotHost = sanitizeRobotHost(intent.getStringExtra(ConnectorForegroundService.EXTRA_ROBOT_HOST).orEmpty())
            val robotPort = intent.getIntExtra(ConnectorForegroundService.EXTRA_ROBOT_PORT, 8080)
            val robotPath = intent.getStringExtra(ConnectorForegroundService.EXTRA_ROBOT_PATH).orEmpty()
            if (hubUrl.isBlank() || robotId.isBlank() || token.isBlank() || robotHost.isBlank()) {
                return null
            }
            return ConnectorConfig(
                hubBaseUrl = hubUrl,
                robotId = robotId,
                token = token,
                robotHost = robotHost,
                robotPort = robotPort,
                robotPath = if (robotPath.isBlank()) "/ws" else robotPath,
            )
        }

        fun normalizePath(path: String): String {
            val p = path.trim()
            return if (p.startsWith("/")) p else "/$p"
        }

        private fun enc(v: String): String = URLEncoder.encode(v, Charsets.UTF_8.name())

        private fun encodePath(v: String): String = v.replace(" ", "-")
    }
}

class ConfigStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadConfig(): ConnectorConfig? {
        val hub = prefs.getString(K_CONNECTOR_HUB_URL, "")?.ifBlank {
            prefs.getString(K_HUB_URL, "") ?: ""
        } ?: ""
        val robotId = prefs.getString(K_CONNECTOR_ROBOT_ID, "")?.ifBlank {
            prefs.getString(K_ROBOT_ID, "") ?: ""
        } ?: ""
        val token = prefs.getString(K_CONNECTOR_TOKEN, "")?.ifBlank {
            prefs.getString(K_TOKEN, "") ?: ""
        } ?: ""
        val host = sanitizeRobotHost(
            prefs.getString(K_CONNECTOR_ROBOT_HOST, "")?.ifBlank {
                prefs.getString(K_ROBOT_HOST, "") ?: ""
            } ?: ""
        )
        val port = prefs.getInt(
            K_CONNECTOR_ROBOT_PORT,
            prefs.getInt(K_ROBOT_PORT, 8080),
        )
        val path = prefs.getString(K_CONNECTOR_ROBOT_PATH, "")?.ifBlank {
            prefs.getString(K_ROBOT_PATH, "/ws") ?: "/ws"
        } ?: "/ws"
        if (hub.isBlank() || robotId.isBlank() || token.isBlank() || host.isBlank()) {
            return null
        }
        return ConnectorConfig(
            hubBaseUrl = hub,
            robotId = robotId,
            token = token,
            robotHost = host,
            robotPort = port,
            robotPath = path,
        )
    }

    fun saveConfig(config: ConnectorConfig) {
        prefs.edit()
            .putString(K_CONNECTOR_HUB_URL, config.hubBaseUrl)
            .putString(K_CONNECTOR_ROBOT_ID, config.robotId)
            .putString(K_CONNECTOR_TOKEN, config.token)
            .putString(K_CONNECTOR_ROBOT_HOST, sanitizeRobotHost(config.robotHost))
            .putInt(K_CONNECTOR_ROBOT_PORT, config.robotPort)
            .putString(K_CONNECTOR_ROBOT_PATH, config.robotPath)
            .apply()
    }

    fun clearConfig() {
        prefs.edit()
            .remove(K_CONNECTOR_HUB_URL)
            .remove(K_CONNECTOR_ROBOT_ID)
            .remove(K_CONNECTOR_TOKEN)
            .remove(K_CONNECTOR_ROBOT_HOST)
            .remove(K_CONNECTOR_ROBOT_PORT)
            .remove(K_CONNECTOR_ROBOT_PATH)
            .apply()
    }

    fun saveStatus(status: String) {
        prefs.edit()
            .putString(K_STATUS, status)
            .putLong(K_STATUS_TS, System.currentTimeMillis())
            .apply()
    }

    fun loadStatus(): Pair<String, Long> {
        val status = prefs.getString(K_STATUS, "idle") ?: "idle"
        val ts = prefs.getLong(K_STATUS_TS, 0L)
        return status to ts
    }

    fun backgroundAccessGuidanceVersion(): Int =
        prefs.getInt(K_BACKGROUND_ACCESS_GUIDANCE_VERSION, 0)

    fun markBackgroundAccessGuidanceShown(version: Int) {
        prefs.edit()
            .putInt(K_BACKGROUND_ACCESS_GUIDANCE_VERSION, version)
            .apply()
    }

    fun notificationPermissionPromptVersion(): Int =
        prefs.getInt(K_NOTIFICATION_PERMISSION_PROMPT_VERSION, 0)

    fun markNotificationPermissionPromptShown(version: Int) {
        prefs.edit()
            .putInt(K_NOTIFICATION_PERMISSION_PROMPT_VERSION, version)
            .apply()
    }

    fun loadDraft(): OnboardingDraft {
        return OnboardingDraft(
            panelBaseUrl = prefs.getString(K_PANEL_URL, "http://151.241.228.232:18876") ?: "http://151.241.228.232:18876",
            hubBaseUrl = prefs.getString(K_HUB_URL, "ws://151.241.228.232:18080/mcp") ?: "ws://151.241.228.232:18080/mcp",
            robotId = prefs.getString(K_ROBOT_ID, "") ?: "",
            robotName = prefs.getString(K_ROBOT_NAME, "") ?: "",
            token = prefs.getString(K_TOKEN, "") ?: "",
            robotHost = sanitizeRobotHost(prefs.getString(K_ROBOT_HOST, "") ?: ""),
            robotPort = prefs.getInt(K_ROBOT_PORT, 8080),
            robotPath = prefs.getString(K_ROBOT_PATH, "/ws") ?: "/ws",
            cloudEndpoint = prefs.getString(K_CLOUD_ENDPOINT, "") ?: "",
            ownerName = prefs.getString(K_OWNER_NAME, "") ?: "",
            ownerEmail = prefs.getString(K_OWNER_EMAIL, "") ?: "",
            ownerPhone = prefs.getString(K_OWNER_PHONE, "") ?: "",
            clientCompany = prefs.getString(K_CLIENT_COMPANY, "") ?: "",
            clientContact = prefs.getString(K_CLIENT_CONTACT, "") ?: "",
            clientComment = prefs.getString(K_CLIENT_COMMENT, "") ?: "",
            planCode = prefs.getString(K_PLAN_CODE, "start") ?: "start",
            planName = prefs.getString(K_PLAN_NAME, "") ?: "",
            billingStart = prefs.getString(K_BILLING_START, "") ?: "",
            billingEnd = prefs.getString(K_BILLING_END, "") ?: "",
            paymentStatus = prefs.getString(K_PAYMENT_STATUS, "trial") ?: "trial",
            subscriptionNote = prefs.getString(K_SUBSCRIPTION_NOTE, "") ?: "",
            onboardingCode = prefs.getString(K_ONBOARDING_CODE, "") ?: "",
            panelClientToken = prefs.getString(K_PANEL_CLIENT_TOKEN, "") ?: "",
            wifiReconnectPending = prefs.getBoolean(K_WIFI_RECONNECT_PENDING, false),
            setupCompleted = prefs.getBoolean(K_SETUP_COMPLETED, false),
            mobileBrand = prefs.getString(K_MOBILE_BRAND, "GOSHA") ?: "GOSHA",
            portalUrl = prefs.getString(K_PORTAL_URL, "http://192.168.4.1") ?: "http://192.168.4.1",
            mobileWebsocketUrl = prefs.getString(K_MOBILE_WEBSOCKET_URL, "") ?: "",
            preferredBackendMode = prefs.getString(K_PREFERRED_BACKEND_MODE, "") ?: "",
            robotWifiPrefixesCsv = prefs.getString(K_ROBOT_WIFI_PREFIXES, "GOSHA-,Xiaozhi-") ?: "GOSHA-,Xiaozhi-",
        )
    }

    fun saveDraft(draft: OnboardingDraft) {
        prefs.edit()
            .putString(K_PANEL_URL, draft.panelBaseUrl)
            .putString(K_HUB_URL, draft.hubBaseUrl)
            .putString(K_ROBOT_ID, draft.robotId)
            .putString(K_ROBOT_NAME, draft.robotName)
            .putString(K_TOKEN, draft.token)
            .putString(K_ROBOT_HOST, sanitizeRobotHost(draft.robotHost))
            .putInt(K_ROBOT_PORT, draft.robotPort)
            .putString(K_ROBOT_PATH, draft.robotPath)
            .putString(K_CLOUD_ENDPOINT, draft.cloudEndpoint)
            .putString(K_OWNER_NAME, draft.ownerName)
            .putString(K_OWNER_EMAIL, draft.ownerEmail)
            .putString(K_OWNER_PHONE, draft.ownerPhone)
            .putString(K_CLIENT_COMPANY, draft.clientCompany)
            .putString(K_CLIENT_CONTACT, draft.clientContact)
            .putString(K_CLIENT_COMMENT, draft.clientComment)
            .putString(K_PLAN_CODE, draft.planCode)
            .putString(K_PLAN_NAME, draft.planName)
            .putString(K_BILLING_START, draft.billingStart)
            .putString(K_BILLING_END, draft.billingEnd)
            .putString(K_PAYMENT_STATUS, draft.paymentStatus)
            .putString(K_SUBSCRIPTION_NOTE, draft.subscriptionNote)
            .putString(K_ONBOARDING_CODE, draft.onboardingCode)
            .putString(K_PANEL_CLIENT_TOKEN, draft.panelClientToken)
            .putBoolean(K_WIFI_RECONNECT_PENDING, draft.wifiReconnectPending)
            .putBoolean(K_SETUP_COMPLETED, draft.setupCompleted)
            .putString(K_MOBILE_BRAND, draft.mobileBrand)
            .putString(K_PORTAL_URL, draft.portalUrl)
            .putString(K_MOBILE_WEBSOCKET_URL, draft.mobileWebsocketUrl)
            .putString(K_PREFERRED_BACKEND_MODE, draft.preferredBackendMode)
            .putString(K_ROBOT_WIFI_PREFIXES, draft.robotWifiPrefixesCsv)
            .apply()
    }

    fun resetForNextRobot() {
        val current = loadDraft()
        saveDraft(
            OnboardingDraft(
                panelBaseUrl = current.panelBaseUrl,
                hubBaseUrl = current.hubBaseUrl,
            )
        )
        clearConfig()
        saveStatus("idle")
    }
}

data class OnboardingDraft(
    val panelBaseUrl: String = "http://151.241.228.232:18876",
    val hubBaseUrl: String = "ws://151.241.228.232:18080/mcp",
    val robotId: String = "",
    val robotName: String = "",
    val token: String = "",
    val robotHost: String = "",
    val robotPort: Int = 8080,
    val robotPath: String = "/ws",
    val cloudEndpoint: String = "",
    val ownerName: String = "",
    val ownerEmail: String = "",
    val ownerPhone: String = "",
    val clientCompany: String = "",
    val clientContact: String = "",
    val clientComment: String = "",
    val planCode: String = "start",
    val planName: String = "",
    val billingStart: String = "",
    val billingEnd: String = "",
    val paymentStatus: String = "trial",
    val subscriptionNote: String = "",
    val onboardingCode: String = "",
    val panelClientToken: String = "",
    val wifiReconnectPending: Boolean = false,
    val setupCompleted: Boolean = false,
    val mobileBrand: String = "GOSHA",
    val portalUrl: String = "http://192.168.4.1",
    val mobileWebsocketUrl: String = "",
    val preferredBackendMode: String = "",
    val robotWifiPrefixesCsv: String = "GOSHA-,Xiaozhi-",
) {
    fun toConnectorConfigOrNull(): ConnectorConfig? {
        if (hubBaseUrl.isBlank() || robotId.isBlank() || token.isBlank() || robotHost.isBlank()) {
            return null
        }
        return ConnectorConfig(
            hubBaseUrl = hubBaseUrl,
            robotId = robotId,
            token = token,
            robotHost = robotHost,
            robotPort = robotPort,
            robotPath = if (robotPath.isBlank()) "/ws" else robotPath,
        )
    }
}
