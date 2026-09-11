#!/usr/bin/env bash
# Rebuilds the vendored Rust staticlib for all Android ABIs and refreshes
# src/main/prebuilt/<abi>/librandom_forest_rust.a (checked into git, ~80MB).
# Run after touching rust/. Requires cargo + rustup + cargo-ndk + Android NDK.
# Gradle builds never invoke this — they link whatever prebuilt is present and
# compile the JNI layer without RF_USE_RUST when it is missing.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUST_DIR="$ROOT_DIR/rust"
PREBUILT_DIR="$ROOT_DIR/src/main/prebuilt"

ABI_MAP=(
  "aarch64-linux-android:arm64-v8a"
  "armv7-linux-androideabi:armeabi-v7a"
  "i686-linux-android:x86"
  "x86_64-linux-android:x86_64"
)

check_stale() {
  for pair in "${ABI_MAP[@]}"; do
    abi="${pair##*:}"
    prebuilt="$PREBUILT_DIR/$abi/librandom_forest_rust.a"
    if [ -f "$prebuilt" ]; then
      if [ "$prebuilt" -nt "$RUST_DIR/Cargo.toml" ] && [ "$prebuilt" -nt "$RUST_DIR/src/lib.rs" ] && [ "$prebuilt" -nt "$RUST_DIR/src/rf_model.rs" ]; then
        continue
      fi
    fi
    return 0
  done
  return 1
}

if ! check_stale; then
  echo "build-rust-android: prebuilt binaries are up to date"
  exit 0
fi

command -v cargo >/dev/null 2>&1 || { echo "build-rust-android: cargo not found, keeping existing prebuilt"; exit 0; }
command -v rustup >/dev/null 2>&1 || { echo "build-rust-android: rustup not found, keeping existing prebuilt"; exit 0; }

for pair in "${ABI_MAP[@]}"; do
  target="${pair%%:*}"
  abi="${pair##*:}"
  rustup target add "$target" >/dev/null 2>&1 || true
  cargo ndk --manifest-path "$RUST_DIR/Cargo.toml" --target "$target" --platform 24 build --release
  mkdir -p "$PREBUILT_DIR/$abi"
  cp "$RUST_DIR/target/$target/release/librandom_forest_rust.a" "$PREBUILT_DIR/$abi/"
  echo "build-rust-android: refreshed $abi"
done
