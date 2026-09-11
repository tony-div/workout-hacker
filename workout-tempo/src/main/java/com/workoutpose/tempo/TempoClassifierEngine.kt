package com.workoutpose.tempo

import android.util.Log
import com.workoutpose.randomforest.RandomForest
import org.json.JSONObject

/**
 * Tempo classifier ported from `react-native-tempo-classifier`'s
 * `TempoClassifierEngine.kt` (MIT, tony-div). Algorithm unchanged:
 * UP/DOWN phase transitions time full rep cycles, builds the 5-feature
 * vector [total_time_s, first_half_s, second_half_s, half_ratio,
 * exercise_encoded], and classifies fast/normal/slow through the shared
 * [RandomForest] runtime (model slot "tempo").
 *
 * Only the package and inference backend changed (this module's
 * [RandomForest] instead of the Nitro JNI bridge). Timing uses
 * `System.nanoTime()`; the `fps` argument is accepted for API parity and
 * ignored, exactly like upstream.
 */
class TempoClassifierEngine {
    private val TAG = "NitroTempoCls"
    private val MODEL_NAME = "tempo"

    private var modelLoaded = false
    private var exerciseClasses: List<String> = emptyList()
    private var phase: String = "UNKNOWN"
    private var phaseStart: Long? = null
    private var repStart: Long? = null
    private var midTime: Long? = null
    private var exercise: String? = null
    private var currentTempo: String = "unknown"
    private var currentQuality: Double = 0.0
    private var lastInferenceMs = -1.0

    fun loadModel(modelJson: String): Boolean {
        Log.d(TAG, "loadModel(): begin")
        val ok = RandomForest.loadModel(modelJson, MODEL_NAME)
        if (!ok) { Log.d(TAG, "loadModel(): failed"); return false }
        try {
            val json = JSONObject(modelJson)
            val arr = json.optJSONArray("exercise_classes")
            if (arr != null) exerciseClasses = (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {}
        modelLoaded = true
        currentTempo = "unknown"
        currentQuality = 0.0
        return true
    }

    fun setExercise(exerciseName: String?) {
        exercise = if (exerciseName.isNullOrEmpty()) null else exerciseName
    }

    fun update(phaseStr: String, fps: Double) {
        val now = System.nanoTime()
        val prev = phase
        val current = phaseStr

        if (current != prev) {
            if (prev == "UNKNOWN" && (current == "UP" || current == "DOWN")) {
                repStart = now; phaseStart = now; midTime = null
            } else if ((prev == "UP" || prev == "DOWN") && (current == "UP" || current == "DOWN")) {
                midTime = now; phaseStart = now
                val rs = repStart ?: return; val mt = midTime ?: return
                val totalS = (now - rs) / 1_000_000_000.0
                if (totalS > 0.3 && totalS < 15.0) {
                    val half1S = ((mt - rs) / 1_000_000_000.0).coerceAtLeast(0.05)
                    val half2S = (totalS - half1S).coerceAtLeast(0.05)
                    val halfRatio = half1S / half2S
                    val exEnc = if (exercise != null) exerciseClasses.indexOf(exercise).let { if (it >= 0) it else 0 } else 0
                    val flatData = doubleArrayOf(totalS, half1S, half2S, halfRatio, exEnc.toDouble())
                    val inferStart = System.nanoTime()
                    val rawProbs = RandomForest.predictProbabilities(flatData, 1, 5, MODEL_NAME)
                    lastInferenceMs = (System.nanoTime() - inferStart) / 1_000_000.0
                    if (rawProbs != null && rawProbs.isNotEmpty()) {
                        var bi = 0; var bv = Float.NEGATIVE_INFINITY
                        for (i in rawProbs.indices) { val v = rawProbs[i].toFloat(); if (v > bv) { bv = v; bi = i } }
                        currentTempo = when (bi) { 0 -> "fast"; 1 -> "normal"; 2 -> "slow"; else -> "unknown" }
                        currentQuality = (bv * 100.0).toDouble()
                    }
                }
                repStart = now
            }
        }
        phase = current
    }

    fun getCurrentTempo(): String = currentTempo
    fun getCurrentQuality(): Double = currentQuality
    /** Last RF inference time, ms; -1 = no classification yet. */
    fun getLastInferenceTimeMs(): Double = lastInferenceMs

    fun reset() {
        phase = "UNKNOWN"; phaseStart = null; repStart = null; midTime = null
        currentTempo = "unknown"; currentQuality = 0.0; lastInferenceMs = -1.0
    }
}
