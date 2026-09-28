#!/usr/bin/env bash
set -euo pipefail

# Watch Instruments — one-command bootstrap and build check.

echo "==> Checking JDK"
if ! command -v java >/dev/null 2>&1; then
  echo "Java not found. Install JDK 17: https://adoptium.net/temurin/releases/?version=17" >&2
  exit 1
fi
java -version 2>&1 | head -n 1

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  echo "ANDROID_HOME / ANDROID_SDK_ROOT is not set." >&2
  echo "Install Android SDK 35 with build-tools 35.0.0, then export ANDROID_HOME." >&2
  exit 1
fi
echo "==> Android SDK: $SDK"

if [ ! -f "$SDK/platforms/android-35/android.jar" ]; then
  echo "Android platform 35 not found under $SDK/platforms." >&2
  exit 1
fi

echo "==> Running unit tests, lint, and debug build"
./gradlew testDebugUnitTest lintDebug assembleDebug

echo
echo "Done. Debug APK:"
echo "  app/build/outputs/apk/debug/app-debug.apk"
echo
echo "Install on a connected watch:"
echo "  adb install -r app/build/outputs/apk/debug/app-debug.apk"
