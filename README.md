# WorkoutHacker Revamp

Native Kotlin + Jetpack Compose rewrite of WorkoutHacker. Replaces the old
React Native app and the `react-native-pose-landmarks` module.

## Modules

**`app`** — Compose UI. Preview + skeleton overlay + joint-angle readout,
model/delegate/camera/filter controls, camera permission flow.

**`workout-pose`** — reusable Android library:
- `WorkoutPoseManager` — CameraX Preview + ImageAnalysis binding, exposes
  `StateFlow<PoseFrame> poseState`.
- `PoseLandmarkerHelper` — MediaPipe `tasks-vision` LiveStream wrapper
  (`pose_landmarker_lite.task` bundled in `app/src/main/assets/`).
- `WorkoutPoseEngine` — One Euro filtering, visibility recovery, motion prediction.
- `PoseGeometry` — angles, visibility, skeleton connections.
- `PoseSkeletonOverlay` — Compose canvas overlay.

## Build

```
./gradlew :app:assembleDebug
./gradlew :workout-pose:assembleDebug   # library AAR
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

No emulator/device is available in the build environment, so runtime behavior
has not been verified on-device yet.

## Configuration

`PoseScreen` controls (defaults):
- Model: Lite (Full requires `pose_landmarker_full.task` in assets and config change)
- Delegate: CPU (GPU supported on device)
- Camera: Front (mirrored overlay)
- Filters: One Euro on, visibility recovery on
- Changes take effect via **Apply & Restart**.

`PoseFrame` readout: pose visibility, visible landmark count, inference ms,
and 8 joint angles (L/R hip, knee, elbow, shoulder; `--` = not measured).