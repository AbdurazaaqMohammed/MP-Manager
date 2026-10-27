# Xposed Patcher Integration (NPatch + LSPatch, Integrated Mode)

MP-Manager embeds **two** integrated-mode Xposed patcher engines, selectable from
the APK dialog's "Xposed patcher" entry:

- **NPatch** — [7723mod/NPatch](https://github.com/7723mod/NPatch) (`NPatch-dev/`)
- **LSPatch** — the [JingMatrix/LSPatch](https://github.com/JingMatrix/LSPatch) fork (`LSPatch-master/`)

Both patch an APK with Xposed modules baked in (`--embed` / embedded-modules
mode), so the result runs standalone without a manager app. Each engine runs
**in-process** on-device, exactly as its own manager app would.

The user's MP-Manager signing key (Sign Settings) is **handed to the engine**
(SignWrapper.exportKey writes it into a keystore the engine reads — BKS for
NPatch, KeyStore.getDefaultType() for LSPatch, verified with a read-back), and
the engine signs its output in place with it. When "Auto Sign" is unchecked the
engines' built-in keystores are used instead. Never re-sign a patcher output
with apksign afterwards: both engines *data-multiplex* their APKs (unchanged
entries are links into the stored nested origin APK), and any zip
re-serialization — apksig or a zipalign rebuild — materializes those links and
roughly doubles the file size.

This document explains the architecture and how to **update the integration when
either upstream project updates**.

---

## 1. What lives where

| Artifact | Purpose |
|---|---|
| `NPatch-dev/` | NPatch source tree (dev state 2026-10-18). `core/` = HSSkyBoy/Vector at the commit NPatch's dev branch pins; `../NeoApk/` is picked up automatically as an included build (not on Maven). |
| `NeoApk/` | HSSkyBoy/NeoApk clone — NPatch's zip/signing library dependency. |
| `LSPatch-master/` | JingMatrix LSPatch source tree (master, 2026-10-27). `core/` = JingMatrix/LSPosed clone (branch master) with its own submodules initialized (commons-lang3, manifest-editor, lsplant, lsplt, …). No git metadata: the build script seeds a throwaway repo in the build copy. |
| `app/libs/npatch-patcher.jar` | NPatch patcher engine classes (246 KB). Keeps only `top/nkbe/**` (patcher + share + NeoApk), `com/wind/meditor/**`, `pxb/android/**` (axml). gson / jcommander / commons-io / kotlin-stdlib / bouncycastle come from MP-Manager or the platform. |
| `app/libs/lspatch-patcher.jar` | LSPatch patcher engine classes. Keeps `org/lsposed/**`, `org/matrix/**`, `de/robv/**`, `com/android/apkzlib/**`, `io/github/libxposed/**`, plus the hook libs (`com/swift/**`, `com/lody/**`); the axml packages are excluded — byte-identical copies are already vendored from the NPatch build (verified with a tree diff; re-verify after updates). |
| `app/src/main/assets/npatch/…` | NPatch loader runtime: `loader.bin` (patch-loader dex), `metaloader.dex`, `so/{arm64-v8a,x86_64}/libnpatch.so`, plus `npatch.key`, `fpa_app.key`, `mtprovider.dex`. |
| `app/src/main/assets/lspatch/…` | LSPatch loader runtime: `loader.dex` (no R8 — the module ships unminified), `metaloader.dex`, `so/{armeabi-v7a,arm64-v8a,x86,x86_64}/liblspatch.so` (4 ABIs). LSPatch's built-in signing keystore goes to the zip root as `assets/keystore` (`app/src/main/assets/keystore`), matching `KeystoreSpec.builtIn()`. |
| `app/src/main/assets/public.xml` | **Required.** Android framework attribute-ID table used by the vendored manifest editor (`com.wind.meditor`, shared by both engines). Without it, injected manifest attributes get resource-id `-1` and patched APKs fail to parse on Android 9/10+ package parsers ("There was a problem parsing the package"). The official NPatch manager APK ships the same file. |
| `app/src/main/java/io/github/abdurazaaqmohammed/features/apk/NPatchDialogs.java` | UI + engine dispatch. "Xposed patcher" entry in the APK info dialog's "More" list (index 12 in `ApkInfoDialogs.java`). Engine selector at the top toggles engine-specific rows (NPatch: MicroG / hide-libs / MT provider; LSPatch: DocumentsProvider / fake-signature / GMS replacement) and the bypass-level list (NPatch 0–2, LSPatch 0–3). |
| `app/src/main/res/layout/dialog_npatch.xml` | Options dialog (Material 3). |
| `app/src/main/res/values/strings.xml` | All `npatch_*` strings. |
| `tools/build-npatch-artifacts.sh` | Rebuilds NPatch artifacts from `NPatch-dev`. |
| `tools/build-lspatch-artifacts.sh` | Rebuilds LSPatch artifacts from `LSPatch-master`. |

Both engines load their runtime resources via
`getClass().getClassLoader().getResourceAsStream("assets/…")`, which on Android
reads entries straight out of the host APK's zip — the assets must keep those
exact zip paths, which placing them in `app/src/main/assets/` does.

## 2. How a patch runs

`NPatchDialogs.startPatch()` resolves the user's signing key first
(`SignWrapper.requireAuth`, UI thread, may password-prompt), exports it to
cache (`SignWrapper.exportKey`, dual-format: BKS + default-type), then on a
worker thread runs the selected engine with `-k <exported keystore>`. The
engines sign in place (NPatch: V2+V3 via NeoApk's raw-file signer; LSPatch: V2
via its apkzlib `SigningExtension`), preserving the multiplexed layout. The
exported keystores live in the app-private cache and are deleted on exit.

- **NPatch**: `new NPatch(logger, args).doCommandLine()` with the same argument
  construction as NPatch Manager's `Patcher.Options.toStringArray()` and
  `useManager=false`; always `-npa` internally (bypass capped at level 2 —
  levels 3/4 need NPatch Manager). Output: `<name>-npatched.apk`.
- **LSPatch**: `new LSPatch(args).toSpec()` → `new ApkPatcher(logger, spec).patch()`
  — the engine's own CLI front end and spec builder. Output:
  `<name>-lspatched.apk`.

Both engines write `<name>-<verCode><suffix>` into the output directory; the
dialog picks the newest and normalizes the name (dedup with `_2`, `_3`…).

## 3. Updating NPatch when it updates

```bash
cd NPatch-dev
git -C core fetch origin && git -C core checkout <commit pinned by the new NPatch dev branch>
git -C core submodule update --init --recursive --depth 1
```

The pinned `core` commit is the `sha` shown by
`https://api.github.com/repos/7723mod/NPatch/contents/core?ref=dev`.

### 3.1 Re-apply the local source patches (NPatch)

As of 2026-10-26 upstream dev needs two fixes; re-apply if the update overwrites
these files:

1. **`patch-loader/src/main/jni/src/patch_loader.cpp`** — upstream calls
   `RegisterBypass`/`PrepareLibHideSnapshots`/`RefreshLibHideSnapshots` without
   including their header. Add `#include "jni/bypass_sig.h"` next to the
   existing `#include "jni/svc_bypass.h"`.
2. **`patch-loader/proguard-rules.pro`** — R8 full mode (+ `-allowaccessmodification`)
   repackages obfuscated classes into the root package, which collides with the
   metaloader's root-level names (`a`…`i`) → `NoSuchFieldError` at loader start.
   Add `-repackageclasses "npl"`.

### 3.2 Rebuild

```bash
JAVA_HOME="<jdk-21+>" bash tools/build-npatch-artifacts.sh
```

The script mirrors `NPatch-dev` (+ `NeoApk`) to `C:\npatch-build` (a
**space-free path** — NPatch's native build passes an unquoted
`-Wl,--thinlto-cache-dir=<path>`, which clang splits at spaces), runs
`:jar:buildRelease` (NDK/CMake auto-installed on first run), repackages the fat
jar to `app/libs/npatch-patcher.jar`, and copies the loader assets
(**including `public.xml`**) into `app/src/main/assets/`.

## 4. Updating LSPatch when it updates

`LSPatch-master` is a source drop without git metadata, so:

```bash
# refresh the source (replace files or make it a git clone of JingMatrix/LSPatch)
cd LSPatch-master/core
git fetch origin && git checkout origin/master -- . # or re-clone at the desired commit
git submodule update --init --recursive --depth 1   # commons-lang3, manifest-editor, lsplant, …
```

If you re-clone `core`, remember its own submodules (the build fails with
`cannot find symbol` in `core/external/apache/local/MemberUtilsX.java` and
`package com.android.tools.build.apkzlib…` style errors otherwise).

### 4.1 Re-apply the local source patches (LSPatch)

1. **`build.gradle.kts` (root)** — the CMake define must be a real string literal:

   ```kotlin
   "-DVERSION_NAME=\"${verName}\"",
   ```

   Upstream writes `"-DVERSION_NAME='\"$verName\"'"`; AGP invokes cmake without
   a shell and CMake strips quotes from -D values, so the preprocessor ends up
   with a non-string token.

2. **`core/native/include/common/config.h`** — because of the same quote
   stripping, stringify the incoming value so either form works:

   ```cpp
   #define LSPATCH_STR2(x) #x
   #define LSPATCH_STR(x) LSPATCH_STR2(x)
   const char *const kVersionName = LSPATCH_STR(VERSION_NAME);
   ```

   (Upstream has `const char *const kVersionName = VERSION_NAME;`, which needs
   the define to already be a quoted literal.) If a future upstream fixes
   either side themselves, theirs wins.

### 4.2 Rebuild

The script mirrors the tree to `C:\lspatch-build\LSPatch-master` (**delete +
`cp -r` sync with a file-count check** — OneDrive cloud placeholders make
robocopy silently skip files, leaving empty dirs), **seeds a minimal git repo
there** (the root build reads its version code via JGit and NPEs without a
`.git`), runs `:jar:buildRelease` (which chains `meta-loader:copyDexRelease` +
`patch-loader:copyDexRelease` + `patch-loader:copySoRelease`), repackages the
fat jar to `app/libs/lspatch-patcher.jar`, and copies the 4-ABI loader assets +
`keystore` into `app/src/main/assets/lspatch/`.

Notes:
- LSPatch's `patch-loader` ships **unminified** (`isMinifyEnabled = false`), so
  the NPatch-style repackaging collision cannot occur there.
- Its `patch-loader/proguard-rules.pro` still contains legacy Xpatch-era keep
  rules; they are inert at the current minify setting.
- The `.gitmodules` `apksigner` entry is vestigial — nothing in the build
  references it; don't bother cloning googlesource.

### 4.3 If classes were added/renamed upstream

- NPatch slim jar: extend `keep_prefixes` in `tools/build-npatch-artifacts.sh`.
- LSPatch slim jar: extend `keep_prefixes` in `tools/build-lspatch-artifacts.sh`
  (and drop `com/wind/meditor` + `pxb/android` from the exclusion list only if a
  tree diff against `NPatch-dev/core/external/axml/manifest-editor` shows the
  copies have diverged — then also re-copy the newer version into
  `app/src/main/assets/`-served classes by rebuilding NPatch's jar, since both
  engines share one copy).
- Re-check dependency pins in `app/build.gradle`: `kotlin-stdlib` ≥ NeoApk's
  Kotlin (currently 2.4.20); gson / jcommander 1.82 / commons-io / bcprov must
  stay since both slim jars rely on them.

## 5. APK data multiplexing (standalone function, full flavor)

The APK dialog's "Multiplex APK (deduplicate)" entry runs MP-Manager's own
`ApkDataMultiplexer` (full flavor): it detects the stored embedded APK
(`assets/lspatch/origin.apk`, `assets/npatch/origin.apk`, `assets/base.apk`,
or the largest stored ZIP entry whose central directory parses), and for every
outer entry that is byte-identical (same name, data, method, sizes, CRC) to an
entry inside the host blob it drops the outer local header + data and repoints
the central-directory offset at the matching local header inside the blob.
The host bytes are copied verbatim and re-aligned to 4096, and the entry
region is padded to 4096 before the central directory so the raw-file V2/V3
signer (NeoApk, vendored with NPatch) can sign the result in place — the only
signing that preserves the multiplexed layout. Entries that need page
alignment (`lib/*.so`, `resources.arsc`, stored `assets/*.so`) are never
redirected, only re-aligned. Works on both materialized and already-multiplexed
patcher outputs (the latter simply save less).

## 6. Full / lite build variants

The engines are large (~5 MB of assets + classes), so the app ships in two
product flavors:

- **full** — engines bundled (`fullImplementation` deps in `app/build.gradle`,
  loader assets under `app/src/full/assets/`, patcher dialog under
  `app/src/full/java/`).
- **lite** — no engines. Tapping "Xposed patcher" explains that the feature
  needs the full version. "Multiplex APK" still works.

Builds: `./gradlew :app:assembleFullDebug` / `:app:assembleLiteDebug`
(release: `assembleFullRelease` / `assembleLiteRelease`). The seam between
flavors is `features/apk/XposedPatcherLauncher`, which exists in both
`src/full` and `src/lite` with the same signature; everything else lives in
`src/main` untouched.

## 7. Build environment requirements

- JDK 21+ (both engines target Java 21; the vendored jars are Java-21 bytecode,
  which is also why `gradle/gradle-daemon-jvm.properties` pins the MP-Manager
  daemon to JDK 25 — javac 17 cannot read Java-21 class files).
- Android SDK with accepted licenses; AGP auto-installs NDK 29.0.13846066 and
  CMake 3.31.6 on first engine build.
- `NPatch-dev/local.properties` / (build copies re-use the synced tree) with
  `sdk.dir`.
- Network access for the first Gradle run of each engine (maven deps).

## 8. Known limitations / behavior notes

- Patched apps require **Android 9+** (both engines; the manifest gets
  `minSdkVersion=28`). Installing an output on Android <9 fails — on some OEM
  installers this surfaces as "problem parsing the package". Matches the
  upstream projects.
- Integrated mode only: no manager mode, no per-engine keystore choice — the
  final signature always comes from MP-Manager's Sign Settings.
- NPatch bypass levels 0–2; LSPatch 0–3 (level 3 instruments raw syscalls and
  can be detected — LSPatch docs recommend 2).
- The engines load `assets/public.xml`, the keystores and the loader assets via
  the classloader; those files **must** stay in `app/src/main/assets/` at their
  exact paths.
- LSPatch's loader mmaps its native lib straight out of the patched APK
  (`base.apk!/assets/lspatch/so/...`), so those entries must be stored
  uncompressed **and** 4096-aligned — the engine's `Z_FILE_OPTIONS` handles
  that. If any flow ever re-serializes such an APK,
  `ApkZipAlignUtil.alignFor` (apkkit module) keeps `assets/**/*.so` and
  `origin.apk` aligned too; don't narrow it back to `lib/*.so` only.
- Key format quirks: `KeyStore.getDefaultType()` is **JKS** on some Android
  versions and **BKS** on others, while NPatch hardcodes BKS — that is why the
  key is exported in two verified formats instead of one.
- Output naming: `<basename>-npatched.apk` / `<basename>-lspatched.apk` next to
  the source APK.
