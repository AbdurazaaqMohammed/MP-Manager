# NPatch Integration (Integrated Mode / `--embed`)

MP-Manager embeds the [NPatch](https://github.com/7723mod/NPatch) patching engine so
users can patch an APK with Xposed modules baked in (NPatch "Integrated Mode").
Patched apps run standalone, without NPatch Manager. The engine runs **in-process**
on-device, exactly like NPatch's own manager app does.

This document explains the architecture and how to **update the integration when
NPatch updates**.

---

## 1. What lives where

| Artifact | Purpose |
|---|---|
| `NPatch-dev/` | NPatch source tree (upstream dev branch state, 2026-10-18). Its `core/` submodule is a clone of HSSkyBoy/Vector at the commit NPatch's dev branch pins. |
| `NeoApk/` | Clone of HSSkyBoy/NeoApk. NPatch's `settings.gradle.kts` picks it up automatically as an included build (`includeBuild("../NeoApk")`) because `top.nkbe:NeoApk` is not published to any Maven repo. |
| `app/libs/npatch-patcher.jar` | Vendored **patcher engine** classes (246 KB). Contains only `top/nkbe/**` (patcher + share + NeoApk), `com/wind/meditor/**` and `pxb/android/**` (axml). Everything else (gson, jcommander, commons-io, kotlin-stdlib, bouncycastle) is already provided by MP-Manager's dependencies or the Android platform. |
| `app/src/main/assets/npatch/loader.bin` | The loader DEX that gets embedded into patched apps (built from `patch-loader` module). |
| `app/src/main/assets/npatch/metaloader.dex` | Metaloader DEX (built from `meta-loader` module). |
| `app/src/main/assets/npatch/so/{arm64-v8a,x86_64}/libnpatch.so` | Native loader core (built from Vector core via NDK). |
| `app/src/main/assets/npatch.key`, `fpa_app.key` | NPatch's built-in signing keystores (BKS, from `jar/src/main/assets`). |
| `app/src/main/assets/mtprovider.dex` | Optional provider dex for the `--provider` option. |
| `app/src/main/assets/public.xml` | **Required.** Android framework attribute-ID table used by the vendored manifest editor (`com.wind.meditor`). Without it, injected manifest attributes get resource-id `-1` and patched APKs fail to parse on Android 9/10+ package parsers ("There was a problem parsing the package"). The official NPatch manager APK ships the same file. |
| `app/src/main/java/io/github/abdurazaaqmohammed/features/apk/NPatchDialogs.java` | UI + engine invocation. Adds a "NPatch (integrated)" entry to the APK info dialog's "More" list (index 12 in `ApkInfoDialogs.java`). |
| `app/src/main/res/layout/dialog_npatch.xml` | Options dialog (Material 3: MaterialSwitch rows, outlined selector buttons, Auto Sign row). |
| `app/src/main/res/values/strings.xml` | All `npatch_*` strings. |
| `tools/build-npatch-artifacts.sh` | One-shot script that rebuilds and repackages everything above from `NPatch-dev`. |

Runtime resources are loaded by the engine via
`getClass().getClassLoader().getResourceAsStream("assets/...")`, which on Android
reads entries straight out of the host APK's zip. That is why the assets must keep
those exact zip paths — placing them in `app/src/main/assets/` does this.

## 2. How a patch runs

`NPatchDialogs.runNPatch()` builds the same CLI arguments NPatch Manager builds
(see `Patcher.Options.toStringArray()` in NPatch's manager module) with
`useManager=false`, then calls `new NPatch(logger, args).doCommandLine()`:

```
-o <source dir> --force -npa|-fpa [-d] -l <0|1|2> [--injectdex] [--allowdown …]
[--override-target-sdk …] -m <module.apk> … [--provider] [--useMicroG]
[--hidelibs] [--cleartext] [--name <label>] [--extract-libs] <target.apk>
```

The engine writes `<name>-<verCode>-npatched.apk` next to the source APK; MP-Manager
renames it to `<name>-npatched.apk` (with `_2`, `_3`… dedup suffixes). Integrated
mode caps the signature-bypass level at 2 (`Constants.SIGBYPASS_HIGH`) — levels 3/4
require NPatch Manager and are rejected by the engine.

After patching, if **Auto Sign** is enabled (same `autosign` preference as the
Optimize dialog), the output is re-signed in place with the user's own key via
`SignWrapper.requireAuth(...)` / `SignWrapper.signApk(file)`; otherwise it keeps
NPatch's built-in V2+V3 signature.

## 3. Updating when NPatch updates

### 3.1 Update the source

```bash
cd NPatch-dev
git -C core fetch origin && git -C core checkout <commit pinned by the new NPatch dev branch>
git -C core submodule update --init --recursive --depth 1
```

The pinned `core` commit is the `sha` shown by
`https://api.github.com/repos/7723mod/NPatch/contents/core?ref=dev`.
To refresh the NPatch tree itself, either `git pull` (if it is a git clone) or
replace the files with the new source drop. Keep `local.properties` (SDK path).

To find out which core commit a *local* source drop pins when it has no git
history, query the GitHub API for `dev` as above (the contents API returns the
gitlink sha; the `html_url` field points at the Vector commit).

### 3.2 Re-apply the two local source patches

The vendored tree carries two fixes that upstream dev needs (as of 2026-10-26);
re-apply them if the update overwrites these files:

1. **`patch-loader/src/main/jni/src/patch_loader.cpp`** — upstream calls
   `RegisterBypass(env)`, `PrepareLibHideSnapshots()` and `RefreshLibHideSnapshots()`
   but never includes the header that declares them, so the native build fails with
   `use of undeclared identifier`. Add `#include "jni/bypass_sig.h"` next to the
   existing `#include "jni/svc_bypass.h"`.
2. **`patch-loader/proguard-rules.pro`** — current R8 (full mode +
   `-allowaccessmodification`) repackages obfuscated classes into the *root*
   package. The metaloader dex also uses root-level names (`a`…`i`), and the
   in-memory loader dex resolves parent-first, so the loader crashes at startup
   with `NoSuchFieldError`. Add:
   ```
   -repackageclasses "npl"
   ```
   (If a future upstream adds its own equivalent, theirs wins.)

### 3.3 Rebuild the artifacts

```bash
# From the repo root. Needs JAVA_HOME pointing at a JDK 21+.
JAVA_HOME="<jdk-21+>" bash tools/build-npatch-artifacts.sh
```

The script:

1. Mirrors `NPatch-dev` (+ `NeoApk`) to `C:\npatch-build` — a **space-free path**,
   because NPatch's native build passes unquoted `-Wl,--thinlto-cache-dir=<path>`
   to clang, which splits at spaces ("OneDrive - wanin" breaks it).
2. Runs `:jar:buildRelease` (builds meta-loader dex, patch-loader dex + native
   libs via NDK, and the fat CLI jar). NDK/CMake are auto-installed by AGP on
   first run; Android SDK licenses must be accepted.
3. Repackages the fat jar down to `app/libs/npatch-patcher.jar` (NPatch-specific
   packages only) and copies `loader.bin`, `metaloader.dex`, `libnpatch.so`,
   `npatch.key`, `fpa_app.key`, `mtprovider.dex`, **and `public.xml`** into
   `app/src/main/assets/`.

Then rebuild and reinstall MP-Manager (`./gradlew :app:assembleDebug`) and smoke
test: patch a small APK with no modules, install the output on an Android 9+
device/emulator, launch it, and check `logcat` for
`NPatch-MetaLoader: Loading native bootstrap` and no `FATAL`.

### 3.4 If classes were added/renamed upstream

The slim jar keeps `top/nkbe/**`, `com/wind/meditor/**`, `pxb/android/**`. If new
NPatch code pulls in another package, the patcher will fail at runtime with
`NoClassDefFoundError` — extend the `keep_prefixes` list in the repackaging step
of `tools/build-npatch-artifacts.sh` and re-run it.

Dependency notes (re-check after updates):
- `jcommander 1.82`, `gson`, `commons-io`, `bcprov` come from MP-Manager — the
  engine's static block registers BouncyCastle from the classpath, and MP-Manager
  ships `bcprov-jdk18on:1.86` (same as what the official manager relies on).
- NeoApk is Kotlin 2.4.20 bytecode → MP-Manager pins
  `org.jetbrains.kotlin:kotlin-stdlib:2.4.20` in `app/build.gradle`. Bump it if
  NeoApk moves to a newer Kotlin.

## 4. Build environment requirements

- JDK 21+ (NPatch targets Java 21; the vendored jar is Java-21 bytecode, which is
  also why `gradle/gradle-daemon-jvm.properties` pins the MP-Manager daemon to
  JDK 25 — javac 17 cannot read Java-21 class files).
- Android SDK with accepted licenses; AGP auto-installs NDK 29.0.13846066 and
  CMake 3.31.6 on first NPatch build.
- `NPatch-dev/local.properties` with `sdk.dir` (same as the root one).
- Network access for the first Gradle run (NeoApk/Vector catalog, maven deps).

## 5. Known limitations / behavior notes

- Patched apps require **Android 9+** (NPatch design; the manifest gets
  `minSdkVersion=28`). Installing an output on Android <9 fails — on some OEM
  installers this surfaces as "problem parsing the package". This matches the
  official NPatch.
- Integrated mode only: no `--manager`, sig-bypass capped at level 2.
- The engine loads its keystores (`npatch.key`/`fpa_app.key`) and the manifest
  attribute table (`public.xml`) via the classloader; all three **must** stay in
  `app/src/main/assets/`.
- Output naming: `<basename>-npatched.apk` next to the source APK.
- Debug builds of MP-Manager are fine for testing; the engine is independent of
  the app's signing config.
