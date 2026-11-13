package com.chyps.solver

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val switchService = findViewById<Switch>(R.id.switchService)
        
        switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Xin quyền bỏ qua tối ưu pin (tránh bị Android kill ngầm)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
                // Mở màn hình xin quyền Trợ năng
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                switchService.isChecked = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val isEnabled = isAccessibilityServiceEnabled()
        findViewById<Switch>(R.id.switchService).isChecked = isEnabled
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${SolverService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(service) == true
    }
}
