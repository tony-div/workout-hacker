package com.workoutpose.ghost

/** 3D point in normalized MediaPipe space (x right, y down). */
data class Point3D(val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0)

/** 33-point skeleton. */
data class Skeleton(val points: List<Point3D>)

/**
 * Ghost processing result.
 *
 * Mirrors upstream `ProcessResult` plus [deviation]: upstream has no
 * deviation field (the old app read `res.deviationScore`, which is
 * `undefined` at runtime) — here it is the real mean 3D distance over the
 * alignment key joints, so form scoring actually works.
 */
data class GhostProcessResult(
        val ghostSkeleton: Skeleton,
        val currentCheckpointIndex: Int,
        val isAligned: Boolean,
        val repCount: Int,
        val deviation: Double = 0.0,
)

/** Raw landmark with optional components (reference-frame JSON layout). */
data class RawLandmark(val x: Double? = null, val y: Double? = null, val z: Double? = null)

/** One reference frame: 33 raw landmarks. */
data class RawLandmarkFrame(val landmarks: List<RawLandmark>)

/** A processed reference frame. */
data class ReferenceFrame(val points: List<Point3D>)

/** Named alignment checkpoint inside a reference. */
data class Checkpoint(
        val name: String,
        val frameIndex: Int,
        val targetSkeleton: Skeleton,
)

/** Loaded reference routine for one exercise. */
data class ReferenceData(
        val exercise: String,
        val frames: List<ReferenceFrame>,
        val checkpoints: List<Checkpoint> = emptyList(),
        var currentFrameIndex: Int = 0,
        var currentCheckpointIdx: Int = 0,
        var repCount: Int = 0,
)

/** Options for reference-pose application. */
data class GhostPoseOptions(
        val applyReferencePose: Boolean = false,
        val frameIndex: Int = 0,
)

/** Bundled reference keys, one per JSON file under `assets/ghost-guide/`. */
object GhostExercises {
    const val BICEP_CURL = "bicep_curl"
    const val SHOULDER_PRESS = "shoulder_press"
    const val FRONT_RAISE = "front_raise"
    const val LATERAL_RAISE = "lateral_raise"
    const val TRICEPS_EXTENSION = "triceps_extension"

    val ALL = listOf(BICEP_CURL, SHOULDER_PRESS, FRONT_RAISE, LATERAL_RAISE, TRICEPS_EXTENSION)

    /** Maps classifier labels / workout names to bundled reference keys. */
    fun keyForExerciseName(name: String?): String? {
        val n = name?.lowercase() ?: return null
        return when {
            "bicep" in n -> BICEP_CURL
            "shoulder press" in n -> SHOULDER_PRESS
            "front raise" in n -> FRONT_RAISE
            "lateral raise" in n -> LATERAL_RAISE
            "tricep" in n -> TRICEPS_EXTENSION
            else -> null
        }
    }
}
