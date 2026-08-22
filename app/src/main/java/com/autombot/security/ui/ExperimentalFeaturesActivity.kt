package com.autombot.security.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autombot.security.databinding.ActivityExperimentalFeaturesBinding
import com.autombot.security.util.ExperimentalPolicyManager
import com.autombot.security.util.PrefsManager

class ExperimentalFeaturesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExperimentalFeaturesBinding
    private lateinit var prefs: PrefsManager
    private lateinit var policy: ExperimentalPolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExperimentalFeaturesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        policy = ExperimentalPolicyManager(this)

        binding.btnBack.setOnClickListener { finish() }

        binding.switchStatusBar.isChecked = prefs.experimentalStatusBarProtection
        binding.switchPowerMenu.isChecked = prefs.experimentalPowerMenuProtection
        binding.switchCaptureOnSystemUi.isChecked = prefs.experimentalCaptureOnSystemUi

        refreshCapabilityState()

        binding.switchStatusBar.setOnCheckedChangeListener { _, enabled ->
            if (!binding.switchStatusBar.isEnabled) return@setOnCheckedChangeListener
            val applied = policy.setStatusBarDisabled(enabled)
            if (applied) {
                prefs.experimentalStatusBarProtection = enabled
                Toast.makeText(
                    this,
                    if (enabled) "Sombra de notificações bloqueada" else "Sombra de notificações liberada",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                binding.switchStatusBar.isChecked = prefs.experimentalStatusBarProtection
                Toast.makeText(this, "Não foi possível aplicar esta política", Toast.LENGTH_LONG).show()
            }
        }

        binding.switchPowerMenu.setOnCheckedChangeListener { _, enabled ->
            if (!binding.switchPowerMenu.isEnabled) return@setOnCheckedChangeListener
            prefs.experimentalPowerMenuProtection = enabled
        }

        binding.switchCaptureOnSystemUi.setOnCheckedChangeListener { _, enabled ->
            prefs.experimentalCaptureOnSystemUi = enabled
        }
    }

    private fun refreshCapabilityState() {
        val owner = policy.isDeviceOwner()
        binding.tvManagedStatus.text = if (owner) {
            "Modo gerenciado ativo: recursos avançados disponíveis neste aparelho."
        } else {
            "Modo gerenciado inativo: este aparelho está em modo Android comum."
        }

        binding.switchStatusBar.isEnabled = policy.canDisableStatusBar()
        binding.tvStatusBarDetail.text = if (policy.canDisableStatusBar()) {
            "Bloqueia a sombra de notificações e os atalhos rápidos usando a API oficial de Device Owner."
        } else {
            "Indisponível neste aparelho. Requer que o AutomBot Security seja provisionado como Device Owner."
        }

        binding.switchPowerMenu.isEnabled = policy.powerMenuProtectionSupported()
        binding.tvPowerMenuDetail.text = policy.powerMenuSupportMessage()

        binding.switchCaptureOnSystemUi.isEnabled = false
        binding.tvCaptureOnSystemUiDetail.text =
            "O Android não fornece um evento oficial informando que o menu de energia ou a sombra foram abertos. " +
                "A captura continua vinculada aos eventos de intrusão já suportados pelo AutomBot Security."
    }
}
