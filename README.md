# WorkoutHacker Revamp

Native Kotlin + Jetpack Compose rewrite of WorkoutHacker. Replaces the old
React Native app and the `react-native-pose-landmarks` module.

## Modules

**`app`** — Compose UI + CameraX lifecycle owner.
- `WorkoutCameraController` — binds CameraX `Preview` + `ImageAnalysis` to the
  lifecycle, converts/rotates frames, and feeds upright bitmaps to the library.
- `PoseScreen` — preview + skeleton overlay + joint-angle readout,
  model/delegate/camera/filter controls, camera permission flow.

**`workout-pose`** — reusable Android library (no CameraX dependency):
- `PoseAnalyzer` — consumes upright `Bitmap`s, runs MediaPipe pose detection
  in LIVE_STREAM mode, applies engine post-processing, exposes
  `StateFlow<PoseFrame> poseState`.
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