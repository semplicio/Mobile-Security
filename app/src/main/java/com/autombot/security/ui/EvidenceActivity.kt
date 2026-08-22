package com.autombot.security.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.autombot.security.databinding.ActivityEvidenceBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EvidenceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEvidenceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEvidenceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadEvidence()
    }

    private fun loadEvidence() {
        val dir = File(filesDir, "intrusion_photos")
        val photos = dir.listFiles { file -> file.extension.equals("jpg", true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        binding.tvEmpty.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
        binding.evidenceContainer.removeAllViews()

        photos.forEach { photo ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setBackgroundResource(com.autombot.security.R.drawable.bg_security_card)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }
            }

            val image = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(84), dp(84))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(BitmapFactory.decodeFile(photo.absolutePath))
            }

            val info = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(14) }
                setTextColor(android.graphics.Color.WHITE)
                textSize = 15f
                text = buildString {
                    append("Captura de segurança\n")
                    append(formatDate(photo.lastModified()))
                    append("\n")
                    append(photo.name)
                }
            }

            card.addView(image)
            card.addView(info)
            binding.evidenceContainer.addView(card)
        }
    }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
