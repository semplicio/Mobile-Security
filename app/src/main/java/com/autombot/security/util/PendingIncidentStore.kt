package com.autombot.security.util

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Fila persistente dos incidentes que ainda precisam ser entregues.
 *
 * Cada incidente é salvo em um arquivo JSON separado dentro do armazenamento
 * privado do aplicativo. As mídias permanecem nos diretórios privados onde
 * foram capturadas e só são removidas depois da confirmação de envio.
 */
data class PendingIncident(
    val id: String,
    val createdAtEpochMs: Long,
    val evidencePaths: List<String>,
    val mapsLink: String?,
    val incident: IncidentDetails,
    val attempts: Int,
    val lastError: String?
)

class PendingIncidentStore(private val context: Context) {

    private val queueDir: File
        get() = File(context.filesDir, QUEUE_DIR).apply { mkdirs() }

    @Synchronized
    fun create(
        evidence: List<File>,
        mapsLink: String?,
        incident: IncidentDetails
    ): PendingIncident {
        val pending = PendingIncident(
            id = "INC-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}",
            createdAtEpochMs = System.currentTimeMillis(),
            evidencePaths = evidence
                .filter { it.exists() && it.isFile && it.length() > 0L }
                .map { it.absolutePath },
            mapsLink = mapsLink,
            incident = incident,
            attempts = 0,
            lastError = null
        )
        write(pending)
        return pending
    }

    @Synchronized
    fun get(id: String): PendingIncident? {
        val file = fileFor(id)
        if (!file.exists()) return null
        return runCatching { fromJson(JSONObject(file.readText())) }
            .onFailure { Log.e(TAG, "Falha ao ler incidente pendente $id", it) }
            .getOrNull()
    }

    @Synchronized
    fun list(): List<PendingIncident> =
        queueDir.listFiles { file -> file.isFile && file.extension.equals("json", true) }
            .orEmpty()
            .mapNotNull { file ->
                runCatching { fromJson(JSONObject(file.readText())) }
                    .onFailure { Log.e(TAG, "Falha ao ler ${file.name}", it) }
                    .getOrNull()
            }
            .sortedBy { it.createdAtEpochMs }

    @Synchronized
    fun registerAttempt(id: String, error: String? = null): PendingIncident? {
        val current = get(id) ?: return null
        val updated = current.copy(
            attempts = current.attempts + 1,
            lastError = error?.take(500)
        )
        write(updated)
        return updated
    }

    @Synchronized
    fun updateLastError(id: String, error: String?) {
        val current = get(id) ?: return
        write(current.copy(lastError = error?.take(500)))
    }

    @Synchronized
    fun markDelivered(id: String, deleteEvidence: Boolean = true) {
        val current = get(id)
        if (deleteEvidence) {
            current?.evidencePaths
                ?.map(::File)
                ?.forEach { file ->
                    runCatching {
                        if (file.exists() && file.isFile && isInsideAppStorage(file)) {
                            file.delete()
                        }
                    }
                }
        }
        runCatching { fileFor(id).delete() }
            .onFailure { Log.w(TAG, "Falha ao remover incidente entregue $id", it) }
    }

    private fun write(pending: PendingIncident) {
        val target = fileFor(pending.id)
        val temp = File(queueDir, ".${pending.id}.tmp")
        temp.writeText(toJson(pending).toString())
        if (!temp.renameTo(target)) {
            target.writeText(temp.readText())
            temp.delete()
        }
    }

    private fun fileFor(id: String): File {
        val safe = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(queueDir, "$safe.json")
    }

    private fun isInsideAppStorage(file: File): Boolean {
        val appRoot = context.filesDir.canonicalFile
        val candidate = file.canonicalFile
        return candidate.path.startsWith(appRoot.path + File.separator)
    }

    private fun toJson(pending: PendingIncident): JSONObject = JSONObject().apply {
        put("id", pending.id)
        put("created_at_epoch_ms", pending.createdAtEpochMs)
        put("evidence_paths", JSONArray(pending.evidencePaths))
        put("maps_link", pending.mapsLink ?: JSONObject.NULL)
        put("attempts", pending.attempts)
        put("last_error", pending.lastError ?: JSONObject.NULL)
        put("incident", JSONObject().apply {
            put("event_time", pending.incident.eventTime)
            put("failed_attempts", pending.incident.failedAttempts)
            put("device", pending.incident.device)
            put("android_version", pending.incident.androidVersion)
            put("battery_percent", pending.incident.batteryPercent ?: JSONObject.NULL)
            put("charging", pending.incident.charging ?: JSONObject.NULL)
            put("network_type", pending.incident.networkType)
            put("app_version", pending.incident.appVersion)
        })
    }

    private fun fromJson(json: JSONObject): PendingIncident {
        val pathsJson = json.optJSONArray("evidence_paths") ?: JSONArray()
        val paths = buildList {
            for (index in 0 until pathsJson.length()) {
                pathsJson.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        val incidentJson = json.getJSONObject("incident")
        val batteryPercent = if (incidentJson.isNull("battery_percent")) {
            null
        } else {
            incidentJson.optInt("battery_percent")
        }
        val charging = if (incidentJson.isNull("charging")) {
            null
        } else {
            incidentJson.optBoolean("charging")
        }

        return PendingIncident(
            id = json.getString("id"),
            createdAtEpochMs = json.optLong("created_at_epoch_ms", 0L),
            evidencePaths = paths,
            mapsLink = json.optString("maps_link").takeIf {
                it.isNotBlank() && !json.isNull("maps_link")
            },
            incident = IncidentDetails(
                eventTime = incidentJson.optString("event_time"),
                failedAttempts = incidentJson.optInt("failed_attempts"),
                device = incidentJson.optString("device"),
                androidVersion = incidentJson.optString("android_version"),
                batteryPercent = batteryPercent,
                charging = charging,
                networkType = incidentJson.optString("network_type"),
                appVersion = incidentJson.optString("app_version")
            ),
            attempts = json.optInt("attempts", 0),
            lastError = json.optString("last_error").takeIf {
                it.isNotBlank() && !json.isNull("last_error")
            }
        )
    }

    companion object {
        private const val QUEUE_DIR = "pending_incidents"
        private const val TAG = "PendingIncidentStore"
    }
}
