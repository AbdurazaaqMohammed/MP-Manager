package io.github.abdurazaaqmohammed.features.diff;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Compares two directories (Base pane vs Target pane).
 * Matches files by relative path, classifies their status:
 * ADDED (in target but not base), MODIFIED (different size or CRC32), IDENTICAL.
 */
public final class FolderDiffEngine {

    public enum DiffStatus {
        IDENTICAL,
        MODIFIED,
        ADDED
    }

    public static class DiffResult {
        public final Map<String, DiffStatus> statusByName = new HashMap<>();
        public int countAdded = 0;
        public int countModified = 0;
        public int countIdentical = 0;
    }

    private FolderDiffEngine() {}

    public static DiffResult compare(File baseFolder, File targetFolder) {
        DiffResult result = new DiffResult();
        if (targetFolder == null || !targetFolder.exists()) return result;

        Map<String, FileInfo> baseFiles = new HashMap<>();
        if (baseFolder != null && baseFolder.exists()) {
            File[] files = baseFolder.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f != null && !f.getName().equals(".") && !f.getName().equals("..")) {
                        baseFiles.put(f.getName(), new FileInfo(f.isDirectory(), f.length()));
                    }
                }
            }
        }

        File[] targetFiles = targetFolder.listFiles();
        if (targetFiles != null) {
            for (File tf : targetFiles) {
                if (tf == null || tf.getName().equals(".") || tf.getName().equals("..")) continue;
                String name = tf.getName();
                FileInfo baseInfo = baseFiles.get(name);
                if (baseInfo == null) {
                    result.statusByName.put(name, DiffStatus.ADDED);
                    result.countAdded++;
                } else if (baseInfo.isDirectory != tf.isDirectory()) {
                    result.statusByName.put(name, DiffStatus.MODIFIED);
                    result.countModified++;
                } else if (!tf.isDirectory()) {
                    if (baseInfo.length != tf.length()) {
                        result.statusByName.put(name, DiffStatus.MODIFIED);
                        result.countModified++;
                    } else {
                        // Same size: verify fast CRC32 if size > 0 and size < 50MB
                        File baseFile = new File(baseFolder, name);
                        if (isContentIdentical(baseFile, tf)) {
                            result.statusByName.put(name, DiffStatus.IDENTICAL);
                            result.countIdentical++;
                        } else {
                            result.statusByName.put(name, DiffStatus.MODIFIED);
                            result.countModified++;
                        }
                    }
                } else {
                    // Both directories: quick shallow check if counts match
                    File baseDir = new File(baseFolder, name);
                    String[] bList = baseDir.list();
                    String[] tList = tf.list();
                    int bCount = bList != null ? bList.length : 0;
                    int tCount = tList != null ? tList.length : 0;
                    if (bCount != tCount) {
                        result.statusByName.put(name, DiffStatus.MODIFIED);
                        result.countModified++;
                    } else {
                        result.statusByName.put(name, DiffStatus.IDENTICAL);
                        result.countIdentical++;
                    }
                }
            }
        }

        return result;
    }

    private static boolean isContentIdentical(File f1, File f2) {
        if (f1 == null || f2 == null || !f1.exists() || !f2.exists()) return false;
        long len1 = f1.length();
        long len2 = f2.length();
        if (len1 != len2) return false;
        if (len1 == 0) return true;
        // Avoid calculating CRC on files larger than 50MB to remain responsive
        if (len1 > 50L * 1024L * 1024L) return true;

        try {
            long crc1 = calculateCRC32(f1);
            long crc2 = calculateCRC32(f2);
            return crc1 == crc2;
        } catch (Exception e) {
            return false;
        }
    }

    private static long calculateCRC32(File file) {
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[8192];
        try (InputStream is = new FileInputStream(file)) {
            int read;
            while ((read = is.read(buffer)) != -1) {
                crc.update(buffer, 0, read);
            }
            return crc.getValue();
        } catch (Exception e) {
            return -1L;
        }
    }

    private record FileInfo(boolean isDirectory, long length) {}
}
