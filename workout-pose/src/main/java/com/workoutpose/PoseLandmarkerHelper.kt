package com.workoutpose

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Thin wrapper around the MediaPipe Pose Landmarker running in LIVE_STREAM mode.
 * Owns the model lifecycle; results are pushed to [onResult].
 */
class PoseLandmarkerHelper(
    private val config: WorkoutPoseConfig,
    val context: Context,
    private val onResult: (PoseLandmarkerResult) -> Unit,
) {
    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    fun isInitialized(): Boolean = poseLandmarker != null

    private fun setupPoseLandmarker() {
        val baseOptionBuilder = BaseOptions.builder()

        when (config.delegateSelection) {
            WorkoutPoseConfig.DELEGATE_CPU -> baseOptionBuilder.setDelegate(Delegate.CPU)
            WorkoutPoseConfig.DELEGATE_GPU -> baseOptionBuilder.setDelegate(Delegate.GPU)
            else -> baseOptionBuilder.setDelegate(Delegate.CPU)
        }

        val modelName = modelAssetName(config.modelSelection)

        val modelAssetExists = runCatching {
            context.assets.open(modelName).use { }
            true
        }.getOrElse { false }
        if (!modelAssetExists) {
            val rootAssets = runCatching { context.assets.list("")?.joinToString(", ") ?: "<empty>" }
                .getOrElse { "<unavailable>" }
            Log.w(
                TAG,
                "setupPoseLandmarker: model asset '$modelName' missing (assets: $rootAssets); " +
                    "falling back to '${FALLBACK_MODEL_ASSET}'",
            )
        }

        val resolvedModelName = if (modelAssetExists) modelName else FALLBACK_MODEL_ASSET
        val fallbackExists = modelAssetExists || runCatching {
            context.assets.open(resolvedModelName).use { }
            true
        }.getOrElse { false }
        if (!fallbackExists) {
            Log.e(TAG, "setupPoseLandmarker: no usable model asset ('$resolvedModelName' missing too)")
            poseLandmarker = null
            return
        }
        Log.d(TAG, "setupPoseLandmarker: using '$resolvedModelName'")

        baseOptionBuilder.setModelAssetPath(resolvedModelName)

        try {
            val baseOptions = baseOptionBuilder.build()
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinPoseDetectionConfidence(DEFAULT_POSE_CONFIDENCE)
                .setMinTrackingConfidence(DEFAULT_POSE_CONFIDENCE)
                .setMinPosePresenceConfidence(DEFAULT_POSE_CONFIDENCE)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ -> onResult(result) }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            val delegateLabel = if (config.delegateSelection == WorkoutPoseConfig.DELEGATE_GPU) "GPU" else "CPU"
            Log.d(TAG, "setupPoseLandmarker: created (LIVE_STREAM, $delegateLabel)")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "setupPoseLandmarker: failed: ${e.message}", e)
        } catch (e: RuntimeException) {
            Log.e(TAG, "setupPoseLandmarker: failed (GPU?): ${e.message}", e)
        }
    }

    /**
     * Runs detection on a bitmap that is already in display (upright) orientation.
     *
     * Note: no [ImageProcessingOptions] rotation is used on purpose. Feed frames
     * pre-rotated to the upright orientation (see [WorkoutPoseManager]); the Tasks
     * rotation option here is unreliable for remapping the returned landmark
     * coordinates into the rotated frame.
     */
    fun detectAsync(bitmap: Bitmap, timestampMs: Long) {
        val landmarker = poseLandmarker
        if (landmarker == null) {
            Log.e(TAG, "detectAsync: poseLandmarker is null")
            return
        }
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            landmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "detectAsync: error: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "PoseLandmarkerHelper"
        private const val DEFAULT_POSE_CONFIDENCE = 0.5f
        private const val FALLBACK_MODEL_ASSET = "pose_landmarker_lite.task"

        fun modelAssetName(modelSelection: Int): String = when (modelSelection) {
            WorkoutPoseConfig.MODEL_POSE_LANDMARKER_FULL -> "pose_landmarker_full.task"
            WorkoutPoseConfig.MODEL_POSE_LANDMARKER_HEAVY -> "pose_landmarker_heavy.task"
            else -> "pose_landmarker_lite.task"
        }
    }
}