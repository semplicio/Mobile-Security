package com.autombot.security.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.autombot.security.databinding.ActivitySettingsBinding
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

        binding.btnSave.setOnClickListener {
            saveValues()
            finish()
        }
    }

    private fun loadCurrentValues() = with(binding) {
        etThreshold.setText(prefs.failedAttemptsThreshold.toString())
        etDestEmail.setText(prefs.destinationEmail)
        etSmtpHost.setText(prefs.smtpHost)
        etSmtpPort.setText(prefs.smtpPort.toString())
        etSmtpUser.setText(prefs.smtpUser)
        etSmtpPass.setText(prefs.smtpPassword)

        switchCapturePhotos.isChecked = prefs.capturePhotosEnabled
        etPhotoCount.setText(prefs.photoCount.toString())
        etPhotoCount.isEnabled = prefs.capturePhotosEnabled
        switchAlarm.isChecked = prefs.alarmEnabled
        switchOwnerNotification.isChecked = prefs.ownerNotificationEnabled
        switchShowMessage.isChecked = prefs.showAlertMessageEnabled
        switchAppLock.isChecked = prefs.appLockEnabled
    }

    private fun saveValues() = with(binding) {
        prefs.failedAttemptsThreshold =
            etThreshold.text?.toString()?.toIntOrNull() ?: PrefsManager.DEFAULT_THRESHOLD
        prefs.destinationEmail = etDestEmail.text?.toString()?.trim().orEmpty()
        prefs.smtpHost = etSmtpHost.text?.toString()?.trim().orEmpty().ifBlank { "smtp.hostinger.com" }
        prefs.smtpPort = etSmtpPort.text?.toString()?.toIntOrNull() ?: 465
        prefs.smtpUser = etSmtpUser.text?.toString()?.trim().orEmpty()
        prefs.smtpPassword = etSmtpPass.text?.toString().orEmpty()

        prefs.capturePhotosEnabled = switchCapturePhotos.isChecked
        prefs.photoCount = etPhotoCount.text?.toString()?.toIntOrNull() ?: 3
        prefs.alarmEnabled = switchAlarm.isChecked
        prefs.ownerNotificationEnabled = switchOwnerNotification.isChecked
        prefs.showAlertMessageEnabled = switchShowMessage.isChecked
        prefs.appLockEnabled = switchAppLock.isChecked
    }
}
