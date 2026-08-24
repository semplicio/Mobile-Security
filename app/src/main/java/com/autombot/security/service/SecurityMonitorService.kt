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
import com.autombot.security.util.GmailApiSender
import com.autombot.security.util.IncidentHistoryLogger
import com.autombot.security.util.IncidentInfoCollector
import com.autombot.security.util.IncidentDetails
import com.autombot.security.util.LocationHelper
import com.autombot.security.util.PendingIncidentStore
import com.autombot.security.util.PrefsManager
import com.autombot.security.worker.PendingIncidentScheduler
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
            "A captura de mídia não foi concluída a tempo. O alerta seguirá com localização e dados do incidente."
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

        // Recupera qualquer incidente que tenha permanecido pendente após
        // encerramento do processo ou reinicialização inesperada.
        PendingIncidentScheduler.enqueueAllPending(this)
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

        // Não envia o e-mail imediatamente. Primeiro dá tempo para a Activity
        // visível registrar as mídias habilitadas pelo proprietário. Se o Android
        // impedir a abertura da tela ou a captura falhar, o fallback envia o
        // alerta somente com localização e dados técnicos.
        handler.removeCallbacks(productionFallback)
        handler.postDelayed(productionFallback, PRODUCTION_CAPTURE_TIMEOUT_MS)
    }

    private fun handleCapturedEvidence(intent: Intent) {
        handler.removeCallbacks(productionFallback)

        val evidence = intent.getStringArrayListExtra(EXTRA_EVIDENCE_PATHS)
            .orEmpty()
            .map(::File)
            .filter { it.exists() && it.isFile && it.length() > 0L }

        if (evidence.isEmpty()) {
            showStatusNotification(
                "Evidência sem mídia",
                "A tela de registro foi concluída, mas nenhum arquivo de mídia válido foi gerado."
            )
        }

        sendEvidence(evidence, isTest = false)
    }

    private fun handleTestAlert() {
        showStatusNotification(
            "Teste em andamento",
            "Validando recursos autorizados e envio do alerta."
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
                        "Falha no teste",
                        "Não foi possível concluir a etapa visual. O alerta seguirá sem arquivo de mídia."
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
            val incident = IncidentInfoCollector.collect(this, prefs)

            if (!isTest) {
                IncidentHistoryLogger.record(this, incident, mapsLink)
            }

            if (!prefs.ownerNotificationEnabled) {
                if (isTest) {
                    showStatusNotification(
                        "Teste concluído sem envio",
                        "O alerta ao proprietário está desativado nas configurações."
                    )
                }
                return@getCurrentLocation
            }

            // Produção: grava o incidente antes de qualquer tentativa de rede.
            // Assim fotos, vídeos, áudio, localização e dados do evento continuam
            // disponíveis mesmo se o aparelho estiver sem chip, sem Wi-Fi ou se
            // o processo for encerrado durante a tentativa de envio.
            val pendingId = if (!isTest) {
                runCatching {
                    val pending = PendingIncidentStore(this).create(
                        evidence = evidence,
                        mapsLink = mapsLink,
                        incident = incident
                    )
                    PendingIncidentScheduler.enqueue(this, pending.id)
                    pending.id
                }.getOrElse { error ->
                    showStatusNotification(
                        "Falha ao guardar incidente",
                        "Não foi possível criar a fila local de reenvio: ${error.message.orEmpty().take(120)}"
                    )
                    null
                }
            } else {
                null
            }

            if (prefs.alertTransport == PrefsManager.TRANSPORT_GMAIL && prefs.isGmailConfigured()) {
                GmailApiSender(this, prefs).sendIntrusionAlert(
                    evidence = evidence,
                    mapsLink = mapsLink,
                    incident = incident,
                    isTest = isTest
                ) { success, detail ->
                    if (success) {
                        pendingId?.let { PendingIncidentStore(this).markDelivered(it) }

                        if (isTest) {
                            showStatusNotification(
                                "Teste concluído",
                                "Alerta enviado pela Gmail API. Arquivos anexados: ${evidence.size}."
                            )
                        } else {
                            showStatusNotification(
                                "Alerta enviado",
                                "Evento enviado ao proprietário. Arquivos anexados: ${evidence.size}."
                            )
                        }
                    } else {
                        trySmtpFallback(
                            evidence = evidence,
                            mapsLink = mapsLink,
                            incident = incident,
                            isTest = isTest,
                            gmailFailureDetail = detail,
                            pendingId = pendingId
                        )
                    }
                }
            } else {
                trySmtpFallback(
                    evidence = evidence,
                    mapsLink = mapsLink,
                    incident = incident,
                    isTest = isTest,
                    gmailFailureDetail = "Conta Google ainda não conectada",
                    pendingId = pendingId
                )
            }
        }
    }

    private fun trySmtpFallback(
        evidence: List<File>,
        mapsLink: String?,
        incident: IncidentDetails,
        isTest: Boolean,
        gmailFailureDetail: String,
        pendingId: String?
    ) {
        emailExecutor.execute {
            val smtpSuccess = EmailSender(prefs).sendIntrusionAlert(
                evidence = evidence,
                mapsLink = mapsLink,
                incident = incident,
                isTest = isTest
            )

            if (smtpSuccess) {
                pendingId?.let { PendingIncidentStore(this).markDelivered(it) }
            } else if (pendingId != null) {
                PendingIncidentStore(this).updateLastError(
                    pendingId,
                    "$gmailFailureDetail; fallback SMTP indisponível"
                )
                // O trabalho já está persistido. Reforçamos o agendamento para
                // o caso de o processo ter sido recriado entre as etapas.
                PendingIncidentScheduler.enqueue(this, pendingId, immediate = true)
            }

            if (isTest) {
                showStatusNotification(
                    if (smtpSuccess) "Teste concluído via fallback" else "Falha no envio do teste",
                    if (smtpSuccess) {
                        "Gmail OAuth indisponível; alerta enviado pelo transporte de fallback."
                    } else {
                        "$gmailFailureDetail. Abra Configurações e conecte novamente a conta Google."
                    }
                )
            } else if (smtpSuccess) {
                showStatusNotification(
                    "Alerta enviado via fallback",
                    "Evento enviado ao proprietário. Arquivos anexados: ${evidence.size}."
                )
            } else if (pendingId != null) {
                showStatusNotification(
                    "Alerta guardado para reenvio",
                    "Sem conexão ou transporte disponível. O incidente foi salvo e será reenviado automaticamente quando houver internet."
                )
            } else {
                showStatusNotification(
                    "Alerta não enviado",
                    "$gmailFailureDetail. Não foi possível criar uma fila local para este evento."
                )
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
        handler.removeCallbacksAndMessages(null)
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
        private const val PRODUCTION_CAPTURE_TIMEOUT_MS = 45_000L
    }
}
