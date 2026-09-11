package com.workoutpose.tempo

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real-time rep-tempo classifier (fast/normal/slow).
 *
 * Mirrors the old `tempoClassifier` Nitro hybrid object
 * (`HybridTempoClassifier.kt`), minus Nitro. Feed it the rep [phase]
 * stream from [com.workoutpose.reps.RepCounter] and the current exercise
 * label from [com.workoutpose.exercise.ExerciseRecognition]:
 *
 * ```
 * val tempo = remember { TempoClassifier() }
 * tempo.loadModelFromJson(json)
 * // on each rep-counter update:
 * if (phase == "UP" || phase == "DOWN") tempo.update(phase)
 * tempo.setExercise(exercise)
 * ```
 */
class TempoClassifier {

    private val engine = TempoClassifierEngine()

    private val _tempoState = MutableStateFlow(TempoState())
    val tempoState: StateFlow<TempoState> = _tempoState.asStateFlow()

    @Volatile
    private var modelLoaded = false

    val isModelLoaded: Boolean
        get() = modelLoaded

    /** Loads a model from its JSON string into the shared RF `"tempo"` slot. */
    fun loadModelFromJson(modelJson: String): Boolean {
        val ok = engine.loadModel(modelJson)
        if (ok) modelLoaded = true
        else Log.e(TAG, "loadModelFromJson: invalid model JSON")
        return ok
    }

    /**
     * Android-only asset loader (upstream `loadModelFromAsset` is Android-only
     * too). Pass the file name only, e.g. `"tempo_classifier.json"`.
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

    /** Sets the current exercise (encoded as an RF feature); null clears it. */
    fun setExercise(exercise: String?) {
        engine.setExercise(exercise)
    }

    /**
     * Feeds one phase sample (`"UP"`, `"DOWN"`, `"UNKNOWN"`, case-insensitive).
     * [fps] is accepted for API parity and ignored (timing uses
     * `System.nanoTime()`), exactly like upstream. Publishes the new state.
     */
    fun update(phase: String, fps: Double = 30.0) {
        engine.update(phase, fps)
        _tempoState.value = TempoState(
                tempo = engine.getCurrentTempo(),
                quality = engine.getCurrentQuality(),
                inferenceMs = engine.getLastInferenceTimeMs(),
        )
    }

    fun getCurrentTempo(): String = engine.getCurrentTempo()

    fun getCurrentQuality(): Double = engine.getCurrentQuality()

    fun getLastInferenceTimeMs(): Double = engine.getLastInferenceTimeMs()

    /** Clears phase history, rep tracking and the published state. */
    fun reset() {
        engine.reset()
        _tempoState.value = TempoState()
    }

    private companion object {
        const val TAG = "TempoClassifier"
    }
}
