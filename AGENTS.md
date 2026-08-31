# AGENTS.md

## Project
`workout-hacker-revamp` — native Kotlin + Jetpack Compose Android app built on top of a
reusable `workout-pose` android library. Replaces the old React Native
`react-native-pose-landmarks` (reference implementation only).

## Module layout
- `app/` — Compose UI (`com.workouthacker.revamp`), depends on `project(":workout-pose")`.
- `workout-pose/` — pure library (`com.workoutpose`): CameraX binding,
  MediaPipe `tasks-vision` LiveStream pose landmarking, One Euro smoothing,
  joint angles, Compose skeleton overlay.

## Build / verify
Use the wrapper — the system `gradle` is 4.4.1 and must NOT be used.

- `./gradlew :app:assembleDebug` — build debug APK.
- `./gradlew :workout-pose:assembleDebug` — build library AAR.
- `./gradlew :app:lintDebug` — lint.
- No `test` sources yet.

Version catalog: `gradle/libs.versions.toml` (single source of truth).

## Conventions / gotchas
- Min SDK 24, target/compile SDK 35/36. Keep `targetSdk` >= 35.
- CameraX core/camera2/view are `api` in `workout-pose` because they leak into
  public signatures (`WorkoutPoseManager.start`, `WorkoutPoseConfig.lensFacing`).
  Add new leaked types as `api`, not `implementation`.
- `PoseFrame.landmarks` is a flat 132-value buffer: 33 × (x, y, z, visibility),
  normalized MediaPipe space (x right, y down). New convention — do NOT flip
  like the old RN module did.
- `JointAngles` = 8 values (L/R hip, knee, elbow, shoulder) in degrees; `-1f` = unmeasured.
- `poseVisible` = visible landmark count >= 10 (`PoseGeometry.MIN_POSE_VISIBLE`).
- Overlay mirroring defaults to `true` (front camera convention).
- First build downloads Gradle 8.13 dist (~120MB) + deps; Compose artifacts were not
  cached on this machine, so a fresh-deps build needs network.
- No emulator/AVD or attached device on this machine — verification is compile-only.