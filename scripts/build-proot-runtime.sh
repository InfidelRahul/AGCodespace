#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROOT_DIR="${PROOT_DIR:-$ROOT/.work/proot}"
PROOT_REVISION="${PROOT_REVISION:-caadcae0e7697ec29f02e231a3a88866561aacd0}"

if [[ ! -d "$PROOT_DIR/.git" ]]; then
  mkdir -p "$(dirname "$PROOT_DIR")"
  git clone --filter=blob:none https://github.com/LinuxDroidapp/proot.git "$PROOT_DIR"
fi
git -C "$PROOT_DIR" fetch --depth=1 origin "$PROOT_REVISION"
git -C "$PROOT_DIR" checkout --detach "$PROOT_REVISION"
(
  cd "$PROOT_DIR"
  ./gradlew assembleAndroidDist --no-daemon
)

DIST="$PROOT_DIR/dist/android/arm64-v8a"
DEST="$ROOT/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$DEST"
for name in libproot.so libproot_loader.so libprootloader.so libtalloc.so libandroid-shmem.so liblinuxdroidspawn.so libpocketspawn.so; do
  [[ ! -f "$DIST/$name" ]] || install -m 0755 "$DIST/$name" "$DEST/$name"
done
for name in libproot.so libproot_loader.so; do
  [[ -s "$DEST/$name" ]] || { echo "Missing required PRoot component: $name" >&2; exit 1; }
done
mkdir -p "$ROOT/app/src/main/assets/runtime"
(cd "$DEST" && sha256sum *.so > "$ROOT/app/src/main/assets/runtime/RUNTIME-SHA256SUMS")
echo "PRoot runtime staged at $DEST"
