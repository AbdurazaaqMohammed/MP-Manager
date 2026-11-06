# Plan: Hybrid plugin isolation (all five areas)

Locked decisions: first-party packs stay in-process; third-party goes out-of-process with signature pinning; all five host areas get intent contracts.

## Phase 0 — Same-signer gate for in-process loading (1–2 days)
Goal: in-process path only ever loads code signed by you; everything else is refused and directed to the external-plugin flow.
- `PackManager.loadPack`: after `DexClassLoader`, before `newInstance()`, verify the APK's signing cert digest against a pinned first-party digest (build-time constant + `GET_SIGNING_CERTIFICATES`, API 19 fallback `GET_SIGNATURES`). Mismatch → refuse load, return error naming the external-plugin route.
- `installDownloadedPack`/`installFromFile`: enforce `hasChecksum()` (no more checksum-less installs); sign `packs.json` (Ed25519/RSA detached signature or digest-in-app) and verify in `PackCatalog.refreshAsync` before caching.
- Narrow `FileProvider` `paths.xml` (`root-path "."` → scoped dirs) since URI grants will carry file access going forward.
- Acceptance: repackaged/foreign-signed APK fails to load with a clear message; tampered catalog rejected.

## Phase 1 — External plugin trust core (3–5 days)
New `plugins.ipc.PluginHost` (app side):
- Discovery per area via `PackageManager.queryIntentActivities()` with five actions (`…action.SIDEBAR_OPEN/SETTING_CONFIG/FILE_MENU/EDITOR_ACTION/APK_ACTION`) + `<queries>` manifest entries (replaces broad reliance on `QUERY_ALL_PACKAGES` for this).
- Trust store `SharedPreferences("plugin_trust")`: `{package → sha256 cert digest, label, enabled}`. First-seen prompt shows label/icon/package/fingerprint; pin on allow; re-prompt when digest changes (accept silently only if old digest ∈ `signingCertificateHistory`); evict on `PACKAGE_REMOVED/REPLACED` receiver; re-verify digest on every invoke; always explicit `setClassName` intents.
- Enable/disable UI in ToolsHub (external section).
- Acceptance: unknown plugin blocked until user pins fingerprint; cert change re-prompts; uninstall cleans state.

## Phase 2 — Five intent contracts (2–3 weeks)
One contract doc + host wrappers with `ActivityResultLauncher`s; plugin declares capabilities in manifest `meta-data` (mime/regex/size filters replace `visibleFor()`; titles replace labels):
1. **Sidebar**: host row → `startActivity(explicit + extras)`. ✅ trivial.
2. **Settings**: `SETTING_CONFIG` activity returns `EXTRA_VALUE` (toggle) or performs its own screen (action); host persists to prefs. Static rows, no live plugin code in dialog.
3. **File menu**: host stages `FileProvider` content URIs + `FLAG_GRANT_*` (+ `grantUriPermission` loop, `ClipData` for multi) → plugin `setResult(EXTRA_OUTPUT_URI/EXTRA_MESSAGE)`.
4. **Editor**: host sends `EXTRA_SELECTED_TEXT` (+ truncated full text ≤ ~150KB, offsets) → plugin returns `EXTRA_REPLACE_SELECTION` / `EXTRA_SET_FULL_TEXT`; host applies via existing batch edit. Large docs fall back to "open full file via URI" variant.
5. **APK dialog**: fire-and-forget intent with content URI + `EXTRA_FILE_NAME`; never raw `/data/…` paths.
- Keep `AppExtension` ids stable so `FileMenuOrder`, sidebar organize/hide, and `menu_order` keep working across the migration (external entries reuse the same id namespace, e.g. `ext.<package>.<action>`).
- Acceptance per area: end-to-end with a sample external plugin (Phase 3).

## Phase 3 — Sample external plugin + docs (1 week)
- `samples/sample-external-plugin/`: minimal separate app module implementing all five contracts (its own manifest permissions, Kotlin allowed, XML layouts allowed — demonstrating the lifted constraints).
- Rewrite `docs/THIRD_PARTY_PLUGINS.md`: external-first; in-process `ToolPack.extensions()` documented as first-party-only; manifest templates, intent/bundle schemas, Binder limits, trust/rotation UX, Play `<queries>` justification.
- Acceptance: sample installs from APK, passes first-seen prompt, works in all five areas, survives rotation/update prompts.

## Phase 4 — Cutover + hardening (3–5 days)
- ToolsHub: external plugins section (trust state, disable, remove); in-process sideload restricted to pinned signer.
- `PackPrompts`/install UX: fingerprint display + permission/risk disclosure for external; keep checksum flow for first-party.
- Full `:app:assembleDebug` + all packs + sample; regression pass on file sharing grants and menu ordering.
- Acceptance: no unsigned code path reaches `DexClassLoader`; all five areas verified on-device.

## Key risks / open points
- `ToolPlugin.createView` embedded views stay first-party-only; third-party tools become full Activities (documented limitation).
- Editor large-file path needs the URI variant, not just extras.
- Target SDK 37: audit all new exported components (`android:exported="true"` + explicit-intent-only invocation).

Say the word (outside plan mode) and I'll start at Phase 0.