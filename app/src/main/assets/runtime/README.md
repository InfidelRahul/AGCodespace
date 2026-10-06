# AGCodespace Linux runtime

The supported release ABI is **arm64-v8a**.

The guest distribution is **Ubuntu Base 26.04.1 LTS (Resolute Raccoon), ARM64**:

- URL: https://cdimage.ubuntu.com/ubuntu-base/releases/resolute/release/ubuntu-base-26.04.1-base-arm64.tar.gz
- SHA-256: `5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd`
- Size: 35,092,106 bytes

The application verifies the archive before extraction. The guest is then bootstrapped with the packages required for GitHub Codespaces and the official Antigravity CLI. The Antigravity installer used by the bootstrap is the official Linux installer documented by Google.

The PRoot executable itself is still built from the pinned LinuxDroidapp/proot source by `tools/package-runtime.sh`.
