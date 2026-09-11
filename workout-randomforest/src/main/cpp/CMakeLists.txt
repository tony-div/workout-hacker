cmake_minimum_required(VERSION 3.22.1)

project(workout_randomforest)

set(PACKAGE_NAME workout_randomforest)
set(CMAKE_VERBOSE_MAKEFILE ON)
set(CMAKE_CXX_STANDARD 17)

add_library(${PACKAGE_NAME} SHARED
        workout_randomforest_jni.cpp
)

# Link the vendored Rust staticlib when a prebuilt exists for this ABI.
# (Rebuild it with ../../build-rust-android.sh after touching ../../rust/.)
# When absent, the JNI layer compiles without RF_USE_RUST and every native
# call reports failure (Rust-only by design — no fallback engine).
if(ANDROID_ABI STREQUAL "arm64-v8a")
  set(RUST_TARGET "aarch64-linux-android")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
  set(RUST_TARGET "armv7-linux-androideabi")
elseif(ANDROID_ABI STREQUAL "x86")
  set(RUST_TARGET "i686-linux-android")
elseif(ANDROID_ABI STREQUAL "x86_64")
  set(RUST_TARGET "x86_64-linux-android")
else()
  set(RUST_TARGET "")
endif()

set(RUST_LIB_PATH "")
if(NOT RUST_TARGET STREQUAL "")
  set(RUST_LIB_PATH "${CMAKE_CURRENT_SOURCE_DIR}/../prebuilt/${ANDROID_ABI}/librandom_forest_rust.a")
  if(NOT EXISTS "${RUST_LIB_PATH}")
    set(RUST_LIB_PATH "${CMAKE_CURRENT_SOURCE_DIR}/../../../rust/target/${RUST_TARGET}/release/librandom_forest_rust.a")
  endif()
  if(EXISTS "${RUST_LIB_PATH}")
    target_compile_definitions(${PACKAGE_NAME} PRIVATE RF_USE_RUST=1)
  endif()
endif()

find_library(LOG_LIB log)

target_link_libraries(
        ${PACKAGE_NAME}
        ${LOG_LIB}
        android
)

if(NOT RUST_LIB_PATH STREQUAL "" AND EXISTS "${RUST_LIB_PATH}")
  target_link_libraries(${PACKAGE_NAME} ${RUST_LIB_PATH})
endif()
