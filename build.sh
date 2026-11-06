#!/usr/bin/env bash
set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

export ANDROID_HOME="/data/user/0/com.agy/files/android-sdk"
export GRADLE_USER_HOME="/data/user/0/com.agy/files/.gradle"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=/data/user/0/com.agy/files/usr/tmp -Duser.home=/data/user/0/com.agy/files"
mkdir -p /data/user/0/com.agy/files/usr/tmp

BUILD_TYPE="Debug"
GRADLE_TASK="assembleFullDebug"
APK_DIR="app/build/outputs/apk/full/debug"

if [ "$1" == "release" ]; then
    BUILD_TYPE="Release"
    GRADLE_TASK="assembleFullRelease"
    APK_DIR="app/build/outputs/apk/full/release"
fi

echo "=========================================="
echo "  Building MP-Manager ($BUILD_TYPE) offline on device..."
echo "=========================================="

bash ./gradlew "$GRADLE_TASK" --no-daemon

OUTPUT_APK=$(find "$APK_DIR" -name "*.apk" 2>/dev/null | head -1)

if [ -f "$OUTPUT_APK" ]; then
    DEST="/storage/emulated/0/Download/MP-Manager-release.apk"
    cp "$OUTPUT_APK" "$DEST"
    echo "=========================================="
    echo "  Build SUCCESSFUL!"
    echo "  APK Saved to: $DEST"
    echo "  Size: $(du -h "$DEST" | cut -f1)"
    echo "=========================================="

    # Auto-clean temporary intermediates to save phone storage
    echo "Cleaning temporary build cache..."
    rm -rf app/build/intermediates app/build/tmp
else
    echo "Error: Output APK not found!"
    exit 1
fi
