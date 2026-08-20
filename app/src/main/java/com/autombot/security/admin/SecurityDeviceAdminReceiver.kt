package com.autombot.security.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.autombot.security.service.SecurityMonitorService
import com.autombot.security.util.PrefsManager

/**
 * Recebe os callbacks do sistema Android sobre tentativas de desbloqueio de tela.
 *
 * onPasswordFailed() dispara toda vez que o usuário erra o PIN/senha/padrão na
 * tela de bloqueio — é exatamente o gatilho que precisamos para o cenário do
 * "celular esquecido em algum lugar e alguém tentando acessar".
 */
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
        if (!prefs.monitoringEnabled) {
            return
        }

        val attempts = prefs.incrementFailedAttempts()
        Log.w(TAG, "Tentativa de senha incorreta detectada. Total: $attempts")

        if (attempts >= prefs.failedAttemptsThreshold) {
            // Aciona o serviço para tirar foto + alarme + enviar e-mail
            val serviceIntent = Intent(context, SecurityMonitorService::class.java).apply {
                action = SecurityMonitorService.ACTION_INTRUSION_DETECTED
            }
            context.startForegroundService(serviceIntent)
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        // Senha certa digitada: zera o contador, é o dono desbloqueando normalmente
        PrefsManager(context).resetFailedAttempts()
        Log.i(TAG, "Senha correta digitada — contador de tentativas zerado")
    }

    companion object {
        private const val TAG = "SecurityDeviceAdmin"
    }
}
