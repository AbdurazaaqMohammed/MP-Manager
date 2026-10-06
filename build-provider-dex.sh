#!/system/bin/sh
# Rebuild app/src/main/assets/file_provider/provider.dex from the injected
# provider sources. Run on a machine with a JDK, android.jar and network:
#
#   ./build-provider-dex.sh [path/to/android.jar]
#
# d8 comes from the r8 jar (downloaded to the working directory if missing).
set -e

REPO_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SRC_DIR="$REPO_DIR/app/src/main/java/bin/mt/file/content"
OUT_DEX="$REPO_DIR/app/src/main/assets/file_provider/provider.dex"
ANDROID_JAR=${1:-$HOME/Android/Sdk/platforms/android-36/android.jar}
R8_JAR=r8.jar
R8_URL="https://dl.google.com/android/maven2/com/android/tools/r8/8.3.37/r8-8.3.37.jar"

if [ ! -f "$ANDROID_JAR" ]; then
    echo "android.jar not found: $ANDROID_JAR" >&2
    exit 1
fi
if [ ! -f "$R8_JAR" ]; then
    curl -sL -o "$R8_JAR" "$R8_URL"
fi

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

javac -nowarn -source 8 -target 8 -cp "$ANDROID_JAR" -d "$WORK/classes" \
    "$SRC_DIR/MTDataFilesProvider.java" \
    "$SRC_DIR/MTDataFilesWakeUpActivity.java"

java -cp "$R8_JAR" com.android.tools.r8.D8 \
    --min-api 21 --lib "$ANDROID_JAR" --output "$WORK" \
    "$WORK/classes/bin/mt/file/content/MTDataFilesProvider.class" \
    "$WORK/classes/bin/mt/file/content/MTDataFilesWakeUpActivity.class"

cp "$WORK/classes.dex" "$OUT_DEX"
echo "wrote $OUT_DEX"
