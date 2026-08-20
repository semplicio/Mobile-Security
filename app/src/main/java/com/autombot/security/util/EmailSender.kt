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

/**
 * Envia o alerta de invasão por e-mail, com a foto do intruso anexada e o
 * link de localização no corpo da mensagem.
 *
 * IMPORTANTE: deve ser chamado sempre em thread de background (o
 * SecurityMonitorService já faz isso). Nunca chamar na main thread.
 *
 * Para Gmail: o usuário precisa gerar uma "senha de app" nas configurações
 * de segurança da conta Google (senha normal não funciona com SMTP direto).
 */
class EmailSender(private val prefs: PrefsManager) {

    fun sendIntrusionAlert(photoFile: File?, mapsLink: String?): Boolean {
        if (!prefs.isEmailConfigured()) {
            Log.w(TAG, "E-mail não configurado — pulando envio")
            return false
        }

        return try {
            val session = buildSession()

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(prefs.smtpUser))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(prefs.destinationEmail))
                subject = "⚠️ AutomBot Security: tentativa de acesso não autorizado"
            }

            val bodyText = buildString {
                append("Foram detectadas tentativas incorretas de desbloqueio no seu aparelho.\n\n")
                if (mapsLink != null) {
                    append("Localização aproximada no momento do alerta:\n$mapsLink\n\n")
                } else {
                    append("Não foi possível obter a localização no momento.\n\n")
                }
                append("Este é um alerta automático do AutomBot Security.")
            }

            val textPart = MimeBodyPart().apply { setText(bodyText, "utf-8") }

            val multipart = MimeMultipart().apply { addBodyPart(textPart) }

            if (photoFile != null && photoFile.exists()) {
                val imagePart = MimeBodyPart().apply {
                    dataHandler = DataHandler(FileDataSource(photoFile))
                    fileName = photoFile.name
                }
                multipart.addBodyPart(imagePart)
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

    private fun buildSession(): Session {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", prefs.smtpHost)
            put("mail.smtp.port", prefs.smtpPort.toString())
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
