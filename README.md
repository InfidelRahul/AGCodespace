# AGCodespace

AGCodespace is an Android app project for a persistent, rootless Ubuntu ARM64 userspace driven by the LinuxDroidapp PRoot runtime. The project includes a Compose-based mobile UI, a JNI POSIX PTY bridge, Ubuntu Base download/checksum/extraction, PRoot process launch configuration, and a GitHub Actions build pipeline.

## Main capabilities in this source tree

- Android ARM64 app (minimum API 28) with workspace, terminal, files and diagnostics screens.
- Ubuntu Base 26.04 ARM64 bootstrap from the official Ubuntu image directory.
- Downloads and verifies the rootfs archive against Ubuntu's published `SHA256SUMS` before extraction; extracts into private persistent app storage and rejects unsafe archive paths/symlinks.
- Native POSIX PTY using `forkpty`, nonblocking output reads, streamed keyboard input, resize ioctl, and process termination.
- Compact VT screen model with cursor addressing, erase, scroll and alternate-screen basics, plus a touch-oriented modifier/key bar.
- Basic navigable rootfs file browser.
- A user-started special-use foreground service to help keep the PTY/SSH process alive while the app is backgrounded (requires Play Console policy review and device testing).
- Remote workflow controls for GitHub CLI authentication, Codespaces SSH, official Antigravity CLI installation, and opening URLs detected in terminal output.
- PRoot runtime is built from the pinned `LinuxDroidapp/proot` source revision in CI, rather than bundling an unrelated prebuilt executable.
- GitHub Actions builds and checks the ARM64 APK, generates checksums, and uploads the artifact with `retention-days: 1`.

## Build through GitHub Actions

Push this project to a GitHub repository and run **Build AGCodespace Android app** from Actions. CI installs Java 21, Android API 36, NDK 30.0.16138531 and CMake 3.22.1, builds PRoot from the pinned source, packages its native carrier libraries, runs unit tests, builds the release variant and uploads the artifact for one day.

For a Google Play upload, configure a private signing keystore and set these environment variables in your secure build environment: `AGCODESPACE_KEYSTORE_PATH`, `AGCODESPACE_KEYSTORE_PASSWORD`, `AGCODESPACE_KEY_ALIAS`, and `AGCODESPACE_KEY_PASSWORD`. Never commit the keystore or passwords. Without those values, Gradle produces an unsigned release APK that is useful for validation but cannot be uploaded to Play Console as a release.

## Important engineering status

This is an actively implemented engineering build, not yet a verified Play Store release. The source includes a real PTY and rootfs installer, but the current Compose terminal output surface is not a complete VT100/xterm-compatible renderer. Full-screen terminal applications such as `nano` and `vim`, alternate-screen handling, cursor movement, ANSI color rendering, terminal resize propagation, long-running background session supervision, and device-level validation still need to be completed before advertising production readiness. The native runtime is assembled by CI, so a plain local build without the workflow's PRoot packaging step does not contain the PRoot runtime.

PRoot is a userspace compatibility layer, not a virtual machine or a real root environment. Android kernel policy may restrict some Linux behaviors, including systemd/PID 1, privileged mounts, kernel modules, nested containers, and some Secret Service/D-Bus use cases.

## Third-party components

- PRoot source: `https://github.com/LinuxDroidapp/proot` (pinned commit is recorded in `.github/workflows/build-release-apk.yml`; see its license and notices before redistribution).
- Ubuntu Base ARM64: `https://cdimages.ubuntu.com/cdimage/ubuntu-base/releases/resolute/release/`.
- Apache Commons Compress is used for safe tar extraction; see its Apache-2.0 license.

Review all applicable notices and licensing obligations before publishing.
