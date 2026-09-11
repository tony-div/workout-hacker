package com.workoutpose.reps

/** Rep phase labels, matching the upstream `RepPhase` / `phase` strings. */
object RepPhase {
    const val UNKNOWN = "UNKNOWN"
    const val UP = "UP"
    const val DOWN = "DOWN"
}

/** Session config, mirroring upstream `RepCounterConfig`. */
data class RepCounterConfig(
        val exercise: String? = null,
)

/**
 * Counter state, mirroring upstream `RepCounterState`.
 * `reps` stays `Double` (upstream `number`); `phase` is one of [RepPhase].
 * `inferenceMs` is new (upstream measures nothing — pure math): last
 * [RepCounter.update] wall time, ms; -1 = no update yet.
 */
data class RepCounterState(
        val exercise: String?,
        val reps: Double,
        val confidence: Double,
        val phase: String,
        val activeArm: String?,
        val inferenceMs: Double = -1.0,
)
