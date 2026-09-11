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

    /** Live-retunes the visibility-confidence threshold without restarting the camera or model. */
    @Synchronized
    fun updateVisibilityThreshold(minVisibilityConfidence: Float) {
        analyzer?.updateVisibilityThreshold(minVisibilityConfidence)
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
        val scaledBitmap = imageProxy.toDownscaledBitmap(MAX_POSE_INPUT_SIDE_LONG)
        val rawTimestampNs = imageProxy.imageInfo.timestamp
        val timestampMs =
            if (rawTimestampNs > 0) {
                rawTimestampNs / 1_000_000
            } else {
                System.currentTimeMillis()
            }
        imageProxy.close()
        if (scaledBitmap == null) return

        // Rotate the scaled sensor frame into upright (display) orientation so landmark
        // coordinates share the exact same frame as the PreviewView. Rotation on the small
        // bitmap is one cheap copy (the expensive full-res pass already fed the downscale).
        val uprightBitmap =
            if (rotationDegrees == 0) {
                scaledBitmap
            } else {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(
                        scaledBitmap,
                        0,
                        0,
                        scaledBitmap.width,
                        scaledBitmap.height,
                        matrix,
                        true
                    )
                    .also { scaledBitmap.recycle() }
            }

        analyzer?.detectAsync(uprightBitmap, timestampMs)
        uprightBitmap.recycle()
    }

    /**
     * Copies the RGBA sensor frame into an ARGB bitmap whose long side is capped at [maxSideLong],
     * using nearest-neighbour sampling. Rows are pulled out of the ByteBuffer in bulk (one native
     * copy per row) so per-pixel access stays on the JVM; when no scaling is needed and the row
     * stride is tight, delegates to the fast bulk [Bitmap.copyPixelsFromBuffer] instead.
     */
    private fun ImageProxy.toDownscaledBitmap(maxSideLong: Int): Bitmap? {
        return try {
            val width = this.width
            val height = this.height
            if (width <= 0 || height <= 0) return null

            val scale = minOf(1f, maxSideLong.toFloat() / maxOf(width, height))
            val outWidth = maxOf(1, (width * scale).toInt())
            val outHeight = maxOf(1, (height * scale).toInt())

            val plane = planes[0]
            val buffer = plane.buffer
            buffer.rewind()
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            if (scale >= 1f && rowStride == width * pixelStride) {
                return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    .also { it.copyPixelsFromBuffer(buffer) }
            }

            val rowBytes = width * pixelStride
            val row = ByteArray(rowBytes)
            val pixels = IntArray(outWidth * outHeight)
            for (dy in 0 until outHeight) {
                val sourceY = (dy / scale).toInt().coerceIn(0, height - 1)
                buffer.position(sourceY * rowStride)
                val count = minOf(rowBytes, buffer.remaining())
                buffer.get(row, 0, count)
                val rowBase = dy * outWidth
                for (dx in 0 until outWidth) {
                    val sourceX = (dx / scale).toInt().coerceIn(0, width - 1)
                    val p = sourceX * pixelStride
                    val r = row[p].toInt() and 0xFF
                    val g = row[p + 1].toInt() and 0xFF
                    val b = row[p + 2].toInt() and 0xFF
                    val a = row[p + 3].toInt() and 0xFF
                    pixels[rowBase + dx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            Bitmap.createBitmap(pixels, outWidth, outHeight, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Log.e(TAG, "toDownscaledBitmap failed: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "WorkoutCameraController"
        /** Long-side cap for the pose input bitmap (matching MediaPipe's internal resize). */
        const val MAX_POSE_INPUT_SIDE_LONG = 640
    }
}