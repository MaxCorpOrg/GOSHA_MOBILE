package com.maxcorp.gosha.mobile

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ConnectorForegroundService : Service() {
    private val logTag = "ConnectorService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val panelHttpClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    private lateinit var configStore: ConfigStore
    private lateinit var notificationManager: NotificationManager
    private lateinit var runtimeEventReporter: RuntimeEventReporter

    private var connectorJob = SupervisorJob()
    private val connectorStateLock = Any()
    @Volatile private var activeConnectorConfig: ConnectorConfig? = null
    @Volatile private var activeConnectorStartId: Int = 0
    private val robotWsProbeTracker = ServiceRobotWsProbeTracker()
    @Volatile private var lastConnectorState: String = "idle"
    @Volatile private var lastRuntimeProbeSignature: String = ""

    override fun onCreate() {
        super.onCreate()
        configStore = ConfigStore(this)
        runtimeEventReporter = RuntimeEventReporter(this, panelHttpClient)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Инициализация"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Инициализация"))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                publishStatus("Остановлен пользователем")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val cfg = ConnectorConfig.fromIntent(intent) ?: configStore.loadConfig()
                if (cfg == null) {
                    publishStatus("Ошибка: пустая конфигурация")
                } else {
                    configStore.saveConfig(cfg)
                    startOrRestart(cfg, startId)
                }
                return START_STICKY
            }
            else -> {
                val cfg = configStore.loadConfig()
                if (cfg != null) {
                    startOrRestart(cfg, startId)
                    return START_STICKY
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startOrRestart(config: ConnectorConfig, startId: Int) {
        val nextJob = SupervisorJob()
        synchronized(connectorStateLock) {
            activeConnectorConfig = null
            activeConnectorStartId = 0
            connectorJob.cancel()
            connectorJob = nextJob
            activeConnectorConfig = config
            activeConnectorStartId = startId
        }
        serviceScope.launch(nextJob) {
            runPanelPresenceLoop(config, startId, nextJob)
        }
        if (config.canRunEdgeHub()) {
            serviceScope.launch(nextJob) {
                runConnectorLoop(config, startId, nextJob)
            }
        } else {
            setConnectorState("hub_not_configured")
            publishStatus("Edge Hub не настроен: работает локальная проверка и presence")
        }
    }

    private fun localRobotHttpClient(): OkHttpClient {
        return WifiBoundHttp.forCurrentWifi(this, httpClient)
    }

    private fun localRobotSocketFactory() =
        WifiInfoHelper.currentWifiNetwork(this)?.socketFactory

    private suspend fun runConnectorLoop(
        config: ConnectorConfig,
        startId: Int,
        runJob: kotlinx.coroutines.Job,
    ) {
        var backoffSec = 1L
        val agentUrl = config.agentUrl()

        publishStatus("Старт коннектора: ${config.robotId}")
        publishStatus("Hub: ${config.hubBaseUrl}")
        publishStatus("Robot WS: ${config.robotWsUrl()}")

        while (serviceScope.isActive && runJob.isActive) {
            if (!ensureCurrentConnectorIdentity(config, startId)) break
            try {
                publishStatus("Подключение к Hub...")
                val hub = HubSocket.connect(
                    http = httpClient,
                    url = agentUrl,
                    expectedRobotId = config.robotId,
                )
                publishStatus("Hub готов к маршрутизации")
                setConnectorState("hub_ready")
                backoffSec = 1L
                var heartbeatJob: kotlinx.coroutines.Job? = null
                try {
                    publishAgentStatus(hub, config, startId)

                    heartbeatJob = serviceScope.launch(runJob) {
                        while (isActive) {
                            delay(15_000)
                            publishAgentStatus(hub, config, startId)
                        }
                    }

                    for (raw in hub.incoming) {
                        if (!ensureCurrentConnectorIdentity(config, startId)) break
                        handleHubMessage(raw, hub, config, startId)
                    }

                    val reason = hub.awaitClosed()
                    publishStatus("Hub закрыт: $reason")
                    setConnectorState("hub_closed")
                } finally {
                    heartbeatJob?.cancelAndJoin()
                    hub.close()
                }
            } catch (exc: Exception) {
                publishStatus("Ошибка: ${exc.message}")
                setConnectorState("hub_error")
            }

            if (!runJob.isActive) break

            publishStatus("Переподключение через ${backoffSec}s")
            delay(backoffSec * 1000)
            backoffSec = (backoffSec * 2).coerceAtMost(20L)
        }
    }

    private suspend fun runPanelPresenceLoop(
        config: ConnectorConfig,
        startId: Int,
        runJob: kotlinx.coroutines.Job,
    ) {
        while (serviceScope.isActive && runJob.isActive) {
            if (!ensureCurrentConnectorIdentity(config, startId)) break
            try {
                refreshPanelPresence(config, startId = startId)
            } catch (exc: Exception) {
                Log.w(logTag, "Presence refresh failed: ${exc.message}")
            }
            delay(PANEL_PRESENCE_REFRESH_INTERVAL_MS)
        }
    }

    private suspend fun handleHubMessage(
        raw: String,
        hub: HubSocket,
        config: ConnectorConfig,
        startId: Int,
    ) {
        if (!ensureCurrentConnectorIdentity(config, startId)) return
        val msg = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return
        }
        when (msg.optString("type")) {
            "agent_ready" -> {
                publishStatus("Hub готов к маршрутизации")
                setConnectorState("hub_ready")
                publishAgentStatus(hub, config, startId)
            }

            "ping" -> {
                sendToHub(
                    hub,
                    JSONObject().put("type", "pong").put("ts", System.currentTimeMillis() / 1000).toString(),
                    "pong",
                )
                publishAgentStatus(hub, config, startId)
            }

            "mcp_notify" -> {
                val payload = msg.optJSONObject("payload") ?: return
                try {
                    RobotJsonRpcProxy.notify(
                        http = localRobotHttpClient(),
                        robotWsUrl = config.robotWsUrl(),
                        payload = payload,
                        expectedDeviceId = config.expectedDeviceId,
                    )
                    setRobotWsState(true, "")
                } catch (exc: Exception) {
                    publishStatus("notify->robot failed: ${exc.message}")
                    setRobotWsState(false, exc.message ?: "notify failed")
                }
                publishAgentStatus(hub, config, startId)
            }

            "mcp_request" -> {
                val bridgeId = msg.optString("bridge_id")
                val payload = msg.optJSONObject("payload") ?: return
                val responsePayload = try {
                    RobotJsonRpcProxy.call(
                        http = localRobotHttpClient(),
                        robotWsUrl = config.robotWsUrl(),
                        payload = payload,
                        expectedDeviceId = config.expectedDeviceId,
                    ).also { setRobotWsState(true, "") }
                } catch (exc: Exception) {
                    setRobotWsState(false, exc.message ?: "request failed")
                    jsonRpcError(payload.opt("id"), "mobile connector error: ${exc.message}")
                }
                val envelope = JSONObject()
                    .put("type", "mcp_response")
                    .put("bridge_id", bridgeId)
                    .put("payload", responsePayload)
                    .put("ts", System.currentTimeMillis() / 1000)
                sendToHub(hub, envelope.toString(), "mcp_response")
                publishAgentStatus(hub, config, startId)
            }
        }
    }

    private suspend fun publishAgentStatus(
        hub: HubSocket,
        config: ConnectorConfig,
        startId: Int,
    ) {
        if (!ensureCurrentConnectorIdentity(config, startId)) return
        val probe = probeRobotWs(config)
        runCatching {
            refreshPanelPresence(config, probe, startId)
        }.onFailure { exc ->
            Log.w(logTag, "Panel presence refresh failed during agent status: ${exc.message}")
        }

        val status = JSONObject()
            .put("connector_status", lastConnectorState)
            .put("robot_ws_url", config.robotWsUrl())
            .put("robot_ws_ok", probe.ok)
            .put("robot_ws_error", probe.error)
            .put("robot_ws_probe_state", probe.state.wireValue)
            .put("robot_ws_probe_active_source", probe.activeSource)
            .put("robot_ws_probe_retry_after_ms", probe.retryAfterMs)
            .put("robot_ws_probe_cached_age_ms", probe.cachedAgeMs ?: JSONObject.NULL)
            .put("robot_ws_probe_executed_count", probe.executedCount)
            .put("robot_ws_probe_skipped_count", probe.skippedCount)
            .put("robot_ws_probe_stale_count", probe.staleCount)
            .put("robot_ws_probe_min_interval_ms", probe.serviceMinIntervalMs)
            .put("updated_at", System.currentTimeMillis() / 1000)

        sendToHub(
            hub,
            JSONObject()
                .put("type", "agent_status")
                .put("status", status)
                .put("ts", System.currentTimeMillis() / 1000)
                .toString(),
            "agent_status",
        )
    }

    private fun sendToHub(hub: HubSocket, text: String, messageType: String) {
        if (!hub.send(text)) {
            throw IOException("failed to send $messageType to hub")
        }
    }

    private suspend fun probeRobotWs(config: ConnectorConfig): ServiceRobotWsProbeResult {
        val result = when (
            val run = LocalRobotProbeCoordinator.runServiceProbe(
                source = SERVICE_PROBE_SOURCE,
                minIntervalMs = robotWsProbeTracker.serviceMinIntervalMs(),
            ) {
                val probe = probeRobotIdentity(config)
                if (probe.first) {
                    LocalRobotProbeCoordinator.recordSuccessfulServiceHost(
                        host = config.robotHost,
                        source = SERVICE_PROBE_SOURCE,
                        expectedDeviceId = config.expectedDeviceId,
                    )
                }
                probe
            }
        ) {
            is LocalRobotProbeRun.Executed -> {
                val (ok, error) = run.value
                robotWsProbeTracker.recordExecuted(ok, error, System.currentTimeMillis())
            }
            is LocalRobotProbeRun.Skipped -> {
                robotWsProbeTracker.recordSkipped(run, System.currentTimeMillis())
            }
        }

        Log.d(
            logTag,
            "Robot WS probe state=${result.state.wireValue} ok=${result.ok} active=${result.activeSource} " +
                "retryAfterMs=${result.retryAfterMs} cachedAgeMs=${result.cachedAgeMs} " +
                "executed=${result.executedCount} skipped=${result.skippedCount} stale=${result.staleCount} " +
                "minIntervalMs=${result.serviceMinIntervalMs}"
        )
        return result
    }

    private suspend fun probeRobotIdentity(config: ConnectorConfig): Pair<Boolean, String> {
        val expectedDeviceId = config.expectedDeviceId.trim()
        if (expectedDeviceId.isBlank()) {
            return false to "expected_device_id_missing"
        }
        val ok = matchesRobotIdentity(config)
        return if (ok) {
            true to ""
        } else {
            false to "device identity mismatch"
        }
    }

    private suspend fun matchesRobotIdentity(config: ConnectorConfig): Boolean {
        return LocalRobotIdentityProbe.matches(
            socketFactory = localRobotSocketFactory(),
            host = config.robotHost,
            expectedDeviceId = config.expectedDeviceId,
            port = config.robotPort,
            path = config.robotPath,
            timeoutMs = 4_000L,
        )
    }

    private suspend fun refreshPanelPresence(
        config: ConnectorConfig,
        probeResult: ServiceRobotWsProbeResult? = null,
        startId: Int,
    ) {
        if (!ensureCurrentConnectorIdentity(config, startId)) return
        val probe = probeResult ?: run {
            probeRobotWs(config)
        }
        if (!probe.canPublishPresence) {
            Log.d(
                logTag,
                "Panel presence skipped: probeState=${probe.state.wireValue} ok=${probe.ok} cachedAgeMs=${probe.cachedAgeMs}"
            )
            return
        }

        val draft = configStore.loadDraft()
        if (!ensureCurrentConnectorIdentity(config, startId, draft)) {
            return
        }

        PanelApiClient.updateMobilePresence(
            http = panelHttpClient,
            baseUrl = draft.panelBaseUrl,
            robotId = config.robotId,
            state = MobilePresenceState.HOME_WIFI_LOCAL,
            localHost = config.robotHost,
            panelClientToken = draft.panelClientToken,
            onboardingCode = draft.onboardingCode,
            sourceId = runtimeEventReporter.sourceId,
            instanceId = runtimeEventReporter.sessionId,
            appVersion = BuildConfig.VERSION_NAME,
        )
        val target = RuntimeEventTarget(
            baseUrl = draft.panelBaseUrl,
            robotId = config.robotId,
            panelClientToken = draft.panelClientToken,
            onboardingCode = draft.onboardingCode,
        )
        val runtimeSignature = listOf(
            probe.ok,
            probe.state.wireValue,
            lastConnectorState,
        ).joinToString("|")
        if (runtimeSignature != lastRuntimeProbeSignature) {
            runtimeEventReporter.publish(
                target,
                runtimeEventReporter.event(
                    eventType = "mobile.robot_link.changed",
                    severity = if (probe.ok) "info" else "warning",
                    state = JSONObject()
                        .put("domain", "connector")
                        .put("name", lastConnectorState)
                        .put("status", if (probe.ok) "ready" else "degraded"),
                    link = JSONObject()
                        .put("kind", "mobile_robot")
                        .put("status", if (probe.ok) "available" else "unavailable"),
                    error = if (probe.ok) {
                        null
                    } else {
                        JSONObject()
                            .put("code", "robot_probe_failed")
                            .put("message", "Локальная проверка робота не выполнена")
                            .put("retryable", true)
                    },
                    metrics = JSONObject()
                        .put("probe_executed_count", probe.executedCount)
                        .put("probe_skipped_count", probe.skippedCount)
                        .put("probe_stale_count", probe.staleCount),
                    attributes = JSONObject()
                        .put("probe_state", probe.state.wireValue)
                        .put("active_source", probe.activeSource),
                ),
            )
            lastRuntimeProbeSignature = runtimeSignature
        } else {
            runtimeEventReporter.flush(target)
        }
    }

    private fun ensureCurrentConnectorIdentity(
        config: ConnectorConfig,
        startId: Int,
        draft: OnboardingDraft = configStore.loadDraft(),
    ): Boolean {
        var shouldStop = false
        val matches = synchronized(connectorStateLock) {
            if (activeConnectorConfig !== config || activeConnectorStartId != startId) {
                false
            } else if (connectorIdentityMatchesDraft(config, draft)) {
                true
            } else {
                activeConnectorConfig = null
                activeConnectorStartId = 0
                connectorJob.cancel()
                shouldStop = true
                false
            }
        }
        if (shouldStop) {
            // stopSelfResult fences the stop to this start request: if Android has already
            // accepted a newer ACTION_START, an older coroutine cannot stop that new run.
            val stopped = stopSelfResult(startId)
            Log.w(
                logTag,
                "Stale connector stop requested for startId=$startId; stopped=$stopped",
            )
        }
        return matches
    }

    private fun setRobotWsState(ok: Boolean, error: String) {
        robotWsProbeTracker.recordExternalObservation(ok, error, System.currentTimeMillis())
    }

    private fun setConnectorState(state: String) {
        lastConnectorState = state
    }

    private fun jsonRpcError(id: Any?, message: String): JSONObject {
        val errorObj = JSONObject().put("message", message)
        val root = JSONObject().put("jsonrpc", "2.0").put("error", errorObj)
        if (id != null) root.put("id", id)
        return root
    }

    @android.annotation.SuppressLint("NotificationPermission")
    private fun publishStatus(text: String) {
        val line = "${System.currentTimeMillis()}: $text"
        configStore.saveStatus(line)
        sendBroadcast(Intent(ACTION_STATUS).putExtra(EXTRA_STATUS_TEXT, line))
        if (canPostNotifications()) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Гоша",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "Фоновое подключение робота"
        notificationManager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildNotification(status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Гоша")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        synchronized(connectorStateLock) {
            activeConnectorConfig = null
            activeConnectorStartId = 0
            connectorJob.cancel()
        }
        serviceScope.cancel()
        publishStatus("Сервис остановлен")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.maxcorp.gosha.mobile.action.START"
        const val ACTION_STOP = "com.maxcorp.gosha.mobile.action.STOP"
        const val ACTION_STATUS = "com.maxcorp.gosha.mobile.action.STATUS"

        const val EXTRA_HUB_URL = "extra_hub_url"
        const val EXTRA_ROBOT_ID = "extra_robot_id"
        const val EXTRA_EXPECTED_DEVICE_ID = "extra_expected_device_id"
        const val EXTRA_TOKEN = "extra_token"
        const val EXTRA_ROBOT_HOST = "extra_robot_host"
        const val EXTRA_ROBOT_PORT = "extra_robot_port"
        const val EXTRA_ROBOT_PATH = "extra_robot_path"
        const val EXTRA_STATUS_TEXT = "extra_status_text"

        private const val CHANNEL_ID = "maxcorp_connector_channel"
        private const val NOTIFICATION_ID = 91101
        private const val PANEL_PRESENCE_REFRESH_INTERVAL_MS = 20_000L
        private const val SERVICE_PROBE_SOURCE = "ConnectorForegroundService.probeRobotWs"

        fun startIntent(context: Context, config: ConnectorConfig): Intent {
            return config.toIntent(Intent(context, ConnectorForegroundService::class.java).apply {
                action = ACTION_START
            })
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, ConnectorForegroundService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
