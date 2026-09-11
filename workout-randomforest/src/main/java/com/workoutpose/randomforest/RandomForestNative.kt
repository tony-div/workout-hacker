package com.workoutpose.randomforest

/**
 * JNI entry points into `libworkout_randomforest.so` (Rust staticlib).
 *
 * Never called directly — use [RandomForest]. Method names must match
 * `workout_randomforest_jni.cpp`.
 */
internal object RandomForestNative {
    external fun nativeLoadModel(modelJson: String): Boolean
    external fun nativeLoadModelNamed(modelName: String, modelJson: String): Boolean
    external fun nativePredictProbabilities(flatData: DoubleArray, rows: Int, cols: Int): DoubleArray?
    external fun nativePredictProbabilitiesNamed(
            modelName: String,
            flatData: DoubleArray,
            rows: Int,
            cols: Int,
    ): DoubleArray?
    external fun nativeGetClassIds(): IntArray?
    external fun nativeGetClassIdsNamed(modelName: String): IntArray?
    external fun nativeUnloadModel()
    external fun nativeUnloadModelNamed(modelName: String)
}
