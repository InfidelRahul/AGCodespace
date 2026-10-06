#!/usr/bin/env bash
set -euo pipefail
# Runtime packager for release builds. It pins the requested LinuxDroidapp/proot source.
# Build prerequisites: Android SDK/NDK, CMake, a host tar/xz implementation, and a prepared
# Linux rootfs for each ABI. The script intentionally does not download an unverified rootfs.
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/app/src/main/assets/runtime"
PROOT_REPO="https://github.com/LinuxDroidapp/proot.git"
PROOT_COMMIT="caadcae0e7697ec29f02e231a3a88866561aacd0"
ABI="${1:-arm64-v8a}"
ROOTFS="${2:-}"
UBUNTU_BASE_URL="https://cdimage.ubuntu.com/ubuntu-base/releases/resolute/release/ubuntu-base-26.04.1-base-arm64.tar.gz"
UBUNTU_BASE_SHA256="5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd"
mkdir -p "$OUT/$ABI"
if [ -z "$ROOTFS" ]; then
  echo "Usage: $0 arm64-v8a <prepared-rootfs-directory>" >&2
  echo "Expected source archive: $UBUNTU_BASE_URL" >&2
  exit 2
fi
if [ "$ABI" != "arm64-v8a" ]; then
  echo "This release currently targets the supplied Ubuntu Base ARM64 rootfs; use arm64-v8a." >&2
  exit 2
fi
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
command -v git >/dev/null || { echo git required; exit 3; }
git clone --depth 1 "$PROOT_REPO" "$TMP/proot"
git -C "$TMP/proot" fetch --depth 1 origin "$PROOT_COMMIT"
git -C "$TMP/proot" checkout --detach "$PROOT_COMMIT"
# The exact native build is delegated to the checked-out LinuxDroidapp/proot CMake project.
# NDK toolchain variables can be supplied through CMAKE_TOOLCHAIN_FILE and ANDROID_ABI.
cmake -S "$TMP/proot" -B "$TMP/build" \
  -DCMAKE_TOOLCHAIN_FILE="${ANDROID_NDK_HOME:?}/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release
cmake --build "$TMP/build" --target proot --parallel
cp "$TMP/build/proot" "$OUT/$ABI/proot"
chmod 755 "$OUT/$ABI/proot"
# Copy the prepared guest rootfs into assets. A release pipeline must hash/sign this artifact.
rm -rf "$OUT/$ABI/rootfs"
tar -C "$ROOTFS" -cJf "$OUT/$ABI/rootfs.tar.xz" .
printf '%s  %s\n' "$UBUNTU_BASE_SHA256" "ubuntu-base-26.04.1-base-arm64.tar.gz" > "$OUT/$ABI/ROOTFS.SHA256"
printf '%s\n' "$UBUNTU_BASE_URL" > "$OUT/$ABI/ROOTFS.URL"
echo "Packaged $ABI from LinuxDroidapp/proot@$PROOT_COMMIT"
