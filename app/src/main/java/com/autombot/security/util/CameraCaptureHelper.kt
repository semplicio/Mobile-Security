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

class CameraCaptureHelper(private val context: Context) {

    fun capturePhotos(
        lifecycleOwner: LifecycleOwner,
        count: Int,
        lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
        onComplete: (List<File>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val target = count.coerceIn(1, 5)
        val captured = mutableListOf<File>()

        fun captureNext() {
            if (captured.size >= target) {
                onComplete(captured)
                return
            }

            capturePhoto(
                lifecycleOwner = lifecycleOwner,
                lensFacing = lensFacing,
                onSuccess = {
                    captured += it
                    captureNext()
                },
                onError = {
                    if (captured.isNotEmpty()) onComplete(captured) else onError(it)
                }
            )
        }

        captureNext()
    }

    fun capturePhoto(
        lifecycleOwner: LifecycleOwner,
        lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
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

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                if (!cameraProvider.hasCamera(selector)) {
                    onError(IllegalStateException("Câmera solicitada não está disponível"))
                    return@addListener
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    imageCapture
                )

                val outputDir = File(context.filesDir, "intrusion_photos").apply { mkdirs() }
                val side = if (lensFacing == CameraSelector.LENS_FACING_BACK) "traseira" else "frontal"
                val photoFile = File(
                    outputDir,
                    "intruso_${side}_${timestamp()}_${System.nanoTime()}.jpg"
                )
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
