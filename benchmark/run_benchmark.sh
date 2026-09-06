#!/usr/bin/env bash
# Run a pose-latency benchmark session against one app and capture PosePerf logcat lines.
#
# Usage:
#   ./run_benchmark.sh --app revamp --duration 90 [--warmup 2] [--rounds 3] [--out revamp.log]
#
# The app must already be installed. The revamp logs PosePerf by default; the RN
# module has the logging compiled in for the benchmark build.
set -euo pipefail

usage() {
  grep '^# Usage' "$0" >/dev/null 2>&1 && sed -n '/^# Usage/,/^$/p' "$0" | sed 's/^# //'
  exit 1
}

APP=""
DURATION=90
WARMUP=2
ROUNDS=3
OUT=""
DEVICE=""

# app id to launch activity "intent action" used for switch.
declare -A PACKAGE=(
  [revamp]="com.workouthacker.revamp"
  [rn]="com.poselandmarksexample"
)

# Revamp PoseScreen has the benchmark toggle; the RN module logs unconditionally (bench build).
# Revamp main activity:
declare -A ACTIVITY=(
  [revamp]="com.workouthacker.revamp/.MainActivity"
  [rn]="com.poselandmarksexample/.MainActivity"
)

while [[ $# -gt 0 ]]; do
  case "$1" in
    --app) APP="$2"; shift 2 ;;
    --duration) DURATION="$2"; shift 2 ;;
    --warmup) WARMUP="$2"; shift 2 ;;
    --rounds) ROUNDS="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --device) DEVICE="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "Unknown option: $1" >&2; usage ;;
  esac
done

if [[ -z "$APP" || -z "${PACKAGE[$APP]:-}" ]]; then
  echo "error: --app must be 'revamp' or 'rn'" >&2
  usage
fi

ADB=(adb)
[[ -n "$DEVICE" ]] && ADB+=( -s "$DEVICE" )

OUT="${OUT:-bench-$APP.log}"
: > "$OUT"

echo "==> Benchmarking $APP (pkg ${PACKAGE[$APP]})"
echo "    rounds=$ROUNDS duration=${DURATION}s warmup=${WARMUP}s out=$OUT"

force_stop() {
  "${ADB[@]}" shell am force-stop "${PACKAGE[$APP]}" >/dev/null 2>&1 || true
}

launch() {
  "${ADB[@]}" shell monkey -p "${PACKAGE[$APP]}" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 \
    || "${ADB[@]}" shell am start -n "${ACTIVITY[$APP]}" >/dev/null 2>&1
}

for round in $(seq 1 "$ROUNDS"); do
  echo "==> Round $round/$ROUNDS"
  force_stop
  sleep 2

  # Warm up: short session whose trailing window is allowed to retain cached logs? We clear
  # logcat right before the measured window so warmup lines are dropped.
  launch
  sleep "$WARMUP"

  echo "==> clearing logcat"
  "${ADB[@]}" logcat -c || true

  sleep "$DURATION"

  echo "==> capturing PosePerf lines"
  {
    echo "# round=$round app=$APP ts=$(date +%s)"
    "${ADB[@]}" logcat -d -s PosePerf:*
  } >> "$OUT"

  force_stop
  sleep 3
done

echo "==> done. Appended $ROUNDS round(s) to $OUT"
echo "    Next: python3 analyze.py --logs $OUT"
