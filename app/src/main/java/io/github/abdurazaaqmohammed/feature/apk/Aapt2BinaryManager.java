package io.github.abdurazaaqmohammed.feature.apk;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

import io.github.abdurazaaqmohammed.privilege.internal.NativeToolManager;
import io.github.abdurazaaqmohammed.util.archive.Aapt2Jni;

/**
 * Downloads the Android AAPT2 binary on first use instead of shipping it in
 * every APK.
 *
 * <p>The official termux-aapt release is downloaded once, the binary for the
 * device ABI is extracted and verified, and its {@code DF_1_PIE} flag is
 * cleared so {@link Aapt2Jni} can {@code dlopen()} it. The patched file is
 * verified against a hard-coded SHA-256 before every run.</p>
 */
public final class Aapt2BinaryManager {
    public static final String DEFAULT_URL =
            "https://github.com/rendiix/termux-aapt/releases/download/termux-sdk-tools-34.0.0/"
                    + "termux-sdk-tools-34.0.0.tar.xz";
    private static final String BINARY_NAME = "aapt2";
    private static final long MAX_ARCHIVE = 32L * 1024 * 1024;
    private static final long MAX_BINARY = 16L * 1024 * 1024;
    private static final long DF_1_PIE = 0x08000000L;

    public interface Progress {
        void onProgress(long done, long total);
    }

    private static final class Abi {
        final String abi;
        final String archiveDir;
        final String rawSha256;
        final String patchedSha256;
        final long rawSize;
        final long mainOffset;

        Abi(String abi, String archiveDir, String rawSha256, String patchedSha256,
            long rawSize, long mainOffset) {
            this.abi = abi;
            this.archiveDir = archiveDir;
            this.rawSha256 = rawSha256;
            this.patchedSha256 = patchedSha256;
            this.rawSize = rawSize;
            this.mainOffset = mainOffset;
        }

        String entryPath() {
            return "prebuilt-binary/" + archiveDir + "/" + BINARY_NAME;
        }
    }

    // Pulled from termux-sdk-tools-34.0.0 and verified once at download time.
    // mainOffset is the address of aapt2's main entry (or its thunk) in the
    // linked (unrebased) PIE, found from the __libc_init argument.
    private static final Abi[] ABIS = {
            new Abi("arm64-v8a", "arm64",
                    "4f0339832f517c1e59a8a75b0a0c594ff6af0361b89e493bb79b224801077465",
                    "c12225cc71ee99632f3953029dd377a79686bdbbaf049ef050cd24ba318a475a",
                    3498816L, 0xEF5FCL),
            new Abi("armeabi-v7a", "arm",
                    "7315ad6fc23817b9c72595db3299410ab0f623a5e7fb68675b9344d858cdeb1a",
                    "aabd84b8cb2f04cb1571d16e6f08762ca275cba8ed39f5640f29f08c2bddb2e5",
                    2231168L, 0x72DB9L),
            new Abi("x86", "i686",
                    "709f5e2249617b1c8effe911ecabe2dccb6538aac8a587a2aeb14b9c9508103c",
                    "b0b043d7879bc544b0538e3ce79eca2ca6d691d5d284c75242ee2725bff579d3",
                    3901004L, 0x12FCF5L),
            new Abi("x86_64", "x86-64",
                    "292125b20a50e438b0cb4c2894f2a2b9230c8ee633c8ecfb6f0e209b5dca1d46",
                    "71e1547f018509a39cb1003276e4f8573b95e309344ddd43ce7d0a4fac666b2a",
                    3528528L, 0xF8305L),
    };

    private Aapt2BinaryManager() {
    }

    private static Abi currentAbi() {
        String abi = NativeToolManager.deviceAbi();
        for (Abi candidate : ABIS) {
            if (candidate.abi.equals(abi)) return candidate;
        }
        return null;
    }

    private static String baseUrl(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String custom = prefs.getString("aapt2_url", "").trim();
            if (!custom.isEmpty()) return custom;
        } catch (Exception ignored) {
        }
        return DEFAULT_URL;
    }

    private static File cacheFile(Context context, Abi abi) {
        return new File(new File(context.getNoBackupFilesDir(), "aapt2/" + abi.abi), BINARY_NAME);
    }

    public static boolean isReady(Context context) {
        return Aapt2Jni.isAvailable() && isDownloaded(context);
    }

    public static boolean isDownloaded(Context context) {
        Abi abi = currentAbi();
        return abi != null && matches(cacheFile(context, abi), abi.patchedSha256);
    }

    /** Returns the verified, executable AAPT2 file, or null when not installed. */
    public static File binary(Context context) {
        Abi abi = currentAbi();
        if (abi == null) return null;
        File file = cacheFile(context, abi);
        return matches(file, abi.patchedSha256) ? file : null;
    }

    public static long mainOffset(Context context) {
        Abi abi = currentAbi();
        return abi == null ? -1 : abi.mainOffset;
    }

    public static String approximateSizeMb() {
        return "~5 MB";
    }

    public static synchronized void download(Context context, Progress progress) throws Exception {
        Abi abi = currentAbi();
        if (abi == null) throw new IOException("No AAPT2 build for this device architecture");
        if (!Aapt2Jni.isAvailable()) throw new IOException("AAPT2 runner is unavailable in this build");

        File target = cacheFile(context, abi);
        if (matches(target, abi.patchedSha256)) return;
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create AAPT2 storage directory");
        }

        File archive = File.createTempFile("aapt2-", ".tar.xz", context.getCacheDir());
        File extracted = File.createTempFile("aapt2-bin-", ".part", context.getCacheDir());
        try {
            downloadFile(baseUrl(context), archive, progress);

            extract(archive, abi.entryPath(), extracted);
            if (extracted.length() != abi.rawSize) {
                throw new IOException("Unexpected AAPT2 size (" + extracted.length() + " bytes)");
            }
            if (!abi.rawSha256.equalsIgnoreCase(sha256(extracted))) {
                throw new IOException("Downloaded AAPT2 failed checksum verification");
            }

            clearPieFlag(extracted);
            if (!abi.patchedSha256.equalsIgnoreCase(sha256(extracted))) {
                throw new IOException("Could not prepare AAPT2 for this device");
            }
            extracted.setReadable(true, true);
            extracted.setExecutable(true, true);

            if (target.exists() && !target.delete()) {
                throw new IOException("Cannot replace the existing AAPT2 binary");
            }
            if (!extracted.renameTo(target)) {
                throw new IOException("Cannot install the AAPT2 binary");
            }
        } finally {
            if (archive.exists()) archive.delete();
            if (extracted.exists()) extracted.delete();
        }
    }

    private static void downloadFile(String url, File destination, Progress progress) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        long total = connection.getContentLengthLong();
        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[65536];
            long done = 0;
            int count;
            long lastReport = 0;
            while ((count = in.read(buffer)) != -1) {
                done += count;
                if (done > MAX_ARCHIVE) throw new IOException("Downloaded archive is too large");
                out.write(buffer, 0, count);
                if (progress != null && done - lastReport >= 256 * 1024) {
                    lastReport = done;
                    progress.onProgress(done, total);
                }
            }
            if (progress != null) progress.onProgress(done, total);
        } finally {
            connection.disconnect();
        }
    }

    private static void extract(File archive, String wantedEntry, File destination) throws IOException {
        try (InputStream raw = new BufferedInputStream(new FileInputStream(archive));
             XZInputStream xz = new XZInputStream(raw);
             TarArchiveInputStream tar = new TarArchiveInputStream(xz)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isDirectory() || !wantedEntry.equals(entry.getName())) continue;
                if (entry.getSize() > MAX_BINARY) throw new IOException("AAPT2 entry is too large");
                try (OutputStream out = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[65536];
                    long total = 0;
                    int count;
                    while ((count = tar.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_BINARY) throw new IOException("AAPT2 entry is too large");
                        out.write(buffer, 0, count);
                    }
                }
                return;
            }
        }
        throw new IOException("AAPT2 is missing from the downloaded archive");
    }

    /**
     * Clears DF_1_PIE so Android's dynamic linker allows dlopen() on the PIE
     * executable. Everything else in the ELF stays untouched.
     */
    static void clearPieFlag(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            byte[] ident = new byte[16];
            raf.readFully(ident);
            if (ident[0] != 0x7f || ident[1] != 'E' || ident[2] != 'L' || ident[3] != 'F') {
                throw new IOException("Not an ELF file");
            }
            boolean is64 = ident[4] == 2;
            boolean littleEndian = ident[5] == 1;

            long phoff = readInt(raf, is64 ? 0x20 : 0x1c, is64, littleEndian) & 0xffffffffL;
            int phentsize = readShort(raf, is64 ? 0x36 : 0x2a, littleEndian);
            int phnum = readShort(raf, is64 ? 0x38 : 0x2c, littleEndian);

            long dynamicOffset = -1;
            long dynamicSize = 0;
            for (int i = 0; i < phnum; i++) {
                long ph = phoff + (long) i * phentsize;
                long type = readInt(raf, ph, false, littleEndian);
                if (type != 2) continue; // PT_DYNAMIC
                dynamicOffset = is64
                        ? readLong(raf, ph + 8, littleEndian)
                        : readInt(raf, ph + 4, false, littleEndian) & 0xffffffffL;
                dynamicSize = is64
                        ? readLong(raf, ph + 32, littleEndian)
                        : readInt(raf, ph + 16, false, littleEndian) & 0xffffffffL;
                break;
            }
            if (dynamicOffset < 0) throw new IOException("ELF has no dynamic section");

            int entrySize = is64 ? 16 : 8;
            for (long pos = dynamicOffset; pos + entrySize <= dynamicOffset + dynamicSize; pos += entrySize) {
                long tag = is64
                        ? readLong(raf, pos, littleEndian)
                        : readInt(raf, pos, false, littleEndian) & 0xffffffffL;
                if (tag != 0x6ffffffbL) continue; // DT_FLAGS_1
                long value = is64
                        ? readLong(raf, pos + 8, littleEndian)
                        : readInt(raf, pos + 4, false, littleEndian) & 0xffffffffL;
                if ((value & DF_1_PIE) == 0) return;
                long updated = value & ~DF_1_PIE;
                if (is64) {
                    writeLong(raf, pos + 8, updated, littleEndian);
                } else {
                    writeInt(raf, pos + 4, (int) updated, littleEndian);
                }
                return;
            }
            throw new IOException("ELF has no DT_FLAGS_1 entry");
        }
    }

    private static long readLong(RandomAccessFile raf, long offset, boolean littleEndian) throws IOException {
        raf.seek(offset);
        long value = raf.readLong();
        return littleEndian ? Long.reverseBytes(value) : value;
    }

    private static void writeLong(RandomAccessFile raf, long offset, long value,
                                  boolean littleEndian) throws IOException {
        raf.seek(offset);
        raf.writeLong(littleEndian ? Long.reverseBytes(value) : value);
    }

    private static long readInt(RandomAccessFile raf, long offset, boolean is64,
                                boolean littleEndian) throws IOException {
        if (is64) return readLong(raf, offset, littleEndian);
        raf.seek(offset);
        int value = raf.readInt();
        return (littleEndian ? Integer.reverseBytes(value) : value) & 0xffffffffL;
    }

    private static void writeInt(RandomAccessFile raf, long offset, int value,
                                 boolean littleEndian) throws IOException {
        raf.seek(offset);
        raf.writeInt(littleEndian ? Integer.reverseBytes(value) : value);
    }

    private static int readShort(RandomAccessFile raf, long offset, boolean littleEndian)
            throws IOException {
        raf.seek(offset);
        int value = raf.readUnsignedShort();
        return littleEndian ? Integer.reverseBytes(value) >>> 16 : value;
    }

    private static boolean matches(File file, String expectedSha256) {
        if (file == null || !file.isFile() || file.length() == 0) return false;
        try {
            return expectedSha256.equalsIgnoreCase(sha256(file));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.ROOT, "%02x", value));
        }
        return result.toString();
    }
}
