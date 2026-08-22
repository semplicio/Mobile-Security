package com.autombot.security.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.autombot.security.admin.SecurityDeviceAdminReceiver

class ExperimentalPolicyManager(private val context: Context) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, SecurityDeviceAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    fun canDisableStatusBar(): Boolean = isDeviceOwner() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    fun setStatusBarDisabled(disabled: Boolean): Boolean {
        if (!canDisableStatusBar()) return false
        return try {
            dpm.setStatusBarDisabled(admin, disabled)
        } catch (_: SecurityException) {
            false
        }
    }

    fun powerMenuProtectionSupported(): Boolean = false

    fun powerMenuSupportMessage(): String =
        "O Android não permite bloquear de forma confiável o menu de energia na tela de bloqueio em um aparelho comum. " +
            "Esse recurso exige modo kiosk/Device Owner e ainda depende do fabricante."
}
