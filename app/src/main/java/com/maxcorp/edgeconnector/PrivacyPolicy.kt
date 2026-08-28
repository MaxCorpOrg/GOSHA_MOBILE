package com.maxcorp.gosha.mobile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import java.net.URI

internal fun isHttpUrl(url: String): Boolean {
    return try {
        val uri = URI(url.trim())
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }
}

private fun openInternalDocument(
    context: Context,
    title: String,
    subtitle: String,
    url: String,
): Boolean {
    if (!isHttpUrl(url)) return false
    return try {
        context.startActivity(
            Intent(context, LegalDocumentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(LegalDocumentActivity.EXTRA_TITLE, title)
                putExtra(LegalDocumentActivity.EXTRA_SUBTITLE, subtitle)
                putExtra(LegalDocumentActivity.EXTRA_URL, url)
            }
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

object PrivacyPolicy {
    fun url(): String = BuildConfig.PRIVACY_POLICY_URL.trim()

    fun isConfigured(): Boolean = isHttpUrl(url())

    fun open(context: Context): Boolean = openInternalDocument(
        context = context,
        title = context.getString(R.string.legal_document_privacy_title),
        subtitle = context.getString(R.string.legal_document_privacy_subtitle),
        url = url(),
    )
}

object TermsOfUse {
    fun url(): String = BuildConfig.TERMS_OF_USE_URL.trim()

    fun isConfigured(): Boolean = isHttpUrl(url())

    fun open(context: Context): Boolean = openInternalDocument(
        context = context,
        title = context.getString(R.string.legal_document_terms_title),
        subtitle = context.getString(R.string.legal_document_terms_subtitle),
        url = url(),
    )
}
