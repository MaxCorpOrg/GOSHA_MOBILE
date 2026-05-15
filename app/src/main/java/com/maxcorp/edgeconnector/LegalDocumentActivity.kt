package com.maxcorp.gosha.mobile

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LegalDocumentActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal_document)

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE).orEmpty()
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()

        val titleView = findViewById<TextView>(R.id.tvLegalTitle)
        val subtitleView = findViewById<TextView>(R.id.tvLegalSubtitle)
        val statusView = findViewById<TextView>(R.id.tvLegalStatus)
        val webView = findViewById<WebView>(R.id.legalWebView)
        val backButton = findViewById<Button>(R.id.btnLegalBack)

        titleView.text = title.ifBlank { getString(R.string.legal_document_title_default) }
        subtitleView.text = subtitle.ifBlank { getString(R.string.legal_document_subtitle_default) }
        statusView.text = getString(R.string.legal_document_status_loading)

        UiPlayful.enhanceButtons(backButton)
        backButton.setOnClickListener { finish() }

        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString().orEmpty()
                return !isAllowedLegalUrl(target)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                statusView.text = getString(R.string.legal_document_status_ready)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    statusView.text = getString(R.string.legal_document_status_error)
                }
            }
        }

        if (isAllowedLegalUrl(url)) {
            webView.loadUrl(url)
        } else {
            statusView.text = getString(R.string.legal_document_status_error)
        }
    }

    companion object {
        const val EXTRA_TITLE = "extra_legal_title"
        const val EXTRA_SUBTITLE = "extra_legal_subtitle"
        const val EXTRA_URL = "extra_legal_url"
    }

    private fun isAllowedLegalUrl(url: String): Boolean {
        val normalized = url.trim().trimEnd('/')
        if (normalized.isBlank()) return false
        val allowed = listOf(PrivacyPolicy.url(), TermsOfUse.url())
            .map { it.trim().trimEnd('/') }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .toSet()
        return normalized in allowed
    }
}
