# Plugin smoke tests

Use a debug host built with `-PeditorRegressionBuild` so it installs as
`io.github.abdurazaaqmohammed.MPManager.editorregression`, not over a release or its
user data. Use the normally installed plugin and theme samples with their own
package IDs. This is a checklist, not a claim that every check has been executed.

## Build and install

```sh
./gradlew -p plugin-sdk test publishToMavenLocal
./gradlew -p samples/plugin-template assembleDebug
./gradlew :app:assembleFullDebug :samples:plugin-sample:assembleDebug :samples:theme-sample:assembleDebug -PeditorRegressionBuild
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
adb install -r samples/plugin-sample/build/outputs/apk/debug/plugin-sample-debug.apk
adb install -r samples/theme-sample/build/outputs/apk/debug/theme-sample-debug.apk
```

Check actual output names in the build directories before installing. Do not
replace a differently signed package, clear release data, or uninstall user apps
just to make a test pass.

## Native actions and trust

1. Enable plugin rows in sidebar settings if disabled. Open the sample row;
   decline the first-seen certificate prompt and verify no activity starts.
2. Retry and allow the displayed certificate. Check the sample screen receives
   host capabilities, light/dark mode, and palette values.
3. Open the main toolbar's plugin-actions button. Verify only native host menu
   rows appear; click the sample action.
4. Open a text file's context menu. Verify `filemenu.extra` respects MIME/pattern
   filters and receives a read-only content URI, not a filesystem path.
5. Open an ELF file's options. Verify the extra action receives read-only data.
6. Open an APK's More menu. Verify the extra action gets a staged copy; cancel
   leaves the original unchanged. A successful rewrite imports a separate result.
7. Select text in the editor and use its extra floating-menu action. Verify the
   selection transformation and undo, and that an oversized selection is declined
   rather than partially transformed.
8. Hide a hook in Tools Hub. Verify it disappears from its surface; show it again.
   Reorder toolbar/ELF actions in Tools Hub, and sidebar/file/editor/APK actions in
   the existing customizers. Reopen the surfaces and verify persisted order.
9. Disable the sample package. All action hooks disappear, but Tools Hub still
   lists the package for re-enabling. An untrusted reinstall must prompt again.

## Events and reverse theme requests

1. Trust the action plugin but do **not** grant automatic events. Verify its
   receiver sees no host events.
2. Allow automatic events in Tools Hub and reopen a file. Inspect only the sample's
   receiver output/log: `file_name` + `mime`, palette/common metadata, no raw path,
   content, editor text, or URI grant.
3. Start a fresh host process; verify one `app.launch` notification. Excess events
   may be dropped; this is not an ordered/durable stream.
4. Disable events or the package; no further notifications arrive.
5. From a result-capable action with a provider in its own package, request a
   theme ID. Cancel host confirmation and verify no change; confirm and verify
   the provider's **matching** ID imports. Different IDs/packages/URIs must not
   bypass validation.

## Theme v1/v2 and live editor state

1. Import a provider serving contract 1 with the original columns; verify its
   palette/labels/file-pane wallpaper still apply.
2. Import the v2 theme sample. Verify Material surface/accent colors on API 30+
   where supported, editor gutter/line numbers/selection/current line/matches,
   and canonical syntax category colors for text/Smali.
3. Open two unsaved text-editor tabs. Record text, selected range, scroll,
   undo/redo availability, and modified markers. Minimize the editor.
4. Change themes in Settings and resume the minimized editor. Verify buffers,
   tabs, selection, scroll and undo history survive; colors update in place.
5. Repeat with a DEX/Smali editor and an unsaved ARSC text edit.
6. Check `wallpaper_scope=files/all/none`. `all` covers host activity backgrounds,
   not opaque controls or Android-owned permission prompts/keyboards.
7. Update the selected theme package under the same certificate. Verify new data
   applies without reselecting or restarting. A malformed provider/image update
   must keep the previous valid snapshot.
8. During a deliberately slow update refresh, switch to another theme. The stale
   refresh must not reactivate the old selection.
9. Uninstall/change the theme signer; verify deactivation and safe built-in
   fallback. Preserve action-plugin trust separately.
10. On API 23–29 verify the documented closest-accent Material fallback, direct
    app/editor colors, readable system-bar icons, and no resource-loader crash.

## Automated device checks

With the sample installed and the isolated host:

```sh
./gradlew :app:connectedFullDebugAndroidTest -PeditorRegressionBuild \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.abdurazaaqmohammed.plugins.ipc.PluginHooksInstrumentedTest
```

The automated plugin suites verified native discovery/consent, package-scoped
settings migration, primitive palette extras, live editor buffer/selection/undo
preservation, identical theme re-imports, and separate-process metadata event
delivery on an API 31 emulator. The manual per-surface and older-API checks above
are still a release checklist, not all automated or executed.

Theme parser/snapshot/update-selection tests run in `testFullDebugUnitTest`.
Additional live-editor instrumentation tests exercise content preservation without
relying on screenshot color averages or a process restart.
