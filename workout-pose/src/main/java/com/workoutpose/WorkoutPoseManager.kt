package com.workoutpose

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
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-level facade of the workout-pose module.
 *
 * Binds a CameraX [Preview] (into the supplied [PreviewView]) plus an [ImageAnalysis] pipeline to
 * the given [LifecycleOwner], runs MediaPipe pose detection in LIVE_STREAM mode and publishes
 * processed frames on [poseState].
 *
 * Usage (Compose):
 * ```
 * val manager = remember { WorkoutPoseManager() }
 * val poseFrame by manager.poseState.collectAsStateWithLifecycle()
 * LaunchedEffect(config, hasPermission) {
 *     if (hasPermission) manager.start(context, lifecycleOwner, previewView, config)
 * }
 * DisposableEffect(manager) { onDispose { manager.stop() } }
 * ```
 */
class WorkoutPoseManager {

    private val engine = WorkoutPoseEngine(WorkoutPoseConfig.DEFAULT)

    private var helper: PoseLandmarkerHelper? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null
    private val inferenceTimestamps = ConcurrentHashMap<Long, Long>()

    private val _poseState = MutableStateFlow(PoseFrame.empty())
    val poseState: StateFlow<PoseFrame> = _poseState.asStateFlow()

    @get:Synchronized
    val isRunning: Boolean
        get() = helper?.isInitialized() == true

    /**
     * Starts (or restarts) the camera + detection pipeline. Returns false if the model failed to
     * load.
     */
    @Synchronized
    fun start(
            context: Context,
            lifecycleOwner: LifecycleOwner,
            previewView: PreviewView,
            config: WorkoutPoseConfig,
    ): Boolean {
        stop()

        engine.config = config
        cameraExecutor = Executors.newSingleThreadExecutor()

        helper =
                PoseLandmarkerHelper(
                        config = config,
                        context = context,
                        onResult = { result -> processResult(result) },
                )
        if (helper?.isInitialized() != true) {
            Log.e(TAG, "start: pose landmarker failed to initialize (missing model asset?)")
            helper = null
            cameraExecutor?.shutdown()
            cameraExecutor = null
            return false
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
                {
                    try {
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider
                        bindUseCases(provider, lifecycleOwner, previewView, config)
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
        engine.updateOneEuroParameters(minCutoff, beta, dCutoff)
    }

    @Synchronized
    fun stop() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}
        cameraProvider = null
        helper?.clearPoseLandmarker()
        helper = null
        cameraExecutor?.shutdown()
        cameraExecutor = null
        inferenceTimestamps.clear()
        engine.resetFilters()
        _poseState.value = PoseFrame.empty()
    }

    private fun bindUseCases(
            provider: ProcessCameraProvider,
            lifecycleOwner: LifecycleOwner,
            previewView: PreviewView,
            config: WorkoutPoseConfig,
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

        val selector = CameraSelector.Builder().requireLensFacing(config.lensFacing).build()

        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
            Log.d(TAG, "bindUseCases: bound preview + imageAnalysis")
        } catch (e: Exception) {
            Log.e(TAG, "bindUseCases: failed: ${e.message}", e)
        }
    }

    private var lastFrameTimeMs = 0L

    private fun processFrame(imageProxy: ImageProxy, config: WorkoutPoseConfig) {
        val now = System.currentTimeMillis()
        val minIntervalMs = (1000f / config.inferenceSampleRateHz).toLong().coerceAtLeast(1L)
        if (now - lastFrameTimeMs < minIntervalMs) {
            imageProxy.close()
            return
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

        inferenceTimestamps[timestampMs] = SystemClock.uptimeMillis()
        helper?.detectAsync(uprightBitmap, timestampMs)
        uprightBitmap.recycle()
    }

    private fun processResult(result: PoseLandmarkerResult) {
        val pose =
                runCatching {
                    val landmarks = result.landmarks()
                    if (landmarks.isEmpty() || landmarks[0].isEmpty()) return
                    landmarks[0]
                }
                        .getOrElse {
                            Log.e(TAG, "processResult: ${it.message}")
                            return
                        }

        val coords = FloatArray(pose.size * 3)
        val visibility = FloatArray(pose.size)
        for (i in pose.indices) {
            val lm = pose[i]
            coords[i * 3] = lm.x()
            coords[i * 3 + 1] = lm.y()
            coords[i * 3 + 2] = lm.z()
            visibility[i] = if (lm.visibility().isPresent) lm.visibility().get() else 1f
        }

        val submissionTimeNs =
                inferenceTimestamps.remove(result.timestampMs()) ?: SystemClock.uptimeMillis()
        val inferenceTimeMs =
                (SystemClock.uptimeMillis() - submissionTimeNs).coerceAtLeast(0L).toDouble()

        val flat = engine.feed(coords, visibility, System.currentTimeMillis(), inferenceTimeMs)
        if (flat.isEmpty()) return

        _poseState.value =
                PoseFrame(
                        landmarks = flat,
                        angles = PoseGeometry.jointAngles(flat),
                        poseVisible =
                                PoseGeometry.isPoseVisible(
                                        flat,
                                        engine.config.minVisibilityConfidence
                                ),
                        visibleLandmarkCount =
                                PoseGeometry.countVisible(
                                        flat,
                                        engine.config.minVisibilityConfidence
                                ),
                        inferenceTimeMs = engine.lastInferenceTimeMs,
                        timestampMs = engine.lastFrameTimestampMs,
                )
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
                    buffer.position(y * rowStride)
                    for (x in 0 until width) {
                        val r = buffer.get().toInt() and 0xFF
                        val g = buffer.get().toInt() and 0xFF
                        val b = buffer.get().toInt() and 0xFF
                        val a = buffer.get().toInt() and 0xFF
                        pixels[y * width + x] =
                                if (pixelStride == 4) {
                                    (a shl 24) or (r shl 16) or (g shl 8) or b
                                } else {
                                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                                }
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
        const val TAG = "WorkoutPoseManager"
    }
}
