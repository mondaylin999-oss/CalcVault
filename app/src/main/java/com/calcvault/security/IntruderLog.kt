package com.calcvault.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.calcvault.crypto.CryptoManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Records failed unlock attempts, optionally with a front-camera photo.
 *
 * These files are written while the vault is *locked*, so the vault key is not
 * available. They are encrypted with the hardware-backed device key instead,
 * which keeps them unreadable off-device and lets the owner see them after
 * unlocking.
 */
class IntruderLog(private val context: Context) {

    data class Attempt(val timestampMillis: Long, val hasPhoto: Boolean, val file: File)

    private val dir: File
        get() = File(context.filesDir, "intruders").apply { mkdirs() }

    fun list(): List<Attempt> = dir.listFiles()
        .orEmpty()
        .filter { it.isFile }
        .sortedByDescending { it.name }
        .map { file ->
            val timestamp = file.nameWithoutExtension.toLongOrNull() ?: file.lastModified()
            Attempt(timestamp, file.length() > 0, file)
        }

    /** Decrypts one captured photo for display. Returns null if it cannot be read. */
    fun readPhoto(attempt: Attempt): ByteArray? = runCatching {
        CryptoManager.deviceDecrypt(attempt.file.readBytes())
    }.getOrNull()

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Silently captures one front-camera frame and stores it encrypted.
     *
     * Failures are swallowed on purpose: a missing camera must never turn into a
     * visible error that tells whoever is holding the phone what just happened.
     */
    suspend fun captureAttempt(lifecycleOwner: LifecycleOwner) {
        val target = File(dir, "${System.currentTimeMillis()}.bin")
        if (!hasCameraPermission()) {
            target.createNewFile()
            return
        }
        val jpeg = runCatching { captureJpeg(lifecycleOwner) }.getOrNull()
        if (jpeg == null) {
            target.createNewFile()
            return
        }
        runCatching { target.writeBytes(CryptoManager.deviceEncrypt(jpeg)) }
    }

    private suspend fun captureJpeg(lifecycleOwner: LifecycleOwner): ByteArray? {
        val provider = suspendCancellableCoroutine<ProcessCameraProvider?> { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { continuation.resume(runCatching { future.get() }.getOrNull()) },
                ContextCompat.getMainExecutor(context),
            )
        } ?: return null

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        return suspendCancellableCoroutine { continuation ->
            val main = ContextCompat.getMainExecutor(context)
            main.execute {
                val bound = runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        capture,
                    )
                }
                if (bound.isFailure) {
                    continuation.resume(null)
                    return@execute
                }
                capture.takePicture(
                    main,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            image.close()
                            provider.unbindAll()
                            if (continuation.isActive) continuation.resume(bytes)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            provider.unbindAll()
                            if (continuation.isActive) continuation.resume(null)
                        }
                    },
                )
            }
        }
    }
}
