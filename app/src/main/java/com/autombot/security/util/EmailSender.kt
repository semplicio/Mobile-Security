package com.autombot.security.util

import android.util.Log
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

    fun sendIntrusionAlert(photoFiles: List<File>, mapsLink: String?): Boolean {
        if (!prefs.ownerNotificationEnabled || !prefs.isEmailConfigured()) {
            Log.w(TAG, "Envio ao proprietário desativado ou e-mail não configurado")
            return false
        }

        return try {
            val session = buildSession()
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(prefs.smtpUser))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(prefs.destinationEmail))
                subject = "AutomBot Security: tentativa de acesso não autorizado"
            }

            val bodyText = buildString {
                append("O AutomBot Security detectou tentativas incorretas de desbloqueio.\n\n")
                append("Tentativas registradas: ${prefs.currentFailedAttempts}\n")
                append("Evidências capturadas: ${photoFiles.count { it.exists() }} foto(s)\n\n")
                if (mapsLink != null) {
                    append("Localização aproximada:\n$mapsLink\n\n")
                } else {
                    append("Não foi possível obter a localização neste momento.\n\n")
                }
                append("Alerta automático do AutomBot Security.")
            }

            val multipart = MimeMultipart().apply {
                addBodyPart(MimeBodyPart().apply { setText(bodyText, "utf-8") })
            }

            photoFiles.filter { it.exists() }.forEach { photoFile ->
                multipart.addBodyPart(MimeBodyPart().apply {
                    dataHandler = DataHandler(FileDataSource(photoFile))
                    fileName = photoFile.name
                })
            }

            message.setContent(multipart)
            Transport.send(message)
            Log.i(TAG, "E-mail de alerta enviado com sucesso")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao enviar e-mail de alerta", e)
            false
        }
    }

    fun sendIntrusionAlert(photoFile: File?, mapsLink: String?): Boolean =
        sendIntrusionAlert(photoFile?.let { listOf(it) } ?: emptyList(), mapsLink)

    private fun buildSession(): Session {
        val ssl = prefs.smtpPort == 465
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", prefs.smtpHost)
            put("mail.smtp.port", prefs.smtpPort.toString())
            if (ssl) {
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.socketFactory.port", prefs.smtpPort.toString())
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                put("mail.smtp.starttls.enable", "false")
            } else {
                put("mail.smtp.starttls.enable", "true")
            }
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "20000")
            put("mail.smtp.writetimeout", "20000")
        }

        return Session.getInstance(props, object : javax.mail.Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(prefs.smtpUser, prefs.smtpPassword)
        })
    }

    companion object {
        private const val TAG = "EmailSender"
    }
}
