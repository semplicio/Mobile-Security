package com.autombot.security.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.core.content.ContextCompat
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

        binding.tvSecurityMessage.text =
            "Tecnologia, automação e soluções inteligentes para proteger e simplificar seu dia a dia."
        binding.tvDetail.text = "autombot.com.br"
        binding.tvRecordingIndicator.text =
            "Proteção ativa • registro de evidências em andamento"
    }

    override fun onResume() {
        super.onResume()
        if (started) return
        started = true
        captureFrontPhotos()
    }

    private fun captureFrontPhotos() {
        if (!prefs.capturePhotosEnabled) {
            captureBackPhotos()
            return
        }

        cameraHelper.capturePhotos(
            lifecycleOwner = this,
            count = 2,
            lensFacing = CameraSelector.LENS_FACING_FRONT,
            onComplete = { files ->
                evidencePaths += files.map { it.absolutePath }
                captureBackPhotos()
            },
            onError = {
                captureBackPhotos()
            }
        )
    }

    private fun captureBackPhotos() {
        if (!prefs.capturePhotosEnabled) {
            recordAudioThenContinue()
            return
        }

        cameraHelper.capturePhotos(
            lifecycleOwner = this,
            count = 2,
            lensFacing = CameraSelector.LENS_FACING_BACK,
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
            recordFrontVideo()
            return
        }

        audioHelper.recordFiveSeconds(
            onSuccess = { file ->
                evidencePaths += file.absolutePath
                recordFrontVideo()
            },
            onError = {
                recordFrontVideo()
            }
        )
    }

    private fun recordFrontVideo() {
        if (!prefs.recordVideoEnabled) {
            recordBackVideo()
            return
        }

        videoHelper.recordFiveSeconds(
            lifecycleOwner = this,
            withAudio = false,
            lensFacing = CameraSelector.LENS_FACING_FRONT,
            onSuccess = { file ->
                evidencePaths += file.absolutePath
                recordBackVideo()
            },
            onError = {
                recordBackVideo()
            }
        )
    }

    private fun recordBackVideo() {
        if (!prefs.recordVideoEnabled) {
            sendCapturedEvidence()
            return
        }

        videoHelper.recordFiveSeconds(
            lifecycleOwner = this,
            withAudio = false,
            lensFacing = CameraSelector.LENS_FACING_BACK,
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
        binding.tvRecordingIndicator.text = "Proteção ativa • finalizando registro"

        val intent = Intent(this, SecurityMonitorService::class.java).apply {
            action = SecurityMonitorService.ACTION_PROCESS_CAPTURED_EVIDENCE
            putStringArrayListExtra(
                SecurityMonitorService.EXTRA_EVIDENCE_PATHS,
                ArrayList(evidencePaths)
            )
        }
        ContextCompat.startForegroundService(this, intent)
        finish()
    }
}
