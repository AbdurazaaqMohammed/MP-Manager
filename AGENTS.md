# AGENTS.md

## Build & Test

- Build: `bash build.sh` — picks JDK 17/21 if needed, runs `./gradlew assembleDebug`, then **auto-installs to any connected adb device** (`adb install -r`). Add `echo` guard or run `./gradlew :app:assembleDebug` directly to avoid the install.
- `gradlew` must keep the executable bit (mode 755). If `./gradlew: 权限不够` appears, run `chmod +x gradlew && git update-index --chmod=+x gradlew`.
- Tests are plain JUnit 4 on the JVM (`app/src/test`, no Robolectric — `android.os.Build`/`TextUtils` are stubbed locally). Run one class:
  `./gradlew :app:testDebugUnitTest --tests "io.github.abdurazaaqmohammed.utils.ApkRebuildTest"`
- No CI, no lint gate. `app/lint.xml` is a lint baseline, not a task that runs by default.
- `dependencyResolutionManagement` uses `FAIL_ON_PROJECT_REPOS`: only the root `build.gradle` may declare repositories.

## Modules

- `:app` — the Android app (all features under `app/src/main/java/io/github/abdurazaaqmohammed/`: `features/`, `tools/`, `player/`, `arsc/`, `ui/`, `plugins/`). Pure Java, no Kotlin.
- `:sdk` — plugin API (`plugins.api`, `plugins.ext`) and IPC contracts (`plugins.ipc.PluginContracts`), shared verbatim with external plugins.
- `:packs:pack-{math,text,time,random,device,media,network}` — first-party in-process tool packs, loaded via `DexClassLoader`.
- `:samples:plugin-sample` — external (out-of-process) plugin example implementing all five intent areas.

## Plugin architecture (two different things)

- **In-process packs** are first-party only: `PackManager` refuses APKs not signed with the host certificate (debuggable builds skip the check for local development). Release packs must list their SHA-256 in `app/src/main/assets/packs.json` — empty checksum = install refused.
- **External plugins** are normal apps integrating via 5 explicit-intent actions (`...action.SIDEBAR_OPEN/SETTING_CONFIG/FILE_MENU/EDITOR_ACTION/APK_ACTION`) discovered through `<queries>` in the manifest, with certificate pinning on first use. Contract details: `docs/THIRD_PARTY_PLUGINS.md`.
- Files cross the external boundary only as `content://` URIs with one-shot grants — never raw paths.

## Manifest conventions

- Exactly **one LAUNCHER activity** (`MainActivity`). Adding another (e.g. `ToolsHubActivity`) makes the launcher show two icons — that was already fixed once; internal activities must stay `exported="false"` without intent-filters.
- Known-harmless build warnings: legacy `package=` attribute in the manifest, duplicate `CAMERA` permission.

## Toolchain

- minSdk 19 / targetSdk 37 / compileSdk 36; Java 17 with coreLibraryDesugaring; multidex enabled.
- Many dependencies are vendored jars in `app/libs/` (sora-editor aars, APKEditor-related, commons-*, etc.) — not on any Maven repo.
- `local.properties` holds `sdk.dir` (machine-specific, not committed).
- `.opencode/` is local tooling; do not commit it.
