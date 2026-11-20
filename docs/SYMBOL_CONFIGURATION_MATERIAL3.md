# Bottom-bar configuration Material 3 follow-up

The bottom-bar configuration screens mixed platform widgets with Material views.
This update replaces the mismatched controls without changing stored settings,
action IDs, JSON formats or migration.

| Configuration surface | Change | Verification |
| --- | --- | --- |
| Settings toggles | MaterialSwitch | Light/dark device hierarchy checks; toggle persists showGroupTitles |
| Numeric settings | Material Slider with integer steps and original ranges, including negative spacing | Device asserts integer steps and valid bounds; existing settings JVM suite |
| Enum/action choices | Outlined TextInputLayout with select-only MaterialAutoCompleteTextView | Device checks exposed-dropdown types and absence of framework Spinners |
| Group/symbol/payload/replacement/search inputs | Outlined TextInputLayout and TextInputEditText | Device opens symbol edit form and asserts outlined field mode |
| Find/replace options | MaterialCheckBox | Device verifies actual checkboxes in symbol form |
| Manager and import/confirmation dialogs | MaterialAlertDialogBuilder with AppCompat AlertDialog | Real manager edit dialog opens; cancellation/navigation regression checks |
| Header, undo, batch, backup actions | Theme-styled outlined/text MaterialButtons | Removed manually assigned tint/stroke/corner overrides; builds and real screen checks |
| Configuration app bar and fallback settings dialog | MaterialToolbar and MaterialAlertDialogBuilder | Device hierarchy and existing manager/settings integration checks |

Checks passed:

- Full debug app + instrumentation build.
- 351 drawer unit tests, zero failures.
- Application unit suite (292 cases, seven existing environment-dependent skips), zero failures.
- Lite debug compilation.
- 14 Android API 31 device tests: three new Material configuration tests plus
  11 existing editor/drawer integration tests. All passed.

Commands used from repository root:

```
./gradlew :app:assembleFullDebug :app:assembleFullDebugAndroidTest :zero-symbol-input-view-v2:testDebugUnitTest -PeditorRegressionBuild
./gradlew :app:testFullDebugUnitTest :app:compileLiteDebugJavaWithJavac
```

Device tests target the isolated `.editorregression` package. The supplied
`zero-Symbol-input-view-NEW` tree remains untouched. Logs and captured light/dark
settings and dark symbol-editor PNGs are in the ignored
`build/reports/material-config` directory.
