# Linux runtime

## Guest distribution

AGCodespace uses the exact Ubuntu Base archive requested for the project:

`ubuntu-base-26.04.1-base-arm64.tar.gz`

Official source:
`https://cdimage.ubuntu.com/ubuntu-base/releases/resolute/release/ubuntu-base-26.04.1-base-arm64.tar.gz`

Published SHA-256:
`5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd`

The archive is downloaded on first runtime initialization, verified byte-for-byte by SHA-256, and extracted into the app's private Linux rootfs. This avoids shipping an unverified or silently substituted distro image in the APK.

## Bootstrap

Ubuntu Base is intentionally minimal. After extraction the runtime bootstrap installs:

- `ca-certificates`
- `curl`
- `git`
- `openssh-client`
- `bash`
- `coreutils`
- `tar`
- `xz-utils`
- `gzip`
- `procps`
- `iproute2`
- GitHub CLI (`gh`), when available from Ubuntu repositories
- Antigravity CLI using Google's official Linux installer:
  `curl -fsSL https://antigravity.google/cli/install.sh | bash`

Antigravity's official documentation states that its Linux CLI installer places `agy` under `~/.local/bin`, and documents a manual browser/code loop for remote SSH authentication.

## PRoot

PRoot remains the LinuxDroidapp/proot implementation selected by the project. `tools/package-runtime.sh` pins the exact source revision used by the application runtime and produces the ARM64 executable.

## DNS and mounts

The runtime creates an app-owned `/etc/resolv.conf` and enters the guest with `/proc`, `/sys`, `/dev`, and the app-private host directory available through PRoot bindings.

No root permission or Android privileged capability is required.
