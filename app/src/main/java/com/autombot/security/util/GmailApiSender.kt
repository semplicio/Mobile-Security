package com.autombot.security.util

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import java.util.concurrent.Executors
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class GmailApiSender(
    private val context: Context,
    private val prefs: PrefsManager
) {

    fun resolveAuthorizedAccountEmail(
        accessToken: String,
        onResult: (String?) -> Unit
    ) {
        EXECUTOR.execute {
            onResult(resolveSenderEmail(accessToken))
        }
    }

    fun sendIntrusionAlert(
        evidence: List<File>,
        mapsLink: String?,
        incident: IncidentDetails? = null,
        isTest: Boolean = false,
        onResult: (Boolean, String) -> Unit
    ) {
        val configuredAccount = prefs.googleAccountEmail.trim()
        if (!isValidEmail(configuredAccount)) {
            onResult(false, "Conta Google do proprietário não configurada")
            return
        }

        // Desde a versão 0.1.11, o destino do alerta é sempre a própria Conta
        // Google conectada pelo proprietário. Não existe mais um segundo campo
        // de e-mail a ser preenchido manualmente.
        prefs.destinationEmail = configuredAccount

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(requiredScopes())
            .build()

        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    onResult(false, "A conta Google precisa ser reconectada no aplicativo")
                    return@addOnSuccessListener
                }

                val token = result.accessToken
                if (token.isNullOrBlank()) {
                    onResult(false, "Google OAuth não retornou um token de acesso")
                    return@addOnSuccessListener
                }

                prefs.googleAccountConnected = true

                EXECUTOR.execute {
                    val senderEmail = resolveSenderEmail(token)
                        ?: prefs.googleAccountEmail.takeIf(::isValidEmail)

                    if (senderEmail == null) {
                        onResult(
                            false,
                            "Conta Google autorizada, mas não foi possível identificar o e-mail da conta. Reconecte a conta Google."
                        )
                        return@execute
                    }

                    prefs.googleAccountEmail = senderEmail
                    prefs.destinationEmail = senderEmail

                    val sendResult = runCatching {
                        sendWithAccessToken(
                            accessToken = token,
                            senderEmail = senderEmail,
                            evidence = evidence,
                            mapsLink = mapsLink,
                            incident = incident,
                            isTest = isTest
                        )
                    }.onFailure {
                        Log.e(TAG, "Falha ao enviar pela Gmail API", it)
                    }.getOrElse { error ->
                        ApiSendResult(
                            success = false,
                            detail = error.message ?: "Falha inesperada ao enviar pela Gmail API"
                        )
                    }

                    onResult(sendResult.success, sendResult.detail)
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Falha ao obter autorização Google", error)
                onResult(false, googleAuthorizationErrorMessage(error))
            }
    }

    private fun resolveSenderEmail(accessToken: String): String? {
        val connection = (URL(USER_INFO_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (code !in 200..299) {
                Log.e(TAG, "Google UserInfo HTTP $code: $body")
                null
            } else {
                JSONObject(body)
                    .optString("email")
                    .trim()
                    .takeIf(::isValidEmail)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Falha ao identificar a conta Google", error)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun googleAuthorizationErrorMessage(error: Throwable): String {
        val detail = error.message.orEmpty()
        return if (
            detail.contains("UNREGISTERED_ON_API_CONSOLE", ignoreCase = true) ||
            detail.contains("status=UNREGISTERED", ignoreCase = true)
        ) {
            "Cliente OAuth Android não registrado para a assinatura deste APK"
        } else {
            detail.ifBlank { "Falha de autorização Google" }
        }
    }

    private fun sendWithAccessToken(
        accessToken: String,
        senderEmail: String,
        evidence: List<File>,
        mapsLink: String?,
        incident: IncidentDetails?,
        isTest: Boolean
    ): ApiSendResult {
        val rawMessage = buildMimeMessage(senderEmail, evidence, mapsLink, incident, isTest)
        val encoded = Base64.encodeToString(
            rawMessage,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        val connection = (URL(GMAIL_SEND_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val payload = JSONObject()
                .put("raw", encoded)
                .toString()
                .toByteArray(Charsets.UTF_8)

            connection.outputStream.use { it.write(payload) }

            val code = connection.responseCode
            val responseText = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (code in 200..299) {
                Log.i(TAG, "Gmail API HTTP $code: mensagem enviada")
                ApiSendResult(true, "Alerta enviado pela Gmail API")
            } else {
                val detail = parseGmailApiError(code, responseText)
                Log.e(TAG, "Gmail API HTTP $code: $responseText")
                ApiSendResult(false, detail)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseGmailApiError(code: Int, responseText: String): String {
        val apiMessage = runCatching {
            JSONObject(responseText)
                .optJSONObject("error")
                ?.optString("message")
                ?.trim()
                .orEmpty()
        }.getOrDefault("")

        return when {
            apiMessage.isNotBlank() -> "Gmail API HTTP $code: $apiMessage"
            responseText.isNotBlank() -> "Gmail API HTTP $code: ${responseText.take(220)}"
            else -> "Gmail API HTTP $code: falha no envio"
        }
    }

    private fun buildMimeMessage(
        senderEmail: String,
        evidence: List<File>,
        mapsLink: String?,
        incident: IncidentDetails?,
        isTest: Boolean
    ): ByteArray {
        val validEvidence = evidence.filter { it.exists() && it.isFile }
        val photos = validEvidence.count { it.extension.equals("jpg", true) || it.extension.equals("jpeg", true) }
        val videos = validEvidence.count { it.extension.equals("mp4", true) }

        val session = Session.getInstance(Properties())
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(senderEmail))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(senderEmail))
            subject = if (isTest) {
                "AutomBot Security: teste de alerta"
            } else {
                "AutomBot Security: tentativa de acesso não autorizado"
            }
        }

        val bodyText = buildString {
            append("AUTOMBOT SECURITY — ")
            append(if (isTest) "TESTE DE ALERTA" else "ALERTA DE SEGURANÇA")
            append("\n\n")

            if (isTest) {
                append("Este é um teste do sistema de segurança do aparelho.\n\n")
            } else {
                append("Foi detectada uma sequência de tentativas incorretas de desbloqueio no aparelho monitorado.\n\n")
            }

            incident?.let {
                append("DETALHES DO EVENTO\n")
                append("Data/hora: ${it.eventTime}\n")
                append("Tentativas incorretas: ${it.failedAttempts}\n")
                append("Dispositivo: ${it.device}\n")
                append("Sistema: ${it.androidVersion}\n")
                append("Bateria: ${it.batteryDescription()}\n")
                append("Conexão: ${it.networkType}\n")
                append("Versão do app: ${it.appVersion}\n\n")
            }

            append("LOCALIZAÇÃO\n")
            if (mapsLink != null) {
                append("Localização aproximada: $mapsLink\n\n")
            } else {
                append("Não foi possível obter a localização no momento.\n\n")
            }

            append("MÍDIA DO EVENTO\n")
            if (validEvidence.isEmpty()) {
                append("Captura de mídia não realizada automaticamente neste evento.\n")
            } else {
                append("Fotos anexadas: $photos\n")
                append("Vídeos anexados: $videos\n")
                append("O áudio, quando autorizado, é incorporado aos vídeos.\n")
            }

            append("\nMensagem automática do AutomBot Security.")
        }

        val multipart = MimeMultipart().apply {
            addBodyPart(MimeBodyPart().apply { setText(bodyText, "utf-8") })
        }

        validEvidence.forEach { file ->
            multipart.addBodyPart(
                MimeBodyPart().apply {
                    dataHandler = DataHandler(FileDataSource(file))
                    fileName = file.name
                }
            )
        }

        message.setContent(multipart)
        message.saveChanges()

        return ByteArrayOutputStream().use { out ->
            message.writeTo(out)
            out.toByteArray()
        }
    }

    companion object {
        const val GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send"
        const val OPENID_SCOPE = "openid"
        const val PROFILE_SCOPE = "profile"
        const val EMAIL_SCOPE = "email"

        private const val GMAIL_SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send"
        private const val USER_INFO_URL = "https://openidconnect.googleapis.com/v1/userinfo"
        private const val TAG = "GmailApiSender"
        private val EXECUTOR = Executors.newSingleThreadExecutor()

        fun requiredScopes(): List<Scope> = listOf(
            Scope(GMAIL_SEND_SCOPE),
            Scope(OPENID_SCOPE),
            Scope(PROFILE_SCOPE),
            Scope(EMAIL_SCOPE)
        )

        private fun isValidEmail(value: String): Boolean =
            value.contains("@") && value.substringAfter("@").contains(".")
    }

    private data class ApiSendResult(
        val success: Boolean,
        val detail: String
    )
}
