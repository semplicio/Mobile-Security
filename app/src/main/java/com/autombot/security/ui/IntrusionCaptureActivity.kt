package com.autombot.security.ui

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
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
    private var protectionActive = true
    private var managedLockTaskStarted = false
    private var evidenceProcessed = false
    private var protectionTimer: CountDownTimer? = null

    private var photosCompleted = false
    private var audioCompleted = false
    private var videosStarted = false

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
        window.statusBarColor = Color.rgb(0, 126, 121)
        window.navigationBarColor = Color.rgb(10, 92, 88)

        binding = ActivityIntrusionCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        cameraHelper = CameraCaptureHelper(this)
        audioHelper = AudioRecorderHelper(this)
        videoHelper = VideoCaptureHelper(this)

        binding.tvRecordingIndicator.text = "Processamento de segurança ativo"

        applyInstalledLauncherIcons()
        wireLauncherInteractions()
        configureBackGuard()
        enterManagedLockTaskIfPermitted()
        startProtectionTimer()
    }

    override fun onResume() {
        super.onResume()
        if (started) return
        started = true
        startFastEvidenceCapture()
    }

    override fun onDestroy() {
        protectionTimer?.cancel()
        exitManagedLockTask()
        super.onDestroy()
    }

    private fun startFastEvidenceCapture() {
        startAudioCaptureInParallel()
        captureFrontPhotos()
    }

    /**
     * O áudio começa junto com as fotos. Os vídeos só começam quando fotos e
     * áudio terminarem, evitando conflito de recursos e reduzindo o tempo total.
     */
    private fun startAudioCaptureInParallel() {
        if (!prefs.recordAudioEnabled) {
            audioCompleted = true
            maybeStartVideos()
            return
        }

        audioHelper.recordFor(
            durationMs = AUDIO_DURATION_MS,
            onSuccess = { file ->
                evidencePaths += file.absolutePath
                audioCompleted = true
                maybeStartVideos()
            },
            onError = {
                audioCompleted = true
                maybeStartVideos()
            }
        )
    }

    private fun captureFrontPhotos() {
        if (!prefs.capturePhotosEnabled) {
            photosCompleted = true
            maybeStartVideos()
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
        cameraHelper.capturePhotos(
            lifecycleOwner = this,
            count = 2,
            lensFacing = CameraSelector.LENS_FACING_BACK,
            onComplete = { files ->
                evidencePaths += files.map { it.absolutePath }
                photosCompleted = true
                maybeStartVideos()
            },
            onError = {
                photosCompleted = true
                maybeStartVideos()
            }
        )
    }

    private fun maybeStartVideos() {
        if (!photosCompleted || !audioCompleted || videosStarted) return
        videosStarted = true
        recordFrontVideo()
    }

    private fun recordFrontVideo() {
        if (!prefs.recordVideoEnabled) {
            sendCapturedEvidence()
            return
        }

        videoHelper.recordFor(
            lifecycleOwner = this,
            durationMs = VIDEO_DURATION_MS,
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
        videoHelper.recordFor(
            lifecycleOwner = this,
            durationMs = VIDEO_DURATION_MS,
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

    private fun applyInstalledLauncherIcons() {
        applyInstalledIcon(
            binding.searchLogo,
            listOf("com.google.android.googlequicksearchbox")
        )
        applyInstalledIcon(
            binding.dockPhone,
            listOf(
                "com.google.android.dialer",
                "com.android.dialer",
                "com.motorola.dialer"
            )
        )
        applyInstalledIcon(
            binding.dockContacts,
            listOf(
                "com.google.android.contacts",
                "com.android.contacts"
            )
        )
        applyInstalledIcon(
            binding.dockBrowser,
            listOf(
                "com.android.chrome",
                "com.google.android.apps.chrome"
            )
        )
        applyInstalledIcon(
            binding.dockMessages,
            listOf(
                "com.whatsapp",
                "com.google.android.apps.messaging",
                "com.android.messaging"
            )
        )
        applyInstalledIcon(
            binding.dockCamera,
            listOf(
                "com.google.android.GoogleCamera",
                "com.motorola.camera3",
                "com.motorola.camera2",
                "com.android.camera2",
                "com.android.camera"
            )
        )
    }

    private fun applyInstalledIcon(view: ImageView, packages: List<String>) {
        val drawable = packages.firstNotNullOfOrNull { packageName ->
            runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
        } ?: return

        view.background = null
        view.setPadding(0, 0, 0, 0)
        view.setImageDrawable(drawable)
        view.imageTintList = null
    }

    private fun wireLauncherInteractions() {
        binding.searchBar.setOnClickListener {
            showInteraction("Pesquisa", "Pesquisa temporariamente indisponível.")
        }
        binding.dockPhone.setOnClickListener { showProtectedShortcut("Telefone") }
        binding.dockContacts.setOnClickListener { showProtectedShortcut("Contatos") }
        binding.dockBrowser.setOnClickListener { showProtectedShortcut("Navegador") }
        binding.dockMessages.setOnClickListener { showProtectedShortcut("Mensagens") }
        binding.dockCamera.setOnClickListener { showProtectedShortcut("Câmera") }

        binding.btnPanelClose.setOnClickListener {
            binding.interactionPanel.visibility = View.GONE
        }
    }

    private fun showProtectedShortcut(name: String) {
        showInteraction(name, "Aplicativo temporariamente indisponível. Tente novamente em instantes.")
    }

    private fun showInteraction(title: String, body: String) {
        binding.tvInteractionTitle.text = title
        binding.tvInteractionBody.text = body
        binding.interactionPanel.visibility = View.VISIBLE
    }

    private fun configureBackGuard() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.interactionPanel.visibility == View.VISIBLE) {
                        binding.interactionPanel.visibility = View.GONE
                        return
                    }

                    if (!protectionActive) {
                        finish()
                    }
                }
            }
        )
    }

    private fun startProtectionTimer() {
        protectionTimer?.cancel()
        protectionTimer = object : CountDownTimer(PROTECTION_DURATION_MS, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = ((millisUntilFinished + 999L) / 1_000L).toInt()
                binding.tvProtectionStatus.text = "AUTOMBOT • ${seconds} s"
            }

            override fun onFinish() {
                protectionActive = false
                binding.tvProtectionStatus.text = "AUTOMBOT • SAÍDA LIBERADA"
                exitManagedLockTask()

                if (evidenceProcessed) {
                    finish()
                }
            }
        }.start()
    }

    private fun enterManagedLockTaskIfPermitted() {
        val devicePolicyManager = getSystemService(DevicePolicyManager::class.java)
        if (!devicePolicyManager.isLockTaskPermitted(packageName)) return

        runCatching {
            startLockTask()
            managedLockTaskStarted = true
        }
    }

    private fun exitManagedLockTask() {
        if (!managedLockTaskStarted) return

        runCatching { stopLockTask() }
        managedLockTaskStarted = false
    }

    private fun sendCapturedEvidence() {
        if (evidenceProcessed) return

        val intent = Intent(this, SecurityMonitorService::class.java).apply {
            action = SecurityMonitorService.ACTION_PROCESS_CAPTURED_EVIDENCE
            putStringArrayListExtra(
                SecurityMonitorService.EXTRA_EVIDENCE_PATHS,
                ArrayList(evidencePaths.distinct())
            )
        }
        ContextCompat.startForegroundService(this, intent)
        evidenceProcessed = true

        if (!protectionActive) {
            finish()
        }
    }

    companion object {
        private const val PROTECTION_DURATION_MS = 20_000L
        private const val AUDIO_DURATION_MS = 3_000L
        private const val VIDEO_DURATION_MS = 2_500L
    }
}
