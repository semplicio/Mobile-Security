package com.autombot.security.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.autombot.security.BuildConfig
import com.autombot.security.databinding.ActivitySettingsBinding
import com.autombot.security.service.SecurityMonitorService
import com.autombot.security.util.AppLockSession
import com.autombot.security.util.GmailApiSender
import com.autombot.security.util.PrefsManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager
    private var unlockDialogVisible = false

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
        binding.switchAppLock.setOnCheckedChangeListener { _, enabled ->
            if (enabled && !prefs.hasAppLockPassword()) {
                showCreatePasswordDialog()
            }
        }

        binding.btnExperimentalFeatures.setOnClickListener {
            AppLockSession.suppressNextBackgroundLock()
            startActivity(Intent(this, ExperimentalFeaturesActivity::class.java))
        }

        binding.btnConnectGoogle.setOnClickListener {
            connectGoogleAccount()
        }

        binding.btnTestAlert.setOnClickListener {
            saveValues()
            if (!prefs.isGmailConfigured() || !prefs.isEmailConfigured()) {
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
                "Teste iniciado. O alerta será enviado para ${prefs.googleAccountEmail}.",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.btnSave.setOnClickListener {
            if (!saveValues()) return@setOnClickListener
            Toast.makeText(this, "Configurações salvas", Toast.LENGTH_SHORT).show()
            AppLockSession.suppressNextBackgroundLock()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::prefs.isInitialized) {
            refreshGoogleAccountStatus()
            enforceAppLockIfNeeded()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !AppLockSession.consumeBackgroundSuppression()) {
            AppLockSession.lock()
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
                    AppLockSession.suppressNextBackgroundLock()
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
        val token = result.accessToken

        if (!hasSendScope || !hasOpenId || !hasEmail || token.isNullOrBlank()) {
            prefs.googleAccountConnected = false
            Toast.makeText(
                this,
                "As permissões necessárias do Google não foram concedidas.",
                Toast.LENGTH_LONG
            ).show()
            refreshGoogleAccountStatus()
            return
        }

        binding.tvGoogleAccountStatus.text = "Confirmando e-mail da Conta Google..."
        GmailApiSender(this, prefs).resolveAuthorizedAccountEmail(token) { email ->
            runOnUiThread {
                if (email.isNullOrBlank()) {
                    prefs.googleAccountConnected = false
                    prefs.googleAccountEmail = ""
                    prefs.destinationEmail = ""
                    Toast.makeText(
                        this,
                        "Não foi possível confirmar o e-mail da Conta Google. Tente conectar novamente.",
                        Toast.LENGTH_LONG
                    ).show()
                    refreshGoogleAccountStatus()
                    return@runOnUiThread
                }

                prefs.googleAccountEmail = email
                prefs.destinationEmail = email
                prefs.googleAccountConnected = true
                prefs.alertTransport = PrefsManager.TRANSPORT_GMAIL
                refreshGoogleAccountStatus()

                Toast.makeText(
                    this,
                    "Conta conectada: $email. Os alertas do proprietário usarão esta conta.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
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
        switchCapturePhotos.isChecked = prefs.capturePhotosEnabled
        etPhotoCount.setText(prefs.photoCount.toString())
        etPhotoCount.isEnabled = prefs.capturePhotosEnabled
        switchRecordVideo.isChecked = prefs.recordVideoEnabled
        switchAlarm.isChecked = prefs.alarmEnabled
        switchOwnerNotification.isChecked = prefs.ownerNotificationEnabled
        switchShowMessage.isChecked = prefs.showAlertMessageEnabled
        etSecurityMessage.setText(prefs.securityMessage)
        etSecurityMessage.isEnabled = prefs.showAlertMessageEnabled

        if (prefs.appLockEnabled && !prefs.hasAppLockPassword()) {
            // Migração segura da opção antiga, que ainda não tinha senha real.
            prefs.appLockEnabled = false
        }
        switchAppLock.isChecked = prefs.appLockEnabled

        if (prefs.googleAccountEmail.contains("@")) {
            prefs.destinationEmail = prefs.googleAccountEmail
        }
        refreshGoogleAccountStatus()
    }

    private fun refreshGoogleAccountStatus() = with(binding) {
        val connected = prefs.isGmailConfigured()
        if (connected) {
            prefs.destinationEmail = prefs.googleAccountEmail
        }

        tvGoogleAccountStatus.text = when {
            !connected -> "Conta Google não conectada"
            else -> "Conta conectada: ${prefs.googleAccountEmail}\nAlertas serão enviados para esta conta."
        }
        btnConnectGoogle.text = if (connected) {
            "Reconectar conta Google"
        } else {
            "Conectar conta Google"
        }

        switchOwnerNotification.isEnabled = connected
        if (!connected) {
            switchOwnerNotification.isChecked = false
        }
    }

    private fun saveValues(): Boolean = with(binding) {
        prefs.failedAttemptsThreshold =
            etThreshold.text?.toString()?.toIntOrNull() ?: PrefsManager.DEFAULT_THRESHOLD
        prefs.capturePhotosEnabled = switchCapturePhotos.isChecked
        prefs.photoCount = etPhotoCount.text?.toString()?.toIntOrNull() ?: 3
        prefs.recordAudioEnabled = false
        prefs.recordVideoEnabled = switchRecordVideo.isChecked
        prefs.alarmEnabled = switchAlarm.isChecked
        prefs.ownerNotificationEnabled = switchOwnerNotification.isChecked && prefs.isGmailConfigured()
        prefs.showAlertMessageEnabled = switchShowMessage.isChecked
        prefs.securityMessage = etSecurityMessage.text?.toString()?.trim().orEmpty()

        if (switchAppLock.isChecked) {
            if (!prefs.hasAppLockPassword()) {
                Toast.makeText(
                    this@SettingsActivity,
                    "Crie uma senha para ativar a proteção do aplicativo.",
                    Toast.LENGTH_LONG
                ).show()
                showCreatePasswordDialog()
                return false
            }
            prefs.appLockEnabled = true
        } else {
            prefs.appLockEnabled = false
            prefs.clearAppLockPassword()
            AppLockSession.unlock()
        }

        if (prefs.googleAccountEmail.contains("@")) {
            prefs.destinationEmail = prefs.googleAccountEmail
        }
        true
    }

    private fun showCreatePasswordDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
        }
        val password = EditText(this).apply {
            hint = "Nova senha"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmation = EditText(this).apply {
            hint = "Confirmar senha"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(password)
        container.addView(confirmation)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Criar senha do AutomBot")
            .setMessage("Crie uma senha com pelo menos ${PrefsManager.MIN_APP_LOCK_LENGTH} caracteres. Ela é independente da senha da tela de bloqueio do Android.")
            .setView(container)
            .setPositiveButton("Criar", null)
            .setNegativeButton("Cancelar") { _, _ ->
                binding.switchAppLock.isChecked = false
            }
            .setOnCancelListener {
                binding.switchAppLock.isChecked = false
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val first = password.text?.toString().orEmpty()
                val second = confirmation.text?.toString().orEmpty()
                when {
                    first.length < PrefsManager.MIN_APP_LOCK_LENGTH ->
                        password.error = "Use pelo menos ${PrefsManager.MIN_APP_LOCK_LENGTH} caracteres"
                    first != second ->
                        confirmation.error = "As senhas não coincidem"
                    else -> {
                        prefs.setAppLockPassword(first)
                        AppLockSession.unlock()
                        binding.switchAppLock.isChecked = true
                        Toast.makeText(this, "Senha do aplicativo criada", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun enforceAppLockIfNeeded() {
        if (!prefs.appLockEnabled || AppLockSession.unlocked || unlockDialogVisible) {
            binding.root.visibility = View.VISIBLE
            return
        }
        if (!prefs.hasAppLockPassword()) {
            prefs.appLockEnabled = false
            binding.root.visibility = View.VISIBLE
            return
        }

        binding.root.visibility = View.INVISIBLE
        unlockDialogVisible = true
        val password = EditText(this).apply {
            hint = "Senha do AutomBot"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("AutomBot Security bloqueado")
            .setMessage("Digite a senha criada para acessar as configurações.")
            .setView(password)
            .setCancelable(false)
            .setPositiveButton("Desbloquear", null)
            .setNegativeButton("Sair") { _, _ -> finish() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = password.text?.toString().orEmpty()
                if (prefs.verifyAppLockPassword(value)) {
                    AppLockSession.unlock()
                    unlockDialogVisible = false
                    binding.root.visibility = View.VISIBLE
                    dialog.dismiss()
                } else {
                    password.error = "Senha incorreta"
                }
            }
        }
        dialog.setOnDismissListener { unlockDialogVisible = false }
        dialog.show()
    }
}
