package com.autombot.security

import android.app.Application
import com.autombot.security.worker.PendingIncidentScheduler

class AutomBotApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Defesa adicional: se existir algum incidente salvo localmente e o
        // WorkManager tiver sido recriado pelo sistema, garantimos que cada item
        // volte para a fila de entrega assim que o processo do app iniciar.
        PendingIncidentScheduler.enqueueAllPending(this)
    }
}
