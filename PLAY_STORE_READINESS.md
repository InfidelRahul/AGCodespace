# Play Store release gate

This checklist is intentionally specific. Do not publish until every required gate is completed and tested on physical devices.

- [x] Native POSIX PTY implementation (forkpty, read/write, resize ioctl, signal termination).
- [x] Ubuntu ARM64 installer with official SHA-256 manifest validation and guarded extraction.
- [x] PRoot source build in CI pinned to a known revision; package runtime carrier libraries.
- [x] GitHub Actions unit-test and release-variant build; SHA-256 output; uploaded artifact retention set to one day.
- [ ] Complete the VT/xterm renderer (current compact renderer handles basic cursor addressing, erase, scroll and alternate screen but ignores SGR color/style and advanced protocols); test `nano`, `vim`, `less`, `top`, shell line editing, Unicode and paste.
- [ ] Device-test the special-use foreground service, notification permission behavior, screen-off/background sessions, process limits and Android process reclaim; complete the required Play Console special-use FGS declaration.
- [ ] Validate all tar entry types, file modes, symlink and hardlink behavior against the official Ubuntu archive on-device; add resumable downloads and low-storage recovery.
- [ ] Extend the in-app rootfs browser with file operations, Android shared-storage bridge via Storage Access Framework, exportable diagnostics, and runtime recovery/reset UI.
- [ ] Device test on supported ARM64 Android versions and vendor ROMs; verify PRoot loader execution and every required native shared library on actual devices.
- [ ] Verify D-Bus/Secret Service expectations under PRoot and document unsupported cases.
- [ ] Add instrumented tests, security review, accessibility checks, privacy policy, Play Data Safety declarations, store listing and crash/ANR testing.
- [ ] Configure an owner-controlled release keystore in CI; build and test the signed Android App Bundle (AAB). Never use an ephemeral key for Play Store releases.
- [ ] Review third-party license obligations and include required notices/source offers before distribution.

**Status:** not yet production-ready. The release APK produced without configured signing variables is unsigned. CI success alone does not establish device-level runtime correctness.
