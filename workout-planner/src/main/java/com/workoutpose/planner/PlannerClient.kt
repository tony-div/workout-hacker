package com.workoutpose.planner

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Planner HTTP client, ported from `core/src/client/index.ts`
 * (`generatePlan`). The Node/Gemini backend stays as-is — this only swaps
 * `fetch` for `HttpURLConnection` on `Dispatchers.IO`.
 *
 * ```
 * val plan = PlannerClient.generatePlan(
 *     PlannerClientConfig(apiBaseUrl = "https://gymhacker.onrender.com"),
 *     WorkoutRequest(daysPerWeek = 4, primaryGoal = PrimaryGoals.HYPERTROPHY),
 * )
 * ```
 */
object PlannerClient {

    private const val TAG = "PlannerClient"
    private const val DEFAULT_ENDPOINT = "/api/workout"

    fun endpointFor(config: PlannerClientConfig): String {
        val base = config.apiBaseUrl.trimEnd('/')
        val path = config.endpointPath.ifEmpty { DEFAULT_ENDPOINT }
                .let { if (it.startsWith("/")) it else "/$it" }
        return base + path
    }

    /**
     * POSTs [request] and returns the validated [WorkoutPlan].
     * Throws with the server's `error` message on non-2xx, like upstream.
     */
    suspend fun generatePlan(
            config: PlannerClientConfig,
            request: WorkoutRequest,
    ): WorkoutPlan = withContext(Dispatchers.IO) {
        val endpoint = endpointFor(config)
        val body = PlannerJson.workoutRequestToJson(request)
        Log.d(TAG, "generatePlan POST $endpoint")
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            for ((key, value) in config.headers) setRequestProperty(key, value)
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException(serverErrorMessage(connection, code))
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            PlannerJson.parseWorkoutPlanResponse(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun serverErrorMessage(connection: HttpURLConnection, code: Int): String {
        var message = "Request failed with status $code"
        try {
            val text = connection.errorStream?.bufferedReader()?.use { it.readText() }
            val error = text?.let { JSONObject(it).optString("error") }
            if (!error.isNullOrEmpty()) message = error
        } catch (_: Exception) {
            // Keep the status-based message, like upstream.
        }
        return message
    }
}
