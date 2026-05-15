package com.maxcorp.gosha.mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SupportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support)

        val backButton = findViewById<Button>(R.id.btnBackToMenu)
        val policyButton = findViewById<Button>(R.id.btnOpenPrivacyPolicy)
        val policyLink = findViewById<TextView>(R.id.tvSupportPolicyLink)
        val termsButton = findViewById<Button>(R.id.btnOpenTermsOfUse)
        val termsLink = findViewById<TextView>(R.id.tvSupportTermsLink)

        policyLink.text = if (PrivacyPolicy.isConfigured()) {
            getString(R.string.support_policy_link_ready)
        } else {
            getString(R.string.support_policy_link_pending)
        }
        policyButton.visibility = if (PrivacyPolicy.isConfigured()) View.VISIBLE else View.GONE
        policyButton.setOnClickListener {
            if (!PrivacyPolicy.open(this)) {
                Toast.makeText(this, getString(R.string.support_policy_open_error), Toast.LENGTH_SHORT).show()
            }
        }

        termsLink.text = if (TermsOfUse.isConfigured()) {
            getString(R.string.support_terms_link_ready)
        } else {
            getString(R.string.support_terms_link_pending)
        }
        termsButton.visibility = if (TermsOfUse.isConfigured()) View.VISIBLE else View.GONE
        termsButton.setOnClickListener {
            if (!TermsOfUse.open(this)) {
                Toast.makeText(this, getString(R.string.support_terms_open_error), Toast.LENGTH_SHORT).show()
            }
        }

        UiPlayful.enhanceButtons(backButton, policyButton, termsButton)
        backButton.setOnClickListener { returnToMainMenu() }
    }

    private fun returnToMainMenu() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
