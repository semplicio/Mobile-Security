package com.autombot.security.util

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var failedAttemptsThreshold: Int
        get() = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) = prefs.edit().putInt(KEY_THRESHOLD, value.coerceIn(1, 10)).apply()

    var currentFailedAttempts: Int
        get() = prefs.getInt(KEY_CURRENT_ATTEMPTS, 0)
        set(value) = prefs.edit().putInt(KEY_CURRENT_ATTEMPTS, value).apply()

    var destinationEmail: String
        get() = prefs.getString(KEY_DEST_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEST_EMAIL, value).apply()

    var alertTransport: String
        get() = prefs.getString(KEY_ALERT_TRANSPORT, TRANSPORT_GMAIL) ?: TRANSPORT_GMAIL
        set(value) = prefs.edit().putString(KEY_ALERT_TRANSPORT, value).apply()

    var googleAccountEmail: String
        get() = prefs.getString(KEY_GOOGLE_ACCOUNT_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GOOGLE_ACCOUNT_EMAIL, value).apply()

    var googleAccountConnected: Boolean
        get() = prefs.getBoolean(
            KEY_GOOGLE_ACCOUNT_CONNECTED,
            googleAccountEmail.contains("@")
        )
        set(value) = prefs.edit().putBoolean(KEY_GOOGLE_ACCOUNT_CONNECTED, value).apply()

    var smtpHost: String
        get() = prefs.getString(KEY_SMTP_HOST, "smtp.hostinger.com") ?: "smtp.hostinger.com"
        set(value) = prefs.edit().putString(KEY_SMTP_HOST, value).apply()

    var smtpPort: Int
        get() = prefs.getInt(KEY_SMTP_PORT, 465)
        set(value) = prefs.edit().putInt(KEY_SMTP_PORT, value).apply()

    var smtpUser: String
        get() = prefs.getString(KEY_SMTP_USER, "security@autombot.com.br") ?: "security@autombot.com.br"
        set(value) = prefs.edit().putString(KEY_SMTP_USER, value).apply()

    var smtpPassword: String
        get() = prefs.getString(KEY_SMTP_PASS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SMTP_PASS, value).apply()

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING_ENABLED, value).apply()

    var capturePhotosEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAPTURE_PHOTOS, true)
        set(value) = prefs.edit().putBoolean(KEY_CAPTURE_PHOTOS, value).apply()

    var photoCount: Int
        get() = prefs.getInt(KEY_PHOTO_COUNT, 3)
        set(value) = prefs.edit().putInt(KEY_PHOTO_COUNT, value.coerceIn(1, 5)).apply()

    var alarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALARM_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ALARM_ENABLED, value).apply()

    var ownerNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_OWNER_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_OWNER_NOTIFICATION, value).apply()

    var showAlertMessageEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHOW_MESSAGE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_MESSAGE, value).apply()

    var securityMessage: String
        get() = prefs.getString(KEY_SECURITY_MESSAGE, DEFAULT_SECURITY_MESSAGE) ?: DEFAULT_SECURITY_MESSAGE
        set(value) = prefs.edit().putString(KEY_SECURITY_MESSAGE, value.ifBlank { DEFAULT_SECURITY_MESSAGE }).apply()

    var recordAudioEnabled: Boolean
        get() = prefs.getBoolean(KEY_RECORD_AUDIO, false)
        set(value) = prefs.edit().putBoolean(KEY_RECORD_AUDIO, value).apply()

    var recordVideoEnabled: Boolean
        get() = prefs.getBoolean(KEY_RECORD_VIDEO, false)
        set(value) = prefs.edit().putBoolean(KEY_RECORD_VIDEO, value).apply()

    var appLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK, value).apply()

    var experimentalPowerProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXPERIMENTAL_POWER, false)
        set(value) = prefs.edit().putBoolean(KEY_EXPERIMENTAL_POWER, value).apply()

    var experimentalStatusBarBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXPERIMENTAL_STATUS_BAR, false)
        set(value) = prefs.edit().putBoolean(KEY_EXPERIMENTAL_STATUS_BAR, value).apply()

    fun incrementFailedAttempts(): Int {
        val next = currentFailedAttempts + 1
        currentFailedAttempts = next
        return next
    }

    fun resetFailedAttempts() {
        currentFailedAttempts = 0
    }

    fun isGmailConfigured(): Boolean =
        googleAccountConnected || googleAccountEmail.contains("@")

    fun isEmailConfigured(): Boolean = destinationEmail.isNotBlank()

    companion object {
        private const val PREFS_NAME = "autombot_security_prefs"
        private const val KEY_THRESHOLD = "failed_attempts_threshold"
        private const val KEY_CURRENT_ATTEMPTS = "current_failed_attempts"
        private const val KEY_DEST_EMAIL = "destination_email"
        private const val KEY_ALERT_TRANSPORT = "alert_transport"
        private const val KEY_GOOGLE_ACCOUNT_EMAIL = "google_account_email"
        private const val KEY_GOOGLE_ACCOUNT_CONNECTED = "google_account_connected"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_SMTP_USER = "smtp_user"
        private const val KEY_SMTP_PASS = "smtp_pass"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_CAPTURE_PHOTOS = "capture_photos_enabled"
        private const val KEY_PHOTO_COUNT = "photo_count"
        private const val KEY_ALARM_ENABLED = "alarm_enabled"
        private const val KEY_OWNER_NOTIFICATION = "owner_notification_enabled"
        private const val KEY_SHOW_MESSAGE = "show_alert_message_enabled"
        private const val KEY_SECURITY_MESSAGE = "security_message"
        private const val KEY_RECORD_AUDIO = "record_audio_enabled"
        private const val KEY_RECORD_VIDEO = "record_video_enabled"
        private const val KEY_APP_LOCK = "app_lock_enabled"
        private const val KEY_EXPERIMENTAL_POWER = "experimental_power_protection"
        private const val KEY_EXPERIMENTAL_STATUS_BAR = "experimental_status_bar_block"

        const val TRANSPORT_GMAIL = "gmail_oauth"
        const val TRANSPORT_SMTP = "smtp_fallback"
        const val DEFAULT_THRESHOLD = 2
        const val DEFAULT_SECURITY_MESSAGE = "Aparelho protegido pelo AutomBot Security. Tentativa de acesso registrada."
    }
}
