# 05 - Amazon Shopping Sterilization: Critical Lessons Learned & Debugging Knowledge Base

**Date**: November 19, 2026  
**Target Analyzed**: Amazon Shopping Android (`com.amazon.mShop.android.shopping`, v32.19.0.100, 183 MB, 14 DEX files)  
**Host & Device**: Steam Deck Linux (x86_64) -> Pixel 10 Pro XL (`mustang`, Android 16, targetSdk 37, Rooted)

---

## 1. Executive Summary of Findings

During the end-to-end sterilization and physical deployment of the 183MB multi-DEX Amazon Shopping app, we discovered critical Dalvik runtime invariants and edge cases that distinguish toy smali modifications from enterprise-grade Android APK sanitization. 

These findings directly dictate the architecture of MP-Manager's autonomous "Antivirus / Privacy Suite" and explain why existing tools (Morphe, basic Revanced patches) either fail to launch or report false-positive placebo protections.

---

## 2. Critical Runtime Invariants & Root Causes

### Lesson 1: The Dalvik Class Descriptor Array Trap (`dex2oat` Verifier Failure)
- **Symptom**: During on-device compilation (`cmd package compile -m verify -f com.amazon.mShop.android.shopping`), Android's ART `dex2oat64` aborted with:
  ```text
  E dex2oat64: Failed to verify .../base.apk!9: Failure to verify dex file: Invalid descriptor for class_idx: '[Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;'
  ```
  This caused `dex2oat` to return exit code 1, which subsequent app launches failed with `ClassNotFoundException: Didn't find class "com.amazon.mShop.MShopApplication"`.
- **Root Cause**:
  In DEX specifications, `class_def_item.class_idx` and `field_id_item.class_idx` must strictly refer to a non-array class descriptor (`Lcom/foo/Bar;`). Array descriptors (`[L...`) are only valid in `type_ids` and method signatures, never as defining classes of static fields.
  Because Java enums declare a synthetic method `public static MyEnum[] values()`, its return type is `[Lcom/.../MyEnum;`. A naive return-type check matching `contains("SavXEligibilityState")` treated `values()` as an eligibility method and generated an `sget-object` referencing `[L...;->NOT_ELIGIBLE`.
- **Generalization Invariant**:
  1. **Enum Protection**: Never mutate enum synthetic or lifecycle methods (`values()`, `valueOf()`, `$values()`).
  2. **Descriptor Normalization**: Any synthesized `FieldReference` or `MethodReference` must strictly ensure the defining class starts with `L`, ends with `;`, and contains no leading array dimension `[`.

### Lesson 2: OpenTelemetry & Reflection Class Name vs. URL Collision
- **Symptom**: The app crashed immediately at startup (`MShopApplication.onCreate`) with:
  ```text
  Caused by: com.amazon.platform.extension.ConfigurationException: Failed to load http://127.0.0.1:0/
  at com.amazon.shopkit2.ServiceRegistryInitializer.runAppStartListener(...)
  ```
- **Root Cause**:
  In `ServiceRegistryInitializer`, Amazon dynamically instantiates essential app start listeners via reflection:
  ```java
  this.mAppStartListeners.add("com.amazon.mShop.opentelemetry.providers.ApplicationInstanceProvider$ApplicationInjectionHandler");
  Class.forName(str).newInstance();
  ```
  The domain sinkholer had a pattern matching `telemetry.`. Because `"com.amazon.mShop.opentelemetry..."` contained `telemetry.`, it replaced the Java class name string in bytecode with `"http://127.0.0.1:0/"`. When `Class.forName("http://127.0.0.1:0/")` was invoked, the startup orchestrator aborted.
- **Generalization Invariant**:
  1. **Strict Scheme or Host Boundary**: Sinkholing to `http://127.0.0.1:0/` must only apply to strings with valid URI schemes (`http://`, `https://`, `ws://`, `wss://`).
  2. **Class Path Exclusion**: Any string matching Java class naming conventions (containing `$`, `;`, `(`, or starting with standard namespaces `com.`, `org.`, `android.`, `androidx.`, `io.`, `java.`, `javax.`, `kotlin.`, `dalvik.`) must be explicitly excluded from domain sinkholing.
  3. **Bare Domain Replacement**: Bare host strings (e.g. `telemetry.amazon.com`) must be replaced with `127.0.0.1`, not `http://127.0.0.1:0/`, to prevent malformed nested URLs if concatenated into `https://` + host.

### Lesson 3: Android ART AOT / OAT Cache Desynchronization
- **Symptom**: After bind-mounting the sanitized APK over the stock APK, ART crashed with `ClassNotFoundException` even when the classes existed in the DEX.
- **Root Cause**:
  Android 14/15/16 uses Ahead-of-Time (AOT) compilation storing artifacts in `/data/app/.../oat/arm64/base.odex` and `base.vdex`. These files contain SHA-1/CRC checksums of the original DEX files. When an APK is patched or bind-mounted, the DEX checksums change. ART detects the mismatch and rejects the stale `.odex` file.
- **Generalization Invariant**:
  When installing or mounting sanitized APKs on rooted devices, the OAT cache must be reset/recompiled using `cmd package compile -m verify -f <package>` (or `--reset`).

### Lesson 4: Multi-DEX Memory Churn & Lazy DexPool Allocation
- **Symptom**: Processing enterprise APKs (180MB–320MB, 14–21 DEX files) during unit tests triggered `OutOfMemoryError: Java heap space` on 2GB JVM heaps.
- **Root Cause**:
  `DexPool` allocates large internal hash tables and string intern pools. Allocating a `DexPool` for every DEX in a 21-DEX app, even when 15 of those DEXes contain zero target classes, exhausts heap space.
- **Generalization Invariant**:
  Pass 2 must be **lazy**: DEX files must be evaluated in-memory, and `DexPool` must only be allocated if `anyClassChanged == true`. If a DEX has 0 mutations, the original raw bytes must be re-emitted directly.

### Lesson 5: Zero Placebo Patching via Cross-DEX Reachability
- **Symptom**: Morphe and older Lucky Patcher scripts report "Success: patched telemetry" on methods that have 0 incoming invocations (e.g., dead code in unused SDK modules).
- **Reality**: The app continues transmitting telemetry from other active SDK paths, giving the user a false sense of security.
- **Generalization Invariant**:
  MP-Manager's sanitizer must stream an index (`DexReachabilityIndex`) across all DEXes first. Any target method with 0 incoming invocations must be classified as `DEAD_CODE_SKIPPED`, not counted as a protection success.

---

## 3. Open Questions to Answer via Additional Apps (Xfinity, etc.)

1. **How do different apps handle nullified WorkManager workers?**
   - In Amazon, rewriting `Worker.doWork()` to return `Result.success()` kept the WorkManager queue healthy and prevented rescheduling loops. Does Xfinity or DoorDash rely on worker output data (`Data`), and does an empty `Result.success()` cause null pointer exceptions downstream?
2. **Standard Mode vs. Aggressive Mode boundaries**:
   - What differentiates "Standard Mode" (safe signature catalog + reachability verification) from "Aggressive Mode" (pure structural bytecode elimination)?
   - In Aggressive Mode, can we neutralize all classes matching `*telemetry*`, `*tracker*`, `*analytics*`, or `*assistant*` by gutting their static initializers (`<clinit>`) and constructor bodies, or will that break dependency injection frameworks (Dagger, Hilt, Guice)?
3. **Obfuscated / ProGuard-Renamed Telemetry Targets**:
   - In apps where package names are flattened to `a.b.c.a`, how effective are structural method invocations (calls to `Settings.Secure.getString(..., ANDROID_ID)` or `AdvertisingIdClient.getAdvertisingIdInfo`) versus domain sinkholing?
4. **AI Assistant Ingress Patterns**:
   - Amazon uses bottom-bar tab controllers (`BottomTabsBarSavXController`) and enum eligibility managers (`SavXEligibilityState`). How does Xfinity structure Comcast Assistant / AIQ (e.g. WebView bridges, FAB buttons, or intent handlers)?
