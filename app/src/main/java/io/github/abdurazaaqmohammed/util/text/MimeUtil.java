package io.github.abdurazaaqmohammed.util.text;

import io.github.abdurazaaqmohammed.util.io.FileUtils;


import android.content.Context;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;

public class MimeUtil {

    /**
     * Preference key for the user's external default app (Open with →
     * long-press → Set as default) for one MIME type. The key stays
     * MIME-scoped on purpose: one saved default per MIME, no per-extension
     * aliases, so existing keys keep working.
     */
    public static String defaultAppKey(String mime) {
        return "openwith_default_" + mime.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
    }

    /**
     * The semantic display name of a file for MIME purposes. When the caller
     * dispatches {@code foo.pdf.bak} under its stripped name, MIME probing
     * should see {@code foo.pdf} - never a default keyed off the {@code .bak}
     * suffix. A lone {@code foo.bak} (nothing meaningful under the suffix)
     * keeps its full name.
     */
    public static String logicalDisplayName(String fileName) {
        if (fileName == null) return null;
        String bak = ".bak";
        if (fileName.toLowerCase(Locale.ROOT).endsWith(bak)) {
            String stripped = fileName.substring(0, fileName.length() - bak.length());
            if (!FilenameUtils.getExtension(stripped).isEmpty()) return stripped;
        }
        return fileName;
    }

    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    private static final byte[] RAR_MAGIC = {'R', 'a', 'r', '!', 0x1A};
    private static final byte[] SEVENZ_MAGIC = {'7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
    private static final byte[] GZIP_MAGIC = {(byte) 0x1F, (byte) 0x8B};
    private static final byte[] BZ2_MAGIC = {'B', 'Z', 'h'};
    private static final byte[] XZ_MAGIC = {(byte) 0xFD, '7', 'z', 'X', 'Z', 0x00};
    private static final byte[] ZSTD_MAGIC = {0x28, (byte) 0xB5, 0x2F, (byte) 0xFD};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF_MAGIC = {'G', 'I', 'F'};
    private static final byte[] BMP_MAGIC = {'B', 'M'};
    private static final byte[] RIFF_MAGIC = {'R', 'I', 'F', 'F'};
    private static final byte[] EBML_MAGIC = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};
    private static final byte[] OGG_MAGIC = {'O', 'g', 'g', 'S'};
    private static final byte[] FLAC_MAGIC = {'f', 'L', 'a', 'C'};
    private static final byte[] FLV_MAGIC = {'F', 'L', 'V'};
    private static final byte[] ELF_MAGIC = {0x7F, 'E', 'L', 'F'};
    private static final byte[] EXE_MAGIC = {'M', 'Z'};
    private static final byte[] DEX_MAGIC = {'d', 'e', 'x', '\n'};

    public static String getReportedMimeType(Context context, Object fileOrZipEntry) {
        try {
            if (fileOrZipEntry instanceof ZipEntryInfo entry) return guessFromExtension(entry.getName());
            return context.getContentResolver().getType(FileProvider.getUriForFile(context, context.getPackageName() + ".provider", (File) fileOrZipEntry));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reported MIME type with an explicit logical name. When the on-disk name
     * differs from the name the user clicked (a staged {@code foo.pdf.bak}
     * copy, a renamed cache entry), the extension of {@code logicalName} wins
     * before any probing of the physical file, so the saved default for the
     * semantic type still applies.
     */
    public static String getReportedMimeType(Context context, Object fileOrZipEntry, String logicalName) {
        if (logicalName != null) {
            String byName = getMimeTypeFromExtension(logicalName);
            if (byName != null) return byName;
        }
        return getReportedMimeType(context, fileOrZipEntry);
    }

    public static String getRealMimeType(File file) {
        byte[] data = new byte[300];
        int len;
        try (InputStream is = new FileInputStream(file)) {
            len = is.read(data);
        } catch (Exception e) {
            return null;
        }
        if (len <= 0) return null;

        String ext = FilenameUtils.getExtension(file.getName()).toLowerCase(Locale.ROOT);

        if (startsWith(data, ZIP_MAGIC)) return zipFamilyMimeType(ext);
        if (startsWith(data, RAR_MAGIC)) return "application/x-rar-compressed";
        if (startsWith(data, SEVENZ_MAGIC)) return "application/x-7z-compressed";
        if (startsWith(data, GZIP_MAGIC)) return ext.equals("svgz") ? "image/svg+xml" : "application/gzip";
        if (startsWith(data, BZ2_MAGIC)) return "application/x-bzip2";
        if (startsWith(data, XZ_MAGIC)) return "application/x-xz";
        if (startsWith(data, ZSTD_MAGIC)) return "application/zstd";
        if (containsAt(data, len, "ustar".getBytes(StandardCharsets.US_ASCII), 257)) return "application/x-tar";

        if (startsWith(data, PNG_MAGIC)) return "image/png";
        if (startsWith(data, JPEG_MAGIC)) return "image/jpeg";
        if (startsWith(data, GIF_MAGIC)) return "image/gif";
        if (startsWith(data, BMP_MAGIC)) return "image/bmp";
        if (startsWith(data, RIFF_MAGIC)) {
            String riffType = ascii(data, 8, 4);
            switch (riffType == null ? "" : riffType) {
                case "WEBP" -> {return "image/webp";}
                case "WAVE" -> {return "audio/x-wav";}
                case "AVI " -> {return "video/x-msvideo";}
                default -> {}
            }
        }
        if (startsWith(data, "ID3".getBytes(StandardCharsets.US_ASCII))) return "audio/mpeg";
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xE0) == 0xE0 && !startsWith(data, JPEG_MAGIC)) return "audio/mpeg";
        if (len > 11 && ascii(data, 4, 4).equals("ftyp")) {
            String brand = ascii(data, 8, 4);
            switch (brand == null ? "" : brand) {
                case "M4A ", "M4B ", "M4P " -> {return "audio/mp4";}
                case "avif", "avis" -> {return "image/avif";}
                case "heic", "heix", "hevc", "hevx", "mif1", "msf1", "heim", "heis", "avic" -> {return "image/heic";}
                case "qt   ", "qt" -> {return "video/quicktime";}
                default -> {return "video/mp4";}
            }
        }
        if (startsWith(data, EBML_MAGIC)) {
            String head = new String(data, 0, Math.min(len, 64), StandardCharsets.US_ASCII);
            return head.contains("webm") ? "video/webm" : "video/x-matroska";
        }
        if (startsWith(data, PDF_MAGIC)) return "application/pdf";
        if (startsWith(data, OGG_MAGIC)) return "audio/ogg";
        if (startsWith(data, FLAC_MAGIC)) return "audio/flac";
        if (startsWith(data, FLV_MAGIC)) return "video/x-flv";
        if (startsWith(data, DEX_MAGIC)) return "application/vnd.android.dex";
        if (startsWith(data, ELF_MAGIC)) return "application/x-executable";
        if (startsWith(data, EXE_MAGIC)) return "application/x-msdownload";
        if (isBinaryXml(file)) return "text/xml";
        if (looksLikeText(data, len)) return "text/plain";
        return guessFromExtension(ext);
    }

    public static String getMimeTypeForAction(Context context, File file) {
        String reported = getReportedMimeType(context, file);
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("fix_mime_type", false))
            return reported;
        String real = getRealMimeType(file);
        return real != null ? real : reported;
    }

    /**
     * MIME to dispatch on, with the logical (clicked) name overriding the
     * physical file name for reported probing; the real-bytes probe below is
     * unchanged, so a genuinely different payload still wins under the
     * fix_mime_type setting.
     */
    public static String getMimeTypeForAction(Context context, File file, String logicalName) {
        String reported = getReportedMimeType(context, file, logicalName);
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("fix_mime_type", false))
            return reported;
        String real = getRealMimeType(file);
        return real != null ? real : reported;
    }

    private static String zipFamilyMimeType(String ext) {
        return switch (ext) {
            case "apk", "apks", "xapk", "apkm" -> "application/vnd.android.package-archive";
            case "jar", "war" -> "application/java-archive";
            case "ipa" -> "application/octet-stream";
            default -> "application/zip";
        };
    }

    private static String guessFromExtension(String fileNameOrExt) {
        try {
            String ext = fileNameOrExt.contains(".")
                    ? FilenameUtils.getExtension(fileNameOrExt).toLowerCase(Locale.ROOT)
                    : fileNameOrExt;
            if (ext.isEmpty()) return null;
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        } catch (Exception e) {
            return null;
        }
    }

    /** Public extension-to-MIME lookup used by the saved-default fast path. */
    public static String getMimeTypeFromExtension(String fileNameOrExt) {
        return guessFromExtension(fileNameOrExt);
    }

    private static boolean isBinaryXml(File file) {
        try (InputStream is = new FileInputStream(file)) {
            return FileUtils.isAxml(is);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean looksLikeText(byte[] data, int len) {
        for (int i = 0; i < len; i++) {
            byte b = data[i];
            if (b == 0) return false;
            if (b < 9 || (b > 13 && b < 32) || b == 27) return false;
        }
        return true;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }

    private static boolean containsAt(byte[] data, int len, byte[] pattern, int offset) {
        if (len < offset + pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) if (data[offset + i] != pattern[i]) return false;
        return true;
    }

    private static String ascii(byte[] data, int offset, int count) {
        if (data.length < offset + count) return "";
        return new String(data, offset, count, StandardCharsets.US_ASCII);
    }
}
