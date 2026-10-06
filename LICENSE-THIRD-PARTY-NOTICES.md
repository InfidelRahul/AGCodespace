# Third-party notices

AGCodespace incorporates or references the following third-party components. Their licenses remain with their respective authors.

## Termux terminal libraries

- Project: Termux `terminal-view` / `terminal-emulator`
- Version: 0.118.0
- License: GPL-3.0-or-later (see the upstream project for the complete license and source)
- Source: https://github.com/termux/termux-app

These libraries provide the VT/ANSI terminal emulator and Android terminal view used by AGCodespace.

## LinuxDroidapp/proot

- Project: LinuxDroidapp/proot
- Pinned source commit: `caadcae0e7697ec29f02e231a3a88866561aacd0`
- License: upstream repository license applies; package the complete corresponding source and notices with any binary distribution as required by that license.
- Source: https://github.com/LinuxDroidapp/proot

## Apache Commons Compress

- Artifact: `org.apache.commons:commons-compress:1.28.0`
- License: Apache License 2.0
- Source: https://commons.apache.org/proper/commons-compress/

## XZ for Java

- Artifact: `org.tukaani:xz:1.10`
- License: Public Domain / LGPL-2.1-or-later depending on component; see upstream distribution for exact notices.
- Source: https://tukaani.org/xz/java.html

## GitHub CLI and Antigravity

AGCodespace does not embed GitHub CLI or Antigravity CLI binaries in the Android source tree. They are expected inside the user-installed Linux guest runtime and remain subject to their own distribution terms and licenses.
