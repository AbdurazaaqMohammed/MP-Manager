# Plugin system implementation tracks

The current external-plugin authoring guide is
[THIRD_PARTY_PLUGINS.md](THIRD_PARTY_PLUGINS.md). This file records the implementation
scope of the 2026-11-22 proposal, not a request to start another implementation.

## A — Build and authoring workflow

- Small bundleable `plugin-sdk` with preserved wire package names, runtime helpers,
  a lightweight independent build, release/source artifact, and Maven Local
  publication. The first-party `sdk` exposes it transitively.
- Deterministic module discovery under `plugins/<id>/` and root `plugin-*` folders.
- Opt-in external composite builds (`mpPluginBuilds`) and SDK substitution
  (`mpPluginSdk`) without copying projects into the host.
- Independent `samples/plugin-template`, working action/theme samples, and
  documented artifact-vs-first-party dependency semantics.
- Public artifact/release upload is a separate maintainer action, not implied by
  local publication configuration.

## B — Declarative native hook core

- One versioned `ACTION_HOOK` declaration supports stable host-defined slots:
  sidebar footer, main toolbar actions, file menu, ELF options, APK More,
  editor floating actions.
- Manifest-only discovery; host-native rows; user-clicked, certificate-verified
  explicit activity launches. Existing result/content URI contracts are reused.
- Package-qualified IDs, deterministic deduplication, existing customizers, and
  shared visibility/order controls for new slots.
- Separate certificate-bound opt-in for metadata-only `app.launch`, `file.opened`,
  and `theme.changed` events. Explicit unordered broadcasts, bounded background
  queue, no URI grants, paths, contents, or inherited host permissions.
- Disable/uninstall invalidates every discovery cache; disabled packages stay in
  management UI without appearing on action surfaces.

## C — Functional data-only theme v2 core

- Version 1 and 2 rows and old snapshots remain readable. Optional v2 editor
  syntax/wallpaper scope data is bounded and validated.
- Host-owned palette resources/styles, actual editor token consumers, per-editor
  TextMate theme isolation, consistent system-bar fallback, and a full primitive
  palette bridge to cooperative plugin screens.
- Live appearance revision with foreground/resume refresh. Editor hosts refresh
  in place to preserve unsaved buffers/tabs instead of recreation/process kills.
- Atomic active-theme refresh after package replacement, signer recheck, retained
  valid snapshot on malformed update, stale-selection generation guard.
- Result-based request to apply the action package's own theme, with host
  confirmation and matching provider row-ID validation before selection.

## Boundaries retained / deferred

Same-signer `PackManager` code-loading gate stays. Third-party plugins never load
code, layouts, or arbitrary resource tables into the host. RemoteViews/XML
embedding, dynamic badge IPC, trusted-third-party in-process execution, foreign
APK overlays, and asset endpoints for fonts/icons/layouts/motion are **deferred**.
Parsing a field without a consumer is not a supported feature.

## Verification

See [PLUGIN_SMOKE_TESTS.md](PLUGIN_SMOKE_TESTS.md) and the authoring guide's test
commands. Use `-PeditorRegressionBuild` for device verification so test installs
never overwrite a normal host or its user data. Report actual test outcomes and
unexecuted device/API matrix items separately.
