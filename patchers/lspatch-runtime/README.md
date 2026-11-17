# LSPatch manager runtime

This library is a Full-only dependency of MP Manager. Its AIDL interfaces and
shared module/remote-service Java files are byte-for-byte copies of JingMatrix
LSPatch and its Vector core. They are the wire protocol consumed by the existing
loader assets, not another copy of the Xposed framework.

Refresh them from a separately obtained recursive checkout:

```powershell
python tools/sync-lspatch-runtime.py
python tools/sync-lspatch-runtime.py --check
```

`upstream-files.json` records SHA-256 for every file. Never edit generated AIDL
Java or change transaction IDs locally. Update the loader assets and this
protocol together with `tools/build-lspatch-artifacts.sh`.

MP Manager-specific UID checks, module scope persistence and service wiring live
under `app/src/full/java/.../feature/apk/xposed/`; they are not changes to the
upstream sources. See `docs/XPOSED_PATCHER.md`.
