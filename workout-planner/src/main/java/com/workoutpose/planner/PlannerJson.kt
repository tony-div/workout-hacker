package com.workoutpose.planner

import org.json.JSONArray
import org.json.JSONObject

/**
 * Manual JSON mapping for the planner wire format (mirrors
 * `core/src/shared/schemas.ts` validation + the client POST body).
 * Hand-rolled on `org.json` to avoid a serialization dependency; field
 * names match the TS types exactly.
 */
object PlannerJson {

    fun workoutRequestToJson(request: WorkoutRequest): String {
        val root = JSONObject()
        request.primaryGoal?.let { root.put("primaryGoal", it) }
        request.trainingLevel?.let { root.put("trainingLevel", it) }
        root.put("daysPerWeek", request.daysPerWeek)
        request.programDurationWeeks?.let { root.put("programDurationWeeks", it) }
        if (request.equipmentAvailable.isNotEmpty()) {
            root.put("equipmentAvailable", JSONArray(request.equipmentAvailable))
        }
        request.demographics?.let { d ->
            root.put("demographics", JSONObject().apply {
                d.gender?.let { put("gender", it) }
                d.bodyWeight?.let { put("bodyWeight", it) }
                d.height?.let { put("height", it) }
                d.age?.let { put("age", it) }
                put("trainingAge", d.trainingAge)
            })
        }
        request.limitations?.let { l ->
            root.put("limitations", JSONObject().apply {
                put("injuries", JSONArray(l.injuries))
                put("mobilityDifficulties", JSONArray(l.mobilityDifficulties))
            })
        }
        request.currentRPE?.let { root.put("currentRPE", it) }
        request.naturalLanguageRequest?.let { root.put("naturalLanguageRequest", it) }
        // currentPlan round-trips as an opaque object when present.
        return root.toString()
    }

    /**
     * Parses + validates a server plan response.
     * Mirrors TS `parseWorkoutPlanResponse` / `isWorkoutPlan`: throws on
     * invalid JSON or failed structural checks.
     */
    fun parseWorkoutPlanResponse(text: String): WorkoutPlan {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON from model: ${e.message}")
        }
        if (!isWorkoutPlan(root)) {
            throw IllegalArgumentException("Model response failed WorkoutPlan validation checks")
        }
        return parseWorkoutPlan(root)
    }

    /** Structural check mirroring TS `isWorkoutPlan`. */
    fun isWorkoutPlan(root: JSONObject): Boolean {
        return root.has("planName") &&
                root.optJSONArray("days")?.length()?.let { it > 0 } == true &&
                root.has("interSetRecoveryPolicy") &&
                root.optJSONArray("progressiveOverload") != null
    }

    fun parseWorkoutPlan(root: JSONObject): WorkoutPlan {
        val days = root.optJSONArray("days")
        val overload = root.optJSONArray("progressiveOverload")
        return WorkoutPlan(
                planName = root.optString("planName"),
                primaryGoal = root.optString("primaryGoal"),
                trainingLevel = root.optString("trainingLevel", TrainingLevels.BEGINNER),
                daysPerWeek = root.optInt("daysPerWeek"),
                durationWeeks = root.takeIf { it.has("durationWeeks") }?.optInt("durationWeeks"),
                rationale = root.optString("rationale"),
                interSetRecoveryPolicy = root.optString("interSetRecoveryPolicy"),
                progressiveOverload = List(overload?.length() ?: 0) { i ->
                    val rule = overload!!.getJSONObject(i)
                    ProgressiveOverloadRule(
                            ruleName = rule.optString("ruleName"),
                            description = rule.optString("description"),
                    )
                },
                days = List(days?.length() ?: 0) { i ->
                    val day = days!!.getJSONObject(i)
                    val exercises = day.optJSONArray("exercises")
                    WorkoutDay(
                            dayLabel = day.optString("dayLabel"),
                            focus = day.optString("focus"),
                            warmup = day.optJSONArray("warmup")?.toStringList() ?: emptyList(),
                            exercises = List(exercises?.length() ?: 0) { j ->
                                val ex = exercises!!.getJSONObject(j)
                                val sets = ex.optJSONObject("sets")
                                ExercisePrescription(
                                        exerciseName = ex.optString("exerciseName"),
                                        equipment = ex.optString("equipment"),
                                        notes = ex.takeIf { it.has("notes") }?.optString("notes"),
                                        sets = ExerciseSets(
                                                sets = sets?.optInt("sets") ?: 0,
                                                weight = sets?.optDouble("weight") ?: 0.0,
                                                reps = sets?.optInt("reps") ?: 0,
                                                rest = sets?.optInt("rest") ?: 0,
                                                targetRpe = sets?.optDouble("targetRpe") ?: 0.0,
                                        ),
                                )
                            },
                    )
                },
        )
    }

    private fun JSONArray.toStringList(): List<String> =
            List(length()) { i -> optString(i) }
}
