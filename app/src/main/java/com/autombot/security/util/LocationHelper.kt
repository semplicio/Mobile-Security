package com.autombot.security.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Obtém a última localização conhecida do aparelho — usada tanto no e-mail
 * de alerta de invasão quanto na função "encontrar meu aparelho".
 */
class LocationHelper(private val context: Context) {

    @SuppressLint("MissingPermission") // verificado antes de chamar, na Activity/Service
    fun getCurrentLocation(onResult: (Location?) -> Unit) {
        val client = LocationServices.getFusedLocationProviderClient(context)

        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                onResult(location)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Falha ao obter localização", e)
                onResult(null)
            }
    }

    fun mapsLink(location: Location): String =
        "https://maps.google.com/?q=${location.latitude},${location.longitude}"

    companion object {
        private const val TAG = "LocationHelper"
    }
}
