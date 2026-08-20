package com.autombot.security.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.autombot.security.databinding.ActivitySettingsBinding
import com.autombot.security.util.PrefsManager

/**
 * Tela de configurações: limite de tentativas erradas para disparar o alerta,
 * e-mail de destino e credenciais SMTP do remetente.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        loadCurrentValues()

        binding.btnSave.setOnClickListener {
            saveValues()
            finish()
        }
    }

    private fun loadCurrentValues() {
        binding.etThreshold.setText(prefs.failedAttemptsThreshold.toString())
        binding.etDestEmail.setText(prefs.destinationEmail)
        binding.etSmtpHost.setText(prefs.smtpHost)
        binding.etSmtpPort.setText(prefs.smtpPort.toString())
        binding.etSmtpUser.setText(prefs.smtpUser)
        binding.etSmtpPass.setText(prefs.smtpPassword)
    }

    private fun saveValues() {
        prefs.failedAttemptsThreshold =
            binding.etThreshold.text.toString().toIntOrNull() ?: PrefsManager.DEFAULT_THRESHOLD
        prefs.destinationEmail = binding.etDestEmail.text.toString().trim()
        prefs.smtpHost = binding.etSmtpHost.text.toString().trim()
        prefs.smtpPort = binding.etSmtpPort.text.toString().toIntOrNull() ?: 587
        prefs.smtpUser = binding.etSmtpUser.text.toString().trim()
        prefs.smtpPassword = binding.etSmtpPass.text.toString()
    }
}
