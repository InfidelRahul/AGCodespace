#!/bin/sh
set -eu
GRADLE_VERSION=8.13
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/agcodespace-gradle-$GRADLE_VERSION"
DIST="$CACHE/gradle-$GRADLE_VERSION"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$CACHE"
  TMP="$CACHE/gradle.zip"
  if [ ! -f "$TMP" ]; then
    command -v curl >/dev/null 2>&1 || { echo "curl is required to bootstrap Gradle" >&2; exit 1; }
    curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$TMP"
  fi
  rm -rf "$DIST.new"
  mkdir "$DIST.new"
  command -v unzip >/dev/null 2>&1 || { echo "unzip is required to bootstrap Gradle" >&2; exit 1; }
  unzip -q "$TMP" -d "$DIST.new"
  mv "$DIST.new/gradle-$GRADLE_VERSION" "$DIST"
  rm -rf "$DIST.new"
fi
exec "$DIST/bin/gradle" "$@"
