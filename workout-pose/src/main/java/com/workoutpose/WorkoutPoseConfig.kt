package com.workoutpose

import androidx.camera.core.CameraSelector

/**
 * Configuration for the [WorkoutPoseManager].
 *
 * All options are validated/clamped by the engine at runtime.
 */
data class WorkoutPoseConfig(
    /** See [MODEL_POSE_LANDMARKER_FULL], [MODEL_POSE_LANDMARKER_LITE], [MODEL_POSE_LANDMARKER_HEAVY]. */
    val modelSelection: Int = MODEL_POSE_LANDMARKER_LITE,
    /** See [DELEGATE_CPU], [DELEGATE_GPU]. */
    val delegateSelection: Int = DELEGATE_CPU,
    /** Landmark visibility threshold in 0..1 used for filtering and pose-presence. */
    val minVisibilityConfidence: Float = 0.9f,
    /** Target inference rate in Hz (0..30). */
    val inferenceSampleRateHz: Float = 30f,
    /** Freeze low-confidence landmarks to their last-known-good position. */
    val enableVisibilityRecovery: Boolean = true,
    /** Velocity-adaptive (One Euro) smoothing of landmark positions. */
    val enableOneEuroFilter: Boolean = true,
    /** Linear extrapolation of the latest frame to the current wall-clock time. */
    val enableMotionPrediction: Boolean = false,
    /** One Euro minimum cutoff in Hz (0.1..10). Higher = less lag at rest, more jitter. */
    val oneEuroMinCutoff: Float = 2.0f,
    /** One Euro velocity response (0..0.1). Higher = keeps up with fast motion. */
    val oneEuroBeta: Float = 0.02f,
    /** One Euro derivative cutoff in Hz (0.1..20). Speeds up/slows jitter rejection. */
    val oneEuroDCutoff: Float = 1.0f,
    /** CameraX lens facing, one of [CameraSelector.LENS_FACING_FRONT] / [CameraSelector.LENS_FACING_BACK]. */
    val lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
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