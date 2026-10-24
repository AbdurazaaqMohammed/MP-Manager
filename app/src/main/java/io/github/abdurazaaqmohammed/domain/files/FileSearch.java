package io.github.abdurazaaqmohammed.domain.files;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Pure file-search logic extracted from MainActivity.
 * Directory listing is injected so callers can fall back to
 * elevated (root/Shizuku) listings on unreadable folders.
 */
public final class FileSearch {

    /** Max hits/content size guards, kept from the original implementation. */
    public static final int MAX_HITS = 500;
    public static final long MAX_CONTENT_BYTES = 2097152;
    public static final int MAX_LINES_PER_FILE = 20000;
    public static final int SNIPPET_LEN = 140;

    private FileSearch() {
    }

    public interface SearchCallback {
        void onFolderEntered(File folder);
        void onFileChecking(File file);
        void onFileFound(File file, int totalFound);
        boolean isCancelled();
    }

    public static List<File> searchByName(File dir, String query, boolean subfolders,
            boolean matchCase, boolean regex, String textInside,
            long minSize, long maxSize, Function<File, File[]> lister) {
        return searchByName(dir, query, subfolders, matchCase, regex, textInside, minSize, maxSize, lister, null);
    }

    public static List<File> searchByName(File dir, String query, boolean subfolders,
            boolean matchCase, boolean regex, String textInside,
            long minSize, long maxSize, Function<File, File[]> lister, SearchCallback callback) {
        List<File> results = new ArrayList<>();
        Pattern pattern = null;
        if (regex) {
            try {
                pattern = Pattern.compile(query, matchCase ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                return results;
            }
        }
        searchRecursive(dir, results, matchCase ? query : query.toLowerCase(),
                subfolders, matchCase, regex, pattern, textInside, minSize, maxSize, lister, callback);
        return results;
    }

    private static void searchRecursive(File dir, List<File> results, String query,
            boolean subfolders, boolean mCase, boolean regex, Pattern pattern,
            String textInside, long minSize, long maxSize, Function<File, File[]> lister, SearchCallback callback) {
        if (callback != null && callback.isCancelled()) return;
        if (callback != null) callback.onFolderEntered(dir);
        File[] files = dir.listFiles();
        if (files == null) {
            try {
                files = lister.apply(dir);
            } catch (Exception ignored) {
            }
            if (files == null) return;
        }
        for (File f : files) {
            if (callback != null && callback.isCancelled()) return;
            if (callback != null) callback.onFileChecking(f);
            boolean matchName;
            String name = f.getName();
            if (regex && pattern != null) {
                matchName = pattern.matcher(name).find();
            } else {
                matchName = mCase ? name.contains(query) : name.toLowerCase().contains(query);
            }

            boolean matchSize = true;
            if (f.isFile() && (minSize != -1 || maxSize != -1)) {
                long len = f.length();
                if (minSize != -1 && len < minSize) matchSize = false;
                if (maxSize != -1 && len > maxSize) matchSize = false;
            }

            boolean matchText = true;
            if (f.isFile() && textInside != null && !textInside.isEmpty()) {
                matchText = false;
                if (f.length() < 10485760) { // Limit to 10MB files to prevent OOM
                    try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (mCase ? line.contains(textInside)
                                    : line.toLowerCase().contains(textInside.toLowerCase())) {
                                matchText = true;
                                break;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            if (matchName && matchSize && matchText) {
                results.add(f);
                if (callback != null) callback.onFileFound(f, results.size());
            }

            if (subfolders && f.isDirectory()) {
                searchRecursive(f, results, query, subfolders, mCase, regex,
                        pattern, textInside, minSize, maxSize, lister, callback);
            }
        }
    }

    public static List<ContentHit> findInFiles(File dir, String needle,
            boolean matchCase, boolean regex, Progress progress) {
        List<ContentHit> hits = new ArrayList<>();
        Pattern pattern = null;
        if (regex) {
            try {
                pattern = Pattern.compile(needle, matchCase ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                return hits;
            }
        }
        String normNeedle = matchCase ? needle : needle.toLowerCase();
        int[] scanned = {0};
        findRecursive(dir, normNeedle, matchCase, regex, pattern, hits, scanned, progress);
        return hits;
    }

    private static void findRecursive(File dir, String needle, boolean matchCase,
            boolean regex, Pattern pattern, List<ContentHit> hits, int[] scanned,
            Progress progress) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (hits.size() >= MAX_HITS) return;
            if (f.isDirectory()) {
                findRecursive(f, needle, matchCase, regex, pattern, hits, scanned, progress);
            } else if (f.isFile() && f.length() < MAX_CONTENT_BYTES) {
                scanned[0]++;
                if (scanned[0] % 50 == 0 && progress != null) progress.onProgress(scanned[0]);
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line;
                    int lineNo = 0;
                    while ((line = br.readLine()) != null) {
                        lineNo++;
                        if (lineNo == 1 && line.indexOf('\0') >= 0) break;
                        boolean hit;
                        if (regex && pattern != null) hit = pattern.matcher(line).find();
                        else hit = matchCase ? line.contains(needle) : line.toLowerCase().contains(needle);
                        if (hit) {
                            String snippet = line.trim();
                            if (snippet.length() > SNIPPET_LEN)
                                snippet = snippet.substring(0, SNIPPET_LEN) + "\u2026";
                            hits.add(new ContentHit(f, lineNo, snippet));
                            if (hits.size() >= MAX_HITS) return;
                        }
                        if (lineNo > MAX_LINES_PER_FILE) break;
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Called from worker threads; the caller marshals to the UI thread. */
    public interface Progress {
        void onProgress(int scannedFiles);
    }

    public static List<ContentHit> searchInXmlFiles(File root, String query, boolean matchCase, boolean regex, Progress progress) {
        List<ContentHit> hits = new ArrayList<>();
        Pattern pattern = null;
        if (regex) {
            try {
                pattern = Pattern.compile(query, matchCase ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                return hits;
            }
        }
        String normQuery = matchCase ? query : query.toLowerCase();
        int[] scanned = {0};

        if (root.isFile() && (root.getName().toLowerCase().endsWith(".apk") || root.getName().toLowerCase().endsWith(".zip"))) {
            searchInArchiveXml(root, normQuery, matchCase, regex, pattern, hits, scanned, progress);
        } else {
            searchInDirectoryXml(root, normQuery, matchCase, regex, pattern, hits, scanned, progress);
        }
        return hits;
    }

    private static void searchInDirectoryXml(File dir, String query, boolean matchCase, boolean regex,
            Pattern pattern, List<ContentHit> hits, int[] scanned, Progress progress) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (hits.size() >= MAX_HITS) return;
            if (f.isDirectory()) {
                searchInDirectoryXml(f, query, matchCase, regex, pattern, hits, scanned, progress);
            } else if (f.isFile() && f.getName().toLowerCase().endsWith(".xml") && f.length() < MAX_CONTENT_BYTES) {
                scanned[0]++;
                if (progress != null) progress.onProgress(scanned[0]);
                searchSingleXmlFile(f, f.getName(), query, matchCase, regex, pattern, hits);
            }
        }
    }

    private static void searchSingleXmlFile(File file, String displayName, String query, boolean matchCase,
            boolean regex, Pattern pattern, List<ContentHit> hits) {
        try (java.io.InputStream is = new java.io.BufferedInputStream(new java.io.FileInputStream(file))) {
            is.mark(4);
            byte[] magic = new byte[4];
            int r = is.read(magic);
            is.reset();
            String decodedXml = null;
            if (r == 4 && magic[0] == 0x03 && magic[1] == 0x00 && magic[2] == 0x08 && magic[3] == 0x00) {
                // Binary Android XML
                try {
                    decodedXml = new com.apk.axml.aXMLDecoder(is).decodeAsString();
                } catch (Exception ignored) {
                }
            }
            if (decodedXml != null) {
                matchXmlLines(decodedXml, file, displayName, query, matchCase, regex, pattern, hits);
            } else {
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    int lineNo = 0;
                    while ((line = br.readLine()) != null) {
                        lineNo++;
                        boolean hit;
                        if (regex && pattern != null) hit = pattern.matcher(line).find();
                        else hit = matchCase ? line.contains(query) : line.toLowerCase().contains(query);
                        if (hit) {
                            String snippet = line.trim();
                            if (snippet.length() > SNIPPET_LEN)
                                snippet = snippet.substring(0, SNIPPET_LEN) + "\u2026";
                            hits.add(new ContentHit(file, lineNo, snippet, displayName));
                            if (hits.size() >= MAX_HITS) return;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void searchInArchiveXml(File archive, String query, boolean matchCase, boolean regex,
            Pattern pattern, List<ContentHit> hits, int[] scanned, Progress progress) {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(archive)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                if (hits.size() >= MAX_HITS) return;
                java.util.zip.ZipEntry ze = en.nextElement();
                String name = ze.getName();
                if (!ze.isDirectory() && name.toLowerCase().endsWith(".xml") && ze.getSize() < MAX_CONTENT_BYTES) {
                    scanned[0]++;
                    if (progress != null) progress.onProgress(scanned[0]);
                    try (java.io.InputStream is = new java.io.BufferedInputStream(zf.getInputStream(ze))) {
                        is.mark(4);
                        byte[] magic = new byte[4];
                        int r = is.read(magic);
                        is.reset();
                        String decoded = null;
                        if (r == 4 && magic[0] == 0x03 && magic[1] == 0x00 && magic[2] == 0x08 && magic[3] == 0x00) {
                            try {
                                decoded = new com.apk.axml.aXMLDecoder(is).decodeAsString();
                            } catch (Exception ignored) {
                            }
                        }
                        if (decoded == null) {
                            byte[] bytes = is.readAllBytes();
                            decoded = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                        }
                        matchXmlLines(decoded, archive, name, query, matchCase, regex, pattern, hits);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void matchXmlLines(String text, File file, String displayName, String query,
            boolean matchCase, boolean regex, Pattern pattern, List<ContentHit> hits) {
        String[] lines = text.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean hit;
            if (regex && pattern != null) hit = pattern.matcher(line).find();
            else hit = matchCase ? line.contains(query) : line.toLowerCase().contains(query);
            if (hit) {
                String snippet = line.trim();
                if (snippet.length() > SNIPPET_LEN)
                    snippet = snippet.substring(0, SNIPPET_LEN) + "\u2026";
                hits.add(new ContentHit(file, i + 1, snippet, displayName));
                if (hits.size() >= MAX_HITS) return;
            }
        }
    }
