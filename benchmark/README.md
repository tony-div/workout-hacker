# Pose-Latency Benchmark

Compares responsiveness between the **revamped app** (`workout-hacker` revamp,
Kotlin/Compose) and the **old React Native module** (`react-native-pose-landmarks`
example app).

## The only metric: frame-to-frame latency (`ds`)

`ds` = elapsed time between consecutive **UI update points** = the gap between the
**current rendered frame and the latest/previous rendered frame**. Each app
timestamps **after One Euro smoothing and pushing the pose to the UI**
(`setLandmarks` / pose-state write), so `ds` covers the full pipeline up to and
including the render handoff — the perceived responsiveness users actually feel.

- Lower `ds` ⇒ more responsive (skeleton updates faster).
- `ds` ≫ 33ms ⇒ frames being dropped / pipeline under pressure.

Derived offline from the per-frame `ts` (uptime at result); no other on-device field
is used by the analysis.

## Slice

`analyze.py` slices to frames where the upper body is **visible AND moving**
(`ub_vis` ≥ `--min-vis` of 8 upper-body landmarks, `motion` ≥ `--motion`), then
reports `ds` percentiles (mean, p50, p90, p95, p99, max) + inferred display fps for
that slice. `ub_vis`/`motion` are read **only** to select frames — they are not
reported metrics.

```bash
./run_benchmark.sh --app revamp --duration 90 --out bench-revamp.log
./run_benchmark.sh --app rn     --duration 90 --out bench-rn.log
python3 analyze.py --logs bench-revamp.log bench-rn.log --min-vis 6 --motion 0.005
```

## Log format (on-device, unchanged)

Each processed frame emits one line via the `PosePerf` log tag:

```
ts=<uptimeMs> inf=<ms> backlog=<n> ub_vis=<0-8> motion=<norm/frame>
```

- `ts` — uptime just after the UI update (post One Euro smoothing + pose pushed to UI);
  used to derive `ds`.
- `ub_vis` — count of upper-body landmarks (shoulders, elbows, wrists, hips = indices
  11,12,13,14,15,16,23,24) with visibility ≥ 0.8. Used only for slicing.
- `motion` — summed per-frame displacement of those upper-body landmarks (normalized
  units). Used only for slicing.
- `inf` / `backlog` — logged but **not analyzed** (historical; kept for the verbosity
  of the on-device line).

Revamp logs by default (`enableBenchmarkLogging = true`); it can be silenced by
turning off **Benchmark logging** in `PoseScreen` settings (production build). The RN
module emits it only in benchmark builds (src-only flag flipped to `true`, never
shipped).

## Fairness protocol

- Same physical device, camera, lighting, person, and routine for every run.
- Revamp: front camera (default), Core/Lite model, CPU delegate, filters at default ON.
- RN example: front camera, Lite model, CPU delegate, filters at default ON.
- Run `run_benchmark.sh` sequentially per app (not side-by-side) so each gets the
  full device.
- Warmup window discarded; logcat cleared right before the measured window.
- Repeat 3 rounds per app for variance.

### Known confounds (accepted)

- **CameraX resolutions** are left at platform defaults on both apps (deliberately).
- **One Euro defaults differ** between apps (RN 1.0/0.009 vs revamp 4.0/0.06/2.0),
  so the raw per-frame `motion` values differ. Because each frame's `motion`/`ub_vis`
  is logged, slicing is a pure post-processing step and can be re-tuned without
  re-running on-device.

## Usage

```bash
# 1. Build & install both apps. Revamp logs `PosePerf` by default (no setup needed);
#    optionally silence via Settings -> Benchmark logging.

# 2. Capture logs (one device; run each app's session sequentially):
./run_benchmark.sh --app revamp --duration 90 --out bench-revamp.log
./run_benchmark.sh --app rn     --duration 90 --out bench-rn.log

# 3. Analyze the "upper body visible and moving" slice (frame-to-frame latency):
python3 analyze.py --logs bench-revamp.log bench-rn.log --min-vis 6 --motion 0.005

# Optional CSV for external plotting:
python3 analyze.py --logs bench-revamp.log bench-rn.log --csv all.csv
```

## Files

- `run_benchmark.sh` — drive adb logcat capture for one app.
- `analyze.py` — parse, slice, frame-to-frame latency stats + comparison, CSV export.

## Results log

| date | device | app | `ds` p50 (ms) | `ds` p90 (ms) | `ds` p99 (ms) | disp fps | n |
|---|---|---|---|---|---|---|---|
| 2026-09-06 | Xiaomi 23021RAAEG, Android 15 | revamp | 83.0 | 110.0 | 181.0 | 11.1 | 2931 |
| 2026-09-06 | Xiaomi 23021RAAEG, Android 15 | rn | 88.0 | 117.0 | 174.0 | 10.9 | 2727 |
| _(fill in)_ | | | | | | | |