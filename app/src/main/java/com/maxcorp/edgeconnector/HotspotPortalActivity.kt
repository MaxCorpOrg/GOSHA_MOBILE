package com.maxcorp.gosha.mobile

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class HotspotPortalActivity : AppCompatActivity() {
    private val logTag = "HotspotPortal"
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var webView: WebView
    private lateinit var tvPortalStatus: TextView
    private var lastPortalUrl = PORTAL_BASE_URL
    private var reloadAttempts = 0
    private var provisionSubmitted = false
    private var provisionCompleted = false
    private var waitForExitAttempts = 0
    private var submissionExitPollAttempts = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotspot_portal)

        tvPortalStatus = findViewById(R.id.tvPortalStatus)
        webView = findViewById(R.id.portalWebView)

        val backButton = findViewById<Button>(R.id.btnPortalBack)
        val reloadButton = findViewById<Button>(R.id.btnPortalReload)
        UiPlayful.enhanceButtons(backButton, reloadButton)
        backButton.setOnClickListener { finish() }
        reloadButton.setOnClickListener { loadPortal(resetAttempts = true) }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            loadsImagesAutomatically = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        webView.addJavascriptInterface(PortalBridge(), "RobotPortalBridge")
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    Log.d(
                        logTag,
                        "Portal console ${consoleMessage.messageLevel()}: ${consoleMessage.message()} @${consoleMessage.lineNumber()}"
                    )
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                if (isAllowedPortalUrl(url)) {
                    Log.d(logTag, "Intercepted portal navigation to $url")
                    loadPortalPage(url)
                    return true
                }
                Log.w(logTag, "Blocked unexpected portal navigation: $url")
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(logTag, "Portal page finished: $url")
                reloadAttempts = 0
                webView.clearFocus()
                webView.requestFocus(View.FOCUS_DOWN)
                injectRussianHelpers()
                tvPortalStatus.text = when {
                    provisionCompleted -> getString(R.string.portal_status_done)
                    provisionSubmitted -> getString(R.string.portal_status_submitted)
                    else -> getString(R.string.portal_status_opened)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    Log.w(
                        logTag,
                        "Portal main-frame error: code=${error?.errorCode} desc=${error?.description} url=${request.url}"
                    )
                    retryLoad()
                }
            }
        }

        if (RobotBranding.isRobotWifiSsid(WifiInfoHelper.currentSsid(this), ROBOT_WIFI_PREFIX)) {
            RobotWifiConnector.bindToCurrentRobotWifi(this)
        }
        loadPortal(resetAttempts = true)
    }

    private fun loadPortal(resetAttempts: Boolean) {
        if (resetAttempts) {
            reloadAttempts = 0
        }
        lastPortalUrl = PORTAL_BASE_URL
        provisionSubmitted = false
        provisionCompleted = false
        waitForExitAttempts = 0
        submissionExitPollAttempts = 0
        tvPortalStatus.text = getString(R.string.portal_status_opening)
        if (RobotBranding.isRobotWifiSsid(WifiInfoHelper.currentSsid(this), ROBOT_WIFI_PREFIX)) {
            RobotWifiConnector.bindToCurrentRobotWifi(this)
        }
        loadPortalPage(PORTAL_BASE_URL)
    }

    private fun retryLoad() {
        if (reloadAttempts >= 4) {
            tvPortalStatus.text = getString(R.string.portal_status_error)
            return
        }
        reloadAttempts += 1
        val delayMs = if (reloadAttempts < 2) 700L else 1200L
        tvPortalStatus.text = getString(R.string.portal_status_retry, reloadAttempts)
        mainHandler.postDelayed({ loadPortalPage(lastPortalUrl) }, delayMs)
    }

    private fun loadPortalPage(targetUrl: String) {
        lastPortalUrl = targetUrl
        tvPortalStatus.text = when {
            provisionCompleted -> getString(R.string.portal_status_done)
            provisionSubmitted -> getString(R.string.portal_status_submitted)
            else -> getString(R.string.portal_status_opening)
        }
        Log.d(logTag, "Loading portal page through RobotPortalClient: $targetUrl")
        Thread {
            val result = runCatching { RobotPortalClient.fetch(this, targetUrl) }
            runOnUiThread {
                if (isDestroyed || isFinishing) {
                    return@runOnUiThread
                }
                result.onSuccess { response ->
                    Log.d(
                        logTag,
                        "Portal response: request=$targetUrl resolved=${response.url} code=${response.code} type=${response.contentType} bytes=${response.bodyBytes.size}"
                    )
                    val body = response.bodyText()
                    if (response.code !in 200..299 || body.isBlank()) {
                        retryLoad()
                        return@onSuccess
                    }
                    lastPortalUrl = response.url
                    webView.loadDataWithBaseURL(
                        response.url,
                        preparePortalHtml(body),
                        resolveMimeType(response.contentType),
                        "utf-8",
                        response.url,
                    )
                }.onFailure { error ->
                    Log.w(logTag, "Failed to load portal page $targetUrl: ${error.message}", error)
                    retryLoad()
                }
            }
        }.start()
    }

    private fun injectRussianHelpers() {
        val js = """
            (function() {
              function notifySubmitted() {
                if (window.RobotPortalBridge && window.RobotPortalBridge.onProvisionSubmitted) {
                  window.RobotPortalBridge.onProvisionSubmitted();
                }
              }

              function notifyCompleted() {
                if (window.RobotPortalBridge && window.RobotPortalBridge.onProvisionCompleted) {
                  window.RobotPortalBridge.onProvisionCompleted();
                }
              }

              function wireSubmitHandlers() {
                document.querySelectorAll('form').forEach((form) => {
                  if (form.dataset.maxcorpBound === '1') return;
                  form.dataset.maxcorpBound = '1';
                  form.addEventListener('submit', notifySubmitted);
                });
                document.querySelectorAll('button, input[type="submit"]').forEach((el) => {
                  if (el.dataset.maxcorpBound === '1') return;
                  const text = (el.textContent || el.value || '').trim();
                  if (!text || text === '连接' || text === 'Подключить' || text === 'Connect') {
                    el.dataset.maxcorpBound = '1';
                    el.addEventListener('click', notifySubmitted);
                  }
                });
              }

              function setText(selector, expected, replacement) {
                document.querySelectorAll(selector).forEach((el) => {
                  const text = (el.textContent || '').trim();
                  if (text === expected) {
                    el.textContent = replacement;
                  }
                });
              }

              function replacePlaceholder(selector, expected, replacement) {
                document.querySelectorAll(selector).forEach((el) => {
                  const placeholder = el.getAttribute('placeholder') || '';
                  if (placeholder.indexOf(expected) >= 0) {
                    el.setAttribute('placeholder', placeholder.split(expected).join(replacement));
                  }
                });
              }

              function softenLanguagePicker() {
                const active = document.activeElement;
                if (active && typeof active.blur === 'function') active.blur();
                document.querySelectorAll('select').forEach((select) => {
                  const option = Array.from(select.options || []).find((item) => {
                    const text = (item.text || '').trim();
                    return text === 'English' || text === 'Русский' || text === '简体中文';
                  });
                  if (option) {
                    select.value = option.value;
                  }
                  if (typeof select.blur === 'function') select.blur();
                });
              }

              function detectProvisionSuccess() {
                const text = (document.body && document.body.innerText ? document.body.innerText : '').trim();
                if (!text) return;
                const markers = [
                  'Device will restart in',
                  '设备将在',
                  '配置成功',
                  'Configuration successful',
                  'Настройки сохранены',
                  'Подключение выполнено',
                  'Rebooting'
                ];
                if (markers.some((marker) => text.indexOf(marker) >= 0)) {
                  notifyCompleted();
                }
              }

              function installSuccessObserver() {
                if (window.__maxcorpObserverInstalled) return;
                window.__maxcorpObserverInstalled = true;
                if (!document.body) return;
                const observer = new MutationObserver(function() {
                  wireSubmitHandlers();
                  detectProvisionSuccess();
                });
                observer.observe(document.body, { childList: true, subtree: true, characterData: true });
              }

              setText('button, a, span, div, label, h1, h2, h3', 'Wi-Fi 配置', 'Настройка Wi‑Fi');
              setText('button, a, span, div, label, h1, h2, h3', '高级选项', 'Дополнительно');
              setText('button, a, span, div, label, h1, h2, h3', '新的 Wi-Fi', 'Новый Wi‑Fi');
              setText('button, a, span, div, label, h1, h2, h3', 'SSID', 'Сеть Wi‑Fi');
              setText('button, a, span, div, label, h1, h2, h3', '密码', 'Пароль');
              setText('button, a, span, div, label, h1, h2, h3', '连接', 'Подключить');
              setText('button, a, span, div, label, h1, h2, h3', '扫描', 'Сканирование...');
              replacePlaceholder('input', '请输入密码', 'Введите пароль');
              replacePlaceholder('input', '输入密码', 'Введите пароль');

              softenLanguagePicker();
              setTimeout(softenLanguagePicker, 250);
              wireSubmitHandlers();
              installSuccessObserver();
              detectProvisionSuccess();
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun preparePortalHtml(originalHtml: String): String {
        val bridgeScript = buildPortalFetchBridgeScript()
        return when {
            originalHtml.contains("</head>", ignoreCase = true) ->
                originalHtml.replace("</head>", "$bridgeScript\n</head>", ignoreCase = true)
            originalHtml.contains("<body", ignoreCase = true) ->
                "$bridgeScript\n$originalHtml"
            else -> "$bridgeScript\n$originalHtml"
        }
    }

    private fun buildPortalFetchBridgeScript(): String {
        return """
            <script>
            (function() {
              if (window.__maxcorpPortalFetchInstalled) return;
              window.__maxcorpPortalFetchInstalled = true;
              const originalFetch = typeof window.fetch === 'function' ? window.fetch.bind(window) : null;

              function normalizeUrl(input) {
                if (!input) return '';
                if (input.startsWith('http://') || input.startsWith('https://')) return input;
                if (input.startsWith('/')) return '${PORTAL_BASE_URL}' + input;
                return '${PORTAL_BASE_URL}/' + input;
              }

              function isPortalUrl(input) {
                if (!input) return false;
                return input.startsWith('${PORTAL_BASE_URL}') || input.startsWith('http://robot.local') || input.startsWith('/');
              }

              function makeResponse(payload) {
                const status = Number(payload.code || 0);
                const body = typeof payload.body === 'string' ? payload.body : '';
                return {
                  ok: status >= 200 && status < 300,
                  status: status,
                  url: payload.url || '',
                  text: async function() {
                    return body;
                  },
                  json: async function() {
                    if (!body) return {};
                    return JSON.parse(body);
                  }
                };
              }

              window.fetch = function(input, init) {
                const rawUrl = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                if (!isPortalUrl(rawUrl) || !window.RobotPortalBridge || !window.RobotPortalBridge.performPortalRequest) {
                  if (originalFetch) {
                    return originalFetch(input, init);
                  }
                  return Promise.reject(new Error('Fetch unavailable'));
                }

                const method = ((init && init.method) || 'GET').toUpperCase();
                const body = init && typeof init.body === 'string' ? init.body : '';
                let contentType = '';
                if (init && init.headers) {
                  if (typeof init.headers.get === 'function') {
                    contentType = init.headers.get('Content-Type') || '';
                  } else {
                    contentType = init.headers['Content-Type'] || init.headers['content-type'] || '';
                  }
                }

                try {
                  const payloadText = window.RobotPortalBridge.performPortalRequest(
                    normalizeUrl(rawUrl),
                    method,
                    body,
                    contentType
                  );
                  const payload = JSON.parse(payloadText || '{}');
                  if (!payload.success) {
                    throw new Error(payload.error || 'Portal request failed');
                  }
                  return Promise.resolve(makeResponse(payload));
                } catch (error) {
                  const message = error && error.message ? error.message : String(error);
                  return Promise.reject(new Error(message));
                }
              };
            })();
            </script>
        """.trimIndent()
    }

    private fun resolveMimeType(contentType: String): String {
        val normalized = contentType.substringBefore(';').trim()
        return if (normalized.isBlank()) {
            "text/html"
        } else {
            normalized
        }
    }

    private fun waitForRobotNetworkExit() {
        val currentSsid = WifiInfoHelper.currentSsid(this)
        if (!RobotBranding.isRobotWifiSsid(currentSsid, ROBOT_WIFI_PREFIX)) {
            Log.d(logTag, "waitForRobotNetworkExit(): leaving robot wifi, currentSsid=$currentSsid")
            tvPortalStatus.text = getString(R.string.portal_status_returning)
            finishSafely()
            return
        }

        waitForExitAttempts += 1
        if (waitForExitAttempts >= WAIT_FOR_EXIT_MAX_ATTEMPTS) {
            tvPortalStatus.text = getString(R.string.portal_status_return_timeout)
            finishSafely(delayMs = RETURN_TIMEOUT_FINISH_DELAY_MS)
            return
        }

        tvPortalStatus.text = getString(
            R.string.portal_status_wait_reboot_retry,
            waitForExitAttempts,
            WAIT_FOR_EXIT_MAX_ATTEMPTS
        )
        mainHandler.postDelayed({ waitForRobotNetworkExit() }, WAIT_FOR_EXIT_INTERVAL_MS)
    }

    private fun scheduleSubmittedExitPoll(resetAttempts: Boolean = false) {
        if (!provisionSubmitted || provisionCompleted) return
        if (resetAttempts) {
            submissionExitPollAttempts = 0
        }
        mainHandler.removeCallbacks(submittedExitPollRunnable)
        mainHandler.postDelayed(submittedExitPollRunnable, SUBMITTED_EXIT_POLL_INTERVAL_MS)
    }

    private fun checkSubmittedExit() {
        if (!provisionSubmitted || provisionCompleted || isFinishing || isDestroyed) return

        val currentSsid = WifiInfoHelper.currentSsid(this)
        val onRobotWifi = RobotBranding.isRobotWifiSsid(currentSsid, ROBOT_WIFI_PREFIX)
        Log.d(
            logTag,
            "checkSubmittedExit(attempt=$submissionExitPollAttempts, currentSsid=$currentSsid, onRobotWifi=$onRobotWifi)"
        )
        if (!onRobotWifi) {
            provisionCompleted = true
            RobotWifiConnector.release()
            tvPortalStatus.text = getString(R.string.portal_status_returning)
            finishSafely()
            return
        }

        submissionExitPollAttempts += 1
        if (submissionExitPollAttempts < SUBMITTED_EXIT_POLL_MAX_ATTEMPTS) {
            scheduleSubmittedExitPoll()
        }
    }

    private fun finishSafely(delayMs: Long = 350L) {
        mainHandler.postDelayed(
            {
                if (!isFinishing && !isDestroyed) {
                    finish()
                }
            },
            delayMs
        )
    }

    private inner class PortalBridge {
        @JavascriptInterface
        fun performPortalRequest(
            url: String,
            method: String,
            body: String?,
            contentType: String?,
        ): String {
            return try {
                Log.d(
                    logTag,
                    "Portal bridge request: method=$method url=$url contentType=${contentType.orEmpty()} bodyLength=${body?.length ?: 0}"
                )
                val response = RobotPortalClient.request(
                    context = this@HotspotPortalActivity,
                    target = url,
                    method = method,
                    body = body?.toByteArray(Charsets.UTF_8),
                    contentType = contentType,
                )
                JSONObject()
                    .put("success", true)
                    .put("url", response.url)
                    .put("code", response.code)
                    .put("contentType", response.contentType)
                    .put("body", response.bodyText())
                    .toString()
            } catch (error: Exception) {
                Log.w(logTag, "Portal bridge request failed for $method $url: ${error.message}", error)
                JSONObject()
                    .put("success", false)
                    .put("error", error.message ?: "unknown error")
                    .toString()
            }
        }

        @JavascriptInterface
        fun onProvisionSubmitted() {
            runOnUiThread {
                provisionSubmitted = true
                tvPortalStatus.text = getString(R.string.portal_status_submitted)
                scheduleSubmittedExitPoll(resetAttempts = true)
            }
        }

        @JavascriptInterface
        fun onProvisionCompleted() {
            runOnUiThread {
                if (provisionCompleted) return@runOnUiThread
                provisionCompleted = true
                provisionSubmitted = true
                mainHandler.removeCallbacks(submittedExitPollRunnable)
                tvPortalStatus.text = getString(R.string.portal_status_wait_reboot)
                waitForExitAttempts = 0
                mainHandler.postDelayed(
                    {
                        RobotWifiConnector.release()
                        waitForRobotNetworkExit()
                    },
                    SUCCESS_SETTLE_DELAY_MS
                )
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        RobotWifiConnector.release()
        webView.destroy()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (provisionSubmitted && !provisionCompleted) {
            scheduleSubmittedExitPoll()
        }
    }

    companion object {
        private const val PORTAL_BASE_URL = "http://192.168.4.1"
        private const val PORTAL_HOST = "192.168.4.1"
        private const val PORTAL_ALIAS_HOST = "robot.local"
        private const val ROBOT_WIFI_PREFIX = RobotBranding.PRIMARY_WIFI_PREFIX
        private const val SUCCESS_SETTLE_DELAY_MS = 6500L
        private const val WAIT_FOR_EXIT_INTERVAL_MS = 1000L
        private const val WAIT_FOR_EXIT_MAX_ATTEMPTS = 30
        private const val RETURN_TIMEOUT_FINISH_DELAY_MS = 1800L
        private const val SUBMITTED_EXIT_POLL_INTERVAL_MS = 1000L
        private const val SUBMITTED_EXIT_POLL_MAX_ATTEMPTS = 45
    }

    private fun isAllowedPortalUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme.orEmpty().lowercase()
        val host = uri.host.orEmpty().lowercase()
        if (scheme != "http") return false
        return host == PORTAL_HOST || host == PORTAL_ALIAS_HOST
    }

    private val submittedExitPollRunnable = Runnable { checkSubmittedExit() }
}
