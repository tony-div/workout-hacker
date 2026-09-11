package com.workouthacker.revamp.session

import android.content.Context
import android.util.Log
import com.workoutpose.exercise.ExerciseConfig
import com.workoutpose.exercise.ExercisePrediction
import com.workoutpose.exercise.ExerciseRecognition
import com.workoutpose.ghost.GhostExercises
import com.workoutpose.ghost.GhostProcessResult
import com.workoutpose.ghost.GhostGuide
import com.workoutpose.reps.RepCounter
import com.workoutpose.reps.RepCounterState
import com.workoutpose.tempo.TempoClassifier
import com.workoutpose.tempo.TempoState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Kotlin port of the old app's `useAiPipeline` — minus physical sensors.
 *
 * Owns the full pose-frame pipeline: exercise recognition → rep counter →
 * tempo classifier → ghost guide, in the same order and with the same
 * gating (UP/DOWN-only tempo updates, exercise-driven ghost reference) as
 * the RN hook. EMG/IMU/WiFi sensor feeds are intentionally absent
 * (out of scope for this revamp round), so fatigue + IMU form scoring do
 * not run here.
 *
 * Threading: [processFrame] is synchronous (Rust RF calls are short) and is
 * expected from a single collector (e.g. `LaunchedEffect(poseFrame)`), just
 * like the engines it drives.
 */
class WorkoutSessionOrchestrator(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** When true, emits a throttled (1 Hz) `TrackPerf` logcat line for benchmarks. */
    var benchmarkLogging: Boolean = true

    val recognition = ExerciseRecognition()
    val repCounter = RepCounter()
    val tempoClassifier = TempoClassifier()
    val exerciseModelStore = ExerciseModelStore(appContext)

    /** Per-exercise ghost engines, preloaded once the session starts. */
    private val ghostGuides = HashMap<String, GhostGuide>()

    val modelState: StateFlow<ExerciseModelState> = exerciseModelStore.state
    val exercisePrediction: StateFlow<ExercisePrediction> = recognition.prediction
    val repState: StateFlow<RepCounterState> = repCounter.state
    val tempoState: StateFlow<TempoState> = tempoClassifier.tempoState

    private val _ghostResult = MutableStateFlow<GhostProcessResult?>(null)
    val ghostResult: StateFlow<GhostProcessResult?> = _ghostResult.asStateFlow()

    private val _trackingReady = MutableStateFlow(false)
    val trackingReady: StateFlow<Boolean> = _trackingReady.asStateFlow()

    /** Set false to skip ghost reference loading + per-frame ghost math. */
    var ghostEnabled: Boolean = true

    private var ghostKey: String? = null
    private var lastTrackLogMs = 0L

    init {
        scope.launch {
            exerciseModelStore.state.collect { state ->
                val ready = state as? ExerciseModelState.Ready ?: return@collect
                onExerciseModelReady(ready)
            }
        }
    }

    /** Starts model provisioning: exercise-model download + bundled tempo load. */
    fun ensureModels() {
        exerciseModelStore.ensureModel()
        scope.launch {
            // Bundled asset (742 KB) — loads synchronously, no download needed.
            if (!tempoClassifier.loadModelFromAsset(appContext, "tempo_classifier.json")) {
                Log.e(TAG, "tempo model missing from assets")
            }
        }
    }

    /**
     * Feeds one flat 132-value landmark buffer through the whole pipeline.
     * Buffers of any other size are ignored.
     */
    fun processFrame(landmarks: FloatArray) {
        if (!_trackingReady.value) return
        if (landmarks.size != LANDMARK_COUNT * VALUES_PER_LANDMARK) return
        recognition.ingestLandmarksBuffer(landmarks)
        val exercise = recognition.getCurrentExercise()
        val rep = repCounter.update(landmarks, exercise)
        // Same gating as the old app: only UP/DOWN phases drive tempo.
        if (rep.phase == "UP" || rep.phase == "DOWN") {
            tempoClassifier.update(rep.phase)
        }
        if (exercise != null) tempoClassifier.setExercise(exercise)
        updateGhost(landmarks, exercise)
        logTrackPerf()
    }

    /** Stops all sessions and clears published state. */
    fun stopAll() {
        recognition.stopSession()
        repCounter.stopSession()
        tempoClassifier.reset()
        ghostKey = null
        _ghostResult.value = null
        _trackingReady.value = false
    }

    private fun onExerciseModelReady(ready: ExerciseModelState.Ready) {
        val json = try {
            ready.file.readText()
        } catch (e: Exception) {
            Log.e(TAG, "reading cached exercise model failed", e)
            return
        }
        if (!recognition.loadModelFromJson(json)) return
        // Same session tuning the old app used (enter 0.40 / exit 0.30 / 3 frames).
        recognition.startSession(
                ExerciseConfig(
                        enterConfidence = 0.40,
                        exitConfidence = 0.30,
                        enterFrames = 3,
                )
        )
        repCounter.startSession()
        _trackingReady.value = true
        // Preload ghost references off the main thread so the first
        // detection of each exercise never hitches the camera pipeline.
        scope.launch {
            for (key in GhostExercises.ALL) {
                if (!ghostEnabled) return@launch
                val guide = GhostGuide()
                if (guide.loadReferenceFromAsset(appContext, key)) {
                    ghostGuides[key] = guide
                }
            }
        }
    }

    /** Throttled per-stage snapshot for the benchmark harness (1 Hz max). */
    private fun logTrackPerf() {
        if (!benchmarkLogging) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTrackLogMs < 1000) return
        lastTrackLogMs = now
        val pred = recognition.prediction.value
        val rep = repCounter.state.value
        val tempo = tempoClassifier.tempoState.value
        val ghost = _ghostResult.value
        // Locale.US: logcat output is machine-parsed by benchmark/analyze_device.py.
        Log.d(
                "TrackPerf",
                "ts=$now " +
                        "ex=${pred.exercise ?: "-"} " +
                        "exConf=${"%.3f".format(java.util.Locale.US, pred.confidence)} " +
                        "exMs=${"%.2f".format(java.util.Locale.US, pred.inferenceMs)} " +
                        "reps=${"%.0f".format(java.util.Locale.US, rep.reps)} " +
                        "phase=${rep.phase} " +
                        "repUs=${"%.0f".format(java.util.Locale.US, rep.inferenceMs * 1000)} " +
                        "tempo=${tempo.tempo} " +
                        "tempoQ=${"%.0f".format(java.util.Locale.US, tempo.quality)} " +
                        "tempoMs=${"%.2f".format(java.util.Locale.US, tempo.inferenceMs)} " +
                        "aligned=${ghost?.isAligned ?: "-"}",
        )
    }

    private fun updateGhost(landmarks: FloatArray, exercise: String?) {
        if (!ghostEnabled) {
            if (ghostKey != null || _ghostResult.value != null) {
                ghostKey = null
                _ghostResult.value = null
            }
            return
        }
        // Ghost reference follows the detected exercise (old useAiPipeline mapping).
        val key = GhostExercises.keyForExerciseName(exercise)
        if (key == null) {
            ghostKey = null
            _ghostResult.value = null
            return
        }
        // Cache miss (preload still running) → skip this frame, keep last result.
        val guide = ghostGuides[key] ?: return
        ghostKey = key
        _ghostResult.value = guide.processLandmarksBuffer(landmarks)
    }

    private companion object {
        const val TAG = "SessionOrchestrator"
        const val LANDMARK_COUNT = 33
        const val VALUES_PER_LANDMARK = 4
    }
}
