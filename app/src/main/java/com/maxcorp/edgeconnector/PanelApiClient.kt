package com.maxcorp.gosha.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

data class PlanOption(
    val code: String,
    val name: String,
    val description: String,
    val services: Map<String, Boolean>,
    val limitsClients: Int,
    val limitsMemoryMb: Int,
    val limitsOperators: Int,
) {
    override fun toString(): String = "$name ($code)"
}

data class RobotUser(
    val userId: String,
    val name: String,
    val contact: String,
    val role: String,
)

data class RobotRuntimeSnapshot(
    val robotId: String,
    val connected: Boolean,
    val mode: String,
    val transportState: String,
    val target: String,
    val localHost: String,
    val connectivityEvidence: String,
    val verifiedNow: Boolean,
    val freshDeviceContact: Boolean,
    val lastSeenIso: String,
    val boardName: String,
    val appVersion: String,
)

data class SelfhostXiaozhiBundle(
    val provider: String,
    val otaUrl: String,
    val activateUrl: String,
    val websocketUrl: String,
    val mcpEndpointBase: String,
)

data class MobileProfile(
    val brand: String,
    val panelUrl: String,
    val mcpEndpointBase: String,
    val websocketUrl: String,
    val portalUrl: String,
    val robotWifiPrefixes: List<String>,
    val preferredBackendMode: String,
)

data class OnboardingBundle(
    val code: String,
    val panelUrl: String,
    val panelClientToken: String,
    val robotId: String,
    val robotName: String,
    val cloudEndpoint: String,
    val planCode: String,
    val planName: String,
    val billingStart: String,
    val billingEnd: String,
    val paymentStatus: String,
    val ownerName: String,
    val ownerEmail: String,
    val ownerPhone: String,
    val ownerCompany: String,
    val ownerContact: String,
    val ownerComment: String,
    val instruction: String,
    val users: List<RobotUser>,
    val selfhostXiaozhi: SelfhostXiaozhiBundle?,
    val mobileProfile: MobileProfile?,
)

object PanelApiClient {
    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.contains("://")) trimmed.trimEnd('/') else "http://${trimmed.trimEnd('/')}"
    }

    fun defaultPlans(): List<PlanOption> = listOf(
        PlanOption(
            code = "start",
            name = "Старт",
            description = "База знаний и память клиента.",
            services = mapOf("knowledge" to true, "memory" to true, "telegram" to false, "email" to false, "music" to false, "call" to false),
            limitsClients = 100,
            limitsMemoryMb = 256,
            limitsOperators = 1,
        ),
        PlanOption(
            code = "business",
            name = "Бизнес",
            description = "База знаний, память, Телеграм и почта.",
            services = mapOf("knowledge" to true, "memory" to true, "telegram" to true, "email" to true, "music" to false, "call" to false),
            limitsClients = 1000,
            limitsMemoryMb = 1024,
            limitsOperators = 3,
        ),
        PlanOption(
            code = "max",
            name = "MAX",
            description = "Все сервисы и расширенные лимиты.",
            services = mapOf("knowledge" to true, "memory" to true, "telegram" to true, "email" to true, "music" to true, "call" to true),
            limitsClients = 5000,
            limitsMemoryMb = 4096,
            limitsOperators = 10,
        ),
        PlanOption(
            code = "custom",
            name = "Индивидуальный",
            description = "Ручная настройка сервиса и лимитов.",
            services = mapOf("knowledge" to true, "memory" to true, "telegram" to false, "email" to false, "music" to false, "call" to false),
            limitsClients = 100,
            limitsMemoryMb = 256,
            limitsOperators = 1,
        ),
    )

    suspend fun resolveCode(http: OkHttpClient, baseUrl: String, code: String): OnboardingBundle = withContext(Dispatchers.IO) {
        val root = requestJson(
            http,
            normalizeBaseUrl(baseUrl) + "/api/mobile/resolve-code",
            "POST",
            JSONObject().put("code", code),
        )
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("error", "Не удалось обработать код"))
        }
        val bundle = root.optJSONObject("bundle") ?: JSONObject()
        bundle.toOnboardingBundle()
    }

    suspend fun activateCode(
        http: OkHttpClient,
        baseUrl: String,
        code: String,
        ownerName: String,
        ownerEmail: String,
        ownerPhone: String,
    ): OnboardingBundle = withContext(Dispatchers.IO) {
        val root = requestJson(
            http,
            normalizeBaseUrl(baseUrl) + "/api/mobile/activate-code",
            "POST",
            JSONObject()
                .put("code", code)
                .put(
                    "owner",
                    JSONObject()
                        .put("name", ownerName)
                        .put("email", ownerEmail)
                        .put("phone", ownerPhone)
                ),
        )
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("error", "Не удалось активировать код"))
        }
        val bundle = root.optJSONObject("bundle") ?: JSONObject()
        bundle.toOnboardingBundle()
    }

    suspend fun fetchPlans(http: OkHttpClient, baseUrl: String): List<PlanOption> = withContext(Dispatchers.IO) {
        val url = normalizeBaseUrl(baseUrl) + "/api/mobile/plans"
        val root = requestJson(http, url)
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("error", "Не удалось загрузить тарифы"))
        }
        val plans = root.optJSONArray("plans") ?: JSONArray()
        buildList {
            for (i in 0 until plans.length()) {
                val item = plans.optJSONObject(i) ?: continue
                add(item.toPlanOption())
            }
        }
    }

    suspend fun createRobot(http: OkHttpClient, baseUrl: String, draft: OnboardingDraft, selectedPlan: PlanOption) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("robot_id", draft.robotId)
            .put("robot_name", draft.robotName)
            .put("plan_code", selectedPlan.code)
            .put("endpoint", draft.cloudEndpoint)
            .put(
                "owner",
                JSONObject()
                    .put("company", draft.clientCompany)
                    .put("contact", draft.clientContact)
                    .put("comment", draft.clientComment)
            )
        val root = requestJson(http, normalizeBaseUrl(baseUrl) + "/api/operator/robots/create", "POST", body)
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("error", "Не удалось зарегистрировать робота"))
        }
        root
    }

    suspend fun fetchOwner(
        http: OkHttpClient,
        baseUrl: String,
        robotId: String,
        panelClientToken: String = "",
        onboardingCode: String = "",
    ): JSONObject = withContext(Dispatchers.IO) {
        val root = requestJson(
            http,
            normalizeBaseUrl(baseUrl) + "/api/mobile/robots/${robotId}/owner",
            headers = mobileHeaders(panelClientToken, onboardingCode),
        )
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("error", "Не удалось загрузить владельца"))
        }
        root.optJSONObject("data")?.optJSONObject("owner") ?: JSONObject()
    }

    suspend fun updateOwner(http: OkHttpClient, baseUrl: String, robotId: String, draft: OnboardingDraft): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", draft.ownerName)
            .put("email", draft.ownerEmail)
            .put("phone", draft.ownerPhone)
            .put("company", draft.clientCompany)
            .put("contact", draft.clientContact)
            .put("comment", draft.clientComment)
        val root = requestJson(
            http,
            normalizeBaseUrl(baseUrl) + "/api/mobile/robots/${robotId}/owner",
            "POST",
            body,
            mobileHeaders(draft.panelClientToken, draft.onboardingCode),
        )
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("error", "Не удалось сохранить владельца"))
        }
        root.optJSONObject("owner") ?: JSONObject()
    }

    suspend fun fetchSubscription(
        http: OkHttpClient,
        baseUrl: String,
        robotId: String,
        panelClientToken: String = "",
        onboardingCode: String = "",
    ): JSONObject = withContext(Dispatchers.IO) {
        val root = requestJson(
            http,
            normalizeBaseUrl(baseUrl) + "/api/mobile/robots/${robotId}/subscription",
            headers = mobileHeaders(panelClientToken, onboardingCode),
        )
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("error", "Не удалось загрузить подписку"))
        }
        root.optJSONObject("data")?.optJSONObject("subscription") ?: JSONObject()
    }

    suspend fun updateSubscription(
        http: OkHttpClient,
        baseUrl: String,
        robotId: String,
        draft: OnboardingDraft,
        selectedPlan: PlanOption,
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("plan_code", selectedPlan.code)
            .put("plan_name", selectedPlan.name)
            .put(
                "limits",
                JSONObject()
                    .put("clients", selectedPlan.limitsClients)
                    .put("memory_mb", selectedPlan.limitsMemoryMb)
                    .put("operators", selectedPlan.limitsOperators)
            )
            .put(
                "billing",
                JSONObject()
                    .put("start_date", draft.billingStart)
                    .put("end_date", draft.billingEnd)
                    .put("payment_status", draft.paymentStatus)
            )
            .put("notes", draft.subscriptionNote)
            .put(
                "services",
                JSONObject()
                    .put("knowledge", selectedPlan.services["knowledge"] == true)
                    .put("memory", selectedPlan.services["memory"] == true)
                    .put("telegram", selectedPlan.services["telegram"] == true)
                    .put("email", selectedPlan.services["email"] == true)
                    .put("music", selectedPlan.services["music"] == true)
                    .put("call", selectedPlan.services["call"] == true)
            )
        val root = requestJson(
            http,
            normalizeBaseUrl(baseUrl) + "/api/mobile/robots/${robotId}/subscription",
            "POST",
            body,
            mobileHeaders(draft.panelClientToken, draft.onboardingCode),
        )
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("error", "Не удалось сохранить подписку"))
        }
        root.optJSONObject("subscription") ?: JSONObject()
    }

    suspend fun fetchUsers(
        http: OkHttpClient,
        baseUrl: String,
        robotId: String,
        panelClientToken: String = "",
        onboardingCode: String = "",
    ): List<RobotUser> = withContext(Dispatchers.IO) {
        val root = requestJson(
            http,
            normalizeBaseUrl(baseUrl) + "/api/mobile/robots/${robotId}/users",
            headers = mobileHeaders(panelClientToken, onboardingCode),
        )
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("error", "Не удалось загрузить пользователей"))
        }
        val users = root.optJSONObject("data")?.optJSONArray("users") ?: JSONArray()
        users.toUsers()
    }

    suspend fun addUser(
        http: OkHttpClient,
        baseUrl: String,
        robotId: String,
        name: String,
        contact: String,
        panelClientToken: String = "",
        onboardingCode: String = "",
    ): List<RobotUser> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name)
            .put("contact", contact)
        val root = requestJson(
            http,
            normalizeBaseUrl(baseUrl) + "/api/mobile/robots/${robotId}/users",
            "POST",
            body,
            mobileHeaders(panelClientToken, onboardingCode),
        )
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("error", "Не удалось добавить пользователя"))
        }
        val users = root.optJSONArray("users") ?: JSONArray()
        users.toUsers()
    }

    suspend fun deleteUser(
        http: OkHttpClient,
        baseUrl: String,
        robotId: String,
        userId: String,
        panelClientToken: String = "",
        onboardingCode: String = "",
    ): List<RobotUser> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("user_id", userId)
        val root = requestJson(
            http,
            normalizeBaseUrl(baseUrl) + "/api/mobile/robots/${robotId}/users/delete",
            "POST",
            body,
            mobileHeaders(panelClientToken, onboardingCode),
        )
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("error", "Не удалось удалить пользователя"))
        }
        val users = root.optJSONArray("users") ?: JSONArray()
        users.toUsers()
    }

    suspend fun fetchRobotRuntime(
        http: OkHttpClient,
        baseUrl: String,
        robotId: String,
        panelClientToken: String = "",
        onboardingCode: String = "",
    ): RobotRuntimeSnapshot? = withContext(Dispatchers.IO) {
        val root = requestJson(
            http,
            normalizeBaseUrl(baseUrl) + "/api/mobile/robots/${robotId}/runtime",
            headers = mobileHeaders(panelClientToken, onboardingCode),
        )
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("error", "Не удалось загрузить состояние роботов"))
        }
        val item = root.optJSONObject("data")
            ?: root.optJSONArray("robots")?.let { robots ->
                (0 until robots.length())
                    .asSequence()
                    .mapNotNull { robots.optJSONObject(it) }
                    .firstOrNull { it.optString("robot_id", "") == robotId }
            }
            ?: return@withContext null
        parseRobotRuntimeSnapshot(item, robotId)
    }

    internal fun parseRobotRuntimeSnapshot(item: JSONObject, robotId: String): RobotRuntimeSnapshot {
        val diagnostics = item.optJSONObject("diagnostics") ?: JSONObject()
        val control = item.optJSONObject("control") ?: JSONObject()
        val cloudConsole = item.optJSONObject("cloud_console") ?: JSONObject()
        val connectivity = item.optJSONObject("connectivity") ?: JSONObject()
        val detection = item.optJSONObject("detection") ?: JSONObject()
        return buildRobotRuntimeSnapshot(
            robotId = robotId,
            diagnosticsTarget = diagnostics.optString("target", ""),
            fallbackWsUrl = control.optString("fallback_ws_url", ""),
            diagnosticsMode = diagnostics.optString("mode", ""),
            controlTransport = control.optString("transport", ""),
            transportState = diagnostics.optString("transport_state", ""),
            connectivityHasConnected = connectivity.has("connected"),
            connectivityConnected = connectivity.optBoolean("connected", false),
            connectivityLocalHost = connectivity.optString("local_host", ""),
            connectivityEvidence = connectivity.optString("evidence", ""),
            connectivityVerifiedNow = connectivity.optBoolean("verified_now", detection.optBoolean("verified_now", false)),
            connectivityFreshDeviceContact = connectivity.optBoolean("fresh_device_contact", false),
            connectivityLastSeenIso = connectivity.optString("last_seen_iso", ""),
            connectivityBoardName = connectivity.optString("board_name", ""),
            connectivityAppVersion = connectivity.optString("app_version", ""),
            cloudLastSeenIso = cloudConsole.optString("last_seen_iso", ""),
            cloudBoardName = cloudConsole.optString("board_name", ""),
            cloudAppVersion = cloudConsole.optString("app_version", ""),
        )
    }

    internal fun buildRobotRuntimeSnapshot(
        robotId: String,
        diagnosticsTarget: String,
        fallbackWsUrl: String,
        diagnosticsMode: String,
        controlTransport: String,
        transportState: String,
        connectivityHasConnected: Boolean,
        connectivityConnected: Boolean,
        connectivityLocalHost: String,
        connectivityEvidence: String,
        connectivityVerifiedNow: Boolean,
        connectivityFreshDeviceContact: Boolean,
        connectivityLastSeenIso: String,
        connectivityBoardName: String,
        connectivityAppVersion: String,
        cloudLastSeenIso: String,
        cloudBoardName: String,
        cloudAppVersion: String,
    ): RobotRuntimeSnapshot {
        val target = diagnosticsTarget.ifBlank { fallbackWsUrl }
        val mode = diagnosticsMode.ifBlank { controlTransport }
        val directLocalHost = parseLocalHost(target)
        val normalizedConnectivityLocalHost = directRobotHostOrBlank(connectivityLocalHost)
        val localHost = normalizedConnectivityLocalHost.ifBlank { directLocalHost }
        val panelConnected = when {
            localHost.isNotBlank() -> true
            connectivityHasConnected -> connectivityConnected
            mode == "cloud-mcp" && transportState == "reachable" -> true
            else -> false
        }
        return RobotRuntimeSnapshot(
            robotId = robotId,
            connected = panelConnected,
            mode = mode,
            transportState = transportState,
            target = target,
            localHost = localHost,
            connectivityEvidence = connectivityEvidence,
            verifiedNow = connectivityVerifiedNow,
            freshDeviceContact = connectivityFreshDeviceContact,
            lastSeenIso = connectivityLastSeenIso.ifBlank { cloudLastSeenIso },
            boardName = connectivityBoardName.ifBlank { cloudBoardName },
            appVersion = connectivityAppVersion.ifBlank { cloudAppVersion },
        )
    }

    private fun JSONObject.toPlanOption(): PlanOption {
        val servicesObj = optJSONObject("services") ?: JSONObject()
        val limitsObj = optJSONObject("limits") ?: JSONObject()
        return PlanOption(
            code = optString("code", "custom"),
            name = optString("name", optString("code", "custom")),
            description = optString("description", ""),
            services = mapOf(
                "knowledge" to servicesObj.optBoolean("knowledge", false),
                "memory" to servicesObj.optBoolean("memory", false),
                "telegram" to servicesObj.optBoolean("telegram", false),
                "email" to servicesObj.optBoolean("email", false),
                "music" to servicesObj.optBoolean("music", false),
                "call" to servicesObj.optBoolean("call", false),
            ),
            limitsClients = limitsObj.optInt("clients", 0),
            limitsMemoryMb = limitsObj.optInt("memory_mb", 0),
            limitsOperators = limitsObj.optInt("operators", 0),
        )
    }

    private fun JSONArray.toUsers(): List<RobotUser> = buildList {
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            add(
                RobotUser(
                    userId = obj.optString("user_id", ""),
                    name = obj.optString("name", ""),
                    contact = obj.optString("contact", ""),
                    role = obj.optString("role", "client"),
                )
            )
        }
    }

    private fun JSONObject.toOnboardingBundle(): OnboardingBundle {
        val subscription = optJSONObject("subscription") ?: JSONObject()
        val owner = optJSONObject("owner") ?: JSONObject()
        val users = optJSONArray("users") ?: JSONArray()
        val selfhost = optJSONObject("selfhost_xiaozhi")
        val mobileProfile = optJSONObject("mobile_profile")
        return OnboardingBundle(
            code = optString("code", ""),
            panelUrl = optString("panel_url", ""),
            panelClientToken = optString("panel_client_token", ""),
            robotId = optString("robot_id", ""),
            robotName = optString("robot_name", optString("robot_id", "")),
            cloudEndpoint = optString("cloud_endpoint", ""),
            planCode = subscription.optString("plan_code", "start"),
            planName = subscription.optString("plan_name", "Старт"),
            billingStart = subscription.optJSONObject("billing")?.optString("start_date", "") ?: "",
            billingEnd = subscription.optJSONObject("billing")?.optString("end_date", "") ?: "",
            paymentStatus = subscription.optJSONObject("billing")?.optString("payment_status", "") ?: "",
            ownerName = owner.optString("name", ""),
            ownerEmail = owner.optString("email", ""),
            ownerPhone = owner.optString("phone", ""),
            ownerCompany = owner.optString("company", ""),
            ownerContact = owner.optString("contact", ""),
            ownerComment = owner.optString("comment", ""),
            instruction = optString("instruction", ""),
            users = users.toUsers(),
            selfhostXiaozhi = selfhost?.toSelfhostXiaozhiBundle(),
            mobileProfile = mobileProfile?.toMobileProfile(),
        )
    }

    private fun JSONObject.toSelfhostXiaozhiBundle(): SelfhostXiaozhiBundle {
        return SelfhostXiaozhiBundle(
            provider = optString("provider", ""),
            otaUrl = optString("ota_url", ""),
            activateUrl = optString("activate_url", ""),
            websocketUrl = optString("websocket_url", ""),
            mcpEndpointBase = optString("mcp_endpoint_base", ""),
        )
    }

    private fun JSONObject.toMobileProfile(): MobileProfile {
        val prefixes = buildList {
            val items = optJSONArray("robot_wifi_prefixes") ?: JSONArray()
            for (i in 0 until items.length()) {
                val value = items.optString(i, "").trim()
                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }
        return MobileProfile(
            brand = optString("brand", ""),
            panelUrl = optString("panel_url", ""),
            mcpEndpointBase = optString("mcp_endpoint_base", ""),
            websocketUrl = optString("websocket_url", ""),
            portalUrl = optString("portal_url", ""),
            robotWifiPrefixes = prefixes,
            preferredBackendMode = optString("preferred_backend_mode", ""),
        )
    }

    private fun parseLocalHost(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        return try {
            val uri = URI(rawUrl.trim())
            val host = uri.host.orEmpty().trim()
            if (host.isBlank() || !isDirectRobotWsPath(uri.path.orEmpty())) return ""
            directRobotHostOrBlank(host)
        } catch (_: Exception) {
            ""
        }
    }

    private fun isDirectRobotWsPath(path: String): Boolean {
        val normalized = path.trim().ifBlank { "/" }.trimEnd('/')
        return normalized == "/ws"
    }

    private fun directRobotHostOrBlank(host: String): String {
        val normalized = host.trim().lowercase()
        if (normalized.isBlank()) return ""
        if (normalized == "localhost" || normalized == "0.0.0.0" || normalized.startsWith("127.")) return ""
        if (normalized.endsWith(".local")) return host.trim()
        if (normalized.startsWith("10.") || normalized.startsWith("192.168.")) return host.trim()
        if (normalized.startsWith("172.")) {
            val second = normalized.split('.').getOrNull(1)?.toIntOrNull()
            if (second != null && second in 16..31) return host.trim()
        }
        return ""
    }

    private fun mobileHeaders(panelClientToken: String, onboardingCode: String): Map<String, String> = buildMap {
        if (panelClientToken.isNotBlank()) {
            put("X-Mobile-Token", panelClientToken.trim())
        }
        if (onboardingCode.isNotBlank()) {
            put("X-Mobile-Code", onboardingCode.trim())
        }
    }

    private fun requestJson(
        http: OkHttpClient,
        url: String,
        method: String = "GET",
        body: JSONObject? = null,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val builder = Request.Builder().url(url)
        for ((key, value) in headers) {
            if (value.isNotBlank()) {
                builder.header(key, value)
            }
        }
        if (method != "GET") {
            builder.method(method, (body?.toString() ?: "{}").toRequestBody(JSON_MEDIA))
        }
        http.newCall(builder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException(if (raw.isNotBlank()) raw else "HTTP ${resp.code}")
            }
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        }
    }
}
