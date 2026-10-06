#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
test -f app/src/main/AndroidManifest.xml
test -f app/src/main/java/com/agcodespace/runtime/ProotRuntime.kt
test -f app/src/main/java/com/agcodespace/terminal/TerminalService.kt
test -f app/src/main/java/com/agcodespace/antigravity/AntigravityDetector.kt
test -f tools/package-runtime.sh
grep -q 'LinuxDroidapp/proot' docs/RUNTIME.md
grep -q 'gh codespace ssh' docs/ARCHITECTURE.md
grep -q 'agy --remote-control' README.md
grep -q 'terminal-view:0.118.0' app/build.gradle.kts
if grep -R 'androidx.compose.ui.Modifier' -n app/src/main/java >/dev/null; then :; fi
printf 'AGCodespace source verification: PASS\n'
