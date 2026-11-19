# File-manager usability regressions

## Requested behavior and implementation evidence

| Requirement | Reachable implementation | Regression coverage |
| --- | --- | --- |
| Hardware Menu opens the three-dot menu | `MainActivity.dispatchKeyEvent` and the three-dot click listener share `toggleRightMenuDrawer` | `NavigationRegressionTest.menuKeyOpensAndClosesTheSameDrawerAsTheButton` |
| Option to skip browsing history on Android Back | Behavior page `backNavigatesFoldersToggle` → `SettingsController` → `back_navigates_folders`; selection/search/drawers still dismiss before exit | `NavigationRegressionTest` checks enabled navigation, disabled navigation and independent Up button |
| Explicit Exit needs no confirmation | `RightMenuController.EXIT` invokes `MainActivity.exitApp` directly | Source wiring audit plus external Android 12 Menu → Exit check: process stopped without confirmation |
| Main-settings option for permissions instead of dates outside shared storage | `dialog_settings.xml` → `MainSettingsActivity` → Files page `filePermsSwitch` → `UiPrefs` → `FileIconLoader` | `NavigationRegressionTest.settingsSwitchesPersistBothNewOptions`; `FilePermissionRenderingTest` checks on/off, files/folders, shared storage and chmod plus fresh listing; `FilePermissionsTest` checks mode formatting |
| Show only differing or matching text | Text comparison Show menu → `FilterMode.ALL/DIFFERENT/SAME`; navigation/search use visible rows | `TextDiffEngineTest` filter/alignment tests; `TextComparisonRegressionTest.filtersShowOnlyMatchingOrDifferingRows` |
| Export text differences | Text-comparison Export as .diff → system document picker → UTF-8 unified diff, using complete comparison independent of visible filter | `TextDiffEngineTest` patch round-trips and hunk-count cases; `TextComparisonRegressionTest` checks the picker and bytes written to the returned document URI |
| Generate CRC32 / MD5 checksum files from selected files | Checksums dialog → Generate .sfv / Generate .md5; same-folder selection, filename validation, overwrite confirmation and pane refresh | `HashUtilTest` known vectors, generation and round-trips; `ChecksumRegressionTest` real-file generation/verification; `NavigationRegressionTest.checksumDialogGeneratesBothManifestFormats` clicks both buttons and checks the resulting files and changed-file results dialog |
| Tap checksum files and list changed files | Existing `FileOpener` dispatch preserves original parent for staged manifests; archive entries route via `DexTools`; results show valid/changed/other summary and per-file status | `HashUtilTest` tamper/missing/path rejection/opener tests; `ChecksumRegressionTest.stagingOpenerClassifiesValidChangedMissingAndRejected` |
| Disabled editor line numbers stay disabled on open | All `UnifiedEditorFragment` initialization/preference paths use `resolveShowLineNumbers`; current preference wins over legacy | `EditorRegressionTest.lineNumbersFollowEditorPreferencesOnFileOpen` |
| English and Simplified Chinese resources | New UI text is in `values/strings.xml` and `values-zh-rCN/strings.xml`; other locales unchanged | Resource-key/placeholder audit and Android resource compilation |
| Keep privilege architecture and local environment intact | Existing `PrivilegeManager` mirrors/sessions; no new internal dependencies or backend registration; no build-environment changes | Diff/import audit; full and Lite debug build |
| Local changes only, no push | Work on `feature/navigation-comparison-checksums` | Git branch/status/history audit |

## Commands

Standard build and JVM suite:

```bash
./gradlew assembleDebug :app:testFullDebugUnitTest
```

Runtime regressions, using the existing isolated debug application ID option:

```bash
./gradlew :app:connectedLiteDebugAndroidTest -PeditorRegressionBuild '-Pandroid.testInstrumentationRunnerArguments.class=io.github.abdurazaaqmohammed.utils.NavigationRegressionTest,io.github.abdurazaaqmohammed.ui.main.FilePermissionRenderingTest,io.github.abdurazaaqmohammed.utils.ChecksumRegressionTest,io.github.abdurazaaqmohammed.utils.TextComparisonRegressionTest,io.github.abdurazaaqmohammed.utils.EditorRegressionTest#lineNumbersFollowEditorPreferencesOnFileOpen'
```

## Validation status

Verified on 2026-11-19:

- `./gradlew assembleDebug :app:testFullDebugUnitTest` passed for both Full and Lite debug variants. The full JVM result XML reports **239 tests, 0 failures, 0 errors, 8 skips**, including 38 diff-engine, 21 checksum and 6 permission-format tests.
- All **21 new English resource keys** have Simplified Chinese translations and matching format placeholders. Android resource compilation and `git diff --check` passed. No build-environment or privilege-implementation import changes were introduced.
- External Android 12 check: a hardware Menu key event opened the actual right drawer; tapping Exit once stopped the isolated app process without a confirmation dialog (`pidof` returned no process).
- The final Android 12 integration pass reports **OK (16 tests)**: five navigation/settings/checksum-dialog tests, four permission-rendering tests, three checksum-verification tests, three text-comparison tests (including actual export bytes), and the editor line-number regression. The Up-button assertion waits for asynchronous navigation to finish. The same isolated Lite APK and test APK produced by Gradle were installed on `emulator-5556`, and the test classes above were run with `adb shell am instrument -w -r -e class ... io.github.abdurazaaqmohammed.MPManager.editorregression.test/androidx.test.runner.AndroidJUnitRunner`.

Earlier attempts and recovery: Android 17 emulator runs hit existing slow activity inflation/startup timeouts. Initial permission fixtures were also removed by `MainActivity.onDestroy` cache cleanup; those fixtures now use private files storage. An intermediate compile overlapped an unfinished permission-cache rename, and a subsequent comparison compile caught stale loader declarations; both were fixed before the successful standard build. These failed/superseded runs are not counted as verification passes.

## Scope and remaining device checks

- Generation writes `<folder>.sfv` or `<folder>.md5` next to selected files in one directory. Selections across directories are rejected. Filenames not representable by the unescaped format show an error instead of generating an unverifiable manifest.
- Unix permissions apply only to local non-shared-storage entries. Unavailable metadata falls back to the date; FTP, provider metadata-only views and archive rows retain their existing display.
- Text comparison reads logical lines and exports LF-normalized UTF-8. Final-newline presence is preserved with the standard no-newline marker; CRLF-versus-LF formatting is not preserved as a byte-level change.
- Real root/Shizuku authorization and vendor-specific filesystem behavior require an appropriately configured device. Automated local-file tests do not prove elevated access on every device.
- Explicit Exit stops the application process and services, so it must be verified outside an in-process test. Android Back retains the existing double-press close behavior after transient UI dismissal.
