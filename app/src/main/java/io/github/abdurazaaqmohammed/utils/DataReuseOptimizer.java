package io.github.abdurazaaqmohammed.utils;

import android.content.Context;

import com.reandroid.apk.APKLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.multiplex.apksign.V2V3SchemeSigner;
import io.github.abdurazaaqmohammed.multiplex.zip.DataMultiplexing;

/**
 * Shrinks a "resigned" APK (one that embeds the original APK at {@link #HOST_ENTRY}) by making the
 * outer zip share the original zip's data segments instead of storing duplicates.
 *
 * <p>Signing order matters and is fixed here: apksig rewrites the zip, so V1 is applied first,
 * the optimization runs second, and V2/V3 is then written in place by
 * {@link V2V3SchemeSigner} - the only signer that leaves the optimized offsets alone.
 */
public final class DataReuseOptimizer {

    /** The entry the outer APK stores the original APK in. */
    public static final String HOST_ENTRY = "assets/base.apk";

    /** Smallest size worth testing for a nested APK; the scan only reads four bytes anyway. */
    private static final long MIN_HOST_SIZE = 4096L;

    private DataReuseOptimizer() {
    }

    /**
     * Optimizes {@code inputApk} into a temp file, V1-signed with apksig and V2/V3-signed in
     * place afterwards. The caller swaps the result over the original file.
     */
    public static File optimize(Context context, File inputApk, APKLogger logger, SignWrapper signer) throws Exception {
        String hostEntry = locateHostEntry(context, inputApk, logger);
        long inputLength = inputApk.length();

        File workDir = new File(context.getCacheDir(), "multiplex_" + System.currentTimeMillis());
        if (!workDir.mkdirs() && !workDir.isDirectory()) {
            throw new IOException("Cannot create " + workDir);
        }
        File v1Signed = new File(workDir, "v1.apk");
        File output = new File(workDir, inputApk.getName());

        try {
            logger.logMessage(context.getString(R.string.data_reuse_step_v1));
            signer.signApk(inputApk, v1Signed, true, false, false, false);

            logger.logMessage(context.getString(R.string.data_reuse_step_opt));
            try {
                DataMultiplexing.optimize(v1Signed, output, hostEntry, false);
            } catch (IOException e) {
                String message = e.getMessage();
                if (message != null && message.startsWith("No multiplexable data")) {
                    throw new IOException(context.getString(R.string.data_reuse_no_shared), e);
                }
                throw e;
            }
            if (!DataMultiplexing.isZipFileContentEquals(v1Signed, output)) {
                throw new IOException(context.getString(R.string.data_reuse_check_failed));
            }

            logger.logMessage(context.getString(R.string.data_reuse_step_v2v3));
            V2V3SchemeSigner.sign(output, signer.getSignatureKey(), true, true);
            v1Signed.delete();

            logger.logMessage(context.getString(R.string.data_reuse_saved,
                    formatSize(inputLength),
                    formatSize(output.length()),
                    formatPercent(inputLength, output.length())));
            return output;
        } catch (Exception e) {
            deleteRecursively(workDir);
            throw e;
        }
    }

    /**
     * Finds the entry the original APK is stored in: the conventional {@link #HOST_ENTRY} when it
     * is usable, otherwise the largest stored entry carrying a zip magic number, since packers do
     * not all agree on a path. Throws a localized error when the package embeds no original APK,
     * or when the conventional entry exists but is compressed.
     */
    private static String locateHostEntry(Context context, File inputApk, APKLogger logger) throws IOException {
        try (ZipFile zipFile = new ZipFile(inputApk)) {
            ZipEntry preferred = zipFile.getEntry(HOST_ENTRY);
            String compressedHost = null;
            if (preferred != null && preferred.getMethod() != ZipEntry.STORED) {
                compressedHost = context.getString(R.string.data_reuse_not_stored, HOST_ENTRY);
            } else if (preferred != null && hasZipMagic(zipFile, preferred)) {
                logger.logMessage(context.getString(R.string.data_reuse_host, HOST_ENTRY));
                return HOST_ENTRY;
            }

            String hostEntry = null;
            long hostSize = -1;
            for (Enumeration<? extends ZipEntry> entries = zipFile.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getMethod() != ZipEntry.STORED) {
                    continue;
                }
                long size = entry.getSize();
                if (size < MIN_HOST_SIZE || size <= hostSize) {
                    continue;
                }
                if (!hasZipMagic(zipFile, entry)) {
                    continue;
                }
                hostEntry = entry.getName();
                hostSize = size;
            }
            if (hostEntry != null) {
                logger.logMessage(context.getString(R.string.data_reuse_host, hostEntry));
                return hostEntry;
            }
            if (compressedHost != null) {
                throw new IOException(compressedHost);
            }
            throw new IOException(context.getString(R.string.data_reuse_no_host));
        }
    }

    private static boolean hasZipMagic(ZipFile zipFile, ZipEntry entry) throws IOException {
        byte[] head = new byte[4];
        try (InputStream input = zipFile.getInputStream(entry)) {
            return input.read(head) == 4
                    && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4;
        }
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.US, "%.2f MB", bytes / 1048576.0);
        }
        if (bytes >= 1024L) {
            return String.format(Locale.US, "%.2f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    private static String formatPercent(long before, long after) {
        if (before <= 0) {
            return "0%";
        }
        return String.format(Locale.US, "%.1f%%", (before - after) * 100.0 / before);
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
