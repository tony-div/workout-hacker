package com.workoutpose.exercise

import android.util.Log
import com.workoutpose.randomforest.RandomForest
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * On-device exercise classifier ported from `react-native-exercise-recognition`'s
 * `ExerciseRecognitionEngine.kt` (MIT, tony-div). Algorithm unchanged:
 * hip-centered + shoulder-width normalized skeleton -> temporal features
 * (joint angles, velocity, acceleration, rolling std over 15-frame window) ->
 * shared [RandomForest] runtime (model slot "exercise") -> EMA smoothing ->
 * enter/exit frame state machine with null-window exit detector.
 *
 * The only differences from upstream are the package name and the inference
 * backend (this module's [RandomForest] instead of the Nitro JNI bridge).
 */
class ExerciseRecognitionEngine {
    companion object {
        /** Shared-RF registry slot, matching the upstream `"exercise"` name. */
        const val MODEL_NAME = "exercise"
    }

    private val TAG = "NitroExerciseRec"
    private val NUM_LANDMARKS = 33
    private val FEATURE_STD_WINDOW = 15
    private val ARM_JOINTS = intArrayOf(11, 12, 13, 14, 15, 16)

    private class SessionConfig {
        var enterConfidence = 0.65f
        var exitConfidence = 0.45f
        var enterFrames = 3
        var exitFrames = 8
        var emaAlpha = 0.2f
        var minVisibility = 0.2f
        var minVisibleUpperBodyJoints = 4
        var nullExitWindowSeconds = 5.0f
        var nullExitWindowThreshold = 0.99f
    }

    private var modelLoaded = false
    private var classIds: IntArray? = null
    private val config = SessionConfig()
    private val frames = ArrayDeque<List<Landmark>>()
    private var currentLabel: String? = null
    private var currentConfidence = 0.0
    private var lastInferenceMs = -1.0
    private var emaProbs: FloatArray? = null
    private var activeClassId: Int? = null
    private var pendingClassId: Int? = null
    private var pendingClassCount = 0
    private var lowConfidenceCount = 0
    private var nullProbsCount = 0
    private val frameTimes = ArrayDeque<Long>()

    fun loadModelFromJson(modelJson: String): Boolean {
        Log.d(TAG, "loadModelFromJson(): begin, bytes=${modelJson.length}")
        val ok = RandomForest.loadModel(modelJson, MODEL_NAME)
        if (ok) {
            classIds = RandomForest.getClassIds(MODEL_NAME)
            modelLoaded = true
            Log.d(TAG, "loadModelFromJson(): model loaded, classes=${classIds?.contentToString()}")
        }
        return ok
    }

    fun startSession(
        enterConfidenceVal: Double?,
        exitConfidenceVal: Double?,
        enterFramesVal: Double?,
        exitFramesVal: Double?,
        emaAlphaVal: Double?,
        minVisibilityVal: Double?,
        minVisibleUpperBodyJointsVal: Double?,
        nullExitWindowSecondsVal: Double?,
        nullExitWindowThresholdVal: Double?
    ) {
        config.enterConfidence = if (enterConfidenceVal != null && enterConfidenceVal > 0.0) enterConfidenceVal.toFloat() else 0.65f
        config.exitConfidence = if (exitConfidenceVal != null && exitConfidenceVal >= 0.0) exitConfidenceVal.toFloat() else 0.45f
        if (config.exitConfidence > config.enterConfidence) config.exitConfidence = config.enterConfidence
        config.enterFrames = if (enterFramesVal != null && enterFramesVal > 0) enterFramesVal.toInt() else 3
        config.exitFrames = if (exitFramesVal != null && exitFramesVal > 0) exitFramesVal.toInt() else 8
        config.emaAlpha = if (emaAlphaVal != null && emaAlphaVal in 0.0..1.0) emaAlphaVal.toFloat() else 0.2f
        config.minVisibility = if (minVisibilityVal != null && minVisibilityVal in 0.0..1.0) minVisibilityVal.toFloat() else 0.2f
        config.minVisibleUpperBodyJoints = if (minVisibleUpperBodyJointsVal != null && minVisibleUpperBodyJointsVal > 0) (minVisibleUpperBodyJointsVal.toInt()).coerceAtMost(6) else 4
        config.nullExitWindowSeconds = if (nullExitWindowSecondsVal != null && nullExitWindowSecondsVal > 0.0) nullExitWindowSecondsVal.toFloat() else 5.0f
        config.nullExitWindowThreshold = if (nullExitWindowThresholdVal != null && nullExitWindowThresholdVal in 0.0..1.0) nullExitWindowThresholdVal.toFloat() else 0.99f
        frames.clear()
        currentLabel = null
        currentConfidence = 0.0
        lastInferenceMs = -1.0
        nullProbsCount = 0
        frameTimes.clear()
        resetTemporalState()
    }

    fun stopSession() {
        frames.clear()
        currentLabel = null
        currentConfidence = 0.0
        lastInferenceMs = -1.0
        resetTemporalState()
    }

    fun ingestLandmarksBuffer(values: DoubleArray) {
        if (values.size < NUM_LANDMARKS * 4) return
        if (!modelLoaded) return

        val landmarks = ArrayList<Landmark>(NUM_LANDMARKS)
        for (i in 0 until NUM_LANDMARKS) {
            val base = i * 4
            landmarks.add(Landmark(values[base], values[base + 1], values[base + 2], values[base + 3]))
        }

        frames.addLast(landmarks)
        if (frames.size > 45) frames.removeFirst()
        if (frames.size < 10) return

        val inferStart = System.nanoTime()
        val framesSnapshot = frames.toList()
        val featureRows = extractFeatureRows(framesSnapshot)

        val flatData = DoubleArray(featureRows.size * featureRows[0].size)
        var idx = 0
        for (row in featureRows) {
                for (v in row) flatData[idx++] = v.toDouble()
        }

        val rawProbs = RandomForest.predictProbabilities(flatData, featureRows.size, featureRows[0].size, MODEL_NAME)
        if (rawProbs == null || rawProbs.isEmpty()) return

        val nClasses = classIds?.size ?: rawProbs.size
        val nRows = rawProbs.size / nClasses
        val frameProbs = Array(nRows) { r -> FloatArray(nClasses) { c -> rawProbs[r * nClasses + c].toFloat() } }
        if (frameProbs.isEmpty()) return

        val avgProbs = averageProbabilities(frameProbs)
        if (avgProbs.isEmpty()) return

        val smoothedProbs = if (emaProbs == null || emaProbs!!.size != avgProbs.size) avgProbs
        else blendWithEma(emaProbs!!, avgProbs, config.emaAlpha)
        emaProbs = smoothedProbs.copyOf()

        val winnerIdx = argmax(smoothedProbs)
        val winnerClassId = classIds?.getOrElse(winnerIdx) { winnerIdx } ?: winnerIdx
        val winnerConfidence = smoothedProbs.getOrElse(winnerIdx) { 0.0f }
        currentConfidence = winnerConfidence.toDouble()

        val lastFrame = framesSnapshot.lastOrNull()
        val visibleJointCount = if (lastFrame != null) upperBodyVisibleJointCount(lastFrame, config.minVisibility) else 0
        val qualityOk = visibleJointCount >= config.minVisibleUpperBodyJoints

        if (activeClassId != null) handleActiveState(smoothedProbs, winnerClassId, winnerConfidence, qualityOk)
        else handleInactiveState(winnerClassId, winnerConfidence, qualityOk)

        currentLabel = activeClassId?.let { labelForClassId(it) }
        lastInferenceMs = (System.nanoTime() - inferStart) / 1_000_000.0
    }

    fun getCurrentExercise(): String? = currentLabel
    fun getCurrentConfidence(): Double = currentConfidence
    fun getLastClassifierInferenceTimeMs(): Double = lastInferenceMs

    private fun handleActiveState(smoothedProbs: FloatArray, winnerClassId: Int, winnerConfidence: Float, qualityOk: Boolean) {
        val activeId = activeClassId!!
        val activeConfidence = probabilityForClassId(classIds ?: intArrayOf(), smoothedProbs, activeId)
        currentConfidence = activeConfidence.toDouble()

        if (winnerClassId == activeId) { pendingClassId = null; pendingClassCount = 0 }

        val isLowConfidence = !qualityOk || activeConfidence < config.exitConfidence
        if (isLowConfidence) lowConfidenceCount++ else lowConfidenceCount = 0

        if (lowConfidenceCount >= config.exitFrames) {
            activeClassId = null; pendingClassId = null; pendingClassCount = 0; lowConfidenceCount = 0
        }

        val now = System.nanoTime()
        frameTimes.addLast(now)
        val maxAgeNs = (config.nullExitWindowSeconds * 1_000_000_000).toLong()
        while (frameTimes.isNotEmpty() && (now - frameTimes.first()) > maxAgeNs) frameTimes.removeFirst()

        if (winnerClassId == 2 && winnerConfidence >= config.nullExitWindowThreshold) nullProbsCount++ else nullProbsCount = 0

        if (nullProbsCount > 0 && frameTimes.isNotEmpty()) {
            val ratio = nullProbsCount.toFloat() / frameTimes.size.toFloat()
            if (ratio >= config.nullExitWindowThreshold) {
                activeClassId = null; pendingClassId = null; pendingClassCount = 0; lowConfidenceCount = 0; nullProbsCount = 0
            }
        }
    }

    private fun handleInactiveState(winnerClassId: Int, winnerConfidence: Float, qualityOk: Boolean) {
        val canEnter = qualityOk && winnerClassId != 2 && winnerConfidence >= config.enterConfidence
        if (canEnter) {
            if (pendingClassId == winnerClassId) pendingClassCount++
            else { pendingClassId = winnerClassId; pendingClassCount = 1 }
            if (pendingClassCount >= config.enterFrames) {
                activeClassId = winnerClassId; pendingClassId = null; pendingClassCount = 0; lowConfidenceCount = 0
            }
        } else {
            pendingClassId = null; pendingClassCount = 0
        }
    }

    private fun resetTemporalState() {
        emaProbs = null; activeClassId = null; pendingClassId = null
        pendingClassCount = 0; lowConfidenceCount = 0
    }

    // ===== Feature Extraction =====

    private fun calculateAngle(a: DoubleArray, b: DoubleArray, c: DoubleArray): Double {
        val ba = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
        val bc = doubleArrayOf(c[0] - b[0], c[1] - b[1], c[2] - b[2])
        val dot = ba[0] * bc[0] + ba[1] * bc[1] + ba[2] * bc[2]
        val baNorm = sqrt(ba[0] * ba[0] + ba[1] * ba[1] + ba[2] * ba[2])
        val bcNorm = sqrt(bc[0] * bc[0] + bc[1] * bc[1] + bc[2] * bc[2])
        return Math.toDegrees(acos((dot / (baNorm * bcNorm + 1e-6)).coerceIn(-1.0, 1.0)))
    }

    private fun extractFeatureRows(frames: List<List<Landmark>>): List<FloatArray> {
        if (frames.isEmpty()) return emptyList()
        val arr = buildFrameArray(frames)
        normalizeSkeletonForClassifier(arr)
        return extractTemporalFeatures(arr)
    }

    private fun buildFrameArray(frames: List<List<Landmark>>): Array<Array<DoubleArray>> {
        return Array(frames.size) { i ->
            Array(NUM_LANDMARKS) { j ->
                val lm = if (j < frames[i].size) frames[i][j] else Landmark(0.0, 0.0, 0.0, 1.0)
                doubleArrayOf(lm.x, lm.y, lm.z, lm.visibility)
            }
        }
    }

    private fun normalizeSkeletonForClassifier(arr: Array<Array<DoubleArray>>) {
        for (frame in arr) {
            val leftHip = doubleArrayOf(frame[23][0], frame[23][1], frame[23][2])
            val rightHip = doubleArrayOf(frame[24][0], frame[24][1], frame[24][2])
            val hipCenter = doubleArrayOf(
                (leftHip[0] + rightHip[0]) / 2.0, (leftHip[1] + rightHip[1]) / 2.0, (leftHip[2] + rightHip[2]) / 2.0
            )
            for (lm in frame) { lm[0] -= hipCenter[0]; lm[1] -= hipCenter[1]; lm[2] -= hipCenter[2] }

            val ls = doubleArrayOf(frame[11][0], frame[11][1], frame[11][2])
            val rs = doubleArrayOf(frame[12][0], frame[12][1], frame[12][2])
            val sd = sqrt((ls[0] - rs[0]).pow(2) + (ls[1] - rs[1]).pow(2) + (ls[2] - rs[2]).pow(2))
            if (sd > 0.0) { for (lm in frame) { lm[0] /= sd; lm[1] /= sd; lm[2] /= sd } }
        }
    }

    private fun rollingStd(values: List<Double>, endIdx: Int, window: Int): Double {
        val start = (endIdx + 1 - window).coerceAtLeast(0)
        val slice = values.subList(start, endIdx + 1)
        val n = slice.size
        if (n <= 1) return 0.0
        val mean = slice.sum() / n
        return sqrt(slice.map { (it - mean).pow(2) }.sum() / (n - 1))
    }

    private fun extractTemporalFeatures(arr: Array<Array<DoubleArray>>): List<FloatArray> {
        val numFrames = arr.size
        val out = ArrayList<FloatArray>(numFrames)
        val coordsHist = Array(ARM_JOINTS.size) { Array(3) { ArrayList<Double>() } }
        val prevVel = Array(ARM_JOINTS.size) { DoubleArray(3) }

        for (i in 0 until numFrames) {
            val f = arr[i]
            val lsh = doubleArrayOf(f[11][0], f[11][1], f[11][2])
            val rsh = doubleArrayOf(f[12][0], f[12][1], f[12][2])
            val lel = doubleArrayOf(f[13][0], f[13][1], f[13][2])
            val rel = doubleArrayOf(f[14][0], f[14][1], f[14][2])
            val lwr = doubleArrayOf(f[15][0], f[15][1], f[15][2])
            val rwr = doubleArrayOf(f[16][0], f[16][1], f[16][2])
            val lhi = doubleArrayOf(f[23][0], f[23][1], f[23][2])
            val rhi = doubleArrayOf(f[24][0], f[24][1], f[24][2])

            val row = ArrayList<Float>()
            row.add(calculateAngle(lsh, lel, lwr).toFloat())
            row.add(calculateAngle(rsh, rel, rwr).toFloat())
            row.add(calculateAngle(lhi, lsh, lel).toFloat())
            row.add(calculateAngle(rhi, rsh, rel).toFloat())
            row.add((lwr[1] - lsh[1]).toFloat())
            row.add((rwr[1] - rsh[1]).toFloat())

            for ((ji, jx) in ARM_JOINTS.withIndex()) {
                val coord = doubleArrayOf(f[jx][0], f[jx][1], f[jx][2])
                for (ax in 0..2) coordsHist[ji][ax].add(coord[ax])

                val vel: DoubleArray
                val acc: DoubleArray
                if (i == 0) {
                    vel = doubleArrayOf(0.0, 0.0, 0.0)
                    acc = doubleArrayOf(0.0, 0.0, 0.0)
                } else {
                    val prev = arr[i - 1][jx]
                    vel = doubleArrayOf(coord[0] - prev[0], coord[1] - prev[1], coord[2] - prev[2])
                    acc = doubleArrayOf(vel[0] - prevVel[ji][0], vel[1] - prevVel[ji][1], vel[2] - prevVel[ji][2])
                }
                prevVel[ji] = vel
                row.add(vel[0].toFloat()); row.add(vel[1].toFloat()); row.add(vel[2].toFloat())
                row.add(acc[0].toFloat()); row.add(acc[1].toFloat()); row.add(acc[2].toFloat())
                row.add(rollingStd(coordsHist[ji][0], i, FEATURE_STD_WINDOW).toFloat())
                row.add(rollingStd(coordsHist[ji][1], i, FEATURE_STD_WINDOW).toFloat())
                row.add(rollingStd(coordsHist[ji][2], i, FEATURE_STD_WINDOW).toFloat())
            }
            out.add(row.toFloatArray())
        }
        return out
    }

    private fun upperBodyVisibleJointCount(landmarks: List<Landmark>, minVis: Float): Int {
        val joints = intArrayOf(11, 12, 13, 14, 15, 16)
        return joints.count { idx -> (landmarks.getOrNull(idx)?.visibility ?: 0.0).toFloat() >= minVis }
    }

    private fun labelForClassId(classId: Int): String = when (classId) {
        0 -> "Bicep Curl"; 1 -> "Lateral Raise"; 2 -> "Null/Unknown"
        3 -> "Shoulder Press"; 4 -> "Triceps Extension"; 5 -> "Front Raises"
        else -> "Unknown"
    }

    private fun argmax(values: FloatArray): Int {
        var bi = 0; var bv = Float.NEGATIVE_INFINITY
        for ((i, v) in values.withIndex()) { if (v > bv) { bv = v; bi = i } }
        return bi
    }

    private fun averageProbabilities(fp: Array<FloatArray>): FloatArray {
        if (fp.isEmpty()) return FloatArray(0)
        val nc = fp[0].size; val avg = FloatArray(nc)
        for (p in fp) for (i in 0 until nc.coerceAtMost(p.size)) avg[i] += p[i]
        val n = fp.size.toFloat(); if (n > 0f) for (i in avg.indices) avg[i] /= n
        return avg
    }

    private fun blendWithEma(prev: FloatArray, cur: FloatArray, alpha: Float): FloatArray {
        if (prev.size != cur.size) return cur.copyOf()
        return FloatArray(cur.size) { i -> alpha * cur[i] + (1f - alpha) * prev[i] }
    }

    private fun probabilityForClassId(ids: IntArray, probs: FloatArray, classId: Int): Float {
        val idx = ids.indexOf(classId); return if (idx in probs.indices) probs[idx] else 0.0f
    }
}
