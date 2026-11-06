package io.github.abdurazaaqmohammed.features.apk.flutter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.utils.ZipCompat;
import net.lingala.zip4j.model.FileHeader;

/**
 * Parses and holds Flutter engine version, Dart snapshot hash, features, and assets.
 */
public final class FlutterInfo {

    private static final Pattern ENGINE_COMMIT_PATTERN =
            Pattern.compile("\\b([0-9a-f]{40})\\b");

    private static final Pattern DART_HASH_PATTERN =
            Pattern.compile("\\b([0-9a-f]{32})\\b");

    private static final Pattern DART_FEATURES_PATTERN =
            Pattern.compile("(null_safety|no-type_checks|dedup_instructions|lazy_async_stacks|precompiler)");

    public final String engineCommit;
    public final String dartSnapshotHash;
    public final String dartFeatures;
    public final List<String> abis;
    public final int assetsCount;
    public final boolean hasManifest;

    public FlutterInfo(String engineCommit, String dartSnapshotHash, String dartFeatures,
                       List<String> abis, int assetsCount, boolean hasManifest) {
        this.engineCommit = engineCommit;
        this.dartSnapshotHash = dartSnapshotHash;
        this.dartFeatures = dartFeatures;
        this.abis = abis != null ? abis : Collections.emptyList();
        this.assetsCount = assetsCount;
        this.hasManifest = hasManifest;
    }

    public static FlutterInfo parseFromApk(File apkFile) {
        if (apkFile == null || !apkFile.isFile()) {
            return new FlutterInfo("Unknown", "Unknown", "", Collections.emptyList(), 0, false);
        }

        List<String> abis = FlutterDetector.getFlutterAbis(apkFile);
        int assetsCount = 0;
        boolean hasManifest = false;

        String foundCommit = null;
        String foundHash = null;
        String foundFeatures = null;

        try {
            List<FileHeader> headers = ZipCompat.listHeaders(apkFile);
            for (FileHeader fh : headers) {
                String name = fh.getFileName();
                if (name == null) continue;
                if (name.startsWith(FlutterDetector.FLUTTER_ASSETS_DIR)) {
                    assetsCount++;
                    if (name.contains("AssetManifest")) hasManifest = true;
                }
                if (foundCommit == null && name.endsWith(FlutterDetector.LIB_FLUTTER)) {
                    try (InputStream is = ZipCompat.openEntry(apkFile, name)) {
                        foundCommit = scanEngineCommit(is);
                    } catch (Exception ignored) {}
                }
                if (foundHash == null && name.endsWith(FlutterDetector.LIB_APP)) {
                    try (InputStream is = ZipCompat.openEntry(apkFile, name)) {
                        String[] res = scanDartSnapshot(is);
                        if (res != null) {
                            foundHash = res[0];
                            foundFeatures = res[1];
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        return new FlutterInfo(
                foundCommit != null ? foundCommit : "Not found in libflutter.so",
                foundHash != null ? foundHash : "Not found in libapp.so",
                foundFeatures != null ? foundFeatures : "",
                abis, assetsCount, hasManifest
        );
    }

    public static FlutterInfo parseFromLibrary(File libFile) {
        if (libFile == null || !libFile.isFile()) {
            return new FlutterInfo("Unknown", "Unknown", "", Collections.emptyList(), 0, false);
        }
        String name = libFile.getName();
        String commit = null;
        String hash = null;
        String features = null;

        try (InputStream is = new BufferedInputStream(new FileInputStream(libFile))) {
            if (name.equals(FlutterDetector.LIB_FLUTTER)) {
                commit = scanEngineCommit(is);
            } else if (name.equals(FlutterDetector.LIB_APP)) {
                String[] res = scanDartSnapshot(is);
                if (res != null) {
                    hash = res[0];
                    features = res[1];
                }
            }
        } catch (Exception ignored) {}

        return new FlutterInfo(
                commit != null ? commit : "N/A",
                hash != null ? hash : "N/A",
                features != null ? features : "",
                Collections.emptyList(), 0, false
        );
    }

    private static String scanEngineCommit(InputStream in) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        StringBuilder ascii = new StringBuilder();
        int maxScan = 5 * 1024 * 1024; // Scan first 5MB
        int totalRead = 0;

        while ((n = in.read(buf)) != -1 && totalRead < maxScan) {
            totalRead += n;
            for (int i = 0; i < n; i++) {
                byte b = buf[i];
                if ((b >= 'a' && b <= 'f') || (b >= '0' && b <= '9')) {
                    ascii.append((char) b);
                    if (ascii.length() == 40) {
                        return ascii.toString();
                    }
                } else {
                    ascii.setLength(0);
                }
            }
        }
        return null;
    }

    private static String[] scanDartSnapshot(InputStream in) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        StringBuilder ascii = new StringBuilder();
        String hash = null;
        String features = null;
        int maxScan = 2 * 1024 * 1024; // First 2MB
        int totalRead = 0;

        while ((n = in.read(buf)) != -1 && totalRead < maxScan) {
            totalRead += n;
            String chunk = new String(buf, 0, n, StandardCharsets.ISO_8859_1);

            if (hash == null) {
                Matcher mh = DART_HASH_PATTERN.matcher(chunk);
                if (mh.find()) hash = mh.group(1);
            }

            if (features == null) {
                Matcher mf = DART_FEATURES_PATTERN.matcher(chunk);
                if (mf.find()) features = mf.group(1);
            }

            if (hash != null && features != null) break;
        }

        if (hash != null || features != null) {
            return new String[]{hash != null ? hash : "Unknown", features != null ? features : ""};
        }
        return null;
    }
}
