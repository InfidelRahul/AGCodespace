# Source status

Implemented in this source release:

- Native Compose Android UI
- Real VT/ANSI terminal view based on Termux terminal-view/terminal-emulator
- Foreground service ownership of the terminal session
- App-private PRoot/Linux rootfs runtime contract
- LinuxDroidapp/proot source pin and runtime packager
- GitHub CLI/Codespaces command integration
- Browser handoff for detected authentication URLs
- Antigravity Remote Control URL detection and WebView loading
- Secure WebView defaults and external-navigation fallback
- Runtime extraction with symlink/path traversal protection
- Release/security/license documentation
- Source verification script

Not certified in this environment:

- Android Gradle build (network/DNS prevents dependency and Gradle distribution resolution here)
- Physical-device PRoot execution
- Exact LinuxDroidapp/proot binary build for every ABI
- Exact guest rootfs and Antigravity CLI compatibility on-device
- Play Console submission/signing/Data Safety validation

Those are release validation requirements, not hidden TODOs in the application architecture.
