package io.github.abdurazaaqmohammed.features.apk.flutter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.ZipCompat;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;

/**
 * Universal Flutter SSL Pinning bypass patcher.
 * Patches BoringSSL's session_verify_cert_chain in libflutter.so to always return 1 (verify success).
 */
public final class FlutterSslPatcher {

    // ARM64 Patch: "mov w0, #1; ret" (0x52800020, 0xD65F03C0)
    private static final byte[] PATCH_ARM64 = {
            (byte) 0x20, (byte) 0x00, (byte) 0x80, (byte) 0x52,
            (byte) 0xC0, (byte) 0x03, (byte) 0x5F, (byte) 0xD6
    };

    // ARMv7 Thumb Patch: "movs r0, #1; bx lr"
    private static final byte[] PATCH_ARM_THUMB = {
            (byte) 0x01, (byte) 0x20, (byte) 0x70, (byte) 0x47
    };

    // x86_64 Patch: "mov eax, 1; ret"
    private static final byte[] PATCH_X86_64 = {
            (byte) 0xB8, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xC3
    };

    // Pattern table for ARM64 session_verify_cert_chain
    private static final byte[][] PATTERNS_ARM64 = {
            // Pattern 1: standard prologue
            new byte[]{(byte) 0xFF, (byte) 0x83, (byte) 0x00, (byte) 0xD1, (byte) 0xF6, (byte) 0x57, (byte) 0x01, (byte) 0xA9, (byte) 0xF4, (byte) 0x4F, (byte) 0x02, (byte) 0xA9, (byte) 0xFD, (byte) 0x7B, (byte) 0x03, (byte) 0xA9},
            // Pattern 2: alternate stack size
            new byte[]{(byte) 0xFF, (byte) 0x43, (byte) 0x01, (byte) 0xD1, (byte) 0xF6, (byte) 0x57, (byte) 0x02, (byte) 0xA9, (byte) 0xF4, (byte) 0x4F, (byte) 0x03, (byte) 0xA9, (byte) 0xFD, (byte) 0x7B, (byte) 0x04, (byte) 0xA9},
            // Pattern 3: newer BoringSSL variant
            new byte[]{(byte) 0xFF, (byte) 0x03, (byte) 0x01, (byte) 0xD1, (byte) 0xF8, (byte) 0x5F, (byte) 0x01, (byte) 0xA9, (byte) 0xF6, (byte) 0x57, (byte) 0x02, (byte) 0xA9, (byte) 0xF4, (byte) 0x4F, (byte) 0x03, (byte) 0xA9},
            // Pattern 4: compact prologue
            new byte[]{(byte) 0xF4, (byte) 0x03, (byte) 0x00, (byte) 0xAA, (byte) 0xFD, (byte) 0x7B, (byte) 0x02, (byte) 0xA9, (byte) 0xFD, (byte) 0x03, (byte) 0x00, (byte) 0x91}
    };

    // Pattern table for ARMv7
    private static final byte[][] PATTERNS_ARM32 = {
            new byte[]{(byte) 0x2D, (byte) 0xE9, (byte) 0xF0, (byte) 0x4F, (byte) 0x85, (byte) 0xB0},
            new byte[]{(byte) 0x2D, (byte) 0xE9, (byte) 0xF8, (byte) 0x4F, (byte) 0x86, (byte) 0xB0}
    };

    public record PatchResult(boolean success, long offset, String architecture, String message) {}

    private FlutterSslPatcher() {
    }

    /**
     * Patches a standalone libflutter.so file. Creates a .bak backup before modifying.
     */
    public static PatchResult patchLibrary(File libFile) throws IOException {
        if (libFile == null || !libFile.isFile()) {
            return new PatchResult(false, -1, "Unknown", "File not found");
        }

        byte[] header = new byte[20];
        try (FileInputStream fis = new FileInputStream(libFile)) {
            if (fis.read(header) < 20) {
                return new PatchResult(false, -1, "Unknown", "File too small to be an ELF");
            }
        }
        if (header[0] != 0x7F || header[1] != 'E' || header[2] != 'L' || header[3] != 'F') {
            return new PatchResult(false, -1, "Unknown", "Not an ELF binary");
        }

        int machine = (header[18] & 0xFF) | ((header[19] & 0xFF) << 8);
        byte[][] targetPatterns;
        byte[] patchOpcodes;
        String archName;

        if (machine == 183) { // EM_AARCH64
            targetPatterns = PATTERNS_ARM64;
            patchOpcodes = PATCH_ARM64;
            archName = "arm64-v8a";
        } else if (machine == 40) { // EM_ARM
            targetPatterns = PATTERNS_ARM32;
            patchOpcodes = PATCH_ARM_THUMB;
            archName = "armeabi-v7a";
        } else if (machine == 62) { // EM_X86_64
            targetPatterns = PATTERNS_ARM64; // fallback scanner
            patchOpcodes = PATCH_X86_64;
            archName = "x86_64";
        } else {
            return new PatchResult(false, -1, "Machine: " + machine, "Unsupported architecture");
        }

        byte[] fileBytes = readFully(libFile);

        // Check if already patched
        for (long off : findSequence(fileBytes, patchOpcodes)) {
            return new PatchResult(true, off, archName, "Already patched at 0x" + Long.toHexString(off));
        }

        long patchOffset = -1;
        for (byte[] pattern : targetPatterns) {
            List<Long> matches = findSequence(fileBytes, pattern);
            if (!matches.isEmpty()) {
                patchOffset = matches.get(0);
                break;
            }
        }

        if (patchOffset == -1) {
            return new PatchResult(false, -1, archName, "Verification signature not found in binary");
        }

        // Backup
        File bak = new File(libFile.getParentFile(), libFile.getName() + ".bak");
        if (!bak.exists()) {
            FileUtils.copyFile(libFile, bak);
        }

        try (RandomAccessFile raf = new RandomAccessFile(libFile, "rw")) {
            raf.seek(patchOffset);
            raf.write(patchOpcodes);
        }

        return new PatchResult(true, patchOffset, archName, "Successfully patched at 0x" + Long.toHexString(patchOffset));
    }

    /**
     * Patches all libflutter.so instances inside an APK and writes out the patched APK.
     */
    public static int patchApk(File apkFile, File outputApk, ProgressManager pm) throws Exception {
        if (apkFile == null || !apkFile.isFile()) throw new IOException("Invalid APK file");
        if (outputApk == null) outputApk = new File(apkFile.getParentFile(), apkFile.getName().replace(".apk", "_unpinned.apk"));

        if (!outputApk.equals(apkFile)) {
            FileUtils.copyFile(apkFile, outputApk);
        }

        int patchedCount = 0;
        File tempDir = new File(outputApk.getParentFile(), ".flutter_patch_tmp_" + System.currentTimeMillis());
        tempDir.mkdirs();

        try (ZipFile zf = new ZipFile(outputApk)) {
            List<FileHeader> headers = zf.getFileHeaders();
            List<String> toPatch = new ArrayList<>();
            for (FileHeader fh : headers) {
                if (fh.getFileName().endsWith(FlutterDetector.LIB_FLUTTER)) {
                    toPatch.add(fh.getFileName());
                }
            }

            for (String entryPath : toPatch) {
                if (pm != null) pm.setText("Patching " + entryPath + "…");
                File extractedLib = new File(tempDir, new File(entryPath).getName());
                zf.extractFile(entryPath, tempDir.getAbsolutePath(), extractedLib.getName());

                PatchResult res = patchLibrary(extractedLib);
                if (res.success) {
                    ZipParameters params = new ZipParameters();
                    String parentPath = new File(entryPath).getParent();
                    if (parentPath != null) params.setRootFolderNameInZip(parentPath);
                    zf.addFile(extractedLib, params);
                    patchedCount++;
                }
                extractedLib.delete();
            }
        } finally {
            tempDir.delete();
        }

        return patchedCount;
    }

    private static List<Long> findSequence(byte[] data, byte[] seq) {
        List<Long> results = new ArrayList<>();
        if (data == null || seq == null || data.length < seq.length) return results;

        int limit = data.length - seq.length;
        for (int i = 0; i <= limit; i++) {
            boolean match = true;
            for (int j = 0; j < seq.length; j++) {
                if (data[i + j] != seq[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                results.add((long) i);
                i += seq.length - 1;
            }
        }
        return results;
    }

    private static byte[] readFully(File file) throws IOException {
        long len = file.length();
        if (len > 50L * 1024 * 1024) throw new IOException("Library file too large");
        byte[] bytes = new byte[(int) len];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read = 0;
            while (read < bytes.length) {
                int n = fis.read(bytes, read, bytes.length - read);
                if (n < 0) break;
                read += n;
            }
        }
        return bytes;
    }
}
