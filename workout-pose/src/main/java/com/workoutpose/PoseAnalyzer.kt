package com.workoutpose

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure consumer of pose frames.
 *
 * The camera/lifecycle is owned by the host app; this class is fed upright (display-oriented)
 * [Bitmap]s, runs MediaPipe pose detection in LIVE_STREAM mode, post-processes the results (
 * [WorkoutPoseEngine]) and publishes [PoseFrame]s on [poseState].
 *
 * Usage (app side pump):
 * ```
 * val analyzer = remember { PoseAnalyzer(config, context) }
 * val poseFrame by analyzer.poseState.collectAsStateWithLifecycle()
 * // on each camera frame:
 * analyzer.detectAsync(uprightBitmap, timestampMs)
 * // when tearing down:
 * analyzer.release()
 * ```
 */
class PoseAnalyzer(
        config: WorkoutPoseConfig = WorkoutPoseConfig.DEFAULT,
) {
    private val engine = WorkoutPoseEngine(config)
    private val inferenceTimestamps = ConcurrentHashMap<Long, Long>()

    private var helper: PoseLandmarkerHelper? = null

    private var lastBenchmarkCoords: FloatArray? = null

    private val _poseState = MutableStateFlow(PoseFrame.empty())
    val poseState: StateFlow<PoseFrame> = _poseState.asStateFlow()

    val isInitialized: Boolean
        get() = helper?.isInitialized() == true

    /** Loads the pose landmarker for [config]. Safe to call again to (re)load a new model. */
    fun prepare(config: WorkoutPoseConfig, context: Context): Boolean {
        helper?.clearPoseLandmarker()

        engine.config = config
        helper =
                PoseLandmarkerHelper(
                        config = config,
                        context = context,
                        onResult = { result -> processResult(result) },
                )
        if (helper?.isInitialized() != true) {
            Log.e(TAG, "prepare: pose landmarker failed to initialize (missing model asset?)")
            helper = null
            inferenceTimestamps.clear()
            _poseState.value = PoseFrame.empty()
            return false
        }
        return true
    }

    /** Live-retunes the One Euro filter without reloading the model. */
    fun updateOneEuroParameters(minCutoff: Float, beta: Float, dCutoff: Float) {
        engine.updateOneEuroParameters(minCutoff, beta, dCutoff)
    }

    /** Live-retunes the visibility-confidence threshold without reloading the model. */
    fun updateVisibilityThreshold(minVisibilityConfidence: Float) {
        engine.updateVisibilityThreshold(minVisibilityConfidence)
    }

    /**
     * Feeds a bitmap (already upright / display-oriented) to MediaPipe for asynchronous detection.
     *
     * Note: no [com.google.mediapipe.tasks.vision.core.ImageProcessingOptions] rotation is used on
     * purpose. The caller feeds pre-rotated bitmaps; the Tasks rotation option is unreliable for
     * remapping the returned landmark coordinates into the rotated frame.
     */
    fun detectAsync(bitmap: Bitmap, timestampMs: Long) {
        inferenceTimestamps[timestampMs] = SystemClock.uptimeMillis()
        helper?.detectAsync(bitmap, timestampMs)
    }

    /** Releases the underlying landmarker. Safe to call multiple times. */
    fun release() {
        helper?.clearPoseLandmarker()
        helper = null
        inferenceTimestamps.clear()
        engine.resetFilters()
        _poseState.value = PoseFrame.empty()
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
                                        engine.currentMinVisibilityConfidence
                                ),
                        visibleLandmarkCount =
                                PoseGeometry.countVisible(
                                        flat,
                                        engine.currentMinVisibilityConfidence
                                ),
                        inferenceTimeMs = engine.lastInferenceTimeMs,
                        timestampMs = engine.lastFrameTimestampMs,
                )

        if (engine.config.enableBenchmarkLogging) {
            logPosePerf(coords, visibility, inferenceTimeMs, inferenceTimestamps.size)
        }
    }

    private fun logPosePerf(rawCoords: FloatArray, visibility: FloatArray, inferenceTimeMs: Double, backlog: Int) {
        var upperBodyVisible = 0
        for (i in UPPER_BODY_INDICES) {
            if (visibility[i] >= UPPER_BODY_VISIBILITY_THRESHOLD) upperBodyVisible++
        }

        var motion = 0f
        val prev = lastBenchmarkCoords
        if (prev != null && prev.size == rawCoords.size) {
            for (i in UPPER_BODY_INDICES) {
                if (visibility[i] < UPPER_BODY_VISIBILITY_THRESHOLD) continue
                val dx = rawCoords[i * 3] - prev[i * 3]
                val dy = rawCoords[i * 3 + 1] - prev[i * 3 + 1]
                motion += kotlin.math.sqrt(dx * dx + dy * dy)
            }
        }
        lastBenchmarkCoords = rawCoords.copyOf()

        Log.d(
                "PosePerf",
                "ts=${SystemClock.uptimeMillis()} " +
                        "inf=${String.format(Locale.US, "%.1f", inferenceTimeMs)} " +
                        "backlog=$backlog " +
                        "ub_vis=$upperBodyVisible " +
                        "motion=${String.format(Locale.US, "%.4f", motion)}",
        )
    }

    private companion object {
        const val TAG = "PoseAnalyzer"
        const val UPPER_BODY_VISIBILITY_THRESHOLD = 0.8f
        val UPPER_BODY_INDICES = intArrayOf(11, 12, 13, 14, 15, 16, 23, 24)
    }
}
