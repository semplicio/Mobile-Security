package com.autombot.security.util

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCapture.OutputFileResults
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Captura uma foto usando a câmera frontal (quem está mexendo no aparelho),
 * sem precisar mostrar preview na tela — usada quando o app detecta uma
 * tentativa de invasão.
 *
 * Requer um LifecycleOwner. Na base, o SecurityMonitorService implementa
 * LifecycleOwner (via LifecycleService) para poder acionar isso mesmo sem
 * Activity em primeiro plano.
 */
class CameraCaptureHelper(private val context: Context) {

    fun capturePhoto(
        lifecycleOwner: LifecycleOwner,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)

                val outputDir = File(context.filesDir, "intrusion_photos").apply { mkdirs() }
                val fileName = "intruso_${timestamp()}.jpg"
                val photoFile = File(outputDir, fileName)

                val outputOptions = OutputFileOptions.Builder(photoFile).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: OutputFileResults) {
                            Log.i(TAG, "Foto capturada: ${photoFile.absolutePath}")
                            cameraProvider.unbindAll()
                            onSuccess(photoFile)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Erro ao capturar foto", exception)
                            cameraProvider.unbindAll()
                            onError(exception)
                        }
                    }
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Erro ao inicializar câmera", t)
                onError(t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())

    companion object {
        private const val TAG = "CameraCaptureHelper"
    }
}
