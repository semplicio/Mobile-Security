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

    /**
     * Captura uma sequência de fotos mantendo a mesma câmera vinculada durante
     * toda a série. Isso evita o custo de abrir/fechar a câmera entre cada foto.
     */
    fun capturePhotos(
        lifecycleOwner: LifecycleOwner,
        count: Int,
        lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
        onComplete: (List<File>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val target = count.coerceIn(1, 5)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                if (!provider.hasCamera(selector)) {
                    onError(IllegalStateException("Câmera solicitada não está disponível"))
                    return@addListener
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(82)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    imageCapture
                )

                val captured = mutableListOf<File>()
                val outputDir = File(context.filesDir, "intrusion_photos").apply { mkdirs() }
                val side = if (lensFacing == CameraSelector.LENS_FACING_BACK) "traseira" else "frontal"

                fun finishSuccess() {
                    provider.unbindAll()
                    onComplete(captured.toList())
                }

                fun captureNext() {
                    if (captured.size >= target) {
                        finishSuccess()
                        return
                    }

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
                                captured += photoFile
                                captureNext()
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e(TAG, "Erro ao capturar foto", exception)
                                provider.unbindAll()
                                if (captured.isNotEmpty()) {
                                    onComplete(captured.toList())
                                } else {
                                    onError(exception)
                                }
                            }
                        }
                    )
                }

                captureNext()
            } catch (t: Throwable) {
                Log.e(TAG, "Erro ao inicializar câmera", t)
                onError(t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun capturePhoto(
        lifecycleOwner: LifecycleOwner,
        lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        capturePhotos(
            lifecycleOwner = lifecycleOwner,
            count = 1,
            lensFacing = lensFacing,
            onComplete = { files ->
                files.firstOrNull()?.let(onSuccess)
                    ?: onError(IllegalStateException("Nenhuma foto foi gerada"))
            },
            onError = onError
        )
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())

    companion object {
        private const val TAG = "CameraCaptureHelper"
    }
}
