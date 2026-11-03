package io.github.abdurazaaqmohammed.utils;

import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class StickerUtil {

    private StickerUtil() {}

    /**
     * Checks if a given file is a WhatsApp animated Lottie sticker package
     * disguised as a .webp (or any other extension).
     */
    public static boolean isLottieSticker(File file) {
        if (file == null || !file.isFile() || file.length() < 30) return false;
        try (InputStream in = FileUtils.getInputStream(file)) {
            byte[] header = new byte[4];
            if (in.read(header) != 4) return false;
            // Must have zip magic PK\03\04
            if (header[0] != 0x50 || header[1] != 0x4B || header[2] != 0x03 || header[3] != 0x04) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }

        // Verify it contains animation/animation.json
        try (ZipInputStream zis = new ZipInputStream(FileUtils.getInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if ("animation/animation.json".equals(name) || name.endsWith("/animation.json") || "animation.json".equals(name)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Extracts the animation.json string from the sticker zip file.
     */
    public static String extractLottieJson(File file) {
        try (ZipInputStream zis = new ZipInputStream(FileUtils.getInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if ("animation/animation.json".equals(name) || name.endsWith("/animation.json") || "animation.json".equals(name)) {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zis.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                    return baos.toString("UTF-8");
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
