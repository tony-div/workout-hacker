# AGENTS.md

## Code exploration
Always use the **Codanna MCP** tools (codanna_*) to answer questions about this
codebase — symbol search, semantic search, call graphs, and impact analysis.
Do **not** delegate to explore subagents or read files directly for exploration;
prefer codanna_* tools (e.g. `codanna_semantic_search_with_context`,
`codanna_find_symbol`, `codanna_analyze_impact`) to anchor on the right APIs
first, then read the specific file only to confirm details.

## Project
`workout-hacker-revamp` — native Kotlin + Jetpack Compose Android app built on top of a
reusable `workout-pose` android library. Replaces the old React Native
`react-native-pose-landmarks` (reference implementation only).

## Module layout
- `app/` — Compose UI (`com.workouthacker.revamp`) + CameraX lifecycle ownership
  (`WorkoutCameraController`). Depends on `project(":workout-pose")` and
  owns `camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`.
- `workout-pose/` — pure library (`com.workoutpose`): CameraX-free pose consumer
  (`PoseAnalyzer`), MediaPipe `tasks-vision` LiveStream pose landmarking,
  One Euro smoothing, visibility recovery, joint angles, Compose skeleton overlay.
  No CameraX dependency by design; fed upright bitmaps from the host app.

## Build / verify
Use the wrapper — the system `gradle` is 4.4.1 and must NOT be used.

- NEVER run builds (`./gradlew assemble*`, `build`, `lint`, tests) autonomously.
  Only write/verify code; the user runs all builds themselves.
- Build commands (for user reference only — do NOT execute):
  - `./gradlew :app:assembleDebug` — build debug APK.
  - `./gradlew :workout-pose:assembleDebug` — build library AAR.
  - `./gradlew :app:lintDebug` — lint.
- No `test` sources yet.

Version catalog: `gradle/libs.versions.toml` (single source of truth).

## Conventions / gotchas
- Min SDK 24, target/compile SDK 35/36. Keep `targetSdk` >= 35.
- CameraX core/camera2/view are in the `app` module only. The `workout-pose` library
  has no CameraX dependency — it is a pure frame consumer (fed upright bitmaps).
- `PoseFrame.landmarks` is a flat 132-value buffer: 33 × (x, y, z, visibility),
  normalized MediaPipe space (x right, y down). New convention — do NOT flip
  like the old RN module did.
- `JointAngles` = 8 values (L/R hip, knee, elbow, shoulder) in degrees; `-1f` = unmeasured.
- `poseVisible` = visible landmark count >= 10 (`PoseGeometry.MIN_POSE_VISIBLE`).
- Overlay mirroring defaults to `true` (front camera convention).
- First build downloads Gradle 8.13 dist (~120MB) + deps; Compose artifacts were not
  cached on this machine, so a fresh-deps build needs network.
- No emulator/AVD or attached device on this machine — verification is compile-only.