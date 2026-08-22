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
    private val handler = Handler(Looper.getMainLooper())

    private val productionFallback = Runnable {
        showStatusNotification(
            "Evidência parcial",
            "O Android não concluiu a captura visual; localização e alerta serão enviados."
        )
        sendEvidence(emptyList(), isTest = false)
    }

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
            ACTION_PROCESS_CAPTURED_EVIDENCE -> handleCapturedEvidence(intent)
            ACTION_TEST_ALERT -> handleTestAlert()
            ACTION_FIND_DEVICE -> alarmHelper.playAlarm()
            ACTION_STOP_ALARM -> alarmHelper.stopAlarm()
        }

        return START_STICKY
    }

    private fun handleIntrusionDetected() {
        if (prefs.alarmEnabled) alarmHelper.playAlarm()
        if (prefs.showAlertMessageEnabled) showSecurityAlertNotification()

        handler.removeCallbacks(productionFallback)
        handler.postDelayed(productionFallback, PRODUCTION_CAPTURE_TIMEOUT_MS)
        handler.postDelayed({ prefs.resetFailedAttempts() }, 5000)
    }

    private fun handleCapturedEvidence(intent: Intent) {
        handler.removeCallbacks(productionFallback)

        val evidence = intent.getStringArrayListExtra(EXTRA_EVIDENCE_PATHS)
            .orEmpty()
            .map(::File)
            .filter { it.exists() && it.isFile }

        if (evidence.isEmpty()) {
            showStatusNotification(
                "Evidência sem mídia",
                "O alerta seguirá com localização, mas sem foto, áudio ou vídeo."
            )
        }

        sendEvidence(evidence, isTest = false)
    }

    private fun handleTestAlert() {
        showStatusNotification(
            "Teste em andamento",
            "Validando câmera, localização e envio do alerta."
        )
        captureAndSendTest()
    }

    private fun captureAndSendTest() {
        if (prefs.capturePhotosEnabled) {
            cameraHelper.capturePhotos(
                lifecycleOwner = this,
                count = prefs.photoCount,
                onComplete = { photos -> sendEvidence(photos, isTest = true) },
                onError = {
                    showStatusNotification(
                        "Falha na captura",
                        "A câmera não pôde ser usada. O alerta seguirá sem foto."
                    )
                    sendEvidence(emptyList(), isTest = true)
                }
            )
        } else {
            sendEvidence(emptyList(), isTest = true)
        }
    }

    private fun sendEvidence(evidence: List<File>, isTest: Boolean) {
        locationHelper.getCurrentLocation { location ->
            val mapsLink = location?.let { locationHelper.mapsLink(it) }

            if (!prefs.ownerNotificationEnabled) {
                if (isTest) {
                    showStatusNotification(
                        "Teste concluído sem envio",
                        "O alerta ao proprietário está desativado nas configurações."
                    )
                }
                return@getCurrentLocation
            }

            emailExecutor.execute {
                val success = EmailSender(prefs).sendIntrusionAlert(
                    evidence = evidence,
                    mapsLink = mapsLink,
                    isTest = isTest
                )

                if (isTest) {
                    showStatusNotification(
                        if (success) "Teste concluído" else "Falha no envio do teste",
                        if (success) {
                            "Alerta enviado. Arquivos anexados: ${evidence.size}."
                        } else {
                            "Confira a configuração administrativa do build e a conexão de rede."
                        }
                    )
                }
            }
        }
    }

    private fun showSecurityAlertNotification() {
        showStatusNotification("Aparelho protegido", prefs.securityMessage)
    }

    private fun showStatusNotification(title: String, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            SECURITY_ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun buildMonitoringNotification(): android.app.Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
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
                NotificationChannel(ALERT_CHANNEL_ID, "Alertas de segurança", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(productionFallback)
        alarmHelper.stopAlarm()
        emailExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_INTRUSION_DETECTED = "com.autombot.security.action.INTRUSION_DETECTED"
        const val ACTION_PROCESS_CAPTURED_EVIDENCE = "com.autombot.security.action.PROCESS_CAPTURED_EVIDENCE"
        const val ACTION_TEST_ALERT = "com.autombot.security.action.TEST_ALERT"
        const val ACTION_FIND_DEVICE = "com.autombot.security.action.FIND_DEVICE"
        const val ACTION_STOP_ALARM = "com.autombot.security.action.STOP_ALARM"
        const val EXTRA_EVIDENCE_PATHS = "evidence_paths"

        private const val CHANNEL_ID = "autombot_security_monitoring"
        private const val ALERT_CHANNEL_ID = "autombot_security_alerts"
        private const val NOTIFICATION_ID = 1001
        private const val SECURITY_ALERT_NOTIFICATION_ID = 1002
        private const val PRODUCTION_CAPTURE_TIMEOUT_MS = 22000L
    }
}
