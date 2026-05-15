package com.maxcorp.gosha.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ClientCabinetActivity : AppCompatActivity() {
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private lateinit var configStore: ConfigStore
    private lateinit var etName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var tvRobot: TextView
    private lateinit var tvPlan: TextView
    private lateinit var tvDates: TextView
    private lateinit var tvPayment: TextView
    private lateinit var tvCabinetStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_cabinet)

        configStore = ConfigStore(this)
        etName = findViewById(R.id.etCabinetName)
        etEmail = findViewById(R.id.etCabinetEmail)
        etPhone = findViewById(R.id.etCabinetPhone)
        tvRobot = findViewById(R.id.tvCabinetRobot)
        tvPlan = findViewById(R.id.tvCabinetPlan)
        tvDates = findViewById(R.id.tvCabinetDates)
        tvPayment = findViewById(R.id.tvCabinetPayment)
        tvCabinetStatus = findViewById(R.id.tvCabinetStatus)

        renderDraft()

        findViewById<Button>(R.id.btnCabinetSave).setOnClickListener { saveProfile() }
        findViewById<Button>(R.id.btnBackToMenu).setOnClickListener { returnToMainMenu() }
        UiPlayful.enhanceButtons(findViewById(R.id.btnCabinetSave), findViewById(R.id.btnBackToMenu))
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    private fun renderDraft() {
        val draft = configStore.loadDraft()
        tvRobot.text = draft.robotName.ifBlank { draft.robotId.ifBlank { getString(R.string.cabinet_robot_empty) } }
        etName.setText(draft.ownerName)
        etEmail.setText(draft.ownerEmail)
        etPhone.setText(draft.ownerPhone)
        tvPlan.text = draft.planName.ifBlank { draft.planCode.ifBlank { getString(R.string.menu_value_empty) } }
        tvDates.text = getString(
            R.string.cabinet_dates_range,
            draft.billingStart.ifBlank { getString(R.string.menu_value_empty) },
            draft.billingEnd.ifBlank { getString(R.string.menu_value_empty) }
        )
        tvPayment.text = draft.paymentStatus.ifBlank { getString(R.string.menu_value_empty) }
        tvCabinetStatus.text = getString(R.string.cabinet_status_ready)
    }

    private fun saveProfile() {
        val current = configStore.loadDraft()
        val updated = current.copy(
            ownerName = etName.text?.toString()?.trim().orEmpty(),
            ownerEmail = etEmail.text?.toString()?.trim().orEmpty(),
            ownerPhone = etPhone.text?.toString()?.trim().orEmpty(),
        )
        configStore.saveDraft(updated)
        tvCabinetStatus.text = getString(R.string.cabinet_status_saving)

        uiScope.launch {
            try {
                if (updated.robotId.isNotBlank()) {
                    PanelApiClient.updateOwner(
                        http = httpClient,
                        baseUrl = updated.panelBaseUrl.ifBlank { "http://151.241.228.232:18876" },
                        robotId = updated.robotId,
                        draft = updated,
                    )
                }
                tvCabinetStatus.text = getString(R.string.cabinet_status_saved)
                toast(getString(R.string.cabinet_toast_updated))
            } catch (exc: Exception) {
                tvCabinetStatus.text = getString(
                    R.string.cabinet_status_saved_with_panel_error,
                    exc.message ?: getString(R.string.menu_value_empty)
                )
                toast(getString(R.string.cabinet_toast_updated_local))
            }
        }
    }

    private fun returnToMainMenu() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
