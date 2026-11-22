# Third-Party Plugins for MP Manager

Plugins are **normally-installed Android apps** with their own UID, resources,
libraries, permissions, and process. MP Manager renders native menu entries and
opens your exported activity with an explicit intent after the user pins your
signing certificate. It never loads third-party dex, layouts, or resource tables
into its process. Theme providers publish validated appearance **data**, not code.

> Host-loaded `ToolPack` / `plugins.ext` packs remain first-party only. The
> `PackManager` same-signer gate is unchanged. A public action plugin uses the
> small `plugin-sdk`, not the first-party `sdk` or a host classloader.

## Quick start: build a plugin outside the host tree

The [standalone template](../samples/plugin-template/) is a complete Android
Gradle project. Copy that directory to any location, including its own repository.
It has its own wrapper, settings, manifest, activities, and event example. The host
checkout is needed only to publish the SDK locally during this development stage;
it is not needed by an artifact-based plugin build.

1. Install JDK 21, Android SDK platform 36, and the matching build tools. Configure
   `JAVA_HOME` and `ANDROID_SDK_ROOT`, or an untracked `local.properties`.
2. Publish the small SDK to **Maven Local** from the host checkout:

   ```sh
   ./gradlew -p plugin-sdk publishToMavenLocal
   ```

   This configures the SDK only, not MP Manager's app, packs, or native engines.
   The SDK coordinate is:

   ```groovy
   implementation 'io.github.abdurazaaqmohammed:mpmanager-plugin-sdk:1.0.0'
   ```

   This repository configures local publication. That coordinate is **not a
   promise of a published Maven Central/JitPack release**. Public publication is a
   separate maintainer release step.
3. From your copied template, run:

   ```sh
   ./gradlew assembleDebug
   ```

4. Change the template's application ID, Java package, labels, and stable plugin
   IDs. Keep only the integration areas you use. Install the resulting APK
   normally alongside MP Manager, then enable/trust it in **Tools → Plugins**.
5. Share the APK and your certificate SHA-256 fingerprint. MP Manager does not
   require a catalog entry or the host's signing key for external plugins.

For local SDK development without republishing an artifact:

```sh
./gradlew assembleDebug -PmpPluginSdk=/absolute/path/to/MP-Manager/plugin-sdk
```

The template substitutes the SDK coordinate using a lightweight composite build.
The plugin stays in its final location; it still ships as a separate APK.

### Bundle the public SDK

Use **`implementation`**, not `compileOnly`, for the public plugin SDK. Inlined
constants happened to work with the old sample's `compileOnly :sdk`, but runtime
helpers cannot resolve host classes in a different app process. The small SDK has
no Material, ZXing, ViewPager, or APK engine dependencies.

`compileOnly project(':sdk')` is still appropriate for first-party interfaces that
the host itself loads, not this external-plugin path. If you do not want any SDK
dependency, you can implement the documented intent/provider wire formats yourself.

## Project and folder conventions

| Location | Behavior |
| --- | --- |
| `plugins/<id>/` | Android modules auto-included as `:plugins:<id>` when a `build.gradle` or `build.gradle.kts` exists. |
| `plugin-<id>/` at the host root | Legacy-style modules auto-included as `:plugin-<id>`. |
| `plugin-sdk/` | Standalone, bundleable external-plugin contracts and helpers. |
| `sdk/` | First-party host-loaded interfaces; exposes the public SDK transitively. |
| `packs/pack-<id>/` | First-party packs, explicitly included and same-signer gated. |
| `samples/plugin-sample/`, `samples/theme-sample/` | In-tree working examples. |
| `samples/plugin-template/` | Independent example; not included in the host's module graph. |

Module discovery is deterministic and does not install or load plugin APKs. An
installed external plugin can be built from **any** directory; discovery at
runtime is based on Android manifests, not folder names.

Opt in to external standalone builds from the host:

```sh
./gradlew projects -PmpPluginBuilds=/absolute/path/plugin-one,/absolute/path/plugin-two
```

Each path must be a build directory with its own Gradle settings. Composite builds
expose their build tasks and substitutions only; they are not app dependencies,
installation, certificate trust, or permission grants. Only include build scripts
from a source you trust: Gradle configuration executes on your development machine.

## Action-plugin manifest

Expose one activity for each contribution, or share an activity across supported
areas. Each exported activity needs a `DEFAULT` category and a stable ID. A legacy
file action looks like this:

```xml
<activity android:name=".HashAction" android:exported="true"
          android:label="Hash file">
    <intent-filter>
        <action android:name="io.github.abdurazaaqmohammed.MPManager.action.FILE_MENU" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <meta-data android:name="io.github.abdurazaaqmohammed.MPManager.PLUGIN_ID"
               android:value="mypack.hash" />
    <meta-data android:name="io.github.abdurazaaqmohammed.MPManager.TITLE"
               android:value="Hash file" />
    <meta-data android:name="io.github.abdurazaaqmohammed.MPManager.FILE_MIME"
               android:value="*/*" />
</activity>
```

The [wire contracts](../plugin-sdk/src/main/java/io/github/abdurazaaqmohammed/plugins/ipc/PluginContracts.java)
retain all five original action strings and extras. The
[working sample](../samples/plugin-sample/) demonstrates them.

### The five compatible integration areas

| Action constant | Input | Result / behavior |
| --- | --- | --- |
| `ACTION_SIDEBAR_OPEN` | `EXTRA_PLUGIN_ID` and common host/theme extras | Open your screen. The row supports sidebar order/hiding. |
| `ACTION_SETTING_CONFIG` | `EXTRA_KEY`, `EXTRA_VALUE`, common extras | `RESULT_OK` + `EXTRA_VALUE` boolean/string; action-type settings open your screen without persisting a value. |
| `ACTION_FILE_MENU` | Read-only content URI(s), `ClipData`, `EXTRA_FILE_NAMES`, `EXTRA_MIME` | `EXTRA_MESSAGE` is displayed. Do not assume output URIs are automatically imported. |
| `ACTION_EDITOR` (`EDITOR_ACTION`) | Selection and bounded full document text | `EXTRA_REPLACE_SELECTION` / `EXTRA_SET_FULL_TEXT` are applied as an edit. No live editor handle crosses the process boundary. |
| `ACTION_APK` (`APK_ACTION`) | Read/write content URI to a **staged copy**, `EXTRA_APK_NAME` | Rewrite the staged URI and return `RESULT_OK`; the host imports the result using a safe suggested output name. |

Settings metadata: `SETTING_KEY` (defaults to plugin ID), `SETTING_TYPE` =
`boolean` (default) or `action`. The host stores external values under
`external_setting:<package>:<key>`, never under a raw host preference key. Unambiguous `plugin.*` legacy values from a currently trusted plugin are migrated
once; arbitrary host preference keys are never migrated. Menu IDs/order remain
compatible.

File metadata: `FILE_MIME` (comma list, `type/*` wildcards) and optional
`FILE_PATTERN` filename regex (maximum 256 characters). Matching uses linear-time
RE2 syntax: lookaround, backreferences, and other backtracking-only constructs are
unsupported and fail closed. File contributions must match every selected real
file. At most 64 file URIs/64 KiB of file metadata are shared per request.

APK choices use `APK_CHOICES` entries `Label|value`, and `APK_AUTO_SIGN` may opt into
the host's options UI. External APK plugins must bundle their own processing
libraries. They do not resolve `:apkkit` or arbitrary host classes through the
host's classloader.

## Generic native action hooks

A hook is a **host-defined native insertion point**, not arbitrary code injection.
The host discovers static manifest declarations without running your activity.
The same action covers supported current and future points:

```xml
<activity android:name=".HookActivity" android:exported="true"
          android:label="My plugin action">
    <intent-filter>
        <action android:name="io.github.abdurazaaqmohammed.MPManager.action.HOOK" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <meta-data android:name="io.github.abdurazaaqmohammed.MPManager.PLUGIN_ID"
               android:value="mypack.action" />
    <meta-data android:name="io.github.abdurazaaqmohammed.MPManager.CONTRACT_VERSION"
               android:value="1" />
    <meta-data android:name="io.github.abdurazaaqmohammed.MPManager.HOOKS"
               android:value="toolbar.actions,filemenu.extra,dialog.elf.extra" />
</activity>
```

Read `EXTRA_HOOK_ID` to distinguish where the action was invoked. Missing manifest
contract version means `1`; unsupported versions are ignored. Unknown hooks are
ignored. Each activity contributes one native action to each declared supported
slot; use separate activities for different labels/actions.

| Stable hook ID | Host UI | Context/result |
| --- | --- | --- |
| `sidebar.footer` | Sidebar action rows, initially appended | Same as sidebar; existing sidebar order/hiding. |
| `toolbar.actions` | Visible plugin-actions button in the main top bar | Native popup; no file access by default. |
| `filemenu.extra` | File context menu | Same matching/read-only URI and result contract as `FILE_MENU`. |
| `dialog.elf.extra` | ELF options dialog | Read-only URI to the selected file; native action row. |
| `dialog.apk.more` | APK More menu | Same staged-copy input/result as `APK_ACTION`. |
| `editor.toolbar` | Existing editor floating action menu | Same bounded selection/text and result contract as `EDITOR_ACTION`. |

New IDs are package-qualified:
`ext:<package>/<plugin-id-or-class>@<hook-id>`. Duplicate contributions collapse
predictably. Existing legacy IDs are unchanged. Use **Tools → Plugins → Plugin
actions** to hide hook contributions; toolbar/ELF order is managed there. The
sidebar/file/editor/APK surfaces retain their existing order customizers.

## Automatic events are separately opt-in

Trusting a clicked action does **not** enable automatic notifications. A receiver
may declare event interests, but the user must separately choose **Allow automatic
events** in Tools Hub. Consent is tied to the pinned certificate and is cleared
when that package is re-pinned with a different certificate. Only the event IDs
shown in the consent prompt are allowed; an update adding a new interest needs
fresh consent for it.

```xml
<receiver android:name=".PluginEventReceiver" android:exported="true">
    <intent-filter>
        <action android:name="io.github.abdurazaaqmohammed.MPManager.action.EVENT" />
    </intent-filter>
    <meta-data android:name="io.github.abdurazaaqmohammed.MPManager.CONTRACT_VERSION"
               android:value="1" />
    <meta-data android:name="io.github.abdurazaaqmohammed.MPManager.HOOKS"
               android:value="app.launch,file.opened,theme.changed" />
</receiver>
```

- `app.launch`: once per host process after the main UI is ready. Not replayed if
  consent is enabled later in that process.
- `file.opened`: best-effort notification after a successful host open/launch.
  `EXTRA_HOOK_PAYLOAD` is a small **Bundle** with `file_name` and `mime` strings.
- `theme.changed`: the active appearance changed; common theme extras describe it.

The host sends explicit-component, unordered broadcasts, not implicit system-wide
broadcasts or background activities. There is no result or synchronous timeout to
wait for. A bounded background queue drops excess events; delivery is not a durable
log. Receivers must return promptly or use their own appropriate background-work
API. Events never carry paths, file contents, editor text, or URI grants. Another
app could call your exported receiver, so treat all inputs as untrusted and never
perform privileged operations based only on `EXTRA_HOST_PACKAGE`.

## Common host capabilities and palette bridge

All context-aware action/hook/event requests include:

- `EXTRA_CONTRACT_VERSION` (`1`), `EXTRA_HOST_PACKAGE`, `EXTRA_HOST_HOOKS` (`String[]`).
- `EXTRA_THEME_ID`, `EXTRA_THEME_DARK`, optional `EXTRA_THEME_ACCENT`.
- `EXTRA_THEME_PALETTE`: a bounded Bundle of allowlisted token → primitive ARGB int.

Use the bundleable runtime helper in your own screen:

```java
PluginTheme.ThemeInfo theme = PluginTheme.from(getIntent());
int background = theme.color("colorSurface", theme.dark ? 0xff121212 : 0xfffafafa);
int foreground = theme.color("colorOnSurface", theme.dark ? 0xffeeeeee : 0xff202020);
root.setBackgroundColor(background);
title.setTextColor(foreground);
```

Missing tokens retain your own safe defaults. An installed pack's validated
palette is shared as data; built-in palette values come from host theme attributes.
This is cooperative styling, not permission to inflate host layouts or run code in
the host process.

A successful action result may request **its own package's** installed theme:

```java
Intent result = PluginRequests.resultIntent("Done");
PluginTheme.requestApplyTheme(result, "my-theme-id");
setResult(RESULT_OK, result);
finish();
```

MP Manager shows a confirmation, resolves the theme provider owned by that action
package, validates the requested row ID/signature/data, and imports it in the
background. A result cannot silently apply a theme, nominate another package, or
supply an arbitrary provider URI. A plain sidebar launch has no result callback;
use a result-capable action/hook to request a theme.

## Installed themes: v1 and v2

Install a theme APK normally, then go to **Settings → General → Theme → Installed
themes**. A successful selection takes effect without a process restart. Other
screens refresh on resume; editor screens restyle in place to preserve unsaved
buffers. To return to a built-in theme, choose Light, Dark, Black, or System.

The [theme sample](../samples/theme-sample/) demonstrates v2. V1 provider rows and
previously stored v1 snapshots are still accepted. No root, overlay permission,
host signing key, or shared process is needed.

### Manifest and fixed provider endpoints

The discovery activity is not a launcher and the host never starts it. Put the
provider authority metadata directly under `<application>`:

```xml
<activity android:name=".ThemeInfoActivity" android:exported="true"
          android:excludeFromRecents="true">
    <intent-filter>
        <action android:name="io.github.abdurazaaqmohammed.MPManager.action.THEME" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
<meta-data android:name="io.github.abdurazaaqmohammed.MPManager.THEME_PROVIDER"
           android:value="com.example.mytheme.provider" />
<provider android:name=".ThemeProvider" android:exported="true"
          android:authorities="com.example.mytheme.provider" />
```

Expose public theme data only; reject writes. Return exactly one row from
`content://<authority>/theme` (`vnd.android.cursor.item/theme`). Optional columns
may be absent or null.

| Column | Type | Meaning |
| --- | --- | --- |
| `contract` | INTEGER | Required: `1` or `2`; unknown versions rejected. |
| `id` | TEXT | Required stable ID within the package. |
| `name` | TEXT | Display name; falls back to app label/ID if absent. |
| `dark` | INTEGER | `1` dark base, `0` light base; absent follows last built-in choice. |
| `accent` | INTEGER | Signed 32-bit ARGB; fallback for `colorPrimary`. |
| `colors` | TEXT | JSON mapping accepted tokens to `#RRGGBB` / `#AARRGGBB`. |
| `strings` | TEXT | JSON mapping cosmetic label tokens to locale → plain text. |
| `wallpaper` | TEXT | Exact same-authority `/wallpaper` endpoint, PNG/JPEG only. |
| `scrim` | INTEGER | Optional ARGB tint over wallpaper. |
| `preview` | TEXT | Exact same-authority `/preview` endpoint, PNG/JPEG only. |
| `wallpaper_scope` | TEXT, v2 | `files` (default), `all`, or `none`. |
| `editor_syntax` | TEXT, v2 | JSON mapping accepted syntax categories/scopes to colors. |

Color and label JSON are each capped at 256 Ki UTF-16 code units; syntax JSON at
32 Ki. Wallpaper: 8 MiB, 8,192 pixels per side, 16,777,216 total pixels. Preview:
256 KiB, 2,048 pixels per side. IDs, names, and labels: 100 characters; at most
32 locale entries per label. Unknown tokens/columns are ignored; malformed
required data, unsupported scope, wrong authority, or oversized data rejects the
import atomically.

### Colors, editor syntax, and coverage

The canonical allowlist lives in
[`ThemePackContracts`](../plugin-sdk/src/main/java/io/github/abdurazaaqmohammed/plugins/themes/ThemePackContracts.java).
Names are case/separator-insensitive. Material tokens map to **host-owned** color
resources/styles. Android 11+ applies exact Material colors where the resource
loader is available. Older devices retain their base Material palette and use the
closest bundled accent. Direct app/editor tokens work independently of that API.

App tokens include file/folder icon tile backgrounds, active pane highlight,
wallpaper scrim, system bars, and editor colors:

```text
editor_background          editor_text
editor_gutter              editor_line_number
editor_line_number_current editor_selection
editor_current_line        editor_match_highlight
```

Syntax accepts `comment`, `keyword`, `string`, `number`, `type`, `function`,
`operator`, and `variable`, plus documented common TextMate scope aliases mapped
to those categories. TextMate languages use host-generated theme rules in an
isolated per-editor registry. Non-TextMate Java schemes share some color slots:
string/number share the literal slot; type also styles plain identifiers. These
are not arbitrary grammar or UI replacements.

```json
{"comment":"#6A9955","keyword":"#C586C0","entity.name.function":"#DCDCAA"}
```

`wallpaper_scope=all` applies to host-owned activity backgrounds, not OS permission
prompts, keyboards, notifications, or replacement layouts. Opaque host controls
retain their own surfaces. `none` disables wallpaper. Missing v2 fields preserve
v1 behavior (`files` scope, existing syntax defaults).

Allowed labels remain cosmetic: `tab.files`, `tab.root`, `tab.general`,
`tab.behavior`, `tab.other`, `settings.theme`. They cannot rename security prompts,
confirmations, commands, file formats, or protocol values. Locale lookup prefers
exact locale, language, English, then a safe fallback.

### Snapshot refresh and trust

The host imports a private, atomic snapshot; providers are not read on every
screen. The active provider is refreshed in background after its package update.
A malformed/unavailable update keeps the previous valid snapshot. A signature
mismatch/uninstall deactivates it. A refresh finishing after the user selects a
different theme cannot switch them back. Theme signer pins are separate from
action-plugin/event consent.

### Build the working samples

```sh
./gradlew :samples:plugin-sample:assembleDebug :samples:theme-sample:assembleDebug
```

The output APKs are under each module's `build/outputs/apk/debug/`. Install them
alongside MP Manager; they need not share its certificate.

## Security and authoring limits

- Keep metadata ≤2,048 characters, declared interests ≤32, IDs/labels ≤128 chars.
  Stream file bytes from content URIs rather than putting them in extras.
- All action launches are explicit and recheck the current pinned certificate.
  Disabled packages are not offered; Tools Hub still lists them for re-enabling.
- File/ELF input grants are read-only and activity-scoped. APK writes target a
  staged copy only. The plugin retains only the access Android grants it, not the
  host's root/Shizuku/all-files/network privileges.
- Editor full-text input is capped at 150 KiB. An oversized selection is not
  silently truncated into a misleading whole-selection transformation.
- Keep result messages short (host display cap 512 characters). Avoid custom
  Parcelables in wire Bundles. A hostile plugin is still untrusted data even after
  certificate pinning; pinning identifies an author, not safety.
- Automatic events are best effort and separately consented. No silent file access,
  host callbacks, plugin result execution, or synchronous plugin UI query exists.

### Deferred extension levels

This version intentionally does not support arbitrary external XML/RemoteViews,
per-file dynamic badge IPC, in-process third-party plugins, foreign APK resource
mapping, or font/icon/layout/motion asset endpoints. Those need separate bounded
contracts and actual host consumers. Do not advertise those manifest IDs/columns as
working features. The first-party signer gate is not loosened.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| SDK coordinate not found | Run standalone `publishToMavenLocal`, or point `mpPluginSdk` at the SDK build. No public artifact is claimed yet. |
| Plugin activity not discovered | Exported/enabled, `DEFAULT` category, exact action, correct activity metadata; reinstall after manifest changes. |
| Hook missing | Known hook ID, contract version 1, not disabled/hidden, matching file filters if applicable. |
| Events missing | Exported receiver, exact `EVENT` action, supported interests, action trust **and** separate event consent. |
| Theme rejected | One row, version 1/2, owned authority, exact endpoints, signer unchanged, valid JSON/image limits. |
| Some colors keep defaults | Missing tokens, older-API Material fallback, or opaque host control surfaces; not a generic resource overlay. |
| Activity helper class missing | Use `implementation` of `plugin-sdk`, not `compileOnly` of the host SDK. |

## Verification

```sh
./gradlew -p plugin-sdk test publishToMavenLocal
./gradlew -p samples/plugin-template assembleDebug
./gradlew -p samples/plugin-template assembleDebug -PmpPluginSdk=/absolute/path/plugin-sdk
./gradlew :app:testFullDebugUnitTest :samples:plugin-sample:assembleDebug :samples:theme-sample:assembleDebug
python -I tools/verify_plugin_settings.py .
```

Device regression tests use the isolated `-PeditorRegressionBuild` host. Install
the working sample before running `PluginHooksInstrumentedTest`. See
[plugin smoke tests](PLUGIN_SMOKE_TESTS.md) for manual surface/refresh checks.

## Maintainer release checklist: first-party packs

First-party packs still require their catalog checksums, catalog signature, and
host signer. Use `tools/SignCatalog.java` to hash/sign/verify the catalog, keep
private keys outside the repository, and only publish artifacts in a separately
authorized release. Neither building a public plugin nor publishing the SDK to
Maven Local changes this gate.
