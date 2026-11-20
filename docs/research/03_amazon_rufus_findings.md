# Case Study Audit: Amazon Rufus (`com.amazon.mShop.android.shopping`)

## Summary
- **Target App:** Amazon Shopping v32.19.0.100 (183 MB base.apk, 14 DEX files).
- **In-App AI Assistant:** Rufus (Internal code name: **SavX** / Shopping Assistant Voice & eXperience).

---

### Class & Method Discovery

Across 14 DEX files, **284 classes** in `classes10.dex` implement the SavX/Rufus architecture.

#### 1. Core State & Eligibility Controller
- **Class:** `com.amazon.mShop.savX.manager.eligibility.SavXStableEligibilityManager`
- **Key Methods:**
  ```java
  public final boolean isEligible() {
      if (this.eligibility == SavXEligibilityState.INITIAL) {
          update();
      }
      return this.eligibility == SavXEligibilityState.ELIGIBLE;
  }

  private final SavXEligibilityState evaluateEligibility() {
      if (!arePrerequisitesMet()) {
          return SavXEligibilityState.NOT_ELIGIBLE;
      }
      if (getFullyEnabledMarketplaceEligibilityProvider().isEligible()) {
          return SavXEligibilityState.ELIGIBLE;
      }
      if (getEm5ExpansionWebLab().isEnabled()) {
          return SavXEligibilityState.ELIGIBLE;
      }
      return SavXEligibilityState.NOT_ELIGIBLE;
  }
  ```

#### 2. Cloud A/B Experimentation Engine (WebLab)
- **Interface:** `com.amazon.weblab.mobile.IMobileWeblab`
- **Methods:**
  - `getTreatmentAssignment() -> String`
  - `getTreatmentAndRecordTrigger() -> IMobileWeblabTreatmentAndTriggerResult`
- **Relevant Experiments:**
  - `RUFUS_*`
  - `_RUFUS_*`
  - `AI_ASSISTANT`
  - Returning `"C"` (Control) disables the server-side Rufus flag.

#### 3. Bottom Tab UI Ingress
- **Class:** `com.amazon.mShop.rendering.BottomTabsBarSavXController`
- **Tab Ingress Tag:** `"RUFUS_INGRESS"`
- **Delegate:** `SavXTabBarServiceControllerDelegate`
- Manages the visual tab icon (`R.drawable.ic_bottom_tab_blue_heron`), custom background drawables (`SavXTabBackgroundDrawable`), and click selection routing.

---

### Why Morphe Patches May Fail on Amazon Shopping
1. **Dynamic Marketplace Provider:**  
   Notice line 119 in `SavXStableEligibilityManager`:
   `if (getFullyEnabledMarketplaceEligibilityProvider().isEligible()) return SavXEligibilityState.ELIGIBLE;`  
   Even if the `IMobileWeblab` experiments are forced to `"C"`, if `SavXFullyEnabledMarketplaceEligibilityProvider` determines the user's marketplace (e.g. US) has Rufus globally rolled out, `evaluateEligibility()` returns `ELIGIBLE`!
2. **WebLab Caching:**  
   WebLab treatments are often cached in local SharedPreferences or SQLite tables (`SavXEligibilityV1`). Unless the eligibility state evaluator itself is forced to `NOT_ELIGIBLE`, cached treatments override dynamic hooks.

---

### Universal Nullification Recipe for Amazon Rufus
1. **Bytecode Stub:**
   Rewrite `SavXStableEligibilityManager.evaluateEligibility()` to:
   ```smali
   sget-object v0, Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;->NOT_ELIGIBLE:Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;
   return-object v0
   ```
2. **Boolean Gate Stub:**
   Rewrite `SavXStableEligibilityManager.isEligible()` and `isEnabled()` to:
   ```smali
   const/4 v0, 0x0
   return v0
   ```
3. **Tab Ingress Neutralization:**
   In `BottomTabsBarSavXController`, stub `selectRufusTabItem` and tab population to no-op.
