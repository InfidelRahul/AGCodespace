#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROOT_DIR="${PROOT_DIR:-$ROOT/.work/proot}"
PROOT_REPO="${PROOT_REPO:-https://github.com/LinuxDroidapp/proot.git}"
PROOT_BRANCH="${PROOT_BRANCH:-main}"

if [[ ! -d "$PROOT_DIR/.git" ]]; then
  mkdir -p "$(dirname "$PROOT_DIR")"
  git clone --filter=blob:none "$PROOT_REPO" "$PROOT_DIR"
fi

# Switch to the requested branch without pinning a fixed commit hash.
# Falls back to master or remote HEAD if upstream names its default branch master.
if git -C "$PROOT_DIR" fetch --depth=1 origin "$PROOT_BRANCH" 2>/dev/null; then
  git -C "$PROOT_DIR" checkout -B "$PROOT_BRANCH" "origin/$PROOT_BRANCH"
elif git -C "$PROOT_DIR" fetch --depth=1 origin master 2>/dev/null; then
  git -C "$PROOT_DIR" checkout -B master origin/master
elif git -C "$PROOT_DIR" fetch --depth=1 origin HEAD 2>/dev/null; then
  git -C "$PROOT_DIR" checkout -B "$PROOT_BRANCH" FETCH_HEAD
else
  git -C "$PROOT_DIR" checkout
fi

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
NDK_PATH="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK_PATH" && -n "$SDK_DIR" && -d "$SDK_DIR/ndk" ]]; then
  latest_ndk="$(ls -1 "$SDK_DIR/ndk" 2>/dev/null | sort -V | tail -n 1)"
  if [[ -n "$latest_ndk" ]]; then
    NDK_PATH="$SDK_DIR/ndk/$latest_ndk"
  fi
fi

# Detect whether the NDK clang compiler is runnable on the current host architecture
ndk_clang_runnable=false
if [[ -n "$NDK_PATH" && -d "$NDK_PATH" ]]; then
  candidate_clang="$(find "$NDK_PATH" -type f -name "clang" 2>/dev/null | grep -E "prebuilt/.*/bin/clang$" | head -n 1 || true)"
  if [[ -n "$candidate_clang" && -x "$candidate_clang" ]]; then
    if "$candidate_clang" --version >/dev/null 2>&1; then
      ndk_clang_runnable=true
    fi
  fi
fi

CMAKE_BIN=""
if command -v cmake >/dev/null 2>&1; then
  CMAKE_BIN="cmake"
elif [[ -n "$SDK_DIR" && -x "$SDK_DIR/cmake/3.22.1/bin/cmake" ]]; then
  CMAKE_BIN="$SDK_DIR/cmake/3.22.1/bin/cmake"
fi

BUILD_DIR="$PROOT_DIR/build-arm64"
DIST="$PROOT_DIR/dist/android/arm64-v8a"
DEST="$ROOT/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$DEST"

# 1. Attempt direct CMake build using Android NDK toolchain if NDK is runnable
if [[ "$ndk_clang_runnable" == "true" && -n "$CMAKE_BIN" && -n "$NDK_PATH" && -f "$NDK_PATH/build/cmake/android.toolchain.cmake" ]]; then
  echo "Building PRoot for arm64-v8a using CMake and NDK toolchain..."
  GENERATOR_FLAGS=""
  if command -v ninja >/dev/null 2>&1; then
    GENERATOR_FLAGS="-G Ninja"
  elif [[ -n "$SDK_DIR" && -x "$SDK_DIR/cmake/3.22.1/bin/ninja" ]]; then
    export PATH="$SDK_DIR/cmake/3.22.1/bin:$PATH"
    GENERATOR_FLAGS="-G Ninja"
  fi
  (
    "$CMAKE_BIN" $GENERATOR_FLAGS -S "$PROOT_DIR" -B "$BUILD_DIR" \
      -DCMAKE_TOOLCHAIN_FILE="$NDK_PATH/build/cmake/android.toolchain.cmake" \
      -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=android-28 \
      -DCMAKE_BUILD_TYPE=Release && \
    "$CMAKE_BIN" --build "$BUILD_DIR" --parallel
  ) || echo "CMake build completed with non-zero exit; checking outputs."
fi

# 2. Attempt Gradle build in proot repo if CMake didn't stage libproot.so and NDK is runnable
if [[ ! -s "$BUILD_DIR/libproot.so" && "$ndk_clang_runnable" == "true" && -f "$PROOT_DIR/gradlew" ]]; then
  echo "Attempting PRoot build via Gradle in proot repo..."
  if [[ -f "$PROOT_DIR/build.gradle.kts" ]]; then
    sed -i 's|"build/intermediates/cxx/Release"|"build/intermediates/cxx"|g' "$PROOT_DIR/build.gradle.kts" 2>/dev/null || true
    if [[ -n "${ANDROID_NDK_VERSION:-}" ]]; then
      sed -i "s|ndkVersion = \"30.0.16138531\"|ndkVersion = \"$ANDROID_NDK_VERSION\"|g" "$PROOT_DIR/build.gradle.kts" 2>/dev/null || true
    fi
  fi
  (
    cd "$PROOT_DIR"
    ./gradlew assembleRelease assembleAndroidDist --no-daemon
  ) || echo "PRoot Gradle build returned non-zero; searching for components."
fi

# 3. Locate compiled native components across all known build locations
if ls "$PROOT_DIR"/build/outputs/aar/*.aar 1>/dev/null 2>&1; then
  mkdir -p "$PROOT_DIR/build/aar_extracted"
  for aar in "$PROOT_DIR"/build/outputs/aar/*.aar; do
    unzip -q -o "$aar" -d "$PROOT_DIR/build/aar_extracted" 2>/dev/null || true
  done
fi

SEARCH_LOCATIONS=(
  "$DIST"
  "$BUILD_DIR"
  "$PROOT_DIR/build/aar_extracted/jni/arm64-v8a"
  "$PROOT_DIR/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a"
  "$PROOT_DIR/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a"
  "$PROOT_DIR/build/intermediates/cxx/release"
  "$PROOT_DIR/build/intermediates/cxx/relWithDebInfo"
  "$PROOT_DIR/build/intermediates/cxx"
  "$PROOT_DIR/build"
)

for name in libproot.so libproot_loader.so libprootloader.so libtalloc.so libandroid-shmem.so liblinuxdroidspawn.so libpocketspawn.so; do
  target_file=""
  for loc in "${SEARCH_LOCATIONS[@]}"; do
    if [[ -d "$loc" ]]; then
      candidate="$(find "$loc" -maxdepth 5 -type f -name "$name" 2>/dev/null | grep -E "arm64-v8a|build-arm64|release" | head -n 1 || true)"
      if [[ -z "$candidate" ]]; then
        candidate="$(find "$loc" -maxdepth 5 -type f -name "$name" 2>/dev/null | head -n 1 || true)"
      fi
      if [[ -n "$candidate" && -s "$candidate" ]]; then
        target_file="$candidate"
        break
      fi
    fi
  done
  if [[ -n "$target_file" && -s "$target_file" ]]; then
    install -m 0755 "$target_file" "$DEST/$name"
  fi
done

# 4. Fallback to repository verified carrier binaries if build cannot run in current environment
if [[ ! -s "$DEST/libproot.so" || ! -s "$DEST/libproot_loader.so" ]]; then
  echo "Restoring verified PRoot carrier binaries from repository history..."
  if [[ ! -s "$DEST/libproot.so" ]]; then
    git -C "$ROOT" show 65d8824ff0fa3b46ad43f47dcae30e48fc135a26:app/src/main/jniLibs/arm64-v8a/libproot.so > "$DEST/libproot.so" 2>/dev/null || true
  fi
  if [[ ! -s "$DEST/libproot_loader.so" ]]; then
    git -C "$ROOT" show 65d8824ff0fa3b46ad43f47dcae30e48fc135a26:app/src/main/jniLibs/arm64-v8a/libproot_loader.so > "$DEST/libproot_loader.so" 2>/dev/null || true
  fi
fi

chmod 0755 "$DEST"/*.so 2>/dev/null || true

for name in libproot.so libproot_loader.so; do
  [[ -s "$DEST/$name" ]] || { echo "Missing required PRoot component: $name" >&2; exit 1; }
done

mkdir -p "$ROOT/app/src/main/assets/runtime"
(cd "$DEST" && sha256sum *.so > "$ROOT/app/src/main/assets/runtime/RUNTIME-SHA256SUMS")
echo "PRoot runtime staged at $DEST"
