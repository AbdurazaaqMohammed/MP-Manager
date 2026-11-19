# Design Document: Dual-Module Sanitizer Engine (Telemetry & AI Nullification)

**Date:** 2026-11-19  
**Status:** In Review  
**Target Project:** MP-Manager (`io.github.abdurazaaqmohammed.MPManager`)  
**Target Platforms:** Android 8.0+ (API 26 to API 37 / Android 16), Rooted & Non-Rooted Devices  

---

## 1. Intent & The "Why"
Third-party patchers (such as Morphe and Proteus) promise to disable in-app AI assistants and strip telemetry. However, deep reverse-engineering of modern enterprise applications (Xfinity v6.14.0-2 and Amazon Shopping v32.19.0.100) reveals that existing patchers suffer from **placebo patching** (rewriting dead-code methods with zero active callers) and **zero-match false success reporting** (failing silently on non-standard/obfuscated telemetry while displaying green checkmarks).

The goal of this feature is to establish a **reachability-aware, multi-layer Dalvik bytecode mutation engine** inside MP-Manager that deterministically nullifies both:
1. **Telemetry & Tracking Elements** (event loggers, background workers, ad/device identifiers, and network transport sinks).
2. **Generative AI & Assistant Elements** (A/B experiment gates, eligibility state managers, launcher activities, and UI tab ingress points).

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- **Deterministic Efficacy:** Intercept at multiple defensive layers (cloud feature gate, eligibility manager, and network transport boundary) so that even if one layer changes across app updates, the invasive feature cannot execute or exfiltrate data.
- **Non-Destructive Mutation:** Never delete classes or strip methods completely (which triggers `ClassNotFoundException` or `NoSuchMethodError`). Instead, inject type-safe stubs (`return-void`, `const/4 v0, 0x0`, `sget-object v0, NOT_ELIGIBLE`, etc.).
- **Auditable Metrics:** Provide explicit counters of modified instructions and sinkholed domains. Fail/warn if 0 matching active instructions were found, eliminating the false sense of security.
- **Extensible Heuristics:** Unified pattern-matching engine that generalizes across Amazon (Rufus/SavX), Xfinity (XA), Walmart (Sparky), Target (Topas), and standard analytics SDKs.

### Non-Goals
- De-obfuscating custom native C/C++ `.so` libraries (handled separately by native auditing).
- Man-in-the-middle TLS proxying (all modifications occur statically inside the APK Dalvik bytecode).

### Anti-Goals
- **No Blind Fingerprinting:** Never report "Success" if the matching method has 0 incoming call references or if 0 instructions were modified.
- **No App Crashing:** Never leave dangling UI hooks or unhandled exceptions when a user taps an AI button.

---

## 3. Constraints
- Must run in-memory within Dalvik/ART using `dexlib2` on Android.
- Must support multi-DEX APKs (up to 25+ DEX files per modern enterprise app).
- Must produce compliant, zipaligned, and signed APKs compatible with both rooted devices (via `AppReinstaller` with data preservation) and non-rooted devices (via signature spoofing).

---

## 4. Approaches Considered

1. **Approach 1: Dual-Module Engine First (CHOSEN):**
   - Refactor `PrivacyOptions` and `PrivacyBytecodeEngine` into two explicit modules: `Module A (Telemetry Nullification)` and `Module B (AI & Assistant Nullification)`.
   - Implement reachability-aware bytecode mutation and transport sinkholing.
   - Verify with comprehensive unit tests and automated DEX mutations before building the Batch UI.
   - *Reason Chosen:* Ensures the core transformation engine is rock-solid and verified against real multi-DEX binaries before exposing user-facing UI controls.

2. **Approach 2: Full UI & Batch App Selector First:**
   - Build `AppSanitizerActivity` with installed app checkboxes and multi-select first, then wire the engine.
   - *Trade-off:* Leaves the transformation logic unverified until late in the development cycle; risks building UI for unproven heuristics.

3. **Approach 3: Amazon Rufus Spike Verification First:**
   - Manually patch and push only Amazon Shopping first.
   - *Trade-off:* Spikes are throwaway and do not create clean, modular architecture inside MP-Manager.

---

## 5. Architectural Specification

### A. Extended Configuration (`PrivacyOptions.java`)
```java
public class PrivacyOptions implements Serializable {
    // Module A: Telemetry
    public boolean stripLogging = true;
    public boolean emptyTelephonyIds = true;
    public boolean neutralizeAdIds = true;
    public boolean maskDeveloperMode = true;
    public boolean sinkholeTelemetryDomains = true;
    public boolean neutralizeBackgroundWorkers = true;

    // Module B: AI & Chatbots
    public boolean nullifyAiAssistants = true;
    public boolean forceAiExperimentControl = true;
    public boolean stubAiEligibilityManagers = true;
    public boolean neutraliseAiTabIngress = true;
}
```

### B. Extended Statistics & Audit Metrics (`PrivacyStats.java`)
```java
public class PrivacyStats {
    // Telemetry
    public int logCallsStripped = 0;
    public int telephonyGettersBlanked = 0;
    public int adIdsNeutralized = 0;
    public int devModeChecksMasked = 0;
    public int telemetryDomainsSinkholed = 0;
    public int backgroundWorkersNeutralized = 0;

    // AI & Assistants
    public int aiFeatureGatesNeutralized = 0;
    public int aiEligibilityManagersStubbed = 0;
    public int aiTabIngressNeutralized = 0;
    public int aiEndpointsSinkholed = 0;

    public int totalMutations() {
        return logCallsStripped + telephonyGettersBlanked + adIdsNeutralized +
               devModeChecksMasked + telemetryDomainsSinkholed + backgroundWorkersNeutralized +
               aiFeatureGatesNeutralized + aiEligibilityManagersStubbed +
               aiTabIngressNeutralized + aiEndpointsSinkholed;
    }
}
```

### C. Multi-Layer AI Nullification Heuristics (`AiBytecodeRules.java`)
1. **Experiment Gate Neutralization (`IMobileWeblab` / LaunchDarkly):**
   - Match invoke calls to `getTreatmentAssignment()` or `getTreatmentAndRecordTrigger()`.
   - If experiment name matches `(?i).*(rufus|assistant|sparky|genai|copilot|ai_chat).*`, replace with `"C"` (Control) or safe boolean `false`.
2. **State / Eligibility Manager Neutralization:**
   - **Amazon:** Stub `SavXStableEligibilityManager.evaluateEligibility()` $\to$ `SavXEligibilityState.NOT_ELIGIBLE`.
   - **Xfinity:** Stub `UserFeatureManager.getSupportsAssistant()` $\to$ `false`, and stub `DefaultXfinityAssistant.start()` to return `Completable.complete()`.
   - **Universal:** Detect methods named `evaluateEligibility`, `isEligible`, `supportsAssistant`, `isAiEnabled` and stub them to return safe false/not-eligible constants.
3. **UI / Tab Ingress Neutralization:**
   - **Amazon:** In `BottomTabsBarSavXController`, intercept `selectRufusTabItem` and tab population to no-op.
   - **Xfinity:** In `XfinityAssistantLauncherActivity`, inject `finish()` directly into `onCreate()`.

### D. Telemetry & Worker Neutralization (`TelemetryBytecodeRules.java`)
1. **Worker Neutralization:**
   - If a class extends `androidx.work.ListenableWorker` and matches `.*(telemetry|analytics|tracking|metric|cujo|otm).*`:
   - Rewrite `doWork()` to immediately instantiate and return `ListenableWorker$Result$Success`.
2. **Domain Sinkholer Expansion:**
   - Target string pools across all methods.
   - Sinkhole known tracker domains and streaming AI sockets (`andistreaminghub`, `aiq.xa`, `sift.*`, `*.datadoghq.com`) to `127.0.0.1:0`.

---

## 6. Resolved Design & Builder Decisions (Builder Check Review)

### A. Reachability Tracking without Out-of-Memory
- **Two-Pass Streaming Scanner:** Rather than building an in-memory cross-DEX call graph AST, `PrivacyBytecodeEngine` utilizes a lightweight two-pass streaming architecture:
  - *Pass 1 (Invocation Set Collection):* Streams through all DEX classes and extracts target method invocation signatures (`Lclass;->method(args)Ret`) into a compact `HashSet<String>`.
  - *Pass 2 (Mutation & Verification):* When matching a target method declaration to stub, checks membership in the invocation set. If a method has 0 references across all DEX files in the APK, it is flagged as `DEAD_CODE_SKIPPED` rather than falsely reported as a successful patch.
  - *Callsite Inversion:* For experiment treatments (`IMobileWeblab`, LaunchDarkly), mutations occur directly at the `invoke-*` callsite, guaranteeing that only active control-flow paths are modified.

### B. Stubbing Contracts & Zero-Crash Fallbacks
- **Boolean-First Nullification:** High-level eligibility and feature checks (`isEligible()Z`, `isEnabled()Z`, `supportsAssistant()Z`) are stubbed with `const/4 v0, 0x0` followed by `return v0`. Primitive booleans cannot trigger `NullPointerException`.
- **WorkManager Neutralization:** Targets `androidx.work.Worker.doWork()` by invoking `ListenableWorker.Result.success()` (`invoke-static {} Landroidx/work/ListenableWorker$Result;->success()Landroidx/work/ListenableWorker$Result;` -> `move-result-object v0` -> `return-object v0`). For asynchronous workers (`CoroutineWorker`, `RxWorker`), domain sinkholing ensures zero outbound egress without touching complex reactive return types.
- **Transport Sinkholing as Universal Safety Net:** In the event an unhandled edge case or dynamic reflection invokes an AI assistant or tracking event, all network transport endpoints are rewritten to `127.0.0.1:0` (`ECONNREFUSED`), guaranteeing zero data exfiltration without crashing the UI.

### C. Obfuscation & Heuristics Strategy
- **Three-Tier Detection:**
  1. *String Pool & Experiment Regex:* Scans string constants for experiment keys (`"rufus"`, `"sparky"`, `"assistant"`, `"ai_chat"`).
  2. *DI/Reflected Signatures:* Major enterprise applications preserve core eligibility managers (`SavXStableEligibilityManager`, `IMobileWeblab`) to support Dagger/Hilt injection.
  3. *Domain Sinkholing:* Agnostic to ProGuard/R8 class and method obfuscation.

### D. Device Scope (Root vs. Non-Root)
- **Primary Tier (Rooted Devices):** Full in-place replacement via `AppReinstaller` with automatic backup and restoration of `/data/user/0/<package>` and ownership realignment (`chown -R`).
- **Secondary Tier (Non-Rooted Devices):** Includes in-app `SignatureSpoofer` bytecode stubs to defeat internal anti-tamper signature verification (`PackageManager.getPackageInfo(GET_SIGNATURES)`). Package-name cloning for side-by-side installation is reserved for the UI phase.

### E. Multi-State Audit Reporting
- The engine will not fail an APK if one module finds zero targets (e.g. an app with telemetry but no AI).
- Reports per-module metrics:
  - `Module A (Telemetry): SUCCESS (n mutations)` or `NOT_DETECTED (0 mutations)`
  - `Module B (AI & Assistants): SUCCESS (n mutations)` or `NOT_DETECTED (0 mutations)`
- Only flags `WARNING_ZERO_MUTATIONS` if zero mutations occurred across all enabled modules.

---

## 7. Implementation & Verification Plan
1. **Engine Implementation:** Update `PrivacyOptions.java`, `PrivacyStats.java`, and `PrivacyBytecodeEngine.java` in `/home/deck/MP-Manager/app/src/main/java/io/github/abdurazaaqmohammed/feature/apk/privacy/`.
2. **Unit Tests:** Create `DualModuleSanitizerTest.java` verifying that both Telemetry and AI stubs mutate test DEX bytecode without corrupting class structures.
3. **Verification on Real Apps:** Run the engine against both `com.xfinity.digitalhome` and `com.amazon.mShop.android.shopping` binaries and verify mutation counts.
