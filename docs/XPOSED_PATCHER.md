# Xposed Patcher Integration (LSPatch, Integrated Mode)

MP-Manager embeds the [JingMatrix/LSPatch](https://github.com/JingMatrix/LSPatch)
fork (`LSPatch-master/`) as its integrated-mode Xposed patcher engine,
reachable from the APK dialog's "Xposed patcher" entry. It patches an APK with
Xposed modules baked in (embedded-modules mode), so the result runs standalone
without a manager app. The engine runs **in-process** on-device, exactly as its
own manager app would.

> **`LSPatch-master/` is not included in this repository.** Only the prebuilt
> artifacts are tracked (`app/libs/lspatch-patcher.jar`,
> `app/libs/patcher-common.jar`, `app/src/full/assets/lspatch/…`). To rebuild
> them, fetch the source yourself first:
>
> ```bash
> git clone --recursive https://github.com/JingMatrix/LSPatch LSPatch-master
> ```
>
> `tools/build-lspatch-artifacts.sh` fails immediately if the tree is missing.

The NPatch engine that used to ship alongside LSPatch has been removed: the
`top/nkbe/npatch/**` classes, its loader assets (`assets/npatch/`,
`npatch.key`, `fpa_app.key`, `mtprovider.dex`) and `tools/build-npatch-artifacts.sh`
are gone. The shared vendored classes both the old NPatch engine and LSPatch
link against (the aXML manifest editor and the NeoApk signer) live on in
`app/libs/patcher-common.jar`.

The user's MP-Manager signing key (Sign Settings) is **handed to the engine**
(SignWrapper.exportKey writes it into a keystore the engine reads —
`KeyStore.getDefaultType()`, with a BKS fallback), and the engine signs its
output in place with it. When "Auto Sign" is unchecked the engine's built-in
keystore is used instead. Never re-sign a patcher output with apksign
afterwards: LSPatch *data-multiplexes* its APKs (unchanged entries are links
into the stored nested origin APK), and any zip re-serialization — apksig or a
zipalign rebuild — materializes those links and roughly doubles the file size.

This document explains the architecture and how to **update the integration
when upstream LSPatch updates**.

---

## 1. What lives where

| Artifact | Purpose |
|---|---|
| `LSPatch-master/` | JingMatrix LSPatch source tree (master, 2026-10-27). `core/` = JingMatrix/LSPosed clone (branch master) with its own submodules initialized (commons-lang3, manifest-editor, lsplant, lsplt, …). No git metadata: the build script seeds a throwaway repo in the build copy. |
| `app/libs/lspatch-patcher.jar` | LSPatch patcher engine classes. Keeps `org/lsposed/**` and `com/android/tools/build/apkzlib/**`; everything else is excluded (see `tools/build-lspatch-artifacts.sh`). |
| `app/libs/patcher-common.jar` | Shared vendored classes the engine links against: `com/wind/meditor/**` + `pxb/android/**` (aXML manifest editor, byte-identical copies originally built from the NPatch tree) and `top/nkbe/nza/**` (NeoApk raw-file V2/V3 signer used by APK multiplexing). gson / jcommander / commons-io / bcprov come from MP-Manager; kotlin-stdlib is pinned in `app/build.gradle` because NeoApk is Kotlin 2.4.20 bytecode. |
| `app/src/full/assets/lspatch/…` | LSPatch loader runtime: `loader.dex` (no R8 — the module ships unminified), `metaloader.dex`, `so/{armeabi-v7a,arm64-v8a,x86,x86_64}/liblspatch.so` (4 ABIs). LSPatch's built-in signing keystore goes to the zip root as `assets/keystore` (`app/src/full/assets/keystore`), matching `KeystoreSpec.builtIn()`. |
| `app/src/full/assets/public.xml` | Android framework attribute-ID table used by the vendored manifest editor (`com.wind.meditor`). Without it, injected manifest attributes can get resource-id `-1` and patched APKs fail to parse on Android 9/10+ package parsers ("There was a problem parsing the package"). |
| `app/src/full/java/io/github/abdurazaaqmohammed/features/apk/XposedPatcherDialogs.java` | UI. "Xposed patcher" entry in the APK info dialog's "More" list (`ApkInfoDialogs.java`, menu id `"npatch"` in `ApkMoreMenuOrder` — the id is persisted in user prefs, so it stays). |
| `app/src/full/res/layout/dialog_xposed_patcher.xml` | Options dialog (Material 3). |
| `tools/build-lspatch-artifacts.sh` | Rebuilds the LSPatch artifacts from `LSPatch-master`. |

The engine loads its runtime resources via
`getClass().getClassLoader().getResourceAsStream("assets/…")`, which on Android
reads entries straight out of the host APK's zip — the assets must keep those
exact zip paths, which placing them in `app/src/full/assets/` does.

## 2. How a patch runs

`XposedPatcherDialogs.startPatch()` resolves the user's signing key first
(`SignWrapper.requireAuth`, UI thread, may password-prompt), exports it to
cache (`SignWrapper.exportKey`, default keystore type with a BKS fallback),
then on a worker thread runs the engine with `-k <exported keystore>`. The
engine signs in place (V2 via its apkzlib `SigningExtension`), preserving the
multiplexed layout. The exported keystore lives in the app-private cache and
is deleted on exit.

`new LSPatch(args).toSpec()` → `new ApkPatcher(logger, spec).patch()` — the
engine's own CLI front end and spec builder. Output:
`<name>-lspatched.apk`.

The engine writes `<name>-<verCode>-lspatched.apk` into the output directory;
the dialog picks the newest and normalizes the name (dedup with `_2`, `_3`…).

## 3. Updating LSPatch when it updates

The source tree is fetched separately (see the notice at the top) and is not
tracked by this repo:

```bash
# fetch or refresh the source, including core's own submodules
git clone --recursive https://github.com/JingMatrix/LSPatch LSPatch-master
```

If you re-clone `core`, remember its own submodules (the build fails with
`cannot find symbol` in `core/external/apache/local/MemberUtilsX.java` and
`package com.android.tools.build.apkzlib…` style errors otherwise).

### 3.1 Re-apply the local source patches

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

### 3.2 Rebuild

```bash
JAVA_HOME="<jdk-21+>" bash tools/build-lspatch-artifacts.sh
```

The script mirrors the tree to `C:\lspatch-build\LSPatch-master` (**delete +
`cp -r` sync with a file-count check** — OneDrive cloud placeholders make
robocopy silently skip files, leaving empty dirs; and the path must be
**space-free** — the native build passes an unquoted
`-Wl,--thinlto-cache-dir=<path>` which clang splits at spaces), **seeds a
minimal git repo there** (the root build reads its version code via JGit and
NPEs without a `.git`), runs `:jar:buildRelease` (which chains
`meta-loader:copyDexRelease` + `patch-loader:copyDexRelease` +
`patch-loader:copySoRelease`), repackages the fat jar to
`app/libs/lspatch-patcher.jar`, and copies the 4-ABI loader assets +
`keystore` into `app/src/full/assets/lspatch/`.

Notes:
- LSPatch's `patch-loader` ships **unminified** (`isMinifyEnabled = false`).
- Its `patch-loader/proguard-rules.pro` still contains legacy Xpatch-era keep
  rules; they are inert at the current minify setting.
- The `.gitmodules` `apksigner` entry is vestigial — nothing in the build
  references it; don't bother cloning googlesource.

### 3.3 If classes were added/renamed upstream

- Slim jar: extend `keep_prefixes` in `tools/build-lspatch-artifacts.sh`.
- The aXML/manifest-editor classes the engine links against come from
  `app/libs/patcher-common.jar`; if upstream starts using newer
  `com/wind/meditor` or `pxb/android` copies, rebuild that jar from the
  updated sources (it can be regenerated by repackaging the LSPatch fat jar's
  `com/wind/**` + `pxb/**` entries, or from the manifest-editor sources under
  `LSPatch-master/core`).
- Re-check dependency pins in `app/build.gradle`: `kotlin-stdlib` ≥ NeoApk's
  Kotlin (currently 2.4.20); gson / jcommander / commons-io / bcprov must
  stay since the slim jars rely on them.

## 4. APK data multiplexing (standalone function, full flavor)

The APK dialog's "Multiplex APK (deduplicate)" entry runs MP-Manager's own
`ApkDataMultiplexer` (full flavor): it detects the stored embedded APK
(`assets/lspatch/origin.apk`, `assets/npatch/origin.apk`, `assets/base.apk`,
or the largest stored ZIP entry whose central directory parses), and for every
outer entry that is byte-identical (same name, data, method, sizes, CRC) to an
entry inside the host blob it drops the outer local header + data and repoints
the central-directory offset at the matching local header inside the blob.
The `assets/npatch/origin.apk` probe stays in the list so APKs patched by the
standalone NPatch Manager app still multiplex.
The host bytes are copied verbatim and re-aligned to 4096, and the entry
region is padded to 4096 before the central directory so the raw-file V2/V3
signer (NeoApk, vendored in `patcher-common.jar`) can sign the result in place
— the only signing that preserves the multiplexed layout. Entries that need
page alignment (`lib/*.so`, `resources.arsc`, stored `assets/*.so`) are never
redirected, only re-aligned. Works on both materialized and already-multiplexed
patcher outputs (the latter simply save less).

## 5. Full / lite build variants

The engine is large (~4 MB of assets + classes), so the app ships in two
product flavors:

- **full** — engine bundled (`fullImplementation` deps in `app/build.gradle`,
  loader assets under `app/src/full/assets/`, patcher dialog under
  `app/src/full/java/`).
- **lite** — no engine. Tapping "Xposed patcher" explains that the feature
  needs the full version. "Multiplex APK" still works.

Builds: `./gradlew :app:assembleFullDebug` / `:app:assembleLiteDebug`
(release: `assembleFullRelease` / `assembleLiteRelease`). The seam between
flavors is `features/apk/XposedPatcherLauncher`, which exists in both
`src/full` and `src/lite` with the same signature; everything else lives in
`src/main` untouched.

## 6. Build environment requirements

- JDK 21+ (the vendored jar is Java-21 bytecode, which is also why
  `gradle/gradle-daemon-jvm.properties` pins the MP-Manager daemon to JDK 25 —
  javac 17 cannot read Java-21 class files).
- Android SDK with accepted licenses; AGP auto-installs NDK 29.0.13846066 and
  CMake 3.31.6 on the first engine build.
- Network access for the first Gradle run of the engine (maven deps).

## 7. Known limitations / behavior notes

- Patched apps require **Android 9+** (the manifest gets `minSdkVersion=28`).
  Installing an output on Android <9 fails — on some OEM installers this
  surfaces as "problem parsing the package". Matches upstream.
- Integrated mode only: no manager mode, no keystore choice — the final
  signature always comes from MP-Manager's Sign Settings.
- Signature bypass levels 0–3 (level 3 instruments raw syscalls and can be
  detected — LSPatch docs recommend 2).
- The engine loads `assets/public.xml`, the keystore and the loader assets via
  the classloader; those files **must** stay in `app/src/full/assets/` at
  their exact paths.
- LSPatch's loader mmaps its native lib straight out of the patched APK
  (`base.apk!/assets/lspatch/so/...`), so those entries must be stored
  uncompressed **and** 4096-aligned — the engine's `Z_FILE_OPTIONS` handles
  that. If any flow ever re-serializes such an APK,
  `ApkZipAlignUtil.alignFor` (apkkit module) keeps `assets/**/*.so` and
  `origin.apk` aligned too; don't narrow it back to `lib/*.so` only.
- Key format quirk: `KeyStore.getDefaultType()` is **JKS** on some Android
  versions and **BKS** on others — the key is exported in the default type,
  falling back to BKS if that fails.
- Output naming: `<basename>-lspatched.apk` next to the source APK.
