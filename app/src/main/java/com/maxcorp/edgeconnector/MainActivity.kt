package com.maxcorp.gosha.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val logTag = "MaxRobotFlow"
    private enum class WizardStep { LOADING, WELCOME, REGISTRATION, WIFI, SUCCESS, MENU }

    private val robotWifiPrefix = RobotBranding.PRIMARY_WIFI_PREFIX
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val panelStatusHttpClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .build()
    private val presenceHttpClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    private lateinit var configStore: ConfigStore
    private lateinit var runtimeEventReporter: RuntimeEventReporter
    private lateinit var rootScrollView: ScrollView
    private lateinit var rootContent: View

    private lateinit var cardLoadingStep: View
    private lateinit var cardWelcomeStep: View
    private lateinit var cardRegistrationStep: View
    private lateinit var cardWifiStep: View
    private lateinit var cardSuccessStep: View
    private lateinit var cardMenuStep: View
    private lateinit var cardDiagnostics: View

    private lateinit var etCode: TextInputEditText
    private lateinit var etOwnerName: TextInputEditText
    private lateinit var etOwnerEmail: TextInputEditText
    private lateinit var etOwnerPhone: TextInputEditText
    private lateinit var cbPrivacyConsent: CheckBox

    private lateinit var tvCodeStatus: TextView
    private lateinit var tvRegistrationIntro: TextView
    private lateinit var tvPrivacyConsentHint: TextView
    private lateinit var tvWifiName: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var tvRobotCheck: TextView
    private lateinit var tvSuccessMessage: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvAppTitle: TextView
    private lateinit var tvAppSubtitle: TextView
    private lateinit var tvMenuHeroTitle: TextView
    private lateinit var tvMenuHeroBody: TextView
    private lateinit var tvLoadingTitle: TextView
    private lateinit var tvLoadingBody: TextView
    private lateinit var tvMenuRobot: TextView
    private lateinit var tvMenuPlan: TextView
    private lateinit var tvMenuOwner: TextView
    private lateinit var tvDiagnosticsBody: TextView
    private lateinit var loadingRobot: View
    private lateinit var loadingGlow: View
    private lateinit var loadingShadow: View
    private lateinit var loadingSparkleLeft: View
    private lateinit var loadingSparkleRight: View
    private lateinit var btnRegistrationBack: Button
    private lateinit var btnActivate: Button
    private lateinit var btnWelcomePrivacyPolicy: Button
    private lateinit var btnWelcomeTermsOfUse: Button
    private lateinit var btnRegistrationPrivacyPolicy: Button
    private lateinit var btnRegistrationTermsOfUse: Button
    private lateinit var btnWifiBack: Button
    private lateinit var btnSuccessBack: Button
    private lateinit var btnMenuReconnect: Button
    private lateinit var btnMenuReset: Button

    private var currentBundle: OnboardingBundle? = null
    private var currentStep: WizardStep = WizardStep.WELCOME
    private var awaitingRobotProvision = false
    private var robotWifiPortalActive = false
    private var pendingRobotWifiConnection = false
    private var robotProvisionCheckJob: Job? = null
    private var robotWifiConnectTimeoutJob: Job? = null
    private var resumeDiscoveryJob: Job? = null
    private var wifiStepStatusJob: Job? = null
    private var menuStabilizationJob: Job? = null
    private var menuStabilizationRunning = false
    private var queuedMenuStabilizationReason = ""
    private var menuRecoveryRetryJob: Job? = null
    private var menuRecoveryRetryAttempt = 0
    private var wifiWatcherJob: Job? = null
    private var startupResolutionPending = true
    private var wifiBackToMenuMode = false
    private var pendingRobotWifiSsidHint = ""
    private var lastObservedWifiSsid = ""
    private var lastObservedWifiSubnetPrefix = ""
    private var lastObservedNearbyRobotSsid = ""
    private var lastWifiScanRequestAt = 0L
    private var loadingAnimator: ObjectAnimator? = null
    private var loadingGlowAnimator: ObjectAnimator? = null
    private var loadingShadowAnimator: ObjectAnimator? = null
    private var loadingSparkleLeftAnimator: ObjectAnimator? = null
    private var loadingSparkleRightAnimator: ObjectAnimator? = null
    private var diagnosticsLocal = ""
    private var diagnosticsPanel = ""
    private var diagnosticsDecision = ""
    private var lastPresenceSignature = ""
    private var wifiRecoveryCorrelationId = ""
    private var pendingRobotWifiPermissionPurpose: PermissionRequestPurpose? = null
    private var notificationPermissionRequestStarted = false
    private var notificationPermissionRequestPending = false
    private var backgroundAccessDialog: AlertDialog? = null
    private var robotWifiPortalSubmitted = false

    private lateinit var robotWifiPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var robotWifiPortalLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    private data class ReachabilityCheckResult(
        val panelSnapshot: RobotRuntimeSnapshot?,
        val localHost: String?,
        val localDeviceId: String = "",
    )

    private data class ProvisionPanelState(
        val panelOnlySeen: Boolean,
        val localHostHint: String,
        val deviceIdHint: String,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        configStore = ConfigStore(this)
        runtimeEventReporter = RuntimeEventReporter(this, presenceHttpClient)
        robotWifiPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions.values.all { it }
            val pendingPermissionPurpose = pendingRobotWifiPermissionPurpose
            pendingRobotWifiPermissionPurpose = null
            if (granted && pendingRobotWifiConnection) {
                connectToRobotWifiInsideApp()
            } else if (granted && pendingPermissionPurpose != null) {
                uiScope.launch {
                    WifiInfoHelper.requestFreshScanIfPossible(this@MainActivity)
                    delay(1200L)
                    when (pendingPermissionPurpose) {
                        PermissionRequestPurpose.RESOLVE_EXISTING -> resolveExistingRobotState()
                        PermissionRequestPurpose.PROVISION -> provisionRobotToWifi()
                    }
                }
            } else if (!granted && (pendingRobotWifiConnection || pendingPermissionPurpose != null)) {
                pendingRobotWifiConnection = false
                robotWifiConnectTimeoutJob?.cancel()
                pendingRobotWifiSsidHint = ""
                showStep(WizardStep.WIFI)
                tvRobotCheck.text = getString(R.string.wifi_permission_denied_text)
                updateLocalDiagnostics(getString(R.string.diagnostics_local_permission_required))
                diagnosticsPanel = getString(R.string.diagnostics_panel_skipped_permission_required)
                renderDiagnostics()
                setStatus(getString(R.string.wifi_permission_denied_status))
                toast(getString(R.string.wifi_permission_denied_toast))
            }
        }
        robotWifiPortalLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            robotWifiPortalActive = false
            robotWifiPortalSubmitted = result.resultCode == Activity.RESULT_OK
            if (!robotWifiPortalSubmitted) {
                Log.d(logTag, "Robot Wi-Fi portal returned before submit; opening reconnect step")
                handleRobotWifiPortalCancelledBeforeSubmit()
                return@registerForActivityResult
            }
            Log.d(logTag, "Robot Wi-Fi portal returned after submit; starting post-provision check")
            maybeContinueProvisionAfterReturn()
        }
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            notificationPermissionRequestPending = false
            Log.d(logTag, "Notification permission granted=$granted")
            rootContent.post { maybeOfferBackgroundAccess() }
        }

        bindViews()
        setupListeners()
        setupBackNavigation()
        fillFromStore()
        refreshWifiInfo()
    }

    override fun onResume() {
        super.onResume()
        val previousWifiSsid = lastObservedWifiSsid
        val previousWifiSubnetPrefix = lastObservedWifiSubnetPrefix
        val previousNearbyRobotSsid = lastObservedNearbyRobotSsid
        refreshWifiInfo()
        startWifiWatcher()
        maybeCheckRobotConnectedAfterProvision()
        if (!awaitingRobotProvision) {
            if (startupResolutionPending) {
                maybeRestoreConnectedRobot()
            } else {
                val currentSsid = WifiInfoHelper.currentSsid(this)
                val nearbyRobotSsid = currentVisibleRobotSsid()
                maybeReactToWifiChange(
                    previousSsid = previousWifiSsid,
                    currentSsid = currentSsid,
                    previousSubnetPrefix = previousWifiSubnetPrefix,
                    currentSubnetPrefix = WifiInfoHelper.currentSubnetPrefix(this),
                    previousNearbyRobotSsid = previousNearbyRobotSsid,
                    currentNearbyRobotSsid = nearbyRobotSsid,
                    fromWatcher = false
                )
            }
        }
        if (!awaitingRobotProvision && currentStep == WizardStep.WIFI && wifiBackToMenuMode) {
            refreshWifiReconnectStatus(forceScan = false)
        }
        if (!awaitingRobotProvision && currentStep == WizardStep.MENU && configStore.loadDraft().setupCompleted) {
            applyImmediateMenuHint()
            scheduleMenuStabilization(reason = "resume")
            rootContent.post { maybePrepareBackgroundAccess() }
        }
    }

    override fun onPause() {
        cancelMenuStabilization()
        wifiStepStatusJob?.cancel()
        wifiStepStatusJob = null
        wifiWatcherJob?.cancel()
        wifiWatcherJob = null
        super.onPause()
    }

    override fun onDestroy() {
        robotProvisionCheckJob?.cancel()
        robotWifiConnectTimeoutJob?.cancel()
        resumeDiscoveryJob?.cancel()
        wifiStepStatusJob?.cancel()
        cancelMenuStabilization()
        wifiWatcherJob?.cancel()
        loadingAnimator?.cancel()
        loadingGlowAnimator?.cancel()
        loadingShadowAnimator?.cancel()
        loadingSparkleLeftAnimator?.cancel()
        loadingSparkleRightAnimator?.cancel()
        backgroundAccessDialog?.dismiss()
        backgroundAccessDialog = null
        uiScope.cancel()
        super.onDestroy()
    }

    private fun bindViews() {
        rootScrollView = findViewById(R.id.rootScrollView)
        rootContent = findViewById(R.id.rootContent)
        cardLoadingStep = findViewById(R.id.cardLoadingStep)
        cardWelcomeStep = findViewById(R.id.cardWelcomeStep)
        cardRegistrationStep = findViewById(R.id.cardRegistrationStep)
        cardWifiStep = findViewById(R.id.cardWifiStep)
        cardSuccessStep = findViewById(R.id.cardSuccessStep)
        cardMenuStep = findViewById(R.id.cardMenuStep)
        cardDiagnostics = findViewById(R.id.cardDiagnostics)

        etCode = findViewById(R.id.etCode)
        etOwnerName = findViewById(R.id.etOwnerName)
        etOwnerEmail = findViewById(R.id.etOwnerEmail)
        etOwnerPhone = findViewById(R.id.etOwnerPhone)
        cbPrivacyConsent = findViewById(R.id.cbPrivacyConsent)

        tvCodeStatus = findViewById(R.id.tvCodeStatus)
        tvRegistrationIntro = findViewById(R.id.tvRegistrationIntro)
        tvPrivacyConsentHint = findViewById(R.id.tvPrivacyConsentHint)
        tvWifiName = findViewById(R.id.tvWifiName)
        tvInstruction = findViewById(R.id.tvInstruction)
        tvRobotCheck = findViewById(R.id.tvRobotCheck)
        tvSuccessMessage = findViewById(R.id.tvSuccessMessage)
        tvStatus = findViewById(R.id.tvStatus)
        tvAppTitle = findViewById(R.id.tvAppTitle)
        tvAppSubtitle = findViewById(R.id.tvAppSubtitle)
        tvMenuHeroTitle = findViewById(R.id.tvMenuHeroTitle)
        tvMenuHeroBody = findViewById(R.id.tvMenuHeroBody)
        tvLoadingTitle = findViewById(R.id.tvLoadingTitle)
        tvLoadingBody = findViewById(R.id.tvLoadingBody)
        tvMenuRobot = findViewById(R.id.tvMenuRobot)
        tvMenuPlan = findViewById(R.id.tvMenuPlan)
        tvMenuOwner = findViewById(R.id.tvMenuOwner)
        tvDiagnosticsBody = findViewById(R.id.tvDiagnosticsBody)
        loadingRobot = findViewById(R.id.loadingRobot)
        loadingGlow = findViewById(R.id.loadingGlow)
        loadingShadow = findViewById(R.id.loadingShadow)
        loadingSparkleLeft = findViewById(R.id.loadingSparkleLeft)
        loadingSparkleRight = findViewById(R.id.loadingSparkleRight)
        btnRegistrationBack = findViewById(R.id.btnRegistrationBack)
        btnActivate = findViewById(R.id.btnActivate)
        btnWelcomePrivacyPolicy = findViewById(R.id.btnWelcomePrivacyPolicy)
        btnWelcomeTermsOfUse = findViewById(R.id.btnWelcomeTermsOfUse)
        btnRegistrationPrivacyPolicy = findViewById(R.id.btnRegistrationPrivacyPolicy)
        btnRegistrationTermsOfUse = findViewById(R.id.btnRegistrationTermsOfUse)
        btnWifiBack = findViewById(R.id.btnWifiBack)
        btnSuccessBack = findViewById(R.id.btnSuccessBack)
        btnMenuReconnect = findViewById(R.id.btnMenuReconnect)
        btnMenuReset = findViewById(R.id.btnMenuReset)

        // Xiaomi/MIUI can leave a stale loading layer on screen until a full redraw.
        cardLoadingStep.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    private fun setupListeners() {
        etCode.doAfterTextChanged {
            saveDraftLocally()
            updateRegistrationUi()
        }
        etOwnerName.doAfterTextChanged {
            saveDraftLocally()
            updateRegistrationUi()
        }
        etOwnerEmail.doAfterTextChanged {
            saveDraftLocally()
            updateRegistrationUi()
        }
        etOwnerPhone.doAfterTextChanged {
            saveDraftLocally()
            updateRegistrationUi()
        }
        cbPrivacyConsent.setOnCheckedChangeListener { _, _ ->
            updateRegistrationUi()
        }

        findViewById<Button>(R.id.btnWelcomeNext).setOnClickListener { showStep(WizardStep.REGISTRATION) }
        btnWelcomePrivacyPolicy.setOnClickListener { openPrivacyPolicy() }
        btnWelcomeTermsOfUse.setOnClickListener { openTermsOfUse() }
        btnRegistrationPrivacyPolicy.setOnClickListener { openPrivacyPolicy() }
        btnRegistrationTermsOfUse.setOnClickListener { openTermsOfUse() }
        btnRegistrationBack.setOnClickListener { showStep(WizardStep.WELCOME) }
        btnActivate.setOnClickListener { activateByCode() }
        btnWifiBack.setOnClickListener {
            showStep(if (wifiBackToMenuMode) WizardStep.MENU else WizardStep.REGISTRATION)
        }
        findViewById<Button>(R.id.btnProvisionRobotWifi).setOnClickListener { provisionRobotToWifi() }
        btnSuccessBack.setOnClickListener { showStep(WizardStep.WIFI) }
        findViewById<Button>(R.id.btnSuccessNext).setOnClickListener {
            showMenuWithStatus(getString(R.string.runtime_status_menu))
        }
        btnMenuReconnect.setOnClickListener { reconnectRobotWifi() }
        findViewById<Button>(R.id.btnMenuStatus).setOnClickListener { showDeviceStatus() }
        findViewById<Button>(R.id.btnMenuCabinet).setOnClickListener { openCabinet() }
        findViewById<Button>(R.id.btnMenuGuides).setOnClickListener { showGuides() }
        findViewById<Button>(R.id.btnMenuSupport).setOnClickListener { showSupport() }
        btnMenuReset.setOnClickListener { confirmResetForNextRobot() }
        UiPlayful.enhanceButtons(
            findViewById(R.id.btnWelcomeNext),
            btnWelcomePrivacyPolicy,
            btnWelcomeTermsOfUse,
            btnRegistrationPrivacyPolicy,
            btnRegistrationTermsOfUse,
            btnRegistrationBack,
            btnActivate,
            btnWifiBack,
            findViewById(R.id.btnProvisionRobotWifi),
            btnSuccessBack,
            findViewById(R.id.btnSuccessNext),
            btnMenuReconnect,
            findViewById(R.id.btnMenuStatus),
            findViewById(R.id.btnMenuCabinet),
            findViewById(R.id.btnMenuGuides),
            findViewById(R.id.btnMenuSupport),
            btnMenuReset,
        )
        btnMenuReset.visibility = if (BuildConfig.IS_ADMIN_APP) View.VISIBLE else View.GONE
        if (BuildConfig.IS_ADMIN_APP) {
            tvRegistrationIntro.text = getString(R.string.registration_intro_admin)
            btnActivate.text = getString(R.string.button_continue_by_code)
            cbPrivacyConsent.visibility = View.GONE
            tvPrivacyConsentHint.visibility = View.GONE
            cbPrivacyConsent.isChecked = true
        }
        updateRegistrationUi()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navigateBackWithinWizard()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun navigateBackWithinWizard(): Boolean {
        return when (currentStep) {
            WizardStep.LOADING -> false
            WizardStep.WELCOME -> false
            WizardStep.REGISTRATION -> {
                showStep(WizardStep.WELCOME)
                true
            }
            WizardStep.WIFI -> {
                showStep(if (wifiBackToMenuMode) WizardStep.MENU else WizardStep.REGISTRATION)
                true
            }
            WizardStep.SUCCESS -> {
                showStep(WizardStep.WIFI)
                true
            }
            WizardStep.MENU -> false
        }
    }

    private fun fillFromStore() {
        val draft = configStore.loadDraft()
        etCode.setText(draft.onboardingCode)
        etOwnerName.setText(draft.ownerName)
        etOwnerEmail.setText(draft.ownerEmail)
        etOwnerPhone.setText(draft.ownerPhone)

        if (draft.robotId.isNotBlank()) {
            tvCodeStatus.text = buildString {
                append(getString(R.string.summary_label_robot))
                append(": ")
                append(draft.robotName.ifBlank { draft.robotId })
                append("\n")
                append(getString(R.string.summary_label_plan))
                append(": ")
                append(draft.planCode)
                if (draft.ownerName.isNotBlank()) {
                    append("\n")
                    append(getString(R.string.summary_label_user))
                    append(": ")
                    append(draft.ownerName)
                }
                if (draft.ownerEmail.isNotBlank()) {
                    append("\n")
                    append(getString(R.string.summary_label_email))
                    append(": ")
                    append(draft.ownerEmail)
                }
                if (draft.ownerPhone.isNotBlank()) {
                    append("\n")
                    append(getString(R.string.summary_label_phone))
                    append(": ")
                    append(draft.ownerPhone)
                }
            }
        }

        updateMenuUi(draft)
        showStep(WizardStep.LOADING)
        updateRegistrationUi()
        renderDiagnostics()
    }

    private fun initialStepForDraft(draft: OnboardingDraft): WizardStep {
        return when {
            draft.robotId.isNotBlank() -> WizardStep.WIFI
            else -> WizardStep.WELCOME
        }
    }

    private fun saveDraftLocally(
        bundle: OnboardingBundle? = currentBundle,
        discoveredHost: String? = null,
        discoveredDeviceId: String? = null,
        savedDeviceIdIsAuthoritative: Boolean = true,
    ): Boolean {
        val current = configStore.loadDraft()
        val mobileProfile = bundle?.mobileProfile
        val resolvedCode = bundle?.code?.takeIf { it.isNotBlank() }
            ?: etCode.text?.toString()?.trim().orEmpty().ifBlank { current.onboardingCode }
        val resolvedCloudEndpoint = bundle?.cloudEndpoint?.takeIf { it.isNotBlank() } ?: current.cloudEndpoint
        val panelUrl = mobileProfile?.panelUrl?.takeIf { it.isNotBlank() }
            ?: bundle?.panelUrl?.takeIf { it.isNotBlank() }
            ?: current.panelBaseUrl
        val runtimeEndpoints = mergeRuntimeEndpointConfig(
            current = current,
            panelBaseUrl = panelUrl,
            edgeHubUrl = bundle?.edgeHubUrl,
            cloudEndpoint = resolvedCloudEndpoint,
            robotId = bundle?.robotId.orEmpty(),
        )
        val robotWifiPrefixes = mobileProfile?.robotWifiPrefixes
            ?.filter { it.isNotBlank() }
            ?.joinToString(",")
            ?.ifBlank { current.robotWifiPrefixesCsv }
            ?: current.robotWifiPrefixesCsv
        val resolvedExpectedDeviceId = resolveSavedExpectedDeviceIdentity(
            current = current,
            bundleDeviceId = bundle?.selfhostXiaozhi?.deviceId,
            verifiedLocalDeviceId = discoveredDeviceId,
            savedDeviceIdIsAuthoritative = savedDeviceIdIsAuthoritative,
        )
        if (resolvedExpectedDeviceId.hasConflict) {
            Log.w(logTag, "Refusing to save local robot draft: ${resolvedExpectedDeviceId.conflictReason}")
            updateLocalDiagnostics(resolvedExpectedDeviceId.conflictReason)
            renderDiagnostics()
            return false
        }
        val merged = current.copy(
            panelBaseUrl = runtimeEndpoints.panelBaseUrl,
            hubBaseUrl = runtimeEndpoints.hubBaseUrl,
            panelClientToken = bundle?.panelClientToken?.takeIf { it.isNotBlank() } ?: current.panelClientToken,
            robotId = runtimeEndpoints.robotId,
            expectedDeviceId = resolvedExpectedDeviceId.deviceId,
            robotName = bundle?.robotName?.takeIf { it.isNotBlank() } ?: current.robotName,
            token = runtimeEndpoints.token,
            cloudEndpoint = runtimeEndpoints.cloudEndpoint,
            planCode = bundle?.planCode?.takeIf { it.isNotBlank() } ?: current.planCode,
            planName = bundle?.planName?.takeIf { it.isNotBlank() } ?: current.planName,
            billingStart = bundle?.billingStart?.takeIf { it.isNotBlank() } ?: current.billingStart,
            billingEnd = bundle?.billingEnd?.takeIf { it.isNotBlank() } ?: current.billingEnd,
            paymentStatus = bundle?.paymentStatus?.takeIf { it.isNotBlank() } ?: current.paymentStatus,
            ownerName = bundle?.ownerName?.takeIf { it.isNotBlank() } ?: etOwnerName.text?.toString()?.trim().orEmpty(),
            ownerEmail = bundle?.ownerEmail?.takeIf { it.isNotBlank() } ?: etOwnerEmail.text?.toString()?.trim().orEmpty(),
            ownerPhone = bundle?.ownerPhone?.takeIf { it.isNotBlank() } ?: etOwnerPhone.text?.toString()?.trim().orEmpty(),
            clientCompany = bundle?.ownerCompany ?: current.clientCompany,
            clientContact = bundle?.ownerContact ?: current.clientContact,
            clientComment = bundle?.ownerComment ?: current.clientComment,
            onboardingCode = resolvedCode,
            robotHost = discoveredHost ?: current.robotHost,
            robotPort = 8080,
            robotPath = "/ws",
            mobileBrand = mobileProfile?.brand?.takeIf { it.isNotBlank() } ?: current.mobileBrand,
            portalUrl = mobileProfile?.portalUrl?.takeIf { it.isNotBlank() } ?: current.portalUrl,
            mobileWebsocketUrl = mobileProfile?.websocketUrl?.takeIf { it.isNotBlank() } ?: current.mobileWebsocketUrl,
            preferredBackendMode = mobileProfile?.preferredBackendMode?.takeIf { it.isNotBlank() } ?: current.preferredBackendMode,
            robotWifiPrefixesCsv = robotWifiPrefixes,
        )
        persistDraft(merged)
        return true
    }

    private fun persistDraft(draft: OnboardingDraft) {
        configStore.saveDraft(draft)
        draft.toConnectorConfigOrNull()?.let(configStore::saveConfig) ?: configStore.clearConfig()
        updateMenuUi(draft)
    }

    private fun panelBaseUrl(): String {
        val draft = configStore.loadDraft()
        return draft.panelBaseUrl.ifBlank { "http://151.241.228.232:18876" }
    }

    private fun updateRegistrationUi() {
        val code = etCode.text?.toString()?.trim().orEmpty()
        val ownerName = etOwnerName.text?.toString()?.trim().orEmpty()
        val ownerEmail = etOwnerEmail.text?.toString()?.trim().orEmpty()
        val ownerPhone = etOwnerPhone.text?.toString()?.trim().orEmpty()
        val hasActivatedRobot = currentBundle?.robotId?.isNotBlank() == true || configStore.loadDraft().robotId.isNotBlank()

        if (BuildConfig.IS_ADMIN_APP) {
            btnActivate.isEnabled = code.isNotBlank()
            btnActivate.alpha = if (btnActivate.isEnabled) 1f else 0.65f
            if (!hasActivatedRobot) {
                tvCodeStatus.text = if (code.isBlank()) {
                    getString(R.string.registration_status_code_only)
                } else {
                    getString(R.string.registration_status_code_ready)
                }
            }
            return
        }

        val readyForActivation = code.isNotBlank() &&
            ownerName.isNotBlank() &&
            ownerEmail.isNotBlank() &&
            ownerPhone.isNotBlank() &&
            cbPrivacyConsent.isChecked
        btnActivate.isEnabled = readyForActivation
        btnActivate.alpha = if (readyForActivation) 1f else 0.65f
        if (!hasActivatedRobot) {
            tvCodeStatus.text = when {
                code.isBlank() || ownerName.isBlank() || ownerEmail.isBlank() || ownerPhone.isBlank() ->
                    getString(R.string.registration_status_fill)
                !cbPrivacyConsent.isChecked ->
                    getString(R.string.registration_status_consent)
                else ->
                    getString(R.string.registration_status_fill)
            }
        }
    }

    private fun updateMenuUi(draft: OnboardingDraft = configStore.loadDraft()) {
        tvMenuRobot.text = draft.robotName.ifBlank { draft.robotId.ifBlank { getString(R.string.menu_robot_empty) } }
        tvMenuPlan.text = draft.planName.ifBlank { draft.planCode.ifBlank { getString(R.string.menu_value_empty) } }
        tvMenuOwner.text = draft.ownerName.ifBlank {
            draft.ownerEmail.ifBlank {
                draft.ownerPhone.ifBlank { getString(R.string.menu_value_empty) }
            }
        }
        renderDiagnostics()
    }

    private fun describeCurrentStep(): String = when (currentStep) {
        WizardStep.LOADING -> getString(R.string.diagnostics_step_loading)
        WizardStep.WELCOME -> getString(R.string.diagnostics_step_welcome)
        WizardStep.REGISTRATION -> getString(R.string.diagnostics_step_registration)
        WizardStep.WIFI -> getString(R.string.diagnostics_step_wifi)
        WizardStep.SUCCESS -> getString(R.string.diagnostics_step_success)
        WizardStep.MENU -> getString(R.string.diagnostics_step_menu)
    }

    private fun updateLocalDiagnostics(message: String) {
        diagnosticsLocal = message
        renderDiagnostics()
    }

    private fun updatePanelDiagnostics(snapshot: RobotRuntimeSnapshot?, error: String? = null) {
        diagnosticsPanel = when {
            error != null -> getString(R.string.diagnostics_panel_error, error)
            snapshot == null -> getString(R.string.diagnostics_panel_robot_not_found)
            else -> getString(
                R.string.diagnostics_panel_snapshot,
                snapshot.mode.ifBlank { getString(R.string.diagnostics_empty) },
                snapshot.transportState.ifBlank { getString(R.string.diagnostics_empty) },
                snapshot.localHost.ifBlank { getString(R.string.diagnostics_empty) },
                if (snapshot.connected) getString(R.string.diagnostics_yes) else getString(R.string.diagnostics_no),
                snapshot.connectivityEvidence.ifBlank { getString(R.string.diagnostics_empty) },
                snapshot.lastSeenIso.ifBlank { getString(R.string.diagnostics_empty) }
            )
        }
        renderDiagnostics()
    }

    private fun renderDiagnostics() {
        if (!::tvDiagnosticsBody.isInitialized) return
        val draft = configStore.loadDraft()
        val ssid = WifiInfoHelper.currentSsid(this).ifBlank { getString(R.string.diagnostics_empty) }
        val subnet = WifiInfoHelper.currentSubnetPrefix(this).ifBlank { getString(R.string.diagnostics_empty) }
        val vpn = if (NetworkStateHelper.isVpnActive(this)) getString(R.string.diagnostics_yes) else getString(R.string.diagnostics_no)
        val robotId = draft.robotId.ifBlank { getString(R.string.diagnostics_empty) }
        val savedHost = draft.robotHost.ifBlank { getString(R.string.diagnostics_empty) }
        val local = diagnosticsLocal.ifBlank { getString(R.string.diagnostics_waiting) }
        val panel = diagnosticsPanel.ifBlank { getString(R.string.diagnostics_waiting) }
        val decision = diagnosticsDecision.ifBlank { getString(R.string.diagnostics_waiting) }
        tvDiagnosticsBody.text = buildString {
            append(getString(R.string.diagnostics_step))
            append(": ")
            append(describeCurrentStep())
            append("\n")
            append(getString(R.string.diagnostics_phone_wifi))
            append(": ")
            append(ssid)
            append("\n")
            append(getString(R.string.diagnostics_subnet))
            append(": ")
            append(subnet)
            append("\n")
            append(getString(R.string.diagnostics_vpn))
            append(": ")
            append(vpn)
            append("\n")
            append(getString(R.string.diagnostics_robot_id))
            append(": ")
            append(robotId)
            append("\n")
            append(getString(R.string.diagnostics_saved_host))
            append(": ")
            append(savedHost)
            append("\n")
            append(getString(R.string.diagnostics_local_search))
            append(": ")
            append(local)
            append("\n")
            append(getString(R.string.diagnostics_panel))
            append(": ")
            append(panel)
            append("\n")
            append(getString(R.string.diagnostics_decision))
            append(": ")
            append(decision)
        }
    }

    private fun showLoadingState(title: String, body: String) {
        tvLoadingTitle.text = title
        tvLoadingBody.text = body
        showStep(WizardStep.LOADING)
    }

    private fun updateProvisionProgress(message: String) {
        if (currentStep == WizardStep.LOADING) {
            tvLoadingBody.text = message
        }
        tvRobotCheck.text = message
    }

    private fun updateWifiBackButton() {
        if (wifiBackToMenuMode) {
            btnWifiBack.visibility = View.VISIBLE
            btnWifiBack.text = getString(R.string.button_back_to_menu)
        } else {
            btnWifiBack.visibility = View.GONE
        }
    }

    private fun ensureLoadingAnimation(active: Boolean) {
        if (active) {
            if (loadingAnimator == null) {
                val moveY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -20f, 0f)
                val tilt = PropertyValuesHolder.ofFloat(View.ROTATION, 0f, 3f, -3f, 0f)
                loadingAnimator = ObjectAnimator.ofPropertyValuesHolder(loadingRobot, moveY, tilt).apply {
                    duration = 1800L
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                }
            }
            if (loadingShadowAnimator == null) {
                val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.82f, 1f)
                val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.82f, 1f)
                val fade = PropertyValuesHolder.ofFloat(View.ALPHA, 0.28f, 0.16f, 0.28f)
                loadingShadowAnimator = ObjectAnimator.ofPropertyValuesHolder(loadingShadow, scaleX, scaleY, fade).apply {
                    duration = 1800L
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                }
            }
            if (loadingGlowAnimator == null) {
                val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.92f, 1.08f, 0.92f)
                val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.92f, 1.08f, 0.92f)
                val fade = PropertyValuesHolder.ofFloat(View.ALPHA, 0.45f, 0.85f, 0.45f)
                loadingGlowAnimator = ObjectAnimator.ofPropertyValuesHolder(loadingGlow, scaleX, scaleY, fade).apply {
                    duration = 2100L
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                }
            }
            if (loadingSparkleLeftAnimator == null) {
                val moveY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -12f, 0f)
                val fade = PropertyValuesHolder.ofFloat(View.ALPHA, 0.55f, 1f, 0.55f)
                val spin = PropertyValuesHolder.ofFloat(View.ROTATION, 0f, 12f, 0f)
                loadingSparkleLeftAnimator = ObjectAnimator.ofPropertyValuesHolder(loadingSparkleLeft, moveY, fade, spin).apply {
                    duration = 1700L
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                }
            }
            if (loadingSparkleRightAnimator == null) {
                val moveY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -9f, 0f)
                val fade = PropertyValuesHolder.ofFloat(View.ALPHA, 0.45f, 0.95f, 0.45f)
                val spin = PropertyValuesHolder.ofFloat(View.ROTATION, 0f, -18f, 0f)
                loadingSparkleRightAnimator = ObjectAnimator.ofPropertyValuesHolder(loadingSparkleRight, moveY, fade, spin).apply {
                    duration = 2000L
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                }
            }
            if (loadingAnimator?.isStarted != true) {
                loadingAnimator?.start()
            }
            if (loadingGlowAnimator?.isStarted != true) {
                loadingGlowAnimator?.start()
            }
            if (loadingShadowAnimator?.isStarted != true) {
                loadingShadowAnimator?.start()
            }
            if (loadingSparkleLeftAnimator?.isStarted != true) {
                loadingSparkleLeftAnimator?.start()
            }
            if (loadingSparkleRightAnimator?.isStarted != true) {
                loadingSparkleRightAnimator?.start()
            }
        } else {
            loadingAnimator?.cancel()
            loadingGlowAnimator?.cancel()
            loadingShadowAnimator?.cancel()
            loadingSparkleLeftAnimator?.cancel()
            loadingSparkleRightAnimator?.cancel()
            loadingRobot.rotation = 0f
            loadingRobot.translationY = 0f
            loadingGlow.scaleX = 1f
            loadingGlow.scaleY = 1f
            loadingGlow.alpha = 0.65f
            loadingShadow.scaleX = 1f
            loadingShadow.scaleY = 1f
            loadingShadow.alpha = 0.28f
            loadingSparkleLeft.rotation = 0f
            loadingSparkleLeft.translationY = 0f
            loadingSparkleLeft.alpha = 0.75f
            loadingSparkleRight.rotation = 0f
            loadingSparkleRight.translationY = 0f
            loadingSparkleRight.alpha = 0.75f
        }
    }

    private fun showStep(step: WizardStep) {
        val previousStep = currentStep
        currentStep = step
        cardLoadingStep.visibility = if (step == WizardStep.LOADING) View.VISIBLE else View.GONE
        cardLoadingStep.alpha = if (step == WizardStep.LOADING) 1f else 0f
        cardLoadingStep.setLayerType(
            if (step == WizardStep.LOADING) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_NONE,
            null
        )
        cardWelcomeStep.visibility = if (step == WizardStep.WELCOME) View.VISIBLE else View.GONE
        cardRegistrationStep.visibility = if (step == WizardStep.REGISTRATION) View.VISIBLE else View.GONE
        cardWifiStep.visibility = if (step == WizardStep.WIFI) View.VISIBLE else View.GONE
        cardSuccessStep.visibility = if (step == WizardStep.SUCCESS) View.VISIBLE else View.GONE
        cardMenuStep.visibility = if (step == WizardStep.MENU) View.VISIBLE else View.GONE
        tvAppTitle.visibility = if (step == WizardStep.LOADING) View.GONE else View.VISIBLE
        tvAppSubtitle.visibility = if (step == WizardStep.LOADING) View.GONE else View.VISIBLE
        ensureLoadingAnimation(step == WizardStep.LOADING)
        updateWifiBackButton()
        renderDiagnostics()

        if (step == WizardStep.LOADING) {
            setStatus(getString(R.string.runtime_status_searching))
        }
        if (step == WizardStep.WELCOME) {
            setStatus(getString(R.string.runtime_status_vpn_off))
        }
        if (step == WizardStep.REGISTRATION) {
            setStatus(
                getString(
                    if (BuildConfig.IS_ADMIN_APP) R.string.runtime_status_code_only
                    else R.string.runtime_status_fill_data
                )
            )
        }
        if (step == WizardStep.WIFI) {
            setStatus(getString(R.string.runtime_status_wifi))
        }
        if (step == WizardStep.SUCCESS) {
            setStatus(getString(R.string.runtime_status_success))
        }
        if (step == WizardStep.MENU) {
            updateMenuUi()
            setStatus(getString(R.string.runtime_status_menu))
            rootContent.post { maybePrepareBackgroundAccess() }
        }

        if (step != WizardStep.LOADING || previousStep == WizardStep.LOADING) {
            forceStepRedraw(
                scrollToTop = step != WizardStep.LOADING,
                hardRefresh = previousStep == WizardStep.LOADING && step != WizardStep.LOADING
            )
        }
    }

    private fun forceStepRedraw(scrollToTop: Boolean, hardRefresh: Boolean = false) {
        rootContent.requestLayout()
        rootContent.invalidate()
        rootScrollView.requestLayout()
        rootScrollView.invalidate()
        window.decorView.invalidate()
        rootScrollView.post {
            if (hardRefresh) {
                rootContent.visibility = View.INVISIBLE
            }
            rootContent.requestLayout()
            rootContent.invalidate()
            window.decorView.invalidate()
            if (scrollToTop) {
                rootScrollView.scrollTo(0, 0)
            }
            if (hardRefresh) {
                rootContent.post {
                    rootContent.visibility = View.VISIBLE
                    rootContent.requestLayout()
                    rootContent.invalidate()
                    rootScrollView.requestLayout()
                    rootScrollView.invalidate()
                    window.decorView.invalidate()
                }
            }
        }
    }

    private fun startWifiWatcher() {
        wifiWatcherJob?.cancel()
        lastObservedWifiSsid = WifiInfoHelper.currentSsid(this)
        lastObservedWifiSubnetPrefix = WifiInfoHelper.currentSubnetPrefix(this)
        lastObservedNearbyRobotSsid = currentVisibleRobotSsid()
        wifiWatcherJob = uiScope.launch {
            while (isActive) {
                delay(1200L)
                maybeRequestFreshRobotScan()
                val currentSsid = WifiInfoHelper.currentSsid(this@MainActivity)
                val currentSubnetPrefix = WifiInfoHelper.currentSubnetPrefix(this@MainActivity)
                val nearbyRobotSsid = currentVisibleRobotSsid()
                if (
                    currentSsid == lastObservedWifiSsid &&
                    currentSubnetPrefix == lastObservedWifiSubnetPrefix &&
                    nearbyRobotSsid == lastObservedNearbyRobotSsid
                ) {
                    continue
                }
                val previousSsid = lastObservedWifiSsid
                val previousSubnetPrefix = lastObservedWifiSubnetPrefix
                val previousNearbyRobotSsid = lastObservedNearbyRobotSsid
                lastObservedWifiSsid = currentSsid
                lastObservedWifiSubnetPrefix = currentSubnetPrefix
                lastObservedNearbyRobotSsid = nearbyRobotSsid
                maybeReactToWifiChange(
                    previousSsid = previousSsid,
                    currentSsid = currentSsid,
                    previousSubnetPrefix = previousSubnetPrefix,
                    currentSubnetPrefix = currentSubnetPrefix,
                    previousNearbyRobotSsid = previousNearbyRobotSsid,
                    currentNearbyRobotSsid = nearbyRobotSsid,
                    fromWatcher = true
                )
            }
        }
    }

    private fun maybeRequestFreshRobotScan(force: Boolean = false) {
        if (!force && (awaitingRobotProvision || currentStep == WizardStep.LOADING)) {
            return
        }
        if (configStore.loadDraft().robotId.isBlank()) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastWifiScanRequestAt < 5000L) {
            return
        }
        if (WifiInfoHelper.requestFreshScanIfPossible(this)) {
            lastWifiScanRequestAt = now
        }
    }

    private fun currentVisibleRobotSsid(maxAgeMs: Long = 12_000L): String {
        val currentSsid = WifiInfoHelper.currentSsid(this)
        val acceptedPrefixes = RobotBranding.acceptedWifiPrefixes(robotWifiPrefix)
        val nearbyRobotSsid = WifiInfoHelper.nearbySsidByPrefixes(
            this,
            acceptedPrefixes,
            maxAgeMs = maxAgeMs,
        ).ifBlank {
            WifiInfoHelper.nearbySsidByPrefixesAnyAge(this, acceptedPrefixes)
        }
        return RobotConnectivityResolver.visibleRobotSsid(
            currentSsid = currentSsid,
            nearbyRobotSsid = nearbyRobotSsid,
            robotWifiPrefix = robotWifiPrefix,
        )
    }

    private fun maybeReactToWifiChange(
        previousSsid: String,
        currentSsid: String,
        previousSubnetPrefix: String,
        currentSubnetPrefix: String,
        previousNearbyRobotSsid: String,
        currentNearbyRobotSsid: String,
        fromWatcher: Boolean,
    ) {
        refreshWifiInfo()
        val draft = configStore.loadDraft()
        val visibilityDecision = RobotConnectivityResolver.resolve(
            currentSsid = currentSsid,
            nearbyRobotSsid = currentNearbyRobotSsid,
            robotWifiPrefix = robotWifiPrefix,
        )
        Log.d(
            logTag,
            "maybeReactToWifiChange(wifi=${previousSubnetPrefix.isNotBlank()} -> ${currentSubnetPrefix.isNotBlank()}, " +
                "robotSsidChanged=${previousSsid != currentSsid}, nearbyChanged=${previousNearbyRobotSsid != currentNearbyRobotSsid}, " +
                "step=$currentStep, awaiting=$awaitingRobotProvision, watcher=$fromWatcher)"
        )
        reportNetworkTransitionAsync(
            draft = draft,
            previousWifiAvailable = previousSubnetPrefix.isNotBlank(),
            currentWifiAvailable = currentSubnetPrefix.isNotBlank(),
            visibilityDecision = visibilityDecision,
            fromWatcher = fromWatcher,
        )

        val visibilityTransition = when (currentStep) {
            WizardStep.MENU -> OnboardingCoordinator.menuVisibility(
                decision = visibilityDecision,
                setupCompleted = draft.setupCompleted,
            )

            WizardStep.WIFI -> OnboardingCoordinator.visibility(
                decision = visibilityDecision,
                presentation = WifiPresentationMode.UPDATE_CURRENT_STEP,
            )

            else -> null
        }
        if (currentStep == WizardStep.MENU && draft.setupCompleted) {
            when (visibilityDecision.type) {
                RobotConnectivityDecisionType.PHONE_ON_ROBOT_WIFI -> {
                    reportPresenceAsync(MobilePresenceState.PHONE_ON_ROBOT_WIFI)
                }

                RobotConnectivityDecisionType.ROBOT_VISIBLE_NEARBY -> {
                    reportPresenceAsync(MobilePresenceState.ROBOT_HOTSPOT_VISIBLE)
                }

                else -> Unit
            }
        }
        if (!awaitingRobotProvision && visibilityTransition != null) {
            applyWifiStepTransition(visibilityTransition)
            return
        }

        if (awaitingRobotProvision && robotProvisionCheckJob?.isActive != true) {
            maybeContinueProvisionAfterReturn()
            return
        }

        val movedOffRobotWifi = RobotBranding.isRobotWifiSsid(previousSsid, robotWifiPrefix) &&
            !RobotBranding.isRobotWifiSsid(currentSsid, robotWifiPrefix)
        if (
            movedOffRobotWifi &&
            !awaitingRobotProvision &&
            currentStep == WizardStep.WIFI &&
            wifiBackToMenuMode &&
            resumeDiscoveryJob?.isActive != true &&
            robotProvisionCheckJob?.isActive != true &&
            configStore.loadDraft().robotId.isNotBlank()
        ) {
            resolveExistingRobotState()
            return
        }

        if (
            !awaitingRobotProvision &&
            currentStep == WizardStep.MENU &&
            draft.setupCompleted
        ) {
            applyImmediateMenuHint(draft)
            scheduleMenuStabilization(
                reason = if (fromWatcher) "wifi_change" else "resume_change"
            )
        }

        if (
            !awaitingRobotProvision &&
            currentStep == WizardStep.WIFI &&
            wifiBackToMenuMode &&
            draft.setupCompleted
        ) {
            refreshWifiReconnectStatus(forceScan = false)
        }
    }

    private fun activateByCode() {
        val code = etCode.text?.toString()?.trim().orEmpty()
        val ownerName = etOwnerName.text?.toString()?.trim().orEmpty()
        val ownerEmail = etOwnerEmail.text?.toString()?.trim().orEmpty()
        val ownerPhone = etOwnerPhone.text?.toString()?.trim().orEmpty()

        val ownerRequired = !BuildConfig.IS_ADMIN_APP
        val ownerReady = !ownerRequired || (
            ownerName.isNotBlank() &&
            ownerEmail.isNotBlank() &&
            ownerPhone.isNotBlank() &&
            cbPrivacyConsent.isChecked
        )
        if (code.isBlank() || !ownerReady) {
            toast(
                getString(
                    if (ownerRequired && !cbPrivacyConsent.isChecked) R.string.activation_consent_toast
                    else if (ownerRequired) R.string.activation_fill_client_toast
                    else R.string.activation_fill_admin_toast
                )
            )
            return
        }

        setStatus(getString(R.string.runtime_status_registering))
        uiScope.launch {
            try {
                val bundle = PanelApiClient.activateCode(httpClient, panelBaseUrl(), code, ownerName, ownerEmail, ownerPhone)
                currentBundle = bundle
                saveDraftLocally(bundle)
                persistDraft(
                    configStore.loadDraft().copy(
                        robotHost = "",
                        wifiReconnectPending = false,
                        setupCompleted = false,
                    )
                )
                lastPresenceSignature = ""
                stopConnectorService()
                tvCodeStatus.text = buildString {
                    append(getString(R.string.summary_label_robot))
                    append(": ")
                    append(bundle.robotName)
                    append("\n")
                    append(getString(R.string.summary_label_id))
                    append(": ")
                    append(bundle.robotId)
                    append("\n")
                    append(getString(R.string.summary_label_plan))
                    append(": ")
                    append(bundle.planName)
                    if (bundle.billingStart.isNotBlank() || bundle.billingEnd.isNotBlank()) {
                        append("\n")
                        append(getString(R.string.summary_label_period))
                        append(": ")
                        append(bundle.billingStart.ifBlank { getString(R.string.summary_value_not_specified) })
                        append(" - ")
                        append(bundle.billingEnd.ifBlank { getString(R.string.summary_value_not_specified) })
                    }
                    append("\n")
                    append(getString(R.string.summary_label_client))
                    append(": ")
                    append(bundle.ownerName)
                    append("\n")
                    append(getString(R.string.summary_label_email))
                    append(": ")
                    append(bundle.ownerEmail)
                    append("\n")
                    append(getString(R.string.summary_label_phone))
                    append(": ")
                    append(bundle.ownerPhone)
                }
                tvInstruction.text = getString(R.string.wifi_instruction_main)
                wifiBackToMenuMode = false
                toast(getString(R.string.activation_done_toast))
                showStep(WizardStep.WIFI)
            } catch (exc: Exception) {
                setStatus(getString(R.string.runtime_status_registration_failed))
                toast(getString(R.string.activation_failed_toast, exc.message ?: getString(R.string.menu_value_empty)))
            }
        }
    }

    private fun refreshWifiInfo() {
        val ssid = WifiInfoHelper.currentSsid(this)
        tvWifiName.text = if (ssid.isBlank()) {
            getString(R.string.wifi_state_phone_disconnected)
        } else {
            getString(R.string.wifi_state_phone_connected, ssid)
        }
        renderDiagnostics()
    }

    private suspend fun discoverRobotLocally(
        subnetPrefix: String,
        preferredHosts: List<String> = emptyList(),
        allowGenericSweep: Boolean = false,
        expectedDeviceId: String = "",
    ): Pair<String?, String> {
        Log.d(
            logTag,
            "discoverRobotLocally(subnet=$subnetPrefix, preferred=${preferredHosts.filter { it.isNotBlank() }}, " +
                "generic=$allowGenericSweep)"
        )
        val result = when (
            val run = LocalRobotProbeCoordinator.runMainActivitySearch(
                source = "MainActivity.discoverRobotLocally",
            ) {
                val freshServiceSuccess = LocalRobotProbeCoordinator.freshSuccessfulServiceHost(
                    subnetPrefix = subnetPrefix,
                    preferredHosts = preferredHosts,
                    expectedDeviceId = expectedDeviceId,
                )
                if (freshServiceSuccess != null) {
                    Log.d(
                        logTag,
                        "discoverRobotLocally reused service success host=${freshServiceSuccess.host} " +
                            "source=${freshServiceSuccess.source} ageMs=${freshServiceSuccess.ageMs}"
                    )
                    freshServiceSuccess.host to ""
                } else {
                    LocalRobotDiscovery.discover(
                        socketFactory = WifiInfoHelper.currentWifiNetwork(this)?.socketFactory,
                        subnetPrefix = subnetPrefix,
                        preferredHosts = preferredHosts,
                        allowGenericSweep = allowGenericSweep,
                        expectedDeviceId = expectedDeviceId,
                    )
                }
            }
        ) {
            is LocalRobotProbeRun.Executed -> run.value
            is LocalRobotProbeRun.Skipped -> {
                Log.d(
                    logTag,
                    "discoverRobotLocally skipped: ${run.reason} active=${run.activeSource} retryAfterMs=${run.retryAfterMs}"
                )
                null to getString(R.string.diagnostics_local_searching_robot_wifi)
            }
        }
        Log.d(
            logTag,
            "discoverRobotLocally result host=${result.first.orEmpty()} detail=${result.second}"
        )
        updateLocalDiagnostics(
            if (!result.first.isNullOrBlank()) {
                getString(R.string.diagnostics_local_found, result.first)
            } else {
                result.second.ifBlank { getString(R.string.diagnostics_empty) }
            }
        )
        return result
    }

    private fun cancelMenuStabilization() {
        menuStabilizationJob?.cancel()
        menuStabilizationJob = null
        menuStabilizationRunning = false
        queuedMenuStabilizationReason = ""
        resetMenuRecoveryRetry()
    }

    private fun resetMenuRecoveryRetry() {
        menuRecoveryRetryJob?.cancel()
        menuRecoveryRetryJob = null
        menuRecoveryRetryAttempt = 0
    }

    private fun scheduleMenuRecoveryRetry(
        recoveryNeeded: Boolean,
        hasHomeWifi: Boolean,
    ) {
        if (menuRecoveryRetryJob?.isActive == true) return
        val plan = MenuRecoveryRetryPolicy.next(
            completedAttempts = menuRecoveryRetryAttempt,
            recoveryNeeded = recoveryNeeded,
            hasHomeWifi = hasHomeWifi,
        )
        if (plan == null) {
            if (
                MenuRecoveryRetryPolicy.exhausted(
                    completedAttempts = menuRecoveryRetryAttempt,
                    recoveryNeeded = recoveryNeeded,
                    hasHomeWifi = hasHomeWifi,
                )
            ) {
                timeoutWifiRecoveryAsync()
            }
            return
        }
        menuRecoveryRetryAttempt = plan.attempt
        menuRecoveryRetryJob = uiScope.launch {
            delay(plan.delayMs)
            menuRecoveryRetryJob = null
            val draft = configStore.loadDraft()
            if (
                currentStep == WizardStep.MENU &&
                !awaitingRobotProvision &&
                draft.setupCompleted &&
                draft.robotId.isNotBlank() &&
                WifiInfoHelper.currentSubnetPrefix(this@MainActivity).isNotBlank()
            ) {
                reportWifiRecoveryRetryAsync(plan.attempt)
                scheduleMenuStabilization(reason = "wifi_recovery_retry_${plan.attempt}", delayMs = 0L)
            }
        }
    }

    private fun preferredDiscoveryHosts(
        draft: OnboardingDraft = configStore.loadDraft(),
        extraHosts: List<String> = emptyList(),
    ): List<String> {
        return buildList {
            add(draft.robotHost)
            addAll(extraHosts)
        }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun applyImmediateMenuHint(draft: OnboardingDraft = configStore.loadDraft()) {
        if (currentStep != WizardStep.MENU || awaitingRobotProvision || !draft.setupCompleted) {
            return
        }

        val currentSsid = WifiInfoHelper.currentSsid(this)
        val visibleRobotSsid = currentVisibleRobotSsid(maxAgeMs = 8_000L)
        val message = when {
            RobotBranding.isRobotWifiSsid(currentSsid, robotWifiPrefix) ->
                getString(R.string.menu_status_phone_on_robot_wifi, currentSsid)

            visibleRobotSsid.isNotBlank() ->
                getString(R.string.menu_status_robot_visible, visibleRobotSsid)

            WifiInfoHelper.currentSubnetPrefix(this).isBlank() ->
                getString(R.string.menu_status_no_home_wifi)

            draft.robotHost.isNotBlank() ->
                getString(R.string.menu_status_checking_saved_host, draft.robotHost)

            else ->
                getString(R.string.menu_status_checking)
        }
        setStatus(message)
    }

    private suspend fun resolvePanelAndLocalReachability(
        draft: OnboardingDraft,
        subnetPrefix: String,
        allowGenericSweep: Boolean = false,
        savedDeviceIdIsAuthoritative: Boolean = true,
        panelDeviceIdHint: String = "",
        panelLocalHostHint: String = "",
    ): ReachabilityCheckResult = coroutineScope {
        val panelDeferred = async { resolveRobotViaPanel() }
        if (subnetPrefix.isBlank()) {
            return@coroutineScope ReachabilityCheckResult(
                panelSnapshot = panelDeferred.await(),
                localHost = null,
                localDeviceId = "",
            )
        }
        val localDeferred = if (subnetPrefix.isNotBlank()) {
            async {
                // Локальную проверку ведем через Wi-Fi-привязанный клиент,
                // поэтому не прерываем ее только из-за общего VPN на телефоне.
                val panelSnapshotHint = withTimeoutOrNull(900L) {
                    panelDeferred.await()
                }
                val panelHostHint = panelSnapshotHint?.localHostHint.orEmpty().ifBlank { panelLocalHostHint }
                val expectedDeviceId = resolveDiscoveryExpectedDeviceIdentity(
                    panelSnapshot = panelSnapshotHint,
                    draft = draft,
                    bundleDeviceId = currentBundle?.selfhostXiaozhi?.deviceId,
                    savedDeviceIdIsAuthoritative = savedDeviceIdIsAuthoritative,
                    panelDeviceIdHint = panelDeviceIdHint,
                )
                if (!expectedDeviceId.canVerify) {
                    val detail = expectedDeviceId.conflictReason.ifBlank { "expected_device_id_missing" }
                    updateLocalDiagnostics(detail)
                    renderDiagnostics()
                    return@async Triple(null, detail, "")
                }
                val (host, detail) = discoverRobotLocally(
                    subnetPrefix,
                    preferredDiscoveryHosts(draft, listOf(panelHostHint)),
                    allowGenericSweep = allowGenericSweep,
                    expectedDeviceId = expectedDeviceId.deviceId,
                )
                Triple(host, detail, if (!host.isNullOrBlank()) expectedDeviceId.deviceId else "")
            }
        } else {
            null
        }

        val localResult = localDeferred?.await()
        val localHost = localResult?.first
        val localDeviceId = localResult?.third.orEmpty()
        val panelSnapshot = when {
            !localHost.isNullOrBlank() && !panelDeferred.isCompleted ->
                withTimeoutOrNull(150L) { panelDeferred.await() }

            else -> panelDeferred.await()
        }
        val finalExpectedDeviceId = resolveDiscoveryExpectedDeviceIdentity(
            panelSnapshot = panelSnapshot,
            draft = draft,
            bundleDeviceId = currentBundle?.selfhostXiaozhi?.deviceId,
            savedDeviceIdIsAuthoritative = savedDeviceIdIsAuthoritative,
            panelDeviceIdHint = panelDeviceIdHint,
        )
        val identityMismatch = finalExpectedDeviceId.hasConflict ||
            (
                !localHost.isNullOrBlank() &&
                    finalExpectedDeviceId.deviceId.isNotBlank() &&
                    localDeviceId.isNotBlank() &&
                    finalExpectedDeviceId.deviceId != localDeviceId
                )
        val finalLocalHost = if (identityMismatch) {
            val detail = finalExpectedDeviceId.conflictReason.ifBlank { "device identity mismatch" }
            updateLocalDiagnostics(detail)
            renderDiagnostics()
            null
        } else {
            localHost
        }

        ReachabilityCheckResult(
            panelSnapshot = panelSnapshot,
            localHost = finalLocalHost,
            localDeviceId = if (finalLocalHost.isNullOrBlank()) "" else localDeviceId,
        )
    }

    private fun applyConnectedWifiStepState(host: String, verifiedDeviceId: String = "") {
        if (!saveDraftLocally(discoveredHost = host, discoveredDeviceId = verifiedDeviceId)) {
            return
        }
        val updatedDraft = configStore.loadDraft().copy(
            wifiReconnectPending = false,
            setupCompleted = true,
        )
        persistDraft(updatedDraft)
        updateLocalDiagnostics(getString(R.string.diagnostics_local_found, host))
        renderDiagnostics()
        tvRobotCheck.text = getString(R.string.wifi_reconnect_status_found)
        setStatus(getString(R.string.menu_status_connected_host, host))
        reportPresenceAsync(MobilePresenceState.HOME_WIFI_LOCAL, localHost = host)
        syncConnectorService(updatedDraft)
    }

    private fun applyPanelOnlyWifiStepState() {
        val updatedDraft = configStore.loadDraft().copy(
            robotHost = "",
            wifiReconnectPending = false,
            setupCompleted = true,
        )
        persistDraft(updatedDraft)
        updateLocalDiagnostics(getString(R.string.diagnostics_local_platform_only))
        diagnosticsPanel = getString(R.string.diagnostics_panel_connected_platform)
        renderDiagnostics()
        tvRobotCheck.text = getString(R.string.wifi_reconnect_status_platform_only)
        setStatus(getString(R.string.menu_status_platform_only))
        stopConnectorService()
    }

    private fun refreshWifiReconnectStatus(forceScan: Boolean = true) {
        val draft = configStore.loadDraft()
        if (!wifiBackToMenuMode || draft.robotId.isBlank() || awaitingRobotProvision || pendingRobotWifiConnection) {
            return
        }

        wifiStepStatusJob?.cancel()
        if (draft.robotHost.isNotBlank()) {
            tvRobotCheck.text = getString(R.string.wifi_reconnect_status_checking_saved_host, draft.robotHost)
            setStatus(getString(R.string.menu_status_checking_saved_host, draft.robotHost))
        } else {
            tvRobotCheck.text = getString(R.string.wifi_reconnect_status_checking)
            setStatus(getString(R.string.menu_status_checking))
        }

        wifiStepStatusJob = uiScope.launch {
            val currentSsid = WifiInfoHelper.currentSsid(this@MainActivity)
            if (RobotBranding.isRobotWifiSsid(currentSsid, robotWifiPrefix)) {
                updateLocalDiagnostics(getString(R.string.diagnostics_local_wait_robot_wifi, currentSsid))
                diagnosticsPanel = getString(R.string.diagnostics_panel_skipped_robot_wifi)
                renderDiagnostics()
                tvRobotCheck.text = getString(R.string.wifi_continue_on_robot_network)
                setStatus(getString(R.string.menu_status_phone_on_robot_wifi, currentSsid))
                reportPresenceAsync(MobilePresenceState.PHONE_ON_ROBOT_WIFI)
                return@launch
            }

            val visibleRobotSsid = currentVisibleRobotSsid(maxAgeMs = 8_000L)
            if (visibleRobotSsid.isNotBlank()) {
                updateLocalDiagnostics(getString(R.string.diagnostics_local_robot_visible_nearby, visibleRobotSsid))
                diagnosticsPanel = getString(R.string.diagnostics_panel_skipped_robot_visible, visibleRobotSsid)
                renderDiagnostics()
                tvRobotCheck.text = getString(R.string.wifi_robot_visible_nearby, visibleRobotSsid)
                setStatus(getString(R.string.menu_status_robot_visible, visibleRobotSsid))
                reportPresenceAsync(MobilePresenceState.ROBOT_HOTSPOT_VISIBLE)
                return@launch
            }

            if (forceScan) {
                maybeRequestFreshRobotScan(force = true)
            }

            val subnetPrefix = WifiInfoHelper.currentSubnetPrefix(this@MainActivity)
            if (subnetPrefix.isBlank()) {
                updateLocalDiagnostics(getString(R.string.diagnostics_local_wait_home_wifi))
                diagnosticsPanel = getString(R.string.diagnostics_panel_skipped_no_internet)
                renderDiagnostics()
                tvRobotCheck.text = getString(R.string.wifi_reconnect_status_no_home_wifi)
                setStatus(getString(R.string.menu_status_no_home_wifi))
                return@launch
            }

            if (draft.robotHost.isNotBlank()) {
                tvRobotCheck.text = getString(R.string.wifi_reconnect_status_checking_saved_host, draft.robotHost)
                setStatus(getString(R.string.menu_status_checking_saved_host, draft.robotHost))
            } else {
                tvRobotCheck.text = getString(R.string.wifi_reconnect_status_checking_network)
                setStatus(getString(R.string.menu_status_checking))
            }

            val reachability = resolvePanelAndLocalReachability(draft, subnetPrefix)
            val panelDecision = RobotConnectivityResolver.resolveVerifiedReachability(
                panelSnapshot = reachability.panelSnapshot,
                verifiedLocalHost = reachability.localHost.orEmpty(),
            )
            when (panelDecision.type) {
                RobotConnectivityDecisionType.CONNECTED_LOCALLY -> {
                    val host = panelDecision.localHost.ifBlank { reachability.localHost.orEmpty() }
                    if (host.isNotBlank()) {
                        applyConnectedWifiStepState(host, verifiedDeviceId = reachability.localDeviceId)
                        return@launch
                    }
                }

                RobotConnectivityDecisionType.CONNECTED_VIA_PANEL -> {
                    applyPanelOnlyWifiStepState()
                    return@launch
                }

                else -> Unit
            }

            if (NetworkStateHelper.isVpnActive(this@MainActivity)) {
                updateLocalDiagnostics(getString(R.string.diagnostics_local_vpn_active))
                diagnosticsPanel = getString(R.string.diagnostics_panel_connected_platform)
                renderDiagnostics()
                tvRobotCheck.text = getString(R.string.wifi_reconnect_status_vpn_active)
                setStatus(getString(R.string.menu_status_vpn_active))
                return@launch
            }

            val refreshedVisibleRobotSsid = findVisibleRobotSsid(attempts = 1, delayMs = 0L)
            if (refreshedVisibleRobotSsid.isNotBlank()) {
                updateLocalDiagnostics(
                    getString(R.string.diagnostics_local_robot_visible_nearby, refreshedVisibleRobotSsid)
                )
                diagnosticsPanel = getString(
                    R.string.diagnostics_panel_skipped_robot_visible,
                    refreshedVisibleRobotSsid
                )
                renderDiagnostics()
                tvRobotCheck.text = getString(R.string.wifi_robot_visible_nearby, refreshedVisibleRobotSsid)
                setStatus(getString(R.string.menu_status_robot_visible, refreshedVisibleRobotSsid))
                reportPresenceAsync(MobilePresenceState.ROBOT_HOTSPOT_VISIBLE)
                return@launch
            }

            tvRobotCheck.text = getString(R.string.wifi_reconnect_status_not_found)
            setStatus(getString(R.string.menu_status_robot_missing))
            reportPresenceAsync(MobilePresenceState.NOT_FOUND)
        }
    }

    private fun provisionRobotToWifi() {
        wifiStepStatusJob?.cancel()
        wifiStepStatusJob = null
        if (NetworkStateHelper.isVpnActive(this)) {
            tvRobotCheck.text = getString(R.string.wifi_vpn_enabled_text)
            setStatus(getString(R.string.wifi_vpn_enabled_status))
            toast(getString(R.string.wifi_vpn_enabled_toast))
            return
        }

        if (!RobotWifiConnector.hasRequiredPermissions(this)) {
            requestRobotWifiPermissions(PermissionRequestPurpose.PROVISION)
            return
        }

        val currentSsid = WifiInfoHelper.currentSsid(this)
        if (RobotBranding.isRobotWifiSsid(currentSsid, robotWifiPrefix)) {
            tvRobotCheck.text = getString(R.string.wifi_phone_on_robot_text)
            setStatus(getString(R.string.wifi_phone_on_robot_status))
            startRobotWifiPortal()
            return
        }

        val subnetPrefix = WifiInfoHelper.currentSubnetPrefix(this)
        if (subnetPrefix.isBlank()) {
            tvRobotCheck.text = getString(R.string.wifi_need_home_wifi_text)
            setStatus(getString(R.string.wifi_need_home_wifi_status))
            toast(getString(R.string.wifi_need_home_wifi_toast))
            return
        }

        if (resumeDiscoveryJob?.isActive == true) {
            tvRobotCheck.text = getString(R.string.wifi_search_already_running)
            return
        }

        showLoadingState(
            title = getString(R.string.loading_title_robot_wifi),
            body = getString(R.string.loading_body_robot_wifi)
        )
        updateLocalDiagnostics(getString(R.string.diagnostics_local_searching_robot_wifi))
        diagnosticsPanel = getString(R.string.diagnostics_panel_skipped_robot_wifi_search)
        renderDiagnostics()
        setStatus(getString(R.string.runtime_status_searching_robot_wifi))
        resumeDiscoveryJob = uiScope.launch {
            val draft = configStore.loadDraft()
            val visibleRobotSsid = findVisibleRobotSsid(attempts = 2, delayMs = 650L)
            if (visibleRobotSsid.isNotBlank()) {
                updateLocalDiagnostics(getString(R.string.diagnostics_local_robot_visible_nearby, visibleRobotSsid))
                diagnosticsPanel = getString(R.string.diagnostics_panel_skipped_robot_visible, visibleRobotSsid)
                renderDiagnostics()
                reportPresenceAsync(MobilePresenceState.ROBOT_HOTSPOT_VISIBLE)
                showLoadingState(
                    title = getString(R.string.loading_title_open_robot_wifi),
                    body = getString(R.string.loading_body_open_robot_wifi, visibleRobotSsid)
                )
                setStatus(getString(R.string.wifi_connecting_robot_status))
                ensureRobotWifiPermissionsAndConnect(visibleRobotSsid)
                return@launch
            }

            val reachability = resolvePanelAndLocalReachability(
                draft,
                subnetPrefix,
                allowGenericSweep = true,
            )
            if (
                maybeHandleRobotViaPanel(
                    snapshot = reachability.panelSnapshot,
                    messagePrefix = getString(R.string.wifi_robot_already_connected_panel),
                    verifiedLocalHost = reachability.localHost.orEmpty(),
                    verifiedLocalDeviceId = reachability.localDeviceId,
                )
            ) {
                return@launch
            }
            applyWifiStepTransition(OnboardingCoordinator.reconnectWaiting())
        }
    }

    private fun maybeHandleRobotFound(
        host: String,
        statusText: String,
        toastText: String? = null,
        verifiedDeviceId: String = "",
        savedDeviceIdIsAuthoritative: Boolean = true,
    ) {
        awaitingRobotProvision = false
        robotWifiPortalSubmitted = false
        pendingRobotWifiConnection = false
        robotWifiConnectTimeoutJob?.cancel()
        wifiStepStatusJob?.cancel()
        wifiStepStatusJob = null
        cancelMenuStabilization()
        wifiBackToMenuMode = false
        if (
            !saveDraftLocally(
                discoveredHost = host,
                discoveredDeviceId = verifiedDeviceId,
                savedDeviceIdIsAuthoritative = savedDeviceIdIsAuthoritative,
            )
        ) {
            return
        }
        val updatedDraft = configStore.loadDraft().copy(
            wifiReconnectPending = false,
            setupCompleted = true,
        )
        persistDraft(updatedDraft)
        tvRobotCheck.text = statusText
        tvSuccessMessage.text = getString(R.string.wifi_robot_found_success)
        showMenuWithStatus(getString(R.string.menu_status_connected_host, host))
        completeWifiRecoveryAsync(updatedDraft)
        reportPresenceAsync(MobilePresenceState.HOME_WIFI_LOCAL, localHost = host)
        syncConnectorService(updatedDraft)
        toastText?.let(::toast)
    }

    private fun maybeHandleRobotMissing(message: String) {
        awaitingRobotProvision = false
        robotWifiPortalSubmitted = false
        pendingRobotWifiConnection = false
        robotWifiConnectTimeoutJob?.cancel()
        wifiStepStatusJob?.cancel()
        wifiStepStatusJob = null
        cancelMenuStabilization()
        persistDraft(configStore.loadDraft().copy(robotHost = ""))
        wifiBackToMenuMode = configStore.loadDraft().robotId.isNotBlank()
        tvInstruction.text = getString(
            if (wifiBackToMenuMode) R.string.wifi_instruction_reconnect
            else R.string.wifi_instruction_main
        )
        showStep(WizardStep.WIFI)
        setStatus(getString(R.string.runtime_status_robot_missing))
        tvRobotCheck.text = message
        reportPresenceAsync(MobilePresenceState.NOT_FOUND)
    }

    private fun applyPanelOnlyProvisionFinal(localHostHint: String = "") {
        awaitingRobotProvision = false
        robotWifiPortalSubmitted = false
        pendingRobotWifiConnection = false
        robotWifiConnectTimeoutJob?.cancel()
        wifiStepStatusJob?.cancel()
        wifiStepStatusJob = null
        cancelMenuStabilization()
        wifiBackToMenuMode = false
        val updatedDraft = configStore.loadDraft().copy(
            robotHost = "",
            wifiReconnectPending = false,
            setupCompleted = true,
        )
        persistDraft(updatedDraft)
        tvRobotCheck.text = getString(R.string.wifi_robot_local_address_not_confirmed)
        tvSuccessMessage.text = getString(R.string.wifi_robot_local_address_not_confirmed)
        updateLocalDiagnostics(
            if (localHostHint.isNotBlank()) {
                getString(R.string.diagnostics_local_platform_only_final_with_hint, localHostHint)
            } else {
                getString(R.string.diagnostics_local_platform_only_final)
            }
        )
        diagnosticsPanel = getString(R.string.diagnostics_panel_connected_platform)
        renderDiagnostics()
        showMenuWithStatus(getString(R.string.menu_status_platform_local_unconfirmed))
        stopConnectorService()
    }

    private suspend fun resolveRobotViaPanel(): RobotRuntimeSnapshot? {
        val robotId = configStore.loadDraft().robotId
        if (robotId.isBlank()) {
            diagnosticsPanel = getString(R.string.diagnostics_panel_robot_id_empty)
            renderDiagnostics()
            return null
        }
        return try {
            val draft = configStore.loadDraft()
            PanelApiClient.fetchRobotRuntime(
                http = panelStatusHttpClient,
                baseUrl = panelBaseUrl(),
                robotId = robotId,
                panelClientToken = draft.panelClientToken,
                onboardingCode = draft.onboardingCode,
            ).also {
                updatePanelDiagnostics(it)
            }
        } catch (exc: Exception) {
            updatePanelDiagnostics(null, exc.message ?: getString(R.string.menu_value_empty))
            null
        }
    }

    private suspend fun maybeCompleteViaPanel(statusText: String): Boolean {
        return maybeHandleRobotViaPanel(resolveRobotViaPanel(), statusText)
    }

    private fun maybeHandleRobotViaPanel(
        snapshot: RobotRuntimeSnapshot?,
        messagePrefix: String,
        verifiedLocalHost: String = "",
        verifiedLocalDeviceId: String = "",
    ): Boolean {
        val decision = RobotConnectivityResolver.resolveVerifiedReachability(
            panelSnapshot = snapshot,
            verifiedLocalHost = verifiedLocalHost,
        )
        val connectedTransition = OnboardingCoordinator.connectedMenu(decision) ?: return false
        return applyConnectedMenuTransition(
            transition = connectedTransition,
            statusText = messagePrefix,
            toastText = getString(R.string.wifi_robot_already_connected_toast),
            verifiedDeviceId = verifiedLocalDeviceId,
        )
    }

    private fun provisionPanelSignalPlan(snapshot: RobotRuntimeSnapshot?): ProvisionPanelSignalPlan {
        val decision = RobotConnectivityResolver.resolveVerifiedReachability(
            panelSnapshot = snapshot,
            verifiedLocalHost = "",
        )
        return ProvisionCoordinator.planPanelSignal(decision)
    }

    private fun visibleRobotDecision(currentSsid: String, nearbyRobotSsid: String): RobotConnectivityDecision {
        return RobotConnectivityResolver.resolve(
            currentSsid = currentSsid,
            nearbyRobotSsid = nearbyRobotSsid,
            robotWifiPrefix = robotWifiPrefix,
        )
    }

    private fun requestRobotWifiPermissions(purpose: PermissionRequestPurpose) {
        pendingRobotWifiPermissionPurpose = purpose
        applyPermissionRequestTransition(OnboardingCoordinator.permissionRequest(purpose))
        robotWifiPermissionsLauncher.launch(RobotWifiConnector.requiredPermissions())
    }

    private fun applyPermissionRequestTransition(transition: PermissionRequestTransition) {
        when (transition.presentation) {
            PermissionRequestPresentation.WIFI_STEP -> showStep(WizardStep.WIFI)
            PermissionRequestPresentation.LOADING -> showLoadingState(
                title = getString(R.string.loading_title_search),
                body = getString(R.string.wifi_permission_request_text)
            )
        }
        tvRobotCheck.text = getString(R.string.wifi_permission_request_text)
        updateLocalDiagnostics(getString(R.string.diagnostics_local_permission_required))
        diagnosticsPanel = getString(R.string.diagnostics_panel_skipped_permission_required)
        renderDiagnostics()
        setStatus(getString(R.string.wifi_permission_request_status))
    }

    private fun applyWifiStepTransition(transition: WifiStepTransition) {
        when (transition.instructionMode) {
            WifiInstructionMode.KEEP_CURRENT -> Unit
            WifiInstructionMode.MAIN -> tvInstruction.text = getString(R.string.wifi_instruction_main)
            WifiInstructionMode.RECONNECT -> tvInstruction.text = getString(R.string.wifi_instruction_reconnect)
        }

        val message = when (transition.messageKind) {
            WifiMessageKind.CONTINUE_ON_ROBOT_NETWORK -> getString(R.string.wifi_continue_on_robot_network)
            WifiMessageKind.ROBOT_VISIBLE_NEARBY -> getString(R.string.wifi_robot_visible_nearby, transition.robotSsid)
            WifiMessageKind.RECONNECT_WAIT_MODE -> getString(R.string.wifi_reconnect_wait_mode)
            WifiMessageKind.CONNECT_TIMEOUT_RETRY -> getString(R.string.wifi_connect_timeout_retry)
        }
        val localDiagnostics = when (transition.localDiagnosticsKind) {
            LocalDiagnosticsKind.WAIT_ROBOT_WIFI -> getString(
                R.string.diagnostics_local_wait_robot_wifi,
                transition.robotSsid.ifBlank { RobotBranding.displayWifiHint(robotWifiPrefix) }
            )

            LocalDiagnosticsKind.ROBOT_VISIBLE_NEARBY -> getString(
                R.string.diagnostics_local_robot_visible_nearby,
                transition.robotSsid
            )

            LocalDiagnosticsKind.ROBOT_WIFI_HIDDEN -> getString(R.string.diagnostics_local_robot_wifi_hidden)
        }
        val panelDiagnosticsText = when (transition.panelDiagnosticsKind) {
            PanelDiagnosticsKind.SKIPPED_ROBOT_WIFI -> getString(R.string.diagnostics_panel_skipped_robot_wifi)
            PanelDiagnosticsKind.SKIPPED_ROBOT_VISIBLE -> getString(
                R.string.diagnostics_panel_skipped_robot_visible,
                transition.robotSsid
            )

            PanelDiagnosticsKind.SKIPPED_RECONNECT_PENDING -> getString(R.string.diagnostics_panel_skipped_reconnect_pending)
        }

        if (transition.presentation == WifiPresentationMode.OPEN_RECONNECT_STEP) {
            openWifiReconnectStep(
                message = message,
                localDiagnostics = localDiagnostics,
                panelDiagnosticsText = panelDiagnosticsText,
            )
            return
        }

        tvRobotCheck.text = message
        updateLocalDiagnostics(localDiagnostics)
        diagnosticsPanel = panelDiagnosticsText
        renderDiagnostics()
        when (transition.messageKind) {
            WifiMessageKind.CONTINUE_ON_ROBOT_NETWORK ->
                reportPresenceAsync(MobilePresenceState.PHONE_ON_ROBOT_WIFI)

            WifiMessageKind.ROBOT_VISIBLE_NEARBY ->
                reportPresenceAsync(MobilePresenceState.ROBOT_HOTSPOT_VISIBLE)

            else -> Unit
        }
    }

    private fun applyConnectedMenuTransition(
        transition: ConnectedMenuTransition,
        statusText: String,
        toastText: String? = null,
        preserveMenuRecoveryRetry: Boolean = false,
        verifiedDeviceId: String = "",
    ): Boolean {
        return when (transition.route) {
            ConnectedMenuRoute.LOCAL_HOST -> {
                updateLocalDiagnostics(getString(R.string.diagnostics_local_found, transition.localHost))
                diagnosticsPanel = getString(R.string.diagnostics_panel_connected_host, transition.localHost)
                renderDiagnostics()
                maybeHandleRobotFound(
                    host = transition.localHost,
                    statusText = statusText,
                    toastText = toastText,
                    verifiedDeviceId = verifiedDeviceId,
                )
                true
            }

            ConnectedMenuRoute.PANEL_ONLY -> {
                awaitingRobotProvision = false
                pendingRobotWifiConnection = false
                robotWifiConnectTimeoutJob?.cancel()
                if (!preserveMenuRecoveryRetry) {
                    cancelMenuStabilization()
                }
                wifiBackToMenuMode = false
                val hasHomeWifi = WifiInfoHelper.currentSubnetPrefix(this).isNotBlank()
                val updatedDraft = configStore.loadDraft().copy(
                    robotHost = "",
                    wifiReconnectPending = false,
                    setupCompleted = true,
                )
                persistDraft(updatedDraft)
                if (hasHomeWifi) {
                    tvRobotCheck.text = getString(R.string.wifi_robot_resolving_address)
                    tvSuccessMessage.text = getString(R.string.wifi_robot_connected_platform)
                    updateLocalDiagnostics(getString(R.string.diagnostics_local_platform_only))
                } else {
                    tvRobotCheck.text = getString(R.string.wifi_robot_wait_home_wifi)
                    tvSuccessMessage.text = getString(R.string.wifi_robot_wait_home_wifi)
                    updateLocalDiagnostics(getString(R.string.diagnostics_local_wait_home_wifi))
                    WifiInfoHelper.requestFreshScanIfPossible(this)
                }
                diagnosticsPanel = getString(R.string.diagnostics_panel_connected_platform)
                renderDiagnostics()
                showMenuWithStatus(
                    if (hasHomeWifi) {
                        getString(R.string.menu_status_platform_only)
                    } else {
                        getString(R.string.menu_status_platform_wait_home_wifi)
                    }
                )
                stopConnectorService()
                toastText?.let(::toast)
                true
            }
        }
    }

    private fun openWifiReconnectStep(
        message: String,
        localDiagnostics: String,
        panelDiagnosticsText: String,
    ) {
        awaitingRobotProvision = false
        pendingRobotWifiConnection = false
        robotWifiConnectTimeoutJob?.cancel()
        wifiBackToMenuMode = configStore.loadDraft().robotId.isNotBlank()
        showStep(WizardStep.WIFI)
        tvInstruction.text = getString(R.string.wifi_instruction_reconnect)
        tvRobotCheck.text = message
        updateLocalDiagnostics(localDiagnostics)
        diagnosticsPanel = panelDiagnosticsText
        renderDiagnostics()
        setStatus(getString(R.string.runtime_status_reconnect_open))
        stopConnectorService()
        refreshWifiReconnectStatus()
    }

    private fun handleRobotWifiPortalCancelledBeforeSubmit() {
        if (!awaitingRobotProvision) {
            return
        }
        RobotWifiConnector.release()
        openWifiReconnectStep(
            message = getString(R.string.wifi_reconnect_hint),
            localDiagnostics = getString(R.string.diagnostics_local_reconnect_pending),
            panelDiagnosticsText = getString(R.string.diagnostics_panel_skipped_reconnect_pending),
        )
    }

    private suspend fun findVisibleRobotSsid(attempts: Int = 3, delayMs: Long = 1200L): String {
        repeat(attempts) { index ->
            val currentSsid = WifiInfoHelper.currentSsid(this)
            if (RobotBranding.isRobotWifiSsid(currentSsid, robotWifiPrefix)) {
                return currentSsid
            }

            val acceptedPrefixes = RobotBranding.acceptedWifiPrefixes(robotWifiPrefix)
            val nearby = WifiInfoHelper.nearbySsidByPrefixes(
                this,
                acceptedPrefixes,
            ).ifBlank {
                WifiInfoHelper.nearbySsidByPrefixesAnyAge(this, acceptedPrefixes)
            }
            if (nearby.isNotBlank()) {
                return nearby
            }

            if (index < attempts - 1) {
                WifiInfoHelper.requestFreshScanIfPossible(this)
                delay(delayMs)
            }
        }
        return ""
    }

    private fun maybeContinueProvisionAfterReturn() {
        val shouldStartReturnCheck = ProvisionCoordinator.shouldStartReturnCheck(
            awaitingRobotProvision = awaitingRobotProvision,
            robotWifiPortalActive = robotWifiPortalActive,
            returnCheckRunning = robotProvisionCheckJob?.isActive == true,
            portalSubmitted = robotWifiPortalSubmitted,
        )
        if (!shouldStartReturnCheck) {
            if (robotWifiPortalActive) {
                Log.d(logTag, "Post-provision check deferred while Robot Wi-Fi portal is active")
            }
            return
        }

        showLoadingState(
            title = getString(R.string.loading_title_after_portal),
            body = getString(R.string.loading_body_after_portal)
        )
        setStatus(getString(R.string.runtime_status_searching))

        robotProvisionCheckJob = uiScope.launch {
            val totalAttempts = 24
            val settleAttempts = 3
            var provisionPanelState = ProvisionPanelState(
                panelOnlySeen = false,
                localHostHint = "",
                deviceIdHint = "",
            )
            repeat(totalAttempts) { index ->
                val currentSsid = WifiInfoHelper.currentSsid(this@MainActivity)
                val subnetPrefix = WifiInfoHelper.currentSubnetPrefix(this@MainActivity)
                val onRobotWifi = RobotBranding.isRobotWifiSsid(currentSsid, robotWifiPrefix)
                if (!onRobotWifi) {
                    maybeRequestFreshRobotScan(force = index == 0 || index % 3 == 0)
                }
                val visibleRobotSsid = currentVisibleRobotSsid(
                    maxAgeMs = if (onRobotWifi) 12_000L else 8_000L
                )
                Log.d(
                    logTag,
                    "maybeContinueProvisionAfterReturn(attempt=$index, ssid=$currentSsid, visible=$visibleRobotSsid, subnet=$subnetPrefix)"
                )
                when (
                    val plan = ProvisionCoordinator.planAttempt(
                        index = index,
                        totalAttempts = totalAttempts,
                        settleAttempts = settleAttempts,
                        currentSsid = currentSsid,
                        visibleRobotSsid = visibleRobotSsid,
                        hasHomeSubnet = subnetPrefix.isNotBlank(),
                        robotWifiPrefix = robotWifiPrefix,
                    )
                ) {
                    is ProvisionAttemptPlan.WaitOnRobotWifi -> {
                        updateLocalDiagnostics(getString(R.string.diagnostics_local_wait_robot_wifi, currentSsid))
                        diagnosticsPanel = getString(R.string.diagnostics_panel_skipped_robot_wifi)
                        renderDiagnostics()
                        if (plan.showManualSwitchHint) {
                            RobotWifiConnector.release()
                            updateProvisionProgress(getString(R.string.wifi_wait_robot_switch_manual))
                        } else {
                            updateProvisionProgress(getString(R.string.wifi_wait_robot_switch))
                        }
                        delay(1200)
                        return@repeat
                    }

                    is ProvisionAttemptPlan.WaitForHomeWifi -> {
                        if (plan.shouldCheckPanel) {
                            val panelSnapshot = resolveRobotViaPanel()
                            when (val panelPlan = provisionPanelSignalPlan(panelSnapshot)) {
                                is ProvisionPanelSignalPlan.CompleteWithLocalHost -> {
                                    maybeHandleRobotFound(
                                        host = panelPlan.localHost,
                                        statusText = getString(R.string.wifi_robot_found_network),
                                        toastText = getString(R.string.wifi_robot_already_connected_toast),
                                    )
                                    return@launch
                                }

                                is ProvisionPanelSignalPlan.ContinueLocalDiscovery -> {
                                    provisionPanelState = provisionPanelState.copy(
                                        panelOnlySeen = true,
                                        localHostHint = panelPlan.localHostHint.ifBlank {
                                            provisionPanelState.localHostHint
                                        },
                                        deviceIdHint = panelSnapshot?.deviceId.orEmpty()
                                            .ifBlank { provisionPanelState.deviceIdHint },
                                    )
                                }

                                ProvisionPanelSignalPlan.NoPanelSignal -> Unit
                            }
                        }
                        updateLocalDiagnostics(getString(R.string.diagnostics_local_wait_home_wifi))
                        diagnosticsPanel = if (provisionPanelState.panelOnlySeen) {
                            getString(R.string.diagnostics_panel_connected_platform)
                        } else {
                            getString(R.string.diagnostics_panel_skipped_no_internet)
                        }
                        renderDiagnostics()
                        updateProvisionProgress(getString(R.string.wifi_wait_phone_return))
                        delay(1200)
                        return@repeat
                    }

                    is ProvisionAttemptPlan.SettleOnHomeWifi -> {
                        if (plan.shouldCheckPanelBeforeWait) {
                            updateLocalDiagnostics(getString(R.string.diagnostics_local_settling))
                            diagnosticsPanel = getString(R.string.diagnostics_waiting)
                            renderDiagnostics()
                            updateProvisionProgress(getString(R.string.loading_body_check))
                            val panelSnapshot = resolveRobotViaPanel()
                            when (val panelPlan = provisionPanelSignalPlan(panelSnapshot)) {
                                is ProvisionPanelSignalPlan.CompleteWithLocalHost -> {
                                    maybeHandleRobotFound(
                                        host = panelPlan.localHost,
                                        statusText = getString(R.string.wifi_robot_found_network),
                                        toastText = getString(R.string.wifi_robot_already_connected_toast),
                                    )
                                    return@launch
                                }

                                is ProvisionPanelSignalPlan.ContinueLocalDiscovery -> {
                                    provisionPanelState = provisionPanelState.copy(
                                        panelOnlySeen = true,
                                        localHostHint = panelPlan.localHostHint.ifBlank {
                                            provisionPanelState.localHostHint
                                        },
                                        deviceIdHint = panelSnapshot?.deviceId.orEmpty()
                                            .ifBlank { provisionPanelState.deviceIdHint },
                                    )
                                }

                                ProvisionPanelSignalPlan.NoPanelSignal -> Unit
                            }
                        }

                        updateLocalDiagnostics(
                            if (plan.visibleRobotSsid.isNotBlank()) {
                                getString(R.string.diagnostics_local_robot_visible_nearby, plan.visibleRobotSsid)
                            } else {
                                getString(R.string.diagnostics_local_settling)
                            }
                        )
                        diagnosticsPanel = if (plan.visibleRobotSsid.isNotBlank()) {
                            getString(R.string.diagnostics_panel_skipped_robot_visible, plan.visibleRobotSsid)
                        } else {
                            getString(R.string.diagnostics_panel_delayed_local_search)
                        }
                        renderDiagnostics()
                        updateProvisionProgress(
                            if (plan.visibleRobotSsid.isNotBlank()) {
                                getString(R.string.wifi_wait_robot_switch)
                            } else {
                                getString(R.string.wifi_wait_robot_boot)
                            }
                        )
                        delay(1500)
                        return@repeat
                    }

                    is ProvisionAttemptPlan.DiscoverOnHomeWifi -> {
                        updateProvisionProgress(
                            getString(
                                R.string.wifi_check_attempt,
                                plan.displayAttempt,
                                plan.displayTotal
                            )
                        )
                        val draft = configStore.loadDraft()
                        val savedDeviceIdIsAuthoritative =
                            currentBundle?.selfhostXiaozhi?.deviceId?.isNotBlank() == true
                        val reachability = resolvePanelAndLocalReachability(
                            draft = draft,
                            subnetPrefix = subnetPrefix,
                            allowGenericSweep = true,
                            savedDeviceIdIsAuthoritative = savedDeviceIdIsAuthoritative,
                            panelDeviceIdHint = provisionPanelState.deviceIdHint,
                            panelLocalHostHint = provisionPanelState.localHostHint,
                        )
                        val host = reachability.localHost
                        if (!host.isNullOrBlank()) {
                            maybeHandleRobotFound(
                                host = host,
                                statusText = getString(R.string.wifi_robot_found_network),
                                toastText = getString(R.string.wifi_robot_found_toast),
                                verifiedDeviceId = reachability.localDeviceId,
                                savedDeviceIdIsAuthoritative = savedDeviceIdIsAuthoritative,
                            )
                            return@launch
                        }
                        if (plan.shouldCheckPanelAfterDiscovery) {
                            val panelSnapshot = reachability.panelSnapshot
                            when (val panelPlan = provisionPanelSignalPlan(panelSnapshot)) {
                                is ProvisionPanelSignalPlan.CompleteWithLocalHost -> {
                                    maybeHandleRobotFound(
                                        host = panelPlan.localHost,
                                        statusText = getString(R.string.wifi_robot_found_network),
                                        toastText = getString(R.string.wifi_robot_already_connected_toast),
                                    )
                                    return@launch
                                }

                                is ProvisionPanelSignalPlan.ContinueLocalDiscovery -> {
                                    provisionPanelState = provisionPanelState.copy(
                                        panelOnlySeen = true,
                                        localHostHint = panelPlan.localHostHint.ifBlank {
                                            provisionPanelState.localHostHint
                                        },
                                        deviceIdHint = panelSnapshot?.deviceId.orEmpty()
                                            .ifBlank { provisionPanelState.deviceIdHint },
                                    )
                                    updateLocalDiagnostics(
                                        getString(R.string.diagnostics_local_platform_only_retrying)
                                    )
                                    diagnosticsPanel = getString(R.string.diagnostics_panel_connected_platform)
                                    renderDiagnostics()
                                }

                                ProvisionPanelSignalPlan.NoPanelSignal -> Unit
                            }
                        }
                        delay(1200)
                    }
                }
            }
            if (provisionPanelState.panelOnlySeen) {
                applyPanelOnlyProvisionFinal(provisionPanelState.localHostHint)
                return@launch
            }
            maybeHandleRobotMissing(getString(R.string.wifi_after_portal_not_found))
        }
    }

    private fun ensureRobotWifiPermissionsAndConnect(robotSsidHint: String = "") {
        Log.d(logTag, "ensureRobotWifiPermissionsAndConnect(ssidHint=$robotSsidHint)")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            pendingRobotWifiConnection = false
            robotWifiConnectTimeoutJob?.cancel()
            pendingRobotWifiSsidHint = ""
            showStep(WizardStep.WIFI)
            tvRobotCheck.text = getString(R.string.wifi_android_unsupported_text)
            setStatus(getString(R.string.wifi_android_unsupported_status))
            toast(getString(R.string.wifi_android_unsupported_toast))
            return
        }
        pendingRobotWifiSsidHint = robotSsidHint
        showLoadingState(
            title = getString(R.string.loading_title_open_robot_wifi),
            body = getString(
                R.string.loading_body_open_robot_wifi,
                robotSsidHint.ifBlank { RobotBranding.displayWifiHint(robotWifiPrefix) }
            )
        )
        pendingRobotWifiConnection = true
        if (RobotWifiConnector.hasRequiredPermissions(this)) {
            connectToRobotWifiInsideApp()
            return
        }
        setStatus(getString(R.string.wifi_permission_request_status))
        robotWifiPermissionsLauncher.launch(RobotWifiConnector.requiredPermissions())
    }

    private fun connectToRobotWifiInsideApp() {
        val ssidHint = pendingRobotWifiSsidHint.ifBlank { RobotBranding.displayWifiHint(robotWifiPrefix) }
        Log.d(logTag, "connectToRobotWifiInsideApp(ssidHint=$ssidHint)")
        showLoadingState(
            title = getString(R.string.loading_title_open_robot_wifi),
            body = getString(R.string.loading_body_open_robot_wifi, ssidHint)
        )
        tvRobotCheck.text = getString(R.string.wifi_connecting_robot_request_choice, ssidHint)
        setStatus(getString(R.string.wifi_connecting_robot_status))
        robotWifiConnectTimeoutJob?.cancel()
        robotWifiConnectTimeoutJob = uiScope.launch {
            delay(60_000L)
            if (!pendingRobotWifiConnection) return@launch

            Log.d(logTag, "robot wifi connect timeout fired")
            pendingRobotWifiConnection = false
            pendingRobotWifiSsidHint = ""
            RobotWifiConnector.release()

            val currentSsid = WifiInfoHelper.currentSsid(this@MainActivity)
            if (RobotBranding.isRobotWifiSsid(currentSsid, robotWifiPrefix)) {
                tvRobotCheck.text = getString(R.string.wifi_phone_on_robot_text)
                setStatus(getString(R.string.wifi_phone_on_robot_status))
                startRobotWifiPortal()
                return@launch
            }

            val visibleRobotSsid = findVisibleRobotSsid(attempts = 1, delayMs = 0L)
            if (visibleRobotSsid.isNotBlank()) {
                val visibilityTransition = OnboardingCoordinator.visibility(
                    decision = visibleRobotDecision(currentSsid, visibleRobotSsid),
                    presentation = WifiPresentationMode.OPEN_RECONNECT_STEP,
                )
                if (visibilityTransition != null) {
                    applyWifiStepTransition(visibilityTransition)
                }
                return@launch
            }

            val subnetPrefix = WifiInfoHelper.currentSubnetPrefix(this@MainActivity)
            if (subnetPrefix.isNotBlank()) {
                if (maybeHandleRobotViaPanel(resolveRobotViaPanel(), getString(R.string.wifi_robot_already_connected_panel))) {
                    return@launch
                }

                val draft = configStore.loadDraft()
                val (host, _) = discoverRobotLocally(
                    subnetPrefix = subnetPrefix,
                    preferredHosts = listOf(draft.robotHost),
                    expectedDeviceId = draft.expectedDeviceId,
                )
                if (!host.isNullOrBlank()) {
                    maybeHandleRobotFound(
                        host = host,
                        statusText = getString(R.string.wifi_robot_found_network),
                        toastText = getString(R.string.wifi_robot_already_connected_toast),
                        verifiedDeviceId = draft.expectedDeviceId,
                    )
                    return@launch
                }
            }

            applyWifiStepTransition(OnboardingCoordinator.connectTimeout())
        }
        RobotWifiConnector.connect(
            context = this,
            onConnected = {
                runOnUiThread {
                    Log.d(logTag, "RobotWifiConnector.onConnected()")
                    robotWifiConnectTimeoutJob?.cancel()
                    val ssid = WifiInfoHelper.currentSsid(this).ifBlank { RobotBranding.PRIMARY_WIFI_PREFIX + "XXXX" }
                    pendingRobotWifiSsidHint = ssid
                    tvRobotCheck.text = getString(R.string.wifi_connected_robot_text, ssid)
                    setStatus(getString(R.string.runtime_status_open_portal))
                    startRobotWifiPortal()
                }
            },
            onError = { message ->
                runOnUiThread {
                    Log.d(logTag, "RobotWifiConnector.onError($message)")
                    robotWifiConnectTimeoutJob?.cancel()
                    pendingRobotWifiConnection = false
                    pendingRobotWifiSsidHint = ""
                    showStep(WizardStep.WIFI)
                    tvRobotCheck.text = message
                    setStatus(getString(R.string.wifi_connect_failed_status))
                    toast(message)
                }
            },
        )
    }

    private fun startRobotWifiPortal() {
        Log.d(logTag, "startRobotWifiPortal()")
        awaitingRobotProvision = true
        robotWifiPortalSubmitted = false
        pendingRobotWifiConnection = false
        robotWifiConnectTimeoutJob?.cancel()
        wifiStepStatusJob?.cancel()
        wifiStepStatusJob = null
        cancelMenuStabilization()
        pendingRobotWifiSsidHint = ""
        robotProvisionCheckJob?.cancel()
        resumeDiscoveryJob?.cancel()
        setStatus(getString(R.string.runtime_status_open_portal))
        reportPresenceAsync(MobilePresenceState.PHONE_ON_ROBOT_WIFI)
        stopConnectorService()
        if (RobotBranding.isRobotWifiSsid(WifiInfoHelper.currentSsid(this), robotWifiPrefix)) {
            RobotWifiConnector.bindToCurrentRobotWifi(this)
        }
        robotWifiPortalActive = true
        robotWifiPortalLauncher.launch(Intent(this, HotspotPortalActivity::class.java))
    }

    private fun maybeRestoreConnectedRobot() {
        if (!startupResolutionPending) return
        startupResolutionPending = false

        val draft = configStore.loadDraft()
        if (draft.robotId.isBlank()) {
            showLoadingState(
                title = getString(R.string.loading_title_check),
                body = getString(R.string.loading_body_check)
            )
            uiScope.launch {
                delay(900)
                showStep(WizardStep.WELCOME)
            }
            return
        }

        resolveExistingRobotState()
    }

    private fun resolveExistingRobotState() {
        val draft = configStore.loadDraft()
        if (draft.robotId.isBlank()) {
            return
        }

        if (draft.setupCompleted) {
            showStep(WizardStep.MENU)
            applyImmediateMenuHint(draft)
            syncConnectorService(draft)
            scheduleMenuStabilization(reason = "restore", delayMs = 250L)
            return
        }

        if (!RobotWifiConnector.hasRequiredPermissions(this)) {
            requestRobotWifiPermissions(PermissionRequestPurpose.RESOLVE_EXISTING)
            return
        }

        val currentSsid = WifiInfoHelper.currentSsid(this)
        if (draft.wifiReconnectPending) {
            persistDraft(draft.copy(wifiReconnectPending = false))
        }
        val currentDecision = visibleRobotDecision(currentSsid, nearbyRobotSsid = "")
        val currentVisibilityTransition = OnboardingCoordinator.visibility(
            decision = currentDecision,
            presentation = WifiPresentationMode.OPEN_RECONNECT_STEP,
        )
        if (currentVisibilityTransition != null) {
            applyWifiStepTransition(currentVisibilityTransition)
            return
        }
        if (resumeDiscoveryJob?.isActive == true || robotProvisionCheckJob?.isActive == true) return

        resumeDiscoveryJob = uiScope.launch {
            showLoadingState(
                title = getString(R.string.loading_title_search),
                body = getString(R.string.loading_body_search)
            )

            maybeRequestFreshRobotScan(force = true)
            val visibleRobotSsid = currentVisibleRobotSsid().ifBlank {
                findVisibleRobotSsid(attempts = 2, delayMs = 650L)
            }
            val refreshedCurrentSsid = WifiInfoHelper.currentSsid(this@MainActivity)
            val visibilityDecision = visibleRobotDecision(refreshedCurrentSsid, visibleRobotSsid)
            val visibilityTransition = OnboardingCoordinator.visibility(
                decision = visibilityDecision,
                presentation = WifiPresentationMode.OPEN_RECONNECT_STEP,
            )
            if (visibilityTransition != null) {
                applyWifiStepTransition(visibilityTransition)
                return@launch
            }

            val subnetPrefix = WifiInfoHelper.currentSubnetPrefix(this@MainActivity)
            val reachability = resolvePanelAndLocalReachability(draft, subnetPrefix)
            if (
                maybeHandleRobotViaPanel(
                    snapshot = reachability.panelSnapshot,
                    messagePrefix = getString(R.string.wifi_robot_already_connected_panel),
                    verifiedLocalHost = reachability.localHost.orEmpty(),
                    verifiedLocalDeviceId = reachability.localDeviceId,
                )
            ) {
                return@launch
            }

            if (subnetPrefix.isNotBlank()) {
                tvLoadingBody.text = getString(R.string.loading_body_search)
            } else {
                tvLoadingBody.text = getString(R.string.loading_body_check)
            }

            if (subnetPrefix.isNotBlank()) {
                val directVisibleRobotSsid = findVisibleRobotSsid(attempts = 1, delayMs = 0L)
                if (directVisibleRobotSsid.isNotBlank()) {
                    applyWifiStepTransition(
                        OnboardingCoordinator.visibility(
                            decision = visibleRobotDecision(refreshedCurrentSsid, directVisibleRobotSsid),
                            presentation = WifiPresentationMode.OPEN_RECONNECT_STEP,
                        ) ?: OnboardingCoordinator.reconnectWaiting()
                    )
                    return@launch
                }
            } else {
                maybeHandleRobotMissing(getString(R.string.wifi_missing_open_hint))
                return@launch
            }

            maybeHandleRobotMissing(getString(R.string.wifi_missing_open_hint))
        }
    }

    private fun maybeCheckRobotConnectedAfterProvision() {
        maybeContinueProvisionAfterReturn()
    }

    private fun showMenuWithStatus(statusText: String) {
        showStep(WizardStep.MENU)
        setStatus(statusText)
    }

    private fun scheduleMenuStabilization(reason: String, delayMs: Long = 700L) {
        val draft = configStore.loadDraft()
        if (!draft.setupCompleted || draft.robotId.isBlank() || awaitingRobotProvision) {
            return
        }
        if (menuStabilizationRunning) {
            queuedMenuStabilizationReason = reason
            Log.d(logTag, "scheduleMenuStabilization queued while running: $reason")
            return
        }
        menuStabilizationJob?.cancel()
        menuStabilizationJob = uiScope.launch {
            try {
                delay(delayMs)
                if (awaitingRobotProvision || currentStep != WizardStep.MENU) {
                    return@launch
                }
                menuStabilizationRunning = true
                runMenuStabilization(reason)
            } finally {
                menuStabilizationRunning = false
                menuStabilizationJob = null
                val queuedReason = queuedMenuStabilizationReason
                queuedMenuStabilizationReason = ""
                if (queuedReason.isNotBlank()) {
                    scheduleMenuStabilization(queuedReason, delayMs = 500L)
                }
            }
        }
    }

    private suspend fun runMenuStabilization(reason: String) {
        if (awaitingRobotProvision || currentStep != WizardStep.MENU) return

        val draft = configStore.loadDraft()
        if (!draft.setupCompleted || draft.robotId.isBlank()) return

        Log.d(logTag, "runMenuStabilization(reason=$reason, robotId=${draft.robotId})")
        maybeRequestFreshRobotScan(force = reason == "restore")
        applyImmediateMenuHint(draft)

        val currentSsid = WifiInfoHelper.currentSsid(this@MainActivity)
        val visibleRobotSsid = currentVisibleRobotSsid()
        val visibilityDecision = visibleRobotDecision(currentSsid, visibleRobotSsid)
        when (visibilityDecision.type) {
            RobotConnectivityDecisionType.PHONE_ON_ROBOT_WIFI -> {
                updateLocalDiagnostics(getString(R.string.diagnostics_local_wait_robot_wifi, visibilityDecision.robotSsid))
                diagnosticsPanel = getString(R.string.diagnostics_panel_skipped_robot_wifi)
                renderDiagnostics()
                setStatus(getString(R.string.menu_status_phone_on_robot_wifi, visibilityDecision.robotSsid))
                reportPresenceAsync(MobilePresenceState.PHONE_ON_ROBOT_WIFI)
            }

            RobotConnectivityDecisionType.ROBOT_VISIBLE_NEARBY -> {
                updateLocalDiagnostics(
                    getString(R.string.diagnostics_local_robot_visible_nearby, visibilityDecision.robotSsid)
                )
                diagnosticsPanel = getString(
                    R.string.diagnostics_panel_skipped_robot_visible,
                    visibilityDecision.robotSsid
                )
                renderDiagnostics()
                setStatus(getString(R.string.menu_status_robot_visible, visibilityDecision.robotSsid))
                reportPresenceAsync(MobilePresenceState.ROBOT_HOTSPOT_VISIBLE)
            }

            else -> Unit
        }

        val subnetPrefix = WifiInfoHelper.currentSubnetPrefix(this@MainActivity)
        val reachability = resolvePanelAndLocalReachability(draft, subnetPrefix)

        // Адрес из панели может быть устаревшей подсказкой от предыдущего сеанса телефона.
        // Локальную связь подтверждаем только прямой проверкой из текущей Wi-Fi-сети.
        if (!reachability.localHost.isNullOrBlank()) {
            resetMenuRecoveryRetry()
            maybeHandleRobotFound(
                host = reachability.localHost,
                statusText = getString(R.string.wifi_robot_found_network),
                toastText = null,
                verifiedDeviceId = reachability.localDeviceId,
            )
            return
        }

        val panelDecision = RobotConnectivityResolver.resolveVerifiedReachability(
            panelSnapshot = reachability.panelSnapshot,
            verifiedLocalHost = "",
        )
        val connectedTransition = OnboardingCoordinator.connectedMenu(panelDecision)
        val panelOnlyConnected = connectedTransition?.route == ConnectedMenuRoute.PANEL_ONLY
        if (panelOnlyConnected) {
            applyConnectedMenuTransition(
                transition = connectedTransition ?: return,
                statusText = getString(R.string.wifi_robot_already_connected_panel),
                toastText = null,
                preserveMenuRecoveryRetry = true,
            )
        }

        if (NetworkStateHelper.isVpnActive(this@MainActivity) && !panelOnlyConnected) {
            updateLocalDiagnostics(getString(R.string.diagnostics_local_vpn_active))
            renderDiagnostics()
            setStatus(getString(R.string.menu_status_vpn_active))
            scheduleMenuRecoveryRetry(
                recoveryNeeded = wifiRecoveryCorrelationId.isNotBlank(),
                hasHomeWifi = subnetPrefix.isNotBlank(),
            )
            return
        }

        if (subnetPrefix.isNotBlank()) {
            if (!panelOnlyConnected && visibilityDecision.type == RobotConnectivityDecisionType.UNKNOWN) {
                persistDraft(configStore.loadDraft().copy(robotHost = ""))
                updateLocalDiagnostics(getString(R.string.diagnostics_local_robot_wifi_hidden))
                renderDiagnostics()
                setStatus(getString(R.string.menu_status_robot_missing))
                reportPresenceAsync(MobilePresenceState.NOT_FOUND)
                stopConnectorService()
                scheduleMenuRecoveryRetry(
                    recoveryNeeded = wifiRecoveryCorrelationId.isNotBlank(),
                    hasHomeWifi = true,
                )
                return
            }
        } else if (visibilityDecision.type == RobotConnectivityDecisionType.UNKNOWN) {
            updateLocalDiagnostics(getString(R.string.diagnostics_local_wait_home_wifi))
            if (!panelOnlyConnected) {
                diagnosticsPanel = getString(R.string.diagnostics_panel_skipped_no_internet)
            }
            renderDiagnostics()
            setStatus(getString(R.string.menu_status_no_home_wifi))
            stopConnectorService()
            return
        }

        if (
            panelOnlyConnected ||
            visibilityDecision.type == RobotConnectivityDecisionType.PHONE_ON_ROBOT_WIFI ||
            visibilityDecision.type == RobotConnectivityDecisionType.ROBOT_VISIBLE_NEARBY
        ) {
            if (subnetPrefix.isNotBlank()) {
                scheduleMenuRecoveryRetry(
                    recoveryNeeded = panelOnlyConnected || wifiRecoveryCorrelationId.isNotBlank(),
                    hasHomeWifi = true,
                )
            }
            stopConnectorService()
            return
        }

        syncConnectorService(configStore.loadDraft())
    }

    private fun reportPresenceAsync(state: MobilePresenceState, localHost: String = "") {
        val draft = configStore.loadDraft()
        if (draft.robotId.isBlank()) return
        if (state.acceptsLocalHost && localHost.isBlank()) return

        val normalizedHost = if (state.acceptsLocalHost) localHost.trim() else ""
        val signature = listOf(draft.robotId, state.wireValue, normalizedHost).joinToString("|")
        if (signature == lastPresenceSignature) {
            return
        }

        uiScope.launch {
            try {
                PanelApiClient.updateMobilePresence(
                    http = presenceHttpClient,
                    baseUrl = panelBaseUrl(),
                    robotId = draft.robotId,
                    state = state,
                    localHost = normalizedHost,
                    panelClientToken = draft.panelClientToken,
                    onboardingCode = draft.onboardingCode,
                    sourceId = runtimeEventReporter.sourceId,
                    instanceId = runtimeEventReporter.sessionId,
                    appVersion = BuildConfig.VERSION_NAME,
                )
                lastPresenceSignature = signature
                Log.d(
                    logTag,
                    "reportPresenceAsync success: state=${state.wireValue} localHost=$normalizedHost"
                )
            } catch (exc: Exception) {
                runtimeEventReporter.publish(
                    RuntimeEventTarget(
                        baseUrl = panelBaseUrl(),
                        robotId = draft.robotId,
                        panelClientToken = draft.panelClientToken,
                        onboardingCode = draft.onboardingCode,
                    ),
                    runtimeEventReporter.event(
                        eventType = "mobile.presence.publish_failed",
                        severity = "warning",
                        state = JSONObject()
                            .put("domain", "presence")
                            .put("name", state.wireValue)
                            .put("status", "delivery_pending"),
                        link = JSONObject()
                            .put("kind", "mobile_platform")
                            .put("status", "unavailable"),
                        error = JSONObject()
                            .put("code", "presence_delivery_failed")
                            .put("message", "Событие мобильного присутствия ожидает повторной отправки")
                            .put("retryable", true),
                    ),
                )
                Log.w(logTag, "reportPresenceAsync failed: ${exc.message}")
            }
        }
    }

    private fun reportNetworkTransitionAsync(
        draft: OnboardingDraft,
        previousWifiAvailable: Boolean,
        currentWifiAvailable: Boolean,
        visibilityDecision: RobotConnectivityDecision,
        fromWatcher: Boolean,
    ) {
        if (draft.robotId.isBlank()) return
        val networkLost = previousWifiAvailable && !currentWifiAvailable
        val networkRecovered = !previousWifiAvailable && currentWifiAvailable
        if (networkLost || networkRecovered) {
            resetMenuRecoveryRetry()
        }
        val recoveryDecision = WifiRecoveryRuntimePolicy.onNetworkTransition(
            currentCorrelationId = wifiRecoveryCorrelationId,
            networkLost = networkLost,
            networkRecovered = networkRecovered,
            newCorrelationId = if (networkLost) "wifi-recovery-${UUID.randomUUID()}" else "",
        )
        wifiRecoveryCorrelationId = recoveryDecision.correlationId
        val correlationId = recoveryDecision.correlationId
        val linkStatus = when (visibilityDecision.type) {
            RobotConnectivityDecisionType.PHONE_ON_ROBOT_WIFI -> "available"
            RobotConnectivityDecisionType.ROBOT_VISIBLE_NEARBY -> "degraded"
            else -> "unknown"
        }
        uiScope.launch {
            runtimeEventReporter.publish(
                RuntimeEventTarget(
                    baseUrl = panelBaseUrl(),
                    robotId = draft.robotId,
                    panelClientToken = draft.panelClientToken,
                    onboardingCode = draft.onboardingCode,
                ),
                runtimeEventReporter.event(
                    eventType = "mobile.network.state_changed",
                    severity = if (networkLost) "warning" else "info",
                    state = JSONObject()
                        .put("domain", "connectivity")
                        .put("name", "wifi")
                        .put("status", if (currentWifiAvailable) "available" else "unavailable"),
                    link = JSONObject()
                        .put("kind", "mobile_robot")
                        .put("status", linkStatus),
                    task = if (correlationId.isBlank()) {
                        null
                    } else {
                        JSONObject()
                            .put("id", correlationId)
                            .put("kind", "wifi_recovery")
                            .put("status", recoveryDecision.taskStatus ?: "running")
                    },
                    error = if (networkLost) {
                        JSONObject()
                            .put("code", "wifi_temporarily_unavailable")
                            .put("message", "Wi-Fi временно недоступен; запущено автоматическое восстановление")
                            .put("retryable", true)
                    } else {
                        null
                    },
                    attributes = JSONObject()
                        .put("trigger", if (fromWatcher) "wifi_watcher" else "activity_resume")
                        .put("robot_visible", visibilityDecision.type != RobotConnectivityDecisionType.UNKNOWN),
                    correlationId = correlationId,
                ),
            )
        }
    }

    private fun reportWifiRecoveryRetryAsync(attempt: Int) {
        val correlationId = wifiRecoveryCorrelationId.trim()
        val draft = configStore.loadDraft()
        if (correlationId.isBlank() || draft.robotId.isBlank()) return
        uiScope.launch {
            runtimeEventReporter.publish(
                RuntimeEventTarget(
                    baseUrl = panelBaseUrl(),
                    robotId = draft.robotId,
                    panelClientToken = draft.panelClientToken,
                    onboardingCode = draft.onboardingCode,
                ),
                runtimeEventReporter.event(
                    eventType = "mobile.wifi_recovery.retrying",
                    state = JSONObject()
                        .put("domain", "connectivity")
                        .put("name", "local_robot")
                        .put("status", "verifying"),
                    link = JSONObject()
                        .put("kind", "mobile_robot")
                        .put("status", "connecting"),
                    task = JSONObject()
                        .put("id", correlationId)
                        .put("kind", "wifi_recovery")
                        .put("status", "retrying"),
                    metrics = JSONObject().put("retry_count", attempt),
                    attributes = JSONObject().put("trigger", "automatic_local_probe"),
                    correlationId = correlationId,
                ),
            )
        }
    }

    private fun completeWifiRecoveryAsync(draft: OnboardingDraft) {
        val completion = WifiRecoveryRuntimePolicy.onLocalRobotVerified(
            currentCorrelationId = wifiRecoveryCorrelationId,
        ) ?: return
        wifiRecoveryCorrelationId = completion.nextCorrelationId
        uiScope.launch {
            runtimeEventReporter.publish(
                RuntimeEventTarget(
                    baseUrl = panelBaseUrl(),
                    robotId = draft.robotId,
                    panelClientToken = draft.panelClientToken,
                    onboardingCode = draft.onboardingCode,
                ),
                runtimeEventReporter.event(
                    eventType = "mobile.wifi_recovery.completed",
                    state = JSONObject()
                        .put("domain", "connectivity")
                        .put("name", "local_robot")
                        .put("status", "ready"),
                    link = JSONObject()
                        .put("kind", "mobile_robot")
                        .put("status", "available"),
                    task = JSONObject()
                        .put("id", completion.correlationId)
                        .put("kind", "wifi_recovery")
                        .put("status", completion.taskStatus),
                    attributes = JSONObject().put("trigger", "local_robot_verified"),
                    correlationId = completion.correlationId,
                ),
            )
        }
    }

    private fun timeoutWifiRecoveryAsync() {
        val timeout = WifiRecoveryRuntimePolicy.onRetryLimitReached(
            currentCorrelationId = wifiRecoveryCorrelationId,
        ) ?: return
        val draft = configStore.loadDraft()
        if (draft.robotId.isBlank()) return
        wifiRecoveryCorrelationId = timeout.nextCorrelationId
        val retryCount = menuRecoveryRetryAttempt
        if (currentStep == WizardStep.MENU && !awaitingRobotProvision) {
            val finalStatus = getString(R.string.wifi_robot_recovery_timed_out)
            tvRobotCheck.text = finalStatus
            tvSuccessMessage.text = finalStatus
            setStatus(getString(R.string.menu_status_recovery_timed_out))
        }
        uiScope.launch {
            runtimeEventReporter.publish(
                RuntimeEventTarget(
                    baseUrl = panelBaseUrl(),
                    robotId = draft.robotId,
                    panelClientToken = draft.panelClientToken,
                    onboardingCode = draft.onboardingCode,
                ),
                runtimeEventReporter.event(
                    eventType = "mobile.wifi_recovery.timed_out",
                    severity = "warning",
                    state = JSONObject()
                        .put("domain", "connectivity")
                        .put("name", "local_robot")
                        .put("status", "not_verified"),
                    link = JSONObject()
                        .put("kind", "mobile_robot")
                        .put("status", "degraded"),
                    task = JSONObject()
                        .put("id", timeout.correlationId)
                        .put("kind", "wifi_recovery")
                        .put("status", timeout.taskStatus),
                    error = JSONObject()
                        .put("code", "local_robot_not_confirmed")
                        .put("message", "Локальная связь с роботом не подтверждена после автоматических проверок")
                        .put("retryable", true),
                    metrics = JSONObject().put("retry_count", retryCount),
                    attributes = JSONObject().put("trigger", "automatic_retry_limit"),
                    correlationId = timeout.correlationId,
                ),
            )
        }
    }

    private fun syncConnectorService(draft: OnboardingDraft = configStore.loadDraft()) {
        val config = if (draft.setupCompleted && !draft.wifiReconnectPending) {
            draft.toConnectorConfigOrNull()
        } else {
            null
        }

        if (config == null) {
            stopConnectorService()
            return
        }

        try {
            ContextCompat.startForegroundService(
                this,
                ConnectorForegroundService.startIntent(this, config),
            )
        } catch (exc: Exception) {
            Log.w(logTag, "syncConnectorService failed: ${exc.message}")
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (notificationPermissionRequestStarted) return
        val permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (
            BackgroundAccessPolicy.shouldRequestNotificationPermission(
                sdkInt = Build.VERSION.SDK_INT,
                permissionGranted = permissionGranted,
                requestedVersion = configStore.notificationPermissionPromptVersion(),
            )
        ) {
            notificationPermissionRequestStarted = true
            notificationPermissionRequestPending = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            configStore.markNotificationPermissionPromptShown(
                BackgroundAccessPolicy.NOTIFICATION_PROMPT_VERSION
            )
        }
    }

    private fun maybePrepareBackgroundAccess() {
        val draft = configStore.loadDraft()
        if (!draft.setupCompleted || draft.wifiReconnectPending) return
        if (draft.toConnectorConfigOrNull() == null) return

        val permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val permissionRequired = BackgroundAccessPolicy.shouldRequestNotificationPermission(
            sdkInt = Build.VERSION.SDK_INT,
            permissionGranted = permissionGranted,
            requestedVersion = configStore.notificationPermissionPromptVersion(),
        )
        if (permissionRequired && !notificationPermissionRequestStarted) {
            maybeRequestNotificationPermission()
            return
        }
        if (notificationPermissionRequestPending) return
        if (!permissionRequired || notificationPermissionRequestStarted) {
            maybeOfferBackgroundAccess()
        }
    }

    private fun maybeOfferBackgroundAccess() {
        if (!BackgroundAccess.isTranssionFamily()) return
        if (currentStep != WizardStep.MENU) return
        if (isFinishing || isDestroyed) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (backgroundAccessDialog?.isShowing == true) return
        val draft = configStore.loadDraft()
        if (
            !BackgroundAccessPolicy.shouldShowGuidance(
                setupCompleted = draft.setupCompleted,
                wifiReconnectPending = draft.wifiReconnectPending,
                connectorConfigReady = draft.toConnectorConfigOrNull() != null,
                shownVersion = configStore.backgroundAccessGuidanceVersion(),
            )
        ) return

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.background_access_dialog_title)
            .setMessage(R.string.background_access_transsion_instruction)
            .setNegativeButton(R.string.background_access_later) { _, _ ->
                configStore.markBackgroundAccessGuidanceShown(
                    BackgroundAccessPolicy.GUIDANCE_VERSION
                )
            }
            .setPositiveButton(R.string.background_access_open_settings) { _, _ ->
                configStore.markBackgroundAccessGuidanceShown(
                    BackgroundAccessPolicy.GUIDANCE_VERSION
                )
                if (!BackgroundAccess.openSettings(this)) {
                    toast(getString(R.string.background_access_open_error))
                }
            }
            .create()
        dialog.setOnDismissListener {
            if (backgroundAccessDialog === dialog) {
                backgroundAccessDialog = null
            }
        }
        backgroundAccessDialog = dialog
        try {
            dialog.show()
        } catch (exc: Exception) {
            backgroundAccessDialog = null
            Log.w(logTag, "Background access guidance could not be shown: ${exc.message}")
        }
    }

    private fun stopConnectorService() {
        stopService(Intent(this, ConnectorForegroundService::class.java))
    }

    private fun showDeviceStatus() {
        startActivity(Intent(this, DeviceStatusActivity::class.java))
    }

    private fun openCabinet() {
        startActivity(Intent(this, ClientCabinetActivity::class.java))
    }

    private fun showGuides() {
        startActivity(Intent(this, GuidesActivity::class.java))
    }

    private fun showSupport() {
        startActivity(Intent(this, SupportActivity::class.java))
    }

    private fun openPrivacyPolicy() {
        if (!PrivacyPolicy.open(this)) {
            toast(getString(R.string.support_policy_open_error))
        }
    }

    private fun openTermsOfUse() {
        if (!TermsOfUse.open(this)) {
            toast(getString(R.string.support_terms_open_error))
        }
    }

    private fun reconnectRobotWifi() {
        val draft = configStore.loadDraft()
        persistDraft(draft.copy(robotHost = "", wifiReconnectPending = true))
        awaitingRobotProvision = false
        robotWifiPortalSubmitted = false
        pendingRobotWifiConnection = false
        robotWifiConnectTimeoutJob?.cancel()
        robotProvisionCheckJob?.cancel()
        resumeDiscoveryJob?.cancel()
        wifiStepStatusJob?.cancel()
        wifiStepStatusJob = null
        cancelMenuStabilization()
        wifiBackToMenuMode = true
        openWifiReconnectStep(
            message = getString(R.string.wifi_reconnect_hint),
            localDiagnostics = getString(R.string.diagnostics_local_reconnect_pending),
            panelDiagnosticsText = getString(R.string.diagnostics_panel_skipped_reconnect_pending)
        )
    }

    companion object {
        private const val ROBOT_PORTAL_URL = "http://192.168.4.1"
    }

    private fun confirmResetForNextRobot() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_reset_title)
            .setMessage(R.string.dialog_reset_message)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_reset_confirm) { _, _ ->
                stopConnectorService()
                configStore.resetForNextRobot()
                lastPresenceSignature = ""
                currentBundle = null
                awaitingRobotProvision = false
                pendingRobotWifiConnection = false
                robotProvisionCheckJob?.cancel()
                wifiStepStatusJob?.cancel()
                wifiStepStatusJob = null
                cancelMenuStabilization()
                etCode.setText("")
                etOwnerName.setText("")
                etOwnerEmail.setText("")
                etOwnerPhone.setText("")
                tvCodeStatus.text = if (BuildConfig.IS_ADMIN_APP) {
                    getString(R.string.registration_status_code_only)
                } else {
                    getString(R.string.registration_status_fill)
                }
                tvInstruction.text = getString(R.string.wifi_instruction_main)
                tvRobotCheck.text = getString(R.string.wifi_check_default)
                tvSuccessMessage.text = getString(R.string.success_message_default)
                refreshWifiInfo()
                updateRegistrationUi()
                setStatus(getString(R.string.runtime_status_reset_ready))
                showStep(WizardStep.REGISTRATION)
                toast(getString(R.string.dialog_reset_result))
            }
            .show()
    }

    private fun setStatus(text: String) {
        diagnosticsDecision = text
        tvStatus.text = getString(R.string.status_prefix, text)
        updateMenuHero(text)
        renderDiagnostics()
    }

    private fun updateMenuHero(statusText: String?) {
        val titleRes = when {
            statusText.isNullOrBlank() -> R.string.menu_hero_title
            matchesStatusTemplate(statusText, R.string.menu_status_checking) ||
                matchesStatusTemplate(statusText, R.string.menu_status_checking_saved_host) ->
                R.string.menu_hero_title_checking

            matchesStatusTemplate(statusText, R.string.menu_status_no_home_wifi) ->
                R.string.menu_hero_title_no_home_wifi

            matchesStatusTemplate(statusText, R.string.menu_status_platform_wait_home_wifi) ->
                R.string.menu_hero_title_no_home_wifi

            matchesStatusTemplate(statusText, R.string.menu_status_vpn_active) ->
                R.string.menu_hero_title_vpn_active

            matchesStatusTemplate(statusText, R.string.menu_status_phone_on_robot_wifi) ||
                matchesStatusTemplate(statusText, R.string.menu_status_robot_visible) ->
                R.string.menu_hero_title_robot_visible

            matchesStatusTemplate(statusText, R.string.menu_status_connected_host) ->
                R.string.menu_hero_title_connected

            matchesStatusTemplate(statusText, R.string.menu_status_platform_only) ->
                R.string.menu_hero_title_platform_only

            matchesStatusTemplate(statusText, R.string.menu_status_recovery_timed_out) ->
                R.string.menu_hero_title_missing

            matchesStatusTemplate(statusText, R.string.menu_status_robot_missing) ->
                R.string.menu_hero_title_missing

            else -> R.string.menu_hero_title
        }
        tvMenuHeroTitle.text = getString(titleRes)
        tvMenuHeroBody.text = statusText ?: getString(R.string.menu_hero_body)
    }

    private fun matchesStatusTemplate(value: String, templateRes: Int): Boolean {
        val template = getString(templateRes)
        if (!template.contains("%1\$s")) {
            return value == template
        }
        val prefix = template.substringBefore("%1\$s")
        val suffix = template.substringAfter("%1\$s")
        return value.startsWith(prefix) && value.endsWith(suffix)
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
