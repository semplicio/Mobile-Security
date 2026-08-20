package com.autombot.security

import android.app.Application

class AutomBotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ponto central para inicializações futuras (ex.: WorkManager periódico,
        // sincronização com o AutomBot Core, etc.)
    }
}
