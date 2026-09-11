// JNI bridge for :workout-randomforest.
//
// Ported from react-native-random-forest's
// android/src/main/cpp/RandomForestJniBridge.cpp (MIT, tony-div).
// Package renamed com.margelo.nitro.randomforest -> com.workoutpose.randomforest
// and Nitro/nitrogen dependencies removed. The Rust FFI contract
// (rf_load_model[_named], rf_predict_probabilities[_named], rf_get_class_ids[_named],
// rf_free_data, rf_free_ints, rf_unload_model[_named]) is unchanged so the
// vendored prebuilt librandom_forest_rust.a links as-is.
//
// When RF_USE_RUST is undefined (no prebuilt for this ABI) every entry point
// reports failure and the Kotlin fallback engine (RandomForestRunner) takes over.

#include <jni.h>
#include <android/log.h>
#include <cstddef>

#if defined(RF_USE_RUST)
extern "C" {
int rf_load_model(const char* model_json);
int rf_load_model_named(const char* model_name, const char* model_json);
int* rf_get_class_ids(int* out_len);
int* rf_get_class_ids_named(const char* model_name, int* out_len);
double* rf_predict_probabilities(const double* data, int rows, int cols, int* out_rows, int* out_cols);
double* rf_predict_probabilities_named(const char* model_name, const double* data, int rows, int cols, int* out_rows, int* out_cols);
void rf_free_data(double* ptr);
void rf_free_ints(int* ptr);
void rf_unload_model();
void rf_unload_model_named(const char* model_name);
}
#endif

namespace {

constexpr const char* kLogTag = "WorkoutRandomForest";

void logDebug(const char* message) {
  __android_log_print(ANDROID_LOG_DEBUG, kLogTag, "%s", message);
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_workoutpose_randomforest_RandomForestNative_nativeLoadModel(
    JNIEnv* env, jclass /*clazz*/, jstring modelJson) {
#if defined(RF_USE_RUST)
  const char* utf = env->GetStringUTFChars(modelJson, nullptr);
  if (utf == nullptr) return JNI_FALSE;
  int result = rf_load_model(utf);
  env->ReleaseStringUTFChars(modelJson, utf);
  return (result == 1) ? JNI_TRUE : JNI_FALSE;
#else
  (void)env; (void)modelJson;
  logDebug("nativeLoadModel(): RF_USE_RUST disabled, using Kotlin fallback");
  return JNI_FALSE;
#endif
}

JNIEXPORT jdoubleArray JNICALL
Java_com_workoutpose_randomforest_RandomForestNative_nativePredictProbabilities(
    JNIEnv* env, jclass /*clazz*/, jdoubleArray flatData, jint rows, jint cols) {
#if defined(RF_USE_RUST)
  jsize len = env->GetArrayLength(flatData);
  if (len == 0 || rows <= 0 || cols <= 0) return nullptr;

  jdouble* data = env->GetDoubleArrayElements(flatData, nullptr);
  if (data == nullptr) return nullptr;

  int outRows = 0, outCols = 0;
  double* outData = rf_predict_probabilities(data, rows, cols, &outRows, &outCols);

  env->ReleaseDoubleArrayElements(flatData, data, JNI_ABORT);

  if (outData == nullptr || outRows <= 0 || outCols <= 0) {
    return nullptr;
  }

  jsize total = outRows * outCols;
  jdoubleArray result = env->NewDoubleArray(total);
  if (result != nullptr) {
    env->SetDoubleArrayRegion(result, 0, total, outData);
  }

  rf_free_data(outData);
  return result;
#else
  (void)env; (void)flatData; (void)rows; (void)cols;
  logDebug("nativePredictProbabilities(): RF_USE_RUST disabled, using Kotlin fallback");
  return nullptr;
#endif
}

JNIEXPORT jintArray JNICALL
Java_com_workoutpose_randomforest_RandomForestNative_nativeGetClassIds(
    JNIEnv* env, jclass /*clazz*/) {
#if defined(RF_USE_RUST)
  int outLen = 0;
  int* ids = rf_get_class_ids(&outLen);
  if (ids == nullptr || outLen <= 0) return nullptr;

  jintArray result = env->NewIntArray(outLen);
  if (result != nullptr) {
    env->SetIntArrayRegion(result, 0, outLen, ids);
  }

  rf_free_ints(ids);
  return result;
#else
  (void)env;
  logDebug("nativeGetClassIds(): RF_USE_RUST disabled, using Kotlin fallback");
  return nullptr;
#endif
}

JNIEXPORT void JNICALL
Java_com_workoutpose_randomforest_RandomForestNative_nativeUnloadModel(
    JNIEnv* env, jclass /*clazz*/) {
#if defined(RF_USE_RUST)
  rf_unload_model();
#else
  (void)env;
  logDebug("nativeUnloadModel(): RF_USE_RUST disabled, using Kotlin fallback");
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_workoutpose_randomforest_RandomForestNative_nativeLoadModelNamed(
    JNIEnv* env, jclass /*clazz*/, jstring modelName, jstring modelJson) {
#if defined(RF_USE_RUST)
  const char* nameUtf = env->GetStringUTFChars(modelName, nullptr);
  if (nameUtf == nullptr) return JNI_FALSE;
  const char* jsonUtf = env->GetStringUTFChars(modelJson, nullptr);
  if (jsonUtf == nullptr) { env->ReleaseStringUTFChars(modelName, nameUtf); return JNI_FALSE; }
  int result = rf_load_model_named(nameUtf, jsonUtf);
  env->ReleaseStringUTFChars(modelJson, jsonUtf);
  env->ReleaseStringUTFChars(modelName, nameUtf);
  return (result == 1) ? JNI_TRUE : JNI_FALSE;
#else
  (void)env; (void)modelName; (void)modelJson;
  logDebug("nativeLoadModelNamed(): RF_USE_RUST disabled, using Kotlin fallback");
  return JNI_FALSE;
#endif
}

JNIEXPORT jdoubleArray JNICALL
Java_com_workoutpose_randomforest_RandomForestNative_nativePredictProbabilitiesNamed(
    JNIEnv* env, jclass /*clazz*/, jstring modelName, jdoubleArray flatData, jint rows, jint cols) {
#if defined(RF_USE_RUST)
  const char* nameUtf = env->GetStringUTFChars(modelName, nullptr);
  if (nameUtf == nullptr) return nullptr;

  jsize len = env->GetArrayLength(flatData);
  if (len == 0 || rows <= 0 || cols <= 0) { env->ReleaseStringUTFChars(modelName, nameUtf); return nullptr; }

  jdouble* data = env->GetDoubleArrayElements(flatData, nullptr);
  if (data == nullptr) { env->ReleaseStringUTFChars(modelName, nameUtf); return nullptr; }

  int outRows = 0, outCols = 0;
  double* outData = rf_predict_probabilities_named(nameUtf, data, rows, cols, &outRows, &outCols);

  env->ReleaseDoubleArrayElements(flatData, data, JNI_ABORT);
  env->ReleaseStringUTFChars(modelName, nameUtf);

  if (outData == nullptr || outRows <= 0 || outCols <= 0) {
    return nullptr;
  }

  jsize total = outRows * outCols;
  jdoubleArray result = env->NewDoubleArray(total);
  if (result != nullptr) {
    env->SetDoubleArrayRegion(result, 0, total, outData);
  }

  rf_free_data(outData);
  return result;
#else
  (void)env; (void)modelName; (void)flatData; (void)rows; (void)cols;
  logDebug("nativePredictProbabilitiesNamed(): RF_USE_RUST disabled, using Kotlin fallback");
  return nullptr;
#endif
}

JNIEXPORT jintArray JNICALL
Java_com_workoutpose_randomforest_RandomForestNative_nativeGetClassIdsNamed(
    JNIEnv* env, jclass /*clazz*/, jstring modelName) {
#if defined(RF_USE_RUST)
  const char* nameUtf = env->GetStringUTFChars(modelName, nullptr);
  if (nameUtf == nullptr) return nullptr;

  int outLen = 0;
  int* ids = rf_get_class_ids_named(nameUtf, &outLen);
  env->ReleaseStringUTFChars(modelName, nameUtf);

  if (ids == nullptr || outLen <= 0) return nullptr;

  jintArray result = env->NewIntArray(outLen);
  if (result != nullptr) {
    env->SetIntArrayRegion(result, 0, outLen, ids);
  }

  rf_free_ints(ids);
  return result;
#else
  (void)env; (void)modelName;
  logDebug("nativeGetClassIdsNamed(): RF_USE_RUST disabled, using Kotlin fallback");
  return nullptr;
#endif
}

JNIEXPORT void JNICALL
Java_com_workoutpose_randomforest_RandomForestNative_nativeUnloadModelNamed(
    JNIEnv* env, jclass /*clazz*/, jstring modelName) {
#if defined(RF_USE_RUST)
  const char* nameUtf = env->GetStringUTFChars(modelName, nullptr);
  if (nameUtf == nullptr) return;
  rf_unload_model_named(nameUtf);
  env->ReleaseStringUTFChars(modelName, nameUtf);
#else
  (void)env; (void)modelName;
  logDebug("nativeUnloadModelNamed(): RF_USE_RUST disabled, using Kotlin fallback");
#endif
}

} // extern "C"
