package com.workoutpose

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Stateful post-processing over raw MediaPipe results:
 *
 * 1. Visibility recovery - freezes low-confidence landmarks to their last-known-good position.
 * 2. One Euro filter - velocity-adaptive smoothing of jitter.
 * 3. Optional motion prediction - linear extrapolation to the current wall-clock time.
 *
 * Concurrency-safe via an internal lock; intended to be fed from MediaPipe's result thread.
 */
class WorkoutPoseEngine(initialConfig: WorkoutPoseConfig = WorkoutPoseConfig.DEFAULT) {

    private val stateLock = Any()

    var config: WorkoutPoseConfig = initialConfig
        set(value) {
            synchronized(stateLock) {
                field = value
                resetFilters()
            }
        }

    var lastInferenceTimeMs: Double = -1.0
        private set
    var lastFrameTimestampMs: Long = 0L
        private set

    private var oneEuroFilters: Array<OneEuroFilter>? = null
    private var previousFrame: LandmarkFrame? = null
    private var latestFrame: LandmarkFrame? = null
    private var latestGoodCoords: FloatArray? = null

    fun resetFilters() {
        synchronized(stateLock) {
            oneEuroFilters = null
            previousFrame = null
            latestFrame = null
            latestGoodCoords = null
        }
    }

    /**
     * Consumes a raw MediaPipe frame and returns the processed flat buffer
     * (33 landmarks x [x, y, z, visibility]).
     */
    fun feed(rawCoords: FloatArray, rawVisibility: FloatArray, timestampMs: Long, inferenceTimeMs: Double): FloatArray {
        synchronized(stateLock) {
            lastInferenceTimeMs = inferenceTimeMs
            lastFrameTimestampMs = timestampMs
            if (rawVisibility.isEmpty()) return FloatArray(0)

            ensureFilterCapacity(rawCoords.size)

            val coords = rawCoords.copyOf()
            val visibility = rawVisibility.copyOf()

            val lastGood = latestGoodCoords
            if (config.enableVisibilityRecovery && lastGood != null && lastGood.size == coords.size) {
                for (index in visibility.indices) {
                    if (visibility[index] < config.minVisibilityConfidence) {
                        coords[index * 3] = lastGood[index * 3]
                        coords[index * 3 + 1] = lastGood[index * 3 + 1]
                        coords[index * 3 + 2] = lastGood[index * 3 + 2]
                    }
                }
            }

            val filteredCoords = if (config.enableOneEuroFilter) {
                applyOneEuroFilters(coords, timestampMs)
            } else {
                coords
            }

            val frame = LandmarkFrame(timestampMs = timestampMs, coords = filteredCoords, visibility = visibility)
            previousFrame = latestFrame
            latestFrame = frame
            latestGoodCoords = filteredCoords.copyOf()

            val outputCoords = if (config.enableMotionPrediction) predict(frame, previousFrame) else frame.coords
            return flattenFrame(outputCoords, frame.visibility)
        }
    }

    private fun predict(latest: LandmarkFrame, previous: LandmarkFrame?): FloatArray {
        if (previous == null) return latest.coords

        val now = System.currentTimeMillis()
        val elapsed = (now - latest.timestampMs).coerceAtLeast(0L).toFloat()
        if (elapsed < 8f) return latest.coords

        val baseDelta = (latest.timestampMs - previous.timestampMs).coerceAtLeast(1L).toFloat()
        val predictionDelta = min(elapsed, 66f)

        var totalVelocity = 0f
        for (i in latest.coords.indices) {
            totalVelocity += abs((latest.coords[i] - previous.coords[i]) / baseDelta)
        }
        val avgVelocity = totalVelocity / latest.coords.size
        if (avgVelocity < MOTION_THRESHOLD) return latest.coords

        val predicted = FloatArray(latest.coords.size)
        for (i in latest.coords.indices) {
            val velocity = (latest.coords[i] - previous.coords[i]) / baseDelta
            predicted[i] = latest.coords[i] + velocity * predictionDelta
        }
        return predicted
    }

    private fun ensureFilterCapacity(coordCount: Int) {
        val filters = oneEuroFilters
        if (filters == null || filters.size != coordCount) {
            oneEuroFilters = Array(coordCount) { OneEuroFilter() }
        }
        (filters ?: oneEuroFilters!!).forEach {
            it.configure(config.oneEuroMinCutoff, config.oneEuroBeta)
        }
    }

    private fun applyOneEuroFilters(coords: FloatArray, timestampMs: Long): FloatArray {
        val filters = oneEuroFilters ?: return coords
        val filtered = FloatArray(coords.size)
        for (i in coords.indices) {
            filtered[i] = filters[i].filter(coords[i], timestampMs / 1000f)
        }
        return filtered
    }

    private fun flattenFrame(coords: FloatArray, visibility: FloatArray): FloatArray {
        val landmarkCount = visibility.size
        val buffer = FloatArray(landmarkCount * 4)
        for (i in 0 until landmarkCount) {
            buffer[i * 4] = coords[i * 3]
            buffer[i * 4 + 1] = coords[i * 3 + 1]
            buffer[i * 4 + 2] = coords[i * 3 + 2]
            buffer[i * 4 + 3] = visibility[i]
        }
        return buffer
    }

    private data class LandmarkFrame(
        val timestampMs: Long,
        val coords: FloatArray,
        val visibility: FloatArray,
    )

    private class OneEuroFilter {
        private var minCutoff: Float = 0.4f
        private var beta: Float = 0.007f
        private val dCutoff: Float = 1.0f
        private var initialized = false
        private var previousTimestampSec = 0f
        private var previousValue = 0f
        private var previousDerivative = 0f

        fun configure(minCutoff: Float, beta: Float) {
            this.minCutoff = minCutoff
            this.beta = beta
        }

        fun filter(value: Float, timestampSec: Float): Float {
            if (!initialized) {
                initialized = true
                previousTimestampSec = timestampSec
                previousValue = value
                previousDerivative = 0f
                return value
            }

            val dt = max(1e-3f, timestampSec - previousTimestampSec)
            val frequency = 1f / dt

            val derivative = (value - previousValue) * frequency
            val alphaD = alpha(frequency, dCutoff)
            val smoothedDerivative = lowPass(alphaD, derivative, previousDerivative)

            val cutoff = minCutoff + beta * abs(smoothedDerivative)
            val alpha = alpha(frequency, cutoff)
            val smoothedValue = lowPass(alpha, value, previousValue)

            previousTimestampSec = timestampSec
            previousDerivative = smoothedDerivative
            previousValue = smoothedValue
            return smoothedValue
        }

        private fun alpha(frequency: Float, cutoff: Float): Float {
            val te = 1f / max(frequency, 1e-6f)
            val tau = 1f / (2f * PI.toFloat() * cutoff)
            return 1f / (1f + tau / te)
        }

        private fun lowPass(alpha: Float, value: Float, previous: Float): Float =
            alpha * value + (1f - alpha) * previous
    }

    private companion object {
        const val MOTION_THRESHOLD = 0.0005f
    }
}