package com.autombot.security.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.autombot.security.admin.SecurityDeviceAdminReceiver
import com.autombot.security.databinding.ActivityMainBinding
import com.autombot.security.service.SecurityMonitorService
import com.autombot.security.util.PrefsManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* resultados tratados em refreshUiState() */ refreshUiState() }

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshUiState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        devicePolicyManager = getSystemService(DevicePolicyManager::class.java)
        adminComponent = ComponentName(this, SecurityDeviceAdminReceiver::class.java)

        binding.switchMonitoring.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) enableProtection() else disableProtection()
        }

        binding.btnFindDevice.setOnClickListener {
            startForegroundService(
                Intent(this, SecurityMonitorService::class.java).apply {
                    action = SecurityMonitorService.ACTION_FIND_DEVICE
                }
            )
        }

        binding.btnStopAlarm.setOnClickListener {
            startForegroundService(
                Intent(this, SecurityMonitorService::class.java).apply {
                    action = SecurityMonitorService.ACTION_STOP_ALARM
                }
            )
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
    }

    private fun enableProtection() {
        requestRuntimePermissionsIfNeeded()

        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(com.autombot.security.R.string.device_admin_explanation)
                )
            }
            deviceAdminLauncher.launch(intent)
            return
        }

        prefs.monitoringEnabled = true
        prefs.resetFailedAttempts()
        startForegroundService(Intent(this, SecurityMonitorService::class.java))
        refreshUiState()
    }

    private fun disableProtection() {
        prefs.monitoringEnabled = false
        stopService(Intent(this, SecurityMonitorService::class.java))
        refreshUiState()
    }

    private fun requestRuntimePermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        val perms = mutableListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        perms.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                needed.add(it)
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun refreshUiState() {
        val adminActive = devicePolicyManager.isAdminActive(adminComponent)
        binding.switchMonitoring.isChecked = prefs.monitoringEnabled && adminActive
        binding.tvStatus.text = if (prefs.monitoringEnabled && adminActive)
            getString(com.autombot.security.R.string.status_protected)
        else
            getString(com.autombot.security.R.string.status_unprotected)
    }
}
