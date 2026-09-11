package com.workoutpose.planner

/**
 * Workout-planner domain types, mirroring `core/src/shared/types.ts` from
 * `react-native-workout-planner` (ISC, tony-div).
 *
 * String-union TS types (`TrainingLevel`, goals, …) stay plain Kotlin
 * `String`s with companion constants, keeping the JSON wire format identical
 * without a serialization dependency.
 */
object TrainingLevels {
    const val BEGINNER = "beginner"
    const val INTERMEDIATE = "intermediate"
    const val ADVANCED = "advanced"
}

object PrimaryGoals {
    const val HYPERTROPHY = "hypertrophy"
    const val STRENGTH = "strength"
    const val GENERAL_FITNESS = "general_fitness"
    const val OTHER = "other"
}

data class Demographics(
        val gender: String? = null,
        val bodyWeight: Double? = null,
        val height: Double? = null,
        val age: Double? = null,
        val trainingAge: Double = 0.0,
)

data class Limitations(
        val injuries: List<String> = emptyList(),
        val mobilityDifficulties: List<String> = emptyList(),
)

data class ExerciseSets(
        val sets: Int = 0,
        val weight: Double = 0.0,
        val reps: Int = 0,
        val rest: Int = 0,
        val targetRpe: Double = 0.0,
)

data class ExercisePrescription(
        val exerciseName: String = "",
        val equipment: String = "",
        val notes: String? = null,
        val sets: ExerciseSets = ExerciseSets(),
)

data class WorkoutDay(
        val dayLabel: String = "",
        val focus: String = "",
        val warmup: List<String> = emptyList(),
        val exercises: List<ExercisePrescription> = emptyList(),
)

data class ProgressiveOverloadRule(
        val ruleName: String = "",
        val description: String = "",
)

data class WorkoutPlan(
        val planName: String = "",
        val primaryGoal: String = "",
        val trainingLevel: String = TrainingLevels.BEGINNER,
        val daysPerWeek: Int = 0,
        val durationWeeks: Int? = null,
        val rationale: String = "",
        val interSetRecoveryPolicy: String = "",
        val progressiveOverload: List<ProgressiveOverloadRule> = emptyList(),
        val days: List<WorkoutDay> = emptyList(),
)

data class WorkoutRequest(
        val primaryGoal: String? = null,
        val trainingLevel: String? = null,
        val daysPerWeek: Int = 0,
        val programDurationWeeks: Int? = null,
        val equipmentAvailable: List<String> = emptyList(),
        val demographics: Demographics? = null,
        val limitations: Limitations? = null,
        val currentRPE: Double? = null,
        val currentPlan: WorkoutPlan? = null,
        val naturalLanguageRequest: String? = null,
)

data class NormalizedWorkoutRequest(
        val primaryGoal: String,
        val trainingLevel: String,
        val daysPerWeek: Int,
        val programDurationWeeks: Int?,
        val equipmentAvailable: List<String>,
        val demographics: Demographics,
        val limitations: Limitations,
        val currentRPE: Double? = null,
        val currentPlan: WorkoutPlan? = null,
        val naturalLanguageRequest: String? = null,
)

/** Client transport config, mirroring TS `ClientConfig`. */
data class PlannerClientConfig(
        val apiBaseUrl: String,
        val endpointPath: String = "/api/workout",
        val headers: Map<String, String> = emptyMap(),
)

data class PlannerErrorResponse(val error: String? = null)
