#!/usr/bin/env bash
# =============================================================================
# build-npatch-artifacts.sh
#
# Rebuilds the NPatch patcher artifacts that MP-Manager embeds:
#   app/libs/npatch-patcher.jar          - NPatch patcher engine classes
#   app/src/main/assets/npatch/...       - loader.bin, metaloader.dex, libnpatch.so
#   app/src/main/assets/npatch.key       - built-in NPatch signing keystore
#   app/src/main/assets/fpa_app.key      - built-in FPA signing keystore
#   app/src/main/assets/mtprovider.dex   - optional provider dex (--provider)
#
# See docs/NPATCH_INTEGRATION.md for the full update walkthrough.
#
# Requirements:
#   - JDK 21+ (NPatch compiles with --release 21). JAVA_HOME must point at it.
#   - Android SDK (licenses accepted); NDK/CMake are auto-installed by AGP.
#   - NPatch source at <repo>/NPatch-dev with the Vector core submodule checked
#     out at the commit NPatch's dev branch pins (see the doc).
#   - NeoApk source at <repo>/NeoApk (sibling of NPatch-dev; the NPatch
#     settings.gradle.kts picks it up automatically via includeBuild("../NeoApk")).
#
# Usage:  tools/build-npatch-artifacts.sh
# =============================================================================
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
NPATCH_DIR="$REPO_DIR/NPatch-dev"
# NPatch's native build fails when the path contains spaces (clang splits
# -Wl,--thinlto-cache-dir=... at the space), so we always build from here.
BUILD_DIR="${NPATCH_BUILD_DIR:-/c/npatch-build}"
JAR_DIR="$BUILD_DIR/NPatch-dev/out/release"
ASSETS_DIR="$JAR_DIR/../assets/release"
OUT_JAR="$REPO_DIR/app/libs/npatch-patcher.jar"
APP_ASSETS="$REPO_DIR/app/src/main/assets"

if [ ! -d "$NPATCH_DIR" ]; then
  echo "ERROR: $NPATCH_DIR not found. Clone NPatch source there first." >&2
  exit 1
fi
if [ -z "${JAVA_HOME:-}" ]; then
  echo "ERROR: set JAVA_HOME to a JDK 21+ installation." >&2
  exit 1
fi

# 1. Sync the source tree to the space-free build directory (NPatch-dev is the
#    source of truth; the build dir is disposable). Full delete+copy with count
#    verification — OneDrive cloud placeholders make robocopy silently skip
#    files (dirs without contents).
mkdir -p "$BUILD_DIR"
rm -rf "$BUILD_DIR/NPatch-dev" "$BUILD_DIR/NeoApk"
mkdir -p "$BUILD_DIR/NPatch-dev"
cp -r "$NPATCH_DIR/." "$BUILD_DIR/NPatch-dev/"
[ -d "$REPO_DIR/NeoApk" ] && { mkdir -p "$BUILD_DIR/NeoApk"; cp -r "$REPO_DIR/NeoApk/." "$BUILD_DIR/NeoApk/"; }
SRC_COUNT=$(find "$NPATCH_DIR" -type f -not -path "*/build/*" -not -path "*/.git/*" -not -path "*/.gradle/*" -not -path "*/.kotlin/*" | wc -l)
DST_COUNT=$(find "$BUILD_DIR/NPatch-dev" -type f -not -path "*/build/*" -not -path "*/.git/*" -not -path "*/.gradle/*" -not -path "*/.kotlin/*" | wc -l)
if [ "$SRC_COUNT" != "$DST_COUNT" ]; then
  echo "ERROR: sync mismatch (source $SRC_COUNT vs build copy $DST_COUNT files)" >&2
  exit 1
fi

# 2. Build the fat jar (also produces out/assets/release with loader runtime).
cd "$BUILD_DIR/NPatch-dev"
./gradlew :jar:buildRelease --stacktrace > build-npatch.log 2>&1 || {
  echo "NPatch build FAILED - see $BUILD_DIR/NPatch-dev/build-npatch.log" >&2
  tail -40 build-npatch.log >&2
  exit 1
}

# 3. Repackage the fat jar down to NPatch-specific classes only. Everything
#    else (gson / jcommander / commons-io / kotlin-stdlib / bouncycastle) is
#    either already bundled with MP-Manager or provided by the Android
#    platform; shipping it again would break the dex step with duplicates.
FAT_JAR="$(ls -t "$JAR_DIR"/jar-v*-release.jar | head -1)"
echo "Repackaging $FAT_JAR -> $OUT_JAR"
mkdir -p "$(dirname "$OUT_JAR")"
# Keep: top/nkbe/** (patcher + share + NeoApk), com/wind/meditor/**,
#       pxb/android/** (axml). Drop the rest: gson / jcommander / commons-io /
#       kotlin-stdlib / bouncycastle are already bundled with MP-Manager or
#       provided by the Android platform; shipping them again would break the
#       dex step with duplicate classes.
python - "$FAT_JAR" "$OUT_JAR" <<'PYEOF'
import sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
keep_prefixes = ("top/nkbe/", "com/wind/meditor/", "pxb/android/")
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
    for info in zin.infolist():
        name = info.filename
        if name.endswith("/"):
            continue
        if not name.startswith(keep_prefixes):
            continue
        zout.writestr(info.filename, zin.read(name))
print(f"  entries kept: {sum(1 for _ in zipfile.ZipFile(dst).infolist())}")
PYEOF

# 4. Copy the loader runtime assets into the app.
rm -rf "$APP_ASSETS/npatch"
mkdir -p "$APP_ASSETS/npatch/so/arm64-v8a" "$APP_ASSETS/npatch/so/x86_64"
cp "$ASSETS_DIR/npatch/loader.bin"      "$APP_ASSETS/npatch/loader.bin"
cp "$ASSETS_DIR/npatch/metaloader.dex"  "$APP_ASSETS/npatch/metaloader.dex"
cp "$ASSETS_DIR/npatch/so/arm64-v8a/libnpatch.so" "$APP_ASSETS/npatch/so/arm64-v8a/libnpatch.so"
cp "$ASSETS_DIR/npatch/so/x86_64/libnpatch.so"    "$APP_ASSETS/npatch/so/x86_64/libnpatch.so"
# Keystores / provider dex ship inside the fat jar's assets/ root.
# public.xml is REQUIRED: the manifest editor resolves android attribute
# resource IDs from it. Without it, injected attributes get resource id -1
# and patched APKs fail to parse on strict package parsers (Android 9/10+),
# showing "problem parsing the package" on install.
FAT_JAR_ABS="$(cygpath -w "$FAT_JAR")"
unzip -p "$FAT_JAR_ABS" "assets/npatch.key"     > "$APP_ASSETS/npatch.key"
unzip -p "$FAT_JAR_ABS" "assets/fpa_app.key"    > "$APP_ASSETS/fpa_app.key"
unzip -p "$FAT_JAR_ABS" "assets/mtprovider.dex" > "$APP_ASSETS/mtprovider.dex"
unzip -p "$FAT_JAR_ABS" "assets/public.xml"     > "$APP_ASSETS/public.xml"

echo
echo "Done. Artifacts refreshed:"
ls -la "$OUT_JAR" "$APP_ASSETS/npatch/loader.bin" "$APP_ASSETS/npatch/so/arm64-v8a/libnpatch.so"
echo
echo "Slim jar size and package summary:"
python - "$OUT_JAR" <<'PYEOF'
import sys, zipfile
names = [i.filename for i in zipfile.ZipFile(sys.argv[1]).infolist()]
tops = sorted({n.split("/")[0] + "/" + n.split("/")[1] for n in names if "/" in n})
print(f"  {len(names)} class files, packages: {', '.join(tops)}")
PYEOF
