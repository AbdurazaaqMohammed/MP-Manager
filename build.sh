#!/usr/bin/env bash
# Build MP-Manager debug APK.
# Usage: ./build.sh [any extra gradle tasks/flags]
set -euo pipefail
cd "$(dirname "$0")"

# Use JDK 17 if JAVA_HOME is not already set to a compatible JDK.
if ! javac -version 2>/dev/null | grep -q "17\|21"; then
    for d in /usr/lib/jvm/*; do
        if [ -x "$d/bin/javac" ] && "$d/bin/javac" -version 2>&1 | grep -q "17\|21"; then
            export JAVA_HOME="$d"
            export PATH="$JAVA_HOME/bin:$PATH"
            break
        fi
    done
fi

./gradlew assembleDebug "$@"
APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] && echo "OK: $APK"

# Install to the connected device if any.
if command -v adb >/dev/null 2>&1 && [ "$(adb get-state 2>/dev/null)" = "device" ]; then
    adb install -r "$APK"
fi
