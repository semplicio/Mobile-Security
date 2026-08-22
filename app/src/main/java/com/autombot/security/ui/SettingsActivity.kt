package com.autombot.security.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.autombot.security.BuildConfig
import com.autombot.security.databinding.ActivitySettingsBinding
import com.autombot.security.service.SecurityMonitorService
import com.autombot.security.util.GmailApiSender
import com.autombot.security.util.PrefsManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    private val googleAuthorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val data = activityResult.data
        if (data == null) {
            Toast.makeText(this, "Autorização Google cancelada", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        runCatching {
            Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)
        }.onSuccess { result ->
            completeGoogleAuthorization(result)
        }.onFailure { error ->
            Toast.makeText(
                this,
                googleAuthorizationErrorMessage(error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        loadCurrentValues()

        binding.switchCapturePhotos.setOnCheckedChangeListener { _, enabled ->
            binding.etPhotoCount.isEnabled = enabled
        }
        binding.switchShowMessage.setOnCheckedChangeListener { _, enabled ->
            binding.etSecurityMessage.isEnabled = enabled
        }

        binding.btnExperimentalFeatures.setOnClickListener {
            startActivity(Intent(this, ExperimentalFeaturesActivity::class.java))
        }

        binding.btnConnectGoogle.setOnClickListener {
            connectGoogleAccount()
        }

        binding.btnTestAlert.setOnClickListener {
            saveValues()
            if (!prefs.isGmailConfigured()) {
                Toast.makeText(
                    this,
                    "Conecte uma conta Google antes de testar o envio.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val intent = Intent(this, SecurityMonitorService::class.java).apply {
                action = SecurityMonitorService.ACTION_TEST_ALERT
            }
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(
                this,
                "Teste iniciado. Confira Evidências e o e-mail do proprietário.",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.btnSave.setOnClickListener {
            saveValues()
            Toast.makeText(this, "Configurações salvas", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::prefs.isInitialized) {
            refreshGoogleAccountStatus()
        }
    }

    private fun connectGoogleAccount() {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(GmailApiSender.requiredScopes())
            .build()

        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        Toast.makeText(this, "Não foi possível abrir a autorização Google", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    googleAuthorizationLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } else {
                    completeGoogleAuthorization(result)
                }
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    googleAuthorizationErrorMessage(error),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun completeGoogleAuthorization(result: AuthorizationResult) {
        val grantedScopes = result.grantedScopes
        val hasSendScope = grantedScopes.contains(GmailApiSender.GMAIL_SEND_SCOPE)
        val hasOpenId = grantedScopes.contains(GmailApiSender.OPENID_SCOPE)
        val hasEmail = grantedScopes.contains(GmailApiSender.EMAIL_SCOPE)

        if (!hasSendScope || !hasOpenId || !hasEmail || result.accessToken.isNullOrBlank()) {
            prefs.googleAccountConnected = false
            Toast.makeText(
                this,
                "As permissões necessárias do Google não foram concedidas.",
                Toast.LENGTH_LONG
            ).show()
            refreshGoogleAccountStatus()
            return
        }

        prefs.googleAccountEmail = ""
        prefs.googleAccountConnected = true
        prefs.alertTransport = PrefsManager.TRANSPORT_GMAIL
        refreshGoogleAccountStatus()

        Toast.makeText(
            this,
            "Conta Google autorizada. O e-mail será confirmado no primeiro envio.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun googleAuthorizationErrorMessage(error: Throwable): String {
        val detail = error.message.orEmpty()
        return if (
            detail.contains("UNREGISTERED_ON_API_CONSOLE", ignoreCase = true) ||
            detail.contains("status=UNREGISTERED", ignoreCase = true)
        ) {
            "OAuth Android não registrado para este APK. No Google Cloud, confira o cliente Android do pacote ${BuildConfig.APPLICATION_ID} e o SHA-1 da assinatura usada neste APK."
        } else {
            "Falha na autorização Google: ${detail.ifBlank { "erro desconhecido" }}"
        }
    }

    private fun loadCurrentValues() = with(binding) {
        etThreshold.setText(prefs.failedAttemptsThreshold.toString())
        etDestEmail.setText(prefs.destinationEmail)
        switchCapturePhotos.isChecked = prefs.capturePhotosEnabled
        etPhotoCount.setText(prefs.photoCount.toString())
        etPhotoCount.isEnabled = prefs.capturePhotosEnabled
        switchRecordAudio.isChecked = prefs.recordAudioEnabled
        switchRecordVideo.isChecked = prefs.recordVideoEnabled
        switchAlarm.isChecked = prefs.alarmEnabled
        switchOwnerNotification.isChecked = prefs.ownerNotificationEnabled
        switchShowMessage.isChecked = prefs.showAlertMessageEnabled
        etSecurityMessage.setText(prefs.securityMessage)
        etSecurityMessage.isEnabled = prefs.showAlertMessageEnabled
        switchAppLock.isChecked = prefs.appLockEnabled
        refreshGoogleAccountStatus()
    }

    private fun refreshGoogleAccountStatus() = with(binding) {
        val connected = prefs.isGmailConfigured()
        tvGoogleAccountStatus.text = when {
            !connected -> "Conta Google não conectada"
            prefs.googleAccountEmail.contains("@") -> "Conta conectada: ${prefs.googleAccountEmail}"
            else -> "Conta Google autorizada"
        }
        btnConnectGoogle.text = if (connected) {
            "Reconectar conta Google"
        } else {
            "Conectar conta Google"
        }
    }

    private fun saveValues() = with(binding) {
        prefs.failedAttemptsThreshold =
            etThreshold.text?.toString()?.toIntOrNull() ?: PrefsManager.DEFAULT_THRESHOLD
        prefs.destinationEmail = etDestEmail.text?.toString()?.trim().orEmpty()
        prefs.capturePhotosEnabled = switchCapturePhotos.isChecked
        prefs.photoCount = etPhotoCount.text?.toString()?.toIntOrNull() ?: 3
        prefs.recordAudioEnabled = switchRecordAudio.isChecked
        prefs.recordVideoEnabled = switchRecordVideo.isChecked
        prefs.alarmEnabled = switchAlarm.isChecked
        prefs.ownerNotificationEnabled = switchOwnerNotification.isChecked
        prefs.showAlertMessageEnabled = switchShowMessage.isChecked
        prefs.securityMessage = etSecurityMessage.text?.toString()?.trim().orEmpty()
        prefs.appLockEnabled = switchAppLock.isChecked
    }
}
