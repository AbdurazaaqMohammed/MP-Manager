# Design Specification: Dual-Mode Autonomous App Sanitizer Engine (Standard vs. Aggressive Mode)

**Date**: November 19, 2026  
**Status**: DRAFT - PENDING APPROVAL  
**Target Component**: MP-Manager Security & Privacy Antivirus Suite (`io.github.abdurazaaqmohammed.feature.apk.privacy`)

---

## 1. Context & Motivation

In our physical tests on the Pixel 10 Pro XL, the dual-module engine successfully sterilized Amazon Shopping (`com.amazon.mShop.android.shopping`, 183 MB), eliminating the Rufus AI Assistant from the bottom navigation bar and dropping all background telemetry while maintaining 100% application stability.

However, real-world Android apps span a spectrum of complexity:
- Some users require **maximum stability** (e.g. enterprise apps with fragile Dagger/Hilt dependency injection graphs where missing dependencies trigger runtime crashes).
- Other users require **absolute privacy & surveillance elimination** (e.g. apps that employ obfuscated trackers, dynamic reflection, hardware fingerprinting via `Build.SERIAL` or `Settings.Secure.ANDROID_ID`, or persistent background WorkManager jobs).

To balance these goals, MP-Manager will provide a **two-tier execution engine**:
1. **Standard Mode (Low-Risk / Stability Priority)**: "Generally guarantees app remains stable, but may miss some targets in highly obfuscated or encrypted apps."
2. **Aggressive Mode (High-Risk / Zero-Tolerance Privacy)**: "Generally will kill all telemetry and assistants, but might also kill your app. If you try this and your app doesn't launch, try standard mode."

---

## 2. Core Differences: Standard vs. Aggressive Mode

| Capability | Standard Mode (Safe) | Aggressive Mode (Zero-Tolerance) |
| :--- | :--- | :--- |
| **Telemetry Scope** | Known SDK signatures (Firebase, AppsFlyer, Adjust, Sift, Branch, Braze, Dynatrace, CUJO, Kochava) | All known SDKs + structural dataflow matching (any class querying hardware identifiers or analytics sinks) |
| **Hardware Fingerprinting** | Blank `TelephonyManager` identifiers and Advertising ID | Blank telephony, ADID, `Settings.Secure.ANDROID_ID` (`""`), `Build.SERIAL`, `WifiInfo.getMacAddress()` |
| **Domain Sinkholing** | Strict URI scheme matching (`https?://`, `wss?://`) on verified tracking/AI streaming domains | Strict URI scheme + bare hostnames + wildcards (`*telemetry*`, `*metrics*`, `*tracking*`) |
| **Worker Neutralization** | Return `ListenableWorker.Result.success()` for known tracking workers | Neutralize known tracking workers + suppress `WorkManager.enqueuePeriodicWork` callsites in bytecode |
| **AI / Assistant Scope** | Verified AI controllers with incoming callers (`DexReachabilityIndex`) | All AI controllers + UI ingress classes + WebLab/A/B experiment callsite forcing |
| **Class Initializer (`<clinit>`)** | Untouched (preserves dependency injection graphs) | Cleared / NOP'd on pure tracker classes to prevent static initialization side-effects |
| **Anti-Placebo Enforcement** | Enforced: dead code is skipped to ensure reported metrics represent real active protection | Enforced: dead code is skipped; active mutations strictly distinguished |

---

## 3. Architecture & Data Structures

### 3.1. `SanitizationMode` Enum
```java
public enum SanitizationMode {
    STANDARD("Standard (Safe)", "Eliminates known telemetry and AI elements with guaranteed app stability."),
    AGGRESSIVE("Aggressive (Deep)", "Aggressively neutralizes all telemetry, hardware fingerprinting, and workers. May cause unstable apps to crash.");

    public final String title;
    public final String description;

    SanitizationMode(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
```

### 3.2. Extended `PrivacyOptions`
```java
public class PrivacyOptions {
    public SanitizationMode mode = SanitizationMode.STANDARD;
    
    // Module A: Telemetry & Fingerprinting
    public boolean stripLogCalls = true;
    public boolean blankTelephonyIdentifiers = true;
    public boolean blankAdvertisingId = true;
    public boolean blankAndroidId = false; // Enabled in AGGRESSIVE
    public boolean blankHardwareSerial = false; // Enabled in AGGRESSIVE
    public boolean neutralizeBackgroundWorkers = true;
    public boolean suppressPeriodicWorkEnqueue = false; // Enabled in AGGRESSIVE
    public boolean sinkholeTelemetryDomains = true;
    public boolean deepStructuralAnalysis = false; // Enabled in AGGRESSIVE
    
    // Module B: AI & Assistant Elements
    public boolean nullifyAiAssistants = true;
    public boolean neutralizeAiTabIngress = true;
    public boolean forceExperimentOff = true;
    
    public static PrivacyOptions standard() { ... }
    public static PrivacyOptions aggressive() { ... }
}
```

### 3.3. Structural Hardware Fingerprint Nullifier
In Aggressive Mode, bytecode invocations that harvest hardware identifiers are rewritten at the call-site:
- `Settings.Secure.getString(ContentResolver, "android_id")` $\to$ returns `const-string ""`
- `Build.getSerial()` $\to$ returns `const-string ""`
- `WifiInfo.getMacAddress()` $\to$ returns `const-string "02:00:00:00:00:00"`
- `WifiInfo.getSSID()` $\to$ returns `const-string "<unknown ssid>"`

---

## 4. Empirical Evaluation Target: Xfinity Digital Home

We will validate both modes against the real Xfinity Digital Home binary (`stock.apk`, 308 MB, 21 DEXes):
1. **Standard Mode Pass**:
   - Neutralize Comcast AIQ (`com.comcast.aiq.xa`), CUJO AI Telemetry (`com.cujo.otm.telemetry`), Firebase Analytics, Crashlytics.
   - Verify stability and clean launch.
2. **Aggressive Mode Pass**:
   - Apply deep structural ID blanking, broad AI assistant suppression, WorkManager enqueue neutralization.
   - Measure stability difference and identify any breakages in DI or reactive frameworks.
