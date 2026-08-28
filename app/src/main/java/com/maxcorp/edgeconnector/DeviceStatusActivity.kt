package com.maxcorp.gosha.mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DeviceStatusActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_status)

        val draft = ConfigStore(this).loadDraft()
        findViewById<TextView>(R.id.tvStatusRobot).text =
            draft.robotName.ifBlank { draft.robotId.ifBlank { getString(R.string.device_status_robot_empty) } }
        findViewById<TextView>(R.id.tvStatusWifi).text =
            WifiInfoHelper.currentSsid(this).ifBlank { getString(R.string.device_status_wifi_empty) }
        findViewById<TextView>(R.id.tvStatusPlan).text = draft.planName.ifBlank { draft.planCode.ifBlank { getString(R.string.menu_value_empty) } }
        findViewById<TextView>(R.id.tvStatusBackground).setText(
            if (BackgroundAccess.isTranssionFamily()) {
                R.string.background_access_transsion_instruction
            } else {
                R.string.background_access_generic_instruction
            }
        )
        findViewById<Button>(R.id.btnBackgroundSettings).setOnClickListener {
            if (!BackgroundAccess.openSettings(this)) {
                Toast.makeText(this, R.string.background_access_open_error, Toast.LENGTH_SHORT).show()
            }
        }
        val notificationSettingsBody = findViewById<TextView>(R.id.tvStatusNotifications)
        val notificationSettingsButton = findViewById<Button>(R.id.btnNotificationSettings)
        val notificationsNeedAttention = !BackgroundAccess.canPostNotifications(this)
        notificationSettingsBody.visibility =
            if (notificationsNeedAttention) View.VISIBLE else View.GONE
        notificationSettingsButton.visibility =
            if (notificationsNeedAttention) View.VISIBLE else View.GONE
        notificationSettingsButton.setOnClickListener {
            if (!BackgroundAccess.openNotificationSettings(this)) {
                Toast.makeText(this, R.string.background_access_open_error, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnBackToMenu).setOnClickListener { returnToMainMenu() }
        UiPlayful.enhanceButtons(
            findViewById(R.id.btnBackgroundSettings),
            notificationSettingsButton,
            findViewById(R.id.btnBackToMenu),
        )
    }

    private fun returnToMainMenu() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
