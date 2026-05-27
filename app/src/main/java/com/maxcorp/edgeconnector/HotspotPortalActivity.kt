package com.maxcorp.gosha.mobile

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HotspotPortalActivity : AppCompatActivity() {
    private val logTag = "HotspotPortal"
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var webView: WebView
    private lateinit var tvPortalStatus: TextView
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
            // Some local captive portals serve a broken or reduced page to embedded WebViews.
            userAgentString = browserLikeUserAgent(userAgentString)
        }
        // Tecno/Mediatek WebView can render a blank white surface for local captive portals.
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        webView.setBackgroundColor(Color.WHITE)
        webView.addJavascriptInterface(PortalBridge(), "RobotPortalBridge")
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
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
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                Log.d(logTag, "Portal page started: $url")
                super.onPageStarted(view, url, favicon)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                if (isAllowedPortalUrl(url)) {
                    Log.d(logTag, "Allow portal navigation to $url")
                    return false
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

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request?.isForMainFrame == true) {
                    Log.d(
                        logTag,
                        "Portal main-frame request: method=${request.method} url=${request.url} headers=${request.requestHeaders}"
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                if (request?.isForMainFrame == true) {
                    Log.w(
                        logTag,
                        "Portal HTTP error: status=${errorResponse?.statusCode} reason=${errorResponse?.reasonPhrase} url=${request.url}"
                    )
                }
                super.onReceivedHttpError(view, request, errorResponse)
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

        loadPortal(resetAttempts = true)
    }

    private fun loadPortal(resetAttempts: Boolean) {
        if (resetAttempts) {
            reloadAttempts = 0
        }
        provisionSubmitted = false
        provisionCompleted = false
        waitForExitAttempts = 0
        submissionExitPollAttempts = 0
        tvPortalStatus.text = getString(R.string.portal_status_opening)
        Log.d(logTag, "Loading portal page in WebView: $PORTAL_BASE_URL")
        webView.loadUrl(PORTAL_BASE_URL)
    }

    private fun retryLoad() {
        if (reloadAttempts >= 4) {
            tvPortalStatus.text = getString(R.string.portal_status_error)
            return
        }
        reloadAttempts += 1
        val delayMs = if (reloadAttempts < 2) 700L else 1200L
        tvPortalStatus.text = getString(R.string.portal_status_retry, reloadAttempts)
        mainHandler.postDelayed({ loadPortal(resetAttempts = false) }, delayMs)
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

    private fun browserLikeUserAgent(original: String): String {
        return original
            .replace("; wv", "")
            .replace("Version/4.0 ", "")
            .replace(" wv)", ")")
            .trim()
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
