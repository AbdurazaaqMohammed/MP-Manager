package io.github.abdurazaaqmohammed.features.apk.flutter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ZipCompat;
import net.lingala.zip4j.model.FileHeader;

/**
 * Extracts URLs, API endpoints, Dart symbols, and strings from libapp.so and Flutter APKs.
 */
public final class DartStringsExtractor {

    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[a-zA-Z0-9.-]+(?:/[a-zA-Z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]*)?");

    private static final Pattern ENDPOINT_PATTERN =
            Pattern.compile("/(?:api|v[0-9]|auth|oauth|users|login|register)/[a-zA-Z0-9._/-]+");

    public record DartStringItem(String text, String category) {}

    private DartStringsExtractor() {
    }

    public static List<DartStringItem> extractFromLibrary(File libFile, int minLength) throws IOException {
        if (libFile == null || !libFile.isFile()) return Collections.emptyList();
        try (InputStream is = new BufferedInputStream(new FileInputStream(libFile), 64 * 1024)) {
            return extractFromStream(is, minLength);
        }
    }

    public static List<DartStringItem> extractFromApk(File apkFile, int minLength) throws IOException {
        if (apkFile == null || !apkFile.isFile()) return Collections.emptyList();
        List<DartStringItem> results = new ArrayList<>();
        List<FileHeader> headers = ZipCompat.listHeaders(apkFile);

        for (FileHeader fh : headers) {
            String name = fh.getFileName();
            if (name == null) continue;
            if (name.endsWith(FlutterDetector.LIB_APP) || name.endsWith(FlutterDetector.LIB_FLUTTER)) {
                try (InputStream is = ZipCompat.openEntry(apkFile, name)) {
                    results.addAll(extractFromStream(is, minLength));
                } catch (Exception ignored) {}
            }
        }
        return results;
    }

    private static List<DartStringItem> extractFromStream(InputStream in, int minLength) throws IOException {
        Set<String> seen = new HashSet<>();
        List<DartStringItem> items = new ArrayList<>();

        byte[] buf = new byte[64 * 1024];
        StringBuilder current = new StringBuilder();
        int n;

        while ((n = in.read(buf)) != -1) {
            for (int i = 0; i < n; i++) {
                byte b = buf[i];
                if ((b >= 32 && b <= 126) || b == 9 || b == 10) {
                    current.append((char) b);
                } else {
                    if (current.length() >= minLength) {
                        String s = current.toString().trim();
                        if (s.length() >= minLength && !seen.contains(s)) {
                            seen.add(s);
                            String cat = categorize(s);
                            items.add(new DartStringItem(s, cat));
                        }
                    }
                    current.setLength(0);
                }
            }
        }

        if (current.length() >= minLength) {
            String s = current.toString().trim();
            if (!seen.contains(s)) {
                items.add(new DartStringItem(s, categorize(s)));
            }
        }

        return items;
    }

    private static String categorize(String text) {
        if (URL_PATTERN.matcher(text).matches() || text.startsWith("http://") || text.startsWith("https://")) {
            return "URL";
        }
        if (ENDPOINT_PATTERN.matcher(text).find() || text.startsWith("/api/")) {
            return "Endpoint";
        }
        if (text.startsWith("package:") || text.startsWith("dart:")) {
            return "Dart Symbol";
        }
        return "String";
    }

    public static void exportToFile(List<DartStringItem> items, File outputFile) throws IOException {
        if (items == null || outputFile == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("# Dart & Flutter Extracted Strings\n");
        sb.append("# Total entries: ").append(items.size()).append("\n\n");

        for (DartStringItem item : items) {
            sb.append("[").append(item.category).append("] ").append(item.text).append("\n");
        }
        FileUtils.write(outputFile, sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
