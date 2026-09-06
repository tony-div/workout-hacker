package com.workouthacker.revamp.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.workoutpose.PoseAnalyzer
import com.workoutpose.WorkoutPoseConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns the CameraX lifecycle and feeds the [workout-pose][PoseAnalyzer] consumer.
 *
 * Responsibilities kept in the app (NOT the library): acquiring [ProcessCameraProvider],
 * binding [Preview] + [ImageAnalysis] to the [LifecycleOwner], frame-rate gating, and
 * converting/rotating camera frames to upright bitmaps for the analyzer.
 *
 * The [PoseAnalyzer] is created by the caller (Compose layer) so its [StateFlow] is stable
 * for the screen's lifetime; the controller simply pumps frames into it.
 *
 * The library (`workout-pose`) is intentionally free of CameraX so it can be consumed
 * by any frame source; see `PoseAnalyzer`.
 */
class WorkoutCameraController {

    private var analyzer: PoseAnalyzer? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null
    private var lastFrameTimeMs = 0L

    @get:Synchronized
    val isRunning: Boolean
        get() = analyzer?.isInitialized == true

    /**
     * Starts (or restarts) the camera + detection pipeline. Returns false if the model failed to
     * load.
     *
     * @param analyzer the pose consumer to feed; prepared with [config] and released on [stop].
     * @param config pose processing + frame rate configuration (see [WorkoutPoseConfig]).
     * @param lensFacing one of [CameraSelector.LENS_FACING_FRONT] / [CameraSelector.LENS_FACING_BACK].
     */
    @Synchronized
    fun start(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: PoseAnalyzer,
        config: WorkoutPoseConfig,
        lensFacing: Int,
    ): Boolean {
        stop()

        if (!analyzer.prepare(config, context)) return false
        this.analyzer = analyzer

        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    bindUseCases(provider, lifecycleOwner, previewView, config, lensFacing)
                } catch (e: Exception) {
                    Log.e(TAG, "start: failed to get camera provider", e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )

        Log.d(TAG, "start: pipeline started")
        return true
    }

    /** Live-retunes the One Euro filter without restarting the camera or model. */
    @Synchronized
    fun updateOneEuroParameters(minCutoff: Float, beta: Float, dCutoff: Float) {
        analyzer?.updateOneEuroParameters(minCutoff, beta, dCutoff)
    }

    @Synchronized
    fun stop() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}
        cameraProvider = null
        cameraExecutor?.shutdown()
        cameraExecutor = null
        analyzer?.release()
        analyzer = null
        lastFrameTimeMs = 0L
    }

    private fun bindUseCases(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        config: WorkoutPoseConfig,
        lensFacing: Int,
    ) {
        val preview =
            Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }

        val imageAnalysis =
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor ?: return@also) { imageProxy ->
                        processFrame(imageProxy, config)
                    }
                }

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
            Log.d(TAG, "bindUseCases: bound preview + imageAnalysis")
        } catch (e: Exception) {
            Log.e(TAG, "bindUseCases: failed: ${e.message}", e)
        }
    }

    private fun processFrame(imageProxy: ImageProxy, config: WorkoutPoseConfig) {
        val now = SystemClock.uptimeMillis()
        val sampleHz = config.inferenceSampleRateHz
        if (sampleHz > 0f) {
            val minIntervalMs = (1000f / sampleHz).toLong().coerceAtLeast(1L)
            if (now - lastFrameTimeMs < minIntervalMs) {
                imageProxy.close()
                return
            }
        }
        lastFrameTimeMs = now

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val sensorBitmap = imageProxy.toArgbBitmap()
        val rawTimestampNs = imageProxy.imageInfo.timestamp
        val timestampMs =
            if (rawTimestampNs > 0) {
                rawTimestampNs / 1_000_000
            } else {
                System.currentTimeMillis()
            }
        imageProxy.close()
        if (sensorBitmap == null) return

        // Rotate the sensor frame into upright (display) orientation so landmark
        // coordinates share the exact same frame as the PreviewView.
        val uprightBitmap =
            if (rotationDegrees == 0) {
                sensorBitmap
            } else {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(
                        sensorBitmap,
                        0,
                        0,
                        sensorBitmap.width,
                        sensorBitmap.height,
                        matrix,
                        true
                    )
                    .also { sensorBitmap.recycle() }
            }

        analyzer?.detectAsync(uprightBitmap, timestampMs)
        uprightBitmap.recycle()
    }

    private fun ImageProxy.toArgbBitmap(): Bitmap? {
        return try {
            val width = this.width
            val height = this.height
            if (width <= 0 || height <= 0) return null

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val plane = planes[0]
            val buffer = plane.buffer
            buffer.rewind()
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            if (rowStride == width * pixelStride) {
                bitmap.copyPixelsFromBuffer(buffer)
            } else {
                val pixels = IntArray(width * height)
                for (y in 0 until height) {
                    val rowStart = y * rowStride
                    for (x in 0 until width) {
                        val pixelStart = rowStart + x * pixelStride
                        if (pixelStart + 3 >= buffer.limit()) break
                        val r = buffer.get(pixelStart).toInt() and 0xFF
                        val g = buffer.get(pixelStart + 1).toInt() and 0xFF
                        val b = buffer.get(pixelStart + 2).toInt() and 0xFF
                        val a = buffer.get(pixelStart + 3).toInt() and 0xFF
                        pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "toArgbBitmap failed: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "WorkoutCameraController"
    }
}