package com.workoutpose.exercise

/**
 * Session tuning, mirroring the upstream `StartSessionConfig` Nitro spec.
 * Defaults are the engine's own (`ExerciseRecognitionEngine.SessionConfig`),
 * which are the source of truth — the old README lists older JS-side values.
 */
data class ExerciseConfig(
        /** Confidence to enter an exercise state. */
        val enterConfidence: Double = 0.65,
        /** Confidence below which an exit is counted (clamped <= enter). */
        val exitConfidence: Double = 0.45,
        /** Consecutive frames above [enterConfidence] needed to enter. */
        val enterFrames: Int = 3,
        /** Consecutive low-confidence frames needed to exit. */
        val exitFrames: Int = 8,
        /** EMA weight for probability smoothing (higher = more responsive). */
        val emaAlpha: Double = 0.2,
        /** Minimum visibility for a landmark to count as valid. */
        val minVisibility: Double = 0.2,
        /** Upper-body joints (of 11,12,13,14,15,16) that must meet [minVisibility]. */
        val minVisibleUpperBodyJoints: Int = 4,
        /** Sliding window for the null-state exit detector, seconds. */
        val nullExitWindowSeconds: Double = 5.0,
        /** Null-class confidence ratio required to force an exit. */
        val nullExitWindowThreshold: Double = 0.99,
) {
    companion object {
        val DEFAULT = ExerciseConfig()
    }
}

/** Latest classification result published on [ExerciseRecognition.prediction]. */
data class ExercisePrediction(
        /** e.g. "Bicep Curl", "Shoulder Press", "Null/Unknown"; null = no exercise. */
        val exercise: String?,
        val confidence: Double = 0.0,
        /** Last RF pipeline time, ms; -1 = no inference yet. */
        val inferenceMs: Double = -1.0,
) {
    companion object {
        fun empty() = ExercisePrediction(exercise = null)
    }
}
