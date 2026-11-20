# MP Manager symbol drawer integration

This module vendors `zero-Symbol-input-view-NEW/zero-symbol-input-view-v2`, the
editor-neutral V2 library supplied with this update. The supplied editor demo,
editor fork, native engines, publishing scripts and build conventions are not
part of the app integration. See `UPSTREAM.md` and the bundled license files for
upstream documentation and notices.

MP Manager uses `SymbolDrawerView` in `fragment_unified_editor.xml` for both text
and DEX/smali editors. The Sora adapter lives in the app's `ui/editor/symbols`
package; the library has no Sora dependency. `SymbolDrawerActivity` hosts the
manager and settings with the app theme, toolbar navigation and existing insets
handling. The drawer remains below the editor in its measured bottom container;
BaseActivity owns IME/system-bar insets.

`SymbolDrawerMigration` runs before view inflation. Existing oldest-format bottom
bar preferences first pass through the existing V1 migration, then V1 groups are
imported once into the independent V2 store. V1 preferences remain available for
rollback; subsequent manager edits use V2 only. Existing column/row, text-size,
indicator and expansion preferences are mapped to V2 equivalents. V2 provides
additional settings with its defaults where there is no equivalent.

Local adaptations from the supplied source:

- A standalone Groovy build follows the host's SDK/Java/AGP setup; consumer R8
  rules preserve reflected settings and model data.
- Legacy IDs 33/34 keep MP Manager's copy/cut-line clipboard semantics.
- MP Manager actions 100/101/102 remain available in the manager. Find/replace
  keeps both replacement text and r/c/a options for short and long presses.
- The V1 codec supports MP Manager's f/g/h/i fields and round-trips them through
  the typed V2 backup/store. V2 full backups preserve typed actions and IDs.

The old `zero-symbol-input-view` module remains only for the oldest preference
migration; active app navigation opens the V2 manager.

Verification commands (run from the repository root):

```
./gradlew :zero-symbol-input-view-v2:testDebugUnitTest
./gradlew :app:testFullDebugUnitTest
./gradlew :app:assembleFullDebug :app:assembleFullDebugAndroidTest -PeditorRegressionBuild
```

Run `SymbolDrawerIntegrationTest` and `FloatingMenuRowThemeInstrumentedTest`
against the isolated `.editorregression` package. The former exercises the real
Sora adapter, actual short/long drawer interactions, custom actions, one-way
migration, clipboard semantics, read-only behavior and host lifecycle reload.
