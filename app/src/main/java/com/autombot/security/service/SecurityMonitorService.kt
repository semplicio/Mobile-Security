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
import java.util.concurrent.Executors

/**
 * Serviço que roda em segundo plano (foreground service) enquanto o
 * monitoramento está ativo. Ao receber ACTION_INTRUSION_DETECTED, ele:
 *
 *  1. Toca o alarme sonoro
 *  2. Tira uma foto pela câmera frontal
 *  3. Pega a localização atual
 *  4. Envia tudo por e-mail para o dono do aparelho
 *
 * Também atende ACTION_FIND_DEVICE, para o próprio dono localizar o
 * aparelho tocando o alarme manualmente (função "achar dentro de casa").
 *
 * LifecycleService (em vez de Service puro) porque o CameraCaptureHelper
 * precisa de um LifecycleOwner para fazer bind da câmera.
 */
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
        // 1. Alarme imediato
        alarmHelper.playAlarm()

        // 2. Foto pela câmera frontal
        cameraHelper.capturePhoto(
            lifecycleOwner = this,
            onSuccess = { photoFile ->
                // 3. Localização, depois envia tudo por e-mail em background
                locationHelper.getCurrentLocation { location ->
                    val mapsLink = location?.let { locationHelper.mapsLink(it) }
                    emailExecutor.execute {
                        EmailSender(prefs).sendIntrusionAlert(photoFile, mapsLink)
                    }
                }
            },
            onError = {
                // Mesmo sem foto, tenta mandar o alerta com localização
                locationHelper.getCurrentLocation { location ->
                    val mapsLink = location?.let { locationHelper.mapsLink(it) }
                    emailExecutor.execute {
                        EmailSender(prefs).sendIntrusionAlert(null, mapsLink)
                    }
                }
            }
        )

        // Zera o contador após reagir, para não disparar de novo a cada tentativa subsequente
        // (opcional — pode-se preferir manter dificultando o acesso; ajustável depois)
        Handler(Looper.getMainLooper()).postDelayed({ prefs.resetFailedAttempts() }, 2000)
    }

    private fun buildMonitoringNotification(): android.app.Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        alarmHelper.stopAlarm()
        super.onDestroy()
    }

    companion object {
        const val ACTION_INTRUSION_DETECTED = "com.autombot.security.action.INTRUSION_DETECTED"
        const val ACTION_FIND_DEVICE = "com.autombot.security.action.FIND_DEVICE"
        const val ACTION_STOP_ALARM = "com.autombot.security.action.STOP_ALARM"

        private const val CHANNEL_ID = "autombot_security_monitoring"
        private const val NOTIFICATION_ID = 1001
    }
}
