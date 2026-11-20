# Privacy Sanitizer & Invasiveness Audit Engine: Staging Gameplan

## Overview & Architecture

The **Privacy Sanitizer & Invasiveness Audit Engine** in MP-Manager is a next-generation Android privacy hardening system. Unlike conventional patchers (such as Morphe/Proteus) that rely on static, greedy pattern matching, MP-Manager leverages its integrated reverse-engineering toolchain:

1. **Pre-Scan De-obfuscation:** Resolves encrypted string literals and reflection targets before scanning using [DexStringDecryptor.java](file:///home/deck/MP-Manager/app/src/main/java/io/github/abdurazaaqmohammed/feature/apk/DexStringDecryptor.java).
2. **Call-Graph & Taint Tracing:** Uses the "Trace to Method" engine in [SmaliSearchText.java](file:///home/deck/MP-Manager/app/src/main/java/modder/hub/dexeditor/utils/SmaliSearchText.java) to track callers and distinguish 1st-party code from 3rd-party tracking SDKs.
3. **Invasiveness Risk Scoring:** Dynamically computes a 0–100 privacy risk score across four threat vectors.
4. **Surgical Bytecode Mutation:** Mutates Dalvik bytecode in-memory via `dexlib2` without breaking register allocation or triggering ART `VerifyError`.
5. **Domain Sinkholing:** Redirects remote telemetry endpoints to local dead-ends directly in the DEX string pool.

---

## Root vs. Non-Root Classification

Every feature in the UI clearly indicates whether it requires device root:

* `[Non-Root]` **Static DEX Bytecode Patching:** Rewrites bytecode inside the APK, re-packs, and re-signs. The resulting APK runs on **any standard, non-rooted Android device**.
* `[Non-Root]` **Domain Sinkholing:** Neutralizes tracking endpoints directly in the string pool. Runs on **any device**.
* `[Non-Root]` **Frida Gadget In-App Hooks:** Injects `libfrida-gadget.so` into the APK package to hook in-process calls without root.
* `[Requires Root]` **Native (`.so`) Root Check Bypasses:** Circumventing low-level Linux syscalls (`/proc/mounts`, direct `su` probes) requires root-level Zygisk/LSPosed or system-wide hooks.
* `[Requires Root]` **Play Integrity / SafetyNet Attestation:** Remote hardware-backed attestation requires root-level fingerprint spoofing (e.g. PlayIntegrityFix).

---

## Staged Implementation & APK Release Plan

Each stage produces a verified, downloadable APK release so functionality can be tested step-by-step on physical hardware (e.g., Pixel 10 Pro XL) to isolate regressions.

```
┌────────────────────────────────────────────────────────────────────────┐
│ STAGE 1: Core Bytecode Mutation Engine                                 │
│  - LogStripper (android.util.Log.* -> NOP + return 0)                  │
│  - TelephonyBlanker (getDeviceId/IMEI/SIM/Phone -> "")                 │
│  - AdvertisingIdNeutralizer (getId -> "", isLimitAdTracking -> true)   │
│  - DeveloperModeMasker (adb_enabled -> 0, ro.debuggable -> "0")        │
│  - Register-width awareness (const/4, const/16, const)                 │
│  -> Deliverable: Stage 1 APK (stage-1-release.apk)                     │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│ STAGE 2: Semantic Privacy Scanner & Invasiveness Scoring               │
│  - Hardware Fingerprinting Detector                                    │
│  - Environment & Surveillance Detector (pm list packages, su, getprop) │
│  - 3rd-Party Tracking SDK Detector (AppsFlyer, Adjust, Segment, etc.)  │
│  - Invasiveness Risk Score Engine (0 to 100)                           │
│  -> Deliverable: Stage 2 APK (stage-2-release.apk)                     │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│ STAGE 3: Interactive Privacy Audit Dialog & Excision Pipeline          │
│  - UI Dialog showing Invasiveness Score & Category Breakdown           │
│  - Clear [Non-Root] and [Requires Root] badges                         │
│  - Hierarchical Tree View of detected threats with selective toggles   │
│  - "Auto-Sanitize" and "Surgical Excision" actions wired to APK writer │
│  -> Deliverable: Stage 3 APK (stage-3-release.apk)                     │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│ STAGE 4: Telemetry Domain Sinkholing & Native Library Auditing         │
│  - String pool scanner against known telemetry blocklists              │
│  - In-place domain sinkholing (https://tracker.com -> 127.0.0.1:0)     │
│  - Native ELF (.so) strings/symbol auditor with risk warnings          │
│  -> Deliverable: Stage 4 APK (stage-4-release.apk)                     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Stage 1 Technical Specifications

### 1. `PrivacyBytecodeEngine`
Located in `io.github.abdurazaaqmohammed.feature.apk.privacy`:
* **Log Stripping:**
  - Opcode target: `INVOKE_STATIC` / `INVOKE_STATIC_RANGE` to `Landroid/util/Log;->[vidwe|wtf|println](...)I`.
  - Action: Replace invoke with `NOP`. If followed by `MOVE_RESULT`, replace `move-result` with `const 0` using appropriate register width.
* **Telephony Blanking:**
  - Target: `Landroid/telephony/TelephonyManager;` methods (`getDeviceId`, `getImei`, `getMeid`, `getSubscriberId`, `getLine1Number`, `getSimSerialNumber`, `getDeviceSoftwareVersion`).
  - Action: Replace following `MOVE_RESULT_OBJECT` with `const-string ""` on the target register.
* **Advertising & Device ID Neutralization:**
  - Target: `Lcom/google/android/gms/ads/identifier/AdvertisingIdClient$Info;->getId()Ljava/lang/String;` $\to$ rewrite `MOVE_RESULT_OBJECT` to `""`.
  - Target: `isLimitAdTrackingEnabled()Z` $\to$ rewrite `MOVE_RESULT` to `1` (`true`).
  - Target: `Settings.Secure.getString(..., "android_id")` $\to$ lookback key match, rewrite `MOVE_RESULT_OBJECT` to `""`.
* **Developer Options / ADB Masking:**
  - Target: `Settings.Global/Secure.getInt(..., "adb_enabled" | "development_settings_enabled")` $\to$ rewrite `MOVE_RESULT` to `0`.
  - Target: `SystemProperties.get("ro.debuggable" | "service.adb.root")` $\to$ rewrite `MOVE_RESULT_OBJECT` to `"0"`.

### 2. Register-Width Safety
Dalvik opcode constraints:
* `const/4 vX, 0`: `vX` in `0..15`.
* `const/16 vX, 0`: `vX` in `0..255`.
* `const vX, 0`: `vX` in `0..65535`.
The engine selects the minimal valid instruction format matching the register index to prevent smali/dex assembler rejection on large methods (>16 registers).

---

## Verification & Staging Matrix

| Stage | Unit Tests | Build Command | Output Release Artifact | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Stage 1** | `PrivacyBytecodeEngineTest` | `./gradlew :app:testFullDebugUnitTest` | [`stage-1` Release](https://github.com/1456319/MP-Manager/releases/tag/stage-1) | **Released** |
| **Stage 2** | `PrivacyScannerEngineTest` | `./gradlew :app:testFullDebugUnitTest` | [`stage-2` Release](https://github.com/1456319/MP-Manager/releases/tag/stage-2) | **Released** |
| **Stage 3** | `PrivacyBytecodeEngineTest` | `./gradlew :app:testFullDebugUnitTest` | [`stage-3` Release](https://github.com/1456319/MP-Manager/releases/tag/stage-3) | **Released** |
| **Stage 4** | `DomainSinkholeTest` | `./gradlew :app:testFullDebugUnitTest` | [`stage-4` Release](https://github.com/1456319/MP-Manager/releases/tag/stage-4) | **Building in CI** |
