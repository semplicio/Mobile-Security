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

/**
 * Envia alertas diretamente do aparelho pela Gmail API usando Google OAuth 2.0.
 * O único escopo solicitado é gmail.send: o app não lê nem gerencia a caixa postal.
 */
class GmailApiSender(
    private val context: Context,
    private val prefs: PrefsManager
) {

    fun sendIntrusionAlert(
        evidence: List<File>,
        mapsLink: String?,
        isTest: Boolean = false,
        onResult: (Boolean, String) -> Unit
    ) {
        if (prefs.destinationEmail.isBlank()) {
            onResult(false, "E-mail do proprietário não configurado")
            return
        }

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GMAIL_SEND_SCOPE)))
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

                result.toGoogleSignInAccount()?.email?.let { email ->
                    prefs.googleAccountEmail = email
                }

                EXECUTOR.execute {
                    val sent = runCatching {
                        sendWithAccessToken(token, evidence, mapsLink, isTest)
                    }.onFailure {
                        Log.e(TAG, "Falha ao enviar pela Gmail API", it)
                    }.getOrDefault(false)

                    onResult(
                        sent,
                        if (sent) "Alerta enviado pela Gmail API" else "Falha ao enviar pela Gmail API"
                    )
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Falha ao obter autorização Google", error)
                onResult(false, error.message ?: "Falha de autorização Google")
            }
    }

    private fun sendWithAccessToken(
        accessToken: String,
        evidence: List<File>,
        mapsLink: String?,
        isTest: Boolean
    ): Boolean {
        val rawMessage = buildMimeMessage(evidence, mapsLink, isTest)
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
        }

        val payload = JSONObject().put("raw", encoded).toString().toByteArray(Charsets.UTF_8)
        connection.outputStream.use { it.write(payload) }

        val code = connection.responseCode
        if (code !in 200..299) {
            val errorText = runCatching {
                connection.errorStream?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            Log.e(TAG, "Gmail API HTTP $code: $errorText")
        }
        connection.disconnect()
        return code in 200..299
    }

    private fun buildMimeMessage(
        evidence: List<File>,
        mapsLink: String?,
        isTest: Boolean
    ): ByteArray {
        val validEvidence = evidence.filter { it.exists() && it.isFile }
        val photos = validEvidence.count { it.extension.equals("jpg", true) || it.extension.equals("jpeg", true) }
        val audios = validEvidence.count { it.extension.equals("m4a", true) }
        val videos = validEvidence.count { it.extension.equals("mp4", true) }

        val session = Session.getInstance(Properties())
        val message = MimeMessage(session).apply {
            val fromAddress = prefs.googleAccountEmail.ifBlank { prefs.destinationEmail }
            setFrom(InternetAddress(fromAddress))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(prefs.destinationEmail))
            subject = if (isTest) {
                "AutomBot Security: teste de alerta"
            } else {
                "AutomBot Security: tentativa de acesso não autorizado"
            }
        }

        val bodyText = buildString {
            if (isTest) append("Este é um teste do sistema de segurança do aparelho.\n\n")
            else append("Foram detectadas tentativas incorretas de desbloqueio no seu aparelho.\n\n")

            if (mapsLink != null) append("Localização aproximada:\n$mapsLink\n\n")
            else append("Não foi possível obter a localização no momento.\n\n")

            append("Evidências anexadas:\n")
            append("- Fotos: $photos\n")
            append("- Áudios: $audios\n")
            append("- Vídeos: $videos\n\n")
            append("Mensagem automática do AutomBot Security.")
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
        private const val GMAIL_SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send"
        private const val TAG = "GmailApiSender"
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
