# Architecture

> Working document — the map for the restructure described in
> [ARCHITECTURE_PLAN.md](ARCHITECTURE_PLAN.md). Update the mapping tables
> as stages land. Checked-off stages in the plan are reflected here.

## Target structure (7 layers, one dependency direction)

```
io.github.abdurazaaqmohammed/
├── app/        # shell: Application, ServiceLocator, startup, UpdateController
├── ui/         # everything visible (Activity/Fragment/Adapter/Dialog/custom views)
│   ├── main/       ← adapters/ + ui/ + UI bits of core/ + listeners/
│   ├── viewer/     ← player/ + drawable/       (pure display, no logic)
│   ├── editor/     # text / dex / arsc editor screens
│   └── common/     ← core/ (BaseDialog, themes, fonts, popup menus)
├── feature/    # business domains, one subpackage each, exposing only XxxController
│   ├── files/      # existing — keep
│   ├── apk/        ← ApkExtractor/ + arsc/ + dex/signature logic
│   ├── system/     ← tools/ — ToolRegistry is a good pattern; generalize it
│   ├── boot/       ← boot/
│   ├── ftp/        ← MPManager/, split: profiles/logic here, UI to ui/
│   ├── freestyle/  ← freestyle/
│   └── text/       ← commandhelper/
├── plugin/     # ← plugins/: host/registry/trust, pairs with the packs/* SDK
├── mcp/        # ← mcp/: self-contained, stays top-level (crosses domains)
├── privilege/  # the privilege layer — see ARCHITECTURE_PLAN.md Problem 1
├── data/       # persistence: SharedPrefs / caches / stores ← data/ + scattered prefs
├── domain/     # pure models & pure logic ← domain/; depends on nothing
└── util/       # pure helpers by topic: util/{io,archive,hash,text,net}
                # ← utils/ minus privilege/business classes; no new business allowed
```

## Dependency rules

A DAG — `ui → feature → domain`; `data / privilege / plugin / mcp / util`
are leaf services usable by anyone, dependent on nothing business-related:

1. `domain` and `util` may not import any other first-party package;
2. `feature` may not import `ui` — needs a dialog? define a callback,
   inject it from ui;
3. `privilege` exposes only `PrivilegeManager`;
4. every top-level package carries a `package-info.java` stating its
   contract. No CI import-lint yet — until then this is a review rule.

### Known deviations (accepted for now, review rules apply to new code)

- Some `feature/` subpackages still contain Activities/Services/Views
  (`feature/system` tools screens, `feature/freestyle` canvas). Splitting
  UI out of them is deferred; do not add new UI to `feature/`.
- `privilege/impl/` (RootManager, AccessManager, Shizuku*, Native*, ipc)
  is implementation, but ~30 feature/ui files still call it directly
  (mostly `RootManager`'s instance API). New code must go through
  `PrivilegeManager`; migrating the remaining call sites onto the session
  interfaces is the open remainder of plan stage M5.
- `rikka/shizuku/ShizukuProcessFactory` stays in `rikka.shizuku` — it
  exists to reach a package-private ShizukuRemoteProcess constructor
  there and cannot move.
- Vendored trees out of scope: `modder.hub.dexeditor`, `com.apk.axml`,
  `apkkit`, `sdk` module (`io.github.abdurazaaqmohammed.ui` /
  `io.github.abdurazaaqmohammed.utils` there are sdk-owned), `packs/*`.
- `sdk` keeps its own `ui`/`utils`/`domain` packages; they belong to the
  plugin SDK, not the app layers above.

## Move mapping (per stage)

### A1 — UI five-into-one (`~65 files`)

| from | to |
|---|---|
| `adapters/` (incl. `adapters/main/`) | `ui/main/` |
| `ui/` top-level helpers & views (`UIHelper`, `MyDrawerLayout`, `ColorWheelView`, `PaneHighlightView`) | `ui/main/` |
| `listeners/` | `ui/main/` |
| `ui/activities/`, `ui/fragment/`, `ui/dialogs/` (editor screens) | `ui/editor/` |
| `player/` | `ui/viewer/` |
| `drawable/` | `ui/viewer/` |
| `core/ui/` (base/theme/util) | `ui/common/` |

### A2 — domains absorbed into `feature/` (`~50 files`)

| from | to |
|---|---|
| `tools/` | `feature/system/` |
| `arsc/` | `feature/apk/` |
| `ApkExtractor/` | `feature/apk/` |
| `boot/` | `feature/boot/` |
| `MPManager/ftp/` (profiles + logic) | `feature/ftp/` |
| `MPManager/` UI (`MainActivity`, `ProfileAdapter`, receivers) | `ui/main/` / stay adjacent per file, see commit |
| `MPManager/shizuku/` | `privilege/legacy/` (with M1) |
| `freestyle/` | `feature/freestyle/` |
| `commandhelper/` | `feature/text/` |

### A3 — `utils/` split (`~119 files, ≤30 per PR`)

| from (`utils/…`) | to |
|---|---|
| privilege classes (13 — see M1 list) | `privilege/legacy/` |
| prefs/settings holders (`UiPrefs`, `FolderPins`, `MinimizedEditors`, `SearchHistoryHelper`, `SearchHistoryDropdown`, …) | `data/prefs/` |
| IO/storage/transfer (`FileUtils`, `StorageUtil`, `SafFile`, `SafFs`, `CopyUtil`, `FileChannelCopy`, `NativeCopyProcess`, `SharedStorageCompat`, `MultiFileSeekableByteChannel`, `RecycleBin`, `RenameUtil`, `RenameHelper`, `DirectoryStats`, `FileTreeStats`, `DestSizePoller`, `TransferControl`, `ZipTransferControl`, `TransferCancelledException`, …) | `util/io/` |
| archive (`ArchiveUtil`, `ArchiveBrowseUtil`, `ArchiveBackupUtil`, `SplitArchiveUtil`, `ZipCompat`, `MpZstdInputStream`, `MpZstdOutputStream`, `ZipProgressPump`, `ZstdBridge`, `JpegtranJni`…) | `util/archive/` |
| hash/crypto (`HashUtil`, `PasswordEncryptor`, `BytePatternSearch`, `HexPageCache`, `HexViewport`, …) | `util/hash/` |
| text/format (`MimeUtil`, `ApkInfoUtil`, `FileSize`, `CollectionsUtils`, `CompareUtils`, `AppGson`, …) | `util/text/` |
| net (`AdbClient`, `AdbFileService`, `AdbSync`, `DnsManager`, `WifiPasswordUtil`, …) | `util/net/` |
| unclassifiable remainder | `util/misc/` (**no-growth rule**) |
| APK/business logic (`ApkOptimizer`, `ApkDeepOptimizer`, `Dex*`, `InstallUtil`, `PrivilegedInstaller`, signing, `Il2CppDumperUtil`, …) | `feature/apk/` / `privilege/` as ownership dictates |

Final layout is whatever the commits produce; the invariant that matters
is: **`util/` contains no business logic and no privilege code.**

### A4 — `data/` consolidation

| from | to |
|---|---|
| scattered SharedPrefs users not already in `data/prefs/` | `data/prefs/` |
| uppercase `MPManager/` package rename | happens with A2/M1 moves |

### M1–M5 — privilege layer

See [ARCHITECTURE_PLAN.md](ARCHITECTURE_PLAN.md) Problem 1 for the
`privilege/` tree, session interfaces and registry rules. Backend
selection order: `DHIZUKU > SHIZUKU > ROOT > NONE` (registered in
`App.onCreate`; registry-driven selection is exposed as
`PrivilegeManager.preferredBackend`, default routing unchanged).

| from | to |
|---|---|
| `utils/` privilege classes (17 — RootManager, RootFile, RootPermissionHelper, RootStaging, ShellProcess, ShellBatchControl, ShellProgress, NativeRootManager, NativeProcess, NativeToolManager, PrivilegedInstaller, StartupPrivileges, ShizukuManager + the native-copy engine quartet) | `privilege/legacy/` → renamed `privilege/impl/` in M5 |
| `utils/AccessManager` | `privilege/impl/` |
| `shizuku/` (`ShizukuConnection`, `ShizukuFileService`) + `IFileService.aidl` | `privilege/impl/ipc/` |
| `MPManager/shizuku/` (`ShizukuShell`, `ShizukuFile`, `ShizukuFileOps`) | `privilege/impl/` |
| `rikka/shizuku/ShizukuProcessFactory` | stays (see deviations) |
| M2: new `PrivilegeManager`, `ShellSession`, `FileOpsSession`, `InstallerSession` | `privilege/api/` |
| M3: `RootBackend`, `ShizukuBackend`, `FallbackBackend`, `Registry`, capabilities | `privilege/spi/`, `privilege/backend/`, `privilege/internal/` |
| M4: `DhizukuBackend` + `io.github.iamr0s:Dhizuku-API` | `privilege/backend/` |
| M5: `privilege/legacy/` dissolved into `privilege/impl/`; unit tests relocated | — |

The old top-level `utils/`, `shizuku/` and `MPManager/` packages are gone.
