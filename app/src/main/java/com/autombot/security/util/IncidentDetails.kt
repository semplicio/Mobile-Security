package com.autombot.security.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.autombot.security.BuildConfig
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class IncidentDetails(
    val eventTime: String,
    val failedAttempts: Int,
    val device: String,
    val androidVersion: String,
    val batteryPercent: Int?,
    val charging: Boolean?,
    val networkType: String,
    val appVersion: String
) {
    fun batteryDescription(): String = when {
        batteryPercent == null -> "Indisponível"
        charging == true -> "$batteryPercent% (carregando)"
        charging == false -> "$batteryPercent% (não carregando)"
        else -> "$batteryPercent%"
    }
}

object IncidentInfoCollector {

    fun collect(context: Context, prefs: PrefsManager): IncidentDetails {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            null
        }

        val batteryStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = when (batteryStatus) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }

        val manufacturer = Build.MANUFACTURER
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val model = Build.MODEL.orEmpty().trim()
        val deviceName = listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .ifBlank { "Android" }

        return IncidentDetails(
            eventTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
            failedAttempts = prefs.currentFailedAttempts,
            device = deviceName,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            batteryPercent = batteryPercent,
            charging = charging,
            networkType = currentNetworkType(context),
            appVersion = BuildConfig.VERSION_NAME
        )
    }

    private fun currentNetworkType(context: Context): String {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return "Indisponível"
        val network = manager.activeNetwork ?: return "Sem conexão"
        val capabilities = manager.getNetworkCapabilities(network) ?: return "Indisponível"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados móveis"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Outra conexão"
        }
    }
}

object IncidentHistoryLogger {
    private const val FILE_NAME = "security_incidents.jsonl"
    private const val TAG = "IncidentHistory"

    fun record(context: Context, details: IncidentDetails, mapsLink: String?) {
        runCatching {
            val json = JSONObject()
                .put("event_time", details.eventTime)
                .put("failed_attempts", details.failedAttempts)
                .put("device", details.device)
                .put("android_version", details.androidVersion)
                .put("battery", details.batteryDescription())
                .put("network", details.networkType)
                .put("app_version", details.appVersion)
                .put("maps_link", mapsLink ?: JSONObject.NULL)

            context.openFileOutput(FILE_NAME, Context.MODE_APPEND)
                .bufferedWriter()
                .use { writer ->
                    writer.append(json.toString())
                    writer.newLine()
                }
        }.onFailure { error ->
            Log.e(TAG, "Não foi possível registrar o incidente no histórico local", error)
        }
    }
}