package com.autombot.security.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.autombot.security.databinding.ActivitySafeModeBinding

/**
 * Tela alternativa do próprio AutomBot Security.
 *
 * Ela não substitui nem imita a tela de desbloqueio do Android e não inicia
 * câmera, microfone ou gravação de vídeo. O aparelho continua bloqueado pelo
 * sistema operacional por baixo desta Activity.
 */
class SafeModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySafeModeBinding

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

        binding = ActivitySafeModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showSection(
            title = "Visão geral",
            detail = "O aplicativo está disponível. Selecione uma opção."
        )

        binding.btnOverview.setOnClickListener {
            showSection(
                title = "Visão geral",
                detail = "Nenhuma ação pendente."
            )
        }

        binding.btnTools.setOnClickListener {
            showSection(
                title = "Ferramentas",
                detail = "Os recursos protegidos não ficam disponíveis neste modo."
            )
        }

        binding.btnAbout.setOnClickListener {
            showSection(
                title = "Sobre",
                detail = "AutomBot Security"
            )
        }

        binding.btnClose.setOnClickListener {
            finish()
        }
    }

    private fun showSection(title: String, detail: String) {
        binding.tvSectionTitle.text = title
        binding.tvSectionDetail.text = detail
    }
}
