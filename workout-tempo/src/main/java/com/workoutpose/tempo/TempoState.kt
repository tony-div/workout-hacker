package com.workoutpose.tempo

/** Tempo labels, matching the upstream classifier outputs. */
object Tempo {
    const val UNKNOWN = "unknown"
    const val FAST = "fast"
    const val NORMAL = "normal"
    const val SLOW = "slow"
}

/** Latest tempo result published on [TempoClassifier.tempoState]. */
data class TempoState(
        val tempo: String = Tempo.UNKNOWN,
        /** Confidence percentage 0–100; 0 when [tempo] is unknown. */
        val quality: Double = 0.0,
        /** Last RF inference time, ms; -1 = no classification yet. */
        val inferenceMs: Double = -1.0,
)
