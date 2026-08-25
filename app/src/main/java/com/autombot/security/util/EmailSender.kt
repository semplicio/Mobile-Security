package com.autombot.security.util

import android.util.Log
import com.autombot.security.BuildConfig
import java.io.File
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class EmailSender(private val prefs: PrefsManager) {

    fun sendIntrusionAlert(
        evidence: List<File>,
        mapsLink: String?,
        incident: IncidentDetails? = null,
        isTest: Boolean = false
    ): Boolean {
        val destination = prefs.googleAccountEmail.takeIf { it.contains("@") }
            ?: prefs.destinationEmail.takeIf { it.contains("@") }
            ?: run {
                Log.w(TAG, "E-mail do proprietário não configurado")
                return false
            }

        prefs.destinationEmail = destination

        if (BuildConfig.SMTP_USER.isBlank() || BuildConfig.SMTP_PASSWORD.isBlank()) {
            Log.e(TAG, "Credenciais administrativas SMTP não foram fornecidas no build")
            return false
        }

        return try {
            val validEvidence = evidence.filter { it.exists() && it.isFile }
            val photos = validEvidence.count { it.extension.equals("jpg", true) || it.extension.equals("jpeg", true) }
            val videos = validEvidence.count { it.extension.equals("mp4", true) }

            val session = buildSession()
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(BuildConfig.SMTP_USER))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(destination))
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
            Transport.send(message)
            Log.i(TAG, "E-mail enviado com sucesso para $destination")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao enviar e-mail", e)
            false
        }
    }

    private fun buildSession(): Session {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", BuildConfig.SMTP_HOST)
            put("mail.smtp.port", BuildConfig.SMTP_PORT.toString())
            put("mail.smtp.ssl.enable", "true")
            put("mail.smtp.socketFactory.port", BuildConfig.SMTP_PORT.toString())
            put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "20000")
            put("mail.smtp.writetimeout", "20000")
        }

        return Session.getInstance(props, object : javax.mail.Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(BuildConfig.SMTP_USER, BuildConfig.SMTP_PASSWORD)
        })
    }

    companion object {
        private const val TAG = "EmailSender"
    }
}
