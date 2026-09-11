package com.workoutpose.reps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/**
 * Rep counter ported from `react-native-rep-counter`'s `HybridRepCounter.kt`
 * (MIT, tony-div). Algorithm unchanged: per-exercise elbow/shoulder/wrist
 * angle + vertical-position phase machine with 3-frame phase confirmation,
 * bicep-curl swing guard, and triceps head-proximity + ROM gate.
 *
 * Nitro types stripped (`HybridHybridRepCounterSpec` base,
 * `Variant_NullType_*` wrappers) — plain Kotlin nullables instead. State is
 * additionally published on [state] as a StateFlow (workout-pose pattern);
 * synchronous [getState] is kept for drop-in parity.
 */
class RepCounter {
  private val _state = MutableStateFlow(RepCounterState(exercise = null, reps = 0.0, confidence = 0.0, phase = RepPhase.UNKNOWN, activeArm = null))
  /** Latest counter state, updated on every [update]. */
  val state: StateFlow<RepCounterState> = _state.asStateFlow()

  private var reps = 0.0
  private var phase: String = "UNKNOWN"
  private var exercise: String? = null
  private var confidence = 0.0
  private var lastElbow: Double? = null
  private var currentRepMaxSwing = 0.0
  private var swingOk = true
  private var minElbow = 999.0
  private var maxElbow = 0.0
  private var activeArm: String? = null
  private var phaseCandidate: String? = null
  private var phaseCandidateCount = 0
  private var lastUpdateMs = -1.0

  companion object {
    private const val TAG = "RepCounter"
    private const val NOSE = 0
    private const val L_SHOULDER = 11
    private const val R_SHOULDER = 12
    private const val L_ELBOW = 13
    private const val R_ELBOW = 14
    private const val L_WRIST = 15
    private const val R_WRIST = 16
    private const val L_HIP = 23
    private const val R_HIP = 24
    private const val VALUES_PER_LANDMARK = 4
    private const val PHASE_CONFIRM_FRAMES = 3

    private val EXERCISE_ALIASES = mapOf(
      "bicep curl" to "bicep_curl",
      "bicep_curl" to "bicep_curl",
      "front raise" to "front_raise",
      "front raises" to "front_raise",
      "front_raise" to "front_raise",
      "lateral raise" to "lateral_raise",
      "lateral raises" to "lateral_raise",
      "lateral_raise" to "lateral_raise",
      "shoulder press" to "shoulder_press",
      "shoulder_press" to "shoulder_press",
      "triceps extension" to "tricep_extension",
      "tricep extension" to "tricep_extension",
      "tricep_extension" to "tricep_extension",
      "push up" to "push_up",
      "push_up" to "push_up",
      "pull up" to "pull_up",
      "pull_up" to "pull_up",
      "bench pressing" to "bench_pressing",
      "bench_pressing" to "bench_pressing",
    )
  }

  fun startSession(config: RepCounterConfig? = null) {
    resetAll()
    if (config != null) {
      exercise = normalizeExercise(config.exercise)
    }
    _state.value = getState()
  }

  fun stopSession() {
    exercise = null
    _state.value = getState()
  }

  fun setExercise(exercise: String?) {
    this.exercise = normalizeExercise(exercise)
    _state.value = getState()
  }

  fun resetReps() {
    reps = 0.0
    phase = "UNKNOWN"
    lastElbow = null
    currentRepMaxSwing = 0.0
    swingOk = true
    minElbow = 999.0
    maxElbow = 0.0
    activeArm = null
    phaseCandidate = null
    phaseCandidateCount = 0
    _state.value = getState()
  }

  fun resetAll() {
    resetReps()
    exercise = null
    confidence = 0.0
    _state.value = getState()
  }

  fun getState(): RepCounterState {
    return RepCounterState(
      exercise = exercise,
      reps = reps,
      confidence = confidence,
      phase = phase,
      activeArm = activeArm,
      inferenceMs = lastUpdateMs,
    )
  }

  /**
   * Feeds one flat 132-value landmark buffer (33 x x, y, z, visibility).
   * Buffers of any other size are ignored. Returns the new state and
   * publishes it on [state]. Wall time is recorded on [RepCounterState.inferenceMs].
   */
  fun update(landmarks: DoubleArray, exerciseOverride: String? = null): RepCounterState {
    if (exerciseOverride != null) {
      exercise = normalizeExercise(exerciseOverride)
    }
    if (landmarks.size != 33 * 4) return getState()
    val startNs = System.nanoTime()
    updatePhase(landmarks)
    lastUpdateMs = (System.nanoTime() - startNs) / 1_000_000.0
    val state = getState()
    _state.value = state
    return state
  }

  /** Float-buffer overload for direct `PoseFrame.landmarks` use. */
  fun update(landmarks: FloatArray, exerciseOverride: String? = null): RepCounterState {
    return update(DoubleArray(landmarks.size) { i -> landmarks[i].toDouble() }, exerciseOverride)
  }

  private fun normalizeExercise(name: String?): String? {
    if (name == null) return null
    return EXERCISE_ALIASES[name.trim().lowercase()]
  }

  private fun clamp(value: Double, min: Double, max: Double): Double {
    return when {
      value < min -> min
      value > max -> max
      else -> value
    }
  }

  private fun angleBetween(a: DoubleArray, b: DoubleArray, c: DoubleArray): Double {
    val bax = a[0] - b[0]
    val bay = a[1] - b[1]
    val baz = a[2] - b[2]
    val bcx = c[0] - b[0]
    val bcy = c[1] - b[1]
    val bcz = c[2] - b[2]
    val baNorm = sqrt(bax * bax + bay * bay + baz * baz)
    val bcNorm = sqrt(bcx * bcx + bcy * bcy + bcz * bcz)
    val denom = baNorm * bcNorm + 1e-8
    val cosVal = clamp((bax * bcx + bay * bcy + baz * bcz) / denom, -1.0, 1.0)
    return acos(cosVal) * 180.0 / PI
  }

  private fun getLandmark(buffer: DoubleArray, index: Int): DoubleArray {
    val base = index * VALUES_PER_LANDMARK
    return doubleArrayOf(buffer[base], buffer[base + 1], buffer[base + 2])
  }

  private fun torsoSize(buffer: DoubleArray): Double {
    val midSh = doubleArrayOf(
      (buffer[L_SHOULDER * 4] + buffer[R_SHOULDER * 4]) / 2.0,
      (buffer[L_SHOULDER * 4 + 1] + buffer[R_SHOULDER * 4 + 1]) / 2.0,
      (buffer[L_SHOULDER * 4 + 2] + buffer[R_SHOULDER * 4 + 2]) / 2.0,
    )
    val midHp = doubleArrayOf(
      (buffer[L_HIP * 4] + buffer[R_HIP * 4]) / 2.0,
      (buffer[L_HIP * 4 + 1] + buffer[R_HIP * 4 + 1]) / 2.0,
      (buffer[L_HIP * 4 + 2] + buffer[R_HIP * 4 + 2]) / 2.0,
    )
    val dx = midSh[0] - midHp[0]
    val dy = midSh[1] - midHp[1]
    val dz = midSh[2] - midHp[2]
    return sqrt(dx * dx + dy * dy + dz * dz) + 1e-8
  }

  private fun confirmPhase(candidate: String?): String {
    if (candidate == phase) {
      phaseCandidate = null
      phaseCandidateCount = 0
      return phase
    }
    if (candidate == null) {
      return phase
    }
    if (candidate == phaseCandidate) {
      phaseCandidateCount += 1
    } else {
      phaseCandidate = candidate
      phaseCandidateCount = 1
    }
    if (phaseCandidateCount >= PHASE_CONFIRM_FRAMES) {
      phaseCandidate = null
      phaseCandidateCount = 0
      return candidate
    }
    return phase
  }

  private fun updatePhase(buffer: DoubleArray) {
    val ex = exercise ?: return
    val prev = phase

    val rElbow = angleBetween(
      getLandmark(buffer, R_SHOULDER),
      getLandmark(buffer, R_ELBOW),
      getLandmark(buffer, R_WRIST)
    )
    val lElbow = angleBetween(
      getLandmark(buffer, L_SHOULDER),
      getLandmark(buffer, L_ELBOW),
      getLandmark(buffer, L_WRIST)
    )
    val elbowAvg = (rElbow + lElbow) / 2.0

    val rShY = buffer[R_SHOULDER * 4 + 1]
    val lShY = buffer[L_SHOULDER * 4 + 1]
    val rWrY = buffer[R_WRIST * 4 + 1]
    val lWrY = buffer[L_WRIST * 4 + 1]
    val wristY = (rWrY + lWrY) / 2.0
    val shoulderY = (rShY + lShY) / 2.0
    val noseY = buffer[NOSE * 4 + 1]

    when (ex) {
      "bicep_curl" -> {
        val rSwing = angleBetween(
          getLandmark(buffer, R_ELBOW),
          getLandmark(buffer, R_SHOULDER),
          getLandmark(buffer, R_HIP)
        )
        val lSwing = angleBetween(
          getLandmark(buffer, L_ELBOW),
          getLandmark(buffer, L_SHOULDER),
          getLandmark(buffer, L_HIP)
        )
        var elbow: Double
        val swing: Double
        if (lElbow <= rElbow) {
          elbow = lElbow
          swing = lSwing
          activeArm = "left"
        } else {
          elbow = rElbow
          swing = rSwing
          activeArm = "right"
        }
        if (lastElbow == null) {
          lastElbow = elbow
        }
        elbow = 0.4 * lastElbow!! + 0.6 * elbow
        lastElbow = elbow

        val candidate: String? = when {
          elbow < 110 -> "UP"
          elbow > 120 -> "DOWN"
          else -> null
        }
        phase = confirmPhase(candidate)

        if (phase == "DOWN") {
          currentRepMaxSwing = max(currentRepMaxSwing, swing)
          if (swing > 80) {
            swingOk = false
          }
        }
        if (prev == "UP" && phase == "DOWN") {
          swingOk = true
          currentRepMaxSwing = 0.0
        }
        if (prev == "DOWN" && phase == "UP" && swingOk) {
          reps += 1.0
          currentRepMaxSwing = 0.0
        }
        return
      }

      "tricep_extension" -> {
        val rightDominant = rElbow > lElbow
        val elbow = if (rightDominant) rElbow else lElbow
        val wrist3 = if (rightDominant) getLandmark(buffer, R_WRIST) else getLandmark(buffer, L_WRIST)
        val nose3 = getLandmark(buffer, NOSE)
        val torso = torsoSize(buffer)
        val nearHead = sqrt(
          (wrist3[0] - nose3[0]).pow(2) +
          (wrist3[1] - nose3[1]).pow(2) +
          (wrist3[2] - nose3[2]).pow(2)
        ) / torso < 1.5

        val smoothedElbow = if (lastElbow == null) elbow else 0.5 * lastElbow!! + 0.5 * elbow
        lastElbow = smoothedElbow
        minElbow = min(minElbow, smoothedElbow)
        maxElbow = max(maxElbow, smoothedElbow)
        val rom = maxElbow - minElbow

        when {
          smoothedElbow > 140 -> phase = "UP"
          smoothedElbow < 115 -> phase = "DOWN"
        }
        if (prev == "DOWN" && phase == "UP" && nearHead && rom > 35) {
          reps += 1.0
          minElbow = smoothedElbow
          maxElbow = smoothedElbow
        }
        return
      }

      "front_raise", "lateral_raise" -> {
        if (wristY < shoulderY) {
          phase = "UP"
        } else if (wristY > shoulderY + 0.1) {
          phase = "DOWN"
        }
      }

      "shoulder_press" -> {
        if (wristY < noseY - 0.15) {
          phase = "UP"
        } else if (wristY > shoulderY - 0.25) {
          phase = "DOWN"
        }
      }

      "push_up" -> {
        if (elbowAvg > 145) {
          phase = "UP"
        } else if (elbowAvg < 95) {
          phase = "DOWN"
        }
      }

      "pull_up" -> {
        if (noseY < wristY) {
          phase = "UP"
        } else if (noseY > wristY + 0.08) {
          phase = "DOWN"
        }
      }

      "bench_pressing" -> {
        if (elbowAvg > 150) {
          phase = "UP"
        } else if (elbowAvg < 85) {
          phase = "DOWN"
        }
      }
    }

    if (prev == "DOWN" && phase == "UP") {
      reps += 1.0
    }
  }
}
