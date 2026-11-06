package io.github.abdurazaaqmohammed.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for detecting, ordering, browsing, extracting, and merging
 * multi-part / split archives (.part1.rar, .7z.001, .z01, .001/.002, etc.).
 */
public final class SplitArchiveUtil {

    // Modern RAR split: name.part1.rar, name.part01.rar, name.pt01.rar
    private static final Pattern PATTERN_PART_RAR =
            Pattern.compile("^(.*?)[.](?:part|pt)(\\d+)[.](rar)$", Pattern.CASE_INSENSITIVE);

    // Old RAR split: name.rar, name.r00, name.r01, ...
    private static final Pattern PATTERN_OLD_RAR =
            Pattern.compile("^(.*?)[.](rar|r\\d{2,})$", Pattern.CASE_INSENSITIVE);

    // Numbered split: name.7z.001, name.zip.001, name.tar.001, name.001
    private static final Pattern PATTERN_NUMBERED_SPLIT =
            Pattern.compile("^(.*?)[.](\\d{3,})$", Pattern.CASE_INSENSITIVE);

    // Split ZIP: name.z01, name.z02, ..., name.zip
    private static final Pattern PATTERN_ZIP_SPLIT =
            Pattern.compile("^(.*?)[.](z\\d{2,}|zip)$", Pattern.CASE_INSENSITIVE);

    private static final int BUFFER_SIZE = 64 * 1024;

    private SplitArchiveUtil() {
    }

    /**
     * Checks if a file name matches any known multi-part / split archive naming scheme.
     */
    public static boolean isSplitArchiveName(String name) {
        if (name == null || name.isEmpty()) return false;
        return PATTERN_PART_RAR.matcher(name).matches()
                || PATTERN_NUMBERED_SPLIT.matcher(name).matches()
                || PATTERN_OLD_RAR.matcher(name).matches()
                || PATTERN_ZIP_SPLIT.matcher(name).matches();
    }

    /**
     * Returns true if the given file looks like a part of a split archive.
     */
    public static boolean isSplitArchive(File file) {
        return file != null && file.isFile() && isSplitArchiveName(file.getName());
    }

    /**
     * Determines whether the file is part of a split RAR archive.
     */
    public static boolean isRarSplit(File file) {
        if (file == null) return false;
        String name = file.getName();
        return PATTERN_PART_RAR.matcher(name).matches()
                || (PATTERN_OLD_RAR.matcher(name).matches() && !name.toLowerCase(Locale.ROOT).endsWith(".rar"));
    }

    /**
     * Determines whether the file is part of a split 7-Zip archive (.7z.001 or .001).
     */
    public static boolean isSevenZSplit(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.contains(".7z.") || name.endsWith(".7z.001");
    }

    /**
     * Given any part in a split archive sequence, finds the primary (first) part.
     * For .part2.rar -> returns .part1.rar (or .part01.rar).
     * For .7z.002 -> returns .7z.001.
     * For .z01 -> returns .zip (central directory).
     * For .r01 -> returns .rar.
     */
    public static File findFirstPart(File file) {
        if (file == null || !file.isFile()) return file;
        File parent = file.getParentFile();
        if (parent == null) return file;
        String name = file.getName();

        // 1. Modern RAR: name.part2.rar -> name.part1.rar
        Matcher mPart = PATTERN_PART_RAR.matcher(name);
        if (mPart.matches()) {
            String base = mPart.group(1);
            String numStr = mPart.group(2);
            String ext = mPart.group(3);
            int width = numStr.length();
            String firstNum = String.format(Locale.ROOT, "%0" + width + "d", 1);
            File candidate = new File(parent, base + ".part" + firstNum + "." + ext);
            if (candidate.exists()) return candidate;
            File alt = new File(parent, base + ".part1." + ext);
            if (alt.exists()) return alt;
            return file;
        }

        // 2. Old RAR: name.r01 -> name.rar
        Matcher mOldRar = PATTERN_OLD_RAR.matcher(name);
        if (mOldRar.matches()) {
            String base = mOldRar.group(1);
            File rarFile = new File(parent, base + ".rar");
            if (rarFile.exists()) return rarFile;
            File r00File = new File(parent, base + ".r00");
            if (r00File.exists()) return r00File;
            return file;
        }

        // 3. Numbered split: name.7z.002 -> name.7z.001, name.002 -> name.001
        Matcher mNum = PATTERN_NUMBERED_SPLIT.matcher(name);
        if (mNum.matches()) {
            String base = mNum.group(1);
            String numStr = mNum.group(2);
            int width = numStr.length();
            String firstNum = String.format(Locale.ROOT, "%0" + width + "d", 1);
            File candidate = new File(parent, base + "." + firstNum);
            if (candidate.exists()) return candidate;
            File zeroNum = String.format(Locale.ROOT, "%0" + width + "d", 0);
            File candidate0 = new File(parent, base + "." + zeroNum);
            if (candidate0.exists()) return candidate0;
            return file;
        }

        // 4. Split ZIP: name.z01 -> name.zip
        Matcher mZip = PATTERN_ZIP_SPLIT.matcher(name);
        if (mZip.matches()) {
            String base = mZip.group(1);
            File zipFile = new File(parent, base + ".zip");
            if (zipFile.exists()) return zipFile;
            return file;
        }

        return file;
    }

    /**
     * Finds and returns all consecutive parts belonging to the split archive in sorted order.
     */
    public static List<File> getOrderedParts(File anyPart) {
        if (anyPart == null || !anyPart.exists()) return Collections.emptyList();
        File parent = anyPart.getParentFile();
        if (parent == null) return Collections.singletonList(anyPart);
        String name = anyPart.getName();

        // 1. Modern RAR: name.partN.rar
        Matcher mPart = PATTERN_PART_RAR.matcher(name);
        if (mPart.matches()) {
            String base = mPart.group(1);
            String ext = mPart.group(3);
            File[] siblings = parent.listFiles();
            if (siblings == null) return Collections.singletonList(anyPart);

            List<PartInfo> matched = new ArrayList<>();
            for (File sib : siblings) {
                if (!sib.isFile()) continue;
                Matcher sm = PATTERN_PART_RAR.matcher(sib.getName());
                if (sm.matches() && sm.group(1).equalsIgnoreCase(base) && sm.group(3).equalsIgnoreCase(ext)) {
                    try {
                        int num = Integer.parseInt(sm.group(2));
                        matched.add(new PartInfo(sib, num));
                    } catch (NumberFormatException ignored) {}
                }
            }
            matched.sort(Comparator.comparingInt(p -> p.number));
            List<File> res = new ArrayList<>();
            for (PartInfo pi : matched) res.add(pi.file);
            return res.isEmpty() ? Collections.singletonList(anyPart) : res;
        }

        // 2. Numbered split: name.001, name.7z.001, etc.
        Matcher mNum = PATTERN_NUMBERED_SPLIT.matcher(name);
        if (mNum.matches()) {
            String base = mNum.group(1);
            File[] siblings = parent.listFiles();
            if (siblings == null) return Collections.singletonList(anyPart);

            List<PartInfo> matched = new ArrayList<>();
            for (File sib : siblings) {
                if (!sib.isFile()) continue;
                Matcher sm = PATTERN_NUMBERED_SPLIT.matcher(sib.getName());
                if (sm.matches() && sm.group(1).equalsIgnoreCase(base)) {
                    try {
                        int num = Integer.parseInt(sm.group(2));
                        matched.add(new PartInfo(sib, num));
                    } catch (NumberFormatException ignored) {}
                }
            }
            matched.sort(Comparator.comparingInt(p -> p.number));
            List<File> res = new ArrayList<>();
            for (PartInfo pi : matched) res.add(pi.file);
            return res.isEmpty() ? Collections.singletonList(anyPart) : res;
        }

        // 3. Old RAR: name.rar, name.r00, name.r01...
        Matcher mOldRar = PATTERN_OLD_RAR.matcher(name);
        if (mOldRar.matches()) {
            String base = mOldRar.group(1);
            File[] siblings = parent.listFiles();
            if (siblings == null) return Collections.singletonList(anyPart);

            List<File> res = new ArrayList<>();
            File mainRar = new File(parent, base + ".rar");
            if (mainRar.exists()) res.add(mainRar);

            List<PartInfo> rParts = new ArrayList<>();
            for (File sib : siblings) {
                if (!sib.isFile()) continue;
                String sn = sib.getName();
                if (sn.equalsIgnoreCase(base + ".rar")) continue;
                Matcher sm = PATTERN_OLD_RAR.matcher(sn);
                if (sm.matches() && sm.group(1).equalsIgnoreCase(base)) {
                    String extPart = sm.group(2).toLowerCase(Locale.ROOT);
                    if (extPart.startsWith("r")) {
                        try {
                            int num = Integer.parseInt(extPart.substring(1));
                            rParts.add(new PartInfo(sib, num));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            rParts.sort(Comparator.comparingInt(p -> p.number));
            for (PartInfo pi : rParts) res.add(pi.file);
            return res.isEmpty() ? Collections.singletonList(anyPart) : res;
        }

        // 4. Split ZIP: name.z01, name.z02... name.zip
        Matcher mZip = PATTERN_ZIP_SPLIT.matcher(name);
        if (mZip.matches()) {
            String base = mZip.group(1);
            File[] siblings = parent.listFiles();
            if (siblings == null) return Collections.singletonList(anyPart);

            List<PartInfo> zParts = new ArrayList<>();
            File mainZip = null;
            for (File sib : siblings) {
                if (!sib.isFile()) continue;
                String sn = sib.getName();
                if (sn.equalsIgnoreCase(base + ".zip")) {
                    mainZip = sib;
                    continue;
                }
                Matcher sm = PATTERN_ZIP_SPLIT.matcher(sn);
                if (sm.matches() && sm.group(1).equalsIgnoreCase(base)) {
                    String extPart = sm.group(2).toLowerCase(Locale.ROOT);
                    if (extPart.startsWith("z")) {
                        try {
                            int num = Integer.parseInt(extPart.substring(1));
                            zParts.add(new PartInfo(sib, num));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            zParts.sort(Comparator.comparingInt(p -> p.number));
            List<File> res = new ArrayList<>();
            for (PartInfo pi : zParts) res.add(pi.file);
            if (mainZip != null) res.add(mainZip); // PKZIP format puts .zip last
            return res.isEmpty() ? Collections.singletonList(anyPart) : res;
        }

        return Collections.singletonList(anyPart);
    }

    /**
     * Returns true if there are sibling parts forming a multi-volume sequence.
     */
    public static boolean hasSiblingParts(File file) {
        List<File> parts = getOrderedParts(file);
        return parts != null && parts.size() > 1;
    }

    /**
     * Determines the suggested merged output filename when combining split files.
     */
    public static String getMergedFileName(File file) {
        if (file == null) return "merged.bin";
        String name = file.getName();

        Matcher mPart = PATTERN_PART_RAR.matcher(name);
        if (mPart.matches()) {
            return mPart.group(1) + ".rar";
        }

        Matcher mNum = PATTERN_NUMBERED_SPLIT.matcher(name);
        if (mNum.matches()) {
            return mNum.group(1);
        }

        Matcher mOldRar = PATTERN_OLD_RAR.matcher(name);
        if (mOldRar.matches()) {
            return mOldRar.group(1) + ".rar";
        }

        Matcher mZip = PATTERN_ZIP_SPLIT.matcher(name);
        if (mZip.matches()) {
            return mZip.group(1) + ".zip";
        }

        return name + ".merged";
    }

    /**
     * Merges a series of split files into a single destination file.
     */
    public static void mergeParts(List<File> parts, File outputFile, FileUtils.TransferListener listener) throws IOException {
        if (parts == null || parts.isEmpty()) {
            throw new IOException("No parts to merge");
        }
        if (outputFile == null) {
            throw new IOException("Output file cannot be null");
        }
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        byte[] buf = new byte[BUFFER_SIZE];
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile), BUFFER_SIZE)) {
            for (File part : parts) {
                if (listener != null) {
                    listener.checkpoint();
                    listener.onFile(part, outputFile);
                }
                try (InputStream is = new BufferedInputStream(new FileInputStream(part), BUFFER_SIZE)) {
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        if (listener != null) listener.checkpoint();
                        os.write(buf, 0, n);
                        if (listener != null) listener.onBytes(n);
                    }
                }
            }
            os.flush();
        }
    }

    /**
     * Splits a file into multiple parts of specified byte size.
     *
     * @param inputFile the file to split
     * @param outputDir target folder for the parts
     * @param partSizeBytes maximum size of each slice
     * @param namingStyle "ext_numbered" (file.zip.001) or "part_ext" (file.part1.zip)
     * @param listener progress reporter with checkpoint support
     * @return the list of created part files
     */
    public static List<File> splitFile(File inputFile, File outputDir, long partSizeBytes, String namingStyle, FileUtils.TransferListener listener) throws IOException {
        if (inputFile == null || !inputFile.isFile()) throw new IOException("Invalid input file");
        if (outputDir == null) outputDir = inputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) outputDir.mkdirs();
        if (partSizeBytes < 1024) throw new IOException("Part size too small (min 1KB)");

        long totalSize = inputFile.length();
        int numParts = (int) Math.ceil((double) totalSize / partSizeBytes);
        if (numParts <= 1) throw new IOException("File is smaller than part size, no splitting needed");

        String origName = inputFile.getName();
        int dot = origName.lastIndexOf('.');
        String baseName = dot != -1 ? origName.substring(0, dot) : origName;
        String ext = dot != -1 ? origName.substring(dot) : "";

        List<File> created = new ArrayList<>();
        byte[] buf = new byte[BUFFER_SIZE];

        try (InputStream in = new BufferedInputStream(new FileInputStream(inputFile), BUFFER_SIZE)) {
            for (int part = 1; part <= numParts; part++) {
                if (listener != null) listener.checkpoint();

                String partName;
                if ("part_ext".equals(namingStyle)) {
                    partName = baseName + ".part" + part + ext;
                } else {
                    partName = origName + "." + String.format(Locale.ROOT, "%03d", part);
                }

                File partFile = new File(outputDir, partName);
                created.add(partFile);

                if (listener != null) listener.onFile(inputFile, partFile);

                long bytesWrittenForPart = 0;
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(partFile), BUFFER_SIZE)) {
                    while (bytesWrittenForPart < partSizeBytes) {
                        if (listener != null) listener.checkpoint();
                        long toRead = Math.min((long) buf.length, partSizeBytes - bytesWrittenForPart);
                        int n = in.read(buf, 0, (int) toRead);
                        if (n < 0) break;
                        out.write(buf, 0, n);
                        bytesWrittenForPart += n;
                        if (listener != null) listener.onBytes(n);
                    }
                    out.flush();
                }
            }
        } catch (Exception e) {
            for (File f : created) {
                if (f.exists()) f.delete();
            }
            throw e;
        }

        return created;
    }

    private static class PartInfo {
        final File file;
        final int number;

        PartInfo(File file, int number) {
            this.file = file;
            this.number = number;
        }
    }
}
