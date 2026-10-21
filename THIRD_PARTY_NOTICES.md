# Third-party notices and source obligations

## LinuxDroidapp PRoot

- Source repository: https://github.com/LinuxDroidapp/proot
- Pinned source revision used by CI: `caadcae0e7697ec29f02e231a3a88866561aacd0`
- License: GNU General Public License version 2 (GPL-2.0), as declared by the upstream [`COPYING`](https://github.com/LinuxDroidapp/proot/blob/caadcae0e7697ec29f02e231a3a88866561aacd0/COPYING) file.
- The Android package produced by CI contains PRoot runtime binaries built from that source. Before distributing the APK/AAB, publish or otherwise provide the complete corresponding source code and build/install scripts for the exact pinned revision, preserve upstream notices, and comply with GPL-2.0. A one-day Actions artifact is not a durable source-code offer.

## Apache Commons Compress

- Project: https://commons.apache.org/proper/commons-compress/
- License: Apache License 2.0.
- The library is used for reading the compressed Ubuntu Base tar archive. Include the required Apache license and notices in a final distribution.

## Ubuntu Base

- Source directory: https://cdimages.ubuntu.com/cdimage/ubuntu-base/releases/resolute/release/
- The app downloads the Ubuntu Base ARM64 archive directly from the official Ubuntu image directory at install time and verifies it against the published `SHA256SUMS` file.
- Review Ubuntu licensing notices and distribution terms relevant to any packages subsequently installed by the user.

This notice is an engineering checklist, not legal advice. Recheck upstream licenses at the pinned revisions before release.
