package com.autombot.security.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autombot.security.service.SecurityMonitorService
import com.autombot.security.util.PrefsManager

/**
 * Reinicia o serviço de monitoramento após o aparelho ser ligado, caso o
 * usuário já tenha ativado a proteção antes de desligar/reiniciar.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = PrefsManager(context)
        if (prefs.monitoringEnabled) {
            val serviceIntent = Intent(context, SecurityMonitorService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
