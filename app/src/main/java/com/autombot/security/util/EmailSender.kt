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
        photos: List<File>,
        mapsLink: String?,
        isTest: Boolean = false
    ): Boolean {
        if (prefs.destinationEmail.isBlank()) {
            Log.w(TAG, "E-mail do proprietário não configurado")
            return false
        }
        if (BuildConfig.SMTP_USER.isBlank() || BuildConfig.SMTP_PASSWORD.isBlank()) {
            Log.e(TAG, "Credenciais administrativas SMTP não foram fornecidas no build")
            return false
        }

        return try {
            val session = buildSession()
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(BuildConfig.SMTP_USER))
                setRecipients(
                    Message.RecipientType.TO,
                    InternetAddress.parse(prefs.destinationEmail)
                )
                subject = if (isTest) {
                    "AutomBot Security: teste de alerta"
                } else {
                    "AutomBot Security: tentativa de acesso não autorizado"
                }
            }

            val bodyText = buildString {
                if (isTest) {
                    append("Este é um teste do sistema de segurança do aparelho.\n\n")
                } else {
                    append("Foram detectadas tentativas incorretas de desbloqueio no seu aparelho.\n\n")
                }
                if (mapsLink != null) {
                    append("Localização aproximada:\n$mapsLink\n\n")
                } else {
                    append("Não foi possível obter a localização no momento.\n\n")
                }
                append("Fotos anexadas: ${photos.count { it.exists() }}\n\n")
                append("Mensagem automática do AutomBot Security.")
            }

            val multipart = MimeMultipart().apply {
                addBodyPart(MimeBodyPart().apply { setText(bodyText, "utf-8") })
            }

            photos.filter { it.exists() }.forEach { photo ->
                multipart.addBodyPart(
                    MimeBodyPart().apply {
                        dataHandler = DataHandler(FileDataSource(photo))
                        fileName = photo.name
                    }
                )
            }

            message.setContent(multipart)
            Transport.send(message)
            Log.i(TAG, "E-mail enviado com sucesso para ${prefs.destinationEmail}")
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
