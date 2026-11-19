# Dual-Module Sanitizer Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a robust, reachability-aware Dalvik bytecode mutation engine inside MP-Manager that deterministically nullifies Telemetry Elements (Module A) and Generative AI / Assistant Elements (Module B) across multi-DEX APKs without causing crashes or reporting false successes.

**Architecture:** A streaming two-pass Dalvik transformation engine built on `dexlib2`. Pass 1 scans invocation instructions across all DEX files into a compact reference set (`DexReachabilityIndex`) to verify active callers; Pass 2 applies type-safe bytecode mutations (boolean stubs, worker result injection, experiment treatment rewrites, and socket loopback sinkholes). Each module reports granular mutation metrics (`SUCCESS`, `NOT_DETECTED`, or `DEAD_CODE_SKIPPED`).

**Tech Stack:** Java 17/24, Android SDK (API 26-37), `dexlib2` / `smali`, `re-android:APKEditor`, JUnit 4.

**Spec:** [`docs/superpowers/specs/2026-11-19-dual-module-sanitizer-engine-design.md`](file:///home/deck/MP-Manager/docs/superpowers/specs/2026-11-19-dual-module-sanitizer-engine-design.md)

---

## Global Constraints
- Target platforms: Android 8.0+ (API 26 to API 37 / Android 16), rooted and non-rooted devices.
- Non-destructive mutation: never delete classes or strip methods completely; always inject type-safe stubs (`const/4 v0, 0x0`, `return v0`, or `ListenableWorker$Result.success()`).
- No placebo patching: never report success for methods with 0 incoming invocations across the APK.
- No app crashes: Object-returning methods must not blindly return null if callers dereference them; boolean-first stubs and socket sinkholing (`127.0.0.1:0`) provide fail-safe isolation.
- Heap efficiency: must operate within 256MB–512MB ART heap limits on multi-DEX APKs containing 25+ DEX files.

---

## Review Focus
1. **Silent False Success on Dead Code:** A patch claims success when modifying a method that has zero incoming call references in the target APK. *Expectation:* The engine marks it as `DEAD_CODE_SKIPPED` and counts 0 mutations.
2. **NullPointerException on Object Return Stubs:** Stubbing an Object-returning method with `null` causing callers to crash. *Expectation:* Prefer boolean getters (`false`) or static success instances; use network sinkholes for complex reactive return flows.
3. **Out-of-Memory on 25+ DEX Files:** Attempting to cache full ASTs of all DEX files simultaneously. *Expectation:* Stream one DEX at a time with a compact signature index.
4. **Zero-Mutation App Abort:** An app with telemetry but no AI failing the build when AI nullification is enabled. *Expectation:* Granular status: Module A succeeds, Module B reports `NOT_DETECTED`, overall APK build proceeds.
5. **Class Loading Failure on Missing External Types:** Emitting bytecode referencing third-party classes not packaged in the app. *Expectation:* Only reference Android framework, AndroidX, or classes already defined in the target APK.

---

### Task 1: Extended Configuration, Metrics & Audit Enums

**Files:**
- Modify: `app/src/main/java/io/github/abdurazaaqmohammed/feature/apk/privacy/PrivacyOptions.java`
- Modify: `app/src/main/java/io/github/abdurazaaqmohammed/feature/apk/privacy/PrivacyStats.java`
- Create: `app/src/main/java/io/github/abdurazaaqmohammed/feature/apk/privacy/ModuleAuditResult.java`
- Test: `app/src/test/java/io/github/abdurazaaqmohammed/feature/apk/privacy/PrivacyOptionsStatsTest.java`

**Interfaces:**
- Consumes: Existing `PrivacyOptions` and `PrivacyStats`
- Produces:
  - `PrivacyOptions`: fields `nullifyAiAssistants`, `forceAiExperimentControl`, `stubAiEligibilityManagers`, `neutralizeBackgroundWorkers`
  - `PrivacyStats`: counters `backgroundWorkersNeutralized`, `aiFeatureGatesNeutralized`, `aiEligibilityManagersStubbed`, `aiTabIngressNeutralized`, `aiEndpointsSinkholed`, `deadCodeSkipped`
  - `ModuleAuditResult`: enum `Status { SUCCESS, NOT_DETECTED, WARNING_ZERO_MUTATIONS, DEAD_CODE_SKIPPED }`

- [ ] **Step 1: Write failing test `PrivacyOptionsStatsTest.java`**
  Verify default options, all-toggles builder, stats merging, and total mutation calculations including AI counters.
- [ ] **Step 2: Run test to verify it fails**
  Run: `JAVA_HOME=/usr/lib/jvm/java-24-openjdk PATH=/usr/lib/jvm/java-24-openjdk/bin:$PATH /home/deck/MP-Manager/gradlew -p /home/deck/MP-Manager :app:testFullDebugUnitTest --tests "io.github.abdurazaaqmohammed.feature.apk.privacy.PrivacyOptionsStatsTest"`
  Expected: FAIL with compilation error on missing fields/class.
- [ ] **Step 3: Implement `ModuleAuditResult.java` and update `PrivacyOptions.java` & `PrivacyStats.java`**
  Add all new fields, serializable constructors, and merge logic.
- [ ] **Step 4: Run test to verify it passes**
  Expected: PASS
- [ ] **Step 5: Commit**
  `git commit -m "feat(privacy): add AI nullification options, metrics, and audit enums"`

---

### Task 2: Expanded Domain Sinkholer for AI Streaming & Obfuscated Telemetry

**Files:**
- Modify: `app/src/main/java/io/github/abdurazaaqmohammed/feature/apk/privacy/DomainSinkholer.java`
- Modify: `app/src/test/java/io/github/abdurazaaqmohammed/feature/apk/privacy/DomainSinkholeTest.java`

**Interfaces:**
- Consumes: `DomainSinkholer.isTelemetryEndpoint(String)`
- Produces:
  - `DomainSinkholer.isAiOrTelemetryEndpoint(String str)`
  - Adds endpoints: `andistreaminghub`, `aiq.xa`, `siftscience.com`, `datadoghq.com`, `braze.com`, `launchdarkly.com`
  - `DomainSinkholer.sinkholeMethod(Method, PrivacyStats, PrivacyOptions)`

- [ ] **Step 1: Write failing test in `DomainSinkholeTest.java`**
  Add test assertions for AI streaming endpoints (`andistreaminghub.amazon.com`, `aiq.xa.comcast.net`) and analytics services.
- [ ] **Step 2: Run test to verify it fails**
  Run: `JAVA_HOME=/usr/lib/jvm/java-24-openjdk PATH=/usr/lib/jvm/java-24-openjdk/bin:$PATH /home/deck/MP-Manager/gradlew -p /home/deck/MP-Manager :app:testFullDebugUnitTest --tests "io.github.abdurazaaqmohammed.feature.apk.privacy.DomainSinkholeTest"`
  Expected: FAIL (endpoints not recognized as sinkhole targets).
- [ ] **Step 3: Update `DomainSinkholer.java`**
  Extend domain list and URL regex; update stats tracking for `aiEndpointsSinkholed` vs `telemetryDomainsSinkholed`.
- [ ] **Step 4: Run test to verify it passes**
  Expected: PASS
- [ ] **Step 5: Commit**
  `git commit -m "feat(privacy): expand DomainSinkholer to sinkhole AI streaming and analytics endpoints"`

---

### Task 3: Streaming Multi-DEX Reachability Discovery Engine

**Files:**
- Create: `app/src/main/java/io/github/abdurazaaqmohammed/feature/apk/privacy/DexReachabilityIndex.java`
- Test: `app/src/test/java/io/github/abdurazaaqmohammed/feature/apk/privacy/DexReachabilityIndexTest.java`

**Interfaces:**
- Consumes: `List<DexBackedDexFile>`
- Produces:
  - `DexReachabilityIndex.buildFromDexFiles(Iterable<DexBackedDexFile>)`
  - `boolean isMethodInvoked(String declaringClass, String methodName, String returnType)`
  - `boolean isFieldReferenced(String declaringClass, String fieldName)`
  - `int getTotalIndexedReferences()`

- [ ] **Step 1: Write failing test `DexReachabilityIndexTest.java`**
  Construct two sample DEX files via Smali: `classes.dex` calling `Ltest/Service;->activeCall()V`, and `classes2.dex` defining `activeCall()V` and `deadCall()V`. Verify index reports `activeCall()` as invoked and `deadCall()` as uninvoked.
- [ ] **Step 2: Run test to verify it fails**
  Run: `JAVA_HOME=/usr/lib/jvm/java-24-openjdk PATH=/usr/lib/jvm/java-24-openjdk/bin:$PATH /home/deck/MP-Manager/gradlew -p /home/deck/MP-Manager :app:testFullDebugUnitTest --tests "io.github.abdurazaaqmohammed.feature.apk.privacy.DexReachabilityIndexTest"`
  Expected: FAIL with missing class `DexReachabilityIndex`.
- [ ] **Step 3: Implement `DexReachabilityIndex.java`**
  Iterate through all instructions in methods; inspect `ReferenceInstruction` with `MethodReference` and `FieldReference`; store compact method keys in a `HashSet<String>`.
- [ ] **Step 4: Run test to verify it passes**
  Expected: PASS
- [ ] **Step 5: Commit**
  `git commit -m "feat(privacy): implement streaming multi-DEX reachability index"`

---

### Task 4: Telemetry & Background Worker Bytecode Nullification (Module A)

**Files:**
- Modify: `app/src/main/java/io/github/abdurazaaqmohammed/feature/apk/privacy/PrivacyBytecodeEngine.java`
- Test: `app/src/test/java/io/github/abdurazaaqmohammed/feature/apk/privacy/WorkerNeutralizationTest.java`

**Interfaces:**
- Consumes: `DexReachabilityIndex`, `PrivacyOptions.neutralizeBackgroundWorkers`
- Produces:
  - `PrivacyBytecodeEngine.neutralizeWorkerClass(ClassDef, MutableMethodImplementation, PrivacyStats)`
  - Rewrites `androidx.work.Worker.doWork()` to return `ListenableWorker$Result.success()`.

- [ ] **Step 1: Write failing test `WorkerNeutralizationTest.java`**
  Assemble a mock `Lcom/example/AnalyticsWorker;` extending `Landroidx/work/Worker;` with a mock `doWork()Landroidx/work/ListenableWorker$Result;`. Verify engine mutates `doWork()` to invoke `Result.success()`.
- [ ] **Step 2: Run test to verify it fails**
  Run: `JAVA_HOME=/usr/lib/jvm/java-24-openjdk PATH=/usr/lib/jvm/java-24-openjdk/bin:$PATH /home/deck/MP-Manager/gradlew -p /home/deck/MP-Manager :app:testFullDebugUnitTest --tests "io.github.abdurazaaqmohammed.feature.apk.privacy.WorkerNeutralizationTest"`
  Expected: FAIL
- [ ] **Step 3: Implement worker neutralization in `PrivacyBytecodeEngine.java`**
  Detect worker classes matching telemetry patterns (`analytics`, `telemetry`, `tracker`, `metric`, `cujo`, `otm`); replace `doWork()` body with `invoke-static {} Landroidx/work/ListenableWorker$Result;->success()` -> `move-result-object v0` -> `return-object v0`.
- [ ] **Step 4: Run test to verify it passes**
  Expected: PASS
- [ ] **Step 5: Commit**
  `git commit -m "feat(privacy): implement WorkManager telemetry worker neutralization"`

---

### Task 5: Generative AI & Assistant Bytecode Nullification (Module B)

**Files:**
- Create: `app/src/main/java/io/github/abdurazaaqmohammed/feature/apk/privacy/AiBytecodeRules.java`
- Modify: `app/src/main/java/io/github/abdurazaaqmohammed/feature/apk/privacy/PrivacyBytecodeEngine.java`
- Test: `app/src/test/java/io/github/abdurazaaqmohammed/feature/apk/privacy/AiBytecodeNullificationTest.java`

**Interfaces:**
- Consumes: `DexReachabilityIndex`, `PrivacyOptions.nullifyAiAssistants`
- Produces:
  - `AiBytecodeRules.nullifyEligibilityMethod(Method, DexReachabilityIndex, PrivacyStats)`
  - `AiBytecodeRules.interceptWebLabTreatment(MutableMethodImplementation, int instructionIndex, PrivacyStats)`
  - `AiBytecodeRules.isAiControllerClass(String classType)`

- [ ] **Step 1: Write failing test `AiBytecodeNullificationTest.java`**
  Assemble smali mock of:
  1. `SavXStableEligibilityManager` with `isEligible()Z` and `evaluateEligibility()Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;`
  2. `UserFeatureManager` with `getSupportsAssistant()Z`
  3. `IMobileWeblab` callsite passing `"RUFUS_SEARCH_INGRESS"`
  Verify all are stubbed/rewritten to `false`, `NOT_ELIGIBLE`, or `"C"` treatment without crashing.
- [ ] **Step 2: Run test to verify it fails**
  Run: `JAVA_HOME=/usr/lib/jvm/java-24-openjdk PATH=/usr/lib/jvm/java-24-openjdk/bin:$PATH /home/deck/MP-Manager/gradlew -p /home/deck/MP-Manager :app:testFullDebugUnitTest --tests "io.github.abdurazaaqmohammed.feature.apk.privacy.AiBytecodeNullificationTest"`
  Expected: FAIL
- [ ] **Step 3: Implement `AiBytecodeRules.java` and wire into `PrivacyBytecodeEngine.java`**
  Add eligibility method stubs, experiment treatment callsite interception, and dead-code verification.
- [ ] **Step 4: Run test to verify it passes**
  Expected: PASS
- [ ] **Step 5: Commit**
  `git commit -m "feat(privacy): implement generative AI & assistant nullification rules"`

---

### Task 6: Multi-DEX APK Integration & Granular Audit Reporting

**Files:**
- Modify: `app/src/main/java/io/github/abdurazaaqmohammed/feature/apk/privacy/PrivacyBytecodeEngine.java`
- Modify: `app/src/main/java/io/github/abdurazaaqmohammed/feature/apk/privacy/PrivacyAuditReport.java`
- Test: `app/src/test/java/io/github/abdurazaaqmohammed/feature/apk/privacy/DualModuleSanitizerIntegrationTest.java`

**Interfaces:**
- Consumes: Tasks 1–5
- Produces:
  - `PrivacyBytecodeEngine.sanitizeApkWithAudit(File inputApk, File outputApk, PrivacyOptions options, APKLogger logger)`
  - Returns `PrivacyAuditReport` with granular module status and verified mutation counts.

- [ ] **Step 1: Write failing test `DualModuleSanitizerIntegrationTest.java`**
  Build a test multi-DEX APK containing both Module A (telemetry) and Module B (AI) targets, plus an uninvoked dead-code method. Verify that:
  - Active telemetry is mutated and reported as `SUCCESS`.
  - Active AI is mutated and reported as `SUCCESS`.
  - Dead code method is marked as `DEAD_CODE_SKIPPED`.
  - Overall status is `SUCCESS`.
- [ ] **Step 2: Run test to verify it fails**
  Run: `JAVA_HOME=/usr/lib/jvm/java-24-openjdk PATH=/usr/lib/jvm/java-24-openjdk/bin:$PATH /home/deck/MP-Manager/gradlew -p /home/deck/MP-Manager :app:testFullDebugUnitTest --tests "io.github.abdurazaaqmohammed.feature.apk.privacy.DualModuleSanitizerIntegrationTest"`
  Expected: FAIL
- [ ] **Step 3: Implement full multi-DEX orchestration and audit report generation**
  Connect `DexReachabilityIndex` pass followed by class transformations, outputting valid multi-DEX APK.
- [ ] **Step 4: Run test to verify it passes**
  Expected: PASS
- [ ] **Step 5: Commit**
  `git commit -m "feat(privacy): integrate multi-DEX dual-module sanitization with granular audit reporting"`

---

### Task 7: Real-Binary Verification & In-App Validation on Stock Apps

**Files:**
- Verification Script: `/home/deck/MP-Manager/app/src/test/java/io/github/abdurazaaqmohammed/feature/apk/privacy/RealBinarySanitizerVerification.java`
- Target Binaries:
  - Amazon Shopping (`/home/deck/.gemini/antigravity/scratch/amazon_audit/apk/base.apk`)
  - Xfinity (`/home/deck/.gemini/antigravity/scratch/xfinity_audit/base.apk`)
- Connected Device: Pixel 10 Pro XL (`5A141FDCQS0436`)

**Interfaces:**
- Consumes: Real-world APK binaries and adb bridge
- Produces:
  - Mutation verification logs proving non-zero active mutations.
  - Decompilation verification proving clean Dalvik bytecode.
  - Device install and launch validation.

- [ ] **Step 1: Execute engine against extracted DEX layers of Amazon Shopping and Xfinity**
  Run sanitization pass on both binaries; assert non-zero mutations and zero unhandled exceptions.
- [ ] **Step 2: Verify transformed DEX files with JADX decompilation**
  Decompile transformed `SavXStableEligibilityManager` and `UserFeatureManager` to verify that `isEligible()` decompiles to `return false;`.
- [ ] **Step 3: Build debug APK and install on connected Pixel 10 Pro XL**
  Install patched MP-Manager APK on phone (`pm install -r -d`).
- [ ] **Step 4: Push patched target app and verify runtime stability**
  Verify app launches without crash, Rufus/Assistant elements are disabled, and network telemetry is dropped.
- [ ] **Step 5: Commit**
  `git commit -m "test(privacy): verify dual-module sanitization on real Amazon and Xfinity binaries"`
