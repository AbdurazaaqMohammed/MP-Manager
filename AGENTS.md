# AGENTS.md — MP-Manager Contributor Guide

## Before Starting Any Work (Required)

1. **Read the `docs/` folder first**, then touch code:
   - `docs/ARCHITECTURE_PLAN.md` — staged plan (status of each stage, open items)
   - `docs/ARCHITECTURE.md` — current architecture facts and known deviations
2. **Check there whether a capability is actually wired before building on it** — code existing ≠ feature reachable; the docs record which registry paths and backends are live and which are staged but not yet connected. Do not copy patterns from code that docs mark as unwired.

## Coding Rules

- **Reuse existing interfaces first**: privilege-related features go through `PrivilegeManager` mirrors and the session APIs (`FileOpsSession`/`ShellSession`/`InstallerSession`); do not import implementation classes from `privilege/internal` directly. When a new interface is genuinely needed, extend the existing SPI (`Capability`/`ShellCap`/`FileOpsCap`/`InstallerCap`) instead of creating a parallel abstraction
- **Every new user-facing string must be added to resources**: add the English text to `values/strings.xml` and the Simplified Chinese translation to `values-zh-rCN/strings.xml`. Never hardcode strings in layouts or code. Other locales are handled by the upstream translation flow — do not machine-translate all languages
- **Minimize new code**: a new backend = one backend file + one `Registry.register` line (see `DhizukuBackend`); no duplicate layers of the same responsibility
- **Keep it simple (KISS)**: change one line instead of ten when possible; reuse helpers in `util/` instead of writing new ones; no unrequested config options, abstraction layers, or speculative defensiveness
- **Document with the change**: stage/architecture changes must update both `ARCHITECTURE_PLAN.md` (check off the stage) and `ARCHITECTURE.md` (state the facts). A change without doc updates is not done
- **Preserve history on moves**: always use `git mv` when moving or renaming files. After moving a package, grep the whole repo for stale references — AIDL package names must stay in sync with the Java package

## Build and Verify

- Standard build: `./gradlew assembleDebug`; run unit tests with `./gradlew :app:testFullDebugUnitTest`
- After touching the privilege layer, confirm no stale `privilege.impl` references remain (the package no longer exists)
- Do not commit local build-environment tweaks (repository mirrors, machine-specific paths, signing config)

## Git

- Open pull requests against `main` of `AbdurazaaqMohammed/MP-Manager`; do your work in your own branch
- Commit messages: imperative English, one-line summary with a body when the change needs context
