# Third-Party Plugins for MP-Manager

Third-party plugins are **normally-installed apps** with their own UID and
their own permissions. They integrate with MP-Manager through explicit
intents defined in `sdk/.../plugins/ipc/PluginContracts.java` (shared
verbatim by host and plugins). There is deliberately **no code loading**:
a plugin can never inherit the host's root, Shizuku, all-files, or network
access, and a crashing plugin cannot take the host down.

> In-process packs (`ToolPack` + `plugins.ext` extensions loaded with
> `DexClassLoader`) are **first-party only**: `PackManager` refuses to load
> any APK not signed with the host app's own certificate. If you are not the
> app maintainer, build an external plugin as described here.

Theme APKs use a separate, data-only provider contract described in [Installed themes](#installed-themes-v1). The activity-based contract below is for action plugins.

## 1. Concepts

- You ship an APK (any language, libraries, resources, and layouts allowed)
  with one exported `Activity` per integration area.
- Each activity declares an intent-filter action from `PluginContracts` plus
  `meta-data` describing itself (id, title, file filters, setting key).
- The host discovers plugins with `PackageManager.queryIntentActivities()`,
  shows a **first-seen prompt** (label, package, certificate SHA-256), and
  pins the certificate. Updates with a rotated certificate keep working when
  the old digest is in the cert history; anything else re-prompts.
- All calls use **explicit intents** (`setClassName`) to pinned packages.
- Files cross the boundary as `content://` URIs with one-shot grants — never
  raw paths (you cannot see `/data/data`, root paths, or anything outside
  the grant).

## 2. Project setup

- `minSdk` 19+ recommended; `targetSdk` per Play policy.
- Reference the contracts: copy
  `sdk/src/main/java/io/github/abdurazaaqmohammed/plugins/ipc/PluginContracts.java`
  into your project (or `compileOnly` the host `:sdk` module while
  developing — never bundle it).
- Declare your own `<uses-permission>` entries (camera, location, network…)
  and request runtime permissions yourself; the host grants you nothing.

## 3. Manifest template (one activity per area you use)

```xml
<activity android:name=".HashAction" android:exported="true"
    android:label="Hash File">
  <intent-filter>
    <action android:name="io.github.abdurazaaqmohammed.MPManager.action.FILE_MENU" />
    <category android:name="android.intent.category.DEFAULT" />
  </intent-filter>
  <meta-data android:name="io.github.abdurazaaqmohammed.MPManager.PLUGIN_ID"
      android:value="mypack.hash" />
  <meta-data android:name="io.github.abdurazaaqmohammed.MPManager.TITLE"
      android:value="Hash File" />
  <meta-data android:name="io.github.abdurazaaqmohammed.MPManager.FILE_MIME"
      android:value="*/*" />
  <meta-data android:name="io.github.abdurazaaqmohammed.MPManager.FILE_PATTERN"
      android:value="" />
</activity>
```

Actions: `...action.SIDEBAR_OPEN`, `...action.SETTING_CONFIG`,
`...action.FILE_MENU`, `...action.EDITOR_ACTION`, `...action.APK_ACTION`.
A full working example with all five lives in `samples/plugin-sample/`.

## 4. The five areas

### Sidebar (`ACTION_SIDEBAR_OPEN`)

- In: `EXTRA_PLUGIN_ID`. Just show your screen.
- Your activity label (or `TITLE` meta) becomes the sidebar row; users can
  reorder/hide it like built-ins.

### Settings (`ACTION_SETTING_CONFIG`)

- Meta: `SETTING_KEY` (pref key, default = plugin id),
  `SETTING_TYPE` = `boolean` (default) or `action`.
- In: `EXTRA_PLUGIN_ID`, `EXTRA_KEY`, `EXTRA_VALUE` (current, booleans only).
- Out (`setResult(RESULT_OK, …)`): `EXTRA_VALUE` with the new boolean/String.
  The host persists it under the key; read it back with the same key.
- `action` type: tap opens your screen, nothing is persisted.

### File menu (`ACTION_FILE_MENU`)

- Listed only when your `FILE_MIME` (comma list, `type/*` wildcards) and
  `FILE_PATTERN` (filename regex) match **every** selected file, and only
  for real files (never inside archives).
- In: data URI + `ClipData` (multi-select), `EXTRA_FILE_NAMES`,
  `EXTRA_MIME`, read grant (write grant is never added).
- Out: `EXTRA_MESSAGE` (shown as a toast) and optionally
  `EXTRA_OUTPUT_URI`.
- Your id (`ext:<plugin-id>`) joins the persisted menu order automatically,
  so users rearrange it in **File menu order** like built-ins.

### Editor floating menu (`ACTION_EDITOR`)

- Your id is auto-appended to the stored menu order: users reorder, disable,
  or hide your button in the floating-menu editor like built-ins.
- In: `EXTRA_SELECTED_TEXT` ("" when empty), `EXTRA_FULL_TEXT` (truncated
  past 150 KB — open the file yourself for huge docs).
- Out: `EXTRA_REPLACE_SELECTION` and/or `EXTRA_SET_FULL_TEXT`. The host
  applies them as one batch edit. There is no live editor handle across the
  process boundary by design.

### APK dialog (`ACTION_APK`)

- Host starts your activity for result. In: data URI (a staged copy of the
  APK in the host cache, read+write grant) + `EXTRA_APK_NAME`.
- Heavy lifting belongs to shared code that lives **once**, in the host:
  `com.reandroid`, smali, apksig, etc. live in the `:apkkit` module
  (implementation for the host, compileOnly for plugins). At runtime a
  plugin DexClassLoads its kill/transform classes with the **host APK's
  dexloader as parent**, so references to the universal classes resolve
  from the host while plugin-only classes (your kill-sig helpers,
  `com.antik`, staged assets) resolve from the plugin's own dex/assets.
  See `samples/plugin-kill-sig` for a full example.
- Your entry is appended after the built-ins in the More list.

## 5. Conventions (required)

- Namespace every id: `<pack>.<name>`. Collisions resolve to one entry.
- Keep titles ≤ ~42 chars, no trailing period; subtitles/labels read best
  as short sentences or comma lists (`"Split bills and tip"`).
- Never block on the host side: every host call is guarded, but prefer fast
  results and progress UI of your own for long work.
- Binder limit (~1 MB total per transaction): keep extras small; stream file
  bytes from the content URI instead of stuffing them into extras.

## 6. Distribution

Users install your APK normally (Play, F-Droid, sideload). No catalog entry
is needed: the host discovers you on launch and on package changes, then
asks the user to pin your certificate on first use. Publish your signing
fingerprint alongside the download so users can compare it in the prompt.

## 7. Maintainer release checklist (first-party packs)

1. Build each pack APK; record its SHA-256 in `app/src/main/assets/packs.json`
   (`sha256` must be non-empty — installs without one are refused). Use
   `--checksums`: after uploading the APKs it hashes each `apkUrl` and rewrites
   the fields, or hash the local builds directly with `id=path` pairs:
   `java tools/SignCatalog.java --checksums packs.json media=packs/pack-media/release/pack-media-release.apk ...`
   Run it again once the APKs are uploaded if you hashed local files, so the
   recorded values match the served bytes.
2. Sign the catalog (private key in `%USERPROFILE%\.config\mp-manager\`,
   never in the repo):
   `java tools/SignCatalog.java --sign <keystore> <alias> <password> packs.json`
   then verify with `--verify`. Upload `packs.json` + `packs.json.sig` +
   the APKs to the release. Unsigned catalog refreshes are ignored by the app.
   `--fill` does steps 1 and 2 in one command (add the same `id=path` pairs).
3. Same-signer rule is automatic: release packs must be signed with the same
   key as the app or `PackManager` refuses to load them (debuggable builds
   skip the check for local development).


## Installed themes (v1)

Install a theme APK normally, then go to **Settings → General → Theme → Installed themes**.
Select it and restart MP Manager when prompted. To return to a built-in theme,
choose Light, Dark, Black, or System in the same card. No root, overlay permission,
shared signer, network permission, or MP Manager SDK dependency is needed.

### Manifest and endpoints

The discovery activity is not a launcher activity and MP Manager never starts it.
Use a unique authority owned by your APK. Put the metadata directly under `<application>`:

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
<provider android:name=".ThemeProvider"
          android:authorities="com.example.mytheme.provider" android:exported="true" />
```

Expose only public theme data, reject writes, and return one cursor row from
`content://<authority>/theme` (`vnd.android.cursor.item/theme`). Use these
column names and types; optional columns can be absent or `NULL`:

| Column | Type | Meaning |
| --- | --- | --- |
| `contract` | INTEGER | Required; `1`. Future incompatible versions will not be imported. |
| `id` | TEXT | Required; stable ID within your package (e.g. `midnight-violet`). |
| `name` | TEXT | Display name. The chooser uses your app icon and label before import. |
| `dark` | INTEGER | `1` uses the dark base, `0` the light base; unset uses the last built-in choice. |
| `accent` | INTEGER | Signed 32-bit ARGB; fallback for `colorPrimary`. |
| `colors` | TEXT | JSON object mapping color tokens to `#AARRGGBB` strings. |
| `strings` | TEXT | JSON object mapping text tokens to objects of locale tag → plain text. |
| `wallpaper` | TEXT | Exact `content://<authority>/wallpaper` URI for a PNG or JPEG. |
| `scrim` | INTEGER | Optional signed ARGB tint drawn over the wallpaper. |
| `preview` | TEXT | Optional `content://<authority>/preview` PNG or JPEG (up to 256 KiB); the current chooser uses app icons instead. |

MP Manager ignores unknown tokens. Each JSON column is capped at 256 Ki UTF-16
code units. Wallpaper is limited to 8 MiB, 8,192 pixels per side and
16,777,216 total pixels. A preview is limited to 2,048 pixels per side.
The ID, name, and each label are limited to 100 characters; at most 32 locale
entries per label are read.

MP Manager imports a private snapshot, rather than
reading the provider on every screen. A package update does not automatically
apply new theme data: select the theme again to refresh it. The host pins the
signer on import and drops the active snapshot on uninstall or signer mismatch.
Pins are separate from action-plugin trust. Theme APK code runs only under its
own app identity; MP Manager never loads it into its process or grants access to files.

### Colors and labels

The following Material attributes honor exact colors on Android 11 and newer
when the resource loader is available. Missing attributes come from the base
style. On older OS versions, `colorPrimary` (or `accent`) selects the closest
bundled accent, and other Material attributes retain their base colors.

```text
colorPrimary                  colorOnPrimary
colorPrimaryContainer         colorOnPrimaryContainer
colorSecondary                colorOnSecondary
colorSecondaryContainer       colorOnSecondaryContainer
colorTertiary                 colorOnTertiary
colorSurface                  colorOnSurface
colorSurfaceVariant           colorOnSurfaceVariant
colorSurfaceContainerLowest   colorSurfaceContainerLow
colorSurfaceContainer         colorSurfaceContainerHigh
colorSurfaceContainerHighest  colorOutline
colorOutlineVariant           colorError
colorOnError
```

App-specific colors: `folder_icon_bg` and `file_icon_bg` tint the folder and
generic file tiles, `list_highlight` tints the active pane indicator,
`wallpaper_scrim` controls the file-pane wallpaper tint, `status_bar` and
`nav_bar` tint the main screen's bars where Android allows it, and
`editor_background` and `editor_text` tint the unified editor's background and
plain text. TextMate grammar syntax colors are not overridden. Wallpaper is
shown behind the main file panes, not behind dialogs or permission screens.

Allowed label keys: `tab.files`, `tab.root`, `tab.general`, `tab.behavior`,
`tab.other`, and `settings.theme`. For example:

```json
{"tab.files":{"en":"Files","es":"Archivos","zh-CN":"文件"}}
```

MP Manager tries the current locale, its language, English, then the built-in
label. These are cosmetic labels only: commands, confirmations, file formats,
and protocol values cannot be renamed.

### Build the sample

`./gradlew :samples:theme-sample:assembleDebug` builds the permission-free
[theme sample](../samples/theme-sample/) with its icon and wallpaper. Install the
APK from `samples/theme-sample/build/outputs/apk/debug/` alongside your MP Manager
build. The sample does not need to be signed with MP Manager's certificate.
