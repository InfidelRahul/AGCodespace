# AGCodespace

AGCodespace is an Android-native development client for a real Linux userspace under PRoot, GitHub Codespaces over the official GitHub CLI/SSH flow, and Antigravity CLI Remote Control.

## Implemented architecture

- Real Android foreground service owns the terminal process so an interactive session can survive activity recreation/backgrounding better than an activity-only PTY.
- Full terminal emulation/input is provided by the Termux `terminal-view`/`terminal-emulator` libraries; this is not a fake terminal and is designed for ANSI/VT applications such as `nano` and `vim`. 
- Linux commands execute through an app-private PRoot rootfs. The runtime layout is `files/linux/bin/proot` + `files/linux/rootfs`.
- PRoot integration is intentionally isolated in `ProotRuntime`; the release runtime must be produced from the requested `LinuxDroidapp/proot` fork at a pinned commit and packaged for every supported ABI.
- GitHub CLI commands use the official `gh auth login --web`, `gh codespace list`, `gh codespace create`, and `gh codespace ssh -c ...` flows. GitHub documents `gh codespace ssh` as the supported command-shell connection path. 
- Antigravity starts with the official interactive `agy --remote-control` model. Remote Control remains alive for the lifetime of the CLI process, so AGCodespace does not kill the terminal after discovering the URL. 
- URLs appearing in the live terminal transcript are detected. Authentication URLs are offered to Android Custom Tabs; a final Antigravity Remote Control URL is routed to the in-app WebView.
- WebView blocks local file/content access and sends unrelated navigation back to the system browser.

## Runtime packaging contract

The Android application cannot legally or technically invent a Linux distribution. The release bundle must contain a tested guest filesystem and the exact PRoot executable built from the requested source. The app downloads the exact Ubuntu Base 26.04.1 ARM64 archive on first initialization, verifies its published SHA-256, extracts it privately, and then bootstraps the guest. The app refuses to start the Linux terminal if the fixed archive cannot be verified or the packaged PRoot binary is unavailable.

Expected paths inside the app's private files directory:

```
files/linux/bin/proot
files/linux/rootfs/bin/sh
files/linux/rootfs/bin/bash
files/linux/rootfs/usr/bin/ssh
files/linux/rootfs/usr/bin/gh
files/linux/rootfs/home/agcodespace/.local/bin/agy
```

The rootfs source is fixed to Ubuntu Base 26.04.1 ARM64:
`https://cdimage.ubuntu.com/ubuntu-base/releases/resolute/release/ubuntu-base-26.04.1-base-arm64.tar.gz`
SHA-256: `5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd`.

The rootfs should include OpenSSH, GitHub CLI, CA certificates, bash, coreutils, tar, xz, gzip, procps and any packages required by the Antigravity CLI. Secret Service/D-Bus is optional and must not block the terminal: Antigravity authentication failures are surfaced to the UI instead of causing a global startup timeout.

## GitHub/Codespaces flow

1. Open Terminal.
2. Tap **GitHub Login** or type `gh auth login --web`.
3. AGCodespace opens detected authentication URLs in Android's browser/custom tab while the real CLI remains attached to the PTY.
4. Run `gh codespace list` or create a Codespace with `gh codespace create -r owner/repo`.
5. Connect using `gh codespace ssh -c NAME`.
6. Inside the Codespace run `agy --remote-control --dangerously-skip-permissions`.
7. Complete Antigravity's interactive authentication in the terminal/browser flow.
8. AGCodespace detects the final Remote Control URL and loads it in the Antigravity screen.

## Security

- No GitHub or Google password is stored by AGCodespace.
- Browser authentication stays in Android's browser/custom-tab surface.
- Remote Control URLs are treated as session secrets and are not logged by the app.
- No root permission is requested.
- PRoot is userspace isolation, not a security sandbox; the app's privacy/security model must not claim otherwise.

## Release gate

A Play Store release still requires device validation of the exact runtime artifacts, Android release signing, Play Console Data Safety/privacy declarations, and tests on supported ABIs/Android versions. This repository contains the application source and runtime contract; it does not silently fabricate unverified PRoot/rootfs binaries.
