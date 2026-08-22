package com.autombot.security.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.autombot.security.service.SecurityMonitorService
import com.autombot.security.ui.IntrusionCaptureActivity
import com.autombot.security.util.PrefsManager

class SecurityDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "AutomBot Security foi habilitado como administrador do dispositivo")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "AutomBot Security foi desabilitado como administrador do dispositivo")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)

        val prefs = PrefsManager(context)
        if (!prefs.monitoringEnabled) return

        val attempts = prefs.incrementFailedAttempts()
        Log.w(TAG, "Tentativa de senha incorreta detectada. Total: $attempts")

        if (attempts >= prefs.failedAttemptsThreshold) {
            // Mantém sirene/notificação/fallback de e-mail mesmo se o Android
            // bloquear a abertura da Activity a partir do background.
            val serviceIntent = Intent(context, SecurityMonitorService::class.java).apply {
                action = SecurityMonitorService.ACTION_INTRUSION_DETECTED
            }
            context.startForegroundService(serviceIntent)

            // Melhor esforço: torna o app visível para permitir uso legítimo da
            // câmera no Android moderno. Se o fabricante bloquear a Activity,
            // o serviço envia localização/e-mail sem foto após o timeout.
            runCatching {
                val captureIntent = Intent(context, IntrusionCaptureActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                context.startActivity(captureIntent)
            }.onFailure {
                Log.e(TAG, "Não foi possível abrir a tela de captura", it)
            }
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        PrefsManager(context).resetFailedAttempts()
        Log.i(TAG, "Senha correta digitada — contador de tentativas zerado")
    }

    companion object {
        private const val TAG = "SecurityDeviceAdmin"
    }
}
