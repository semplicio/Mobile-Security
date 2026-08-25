package com.autombot.security.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorderHelper(private val context: Context) {

    fun recordFor(
        durationMs: Long,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError(SecurityException("Permissão de microfone não concedida"))
            return
        }

        val outputDir = File(context.filesDir, "intrusion_audio").apply { mkdirs() }
        val file = File(outputDir, "audio_${timestamp()}.m4a")
        var recorder: MediaRecorder? = null

        try {
            recorder = createRecorder()
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(22050)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    recorder?.stop()
                    recorder?.release()
                    recorder = null
                    if (file.exists() && file.length() > 0L) {
                        onSuccess(file)
                    } else {
                        onError(IllegalStateException("Arquivo de áudio vazio"))
                    }
                } catch (t: Throwable) {
                    runCatching { recorder?.release() }
                    recorder = null
                    onError(t)
                }
            }, durationMs.coerceIn(1_500L, 10_000L))
        } catch (t: Throwable) {
            runCatching { recorder?.release() }
            onError(t)
        }
    }

    fun recordFiveSeconds(
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) = recordFor(5_000L, onSuccess, onError)

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
