# Editor and Partition Dumper regressions

## Changes

- Minimized editors are identified by Android task ID, not activity class. Each editor launch gets a separate task. The activity-result bridge keeps both legacy result callers and `ActivityResultRegistry` callbacks working while the process is alive. Editor tasks can also appear separately in Android Recents.
- DEX trees, tabs, modification flags, and method-list state belong to the owning activity. TextMate grammars are shared, but mutable language analyzers are not.
- ARSC text-page handoffs have unique session keys instead of a single global payload.
- Text files above 10 MiB show a warning with **Open as text anyway**, Hex editor (when a file path is available), and Cancel. Files above 2 MiB start read-only. Syntax highlighting and wrapping are disabled above 512 KiB or for very long lines; editing can be explicitly enabled.
- Text loading uses Sora's streaming `ContentIO` reader on a worker thread and reuses the resulting `Content`. It preserves CRLF, CR, LF, and the absence of a final newline. Tab changes/minimization do not stringify the document. Session serialization is queued off-thread. Large plain-text saves use shallow copy-on-write snapshots and streaming UTF-8 output.
- Hex files above 32 MiB start read-only, with an explicit Enable editing action. Original-file reads use a bounded 512 KiB page cache. Search uses buffered KMP in either direction and checks cancellation. Selection updates only visible rows. Row windows use long offsets and let scrolling/search/Go to offset reach files beyond RecyclerView's integer range. Structural edits splice array slices, and conversion from sparse byte edits is linear rather than repeated chunk splitting.
- Hex saves run off-thread. Structural saves never fall back to truncating the original when an atomic replacement fails. Failed root write-back retains a pending save and rebases the staged document.
- Partition Dumper uses the app's Material theme, recycled rows, standard dialogs and file picker, and translatable resources. No emojis, fixed dark palette, or Java-side Arabic/English language selection remain. English and Arabic resources have matching format placeholders.
- Backups use non-overwriting filenames, check free space, reject incomplete reads/nonzero root exit codes, and clean up incomplete output. Flashing retains slot selection, sparse expansion, size/type checks, acknowledgment and final confirmation. Root stderr is drained to avoid deadlocks. Sparse headers use the correct field offsets, with complete chunk preflight before writing.

## Automated checks

Verified on 2026-11-09: Full and Lite debug APK builds succeeded; all 10 targeted JVM tests and all 9 Android 12 emulator tests passed. The 12 MiB text fixture loaded in 1.46 seconds with approximately 25 MiB of Java heap in the test process. This is an emulator measurement, not a performance guarantee for other files or devices. The English/Arabic resource audit passed for all 70 new strings, including placeholder parity and absence of partition emojis.

Unit tests:

```powershell
.\gradlew.bat :app:testLiteDebugUnitTest --tests io.github.abdurazaaqmohammed.utils.EditorIoTest --tests io.github.abdurazaaqmohammed.utils.SparseImageRegressionTest
```

Real Android emulator tests (isolated package; does not replace an installed release):

```powershell
.\gradlew.bat :app:connectedLiteDebugAndroidTest -PeditorRegressionBuild -Pandroid.testInstrumentationRunnerArguments.class=io.github.abdurazaaqmohammed.utils.EditorRegressionTest
```

The instrumentation suite covers restoring two hex/text/DEX activities in different orders, independent document state and undo, activity-result callbacks, Sora line endings and snapshots, large-file read-only behavior, the working Open anyway button, structural hex edits, and light-theme/Arabic partition browsing using synthetic partition metadata. It never flashes a device partition.

## Remaining device checks

Use a disposable rooted device or emulator for actual partition discovery and backup. Compare a dumped noncritical partition's size/checksum with a direct read. Check cancellation, denied root access and insufficient space. Do not test flashing on a personal device just to verify the UI. Hardware flashing, device-specific root implementations, and real partition compatibility cannot be established by synthetic UI tests.

Large text still needs memory proportional to its decoded content. Open anyway is not a guarantee that a multi-gigabyte file can fit in the Android process heap; use Hex for such files.
