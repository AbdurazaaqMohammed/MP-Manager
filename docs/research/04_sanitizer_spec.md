# Engineering Specification: MP-Manager App Sanitizer Suite

## 1. Overview & Objectives
The App Sanitizer Suite is a native Dalvik bytecode hardening and privacy suite embedded within MP-Manager. It provides batch processing of installed or storage-staged APKs to:
1. **Nullify Telemetry Elements:** Strip `android.util.Log`, blank telephony & advertising identifiers, neutralize background `ListenableWorker` jobs, and sinkhole tracker domains at the bytecode level.
2. **Nullify AI & Assistant Elements:** Detect and neutralize conversational AI assistants (Amazon Rufus, Xfinity Assistant, Sparky, Copilot, etc.) across feature gates, eligibility managers, and UI tab ingress controllers.
3. **Auditable Metrics:** Provide concrete counts of instructions modified and endpoints sinkholed; explicitly flag and warn when zero targets were modified (eliminating "placebo patching").
4. **Root & Non-Root Support:** In-place reinstall with data preservation on rooted devices via `AppReinstaller`; clean install with signature spoofing or side-by-side cloned packages on non-rooted devices.

---

## 2. Core Architecture Components

### A. Discovery & Reachability Scanner (`PrivacyScannerEngine`)
- Analyzes all DEX files using `dexlib2`.
- Tracks incoming call references (`getIncomingReferences()`) to ensure methods are active and reachable.
- Classifies discovered elements into:
  - `Telemetry` (SDK event callers, workers, endpoints)
  - `AI / Assistant` (Eligibility managers, WebLab flags, launcher activities)
  - `Hardware IDs` (Telephony, Android ID, GAID)

### B. Dalvik Bytecode Mutator (`PrivacyBytecodeEngine`)
- Surgical opcode transformation in-memory via `MutableMethodImplementation`.
- Safe return stubs based on method return type:
  - `V` $\to$ `BuilderInstruction10x(Opcode.RETURN_VOID)`
  - `Z` / `I` / `B` / `S` $\to$ `const/4 reg, 0x0; return reg`
  - `J` $\to$ `const-wide/16 reg, 0x0; return-wide reg`
  - `Ljava/lang/Object;` / Reference $\to$ `const/4 reg, 0x0; return-object reg`
  - `Landroidx/work/ListenableWorker$Result;` $\to$ `new-instance reg, ListenableWorker$Result$Success; return-object reg`
  - `Lkotlin/Unit;` $\to$ `sget-object reg, Lkotlin/Unit;->INSTANCE; return-object reg`

### C. Domain Sinkholer (`DomainSinkholer`)
- Scans `CONST_STRING` and `CONST_STRING_JUMBO` instructions across all methods.
- Matches against regex and domain blacklist (`*.datadoghq.com`, `sift.*`, `app-measurement.com`, `*.branch.io`, AI streaming endpoints).
- Replaces matches with `127.0.0.1:0`.

### D. Signature Spoofer (`SignatureSpoofer`)
- Intercepts `PackageManager.getPackageInfo(..., GET_SIGNATURES | GET_SIGNING_CERTIFICATES)` calls inside the APK.
- Returns original embedded developer certificates, preventing anti-tamper crashes on re-signed APKs.

---

## 3. UI Flow (`AppSanitizerActivity`)
1. **Entry Point:** Tools Hub $\to$ "App Sanitizer" card (`sidebar_sanitizer`).
2. **App Picker Screen:**
   - Tabs: `Installed Apps` | `Storage APKs`.
   - Multi-select checkboxes with "Select All".
   - Search bar filtering by app name, package name, or detected threats.
3. **Action Dialog:**
   - `[✓] Nullify Telemetry & Tracking`
   - `[✓] Nullify AI & Chatbot Elements`
   - `[✓] Blank Hardware & Device IDs`
   - `[✓] Embed Signature Spoofer (Bypass Anti-Tamper)`
   - Install Mode: `Automatic (Root / Data Preserved)` vs `Standard Install` vs `Clone (Side-by-Side)`.
4. **Progress & Execution Screen:**
   - Step-by-step progress per APK: *Extracting $\to$ Scanning $\to$ Mutating Bytecode $\to$ Signing $\to$ Installing*.
   - Final report with detailed interception metrics.
