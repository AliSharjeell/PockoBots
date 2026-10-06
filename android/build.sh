#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v gradle >/dev/null; then
  echo 'Install Gradle 8.11.1 and Android SDK 35, or open this directory in Android Studio.' >&2
  exit 1
fi
gradle --no-daemon :app:assembleDebug :app:lintDebug
