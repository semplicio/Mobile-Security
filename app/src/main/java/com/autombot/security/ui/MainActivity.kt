package com.autombot.security.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.autombot.security.admin.SecurityDeviceAdminReceiver
import com.autombot.security.databinding.ActivityMainBinding
import com.autombot.security.service.SecurityMonitorService
import com.autombot.security.util.AppLockSession
import com.autombot.security.util.PrefsManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private var unlockDialogVisible = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshUiState() }

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
            AppLockSession.suppressNextBackgroundLock()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
        enforceAppLockIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !AppLockSession.consumeBackgroundSuppression()) {
            AppLockSession.lock()
        }
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
            AppLockSession.suppressNextBackgroundLock()
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
        val perms = mutableListOf<String>()

        if (prefs.capturePhotosEnabled || prefs.recordVideoEnabled) {
            perms.add(android.Manifest.permission.CAMERA)
        }
        if (prefs.recordVideoEnabled) {
            perms.add(android.Manifest.permission.RECORD_AUDIO)
        }
        perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        perms.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                needed.add(it)
            }
        }
        if (needed.isNotEmpty()) {
            AppLockSession.suppressNextBackgroundLock()
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

    private fun enforceAppLockIfNeeded() {
        if (!prefs.appLockEnabled || AppLockSession.unlocked || unlockDialogVisible) {
            binding.root.visibility = View.VISIBLE
            return
        }

        if (!prefs.hasAppLockPassword()) {
            // Compatibilidade com instalações anteriores, nas quais a chave de
            // bloqueio existia mas nenhuma senha local havia sido criada.
            prefs.appLockEnabled = false
            binding.root.visibility = View.VISIBLE
            return
        }

        binding.root.visibility = View.INVISIBLE
        unlockDialogVisible = true

        val password = EditText(this).apply {
            hint = "Senha do AutomBot"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("AutomBot Security bloqueado")
            .setMessage("Digite a senha do aplicativo para continuar.")
            .setView(password)
            .setCancelable(false)
            .setPositiveButton("Desbloquear", null)
            .setNegativeButton("Sair") { _, _ -> finishAndRemoveTask() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = password.text?.toString().orEmpty()
                if (prefs.verifyAppLockPassword(value)) {
                    AppLockSession.unlock()
                    unlockDialogVisible = false
                    binding.root.visibility = View.VISIBLE
                    dialog.dismiss()
                } else {
                    password.error = "Senha incorreta"
                }
            }
        }
        dialog.setOnDismissListener { unlockDialogVisible = false }
        dialog.show()
    }
}
