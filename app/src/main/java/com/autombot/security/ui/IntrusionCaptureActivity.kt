package com.autombot.security.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.autombot.security.databinding.ActivityIntrusionCaptureBinding
import com.autombot.security.service.SecurityMonitorService
import com.autombot.security.util.CameraCaptureHelper
import com.autombot.security.util.PrefsManager

/**
 * Activity visível usada como ponte para acesso à câmera em Android moderno.
 * Ela aparece sobre a tela bloqueada, informa claramente que o aparelho está
 * protegido e captura evidências somente enquanto está visível.
 */
class IntrusionCaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntrusionCaptureBinding
    private lateinit var prefs: PrefsManager
    private lateinit var cameraHelper: CameraCaptureHelper
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityIntrusionCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        cameraHelper = CameraCaptureHelper(this)

        binding.tvDetail.text = if (prefs.capturePhotosEnabled) {
            "Registrando evidência de segurança…"
        } else {
            "Registrando evento de segurança…"
        }
    }

    override fun onResume() {
        super.onResume()
        if (completed) return

        if (!prefs.capturePhotosEnabled) {
            completed = true
            sendCapturedEvidence(emptyList())
            return
        }

        cameraHelper.capturePhotos(
            lifecycleOwner = this,
            count = prefs.photoCount,
            onComplete = { files ->
                completed = true
                sendCapturedEvidence(files.map { it.absolutePath })
            },
            onError = {
                completed = true
                sendCapturedEvidence(emptyList())
            }
        )
    }

    private fun sendCapturedEvidence(paths: List<String>) {
        val intent = Intent(this, SecurityMonitorService::class.java).apply {
            action = SecurityMonitorService.ACTION_PROCESS_CAPTURED_EVIDENCE
            putStringArrayListExtra(SecurityMonitorService.EXTRA_PHOTO_PATHS, ArrayList(paths))
        }
        startForegroundService(intent)
        finish()
    }
}
