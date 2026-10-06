# Release checklist

## Required before Play Store publication

- [ ] Build and test PRoot from `LinuxDroidapp/proot@caadcae0e7697ec29f02e231a3a88866561aacd0` for arm64-v8a and every additional supported ABI.
- [ ] Produce a reproducible rootfs tarball with bash, OpenSSH, GitHub CLI, Git, CA certificates, curl/tar/xz/gzip and the Antigravity CLI.
- [ ] Verify rootfs symlinks, ELF interpreters and executable permissions after extraction on Android.
- [ ] Run the PRoot guest on physical Android devices with unprivileged PRoot/ptrace.
- [ ] Verify `bash`, `ssh`, `gh auth login --web`, `gh codespace list`, `gh codespace ssh`, and Antigravity CLI interactively.
- [ ] Verify browser authentication does not block the PTY and that copied/pasted device codes work.
- [ ] Verify Antigravity final Remote Control URL is detected without killing the `agy` process.
- [ ] Verify Remote Control WebView navigation and browser fallback.
- [ ] Test `nano`, `vim`, Ctrl/Alt/Shift/Fn, paste, Unicode, mouse reporting, resize, alternate screen, scrollback, and long-running progress output.
- [ ] Test app rotation/background/foreground and Android process pressure with the foreground terminal service.
- [ ] Test offline startup: terminal must remain available and errors must be actionable.
- [ ] Test Android 14/15/16 behavior and foreground-service special-use declaration.
- [ ] Run lint, unit tests, instrumentation tests and a signed release build.
- [ ] Review GPL/Apache/XZ/PRoot notices and source-offer obligations.
- [ ] Complete Play Console Data Safety, privacy policy, account/data deletion disclosures where applicable, content-rating and app-access declarations.
- [ ] Configure release signing outside the repository; no signing secrets belong in source control.

## Important

The project deliberately does not claim these checks are complete merely because source code exists. A Play Store production label requires successful device validation of the exact packaged runtime.
