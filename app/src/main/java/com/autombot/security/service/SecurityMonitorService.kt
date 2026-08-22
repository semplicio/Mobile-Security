package com.autombot.security.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.autombot.security.R
import com.autombot.security.ui.MainActivity
import com.autombot.security.util.AlarmHelper
import com.autombot.security.util.CameraCaptureHelper
import com.autombot.security.util.EmailSender
import com.autombot.security.util.LocationHelper
import com.autombot.security.util.PrefsManager
import java.io.File
import java.util.concurrent.Executors

class SecurityMonitorService : LifecycleService() {

    private lateinit var prefs: PrefsManager
    private lateinit var alarmHelper: AlarmHelper
    private lateinit var cameraHelper: CameraCaptureHelper
    private lateinit var locationHelper: LocationHelper
    private val emailExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        alarmHelper = AlarmHelper(this)
        cameraHelper = CameraCaptureHelper(this)
        locationHelper = LocationHelper(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, buildMonitoringNotification())

        when (intent?.action) {
            ACTION_INTRUSION_DETECTED -> handleIntrusionDetected()
            ACTION_FIND_DEVICE -> alarmHelper.playAlarm()
            ACTION_STOP_ALARM -> alarmHelper.stopAlarm()
        }

        return START_STICKY
    }

    private fun handleIntrusionDetected() {
        if (prefs.alarmEnabled) {
            alarmHelper.playAlarm()
        }

        if (prefs.showAlertMessageEnabled) {
            showSecurityAlertNotification()
        }

        if (prefs.capturePhotosEnabled) {
            cameraHelper.capturePhotos(
                lifecycleOwner = this,
                count = prefs.photoCount,
                onComplete = { photos -> sendEvidence(photos) },
                onError = { sendEvidence(emptyList()) }
            )
        } else {
            sendEvidence(emptyList())
        }

        Handler(Looper.getMainLooper()).postDelayed({ prefs.resetFailedAttempts() }, 5000)
    }

    private fun sendEvidence(photos: List<File>) {
        locationHelper.getCurrentLocation { location ->
            val mapsLink = location?.let { locationHelper.mapsLink(it) }
            if (prefs.ownerNotificationEnabled) {
                emailExecutor.execute {
                    EmailSender(prefs).sendIntrusionAlert(photos, mapsLink)
                }
            }
        }
    }

    private fun showSecurityAlertNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            SECURITY_ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("Aparelho protegido")
                .setContentText("Foram detectadas tentativas de acesso não autorizado.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun buildMonitoringNotification(): android.app.Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_monitoring_title))
            .setContentText(getString(R.string.notification_monitoring_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Alertas de segurança",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    override fun onDestroy() {
        alarmHelper.stopAlarm()
        emailExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_INTRUSION_DETECTED = "com.autombot.security.action.INTRUSION_DETECTED"
        const val ACTION_FIND_DEVICE = "com.autombot.security.action.FIND_DEVICE"
        const val ACTION_STOP_ALARM = "com.autombot.security.action.STOP_ALARM"

        private const val CHANNEL_ID = "autombot_security_monitoring"
        private const val ALERT_CHANNEL_ID = "autombot_security_alerts"
        private const val NOTIFICATION_ID = 1001
        private const val SECURITY_ALERT_NOTIFICATION_ID = 1002
    }
}
