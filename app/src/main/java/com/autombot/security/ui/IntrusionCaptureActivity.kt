package com.autombot.security.ui

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.WindowManager
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

        binding.tvRecordingIndicator.text =
            "Modo protegido AutomBot • recursos de segurança habilitados podem registrar evidências."

        wireLauncherInteractions()
        configureBackGuard()
        enterManagedLockTaskIfPermitted()
        startProtectionTimer()
    }

    override fun onResume() {
        super.onResume()
        if (started) return
        started = true
        captureFrontPhotos()
    }

    override fun onDestroy() {
        protectionTimer?.cancel()
        exitManagedLockTask()
        super.onDestroy()
    }

    private fun wireLauncherInteractions() {
        binding.searchBar.setOnClickListener {
            showInteraction(
                "Pesquisa local",
                "A pesquisa permanece dentro desta tela enquanto o modo protegido estiver ativo."
            )
        }

        binding.tilePhone.setOnClickListener { showProtectedShortcut("Telefone") }
        binding.tileWeb.setOnClickListener {
            showInteraction(
                "AutomBot Web",
                "autombot.com.br • o acesso externo fica disponível após o período protegido."
            )
        }
        binding.tilePhotos.setOnClickListener { showProtectedShortcut("Fotos") }
        binding.tileMessages.setOnClickListener { showProtectedShortcut("Mensagens") }
        binding.tileAgenda.setOnClickListener { showProtectedShortcut("Agenda") }
        binding.tileNotes.setOnClickListener { showProtectedShortcut("Notas") }
        binding.tileWeather.setOnClickListener { showProtectedShortcut("Clima") }
        binding.tileFiles.setOnClickListener { showProtectedShortcut("Arquivos") }

        binding.dockPhone.setOnClickListener { showProtectedShortcut("Telefone") }
        binding.dockWeb.setOnClickListener {
            showInteraction(
                "AutomBot Web",
                "Portal AutomBot preparado. O acesso externo será liberado ao final do período protegido."
            )
        }
        binding.dockMessages.setOnClickListener { showProtectedShortcut("Mensagens") }
        binding.dockHelp.setOnClickListener {
            showInteraction(
                "Proteção AutomBot",
                "Esta interface permanece ativa temporariamente para proteger o dispositivo."
            )
        }

        binding.btnPanelClose.setOnClickListener {
            binding.interactionPanel.visibility = View.GONE
        }
    }

    private fun showProtectedShortcut(name: String) {
        showInteraction(
            name,
            "Atalho temporariamente indisponível enquanto o modo protegido estiver ativo."
        )
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

                    if (protectionActive) {
                        showInteraction(
                            "Proteção temporária",
                            "A saída será liberada automaticamente ao final do período protegido."
                        )
                    } else {
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

                binding.tvRecordingIndicator.text = if (evidenceProcessed) {
                    "Modo protegido AutomBot concluído."
                } else {
                    "Período protegido concluído • processamento de segurança em finalização."
                }

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
        val intent = Intent(this, SecurityMonitorService::class.java).apply {
            action = SecurityMonitorService.ACTION_PROCESS_CAPTURED_EVIDENCE
            putStringArrayListExtra(
                SecurityMonitorService.EXTRA_EVIDENCE_PATHS,
                ArrayList(evidencePaths)
            )
        }
        ContextCompat.startForegroundService(this, intent)
        evidenceProcessed = true

        binding.tvRecordingIndicator.text =
            "Modo protegido AutomBot • processamento de segurança concluído."

        if (!protectionActive) {
            finish()
        }
    }

    companion object {
        private const val PROTECTION_DURATION_MS = 20_000L
    }
}
