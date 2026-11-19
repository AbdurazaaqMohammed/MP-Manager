# Editor, archive-name and symbol-drawer update

## Scope and verification

| Request | Implementation | Evidence |
| --- | --- | --- |
| White boxes in Edit floating menus with a dark theme | Activity uses BaseActivity/ThemeRegistry; row surface, icon tint and title use theme attributes | FloatingMenuRowThemeTest; FloatingMenuRowThemeInstrumentedTest checks rendered rows in system light, system dark, and app-selected dark with a light OS |
| Chinese filenames garbled inside an archive and after extraction | Shared per-entry ZIP name decoder; UTF-8, legacy GBK/Big5 heuristics, CP437 fallback and CRC-validated Unicode Path metadata; zip4j handles use matching names/charsets | ZipCharsetTest covers listing, opening, extraction to disk, Unicode extras, mixed-name mutation and Western/UTF-8 preservation; ZipCompatTest covers directory metadata |
| Integrate supplied new symbol bar | Vendored independent V2 library, live editor callbacks, app-owned Sora adapter, themed manager/settings Activity, custom-action editing, non-destructive V1 migration | 351 drawer JVM tests plus SymbolDrawerIntegrationTest on the actual Sora AAR |
| Preserve editor behavior during upgrade | Exact macro insertion, Unicode deletion, read-only gates, copy/cut semantics, short/long press, manager/settings navigation and reload on resume | 11 SymbolDrawerIntegrationTest cases on Android API 31 |
| Customized floating menus continue to work | Optional Paste/Cut buttons are null-safe | Device regression constructs menus omitting/disabling both buttons and updates selection |
| Archive search uses names that can be opened | Name/content/XML search uses ZipCompat directly; search results retain decoded paths and epoch timestamps | 9 FileSearchTest cases with real GBK ZIP fixtures |
| Do not push | Local branch and commit only | No push operation performed; supplied NEW tree is untouched and excluded from the commit |

## Checks performed

Run from repository root:

```
./gradlew :app:assembleFullDebug :app:assembleFullDebugAndroidTest :app:testFullDebugUnitTest :zero-symbol-input-view-v2:testDebugUnitTest -PeditorRegressionBuild
./gradlew :app:assembleFullRelease :app:compileLiteDebugJavaWithJavac
```

The final app JVM suite has 292 tests: 285 passed, 7 skipped, zero failures or
errors. Six skips require the native Android Zstandard library, unavailable on
the desktop JVM; one requires an explicitly configured live SMB server. The V2
suite has 351 tests, all passed. The focused device run has 14 tests, all passed
(11 editor/drawer integration tests and 3 rendered-row theme tests).

Device tests used the isolated `.editorregression` application on an API 31
read-only emulator instance. The main installed app was not replaced. The
already-running preview emulator had package-manager/instrumentation timeouts;
the successful test evidence comes from the separate clean instance.

## Integration notes

The original supplied `zero-Symbol-input-view-NEW` tree remains unchanged. Only
the independent V2 source/resources/tests/notices are vendored into the new root
`zero-symbol-input-view-v2` module; upstream editor/demo/native/build scripts are
not imported. See that module's README for local adaptations and the included
license notices. The old module remains available for the oldest bottom-bar
preference migration; all active app manager navigation now opens V2.

The editor keeps the drawer in its measured bottom container and lets
BaseActivity own keyboard/system insets. No manual keyboard translation is
applied. Adaptive symbol grids remain part of V2; the host keeps its existing
bottom placement.

## Remaining limits

Legacy ZIP filenames do not identify their encoding, so GBK versus Big5 and
unusual Western byte sequences remain heuristic. UTF-8 flags and valid Unicode
Path metadata take precedence. Rare GBK extensions with no strong Hanzi-area
byte-pair evidence can fall back to the Western decoder. Legacy RAR charset
handling is unchanged. Encrypted mixed-charset split ZIP naming correction is
best-effort if the central directory cannot be read. No original user-reported
archive was supplied; verification uses controlled archive fixtures.
