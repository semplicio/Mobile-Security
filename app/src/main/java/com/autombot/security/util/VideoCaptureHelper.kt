package com.autombot.security.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoCaptureHelper(private val context: Context) {

    fun recordFiveSeconds(
        lifecycleOwner: LifecycleOwner,
        withAudio: Boolean,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.SD))
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    videoCapture
                )

                val outputDir = File(context.filesDir, "intrusion_video").apply { mkdirs() }
                val file = File(outputDir, "video_${timestamp()}.mp4")
                val output = FileOutputOptions.Builder(file).build()
                var pending = videoCapture.output.prepareRecording(context, output)

                if (withAudio && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    pending = pending.withAudioEnabled()
                }

                var recording: Recording? = null
                recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        provider.unbindAll()
                        if (!event.hasError() && file.exists() && file.length() > 0L) {
                            onSuccess(file)
                        } else {
                            onError(IllegalStateException("Falha ao finalizar gravação de vídeo: ${event.error}"))
                        }
                    }
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    runCatching { recording?.stop() }
                }, 5000L)
            } catch (t: Throwable) {
                onError(t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
