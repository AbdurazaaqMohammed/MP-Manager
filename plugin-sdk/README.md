# MP-Manager plugin SDK (slim)

Standalone Android library with the wire contracts and runtime helpers for
out-of-process MP-Manager plugins. Zero third-party dependencies: no
Material, no ZXing, no ViewPager — plugins render their own framework UI.

- Coordinate: `io.github.abdurazaaqmohammed:mpmanager-plugin-sdk:1.0.0`
- Package: `io.github.abdurazaaqmohammed.plugins.ipc`
  (theme wire contract: `io.github.abdurazaaqmohammed.plugins.themes`)
- minSdk 19, compileSdk 36, Java 17, AGP 9.2.1, Gradle 9.4.1

## Modules

| Class | Purpose |
| --- | --- |
| `PluginContracts` | Exact strings: intent actions, manifest meta-data, extras, stable hook ids, stable event ids, payload keys, wire version. |
| `PluginHooks` | Pure helpers: allowlist checks, `META_HOOKS` parsing, stable entry ids, ordered merging. No Android classes. |
| `PluginTheme` | Read host theme extras (`from(Intent)`), allowlisted palette lookup (`color(name, fallback)`), palette bundle builder, plugin -> host "apply theme" request. |
| `PluginRequests` | Wire bounds (payload 16 KiB, meta 2048 chars, 32 declared hooks, ids/labels 128 chars) and result-intent helpers. |
| `ThemePackContracts` | Theme provider wire contract (moved from the app; owned by the theme workstream). |

## Build

Standalone (own `settings.gradle`, no app/native configuration):

```sh
./gradlew -p plugin-sdk test
./gradlew -p plugin-sdk assembleRelease
./gradlew -p plugin-sdk publishToMavenLocal   # release + sources jars
```

The daemon JVM criteria (`toolchainVersion=21`, same as the root build) live
in `gradle/gradle-daemon-jvm.properties`. Consumers that build the SDK from
sources via composite substitution only need a local Android SDK
(`local.properties` or `ANDROID_HOME`).

## Consuming

```groovy
dependencies {
    // Published artifact (bundles contracts + helpers into the plugin APK):
    implementation 'io.github.abdurazaaqmohammed:mpmanager-plugin-sdk:1.0.0'
    // ...or, inside the MP-Manager repo, the module directly:
    // implementation project(':plugin-sdk')
}
```

When resolving from `mavenLocal`, scope it to the SDK coordinate:

```groovy
repositories {
    google()
    mavenCentral()
    mavenLocal {
        content { includeGroup 'io.github.abdurazaaqmohammed' }
    }
}
```

See `samples/plugin-template` for a standalone plugin app that consumes the
artifact and supports `-PmpPluginSdk=/abs/path/plugin-sdk` composite
substitution.

## Hook/event protocol summary

- Declare served hooks in manifest meta-data
  `io.github.abdurazaaqmohammed.MPManager.HOOKS` (comma-separated stable ids,
  <= 32 entries / 2048 chars) and declare
  `io.github.abdurazaaqmohammed.MPManager.CONTRACT_VERSION` (`1`, absent = 1).
- Handle `io.github.abdurazaaqmohammed.MPManager.action.HOOK` in an exported
  activity; the host picks the hook with extra
  `...extra.HOOK_ID` and may attach `...extra.HOOK_PAYLOAD` (a Bundle of
  primitive values, <= 16 KiB). Return `RESULT_OK` with `...extra.MESSAGE`.
- Register an exported receiver for
  `io.github.abdurazaaqmohammed.MPManager.action.EVENT`; the event id rides
  in `...extra.HOOK_ID`. Events (`app.launch`, `file.opened`,
  `theme.changed`) carry a small primitive Bundle payload (`file_name`,
  `mime`) or, for `theme.changed`, only the common `...extra.THEME_*`
  extras; events never carry raw paths, file contents or URI grants.
- Stable hooks (host allowlist): `sidebar.footer`, `toolbar.actions`,
  `filemenu.extra`, `dialog.elf.extra`, `dialog.apk.more`, `editor.toolbar`.
- The host attaches its capabilities and appearance to every invocation:
  `...extra.CONTRACT_VERSION`, `...extra.HOST_PACKAGE`,
  `...extra.HOST_HOOKS`, plus `...extra.THEME_*`
  (`PluginTheme.from(intent)` reads them; palette is a Bundle of
  allowlisted name -> int colors).
- Plugins can request a theme switch by attaching
  `...extra.APPLY_THEME_ID` (`PluginTheme.requestApplyTheme`).

## Public API

```java
// PluginHooks (pure)
boolean isActionHook(String hookId)   // stable hook id?
boolean isEvent(String eventId)       // stable event id?
String[] actionHooks()                // defensive copy, declaration order
String[] eventHooks()                 // defensive copy, declaration order
List<String> knownHooks()             // unmodifiable allowlist
List<String> knownEvents()            // unmodifiable allowlist
List<String> parse(String declared)   // META_HOOKS value -> known ids, dedup, <= 32
String entryId(String pkg, String pluginId, String className, String hookId)
                                      // "ext:<pkg>/<pluginId-or-class>@<hookId>", null when invalid
List<String> mergeOrder(List<String> base, List<String> extra) // dedup + append, unmodifiable

// PluginTheme
PluginTheme.ThemeInfo from(Intent)    // also from(Bundle); never throws
ThemeInfo: String themeId, boolean dark, boolean hasAccent, int accent,
           int color(String name, int fallback), boolean has(String name),
           Map<String, Integer> palette()
int color(Bundle palette, String name, int fallback)
String canonicalKey(String raw)       // allowlist spelling or null
boolean isPaletteValid(Map<String, Integer>)
Bundle paletteBundle(Map<String, Integer>)
void putThemeExtras(Intent out, String themeId, boolean dark,
                    boolean hasAccent, int accent, Bundle palette)
void requestApplyTheme(Intent target, String themeId)

// PluginRequests
int MAX_HOOK_PAYLOAD_BYTES = 16384; int MAX_META_VALUE_CHARS = 2048;
int MAX_DECLARED_HOOKS = 32; int MAX_ID_CHARS = 128; int MAX_LABEL_CHARS = 128;
boolean isPayloadSizeOk(byte[]);
boolean isPrimitiveValueOk(Object);   // String/Number/Boolean payload entries
boolean isMetaValueOk(String); boolean isIdOk(String); boolean isLabelOk(String);
boolean isResultOk(int resultCode);
Intent resultIntent(String message);
```
