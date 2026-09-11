package com.workoutpose.planner

/**
 * Request normalization, ported from `core/src/shared/normalize.ts`.
 * Same defaults, same validation errors.
 */
object PlannerNormalize {

    val DEFAULT_EQUIPMENT = listOf("barbell", "dumbbell", "machines", "bands", "bodyweight")

    fun normalizeLimitations(limitations: Limitations?): Limitations = Limitations(
            injuries = limitations?.injuries ?: emptyList(),
            mobilityDifficulties = limitations?.mobilityDifficulties ?: emptyList(),
    )

    fun normalizeWorkoutRequest(request: WorkoutRequest): NormalizedWorkoutRequest {
        if (request.daysPerWeek < 1 || request.daysPerWeek > 7) {
            throw IllegalArgumentException("daysPerWeek must be between 1 and 7")
        }
        request.currentRPE?.let {
            if (it < 1 || it > 10) throw IllegalArgumentException("currentRPE must be between 1 and 10")
        }
        request.programDurationWeeks?.let {
            if (it < 1) throw IllegalArgumentException("programDurationWeeks must be a positive number")
        }
        val demographics = request.demographics
        return NormalizedWorkoutRequest(
                primaryGoal = request.primaryGoal ?: PrimaryGoals.GENERAL_FITNESS,
                trainingLevel = request.trainingLevel ?: TrainingLevels.BEGINNER,
                daysPerWeek = request.daysPerWeek,
                programDurationWeeks = request.programDurationWeeks,
                equipmentAvailable = request.equipmentAvailable.ifEmpty { DEFAULT_EQUIPMENT },
                demographics = Demographics(
                        gender = demographics?.gender,
                        bodyWeight = demographics?.bodyWeight,
                        height = demographics?.height,
                        age = demographics?.age,
                        trainingAge = demographics?.trainingAge ?: 0.0,
                ),
                limitations = normalizeLimitations(request.limitations),
                currentRPE = request.currentRPE,
                currentPlan = request.currentPlan,
                naturalLanguageRequest = request.naturalLanguageRequest,
        )
    }
}
