package com.workoutpose

/**
 * Configuration for the [PoseAnalyzer].
 *
 * Camera concerns (lens facing, frame rate gating) are owned by the host app; this config only
 * covers pose processing parameters. All options are validated/clamped by the engine at runtime.
 */
data class WorkoutPoseConfig(
        /**
         * See [MODEL_POSE_LANDMARKER_FULL], [MODEL_POSE_LANDMARKER_LITE],
         * [MODEL_POSE_LANDMARKER_HEAVY].
         */
        val modelSelection: Int = MODEL_POSE_LANDMARKER_LITE,
        /** See [DELEGATE_CPU], [DELEGATE_GPU]. */
        val delegateSelection: Int = DELEGATE_CPU,
        /** Landmark visibility threshold in 0..1 used for filtering and pose-presence. */
        val minVisibilityConfidence: Float = 0.8f,
        /** Target inference rate in Hz (0..30); used by the host app to gate frame submission. */
        val inferenceSampleRateHz: Float = 30f,
        /** Freeze low-confidence landmarks to their last-known-good position. */
        val enableVisibilityRecovery: Boolean = true,
        /** Velocity-adaptive (One Euro) smoothing of landmark positions. */
        val enableOneEuroFilter: Boolean = true,
        /** Linear extrapolation of the latest frame to the current wall-clock time. */
        val enableMotionPrediction: Boolean = false,
        /** One Euro minimum cutoff in Hz (0.1..10). Higher = less lag at rest, more jitter. */
        val oneEuroMinCutoff: Float = 4.0f,
        /** One Euro velocity response (0..0.1). Higher = keeps up with fast motion. */
        val oneEuroBeta: Float = 0.06f,
        /** One Euro derivative cutoff in Hz (0.1..20). Speeds up/slows jitter rejection. */
        val oneEuroDCutoff: Float = 2.0f,
) {
    companion object {
        const val MODEL_POSE_LANDMARKER_FULL = 0
        const val MODEL_POSE_LANDMARKER_LITE = 1
        const val MODEL_POSE_LANDMARKER_HEAVY = 2

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1

        val DEFAULT = WorkoutPoseConfig()
    }
}
