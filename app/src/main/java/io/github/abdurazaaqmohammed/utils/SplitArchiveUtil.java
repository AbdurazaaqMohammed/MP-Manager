package io.github.abdurazaaqmohammed.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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

    // Numbered split: name.7z.001, name.zip.001, name.tar.001, name.001, name.01
    private static final Pattern PATTERN_NUMBERED_SPLIT =
            Pattern.compile("^(.*?)[.](\\d{2,})$", Pattern.CASE_INSENSITIVE);

    // Split ZIP: name.z01, name.z02, ..., name.zip
    private static final Pattern PATTERN_ZIP_SPLIT =
            Pattern.compile("^(.*?)[.](z\\d{2,}|zip)$", Pattern.CASE_INSENSITIVE);

    // Numbered split with alphanumeric extension prefix: name.win000, name.w01, name.tar001, etc.
    private static final Pattern PATTERN_ALPHANUM_SPLIT =
            Pattern.compile("^(.*?)[.]([a-zA-Z]+)(\\d{2,})$", Pattern.CASE_INSENSITIVE);

    // Chunk / Part split: name_part01.tar, name_01.tar, name-01.img, etc.
    private static final Pattern PATTERN_CHUNK_SPLIT =
            Pattern.compile("^(.*?)[._-](?:part|vol|chunk|seg)?[._-]?(\\d{2,})[.]([a-zA-Z0-9]+)$", Pattern.CASE_INSENSITIVE);

    // Suffix numeric split without extension: name_01, name-01
    private static final Pattern PATTERN_SUFFIX_SPLIT =
            Pattern.compile("^(.*?)[._-](\\d{2,})$", Pattern.CASE_INSENSITIVE);

    private static final int BUFFER_SIZE = 64 * 1024;

    private SplitArchiveUtil() {
    }

    /**
     * Checks if a file name matches any known multi-part / split archive naming scheme.
     */
    public static boolean isSplitArchiveName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (ApkIconHelper.isApkLikeFile(name)) return false;
        return PATTERN_PART_RAR.matcher(name).matches()
                || PATTERN_NUMBERED_SPLIT.matcher(name).matches()
                || PATTERN_OLD_RAR.matcher(name).matches()
                || PATTERN_ZIP_SPLIT.matcher(name).matches()
                || PATTERN_ALPHANUM_SPLIT.matcher(name).matches()
                || PATTERN_CHUNK_SPLIT.matcher(name).matches();
    }

    /**
     * Fast local candidate check on the file itself (NO directory listing or sibling scanning).
     * Returns true ONLY if:
     * 1. The name indicates it is a split archive part (RAR, ZIP, 7Z, .001, .win000, etc.), OR
     * 2. The physical file header indicates an archive/disk image AND the filename has numeric indexing.
     */
    public static boolean isPotentialSplitCandidate(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName();
        if (name == null || name.isEmpty() || ApkIconHelper.isApkLikeFile(name)) return false;

        // 1. By naming convention
        if (isSplitArchiveName(name)) {
            return true;
        }

        // 2. By content header + numeric indexing
        if (hasNumericIndex(name) && detectMergedExtension(file) != null) {
            return true;
        }

        return false;
    }

    private static boolean hasNumericIndex(String name) {
        if (name == null || name.isEmpty()) return false;
        return PATTERN_PART_RAR.matcher(name).matches()
                || PATTERN_NUMBERED_SPLIT.matcher(name).matches()
                || PATTERN_OLD_RAR.matcher(name).matches()
                || PATTERN_ZIP_SPLIT.matcher(name).matches()
                || PATTERN_ALPHANUM_SPLIT.matcher(name).matches()
                || PATTERN_CHUNK_SPLIT.matcher(name).matches()
                || PATTERN_SUFFIX_SPLIT.matcher(name).matches();
    }

    /**
     * Returns true if the given file looks like a part of a split archive AND
     * has sibling parts in the same directory forming a multi-volume sequence.
     * APK files and their derivatives are strictly excluded.
     *
     * Performance optimization: First checks name and header on the file itself.
     * Only if the file qualifies as a candidate does it scan the directory for siblings.
     */
    public static boolean isSplitArchive(File file) {
        if (!isPotentialSplitCandidate(file)) return false;
        return hasSiblingParts(file);
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
            String zeroNum = String.format(Locale.ROOT, "%0" + width + "d", 0);
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

        // 5. Alphanumeric split: name.win002 -> name.win000 or name.win001
        Matcher mAlpha = PATTERN_ALPHANUM_SPLIT.matcher(name);
        if (mAlpha.matches()) {
            String base = mAlpha.group(1);
            String prefix = mAlpha.group(2);
            String numStr = mAlpha.group(3);
            int width = numStr.length();
            String zeroNum = String.format(Locale.ROOT, "%0" + width + "d", 0);
            File candidate0 = new File(parent, base + "." + prefix + zeroNum);
            if (candidate0.exists()) return candidate0;
            String firstNum = String.format(Locale.ROOT, "%0" + width + "d", 1);
            File candidate1 = new File(parent, base + "." + prefix + firstNum);
            if (candidate1.exists()) return candidate1;
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

        // 5. Alphanumeric split: name.win000, name.win001, etc.
        Matcher mAlpha = PATTERN_ALPHANUM_SPLIT.matcher(name);
        if (mAlpha.matches()) {
            String base = mAlpha.group(1);
            String prefix = mAlpha.group(2);
            File[] siblings = parent.listFiles();
            if (siblings == null) return Collections.singletonList(anyPart);

            List<PartInfo> matched = new ArrayList<>();
            for (File sib : siblings) {
                if (!sib.isFile()) continue;
                Matcher sm = PATTERN_ALPHANUM_SPLIT.matcher(sib.getName());
                if (sm.matches() && sm.group(1).equalsIgnoreCase(base) && sm.group(2).equalsIgnoreCase(prefix)) {
                    try {
                        int num = Integer.parseInt(sm.group(3));
                        matched.add(new PartInfo(sib, num));
                    } catch (NumberFormatException ignored) {}
                }
            }
            matched.sort(Comparator.comparingInt(p -> p.number));
            List<File> res = new ArrayList<>();
            for (PartInfo pi : matched) res.add(pi.file);
            return res.isEmpty() ? Collections.singletonList(anyPart) : res;
        }

        return Collections.singletonList(anyPart);
    }

    /**
     * Checks if a user-selected set of files can be merged together as split parts.
     * Evaluates naming conventions, shared base prefixes, and content headers.
     */
    public static boolean areMergeableParts(List<File> files) {
        if (files == null || files.size() < 2) return false;
        for (File f : files) {
            if (f == null || !f.isFile() || ApkIconHelper.isApkLikeFile(f.getName())) {
                return false;
            }
        }

        // 1. By naming scheme: if files have split patterns and share a base
        String base0 = getFileBasePrefix(files.get(0).getName());
        boolean allShareBase = true;
        for (File f : files) {
            String b = getFileBasePrefix(f.getName());
            if (!b.equalsIgnoreCase(base0)) {
                allShareBase = false;
                break;
            }
        }

        if (allShareBase) {
            for (File f : files) {
                if (isSplitArchiveName(f.getName()) || hasNumericIndex(f.getName())) {
                    return true;
                }
            }
        }

        // 2. Check if first part has an archive/image container header
        List<File> sorted = sortParts(files);
        if (sorted != null && !sorted.isEmpty()) {
            File first = sorted.get(0);
            if (detectMergedExtension(first) != null && hasNumericIndex(first.getName())) {
                return true;
            }
        }

        return false;
    }

    public static String getFileBasePrefix(String name) {
        if (name == null) return "";
        Matcher mPart = PATTERN_PART_RAR.matcher(name);
        if (mPart.matches()) return mPart.group(1);
        Matcher mNum = PATTERN_NUMBERED_SPLIT.matcher(name);
        if (mNum.matches()) return mNum.group(1);
        Matcher mOldRar = PATTERN_OLD_RAR.matcher(name);
        if (mOldRar.matches()) return mOldRar.group(1);
        Matcher mZip = PATTERN_ZIP_SPLIT.matcher(name);
        if (mZip.matches()) return mZip.group(1);
        Matcher mAlpha = PATTERN_ALPHANUM_SPLIT.matcher(name);
        if (mAlpha.matches()) return mAlpha.group(1);
        Matcher mChunk = PATTERN_CHUNK_SPLIT.matcher(name);
        if (mChunk.matches()) return mChunk.group(1);
        Matcher mSuffix = PATTERN_SUFFIX_SPLIT.matcher(name);
        if (mSuffix.matches()) return mSuffix.group(1);
        return name.replaceAll("[._-]?\\d+$", "");
    }

    /**
     * Sorts the given split parts into their correct sequential order
     * (e.g. part1, part2, part3... or 000, 001, 002... or .z01, .z02... .zip).
     */
    public static List<File> sortParts(List<File> parts) {
        if (parts == null || parts.isEmpty()) return new ArrayList<>();
        List<File> sorted = new ArrayList<>(parts);
        sorted.sort((f1, f2) -> {
            int n1 = extractPartNumber(f1.getName());
            int n2 = extractPartNumber(f2.getName());
            if (n1 != -1 && n2 != -1 && n1 != n2) {
                return Integer.compare(n1, n2);
            }
            return naturalCompare(f1.getName(), f2.getName());
        });
        return sorted;
    }

    public static int extractPartNumber(String name) {
        if (name == null || name.isEmpty()) return -1;
        Matcher mPart = PATTERN_PART_RAR.matcher(name);
        if (mPart.matches()) {
            try { return Integer.parseInt(mPart.group(2)); } catch (Exception ignored) {}
        }
        Matcher mOldRar = PATTERN_OLD_RAR.matcher(name);
        if (mOldRar.matches()) {
            String ext = mOldRar.group(2).toLowerCase(Locale.ROOT);
            if (ext.equals("rar")) return 0;
            if (ext.startsWith("r")) {
                try { return Integer.parseInt(ext.substring(1)); } catch (Exception ignored) {}
            }
        }
        Matcher mNum = PATTERN_NUMBERED_SPLIT.matcher(name);
        if (mNum.matches()) {
            try { return Integer.parseInt(mNum.group(2)); } catch (Exception ignored) {}
        }
        Matcher mZip = PATTERN_ZIP_SPLIT.matcher(name);
        if (mZip.matches()) {
            String ext = mZip.group(2).toLowerCase(Locale.ROOT);
            if (ext.equals("zip")) return Integer.MAX_VALUE;
            if (ext.startsWith("z")) {
                try { return Integer.parseInt(ext.substring(1)); } catch (Exception ignored) {}
            }
        }
        Matcher mAlpha = PATTERN_ALPHANUM_SPLIT.matcher(name);
        if (mAlpha.matches()) {
            try { return Integer.parseInt(mAlpha.group(3)); } catch (Exception ignored) {}
        }
        Matcher mChunk = PATTERN_CHUNK_SPLIT.matcher(name);
        if (mChunk.matches()) {
            try { return Integer.parseInt(mChunk.group(2)); } catch (Exception ignored) {}
        }
        Matcher mSuffix = PATTERN_SUFFIX_SPLIT.matcher(name);
        if (mSuffix.matches()) {
            try { return Integer.parseInt(mSuffix.group(2)); } catch (Exception ignored) {}
        }
        return -1;
    }

    public static int naturalCompare(String s1, String s2) {
        int i1 = 0, i2 = 0;
        int len1 = s1.length(), len2 = s2.length();
        while (i1 < len1 && i2 < len2) {
            char c1 = s1.charAt(i1);
            char c2 = s2.charAt(i2);
            if (Character.isDigit(c1) && Character.isDigit(c2)) {
                int start1 = i1;
                while (i1 < len1 && Character.isDigit(s1.charAt(i1))) i1++;
                int start2 = i2;
                while (i2 < len2 && Character.isDigit(s2.charAt(i2))) i2++;
                String numStr1 = s1.substring(start1, i1);
                String numStr2 = s2.substring(start2, i2);
                try {
                    long l1 = Long.parseLong(numStr1);
                    long l2 = Long.parseLong(numStr2);
                    if (l1 != l2) return Long.compare(l1, l2);
                } catch (NumberFormatException ignored) {}
                int cmp = numStr1.compareTo(numStr2);
                if (cmp != 0) return cmp;
            } else {
                int cmp = Character.compare(Character.toLowerCase(c1), Character.toLowerCase(c2));
                if (cmp != 0) return cmp;
                i1++;
                i2++;
            }
        }
        return Integer.compare(len1, len2);
    }

    /**
     * Returns true if there are sibling parts forming a multi-volume sequence.
     */
    public static boolean hasSiblingParts(File file) {
        if (file == null || !file.isFile() || ApkIconHelper.isApkLikeFile(file.getName())) return false;
        List<File> parts = getOrderedParts(file);
        return parts != null && parts.size() > 1;
    }

    /**
     * Determines the suggested merged output filename when combining split files.
     * Checks the physical magic bytes of the file first to suggest the true extension.
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
            String base = mNum.group(1);
            String detected = detectMergedExtension(file);
            if (detected != null && !base.toLowerCase(Locale.ROOT).endsWith("." + detected)) {
                return base + "." + detected;
            }
            return base;
        }

        Matcher mOldRar = PATTERN_OLD_RAR.matcher(name);
        if (mOldRar.matches()) {
            return mOldRar.group(1) + ".rar";
        }

        Matcher mZip = PATTERN_ZIP_SPLIT.matcher(name);
        if (mZip.matches()) {
            return mZip.group(1) + ".zip";
        }

        Matcher mAlpha = PATTERN_ALPHANUM_SPLIT.matcher(name);
        if (mAlpha.matches()) {
            String base = mAlpha.group(1);
            String extPrefix = mAlpha.group(2).toLowerCase(Locale.ROOT);
            String detected = detectMergedExtension(file);
            if (detected != null) {
                return base + "." + detected;
            }
            if ("win".equals(extPrefix)) {
                return base + ".tar";
            }
            return base;
        }

        return name + ".merged";
    }

    /**
     * Inspects leading bytes to detect true archive/image container kind.
     */
    public static String detectMergedExtension(File file) {
        if (file == null || !file.isFile() || file.length() < 16) return null;
        byte[] head = new byte[1088];
        int n;
        try (InputStream in = new FileInputStream(file)) {
            n = in.read(head);
        } catch (Exception e) {
            return null;
        }
        if (n < 4) return null;
        // Zip: PK\x03\x04 or PK\x07\x08
        if (head[0] == 'P' && head[1] == 'K' && (head[2] == 3 || head[2] == 7) && (head[3] == 4 || head[3] == 8)) {
            return "zip";
        }
        // 7z: 37 7a bc af 27 1c
        if (n >= 6 && head[0] == 0x37 && head[1] == 0x7A && head[2] == (byte) 0xBC && head[3] == (byte) 0xAF && head[4] == 0x27 && head[5] == 0x1C) {
            return "7z";
        }
        // RAR: Rar!\x1a\x07
        if (n >= 6 && head[0] == 'R' && head[1] == 'a' && head[2] == 'r' && head[3] == '!' && head[4] == 0x1A && head[5] == 0x07) {
            return "rar";
        }
        // Gzip: \x1f\x8b
        if (head[0] == (byte) 0x1F && head[1] == (byte) 0x8B) {
            return "tar.gz";
        }
        // Tar POSIX: "ustar" at 257
        if (n >= 262 && head[257] == 'u' && head[258] == 's' && head[259] == 't' && head[260] == 'a' && head[261] == 'r') {
            return "tar";
        }
        // Tar Checksum
        if (n >= 512 && isTarHeader(head)) {
            return "tar";
        }
        // EXT4 superblock at 1080: 0x53 0xef
        if (n >= 1082 && (head[1080] & 0xFF) == 0x53 && (head[1081] & 0xFF) == 0xEF) {
            return "img";
        }
        // Android Boot Image ("ANDROID!")
        if (head[0] == 'A' && head[1] == 'N' && head[2] == 'D' && head[3] == 'R' && head[4] == 'O' && head[5] == 'I' && head[6] == 'D' && head[7] == '!') {
            return "img";
        }
        return null;
    }

    private static boolean isTarHeader(byte[] head) {
        if (head.length < 512 || head[0] == 0) return false;
        try {
            String chk = new String(head, 148, 8, StandardCharsets.ISO_8859_1).trim();
            if (chk.isEmpty()) return false;
            long stored = Long.parseLong(chk, 8);
            long sum = 0;
            for (int i = 0; i < 512; i++) {
                sum += (i >= 148 && i < 156) ? 0x20 : (head[i] & 0xFF);
            }
            return sum == stored;
        } catch (Exception e) {
            return false;
        }
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
