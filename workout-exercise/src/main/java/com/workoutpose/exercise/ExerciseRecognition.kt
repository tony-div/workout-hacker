package com.workoutpose.exercise

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure consumer of pose landmark buffers.
 *
 * Mirrors the old `exerciseRecognition` Nitro hybrid object
 * (`HybridExerciseRecognition.kt`), minus Nitro: the host app owns the
 * camera/lifecycle and feeds flat 132-value landmark buffers (33 ×
 * x, y, z, visibility, same layout as `PoseFrame.landmarks`).
 *
 * Usage:
 * ```
 * val recognition = remember { ExerciseRecognition() }
 * val prediction by recognition.prediction.collectAsStateWithLifecycle()
 * recognition.loadModelFromAsset(context, "exercise_classifier_rf.json")
 * recognition.startSession(ExerciseConfig.DEFAULT)
 * // on each pose frame:
 * recognition.ingestLandmarksBuffer(poseFrame.landmarks.map { it.toDouble() }.toDoubleArray())
 * // when tearing down:
 * recognition.stopSession()
 * ```
 */
class ExerciseRecognition {

    private val engine = ExerciseRecognitionEngine()

    private val _prediction = MutableStateFlow(ExercisePrediction.empty())
    val prediction: StateFlow<ExercisePrediction> = _prediction.asStateFlow()

    val isModelLoaded: Boolean
        get() = modelLoaded

    @Volatile
    private var modelLoaded = false

    /**
     * Loads a model from its JSON string into the shared RF `"exercise"` slot.
     * Safe to call again to swap models.
     */
    fun loadModelFromJson(modelJson: String): Boolean {
        val ok = engine.loadModelFromJson(modelJson)
        if (ok) modelLoaded = true
        else Log.e(TAG, "loadModelFromJson: invalid model JSON")
        return ok
    }

    /**
     * Android-only asset loader (upstream `loadModelFromAsset` is Android-only
     * too; it returns `false` on iOS). Pass the file name only, e.g.
     * `"exercise_classifier_rf.json"`. Returns `false` when the asset is
     * missing — the old code failed silently here, this logs loudly instead.
     */
    fun loadModelFromAsset(context: Context, assetName: String): Boolean {
        val json = try {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "loadModelFromAsset($assetName): missing asset — ${e.message}")
            return false
        }
        return loadModelFromJson(json)
    }

    /** Starts (or restarts) classification with [config]; clears all state. */
    fun startSession(config: ExerciseConfig = ExerciseConfig.DEFAULT) {
        engine.startSession(
                enterConfidenceVal = config.enterConfidence,
                exitConfidenceVal = config.exitConfidence,
                enterFramesVal = config.enterFrames.toDouble(),
                exitFramesVal = config.exitFrames.toDouble(),
                emaAlphaVal = config.emaAlpha,
                minVisibilityVal = config.minVisibility,
                minVisibleUpperBodyJointsVal = config.minVisibleUpperBodyJoints.toDouble(),
                nullExitWindowSecondsVal = config.nullExitWindowSeconds,
                nullExitWindowThresholdVal = config.nullExitWindowThreshold,
        )
        _prediction.value = ExercisePrediction.empty()
    }

    /** Stops classification and clears the published prediction. */
    fun stopSession() {
        engine.stopSession()
        _prediction.value = ExercisePrediction.empty()
    }

    /**
     * Feeds one flat 132-value landmark buffer. Buffers shorter than
     * 33 × 4 are ignored; the engine needs 10 frames before its first
     * inference, like upstream.
     */
    fun ingestLandmarksBuffer(landmarks: DoubleArray) {
        if (landmarks.size < NUM_LANDMARKS * VALUES_PER_LANDMARK) return
        engine.ingestLandmarksBuffer(landmarks)
        _prediction.value = ExercisePrediction(
                exercise = engine.getCurrentExercise(),
                confidence = engine.getCurrentConfidence(),
                inferenceMs = engine.getLastClassifierInferenceTimeMs(),
        )
    }

    /** Float-buffer overload for direct `PoseFrame.landmarks` use. */
    fun ingestLandmarksBuffer(landmarks: FloatArray) {
        ingestLandmarksBuffer(DoubleArray(landmarks.size) { i -> landmarks[i].toDouble() })
    }

    fun getCurrentExercise(): String? = engine.getCurrentExercise()

    fun getCurrentConfidence(): Double = engine.getCurrentConfidence()

    fun getLastClassifierInferenceTimeMs(): Double = engine.getLastClassifierInferenceTimeMs()

    private companion object {
        const val TAG = "ExerciseRecognition"
        const val NUM_LANDMARKS = 33
        const val VALUES_PER_LANDMARK = 4
    }
}
