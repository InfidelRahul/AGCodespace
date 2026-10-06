# AGCodespace architecture

```text
Android UI (Compose)
       |
       +-- Terminal screen ----> TerminalService (foreground)
       |                              |
       |                              +--> Termux TerminalView/Emulator
       |                              +--> host /system/bin/sh -c
       |                                      |
       |                                      +--> LinuxDroidapp/proot
       |                                             |
       |                                             +--> Linux rootfs
       |                                                   +-- bash
       |                                                   +-- gh / ssh
       |                                                   +-- agy
       |
       +-- Antigravity screen <---- TerminalEvents <---- live VT transcript
       |          |
       |          +--> Android Custom Tab for auth URLs
       |          +--> WebView for final Remote Control URL
       |
       +-- Home / Settings
```

## State machine

`LINUX_UNAVAILABLE -> LINUX_READY -> GITHUB_AUTH -> CODESPACE_CONNECTED -> AGY_AUTH_REQUIRED -> AGY_REMOTE_CONTROL -> WEBVIEW`

Failures never remove the terminal. The terminal is the primary recovery surface and can always be used for manual diagnosis.

## Authentication

GitHub authentication uses `gh auth login --web`, keeping the OAuth/device flow in the supported GitHub CLI/browser path. Codespace access uses `gh codespace ssh -c NAME`.

Antigravity authentication remains an interactive PTY workflow. AGCodespace watches the emulator transcript for URLs and auth prompts, launches detected external auth links in Android's browser, and keeps the `agy` process alive until the user exits it.

## Security boundary

PRoot is a userspace filesystem/process virtualization mechanism. It is not a security sandbox. The app must never describe guest code as isolated from the Android application with a security boundary equivalent to a VM/container.
