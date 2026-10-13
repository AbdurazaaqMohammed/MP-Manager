package io.github.abdurazaaqmohammed.utils;

import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ZipEntryMap;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class SignatureStripUtil {

    public static boolean hasSignature(File apk) {
        try (ZipFile zf = new ZipFile(apk)) {
            for (FileHeader h : zf.getFileHeaders()) {
                String name = h.getFileName();
                if (name == null) continue;
                String upper = name.toUpperCase(Locale.US);
                if (!upper.startsWith("META-INF/")) continue;
                if (upper.equals("META-INF/MANIFEST.MF")
                        || upper.endsWith(".SF")
                        || upper.endsWith(".RSA")
                        || upper.endsWith(".DSA")
                        || upper.endsWith(".EC")) return true;
            }
        } catch (Exception ignored) {
        }
        try {
            ApkModule m = ApkModule.loadApkFile(apk);
            try {
                if (m.hasSignatureBlock()) return true;
            } finally {
                try {
                    m.close();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean strip(File apk) {
        try {
            File bak = new File(apk.getParentFile(), apk.getName() + ".bak");
            if (!bak.exists()) FileUtils.copyFile(apk, bak);
            boolean removed = false;
            try {
                ApkModule m = ApkModule.loadApkFile(apk);
                try {
                    ZipEntryMap archive = m.getZipEntryMap();
                    int before = archive.size();
                    archive.removeIf(Pattern.compile("(?i)^META-INF/(MANIFEST\\.MF|.*\\.(SF|RSA|DSA|EC))$"));
                    archive.remove("stamp-cert-sha256");
                    m.setApkSignatureBlock(null);
                    m.writeApk(apk);
                    removed = archive.size() < before || m.getApkSignatureBlock() == null;
                } finally {
                    try {
                        m.close();
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
            if (!removed) {
                List<String> doomed = new ArrayList<>();
                try (ZipFile zf = new ZipFile(apk)) {
                    for (FileHeader h : zf.getFileHeaders()) {
                        String name = h.getFileName();
                        if (name == null) {
                            continue;
                        }
                        String upper = name.toUpperCase(Locale.US);
                        if (!upper.startsWith("META-INF/")) {
                            continue;
                        }
                        if (upper.equals("META-INF/MANIFEST.MF")
                                || upper.endsWith(".SF")
                                || upper.endsWith(".RSA")
                                || upper.endsWith(".DSA")
                                || upper.endsWith(".EC")) {
                            doomed.add(name);
                        }
                    }
                    for (String entry : doomed) {
                        zf.removeFile(entry);
                    }
                }
                removed = !doomed.isEmpty();
            }
            return removed;
        } catch (Exception e) {
            return false;
        }
    }
}
