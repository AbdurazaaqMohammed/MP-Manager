package io.github.abdurazaaqmohammed.utils;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SignatureStripUtil {

    public static boolean strip(File apk) {
        try {
            FileUtils.copyFile(apk, new File(apk.getParentFile(), apk.getName() + ".bak"));
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
                    if (upper.equals("META-INF/MANIFEST.MF")) {
                        continue;
                    }
                    if (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")) {
                        doomed.add(name);
                    }
                }
                for (String entry : doomed) {
                    zf.removeFile(entry);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
