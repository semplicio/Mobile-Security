package com.autombot.security.util

/**
 * Estado efêmero do bloqueio local do app.
 *
 * A senha nunca é guardada aqui. Este objeto apenas evita pedir a senha de
 * novo em navegações internas normais. Ao sair do app, a sessão é bloqueada.
 */
object AppLockSession {
    @Volatile
    var unlocked: Boolean = false
        private set

    @Volatile
    private var suppressNextBackgroundLock: Boolean = false

    fun unlock() {
        unlocked = true
    }

    fun lock() {
        unlocked = false
    }

    fun suppressNextBackgroundLock() {
        suppressNextBackgroundLock = true
    }

    fun consumeBackgroundSuppression(): Boolean {
        val value = suppressNextBackgroundLock
        suppressNextBackgroundLock = false
        return value
    }
}
