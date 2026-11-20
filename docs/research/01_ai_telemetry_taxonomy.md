# Taxonomy of In-App AI & Telemetry in Modern Android Applications

## Overview
Enterprise applications do not implement AI or telemetry as isolated monolithic codeblocks. Instead, modern high-scale apps (Amazon, Xfinity, Walmart, Target, Lowe's, AT&T) share three universal architectural integration patterns.

---

### Pattern 1: Dynamic Cloud Feature Gates & A/B Engines
Apps rarely ship hardcoded binary flags. Instead, they query cloud-managed experiment providers:
- **Amazon:** `com.amazon.weblab.mobile.IMobileWeblab` (experiments prefixed `RUFUS_*`, `AI_ASSISTANT`). Treatment `"T1"`/`"T2"` = ON, `"C"` = Control (OFF).
- **Target / Walmart:** LaunchDarkly & internal split routers (`sparky`, `genai`, `agentic_shopper_clarity`).
- **Xfinity:** Unleash & GraphQL config managers (`DigitalHomeConfiguration.getSupportsAssistant()`).

#### Sanitization Principle:
Intercept the treatment/flag evaluation callback. If the experiment key matches `(?i).*(rufus|assistant|ai|copilot|genai|sparky|chat).*`, rewrite the return value to `"C"` (Control) or `false`.

---

### Pattern 2: Centralized Eligibility & State Managers
Before rendering UI, apps consult a singleton state evaluator:
- **Amazon:** `com.amazon.mShop.savX.manager.eligibility.SavXStableEligibilityManager`
  - `isEligible() -> boolean`
  - `evaluateEligibility() -> SavXEligibilityState` (`ELIGIBLE` vs `NOT_ELIGIBLE`)
- **Xfinity:** `com.xfinity.digitalhome.manager.UserFeatureManager`
  - `getSupportsAssistant() -> boolean`
  - `getXfinityAssistantEnabled() -> boolean`

#### Sanitization Principle:
Do not rely on single legacy methods. Force the primary eligibility state evaluator (`evaluateEligibility`) to return `NOT_ELIGIBLE` / `false`. Ensure compound guard conditions (e.g. `!flagA && !flagB`) are neutralized.

---

### Pattern 3: UI Layer Ingress & Tab Inset Controllers
Even if backend managers are stubbed, UI controllers may attempt to render empty tabs, floating buttons, or uninitialized webviews:
- **Amazon:** `BottomTabsBarSavXController` (handles `RUFUS_INGRESS` in `AmazonTabLayout`).
- **Home Depot:** `ExpandableFab` / `magicapron` floating action buttons.
- **Xfinity:** `XfinityAssistantLauncherActivity` and Jetpack Navigation routes (`XfinityAssistantDeepLinkRoute`).

#### Sanitization Principle:
Zero-crash UI neutralization:
1. Prevent tab item insertion (`RUFUS_INGRESS` stripped from tab map).
2. Launcher activities stubbed with immediate `finish()` rather than crashing with `NullPointerException` or `ClassNotFoundException`.

---

### Pattern 4: Transport-Layer Telemetry Sinks
Telemetry is transmitted via:
1. Native background jobs (`androidx.work.ListenableWorker`, e.g., `TelemetryWorker`).
2. OkHttp network interceptors and socket pipelines.
3. First-party REST/GraphQL endpoints (`sift.*`, `*.datadoghq.com`, `app-measurement.com`).

#### Sanitization Principle:
1. Stub `ListenableWorker.doWork()` to return `Result.success()`.
2. Redirect string constants matching telemetry URLs to `127.0.0.1:0`.
3. Strip `android.util.Log` and blank `TelephonyManager` / `AdvertisingIdClient` identifiers.
