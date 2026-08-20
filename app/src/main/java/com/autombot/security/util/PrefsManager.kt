package com.autombot.security.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Centraliza todas as configurações do AutomBot Security.
 * Guardado em SharedPreferences simples na base (versão futura pode migrar
 * credenciais sensíveis para EncryptedSharedPreferences).
 */
class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Quantas tentativas erradas de senha até disparar a captura + alarme
    var failedAttemptsThreshold: Int
        get() = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) = prefs.edit().putInt(KEY_THRESHOLD, value).apply()

    // Contador atual de tentativas erradas (zera quando a senha certa é digitada)
    var currentFailedAttempts: Int
        get() = prefs.getInt(KEY_CURRENT_ATTEMPTS, 0)
        set(value) = prefs.edit().putInt(KEY_CURRENT_ATTEMPTS, value).apply()

    // E-mail de destino (o dono do aparelho) que vai receber os alertas
    var destinationEmail: String
        get() = prefs.getString(KEY_DEST_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEST_EMAIL, value).apply()

    // Conta SMTP remetente (pode ser a mesma conta do usuário, ex.: Gmail com senha de app)
    var smtpHost: String
        get() = prefs.getString(KEY_SMTP_HOST, "smtp.gmail.com") ?: "smtp.gmail.com"
        set(value) = prefs.edit().putString(KEY_SMTP_HOST, value).apply()

    var smtpPort: Int
        get() = prefs.getInt(KEY_SMTP_PORT, 587)
        set(value) = prefs.edit().putInt(KEY_SMTP_PORT, value).apply()

    var smtpUser: String
        get() = prefs.getString(KEY_SMTP_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SMTP_USER, value).apply()

    var smtpPassword: String
        get() = prefs.getString(KEY_SMTP_PASS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SMTP_PASS, value).apply()

    // Liga/desliga o monitoramento
    var monitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING_ENABLED, value).apply()

    fun incrementFailedAttempts(): Int {
        val next = currentFailedAttempts + 1
        currentFailedAttempts = next
        return next
    }

    fun resetFailedAttempts() {
        currentFailedAttempts = 0
    }

    fun isEmailConfigured(): Boolean =
        destinationEmail.isNotBlank() && smtpUser.isNotBlank() && smtpPassword.isNotBlank()

    companion object {
        private const val PREFS_NAME = "autombot_security_prefs"
        private const val KEY_THRESHOLD = "failed_attempts_threshold"
        private const val KEY_CURRENT_ATTEMPTS = "current_failed_attempts"
        private const val KEY_DEST_EMAIL = "destination_email"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_SMTP_USER = "smtp_user"
        private const val KEY_SMTP_PASS = "smtp_pass"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"

        const val DEFAULT_THRESHOLD = 2 // no 2º erro já dispara, como você pediu no exemplo
    }
}
