package com.autombot.security.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.autombot.security.databinding.ActivitySettingsBinding
import com.autombot.security.service.SecurityMonitorService
import com.autombot.security.util.PrefsManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        loadCurrentValues()

        binding.switchCapturePhotos.setOnCheckedChangeListener { _, enabled ->
            binding.etPhotoCount.isEnabled = enabled
        }
        binding.switchShowMessage.setOnCheckedChangeListener { _, enabled ->
            binding.etSecurityMessage.isEnabled = enabled
        }

        binding.btnExperimentalFeatures.setOnClickListener {
            startActivity(Intent(this, ExperimentalFeaturesActivity::class.java))
        }

        binding.btnTestAlert.setOnClickListener {
            saveValues()
            val intent = Intent(this, SecurityMonitorService::class.java).apply {
                action = SecurityMonitorService.ACTION_TEST_ALERT
            }
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(
                this,
                "Teste iniciado. Confira Evidências e o e-mail do proprietário.",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.btnSave.setOnClickListener {
            saveValues()
            Toast.makeText(this, "Configurações salvas", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadCurrentValues() = with(binding) {
        etThreshold.setText(prefs.failedAttemptsThreshold.toString())
        etDestEmail.setText(prefs.destinationEmail)
        switchCapturePhotos.isChecked = prefs.capturePhotosEnabled
        etPhotoCount.setText(prefs.photoCount.toString())
        etPhotoCount.isEnabled = prefs.capturePhotosEnabled
        switchRecordAudio.isChecked = prefs.recordAudioEnabled
        switchRecordVideo.isChecked = prefs.recordVideoEnabled
        switchAlarm.isChecked = prefs.alarmEnabled
        switchOwnerNotification.isChecked = prefs.ownerNotificationEnabled
        switchShowMessage.isChecked = prefs.showAlertMessageEnabled
        etSecurityMessage.setText(prefs.securityMessage)
        etSecurityMessage.isEnabled = prefs.showAlertMessageEnabled
        switchAppLock.isChecked = prefs.appLockEnabled
    }

    private fun saveValues() = with(binding) {
        prefs.failedAttemptsThreshold =
            etThreshold.text?.toString()?.toIntOrNull() ?: PrefsManager.DEFAULT_THRESHOLD
        prefs.destinationEmail = etDestEmail.text?.toString()?.trim().orEmpty()
        prefs.capturePhotosEnabled = switchCapturePhotos.isChecked
        prefs.photoCount = etPhotoCount.text?.toString()?.toIntOrNull() ?: 3
        prefs.recordAudioEnabled = switchRecordAudio.isChecked
        prefs.recordVideoEnabled = switchRecordVideo.isChecked
        prefs.alarmEnabled = switchAlarm.isChecked
        prefs.ownerNotificationEnabled = switchOwnerNotification.isChecked
        prefs.showAlertMessageEnabled = switchShowMessage.isChecked
        prefs.securityMessage = etSecurityMessage.text?.toString()?.trim().orEmpty()
        prefs.appLockEnabled = switchAppLock.isChecked
    }
}
