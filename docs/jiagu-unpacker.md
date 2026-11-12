# Jiagu Dumper & Jiagu Unpacker (360加固 unpacking)

Two features for unpacking APKs protected with 360 "Jiagu" (`com.stub.StubApp`):

## Jiagu Dumper (Tools Hub → Jiagu Dumper, root required)

Reads the unpacked DEX files out of a running protected app's memory:

1. Launches the target package (`monkey -p <pkg> 1`) and waits for the stub to
   decrypt the real dexes (configurable wait, default 15s).
2. Reads `/proc/<pid>/maps` through `su` and scans every readable
   `dalvik`/`dex`/anonymous region (up to 2 GB each) in 4 MB chunks for dex
   headers (`dex\n03X\0`, header size 112).
3. Carves each valid dex with `dd if=/proc/<pid>/mem skip=… count=…`. When the
   memory read fails on file-backed pages (vdex), it falls back to reading the
   backing file directly.
4. Saves `dump_NNN.dex` + `report.txt` to
   `Android/data/<this app>/files/jiagu_dump/<package>/<timestamp>/`.

The "Pick from installed apps" button lists third-party packages with their
labels. An optional APK path rebuilds the unpacked APK right after the dump.

The same engine is exposed over MCP as `mp_apk_jiagu_dump`.

## Jiagu Unpacker (APK dialog → More → Jiagu Unpacker, offline)

Rebuilds an installable APK from the original file + a dump folder
(auto-discovers the newest dump of the APK's package):

1. Validates and de-duplicates the dumped dexes (dexlib2 parse).
2. NOP-patches the injected `Lcom/stub/StubApp;->…` hook invocations in the
   binary dex model — every removed invoke is replaced with the same number of
   1-unit NOPs so try/catch and debug addresses stay valid; the `move-result`
   consuming `getOrigApplicationContext()` is NOPed as well (identity: without
   the packer the context is already the real one).
3. Locates the real Application class by walking superclass chains across the
   dumped dexes (concrete, non-stub, topmost) and patches the manifest
   `application android:name` via the app's own axml decoder/encoder.
4. Rebuilds the zip: drops the old dexes, `assets/libjiagu*`, signature files;
   adds the rewritten dexes; aligns (`ApkZipAlignUtil`); signs with the
   configured key.

Exposed over MCP as `mp_apk_jiagu_unpack`.
