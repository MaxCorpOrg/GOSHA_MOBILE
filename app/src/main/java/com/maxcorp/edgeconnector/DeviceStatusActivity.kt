package com.maxcorp.gosha.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
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
        findViewById<Button>(R.id.btnBackToMenu).setOnClickListener { returnToMainMenu() }
        UiPlayful.enhanceButtons(findViewById(R.id.btnBackToMenu))
    }

    private fun returnToMainMenu() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
