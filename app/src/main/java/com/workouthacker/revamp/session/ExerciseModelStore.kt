package com.workouthacker.revamp.session

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Download state for the exercise-classifier model file. */
sealed interface ExerciseModelState {
    data object Checking : ExerciseModelState
    data class Downloading(val receivedBytes: Long, val totalBytes: Long) : ExerciseModelState
    data class Ready(val file: File) : ExerciseModelState
    data class Error(val message: String) : ExerciseModelState
}

/**
 * Fetches `exercise_classifier_rf.json` (55 MB, too large to bundle) to
 * internal storage on first launch, then serves it from disk.
 *
 * This is the user-visible stopgap for the `TODO` item "remove the model
 * asset files from app assets and add script to download it on device on
 * first open" — applied here to the exercise model first. The pose
 * landmarker `.task` files still ship in assets.
 */
class ExerciseModelStore(context: Context) {

    private val appContext = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ExerciseModelState>(ExerciseModelState.Checking)
    val state: StateFlow<ExerciseModelState> = _state.asStateFlow()

    /** No-op when the file already exists; downloads otherwise. */
    fun ensureModel() {
        scope.launch {
            val file = modelFile()
            if (file.exists() && file.length() > MIN_VALID_BYTES) {
                Log.d(TAG, "model cached (${file.length()} bytes)")
                _state.value = ExerciseModelState.Ready(file)
                return@launch
            }
            download(file)
        }
    }

    private fun modelFile(): File = File(File(appContext.filesDir, MODELS_DIR), MODEL_NAME)

    private fun download(dest: File) {
        _state.value = ExerciseModelState.Downloading(0, -1)
        try {
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parent, "${dest.name}.tmp")
            val url = URL(MODEL_URL)
            var received = 0L
            var total = -1L
            (url.openConnection() as HttpURLConnection).run {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                connect()
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("HTTP $responseCode")
                }
                total = contentLengthLong
                inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            received += n
                            _state.value = ExerciseModelState.Downloading(received, total)
                        }
                    }
                }
                disconnect()
            }
            if (tmp.length() < MIN_VALID_BYTES) {
                tmp.delete()
                throw IllegalStateException("downloaded file too small (${tmp.length()} bytes)")
            }
            if (!tmp.renameTo(dest)) throw IllegalStateException("rename failed")
            Log.d(TAG, "model downloaded (${dest.length()} bytes)")
            _state.value = ExerciseModelState.Ready(dest)
        } catch (e: Exception) {
            Log.e(TAG, "model download failed", e)
            _state.value = ExerciseModelState.Error(e.message ?: e.toString())
        }
    }

    private companion object {
        const val TAG = "ExerciseModelStore"
        const val MODELS_DIR = "models"
        const val MODEL_NAME = "exercise_classifier_rf.json"
        // Canonical source: same file the old RN app vendored in android assets.
        const val MODEL_URL =
                "https://raw.githubusercontent.com/AHMED-ELMAKI/WorkoutHacker/main/exercise_classifier_rf.json"
        // Anything smaller is a truncated download, not a model.
        const val MIN_VALID_BYTES = 10_000_000L
    }
}
