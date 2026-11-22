# MP-Manager plugin template

A standalone, copy-and-go Android app that registers as an MP-Manager
external plugin. It demonstrates every action area plus the hook and event
protocol using only framework UI (no Material, no ZXing) in the plugin's own
process.

Areas demonstrated:

| Area | Manifest action | Class |
| --- | --- | --- |
| Sidebar | `...action.SIDEBAR_OPEN` | `TemplateSidebarActivity` |
| Setting | `...action.SETTING_CONFIG` | `TemplateSettingActivity` |
| File menu | `...action.FILE_MENU` | `TemplateFileActivity` (names/mime only, never reads content) |
| Editor | `...action.EDITOR_ACTION` | `TemplateEditorActivity` |
| APK dialog | `...action.APK_ACTION` | `TemplateApkActivity` (display-only, no transforms) |
| Hooks | `...action.HOOK` | `HookActivity` — all six stable hooks via `META_HOOKS` |
| Events | `...action.EVENT` | `EventReceiver` — `app.launch`, `file.opened`, `theme.changed` |

## Getting started

1. Copy this whole directory anywhere (inside or outside the MP-Manager
   tree). It carries its own Gradle wrapper and settings and is never
   auto-included by the MP-Manager root build.
2. Rename `namespace` / `applicationId` in `build.gradle` and the
   `package` of the Java sources (Android Studio refactor works fine).
3. Point the SDK dependency at the slim plugin SDK
   (`io.github.abdurazaaqmohammed:mpmanager-plugin-sdk:1.0.0`):

   Option A — published artifact:

   ```sh
   # From the MP-Manager repo root (publishes release + sources jars):
   ./gradlew -p plugin-sdk publishToMavenLocal
   # In the template:
   ./gradlew assembleDebug
   ```

   `settings.gradle` scopes `mavenLocal` to the
   `io.github.abdurazaaqmohammed` group only, so no other stale local
   artifacts can leak in.

   Option B — local SDK sources via composite substitution:

   ```sh
   ./gradlew assembleDebug -PmpPluginSdk=/abs/path/to/MP-Manager/plugin-sdk
   ```

4. Install the APK on the device, open MP-Manager, and pin the plugin's
   signing certificate when the host asks. The host discovers the activities
   by their intent actions and invokes them with explicit intents.

## Build requirements

- JDK 21 (the bundled `gradle/gradle-daemon-jvm.properties` selects it
  automatically; install one or run with `JAVA_HOME` pointed at it)
- Android SDK (platform 36); set `ANDROID_HOME` or a `local.properties`
  with `sdk.dir` (never commit that file)

## Manifest contract notes

- `META_PLUGIN_ID` per entry, `META_TITLE` optional display title.
- `META_HOOKS` on `HookActivity` lists the stable hook ids served
  (`sidebar.footer`, `toolbar.actions`, `filemenu.extra`,
  `dialog.elf.extra`, `dialog.apk.more`, `editor.toolbar`), max 32 entries /
  2048 chars. `META_CONTRACT_VERSION` is `1`; the host accepts 1 (absent
  = 1).
- The host attaches `EXTRA_CONTRACT_VERSION`, `EXTRA_HOST_PACKAGE`,
  `EXTRA_HOST_HOOKS` and the theme extras (`EXTRA_THEME_*`) to every
  invocation; read them with `PluginTheme.from(intent)`.
- Hook results: return `RESULT_OK` with `EXTRA_MESSAGE` (and hook-specific
  extras, e.g. `EXTRA_REPLACE_SELECTION` for `editor.toolbar`).
- `EXTRA_HOOK_PAYLOAD` is a Bundle of primitive values (`file_name`,
  `mime`, ...), at most 16 KiB, and never contains raw paths, file contents
  or URI grants; `theme.changed` carries no payload (theme info rides in
  the `EXTRA_THEME_*` extras).
- Plugins can request a theme switch by returning an intent carrying
  `EXTRA_APPLY_THEME_ID` (`PluginTheme.requestApplyTheme`).
