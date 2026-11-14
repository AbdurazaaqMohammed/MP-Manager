# Architecture Plan

> **Status: PROPOSAL — pending maintainer approval.**
>
> This document describes where the codebase is heading and how we get
> there. Nothing here changes runtime behavior: every transition stage is
> pure code motion (`git mv` + import fixes), verified by compile + smoke
> test, until a stage is explicitly marked as adding a capability.
> If approved, stages are executed one PR at a time — each independently
> revertible — using this document as the map. Any part can be approved or
> rejected independently (e.g. "do Problem 1, skip Problem 2").

## Contents

- [Problem 1 — the privilege layer cannot absorb new backends](#problem-1--the-privilege-layer-cannot-absorb-new-backends)
- [Problem 2 — flat package layout, no dependency direction](#problem-2--flat-package-layout-no-dependency-direction)
- [Execution checklist](#execution-checklist)

---

## Problem 1 — the privilege layer cannot absorb new backends

### Current state

Adding Dhizuku (or ADB-over-WiFi, or anything else) today means
copy-pasting a third Shizuku-style wrapper and hand-editing 30+ call
sites. Root cause: no backend abstraction — every privilege channel is a
pile of point-to-point code:

- **3 coexisting Shizuku wrappers**:
  - `rikka/shizuku/ShizukuProcessFactory` (20-line helper to reach a
    package-private class),
  - `shizuku/` — `ShizukuConnection` + a 789-line `ShizukuFileService` + aidl,
  - `MPManager/shizuku/` — a 413-line `ShizukuShell` + `ShizukuFile` +
    `ShizukuFileOps`;
  all three are imported directly by 15+ UI/business files;
- **7 root classes** (incl. `RootManager` at 2,607 lines) mixed into the
  generic `utils/` package;
- `PrivilegedInstaller` grows a new pair of `isXxxSilentReady()` methods
  per backend;
- `WorkingMode` (`NON_ROOT | ROOT | SHIZUKU`) has no slot for Dhizuku and
  lives inside an implementation class.

### Design — capability interfaces + registry (open/closed)

> **Adding a privilege backend = 1 new `XxxBackend.java` + 1 line in the
> registry. Nothing else changes.**

```
privilege/
├── api/                    ← the ONLY layer visible to callers
│   ├── PrivilegeManager.java   # single entry point
│   ├── ShellSession.java       # exec / stream / batch(progress)
│   ├── FileOpsSession.java     # read/write/list/copy/delete/chmod
│   └── InstallerSession.java   # install/uninstall/backup-data
├── spi/                    ← what a backend author implements
│   ├── PrivilegeBackend.java   # id/label/priority + isAvailable()/isAuthorized()
│   └── Capability.java         # marker; backends implement only what they support:
│       ├── ShellCap.java       #     ShellSession openShell()
│       ├── FileOpsCap.java     #     FileOpsSession openFileOps()
│       └── InstallerCap.java   #     InstallerSession openInstaller()
├── backend/                ← each backend is one file; backends never see each other
│   ├── RootBackend.java        # merged from RootManager + ShellProcess + RootFile
│   ├── ShizukuBackend.java     # merged from the 3 existing wrappers
│   └── DhizukuBackend.java     # depends only on com.rosan.dhizuku:api (official lib)
└── internal/
    ├── Registry.java           # the ONLY file that grows per backend
    ├── FallbackBackend.java    # NONE backend → callers never null-check
    └── ipc/                    # Shizuku/Dhizuku user-service connections + aidl
```

Rules:

1. UI/features may import only `PrivilegeManager` and the session
   interfaces — never a backend, never `internal/`;
2. backends implement capabilities à la carte — no
   `throw new UnsupportedOperationException` stubs, no paired
   `isXxxReady()` methods; the registry skips a backend that lacks the
   requested capability and falls through to the next one;
3. `getShell()` and friends always return non-null; when nothing is
   authorized they return the `FallbackBackend`'s explicit-error
   implementation, so callers carry zero if-else.

Backend selection order (proposal, maintainer's call):
`DHIZUKU > SHIZUKU > ROOT > NONE` — persistent delegation preferred over
interactive authorization; the user may pin a mode manually in settings.

### Old vs. new — cost of adding Dhizuku

| | old structure | new structure |
|---|---|---|
| new files | 3+ (shell / fileops / connection) | 1 |
| modified files | 30+ (enum, installer, every call site) | 1 line in `Registry` |
| next backend | repeat everything | repeat the 1-file pattern |

### Transition (each PR independent, no behavior change)

| stage | action | behavior change |
|---|---|---|
| M1 | create `privilege/`; move the 13 utils privilege classes + the 3 Shizuku wrappers into `privilege/legacy/` as-is (package/import fixes only) | none |
| M2 | wrap legacy with `PrivilegeManager` + session interfaces; re-point the 15+ import sites | none (facade) |
| M3 | re-implement Root, then Shizuku, as backends behind the facade; one backend per PR | none |
| M4 | add `DhizukuBackend` + registry line — first real exercise of the plugin design | **new capability** |
| M5 | delete `legacy/`, drop the `WorkingMode` compatibility shim | none |

---

## Problem 2 — flat package layout, no dependency direction

### Current state

Everything is one module with:

- `utils/` (119 files) as a junk drawer — color helpers next to
  `RootManager`, signing, `AdbClient`, …;
- UI shattered across 5 packages: `adapters/` (23), `ui/` (20), UI bits of
  `core/`, `player/` (13), `drawable/` (5);
- domains shattered across 6 packages: `features/` (84), `tools/` (21),
  `arsc/`, `ApkExtractor/`, `boot/`, `commandhelper/`;
- an uppercase `MPManager` package (13 FTP classes) and features importing
  UI dialogs directly.

### Target — 7 layers, a single direction of dependencies

```
io.github.abdurazaaqmohammed/
├── app/        # shell: Application, ServiceLocator, startup, UpdateController
├── ui/         # everything visible (Activity/Fragment/Adapter/Dialog/custom views)
│   ├── main/       ← adapters/ + ui/ + UI bits of core/ + listeners/
│   ├── viewer/     ← player/ + drawable/       (pure display, no logic)
│   ├── editor/     # text / dex / arsc editor screens
│   └── common/     ← core/ (BaseDialog, themes, fonts, popup menus)
├── feature/    # business domains, one subpackage each, exposing only XxxController
│   ├── files/      # existing — keep
│   ├── apk/        ← ApkExtractor/ + arsc/ + dex/signature logic
│   ├── system/     ← tools/ — ToolRegistry is a good pattern; generalize it
│   ├── boot/       ← boot/
│   ├── ftp/        ← MPManager/, split: profiles/logic here, UI to ui/
│   ├── freestyle/  ← freestyle/
│   └── text/       ← commandhelper/
├── plugin/     # ← plugins/: host/registry/trust, pairs with the packs/* SDK
├── mcp/        # ← mcp/: self-contained, stays top-level (crosses domains)
├── privilege/  # ← the layer from Problem 1
├── data/       # persistence: SharedPrefs / caches / stores ← data/ + scattered prefs
├── domain/     # pure models & pure logic ← domain/; depends on nothing
└── util/       # pure helpers by topic: util/{io,archive,hash,text,net}
                # ← utils/ minus privilege/business classes; no new business allowed
```

Dependency rules (as drawn: a DAG — `ui → feature → domain`;
`data / privilege / plugin / mcp / util` are leaf services usable by
anyone, dependent on nothing business-related):

1. `domain` and `util` may not import any other first-party package;
2. `feature` may not import `ui` — needs a dialog? define a callback,
   inject it from ui;
3. `privilege` exposes only `PrivilegeManager` (Problem 1);
4. every top-level package carries a `package-info.java` stating its
   contract; a CI import-lint check can be added later — until then this
   is a review rule.

Gradle modules (`packs/*`, `sdk`, …) stay as they are for now; extracting
`privilege` / `plugin` / `mcp` into library modules is a later, optional
stage (B below) that turns the review rules into compile-time enforcement.

### Transition (pure code motion, lowest risk first)

| stage | action | scope |
|---|---|---|
| A0 | add `docs/ARCHITECTURE.md`: the diagram, per-package move mapping, dependency rules — the map for every later PR | doc only |
| A1 | merge the 5 UI fragments into `ui/{main,viewer,editor,common}` | ~65 files |
| A2 | domains absorb `tools/` `arsc/` `ApkExtractor/` `boot/` `MPManager/` `freestyle/` `commandhelper/` | ~50 files |
| A3 | split `utils/`: 13 privilege classes → `privilege/` (runs in parallel with M1–M3); rest → `util/{io,archive,hash,text,net}`; unclassifiable remainder → `util/misc` with a no-growth rule | ~119 files, multiple PRs ≤ 30 files each |
| A4 | scattered SharedPrefs → `data/`; one-shot rename of the uppercase `MPManager` package (last, to break the least) | ~20 files |
| B | (optional, later) extract `privilege` / `plugin` / `mcp` as Gradle library modules — compile-time enforcement | 3 modules |
| C | land `DhizukuBackend` — by now the structure itself dictates where it goes and what it may depend on | 1 file |

**Sequencing rationale:** A1–A4 and M1–M3 are mechanical moves a reviewer
can verify with a build; anything that changes logic (Dhizuku, backend
extraction) lands only after the structure settles, so a regression is
always one revert away.

---

## Execution checklist

Check off as stages land (one PR per stage, linked from here):

- [ ] **A0** — `docs/ARCHITECTURE.md` (target structure + move mapping)
- [ ] **A1** — UI five-into-one
- [ ] **A2** — domain packages absorbed into `feature/`
- [ ] **A3** — `utils/` split (privilege classes → `privilege/`)
- [ ] **A4** — `data/` consolidation + `MPManager` package rename
- [ ] **M1** — `privilege/legacy/` assembly move
- [ ] **M2** — facade (`PrivilegeManager` + sessions) over legacy
- [ ] **M3** — Root backend, then Shizuku backend, behind the facade
- [ ] **M4** — `DhizukuBackend` (new capability)
- [ ] **M5** — delete `legacy/` + `WorkingMode` shim
- [ ] **B**  — (optional) Gradle library module extraction
- [ ] **C**  — (redundant with M4; kept as the independent checklist entry)