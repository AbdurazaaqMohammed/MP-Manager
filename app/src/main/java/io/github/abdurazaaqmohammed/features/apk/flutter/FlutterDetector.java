package io.github.abdurazaaqmohammed.features.apk.flutter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.abdurazaaqmohammed.utils.ZipCompat;
import net.lingala.zip4j.model.FileHeader;

/**
 * Detects Flutter runtime artifacts, architectures, and assets inside APKs or directories.
 */
public final class FlutterDetector {

    public static final String LIB_FLUTTER = "libflutter.so";
    public static final String LIB_APP = "libapp.so";
    public static final String FLUTTER_ASSETS_DIR = "assets/flutter_assets/";

    private FlutterDetector() {
    }

    public static boolean isFlutterLib(String fileName) {
        if (fileName == null) return false;
        return fileName.equals(LIB_FLUTTER) || fileName.equals(LIB_APP);
    }

    public static boolean isFlutterApk(File apkFile) {
        if (apkFile == null || !apkFile.isFile() || apkFile.length() < 1024) return false;
        try {
            List<FileHeader> headers = ZipCompat.listHeaders(apkFile);
            for (FileHeader fh : headers) {
                String name = fh.getFileName();
                if (name == null) continue;
                if (name.contains(LIB_FLUTTER) || name.contains(LIB_APP) || name.startsWith(FLUTTER_ASSETS_DIR)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static List<String> getFlutterAbis(File apkFile) {
        if (apkFile == null || !apkFile.isFile()) return Collections.emptyList();
        Set<String> abis = new HashSet<>();
        try {
            List<FileHeader> headers = ZipCompat.listHeaders(apkFile);
            for (FileHeader fh : headers) {
                String name = fh.getFileName();
                if (name == null) continue;
                if (name.startsWith("lib/") && (name.endsWith("/" + LIB_FLUTTER) || name.endsWith("/" + LIB_APP))) {
                    String[] parts = name.split("/");
                    if (parts.length >= 3) {
                        abis.add(parts[1]);
                    }
                }
            }
        } catch (Exception ignored) {}
        List<String> list = new ArrayList<>(abis);
        Collections.sort(list);
        return list;
    }
}
