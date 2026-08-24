package com.autombot.security.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.autombot.security.util.EmailSender
import com.autombot.security.util.GmailApiSender
import com.autombot.security.util.PendingIncident
import com.autombot.security.util.PendingIncidentStore
import com.autombot.security.util.PrefsManager
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Reenvia um incidente assim que o Android confirmar uma conexão de rede.
 *
 * O WorkManager persiste este trabalho entre encerramentos do processo e
 * reinicializações do aparelho. Em caso de falha, o próprio WorkManager aplica
 * backoff exponencial e agenda uma nova tentativa.
 */
class PendingIncidentDeliveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val incidentId = inputData.getString(KEY_INCIDENT_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success()

        val store = PendingIncidentStore(applicationContext)
        val pending = store.get(incidentId) ?: return Result.success()
        val prefs = PrefsManager(applicationContext)

        if (!prefs.ownerNotificationEnabled || prefs.destinationEmail.isBlank()) {
            // Se o proprietário desativou o envio posteriormente, não mantemos
            // mídia privada indefinidamente na fila.
            store.markDelivered(incidentId, deleteEvidence = true)
            return Result.success()
        }

        store.registerAttempt(incidentId)

        val evidence = pending.evidencePaths
            .map(::File)
            .filter { it.exists() && it.isFile && it.length() > 0L }

        val delivery = deliver(prefs, pending, evidence)
        return if (delivery.first) {
            Log.i(TAG, "Incidente $incidentId entregue após ${pending.attempts + 1} tentativa(s)")
            store.markDelivered(incidentId, deleteEvidence = true)
            Result.success()
        } else {
            val detail = delivery.second.ifBlank { "Falha temporária de envio" }
            Log.w(TAG, "Incidente $incidentId continua pendente: $detail")
            store.updateLastError(incidentId, detail)
            Result.retry()
        }
    }

    private fun deliver(
        prefs: PrefsManager,
        pending: PendingIncident,
        evidence: List<File>
    ): Pair<Boolean, String> {
        var gmailDetail = "Gmail OAuth não configurado"

        if (prefs.alertTransport == PrefsManager.TRANSPORT_GMAIL && prefs.isGmailConfigured()) {
            val latch = CountDownLatch(1)
            val gmailSuccess = AtomicBoolean(false)
            val detail = AtomicReference(gmailDetail)

            runCatching {
                GmailApiSender(applicationContext, prefs).sendIntrusionAlert(
                    evidence = evidence,
                    mapsLink = pending.mapsLink,
                    incident = pending.incident,
                    isTest = false
                ) { success, message ->
                    gmailSuccess.set(success)
                    detail.set(message)
                    latch.countDown()
                }

                val completed = latch.await(GMAIL_WAIT_SECONDS, TimeUnit.SECONDS)
                if (!completed) {
                    detail.set("Tempo esgotado aguardando Gmail API")
                }
            }.onFailure { error ->
                detail.set(error.message ?: "Falha ao iniciar Gmail API")
            }

            gmailDetail = detail.get().orEmpty()
            if (gmailSuccess.get()) {
                return true to gmailDetail
            }
        }

        val smtpSuccess = EmailSender(prefs).sendIntrusionAlert(
            evidence = evidence,
            mapsLink = pending.mapsLink,
            incident = pending.incident,
            isTest = false
        )

        return if (smtpSuccess) {
            true to "Alerta entregue pelo transporte SMTP de fallback"
        } else {
            false to "$gmailDetail; fallback SMTP indisponível"
        }
    }

    companion object {
        const val KEY_INCIDENT_ID = "pending_incident_id"
        private const val TAG = "PendingIncidentWorker"
        private const val GMAIL_WAIT_SECONDS = 45L
    }
}

object PendingIncidentScheduler {
    private const val UNIQUE_PREFIX = "autombot_pending_incident_"
    private const val INITIAL_SAFETY_DELAY_SECONDS = 30L

    fun enqueue(
        context: Context,
        incidentId: String,
        immediate: Boolean = false
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val requestBuilder = OneTimeWorkRequestBuilder<PendingIncidentDeliveryWorker>()
            .setInputData(workDataOf(PendingIncidentDeliveryWorker.KEY_INCIDENT_ID to incidentId))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30L,
                TimeUnit.SECONDS
            )
            .addTag(UNIQUE_PREFIX + incidentId)

        if (!immediate) {
            requestBuilder.setInitialDelay(INITIAL_SAFETY_DELAY_SECONDS, TimeUnit.SECONDS)
        }

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_PREFIX + incidentId,
            ExistingWorkPolicy.KEEP,
            requestBuilder.build()
        )
    }

    fun enqueueAllPending(context: Context) {
        PendingIncidentStore(context.applicationContext)
            .list()
            .forEach { pending ->
                enqueue(context, pending.id, immediate = true)
            }
    }
}
