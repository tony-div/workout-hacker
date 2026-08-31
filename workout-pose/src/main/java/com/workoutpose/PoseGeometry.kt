package com.workoutpose

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Pure geometry helpers over the flattened 132-value landmark buffer.
 */
object PoseGeometry {

    const val LANDMARK_COUNT = 33
    const val MIN_POSE_VISIBLE = 10

    /** MediaPipe pose connections. Indexes refer to the 33-landmark layout. */
    val POSE_CONNECTIONS: Array<IntArray> = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 7),
        intArrayOf(0, 4), intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(6, 8),
        intArrayOf(9, 10),
        intArrayOf(11, 12), intArrayOf(11, 13), intArrayOf(11, 23),
        intArrayOf(12, 14), intArrayOf(12, 24),
        intArrayOf(13, 15),
        intArrayOf(14, 16),
        intArrayOf(15, 17), intArrayOf(15, 19), intArrayOf(15, 21),
        intArrayOf(16, 18), intArrayOf(16, 20), intArrayOf(16, 22),
        intArrayOf(17, 19),
        intArrayOf(18, 20),
        intArrayOf(23, 24), intArrayOf(23, 25),
        intArrayOf(24, 26),
        intArrayOf(25, 27),
        intArrayOf(26, 28),
        intArrayOf(27, 29), intArrayOf(27, 31),
        intArrayOf(28, 30), intArrayOf(28, 32),
        intArrayOf(29, 31),
        intArrayOf(30, 32),
    )

    fun visibilityOf(buffer: FloatArray, index: Int): Float =
        if (index * 4 + 3 < buffer.size) buffer[index * 4 + 3] else 0f

    fun countVisible(buffer: FloatArray, minVisibility: Float): Int {
        if (buffer.size < LANDMARK_COUNT * 4) return 0
        var count = 0
        for (i in 0 until LANDMARK_COUNT) {
            if (visibilityOf(buffer, i) >= minVisibility) count++
        }
        return count
    }

    fun isPoseVisible(buffer: FloatArray, minVisibility: Float): Boolean =
        countVisible(buffer, minVisibility) >= MIN_POSE_VISIBLE

    /** Angle in degrees at vertex [b] between points [a]-[b]-[c]; -1f when degenerate. */
    fun angleDegrees(a: Int, b: Int, c: Int, buffer: FloatArray): Float {
        if (buffer.size < LANDMARK_COUNT * 4) return -1f
        val ax = buffer[a * 4]; val ay = buffer[a * 4 + 1]; val az = buffer[a * 4 + 2]
        val bx = buffer[b * 4]; val by = buffer[b * 4 + 1]; val bz = buffer[b * 4 + 2]
        val cx = buffer[c * 4]; val cy = buffer[c * 4 + 1]; val cz = buffer[c * 4 + 2]

        val v1x = ax - bx; val v1y = ay - by; val v1z = az - bz
        val v2x = cx - bx; val v2y = cy - by; val v2z = cz - bz

        val dot = v1x * v2x + v1y * v2y + v1z * v2z
        val mag = sqrt(v1x * v1x + v1y * v1y + v1z * v1z) * sqrt(v2x * v2x + v2y * v2y + v2z * v2z)
        if (mag <= 1e-6f) return -1f

        return Math.toDegrees(acos((dot / mag).coerceIn(-1f, 1f)).toDouble()).toFloat()
    }

    fun jointAngles(buffer: FloatArray): JointAngles = JointAngles(
        leftHip = angleDegrees(11, 23, 25, buffer),
        rightHip = angleDegrees(12, 24, 26, buffer),
        leftKnee = angleDegrees(23, 25, 27, buffer),
        rightKnee = angleDegrees(24, 26, 28, buffer),
        leftElbow = angleDegrees(11, 13, 15, buffer),
        rightElbow = angleDegrees(12, 14, 16, buffer),
        leftShoulder = angleDegrees(13, 11, 23, buffer),
        rightShoulder = angleDegrees(14, 12, 24, buffer),
    )
}