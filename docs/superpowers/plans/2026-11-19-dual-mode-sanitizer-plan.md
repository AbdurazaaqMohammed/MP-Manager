# Implementation Plan: Dual-Mode Autonomous App Sanitizer & Xfinity Validation

**Date**: November 19, 2026  
**Spec**: [docs/superpowers/specs/2026-11-19-dual-mode-sanitizer-architecture-design.md](file:///home/deck/MP-Manager/docs/superpowers/specs/2026-11-19-dual-mode-sanitizer-architecture-design.md)

---

## Plan Overview
Implement the two-tier sanitization architecture (Standard Mode vs. Aggressive Mode) in MP-Manager's privacy engine, evaluate both modes empirically on Xfinity Digital Home (`stock.apk`, 308 MB, 21 DEXes), test on the physical Pixel 10 Pro XL, and synthesize cross-app invariants into MP-Manager's autonomous antivirus component.

---

## Tasks

- [ ] **Task 8: Dual-Mode Configuration & UI Metadata (`SanitizationMode`)**
  - Step 1: Write failing unit test `SanitizationModeTest.java` in `app/src/test/java/io/github/abdurazaaqmohammed/feature/apk/privacy/`.
  - Step 2: Verify test fails.
  - Step 3: Implement `SanitizationMode` enum and add mode presets (`standard()`, `aggressive()`) and flags to `PrivacyOptions.java`.
  - Step 4: Verify test passes.
  - Step 5: Independent subagent review & git commit.

- [ ] **Task 9: Aggressive Hardware Fingerprinting & Callsite Rewriter**
  - Step 1: Write failing unit test `AggressiveHardwareNullificationTest.java` testing Dalvik callsite rewriting of `Settings.Secure.getString(..., "android_id")`, `Build.getSerial()`, `WifiInfo.getMacAddress()`.
  - Step 2: Verify test fails.
  - Step 3: Implement callsite rewriting in `PrivacyBytecodeEngine.java`.
  - Step 4: Verify test passes.
  - Step 5: Independent subagent review & git commit.

- [ ] **Task 10: Aggressive WorkManager Enqueue Suppression & Tracker `<clinit>` Sanitization**
  - Step 1: Write failing unit test `AggressiveWorkManagerAndClinitTest.java` testing periodic worker enqueue suppression and `<clinit>` clearing.
  - Step 2: Verify test fails.
  - Step 3: Implement enqueue suppression and `<clinit>` neutralization in `PrivacyBytecodeEngine.java`.
  - Step 4: Verify test passes.
  - Step 5: Independent subagent review & git commit.

- [ ] **Task 11: Empirical Multi-DEX Sanitization of Xfinity (`stock.apk`)**
  - Step 1: Execute engine in Standard Mode on `xfinity_audit/stock.apk` (308 MB, 21 DEXes), logging audit metrics and skipped dead code.
  - Step 2: Execute engine in Aggressive Mode on `xfinity_audit/stock.apk`, logging comparative delta.
  - Step 3: Decompile mutated classes with JADX to verify bytecode correctness and absence of descriptor/string collision regressions.
  - Step 4: Document findings in `docs/research/06_xfinity_sterilization_and_cross_app_patterns.md`.
  - Step 5: Independent subagent review & git commit.

- [ ] **Task 12: Physical On-Device Verification on Pixel 10 Pro XL**
  - Step 1: Mount/install sanitized Xfinity APK on device.
  - Step 2: Execute `cmd package compile -m verify -f com.xfinity.digitalhome` to synchronize ART OAT cache.
  - Step 3: Launch app, monitor logcat, and verify stability and elimination of Comcast Assistant / telemetry.
  - Step 4: Capture UI dump and screenshot as proof.

- [ ] **Task 13: MP-Manager UI & Antivirus Integration + Final Deployment**
  - Step 1: Expose Dual-Mode selection (Standard vs Aggressive) in MP-Manager UI with user disclaimers.
  - Step 2: Build `app-full-debug.apk`, sign with `release.keystore`, and install on Pixel 10 Pro XL.
  - Step 3: Run full test suite and final whole-branch review.
