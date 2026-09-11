package com.workoutpose

/**
 * The 8 workout-relevant joint angles in degrees, computed from the current
 * landmark buffer. A value of `-1f` means the joint could not be computed
 * (landmarks missing / degenerate geometry).
 */
data class JointAngles(
    val leftHip: Float = -1f,
    val rightHip: Float = -1f,
    val leftKnee: Float = -1f,
    val rightKnee: Float = -1f,
    val leftElbow: Float = -1f,
    val rightElbow: Float = -1f,
    val leftShoulder: Float = -1f,
    val rightShoulder: Float = -1f,
) {
    /** Fixed order: L hip, R hip, L knee, R knee, L elbow, R elbow, L shoulder, R shoulder. */
    val all: List<Float>
        get() = listOf(
            leftHip, rightHip, leftKnee, rightKnee,
            leftElbow, rightElbow, leftShoulder, rightShoulder,
        )
}

/**
 * A processed pose frame ready for rendering / analysis.
 *
 * [landmarks] is the flattened 132-value buffer (33 landmarks x [x, y, z, visibility]),
 * with x/y normalized to 0..1 in the MediaPipe image space (x right, y down).
 */
data class PoseFrame(
    val landmarks: FloatArray,
    val angles: JointAngles,
    val poseVisible: Boolean,
    val visibleLandmarkCount: Int,
    val inferenceTimeMs: Double,
    val timestampMs: Long,
) {
    companion object {
        fun empty() = PoseFrame(
            landmarks = FloatArray(0),
            angles = JointAngles(),
            poseVisible = false,
            visibleLandmarkCount = 0,
            inferenceTimeMs = -1.0,
            timestampMs = 0L,
        )
    }
}