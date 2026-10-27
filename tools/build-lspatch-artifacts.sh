#!/usr/bin/env bash
# =============================================================================
# build-lspatch-artifacts.sh
#
# Rebuilds the LSPatch (JingMatrix fork) patcher artifacts that MP-Manager
# embeds alongside NPatch:
#   app/libs/lspatch-patcher.jar             - LSPatch patcher engine classes
#   app/src/main/assets/lspatch/loader.dex   - loader DEX embedded into patched apps
#   app/src/main/assets/lspatch/metaloader.dex
#   app/src/main/assets/lspatch/so/<abi>/liblspatch.so  (armeabi-v7a, arm64-v8a, x86, x86_64)
#   app/src/main/assets/lspatch/keystore     - LSPatch's built-in signing keystore
#
# See docs/NPATCH_INTEGRATION.md (covers both engines) for the full update
# walkthrough.
#
# Requirements: JDK 21+ in JAVA_HOME, Android SDK with accepted licenses,
# LSPatch source at <repo>/LSPatch-master with its `core` submodule checked out
# (JingMatrix/LSPosed, branch master).
#
# Usage:  tools/build-lspatch-artifacts.sh
# =============================================================================
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LSPATCH_DIR="$REPO_DIR/LSPatch-master"
# Same reason as NPatch: the native build breaks when the path has spaces.
BUILD_DIR="${LSPATCH_BUILD_DIR:-/c/lspatch-build}"
JAR_DIR="$BUILD_DIR/LSPatch-master/out/release"
ASSETS_DIR="$JAR_DIR/../assets/release"
OUT_JAR="$REPO_DIR/app/libs/lspatch-patcher.jar"
APP_ASSETS="$REPO_DIR/app/src/main/assets"

if [ ! -d "$LSPATCH_DIR" ]; then
  echo "ERROR: $LSPATCH_DIR not found." >&2
  exit 1
fi
if [ -z "${JAVA_HOME:-}" ]; then
  echo "ERROR: set JAVA_HOME to a JDK 21+ installation." >&2
  exit 1
fi

# 1. Sync the source tree to the space-free build directory.
#    Full delete+copy with count verification: OneDrive cloud placeholders make
#    robocopy silently skip files (dirs without contents), so verify a sentinel
#    file landed before building.
mkdir -p "$BUILD_DIR"
rm -rf "$BUILD_DIR/LSPatch-master"
mkdir -p "$BUILD_DIR/LSPatch-master"
cp -r "$LSPATCH_DIR/." "$BUILD_DIR/LSPatch-master/"
SRC_COUNT=$(find "$LSPATCH_DIR" -type f -not -path "*/build/*" -not -path "*/.git/*" -not -path "*/.gradle/*" -not -path "*/.kotlin/*" | wc -l)
DST_COUNT=$(find "$BUILD_DIR/LSPatch-master" -type f -not -path "*/build/*" -not -path "*/.git/*" -not -path "*/.gradle/*" -not -path "*/.kotlin/*" | wc -l)
if [ "$SRC_COUNT" != "$DST_COUNT" ]; then
  echo "ERROR: sync mismatch (source $SRC_COUNT vs build copy $DST_COUNT files)" >&2
  exit 1
fi

# 1b. LSPatch's root build.gradle.kts reads its version code from git history
#     (JGit throws an NPE without a .git dir), so seed a minimal repo in the
#     disposable build copy when the source drop has none.
if [ ! -e "$BUILD_DIR/LSPatch-master/.git" ]; then
  git -C "$BUILD_DIR/LSPatch-master" init -q
  git -C "$BUILD_DIR/LSPatch-master" add -A
  git -C "$BUILD_DIR/LSPatch-master" -c user.name=builder -c user.email=builder@local commit -q -m "vendored LSPatch source" || true
fi

# 2. Build the fat jar (also produces out/assets/release with the loader runtime).
cd "$BUILD_DIR/LSPatch-master"
./gradlew :jar:buildRelease --stacktrace > build-lspatch.log 2>&1 || {
  echo "LSPatch build FAILED - see $BUILD_DIR/LSPatch-master/build-lspatch.log" >&2
  tail -40 build-lspatch.log >&2
  exit 1
}

# 3. Repackage the fat jar down to LSPatch-specific classes only.
#    Excluded: gson / jcommander / commons-io (MP-Manager already bundles them),
#    kotlin-stdlib (provided via the app's kotlin-stdlib pin), bouncycastle /
#    guava / checker annotations (apkzlib deps MP-Manager already has), and
#    com/android/apksig/** — MP-Manager vendors apksig inside the :apkkit
#    module, so shipping it again would break the dex step with duplicates.
#    The axml packages (com/wind/meditor, pxb/android) are excluded too —
#    byte-identical copies are already vendored from the NPatch build; both
#    engines share them.
FAT_JAR="$(ls -t "$JAR_DIR"/lspatch-v*-release.jar | head -1)"
echo "Repackaging $FAT_JAR -> $OUT_JAR"
mkdir -p "$(dirname "$OUT_JAR")"
python - "$FAT_JAR" "$OUT_JAR" <<'PYEOF'
import sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
keep_prefixes = ("org/lsposed/", "com/android/tools/build/apkzlib/")
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
    kept = 0
    for info in zin.infolist():
        name = info.filename
        if name.endswith("/") or not name.startswith(keep_prefixes):
            continue
        zout.writestr(info.filename, zin.read(name))
        kept += 1
print(f"  entries kept: {kept}")
PYEOF

# 4. Copy the loader runtime assets into the app.
rm -rf "$APP_ASSETS/lspatch"
mkdir -p "$APP_ASSETS/lspatch/so"
cp -r "$ASSETS_DIR/lspatch/." "$APP_ASSETS/lspatch/"
FAT_JAR_ABS="$(cygpath -w "$FAT_JAR")"
# KeystoreSpec.builtIn() loads "assets/keystore" from the classloader, i.e. the
# APK zip root — like npatch.key, NOT under assets/lspatch/.
unzip -p "$FAT_JAR_ABS" "assets/keystore" > "$APP_ASSETS/keystore"

echo
echo "Done. Artifacts refreshed:"
ls -la "$OUT_JAR" "$APP_ASSETS/lspatch/loader.dex" "$APP_ASSETS/lspatch/so/arm64-v8a/liblspatch.so"
