package com.autombot.security.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.autombot.security.databinding.ActivityIntrusionCaptureBinding
import com.autombot.security.service.SecurityMonitorService
import com.autombot.security.util.AudioRecorderHelper
import com.autombot.security.util.CameraCaptureHelper
import com.autombot.security.util.PrefsManager
import com.autombot.security.util.VideoCaptureHelper

class IntrusionCaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntrusionCaptureBinding
    private lateinit var prefs: PrefsManager
    private lateinit var cameraHelper: CameraCaptureHelper
    private lateinit var audioHelper: AudioRecorderHelper
    private lateinit var videoHelper: VideoCaptureHelper
    private var started = false
    private val evidencePaths = mutableListOf<String>()

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
        audioHelper = AudioRecorderHelper(this)
        videoHelper = VideoCaptureHelper(this)

        binding.tvSecurityMessage.text = if (prefs.showAlertMessageEnabled) prefs.securityMessage else ""
        binding.tvDetail.text = "Proteção ativa"
        binding.tvRecordingIndicator.text = "Processando…"
    }

    override fun onResume() {
        super.onResume()
        if (started) return
        started = true
        capturePhotosThenContinue()
    }

    private fun capturePhotosThenContinue() {
        if (!prefs.capturePhotosEnabled) {
            recordAudioThenContinue()
            return
        }

        binding.tvRecordingIndicator.text = "Processando…"
        cameraHelper.capturePhotos(
            lifecycleOwner = this,
            count = prefs.photoCount,
            onComplete = { files ->
                evidencePaths += files.map { it.absolutePath }
                recordAudioThenContinue()
            },
            onError = {
                recordAudioThenContinue()
            }
        )
    }

    private fun recordAudioThenContinue() {
        if (!prefs.recordAudioEnabled) {
            recordVideoThenFinish()
            return
        }

        binding.tvRecordingIndicator.text = "Processando…"
        audioHelper.recordFiveSeconds(
            onSuccess = { file ->
                evidencePaths += file.absolutePath
                recordVideoThenFinish()
            },
            onError = {
                recordVideoThenFinish()
            }
        )
    }

    private fun recordVideoThenFinish() {
        if (!prefs.recordVideoEnabled) {
            sendCapturedEvidence()
            return
        }

        binding.tvRecordingIndicator.text = "Processando…"
        videoHelper.recordFiveSeconds(
            lifecycleOwner = this,
            withAudio = false,
            onSuccess = { file ->
                evidencePaths += file.absolutePath
                sendCapturedEvidence()
            },
            onError = {
                sendCapturedEvidence()
            }
        )
    }

    private fun sendCapturedEvidence() {
        binding.tvRecordingIndicator.text = "Concluído"
        val intent = Intent(this, SecurityMonitorService::class.java).apply {
            action = SecurityMonitorService.ACTION_PROCESS_CAPTURED_EVIDENCE
            putStringArrayListExtra(
                SecurityMonitorService.EXTRA_EVIDENCE_PATHS,
                ArrayList(evidencePaths)
            )
        }
        startForegroundService(intent)
        finish()
    }
}
