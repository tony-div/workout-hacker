package com.workoutpose.randomforest

import android.util.Log

/**
 * Shared on-device Random Forest inference runtime — **Rust only**.
 *
 * Thin Kotlin wrapper over [RandomForestNative] JNI into the vendored Rust
 * staticlib (`src/main/prebuilt/<abi>/librandom_forest_rust.a`, rebuilt with
 * `build-rust-android.sh`). Same engine the exercise and tempo classifiers
 * use upstream (`react-native-random-forest`).
 *
 * Named-model registry lives in Rust (`Mutex<HashMap<String,
 * RandomForestRunner>>`), so multiple classifiers (exercise + tempo, …)
 * coexist in one process via the `modelName` slot — matching the upstream
 * `_named` FFI variants. There is deliberately **no Kotlin fallback**: when
 * the native library is absent every call fails loudly (`false`/`null` +
 * logcat) instead of silently degrading.
 *
 * All methods are thread-safe. Inference is synchronous — call off the UI
 * thread (downstream engines already do).
 */
object RandomForest {

    const val DEFAULT_MODEL = "default"

    private const val TAG = "WorkoutRandomForest"

    @Volatile
    private var nativeLoaded: Boolean? = null

    private fun ensureNative(): Boolean {
        nativeLoaded?.let { return it }
        val ok = runCatching { System.loadLibrary("workout_randomforest") }
            .onFailure { Log.e(TAG, "Rust engine missing: libworkout_randomforest.so not loaded", it) }
            .isSuccess
        nativeLoaded = ok
        return ok
    }

    /**
     * Loads a model JSON (scikit-learn `tree_.__getstate__()` layout) under
     * [modelName]. Returns `false` when the Rust engine is missing or the
     * JSON fails validation.
     */
    @JvmStatic
    @JvmOverloads
    fun loadModel(modelJson: String, modelName: String = DEFAULT_MODEL): Boolean {
        val name = modelName.ifEmpty { DEFAULT_MODEL }
        if (!ensureNative()) return false
        return runCatching {
            if (name == DEFAULT_MODEL) RandomForestNative.nativeLoadModel(modelJson)
            else RandomForestNative.nativeLoadModelNamed(name, modelJson)
        }.getOrDefault(false)
    }

    /**
     * Runs inference on row-major [flatData] (`rows × cols`, `cols` must equal
     * the model's `n_features`). Returns row-major `rows × n_classes`
     * probabilities, or `null` when the engine is missing, no model is
     * loaded, or dims mismatch.
     */
    @JvmStatic
    @JvmOverloads
    fun predictProbabilities(
            flatData: DoubleArray,
            rows: Int,
            cols: Int,
            modelName: String = DEFAULT_MODEL,
    ): DoubleArray? {
        if (rows <= 0 || cols <= 0 || flatData.size != rows * cols) return null
        if (!ensureNative()) return null
        val name = modelName.ifEmpty { DEFAULT_MODEL }
        return runCatching {
            if (name == DEFAULT_MODEL) {
                RandomForestNative.nativePredictProbabilities(flatData, rows, cols)
            } else {
                RandomForestNative.nativePredictProbabilitiesNamed(name, flatData, rows, cols)
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Class ids in probability-vector order, or `null` when unavailable. */
    @JvmStatic
    @JvmOverloads
    fun getClassIds(modelName: String = DEFAULT_MODEL): IntArray? {
        if (!ensureNative()) return null
        val name = modelName.ifEmpty { DEFAULT_MODEL }
        return runCatching {
            if (name == DEFAULT_MODEL) RandomForestNative.nativeGetClassIds()
            else RandomForestNative.nativeGetClassIdsNamed(name)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Drops a model and frees its trees. Safe to call when absent. */
    @JvmStatic
    @JvmOverloads
    fun unloadModel(modelName: String = DEFAULT_MODEL) {
        if (!ensureNative()) return
        val name = modelName.ifEmpty { DEFAULT_MODEL }
        runCatching {
            if (name == DEFAULT_MODEL) RandomForestNative.nativeUnloadModel()
            else RandomForestNative.nativeUnloadModelNamed(name)
        }
    }

    /** Diagnostics: whether the Rust engine loaded. */
    fun isUsingNative(): Boolean = ensureNative()
}
