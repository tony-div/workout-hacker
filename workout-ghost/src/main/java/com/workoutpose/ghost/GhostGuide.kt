package com.workoutpose.ghost

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ghost skeleton engine ported from `react-native-ghost-guide`'s
 * `src/index.ts` (MIT, tony-div) — pure TypeScript upstream, so this is a
 * straight TS→Kotlin translation with identical math:
 *
 * - [scaleToUser]: shoulder/hip/torso-scaled alignment with 0.6-strength
 *   shoulder-axis rotation, yaw correction, and per-arm length rescaling.
 * - [buildGhostSkeleton]: shoulder + elbow angle transfer from the reference
 *   frame onto the user's skeleton (2D signed-angle deltas).
 * - [checkAlignment]: mean 3D distance over key joints [11,13,15,23,25,27]
 *   below 0.1.
 *
 * One deliberate addition: [GhostProcessResult.deviation] (mean alignment
 * distance). Upstream has no deviation output — the old app read
 * `res.deviationScore` (`undefined` at runtime, so its vision form score was
 * `NaN`). Here deviation is real, and form scoring works.
 *
 * Reference state is per-instance (upstream used a module global).
 */
class GhostGuide {

    private var referenceData: ReferenceData? = null

    // ── reference management ──────────────────────────────

    fun createReferenceFromFrames(
            frames: List<RawLandmarkFrame>,
            exercise: String,
            checkpoints: List<Checkpoint> = emptyList(),
    ): ReferenceData = ReferenceData(
            exercise = exercise,
            frames = frames.map { frame ->
                ReferenceFrame(
                        points = frame.landmarks.take(LANDMARK_COUNT).map { lm ->
                            Point3D(lm.x ?: 0.0, lm.y ?: 0.0, lm.z ?: 0.0)
                        },
                )
            },
            checkpoints = checkpoints,
    )

    fun loadReference(reference: ReferenceData) {
        referenceData = reference.copy(
                currentFrameIndex = 0,
                currentCheckpointIdx = 0,
                repCount = 0,
        )
    }

    /**
     * Loads a bundled `assets/ghost-guide/<key>_frames.json` reference.
     * Returns false when the asset is missing or unparsable.
     */
    fun loadReferenceFromAsset(context: Context, exerciseKey: String): Boolean {
        val json = try {
            context.assets.open("ghost-guide/${exerciseKey}_frames.json")
                    .bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "loadReferenceFromAsset($exerciseKey): missing asset — ${e.message}")
            return false
        }
        return try {
            loadReference(createReferenceFromFrames(parseRawFrames(json), exerciseKey))
            true
        } catch (e: Exception) {
            Log.e(TAG, "loadReferenceFromAsset($exerciseKey): parse failed — ${e.message}")
            false
        }
    }

    fun setReferenceFrameIndex(index: Int) {
        val ref = referenceData ?: return
        if (ref.frames.isEmpty()) return
        ref.currentFrameIndex = index.coerceIn(0, ref.frames.size - 1)
    }

    val frameCount: Int
        get() = referenceData?.frames?.size ?: 0

    // ── per-frame processing ──────────────────────────────

    fun processFrame(userSkeleton: Skeleton): GhostProcessResult {
        val ref = referenceData
        if (ref == null || ref.frames.isEmpty()) {
            return GhostProcessResult(
                    ghostSkeleton = userSkeleton,
                    currentCheckpointIndex = 0,
                    isAligned = false,
                    repCount = ref?.repCount ?: 0,
                    deviation = 0.0,
            )
        }
        val frameIndex = ref.currentFrameIndex
        val reference = ref.frames.getOrElse(frameIndex) { ref.frames[0] }
        val scaledGhost = scaleToUser(userSkeleton, Skeleton(reference.points))
        val deviation = meanKeyJointDistance(userSkeleton, scaledGhost)
        return GhostProcessResult(
                ghostSkeleton = scaledGhost,
                currentCheckpointIndex = ref.currentCheckpointIdx,
                isAligned = deviation < ALIGNMENT_THRESHOLD,
                repCount = ref.repCount,
                deviation = deviation,
        )
    }

    fun processFrameWithReference(
            userSkeleton: Skeleton,
            referenceFrames: List<RawLandmarkFrame>,
            options: GhostPoseOptions = GhostPoseOptions(),
    ): GhostProcessResult {
        val result = processFrame(userSkeleton)
        return result.copy(
                ghostSkeleton = buildGhostSkeleton(userSkeleton, referenceFrames, options),
        )
    }

    fun buildGhostSkeleton(
            userSkeleton: Skeleton,
            referenceFrames: List<RawLandmarkFrame>,
            options: GhostPoseOptions = GhostPoseOptions(),
    ): Skeleton {
        val ghostPoints = userSkeleton.points.map { it.copy() }.toMutableList()
        if (!options.applyReferencePose) return Skeleton(ghostPoints)
        val frame = referenceFrames.getOrNull(options.frameIndex) ?: return Skeleton(ghostPoints)
        val referencePoints = landmarksToPoints(frame, LANDMARK_COUNT)

        applyShoulderAngle(ghostPoints, referencePoints, 23, 11, 13, 15)
        applyElbowAngle(ghostPoints, referencePoints, 11, 13, 15)
        applyShoulderAngle(ghostPoints, referencePoints, 24, 12, 14, 16)
        applyElbowAngle(ghostPoints, referencePoints, 12, 14, 16)
        return Skeleton(ghostPoints)
    }

    // ── buffer API (PoseFrame-landmarks entry points) ─────

    fun skeletonFromLandmarksBuffer(buffer: FloatArray, landmarkCount: Int = 33): Skeleton? {
        if (buffer.size != landmarkCount * 4) return null
        return Skeleton(
                List(landmarkCount) { i ->
                    Point3D(
                            x = buffer[i * 4].toDouble(),
                            y = buffer[i * 4 + 1].toDouble(),
                            z = buffer[i * 4 + 2].toDouble(),
                    )
                },
        )
    }

    fun processLandmarksBuffer(buffer: FloatArray, landmarkCount: Int = 33): GhostProcessResult? {
        val skeleton = skeletonFromLandmarksBuffer(buffer, landmarkCount) ?: return null
        return processFrame(skeleton)
    }

    fun processLandmarksBufferWithReference(
            buffer: FloatArray,
            referenceFrames: List<RawLandmarkFrame>,
            options: GhostPoseOptions = GhostPoseOptions(),
            landmarkCount: Int = 33,
    ): GhostProcessResult? {
        val skeleton = skeletonFromLandmarksBuffer(buffer, landmarkCount) ?: return null
        return processFrameWithReference(skeleton, referenceFrames, options)
    }

    // ── math (verbatim port) ──────────────────────────────

    private fun ensurePoint(points: List<Point3D>, index: Int): Point3D =
            points.getOrElse(index) { Point3D() }

    private fun dist2(a: Point3D, b: Point3D): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun dist3(a: Point3D, b: Point3D): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun normalize3(v: Point3D): Point3D {
        val len = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
        if (len < 0.000001) return Point3D()
        return Point3D(v.x / len, v.y / len, v.z / len)
    }

    private fun cross3(a: Point3D, b: Point3D): Point3D = Point3D(
            x = a.y * b.z - a.z * b.y,
            y = a.z * b.x - a.x * b.z,
            z = a.x * b.y - a.y * b.x,
    )

    private fun dot3(a: Point3D, b: Point3D): Double = a.x * b.x + a.y * b.y + a.z * b.z

    private fun rotate3D(origin: Point3D, point: Point3D, axis: Point3D, angle: Double): Point3D {
        val k = normalize3(axis)
        if (k.x == 0.0 && k.y == 0.0 && k.z == 0.0) return point
        val cosA = cos(angle)
        val sinA = sin(angle)
        val vx = point.x - origin.x
        val vy = point.y - origin.y
        val vz = point.z - origin.z
        val kv = dot3(k, Point3D(vx, vy, vz))
        val kCrossV = cross3(k, Point3D(vx, vy, vz))
        return Point3D(
                x = origin.x + vx * cosA + kCrossV.x * sinA + k.x * kv * (1 - cosA),
                y = origin.y + vy * cosA + kCrossV.y * sinA + k.y * kv * (1 - cosA),
                z = origin.z + vz * cosA + kCrossV.z * sinA + k.z * kv * (1 - cosA),
        )
    }

    private fun normalizeAngle(angle: Double): Double {
        val twoPi = PI * 2
        var a = angle % twoPi
        if (a > PI) a -= twoPi
        if (a < -PI) a += twoPi
        return a
    }

    internal fun scaleToUser(user: Skeleton, reference: Skeleton): Skeleton {
        var scaled = reference.points.map { it.copy() }.toMutableList()

        val userLeft = ensurePoint(user.points, 11)
        val userRight = ensurePoint(user.points, 12)
        val ghostLeft = ensurePoint(scaled, 11)
        val ghostRight = ensurePoint(scaled, 12)

        val userHipLeft = ensurePoint(user.points, 23)
        val userHipRight = ensurePoint(user.points, 24)
        val ghostHipLeft = ensurePoint(scaled, 23)
        val ghostHipRight = ensurePoint(scaled, 24)

        val userShoulderWidth = dist2(userLeft, userRight)
        val ghostShoulderWidth = max(dist2(ghostLeft, ghostRight), 0.001)
        val userHipWidth = dist2(userHipLeft, userHipRight)
        val ghostHipWidth = max(dist2(ghostHipLeft, ghostHipRight), 0.001)

        val userTorso = (dist3(userLeft, userHipLeft) + dist3(userRight, userHipRight)) * 0.5
        val ghostTorso = max(
                (dist3(ghostLeft, ghostHipLeft) + dist3(ghostRight, ghostHipRight)) * 0.5,
                0.001,
        )

        val shoulderScale = userShoulderWidth / ghostShoulderWidth
        val hipScale = userHipWidth / ghostHipWidth
        val heightScale = userTorso / ghostTorso

        val userCenter = Point3D(
                (userLeft.x + userRight.x) * 0.5,
                (userLeft.y + userRight.y) * 0.5,
                (userLeft.z + userRight.z) * 0.5,
        )
        val userHipCenter = Point3D(
                (userHipLeft.x + userHipRight.x) * 0.5,
                (userHipLeft.y + userHipRight.y) * 0.5,
                (userHipLeft.z + userHipRight.z) * 0.5,
        )
        val ghostCenter = Point3D(
                (ghostLeft.x + ghostRight.x) * 0.5,
                (ghostLeft.y + ghostRight.y) * 0.5,
                (ghostLeft.z + ghostRight.z) * 0.5,
        )
        val ghostHipCenter = Point3D(
                (ghostHipLeft.x + ghostHipRight.x) * 0.5,
                (ghostHipLeft.y + ghostHipRight.y) * 0.5,
                (ghostHipLeft.z + ghostHipRight.z) * 0.5,
        )
        val userTorsoCenter = Point3D(
                (userCenter.x + userHipCenter.x) * 0.5,
                (userCenter.y + userHipCenter.y) * 0.5,
                (userCenter.z + userHipCenter.z) * 0.5,
        )
        val ghostTorsoCenter = Point3D(
                (ghostCenter.x + ghostHipCenter.x) * 0.5,
                (ghostCenter.y + ghostHipCenter.y) * 0.5,
                (ghostCenter.z + ghostHipCenter.z) * 0.5,
        )

        val userVec = normalize3(Point3D(userRight.x - userLeft.x, userRight.y - userLeft.y, userRight.z - userLeft.z))
        val ghostVec = normalize3(Point3D(ghostRight.x - ghostLeft.x, ghostRight.y - ghostLeft.y, ghostRight.z - ghostLeft.z))

        val axis = cross3(ghostVec, userVec)
        val dot = dot3(ghostVec, userVec).coerceIn(-1.0, 1.0)
        val rotation = acos(dot) * ROTATION_STRENGTH
        scaled = scaled.map { point -> rotate3D(ghostTorsoCenter, point, axis, rotation) }.toMutableList()

        val userYaw = atan2(userVec.z, userVec.x)
        val ghostYaw = atan2(ghostVec.z, ghostVec.x)
        val yawDelta = normalizeAngle(userYaw - ghostYaw)
        val yawAmount = abs(userYaw)
        val yawStrength = if (yawAmount <= YAW_THRESHOLD) 0.0
        else min(1.0, (yawAmount - YAW_THRESHOLD) / (PI / 2 - YAW_THRESHOLD))

        if (yawStrength > 0) {
            val yawAxis = Point3D(0.0, 1.0, 0.0)
            scaled = scaled.map { point -> rotate3D(ghostTorsoCenter, point, yawAxis, yawDelta * yawStrength) }.toMutableList()
        }

        val shoulderY = ghostCenter.y
        val hipY = ghostHipCenter.y
        val denom = max(abs(hipY - shoulderY), 0.000001)

        scaled = scaled.map { point ->
            val t = (abs(point.y - shoulderY) / denom).coerceIn(0.0, 1.0)
            val widthScale = shoulderScale + (hipScale - shoulderScale) * t
            val dx = point.x - ghostTorsoCenter.x
            val dy = point.y - ghostTorsoCenter.y
            val dz = point.z - ghostTorsoCenter.z
            Point3D(
                    x = userTorsoCenter.x + dx * widthScale,
                    y = userTorsoCenter.y + dy * heightScale,
                    z = userTorsoCenter.z + dz * widthScale,
            )
        }.toMutableList()

        val userLeftArm = dist2(ensurePoint(user.points, 11), ensurePoint(user.points, 13)) +
                dist2(ensurePoint(user.points, 13), ensurePoint(user.points, 15))
        val userRightArm = dist2(ensurePoint(user.points, 12), ensurePoint(user.points, 14)) +
                dist2(ensurePoint(user.points, 14), ensurePoint(user.points, 16))
        val ghostLeftArm = dist2(ensurePoint(scaled, 11), ensurePoint(scaled, 13)) +
                dist2(ensurePoint(scaled, 13), ensurePoint(scaled, 15))
        val ghostRightArm = dist2(ensurePoint(scaled, 12), ensurePoint(scaled, 14)) +
                dist2(ensurePoint(scaled, 14), ensurePoint(scaled, 16))

        val leftArmScale = if (ghostLeftArm > 0.001) userLeftArm / ghostLeftArm else 1.0
        val rightArmScale = if (ghostRightArm > 0.001) userRightArm / ghostRightArm else 1.0

        fun scaleFrom(origin: Point3D, point: Point3D, s: Double): Point3D = Point3D(
                x = origin.x + (point.x - origin.x) * s,
                y = origin.y + (point.y - origin.y) * s,
                z = origin.z + (point.z - origin.z) * s,
        )

        val leftShoulder = ensurePoint(scaled, 11)
        val rightShoulder = ensurePoint(scaled, 12)
        scaled[13] = scaleFrom(leftShoulder, ensurePoint(scaled, 13), leftArmScale)
        scaled[15] = scaleFrom(leftShoulder, ensurePoint(scaled, 15), leftArmScale)
        scaled[14] = scaleFrom(rightShoulder, ensurePoint(scaled, 14), rightArmScale)
        scaled[16] = scaleFrom(rightShoulder, ensurePoint(scaled, 16), rightArmScale)

        return Skeleton(scaled)
    }

    private fun meanKeyJointDistance(user: Skeleton, ghost: Skeleton): Double {
        var total = 0.0
        for (idx in KEY_JOINTS) {
            total += dist3(ensurePoint(user.points, idx), ensurePoint(ghost.points, idx))
        }
        return total / KEY_JOINTS.size
    }

    private fun landmarksToPoints(frame: RawLandmarkFrame, count: Int = LANDMARK_COUNT): List<Point3D> =
            frame.landmarks.take(count).map { lm ->
                Point3D(lm.x ?: 0.0, lm.y ?: 0.0, lm.z ?: 0.0)
            }

    private fun angleBetweenSigned(a: Point3D, b: Point3D): Double {
        val cross = a.x * b.y - a.y * b.x
        val dot = a.x * b.x + a.y * b.y
        return atan2(cross, dot)
    }

    private fun rotate2D(v: Point3D, angle: Double): Point3D {
        val cosA = cos(angle)
        val sinA = sin(angle)
        return Point3D(
                x = v.x * cosA - v.y * sinA,
                y = v.x * sinA + v.y * cosA,
                z = v.z,
        )
    }

    private fun applyShoulderAngle(
            ghostPoints: MutableList<Point3D>,
            referencePoints: List<Point3D>,
            hipIndex: Int,
            shoulderIndex: Int,
            elbowIndex: Int,
            wristIndex: Int,
    ) {
        val gHip = ensurePoint(ghostPoints, hipIndex)
        val gShoulder = ensurePoint(ghostPoints, shoulderIndex)
        val gElbow = ensurePoint(ghostPoints, elbowIndex)
        val gWrist = ensurePoint(ghostPoints, wristIndex)

        val rHip = ensurePoint(referencePoints, hipIndex)
        val rShoulder = ensurePoint(referencePoints, shoulderIndex)
        val rElbow = ensurePoint(referencePoints, elbowIndex)

        val rTorso = Point3D(rHip.x - rShoulder.x, rHip.y - rShoulder.y, 0.0)
        val rUpper = Point3D(rElbow.x - rShoulder.x, rElbow.y - rShoulder.y, 0.0)
        val desiredAngle = angleBetweenSigned(rTorso, rUpper)

        val gTorso = Point3D(gHip.x - gShoulder.x, gHip.y - gShoulder.y, 0.0)
        val gUpper = Point3D(gElbow.x - gShoulder.x, gElbow.y - gShoulder.y, 0.0)
        val currentAngle = angleBetweenSigned(gTorso, gUpper)

        val delta = desiredAngle - currentAngle
        val upperArm = Point3D(gElbow.x - gShoulder.x, gElbow.y - gShoulder.y, gElbow.z - gShoulder.z)
        val rotatedUpper = rotate2D(upperArm, delta)

        val newElbow = Point3D(
                gShoulder.x + rotatedUpper.x,
                gShoulder.y + rotatedUpper.y,
                gShoulder.z + rotatedUpper.z,
        )
        ghostPoints[elbowIndex] = newElbow

        val forearm = Point3D(gWrist.x - gElbow.x, gWrist.y - gElbow.y, gWrist.z - gElbow.z)
        val rotatedFore = rotate2D(forearm, delta)
        ghostPoints[wristIndex] = Point3D(
                newElbow.x + rotatedFore.x,
                newElbow.y + rotatedFore.y,
                newElbow.z + rotatedFore.z,
        )
    }

    private fun applyElbowAngle(
            ghostPoints: MutableList<Point3D>,
            referencePoints: List<Point3D>,
            shoulderIndex: Int,
            elbowIndex: Int,
            wristIndex: Int,
    ) {
        val gShoulder = ghostPoints.getOrNull(shoulderIndex) ?: return
        val gElbow = ghostPoints.getOrNull(elbowIndex) ?: return
        val gWrist = ghostPoints.getOrNull(wristIndex) ?: return
        val rShoulder = referencePoints.getOrNull(shoulderIndex) ?: return
        val rElbow = referencePoints.getOrNull(elbowIndex) ?: return
        val rWrist = referencePoints.getOrNull(wristIndex) ?: return

        val gUpper = Point3D(gShoulder.x - gElbow.x, gShoulder.y - gElbow.y, 0.0)
        val gFore = Point3D(gWrist.x - gElbow.x, gWrist.y - gElbow.y, 0.0)
        val rUpper = Point3D(rShoulder.x - rElbow.x, rShoulder.y - rElbow.y, 0.0)
        val rFore = Point3D(rWrist.x - rElbow.x, rWrist.y - rElbow.y, 0.0)

        val currentAngle = angleBetweenSigned(gUpper, gFore)
        val desiredAngle = angleBetweenSigned(rUpper, rFore)
        val rotatedFore = rotate2D(gFore, desiredAngle - currentAngle)
        ghostPoints[wristIndex] = Point3D(
                gElbow.x + rotatedFore.x,
                gElbow.y + rotatedFore.y,
                gWrist.z,
        )
    }

    // ── reference-frame JSON parsing ──────────────────────

    private fun parseRawFrames(json: String): List<RawLandmarkFrame> {
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val frame = arr.getJSONObject(i)
            val landmarks = frame.getJSONArray("landmarks")
            RawLandmarkFrame(
                    landmarks = List(landmarks.length()) { j ->
                        val lm = landmarks.getJSONObject(j)
                        RawLandmark(
                                x = lm.optDouble("x").takeIf { !it.isNaN() },
                                y = lm.optDouble("y").takeIf { !it.isNaN() },
                                z = lm.optDouble("z").takeIf { !it.isNaN() },
                        )
                    },
            )
        }
    }

    companion object {
        const val TAG = "GhostGuide"
        const val LANDMARK_COUNT = 33
        /** Alignment gate, matching upstream `totalDist / 6 < 0.1`. */
        const val ALIGNMENT_THRESHOLD = 0.1
        private const val ROTATION_STRENGTH = 0.6
        private const val YAW_THRESHOLD = 0.25
        private val KEY_JOINTS = intArrayOf(11, 13, 15, 23, 25, 27)

        /** Drop-in singleton mirroring the upstream module-global API. */
        val core: GhostGuide by lazy { GhostGuide() }
    }
}
