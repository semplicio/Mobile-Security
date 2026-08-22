package com.autombot.security.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
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
            val serviceIntent = Intent(context, SecurityMonitorService::class.java).apply {
                action = SecurityMonitorService.ACTION_INTRUSION_DETECTED
            }

            runCatching {
                ContextCompat.startForegroundService(context, serviceIntent)
            }.onFailure {
                Log.e(TAG, "Não foi possível iniciar o serviço de segurança", it)
            }

            // Abre uma tela visível e identificada do AutomBot sobre a tela
            // bloqueada. Isso coloca o app em primeiro plano para permitir que
            // os recursos de evidência previamente habilitados pelo proprietário
            // sejam executados dentro das regras do Android moderno.
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
                Log.e(TAG, "Não foi possível abrir a tela de registro do incidente", it)
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
